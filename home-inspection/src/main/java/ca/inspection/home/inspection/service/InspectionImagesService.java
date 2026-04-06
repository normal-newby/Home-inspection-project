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
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class InspectionImagesService {
    @Autowired
    private InspectionImagesRepository inspectionImagesRepository;

    @Autowired
    private InspectionReportsRepository inspectionReportsRepository;

    @Autowired
    InspectionBookingsService inspectionBookingsService;

    @Value("${app.upload-dir}")
    private String uploadDir;

    private Path getDirectory(){
        return Paths.get(uploadDir);
    }

    @Transactional
    public void saveImages(
            List<MultipartFile> files,
            UUID bookingId,
            List<String> descriptions
    ) throws IOException {
        try {
            //Find the report it belongs to
            InspectionReport inspectionReport = inspectionBookingsService.getReportFromBooking(bookingId);

            //make sure path exists
            Files.createDirectories(getDirectory());

            //iterate through each image upload and saves to file and database
            for (int i = 0; i < files.size(); i++) {
                MultipartFile file = files.get(i);
                String description = i < descriptions.size() ? descriptions.get(i) : "N/A";

                //creates filename
                String fileName = UUID.randomUUID().toString() + ".jpg";
                Path path = getDirectory().resolve(fileName);

                //saves to folder
                file.transferTo(path.toFile());

                //saves to db
                InspectionImage inspectionImage = new InspectionImage();
                inspectionImage.setInspectionReport(inspectionReport);
                inspectionImage.setImageUrl(fileName);
                inspectionImage.setDescription(description);

                inspectionImagesRepository.save(inspectionImage);
            }
        } catch (Exception e){
            e.printStackTrace();
            throw e;
        }
    }

    public Set<InspectionImage> getImages(UUID id){
        InspectionReport inspectionReport = inspectionBookingsService.getReportFromBooking(id);
        return inspectionReport.getImages();
    }

    public ResponseEntity<Resource> getImageFile(UUID id){
        try {
            InspectionImage image = inspectionImagesRepository.getById(id);

            Path filePath = getDirectory().resolve(image.getImageUrl());
            Resource resource = new UrlResource(filePath.toUri());

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType("image/jpeg"))
                    .body(resource);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    public String toBase64(UUID id, List<ImageAnnotation> annotations){
        try {
            InspectionImage image = inspectionImagesRepository.getById(id);
            Path filePath = getDirectory().resolve(image.getImageUrl());
            BufferedImage img = ImageIO.read(filePath.toFile());

            Graphics2D graphics2D = img.createGraphics();
            graphics2D.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            if (annotations != null) {
                for (ImageAnnotation annotation : annotations){
                    Color color = Color.decode(annotation.getColor() == null ? "ff0000" : annotation.getColor());
                    graphics2D.setColor(color);

                    float stroke = 5 * Float.parseFloat(
                            annotation.getStrokeWidth() == null ? "1" : annotation.getStrokeWidth());
                    graphics2D.setStroke(new BasicStroke(stroke));

                    double scaleX = img.getWidth() / annotation.getImageDisplayWidth();
                    double scaleY = img.getHeight() / annotation.getImageDisplayHeight();

                    int x = (int)(annotation.getX() * scaleX);
                    int y = (int)(annotation.getY() * scaleY);
                    int width = (int)(annotation.getWidth() * scaleX);
                    int height = (int)(annotation.getHeight() * scaleY);

                    String type = annotation.getType();
                    switch (type) {
                        case "rectangle" -> graphics2D.drawRect(x, y, width, height);
                        case "ellipse" -> graphics2D.drawOval(x, y, width, height);
                        case "text" -> {
                            graphics2D.setFont(new Font("Arial", Font.PLAIN, (int) stroke));
                            graphics2D.drawString(annotation.getContent(), x, y);
                        }
                        case "arrow" -> drawArrow(graphics2D, annotation, stroke);
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

    private void drawArrow(Graphics2D graphics2D, ImageAnnotation annotation, float stroke){
        int x1 = annotation.getX().intValue(), y1 = annotation.getY().intValue(),
                x2 = x1 + annotation.getWidth().intValue(), y2 = y1 + annotation.getHeight().intValue();
        graphics2D.drawLine(x1, y1, x2, y2);

        double angle = Math.atan2(y2-y1, x2-x1);
        int headLen = Math.max(5, (int)stroke * 2);

        int[] xPoints = {x2,
                (int)(x2 - headLen * Math.cos(angle - Math.PI / 6)),
                (int)(x2 - headLen * Math.cos(angle + Math.PI / 6))};
        int[] yPoints = {y2,
                (int)(y2 - headLen * Math.sin(angle - Math.PI / 6)),
                (int)(y2 - headLen * Math.sin(angle + Math.PI / 6))};
        graphics2D.fillPolygon(xPoints, yPoints, 3);
    }

    public ResponseEntity<Void> deleteImage(UUID id){
        try {
            InspectionImage image = inspectionImagesRepository.getById(id);

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
