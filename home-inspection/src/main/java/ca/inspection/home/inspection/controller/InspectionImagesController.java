package ca.inspection.home.inspection.controller;

import ca.inspection.home.inspection.repository.InspectionImagesRepository;
import ca.inspection.home.inspection.service.InspectionImagesService;
import org.springframework.beans.factory.annotation.Autowired;
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
    public void uploadImages(@PathVariable UUID id,
                             @RequestParam("files") List<MultipartFile> files,
                             @RequestParam("descriptions") List<String> descriptions
    ) throws IOException {
        inspectionImagesService.saveImages(files, id, descriptions);
    }
}
