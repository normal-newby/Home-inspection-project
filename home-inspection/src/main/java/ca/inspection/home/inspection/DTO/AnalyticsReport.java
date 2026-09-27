package ca.inspection.home.inspection.DTO;

import java.math.BigDecimal;
import java.util.List;

// Revenue is the pre-tax subtotal
public record AnalyticsReport(
        String from,
        String to,
        Totals totals,
        List<MonthTotals> months,
        List<ServiceTotals> services,
        long undatedBookings
) {
    public record Totals(int inspections, BigDecimal revenue, BigDecimal tax, BigDecimal averageFee) {}

    public record MonthTotals(String month, int inspections, BigDecimal revenue, BigDecimal tax, BigDecimal averageFee) {}

    public record ServiceTotals(String type, int count, BigDecimal revenue) {}
}
