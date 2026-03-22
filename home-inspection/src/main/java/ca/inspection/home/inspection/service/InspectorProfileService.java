package ca.inspection.home.inspection.service;

import ca.inspection.home.inspection.entity.InspectorProfile;
import ca.inspection.home.inspection.repository.InspectorProfileRepository;
import org.apache.coyote.Response;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

@Service
public class InspectorProfileService {

    @Autowired
    private InspectorProfileRepository inspectorProfileRepository;

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
            e.printStackTrace();
            return ResponseEntity.badRequest().build();
        }
    }
}
