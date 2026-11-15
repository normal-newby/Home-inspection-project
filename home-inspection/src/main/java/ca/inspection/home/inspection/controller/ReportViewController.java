package ca.inspection.home.inspection.controller;

import ca.inspection.home.inspection.DTO.InvoiceAmount;
import ca.inspection.home.inspection.DTO.FieldGroup;
import ca.inspection.home.inspection.entity.*;
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
    private SpringTemplateEngine templateEngine;

    @Autowired
    private ReportViewService reportViewService;

    @Autowired
    private GoogleEmailService googleEmailService;

    @GetMapping(value = "/{bookingId}", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> getReport(@PathVariable UUID bookingId){
        byte[] pdf = renderPdf(bookingId, reportViewService.loadReportData(bookingId));

        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,
                "inline; filename=report-" + bookingId + ".pdf")
                .body(pdf);
    }

    // Lets the page check the Google link and show who the report goes to before sending.
    @GetMapping("/{bookingId}/email")
    @ResponseBody
    public Map<String, Object> emailRecipients(@PathVariable UUID bookingId){
        InspectionBookings booking = inspectionBookingsService.findWithClients(bookingId);
        List<String> emails = HelperFunctions.clientEmails(booking);

        Map<String, Object> recipients = new HashMap<>(Map.of("linked", googleEmailService.isLinked(), "to", emails));
        if (!emails.isEmpty()) {
            String cc = googleEmailService.ccFor(emails);
            if (cc != null) recipients.put("cc", cc);
            String agentCc = googleEmailService.agentCcFor(booking, emails);
            if (agentCc != null) {
                recipients.put("agentEmail", agentCc);
                if (HelperFunctions.notBlank(booking.getAgentName())) recipients.put("agentName", booking.getAgentName().trim());
            }
        }
        return recipients;
    }

    @PostMapping("/{bookingId}/email")
    public ResponseEntity<?> emailReport(@PathVariable UUID bookingId,
                                         @RequestParam(defaultValue = "false") boolean ccAgent){
        try {
            InspectionBookings booking = inspectionBookingsService.findWithClients(bookingId);
            List<String> emails = HelperFunctions.clientEmails(booking);

            if (emails.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "booking has no email"));
            }

            if (!googleEmailService.isLinked()) {
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                        .body(Map.of("error", "Google is not linked. Link it on the Profile page to send emails."));
            }

            ReportViewService.ReportData data = reportViewService.loadReportData(bookingId);
            byte[] pdf = renderPdf(bookingId, data);

            String cc = googleEmailService.sendReportEmail(emails, pdf, data.booking(), data.report().getEmailBody(), ccAgent);

            Map<String, Object> sent = new HashMap<>(Map.of("sent", true, "to", String.join(", ", emails)));
            if (cc != null) sent.put("cc", cc);
            return ResponseEntity.ok().body(sent);
        } catch (Exception e){
            log.error("Failed to send report email for booking {}", bookingId, e);
            return ResponseEntity.internalServerError().body(Map.of("error", "could not send email"));
        }
    }

    private byte[] renderPdf(UUID bookingId, ReportViewService.ReportData data){
        // Read once: the template needs to know it exists, the pdf service needs its bytes.
        byte[] appendix = inspectionReportsService.readAppendixPdfBytes(data.report());
        Context context = buildReportContext(data, appendix != null);
        return reportViewService.generatePdf("report", context, bookingId, appendix);
    }

    // Works on data already loaded: only the photo files are read here.
    private Context buildReportContext(ReportViewService.ReportData data, boolean hasAppendix){
        InspectionBookings booking = data.booking();
        InspectionReport report = data.report();
        Context context = new Context();

        InspectorProfile profile = inspectorProfileService.getProfile();
        InvoiceAmount amount = inspectionBookingsService.buildInvoiceAmount(
                booking.getInvoices(), Boolean.TRUE.equals(booking.getRemoveTax()));

        reportViewService.setCoverPageImageBase64(report);

        List<InspectionField> fields = reportViewService.getSortedFields(report);
        Map<String, Map<String, List<FieldGroup>>> allFields = reportViewService.getAllFields(fields);
        reportViewService.numberFigures(allFields);
        Map<String, List<InspectionField>> summaryFields = reportViewService.getSummaryFields(fields);

        context.setVariable("booking", booking);
        context.setVariable("clientNames", Objects.requireNonNullElse(HelperFunctions.fullName(booking), ""));
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
