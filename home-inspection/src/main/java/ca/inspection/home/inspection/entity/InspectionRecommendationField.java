package ca.inspection.home.inspection.entity;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.UUID;

@Entity
@Data
@AllArgsConstructor
@NoArgsConstructor
public class InspectionRecommendationField {
    @Id
    @GeneratedValue
    @Column(name = "id", columnDefinition = "TEXT")
    private UUID id;

    @OneToOne
    @JsonBackReference("inspectionField-recommendation")
    @JoinColumn(name = "inspection_field_id")
    private InspectionField inspectionField;

    private String direction; //direction in the house
    private String floorLevel; //which floor is it on?
    private String room; //which room is it in?
    private String task; //what recommendation are we making?
    private String time; //when do we have to fix it by?
    private String lower_cost; //whats the lower bound
    private String upper_cost; //whats the upper bound

    @Column(columnDefinition = "text")
    private String implication;
}
