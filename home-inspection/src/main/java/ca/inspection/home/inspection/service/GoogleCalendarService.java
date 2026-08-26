package ca.inspection.home.inspection.service;

import ca.inspection.home.inspection.entity.InspectionBookings;
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
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class GoogleCalendarService {

    private static final String AUTH_URL = "https://accounts.google.com/o/oauth2/v2/auth";
    private static final String TOKEN_URL = "https://oauth2.googleapis.com/token";
    private static final String REVOKE_URL = "https://oauth2.googleapis.com/revoke";
    private static final String USERINFO_URL = "https://www.googleapis.com/oauth2/v3/userinfo";
    private static final String CALENDAR_API = "https://www.googleapis.com/calendar/v3";

    // Read the calendar list so the inspector can pick one; write only events.
    private static final String SCOPES = String.join(" ",
            "openid",
            "email",
            "https://www.googleapis.com/auth/calendar.events",
            "https://www.googleapis.com/auth/calendar.readonly");

    private static final String DEFAULT_CALENDAR_ID = "primary";

    // Refresh a little early so a token can't expire mid request.
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

    // -- Connection state --

    public boolean isConfigured() {
        return notBlank(clientId) && notBlank(clientSecret);
    }

    public boolean isConnected() {
        return notBlank(inspectorProfileService.getProfile().getGoogleRefreshToken());
    }

    // Status payload for the profile page
    public Map<String, Object> status() {
        InspectorProfile profile = inspectorProfileService.getProfile();
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("configured", isConfigured());
        status.put("connected", notBlank(profile.getGoogleRefreshToken()));
        status.put("account", profile.getGoogleAccountEmail());
        status.put("calendarId", calendarId(profile));
        status.put("enabled", !Boolean.FALSE.equals(profile.getGoogleCalendarEnabled()));

        List<Map<String, String>> calendars = List.of();
        if (notBlank(profile.getGoogleRefreshToken())) {
            try {
                calendars = listCalendars();
                if (!writable(calendars, calendarId(profile))) {
                    status.put("warning", "\"" + calendarId(profile) + "\" is not a calendar this Google "
                            + "account can write to. Pick one below and save.");
                }
            } catch (RuntimeException e) {
                log.warn("Could not list Google calendars: {}", e.getMessage());
                status.put("warning", "Could not reach Google Calendar. Try reconnecting.");
            }
        }
        status.put("calendars", calendars);
        return status;
    }

    // URL of Google's consent screen; the profile page sends the browser here
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
        String previousAccount = profile.getGoogleAccountEmail();
        String account = fetchAccountEmail(accessToken);

        profile.setGoogleRefreshToken(refreshToken);
        profile.setGoogleAccessToken(accessToken);
        profile.setGoogleTokenExpiry(expiryFrom(tokens));
        profile.setGoogleAccountEmail(account);
        // Calendar ids belong to the account that granted access
        if (!notBlank(profile.getGoogleCalendarId()) || switchedAccount(previousAccount, account)) {
            profile.setGoogleCalendarId(DEFAULT_CALENDAR_ID);
        }
        profile.setGoogleCalendarEnabled(true);
        inspectorProfileRepository.save(profile);
        log.info("Linked Google Calendar for {}", profile.getGoogleAccountEmail());
    }

    public void disconnect() {
        InspectorProfile profile = inspectorProfileService.getProfile();
        String refreshToken = profile.getGoogleRefreshToken();

        profile.setGoogleRefreshToken(null);
        profile.setGoogleAccessToken(null);
        profile.setGoogleTokenExpiry(null);
        profile.setGoogleAccountEmail(null);
        // The chosen calendar only means something under the grant being revoked here.
        profile.setGoogleCalendarId(null);
        profile.setGoogleCalendarEnabled(false);
        inspectorProfileRepository.save(profile);

        if (notBlank(refreshToken)) {
            try {
                postForm(REVOKE_URL, Map.of("token", refreshToken));
            } catch (RuntimeException e) {
                // Already revoked, or offline. The local grant is gone either way.
                log.warn("Google token revoke failed: {}", e.getMessage());
            }
        }
        log.info("Unlinked Google Calendar");
    }

    public void updateSettings(String calendarId, Boolean enabled) {
        InspectorProfile profile = inspectorProfileService.getProfile();
        if (notBlank(calendarId)) {
            profile.setGoogleCalendarId(calendarId);
        }
        if (enabled != null) {
            profile.setGoogleCalendarEnabled(enabled);
        }
        inspectorProfileRepository.save(profile);
    }

    public List<Map<String, String>> listCalendars() {
        Map<String, Object> body = get(CALENDAR_API + "/users/me/calendarList?minAccessRole=writer");
        List<Map<String, String>> calendars = new ArrayList<>();
        Object items = body.get("items");
        if (items instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> entry) {
                    Map<String, String> calendar = new LinkedHashMap<>();
                    calendar.put("id", string(entry.get("id")));
                    calendar.put("name", string(entry.get("summary")));
                    calendars.add(calendar);
                }
            }
        }
        return calendars;
    }

    // Booking sync

    public String syncBooking(InspectionBookings booking) {
        InspectorProfile profile = inspectorProfileService.getProfile();
        if (!syncEnabled(profile)) {
            return booking.getGoogleEventId();
        }

        BookingSchedule schedule = BookingSchedule.of(booking);
        if (schedule == null) {
            deleteEvent(booking);
            return null;
        }

        String calendarId = calendarId(profile);
        Map<String, Object> event = buildEvent(booking, schedule);

        if (notBlank(booking.getGoogleEventId())) {
            try {
                Map<String, Object> updated = send("PUT",
                        CALENDAR_API + "/calendars/" + encode(calendarId)
                                + "/events/" + encode(booking.getGoogleEventId()),
                        event);
                return string(updated.get("id"));
            } catch (EventGoneException e) {
                log.info("Google event {} is gone; creating a new one", booking.getGoogleEventId());
            }
        }

        Map<String, Object> created = send("POST",
                CALENDAR_API + "/calendars/" + encode(calendarId) + "/events", event);
        String eventId = string(created.get("id"));
        log.info("Created Google Calendar event {} for booking {}", eventId, booking.getId());
        return eventId;
    }

    public void deleteEvent(InspectionBookings booking) {
        InspectorProfile profile = inspectorProfileService.getProfile();
        if (!notBlank(booking.getGoogleEventId()) || !notBlank(profile.getGoogleRefreshToken())) {
            return;
        }
        try {
            send("DELETE",
                    CALENDAR_API + "/calendars/" + encode(calendarId(profile))
                            + "/events/" + encode(booking.getGoogleEventId()),
                    null);
            log.info("Deleted Google Calendar event {} for booking {}",
                    booking.getGoogleEventId(), booking.getId());
        } catch (EventGoneException e) {
            log.warn("Google event {} was not deleted: calendar {} has no such event. It was already "
                            + "removed, or the booking was synced to a different calendar.",
                    booking.getGoogleEventId(), calendarId(profile));
        }
    }

    Map<String, Object> buildEvent(InspectionBookings booking, BookingSchedule schedule) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("summary", eventTitle(booking));

        String location = formatAddress(booking);
        if (notBlank(location)) {
            event.put("location", location);
        }
        event.put("description", eventDescription(booking));

        if (schedule.allDay()) {
            DateTimeFormatter dateOnly = DateTimeFormatter.ISO_LOCAL_DATE;
            event.put("start", Map.of("date", schedule.date().format(dateOnly)));
            event.put("end", Map.of("date", schedule.date().plusDays(1).format(dateOnly)));
        } else {
            String zone = ZoneId.systemDefault().getId();
            event.put("start", Map.of(
                    "dateTime", schedule.start().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                    "timeZone", zone));
            event.put("end", Map.of(
                    "dateTime", schedule.end().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                    "timeZone", zone));
        }
        return event;
    }

    private String eventTitle(InspectionBookings booking) {
        StringBuilder title = new StringBuilder("Home Inspection");
        if (booking.getInspectionNumber() != null) {
            title.append(" #").append(booking.getInspectionNumber());
        }
        String client = fullName(booking);
        if (notBlank(booking.getInspectionAddress())) {
            title.append(" - ").append(booking.getInspectionAddress().trim());
        } else if (client != null) {
            title.append(" - ").append(client);
        }
        return title.toString();
    }

    private String eventDescription(InspectionBookings booking) {
        StringBuilder description = new StringBuilder();
        appendLine(description, "Client", fullName(booking));
        appendLine(description, "Phone", booking.getPhone());
        appendLine(description, "Email", booking.getEmail());
        appendLine(description, "Booked by", booking.getBookedBy());
        appendLine(description, "Referred by", booking.getReferredBy());
        if (booking.getInspectionNumber() != null) {
            appendLine(description, "Inspection #", String.valueOf(booking.getInspectionNumber()));
        }
        return description.toString().trim();
    }

    private static void appendLine(StringBuilder sb, String label, String value) {
        // "None" is the form's placeholder for the referral dropdowns; it adds nothing.
        if (value != null && !value.isBlank() && !"None".equalsIgnoreCase(value.trim())) {
            sb.append(label).append(": ").append(value.trim()).append("\n");
        }
    }

    private static String fullName(InspectionBookings booking) {
        String first = booking.getClientFirstName() == null ? "" : booking.getClientFirstName();
        String last = booking.getClientLastName() == null ? "" : booking.getClientLastName();
        String name = (first + " " + last).trim();
        return name.isEmpty() ? null : name;
    }

    private static String formatAddress(InspectionBookings booking) {
        List<String> parts = new ArrayList<>();
        String street = booking.getInspectionAddress();
        if (notBlank(street)) {
            parts.add(notBlank(booking.getSuite())
                    ? street.trim() + " Unit " + booking.getSuite().trim()
                    : street.trim());
        }
        if (notBlank(booking.getCity())) {
            parts.add(booking.getCity().trim());
        }
        String region = booking.getProvince();
        String postal = booking.getPostalCode();
        if (notBlank(region) && notBlank(postal)) {
            parts.add(region.trim() + " " + postal.trim());
        } else if (notBlank(region)) {
            parts.add(region.trim());
        } else if (notBlank(postal)) {
            parts.add(postal.trim());
        }
        return String.join(", ", parts);
    }

    private boolean syncEnabled(InspectorProfile profile) {
        return notBlank(profile.getGoogleRefreshToken())
                && !Boolean.FALSE.equals(profile.getGoogleCalendarEnabled());
    }

    private static boolean writable(List<Map<String, String>> calendars, String calendarId) {
        return DEFAULT_CALENDAR_ID.equals(calendarId)
                || calendars.stream().anyMatch(calendar -> calendarId.equals(calendar.get("id")));
    }

    private static boolean switchedAccount(String previous, String current) {
        return notBlank(previous) && notBlank(current) && !previous.equalsIgnoreCase(current);
    }

    private static String calendarId(InspectorProfile profile) {
        String id = profile.getGoogleCalendarId();
        return notBlank(id) ? id : DEFAULT_CALENDAR_ID;
    }

    // -- Tokens --

    /** A usable access token, refreshing the cached one when it has aged out. */
    private String accessToken() {
        InspectorProfile profile = inspectorProfileService.getProfile();
        String refreshToken = profile.getGoogleRefreshToken();
        if (!notBlank(refreshToken)) {
            throw new IllegalStateException("Google Calendar is not connected.");
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
            // Cosmetic only - the link works without knowing the address.
            log.warn("Could not read Google account email: {}", e.getMessage());
            return null;
        }
    }

    private void requireConfigured() {
        if (!isConfigured()) {
            throw new IllegalStateException(
                    "Google Calendar is not set up. Add GOOGLE_CLIENT_ID and GOOGLE_CLIENT_SECRET to .env.");
        }
    }

    // -- HTTP --

    private Map<String, Object> get(String url) {
        return send("GET", url, null);
    }

    /** Calls the Calendar API with the current access token. Body may be null. */
    private Map<String, Object> send(String method, String url, Map<String, Object> body) {
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
                throw new EventGoneException(url);
            }
            if (status < 200 || status >= 300) {
                throw new RuntimeException("Google Calendar request failed (" + status + "): " + response.body());
            }
            return response.body() == null || response.body().isBlank() ? Map.of() : parse(response.body());
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Google Calendar request failed: " + e.getMessage(), e);
        }
    }

    private Map<String, Object> postForm(String url, Map<String, String> form) {
        StringBuilder encoded = new StringBuilder();
        form.forEach((key, value) -> {
            if (!encoded.isEmpty()) {
                encoded.append("&");
            }
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

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    static class EventGoneException extends RuntimeException {
        EventGoneException(String url) {
            super("Google Calendar resource no longer exists: " + url);
        }
    }
}
