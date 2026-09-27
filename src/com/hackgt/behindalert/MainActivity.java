package com.hackgt.behindalert;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.Date;
import java.util.Locale;

/**
 * Left / middle / right sound direction from the phone's two mics.
 * Calibration: (1) quiet room -> noise floor, mic gain mismatch, noise correlation baseline
 *              (2) claps on your LEFT, (3) on your RIGHT, (4) in FRONT.
 */
public class MainActivity extends Activity {
    static final int FS = 48000;
    static final int BLOCK = 2048;                                  // ~43 ms per read
    static final double C = 343.0;
    static final int MAX_LAG = (int) Math.ceil(0.30 / C * FS);      // search ±30 cm of delay
    static final int NOISE_BLOCKS = 70;                             // ~3 s of quiet
    static final int CAL_CLAPS = 4;
    static final int[] SOURCES = {
            MediaRecorder.AudioSource.CAMCORDER, MediaRecorder.AudioSource.UNPROCESSED,
            MediaRecorder.AudioSource.MIC, MediaRecorder.AudioSource.VOICE_RECOGNITION };
    static final String[] SOURCE_NAMES = { "CAMCORDER", "UNPROCESSED", "MIC", "VOICE_RECOGNITION" };
    static final String[] MODE_NAMES = { "All cues combined", "Delay (GCC) only", "Onset only", "Level only" };
    static final int COL_BG = 0xFF0E1116, COL_CARD = 0xFF171B22, COL_FG = 0xFFE8ECF1, COL_MUT = 0xFF8B95A3,
            COL_OK = 0xFF3ECF8E, COL_BAD = 0xFFFF4D4F, COL_WARN = 0xFFF5A524, COL_ACC = 0xFF5B9DFF,
            COL_LEFT = 0xFF5B9DFF, COL_RIGHT = 0xFFE879F9;
    static final int[] CUE_COL = { 0xFF5B9DFF, 0xFFF5A524, 0xFF3ECF8E };

    // calibration steps
    static final int STEP_NONE = 0, STEP_NOISE = 1, STEP_LEFT = 2, STEP_RIGHT = 3, STEP_MID = 4;

    // ---- settings (UI thread writes, audio thread reads) ----
    volatile double triggerDb = 10, threshold = 0.4, minConf = 0.20, onsetFrac = 0.30;
    volatile int mode = 0;                     // index into MODE_NAMES; 0 = combined
    volatile boolean vibrateOn = true;
    volatile String questIp = "";
    volatile int sourceIdx = 0;

    // ---- calibration state (guarded by `this`) ----
    final Direction dir = new Direction();
    double gainOffsetDb = 0;                   // L minus R level in a quiet room (mic sensitivity mismatch)
    double[] noiseCurve = null;                // average correlation of pure room noise
    double calNoiseDb = Double.NaN;
    int step = STEP_NONE;
    boolean wizard = false;                    // run noise -> left -> right -> front automatically
    int stepCount = 0;
    double noiseSumL, noiseSumR; double[] noiseCurveSum; int noiseCurveN;

    // ---- scorecard ----
    int scoreTarget = -1, scoreN = 0;
    final int[] scoreCorrect = new int[4];     // GCC, onset, level, combined
    String scoreHistory = "";

    // ---- audio ----
    volatile boolean running = false;
    Thread audioThread; AudioRecord rec;
    DatagramSocket udp; InetAddress questAddr; String questAddrFor = "";

    // ---- UI ----
    final Handler ui = new Handler(Looper.getMainLooper());
    SharedPreferences prefs;
    View flash;
    Button startBtn, sourceBtn, modeBtn;
    TextView status, stereoInfo, verdict, stepText, calReport, zoneText, liveText, scoreText, logText, questStat, floorText, dbLText, dbRText;
    ProgressBar meterL, meterR, stepBar;
    DirView dirView;
    final ArrayDeque<String> logLines = new ArrayDeque<>();

