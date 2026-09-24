package ca.inspection.home.inspection.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

// Moves client columns to client table
@Slf4j
@Component
public class ClientMigration implements ApplicationRunner {
    private static final List<String> LEGACY_COLUMNS = List.of("client_first_name", "client_last_name", "email", "phone");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        List<String> present = jdbcTemplate.queryForList(
                "SELECT name FROM pragma_table_info('inspection_bookings')", String.class);
        if (!present.containsAll(LEGACY_COLUMNS)) return;

        // Ids are stored as 16-byte blobs, which is what randomblob(16) gives.
        int moved = jdbcTemplate.update("""
                INSERT INTO client (id, booking_id, first_name, last_name, email, phone, position)
                SELECT randomblob(16), b.id, b.client_first_name, b.client_last_name, b.email, b.phone, 0
                FROM inspection_bookings b
                WHERE COALESCE(b.client_first_name, b.client_last_name, b.email, b.phone) IS NOT NULL
                  AND NOT EXISTS (SELECT 1 FROM client c WHERE c.booking_id = b.id)
                """);

        for (String column : LEGACY_COLUMNS) {
            jdbcTemplate.execute("ALTER TABLE inspection_bookings DROP COLUMN " + column);
        }
        log.info("Moved {} booking client(s) into the client table", moved);
    }
}
