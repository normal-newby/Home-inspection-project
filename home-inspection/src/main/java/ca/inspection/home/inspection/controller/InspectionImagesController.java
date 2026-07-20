package ca.inspection.home.inspection.controller;

import ca.inspection.home.inspection.entity.InspectionImage;
import ca.inspection.home.inspection.repository.InspectionImagesRepository;
import ca.inspection.home.inspection.service.InspectionImagesService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/images")
@CrossOrigin(origins = "*")
public class InspectionImagesController {
    @Autowired
    private InspectionImagesService inspectionImagesService;

    @PostMapping("/{id}/upload")
    public ResponseEntity<String> uploadImage(@PathVariable UUID id,
                             @RequestParam("file") MultipartFile file
    ) throws IOException {
        try {
            inspectionImagesService.saveImages(file, id);
            return ResponseEntity.ok("Saved");
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Error: " + e.getMessage());
        }
    }

    @GetMapping("/{id}")
    public Set<InspectionImage> getImages(@PathVariable UUID id) {
        return inspectionImagesService.getImages(id);
    }

    @GetMapping("/file/{filename}")
    public ResponseEntity<Resource> getImageFile(@PathVariable UUID filename){
        return inspectionImagesService.getImageFile(filename);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteImage(@PathVariable UUID id) {
        return inspectionImagesService.deleteImage(id);
    }

    @PostMapping("/{bookingId}/cover-page-image")
    public ResponseEntity<?> updateCoverPageImage(@PathVariable UUID bookingId, @RequestParam MultipartFile file){
        return inspectionImagesService.updateCoverPageImage(bookingId, file);
    }
}
