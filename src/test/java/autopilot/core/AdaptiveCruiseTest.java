package autopilot.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class AdaptiveCruiseTest {

    private static final double TARGET_SPEED_KMH = 100.0;
    private static final double HYSTERESIS_BRAKE_PRESSURE = 10.0;

    private AdaptiveCruise adaptiveCruise;

    @BeforeEach
    void setUp() {
        adaptiveCruise = new AdaptiveCruise(TARGET_SPEED_KMH);
    }

    @ParameterizedTest
    @CsvFileSource(resources = "/safe_distances.csv", numLinesToSkip = 1)
    void safeDistanceFor_ValidSpeeds_CalculatesCorrectDistance(double speedKmh, double expectedDistance) {
        assertEquals(expectedDistance, adaptiveCruise.safeDistanceFor(speedKmh));
    }

    @ParameterizedTest
    @ValueSource(doubles = {-10.0, -0.1, Double.NaN, Double.POSITIVE_INFINITY})
    void safeDistanceFor_InvalidSpeeds_ThrowsIllegalArgumentException(double invalidSpeed) {
        assertThrows(IllegalArgumentException.class, () -> adaptiveCruise.safeDistanceFor(invalidSpeed));
    }

    @ParameterizedTest
    @MethodSource("provideInvalidUpdateInputs")
    void update_InvalidInputs_ThrowsIllegalArgumentException(double speed, double distance) {
        assertThrows(IllegalArgumentException.class, () -> adaptiveCruise.update(speed, distance));
    }

    private static Stream<Arguments> provideInvalidUpdateInputs() {
        return Stream.of(
                Arguments.of(-10.0, 50.0),
                Arguments.of(Double.NaN, 50.0),
                Arguments.of(80.0, -5.0),
                Arguments.of(80.0, Double.POSITIVE_INFINITY)
        );
    }

    @ParameterizedTest
    @CsvSource({
            "100.0, 20.0, EMERGENCY_BRAKE, 100.0",
            "100.0, 40.0, BRAKE, 20.0",
            "80.0,  50.0, ACCELERATE, 5.0",
            "98.0,  60.0, ACCELERATE, 2.0",
            "100.0, 60.0, HOLD, 0.0"
    })
    void update_StatelessConditions_ReturnsExpectedDecision(
            double speed, double distance, AdaptiveCruise.Mode expectedMode, double expectedAmount) {

        AdaptiveCruise.Decision result = adaptiveCruise.update(speed, distance);

        assertAll("Decision Evaluation",
                () -> assertEquals(expectedMode, result.mode()),
                () -> assertEquals(expectedAmount, result.amount(), 0.001)
        );
    }

    @Test
    void maintainsBrakeStateWithinHysteresisZoneAndReleasesBeyondThreshold() {
        final double cruisingSpeed = 100.0;
        final double distanceInBrakingZone = 40.0;
        final double distanceInHysteresisZone = 55.0;
        final double distancePastReleaseThreshold = 65.0;

        adaptiveCruise.update(cruisingSpeed, distanceInBrakingZone);

        var hysteresisDecision = adaptiveCruise.update(cruisingSpeed, distanceInHysteresisZone);
        var releaseDecision = adaptiveCruise.update(cruisingSpeed, distancePastReleaseThreshold);

        assertAll("Hysteresis behavior from braking to release",
                () -> assertEquals(AdaptiveCruise.Mode.BRAKE, hysteresisDecision.mode()),
                () -> assertEquals(HYSTERESIS_BRAKE_PRESSURE, hysteresisDecision.amount()),
                () -> assertEquals(AdaptiveCruise.Mode.HOLD, releaseDecision.mode()),
                () -> assertEquals(0.0, releaseDecision.amount())
        );
    }

    @ParameterizedTest
    @EnumSource(AdaptiveCruise.Mode.class)
    void decisionRecord_Instantiation_StoresModeProperly(AdaptiveCruise.Mode mode) {
        AdaptiveCruise.Decision decision = new AdaptiveCruise.Decision(mode, TARGET_SPEED_KMH);
        assertEquals(mode, decision.mode());
    }
}