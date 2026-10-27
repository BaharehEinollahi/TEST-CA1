package autopilot.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class SteeringControllerTest {

    private static final double NORMAL_ANGLE = 50.0;
    private static final double NORMAL_SPEED = 50.0;
    private static final double LARGE_ANGLE = 100.0;
    private static final double EXCESSIVE_LOW_SPEED_ANGLE = 500.0;
    private static final double EXCESSIVE_HIGH_SPEED_ANGLE = 150.0;
    private static final double HIGH_SPEED = 130.0;
    private static final double EXACT_HIGH_SPEED_THRESHOLD = 120.0;

    private static final double MAX_TICK_CHANGE = 60.0;
    private static final double MAX_PHYSICAL_LIMIT = 450.0;
    private static final double MAX_HIGH_SPEED_LIMIT = 90.0;

    private static final double CENTER_TICK_TARGET = 40.0;
    private static final double ZERO_ANGLE = 0.0;

    private SteeringController controller;

    @BeforeEach
    void setUp() {
        controller = new SteeringController();
    }

    // --- MOVEMENT & TICK LIMIT TESTS ---

    @Test
    @DisplayName("Update within tick limit moves current angle instantly to target")
    void update_withinTickLimit_reachesTargetInstantly() {
        double newAngle = controller.update(NORMAL_ANGLE, NORMAL_SPEED);

        assertEquals(NORMAL_ANGLE, newAngle);
        assertEquals(NORMAL_ANGLE, controller.getCurrentAngleDegrees());
        assertEquals(NORMAL_ANGLE, controller.getTargetAngleDegrees());
    }

    @Test
    @DisplayName("Update exceeding tick limit caps movement to 60 degrees per tick (Positive)")
    void update_exceedsTickLimitPositive_movesByMaxChange() {
        double firstUpdate = controller.update(LARGE_ANGLE, NORMAL_SPEED);
        assertEquals(MAX_TICK_CHANGE, firstUpdate);
        assertEquals(LARGE_ANGLE, controller.getTargetAngleDegrees());

        double secondUpdate = controller.update(LARGE_ANGLE, NORMAL_SPEED);
        assertEquals(LARGE_ANGLE, secondUpdate);
    }

    @Test
    @DisplayName("Update exceeding tick limit caps movement to 60 degrees per tick (Negative)")
    void update_exceedsTickLimitNegative_movesByMaxChange() {
        double firstUpdate = controller.update(-LARGE_ANGLE, NORMAL_SPEED);
        assertEquals(-MAX_TICK_CHANGE, firstUpdate);
        assertEquals(-LARGE_ANGLE, controller.getTargetAngleDegrees());

        double secondUpdate = controller.update(-LARGE_ANGLE, NORMAL_SPEED);
        assertEquals(-LARGE_ANGLE, secondUpdate);
    }

    // --- SPEED LIMIT CLAMPING TESTS ---

    @Test
    @DisplayName("Low speed requested angle is clamped to positive physical limit (450)")
    void update_lowSpeed_clampsToPositivePhysicalLimit() {
        controller.update(EXCESSIVE_LOW_SPEED_ANGLE, NORMAL_SPEED);
        assertEquals(MAX_PHYSICAL_LIMIT, controller.getTargetAngleDegrees());
    }

    @Test
    @DisplayName("Low speed requested angle is clamped to negative physical limit (-450)")
    void update_lowSpeed_clampsToNegativePhysicalLimit() {
        controller.update(-EXCESSIVE_LOW_SPEED_ANGLE, NORMAL_SPEED);
        assertEquals(-MAX_PHYSICAL_LIMIT, controller.getTargetAngleDegrees());
    }

    @Test
    @DisplayName("High speed requested angle is clamped to positive high speed limit (90)")
    void update_highSpeed_clampsToPositiveHighSpeedLimit() {
        controller.update(EXCESSIVE_HIGH_SPEED_ANGLE, HIGH_SPEED);
        assertEquals(MAX_HIGH_SPEED_LIMIT, controller.getTargetAngleDegrees());
    }

    @Test
    @DisplayName("High speed requested angle is clamped to negative high speed limit (-90)")
    void update_highSpeed_clampsToNegativeHighSpeedLimit() {
        controller.update(-EXCESSIVE_HIGH_SPEED_ANGLE, HIGH_SPEED);
        assertEquals(-MAX_HIGH_SPEED_LIMIT, controller.getTargetAngleDegrees());
    }

    @Test
    @DisplayName("Speed exactly at high speed threshold (120) uses the wider physical limit")
    void update_atExactSpeedThreshold_usesPhysicalLimit() {
        controller.update(EXCESSIVE_HIGH_SPEED_ANGLE, EXACT_HIGH_SPEED_THRESHOLD);
        assertEquals(EXCESSIVE_HIGH_SPEED_ANGLE, controller.getTargetAngleDegrees());
    }

    // --- SELF CENTERING TESTS ---

    @Test
    @DisplayName("Self center targets 0.0 and obeys tick movement limits from a positive angle")
    void selfCenter_fromPositiveAngle_movesTowardsZero() {
        controller.update(LARGE_ANGLE, NORMAL_SPEED);
        controller.update(LARGE_ANGLE, NORMAL_SPEED);

        double centerTick = controller.selfCenter();
        assertEquals(CENTER_TICK_TARGET, centerTick);
        assertEquals(ZERO_ANGLE, controller.getTargetAngleDegrees());

        double finalTick = controller.selfCenter();
        assertEquals(ZERO_ANGLE, finalTick);
    }

    @Test
    @DisplayName("Self center targets 0.0 and obeys tick movement limits from a negative angle")
    void selfCenter_fromNegativeAngle_movesTowardsZero() {
        controller.update(-LARGE_ANGLE, NORMAL_SPEED);
        controller.update(-LARGE_ANGLE, NORMAL_SPEED);

        double centerTick = controller.selfCenter();
        assertEquals(-CENTER_TICK_TARGET, centerTick);
        assertEquals(ZERO_ANGLE, controller.getTargetAngleDegrees());
    }

    // --- VALIDATION & EXCEPTIONS TESTS ---

    @ParameterizedTest
    @ValueSource(doubles = {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
    @DisplayName("Non-finite angles throw IllegalArgumentException")
    void update_invalidAngle_throwsIllegalArgumentException(double invalidAngle) {
        assertThrows(IllegalArgumentException.class, () -> controller.update(invalidAngle, NORMAL_SPEED));
    }

    @ParameterizedTest
    @ValueSource(doubles = {-1.0, -0.1, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
    @DisplayName("Invalid speed values throw IllegalArgumentException")
    void update_invalidSpeed_throwsIllegalArgumentException(double invalidSpeed) {
        assertThrows(IllegalArgumentException.class, () -> controller.update(30.0, invalidSpeed));
    }
}