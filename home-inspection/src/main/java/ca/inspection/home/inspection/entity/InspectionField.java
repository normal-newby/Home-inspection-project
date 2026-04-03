package ca.inspection.home.inspection.entity;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.List;
import java.util.ArrayList;
import java.util.UUID;

@Entity
@Table(name = "inspection_field")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@ToString(exclude = {"inspectionReport", "inspectionFieldDefinition", "selectedValue", "inspectionImages", "inspectionRecommendationField", "annotations"})
@EqualsAndHashCode(exclude = {"inspectionReport", "inspectionFieldDefinition", "selectedValue", "inspectionImages", "inspectionRecommendationField", "annotations"})
public class InspectionField {
    @Id
    @GeneratedValue
    @Column(name = "id", columnDefinition = "TEXT")
    @JdbcTypeCode(SqlTypes.CHAR)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JsonBackReference("inspectionReport-fields")
    @JoinColumn(name = "inspection_report_id", nullable = false)
    private InspectionReport inspectionReport;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JsonBackReference
    @JoinColumn(name = "inspection_field_definition_id", nullable = false)
    private InspectionFieldDefinition inspectionFieldDefinition;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "inspection_field_definition_value_id")
    private InspectionFieldDefinitionValue selectedValue;

    @OneToMany(mappedBy = "inspectionField", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonManagedReference("inspectionField-images")
    private List<InspectionImage> inspectionImages = new ArrayList<>(); //multiple images per field

    @OneToOne(mappedBy = "inspectionField")
    @JsonManagedReference("inspectionField-recommendation")
    private InspectionRecommendationField inspectionRecommendationField; //only used if fieldType is recommendations

    @OneToMany(mappedBy = "inspectionField", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonManagedReference("inspectionField-annotations")
    private List<ImageAnnotation> annotations = new ArrayList<>();


    @Column(name = "note")
    private String note;

    @Column(name = "include_in_summary")
    private Boolean includeInSummary;
}
