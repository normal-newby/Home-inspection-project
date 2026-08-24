package ca.inspection.home.inspection.integration;

import ca.inspection.home.inspection.entity.InspectionBookings;
import ca.inspection.home.inspection.entity.InspectorProfile;
import ca.inspection.home.inspection.entity.Invoice;
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
import tools.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// Boots the full Spring context on a random port, wires up the real controller
// → service → repository → SQLite chain. Only external I/O we don't want to
// touch is the PDF microservice, and we don't hit those endpoints here.
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Tag("integration")
public class InspectionBookingsFlowIT {

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

        // createBooking() calls getAndUpdateNumber() which requires row id=1.
        InspectorProfile profile = new InspectorProfile();
        profile.setId(1L);
        profile.setInspectionNumber(0);
        profile.setSummaryLetterBody("Default summary body");
        inspectorProfileRepository.save(profile);
    }

    @Test
    void createBooking_persistsBookingAndLinkedReport() throws Exception {
        InspectionBookings payload = new InspectionBookings();
        payload.setInspectionAddress("500 Test Ave");
        payload.setClientFirstName("Ada");
        payload.setClientLastName("Lovelace");

        MvcResult postResult = mockMvc.perform(post("/api/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.inspectionAddress").value("500 Test Ave"))
                .andExpect(jsonPath("$.inspectionNumber").value(1))
                .andReturn();

        InspectionBookings returned = objectMapper.readValue(
                postResult.getResponse().getContentAsString(), InspectionBookings.class);
        UUID bookingId = returned.getId();
        assertThat(bookingId).isNotNull();

        // Booking really landed in the DB…
        InspectionBookings persisted = bookingsRepository.findById(bookingId).orElseThrow();
        assertThat(persisted.getClientLastName()).isEqualTo("Lovelace");

        // …and its report was created with the inspector's summary letter body.
        var report = reportsRepository.findByInspectionBooking_IdLite(bookingId);
        assertThat(report).isNotNull();
        assertThat(report.getSummary()).isEqualTo("Default summary body");
    }

    @Test
    void getBooking_afterCreate_returnsSameBooking() throws Exception {
        InspectionBookings payload = new InspectionBookings();
        payload.setInspectionAddress("1 Round Trip Rd");
        MvcResult created = mockMvc.perform(post("/api/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andReturn();
        UUID id = UUID.fromString(objectMapper.readTree(created.getResponse().getContentAsString())
                .get("id").asString());

        mockMvc.perform(get("/api/bookings/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.inspectionAddress").value("1 Round Trip Rd"));
    }

    @Test
    void createBooking_withInvoices_persistsInvoicesLinkedToBooking() throws Exception {
        InspectionBookings payload = new InspectionBookings();
        payload.setInspectionAddress("77 Invoice Ln");

        Invoice inspection = new Invoice();
        inspection.setType("Inspection");
        inspection.setFee(new BigDecimal("450.00"));

        Invoice radon = new Invoice();
        radon.setType("Radon Test");
        radon.setFee(new BigDecimal("150.00"));

        payload.setInvoices(List.of(inspection, radon));

        MvcResult postResult = mockMvc.perform(post("/api/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andReturn();

        InspectionBookings returned = objectMapper.readValue(
                postResult.getResponse().getContentAsString(), InspectionBookings.class);
        UUID bookingId = returned.getId();

        // Query invoices directly — the booking's own collection is lazy and
        // would need an open transaction to walk. The point is that the rows
        // exist AND each one has booking_id set (the bug: it was NULL).
        List<Invoice> persistedInvoices = invoiceRepository.findAll();
        assertThat(persistedInvoices)
                .hasSize(2)
                .allSatisfy(inv -> assertThat(inv.getBookings().getId()).isEqualTo(bookingId));
        assertThat(persistedInvoices.stream().map(Invoice::getType))
                .containsExactlyInAnyOrder("Inspection", "Radon Test");
    }

    @Test
    void deleteBooking_removesFromRepository() throws Exception {
        InspectionBookings payload = new InspectionBookings();
        payload.setInspectionAddress("Delete Me Ln");
        MvcResult created = mockMvc.perform(post("/api/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andReturn();
        InspectionBookings returned = objectMapper.readValue(
                created.getResponse().getContentAsString(), InspectionBookings.class);
        UUID id = returned.getId();
        assertThat(bookingsRepository.findById(id)).isPresent();

        mockMvc.perform(delete("/api/bookings/{id}", id))
                .andExpect(status().isOk());

        assertThat(bookingsRepository.findById(id)).isEmpty();
    }
}
