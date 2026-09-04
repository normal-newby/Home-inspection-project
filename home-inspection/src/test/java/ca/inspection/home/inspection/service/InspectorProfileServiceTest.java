package ca.inspection.home.inspection.service;

import ca.inspection.home.inspection.entity.InspectorProfile;
import ca.inspection.home.inspection.repository.InspectionBookingsRepository;
import ca.inspection.home.inspection.repository.InspectorProfileRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class InspectorProfileServiceTest {

    @Mock
    private InspectorProfileRepository inspectorProfileRepository;

    @Mock
    private InspectionBookingsRepository inspectionBookingsRepository;

    @Mock
    private HelperFunctions helperFunctions;

    @InjectMocks
    private InspectorProfileService inspectorProfileService;

    private InspectorProfile storedProfile(Integer inspectionNumber) {
        InspectorProfile profile = new InspectorProfile();
        profile.setId(1L);
        profile.setInspectionNumber(inspectionNumber);
        when(inspectorProfileRepository.findById(1L)).thenReturn(Optional.of(profile));
        return profile;
    }

    // INSPECTION NUMBER ALLOCATION

    @Test
    void getAndUpdateNumber_nothingTaken_countsUpFromTheProfile() {
        storedProfile(1330);
        when(inspectionBookingsRepository.existsByInspectionNumber(anyInt())).thenReturn(false);

        assertThat(inspectorProfileService.getAndUpdateNumber()).isEqualTo(1331);
    }

    @Test
    void getAndUpdateNumber_nextNumbersAlreadyTaken_skipsPastThem() {
        // The number keys the upload folder, so handing out a used one would mix two
        // inspections' photos into the same directory.
        storedProfile(1311);
        when(inspectionBookingsRepository.existsByInspectionNumber(1312)).thenReturn(true);
        when(inspectionBookingsRepository.existsByInspectionNumber(1313)).thenReturn(false);

        assertThat(inspectorProfileService.getAndUpdateNumber()).isEqualTo(1313);
    }

    @Test
    void getAndUpdateNumber_advancesTheStoredCounter() {
        InspectorProfile profile = storedProfile(1330);
        when(inspectionBookingsRepository.existsByInspectionNumber(anyInt())).thenReturn(false);

        inspectorProfileService.getAndUpdateNumber();

        assertThat(profile.getInspectionNumber()).isEqualTo(1331);
    }

    @Test
    void getAndUpdateNumber_counterNeverSet_startsAtOneInsteadOfThrowing() {
        storedProfile(null);
        when(inspectionBookingsRepository.existsByInspectionNumber(anyInt())).thenReturn(false);

        assertThat(inspectorProfileService.getAndUpdateNumber()).isEqualTo(1);
    }

    // SAVING THE PROFILE FORM

    @Test
    void saveProfile_formOmitsInspectionNumber_keepsTheExistingCounter() {
        // A save that leaves the field blank must not roll the counter back to null; that is
        // how several bookings ended up sharing one number.
        storedProfile(1330);

        InspectorProfile incoming = new InspectorProfile();
        incoming.setInspectionNumber(null);
        inspectorProfileService.saveProfile(incoming);

        ArgumentCaptor<InspectorProfile> captor = ArgumentCaptor.forClass(InspectorProfile.class);
        verify(inspectorProfileRepository).save(captor.capture());
        assertThat(captor.getValue().getInspectionNumber()).isEqualTo(1330);
    }

    @Test
    void saveProfile_formSetsInspectionNumber_respectsIt() {
        // The field is editable on purpose, so an explicit value still wins.
        storedProfile(1330);

        InspectorProfile incoming = new InspectorProfile();
        incoming.setInspectionNumber(2000);
        inspectorProfileService.saveProfile(incoming);

        ArgumentCaptor<InspectorProfile> captor = ArgumentCaptor.forClass(InspectorProfile.class);
        verify(inspectorProfileRepository).save(captor.capture());
        assertThat(captor.getValue().getInspectionNumber()).isEqualTo(2000);
    }

    @Test
    void saveProfile_reportLayoutPageSaves_keepsTheProfilePagesFields() {
        // The two pages share one row. The layout page only sends the document bodies, so
        // everything it left out has to survive the save.
        InspectorProfile existing = storedProfile(1330);
        existing.setName("John Smith");
        existing.setCompany("Smith Inspections Inc.");
        existing.setCity("Toronto");

        InspectorProfile incoming = new InspectorProfile();
        incoming.setCoverLetterBody("Thank you for choosing us.");
        inspectorProfileService.saveProfile(incoming);

        ArgumentCaptor<InspectorProfile> captor = ArgumentCaptor.forClass(InspectorProfile.class);
        verify(inspectorProfileRepository).save(captor.capture());
        assertThat(captor.getValue().getName()).isEqualTo("John Smith");
        assertThat(captor.getValue().getCompany()).isEqualTo("Smith Inspections Inc.");
        assertThat(captor.getValue().getCity()).isEqualTo("Toronto");
        assertThat(captor.getValue().getCoverLetterBody()).isEqualTo("Thank you for choosing us.");
    }

    @Test
    void saveProfile_profilePageSaves_keepsTheReportLayoutPagesFields() {
        InspectorProfile existing = storedProfile(1330);
        existing.setCoverLetterBody("Thank you for choosing us.");
        existing.setSummaryLetterBody("The following is a summary.");
        existing.setAgreementBody("This agreement is entered into.");

        InspectorProfile incoming = new InspectorProfile();
        incoming.setName("John Smith");
        inspectorProfileService.saveProfile(incoming);

        ArgumentCaptor<InspectorProfile> captor = ArgumentCaptor.forClass(InspectorProfile.class);
        verify(inspectorProfileRepository).save(captor.capture());
        assertThat(captor.getValue().getCoverLetterBody()).isEqualTo("Thank you for choosing us.");
        assertThat(captor.getValue().getSummaryLetterBody()).isEqualTo("The following is a summary.");
        assertThat(captor.getValue().getAgreementBody()).isEqualTo("This agreement is entered into.");
    }

    @Test
    void saveProfile_fieldClearedOnTheForm_isActuallyCleared() {
        // An emptied box arrives as "", not null, so carrying values over must not undo it.
        InspectorProfile existing = storedProfile(1330);
        existing.setWebsite("https://www.abc.ca");

        InspectorProfile incoming = new InspectorProfile();
        incoming.setWebsite("");
        inspectorProfileService.saveProfile(incoming);

        ArgumentCaptor<InspectorProfile> captor = ArgumentCaptor.forClass(InspectorProfile.class);
        verify(inspectorProfileRepository).save(captor.capture());
        assertThat(captor.getValue().getWebsite()).isEmpty();
    }

    @Test
    void saveProfile_noExistingProfile_savesWhatCameIn() {
        when(inspectorProfileRepository.findById(1L)).thenReturn(Optional.empty());

        InspectorProfile incoming = new InspectorProfile();
        incoming.setInspectionNumber(500);

        assertThat(inspectorProfileService.saveProfile(incoming).getStatusCode().is2xxSuccessful())
                .isTrue();
        verify(inspectorProfileRepository).save(any(InspectorProfile.class));
    }
}
