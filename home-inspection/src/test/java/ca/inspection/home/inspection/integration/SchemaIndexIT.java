package ca.inspection.home.inspection.integration;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// The columns the app filters and joins on most; without these SQLite scans the whole table.
// Own database file: sharing the other repository tests' context would hand them a used schema.
@DataJpaTest(properties = "spring.datasource.url=jdbc:sqlite:target/schema-test.db")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Tag("integration")
public class SchemaIndexIT {

    @Autowired
    private EntityManager entityManager;

    @Test
    @SuppressWarnings("unchecked")
    void lookupColumnsAreIndexed() {
        List<String> indexes = entityManager
                .createNativeQuery("select name from sqlite_master where type = 'index'")
                .getResultList();

        assertThat(indexes).contains(
                "idx_definition_value_definition",
                "idx_booking_inspection_number",
                "idx_booking_inspection_date",
                "idx_value_diagram_diagram",
                "idx_report_cover_image");
    }
}
