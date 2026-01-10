package ca.inspection.home.inspection.controller;

import ca.inspection.home.inspection.entity.InspectionFieldDefinition;
import ca.inspection.home.inspection.service.InspectionFieldDefinitionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class InspectionFieldDefinitionController {
    @Autowired
    private InspectionFieldDefinitionService inspectionFieldDefinitionService;

    @GetMapping("/fields/definition/{place}/{type}/get")
    public List<InspectionFieldDefinition> getAllFields(@PathVariable String place, @PathVariable String type){
        return inspectionFieldDefinitionService.getAllFields(place, type);
    }
}
