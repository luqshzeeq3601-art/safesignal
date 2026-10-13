package com.safesignal.detection;

import org.junit.Test;

import static org.junit.Assert.*;

public class AccelSampleTest {

    @Test
    public void testMagnitudeCalculation() {
        // 3-4-0 right triangle + z=0 => magnitude 5.0
        AccelSample sample = new AccelSample(100L, 3.0, 4.0, 0.0);
        assertEquals(5.0, sample.magnitude(), 0.0001);
        assertEquals(5.0, sample.getMagnitude(), 0.0001);

        // 3D vector: sqrt(1^2 + 2^2 + 2^2) = sqrt(9) = 3.0
        AccelSample sample3D = new AccelSample(200L, 1.0, 2.0, 2.0);
        assertEquals(3.0, sample3D.magnitude(), 0.0001);

        assertEquals(200L, sample3D.timestampMs());
        assertEquals(200L, sample3D.getTimestampMs());
        assertEquals(1.0, sample3D.x(), 0.0001);
        assertEquals(1.0, sample3D.getX(), 0.0001);
        assertEquals(2.0, sample3D.y(), 0.0001);
        assertEquals(2.0, sample3D.getY(), 0.0001);
        assertEquals(2.0, sample3D.z(), 0.0001);
        assertEquals(2.0, sample3D.getZ(), 0.0001);
    }

    @Test
    public void testDetectionEventProperties() {
        DetectionEvent event = new DetectionEvent(DetectionEvent.Type.FALL, 12345L, "Impact 28.5 m/s^2");
        assertEquals(DetectionEvent.Type.FALL, event.type());
        assertEquals(DetectionEvent.Type.FALL, event.getType());
        assertEquals(12345L, event.timestampMs());
        assertEquals(12345L, event.getTimestampMs());
        assertEquals("Impact 28.5 m/s^2", event.details());
        assertEquals("Impact 28.5 m/s^2", event.getDetails());
    }
}
