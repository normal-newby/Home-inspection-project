package ca.inspection.home.inspection.DTO;

import ca.inspection.home.inspection.entity.InspectionField;
import ca.inspection.home.inspection.entity.InspectionFieldDefinition;
import ca.inspection.home.inspection.entity.InspectionImage;
import ca.inspection.home.inspection.entity.InspectionRecommendationField;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

public class FieldGroupTest {

    private static InspectionFieldDefinition definition(String type) {
        InspectionFieldDefinition definition = new InspectionFieldDefinition();
        definition.setId(UUID.randomUUID());
        definition.setFieldName("Roof Covering");
        definition.setFieldPlace("roofing");
        definition.setFieldType(type);
        return definition;
    }

    private static InspectionField fieldValued(String value) {
        InspectionField field = new InspectionField();
        field.setId(UUID.randomUUID());
        field.setConditionName(value);
        return field;
    }

    private static FieldGroup groupOf(String type, InspectionField... fields) {
        return new FieldGroup(definition(type), List.of(fields));
    }

    // DISPLAY VALUES

    @Test
    void getDisplayValues_singleEntry_isJustThatValue() {
        FieldGroup group = groupOf("description", fieldValued("Asphalt shingle"));

        assertThat(group.getDisplayValues()).isEqualTo("Asphalt shingle");
    }

    @Test
    void getDisplayValues_severalEntries_joinsThemWithCommas() {
        FieldGroup group = groupOf("description",
                fieldValued("Asphalt shingle"), fieldValued("Metal"), fieldValued("Slate"));

        assertThat(group.getDisplayValues()).isEqualTo("Asphalt shingle, Metal, Slate");
    }

    @Test
    void getDisplayValues_keepsTheOrderTheEntriesWereAddedIn() {
        FieldGroup group = groupOf("limitations", fieldValued("Snow cover"), fieldValued("Access"));

        assertThat(group.getDisplayValues()).isEqualTo("Snow cover, Access");
    }

    @Test
    void getDisplayValues_blankAndMissingValues_areSkippedRatherThanLeavingGaps() {
        FieldGroup group = groupOf("description",
                fieldValued("Asphalt shingle"), fieldValued("   "), fieldValued(null), fieldValued("Metal"));

        assertThat(group.getDisplayValues()).isEqualTo("Asphalt shingle, Metal");
    }

    @Test
    void getDisplayValues_noEntryHasAValue_isNullSoTheLineIsSkipped() {
        FieldGroup group = groupOf("description", fieldValued(null), fieldValued("  "));

        assertThat(group.getDisplayValues()).isNull();
    }

    @Test
    void getDisplayValues_recommendations_areNotJoined() {
        // Each recommendation pairs its condition with its own location and cost, so merging
        // the conditions onto one line would detach them from the rest of the entry.
        FieldGroup group = groupOf("recommendations", fieldValued("Poor"), fieldValued("Fair"));

        assertThat(group.getDisplayValues()).isNull();
    }

    // GROUPED / DETAILED

    @Test
    void isGrouped_onlyWhenTheDefinitionWasUsedMoreThanOnce() {
        assertThat(groupOf("description", fieldValued("Metal")).isGrouped()).isFalse();
        assertThat(groupOf("description", fieldValued("Metal"), fieldValued("Slate")).isGrouped()).isTrue();
    }

    @Test
    void isDetailed_entryWithAValue_hasContentUnderTheHeading() {
        assertThat(groupOf("description", fieldValued("Metal")).isDetailed()).isTrue();
    }

    @Test
    void isDetailed_bareEntry_keepsTheBorderlessHeading() {
        assertThat(groupOf("description", fieldValued(null)).isDetailed()).isFalse();
    }

    @Test
    void isDetailed_entryWithOnlyANote_stillCountsAsDetailed() {
        InspectionField field = fieldValued(null);
        field.setNote("Moss on the north slope");

        assertThat(groupOf("description", field).isDetailed()).isTrue();
    }

    @Test
    void isDetailed_entryWithOnlyAPhoto_stillCountsAsDetailed() {
        InspectionField field = fieldValued(null);
        field.setInspectionImages(List.of(new InspectionImage()));

        assertThat(groupOf("description", field).isDetailed()).isTrue();
    }

    @Test
    void isDetailed_recommendationWithNoValue_stillCountsAsDetailed() {
        InspectionField field = fieldValued(null);
        field.setInspectionRecommendationField(new InspectionRecommendationField());

        assertThat(groupOf("recommendations", field).isDetailed()).isTrue();
    }

    // MATCHING

    @Test
    void matches_sameDefinitionId_isTrue() {
        InspectionFieldDefinition definition = definition("description");
        FieldGroup group = new FieldGroup(definition, List.of(fieldValued("Metal")));

        InspectionFieldDefinition sameRow = new InspectionFieldDefinition();
        sameRow.setId(definition.getId());

        assertThat(group.matches(sameRow)).isTrue();
    }

    @Test
    void matches_differentDefinitionId_isFalse() {
        FieldGroup group = groupOf("description", fieldValued("Metal"));

        assertThat(group.matches(definition("description"))).isFalse();
    }

    @Test
    void matches_unsavedDefinitions_fallBackToIdentity() {
        InspectionFieldDefinition unsaved = new InspectionFieldDefinition();
        FieldGroup group = new FieldGroup(unsaved, List.of(fieldValued("Metal")));

        assertThat(group.matches(unsaved)).isTrue();
        assertThat(group.matches(new InspectionFieldDefinition())).isFalse();
    }
}
