package ca.inspection.home.inspection.repository;

import ca.inspection.home.inspection.entity.InspectionFieldDefinitionValue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface InspectionFieldDefinitionValueRepository extends JpaRepository<InspectionFieldDefinitionValue, UUID> {
    InspectionFieldDefinitionValue findByValue(String value);

    // Every recommendation a diagram is attached to
    @Query("""
            SELECT DISTINCT v FROM InspectionFieldDefinitionValue v
            LEFT JOIN FETCH v.diagrams
            WHERE EXISTS (SELECT 1 FROM InspectionFieldDefinitionValue h
                          JOIN h.diagrams x
                          WHERE h.id = v.id AND x.id = :diagramId)
            """)
    List<InspectionFieldDefinitionValue> findAllHolding(@Param("diagramId") UUID diagramId);

    @Query("""
            SELECT DISTINCT v FROM InspectionFieldDefinitionValue v
            LEFT JOIN FETCH v.diagrams
            WHERE v.id IN :ids
            """)
    List<InspectionFieldDefinitionValue> findAllWithDiagrams(@Param("ids") Collection<UUID> ids);
}
