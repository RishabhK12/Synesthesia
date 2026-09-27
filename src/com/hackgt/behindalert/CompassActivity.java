package com.hackgt.behindalert;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
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

/** Guided 8-sector calibration and an honest two-candidate 360-degree compass. */
public final class CompassActivity extends Activity implements SensorEventListener {
    static final int FS = 48000, BLOCK = 2048, WINDOW = 4096, MAX_LAG = 42, TRIAL_SECONDS = 15;
    final Handler ui = new Handler(Looper.getMainLooper());
    final CompassModel model = new CompassModel();
    final Object recordLock = new Object();
    SharedPreferences prefs;
    SensorManager sensors;
    Sensor rotationSensor;
    AudioManager audioManager;
    AudioRecord recorder;
    Thread audioThread;
    File sessionDir;
    Spinner sectorPicker, trialPicker;
    EditText notes;
    TextView status, calibrationText, liveText, sessionText;
    Button listenButton, captureButton, trialButton, exportButton;
    CompassView circle;
    volatile boolean running, destroyed;
    volatile int requestedSector = -1;
    volatile boolean trialRequested, trialStop;
    volatile int trialSector;
    volatile String trialNotes = "";
    volatile double heading = Double.NaN;
    volatile long headingTimestampNs;
    double calibrationHeading = Double.NaN;
    long lastEventMs;
    double noiseFloorDb = -55;
    boolean exporting;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        prefs = getSharedPreferences("compass", MODE_PRIVATE);
        model.deserialize(prefs.getString("calibration", ""));
        audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        sensors = (SensorManager) getSystemService(SENSOR_SERVICE);
        rotationSensor = sensors.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR);
        if (rotationSensor == null) rotationSensor = sensors.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        sessionDir = getSession();

        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.HORIZONTAL);
        root.setBackgroundColor(0xff101318); setContentView(root);
        LinearLayout visual = new LinearLayout(this); visual.setOrientation(LinearLayout.VERTICAL);
        visual.setPadding(dp(10), dp(6), dp(4), dp(8));
        root.addView(visual, new LinearLayout.LayoutParams(0, -1, .44f));
        TextView title = text(visual, "Sound compass", 22);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        circle = new CompassView(this);
        visual.addView(circle, new LinearLayout.LayoutParams(-1, 0, 1));
        synchronized (model) { circle.set(null, model.usable()); }
        TextView key = text(visual, "F front   B behind   • your phone at center", 12);
        key.setGravity(Gravity.CENTER);

        ScrollView scroll = new ScrollView(this);
        root.addView(scroll, new LinearLayout.LayoutParams(0, -1, .56f));
        LinearLayout controls = new LinearLayout(this); controls.setPadding(dp(10), dp(6), dp(12), dp(24));
        controls.setOrientation(LinearLayout.VERTICAL); scroll.addView(controls);
        text(controls, "Hold landscape, screen toward your eyes, rear camera facing forward. Keep the same case and grip for calibration and use.", 14);
        listenButton = button(controls, "Start microphone", v -> {
            if (running) { stopAudio(); status.setText("Microphone stopped."); }
            else ensurePermission();
        });
        status = text(controls, "Start the microphone to calibrate.", 16);
        liveText = text(controls, "One dominant sound at a time. A short sound can leave two possible directions.", 14);
        text(controls, "1. Calibrate: keep the phone facing the same way. Move the sound source around it, 1–2 m away at phone height.", 15);
        sectorPicker = selector(controls, CompassModel.NAMES);
        captureButton = button(controls, "Capture 4 claps at Front", v -> startCalibration());
        sectorPicker.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) { updateButtons(); }
            public void onNothingSelected(android.widget.AdapterView<?> parent) { }
        });
        calibrationText = text(controls, "", 13);
        button(controls, "Clear calibration / new session", v -> {
            if (requestedSector >= 0 || trialRequested || exporting) return;
            stopAudio();
            synchronized (model) { model.clearAll(); }
            prefs.edit().remove("calibration").apply(); calibrationHeading = Double.NaN;
            prefs.edit().remove("session").apply(); sessionDir = getSession();
            sessionText.setText(sessionDir.getName());
            sectorPicker.setSelection(0); circle.set(null, false);
            status.setText("New session ready. Start the microphone, then calibrate at Front."); showCalibration(); updateButtons();
        });
        text(controls, "2. Test: keep one sound in place. Start still, then slowly turn with the phone. Select its starting position.", 15);
        trialPicker = selector(controls, CompassModel.NAMES);
        trialButton = button(controls, "Record 15-second sound test", v -> startTrial());
        notes = new EditText(this); notes.setSingleLine(false); notes.setMinLines(1); notes.setMaxLines(2);
        notes.setTextColor(Color.WHITE); notes.setHint("Room, case, grip, USB side, sound/distance");
        notes.setText(prefs.getString("notes", "")); controls.addView(notes);
        exportButton = button(controls, "Export compass ZIP", v -> chooseExport());
        sessionText = text(controls, sessionDir.getName(), 12);
        showCalibration(); updateButtons();
        if (model.usable()) status.setText("Saved calibration loaded. Start the microphone to listen or test.");
        if (rotationSensor == null) status.setText("This phone has no rotation sensor available to the app.");
    }

    File getSession() {
        File parent = new File(getFilesDir(), "compass");
        String saved = prefs.getString("session", "");
        if (saved.matches("session-[A-Za-z0-9-]+")) {
            File prior = new File(parent, saved);
            if (prior.isDirectory()) return prior;
        }
        File dir = new File(parent, "session-" + stamp() + "-" + UUID.randomUUID().toString().substring(0, 8));
        if (!dir.mkdirs()) throw new IllegalStateException("Cannot create compass session");
        prefs.edit().putString("session", dir.getName()).apply();
        return dir;
    }

    @Override protected void onResume() {
        super.onResume();
        if (rotationSensor != null) sensors.registerListener(this, rotationSensor, SensorManager.SENSOR_DELAY_GAME);
    }
    @Override protected void onPause() {
        stopAudio(); sensors.unregisterListener(this);
        prefs.edit().putString("notes", notes.getText().toString()).apply();
        super.onPause();
    }
    @Override protected void onDestroy() { destroyed = true; stopAudio(); super.onDestroy(); }
    @Override public void onConfigurationChanged(Configuration c) { super.onConfigurationChanged(c); }
    @Override public void onAccuracyChanged(Sensor s, int accuracy) { }
    @Override public void onSensorChanged(SensorEvent e) {
        float[] matrix = new float[9];
        SensorManager.getRotationMatrixFromVector(matrix, e.values);
        // The rear camera looks along device -Z. Project that direction to the floor.
        double east = -matrix[2], north = -matrix[5];
        if (Math.hypot(east, north) < .55) { heading = Double.NaN; return; }
        heading = CompassModel.wrap360(Math.toDegrees(Math.atan2(east, north)));
        headingTimestampNs = e.timestamp;
    }

    void ensurePermission() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 40);
        else startAudio();
    }
    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == 40 && results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) startAudio();
        else status.setText("Microphone permission is needed for the compass.");
    }

    void startAudio() {
        if (running || exporting) return;
        int min = AudioRecord.getMinBufferSize(FS, AudioFormat.CHANNEL_IN_STEREO, AudioFormat.ENCODING_PCM_16BIT);
        if (min <= 0) { status.setText("48 kHz stereo recording is unavailable."); return; }
        try {
            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)
                throw new SecurityException("Microphone permission is required");
            recorder = new AudioRecord(MediaRecorder.AudioSource.CAMCORDER, FS, AudioFormat.CHANNEL_IN_STEREO,
                    AudioFormat.ENCODING_PCM_16BIT, Math.max(min, BLOCK * 16));
            if (recorder.getState() != AudioRecord.STATE_INITIALIZED || recorder.getChannelCount() != 2)
                throw new IOException("CAMCORDER did not provide two channels");
            recorder.startRecording();
            if (recorder.getRecordingState() != AudioRecord.RECORDSTATE_RECORDING) throw new IOException("Recording did not start");
            running = true; noiseFloorDb = -55; lastEventMs = 0;
            synchronized (model) { model.clearTrack(); }
            boolean calibrated; synchronized (model) { calibrated = model.usable(); }
            circle.set(null, calibrated);
            listenButton.setText("Stop microphone");
            status.setText("Listening with CAMCORDER. Start with a second of quiet before making sound.");
            updateButtons();
            final AudioRecord use = recorder;
            audioThread = new Thread(() -> audioLoop(use), "compass-audio"); audioThread.start();
        } catch (Exception e) {
            if (recorder != null) { recorder.release(); recorder = null; }
            status.setText("Cannot start microphone: " + e.getMessage());
        }
    }

    void stopAudio() {
        running = false; requestedSector = -1; trialStop = true; trialRequested = false;
        synchronized (recordLock) {
            if (recorder != null) try { recorder.stop(); } catch (IllegalStateException ignored) { }
        }
        if (audioThread != null) {
            try { audioThread.join(700); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
            audioThread = null;
        }
        synchronized (recordLock) {
            if (recorder != null) { recorder.release(); recorder = null; }
        }
        if (listenButton != null) listenButton.setText("Start microphone");
        if (circle != null) { boolean ready; synchronized (model) { ready = model.usable(); } circle.set(null, ready); }
        if (captureButton != null) updateButtons();
    }

    void startCalibration() {
        if (!running || requestedSector >= 0 || trialRequested) return;
        if (!headingReady()) { status.setText("Hold the screen upright toward your eyes so the phone can track turns."); return; }
        int chosen = sectorPicker.getSelectedItemPosition();
        if (Double.isNaN(calibrationHeading)) {
            // Game rotation is relative to this sensor session. A prior saved
            // calibration must be cleared into a new data session to retake it.
            synchronized (model) {
                if (model.complete()) {
                    status.setText("Tap Clear calibration / new session before recalibrating a saved setup.");
                    return;
                }
            }
            calibrationHeading = heading;
        }
        if (Math.abs(CompassModel.wrap180(heading - calibrationHeading)) > 12) {
            status.setText("Phone turned since calibration began. Face the same way or clear calibration and restart."); return;
        }
        synchronized (model) { model.clearSector(chosen); }
        prefs.edit().remove("calibration").apply();
        circle.set(null, false);
        requestedSector = chosen;
        status.setText("Recording " + CompassModel.NAMES[chosen] + ": make 4–6 spaced claps at that position.");
        showCalibration(); updateButtons();
    }

    boolean headingReady() {
        return Double.isFinite(heading) && SystemClock.elapsedRealtimeNanos() - headingTimestampNs < 1500000000L;
    }

    void startTrial() {
        if (!running || requestedSector >= 0 || trialRequested) return;
        synchronized (model) {
            if (!model.usable()) { status.setText("Finish all 8 calibration directions first."); return; }
            model.clearTrack();
        }
        if (!headingReady()) { status.setText("Hold the screen upright toward your eyes before the turn test."); return; }
        trialSector = trialPicker.getSelectedItemPosition();
        trialNotes = notes.getText().toString();
        trialStop = false; trialRequested = true;
        status.setText("Test started: keep the source fixed. Stay still briefly, then turn 40–60°.");
        updateButtons();
    }

    void audioLoop(AudioRecord use) {
        short[] pcm = new short[BLOCK * 2];
        float[] left = new float[WINDOW], right = new float[WINDOW];
        Dsp.GccPhat gcc = new Dsp.GccPhat(WINDOW);
        WavWriter calibrationWav = null, trialWav = null;
        BufferedWriter trialCsv = null;
        int openSector = -1, baselineBlocks = 0; long stepStart = 0, trialFrames = 0, lastUi = 0;
        boolean wasLoud = false;
        String trialStem = null;
        try {
            write(new File(sessionDir, "device.json"), DiagnosticMetadata.device(audioManager).toString(2));
            write(new File(sessionDir, "recording.json"), DiagnosticMetadata.snapshot(use).toString(2));
            while (running) {
                int off = 0;
                while (running && off < pcm.length) {
                    int n = use.read(pcm, off, pcm.length - off, AudioRecord.READ_NON_BLOCKING);
                    if (n < 0) { if (!running) break; throw new IOException("Audio read error " + n); }
                    if (n > 0) off += n;
                    else Thread.sleep(5);
                }
                if (!running || off < pcm.length) break;
                long now = SystemClock.uptimeMillis();
                long frameNs = SystemClock.elapsedRealtimeNanos();
                if (requestedSector != openSector) {
                    if (calibrationWav != null) { calibrationWav.close(); calibrationWav = null; }
                    openSector = requestedSector;
                    if (openSector >= 0) {
                        stepStart = now;
                        calibrationWav = new WavWriter(new File(sessionDir,
                                "cal-" + openSector + "-" + stamp() + ".wav"), FS);
                    }
                }
                if (calibrationWav != null) calibrationWav.write(pcm, pcm.length);
                if (trialRequested && trialWav == null) {
                    trialStem = "turn-" + trialSector + "-" + stamp();
                    trialWav = new WavWriter(new File(sessionDir, trialStem + ".wav"), FS);
                    trialCsv = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(new File(sessionDir, trialStem + ".csv")), StandardCharsets.UTF_8));
                    trialCsv.write("start_frame,read_boottime_ns,known_start_bearing_deg,dbfs,gcc_lag_samples,gcc_peak,yaw_deg,best_bearing_deg,alternative_bearing_deg,resolved,turn_deg,noise_floor_dbfs,activity_detected,heading_ready\n");
                    trialFrames = 0;
                    JSONObject trial = new JSONObject().put("known_start_bearing_deg", trialSector * 45)
                            .put("app_version", "2.3-speech")
                            .put("known_start_label", CompassModel.NAMES[trialSector])
                            .put("source", "CAMCORDER").put("notes", trialNotes)
                            .put("interpretation", "Bearing is relative to rear camera at trial start. Keep source stationary; rotate the phone.");
                    write(new File(sessionDir, trialStem + ".json"), trial.toString(2));
                }
                long trialFrameStart = trialFrames;
                if (trialWav != null) {
                    int count = (int) Math.min(pcm.length, (TRIAL_SECONDS * FS - trialFrames) * 2);
                    if (count > 0) { trialWav.write(pcm, count); trialFrames += count / 2; }
                }
                System.arraycopy(left, BLOCK, left, 0, BLOCK);
                System.arraycopy(right, BLOCK, right, 0, BLOCK);
                double energy = 0, same = 0;
                for (int i = 0; i < BLOCK; i++) {
                    float a = pcm[2 * i] / 32768f, b = pcm[2 * i + 1] / 32768f;
                    left[BLOCK + i] = a; right[BLOCK + i] = b;
                    energy += ((double) a * a + (double) b * b) / 2;
                    if (pcm[2 * i] == pcm[2 * i + 1]) same++;
                }
                double db = 10 * Math.log10(energy / BLOCK + 1e-12);
                if (baselineBlocks < 20) {
                    noiseFloorDb = baselineBlocks == 0 ? db :
                            (noiseFloorDb * baselineBlocks + db) / (baselineBlocks + 1);
                    baselineBlocks++;
                }
                boolean separateChannels = same / BLOCK < .999;
                boolean loud = baselineBlocks >= 20 && separateChannels
                        && db > Math.max(-55, noiseFloorDb + 9);
                boolean active = baselineBlocks >= 20 && separateChannels
                        && db > Math.max(-58, noiseFloorDb + 4);
                if (!active) noiseFloorDb = Math.min(-25, .995 * noiseFloorDb + .005 * db);
                double lag = Double.NaN, peak = Double.NaN;
                CompassModel.Estimate estimate = null;
                // During a saved test, retain delay evidence even below the live
                // gate so missed speech can be diagnosed from the export.
                if (active || trialWav != null) {
                    Dsp.GccResult result = gcc.run(left, right, MAX_LAG, FS, 150, 12000);
                    lag = result.lag; peak = result.peak;
                }
                if (active) {
                    if (openSector >= 0 && loud && !wasLoud && peak >= .17
                            && now - lastEventMs >= 500 && headingReady()
                            && Math.abs(CompassModel.wrap180(heading - calibrationHeading)) <= 12) {
                        lastEventMs = now;
                        synchronized (model) { model.add(openSector, lag); }
                        appendCalibration(openSector, now, lag, peak, heading, db);
                        final int accepted = openSector, count;
                        synchronized (model) { count = model.count(openSector); }
                        ui.post(() -> {
                            if (destroyed) return;
                            status.setText(CompassModel.NAMES[accepted] + ": " + count + "/4 accepted claps");
                            showCalibration();
                        });
                        if (count >= CompassModel.REQUIRED) {
                            requestedSector = -1;
                            calibrationWav.close(); calibrationWav = null; openSector = -1;
                            final boolean ready, good;
                            synchronized (model) {
                                ready = model.complete(); good = model.usable();
                                if (ready && good) prefs.edit().putString("calibration", model.serialize()).apply();
                            }
                            if (ready && good) saveCalibrationSummary();
                            ui.post(() -> {
                                if (destroyed) return;
                                sectorPicker.setSelection((accepted + 1) % CompassModel.SECTORS);
                                showCalibration(); updateButtons();
                                status.setText(ready ? good ? "Calibration complete. Test a fixed sound while turning."
                                        : "Calibration could not separate left and right. Clear it and check the audio source."
                                        : "Saved " + CompassModel.NAMES[accepted] + ". Move the sound to the next position, then tap Capture.");
                            });
                        }
                    } else if (openSector < 0 && peak >= .12 && headingReady()) {
                        synchronized (model) { estimate = model.observe(now, heading, lag, peak); }
                        if (estimate != null && now - lastUi >= 180) {
                            CompassModel.Estimate display = estimate;
                            ui.post(() -> {
                                if (destroyed) return;
                                circle.set(display, true);
                                liveText.setText(display.resolved ?
                                        "Approx. " + display.primary + "° from rear camera · " + display.observations + " observations · " +
                                                String.format(Locale.US, "%.0f° turn", display.turnDegrees) :
                                        "Two possible directions. Keep the sound steady and turn slowly. " +
                                                display.primary + "° / " + display.alternative + "°");
                            });
                            lastUi = now;
                        }
                    }
                    if (openSector < 0 && estimate == null && now - lastUi >= 250) {
                        final CompassModel.Estimate held;
                        final boolean calibrated, upright = headingReady();
                        synchronized (model) {
                            model.expire(now); held = model.estimate(heading); calibrated = model.usable();
                        }
                        ui.post(() -> {
                            if (!destroyed) {
                                circle.set(held, calibrated);
                                liveText.setText(!calibrated ? "Sound heard. Finish calibration to show a direction." :
                                        !upright ? "Sound heard. Hold the screen upright toward your eyes." :
                                        "Sound heard; waiting for a clearer direction.");
                            }
                        });
                        lastUi = now;
                    }
                }
                wasLoud = loud;
                if (openSector >= 0 && now - stepStart > 20000) {
                    final int missed = openSector;
                    requestedSector = -1;
                    calibrationWav.close(); calibrationWav = null; openSector = -1;
                    ui.post(() -> {
                        if (destroyed) return;
                        int have; synchronized (model) { have = model.count(missed); }
                        status.setText("Time expired at " + CompassModel.NAMES[missed] + " (" + have + "/4). Tap Capture to retry with 4–6 spaced claps.");
                        showCalibration(); updateButtons();
                    });
                }
                if (trialCsv != null) {
                    trialCsv.write(String.format(Locale.US, "%d,%d,%d,%.3f,%.4f,%.5f,%.4f,%d,%d,%s,%.1f,%.3f,%s,%s\n",
                            trialFrameStart, frameNs, trialSector * 45, db, lag, peak, heading,
                            estimate == null ? -1 : estimate.primary, estimate == null ? -1 : estimate.alternative,
                            estimate != null && estimate.resolved, estimate == null ? 0 : estimate.turnDegrees,
                            noiseFloorDb, active, headingReady()));
                }
                if (trialWav != null && (trialFrames >= TRIAL_SECONDS * FS || trialStop)) {
                    trialWav.close(); trialWav = null; trialCsv.close(); trialCsv = null;
                    final long savedFrames = trialFrames; final boolean finished = !trialStop;
                    trialRequested = false; trialStop = false;
                    ui.post(() -> {
                        if (destroyed) return;
                        status.setText((finished ? "Turn test saved" : "Partial turn test saved") +
                                String.format(Locale.US, " (%.1f s). Export ZIP when finished.", savedFrames / (double) FS));
                        updateButtons();
                    });
                }
                if (!active && openSector < 0 && now - lastUi >= 250) {
                    synchronized (model) { model.expire(now); estimate = model.estimate(heading); }
                    if (estimate == null) {
                        ui.post(() -> { if (!destroyed) { circle.set(null, true); liveText.setText("Listening. One dominant sound at a time."); } });
                    }
                    lastUi = now;
                }
            }
        } catch (Exception e) {
            final String message = e.getMessage();
            if (running) ui.post(() -> { if (!destroyed) status.setText("Audio stopped: " + message); });
        } finally {
            try { if (calibrationWav != null) calibrationWav.close(); } catch (IOException ignored) { }
            try { if (trialWav != null) trialWav.close(); } catch (IOException ignored) { }
            try { if (trialCsv != null) trialCsv.close(); } catch (IOException ignored) { }
            synchronized (recordLock) {
                if (recorder == use) {
                    try { use.stop(); } catch (IllegalStateException ignored) { }
                    use.release(); recorder = null;
                }
            }
            running = false; requestedSector = -1; trialRequested = false;
            ui.post(() -> { if (!destroyed) { listenButton.setText("Start microphone"); updateButtons(); } });
        }
    }

    void appendCalibration(int sector, long time, double lag, double peak, double yaw, double db) throws IOException {
        File file = new File(sessionDir, "calibration-events.csv");
        boolean header = !file.exists();
        try (BufferedWriter out = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file, true), StandardCharsets.UTF_8))) {
            if (header) out.write("uptime_ms,sector,label,lag_samples,gcc_peak,yaw_deg,dbfs\n");
            out.write(String.format(Locale.US, "%d,%d,%s,%.4f,%.5f,%.4f,%.3f\n",
                    time, sector, CompassModel.NAMES[sector], lag, peak, yaw, db));
        }
    }
    void saveCalibrationSummary() throws IOException {
        StringBuilder out = new StringBuilder("sector,label,median_lag_samples,scatter_samples,accepted_claps\n");
        synchronized (model) {
            for (int i = 0; i < CompassModel.SECTORS; i++)
                out.append(String.format(Locale.US, "%d,%s,%.4f,%.4f,%d\n", i, CompassModel.NAMES[i], model.center(i), model.scatter(i), model.count(i)));
        }
        write(new File(sessionDir, "calibration-summary.csv"), out.toString());
    }

    void showCalibration() {
        StringBuilder b = new StringBuilder();
        synchronized (model) {
            for (int i = 0; i < CompassModel.SECTORS; i++) {
                if (i > 0) b.append("  ·  ");
                b.append(CompassModel.NAMES[i]).append(' ').append(model.count(i)).append('/').append(CompassModel.REQUIRED);
                if (i == 3) b.append('\n');
            }
            b.append(model.usable() ? "\nReady for a 360° turn test." : "\nDirections remain tentative until all eight are calibrated.");
        }
        calibrationText.setText(b.toString());
    }
    void updateButtons() {
        if (captureButton == null || trialButton == null) return;
        captureButton.setEnabled(running && requestedSector < 0 && !trialRequested && !exporting);
        sectorPicker.setEnabled(requestedSector < 0 && !trialRequested && !exporting);
        captureButton.setText(requestedSector >= 0 ? "Listening for claps…" :
                "Capture 4 claps at " + CompassModel.NAMES[sectorPicker.getSelectedItemPosition()]);
        boolean calibrated; synchronized (model) { calibrated = model.usable(); }
        trialButton.setEnabled(running && calibrated && requestedSector < 0 && !trialRequested && !exporting);
        exportButton.setEnabled(!exporting && requestedSector < 0 && !trialRequested);
        listenButton.setEnabled(!exporting);
    }

    void chooseExport() {
        if (exporting || requestedSector >= 0 || trialRequested) return;
        stopAudio();
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("application/zip")
                .addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_TITLE, "sound-compass-" + sessionDir.getName() + ".zip");
        try { startActivityForResult(intent, 41); }
        catch (Exception e) { status.setText("No file picker: " + e.getMessage()); }
    }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != 41 || result != RESULT_OK || data == null || data.getData() == null) return;
        Uri destination = data.getData();
        final String exportNotes = notes.getText().toString();
        exporting = true; updateButtons(); status.setText("Exporting compass data…");
        new Thread(() -> {
            String error = null;
            try {
                JSONObject manifest = new JSONObject().put("schema", 1).put("source", "CAMCORDER")
                        .put("app_version", "2.3-speech")
                        .put("case_and_setup_notes", exportNotes)
                        .put("front_definition", "Rear camera direction")
                        .put("units", "Phone-relative clockwise degrees. No distance estimate. Use processed stereo input with caution.")
                        .put("calibration_complete", model.complete()).put("calibration_usable", model.usable());
                write(new File(sessionDir, "session.json"), manifest.toString(2));
                File[] files = sessionDir.listFiles();
                if (files == null) throw new IOException("Cannot list session files");
                Arrays.sort(files);
                try (OutputStream output = getContentResolver().openOutputStream(destination, "wt")) {
                    if (output == null) throw new IOException("Cannot open destination");
                    try (ZipOutputStream zip = new ZipOutputStream(output)) {
                        zip.putNextEntry(new ZipEntry("README.txt"));
                        zip.write(("Sound compass test files. WAV is 48 kHz stereo PCM16; system processing may be present. " +
                                "Cal WAV names contain sector indices 0–7. CSV records accepted GCC delays and orientation. " +
                                "Turn trials include the known source position at start, per-block yaw, candidates and full WAV. " +
                                "A compass candidate needs turn evidence before it is shown as one direction.\n").getBytes(StandardCharsets.UTF_8));
                        zip.closeEntry();
                        byte[] buffer = new byte[32768];
                        for (File file : files) {
                            if (!file.isFile()) continue;
                            zip.putNextEntry(new ZipEntry(file.getName()));
                            try (FileInputStream input = new FileInputStream(file)) {
                                int n; while ((n = input.read(buffer)) != -1) zip.write(buffer, 0, n);
                            }
                            zip.closeEntry();
                        }
                    }
                }
            } catch (Exception e) { error = e.toString(); }
            final String failure = error;
            ui.post(() -> {
                if (destroyed) return;
                exporting = false; updateButtons();
                status.setText(failure == null ? "Compass ZIP exported. Share it for direction analysis." : "Export failed: " + failure);
            });
        }, "compass-export").start();
    }

    static void write(File file, String text) throws IOException {
        try (OutputStreamWriter out = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) { out.write(text); }
    }
    static String stamp() { return new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date()); }
    int dp(float n) { return (int) (n * getResources().getDisplayMetrics().density + .5f); }
    TextView text(LinearLayout parent, String value, int sp) {
        TextView t = new TextView(this); t.setText(value); t.setTextSize(sp); t.setTextColor(Color.WHITE);
        t.setPadding(0, dp(3), 0, dp(3)); parent.addView(t); return t;
    }
    Button button(LinearLayout parent, String value, View.OnClickListener click) {
        Button b = new Button(this); b.setText(value); b.setAllCaps(false); b.setOnClickListener(click);
        parent.addView(b, new LinearLayout.LayoutParams(-1, -2)); return b;
    }
    Spinner selector(LinearLayout parent, String[] labels) {
        Spinner s = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, labels);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item); s.setAdapter(adapter);
        parent.addView(s, new LinearLayout.LayoutParams(-1, -2)); return s;
    }

    static final class CompassView extends View {
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        CompassModel.Estimate estimate;
        boolean calibrated;
        CompassView(Context context) { super(context); }
        void set(CompassModel.Estimate next, boolean calibrated) {
            estimate = next; this.calibrated = calibrated; invalidate();
        }
        @Override protected void onDraw(Canvas c) {
            super.onDraw(c);
            float scale = getResources().getDisplayMetrics().density;
            float cx = getWidth() / 2f, cy = getHeight() / 2f;
            float r = Math.max(30 * scale, Math.min(getWidth(), getHeight()) / 2f - 44 * scale);
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(2 * scale); p.setColor(0xff647282);
            c.drawCircle(cx, cy, r, p);
            for (int i = 0; i < 8; i++) {
                double angle = Math.toRadians(i * 45);
                c.drawLine(cx + (float)Math.sin(angle) * (r - 6 * scale), cy - (float)Math.cos(angle) * (r - 6 * scale),
                        cx + (float)Math.sin(angle) * (r + 5 * scale), cy - (float)Math.cos(angle) * (r + 5 * scale), p);
            }
            if (estimate != null) {
                RectF ring = new RectF(cx - r, cy - r, cx + r, cy + r);
                p.setStrokeWidth(12 * scale); p.setStrokeCap(Paint.Cap.ROUND);
                p.setColor(0xff52b6ff); c.drawArc(ring, estimate.primary - 90 - 18, 36, false, p);
                if (!estimate.resolved) {
                    p.setColor(0xffffbc70); c.drawArc(ring, estimate.alternative - 90 - 18, 36, false, p);
                }
            }
            p.setStyle(Paint.Style.FILL); p.setColor(Color.WHITE); p.setTextSize(14 * scale);
            String[] shortNames = {"F", "FR", "R", "BR", "B", "BL", "L", "FL"};
            for (int i = 0; i < 8; i++) {
                double angle = Math.toRadians(i * 45);
                float x = cx + (float)Math.sin(angle) * (r + 24 * scale);
                float y = cy - (float)Math.cos(angle) * (r + 24 * scale);
                c.drawText(shortNames[i], x - p.measureText(shortNames[i]) / 2f, y + 5 * scale, p);
            }
            p.setTextSize(12 * scale); p.setColor(0xffc0cad5);
            String center = !calibrated ? "CALIBRATE" : estimate == null ? "LISTENING" : estimate.resolved ? "DIRECTION" : "TWO POSSIBLE";
            c.drawText(center, cx - p.measureText(center) / 2, cy + 4 * scale, p);
            p.setColor(0xffe5edf6); c.drawCircle(cx, cy + 24 * scale, 5 * scale, p);
        }
    }
}
