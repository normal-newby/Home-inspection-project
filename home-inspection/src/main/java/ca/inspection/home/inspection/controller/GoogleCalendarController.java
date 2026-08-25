package ca.inspection.home.inspection.controller;

import ca.inspection.home.inspection.service.GoogleCalendarService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@RestController
@RequestMapping("/api/google/calendar")
@CrossOrigin(origins = "*")
@Slf4j
public class GoogleCalendarController {

    private static final String PROFILE_PAGE = "/html/profile.html";

    @Autowired
    private GoogleCalendarService googleCalendarService;

    @GetMapping("/status")
    public Map<String, Object> status() {
        return googleCalendarService.status();
    }

    @GetMapping("/connect")
    public ResponseEntity<Void> connect() {
        if (!googleCalendarService.isConfigured()) {
            // Reached by typing the URL, or by a stale page
            return redirectToProfile("error",
                    "Google Calendar is not set up yet. Add GOOGLE_CLIENT_ID and GOOGLE_CLIENT_SECRET to .env.");
        }
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(googleCalendarService.buildAuthUrl()))
                .build();
    }

    // Where Google returns the browser after the inspector approves (or cancels)
    @GetMapping("/callback")
    public ResponseEntity<Void> callback(@RequestParam(required = false) String code,
                                         @RequestParam(required = false) String state,
                                         @RequestParam(required = false) String error) {
        if (error != null) {
            log.warn("Google Calendar consent was declined: {}", error);
            return redirectToProfile("error", "Google sign-in was cancelled.");
        }
        if (code == null || code.isBlank()) {
            return redirectToProfile("error", "Google did not return an authorization code.");
        }
        try {
            googleCalendarService.completeConnection(code, state);
            return redirectToProfile("connected", null);
        } catch (RuntimeException e) {
            log.error("Google Calendar connection failed", e);
            return redirectToProfile("error", e.getMessage());
        }
    }

    @PostMapping("/disconnect")
    public ResponseEntity<?> disconnect() {
        googleCalendarService.disconnect();
        return ResponseEntity.ok(Map.of("connected", false));
    }

    @PostMapping("/settings")
    public ResponseEntity<?> updateSettings(@RequestBody Settings settings) {
        googleCalendarService.updateSettings(settings.calendarId(), settings.enabled());
        return ResponseEntity.ok(googleCalendarService.status());
    }

    public record Settings(String calendarId, Boolean enabled) {}

    private ResponseEntity<Void> redirectToProfile(String result, String message) {
        String target = PROFILE_PAGE + "?calendar=" + result;
        if (message != null && !message.isBlank()) {
            target += "&message=" + URLEncoder.encode(message, StandardCharsets.UTF_8);
        }
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(target)).build();
    }
}
