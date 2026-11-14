package ca.inspection.home.inspection.entity;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.*;

@Entity
@Table(name = "inspection_image", indexes = {
        @Index(name = "idx_image_report", columnList = "inspection_report_id"),
        @Index(name = "idx_image_field",  columnList = "inspection_field_id")
})
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@ToString(exclude = {"inspectionReport", "fields"})
@EqualsAndHashCode(exclude = {"inspectionReport", "inspectionField", "annotations", "reportAnnotations"})
public class InspectionImage {
    @Id
    @GeneratedValue
    @Column(name = "id", columnDefinition = "TEXT")
    private UUID id;

    @Transient
    private String base64;

    @Transient
    private Integer figureNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JsonBackReference
    @JoinColumn(name = "inspection_report_id", nullable = false)
    private InspectionReport inspectionReport;

    @Column(name = "image_url")
    private String imageUrl;

    @ManyToOne(fetch = FetchType.LAZY)
    @JsonBackReference("inspectionField-images")
    @JoinColumn(name = "inspection_field_id")
    private InspectionField inspectionField;

    // Never sent to the page: the editor asks for one photo's annotations when it opens.
    @OneToMany(mappedBy = "inspectionImage", cascade = CascadeType.ALL, orphanRemoval = true)
    @BatchSize(size = 100)
    @JsonIgnore
    private Set<ImageAnnotation> annotations = new HashSet<>();

    private Boolean used = false;

    // Filled in only while a report is rendered, from one query for the whole report.
    @Transient
    @JsonIgnore
    private Set<ImageAnnotation> reportAnnotations = Set.of();
}
