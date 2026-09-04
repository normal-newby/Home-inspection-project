package ca.inspection.home.inspection.service;

import ca.inspection.home.inspection.DTO.FieldGroup;
import ca.inspection.home.inspection.entity.InspectionField;
import ca.inspection.home.inspection.entity.InspectionFieldDefinition;
import ca.inspection.home.inspection.entity.InspectionImage;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
public class ReportTemplateRenderTest {

    @Autowired
    private SpringTemplateEngine templateEngine;

    @Autowired
    private ReportViewService reportViewService;

    private String renderBody(List<InspectionField> fields) {
        Map<String, Map<String, List<FieldGroup>>> allFields = reportViewService.getAllFields(fields);
        reportViewService.numberFigures(allFields);

        Context context = new Context();
        context.setVariable("allFields", allFields);
        return templateEngine.process("report", Set.of("#report-sections"), context);
    }

    private static InspectionFieldDefinition definition(String name, String place, String type) {
        InspectionFieldDefinition definition = new InspectionFieldDefinition();
        definition.setId(UUID.randomUUID());
        definition.setFieldName(name);
        definition.setFieldPlace(place);
        definition.setFieldType(type);
        return definition;
    }

    private static InspectionField field(InspectionFieldDefinition definition, String value) {
        InspectionField field = new InspectionField();
        field.setId(UUID.randomUUID());
        field.setInspectionFieldDefinition(definition);
        field.setConditionName(value);
        return field;
    }

    private static InspectionField withPhotos(InspectionField field, int count) {
        List<InspectionImage> images = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            InspectionImage image = new InspectionImage();
            image.setId(UUID.randomUUID());
            image.setBase64("data:image/jpeg;base64,FAKE");
            images.add(image);
        }
        field.setInspectionImages(images);
        return field;
    }

    private static int occurrences(String haystack, String needle) {
        int count = 0, from = 0, at;
        while ((at = haystack.indexOf(needle, from)) >= 0) {
            count++;
            from = at + needle.length();
        }
        return count;
    }

    @Test
    void definitionUsedOnce_putsTheValueOnItsOwnLineUnderTheName() {
        InspectionFieldDefinition covering = definition("Roof Covering", "roofing", "description");

        String html = renderBody(List.of(field(covering, "Asphalt shingle")));

        assertThat(occurrences(html, "Roof Covering")).isEqualTo(1);
        assertThat(html).contains("report-field-value-line");
        assertThat(html).contains("Asphalt shingle");
        // The value no longer rides on the heading row.
        assertThat(html).doesNotContain("report-field-value--right");
    }

    @Test
    void definitionUsedSeveralTimes_writesOneHeadingWithCommaSeparatedValues() {
        InspectionFieldDefinition covering = definition("Roof Covering", "roofing", "description");

        String html = renderBody(List.of(
                field(covering, "Asphalt shingle"),
                field(covering, "Metal"),
                field(covering, "Slate")));

        assertThat(occurrences(html, "Roof Covering")).isEqualTo(1);
        assertThat(html).contains("Asphalt shingle, Metal, Slate");
    }

    @Test
    void separateDefinitions_keepTheirOwnHeadings() {
        InspectionFieldDefinition covering = definition("Roof Covering", "roofing", "description");
        InspectionFieldDefinition flashing = definition("Flashing", "roofing", "description");

        String html = renderBody(List.of(field(covering, "Metal"), field(flashing, "Aluminum")));

        assertThat(occurrences(html, "Roof Covering")).isEqualTo(1);
        assertThat(occurrences(html, "Flashing")).isEqualTo(1);
    }

    @Test
    void captionNumbers_runStraightThroughTheDocumentWithoutRestarting() {
        // Two photos under roofing, one under exterior: the exterior caption reads 3.
        InspectionFieldDefinition covering = definition("Roof Covering", "roofing", "description");
        InspectionFieldDefinition siding = definition("Siding", "exterior", "description");

        String html = renderBody(List.of(
                withPhotos(field(covering, "Asphalt shingle"), 2),
                withPhotos(field(siding, "Vinyl"), 1)));

        assertThat(html).contains("1. Asphalt shingle");
        assertThat(html).contains("2. Asphalt shingle");
        assertThat(html).contains("3. Vinyl");
        assertThat(html).doesNotContain("1. Vinyl");
    }

    @Test
    void captionNumbers_keepCountingAcrossEntriesInOneGroup() {
        InspectionFieldDefinition covering = definition("Roof Covering", "roofing", "description");

        String html = renderBody(List.of(
                withPhotos(field(covering, "Asphalt shingle"), 1),
                withPhotos(field(covering, "Metal"), 2)));

        assertThat(html).contains("1. Asphalt shingle");
        assertThat(html).contains("2. Metal");
        assertThat(html).contains("3. Metal");
    }

    @Test
    void valueOnlyGroup_drawsNoEmptyEntryBlocks() {
        // Nothing is left per entry once the values are on one line, so the separators between
        // entries must not render against empty blocks.
        InspectionFieldDefinition covering = definition("Roof Covering", "roofing", "description");

        String html = renderBody(List.of(field(covering, "Metal"), field(covering, "Slate")));

        assertThat(html).contains("Metal, Slate");
        assertThat(html).doesNotContain("report-field-instance");
    }

    @Test
    void groupedEntriesWithNotes_stillGetTheirOwnBlocks() {
        InspectionFieldDefinition covering = definition("Roof Covering", "roofing", "description");
        InspectionField metal = field(covering, "Metal");
        metal.setNote("Rust at the ridge");
        InspectionField slate = field(covering, "Slate");
        slate.setNote("Two cracked tiles");

        String html = renderBody(List.of(metal, slate));

        assertThat(html).contains("Metal, Slate");
        assertThat(occurrences(html, "report-field-instance")).isEqualTo(2);
        assertThat(html).contains("Rust at the ridge").contains("Two cracked tiles");
    }

    @Test
    void groupedCard_marksItselfSoItCanSplitAcrossPages() {
        InspectionFieldDefinition covering = definition("Roof Covering", "roofing", "description");

        String grouped = renderBody(List.of(field(covering, "Metal"), field(covering, "Slate")));
        String single = renderBody(List.of(field(covering, "Metal")));

        assertThat(grouped).contains("report-field-entry--grouped");
        assertThat(single).doesNotContain("report-field-entry--grouped");
    }
}
