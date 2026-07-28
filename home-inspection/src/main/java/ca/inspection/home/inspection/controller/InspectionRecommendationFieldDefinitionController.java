package ca.inspection.home.inspection.controller;

import ca.inspection.home.inspection.DTO.DefinitionValueDTO;
import ca.inspection.home.inspection.entity.InspectionRecommendationFieldDefinition;
import ca.inspection.home.inspection.service.InspectionRecommendationFieldDefinitionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/recommendation-definition")
@CrossOrigin(origins = "*")
public class InspectionRecommendationFieldDefinitionController {
    @Autowired
    private InspectionRecommendationFieldDefinitionService service;

    @GetMapping
    public Map<String, List<DefinitionValueDTO>> getAllDefinitions(){
        return service.getAllDefinitions();
    }

    @PostMapping
    public ResponseEntity<?> addDefinition(@RequestBody InspectionRecommendationFieldDefinition definition){
        return service.addDefinition(definition);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteDefinition(@PathVariable UUID id){
        return service.deleteDefinition(id);
    }
}
