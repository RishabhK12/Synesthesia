package com.hackgt.behindalert;

/**
 * The two direction algorithms, side by side, so you can compare them.
 * Both return a lag in samples with the SAME sign convention:
 *   POSITIVE lag = the R channel heard the sound first.
 */
public final class Dsp {
    private Dsp() {}

    // ------------------------------------------------------------------
    // Method A: GCC-PHAT (cross-correlation of the whole waveform)
    // ------------------------------------------------------------------
    public static final class GccResult {
        public final double lag;      // samples (sub-sample, via parabolic fit)
        public final double peak;     // ~1 = one clean delay, ~0.05–0.1 = noise
        public final double[] curve;  // correlation for lags -maxLag..+maxLag
        GccResult(double lag, double peak, double[] curve) { this.lag = lag; this.peak = peak; this.curve = curve; }
    }

    public static final class GccPhat {
        final int b, n;
        final double[] cos, sin, win, xr, xi, yr, yi;
        final int[] rev;

        /** @param blockSize analysis window length, power of 2 (FFT is 2x that so nothing wraps) */
        public GccPhat(int blockSize) {
            if (blockSize < 2 || (blockSize & (blockSize - 1)) != 0) throw new IllegalArgumentException("power of 2");
            b = blockSize; n = 2 * blockSize;
            cos = new double[n / 2]; sin = new double[n / 2];
            for (int i = 0; i < n / 2; i++) { cos[i] = Math.cos(2 * Math.PI * i / n); sin[i] = Math.sin(2 * Math.PI * i / n); }
            int levels = Integer.numberOfTrailingZeros(n);
            rev = new int[n];
            for (int i = 0; i < n; i++) { int x = i, r = 0; for (int j = 0; j < levels; j++) { r = (r << 1) | (x & 1); x >>= 1; } rev[i] = r; }
            win = new double[b];
            for (int i = 0; i < b; i++) win[i] = 0.5 - 0.5 * Math.cos(2 * Math.PI * i / (b - 1));
            xr = new double[n]; xi = new double[n]; yr = new double[n]; yi = new double[n];
        }

        void fft(double[] re, double[] im, boolean inverse) {
            for (int i = 0; i < n; i++) {
                int j = rev[i];
                if (j > i) { double t = re[i]; re[i] = re[j]; re[j] = t; t = im[i]; im[i] = im[j]; im[j] = t; }
            }
            for (int size = 2; size <= n; size <<= 1) {
                int half = size >> 1, step = n / size;
                for (int i = 0; i < n; i += size) {
                    for (int j = i, k = 0; j < i + half; j++, k += step) {
                        double wr = cos[k], wi = inverse ? sin[k] : -sin[k];
                        double ar = re[j + half], ai = im[j + half];
                        double tr = ar * wr - ai * wi, ti = ar * wi + ai * wr;
                        re[j + half] = re[j] - tr; im[j + half] = im[j] - ti;
                        re[j] += tr; im[j] += ti;
                    }
                }
            }
            if (inverse) for (int i = 0; i < n; i++) { re[i] /= n; im[i] /= n; }
        }

