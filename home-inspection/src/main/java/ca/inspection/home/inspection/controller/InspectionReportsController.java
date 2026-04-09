package ca.inspection.home.inspection.controller;

import ca.inspection.home.inspection.entity.InspectionReport;
import ca.inspection.home.inspection.service.InspectionReportsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
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

    @PostMapping("/reports/{bookingId}")
    public ResponseEntity<?> updateReportData(@PathVariable UUID bookingId, @RequestBody Map<String, String> body){
        return inspectionReportsService.updateReportData(bookingId, body);
    }

    @PostMapping("/reports/{bookingId}/appendix-pdf")
    public ResponseEntity<?> updateAppendixPdf(@PathVariable UUID bookingId, @RequestParam MultipartFile file){
        return inspectionReportsService.updateAppendixPdf(bookingId, file);
    }
}
