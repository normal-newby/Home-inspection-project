package ca.inspection.home.inspection.service;

import ca.inspection.home.inspection.entity.*;
import ca.inspection.home.inspection.repository.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
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

    @Autowired
    private InspectionFieldDefinitionValueRepository inspectionFieldDefinitionValueRepository;

    public ResponseEntity<?> createNewInspectionField(UUID id,
                                                   UUID fieldDefinitionId,
                                                   String value){
        try {
            //report
            InspectionReport report = inspectionBookingsService.getReportFromBooking(id);

            //definition
            InspectionFieldDefinition definition = inspectionFieldDefinitionRepository
                    .findWithValues(fieldDefinitionId);

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

            InspectionField saved = inspectionFieldRepository.save(inspectionField);
            log.debug("Created inspection field {} for definition {}", saved.getId(), definition.getId());

            return ResponseEntity.ok(saved);
        } catch (Exception e) {
            log.error("Failed to create field for booking={} definition={} value={}",
                    id, fieldDefinitionId, value, e);
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    public Map<UUID, List<InspectionField>> getAlreadyExistingFieldsCombined(
            UUID bookingId, String place, String type
    ){
        try {
            InspectionReport report = inspectionBookingsService.getReportFromBooking(bookingId);
            List<InspectionField> fields = inspectionFieldRepository
                    .getExistingFieldsForPlaceAndType(report.getId(), place, type);
            attachAnnotations(fields);
            return fields.stream()
                    .collect(Collectors.groupingBy(field -> field.getInspectionFieldDefinition().getId()));
        } catch (Exception e){
            log.warn("Failed to fetch existing fields for booking={} place={} type={}",
                    bookingId, place, type, e);
            return Map.of();
        }
    }

    // Loads the annotations for every image on these fields in one query and wires them onto the images
    private void attachAnnotations(List<InspectionField> fields){
        List<InspectionImage> images = fields.stream()
                .map(InspectionField::getInspectionImages)
                .filter(Objects::nonNull)
                .flatMap(List::stream)
                .toList();
        if (images.isEmpty()) return;

        List<UUID> imageIds = images.stream().map(InspectionImage::getId).toList();
        Map<UUID, Set<ImageAnnotation>> annotationMap = imageAnnotationRepository
                .findByInspectionImageIdIn(imageIds).stream()
                .collect(Collectors.groupingBy(ann -> ann.getInspectionImage().getId(),
                        Collectors.toSet()));

        images.forEach(img -> img.setAnnotations(
                annotationMap.getOrDefault(img.getId(), new HashSet<>())));
    }

    @Transactional
    public void deleteInspectionField(UUID id){
        if (!inspectionFieldRepository.existsById(id)){
            throw new RuntimeException("InspectionField not found");
        }

        // Does not delete images
        List<InspectionImage> images = inspectionImagesRepository.findByInspectionField_IdIn(List.of(id));
        if (!images.isEmpty()){
            images.forEach(image -> {
                image.setInspectionField(null);
                image.setUsed(false);
            });
            inspectionImagesRepository.saveAllAndFlush(images);
            log.debug("Released {} image(s) from deleted field {}", images.size(), id);
        }

        inspectionFieldRepository.deleteById(id);
    }

    //Images

    public ResponseEntity<?> addImageToField(UUID fieldId, UUID imageId){
        try {
            if (!inspectionFieldRepository.existsById(fieldId)){
                throw new RuntimeException("InspectionField not found");
            }
            InspectionImage image = inspectionImagesRepository.findById(imageId)
                    .orElseThrow(() -> new RuntimeException("InspectionImage not found"));

            // Image owns the FK, so writing on the image side is enough — no need to
            // fetch the field entity or force-init the field's lazy image collection.
            InspectionField fieldRef = inspectionFieldRepository.getReferenceById(fieldId);
            image.setInspectionField(fieldRef);
            image.setUsed(true);
            inspectionImagesRepository.save(image);

            return ResponseEntity.ok().body(Map.of("Image added", true));
        } catch (Exception e){
            log.error("Failed to add image {} to field {}", imageId, fieldId, e);
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    public ResponseEntity<?> deleteImageFromField(UUID fieldId, UUID imageId){
        try {
            InspectionImage image = inspectionImagesRepository.findById(imageId)
                    .orElseThrow(() -> new RuntimeException("InspectionImage not found"));

            // Same as add: image side owns the FK — no lazy collection init needed.
            image.setUsed(false);
            image.setInspectionField(null);
            inspectionImagesRepository.save(image);

            return ResponseEntity.ok().body(Map.of("Image deleted", true));
        } catch (Exception e){
            log.error("Failed to remove image {} from field {}", imageId, fieldId, e);
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // Condition name (blank items)

    public ResponseEntity<?> saveConditionName(UUID fieldId, String conditionName, boolean saveAsPermanentValue){
        try {
            InspectionField field = inspectionFieldRepository.findById(fieldId)
                    .orElseThrow(() -> new RuntimeException("InspectionField not found"));

            String name = conditionName == null ? "" : conditionName.trim();
            field.setConditionName(name.isEmpty() ? null : name);
            inspectionFieldRepository.save(field);

            boolean savedPermanently = false;
            boolean alreadyExisted = false;
            if (saveAsPermanentValue && !name.isEmpty()){
                PermanentValueResult result = savePermanentValue(field, name);
                savedPermanently = result.saved();
                alreadyExisted = result.alreadyExisted();
            }

            return ResponseEntity.ok(Map.of(
                    "conditionName", name,
                    "savedPermanently", savedPermanently,
                    "alreadyExisted", alreadyExisted
            ));
        } catch (Exception e){
            log.error("Failed to save condition name on field {}", fieldId, e);
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    private record PermanentValueResult(boolean saved, boolean alreadyExisted) {}

    /** Adds the name to the definition's option list, unless it already offers it. */
    private PermanentValueResult savePermanentValue(InspectionField field, String name){
        InspectionFieldDefinition definition = inspectionFieldDefinitionRepository
                .findWithValues(field.getInspectionFieldDefinition().getId());

        boolean exists = definition.getPossibleValues().stream()
                .map(InspectionFieldDefinitionValue::getValue)
                .filter(Objects::nonNull)
                .anyMatch(existing -> existing.trim().equalsIgnoreCase(name));
        if (exists){
            log.debug("Definition {} already offers \"{}\"", definition.getId(), name);
            return new PermanentValueResult(false, true);
        }

        InspectionFieldDefinitionValue value = new InspectionFieldDefinitionValue();
        value.setInspectionFieldDefinition(definition);
        value.setValue(name);
        inspectionFieldDefinitionValueRepository.save(value);

        log.info("Saved \"{}\" as a permanent value on definition {}", name, definition.getId());
        return new PermanentValueResult(true, false);
    }

    // Notes

    public ResponseEntity<?> addNoteToField(UUID fieldId, String note){
        try {
            InspectionField field = inspectionFieldRepository.findById(fieldId)
                    .orElseThrow(() -> new RuntimeException("InspectionField not found"));
            field.setNote(note);
            inspectionFieldRepository.save(field);
            return ResponseEntity.ok().build();
        } catch (Exception e){
            log.error("Failed to save note on field {}", fieldId, e);
            return ResponseEntity.badRequest().build();
        }
    }

    public String getNoteFromField(UUID fieldId){
        try {
            InspectionField field = inspectionFieldRepository.findById(fieldId)
                    .orElseThrow(() -> new RuntimeException("InspectionField not found"));
            return field.getNote();
        } catch (Exception e){
            log.warn("Failed to read note from field {}", fieldId, e);
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
            log.error("Failed to update summary flag on field {}", fieldId, e);
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
            log.warn("Failed to read summary flag on field {}", fieldId, e);
            return false;
        }
    }

    // Annotations

    public ResponseEntity<?> addAnnotation(UUID imageId, ImageAnnotation annotation){
        try {
            InspectionImage image = inspectionImagesRepository.findById(imageId)
                    .orElseThrow(() -> new RuntimeException("InspectionImage not found"));
            annotation.setInspectionImage(image);
            ImageAnnotation saved = imageAnnotationRepository.save(annotation);
            // The editor needs the generated id back, so saving again edits instead of duplicating.
            return ResponseEntity.ok(saved);
        } catch (Exception e){
            log.error("Failed to add annotation to image {}", imageId, e);
            return ResponseEntity.badRequest().build();
        }
    }

    public List<ImageAnnotation> getAnnotations(UUID imageId){
        return imageAnnotationRepository.findByInspectionImageId(imageId);
    }

    // The editor sends the whole annotation back, so the server-owned fields are kept from the stored row.
    public ResponseEntity<?> updateAnnotation(UUID annotationId, ImageAnnotation updated){
        try {
            ImageAnnotation annotation = imageAnnotationRepository.findById(annotationId)
                    .orElseThrow(() -> new RuntimeException("Annotation not found"));

            if (updated.getType() != null) annotation.setType(updated.getType());
            annotation.setX(updated.getX());
            annotation.setY(updated.getY());
            annotation.setWidth(updated.getWidth());
            annotation.setHeight(updated.getHeight());
            annotation.setContent(updated.getContent());
            annotation.setColor(updated.getColor());
            annotation.setStrokeWidth(updated.getStrokeWidth());
            annotation.setFixedLength(updated.getFixedLength());
            annotation.setImageDisplayWidth(updated.getImageDisplayWidth());
            annotation.setImageDisplayHeight(updated.getImageDisplayHeight());

            imageAnnotationRepository.save(annotation);
            return ResponseEntity.ok().build();
        } catch (Exception e){
            log.error("Failed to update annotation {}", annotationId, e);
            return ResponseEntity.badRequest().build();
        }
    }

    public ResponseEntity<?> deleteAnnotation(UUID annotationId){
        try {
            ImageAnnotation annotation = imageAnnotationRepository.findById(annotationId)
                    .orElseThrow(() -> new RuntimeException("Annotation not found"));
            imageAnnotationRepository.delete(annotation);
            return ResponseEntity.ok().build();
        } catch (Exception e){
            log.error("Failed to delete annotation {}", annotationId, e);
            return ResponseEntity.badRequest().build();
        }
    }

    // Recommendation Fields

    public InspectionRecommendationField getRecommendationField(UUID fieldId){
        try {
            // JOIN FETCHes selectedValue + recommendation in the same query
            InspectionField field = inspectionFieldRepository.findWithRecommendationAndValue(fieldId)
                    .orElseThrow(() -> new RuntimeException("Field not found"));
            InspectionRecommendationField recommendationField = field.getInspectionRecommendationField();
            InspectionFieldDefinitionValue definitionValue = field.getSelectedValue();

            if (recommendationField == null && definitionValue.getDefaultImplication() != null){
                InspectionRecommendationField newField = new InspectionRecommendationField();
                newField.setImplication(definitionValue.getDefaultImplication());
                return newField;
            }

            return recommendationField;
        } catch (Exception e){
            log.warn("Failed to load recommendation for field {}", fieldId, e);
            return null;
        }
    }

    public ResponseEntity<?> addRecommendationField(UUID fieldId, InspectionRecommendationField recommendationField,
                                                    Boolean saveAsDefaultImplication){
        try {
            InspectionField field = inspectionFieldRepository.findById(fieldId)
                    .orElseThrow(() -> new RuntimeException("Field not found"));
            InspectionRecommendationField existing = field.getInspectionRecommendationField();
            InspectionFieldDefinitionValue definitionValue = field.getSelectedValue();

            // Default Implication
            if (saveAsDefaultImplication && recommendationField.getImplication() != null){
                if (definitionValue != null){
                    definitionValue.setDefaultImplication(recommendationField.getImplication());
                    inspectionFieldDefinitionValueRepository.save(definitionValue);
                }
            }

            InspectionRecommendationField saved;
            if (existing == null) {
                recommendationField.setInspectionField(field);
                saved = inspectionRecommendationFieldRepository.save(recommendationField);
            } else {
                existing.setDirection(recommendationField.getDirection());
                existing.setFloorLevel(recommendationField.getFloorLevel());
                existing.setRoom(recommendationField.getRoom());
                existing.setTask(recommendationField.getTask());
                existing.setTime(recommendationField.getTime());
                existing.setLower_cost(recommendationField.getLower_cost());
                existing.setUpper_cost(recommendationField.getUpper_cost());
                existing.setImplication(recommendationField.getImplication());
                saved = inspectionRecommendationFieldRepository.save(existing);
            }

            return ResponseEntity.ok(saved);
        } catch (Exception e){
            log.error("Failed to save recommendation for field {}", fieldId, e);
            return ResponseEntity.badRequest().build();
        }
    }
}
