import com.hackgt.behindalert.listen.*;
import org.junit.Test;
import static org.junit.Assert.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

public class ListenTest {
    @Test public void resamplerRejectsAliasesAndPreservesSpeechBand() {
        assertTrue(rms(tone(1000, false)) > .3);
        assertTrue(rms(tone(12000, false)) < .002);
        assertEquals(0, rms(tone(1000, true)), 0);
    }
    private float[] tone(int frequency, boolean rightOnly) {
        short[] pcm = new short[96000];
        for (int i = 0; i < 48000; i++) pcm[2 * i + (rightOnly ? 1 : 0)] = (short) (16384 * Math.sin(2 * Math.PI * frequency * i / 48000));
        return new MonoResampler().process(pcm);
    }
    private double rms(float[] samples) {
        assertEquals(16000, samples.length); double energy = 0;
        for (int i = 1000; i < samples.length; i++) energy += samples[i] * samples[i];
        return Math.sqrt(energy / (samples.length - 1000));
    }
    @Test public void resamplerIsContinuousAcrossArbitraryBlocks() {
        short[] samples = new short[12000]; Random random = new Random(17);
        for (int i = 0; i < samples.length; i++) samples[i] = (short) random.nextInt();
        float[] expected = new MonoResampler().process(samples);
        MonoResampler streaming = new MonoResampler(); List<Float> actual = new ArrayList<>();
        for (int i = 0; i < samples.length; i += 14) {
            for (float value : streaming.process(Arrays.copyOfRange(samples, i, Math.min(i + 14, samples.length)))) actual.add(value);
        }
        assertEquals(expected.length, actual.size());
        for (int i = 0; i < expected.length; i++) assertEquals(expected[i], actual.get(i), 0);
    }
    @Test public void briefStrongSoundsAndPersistentSounds() {
        EventFilter filter = all();
        Map<SoundEvent.Kind, Float> scores = Collections.singletonMap(SoundEvent.Kind.HORN, .8f);
        SoundEvent first = filter.classify(scores, 0, 975, 1000).get(0);
        assertTrue(first.newAlert);
        SoundEvent continuing = filter.classify(scores, 975, 1950, 2000).get(0);
        assertFalse(continuing.newAlert); assertEquals(first.id, continuing.id);
        SoundEvent later = filter.classify(scores, 4000, 4975, 5000).get(0);
        assertTrue(later.newAlert); assertNotEquals(first.id, later.id);
    }
    @Test public void weakEvidenceNeedsTwoAdjacentWindowsAndResetsOnGap() {
        EventFilter filter = all(); Map<SoundEvent.Kind, Float> score = Collections.singletonMap(SoundEvent.Kind.KNOCK, .3f);
        assertTrue(filter.classify(score, 0, 975, 1000).isEmpty());
        assertEquals(1, filter.classify(score, 975, 1950, 2000).size());
        filter.resetEnvironment();
        assertTrue(filter.classify(score, 3000, 3975, 4000).isEmpty());
        assertTrue(filter.classify(score, 6000, 6975, 7000).isEmpty());
    }
    @Test public void overlapAndDisabledCategories() {
        Map<SoundEvent.Kind, Float> scores = new EnumMap<>(SoundEvent.Kind.class);
        scores.put(SoundEvent.Kind.HORN, .9f); scores.put(SoundEvent.Kind.SIREN, .9f);
        assertEquals(2, all().classify(scores, 0, 975, 1000).size());
        EventFilter filter = new EventFilter(EnumSet.of(SoundEvent.Kind.HORN));
        assertEquals(1, filter.classify(scores, 0, 975, 1000).size());
        assertNull(filter.name("Alex", 0, 1000, 1200));
        assertNull(EventFilter.category("Music"));
    }
    @Test public void nameCooldown() {
        EventFilter filter = all();
        assertNotNull(filter.name("Alex", 0, 1000, 1200));
        assertNull(filter.name("Alex", 1000, 2000, 2200));
        assertNotNull(filter.name("Alex", 4000, 5000, 5200));
    }
    @Test public void slowWorkerDropsBacklogAndResetsWithoutBlockingProducer() throws Exception {
        CountDownLatch ready = new CountDownLatch(1), entered = new CountDownLatch(1), release = new CountDownLatch(1), done = new CountDownLatch(1);
        AtomicInteger resets = new AtomicInteger(); List<Long> received = Collections.synchronizedList(new ArrayList<>());
        DetectorWorker worker = new DetectorWorker("test", () -> new DetectorWorker.Processor() {
            public void accept(AudioBlock block) throws Exception {
                received.add(block.startMs);
                if (block.startMs == 0) { entered.countDown(); release.await(3, TimeUnit.SECONDS); }
                if (block.startMs == 600) done.countDown();
            }
            public void reset() { resets.incrementAndGet(); }
            public void close() { }
        }, state -> { if (state.equals("Ready")) ready.countDown(); });
        try {
            assertTrue(ready.await(3, TimeUnit.SECONDS)); worker.offer(block(0)); assertTrue(entered.await(3, TimeUnit.SECONDS));
            for (int i = 1; i <= 6; i++) worker.offer(block(i * 100));
            release.countDown(); assertTrue(done.await(3, TimeUnit.SECONDS));
            assertEquals(Arrays.asList(0L, 600L), received); assertEquals(1, resets.get());
        } finally { release.countDown(); worker.close(); }
    }
    private AudioBlock block(long time) { return new AudioBlock(time, new float[1600], new short[9600]); }
    private EventFilter all() { return new EventFilter(EnumSet.allOf(SoundEvent.Kind.class)); }
}
