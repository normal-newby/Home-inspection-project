package ca.inspection.home.inspection.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

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
}
