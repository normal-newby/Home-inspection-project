package ca.inspection.home.inspection.entity;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "inspection_field_definition_value")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class InspectionFieldDefinitionValue {

    public static final String BLANK_ITEM = "blank item";

    @GeneratedValue
    @Id
    @Column(name = "id", columnDefinition = "TEXT")
    @JdbcTypeCode(SqlTypes.CHAR)
    private UUID id;

    @JsonBackReference
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "inspection_field_definition_id", nullable = false)
    private InspectionFieldDefinition inspectionFieldDefinition;

    private String value;

    private String defaultImplication; // For all fields which are recommendations, if there is default
    // implication, use it.

    // Supporting drawings
    @JsonIgnore
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "definition_value_diagram",
            joinColumns = @JoinColumn(name = "definition_value_id"),
            inverseJoinColumns = @JoinColumn(name = "diagram_id")
    )
    @OrderColumn(name = "position")
    private List<RecommendationDiagram> diagrams = new ArrayList<>();
}
