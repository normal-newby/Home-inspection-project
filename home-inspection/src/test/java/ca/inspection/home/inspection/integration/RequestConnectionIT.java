package ca.inspection.home.inspection.integration;

import ca.inspection.home.inspection.entity.*;
import ca.inspection.home.inspection.repository.*;
import ca.inspection.home.inspection.service.InspectionReportsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.RestTemplate;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A request used to keep its database connection until the response was written, so a slow
 * PDF render starved every other request. The test profile's pool holds one connection,
 * which makes that visible: anything still holding it blocks the next request.
 * The rest checks the pages still get everything they read now that nothing loads lazily
 * while the response is being written.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Tag("integration")
public class RequestConnectionIT {

    @Autowired private MockMvc mockMvc;
    @Autowired private InspectionBookingsRepository bookingsRepository;
    @Autowired private InspectionReportsRepository reportsRepository;
    @Autowired private InspectionFieldRepository fieldRepository;
    @Autowired private InspectionFieldDefinitionRepository definitionRepository;
    @Autowired private InspectionFieldDefinitionValueRepository valueRepository;
    @Autowired private InspectionRecommendationFieldRepository recommendationRepository;
    @Autowired private InspectionImagesRepository imagesRepository;
    @Autowired private ImageAnnotationRepository annotationRepository;
    @Autowired private InvoiceRepository invoiceRepository;
    @Autowired private ClientRepository clientRepository;
    @Autowired private InspectorProfileRepository profileRepository;

    @MockitoBean private RestTemplate restTemplate;
    @MockitoSpyBean private InspectionReportsService inspectionReportsService;

    private UUID bookingId;
    private InspectionReport report;

    @BeforeEach
    void resetState() {
        annotationRepository.deleteAll();
        imagesRepository.deleteAll();
        recommendationRepository.deleteAll();
        fieldRepository.deleteAll();
        reportsRepository.deleteAll();
        invoiceRepository.deleteAll();
        clientRepository.deleteAll();
        bookingsRepository.deleteAll();
        profileRepository.deleteAll();

        InspectorProfile profile = new InspectorProfile();
        profile.setId(1L);
        profile.setCompany("Careful Inspections");
        profileRepository.save(profile);

        InspectionBookings booking = new InspectionBookings();
        booking.setInspectionNumber(4242);
        booking.setInspectionAddress("9 Pool Rd");
        booking.setClients(new ArrayList<>(List.of(
                new Client(null, "Ada", "Lovelace", "ada@example.com", null, 0, booking))));
        Invoice invoice = new Invoice();
        invoice.setType("Detached house");
        invoice.setFee(new BigDecimal("400.00"));
        invoice.setBookings(booking);
        booking.setInvoices(new ArrayList<>(List.of(invoice)));
        booking = bookingsRepository.save(booking);
        bookingId = booking.getId();

        report = new InspectionReport();
        report.setInspectionBooking(booking);
        report.setSummary("All in order.");
        report.setEmailBody("Your report is attached.");
        report = reportsRepository.save(report);
    }

    private InspectionField saveRecommendation(String value) {
        InspectionFieldDefinition definition = new InspectionFieldDefinition();
        definition.setFieldName("Gutters");
        definition.setFieldPlace("roofing");
        definition.setFieldType("recommendations");
        definition = definitionRepository.save(definition);

        InspectionFieldDefinitionValue definitionValue = new InspectionFieldDefinitionValue();
        definitionValue.setInspectionFieldDefinition(definition);
        definitionValue.setValue(value);
        definitionValue = valueRepository.save(definitionValue);

        InspectionField field = new InspectionField();
        field.setInspectionReport(report);
        field.setInspectionFieldDefinition(definition);
        field.setSelectedValue(definitionValue);
        return fieldRepository.save(field);
    }

    private void attachAnnotatedPhoto(InspectionField field) throws Exception {
        Path dir = Path.of("target/integration-test-uploads", "booking_4242");
        Files.createDirectories(dir);
        ByteArrayOutputStream jpeg = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(40, 30, BufferedImage.TYPE_INT_RGB), "jpeg", jpeg);
        String fileName = UUID.randomUUID() + ".jpg";
        Files.write(dir.resolve(fileName), jpeg.toByteArray());

        InspectionImage image = new InspectionImage();
        image.setInspectionReport(report);
        image.setInspectionField(field);
        image.setImageUrl(fileName);
        image.setUsed(true);
        image = imagesRepository.save(image);

