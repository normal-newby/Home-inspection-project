package ca.inspection.home.inspection.controller;

import ca.inspection.home.inspection.DTO.InvoiceAmount;
import ca.inspection.home.inspection.entity.*;
import ca.inspection.home.inspection.service.InspectionBookingsService;
import ca.inspection.home.inspection.service.InspectionReportsService;
import ca.inspection.home.inspection.service.InspectorProfileService;
import ca.inspection.home.inspection.service.ReportViewService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.util.*;

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
    private SpringTemplateEngine templateEngine;

    @Autowired
    private ReportViewService reportViewService;

    @GetMapping(value = "/{bookingId}", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> getReport(@PathVariable UUID bookingId){
        Context context = new Context();

        InspectorProfile profile = inspectorProfileService.getProfile();
        InspectionBookings booking = inspectionBookingsService.findById(bookingId);
        InvoiceAmount amount = inspectionBookingsService.buildInvoiceAmount(booking.getInvoices());
        InspectionReport report = inspectionReportsService.getOrCreateByBooking(bookingId);

        reportViewService.getOtherFields(report);
        reportViewService.setCoverPageImageBase64(report);

        Comparator<InspectionField> fieldComparator = reportViewService.getComparator();

        List<InspectionField> fields = reportViewService.getSortedFields(
                report, fieldComparator
        );
        Map<String, Map<String, List<InspectionField>>> allFields = reportViewService.getAllFields(fields);
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

        byte[] pdf = reportViewService.generatePdf("report", context, bookingId, report);

        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,
                "inline; filename=report-" + bookingId + ".pdf")
                .body(pdf);
    }
}
