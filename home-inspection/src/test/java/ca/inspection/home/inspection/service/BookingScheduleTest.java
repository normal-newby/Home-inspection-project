package ca.inspection.home.inspection.service;

import ca.inspection.home.inspection.entity.InspectionBookings;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class BookingScheduleTest {

    private static InspectionBookings booking(String month, Integer day, Integer year) {
        InspectionBookings booking = new InspectionBookings();
        booking.setMonth(month);
        booking.setDay(day);
        booking.setYear(year);
        return booking;
    }

    // VALID DATES

    @Test
    void of_fullDate_resolvesToThatDay() {
        BookingSchedule schedule = BookingSchedule.of(booking("March", 12, 2026));

        assertThat(schedule.date()).isEqualTo(LocalDate.of(2026, 3, 12));
        assertThat(schedule.allDay()).isTrue();
        assertThat(schedule.durationMinutes()).isEqualTo(BookingSchedule.DEFAULT_DURATION_MINUTES);
    }

    @Test
    void of_leapDay_isAccepted() {
        assertThat(BookingSchedule.of(booking("February", 29, 2028)).date())
                .isEqualTo(LocalDate.of(2028, 2, 29));
    }

    @Test
    void of_shortMonthName_isAccepted() {
        assertThat(BookingSchedule.of(booking("Sep", 1, 2026)).date())
                .isEqualTo(LocalDate.of(2026, 9, 1));
    }

    @Test
    void of_numericMonth_isAccepted() {
        assertThat(BookingSchedule.of(booking("9", 1, 2026)).date())
                .isEqualTo(LocalDate.of(2026, 9, 1));
    }

    @Test
    void of_noDateAtAll_returnsNull() {
        // A booking can be taken before a date is agreed on.
        assertThat(BookingSchedule.of(booking(null, null, null))).isNull();
    }

    @Test
    void of_blankMonthOnly_returnsNull() {
        assertThat(BookingSchedule.of(booking("   ", null, null))).isNull();
    }

    // INVALID DATES

    @Test
    void of_dayThatMonthDoesNotHave_isRejected() {
        assertThatThrownBy(() -> BookingSchedule.of(booking("February", 30, 2026)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("February 30, 2026");
    }

    @Test
    void of_feb29InNonLeapYear_isRejected() {
        assertThatThrownBy(() -> BookingSchedule.of(booking("February", 29, 2026)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not a valid date");
    }

    @Test
    void of_partiallyFilledDate_isRejected() {
        assertThatThrownBy(() -> BookingSchedule.of(booking("March", null, 2026)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("complete inspection date");
    }

    @Test
    void of_unknownMonthName_isRejected() {
        assertThatThrownBy(() -> BookingSchedule.of(booking("Smarch", 1, 2026)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Smarch");
    }

    @Test
    void of_monthNumberOutOfRange_isRejected() {
        assertThatThrownBy(() -> BookingSchedule.of(booking("13", 1, 2026)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void of_yearOutOfRange_isRejected() {
        assertThatThrownBy(() -> BookingSchedule.of(booking("March", 1, 1500)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("year");
    }

    @Test
    void of_dayZero_isRejected() {
        assertThatThrownBy(() -> BookingSchedule.of(booking("March", 0, 2026)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // TIMES

    @Test
    void of_startTime_producesTimedWindow() {
        InspectionBookings booking = booking("March", 12, 2026);
        booking.setStartTime("09:30");
        booking.setDurationMinutes(150);

        BookingSchedule schedule = BookingSchedule.of(booking);

        assertThat(schedule.allDay()).isFalse();
        assertThat(schedule.startTime()).isEqualTo(LocalTime.of(9, 30));
        assertThat(schedule.start()).isEqualTo(LocalDate.of(2026, 3, 12).atTime(9, 30));
        assertThat(schedule.end()).isEqualTo(LocalDate.of(2026, 3, 12).atTime(12, 0));
    }

    @Test
    void of_blankStartTime_staysAllDay() {
        InspectionBookings booking = booking("March", 12, 2026);
        booking.setStartTime("");

        assertThat(BookingSchedule.of(booking).allDay()).isTrue();
    }

    @Test
    void of_malformedStartTime_isRejected() {
        InspectionBookings booking = booking("March", 12, 2026);
        booking.setStartTime("half past nine");

        assertThatThrownBy(() -> BookingSchedule.of(booking))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("start time");
    }

    @Test
    void of_timeWithoutDate_isRejected() {
        InspectionBookings booking = booking(null, null, null);
        booking.setStartTime("09:00");

        assertThatThrownBy(() -> BookingSchedule.of(booking))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("date");
    }

    @Test
    void of_durationBeyondADay_isRejected() {
        InspectionBookings booking = booking("March", 12, 2026);
        booking.setDurationMinutes(2000);

        assertThatThrownBy(() -> BookingSchedule.of(booking))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("length");
    }
}
