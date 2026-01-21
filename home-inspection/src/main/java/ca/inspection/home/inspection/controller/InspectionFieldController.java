package ca.inspection.home.inspection.controller;

import ca.inspection.home.inspection.entity.InspectionField;
import ca.inspection.home.inspection.service.InspectionFieldService;
import org.apache.coyote.Response;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.stereotype.Repository;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class InspectionFieldController {
    @Autowired
    private InspectionFieldService inspectionFieldService;

    @PostMapping("/fields/{id}/{place}/{type}/{name}/{value}")
    public ResponseEntity<?> createNewInspectionField(@PathVariable UUID id,
                                                   @PathVariable String place,
                                                   @PathVariable String type,
                                                   @PathVariable String name,
                                                   @PathVariable String value){
        return inspectionFieldService.createNewInspectionField(id, place, type, name, value);
    }

    @GetMapping("/fields/{id}/{place}/{type}/{name}")
    public List<InspectionField> getInspectionFields(@PathVariable UUID id,
                                                                  @PathVariable String place,
                                                                  @PathVariable String type,
                                                                  @PathVariable String name){
        return inspectionFieldService.getInspectionFields(id, place, type, name);
    }
}
