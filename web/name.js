// Detect a configured name from local speech transcripts. SpeechRecognition is
// used only with processLocally=true; never start a cloud-backed fallback.
export function cleanName(value) {
  const name = value.normalize('NFKC').trim().replace(/\s+/g, ' ');
  if (!name || name.length > 60 || !/^[A-Za-z][A-Za-z '\-]*$/.test(name)) {
    throw new Error('Use 1–60 English letters, spaces, apostrophes or hyphens.');
  }
  return name;
}

export function normalizeWords(value) {
  return value.normalize('NFKC').toLowerCase().replace(/[^a-z]+/g, ' ').trim().replace(/\s+/g, ' ');
}

export function heardName(transcript, names) {
  const words = ` ${normalizeWords(transcript)} `;
  return names.some(name => words.includes(` ${normalizeWords(name)} `));
}

export class NameSpotter {
  constructor({ onEvent, onStatus, recognitionClass } = {}) {
    this.onEvent = onEvent;
    this.onStatus = onStatus;
    this.Recognition = recognitionClass ?? globalThis.SpeechRecognition ?? globalThis.webkitSpeechRecognition;
    this.names = [];
    this.active = false;
    this.lastHit = -Infinity;
    this.restartTimer = null;
    this.recognition = null;
    this.status('off', 'Enter a name to enable name alerts');
  }

  configure(values) {
    this.names = values.map(value => value.trim()).filter(Boolean).map(cleanName);
    if (this.names.length > 3) throw new Error('Enter one name and up to two nicknames.');
    if (this.active) this.stop();
    this.status('off', this.names.length ? 'Name saved on this phone' : 'Enter a name to enable name alerts');
  }

  status(state, message) {
    this.state = state;
    this.onStatus?.(state, message);
  }

  async start() {
    if (this.active || !this.names.length) return;
    const Recognition = this.Recognition;
    if (!Recognition || typeof Recognition.available !== 'function') {
      this.status('unsupported', 'This Chrome cannot recognize names locally');
      return;
    }
    this.active = true;
    this.status('starting', 'Checking local speech recognition…');
    try {
      const probe = new Recognition();
      if (!('processLocally' in probe)) throw new Error('Local speech recognition is unavailable in this browser');
      const opts = { langs: ['en-US'], processLocally: true };
      let availability = await Recognition.available(opts);
      if (!this.active) return;
      if (availability === 'downloadable' || availability === 'downloading') {
        this.status('starting', 'Installing English speech pack on this phone…');
        if (typeof Recognition.install !== 'function' || !await Recognition.install(opts)) {
          throw new Error('Could not install the local English speech pack');
        }
        availability = await Recognition.available(opts);
      }
      if (!this.active) return;
      if (availability !== 'available') throw new Error('Local English speech recognition is unavailable');
      this.recognition = probe;
      probe.processLocally = true;
      probe.lang = 'en-US';
      probe.continuous = true;
      probe.interimResults = false;
      probe.maxAlternatives = 1;
      probe.onresult = event => {
        for (let i = event.resultIndex; i < event.results.length; i++) {
          const result = event.results[i];
          if (!result.isFinal || !heardName(result[0]?.transcript || '', this.names)) continue;
          const now = performance.now();
          if (now - this.lastHit >= 2500) {
            this.lastHit = now;
            this.onEvent?.(this.names[0]);
          }
        }
      };
      probe.onerror = event => {
        if (!this.active) return;
        if (['not-allowed', 'service-not-allowed', 'language-not-supported', 'audio-capture'].includes(event.error)) {
          this.fail(`Name detection unavailable: ${event.error}`);
        } else if (event.error !== 'no-speech' && event.error !== 'aborted') {
          this.status('starting', `Speech paused: ${event.error}`);
        }
      };
      probe.onend = () => {
        if (!this.active) return;
        this.status('starting', 'Restarting local name detection…');
        this.restartTimer = setTimeout(() => this.open(), 400);
      };
      this.open();
    } catch (error) {
      if (this.active) this.fail(error.message || String(error));
    }
  }

  open() {
    if (!this.active || !this.recognition) return;
    try {
      this.recognition.start();
      this.status('on', `Listening for ${this.names[0]} on this phone`);
    } catch (error) {
      this.fail(`Name detection could not start: ${error.message}`);
    }
  }

  fail(message) {
    this.stop();
    this.status('failed', message);
  }

  stop() {
    this.active = false;
    clearTimeout(this.restartTimer);
    this.restartTimer = null;
    const recognition = this.recognition;
    this.recognition = null;
    if (recognition) {
      recognition.onend = null;
      recognition.onerror = null;
      recognition.onresult = null;
      recognition.abort();
    }
    this.status('off', this.names.length ? 'Name detection off' : 'Enter a name to enable name alerts');
  }
}
