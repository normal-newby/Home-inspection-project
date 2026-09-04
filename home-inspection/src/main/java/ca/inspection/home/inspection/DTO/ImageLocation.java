package ca.inspection.home.inspection.DTO;

// Where an image's bytes live: which inspection's folder, and the file name inside it.
// Doubles as a Spring Data projection - see InspectionImagesRepository#findLocationById.
public interface ImageLocation {
    String getImageUrl();
    Integer getInspectionNumber();

    // For callers that already hold the booking, so they can skip the projection query.
    static ImageLocation of(Integer inspectionNumber, String imageUrl) {
        return new Location(inspectionNumber, imageUrl);
    }

    record Location(Integer inspectionNumber, String imageUrl) implements ImageLocation {
        @Override
        public String getImageUrl() {
            return imageUrl;
        }

        @Override
        public Integer getInspectionNumber() {
            return inspectionNumber;
        }
    }
}
