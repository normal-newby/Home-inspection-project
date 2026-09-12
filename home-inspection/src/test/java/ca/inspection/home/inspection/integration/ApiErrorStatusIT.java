package ca.inspection.home.inspection.integration;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import ca.inspection.home.inspection.entity.InspectionBookings;
import ca.inspection.home.inspection.entity.InspectionImage;
import ca.inspection.home.inspection.entity.InspectionReport;
import ca.inspection.home.inspection.repository.InspectionBookingsRepository;
import ca.inspection.home.inspection.repository.InspectionImagesRepository;
import ca.inspection.home.inspection.repository.InspectionReportsRepository;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Tag("integration")
public class ApiErrorStatusIT {

    @Autowired private MockMvc mockMvc;
    @Autowired private InspectionBookingsRepository bookingsRepository;
    @Autowired private InspectionReportsRepository reportsRepository;
    @Autowired private InspectionImagesRepository imagesRepository;

    @Test
    void aMalformedUuidInThePath_is400() throws Exception {
        mockMvc.perform(get("/api/bookings/{id}", "not-a-uuid"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void anUnknownBooking_is404() throws Exception {
        mockMvc.perform(get("/api/bookings/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    @Test
    void imagesForAnUnknownBooking_is404() throws Exception {
        mockMvc.perform(get("/api/images/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    @Test
    void anUnknownImageFile_is404() throws Exception {
        mockMvc.perform(get("/api/images/file/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    @Test
    void anUnknownThumbnail_is404() throws Exception {
        mockMvc.perform(get("/api/images/file/{id}/thumb", UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    @Test
    void aCachedThumbnail_survivesTheFullSizeFileBeingDeleted() throws Exception {
        InspectionBookings booking = new InspectionBookings();
        booking.setInspectionAddress("1 Thumb Way");
        booking.setInspectionNumber(4242);
        booking = bookingsRepository.save(booking);

        InspectionReport report = new InspectionReport();
        report.setInspectionBooking(booking);
        report = reportsRepository.save(report);

        Path dir = Path.of("target/integration-test-uploads", "booking_4242");
        Files.createDirectories(dir);
        String fileName = UUID.randomUUID() + ".jpg";
        BufferedImage source = new BufferedImage(900, 600, BufferedImage.TYPE_INT_RGB);
        ImageIO.write(source, "jpeg", dir.resolve(fileName).toFile());

        InspectionImage image = new InspectionImage();
        image.setInspectionReport(report);
        image.setImageUrl(fileName);
        image = imagesRepository.save(image);

        // First call builds the thumbnail from the full-size file.
        mockMvc.perform(get("/api/images/file/{id}/thumb", image.getId()))
                .andExpect(status().isOk());

        Files.delete(dir.resolve(fileName));

        mockMvc.perform(get("/api/images/file/{id}/thumb", image.getId()))
                .andExpect(status().isOk());
    }
}
