package ca.inspection.home.inspection.service;

import ca.inspection.home.inspection.entity.InspectionBookings;
import ca.inspection.home.inspection.entity.InspectorProfile;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

public class GoogleEmailServiceTest {

    private static InspectorProfile profile(String email, String googleAccount) {
        InspectorProfile profile = new InspectorProfile();
        profile.setEmail(email);
        profile.setGoogleAccountEmail(googleAccount);
        return profile;
    }

    private static final List<String> CLIENTS = List.of("ada@example.com", "alan@example.com");

    @Test
    void inspectorCc_usesTheProfileContactEmail() {
        assertThat(GoogleEmailService.inspectorCc(profile(" office@inspect.ca ", "me@gmail.com"), CLIENTS))
                .isEqualTo("office@inspect.ca");
    }

    @Test
    void inspectorCc_fallsBackToTheConnectedGmailAccount() {
        assertThat(GoogleEmailService.inspectorCc(profile("  ", "me@gmail.com"), CLIENTS))
                .isEqualTo("me@gmail.com");
    }

    @Test
    void inspectorCc_isSkippedWhenTheInspectorIsAlreadyARecipient() {
        assertThat(GoogleEmailService.inspectorCc(profile("ADA@example.com", null), CLIENTS)).isNull();
    }

    @Test
    void inspectorCc_noAddressAtAll_isNull() {
        assertThat(GoogleEmailService.inspectorCc(profile(null, null), CLIENTS)).isNull();
    }

    @Test
    void buildMimeMessage_addressesEveryClientAndCopiesTheInspector() {
        String mime = GoogleEmailService.buildMimeMessage("me@gmail.com", String.join(", ", CLIENTS),
                "office@inspect.ca", "body", new byte[]{1}, UUID.randomUUID(), new InspectionBookings());

        assertThat(mime).contains("\r\nTo: ada@example.com, alan@example.com\r\n");
        assertThat(mime).contains("\r\nCc: office@inspect.ca\r\n");
    }

    @Test
    void buildMimeMessage_withoutCc_hasNoCcHeader() {
        String mime = GoogleEmailService.buildMimeMessage("me@gmail.com", "ada@example.com",
                null, "body", new byte[]{1}, UUID.randomUUID(), new InspectionBookings());

        assertThat(mime).doesNotContain("Cc:");
    }
}
