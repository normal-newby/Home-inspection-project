package ca.inspection.home.inspection.DTO;

import ca.inspection.home.inspection.entity.BookingStatus;
import ca.inspection.home.inspection.entity.Client;
import ca.inspection.home.inspection.entity.Invoice;
import ca.inspection.home.inspection.service.InspectionBookingsService;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public interface BookingDetails {
    UUID getId();
    Integer getInspectionNumber();
    String getInspectionAddress();
    String getSuite();
    String getCity();
    String getPostalCode();
    String getProvince();
    List<Client> getClients();
    String getAgentName();
    String getAgentPhone();
    String getAgentEmail();
    String getMonth();
    Integer getDay();
    Integer getYear();
    String getStartTime();
    Integer getDurationMinutes();
    BookingStatus getStatus();
    String getReferredBy();
    String getBookedBy();
    Boolean getPaidInFull();
    Boolean getRemoveTax();
    List<Invoice> getInvoices();

    // Invoice total with tax
    default BigDecimal getPrice() {
        List<Invoice> invoices = getInvoices();
        if (invoices == null || invoices.isEmpty()) return null;
        BigDecimal subtotal = invoices.stream()
                .map(Invoice::getFee)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return InspectionBookingsService.invoiceAmount(subtotal, Boolean.TRUE.equals(getRemoveTax())).getTotal();
    }
}
