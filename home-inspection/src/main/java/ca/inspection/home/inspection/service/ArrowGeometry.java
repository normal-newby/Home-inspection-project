package ca.inspection.home.inspection.service;

// Mirrors image annotation js
public record ArrowGeometry(
        double length,
        double angle,
        double shaftLength,
        double shaftWidth,
        double headLength,
        double headHalfWidth
) {

    public static final double SHAFT_WIDTH_PER_STEP = 5.0;
    public static final double HEAD_LENGTH_RATIO = 2.6;
    public static final double HEAD_WIDTH_RATIO = 1.6;
    public static final double MAX_HEAD_SHARE = 0.6;
    public static final double FIXED_LENGTH_HEAD_MULTIPLE = 2.0;
    public static ArrowGeometry of(double dx, double dy, double sizeSteps, double scale) {
        return of(dx, dy, sizeSteps, scale, false);
    }

    public static ArrowGeometry of(double dx, double dy, double sizeSteps, double scale, boolean fixedLength) {
        double angle = Math.atan2(dy, dx);

        double size = sizeSteps <= 0 ? 1 : sizeSteps;
        double shaftWidth = size * SHAFT_WIDTH_PER_STEP * scale;
        double headHalfWidth = shaftWidth * HEAD_WIDTH_RATIO;

        if (fixedLength) {
            if (Math.hypot(dx, dy) == 0) {
                return new ArrowGeometry(0, angle, 0, shaftWidth, 0, headHalfWidth);
            }
            double headLength = shaftWidth * HEAD_LENGTH_RATIO;
            return new ArrowGeometry(headLength * FIXED_LENGTH_HEAD_MULTIPLE, angle,
                    headLength * (FIXED_LENGTH_HEAD_MULTIPLE - 1), shaftWidth, headLength, headHalfWidth);
        }

        double length = Math.hypot(dx, dy);
        double headLength = Math.min(shaftWidth * HEAD_LENGTH_RATIO, length * MAX_HEAD_SHARE);

        return new ArrowGeometry(length, angle, length - headLength, shaftWidth, headLength, headHalfWidth);
    }

    public static double fixedLengthFor(double sizeSteps, double scale) {
        double size = sizeSteps <= 0 ? 1 : sizeSteps;
        return size * SHAFT_WIDTH_PER_STEP * scale * HEAD_LENGTH_RATIO * FIXED_LENGTH_HEAD_MULTIPLE;
    }

    public boolean isDegenerate() {
        return length < 1;
    }
}
