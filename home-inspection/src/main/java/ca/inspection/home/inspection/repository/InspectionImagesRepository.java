package ca.inspection.home.inspection.repository;

import ca.inspection.home.inspection.entity.InspectionImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface InspectionImagesRepository extends JpaRepository<InspectionImage, UUID> {
    List<InspectionImage> findByInspectionField_IdIn(List<UUID> fieldId);

    // Lean fetch of all images
    @Query("""
        SELECT i FROM InspectionImage i
        WHERE i.inspectionReport.inspectionBooking.id = :bookingId
        ORDER BY i.imageUrl ASC
    """)
    List<InspectionImage> findByBookingIdOrdered(@Param("bookingId") UUID bookingId);

    // Lean fetch of image
    @Query("SELECT i.imageUrl FROM InspectionImage i WHERE i.id = :id")
    Optional<String> findImageUrlById(@Param("id") UUID id);
}
