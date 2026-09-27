import { SOUNDS, resolve, wrap, dirWord } from './common.js';
import { Halo } from './halo.js';
import { Listener } from './listen.js';
import { DirectionSensor } from './ble.js';
import { SoundFusion } from './fusion.js';
import { NameSpotter, cleanName } from './name.js';

const $ = (id) => document.getElementById(id);
const halo = new Halo($('halo'));
const camera = $('camera');
const R2D = 180 / Math.PI;
const D2R = Math.PI / 180;
let heading = 0;
let initialHeading = null;
let cameraStream = null;
let mountOffset = Number(localStorage.getItem('syn.mountOffset') || 0);
let rearTimer;

function setIndicator(id, state) {
  $(id).className = 'indicator' + (state ? ` ${state}` : '');
}

function note(message) {
  $('fusion-note').textContent = message;
}

function showCue(cue) {
  clearTimeout(rearTimer);
  $('rear-cue').hidden = true;
  if (cue.possibleRear) {
    $('rear-cue').hidden = false;
    rearTimer = setTimeout(() => { $('rear-cue').hidden = true; }, 2500);
    $('cue-name').textContent = 'Possible sound behind';
    $('cue-direction').textContent = 'Rear threshold sensor fired; exact direction and sound type are unknown.';
    navigator.vibrate?.([90, 60, 90]);
    return;
  }

  const sound = SOUNDS[cue.key] ? resolve({ sound: cue.key }) : resolve({ sound: 'behind' });
  const label = cue.label || sound.label;
  const relativeAngle = cue.angle_deg == null ? null : wrap(cue.angle_deg + mountOffset);
  const yaw = relativeAngle == null ? null : wrap(heading + relativeAngle);
  if (cue.key === 'name') halo.clear(performance.now(), 'speech');
  if (cue.replace) halo.clear(performance.now(), cue.key);
  const loud = Number.isFinite(cue.loud) ? cue.loud : 0.5;
  const level = Math.min(1, (sound.form.size ?? 0.6) * (0.65 + 0.4 * loud));
  halo.add({ key: cue.key, color: sound.color, core: sound.core, icon: sound.icon,
    label, form: sound.form, yaw, ttl: cue.fresh ? 5500 : 3000, level,
    alert: !!cue.fresh && !!sound.form.hazard, kick: !!cue.fresh });
  $('cue-name').textContent = label;
  $('cue-direction').textContent = relativeAngle == null
    ? 'Recognized by the phone; direction not available.'
    : `About ${dirWord(relativeAngle)} (${Math.round((relativeAngle + 360) % 360)}° from rig front)${cue.rear_triggered ? ' · rear sensor also triggered' : ''}.`;
  if (cue.fresh && sound.form.hazard) navigator.vibrate?.([140, 70, 140]);
}

const fusion = new SoundFusion({ onCue: showCue, onNote: note });
setInterval(() => fusion.flush(performance.now()), 200);

const nameSpotter = new NameSpotter({
  onEvent: name => fusion.receiveSounds([{ key: 'name', label: `${name} called`,
    score: 1, fresh: true, loud: 0.8 }], performance.now()),
  onStatus: (state, message) => {
    $('name-state').textContent = message;
    setIndicator('name-indicator', state === 'on' ? 'on' : state === 'starting' ? 'busy'
      : state === 'failed' || state === 'unsupported' ? 'bad' : '');
  },
});

const nameIds = ['your-name', 'name-alias-1', 'name-alias-2'];
try {
  const saved = JSON.parse(localStorage.getItem('syn.names') || '[]');
  if (Array.isArray(saved)) {
    saved.slice(0, 3).forEach((value, i) => { $(nameIds[i]).value = value; });
    if (saved.length) nameSpotter.configure(saved.slice(0, 3));
  }
} catch (_) { localStorage.removeItem('syn.names'); }
$('name-form').addEventListener('submit', event => {
  event.preventDefault();
  try {
    const names = nameIds.map(id => $(id).value.trim()).filter(Boolean).map(cleanName);
    nameSpotter.configure(names);
    localStorage.setItem('syn.names', JSON.stringify(names));
    $('name-save-status').textContent = 'Saved on this phone';
    if (ears.run) nameSpotter.start();
  } catch (error) {
    $('name-save-status').textContent = error.message;
  }
});

const sensor = new DirectionSensor({
  onEvent: (event) => fusion.receiveDirection(event, performance.now()),
  onState: (state, message) => {
    $('bluetooth-state').textContent = message;
    setIndicator('bluetooth-indicator', state === 'connected' ? 'on'
      : state === 'connecting' ? 'busy' : state === 'unsupported' ? 'bad' : '');
    $('connect-sensor').disabled = state === 'connecting' || state === 'connected';
    $('connect-sensor').textContent = state === 'connected' ? 'Connected' : 'Connect';
  },
});
$('connect-sensor').addEventListener('click', () => sensor.connect());

