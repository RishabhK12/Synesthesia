package com.hackgt.behindalert;

/** Signal checks, with channel numbers rather than assumed physical left/right mics. */
public final class DiagnosticSignal {
    public static final class Stats {
        public double db0, db1, correlation, differenceDb, identicalFraction, clippedFraction;
        public String observation() {
            if (Math.max(db0, db1) < -65) return "Very quiet: make a test sound before comparing channels.";
            if (clippedFraction > 0.001) return "Clipping detected: move the sound farther away.";
            if (identicalFraction > 0.999) return "Channels are identical; independent microphones are not established.";
            if (Math.abs(correlation) > 0.999) return "Channels closely match; they may be scaled copies.";
            return "Channels differ. This alone does not prove separate, unprocessed microphones.";
        }
    }

    public static final class Accumulator {
        private long frames, identical, clipped;
        private double s0, s1, p0, p1, cross, difference;

        public void add(short[] pcm, int count) {
            if (count < 0 || count > pcm.length || count % 2 != 0)
                throw new IllegalArgumentException("Complete stereo frames required");
            for (int i = 0; i < count; i += 2) {
                double a = pcm[i] / 32768.0, b = pcm[i + 1] / 32768.0;
                s0 += a; s1 += b; p0 += a * a; p1 += b * b; cross += a * b;
                difference += (a - b) * (a - b);
                if (pcm[i] == pcm[i + 1]) identical++;
                if (Math.abs((int) pcm[i]) >= 32760) clipped++;
                if (Math.abs((int) pcm[i + 1]) >= 32760) clipped++;
                frames++;
            }
        }

        public Stats stats() {
            Stats result = new Stats();
            double n = Math.max(1, frames);
            result.db0 = 10 * Math.log10(p0 / n + 1e-12);
            result.db1 = 10 * Math.log10(p1 / n + 1e-12);
            double var0 = Math.max(0, p0 - s0 * s0 / n), var1 = Math.max(0, p1 - s1 * s1 / n);
            double denom = Math.sqrt(var0 * var1);
            result.correlation = denom > 1e-15 ? Math.max(-1, Math.min(1, (cross - s0 * s1 / n) / denom)) : Double.NaN;
            result.differenceDb = 10 * Math.log10((difference + 1e-12) / ((p0 + p1) / 2 + 1e-12));
            result.identicalFraction = identical / n;
            result.clippedFraction = clipped / (2 * n);
            return result;
        }
    }

    public static Stats measure(short[] pcm, int count) {
        Accumulator a = new Accumulator(); a.add(pcm, count); return a.stats();
    }
}
