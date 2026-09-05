package ca.inspection.home.inspection.DTO;

import java.util.List;
import java.util.UUID;

public record AttachedDiagrams(UUID valueId, String fieldName, String valueLabel, List<UUID> diagramIds) {
}
