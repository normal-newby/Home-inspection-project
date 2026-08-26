package ca.inspection.home.inspection.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

public class ArrowGeometryTest {

    private static final double TOLERANCE = 0.0001;

    // LENGTH

    @Test
    void of_diagonalDrag_lengthIsTheDistanceDraggedNotTheLargerAxis() {
        ArrowGeometry arrow = ArrowGeometry.of(100, 100, 3, 1);

        assertThat(arrow.length()).isCloseTo(Math.hypot(100, 100), within(TOLERANCE));
        assertThat(arrow.length()).isGreaterThan(100);
    }

    @Test
    void of_straightDrag_lengthIsTheDragItself() {
        assertThat(ArrowGeometry.of(0, -80, 3, 1).length()).isCloseTo(80, within(TOLERANCE));
    }

    @Test
    void of_shaftPlusHeadCoverTheWholeArrow() {
        ArrowGeometry arrow = ArrowGeometry.of(150, 40, 4, 1);

        // The tip has to land exactly on the point that was dragged to.
        assertThat(arrow.shaftLength() + arrow.headLength()).isCloseTo(arrow.length(), within(TOLERANCE));
    }

    @Test
    void of_anglePointsAtTheDraggedDirection() {
        assertThat(ArrowGeometry.of(10, 10, 3, 1).angle()).isCloseTo(Math.PI / 4, within(TOLERANCE));
        assertThat(ArrowGeometry.of(-10, 0, 3, 1).angle()).isCloseTo(Math.PI, within(TOLERANCE));
    }

    // SIZE COMES FROM THE SLIDER, NOT THE LENGTH

    @Test
    void of_thicknessFollowsTheSizeSlider() {
        ArrowGeometry thin = ArrowGeometry.of(200, 0, 1, 1);
        ArrowGeometry thick = ArrowGeometry.of(200, 0, 5, 1);

        assertThat(thin.shaftWidth()).isCloseTo(5, within(TOLERANCE));
        assertThat(thick.shaftWidth()).isCloseTo(25, within(TOLERANCE));
    }

    @Test
    void of_smallestSize_isStillThickEnoughToSeeOnAPhoto() {
        // The whole point of the slider driving arrows: at 2px per step the shaft vanished
        // against the image, which is what "the shafts have disappeared" meant.
        assertThat(ArrowGeometry.of(200, 0, 1, 1).shaftWidth()).isGreaterThanOrEqualTo(4);
    }

    @Test
    void of_longerArrowAtTheSameSize_isNotThicker() {
        // The old code derived thickness from length, so long arrows came out enormous.
        ArrowGeometry shortArrow = ArrowGeometry.of(50, 0, 3, 1);
        ArrowGeometry longArrow = ArrowGeometry.of(600, 0, 3, 1);

        assertThat(longArrow.shaftWidth()).isEqualTo(shortArrow.shaftWidth());
        assertThat(longArrow.headHalfWidth()).isEqualTo(shortArrow.headHalfWidth());
    }

    @Test
    void of_headScalesWithTheSizeSliderToo() {
        ArrowGeometry arrow = ArrowGeometry.of(400, 0, 3, 1);

        double shaftWidth = 3 * ArrowGeometry.SHAFT_WIDTH_PER_STEP;
        assertThat(arrow.headLength())
                .isCloseTo(shaftWidth * ArrowGeometry.HEAD_LENGTH_RATIO, within(TOLERANCE));
        assertThat(arrow.headHalfWidth())
                .isCloseTo(shaftWidth * ArrowGeometry.HEAD_WIDTH_RATIO, within(TOLERANCE));
    }

    @Test
    void of_missingOrZeroSize_fallsBackToOneStep() {
        // Annotations saved before the size slider drove arrows come through as 1.
        assertThat(ArrowGeometry.of(100, 0, 0, 1).shaftWidth())
                .isCloseTo(ArrowGeometry.SHAFT_WIDTH_PER_STEP, within(TOLERANCE));
    }

    // SHORT ARROWS

    @Test
    void of_shortArrow_headIsCappedSoAShaftRemains() {
        // At size 10 the head alone would want 130px; the arrow is only 20px long.
        ArrowGeometry arrow = ArrowGeometry.of(20, 0, 10, 1);

        assertThat(arrow.headLength()).isCloseTo(20 * ArrowGeometry.MAX_HEAD_SHARE, within(TOLERANCE));
        assertThat(arrow.shaftLength()).isGreaterThan(0);
    }

    @Test
    void of_clickWithoutADrag_isDegenerate() {
        assertThat(ArrowGeometry.of(0, 0, 3, 1).isDegenerate()).isTrue();
        assertThat(ArrowGeometry.of(30, 30, 3, 1).isDegenerate()).isFalse();
    }

    // FIXED LENGTH: THE ARROW IS ITS OWN HEAD

