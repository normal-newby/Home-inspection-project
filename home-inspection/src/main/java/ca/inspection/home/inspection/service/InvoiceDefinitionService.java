package ca.inspection.home.inspection.service;

import ca.inspection.home.inspection.entity.Invoice;
import ca.inspection.home.inspection.entity.InvoiceDefinition;
import ca.inspection.home.inspection.repository.InvoiceDefinitionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class InvoiceDefinitionService {
    @Autowired
    private InvoiceDefinitionRepository invoiceDefinitionRepository;

    public InvoiceDefinition saveInvoiceDefinition(InvoiceDefinition invoice){
        return invoiceDefinitionRepository.save(invoice);
    }

    public List<InvoiceDefinition> getInvoiceDefinitions(){
        return invoiceDefinitionRepository.findAll();
    }
}
