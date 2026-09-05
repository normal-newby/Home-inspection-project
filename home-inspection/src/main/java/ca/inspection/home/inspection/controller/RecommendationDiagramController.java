package ca.inspection.home.inspection.controller;

import ca.inspection.home.inspection.DTO.AttachedDiagrams;
import ca.inspection.home.inspection.entity.RecommendationDiagram;
import ca.inspection.home.inspection.service.RecommendationDiagramService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/recommendation-diagrams")
@CrossOrigin(origins = "*")
public class RecommendationDiagramController {

    @Autowired
    private RecommendationDiagramService recommendationDiagramService;

    @GetMapping
    public List<RecommendationDiagram> getAllDiagrams() {
        return recommendationDiagramService.getAllDiagrams();
    }

    @PostMapping
    public ResponseEntity<?> uploadDiagram(@RequestParam String title,
                                           @RequestParam MultipartFile file) {
        return recommendationDiagramService.uploadDiagram(title, file);
    }

    @GetMapping("/{id}/file")
    public ResponseEntity<Resource> getDiagramFile(@PathVariable UUID id) {
        return recommendationDiagramService.getDiagramFile(id, false);
    }

    @GetMapping("/{id}/thumb")
    public ResponseEntity<Resource> getDiagramThumbnail(@PathVariable UUID id) {
        return recommendationDiagramService.getDiagramFile(id, true);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteDiagram(@PathVariable UUID id) {
        return recommendationDiagramService.deleteDiagram(id);
    }

    @GetMapping("/field/{fieldId}")
    public AttachedDiagrams getAttachedDiagrams(@PathVariable UUID fieldId) {
        return recommendationDiagramService.getAttachedDiagrams(fieldId);
    }

    @PutMapping("/field/{fieldId}")
    public ResponseEntity<?> setAttachedDiagrams(@PathVariable UUID fieldId,
                                                 @RequestBody List<UUID> diagramIds) {
        return recommendationDiagramService.setAttachedDiagrams(fieldId, diagramIds);
    }
}
