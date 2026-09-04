package ca.inspection.home.inspection.controller;

import ca.inspection.home.inspection.DTO.InvoiceAmount;
import ca.inspection.home.inspection.DTO.FieldGroup;
import ca.inspection.home.inspection.entity.*;
import ca.inspection.home.inspection.repository.InspectionReportsRepository;
import ca.inspection.home.inspection.service.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.util.*;

import static ca.inspection.home.inspection.service.HelperFunctions.notBlank;

@Slf4j
@Controller
@RequestMapping("/report")
@CrossOrigin(origins = "*")
public class ReportViewController {

    @Autowired
    private InspectorProfileService inspectorProfileService;

    @Autowired
    private InspectionBookingsService inspectionBookingsService;

    @Autowired
    private InspectionReportsService inspectionReportsService;

    @Autowired
    private InspectionReportsRepository inspectionReportsRepository;

    @Autowired
    private SpringTemplateEngine templateEngine;

    @Autowired
    private ReportViewService reportViewService;

    @Autowired
    private GoogleEmailService googleEmailService;

    @GetMapping(value = "/{bookingId}", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> getReport(@PathVariable UUID bookingId){
        InspectionBookings booking = inspectionBookingsService.findById(bookingId);
        InspectionReport report = inspectionReportsRepository.findByInspectionBooking_Id(bookingId);
        Context context = buildReportContext(bookingId, booking, report);

        byte[] pdf = reportViewService.generatePdf("report", context, bookingId, report);

        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,
                "inline; filename=report-" + bookingId + ".pdf")
                .body(pdf);
    }

    @PostMapping("/{bookingId}/email")
    public ResponseEntity<?> emailReport(@PathVariable UUID bookingId){
        try {
            InspectionBookings booking = inspectionBookingsService.findById(bookingId);
            String email = booking.getEmail();

            if (!notBlank(email)) {
                return ResponseEntity.badRequest().body(Map.of("error", "booking has no email"));
            }

            if (!googleEmailService.isConfigured()) {
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                        .body(Map.of("error", "Email or API key not set up"));
            }

            InspectionReport report = inspectionReportsRepository.findByInspectionBooking_Id(bookingId);
            Context context = buildReportContext(bookingId, booking, report);

            byte[] pdf = reportViewService.generatePdf("report", context, bookingId, report);

            googleEmailService.sendReportEmail(email, pdf, booking);

            return ResponseEntity.ok().body(Map.of("sent", true, "to", email));
        } catch (Exception e){
            log.error("Failed to send report email for booking {}", bookingId, e);
            return ResponseEntity.internalServerError().body(Map.of("error", "could not send email"));
        }
    }

    private Context buildReportContext(UUID bookingId, InspectionBookings booking, InspectionReport report){
        Context context = new Context();

        InspectorProfile profile = inspectorProfileService.getProfile();
        InvoiceAmount amount = inspectionBookingsService.buildInvoiceAmount(
                booking.getInvoices(), Boolean.TRUE.equals(booking.getRemoveTax()));

        reportViewService.getOtherFields(report);
        reportViewService.setCoverPageImageBase64(report);

        List<InspectionField> fields = reportViewService.getSortedFields(report);
        Map<String, Map<String, List<FieldGroup>>> allFields = reportViewService.getAllFields(fields);
        reportViewService.numberFigures(allFields);
        Map<String, List<InspectionField>> summaryFields = reportViewService.getSummaryFields(fields);

        boolean hasAppendix = inspectionReportsService.readAppendixPdfBytes(report) != null;

        context.setVariable("booking", booking);
        context.setVariable("invoiceAmount", amount);
        context.setVariable("profile", profile);
        context.setVariable("summaryFields", summaryFields);
        context.setVariable("allFields", allFields);
        context.setVariable("report", report);
        context.setVariable("navSections", reportViewService.getPopulatedNavSections(
                allFields, summaryFields, hasAppendix
        ));
        context.setVariable("companyAssets", reportViewService.getCompanyAssetsBase64());

        return context;
    }
}
