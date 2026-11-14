package ca.inspection.home.inspection.repository;

import ca.inspection.home.inspection.DTO.AnalyticsRow;
import ca.inspection.home.inspection.DTO.BookingDetails;
import ca.inspection.home.inspection.DTO.BookingRow;
import ca.inspection.home.inspection.DTO.ClientRow;
import ca.inspection.home.inspection.DTO.InvoiceRow;
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
    // Shared by the three list queries below, so they always cover the same bookings.
    String IN_RANGE = " (b.inspectionDate BETWEEN :from AND :to OR (:undated = true AND b.inspectionDate IS NULL)) ";

    @Query("""
        SELECT new ca.inspection.home.inspection.DTO.BookingRow(
            b.id, b.inspectionNumber, b.inspectionAddress, b.suite, b.city, b.province, b.postalCode,
            b.agentName, b.agentPhone, b.agentEmail, b.month, b.day, b.year, b.inspectionDate, b.startTime,
            b.status, b.paidInFull, b.removeTax)
        FROM InspectionBookings b
        WHERE""" + IN_RANGE + "ORDER BY b.inspectionDate, b.inspectionNumber")
    List<BookingRow> findRows(@Param("from") LocalDate from, @Param("to") LocalDate to, @Param("undated") boolean undated);

    @Query("""
        SELECT new ca.inspection.home.inspection.DTO.ClientRow(b.id, c.firstName, c.lastName, c.email, c.phone)
        FROM Client c JOIN c.booking b
        WHERE""" + IN_RANGE + "ORDER BY c.position")
    List<ClientRow> findClientRows(@Param("from") LocalDate from, @Param("to") LocalDate to, @Param("undated") boolean undated);

    @Query("""
        SELECT new ca.inspection.home.inspection.DTO.InvoiceRow(b.id, i.type, i.fee)
        FROM Invoice i JOIN i.bookings b
        WHERE""" + IN_RANGE)
    List<InvoiceRow> findInvoiceRows(@Param("from") LocalDate from, @Param("to") LocalDate to, @Param("undated") boolean undated);

    long countByInspectionDateBefore(LocalDate date);

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
}
