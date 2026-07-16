package ca.inspection.home.inspection.entity;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.*;

@Entity
@Table(name = "inspection_report")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@ToString(exclude = {"inspectionBooking", "images", "fields"})
@EqualsAndHashCode(exclude = {"inspectionBooking", "images", "fields"})
public class InspectionReport {
    @Id
    @GeneratedValue
    @Column(name = "id", columnDefinition = "TEXT")
    private UUID id;

    @OneToOne
    @JsonBackReference("reportBooking")
    @JoinColumn(name = "inspection_booking_id", nullable = false, unique = true)
    private InspectionBookings inspectionBooking;

    @Column(columnDefinition = "TEXT")
    private String summary;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cover_page_image_id")
    private InspectionImage coverPageImage;

    private String appendixPdf;

    @OneToMany(mappedBy = "inspectionReport", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonManagedReference
    private Set<InspectionImage> images = new HashSet<>();

    @OneToMany(mappedBy = "inspectionReport", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonManagedReference("inspectionReport-fields")
    private Set<InspectionField> fields = new HashSet<>();
}
