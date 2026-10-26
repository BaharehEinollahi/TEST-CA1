package autopilot.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class BrakeControllerTest {

    private BrakeController controller;
    private final GearBox.Gear validGear = GearBox.Gear.D;

    @BeforeEach
    void setUp() {
        controller = new BrakeController();
    }

    @Test
    @DisplayName("Initial state should have 0 pressure and no warning")
    void initialState_hasZeroPressureAndNoWarning() {
        assertEquals(0.0, controller.getPressure());
        assertFalse(controller.isWarningActive());
    }

    // --- NORMAL BRAKING TESTS ---

    @Test
    @DisplayName("Normal conditions should apply requested exact pressure")
    void apply_normalConditions_setsExactPressure() {
        double result = controller.apply(50.0, 20.0, validGear);
        assertEquals(50.0, result);
        assertEquals(50.0, controller.getPressure());
    }

    @Test
    @DisplayName("Requested pressure exceeding MAX_PRESSURE caps at 100.0 without ABS")
    void apply_exceedsMaxPressureAtLowSpeed_capsAtMax() {
        double result = controller.apply(150.0, 20.0, validGear);
        assertEquals(100.0, result);
        assertEquals(100.0, controller.getPressure());
    }

    @Test
    @DisplayName("Values exactly at ABS thresholds do NOT trigger ABS")
    void apply_exactlyAtAbsThresholds_doesNotTriggerAbs() {
        // Exactly at pressure threshold, speed above
        assertEquals(80.0, controller.apply(80.0, 50.0, validGear));

        // Pressure above threshold, exactly at speed threshold
        assertEquals(85.0, controller.apply(85.0, 30.0, validGear));
    }

    // --- ABS BRAKING TESTS ---

    @Test
    @DisplayName("ABS conditions met should alternate high and low pulses")
    void apply_absConditionsMet_alternatesPulses() {
        assertEquals(90.0, controller.apply(85.0, 35.0, validGear), "1st pulse should be HIGH (90.0)");
        assertEquals(60.0, controller.apply(85.0, 35.0, validGear), "2nd pulse should be LOW (60.0)");
        assertEquals(90.0, controller.apply(85.0, 35.0, validGear), "3rd pulse should be HIGH (90.0)");
        assertEquals(60.0, controller.apply(85.0, 35.0, validGear), "4th pulse should be LOW (60.0)");
    }

    @Test
    @DisplayName("Requested pressure exceeding MAX_PRESSURE at high speed triggers ABS")
    void apply_exceedsMaxPressureAtHighSpeed_triggersAbs() {
        // requested 150 -> bounded 100 -> bounded > 80 && speed > 30 -> ABS HIGH
        assertEquals(90.0, controller.apply(150.0, 50.0, validGear));
    }

    @Test
    @DisplayName("Normal braking should reset the ABS alternating state")
    void apply_absStateResets_whenNormalBrakingOccurs() {
        controller.apply(85.0, 35.0, validGear); // triggers HIGH (90)
        controller.apply(50.0, 20.0, validGear); // normal braking (50), resets ABS

        assertEquals(90.0, controller.apply(85.0, 35.0, validGear), "Should start with HIGH pulse again");
    }

    // --- EMERGENCY BRAKING TESTS ---

    @Test
    @DisplayName("Emergency brake applies MAX pressure and activates warning")
    void emergencyBrake_appliesMaxPressureAndSetsWarning() {
        controller.emergencyBrake(validGear);

        assertEquals(100.0, controller.getPressure());
        assertTrue(controller.isWarningActive());
    }

    @Test
    @DisplayName("Emergency brake resets the ABS alternating state")
    void emergencyBrake_resetsAbsState() {
        controller.apply(85.0, 35.0, validGear); // triggers HIGH (90)
        controller.emergencyBrake(validGear);    // applies MAX (100) and resets ABS

        assertEquals(90.0, controller.apply(85.0, 35.0, validGear), "Should start with HIGH pulse again");
    }

    // --- EXCEPTION & VALIDATION TESTS ---

    @ParameterizedTest
    @ValueSource(doubles = {-1.0, -0.1, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
    @DisplayName("Invalid pressure values throw IllegalArgumentException")
    void apply_invalidPressure_throwsIllegalArgumentException(double invalidPressure) {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> controller.apply(invalidPressure, 20.0, validGear)
        );
        assertEquals("pressure must be a finite non-negative value", exception.getMessage());
    }

    @ParameterizedTest
    @ValueSource(doubles = {-1.0, -0.1, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
    @DisplayName("Invalid speed values throw IllegalArgumentException")
    void apply_invalidSpeed_throwsIllegalArgumentException(double invalidSpeed) {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> controller.apply(50.0, invalidSpeed, validGear)
        );
        assertEquals("speed must be a finite non-negative value", exception.getMessage());
    }

    @Test
    @DisplayName("Gear in Park throws IllegalStateException during apply")
    void apply_gearIsPark_throwsIllegalStateException() {
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> controller.apply(50.0, 20.0, GearBox.Gear.P)
        );
        assertEquals("braking is unavailable while the gearbox is in park", exception.getMessage());
    }

    @Test
    @DisplayName("Gear in Park throws IllegalStateException during emergencyBrake")
    void emergencyBrake_gearIsPark_throwsIllegalStateException() {
        assertThrows(IllegalStateException.class, () -> controller.emergencyBrake(GearBox.Gear.P));
    }

    @Test
    @DisplayName("Null gear throws NullPointerException")
    void ensureBrakingAllowed_nullGear_throwsNullPointerException() {
        NullPointerException exception = assertThrows(
                NullPointerException.class,
                () -> controller.apply(50.0, 20.0, null)
        );
        assertEquals("gear must not be null", exception.getMessage());
    }
}