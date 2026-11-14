package ca.inspection.home.inspection.DTO;

import ca.inspection.home.inspection.entity.BookingStatus;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

// One row of the home page's bookings table.
public record BookingListItem(
        UUID id,
        Integer inspectionNumber,
        String inspectionAddress,
        String city,
        String postalCode,
        List<ClientName> clients,
        String month,
        Integer day,
        Integer year,
        String startTime,
        BookingStatus status,
        // Invoice total with tax; null when nothing has been invoiced
        BigDecimal price
) {
    public record ClientName(String firstName, String lastName) {}

    public static BookingListItem of(BookingSummary summary){
        BookingRow booking = summary.booking();
        return new BookingListItem(
                booking.id(),
                booking.inspectionNumber(),
                booking.inspectionAddress(),
                booking.city(),
                booking.postalCode(),
                summary.clients().stream().map(client -> new ClientName(client.firstName(), client.lastName())).toList(),
                booking.month(),
                booking.day(),
                booking.year(),
                booking.startTime(),
                BookingStatus.orDefault(booking.status()),
                summary.invoices().isEmpty() ? null : summary.amount().getTotal()
        );
    }
}
