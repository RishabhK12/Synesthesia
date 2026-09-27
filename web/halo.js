// The display's visual language. Each sound is a living wave that grows out of the edge of the
// view, only on the stretch of edge that faces the sound.
//
// The edge is a ring around you:
//
//      top = in front of you (a sound you can see sits right above it)
//        ┌──────────────────────────────┐
//   left │                              │ right
//        └──────────────────────────────┘
//                bottom = behind you
//
// The corners are the diagonals, so a sound ahead-right lives in the top-right corner.
//
// How a sound looks comes from its `form` in common.js:
//   size   0–1  loudness → how tall and wide the wave is (an alert's `level` overrides it)
//   sharp  0–1  round bumps → pointed spikes
//   grain  0–1  solid → dotted spray
//   freq   0–1  a few broad swells (low pitch) → many tight ripples (high pitch)
//   flow   0–1  separate bumps → one continuous band
//   blob   0–1  size of the bump that marks the exact direction
//   beat        rhythm it breathes with, drawn from the sound itself (see BEATS): the alarm's three beeps,
//               the siren's wail, a knock's three raps, a dog's woof-woof, the tick of a clock…
//   bars        a voice: a row of bars either side of the marker, like a voice waveform (with a beat, the
//               bars follow it: a shout's bursts, a song's long notes)
//   hazard      bigger surge on arrival
//   omni        no single source (ambient): a soft band around the whole edge, fuller on its side
// A sound's `icon` (icons.js) sits upright inside the marker, which grows into a dome big enough to hold it.
//
// A sound that was heard but not located (the microphone listener knows what, not where) has no side to
// grow from: its ripples circle the whole edge instead, and a chip above the bottom edge names it.
import { wrap } from './common.js';
import { icon } from './icons.js';

const D2R = Math.PI / 180;
const FADE = 900;                     // ms a wave takes to sink back into the edge
const AMAX = 130;                     // px, tallest a wave gets on a big screen

const clamp = (x, a, b) => x < a ? a : x > b ? b : x;
const lerp = (a, b, t) => a + (b - a) * t;
const sstep = (a, b, x) => { const t = clamp((x - a) / (b - a), 0, 1); return t * t * (3 - 2 * t); };
const backOut = (x, c) => { x -= 1; return 1 + (c + 1) * x * x * x + c * x * x; };
const hash = (n) => { const s = Math.sin(n * 127.1 + 311.7) * 43758.5453; return s - Math.floor(s); };
const noise = (x, seed = 0) => { const i = Math.floor(x), f = x - i, u = f * f * (3 - 2 * f); return lerp(hash(i + seed * 57.31), hash(i + 1 + seed * 57.31), u); };
// max() with a rounded crease, so bumps melt into each other instead of meeting at a corner
const smax = (a, b, r) => { const d = Math.max(r - Math.abs(a - b), 0); return Math.max(a, b) + d * d / (4 * r); };
const rgb = (c) => {
  const m = /^#([\da-f]{3}|[\da-f]{6})$/i.exec(c || '');
  if (!m) return [166, 107, 255];
  const h = m[1].length === 3 ? m[1].replace(/./g, '$&$&') : m[1], n = parseInt(h, 16);
  return [n >> 16 & 255, n >> 8 & 255, n & 255];
};
const rgba = (c, a) => `rgba(${c[0]},${c[1]},${c[2]},${a < 0 ? 0 : a > 1 ? 1 : a.toFixed(3)})`;

