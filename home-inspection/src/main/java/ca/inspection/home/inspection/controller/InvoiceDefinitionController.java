package ca.inspection.home.inspection.controller;

import ca.inspection.home.inspection.entity.InvoiceDefinition;
import ca.inspection.home.inspection.service.InvoiceDefinitionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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
}
