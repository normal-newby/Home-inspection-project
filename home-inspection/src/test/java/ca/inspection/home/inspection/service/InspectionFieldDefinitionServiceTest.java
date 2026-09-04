package ca.inspection.home.inspection.service;

import ca.inspection.home.inspection.DTO.ReportLayoutPlace;
import ca.inspection.home.inspection.DTO.ReportLayoutType;
import ca.inspection.home.inspection.entity.InspectionFieldDefinition;
import ca.inspection.home.inspection.repository.InspectionFieldDefinitionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class InspectionFieldDefinitionServiceTest {

    @Mock
    private InspectionFieldDefinitionRepository inspectionFieldDefinitionRepository;

    @InjectMocks
    private InspectionFieldDefinitionService inspectionFieldDefinitionService;

    private InspectionFieldDefinition definition(String name, String place, String type, Integer reportOrder) {
        InspectionFieldDefinition definition = new InspectionFieldDefinition();
        definition.setId(UUID.randomUUID());
        definition.setFieldName(name);
        definition.setFieldPlace(place);
        definition.setFieldType(type);
        definition.setReportOrder(reportOrder);
        return definition;
    }

    private List<String> namesIn(List<ReportLayoutPlace> layout, String place, String type) {
        return layout.stream()
                .filter(p -> p.place().equals(place))
                .flatMap(p -> p.types().stream())
                .filter(t -> t.type().equals(type))
                .flatMap(t -> t.definitions().stream())
                .map(d -> d.fieldName())
                .toList();
    }

    // READING THE LAYOUT

    @Test
    void getReportLayout_listsPlacesInTheOrderTheReportPrintsThem() {
        // Not alphabetical: the page has to mirror the PDF or reordering it means nothing.
        when(inspectionFieldDefinitionRepository.findAll()).thenReturn(List.of(
                definition("Fixtures", "plumbing", "description", null),
                definition("Covering", "roofing", "description", null),
                definition("Panel", "electrical", "description", null)));

        List<ReportLayoutPlace> layout = inspectionFieldDefinitionService.getReportLayout();

        assertThat(layout).extracting(ReportLayoutPlace::place)
                .containsExactly("roofing", "electrical", "plumbing");
    }

    @Test
    void getReportLayout_listsTypesInTheOrderTheReportPrintsThem() {
        when(inspectionFieldDefinitionRepository.findAll()).thenReturn(List.of(
                definition("Gutters", "roofing", "recommendations", null),
                definition("Covering", "roofing", "description", null),
                definition("Snow cover", "roofing", "limitations", null)));

        List<ReportLayoutPlace> layout = inspectionFieldDefinitionService.getReportLayout();

        assertThat(layout).singleElement()
                .extracting(ReportLayoutPlace::types, org.assertj.core.api.InstanceOfAssertFactories.list(ReportLayoutType.class))
                .extracting(ReportLayoutType::type)
                .containsExactly("description", "limitations", "recommendations");
    }

    @Test
    void getReportLayout_ordersRowsByTheirSavedOrder() {
        when(inspectionFieldDefinitionRepository.findAll()).thenReturn(List.of(
                definition("Covering", "roofing", "description", 2),
                definition("Ventilation", "roofing", "description", 0),
                definition("Flashing", "roofing", "description", 1)));

        List<ReportLayoutPlace> layout = inspectionFieldDefinitionService.getReportLayout();

        assertThat(namesIn(layout, "roofing", "description"))
                .containsExactly("Ventilation", "Flashing", "Covering");
    }

    @Test
    void getReportLayout_rowsWithNoSavedOrder_comeLastByName() {
        // Same rule the report itself sorts by, so the page shows what will print.
        when(inspectionFieldDefinitionRepository.findAll()).thenReturn(List.of(
                definition("Ventilation", "roofing", "description", null),
                definition("Flashing", "roofing", "description", null),
                definition("Covering", "roofing", "description", 5)));

        List<ReportLayoutPlace> layout = inspectionFieldDefinitionService.getReportLayout();

        assertThat(namesIn(layout, "roofing", "description"))
                .containsExactly("Covering", "Flashing", "Ventilation");
    }

    @Test
    void getReportLayout_placeNotOnTheReportsList_stillShowsUp() {
        // Otherwise a newly seeded section would be unorderable and invisible here.
        when(inspectionFieldDefinitionRepository.findAll()).thenReturn(List.of(
                definition("Sump pump", "basement", "description", null),
                definition("Covering", "roofing", "description", null)));

        List<ReportLayoutPlace> layout = inspectionFieldDefinitionService.getReportLayout();

        assertThat(layout).extracting(ReportLayoutPlace::place).containsExactly("roofing", "basement");
    }

    @Test
    void getReportLayout_definitionMissingItsPlaceOrType_isSkipped() {
        when(inspectionFieldDefinitionRepository.findAll()).thenReturn(List.of(
                definition("Orphan", null, "description", null),
                definition("Covering", "roofing", "description", null)));

        List<ReportLayoutPlace> layout = inspectionFieldDefinitionService.getReportLayout();

        assertThat(layout).extracting(ReportLayoutPlace::place).containsExactly("roofing");
    }

    // SAVING AN ORDER

    @Test
    void saveReportOrder_numbersTheDefinitionsFromZeroInTheOrderSent() {
        InspectionFieldDefinition covering = definition("Covering", "roofing", "description", null);
        InspectionFieldDefinition flashing = definition("Flashing", "roofing", "description", null);
        when(inspectionFieldDefinitionRepository.findByFieldPlaceAndFieldType("roofing", "description"))
                .thenReturn(List.of(covering, flashing));

        inspectionFieldDefinitionService.saveReportOrder("roofing", "description",
                List.of(flashing.getId(), covering.getId()));

        assertThat(flashing.getReportOrder()).isZero();
        assertThat(covering.getReportOrder()).isEqualTo(1);
    }

    @Test
    void saveReportOrder_definitionTheClientDidNotList_keepsPrintingAfterTheOrderedOnes() {
        // A definition seeded while the page was open would otherwise be left on a null
        // order, which sorts it to the end anyway — but only until someone reorders again.
        InspectionFieldDefinition covering = definition("Covering", "roofing", "description", null);
        InspectionFieldDefinition added = definition("Added later", "roofing", "description", null);
        when(inspectionFieldDefinitionRepository.findByFieldPlaceAndFieldType("roofing", "description"))
                .thenReturn(List.of(covering, added));

        inspectionFieldDefinitionService.saveReportOrder("roofing", "description", List.of(covering.getId()));

        assertThat(covering.getReportOrder()).isZero();
        assertThat(added.getReportOrder()).isEqualTo(1);
    }

    @Test
    void saveReportOrder_idFromAnotherSection_isRejectedWithoutRenumbering() {
        // Renumbering on a stale page would silently move a definition out of its section.
        InspectionFieldDefinition covering = definition("Covering", "roofing", "description", 0);
        when(inspectionFieldDefinitionRepository.findByFieldPlaceAndFieldType("roofing", "description"))
                .thenReturn(List.of(covering));

        var response = inspectionFieldDefinitionService.saveReportOrder("roofing", "description",
                List.of(covering.getId(), UUID.randomUUID()));

        assertThat(response.getStatusCode().is4xxClientError()).isTrue();
        verify(inspectionFieldDefinitionRepository, never()).saveAll(anyList());
    }

    @Test
    void saveReportOrder_repeatedId_isRejectedInsteadOfHalfApplied() {
        InspectionFieldDefinition covering = definition("Covering", "roofing", "description", 0);
        InspectionFieldDefinition flashing = definition("Flashing", "roofing", "description", 1);
        when(inspectionFieldDefinitionRepository.findByFieldPlaceAndFieldType("roofing", "description"))
                .thenReturn(List.of(covering, flashing));

        var response = inspectionFieldDefinitionService.saveReportOrder("roofing", "description",
                List.of(covering.getId(), covering.getId()));

        assertThat(response.getStatusCode().is4xxClientError()).isTrue();
        verify(inspectionFieldDefinitionRepository, never()).saveAll(anyList());
    }

    @Test
    void saveReportOrder_mixedCasePlaceAndType_stillFindsTheSection() {
        InspectionFieldDefinition covering = definition("Covering", "roofing", "description", null);
        when(inspectionFieldDefinitionRepository.findByFieldPlaceAndFieldType("roofing", "description"))
                .thenReturn(List.of(covering));

        inspectionFieldDefinitionService.saveReportOrder("Roofing", "Description", List.of(covering.getId()));

        ArgumentCaptor<List<InspectionFieldDefinition>> captor = ArgumentCaptor.forClass(List.class);
        verify(inspectionFieldDefinitionRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).containsExactly(covering);
    }

    @Test
    void saveReportOrder_noIdsAtAll_isRejected() {
        var response = inspectionFieldDefinitionService.saveReportOrder("roofing", "description", null);

        assertThat(response.getStatusCode().is4xxClientError()).isTrue();
        verify(inspectionFieldDefinitionRepository, never()).saveAll(anyList());
    }
}
