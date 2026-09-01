package ca.inspection.home.inspection.service;

import ca.inspection.home.inspection.entity.InspectorProfile;
import ca.inspection.home.inspection.repository.InspectorProfileRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class GoogleServiceTest {

    @Mock
    private InspectorProfileRepository inspectorProfileRepository;

    @Mock
    private InspectorProfileService inspectorProfileService;

    @InjectMocks
    private GoogleService googleService;

    // CONFIGURATION

    @Test
    void isConfigured_withoutCredentials_isFalse() {
        assertThat(googleService.isConfigured()).isFalse();
    }

    @Test
    void isConfigured_withCredentials_isTrue() {
        ReflectionTestUtils.setField(googleService, "clientId", "client-id");
        ReflectionTestUtils.setField(googleService, "clientSecret", "client-secret");

        assertThat(googleService.isConfigured()).isTrue();
    }

    @Test
    void isConfigured_onlyClientId_isFalse() {
        ReflectionTestUtils.setField(googleService, "clientId", "client-id");

        assertThat(googleService.isConfigured()).isFalse();
    }

    @Test
    void isConfigured_onlyClientSecret_isFalse() {
        ReflectionTestUtils.setField(googleService, "clientSecret", "client-secret");

        assertThat(googleService.isConfigured()).isFalse();
    }

    // CONNECTION STATE

    @Test
    void isConnected_noRefreshToken_isFalse() {
        when(inspectorProfileService.getProfile()).thenReturn(new InspectorProfile());

        assertThat(googleService.isConnected()).isFalse();
    }

    @Test
    void isConnected_hasRefreshToken_isTrue() {
        InspectorProfile profile = new InspectorProfile();
        profile.setGoogleRefreshToken("refresh-token");
        when(inspectorProfileService.getProfile()).thenReturn(profile);

        assertThat(googleService.isConnected()).isTrue();
    }

    @Test
    void isConnected_blankRefreshToken_isFalse() {
        InspectorProfile profile = new InspectorProfile();
        profile.setGoogleRefreshToken("   ");
        when(inspectorProfileService.getProfile()).thenReturn(profile);

        assertThat(googleService.isConnected()).isFalse();
    }

    // BUILD AUTH URL

    @Test
    void buildAuthUrl_notConfigured_throwsIllegalStateException() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> googleService.buildAuthUrl())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not set up");
    }

    @Test
    void buildAuthUrl_configured_includesClientIdAndScopes() {
        ReflectionTestUtils.setField(googleService, "clientId", "client-id");
        ReflectionTestUtils.setField(googleService, "clientSecret", "client-secret");
        ReflectionTestUtils.setField(googleService, "redirectUri", "http://localhost:8080/api/google/calendar/callback");

        String url = googleService.buildAuthUrl();

        assertThat(url).contains("client_id=client-id");
        assertThat(url).contains("calendar.events");
        assertThat(url).contains("gmail.send");
        assertThat(url).contains("state=");
    }

    // DISCONNECT

    @Test
    void disconnect_clearsTheStoredGrant() {
        InspectorProfile profile = new InspectorProfile();
        profile.setGoogleRefreshToken(null);
        profile.setGoogleAccountEmail("inspector@example.com");
        when(inspectorProfileService.getProfile()).thenReturn(profile);

        googleService.disconnect();

        assertThat(profile.getGoogleAccountEmail()).isNull();
        assertThat(profile.getGoogleAccessToken()).isNull();
        assertThat(profile.getGoogleTokenExpiry()).isNull();
        verify(inspectorProfileRepository).save(profile);
    }

    @Test
    void disconnect_noRefreshToken_stillClearsProfileWithoutCallingRevoke() {
        InspectorProfile profile = new InspectorProfile();
        profile.setGoogleAccountEmail("inspector@example.com");
        when(inspectorProfileService.getProfile()).thenReturn(profile);

        googleService.disconnect();

        assertThat(profile.getGoogleAccountEmail()).isNull();
        verify(inspectorProfileRepository).save(profile);
    }

    // ACCESS TOKEN

    @Test
    void accessToken_notConnected_throwsIllegalStateException() {
        when(inspectorProfileService.getProfile()).thenReturn(new InspectorProfile());

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> googleService.accessToken())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not connected");
    }

    @Test
    void accessToken_cachedTokenStillValid_returnsCachedTokenWithoutRefreshing() {
        InspectorProfile profile = new InspectorProfile();
        profile.setGoogleRefreshToken("refresh-token");
        profile.setGoogleAccessToken("cached-access-token");
        profile.setGoogleTokenExpiry(java.time.Instant.now().getEpochSecond() + 3600);
        when(inspectorProfileService.getProfile()).thenReturn(profile);

        String token = googleService.accessToken();

        assertThat(token).isEqualTo("cached-access-token");
    }

    @Test
    void accessToken_expiredCachedToken_notConfigured_throwsIllegalStateException() {
        InspectorProfile profile = new InspectorProfile();
        profile.setGoogleRefreshToken("refresh-token");
        profile.setGoogleAccessToken("stale-token");
        profile.setGoogleTokenExpiry(java.time.Instant.now().getEpochSecond() - 100); // already expired
        when(inspectorProfileService.getProfile()).thenReturn(profile);
        // clientId/clientSecret left unset -> not configured -> refresh attempt fails fast

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> googleService.accessToken())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not set up");
    }
}