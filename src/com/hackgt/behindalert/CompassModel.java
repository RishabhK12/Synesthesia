package com.hackgt.behindalert;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** Eight-direction, device-specific lag calibration and single-source motion tracking. */
public final class CompassModel {
    public static final String[] NAMES = {"Front", "Front-right", "Right", "Behind-right",
            "Behind", "Behind-left", "Left", "Front-left"};
    public static final int SECTORS = 8, REQUIRED = 4;
    private static final double SIGMA_FLOOR = 2.5;
    private final List<List<Double>> samples = new ArrayList<>();
    private final ArrayDeque<Observation> track = new ArrayDeque<>();
    private double trackYaw = Double.NaN;
    private long lastObservationMs = -1;

    public CompassModel() { for (int i = 0; i < SECTORS; i++) samples.add(new ArrayList<>()); }
    public int count(int sector) { return samples.get(sector).size(); }
    public void add(int sector, double lagSamples) {
        if (sector < 0 || sector >= SECTORS || !Double.isFinite(lagSamples) || Math.abs(lagSamples) > 42)
            throw new IllegalArgumentException("Invalid calibration event");
        samples.get(sector).add(lagSamples);
        clearTrack();
    }
    public void clearSector(int sector) { samples.get(sector).clear(); clearTrack(); }
    public void clearAll() { for (List<Double> s : samples) s.clear(); clearTrack(); }
    public boolean complete() {
        for (int i = 0; i < SECTORS; i++) if (count(i) < REQUIRED) return false;
        return true;
    }
    /** A nearly mono or mislabeled left/right calibration cannot support a compass. */
    public boolean usable() {
        return complete() && Math.abs(center(2) - center(6)) >= 8;
    }
    public double center(int sector) { return median(samples.get(sector)); }
    public double scatter(int sector) {
        List<Double> values = samples.get(sector);
        if (values.size() < 2) return SIGMA_FLOOR;
        double mid = median(values);
        double[] deviation = new double[values.size()];
        for (int i = 0; i < values.size(); i++) deviation[i] = Math.abs(values.get(i) - mid);
        return Math.max(SIGMA_FLOOR, 1.4826 * median(deviation));
    }
    public double predictedLag(double angle) {
        if (!complete()) return Double.NaN;
        double wrapped = wrap360(angle) / 45.0;
        int low = (int) Math.floor(wrapped), high = (low + 1) % SECTORS;
        double fraction = wrapped - low;
        return center(low) * (1 - fraction) + center(high) * fraction;
    }
    private double predictedScatter(double angle) {
        double wrapped = wrap360(angle) / 45.0;
        int low = (int) Math.floor(wrapped), high = (low + 1) % SECTORS;
        double fraction = wrapped - low;
        return scatter(low) * (1 - fraction) + scatter(high) * fraction;
    }

    public static final class Estimate {
        /** Candidate bearings in degrees relative to the phone at the latest observation. */
        public final int primary, alternative, observations;
        public final double turnDegrees, bestError, gap;
        public final boolean resolved;
        Estimate(int primary, int alternative, int observations, double turnDegrees,
                 double bestError, double gap, boolean resolved) {
            this.primary = primary; this.alternative = alternative; this.observations = observations;
            this.turnDegrees = turnDegrees; this.bestError = bestError; this.gap = gap; this.resolved = resolved;
        }
    }
    private static final class Observation {
        final long timeMs; final double yaw, lag, weight;
        Observation(long timeMs, double yaw, double lag, double weight) {
            this.timeMs = timeMs; this.yaw = yaw; this.lag = lag; this.weight = weight;
        }
    }

