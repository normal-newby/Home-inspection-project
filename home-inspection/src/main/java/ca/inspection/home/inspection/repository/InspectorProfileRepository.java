package ca.inspection.home.inspection.repository;

import ca.inspection.home.inspection.entity.InspectorProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface InspectorProfileRepository extends JpaRepository<InspectorProfile, Long> {
}
