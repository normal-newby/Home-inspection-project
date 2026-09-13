package ca.inspection.home.inspection.entity;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonManagedReference;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

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

    @Column(columnDefinition = "TEXT")
    private String emailBody;

    @OneToOne
    @JoinColumn(name = "cover_page_image_id")
    private InspectionImage coverPageImage;

    private String appendixPdf;

    @Column(name = "show_invoice", columnDefinition = "BOOLEAN DEFAULT TRUE")
    private Boolean showInvoice = true;

    @OneToMany(mappedBy = "inspectionReport", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonManagedReference
    private Set<InspectionImage> images = new HashSet<>();

    @OneToMany(mappedBy = "inspectionReport", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonManagedReference("inspectionReport-fields")
    private Set<InspectionField> fields = new HashSet<>();
}
