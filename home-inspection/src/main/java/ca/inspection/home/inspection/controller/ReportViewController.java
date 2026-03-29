package ca.inspection.home.inspection.controller;

import ca.inspection.home.inspection.entity.InspectionBookings;
import ca.inspection.home.inspection.entity.InspectionField;
import ca.inspection.home.inspection.entity.InspectionReport;
import ca.inspection.home.inspection.entity.InspectorProfile;
import ca.inspection.home.inspection.service.InspectionBookingsService;
import ca.inspection.home.inspection.service.InspectionReportsService;
import ca.inspection.home.inspection.service.InspectorProfileService;
import ca.inspection.home.inspection.service.ReportViewService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

import javax.print.attribute.standard.Media;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

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
    /*
    @GetMapping("/{bookingId}")
    public String viewReport(@PathVariable UUID bookingId, Model model) {
        InspectorProfile profile = inspectorProfileService.getProfile();

        InspectionBookings booking = inspectionBookingsService.findById(bookingId);

        InspectionReport report = inspectionReportsService.getOrCreateByBooking(bookingId);

        Comparator<InspectionField> fieldComparator = reportViewService.getComparator();

        List<InspectionField> fields = reportViewService.getSortedFields(
                report, fieldComparator
        );

        Map<String, Map<String, List<InspectionField>>> allFields = reportViewService.getAllFields(fields);

        Map<String, Map<String, List<InspectionField>>> summaryFields = reportViewService.getSummaryFields(fields);

        model.addAttribute("booking", booking);
        model.addAttribute("profile", profile);
        model.addAttribute("summaryFields", summaryFields);
        model.addAttribute("allFields", allFields);
        model.addAttribute("report", report);
        return "report";
    }*/

    @GetMapping(value = "/{bookingId}", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> getReport(@PathVariable UUID bookingId){
        Context context = new Context();

        InspectorProfile profile = inspectorProfileService.getProfile();
        InspectionBookings booking = inspectionBookingsService.findById(bookingId);
        InspectionReport report = inspectionReportsService.getOrCreateByBooking(bookingId);

        Comparator<InspectionField> fieldComparator = reportViewService.getComparator();

        List<InspectionField> fields = reportViewService.getSortedFields(
                report, fieldComparator
        );
        Map<String, Map<String, List<InspectionField>>> allFields = reportViewService.getAllFields(fields);
        Map<String, List<InspectionField>> summaryFields = reportViewService.getSummaryFields(fields);

        context.setVariable("booking", booking);
        context.setVariable("profile", profile);
        context.setVariable("summaryFields", summaryFields);
        context.setVariable("allFields", allFields);
        context.setVariable("report", report);

        byte[] pdf = reportViewService.generatePdf("report", context);

        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,
                "inline; filename = report-" + bookingId + ".pdf")
                .body(pdf);
    }
}
