package ca.inspection.home.inspection.DTO;

import ca.inspection.home.inspection.service.InspectionBookingsService;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

// A booking with its clients and invoice lines, put together from three flat queries.
public record BookingSummary(BookingRow booking, List<ClientRow> clients, List<InvoiceRow> invoices) {

    public static List<BookingSummary> assemble(List<BookingRow> bookings, List<ClientRow> clients, List<InvoiceRow> invoices){
        Map<UUID, List<ClientRow>> clientsByBooking = clients.stream().collect(Collectors.groupingBy(ClientRow::bookingId));
        Map<UUID, List<InvoiceRow>> invoicesByBooking = invoices.stream().collect(Collectors.groupingBy(InvoiceRow::bookingId));

        return bookings.stream()
                .map(booking -> new BookingSummary(
                        booking,
                        clientsByBooking.getOrDefault(booking.id(), List.of()),
                        invoicesByBooking.getOrDefault(booking.id(), List.of())))
                .toList();
    }

    public InvoiceAmount amount(){
        BigDecimal subtotal = invoices.stream()
                .map(InvoiceRow::fee)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return InspectionBookingsService.invoiceAmount(subtotal, Boolean.TRUE.equals(booking.removeTax()));
    }
}
