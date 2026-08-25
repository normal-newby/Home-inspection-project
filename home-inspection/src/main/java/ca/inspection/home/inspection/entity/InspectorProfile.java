package ca.inspection.home.inspection.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.stereotype.Component;

import java.util.List;

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
    private Integer inspectionNumber;

    private String address;
    private String city;
    private String province;
    private String postalCode;

    @Column(columnDefinition = "TEXT")
    private String coverLetterBody;

    @Column(columnDefinition = "TEXT")
    private String summaryLetterBody;

    @Column(columnDefinition = "TEXT")
    private String agreementBody;

    private String appendixPdf;

    // --- Google Calendar link ---
    // Long lived grant from the OAuth consent flow.
    @JsonIgnore
    @Column(columnDefinition = "TEXT")
    private String googleRefreshToken;

    @JsonIgnore
    @Column(columnDefinition = "TEXT")
    private String googleAccessToken;

    // Epoch seconds at which googleAccessToken stops working.
    @JsonIgnore
    private Long googleTokenExpiry;

    // Which Google account is linked, shown on the profile page.
    private String googleAccountEmail;

    // Target calendar; "primary" unless the inspector picks another one.
    private String googleCalendarId;

    // Master switch: bookings only reach Google while this is true.
    private Boolean googleCalendarEnabled;
}