// Rhythms: loudness over time, roughly 0.4–1.2. t = seconds since the sound arrived.
const BEATS = {
  breathe: (t) => 0.9 + 0.1 * Math.sin(t * 2.1),
  alarm: (t) => {                     // three beeps, then a pause (the fire-alarm pattern)
    const c = t % 3.6; let b = 0;
    for (const s of [0, 0.9, 1.8]) b = Math.max(b, sstep(s, s + 0.05, c) * (1 - sstep(s + 0.45, s + 0.6, c)));
    return 0.5 + 0.62 * b;
  },
  wail: (t) => 0.86 + 0.14 * Math.sin(t * 2.6),
  honk: (t) => { const c = t % 3.2; return 0.5 + 0.65 * Math.max(sstep(0, 0.04, c) * (1 - sstep(0.3, 0.4, c)), sstep(0.5, 0.54, c) * (1 - sstep(1.1, 1.3, c))); },
  blast: (t) => { const c = t % 4; return 0.5 + 0.7 * sstep(0, 0.06, c) * (1 - sstep(1.5, 1.8, c)); },          // one long air-horn blast
  ring: (t) => {                      // bike bell: two quick trills
    const c = t % 2.4, on = Math.max(sstep(0, 0.02, c) * (1 - sstep(0.35, 0.45, c)), sstep(0.55, 0.57, c) * (1 - sstep(0.9, 1, c)));
    return 0.45 + 0.7 * on * (0.85 + 0.15 * Math.sin(t * 55));
  },
  rumble: (t, s) => 0.8 + 0.2 * noise(t * 1.6, s),
  chime: (t) => { const c = t % 2.8; return 0.42 + 0.7 * sstep(0, 0.03, c) * Math.max(Math.exp(-c * 2.6), c > 0.55 ? 0.85 * Math.exp(-(c - 0.55) * 2.6) : 0); },
  crackle: (t, s) => 0.6 + 0.4 * noise(t * 6, s),
  speech: (t, s) => noise(t * 4.6, s) * sstep(0.28, 0.5, noise(t * 0.6, s + 7)),   // syllables, with pauses between phrases
  // one-offs and patterns: `hits` makes sharp raps that die away, at these times (s) in a cycle this long
  knock: hits(2.6, [0, 0.24, 0.48], 14),
  hit: (t, s) => { const c = t % 5; return 0.45 + 0.8 * Math.exp(-c * 2.2) + 0.12 * noise(t * 9, s) * Math.exp(-c); },   // an impact, then debris
  shatter: (t, s) => { const c = t % 4; return 0.45 + 0.75 * Math.exp(-c * 5) + 0.3 * noise(t * 16, s) * Math.exp(-c * 1.1); },   // a crack, then tinkling
  scream: (t) => { const c = t % 3; return 0.5 + 0.7 * sstep(0, 0.12, c) * (1 - sstep(1.5, 2, c)) * (0.9 + 0.1 * Math.sin(t * 38)); },
  beep: (t) => { const c = t % 1.2; return 0.45 + 0.7 * sstep(0, 0.02, c) * (1 - sstep(0.5, 0.52, c)); },               // reversing: slow and even
  beeps: hits(2.4, [0, 0.25, 0.5, 0.75], 9),                                                                               // a timer: four quick beeps
  squeal: (t, s) => { const c = t % 4; return 0.5 + 0.7 * sstep(0, 0.05, c) * (1 - sstep(1.2, 1.8, c)) * (0.85 + 0.15 * noise(t * 20, s)); },
  clack: hits(1.1, [0, 0.18], 10, 0.7, 0.35),                                                                               // a train's da-dum on the rails
  engine: (t) => { const c = t % 5, x = (c - 2) / 0.9; return 0.55 + 0.6 * Math.exp(-x * x); },                            // a car passing
  rev: (t) => { const c = t % 2.2; return 0.6 + 0.55 * sstep(0, 0.3, c) * (1 - sstep(0.5, 1.4, c)) + 0.05 * Math.sin(t * 40); },
  cry: (t) => { const c = t % 1.8; return 0.5 + 0.65 * sstep(0, 0.2, c) * (1 - sstep(1, 1.4, c)) * (0.9 + 0.1 * Math.sin(t * 12)); },   // waah… waah…
  shout: (t, s) => { const c = t % 2.5; return 0.45 + 0.75 * sstep(0, 0.08, c) * (1 - sstep(0.6, 0.9, c)) * (0.8 + 0.2 * noise(t * 7, s)); },
  laugh: (t) => { const c = t % 2.2; return 0.5 + 0.6 * (1 - sstep(1.2, 1.5, c)) * Math.abs(Math.sin(t * Math.PI * 5)) ** 1.5; },   // ha-ha-ha
  clap: (t, s) => 0.75 + 0.3 * noise(t * 10, s),
  cheer: (t, s) => { const c = t % 6; return 0.6 + 0.45 * sstep(0, 1.2, c) * (1 - sstep(3.5, 5.5, c)) + 0.1 * noise(t * 5, s); },
  sing: (t) => 0.8 + 0.25 * Math.sin(t * 2.8) + 0.05 * Math.sin(t * 34),                                                  // long notes, with vibrato
  whistle: (t) => { const c = t % 0.9; return 0.7 + 0.35 * sstep(0, 0.05, c) * (1 - sstep(0.6, 0.85, c)); },
  cough: hits(3, [0, 0.35], 7),
  steps: hits(0.55, [0], 12, 0.5, 0.6),
  kids: (t, s) => 0.6 + 0.5 * noise(t * 5, s) * sstep(0.2, 0.5, noise(t * 0.8, s + 3)),
  ringring: (t) => { const c = t % 3, on = Math.max(sstep(0, 0.02, c) * (1 - sstep(0.4, 0.42, c)), sstep(0.6, 0.62, c) * (1 - sstep(1, 1.02, c))); return 0.45 + 0.7 * on * (0.85 + 0.15 * Math.sin(t * 80)); },   // ring-ring… ring-ring
  creak: (t) => { const c = t % 4; return 0.45 + 0.4 * sstep(0, 0.4, c) * (1 - sstep(1.1, 1.4, c)) + (c > 1.5 ? 0.75 * Math.exp(-(c - 1.5) * 6) : 0); },   // creaks open, bangs shut
  boil: (t, s) => 0.6 + 0.3 * sstep(0, 4, t % 8) + 0.2 * noise(t * 10, s),
  flow: (t, s) => 0.85 + 0.1 * Math.sin(t * 1.7) + 0.08 * noise(t * 3, s),
  flush: (t, s) => { const c = t % 6; return 0.5 + 0.7 * sstep(0, 0.3, c) * (1 - sstep(2, 4, c)) * (0.85 + 0.15 * noise(t * 8, s)); },
  clink: hits(1.7, [0, 0.4, 0.55, 1.1], 12),
  jingle: (t, s) => 0.6 + 0.5 * noise(t * 16, s) * (1 - sstep(1.2, 1.6, t % 3)),
  type: (t, s) => 0.55 + 0.5 * noise(t * 14, s) ** 2,
  patter: (t, s) => 0.8 + 0.25 * noise(t * 9, s),
  swell: (t) => { const c = t % 6.5; return 0.55 + 0.6 * sstep(0, 2.2, c) * (1 - sstep(2.8, 5.5, c)); },                 // a wave rolls in and draws back
  gust: (t, s) => 0.6 + 0.55 * noise(t * 0.7, s) ** 1.5,
  chirp: hits(1.6, [0, 0.15, 0.3], 18),
  pulse: (t) => { const c = t % 1.2; return 0.5 + 0.55 * (1 - sstep(0.45, 0.55, c)) * (0.6 + 0.4 * Math.abs(Math.sin(t * Math.PI * 28))); },   // a cricket's trill
  bark: hits(2.6, [0, 0.35], 9),
  meow: (t) => { const c = t % 3.5; return 0.45 + 0.7 * sstep(0, 0.15, c) * (1 - sstep(0.6, 1, c)); },
  melody: (t) => 0.7 + 0.3 * Math.exp(-(t % 0.5) * 6) + 0.1 * Math.sin(t * 0.9),                                           // on the beat
  thunder: (t, s) => { const c = t % 8; return 0.5 + 0.7 * Math.exp(-c * 3) + 0.35 * sstep(0.2, 0.8, c) * (1 - sstep(2, 5, c)) * noise(t * 3, s); },
  drone: (t) => 0.65 + 0.45 * Math.sin(Math.PI * (t % 10) / 10) ** 2,                                                    // a plane overhead
  hum: (t) => 0.92 + 0.06 * Math.sin(t * 1.3),
  slosh: (t) => 0.75 + 0.3 * Math.max(0, Math.sin(t * 1.9)),
  snore: (t) => { const c = t % 4.2; return 0.45 + 0.7 * sstep(0, 1.2, c) * (1 - sstep(1.6, 2, c)) + 0.15 * sstep(2.2, 2.6, c) * (1 - sstep(3.2, 3.8, c)); },
  tick: hits(2, [0, 1], 16, 0.6, 0.45),                                                                                    // tick… tock…
};
// Raps at `at` seconds into each `cycle`-second loop, each dying away at `decay`/s, from `base` up by `amp`.
function hits(cycle, at, decay, base = 0.45, amp = 0.75) {
  return (t) => { const c = t % cycle; let b = 0; for (const s of at) if (c >= s) b = Math.max(b, Math.exp(-(c - s) * decay)); return base + amp * b; };
}

