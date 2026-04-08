package ca.inspection.home.inspection.controller;

import ca.inspection.home.inspection.entity.InspectorProfile;
import ca.inspection.home.inspection.service.InspectorProfileService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/profile")
@CrossOrigin(origins = "*")
public class InspectorProfileController {

    @Autowired
    private InspectorProfileService inspectorProfileService;


    @GetMapping
    public InspectorProfile getProfile() {
        return inspectorProfileService.getProfile();
    }

    @PostMapping
    public ResponseEntity<?> saveProfile(@RequestBody InspectorProfile profile) {
        return inspectorProfileService.saveProfile(profile);
    }

    @PostMapping("/appendix-pdf")
    public ResponseEntity<?> uploadAppendixPdf(@RequestParam MultipartFile appendixPdf){
        return inspectorProfileService.uploadAppendixPdf(appendixPdf);
    }
}
