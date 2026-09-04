package ca.inspection.home.inspection.service;

import ca.inspection.home.inspection.DTO.ImageLocation;
import ca.inspection.home.inspection.DTO.NavSection;
import ca.inspection.home.inspection.entity.*;
import ca.inspection.home.inspection.repository.ImageAnnotationRepository;
import ca.inspection.home.inspection.repository.InspectionImagesRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ReportViewServiceTest {

    private static final Map<String, String> NAV_SECTION_COLOURS = new LinkedHashMap<>(){{
        put("summary",     "#943b08");
        put("roofing",     "#a68368");
        put("exterior",    "#7BB369");
        put("structure",   "#777777");
        put("electrical",  "#FFA500");
        put("heating",     "#ff6d4d");
        put("cooling",     "#399cff");
        put("insulation",  "#ffb6c1");
        put("plumbing",    "#ADD8E6");
        put("interior",    "#D2D1CD");
        put("appendix",    "#5c5a52");
    }};

    @Mock
    private InspectionImagesService inspectionImagesService;

    @Mock
    private InspectionImagesRepository inspectionImagesRepository;

    @Mock
    private ImageAnnotationRepository imageAnnotationRepository;

    @Mock
    private CompanyAssetService companyAssetService;

    @InjectMocks
    private ReportViewService reportViewService;

    // Helper Functions

    private InspectionField fieldWithTypeAndPlace(String place, String type){
        InspectionFieldDefinition definition = new InspectionFieldDefinition();
        definition.setFieldType(type);
        definition.setFieldPlace(place);

        InspectionField field = new InspectionField();
        field.setId(UUID.randomUUID());
        field.setInspectionFieldDefinition(definition);

        return field;
    }

    private InspectionField fieldWithSummary(String place,String type, boolean inSummary){
        InspectionField field = fieldWithTypeAndPlace(place, type);
        field.setIncludeInSummary(inSummary);
        field.setInspectionRecommendationField(new InspectionRecommendationField());

        return field;
    }

    // Comparator Tests

    @Test
    void getComparator_differentPlaces_sortsInOrder(){
        InspectionField field = fieldWithTypeAndPlace("roofing", "description");
        InspectionField field2 = fieldWithTypeAndPlace("plumbing", "description");
        Comparator<InspectionField> comparator = reportViewService.getComparator();

        int result = comparator.compare(field, field2);

        assertThat(result).isNegative();
    }

    @Test
    void getComparator_samePlaceDifferentType_sortsInOrder(){
        InspectionField field = fieldWithTypeAndPlace("roofing", "description");
        InspectionField field2 = fieldWithTypeAndPlace("roofing", "limitations");
        Comparator<InspectionField> comparator = reportViewService.getComparator();

        int result = comparator.compare(field, field2);

        assertThat(result).isNegative();
    }

    @Test
    void getComparator_unknownPlace_sortsToEnd(){
        InspectionField known = fieldWithTypeAndPlace("cooling", "description");
        InspectionField unknown = fieldWithTypeAndPlace("basement", "description");
        Comparator<InspectionField> comparator = reportViewService.getComparator();

        int result = comparator.compare(known, unknown);

        assertThat(result).isNegative();
    }

    @Test
    void getComparator_samePlaceAndType_isEqual(){
        InspectionField field = fieldWithTypeAndPlace("cooling", "description");
        InspectionField field2 = fieldWithTypeAndPlace("cooling", "description");
        Comparator<InspectionField> comparator = reportViewService.getComparator();

        int result = comparator.compare(field, field2);

        assertThat(result).isZero();
    }

    // Summary Field Tests

    @Test
    void getSummaryFields_wrongType_excluded() {
        InspectionField description = fieldWithSummary("roofing", "description", true);

        List<InspectionField> fields = List.of(description);
        Map<String, List<InspectionField>> result = reportViewService.getSummaryFields(fields);

        assertThat(result).isEmpty();
    }

    @Test
    void getSummaryFields_notIncludedInSummary_excluded() {
        InspectionField noSummary = fieldWithSummary("roofing", "recommendations", false);

        List<InspectionField> fields = List.of(noSummary);
        Map<String, List<InspectionField>> result = reportViewService.getSummaryFields(fields);

        assertThat(result).isEmpty();
    }

    @Test
    void getSummaryFields_noRecommendationField_excluded() {
        InspectionField noRecommendationField = fieldWithSummary("roofing", "recommendations", true);
        noRecommendationField.setInspectionRecommendationField(null);

        List<InspectionField> fields = List.of(noRecommendationField);
        Map<String, List<InspectionField>> result = reportViewService.getSummaryFields(fields);

        assertThat(result).isEmpty();
    }

    @Test
    void getSummaryFields_meetAllConditions_included() {
        InspectionField correct = fieldWithSummary("roofing", "recommendations", true);

        List<InspectionField> fields = List.of(correct);
        Map<String, List<InspectionField>> result = reportViewService.getSummaryFields(fields);

        assertThat(result).containsOnlyKeys("roofing");
        assertThat(result.get("roofing")).containsExactly(correct);
    }

    @Test
    void getSummaryFields_multiplePlaces_groupedSeparately(){
        InspectionField roofing = fieldWithSummary("roofing", "recommendations", true);
        InspectionField exterior = fieldWithSummary("exterior", "recommendations", true);

        List<InspectionField> fields = List.of(roofing, exterior);
        Map<String, List<InspectionField>> result = reportViewService.getSummaryFields(fields);

        assertThat(result).containsOnlyKeys("roofing", "exterior");
        assertThat(result.get("roofing")).containsExactly(roofing);
        assertThat(result.get("exterior")).containsExactly(exterior);
    }

    // Get all fields

    @Test
    void getAllFields_samePlaceSameType_groupedTogether(){
        InspectionField field1 = fieldWithTypeAndPlace("roofing", "description");
        InspectionField field2 = fieldWithTypeAndPlace("roofing", "description");

        List<InspectionField> fields = List.of(field1, field2);
        Map<String, Map<String, List<InspectionField>>> result = reportViewService.getAllFields(fields);

        assertThat(result).containsOnlyKeys("roofing");
        assertThat(result.get("roofing")).containsOnlyKeys("description");
        assertThat(result.get("roofing").get("description")).containsExactlyInAnyOrder(field1, field2);
    }

    @Test
    void getAllFields_samePlaceDifferentType_groupedSeparately(){
        InspectionField description = fieldWithTypeAndPlace("roofing", "description");
        InspectionField limitations = fieldWithTypeAndPlace("roofing", "limitations");

        List<InspectionField> fields = List.of(description, limitations);
        Map<String, Map<String, List<InspectionField>>> result = reportViewService.getAllFields(fields);

        assertThat(result.get("roofing")).containsOnlyKeys("description", "limitations");
        assertThat(result.get("roofing").get("description")).containsExactly(description);
        assertThat(result.get("roofing").get("limitations")).containsExactly(limitations);
    }

    @Test
    void getAllFields_differentPlace_groupedSeparately(){
        InspectionField roofing = fieldWithTypeAndPlace("roofing", "description");
        InspectionField exterior = fieldWithTypeAndPlace("exterior", "description");

        List<InspectionField> fields = List.of(roofing, exterior);
        Map<String, Map<String, List<InspectionField>>> result = reportViewService.getAllFields(fields);

        assertThat(result).containsOnlyKeys("roofing", "exterior");
        assertThat(result.get("roofing").get("description")).containsExactly(roofing);
        assertThat(result.get("exterior").get("description")).containsExactly(exterior);
    }

    private static InspectionReport reportForInspection(int inspectionNumber){
        InspectionBookings booking = new InspectionBookings();
        booking.setInspectionNumber(inspectionNumber);
        InspectionReport report = new InspectionReport();
        report.setInspectionBooking(booking);
        return report;
    }

    // Get Sorted Fields

    @Test
    void getSortedFields_fieldWithNullDefinition_filteredOut(){
        InspectionField incompleteField = new InspectionField();
        InspectionField completeField = fieldWithTypeAndPlace("roofing", "description");

        InspectionReport report = new InspectionReport();
        report.setFields(Set.of(incompleteField, completeField));

        List<InspectionField> result = reportViewService.getSortedFields(report);

        assertThat(result).containsExactly(completeField);
    }

    @Test
    void getSortedFields_fieldWithImages_convertsImageToBase64(){
        InspectionField field = fieldWithTypeAndPlace("roofing", "description");
        InspectionImage image = new InspectionImage();
        image.setId(UUID.randomUUID());
        image.setImageUrl("photo.jpg");
        field.setInspectionImages(List.of(image));

        InspectionReport report = reportForInspection(1001);
        report.setFields(Set.of(field));

        // Return mock base 64 when function called
        when(inspectionImagesService.toBase64(any(ImageLocation.class), any()))
                .thenReturn("data:image/jpeg;base64,FAKE");

        reportViewService.getSortedFields(report);

        assertThat(image.getBase64()).isEqualTo("data:image/jpeg;base64,FAKE");
    }

    @Test
    void getSortedFields_fieldWithImages_locatesImageByTheReportsInspectionNumber(){
        InspectionField field = fieldWithTypeAndPlace("roofing", "description");
        InspectionImage image = new InspectionImage();
        image.setId(UUID.randomUUID());
        image.setImageUrl("photo.jpg");
        field.setInspectionImages(List.of(image));

        InspectionReport report = reportForInspection(1001);
        report.setFields(Set.of(field));

        reportViewService.getSortedFields(report);

        // The booking is already on the report, so no extra lookup per image.
        verifyNoInteractions(inspectionImagesRepository);
        ArgumentCaptor<ImageLocation> captor = ArgumentCaptor.forClass(ImageLocation.class);
        verify(inspectionImagesService).toBase64(captor.capture(), any());
        assertThat(captor.getValue().getInspectionNumber()).isEqualTo(1001);
        assertThat(captor.getValue().getImageUrl()).isEqualTo("photo.jpg");
    }

    @Test
    void getSortedFields_bookingWithNoInspectionNumber_stillResolvesTheImage(){
        InspectionField field = fieldWithTypeAndPlace("roofing", "description");
        InspectionImage image = new InspectionImage();
        image.setId(UUID.randomUUID());
        image.setImageUrl("legacy.jpg");
        field.setInspectionImages(List.of(image));

        // Bookings predating inspection numbers must not blow up the whole render.
        InspectionReport report = new InspectionReport();
        report.setFields(Set.of(field));

        reportViewService.getSortedFields(report);

        ArgumentCaptor<ImageLocation> captor = ArgumentCaptor.forClass(ImageLocation.class);
        verify(inspectionImagesService).toBase64(captor.capture(), any());
        assertThat(captor.getValue().getInspectionNumber()).isNull();
        assertThat(captor.getValue().getImageUrl()).isEqualTo("legacy.jpg");
    }

    @Test
    void getSortedFields_fieldsWithNoImages_skipsBase64(){
        InspectionField field = fieldWithTypeAndPlace("roofing", "description");
        field.setInspectionImages(new ArrayList<>());

        InspectionReport report = new InspectionReport();
        report.setFields(Set.of(field));

        reportViewService.getSortedFields(report);

        verifyNoInteractions(inspectionImagesService);
    }

    // Get Populated Nav Sections

    @Test
    void getPopulatedNavSections_emptySummaryFields_summaryExcluded(){
        Map<String, Map<String, List<InspectionField>>> fields = Map.of();
        Map<String, List<InspectionField>> summaryFields = Map.of();

        List<NavSection> result = reportViewService.getPopulatedNavSections(fields, summaryFields, false);

        assertThat(result).extracting(NavSection::key).doesNotContain("summary");
    }

    @Test
    void getPopulatedNavSections_nullSummaryFields_summaryExcluded(){
        Map<String, Map<String, List<InspectionField>>> fields = Map.of();

        List<NavSection> result = reportViewService.getPopulatedNavSections(fields, null, false);

        assertThat(result).extracting(NavSection::key).doesNotContain("summary");
    }

    @Test
    void getPopulatedNavSections_nonEmptySummaryFields_summaryFirst(){
        Map<String, Map<String, List<InspectionField>>> fields = Map.of();
        Map<String, List<InspectionField>> summaryFields = Map.of(
                "roofing", List.of(fieldWithSummary("roofing", "recommendations", true))
        );

        List<NavSection> result = reportViewService.getPopulatedNavSections(fields, summaryFields, false);

        assertThat(result).isNotEmpty();
        assertThat(result).hasSize(1);
        NavSection summary = result.getFirst();
        assertThat(summary.key()).isEqualTo("summary");
        assertThat(summary.label()).isEqualTo("Summary");
        assertThat(summary.anchor()).isEqualTo("summary-page");
        assertThat(summary.colour()).isEqualTo("#943b08");
    }

    @Test
    void getPopulatedNavSections_noAppendix_appendixExcluded(){
        Map<String, Map<String, List<InspectionField>>> fields = Map.of();
        Map<String, List<InspectionField>> summaryFields = Map.of();

        List<NavSection> result = reportViewService.getPopulatedNavSections(fields, summaryFields, false);

        assertThat(result).extracting(NavSection::key).doesNotContain("appendix");
    }

    @Test
    void getPopulatedNavSections_hasAppendix_appendixIncludedLast(){
        Map<String, Map<String, List<InspectionField>>> fields = Map.of(
                "roofing",
                Map.of("description",
                        List.of(fieldWithSummary("roofing", "description", false)))
        );
        Map<String, List<InspectionField>> summaryFields = Map.of();

        List<NavSection> result = reportViewService.getPopulatedNavSections(fields, summaryFields, true);

        assertThat(result).isNotEmpty();
        NavSection appendix = result.getLast();
        assertThat(appendix.key()).isEqualTo("appendix");
        assertThat(appendix.label()).isEqualTo("Appendix");
        assertThat(appendix.anchor()).isEqualTo("appendix-page");
        assertThat(appendix.colour()).isEqualTo("#5c5a52");
    }

    @Test
    void getPopulatedNavSections_allFields_onlyContainsFieldsIncluded(){
        Map<String, Map<String, List<InspectionField>>> fields = Map.of(
                "roofing",
                Map.of("description",
                        List.of(fieldWithSummary("roofing", "description", false)))
        );
        Map<String, List<InspectionField>> summaryFields = Map.of();

        List<NavSection> result = reportViewService.getPopulatedNavSections(fields, summaryFields, false);

        assertThat(result).extracting(NavSection::key).containsOnly("roofing");
    }

    @Test
    void getPopulatedNavSections_placeInAllFields_containsCorrectInformation(){
        Map<String, Map<String, List<InspectionField>>> fields = Map.of(
                "roofing",
                Map.of("description",
                        List.of(fieldWithSummary("roofing", "description", false)))
        );
        Map<String, List<InspectionField>> summaryFields = Map.of();

        List<NavSection> result = reportViewService.getPopulatedNavSections(fields, summaryFields, false);

        assertThat(result).hasSize(1);
        NavSection appendix = result.getFirst();
        assertThat(appendix.key()).isEqualTo("roofing");
        assertThat(appendix.label()).isEqualTo("Roofing");
        assertThat(appendix.anchor()).isEqualTo("place-roofing");
        assertThat(appendix.colour()).isEqualTo("#a68368");
    }

    @Test
    void getPopulatedNavSections_multiplePlaces_orderedCorrectly(){
        Map<String, Map<String, List<InspectionField>>> fields = Map.of(
                "roofing",
                Map.of("description",
                        List.of(fieldWithSummary("roofing", "description", false))),
                "interior",
                Map.of("description",
                        List.of(fieldWithSummary("interior", "description", false)))
        );
        Map<String, List<InspectionField>> summaryFields = Map.of();

        List<NavSection> result = reportViewService.getPopulatedNavSections(fields, summaryFields, false);

        assertThat(result).extracting(NavSection::key).containsExactly("roofing", "interior");
    }

    @Test
    void getPopulatedNavSections_summaryPlacesAndAppendix_fullOrdering(){
        Map<String, Map<String, List<InspectionField>>> fields = Map.of(
                "roofing",
                Map.of("description",
                        List.of(fieldWithSummary("roofing", "description", false)))
        );
        Map<String, List<InspectionField>> summaryFields = Map.of(
                "roofing", List.of(fieldWithSummary("roofing", "recommendations", true))
        );

        List<NavSection> result = reportViewService.getPopulatedNavSections(fields, summaryFields, true);

        assertThat(result).extracting(NavSection::key).containsExactly("summary", "roofing", "appendix");
    }

    // Build Colour Variable CSS tests

    @Test
    void buildColourVariableCSS_startsWithRootSelector(){
        String result = reportViewService.buildColourVariablesCSS();

        assertThat(result).startsWith(":root {");
    }

    @Test
    void buildColourVariableCSS_endsWithClosingBrace(){
        String result = reportViewService.buildColourVariablesCSS();

        assertThat(result).endsWith("}\n");
    }

    @Test
    void buildColourVariableCSS_containsEntryForEachColour(){
        String result = reportViewService.buildColourVariablesCSS();

        NAV_SECTION_COLOURS.forEach((key, colour) -> {
            assertThat(result).contains("--place-" + key + ": " + colour + ";");
        });
    }

    // Build Nav Page CSS

    @Test
    void buildNavPageCSS_containsPageRuleForEachKey(){
        String result = reportViewService.buildNavPageCSS();

        NAV_SECTION_COLOURS.keySet().forEach(key -> {
            assertThat(result).contains("@page " + key + "-page {");
        });
    }

    @Test
    void buildNavPageCSS_containsRunningElementForEachKey(){
        String result = reportViewService.buildNavPageCSS();

        NAV_SECTION_COLOURS.keySet().forEach(key -> {
            assertThat(result).contains("element(nav-" + key + ")");
        });
    }

    @Test
    void buildNavPageCSS_containHeaderBlockForEachKey(){
        String result = reportViewService.buildNavPageCSS();

        NAV_SECTION_COLOURS.keySet().forEach(key -> {
            assertThat(result).contains(".report-header-block-" + key + " { position: running(nav-" + key + ");");
        });
    }

    // Get Other Fields

    private InspectionImage imageForField(InspectionField field){
        InspectionImage image = new InspectionImage();
        image.setId(UUID.randomUUID());
        image.setInspectionField(field);
        return image;
    }

    private ImageAnnotation annotationForImage(InspectionImage image){
        ImageAnnotation annotation = new ImageAnnotation();
        annotation.setId(UUID.randomUUID());
        annotation.setInspectionImage(image);
        return annotation;
    }

    @Test
    void getOtherFields_noFields_noCallRepositories(){
        InspectionReport report = new InspectionReport();
        report.setFields(Set.of());

        reportViewService.getOtherFields(report);

        verifyNoInteractions(inspectionImagesRepository);
        verifyNoInteractions(imageAnnotationRepository);
    }

    @Test
    void getOtherFields_imagesWithAnnotations_correctAnnotationsAttached(){
        InspectionReport report = new InspectionReport();
        InspectionField field = fieldWithTypeAndPlace("roofing", "description");
        InspectionImage image = imageForField(field);
        ImageAnnotation annotation = annotationForImage(image);
        report.setFields(Set.of(field));

        when(inspectionImagesRepository.findByInspectionField_IdIn(anyList())).thenReturn(List.of(image));
        when(imageAnnotationRepository.findByInspectionImageIdIn(anyList())).thenReturn(List.of(annotation));
        reportViewService.getOtherFields(report);

        assertThat(image.getAnnotations()).containsExactly(annotation);
    }

    @Test
    void getOtherFields_multipleFields_imagesByFieldId(){
        InspectionReport report = new InspectionReport();
        InspectionField roofing = fieldWithTypeAndPlace("roofing", "description");
        InspectionField exterior = fieldWithTypeAndPlace("exterior", "description");
        InspectionImage roofingImage = imageForField(roofing);
        InspectionImage exteriorImage = imageForField(exterior);
        report.setFields(Set.of(roofing, exterior));

        when(inspectionImagesRepository.findByInspectionField_IdIn(anyList())).thenReturn(List.of(roofingImage, exteriorImage));
        reportViewService.getOtherFields(report);

        assertThat(roofing.getInspectionImages()).containsExactly(roofingImage);
        assertThat(exterior.getInspectionImages()).containsExactly(exteriorImage);
    }

    @Test
    void getOtherFields_multipleImages_annotationsByImage(){
        InspectionReport report = new InspectionReport();
        InspectionField field = fieldWithTypeAndPlace("roofing", "description");
        InspectionImage image1 = imageForField(field);
        InspectionImage image2 = imageForField(field);
        ImageAnnotation annotation1 = annotationForImage(image1);
        ImageAnnotation annotation2 = annotationForImage(image2);
        report.setFields(Set.of(field));

        when(inspectionImagesRepository.findByInspectionField_IdIn(anyList()))
                .thenReturn(List.of(image1, image2));
        when(imageAnnotationRepository.findByInspectionImageIdIn(anyList()))
                .thenReturn(List.of(annotation1, annotation2));
        reportViewService.getOtherFields(report);

        assertThat(image1.getAnnotations()).containsExactly(annotation1);
        assertThat(image2.getAnnotations()).containsExactly(annotation2);
    }

    @Test
    void getOtherFields_fieldWithNoImage_getsEmptyList(){
        InspectionReport report = new InspectionReport();
        InspectionField fieldImages = fieldWithTypeAndPlace("roofing", "description");
        InspectionField fieldNoImages = fieldWithTypeAndPlace("exterior", "description");
        InspectionImage image = imageForField(fieldImages);
        report.setFields(Set.of(fieldNoImages, fieldImages));

        when(inspectionImagesRepository.findByInspectionField_IdIn(anyList()))
                .thenReturn(List.of(image));
        reportViewService.getOtherFields(report);

        assertThat(fieldImages.getInspectionImages()).containsExactly(image);
        assertThat(fieldNoImages.getInspectionImages()).isEmpty();
    }

    // Set Cover Page Image Base 64

    @Test
    void setCoverPageImageBase64_hasCoverImage_setsBase64FromService(){
        InspectionImage cover = new InspectionImage();
        cover.setImageUrl("cover.jpg");
        InspectionReport report = reportForInspection(1001);
        report.setCoverPageImage(cover);

        when(inspectionImagesService.toBase64(any(ImageLocation.class), isNull()))
                .thenReturn("data:image/jpeg;base64,COVER");

        reportViewService.setCoverPageImageBase64(report);

        assertThat(cover.getBase64()).isEqualTo("data:image/jpeg;base64,COVER");
        ArgumentCaptor<ImageLocation> captor = ArgumentCaptor.forClass(ImageLocation.class);
        verify(inspectionImagesService).toBase64(captor.capture(), isNull());
        assertThat(captor.getValue().getInspectionNumber()).isEqualTo(1001);
        assertThat(captor.getValue().getImageUrl()).isEqualTo("cover.jpg");
    }

    @Test
    void setCoverPageImageBase64_noCoverImage_doesNothing(){
        InspectionReport report = new InspectionReport();
        report.setCoverPageImage(null);

        reportViewService.setCoverPageImageBase64(report);

        verifyNoInteractions(inspectionImagesService);
    }

    // Get Company Assets Base 64

    @Test
    void getCompanyAssetsBase64_noAssets_returnsEmptyList(){
        when(companyAssetService.getAllAssets()).thenReturn(List.of());

        List<String> result = reportViewService.getCompanyAssetsBase64();

        assertThat(result).isEmpty();
    }

    @Test
    void getCompanyAssetsBase64_multipleAssets_returnsBase64ForEach(){
        CompanyAsset a = new CompanyAsset();
        a.setKey("logo");
        CompanyAsset b = new CompanyAsset();
        b.setKey("stamp");

        when(companyAssetService.getAllAssets()).thenReturn(List.of(a, b));
        when(companyAssetService.toBase64(a)).thenReturn("data:A");
        when(companyAssetService.toBase64(b)).thenReturn("data:B");

        List<String> result = reportViewService.getCompanyAssetsBase64();

        assertThat(result).containsExactly("data:A", "data:B");
    }

    @Test
    void getCompanyAssetsBase64_missingFileYieldsNull_preservesNullInList(){
        CompanyAsset a = new CompanyAsset();

        when(companyAssetService.getAllAssets()).thenReturn(List.of(a));
        when(companyAssetService.toBase64(a)).thenReturn(null);

        List<String> result = reportViewService.getCompanyAssetsBase64();

        assertThat(result).containsExactly((String) null);
    }
}
