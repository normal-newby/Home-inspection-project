package ca.inspection.home.inspection.DTO;

import java.util.List;

public record ReportLayoutType(String type, List<ReportLayoutDefinition> definitions) {
}
