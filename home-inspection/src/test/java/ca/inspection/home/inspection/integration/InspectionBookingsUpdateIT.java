package ca.inspection.home.inspection.integration;

import ca.inspection.home.inspection.entity.InspectionBookings;
import ca.inspection.home.inspection.entity.InspectionImage;
import ca.inspection.home.inspection.entity.InspectionReport;
import ca.inspection.home.inspection.entity.InspectorProfile;
import ca.inspection.home.inspection.entity.Invoice;
import ca.inspection.home.inspection.repository.InspectionBookingsRepository;
import ca.inspection.home.inspection.repository.InspectionImagesRepository;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// Updating a booking shouldn't edit other stuff
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Tag("integration")
public class InspectionBookingsUpdateIT {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private InspectionBookingsRepository bookingsRepository;
    @Autowired private InspectionReportsRepository reportsRepository;
    @Autowired private InspectorProfileRepository inspectorProfileRepository;
    @Autowired private InvoiceRepository invoiceRepository;
    @Autowired private InspectionImagesRepository imagesRepository;

    @BeforeEach
    void resetState() {
        reportsRepository.deleteAll();
        invoiceRepository.deleteAll();
        bookingsRepository.deleteAll();
        inspectorProfileRepository.deleteAll();

        InspectorProfile profile = new InspectorProfile();
        profile.setId(1L);
        profile.setInspectionNumber(0);
        profile.setSummaryLetterBody("Default summary body");
        inspectorProfileRepository.save(profile);
    }

