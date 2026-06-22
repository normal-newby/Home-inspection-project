package ca.inspection.home.inspection.repository;

import ca.inspection.home.inspection.entity.InspectionFieldDefinition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface InspectionFieldDefinitionRepository extends JpaRepository<InspectionFieldDefinition, UUID> {

    @Query("""
        SELECT d 
        FROM InspectionFieldDefinition d
        WHERE d.fieldPlace = :place
        AND d.fieldType = :type
        ORDER BY d.fieldName
            """)
    List<InspectionFieldDefinition> findAllWithoutValues(
            @Param("place") String place,
            @Param("type") String type
    );

    @Query("""
            SELECT d FROM InspectionFieldDefinition d
            LEFT JOIN FETCH d.possibleValues v
            WHERE d.id = :id
            """)
    InspectionFieldDefinition findWithValues(
            @Param("id") UUID id
    );
}
