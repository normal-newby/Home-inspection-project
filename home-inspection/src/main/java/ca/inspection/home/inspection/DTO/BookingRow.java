package ca.inspection.home.inspection.DTO;

import ca.inspection.home.inspection.entity.BookingStatus;

import java.time.LocalDate;
import java.util.UUID;

// A booking's own columns, read without loading the entity (which would drag its report along).
public record BookingRow(
        UUID id,
        Integer inspectionNumber,
        String inspectionAddress,
        String suite,
        String city,
        String province,
        String postalCode,
        String agentName,
        String agentPhone,
        String agentEmail,
        String month,
        Integer day,
        Integer year,
        LocalDate inspectionDate,
        String startTime,
        BookingStatus status,
        Boolean paidInFull,
        Boolean removeTax
) {}
