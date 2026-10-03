package ca.inspection.home.inspection.DTO;

import java.util.List;
import java.util.UUID;

public record FieldSearchEntry(UUID id, String fieldName, String place, String type, List<String> values) {
}
