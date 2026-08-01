package ca.inspection.home.inspection.service;

import ca.inspection.home.inspection.entity.*;
import ca.inspection.home.inspection.repository.InspectionFieldDefinitionRepository;
import ca.inspection.home.inspection.repository.InspectionFieldRepository;
import ca.inspection.home.inspection.repository.InspectionImagesRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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

    @InjectMocks
    private InspectionFieldService inspectionFieldService;

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
        InspectionField field = new InspectionField();
        UUID fieldId = UUID.randomUUID();
        field.setId(fieldId);

        when(inspectionFieldRepository.findById(fieldId)).thenReturn(Optional.of(field));

        inspectionFieldService.deleteInspectionField(fieldId);

        verify(inspectionFieldRepository).delete(field);
    }

    @Test
    void deleteInspectionField_fieldNotFound_throwsRuntimeException(){
        UUID fieldId = UUID.randomUUID();

        when(inspectionFieldRepository.findById(fieldId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> inspectionFieldService.deleteInspectionField(fieldId))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("InspectionField not found");
    }

    // Add Image To Field

    @Test
    void addImageToField_fieldAndImageExist_imageSaved(){
        InspectionField field = new InspectionField();
        UUID fieldId = UUID.randomUUID();
        InspectionImage image = new InspectionImage();
        UUID imageId = UUID.randomUUID();

        when(inspectionFieldRepository.findById(fieldId)).thenReturn(Optional.of(field));
        when(inspectionImagesRepository.findById(imageId)).thenReturn(Optional.of(image));

        ResponseEntity<?> result = inspectionFieldService.addImageToField(fieldId, imageId);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isEqualTo(Map.of("Image added", true));
        assertThat(field.getInspectionImages()).containsExactly(image);
        assertThat(image.getInspectionField()).isEqualTo(field);
        assertThat(image.getUsed()).isTrue();
    }

    @Test
    void addImageToField_fieldNotFound_badRequest(){
        UUID fieldId = UUID.randomUUID();
        UUID imageId = UUID.randomUUID();

        when(inspectionFieldRepository.findById(fieldId)).thenReturn(Optional.empty());

        ResponseEntity<?> result = inspectionFieldService.addImageToField(fieldId, imageId);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(result.getBody()).isEqualTo("InspectionField not found");
    }

    @Test
    void addImageToField_imageNotFound_badRequest(){
        InspectionField field = new InspectionField();
        UUID fieldId = UUID.randomUUID();
        UUID imageId = UUID.randomUUID();

        when(inspectionFieldRepository.findById(fieldId)).thenReturn(Optional.of(field));
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
        field.getInspectionImages().add(image);
        image.setInspectionField(field);
        image.setUsed(true);

        when(inspectionFieldRepository.findById(fieldId)).thenReturn(Optional.of(field));
        when(inspectionImagesRepository.findById(imageId)).thenReturn(Optional.of(image));

        ResponseEntity<?> result = inspectionFieldService.deleteImageFromField(fieldId, imageId);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isEqualTo(Map.of("Image deleted", true));
        assertThat(field.getInspectionImages()).isEmpty();
        assertThat(image.getInspectionField()).isNull();
        assertThat(image.getUsed()).isFalse();
    }

    @Test
    void deleteImageFromField_fieldNotFound_badRequest(){
        UUID fieldId = UUID.randomUUID();
        UUID imageId = UUID.randomUUID();

        when(inspectionFieldRepository.findById(fieldId)).thenReturn(Optional.empty());

        ResponseEntity<?> result = inspectionFieldService.addImageToField(fieldId, imageId);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(result.getBody()).isEqualTo("InspectionField not found");
    }

    @Test
    void deleteImageFromField_imageNotFound_badRequest(){
        InspectionField field = new InspectionField();
        UUID fieldId = UUID.randomUUID();
        UUID imageId = UUID.randomUUID();

        when(inspectionFieldRepository.findById(fieldId)).thenReturn(Optional.of(field));
        when(inspectionImagesRepository.findById(imageId)).thenReturn(Optional.empty());

        ResponseEntity<?> result = inspectionFieldService.addImageToField(fieldId, imageId);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(result.getBody()).isEqualTo("InspectionImage not found");
    }
}
