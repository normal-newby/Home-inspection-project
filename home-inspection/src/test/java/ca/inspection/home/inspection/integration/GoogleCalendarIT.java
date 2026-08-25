package ca.inspection.home.inspection.integration;

import ca.inspection.home.inspection.entity.InspectorProfile;
import ca.inspection.home.inspection.repository.InspectorProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Tag("integration")
public class GoogleCalendarIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private InspectorProfileRepository inspectorProfileRepository;

    @BeforeEach
    void resetState() {
        inspectorProfileRepository.deleteAll();
        InspectorProfile profile = new InspectorProfile();
        profile.setId(1L);
        profile.setInspectionNumber(0);
        profile.setName("Ada Inspector");
        inspectorProfileRepository.save(profile);
    }

    @Test
    void status_withoutCredentials_reportsNotSetUp() throws Exception {
        mockMvc.perform(get("/api/google/calendar/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configured").value(false))
                .andExpect(jsonPath("$.connected").value(false))
                .andExpect(jsonPath("$.calendarId").value("primary"))
                .andExpect(jsonPath("$.calendars").isArray());
    }

    @Test
    void connect_withoutCredentials_redirectsBackWithTheReason() throws Exception {
        mockMvc.perform(get("/api/google/calendar/connect"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location",
                        org.hamcrest.Matchers.startsWith("/html/profile.html?calendar=error")));
    }

    @Test
    void callback_whenConsentDeclined_redirectsBackWithTheReason() throws Exception {
        mockMvc.perform(get("/api/google/calendar/callback").param("error", "access_denied"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location",
                        org.hamcrest.Matchers.startsWith("/html/profile.html?calendar=error")));
    }

    @Test
    void settings_persistCalendarChoiceAndSwitch() throws Exception {
        mockMvc.perform(post("/api/google/calendar/settings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"calendarId\":\"work@group.calendar.google.com\",\"enabled\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.calendarId").value("work@group.calendar.google.com"));

        InspectorProfile saved = inspectorProfileRepository.findById(1L).orElseThrow();
        assertThat(saved.getGoogleCalendarId()).isEqualTo("work@group.calendar.google.com");
        assertThat(saved.getGoogleCalendarEnabled()).isTrue();
    }

    @Test
    void profileJson_neverExposesTheRefreshToken() throws Exception {
        InspectorProfile profile = inspectorProfileRepository.findById(1L).orElseThrow();
        profile.setGoogleRefreshToken("secret-refresh-token");
        profile.setGoogleAccountEmail("inspector@example.com");
        inspectorProfileRepository.save(profile);

        String body = mockMvc.perform(get("/api/profile"))
                .andExpect(status().isOk())
                // The linked address is shown on the page; the token backing it is not.
                .andExpect(jsonPath("$.googleAccountEmail").value("inspector@example.com"))
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("secret-refresh-token");
        assertThat(body).doesNotContain("googleRefreshToken");
    }

    @Test
    void savingTheProfileForm_keepsTheGoogleGrant() throws Exception {
        InspectorProfile profile = inspectorProfileRepository.findById(1L).orElseThrow();
        profile.setGoogleRefreshToken("secret-refresh-token");
        profile.setGoogleCalendarId("work@group.calendar.google.com");
        profile.setGoogleCalendarEnabled(true);
        profile.setAppendixPdf("appendix.pdf");
        inspectorProfileRepository.save(profile);

        String formPayload = objectMapper.writeValueAsString(Map.of(
                "name", "Ada Inspector",
                "company", "Lovelace Inspections",
                "inspectionNumber", 12
        ));

        mockMvc.perform(post("/api/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(formPayload))
                .andExpect(status().isOk());

        InspectorProfile after = inspectorProfileRepository.findById(1L).orElseThrow();
        assertThat(after.getCompany()).isEqualTo("Lovelace Inspections");
        assertThat(after.getGoogleRefreshToken()).isEqualTo("secret-refresh-token");
        assertThat(after.getGoogleCalendarId()).isEqualTo("work@group.calendar.google.com");
        assertThat(after.getGoogleCalendarEnabled()).isTrue();
        assertThat(after.getAppendixPdf()).isEqualTo("appendix.pdf");
    }
}
