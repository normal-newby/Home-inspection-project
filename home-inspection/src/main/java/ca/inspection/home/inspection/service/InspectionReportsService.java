package ca.inspection.home.inspection.service;

import ca.inspection.home.inspection.entity.InspectionBookings;
import ca.inspection.home.inspection.entity.InspectionImage;
import ca.inspection.home.inspection.entity.InspectionReport;
import ca.inspection.home.inspection.entity.InspectorProfile;
import ca.inspection.home.inspection.repository.InspectionBookingsRepository;
import ca.inspection.home.inspection.repository.InspectionReportsRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

@Service
public class InspectionReportsService {
    @Autowired
    private InspectionReportsRepository inspectionReportsRepository;

    @Autowired
    private InspectionBookingsRepository inspectionBookingsRepository;

    @Autowired
    private InspectorProfileService inspectorProfileService;

    @Autowired
    private InspectionImagesService inspectionImagesService;

    @Value("${app.upload-dir}")
    private String uploadDir;

    private Path getDirectory(){
        return Paths.get(uploadDir);
    }

    public InspectionReport getOrCreateByBooking(UUID bookingId){
        InspectionReport report = inspectionReportsRepository.findByInspectionBooking_Id(bookingId);
        if (report != null){
            return report;
        }

        InspectionBookings booking = inspectionBookingsRepository.findById(bookingId).orElseThrow();

        InspectionReport newReport = new InspectionReport();
        newReport.setInspectionBooking(booking);

        InspectorProfile profile = inspectorProfileService.getProfile();
        newReport.setSummary(profile.getSummaryLetterBody());

        return inspectionReportsRepository.save(newReport);
    }

    public ResponseEntity<?> updateReportData(UUID bookingId, Map<String, String> body){
        try {
            InspectionReport report = inspectionReportsRepository.findByInspectionBooking_IdLite(bookingId);

            Map<String, Consumer<String>> setters = Map.of(
                    "summary", report::setSummary
            );

            setters.forEach((key, setter) ->{
                if (body.containsKey(key)) {
                    setter.accept(body.get(key));
                }
            });

            inspectionReportsRepository.save(report);
            return ResponseEntity.ok(report);
        } catch (Exception e){
            e.printStackTrace();
            return ResponseEntity.badRequest().build();
        }
    }

    public ResponseEntity<?> updateAppendixPdf(UUID bookingId, MultipartFile pdf){
        try {
            InspectionReport report = inspectionReportsRepository.findByInspectionBooking_IdLite(bookingId);

            String fileName = "appendix_" + bookingId + ".pdf";
            Path path = getDirectory().resolve(fileName);
            pdf.transferTo(path.toFile());

            report.setAppendixPdf(fileName);
            inspectionReportsRepository.save(report);

            return ResponseEntity.ok(report);
        } catch (Exception e){
            e.printStackTrace();
            return ResponseEntity.badRequest().build();
        }
    }

    public ResponseEntity<?> updateCoverPageImage(UUID bookingId, MultipartFile file){
        try {
            InspectionReport report = inspectionReportsRepository.findByInspectionBooking_IdLite(bookingId);

            InspectionImage oldCover = report.getCoverPageImage();
            if (oldCover != null) {
                report.setCoverPageImage(null);
                inspectionReportsRepository.save(report);
                inspectionImagesService.deleteImage(oldCover.getId());
            }

            InspectionImage image = inspectionImagesService.saveImages(file, bookingId);
            report.setCoverPageImage(image);
            inspectionReportsRepository.save(report);

            return ResponseEntity.ok("Cover Page Image saved!");
        } catch (Exception e){
            e.printStackTrace();
            return ResponseEntity.badRequest().body("Cover page cannot be saved");
        }
    }

    public byte[] readAppendixPdfBytes(InspectionReport report){
        try {
            Path path;
            if (report.getAppendixPdf() != null){
                path = getDirectory().resolve(report.getAppendixPdf());
            } else {
                Path profilePath = inspectorProfileService.getAppendixPdfPath();
                if (profilePath == null) return null;
                path = profilePath;
            }
            if (!Files.exists(path)) return null;
            return Files.readAllBytes(path);
        } catch (Exception e){
            e.printStackTrace();
            return null;
        }
    }
}
