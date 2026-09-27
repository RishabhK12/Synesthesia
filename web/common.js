// Sound catalogue shared by the phone classifier and visual renderer.
// Change a sound's name, icon, colour or shape here.

// Colour is attention (colour psychology). Warm = less calm, look now; cool = calm; purple = a low hum.
//   red = danger · orange → amber → gold = traffic and other warnings, hottest first · pink = someone needs you
//   blue = people · teal = home · green = nature · purple = low tones, music, ambience, unknown
// `core` is the hot colour at the heart of the wave.
// `icon` names a shape in icons.js, drawn inside the dome that marks the sound's direction.
// `form` is the wave's shape; halo.js explains each number, and each `beat` (the rhythm it breathes with).
//   spiky ↔ soft (sharp) · dotted ↔ solid (grain) · large ↔ small (size) · tight ↔ broad waves (freq)
//   voices (words, song) are bars; sounds that matter (`hazard`) surge in and send a light round the edge.
// `group` groups sounds. `loop` marks a sound that may continue (rain, a crowd).
// `hear` is what the microphone listener (listen.js) listens for: YAMNet labels (each used once), plus the
// score one window needs to count (`weak`, confirmed by a second window) or to alert on its own (`strong`).
// `yields`: sounds it gives way to when heard at the same time (a smoke alarm's beeps also score as a timer's).
// Horn, siren, alarm, doorbell and knock use the Synesthesia app's thresholds; the rest are provisional.
export const GROUPS = [
  { key: 'danger',  label: 'Danger',  color: '#ff2e4d' },
  { key: 'traffic', label: 'Traffic', color: '#ff8c1a' },
  { key: 'people',  label: 'People',  color: '#4f9dff' },
  { key: 'home',    label: 'Home',    color: '#14d2b9' },
  { key: 'nature',  label: 'Nature',  color: '#52d273' },
  { key: 'low',     label: 'Low & music', color: '#9b5cff' },
];

