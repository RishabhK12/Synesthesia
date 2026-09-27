// Icons that sit inside a sound's wave or beacon. All are drawn on a 24×24 grid
// and filled with the sound's dark ink.
//
// Two kinds:
//  - eo(path): a hand-written SVG path filled even-odd, so a shape inside another becomes a hole
//    (the clock's hands, the bus's windscreen). The first set is drawn this way.
//  - nz(parts): built from parts. Filled parts merge where they overlap; hole parts cut. They're wound so
//    the non-zero rule does that, which means a hole must sit inside the fills it cuts, and where two fills
//    overlap it's cut twice (see the dog's nose).
// To add one: draw it on the grid, add it here, then name it as a sound's `icon` in common.js.
const circle = (x, y, r) => `M${x - r} ${y}a${r} ${r} 0 1 0 ${2 * r} 0a${r} ${r} 0 1 0 ${-2 * r} 0z`;
const eo = (d) => ({ d, rule: 'evenodd' });
const nz = (...parts) => ({ d: parts.join(''), rule: 'nonzero' });

// ---- parts for nz(): outlines as point lists, then fill() or hole() ----
const RAD = Math.PI / 180, r2 = (n) => Math.round(n * 100) / 100;
const arc = (cx, cy, rx, ry, a0, a1, rot = 0, n = Math.max(6, Math.ceil(Math.abs(a1 - a0) / 8))) => {
  const c = Math.cos(rot * RAD), s = Math.sin(rot * RAD), pts = [];
  for (let i = 0; i <= n; i++) {
    const a = (a0 + (a1 - a0) * i / n) * RAD, x = rx * Math.cos(a), y = ry * Math.sin(a);
    pts.push([cx + x * c - y * s, cy + x * s + y * c]);
  }
  return pts;
};
const P = {
  ell: (cx, cy, rx, ry, rot = 0) => arc(cx, cy, rx, ry, 0, 360, rot, 36).slice(0, -1),
  box: (x, y, w, h, r = 0) => r <= 0 ? [[x, y], [x + w, y], [x + w, y + h], [x, y + h]]
    : [...arc(x + w - r, y + r, r, r, -90, 0), ...arc(x + w - r, y + h - r, r, r, 0, 90), ...arc(x + r, y + h - r, r, r, 90, 180), ...arc(x + r, y + r, r, r, 180, 270)],
  bar: (x1, y1, x2, y2, w) => { const a = Math.atan2(y2 - y1, x2 - x1) / RAD, r = w / 2; return [...arc(x2, y2, r, r, a - 90, a + 90), ...arc(x1, y1, r, r, a + 90, a + 270)]; },
  band: (cx, cy, r1, r2, a0, a1) => [...arc(cx, cy, r2, r2, a0, a1), ...arc(cx, cy, r1, r1, a1, a0)],
  drop: (x, y, r) => [...arc(x, y, r, r, -35, 215), [x, y - r * 2.3]],
  wavy: (x0, x1, y, amp, len, w) => {
    const top = [], bottom = [];
    for (let x = x0; x <= x1 + 1e-6; x += 0.5) { const yy = y + amp * Math.sin(2 * Math.PI * (x - x0) / len); top.push([x, yy]); bottom.unshift([x, yy + w]); }
    return [...top, ...bottom];
  },
  z: (x, y, w, h, t) => [[x, y], [x + w, y], [x + w, y + t], [x + 1.7 * t, y + h - t], [x + w, y + h - t], [x + w, y + h], [x, y + h], [x, y + h - t], [x + w - 1.7 * t, y + t], [x, y + t]],
  star: (cx, cy, outer, inner, turn = 0) => outer.flatMap((ro, i) => { const a = (turn + i * 360 / outer.length) * RAD, b = a + Math.PI / outer.length; return [[cx + ro * Math.cos(a), cy + ro * Math.sin(a)], [cx + inner * Math.cos(b), cy + inner * Math.sin(b)]]; }),
};
// move / turn / mirror a point list: about (0, 0), then placed at (x, y)
const place = (pts, x, y, rot = 0, flip = false) => pts.map(([px, py]) => {
  const fx = flip ? -px : px, c = Math.cos(rot * RAD), s = Math.sin(rot * RAD);
  return [x + fx * c - py * s, y + fx * s + py * c];
});
const outline = (pts, hole) => {
  let a = 0;
  for (let i = 0; i < pts.length; i++) { const [x1, y1] = pts[i], [x2, y2] = pts[(i + 1) % pts.length]; a += x1 * y2 - x2 * y1; }
  const p = (a > 0) !== hole ? pts : pts.slice().reverse();   // positive area = clockwise on screen
  return 'M' + p.map(([x, y]) => `${r2(x)} ${r2(y)}`).join('L') + 'z';
};
const fill = (pts) => outline(pts, false), hole = (pts) => outline(pts, true);
// true circles (arcs): swept clockwise to fill, anticlockwise to cut
const dot = (x, y, r, cut = false) => { const s = cut ? 0 : 1; return `M${r2(x - r)} ${r2(y)}a${r} ${r} 0 1 ${s} ${r2(2 * r)} 0a${r} ${r} 0 1 ${s} ${r2(-2 * r)} 0z`; };

