package ca.inspection.home.inspection.service;

import lombok.experimental.Helper;
import org.junit.jupiter.api.Test;

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
}
