package com.safesignal.detection;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.*;

public class ManDownDetectorTest {

    private ManDownDetector detector;

    @Before
    public void setUp() {
        // Use a test config with 3000 ms inactivity threshold for fast unit testing
        DetectorConfig config = DetectorConfig.builder()
                .manDownInactivityDurationMs(3000)
                .manDownMovementThreshold(1.2)
                .manDownDeltaThreshold(0.8)
                .build();
        detector = new ManDownDetector(config);
    }

    @Test
    public void testContinuousImmobilityTriggersManDown() {
        long t = 1000;
        List<DetectionEvent> events = new ArrayList<>();

        // Feed stationary samples (9.81 m/s^2) for 4000 ms (> 3000 ms threshold)
        for (int i = 0; i <= 200; i++) {
            Optional<DetectionEvent> event = detector.onSample(new AccelSample(t, 0, 9.80665, 0));
            event.ifPresent(events::add);
            t += 20;
        }

        assertEquals("Expected exactly 1 MAN_DOWN detection", 1, events.size());
        assertEquals(DetectionEvent.Type.MAN_DOWN, events.get(0).getType());
        assertTrue(events.get(0).getDetails().contains("Inactivity detected"));
    }

    @Test
    public void testWalkingDoesNotTriggerManDown() {
        List<AccelSample> walkSamples = TraceReader.loadTrace("traces/walk_normal.csv");
        assertFalse("Walk trace must not be empty", walkSamples.isEmpty());

        for (AccelSample sample : walkSamples) {
            Optional<DetectionEvent> event = detector.onSample(sample);
            assertFalse("Walking motion must not trigger MAN_DOWN", event.isPresent());
        }
    }

    @Test
    public void testBriefPauseFollowedByMovementDoesNotTriggerManDown() {
        long t = 1000;

        // Active motion for 1000 ms
        for (int i = 0; i < 50; i++) {
            double dyn = 9.80665 + Math.sin(i * 0.5) * 3.0;
            detector.onSample(new AccelSample(t, 0, dyn, 0));
            t += 20;
        }

        // Brief pause / stationary for 1500 ms (less than 3000 ms threshold)
        for (int i = 0; i < 75; i++) {
            Optional<DetectionEvent> event = detector.onSample(new AccelSample(t, 0, 9.80665, 0));
            assertFalse("Brief pause should not trigger", event.isPresent());
            t += 20;
        }

        // Resumes active movement for 2000 ms
        for (int i = 0; i < 100; i++) {
            double dyn = 9.80665 + Math.sin(i * 0.5) * 3.0;
            Optional<DetectionEvent> event = detector.onSample(new AccelSample(t, 0, dyn, 0));
            assertFalse("Resumed movement should keep detector clear", event.isPresent());
            t += 20;
        }
    }

    @Test
    public void testMandownCollapseTraceTriggersManDown() {
        // Trace mandown_collapse.csv has 10 seconds of immobility from t=3000 to t=14000
        DetectorConfig config = DetectorConfig.builder()
                .manDownInactivityDurationMs(5000)
                .build();
        ManDownDetector collapseDetector = new ManDownDetector(config);

        List<AccelSample> samples = TraceReader.loadTrace("traces/mandown_collapse.csv");
        List<DetectionEvent> events = new ArrayList<>();

        for (AccelSample s : samples) {
            collapseDetector.onSample(s).ifPresent(events::add);
        }

        assertEquals("Expected exactly 1 MAN_DOWN detection on collapse trace", 1, events.size());
        assertEquals(DetectionEvent.Type.MAN_DOWN, events.get(0).getType());
    }

    @Test
    public void testResetClearsState() {
        detector.reset();
        assertEquals(ManDownDetector.State.MONITORING, detector.getState());
        assertEquals(-1, detector.getLastMovementTimestampMs());
    }
}
