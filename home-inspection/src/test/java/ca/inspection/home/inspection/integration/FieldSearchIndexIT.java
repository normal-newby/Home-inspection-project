package ca.inspection.home.inspection.integration;

import ca.inspection.home.inspection.entity.InspectionFieldDefinition;
import ca.inspection.home.inspection.entity.InspectionFieldDefinitionValue;
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

import java.util.ArrayList;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Tag("integration")
public class FieldSearchIndexIT {

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

    @BeforeEach
    void resetState() {
        imagesRepository.deleteAll();
        fieldRepository.deleteAll();
        reportsRepository.deleteAll();
        bookingsRepository.deleteAll();
        definitionRepository.deleteAll();
    }

    private void saveDefinition(String name, String place, String type, String... values) {
        InspectionFieldDefinition definition = new InspectionFieldDefinition();
        definition.setFieldName(name);
        definition.setFieldPlace(place);
        definition.setFieldType(type);
        definition.setPossibleValues(new ArrayList<>());
        for (String value : values) {
            InspectionFieldDefinitionValue possible = new InspectionFieldDefinitionValue();
            possible.setValue(value);
            possible.setInspectionFieldDefinition(definition);
            definition.getPossibleValues().add(possible);
        }
        definitionRepository.save(definition);
    }

    @Test
    void searchIndex_listsEveryDefinitionOnceWithAllItsValues() throws Exception {
        // The fetch join returns a row per value; each definition must still appear once.
        saveDefinition("Gutters", "roofing", "recommendations", "Leaking", "Loose", "Clogged");
        saveDefinition("Furnace", "heating", "description", "Gas", "Oil");
        saveDefinition("Snow cover", "roofing", "limitations");

        mockMvc.perform(get("/api/fields/definition/search-index"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[*].fieldName", containsInAnyOrder("Gutters", "Furnace", "Snow cover")))
                .andExpect(jsonPath("$[?(@.fieldName == 'Gutters')].values[*]",
                        containsInAnyOrder("Leaking", "Loose", "Clogged")))
                .andExpect(jsonPath("$[?(@.fieldName == 'Furnace')].place", containsInAnyOrder("heating")))
                .andExpect(jsonPath("$[?(@.fieldName == 'Snow cover')].values[*]", hasSize(0)));
    }
}