// The screen edge as a loop, measured in px along the edge.
// u = 0 is the top centre (in front), positive u runs right and down the right side, ±half is the bottom
// centre (behind). It's laid out bottom-first below and mirrored top-to-bottom at the end of at().
// Distances are measured along a rounded-corner path so the direction waves grow in turns smoothly
// round each corner (towards the corner's centre, so tall waves never fold over themselves), but every
// point is pushed out onto the real screen edge, so waves still rise from the very corner of the screen.
// In a corner the normal is stretched by however far it was pushed, so a wave keeps its shape there.
class Rim {
  constructor(W, H, R) {
    const a = W / 2 - R, q = R * Math.PI / 2, c = H - 2 * R;
    Object.assign(this, { W, H, R, a, ends: [a, a + q, a + q + c, a + 2 * q + c], half: 2 * a + 2 * q + c });
    this.corner = a + q / 2;          // top-right corner (ahead-right)
    this.side = a + q + c / 2;        // middle of the right edge (right)
    this.back = a + 1.5 * q + c;      // bottom-right corner (behind-right)
  }
  wrapU(u) { const L = 2 * this.half; return ((u + this.half) % L + L) % L - this.half; }

  // Point on the screen edge at u, plus the normal pointing into the screen (1 px of wave height).
  at(u, p) {
    const { W, H, R, a, ends } = this;
    u = this.wrapU(u);
    const flip = u < 0; u = Math.abs(u);
    const arc = (cx, cy, th) => { const c = Math.cos(th), s = Math.sin(th), k = 1 / Math.max(Math.abs(c), Math.abs(s)); p.x = cx + R * k * c; p.y = cy + R * k * s; p.nx = -c * k; p.ny = -s * k; };
    if (u <= ends[0]) { p.x = W / 2 + u; p.y = H; p.nx = 0; p.ny = -1; }
    else if (u <= ends[1]) arc(W - R, H - R, Math.PI / 2 - (u - a) / R);
    else if (u <= ends[2]) { p.x = W; p.y = H - R - (u - ends[1]); p.nx = -1; p.ny = 0; }
    else if (u <= ends[3]) arc(W - R, R, -(u - ends[2]) / R);
    else { p.x = W - R - (u - ends[3]); p.y = 0; p.nx = 0; p.ny = 1; }
    if (flip) { p.x = W - p.x; p.nx = -p.nx; }
    p.y = H - p.y; p.ny = -p.ny;      // in front at the top, behind at the bottom
    return p;
  }

  // Direction (degrees, 0 = where you look, + = right) → place on the edge.
  // Inside the field of view it sits right above the sound, so it lines up with what you see;
  // outside, it runs through the corner, up the side and round to the top as the sound goes behind.
  toU(rel, fov) {
    const r = Math.abs(rel), h = clamp(fov / 2, 10, 80), { W, a } = this;
    let u;
    if (r <= h) { const x = (W / 2) * Math.tan(r * D2R) / Math.tan(h * D2R); u = x <= a ? x : a + this.R * (Math.PI / 2 - Math.atan2(this.R, x - a)); }
    else if (r <= 90) u = lerp(this.corner, this.side, (r - h) / (90 - h));
    else if (r <= 135) u = lerp(this.side, this.back, (r - 90) / 45);
    else u = lerp(this.back, this.half, (r - 135) / 45);
    return rel < 0 ? -u : u;
  }
}

export class Halo {
  constructor(canvas, size, dpr) {
    this.voices = []; this.last = 0; this.p = { x: 0, y: 0, nx: 0, ny: 0 };
    this.buf = { x: new Float32Array(0) };
    this.use(canvas, size, dpr);
  }

  // Draw onto `canvas`. Pass `size` ([w, h]) for a fixed-size canvas (the Quest texture, the forms page);
  // otherwise it fills the window.
  use(canvas, size = null, dpr = 1) { this.cv = canvas; this.g = canvas.getContext('2d'); this.size = size; this.fixedDpr = dpr; this.W = 0; }

  // a: { key, color, core, icon, label, form, yaw, ttl, level, alert, kick, live }. yaw = world direction in
  // degrees, or null for a sound heard but not located. live() (optional) is the sound's loudness right now,
  // 0–1; the wave follows it instead of its usual rhythm. kick: false refreshes a wave without a pulse.
  // The same sound again from about the same direction keeps the wave alive instead of stacking a second one.
  add(a, now = performance.now()) {
    const f = a.form || {}, ttl = a.ttl || 6000, around = a.yaw == null, size = clamp(+(a.level ?? f.size ?? 0.6) || 0.6, 0, 1);
    const same = this.voices.find(v => v.key === a.key && v.end - now > FADE && v.around === around
      && (around || f.omni || Math.abs(wrap(v.yaw - a.yaw)) < 30));
    if (same) {
      Object.assign(same, { yaw: a.yaw, end: now + ttl, fadeLen: FADE, size });
      if (a.kick !== false) same.kick = now;
      if (a.alert) same.sweep = now;
      return same;
    }
    const v = {
      key: a.key, form: f, yaw: a.yaw, around, size, sz: size, label: a.label || a.key, live: a.live || null,
      c1: rgb(a.color), c2: rgb(a.core || a.color), icon: icon(a.icon), born: now, end: now + ttl, fadeLen: FADE,
      kick: 0, sweep: a.alert ? now : 0, seed: Math.random() * 100, u: null, vel: 0, dots: [], acc: 0,
    };
    this.voices.push(v);
    return v;
  }