// a mitten-shaped hand, fingers up, thumb to the right, wrist at (0, 0)
const MITT = [P.box(-2.4, -10.6, 4.8, 10.6, 2.4), P.bar(2.1, -4.8, 4.3, -7.2, 1.9)];
// a footprint, toes up, centred on (0, 0)
const FOOT = [P.ell(0, -1.2, 2.3, 3.3), P.ell(0.5, 3.9, 1.6, 1.8), P.ell(-1.6, -6, 0.85, 0.85), P.ell(-0.25, -6.7, 0.8, 0.8), P.ell(1.05, -6.4, 0.7, 0.7), P.ell(2.05, -5.6, 0.6, 0.6)];
const cloud = (dy = 0) => [fill(P.ell(8, 9.2 + dy, 3.9, 3.9)), fill(P.ell(12.6, 7.2 + dy, 4.7, 4.7)), fill(P.ell(17, 9.8 + dy, 3.4, 3.4)), fill(P.box(4.1, 9.2 + dy, 16.3, 4.8, 2.4))];
const boomPts = [10.8, 8.4, 11.2, 8.8, 10.4, 9.2, 11, 8.6, 10.2, 9, 11.2];

export const ICONS = {
  // alarm clock: round face with hands, two bells, a hammer and feet
  alarm: eo(circle(12, 13.5, 7.2) + 'M11.2 9.3h1.6v5h-1.6zM12.8 12.7h3.2v1.6h-3.2z'
    + 'M4.18 9.42A3 3 0 0 1 8.42 5.18zM15.58 5.18A3 3 0 0 1 19.82 9.42zM10.9 4.4h2.2v1.5h-2.2z'
    + 'M6 21.8l1.4-2.1 1.2.8-1.4 2.1zM18 21.8l-1.4-2.1-1.2.8 1.4 2.1z'),
  // police light on its base, flashing both ways
  siren: eo('M7 16v-5a5 5 0 0 1 10 0v5zM11 9h2v5h-2zM5 17h14v2.5H5z'
    + 'M2.3 7.2l.8-1.2 2.4 1.8-.8 1.2zM1.5 11.6h3.2V13H1.5zM21.7 7.2l-.8-1.2-2.4 1.8.8 1.2zM19.3 11.6h3.2V13h-3.2z'),
  // car horn: a flared horn with sound coming out
  horn: eo('M2.5 10.2h4.5l7.5-5.2c.6-.4 1.5 0 1.5.8v12.4c0 .8-.9 1.2-1.5.8L7 13.8H2.5z'
    + 'M18.5 8.4l2.4-1.6.8 1.2-2.4 1.6zM19 11.3h3v1.4h-3zM18.5 15.6l.8-1.2 2.4 1.6-.8 1.2z'),
  // truck / train air horn: twin trumpets on one pipe
  airhorn: eo('M2.5 6.5h4l9-3v7l-9-2h-4zM2.5 14h6l9-3v8l-9-3h-6zM3.2 8.5h1.4V14H3.2z'
    + 'M18.5 5.8h3v1.4h-3zM20 14.3h2.5v1.4H20z'),
  // bicycle bell on the handlebar
  bikebell: eo('M5 15A7 7 0 0 1 19 15zM4 16h16v1.8H4zM11 17.8h2V20h-2zM3 20h18v1.8H3z' + circle(12, 6.6, 1)
    + 'M19.3 13.2l2.7-1.4.6 1.2-2.7 1.4z'),
  // bus, from the front
  bus: eo('M7 2.5h10a3 3 0 0 1 3 3V17a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V5.5a3 3 0 0 1 3-3z'
    + 'M6.5 6h11a.8.8 0 0 1 .8.8v4.6a.8.8 0 0 1-.8.8h-11a.8.8 0 0 1-.8-.8V6.8a.8.8 0 0 1 .8-.8zM9 3.6h6v1.2H9z'
    + circle(7.8, 15.2, 1.4) + circle(16.2, 15.2, 1.4)
    + 'M5.5 19.4h3v1.8a.8.8 0 0 1-.8.8H6.3a.8.8 0 0 1-.8-.8zM15.5 19.4h3v1.8a.8.8 0 0 1-.8.8h-1.4a.8.8 0 0 1-.8-.8z'
    + 'M2 7h1.4v3.5H2zM20.6 7H22v3.5h-1.4z'),
  // someone calling you: a person, their voice reaching out
  person: eo('M2.5 21c0-4.4 2.9-7.5 6.5-7.5s6.5 3.1 6.5 7.5z' + circle(9, 8, 3.6)
    + 'M15.96 4.48A4.6 4.6 0 0 1 15.96 11.52L15.06 10.45A3.2 3.2 0 0 0 15.06 5.55z'
    + 'M17.63 2.48A7.2 7.2 0 0 1 17.63 13.52L16.73 12.44A5.8 5.8 0 0 0 16.73 3.56z'),
  // people talking
  speech: eo('M4 4.5h16a2 2 0 0 1 2 2V15a2 2 0 0 1-2 2h-8.5L6 21v-4H4a2 2 0 0 1-2-2V6.5a2 2 0 0 1 2-2z'
    + circle(7.5, 10.75, 1.4) + circle(12, 10.75, 1.4) + circle(16.5, 10.75, 1.4)),
  // doorbell / chime
  bell: eo('M12 2.8a1.5 1.5 0 0 1 1.5 1.5V5A6 6 0 0 1 18 10.8v4.4l2 2.6H4l2-2.6v-4.4A6 6 0 0 1 10.5 5v-.7A1.5 1.5 0 0 1 12 2.8z'
    + circle(12, 20, 1.9)),
  // knocking at the door
  door: eo('M6 2.5h9a1.5 1.5 0 0 1 1.5 1.5v17.5h-12V4A1.5 1.5 0 0 1 6 2.5zM7.2 5.2h6.6v5.2H7.2zM7.2 12.4h4.4v6.4H7.2z' + circle(14, 14.5, 1.1)
    + 'M18.6 8.2l2.6-1.3.6 1.2-2.6 1.3zM18.8 11.8h3v1.4h-3zM18.6 16.8l.6-1.2 2.6 1.3-.6 1.2z'),
  // crackling leaves, snapping twigs
  leaf: eo('M20 3.5C11 3.5 4.5 8 4.5 15c0 1.3.3 2.5.8 3.5L3 20.8 4.2 22l2.3-2.3c1 .5 2.2.8 3.5.8 7.2 0 10-6.5 10-15z'
    + 'M8.2 17.3C10.3 13.6 13 10.6 16.6 8l.9 1.1c-3.4 2.5-6 5.4-8 9z'),
  // music, voices in a room: ambient sound
  music: eo('M9 5.5l11-2.5v12a3 3 0 1 1-2-2.83V7.6l-7 1.6V17a3 3 0 1 1-2-2.83z'),
  // an ear: something heard, not yet known
  ear: eo('M12.5 2.5c-4.1 0-7 3-7 7 0 1 .7 1.7 1.6 1.7s1.6-.7 1.6-1.7c0-2.2 1.6-3.8 3.8-3.8s3.8 1.6 3.8 3.8c0 1.7-.8 2.6-2 3.7'
    + '-1.3 1.1-2.4 2.4-2.4 4.6 0 1.3-.9 2.2-2.1 2.2-.9 0-1.6-.5-2-1.3-.4-.8-1.4-1.1-2.2-.7-.8.4-1.1 1.4-.7 2.2.9 1.8 2.8 3 4.9 3'
    + ' 3 0 5.3-2.4 5.3-5.4 0-1 .5-1.5 1.4-2.3 1.3-1.2 3-2.8 3-6 0-4-3-7-7-7z'),

  // ---- danger ----
  // breaking glass: a wine glass with a crack, shards flying off
  glass: nz(fill([[4.5, 3], [13, 3], [12.6, 8], [11.2, 10.6], [8.75, 11.6], [6.3, 10.6], [4.9, 8]]),
    hole([[9.4, 3], [10.7, 3], [9.4, 6.1], [11, 6.1], [8.4, 10.4], [9.1, 7.3], [7.6, 7.3]]),
    fill(P.bar(8.75, 11.4, 8.75, 18.6, 1.5)), fill(P.ell(8.75, 19.8, 3.8, 1.25)),
    fill([[15.5, 4.2], [19.4, 2.6], [17.6, 7.1]]), fill([[16.2, 9.6], [21, 9], [17.6, 12.8]]), fill([[15.2, 15.2], [18.6, 14.2], [16.8, 18]])),
  // a crash or bang: a comic burst
  boom: nz(fill(P.star(12, 12, boomPts, 6, -90)), hole(P.star(12, 12, boomPts.map(r => r * 0.5), 3, -90))),
  // someone screaming, hands to their face
  scream: nz(fill(P.ell(12, 11.4, 7.8, 8.6)), fill(P.ell(4.4, 14.6, 1.8, 3.8, 14)), fill(P.ell(19.6, 14.6, 1.8, 3.8, -14)),
    hole(P.ell(9.2, 8.6, 1.1, 1.6)), hole(P.ell(14.8, 8.6, 1.1, 1.6)), hole(P.ell(12, 14.8, 2.4, 3.6))),

  // ---- traffic ----
  // train, from the front, on its rails
  train: nz(fill(P.box(5, 2.5, 14, 15.5, 4)), hole(P.box(7.2, 5, 9.6, 5.6, 1.4)), dot(8.6, 14.4, 1.3, true), dot(15.4, 14.4, 1.3, true),
    fill(P.bar(8, 19.6, 5.4, 22.6, 1.6)), fill(P.bar(16, 19.6, 18.6, 22.6, 1.6)), fill(P.bar(6.4, 21.2, 17.6, 21.2, 1.1))),
  // reversing: an arrow turning back
  reverse: nz(fill(P.band(12.8, 12.4, 4.6, 7.2, 180, 420)), fill([[3.4, 12.2], [10, 12.2], [6.7, 17.4]])),
  // tyres screeching: a wheel with skid marks
  skid: nz(dot(14.2, 12, 7.6), dot(14.2, 12, 4.8, true), dot(14.2, 12, 2.4),
    fill(P.bar(1.6, 8.4, 4.6, 8.4, 1.5)), fill(P.bar(1, 12, 4.4, 12, 1.5)), fill(P.bar(1.6, 15.6, 4.6, 15.6, 1.5))),
  // a car, side on
  car: nz(fill(P.box(1.8, 10.4, 20.4, 5.6, 2.2)), fill([[5.5, 10.8], [8.2, 5.4], [15.6, 5.4], [18.8, 10.8]]),
    hole([[8.4, 10], [9.8, 6.7], [11.5, 6.7], [11.5, 10]]), hole([[12.6, 10], [12.6, 6.7], [15, 6.7], [16.9, 10]]),
    dot(6.8, 17.6, 2.5), dot(17.2, 17.6, 2.5), dot(6.8, 17.6, 0.95, true), dot(17.2, 17.6, 0.95, true)),
  // a motorbike
  motorbike: nz(dot(5.4, 16.6, 3.8), dot(5.4, 16.6, 2, true), dot(18.6, 16.6, 3.8), dot(18.6, 16.6, 2, true),
    fill(P.bar(5.4, 16.6, 10, 12.4, 1.8)), fill(P.box(9.4, 11.6, 5.8, 4, 1)), fill(P.bar(7.6, 10.6, 13, 10.6, 2)),
    fill(P.ell(14.4, 10.6, 2.6, 1.6)), fill(P.bar(18.6, 16.6, 16.4, 8.4, 1.7)), fill(P.bar(14.8, 7.8, 18, 7.8, 1.5))),
  // a plane overhead
  plane: nz(fill(P.bar(12, 2.4, 12, 20.4, 3)), fill([[12, 8.4], [22, 13.6], [22, 15.2], [12, 12.6], [2, 15.2], [2, 13.6]]),
    fill([[12, 16.8], [16.4, 20.2], [16.4, 21.6], [12, 20.2], [7.6, 21.6], [7.6, 20.2]]), hole(P.ell(12, 5.2, 0.8, 1.4))),

  // ---- people ----
  // a baby crying
  baby: nz(fill(P.ell(12, 13, 8.3, 8.3)), fill(P.ell(12, 4.7, 1.8, 1.8)),
    hole(P.bar(8.2, 11.4, 10.2, 12.2, 1)), hole(P.bar(15.8, 11.4, 13.8, 12.2, 1)), hole(P.ell(12, 16.8, 2.3, 2)), hole(P.drop(7.4, 15.4, 0.95))),
  // laughing
  laugh: nz(fill(P.ell(12, 12, 9.6, 9.6)), hole(P.band(8.6, 10.4, 1, 2.1, 200, 340)), hole(P.band(15.4, 10.4, 1, 2.1, 200, 340)),
    hole([...arc(12, 13.2, 5.2, 5.2, 0, 180)])),
  // applause: two hands clapping
  clap: nz(fill(place(MITT[0], 6.9, 22, 15, true)), fill(place(MITT[1], 6.9, 22, 15, true)),
    fill(place(MITT[0], 17.1, 22, -15)), fill(place(MITT[1], 17.1, 22, -15)),
    fill(P.bar(12, 1.8, 12, 4.4, 1.2)), fill(P.bar(7.4, 3, 8.4, 5.4, 1.2)), fill(P.bar(16.6, 3, 15.6, 5.4, 1.2))),
  // cheering: arms up, confetti
  cheer: nz(fill(P.ell(12, 5.2, 2.8, 2.8)), fill(P.bar(12, 10.2, 12, 15.2, 5)), fill(P.bar(9.6, 10.8, 5.2, 4.4, 2)), fill(P.bar(14.4, 10.8, 18.8, 4.4, 2)),
    fill(P.bar(10.8, 16, 9.6, 21.6, 2.2)), fill(P.bar(13.2, 16, 14.4, 21.6, 2.2)),
    dot(3.8, 12, 0.9), dot(20.2, 12, 0.9), dot(5.4, 16.6, 0.7), dot(18.6, 16.8, 0.7), dot(2.8, 7.4, 0.6), dot(21.2, 7.6, 0.6)),
  // singing: a microphone and a note
  mic: nz(fill(P.ell(10.4, 7, 4.3, 4.3)), hole(P.bar(7.4, 6, 13.4, 6, 0.8)), hole(P.bar(7.6, 8.2, 13.2, 8.2, 0.8)),
    fill([[8.6, 10.6], [12.2, 10.6], [11.3, 21], [9.5, 21]]),
    fill(P.ell(17.9, 9.4, 1.6, 1.2, -20)), fill(P.bar(19.3, 9, 19.3, 3, 0.9)), fill([[19.3, 2.6], [22, 4], [22, 5.6], [19.3, 4.4]])),
  // someone shouting
  shout: nz(fill(P.box(2.5, 3, 19, 13.5, 3.5)), fill([[6.5, 15.6], [6.5, 21.5], [12, 15.6]]),
    hole(P.bar(12, 6, 12, 10.8, 2.1)), dot(12, 13.7, 1.15, true)),
  // whistling
  whistle: nz(fill(P.box(8, 8, 13.5, 4.3, 1.2)), dot(9, 14.3, 6), dot(9, 14.8, 2.3, true), dot(3.6, 6.2, 2), dot(3.6, 6.2, 1, true),
    fill(P.bar(17.4, 15.6, 20.6, 16.6, 1.1)), fill(P.bar(16.8, 18.8, 19.2, 20.8, 1.1))),
  // coughing or sneezing
  cough: nz(fill(P.ell(8, 8, 4, 4)), fill([...arc(8, 21.4, 6.8, 6.6, 180, 360)]),
    dot(14.8, 8.8, 1.2), dot(17.6, 6.8, 1.5), dot(18.2, 10.8, 1.7), dot(21.2, 8.6, 1.2)),
  // footsteps
  steps: nz(...FOOT.map(p => fill(place(p, 7.6, 15.4, -10))), ...FOOT.map(p => fill(place(p, 16.6, 8.2, 10, true)))),
  // children playing: two kids holding hands, and a ball
  kids: nz(fill(P.ell(7.4, 5.6, 2.5, 2.5)), fill(P.bar(7.4, 9.8, 7.4, 14.6, 4.2)), fill(P.bar(6.5, 15, 5.5, 21, 1.8)), fill(P.bar(8.3, 15, 9.3, 21, 1.8)),
    fill(P.bar(9.3, 10.4, 12, 13.2, 1.6)), fill(P.bar(5.5, 10.4, 3.4, 13.8, 1.6)),
    fill(P.ell(16.6, 9, 2.1, 2.1)), fill(P.bar(16.6, 12.4, 16.6, 16.2, 3.6)), fill(P.bar(15.9, 16.6, 15.3, 21.2, 1.6)), fill(P.bar(17.3, 16.6, 17.9, 21.2, 1.6)),
    fill(P.bar(14.9, 13, 12, 13.2, 1.6)), fill(P.bar(18.3, 13, 20.2, 15.6, 1.4)), dot(21.2, 20, 1.6)),

  // ---- home ----
  // a door swinging open
  dooropen: nz(fill(P.box(3.5, 2.5, 12.5, 19.5, 0.6)), hole(P.box(5.5, 4.5, 8.5, 17.5)),
    fill([[5.5, 4.5], [12.4, 2.2], [12.4, 23.2], [5.5, 22]]), dot(10.9, 13, 0.8, true), fill(P.band(16, 12, 3.4, 4.6, -45, 45))),
  // a phone ringing
  phone: nz(fill(P.box(7.5, 2.5, 9, 19, 2.2)), hole(P.box(9, 5, 6, 12.6, 0.6)), dot(12, 19.6, 0.8, true),
    fill(P.band(7.5, 12, 3.2, 4.5, 140, 220)), fill(P.band(16.5, 12, 3.2, 4.5, -40, 40)),
    fill(P.band(7.5, 12, 5.8, 7.1, 146, 214)), fill(P.band(16.5, 12, 5.8, 7.1, -34, 34))),
  // a kitchen timer going off
  timer: nz(dot(12, 13.6, 8), dot(12, 13.6, 6.3, true), fill(P.bar(12, 13.6, 15, 9.8, 1.5)), dot(12, 13.6, 1.3),
    fill(P.bar(12, 8.3, 12, 9.3, 0.9)), fill(P.bar(12, 17.9, 12, 18.9, 0.9)), fill(P.bar(6.7, 13.6, 7.7, 13.6, 0.9)), fill(P.bar(16.3, 13.6, 17.3, 13.6, 0.9)),
    fill(P.box(10.4, 2, 3.2, 2.4, 0.8)), fill(P.box(11.2, 4.2, 1.6, 1.8)), fill(P.bar(18.2, 6.6, 19.6, 5.2, 1.8))),
  // a kettle boiling
  kettle: nz(fill(P.box(5, 9.6, 12.6, 11, 3.2)), fill(P.ell(11.3, 9.5, 5.2, 1.5)), dot(11.3, 7.5, 1.3),
    fill([[16.8, 12.2], [21.6, 8.2], [22.4, 9.6], [17.6, 15.6]]), fill(P.band(5.2, 14.6, 2.4, 3.8, 90, 270)),
    dot(21.2, 5.4, 0.9), dot(22.2, 3.2, 0.7), dot(20.4, 2, 0.55)),
  // a running tap
  tap: nz(fill(P.box(1.5, 5, 2.4, 7, 0.8)), fill(P.box(3.4, 7, 11.2, 3.6, 1.4)), fill(P.box(11, 7, 3.6, 6.8, 1.4)),
    fill(P.bar(8, 7, 8, 4.2, 1.7)), fill(P.bar(5.4, 3.4, 10.6, 3.4, 1.7)), fill(P.drop(12.8, 17.4, 1.2)), fill(P.drop(12.8, 21.6, 1))),
  // a toilet flushing
  toilet: nz(fill(P.box(3.2, 2.8, 5.6, 9.8, 1.2)), hole(P.bar(4.6, 5.4, 6.6, 5.4, 0.9)), fill(P.box(2.8, 11.6, 17, 2.4, 1.2)),
    fill([[3.6, 14], [19.2, 14], [18.6, 15.6], [15.4, 18.6], [12, 19.4], [11, 22], [5.2, 22], [6.2, 18.8], [3.6, 16.6]])),
  // dishes and cutlery
  dishes: nz(dot(12.6, 12, 7.4), dot(12.6, 12, 5.5, true), dot(12.6, 12, 4),
    fill(P.bar(1.6, 3, 1.6, 7.6, 0.75)), fill(P.bar(3, 3, 3, 7.6, 0.75)), fill(P.bar(4.4, 3, 4.4, 7.6, 0.75)), fill(P.box(1.2, 7.4, 3.6, 1.8, 0.9)), fill(P.bar(3, 9, 3, 21, 1.5)),
    fill([[20.8, 3], [22.6, 3.6], [22.6, 12.6], [20.8, 12.6]]), fill(P.bar(21.7, 12.4, 21.7, 21, 1.5))),
  // keys
  keys: nz(dot(7.2, 7.2, 4.6), dot(7.2, 7.2, 2, true), fill(P.bar(9.9, 9.9, 20.2, 20.2, 2.3)),
    fill(P.bar(15.6, 15.6, 17.3, 13.9, 1.6)), fill(P.bar(18.4, 18.4, 20.1, 16.7, 1.6)), fill(P.bar(2, 14.4, 4.2, 13.6, 1)), fill(P.bar(2.6, 17.8, 5, 16.4, 1))),
  // typing on a keyboard
  keyboard: nz(fill(P.box(1.5, 6, 21, 12, 2)),
    ...[0, 1, 2, 3, 4, 5, 6].map(i => hole(P.box(3.3 + i * 2.55, 8.1, 1.9, 1.9, 0.4))),
    ...[0, 1, 2, 3, 4, 5, 6].map(i => hole(P.box(3.9 + i * 2.55, 10.9, 1.9, 1.9, 0.4))),
    hole(P.box(3.3, 13.9, 1.9, 1.9, 0.4)), hole(P.box(6.4, 13.9, 11.2, 1.9, 0.5)), hole(P.box(18.8, 13.9, 1.9, 1.9, 0.4))),
  // a clock ticking
  clock: nz(dot(12, 12, 9.5), dot(12, 12, 7.7, true), fill(P.bar(12, 12, 12, 6.6, 1.6)), fill(P.bar(12, 12, 16, 12, 1.6)), dot(12, 12, 1.3),
    fill(P.bar(12, 17.2, 12, 18.4, 0.9)), fill(P.bar(5.6, 12, 6.8, 12, 0.9))),

  // ---- nature and animals ----
  // rain
  rain: nz(...cloud(), fill(P.drop(7.6, 19.2, 1.2)), fill(P.drop(12.2, 21.4, 1.2)), fill(P.drop(16.8, 19.2, 1.2))),
  // a stream: running water
  stream: nz(fill(P.wavy(2, 22, 5.5, 1.25, 6.5, 2)), fill(P.wavy(2, 22, 10.5, 1.25, 6.5, 2)), fill(P.wavy(2, 22, 15.5, 1.25, 6.5, 2))),
  // an ocean wave curling over
  wave: nz(fill(P.band(13.6, 10.6, 3.2, 6.6, 180, 420)),
    fill([[1.5, 22], [1.5, 17.2], [3.8, 15.6], [5.8, 13.4], [7, 10.6], [10.4, 10.6], [10, 13.6], [11.4, 16.6], [14.8, 18.6], [22.5, 18.6], [22.5, 22]])),
  // wind
  wind: nz(fill(P.bar(2, 8.2, 14, 8.2, 1.8)), fill(P.band(14, 5.8, 1.5, 3.3, 90, -180)),
    fill(P.bar(2, 12.6, 18.6, 12.6, 1.8)), fill(P.band(18.6, 10.2, 1.5, 3.3, 90, -180)),
    fill(P.bar(2, 17, 12, 17, 1.8)), fill(P.band(12, 19.4, 1.5, 3.3, -90, 180))),
  // thunder and lightning
  thunder: nz(...cloud(-1.2), fill([[12.6, 11.8], [16.4, 11.8], [13.8, 15.8], [16.8, 15.8], [10, 23], [11.8, 17.6], [9, 17.6]])),
  // a bird singing
  bird: nz(fill(P.ell(10.6, 13.4, 6.6, 4.8, -18)), fill(P.ell(16.4, 8.4, 3.3, 3.3)), fill([[19.3, 7.4], [22.8, 8.3], [19.5, 9.6]]),
    fill([[5.2, 14.2], [1, 11], [1.4, 16.8], [5.2, 17]]), hole([[7.8, 12], [13, 11.4], [10.4, 15.2]]), dot(17.3, 7.8, 0.75, true),
    fill(P.bar(9.4, 17.6, 8.8, 21.6, 0.8)), fill(P.bar(12, 17.2, 12.4, 21.4, 0.8))),
  // crickets at night
  cricket: nz(fill(P.bar(8.4, 12.6, 18.8, 15.4, 5.2)), fill(P.ell(6.2, 10.8, 2.4, 2.4)), hole(P.bar(10.8, 13.4, 17.2, 15, 0.7)),
    fill(P.bar(15.6, 13, 19.6, 8.2, 1.3)), fill(P.bar(19.6, 8.2, 21.6, 19.2, 1.1)),
    fill(P.bar(9.6, 15.2, 7.6, 19.8, 0.9)), fill(P.bar(12.8, 16, 12, 20.4, 0.9)),
    fill(P.bar(5.2, 9, 1.6, 2.2, 0.6)), fill(P.bar(6.6, 8.6, 5.4, 1.6, 0.6))),
  // a dog barking (the nose and mouth are cut twice: head and snout overlap there)
  dog: nz(fill(P.ell(12, 11.6, 6.6, 6.6)), fill(P.ell(4.8, 11.6, 2.4, 5, 12)), fill(P.ell(19.2, 11.6, 2.4, 5, -12)), fill(P.ell(12, 15.6, 3.7, 3)),
    dot(9.4, 10, 1.05, true), dot(14.6, 10, 1.05, true),
    hole(P.ell(12, 14.2, 1.7, 1.15)), hole(P.ell(12, 14.2, 1.7, 1.15)), hole(P.bar(12, 15.6, 12, 17, 0.6)), hole(P.bar(12, 15.6, 12, 17, 0.6))),
  // a cat
  cat: nz(fill(P.ell(12, 13.6, 8, 7)), fill([[4.8, 10.4], [5.6, 2.6], [11, 7.4]]), fill([[19.2, 10.4], [18.4, 2.6], [13, 7.4]]),
    hole(P.ell(8.8, 12.6, 1.5, 1.1)), hole(P.ell(15.2, 12.6, 1.5, 1.1)), hole([[10.9, 15.5], [13.1, 15.5], [12, 16.8]]),
    hole(P.bar(4.8, 15.6, 8.8, 16.3, 0.55)), hole(P.bar(6, 17.6, 9, 17.2, 0.55)), hole(P.bar(19.2, 15.6, 15.2, 16.3, 0.55)), hole(P.bar(18, 17.6, 15, 17.2, 0.55))),

  // ---- low hums and music ----
  // snoring
  snore: nz(fill(P.z(3, 11, 9, 9.5, 2.2)), fill(P.z(13, 5.5, 6, 6.5, 1.7)), fill(P.z(19.2, 1.8, 3.8, 4.2, 1.2))),
  // a vacuum cleaner
  vacuum: nz(fill(P.bar(15.5, 2.6, 11.2, 14.6, 1.9)), fill(P.bar(13.6, 2.3, 17.4, 2.9, 2.1)), fill([[9.6, 10.2], [14, 11.2], [12.6, 17.4], [8.2, 16.4]]),
    fill(P.box(2.6, 17, 14.4, 3.6, 1.8)), dot(5.2, 21, 1.4)),
  // a washing machine
  washer: nz(fill(P.box(3.5, 2.5, 17, 19, 2.5)), hole(P.bar(6.4, 5.6, 10.6, 5.6, 1.3)), dot(16.6, 5.6, 1.15, true),
    dot(12, 14.2, 5.4, true), dot(12, 14.2, 3.9), hole(P.band(12, 14.2, 2.3, 3, 200, 260))),
  // a crowd: people murmuring
  crowd: nz(fill(P.ell(4.6, 8.6, 2.3, 2.3)), fill(arc(4.6, 19.6, 3.3, 5.4, 180, 360)),
    fill(P.ell(12, 6.8, 2.9, 2.9)), fill(arc(12, 19.8, 4, 6.6, 180, 360)),
    fill(P.ell(19.4, 8.6, 2.3, 2.3)), fill(arc(19.4, 19.6, 3.3, 5.4, 180, 360))),
};

// Inline SVG for pages. It takes the text colour (currentColor), so style it with `color`.
export const iconSvg = (name) => {
  const i = ICONS[name];
  return i ? `<svg viewBox="0 0 24 24" aria-hidden="true"><path fill="currentColor" fill-rule="${i.rule}" d="${i.d}"/></svg>` : '';
};

// For the canvas: { path: Path2D, rule }, made once per icon. Fill with ctx.fill(icon.path, icon.rule).
const made = {};
export const icon = (name) => ICONS[name] ? (made[name] ??= { path: new Path2D(ICONS[name].d), rule: ICONS[name].rule }) : null;
