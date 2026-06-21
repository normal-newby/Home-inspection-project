package ca.inspection.home.inspection.repository;

import ca.inspection.home.inspection.entity.InspectionFieldDefinitionValue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface InspectionFieldDefinitionValueRepository extends JpaRepository<InspectionFieldDefinitionValue, UUID> {
    InspectionFieldDefinitionValue findByValue(String value);
}
