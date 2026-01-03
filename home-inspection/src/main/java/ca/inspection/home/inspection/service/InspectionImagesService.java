package ca.inspection.home.inspection.service;

import ca.inspection.home.inspection.entity.InspectionImage;
import ca.inspection.home.inspection.entity.InspectionReport;
import ca.inspection.home.inspection.repository.InspectionImagesRepository;
import ca.inspection.home.inspection.repository.InspectionReportsRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;

@Service
public class InspectionImagesService {
    @Autowired
    private InspectionImagesRepository inspectionImagesRepository;

    @Autowired
    private InspectionReportsRepository inspectionReportsRepository;

    private static final Path DIRECTORY = Paths.get("D:\\Projects\\Home inspection project\\images");

    public void saveImages(
            List<MultipartFile> files,
            UUID inspectionReportId,
            List<String> descriptions
    ) throws IOException {

        //Find the report it belongs to
        InspectionReport inspectionReport = inspectionReportsRepository.findById(inspectionReportId)
                .orElseThrow();

        //make sure path exists
        Files.createDirectories(DIRECTORY);

        //iterate through each image upload and saves to file and database
        for (int i = 0; i < files.size(); i++){
            MultipartFile file = files.get(i);
            String description = descriptions.get(i);

            //creates filename
            String fileName = UUID.randomUUID().toString();
            Path path = DIRECTORY.resolve(fileName);

            //saves to folder
            file.transferTo(path.toFile());

            //saves to db
            InspectionImage inspectionImage = new InspectionImage();
            inspectionImage.setInspectionReport(inspectionReport);
            inspectionImage.setImageURL(fileName);
            inspectionImage.setDescription(description);

            inspectionImagesRepository.save(inspectionImage);
        }
    }
}
