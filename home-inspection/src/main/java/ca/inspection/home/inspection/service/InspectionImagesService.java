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

    @Transactional
    public InspectionImage saveImages(MultipartFile file, UUID bookingId) {
        InspectionReport report = inspectionReportsRepository.findByInspectionBooking_IdLite(bookingId);
        return saveImages(file, report);
    }

    // Overload used when the caller already has the report loaded
    @Transactional
    public InspectionImage saveImages(MultipartFile file, InspectionReport report) {
        try {
            if (report == null) return null;

            InspectionBookings booking = report.getInspectionBooking();
            Path dir = helperFunctions.getDirectory(
                    booking == null ? null : booking.getInspectionNumber());
            Files.createDirectories(dir);

            String fileName = System.currentTimeMillis() + "_" + UUID.randomUUID().toString() + ".jpg";
            Path path = dir.resolve(fileName);
            file.transferTo(path.toFile());

            InspectionImage inspectionImage = new InspectionImage();
            inspectionImage.setInspectionReport(report);
            inspectionImage.setImageUrl(fileName);

            return inspectionImagesRepository.save(inspectionImage);
        } catch (Exception e){
            log.error("Failed to save uploaded image (report={})", report == null ? null : report.getId(), e);
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
        Path thumbsDir = helperFunctions.getDirectory(location.getInspectionNumber())
                .resolve("thumbs");
        Files.createDirectories(thumbsDir);
        Path thumbPath = thumbsDir.resolve(location.getImageUrl());
        if (Files.exists(thumbPath)) return thumbPath;

        Path source = helperFunctions.resolveUpload(location);
        BufferedImage src = ImageIO.read(source.toFile());
        if (src == null) throw new IOException("Unreadable image: " + location.getImageUrl());

        double scale = Math.min(1.0, (double) THUMB_MAX_WIDTH / src.getWidth());
        int w = Math.max(1, (int) Math.round(src.getWidth() * scale));
        int h = Math.max(1, (int) Math.round(src.getHeight() * scale));

        BufferedImage thumb = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = thumb.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(src, 0, 0, w, h, null);
        g.dispose();

        Path tmp = Files.createTempFile(thumbsDir, "thumb_", ".jpg");
        try {
            ImageIO.write(thumb, "jpeg", tmp.toFile());
            Files.move(tmp, thumbPath);
        } catch (Exception e) {
            Files.deleteIfExists(tmp);
            throw e;
        }
        return thumbPath;
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
            ImageIO.write(img, "jpeg", baos);
            String base64 = Base64.getEncoder().encodeToString(baos.toByteArray());

            String type = Files.probeContentType(filePath);
            if (type == null) type = "image/jpeg";

            return "data:" + type + ";base64," + base64;
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
        int maxWidth = maxReportWidth();
        if (source == null || source.getWidth() <= maxWidth) return source;

        int width = maxWidth;
        int height = Math.max(1, (int) Math.round(
                source.getHeight() * ((double) maxWidth / source.getWidth())));

        BufferedImage scaled = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics2D = scaled.createGraphics();
        graphics2D.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        graphics2D.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        graphics2D.drawImage(source, 0, 0, width, height, null);
        graphics2D.dispose();

        return scaled;
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
