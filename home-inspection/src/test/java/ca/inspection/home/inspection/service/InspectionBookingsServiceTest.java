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
    void getBookingDetails_delegatesToRepository() {
        UUID id = UUID.randomUUID();
        BookingDetails details = mock(BookingDetails.class);

        when(inspectionBookingsRepository.getBookingDetails(id)).thenReturn(details);

        BookingDetails result = inspectionBookingsService.getBookingDetails(id);

        assertThat(result).isEqualTo(details);
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
    void deleteBooking_bookingNotFound_returnsBadRequest() {
        UUID id = UUID.randomUUID();

        when(inspectionBookingsRepository.findById(id)).thenReturn(Optional.empty());

        ResponseEntity<?> result = inspectionBookingsService.deleteBooking(id);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verify(inspectionBookingsRepository, never()).delete(any());
    }
}
