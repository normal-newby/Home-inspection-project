package ca.inspection.home.inspection.integration;

import ca.inspection.home.inspection.entity.InspectionBookings;
import ca.inspection.home.inspection.entity.InspectorProfile;
import ca.inspection.home.inspection.repository.InspectionBookingsRepository;
import ca.inspection.home.inspection.repository.InspectionImagesRepository;
import ca.inspection.home.inspection.repository.InspectionReportsRepository;
import ca.inspection.home.inspection.repository.InspectorProfileRepository;
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

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Tag("integration")
public class ImageUploadValidationIT {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private InspectionBookingsRepository bookingsRepository;
    @Autowired private InspectionReportsRepository reportsRepository;
    @Autowired private InspectionImagesRepository imagesRepository;
    @Autowired private InspectorProfileRepository inspectorProfileRepository;

    private UUID bookingId;

    @BeforeEach
    void resetState() throws Exception {
        imagesRepository.deleteAll();
        reportsRepository.deleteAll();
        bookingsRepository.deleteAll();
        inspectorProfileRepository.deleteAll();

        InspectorProfile profile = new InspectorProfile();
        profile.setId(1L);
        profile.setInspectionNumber(0);
        inspectorProfileRepository.save(profile);

        InspectionBookings payload = new InspectionBookings();
        payload.setInspectionAddress("1 Upload Ln");
        MvcResult res = mockMvc.perform(post("/api/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andReturn();
        bookingId = UUID.fromString(objectMapper.readTree(res.getResponse().getContentAsString())
                .get("id").asString());
    }

    @Test
    void aFileThatIsNotAnImage_isRejected() throws Exception {
        mockMvc.perform(multipart("/api/images/{id}/upload", bookingId)
                        .file(new MockMultipartFile("file", "evil.exe",
                                "application/octet-stream", "MZ not an image".getBytes())))
                .andExpect(status().is4xxClientError());

        assertThat(imagesRepository.findByBookingIdOrdered(bookingId)).isEmpty();
    }

    @Test
    void anEmptyFile_isRejected() throws Exception {
        mockMvc.perform(multipart("/api/images/{id}/upload", bookingId)
                        .file(new MockMultipartFile("file", "empty.jpg", "image/jpeg", new byte[0])))
                .andExpect(status().is4xxClientError());

        assertThat(imagesRepository.findByBookingIdOrdered(bookingId)).isEmpty();
    }

    @Test
    void aTruncatedJpeg_isRejected() throws Exception {
        byte[] full = tinyJpeg();
        mockMvc.perform(multipart("/api/images/{id}/upload", bookingId)
                        .file(new MockMultipartFile("file", "half.jpg", "image/jpeg",
                                java.util.Arrays.copyOf(full, full.length / 2))))
                .andExpect(status().is4xxClientError());

        assertThat(imagesRepository.findByBookingIdOrdered(bookingId)).isEmpty();
    }

    @Test
    void aRealPhoto_isAccepted() throws Exception {
        mockMvc.perform(multipart("/api/images/{id}/upload", bookingId)
                        .file(new MockMultipartFile("file", "a.jpg", "image/jpeg", tinyJpeg())))
                .andExpect(status().isOk());

        assertThat(imagesRepository.findByBookingIdOrdered(bookingId)).hasSize(1);
    }

    private static byte[] tinyJpeg() throws Exception {
        java.awt.image.BufferedImage img =
                new java.awt.image.BufferedImage(80, 60, java.awt.image.BufferedImage.TYPE_INT_RGB);
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(img, "jpeg", out);
        return out.toByteArray();
    }
}
