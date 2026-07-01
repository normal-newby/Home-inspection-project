package ca.inspection.home.inspection.service;

import ca.inspection.home.inspection.entity.InspectionFieldDefinition;
import ca.inspection.home.inspection.entity.InspectionReport;
import ca.inspection.home.inspection.repository.InspectionFieldDefinitionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class InspectionFieldDefinitionService {
    @Autowired
    private InspectionFieldDefinitionRepository inspectionFieldDefinitionRepository;

    public List<InspectionFieldDefinition> getAllFields(String place, String type){
        try {
            place = place.toLowerCase();
            type = type.toLowerCase();

            return inspectionFieldDefinitionRepository.findAllWithValues(place, type);
        } catch (Exception e){
            e.printStackTrace();
            return null;
        }
    }

    public ResponseEntity<?> setExpandedByDefault(UUID id, Boolean value){
        try {
            InspectionFieldDefinition definition = inspectionFieldDefinitionRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("definition not found"));
            definition.setExpandedByDefault(value);
            inspectionFieldDefinitionRepository.save(definition);
            return ResponseEntity.ok().build();
        } catch (Exception e){
            e.printStackTrace();
            return ResponseEntity.badRequest().build();
        }
    }

    public InspectionFieldDefinition getFieldWithValues(UUID id){
        try {
            return inspectionFieldDefinitionRepository.findWithValues(id);
        } catch (Exception e){
            e.printStackTrace();
            return null;
        }
    }
}
