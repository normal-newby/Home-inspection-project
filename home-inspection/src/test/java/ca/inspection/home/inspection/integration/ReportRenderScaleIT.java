package ca.inspection.home.inspection.integration;

import ca.inspection.home.inspection.entity.*;
import ca.inspection.home.inspection.repository.*;
import ca.inspection.home.inspection.service.ReportViewService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

// Diagnostic test for report rendering preformance
@SpringBootTest
@ActiveProfiles("test")
@Tag("integration")
public class ReportRenderScaleIT {

    @Autowired private InspectionBookingsRepository bookingsRepository;
    @Autowired private InspectionReportsRepository reportsRepository;
    @Autowired private InspectionFieldRepository fieldRepository;
    @Autowired private InspectionFieldDefinitionRepository definitionRepository;
    @Autowired private InspectionImagesRepository imagesRepository;
    @Autowired private ImageAnnotationRepository annotationRepository;
    @Autowired private ReportViewService reportViewService;

    private InspectionReport report;
    private UUID bookingId;

    private static final int PHOTO_COUNT = 80;
    private static final int PHOTO_W = 4032;  // a modern phone camera
    private static final int PHOTO_H = 3024;

    @BeforeEach
    void resetState() {
        annotationRepository.deleteAll();
        imagesRepository.deleteAll();
        fieldRepository.deleteAll();
        reportsRepository.deleteAll();
        bookingsRepository.deleteAll();

        InspectionBookings booking = new InspectionBookings();
        booking.setInspectionAddress("1 Big Report Rd");
        booking.setInspectionNumber(7777);
        booking = bookingsRepository.save(booking);
        bookingId = booking.getId();

        report = new InspectionReport();
        report.setInspectionBooking(booking);
        report = reportsRepository.save(report);
    }

    @Test
    void everyPhotoInABigReportIsEncoded() throws Exception {
        Path dir = Path.of("target/integration-test-uploads", "booking_7777");
        Files.createDirectories(dir);

        byte[] jpeg = photo(PHOTO_W, PHOTO_H);
        String fileName = "big.jpg";
        Files.write(dir.resolve(fileName), jpeg);
        System.out.println(">>> source photo on disk: " + (jpeg.length / 1024) + " KB");

        InspectionFieldDefinition definition = new InspectionFieldDefinition();
        definition.setFieldName("shingles");
        definition.setFieldPlace("roofing");
        definition.setFieldType("description");
        definition = definitionRepository.save(definition);

        for (int i = 0; i < PHOTO_COUNT; i++) {
            InspectionField field = new InspectionField();
            field.setInspectionReport(report);
            field.setInspectionFieldDefinition(definition);
            field = fieldRepository.save(field);

            InspectionImage image = new InspectionImage();
            image.setInspectionReport(report);
            image.setInspectionField(field);
            image.setImageUrl(fileName);
            image.setUsed(true);
            imagesRepository.save(image);
        }

        InspectionReport loaded = reportsRepository.findByInspectionBooking_Id(bookingId);
        reportViewService.getOtherFields(loaded);

        Runtime runtime = Runtime.getRuntime();
        System.out.println(">>> max heap: " + (runtime.maxMemory() / 1024 / 1024) + " MB");

        long start = System.currentTimeMillis();
        var fields = reportViewService.getSortedFields(loaded);
        long elapsed = System.currentTimeMillis() - start;

        long base64Bytes = fields.stream()
                .flatMap(f -> f.getInspectionImages().stream())
                .filter(i -> i.getBase64() != null)
                .mapToLong(i -> i.getBase64().length())
                .sum();

        System.out.println(">>> encoded " + PHOTO_COUNT + " photos in " + elapsed + " ms");
        System.out.println(">>> base64 held in memory: " + (base64Bytes / 1024 / 1024) + " MB");

        // Timing is reported, not gated — it swings too much across machines to assert on.
        assertThat(fields)
                .hasSize(PHOTO_COUNT)
                .allSatisfy(field -> assertThat(field.getInspectionImages())
                        .singleElement()
                        .satisfies(image -> assertThat(image.getBase64())
                                .startsWith("data:image/jpeg;base64,")));
    }

    private static byte[] photo(int w, int h) throws Exception {
        java.awt.image.BufferedImage img =
                new java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g = img.createGraphics();
        // Noise, so the JPEG doesn't compress down to nothing and skew the numbers.
        java.util.Random random = new java.util.Random(42);
        for (int x = 0; x < w; x += 8) {
            for (int y = 0; y < h; y += 8) {
                g.setColor(new java.awt.Color(random.nextInt(0xFFFFFF)));
                g.fillRect(x, y, 8, 8);
            }
        }
        g.dispose();
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(img, "jpeg", out);
        return out.toByteArray();
    }
}
