import test from 'node:test';
import assert from 'node:assert/strict';
import { decodeDirection } from './ble.js';
import { SoundFusion } from './fusion.js';
import { cleanName, heardName, NameSpotter } from './name.js';

function packet(status, angle = 0xffff, separation = 0, sequence = 1) {
  const bytes = new Uint8Array([1, status, angle & 255, angle >> 8, separation, sequence]);
  return new DataView(bytes.buffer);
}

function fusion() {
  const cues = [], notes = [];
  return { cues, notes, matcher: new SoundFusion({
    onCue: cue => cues.push(cue), onNote: note => notes.push(note),
  }) };
}

test('decodes the six-byte ESP32 packet and rear flag', () => {
  assert.deepEqual(decodeDirection(packet(0x80, 1372, 61, 8)), {
    status: 'ok', angle_deg: 137.2, separation: 0.61,
    rear_triggered: true, sequence: 8,
  });
  assert.equal(decodeDirection(packet(0x83)).status, 'rear_possible');
  assert.throws(() => decodeDirection(packet(0x03)), /Invalid sensor packet/);
  assert.throws(() => decodeDirection(packet(0x80, 0xffff)), /Invalid sensor angle/);
});

test('matches classification that arrives before a direction event', () => {
  const { cues, matcher } = fusion();
  matcher.receiveSounds([{ key: 'siren', score: 0.8, fresh: true, loud: 0.6 }], 1000);
  assert.equal(cues[0].angle_deg, null);
  matcher.receiveDirection({ status: 'ok', angle_deg: 90, rear_triggered: false }, 1500);
  assert.equal(cues[1].key, 'siren');
  assert.equal(cues[1].angle_deg, 90);
  assert.equal(cues[1].replace, true);
  matcher.flush(4000);
  assert.equal(cues.length, 2);
});

test('matches direction that arrives before classification', () => {
  const { cues, matcher } = fusion();
  matcher.receiveDirection({ status: 'ok', angle_deg: 240, rear_triggered: true }, 1000);
  matcher.receiveSounds([{ key: 'dog', score: 0.8, fresh: true, loud: 0.6 }], 1700);
  assert.equal(cues.length, 1);
  assert.equal(cues[0].angle_deg, 240);
  assert.equal(cues[0].rear_triggered, true);
});

test('does not assign one direction to two classified sounds', () => {
  const { cues, notes, matcher } = fusion();
  matcher.receiveDirection({ status: 'ok', angle_deg: 70 }, 1000);
  matcher.receiveSounds([
    { key: 'siren', fresh: true }, { key: 'dog', fresh: true },
  ], 1400);
  assert.equal(cues.filter(cue => cue.angle_deg != null).length, 0);
  assert.equal(notes.length, 1);
  matcher.flush(3000);
  assert.equal(cues.at(-1).label, 'Unclassified sound');
});

test('rear-only detection stays uncertain', () => {
  const { cues, matcher } = fusion();
  matcher.receiveDirection({ status: 'rear_possible', angle_deg: null }, 1000);
  assert.equal(cues[0].angle_deg, null);
  assert.equal(cues[0].possibleRear, true);
});

test('name cue can inherit a recent talking direction without assigning other sounds', () => {
  const { cues, matcher } = fusion();
  matcher.receiveSounds([{ key: 'speech', fresh: true }], 1000);
  matcher.receiveDirection({ status: 'ok', angle_deg: 35 }, 1300);
  matcher.receiveSounds([{ key: 'name', label: 'Alex called', fresh: true }], 2200);
  assert.equal(cues.at(-1).angle_deg, 35);
  assert.equal(cues.at(-1).label, 'Alex called');
  assert.equal(cues.at(-1).replace, true);
});

test('matches a saved name or pronunciation as whole words only', () => {
  assert.equal(cleanName('  Rishabh  '), 'Rishabh');
  assert.equal(heardName('Hey Rishabh, come here!', ['Rishabh']), true);
  assert.equal(heardName('Hey Rishab!', ['Rishabh', 'Rishab']), true);
  assert.equal(heardName('I will announce it', ['Ann']), false);
  assert.throws(() => cleanName('name@host.com'), /English letters/);
});

test('name detector requires a local speech pack and never starts remote recognition', async () => {
  let starts = 0;
  class Recognition {
    static async available(options) {
      assert.deepEqual(options, { langs: ['en-US'], processLocally: true });
      return 'unavailable';
    }
    processLocally = false;
    start() { starts++; }
  }
  const statuses = [];
  const spotter = new NameSpotter({ recognitionClass: Recognition,
    onStatus: (state, message) => statuses.push({ state, message }) });
  spotter.configure(['Rishabh']);
  await spotter.start();
  assert.equal(starts, 0);
  assert.equal(statuses.at(-1).state, 'failed');
});

test('name detector emits one cue per cooldown from local transcripts', async () => {
  class Recognition {
    static async available() { return 'available'; }
    processLocally = false;
    start() { assert.equal(this.processLocally, true); }
    abort() {}
  }
  const hits = [];
  const spotter = new NameSpotter({ recognitionClass: Recognition, onEvent: name => hits.push(name) });
  spotter.configure(['Rishabh', 'Rishab']);
  await spotter.start();
  const result = transcript => ({ resultIndex: 0, results: [Object.assign([{ transcript }], { isFinal: true })] });
  spotter.recognition.onresult(result('Hey Rishab'));
  spotter.recognition.onresult(result('Rishabh'));
  assert.deepEqual(hits, ['Rishabh']);
  spotter.stop();
});
