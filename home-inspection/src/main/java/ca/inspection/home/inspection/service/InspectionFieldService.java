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

    public ResponseEntity<?> createNewInspectionField(UUID id,
                                                   String place,
                                                   String type,
                                                   String name,
                                                   String value){
        try {
            //report
            InspectionBookings booking = inspectionBookingsRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("booking not found"));
            InspectionReport report = booking.getInspectionReport();

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
            InspectionBookings booking = inspectionBookingsRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("booking not found"));
            UUID reportId = booking.getInspectionReport().getId();

            //Get
           return inspectionFieldRepository.getInspectionFields(reportId, place, type, name);

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}
