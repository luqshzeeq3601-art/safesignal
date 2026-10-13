package com.safesignal.detection;

import java.util.Objects;
import java.util.Optional;

/**
 * Detects prolonged immobility / man-down situations from continuous accelerometer samples.
 *
 * If a worker does not move (dynamic acceleration remains below movementThreshold and delta
 * between successive samples remains below deltaThreshold) for longer than
 * manDownInactivityDurationMs, a MAN_DOWN event is triggered.
 */
public final class ManDownDetector {

    private static final double STANDARD_GRAVITY = 9.80665;

    public enum State {
        MONITORING,
        TRIGGERED
    }

    private final DetectorConfig config;

    private State state = State.MONITORING;
    private long lastMovementTimestampMs = -1;
    private AccelSample previousSample;

    public ManDownDetector(DetectorConfig config) {
        this.config = Objects.requireNonNull(config, "config cannot be null");
    }

    public ManDownDetector() {
        this(DetectorConfig.defaultConfig());
    }

    /**
     * Ingests a new accelerometer sample.
     *
     * @param sample the incoming accelerometer reading
     * @return an Optional containing a DetectionEvent if immobility exceeded the threshold, or empty otherwise
     */
    public Optional<DetectionEvent> onSample(AccelSample sample) {
        Objects.requireNonNull(sample, "sample cannot be null");

        long t = sample.timestampMs();
        if (lastMovementTimestampMs < 0) {
            lastMovementTimestampMs = t;
            previousSample = sample;
            return Optional.empty();
        }

        boolean isMoving = checkMovement(sample);

        if (isMoving) {
            lastMovementTimestampMs = t;
            previousSample = sample;
            if (state == State.TRIGGERED) {
                // Movement detected after trigger resets back to active monitoring
                state = State.MONITORING;
            }
            return Optional.empty();
        }

        previousSample = sample;

        if (state == State.MONITORING) {
            long inactivityDuration = t - lastMovementTimestampMs;
            if (inactivityDuration >= config.getManDownInactivityDurationMs()) {
                state = State.TRIGGERED;
                String details = String.format(
                        java.util.Locale.ROOT,
                        "Inactivity detected for %d ms (threshold: %d ms)",
                        inactivityDuration, config.getManDownInactivityDurationMs()
                );
                return Optional.of(new DetectionEvent(DetectionEvent.Type.MAN_DOWN, t, details));
            }
        }

        return Optional.empty();
    }

    private boolean checkMovement(AccelSample sample) {
        // 1. Dynamic acceleration deviation from 1g
        double dynamicAccel = Math.abs(sample.magnitude() - STANDARD_GRAVITY);
        if (dynamicAccel > config.getManDownMovementThreshold()) {
            return true;
        }

        // 2. Vector delta relative to previous sample (detects tilt or slow rotation)
        if (previousSample != null) {
            double dx = sample.x() - previousSample.x();
            double dy = sample.y() - previousSample.y();
            double dz = sample.z() - previousSample.z();
            double deltaMagnitude = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (deltaMagnitude > config.getManDownDeltaThreshold()) {
                return true;
            }
        }

        return false;
    }

    /**
     * Resets immobility timer and state machine back to MONITORING.
     */
    public void reset() {
        state = State.MONITORING;
        lastMovementTimestampMs = -1;
        previousSample = null;
    }

    public State getState() {
        return state;
    }

    public DetectorConfig getConfig() {
        return config;
    }

    public long getLastMovementTimestampMs() {
        return lastMovementTimestampMs;
    }
}
