package ca.inspection.home.inspection.service;

import ca.inspection.home.inspection.DTO.InvoiceAmount;
import ca.inspection.home.inspection.entity.BookingSummary;
import ca.inspection.home.inspection.entity.InspectionBookings;
import ca.inspection.home.inspection.entity.InspectionReport;
import ca.inspection.home.inspection.entity.Invoice;
import ca.inspection.home.inspection.repository.InspectionBookingsRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

@Service
public class InspectionBookingsService {
    @Autowired
    private InspectionBookingsRepository inspectionBookingsRepository;

    @Autowired
    private InspectorProfileService inspectorProfileService;

    public InspectionBookings createBooking(InspectionBookings booking){
        booking.setInspectionNumber(inspectorProfileService.getAndUpdateNumber());
        return inspectionBookingsRepository.save(booking);
    }

    public List<BookingSummary> findAll(){
        return inspectionBookingsRepository.findBookingSummariesByOrderByCreatedAtDesc();
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
            if (booking.getInvoices() != null){
                booking.getInvoices().forEach(invoice -> invoice.setBookings(booking));
            }
            inspectionBookingsRepository.save(booking);
            return ResponseEntity.ok().build();
        } catch (Exception e){
            e.printStackTrace();
            return ResponseEntity.badRequest().build();
        }
    }

    public ResponseEntity<?> deleteBooking(UUID id){
        try {
            InspectionBookings bookings = findById(id);
            inspectionBookingsRepository.delete(bookings);
            return ResponseEntity.ok().build();
        } catch (Exception e){
            e.printStackTrace();
            return ResponseEntity.badRequest().build();
        }
    }

    public InvoiceAmount buildInvoiceAmount(List<Invoice> invoices){
        BigDecimal subtotal = invoices.stream()
                .map(Invoice::getFee)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal hst = subtotal.multiply(new BigDecimal("0.08"));
        BigDecimal gst = subtotal.multiply(new BigDecimal("0.05"));
        BigDecimal total = subtotal.add(hst).add(gst);
        return new InvoiceAmount(
                subtotal.setScale(2, RoundingMode.HALF_UP),
                hst.setScale(2, RoundingMode.HALF_UP),
                gst.setScale(2, RoundingMode.HALF_UP),
                total.setScale(2, RoundingMode.HALF_UP)
        );
    }
}
