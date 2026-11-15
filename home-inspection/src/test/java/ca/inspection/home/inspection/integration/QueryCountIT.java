package ca.inspection.home.inspection.integration;

import ca.inspection.home.inspection.entity.*;
import ca.inspection.home.inspection.repository.*;
import ca.inspection.home.inspection.service.ReportViewService;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Pins how many SQL statements the busy endpoints run. Each one used to run a
 * statement per photo or per booking, so these fail if that pattern comes back.
 */
@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Tag("integration")
public class QueryCountIT {

    private static final int PHOTOS = 30;
    private static final int BOOKINGS = 12;

    @Autowired private MockMvc mockMvc;
    @Autowired private EntityManagerFactory entityManagerFactory;
    @Autowired private InspectionBookingsRepository bookingsRepository;
    @Autowired private InspectionReportsRepository reportsRepository;
    @Autowired private InspectionFieldRepository fieldRepository;
    @Autowired private InspectionFieldDefinitionRepository definitionRepository;
    @Autowired private InspectionImagesRepository imagesRepository;
    @Autowired private ImageAnnotationRepository annotationRepository;
    @Autowired private InvoiceRepository invoiceRepository;
    @Autowired private ClientRepository clientRepository;
    @Autowired private ReportViewService reportViewService;

    private Statistics statistics;

    @BeforeEach
    void resetState() {
        annotationRepository.deleteAll();
        imagesRepository.deleteAll();
        fieldRepository.deleteAll();
        reportsRepository.deleteAll();
        invoiceRepository.deleteAll();
        clientRepository.deleteAll();
        bookingsRepository.deleteAll();

        statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
    }

    private InspectionBookings saveBooking(int number, LocalDate date) {
        InspectionBookings booking = new InspectionBookings();
        booking.setInspectionNumber(number);
        booking.setInspectionAddress(number + " Count St");
        booking.setInspectionDate(date);
        booking.setClients(new ArrayList<>(List.of(
                new Client(null, "Ada", "Client" + number, "ada" + number + "@example.com", "416-555-0101", 0, booking))));

        Invoice invoice = new Invoice();
        invoice.setType("Detached");
        invoice.setFee(new BigDecimal("400.00"));
        invoice.setBookings(booking);
        booking.setInvoices(new ArrayList<>(List.of(invoice)));
        booking = bookingsRepository.save(booking);

        InspectionReport report = new InspectionReport();
        report.setInspectionBooking(booking);
        reportsRepository.save(report);
        return booking;
    }

    /** One booking whose report has PHOTOS items, each with an annotated photo. */
    private UUID saveBookingWithAnnotatedPhotos() {
        InspectionBookings booking = saveBooking(500, LocalDate.now());
        InspectionReport report = reportsRepository.findByInspectionBooking_IdLite(booking.getId());

        InspectionFieldDefinition definition = new InspectionFieldDefinition();
        definition.setFieldName("shingles");
        definition.setFieldPlace("roofing");
        definition.setFieldType("description");
        definition = definitionRepository.save(definition);

        for (int i = 0; i < PHOTOS; i++) {
            InspectionField field = new InspectionField();
            field.setInspectionReport(report);
            field.setInspectionFieldDefinition(definition);
            field = fieldRepository.save(field);

            InspectionImage image = new InspectionImage();
            image.setInspectionReport(report);
            image.setInspectionField(field);
            image.setImageUrl("photo-" + i + ".jpg");
            image.setUsed(true);
            image = imagesRepository.save(image);

            ImageAnnotation annotation = new ImageAnnotation();
            annotation.setInspectionImage(image);
            annotation.setType("arrow");
            annotation.setX(5.0);
            annotation.setY(5.0);
            annotationRepository.save(annotation);
        }
        return booking.getId();
    }

    private long statementsDuring(ThrowingRunnable action) throws Exception {
        statistics.clear();
        action.run();
        return statistics.getPrepareStatementCount();
    }

    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    @Test
    void imageList_doesNotQueryPerPhoto() throws Exception {
        UUID bookingId = saveBookingWithAnnotatedPhotos();

        long statements = statementsDuring(() ->
                mockMvc.perform(get("/api/images/{id}", bookingId))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$", hasSize(PHOTOS)))
                        .andExpect(jsonPath("$[0].id").isNotEmpty())
                        .andExpect(jsonPath("$[0].imageUrl").value("photo-0.jpg"))
                        .andExpect(jsonPath("$[0].used").value(true)));

        assertThat(statements).isLessThanOrEqualTo(3);
    }

    @Test
    void tabItems_doNotQueryPerPhoto() throws Exception {
        UUID bookingId = saveBookingWithAnnotatedPhotos();

        long statements = statementsDuring(() ->
                mockMvc.perform(get("/api/fields/{id}/roofing/description/combined", bookingId))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.*[0].inspectionImages[0].id").isNotEmpty()));

        assertThat(statements).isEqualTo(1);
    }

    @Test
    void reportRender_loadsAnnotationsInOneQuery() throws Exception {
        UUID bookingId = saveBookingWithAnnotatedPhotos();

        List<InspectionField> rendered = new ArrayList<>();
        long statements = statementsDuring(() -> {
            InspectionReport loaded = reportsRepository.findByInspectionBooking_Id(bookingId);
            reportViewService.getOtherFields(loaded);
            rendered.addAll(loaded.getFields());
        });

        assertThat(rendered).hasSize(PHOTOS);
        assertThat(rendered).allSatisfy(field ->
                assertThat(field.getInspectionImages().getFirst().getReportAnnotations()).hasSize(1));
        assertThat(statements).isLessThanOrEqualTo(6);
    }

    @Test
    void reportData_doesNotLoadTheReportsItemsOrPhotos() throws Exception {
        UUID bookingId = saveBookingWithAnnotatedPhotos();

        long statements = statementsDuring(() ->
                mockMvc.perform(get("/api/reports/{id}", bookingId))
                        .andExpect(status().isOk()));

        assertThat(statements).isLessThanOrEqualTo(2);
    }

    @Test
    void bookingList_doesNotQueryPerBooking() throws Exception {
        for (int i = 0; i < BOOKINGS; i++) saveBooking(100 + i, LocalDate.now().plusDays(i));

        long statements = statementsDuring(() ->
                mockMvc.perform(get("/api/get/bookings"))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.bookings", hasSize(BOOKINGS)))
                        .andExpect(jsonPath("$.bookings[0].clients[0].firstName").value("Ada"))
                        .andExpect(jsonPath("$.bookings[0].price").value(452.00)));

        assertThat(statements).isLessThanOrEqualTo(4);
    }

    @Test
    void export_doesNotQueryPerBooking() throws Exception {
        LocalDate date = LocalDate.of(2026, 3, 14);
        for (int i = 0; i < BOOKINGS; i++) saveBooking(100 + i, date);

        long statements = statementsDuring(() ->
                mockMvc.perform(get("/api/analytics/export?from=2026-03&to=2026-03"))
                        .andExpect(status().isOk()));

        assertThat(statements).isLessThanOrEqualTo(4);
    }
}
