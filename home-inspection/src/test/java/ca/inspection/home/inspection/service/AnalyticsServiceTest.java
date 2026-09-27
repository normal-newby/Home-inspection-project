package ca.inspection.home.inspection.service;

import ca.inspection.home.inspection.DTO.AnalyticsReport;
import ca.inspection.home.inspection.DTO.AnalyticsReport.MonthTotals;
import ca.inspection.home.inspection.DTO.AnalyticsReport.ServiceTotals;
import ca.inspection.home.inspection.DTO.AnalyticsRow;
import ca.inspection.home.inspection.entity.BookingStatus;
import ca.inspection.home.inspection.repository.InspectionBookingsRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class AnalyticsServiceTest {

    private static final YearMonth JAN = YearMonth.of(2026, 1);
    private static final YearMonth MAR = YearMonth.of(2026, 3);

    @Mock
    private InspectionBookingsRepository inspectionBookingsRepository;

    @InjectMocks
    private AnalyticsService analyticsService;

    private static AnalyticsRow line(UUID booking, LocalDate date, BookingStatus status, Boolean paid,
                                     Boolean removeTax, String type, String fee) {
        return new AnalyticsRow(booking, date, status, paid, removeTax, type, fee == null ? null : new BigDecimal(fee));
    }

    private void givenRows(AnalyticsRow... rows) {
        when(inspectionBookingsRepository.findAnalyticsRows(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31)))
                .thenReturn(List.of(rows));
    }

    private AnalyticsReport build(String status, String payment) {
        return analyticsService.build(JAN, MAR, status, payment);
    }

    @Test
    void noBookings_everyMonthIsListedWithZeroes() {
        givenRows();

        AnalyticsReport report = build("ALL", "ALL");

        assertThat(report.months()).extracting(MonthTotals::month).containsExactly("2026-01", "2026-02", "2026-03");
        assertThat(report.months()).allSatisfy(month -> {
            assertThat(month.inspections()).isZero();
            assertThat(month.revenue()).isEqualByComparingTo("0");
            assertThat(month.averageFee()).isNull();
        });
        assertThat(report.totals().inspections()).isZero();
        assertThat(report.totals().averageFee()).isNull();
        assertThat(report.services()).isEmpty();
    }

    @Test
    void revenueIsThePreTaxSubtotal_andTaxIsReportedSeparately() {
        UUID booking = UUID.randomUUID();
        givenRows(
                line(booking, LocalDate.of(2026, 2, 10), BookingStatus.COMPLETED, true, false, "Detached", "400.00"),
                line(booking, LocalDate.of(2026, 2, 10), BookingStatus.COMPLETED, true, false, "Radon", "100.00"));

        AnalyticsReport report = build("ALL", "ALL");

        MonthTotals feb = report.months().get(1);
        // Two invoice lines on one booking are one inspection.
        assertThat(feb.inspections()).isEqualTo(1);
        assertThat(feb.revenue()).isEqualByComparingTo("500.00");
        assertThat(feb.tax()).isEqualByComparingTo("65.00");
        assertThat(feb.averageFee()).isEqualByComparingTo("500.00");
        assertThat(report.totals().revenue()).isEqualByComparingTo("500.00");
        assertThat(report.totals().tax()).isEqualByComparingTo("65.00");
    }

    @Test
    void removeTax_contributesNoTax() {
        givenRows(line(UUID.randomUUID(), LocalDate.of(2026, 1, 5), null, false, true, "Condo", "300.00"));

        AnalyticsReport report = build("ALL", "ALL");

        assertThat(report.totals().revenue()).isEqualByComparingTo("300.00");
        assertThat(report.totals().tax()).isEqualByComparingTo("0");
    }

    @Test
    void averageFee_isRevenuePerInspectionRoundedToTheCent() {
        givenRows(
                line(UUID.randomUUID(), LocalDate.of(2026, 1, 5), null, true, false, "A", "100.00"),
                line(UUID.randomUUID(), LocalDate.of(2026, 1, 6), null, true, false, "A", "100.00"),
                line(UUID.randomUUID(), LocalDate.of(2026, 1, 7), null, true, false, "A", "200.00"));

        AnalyticsReport report = build("ALL", "ALL");

        assertThat(report.months().get(0).inspections()).isEqualTo(3);
        assertThat(report.months().get(0).averageFee()).isEqualByComparingTo("133.33");
    }

    @Test
    void bookingWithNoInvoices_countsAsAnInspectionWithNoRevenue() {
        givenRows(line(UUID.randomUUID(), LocalDate.of(2026, 3, 1), null, null, null, null, null));

        AnalyticsReport report = build("ALL", "ALL");

        assertThat(report.totals().inspections()).isEqualTo(1);
        assertThat(report.totals().revenue()).isEqualByComparingTo("0");
        assertThat(report.services()).isEmpty();
    }

    @Test
    void statusFilter_keepsOnlyThatStatus_andTreatsNullAsScheduled() {
        givenRows(
                line(UUID.randomUUID(), LocalDate.of(2026, 1, 5), BookingStatus.COMPLETED, true, false, "A", "400.00"),
                line(UUID.randomUUID(), LocalDate.of(2026, 1, 6), null, true, false, "A", "300.00"),
                line(UUID.randomUUID(), LocalDate.of(2026, 1, 7), BookingStatus.IN_PROGRESS, true, false, "A", "200.00"));

        assertThat(build("COMPLETED", "ALL").totals().revenue()).isEqualByComparingTo("400.00");
        assertThat(build("SCHEDULED", "ALL").totals().revenue()).isEqualByComparingTo("300.00");
        assertThat(build("all", "ALL").totals().inspections()).isEqualTo(3);
    }

    @Test
    void paymentFilter_unpaidIncludesBookingsNeverMarkedPaid() {
        givenRows(
                line(UUID.randomUUID(), LocalDate.of(2026, 1, 5), null, true, false, "A", "400.00"),
                line(UUID.randomUUID(), LocalDate.of(2026, 1, 6), null, false, false, "A", "300.00"),
                line(UUID.randomUUID(), LocalDate.of(2026, 1, 7), null, null, false, "A", "200.00"));

        assertThat(build("ALL", "PAID").totals().revenue()).isEqualByComparingTo("400.00");
        assertThat(build("ALL", "unpaid").totals().revenue()).isEqualByComparingTo("500.00");
    }

    @Test
    void serviceMix_groupsByType_largestRevenueFirst() {
        UUID first = UUID.randomUUID();
        givenRows(
                line(first, LocalDate.of(2026, 1, 5), null, true, false, "Condo", "300.00"),
                line(first, LocalDate.of(2026, 1, 5), null, true, false, "  ", "50.00"),
                line(UUID.randomUUID(), LocalDate.of(2026, 2, 5), null, true, false, "Detached", "475.00"),
                line(UUID.randomUUID(), LocalDate.of(2026, 3, 5), null, true, false, "Condo", "280.00"));

        List<ServiceTotals> services = build("ALL", "ALL").services();

        assertThat(services).extracting(ServiceTotals::type).containsExactly("Condo", "Detached", "Unspecified");
        assertThat(services.get(0).count()).isEqualTo(2);
        assertThat(services.get(0).revenue()).isEqualByComparingTo("580.00");
    }

    @Test
    void servicesFollowTheFilters() {
        givenRows(
                line(UUID.randomUUID(), LocalDate.of(2026, 1, 5), BookingStatus.COMPLETED, true, false, "Condo", "300.00"),
                line(UUID.randomUUID(), LocalDate.of(2026, 1, 6), null, true, false, "Detached", "475.00"));

        assertThat(build("COMPLETED", "ALL").services()).extracting(ServiceTotals::type).containsExactly("Condo");
    }

    @Test
    void reportsHowManyBookingsHaveNoDate() {
        givenRows();
        when(inspectionBookingsRepository.countByInspectionDateIsNull()).thenReturn(2L);

        assertThat(build("ALL", "ALL").undatedBookings()).isEqualTo(2);
    }

    @Test
    void startAfterEnd_isRejected() {
        assertThatThrownBy(() -> analyticsService.build(MAR, JAN, "ALL", "ALL"))
                .isInstanceOf(IllegalArgumentException.class);
        verify(inspectionBookingsRepository, never()).findAnalyticsRows(any(), any());
    }

    @Test
    void rangeOverTenYears_isRejected() {
        assertThatThrownBy(() -> analyticsService.build(YearMonth.of(2016, 1), YearMonth.of(2026, 1), "ALL", "ALL"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void unknownFilters_areRejected() {
        assertThatThrownBy(() -> build("CANCELLED", "ALL")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> build("ALL", "SOMETIMES")).isInstanceOf(IllegalArgumentException.class);
    }
}
