package com.hackgt.behindalert.listen;

public final class SoundEvent {
    public enum Kind { HORN, SIREN, ALARM, DOORBELL, KNOCK, NAME }
    public final long id, audioStartMs, audioEndMs, detectedMs;
    public final Kind kind;
    public final String name;
    public final Float score;
    public final boolean newAlert;
    public SoundEvent(long id, Kind kind, String name, long start, long end, long detected, Float score, boolean newAlert) {
        this.id = id; this.kind = kind; this.name = name;
        this.audioStartMs = start; this.audioEndMs = end; this.detectedMs = detected;
        this.score = score; this.newAlert = newAlert;
    }
    public String label() {
        switch (kind) {
            case HORN: return "Horn";
            case SIREN: return "Siren";
            case ALARM: return "Alarm";
            case DOORBELL: return "Doorbell";
            case KNOCK: return "Knocking";
            default: return "Your name was heard: " + name;
        }
    }
}
