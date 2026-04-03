package ca.inspection.home.inspection.entity;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "image_annotation")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@ToString(exclude = {"inspectionField"})
@EqualsAndHashCode(exclude = {"inspectionField"})
public class ImageAnnotation {
    @Id
    @GeneratedValue
    @Column(name = "id", columnDefinition = "TEXT")
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "inspection_image_id")
    @JsonBackReference("inspectionImage-annotations")
    private InspectionImage inspectionImage;

    @Column(name = "image_display_width")
    private Double imageDisplayWidth;

    @Column(name = "image_display_height")
    private Double imageDisplayHeight;

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

    @Column(name = "stroke_width")
    private String strokeWidth;
}