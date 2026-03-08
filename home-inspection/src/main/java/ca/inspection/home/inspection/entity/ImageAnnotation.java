package ca.inspection.home.inspection.entity;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Table(name = "image_annotation")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ImageAnnotation {
    @Id
    @GeneratedValue
    @Column(name = "id", columnDefinition = "TEXT")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "inspection_field_id", nullable = false)
    @JsonBackReference("inspectionField-annotations")
    private InspectionField inspectionField;

    @Column(name = "annotation_type")
    private String type; // e.g., "rectangle", "circle", "text", "freehand"

    @Column(name = "x")
    private Double x;

    @Column(name = "y")
    private Double y;

    @Column(name = "width")
    private Double width;

    @Column(name = "height")
    private Double height;

    @Column(name = "content")
    private String content; // for text annotations

    @Column(name = "color")
    private String color;
}