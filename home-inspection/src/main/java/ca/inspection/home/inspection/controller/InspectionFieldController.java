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
@RequestMapping("/api/fields")
@CrossOrigin(origins = "*")
public class InspectionFieldController {
    @Autowired
    private InspectionFieldService inspectionFieldService;

    @PostMapping("{id}/{fieldDefinitionId}/{value}")
    public ResponseEntity<?> createNewInspectionField(@PathVariable UUID id,
                                                   @PathVariable UUID fieldDefinitionId,
                                                   @PathVariable String value){
        return inspectionFieldService.createNewInspectionField(id, fieldDefinitionId, value);
    }

    @GetMapping("/{id}/{fieldId}")
    public List<InspectionField> getInspectionFields(@PathVariable UUID id,
                                                      @PathVariable UUID fieldId){
        return inspectionFieldService.getInspectionFields(id, fieldId);
    }

    @DeleteMapping("/{id}")
    public void deleteInspectionField(@PathVariable UUID id){
        inspectionFieldService.deleteInspectionField(id);
    }

    //Images

    @PutMapping("/{fieldId}/{imageId}")
    public ResponseEntity<?> addImageToField(@PathVariable UUID fieldId, @PathVariable UUID imageId){
        return inspectionFieldService.addImageToField(fieldId, imageId);
    }

    @DeleteMapping("/{fieldId}/{imageId}")
    public ResponseEntity<?> deleteImageFromField(@PathVariable UUID fieldId, @PathVariable UUID imageId){
        return inspectionFieldService.deleteImageFromField(fieldId, imageId);
    }

    //Notes

    @PostMapping("/{fieldId}/note")
    public ResponseEntity<?> addNoteToField(@PathVariable UUID fieldId, @RequestBody String note){
        return inspectionFieldService.addNoteToField(fieldId, note);
    }

    @GetMapping("/{fieldId}/note")
    public String getNoteFromField(@PathVariable UUID fieldId){
        return inspectionFieldService.getNoteFromField(fieldId);
    }

    @PutMapping("/{fieldId}/note")
    public ResponseEntity<?> updateNoteToField(@PathVariable UUID fieldId, @RequestBody String note){
        return  inspectionFieldService.addNoteToField(fieldId, note);
    }

    // Annotations

    @PostMapping("/images/{imageId}/annotations")
    public ResponseEntity<?> addAnnotation(@PathVariable UUID imageId, @RequestBody ImageAnnotation annotation){
        return inspectionFieldService.addAnnotation(imageId, annotation);
    }

    @GetMapping("/images/{imageId}/annotations")
    public List<ImageAnnotation> getAnnotations(@PathVariable UUID imageId){
        return inspectionFieldService.getAnnotations(imageId);
    }

    @DeleteMapping("/annotations/{annotationId}")
    public ResponseEntity<?> deleteAnnotation(@PathVariable UUID annotationId){
        return inspectionFieldService.deleteAnnotation(annotationId);
    }

    // include in summary

    @PutMapping("/{fieldId}/summary")
    public ResponseEntity<?> checkPutInSummary(@PathVariable UUID fieldId, @RequestBody Boolean checked){
        return inspectionFieldService.updateFieldInSummary(fieldId, checked);
    }

    @GetMapping("/{fieldId}/summary")
    public Boolean checkInSummary(@PathVariable UUID fieldId){
        return inspectionFieldService.checkInSummary(fieldId);
    }

    // Recommendations

    @GetMapping("/{fieldId}/recommendations")
    public InspectionRecommendationField getRecommendationField(@PathVariable UUID fieldId){
        return inspectionFieldService.getRecommendationField(fieldId);
    }

    @PostMapping("/{fieldId}/recommendations")
    public ResponseEntity<?> addRecommendationField(@PathVariable UUID fieldId, @RequestBody InspectionRecommendationField recommendationField){
        return inspectionFieldService.addRecommendationField(fieldId, recommendationField);
    }
}
