package com.safesignal.detection;

import org.junit.Test;

import static org.junit.Assert.*;

public class DetectorConfigTest {

    @Test
    public void testDefaultConfigValues() {
        DetectorConfig config = DetectorConfig.defaultConfig();

        assertEquals(5.0, config.getFreeFallThreshold(), 0.001);
        assertEquals(40L, config.getFreeFallMinDurationMs());
        assertEquals(1000L, config.getFreeFallMaxDurationMs());
        assertEquals(22.0, config.getImpactThreshold(), 0.001);
        assertEquals(800L, config.getImpactWindowMs());
        assertEquals(2000L, config.getStillnessDurationMs());
        assertEquals(200L, config.getStillnessSettlingMs());
        assertEquals(2.5, config.getStillnessGravityTolerance(), 0.001);
        assertEquals(1.0, config.getStillnessMaxVariance(), 0.001);

        assertEquals(10000L, config.getManDownInactivityDurationMs());
        assertEquals(1.2, config.getManDownMovementThreshold(), 0.001);
        assertEquals(0.8, config.getManDownDeltaThreshold(), 0.001);
    }

    @Test
    public void testBuilderCustomValues() {
        DetectorConfig custom = DetectorConfig.builder()
                .freeFallThreshold(4.5)
                .freeFallMinDurationMs(50)
                .impactThreshold(25.0)
                .impactWindowMs(600)
                .stillnessDurationMs(3000)
                .stillnessSettlingMs(300)
                .stillnessGravityTolerance(1.8)
                .stillnessMaxVariance(0.5)
                .manDownInactivityDurationMs(5000)
                .manDownMovementThreshold(2.0)
                .manDownDeltaThreshold(1.5)
                .build();

        assertEquals(4.5, custom.getFreeFallThreshold(), 0.001);
        assertEquals(50L, custom.getFreeFallMinDurationMs());
        assertEquals(25.0, custom.getImpactThreshold(), 0.001);
        assertEquals(600L, custom.getImpactWindowMs());
        assertEquals(3000L, custom.getStillnessDurationMs());
        assertEquals(300L, custom.getStillnessSettlingMs());
        assertEquals(1.8, custom.getStillnessGravityTolerance(), 0.001);
        assertEquals(0.5, custom.getStillnessMaxVariance(), 0.001);

        assertEquals(5000L, custom.getManDownInactivityDurationMs());
        assertEquals(2.0, custom.getManDownMovementThreshold(), 0.001);
        assertEquals(1.5, custom.getManDownDeltaThreshold(), 0.001);
    }
}
