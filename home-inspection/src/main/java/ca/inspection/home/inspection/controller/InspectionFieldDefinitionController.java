package ca.inspection.home.inspection.controller;

import ca.inspection.home.inspection.entity.InspectionFieldDefinition;
import ca.inspection.home.inspection.service.InspectionFieldDefinitionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/fields")
@CrossOrigin(origins = "*")
public class InspectionFieldDefinitionController {
    @Autowired
    private InspectionFieldDefinitionService inspectionFieldDefinitionService;

    @PutMapping("/definition/{id}/expanded")
    public ResponseEntity<?> setExpandedByDefault(
            @PathVariable UUID id,
            @RequestBody Boolean value){
        return inspectionFieldDefinitionService.setExpandedByDefault(id, value);
    }

    @GetMapping("/definition/{id}/values")
    public InspectionFieldDefinition getFieldWithValues(@PathVariable UUID id){
        return inspectionFieldDefinitionService.getFieldWithValues(id);
    }

    @GetMapping("/definition/{place}/{type}")
    public List<InspectionFieldDefinition> getAllFields(@PathVariable String place, @PathVariable String type){
        return inspectionFieldDefinitionService.getAllFields(place, type);
    }
}
