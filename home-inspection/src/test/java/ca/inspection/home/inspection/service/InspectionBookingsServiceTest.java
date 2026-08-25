package ca.inspection.home.inspection.service;

import ca.inspection.home.inspection.DTO.BookingDetails;
import ca.inspection.home.inspection.DTO.InvoiceAmount;
import ca.inspection.home.inspection.entity.InspectionBookings;
import ca.inspection.home.inspection.entity.InspectionReport;
import ca.inspection.home.inspection.entity.InspectorProfile;
import ca.inspection.home.inspection.entity.Invoice;
import ca.inspection.home.inspection.repository.InspectionBookingsRepository;
import ca.inspection.home.inspection.repository.InspectionReportsRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class InspectionBookingsServiceTest {

    @Mock
    private InspectionBookingsRepository inspectionBookingsRepository;

    @Mock
    private InspectorProfileService inspectorProfileService;

    @Mock
    private InspectionReportsRepository inspectionReportsRepository;

    @Mock
    private GoogleCalendarService googleCalendarService;

    @InjectMocks
    private InspectionBookingsService inspectionBookingsService;

    // BUILD INVOICE AMOUNT

    @Test
    void buildInvoiceAmount_singleInvoice_calculatesTaxesCorrectly() {
        Invoice invoice = new Invoice(UUID.randomUUID(), "Inspection", new BigDecimal("500.00"), null);

        InvoiceAmount result = inspectionBookingsService.buildInvoiceAmount(List.of(invoice));

        assertThat(result.getSubtotal()).isEqualByComparingTo("500.00");
        assertThat(result.getHst()).isEqualByComparingTo("40.00");
        assertThat(result.getGst()).isEqualByComparingTo("25.00");
        assertThat(result.getTotal()).isEqualByComparingTo("565.00");
    }

    @Test
    void buildInvoiceAmount_multipleInvoices_summedBeforeTax() {
        Invoice a = new Invoice(UUID.randomUUID(), "Home Inspection", new BigDecimal("400.00"), null);
        Invoice b = new Invoice(UUID.randomUUID(), "Radon Test", new BigDecimal("100.00"), null);

        InvoiceAmount result = inspectionBookingsService.buildInvoiceAmount(List.of(a, b));

        assertThat(result.getSubtotal()).isEqualByComparingTo("500.00");
        assertThat(result.getTotal()).isEqualByComparingTo("565.00");
    }

    @Test
    void buildInvoiceAmount_emptyList_returnsZero() {
        InvoiceAmount result = inspectionBookingsService.buildInvoiceAmount(List.of());

        assertThat(result.getSubtotal()).isEqualByComparingTo("0.00");
        assertThat(result.getTotal()).isEqualByComparingTo("0.00");
    }

    @Test
    void buildInvoiceAmount_fractionalCents_roundsHalfUp() {
        // 333.33 * 0.08 = 26.6664 -> 26.67, * 0.05 = 16.6665 -> 16.67
        Invoice invoice = new Invoice(UUID.randomUUID(), "Inspection", new BigDecimal("333.33"), null);

        InvoiceAmount result = inspectionBookingsService.buildInvoiceAmount(List.of(invoice));

        assertThat(result.getHst()).isEqualByComparingTo("26.67");
        assertThat(result.getGst()).isEqualByComparingTo("16.67");
    }

    // CREATE BOOKING

    @Test
    void createBooking_validBooking_savesAndLinksReport() {
        InspectionBookings booking = new InspectionBookings();
        InspectorProfile profile = new InspectorProfile();
        profile.setSummaryLetterBody("summary body");

        when(inspectorProfileService.getAndUpdateNumber()).thenReturn(42);
        when(inspectorProfileService.getProfile()).thenReturn(profile);
        when(inspectionBookingsRepository.save(booking)).thenReturn(booking);
        when(inspectionReportsRepository.save(any(InspectionReport.class)))
                .thenAnswer(res -> res.getArgument(0));

        InspectionBookings result = inspectionBookingsService.createBooking(booking);

        assertThat(result).isEqualTo(booking);
        assertThat(result.getInspectionNumber()).isEqualTo(42);
        assertThat(result.getInspectionReport()).isNotNull();
        assertThat(result.getInspectionReport().getInspectionBooking()).isEqualTo(booking);
        assertThat(result.getInspectionReport().getSummary()).isEqualTo("summary body");
        verify(inspectionBookingsRepository).save(booking);
        verify(inspectionReportsRepository).save(any(InspectionReport.class));
    }

    @Test
    void createBooking_withInvoices_setsBookingReferenceOnEachInvoice() {
        InspectionBookings booking = new InspectionBookings();
        Invoice invoice1 = new Invoice();
        Invoice invoice2 = new Invoice();
        booking.setInvoices(List.of(invoice1, invoice2));

        when(inspectorProfileService.getAndUpdateNumber()).thenReturn(1);
        when(inspectorProfileService.getProfile()).thenReturn(new InspectorProfile());
        when(inspectionBookingsRepository.save(booking)).thenReturn(booking);
        when(inspectionReportsRepository.save(any(InspectionReport.class)))
                .thenAnswer(res -> res.getArgument(0));

        inspectionBookingsService.createBooking(booking);

        // Without this, cascade-save writes invoices with a null booking_id.
        assertThat(invoice1.getBookings()).isEqualTo(booking);
        assertThat(invoice2.getBookings()).isEqualTo(booking);
    }

    @Test
    void createBooking_impossibleDate_isRejectedBeforeAnythingIsSaved() {
        InspectionBookings booking = new InspectionBookings();
        booking.setMonth("February");
        booking.setDay(30);
        booking.setYear(2026);

        assertThatThrownBy(() -> inspectionBookingsService.createBooking(booking))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not a valid date");

        verify(inspectionBookingsRepository, never()).save(any());
        verify(inspectorProfileService, never()).getAndUpdateNumber();
    }

    @Test
    void createBooking_calendarReturnsEventId_storesItOnTheBooking() {
        InspectionBookings booking = new InspectionBookings();

        when(inspectorProfileService.getAndUpdateNumber()).thenReturn(7);
        when(inspectorProfileService.getProfile()).thenReturn(new InspectorProfile());
        when(inspectionBookingsRepository.save(booking)).thenReturn(booking);
        when(inspectionReportsRepository.save(any(InspectionReport.class)))
                .thenAnswer(res -> res.getArgument(0));
        when(googleCalendarService.syncBooking(booking)).thenReturn("google-event-1");

        InspectionBookings result = inspectionBookingsService.createBooking(booking);

        assertThat(result.getGoogleEventId()).isEqualTo("google-event-1");
        // Once for the booking itself, once to record the event id.
        verify(inspectionBookingsRepository, times(2)).save(booking);
    }

    @Test
    void createBooking_calendarUnavailable_stillReturnsTheBooking() {
        InspectionBookings booking = new InspectionBookings();

        when(inspectorProfileService.getAndUpdateNumber()).thenReturn(7);
        when(inspectorProfileService.getProfile()).thenReturn(new InspectorProfile());
        when(inspectionBookingsRepository.save(booking)).thenReturn(booking);
        when(inspectionReportsRepository.save(any(InspectionReport.class)))
                .thenAnswer(res -> res.getArgument(0));
        when(googleCalendarService.syncBooking(booking)).thenThrow(new RuntimeException("Google is down"));

        InspectionBookings result = inspectionBookingsService.createBooking(booking);

        // A calendar outage must not cost the inspector the booking.
        assertThat(result).isEqualTo(booking);
        assertThat(result.getGoogleEventId()).isNull();
    }

    // FIND ALL

    @Test
    void findAll_returnsBookingDetailsFromRepository() {
        BookingDetails a = mock(BookingDetails.class);
        BookingDetails b = mock(BookingDetails.class);

        when(inspectionBookingsRepository.findBookingDetailsByOrderByCreatedAtDesc())
                .thenReturn(List.of(a, b));

        List<BookingDetails> result = inspectionBookingsService.findAll();

        assertThat(result).containsExactly(a, b);
    }

    // FIND BY ID

    @Test
    void findById_bookingExists_returnsBooking() {
        UUID id = UUID.randomUUID();
        InspectionBookings booking = new InspectionBookings();
        booking.setId(id);

        when(inspectionBookingsRepository.findById(id)).thenReturn(Optional.of(booking));

        InspectionBookings result = inspectionBookingsService.findById(id);

        assertThat(result).isEqualTo(booking);
    }

    @Test
    void findById_bookingNotFound_throwsNoSuchElementException() {
        UUID id = UUID.randomUUID();
        when(inspectionBookingsRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> inspectionBookingsService.findById(id))
                .isInstanceOf(NoSuchElementException.class);
    }

    // GET BOOKING DETAILS

    @Test
    void getBookingDetails_bookingExists_returnsProjection() {
        UUID id = UUID.randomUUID();
        BookingDetails details = mock(BookingDetails.class);

        when(inspectionBookingsRepository.getBookingDetails(id)).thenReturn(details);

        BookingDetails result = inspectionBookingsService.getBookingDetails(id);

        assertThat(result).isEqualTo(details);
    }

    @Test
    void getBookingDetails_bookingNotFound_throwsNoSuchElementException() {
        UUID id = UUID.randomUUID();
        when(inspectionBookingsRepository.getBookingDetails(id)).thenReturn(null);

        assertThatThrownBy(() -> inspectionBookingsService.getBookingDetails(id))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining(id.toString());
    }

    // GET REPORT FROM BOOKING

    @Test
    void getReportFromBooking_bookingExists_returnsReport() {
        UUID id = UUID.randomUUID();
        InspectionBookings booking = new InspectionBookings();
        InspectionReport report = new InspectionReport();
        booking.setInspectionReport(report);

        when(inspectionBookingsRepository.findById(id)).thenReturn(Optional.of(booking));

        InspectionReport result = inspectionBookingsService.getReportFromBooking(id);

        assertThat(result).isEqualTo(report);
    }

    @Test
    void getReportFromBooking_bookingNotFound_throwsRuntimeException() {
        UUID id = UUID.randomUUID();
        when(inspectionBookingsRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> inspectionBookingsService.getReportFromBooking(id))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("booking not found");
    }

    // UPDATE BOOKING

    @Test
    void updateBooking_withInvoices_setsBookingReferenceOnEachInvoice() {
        UUID id = UUID.randomUUID();
        InspectionBookings booking = new InspectionBookings();
        Invoice invoice1 = new Invoice();
        Invoice invoice2 = new Invoice();
        booking.setInvoices(List.of(invoice1, invoice2));

        when(inspectionBookingsRepository.save(booking)).thenReturn(booking);

        ResponseEntity<?> result = inspectionBookingsService.updateBooking(id, booking);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(booking.getId()).isEqualTo(id);
        assertThat(invoice1.getBookings()).isEqualTo(booking);
        assertThat(invoice2.getBookings()).isEqualTo(booking);
        verify(inspectionBookingsRepository).save(booking);
    }

    @Test
    void updateBooking_nullInvoices_savesWithoutError() {
        UUID id = UUID.randomUUID();
        InspectionBookings booking = new InspectionBookings();
        booking.setInvoices(null);

        when(inspectionBookingsRepository.save(booking)).thenReturn(booking);

        ResponseEntity<?> result = inspectionBookingsService.updateBooking(id, booking);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(booking.getId()).isEqualTo(id);
        verify(inspectionBookingsRepository).save(booking);
    }

    @Test
    void updateBooking_carriesOverFieldsTheEditFormDoesNotPost() {
        UUID id = UUID.randomUUID();
        InspectionBookings existing = new InspectionBookings();
        existing.setInspectionNumber(1042);
        existing.setGoogleEventId("google-event-1");

        // What the edit form actually posts: no inspection number, no event id.
        InspectionBookings incoming = new InspectionBookings();

        when(inspectionBookingsRepository.findById(id)).thenReturn(Optional.of(existing));
        when(inspectionBookingsRepository.save(incoming)).thenReturn(incoming);
        // What a disconnected calendar does: hands the booking's own event id straight back.
        when(googleCalendarService.syncBooking(incoming))
                .thenAnswer(res -> ((InspectionBookings) res.getArgument(0)).getGoogleEventId());

        inspectionBookingsService.updateBooking(id, incoming);

        assertThat(incoming.getInspectionNumber()).isEqualTo(1042);
        assertThat(incoming.getGoogleEventId()).isEqualTo("google-event-1");
    }

    @Test
    void updateBooking_impossibleDate_throwsInsteadOfSaving() {
        UUID id = UUID.randomUUID();
        InspectionBookings booking = new InspectionBookings();
        booking.setMonth("April");
        booking.setDay(31);
        booking.setYear(2026);

        assertThatThrownBy(() -> inspectionBookingsService.updateBooking(id, booking))
                .isInstanceOf(IllegalArgumentException.class);

        verify(inspectionBookingsRepository, never()).save(any());
    }

    @Test
    void updateBooking_repositoryThrows_returnsBadRequest() {
        UUID id = UUID.randomUUID();
        InspectionBookings booking = new InspectionBookings();

        when(inspectionBookingsRepository.save(booking)).thenThrow(new RuntimeException("db failure"));

        ResponseEntity<?> result = inspectionBookingsService.updateBooking(id, booking);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // DELETE BOOKING

    @Test
    void deleteBooking_bookingExists_deletesAndReturnsOk() {
        UUID id = UUID.randomUUID();
        InspectionBookings booking = new InspectionBookings();
        booking.setId(id);

        when(inspectionBookingsRepository.findById(id)).thenReturn(Optional.of(booking));

        ResponseEntity<?> result = inspectionBookingsService.deleteBooking(id);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(inspectionBookingsRepository).delete(booking);
    }

    @Test
    void deleteBooking_removesTheCalendarEventFirst() {
        UUID id = UUID.randomUUID();
        InspectionBookings booking = new InspectionBookings();
        booking.setId(id);
        booking.setGoogleEventId("google-event-1");

        when(inspectionBookingsRepository.findById(id)).thenReturn(Optional.of(booking));

        inspectionBookingsService.deleteBooking(id);

        verify(googleCalendarService).deleteEvent(booking);
        verify(inspectionBookingsRepository).delete(booking);
    }

    @Test
    void deleteBooking_calendarFailure_stillDeletesTheBooking() {
        UUID id = UUID.randomUUID();
        InspectionBookings booking = new InspectionBookings();
        booking.setId(id);
        booking.setGoogleEventId("google-event-1");

        when(inspectionBookingsRepository.findById(id)).thenReturn(Optional.of(booking));
        doThrow(new RuntimeException("Google is down")).when(googleCalendarService).deleteEvent(booking);

        ResponseEntity<?> result = inspectionBookingsService.deleteBooking(id);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(inspectionBookingsRepository).delete(booking);
    }

    @Test
    void deleteBooking_bookingNotFound_returnsBadRequest() {
        UUID id = UUID.randomUUID();

        when(inspectionBookingsRepository.findById(id)).thenReturn(Optional.empty());

        ResponseEntity<?> result = inspectionBookingsService.deleteBooking(id);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verify(inspectionBookingsRepository, never()).delete(any());
    }
}
