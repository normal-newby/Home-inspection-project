package ca.inspection.home.inspection.DTO;

import java.util.List;

public record ReportLayoutPlace(String place, List<ReportLayoutType> types) {
}
