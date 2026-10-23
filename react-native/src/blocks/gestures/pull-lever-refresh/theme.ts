import type { TextStyle } from 'react-native';

/**
 * Every colour, size and type style of the block: a lever espresso machine seen from the front, the eye a little
 * above the cup's rim, shot like a product photo. A matte black body carries the pressure gauge and the shot timer; a polished
 * steel group head stands off it with the lever on top (a brushed steel arm, a turned walnut handle), the
 * portafilter below, and a white porcelain cup on the drip tray's grate. One key light from the top left; the only
 * colours are the coffee, its crema, the gauge's needle and the warm light of a finished shot.
 *
 * Material colours are linear RGB triples (0–1) because the shaders light them and then encode to sRGB; UI colours
 * are strings.
 */
export const MATERIAL = {
  steel: [0.6, 0.6, 0.6] as const, // brushed: the lever arm, the drip tray
  chrome: [0.86, 0.86, 0.87] as const, // polished: the group head, the portafilter, the bezel
  enamel: [0.022, 0.021, 0.02] as const, // the body: black powder coat, bead-blasted
  walnut: [0.1, 0.04, 0.02] as const, // the handles, oiled and lacquered
  walnutDark: [0.032, 0.012, 0.006] as const, // its late wood
  porcelain: [0.86, 0.84, 0.8] as const, // the cup: warm white glaze
  dial: [0.012, 0.012, 0.012] as const, // the gauge's face
  rubber: [0.012, 0.012, 0.012] as const,
  coffee: [0.02, 0.0065, 0.0025] as const, // the shot, under its crema
  crema: [0.42, 0.16, 0.05] as const, // hazelnut
  cremaLight: [0.66, 0.38, 0.17] as const, // where it is freshest
  tiger: [0.16, 0.05, 0.014] as const, // the darker flecks of a good crema
  needle: [0.86, 0.22, 0.05] as const, // the gauge's needle: the one bright accent
  zone: [0.95, 0.5, 0.16] as const, // the brew zone on the dial (8–10 bar), lit at 9
  warm: [1.0, 0.62, 0.32] as const, // the light of a finished shot
  // The studio: a warm near-black sweep, a little lighter behind the machine.
  panelLow: [0.003, 0.0028, 0.0026] as const,
  panelHigh: [0.024, 0.021, 0.018] as const,
  floor: [0.016, 0.014, 0.012] as const,
} as const;

export const COLOR = {
  panel: '#0B0A09', // behind the canvas, never seen once it draws
  ink: '#F4F1EC',
  mute: '#948C83',
  eyebrow: '#C08A5A', // crema
  dialInk: '#E9E4DC', // the gauge's numerals, printed
  dialMute: '#8D867E',
  etch: '#A49C93', // laser-etched into the powder coat
  digit: '#F1ECE4', // printed on the timer's drums
} as const;

/** The key light and the studio, in the machine's units (origin at the lever's pivot, z toward the viewer). */
export const LIGHT = {
  key: [-620, -980, 1000] as const, // a softbox above and to the left, in front
  ambient: 0.06,
  diffuse: 0.95,
  grain: 0.018, // film grain on the sweep
  shadow: [16, 22] as const, // how far the machine's shadow falls on the sweep behind it
} as const;

/**
 * The machine in its own units, origin at the lever's pivot, y down. One camera for every part: a front elevation
 * with the eye a few degrees above the cup's rim, so a circle of radius r shows as a thin ellipse r × r·tilt (the
 * rim of the cup, the tops of the turned parts) and the grate's top shows as a narrow strip.
 * The screen fits the lever's whole reach (`REACH` in lever-look.ts) inside the edges and centres the machine at rest.
 */
