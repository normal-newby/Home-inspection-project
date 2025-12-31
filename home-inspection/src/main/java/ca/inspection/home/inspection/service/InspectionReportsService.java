package ca.inspection.home.inspection.service;

import ca.inspection.home.inspection.entity.InspectionBookings;
import ca.inspection.home.inspection.entity.InspectionReport;
import ca.inspection.home.inspection.repository.InspectionBookingsRepository;
import ca.inspection.home.inspection.repository.InspectionReportsRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class InspectionReportsService {
    @Autowired
    private InspectionReportsRepository inspectionReportsRepository;

    @Autowired
    private InspectionBookingsRepository inspectionBookingsRepository;

    public InspectionReport getOrCreateByBooking(UUID bookingId){
        InspectionReport report = inspectionReportsRepository.findByInspectionBooking_Id(bookingId);
        if (report != null){
            System.out.println(" seen before");
            return report;
        }
        System.out.println("not Seen before");

        InspectionBookings booking = inspectionBookingsRepository.findById(bookingId).orElseThrow();

        InspectionReport newReport = new InspectionReport();
        newReport.setInspectionBooking(booking);

        return inspectionReportsRepository.save(newReport);
    }
}
