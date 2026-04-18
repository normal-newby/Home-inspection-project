package ca.inspection.home.inspection.service;

import ca.inspection.home.inspection.entity.*;
import ca.inspection.home.inspection.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class InspectionFieldService {
    @Autowired
    private InspectionFieldRepository inspectionFieldRepository;

    @Autowired
    private InspectionFieldDefinitionRepository inspectionFieldDefinitionRepository;

    @Autowired
    private InspectionBookingsService inspectionBookingsService;

    @Autowired
    private InspectionImagesRepository inspectionImagesRepository;

    @Autowired
    private ImageAnnotationRepository imageAnnotationRepository;

    @Autowired
    private InspectionRecommendationFieldRepository inspectionRecommendationFieldRepository;

    public ResponseEntity<?> createNewInspectionField(UUID id,
                                                   String place,
                                                   String type,
                                                   String name,
                                                   String value){
        try {
            //report
            InspectionReport report = inspectionBookingsService.getReportFromBooking(id);

            //definition
            InspectionFieldDefinition definition = inspectionFieldDefinitionRepository
                    .findWithValues(place, type, name);

            //create value
            InspectionFieldDefinitionValue definitionValue = definition.getPossibleValues().stream()
                    .filter(v -> v.getValue().equals(value))
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("value not found"));

            //create field
            InspectionField inspectionField = new InspectionField();
            inspectionField.setInspectionReport(report);
            inspectionField.setInspectionFieldDefinition(definition);
            inspectionField.setSelectedValue(definitionValue);

            System.out.println(definition.getId());

            InspectionField saved = inspectionFieldRepository.save(inspectionField);

            return ResponseEntity.ok(saved);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    public List<InspectionField> getInspectionFields(UUID id,
                                                                  String place,
                                                                  String type,
                                                                  String name){
        try {
            //report
            UUID reportId = inspectionBookingsService.getReportFromBooking(id).getId();

            //Get
           return inspectionFieldRepository.getInspectionFields(reportId, place, type, name);

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public void deleteInspectionField(UUID id){
        InspectionField field = inspectionFieldRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("InspectionField not found"));

        inspectionFieldRepository.delete(field);
    }

    //Images

    public ResponseEntity<?> addImageToField(UUID fieldId, UUID imageId){
        try {
            InspectionField field = inspectionFieldRepository.findById(fieldId)
                    .orElseThrow(() -> new RuntimeException("InspectionField not found"));
            InspectionImage image = inspectionImagesRepository.findById(imageId)
                    .orElseThrow(() -> new RuntimeException("InspectionImage not found"));
            field.getInspectionImages().add(image);
            image.setInspectionField(field);
            image.setUsed(true);
            inspectionFieldRepository.save(field);

            return ResponseEntity.ok().body(Map.of("Image added", true));
        } catch (Exception e){
            e.printStackTrace();
            return ResponseEntity.badRequest().build();
        }
    }

    public ResponseEntity<?> deleteImageFromField(UUID fieldId, UUID imageId){
        try {
            InspectionField field = inspectionFieldRepository.findById(fieldId)
                    .orElseThrow(() -> new RuntimeException("InspectionField not found"));
            InspectionImage image = inspectionImagesRepository.findById(imageId)
                    .orElseThrow(() -> new RuntimeException("InspectionImage not found"));
            image.setUsed(false);
            image.setInspectionField(null);
            inspectionImagesRepository.save(image);
            field.getInspectionImages().remove(image);
            inspectionFieldRepository.save(field);

            return ResponseEntity.ok().body(Map.of("Image deleted", true));
        } catch (Exception e){
            e.printStackTrace();
            return ResponseEntity.badRequest().build();
        }
    }

    // Notes

    public ResponseEntity<?> addNoteToField(UUID fieldId, String note){
        try {
            InspectionField field = inspectionFieldRepository.findById(fieldId)
                    .orElseThrow(() -> new RuntimeException("InspectionField not found"));
            System.out.println(note);
            field.setNote(note);
            inspectionFieldRepository.save(field);
            return ResponseEntity.ok().build();
        } catch (Exception e){
            e.printStackTrace();
            return ResponseEntity.badRequest().build();
        }
    }

    public String getNoteFromField(UUID fieldId){
        try {
            InspectionField field = inspectionFieldRepository.findById(fieldId)
                    .orElseThrow(() -> new RuntimeException("InspectionField not found"));
            return field.getNote();
        } catch (Exception e){
            e.printStackTrace();
            return null;
        }
    }

    // Check In summary

    public ResponseEntity<?> updateFieldInSummary(UUID fieldId, Boolean checked){
        try {
            InspectionField field = inspectionFieldRepository.findById(fieldId)
                    .orElseThrow(() -> new RuntimeException("InspectionField not found"));
            field.setIncludeInSummary(checked);
            inspectionFieldRepository.save(field);
            return ResponseEntity.ok().build();
        } catch (Exception e){
            e.printStackTrace();
            return ResponseEntity.badRequest().build();
        }
    }

    public Boolean checkInSummary(UUID fieldId){
        try {
            InspectionField field = inspectionFieldRepository.findById(fieldId)
                    .orElseThrow(() -> new RuntimeException("InspectionField not found"));
            Boolean value = field.getIncludeInSummary();
            return value != null ? value : false;
        } catch (Exception e){
            e.printStackTrace();
            return false;
        }
    }

    // Annotations

    public ResponseEntity<?> addAnnotation(UUID imageId, ImageAnnotation annotation){
        try {
            InspectionImage image = inspectionImagesRepository.findById(imageId)
                    .orElseThrow(() -> new RuntimeException("InspectionImage not found"));
            annotation.setInspectionImage(image);
            imageAnnotationRepository.save(annotation);
            return ResponseEntity.ok().build();
        } catch (Exception e){
            e.printStackTrace();
            return ResponseEntity.badRequest().build();
        }
    }

    public List<ImageAnnotation> getAnnotations(UUID imageId){
        return imageAnnotationRepository.findByInspectionImageId(imageId);
    }

    public ResponseEntity<?> deleteAnnotation(UUID annotationId){
        try {
            ImageAnnotation annotation = imageAnnotationRepository.findById(annotationId)
                    .orElseThrow(() -> new RuntimeException("Annotation not found"));
            imageAnnotationRepository.delete(annotation);
            return ResponseEntity.ok().build();
        } catch (Exception e){
            e.printStackTrace();
            return ResponseEntity.badRequest().build();
        }
    }

    // Recommendation Fields

    public InspectionRecommendationField getRecommendationField(UUID fieldId){
        try {
            InspectionField field = inspectionFieldRepository.findById(fieldId)
                    .orElseThrow(() -> new RuntimeException("Field not found"));
            return field.getInspectionRecommendationField();
        } catch (Exception e){
            e.printStackTrace();
            return null;
        }
    }

    public ResponseEntity<?> addRecommendationField(UUID fieldId, InspectionRecommendationField recommendationField){
        try {
            InspectionField field = inspectionFieldRepository.findById(fieldId)
                    .orElseThrow(() -> new RuntimeException("Field not found"));

            InspectionRecommendationField existing = field.getInspectionRecommendationField();

            InspectionRecommendationField saved;
            if (existing == null) {
                recommendationField.setInspectionField(field);
                saved = inspectionRecommendationFieldRepository.save(recommendationField);
            } else {
                // Update only the fields that are provided by the client.
                if (recommendationField.getDirection() != null) {
                    existing.setDirection(recommendationField.getDirection());
                }
                if (recommendationField.getFloorLevel() != null) {
                    existing.setFloorLevel(recommendationField.getFloorLevel());
                }
                if (recommendationField.getRoom() != null) {
                    existing.setRoom(recommendationField.getRoom());
                }
                if (recommendationField.getTask() != null) {
                    existing.setTask(recommendationField.getTask());
                }
                if (recommendationField.getTime() != null) {
                    existing.setTime(recommendationField.getTime());
                }
                if (recommendationField.getLower_cost() != null) {
                    existing.setLower_cost(recommendationField.getLower_cost());
                }
                if (recommendationField.getUpper_cost() != null) {
                    existing.setUpper_cost(recommendationField.getUpper_cost());
                }
                if (recommendationField.getImplication() != null) {
                    existing.setImplication(recommendationField.getImplication());
                }
                existing.setInspectionField(field);
                saved = inspectionRecommendationFieldRepository.save(existing);
            }

            return ResponseEntity.ok(saved);
        } catch (Exception e){
            e.printStackTrace();
            return ResponseEntity.badRequest().build();
        }
    }
}
