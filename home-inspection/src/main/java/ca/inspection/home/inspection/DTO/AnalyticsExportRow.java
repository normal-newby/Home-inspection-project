package ca.inspection.home.inspection.DTO;

import java.time.LocalDate;

// One booking in the analytics spreadsheet.
public record AnalyticsExportRow(
        Integer inspectionNumber,
        LocalDate inspectionDate,
        String address,
        String clientNames,
        String clientEmails,
        String clientPhones,
        String agentName,
        String agentPhone,
        String agentEmail,
        String inspectionTypes,
        InvoiceAmount amount
) {}
