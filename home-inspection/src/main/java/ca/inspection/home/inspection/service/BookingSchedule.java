package ca.inspection.home.inspection.service;

import ca.inspection.home.inspection.entity.InspectionBookings;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.Month;
import java.time.format.TextStyle;
import java.util.Locale;

public record BookingSchedule(LocalDate date, LocalTime startTime, int durationMinutes) {

    public static final int DEFAULT_DURATION_MINUTES = 180;

    private static final int MIN_YEAR = 1900;
    private static final int MAX_YEAR = 2200;

    public static BookingSchedule of(InspectionBookings booking) {
        String rawMonth = booking.getMonth();
        Integer day = booking.getDay();
        Integer year = booking.getYear();

        boolean hasMonth = rawMonth != null && !rawMonth.isBlank();
        boolean noneSet = !hasMonth && day == null && year == null;
        if (noneSet) {
            requireNoTimeWithoutDate(booking);
            return null;
        }
        if (!hasMonth || day == null || year == null) {
            throw new IllegalArgumentException(
                    "Enter a complete inspection date — month, day and year are all required.");
        }

        Month month = parseMonth(rawMonth);
        if (year < MIN_YEAR || year > MAX_YEAR) {
            throw new IllegalArgumentException(
                    "Inspection year must be between " + MIN_YEAR + " and " + MAX_YEAR + ".");
        }

        LocalDate date;
        try {
            date = LocalDate.of(year, month, day);
        } catch (DateTimeException e) {
            throw new IllegalArgumentException(
                    month.getDisplayName(TextStyle.FULL, Locale.CANADA) + " " + day + ", " + year
                            + " is not a valid date.");
        }

        return new BookingSchedule(date, parseTime(booking.getStartTime()), parseDuration(booking));
    }

    public boolean allDay() {
        return startTime == null;
    }

    public LocalDateTime start() {
        return startTime == null ? date.atStartOfDay() : date.atTime(startTime);
    }

    public LocalDateTime end() {
        return start().plusMinutes(durationMinutes);
    }

    private static Month parseMonth(String raw) {
        String value = raw.trim();

        // The form sends full English names, accept number too
        if (value.chars().allMatch(Character::isDigit)) {
            int number = Integer.parseInt(value);
            if (number < 1 || number > 12) {
                throw new IllegalArgumentException("\"" + raw + "\" is not a valid month.");
            }
            return Month.of(number);
        }

        for (Month candidate : Month.values()) {
            String full = candidate.getDisplayName(TextStyle.FULL, Locale.CANADA);
            String shortName = candidate.getDisplayName(TextStyle.SHORT, Locale.CANADA);
            if (full.equalsIgnoreCase(value) || shortName.equalsIgnoreCase(value)) {
                return candidate;
            }
        }
        throw new IllegalArgumentException("\"" + raw + "\" is not a valid month.");
    }

    private static LocalTime parseTime(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            // <input type="time"> sends "HH:mm", and "HH:mm:ss" when seconds are enabled.
            return LocalTime.parse(raw.trim().length() == 5 ? raw.trim() : raw.trim().substring(0, 5));
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("\"" + raw + "\" is not a valid start time (use HH:mm).");
        }
    }

    private static int parseDuration(InspectionBookings booking) {
        Integer minutes = booking.getDurationMinutes();
        if (minutes == null) return DEFAULT_DURATION_MINUTES;
        if (minutes < 1 || minutes > 24 * 60) {
            throw new IllegalArgumentException("Inspection length must be between 1 minute and 24 hours.");
        }
        return minutes;
    }

    private static void requireNoTimeWithoutDate(InspectionBookings booking) {
        if (booking.getStartTime() != null && !booking.getStartTime().isBlank()) {
            throw new IllegalArgumentException("Pick an inspection date before setting a start time.");
        }
    }
}
