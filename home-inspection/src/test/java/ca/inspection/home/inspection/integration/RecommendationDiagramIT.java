package ca.inspection.home.inspection.integration;

import ca.inspection.home.inspection.DTO.AttachedDiagrams;
import ca.inspection.home.inspection.entity.InspectionBookings;
import ca.inspection.home.inspection.entity.InspectionField;
import ca.inspection.home.inspection.entity.InspectionFieldDefinition;
import ca.inspection.home.inspection.entity.InspectionFieldDefinitionValue;
import ca.inspection.home.inspection.entity.InspectionReport;
import ca.inspection.home.inspection.entity.RecommendationDiagram;
import ca.inspection.home.inspection.repository.InspectionBookingsRepository;
import ca.inspection.home.inspection.repository.InspectionFieldDefinitionRepository;
import ca.inspection.home.inspection.repository.InspectionFieldDefinitionValueRepository;
import ca.inspection.home.inspection.repository.InspectionFieldRepository;
import ca.inspection.home.inspection.repository.InspectionReportsRepository;
import ca.inspection.home.inspection.repository.RecommendationDiagramRepository;
import ca.inspection.home.inspection.service.HelperFunctions;
import ca.inspection.home.inspection.service.RecommendationDiagramService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import javax.imageio.ImageIO;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Tag("integration")
public class RecommendationDiagramIT {

    @Autowired
    private RecommendationDiagramService recommendationDiagramService;

    @Autowired
    private RecommendationDiagramRepository diagramRepository;

    @Autowired
    private InspectionFieldDefinitionValueRepository valueRepository;

    @Autowired
    private InspectionFieldDefinitionRepository definitionRepository;

    @Autowired
    private InspectionFieldRepository fieldRepository;

    @Autowired
    private InspectionReportsRepository reportsRepository;

    @Autowired
    private InspectionBookingsRepository bookingsRepository;

    @Autowired
    private HelperFunctions helperFunctions;

    private InspectionFieldDefinition definition;
    private InspectionReport report;

    @BeforeEach
    void resetState() {
        fieldRepository.deleteAll();
        reportsRepository.deleteAll();
        bookingsRepository.deleteAll();
        valueRepository.deleteAll();
        definitionRepository.deleteAll();
        diagramRepository.deleteAll();

        InspectionBookings booking = new InspectionBookings();
        booking.setInspectionAddress("42 Shutter Lane");
        booking = bookingsRepository.save(booking);

        report = new InspectionReport();
        report.setInspectionBooking(booking);
        report = reportsRepository.save(report);

        definition = new InspectionFieldDefinition();
        definition.setFieldName("Flat roof flashings");
        definition.setFieldPlace("roofing");
        definition.setFieldType("recommendations");
        definition = definitionRepository.save(definition);
    }

    private static MockMultipartFile drawing(String name) throws Exception {
        BufferedImage image = new BufferedImage(600, 400, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(image, "jpeg", bytes);
        return new MockMultipartFile("file", name, "image/jpeg", bytes.toByteArray());
    }

    private RecommendationDiagram upload(String title) throws Exception {
        Object body = recommendationDiagramService.uploadDiagram(title, drawing(title + ".jpg")).getBody();
        return (RecommendationDiagram) body;
    }

    private InspectionFieldDefinitionValue value(String text) {
        InspectionFieldDefinitionValue value = new InspectionFieldDefinitionValue();
        value.setInspectionFieldDefinition(definition);
        value.setValue(text);
        return valueRepository.save(value);
    }

    private InspectionField field(InspectionFieldDefinitionValue value) {
        InspectionField field = new InspectionField();
        field.setInspectionReport(report);
        field.setInspectionFieldDefinition(definition);
        field.setSelectedValue(value);
        return fieldRepository.save(field);
    }

    // THE LIBRARY

    @Test
    void uploadDiagram_putsTheFileUnderRecommendationsDiagrams() throws Exception {
        RecommendationDiagram diagram = upload("Correct flashing detail");

        Path path = helperFunctions.getRecommendationDiagramDirectory().resolve(diagram.getFileName());
        assertThat(Files.exists(path)).as("file at %s", path).isTrue();
        assertThat(diagram.getTitle()).isEqualTo("Correct flashing detail");
    }

    @Test
    void uploadDiagram_withoutATitle_isRejectedAndWritesNothing() throws Exception {
        var response = recommendationDiagramService.uploadDiagram("  ", drawing("untitled.jpg"));

        assertThat(response.getStatusCode().is4xxClientError()).isTrue();
        assertThat(diagramRepository.findAll()).isEmpty();
    }

    // ATTACHING

    @Test
    void setAttachedDiagrams_persistsOntoTheDefinitionValue() throws Exception {
        RecommendationDiagram first = upload("Flashing detail");
        RecommendationDiagram second = upload("Drip edge");
        InspectionFieldDefinitionValue value = value("Improperly installed");
        InspectionField field = field(value);

        recommendationDiagramService.setAttachedDiagrams(field.getId(),
                List.of(first.getId(), second.getId()));

        // Read back off the value, not the field: that is where they are supposed to live.
        List<InspectionFieldDefinitionValue> stored =
                valueRepository.findAllWithDiagrams(List.of(value.getId()));
        assertThat(stored).singleElement()
                .extracting(InspectionFieldDefinitionValue::getDiagrams)
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.list(RecommendationDiagram.class))
                .extracting(RecommendationDiagram::getTitle)
                .containsExactly("Flashing detail", "Drip edge");
    }

