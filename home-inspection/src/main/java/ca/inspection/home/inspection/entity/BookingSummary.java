package ca.inspection.home.inspection.entity;

import java.util.UUID;

public interface BookingSummary {
    UUID getId();
    String getClientFirstName();
    String getClientLastName();
    String getInspectionAddress();
    String getPostalCode();
    String getMonth();
    Integer getDay();
    Integer getYear();
}
