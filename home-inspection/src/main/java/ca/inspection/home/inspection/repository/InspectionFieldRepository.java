package ca.inspection.home.inspection.repository;

import ca.inspection.home.inspection.entity.InspectionField;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface InspectionFieldRepository extends JpaRepository<InspectionField, UUID> {
    @Query("""
            SELECT f
            FROM InspectionField f
            JOIN FETCH f.inspectionFieldDefinition d
            LEFT JOIN FETCH f.selectedValue
            LEFT JOIN FETCH f.inspectionImages
            WHERE f.inspectionReport.id = :report
            AND d.fieldPlace = :place
            AND d.fieldType = :type
            AND d.fieldName = :name
            """)
    List<InspectionField> getInspectionFields(UUID report, String place, String type, String name);
}
