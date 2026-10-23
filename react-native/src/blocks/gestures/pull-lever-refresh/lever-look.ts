/**
 * Where the machine's parts sit, and how its light, its coffee and its gauge follow the mechanism. The functions
 * are worklets: the screen derives every frame's uniforms from the machine's values with them, and they are plain
 * math, so the same numbers can be rendered off the device.
 */
import { BREW, PUMP, STEAM, TIMING } from './motion';
import { GEO, LAYOUT, TYPE } from './theme';

const RAD = Math.PI / 180;
const H = GEO.lever.handle;
const C = GEO.cup;

/** The handle's radius at `s` along the lever (from the pivot): a turned walnut grip, widest past its middle. */
export function handleRadius(s: number): number {
  'worklet';
  // Swells from the ferrule to its widest, then closes in an egg-shaped end: the same profile as the shader's.
  const u = Math.min(Math.max((s - H.from) / (H.to - H.from), 0), 1);
  if (u <= H.swell) return H.r0 + (H.r1 - H.r0) * Math.sin((Math.PI / 2) * (u / H.swell));
  const v = (u - H.swell) / (1 - H.swell);
  return H.r1 * Math.sqrt(Math.max(1 - Math.pow(v, 2.4), 0));
}

/** The top of the handle, above the pivot, with the lever at `deg` (screen angle). */
function handleTop(deg: number): number {
  const c = Math.cos(deg * RAD);
  const s = Math.sin(deg * RAD);
  let up = 0;
  for (let t = H.from; t <= H.to; t += 1) up = Math.max(up, -t * s + handleRadius(t) * Math.abs(c));
  return up;
}

/**
 * How far the machine reaches from the lever's pivot, in its own units, over the lever's whole travel (both stops
 * and the rubber band past them): left, the body; right, the handle at the bottom of a stroke; up, the handle
 * pushed past its upper stop; down, the floor's contact shadow. Nothing may cross the screen's margins.
 */
function machineReach() {
  let right: number = GEO.tray.right;
  let up: number = -GEO.body.top;
  for (let a = PUMP.rest - PUMP.overUp; a <= PUMP.bottom + PUMP.overDown; a += 0.5) {
    const c = Math.cos(a * RAD);
    for (let t = H.from; t <= H.to; t += 1) right = Math.max(right, t * c + handleRadius(t) * Math.abs(Math.sin(a * RAD)));
    up = Math.max(up, handleTop(a));
  }
  return { left: -GEO.body.left, right, up, down: GEO.floor + 6 };
}
export const REACH = machineReach();

/**
 * The machine as it stands at rest, which is what the eye centres: from the body's left side to the end of the
 * portafilter's handle, and from the top of the lever's handle to the floor.
 */
export const SILHOUETTE = {
  left: -GEO.body.left,
  right: Math.max(GEO.tray.right, GEO.pf.wood.to),
  up: handleTop(PUMP.rest),
  down: GEO.floor,
};

/** The copy's height: eyebrow, title, sub line. */
export const COPY_HEIGHT = TYPE.eyebrow.lineHeight + LAYOUT.copyGap + TYPE.title.lineHeight + LAYOUT.subGap + TYPE.sub.lineHeight;

/**
 * Where the copy and the machine go on a screen: the copy under the Dynamic Island; the machine at rest centred
 * across the screen and between the copy and the bottom safe area, as large as it can be while the lever's whole
 * travel stays inside the side margins and clear of the copy. The pivot lands on whole points.
 */
export function machineLayout(width: number, height: number, insets: { top: number; bottom: number; left: number; right: number }) {
  const copyTop = insets.top + LAYOUT.copyTop;
  const top = copyTop + COPY_HEIGHT;
  const bottom = height - insets.bottom;
  const left = insets.left + LAYOUT.edge;
  const right = width - insets.right - LAYOUT.edge;
  const midX = (left + right) / 2;
  const half = (right - left) / 2 - 0.5; // room for the pivot's rounding to whole points
  // The silhouette's middle, as an offset from the pivot.
  const cx = (SILHOUETTE.right - SILHOUETTE.left) / 2;
  const cy = (SILHOUETTE.down - SILHOUETTE.up) / 2;
  const scale = Math.min(
    LAYOUT.maxScale,
    half / (REACH.left + cx),
    half / (REACH.right - cx),
    (bottom - top - LAYOUT.machineGap - LAYOUT.bottomGap) / (REACH.up + REACH.down),
  );
  const px = Math.round(midX - scale * cx);
  // Equal space over the handle and under the floor; then kept clear of the copy and the safe area.
  let py = (top + bottom) / 2 - scale * cy;
  py = Math.max(py, top + LAYOUT.machineGap + scale * REACH.up);
  py = Math.min(py, bottom - LAYOUT.bottomGap - scale * REACH.down);
  return { copyTop, scale, px, py: Math.round(py) };
}

/** The gauge's face (numerals over it) and the shot timer's window, in the machine's units. */
export const GAUGE_BOX = {
  x: GEO.gauge.x - GEO.gauge.bezel,
  y: GEO.gauge.y - GEO.gauge.bezel,
  size: 2 * GEO.gauge.bezel,
};
const T = GEO.timer;
export const TIMER_BOX = {
  x: T.x - T.width / 2,
  y: T.y - T.height / 2,
  width: T.width,
  height: T.height,
};
export const DRUM = { width: T.drum, height: T.height, gap: T.gap };
/**
 * The etched label's text box, from the window's corner: its line centred on the row, its S at the left inset (the
 * box starts half a unit sooner for the letter's side bearing, so the label and the lamp sit equally far in).
 */
