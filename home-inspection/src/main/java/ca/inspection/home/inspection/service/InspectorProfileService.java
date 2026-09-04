package ca.inspection.home.inspection.service;

import ca.inspection.home.inspection.entity.InspectionReport;
import ca.inspection.home.inspection.entity.InspectorProfile;
import ca.inspection.home.inspection.repository.InspectionBookingsRepository;
import ca.inspection.home.inspection.repository.InspectorProfileRepository;
import jakarta.transaction.Transactional;
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

    @Autowired
    private InspectionBookingsRepository inspectionBookingsRepository;

    public InspectorProfile getProfile() {
        return inspectorProfileRepository.findById(1L)
                .orElseGet(() -> inspectorProfileRepository.save(new InspectorProfile()));
    }

    public ResponseEntity<?> saveProfile(InspectorProfile profile) {
        try {
            profile.setId(1L);
            carryOverMissingFields(profile);
            inspectorProfileRepository.save(profile);
            return ResponseEntity.ok().build();
        } catch (Exception e){
            log.error("Failed to save inspector profile", e);
            return ResponseEntity.badRequest().build();
        }
    }

    private void carryOverMissingFields(InspectorProfile incoming) {
        InspectorProfile existing = inspectorProfileRepository.findById(1L).orElse(null);
        if (existing == null) return;

        if (incoming.getName() == null) incoming.setName(existing.getName());
        if (incoming.getCompany() == null) incoming.setCompany(existing.getCompany());
        if (incoming.getPhone() == null) incoming.setPhone(existing.getPhone());
        if (incoming.getEmail() == null) incoming.setEmail(existing.getEmail());
        if (incoming.getWebsite() == null) incoming.setWebsite(existing.getWebsite());
        if (incoming.getLogoPath() == null) incoming.setLogoPath(existing.getLogoPath());
        if (incoming.getInspectionNumber() == null) incoming.setInspectionNumber(existing.getInspectionNumber());

        if (incoming.getAddress() == null) incoming.setAddress(existing.getAddress());
        if (incoming.getCity() == null) incoming.setCity(existing.getCity());
        if (incoming.getProvince() == null) incoming.setProvince(existing.getProvince());
        if (incoming.getPostalCode() == null) incoming.setPostalCode(existing.getPostalCode());

        if (incoming.getCoverLetterBody() == null) incoming.setCoverLetterBody(existing.getCoverLetterBody());
        if (incoming.getSummaryLetterBody() == null) incoming.setSummaryLetterBody(existing.getSummaryLetterBody());
        if (incoming.getAgreementBody() == null) incoming.setAgreementBody(existing.getAgreementBody());
        if (incoming.getEmailTemplate() == null) incoming.setEmailTemplate(existing.getEmailTemplate());
        if (incoming.getAppendixPdf() == null) incoming.setAppendixPdf(existing.getAppendixPdf());

        if (incoming.getGoogleRefreshToken() == null) incoming.setGoogleRefreshToken(existing.getGoogleRefreshToken());
        if (incoming.getGoogleAccessToken() == null) incoming.setGoogleAccessToken(existing.getGoogleAccessToken());
        if (incoming.getGoogleTokenExpiry() == null) incoming.setGoogleTokenExpiry(existing.getGoogleTokenExpiry());
        if (incoming.getGoogleAccountEmail() == null) incoming.setGoogleAccountEmail(existing.getGoogleAccountEmail());
        if (incoming.getGoogleCalendarId() == null) incoming.setGoogleCalendarId(existing.getGoogleCalendarId());
        if (incoming.getGoogleCalendarEnabled() == null) {
            incoming.setGoogleCalendarEnabled(existing.getGoogleCalendarEnabled());
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

    @Transactional
    public Integer getAndUpdateNumber() {
        InspectorProfile profile = inspectorProfileRepository.findById(1L)
                .orElseThrow(() -> new RuntimeException("Inspector profile not found"));

        Integer current = profile.getInspectionNumber();
        int updated = (current == null ? 0 : current) + 1;
        while (inspectionBookingsRepository.existsByInspectionNumber(updated)) {
            updated++;
        }

        profile.setInspectionNumber(updated);
        inspectorProfileRepository.save(profile);
        return updated;
    }
}
