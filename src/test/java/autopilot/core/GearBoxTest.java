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

    private static final double STATIONARY_SPEED = 0.0;
    private static final double CREEPING_SPEED = 0.1;
    private static final double UNDER_REVERSE_LIMIT_SPEED = 4.99;
    private static final double REVERSE_LIMIT_SPEED = 5.0;
    private static final double REVERSING_SPEED = 20.0;
    private static final double COASTING_SPEED = 50.0;
    private static final double NEGATIVE_SPEED_TEST = -50.0;

    private FakeSpeedSensor fakeSensor;
    private GearBox gearBox;

    @BeforeEach
    void setUp() {
        fakeSensor = new FakeSpeedSensor();
        gearBox = new GearBox(fakeSensor);
    }

    // --- SAME GEAR (EARLY EXIT) ---

    @Test
    @DisplayName("Changing to the current gear ignores speed validation and succeeds")
    void changeTo_sameGear_returnsEarly() {
        fakeSensor.setSpeed(NEGATIVE_SPEED_TEST);
        assertDoesNotThrow(() -> gearBox.changeTo(GearBox.Gear.P));
        assertEquals(GearBox.Gear.P, gearBox.getCurrentGear());
    }

    // --- INVALID SPEED VALUES ---

    @ParameterizedTest
    @ValueSource(doubles = {-1.0, -0.1, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
    @DisplayName("Invalid speed values throw IllegalArgumentException")
    void changeTo_invalidSpeed_throwsIllegalArgumentException(double invalidSpeed) {
        fakeSensor.setSpeed(invalidSpeed);
        assertThrows(IllegalArgumentException.class, () -> gearBox.changeTo(GearBox.Gear.R));
    }

    // --- VALID TRANSITIONS ---

    @Test
    @DisplayName("P -> R is allowed if speed is strictly under 5.0 km/h")
    void changeTo_PtoR_underSpeedLimit_succeeds() {
        fakeSensor.setSpeed(UNDER_REVERSE_LIMIT_SPEED);
        gearBox.changeTo(GearBox.Gear.R);
        assertEquals(GearBox.Gear.R, gearBox.getCurrentGear());
    }

    @Test
    @DisplayName("R -> D is allowed when stationary")
    void changeTo_RtoD_stationary_succeeds() {
        setToReverse();

        fakeSensor.setSpeed(STATIONARY_SPEED);
        gearBox.changeTo(GearBox.Gear.D);
        assertEquals(GearBox.Gear.D, gearBox.getCurrentGear());
    }

    @Test
    @DisplayName("D -> R is allowed when stationary")
    void changeTo_DtoR_stationary_succeeds() {
        setToDrive();

        fakeSensor.setSpeed(STATIONARY_SPEED);
        gearBox.changeTo(GearBox.Gear.R);
        assertEquals(GearBox.Gear.R, gearBox.getCurrentGear());
    }

    @Test
    @DisplayName("R -> N is allowed at any moving speed")
    void changeTo_RtoN_atAnySpeed_succeeds() {
        setToReverse();

        fakeSensor.setSpeed(REVERSING_SPEED);
        gearBox.changeTo(GearBox.Gear.N);
        assertEquals(GearBox.Gear.N, gearBox.getCurrentGear());
    }

    @Test
    @DisplayName("N -> D is allowed at any moving speed")
    void changeTo_NtoD_atAnySpeed_succeeds() {
        setToNeutral();

        fakeSensor.setSpeed(COASTING_SPEED);
        gearBox.changeTo(GearBox.Gear.D);
        assertEquals(GearBox.Gear.D, gearBox.getCurrentGear());
    }

    // --- SPEED BOUNDARY VIOLATIONS ---

    @Test
    @DisplayName("P -> R fails if speed is exactly 5.0 km/h or higher")
    void changeTo_PtoR_atOrAboveSpeedLimit_fails() {
        fakeSensor.setSpeed(REVERSE_LIMIT_SPEED);
        assertThrows(IllegalGearChangeException.class, () -> gearBox.changeTo(GearBox.Gear.R));
    }

    @Test
    @DisplayName("R -> D fails if vehicle is moving")
    void changeTo_RtoD_moving_fails() {
        setToReverse();
        fakeSensor.setSpeed(CREEPING_SPEED);
        assertThrows(IllegalGearChangeException.class, () -> gearBox.changeTo(GearBox.Gear.D));
    }

    @Test
    @DisplayName("D -> R fails if vehicle is moving")
    void changeTo_DtoR_moving_fails() {
        setToDrive();
        fakeSensor.setSpeed(CREEPING_SPEED);
        assertThrows(IllegalGearChangeException.class, () -> gearBox.changeTo(GearBox.Gear.R));
    }

    // --- ILLEGAL STATE TRANSITIONS ---

    @Test
    @DisplayName("P -> D is an illegal transition")
    void changeTo_PtoD_fails() {
        fakeSensor.setSpeed(STATIONARY_SPEED);
        assertThrows(IllegalGearChangeException.class, () -> gearBox.changeTo(GearBox.Gear.D));
    }

    @Test
    @DisplayName("P -> N is an illegal transition")
    void changeTo_PtoN_fails() {
        fakeSensor.setSpeed(STATIONARY_SPEED);
        assertThrows(IllegalGearChangeException.class, () -> gearBox.changeTo(GearBox.Gear.N));
    }

    @Test
    @DisplayName("N -> R is an illegal transition")
    void changeTo_NtoR_fails() {
        setToNeutral();
        fakeSensor.setSpeed(STATIONARY_SPEED);
        assertThrows(IllegalGearChangeException.class, () -> gearBox.changeTo(GearBox.Gear.R));
    }

    @Test
    @DisplayName("Shifting back to P is illegal from any gear (D -> P)")
    void changeTo_DtoP_fails() {
        setToDrive();
        fakeSensor.setSpeed(STATIONARY_SPEED);
        assertThrows(IllegalGearChangeException.class, () -> gearBox.changeTo(GearBox.Gear.P));
    }

    @Test
    @DisplayName("D -> N is an illegal transition")
    void changeTo_DtoN_fails() {
        setToDrive();
        fakeSensor.setSpeed(STATIONARY_SPEED);
        assertThrows(IllegalGearChangeException.class, () -> gearBox.changeTo(GearBox.Gear.N));
    }

    // --- STATE SETUP HELPERS ---

    private void setToReverse() {
        fakeSensor.setSpeed(STATIONARY_SPEED);
        gearBox.changeTo(GearBox.Gear.R);
    }

    private void setToNeutral() {
        fakeSensor.setSpeed(STATIONARY_SPEED);
        gearBox.changeTo(GearBox.Gear.R);
        gearBox.changeTo(GearBox.Gear.N);
    }

    private void setToDrive() {
        fakeSensor.setSpeed(STATIONARY_SPEED);
        gearBox.changeTo(GearBox.Gear.R);
        gearBox.changeTo(GearBox.Gear.D);
    }

    // --- FAKE DEPENDENCY ---

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