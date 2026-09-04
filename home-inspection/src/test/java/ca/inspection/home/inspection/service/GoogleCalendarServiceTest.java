package ca.inspection.home.inspection.service;

import ca.inspection.home.inspection.entity.InspectionBookings;
import ca.inspection.home.inspection.entity.InspectorProfile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class GoogleCalendarServiceTest {

    @Mock
    private GoogleService googleService;

    @Mock
    private InspectorProfileService inspectorProfileService;

    @InjectMocks
    private GoogleCalendarService googleCalendarService;

    private static InspectionBookings sampleBooking() {
        InspectionBookings booking = new InspectionBookings();
        booking.setInspectionNumber(1042);
        booking.setInspectionAddress("500 Test Ave");
        booking.setSuite("3");
        booking.setCity("Toronto");
        booking.setProvince("ON");
        booking.setPostalCode("M2N 6S3");
        booking.setClientFirstName("Ada");
        booking.setClientLastName("Lovelace");
        booking.setPhone("416-555-0100");
        booking.setEmail("ada@example.com");
        booking.setBookedBy("Client");
        booking.setReferredBy("None");
        booking.setMonth("March");
        booking.setDay(12);
        booking.setYear(2026);
        return booking;
    }

    // Checking isConfigured/isConnected is how these methods decide to stand down, so those
    // calls are expected; the assertion that matters is that nothing was sent to Google.
    private static void verifyNoRequestsTo(GoogleService googleService) {
        verify(googleService, never()).send(any(), any(), any());
        verify(googleService, never()).get(any());
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> nested(Map<String, Object> event, String key) {
        return (Map<String, Object>) event.get(key);
    }

    // EVENT CONTENT (pure logic — no Google calls involved)

    @Test
    void buildEvent_noStartTime_isAnAllDayEventEndingTheNextDay() {
        InspectionBookings booking = sampleBooking();
        Map<String, Object> event =
                googleCalendarService.buildEvent(booking, BookingSchedule.of(booking));

        assertThat(nested(event, "start")).containsEntry("date", "2026-03-12");
        assertThat(nested(event, "end")).containsEntry("date", "2026-03-13");
        assertThat(nested(event, "start")).doesNotContainKey("dateTime");
    }

    @Test
    void buildEvent_withStartTime_spansTheInspectionLength() {
        InspectionBookings booking = sampleBooking();
        booking.setStartTime("09:00");
        booking.setDurationMinutes(180);

        Map<String, Object> event =
                googleCalendarService.buildEvent(booking, BookingSchedule.of(booking));

        assertThat(nested(event, "start")).containsEntry("dateTime", "2026-03-12T09:00:00");
        assertThat(nested(event, "end")).containsEntry("dateTime", "2026-03-12T12:00:00");
        assertThat(nested(event, "start")).containsKey("timeZone");
    }

    @Test
    void buildEvent_titleCarriesInspectionNumberAndAddress() {
        InspectionBookings booking = sampleBooking();
        Map<String, Object> event =
                googleCalendarService.buildEvent(booking, BookingSchedule.of(booking));

        assertThat(event.get("summary")).isEqualTo("Home Inspection #1042 - 500 Test Ave");
    }

    @Test
    void buildEvent_withoutAddress_fallsBackToClientName() {
        InspectionBookings booking = sampleBooking();
        booking.setInspectionAddress(null);

        Map<String, Object> event =
                googleCalendarService.buildEvent(booking, BookingSchedule.of(booking));

        assertThat(event.get("summary")).isEqualTo("Home Inspection #1042 - Ada Lovelace");
    }

    @Test
    void buildEvent_locationJoinsTheAddressParts() {
        InspectionBookings booking = sampleBooking();
        Map<String, Object> event =
                googleCalendarService.buildEvent(booking, BookingSchedule.of(booking));

        assertThat(event.get("location")).isEqualTo("500 Test Ave Unit 3, Toronto, ON M2N 6S3");
    }

    @Test
    void buildEvent_descriptionListsContactDetailsAndSkipsNone() {
        InspectionBookings booking = sampleBooking();
        Map<String, Object> event =
                googleCalendarService.buildEvent(booking, BookingSchedule.of(booking));

        String description = String.valueOf(event.get("description"));
        assertThat(description).contains("Client: Ada Lovelace");
        assertThat(description).contains("Phone: 416-555-0100");
        assertThat(description).contains("Booked by: Client");
        assertThat(description).doesNotContain("Referred by");
    }

    // GUARDS

    @Test
    void syncBooking_notConnected_doesNothing() {
        when(inspectorProfileService.getProfile()).thenReturn(new InspectorProfile());
        when(googleService.isConnected()).thenReturn(false);
        InspectionBookings booking = sampleBooking();

        assertThat(googleCalendarService.syncBooking(booking)).isNull();
        verifyNoRequestsTo(googleService);
    }

    @Test
    void syncBooking_connectedButSwitchedOff_doesNothing() {
        InspectorProfile profile = new InspectorProfile();
        profile.setGoogleCalendarEnabled(false);
        when(inspectorProfileService.getProfile()).thenReturn(profile);
        when(googleService.isConnected()).thenReturn(true);

        InspectionBookings booking = sampleBooking();
        booking.setGoogleEventId("existing-event");

        // The existing event is left alone rather than deleted; syncing is just paused.
        assertThat(googleCalendarService.syncBooking(booking)).isEqualTo("existing-event");
        verifyNoRequestsTo(googleService);
    }

    @Test
    void deleteEvent_bookingHasNoEvent_doesNotCallGoogle() {
        googleCalendarService.deleteEvent(sampleBooking());

        verifyNoRequestsTo(googleService);
    }

    @Test
    void deleteEvent_notConnected_doesNotCallGoogle() {
        InspectionBookings booking = sampleBooking();
        booking.setGoogleEventId("existing-event");
        when(googleService.isConnected()).thenReturn(false);

        googleCalendarService.deleteEvent(booking);

        verifyNoRequestsTo(googleService);
    }

    // STATUS

    @Test
    void status_notConnected_reportsEmptyStateWithoutCallingGoogle() {
        when(inspectorProfileService.getProfile()).thenReturn(new InspectorProfile());
        when(googleService.isConfigured()).thenReturn(false);
        when(googleService.isConnected()).thenReturn(false);

        Map<String, Object> status = googleCalendarService.status();

        assertThat(status).containsEntry("configured", false);
        assertThat(status).containsEntry("connected", false);
        assertThat(status).containsEntry("calendarId", "primary");
        assertThat(status.get("calendars")).asInstanceOf(
                org.assertj.core.api.InstanceOfAssertFactories.LIST).isEmpty();
    }

    // STATUS: a calendar the account cannot write to

    @Test
    void status_calendarMissingFromTheAccount_isCalledOut() {
        InspectorProfile profile = new InspectorProfile();
        profile.setGoogleCalendarId("someone.else@gmail.com");
        when(inspectorProfileService.getProfile()).thenReturn(profile);
        when(googleService.isConnected()).thenReturn(true);
        when(googleService.get(anyString())).thenReturn(Map.of(
                "items", List.of(Map.of("id", "inspector@example.com", "summary", "Work"))
        ));

        Map<String, Object> status = googleCalendarService.status();

        assertThat(status).containsEntry("calendarId", "someone.else@gmail.com");
        assertThat((String) status.get("warning")).contains("someone.else@gmail.com");
    }

    @Test
    void status_chosenCalendarIsWritable_hasNoWarning() {
        InspectorProfile profile = new InspectorProfile();
        profile.setGoogleCalendarId("inspector@example.com");
        when(inspectorProfileService.getProfile()).thenReturn(profile);
        when(googleService.isConnected()).thenReturn(true);
        when(googleService.get(anyString())).thenReturn(Map.of(
                "items", List.of(Map.of("id", "inspector@example.com", "summary", "Work"))
        ));

        assertThat(googleCalendarService.status()).doesNotContainKey("warning");
    }

    @Test
    void status_primaryNeedsNoListing() {
        InspectorProfile profile = new InspectorProfile();
        when(inspectorProfileService.getProfile()).thenReturn(profile);
        when(googleService.isConnected()).thenReturn(true);
        when(googleService.get(anyString())).thenReturn(Map.of());

        assertThat(googleCalendarService.status()).doesNotContainKey("warning");
    }

    @Test
    void status_listCalendarsThrows_showsReconnectWarning() {
        InspectorProfile profile = new InspectorProfile();
        when(inspectorProfileService.getProfile()).thenReturn(profile);
        when(googleService.isConnected()).thenReturn(true);
        when(googleService.get(anyString())).thenThrow(new RuntimeException("network error"));

        Map<String, Object> status = googleCalendarService.status();

        assertThat((String) status.get("warning")).contains("reconnecting");
    }

    // SYNC BOOKING: creating and updating events

    @Test
    void syncBooking_noExistingEvent_createsNewEvent() {
        InspectorProfile profile = new InspectorProfile();
        profile.setGoogleCalendarEnabled(true);
        when(inspectorProfileService.getProfile()).thenReturn(profile);
        when(googleService.isConnected()).thenReturn(true);
        when(googleService.send(eq("POST"), anyString(), any()))
                .thenReturn(Map.of("id", "new-event-id"));

        InspectionBookings booking = sampleBooking();

        String result = googleCalendarService.syncBooking(booking);

        assertThat(result).isEqualTo("new-event-id");
    }

    @Test
    void syncBooking_existingEvent_updatesInPlace() {
        InspectorProfile profile = new InspectorProfile();
        profile.setGoogleCalendarEnabled(true);
        when(inspectorProfileService.getProfile()).thenReturn(profile);
        when(googleService.isConnected()).thenReturn(true);
        when(googleService.send(eq("PUT"), anyString(), any()))
                .thenReturn(Map.of("id", "existing-event-id"));

        InspectionBookings booking = sampleBooking();
        booking.setGoogleEventId("existing-event-id");

        String result = googleCalendarService.syncBooking(booking);

        assertThat(result).isEqualTo("existing-event-id");
    }

    @Test
    void syncBooking_existingEventGone_createsReplacementEvent() {
        InspectorProfile profile = new InspectorProfile();
        profile.setGoogleCalendarEnabled(true);
        when(inspectorProfileService.getProfile()).thenReturn(profile);
        when(googleService.isConnected()).thenReturn(true);
        when(googleService.send(eq("PUT"), anyString(), any()))
                .thenThrow(new GoogleService.ResourceGoneException("gone"));
        when(googleService.send(eq("POST"), anyString(), any()))
                .thenReturn(Map.of("id", "replacement-event-id"));

        InspectionBookings booking = sampleBooking();
        booking.setGoogleEventId("stale-event-id");

        String result = googleCalendarService.syncBooking(booking);

        assertThat(result).isEqualTo("replacement-event-id");
    }

    @Test
    void syncBooking_noScheduleSet_deletesExistingEventAndReturnsNull() {
        InspectorProfile profile = new InspectorProfile();
        profile.setGoogleCalendarEnabled(true);
        when(inspectorProfileService.getProfile()).thenReturn(profile);
        when(googleService.isConnected()).thenReturn(true);

        InspectionBookings booking = new InspectionBookings(); // no month/day/year
        booking.setGoogleEventId("existing-event-id");

        String result = googleCalendarService.syncBooking(booking);

        assertThat(result).isNull();
        verify(googleService).send(eq("DELETE"), anyString(), eq(null));
    }
}