package ca.inspection.home.inspection.entity;

import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "inspection_field_definition")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class InspectionFieldDefinition {
    @GeneratedValue
    @Id
    private UUID id;

    private String fieldName; //shingles
    private String fieldPlace; //heating
    private String fieldType; //limitations and stuff

    @JsonManagedReference
    @OneToMany(mappedBy = "inspectionFieldDefinition", cascade = CascadeType.ALL)
    private List<InspectionFieldDefinitionValue> possibleValues;
}
