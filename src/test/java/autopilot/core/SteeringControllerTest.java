package autopilot.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class SteeringControllerTest {

    private SteeringController controller;

    @BeforeEach
    void setUp() {
        controller = new SteeringController();
    }

    // --- INITIALIZATION TESTS ---

    @Test
    @DisplayName("Initial state should have 0 degrees for both current and target angles")
    void initialState_isZero() {
        assertEquals(0.0, controller.getCurrentAngleDegrees());
        assertEquals(0.0, controller.getTargetAngleDegrees());
    }

    // --- MOVEMENT & TICK LIMIT TESTS ---

    @Test
    @DisplayName("Update within tick limit moves current angle instantly to target")
    void update_withinTickLimit_reachesTargetInstantly() {
        double newAngle = controller.update(50.0, 50.0);

        assertEquals(50.0, newAngle);
        assertEquals(50.0, controller.getCurrentAngleDegrees());
        assertEquals(50.0, controller.getTargetAngleDegrees());
    }

    @Test
    @DisplayName("Update exceeding tick limit caps movement to 60 degrees per tick (Positive)")
    void update_exceedsTickLimitPositive_movesByMaxChange() {
        double firstUpdate = controller.update(100.0, 50.0);
        assertEquals(60.0, firstUpdate, "First tick should be capped at 60.0 degrees");
        assertEquals(100.0, controller.getTargetAngleDegrees(), "Target angle should be stored as 100.0");

        double secondUpdate = controller.update(100.0, 50.0);
        assertEquals(100.0, secondUpdate, "Second tick should cover the remaining distance");
    }

    @Test
    @DisplayName("Update exceeding tick limit caps movement to 60 degrees per tick (Negative)")
    void update_exceedsTickLimitNegative_movesByMaxChange() {
        double firstUpdate = controller.update(-100.0, 50.0);
        assertEquals(-60.0, firstUpdate);
        assertEquals(-100.0, controller.getTargetAngleDegrees());

        double secondUpdate = controller.update(-100.0, 50.0);
        assertEquals(-100.0, secondUpdate);
    }

    // --- SPEED LIMIT CLAMPING TESTS ---

    @Test
    @DisplayName("Low speed requested angle is clamped to positive physical limit (450)")
    void update_lowSpeed_clampsToPositivePhysicalLimit() {
        controller.update(500.0, 50.0); // 500 > 450
        assertEquals(450.0, controller.getTargetAngleDegrees());
    }

    @Test
    @DisplayName("Low speed requested angle is clamped to negative physical limit (-450)")
    void update_lowSpeed_clampsToNegativePhysicalLimit() {
        controller.update(-500.0, 50.0); // -500 < -450
        assertEquals(-450.0, controller.getTargetAngleDegrees());
    }

    @Test
    @DisplayName("High speed requested angle is clamped to positive high speed limit (90)")
    void update_highSpeed_clampsToPositiveHighSpeedLimit() {
        controller.update(150.0, 130.0); // 150 > 90
        assertEquals(90.0, controller.getTargetAngleDegrees());
    }

    @Test
    @DisplayName("High speed requested angle is clamped to negative high speed limit (-90)")
    void update_highSpeed_clampsToNegativeHighSpeedLimit() {
        controller.update(-150.0, 130.0); // -150 < -90
        assertEquals(-90.0, controller.getTargetAngleDegrees());
    }

    @Test
    @DisplayName("Speed exactly at high speed threshold (120) uses the wider physical limit")
    void update_atExactSpeedThreshold_usesPhysicalLimit() {
        // According to `speedKmh > HIGH_SPEED_THRESHOLD_KMH`, exactly 120.0 should evaluate to false.
        controller.update(150.0, 120.0);
        assertEquals(150.0, controller.getTargetAngleDegrees(), "Should allow 150 because limit is 450 at this speed");
    }

    // --- SELF CENTERING TESTS ---

    @Test
    @DisplayName("Self center targets 0.0 and obeys tick movement limits from a positive angle")
    void selfCenter_fromPositiveAngle_movesTowardsZero() {
        // Move to 100 first
        controller.update(100.0, 50.0);
        controller.update(100.0, 50.0);
        assertEquals(100.0, controller.getCurrentAngleDegrees());

        // Self center
        double centerTick = controller.selfCenter();
        assertEquals(40.0, centerTick, "Should reduce by 60 degrees per tick towards 0");
        assertEquals(0.0, controller.getTargetAngleDegrees());

        double finalTick = controller.selfCenter();
        assertEquals(0.0, finalTick);
    }

    @Test
    @DisplayName("Self center targets 0.0 and obeys tick movement limits from a negative angle")
    void selfCenter_fromNegativeAngle_movesTowardsZero() {
        // Move to -100 first
        controller.update(-100.0, 50.0);
        controller.update(-100.0, 50.0);
        assertEquals(-100.0, controller.getCurrentAngleDegrees());

        // Self center
        double centerTick = controller.selfCenter();
        assertEquals(-40.0, centerTick, "Should increase by 60 degrees per tick towards 0");
        assertEquals(0.0, controller.getTargetAngleDegrees());
    }

    // --- VALIDATION & EXCEPTIONS TESTS ---

    @ParameterizedTest
    @ValueSource(doubles = {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
    @DisplayName("Non-finite angles throw IllegalArgumentException")
    void update_invalidAngle_throwsIllegalArgumentException(double invalidAngle) {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> controller.update(invalidAngle, 50.0)
        );
        assertEquals("requested angle must be finite", exception.getMessage());
    }

    @ParameterizedTest
    @ValueSource(doubles = {-1.0, -0.1, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
    @DisplayName("Invalid speed values throw IllegalArgumentException")
    void update_invalidSpeed_throwsIllegalArgumentException(double invalidSpeed) {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> controller.update(30.0, invalidSpeed)
        );
        assertEquals("speed must be a finite non-negative value", exception.getMessage());
    }
}