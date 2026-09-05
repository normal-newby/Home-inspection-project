package ca.inspection.home.inspection.repository;

import ca.inspection.home.inspection.entity.RecommendationDiagram;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface RecommendationDiagramRepository extends JpaRepository<RecommendationDiagram, UUID> {

    List<RecommendationDiagram> findAllByOrderByTitleAsc();
}
