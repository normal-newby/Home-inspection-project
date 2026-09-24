package ca.inspection.home.inspection.service;

import ca.inspection.home.inspection.DTO.ImageLocation;
import ca.inspection.home.inspection.entity.Client;
import ca.inspection.home.inspection.entity.InspectionBookings;
import lombok.experimental.Helper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.in;

public class HelperFunctionsTest {

    // File Extension

    @Test
    void getFileExtension_normalFile_returnsExtension() {
        String fileName = "photo.jpg";

        String extension = HelperFunctions.getFileExtension(fileName);

        assertThat(extension).isEqualTo(".jpg");
    }

    @Test
    void getFileExtension_multipleDots_returnsExtensionOnly() {
        String fileName = "many.dots.png";

        String extension = HelperFunctions.getFileExtension(fileName);

        assertThat(extension).isEqualTo(".png");
    }

    @Test
    void getFileExtension_noExtension_returnsEmptyString() {
        String fileName = "photo";

        String extension = HelperFunctions.getFileExtension(fileName);

        assertThat(extension).isEmpty();
    }

    @Test
    void getFileExtension_nullInput_returnsEmptyString() {
        String fileName = null;

        String extension = HelperFunctions.getFileExtension(fileName);

        assertThat(extension).isEmpty();
    }

    @Test
    void getFileExtension_trailingDot_returnsEmptyString() {
        String fileName = "photo.";

        String extension = HelperFunctions.getFileExtension(fileName);

        assertThat(extension).isEmpty();
    }

    // Capitalize First Letter

    @Test
    void capitalizeFirstLetter_lowerCaseWord_capitalizesFirstLetter() {
        String input = "roofing";

        String result = HelperFunctions.capitalizeFirstLetter(input);

        assertThat(result).isEqualTo("Roofing");
    }

    @Test
    void capitalizeFirstLetter_alreadyCapitalized_staysCapitalized() {
        String input = "Roofing";

        String result = HelperFunctions.capitalizeFirstLetter(input);

        assertThat(result).isEqualTo("Roofing");
    }

    @Test
    void capitalizeFirstLetter_emptyString_returnsEmptyString() {
        String input = "";

        String result = HelperFunctions.capitalizeFirstLetter(input);

        assertThat(result).isEmpty();
    }

    @Test
    void capitalizeFirstLetter_isNull_returnsNull() {
        String input = null;

        String result = HelperFunctions.capitalizeFirstLetter(input);

        assertThat(result).isNull();
    }

    // Upload directories

    @TempDir
    Path tempDir;

    private HelperFunctions helperRootedAtTempDir() {
        HelperFunctions helperFunctions = new HelperFunctions();
        ReflectionTestUtils.setField(helperFunctions, "uploadDir", tempDir.toString());
        return helperFunctions;
    }

    @Test
    void getDirectory_withInspectionNumber_isThatInspectionsOwnFolder() {
        Path directory = helperRootedAtTempDir().getDirectory(1312);

        assertThat(directory).isEqualTo(tempDir.resolve("booking_1312"));
    }

    @Test
    void getDirectory_nullInspectionNumber_fallsBackToRootInsteadOfABookingNullFolder() {
        Path directory = helperRootedAtTempDir().getDirectory(null);

        assertThat(directory).isEqualTo(tempDir);
    }

    @Test
    void resolveImage_fileInTheInspectionFolder_usesIt() throws IOException {
        HelperFunctions helperFunctions = helperRootedAtTempDir();
        Path expected = tempDir.resolve("booking_1312").resolve("photo.jpg");
        Files.createDirectories(expected.getParent());
        Files.writeString(expected, "new");

        assertThat(helperFunctions.resolveUpload(1312, "photo.jpg")).isEqualTo(expected);
    }

    @Test
    void resolveImage_fileStillInTheFlatRoot_fallsBackToIt() throws IOException {
        // Images uploaded before per-inspection folders existed were never moved.
        HelperFunctions helperFunctions = helperRootedAtTempDir();
        Path legacy = tempDir.resolve("photo.jpg");
        Files.writeString(legacy, "old");

        assertThat(helperFunctions.resolveUpload(1312, "photo.jpg")).isEqualTo(legacy);
    }

    @Test
    void resolveImage_fileInNeitherPlace_returnsTheInspectionFolderPath() {
        HelperFunctions helperFunctions = helperRootedAtTempDir();

        assertThat(helperFunctions.resolveUpload(1312, "gone.jpg"))
                .isEqualTo(tempDir.resolve("booking_1312").resolve("gone.jpg"));
    }

    @Test
    void resolveImage_fromLocation_readsBothPartsOfIt() throws IOException {
        HelperFunctions helperFunctions = helperRootedAtTempDir();
        Path expected = tempDir.resolve("booking_7").resolve("photo.jpg");
        Files.createDirectories(expected.getParent());
        Files.writeString(expected, "new");

        assertThat(helperFunctions.resolveUpload(ImageLocation.of(7, "photo.jpg")))
                .isEqualTo(expected);
    }

    // Client names

    private static InspectionBookings bookingWith(Client... clients) {
        InspectionBookings booking = new InspectionBookings();
        booking.setClients(List.of(clients));
        return booking;
    }

    @Test
    void fullName_joinsClientsTheWayALetterWould() {
        Client ada = new Client(null, "Ada", "Lovelace", "ada@example.com", null, 0, null);
        Client alan = new Client(null, "Alan", "Turing", " ADA@example.com ", null, 1, null);
        Client grace = new Client(null, "Grace", "Hopper", "grace@example.com", null, 2, null);

        assertThat(HelperFunctions.fullName(bookingWith(ada))).isEqualTo("Ada Lovelace");
        assertThat(HelperFunctions.fullName(bookingWith(ada, alan))).isEqualTo("Ada Lovelace and Alan Turing");
        assertThat(HelperFunctions.fullName(bookingWith(ada, alan, grace)))
                .isEqualTo("Ada Lovelace, Alan Turing and Grace Hopper");
    }

    @Test
    void fullName_noNamedClients_isNull() {
        assertThat(HelperFunctions.fullName(new InspectionBookings())).isNull();
        assertThat(HelperFunctions.fullName(bookingWith(new Client()))).isNull();
    }

    @Test
    void clientEmails_skipsBlanksAndRepeats() {
        Client ada = new Client(null, "Ada", null, "ada@example.com", null, 0, null);
        Client twin = new Client(null, "Twin", null, " ada@example.com ", null, 1, null);
        Client noEmail = new Client(null, "Alan", null, "  ", null, 2, null);
        Client grace = new Client(null, "Grace", null, "grace@example.com", null, 3, null);

        assertThat(HelperFunctions.clientEmails(bookingWith(ada, twin, noEmail, grace)))
                .isEqualTo(List.of("ada@example.com", "grace@example.com"));
    }
}
