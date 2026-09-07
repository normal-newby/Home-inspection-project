package ca.inspection.home.inspection.service;

import ca.inspection.home.inspection.DTO.ImageLocation;
import ca.inspection.home.inspection.entity.InspectionBookings;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Component
public class HelperFunctions {

    @Value("${app.upload-dir}")
    private String uploadDir;

    public Path getDirectory(){
        return Paths.get(uploadDir);
    }

    public Path getDirectory(Integer inspectionNumber){
        return inspectionNumber == null
                ? getDirectory()
                : getDirectory().resolve("booking_" + inspectionNumber);
    }

    public Path resolveUpload(Integer inspectionNumber, String fileName){
        Path path = getDirectory(inspectionNumber).resolve(fileName);
        if (Files.exists(path)) return path;

        Path legacy = getDirectory().resolve(fileName);
        return Files.exists(legacy) ? legacy : path;
    }

    public Path resolveUpload(ImageLocation location){
        return resolveUpload(location.getInspectionNumber(), location.getImageUrl());
    }

    public Path getCompanyAssetDirectory() {
        return getDirectory().resolve("company");
    }

    public Path getRecommendationDiagramDirectory() {
        return getDirectory().resolve("recommendations_diagrams");
    }

    public static String getFileExtension(String originalFileName){
        if (originalFileName == null || !originalFileName.contains(".")) return "";
        int dotIndex = originalFileName.lastIndexOf(".");
        if (dotIndex == originalFileName.length() - 1) return "";
        return originalFileName.substring(dotIndex);

    }

    public static String capitalizeFirstLetter(String s){
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    public static boolean notBlank(String s){
        return s != null && !s.isBlank();
    }

    public static String fullName(InspectionBookings booking){
        String first = booking.getClientFirstName() == null ? "" : booking.getClientFirstName();
        String last = booking.getClientLastName() == null ? "" : booking.getClientLastName();
        String name = (first + " " + last).trim();
        return name.isEmpty() ? null : name;
    }

    public static String formatAddress(InspectionBookings booking){
        List<String> parts = new ArrayList<>();
        String street = booking.getInspectionAddress();
        if (notBlank(street)) {
            parts.add(notBlank(booking.getSuite())
                    ? street.trim() + " Unit " + booking.getSuite().trim()
                    : street.trim());
        }
        if (notBlank(booking.getCity())) {
            parts.add(booking.getCity().trim());
        }
        String region = booking.getProvince();
        String postal = booking.getPostalCode();
        if (notBlank(region) && notBlank(postal)) {
            parts.add(region.trim() + " " + postal.trim());
        } else if (notBlank(region)) {
            parts.add(region.trim());
        } else if (notBlank(postal)) {
            parts.add(postal.trim());
        }
        return String.join(", ", parts);
    }

    private static final DateTimeFormatter TIME_OF_DAY = DateTimeFormatter.ofPattern("h:mm a", Locale.US);

    public static String formatDateTime(InspectionBookings booking){
        String month = booking.getMonth();
        Integer day = booking.getDay();
        Integer year = booking.getYear();
        if (!notBlank(month) || day == null || year == null) return "";

        String date = month.trim() + " " + day + ", " + year;
        String time = formatTime(booking.getStartTime());
        return time.isEmpty() ? date : date + " " + time;
    }

    public static String formatTime(String startTime){
        if (!notBlank(startTime)) return "";
        try {
            // The form sends "HH:mm", and "HH:mm:ss" when seconds are enabled.
            String raw = startTime.trim();
            return LocalTime.parse(raw.length() == 5 ? raw : raw.substring(0, 5)).format(TIME_OF_DAY);
        } catch (RuntimeException e) {
            return "";
        }
    }
}
