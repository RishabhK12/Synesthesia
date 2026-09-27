package com.hackgt.behindalert.listen;

/** Stateful 48 -> 16 kHz decimator, channel 0 only. 127-tap Blackman-windowed FIR. */
public final class MonoResampler {
    private static final int TAPS = 127;
    private final double[] coefficients = new double[TAPS];
    private final float[] history = new float[TAPS];
    private int position, phase;
    public MonoResampler() {
        double sum = 0, cutoff = 7000.0 / 48000;
        for (int i = 0; i < TAPS; i++) {
            int offset = i - (TAPS - 1) / 2;
            double sinc = offset == 0 ? 2 * cutoff : Math.sin(2 * Math.PI * cutoff * offset) / (Math.PI * offset);
            double window = .42 - .5 * Math.cos(2 * Math.PI * i / (TAPS - 1)) + .08 * Math.cos(4 * Math.PI * i / (TAPS - 1));
            coefficients[i] = sinc * window; sum += coefficients[i];
        }
        for (int i = 0; i < TAPS; i++) coefficients[i] /= sum;
    }
    public float[] process(short[] stereo) {
        if (stereo.length % 2 != 0) throw new IllegalArgumentException("Incomplete stereo frame");
        float[] output = new float[(phase + stereo.length / 2) / 3];
        int n = 0;
        for (int i = 0; i < stereo.length; i += 2) {
            history[position] = stereo[i] / 32768f;
            if (++phase == 3) {
                double sum = 0;
                for (int k = 0; k < TAPS; k++) sum += coefficients[k] * history[(position - k + TAPS) % TAPS];
                output[n++] = (float) sum; phase = 0;
            }
            position = (position + 1) % TAPS;
        }
        return output;
    }
}
