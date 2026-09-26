package com.hackgt.behindalert;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Left / middle / right classifier learned from calibration claps.
 *
 * Three cues are measured for every sound:
 *   0 = delay from GCC-PHAT (µs)     – which mic heard the whole waveform first
 *   1 = delay from onset timing (µs) – which channel "jumped" first
 *   2 = level difference (dB)        – which mic got it louder (head/phone shadow)
 *
 * Calibration stores the claps you make on your LEFT, RIGHT and (optionally) in FRONT.
 * Each cue is then mapped so that your left claps score -1, front claps 0 and right claps +1.
 * Cues that separate left from right cleanly (big gap, little scatter) get a bigger vote.
 */
public final class Direction {
    public static final int GCC = 0, ONSET = 1, LEVEL = 2, NF = 3;
    public static final int LEFT = 0, MID = 1, RIGHT = 2;
    public static final String[] FEATURE = { "Delay (GCC)", "Onset", "Level" };
    public static final String[] UNIT = { "µs", "µs", "dB" };
    public static final String[] CLASS = { "LEFT", "middle", "RIGHT" };
    /** smallest believable scatter per cue, so 4 lucky claps can't claim infinite precision */
    static final double[] MIN_SD = { 25, 25, 0.75 };
    /** fallback scale when uncalibrated: ~16 cm mic spacing, 6 dB */
    static final double[] DEFAULT_SCALE = { 466, 466, -6 };   // level: louder on L = left

    private final List<List<double[]>> samples = new ArrayList<>();
    public final double[][] center = new double[3][NF], spread = new double[3][NF];
    public final boolean[][] has = new boolean[3][NF];
    public final double[] weight = new double[NF];

    public Direction() { for (int c = 0; c < 3; c++) samples.add(new ArrayList<>()); }

    public void clear(int cls) { samples.get(cls).clear(); recompute(); }
    public void clearAll() { for (List<double[]> s : samples) s.clear(); recompute(); }
    public int count(int cls) { return samples.get(cls).size(); }

    /** @param f feature values; use Double.NaN for a cue that wasn't measurable on this sound */
    public void add(int cls, double[] f) { samples.get(cls).add(f.clone()); recompute(); }

    public boolean calibrated() { for (int k = 0; k < NF; k++) if (weight[k] > 0) return true; return false; }

    public void recompute() {
        for (int c = 0; c < 3; c++)
            for (int k = 0; k < NF; k++) {
                List<Double> v = new ArrayList<>();
                for (double[] f : samples.get(c)) if (!Double.isNaN(f[k])) v.add(f[k]);
                has[c][k] = v.size() >= 2;
                if (!has[c][k]) { center[c][k] = 0; spread[c][k] = 0; continue; }
                double med = median(v);
                List<Double> dev = new ArrayList<>();
                for (double x : v) dev.add(Math.abs(x - med));
                center[c][k] = med;
                spread[c][k] = Math.max(MIN_SD[k], 1.4826 * median(dev));   // robust std (MAD)
            }
        for (int k = 0; k < NF; k++) weight[k] = computeWeight(k);
    }

    /** separation of the left and right clusters in units of their scatter; 0 = useless cue */
    double computeWeight(int k) {
        if (!has[LEFT][k] || !has[RIGHT][k]) return 0;
        double gap = Math.abs(center[RIGHT][k] - center[LEFT][k]);
        double w = gap / (spread[LEFT][k] + spread[RIGHT][k]);
        if (has[MID][k]) {                       // front claps must sit between left and right
            double lo = Math.min(center[LEFT][k], center[RIGHT][k]), hi = Math.max(center[LEFT][k], center[RIGHT][k]);
            if (center[MID][k] < lo || center[MID][k] > hi) return 0;
        }
        return w < 1 ? 0 : w;                    // clusters overlap -> don't trust this cue
    }

    double mid(int k) { return has[MID][k] ? center[MID][k] : 0.5 * (center[LEFT][k] + center[RIGHT][k]); }

