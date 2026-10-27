package autopilot.core;

import autopilot.exception.IllegalGearChangeException;
import autopilot.sensor.SpeedSensor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class GearBoxTest {

    private FakeSpeedSensor fakeSensor;
    private GearBox gearBox;

    @BeforeEach
    void setUp() {
        fakeSensor = new FakeSpeedSensor();
        gearBox = new GearBox(fakeSensor);
    }

    // --- INITIALIZATION & NULL CHECKS ---

    @Test
    @DisplayName("Constructor with null sensor throws NullPointerException")
    void constructor_nullSensor_throwsNullPointerException() {
        assertThrows(NullPointerException.class, () -> new GearBox(null));
    }

    @Test
    @DisplayName("Setting null speed sensor throws NullPointerException")
    void setSpeedSensor_null_throwsNullPointerException() {
        assertThrows(NullPointerException.class, () -> gearBox.setSpeedSensor(null));
    }

    @Test
    @DisplayName("Initial gear should be Park (P)")
    void initialState_isPark() {
        assertEquals(GearBox.Gear.P, gearBox.getCurrentGear());
    }

    // --- SAME GEAR (EARLY EXIT) ---

    @Test
    @DisplayName("Changing to the current gear ignores speed validation and succeeds")
    void changeTo_sameGear_returnsEarly() {
        fakeSensor.setSpeed(-50.0); // Would normally throw IllegalArgumentException
        assertDoesNotThrow(() -> gearBox.changeTo(GearBox.Gear.P));
        assertEquals(GearBox.Gear.P, gearBox.getCurrentGear());
    }

    // --- INVALID SPEED VALUES ---

    @ParameterizedTest
    @ValueSource(doubles = {-1.0, -0.1, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
    @DisplayName("Invalid speed values throw IllegalArgumentException")
    void changeTo_invalidSpeed_throwsIllegalArgumentException(double invalidSpeed) {
        fakeSensor.setSpeed(invalidSpeed);
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> gearBox.changeTo(GearBox.Gear.R)
        );
        assertEquals("speed must be a finite non-negative value", exception.getMessage());
    }

    // --- VALID TRANSITIONS ---

    @Test
    @DisplayName("P -> R is allowed if speed is strictly under 5.0 km/h")
    void changeTo_PtoR_underSpeedLimit_succeeds() {
        fakeSensor.setSpeed(4.99);
        gearBox.changeTo(GearBox.Gear.R);
        assertEquals(GearBox.Gear.R, gearBox.getCurrentGear());
    }

    @Test
    @DisplayName("R -> D is allowed when stationary")
    void changeTo_RtoD_stationary_succeeds() {
        setToReverse();

        fakeSensor.setSpeed(0.0);
        gearBox.changeTo(GearBox.Gear.D);
        assertEquals(GearBox.Gear.D, gearBox.getCurrentGear());
    }

    @Test
    @DisplayName("D -> R is allowed when stationary")
    void changeTo_DtoR_stationary_succeeds() {
        setToDrive();

        fakeSensor.setSpeed(0.0);
        gearBox.changeTo(GearBox.Gear.R);
        assertEquals(GearBox.Gear.R, gearBox.getCurrentGear());
    }

    @Test
    @DisplayName("R -> N is allowed at any moving speed")
    void changeTo_RtoN_atAnySpeed_succeeds() {
        setToReverse();

        fakeSensor.setSpeed(20.0); // Moving backwards
        gearBox.changeTo(GearBox.Gear.N);
        assertEquals(GearBox.Gear.N, gearBox.getCurrentGear());
    }

    @Test
    @DisplayName("N -> D is allowed at any moving speed")
    void changeTo_NtoD_atAnySpeed_succeeds() {
        setToNeutral();

        fakeSensor.setSpeed(50.0); // Coasting forward
        gearBox.changeTo(GearBox.Gear.D);
        assertEquals(GearBox.Gear.D, gearBox.getCurrentGear());
    }

    // --- SPEED BOUNDARY VIOLATIONS ---

    @Test
    @DisplayName("P -> R fails if speed is exactly 5.0 km/h or higher")
    void changeTo_PtoR_atOrAboveSpeedLimit_fails() {
        fakeSensor.setSpeed(5.0);
        assertThrows(IllegalGearChangeException.class, () -> gearBox.changeTo(GearBox.Gear.R));
    }

    @Test
    @DisplayName("R -> D fails if vehicle is moving")
    void changeTo_RtoD_moving_fails() {
        setToReverse();
        fakeSensor.setSpeed(0.1);
        assertThrows(IllegalGearChangeException.class, () -> gearBox.changeTo(GearBox.Gear.D));
    }

    @Test
    @DisplayName("D -> R fails if vehicle is moving")
    void changeTo_DtoR_moving_fails() {
        setToDrive();
        fakeSensor.setSpeed(0.1);
        assertThrows(IllegalGearChangeException.class, () -> gearBox.changeTo(GearBox.Gear.R));
    }

    // --- ILLEGAL STATE TRANSITIONS ---
    // These tests ensure paths not explicitly permitted by isAllowed() are rejected.

    @Test
    @DisplayName("P -> D is an illegal transition")
    void changeTo_PtoD_fails() {
        fakeSensor.setSpeed(0.0);
        assertThrows(IllegalGearChangeException.class, () -> gearBox.changeTo(GearBox.Gear.D));
    }

    @Test
    @DisplayName("P -> N is an illegal transition")
    void changeTo_PtoN_fails() {
        fakeSensor.setSpeed(0.0);
        assertThrows(IllegalGearChangeException.class, () -> gearBox.changeTo(GearBox.Gear.N));
    }

    @Test
    @DisplayName("N -> R is an illegal transition")
    void changeTo_NtoR_fails() {
        setToNeutral();
        fakeSensor.setSpeed(0.0);
        assertThrows(IllegalGearChangeException.class, () -> gearBox.changeTo(GearBox.Gear.R));
    }

    @Test
    @DisplayName("Shifting back to P is illegal from any gear (D -> P)")
    void changeTo_DtoP_fails() {
        setToDrive();
        fakeSensor.setSpeed(0.0);
        assertThrows(IllegalGearChangeException.class, () -> gearBox.changeTo(GearBox.Gear.P));
    }

    @Test
    @DisplayName("D -> N is an illegal transition")
    void changeTo_DtoN_fails() {
        setToDrive();
        fakeSensor.setSpeed(0.0);
        assertThrows(IllegalGearChangeException.class, () -> gearBox.changeTo(GearBox.Gear.N));
    }

    // --- STATE SETUP HELPERS ---

    private void setToReverse() {
        fakeSensor.setSpeed(0.0);
        gearBox.changeTo(GearBox.Gear.R);
    }

    private void setToNeutral() {
        fakeSensor.setSpeed(0.0);
        gearBox.changeTo(GearBox.Gear.R);
        gearBox.changeTo(GearBox.Gear.N);
    }

    private void setToDrive() {
        fakeSensor.setSpeed(0.0);
        gearBox.changeTo(GearBox.Gear.R);
        gearBox.changeTo(GearBox.Gear.D);
    }

    // --- FAKE DEPENDENCY ---

    /**
     * Lightweight fake implementation of SpeedSensor to avoid Mockito coupling.
     */
    private static class FakeSpeedSensor implements SpeedSensor {
        private double speed = 0.0;

        public void setSpeed(double speed) {
            this.speed = speed;
        }

        @Override
        public double getSpeedKmh() {
            return speed;
        }
    }
}