    @Test
    void of_fixedLength_isHalfHeadHalfShaft() {
        ArrowGeometry arrow = ArrowGeometry.of(100, 0, 3, 1, true);

        // |head| == |shaft|, so a fixed arrow still reads as an arrow.
        assertThat(arrow.shaftLength()).isCloseTo(arrow.headLength(), within(TOLERANCE));
        assertThat(arrow.length()).isCloseTo(arrow.headLength() * 2, within(TOLERANCE));
        assertThat(arrow.shaftLength()).isGreaterThan(0);
    }

    @Test
    void of_fixedLength_ignoresHowFarTheDragWent() {
        ArrowGeometry shortDrag = ArrowGeometry.of(20, 0, 3, 1, true);
        ArrowGeometry longDrag = ArrowGeometry.of(600, 0, 3, 1, true);

        // The drag only aims a fixed-length arrow; it never stretches it.
        assertThat(shortDrag.length()).isCloseTo(longDrag.length(), within(TOLERANCE));
    }

    @Test
    void of_fixedLength_takesItsLengthFromTheSizeSlider() {
        ArrowGeometry small = ArrowGeometry.of(100, 0, 2, 1, true);
        ArrowGeometry large = ArrowGeometry.of(100, 0, 6, 1, true);

        assertThat(small.length()).isCloseTo(ArrowGeometry.fixedLengthFor(2, 1), within(TOLERANCE));
        assertThat(large.length()).isCloseTo(small.length() * 3, within(TOLERANCE));
    }

    @Test
    void of_fixedLength_stillPointsWhereTheDragWent() {
        assertThat(ArrowGeometry.of(-40, -40, 3, 1, true).angle())
                .isCloseTo(-3 * Math.PI / 4, within(TOLERANCE));
    }

    @Test
    void of_fixedLength_scalesIntoTheReportImage() {
        ArrowGeometry onScreen = ArrowGeometry.of(100, 0, 3, 1, true);
        ArrowGeometry inReport = ArrowGeometry.of(100, 0, 3, 4, true);

        assertThat(inReport.length()).isCloseTo(onScreen.length() * 4, within(TOLERANCE));
        assertThat(inReport.headHalfWidth()).isCloseTo(onScreen.headHalfWidth() * 4, within(TOLERANCE));
        assertThat(inReport.shaftLength()).isCloseTo(onScreen.shaftLength() * 4, within(TOLERANCE));
    }

    @Test
    void of_fixedLength_withoutADirection_isDegenerate() {
        // A click that never moved has nothing to aim at.
        assertThat(ArrowGeometry.of(0, 0, 3, 1, true).isDegenerate()).isTrue();
    }

    @Test
    void fixedLengthFor_matchesTheLengthOfAFixedArrow() {
        // The drawing tool cuts the drag back to this length, so the stored arrow and the
        // drawn arrow are the same thing.
        assertThat(ArrowGeometry.fixedLengthFor(5, 1))
                .isCloseTo(ArrowGeometry.of(999, 0, 5, 1, true).length(), within(TOLERANCE));
    }

    @Test
    void of_freeArrow_isUnaffectedByTheFixedLengthOption() {
        ArrowGeometry free = ArrowGeometry.of(300, 0, 3, 1, false);

        assertThat(free.length()).isCloseTo(300, within(TOLERANCE));
        assertThat(free.shaftLength()).isGreaterThan(0);
    }

    // SCALING INTO THE FULL SIZE IMAGE

    @Test
    void of_scale_growsThicknessButNotTheDraggedLength() {
        // Coordinates are already scaled by the caller; only the slider-driven parts scale here.
        ArrowGeometry onScreen = ArrowGeometry.of(100, 0, 3, 1);
        ArrowGeometry inReport = ArrowGeometry.of(100, 0, 3, 4);

        assertThat(inReport.length()).isCloseTo(onScreen.length(), within(TOLERANCE));
        assertThat(inReport.shaftWidth()).isCloseTo(onScreen.shaftWidth() * 4, within(TOLERANCE));
        assertThat(inReport.headHalfWidth()).isCloseTo(onScreen.headHalfWidth() * 4, within(TOLERANCE));
    }

    @Test
    void of_sameProportionsAtEveryScale() {
        // A 4x image should print an arrow that looks identical, just bigger.
        ArrowGeometry onScreen = ArrowGeometry.of(200, 0, 3, 1);
        ArrowGeometry inReport = ArrowGeometry.of(800, 0, 3, 4);

        assertThat(inReport.shaftWidth() / inReport.length())
                .isCloseTo(onScreen.shaftWidth() / onScreen.length(), within(TOLERANCE));
        assertThat(inReport.headLength() / inReport.length())
                .isCloseTo(onScreen.headLength() / onScreen.length(), within(TOLERANCE));
    }
}