        ImageAnnotation annotation = new ImageAnnotation();
        annotation.setInspectionImage(image);
        annotation.setType("rectangle");
        annotation.setX(5.0);
        annotation.setY(5.0);
        annotation.setWidth(10.0);
        annotation.setHeight(10.0);
        annotationRepository.save(annotation);
    }

    private void pdfServiceAnswers(byte[] pdf) {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(byte[].class)))
                .thenReturn(ResponseEntity.ok(pdf));
    }

    @Test
    void pdfRender_doesNotHoldTheConnectionWhileThePdfServiceWorks() throws Exception {
        CountDownLatch rendering = new CountDownLatch(1);
        CountDownLatch finishRender = new CountDownLatch(1);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(byte[].class)))
                .thenAnswer(call -> {
                    rendering.countDown();
                    finishRender.await(30, TimeUnit.SECONDS);
                    return ResponseEntity.ok("pdf".getBytes());
                });

        CompletableFuture<Integer> render = CompletableFuture.supplyAsync(() -> statusOf("/report/" + bookingId));
        try {
            assertThat(rendering.await(20, TimeUnit.SECONDS)).as("render reached the pdf service").isTrue();

            CompletableFuture<Integer> other = CompletableFuture.supplyAsync(() -> statusOf("/api/profile"));
            assertThat(other.get(5, TimeUnit.SECONDS)).isEqualTo(200);
        } finally {
            finishRender.countDown();
        }
        assertThat(render.get(20, TimeUnit.SECONDS)).isEqualTo(200);
    }

    private int statusOf(String path) {
        try {
            return mockMvc.perform(get(path)).andReturn().getResponse().getStatus();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void pdfRender_readsClientsInvoicesPhotosAndRecommendations() throws Exception {
        InspectionField field = saveRecommendation("Leaking");
        attachAnnotatedPhoto(field);
        InspectionRecommendationField recommendation = new InspectionRecommendationField();
        recommendation.setInspectionField(field);
        recommendation.setTask("Reseal the joints");
        recommendationRepository.save(recommendation);
        pdfServiceAnswers("pdf".getBytes());

        mockMvc.perform(get("/report/{id}", bookingId)).andExpect(status().isOk());

        ArgumentCaptor<HttpEntity<Map<String, Object>>> sent = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).exchange(anyString(), eq(HttpMethod.POST), sent.capture(), eq(byte[].class));
        String html = (String) sent.getValue().getBody().get("html");
        assertThat(html)
                .contains("Ada Lovelace")
                .contains("Detached house")
                .contains("Careful Inspections")
                .contains("Reseal the joints")
                .contains("data:image/jpeg;base64,");
    }

    @Test
    void pdfRender_readsTheAppendixFileOnce() throws Exception {
        pdfServiceAnswers("pdf".getBytes());

        mockMvc.perform(get("/report/{id}", bookingId)).andExpect(status().isOk());

        verify(inspectionReportsService, times(1)).readAppendixPdfBytes(any());
    }

    @Test
    void booking_comesBackWithItsClientsAndInvoices() throws Exception {
        mockMvc.perform(get("/api/bookings/{id}", bookingId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clients[0].firstName").value("Ada"))
                .andExpect(jsonPath("$.invoices[0].type").value("Detached house"))
                .andExpect(jsonPath("$.price").value(452.00));
    }

    @Test
    void reportData_isOnlyWhatThePageReads() throws Exception {
        saveRecommendation("Leaking");

        mockMvc.perform(get("/api/reports/{id}", bookingId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary").value("All in order."))
                .andExpect(jsonPath("$.emailBody").value("Your report is attached."))
                .andExpect(jsonPath("$.showInvoice").value(true))
                .andExpect(jsonPath("$.fields").doesNotExist())
                .andExpect(jsonPath("$.images").doesNotExist());
    }

    @Test
    void reportData_savingAnswersWithTheSavedValues() throws Exception {
        mockMvc.perform(post("/api/reports/{id}", bookingId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"summary\":\"Roof needs work.\",\"showInvoice\":\"false\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary").value("Roof needs work."))
                .andExpect(jsonPath("$.showInvoice").value(false));
    }

    @Test
    void recommendation_canBeSavedAsTheDefaultImplication() throws Exception {
        InspectionField field = saveRecommendation("Leaking");

        mockMvc.perform(put("/api/fields/{id}/recommendations?saveAsDefaultImplication=true", field.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"task\":\"Reseal\",\"implication\":\"Water damage\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.implication").value("Water damage"));

        assertThat(valueRepository.findById(field.getSelectedValue().getId()).orElseThrow().getDefaultImplication())
                .isEqualTo("Water damage");
    }
}
