package ca.inspection.home.inspection.integration;

import ca.inspection.home.inspection.entity.InspectionBookings;
import ca.inspection.home.inspection.entity.InspectionImage;
import ca.inspection.home.inspection.entity.InspectionReport;
import ca.inspection.home.inspection.repository.InspectionBookingsRepository;
import ca.inspection.home.inspection.repository.InspectionImagesRepository;
import ca.inspection.home.inspection.repository.InspectionReportsRepository;
import ca.inspection.home.inspection.service.InspectionImagesService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Tag("integration")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:sqlite:target/concurrent-upload-test.db",
        "spring.datasource.hikari.maximum-pool-size=4",
        "app.upload-dir=target/concurrent-upload-test-uploads"
})
public class ConcurrentImageUploadIT {

    private static final int CONCURRENT_UPLOADS = 8;

    @Autowired
    private InspectionImagesService inspectionImagesService;

    @Autowired
    private InspectionBookingsRepository bookingsRepository;

    @Autowired
    private InspectionReportsRepository reportsRepository;

    @Autowired
    private InspectionImagesRepository imagesRepository;

    private UUID bookingId;

    @BeforeEach
    void resetState() {
        imagesRepository.deleteAll();
        reportsRepository.deleteAll();
        bookingsRepository.deleteAll();

        InspectionBookings booking = new InspectionBookings();
        booking.setInspectionAddress("42 Shutter Lane");
        booking.setInspectionNumber(2001);
        booking = bookingsRepository.save(booking);
        bookingId = booking.getId();

        InspectionReport report = new InspectionReport();
        report.setInspectionBooking(booking);
        reportsRepository.save(report);
    }

    private static MultipartFile photo(int index) {
        return new MockMultipartFile(
                "file", "photo-" + index + ".jpg", "image/jpeg",
                ("not a real jpeg, only the bytes on disk matter here " + index).getBytes());
    }

    @Test
    void everyPhotoInABatchIsStored() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(CONCURRENT_UPLOADS);
        List<Callable<InspectionImage>> uploads = new ArrayList<>();
        for (int i = 0; i < CONCURRENT_UPLOADS; i++) {
            int index = i;
            uploads.add(() -> inspectionImagesService.saveImages(photo(index), bookingId));
        }

        List<Future<InspectionImage>> results;
        try {
            results = pool.invokeAll(uploads);
        } finally {
            pool.shutdown();
        }

        int stored = 0;
        for (Future<InspectionImage> result : results) {
            if (result.get() != null) stored++;
        }

        // saveImages swallows the failure and returns null, which the controller turns into
        // a 500, so a null here is exactly one photo the inspector was told it could not save.
        assertThat(stored)
                .as("photos accepted out of %d uploaded at once", CONCURRENT_UPLOADS)
                .isEqualTo(CONCURRENT_UPLOADS);

        assertThat(imagesRepository.findAll()).hasSize(CONCURRENT_UPLOADS);
    }
}
