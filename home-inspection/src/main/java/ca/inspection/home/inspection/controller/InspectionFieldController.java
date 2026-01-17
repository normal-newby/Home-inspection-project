package ca.inspection.home.inspection.controller;

import ca.inspection.home.inspection.service.InspectionFieldService;
import org.apache.coyote.Response;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.stereotype.Repository;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.UUID;

@Controller
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class InspectionFieldController {
    @Autowired
    private InspectionFieldService inspectionFieldService;

    @PostMapping("/field/{id}/{place}/{type}/{name}/{value}")
    public ResponseEntity<?> createNewInspectionField(@PathVariable UUID id,
                                                   @PathVariable String place,
                                                   @PathVariable String type,
                                                   @PathVariable String name,
                                                   @PathVariable String value){
        return inspectionFieldService.createNewInspectionField(id, place, type, name, value);
    }
}
