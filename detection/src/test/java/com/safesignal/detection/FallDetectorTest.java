package com.safesignal.detection;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.*;

public class FallDetectorTest {

    private FallDetector detector;

    @Before
    public void setUp() {
        detector = new FallDetector();
    }

    @Test
    public void everyFallTraceIsDetected() {
        List<String> fallTraces = TraceReader.listTracesWithPrefix("fall_");
        assertFalse("Fall traces must not be empty", fallTraces.isEmpty());

        for (String tracePath : fallTraces) {
            detector.reset();
            List<AccelSample> samples = TraceReader.loadTrace(tracePath);
            List<DetectionEvent> events = new ArrayList<>();

            for (AccelSample sample : samples) {
                Optional<DetectionEvent> event = detector.onSample(sample);
                event.ifPresent(events::add);
            }

            assertEquals("Expected exactly 1 fall detection for " + tracePath + ", but found " + events.size(),
                    1, events.size());
            assertEquals("Detection type must be FALL for " + tracePath,
                    DetectionEvent.Type.FALL, events.get(0).getType());
        }
    }

    @Test
    public void zeroDetectionsOnWalkTraces() {
        List<String> walkTraces = TraceReader.listTracesWithPrefix("walk_");
        assertFalse("Walk traces must not be empty", walkTraces.isEmpty());

        for (String tracePath : walkTraces) {
            detector.reset();
            List<AccelSample> samples = TraceReader.loadTrace(tracePath);

            for (AccelSample sample : samples) {
                Optional<DetectionEvent> event = detector.onSample(sample);
                assertFalse("Walk trace " + tracePath + " must not trigger fall detection", event.isPresent());
            }
        }
    }

    @Test
    public void zeroDetectionsOnSitTraces() {
        List<String> sitTraces = TraceReader.listTracesWithPrefix("sit_");
        assertFalse("Sit traces must not be empty", sitTraces.isEmpty());

        for (String tracePath : sitTraces) {
            detector.reset();
            List<AccelSample> samples = TraceReader.loadTrace(tracePath);

            for (AccelSample sample : samples) {
                Optional<DetectionEvent> event = detector.onSample(sample);
                assertFalse("Sit trace " + tracePath + " must not trigger fall detection", event.isPresent());
            }
        }
    }

    @Test
    public void zeroDetectionsOnDropPhoneTraces() {
        List<String> dropTraces = TraceReader.listTracesWithPrefix("drop_phone_");
        assertFalse("Drop phone traces must not be empty", dropTraces.isEmpty());

        for (String tracePath : dropTraces) {
            detector.reset();
            List<AccelSample> samples = TraceReader.loadTrace(tracePath);

            for (AccelSample sample : samples) {
                Optional<DetectionEvent> event = detector.onSample(sample);
                assertFalse("Drop phone trace " + tracePath + " must not trigger fall detection", event.isPresent());
            }
        }
    }

    @Test
    public void testFreeFallWithoutImpactDoesNotTrigger() {
        detector.reset();
        // Free fall (magnitude ~1.0 m/s^2) for 200 ms, followed by return to normal gravity without high impact
        long t = 1000;
        for (int i = 0; i < 10; i++) {
            Optional<DetectionEvent> event = detector.onSample(new AccelSample(t, 0.5, 0.5, 0.5));
            assertFalse("Free-fall alone should not trigger", event.isPresent());
            t += 20;
        }

        // Gentle landing / catch (magnitude ~12.0 m/s^2, below impact threshold of 22)
        for (int i = 0; i < 50; i++) {
            Optional<DetectionEvent> event = detector.onSample(new AccelSample(t, 0, 9.8, 0));
            assertFalse(event.isPresent());
            t += 20;
        }
    }

    @Test
    public void testImpactWithoutFreeFallDoesNotTrigger() {
        detector.reset();
        long t = 1000;
        // High acceleration shock (e.g. hitting phone on desk or clapping) without preceding free fall
        Optional<DetectionEvent> event1 = detector.onSample(new AccelSample(t, 0, 30.0, 0));
        assertFalse("Impact without preceding free-fall must not trigger", event1.isPresent());

        // Followed by stillness
        for (int i = 0; i < 150; i++) {
            t += 20;
            Optional<DetectionEvent> event = detector.onSample(new AccelSample(t, 0, 9.8, 0));
            assertFalse(event.isPresent());
        }
    }

    @Test
    public void testFreeFallTooBriefDoesNotTrigger() {
        detector.reset();
        long t = 1000;
        // Single glitch sample of low acceleration (20 ms, below min duration of 40 ms)
        detector.onSample(new AccelSample(t, 0.1, 0.1, 0.1));
        t += 20;
        // Impact
        detector.onSample(new AccelSample(t, 0, 28.0, 0));
        t += 20;
        // Stillness
        for (int i = 0; i < 150; i++) {
            Optional<DetectionEvent> event = detector.onSample(new AccelSample(t, 0, 9.8, 0));
            assertFalse("Single-sample free-fall glitch must not trigger", event.isPresent());
            t += 20;
        }
    }

    @Test
    public void testMovementDuringStillnessWindowAbortsDetection() {
        detector.reset();
        long t = 1000;

        // Valid free fall (100 ms)
        for (int i = 0; i < 5; i++) {
            detector.onSample(new AccelSample(t, 0.5, 0.5, 0.5));
            t += 20;
        }

        // Impact
        detector.onSample(new AccelSample(t, 5.0, 26.0, 5.0));
        t += 20;

        // Settling (200 ms)
        for (int i = 0; i < 10; i++) {
            detector.onSample(new AccelSample(t, 0, 9.8, 0));
            t += 20;
        }

        // Immobility for 800 ms (less than required 2000 ms)
        for (int i = 0; i < 40; i++) {
            detector.onSample(new AccelSample(t, 0, 9.8, 0));
            t += 20;
        }

        // Worker moves / picks up phone (active movement spikes magnitude to 16.0 m/s^2)
        detector.onSample(new AccelSample(t, 5.0, 15.0, 4.0));
        t += 20;

        // Even if followed by rest, the candidate was aborted
        for (int i = 0; i < 150; i++) {
            Optional<DetectionEvent> event = detector.onSample(new AccelSample(t, 0, 9.8, 0));
            assertFalse("Candidate should have aborted when movement occurred during stillness window", event.isPresent());
            t += 20;
        }
    }

    @Test
    public void testResetClearsState() {
        detector.reset();
        long t = 1000;
        // Free fall
        for (int i = 0; i < 5; i++) {
            detector.onSample(new AccelSample(t, 0.5, 0.5, 0.5));
            t += 20;
        }
        assertEquals(FallDetector.State.FREE_FALL, detector.getState());

        detector.reset();
        assertEquals(FallDetector.State.MONITORING, detector.getState());
    }
}