const ears = new Listener({
  events: (events) => fusion.receiveSounds(events, performance.now()),
  status: (state, detail) => {
    $('microphone-state').textContent = {
      off: 'Classification off', starting: 'Loading sound model and microphone…',
      on: 'Classifying sounds on this phone', paused: 'Tap Start to resume audio',
      blocked: 'Microphone permission denied', failed: `Classification failed: ${detail}`,
    }[state] || state;
    setIndicator('microphone-indicator', state === 'on' ? 'on'
      : state === 'starting' ? 'busy' : state === 'blocked' || state === 'failed' ? 'bad' : '');
    $('toggle-listening').textContent = state === 'on' || state === 'starting' ? 'Stop' : 'Start';
  },
  frame: ({ top }) => {
    $('guesses').textContent = top.length
      ? top.map(item => `${item.name} ${Math.round(item.score * 100)}%`).join(' · ')
      : 'No clear sound';
  },
});
$('toggle-listening').addEventListener('click', () => {
  if (ears.state === 'paused') return ears.nudge();
  if (ears.run) { ears.stop(); nameSpotter.stop(); fusion.reset(); }
  else { ears.start(); nameSpotter.start(); }
});

async function toggleCamera() {
  if (cameraStream) {
    cameraStream.getTracks().forEach(track => track.stop());
    cameraStream = null;
    camera.srcObject = null;
    camera.hidden = true;
    $('camera-state').textContent = 'Optional live background';
    $('toggle-camera').textContent = 'Show';
    setIndicator('camera-indicator', '');
    return;
  }
  if (!navigator.mediaDevices?.getUserMedia) {
    $('camera-state').textContent = 'Camera needs HTTPS and permission';
    setIndicator('camera-indicator', 'bad');
    return;
  }
  $('camera-state').textContent = 'Opening back camera…';
  setIndicator('camera-indicator', 'busy');
  try {
    cameraStream = await navigator.mediaDevices.getUserMedia({
      audio: false, video: { facingMode: { ideal: 'environment' } },
    });
    camera.srcObject = cameraStream;
    camera.hidden = false;
    await camera.play();
    $('camera-state').textContent = 'Back camera on';
    $('toggle-camera').textContent = 'Hide';
    setIndicator('camera-indicator', 'on');
  } catch (error) {
    cameraStream?.getTracks().forEach(track => track.stop());
    cameraStream = null;
    camera.srcObject = null;
    camera.hidden = true;
    $('camera-state').textContent = `Camera unavailable: ${error.message}`;
    setIndicator('camera-indicator', 'bad');
  }
}
$('toggle-camera').addEventListener('click', toggleCamera);

function updateOffset() {
  mountOffset = Number($('mount-offset').value);
  $('mount-value').textContent = `${mountOffset}°`;
  localStorage.setItem('syn.mountOffset', String(mountOffset));
}
$('mount-offset').value = String(Number.isFinite(mountOffset) ? Math.max(-180, Math.min(180, mountOffset)) : 0);
$('mount-offset').addEventListener('input', updateOffset);
updateOffset();

const savedSensitivity = localStorage.getItem('syn.sensitivity');
if ([...$('sensitivity').options].some(option => option.value === savedSensitivity)) {
  $('sensitivity').value = savedSensitivity;
}
ears.sens = Number($('sensitivity').value);
$('sensitivity').addEventListener('change', () => {
  ears.sens = Number($('sensitivity').value);
  localStorage.setItem('syn.sensitivity', $('sensitivity').value);
});

// Track phone turns so a recent cue stays in approximately the same world direction.
// The camera's back is the forward axis; if motion data is unavailable, cues
// remain relative to the phone's orientation at detection time.
window.addEventListener('deviceorientation', (event) => {
  if (event.alpha == null || event.beta == null) return;
  const a = event.alpha * D2R, b = event.beta * D2R, g = (event.gamma || 0) * D2R;
  const vx = -Math.cos(a) * Math.sin(g) - Math.sin(a) * Math.sin(b) * Math.cos(g);
  const vy = -Math.sin(a) * Math.sin(g) + Math.cos(a) * Math.sin(b) * Math.cos(g);
  if (Math.hypot(vx, vy) <= 0.25) return;
  const bearing = Math.atan2(vx, vy) * R2D;
  if (initialHeading == null) initialHeading = bearing;
  heading = wrap(bearing - initialHeading);
});

function project(voice) {
  if (!cameraStream) return null;
  const fov = 70;
  const relative = wrap(voice.yaw - heading);
  if (Math.abs(relative) >= fov / 2) return null;
  return { x: innerWidth / 2 + (innerWidth / 2) * Math.tan(relative * D2R) / Math.tan(fov / 2 * D2R),
    y: innerHeight / 2 };
}

function draw(now) {
  halo.draw(now, heading, 70, project);
  requestAnimationFrame(draw);
}
requestAnimationFrame(draw);

window.addEventListener('pagehide', () => {
  ears.stop();
  nameSpotter.stop();
  cameraStream?.getTracks().forEach(track => track.stop());
  sensor.disconnect();
});
