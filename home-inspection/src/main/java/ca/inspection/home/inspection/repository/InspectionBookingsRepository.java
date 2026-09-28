package ca.inspection.home.inspection.repository;

import ca.inspection.home.inspection.DTO.AnalyticsRow;
import ca.inspection.home.inspection.DTO.BookingDetails;
import ca.inspection.home.inspection.entity.InspectionBookings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface InspectionBookingsRepository extends JpaRepository<InspectionBookings, UUID> {
    // JOIN FETCH invoices in the same query so BookingDetails.getInvoices() doesn't
    // trigger a per-row lazy load when the list is serialized.
    @Query("""
        SELECT DISTINCT b FROM InspectionBookings b
        LEFT JOIN FETCH b.invoices
        ORDER BY b.createdAt DESC
    """)
    List<BookingDetails> findBookingDetailsByOrderByCreatedAtDesc();

    @Query("""
        SELECT b FROM InspectionBookings b
        LEFT JOIN FETCH b.invoices
        WHERE b.id = :id
    """)
    BookingDetails getBookingDetails(@Param("id")UUID id);

    boolean existsByInspectionNumber(Integer inspectionNumber);

    List<InspectionBookings> findByInspectionDateIsNullAndMonthIsNotNull();

    long countByInspectionDateIsNull();

    @Query("""
        SELECT new ca.inspection.home.inspection.DTO.AnalyticsRow(
            b.id, b.inspectionDate, b.status, b.paidInFull, b.removeTax, i.type, i.fee)
        FROM InspectionBookings b
        LEFT JOIN b.invoices i
        WHERE b.inspectionDate BETWEEN :from AND :to
    """)
    List<AnalyticsRow> findAnalyticsRows(@Param("from") LocalDate from, @Param("to") LocalDate to);

    // Clients load lazily in batches (@BatchSize), so call this inside a transaction.
    @Query("""
        SELECT DISTINCT b FROM InspectionBookings b
        LEFT JOIN FETCH b.invoices
        WHERE b.inspectionDate BETWEEN :from AND :to
        ORDER BY b.inspectionDate, b.inspectionNumber
    """)
    List<InspectionBookings> findForExport(@Param("from") LocalDate from, @Param("to") LocalDate to);
}
