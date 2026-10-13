package com.safesignal.detection;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Utility to load labelled accelerometer sensor traces from test resources.
 */
public final class TraceReader {

    private TraceReader() {}

    /**
     * Loads accelerometer samples from a CSV file in test resources (e.g. "traces/fall_forward.csv").
     */
    public static List<AccelSample> loadTrace(String resourcePath) {
        List<AccelSample> samples = new ArrayList<>();
        try (InputStream is = TraceReader.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new IllegalArgumentException("Resource trace not found on classpath: " + resourcePath);
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                String header = reader.readLine();
                if (header == null) {
                    return samples;
                }
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty()) {
                        continue;
                    }
                    String[] parts = line.split(",");
                    if (parts.length >= 4) {
                        long tMs = Long.parseLong(parts[0].trim());
                        double x = Double.parseDouble(parts[1].trim());
                        double y = Double.parseDouble(parts[2].trim());
                        double z = Double.parseDouble(parts[3].trim());
                        samples.add(new AccelSample(tMs, x, y, z));
                    }
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to read trace " + resourcePath, e);
        }
        return samples;
    }

    /**
     * Lists resource trace names with a given prefix (e.g. "fall_", "walk_", "sit_", "drop_phone_").
     */
    public static List<String> listTracesWithPrefix(String prefix) {
        URL url = TraceReader.class.getClassLoader().getResource("traces");
        if (url != null && "file".equals(url.getProtocol())) {
            try {
                File dir = new File(url.toURI());
                File[] files = dir.listFiles((d, name) -> name.startsWith(prefix) && name.endsWith(".csv"));
                if (files != null) {
                    List<String> result = new ArrayList<>();
                    for (File f : files) {
                        result.add("traces/" + f.getName());
                    }
                    Collections.sort(result);
                    return result;
                }
            } catch (Exception ignored) {
                // Fallback to static list below
            }
        }

        // Fallback list of known standard traces
        List<String> defaults = Arrays.asList(
                "traces/fall_forward.csv",
                "traces/fall_backward.csv",
                "traces/fall_lateral.csv",
                "traces/fall_faint.csv",
                "traces/walk_normal.csv",
                "traces/walk_fast.csv",
                "traces/walk_stairs.csv",
                "traces/sit_down.csv",
                "traces/sit_quick.csv",
                "traces/drop_phone_pickup.csv",
                "traces/drop_phone_desk.csv",
                "traces/drop_phone_bounce.csv",
                "traces/mandown_collapse.csv"
        );
        List<String> matched = new ArrayList<>();
        for (String name : defaults) {
            String base = name.substring("traces/".length());
            if (base.startsWith(prefix)) {
                matched.add(name);
            }
        }
        return matched;
    }
}
