package ca.inspection.home.inspection.service;

import ca.inspection.home.inspection.entity.InspectionBookings;
import ca.inspection.home.inspection.entity.InspectorProfile;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static ca.inspection.home.inspection.service.HelperFunctions.fullName;
import static ca.inspection.home.inspection.service.HelperFunctions.notBlank;

@Service
@Slf4j
public class GoogleEmailService {

    private final static String SEND_URL = "https://gmail.googleapis.com/gmail/v1/users/me/messages/send";

    private final HttpClient httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_2)
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private GoogleService googleService;

    @Autowired
    private InspectorProfileService inspectorProfileService;

    public boolean isConfigured(){
        return googleService.isConfigured();
    }

    public void sendReportEmail(String toEmail, byte[] pdfBytes, InspectionBookings booking){
        InspectorProfile profile = inspectorProfileService.getProfile();

        if (!isConfigured()){
            throw new IllegalStateException("Email is not set up.");
        }

        String fromAddress = inspectorProfileService.getProfile().getGoogleAccountEmail();
        String clientName = fullName(booking);
        UUID bookingId = booking.getId();

        String raw = buildMimeMessage(fromAddress, toEmail, clientName, pdfBytes, bookingId);
        String encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));

        String token = googleService.accessToken();

        try {
            String body = objectMapper.writeValueAsString(Map.of("raw", encoded));
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(SEND_URL))
                    .timeout(Duration.ofSeconds(30))
                    .header("Authorization", "Bearer " + token)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new RuntimeException("Gmail send failed (" + response.statusCode() + "): " + response.body());
            }
            log.info("Sent report email for booking {} via Gmail", bookingId);
        } catch (Exception e){
            log.error("Failed to send report email for booking {}", booking.getId(), e);
            throw new RuntimeException("Failed to send email: " + e.getMessage(), e);
        }
    }

    private String buildMimeMessage(String from, String to, String clientName, byte[] pdfBytes, UUID bookingId) {
        String boundary = "boundary_" + UUID.randomUUID();
        String base64Pdf = Base64.getMimeEncoder().encodeToString(pdfBytes);

        StringBuilder mime = new StringBuilder();
        mime.append("From: ").append(from).append("\r\n");
        mime.append("To: ").append(to).append("\r\n");
        mime.append("Subject: Your Home Inspection Report\r\n");
        mime.append("MIME-Version: 1.0\r\n");
        mime.append("Content-Type: multipart/mixed; boundary=\"").append(boundary).append("\"\r\n\r\n");

        mime.append("--").append(boundary).append("\r\n");
        mime.append("Content-Type: text/plain; charset=\"UTF-8\"\r\n\r\n");
        mime.append("Hi ").append(clientName).append(",\r\n\r\n");
        mime.append("Please find your inspection report attached.\r\n\r\n");

        mime.append("--").append(boundary).append("\r\n");
        mime.append("Content-Type: application/pdf; name=\"inspection-report-").append(bookingId).append(".pdf\"\r\n");
        mime.append("Content-Disposition: attachment; filename=\"inspection-report-").append(bookingId).append(".pdf\"\r\n");
        mime.append("Content-Transfer-Encoding: base64\r\n\r\n");
        mime.append(base64Pdf).append("\r\n");

        mime.append("--").append(boundary).append("--");

        return mime.toString();
    }
}
