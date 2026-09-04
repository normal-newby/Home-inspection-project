package ca.inspection.home.inspection.service;

import ca.inspection.home.inspection.DTO.ImageLocation;
import ca.inspection.home.inspection.entity.ImageAnnotation;
import ca.inspection.home.inspection.entity.InspectionBookings;
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

    private void stubUploadDir() {
        lenient().when(helperFunctions.getDirectory()).thenReturn(tempDir);
        lenient().when(helperFunctions.getDirectory(any())).thenReturn(tempDir);
        lenient().when(helperFunctions.resolveUpload(any(ImageLocation.class)))
                .thenAnswer(res -> tempDir.resolve(
                        res.getArgument(0, ImageLocation.class).getImageUrl()));
    }

    private void stubLocation(UUID id, String fileName) {
        when(inspectionImagesRepository.findLocationById(id))
                .thenReturn(Optional.of(ImageLocation.of(INSPECTION_NUMBER, fileName)));
    }

    private static final int INSPECTION_NUMBER = 1312;

    private static InspectionReport reportFor(int inspectionNumber) {
        InspectionBookings booking = new InspectionBookings();
        booking.setInspectionNumber(inspectionNumber);
        InspectionReport report = new InspectionReport();
        report.setInspectionBooking(booking);
        return report;
    }

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

    private static boolean isReddish(BufferedImage img, int x, int y) {
        if (x < 0 || y < 0 || x >= img.getWidth() || y >= img.getHeight()) return false;
        Color c = new Color(img.getRGB(x, y));
        return c.getRed() > 100 && c.getRed() - c.getGreen() > 50 && c.getRed() - c.getBlue() > 50;
    }

    private int paintedRunInColumn(BufferedImage img, int column) {
        int run = 0;
        for (int y = 0; y < img.getHeight(); y++) {
            if (isReddish(img, column, y)) run++;
        }
        return run;
    }

    private int[] paintedBounds(BufferedImage img) {
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, maxX = -1, maxY = -1;
        for (int y = 0; y < img.getHeight(); y++) {
            for (int x = 0; x < img.getWidth(); x++) {
                if (!isReddish(img, x, y)) continue;
                minX = Math.min(minX, x);
                minY = Math.min(minY, y);
                maxX = Math.max(maxX, x);
                maxY = Math.max(maxY, y);
            }
        }
        return maxX < 0 ? null : new int[]{minX, minY, maxX, maxY};
    }

    private BufferedImage render(ImageAnnotation annotation, int imageWidth, int imageHeight)
            throws IOException {
        UUID id = UUID.randomUUID();
        String fileName = "photo-" + id + ".jpg";
        createJpegFile(fileName, imageWidth, imageHeight);

        stubLocation(id, fileName);
        stubUploadDir();

        return decodeBase64Image(inspectionImagesService.toBase64(id, Set.of(annotation)));
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
        stubUploadDir();
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
        stubUploadDir();

        InspectionImage result = inspectionImagesService.saveImages(file, bookingId);

        assertThat(result).isNull();
        verifyNoInteractions(inspectionImagesRepository);
    }

    @Test
    void saveImages_reportWithInspectionNumber_writesIntoThatInspectionsFolder() throws IOException {
        UUID bookingId = UUID.randomUUID();
        InspectionReport report = reportFor(INSPECTION_NUMBER);
        Path inspectionDir = tempDir.resolve("booking_" + INSPECTION_NUMBER);

        MultipartFile file = mockFileThatWrites("content".getBytes());

        when(inspectionReportsRepository.findByInspectionBooking_IdLite(bookingId)).thenReturn(report);
        when(helperFunctions.getDirectory(INSPECTION_NUMBER)).thenReturn(inspectionDir);
        when(inspectionImagesRepository.save(any(InspectionImage.class))).thenAnswer(res -> res.getArgument(0));

        InspectionImage result = inspectionImagesService.saveImages(file, bookingId);

        assertThat(result).isNotNull();
        // The folder is created on demand, not assumed to exist.
        assertThat(inspectionDir.resolve(result.getImageUrl())).exists();
        assertThat(tempDir.resolve(result.getImageUrl())).doesNotExist();
    }

    @Test
    void saveImages_reportWithoutABooking_stillSavesInsteadOfThrowing() throws IOException {
        UUID bookingId = UUID.randomUUID();
        InspectionReport report = new InspectionReport();

        MultipartFile file = mockFileThatWrites("content".getBytes());

        when(inspectionReportsRepository.findByInspectionBooking_IdLite(bookingId)).thenReturn(report);
        when(helperFunctions.getDirectory((Integer) null)).thenReturn(tempDir);
        when(inspectionImagesRepository.save(any(InspectionImage.class))).thenAnswer(res -> res.getArgument(0));

        InspectionImage result = inspectionImagesService.saveImages(file, bookingId);

        assertThat(result).isNotNull();
        assertThat(tempDir.resolve(result.getImageUrl())).exists();
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

        stubLocation(id, "photo.jpg");
        stubUploadDir();

        ResponseEntity<Resource> result = inspectionImagesService.getImageFile(id);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getHeaders().getContentType()).isEqualTo(MediaType.IMAGE_JPEG);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().exists()).isTrue();
    }

    @Test
    void getImageFile_imageNotFound_returnsInternalServerError() {
        UUID id = UUID.randomUUID();
        when(inspectionImagesRepository.findLocationById(id)).thenReturn(Optional.empty());

        ResponseEntity<Resource> result = inspectionImagesService.getImageFile(id);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    // GET THUMBNAIL FILE

    @Test
    void getThumbnailFile_thumbnailNotYetCreated_generatesThumbnailAndReturnsOk() throws IOException {
        UUID id = UUID.randomUUID();
        // Source image large enough that the thumb will actually scale.
        createJpegFile("photo.jpg", 800, 600);

        stubLocation(id, "photo.jpg");
        stubUploadDir();

        ResponseEntity<Resource> result = inspectionImagesService.getThumbnailFile(id);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getHeaders().getContentType()).isEqualTo(MediaType.IMAGE_JPEG);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().exists()).isTrue();
        // Thumbnail file was materialized under thumbs/.
        assertThat(tempDir.resolve("thumbs").resolve("photo.jpg")).exists();
    }

    @Test
    void getThumbnailFile_thumbnailAlreadyExists_reusesFile() throws IOException {
        UUID id = UUID.randomUUID();
        Path thumbsDir = tempDir.resolve("thumbs");
        Files.createDirectories(thumbsDir);
        // Pre-seed the thumbnail; a source image is not needed since we take the cached branch.
        Path cachedThumb = thumbsDir.resolve("photo.jpg");
        BufferedImage img = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
        ImageIO.write(img, "jpg", cachedThumb.toFile());
        long sizeBefore = Files.size(cachedThumb);

        stubLocation(id, "photo.jpg");
        stubUploadDir();

        ResponseEntity<Resource> result = inspectionImagesService.getThumbnailFile(id);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        // File was not regenerated (byte-for-byte identical size means no rewrite happened).
        assertThat(Files.size(cachedThumb)).isEqualTo(sizeBefore);
    }

    @Test
    void getThumbnailFile_imageIdNotFound_returnsInternalServerError() {
        UUID id = UUID.randomUUID();
        when(inspectionImagesRepository.findLocationById(id)).thenReturn(Optional.empty());

        ResponseEntity<Resource> result = inspectionImagesService.getThumbnailFile(id);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    void getThumbnailFile_sourceImageMissingOnDisk_returnsInternalServerError() {
        UUID id = UUID.randomUUID();
        stubLocation(id, "missing.jpg");
        stubUploadDir();

        ResponseEntity<Resource> result = inspectionImagesService.getThumbnailFile(id);

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
        stubUploadDir();
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
        stubUploadDir();
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

        stubLocation(id, image.getImageUrl());
        stubUploadDir();

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

        stubLocation(id, image.getImageUrl());
        stubUploadDir();

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

        stubLocation(id, image.getImageUrl());
        stubUploadDir();

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

        stubLocation(id, image.getImageUrl());
        stubUploadDir();

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

        stubLocation(id, image.getImageUrl());
        stubUploadDir();

        String result = inspectionImagesService.toBase64(id, Set.of(text));

        assertThat(result).startsWith("data:");
    }

    @Test
    void toBase64_imageNotFound_throwsNoSuchElementException() {
        UUID id = UUID.randomUUID();
        when(inspectionImagesRepository.findLocationById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> inspectionImagesService.toBase64(id, null))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void toBase64_fileMissingOnDisk_throwsRuntimeException() {
        UUID id = UUID.randomUUID();
        InspectionImage image = new InspectionImage();
        image.setId(id);
        image.setImageUrl("missing.jpg");

        stubLocation(id, image.getImageUrl());
        stubUploadDir();

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
        stubUploadDir();

        ResponseEntity<Void> result = inspectionImagesService.deleteImage(id);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(inspectionImagesRepository).delete(image);
        assertThat(tempDir.resolve("photo.jpg")).doesNotExist();
    }

    @Test
    void deleteImage_imageStillInTheFlatRoot_removesItFromThereToo() throws IOException {
        UUID id = UUID.randomUUID();
        InspectionImage image = new InspectionImage();
        image.setId(id);
        image.setImageUrl("legacy.jpg");
        // Uploaded before per-inspection folders existed, so it never moved out of the root.
        createJpegFile("legacy.jpg", 5, 5);
        Path inspectionDir = tempDir.resolve("booking_" + INSPECTION_NUMBER);
        Files.createDirectories(inspectionDir);

        when(inspectionImagesRepository.findById(id)).thenReturn(Optional.of(image));
        stubLocation(id, "legacy.jpg");
        when(helperFunctions.getDirectory(INSPECTION_NUMBER)).thenReturn(inspectionDir);
        when(helperFunctions.getDirectory()).thenReturn(tempDir);

        ResponseEntity<Void> result = inspectionImagesService.deleteImage(id);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(tempDir.resolve("legacy.jpg")).doesNotExist();
    }

    @Test
    void deleteImage_imageNotFound_returnsInternalServerError() {
        UUID id = UUID.randomUUID();
        when(inspectionImagesRepository.findById(id)).thenReturn(Optional.empty());

        ResponseEntity<Void> result = inspectionImagesService.deleteImage(id);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    // ANNOTATION SCALING (what the report burns in vs what the canvas drew)

    @Test
    void toBase64_strokeThickness_scalesWithThePhotoNotAFixedMultiplier() throws IOException {
        // Drawn on a 200px wide view of a 800px wide photo: 4x. The canvas stroked it at
        // 2px, so the burned in line should be about 8px — the old code drew a fixed 20px,
        // which is why annotations printed fatter than they were drawn.
        ImageAnnotation rectangle = newAnnotation("rectangle", "#ff0000", "2");
        rectangle.setImageDisplayWidth(200.0);
        rectangle.setImageDisplayHeight(200.0);
        rectangle.setX(50.0);
        rectangle.setY(50.0);
        rectangle.setWidth(100.0);
        rectangle.setHeight(100.0);

        BufferedImage rendered = render(rectangle, 800, 800);

        // A column through the middle of the rectangle crosses its top and bottom edges.
        int painted = paintedRunInColumn(rendered, 400);
        assertThat(painted).isBetween(12, 20); // two edges of roughly 8px, allowing for jpeg edges
    }

    @Test
    void toBase64_strokeThickness_keepsTheSameProportionAtEveryPhotoSize() throws IOException {
        ImageAnnotation small = newAnnotation("rectangle", "#ff0000", "2");
        small.setImageDisplayWidth(200.0);
        small.setImageDisplayHeight(200.0);
        small.setX(50.0);
        small.setY(50.0);
        small.setWidth(100.0);
        small.setHeight(100.0);

        ImageAnnotation large = newAnnotation("rectangle", "#ff0000", "2");
        large.setImageDisplayWidth(200.0);
        large.setImageDisplayHeight(200.0);
        large.setX(50.0);
        large.setY(50.0);
        large.setWidth(100.0);
        large.setHeight(100.0);

        int thin = paintedRunInColumn(render(small, 400, 400), 200);   // 2x
        int thick = paintedRunInColumn(render(large, 800, 800), 400);  // 4x

        // Twice the photo, twice the ink: the annotation covers the same fraction either way.
        assertThat((double) thick / thin).isCloseTo(2.0, within(0.35));
    }

    @Test
    void toBase64_ellipse_isCentredOnThePointItWasDrawnAround() throws IOException {
        // The canvas stores an ellipse as centre + radii; drawOval takes a bounding box.
        ImageAnnotation ellipse = newAnnotation("ellipse", "#ff0000", "1");
        ellipse.setImageDisplayWidth(400.0);
        ellipse.setImageDisplayHeight(400.0);
        ellipse.setX(200.0);   // centre
        ellipse.setY(200.0);
        ellipse.setWidth(100.0);  // radii
        ellipse.setHeight(50.0);

        int[] bounds = paintedBounds(render(ellipse, 400, 400));

        assertThat(bounds).isNotNull();
        // Spans centre +/- radius on each axis, rather than starting at the centre.
        assertThat(bounds[0]).isCloseTo(100, within(4));  // minX
        assertThat(bounds[2]).isCloseTo(300, within(4));  // maxX
        assertThat(bounds[1]).isCloseTo(150, within(4));  // minY
        assertThat(bounds[3]).isCloseTo(250, within(4));  // maxY
    }

    @Test
    void toBase64_ellipse_scalesUpWithThePhoto() throws IOException {
        ImageAnnotation ellipse = newAnnotation("ellipse", "#ff0000", "1");
        ellipse.setImageDisplayWidth(200.0);
        ellipse.setImageDisplayHeight(200.0);
        ellipse.setX(100.0);
        ellipse.setY(100.0);
        ellipse.setWidth(50.0);
        ellipse.setHeight(50.0);

        int[] bounds = paintedBounds(render(ellipse, 400, 400)); // 2x

        assertThat(bounds).isNotNull();
        assertThat(bounds[0]).isCloseTo(100, within(6));
        assertThat(bounds[2]).isCloseTo(300, within(6));
    }

    @Test
    void toBase64_text_keepsScalingWithThePhoto() throws IOException {
        // Guards the text path while the stroke maths around it changed: the canvas uses a
        // font of strokeWidth * 10, and the report has to scale that by the same factor.
        ImageAnnotation text = newAnnotation("text", "#ff0000", "2");
        text.setImageDisplayWidth(200.0);
        text.setImageDisplayHeight(200.0);
        text.setX(20.0);
        text.setY(100.0);
        text.setContent("Hello");

        int[] atOneX = paintedBounds(render(text, 200, 200));
        int[] atThreeX = paintedBounds(render(text, 600, 600));

        assertThat(atOneX).isNotNull();
        assertThat(atThreeX).isNotNull();
        double heightOneX = atOneX[3] - atOneX[1] + 1;
        double heightThreeX = atThreeX[3] - atThreeX[1] + 1;
        assertThat(heightThreeX / heightOneX).isCloseTo(3.0, within(0.4));
    }

    // REPORT IMAGE DOWNSCALING

    @Test
    void toBase64_photoLargerThanPrintSize_isScaledDownToTheCap() throws IOException {
        // A phone photo carries far more pixels than a letter page can show, and every one of
        // them costs memory through decode, annotate, re-encode and base64.
        BufferedImage rendered = render(newAnnotation("rectangle", "#ff0000", "1"), 4000, 3000);

        assertThat(rendered.getWidth()).isEqualTo(1600);
        assertThat(rendered.getHeight()).isEqualTo(1200); // aspect ratio kept
    }

    @Test
    void toBase64_photoAlreadySmallEnough_isLeftAtItsOwnSize() throws IOException {
        BufferedImage rendered = render(newAnnotation("rectangle", "#ff0000", "1"), 800, 600);

        assertThat(rendered.getWidth()).isEqualTo(800);
        assertThat(rendered.getHeight()).isEqualTo(600);
    }

    @Test
    void toBase64_scaledPhoto_keepsAnnotationsInTheSamePlace() throws IOException {
        // The annotation covers the middle quarter of the canvas it was drawn on, so it has to
        // land on the middle quarter of the scaled photo too.
        ImageAnnotation rectangle = newAnnotation("rectangle", "#ff0000", "2");
        rectangle.setImageDisplayWidth(400.0);
        rectangle.setImageDisplayHeight(400.0);
        rectangle.setX(100.0);
        rectangle.setY(100.0);
        rectangle.setWidth(200.0);
        rectangle.setHeight(200.0);

        int[] bounds = paintedBounds(render(rectangle, 4000, 4000));

        assertThat(bounds).isNotNull();
        assertThat(bounds[0]).isCloseTo(400, within(10));   // a quarter of 1600
        assertThat(bounds[2]).isCloseTo(1200, within(10));  // three quarters of 1600
    }

    @Test
    void toBase64_missingDisplaySize_rendersAtFullSizeInsteadOfFailing() throws IOException {
        // Rows written before the display size was recorded must not blow up the report.
        ImageAnnotation rectangle = newAnnotation("rectangle", "#ff0000", "2");
        rectangle.setImageDisplayWidth(null);
        rectangle.setImageDisplayHeight(null);
        rectangle.setX(20.0);
        rectangle.setY(20.0);
        rectangle.setWidth(60.0);
        rectangle.setHeight(60.0);

        int[] bounds = paintedBounds(render(rectangle, 200, 200));

        assertThat(bounds).isNotNull();
        assertThat(bounds[0]).isCloseTo(20, within(4));
    }
}
