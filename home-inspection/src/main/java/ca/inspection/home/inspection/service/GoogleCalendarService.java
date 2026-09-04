package ca.inspection.home.inspection.service;

import ca.inspection.home.inspection.entity.InspectionBookings;
import ca.inspection.home.inspection.entity.InspectorProfile;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static ca.inspection.home.inspection.service.HelperFunctions.notBlank;

@Service
@Slf4j
public class GoogleCalendarService {

    private static final String CALENDAR_API = "https://www.googleapis.com/calendar/v3";
    private static final String DEFAULT_CALENDAR_ID = "primary";

    @Autowired
    private GoogleService googleService;

    @Autowired
    private InspectorProfileService inspectorProfileService;

    // Status payload for the profile page
    public Map<String, Object> status() {
        InspectorProfile profile = inspectorProfileService.getProfile();
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("configured", googleService.isConfigured());
        status.put("connected", googleService.isConnected());
        status.put("account", profile.getGoogleAccountEmail());
        status.put("calendarId", calendarId(profile));
        status.put("enabled", !Boolean.FALSE.equals(profile.getGoogleCalendarEnabled()));

        List<Map<String, String>> calendars = List.of();
        if (googleService.isConnected()) {
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

    public void updateSettings(String calendarId, Boolean enabled) {
        InspectorProfile profile = inspectorProfileService.getProfile();
        if (notBlank(calendarId)) profile.setGoogleCalendarId(calendarId);
        if (enabled != null) profile.setGoogleCalendarEnabled(enabled);
        inspectorProfileService.saveProfile(profile);
    }

    public List<Map<String, String>> listCalendars() {
        Map<String, Object> body = googleService.get(CALENDAR_API + "/users/me/calendarList?minAccessRole=writer");
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
                Map<String, Object> updated = googleService.send("PUT",
                        CALENDAR_API + "/calendars/" + encode(calendarId)
                                + "/events/" + encode(booking.getGoogleEventId()),
                        event);
                return string(updated.get("id"));
            } catch (GoogleService.ResourceGoneException e) {
                log.info("Google event {} is gone; creating a new one", booking.getGoogleEventId());
            }
        }

        Map<String, Object> created = googleService.send("POST",
                CALENDAR_API + "/calendars/" + encode(calendarId) + "/events", event);
        String eventId = string(created.get("id"));
        log.info("Created Google Calendar event {} for booking {}", eventId, booking.getId());
        return eventId;
    }

    public void deleteEvent(InspectionBookings booking) {
        InspectorProfile profile = inspectorProfileService.getProfile();
        if (!notBlank(booking.getGoogleEventId()) || !googleService.isConnected()) {
            return;
        }
        try {
            googleService.send("DELETE",
                    CALENDAR_API + "/calendars/" + encode(calendarId(profile))
                            + "/events/" + encode(booking.getGoogleEventId()),
                    null);
            log.info("Deleted Google Calendar event {} for booking {}",
                    booking.getGoogleEventId(), booking.getId());
        } catch (GoogleService.ResourceGoneException e) {
            log.warn("Google event {} was not deleted: calendar {} has no such event.",
                    booking.getGoogleEventId(), calendarId(profile));
        }
    }

    Map<String, Object> buildEvent(InspectionBookings booking, BookingSchedule schedule) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("summary", eventTitle(booking));

        String location = formatAddress(booking);
        if (notBlank(location)) event.put("location", location);
        event.put("description", eventDescription(booking));

        if (schedule.allDay()) {
            DateTimeFormatter dateOnly = DateTimeFormatter.ISO_LOCAL_DATE;
            event.put("start", Map.of("date", schedule.date().format(dateOnly)));
            event.put("end", Map.of("date", schedule.date().plusDays(1).format(dateOnly)));
        } else {
            String zone = ZoneId.systemDefault().getId();
            log.info("System zone detected as: {}", ZoneId.systemDefault());
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
        if (booking.getInspectionNumber() != null) title.append(" #").append(booking.getInspectionNumber());
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
        if (notBlank(booking.getCity())) parts.add(booking.getCity().trim());
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
        return googleService.isConnected() && !Boolean.FALSE.equals(profile.getGoogleCalendarEnabled());
    }

    private static boolean writable(List<Map<String, String>> calendars, String calendarId) {
        return DEFAULT_CALENDAR_ID.equals(calendarId)
                || calendars.stream().anyMatch(calendar -> calendarId.equals(calendar.get("id")));
    }

    private static String calendarId(InspectorProfile profile) {
        String id = profile.getGoogleCalendarId();
        return notBlank(id) ? id : DEFAULT_CALENDAR_ID;
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String string(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}