  // Every sound sinks away, or only those with this key.
  clear(now = performance.now(), key = null) { for (const v of this.voices) if (v.end > now + 450 && (key == null || v.key === key)) { v.end = now + 450; v.fadeLen = 450; } }

  fit() {
    const [W, H] = this.size || [innerWidth, innerHeight];
    const dpr = this.size ? this.fixedDpr : Math.min(devicePixelRatio || 1, 2);
    if (W === this.W && H === this.H && dpr === this.dpr) return;
    Object.assign(this, { W, H, dpr, U: Math.min(W, H) });
    this.cv.width = Math.round(W * dpr); this.cv.height = Math.round(H * dpr);
    // corner radius ≥ the tallest wave, so waves bend round a corner without folding over themselves
    this.rim = new Rim(W, H, Math.min(clamp(this.U * 0.2, 20, AMAX) * 1.35, this.U / 2 - 1));
  }

  // heading = where you're looking (same degrees as yaw); fov = horizontal field of view on screen.
  // project(v) (optional) says where on screen a sound came from, { x, y } in px, or null when that's not in
  // view; the display knows the camera. Then a beacon marks the spot, as well as the wave on the edge.
  draw(now, heading = 0, fov = 60, project = null) {
    this.fit();
    const { g, dpr, W, H } = this, dt = this.last ? clamp((now - this.last) / 1000, 0, 0.05) : 0;
    this.last = now;
    g.setTransform(dpr, 0, 0, dpr, 0, 0); g.clearRect(0, 0, W, H);
    this.voices = this.voices.filter(v => now < v.end);
    this.voices.sort((a, b) => !!b.form.omni - !!a.form.omni);   // ambient underneath
    for (const v of this.voices) this.step(v, now, dt, heading, fov);
    const heard = this.voices.filter(v => v.around);
    if (heard.length) this.vignette(heard);
    for (const v of this.voices) if (!v.form.omni && !v.around) this.scrim(v);
    if (project) for (const v of this.voices) {
      if (v.form.omni || v.around || v.k.alpha < 0.01) continue;
      const pt = project(v);
      if (pt) this.beacon(v, pt);
    }
    g.globalCompositeOperation = 'screen';                         // overlapping sounds mix their colours
    for (const v of this.voices) {
      if (v.form.omni) { this.omni(v); continue; }
      if (v.form.bars) this.speech(v);
      else { this.body(v, -v.k.spread, v.k.spread); this.grain(v, now, dt); }
      if (v.icon && !v.around) this.badge(v);
    }
    for (const v of this.voices) if (v.sweep) this.sweep(v, now);
    g.globalCompositeOperation = 'source-over'; g.shadowBlur = 0;
    if (heard.length) this.captions(heard);
  }

  // Everything about a voice for this frame: where it sits, how big, how far through its life.
  step(v, now, dt, heading, fov) {
    const { rim, U } = this, f = v.form, age = Math.max(0, now - v.born), t = age / 1000;
    const rise = backOut(Math.min(1, age / 650), f.hazard ? 2.4 : 1.5);
    const fall = sstep(0, v.fadeLen, v.end - now);
    const kick = v.kick ? 0.3 * Math.exp(-(now - v.kick) / 280) : 0;
    const level = v.live ? 0.4 + 0.85 * v.live() : f.bars ? 1 : (BEATS[f.beat] || BEATS.breathe)(t, v.seed);
    const life = rise * fall;
    v.sz += (v.size - v.sz) * (1 - Math.exp(-dt * 3));             // a new loudness eases in

    let off = 0, stretch = 0;
    if (v.around) v.u = 0;                                           // heard, not located: centred, drawn all round
    else {
      const rel = wrap(v.yaw - heading), target = rim.toU(rel, fov);
      if (v.u == null) v.u = target;
      const d = rim.wrapU(target - v.u) * (1 - Math.exp(-dt * 12));   // glide, don't jump, when you turn
      v.u = rim.wrapU(v.u + d); v.vel = dt ? d / dt : 0;
      stretch = Math.min(0.5, Math.abs(v.vel) / 2600);              // smears a little when moving fast
      off = sstep(fov / 2 - 2, fov / 2 + 14, Math.abs(rel));        // off-screen: flattens against the side
    }

    const A0 = clamp(U * (0.08 + 0.12 * v.sz), 20, AMAX) * life * (v.around ? (f.hazard ? 0.55 : 0.4) : 1), A = A0 * (level + kick);
    const rd = v.icon && !v.around ? A0 * (f.hazard ? 0.72 : 0.6) : 0;   // smallest dome that holds the icon (bigger when it matters)
    const spread = v.around ? rim.half : clamp(U * (0.16 + 0.22 * v.sz), 60, 300) * (1 + stretch) * (0.75 + 0.25 * Math.min(1, life));
    let lam = clamp(U * (0.19 - 0.145 * (f.freq ?? 0.5)), 9, 110);
    if (f.beat === 'wail') lam *= 1 + 0.3 * Math.sin(t * 2.6 + 1);   // the siren's pitch sweeping up and down
    const blob = v.around ? 0 : f.blob ?? 0.8, sharp = f.sharp || 0;
    v.k = {
      t, A, spread, lam, sharp, level, life, off, flow: f.flow || 0,
      speed: (0.25 + 0.6 * (f.freq ?? 0.5)) * (f.hazard ? 1.3 : 1),
      alpha: Math.min(1, age / 180) * Math.sqrt(fall),
      bh: Math.max(A * blob * (1 + 0.25 * sharp) * (1 - 0.3 * off), rd),
      bw: Math.max(A * blob * (0.85 - 0.35 * sharp) * (1 + 0.7 * off), rd),
      bs: v.icon ? Math.min(sharp, 0.2) : sharp,                       // an icon needs a dome, not a spike
      rd,
    };
  }

