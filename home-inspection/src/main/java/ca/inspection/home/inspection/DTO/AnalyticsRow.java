package ca.inspection.home.inspection.DTO;

import ca.inspection.home.inspection.entity.BookingStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record AnalyticsRow(
        UUID bookingId,
        LocalDate inspectionDate,
        BookingStatus status,
        Boolean paidInFull,
        Boolean removeTax,
        String invoiceType,
        BigDecimal fee
) {}
