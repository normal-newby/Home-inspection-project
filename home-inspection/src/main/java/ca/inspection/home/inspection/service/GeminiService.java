package ca.inspection.home.inspection.service;

import ca.inspection.home.inspection.entity.InspectionField;
import ca.inspection.home.inspection.entity.InspectionRecommendationField;
import ca.inspection.home.inspection.entity.InspectionReport;
import tools.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Sends the flagged summary items of an inspection report to Google Gemini and
 * returns a short, human readable summary of the home for the report.
 */
@Service
public class GeminiService {

    private final HttpClient httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_2)
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${gemini.api-key:}")
    private String apiKey;

    @Value("${gemini.model:gemini-2.5-flash}")
    private String model;

    private static final String API_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent";

    // Order the components are presented to the model (matches the report layout).
    private static final List<String> PLACE_ORDER = List.of(
            "structure", "roofing", "exterior", "electrical",
            "heating", "cooling", "insulation", "plumbing", "interior"
    );

    public String generateSummary(InspectionReport report) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("Gemini API key is not configured");
        }

        String prompt = buildPrompt(report);

        Map<String, Object> requestBody = Map.of(
                "contents", List.of(Map.of(
                        "parts", List.of(Map.of("text", prompt))
                )),
                "generationConfig", Map.of(
                        "temperature", 0.4
                )
        );

        try {
            String jsonBody = objectMapper.writeValueAsString(requestBody);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(String.format(API_URL, model)))
                    .timeout(Duration.ofSeconds(60))
                    .header("Content-Type", "application/json")
                    .header("x-goog-api-key", apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new RuntimeException("Gemini request failed (" + response.statusCode() + "): " + response.body());
            }

            Map<String, Object> parsed = objectMapper.readValue(response.body(), Map.class);
            return extractText(parsed);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Gemini request failed: " + e.getMessage(), e);
        }
    }

    @SuppressWarnings("unchecked")
    private String extractText(Map<String, Object> body) {
        if (body == null) {
            throw new RuntimeException("Empty response from Gemini");
        }
        List<Map<String, Object>> candidates = (List<Map<String, Object>>) body.get("candidates");
        if (candidates == null || candidates.isEmpty()) {
            throw new RuntimeException("No candidates returned from Gemini");
        }
        Map<String, Object> content = (Map<String, Object>) candidates.get(0).get("content");
        if (content == null) {
            throw new RuntimeException("No content returned from Gemini");
        }
        List<Map<String, Object>> parts = (List<Map<String, Object>>) content.get("parts");
        if (parts == null || parts.isEmpty()) {
            throw new RuntimeException("No text returned from Gemini");
        }
        StringBuilder sb = new StringBuilder();
        for (Map<String, Object> part : parts) {
            Object text = part.get("text");
            if (text != null) {
                sb.append(text);
            }
        }
        return sb.toString().trim();
    }

    private String buildPrompt(InspectionReport report) {
        // Issues the inspector chose to include in the summary, grouped by component.
        Map<String, List<InspectionField>> issuesByPlace = report.getFields().stream()
                .filter(f -> Boolean.TRUE.equals(f.getIncludeInSummary()))
                .filter(f -> f.getInspectionFieldDefinition() != null)
                .filter(f -> "recommendations".equals(f.getInspectionFieldDefinition().getFieldType()))
                .filter(f -> f.getInspectionRecommendationField() != null)
                .filter(f -> f.getInspectionFieldDefinition().getFieldPlace() != null)
                .collect(Collectors.groupingBy(
                        f -> f.getInspectionFieldDefinition().getFieldPlace().toLowerCase(),
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        // Every component that was inspected, so components with no issues are still listed as "good".
        Set<String> inspectedPlaces = report.getFields().stream()
                .filter(f -> f.getInspectionFieldDefinition() != null)
                .map(f -> f.getInspectionFieldDefinition().getFieldPlace())
                .filter(Objects::nonNull)
                .map(String::toLowerCase)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        List<String> orderedPlaces = new ArrayList<>();
        for (String p : PLACE_ORDER) {
            if (inspectedPlaces.contains(p)) {
                orderedPlaces.add(p);
            }
        }
        for (String p : inspectedPlaces) {
            if (!orderedPlaces.contains(p)) {
                orderedPlaces.add(p);
            }
        }

        StringBuilder data = new StringBuilder();
        for (String place : orderedPlaces) {
            data.append("\n").append(place.toUpperCase()).append(":\n");
            List<InspectionField> issues = issuesByPlace.get(place);
            if (issues == null || issues.isEmpty()) {
                data.append("  No significant issues flagged (in good condition).\n");
                continue;
            }
            for (InspectionField field : issues) {
                InspectionRecommendationField r = field.getInspectionRecommendationField();
                data.append("  - ");
                // The condition the inspector recorded
                if (notBlank(field.getDisplayValue())) {
                    data.append("Condition: ").append(field.getDisplayValue().trim()).append(" | ");
                }
                if (notBlank(r.getTask())) {
                    data.append("Issue: ").append(r.getTask().trim());
                }
                String location = buildLocation(r);
                if (notBlank(location)) {
                    data.append(" | Location: ").append(location);
                }
                if (notBlank(r.getImplication())) {
                    data.append(" | Implication: ").append(r.getImplication().trim());
                }
                if (notBlank(r.getTime())) {
                    data.append(" | Recommended timeframe: ").append(r.getTime().trim());
                }
                String cost = buildCost(r);
                if (notBlank(cost)) {
                    data.append(" | Estimated cost: ").append(cost);
                }
                if (notBlank(field.getNote())) {
                    data.append(" | Note: ").append(field.getNote().trim());
                }
                data.append("\n");
            }
        }

        return """
                You are a professional home inspector writing the summary section of a home inspection report.
                Write a concise summary that outlines potentially significant issues from a cost or safety standpoint.

                Start with this exact disclaimer paragraph:
                "This Summary outlines potentially significant issues from a cost or safety standpoint. This section is provided as a courtesy and cannot be considered a substitute for reading the entire report. Please read the complete document."

                Then write one short sentence describing the overall condition of the house.

                Then produce a numbered list, one item per component listed below, in the same order.
                For each component:
                  - Write the component name in UPPERCASE (e.g. "The STRUCTURE condition is good.").
                  - If issues are listed for that component, describe each of them in plain, professional sentences.
                  - If no issues are listed, simply state that the component is in good condition.

                Be factual: only describe issues that appear in the data. Do not invent problems that are not listed.
                Do not use markdown or bullet characters; output plain text only.

                Here is the inspection data grouped by component:
                """ + data;
    }

    private String buildLocation(InspectionRecommendationField r) {
        String base = r.getLocationDisplay();
        boolean hasRoom = notBlank(r.getRoom());
        if (notBlank(base) && hasRoom) {
            return base + ", " + r.getRoom().trim();
        }
        if (notBlank(base)) {
            return base;
        }
        if (hasRoom) {
            return r.getRoom().trim();
        }
        return null;
    }

    private String buildCost(InspectionRecommendationField r) {
        boolean hasLower = notBlank(r.getLower_cost());
        boolean hasUpper = notBlank(r.getUpper_cost());
        if (hasLower && hasUpper) {
            return "$" + r.getLower_cost().trim() + " - $" + r.getUpper_cost().trim();
        }
        if (hasLower) {
            return "from $" + r.getLower_cost().trim();
        }
        if (hasUpper) {
            return "up to $" + r.getUpper_cost().trim();
        }
        return null;
    }

    private boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