  // Wave height (px, into the screen) at s px along the edge from the voice's centre.
  h(v, s) {
    if (v.around) return Math.min(this.ring(v, s), this.rim.R * 0.97);
    const k = v.k, x = s / k.spread;
    if (!(x > -1 && x < 1)) return 0;
    let h = 0;
    if (!v.form.bars) {
      // ripples that travel outward from the source; each bump has its own slow wobble
      const p = Math.abs(s) / k.lam - k.t * k.speed, i = Math.floor(p), fr = p - i;
      const round = Math.sin(Math.PI * fr) ** 0.55, tri = 1 - Math.abs(2 * fr - 1);
      const b = round + (tri * tri * Math.sqrt(tri) - round) * k.sharp;
      const wob = 0.7 + 0.3 * Math.sin(i * 2.39 + k.t * (1.1 + hash(i + v.seed)) + v.seed);
      h = k.A * 0.62 * (1 - x * x) ** 1.3 * (k.flow + (1 - k.flow) * b * wob);
    }
    const m = this.dome(v, s);
    if (m > 0) h = smax(h, m, k.A * 0.12 + 0.01);
    return Math.min(h, this.rim.R * 0.97);
  }

  // A sound heard but not located: the same ripples, all the way round the edge, circling slowly.
  // (A whole number of ripples fits the loop, so it has no seam.)
  ring(v, u) {
    const k = v.k, half = this.rim.half, n = Math.max(8, Math.round(2 * half / k.lam)), lam = 2 * half / n;
    const p = (u + half) / lam + k.t * k.speed * 0.5, i = Math.floor(p), fr = p - i, j = ((i % n) + n) % n;
    const round = Math.sin(Math.PI * fr) ** 0.55, tri = 1 - Math.abs(2 * fr - 1);
    const b = round + (tri * tri * Math.sqrt(tri) - round) * k.sharp;
    const wob = 0.7 + 0.3 * Math.sin(j * 2.39 + k.t * (1.1 + hash(j + v.seed)) + v.seed);
    const swell = 0.75 + 0.25 * Math.sin(2 * Math.PI * u / half + k.t * 0.8 + v.seed);   // a slow swell going round
    return k.A * 0.62 * swell * (0.15 + 0.5 * k.flow + 0.85 * (1 - k.flow) * b * wob);
  }

  // The marker that points at the sound: a dome when soft, a spike when sharp.
  dome(v, s) {
    const k = v.k, y = Math.abs(s) / k.bw;
    if (!(y < 1)) return 0;
    const round = Math.sqrt(1 - y * y), spike = (1 - y) ** 1.6;
    return Math.min(k.bh * (round + (spike - round) * k.bs), this.rim.R * 0.97);
  }

  // Heard sounds have no side, so the whole edge darkens a little behind their ring instead.
  vignette(list) {
    const { g, W, H, U } = this, a = Math.max(...list.map(v => v.k.alpha));
    const gr = g.createRadialGradient(W / 2, H / 2, U * 0.35, W / 2, H / 2, Math.hypot(W, H) / 2);
    gr.addColorStop(0, 'rgba(0,0,0,0)'); gr.addColorStop(1, rgba([0, 0, 0], 0.35 * a));
    g.fillStyle = gr; g.fillRect(0, 0, W, H);
  }

  // A dark, faintly tinted shadow behind a wave so it reads over a bright camera image.
  scrim(v) {
    const { g, p } = this, k = v.k, r = k.spread * 0.9 + k.A;
    if (r < 1) return;
    this.rim.at(v.u, p);
    const gr = g.createRadialGradient(p.x, p.y, 0, p.x, p.y, r);
    gr.addColorStop(0, rgba(v.c1.map(c => c * 0.12 | 0), 0.3 * k.alpha)); gr.addColorStop(1, 'rgba(0,0,0,0)');
    g.fillStyle = gr; g.fillRect(p.x - r, p.y - r, 2 * r, 2 * r);
  }

  // The solid shape between s = from and s = to: an echo of its outline that swells outward and
  // fades (the sound travelling), then the glowing core.
  body(v, from, to) {
    const { g, rim, p, U, W, H, dpr } = this, k = v.k;
    if (k.alpha < 0.004 || k.A < 0.5) return;
    const ds = clamp(k.lam / 12, 1.25, 3), n = Math.ceil((to - from) / ds) + 1;
    if (this.buf.x.length < n) for (const key of ['x', 'y', 'nx', 'ny', 'h', 'e']) this.buf[key] = new Float32Array(n * 2);
    const { x: X, y: Y, nx: NX, ny: NY, h: Hs, e: E } = this.buf;
    for (let i = 0; i < n; i++) {
      const s = Math.min(to, from + i * ds), q = s / k.spread;
      rim.at(v.u + s, p); X[i] = p.x; Y[i] = p.y; NX[i] = p.nx; NY[i] = p.ny;
      Hs[i] = this.h(v, s); E[i] = v.around ? 1 : q > -1 && q < 1 ? (1 - q * q) ** 2 : 0;
    }
    const out = 8;                     // the base runs just outside the screen, so the wave rises out of the edge
    const trace = (mul, add, closed) => {
      g.beginPath();
      for (let i = 0; i < n; i++) { const h = Math.min(Hs[i] * mul + add * E[i], rim.R * 0.99); g.lineTo(X[i] + NX[i] * h, Y[i] + NY[i] * h); }
      if (closed) { for (let i = n - 1; i >= 0; i--) g.lineTo(X[i] - NX[i] * out, Y[i] - NY[i] * out); g.closePath(); }
    };
    const solid = 1 - 0.86 * (v.form.grain || 0), grown = Math.min(1, k.life);
    const e = (k.t * (0.45 + 0.5 * k.speed) + v.seed) % 1;
    trace(1 + 0.4 * e, U * 0.06 * e * grown, false);
    g.lineWidth = 1.5; g.strokeStyle = rgba(v.c2, 0.45 * (1 - e) ** 2 * k.alpha * (0.3 + 0.7 * solid)); g.stroke();

    trace(1, 0, true);
    rim.at(v.u, p);
    const gr = v.around ? g.createRadialGradient(W / 2, H / 2, U * 0.42, W / 2, H / 2, Math.hypot(W, H) / 2)   // all round: hot on the sides nearest the middle
      : g.createRadialGradient(p.x, p.y, 0, p.x, p.y, Math.max(k.spread, k.bh, 1));
    gr.addColorStop(0, rgba(v.c2, k.alpha * solid)); gr.addColorStop(0.28, rgba(v.c1, k.alpha * solid)); gr.addColorStop(1, rgba(v.c1, 0.88 * k.alpha * solid));
    g.shadowColor = rgba(v.c1, 0.6 * k.alpha); g.shadowBlur = clamp(k.A * 0.3, 6, 28) * dpr;
    g.fillStyle = gr; g.fill();
    g.shadowBlur = 0;
  }

