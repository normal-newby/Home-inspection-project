package ca.inspection.home.inspection.service;

import ca.inspection.home.inspection.entity.InspectionFieldDefinition;
import ca.inspection.home.inspection.repository.InspectionFieldDefinitionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class InspectionFieldDefinitionService {
    @Autowired
    private InspectionFieldDefinitionRepository inspectionFieldDefinitionRepository;

    public List<InspectionFieldDefinition> getAllFields(String place, String type){
        try {
            place = place.toLowerCase();
            type = type.toLowerCase();
            return inspectionFieldDefinitionRepository.findAllByFieldPlaceAndFieldType(place, type);
        } catch (Exception e){
            e.printStackTrace();
            return null;
        }
    }
}
