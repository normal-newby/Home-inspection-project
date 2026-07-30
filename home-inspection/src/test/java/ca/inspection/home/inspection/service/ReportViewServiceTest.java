package ca.inspection.home.inspection.service;

import ca.inspection.home.inspection.entity.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class ReportViewServiceTest {

    @Mock
    private InspectionImagesService inspectionImagesService;

    @InjectMocks
    private ReportViewService reportViewService;

    // Helper Functions

    private InspectionField fieldWithTypeAndPlace(String place, String type){
        InspectionFieldDefinition definition = new InspectionFieldDefinition();
        definition.setFieldType(type);
        definition.setFieldPlace(place);

        InspectionField field = new InspectionField();
        field.setId(UUID.randomUUID());
        field.setInspectionFieldDefinition(definition);

        return field;
    }

    private InspectionField fieldWithSummary(String place,String type, boolean inSummary){
        InspectionField field = fieldWithTypeAndPlace(place, type);
        field.setIncludeInSummary(inSummary);
        field.setInspectionRecommendationField(new InspectionRecommendationField());

        return field;
    }

    // Comparator Tests

    @Test
    void getComparator_differentPlaces_sortsInOrder(){
        InspectionField field = fieldWithTypeAndPlace("roofing", "description");
        InspectionField field2 = fieldWithTypeAndPlace("plumbing", "description");
        Comparator<InspectionField> comparator = reportViewService.getComparator();

        int result = comparator.compare(field, field2);

        assertThat(result).isNegative();
    }

    @Test
    void getComparator_samePlaceDifferentType_sortsInOrder(){
        InspectionField field = fieldWithTypeAndPlace("roofing", "description");
        InspectionField field2 = fieldWithTypeAndPlace("roofing", "limitations");
        Comparator<InspectionField> comparator = reportViewService.getComparator();

        int result = comparator.compare(field, field2);

        assertThat(result).isNegative();
    }

    @Test
    void getComparator_unknownPlace_sortsToEnd(){
        InspectionField known = fieldWithTypeAndPlace("cooling", "description");
        InspectionField unknown = fieldWithTypeAndPlace("basement", "description");
        Comparator<InspectionField> comparator = reportViewService.getComparator();

        int result = comparator.compare(known, unknown);

        assertThat(result).isNegative();
    }

    @Test
    void getComparator_samePlaceAndType_isEqual(){
        InspectionField field = fieldWithTypeAndPlace("cooling", "description");
        InspectionField field2 = fieldWithTypeAndPlace("cooling", "description");
        Comparator<InspectionField> comparator = reportViewService.getComparator();

        int result = comparator.compare(field, field2);

        assertThat(result).isZero();
    }

    // Summary Field Tests

    @Test
    void getSummaryFields_wrongType_excluded() {
        InspectionField description = fieldWithSummary("roofing", "description", true);

        List<InspectionField> fields = List.of(description);
        Map<String, List<InspectionField>> result = reportViewService.getSummaryFields(fields);

        assertThat(result).isEmpty();
    }

    @Test
    void getSummaryFields_notIncludedInSummary_excluded() {
        InspectionField noSummary = fieldWithSummary("roofing", "recommendations", false);

        List<InspectionField> fields = List.of(noSummary);
        Map<String, List<InspectionField>> result = reportViewService.getSummaryFields(fields);

        assertThat(result).isEmpty();
    }

    @Test
    void getSummaryFields_noRecommendationField_excluded() {
        InspectionField noRecommendationField = fieldWithSummary("roofing", "recommendations", true);
        noRecommendationField.setInspectionRecommendationField(null);

        List<InspectionField> fields = List.of(noRecommendationField);
        Map<String, List<InspectionField>> result = reportViewService.getSummaryFields(fields);

        assertThat(result).isEmpty();
    }

    @Test
    void getSummaryFields_meetAllConditions_included() {
        InspectionField correct = fieldWithSummary("roofing", "recommendations", true);

        List<InspectionField> fields = List.of(correct);
        Map<String, List<InspectionField>> result = reportViewService.getSummaryFields(fields);

        assertThat(result).containsOnlyKeys("roofing");
        assertThat(result.get("roofing")).containsExactly(correct);
    }

    @Test
    void getSummaryFields_multiplePlaces_groupedSeparately(){
        InspectionField roofing = fieldWithSummary("roofing", "recommendations", true);
        InspectionField exterior = fieldWithSummary("exterior", "recommendations", true);

        List<InspectionField> fields = List.of(roofing, exterior);
        Map<String, List<InspectionField>> result = reportViewService.getSummaryFields(fields);

        assertThat(result).containsOnlyKeys("roofing", "exterior");
        assertThat(result.get("roofing")).containsExactly(roofing);
        assertThat(result.get("exterior")).containsExactly(exterior);
    }

    // Get all fields

    @Test
    void getAllFields_samePlaceSameType_groupedTogether(){
        InspectionField field1 = fieldWithTypeAndPlace("roofing", "description");
        InspectionField field2 = fieldWithTypeAndPlace("roofing", "description");

        List<InspectionField> fields = List.of(field1, field2);
        Map<String, Map<String, List<InspectionField>>> result = reportViewService.getAllFields(fields);

        assertThat(result).containsOnlyKeys("roofing");
        assertThat(result.get("roofing")).containsOnlyKeys("description");
        assertThat(result.get("roofing").get("description")).containsExactlyInAnyOrder(field1, field2);
    }

    @Test
    void getAllFields_samePlaceDifferentType_groupedSeparately(){
        InspectionField description = fieldWithTypeAndPlace("roofing", "description");
        InspectionField limitations = fieldWithTypeAndPlace("roofing", "limitations");

        List<InspectionField> fields = List.of(description, limitations);
        Map<String, Map<String, List<InspectionField>>> result = reportViewService.getAllFields(fields);

        assertThat(result.get("roofing")).containsOnlyKeys("description", "limitations");
        assertThat(result.get("roofing").get("description")).containsExactly(description);
        assertThat(result.get("roofing").get("limitations")).containsExactly(limitations);
    }

    @Test
    void getAllFields_differentPlace_groupedSeparately(){
        InspectionField roofing = fieldWithTypeAndPlace("roofing", "description");
        InspectionField exterior = fieldWithTypeAndPlace("exterior", "description");

        List<InspectionField> fields = List.of(roofing, exterior);
        Map<String, Map<String, List<InspectionField>>> result = reportViewService.getAllFields(fields);

        assertThat(result).containsOnlyKeys("roofing", "exterior");
        assertThat(result.get("roofing").get("description")).containsExactly(roofing);
        assertThat(result.get("exterior").get("description")).containsExactly(exterior);
    }

    // Get Sorted Fields

    @Test
    void getSortedFields_fieldWithNullDefinition_filteredOut(){
        InspectionField incompleteField = new InspectionField();
        InspectionField completeField = fieldWithTypeAndPlace("roofing", "description");

        InspectionReport report = new InspectionReport();
        report.setFields(Set.of(incompleteField, completeField));

        List<InspectionField> result = reportViewService.getSortedFields(report);

        assertThat(result).containsExactly(completeField);
    }

    @Test
    void getSortedFields_fieldWithImages_convertsImageToBase64(){
        InspectionField field = fieldWithTypeAndPlace("roofing", "description");
        InspectionImage image = new InspectionImage();
        image.setId(UUID.randomUUID());
        field.setInspectionImages(List.of(image));

        InspectionReport report = new InspectionReport();
        report.setFields(Set.of(field));

        // Return mock base 64 when function called
        when(inspectionImagesService.toBase64(eq(image.getId()), any()))
                .thenReturn("data:image/jpeg;base64,FAKE");

        reportViewService.getSortedFields(report);

        assertThat(image.getBase64()).isEqualTo("data:image/jpeg;base64,FAKE");
    }

    @Test
    void getSortedFields_fieldsWithNoImages_skipsBase64(){
        InspectionField field = fieldWithTypeAndPlace("roofing", "description");
        field.setInspectionImages(new ArrayList<>());

        InspectionReport report = new InspectionReport();
        report.setFields(Set.of(field));

        reportViewService.getSortedFields(report);

        verifyNoInteractions(inspectionImagesService);
    }

}