export const TIMER_LABEL = { x: T.inset - 0.5, y: -T.row - TYPE.label.lineHeight / 2 };
/** The pilot lamp, in the machine's units: on the same row, its right edge at the window's right inset. */
export const LAMP_AT = { x: T.x + T.width / 2 - T.inset - T.lamp, y: T.y - T.height / 2 - T.row, r: T.lamp };

/** A pressure's place on the dial, in radians (screen angles, clockwise from the right): 0 bar is down and left. */
export function scaleAngle(bar: number): number {
  'worklet';
  const start = 90 + (360 - GEO.gauge.sweep) / 2;
  return (start + (bar / GEO.gauge.max) * GEO.gauge.sweep) * RAD;
}

/**
 * The needle's angle for a pressure. With no pressure it rests on its stop pin a little below 0, clear of the
 * printed 0; from 2 bar up it reads true.
 */
export function needleAngle(bar: number): number {
  'worklet';
  const b = Math.min(bar, GEO.gauge.max + 0.4);
  const lift = Math.min(Math.max(b / 2, 0), 1);
  return scaleAngle(Math.max(b + GEO.gauge.pin * (1 - lift * lift * (3 - 2 * lift)), GEO.gauge.pin));
}

function smooth(e0: number, e1: number, x: number): number {
  'worklet';
  const t = Math.min(Math.max((x - e0) / (e1 - e0), 0), 1);
  return t * t * (3 - 2 * t);
}

/** The flow out of the spouts at `b` of the pour: drips, two steady streams, thinning, a last drip, dry. */
export function flowAt(b: number): number {
  'worklet';
  if (b <= BREW.drip || b >= BREW.dry) return 0;
  if (b < BREW.flowing) return 0.18 + 0.82 * smooth(BREW.drip, BREW.flowing, b);
  if (b < BREW.thinning) return 1;
  return 1 - 0.75 * smooth(BREW.thinning, BREW.dry - 0.03, b);
}

/**
 * How far the first drops have joined into a stream (0–1) at `b` of the pour: a column grows down from each spout,
 * faster as it falls, and stays whole to the end.
 */
export function joinAt(b: number): number {
  'worklet';
  const from = BREW.drip + 0.025;
  const u = Math.min(Math.max((b - from) / (BREW.flowing - from), 0), 1);
  return Math.pow(u, 1.6);
}

/** How full the cup is (0–1) at `b` of the pour: the integral of the flow, in closed form near enough. */
export function fillAt(b: number): number {
  'worklet';
  const u = Math.min(Math.max((b - BREW.drip) / (BREW.dry - BREW.drip), 0), 1);
  // A steady middle with a slow start and a slower end, like the flow above.
  return u < 0.15 ? 0.09 * (u / 0.15) * (u / 0.15) : u > 0.75 ? 1 - 0.2 * Math.pow((1 - u) / 0.25, 1.6) : 0.09 + (u - 0.15) * (0.71 / 0.6);
}

/** The machine's values the drawing depends on, as plain numbers. */
export type MachineFrame = {
  angle: number; // the lever, degrees
  bar: number; // the needle's pressure
  zone: number; // 0–1, the brew zone lit
  brew: number; // 0–1 through the pour
  ready: number; // 0–1, the warm light of a finished shot
  cupX: number; // pt the cup is slid to the right while it is swapped
  cupA: number; // its opacity
  clock: number; // s
  puffAt: number; // clock time of the last stroke's vent of steam
  reduceMotion: boolean;
};

/** Every uniform of one frame. */
export function machineLook(s: MachineFrame) {
  'worklet';
  const a = s.angle * RAD;
  const flow = s.cupA > 0.5 ? flowAt(s.brew) : 0;
  const fill = fillAt(s.brew);
  const innerBottom = C.base - 9;
  const top = C.rim + C.full;
  const surface = innerBottom - fill * (innerBottom - top);
  const crema = smooth(0.18, 0.92, s.brew);
  const steam = s.reduceMotion ? 0 : (smooth(0.55, 1, s.brew) * 0.55 + s.ready * 0.45) * s.cupA * smooth(0.6, 1, fill);
  const puffAge = s.clock - s.puffAt;
  const puff = s.reduceMotion || puffAge < 0 || puffAge > TIMING.puff / 1000 ? 0 : 1 - puffAge / (TIMING.puff / 1000);
  return {
    rot: [Math.cos(-a), Math.sin(-a)],
    needle: needleAngle(s.bar),
    zone: s.zone,
    cup: { uFill: fill, uSurface: surface, uCrema: crema, uFlow: flow, uT: s.clock, uReady: s.ready, uMotion: s.reduceMotion ? 0 : 1 },
    streams: { uFlow: flow, uJoin: joinAt(s.brew), uT: s.clock, uSurface: surface, uBlond: smooth(0.62, 0.9, s.brew), uMotion: s.reduceMotion ? 0 : 1 },
    steam: { uT: s.clock * STEAM.rise, uAmt: steam, uPuff: puff, uPuffAge: Math.max(puffAge, 0), uWarm: s.ready },
    ready: s.ready,
  };
}
