package ca.inspection.home.inspection.integration;

import ca.inspection.home.inspection.entity.Client;
import ca.inspection.home.inspection.entity.InspectionBookings;
import ca.inspection.home.inspection.entity.InspectorProfile;
import ca.inspection.home.inspection.repository.InspectionBookingsRepository;
import ca.inspection.home.inspection.repository.InspectionReportsRepository;
import ca.inspection.home.inspection.repository.InspectorProfileRepository;
import ca.inspection.home.inspection.repository.InvoiceRepository;
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

import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Tag("integration")
public class ReportEmailIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private InspectionBookingsRepository bookingsRepository;

    @Autowired
    private InspectionReportsRepository reportsRepository;

    @Autowired
    private InspectorProfileRepository inspectorProfileRepository;

    @Autowired
    private InvoiceRepository invoiceRepository;

    @BeforeEach
    void resetState() {
        reportsRepository.deleteAll();
        invoiceRepository.deleteAll();
        bookingsRepository.deleteAll();
        inspectorProfileRepository.deleteAll();

        InspectorProfile profile = new InspectorProfile();
        profile.setId(1L);
        profile.setInspectionNumber(0);
        profile.setEmail("inspector@example.com");
        inspectorProfileRepository.save(profile);
    }

    private UUID createBooking(String clientEmail) throws Exception {
        InspectionBookings payload = new InspectionBookings();
        payload.setInspectionAddress("12 Mail St");
        Client ada = new Client();
        ada.setFirstName("Ada");
        ada.setLastName("Lovelace");
        ada.setEmail(clientEmail);
        payload.setClients(List.of(ada));

        String body = mockMvc.perform(post("/api/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(body, InspectionBookings.class).getId();
    }

    @Test
    void recipients_listClientsAndInspectorCc_andReportGoogleNotLinked() throws Exception {
        UUID bookingId = createBooking("ada@example.com");

        mockMvc.perform(get("/report/{id}/email", bookingId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.linked").value(false))
                .andExpect(jsonPath("$.to[0]").value("ada@example.com"))
                .andExpect(jsonPath("$.cc").value("inspector@example.com"));
    }

    @Test
    void recipients_withNoClientEmail_isAnEmptyList() throws Exception {
        UUID bookingId = createBooking(null);

        mockMvc.perform(get("/report/{id}/email", bookingId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.to").isEmpty())
                .andExpect(jsonPath("$.cc").doesNotExist());
    }

    @Test
    void send_withoutGoogleLinked_isRefusedWithTheReason() throws Exception {
        UUID bookingId = createBooking("ada@example.com");

        mockMvc.perform(post("/report/{id}/email", bookingId))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("Google is not linked")));
    }
}
