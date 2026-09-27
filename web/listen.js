// Hears sounds with this device's microphone and says what they are, so the halo can show them
// without any remote phone or network connection.
//
// A browser port of the listener in the Synesthesia Android app (github.com/RishabhK12/synesthesia,
// audio_classification branch): the same YAMNet model, run by the same MediaPipe Tasks Audio 0.10.21,
// the same label → sound mapping (now each sound's `hear` in common.js) and the same event filter.
// Direction comes separately from the ESP32 Bluetooth bridge.
//
// Everything runs on the device. About a second of audio is kept in memory and nothing is recorded or sent.
import { SOUNDS } from './common.js';

const WINDOW = 0.97;     // s of audio per look (YAMNet listens in 0.975 s windows)
const HOP = 0.5;         // s between looks, so a sound shows up about 1–1.5 s after it starts
const LIB = new URL('./vendor/mediapipe/', import.meta.url).href;
const MODEL = new URL('./vendor/models/yamnet.tflite', import.meta.url).href;

// YAMNet label → sound key, from each sound's `hear.labels` in common.js
const LABELS = new Map();
for (const [key, s] of Object.entries(SOUNDS)) for (const label of s.hear?.labels || []) LABELS.set(label, key);

const clamp = (x, a, b) => x < a ? a : x > b ? b : x;
const dbfs = (a) => { let s = 0; for (let i = 0; i < a.length; i++) s += a[i] * a[i]; return 10 * Math.log10(s / a.length + 1e-12); };

// Port of Synesthesia's EventFilter, so one stray window never raises an alert. A window scoring above a
// sound's `strong` threshold alerts at once; one above `weak` needs another within 1.1 s. Detections less
// than 1.5 s apart are the same event, so a long siren stays one wave instead of alerting over and over.
// `sens` scales every threshold (below 1 = more sensitive).
class EventFilter {
  constructor() { this.state = new Map(); }
  reset() { this.state.clear(); }
  classify(scores, start, end, sens) {
    const events = [];
    for (const [key, s] of Object.entries(SOUNDS)) {
      if (!s.hear) continue;
      let st = this.state.get(key);
      if (!st) this.state.set(key, st = { end: -1e9, last: -1e9, hits: 0 });
      const score = scores.get(key) || 0;
      if (score < s.hear.weak * sens) { st.hits = 0; continue; }
      st.hits = start - st.last <= 1100 ? st.hits + 1 : 1;
      st.last = start;
      if (score < s.hear.strong * sens && st.hits < 2) continue;
      const fresh = start - st.end > 1500;
      st.end = end;
      events.push({ key, score, fresh });
    }
    // a general sound gives way to a more specific one heard with it (see `yields` in common.js)
    const on = (k) => (this.state.get(k)?.end ?? -1e9) >= start - 1500;
    return events.filter(e => !SOUNDS[e.key].hear.yields?.some(on));
  }
}

export class Listener {
  // on.events([{ key, score, fresh, loud }]) per audio window · on.status(state, detail) with state one of
  // off | starting | on | paused | blocked | failed · on.frame({ top, db }) after every look (diagnostics).
  constructor(on = {}) {
    Object.assign(this, { on, state: 'off', live: 0, sens: 1, floor: null, run: null });
    this.filter = new EventFilter();
  }

  // Loads the bundled MediaPipe runtime and model. Call early so starting is quick.
  prepare() {
    this.model ??= (async () => {
      const { AudioClassifier, FilesetResolver } = await import(`${LIB}audio_bundle.mjs`);
      const files = await FilesetResolver.forAudioTasks(`${LIB}wasm`);
      return AudioClassifier.createFromOptions(files, { baseOptions: { modelAssetPath: MODEL }, maxResults: -1 });
    })();
    this.model.catch(() => { this.model = null; });   // let a later start try again
    return this.model;
  }

