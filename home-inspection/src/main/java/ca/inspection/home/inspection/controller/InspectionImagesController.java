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
import java.util.UUID;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class InspectionImagesController {
    @Autowired
    private InspectionImagesService inspectionImagesService;

    @PostMapping("/images/{id}/upload")
    public ResponseEntity<String> uploadImages(@PathVariable UUID id,
                             @RequestParam("files") List<MultipartFile> files,
                             @RequestParam("descriptions") List<String> descriptions
    ) throws IOException {
        try {
            inspectionImagesService.saveImages(files, id, descriptions);
            return ResponseEntity.ok("Saved");
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Error: " + e.getMessage());
        }
    }

    @GetMapping("/images/{id}/get")
    public List<InspectionImage> getImages(@PathVariable UUID id) {
        return inspectionImagesService.getImages(id);
    }

    @GetMapping("/images/file/{filename}")
    public ResponseEntity<Resource> getImageFile(@PathVariable UUID filename){
        return inspectionImagesService.getImageFile(filename);
    }

    @DeleteMapping("/images/{id}/delete")
    public ResponseEntity<Void> deleteImage(@PathVariable UUID id) {
        return inspectionImagesService.deleteImage(id);
    }
}
