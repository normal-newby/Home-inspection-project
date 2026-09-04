package ca.inspection.home.inspection.service;

import ca.inspection.home.inspection.DTO.ReportLayoutDefinition;
import ca.inspection.home.inspection.DTO.ReportLayoutPlace;
import ca.inspection.home.inspection.DTO.ReportLayoutType;
import ca.inspection.home.inspection.entity.InspectionFieldDefinition;
import ca.inspection.home.inspection.entity.InspectionReport;
import ca.inspection.home.inspection.repository.InspectionFieldDefinitionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Slf4j
public class InspectionFieldDefinitionService {
    @Autowired
    private InspectionFieldDefinitionRepository inspectionFieldDefinitionRepository;

    public List<InspectionFieldDefinition> getAllFields(String place, String type){
        try {
            place = place.toLowerCase();
            type = type.toLowerCase();

            return inspectionFieldDefinitionRepository.findAllWithValues(place, type);
        } catch (Exception e){
            log.error("Failed to load field definitions for place={} type={}", place, type, e);
            return null;
        }
    }

    public ResponseEntity<?> setExpandedByDefault(UUID id, Boolean value){
        try {
            InspectionFieldDefinition definition = inspectionFieldDefinitionRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("definition not found"));
            definition.setExpandedByDefault(value);
            inspectionFieldDefinitionRepository.save(definition);
            return ResponseEntity.ok().build();
        } catch (Exception e){
            log.error("Failed to set expandedByDefault on definition {}", id, e);
            return ResponseEntity.badRequest().build();
        }
    }

    public InspectionFieldDefinition getFieldWithValues(UUID id){
        try {
            return inspectionFieldDefinitionRepository.findWithValues(id);
        } catch (Exception e){
            log.error("Failed to load field definition with values {}", id, e);
            return null;
        }
    }

    public List<ReportLayoutPlace> getReportLayout(){
        Map<String, Map<String, List<InspectionFieldDefinition>>> byPlace = new TreeMap<>(
                ReportSectionOrder.placeComparator());

        for (InspectionFieldDefinition definition : inspectionFieldDefinitionRepository.findAll()){
            if (definition.getFieldPlace() == null || definition.getFieldType() == null) continue;

            byPlace.computeIfAbsent(definition.getFieldPlace(),
                            place -> new TreeMap<>(ReportSectionOrder.typeComparator()))
                    .computeIfAbsent(definition.getFieldType(), type -> new ArrayList<>())
                    .add(definition);
        }

        List<ReportLayoutPlace> layout = new ArrayList<>();
        byPlace.forEach((place, byType) -> {
            List<ReportLayoutType> types = new ArrayList<>();
            byType.forEach((type, definitions) -> types.add(
                    new ReportLayoutType(type, toLayoutRows(definitions))));
            layout.add(new ReportLayoutPlace(place, types));
        });

        return layout;
    }

    // Same tie break as the report itself, so the page shows the printed order.
    private List<ReportLayoutDefinition> toLayoutRows(List<InspectionFieldDefinition> definitions){
        return definitions.stream()
                .sorted(Comparator
                        .comparingInt((InspectionFieldDefinition d) ->
                                d.getReportOrder() == null ? Integer.MAX_VALUE : d.getReportOrder())
                        .thenComparing(InspectionFieldDefinition::getFieldName,
                                Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                .map(d -> new ReportLayoutDefinition(d.getId(), d.getFieldName(), d.getReportOrder()))
                .collect(Collectors.toList());
    }

    public ResponseEntity<?> saveReportOrder(String place, String type, List<UUID> orderedIds){
        try {
            if (orderedIds == null) return ResponseEntity.badRequest().build();

            List<InspectionFieldDefinition> definitions = inspectionFieldDefinitionRepository
                    .findByFieldPlaceAndFieldType(place.toLowerCase(), type.toLowerCase());

            Map<UUID, InspectionFieldDefinition> byId = definitions.stream()
                    .collect(Collectors.toMap(InspectionFieldDefinition::getId, d -> d,
                            (a, b) -> a, LinkedHashMap::new));

            if (!byId.keySet().containsAll(orderedIds) || new HashSet<>(orderedIds).size() != orderedIds.size()){
                log.warn("Report order for place={} type={} named definitions outside that section", place, type);
                return ResponseEntity.badRequest().build();
            }

            int position = 0;
            for (UUID id : orderedIds){
                byId.remove(id).setReportOrder(position++);
            }

            for (InspectionFieldDefinition leftover : byId.values()){
                leftover.setReportOrder(position++);
            }

            inspectionFieldDefinitionRepository.saveAll(definitions);
            return ResponseEntity.ok().build();
        } catch (Exception e){
            log.error("Failed to save report order for place={} type={}", place, type, e);
            return ResponseEntity.badRequest().build();
        }
    }
}