  async start() {
    if (this.run) return;
    const run = this.run = { stopped: false };
    this.status('starting');
    try {
      if (!navigator.mediaDevices?.getUserMedia) throw new Error('the microphone needs https (or localhost)');
      // raw-ish audio: echo cancelling and noise suppression can eat alarms and sirens
      const mic = navigator.mediaDevices.getUserMedia({ audio: { echoCancellation: false, noiseSuppression: false, autoGainControl: false } })
        .then((s) => { run.stream = s; if (run.dead) this.release(run); return s; }, (e) => { e.mic = true; throw e; });
      const [clf] = await Promise.all([this.prepare(), mic]);
      if (run.stopped) return this.release(run);
      run.clf = clf;
      const ctx = run.ctx = new AudioContext();       // the device's own rate; MediaPipe resamples to 16 kHz
      const src = ctx.createMediaStreamSource(run.stream);
      const tap = run.tap = ctx.createScriptProcessor(4096, 1, 1), mute = ctx.createGain();
      mute.gain.value = 0;                            // keeps the tap running without playing the mic back
      src.connect(tap); tap.connect(mute); mute.connect(ctx.destination);
      run.win = new Float32Array(Math.round(ctx.sampleRate * WINDOW));
      run.fill = 0; run.since = 0;
      tap.onaudioprocess = (e) => this.hear(run, e.inputBuffer.getChannelData(0));
      this.filter.reset(); this.floor = null;
      await ctx.resume().catch(() => {});
      if (!run.stopped) this.status(ctx.state === 'running' ? 'on' : 'paused');
    } catch (e) {
      run.dead = true; this.release(run);                // also stops a mic granted after the model failed
      if (this.run === run) this.run = null;
      if (!run.stopped) this.status(e.mic && e.name === 'NotAllowedError' ? 'blocked' : 'failed', e.message || String(e));
    }
  }

  // Some browsers only let audio start from a tap. Call this from any tap while paused.
  nudge() {
    const ctx = this.run?.ctx;
    if (ctx && ctx.state !== 'running') ctx.resume().then(() => { if (this.run?.ctx === ctx && ctx.state === 'running') this.status('on'); }).catch(() => {});
  }

  stop() {
    const run = this.run;
    if (!run) return;
    run.stopped = run.dead = true; this.run = null; this.live = 0;
    this.release(run);
    this.status('off');
  }

  release(run) {
    if (run.tap) { run.tap.onaudioprocess = null; run.tap.disconnect(); }
    run.stream?.getTracks().forEach(t => t.stop());
    run.ctx?.close().catch(() => {});
  }

  status(state, detail = '') { this.state = state; this.on.status?.(state, detail); }

  // Every audio block (~85 ms): track loudness, keep the last window, and look again every HOP seconds.
  hear(run, chunk) {
    if (run.stopped) return;
    const db = dbfs(chunk);
    if (this.floor == null || db < this.floor) this.floor = db; else this.floor += (db - this.floor) * 0.002;   // slow-rising background level
    const target = clamp((db - this.floor - 4) / 26, 0, 1);
    this.live += (target - this.live) * (target > this.live ? 0.6 : 0.2);

    const win = run.win, n = Math.min(chunk.length, win.length);
    win.copyWithin(0, n); win.set(chunk.subarray(chunk.length - n), win.length - n);
    run.fill = Math.min(win.length, run.fill + n); run.since += n;
    if (run.fill < win.length || run.since < HOP * run.ctx.sampleRate) return;
    run.since = 0;
    const samples = win.slice();
    setTimeout(() => this.look(run, samples), 0);   // classify after the audio callback returns
  }

  look(run, samples) {
    if (run.stopped) return;
    const db = dbfs(samples), end = performance.now();
    if (db < -80) return this.on.frame?.({ top: [], db });   // digital silence: nothing to hear
    let cats;
    try { cats = run.clf.classify(samples, run.ctx.sampleRate)[0]?.classifications[0]?.categories || []; }
    catch (e) { this.stop(); return this.status('failed', e.message); }
    const scores = new Map();
    for (const c of cats) {
      const key = LABELS.get(c.categoryName);
      if (key && c.score > (scores.get(key) || 0)) scores.set(key, c.score);
    }
    const loud = clamp((db - this.floor - 6) / 30, 0, 1);
    const events = this.filter.classify(scores, end - WINDOW * 1000, end, this.sens)
      .map(ev => ({ ...ev, loud }));
    if (events.length) this.on.events?.(events);
    if (this.on.frame) {
      const top = cats.slice().sort((a, b) => b.score - a.score).slice(0, 3).map(c => ({ name: c.categoryName, score: c.score, key: LABELS.get(c.categoryName) }));
      this.on.frame({ top, db });
    }
  }
}
