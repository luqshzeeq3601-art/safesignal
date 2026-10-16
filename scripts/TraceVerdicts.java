import com.safesignal.detection.AccelSample;
import com.safesignal.detection.DetectionEvent;
import com.safesignal.detection.FallDetector;
import com.safesignal.detection.ManDownDetector;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Replays trace CSVs through the real :detection classes and prints what they saw,
 * so docs/images charts show the detector's own verdicts instead of a re-implementation.
 *
 * Run (after .\gradlew.bat :detection:classes):
 *   java -cp detection/build/classes/java/main scripts/TraceVerdicts.java <trace.csv>...
 *
 * Output lines (tab separated):
 *   TRACE  <file>  <samples>  <lastMs>
 *   STATE  <file>  <tMs>  <FallDetector.State>      one line per state change
 *   EVENT  <file>  <tMs>  <FALL|MAN_DOWN>  <details>
 */
public final class TraceVerdicts {

    public static void main(String[] args) throws IOException {
        for (String arg : args) {
            Path path = Path.of(arg);
            String name = path.getFileName().toString();
            List<AccelSample> samples = read(path);

            FallDetector fall = new FallDetector();
            ManDownDetector manDown = new ManDownDetector();
            FallDetector.State last = fall.getState();

            System.out.printf(Locale.ROOT, "TRACE\t%s\t%d\t%d%n", name, samples.size(),
                    samples.isEmpty() ? 0 : samples.get(samples.size() - 1).timestampMs());
            for (AccelSample s : samples) {
                print(name, fall.onSample(s));
                print(name, manDown.onSample(s));
                if (fall.getState() != last) {
                    last = fall.getState();
                    System.out.printf(Locale.ROOT, "STATE\t%s\t%d\t%s%n", name, s.timestampMs(), last);
                }
            }
        }
    }

    private static void print(String name, Optional<DetectionEvent> event) {
        event.ifPresent(e -> System.out.printf(Locale.ROOT, "EVENT\t%s\t%d\t%s\t%s%n",
                name, e.timestampMs(), e.type(), e.details()));
    }

    private static List<AccelSample> read(Path path) throws IOException {
        List<AccelSample> samples = new ArrayList<>();
        List<String> lines = Files.readAllLines(path);
        for (String line : lines.subList(1, lines.size())) {
            String[] p = line.trim().split(",");
            if (p.length >= 4) {
                samples.add(new AccelSample(Long.parseLong(p[0].trim()), Double.parseDouble(p[1].trim()),
                        Double.parseDouble(p[2].trim()), Double.parseDouble(p[3].trim())));
            }
        }
        return samples;
    }
}
