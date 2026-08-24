package ca.inspection.home.inspection.service;

import ca.inspection.home.inspection.entity.ImageAnnotation;
import ca.inspection.home.inspection.entity.InspectionBookings;
import ca.inspection.home.inspection.entity.InspectionImage;
import ca.inspection.home.inspection.entity.InspectionReport;
import ca.inspection.home.inspection.repository.InspectionBookingsRepository;
import ca.inspection.home.inspection.repository.InspectionImagesRepository;
import ca.inspection.home.inspection.repository.InspectionReportsRepository;
import jakarta.transaction.Transactional;
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

            Files.createDirectories(helperFunctions.getDirectory());

            String fileName = System.currentTimeMillis() + "_" + UUID.randomUUID().toString() + ".jpg";
            Path path = helperFunctions.getDirectory().resolve(fileName);
            file.transferTo(path.toFile());

            InspectionImage inspectionImage = new InspectionImage();
            inspectionImage.setInspectionReport(report);
            inspectionImage.setImageUrl(fileName);

            return inspectionImagesRepository.save(inspectionImage);
        } catch (Exception e){
            e.printStackTrace();
            return null;
        }
    }

    public List<InspectionImage> getImages(UUID id){
        return inspectionImagesRepository.findByBookingIdOrdered(id);
    }

    public ResponseEntity<Resource> getImageFile(UUID id){
        try {
            String imageUrl = inspectionImagesRepository.findImageUrlById(id).orElseThrow();
            Path filePath = helperFunctions.getDirectory().resolve(imageUrl);
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
            String imageUrl = inspectionImagesRepository.findImageUrlById(id).orElseThrow();
            Path thumbPath = getOrCreateThumbnail(imageUrl);
            Resource resource = new UrlResource(thumbPath.toUri());

            return ResponseEntity.ok()
                    .contentType(MediaType.IMAGE_JPEG)
                    .cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable())
                    .body(resource);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    private Path getOrCreateThumbnail(String imageUrl) throws IOException {
        Path thumbsDir = helperFunctions.getDirectory().resolve("thumbs");
        Files.createDirectories(thumbsDir);
        Path thumbPath = thumbsDir.resolve(imageUrl);
        if (Files.exists(thumbPath)) return thumbPath;

        Path source = helperFunctions.getDirectory().resolve(imageUrl);
        BufferedImage src = ImageIO.read(source.toFile());
        if (src == null) throw new IOException("Unreadable image: " + imageUrl);

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
            e.printStackTrace();
            return ResponseEntity.badRequest().body("Cover page cannot be saved");
        }
    }

    public String toBase64(UUID id, Set<ImageAnnotation> annotations){
        String imageUrl = inspectionImagesRepository.findImageUrlById(id).orElseThrow();
        return toBase64(imageUrl, annotations);
    }

    public String toBase64(String imageUrl, Set<ImageAnnotation> annotations){
        try {
            Path filePath = helperFunctions.getDirectory().resolve(imageUrl);
            BufferedImage img = ImageIO.read(filePath.toFile());

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

                    float stroke = 10 * Float.parseFloat(
                            annotation.getStrokeWidth() == null ? "1" : annotation.getStrokeWidth());
                    graphics2D.setStroke(new BasicStroke(stroke));

                    double scaleX = imgWidth / annotation.getImageDisplayWidth();
                    double scaleY = imgHeight / annotation.getImageDisplayHeight();

                    int x = (int)(annotation.getX() * scaleX);
                    int y = (int)(annotation.getY() * scaleY);
                    int width = (int)(annotation.getWidth() * scaleX);
                    int height = (int)(annotation.getHeight() * scaleY);

                    String type = annotation.getType();
                    switch (type) {
                        case "rectangle" -> graphics2D.drawRect(x, y, width, height);
                        case "ellipse" -> graphics2D.drawOval(x, y, width, height);
                        case "circle" -> graphics2D.drawOval(x, y, width, height);
                        case "text" -> {
                            int fontSize = (int)(stroke * scaleX);
                            Font font = new Font("Arial Unicode MS", Font.PLAIN, fontSize);
                            if (font.canDisplayUpTo(annotation.getContent()) != -1){
                                font = new Font("SansSerif", Font.PLAIN, fontSize);
                            }
                            graphics2D.setFont(font);
                            graphics2D.drawString(annotation.getContent(), x, y);
                        }
                        case "arrow" -> drawArrow(graphics2D, x, y, width, height);
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

    private void drawArrow(Graphics2D graphics2D, int x, int y, int width, int height){
        double endX = x + width;
        double endY = y + height;

        double dx = endX - x;
        double dy = endY - y;
        double angle = Math.atan2(dy, dx);

        width = Math.abs(width);
        height = Math.abs(height);

        double length = Math.max(width, height);
        double shaftLength = length / 2;
        double strokeWidth = length / 3;

        AffineTransform original = graphics2D.getTransform();

        graphics2D.translate(x, y);
        graphics2D.rotate(angle);

        // Shaft
        graphics2D.fill(new java.awt.geom.Rectangle2D.Double(
                0, -strokeWidth / 2, shaftLength, strokeWidth
        ));

        // Head (triangle)
        int[] xPoints = {(int) shaftLength, (int) length, (int) shaftLength};
        int[] yPoints = {(int) -strokeWidth, 0, (int) strokeWidth};
        graphics2D.fillPolygon(xPoints, yPoints, 3);

        graphics2D.setTransform(original);
    }

    public ResponseEntity<Void> deleteImage(UUID id){
        try {
            InspectionImage image = inspectionImagesRepository.findById(id)
                    .orElseThrow();

            //delete from database
            inspectionImagesRepository.delete(image);

            //delete from disk
            Path path = helperFunctions.getDirectory().resolve(image.getImageUrl());
            Files.deleteIfExists(path);
            Files.deleteIfExists(helperFunctions.getDirectory().resolve("thumbs").resolve(image.getImageUrl()));

            return ResponseEntity.ok().build();

        } catch (Exception e){
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
