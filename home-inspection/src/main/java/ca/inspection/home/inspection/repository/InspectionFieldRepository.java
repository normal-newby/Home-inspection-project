package ca.inspection.home.inspection.repository;

import ca.inspection.home.inspection.entity.InspectionField;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface InspectionFieldRepository extends JpaRepository<InspectionField, UUID> {
}
