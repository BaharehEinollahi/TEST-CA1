package autopilot.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class BrakeControllerTest {

    private static final double NORMAL_PRESSURE = 50.0;
    private static final double NORMAL_SPEED = 20.0;
    private static final double HIGH_SPEED = 50.0;
    private static final double MAX_PRESSURE = 100.0;
    private static final double EXCESSIVE_PRESSURE = 150.0;

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

    // --- NORMAL & BOUNDARY BRAKING TESTS ---

    @ParameterizedTest
    @CsvSource({
            "50.0, 20.0, 50.0",   // Normal conditions apply exact pressure
            "150.0, 20.0, 100.0", // Exceeding MAX_PRESSURE at low speed caps at 100
            "80.0, 20.0, 80.0",   // Exactly at pressure threshold does not trigger ABS
            "85.0, 30.0, 85.0"    // Exactly at speed threshold does not trigger ABS
    })
    @DisplayName("Non-ABS conditions apply correct capped or exact pressure")
    void apply_nonAbsConditions_returnsExpectedPressure(double requestedPressure, double speed, double expectedPressure) {
        double result = controller.apply(requestedPressure, speed, validGear);
        assertEquals(expectedPressure, result);
        assertEquals(expectedPressure, controller.getPressure());
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