    // =================================================================
    // UI
    // =================================================================
    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        prefs = getSharedPreferences("lr", MODE_PRIVATE);
        loadPrefs();

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(COL_BG);
        ScrollView scroll = new ScrollView(this);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(16), dp(20), dp(16), dp(48));
        scroll.addView(col);
        root.addView(scroll);
        flash = new View(this); flash.setAlpha(0f); flash.setClickable(false);
        root.addView(flash, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);

        TextView title = text(col, "Left / Right Sound Test", 22, COL_FG); title.setTypeface(null, Typeface.BOLD);
        button(col, "Listen · sounds and names", v -> {
            stopAudio();
            startActivity(new Intent(this, ListenActivity.class));
        });
        button(col, "Microphone test (start here)", v -> {
            stopAudio();
            startActivity(new Intent(this, DiagnosticActivity.class));
        });
        button(col, "360° sound compass", v -> {
            stopAudio();
            startActivity(new Intent(this, CompassActivity.class));
        });
        text(col, "Mount the phone flat with its length running ear-to-ear (one mic by each ear). Calibrate once, then every sound is labelled LEFT, middle or RIGHT.", 13, COL_MUT);

        // --- start ---
        LinearLayout c0 = card(col);
        LinearLayout r0 = row(c0);
        startBtn = button(r0, "Start", v -> { if (running) stopAudio(); else ensurePermissionAndStart(); });
        sourceBtn = button(r0, "Source: " + SOURCE_NAMES[sourceIdx], v -> {
            sourceIdx = (sourceIdx + 1) % SOURCES.length;
            sourceBtn.setText("Source: " + SOURCE_NAMES[sourceIdx]);
            prefs.edit().putInt("source", sourceIdx).apply();
            resetStereoCount();
            if (running) { stopAudio(); ensurePermissionAndStart(); }
        });
        status = text(c0, "Tap Start. If the verdict says MONO, tap Source.", 13, COL_MUT);
        stereoInfo = text(c0, "–", 13, COL_FG);
        verdict = text(c0, "waiting", 15, COL_WARN); verdict.setTypeface(null, Typeface.BOLD);
        LinearLayout ml = row(c0); text(ml, "L ", 13, COL_MUT); meterL = meter(ml); dbLText = text(ml, "  –", 13, COL_MUT);
        LinearLayout mr = row(c0); text(mr, "R ", 13, COL_MUT); meterR = meter(mr); dbRText = text(mr, "  –", 13, COL_MUT);
        floorText = text(c0, "", 12, COL_MUT);

        // --- calibration ---
        LinearLayout c1 = card(col);
        heading(c1, "1 · Calibrate");
        text(c1, "Stand still with the phone mounted. The steps run in order: 3 s of quiet, then 4 claps on your LEFT, 4 on your RIGHT, 4 in FRONT. Clap at arm's length, about ear height.", 13, COL_MUT);
        button(c1, "▶ Run full calibration", v -> startWizard());
        stepText = text(c1, "", 17, COL_WARN); stepText.setTypeface(null, Typeface.BOLD);
        stepBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        stepBar.setMax(100); c1.addView(stepBar, new LinearLayout.LayoutParams(-1, dp(10)));
        text(c1, "Redo one step:", 12, COL_MUT);
        LinearLayout r1 = row(c1);
        button(r1, "Quiet", v -> startStep(STEP_NOISE, false));
        button(r1, "Left", v -> startStep(STEP_LEFT, false));
        button(r1, "Right", v -> startStep(STEP_RIGHT, false));
        button(r1, "Front", v -> startStep(STEP_MID, false));
        LinearLayout r1b = row(c1);
        button(r1b, "Skip step", v -> skipStep());
        button(r1b, "Clear all", v -> {
            synchronized (this) { dir.clearAll(); gainOffsetDb = 0; noiseCurve = null; calNoiseDb = Double.NaN; step = STEP_NONE; wizard = false; }
            savePrefs(); refreshCal(); stepText.setText("");
        });
        calReport = text(c1, "", 12, COL_FG); calReport.setTypeface(Typeface.MONOSPACE);

        // --- live ---
        LinearLayout c2 = card(col);
        heading(c2, "2 · Live");
        zoneText = text(c2, "—", 36, COL_FG);
        zoneText.setGravity(Gravity.CENTER); zoneText.setTypeface(null, Typeface.BOLD);
        zoneText.setPadding(0, dp(14), 0, dp(14)); zoneText.setBackground(round(0xFF11151B));
        dirView = new DirView(this);
        c2.addView(dirView, new LinearLayout.LayoutParams(-1, dp(90)));
        liveText = text(c2, "", 12, COL_FG); liveText.setTypeface(Typeface.MONOSPACE);
        text(c2, "Bar: −1 = where your LEFT claps landed, +1 = your RIGHT claps. Big white dot = decision; small dots = each cue (blue delay, yellow onset, green level). Shaded middle = 'middle' zone.", 12, COL_MUT);

        // --- scorecard ---
        LinearLayout c3 = card(col);
        heading(c3, "3 · Test accuracy");
        text(c3, "Tap a direction, then make 10 sounds from there (claps, knocks, talking). Every cue is scored.", 13, COL_MUT);
        LinearLayout r3 = row(c3);
        button(r3, "10 LEFT", v -> startScore(Direction.LEFT));
        button(r3, "10 FRONT", v -> startScore(Direction.MID));
        button(r3, "10 RIGHT", v -> startScore(Direction.RIGHT));
        scoreText = text(c3, "–", 13, COL_FG); scoreText.setTypeface(Typeface.MONOSPACE);

        // --- settings ---
        LinearLayout c4 = card(col);
        heading(c4, "Settings");
        modeBtn = button(c4, "Decide with: " + MODE_NAMES[mode], v -> {
            mode = (mode + 1) % MODE_NAMES.length; modeBtn.setText("Decide with: " + MODE_NAMES[mode]);
            prefs.edit().putInt("mode", mode).apply();
        });
        slider(c4, "Middle zone: |score| below %.2f", 0.10, 0.90, 0.05, threshold, v -> threshold = v);
        slider(c4, "Trigger: %.0f dB above background", 4, 30, 1, triggerDb, v -> triggerDb = v);
        slider(c4, "GCC min confidence %.2f", 0.05, 0.80, 0.05, minConf, v -> minConf = v);
        slider(c4, "Onset threshold %.2f × peak", 0.10, 0.70, 0.05, onsetFrac, v -> onsetFrac = v);
        CheckBox vib = new CheckBox(this); vib.setText("Vibrate on LEFT/RIGHT"); vib.setTextColor(COL_FG); vib.setChecked(true);
        vib.setOnCheckedChangeListener((bb, on) -> vibrateOn = on);
        c4.addView(vib);

        // --- quest ---
        LinearLayout c5 = card(col);
        heading(c5, "Send to Quest (UDP 5005)");
        text(c5, "Sends 90 for LEFT, 270 for RIGHT, 0 for middle (counter-clockwise degrees, same as the Unity SoundDirection script).", 12, COL_MUT);
        EditText ip = new EditText(this);
        ip.setHint("Quest IP, e.g. 192.168.43.50"); ip.setHintTextColor(COL_MUT); ip.setTextColor(COL_FG);
        ip.setInputType(InputType.TYPE_CLASS_PHONE); ip.setText(questIp);
        ip.addTextChangedListener(new IpWatcher());
        c5.addView(ip);
        LinearLayout r5 = row(c5);
        button(r5, "Send test (90)", v -> new Thread(() -> { String e = send("90"); ui.post(() -> questStat.setText(e == null ? "sent ✓" : "failed: " + e)); }).start());
        questStat = text(c5, "Phone and Quest must be on the same Wi-Fi / hotspot.", 12, COL_MUT);

        // --- log ---
        LinearLayout c6 = card(col);
        heading(c6, "Event log");
        logText = text(c6, "", 11, COL_MUT); logText.setTypeface(Typeface.MONOSPACE);

        refreshCal();
    }

    @Override protected void onDestroy() { stopAudio(); super.onDestroy(); }

    // =================================================================
    // Audio
    // =================================================================
    void ensurePermissionAndStart() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{ Manifest.permission.RECORD_AUDIO }, 1);
        else startAudio();
    }

    @Override public void onRequestPermissionsResult(int code, String[] p, int[] res) {
        if (res.length > 0 && res[0] == PackageManager.PERMISSION_GRANTED) startAudio();
        else status.setText("Microphone permission denied.");
    }

    void startAudio() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            status.setText("Microphone permission is required."); return;
        }
        int minBuf = AudioRecord.getMinBufferSize(FS, AudioFormat.CHANNEL_IN_STEREO, AudioFormat.ENCODING_PCM_16BIT);
        try {
            rec = new AudioRecord(SOURCES[sourceIdx], FS, AudioFormat.CHANNEL_IN_STEREO, AudioFormat.ENCODING_PCM_16BIT,
                    Math.max(minBuf, BLOCK * 2 * 2 * 4));
        } catch (Exception e) { status.setText("Could not create recorder: " + e.getMessage()); return; }
        if (rec.getState() != AudioRecord.STATE_INITIALIZED) {
            status.setText("Source " + SOURCE_NAMES[sourceIdx] + " can't record stereo here. Tap Source.");
            rec.release(); rec = null; return;
        }
        rec.startRecording();
        stereoInfo.setText("Source " + SOURCE_NAMES[sourceIdx] + " · " + rec.getSampleRate() + " Hz · " + rec.getChannelCount() + " channel(s)");
        resetStereoCount();
        running = true;
        startBtn.setText("Stop");
        status.setText(dir.calibrated() ? "Running (using saved calibration)." : "Running. Now run the calibration below.");
        log("started · " + SOURCE_NAMES[sourceIdx]);
        final AudioRecord r = rec;
        audioThread = new Thread(() -> audioLoop(r), "audio");
        audioThread.setPriority(Thread.MAX_PRIORITY);
        audioThread.start();
    }

    void stopAudio() {
        running = false;
        if (audioThread != null) { try { audioThread.join(500); } catch (InterruptedException ignored) {} audioThread = null; }
        if (rec != null) { try { rec.stop(); } catch (Exception ignored) {} rec.release(); rec = null; }
        if (startBtn != null) startBtn.setText("Start");
    }

    // audio-thread state
    double noiseFloor = -60;
    long lastEventMs = 0;
    int identFrames = 0, stereoFrames = 0;
    final float[] prevL = new float[BLOCK], prevR = new float[BLOCK];
    final Dsp.GccPhat gcc = new Dsp.GccPhat(2 * BLOCK);

    void resetStereoCount() { identFrames = 0; stereoFrames = 0; if (verdict != null) { verdict.setText("waiting — make some noise"); verdict.setTextColor(COL_WARN); } }

    void audioLoop(AudioRecord r) {
        short[] buf = new short[BLOCK * 2];
        float[] l = new float[BLOCK], rr = new float[BLOCK];
        while (running) {
            int off = 0;
            while (off < buf.length && running) {
                int n = r.read(buf, off, buf.length - off);
                if (n < 0) { final int err = n; ui.post(() -> status.setText("read error " + err)); running = false; break; }
                off += n;
            }
            if (!running) break;
            for (int i = 0; i < BLOCK; i++) { l[i] = buf[2 * i] / 32768f; rr[i] = buf[2 * i + 1] / 32768f; }
            processBlock(l, rr);
        }
    }

    void processBlock(float[] l, float[] r) {
        double sl = 0, sr = 0, sd = 0;
        for (int i = 0; i < BLOCK; i++) { double a = l[i], c = r[i], d = a - c; sl += a * a; sr += c * c; sd += d * d; }
        final double dbL = 10 * Math.log10(sl / BLOCK + 1e-12), dbR = 10 * Math.log10(sr / BLOCK + 1e-12);
        double db = 10 * Math.log10((sl + sr) / (2.0 * BLOCK) + 1e-12);

        String v = null; boolean ok = false;
        if (sl > 1e-8) {
            if (sd < 1e-6 * sl) identFrames++; else stereoFrames++;
            int total = identFrames + stereoFrames;
            if (total > 20) { ok = (double) identFrames / total <= 0.9; v = ok ? "STEREO ✓" : "MONO — L and R identical. Tap Source."; }
        }
        final String fv = v; final boolean fok = ok; final double fl = noiseFloor;
        ui.post(() -> {
            meterL.setProgress(pct(dbL)); meterR.setProgress(pct(dbR));
            dbLText.setText(String.format(Locale.US, " %4.0f dB", dbL)); dbRText.setText(String.format(Locale.US, " %4.0f dB", dbR));
            floorText.setText(String.format(Locale.US, "background %.0f dB · trigger at %.0f dB", fl, fl + triggerDb));
            if (fv != null) { verdict.setText(fv); verdict.setTextColor(fok ? COL_OK : COL_BAD); }
        });

        float[] wl = new float[2 * BLOCK], wr = new float[2 * BLOCK];
        System.arraycopy(prevL, 0, wl, 0, BLOCK); System.arraycopy(l, 0, wl, BLOCK, BLOCK);
        System.arraycopy(prevR, 0, wr, 0, BLOCK); System.arraycopy(r, 0, wr, BLOCK, BLOCK);
        System.arraycopy(l, 0, prevL, 0, BLOCK); System.arraycopy(r, 0, prevR, 0, BLOCK);

        // ---- quiet-room calibration: just listen ----
        boolean noiseStep;
        synchronized (this) { noiseStep = step == STEP_NOISE; }
        if (noiseStep) { collectNoise(sl, sr, wl, wr); return; }

        long now = SystemClock.uptimeMillis();
        boolean loud = db > noiseFloor + triggerDb && db > -70;
        if (loud && now - lastEventMs > 250 && analyse(wl, wr, db)) lastEventMs = now;
        if (db < noiseFloor) noiseFloor = 0.7 * noiseFloor + 0.3 * db;
        else if (!loud) noiseFloor += 0.04;
    }

    void collectNoise(double sl, double sr, float[] wl, float[] wr) {
        Dsp.GccResult g = gcc.run(wl, wr, MAX_LAG, FS, 150, 12000);
        String done = null; int progress;
        synchronized (this) {
            noiseSumL += sl; noiseSumR += sr;
            if (noiseCurveSum == null) noiseCurveSum = new double[g.curve.length];
            for (int i = 0; i < g.curve.length; i++) noiseCurveSum[i] += g.curve[i];
            noiseCurveN++;
            progress = noiseCurveN * 100 / NOISE_BLOCKS;
            if (noiseCurveN >= NOISE_BLOCKS) {
                gainOffsetDb = Math.max(-12, Math.min(12, 10 * Math.log10((noiseSumL + 1e-12) / (noiseSumR + 1e-12))));
                noiseCurve = new double[noiseCurveSum.length];
                for (int i = 0; i < noiseCurve.length; i++) noiseCurve[i] = noiseCurveSum[i] / noiseCurveN;
                calNoiseDb = 10 * Math.log10((noiseSumL + noiseSumR) / (2.0 * BLOCK * noiseCurveN) + 1e-12);
                noiseFloor = calNoiseDb;
                double[] pk = Dsp.peakOf(noiseCurve);
                done = String.format(Locale.US, "Quiet done: background %.0f dB · mic gain mismatch %+.1f dB (L−R) · noise correlation peak %.2f at %+.0f µs",
                        calNoiseDb, gainOffsetDb, pk[1], pk[0] / FS * 1e6);
                savePrefsLocked();
                advance();
            }
        }
        final String d = done; final int p = progress;
        ui.post(() -> { stepBar.setProgress(Math.min(100, p)); if (d != null) { log(d); refreshCal(); showStep(); } });
    }

    /** @return true if this counted as an event */
    boolean analyse(float[] wl, float[] wr, double db) {
        Dsp.GccResult g = gcc.run(wl, wr, MAX_LAG, FS, 150, 12000);
        Dsp.OnsetResult o = Dsp.onset(wl, wr, onsetFrac, MAX_LAG);
        double[] f = new double[Direction.NF];
        double conf;
        synchronized (this) {
            double[] curve = g.curve;
            if (noiseCurve != null) {                               // remove the room/phone's own correlation
                curve = curve.clone();
                for (int i = 0; i < curve.length; i++) curve[i] -= noiseCurve[i];
            }
            double[] pk = Dsp.peakOf(curve);
            conf = pk[1];
            f[Direction.GCC] = conf >= minConf ? pk[0] / FS * 1e6 : Double.NaN;
            f[Direction.ONSET] = o.valid ? o.lag / FS * 1e6 : Double.NaN;
            f[Direction.LEVEL] = Dsp.levelDb(wl, wr) - gainOffsetDb;
        }
        final double fconf = conf;

        String calMsg = null; Direction.Result res = null; String scoreMsg = null; int zone;
        synchronized (this) {
            if (step == STEP_LEFT || step == STEP_RIGHT || step == STEP_MID) {
                int cls = step == STEP_LEFT ? Direction.LEFT : step == STEP_RIGHT ? Direction.RIGHT : Direction.MID;
                dir.add(cls, f);
                stepCount++;
                calMsg = String.format(Locale.US, "clap %d/%d: delay %s · onset %s · level %+.1f dB", stepCount, CAL_CLAPS,
                        fmt(f[0], "µs"), fmt(f[1], "µs"), f[2]);
                if (stepCount >= CAL_CLAPS) { savePrefsLocked(); advance(); }
                final String m = calMsg; final int p = stepCount * 100 / CAL_CLAPS;
                ui.post(() -> { log(m); stepBar.setProgress(Math.min(100, p)); refreshCal(); showStep(); });
                return true;
            }
            res = dir.classify(f, threshold, mode == 0 ? -1 : mode - 1);
            zone = res.combinedZone;
            if (scoreTarget >= 0) {
                for (int k = 0; k < Direction.NF; k++) if (res.zone[k] == scoreTarget) scoreCorrect[k]++;
                if (zone == scoreTarget) scoreCorrect[3]++;
                scoreN++;
                scoreMsg = String.format(Locale.US, "Expecting %s (%d/10)\nDelay (GCC) %2d/%d\nOnset       %2d/%d\nLevel       %2d/%d\nDECISION    %2d/%d",
                        Direction.CLASS[scoreTarget], scoreN, scoreCorrect[0], scoreN, scoreCorrect[1], scoreN, scoreCorrect[2], scoreN, scoreCorrect[3], scoreN);
                if (scoreN >= 10) {
                    scoreHistory = String.format(Locale.US, "%-6s GCC %d · onset %d · level %d · decision %d  (/10)\n",
                            Direction.CLASS[scoreTarget], scoreCorrect[0], scoreCorrect[1], scoreCorrect[2], scoreCorrect[3]) + scoreHistory;
                    scoreMsg = "Done ✓\n" + scoreHistory;
                    scoreTarget = -1;
                }
            }
        }
        if (zone < 0) return false;
        send(zone == Direction.LEFT ? "90" : zone == Direction.RIGHT ? "270" : "0");

        final Direction.Result fr = res; final int fz = zone; final String sm = scoreMsg;
        final StringBuilder live = new StringBuilder();
        for (int k = 0; k < Direction.NF; k++)
            live.append(String.format(Locale.US, "%-11s %9s  score %s  → %s\n", Direction.FEATURE[k], fmt(f[k], Direction.UNIT[k]),
                    Double.isNaN(fr.s[k]) ? "  –  " : String.format(Locale.US, "%+.2f", fr.s[k]),
                    fr.zone[k] < 0 ? "–" : Direction.CLASS[fr.zone[k]]));
        live.append(String.format(Locale.US, "DECISION    score %+.2f → %s   (GCC conf %.2f, %.0f dB)", fr.combined, Direction.CLASS[fz], fconf, db));
        final String line = String.format(Locale.US, "%-6s %+.2f | d %s o %s lv %+.1f", Direction.CLASS[fz], fr.combined, fmt(f[0], ""), fmt(f[1], ""), f[2]);
        ui.post(() -> {
            dirView.set(fr, threshold);
            liveText.setText(live.toString());
            zoneText.setText(fz == Direction.LEFT ? "◀  LEFT" : fz == Direction.RIGHT ? "RIGHT  ▶" : "middle");
            int colr = fz == Direction.LEFT ? COL_LEFT : fz == Direction.RIGHT ? COL_RIGHT : 0xFF1D2A22;
            zoneText.setTextColor(fz == Direction.MID ? COL_OK : Color.WHITE);
            zoneText.setBackground(round(colr));
            if (sm != null) scoreText.setText(sm);
            log(line);
            if (fz != Direction.MID) {
                flash.setBackgroundColor(colr);
                flash.setAlpha(0.45f); flash.animate().alpha(0f).setDuration(350).start();
                if (vibrateOn) vibrate();
            }
        });
        return true;
    }

    static String fmt(double v, String unit) { return Double.isNaN(v) ? "–" : String.format(Locale.US, "%+.0f%s", v, unit.isEmpty() ? "" : " " + unit); }

    // =================================================================
    // Calibration flow
    // =================================================================
    void startWizard() {
        if (!running) { status.setText("Tap Start first."); return; }
        synchronized (this) { dir.clearAll(); }
        startStep(STEP_NOISE, true);
    }

    void startStep(int s, boolean wiz) {
        if (!running) { status.setText("Tap Start first."); return; }
        synchronized (this) {
            step = s; wizard = wiz; stepCount = 0;
            if (s == STEP_NOISE) { noiseSumL = noiseSumR = 0; noiseCurveSum = null; noiseCurveN = 0; }
            if (s == STEP_LEFT) dir.clear(Direction.LEFT);
            if (s == STEP_RIGHT) dir.clear(Direction.RIGHT);
            if (s == STEP_MID) dir.clear(Direction.MID);
        }
        stepBar.setProgress(0);
        showStep();
    }

    /** lock held: move to the next wizard step (or stop) */
    void advance() {
        stepCount = 0;
        if (!wizard) { step = STEP_NONE; return; }
        if (step == STEP_NOISE) step = STEP_LEFT;
        else if (step == STEP_LEFT) step = STEP_RIGHT;
        else if (step == STEP_RIGHT) step = STEP_MID;
        else { step = STEP_NONE; wizard = false; }
        if (step == STEP_NOISE) { noiseSumL = noiseSumR = 0; noiseCurveSum = null; noiseCurveN = 0; }
        ui.post(() -> stepBar.setProgress(0));
    }

    void skipStep() { synchronized (this) { if (step != STEP_NONE) advance(); } showStep(); refreshCal(); }

    void showStep() {
        int s; synchronized (this) { s = step; }
        switch (s) {
            case STEP_NOISE: stepText.setText("🤫  Stay quiet for 3 seconds…"); break;
            case STEP_LEFT:  stepText.setText("👈  Clap on your LEFT, " + CAL_CLAPS + " times (pause 1 s between)"); break;
            case STEP_RIGHT: stepText.setText("👉  Clap on your RIGHT, " + CAL_CLAPS + " times"); break;
            case STEP_MID:   stepText.setText("☝  Clap in FRONT of you, " + CAL_CLAPS + " times"); break;
            default:
                boolean cal; synchronized (this) { cal = dir.calibrated(); }
                stepText.setText(cal ? "✓ Calibrated — try it in section 2" : "Not calibrated yet");
                stepText.setTextColor(cal ? COL_OK : COL_WARN);
                return;
        }
        stepText.setTextColor(COL_WARN);
    }

    void refreshCal() {
        String rep; double go, nd; boolean cal;
        synchronized (this) { rep = dir.report(); go = gainOffsetDb; nd = calNoiseDb; cal = dir.calibrated(); }
        calReport.setText((Double.isNaN(nd) ? "quiet step: not done\n" : String.format(Locale.US, "background %.0f dB · mic gain mismatch %+.1f dB\n", nd, go)) + rep);
        if (!cal && calReport.getText().toString().contains("✗"))
            calReport.append("\n⚠ No cue separates left from right. Check the phone runs ear-to-ear and clap further out to the side.");
    }

    void startScore(int target) {
        synchronized (this) { scoreTarget = target; scoreN = 0; java.util.Arrays.fill(scoreCorrect, 0); }
        scoreText.setText("Make 10 sounds from " + Direction.CLASS[target] + "…");
    }

    // =================================================================
    // Output
    // =================================================================
    String send(String msg) {
        String ip = questIp;
        if (ip == null || ip.isEmpty()) return "no Quest IP set";
        try {
            if (udp == null) udp = new DatagramSocket();
            if (!ip.equals(questAddrFor)) { questAddr = InetAddress.getByName(ip); questAddrFor = ip; }
            byte[] b = msg.getBytes();
            udp.send(new DatagramPacket(b, b.length, questAddr, 5005));
            return null;
        } catch (Exception e) { return e.getClass().getSimpleName() + ": " + e.getMessage(); }
    }

    void vibrate() {
        Vibrator vb = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        if (vb == null) return;
        if (Build.VERSION.SDK_INT >= 26) vb.vibrate(VibrationEffect.createOneShot(120, VibrationEffect.DEFAULT_AMPLITUDE));
        else vb.vibrate(120);
    }

    // =================================================================
    // Prefs
    // =================================================================
    void loadPrefs() {
        questIp = prefs.getString("ip", "");
        sourceIdx = prefs.getInt("source", 0) % SOURCES.length;
        mode = prefs.getInt("mode", 0) % MODE_NAMES.length;
        dir.deserialize(prefs.getString("cal", ""));
        gainOffsetDb = prefs.getFloat("gain", 0f);
        calNoiseDb = prefs.contains("noiseDb") ? prefs.getFloat("noiseDb", 0f) : Double.NaN;
        String nc = prefs.getString("noiseCurve", "");
        if (!nc.isEmpty()) {
            try {
                String[] p = nc.split(",");
                double[] c = new double[p.length];
                for (int i = 0; i < p.length; i++) c[i] = Double.parseDouble(p[i]);
                if (c.length == 2 * MAX_LAG + 1) noiseCurve = c;
            } catch (Exception ignored) {}
        }
    }
    void savePrefs() { synchronized (this) { savePrefsLocked(); } }
    void savePrefsLocked() {
        SharedPreferences.Editor e = prefs.edit();
        e.putString("cal", dir.serialize());
        e.putFloat("gain", (float) gainOffsetDb);
        if (Double.isNaN(calNoiseDb)) e.remove("noiseDb"); else e.putFloat("noiseDb", (float) calNoiseDb);
        if (noiseCurve == null) e.remove("noiseCurve");
        else {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < noiseCurve.length; i++) { if (i > 0) sb.append(','); sb.append((float) noiseCurve[i]); }
            e.putString("noiseCurve", sb.toString());
        }
        e.apply();
    }

    // =================================================================
    // UI helpers
    // =================================================================
    int dp(float v) { return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics()); }
    static int pct(double db) { return (int) Math.max(0, Math.min(100, (db + 80) / 80 * 100)); }
    GradientDrawable round(int color) { GradientDrawable g = new GradientDrawable(); g.setColor(color); g.setCornerRadius(dp(12)); return g; }

    LinearLayout card(LinearLayout parent) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(14), dp(12), dp(14), dp(12));
        c.setBackground(round(COL_CARD));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(12);
        parent.addView(c, lp);
        return c;
    }
    LinearLayout row(LinearLayout parent) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        parent.addView(r, new LinearLayout.LayoutParams(-1, -2));
        return r;
    }
    TextView text(LinearLayout parent, String s, float sp, int color) {
        TextView t = new TextView(this);
        t.setText(s); t.setTextSize(sp); t.setTextColor(color);
        t.setPadding(0, dp(3), 0, dp(3));
        parent.addView(t);
        return t;
    }
    void heading(LinearLayout parent, String s) { TextView t = text(parent, s, 16, COL_FG); t.setTypeface(null, Typeface.BOLD); }
    Button button(LinearLayout parent, String s, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(s); b.setAllCaps(false); b.setOnClickListener(l);
        LinearLayout.LayoutParams lp = parent.getOrientation() == LinearLayout.HORIZONTAL
                ? new LinearLayout.LayoutParams(0, -2, 1f) : new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(dp(2), dp(4), dp(2), dp(4));
        parent.addView(b, lp);
        return b;
    }
    ProgressBar meter(LinearLayout parent) {
        ProgressBar p = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        p.setMax(100);
        parent.addView(p, new LinearLayout.LayoutParams(0, dp(14), 1f));
        return p;
    }
    interface DoubleSetter { void set(double v); }
    void slider(LinearLayout parent, String fmt, double min, double max, double step, double init, DoubleSetter setter) {
        TextView label = text(parent, String.format(Locale.US, fmt, init), 13, COL_MUT);
        SeekBar sb = new SeekBar(this);
        sb.setMax((int) Math.round((max - min) / step));
        sb.setProgress((int) Math.round((init - min) / step));
        sb.setOnSeekBarChangeListener(new SliderListener(label, fmt, min, step, setter));
        parent.addView(sb);
    }
    final class IpWatcher implements TextWatcher {
        public void beforeTextChanged(CharSequence s, int a, int b2, int c) {}
        public void onTextChanged(CharSequence s, int a, int b2, int c) {}
        public void afterTextChanged(Editable s) { questIp = s.toString().trim(); prefs.edit().putString("ip", questIp).apply(); }
    }
    static final class SliderListener implements SeekBar.OnSeekBarChangeListener {
        final TextView label; final String fmt; final double min, step; final DoubleSetter setter;
        SliderListener(TextView label, String fmt, double min, double step, DoubleSetter setter) {
            this.label = label; this.fmt = fmt; this.min = min; this.step = step; this.setter = setter;
        }
        public void onProgressChanged(SeekBar s, int p, boolean u) { double v = min + p * step; setter.set(v); label.setText(String.format(Locale.US, fmt, v)); }
        public void onStartTrackingTouch(SeekBar s) {}
        public void onStopTrackingTouch(SeekBar s) {}
    }
    void log(String s) {
        logLines.addFirst(new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date()) + " " + s);
        while (logLines.size() > 60) logLines.removeLast();
        StringBuilder sb = new StringBuilder();
        for (String x : logLines) sb.append(x).append('\n');
        logText.setText(sb.toString());
    }

    // =================================================================
    // Direction bar: -1 (your left claps) ... +1 (your right claps)
    // =================================================================
    static final class DirView extends View {
        Direction.Result res; double thr = 0.4;
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        DirView(Context c) { super(c); }
        void set(Direction.Result r, double t) { res = r; thr = t; invalidate(); }
        float x(double s, float w) { return (float) (w / 2 + Math.max(-1.5, Math.min(1.5, s)) / 1.5 * (w / 2 - 20)); }
        @Override protected void onDraw(Canvas cv) {
            float w = getWidth(), h = getHeight(), cy = h / 2;
            cv.drawColor(0xFF0B0E12);
            p.setStyle(Paint.Style.FILL);
            p.setColor(0xFF1D2A22); cv.drawRect(x(-thr, w), 8, x(thr, w), h - 8, p);         // middle zone
            p.setColor(0xFF2A313C); p.setStrokeWidth(3); cv.drawLine(20, cy, w - 20, cy, p);
            p.setColor(COL_LEFT); cv.drawLine(x(-1, w), cy - 18, x(-1, w), cy + 18, p);
            p.setColor(COL_RIGHT); cv.drawLine(x(1, w), cy - 18, x(1, w), cy + 18, p);
            p.setTextSize(26); p.setColor(COL_MUT);
            cv.drawText("LEFT", 20, 30, p);
            cv.drawText("RIGHT", w - 20 - p.measureText("RIGHT"), 30, p);
            if (res == null) return;
            for (int k = 0; k < Direction.NF; k++) {
                if (Double.isNaN(res.s[k])) continue;
                p.setColor(CUE_COL[k]); cv.drawCircle(x(res.s[k], w), cy + 26 + k * 14, 8, p);
            }
            if (!Double.isNaN(res.combined)) { p.setColor(Color.WHITE); cv.drawCircle(x(res.combined, w), cy, 15, p); }
        }
    }
}
