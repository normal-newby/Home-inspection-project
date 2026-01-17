package ca.inspection.home.inspection.service;

import ca.inspection.home.inspection.entity.InspectionField;
import ca.inspection.home.inspection.entity.InspectionFieldDefinition;
import ca.inspection.home.inspection.entity.InspectionFieldDefinitionValue;
import ca.inspection.home.inspection.entity.InspectionReport;
import ca.inspection.home.inspection.repository.InspectionFieldDefinitionRepository;
import ca.inspection.home.inspection.repository.InspectionFieldDefinitionValueRepository;
import ca.inspection.home.inspection.repository.InspectionFieldRepository;
import ca.inspection.home.inspection.repository.InspectionReportsRepository;
import org.apache.coyote.Response;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class InspectionFieldService {
    @Autowired
    private InspectionFieldRepository inspectionFieldRepository;

    @Autowired
    private InspectionReportsRepository inspectionReportsRepository;

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
            InspectionReport report = inspectionReportsRepository.findById(id)
                            .orElseThrow(() -> new RuntimeException("report not found"));

            //definition
            InspectionFieldDefinition definition = inspectionFieldDefinitionRepository
                    .findWithValues(place.toLowerCase(), type.toLowerCase(), name.toLowerCase());

            //create value
            InspectionFieldDefinitionValue definitionValue = inspectionFieldDefinitionValueRepository
                    .findByValue(value);

            //create field
            InspectionField inspectionField = new InspectionField();
            inspectionField.setInspectionReport(report);
            inspectionField.setInspectionFieldDefinition(definition);
            inspectionField.setSelectedValue(definitionValue);

            InspectionField saved = inspectionFieldRepository.save(inspectionField);

            return ResponseEntity.ok(saved);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}