    private UUID createBooking(String address) throws Exception {
        InspectionBookings payload = new InspectionBookings();
        payload.setInspectionAddress(address);
        MvcResult res = mockMvc.perform(post("/api/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andReturn();
        return UUID.fromString(objectMapper.readTree(res.getResponse().getContentAsString())
                .get("id").asString());
    }

    @Test
    void editingABooking_keepsItsReport() throws Exception {
        UUID id = createBooking("1 Report Keeper Rd");
        UUID reportId = reportsRepository.findByInspectionBooking_IdLite(id).getId();

        InspectionBookings edit = new InspectionBookings();
        edit.setInspectionAddress("1 Report Keeper Rd");
        edit.setCity("Toronto");

        mockMvc.perform(put("/api/bookings/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(edit)))
                .andExpect(status().isOk());

        assertThat(reportsRepository.findById(reportId)).isPresent();
    }

    @Test
    void editingABooking_keepsInvoicesItDidNotMention() throws Exception {
        InspectionBookings payload = new InspectionBookings();
        payload.setInspectionAddress("7 Invoice Way");
        Invoice inv = new Invoice();
        inv.setType("Inspection");
        inv.setFee(new BigDecimal("450.00"));
        payload.setInvoices(List.of(inv));

        MvcResult res = mockMvc.perform(post("/api/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andReturn();
        UUID id = UUID.fromString(objectMapper.readTree(res.getResponse().getContentAsString())
                .get("id").asString());
        assertThat(invoiceRepository.findAll()).hasSize(1);

        InspectionBookings edit = new InspectionBookings();
        edit.setInspectionAddress("7 Invoice Way");
        edit.setCity("Ottawa");
        mockMvc.perform(put("/api/bookings/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(edit)))
                .andExpect(status().isOk());

        assertThat(invoiceRepository.findAll()).hasSize(1);
    }

    @Test
    void editingABooking_withAnEmptyInvoiceList_clearsThem() throws Exception {
        InspectionBookings payload = new InspectionBookings();
        payload.setInspectionAddress("8 Cleared Ct");
        Invoice inv = new Invoice();
        inv.setType("Inspection");
        inv.setFee(new BigDecimal("450.00"));
        payload.setInvoices(List.of(inv));

        MvcResult res = mockMvc.perform(post("/api/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andReturn();
        UUID id = UUID.fromString(objectMapper.readTree(res.getResponse().getContentAsString())
                .get("id").asString());

        // An empty list is the form saying the inspector removed them, unlike no list at all.
        InspectionBookings edit = new InspectionBookings();
        edit.setInspectionAddress("8 Cleared Ct");
        edit.setInvoices(List.of());
        mockMvc.perform(put("/api/bookings/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(edit)))
                .andExpect(status().isOk());

        assertThat(invoiceRepository.findAll()).isEmpty();
    }

    @Test
    void editingABooking_ignoresAClientSuppliedInspectionNumber() throws Exception {
        UUID id = createBooking("3 Renumber Rd");
        Integer assigned = bookingsRepository.findById(id).orElseThrow().getInspectionNumber();

        InspectionBookings edit = new InspectionBookings();
        edit.setInspectionAddress("3 Renumber Rd");
        edit.setInspectionNumber(assigned + 500);

        mockMvc.perform(put("/api/bookings/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(edit)))
                .andExpect(status().isOk());

        assertThat(bookingsRepository.findById(id).orElseThrow().getInspectionNumber())
                .isEqualTo(assigned);
    }

    @Test
    void putToAnUnknownId_doesNotCreateABooking() throws Exception {
        UUID ghost = UUID.randomUUID();
        InspectionBookings edit = new InspectionBookings();
        edit.setInspectionAddress("Ghost Town");

        mockMvc.perform(put("/api/bookings/{id}", ghost)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(edit)));

        assertThat(bookingsRepository.findById(ghost)).isEmpty();
    }

    @Test
    void impossibleDateOnUpdate_isRejected() throws Exception {
        UUID id = createBooking("30 Feb Ave");
        InspectionBookings edit = new InspectionBookings();
        edit.setMonth("February");
        edit.setDay(30);
        edit.setYear(2026);

        mockMvc.perform(put("/api/bookings/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(edit)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deletingABooking_leavesAnotherBookingsPhotosAlone() throws Exception {
        UUID keeper = createBooking("Keeper House");
        UUID collider = createBooking("Collider House");

        mockMvc.perform(multipart("/api/images/{id}/upload", keeper)
                        .file(new MockMultipartFile("file", "a.jpg", "image/jpeg", tinyJpeg())))
                .andExpect(status().isOk());

        List<InspectionImage> keeperImages = imagesRepository.findByBookingIdOrdered(keeper);
        assertThat(keeperImages).hasSize(1);
        Integer keeperNumber = bookingsRepository.findById(keeper).orElseThrow().getInspectionNumber();
        Path keeperFile = Path.of("target/integration-test-uploads",
                "booking_" + keeperNumber, keeperImages.get(0).getImageUrl());
        assertThat(Files.exists(keeperFile)).isTrue();

        // Photos are stored under the inspection number, so a collision makes one delete
        // take out both bookings' folders.
        InspectionBookings edit = new InspectionBookings();
        edit.setInspectionAddress("Collider House");
        edit.setInspectionNumber(keeperNumber);
        mockMvc.perform(put("/api/bookings/{id}", collider)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(edit)));

        mockMvc.perform(delete("/api/bookings/{id}", collider));

        assertThat(Files.exists(keeperFile)).isTrue();
    }

    @Test
    void deletingTheCoverPhoto_leavesTheReportReadable() throws Exception {
        UUID id = createBooking("Cover Story");
        mockMvc.perform(multipart("/api/images/{id}/cover-page-image", id)
                        .file(new MockMultipartFile("file", "cover.jpg", "image/jpeg", tinyJpeg())))
                .andExpect(status().isOk());

        InspectionReport report = reportsRepository.findByInspectionBooking_Id(id);
        assertThat(report.getCoverPageImage()).isNotNull();

        mockMvc.perform(delete("/api/images/{id}", report.getCoverPageImage().getId()));

        mockMvc.perform(get("/api/images/{id}", id)).andExpect(status().isOk());
        assertThat(reportsRepository.findByInspectionBooking_Id(id)).isNotNull();
    }

    private static byte[] tinyJpeg() throws Exception {
        java.awt.image.BufferedImage img =
                new java.awt.image.BufferedImage(80, 60, java.awt.image.BufferedImage.TYPE_INT_RGB);
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(img, "jpeg", out);
        return out.toByteArray();
    }
}
