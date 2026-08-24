package ca.inspection.home.inspection.service;

import ca.inspection.home.inspection.entity.InspectionReport;
import ca.inspection.home.inspection.entity.InspectorProfile;
import ca.inspection.home.inspection.repository.InspectorProfileRepository;
import lombok.extern.slf4j.Slf4j;
import org.apache.coyote.Response;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
public class InspectorProfileService {

    @Autowired
    private InspectorProfileRepository inspectorProfileRepository;

    @Autowired
    private HelperFunctions helperFunctions;

    public InspectorProfile getProfile() {
        return inspectorProfileRepository.findById(1L)
                .orElseGet(() -> inspectorProfileRepository.save(new InspectorProfile()));
    }

    public ResponseEntity<?> saveProfile(InspectorProfile profile) {
        try {
            profile.setId(1L);
            inspectorProfileRepository.save(profile);
            return ResponseEntity.ok().build();
        } catch (Exception e){
            log.error("Failed to save inspector profile", e);
            return ResponseEntity.badRequest().build();
        }
    }

    public ResponseEntity<?> uploadAppendixPdf(MultipartFile appendixPdf){
        try {
            InspectorProfile profile = getProfile();

            String filename = "appendix.pdf";
            Path path = helperFunctions.getDirectory().resolve(filename);
            appendixPdf.transferTo(path.toFile());

            profile.setAppendixPdf(filename);
            inspectorProfileRepository.save(profile);

            log.info("Uploaded inspector appendix pdf");
            return ResponseEntity.ok(Map.of("Saved", true));
        } catch (Exception e){
            log.error("Failed to upload inspector appendix pdf", e);
            return ResponseEntity.badRequest().build();
        }
    }

    public Path getAppendixPdfPath(){
        try {
            InspectorProfile profile = getProfile();

            return helperFunctions.getDirectory().resolve(profile.getAppendixPdf());
        } catch (Exception e){
            log.warn("Failed to resolve inspector appendix pdf path", e);
            return null;
        }
    }

    public Integer getAndUpdateNumber() {
        InspectorProfile profile = inspectorProfileRepository.findById(1L)
                .orElseThrow(() -> new RuntimeException("Inspector profile not found"));
        Integer updated = profile.getInspectionNumber() + 1;
        profile.setInspectionNumber(updated);
        inspectorProfileRepository.save(profile);
        return updated;
    }
}