  // Dotted texture: sparks that pop in over the wave, drift away from the edge and fade.
  // Crackle spawns them in little clusters.
  grain(v, now, dt) {
    const k = v.k, f = v.form, gr = f.grain || 0, crackle = f.beat === 'crackle', { g, rim, p, U } = this;
    if (gr > 0.02 && now < v.end - v.fadeLen) {
      v.acc += dt * gr * (crackle ? 34 : 60) * k.level;
      while (v.acc >= 1) {
        v.acc -= 1;
        let s0 = 0;
        for (let j = 0; j < 6; j++) { s0 = (Math.random() * 2 - 1) * k.spread; const x = s0 / k.spread; if (v.around || Math.random() < (1 - x * x) ** 2 + 0.05) break; }
        const m = crackle ? 3 + (Math.random() * 6 | 0) : 1;
        for (let j = 0; j < m; j++) v.dots.push({
          s: s0 + (crackle ? (Math.random() - 0.5) * U * 0.09 : 0), f: Math.random() ** 0.7 * (crackle ? 2.2 : 1.3), drift: Math.random(), hot: Math.random() < 0.5,
          r: (0.8 + Math.random() * (crackle ? 3.2 : 2.2)) * clamp(U / 400, 0.8, 1.4), born: now, life: crackle ? 180 + Math.random() * 420 : 300 + Math.random() * 600,
        });
      }
    }
    v.dots = v.dots.filter(d => now - d.born < d.life);
    for (const d of v.dots) {
      const q = (now - d.born) / d.life, h = this.h(v, d.s) * d.f + U * (0.012 + 0.06 * d.drift * q);
      rim.at(v.u + d.s, p);
      g.fillStyle = rgba(d.hot ? v.c2 : v.c1, k.alpha * Math.sin(Math.PI * q) ** 0.6);
      g.beginPath(); g.arc(p.x + p.nx * h, p.y + p.ny * h, d.r * (crackle ? 1 + 0.7 * (1 - q) ** 3 : 1), 0, 7); g.fill();
    }
  }

  // The icon, upright in the marker dome. Dotted sounds get a solid dome first so the icon has
  // something to sit on. The icon is a dark cut of the sound's own colour, so it reads on any dome.
  badge(v) {
    const { g, rim, p } = this, k = v.k, size = k.rd * 0.92;
    if (k.alpha < 0.01 || size < 4) return;
    const grain = v.form.grain || 0;
    if (grain > 0.1) {
      g.beginPath();
      for (let s = -k.bw; s <= k.bw; s += 1.5) { const h = this.dome(v, s); rim.at(v.u + s, p); g.lineTo(p.x + p.nx * h, p.y + p.ny * h); }
      for (const s of [k.bw, -k.bw]) { rim.at(v.u + s, p); g.lineTo(p.x - p.nx * 8, p.y - p.ny * 8); }
      g.closePath(); g.fillStyle = rgba(v.c1, 0.9 * grain * k.alpha); g.fill();
    }
    rim.at(v.u, p);
    const up = Math.max(k.bh * 0.46, size * 0.55), cx = p.x + p.nx * up, cy = p.y + p.ny * up;
    g.globalCompositeOperation = 'source-over';
    g.save(); g.translate(cx - size / 2, cy - size / 2); g.scale(size / 24, size / 24);
    g.fillStyle = rgba(v.c1.map(c => c * 0.14 | 0), 0.92 * k.alpha); g.fill(v.icon.path, v.icon.rule);
    g.restore();
    g.globalCompositeOperation = 'screen';
  }

  // Conversation: the marker dome swells with the voice, and bars either side flicker like a waveform
  // while someone is talking, dropping to dots in the pauses.
  // Heard but not located: bars all the way round, a voice waveform wrapped round the frame.
  // With a live loudness the bars move with the actual voice.
  speech(v) {
    const { g, rim, p, U, W, H, dpr } = this, k = v.k, beat = BEATS[v.form.beat];
    const E = v.live ? v.live() : beat ? clamp((beat(k.t, v.seed) - 0.4) / 0.8, 0, 1) : BEATS.speech(k.t, v.seed);
    const gap = clamp(U * 0.026, 7, 15), w = gap * 0.5, base = w / 2 + 2, bars = [];
    const lv = (i, side) => 0.1 + 0.9 * E * (0.3 + 0.7 * noise(k.t * 8.5 + i * 1.93, v.seed + i * 3 + (side < 0 ? 40 : 0)));
    if (v.around) {
      const n = Math.floor(2 * rim.half / gap);
      for (let i = 0; i < n; i++) bars.push([-rim.half + (i + 0.5) * 2 * rim.half / n, k.A * 0.7 * lv(i, 1)]);
    } else {
      const r = k.A * (0.55 + 0.35 * E) * (v.form.blob ?? 0.8);
      k.bh = Math.max(r * (1 - 0.3 * k.off), k.rd); k.bw = Math.max(r * 0.95 * (1 + 0.6 * k.off), k.rd);
      this.body(v, -k.bw, k.bw);
      for (let i = 1; ; i++) {
        const s = k.bw + gap * (i - 0.2);
        if (s > k.spread) break;
        const x = s / k.spread, env = (1 - x * x) ** 1.5;
        for (const side of [1, -1]) bars.push([side * s, k.A * 0.8 * env * lv(i, side)]);
      }
    }
    g.beginPath();
    for (const [s, l] of bars) {
      const len = Math.max(0.5, l);
      rim.at(v.u + s, p);
      g.moveTo(p.x + p.nx * base, p.y + p.ny * base); g.lineTo(p.x + p.nx * (base + len), p.y + p.ny * (base + len));
    }
    rim.at(v.u, p);
    const gr = v.around ? g.createRadialGradient(W / 2, H / 2, U * 0.42, W / 2, H / 2, Math.hypot(W, H) / 2)
      : g.createRadialGradient(p.x, p.y, 0, p.x, p.y, Math.max(k.spread, 1));
    gr.addColorStop(0, rgba(v.c2, k.alpha)); gr.addColorStop(0.35, rgba(v.c1, k.alpha)); gr.addColorStop(1, rgba(v.c1, 0.8 * k.alpha));
    g.lineWidth = w; g.lineCap = 'round'; g.strokeStyle = gr;
    g.shadowColor = rgba(v.c1, 0.6 * k.alpha); g.shadowBlur = 8 * dpr;
    g.stroke(); g.shadowBlur = 0;
  }

