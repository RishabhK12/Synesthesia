package com.hackgt.behindalert.listen;

import android.content.Context;
import java.util.*;

/** One session; callbacks after close are suppressed, including native asynchronous results. */
public final class ListenEngine implements AutoCloseable {
    public interface Listener {
        void event(SoundEvent event);
        void status(String detector, String state);
        void speech(boolean present);
        void direction(String text);
        void microphoneFailed(String reason);
    }
    private volatile boolean closed;
    private final StereoCapture capture;
    private final List<DetectorWorker> workers = new ArrayList<>();
    private final DirectionMonitor direction;
    public ListenEngine(Context context, List<String> names, Set<SoundEvent.Kind> enabled, Listener listener) {
        capture = new StereoCapture(context);
        EventFilter filter = new EventFilter(enabled);
        EnvironmentDetector.Listener environmentListener = new EnvironmentDetector.Listener() {
            public void event(SoundEvent event) { if (!closed) listener.event(event); }
            public void speech(boolean present) { if (!closed) listener.speech(present); }
        };
        workers.add(new DetectorWorker("environment", () -> new EnvironmentDetector(context.getApplicationContext(), filter, environmentListener),
                state -> { if (!closed) listener.status("Sounds", state); }));
        if (!names.isEmpty() && enabled.contains(SoundEvent.Kind.NAME)) {
            workers.add(new DetectorWorker("names", () -> new NameDetector(context.getAssets(), names, filter,
                    event -> { if (!closed) listener.event(event); }), state -> { if (!closed) listener.status("Names", state); }));
        } else listener.status("Names", names.isEmpty() ? "Add a name to enable" : "Disabled");
        direction = new DirectionMonitor(context.getSharedPreferences("compass", Context.MODE_PRIVATE).getString("calibration", ""),
                text -> { if (!closed) listener.direction(text); });
        workers.add(new DetectorWorker("direction", () -> direction, state -> {
            if (!closed && state.startsWith("Unavailable")) listener.direction("Direction " + state);
        }));
        capture.start(new StereoCapture.Listener() {
            public void audio(AudioBlock block) { if (!closed) for (DetectorWorker worker : workers) worker.offer(block); }
            public void failed(String reason) { if (!closed) { close(); listener.microphoneFailed(reason); } }
        });
    }
    public void heading(double degrees, long ms) { direction.heading(degrees, ms); }
    @Override public void close() {
        if (closed) return;
        closed = true; capture.close();
        for (DetectorWorker worker : workers) worker.close();
    }
}
