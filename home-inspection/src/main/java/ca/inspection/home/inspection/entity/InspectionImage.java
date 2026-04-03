package ca.inspection.home.inspection.entity;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "inspection_image")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@ToString(exclude = {"inspectionReport", "fields"})
@EqualsAndHashCode(exclude = {"inspectionReport", "fields"})
public class InspectionImage {
    @Id
    @GeneratedValue
    @Column(name = "id", columnDefinition = "TEXT")
    private UUID id;

    @Transient
    private String base64;

    @ManyToOne
    @JsonBackReference
    @JoinColumn(name = "inspection_report_id", nullable = false)
    private InspectionReport inspectionReport;

    @Column(name = "image_url")
    private String ImageUrl;
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JsonBackReference("inspectionField-images")
    @JoinColumn(name = "inspection_field_id")
    private InspectionField inspectionField;

    @OneToMany(mappedBy = "inspectionImage", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonManagedReference("inspectionImage-annotations")
    private List<ImageAnnotation> annotations = new ArrayList<>();

    private Boolean used = false;
}
