package ca.inspection.home.inspection.service;

import ca.inspection.home.inspection.entity.InspectionField;
import ca.inspection.home.inspection.entity.InspectionReport;
import lombok.AllArgsConstructor;
import org.apache.tomcat.util.http.fileupload.ByteArrayOutputStream;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ReportViewService {

    private List<String> placeOrder = List.of(
            "roofing", "exterior", "structure", "electrical", "heating", "cooling", "insulation", "plumbing", "interior"
    );

    private List<String> typeOrder = List.of(
            "description", "limitations", "recommendations"
    );

    @Autowired
    private SpringTemplateEngine templateEngine;

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private InspectionImagesService inspectionImagesService;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${pdf.service.url}")
    private String pdfServiceUrl;

    public Comparator<InspectionField> getComparator(){
        Comparator<InspectionField> fieldComparator = Comparator.comparingInt(f -> {
                    String place = f.getInspectionFieldDefinition().getFieldPlace().toLowerCase();
                    int idx = placeOrder.indexOf(place);
                    return idx == -1 ? Integer.MAX_VALUE : idx; // unknown places go to end
                });

        fieldComparator = fieldComparator.thenComparingInt(f -> {
            String type = f.getInspectionFieldDefinition().getFieldType().toLowerCase();
            int idx = typeOrder.indexOf(type);
            return idx == -1 ? Integer.MAX_VALUE : idx;
        });

        return fieldComparator;
    }

    public List<InspectionField> getSortedFields(InspectionReport report,
                                                 Comparator<InspectionField> fieldComparator){
        List<InspectionField> fields = report.getFields().stream()
                .filter(f -> f != null)
                .filter(f -> f.getInspectionFieldDefinition() != null)
                .filter(f -> f.getInspectionFieldDefinition().getFieldPlace() != null)
                .filter(f -> f.getInspectionFieldDefinition().getFieldType() != null)
                .sorted(fieldComparator)
                .toList();

        // Convert images to base64
        fields.forEach(field -> {
            if (field.getInspectionImages() != null && !field.getInspectionImages().isEmpty()){
                field.getInspectionImages().forEach(image -> {
                    String src = inspectionImagesService.toBase64(image.getId(), image.getAnnotations());
                    image.setBase64(src);
                });
            }
        });

        return fields;
    }

    public Map<String, Map<String, List<InspectionField>>> getAllFields(List<InspectionField> fields){
        return fields.stream()
                .collect(Collectors.groupingBy(
                        f -> f.getInspectionFieldDefinition().getFieldPlace(),
                        LinkedHashMap::new,
                        Collectors.groupingBy(
                                f -> f.getInspectionFieldDefinition().getFieldType(),
                                LinkedHashMap::new,
                                Collectors.toList()
                        )
                ));
    }

    public Map<String, List<InspectionField>> getSummaryFields(List<InspectionField> fields){
        return fields.stream()
                .filter(f -> Boolean.TRUE.equals(f.getIncludeInSummary()))
                .filter(f -> f.getInspectionFieldDefinition().getFieldType().equals("recommendations"))
                .filter(f -> f.getInspectionRecommendationField() != null)
                .collect(Collectors.groupingBy(
                        f -> f.getInspectionFieldDefinition().getFieldPlace(),
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

    }

    public byte[] generatePdf(String templateName, Context context, UUID bookingId){
        try {
            // Read CSS file and inject into context
            String css = new String(getClass()
                    .getClassLoader()
                    .getResourceAsStream("static/styles.css")
                    .readAllBytes());
            context.setVariable("css", css);
        } catch (Exception e) {
            throw new RuntimeException("Could not load styles.css", e);
        }

        String html = templateEngine.process(templateName, context);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = Map.of("html", html, "bookingId", bookingId);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        ResponseEntity<byte[]> response = restTemplate.exchange(
                pdfServiceUrl + "/generate-pdf",
                HttpMethod.POST,
                request,
                byte[].class
        );

        return response.getBody();
    }
}
