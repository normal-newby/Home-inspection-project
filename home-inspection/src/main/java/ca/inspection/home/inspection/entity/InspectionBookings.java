package ca.inspection.home.inspection.entity;

import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.Type;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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
