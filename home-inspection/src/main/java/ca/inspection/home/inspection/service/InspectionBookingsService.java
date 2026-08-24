package ca.inspection.home.inspection.service;

import ca.inspection.home.inspection.DTO.BookingDetails;
import ca.inspection.home.inspection.DTO.InvoiceAmount;
import ca.inspection.home.inspection.entity.InspectionBookings;
import ca.inspection.home.inspection.entity.InspectionReport;
import ca.inspection.home.inspection.entity.InspectorProfile;
import ca.inspection.home.inspection.entity.Invoice;
import ca.inspection.home.inspection.repository.InspectionBookingsRepository;
import ca.inspection.home.inspection.repository.InspectionReportsRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
public class InspectionBookingsService {
    @Autowired
    private InspectionBookingsRepository inspectionBookingsRepository;

    @Autowired
    private InspectorProfileService inspectorProfileService;

    @Autowired
    private InspectionReportsRepository inspectionReportsRepository;

    public InspectionBookings createBooking(InspectionBookings booking){
        log.info("Creating booking for {} {}", booking.getClientFirstName(), booking.getClientLastName());
        booking.setInspectionNumber(inspectorProfileService.getAndUpdateNumber());
        // Set up invoices
        if (booking.getInvoices() != null){
            booking.getInvoices().forEach(invoice -> invoice.setBookings(booking));
        }
        InspectionBookings saved = inspectionBookingsRepository.save(booking);

        InspectionReport report = new InspectionReport();
        report.setInspectionBooking(saved);

        InspectorProfile profile = inspectorProfileService.getProfile();
        report.setSummary(profile.getSummaryLetterBody());

        report = inspectionReportsRepository.save(report);
        saved.setInspectionReport(report);
        return saved;
    }

    public List<BookingDetails> findAll(){
        return inspectionBookingsRepository.findBookingDetailsByOrderByCreatedAtDesc();
    }

    public InspectionBookings findById(UUID id){
        return inspectionBookingsRepository.findById(id).orElseThrow();
    }

    public BookingDetails getBookingDetails(UUID id){
        BookingDetails details = inspectionBookingsRepository.getBookingDetails(id);
        if (details == null){
            throw new java.util.NoSuchElementException("Booking not found: " + id);
        }
        return details;
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
            log.error("Failed to update booking {}", id, e);
            return ResponseEntity.badRequest().build();
        }
    }

    public ResponseEntity<?> deleteBooking(UUID id){
        try {
            InspectionBookings bookings = inspectionBookingsRepository.findById(id)
                            .orElseThrow();
            inspectionBookingsRepository.delete(bookings);
            log.info("Deleted booking {}", id);
            return ResponseEntity.ok().build();
        } catch (Exception e){
            log.error("Failed to delete booking {}", id, e);
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