  // Ambient: a soft band all the way round, lumpy and slowly shifting, fuller on the side it comes from.
  omni(v) {
    const { g, rim, p, W, H, dpr } = this, k = v.k, half = rim.half, n = Math.ceil(half / 2);
    if (k.alpha < 0.004) return;
    g.beginPath(); g.rect(-80, -80, W + 160, H + 160);
    for (let i = 0; i <= n; i++) {
      const u = -half + (2 * half * i) / n, phi = (u / half) * Math.PI;
      const near = v.around ? 0.5 : 0.5 + 0.5 * Math.cos((rim.wrapU(u - v.u) / half) * Math.PI);
      const lump = clamp(0.45 + 0.3 * Math.sin(3 * phi + k.t * 0.7 + v.seed) + 0.22 * Math.sin(5 * phi - k.t * 0.9 + v.seed * 2) + 0.14 * Math.sin(9 * phi + k.t * 1.3), 0.06, 1.2) ** 1.4;
      const h = Math.min(k.A * (0.06 + 0.5 * near ** 1.5) * lump + 2, rim.R * 0.97);
      rim.at(u, p);
      if (i) g.lineTo(p.x + p.nx * h, p.y + p.ny * h); else g.moveTo(p.x + p.nx * h, p.y + p.ny * h);
    }
    g.closePath();
    g.fillStyle = rgba(v.c1, 0.28 * k.alpha); g.shadowColor = rgba(v.c2, 0.4 * k.alpha); g.shadowBlur = 16 * dpr;
    g.fill('evenodd'); g.shadowBlur = 0;
  }

  // Attention: a light runs both ways round the edge from the sound, and the whole rim flashes its colour once.
  sweep(v, now) {
    const q = (now - v.sweep) / 1200;
    if (q >= 1) { v.sweep = 0; return; }
    const { g, rim, p } = this, e = 1 - (1 - q) ** 3, fade = (1 - q) ** 1.3 * v.k.alpha, step = 12, inset = 3;
    g.lineCap = 'round';
    if (q < 0.45) {
      g.beginPath();
      for (let u = -rim.half; u <= rim.half; u += 24) { rim.at(u, p); g.lineTo(p.x + p.nx * inset, p.y + p.ny * inset); }
      g.closePath(); g.lineWidth = 10; g.strokeStyle = rgba(v.c1, (v.around ? 0.5 : 0.3) * (1 - q / 0.45) * v.k.alpha); g.stroke();
    }
    if (v.around) return;                                            // nowhere to run from: the flash is the signal
    for (const side of [1, -1]) {
      const head = v.u + side * e * rim.half;
      for (let j = 0; j < 12; j++) {
        g.beginPath();
        rim.at(head - side * j * step, p); g.moveTo(p.x + p.nx * inset, p.y + p.ny * inset);
        rim.at(head - side * (j + 1) * step, p); g.lineTo(p.x + p.nx * inset, p.y + p.ny * inset);
        g.lineWidth = 6 - j * 0.4; g.strokeStyle = rgba(j < 2 ? v.c2 : v.c1, fade * (1 - j / 12)); g.stroke();
      }
    }
  }

