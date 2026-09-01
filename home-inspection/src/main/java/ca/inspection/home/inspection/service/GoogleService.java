package ca.inspection.home.inspection.service;

import ca.inspection.home.inspection.entity.InspectorProfile;
import ca.inspection.home.inspection.repository.InspectorProfileRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;

import static ca.inspection.home.inspection.service.HelperFunctions.notBlank;

@Service
@Slf4j
public class GoogleService {

    private static final String AUTH_URL = "https://accounts.google.com/o/oauth2/v2/auth";
    private static final String TOKEN_URL = "https://oauth2.googleapis.com/token";
    private static final String REVOKE_URL = "https://oauth2.googleapis.com/revoke";
    private static final String USERINFO_URL = "https://www.googleapis.com/oauth2/v3/userinfo";

    private static final String SCOPES = String.join(" ",
            "openid",
            "email",
            "https://www.googleapis.com/auth/calendar.events",
            "https://www.googleapis.com/auth/calendar.readonly",
            "https://www.googleapis.com/auth/gmail.send");

    private static final long EXPIRY_SKEW_SECONDS = 60;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_2)
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SecureRandom random = new SecureRandom();

    private volatile String pendingState;

    @Autowired
    private InspectorProfileRepository inspectorProfileRepository;

    @Autowired
    private InspectorProfileService inspectorProfileService;

    @Value("${google.oauth.client-id:}")
    private String clientId;

    @Value("${google.oauth.client-secret:}")
    private String clientSecret;

    @Value("${google.oauth.redirect-uri:http://localhost:8080/api/google/calendar/callback}")
    private String redirectUri;

    public boolean isConfigured() {
        return notBlank(clientId) && notBlank(clientSecret);
    }

    public boolean isConnected() {
        return notBlank(inspectorProfileService.getProfile().getGoogleRefreshToken());
    }

    public String buildAuthUrl() {
        requireConfigured();
        byte[] bytes = new byte[24];
        random.nextBytes(bytes);
        pendingState = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        return AUTH_URL
                + "?client_id=" + encode(clientId)
                + "&redirect_uri=" + encode(redirectUri)
                + "&response_type=code"
                + "&scope=" + encode(SCOPES)
                + "&access_type=offline"
                + "&prompt=consent"
                + "&include_granted_scopes=true"
                + "&state=" + encode(pendingState);
    }

    public void completeConnection(String code, String state) {
        requireConfigured();
        if (pendingState == null || !pendingState.equals(state)) {
            throw new IllegalArgumentException("Google sign-in could not be verified. Start the connection again.");
        }
        pendingState = null;

        Map<String, Object> tokens = postForm(TOKEN_URL, Map.of(
                "code", code,
                "client_id", clientId,
                "client_secret", clientSecret,
                "redirect_uri", redirectUri,
                "grant_type", "authorization_code"
        ));

        String refreshToken = string(tokens.get("refresh_token"));
        String accessToken = string(tokens.get("access_token"));
        if (!notBlank(refreshToken)) {
            throw new IllegalStateException("Google did not return a refresh token. Remove this app under "
                    + "myaccount.google.com/permissions and connect again.");
        }

        InspectorProfile profile = inspectorProfileService.getProfile();
        String account = fetchAccountEmail(accessToken);

        profile.setGoogleRefreshToken(refreshToken);
        profile.setGoogleAccessToken(accessToken);
        profile.setGoogleTokenExpiry(expiryFrom(tokens));
        profile.setGoogleAccountEmail(account);
        inspectorProfileRepository.save(profile);
        log.info("Linked Google account for {}", account);
    }

    public void disconnect() {
        InspectorProfile profile = inspectorProfileService.getProfile();
        String refreshToken = profile.getGoogleRefreshToken();

        profile.setGoogleRefreshToken(null);
        profile.setGoogleAccessToken(null);
        profile.setGoogleTokenExpiry(null);
        profile.setGoogleAccountEmail(null);
        inspectorProfileRepository.save(profile);

        if (notBlank(refreshToken)) {
            try {
                postForm(REVOKE_URL, Map.of("token", refreshToken));
            } catch (RuntimeException e) {
                log.warn("Google token revoke failed: {}", e.getMessage());
            }
        }
        log.info("Unlinked Google account");
    }

    /** A usable access token, refreshing the cached one when it has aged out. */
    public String accessToken() {
        InspectorProfile profile = inspectorProfileService.getProfile();
        String refreshToken = profile.getGoogleRefreshToken();
        if (!notBlank(refreshToken)) {
            throw new IllegalStateException("Google is not connected.");
        }

        Long expiry = profile.getGoogleTokenExpiry();
        boolean stillValid = notBlank(profile.getGoogleAccessToken())
                && expiry != null
                && expiry - EXPIRY_SKEW_SECONDS > Instant.now().getEpochSecond();
        if (stillValid) {
            return profile.getGoogleAccessToken();
        }

        requireConfigured();
        Map<String, Object> tokens = postForm(TOKEN_URL, Map.of(
                "client_id", clientId,
                "client_secret", clientSecret,
                "refresh_token", refreshToken,
                "grant_type", "refresh_token"
        ));
        String accessToken = string(tokens.get("access_token"));
        if (!notBlank(accessToken)) {
            throw new IllegalStateException("Google did not return an access token.");
        }
        profile.setGoogleAccessToken(accessToken);
        profile.setGoogleTokenExpiry(expiryFrom(tokens));
        inspectorProfileRepository.save(profile);
        return accessToken;
    }

    private static Long expiryFrom(Map<String, Object> tokens) {
        Object expiresIn = tokens.get("expires_in");
        long seconds = expiresIn instanceof Number number ? number.longValue() : 3600L;
        return Instant.now().getEpochSecond() + seconds;
    }

    private String fetchAccountEmail(String accessToken) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(USERINFO_URL))
                    .timeout(Duration.ofSeconds(20))
                    .header("Authorization", "Bearer " + accessToken)
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                return null;
            }
            return string(parse(response.body()).get("email"));
        } catch (Exception e) {
            log.warn("Could not read Google account email: {}", e.getMessage());
            return null;
        }
    }

    private void requireConfigured() {
        if (!isConfigured()) {
            throw new IllegalStateException(
                    "Google is not set up. Add GOOGLE_CLIENT_ID and GOOGLE_CLIENT_SECRET to .env.");
        }
    }

    /** Generic authenticated call to any Google API, used by Calendar, Gmail, etc. */
    public Map<String, Object> send(String method, String url, Map<String, Object> body) {
        String token = accessToken();
        try {
            HttpRequest.BodyPublisher publisher = body == null
                    ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(30))
                    .header("Authorization", "Bearer " + token)
                    .header("Content-Type", "application/json")
                    .method(method, publisher)
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            int status = response.statusCode();
            if (status == 404 || status == 410) {
                throw new ResourceGoneException(url);
            }
            if (status < 200 || status >= 300) {
                throw new RuntimeException("Google API request failed (" + status + "): " + response.body());
            }
            return response.body() == null || response.body().isBlank() ? Map.of() : parse(response.body());
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Google API request failed: " + e.getMessage(), e);
        }
    }

    public Map<String, Object> get(String url) {
        return send("GET", url, null);
    }

    private Map<String, Object> postForm(String url, Map<String, String> form) {
        StringBuilder encoded = new StringBuilder();
        form.forEach((key, value) -> {
            if (!encoded.isEmpty()) encoded.append("&");
            encoded.append(encode(key)).append("=").append(encode(value));
        });

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(encoded.toString()))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new RuntimeException("Google token request failed (" + response.statusCode() + "): "
                        + response.body());
            }
            return response.body() == null || response.body().isBlank() ? Map.of() : parse(response.body());
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Google token request failed: " + e.getMessage(), e);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parse(String json) {
        return objectMapper.readValue(json, Map.class);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String string(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    public static class ResourceGoneException extends RuntimeException {
        public ResourceGoneException(String url) {
            super("Google resource no longer exists: " + url);
        }
    }
}