package ca.inspection.home.inspection.repository;

import ca.inspection.home.inspection.entity.InspectionFieldDefinition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface InspectionFieldDefinitionRepository extends JpaRepository<InspectionFieldDefinition, UUID> {
    List<InspectionFieldDefinition> findAllByFieldPlaceAndFieldType(String fieldPlace, String fieldType);
}
