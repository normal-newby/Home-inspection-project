package ca.inspection.home.inspection.service;

import java.util.Comparator;
import java.util.List;

// The order the report walks its sections in
public final class ReportSectionOrder {

    public static final List<String> PLACES = List.of(
            "roofing", "exterior", "structure", "electrical", "heating",
            "cooling", "insulation", "plumbing", "interior"
    );

    public static final List<String> TYPES = List.of(
            "description", "limitations", "recommendations"
    );

    private ReportSectionOrder() {
    }

    public static int placeIndex(String place) {
        return indexOf(PLACES, place);
    }

    public static int typeIndex(String type) {
        return indexOf(TYPES, type);
    }

    public static Comparator<String> placeComparator() {
        return Comparator.comparingInt(ReportSectionOrder::placeIndex).thenComparing(Comparator.naturalOrder());
    }

    public static Comparator<String> typeComparator() {
        return Comparator.comparingInt(ReportSectionOrder::typeIndex).thenComparing(Comparator.naturalOrder());
    }

    private static int indexOf(List<String> order, String value) {
        if (value == null) return Integer.MAX_VALUE;
        int index = order.indexOf(value.toLowerCase());
        return index == -1 ? Integer.MAX_VALUE : index;
    }
}
