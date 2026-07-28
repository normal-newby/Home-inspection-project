package ca.inspection.home.inspection.repository;

import ca.inspection.home.inspection.entity.InspectionRecommendationFieldDefinition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface InspectionRecommendationFieldValueRepository extends JpaRepository<InspectionRecommendationFieldDefinition, UUID> {
}
