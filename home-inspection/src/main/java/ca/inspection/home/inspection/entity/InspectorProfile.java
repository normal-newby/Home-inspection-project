package ca.inspection.home.inspection.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.stereotype.Component;

@Entity
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "inspector_profile")
public class InspectorProfile {

    @Id
    private Long id = 1L;

    private String name;
    private String company;
    private String phone;
    private String email;
    private String website;
    private String logoPath;

    @Column(columnDefinition = "TEXT")
    private String coverLetterBody;

    @Column(columnDefinition = "TEXT")
    private String summaryLetterBody;

    @Column(columnDefinition = "TEXT")
    private String agreementBody;
}
