package ca.inspection.home.inspection.service;

import ca.inspection.home.inspection.entity.InspectionBookings;
import ca.inspection.home.inspection.entity.InspectionReport;
import ca.inspection.home.inspection.entity.Invoice;
import ca.inspection.home.inspection.entity.InvoiceDefinition;
import ca.inspection.home.inspection.repository.InspectionBookingsRepository;
import ca.inspection.home.inspection.repository.InvoiceDefinitionRepository;
import ca.inspection.home.inspection.repository.InvoiceRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class InspectionBookingsService {
    @Autowired
    private InspectionBookingsRepository inspectionBookingsRepository;

    @Autowired
    private InspectorProfileService inspectorProfileService;

    @Autowired
    private InvoiceRepository invoiceRepository;

    @Autowired
    private InvoiceDefinitionRepository invoiceDefinitionRepository;

    public InspectionBookings createBooking(InspectionBookings booking){
        booking.setInspectionNumber(inspectorProfileService.getAndUpdateNumber());
        return inspectionBookingsRepository.save(booking);
    }

    public List<InspectionBookings> findAll(){
        return inspectionBookingsRepository.findAllByOrderByCreatedAtDesc();
    }

    public InspectionBookings findById(UUID id){
        return inspectionBookingsRepository.findById(id)
                .orElseThrow();
    }

    public InspectionReport getReportFromBooking(UUID bookingId){
        InspectionBookings booking = inspectionBookingsRepository.findById(bookingId)
                .orElseThrow(() -> new RuntimeException("booking not found"));
        return booking.getInspectionReport();
    }

    public ResponseEntity<?> updateBooking(UUID id, InspectionBookings booking){
        try {
            booking.setId(id);
            inspectionBookingsRepository.save(booking);
            return ResponseEntity.ok().build();
        } catch (Exception e){
            e.printStackTrace();
            return ResponseEntity.badRequest().build();
        }
    }

    public Invoice addInvoiceToBooking(UUID id, UUID templateId){
        try {
            InspectionBookings bookings = findById(id);
            InvoiceDefinition invoiceDefinition = invoiceDefinitionRepository.findById(templateId)
                    .orElseThrow(() -> new RuntimeException("definition not found"));

            Invoice invoice = new Invoice();
            invoice.setType(invoiceDefinition.getType());
            invoice.setFee(invoiceDefinition.getFee());
            invoice.setBookings(bookings);

            return invoiceRepository.save(invoice);
        } catch (Exception e){
            e.printStackTrace();
            return null;
        }
    }

    public ResponseEntity<?> deleteInvoiceFromBooking(UUID id, UUID invoiceId){
        try {
            InspectionBookings bookings = findById(id);
            Invoice invoice = invoiceRepository.findById(invoiceId)
                    .orElseThrow(() -> new RuntimeException("Failed to find invoice"));

            bookings.getInvoices().remove(invoice);
            return ResponseEntity.ok(Map.of("Invoice deleted: ", invoiceId));
        } catch (Exception e){
            e.printStackTrace();
            return ResponseEntity.badRequest().build();
        }
    }
}
