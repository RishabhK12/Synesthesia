import com.hackgt.behindalert.DiagnosticSignal;
import com.hackgt.behindalert.WavWriter;
import java.io.File;
import java.nio.file.Files;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Random;

public class DiagnosticTest {
    static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    public static void main(String[] args) throws Exception {
        File wav = File.createTempFile("microphone-test-", ".wav");
        try {
            short[] pcm = {Short.MIN_VALUE, Short.MAX_VALUE, 1000, -1000, 0, 0};
            try (WavWriter writer = new WavWriter(wav, 48000)) {
                writer.write(pcm, 4); writer.write(new short[]{0, 0}, 2);
                check(writer.frames() == 3, "Frame count");
                try { writer.write(pcm, 3); throw new AssertionError("Accepted partial frame"); }
                catch (IllegalArgumentException expected) { }
            }
            byte[] bytes = Files.readAllBytes(wav.toPath());
            ByteBuffer header = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
            check(bytes.length == 56 && header.getInt(4) == 48 && header.getInt(40) == 12, "Finalized WAV sizes");
            check(header.getShort(22) == 2 && header.getInt(24) == 48000 && header.getShort(34) == 16, "Stereo format");
            for (int i = 0; i < pcm.length; i++) check(header.getShort(44 + 2 * i) == pcm[i], "PCM changed at " + i);
            try (WavWriter empty = new WavWriter(wav, 48000)) { }
            check(Files.size(wav.toPath()) == 44, "Empty recording header");
        } finally { Files.deleteIfExists(wav.toPath()); }

        short[] mono = new short[4096], scaled = new short[4096], independent = new short[4096];
        Random random = new Random(12);
        for (int i = 0; i < mono.length; i += 2) {
            short s = (short)(random.nextInt(20000) - 10000);
            mono[i] = mono[i + 1] = s; scaled[i] = s; scaled[i + 1] = (short)(s / 2);
            independent[i] = s; independent[i + 1] = (short)(random.nextInt(20000) - 10000);
        }
        DiagnosticSignal.Stats m = DiagnosticSignal.measure(mono, mono.length);
        check(m.identicalFraction == 1 && m.correlation > 0.99999, "Duplicate mono detection");
        DiagnosticSignal.Stats s = DiagnosticSignal.measure(scaled, scaled.length);
        check(s.identicalFraction < .01 && s.correlation > .99999, "Scaled mono must still be flagged");
        check(s.observation().contains("scaled copies"), "Scaled copy observation");
        DiagnosticSignal.Stats n = DiagnosticSignal.measure(independent, independent.length);
        check(Math.abs(n.correlation) < .1, "Independent signal correlation");
        check(n.observation().contains("does not prove"), "No false raw-mic claim");
        DiagnosticSignal.Stats quiet = DiagnosticSignal.measure(new short[20], 20);
        check(Double.isNaN(quiet.correlation) && quiet.observation().contains("Very quiet"), "Silence is not microphone proof");
        DiagnosticSignal.Stats clipped = DiagnosticSignal.measure(new short[]{32767, -32768}, 2);
        check(clipped.clippedFraction == 1, "Clipping check");
        System.out.println("DiagnosticTest: WAV integrity, partial-frame rejection, silence, duplicate/scaled channels and clipping passed.");
    }
}
