package ca.inspection.home.inspection.entity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;


public class InspectionFieldTest {

    private static InspectionField fieldWithValue(String value) {
        InspectionFieldDefinitionValue selected = new InspectionFieldDefinitionValue();
        selected.setValue(value);

        InspectionField field = new InspectionField();
        field.setSelectedValue(selected);
        return field;
    }

    @Test
    void getDisplayValue_normalField_showsThePickedOption() {
        InspectionField field = fieldWithValue("Asphalt Shingles");

        assertThat(field.getDisplayValue()).isEqualTo("Asphalt Shingles");
        assertThat(field.isBlankItem()).isFalse();
    }

    @Test
    void getDisplayValue_namedBlankItem_showsTheTypedName() {
        InspectionField field = fieldWithValue("blank item");
        field.setConditionName("Cracked parging at front wall");

        assertThat(field.isBlankItem()).isTrue();
        assertThat(field.getDisplayValue()).isEqualTo("Cracked parging at front wall");
    }

    @Test
    void getDisplayValue_unnamedBlankItem_showsNothing() {
        InspectionField field = fieldWithValue("blank item");

        // "blank item" is scaffolding — printing it in a report would be noise.
        assertThat(field.getDisplayValue()).isNull();
    }

    @Test
    void getDisplayValue_blankItemWithWhitespaceName_showsNothing() {
        InspectionField field = fieldWithValue("blank item");
        field.setConditionName("   ");

        assertThat(field.getDisplayValue()).isNull();
    }

    @Test
    void getDisplayValue_trimsTheTypedName() {
        InspectionField field = fieldWithValue("blank item");
        field.setConditionName("  Loose railing  ");

        assertThat(field.getDisplayValue()).isEqualTo("Loose railing");
    }

    @Test
    void getDisplayValue_conditionNameOnANormalField_winsOverTheOption() {
        // Renaming isn't offered for normal fields, but if a name is set it is the intent.
        InspectionField field = fieldWithValue("Asphalt Shingles");
        field.setConditionName("Cedar Shakes");

        assertThat(field.getDisplayValue()).isEqualTo("Cedar Shakes");
    }

    @Test
    void getDisplayValue_noSelectedValue_isNull() {
        assertThat(new InspectionField().getDisplayValue()).isNull();
    }

    @Test
    void isBlankItem_matchIgnoresCase() {
        assertThat(fieldWithValue("Blank Item").isBlankItem()).isTrue();
        assertThat(fieldWithValue("blank item").isBlankItem()).isTrue();
        assertThat(fieldWithValue("blanket").isBlankItem()).isFalse();
    }

    @Test
    void isBlankItem_noSelectedValue_isFalse() {
        assertThat(new InspectionField().isBlankItem()).isFalse();
    }
}
