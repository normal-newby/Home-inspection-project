package ca.inspection.home.inspection.service;

import ca.inspection.home.inspection.DTO.ImageLocation;
import ca.inspection.home.inspection.entity.ImageAnnotation;
import ca.inspection.home.inspection.entity.InspectionBookings;
import ca.inspection.home.inspection.entity.InspectionImage;
import ca.inspection.home.inspection.entity.InspectionReport;
import ca.inspection.home.inspection.repository.InspectionBookingsRepository;
import ca.inspection.home.inspection.repository.InspectionImagesRepository;
import ca.inspection.home.inspection.repository.InspectionReportsRepository;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
public class InspectionImagesService {
    @Autowired
    private InspectionImagesRepository inspectionImagesRepository;

    @Autowired
    private InspectionReportsRepository inspectionReportsRepository;

    @Autowired
    private InspectionReportsService inspectionReportsService;

    @Autowired
    private HelperFunctions helperFunctions;

    public InspectionImage saveImages(MultipartFile file, UUID bookingId) {
        InspectionReport report = inspectionReportsRepository.findByInspectionBooking_IdLite(bookingId);
        return saveImages(file, report);
    }

    public InspectionImage saveImages(MultipartFile file, InspectionReport report) {
        if (report == null) return null;

        Path path = null;
        try {
            InspectionBookings booking = report.getInspectionBooking();
            Path dir = helperFunctions.getDirectory(
                    booking == null ? null : booking.getInspectionNumber());
            Files.createDirectories(dir);

            String fileName = System.currentTimeMillis() + "_" + UUID.randomUUID().toString() + ".jpg";
            path = dir.resolve(fileName);
            file.transferTo(path.toFile());

            InspectionImage inspectionImage = new InspectionImage();
            inspectionImage.setInspectionReport(report);
            inspectionImage.setImageUrl(fileName);

            return inspectionImagesRepository.save(inspectionImage);
        } catch (Exception e){
            log.error("Failed to save uploaded image (report={})", report.getId(), e);

            // No row means nothing will ever point at those bytes, and nothing will ever
            // clean them up either, so the half-done upload is undone here.
            if (path != null) {
                try {
                    Files.deleteIfExists(path);
                } catch (Exception cleanupFailure) {
                    log.warn("Left an orphaned upload behind at {}", path, cleanupFailure);
                }
            }
            return null;
        }
    }

    public List<InspectionImage> getImages(UUID id){
        return inspectionImagesRepository.findByBookingIdOrdered(id);
    }

    public ResponseEntity<Resource> getImageFile(UUID id){
        try {
            ImageLocation location = inspectionImagesRepository.findLocationById(id).orElseThrow();
            Path filePath = helperFunctions.resolveUpload(location);
            Resource resource = new UrlResource(filePath.toUri());

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType("image/jpeg"))
                    // Filenames embed a timestamp + UUID, so bytes never change for an id.
                    .cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable())
                    .body(resource);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    private static final int THUMB_MAX_WIDTH = 400;

