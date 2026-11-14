package ca.inspection.home.inspection.DTO;

import java.math.BigDecimal;
import java.util.UUID;

public record InvoiceRow(UUID bookingId, String type, BigDecimal fee) {}
