package autopilot.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class BrakeControllerTest {

    private static final double NORMAL_PRESSURE = 50.0;
    private static final double NORMAL_SPEED = 20.0;
    private static final double MAX_PRESSURE = 100.0;
    private static final double EXCESSIVE_PRESSURE = 150.0;
    private static final double HIGH_SPEED = 50.0;

    private static final double ABS_PRESSURE_THRESHOLD = 80.0;
    private static final double ABS_SPEED_THRESHOLD = 30.0;
    private static final double ABS_TRIGGER_PRESSURE = 85.0;
    private static final double ABS_TRIGGER_SPEED = 35.0;
    private static final double ABS_HIGH_PULSE = 90.0;
    private static final double ABS_LOW_PULSE = 60.0;

    private BrakeController controller;
    private final GearBox.Gear validGear = GearBox.Gear.D;

    @BeforeEach
    void setUp() {
        controller = new BrakeController();
    }

    // --- NORMAL BRAKING TESTS ---

    @Test
    @DisplayName("Normal conditions should apply requested exact pressure")
    void apply_normalConditions_setsExactPressure() {
        double result = controller.apply(NORMAL_PRESSURE, NORMAL_SPEED, validGear);
        assertEquals(NORMAL_PRESSURE, result);
        assertEquals(NORMAL_PRESSURE, controller.getPressure());
    }

    @Test
    @DisplayName("Requested pressure exceeding MAX_PRESSURE caps at 100.0 without ABS")
    void apply_exceedsMaxPressureAtLowSpeed_capsAtMax() {
        double result = controller.apply(EXCESSIVE_PRESSURE, NORMAL_SPEED, validGear);
        assertEquals(MAX_PRESSURE, result);
        assertEquals(MAX_PRESSURE, controller.getPressure());
    }

    @Test
    @DisplayName("Values exactly at ABS thresholds do NOT trigger ABS")
    void apply_exactlyAtAbsThresholds_doesNotTriggerAbs() {
        assertEquals(ABS_PRESSURE_THRESHOLD, controller.apply(ABS_PRESSURE_THRESHOLD, NORMAL_SPEED, validGear));
        assertEquals(ABS_TRIGGER_PRESSURE, controller.apply(ABS_TRIGGER_PRESSURE, ABS_SPEED_THRESHOLD, validGear));
    }

    // --- ABS BRAKING TESTS ---

    @Test
    @DisplayName("ABS conditions met should alternate high and low pulses")
    void apply_absConditionsMet_alternatesPulses() {
        assertEquals(ABS_HIGH_PULSE, controller.apply(ABS_TRIGGER_PRESSURE, ABS_TRIGGER_SPEED, validGear));
        assertEquals(ABS_LOW_PULSE, controller.apply(ABS_TRIGGER_PRESSURE, ABS_TRIGGER_SPEED, validGear));
        assertEquals(ABS_HIGH_PULSE, controller.apply(ABS_TRIGGER_PRESSURE, ABS_TRIGGER_SPEED, validGear));
        assertEquals(ABS_LOW_PULSE, controller.apply(ABS_TRIGGER_PRESSURE, ABS_TRIGGER_SPEED, validGear));
    }

    @Test
    @DisplayName("Requested pressure exceeding MAX_PRESSURE at high speed triggers ABS")
    void apply_exceedsMaxPressureAtHighSpeed_triggersAbs() {
        assertEquals(ABS_HIGH_PULSE, controller.apply(EXCESSIVE_PRESSURE, HIGH_SPEED, validGear));
    }

    @Test
    @DisplayName("Normal braking should reset the ABS alternating state")
    void apply_absStateResets_whenNormalBrakingOccurs() {
        controller.apply(ABS_TRIGGER_PRESSURE, ABS_TRIGGER_SPEED, validGear);
        controller.apply(NORMAL_PRESSURE, NORMAL_SPEED, validGear);

        assertEquals(ABS_HIGH_PULSE, controller.apply(ABS_TRIGGER_PRESSURE, ABS_TRIGGER_SPEED, validGear));
    }

    // --- EMERGENCY BRAKING TESTS ---

    @Test
    @DisplayName("Emergency brake applies MAX pressure and activates warning")
    void emergencyBrake_appliesMaxPressureAndSetsWarning() {
        controller.emergencyBrake(validGear);

        assertEquals(MAX_PRESSURE, controller.getPressure());
        assertTrue(controller.isWarningActive());
    }

    @Test
    @DisplayName("Emergency brake resets the ABS alternating state")
    void emergencyBrake_resetsAbsState() {
        controller.apply(ABS_TRIGGER_PRESSURE, ABS_TRIGGER_SPEED, validGear);
        controller.emergencyBrake(validGear);

        assertEquals(ABS_HIGH_PULSE, controller.apply(ABS_TRIGGER_PRESSURE, ABS_TRIGGER_SPEED, validGear));
    }

    // --- EXCEPTION & VALIDATION TESTS ---

    @ParameterizedTest
    @ValueSource(doubles = {-1.0, -0.1, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
    @DisplayName("Invalid pressure values throw IllegalArgumentException")
    void apply_invalidPressure_throwsIllegalArgumentException(double invalidPressure) {
        assertThrows(IllegalArgumentException.class, () -> controller.apply(invalidPressure, NORMAL_SPEED, validGear));
    }

    @ParameterizedTest
    @ValueSource(doubles = {-1.0, -0.1, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
    @DisplayName("Invalid speed values throw IllegalArgumentException")
    void apply_invalidSpeed_throwsIllegalArgumentException(double invalidSpeed) {
        assertThrows(IllegalArgumentException.class, () -> controller.apply(NORMAL_PRESSURE, invalidSpeed, validGear));
    }

    @Test
    @DisplayName("Gear in Park throws IllegalStateException during apply")
    void apply_gearIsPark_throwsIllegalStateException() {
        assertThrows(IllegalStateException.class, () -> controller.apply(NORMAL_PRESSURE, NORMAL_SPEED, GearBox.Gear.P));
    }

    @Test
    @DisplayName("Gear in Park throws IllegalStateException during emergencyBrake")
    void emergencyBrake_gearIsPark_throwsIllegalStateException() {
        assertThrows(IllegalStateException.class, () -> controller.emergencyBrake(GearBox.Gear.P));
    }

    @Test
    @DisplayName("Null gear throws NullPointerException")
    void ensureBrakingAllowed_nullGear_throwsNullPointerException() {
        assertThrows(NullPointerException.class, () -> controller.apply(NORMAL_PRESSURE, NORMAL_SPEED, null));
    }
}