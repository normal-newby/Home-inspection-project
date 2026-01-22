package ca.inspection.home.inspection.service;

import ca.inspection.home.inspection.entity.InspectionBookings;
import ca.inspection.home.inspection.entity.InspectionReport;
import ca.inspection.home.inspection.repository.InspectionBookingsRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;
import java.util.UUID;

@Service
public class InspectionBookingsService {
    @Autowired
    private InspectionBookingsRepository inspectionBookingsRepository;

    public InspectionBookings createBooking(InspectionBookings booking){
        return inspectionBookingsRepository.save(booking);
    }

    public List<InspectionBookings> findAll(){
        return inspectionBookingsRepository.findAll();
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
}
