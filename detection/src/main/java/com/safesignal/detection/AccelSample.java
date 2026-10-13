package com.safesignal.detection;

/**
 * Immutable 3-axis accelerometer sample measured in m/s^2 at a given timestamp.
 */
public record AccelSample(long timestampMs, double x, double y, double z) {

    /**
     * Computes the Euclidean vector magnitude (Signal Vector Magnitude).
     * At rest on Earth, magnitude is approximately 9.81 m/s^2.
     */
    public double magnitude() {
        return Math.sqrt(x * x + y * y + z * z);
    }

    public long getTimestampMs() {
        return timestampMs();
    }

    public double getX() {
        return x();
    }

    public double getY() {
        return y();
    }

    public double getZ() {
        return z();
    }

    public double getMagnitude() {
        return magnitude();
    }
}
