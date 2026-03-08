package ca.inspection.home.inspection.controller;

import ca.inspection.home.inspection.entity.InspectionField;
import ca.inspection.home.inspection.entity.ImageAnnotation;
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

    @DeleteMapping("/fields/{id}")
    public void deleteInspectionField(@PathVariable UUID id){
        inspectionFieldService.deleteInspectionField(id);
    }

    @PutMapping("/fields/{fieldId}/{imageId}")
    public ResponseEntity<?> addImageToField(@PathVariable UUID fieldId, @PathVariable UUID imageId){
        return inspectionFieldService.addImageToField(fieldId, imageId);
    }

    @PostMapping("/fields/{fieldId}/annotations/save")
    public ResponseEntity<?> addAnnotation(@PathVariable UUID fieldId, @RequestBody ImageAnnotation annotation){
        return inspectionFieldService.addAnnotation(fieldId, annotation);
    }

    @GetMapping("/fields/{fieldId}/annotations")
    public List<ImageAnnotation> getAnnotations(@PathVariable UUID fieldId){
        return inspectionFieldService.getAnnotations(fieldId);
    }

    @DeleteMapping("/annotations/{annotationId}")
    public ResponseEntity<?> deleteAnnotation(@PathVariable UUID annotationId){
        return inspectionFieldService.deleteAnnotation(annotationId);
    }
}
