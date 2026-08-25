package ca.inspection.home.inspection.service;

import ca.inspection.home.inspection.entity.InspectionBookings;
import ca.inspection.home.inspection.entity.InspectorProfile;
import ca.inspection.home.inspection.repository.InspectorProfileRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class GoogleCalendarServiceTest {

    @Mock
    private InspectorProfileRepository inspectorProfileRepository;

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

    @SuppressWarnings("unchecked")
    private static Map<String, Object> nested(Map<String, Object> event, String key) {
        return (Map<String, Object>) event.get(key);
    }

    // EVENT CONTENT

    @Test
    void buildEvent_noStartTime_isAnAllDayEventEndingTheNextDay() {
        InspectionBookings booking = sampleBooking();
        Map<String, Object> event =
                googleCalendarService.buildEvent(booking, BookingSchedule.of(booking));

        assertThat(nested(event, "start")).containsEntry("date", "2026-03-12");
        // Google reads the all-day end date as exclusive, so a one day event ends on the 13th.
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
        // "None" is the dropdown's placeholder, not information.
        assertThat(description).doesNotContain("Referred by");
    }

    // GUARDS

    @Test
    void syncBooking_notConnected_doesNothing() {
        when(inspectorProfileService.getProfile()).thenReturn(new InspectorProfile());
        InspectionBookings booking = sampleBooking();

        assertThat(googleCalendarService.syncBooking(booking)).isNull();
        verifyNoInteractions(inspectorProfileRepository);
    }

    @Test
    void syncBooking_connectedButSwitchedOff_doesNothing() {
        InspectorProfile profile = new InspectorProfile();
        profile.setGoogleRefreshToken("refresh-token");
        profile.setGoogleCalendarEnabled(false);
        when(inspectorProfileService.getProfile()).thenReturn(profile);

        InspectionBookings booking = sampleBooking();
        booking.setGoogleEventId("existing-event");

        // The existing event is left alone rather than deleted; syncing is just paused.
        assertThat(googleCalendarService.syncBooking(booking)).isEqualTo("existing-event");
        verifyNoInteractions(inspectorProfileRepository);
    }

    @Test
    void deleteEvent_bookingHasNoEvent_doesNotCallGoogle() {
        InspectorProfile profile = new InspectorProfile();
        profile.setGoogleRefreshToken("refresh-token");
        when(inspectorProfileService.getProfile()).thenReturn(profile);

        // No googleEventId: nothing to delete, so no request and no exception.
        googleCalendarService.deleteEvent(sampleBooking());
    }

    @Test
    void isConfigured_withoutCredentials_isFalse() {
        assertThat(googleCalendarService.isConfigured()).isFalse();
    }

    @Test
    void isConfigured_withCredentials_isTrue() {
        ReflectionTestUtils.setField(googleCalendarService, "clientId", "client-id");
        ReflectionTestUtils.setField(googleCalendarService, "clientSecret", "client-secret");

        assertThat(googleCalendarService.isConfigured()).isTrue();
    }

    // STATUS

    @Test
    void status_notConnected_reportsEmptyStateWithoutCallingGoogle() {
        when(inspectorProfileService.getProfile()).thenReturn(new InspectorProfile());

        Map<String, Object> status = googleCalendarService.status();

        assertThat(status).containsEntry("configured", false);
        assertThat(status).containsEntry("connected", false);
        // Default target until the inspector picks something else.
        assertThat(status).containsEntry("calendarId", "primary");
        assertThat(status.get("calendars")).asInstanceOf(
                org.assertj.core.api.InstanceOfAssertFactories.LIST).isEmpty();
    }

    @Test
    void disconnect_clearsTheStoredGrant() {
        InspectorProfile profile = new InspectorProfile();
        profile.setGoogleRefreshToken(null);
        profile.setGoogleAccountEmail("inspector@example.com");
        profile.setGoogleCalendarEnabled(true);
        when(inspectorProfileService.getProfile()).thenReturn(profile);

        googleCalendarService.disconnect();

        assertThat(profile.getGoogleAccountEmail()).isNull();
        assertThat(profile.getGoogleCalendarEnabled()).isFalse();
        verify(inspectorProfileRepository).save(profile);
    }
}
