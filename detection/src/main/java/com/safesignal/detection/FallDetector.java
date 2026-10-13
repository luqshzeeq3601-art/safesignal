package com.safesignal.detection;

import java.util.Objects;
import java.util.Optional;

/**
 * Detects human falls from continuous accelerometer samples.
 *
 * A fall is recognized as a 3-phase kinematic sequence:
 * 1. Free-fall / weightlessness (acceleration magnitude drops below freeFallThreshold).
 * 2. Ground impact (violent deceleration spike exceeding impactThreshold within impactWindowMs).
 * 3. Post-impact immobility / stillness (magnitude remains steady near 1g for stillnessDurationMs).
 *
 * Activities such as walking, sitting, or dropping a phone and immediately picking it up
 * are discriminated against by requiring sustained post-impact immobility.
 */
public final class FallDetector {

    private static final double STANDARD_GRAVITY = 9.80665;

    public enum State {
        MONITORING,
        FREE_FALL,
        WAITING_FOR_IMPACT,
        MONITORING_STILLNESS,
        TRIGGERED
    }

    private final DetectorConfig config;

    private State state = State.MONITORING;
    private long freeFallStartMs;
    private long freeFallEndMs;
    private long impactTimestampMs;
    private double peakImpactMagnitude;

    // Running statistics during stillness evaluation
    private int stillnessSampleCount;
    private double stillnessSum;
    private double stillnessSumSq;

    public FallDetector(DetectorConfig config) {
        this.config = Objects.requireNonNull(config, "config cannot be null");
    }

    public FallDetector() {
        this(DetectorConfig.defaultConfig());
    }

    /**
     * Ingests a new accelerometer sample.
     *
     * @param sample the incoming accelerometer reading
     * @return an Optional containing a DetectionEvent if a fall was confirmed, or empty otherwise
     */
    public Optional<DetectionEvent> onSample(AccelSample sample) {
        Objects.requireNonNull(sample, "sample cannot be null");

        double mag = sample.magnitude();
        long t = sample.timestampMs();

        switch (state) {
            case MONITORING -> {
                if (mag <= config.getFreeFallThreshold()) {
                    freeFallStartMs = t;
                    freeFallEndMs = t;
                    state = State.FREE_FALL;
                }
            }

            case FREE_FALL -> {
                if (mag <= config.getFreeFallThreshold()) {
                    freeFallEndMs = t;
                    if (t - freeFallStartMs > config.getFreeFallMaxDurationMs()) {
                        reset();
                    }
                } else if (mag >= config.getImpactThreshold()) {
                    long ffDuration = freeFallEndMs - freeFallStartMs;
                    if (ffDuration >= config.getFreeFallMinDurationMs()) {
                        recordImpact(t, mag);
                    } else {
                        reset();
                    }
                } else {
                    long ffDuration = freeFallEndMs - freeFallStartMs;
                    if (ffDuration >= config.getFreeFallMinDurationMs()) {
                        state = State.WAITING_FOR_IMPACT;
                    } else {
                        reset();
                    }
                }
            }

            case WAITING_FOR_IMPACT -> {
                if (t - freeFallStartMs > config.getImpactWindowMs()) {
                    reset();
                    if (mag <= config.getFreeFallThreshold()) {
                        freeFallStartMs = t;
                        freeFallEndMs = t;
                        state = State.FREE_FALL;
                    }
                } else if (mag >= config.getImpactThreshold()) {
                    recordImpact(t, mag);
                }
            }

            case MONITORING_STILLNESS -> {
                long elapsedSinceImpact = t - impactTimestampMs;

                // During initial rebound settling, ignore chatter but track highest impact spike
                if (elapsedSinceImpact < config.getStillnessSettlingMs()) {
                    if (mag > peakImpactMagnitude) {
                        peakImpactMagnitude = mag;
                    }
                    return Optional.empty();
                }

                // Verify the device is at rest near standard gravity
                double deviationFromGravity = Math.abs(mag - STANDARD_GRAVITY);
                if (deviationFromGravity > config.getStillnessGravityTolerance()) {
                    // Significant active motion observed (e.g. phone was picked up, worker stood up)
                    reset();
                    return Optional.empty();
                }

                stillnessSampleCount++;
                stillnessSum += mag;
                stillnessSumSq += (mag * mag);

                // Reject candidate if variance exceeds tolerance
                if (stillnessSampleCount >= 5) {
                    double mean = stillnessSum / stillnessSampleCount;
                    double variance = (stillnessSumSq / stillnessSampleCount) - (mean * mean);
                    if (variance > config.getStillnessMaxVariance()) {
                        reset();
                        return Optional.empty();
                    }
                }

                // Check if stillness duration requirement is fulfilled
                if (elapsedSinceImpact >= config.getStillnessDurationMs()) {
                    state = State.TRIGGERED;
                    String details = String.format(
                            java.util.Locale.ROOT,
                            "Impact %.1f m/s^2 at %d ms followed by %d ms immobility",
                            peakImpactMagnitude, impactTimestampMs, elapsedSinceImpact
                    );
                    return Optional.of(new DetectionEvent(DetectionEvent.Type.FALL, impactTimestampMs, details));
                }
            }

            case TRIGGERED -> {
                // Stays triggered until explicitly reset by controller/user
                return Optional.empty();
            }
        }

        return Optional.empty();
    }

    private void recordImpact(long timestampMs, double magnitude) {
        impactTimestampMs = timestampMs;
        peakImpactMagnitude = magnitude;
        stillnessSampleCount = 0;
        stillnessSum = 0;
        stillnessSumSq = 0;
        state = State.MONITORING_STILLNESS;
    }

    /**
     * Resets detector internal state machine back to MONITORING.
     */
    public void reset() {
        state = State.MONITORING;
        freeFallStartMs = 0;
        freeFallEndMs = 0;
        impactTimestampMs = 0;
        peakImpactMagnitude = 0;
        stillnessSampleCount = 0;
        stillnessSum = 0;
        stillnessSumSq = 0;
    }

    public State getState() {
        return state;
    }

    public DetectorConfig getConfig() {
        return config;
    }
}