  // Where the sound came from, while that's in view: a soft glow in its colours (soft because the place is
  // approximate), its ripples spreading out in its own form (spiky or round, dotted or solid; a voice's bars
  // around it), its icon in a dome at the heart and its name below. It fades near the edge of the screen,
  // where the wave takes over.
  beacon(v, pt) {
    const { g, W, H, U, dpr } = this, k = v.k, f = v.form;
    const a = k.alpha * sstep(0, U * 0.14, Math.min(pt.x, W - pt.x, pt.y, H - pt.y));
    const pop = clamp(k.life, 0, 1.25);
    if (a < 0.01 || pop < 0.02) return;
    const beat = f.bars ? clamp((BEATS[f.beat] || ((t, s) => 0.4 + 0.8 * BEATS.speech(t, s)))(k.t, v.seed), 0.4, 1.2) : clamp(k.level, 0.4, 1.3);
    const r = clamp(U * 0.036, 13, 22) * pop, R = r * (2.6 + 0.8 * beat);
    const { x, y } = pt;
    g.globalCompositeOperation = 'screen';
    let gr = g.createRadialGradient(x, y, 0, x, y, R);
    gr.addColorStop(0, rgba(v.c2, 0.42 * a)); gr.addColorStop(0.3, rgba(v.c1, 0.24 * a)); gr.addColorStop(1, rgba(v.c1, 0));
    g.fillStyle = gr; g.fillRect(x - R, y - R, 2 * R, 2 * R);

    g.lineCap = 'round';
    if (f.bars) {                                                    // a voice: a ring of bars that talk
      const n = 18, E = clamp((beat - 0.4) / 0.8, 0, 1);
      g.beginPath();
      for (let i = 0; i < n; i++) {
        const th = i / n * 2 * Math.PI, l = r * (0.25 + 1.1 * E * (0.3 + 0.7 * noise(k.t * 8.5 + i * 1.93, v.seed + i * 3)));
        const r0 = r * 1.35;
        g.moveTo(x + Math.cos(th) * r0, y + Math.sin(th) * r0); g.lineTo(x + Math.cos(th) * (r0 + l), y + Math.sin(th) * (r0 + l));
      }
      g.lineWidth = Math.max(2, r * 0.18); g.strokeStyle = rgba(v.c1, 0.9 * a); g.stroke();
    } else {                                                         // ripples leaving the source
      const n = Math.round(5 + 9 * (f.freq ?? 0.5)), grain = f.grain || 0, amp = 0.05 + 0.14 * (f.sharp || 0);
      for (let j = 0; j < 3; j++) {
        const q = (k.t * (0.3 + 0.35 * k.speed) + j / 3 + v.seed) % 1, rr = r * (1.25 + 1.9 * q), ra = a * (1 - q) ** 2 * 0.8;
        if (ra < 0.01) continue;
        const pts = [], N = 90;
        for (let i = 0; i < N; i++) {
          const th = i / N * 2 * Math.PI, fr = (th / (2 * Math.PI) * n + v.seed + j * 0.37) % 1;
          const round = Math.sin(Math.PI * fr) ** 0.55, tri = 1 - Math.abs(2 * fr - 1), b = round + (tri * tri - round) * (f.sharp || 0);
          const rad = rr * (1 + amp * (b - 0.5) * 2);
          pts.push([x + Math.cos(th) * rad, y + Math.sin(th) * rad]);
        }
        if (grain > 0.35) {                                          // dotted sounds ripple as dots
          g.fillStyle = rgba(j % 2 ? v.c1 : v.c2, ra);
          const step = Math.max(2, Math.round(6 - 4 * grain));
          for (let i = 0; i < N; i += step) { g.beginPath(); g.arc(pts[i][0], pts[i][1], Math.max(1.2, r * 0.09), 0, 7); g.fill(); }
        } else {
          g.beginPath(); for (const [px, py] of pts) g.lineTo(px, py); g.closePath();
          g.lineWidth = Math.max(1.2, r * 0.1 * (1 - q)); g.strokeStyle = rgba(j ? v.c1 : v.c2, ra); g.stroke();
        }
      }
    }

    g.globalCompositeOperation = 'source-over';
    const rb = r * (1 + (v.kick ? 0.25 * Math.exp(-(performance.now() - v.kick) / 280) : 0));
    gr = g.createRadialGradient(x, y + rb, 0, x, y, rb * 1.6);
    gr.addColorStop(0, rgba(v.c2, a)); gr.addColorStop(0.65, rgba(v.c1, a));
    g.beginPath(); g.arc(x, y, rb, 0, 7); g.fillStyle = gr;
    g.shadowColor = rgba(v.c1, 0.8 * a); g.shadowBlur = 14 * dpr; g.fill(); g.shadowBlur = 0;
    if (v.icon) {
      const s = rb * 1.3;
      g.save(); g.translate(x - s / 2, y - s / 2); g.scale(s / 24, s / 24);
      g.fillStyle = rgba(v.c1.map(c => c * 0.14 | 0), 0.92 * a); g.fill(v.icon.path, v.icon.rule);
      g.restore();
    }
    if (v.label && pop > 0.6) {
      g.font = `600 ${clamp(U * 0.03, 11, 14).toFixed(1)}px ui-rounded, "SF Pro Rounded", system-ui, -apple-system, sans-serif`;
      g.textAlign = 'center'; g.textBaseline = 'top';
      g.shadowColor = 'rgba(0,0,0,.8)'; g.shadowBlur = 6 * dpr;
      const tw = g.measureText(v.label).width;                     // kept on screen when the source is near an edge
      g.fillStyle = `rgba(255,244,236,${(0.92 * a).toFixed(3)})`; g.fillText(v.label, clamp(x, tw / 2 + 8, W - tw / 2 - 8), y + R * 0.62 + 4);
      g.shadowBlur = 0;
    }
  }

  // A chip for each sound heard but not located, centred just above the bottom edge: its icon in a dome of
  // its colours, and its name, since there's no direction to show. Icons only if the names don't fit.
  captions(list) {
    const { g, W, H, U, dpr } = this;
    const hgt = clamp(U * 0.085, 30, 46), r = hgt / 2 - 4, pad = 12, gap = 8;
    g.font = `600 ${clamp(U * 0.036, 12, 17).toFixed(1)}px ui-rounded, "SF Pro Rounded", system-ui, -apple-system, sans-serif`;
    g.textBaseline = 'middle'; g.textAlign = 'left';
    list.sort((a, b) => !!b.form.hazard - !!a.form.hazard || a.born - b.born);
    const chips = list.map(v => ({ v, w: hgt + g.measureText(v.label).width + pad }));
    let total = chips.reduce((sum, c) => sum + c.w, 0) + gap * (chips.length - 1);
    const named = total <= W - 24;
    if (!named) { for (const c of chips) c.w = hgt; total = hgt * chips.length + gap * (chips.length - 1); }
    let x = W / 2 - total / 2;
    const y = H - clamp(U * 0.2, 20, AMAX) * 0.62 - 14 - hgt / 2;   // clear of the tallest ring
    for (const { v, w } of chips) {
      const k = v.k, sc = clamp(k.life, 0, 1.2);
      if (k.alpha > 0.01 && sc > 0.02) {
        g.save(); g.translate(x + w / 2, y); g.scale(sc, sc); g.globalAlpha = clamp(k.alpha, 0, 1);
        g.beginPath(); g.roundRect(-w / 2, -hgt / 2, w, hgt, hgt / 2);
        g.fillStyle = 'rgba(24,11,14,.76)'; g.fill();
        g.lineWidth = 1; g.strokeStyle = 'rgba(255,243,234,.14)'; g.stroke();
        const bx = -w / 2 + hgt / 2, gr = g.createRadialGradient(bx, r, 0, bx, 0, r * 1.6);
        gr.addColorStop(0, rgba(v.c2, 1)); gr.addColorStop(0.65, rgba(v.c1, 1));
        g.beginPath(); g.arc(bx, 0, r, 0, 7); g.fillStyle = gr;
        g.shadowColor = rgba(v.c1, 0.7); g.shadowBlur = 10 * dpr; g.fill(); g.shadowBlur = 0;
        if (v.icon) {
          const s = r * 1.3;
          g.save(); g.translate(bx - s / 2, -s / 2); g.scale(s / 24, s / 24);
          g.fillStyle = rgba(v.c1.map(c => c * 0.14 | 0), 0.92); g.fill(v.icon.path, v.icon.rule);
          g.restore();
        }
        if (named) { g.fillStyle = '#fff3ea'; g.fillText(v.label, bx + r + 8, 1); }
        g.restore();
      }
      x += w + gap;
    }
  }
}
