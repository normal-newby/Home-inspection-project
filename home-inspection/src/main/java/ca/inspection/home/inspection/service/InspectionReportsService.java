package ca.inspection.home.inspection.service;

import ca.inspection.home.inspection.entity.InspectionBookings;
import ca.inspection.home.inspection.entity.InspectionImage;
import ca.inspection.home.inspection.entity.InspectionReport;
import ca.inspection.home.inspection.entity.InspectorProfile;
import ca.inspection.home.inspection.repository.InspectionBookingsRepository;
import ca.inspection.home.inspection.repository.InspectionReportsRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
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
@Slf4j
public class InspectionReportsService {
    @Autowired
    private InspectionReportsRepository inspectionReportsRepository;

    @Autowired
    private InspectionBookingsRepository inspectionBookingsRepository;

    @Autowired
    private InspectorProfileService inspectorProfileService;

    @Autowired
    private GeminiService geminiService;

    @Value("${app.upload-dir}")
    private String uploadDir;

    private Path getDirectory(){
        return Paths.get(uploadDir);
    }

    public InspectionReport getBooking(UUID bookingId){
        return inspectionReportsRepository.findByInspectionBooking_Id(bookingId);
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
            log.error("Failed to update report data for booking {}", bookingId, e);
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

            log.info("Uploaded appendix pdf for booking {}", bookingId);
            return ResponseEntity.ok(report);
        } catch (Exception e){
            log.error("Failed to upload appendix pdf for booking {}", bookingId, e);
            return ResponseEntity.badRequest().build();
        }
    }

    public ResponseEntity<?> generateSummary(UUID bookingId){
        try {
            InspectionReport report = inspectionReportsRepository.findByInspectionBooking_Id(bookingId);
            if (report == null){
                return ResponseEntity.notFound().build();
            }
            String summary = geminiService.generateSummary(report);
            return ResponseEntity.ok(Map.of("summary", summary));
        } catch (IllegalStateException e){
            // Missing / unconfigured API key
            log.warn("Gemini unavailable for booking {}: {}", bookingId, e.getMessage());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e){
            log.error("Failed to generate Gemini summary for booking {}", bookingId, e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to generate summary"));
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
            log.warn("Failed to read appendix pdf for report {}", report == null ? null : report.getId(), e);
            return null;
        }
    }
}
