package com.hackgt.behindalert.listen;

import android.media.*;
import android.os.SystemClock;
import java.util.Arrays;

/** Single owner of AudioRecord; listeners receive the same unmodified stereo frames. */
public final class StereoCapture implements AutoCloseable {
    public interface Listener { void audio(AudioBlock block); void failed(String reason); }
    private volatile boolean running;
    private Thread thread;
    private final android.content.Context context;
    public StereoCapture(android.content.Context context) { this.context = context.getApplicationContext(); }
    public void start(Listener listener) {
        running = true;
        thread = new Thread(() -> {
            AudioRecord local = null;
            try {
                if (context.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED)
                    throw new SecurityException("Microphone permission is required");
                int min = AudioRecord.getMinBufferSize(48000, AudioFormat.CHANNEL_IN_STEREO, AudioFormat.ENCODING_PCM_16BIT);
                if (min <= 0) throw new IllegalStateException("48 kHz stereo is unavailable");
                local = new AudioRecord(MediaRecorder.AudioSource.CAMCORDER, 48000,
                        AudioFormat.CHANNEL_IN_STEREO, AudioFormat.ENCODING_PCM_16BIT, Math.max(min, 38400));
                if (local.getState() != AudioRecord.STATE_INITIALIZED || local.getChannelCount() != 2 || local.getSampleRate() != 48000)
                    throw new IllegalStateException("Stereo microphone could not open");
                if (!running) return;
                local.startRecording();
                if (local.getRecordingState() != AudioRecord.RECORDSTATE_RECORDING) throw new IllegalStateException("Microphone did not start");
                MonoResampler resampler = new MonoResampler();
                long origin = SystemClock.elapsedRealtime(), frames = 0;
                short[] pcm = new short[9600];
                while (running) {
                    int offset = 0;
                    long waitingSince = SystemClock.elapsedRealtime();
                    while (running && offset < pcm.length) {
                        int n = local.read(pcm, offset, pcm.length - offset, AudioRecord.READ_NON_BLOCKING);
                        if (n < 0) throw new IllegalStateException("Microphone read failed (" + n + ")");
                        if (n > 0) offset += n;
                        else {
                            if (SystemClock.elapsedRealtime() - waitingSince > 2000) throw new IllegalStateException("Microphone stopped delivering audio");
                            Thread.sleep(5);
                        }
                    }
                    if (!running) break;
                    if (android.os.Build.VERSION.SDK_INT >= 29) {
                        AudioRecordingConfiguration config = local.getActiveRecordingConfiguration();
                        if (config != null && config.isClientSilenced()) throw new IllegalStateException("Another app or privacy setting silenced the microphone");
                    }
                    short[] copy = Arrays.copyOf(pcm, pcm.length);
                    listener.audio(new AudioBlock(origin + frames * 1000 / 48000, resampler.process(copy), copy));
                    frames += pcm.length / 2;
                }
            } catch (Exception e) {
                if (running) listener.failed(e.getMessage());
            } finally {
                running = false;
                if (local != null) {
                    try { local.stop(); } catch (IllegalStateException ignored) { }
                    local.release();
                }
            }
        }, "listen-capture");
        thread.start();
    }
    @Override public void close() {
        running = false;
        if (thread != null) thread.interrupt();
        if (thread != null && Thread.currentThread() != thread) {
            try { thread.join(250); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }
    }
}
