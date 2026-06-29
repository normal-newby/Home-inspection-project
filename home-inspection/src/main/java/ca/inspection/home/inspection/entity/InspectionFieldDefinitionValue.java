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
@Table(name = "inspection_field_definition_value")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class InspectionFieldDefinitionValue {
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
}
