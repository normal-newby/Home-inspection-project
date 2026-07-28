package ca.inspection.home.inspection.service;

import ca.inspection.home.inspection.DTO.NavSection;
import ca.inspection.home.inspection.entity.*;
import ca.inspection.home.inspection.repository.ImageAnnotationRepository;
import ca.inspection.home.inspection.repository.InspectionImagesRepository;
import ca.inspection.home.inspection.repository.InspectionRecommendationFieldRepository;
import ca.inspection.home.inspection.repository.InspectionReportsRepository;
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

    private static final Map<String, String> navSectionColours = new LinkedHashMap<>() {{
        put("summary",     "#943b08");
        put("roofing",     "#a68368");
        put("exterior",    "#7BB369");
        put("structure",   "#777777");
        put("electrical",  "#FFA500");
        put("heating",     "#ff6d4d");
        put("cooling",     "#399cff");
        put("insulation",  "#ffb6c1");
        put("plumbing",    "#ADD8E6");
        put("interior",    "#D2D1CD");
        put("appendix",    "#5c5a52");
    }};

    @Autowired
    private SpringTemplateEngine templateEngine;

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private InspectionImagesService inspectionImagesService;

    @Autowired
    private InspectionReportsService inspectionReportsService;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${pdf.service.url}")
    private String pdfServiceUrl;

    @Autowired
    private InspectionImagesRepository inspectionImagesRepository;

    @Autowired
    private ImageAnnotationRepository imageAnnotationRepository;

    @Autowired
    private CompanyAssetService companyAssetService;

    public void getOtherFields(InspectionReport report){
        List<UUID> fieldIds = report.getFields().stream().map(InspectionField::getId).toList();
        if (fieldIds.isEmpty()) return;

        //Field images
        Map<UUID, List<InspectionImage>> imagesMap = inspectionImagesRepository
                .findByInspectionField_IdIn(fieldIds).stream()
                .collect(Collectors.groupingBy(img -> img.getInspectionField().getId()));

        //Image Ids
        List<UUID> imageIds = imagesMap.values().stream()
                .flatMap(List::stream).map(InspectionImage::getId).toList();

        //Annotations
        Map<UUID, Set<ImageAnnotation>> annotationMap;
        if (imageIds.isEmpty()){
            annotationMap = Map.of();
        } else {
            annotationMap = imageAnnotationRepository.findByInspectionImageIdIn(imageIds).stream()
                    .collect(Collectors.groupingBy(ann -> ann.getInspectionImage().getId(),
                            Collectors.toSet()));
        }

        //Put into report
        report.getFields().forEach(field -> {
            List<InspectionImage> images = imagesMap.getOrDefault(field.getId(), new ArrayList<>());
            images.forEach(img -> {
                img.setAnnotations(annotationMap.getOrDefault(img.getId(), new HashSet<>()));
            });
            field.setInspectionImages(images);
        });
    }

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

    public void setCoverPageImageBase64(InspectionReport report){
        InspectionImage coverImage = report.getCoverPageImage();
        if (coverImage != null){
            String base64 = inspectionImagesService.toBase64(coverImage.getId(), null);
            coverImage.setBase64(base64);
        }
    }

    public List<String> getCompanyAssetsBase64(){
        return companyAssetService.getAllAssets().stream()
                .map(asset -> companyAssetService.toBase64(asset))
                .collect(Collectors.toList());
    }

    // Nav section colours

    public List<NavSection> getPopulatedNavSections(
            Map<String, Map<String, List<InspectionField>>> allFields,
            Map<String, List<InspectionField>> summaryFields,
            boolean hasAppendix
    ){
        List<NavSection> sections = new ArrayList<>();

        if (summaryFields != null && !summaryFields.isEmpty()){
            sections.add(new NavSection("summary", "Summary", "summary-page", navSectionColours.get("summary")));
        }

        navSectionColours.forEach((key, colour) -> {
            if (allFields.containsKey(key)) {
                sections.add(new NavSection(key, HelperFunctions.capitalizeFirstLetter(key), "place-" + key, colour));
            }
        });

        if (hasAppendix){
            sections.add(new NavSection("appendix", "Appendix", "appendix-page", navSectionColours.get("appendix")));
        }

        return sections;
    }

    public String buildColourVariablesCSS() {
        StringBuilder sb = new StringBuilder(":root {\n");
        navSectionColours.forEach((key, colour) -> {
            sb.append("\t--place-").append(key).append(": ").append(colour).append(";\n");
        });
        sb.append("}\n");
        return sb.toString();
    }

    public String buildNavPageCSS(){
        StringBuilder sb = new StringBuilder();
        navSectionColours.keySet().forEach(key -> {
            sb.append("@page ").append(key).append("-page { ") //page stuff
                    .append("size: Letter portrait;\n")
                    .append("margin: 36mm 15mm 20mm 15mm;\n")
                    .append("@top-left { content: element(nav-").append(key).append("); width: 100% }")
                    .append("@top-right { content: none;} ")
                    .append("@bottom-center { content: element(running-footer); }")
                    .append("}\n");
            sb.append(".report-header-block-").append(key) // css on page
                    .append(" { position: running(nav-").append(key).append(");")
                    .append("border-bottom: 1px solid var(--border);\n")
                    .append("width: 100%; }\n");
        });
        return sb.toString();
    }

    // PDF generation

    public byte[] generatePdf(String templateName, Context context, UUID bookingId, InspectionReport report){
        try {
            // Read CSS file and inject into context
            String css = new String(getClass()
                    .getClassLoader()
                    .getResourceAsStream("static/styles.css")
                    .readAllBytes());
            css += "\n" + buildColourVariablesCSS();
            css += "\n" + buildNavPageCSS();
            context.setVariable("css", css);
        } catch (Exception e) {
            throw new RuntimeException("Could not load styles.css", e);
        }

        String html = templateEngine.process(templateName, context);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = new HashMap<>();
        body.put("html", html);
        body.put("bookingId", bookingId);

        byte[] appendixBytes = inspectionReportsService.readAppendixPdfBytes(report);
        if (appendixBytes != null){
            body.put("appendixBase64", Base64.getEncoder().encodeToString(appendixBytes));
        }

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
