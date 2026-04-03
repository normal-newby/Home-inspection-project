package ca.inspection.home.inspection.controller;

import ca.inspection.home.inspection.entity.InspectionField;
import ca.inspection.home.inspection.entity.ImageAnnotation;
import ca.inspection.home.inspection.entity.InspectionRecommendationField;
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

    // Support names/values that may contain characters like '/' by using query parameters.
    @PostMapping("/fields/{id}/{place}/{type}")
    public ResponseEntity<?> createNewInspectionFieldQuery(@PathVariable UUID id,
                                                           @PathVariable String place,
                                                           @PathVariable String type,
                                                           @RequestParam String name,
                                                           @RequestParam String value){
        return inspectionFieldService.createNewInspectionField(id, place, type, name, value);
    }

    @GetMapping("/fields/{id}/{place}/{type}/{name}")
    public List<InspectionField> getInspectionFields(@PathVariable UUID id,
                                                                  @PathVariable String place,
                                                                  @PathVariable String type,
                                                                  @PathVariable String name){
        return inspectionFieldService.getInspectionFields(id, place, type, name);
    }

    @GetMapping("/fields/{id}/{place}/{type}")
    public List<InspectionField> getInspectionFieldsQuery(@PathVariable UUID id,
                                                          @PathVariable String place,
                                                          @PathVariable String type,
                                                          @RequestParam String name){
        return inspectionFieldService.getInspectionFields(id, place, type, name);
    }

    @DeleteMapping("/fields/{id}")
    public void deleteInspectionField(@PathVariable UUID id){
        inspectionFieldService.deleteInspectionField(id);
    }

    //Images

    @PutMapping("/fields/{fieldId}/{imageId}")
    public ResponseEntity<?> addImageToField(@PathVariable UUID fieldId, @PathVariable UUID imageId){
        return inspectionFieldService.addImageToField(fieldId, imageId);
    }

    @DeleteMapping("/fields/{fieldId}/{imageId}")
    public ResponseEntity<?> deleteImageFromField(@PathVariable UUID fieldId, @PathVariable UUID imageId){
        return inspectionFieldService.deleteImageFromField(fieldId, imageId);
    }

    //Notes

    @PostMapping("/fields/{fieldId}/note")
    public ResponseEntity<?> addNoteToField(@PathVariable UUID fieldId, @RequestBody String note){
        return inspectionFieldService.addNoteToField(fieldId, note);
    }

    @GetMapping("/fields/{fieldId}/note")
    public String getNoteFromField(@PathVariable UUID fieldId){
        return inspectionFieldService.getNoteFromField(fieldId);
    }

    @PutMapping("/fields/{fieldId}/note")
    public ResponseEntity<?> updateNoteToField(@PathVariable UUID fieldId, @RequestBody String note){
        return  inspectionFieldService.addNoteToField(fieldId, note);
    }

    // Annotations

    @PostMapping("/fields/{fieldId}/annotations/save")
    public ResponseEntity<?> addAnnotation(@PathVariable UUID fieldId, @RequestBody ImageAnnotation annotation){
        return inspectionFieldService.addAnnotation(fieldId, annotation);
    }

    @GetMapping("/fields/{fieldId}/annotations")
    public List<ImageAnnotation> getAnnotations(@PathVariable UUID fieldId){
        return inspectionFieldService.getAnnotations(fieldId);
    }

    @DeleteMapping("/annotations/{annotationId}/delete")
    public ResponseEntity<?> deleteAnnotation(@PathVariable UUID annotationId){
        return inspectionFieldService.deleteAnnotation(annotationId);
    }

    // include in summary

    @PutMapping("/fields/{fieldId}/summary")
    public ResponseEntity<?> checkPutInSummary(@PathVariable UUID fieldId, @RequestBody Boolean checked){
        return inspectionFieldService.updateFieldInSummary(fieldId, checked);
    }

    @GetMapping("/fields/{fieldId}/summary")
    public Boolean checkInSummary(@PathVariable UUID fieldId){
        return inspectionFieldService.checkInSummary(fieldId);
    }

    // Recommendations

    @GetMapping("/fields/{fieldId}/recommendations")
    public InspectionRecommendationField getRecommendationField(@PathVariable UUID fieldId){
        return inspectionFieldService.getRecommendationField(fieldId);
    }

    @PostMapping("/fields/{fieldId}/recommendations")
    public ResponseEntity<?> addRecommendationField(@PathVariable UUID fieldId, @RequestBody InspectionRecommendationField recommendationField){
        return inspectionFieldService.addRecommendationField(fieldId, recommendationField);
    }
}
