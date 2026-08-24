package ca.inspection.home.inspection.service;

import ca.inspection.home.inspection.DTO.DefinitionValueDTO;
import ca.inspection.home.inspection.entity.InspectionRecommendationFieldDefinition;
import ca.inspection.home.inspection.repository.InspectionRecommendationFieldValueRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Slf4j
public class InspectionRecommendationFieldDefinitionService {
    @Autowired
    private InspectionRecommendationFieldValueRepository repository;

    public Map<String, List<DefinitionValueDTO>> getAllDefinitions(){
        return repository.findAll().stream()
                .collect(Collectors.groupingBy(InspectionRecommendationFieldDefinition::getType,
                        Collectors.mapping(d ->
                                new DefinitionValueDTO(d.getId(), d.getValue()),
                                Collectors.toList())
                ));
    }

    public ResponseEntity<?> addDefinition(InspectionRecommendationFieldDefinition definition){
        try {
            InspectionRecommendationFieldDefinition saved =  repository.save(definition);
            DefinitionValueDTO dto = new DefinitionValueDTO(saved.getId(), saved.getValue());
            return ResponseEntity.ok(dto);
        } catch (Exception e){
            log.error("Failed to add recommendation definition type={} value={}",
                    definition == null ? null : definition.getType(),
                    definition == null ? null : definition.getValue(), e);
            return ResponseEntity.badRequest().build();
        }
    }

    public ResponseEntity<?> deleteDefinition(UUID id){
        try {
            InspectionRecommendationFieldDefinition definition = repository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Cannot find"));
            repository.delete(definition);

            return ResponseEntity.ok("Deleted recommendation definition");
        } catch (Exception e){
            log.error("Failed to delete recommendation definition {}", id, e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        }
    }
}
