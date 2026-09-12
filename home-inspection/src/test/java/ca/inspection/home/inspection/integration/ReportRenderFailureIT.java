package ca.inspection.home.inspection.integration;

import ca.inspection.home.inspection.entity.*;
import ca.inspection.home.inspection.repository.*;
import ca.inspection.home.inspection.service.ReportViewService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * One unreadable photo used to take the whole report down with it. A photo that cannot be
 * encoded is dropped from the render; a photo that is fine but carries a bad annotation
 * keeps the photo and drops only the annotation. Uploads are screened now, so the bad files
 * here are planted the way they still arise: rotted on disk, or stored before that check.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Tag("integration")
public class ReportRenderFailureIT {

    @Autowired private MockMvc mockMvc;
    @Autowired private InspectionBookingsRepository bookingsRepository;
    @Autowired private InspectionReportsRepository reportsRepository;
    @Autowired private InspectionFieldRepository fieldRepository;
    @Autowired private InspectionFieldDefinitionRepository definitionRepository;
    @Autowired private InspectionImagesRepository imagesRepository;
    @Autowired private ImageAnnotationRepository annotationRepository;
    @Autowired private ReportViewService reportViewService;

    private InspectionReport report;
    private UUID bookingId;
    private Integer inspectionNumber;

    @BeforeEach
    void resetState() {
        annotationRepository.deleteAll();
        imagesRepository.deleteAll();
        fieldRepository.deleteAll();
        reportsRepository.deleteAll();
        bookingsRepository.deleteAll();

        InspectionBookings booking = new InspectionBookings();
        booking.setInspectionAddress("1 Render Rd");
        booking.setInspectionNumber(9001);
        booking = bookingsRepository.save(booking);
        bookingId = booking.getId();
        inspectionNumber = booking.getInspectionNumber();

        report = new InspectionReport();
        report.setInspectionBooking(booking);
        report = reportsRepository.save(report);
    }

    private InspectionField persistField() {
        InspectionFieldDefinition definition = new InspectionFieldDefinition();
        definition.setFieldName("shingles");
        definition.setFieldPlace("roofing");
        definition.setFieldType("description");
        definition = definitionRepository.save(definition);

        InspectionField field = new InspectionField();
        field.setInspectionReport(report);
        field.setInspectionFieldDefinition(definition);
        return fieldRepository.save(field);
    }

    private InspectionImage uploadAndAttach(byte[] bytes, String name, String contentType) throws Exception {
        mockMvc.perform(multipart("/api/images/{id}/upload", bookingId)
                        .file(new MockMultipartFile("file", name, contentType, bytes)))
                .andExpect(status().isOk());

        InspectionImage image = imagesRepository.findByBookingIdOrdered(bookingId).get(0);
        image.setInspectionField(persistField());
        image.setUsed(true);
        return imagesRepository.save(image);
    }

    private InspectionImage attachStoredFile(byte[] bytes) throws Exception {
        Path dir = Path.of("target/integration-test-uploads", "booking_" + inspectionNumber);
        Files.createDirectories(dir);
        String fileName = System.currentTimeMillis() + "_" + UUID.randomUUID() + ".jpg";
        Files.write(dir.resolve(fileName), bytes);

        InspectionImage image = new InspectionImage();
        image.setInspectionReport(report);
        image.setInspectionField(persistField());
        image.setImageUrl(fileName);
        image.setUsed(true);
        return imagesRepository.save(image);
    }

    private ImageAnnotation annotate(InspectionImage image, String type) {
        ImageAnnotation annotation = new ImageAnnotation();
        annotation.setInspectionImage(image);
        annotation.setType(type);
        annotation.setX(5.0);
        annotation.setY(5.0);
        return annotation;
    }

    private List<InspectionField> render() {
        InspectionReport loaded = reportsRepository.findByInspectionBooking_Id(bookingId);
        reportViewService.getOtherFields(loaded);
        return reportViewService.getSortedFields(loaded);
    }

    private static List<InspectionImage> photosIn(List<InspectionField> fields) {
        return fields.stream().flatMap(f -> f.getInspectionImages().stream()).toList();
    }

    @Test
    void storedFileThatIsNotAnImage_isDroppedAndTheReportStillRenders() throws Exception {
        attachStoredFile("MZ this is definitely not a JPEG".getBytes());

        assertThat(photosIn(render())).isEmpty();
    }

