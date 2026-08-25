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

    @Autowired
    private ca.inspection.home.inspection.service.InspectionBookingsService bookingsService;

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
    void createBooking_impossibleDate_isRejectedWithAReadableMessage() throws Exception {
        InspectionBookings payload = new InspectionBookings();
        payload.setInspectionAddress("30 Feb Ave");
        payload.setMonth("February");
        payload.setDay(30);
        payload.setYear(2026);

        mockMvc.perform(post("/api/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("not a valid date")));

        // Nothing was written, and the inspection counter wasn't burned either.
        assertThat(bookingsRepository.findAll()).isEmpty();
        assertThat(inspectorProfileRepository.findById(1L).orElseThrow().getInspectionNumber()).isZero();
    }

    @Test
    void createBooking_halfEnteredDate_isRejected() throws Exception {
        InspectionBookings payload = new InspectionBookings();
        payload.setMonth("March");
        payload.setYear(2026);

        mockMvc.perform(post("/api/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createBooking_dateAndTime_roundTripThroughTheApi() throws Exception {
        InspectionBookings payload = new InspectionBookings();
        payload.setInspectionAddress("12 Timely Cres");
        payload.setMonth("March");
        payload.setDay(12);
        payload.setYear(2026);
        payload.setStartTime("09:30");
        payload.setDurationMinutes(150);

        MvcResult created = mockMvc.perform(post("/api/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andReturn();
        UUID id = UUID.fromString(objectMapper.readTree(created.getResponse().getContentAsString())
                .get("id").asString());

        // The booking form reads these back off the projection when reopening a booking.
        mockMvc.perform(get("/api/bookings/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.startTime").value("09:30"))
                .andExpect(jsonPath("$.durationMinutes").value(150));
    }

    @Test
    void updateBooking_editFormPayload_keepsTheInspectionNumber() throws Exception {
        InspectionBookings payload = new InspectionBookings();
        payload.setInspectionAddress("9 Renumber Rd");
        MvcResult created = mockMvc.perform(post("/api/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andReturn();
        UUID id = UUID.fromString(objectMapper.readTree(created.getResponse().getContentAsString())
                .get("id").asString());
        Integer assignedNumber = bookingsRepository.findById(id).orElseThrow().getInspectionNumber();
        assertThat(assignedNumber).isEqualTo(1);

        // The edit form posts only the fields it renders — no inspection number.
        InspectionBookings edit = new InspectionBookings();
        edit.setInspectionAddress("9 Renumber Rd");
        edit.setCity("Toronto");

        mockMvc.perform(put("/api/bookings/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(edit)))
                .andExpect(status().isOk());

        InspectionBookings after = bookingsRepository.findById(id).orElseThrow();
        assertThat(after.getCity()).isEqualTo("Toronto");
        assertThat(after.getInspectionNumber()).isEqualTo(assignedNumber);
    }

    @Test
    void removeTax_roundTripsAndZeroesTheTaxLines() throws Exception {
        InspectionBookings payload = new InspectionBookings();
        payload.setInspectionAddress("5 Tax Free Way");
        payload.setRemoveTax(true);

        Invoice inspection = new Invoice();
        inspection.setType("Inspection");
        inspection.setFee(new BigDecimal("500.00"));
        payload.setInvoices(List.of(inspection));

        MvcResult created = mockMvc.perform(post("/api/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andReturn();
        UUID id = UUID.fromString(objectMapper.readTree(created.getResponse().getContentAsString())
                .get("id").asString());

        // The booking form reads the flag back off the projection when reopening.
        mockMvc.perform(get("/api/bookings/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.removeTax").value(true));

        InspectionBookings saved = bookingsRepository.findById(id).orElseThrow();
        assertThat(saved.getRemoveTax()).isTrue();

        // What the invoice page prints: subtotal only, no tax.
        var amount = bookingsService.buildInvoiceAmount(invoiceRepository.findAll(), true);
        assertThat(amount.getHst()).isEqualByComparingTo("0.00");
        assertThat(amount.getGst()).isEqualByComparingTo("0.00");
        assertThat(amount.getTotal()).isEqualByComparingTo("500.00");
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