        public GccResult run(float[] l, float[] r, int maxLag, double fs, double fLo, double fHi) {
            java.util.Arrays.fill(xr, 0); java.util.Arrays.fill(xi, 0);
            java.util.Arrays.fill(yr, 0); java.util.Arrays.fill(yi, 0);
            int m = Math.min(b, Math.min(l.length, r.length));
            for (int i = 0; i < m; i++) { xr[i] = l[i] * win[i]; yr[i] = r[i] * win[i]; }
            fft(xr, xi, false); fft(yr, yi, false);
            int kLo = (int) Math.floor(fLo * n / fs), kHi = Math.min(n / 2, (int) Math.ceil(fHi * n / fs));
            int used = 0;
            for (int k = 0; k < n; k++) {
                int kk = k <= n / 2 ? k : n - k;
                if (kk < kLo || kk > kHi) { xr[k] = 0; xi[k] = 0; continue; }
                double a = xr[k], bb = xi[k], c = yr[k], d = yi[k];
                double gr = a * c + bb * d, gi = bb * c - a * d;   // X * conj(Y)
                double mag = Math.sqrt(gr * gr + gi * gi);
                if (mag < 1e-20) { xr[k] = 0; xi[k] = 0; continue; }
                xr[k] = gr / mag; xi[k] = gi / mag; used++;         // PHAT: keep phase only
            }
            fft(xr, xi, true);
            double[] curve = new double[2 * maxLag + 1];
            double best = Double.NEGATIVE_INFINITY; int bi = 0;
            for (int k = -maxLag; k <= maxLag; k++) {
                double v = xr[(k + n) % n];
                curve[k + maxLag] = v;
                if (v > best) { best = v; bi = k; }
            }
            double frac = 0;
            if (bi > -maxLag && bi < maxLag) {
                double y0 = curve[bi - 1 + maxLag], y1 = best, y2 = curve[bi + 1 + maxLag];
                double den = y0 - 2 * y1 + y2;
                if (den < 0) frac = 0.5 * (y0 - y2) / den;
            }
            double scale = used > 0 ? (double) n / used : 0;
            for (int i = 0; i < curve.length; i++) curve[i] *= scale;
            return new GccResult(bi + frac, best * scale, curve);
        }
    }

    // ------------------------------------------------------------------
    // Method B: onset timing ("which channel jumped first")
    // This is what you saw by eye: on a clap, one channel goes up before the other.
    // For each channel: remove DC, find its own peak, then find the FIRST sample
    // that reaches `frac` of that peak. Lag = difference of those two indexes.
    // Using each channel's own peak makes it immune to one mic being louder.
    // ------------------------------------------------------------------
    public static final class OnsetResult {
        public final double lag; public final int idxL, idxR; public final boolean valid;
        OnsetResult(double lag, int idxL, int idxR, boolean valid) { this.lag = lag; this.idxL = idxL; this.idxR = idxR; this.valid = valid; }
    }

    public static OnsetResult onset(float[] l, float[] r, double frac, int maxLag) {
        int iL = firstCross(l, frac), iR = firstCross(r, frac);
        if (iL < 0 || iR < 0) return new OnsetResult(0, iL, iR, false);
        int lag = iL - iR;                       // positive => R crossed first
        return new OnsetResult(lag, iL, iR, Math.abs(lag) <= maxLag);  // bigger than physically possible => two different sounds
    }

    static int firstCross(float[] x, double frac) {
        double mean = 0;
        for (float v : x) mean += v;
        mean /= x.length;
        double peak = 0;
        for (float v : x) peak = Math.max(peak, Math.abs(v - mean));
        if (peak <= 1e-6) return -1;
        double th = peak * frac;
        for (int i = 0; i < x.length; i++) if (Math.abs(x[i] - mean) >= th) return i;
        return -1;
    }

    /** Peak of a correlation curve (index 0 = lag -maxLag). Returns {lag, height}. */
    public static double[] peakOf(double[] curve) {
        int maxLag = (curve.length - 1) / 2, bi = 0;
        for (int i = 1; i < curve.length; i++) if (curve[i] > curve[bi]) bi = i;
        double frac = 0;
        if (bi > 0 && bi < curve.length - 1) {
            double y0 = curve[bi - 1], y1 = curve[bi], y2 = curve[bi + 1], den = y0 - 2 * y1 + y2;
            if (den < 0) frac = 0.5 * (y0 - y2) / den;
        }
        return new double[]{ bi - maxLag + frac, curve[bi] };
    }

    /** Level difference in dB, L minus R (positive = louder on the L mic). */
    public static double levelDb(float[] l, float[] r) {
        double a = 0, b = 0;
        for (int i = 0; i < l.length; i++) { a += (double) l[i] * l[i]; b += (double) r[i] * r[i]; }
        return 10 * Math.log10((a + 1e-12) / (b + 1e-12));
    }

    // ------------------------------------------------------------------
    // score: +1 straight ahead, 0 to the side, -1 straight behind
    // ------------------------------------------------------------------
    public static double score(double tauUs, double frontSign, double tauRefUs) {
        return Math.max(-1.5, Math.min(1.5, frontSign * tauUs / tauRefUs));
    }
}
