package ca.inspection.home.inspection.repository;

import ca.inspection.home.inspection.entity.InspectionField;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface InspectionFieldRepository extends JpaRepository<InspectionField, UUID> {
    @Query("""
            SELECT DISTINCT f
            FROM InspectionField f
            JOIN FETCH f.inspectionFieldDefinition d
            LEFT JOIN FETCH f.selectedValue
            LEFT JOIN FETCH f.inspectionImages img
            LEFT JOIN FETCH img.annotations
            LEFT JOIN FETCH f.inspectionRecommendationField
            WHERE f.inspectionReport.id = :reportId
            AND d.fieldPlace = :place
            AND d.fieldType = :type
            """)
    List<InspectionField> getExistingFieldsForPlaceAndType(
            @Param("reportId") UUID reportId,
            @Param("place") String place,
            @Param("type") String type
    );
}
