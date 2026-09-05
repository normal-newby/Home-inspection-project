package ca.inspection.home.inspection.service;

import ca.inspection.home.inspection.DTO.AttachedDiagrams;
import ca.inspection.home.inspection.entity.InspectionField;
import ca.inspection.home.inspection.entity.InspectionFieldDefinitionValue;
import ca.inspection.home.inspection.entity.RecommendationDiagram;
import ca.inspection.home.inspection.repository.InspectionFieldDefinitionValueRepository;
import ca.inspection.home.inspection.repository.InspectionFieldRepository;
import ca.inspection.home.inspection.repository.RecommendationDiagramRepository;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Slf4j
public class RecommendationDiagramService {

    private static final int THUMB_MAX_WIDTH = 400;

    @Autowired
    private RecommendationDiagramRepository recommendationDiagramRepository;

    @Autowired
    private InspectionFieldRepository inspectionFieldRepository;

    @Autowired
    private InspectionFieldDefinitionValueRepository inspectionFieldDefinitionValueRepository;

    @Autowired
    private HelperFunctions helperFunctions;

    // --- The library ---

    public List<RecommendationDiagram> getAllDiagrams() {
        return recommendationDiagramRepository.findAllByOrderByTitleAsc();
    }

    public ResponseEntity<?> uploadDiagram(String title, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body("No file to upload");
        }
        if (!HelperFunctions.notBlank(title)) {
            return ResponseEntity.badRequest().body("A diagram needs a title");
        }

