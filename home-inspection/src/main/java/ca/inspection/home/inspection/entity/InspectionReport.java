package ca.inspection.home.inspection.entity;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "inspection_report")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class InspectionReport {
    @Id
    @GeneratedValue
    private UUID id;

    @OneToOne
    @JsonBackReference
    @JoinColumn(name = "inspection_booking_id", nullable = false, unique = true)
    private InspectionBookings inspectionBooking;

    @OneToMany(mappedBy = "inspectionReport", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonManagedReference
    private List<InspectionImage> images = new ArrayList<>();

    @OneToMany(mappedBy = "inspectionReport", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonManagedReference
    private List<InspectionField> fields = new ArrayList<>();
}
