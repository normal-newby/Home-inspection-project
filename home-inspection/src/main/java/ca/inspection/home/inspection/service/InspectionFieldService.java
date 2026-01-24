package ca.inspection.home.inspection.service;

import ca.inspection.home.inspection.entity.*;
import ca.inspection.home.inspection.repository.*;
import org.apache.coyote.Response;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class InspectionFieldService {
    @Autowired
    private InspectionFieldRepository inspectionFieldRepository;

    @Autowired
    private InspectionBookingsRepository inspectionBookingsRepository;

    @Autowired
    private InspectionFieldDefinitionRepository inspectionFieldDefinitionRepository;

    @Autowired
    private InspectionFieldDefinitionValueRepository inspectionFieldDefinitionValueRepository;

    @Autowired
    private InspectionBookingsService inspectionBookingsService;

    @Autowired
    private InspectionImagesRepository inspectionImagesRepository;

    public ResponseEntity<?> createNewInspectionField(UUID id,
                                                   String place,
                                                   String type,
                                                   String name,
                                                   String value){
        try {
            //report
            InspectionReport report = inspectionBookingsService.getReportFromBooking(id);

            //definition
            InspectionFieldDefinition definition = inspectionFieldDefinitionRepository
                    .findWithValues(place, type, name);

            //create value
            InspectionFieldDefinitionValue definitionValue = definition.getPossibleValues().stream()
                    .filter(v -> v.getValue().equals(value))
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("value not found"));

            //create field
            InspectionField inspectionField = new InspectionField();
            inspectionField.setInspectionReport(report);
            inspectionField.setInspectionFieldDefinition(definition);
            inspectionField.setSelectedValue(definitionValue);

            System.out.println(definition.getId());

            InspectionField saved = inspectionFieldRepository.save(inspectionField);

            return ResponseEntity.ok().build();
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    public List<InspectionField> getInspectionFields(UUID id,
                                                                  String place,
                                                                  String type,
                                                                  String name){
        try {
            //report
            UUID reportId = inspectionBookingsService.getReportFromBooking(id).getId();

            //Get
           return inspectionFieldRepository.getInspectionFields(reportId, place, type, name);

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public void deleteInspectionField(UUID id){
        InspectionField field = inspectionFieldRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("InspectionField not found"));

        inspectionFieldRepository.delete(field);
    }

    public ResponseEntity<?> addImageToField(UUID fieldId, UUID imageId){
        try {
            InspectionField field = inspectionFieldRepository.findById(fieldId)
                    .orElseThrow(() -> new RuntimeException("InspectionField not found"));
            InspectionImage image = inspectionImagesRepository.findById(imageId)
                    .orElseThrow(() -> new RuntimeException("InspectionField not found"));
            field.setInspectionImage(image);
            image.getFields().add(field);
            inspectionFieldRepository.save(field);

            return ResponseEntity.ok().build();
        } catch (Exception e){
            e.printStackTrace();
            return ResponseEntity.badRequest().build();
        }
    }
}
