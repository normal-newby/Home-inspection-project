package ca.inspection.home.inspection.DTO;

import java.util.List;

// olderCount: bookings left out because they are older than the home page's default window.
public record BookingList(List<BookingListItem> bookings, long olderCount) {}
