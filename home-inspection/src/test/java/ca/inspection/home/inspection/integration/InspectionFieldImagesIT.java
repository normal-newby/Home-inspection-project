package ca.inspection.home.inspection.integration;

import ca.inspection.home.inspection.entity.InspectionBookings;
import ca.inspection.home.inspection.entity.InspectionField;
import ca.inspection.home.inspection.entity.InspectionFieldDefinition;
import ca.inspection.home.inspection.entity.InspectionImage;
import ca.inspection.home.inspection.entity.InspectionReport;
import ca.inspection.home.inspection.repository.InspectionBookingsRepository;
import ca.inspection.home.inspection.repository.InspectionFieldDefinitionRepository;
import ca.inspection.home.inspection.repository.InspectionFieldRepository;
import ca.inspection.home.inspection.repository.InspectionImagesRepository;
import ca.inspection.home.inspection.repository.InspectionReportsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Deleting a field must not take its images down with it — the photos belong to the
 * report's pool and go back to being unused, ready to attach somewhere else.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Tag("integration")
public class InspectionFieldImagesIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private InspectionBookingsRepository bookingsRepository;

    @Autowired
    private InspectionReportsRepository reportsRepository;

    @Autowired
    private InspectionFieldRepository fieldRepository;

    @Autowired
    private InspectionFieldDefinitionRepository definitionRepository;

    @Autowired
    private InspectionImagesRepository imagesRepository;

    private InspectionReport report;

    @BeforeEach
    void resetState() {
        imagesRepository.deleteAll();
        fieldRepository.deleteAll();
        reportsRepository.deleteAll();
        bookingsRepository.deleteAll();

        InspectionBookings booking = new InspectionBookings();
        booking.setInspectionAddress("42 Shutter Lane");
        booking = bookingsRepository.save(booking);

        report = new InspectionReport();
        report.setInspectionBooking(booking);
        report = reportsRepository.save(report);
    }

    private InspectionField persistField() {
        InspectionFieldDefinition definition = new InspectionFieldDefinition();
        definition.setFieldName("shingles");
        definition.setFieldPlace("roofing");
        definition.setFieldType("description");
        definition = definitionRepository.save(definition);

        InspectionField field = new InspectionField();
        field.setInspectionReport(report);
        field.setInspectionFieldDefinition(definition);
        return fieldRepository.save(field);
    }

    private InspectionImage persistImage(InspectionField field, String fileName) {
        InspectionImage image = new InspectionImage();
        image.setInspectionReport(report);
        image.setInspectionField(field);
        image.setImageUrl(fileName);
        image.setUsed(true);
        return imagesRepository.save(image);
    }

    @Test
    void deletingAField_keepsItsImagesAndMarksThemUnused() throws Exception {
        InspectionField field = persistField();
        UUID firstId = persistImage(field, "roof-1.jpg").getId();
        UUID secondId = persistImage(field, "roof-2.jpg").getId();

        mockMvc.perform(delete("/api/fields/{id}", field.getId()))
                .andExpect(status().isOk());

        assertThat(fieldRepository.findById(field.getId())).isEmpty();

        // Both images survive the delete, detached and available again.
        InspectionImage first = imagesRepository.findById(firstId).orElseThrow();
        InspectionImage second = imagesRepository.findById(secondId).orElseThrow();
        assertThat(first.getUsed()).isFalse();
        assertThat(first.getInspectionField()).isNull();
        assertThat(second.getUsed()).isFalse();
        assertThat(second.getInspectionField()).isNull();
    }

    @Test
    void deletingAField_leavesOtherFieldsImagesAlone() throws Exception {
        InspectionField doomed = persistField();
        InspectionField keeper = persistField();
        persistImage(doomed, "doomed.jpg");
        UUID keptId = persistImage(keeper, "kept.jpg").getId();

        mockMvc.perform(delete("/api/fields/{id}", doomed.getId()))
                .andExpect(status().isOk());

        InspectionImage kept = imagesRepository.findById(keptId).orElseThrow();
        assertThat(kept.getUsed()).isTrue();
        assertThat(kept.getInspectionField().getId()).isEqualTo(keeper.getId());
    }

    @Test
    void deletingAFieldWithNoImages_stillDeletesTheField() throws Exception {
        InspectionField field = persistField();

        mockMvc.perform(delete("/api/fields/{id}", field.getId()))
                .andExpect(status().isOk());

        assertThat(fieldRepository.findById(field.getId())).isEmpty();
    }
}
