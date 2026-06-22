package ca.inspection.home.inspection.entity;

import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "inspection_field_definition")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@ToString(exclude = {"possibleValues"})
@EqualsAndHashCode(exclude = {"possibleValues"})
public class InspectionFieldDefinition {
    @GeneratedValue
    @Id
    @Column(name = "id")
    @JdbcTypeCode(SqlTypes.CHAR)
    private UUID id;

    private String fieldName; //shingles
    private String fieldPlace; //heating
    private String fieldType; //limitations and stuff

    private Boolean expandedByDefault = false; //setting to show in fields

    @JsonManagedReference
    @OneToMany(mappedBy = "inspectionFieldDefinition", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("value ASC")
    private List<InspectionFieldDefinitionValue> possibleValues = new ArrayList<>();
}
