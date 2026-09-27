package com.hackgt.behindalert.listen;

/** Immutable audio owned by one listening session. Times use elapsed realtime. */
public final class AudioBlock {
    public final long startMs, endMs;
    public final float[] mono;
    public final short[] stereo;
    public AudioBlock(long startMs, float[] mono, short[] stereo) {
        this.startMs = startMs; this.endMs = startMs + mono.length * 1000L / 16000;
        this.mono = mono; this.stereo = stereo;
    }
}
