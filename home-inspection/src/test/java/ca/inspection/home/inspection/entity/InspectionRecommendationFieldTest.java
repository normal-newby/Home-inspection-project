package ca.inspection.home.inspection.entity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

public class InspectionRecommendationFieldTest {

    @Test
    void getLocationDisplay_bothDirectionAndFloorLevel_displayedCorrectly(){
        InspectionRecommendationField field = new InspectionRecommendationField();
        field.setDirection("North");
        field.setFloorLevel("2nd Floor");

        String display = field.getLocationDisplay();

        assertThat(display).isEqualTo("North, 2nd Floor");
    }

    @Test
    void getLocationDisplay_directionOnly_displayedCorrectly(){
        InspectionRecommendationField field = new InspectionRecommendationField();
        field.setDirection("North");

        String display = field.getLocationDisplay();

        assertThat(display).isEqualTo("North");
    }

    @Test
    void getLocationDisplay_floorLevelOnly_displayedCorrectly(){
        InspectionRecommendationField field = new InspectionRecommendationField();
        field.setFloorLevel("2nd Floor");

        String display = field.getLocationDisplay();

        assertThat(display).isEqualTo("2nd Floor");
    }

    @Test
    void getLocationDisplay_bothNull_returnsNull(){
        InspectionRecommendationField field = new InspectionRecommendationField();

        String display = field.getLocationDisplay();

        assertThat(display).isNull();
    }

    @Test
    void getLocationDisplay_bothBlank_returnsNull(){
        InspectionRecommendationField field = new InspectionRecommendationField();
        field.setDirection("");
        field.setFloorLevel("");

        String display = field.getLocationDisplay();

        assertThat(display).isNull();
    }
}
