package ca.inspection.home.inspection.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

// Moves the old floating-point fee column into whole cents (fee_cents)
@Slf4j
@Component
public class FeeCentsMigration implements ApplicationRunner {
    private static final List<String> TABLES = List.of("invoice", "invoice_definition");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        for (String table : TABLES) {
            List<String> columns = jdbcTemplate.queryForList(
                    "SELECT name FROM pragma_table_info('" + table + "')", String.class);
            if (!columns.contains("fee")) continue;

            // ddl-auto has already added fee_cents by the time this runs.
            int moved = jdbcTemplate.update("UPDATE " + table
                    + " SET fee_cents = CAST(ROUND(fee * 100) AS INTEGER) WHERE fee_cents IS NULL");
            jdbcTemplate.execute("ALTER TABLE " + table + " DROP COLUMN fee");
            log.info("Moved {} {} fee(s) to cents", moved, table);
        }
    }
}