    public void clearTrack() { track.clear(); trackYaw = Double.NaN; lastObservationMs = -1; }
    public void expire(long nowMs) { if (lastObservationMs >= 0 && nowMs - lastObservationMs > 1400) clearTrack(); }
    public Estimate observe(long timeMs, double yawDegrees, double lagSamples, double peak) {
        if (!usable() || !Double.isFinite(yawDegrees) || !Double.isFinite(lagSamples)
                || !Double.isFinite(peak) || peak < 0.12 || Math.abs(lagSamples) > 42) return null;
        expire(timeMs);
        if (track.isEmpty()) trackYaw = yawDegrees;
        if (!track.isEmpty() && timeMs - lastObservationMs < 140) return estimate(yawDegrees);
        lastObservationMs = timeMs;
        track.addLast(new Observation(timeMs, yawDegrees, lagSamples, Math.min(1.0, peak)));
        while (!track.isEmpty() && (timeMs - track.peekFirst().timeMs > 5000 || track.size() > 24)) track.removeFirst();
        trackYaw = track.peekFirst().yaw;
        return estimate(yawDegrees);
    }
    public Estimate estimate(double currentYaw) {
        if (track.isEmpty() || !Double.isFinite(currentYaw) || !usable()) return null;
        // Do not let overlapping windows or UI refreshes reveal a single weak observation.
        if (track.size() < 2 && track.peekFirst().weight < .20) return null;
        double turn = wrap180(currentYaw - trackYaw);
        double span = 0;
        for (Observation o : track) span = Math.max(span, Math.abs(wrap180(o.yaw - trackYaw)));
        double[] scores = new double[72];
        for (int i = 0; i < scores.length; i++) {
            double worldBearing = i * 5.0;
            double weighted = 0, weightSum = 0;
            for (Observation o : track) {
                double relative = wrap360(worldBearing - wrap180(o.yaw - trackYaw));
                double sigma = Math.max(3.0, predictedScatter(relative));
                double residual = (o.lag - predictedLag(relative)) / sigma;
                weighted += o.weight * residual * residual;
                weightSum += o.weight;
            }
            scores[i] = weighted / weightSum;
        }
        int best = 0;
        for (int i = 1; i < scores.length; i++) if (scores[i] < scores[best]) best = i;
        if (scores[best] > 4) return null;
        int second = -1;
        for (int i = 0; i < scores.length; i++) {
            if (Math.abs(wrap180((i - best) * 5.0)) < 55) continue;
            if (second < 0 || scores[i] < scores[second]) second = i;
        }
        double gap = second < 0 ? 0 : scores[second] - scores[best];
        // Multiple nearby windows from one transient are correlated. Motion and a
        // distinct alternative are required before showing a single bearing.
        boolean resolved = track.size() >= 3 && span >= 25 && scores[best] <= 4 && gap >= 1.5;
        return new Estimate((int) Math.round(wrap360(best * 5.0 - turn)),
                (int) Math.round(wrap360(second * 5.0 - turn)), track.size(), span,
                scores[best], gap, resolved);
    }

    public String serialize() {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < SECTORS; i++) {
            if (i > 0) b.append(';');
            for (int j = 0; j < count(i); j++) {
                if (j > 0) b.append(',');
                b.append(String.format(Locale.US, "%.6f", samples.get(i).get(j)));
            }
        }
        return b.toString();
    }
    public void deserialize(String text) {
        clearAll();
        if (text == null) return;
        String[] sectors = text.split(";", -1);
        if (sectors.length != SECTORS) return;
        for (int i = 0; i < SECTORS; i++) {
            if (sectors[i].isEmpty()) continue;
            for (String part : sectors[i].split(",")) {
                try { add(i, Double.parseDouble(part)); } catch (RuntimeException ignored) { }
            }
        }
    }

    static double median(List<Double> values) {
        if (values.isEmpty()) return Double.NaN;
        double[] sorted = new double[values.size()];
        for (int i = 0; i < sorted.length; i++) sorted[i] = values.get(i);
        return median(sorted);
    }
    static double median(double[] values) {
        Arrays.sort(values);
        return values.length % 2 == 0 ? (values[values.length / 2 - 1] + values[values.length / 2]) / 2
                : values[values.length / 2];
    }
    public static double wrap360(double angle) {
        double wrapped = angle % 360;
        return wrapped < 0 ? wrapped + 360 : wrapped;
    }
    public static double wrap180(double angle) {
        double wrapped = wrap360(angle);
        return wrapped >= 180 ? wrapped - 360 : wrapped;
    }
}
