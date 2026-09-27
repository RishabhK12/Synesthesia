package com.hackgt.behindalert.listen;

import android.content.Context;
import android.os.SystemClock;
import com.google.mediapipe.tasks.audio.audioclassifier.AudioClassifier;
import com.google.mediapipe.tasks.audio.audioclassifier.AudioClassifierResult;
import com.google.mediapipe.tasks.audio.core.RunningMode;
import com.google.mediapipe.tasks.core.BaseOptions;
import com.google.mediapipe.tasks.components.containers.AudioData;
import com.google.mediapipe.tasks.components.containers.Category;
import java.util.*;

public final class EnvironmentDetector implements DetectorWorker.Processor {
    public interface Listener { void event(SoundEvent event); void speech(boolean active); }
    private final Context context;
    private final EventFilter filter;
    private final Listener listener;
    private AudioClassifier classifier;
    private volatile long generation, lastResultMs = -1;
    private volatile RuntimeException failure;
    private long firstMs = -1;
    public EnvironmentDetector(Context context, EventFilter filter, Listener listener) {
        this.context = context; this.filter = filter; this.listener = listener; open();
    }
    private void open() {
        final long token = ++generation;
        classifier = AudioClassifier.createFromOptions(context, AudioClassifier.AudioClassifierOptions.builder()
                .setBaseOptions(BaseOptions.builder().setModelAssetPath("models/yamnet.tflite").build())
                .setRunningMode(RunningMode.AUDIO_STREAM)
                .setResultListener(result -> { if (generation == token) result(result); })
                .setErrorListener(error -> { if (generation == token) failure = error; })
                .build());
    }
    private void result(AudioClassifierResult result) {
        for (com.google.mediapipe.tasks.components.containers.ClassificationResult item : result.classificationResults()) {
            Map<SoundEvent.Kind, Float> scores = new EnumMap<>(SoundEvent.Kind.class);
            float speech = 0;
            for (com.google.mediapipe.tasks.components.containers.Classifications head : item.classifications()) {
                for (Category category : head.categories()) {
                    SoundEvent.Kind kind = EventFilter.category(category.categoryName());
                    if (kind != null) scores.put(kind, Math.max(scores.containsKey(kind) ? scores.get(kind) : 0, category.score()));
                    if (category.categoryName().equals("Speech")) speech = category.score();
                }
            }
            long start = result.timestampMs();
            lastResultMs = start + 975;
            listener.speech(speech >= .3f);
            for (SoundEvent event : filter.classify(scores, start, start + 975, SystemClock.elapsedRealtime())) listener.event(event);
        }
    }
    @Override public void accept(AudioBlock block) {
        if (failure != null) throw failure;
        if (firstMs < 0) firstMs = block.startMs;
        long processed = lastResultMs >= 0 ? lastResultMs : firstMs;
        // Bound MediaPipe's internal asynchronous backlog as well as our own input queue.
        if (block.endMs - processed > 2500) { reset(); firstMs = block.startMs; }
        AudioData data = AudioData.create(AudioData.AudioDataFormat.builder().setNumOfChannels(1).setSampleRate(16000).build(), block.mono.length);
        data.load(block.mono);
        classifier.classifyAsync(data, block.startMs);
    }
    @Override public void reset() { close(); filter.resetEnvironment(); firstMs = -1; lastResultMs = -1; failure = null; open(); }
    @Override public void close() { generation++; if (classifier != null) { classifier.close(); classifier = null; } }
}
