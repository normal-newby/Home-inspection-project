package ca.inspection.home.inspection.integration;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Tag("integration")
public class ErrorHandlingIT {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void apiRequest_missingBooking_returns404WithStandardJsonErrorBody() throws Exception {
        UUID nonexistent = UUID.randomUUID();

        mockMvc.perform(get("/api/bookings/{id}", nonexistent)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not found"))
                .andExpect(jsonPath("$.message").value("Booking not found: " + nonexistent))
                .andExpect(jsonPath("$.path").value("/api/bookings/" + nonexistent))
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }
}
