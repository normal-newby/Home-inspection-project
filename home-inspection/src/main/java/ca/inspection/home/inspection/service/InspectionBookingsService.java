package ca.inspection.home.inspection.service;

import ca.inspection.home.inspection.entity.InspectionBookings;
import ca.inspection.home.inspection.entity.InspectionReport;
import ca.inspection.home.inspection.repository.InspectionBookingsRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

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
}
