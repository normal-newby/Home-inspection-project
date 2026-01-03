package ca.inspection.home.inspection.repository;

import ca.inspection.home.inspection.entity.InspectionImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface InspectionImagesRepository extends JpaRepository<InspectionImage, UUID> {
}
