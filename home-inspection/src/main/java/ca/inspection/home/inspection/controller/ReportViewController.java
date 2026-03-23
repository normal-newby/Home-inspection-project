package ca.inspection.home.inspection.controller;

import ca.inspection.home.inspection.entity.InspectionBookings;
import ca.inspection.home.inspection.entity.InspectionField;
import ca.inspection.home.inspection.entity.InspectionReport;
import ca.inspection.home.inspection.entity.InspectorProfile;
import ca.inspection.home.inspection.service.InspectionBookingsService;
import ca.inspection.home.inspection.service.InspectionReportsService;
import ca.inspection.home.inspection.service.InspectorProfileService;
import ca.inspection.home.inspection.service.ReportViewService;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
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
    }

    @GetMapping("/{bookingId}/pdf")
    public ResponseEntity<byte[]> downloadReportPdf(@PathVariable UUID bookingId, HttpServletRequest request) {
        InspectionReport report = inspectionReportsService.getOrCreateByBooking(bookingId);

        Context context = new Context();
        context.setVariable("report", report);
        String html = templateEngine.process("report", context);

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            String baseUrl = getBaseUrl(request);
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.withHtmlContent(html, baseUrl);
            builder.toStream(baos);
            builder.run();

            byte[] pdfBytes = baos.toByteArray();

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.setContentDisposition(ContentDisposition.attachment()
                    .filename("inspection-report-" + bookingId + ".pdf")
                    .build());

            return new ResponseEntity<>(pdfBytes, headers, HttpStatus.OK);
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    private String getBaseUrl(HttpServletRequest request) {
        String scheme = request.getScheme();
        String serverName = request.getServerName();
        int serverPort = request.getServerPort();
        String contextPath = request.getContextPath();
        StringBuilder base = new StringBuilder();
        base.append(scheme).append("://").append(serverName);
        if ((scheme.equals("http") && serverPort != 80) || (scheme.equals("https") && serverPort != 443)) {
            base.append(":").append(serverPort);
        }
        base.append(contextPath);
        if (!contextPath.endsWith("/")) {
            base.append("/");
        }
        return base.toString();
    }
}
