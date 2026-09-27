// Match nearby BLE direction events with YAMNet detections on this phone.
// The ESP32 sends no audio or shared clock, so this is an approximate match.
export const MATCH_MS = 1800;
export const DIRECTION_WAIT_MS = 1900;
const HOLD_MATCH_MS = 2500;

export class SoundFusion {
  constructor({ onCue, onNote }) {
    this.onCue = onCue;
    this.onNote = onNote;
    this.directions = [];
    this.sounds = new Map();
    this.assigned = new Map();
  }

  reset() {
    this.directions.length = 0;
    this.sounds.clear();
    this.assigned.clear();
  }

  prune(now) {
    for (const [key, sound] of this.sounds) {
      if (now - sound.at > MATCH_MS) this.sounds.delete(key);
    }
    for (const [key, assignment] of this.assigned) {
      if (now - assignment.at > HOLD_MATCH_MS) this.assigned.delete(key);
    }
  }

  candidateSounds(now) {
    return [...this.sounds.values()].filter(sound => Math.abs(now - sound.at) <= MATCH_MS);
  }

  assign(sound, direction, now) {
    direction.used = true;
    this.assigned.set(sound.key, { angle_deg: direction.angle_deg, at: now,
      rear_triggered: direction.rear_triggered });
    this.onCue({ ...sound, angle_deg: direction.angle_deg,
      rear_triggered: direction.rear_triggered, replace: true });
  }

  receiveDirection(direction, now) {
    this.prune(now);
    if (direction.status === 'rear_possible') {
      this.onCue({ key: 'behind', label: 'Possible sound behind', angle_deg: null,
        possibleRear: true, fresh: true, loud: 0.5 });
      return;
    }
    if (direction.status !== 'ok') {
      this.onNote(direction.status === 'clipped'
        ? 'Sound detected, but the sensor clipped. Direction unknown.'
        : 'Sound detected, but its direction is unclear.');
      return;
    }
    const entry = { ...direction, at: now, used: false };
    this.directions.push(entry);
    const sounds = this.candidateSounds(now);
    if (sounds.length === 1) this.assign(sounds[0], entry, now);
    else if (sounds.length > 1) this.onNote('Several sounds detected together; direction cannot be assigned safely.');
  }

  receiveSounds(events, now) {
    this.prune(now);
    if (!events.length) return;
    // The name spotter usually returns after YAMNet has already called the
    // same voice "Talking". Reuse that voice's recent bearing only if no
    // other sound competes for it.
    if (events.length === 1 && events[0].key === 'name' && this.sounds.has('speech')) {
      const speech = this.sounds.get('speech');
      const bearing = this.assigned.get('speech');
      const competing = [...this.sounds.keys()].some(key => key !== 'speech');
      this.sounds.delete('speech');
      if (!competing && now - speech.at <= HOLD_MATCH_MS && bearing && now - bearing.at <= HOLD_MATCH_MS) {
        this.sounds.set('name', { ...events[0], at: now });
        this.assigned.set('name', { ...bearing, at: now });
        this.onCue({ ...events[0], angle_deg: bearing.angle_deg,
          rear_triggered: bearing.rear_triggered, replace: true });
        return;
      }
    }
    for (const sound of events) this.sounds.set(sound.key, { ...sound, at: now });
    const available = this.directions.filter(direction =>
      !direction.used && Math.abs(now - direction.at) <= MATCH_MS);
    const sounds = this.candidateSounds(now);
    if (events.length === 1 && sounds.length === 1 && available.length === 1) {
      this.assign(events[0], available[0], now);
      return;
    }
    for (const sound of events) {
      const assigned = this.assigned.get(sound.key);
      this.onCue({ ...sound, angle_deg: assigned?.angle_deg ?? null,
        rear_triggered: assigned?.rear_triggered ?? false, replace: false });
    }
    if (events.length > 1 && available.length) {
      this.onNote('Several sounds detected together; showing their types without a direction.');
    }
  }

  flush(now) {
    this.prune(now);
    const remaining = [];
    for (const direction of this.directions) {
      if (now - direction.at < DIRECTION_WAIT_MS) {
        remaining.push(direction);
      } else if (!direction.used) {
        this.onCue({ key: 'behind', label: 'Unclassified sound',
          angle_deg: direction.angle_deg, rear_triggered: direction.rear_triggered,
          fresh: true, loud: 0.5 });
      }
    }
    this.directions = remaining;
  }
}
