package ca.inspection.home.inspection.service;

import ca.inspection.home.inspection.entity.InspectionBookings;
import ca.inspection.home.inspection.entity.InspectionReport;
import ca.inspection.home.inspection.repository.InspectionReportsRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class InspectionReportsServiceTest {

    private static final int INSPECTION_NUMBER = 1330;

    @Mock
    private InspectionReportsRepository inspectionReportsRepository;

    @Mock
    private InspectorProfileService inspectorProfileService;

    @Mock
    private HelperFunctions helperFunctions;

    @InjectMocks
    private InspectionReportsService inspectionReportsService;

    @TempDir
    Path tempDir;

    private Path inspectionDir() {
        return tempDir.resolve("booking_" + INSPECTION_NUMBER);
    }

    private static InspectionReport reportFor(Integer inspectionNumber) {
        InspectionBookings booking = new InspectionBookings();
        booking.setInspectionNumber(inspectionNumber);
        InspectionReport report = new InspectionReport();
        report.setInspectionBooking(booking);
        return report;
    }

    private MultipartFile pdfThatWrites(byte[] content) throws IOException {
        MultipartFile file = mock(MultipartFile.class);
        doAnswer(invocation -> {
            File dest = invocation.getArgument(0);
            Files.write(dest.toPath(), content);
            return null;
        }).when(file).transferTo(any(File.class));
        return file;
    }

    @Test
    void updateAppendixPdf_writesIntoTheBookingsOwnFolder() throws IOException {
        UUID bookingId = UUID.randomUUID();
        InspectionReport report = reportFor(INSPECTION_NUMBER);

        when(inspectionReportsRepository.findByInspectionBooking_IdLite(bookingId)).thenReturn(report);
        when(helperFunctions.getDirectory(INSPECTION_NUMBER)).thenReturn(inspectionDir());
        when(inspectionReportsRepository.save(any(InspectionReport.class)))
                .thenAnswer(res -> res.getArgument(0));

        inspectionReportsService.updateAppendixPdf(bookingId, pdfThatWrites("pdf".getBytes()));

        String fileName = "appendix_" + bookingId + ".pdf";
        assertThat(report.getAppendixPdf()).isEqualTo(fileName);
        // The folder is created on demand rather than assumed to exist.
        assertThat(inspectionDir().resolve(fileName)).exists();
        assertThat(tempDir.resolve(fileName)).doesNotExist();
    }

    @Test
    void updateAppendixPdf_bookingWithNoInspectionNumber_fallsBackToTheRoot() throws IOException {
        UUID bookingId = UUID.randomUUID();
        InspectionReport report = reportFor(null);

        when(inspectionReportsRepository.findByInspectionBooking_IdLite(bookingId)).thenReturn(report);
        when(helperFunctions.getDirectory((Integer) null)).thenReturn(tempDir);
        when(inspectionReportsRepository.save(any(InspectionReport.class)))
                .thenAnswer(res -> res.getArgument(0));

        inspectionReportsService.updateAppendixPdf(bookingId, pdfThatWrites("pdf".getBytes()));

        assertThat(tempDir.resolve("appendix_" + bookingId + ".pdf")).exists();
    }

    @Test
    void readAppendixPdfBytes_appendixInTheBookingsFolder_readsIt() throws IOException {
        InspectionReport report = reportFor(INSPECTION_NUMBER);
        report.setAppendixPdf("appendix.pdf");
        Path stored = inspectionDir().resolve("appendix.pdf");
        Files.createDirectories(stored.getParent());
        Files.write(stored, "current".getBytes());

        when(helperFunctions.resolveUpload(INSPECTION_NUMBER, "appendix.pdf")).thenReturn(stored);

        assertThat(inspectionReportsService.readAppendixPdfBytes(report))
                .isEqualTo("current".getBytes());
    }

    @Test
    void readAppendixPdfBytes_appendixStillInTheFlatRoot_isFoundByTheFallback() throws IOException {
        // Uploaded before per-inspection folders existed, so it never moved.
        InspectionReport report = reportFor(INSPECTION_NUMBER);
        report.setAppendixPdf("appendix.pdf");
        Path legacy = tempDir.resolve("appendix.pdf");
        Files.write(legacy, "legacy".getBytes());

        when(helperFunctions.resolveUpload(INSPECTION_NUMBER, "appendix.pdf")).thenReturn(legacy);

        assertThat(inspectionReportsService.readAppendixPdfBytes(report))
                .isEqualTo("legacy".getBytes());
    }

    @Test
    void readAppendixPdfBytes_reportHasNoAppendix_fallsBackToTheInspectorsOwn() throws IOException {
        InspectionReport report = reportFor(INSPECTION_NUMBER);
        Path profileAppendix = tempDir.resolve("appendix.pdf");
        Files.write(profileAppendix, "profile".getBytes());

        when(inspectorProfileService.getAppendixPdfPath()).thenReturn(profileAppendix);

        assertThat(inspectionReportsService.readAppendixPdfBytes(report))
                .isEqualTo("profile".getBytes());
    }

    @Test
    void readAppendixPdfBytes_fileMissingOnDisk_returnsNull() {
        InspectionReport report = reportFor(INSPECTION_NUMBER);
        report.setAppendixPdf("gone.pdf");

        when(helperFunctions.resolveUpload(INSPECTION_NUMBER, "gone.pdf"))
                .thenReturn(inspectionDir().resolve("gone.pdf"));

        assertThat(inspectionReportsService.readAppendixPdfBytes(report)).isNull();
    }
}
