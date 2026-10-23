// Every colour, size and string of the block. Sizes are points on a 402 pt wide screen, scaled by width / 402.

export const STAGE_WIDTH = 402;

export const COLORS = {
  // Measured from the source (rows 0, 500, 600, 700, 1000, 1280 of f010): black to 39 %, a soft horizon, indigo.
  sky: ['#050409', '#050409', '#111226', '#1C1C3E', '#2A2B6C', '#262767'],
  skyStops: [0, 0.39, 0.47, 0.55, 0.78, 1],
  skyTop: '#050409',
  skyMid: '#050409',
  horizon: '#1C1D42',
  ground: '#2A2B6C',
  groundConnected: '#0E3D3A',
  groundEnded: '#3A1328',
  grain: 0.07,
  // Neon handset: hot-pink face over a translucent violet shell.
  ring: '#FF1F8E',
  ringDeep: '#C40F6A',
  connected: '#2BE36A',
  connectedDeep: '#12A548',
  off: '#5B2348',
  offDeep: '#3A1630',
  shell: 'rgba(92, 52, 178, 0.78)',
  shellEdge: 'rgba(164, 128, 255, 0.55)',
  grille: 'rgba(60, 0, 40, 0.55)',
  bloom: '#FFFFFF',
  cordBack: '#2C2178',
  cordFront: '#7448E6',
  cordEdge: '#FF4FAE',
  replicaCord: '#302A88',
  replicaRim: '#A5237D',
  replicaRimLeft: '#4C40B4', // the tube's lit left side
  ink: '#F4F2FF',
  mute: 'rgba(244, 242, 255, 0.58)',
  decline: '#FF1450',
  accept: '#22E04A',
  glyph: '#FFFFFF',
  label: 'rgba(244, 242, 255, 0.62)',
};

export const HANDSET = {
  // assets/handset.png (480 × 600 px, Codex v3): cord anchor at (200, 20) px, body 203 × 560 px.
  // Scaled so the body is the source's 195 pt (0.348 pt/px). Local frame: origin at the cord's anchor.
  imageX: -200 * 0.348,
  imageY: -20 * 0.348,
  imageW: 480 * 0.348,
  imageH: 600 * 0.348,
  earX: 12, // earpiece cup centre
  earY: 23,
  mouthX: 12, // mouthpiece cup centre
  mouthY: 172,
  comX: 2, // centre of mass, for the pivot
  comY: 98,
  bloom: 20, // the reference's soft white blooms on the cups
  bloomX: 14,
  bloomY: -14,
  hit: 190,
  restTilt: -0.33, // the source's handset hangs leaning, mouthpiece out to the right
  offOpacity: 0.42, // declined / ended: the neon is off
};

export const CORD = {
  anchorY: -12,
  replicaAnchorDx: -26, // replica: the source's cord hangs from 26 pt left of the screen's centre
  replicaTurns: 9.7, // replica: the source's cord is a flat wave, 37 pt a period at rest...
  replicaWave: 7, // ...7 pt high either side,
  replicaPhase: -3.98, // ...in this phase at the top (rad),
  replicaRoll: [[0, 0], [1.0, -0.39], [1.875, -0.22], [2.9, -0.39], [4, 0]] as const, // ...rolling by this (s, rad) over the swing,
  replicaWidth: 7, // ...a 7 pt tube, deep blue with a pink rim on its right
  replicaRimDx: 2.7,
  restRatio: 0.335, // the cord's end at rest, of the height (the earpiece's top at 471 source px)
  links: 14,
  turns: 20,
  wire: 90, // length of wire in one turn: a 14 pt wave at a 25 pt pitch, as in the source
  width: 2.6, // a fine tube, as in the source at sheet scale
  edge: 1.1,
  maxRadius: 9, // a tight, fine wave
  samples: 8, // per half-turn
};

// Spacing for what the upgrade adds (the source shows no UI, so the scale is the 4 pt grid of its iOS frame).
export const SPACE = { xs: 4, sm: 8, md: 12, lg: 16, xl: 24, xxl: 32, xxxl: 40 } as const;

export const LAYOUT = {
  side: 32, // SPACE.xxl
  nameTopRatio: 0.655,
  copyTopRatio: 0.645, // upgrade: the caller block, below the lowest reach of the source’s swing (0.632) and clear of the in-call pill
  nameSize: 34,
  nameLine: 40,
  statusSize: 17,
  statusLine: 22,
  gap: 8, // SPACE.sm
  buttonsBottom: 40, // SPACE.xxxl
  button: 64,
  buttonGap: 96, // from the centre to each button's centre
  labelSize: 13,
  labelGap: 8, // SPACE.sm (was 10, off the grid)
  glyph: 26,
};

export const COPY = {
  meta: 'mobile · Stockholm',
  controls: [
    { name: 'mute', label: 'Mute', symbol: 'mic.slash.fill' },
    { name: 'keypad', label: 'Keypad', symbol: 'circle.grid.3x3.fill' },
    { name: 'speaker', label: 'Speaker', symbol: 'speaker.wave.2.fill' },
  ],
  name: 'Mara Lindqvist',
  ringing: 'Incoming call · lift to answer',
  declined: 'Call declined',
  ended: 'Call ended',
  decline: 'Decline',
  accept: 'Accept',
  end: 'End',
  a11yHandset: 'Handset',
  a11yHint: 'Swipe up to answer, swipe down to decline',
};

// Replica step (owner's fidelity gate): only what the source shows — horizon, coil, handset, swing. No caller, no
// buttons, no ring. Off after the lead accepts the compare sheets.
export const REPLICA = false;

// Upgrade: Liquid Glass controls and the hue field (owner's Craft bar).
export const GLASS = {
  blur: 18, // fixed backdrop blur radius
  decline: 'rgba(255, 20, 80, 0.55)',
  accept: 'rgba(34, 224, 74, 0.5)',
  neutral: 'rgba(255, 255, 255, 0.12)',
  edgeTop: 'rgba(255, 255, 255, 0.75)',
  edgeBottom: 'rgba(255, 255, 255, 0.08)',
  pillY: -96, // the control pill sits this far above the End button
  pillH: 64,
  field: 300, // hue glow field radius around the handset
  fieldAlpha: 0.35,
};
