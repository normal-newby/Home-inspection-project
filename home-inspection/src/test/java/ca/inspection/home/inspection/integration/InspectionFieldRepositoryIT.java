package ca.inspection.home.inspection.integration;

import ca.inspection.home.inspection.entity.ImageAnnotation;
import ca.inspection.home.inspection.entity.InspectionBookings;
import ca.inspection.home.inspection.entity.InspectionField;
import ca.inspection.home.inspection.entity.InspectionFieldDefinition;
import ca.inspection.home.inspection.entity.InspectionImage;
import ca.inspection.home.inspection.entity.InspectionReport;
import ca.inspection.home.inspection.repository.InspectionFieldRepository;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// See InspectionBookingsRepositoryIT for what @DataJpaTest gives us here.
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Tag("integration")
public class InspectionFieldRepositoryIT {

    private static final String PLACE = "roof";
    private static final String TYPE = "limitations";

    @Autowired
    private TestEntityManager em;

    @Autowired
    private InspectionFieldRepository inspectionFieldRepository;

    private InspectionReport persistReport() {
        InspectionBookings booking = new InspectionBookings();
        booking.setInspectionAddress("1 Test Street");
        booking.setClientFirstName("Jane");
        booking.setClientLastName("Doe");
        em.persist(booking);

        InspectionReport report = new InspectionReport();
        report.setInspectionBooking(booking);
        em.persist(report);
        return report;
    }

    private InspectionField persistField(InspectionReport report) {
        InspectionFieldDefinition definition = new InspectionFieldDefinition();
        definition.setFieldName("shingles");
        definition.setFieldPlace(PLACE);
        definition.setFieldType(TYPE);
        em.persist(definition);

        InspectionField field = new InspectionField();
        field.setInspectionReport(report);
        field.setInspectionFieldDefinition(definition);
        em.persist(field);
        return field;
    }

    private InspectionImage persistImage(InspectionReport report, InspectionField field,
                                         String fileName, int annotationCount) {
        InspectionImage image = new InspectionImage();
        image.setInspectionReport(report);
        image.setInspectionField(field);
        image.setImageUrl(fileName);
        image.setUsed(true);
        em.persist(image);

        for (int i = 0; i < annotationCount; i++) {
            ImageAnnotation annotation = new ImageAnnotation();
            annotation.setInspectionImage(image);
            annotation.setType("rectangle");
            annotation.setX((double) i);
            annotation.setY((double) i);
            annotation.setWidth(10.0);
            annotation.setHeight(10.0);
            annotation.setImageDisplayWidth(100.0);
            annotation.setImageDisplayHeight(100.0);
            em.persist(annotation);
        }
        return image;
    }

    @Test
    void existingFieldsQueryReturnsEachImageOnceRegardlessOfAnnotationCount() {
        InspectionReport report = persistReport();
        InspectionField field = persistField(report);
        persistImage(report, field, "three-annotations.jpg", 3);
        persistImage(report, field, "no-annotations.jpg", 0);
        em.flush();
        em.clear();

        List<InspectionField> fields = inspectionFieldRepository
                .getExistingFieldsForPlaceAndType(report.getId(), PLACE, TYPE);

        assertThat(fields).hasSize(1);
        assertThat(fields.get(0).getInspectionImages())
                .extracting(InspectionImage::getImageUrl)
                .containsExactlyInAnyOrder("three-annotations.jpg", "no-annotations.jpg");
    }

    @Test
    void existingFieldsQueryReturnsFieldOnceWhenItHasManyImages() {
        InspectionReport report = persistReport();
        InspectionField field = persistField(report);
        persistImage(report, field, "a.jpg", 2);
        persistImage(report, field, "b.jpg", 2);
        persistImage(report, field, "c.jpg", 2);
        em.flush();
        em.clear();

        List<InspectionField> fields = inspectionFieldRepository
                .getExistingFieldsForPlaceAndType(report.getId(), PLACE, TYPE);

        assertThat(fields).hasSize(1);
        assertThat(fields.get(0).getInspectionImages()).hasSize(3);
    }
}
