package ca.inspection.home.inspection.integration;

import ca.inspection.home.inspection.entity.InspectionBookings;
import ca.inspection.home.inspection.entity.InspectorProfile;
import ca.inspection.home.inspection.repository.InspectionBookingsRepository;
import ca.inspection.home.inspection.repository.InspectionReportsRepository;
import ca.inspection.home.inspection.repository.InspectorProfileRepository;
import ca.inspection.home.inspection.service.GoogleCalendarService;
import ca.inspection.home.inspection.service.InspectionBookingsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SpringBootTest
@ActiveProfiles("test")
@Tag("integration")
public class CalendarBookingSyncIT {

    @Autowired private InspectionBookingsService bookingsService;
    @Autowired private InspectionBookingsRepository bookingsRepository;
    @Autowired private InspectionReportsRepository reportsRepository;
    @Autowired private InspectorProfileRepository inspectorProfileRepository;

    @MockitoBean private GoogleCalendarService googleCalendarService;

    @BeforeEach
    void resetState() {
        reportsRepository.deleteAll();
        bookingsRepository.deleteAll();
        inspectorProfileRepository.deleteAll();
        InspectorProfile profile = new InspectorProfile();
        profile.setId(1L);
        profile.setInspectionNumber(0);
        inspectorProfileRepository.save(profile);
    }

    private InspectionBookings sample() {
        InspectionBookings b = new InspectionBookings();
        b.setClientFirstName("Ada");
        b.setClientLastName("Lovelace");
        b.setMonth("April");
        b.setDay(2);
        b.setYear(2026);
        return b;
    }

    @Test
    void create_persistsTheEventId() {
        when(googleCalendarService.syncBooking(any())).thenReturn("evt-created");

        InspectionBookings saved = bookingsService.createBooking(sample());

        InspectionBookings reloaded = bookingsRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getGoogleEventId()).isEqualTo("evt-created");
    }

    @Test
    void update_persistsTheEventId() {
        // The booking form never sends googleEventId back, so the id has to be carried over.
        when(googleCalendarService.syncBooking(any())).thenReturn(null);
        InspectionBookings saved = bookingsService.createBooking(sample());
        UUID id = saved.getId();

        reset(googleCalendarService);
        when(googleCalendarService.syncBooking(any())).thenReturn("evt-updated");

        InspectionBookings incoming = sample();
        bookingsService.updateBooking(id, incoming);

        InspectionBookings reloaded = bookingsRepository.findById(id).orElseThrow();
        assertThat(reloaded.getGoogleEventId()).isEqualTo("evt-updated");
    }

    @Test
    void delete_passesTheStoredEventIdToGoogle() {
        when(googleCalendarService.syncBooking(any())).thenReturn("evt-created");
        InspectionBookings saved = bookingsService.createBooking(sample());

        bookingsService.deleteBooking(saved.getId());

        org.mockito.ArgumentCaptor<InspectionBookings> captor =
                org.mockito.ArgumentCaptor.forClass(InspectionBookings.class);
        verify(googleCalendarService).deleteEvent(captor.capture());
        assertThat(captor.getValue().getGoogleEventId()).isEqualTo("evt-created");
    }
}
