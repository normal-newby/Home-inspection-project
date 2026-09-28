package ca.inspection.home.inspection.integration;

import ca.inspection.home.inspection.entity.BookingStatus;
import ca.inspection.home.inspection.entity.Client;
import ca.inspection.home.inspection.entity.InspectionBookings;
import ca.inspection.home.inspection.entity.Invoice;
import ca.inspection.home.inspection.repository.InspectionBookingsRepository;
import ca.inspection.home.inspection.repository.InspectionReportsRepository;
import ca.inspection.home.inspection.repository.InvoiceRepository;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Tag("integration")
public class AnalyticsExportIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private InspectionBookingsRepository bookingsRepository;

    @Autowired
    private InspectionReportsRepository reportsRepository;

    @Autowired
    private InvoiceRepository invoiceRepository;

    @BeforeEach
    void resetState() {
        reportsRepository.deleteAll();
        invoiceRepository.deleteAll();
        bookingsRepository.deleteAll();
    }

    private InspectionBookings saveBooking(int number, LocalDate date, BookingStatus status, boolean removeTax, String... fees) {
        InspectionBookings booking = new InspectionBookings();
        booking.setInspectionNumber(number);
        booking.setInspectionDate(date);
        booking.setStatus(status);
        booking.setRemoveTax(removeTax);
        booking.setInspectionAddress(number + " Main St");
        booking.setCity("Toronto");
        booking.setProvince("ON");

        List<Invoice> invoices = new ArrayList<>();
        for (int i = 0; i < fees.length; i++) {
            Invoice invoice = new Invoice();
            invoice.setType(i == 0 ? "Detached" : "Radon Test");
            invoice.setFee(new BigDecimal(fees[i]));
            invoice.setBookings(booking);
            invoices.add(invoice);
        }
        booking.setInvoices(invoices);
        return bookingsRepository.save(booking);
    }

    private Sheet download(String query) throws Exception {
        byte[] body = mockMvc.perform(get("/api/analytics/export?" + query))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        return new XSSFWorkbook(new ByteArrayInputStream(body)).getSheetAt(0);
    }

    @Test
    void export_writesOneRowPerBookingWithContactsAndTaxBreakdown() throws Exception {
        InspectionBookings booking = saveBooking(7, LocalDate.of(2026, 3, 14), BookingStatus.COMPLETED, false, "400.00", "100.00");
        Client ada = new Client(null, "Ada", "Lovelace", "ada@example.com", "416-555-0101", 0, booking);
        Client alan = new Client(null, "Alan", "Turing", null, "416-555-0102", 1, booking);
        booking.setClients(new ArrayList<>(List.of(ada, alan)));
        booking.setAgentName("Jane Realtor");
        booking.setAgentPhone("416-555-0100");
        booking.setAgentEmail("jane@realty.ca");
        bookingsRepository.save(booking);
        saveBooking(8, LocalDate.of(2026, 5, 1), BookingStatus.COMPLETED, false, "999.00"); // outside the range

        mockMvc.perform(get("/api/analytics/export?from=2026-01&to=2026-03"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"inspections-2026-01-to-2026-03.xlsx\""));
        Sheet sheet = download("from=2026-01&to=2026-03");

        assertThat(sheet.getRow(0).getCell(0).getStringCellValue()).isEqualTo("Inspection #");
        Row row = sheet.getRow(1);
        assertThat(row.getCell(0).getNumericCellValue()).isEqualTo(7);
        assertThat(DateUtil.getJavaDate(row.getCell(1).getNumericCellValue()))
                .isEqualTo(java.sql.Date.valueOf(LocalDate.of(2026, 3, 14)));
        assertThat(row.getCell(2).getStringCellValue()).isEqualTo("7 Main St, Toronto, ON");
        assertThat(row.getCell(3).getStringCellValue()).isEqualTo("Ada Lovelace and Alan Turing");
        assertThat(row.getCell(4).getStringCellValue()).isEqualTo("ada@example.com");
        assertThat(row.getCell(5).getStringCellValue()).isEqualTo("416-555-0101; 416-555-0102");
        assertThat(row.getCell(6).getStringCellValue()).isEqualTo("Jane Realtor");
        assertThat(row.getCell(7).getStringCellValue()).isEqualTo("416-555-0100");
        assertThat(row.getCell(8).getStringCellValue()).isEqualTo("jane@realty.ca");
        assertThat(row.getCell(9).getStringCellValue()).isEqualTo("Detached, Radon Test");
        assertThat(row.getCell(10).getNumericCellValue()).isEqualTo(500.00);
        assertThat(row.getCell(11).getNumericCellValue()).isEqualTo(40.00);
        assertThat(row.getCell(12).getNumericCellValue()).isEqualTo(25.00);
        assertThat(row.getCell(13).getNumericCellValue()).isEqualTo(565.00);

        Row totals = sheet.getRow(2);
        assertThat(totals.getCell(0).getStringCellValue()).isEqualTo("Total (1 inspection)");
        assertThat(totals.getCell(13).getNumericCellValue()).isEqualTo(565.00);
        assertThat(sheet.getLastRowNum()).isEqualTo(2);
    }

    @Test
    void export_appliesTheSameFiltersAsThePage() throws Exception {
        saveBooking(1, LocalDate.of(2026, 2, 1), BookingStatus.COMPLETED, true, "300.00");
        saveBooking(2, LocalDate.of(2026, 2, 2), BookingStatus.SCHEDULED, false, "300.00");

        Sheet sheet = download("from=2026-02&to=2026-02&status=COMPLETED");

        assertThat(sheet.getRow(1).getCell(0).getNumericCellValue()).isEqualTo(1);
        // Tax removed on this booking, so total equals subtotal.
        assertThat(sheet.getRow(1).getCell(13).getNumericCellValue()).isEqualTo(300.00);
        assertThat(sheet.getRow(2).getCell(0).getStringCellValue()).isEqualTo("Total (1 inspection)");
    }

    @Test
    void export_backwardsRange_isRejected() throws Exception {
        mockMvc.perform(get("/api/analytics/export?from=2026-05&to=2026-01"))
                .andExpect(status().isBadRequest());
    }
}
