package ca.inspection.home.inspection.integration;

import ca.inspection.home.inspection.entity.InspectionBookings;
import ca.inspection.home.inspection.entity.InspectionField;
import ca.inspection.home.inspection.entity.InspectionFieldDefinition;
import ca.inspection.home.inspection.entity.InspectionFieldDefinitionValue;
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
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Tag("integration")
public class ConditionNameIT {

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
    private InspectionFieldDefinition definition;

    @BeforeEach
    void resetState() {
        imagesRepository.deleteAll();
        fieldRepository.deleteAll();
        reportsRepository.deleteAll();
        bookingsRepository.deleteAll();
        definitionRepository.deleteAll();

        InspectionBookings booking = new InspectionBookings();
        booking.setInspectionAddress("8 Placeholder Place");
        booking = bookingsRepository.save(booking);

        report = new InspectionReport();
        report.setInspectionBooking(booking);
        report = reportsRepository.save(report);

        definition = new InspectionFieldDefinition();
        definition.setFieldName("shingles");
        definition.setFieldPlace("roofing");
        definition.setFieldType("description");
        definition.setPossibleValues(new java.util.ArrayList<>());
        definition = definitionRepository.save(definition);

        addValue("Asphalt Shingles");
        addValue(InspectionFieldDefinitionValue.BLANK_ITEM);
    }

    private void addValue(String value) {
        InspectionFieldDefinitionValue definitionValue = new InspectionFieldDefinitionValue();
        definitionValue.setInspectionFieldDefinition(definition);
        definitionValue.setValue(value);
        definition.getPossibleValues().add(definitionValue);
        definition = definitionRepository.save(definition);
    }

    private InspectionFieldDefinitionValue valueNamed(String value) {
        return definitionRepository.findWithValues(definition.getId()).getPossibleValues().stream()
                .filter(v -> v.getValue().equals(value))
                .findFirst()
                .orElseThrow();
    }

    private InspectionField persistBlankItemField() {
        InspectionField field = new InspectionField();
        field.setInspectionReport(report);
        field.setInspectionFieldDefinition(definition);
        field.setSelectedValue(valueNamed(InspectionFieldDefinitionValue.BLANK_ITEM));
        return fieldRepository.save(field);
    }

    private List<String> currentValues() {
        return definitionRepository.findWithValues(definition.getId()).getPossibleValues().stream()
                .map(InspectionFieldDefinitionValue::getValue)
                .toList();
    }

    @Test
    void namingABlankItem_savesToTheFieldOnly() throws Exception {
        InspectionField field = persistBlankItemField();

        mockMvc.perform(put("/api/fields/{id}/condition-name", field.getId())
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("Cracked parging at front wall"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.savedPermanently").value(false));

        InspectionField saved = fieldRepository.findById(field.getId()).orElseThrow();
        assertThat(saved.getConditionName()).isEqualTo("Cracked parging at front wall");
        // The definition's option list is untouched — this name is for this report only.
        assertThat(currentValues()).containsExactlyInAnyOrder("Asphalt Shingles", "blank item");
    }

    @Test
    void savingAsPermanent_addsTheValueToTheDefinition() throws Exception {
        InspectionField field = persistBlankItemField();

        mockMvc.perform(put("/api/fields/{id}/condition-name", field.getId())
                        .param("saveAsPermanentValue", "true")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("Cedar Shakes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.savedPermanently").value(true))
                .andExpect(jsonPath("$.alreadyExisted").value(false));

        assertThat(currentValues()).contains("Cedar Shakes");
        assertThat(fieldRepository.findById(field.getId()).orElseThrow().getConditionName())
                .isEqualTo("Cedar Shakes");
    }

    @Test
    void savingAsPermanentTwice_doesNotDuplicateTheValue() throws Exception {
        InspectionField field = persistBlankItemField();

        for (String attempt : List.of("Cedar Shakes", "cedar shakes")) {
            mockMvc.perform(put("/api/fields/{id}/condition-name", field.getId())
                            .param("saveAsPermanentValue", "true")
                            .contentType(MediaType.TEXT_PLAIN)
                            .content(attempt))
                    .andExpect(status().isOk());
        }

        assertThat(currentValues().stream().filter(v -> v.equalsIgnoreCase("cedar shakes"))).hasSize(1);
    }

    @Test
    void aSavedPermanentValue_showsUpAsAnOptionOnTheDefinition() throws Exception {
        InspectionField field = persistBlankItemField();

        mockMvc.perform(put("/api/fields/{id}/condition-name", field.getId())
                        .param("saveAsPermanentValue", "true")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("Cedar Shakes"))
                .andExpect(status().isOk());

        // What the report writing page reads to draw the value buttons.
        mockMvc.perform(get("/api/fields/definition/{place}/{type}", "roofing", "description"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].possibleValues[?(@.value == 'Cedar Shakes')]").exists());
    }

    @Test
    void clearingTheName_removesItFromTheField() throws Exception {
        InspectionField field = persistBlankItemField();
        field.setConditionName("Old name");
        fieldRepository.save(field);

        mockMvc.perform(put("/api/fields/{id}/condition-name", field.getId())
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("   "))
                .andExpect(status().isOk());

        assertThat(fieldRepository.findById(field.getId()).orElseThrow().getConditionName()).isNull();
    }

    @Test
    void theNamedFieldIsServedToTheReportWritingPage() throws Exception {
        InspectionField field = persistBlankItemField();
        mockMvc.perform(put("/api/fields/{id}/condition-name", field.getId())
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("Loose railing"))
                .andExpect(status().isOk());

        // The page labels its buttons from this payload, so the name has to travel with it.
        mockMvc.perform(get("/api/fields/{bookingId}/{place}/{type}/combined",
                        report.getInspectionBooking().getId(), "roofing", "description"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.*[0].conditionName").value("Loose railing"))
                .andExpect(jsonPath("$.*[0].displayValue").value("Loose railing"))
                .andExpect(jsonPath("$.*[0].blankItem").value(true));
    }
}