    @Test
    void setAttachedDiagrams_keepsThePickedOrder() throws Exception {
        RecommendationDiagram first = upload("A drawing");
        RecommendationDiagram second = upload("B drawing");
        InspectionField field = field(value("Improperly installed"));

        // Attached back to front; the report prints them the way they were picked, not by title.
        recommendationDiagramService.setAttachedDiagrams(field.getId(),
                List.of(second.getId(), first.getId()));

        AttachedDiagrams attached = recommendationDiagramService.getAttachedDiagrams(field.getId());
        assertThat(attached.diagramIds()).containsExactly(second.getId(), first.getId());
    }

    @Test
    void setAttachedDiagrams_replacesWhatWasThere() throws Exception {
        RecommendationDiagram first = upload("Flashing detail");
        RecommendationDiagram second = upload("Drip edge");
        InspectionField field = field(value("Improperly installed"));

        recommendationDiagramService.setAttachedDiagrams(field.getId(), List.of(first.getId()));
        recommendationDiagramService.setAttachedDiagrams(field.getId(), List.of(second.getId()));

        assertThat(recommendationDiagramService.getAttachedDiagrams(field.getId()).diagramIds())
                .containsExactly(second.getId());
    }

    @Test
    void setAttachedDiagrams_reachesEveryItemOnThatRecommendation() throws Exception {
        // Attach once, and the next inspection picking the same recommendation gets them too.
        RecommendationDiagram diagram = upload("Flashing detail");
        InspectionFieldDefinitionValue shared = value("Improperly installed");
        InspectionField thisReport = field(shared);
        InspectionField anotherItem = field(shared);

        recommendationDiagramService.setAttachedDiagrams(thisReport.getId(), List.of(diagram.getId()));

        assertThat(recommendationDiagramService.getAttachedDiagrams(anotherItem.getId()).diagramIds())
                .containsExactly(diagram.getId());
    }

    @Test
    void setAttachedDiagrams_idThatIsNoLongerInTheLibrary_isDroppedRatherThanFailing() throws Exception {
        RecommendationDiagram diagram = upload("Flashing detail");
        InspectionField field = field(value("Improperly installed"));

        recommendationDiagramService.setAttachedDiagrams(field.getId(),
                List.of(diagram.getId(), UUID.randomUUID()));

        assertThat(recommendationDiagramService.getAttachedDiagrams(field.getId()).diagramIds())
                .containsExactly(diagram.getId());
    }

    @Test
    void getAttachedDiagrams_namesTheRecommendationItIsAttachingTo() throws Exception {
        InspectionField field = field(value("Improperly installed"));

        AttachedDiagrams attached = recommendationDiagramService.getAttachedDiagrams(field.getId());

        assertThat(attached.fieldName()).isEqualTo("Flat roof flashings");
        assertThat(attached.valueLabel()).isEqualTo("Improperly installed");
        assertThat(attached.diagramIds()).isEmpty();
    }

    // DELETING

    @Test
    void deleteDiagram_takesItOffEveryRecommendationHoldingIt() throws Exception {
        RecommendationDiagram diagram = upload("Flashing detail");
        RecommendationDiagram keep = upload("Drip edge");
        InspectionField roofing = field(value("Improperly installed"));
        InspectionField other = field(value("Missing entirely"));

        recommendationDiagramService.setAttachedDiagrams(roofing.getId(), List.of(diagram.getId(), keep.getId()));
        recommendationDiagramService.setAttachedDiagrams(other.getId(), List.of(diagram.getId()));

        recommendationDiagramService.deleteDiagram(diagram.getId());

        // A join row outliving the diagram would break the next report render.
        assertThat(recommendationDiagramService.getAttachedDiagrams(roofing.getId()).diagramIds())
                .containsExactly(keep.getId());
        assertThat(recommendationDiagramService.getAttachedDiagrams(other.getId()).diagramIds())
                .isEmpty();
        assertThat(diagramRepository.findById(diagram.getId())).isEmpty();
    }

    @Test
    void deleteDiagram_removesTheFileFromDisk() throws Exception {
        RecommendationDiagram diagram = upload("Flashing detail");
        Path path = helperFunctions.getRecommendationDiagramDirectory().resolve(diagram.getFileName());
        assertThat(Files.exists(path)).isTrue();

        recommendationDiagramService.deleteDiagram(diagram.getId());

        assertThat(Files.exists(path)).isFalse();
    }
}
