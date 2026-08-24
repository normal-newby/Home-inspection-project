package ca.inspection.home.inspection.service;

import ca.inspection.home.inspection.entity.*;
import ca.inspection.home.inspection.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class InspectionFieldServiceTest {

    @Mock
    private InspectionBookingsService inspectionBookingsService;

    @Mock
    private InspectionFieldDefinitionRepository inspectionFieldDefinitionRepository;

    @Mock
    private InspectionFieldRepository inspectionFieldRepository;

    @Mock
    private InspectionImagesRepository inspectionImagesRepository;

    @Mock
    private ImageAnnotationRepository imageAnnotationRepository;

    @Mock
    private InspectionRecommendationFieldRepository inspectionRecommendationFieldRepository;

    @Mock
    private InspectionFieldDefinitionValueRepository definitionValueRepository;

    @InjectMocks
    private InspectionFieldService inspectionFieldService;

    // FIELDS TESTS

    // Create New Inspection Field

    @Test
    void createNewInspectionField_valueExists_fieldSaved(){
        InspectionReport report = new InspectionReport();
        UUID bookingId = UUID.randomUUID();

        InspectionFieldDefinitionValue definitionValue = new InspectionFieldDefinitionValue();
        String value = "Asphalt Shingles";
        definitionValue.setValue(value);
        InspectionFieldDefinition definition = new InspectionFieldDefinition();
        UUID definitionId = UUID.randomUUID();
        definition.setId(definitionId);
        definition.setPossibleValues(List.of(definitionValue));

        when(inspectionBookingsService.getReportFromBooking(bookingId)).thenReturn(report);
        when(inspectionFieldDefinitionRepository.findWithValues(definitionId)).thenReturn(definition);
        when(inspectionFieldRepository.save(any(InspectionField.class))).thenAnswer(res -> res.getArgument(0));

        ResponseEntity<?> result = inspectionFieldService.createNewInspectionField(bookingId, definitionId, value);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isInstanceOfSatisfying(InspectionField.class, saved -> {
            assertThat(saved.getInspectionReport()).isEqualTo(report);
            assertThat(saved.getInspectionFieldDefinition()).isEqualTo(definition);
            assertThat(saved.getSelectedValue()).isEqualTo(definitionValue);
        });
    }

    @Test
    void createNewInspectionField_definitionNotFound_returnsBadRequest(){
        InspectionReport report = new InspectionReport();
        UUID bookingId = UUID.randomUUID();
        UUID definitionId = UUID.randomUUID();

        when(inspectionBookingsService.getReportFromBooking(bookingId)).thenReturn(report);
        when(inspectionFieldDefinitionRepository.findWithValues(definitionId)).thenReturn(null);

        ResponseEntity<?> result = inspectionFieldService.createNewInspectionField(bookingId, definitionId, "Roofing");

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void createNewInspectionField_valueNotExists_returnsBadRequest(){
        InspectionReport report = new InspectionReport();
        UUID bookingId = UUID.randomUUID();

        InspectionFieldDefinitionValue definitionValue = new InspectionFieldDefinitionValue();
        definitionValue.setValue("Asphalt Shingles");
        InspectionFieldDefinition definition = new InspectionFieldDefinition();
        UUID definitionId = UUID.randomUUID();
        definition.setId(definitionId);
        definition.setPossibleValues(List.of(definitionValue));

        when(inspectionBookingsService.getReportFromBooking(bookingId)).thenReturn(report);
        when(inspectionFieldDefinitionRepository.findWithValues(definitionId)).thenReturn(definition);

        ResponseEntity<?> result = inspectionFieldService.createNewInspectionField(bookingId, definitionId, "Shingles");

        assertThat(result.getBody()).isEqualTo("value not found");
        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // Get Already Existing Fields Combined

    @Test
    void getAlreadyExistingFieldsCombined_fieldNotInPlace_excluded(){
        InspectionReport report = new InspectionReport();
        UUID reportId = UUID.randomUUID();
        report.setId(reportId);
        UUID bookingId = UUID.randomUUID();

        String place = "roofing";
        String type = "description";

        when(inspectionBookingsService.getReportFromBooking(bookingId)).thenReturn(report);
        when(inspectionFieldRepository.getExistingFieldsForPlaceAndType(reportId, "exterior", type))
                .thenReturn(List.of());

        Map<UUID, List<InspectionField>> result = inspectionFieldService.getAlreadyExistingFieldsCombined(
                bookingId, place, type
        );

        assertThat(result).isEmpty();
    }

    @Test
    void getAlreadyExistingFieldsCombined_differentDefinitions_groupedDifferently(){
        InspectionReport report = new InspectionReport();
        UUID reportId = UUID.randomUUID();
        report.setId(reportId);
        UUID bookingId = UUID.randomUUID();

        InspectionFieldDefinition definition1 = new InspectionFieldDefinition();
        UUID definition1Id = UUID.randomUUID();
        definition1.setId(definition1Id);
        InspectionFieldDefinition definition2 = new InspectionFieldDefinition();
        UUID definition2Id = UUID.randomUUID();
        definition2.setId(definition2Id);

        InspectionField field1 = new InspectionField();
        field1.setInspectionFieldDefinition(definition1);
        InspectionField field2 = new InspectionField();
        field2.setInspectionFieldDefinition(definition2);

        String place = "roofing";
        String type = "description";

        when(inspectionBookingsService.getReportFromBooking(bookingId)).thenReturn(report);
        when(inspectionFieldRepository.getExistingFieldsForPlaceAndType(reportId, place, type))
                .thenReturn(List.of(field1, field2));

        Map<UUID, List<InspectionField>> result = inspectionFieldService.getAlreadyExistingFieldsCombined(bookingId, place, type);

        assertThat(result).containsOnlyKeys(definition1Id, definition2Id);
        assertThat(result.get(definition1Id)).containsExactly(field1);
        assertThat(result.get(definition2Id)).containsExactly(field2);
    }

    @Test
    void getAlreadyExistingFieldsCombined_sameDefinition_groupedTogether(){
        InspectionReport report = new InspectionReport();
        UUID reportId = UUID.randomUUID();
        report.setId(reportId);
        UUID bookingId = UUID.randomUUID();

        InspectionFieldDefinition definition = new InspectionFieldDefinition();
        UUID definitionId = UUID.randomUUID();
        definition.setId(definitionId);

        InspectionField field1 = new InspectionField();
        field1.setInspectionFieldDefinition(definition);
        InspectionField field2 = new InspectionField();
        field2.setInspectionFieldDefinition(definition);

        String place = "roofing";
        String type = "description";

        when(inspectionBookingsService.getReportFromBooking(bookingId)).thenReturn(report);
        when(inspectionFieldRepository.getExistingFieldsForPlaceAndType(reportId, place, type))
                .thenReturn(List.of(field1, field2));

        Map<UUID, List<InspectionField>> result = inspectionFieldService.getAlreadyExistingFieldsCombined(bookingId, place, type);

        assertThat(result).containsOnlyKeys(definitionId);
        assertThat(result.get(definitionId)).containsExactlyInAnyOrder(field1, field2);
    }

    // Delete InspectionField

    @Test
    void deleteInspectionField_fieldExists_deleted(){
        UUID fieldId = UUID.randomUUID();

        when(inspectionFieldRepository.existsById(fieldId)).thenReturn(true);

        inspectionFieldService.deleteInspectionField(fieldId);

        verify(inspectionFieldRepository).deleteById(fieldId);
    }

    @Test
    void deleteInspectionField_fieldNotFound_throwsRuntimeException(){
        UUID fieldId = UUID.randomUUID();

        when(inspectionFieldRepository.existsById(fieldId)).thenReturn(false);

        assertThatThrownBy(() -> inspectionFieldService.deleteInspectionField(fieldId))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("InspectionField not found");
    }

    // IMAGE TESTS

    // Add Image To Field

    @Test
    void addImageToField_fieldAndImageExist_imageSaved(){
        InspectionField fieldRef = new InspectionField();
        UUID fieldId = UUID.randomUUID();
        fieldRef.setId(fieldId);
        InspectionImage image = new InspectionImage();
        UUID imageId = UUID.randomUUID();

        when(inspectionFieldRepository.existsById(fieldId)).thenReturn(true);
        when(inspectionImagesRepository.findById(imageId)).thenReturn(Optional.of(image));
        when(inspectionFieldRepository.getReferenceById(fieldId)).thenReturn(fieldRef);

        ResponseEntity<?> result = inspectionFieldService.addImageToField(fieldId, imageId);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isEqualTo(Map.of("Image added", true));
        assertThat(image.getInspectionField()).isEqualTo(fieldRef);
        assertThat(image.getUsed()).isTrue();
        verify(inspectionImagesRepository).save(image);
    }

    @Test
    void addImageToField_fieldNotFound_badRequest(){
        UUID fieldId = UUID.randomUUID();
        UUID imageId = UUID.randomUUID();

        when(inspectionFieldRepository.existsById(fieldId)).thenReturn(false);

        ResponseEntity<?> result = inspectionFieldService.addImageToField(fieldId, imageId);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(result.getBody()).isEqualTo("InspectionField not found");
    }

    @Test
    void addImageToField_imageNotFound_badRequest(){
        UUID fieldId = UUID.randomUUID();
        UUID imageId = UUID.randomUUID();

        when(inspectionFieldRepository.existsById(fieldId)).thenReturn(true);
        when(inspectionImagesRepository.findById(imageId)).thenReturn(Optional.empty());

        ResponseEntity<?> result = inspectionFieldService.addImageToField(fieldId, imageId);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(result.getBody()).isEqualTo("InspectionImage not found");
    }

    // Delete Image From Field

    @Test
    void deleteImageFromField_successfulDelete_removeRelation(){
        InspectionField field = new InspectionField();
        UUID fieldId = UUID.randomUUID();
        InspectionImage image = new InspectionImage();
        UUID imageId = UUID.randomUUID();
        image.setInspectionField(field);
        image.setUsed(true);

        when(inspectionImagesRepository.findById(imageId)).thenReturn(Optional.of(image));

        ResponseEntity<?> result = inspectionFieldService.deleteImageFromField(fieldId, imageId);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isEqualTo(Map.of("Image deleted", true));
        assertThat(image.getInspectionField()).isNull();
        assertThat(image.getUsed()).isFalse();
        verify(inspectionImagesRepository).save(image);
    }

    @Test
    void deleteImageFromField_imageNotFound_badRequest(){
        UUID fieldId = UUID.randomUUID();
        UUID imageId = UUID.randomUUID();

        when(inspectionImagesRepository.findById(imageId)).thenReturn(Optional.empty());

        ResponseEntity<?> result = inspectionFieldService.deleteImageFromField(fieldId, imageId);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(result.getBody()).isEqualTo("InspectionImage not found");
    }

    // NOTE TESTS

    // Add Note To field

    @Test
    void addNoteToField_hasField_saved(){
        InspectionField field = new InspectionField();
        UUID fieldId = UUID.randomUUID();
        field.setId(fieldId);
        String note = "note";

        when(inspectionFieldRepository.findById(fieldId)).thenReturn(Optional.of(field));

        ResponseEntity<?> result = inspectionFieldService.addNoteToField(fieldId, note);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(field.getNote()).isEqualTo(note);
        verify(inspectionFieldRepository).save(field);
    }

    @Test
    void addNoteToField_fieldNotExist_throwsBadRequest(){
        UUID fieldId = UUID.randomUUID();
        String note = "note";

        when(inspectionFieldRepository.findById(fieldId)).thenReturn(Optional.empty());

        ResponseEntity<?> result = inspectionFieldService.addNoteToField(fieldId, note);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // Get Note From Field

    @Test
    void getNoteFromField_hasField_getsField(){
        InspectionField field = new InspectionField();
        UUID fieldId = UUID.randomUUID();
        String note = "note";
        field.setNote(note);
        field.setId(fieldId);

        when(inspectionFieldRepository.findById(fieldId)).thenReturn(Optional.of(field));

        String result = inspectionFieldService.getNoteFromField(fieldId);

        assertThat(result).isEqualTo(note);
    }

    @Test
    void getNoteFromField_fieldNotExist_returnsNull(){
        UUID fieldId = UUID.randomUUID();

        when(inspectionFieldRepository.findById(fieldId)).thenReturn(Optional.empty());

        String result = inspectionFieldService.getNoteFromField(fieldId);

        assertThat(result).isNull();
    }

    // SUMMARY TESTS

    // Update Field In Summary

    @Test
    void updateFieldInSummary_hasField_updatesInSummary(){
        InspectionField field = new InspectionField();
        UUID fieldId = UUID.randomUUID();
        field.setId(fieldId);
        boolean inSummary = true;

        when(inspectionFieldRepository.findById(fieldId)).thenReturn(Optional.of(field));

        ResponseEntity<?> result = inspectionFieldService.updateFieldInSummary(fieldId, inSummary);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(field.getIncludeInSummary()).isTrue();
        verify(inspectionFieldRepository).save(field);
    }

    @Test
    void updateFieldInSummary_fieldNotExist_throwsException(){
        UUID fieldId = UUID.randomUUID();

        when(inspectionFieldRepository.findById(fieldId)).thenReturn(Optional.empty());

        ResponseEntity<?> result = inspectionFieldService.updateFieldInSummary(fieldId, false);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // Check In Summary

    @Test
    void checkInSummary_hasField_getsInSummary(){
        InspectionField field = new InspectionField();
        UUID fieldId = UUID.randomUUID();
        field.setIncludeInSummary(true);
        field.setId(fieldId);

        when(inspectionFieldRepository.findById(fieldId)).thenReturn(Optional.of(field));

        boolean result = inspectionFieldService.checkInSummary(fieldId);

        assertThat(result).isTrue();
    }

    @Test
    void checkInSummary_nullSummary_returnsFalse(){
        InspectionField field = new InspectionField();
        UUID fieldId = UUID.randomUUID();
        field.setId(fieldId);

        when(inspectionFieldRepository.findById(fieldId)).thenReturn(Optional.of(field));

        boolean result = inspectionFieldService.checkInSummary(fieldId);

        assertThat(result).isFalse();
    }

    @Test
    void checkInSummary_fieldNotExist_returnsFalse(){
        UUID fieldId = UUID.randomUUID();

        when(inspectionFieldRepository.findById(fieldId)).thenReturn(Optional.empty());

        boolean result = inspectionFieldService.checkInSummary(fieldId);

        assertThat(result).isFalse();
    }

    // ANNOTATIONS TESTS

    // Add Annotation

    @Test
    void addAnnotation_hasImage_savedCorrectly(){
        InspectionImage image = new InspectionImage();
        UUID imageId = UUID.randomUUID();
        image.setId(imageId);
        ImageAnnotation annotation = new ImageAnnotation();

        when(inspectionImagesRepository.findById(imageId)).thenReturn(Optional.of(image));

        ResponseEntity<?> result = inspectionFieldService.addAnnotation(imageId, annotation);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(annotation.getInspectionImage()).isEqualTo(image);
        verify(imageAnnotationRepository).save(annotation);
    }

    @Test
    void addAnnotation_imageNotExist_returnsBadRequest(){
        UUID imageId = UUID.randomUUID();
        ImageAnnotation annotation = new ImageAnnotation();

        when(inspectionImagesRepository.findById(imageId)).thenReturn(Optional.empty());

        ResponseEntity<?> result = inspectionFieldService.addAnnotation(imageId, annotation);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // Get Annotations

    @Test
    void getAnnotations_hasAnnotations_returnsCorrectAnnotations(){
        UUID imageId = UUID.randomUUID();

        ImageAnnotation annotation1 = new ImageAnnotation();
        annotation1.setId(UUID.randomUUID());
        ImageAnnotation annotation2 = new ImageAnnotation();
        annotation2.setId(UUID.randomUUID());

        when(imageAnnotationRepository.findByInspectionImageId(imageId)).thenReturn(
                List.of(annotation1, annotation2)
        );

        List<ImageAnnotation> result = inspectionFieldService.getAnnotations(imageId);

        assertThat(result).containsExactlyInAnyOrder(annotation1, annotation2);
    }

    @Test
    void getAnnotations_noAnnotations_returnsEmptyList(){
        UUID imageId = UUID.randomUUID();

        when(imageAnnotationRepository.findByInspectionImageId(imageId)).thenReturn(
                List.of()
        );

        List<ImageAnnotation> result = inspectionFieldService.getAnnotations(imageId);

        assertThat(result).isEmpty();
    }

    // Delete Annotations

    @Test
    void deleteAnnotations_hasAnnotation_deletesAnnotation(){
        ImageAnnotation annotation = new ImageAnnotation();
        UUID annotationId = UUID.randomUUID();
        annotation.setId(annotationId);

        when(imageAnnotationRepository.findById(annotationId)).thenReturn(Optional.of(annotation));

        ResponseEntity<?> result = inspectionFieldService.deleteAnnotation(annotationId);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(imageAnnotationRepository).delete(annotation);
    }

    @Test
    void deleteAnnotations_noAnnotation_returnsBadRequest(){
        UUID annotationId = UUID.randomUUID();

        when(imageAnnotationRepository.findById(annotationId)).thenReturn(Optional.empty());

        ResponseEntity<?> result = inspectionFieldService.deleteAnnotation(annotationId);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // RECOMMENDATION FIELDS

    // Get Recommendation Field

    @Test
    void getRecommendationField_existingRecommendation_returnsCorrectly(){
        InspectionField field = new InspectionField();
        UUID fieldId = UUID.randomUUID();
        field.setId(fieldId);

        InspectionRecommendationField recommendationField = new InspectionRecommendationField();
        recommendationField.setImplication("existing implication");
        field.setInspectionRecommendationField(recommendationField);

        InspectionFieldDefinitionValue value = new InspectionFieldDefinitionValue();
        value.setDefaultImplication("default implication");
        field.setSelectedValue(value);

        when(inspectionFieldRepository.findWithRecommendationAndValue(fieldId)).thenReturn(Optional.of(field));

        InspectionRecommendationField result = inspectionFieldService.getRecommendationField(fieldId);

        assertThat(result).isSameAs(recommendationField);
        assertThat(result.getImplication()).isEqualTo("existing implication");
    }

    @Test
    void getRecommendationField_noExistingRecommendation_hasDefaultImplication_returnsNewField(){
        InspectionField field = new InspectionField();
        UUID fieldId = UUID.randomUUID();
        field.setId(fieldId);
        field.setInspectionRecommendationField(null);

        InspectionFieldDefinitionValue value = new InspectionFieldDefinitionValue();
        value.setDefaultImplication("default implication");
        field.setSelectedValue(value);

        when(inspectionFieldRepository.findWithRecommendationAndValue(fieldId)).thenReturn(Optional.of(field));

        InspectionRecommendationField result = inspectionFieldService.getRecommendationField(fieldId);

        assertThat(result).isNotNull();
        assertThat(result.getImplication()).isEqualTo("default implication");
    }

    @Test
    void getRecommendationField_noExistingRecommendation_noDefaultImplication_returnsNullField(){
        InspectionField field = new InspectionField();
        UUID fieldId = UUID.randomUUID();
        field.setId(fieldId);
        field.setInspectionRecommendationField(null);

        InspectionFieldDefinitionValue value = new InspectionFieldDefinitionValue();
        value.setDefaultImplication(null);
        field.setSelectedValue(value);

        when(inspectionFieldRepository.findWithRecommendationAndValue(fieldId)).thenReturn(Optional.of(field));

        InspectionRecommendationField result = inspectionFieldService.getRecommendationField(fieldId);

        assertThat(result).isNull();
    }

    @Test
    void getRecommendationField_noExistingRecommendation_noSelectedValue_returnsNull(){
        InspectionField field = new InspectionField();
        UUID fieldId = UUID.randomUUID();
        field.setId(fieldId);
        field.setInspectionRecommendationField(null);
        field.setSelectedValue(null);

        when(inspectionFieldRepository.findWithRecommendationAndValue(fieldId)).thenReturn(Optional.of(field));

        InspectionRecommendationField result = inspectionFieldService.getRecommendationField(fieldId);

        assertThat(result).isNull();
    }

    @Test
    void getRecommendationField_fieldNotFound_returnsNull(){
        UUID fieldId = UUID.randomUUID();

        when(inspectionFieldRepository.findWithRecommendationAndValue(fieldId)).thenReturn(Optional.empty());

        InspectionRecommendationField result = inspectionFieldService.getRecommendationField(fieldId);

        assertThat(result).isNull();
    }

    // Add Recommendation Field

    @Test
    void addRecommendationField_noExisting_createsNewRecommendationField(){
        InspectionField field = new InspectionField();
        UUID fieldId = UUID.randomUUID();
        field.setId(fieldId);
        field.setInspectionRecommendationField(null);
        field.setSelectedValue(null);

        InspectionRecommendationField recommendationField = new InspectionRecommendationField();
        recommendationField.setDirection("North");
        recommendationField.setImplication("implication");

        InspectionRecommendationField saved = new InspectionRecommendationField();

        when(inspectionFieldRepository.findById(fieldId)).thenReturn(Optional.of(field));
        when(inspectionRecommendationFieldRepository.save(recommendationField)).thenReturn(saved);

        ResponseEntity<?> result = inspectionFieldService.addRecommendationField(fieldId, recommendationField, false);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isSameAs(saved);
        assertThat(recommendationField.getInspectionField()).isEqualTo(field);
        verify(inspectionRecommendationFieldRepository).save(recommendationField);
        verifyNoInteractions(definitionValueRepository);
    }

    @Test
    void addRecommendationField_existingRecommendation_updatesFieldsCorrectlyAndSaves(){
        InspectionField field = new InspectionField();
        UUID fieldId = UUID.randomUUID();
        field.setId(fieldId);
        field.setSelectedValue(null);

        InspectionRecommendationField existing = new InspectionRecommendationField();
        existing.setDirection("existing direction");
        existing.setRoom("existing room");
        field.setInspectionRecommendationField(existing);

        InspectionRecommendationField newField = new InspectionRecommendationField();
        newField.setDirection("North");
        newField.setFloorLevel("1st Floor");
        newField.setRoom("Kitchen");
        newField.setTask("Repair");
        newField.setTime("As soon as possible");
        newField.setLower_cost("100");
        newField.setUpper_cost("500");
        newField.setImplication("implication");

        when(inspectionFieldRepository.findById(fieldId)).thenReturn(Optional.of(field));
        when(inspectionRecommendationFieldRepository.save(existing)).thenReturn(existing);

        ResponseEntity<?> result = inspectionFieldService.addRecommendationField(fieldId, newField, false);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(existing.getDirection()).isEqualTo("North");
        assertThat(existing.getFloorLevel()).isEqualTo("1st Floor");
        assertThat(existing.getRoom()).isEqualTo("Kitchen");
        assertThat(existing.getTask()).isEqualTo("Repair");
        assertThat(existing.getTime()).isEqualTo("As soon as possible");
        assertThat(existing.getLower_cost()).isEqualTo("100");
        assertThat(existing.getUpper_cost()).isEqualTo("500");
        assertThat(existing.getImplication()).isEqualTo("implication");
        verify(inspectionRecommendationFieldRepository).save(existing);
    }

    @Test
    void addRecommendationField_saveAsDefaultImplication_withSelectedValue_savesCorrectly(){
        InspectionField field = new InspectionField();
        UUID fieldId = UUID.randomUUID();
        field.setId(fieldId);
        field.setInspectionRecommendationField(null);

        InspectionFieldDefinitionValue value = new InspectionFieldDefinitionValue();
        field.setSelectedValue(value);

        InspectionRecommendationField recommendationField = new InspectionRecommendationField();
        recommendationField.setImplication("implication");

        when(inspectionFieldRepository.findById(fieldId)).thenReturn(Optional.of(field));
        when(inspectionRecommendationFieldRepository.save(recommendationField)).thenReturn(recommendationField);

        inspectionFieldService.addRecommendationField(fieldId, recommendationField, true);

        assertThat(value.getDefaultImplication()).isEqualTo("implication");
        verify(definitionValueRepository).save(value);
    }

    @Test
    void addRecommendationField_saveAsDefaultImplication_noSelectedValue_noError(){
        InspectionField field = new InspectionField();
        UUID fieldId = UUID.randomUUID();
        field.setId(fieldId);
        field.setInspectionRecommendationField(null);
        field.setSelectedValue(null);

        InspectionRecommendationField recommendationField = new InspectionRecommendationField();
        recommendationField.setImplication("implication");

        when(inspectionFieldRepository.findById(fieldId)).thenReturn(Optional.of(field));
        when(inspectionRecommendationFieldRepository.save(recommendationField)).thenReturn(recommendationField);

        ResponseEntity<?> result = inspectionFieldService.addRecommendationField(fieldId, recommendationField, true);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        verifyNoInteractions(definitionValueRepository);
    }

    @Test
    void addRecommendationField_saveAsDefaultImplication_nullImplication_skips(){
        InspectionField field = new InspectionField();
        UUID fieldId = UUID.randomUUID();
        field.setId(fieldId);
        field.setInspectionRecommendationField(null);

        InspectionFieldDefinitionValue definitionValue = new InspectionFieldDefinitionValue();
        field.setSelectedValue(definitionValue);

        InspectionRecommendationField recommendationField = new InspectionRecommendationField();
        recommendationField.setImplication(null);

        when(inspectionFieldRepository.findById(fieldId)).thenReturn(Optional.of(field));
        when(inspectionRecommendationFieldRepository.save(recommendationField)).thenReturn(recommendationField);

        inspectionFieldService.addRecommendationField(fieldId, recommendationField, true);

        assertThat(definitionValue.getDefaultImplication()).isNull();
        verifyNoInteractions(definitionValueRepository);
    }

    @Test
    void addRecommendationField_saveAsDefaultIsFalse_skipsSaveAsDefault(){
        InspectionField field = new InspectionField();
        UUID fieldId = UUID.randomUUID();
        field.setId(fieldId);
        field.setInspectionRecommendationField(null);

        InspectionFieldDefinitionValue definitionValue = new InspectionFieldDefinitionValue();
        field.setSelectedValue(definitionValue);

        InspectionRecommendationField recommendationField = new InspectionRecommendationField();
        recommendationField.setImplication("implication");

        when(inspectionFieldRepository.findById(fieldId)).thenReturn(Optional.of(field));
        when(inspectionRecommendationFieldRepository.save(recommendationField)).thenReturn(recommendationField);

        inspectionFieldService.addRecommendationField(fieldId, recommendationField, false);

        assertThat(definitionValue.getDefaultImplication()).isNull();
        verifyNoInteractions(definitionValueRepository);
    }

    @Test
    void addRecommendationField_fieldNotFound_returnsBadRequest(){
        UUID fieldId = UUID.randomUUID();
        InspectionRecommendationField recommendationField = new InspectionRecommendationField();

        when(inspectionFieldRepository.findById(fieldId)).thenReturn(Optional.empty());

        ResponseEntity<?> result = inspectionFieldService.addRecommendationField(fieldId, recommendationField, false);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verifyNoInteractions(definitionValueRepository);
        verifyNoInteractions(inspectionRecommendationFieldRepository);
    }

}
