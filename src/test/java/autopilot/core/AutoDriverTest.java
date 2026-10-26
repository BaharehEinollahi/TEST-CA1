package autopilot.core;

import autopilot.exception.SensorFailureException;
import autopilot.sensor.LaneSensor;
import autopilot.sensor.ObstacleSensor;
import autopilot.sensor.SpeedSensor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class AutoDriverTest {
    // Delete magic values
    private static final double DEFAULT_SPEED_KMH = 80.0;
    private static final double DEFAULT_OBSTACLE_DISTANCE = 100.0;
    private static final double DEFAULT_LANE_OFFSET = 0.0;
    private static final double SAFE_MODE_BRAKE_PRESSURE = 30.0;
    private static final double ZERO_STEERING_ANGLE = 0.0;

    private SpeedSensor speedSensor;
    private ObstacleSensor obstacleSensor;
    private LaneSensor laneSensor;
    private AutoDriver autoDriver;

    @BeforeEach
    void setUp() {
        //java new feature, shorten code easy to read
        speedSensor = () -> DEFAULT_SPEED_KMH;
        obstacleSensor = () -> DEFAULT_OBSTACLE_DISTANCE;
        laneSensor = () -> DEFAULT_LANE_OFFSET;


        autoDriver = new AutoDriver(
                speedSensor,
                obstacleSensor,
                laneSensor,
                DEFAULT_SPEED_KMH
        );
    }
    //check all gears at once
    @ParameterizedTest
    @EnumSource(GearBox.Gear.class)
    void tick_NormalConditionsWithVariousGears_ReturnsValidResult(GearBox.Gear gear) {
        AutoDriver.DriveResult result = autoDriver.tick(gear);

        assertNotNull(result);
        assertFalse(result.safeMode());
        assertNotNull(result.cruiseMode());
        assertEquals(0.0, result.brakePressure());
    }

    @ParameterizedTest
    @CsvSource({
            "1.0, -1.0",
            "-1.0, 1.0"
    })
    void tick_LaneDeviation_ReturnsCorrectSteeringDirection(double laneOffset, double expectedSteeringSign) {
        LaneSensor deviatingLaneSensor = () -> laneOffset;
        autoDriver = new AutoDriver(speedSensor, obstacleSensor, deviatingLaneSensor, DEFAULT_SPEED_KMH);

        AutoDriver.DriveResult result = autoDriver.tick(GearBox.Gear.D);

        assertEquals(Math.signum(expectedSteeringSign), Math.signum(result.steeringAngleDegrees()));
    }

    //test all possible failure at the same test
    @ParameterizedTest
    @MethodSource("provideFailingSensors")
    void tick_SensorFailure_EntersSafeMode(SpeedSensor speed, ObstacleSensor obstacle, LaneSensor lane) {
        autoDriver = new AutoDriver(speed, obstacle, lane, DEFAULT_SPEED_KMH);

        AutoDriver.DriveResult result = autoDriver.tick(GearBox.Gear.D);

        assertAll("Safe Mode State Validation",
                () -> assertTrue(result.safeMode()),
                () -> assertEquals(SAFE_MODE_BRAKE_PRESSURE, result.brakePressure()),
                () -> assertEquals(ZERO_STEERING_ANGLE, result.steeringAngleDegrees()),
                () -> assertEquals(AdaptiveCruise.Mode.BRAKE, result.cruiseMode()),
                () -> assertFalse(result.warningActive())
        );
    }

    private static Stream<Arguments> provideFailingSensors() {
        SpeedSensor failingSpeedSensor = () -> { throw new SensorFailureException("Speed sensor failed"); };
        ObstacleSensor failingObstacleSensor = () -> { throw new SensorFailureException("Obstacle sensor failed"); };
        LaneSensor failingLaneSensor = () -> { throw new SensorFailureException("Lane sensor failed"); };

        SpeedSensor workingSpeedSensor = () -> DEFAULT_SPEED_KMH;
        ObstacleSensor workingObstacleSensor = () -> DEFAULT_OBSTACLE_DISTANCE;
        LaneSensor workingLaneSensor = () -> DEFAULT_LANE_OFFSET;

        return Stream.of(
                Arguments.of(failingSpeedSensor, workingObstacleSensor, workingLaneSensor),
                Arguments.of(workingSpeedSensor, failingObstacleSensor, workingLaneSensor),
                Arguments.of(workingSpeedSensor, workingObstacleSensor, failingLaneSensor)
        );
    }

    @ParameterizedTest
    @ValueSource(doubles = {40.0, 60.0, 80.0, 120.0})
    void tick_DifferentTargetSpeeds_ExecutesWithoutExceptions(double targetSpeed) {
        autoDriver = new AutoDriver(speedSensor, obstacleSensor, laneSensor, targetSpeed);

        AutoDriver.DriveResult result = autoDriver.tick(GearBox.Gear.D);

        assertNotNull(result);
        assertFalse(result.safeMode());
    }
}
