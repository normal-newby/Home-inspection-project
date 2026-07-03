package ca.inspection.home.inspection.entity;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.List;
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

    private List<String> direction; //direction in the house
    private List<String> floorLevel; //which floor is it on?
    private List<String> room; //which room is it in?
    private List<String> task; //what recommendation are we making?
    private List<String> time; //when do we have to fix it by?
    private String lower_cost; //whats the lower bound
    private String upper_cost; //whats the upper bound

    @Column(columnDefinition = "text")
    private String implication;
}
