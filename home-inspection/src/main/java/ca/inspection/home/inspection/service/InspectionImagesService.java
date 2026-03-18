package ca.inspection.home.inspection.service;

import ca.inspection.home.inspection.entity.InspectionBookings;
import ca.inspection.home.inspection.entity.InspectionImage;
import ca.inspection.home.inspection.entity.InspectionReport;
import ca.inspection.home.inspection.repository.InspectionBookingsRepository;
import ca.inspection.home.inspection.repository.InspectionImagesRepository;
import ca.inspection.home.inspection.repository.InspectionReportsRepository;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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

    private static final Path DIRECTORY = Paths.get("D:\\Projects\\Home inspection project\\images");

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
            Files.createDirectories(DIRECTORY);

            //iterate through each image upload and saves to file and database
            for (int i = 0; i < files.size(); i++) {
                MultipartFile file = files.get(i);
                String description = i < descriptions.size() ? descriptions.get(i) : "N/A";

                //creates filename
                String fileName = UUID.randomUUID().toString() + ".jpg";
                Path path = DIRECTORY.resolve(fileName);

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

            Path filePath = Paths.get("D:\\Projects\\Home inspection project\\images")
                    .resolve(image.getImageUrl());
            Resource resource = new UrlResource(filePath.toUri());

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType("image/jpeg"))
                    .body(resource);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    public ResponseEntity<Void> deleteImage(UUID id){
        try {
            InspectionImage image = inspectionImagesRepository.getById(id);

            //delete from database
            inspectionImagesRepository.delete(image);

            //delete from disk
            Path path = DIRECTORY.resolve(image.getImageUrl());
            Files.deleteIfExists(path);

            return ResponseEntity.ok().build();

        } catch (Exception e){
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