export const SOUNDS = {
  // ---- danger: red ----
  fire:     { group: 'danger', label: 'Alarm', icon: 'alarm', color: '#ff2e4d', core: '#ffb03a',
              form: { size: 1, sharp: .95, grain: .08, freq: .85, flow: .1, blob: 1, beat: 'alarm', hazard: true },
              hear: { weak: .30, strong: .65, labels: ['Alarm', 'Fire alarm', 'Smoke detector, smoke alarm', 'Car alarm', 'Alarm clock', 'Buzzer'] } },
  siren:    { group: 'danger', label: 'Siren', icon: 'siren', color: '#ff2e63', core: '#6f8bff',   // red and blue, like the lights
              form: { size: 1, sharp: .7, grain: 0, freq: .7, flow: .25, blob: .9, beat: 'wail', hazard: true },
              hear: { weak: .25, strong: .60, labels: ['Siren', 'Police car (siren)', 'Ambulance (siren)', 'Fire engine, fire truck (siren)', 'Civil defense siren', 'Emergency vehicle'] } },
  glass:    { group: 'danger', label: 'Glass breaking', icon: 'glass', color: '#ff3d6e', core: '#d8f4ff',   // an icy core: shards
              form: { size: .9, sharp: 1, grain: .6, freq: .95, flow: 0, blob: .9, beat: 'shatter', hazard: true },
              hear: { weak: .25, strong: .55, labels: ['Glass', 'Shatter', 'Breaking'] } },
  crash:    { group: 'danger', label: 'Crash', icon: 'boom', color: '#ff3a2a', core: '#ffd35a',
              form: { size: 1, sharp: .9, grain: .35, freq: .3, flow: .1, blob: 1, beat: 'hit', hazard: true },
              hear: { weak: .30, strong: .60, labels: ['Smash, crash', 'Explosion', 'Boom', 'Bang', 'Gunshot, gunfire', 'Burst, pop', 'Fireworks', 'Firecracker'] } },
  scream:   { group: 'danger', label: 'Scream', icon: 'scream', color: '#ff2e7a', core: '#ffd0e0',
              form: { size: 1, sharp: .8, grain: .1, freq: .9, flow: .2, blob: .95, beat: 'scream', hazard: true },
              hear: { weak: .25, strong: .55, labels: ['Screaming'] } },

  // ---- traffic: orange → amber → gold ----
  truck:    { group: 'traffic', label: 'Truck horn', icon: 'airhorn', color: '#ff4a2e', core: '#ffb347',
              form: { size: 1, sharp: .8, grain: 0, freq: .3, flow: .15, blob: 1, beat: 'blast', hazard: true },
              hear: { weak: .25, strong: .55, labels: ['Air horn, truck horn', 'Foghorn'] } },
  horn:     { group: 'traffic', label: 'Car horn', icon: 'horn', color: '#ff6a1a', core: '#ffe14d',
              form: { size: .9, sharp: .85, grain: 0, freq: .55, flow: .05, blob: 1, beat: 'honk', hazard: true },
              hear: { weak: .25, strong: .55, labels: ['Vehicle horn, car horn, honking', 'Toot'] } },
  skid:     { group: 'traffic', label: 'Tyres screeching', icon: 'skid', color: '#ff5a1f', core: '#ffe0a0',
              form: { size: .95, sharp: .75, grain: .45, freq: .95, flow: .3, blob: .9, beat: 'squeal', hazard: true },
              hear: { weak: .25, strong: .55, labels: ['Skidding', 'Tire squeal', 'Squeal'] } },
  train:    { group: 'traffic', label: 'Train', icon: 'train', color: '#ff7a24', core: '#ffd27a',
              form: { size: 1, sharp: .3, grain: .05, freq: .15, flow: .45, blob: .85, beat: 'clack', hazard: true },
              hear: { weak: .30, strong: .60, labels: ['Train', 'Train horn', 'Train whistle', 'Rail transport', 'Railroad car, train wagon', 'Train wheels squealing', 'Subway, metro, underground', 'Clickety-clack'] } },
  reverse:  { group: 'traffic', label: 'Reversing', icon: 'reverse', color: '#ff9a1a', core: '#fff07a',
              form: { size: .8, sharp: .9, grain: 0, freq: .9, flow: 0, blob: .9, beat: 'beep', hazard: true },
              hear: { weak: .25, strong: .55, labels: ['Reversing beeps'] } },
  bus:      { group: 'traffic', label: 'Bus', icon: 'bus', color: '#ff8c1a', core: '#ff4f3a',
              form: { size: .95, sharp: .1, grain: .05, freq: .08, flow: .5, blob: .75, beat: 'rumble', hazard: true },
              hear: { weak: .35, strong: .65, labels: ['Bus'] } },
  motorbike:{ group: 'traffic', label: 'Motorbike', icon: 'motorbike', color: '#ffa21f', core: '#ff6a2a',
              form: { size: .85, sharp: .35, grain: .2, freq: .35, flow: .4, blob: .8, beat: 'rev' },
              hear: { weak: .30, strong: .60, labels: ['Motorcycle', 'Accelerating, revving, vroom', 'Engine starting', 'Race car, auto racing'] } },
  car:      { group: 'traffic', label: 'Car', icon: 'car', color: '#ffae2b', core: '#ffdc8a',
              form: { size: .75, sharp: .1, grain: .1, freq: .1, flow: .6, blob: .75, beat: 'engine' },
              hear: { weak: .35, strong: .65, labels: ['Car', 'Car passing by', 'Motor vehicle (road)', 'Traffic noise, roadway noise', 'Vehicle', 'Truck', 'Engine', 'Idling'],
                      yields: ['horn', 'truck', 'bus', 'train', 'motorbike', 'siren', 'skid', 'reverse'] } },
  bike:     { group: 'traffic', label: 'Bike bell', icon: 'bikebell', color: '#ffb21f', core: '#fff07a',
              form: { size: .6, sharp: .6, grain: 0, freq: .95, flow: 0, blob: .8, beat: 'ring', hazard: true },
              hear: { weak: .25, strong: .55, labels: ['Bicycle bell', 'Bicycle'] } },

  // ---- people: pink when someone needs you, blue for everyone else ----
  baby:     { group: 'people', label: 'Baby crying', icon: 'baby', color: '#ff5fa2', core: '#ffd1e6',
              form: { size: .85, sharp: .55, grain: .1, freq: .85, flow: .1, blob: .9, beat: 'cry', hazard: true },
              hear: { weak: .25, strong: .55, labels: ['Baby cry, infant cry', 'Crying, sobbing', 'Whimper', 'Wail, moan'] } },
  shout:    { group: 'people', label: 'Shouting', icon: 'shout', color: '#ff4fb8', core: '#ffc2ea',
              form: { size: .85, blob: .9, bars: true, beat: 'shout' },
              hear: { weak: .30, strong: .60, labels: ['Shout', 'Yell', 'Bellow'] } },
  name:     { group: 'people', label: 'Your name', icon: 'person', color: '#3d7bff', core: '#7ce8ff',
              form: { size: .7, blob: .8, bars: true, hazard: true } },
  speech:   { group: 'people', label: 'Talking', icon: 'speech', color: '#4f9dff', core: '#c2e4ff',
              form: { size: .55, blob: .7, bars: true },
              hear: { weak: .30, strong: .60, labels: ['Speech', 'Conversation', 'Narration, monologue', 'Whispering'] } },
  laugh:    { group: 'people', label: 'Laughter', icon: 'laugh', color: '#5aa8ff', core: '#fff1a8',   // a warm, happy core
              form: { size: .6, sharp: .15, grain: .3, freq: .75, flow: 0, blob: .8, beat: 'laugh' },
              hear: { weak: .30, strong: .60, labels: ['Laughter', 'Giggle', 'Snicker', 'Belly laugh', 'Chuckle, chortle', 'Baby laughter'] } },
  applause: { group: 'people', label: 'Applause', icon: 'clap', color: '#4a8cff', core: '#d4ecff',
              form: { size: .7, sharp: .4, grain: .85, freq: .9, flow: .2, blob: .7, beat: 'clap' },
              hear: { weak: .30, strong: .60, labels: ['Applause', 'Clapping', 'Hands', 'Finger snapping'] } },
  cheer:    { group: 'people', label: 'Cheering', icon: 'cheer', color: '#3f7dff', core: '#ffe38a',
              form: { size: .8, sharp: .3, grain: .5, freq: .6, flow: .3, blob: .8, beat: 'cheer' },
              hear: { weak: .30, strong: .60, labels: ['Cheering', 'Whoop'] } },
  sing:     { group: 'people', label: 'Singing', icon: 'mic', color: '#6f8dff', core: '#f0c8ff',
              form: { size: .6, blob: .8, bars: true, beat: 'sing' },
              hear: { weak: .30, strong: .60, labels: ['Singing', 'Choir', 'Child singing', 'Humming', 'Chant', 'Yodeling', 'Rapping', 'A capella', 'Vocal music'] } },
  whistle:  { group: 'people', label: 'Whistling', icon: 'whistle', color: '#4fb0ff', core: '#e6fbff',
              form: { size: .55, sharp: .5, grain: 0, freq: 1, flow: .6, blob: .75, beat: 'whistle' },
              hear: { weak: .30, strong: .60, labels: ['Whistling', 'Whistle'] } },
  cough:    { group: 'people', label: 'Coughing', icon: 'cough', color: '#5b9bff', core: '#cfe6ff',
              form: { size: .55, sharp: .6, grain: .5, freq: .5, flow: 0, blob: .75, beat: 'cough' },
              hear: { weak: .30, strong: .60, labels: ['Cough', 'Sneeze', 'Throat clearing'] } },
  steps:    { group: 'people', label: 'Footsteps', icon: 'steps', color: '#3fa0e8', core: '#bfe6ff',
              form: { size: .5, sharp: .3, grain: .3, freq: .35, flow: 0, blob: .7, beat: 'steps' },
              hear: { weak: .30, strong: .60, labels: ['Walk, footsteps', 'Run', 'Shuffle'] } },
  kids:     { group: 'people', label: 'Children playing', icon: 'kids', color: '#57b4ff', core: '#fff0a0', loop: true,
              form: { size: .6, blob: .75, bars: true, beat: 'kids' },
              hear: { weak: .30, strong: .60, labels: ['Children playing', 'Children shouting', 'Child speech, kid speaking'] } },

  // ---- home: teal ----
  doorbell: { group: 'home', label: 'Doorbell', icon: 'bell', color: '#14d2b9', core: '#8dffd9',
              form: { size: .6, sharp: 0, grain: .15, freq: .4, flow: 0, blob: .9, beat: 'chime' },
              hear: { weak: .20, strong: .50, labels: ['Doorbell', 'Ding-dong'] } },
  knock:    { group: 'home', label: 'Knocking', icon: 'door', color: '#22b8e0', core: '#aef0ff',
              form: { size: .55, sharp: .45, grain: .1, freq: .45, flow: 0, blob: .85, beat: 'knock' },
              hear: { weak: .20, strong: .50, labels: ['Knock'] } },
  door:     { group: 'home', label: 'Door', icon: 'dooropen', color: '#1fc4c9', core: '#b5fff4',
              form: { size: .6, sharp: .5, grain: .1, freq: .3, flow: 0, blob: .85, beat: 'creak' },
              hear: { weak: .25, strong: .55, labels: ['Door', 'Slam', 'Sliding door', 'Creak', 'Squeak', 'Cupboard open or close', 'Drawer open or close'] } },
  phone:    { group: 'home', label: 'Phone ringing', icon: 'phone', color: '#19cfe6', core: '#eaffff',
              form: { size: .65, sharp: .6, grain: 0, freq: .9, flow: .1, blob: .85, beat: 'ringring' },
              hear: { weak: .25, strong: .55, labels: ['Telephone', 'Telephone bell ringing', 'Ringtone', 'Telephone dialing, DTMF', 'Dial tone', 'Busy signal'] } },
  timer:    { group: 'home', label: 'Timer', icon: 'timer', color: '#2ee0b0', core: '#f0fff0',
              form: { size: .55, sharp: .85, grain: 0, freq: .95, flow: 0, blob: .85, beat: 'beeps' },
              hear: { weak: .30, strong: .60, labels: ['Beep, bleep', 'Microwave oven', 'Ding', 'Ping'], yields: ['fire', 'reverse', 'phone'] } },
  kettle:   { group: 'home', label: 'Kettle', icon: 'kettle', color: '#22d1a8', core: '#fff6d0',
              form: { size: .55, sharp: .25, grain: .55, freq: .75, flow: .6, blob: .75, beat: 'boil' },
              hear: { weak: .30, strong: .60, labels: ['Boiling', 'Steam', 'Steam whistle', 'Frying (food)', 'Sizzle'] } },
  tap:      { group: 'home', label: 'Running tap', icon: 'tap', color: '#1ec8e0', core: '#d8fbff',
              form: { size: .5, sharp: .1, grain: .7, freq: .8, flow: .5, blob: .7, beat: 'flow' },
              hear: { weak: .30, strong: .60, labels: ['Water tap, faucet', 'Sink (filling or washing)', 'Bathtub (filling or washing)', 'Pour', 'Fill (with liquid)', 'Drip', 'Trickle, dribble', 'Gush'] } },
  toilet:   { group: 'home', label: 'Toilet flush', icon: 'toilet', color: '#1fb8c8', core: '#c8f6ff',
              form: { size: .6, sharp: .15, grain: .5, freq: .4, flow: .5, blob: .75, beat: 'flush' },
              hear: { weak: .30, strong: .60, labels: ['Toilet flush'] } },
  dishes:   { group: 'home', label: 'Dishes', icon: 'dishes', color: '#26d0b8', core: '#ffffff',
              form: { size: .5, sharp: .8, grain: .6, freq: .95, flow: 0, blob: .7, beat: 'clink' },
              hear: { weak: .30, strong: .60, labels: ['Dishes, pots, and pans', 'Cutlery, silverware', 'Chink, clink', 'Clang', 'Chopping (food)', 'Clatter'] } },
  keys:     { group: 'home', label: 'Keys', icon: 'keys', color: '#2fd4a0', core: '#fff4b0',   // brass
              form: { size: .45, sharp: .9, grain: .75, freq: 1, flow: 0, blob: .7, beat: 'jingle' },
              hear: { weak: .30, strong: .60, labels: ['Keys jangling', 'Jingle, tinkle', 'Coin (dropping)'] } },
  typing:   { group: 'home', label: 'Typing', icon: 'keyboard', color: '#20bfae', core: '#c0fff0', loop: true,
              form: { size: .4, sharp: .5, grain: .8, freq: .9, flow: 0, blob: .65, beat: 'type' },
              hear: { weak: .30, strong: .60, labels: ['Typing', 'Computer keyboard', 'Typewriter', 'Clicking'] } },

  // ---- nature: green ----
  rain:     { group: 'nature', label: 'Rain', icon: 'rain', color: '#3ad07a', core: '#bdf5ff', loop: true,
              form: { size: .6, sharp: .1, grain: .9, freq: .85, flow: .3, blob: .7, beat: 'patter' },
              hear: { weak: .30, strong: .60, labels: ['Rain', 'Raindrop', 'Rain on surface'] } },
  stream:   { group: 'nature', label: 'Stream', icon: 'stream', color: '#34d399', core: '#c8fff0', loop: true,
              form: { size: .55, sharp: 0, grain: .4, freq: .7, flow: .7, blob: .7, beat: 'flow' },
              hear: { weak: .30, strong: .60, labels: ['Stream', 'Waterfall', 'Water', 'Gurgling', 'Splash, splatter', 'Liquid'], yields: ['rain', 'tap', 'ocean', 'toilet'] } },
  ocean:    { group: 'nature', label: 'Waves', icon: 'wave', color: '#1fc38e', core: '#d8fff4', loop: true,
              form: { size: .75, sharp: 0, grain: .3, freq: .15, flow: .8, blob: .75, beat: 'swell' },
              hear: { weak: .30, strong: .60, labels: ['Ocean', 'Waves, surf'] } },
  wind:     { group: 'nature', label: 'Wind', icon: 'wind', color: '#7ad96a', core: '#eaffd8', loop: true,
              form: { size: .6, sharp: 0, grain: .35, freq: .2, flow: .85, blob: .7, beat: 'gust' },
              hear: { weak: .30, strong: .60, labels: ['Wind', 'Wind noise (microphone)', 'Whoosh, swoosh, swish'] } },
  bird:     { group: 'nature', label: 'Birdsong', icon: 'bird', color: '#6ee05a', core: '#fff59a', loop: true,
              form: { size: .45, sharp: .55, grain: .3, freq: 1, flow: 0, blob: .7, beat: 'chirp' },
              hear: { weak: .30, strong: .60, labels: ['Bird', 'Bird vocalization, bird call, bird song', 'Chirp, tweet', 'Squawk', 'Pigeon, dove', 'Coo', 'Crow', 'Caw', 'Owl', 'Hoot', 'Bird flight, flapping wings', 'Fowl', 'Chicken, rooster', 'Crowing, cock-a-doodle-doo', 'Duck', 'Quack'] } },
  crickets: { group: 'nature', label: 'Crickets', icon: 'cricket', color: '#8fdc3a', core: '#f4ffb0', loop: true,
              form: { size: .4, sharp: .7, grain: .75, freq: 1, flow: 0, blob: .65, beat: 'pulse' },
              hear: { weak: .30, strong: .60, labels: ['Cricket', 'Insect', 'Buzz', 'Bee, wasp, etc.', 'Mosquito', 'Fly, housefly', 'Frog', 'Croak'] } },
  dog:      { group: 'nature', label: 'Dog barking', icon: 'dog', color: '#b8dc2e', core: '#fff2a0',   // the warmest green: a bark wants attention
              form: { size: .75, sharp: .7, grain: .05, freq: .4, flow: 0, blob: .9, beat: 'bark' },
              hear: { weak: .30, strong: .60, labels: ['Dog', 'Bark', 'Yip', 'Howl', 'Bow-wow', 'Growling', 'Whimper (dog)', 'Canidae, dogs, wolves'] } },
  cat:      { group: 'nature', label: 'Cat', icon: 'cat', color: '#7fd65c', core: '#ffe6f4',
              form: { size: .5, sharp: .2, grain: .1, freq: .8, flow: .2, blob: .8, beat: 'meow' },
              hear: { weak: .30, strong: .60, labels: ['Cat', 'Meow', 'Purr', 'Hiss', 'Caterwaul'] } },
  crackle:  { group: 'nature', label: 'Crackling', icon: 'leaf', color: '#f5c542', core: '#fff2b0', loop: true,   // gold: fire
              form: { size: .5, sharp: .5, grain: .95, freq: .6, flow: 0, blob: .3, beat: 'crackle' },
              hear: { weak: .30, strong: .60, labels: ['Crackle', 'Fire', 'Rustling leaves', 'Crumpling, crinkling'] } },

  // ---- low tones, music, ambience and the unknown: purple ----
  music:    { group: 'low', label: 'Music', icon: 'music', color: '#9b5cff', core: '#ff8ce0', loop: true,
              form: { size: .6, sharp: .2, grain: .15, freq: .5, flow: .3, blob: .8, beat: 'melody' },
              hear: { weak: .35, strong: .60, labels: ['Music', 'Musical instrument', 'Plucked string instrument', 'Guitar', 'Electric guitar', 'Bass guitar', 'Acoustic guitar',
                'Keyboard (musical)', 'Piano', 'Electric piano', 'Organ', 'Synthesizer', 'Percussion', 'Drum kit', 'Drum machine', 'Drum', 'Orchestra', 'Violin, fiddle', 'Flute',
                'Saxophone', 'Trumpet', 'Pop music', 'Rock music', 'Hip hop music', 'Electronic music', 'Classical music', 'Jazz', 'Dance music', 'Song', 'Background music',
                'Theme music', 'Soundtrack music', 'Ambient music'] } },
  ambient:  { group: 'low', label: 'Crowd', icon: 'crowd', color: '#7b5cff', core: '#e08cff', loop: true,
              form: { size: .4, freq: .15, beat: 'breathe', omni: true },
              hear: { weak: .35, strong: .60, labels: ['Hubbub, speech noise, speech babble', 'Crowd', 'Chatter'], yields: ['cheer', 'applause'] } },
  thunder:  { group: 'low', label: 'Thunder', icon: 'thunder', color: '#8a5cff', core: '#fff3a0', loop: true,   // a flash of lightning at the core
              form: { size: .95, sharp: .3, grain: .25, freq: .05, flow: .6, blob: .85, beat: 'thunder' },
              hear: { weak: .30, strong: .60, labels: ['Thunder', 'Thunderstorm', 'Rumble'] } },
  plane:    { group: 'low', label: 'Plane', icon: 'plane', color: '#7b6bff', core: '#d8d0ff',
              form: { size: .85, sharp: 0, grain: .1, freq: .05, flow: .8, blob: .75, beat: 'drone' },
              hear: { weak: .30, strong: .60, labels: ['Aircraft', 'Aircraft engine', 'Jet engine', 'Propeller, airscrew', 'Helicopter', 'Fixed-wing aircraft, airplane'] } },
  vacuum:   { group: 'low', label: 'Vacuum', icon: 'vacuum', color: '#9a6bff', core: '#e0d0ff', loop: true,
              form: { size: .6, sharp: .15, grain: .2, freq: .55, flow: .75, blob: .75, beat: 'hum' },
              hear: { weak: .30, strong: .60, labels: ['Vacuum cleaner', 'Hair dryer', 'Blender', 'Electric shaver, electric razor', 'Mechanical fan', 'Air conditioning', 'Whir', 'Drill', 'Power tool'] } },
  washer:   { group: 'low', label: 'Washing machine', icon: 'washer', color: '#8c62f0', core: '#d6c4ff', loop: true,   // YAMNet has no label for it
              form: { size: .5, sharp: .1, grain: .15, freq: .2, flow: .7, blob: .75, beat: 'slosh' } },
  snore:    { group: 'low', label: 'Snoring', icon: 'snore', color: '#8e7bff', core: '#c8c0ff', loop: true,
              form: { size: .5, sharp: 0, grain: .1, freq: .1, flow: .6, blob: .75, beat: 'snore' },
              hear: { weak: .30, strong: .60, labels: ['Snoring', 'Snort'] } },
  clock:    { group: 'low', label: 'Clock ticking', icon: 'clock', color: '#a07cff', core: '#ffe9b0', loop: true,
              form: { size: .35, sharp: .6, grain: .5, freq: .7, flow: 0, blob: .65, beat: 'tick' },
              hear: { weak: .30, strong: .60, labels: ['Clock', 'Tick', 'Tick-tock'] } },
  behind:   { group: 'low', label: 'Unknown', icon: 'ear', color: '#a66bff', core: '#6b7bff',   // used by the stereo mic detector
              form: { size: .6, sharp: .2, grain: .3, freq: .5, flow: .2, blob: .8, beat: 'breathe' } },
};

// Fill in label/icon/colour/form from the catalogue unless the message overrides them.
export function resolve(a) {
  const s = SOUNDS[a.sound] || SOUNDS.behind;
  return { ...a, label: a.label || s.label, icon: a.icon || s.icon, color: a.color || s.color, core: a.core || s.core, form: s.form };
}

// Angles everywhere: degrees, 0 = ahead, +90 = right, -90 = left, 180 = behind.
export const wrap = (d) => { d = ((d + 180) % 360 + 360) % 360 - 180; return d === -180 ? 180 : d; };

export function dirWord(d) {
  d = wrap(d); const a = Math.abs(d), side = d > 0 ? 'right' : 'left';
  if (a <= 22.5) return 'ahead';
  if (a <= 67.5) return `ahead-${side}`;
  if (a <= 112.5) return side;
  if (a <= 157.5) return `behind-${side}`;
  return 'behind';
}

