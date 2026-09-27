package com.hackgt.behindalert.listen;

import com.hackgt.behindalert.CompassModel;
import com.hackgt.behindalert.Dsp;

/** Optional compass branch. Its result is deliberately not attached to classified events. */
public final class DirectionMonitor implements DetectorWorker.Processor {
    public interface Listener { void direction(String text); }
    private final CompassModel model = new CompassModel();
    private final Dsp.GccPhat gcc = new Dsp.GccPhat(4096);
    private final float[] left = new float[4096], right = new float[4096];
    private final Listener listener;
    private volatile double heading = Double.NaN;
    private volatile long headingMs;
    private double floor = -55;
    private int baseline;
    public DirectionMonitor(String calibration, Listener listener) { model.deserialize(calibration); this.listener = listener; }
    public void heading(double degrees, long timestampMs) { heading = degrees; headingMs = timestampMs; }
    @Override public void accept(AudioBlock block) {
        if (!model.usable()) { listener.direction("Direction: calibrate in Sound compass"); return; }
        if (!Double.isFinite(heading) || block.endMs - headingMs > 600) {
            model.clearTrack(); listener.direction("Direction: hold phone landscape, screen upright"); return;
        }
        int offset = block.stereo.length / 2 - 4096; double energy = 0; int identical = 0;
        for (int i = 0; i < 4096; i++) {
            left[i] = block.stereo[2 * (offset + i)] / 32768f;
            right[i] = block.stereo[2 * (offset + i) + 1] / 32768f;
            energy += (left[i] * left[i] + right[i] * right[i]) * .5;
            if (left[i] == right[i]) identical++;
        }
        double db = 10 * Math.log10(energy / 4096 + 1e-12);
        if (baseline++ < 10) { floor = baseline == 1 ? db : .8 * floor + .2 * db; return; }
        if (db <= Math.max(-58, floor + 4) || identical >= 4092) {
            floor = Math.min(-25, .995 * floor + .005 * db); model.expire(block.endMs);
            listener.direction("Direction: waiting for a clearer sound"); return;
        }
        Dsp.GccResult result = gcc.run(left, right, 42, 48000, 150, 12000);
        CompassModel.Estimate estimate = model.observe(block.endMs, heading, result.lag, result.peak);
        listener.direction(estimate == null ? "Direction: uncertain" : estimate.resolved ?
                "Approx. sound bearing: " + estimate.primary + "°" :
                "Two possible bearings: " + estimate.primary + "° / " + estimate.alternative + "°");
    }
    @Override public void reset() { model.clearTrack(); baseline = 0; }
    @Override public void close() { }
}
