package ca.inspection.home.inspection.controller;

import ca.inspection.home.inspection.entity.InvoiceDefinition;
import ca.inspection.home.inspection.service.InvoiceDefinitionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class InvoiceDefinitionController {
    @Autowired
    private InvoiceDefinitionService invoiceDefinitionService;

    @PostMapping("/invoice-definition")
    public InvoiceDefinition saveInvoiceDefinition(@RequestBody InvoiceDefinition invoiceDefinition){
        return invoiceDefinitionService.saveInvoiceDefinition(invoiceDefinition);
    }

    @GetMapping("/invoice-definition")
    public List<InvoiceDefinition> getInvoiceDefinitions(){
        return invoiceDefinitionService.getInvoiceDefinitions();
    }

    @PutMapping("/invoice-definition/{id}")
    public ResponseEntity<?> updateInvoiceDefinition(@PathVariable UUID id, @RequestBody InvoiceDefinition invoice){
        return invoiceDefinitionService.updateInvoiceDefinition(id, invoice);
    }

    @DeleteMapping("/invoice-definition/{id}")
    public ResponseEntity<?> deleteInvoiceDefinition(@PathVariable UUID id){
        return invoiceDefinitionService.deleteInvoiceDefinition(id);
    }
}
