package ca.inspection.home.inspection.service;

import ca.inspection.home.inspection.entity.ImageAnnotation;
import ca.inspection.home.inspection.entity.InspectionImage;
import ca.inspection.home.inspection.entity.InspectionReport;
import ca.inspection.home.inspection.repository.InspectionImagesRepository;
import ca.inspection.home.inspection.repository.InspectionReportsRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class InspectionImagesServiceTest {

    @Mock
    private InspectionImagesRepository inspectionImagesRepository;

    @Mock
    private InspectionReportsRepository inspectionReportsRepository;

    @Mock
    private InspectionReportsService inspectionReportsService;

    @Mock
    private HelperFunctions helperFunctions;

    @InjectMocks
    private InspectionImagesService inspectionImagesService;

    @TempDir
    Path tempDir;

    private Path createJpegFile(String fileName, int width, int height) throws IOException {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Path path = tempDir.resolve(fileName);
        ImageIO.write(img, "jpg", path.toFile());
        return path;
    }

    private MultipartFile mockFileThatWrites(byte[] content) throws IOException {
        MultipartFile file = mock(MultipartFile.class);
        doAnswer(invocation -> {
            File dest = invocation.getArgument(0);
            Files.write(dest.toPath(), content);
            return null;
        }).when(file).transferTo(any(File.class));
        return file;
    }

    private BufferedImage decodeBase64Image(String dataUrl) throws IOException {
        String base64 = dataUrl.substring(dataUrl.indexOf(',') + 1);
        byte[] bytes = Base64.getDecoder().decode(base64);
        return ImageIO.read(new ByteArrayInputStream(bytes));
    }

    private boolean regionContainsReddish(BufferedImage img, int centerX, int centerY, int radius) {
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                int x = centerX + dx;
                int y = centerY + dy;
                if (x < 0 || y < 0 || x >= img.getWidth() || y >= img.getHeight()) continue;
                Color c = new Color(img.getRGB(x, y));
                if (c.getRed() > 120 && c.getRed() - c.getGreen() > 60 && c.getRed() - c.getBlue() > 60) {
                    return true;
                }
            }
        }
        return false;
    }

    private ImageAnnotation newAnnotation(String type, String color, String strokeWidth) {
        ImageAnnotation annotation = new ImageAnnotation();
        annotation.setType(type);
        annotation.setColor(color);
        annotation.setStrokeWidth(strokeWidth);
        annotation.setImageDisplayWidth(100.0);
        annotation.setImageDisplayHeight(100.0);
        annotation.setX(10.0);
        annotation.setY(10.0);
        annotation.setWidth(20.0);
        annotation.setHeight(20.0);
        return annotation;
    }

    // SAVE IMAGES

    @Test
    void saveImages_validFile_savesToDiskAndDb() throws IOException {
        UUID bookingId = UUID.randomUUID();
        InspectionReport report = new InspectionReport();
        report.setId(UUID.randomUUID());

        MultipartFile file = mockFileThatWrites("content".getBytes());

        when(inspectionReportsRepository.findByInspectionBooking_IdLite(bookingId)).thenReturn(report);
        when(helperFunctions.getDirectory()).thenReturn(tempDir);
        when(inspectionImagesRepository.save(any(InspectionImage.class))).thenAnswer(res -> res.getArgument(0));

        InspectionImage result = inspectionImagesService.saveImages(file, bookingId);

        assertThat(result).isNotNull();
        assertThat(result.getInspectionReport()).isEqualTo(report);
        assertThat(result.getImageUrl()).endsWith(".jpg");
        assertThat(tempDir.resolve(result.getImageUrl())).exists();
    }

    @Test
    void saveImages_transferFails_returnsNull() throws IOException {
        UUID bookingId = UUID.randomUUID();
        InspectionReport report = new InspectionReport();

        MultipartFile file = mock(MultipartFile.class);
        doThrow(new IOException("disk full")).when(file).transferTo(any(File.class));

        when(inspectionReportsRepository.findByInspectionBooking_IdLite(bookingId)).thenReturn(report);
        when(helperFunctions.getDirectory()).thenReturn(tempDir);

        InspectionImage result = inspectionImagesService.saveImages(file, bookingId);

        assertThat(result).isNull();
        verifyNoInteractions(inspectionImagesRepository);
    }

    // GET IMAGES

    @Test
    void getImages_multipleImages_returnsSortedByUrl() {
        UUID id = UUID.randomUUID();

        InspectionImage imageA = new InspectionImage();
        imageA.setImageUrl("a.jpg");
        InspectionImage imageB = new InspectionImage();
        imageB.setImageUrl("b.jpg");
        InspectionImage imageC = new InspectionImage();
        imageC.setImageUrl("c.jpg");

        // Repository sorts by imageUrl ASC; simulate that here.
        when(inspectionImagesRepository.findByBookingIdOrdered(id))
                .thenReturn(List.of(imageA, imageB, imageC));

        List<InspectionImage> result = inspectionImagesService.getImages(id);

        assertThat(result).containsExactly(imageA, imageB, imageC);
    }

    // GET IMAGE FILE

    @Test
    void getImageFile_imageExists_returnsOkWithJpegResource() throws IOException {
        UUID id = UUID.randomUUID();
        createJpegFile("photo.jpg", 5, 5);

        when(inspectionImagesRepository.findImageUrlById(id)).thenReturn(Optional.of("photo.jpg"));
        when(helperFunctions.getDirectory()).thenReturn(tempDir);

        ResponseEntity<Resource> result = inspectionImagesService.getImageFile(id);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getHeaders().getContentType()).isEqualTo(MediaType.IMAGE_JPEG);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().exists()).isTrue();
    }

    @Test
    void getImageFile_imageNotFound_returnsInternalServerError() {
        UUID id = UUID.randomUUID();
        when(inspectionImagesRepository.findImageUrlById(id)).thenReturn(Optional.empty());

        ResponseEntity<Resource> result = inspectionImagesService.getImageFile(id);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    // UPDATE COVER PAGE IMAGE

    @Test
    void updateCoverPageImage_noExistingCover_savesNewCover() throws IOException {
        UUID bookingId = UUID.randomUUID();
        InspectionReport report = new InspectionReport();
        report.setCoverPageImage(null);

        MultipartFile file = mockFileThatWrites("content".getBytes());

        when(inspectionReportsRepository.findByInspectionBooking_IdLite(bookingId)).thenReturn(report);
        when(helperFunctions.getDirectory()).thenReturn(tempDir);
        when(inspectionImagesRepository.save(any(InspectionImage.class))).thenAnswer(res -> res.getArgument(0));
        when(inspectionReportsRepository.save(any(InspectionReport.class))).thenAnswer(res -> res.getArgument(0));

        ResponseEntity<?> result = inspectionImagesService.updateCoverPageImage(bookingId, file);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isEqualTo("Cover Page Image saved!");
        assertThat(report.getCoverPageImage()).isNotNull();
        verify(inspectionReportsRepository).save(report);
    }

    @Test
    void updateCoverPageImage_existingCover_deletesOldCoverThenSavesNew() throws IOException {
        UUID bookingId = UUID.randomUUID();
        InspectionReport report = new InspectionReport();

        InspectionImage oldCover = new InspectionImage();
        UUID oldCoverId = UUID.randomUUID();
        oldCover.setId(oldCoverId);
        oldCover.setImageUrl("old.jpg");
        createJpegFile("old.jpg", 3, 3);
        report.setCoverPageImage(oldCover);

        MultipartFile file = mockFileThatWrites("content".getBytes());

        when(inspectionReportsRepository.findByInspectionBooking_IdLite(bookingId)).thenReturn(report);
        when(helperFunctions.getDirectory()).thenReturn(tempDir);
        when(inspectionImagesRepository.findById(oldCoverId)).thenReturn(Optional.of(oldCover));
        when(inspectionImagesRepository.save(any(InspectionImage.class))).thenAnswer(res -> res.getArgument(0));
        when(inspectionReportsRepository.save(any(InspectionReport.class))).thenAnswer(res -> res.getArgument(0));

        ResponseEntity<?> result = inspectionImagesService.updateCoverPageImage(bookingId, file);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(report.getCoverPageImage()).isNotEqualTo(oldCover);
        verify(inspectionImagesRepository).delete(oldCover);
        assertThat(tempDir.resolve("old.jpg")).doesNotExist();
    }

    @Test
    void updateCoverPageImage_reportNotFound_returnsBadRequest() {
        UUID bookingId = UUID.randomUUID();
        MultipartFile file = mock(MultipartFile.class);

        when(inspectionReportsRepository.findByInspectionBooking_IdLite(bookingId)).thenReturn(null);

        ResponseEntity<?> result = inspectionImagesService.updateCoverPageImage(bookingId, file);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(result.getBody()).isEqualTo("Cover page cannot be saved");
    }

    // TO BASE 64

    @Test
    void toBase64_noAnnotations_returnsJpegDataUrl() throws IOException {
        UUID id = UUID.randomUUID();
        InspectionImage image = new InspectionImage();
        image.setId(id);
        image.setImageUrl("photo.jpg");
        createJpegFile("photo.jpg", 10, 10);

        when(inspectionImagesRepository.findImageUrlById(id)).thenReturn(Optional.of(image.getImageUrl()));
        when(helperFunctions.getDirectory()).thenReturn(tempDir);

        String result = inspectionImagesService.toBase64(id, null);

        assertThat(result).startsWith("data:");
        assertThat(result).contains(";base64,");
    }

    @Test
    void toBase64_withShapeAnnotations_drawsWithoutErrorAndAppliesDefaults() throws IOException {
        UUID id = UUID.randomUUID();
        InspectionImage image = new InspectionImage();
        image.setId(id);
        image.setImageUrl("photo.jpg");
        createJpegFile("photo.jpg", 100, 100);

        ImageAnnotation rectangle = newAnnotation("rectangle", null, null);
        ImageAnnotation ellipse = newAnnotation("ellipse", "#00ff00", "2");
        ImageAnnotation circle = newAnnotation("circle", "#0000ff", "1.5");
        ImageAnnotation arrow = newAnnotation("arrow", "#ffff00", null);

        when(inspectionImagesRepository.findImageUrlById(id)).thenReturn(Optional.of(image.getImageUrl()));
        when(helperFunctions.getDirectory()).thenReturn(tempDir);

        String result = inspectionImagesService.toBase64(id, Set.of(rectangle, ellipse, circle, arrow));

        assertThat(result).startsWith("data:");
    }

    @Test
    void toBase64_displayDimensionsSmallerThanImage_scalesAnnotationCoordinatesUp() throws IOException {
        UUID id = UUID.randomUUID();
        InspectionImage image = new InspectionImage();
        image.setId(id);
        image.setImageUrl("photo.jpg");
        // actual stored image is twice the size the annotation was drawn at on the frontend
        createJpegFile("photo.jpg", 200, 100);

        ImageAnnotation annotation = newAnnotation("rectangle", "#ff0000", "1");
        annotation.setImageDisplayWidth(100.0);
        annotation.setImageDisplayHeight(50.0);
        annotation.setX(10.0);
        annotation.setY(10.0);
        annotation.setWidth(20.0);
        annotation.setHeight(20.0);

        when(inspectionImagesRepository.findImageUrlById(id)).thenReturn(Optional.of(image.getImageUrl()));
        when(helperFunctions.getDirectory()).thenReturn(tempDir);

        String result = inspectionImagesService.toBase64(id, Set.of(annotation));
        BufferedImage decoded = decodeBase64Image(result);

        // scaleX = 200/100 = 2, scaleY = 100/50 = 2 -> rect corner drawn at (20,20), not the raw (10,10)
        assertThat(regionContainsReddish(decoded, 20, 20, 6)).isTrue();
        assertThat(regionContainsReddish(decoded, 10, 10, 3)).isFalse();
    }

    @Test
    void toBase64_displayDimensionsLargerThanImage_scalesAnnotationCoordinatesDown() throws IOException {
        UUID id = UUID.randomUUID();
        InspectionImage image = new InspectionImage();
        image.setId(id);
        image.setImageUrl("photo.jpg");
        // actual stored image is half the size the annotation was drawn at on the frontend
        createJpegFile("photo.jpg", 100, 50);

        ImageAnnotation annotation = newAnnotation("rectangle", "#ff0000", "1");
        annotation.setImageDisplayWidth(200.0);
        annotation.setImageDisplayHeight(100.0);
        annotation.setX(40.0);
        annotation.setY(40.0);
        annotation.setWidth(20.0);
        annotation.setHeight(20.0);

        when(inspectionImagesRepository.findImageUrlById(id)).thenReturn(Optional.of(image.getImageUrl()));
        when(helperFunctions.getDirectory()).thenReturn(tempDir);

        String result = inspectionImagesService.toBase64(id, Set.of(annotation));
        BufferedImage decoded = decodeBase64Image(result);

        // scaleX = 100/200 = 0.5, scaleY = 50/100 = 0.5 -> rect corner drawn at (20,20), not the raw (40,40)
        assertThat(regionContainsReddish(decoded, 20, 20, 4)).isTrue();
        assertThat(regionContainsReddish(decoded, 40, 40, 3)).isFalse();
    }

    @Test
    void toBase64_withTextAnnotation_drawsTextWithoutError() throws IOException {
        UUID id = UUID.randomUUID();
        InspectionImage image = new InspectionImage();
        image.setId(id);
        image.setImageUrl("photo.jpg");
        createJpegFile("photo.jpg", 100, 100);

        ImageAnnotation text = newAnnotation("text", "#ff0000", "1");
        text.setContent("Hello");

        when(inspectionImagesRepository.findImageUrlById(id)).thenReturn(Optional.of(image.getImageUrl()));
        when(helperFunctions.getDirectory()).thenReturn(tempDir);

        String result = inspectionImagesService.toBase64(id, Set.of(text));

        assertThat(result).startsWith("data:");
    }

    @Test
    void toBase64_imageNotFound_throwsNoSuchElementException() {
        UUID id = UUID.randomUUID();
        when(inspectionImagesRepository.findImageUrlById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> inspectionImagesService.toBase64(id, null))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void toBase64_fileMissingOnDisk_throwsRuntimeException() {
        UUID id = UUID.randomUUID();
        InspectionImage image = new InspectionImage();
        image.setId(id);
        image.setImageUrl("missing.jpg");

        when(inspectionImagesRepository.findImageUrlById(id)).thenReturn(Optional.of(image.getImageUrl()));
        when(helperFunctions.getDirectory()).thenReturn(tempDir);

        assertThatThrownBy(() -> inspectionImagesService.toBase64(id, null))
                .isInstanceOf(RuntimeException.class)
                .hasCauseInstanceOf(IOException.class);
    }

    // DELETE IMAGE

    @Test
    void deleteImage_imageExists_deletesFromDbAndDisk() throws IOException {
        UUID id = UUID.randomUUID();
        InspectionImage image = new InspectionImage();
        image.setId(id);
        image.setImageUrl("photo.jpg");
        createJpegFile("photo.jpg", 5, 5);

        when(inspectionImagesRepository.findById(id)).thenReturn(Optional.of(image));
        when(helperFunctions.getDirectory()).thenReturn(tempDir);

        ResponseEntity<Void> result = inspectionImagesService.deleteImage(id);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(inspectionImagesRepository).delete(image);
        assertThat(tempDir.resolve("photo.jpg")).doesNotExist();
    }

    @Test
    void deleteImage_imageNotFound_returnsInternalServerError() {
        UUID id = UUID.randomUUID();
        when(inspectionImagesRepository.findById(id)).thenReturn(Optional.empty());

        ResponseEntity<Void> result = inspectionImagesService.deleteImage(id);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