        Path path = null;
        try {
            Path directory = helperFunctions.getRecommendationDiagramDirectory();
            Files.createDirectories(directory);

            String extension = HelperFunctions.getFileExtension(file.getOriginalFilename());
            String fileName = "diagram_" + UUID.randomUUID() + extension;
            path = directory.resolve(fileName);
            file.transferTo(path.toFile());

            RecommendationDiagram diagram = new RecommendationDiagram();
            diagram.setTitle(title.trim());
            diagram.setFileName(fileName);

            RecommendationDiagram saved = recommendationDiagramRepository.save(diagram);
            log.info("Uploaded recommendation diagram {} ({})", saved.getId(), saved.getTitle());
            return ResponseEntity.ok(saved);

        } catch (Exception e) {
            log.error("Failed to upload recommendation diagram '{}'", title, e);
            deleteQuietly(path);
            return ResponseEntity.badRequest().body("Could not upload that diagram");
        }
    }

    public ResponseEntity<Resource> getDiagramFile(UUID id, boolean thumbnail) {
        try {
            RecommendationDiagram diagram = recommendationDiagramRepository.findById(id).orElseThrow();
            Path directory = helperFunctions.getRecommendationDiagramDirectory();
            Path path = directory.resolve(diagram.getFileName());

            if (thumbnail) {
                path = Thumbnails.getOrCreate(
                        path, directory.resolve("thumbs").resolve(diagram.getFileName() + ".jpg"),
                        THUMB_MAX_WIDTH);
            }

            String contentType = thumbnail ? MediaType.IMAGE_JPEG_VALUE : Files.probeContentType(path);
            if (contentType == null) contentType = MediaType.APPLICATION_OCTET_STREAM_VALUE;

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    // The filename carries a UUID, so an id's bytes never change.
                    .cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable())
                    .body(new FileSystemResource(path.toFile()));

        } catch (Exception e) {
            log.warn("Failed to read recommendation diagram {}", id, e);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    @Transactional
    public ResponseEntity<?> deleteDiagram(UUID id) {
        try {
            RecommendationDiagram diagram = recommendationDiagramRepository.findById(id).orElse(null);
            if (diagram == null) return ResponseEntity.noContent().build();

            // Join rows outliving the diagram would break the next report render.
            List<InspectionFieldDefinitionValue> holders =
                    inspectionFieldDefinitionValueRepository.findAllHolding(id);
            holders.forEach(value -> value.getDiagrams().removeIf(d -> d.getId().equals(id)));
            inspectionFieldDefinitionValueRepository.saveAll(holders);

            recommendationDiagramRepository.delete(diagram);

            Path directory = helperFunctions.getRecommendationDiagramDirectory();
            deleteQuietly(directory.resolve(diagram.getFileName()));
            deleteQuietly(directory.resolve("thumbs").resolve(diagram.getFileName() + ".jpg"));

            log.info("Deleted recommendation diagram {}, detached from {} recommendation(s)",
                    id, holders.size());
            return ResponseEntity.ok().build();

        } catch (Exception e) {
            log.error("Failed to delete recommendation diagram {}", id, e);
            return ResponseEntity.badRequest().body("Could not delete that diagram");
        }
    }

    // --- Attaching to a recommendation ---
    // Addressed by field, but the attachment lands on the definition value behind it.

    @Transactional
    public AttachedDiagrams getAttachedDiagrams(UUID fieldId) {
        try {
            InspectionField field = inspectionFieldRepository.findWithRecommendationAndValue(fieldId)
                    .orElseThrow(() -> new RuntimeException("Field not found"));

            InspectionFieldDefinitionValue value = field.getSelectedValue();
            if (value == null) return null;

            List<UUID> ids = value.getDiagrams().stream()
                    .map(RecommendationDiagram::getId)
                    .collect(Collectors.toList());

            String fieldName = field.getInspectionFieldDefinition() == null
                    ? null : field.getInspectionFieldDefinition().getFieldName();

            return new AttachedDiagrams(value.getId(), fieldName, value.getValue(), ids);

        } catch (Exception e) {
            log.warn("Failed to load diagrams attached to field {}", fieldId, e);
            return null;
        }
    }

    @Transactional
    public ResponseEntity<?> setAttachedDiagrams(UUID fieldId, List<UUID> diagramIds) {
        try {
            if (diagramIds == null) return ResponseEntity.badRequest().build();

            InspectionField field = inspectionFieldRepository.findWithRecommendationAndValue(fieldId)
                    .orElseThrow(() -> new RuntimeException("Field not found"));

            InspectionFieldDefinitionValue value = field.getSelectedValue();
            if (value == null) {
                return ResponseEntity.badRequest().body("That item has no value to attach diagrams to");
            }

            // Re-read in the order the picker sent, which is the order the report prints.
            Map<UUID, RecommendationDiagram> byId = recommendationDiagramRepository
                    .findAllById(new HashSet<>(diagramIds)).stream()
                    .collect(Collectors.toMap(RecommendationDiagram::getId, d -> d,
                            (a, b) -> a, LinkedHashMap::new));

            List<RecommendationDiagram> attached = new ArrayList<>();
            Set<UUID> seen = new HashSet<>();
            for (UUID id : diagramIds) {
                RecommendationDiagram diagram = byId.get(id);
                // One deleted while the picker was open is dropped rather than failing the save.
                if (diagram != null && seen.add(id)) attached.add(diagram);
            }

            value.getDiagrams().clear();
            value.getDiagrams().addAll(attached);
            inspectionFieldDefinitionValueRepository.save(value);

            log.info("Attached {} diagram(s) to definition value {}", attached.size(), value.getId());
            return ResponseEntity.ok(attached.stream().map(RecommendationDiagram::getId).toList());

        } catch (Exception e) {
            log.error("Failed to attach diagrams to field {}", fieldId, e);
            return ResponseEntity.badRequest().body("Could not save those diagrams");
        }
    }

    // --- Report rendering ---

    public String toBase64(RecommendationDiagram diagram) {
        try {
            if (diagram == null || diagram.getFileName() == null) return null;

            Path path = helperFunctions.getRecommendationDiagramDirectory().resolve(diagram.getFileName());
            if (!Files.exists(path)) return null;

            String contentType = Files.probeContentType(path);
            if (contentType == null) contentType = MediaType.IMAGE_JPEG_VALUE;

            return "data:" + contentType + ";base64,"
                    + Base64.getEncoder().encodeToString(Files.readAllBytes(path));

        } catch (Exception e) {
            log.warn("Failed to encode recommendation diagram {}", diagram.getId(), e);
            return null;
        }
    }

    private void deleteQuietly(Path path) {
        if (path == null) return;
        try {
            Files.deleteIfExists(path);
        } catch (Exception e) {
            log.warn("Left a recommendation diagram file behind at {}", path, e);
        }
    }
}
