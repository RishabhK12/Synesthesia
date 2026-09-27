package com.hackgt.behindalert;

import org.junit.Test;
import org.junit.runner.RunWith;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;
import static org.junit.Assert.*;
import com.hackgt.behindalert.listen.*;
import java.io.*;
import java.nio.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** Executes bundled native runtimes on the device; no network or microphone required. */
@RunWith(AndroidJUnit4.class)
public class OfflineModelsTest {
    @Test public void testTokenizerUsesBundledVocabulary() throws Exception {
        NameTokenizer tokenizer = new NameTokenizer(getInstrumentation().getTargetContext().getAssets());
        assertEquals("▁HE LL O ▁WORLD", tokenizer.tokens("hello world"));
        assertFalse(tokenizer.tokens("Risha").isEmpty());
        assertFalse(tokenizer.tokens("Alex").isEmpty());
        try { tokenizer.tokens("Alex / #0.01"); fail("Keyword syntax injection accepted"); }
        catch (IllegalArgumentException expected) { }
    }
    @Test public void testKeywordModelRecognizesBundledFixtureAfterReset() throws Exception {
        List<SoundEvent> events = new ArrayList<>();
        try (NameDetector detector = new NameDetector(getInstrumentation().getTargetContext().getAssets(),
                Arrays.asList("light up"), new EventFilter(EnumSet.allOf(SoundEvent.Kind.class)), events::add)) {
            float[] audio = fixture("0.wav");
            feed(detector, audio, 10000);
            assertFalse("No LIGHT UP detected in the upstream keyword fixture", events.isEmpty());
            assertEquals("light up", events.get(0).name);
            assertTrue(events.get(0).audioStartMs >= 10000);
            detector.reset(); events.clear(); feed(detector, audio, 30000);
            assertFalse("Keyword model failed after reset", events.isEmpty());
            assertTrue(events.get(0).audioStartMs >= 30000);
        }
    }
    @Test public void testEnvironmentModelStreamsSilenceAndSpeech() throws Exception {
        CountDownLatch heardSpeech = new CountDownLatch(1);
        AtomicInteger silenceAlerts = new AtomicInteger(); AtomicBoolean silence = new AtomicBoolean(true);
        EnvironmentDetector detector = new EnvironmentDetector(getInstrumentation().getTargetContext(),
                new EventFilter(EnumSet.allOf(SoundEvent.Kind.class)), new EnvironmentDetector.Listener() {
                    public void event(SoundEvent event) { if (silence.get()) silenceAlerts.incrementAndGet(); }
                    public void speech(boolean active) { if (active && !silence.get()) heardSpeech.countDown(); }
                });
        try {
            for (int i = 0; i < 15; i++) { detector.accept(new AudioBlock(i * 100, new float[1600], new short[9600])); Thread.sleep(30); }
            Thread.sleep(300); assertEquals(0, silenceAlerts.get());
            silence.set(false); detector.reset();
            float[] samples = fixture("0.wav");
            for (int i = 0; i < samples.length; i += 1600) {
                detector.accept(new AudioBlock(10000 + i / 16, Arrays.copyOfRange(samples, i, Math.min(samples.length, i + 1600)), new short[9600]));
                Thread.sleep(30);
            }
            assertTrue("YAMNet did not report speech", heardSpeech.await(5, TimeUnit.SECONDS));
        } finally { detector.close(); }
    }
    private void feed(NameDetector detector, float[] samples, long start) {
        for (int i = 0; i < samples.length + 16000; i += 1600) {
            float[] chunk = new float[1600];
            if (i < samples.length) System.arraycopy(samples, i, chunk, 0, Math.min(1600, samples.length - i));
            detector.accept(new AudioBlock(start + i / 16, chunk, new short[9600]));
        }
    }
    private float[] fixture(String name) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (InputStream in = getInstrumentation().getContext().getAssets().open(name)) {
            byte[] buffer = new byte[8192]; int count; while ((count = in.read(buffer)) != -1) out.write(buffer, 0, count);
        }
        ByteBuffer bytes = ByteBuffer.wrap(out.toByteArray()).order(ByteOrder.LITTLE_ENDIAN);
        assertEquals(16000, bytes.getInt(24)); assertEquals(1, bytes.getShort(22));
        int offset = 12;
        while (bytes.getInt(offset) != 0x61746164) offset += 8 + bytes.getInt(offset + 4) + (bytes.getInt(offset + 4) & 1);
        int length = bytes.getInt(offset + 4) / 2; float[] samples = new float[length];
        for (int i = 0; i < length; i++) samples[i] = bytes.getShort(offset + 8 + 2 * i) / 32768f;
        return samples;
    }
}
