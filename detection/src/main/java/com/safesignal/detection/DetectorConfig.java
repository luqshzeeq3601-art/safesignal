package com.safesignal.detection;

/**
 * Configuration thresholds and timing windows for fall and man-down detectors.
 */
public final class DetectorConfig {

    // Fall detection thresholds
    private final double freeFallThreshold;
    private final long freeFallMinDurationMs;
    private final long freeFallMaxDurationMs;
    private final double impactThreshold;
    private final long impactWindowMs;
    private final long stillnessDurationMs;
    private final long stillnessSettlingMs;
    private final double stillnessGravityTolerance;
    private final double stillnessMaxVariance;

    // Man-down thresholds
    private final long manDownInactivityDurationMs;
    private final double manDownMovementThreshold;
    private final double manDownDeltaThreshold;

    private DetectorConfig(Builder builder) {
        this.freeFallThreshold = builder.freeFallThreshold;
        this.freeFallMinDurationMs = builder.freeFallMinDurationMs;
        this.freeFallMaxDurationMs = builder.freeFallMaxDurationMs;
        this.impactThreshold = builder.impactThreshold;
        this.impactWindowMs = builder.impactWindowMs;
        this.stillnessDurationMs = builder.stillnessDurationMs;
        this.stillnessSettlingMs = builder.stillnessSettlingMs;
        this.stillnessGravityTolerance = builder.stillnessGravityTolerance;
        this.stillnessMaxVariance = builder.stillnessMaxVariance;
        this.manDownInactivityDurationMs = builder.manDownInactivityDurationMs;
        this.manDownMovementThreshold = builder.manDownMovementThreshold;
        this.manDownDeltaThreshold = builder.manDownDeltaThreshold;
    }

    public static DetectorConfig defaultConfig() {
        return new Builder().build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public double getFreeFallThreshold() {
        return freeFallThreshold;
    }

    public long getFreeFallMinDurationMs() {
        return freeFallMinDurationMs;
    }

    public long getFreeFallMaxDurationMs() {
        return freeFallMaxDurationMs;
    }

    public double getImpactThreshold() {
        return impactThreshold;
    }

    public long getImpactWindowMs() {
        return impactWindowMs;
    }

    public long getStillnessDurationMs() {
        return stillnessDurationMs;
    }

    public long getStillnessSettlingMs() {
        return stillnessSettlingMs;
    }

    public double getStillnessGravityTolerance() {
        return stillnessGravityTolerance;
    }

    public double getStillnessMaxVariance() {
        return stillnessMaxVariance;
    }

    public long getManDownInactivityDurationMs() {
        return manDownInactivityDurationMs;
    }

    public double getManDownMovementThreshold() {
        return manDownMovementThreshold;
    }

    public double getManDownDeltaThreshold() {
        return manDownDeltaThreshold;
    }

    public static final class Builder {
        private double freeFallThreshold = 5.0; // m/s^2 (~0.5g)
        private long freeFallMinDurationMs = 40; // minimum duration to filter sensor glitch
        private long freeFallMaxDurationMs = 1000; // maximum free fall duration
        private double impactThreshold = 22.0; // m/s^2 (> 2.2g)
        private long impactWindowMs = 800; // impact must follow within 800 ms of free fall
        private long stillnessDurationMs = 2000; // post-impact stillness required (2 s)
        private long stillnessSettlingMs = 200; // post-impact rebound settling time
        private double stillnessGravityTolerance = 2.5; // deviation from 9.81 m/s^2 allowed
        private double stillnessMaxVariance = 1.0; // max magnitude variance allowed during stillness

        private long manDownInactivityDurationMs = 10000; // default 10 s motionless
        private double manDownMovementThreshold = 1.2; // dynamic acceleration deviation from gravity
        private double manDownDeltaThreshold = 0.8; // sample-to-sample vector delta

        public Builder freeFallThreshold(double value) {
            this.freeFallThreshold = value;
            return this;
        }

        public Builder freeFallMinDurationMs(long value) {
            this.freeFallMinDurationMs = value;
            return this;
        }

        public Builder freeFallMaxDurationMs(long value) {
            this.freeFallMaxDurationMs = value;
            return this;
        }

        public Builder impactThreshold(double value) {
            this.impactThreshold = value;
            return this;
        }

        public Builder impactWindowMs(long value) {
            this.impactWindowMs = value;
            return this;
        }

        public Builder stillnessDurationMs(long value) {
            this.stillnessDurationMs = value;
            return this;
        }

        public Builder stillnessSettlingMs(long value) {
            this.stillnessSettlingMs = value;
            return this;
        }

        public Builder stillnessGravityTolerance(double value) {
            this.stillnessGravityTolerance = value;
            return this;
        }

        public Builder stillnessMaxVariance(double value) {
            this.stillnessMaxVariance = value;
            return this;
        }

        public Builder manDownInactivityDurationMs(long value) {
            this.manDownInactivityDurationMs = value;
            return this;
        }

        public Builder manDownMovementThreshold(double value) {
            this.manDownMovementThreshold = value;
            return this;
        }

        public Builder manDownDeltaThreshold(double value) {
            this.manDownDeltaThreshold = value;
            return this;
        }

        public DetectorConfig build() {
            return new DetectorConfig(this);
        }
    }
}
