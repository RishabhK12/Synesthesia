package com.hackgt.behindalert;

import android.Manifest;
import android.app.Activity;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.hardware.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import com.hackgt.behindalert.listen.*;
import java.util.*;

/** Foreground-only, offline awareness screen. Never persists microphone audio. */
public final class ListenActivity extends Activity implements SensorEventListener {
    private static final int BG = 0xff101821, PANEL = 0xff1b2835, FG = 0xfff2f7fc, MUTED = 0xffb4c4d3, ACCENT = 0xff83e0ce;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private SharedPreferences prefs;
    private ListenEngine engine;
    private long session;
    private boolean visible, testing, pendingTest;
    private int testHits;
    private long testDeadline;
    private Button start, test;
    private TextView microphone, soundStatus, nameStatus, speech, compass, history, testStatus, diagnostics;
    private LinearLayout active;
    private CheckBox vibrate;
    private final EditText[] nameInputs = new EditText[3];
    private final EnumMap<SoundEvent.Kind, CheckBox> categories = new EnumMap<>(SoundEvent.Kind.class);
    private final LinkedHashMap<Long, SoundEvent> events = new LinkedHashMap<>();
    private final LinkedHashMap<Long, SoundEvent> recent = new LinkedHashMap<>();
    private SensorManager sensors;
    private Sensor rotation;
    private final Runnable tick = new Runnable() {
        public void run() {
            if (!visible) return;
            long now = SystemClock.elapsedRealtime();
            if (engine != null && testing) {
                long seconds = Math.max(0, (testDeadline - now + 999) / 1000);
                testStatus.setText("Name test · " + testHits + " detections · " + seconds + " seconds left\nHave someone say your name several times.");
                if (seconds == 0) {
                    int count = testHits; stopListening();
                    testStatus.setText("Test finished: " + count + " detections. Try a nickname or pronunciation spelling if calls were missed.");
                }
            }
            boolean changed = events.values().removeIf(event -> now - event.detectedMs > (event.kind == SoundEvent.Kind.NAME ? 6000 : 3500));
            if (changed) renderEvents();
            if (!recent.isEmpty()) renderHistory();
            ui.postDelayed(this, 500);
        }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences("listen", MODE_PRIVATE);
        sensors = (SensorManager) getSystemService(SENSOR_SERVICE);
        rotation = sensors.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR);
        if (rotation == null) rotation = sensors.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true); scroll.setBackgroundColor(BG);
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(dp(20), dp(24), dp(20), dp(32));
        scroll.addView(root); setContentView(scroll);
        text(root, "SYNESTHESIA", 13, ACCENT);
        TextView title = text(root, "Listen", 34, FG); title.setTypeface(null, Typeface.BOLD);
        text(root, "See the sounds around you", 18, MUTED);
        microphone = text(root, "Microphone off · audio stays on this phone", 14, MUTED);
        start = button(root, "Start listening", () -> { if (engine == null) requestStart(false); else stopListening(); });

        LinearLayout live = card(root);
        text(live, "AROUND YOU", 13, ACCENT);
        active = new LinearLayout(this); active.setOrientation(LinearLayout.VERTICAL); live.addView(active);
        renderEvents();
        speech = text(live, "Speech: —", 14, MUTED);
        soundStatus = text(live, "Sounds: stopped", 13, MUTED);
        nameStatus = text(live, "Names: stopped", 13, MUTED);
        LinearLayout setup = card(root);
        text(setup, "Your name", 23, FG);
        text(setup, "Add a name and up to two nicknames. Use an English pronunciation spelling if needed.", 14, MUTED);
        String[] hints = {"Name", "Nickname or pronunciation (optional)", "Another nickname (optional)"};
        for (int i = 0; i < nameInputs.length; i++) {
            final int index = i;
            EditText input = new EditText(this); input.setSingleLine(true); input.setTextColor(FG); input.setHintTextColor(MUTED);
            input.setTextSize(18); input.setHint(hints[i]); input.setContentDescription(hints[i]);
            input.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(60)});
            input.setText(prefs.getString("name" + i, "")); setup.addView(input); nameInputs[i] = input;
            input.addTextChangedListener(new android.text.TextWatcher() {
                public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
                public void onTextChanged(CharSequence s, int start, int before, int count) { prefs.edit().putString("name" + index, s.toString()).apply(); }
                public void afterTextChanged(android.text.Editable editable) { }
            });
        }
        test = button(setup, "Test my name · 30 seconds", () -> requestStart(true));
        testStatus = text(setup, "The test detects a spoken name; it cannot tell whether someone meant to address you.", 14, MUTED);
        LinearLayout options = card(root);
        text(options, "Alerts", 23, FG);
        for (SoundEvent.Kind kind : SoundEvent.Kind.values()) {
            CheckBox check = new CheckBox(this); check.setText(kind == SoundEvent.Kind.NAME ? "My name" : new SoundEvent(0, kind, null, 0, 0, 0, null, false).label());
            check.setTextColor(FG); check.setTextSize(17); check.setMinHeight(dp(48)); check.setChecked(prefs.getBoolean(kind.name(), true));
            check.setOnCheckedChangeListener((view, on) -> prefs.edit().putBoolean(kind.name(), on).apply());
            options.addView(check); categories.put(kind, check);
        }
        vibrate = new CheckBox(this); vibrate.setText("Vibrate for alerts"); vibrate.setTextColor(FG); vibrate.setTextSize(17);
        vibrate.setChecked(prefs.getBoolean("vibrate", true)); vibrate.setMinHeight(dp(48)); options.addView(vibrate);
        vibrate.setOnCheckedChangeListener((view, on) -> prefs.edit().putBoolean("vibrate", on).apply());
        LinearLayout direction = card(root);
        text(direction, "Sound compass · experimental", 20, FG);
        compass = text(direction, "Direction appears here when calibrated.", 16, MUTED);
        text(direction, "Use the same landscape grip as calibration, with the screen upright. Bearing is relative to the rear camera and describes the dominant sound, separately from the alerts above.", 13, MUTED);
        button(direction, "Open compass calibration", () -> { stopListening(); startActivity(new Intent(this, CompassActivity.class)); });
        LinearLayout past = card(root); text(past, "Recent sounds", 23, FG); history = text(past, "Events from this listening session will appear here.", 16, MUTED);
        CheckBox detail = new CheckBox(this); detail.setText("Show detector diagnostics"); detail.setTextColor(MUTED); root.addView(detail);
        diagnostics = text(root, "No detections yet.", 13, MUTED); diagnostics.setVisibility(View.GONE);
        detail.setOnCheckedChangeListener((view, checked) -> diagnostics.setVisibility(checked ? View.VISIBLE : View.GONE));
        text(root, "Listening stops when you leave this screen. Live audio is not saved.", 13, MUTED);
    }

    private void requestStart(boolean nameTest) {
        if (engine != null) return;
        pendingTest = nameTest;
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 52);
        } else begin(nameTest);
    }
    @Override public void onRequestPermissionsResult(int code, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(code, permissions, results);
        if (code != 52) return;
        if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) {
            ui.post(() -> { if (visible) begin(pendingTest); });
        } else microphone.setText("Microphone permission denied. Enable it in Android Settings to listen.");
    }
    private void begin(boolean nameTest) {
        if (!visible || engine != null) return;
        List<String> names = new ArrayList<>(); Set<String> unique = new HashSet<>();
        try {
            for (EditText input : nameInputs) {
                String name = input.getText().toString().trim();
                if (!name.isEmpty() && unique.add(NameTokenizer.normalize(name))) names.add(name);
            }
        } catch (IllegalArgumentException | LinkageError e) { testStatus.setText(e.getMessage()); return; }
        if (nameTest && names.isEmpty()) { testStatus.setText("Enter your name before starting the test."); return; }
        Set<SoundEvent.Kind> enabled = EnumSet.noneOf(SoundEvent.Kind.class);
        for (Map.Entry<SoundEvent.Kind, CheckBox> entry : categories.entrySet()) if (entry.getValue().isChecked()) enabled.add(entry.getKey());
        if (nameTest) { enabled.clear(); enabled.add(SoundEvent.Kind.NAME); }
        events.clear(); recent.clear(); renderEvents(); history.setText("No events yet.");
        testing = nameTest; testHits = 0; testDeadline = SystemClock.elapsedRealtime() + 30000;
        long token = ++session;
        soundStatus.setText("Sounds: loading on-device model…"); nameStatus.setText("Names: loading on-device model…");
        microphone.setText("Listening · offline · no audio saved"); start.setText("Stop listening"); setSetupEnabled(false);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        engine = new ListenEngine(this, names, enabled, new ListenEngine.Listener() {
            private void post(Runnable action) { ui.post(() -> { if (visible && token == session && engine != null) action.run(); }); }
            public void event(SoundEvent event) { post(() -> show(event)); }
            public void status(String detector, String state) { post(() -> {
                (detector.equals("Sounds") ? soundStatus : nameStatus).setText(detector + ": " + state);
                if (testing && detector.equals("Names") && state.equals("Ready")) testDeadline = SystemClock.elapsedRealtime() + 30000;
                if (testing && detector.equals("Names") && state.startsWith("Unavailable")) {
                    stopListening(); testStatus.setText("Name test could not start. " + state);
                }
            }); }
            public void speech(boolean present) { post(() -> speech.setText(present ? "Speech nearby" : "Speech: —")); }
            public void direction(String text) { post(() -> compass.setText(text)); }
            public void microphoneFailed(String reason) { post(() -> { stopListening(); microphone.setText("Microphone stopped: " + reason); }); }
        });
        renderEvents();
        if (rotation != null) sensors.registerListener(this, rotation, SensorManager.SENSOR_DELAY_GAME);
    }
    private void setSetupEnabled(boolean enabled) {
        for (EditText input : nameInputs) input.setEnabled(enabled);
        for (CheckBox check : categories.values()) check.setEnabled(enabled);
        test.setEnabled(enabled);
    }
    private void stopListening() {
        session++;
        if (engine != null) { engine.close(); engine = null; }
        sensors.unregisterListener(this); testing = false;
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (start == null) return;
        start.setText("Start listening"); microphone.setText("Microphone off · audio stays on this phone");
        soundStatus.setText("Sounds: stopped"); nameStatus.setText("Names: stopped"); speech.setText("Speech: —");
        compass.setText("Direction: stopped"); events.clear(); renderEvents(); setSetupEnabled(true);
    }
    private void show(SoundEvent event) {
        events.put(event.id, event); recent.put(event.id, event);
        while (recent.size() > 30) recent.remove(recent.keySet().iterator().next());
        if (event.newAlert) {
            if (testing && event.kind == SoundEvent.Kind.NAME) testHits++;
            if (vibrate.isChecked()) {
                Vibrator vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
                long[] pattern = event.kind == SoundEvent.Kind.NAME ? new long[]{0, 150, 100, 150} : new long[]{0, 200};
                if (Build.VERSION.SDK_INT >= 26) vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1)); else vibrator.vibrate(pattern, -1);
            }
        }
        renderEvents();
        renderHistory();
        diagnostics.setText(String.format(Locale.US, "%s\nModel score: %s · audio-to-result: %d ms\nAudio: %d–%d ms · event %d",
                event.label(), event.score == null ? "not provided" : String.format(Locale.US, "%.3f", event.score),
                event.detectedMs - event.audioEndMs, event.audioStartMs, event.audioEndMs, event.id));
    }
    private void renderHistory() {
        List<SoundEvent> reverse = new ArrayList<>(recent.values()); Collections.reverse(reverse);
        StringBuilder lines = new StringBuilder();
        long now = SystemClock.elapsedRealtime();
        for (SoundEvent item : reverse) lines.append(item.label()).append(" · ").append(Math.max(0, (now - item.detectedMs) / 1000)).append("s ago\n");
        history.setText(lines.toString().trim());
    }
    private void renderEvents() {
        if (active == null) return;
        active.removeAllViews();
        if (events.isEmpty()) text(active, engine == null ? "Ready when you are" : "Listening for sounds…", 24, FG);
        for (SoundEvent event : events.values()) {
            String symbol;
            switch (event.kind) {
                case NAME: symbol = "●"; break;
                case HORN: case SIREN: case ALARM: symbol = "▲"; break;
                default: symbol = "◆";
            }
            TextView item = text(active, symbol + "  " + event.label(), 26, FG); item.setTypeface(null, Typeface.BOLD);
            item.setContentDescription(event.label());
        }
    }
    @Override protected void onResume() { super.onResume(); visible = true; ui.post(tick); }
    @Override protected void onPause() { visible = false; stopListening(); ui.removeCallbacks(tick); super.onPause(); }
    @Override protected void onDestroy() { stopListening(); ui.removeCallbacksAndMessages(null); super.onDestroy(); }
    @Override public void onAccuracyChanged(Sensor sensor, int accuracy) { }
    @Override public void onSensorChanged(SensorEvent event) {
        if (engine == null) return;
        float[] matrix = new float[9]; SensorManager.getRotationMatrixFromVector(matrix, event.values);
        double east = -matrix[2], north = -matrix[5];
        boolean landscape = getResources().getConfiguration().orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE;
        double heading = !landscape || Math.hypot(east, north) < .35 ? Double.NaN : Math.toDegrees(Math.atan2(east, north));
        engine.heading(heading, event.timestamp / 1000000);
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private TextView text(LinearLayout parent, String value, int size, int color) {
        TextView text = new TextView(this); text.setText(value); text.setTextSize(size); text.setTextColor(color);
        text.setPadding(0, dp(5), 0, dp(7)); parent.addView(text); return text;
    }
    private Button button(LinearLayout parent, String label, Runnable action) {
        Button button = new Button(this); button.setText(label); button.setAllCaps(false); button.setTextSize(17); button.setMinHeight(dp(52));
        parent.addView(button, new LinearLayout.LayoutParams(-1, -2)); button.setOnClickListener(view -> action.run()); return button;
    }
    private LinearLayout card(LinearLayout parent) {
        LinearLayout box = new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setPadding(dp(16), dp(12), dp(16), dp(14));
        GradientDrawable background = new GradientDrawable(); background.setColor(PANEL); background.setCornerRadius(dp(16)); box.setBackground(background);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2); params.topMargin = dp(14); parent.addView(box, params); return box;
    }
}
