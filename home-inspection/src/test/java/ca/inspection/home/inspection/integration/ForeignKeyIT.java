package ca.inspection.home.inspection.integration;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// SQLite only enforces foreign keys on connections that switch them on.
// Own database file: sharing the other repository tests' context would hand them a used schema.
@DataJpaTest(properties = "spring.datasource.url=jdbc:sqlite:target/schema-test.db")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Tag("integration")
public class ForeignKeyIT {

    @Autowired
    private EntityManager entityManager;

    @Test
    void connectionsEnforceForeignKeys() {
        Object enabled = entityManager.createNativeQuery("PRAGMA foreign_keys").getSingleResult();

        assertThat(((Number) enabled).intValue()).isEqualTo(1);
    }

    // Own tables: Hibernate's SQLite dialect leaves foreign keys out of the tables it creates,
    // so the test schema has none to trip over. The live database's hand-written tables do.
    @Test
    void rowPointingAtAMissingParent_isRefused() {
        entityManager.createNativeQuery("create table fk_parent (id text primary key)").executeUpdate();
        entityManager.createNativeQuery(
                "create table fk_child (id text primary key, parent_id text references fk_parent(id))").executeUpdate();

        assertThatThrownBy(() -> entityManager
                .createNativeQuery("insert into fk_child (id, parent_id) values ('c1', 'no-such-parent')")
                .executeUpdate())
                .hasMessageContaining("FOREIGN KEY");
    }
}
