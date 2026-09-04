package ca.inspection.home.inspection.DTO;

import ca.inspection.home.inspection.entity.InspectionField;
import ca.inspection.home.inspection.entity.InspectionFieldDefinition;

import java.util.List;
import java.util.stream.Collectors;

public record FieldGroup(InspectionFieldDefinition definition, List<InspectionField> fields) {

    public boolean isGrouped() {
        return fields.size() > 1;
    }

    public boolean isRecommendations() {
        return definition != null && "recommendations".equals(definition.getFieldType());
    }

    // Recs cant be merged together
    public String getDisplayValues() {
        if (isRecommendations()) return null;

        String joined = fields.stream()
                .map(InspectionField::getDisplayValue)
                .filter(value -> value != null && !value.isBlank())
                .collect(Collectors.joining(", "));

        return joined.isEmpty() ? null : joined;
    }

    public boolean isDetailed() {
        if (isGrouped() || getDisplayValues() != null) return true;

        return fields.get(0).isDetailed();
    }

    // Definitions are compared by id, falling back to identity for anything not yet persisted.
    public boolean matches(InspectionFieldDefinition other) {
        if (definition == null || other == null) return false;
        if (definition.getId() == null || other.getId() == null) return definition == other;
        return definition.getId().equals(other.getId());
    }
}
