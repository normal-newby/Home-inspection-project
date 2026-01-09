package ca.inspection.home.inspection.entity;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Table(name = "inspection_field_definition_value")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class InspectionFieldDefinitionValue {
    @GeneratedValue
    @Id
    private UUID id;

    @JsonBackReference
    @ManyToOne
    @JoinColumn(name = "inspection_field_definition_id", nullable = false)
    private InspectionFieldDefinition inspectionFieldDefinition;

    private String value;
}
