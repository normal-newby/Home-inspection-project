package ca.inspection.home.inspection.integration;

import ca.inspection.home.inspection.DTO.AnalyticsRow;
import ca.inspection.home.inspection.config.FeeCentsMigration;
import ca.inspection.home.inspection.config.InspectionDateBackfill;
import ca.inspection.home.inspection.entity.InspectionBookings;
import ca.inspection.home.inspection.entity.Invoice;
import ca.inspection.home.inspection.entity.InvoiceDefinition;
import ca.inspection.home.inspection.repository.InspectionBookingsRepository;
import ca.inspection.home.inspection.repository.InvoiceDefinitionRepository;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Tag("integration")
@Import({FeeCentsMigration.class, InspectionDateBackfill.class})
public class AnalyticsIT {

    @Autowired
    private TestEntityManager em;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private InspectionBookingsRepository bookingsRepository;

    @Autowired
    private InvoiceDefinitionRepository invoiceDefinitionRepository;

    @Autowired
    private FeeCentsMigration feeCentsMigration;

    @Autowired
    private InspectionDateBackfill inspectionDateBackfill;

    private InspectionBookings persistBooking(LocalDate date, String... fees) {
        InspectionBookings booking = new InspectionBookings();
        booking.setInspectionDate(date);
        em.persist(booking);
        for (String fee : fees) {
            Invoice invoice = new Invoice();
            invoice.setType("Inspection");
            invoice.setFee(new BigDecimal(fee));
            invoice.setBookings(booking);
            em.persist(invoice);
        }
        em.flush();
        return booking;
    }

    @Test
    void findAnalyticsRows_returnsOneRowPerInvoiceInsideTheRangeOnly() {
        persistBooking(LocalDate.of(2026, 1, 1), "400.00", "99.99");
        persistBooking(LocalDate.of(2026, 3, 31), "280.00");
        persistBooking(LocalDate.of(2025, 12, 31), "500.00");
        persistBooking(LocalDate.of(2026, 4, 1), "500.00");
        persistBooking(null, "500.00");
        em.clear();

        List<AnalyticsRow> rows = bookingsRepository.findAnalyticsRows(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31));

        assertThat(rows).extracting(AnalyticsRow::fee)
                .containsExactlyInAnyOrder(new BigDecimal("400.00"), new BigDecimal("99.99"), new BigDecimal("280.00"));
    }

    @Test
    void findAnalyticsRows_keepsBookingsThatHaveNoInvoices() {
        InspectionBookings booking = persistBooking(LocalDate.of(2026, 2, 1));
        em.clear();

        List<AnalyticsRow> rows = bookingsRepository.findAnalyticsRows(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31));

        assertThat(rows).singleElement().satisfies(row -> {
            assertThat(row.bookingId()).isEqualTo(booking.getId());
            assertThat(row.fee()).isNull();
            assertThat(row.invoiceType()).isNull();
        });
    }

    @Test
    void fees_areStoredAsWholeCents() {
        persistBooking(LocalDate.of(2026, 2, 1), "299.99");

        assertThat(jdbcTemplate.queryForObject("SELECT fee_cents FROM invoice", Long.class)).isEqualTo(29999L);
    }

    @Test
    void feeCentsMigration_movesLegacyFloatFeesAndDropsTheOldColumn() {
        for (String table : List.of("invoice", "invoice_definition")) {
            jdbcTemplate.execute("ALTER TABLE " + table + " ADD COLUMN fee float");
        }
        jdbcTemplate.update("INSERT INTO invoice (id, type, fee) VALUES (randomblob(16), 'Condo', 280.10000000000002)");
        jdbcTemplate.update("INSERT INTO invoice (id, type, fee) VALUES (randomblob(16), 'Detached', 475)");
        jdbcTemplate.update("INSERT INTO invoice_definition (id, type, fee) VALUES (randomblob(16), 'Pre-offer', 199.99)");

        feeCentsMigration.run(null);

        assertThat(jdbcTemplate.queryForList("SELECT fee_cents FROM invoice ORDER BY fee_cents", Long.class))
                .containsExactly(28010L, 47500L);
        assertThat(jdbcTemplate.queryForObject("SELECT fee_cents FROM invoice_definition", Long.class)).isEqualTo(19999L);
        assertThat(jdbcTemplate.queryForList("SELECT name FROM pragma_table_info('invoice')", String.class))
                .doesNotContain("fee");

        em.clear();
        List<InvoiceDefinition> definitions = invoiceDefinitionRepository.findAll();
        assertThat(definitions).singleElement().extracting(InvoiceDefinition::getFee).isEqualTo(new BigDecimal("199.99"));
    }

    @Test
    void feeCentsMigration_onACurrentSchema_doesNothing() {
        persistBooking(LocalDate.of(2026, 2, 1), "425.00");

        feeCentsMigration.run(null);

        assertThat(jdbcTemplate.queryForObject("SELECT fee_cents FROM invoice", Long.class)).isEqualTo(42500L);
    }

    @Test
    void inspectionDateBackfill_rewritesDatesStoredAsEpochMillis() {
        InspectionBookings booking = new InspectionBookings();
        booking.setMonth("September");
        booking.setDay(1);
        booking.setYear(2026);
        em.persist(booking);
        em.flush();
        jdbcTemplate.update("UPDATE inspection_bookings SET inspection_date = 1788235200000");

        inspectionDateBackfill.run(null);
        em.flush();
        em.clear();

        assertThat(jdbcTemplate.queryForObject("SELECT inspection_date FROM inspection_bookings", String.class))
                .isEqualTo("2026-09-01");
    }

    @Test
    void inspectionDateBackfill_fillsDatesFromMonthDayYear_andSkipsUnreadableOnes() {
        InspectionBookings dated = new InspectionBookings();
        dated.setMonth("September");
        dated.setDay(3);
        dated.setYear(2026);
        em.persist(dated);

        InspectionBookings impossible = new InspectionBookings();
        impossible.setMonth("February");
        impossible.setDay(30);
        impossible.setYear(2026);
        em.persist(impossible);

        InspectionBookings undated = new InspectionBookings();
        em.persist(undated);
        em.flush();

        inspectionDateBackfill.run(null);
        em.flush();
        em.clear();

        assertThat(bookingsRepository.findById(dated.getId()).orElseThrow().getInspectionDate())
                .isEqualTo(LocalDate.of(2026, 9, 3));
        // Plain ISO text, so the stored day doesn't depend on the JVM's time zone.
        assertThat(jdbcTemplate.queryForList("SELECT inspection_date FROM inspection_bookings WHERE inspection_date IS NOT NULL", String.class))
                .containsExactly("2026-09-03");
        assertThat(bookingsRepository.findById(impossible.getId()).orElseThrow().getInspectionDate()).isNull();
        assertThat(bookingsRepository.countByInspectionDateIsNull()).isEqualTo(2);
    }
}