export const GEO = {
  tilt: 0.12,
  lever: {
    boss: 15, // the round end round the pivot
    hub: 8.5, // the polished cap on the pivot pin
    half0: 6.2, // the arm tapers from the boss…
    half1: 4.4, // …to the ferrule
    bevel: 2.2,
    fillet: 10,
    ferrule: { from: 90, to: 100, half: 6.4 }, // polished steel collar under the handle
    handle: { from: 99, to: 160, r0: 6.2, r1: 10.6, swell: 0.6 }, // turned walnut, widest at 60 % of its length
  },
  yoke: { half: 9, top: -4, bottom: 18 }, // the bracket the lever pivots on
  group: {
    cap: { top: 14, bottom: 24, r: 22 },
    body: { top: 22, bottom: 116, r: 30 },
    groove: 46, // a turned groove round the cylinder
    flange: { top: 110, bottom: 128, r: 37 }, // the bayonet ring the portafilter locks into
  },
  // The boiler's feed to the group: a chrome pipe out of the body's side with a hex union nut halfway.
  neck: { from: -58, to: -28, y: 56, r: 5.2, nut: { from: -49, to: -39, r: 8 } },
  pf: {
    bowl: { top: 127, bottom: 147, r: 30 },
    stem: { top: 145, bottom: 154, r: 7.5 },
    spout: { x: 8.5, spread: 3.5, tip: 166, r: 2.9, bore: 1.9 }, // two spouts, a little apart: the handle turns toward us
    arm: { from: 26, to: 62, y: 134, half: 4.4 },
    wood: { from: 60, to: 140, r0: 6.4, r1: 8.4 },
    tilt: 6, // degrees the handle dips toward its end
  },
  cup: {
    rim: 230, // the mouth's centre line
    base: 280, // where the foot stands on the grate (the middle of its depth)
    r: 37, // the mouth
    belly: 36, // just under the rim
    bottom: 26, // where the wall turns under
    foot: 20,
    wall: 2.8,
    full: 2.5, // the shot's surface stands this far under the rim when it is done: a narrow lens of crema shows
    ear: { x: 44, y: 246, rx: 13, ry: 12, hole: 0.55 },
  },
  // The grate's top is a strip (its depth × tilt) with the cup's foot in its middle; the front face hangs below it.
  // Its left end runs in behind the body.
  tray: { left: -72, right: 100, top: 274, face: 286, bottom: 310, radius: 3, pitch: 4, bar: 2.4 },
  body: { left: -152, right: -58, top: -124, bottom: 310, radius: 14, chamfer: 2.4 },
  feet: { y: 310, height: 6, at: [-140, -70, 88] as const, half: 9 },
  floor: 316,
  // The gauge: a thin polished bezel; from the outside in, the brew zone's track, the ticks, the numerals, the hub.
  gauge: { x: -105, y: -44, bezel: 36.6, dial: 34, sweep: 270, max: 12, ticks: 30, zone: 31.8, numerals: 19.6, unit: 10, pin: -0.9 },
  // The shot timer: two drums behind a window, and over it one row (centred `row` above the window) with the etched
  // label at the window's left inset and the pilot lamp at its right inset.
  timer: { x: -105, y: 252, width: 58, height: 40, radius: 9, drum: 21, gap: 2, row: 10, inset: 4, lamp: 3 },
  fingerAt: 136, // the finger holds the handle here: its travel maps to the lever's angle at this radius
} as const;

/** Spacing scale, 4 pt base. */
export const SPACE = { xs: 4, sm: 8, md: 12, lg: 16, xl: 24, xxl: 32 } as const;

export const LAYOUT = {
  gutter: SPACE.xl, // screen edge to the copy
  edge: SPACE.lg, // screen edge to the machine, on both sides, over the lever's whole travel
  maxScale: 1.3,
  copyTop: SPACE.xl, // safe area to the eyebrow
  copyGap: SPACE.sm, // eyebrow to title
  subGap: SPACE.xs, // title to subline
  machineGap: SPACE.lg, // copy to the lever's highest point, at the most
  bottomGap: SPACE.lg, // floor to the safe area, at the least
} as const;

const TABULAR: TextStyle['fontVariant'] = ['tabular-nums'];

export const TYPE = {
  eyebrow: { fontSize: 12, lineHeight: 16, fontWeight: '600' as const, letterSpacing: 1.8 },
  title: { fontSize: 30, lineHeight: 36, fontWeight: '700' as const, letterSpacing: -0.6 },
  sub: { fontSize: 15, lineHeight: 20, fontWeight: '400' as const, letterSpacing: -0.1, fontVariant: TABULAR },
  numeral: { fontSize: 8, lineHeight: 10, fontWeight: '600' as const, fontVariant: TABULAR },
  unit: { fontSize: 6, lineHeight: 8, fontWeight: '600' as const, letterSpacing: 1 },
  label: { fontSize: 8, lineHeight: 12, fontWeight: '600' as const, letterSpacing: 1.6 },
  digit: { fontSize: 22, lineHeight: 26, fontWeight: '600' as const, fontVariant: TABULAR },
  maxScale: 1.2, // Dynamic Type may grow the copy this far
} as const;
