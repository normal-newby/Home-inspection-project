package ca.inspection.home.inspection.entity;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;

import com.fasterxml.jackson.annotation.JsonManagedReference;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "inspection_bookings")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class InspectionBookings {
    @Id
    @GeneratedValue
    @Column(name = "id", columnDefinition = "TEXT")
    private UUID id; // UUID

    private Integer inspectionNumber;

    // Property info
    private String inspectionAddress;
    private String suite;
    private String city;
    private String postalCode;
    private String province;

    // Client info
    private String clientFirstName;
    private String clientLastName;
    private String email;
    private String phone;

    // Time
    private String month;
    private Integer day;
    private Integer year;

    // Start of the inspection as "HH:mm" (24h)
    private String startTime;

    // How long the inspection is expected to run, in minutes.
    private Integer durationMinutes;

    // Where this booking is in the inspector's workflow
    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private BookingStatus status = BookingStatus.SCHEDULED;

    // Defaults to scheduled
    public BookingStatus getStatus() {
        return BookingStatus.orDefault(status);
    }

    // Metadata
    private String referredBy;
    private String bookedBy;

    // Invoice
    @OneToMany(mappedBy = "bookings", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonManagedReference("invoices")
    private List<Invoice> invoices;

    private Boolean paidInFull;

    private Boolean removeTax;

    @OneToOne(mappedBy = "inspectionBooking", cascade = CascadeType.ALL, optional = true, fetch = FetchType.LAZY)
    @JsonManagedReference("reportBooking")
    private InspectionReport inspectionReport;

    // Id of the event this booking owns in the linked Google Calendar
    private String googleEventId;

    @Column(updatable = false)
    @CreationTimestamp
    private LocalDateTime createdAt;
}
