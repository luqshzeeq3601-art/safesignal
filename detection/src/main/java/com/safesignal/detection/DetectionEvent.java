package com.safesignal.detection;

import java.util.Objects;

/**
 * Event produced when an accelerometer detector identifies an emergency pattern.
 */
public record DetectionEvent(Type type, long timestampMs, String details) {

    public enum Type {
        FALL,
        MAN_DOWN
    }

    public DetectionEvent {
        Objects.requireNonNull(type, "Detection type cannot be null");
        if (details == null) {
            details = "";
        }
    }

    public Type getType() {
        return type();
    }

    public long getTimestampMs() {
        return timestampMs();
    }

    public String getDetails() {
        return details();
    }
}
