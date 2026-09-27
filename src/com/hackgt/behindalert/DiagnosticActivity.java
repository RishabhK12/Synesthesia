package com.hackgt.behindalert;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.AudioDeviceInfo;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.graphics.Color;
import android.view.View;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Foreground-only, labeled recordings for checking usable microphone information. */
public class DiagnosticActivity extends Activity implements SensorEventListener {
    static final int FS = 48000, BLOCK = 2048, SECONDS = 10, MAX_LAG = 42;
    static final String[] SOURCES = { "UNPROCESSED", "CAMCORDER", "MIC", "VOICE_RECOGNITION" };
    static final int[] SOURCE_IDS = { MediaRecorder.AudioSource.UNPROCESSED, MediaRecorder.AudioSource.CAMCORDER,
            MediaRecorder.AudioSource.MIC, MediaRecorder.AudioSource.VOICE_RECOGNITION };
    static final String[] POSITIONS = { "Quiet", "Left", "Front", "Right", "Behind" };
    static final String[] SOUNDS = { "Claps / knocks", "Speech", "Broadband test audio", "Other" };
    final Handler ui = new Handler(Looper.getMainLooper());
    final Object recorderLock = new Object();
    AudioManager audioManager;
    SensorManager sensors;
    Sensor rotationSensor;
    Spinner source, position, sound;
    EditText notes;
    TextView status, live, sessionText;
    ProgressBar progress;
    Button record, export, newSession;
    File session, exportSession;
    volatile boolean stopping, destroyed;
    boolean busy, counting, exporting;
    AudioRecord activeRecorder;
    volatile int displayRotation;
    volatile Pose pose = new Pose(0, new float[]{ Float.NaN, Float.NaN, Float.NaN, Float.NaN });
    Runnable countdown;

    static final class Pose {
        final long ns; final float[] q;
        Pose(long ns, float[] q) { this.ns = ns; this.q = q; }
    }

