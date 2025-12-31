package ca.inspection.home.inspection.controller;

import ca.inspection.home.inspection.entity.InspectionReport;
import ca.inspection.home.inspection.service.InspectionReportsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class InspectionReportsController {
    @Autowired
    private InspectionReportsService inspectionReportsService;

    @GetMapping("/reports/{bookingId}")
    public InspectionReport getReport(@PathVariable UUID bookingId){
        return inspectionReportsService.getOrCreateByBooking(bookingId);
    }
}
