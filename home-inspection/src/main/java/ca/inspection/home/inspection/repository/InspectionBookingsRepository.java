package ca.inspection.home.inspection.repository;

import ca.inspection.home.inspection.entity.BookingSummary;
import ca.inspection.home.inspection.entity.InspectionBookings;
import ca.inspection.home.inspection.entity.InspectionReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface InspectionBookingsRepository extends JpaRepository<InspectionBookings, UUID> {
    @Query("SELECT b FROM InspectionBookings b ORDER BY b.createdAt DESC")
    List<BookingSummary> findBookingSummariesByOrderByCreatedAtDesc();
}