    @Test
    void photoMissingFromDisk_isDroppedAndTheReportStillRenders() throws Exception {
        InspectionImage image = uploadAndAttach(tinyJpeg(), "a.jpg", "image/jpeg");
        Files.deleteIfExists(Path.of("target/integration-test-uploads",
                "booking_" + inspectionNumber, image.getImageUrl()));

        assertThat(photosIn(render())).isEmpty();
    }

    @Test
    void storedFileTruncatedOnDisk_isDroppedAndTheReportStillRenders() throws Exception {
        byte[] full = tinyJpeg();
        attachStoredFile(java.util.Arrays.copyOf(full, full.length / 2));

        assertThat(photosIn(render())).isEmpty();
    }

    @Test
    void oneUnreadablePhoto_doesNotCostTheReadableOnes() throws Exception {
        uploadAndAttach(tinyJpeg(), "good.jpg", "image/jpeg");

        // A second photo on its own field, with nothing behind it on disk.
        InspectionImage broken = new InspectionImage();
        broken.setInspectionReport(report);
        broken.setInspectionField(persistField());
        broken.setImageUrl("never-written.jpg");
        broken.setUsed(true);
        imagesRepository.save(broken);

        List<InspectionImage> rendered = photosIn(render());
        assertThat(rendered).hasSize(1);
        assertThat(rendered.get(0).getBase64()).startsWith("data:image/jpeg;base64,");
    }

    @Test
    void annotationWithNoType_keepsThePhoto() throws Exception {
        InspectionImage image = uploadAndAttach(tinyJpeg(), "a.jpg", "image/jpeg");
        annotationRepository.save(annotate(image, null));

        assertThat(photosIn(render())).hasSize(1);
    }

    @Test
    void annotationWithAnUnparseableColour_keepsThePhoto() throws Exception {
        InspectionImage image = uploadAndAttach(tinyJpeg(), "a.jpg", "image/jpeg");

        ImageAnnotation annotation = annotate(image, "rectangle");
        annotation.setColor("rgba(255,0,0,0.5)");
        annotation.setWidth(10.0);
        annotation.setHeight(10.0);
        annotationRepository.save(annotation);

        assertThat(photosIn(render())).hasSize(1);
    }

    @Test
    void textAnnotationWithNoContent_keepsThePhoto() throws Exception {
        InspectionImage image = uploadAndAttach(tinyJpeg(), "a.jpg", "image/jpeg");
        annotationRepository.save(annotate(image, "text"));

        assertThat(photosIn(render())).hasSize(1);
    }

    @Test
    void textAnnotationWithNoBox_keepsThePhoto() throws Exception {
        InspectionImage image = uploadAndAttach(tinyJpeg(), "a.jpg", "image/jpeg");

        // The canvas sends no width/height for text, so these arrive null.
        ImageAnnotation annotation = annotate(image, "text");
        annotation.setContent("Cracked flashing");
        annotationRepository.save(annotation);

        assertThat(photosIn(render())).hasSize(1);
    }

    @Test
    void coverPagePhotoMissingFromDisk_stillRenders() throws Exception {
        mockMvc.perform(multipart("/api/images/{id}/cover-page-image", bookingId)
                        .file(new MockMultipartFile("file", "cover.jpg", "image/jpeg", tinyJpeg())))
                .andExpect(status().isOk());

        InspectionReport loaded = reportsRepository.findByInspectionBooking_Id(bookingId);
        InspectionImage cover = loaded.getCoverPageImage();
        assertThat(cover).isNotNull();
        Files.deleteIfExists(Path.of("target/integration-test-uploads",
                "booking_" + inspectionNumber, cover.getImageUrl()));

        InspectionReport reloaded = reportsRepository.findByInspectionBooking_Id(bookingId);
        reportViewService.setCoverPageImageBase64(reloaded);

        // Null, not an exception — the template skips the cover photo on null.
        assertThat(reloaded.getCoverPageImage().getBase64()).isNull();
    }

    private static byte[] tinyJpeg() throws Exception {
        java.awt.image.BufferedImage img =
                new java.awt.image.BufferedImage(80, 60, java.awt.image.BufferedImage.TYPE_INT_RGB);
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(img, "jpeg", out);
        return out.toByteArray();
    }
}
