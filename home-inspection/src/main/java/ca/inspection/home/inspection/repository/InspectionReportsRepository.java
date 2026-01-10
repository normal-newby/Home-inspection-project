package ca.inspection.home.inspection.repository;

import ca.inspection.home.inspection.entity.InspectionReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface InspectionReportsRepository extends JpaRepository<InspectionReport, UUID> {
    InspectionReport findByInspectionBooking_Id(UUID bookingId);
}
