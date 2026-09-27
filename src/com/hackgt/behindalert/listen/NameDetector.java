package com.hackgt.behindalert.listen;

import android.content.res.AssetManager;
import android.os.SystemClock;
import com.k2fsa.sherpa.onnx.*;
import java.io.IOException;
import java.util.*;

public final class NameDetector implements DetectorWorker.Processor {
    public interface Listener { void event(SoundEvent event); }
    private final KeywordSpotter spotter;
    private OnlineStream stream;
    private final String keywords;
    private final Map<String, String> labels = new HashMap<>();
    private final EventFilter filter;
    private final Listener listener;
    private long origin = -1;
    public NameDetector(AssetManager assets, List<String> names, EventFilter filter, Listener listener) throws IOException {
        this.filter = filter; this.listener = listener;
        NameTokenizer tokenizer = new NameTokenizer(assets);
        List<String> encoded = new ArrayList<>();
        for (int i = 0; i < names.size(); i++) {
            String alias = "name" + i;
            labels.put(alias, names.get(i));
            encoded.add(tokenizer.tokens(names.get(i)) + " @" + alias);
        }
        keywords = String.join("/", encoded);
        OnlineTransducerModelConfig transducer = new OnlineTransducerModelConfig();
        String prefix = "models/kws/", suffix = "-epoch-12-avg-2-chunk-16-left-64.int8.onnx";
        transducer.setEncoder(prefix + "encoder" + suffix);
        transducer.setDecoder(prefix + "decoder" + suffix);
        transducer.setJoiner(prefix + "joiner" + suffix);
        OnlineModelConfig model = new OnlineModelConfig();
        model.setTransducer(transducer); model.setTokens(prefix + "tokens.txt");
        model.setModelType("zipformer2"); model.setProvider("cpu"); model.setNumThreads(2);
        KeywordSpotterConfig config = new KeywordSpotterConfig();
        config.setModelConfig(model); config.setKeywordsFile("models/empty-keywords.txt");
        config.setKeywordsScore(1.5f); config.setKeywordsThreshold(.30f);
        spotter = new KeywordSpotter(assets, config);
        try { reset(); } catch (RuntimeException e) { spotter.release(); throw e; }
    }
    @Override public void accept(AudioBlock block) {
        if (origin < 0) origin = block.startMs;
        stream.acceptWaveform(block.mono, 16000);
        while (spotter.isReady(stream)) {
            spotter.decode(stream);
            KeywordSpotterResult result = spotter.getResult(stream);
            String label = labels.get(result.getKeyword());
            if (label != null) {
                float[] timestamps = result.getTimestamps();
                long start = timestamps.length == 0 ? block.startMs : origin + Math.round(timestamps[0] * 1000);
                long end = timestamps.length == 0 ? block.endMs : origin + Math.round(timestamps[timestamps.length - 1] * 1000) + 40;
                SoundEvent event = filter.name(label, start, end, SystemClock.elapsedRealtime());
                if (event != null) listener.event(event);
                spotter.reset(stream);
            }
        }
    }
    @Override public void reset() {
        if (stream != null) stream.release();
        stream = spotter.createStream(keywords);
        if (stream.getPtr() == 0) throw new IllegalArgumentException("Could not configure these names");
        origin = -1;
    }
    @Override public void close() { if (stream != null) { stream.release(); stream = null; } spotter.release(); }
}
