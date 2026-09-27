package com.hackgt.behindalert.listen;

import java.util.*;

/** Session-local event hysteresis. Thresholds are provisional until field evaluation. */
public final class EventFilter {
    public static final class Rule {
        public final float weak, strong;
        Rule(float weak, float strong) { this.weak = weak; this.strong = strong; }
    }
    public static Rule rule(SoundEvent.Kind kind) {
        switch (kind) {
            case HORN: return new Rule(.25f, .55f);
            case SIREN: return new Rule(.25f, .60f);
            case ALARM: return new Rule(.30f, .65f);
            case DOORBELL: return new Rule(.20f, .50f);
            case KNOCK: return new Rule(.20f, .50f);
            default: throw new IllegalArgumentException("Names use keyword detection");
        }
    }
    private static final class State { long id, start, end = -10000, lastCandidate = -10000; int hits; }
    private final EnumMap<SoundEvent.Kind, State> states = new EnumMap<>(SoundEvent.Kind.class);
    private final Map<String, Long> names = new HashMap<>();
    private final EnumSet<SoundEvent.Kind> enabled;
    private long nextId;
    public EventFilter(Set<SoundEvent.Kind> enabled) {
        this.enabled = EnumSet.noneOf(SoundEvent.Kind.class); this.enabled.addAll(enabled);
    }
    public synchronized List<SoundEvent> classify(Map<SoundEvent.Kind, Float> scores, long start, long end, long detected) {
        List<SoundEvent> events = new ArrayList<>();
        for (SoundEvent.Kind kind : SoundEvent.Kind.values()) {
            if (kind == SoundEvent.Kind.NAME || !enabled.contains(kind)) continue;
            State state = states.get(kind);
            if (state == null) { state = new State(); states.put(kind, state); }
            float score = scores.containsKey(kind) ? scores.get(kind) : 0;
            Rule rule = rule(kind);
            if (score < rule.weak) { state.hits = 0; continue; }
            state.hits = start - state.lastCandidate <= 1100 ? state.hits + 1 : 1;
            state.lastCandidate = start;
            if (score < rule.strong && state.hits < 2) continue;
            boolean fresh = start - state.end > 1500;
            if (fresh) { state.id = ++nextId; state.start = start; }
            state.end = end;
            events.add(new SoundEvent(state.id, kind, null, state.start, end, detected, score, fresh));
        }
        return events;
    }
    public synchronized SoundEvent name(String name, long start, long end, long detected) {
        if (!enabled.contains(SoundEvent.Kind.NAME)) return null;
        Long last = names.get(name);
        if (last != null && end - last < 2500) return null;
        names.put(name, end);
        return new SoundEvent(++nextId, SoundEvent.Kind.NAME, name, start, end, detected, null, true);
    }
    public synchronized void resetEnvironment() { states.clear(); }
    public static SoundEvent.Kind category(String label) {
        switch (label) {
            case "Vehicle horn, car horn, honking": case "Air horn, truck horn": case "Toot": return SoundEvent.Kind.HORN;
            case "Siren": case "Police car (siren)": case "Ambulance (siren)": case "Fire engine, fire truck (siren)": return SoundEvent.Kind.SIREN;
            case "Alarm": case "Car alarm": case "Smoke detector, smoke alarm": case "Fire alarm": case "Alarm clock": case "Buzzer": return SoundEvent.Kind.ALARM;
            case "Doorbell": case "Ding-dong": return SoundEvent.Kind.DOORBELL;
            case "Knock": return SoundEvent.Kind.KNOCK;
            default: return null;
        }
    }
}