    // Smaller file for preview
    public ResponseEntity<Resource> getThumbnailFile(UUID id){
        try {
            ImageLocation location = inspectionImagesRepository.findLocationById(id).orElseThrow();
            Path thumbPath = getOrCreateThumbnail(location);
            Resource resource = new UrlResource(thumbPath.toUri());

            return ResponseEntity.ok()
                    .contentType(MediaType.IMAGE_JPEG)
                    .cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable())
                    .body(resource);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    private Path getOrCreateThumbnail(ImageLocation location) throws IOException {
        Path thumbPath = helperFunctions.getDirectory(location.getInspectionNumber())
                .resolve("thumbs")
                .resolve(location.getImageUrl());

        return Thumbnails.getOrCreate(helperFunctions.resolveUpload(location), thumbPath, THUMB_MAX_WIDTH);
    }

    public ResponseEntity<?> updateCoverPageImage(UUID bookingId, MultipartFile file){
        try {
            InspectionReport report = inspectionReportsRepository.findByInspectionBooking_IdLite(bookingId);

            InspectionImage oldCover = report.getCoverPageImage();
            InspectionImage image = saveImages(file, report);
            report.setCoverPageImage(image);
            inspectionReportsRepository.save(report);

            // Only remove the old cover from disk/db after the new one is safely in place,
            // and only once — no intermediate save-with-null.
            if (oldCover != null) {
                deleteImage(oldCover.getId());
            }

            return ResponseEntity.ok("Cover Page Image saved!");
        } catch (Exception e){
            log.error("Failed to save cover page image for booking {}", bookingId, e);
            return ResponseEntity.badRequest().body("Cover page cannot be saved");
        }
    }

    public String toBase64(UUID id, Set<ImageAnnotation> annotations){
        ImageLocation location = inspectionImagesRepository.findLocationById(id).orElseThrow();
        return toBase64(location, annotations);
    }

    public String toBase64(ImageLocation location, Set<ImageAnnotation> annotations){
        try {
            Path filePath = helperFunctions.resolveUpload(location);
            BufferedImage img = ImageIO.read(filePath.toFile());

            // Down to print size before anything else: annotations are placed against the
            // dimensions read below, so scaling first keeps them proportional for free.
            img = scaleForReport(img);

            //resize
            double imgWidth = img.getWidth();
            double imgHeight = img.getHeight();

            Graphics2D graphics2D = img.createGraphics();
            graphics2D.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics2D.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics2D.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);

            if (annotations != null) {
                for (ImageAnnotation annotation : annotations){
                    Color color = Color.decode(annotation.getColor() == null ? "#ff0000" : annotation.getColor());
                    graphics2D.setColor(color);

                    // Ensures proper scaling versus scaled down photo in annotation js
                    double scaleX = scaleFor(annotation.getImageDisplayWidth(), imgWidth);
                    double scaleY = scaleFor(annotation.getImageDisplayHeight(), imgHeight);
                    double scale = (scaleX + scaleY) / 2;

                    double sizeSteps = parseSize(annotation.getStrokeWidth());

                    float stroke = (float) Math.max(1, sizeSteps * scale);
                    graphics2D.setStroke(new BasicStroke(stroke));

                    int x = (int)(annotation.getX() * scaleX);
                    int y = (int)(annotation.getY() * scaleY);
                    int width = (int)(annotation.getWidth() * scaleX);
                    int height = (int)(annotation.getHeight() * scaleY);

                    String type = annotation.getType();
                    switch (type) {
                        case "rectangle" -> graphics2D.drawRect(x, y, width, height);
                        case "ellipse", "circle" ->
                                graphics2D.drawOval(x - width, y - height, width * 2, height * 2);
                        case "text" -> {
                            int fontSize = (int) Math.round(sizeSteps * CANVAS_TEXT_PX_PER_STEP * scale);
                            Font font = new Font("Arial Unicode MS", Font.PLAIN, fontSize);
                            if (font.canDisplayUpTo(annotation.getContent()) != -1){
                                font = new Font("SansSerif", Font.PLAIN, fontSize);
                            }
                            graphics2D.setFont(font);
                            graphics2D.drawString(annotation.getContent(), x, y);
                        }

                        case "arrow" -> drawArrow(graphics2D, x, y, width, height,
                                sizeSteps, (scaleX + scaleY) / 2,
                                Boolean.TRUE.equals(annotation.getFixedLength()));
                    }
                }
            }
            graphics2D.dispose();

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            if (!ImageIO.write(img, "jpeg", baos)) {
                throw new IOException("No jpeg writer took " + location.getImageUrl());
            }

            // Always jpeg here, whatever the file on disk started out as.
            return "data:image/jpeg;base64,"
                    + Base64.getEncoder().encodeToString(baos.toByteArray());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static final double CANVAS_TEXT_PX_PER_STEP = 10;

    private static final int DEFAULT_REPORT_IMAGE_MAX_WIDTH = 1600;

    @Value("${report.image.max-width:1600}")
    private int reportImageMaxWidth = DEFAULT_REPORT_IMAGE_MAX_WIDTH;

    private int maxReportWidth(){
        return reportImageMaxWidth > 0 ? reportImageMaxWidth : DEFAULT_REPORT_IMAGE_MAX_WIDTH;
    }

    // Scale to 1600 (no big difference in quality)
    private BufferedImage scaleForReport(BufferedImage source){
        return Thumbnails.scaleToWidth(source, maxReportWidth());
    }

    private static double scaleFor(Double displayed, double actual){
        return displayed == null || displayed <= 0 ? 1 : actual / displayed;
    }

    private static double parseSize(String strokeWidth){
        try {
            return strokeWidth == null || strokeWidth.isBlank() ? 1 : Double.parseDouble(strokeWidth);
        } catch (NumberFormatException e){
            return 1;
        }
    }

    private void drawArrow(Graphics2D graphics2D, int x, int y, int width, int height,
                           double sizeSteps, double scale, boolean fixedLength){
        ArrowGeometry arrow = ArrowGeometry.of(width, height, sizeSteps, scale, fixedLength);
        if (arrow.isDegenerate()) return;

        AffineTransform original = graphics2D.getTransform();

        graphics2D.translate(x, y);
        graphics2D.rotate(arrow.angle());

        // Shaft stops where the head starts, so the tip lands on the point that was dragged to.
        if (arrow.shaftLength() > 0) {
            graphics2D.fill(new java.awt.geom.Rectangle2D.Double(
                    0, -arrow.shaftWidth() / 2, arrow.shaftLength(), arrow.shaftWidth()
            ));
        }

        // Head (triangle)
        Path2D.Double head = new Path2D.Double();
        head.moveTo(arrow.shaftLength(), -arrow.headHalfWidth());
        head.lineTo(arrow.length(), 0);
        head.lineTo(arrow.shaftLength(), arrow.headHalfWidth());
        head.closePath();
        graphics2D.fill(head);

        graphics2D.setTransform(original);
    }

    public void deleteBookingFiles(UUID bookingId, Integer inspectionNumber, Collection<String> fileNames){
        if (inspectionNumber != null){
            deleteDirectory(helperFunctions.getDirectory(inspectionNumber));
        }

        // Images that predate per-inspection folders, plus the appendix, still sit in the root.
        Path root = helperFunctions.getDirectory();
        List<String> leftovers = new ArrayList<>(fileNames);
        leftovers.add("appendix_" + bookingId + ".pdf");

        leftovers.forEach(name -> {
            deleteQuietly(root.resolve(name));
            deleteQuietly(root.resolve("thumbs").resolve(name));
        });
    }

    private void deleteDirectory(Path dir){
        if (!Files.isDirectory(dir)) return;
        try (var paths = Files.walk(dir)) {
            // Deepest first, so a directory is always empty by the time it is removed.
            paths.sorted(Comparator.reverseOrder()).forEach(this::deleteQuietly);
        } catch (Exception e){
            log.warn("Left upload folder {} behind", dir, e);
        }
    }

    private void deleteQuietly(Path path){
        try {
            Files.deleteIfExists(path);
        } catch (Exception e){
            log.warn("Could not delete {}", path, e);
        }
    }

    public ResponseEntity<Void> deleteImage(UUID id){
        try {
            InspectionImage image = inspectionImagesRepository.findById(id)
                    .orElseThrow();

            Integer inspectionNumber = inspectionImagesRepository.findLocationById(id)
                    .map(ImageLocation::getInspectionNumber)
                    .orElse(null);

            //delete from database
            inspectionImagesRepository.delete(image);

            //delete from disk, including the flat root for images that predate
            //per-inspection folders
            Set<Path> dirs = new LinkedHashSet<>();
            dirs.add(helperFunctions.getDirectory(inspectionNumber));
            dirs.add(helperFunctions.getDirectory());
            for (Path dir : dirs) {
                Files.deleteIfExists(dir.resolve(image.getImageUrl()));
                Files.deleteIfExists(dir.resolve("thumbs").resolve(image.getImageUrl()));
            }

            return ResponseEntity.ok().build();

        } catch (Exception e){
            log.error("Failed to delete image {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
