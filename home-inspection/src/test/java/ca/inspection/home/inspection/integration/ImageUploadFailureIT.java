package ca.inspection.home.inspection.integration;

import ca.inspection.home.inspection.entity.InspectionBookings;
import ca.inspection.home.inspection.repository.InspectionBookingsRepository;
import ca.inspection.home.inspection.repository.InspectionImagesRepository;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Tag("integration")
public class ImageUploadFailureIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private InspectionBookingsRepository bookingsRepository;

    @Autowired
    private InspectionImagesRepository imagesRepository;

    @Test
    void upload_bookingHasNoReport_returnsErrorAndStoresNothing() throws Exception {
        // A booking with no report is the case saveImages gives up on by returning null.
        InspectionBookings booking = bookingsRepository.save(new InspectionBookings());

        MockMultipartFile file = new MockMultipartFile(
                "file", "site.jpg", "image/jpeg", new byte[]{1, 2, 3});

        long before = imagesRepository.count();

        mockMvc.perform(multipart("/api/images/{id}/upload", booking.getId()).file(file))
                .andExpect(status().isInternalServerError())
                .andExpect(content().string("Upload failed"));

        assertThat(imagesRepository.count()).isEqualTo(before);
    }

    @Test
    void upload_unknownBooking_returnsErrorRatherThanSaved() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "site.jpg", "image/jpeg", new byte[]{1, 2, 3});

        mockMvc.perform(multipart("/api/images/{id}/upload", UUID.randomUUID()).file(file))
                .andExpect(status().isInternalServerError());
    }
}
