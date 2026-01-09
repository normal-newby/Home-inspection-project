package ca.inspection.home.inspection.entity;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Table(name = "inspection_field_value")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class InspectionField {
    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne
    @JsonBackReference
    @JoinColumn(name = "inspection_report_id", nullable = false)
    private InspectionReport inspectionReport;

    private InspectionFieldDefinition inspectionFieldDefinition;

    private String fieldValue; //e.g. Asphalt shingles, shingles, even more shingles

    @ManyToOne
    @JoinColumn(name = "inspection_image_id")
    @JsonBackReference
    private InspectionImage inspectionImage; //only used if fieldType is description, limitations

    @OneToOne(mappedBy = "inspectionField")
    @JsonManagedReference
    private InspectionRecommendationField inspectionRecommendationField; //only used if fieldType is recommendations
}
