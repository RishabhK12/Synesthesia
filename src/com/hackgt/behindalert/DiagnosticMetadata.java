package com.hackgt.behindalert;

import android.media.AudioDeviceInfo;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioRecordingConfiguration;
import android.media.AudioTimestamp;
import android.media.MicrophoneInfo;
import android.media.audiofx.AudioEffect;
import android.os.Build;
import android.os.SystemClock;
import android.util.Pair;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.List;

/** Best-effort public Android metadata. Missing vendor data is explicitly unknown. */
final class DiagnosticMetadata {
    static JSONObject device(AudioManager manager) throws JSONException {
        JSONObject j = new JSONObject();
        j.put("manufacturer", Build.MANUFACTURER).put("model", Build.MODEL)
                .put("device", Build.DEVICE).put("android_release", Build.VERSION.RELEASE)
                .put("sdk", Build.VERSION.SDK_INT).put("build_fingerprint", Build.FINGERPRINT);
        String raw = manager.getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED);
        j.put("unprocessed_support_property", raw == null ? JSONObject.NULL : raw);
        j.put("audio_mode", manager.getMode());
        JSONArray inputs = new JSONArray();
        for (AudioDeviceInfo d : manager.getDevices(AudioManager.GET_DEVICES_INPUTS)) inputs.put(input(d));
        j.put("input_devices", inputs);
        if (Build.VERSION.SDK_INT >= 28) {
            try { j.put("microphone_inventory", microphones(manager.getMicrophones())); }
            catch (Exception e) { j.put("microphone_inventory_error", e.toString()); }
        } else j.put("microphone_inventory_error", "Requires Android 9 / API 28");
        j.put("interpretation", "Inventory is not proof of independently accessible channels. Empty effect lists do not rule out vendor processing.");
        return j;
    }

    static JSONObject snapshot(AudioRecord recorder) throws JSONException {
        JSONObject j = new JSONObject();
        j.put("routed_device", input(recorder.getRoutedDevice()));
        j.put("client_format", format(recorder.getFormat()));
        j.put("source", recorder.getAudioSource()).put("session_id", recorder.getAudioSessionId());
        if (Build.VERSION.SDK_INT >= 28) {
            try { j.put("active_microphones", microphones(recorder.getActiveMicrophones())); }
            catch (Exception e) { j.put("active_microphones_error", e.toString()); }
        } else j.put("active_microphones_error", "Requires Android 9 / API 28");
        if (Build.VERSION.SDK_INT >= 29) {
            try {
                AudioRecordingConfiguration c = recorder.getActiveRecordingConfiguration();
                if (c == null) j.put("recording_configuration", JSONObject.NULL);
                else {
                    JSONObject config = new JSONObject();
                    config.put("client_silenced", c.isClientSilenced());
                    config.put("device_format", format(c.getFormat()));
                    config.put("client_format", format(c.getClientFormat()));
                    config.put("actual_source", c.getAudioSource());
                    config.put("effects", effects(c.getEffects()));
                    config.put("client_effects", effects(c.getClientEffects()));
                    j.put("recording_configuration", config);
                }
            } catch (Exception e) { j.put("recording_configuration_error", e.toString()); }
        }
        return j;
    }

    static JSONObject timestamp(AudioRecord recorder) throws JSONException {
        AudioTimestamp t = new AudioTimestamp();
        int status = recorder.getTimestamp(t, AudioTimestamp.TIMEBASE_BOOTTIME);
        JSONObject j = new JSONObject().put("status", status).put("queried_at_boottime_ns", SystemClock.elapsedRealtimeNanos());
        if (status == AudioRecord.SUCCESS) j.put("frame_position", t.framePosition).put("boottime_ns", t.nanoTime);
        return j;
    }

    private static JSONObject input(AudioDeviceInfo d) throws JSONException {
        if (d == null) return new JSONObject().put("status", "unknown");
        JSONObject j = new JSONObject().put("id", d.getId()).put("type", d.getType())
                .put("name", d.getProductName().toString()).put("built_in", d.getType() == AudioDeviceInfo.TYPE_BUILTIN_MIC);
        j.put("channel_counts", integers(d.getChannelCounts()));
        j.put("sample_rates", integers(d.getSampleRates()));
        return j;
    }

    private static JSONArray integers(int[] values) {
        JSONArray a = new JSONArray(); for (int v : values) a.put(v); return a;
    }

    private static JSONObject format(AudioFormat f) throws JSONException {
        return new JSONObject().put("sample_rate", f.getSampleRate()).put("channel_count", f.getChannelCount())
                .put("channel_mask", f.getChannelMask()).put("channel_index_mask", f.getChannelIndexMask()).put("encoding", f.getEncoding());
    }

    private static JSONArray effects(List<AudioEffect.Descriptor> effects) throws JSONException {
        JSONArray a = new JSONArray();
        for (AudioEffect.Descriptor e : effects) a.put(new JSONObject().put("name", e.name)
                .put("implementor", e.implementor).put("type", e.type.toString()).put("uuid", e.uuid.toString()));
        return a;
    }

    @android.annotation.TargetApi(28)
    private static JSONArray microphones(List<MicrophoneInfo> microphones) throws JSONException {
        JSONArray a = new JSONArray();
        for (MicrophoneInfo m : microphones) {
            JSONObject j = new JSONObject().put("id", m.getId()).put("description", m.getDescription())
                    .put("type", m.getType()).put("location", m.getLocation()).put("group", m.getGroup())
                    .put("index_in_group", m.getIndexInTheGroup()).put("directionality", m.getDirectionality());
            j.put("position_m", coordinates(m.getPosition(), MicrophoneInfo.POSITION_UNKNOWN));
            j.put("orientation", coordinates(m.getOrientation(), MicrophoneInfo.ORIENTATION_UNKNOWN));
            j.put("sensitivity_dbfs", m.getSensitivity() == MicrophoneInfo.SENSITIVITY_UNKNOWN ? JSONObject.NULL : m.getSensitivity());
            JSONArray mapping = new JSONArray();
            for (Pair<Integer, Integer> p : m.getChannelMapping()) mapping.put(new JSONObject().put("channel_index", p.first)
                    .put("mapping", p.second == MicrophoneInfo.CHANNEL_MAPPING_DIRECT ? "DIRECT" :
                            p.second == MicrophoneInfo.CHANNEL_MAPPING_PROCESSED ? "PROCESSED" : "UNKNOWN")
                    .put("mapping_value", p.second));
            j.put("channel_mapping", mapping); a.put(j);
        }
        return a;
    }

    @android.annotation.TargetApi(28)
    private static Object coordinates(MicrophoneInfo.Coordinate3F p, MicrophoneInfo.Coordinate3F unknown) throws JSONException {
        if (p == null || (p.x == unknown.x && p.y == unknown.y && p.z == unknown.z)) return JSONObject.NULL;
        return new JSONObject().put("x", p.x).put("y", p.y).put("z", p.z);
    }

    static JSONObject stats(DiagnosticSignal.Stats s) throws JSONException {
        return new JSONObject().put("ch0_dbfs", s.db0).put("ch1_dbfs", s.db1)
                .put("zero_lag_correlation", Double.isNaN(s.correlation) ? JSONObject.NULL : s.correlation)
                .put("difference_db", s.differenceDb).put("identical_fraction", s.identicalFraction)
                .put("clipped_sample_fraction", s.clippedFraction).put("observation", s.observation());
    }
}
