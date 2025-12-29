package ca.inspection.home.inspection.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Data
@AllArgsConstructor
@NoArgsConstructor
public class InspectionField {
    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "inspection_report_id", nullable = false)
    private InspectionReport inspectionReport;

    private String fieldName; //e.g. Sloped roofing material
    private String fieldValue; //e.g. Asphalt shingles, shingles, even more shingles
    private String fieldPlace; //e.g. roofing, exterior
    private String fieldType; //e.g. description, limitations, recommendations

    @ManyToOne
    @JoinColumn(name = "inspection_image_id")
    private InspectionImage inspectionImage; //only used if fieldType is description, limitations

    @OneToOne(mappedBy = "inspectionField")
    private InspectionRecommendationField inspectionRecommendationField; //only used if fieldType is recommendations
}
