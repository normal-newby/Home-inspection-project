package ca.inspection.home.inspection.DTO;

import ca.inspection.home.inspection.entity.Invoice;

import java.util.List;
import java.util.UUID;

public interface BookingDetails {
    UUID getId();
    Integer getInspectionNumber();
    String getInspectionAddress();
    String getSuite();
    String getCity();
    String getPostalCode();
    String getProvince();
    String getClientFirstName();
    String getClientLastName();
    String getEmail();
    String getPhone();
    String getMonth();
    Integer getDay();
    Integer getYear();
    String getStartTime();
    Integer getDurationMinutes();
    String getReferredBy();
    String getBookedBy();
    Boolean getPaidInFull();
    Boolean getRemoveTax();
    List<Invoice> getInvoices();
}
