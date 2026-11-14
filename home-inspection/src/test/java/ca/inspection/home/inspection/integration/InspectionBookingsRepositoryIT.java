package ca.inspection.home.inspection.integration;

import ca.inspection.home.inspection.DTO.BookingDetails;
import ca.inspection.home.inspection.DTO.BookingListItem;
import ca.inspection.home.inspection.DTO.BookingSummary;
import ca.inspection.home.inspection.DTO.ClientRow;
import ca.inspection.home.inspection.entity.Client;
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
import java.time.LocalDate;
import java.util.ArrayList;
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
        booking.setClients(new ArrayList<>(List.of(new Client(null, "Jane", "Doe", null, null, 0, booking))));
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

    private static final LocalDate FIRST = LocalDate.of(1, 1, 1);
    private static final LocalDate LAST = LocalDate.of(9999, 12, 31);

    private List<BookingSummary> summaries(LocalDate from, LocalDate to, boolean undated) {
        return BookingSummary.assemble(
                bookingsRepository.findRows(from, to, undated),
                bookingsRepository.findClientRows(from, to, undated),
                bookingsRepository.findInvoiceRows(from, to, undated));
    }

    @Test
    void listQueries_returnEachBookingWithItsClientsAndInvoices() {
        persistBooking("100 Main St", new BigDecimal("400.00"), new BigDecimal("100.00"));
        persistBooking("200 Side St");
        em.clear();

        List<BookingSummary> result = summaries(FIRST, LAST, true);

        assertThat(result).hasSize(2);
        BookingSummary main = byAddress(result, "100 Main St");
        assertThat(main.clients()).extracting(ClientRow::firstName).containsExactly("Jane");
        assertThat(main.invoices()).extracting(invoice -> invoice.fee().doubleValue())
                .containsExactlyInAnyOrder(400.0, 100.0);
        assertThat(byAddress(result, "200 Side St").invoices()).isEmpty();
    }

    @Test
    void listQueries_exposeTaxedPrice() {
        persistBooking("1 Taxed Rd", new BigDecimal("400.00"), new BigDecimal("100.00"));
        InspectionBookings untaxed = persistBooking("2 Untaxed Rd", new BigDecimal("500.00"));
        untaxed.setRemoveTax(true);
        persistBooking("3 Unbilled Rd");
        em.flush();
        em.clear();

        List<BookingSummary> result = summaries(FIRST, LAST, true);

        assertThat(BookingListItem.of(byAddress(result, "1 Taxed Rd")).price()).isEqualByComparingTo("565.00");
        assertThat(BookingListItem.of(byAddress(result, "2 Untaxed Rd")).price()).isEqualByComparingTo("500.00");
        assertThat(BookingListItem.of(byAddress(result, "3 Unbilled Rd")).price()).isNull();
    }

    private static BookingSummary byAddress(List<BookingSummary> bookings, String address) {
        return bookings.stream().filter(b -> address.equals(b.booking().inspectionAddress())).findFirst().orElseThrow();
    }

    @Test
    void listQueries_keepToTheDateRangeAndOnlyIncludeUndatedWhenAsked() {
        InspectionBookings old = persistBooking("1 Old Rd", new BigDecimal("100.00"));
        old.setInspectionDate(LocalDate.of(2025, 1, 10));
        InspectionBookings recent = persistBooking("2 Recent Rd", new BigDecimal("200.00"));
        recent.setInspectionDate(LocalDate.of(2026, 6, 1));
        persistBooking("3 Undated Rd", new BigDecimal("300.00"));
        em.flush();
        em.clear();

        LocalDate from = LocalDate.of(2026, 1, 1);

        assertThat(summaries(from, LAST, true))
                .extracting(summary -> summary.booking().inspectionAddress())
                .containsExactlyInAnyOrder("2 Recent Rd", "3 Undated Rd");
        assertThat(summaries(from, LAST, false))
                .extracting(summary -> summary.booking().inspectionAddress())
                .containsExactly("2 Recent Rd");
        // Clients and invoices follow the same range as the bookings.
        assertThat(bookingsRepository.findInvoiceRows(from, LAST, false)).hasSize(1);
        assertThat(bookingsRepository.findClientRows(from, LAST, false)).hasSize(1);
        assertThat(bookingsRepository.countByInspectionDateBefore(from)).isEqualTo(1);
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
