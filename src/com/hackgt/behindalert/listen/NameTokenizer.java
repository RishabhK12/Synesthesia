package com.hackgt.behindalert.listen;

import android.content.res.AssetManager;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class NameTokenizer {
    static { System.loadLibrary("name_tokenizer"); }
    private static native String[] encode(byte[] model, byte[] text);
    private final byte[] model;
    private final Set<String> vocabulary = new HashSet<>();
    public NameTokenizer(AssetManager assets) throws IOException {
        model = read(assets, "models/kws/bpe.model");
        String tokens = new String(read(assets, "models/kws/tokens.txt"), StandardCharsets.UTF_8);
        for (String line : tokens.split("\n")) {
            int space = line.lastIndexOf(' ');
            if (space > 0) vocabulary.add(line.substring(0, space));
        }
    }
    public String tokens(String name) {
        String normalized = normalize(name);
        String[] pieces = encode(model, normalized.getBytes(StandardCharsets.UTF_8));
        for (String piece : pieces) if (!vocabulary.contains(piece) || piece.equals("<unk>"))
            throw new IllegalArgumentException("Try an English pronunciation spelling for “" + name + "”.");
        return String.join(" ", pieces);
    }
    public static String normalize(String value) {
        String cleaned = java.text.Normalizer.normalize(value.trim(), java.text.Normalizer.Form.NFKC).replaceAll("\\s+", " ");
        if (cleaned.length() < 1 || cleaned.length() > 60 || !cleaned.matches("[A-Za-z][A-Za-z '\\-]*"))
            throw new IllegalArgumentException("Use 1–60 English letters, spaces, apostrophes or hyphens for each name or pronunciation spelling.");
        return cleaned.toUpperCase(Locale.ROOT);
    }
    static byte[] read(AssetManager assets, String path) throws IOException {
        try (InputStream in = assets.open(path); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[16384]; int n;
            while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
            return out.toByteArray();
        }
    }
}
