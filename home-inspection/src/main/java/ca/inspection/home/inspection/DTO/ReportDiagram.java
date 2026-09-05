package ca.inspection.home.inspection.DTO;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class ReportDiagram {
    private final String title;
    private final String base64;
    private Integer figureNumber;
}
