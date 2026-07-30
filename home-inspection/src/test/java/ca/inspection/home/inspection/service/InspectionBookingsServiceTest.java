package ca.inspection.home.inspection.service;

import ca.inspection.home.inspection.DTO.InvoiceAmount;
import ca.inspection.home.inspection.entity.Invoice;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

@ExtendWith(MockitoExtension.class)
public class InspectionBookingsServiceTest {

    @InjectMocks
    private InspectionBookingsService inspectionBookingsService;

    @Test
    void buildInvoiceAmount_singleInvoice_calculatesTaxesCorrectly() {
        Invoice invoice = new Invoice(UUID.randomUUID(), "Inspection", new BigDecimal("500.00"), null);

        InvoiceAmount result = inspectionBookingsService.buildInvoiceAmount(List.of(invoice));

        assertThat(result.getSubtotal()).isEqualByComparingTo("500.00");
        assertThat(result.getHst()).isEqualByComparingTo("40.00");
        assertThat(result.getGst()).isEqualByComparingTo("25.00");
        assertThat(result.getTotal()).isEqualByComparingTo("565.00");
    }

    @Test
    void buildInvoiceAmount_multipleInvoices_summedBeforeTax() {
        Invoice a = new Invoice(UUID.randomUUID(), "Home Inspection", new BigDecimal("400.00"), null);
        Invoice b = new Invoice(UUID.randomUUID(), "Radon Test", new BigDecimal("100.00"), null);

        InvoiceAmount result = inspectionBookingsService.buildInvoiceAmount(List.of(a, b));

        assertThat(result.getSubtotal()).isEqualByComparingTo("500.00");
        assertThat(result.getTotal()).isEqualByComparingTo("565.00");
    }

    @Test
    void buildInvoiceAmount_emptyList_returnsZero() {
        InvoiceAmount result = inspectionBookingsService.buildInvoiceAmount(List.of());

        assertThat(result.getSubtotal()).isEqualByComparingTo("0.00");
        assertThat(result.getTotal()).isEqualByComparingTo("0.00");
    }
}
