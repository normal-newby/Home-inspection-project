package ca.inspection.home.inspection.service;

import ca.inspection.home.inspection.DTO.AnalyticsReport;
import ca.inspection.home.inspection.DTO.AnalyticsReport.MonthTotals;
import ca.inspection.home.inspection.DTO.AnalyticsReport.ServiceTotals;
import ca.inspection.home.inspection.DTO.AnalyticsReport.Totals;
import ca.inspection.home.inspection.DTO.AnalyticsRow;
import ca.inspection.home.inspection.DTO.InvoiceAmount;
import ca.inspection.home.inspection.entity.BookingStatus;
import ca.inspection.home.inspection.repository.InspectionBookingsRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class AnalyticsService {
    private static final int MAX_MONTHS = 120;
    private static final String UNSPECIFIED_SERVICE = "Unspecified";

    public enum PaymentFilter { ALL, PAID, UNPAID }

    @Autowired
    private InspectionBookingsRepository inspectionBookingsRepository;

    public AnalyticsReport build(YearMonth from, YearMonth to, String status, String payment){
        if (from.isAfter(to)) {
            throw new IllegalArgumentException("The start month has to be on or before the end month.");
        }
        if (from.until(to, ChronoUnit.MONTHS) >= MAX_MONTHS) {
            throw new IllegalArgumentException("Pick a range of " + MAX_MONTHS / 12 + " years or less.");
        }
        BookingStatus wantedStatus = parseStatus(status);
        PaymentFilter wantedPayment = parsePayment(payment);

        Map<YearMonth, Tally> months = new LinkedHashMap<>();
        for (YearMonth month = from; !month.isAfter(to); month = month.plusMonths(1)) {
            months.put(month, new Tally());
        }
        Tally overall = new Tally();
        Map<String, ServiceTally> services = new LinkedHashMap<>();

        for (Booking booking : groupByBooking(inspectionBookingsRepository.findAnalyticsRows(from.atDay(1), to.atEndOfMonth()))) {
            if (wantedStatus != null && booking.status != wantedStatus) continue;
            if (wantedPayment == PaymentFilter.PAID && !booking.paid) continue;
            if (wantedPayment == PaymentFilter.UNPAID && booking.paid) continue;

            BigDecimal subtotal = BigDecimal.ZERO;
            for (AnalyticsRow line : booking.lines) {
                BigDecimal fee = line.fee() == null ? BigDecimal.ZERO : line.fee();
                subtotal = subtotal.add(fee);
                String type = line.invoiceType() == null || line.invoiceType().isBlank()
                        ? UNSPECIFIED_SERVICE : line.invoiceType().trim();
                services.computeIfAbsent(type, key -> new ServiceTally()).add(fee);
            }
            InvoiceAmount amount = InspectionBookingsService.invoiceAmount(subtotal, booking.removeTax);
            BigDecimal tax = amount.getHst().add(amount.getGst());

            months.get(YearMonth.from(booking.date)).add(amount.getSubtotal(), tax);
            overall.add(amount.getSubtotal(), tax);
        }

        List<MonthTotals> monthTotals = months.entrySet().stream()
                .map(entry -> {
                    Tally tally = entry.getValue();
                    return new MonthTotals(entry.getKey().toString(), tally.inspections, tally.revenue, tally.tax, tally.averageFee());
                })
                .toList();

        List<ServiceTotals> serviceTotals = services.entrySet().stream()
                .map(entry -> new ServiceTotals(entry.getKey(), entry.getValue().count, entry.getValue().revenue))
                .sorted(Comparator.comparing(ServiceTotals::revenue).reversed()
                        .thenComparing(Comparator.comparingInt(ServiceTotals::count).reversed())
                        .thenComparing(ServiceTotals::type))
                .toList();

        return new AnalyticsReport(
                from.toString(),
                to.toString(),
                new Totals(overall.inspections, overall.revenue, overall.tax, overall.averageFee()),
                monthTotals,
                serviceTotals,
                inspectionBookingsRepository.countByInspectionDateIsNull()
        );
    }

    // "ALL" (or nothing) means no status filter.
    static BookingStatus parseStatus(String raw){
        if (raw == null || raw.isBlank() || raw.trim().equalsIgnoreCase("ALL")) return null;
        return BookingStatus.parse(raw);
    }

    static PaymentFilter parsePayment(String raw){
        if (raw == null || raw.isBlank()) return PaymentFilter.ALL;
        try {
            return PaymentFilter.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("\"" + raw + "\" is not a payment filter.");
        }
    }

    private static List<Booking> groupByBooking(List<AnalyticsRow> rows){
        Map<UUID, Booking> bookings = new LinkedHashMap<>();
        for (AnalyticsRow row : rows) {
            Booking booking = bookings.computeIfAbsent(row.bookingId(), id -> new Booking(row));
            // The left join gives one all-null invoice row for a booking with no invoices.
            if (row.fee() != null || row.invoiceType() != null) booking.lines.add(row);
        }
        return new ArrayList<>(bookings.values());
    }

    private static final class Booking {
        final LocalDate date;
        final BookingStatus status;
        final boolean paid;
        final boolean removeTax;
        final List<AnalyticsRow> lines = new ArrayList<>();

        Booking(AnalyticsRow row){
            date = row.inspectionDate();
            status = BookingStatus.orDefault(row.status());
            paid = Boolean.TRUE.equals(row.paidInFull());
            removeTax = Boolean.TRUE.equals(row.removeTax());
        }
    }

    private static final class Tally {
        int inspections;
        BigDecimal revenue = BigDecimal.ZERO.setScale(2);
        BigDecimal tax = BigDecimal.ZERO.setScale(2);

        void add(BigDecimal subtotal, BigDecimal taxAmount){
            inspections++;
            revenue = revenue.add(subtotal);
            tax = tax.add(taxAmount);
        }

        BigDecimal averageFee(){
            return inspections == 0 ? null : revenue.divide(BigDecimal.valueOf(inspections), 2, RoundingMode.HALF_UP);
        }
    }

    private static final class ServiceTally {
        int count;
        BigDecimal revenue = BigDecimal.ZERO.setScale(2);

        void add(BigDecimal fee){
            count++;
            revenue = revenue.add(fee);
        }
    }
}
