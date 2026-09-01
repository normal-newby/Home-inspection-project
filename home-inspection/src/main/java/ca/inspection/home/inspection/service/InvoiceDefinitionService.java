package ca.inspection.home.inspection.service;

import ca.inspection.home.inspection.entity.InvoiceDefinition;
import ca.inspection.home.inspection.repository.InvoiceDefinitionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
public class InvoiceDefinitionService {
    @Autowired
    private InvoiceDefinitionRepository invoiceDefinitionRepository;

    public InvoiceDefinition saveInvoiceDefinition(InvoiceDefinition invoice){
        return invoiceDefinitionRepository.save(invoice);
    }

    public List<InvoiceDefinition> getInvoiceDefinitions(){
        return invoiceDefinitionRepository.findAll();
    }

    public ResponseEntity<?> updateInvoiceDefinition(UUID id, InvoiceDefinition incomingDefinition){
        try {
            InvoiceDefinition existing = invoiceDefinitionRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Invoice definition not found"));

            existing.setType(incomingDefinition.getType());
            existing.setFee(incomingDefinition.getFee());

            InvoiceDefinition saved = invoiceDefinitionRepository.save(existing);
            log.info("Updated invoice definition {}", id);
            return ResponseEntity.ok(saved);
        } catch (Exception e){
            log.error("Failed to update invoice definition {}", id, e);
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    public ResponseEntity<?> deleteInvoiceDefinition(UUID id){
        try {
            if (!invoiceDefinitionRepository.existsById(id)) {
                throw new RuntimeException("Invoice definition not found: " + id);
            }

            invoiceDefinitionRepository.deleteById(id);
            return ResponseEntity.ok().body(Map.of("Deleted", true));
        } catch (Exception e){
            log.error("Failed to delete invoice definition {}", id, e);
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}
