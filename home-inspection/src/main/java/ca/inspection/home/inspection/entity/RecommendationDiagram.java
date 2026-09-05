package ca.inspection.home.inspection.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.UUID;

/**
 * A reference drawing attached to whichever recommendations it explains. Not a photo of the
 * house, so it lives in its own library rather than under a booking.
 */
@Entity
@Table(name = "recommendation_diagram")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class RecommendationDiagram {

    @Id
    @GeneratedValue
    @Column(name = "id", columnDefinition = "TEXT")
    @JdbcTypeCode(SqlTypes.CHAR)
    private UUID id;

    // What the report prints under it.
    private String title;

    // Name on disk
    private String fileName;
}
