// Every colour, size and string of the block. Sizes are points on a 402 pt wide screen, scaled by width / 402.

export const STAGE_WIDTH = 402;

// Replica step (owner's fidelity gate): the source's own motion (an orb drops in, the lid tips) and nothing added.
// Off after the lead accepts the compare sheets.
export const REPLICA = false;

export const COLORS = {
  // Measured from the source frame f021.
  bg: '#F4FDFE', // the source's page fades from near white to pale blue
  bgBottom: '#E3F0FB',
  card: '#F7FCFF', // the source's cards are a faint blue-white
  label: '#3A3A3F', // picker labels: dark grey in the source
  secondary: '#8F9298', // entry tags and time
  cardShadow: 'rgba(60, 100, 160, 0.08)',
  ink: '#16233A',
  mute: 'rgba(22, 35, 58, 0.55)',
  faint: 'rgba(22, 35, 58, 0.35)',
  glass: 'rgba(255, 255, 255, 0.38)',
  glassBack: 'rgba(214, 230, 246, 0.55)',
  glassEdge: 'rgba(255, 255, 255, 0.95)',
  glassShade: 'rgba(110, 150, 200, 0.28)',
  floorShadow: 'rgba(70, 110, 170, 0.18)',
  lid: ['#8E949C', '#D8DDE3', '#F7F8FA', '#B4BAC2', '#7C828A'],
  lidStops: [0, 0.2, 0.42, 0.75, 1],
  lidRidge: 'rgba(60, 66, 74, 0.25)',
  print: '#0E0E10',
  danger: '#F4545E',
  dangerInk: '#FFFFFF',
  dotsBg: '#EEF1F5',
  sheetLine: 'rgba(22, 35, 58, 0.1)',
  dotsInk: '#5B616B',
  aim: 'rgba(22, 35, 58, 0.18)',
};

// Moods, from awful to great, as in the source: base, light, deep.
export const MOODS = [
  { name: 'Awful', base: '#A24CE6', light: '#E0B0FF', deep: '#6A22A8', tags: 'Angry, Overwhelmed' },
  { name: 'Bad', base: '#5E7DF6', light: '#BCCBFF', deep: '#2F4CC2', tags: 'Disgusted, Friends' },
  { name: 'Neutral', base: '#3FC6E6', light: '#B4F0FF', deep: '#1689A8', tags: 'Work, Tired' },
  { name: 'Good', base: '#57D27A', light: '#C2F5CF', deep: '#22964A', tags: 'Calm, Partner' },
  { name: 'Great', base: '#F4545E', light: '#FFC2B8', deep: '#B8243A', tags: 'Family, Sunshine' },
];

// Spacing, measured on the source: its cards sit 18 pt apart with a 12 pt inner pad, the avatar 25 pt from a card's
// top. Everything the upgrade adds (the month and count label) sits on the same 4 pt steps.
export const SPACE = { xs: 4, sm: 8, cardPad: 12, lg: 16, cardGap: 18, avatarTop: 25 } as const;

export const LAYOUT = {
  side: 16,
  header: 44,
  icon: 44,
  lidWidth: 192, // assets/lid.png, 576 × 110 px
  lidHeight: 36.7,
  lidOverlap: 10, // the lid sits this far down over the neck
  jarTop: 47, // the mouth, below the header
  jarImageW: 280 * 1.01, // assets/jar.png, 840 × 1010 px; its mouth's top centre is at (420, 30) px; matched by overlay
  jarImageH: 326.7, // the body's bottom 10 pt higher, as in the overlay
  jarImageMouthY: 10,
  jarWidth: 276 * 1.01,
  jarHeight: 300, // mouth to the bottom of the glass base, as in the reference (taller than wide)
  base: 2, // the pile sits on the inside of the thick glass base, as low as the source’s (its bottom row clears it)
  wall: 6, // the source’s outer orbs touch the glass
  shoulder: 70,
  corner: 46,
  mouthHalf: 84 * 1.01,
  printSize: 30, // the source's 'US?' print: bold black, white outline
  printStroke: 5,
  printY: 0.724, // baseline, of the jar's height below the mouth
  labelY: 0.12, // the month and count label, just under the jar's shoulder (upgrade) // of the jar's height below the mouth
  pickerTop: 400, // below the header
  pickerHeight: 96,
  pickerOrb: 20.2, // radius (the source's picker orbs are 40 pt across)
  pickerOrbY: 41, // from the card's top
  slotGap: 67.33, // between picker orb centres (the five sit inside the card's padding)
  slotDx: 0.8, // the row's centre, right of the screen's
  slotLabelH: 63, // the label box below each orb (bottom-aligned)
  labelSize: 13,
  cardRadius: 16,
  entriesTop: 512, // below the header
  entryHeight: 106, // the source's entry cards are 106 pt tall
  sectionH: 26, // upgrade: the 'Today' header above the list
  rowHeight: 68, // upgrade: one grouped list, denser rows
  hairlineInset: 70, // separators start at the text, not the edge
  entryGap: 18, // SPACE.cardGap
  entryOrb: 22,
  orb: 25, // radius in the jar (the source's orbs are ~50 pt across)
};

// The source's pile: purple, blue and cyan spread evenly, 3 green (bottom left, middle, top), 1 coral near the centre.
// Index order is the seeded drop's order (rows from the bottom, left to right).
export const PILE_MOODS = [3, 0, 1, 2, 0, 1, 2, 4, 0, 1, 2, 0, 3, 2, 0, 3];

export const COPY = {
  today: 'Today',
  entriesWord: 'entries',
  month: 'OCTOBER',
  replicaEntry: { tags: 'Ashamed, Partner', time: '4:21 PM' }, // the source's new entry after the drop
  print: 'US?',
  back: 'Back',
  calendar: 'Calendar',
  more: 'More',
  undo: 'Undo',
  add: (mood: string) => `Add ${mood}`,
  entries: [
    { mood: 1, tags: 'Disgusted, Friends', time: '4:20 PM' },
    { mood: 2, tags: 'Work, Tired', time: '1:05 PM' },
  ],
  now: 'Just now',
};

// The source's pile at rest, measured on the reference frame: [x, y] px on the 1206-px-wide screen, the mood, and
// the face's turn in degrees (counter-clockwise), each fitted by matching the orb image to the frame.
// assets/pile-plate.png: the reference frame's jar below the lid (y 490–1440 px, x 150–1056, feathered), pile and print included.
export const REPLICA_PLATE = { y: 490, h: 950 }; // pile-plate-b.png: the same band of the source after its cut
// The shipped jar: assets/jar-plate.png (the source's jar with its pile and the creator's caption taken out) and
// pile-atlas.png (each resting orb as the frame shows it). The creator's 'US?' never ships.
export const REPLICA_CUT = 2.0; // s: the source cuts to its fuller jar (ref b)
export const REPLICA_PILE: readonly (readonly [number, number, number, number])[] = [
  [484, 899, 3, 0], [634, 885, 1, -4], [942, 884, 2, 128], [311, 919, 0, -84], [554, 1019, 4, 0], [717, 1009, 2, 116],
  [867, 1015, 0, -90], [272, 1061, 0, -16], [418, 1024, 0, 44], [632, 1154, 0, -40], [295, 1205, 3, 12], [489, 1145, 1, -4],
  [785, 1140, 1, 100], [937, 1146, 1, -45], [415, 1279, 0, -75], [566, 1276, 1, -90], [711, 1281, 0, 0], [852, 1270, 2, -8],
];
