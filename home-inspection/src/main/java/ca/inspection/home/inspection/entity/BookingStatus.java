package ca.inspection.home.inspection.entity;

public enum BookingStatus {
    SCHEDULED,
    IN_PROGRESS,
    COMPLETED;

    public static BookingStatus orDefault(BookingStatus status) {
        return status == null ? SCHEDULED : status;
    }

    // Accepts all forms
    public static BookingStatus parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("A booking status is required.");
        }
        String value = raw.trim().replace('-', '_').replace(' ', '_').toUpperCase();
        // Strip the quotes a JSON string body would arrive with.
        value = value.replace("\"", "");
        try {
            return BookingStatus.valueOf(value);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("\"" + raw + "\" is not a booking status.");
        }
    }
}
