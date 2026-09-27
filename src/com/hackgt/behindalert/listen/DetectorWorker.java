package com.hackgt.behindalert.listen;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;

/** A slow detector loses its own backlog, never blocking capture or the other detector. */
public final class DetectorWorker implements AutoCloseable {
    public interface Processor extends AutoCloseable {
        void accept(AudioBlock block) throws Exception;
        void reset() throws Exception;
        void close();
    }
    public interface Factory { Processor create() throws Exception; }
    public interface Status { void update(String message); }
    private static final class Queued {
        final AudioBlock block; final long generation;
        Queued(AudioBlock block, long generation) { this.block = block; this.generation = generation; }
    }
    private final ArrayBlockingQueue<Queued> queue = new ArrayBlockingQueue<>(5);
    private final Thread thread;
    private volatile boolean running = true, ready;
    private long generation;
    public DetectorWorker(String name, Factory factory, Status status) {
        thread = new Thread(() -> {
            try (Processor processor = factory.create()) {
                if (!running) return;
                ready = true; status.update("Ready");
                long seenGeneration = 0;
                while (running) {
                    Queued queued = queue.poll(100, TimeUnit.MILLISECONDS);
                    if (queued == null) continue;
                    long currentGeneration = queued.generation;
                    if (seenGeneration != currentGeneration) {
                        processor.reset(); seenGeneration = currentGeneration;
                        status.update("Audio gap — resumed");
                    }
                    if (running) processor.accept(queued.block);
                }
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            } catch (Exception | LinkageError e) {
                if (running) status.update("Unavailable: " + e.getMessage());
            } finally { ready = false; running = false; queue.clear(); }
        }, name);
        thread.start();
    }
    public void offer(AudioBlock block) {
        if (!running || !ready) return;
        synchronized (queue) {
            if (!queue.offer(new Queued(block, generation))) { queue.clear(); generation++; queue.offer(new Queued(block, generation)); }
        }
    }
    @Override public void close() { running = false; queue.clear(); thread.interrupt(); }
}
