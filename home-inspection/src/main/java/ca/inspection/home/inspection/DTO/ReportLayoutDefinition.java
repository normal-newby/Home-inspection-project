package ca.inspection.home.inspection.DTO;

import java.util.UUID;

public record ReportLayoutDefinition(UUID id, String fieldName, Integer reportOrder) {
}
