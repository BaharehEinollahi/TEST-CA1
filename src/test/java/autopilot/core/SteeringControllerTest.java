package autopilot.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class SteeringControllerTest {

    private static final double NORMAL_ANGLE = 50.0;
    private static final double NORMAL_SPEED = 50.0;
    private static final double LARGE_ANGLE = 100.0;

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

    @ParameterizedTest
    @CsvSource({
            "100.0, 50.0, 60.0",
            "-100.0, 50.0, -60.0"
    })
    @DisplayName("Update exceeding tick limit caps movement per tick")
    void update_exceedsTickLimit_movesByMaxChange(double targetAngle, double speed, double expectedFirstTick) {
        double firstUpdate = controller.update(targetAngle, speed);
        assertEquals(expectedFirstTick, firstUpdate);
        assertEquals(targetAngle, controller.getTargetAngleDegrees());

        double secondUpdate = controller.update(targetAngle, speed);
        assertEquals(targetAngle, secondUpdate);
    }

    // --- SPEED LIMIT CLAMPING TESTS ---

    @ParameterizedTest
    @CsvSource({
            "500.0, 50.0, 450.0",   // Clamps to positive physical limit
            "-500.0, 50.0, -450.0", // Clamps to negative physical limit
            "150.0, 130.0, 90.0",   // Clamps to positive high-speed limit
            "-150.0, 130.0, -90.0", // Clamps to negative high-speed limit
            "150.0, 120.0, 150.0"   // Speed exactly at threshold uses the wider physical limit
    })
    @DisplayName("Requested angle is clamped properly based on speed limits")
    void update_speedLimits_clampsToExpectedTargetAngle(double requestedAngle, double speed, double expectedTargetAngle) {
        controller.update(requestedAngle, speed);
        assertEquals(expectedTargetAngle, controller.getTargetAngleDegrees());
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