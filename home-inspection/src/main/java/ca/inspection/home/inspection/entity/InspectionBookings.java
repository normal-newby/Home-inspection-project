package ca.inspection.home.inspection.entity;

import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.Type;
import org.hibernate.type.SqlTypes;

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

    // Metadata
    private String referredBy;
    private String bookedBy;

    @OneToOne(mappedBy = "inspectionBooking")
    @JsonManagedReference
    private InspectionReport inspectionReport;
}
