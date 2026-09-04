package ca.inspection.home.inspection.DTO;

import ca.inspection.home.inspection.entity.InspectionField;
import ca.inspection.home.inspection.entity.InspectionFieldDefinition;

import java.util.List;

public record FieldGroup(InspectionFieldDefinition definition, List<InspectionField> fields) {

    public boolean isGrouped() {
        return fields.size() > 1;
    }

    public boolean isDetailed() {
        if (isGrouped()) return true;

        InspectionField only = fields.get(0);
        return only.getInspectionRecommendationField() != null
                || (only.getNote() != null && !only.getNote().isBlank())
                || (only.getInspectionImages() != null && !only.getInspectionImages().isEmpty());
    }

    // Definitions are compared by id, falling back to identity for anything not yet persisted.
    public boolean matches(InspectionFieldDefinition other) {
        if (definition == null || other == null) return false;
        if (definition.getId() == null || other.getId() == null) return definition == other;
        return definition.getId().equals(other.getId());
    }
}
