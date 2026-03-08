package ca.inspection.home.inspection.repository;

import ca.inspection.home.inspection.entity.ImageAnnotation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ImageAnnotationRepository extends JpaRepository<ImageAnnotation, UUID> {
    List<ImageAnnotation> findByInspectionFieldId(UUID fieldId);
}