    static final class Capture {
        final int sourceIndex, positionIndex, displayRotation;
        final String sound, notes;
        final File directory;
        Capture(int s, int p, String sound, String notes, File directory, int rotation) {
            sourceIndex = s; positionIndex = p; this.sound = sound; this.notes = notes;
            this.directory = directory; displayRotation = rotation;
        }
    }

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        sensors = (SensorManager) getSystemService(SENSOR_SERVICE);
        rotationSensor = sensors.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        ScrollView scroll = new ScrollView(this);
        LinearLayout col = new LinearLayout(this); col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(28, 20, 28, 32); scroll.addView(col); setContentView(scroll);
        label(col, "Microphone test", 24);
        label(col, "Landscape, screen toward you. Keep the grip fixed and microphone openings clear. No calibration needed.", 15);
        LinearLayout selectors = new LinearLayout(this); col.addView(selectors);
        source = selector(selectors, "Recording source", SOURCES);
        position = selector(selectors, "Sound position", POSITIONS);
        sound = selector(selectors, "Sound used", SOUNDS);
        notes = new EditText(this);
        notes.setHint("Setup notes: room, distance, case, grip, USB port on your left/right");
        notes.setText(getPreferences(MODE_PRIVATE).getString("notes", ""));
        notes.setMinLines(1); notes.setMaxLines(3); col.addView(notes);
        String support = audioManager.getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED);
        label(col, "Unprocessed input support reported by Android: " + (support == null ? "unknown" : support) +
                ". Source names are recording presets; they do not select individual microphones.", 14);
        LinearLayout buttons = new LinearLayout(this); col.addView(buttons);
        record = button(buttons, "Record 10-second clip", v -> {
            if (busy) stopCapture();
            else if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)
                requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 10);
            else begin();
        });
        export = button(buttons, "Export session ZIP", v -> chooseExport());
        newSession = button(buttons, "New session", v -> makeSession());
        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(SECONDS * FS); col.addView(progress);
        status = label(col, "Ready. Start with Quiet, then Left, Front, Right and Behind for each source.", 18);
        live = label(col, "Channels are labeled 0 and 1 until their physical microphones are established.", 15);
        sessionText = label(col, "", 13);
        label(col, "Test steps: Put an external sound 1–2 m away at phone height. After the silent 3-second countdown, " +
                "stay quiet for Quiet, or make 4–5 spaced claps/knocks at the selected position. Front means beyond the rear camera; " +
                "Behind means on your side of the screen. The next position is selected after each successful clip.", 14);
        label(col, "Recordings stay in this app until you export them. ZIP includes audible recordings, setup notes and device details. " +
                "A different pair of waveforms is not proof of independent microphones. Reported processing may omit vendor internals.", 13);
        String last = getPreferences(MODE_PRIVATE).getString("session", "");
        File parent = new File(getFilesDir(), "diagnostics");
        if (last.matches("session-[A-Za-z0-9-]+") && new File(parent, last).isDirectory()) session = new File(parent, last);
        else makeSession();
        updateSession();
        if (saved != null) {
            source.setSelection(saved.getInt("source", 0)); position.setSelection(saved.getInt("position", 0));
            sound.setSelection(saved.getInt("sound", 0));
        }
    }

    @Override protected void onSaveInstanceState(Bundle b) {
        super.onSaveInstanceState(b);
        b.putInt("source", source.getSelectedItemPosition()); b.putInt("position", position.getSelectedItemPosition());
        b.putInt("sound", sound.getSelectedItemPosition());
    }

    @Override protected void onResume() {
        super.onResume(); displayRotation = getWindowManager().getDefaultDisplay().getRotation() * 90;
        if (rotationSensor != null) sensors.registerListener(this, rotationSensor, SensorManager.SENSOR_DELAY_GAME);
    }

    @Override protected void onPause() {
        stopCapture(); sensors.unregisterListener(this);
        getPreferences(MODE_PRIVATE).edit().putString("notes", notes.getText().toString()).apply();
        super.onPause();
    }

    @Override protected void onDestroy() { destroyed = true; stopCapture(); super.onDestroy(); }

    @Override public void onConfigurationChanged(Configuration c) {
        super.onConfigurationChanged(c); displayRotation = getWindowManager().getDefaultDisplay().getRotation() * 90;
    }

    @Override public void onSensorChanged(SensorEvent e) {
        float[] q = new float[4]; SensorManager.getQuaternionFromVector(q, e.values); pose = new Pose(e.timestamp, q);
    }
    @Override public void onAccuracyChanged(Sensor s, int accuracy) { }

    @Override public void onRequestPermissionsResult(int code, String[] p, int[] results) {
        super.onRequestPermissionsResult(code, p, results);
        if (code == 10 && results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) begin();
        else status.setText("Microphone permission is needed to record a test.");
    }

    void makeSession() {
        if (busy || exporting) return;
        File candidate = new File(new File(getFilesDir(), "diagnostics"), "session-" + stamp() + "-" + UUID.randomUUID().toString().substring(0, 8));
        if (!candidate.mkdirs()) { status.setText("Could not create a test session."); return; }
        session = candidate;
        getPreferences(MODE_PRIVATE).edit().putString("session", session.getName()).apply();
        source.setSelection(0); position.setSelection(0); progress.setProgress(0);
        status.setText("New session ready. Start with Quiet.");
        live.setText("No clip recorded in this session yet."); updateSession();
    }

    void updateSession() {
        if (session == null) { export.setEnabled(false); return; }
        File[] reports = session.listFiles((dir, name) -> name.endsWith(".json"));
        int n = reports == null ? 0 : reports.length;
        sessionText.setText(session.getName() + " · " + n + " clip report(s), including any failed attempts");
        export.setEnabled(!busy && !exporting && n > 0);
    }

    void enabled(boolean enabled) {
        source.setEnabled(enabled); position.setEnabled(enabled); sound.setEnabled(enabled); notes.setEnabled(enabled);
        newSession.setEnabled(enabled); record.setEnabled(!exporting);
        record.setText(busy ? "Stop clip" : "Record 10-second clip"); updateSession();
    }

    void begin() {
        if (busy || exporting || destroyed || session == null) return;
        final Capture c = new Capture(source.getSelectedItemPosition(), position.getSelectedItemPosition(),
                sound.getSelectedItem().toString(), notes.getText().toString(), session, displayRotation);
        stopping = false; busy = true; counting = true; enabled(false); progress.setProgress(0);
        countdown = new Runnable() {
            int remaining = 3;
            public void run() {
                if (stopping || destroyed) return;
                if (remaining > 0) {
                    status.setText("Get ready: " + POSITIONS[c.positionIndex] + " · " + remaining--);
                    ui.postDelayed(this, 1000);
                } else {
                    counting = false; status.setText("RECORDING · " + SOURCES[c.sourceIndex] + " · " + POSITIONS[c.positionIndex]);
                    new Thread(() -> capture(c), "microphone-diagnostic").start();
                }
            }
        };
        countdown.run();
    }

    void stopCapture() {
        if (!busy) return;
        stopping = true;
        if (counting) {
            counting = false; ui.removeCallbacks(countdown); busy = false; enabled(true);
            status.setText("Countdown canceled. No audio recorded.");
        } else {
            record.setEnabled(false); status.setText("Finishing clip…");
            synchronized (recorderLock) {
                if (activeRecorder != null) try { activeRecorder.stop(); } catch (IllegalStateException ignored) { }
            }
        }
    }

    void capture(Capture c) {
        String stem = stamp() + "-" + UUID.randomUUID().toString().substring(0, 8) + "-" + SOURCES[c.sourceIndex] + "-" + POSITIONS[c.positionIndex];
        File reportFile = new File(c.directory, stem + ".json");
        JSONObject report = new JSONObject(); JSONArray changes = new JSONArray(), timestamps = new JSONArray();
        DiagnosticSignal.Accumulator total = new DiagnosticSignal.Accumulator();
        AudioRecord recorder = null; long frames = 0; String error = null; boolean builtIn = true;
        try {
            report.put("schema", 1).put("status", "recording").put("started_utc_ms", System.currentTimeMillis())
                    .put("requested_source", SOURCES[c.sourceIndex]).put("source_id", SOURCE_IDS[c.sourceIndex])
                    .put("position", POSITIONS[c.positionIndex]).put("sound", c.positionIndex == 0 ? "Quiet" : c.sound)
                    .put("notes", c.notes).put("initial_display_rotation_degrees", c.displayRotation)
                    .put("rotation_sensor_available", rotationSensor != null).put("requested_sample_rate", FS)
                    .put("requested_channels", 2).put("requested_seconds", SECONDS)
                    .put("device", DiagnosticMetadata.device(audioManager));
            write(reportFile, report.toString(2));
            int min = AudioRecord.getMinBufferSize(FS, AudioFormat.CHANNEL_IN_STEREO, AudioFormat.ENCODING_PCM_16BIT);
            if (min <= 0) throw new IOException("48 kHz stereo is not supported (" + min + ")");
            recorder = new AudioRecord(SOURCE_IDS[c.sourceIndex], FS, AudioFormat.CHANNEL_IN_STEREO,
                    AudioFormat.ENCODING_PCM_16BIT, Math.max(min, BLOCK * 16));
            if (recorder.getState() != AudioRecord.STATE_INITIALIZED) throw new IOException("This source did not initialize in stereo. Try another source.");
            // Let the source choose its normal input route. Forcing the first built-in
            // device can defeat CAMCORDER's microphone selection on some phones.
            report.put("routing_policy", "Default Android route for this source; no preferred device requested");
            synchronized (recorderLock) {
                if (stopping) throw new IOException("Stopped before recording started");
                activeRecorder = recorder; recorder.startRecording();
            }
            if (recorder.getRecordingState() != AudioRecord.RECORDSTATE_RECORDING) throw new IOException("Recording did not start");
            if (recorder.getChannelCount() != 2 || recorder.getSampleRate() != FS) throw new IOException("Unexpected recorder format");
            AudioDeviceInfo route = recorder.getRoutedDevice();
            builtIn = route != null && route.getType() == AudioDeviceInfo.TYPE_BUILTIN_MIC;
            report.put("initial_route_is_builtin", builtIn);
            try (WavWriter wav = new WavWriter(new File(c.directory, stem + ".wav"), FS);
                 BufferedWriter csv = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(new File(c.directory, stem + ".csv")), StandardCharsets.UTF_8))) {
                csv.write("start_frame,frame_count,read_completed_boottime_ns,ch0_dbfs,ch1_dbfs,zero_lag_correlation,difference_db,identical_fraction,clipped_fraction,gcc_lag_samples,gcc_peak_height,pose_boottime_ns,qw,qx,qy,qz,display_rotation_degrees\n");
                Dsp.GccPhat gcc = new Dsp.GccPhat(BLOCK);
                short[] pcm = new short[BLOCK * 2]; float[] x = new float[BLOCK], y = new float[BLOCK];
                long nextSnapshot = 0, nextUi = 0; String previousSnapshot = "";
                while (!stopping && frames < SECONDS * FS) {
                    int want = (int) Math.min(pcm.length, (SECONDS * FS - frames) * 2), off = 0;
                    long readDeadline = SystemClock.elapsedRealtime() + 3000;
                    // Nonblocking reads keep Stop responsive even if an input route stops delivering data.
                    while (off < want && !stopping) {
                        int n = recorder.read(pcm, off, want - off, AudioRecord.READ_NON_BLOCKING);
                        if (n < 0) {
                            if (stopping) break;
                            throw new IOException("Audio read failed: " + n);
                        }
                        if (n > 0) off += n;
                        else {
                            if (SystemClock.elapsedRealtime() > readDeadline) throw new IOException("Audio input stalled for 3 seconds");
                            Thread.sleep(5);
                        }
                    }
                    off -= off % 2;
                    if (off == 0) break;
                    long readNs = SystemClock.elapsedRealtimeNanos();
                    wav.write(pcm, off); total.add(pcm, off);
                    DiagnosticSignal.Stats s = DiagnosticSignal.measure(pcm, off);
                    Arrays.fill(x, 0); Arrays.fill(y, 0);
                    for (int i = 0; i < off / 2; i++) { x[i] = pcm[2 * i] / 32768f; y[i] = pcm[2 * i + 1] / 32768f; }
                    double lag = Double.NaN, peak = Double.NaN;
                    if (off == pcm.length && Math.min(s.db0, s.db1) > -70) {
                        Dsp.GccResult g = gcc.run(x, y, MAX_LAG, FS, 150, 12000); lag = g.lag; peak = g.peak;
                    }
                    Pose p = pose;
                    csv.write(String.format(Locale.US, "%d,%d,%d,%.3f,%.3f,%.6f,%.3f,%.6f,%.6f,%.4f,%.6f,%d,%.7f,%.7f,%.7f,%.7f,%d\n",
                            frames, off / 2, readNs, s.db0, s.db1, s.correlation, s.differenceDb, s.identicalFraction,
                            s.clippedFraction, lag, peak, p.ns, p.q[0], p.q[1], p.q[2], p.q[3], displayRotation));
                    frames += off / 2;
                    if (frames >= nextSnapshot) {
                        JSONObject state = DiagnosticMetadata.snapshot(recorder); String signature = state.toString();
                        if (!signature.equals(previousSnapshot)) {
                            changes.put(new JSONObject().put("observed_at_frame", frames).put("state", state)); previousSnapshot = signature;
                        }
                        timestamps.put(DiagnosticMetadata.timestamp(recorder));
                        nextSnapshot = frames + FS;
                    }
                    if (frames >= nextUi) {
                        final int done = (int) frames; final double shownLag = lag;
                        ui.post(() -> {
                            if (destroyed) return;
                            progress.setProgress(done);
                            live.setText(String.format(Locale.US, "%.1f / 10 s · channel 0: %.0f dBFS · channel 1: %.0f dBFS · delay: %s\n%s",
                                    done / (double) FS, s.db0, s.db1, Double.isNaN(shownLag) ? "unavailable" : String.format(Locale.US, "%+.1f samples", shownLag), s.observation()));
                        });
                        nextUi = frames + FS / 4;
                    }
                }
                if (!stopping) {
                    changes.put(new JSONObject().put("observed_at_frame", frames).put("state", DiagnosticMetadata.snapshot(recorder)));
                    timestamps.put(DiagnosticMetadata.timestamp(recorder));
                }
            }
        } catch (Exception e) { error = e.toString(); }
        finally {
            synchronized (recorderLock) {
                activeRecorder = null;
                if (recorder != null) {
                    try { recorder.stop(); } catch (IllegalStateException ignored) { }
                    recorder.release();
                }
            }
        }
        boolean completed = error == null && frames == SECONDS * FS;
        try {
            report.put("status", completed ? "complete" : stopping ? "interrupted" : "failed")
                    .put("frames", frames).put("duration_seconds", frames / (double) FS)
                    .put("error", error == null ? JSONObject.NULL : error).put("summary", DiagnosticMetadata.stats(total.stats()))
                    .put("recording_state_snapshots", changes).put("audio_timestamps_boottime", timestamps)
                    .put("gcc_search_limit_samples", MAX_LAG).put("gcc_band_hz", new JSONArray(new int[]{150, 12000}))
                    .put("delay_sign", "Positive means channel 1 arrived before channel 0; channel numbers are not physical left/right.")
                    .put("processing", "No app gain normalization, filtering or noise subtraction on WAV. System processing may still exist.");
            write(reportFile, report.toString(2));
        } catch (Exception e) { completed = false; error = "Could not finish report: " + e; }
        final boolean success = completed, initialBuiltIn = builtIn;
        final String failure = error; final long savedFrames = frames;
        final DiagnosticSignal.Stats summary = total.stats();
        ui.post(() -> {
            if (destroyed) return;
            busy = false; enabled(true); progress.setProgress((int) savedFrames);
            status.setText(success ? "Saved: " + SOURCES[c.sourceIndex] + " · " + POSITIONS[c.positionIndex] :
                    "Clip " + (stopping ? "interrupted" : "failed") + ". " + (failure == null ? "Partial recording saved." : failure));
            live.setText(summary.observation() + (initialBuiltIn ? "" : " Initial input route was external or unknown; inspect the report."));
            if (success) {
                position.setSelection((c.positionIndex + 1) % POSITIONS.length);
                if (c.positionIndex == POSITIONS.length - 1 && c.sourceIndex < SOURCES.length - 1) source.setSelection(c.sourceIndex + 1);
            }
            updateSession();
        });
    }

    void chooseExport() {
        if (busy || exporting || session == null) return;
        exportSession = session;
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("application/zip")
                .addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_TITLE, "sound-diagnostics-" + session.getName() + ".zip");
        try { startActivityForResult(intent, 20); }
        catch (Exception e) { status.setText("No file picker available: " + e.getMessage()); }
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != 20 || result != RESULT_OK || data == null || data.getData() == null) return;
        final Uri uri = data.getData(); final File directory = exportSession == null ? session : exportSession;
        exporting = true; enabled(false); status.setText("Exporting session…");
        new Thread(() -> {
            String error = null;
            try (OutputStream out = getContentResolver().openOutputStream(uri, "wt")) {
                if (out == null) throw new IOException("Cannot open destination");
                try (ZipOutputStream zip = new ZipOutputStream(out)) {
                    zip.putNextEntry(new ZipEntry("READ-ME.txt"));
                    zip.write(EXPORT_NOTES.getBytes(StandardCharsets.UTF_8)); zip.closeEntry();
                    File[] files = directory.listFiles(); if (files == null) throw new IOException("Cannot read session");
                    Arrays.sort(files); byte[] buffer = new byte[32768];
                    for (File file : files) {
                        if (!file.isFile()) continue;
                        zip.putNextEntry(new ZipEntry(file.getName()));
                        try (FileInputStream input = new FileInputStream(file)) {
                            int n; while ((n = input.read(buffer)) != -1) zip.write(buffer, 0, n);
                        }
                        zip.closeEntry();
                    }
                }
            } catch (Exception e) { error = e.toString(); }
            final String failure = error;
            ui.post(() -> {
                if (destroyed) return;
                exporting = false; enabled(true);
                status.setText(failure == null ? "Session exported. Send the ZIP for microphone analysis." : "Export failed: " + failure);
            });
        }, "diagnostic-export").start();
    }

    static final String EXPORT_NOTES = "Sound Direction microphone diagnostic, schema 1\n\n" +
            "WAV: 48000 Hz, stereo, PCM16, unchanged AudioRecord output. System processing may exist.\n" +
            "JSON: known sound position, notes, phone/OS, mic inventory, active mic mappings, route/effect snapshots, timestamps, summary, completion status.\n" +
            "CSV: frame-indexed channel levels (dBFS, not calibrated SPL), correlation, clipping, uncalibrated GCC delay, rotation quaternion (w,x,y,z).\n" +
            "Positive lag: channel 1 heard it first. These are channel numbers, NOT assumed physical left/right microphones.\n" +
            "Front is beyond the rear camera; Behind is the screen/user side. Physical sound labels refer to the holder.\n" +
            "read_completed_boottime_ns is delivery time, NOT exact capture time. JSON AudioTimestamp pairs can align captured frames to boottime.\n" +
            "Pose timestamps share Android boottime; quaternion is the rotation-vector sensor's device-to-world orientation, not remapped to the display.\n" +
            "NaN means unavailable. GCC peak height is not a calibrated probability. The +/-42 sample search is exploratory, not a measured mic spacing.\n" +
            "Snapshots are checked about once per second; short route/processing changes may be missed. Empty metadata does not prove raw input.\n" +
            "A report marked recording indicates an unfinalized attempt (for example process termination); do not treat it as a complete clip.\n";

    static void write(File file, String content) throws IOException {
        try (OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) { writer.write(content); }
    }
    static String stamp() { return new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date()); }
    TextView label(LinearLayout parent, String text, int size) {
        TextView t = new TextView(this); t.setText(text); t.setTextSize(size); t.setTextColor(Color.WHITE);
        t.setPadding(0, 6, 0, 6); parent.addView(t); return t;
    }
    Spinner selector(LinearLayout parent, String title, String[] options) {
        LinearLayout field = new LinearLayout(this); field.setOrientation(LinearLayout.VERTICAL);
        parent.addView(field, new LinearLayout.LayoutParams(0, -2, 1)); label(field, title, 14);
        Spinner spinner = new Spinner(this); ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, options);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item); spinner.setAdapter(adapter); field.addView(spinner); return spinner;
    }
    Button button(LinearLayout parent, String title, View.OnClickListener listener) {
        Button b = new Button(this); b.setText(title); b.setAllCaps(false); b.setOnClickListener(listener);
        parent.addView(b, new LinearLayout.LayoutParams(0, -2, 1)); return b;
    }
}
