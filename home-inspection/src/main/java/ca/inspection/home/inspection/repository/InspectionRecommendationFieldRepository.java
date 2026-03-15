package ca.inspection.home.inspection.repository;

import ca.inspection.home.inspection.entity.InspectionRecommendationField;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface InspectionRecommendationFieldRepository extends JpaRepository<InspectionRecommendationField, UUID> {
}
