package ca.inspection.home.inspection.config;

import ca.inspection.home.inspection.entity.InspectionBookings;
import ca.inspection.home.inspection.repository.InspectionBookingsRepository;
import ca.inspection.home.inspection.service.BookingSchedule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

// Fills inspection_date for bookings saved before the column existed
@Slf4j
@Component
public class InspectionDateBackfill implements ApplicationRunner {

    @Autowired
    private InspectionBookingsRepository inspectionBookingsRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        // An early build stored epoch millis; clear those so they're recomputed as ISO text.
        int stale = jdbcTemplate.update("UPDATE inspection_bookings SET inspection_date = NULL "
                + "WHERE inspection_date NOT GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]'");
        if (stale > 0) log.info("Cleared {} inspection date(s) in the old format", stale);

        List<InspectionBookings> missing = inspectionBookingsRepository.findByInspectionDateIsNullAndMonthIsNotNull();
        int filled = 0;
        for (InspectionBookings booking : missing) {
            try {
                BookingSchedule schedule = BookingSchedule.of(booking);
                if (schedule == null) continue;
                // Managed entity, so the change is flushed when the transaction commits.
                booking.setInspectionDate(schedule.date());
                filled++;
            } catch (IllegalArgumentException e) {
                log.warn("Booking {} has an unreadable date, left out of analytics: {}", booking.getId(), e.getMessage());
            }
        }
        if (filled > 0) log.info("Filled inspection date on {} booking(s)", filled);
    }
}
