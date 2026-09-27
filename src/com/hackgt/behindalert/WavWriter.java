package com.hackgt.behindalert;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;

/** Saves the interleaved recorder output unchanged as little-endian stereo PCM16. */
public final class WavWriter implements Closeable {
    private final RandomAccessFile file;
    private final int sampleRate;
    private long bytes;
    private boolean closed;
    private byte[] buffer = new byte[0];

    public WavWriter(File path, int sampleRate) throws IOException {
        if (sampleRate <= 0) throw new IllegalArgumentException("sample rate");
        this.sampleRate = sampleRate;
        file = new RandomAccessFile(path, "rw");
        file.setLength(0);
        header();
    }

    public void write(short[] pcm, int count) throws IOException {
        if (closed) throw new IOException("Writer is closed");
        if (count < 0 || count > pcm.length || count % 2 != 0)
            throw new IllegalArgumentException("Complete stereo frames required");
        if (bytes + count * 2L > 0xffffffffL - 36) throw new IOException("WAV size limit");
        if (buffer.length < count * 2) buffer = new byte[count * 2];
        for (int i = 0; i < count; i++) {
            buffer[2 * i] = (byte) pcm[i];
            buffer[2 * i + 1] = (byte) (pcm[i] >> 8);
        }
        file.write(buffer, 0, count * 2);
        bytes += count * 2L;
    }

    public long frames() { return bytes / 4; }

    private void le(long value, int count) throws IOException {
        for (int i = 0; i < count; i++) file.write((int) (value >>> (8 * i)) & 255);
    }

    private void header() throws IOException {
        file.seek(0);
        file.writeBytes("RIFF"); le(36 + bytes, 4); file.writeBytes("WAVEfmt ");
        le(16, 4); le(1, 2); le(2, 2); le(sampleRate, 4); le(sampleRate * 4L, 4);
        le(4, 2); le(16, 2); file.writeBytes("data"); le(bytes, 4);
    }

    @Override public void close() throws IOException {
        if (closed) return;
        closed = true;
        try { header(); } finally { file.close(); }
    }
}
