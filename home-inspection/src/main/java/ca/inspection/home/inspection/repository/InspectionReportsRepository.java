package ca.inspection.home.inspection.repository;

import ca.inspection.home.inspection.entity.InspectionReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface InspectionReportsRepository extends JpaRepository<InspectionReport, UUID> {
    @Query("""
        SELECT r FROM InspectionReport r
        LEFT JOIN FETCH r.fields f
        LEFT JOIN FETCH f.inspectionFieldDefinition
        LEFT JOIN FETCH f.selectedValue
        LEFT JOIN FETCH r.images
        LEFT JOIN FETCH r.inspectionBooking
        WHERE r.inspectionBooking.id = :bookingId
    """)
    InspectionReport findByInspectionBooking_Id(@Param("bookingId")  UUID bookingId);
}