    /** maps one cue to a score: your left claps -> -1, front -> 0, right -> +1 */
    public double featureScore(int k, double f) {
        if (Double.isNaN(f)) return Double.NaN;
        if (weight[k] <= 0) return calibrated() ? Double.NaN : clamp(f / DEFAULT_SCALE[k]);
        double m = mid(k), t = f - m, dR = center[RIGHT][k] - m, dL = center[LEFT][k] - m;
        if (t * dR >= 0) return clamp(dR == 0 ? 0 : t / dR);
        return clamp(dL == 0 ? 0 : -t / dL);
    }

    public static final class Result {
        public final double[] s = new double[NF];
        public final int[] zone = new int[NF];      // -1 = cue not usable on this sound
        public double combined; public int combinedZone;
    }

    /** @param mode -1 = combine all cues, otherwise use only cue `mode` */
    public Result classify(double[] f, double threshold, int mode) {
        Result r = new Result();
        double num = 0, den = 0;
        boolean cal = calibrated();
        for (int k = 0; k < NF; k++) {
            r.s[k] = featureScore(k, f[k]);
            r.zone[k] = Double.isNaN(r.s[k]) ? -1 : zoneOf(r.s[k], threshold);
            double w = cal ? weight[k] : (k == LEVEL ? 0.5 : 1);
            if (!Double.isNaN(r.s[k]) && w > 0 && (mode < 0 || mode == k)) { num += w * r.s[k]; den += w; }
        }
        r.combined = den > 0 ? num / den : Double.NaN;
        r.combinedZone = Double.isNaN(r.combined) ? -1 : zoneOf(r.combined, threshold);
        return r;
    }

    static int zoneOf(double s, double thr) { return s <= -thr ? LEFT : s >= thr ? RIGHT : MID; }
    static double clamp(double x) { return Math.max(-1.5, Math.min(1.5, x)); }
    static double median(List<Double> v) {
        double[] a = new double[v.size()];
        for (int i = 0; i < a.length; i++) a[i] = v.get(i);
        Arrays.sort(a);
        int n = a.length;
        return n % 2 == 1 ? a[n / 2] : 0.5 * (a[n / 2 - 1] + a[n / 2]);
    }

    /** human-readable calibration report */
    public String report() {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format(Locale.US, "claps: left %d · front %d · right %d\n", count(LEFT), count(MID), count(RIGHT)));
        for (int k = 0; k < NF; k++) {
            sb.append(String.format(Locale.US, "%-11s ", FEATURE[k]));
            for (int c : new int[]{ LEFT, MID, RIGHT }) {
                String lbl = c == LEFT ? "L" : c == MID ? "F" : "R";
                sb.append(has[c][k] ? String.format(Locale.US, "%s %+.0f±%.0f  ", lbl, center[c][k], spread[c][k]) : lbl + " –  ");
            }
            if (!has[LEFT][k] || !has[RIGHT][k]) sb.append("(needs L+R)");
            else if (weight[k] <= 0) sb.append("✗ can't tell L/R");
            else sb.append(String.format(Locale.US, "✓ %.1f× %s", weight[k], weight[k] >= 3 ? "strong" : "ok"));
            sb.append('\n');
        }
        return sb.toString().trim();
    }

    // ---- persistence: "cls:f0,f1,f2;cls:..." ----
    public String serialize() {
        StringBuilder sb = new StringBuilder();
        for (int c = 0; c < 3; c++)
            for (double[] f : samples.get(c))
                sb.append(c).append(':').append(f[0]).append(',').append(f[1]).append(',').append(f[2]).append(';');
        return sb.toString();
    }
    public void deserialize(String s) {
        for (List<double[]> l : samples) l.clear();
        if (s != null && !s.isEmpty())
            for (String item : s.split(";")) {
                try {
                    String[] a = item.split(":"); String[] v = a[1].split(",");
                    samples.get(Integer.parseInt(a[0])).add(new double[]{ Double.parseDouble(v[0]), Double.parseDouble(v[1]), Double.parseDouble(v[2]) });
                } catch (Exception ignored) {}
            }
        recompute();
    }
}
