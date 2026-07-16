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
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class InspectionImagesService {
    @Autowired
    private InspectionImagesRepository inspectionImagesRepository;

    @Autowired
    InspectionReportsRepository inspectionReportsRepository;

    @Value("${app.upload-dir}")
    private String uploadDir;

    private Path getDirectory(){
        return Paths.get(uploadDir);
    }

    @Transactional
    public InspectionImage saveImages(
            MultipartFile file,
            UUID bookingId
    ) {
        try {
            //Find the report it belongs to
            InspectionReport inspectionReport = inspectionReportsRepository.findByInspectionBooking_IdLite(bookingId);

            //make sure path exists
            Files.createDirectories(getDirectory());

            //creates filename
            String fileName = System.currentTimeMillis() + "_" + UUID.randomUUID().toString() + ".jpg";
            Path path = getDirectory().resolve(fileName);

            //saves to folder
            file.transferTo(path.toFile());

            //saves to db
            InspectionImage inspectionImage = new InspectionImage();
            inspectionImage.setInspectionReport(inspectionReport);
            inspectionImage.setImageUrl(fileName);

            return inspectionImagesRepository.save(inspectionImage);
        } catch (Exception e){
            e.printStackTrace();
            return null;
        }
    }

    public Set<InspectionImage> getImages(UUID id){
        InspectionReport inspectionReport = inspectionReportsRepository.findByInspectionBooking_IdLite(id);
        return inspectionReport.getImages().stream()
                .sorted(Comparator.comparing(InspectionImage::getImageUrl))
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    public ResponseEntity<Resource> getImageFile(UUID id){
        try {
            InspectionImage image = inspectionImagesRepository.findById(id)
                    .orElseThrow();

            Path filePath = getDirectory().resolve(image.getImageUrl());
            Resource resource = new UrlResource(filePath.toUri());

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType("image/jpeg"))
                    .body(resource);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    public String toBase64(UUID id, Set<ImageAnnotation> annotations){
        try {
            InspectionImage image = inspectionImagesRepository.findById(id)
                    .orElseThrow();
            Path filePath = getDirectory().resolve(image.getImageUrl());
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
            Path path = getDirectory().resolve(image.getImageUrl());
            Files.deleteIfExists(path);

            return ResponseEntity.ok().build();

        } catch (Exception e){
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
