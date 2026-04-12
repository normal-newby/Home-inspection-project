package ca.inspection.home.inspection.repository;

import ca.inspection.home.inspection.entity.InvoiceDefinition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface InvoiceDefinitionRepository extends JpaRepository<InvoiceDefinition, UUID> {
}
