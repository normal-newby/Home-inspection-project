package ca.inspection.home.inspection.integration;

import ca.inspection.home.inspection.DTO.BookingDetails;
import ca.inspection.home.inspection.entity.InspectionBookings;
import ca.inspection.home.inspection.entity.InspectionReport;
import ca.inspection.home.inspection.entity.Invoice;
import ca.inspection.home.inspection.repository.InspectionBookingsRepository;
import ca.inspection.home.inspection.repository.InspectionReportsRepository;
import ca.inspection.home.inspection.repository.InvoiceRepository;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

// @DataJpaTest boots JPA + Hibernate + the DataSource, nothing else. Each test
// runs in a transaction that's rolled back at the end, so nothing leaks.
// Replace.NONE tells Spring not to swap our SQLite DataSource for H2.
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Tag("integration")
public class InspectionBookingsRepositoryIT {

    @Autowired
    private TestEntityManager em;

    @Autowired
    private InspectionBookingsRepository bookingsRepository;

    @Autowired
    private InspectionReportsRepository reportsRepository;

    @Autowired
    private InvoiceRepository invoiceRepository;

    private InspectionBookings persistBooking(String address, BigDecimal... invoiceFees) {
        InspectionBookings booking = new InspectionBookings();
        booking.setInspectionAddress(address);
        booking.setClientFirstName("Jane");
        booking.setClientLastName("Doe");
        em.persist(booking);

        for (BigDecimal fee : invoiceFees) {
            Invoice invoice = new Invoice();
            invoice.setType("Inspection");
            invoice.setFee(fee);
            invoice.setBookings(booking);
            em.persist(invoice);
        }
        em.flush();
        return booking;
    }

    @Test
    void findBookingDetails_returnsProjectionWithInvoicesEagerlyFetched() {
        persistBooking("100 Main St", new BigDecimal("400.00"), new BigDecimal("100.00"));
        em.clear();

        List<BookingDetails> result = bookingsRepository.findBookingDetailsByOrderByCreatedAtDesc();

        assertThat(result).hasSize(1);
        BookingDetails details = result.getFirst();
        assertThat(details.getInspectionAddress()).isEqualTo("100 Main St");
        assertThat(details.getClientFirstName()).isEqualTo("Jane");

        assertThat(details.getInvoices()).hasSize(2);
        assertThat(details.getInvoices().stream()
                .map(Invoice::getFee)
                .map(BigDecimal::doubleValue))
                .containsExactlyInAnyOrder(400.0, 100.0);
    }

    @Test
    void findBookingDetailsByOrderByCreatedAtDesc_ordersByCreatedAtDesc() throws InterruptedException {
        InspectionBookings older = persistBooking("1 Old Rd");
        Thread.sleep(20); // CreationTimestamp uses ms precision
        InspectionBookings newer = persistBooking("2 New Rd");

        List<BookingDetails> result = bookingsRepository.findBookingDetailsByOrderByCreatedAtDesc();

        assertThat(result)
                .extracting(BookingDetails::getInspectionAddress)
                .containsExactly("2 New Rd", "1 Old Rd");
    }

    @Test
    void getBookingDetails_byId_returnsMatchingBookingOnly() {
        InspectionBookings target = persistBooking("42 Wanted Ln", new BigDecimal("500.00"));
        persistBooking("99 Ignored Pl", new BigDecimal("200.00"));
        // Detach so the projection actually goes to the DB rather than reading
        // the persistence-context cache — otherwise Spring returns null here.
        em.clear();

        BookingDetails result = bookingsRepository.getBookingDetails(target.getId());

        assertThat(result).isNotNull();
        assertThat(result.getInspectionAddress()).isEqualTo("42 Wanted Ln");
        assertThat(result.getInvoices()).hasSize(1);
    }

    @Test
    void deleteBooking_cascadesToReportAndInvoices() {
        InspectionBookings booking = persistBooking("Cascade Test",
                new BigDecimal("100.00"), new BigDecimal("50.00"));

        InspectionReport report = new InspectionReport();
        report.setInspectionBooking(booking);
        report.setSummary("summary");
        em.persist(report);
        em.flush();
        em.clear();

        UUID bookingId = booking.getId();
        UUID reportId = report.getId();
        List<UUID> invoiceIds = invoiceRepository.findAll().stream().map(Invoice::getId).toList();

        bookingsRepository.deleteById(bookingId);
        em.flush();
        em.clear();

        assertThat(bookingsRepository.findById(bookingId)).isEmpty();
        assertThat(reportsRepository.findById(reportId)).isEmpty();
        invoiceIds.forEach(id -> assertThat(invoiceRepository.findById(id)).isEmpty());
    }
}
