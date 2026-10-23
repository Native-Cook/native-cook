import {
  BlurStyle,
  Canvas,
  PaintStyle,
  Picture,
  Skia,
  StrokeCap,
  StrokeJoin,
  type SkCanvas,
  type SkPaint,
  type SkPathBuilder,
} from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { useEffect, useState } from 'react';
import { type LayoutChangeEvent, StyleSheet, Text, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import { useDerivedValue, useFrameCallback, useReducedMotion, useSharedValue } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

import {
  addDomino,
  aimDomino,
  anyFallen,
  type Chain,
  chainBusy,
  createChain,
  H,
  lengthOf,
  MAX_DOMINOES,
  pointAt,
  popAt,
  PUSH,
  pushDomino,
  raiseChain,
  sinkChain,
  SPACING,
  spiralPoints,
  stepChain,
  T,
  W,
} from './dominoSim';

type Props = {
  /** Lays a spiral, tips it over, stands it back up and clears the table, on a loop. */
  autoPlay?: boolean;
};

// The camera looks down at the table at PITCH, from DISTANCE away, with focal length FOCAL (pt).
// Its aim point sits AIM_Z into the table, and shows on screen at AIM_AT of the height.
const PITCH = (52 * Math.PI) / 180;
const DISTANCE = 820;
const FOCAL = 960;
const AIM_Z = 30;
const AIM_AT = 0.56;
// The far rail of the table shows this far below the top inset, pt.
const RAIL_AT = 150;
const RAIL_HEIGHT = 12;
const RAIL_DEPTH = 18;

function normalize3(x: number, y: number, z: number) {
  const l = Math.hypot(x, y, z);
  return [x / l, y / l, z / l];
}

// The light: a lamp above and behind the table, to the left. Shadows fall towards the viewer.
const LIGHT = normalize3(-0.42, 1, 0.5);
const SHADOW_ALPHA = 0.42;
const SHADOW_BLUR = 3.2;

// The tiles: ivory with ink pips, lit by the lamp.
const IVORY = [0.95, 0.92, 0.85];
const PIP = '#1c1c1e';
const PIP_RADIUS = 2.1;

// The autoPlay spiral, in table units: centre, outer and inner radius, turns and start angle.
const SPIRAL = { x: 0, z: -5, outer: 146, inner: 32, turns: 2.05, start: Math.PI * 0.62 };

// A finger counts as on a domino within this distance of its top, pt.
const HIT = 30;
// A flick tips a domino when it moves along the domino's heading at least this fast, pt/s.
const FLICK_SPEED = 260;
// A drag this long across a fallen run stands it back up, pt.
const SWEEP_DISTANCE = 90;
// While a path is drawn, its points are kept at least this far apart (table units), and the
// finger is smoothed over this share of the way each move.
const PATH_STEP = 3;
const PATH_SMOOTH = 0.55;
// A drawn path never keeps more points than this (a full run needs about 700).
const MAX_PATH = 1024;
// New dominoes pop up at least this long after the one before, s.
const POP_GAP = 0.03;
// One click (light haptic) every this many hits.
const CLICK_EVERY = 3;

const TICK = 1 / 120;
const MAX_TICKS = 4;

// autoPlay, s: the spiral is drawn, tipped, swept back up and cleared.
const LOOP = 12;
const DRAW_FROM = 0.4;
const DRAW_TO = 2.6;
const TAP_AT = 3.2;
const SWEEP_FROM = 8.0;
const SWEEP_TO = 8.5;
const CLEAR_AT = 10.8;
// A touch pauses autoPlay this long, s.
const USER_HOLD = 4;

/** Whether a time `at` was passed going from `before` to `t` on a looping clock. */
function crossed(before: number, t: number, at: number) {
  'worklet';
  return (before < at && t >= at) || (t < before && (at > before || at <= t));
}

/** The pips of a domino half, as (u, v) in a −1…1 grid. */
const PIP_LAYOUTS: number[][] = [
  [],
  [0, 0],
  [-1, -1, 1, 1],
  [-1, -1, 0, 0, 1, 1],
  [-1, -1, 1, -1, -1, 1, 1, 1],
  [-1, -1, 1, -1, 0, 0, -1, 1, 1, 1],
  [-1, -1, 1, -1, -1, 0, 1, 0, -1, 1, 1, 1],
];

// Null only if the shader fails to compile: the felt is then a plain lit gradient.
const FELT = Skia.RuntimeEffect.Make(`
uniform float2 size;
uniform float2 lamp;
uniform float reach;
uniform float rail;

float hash(float2 p) {
  float3 q = fract(float3(p.x, p.y, p.x) * 0.1031);
  q += dot(q, q.yzx + 33.33);
  return fract((q.x + q.y) * q.z);
}

float noise(float2 p) {
  float2 i = floor(p);
  float2 f = fract(p);
  f = f * f * (3.0 - 2.0 * f);
  return mix(mix(hash(i), hash(i + float2(1.0, 0.0)), f.x), mix(hash(i + float2(0.0, 1.0)), hash(i + float2(1.0, 1.0)), f.x), f.y);
}

half4 main(float2 p) {
  // The lamp's pool of light, squashed by the table's tilt.
  float2 d = (p - lamp) / reach * float2(1.0, 1.45);
  float pool = exp(-dot(d, d) * 1.25);
  // Felt: a fine tooth, soft mottling and a faint nap.
  float tooth = hash(floor(p * 1.6)) - 0.5;
  float mottle = noise(p * 0.045) - 0.5;
  float nap = noise(float2(p.x * 0.5, p.y * 0.12)) - 0.5;
  float3 base = float3(0.06, 0.25, 0.18);
  float3 lit = float3(0.13, 0.43, 0.31);
  float3 c = mix(base * 0.55, lit, pool);
  c *= 1.0 + tooth * 0.12 + mottle * 0.16 + nap * 0.07;
  // Darker towards the far rail.
  float far = clamp((p.y - rail) / (size.y * 0.35), 0.0, 1.0);
  c *= 0.62 + 0.38 * far;
  return half4(c, 1.0);
}
`);

type Scene = {
  chains: Chain[];
  /** Which chain is the live one; the other may still be sinking away. */
  live: number;
  /** The path being drawn (table units), the smoothed finger and the arc length placed so far. */
  pathX: number[];
  pathZ: number[];
  drawing: boolean;
  smoothX: number;
  smoothZ: number;
  placed: number;
  lastBorn: number;
  /** When the drawn path was finished, for its fading trace. */
  traceEnd: number;
  /** Arc length of the path drawn so far (table units). */
  pathLength: number;
  /** The table's paints, made once per layout (`tableKey`). */
  tableKey: string;
  table: { room: SkPaint; felt: SkPaint; front: SkPaint; top: SkPaint; lit: SkPaint; lip: SkPaint } | null;
  paints: { shadow: SkPaint; contact: SkPaint; edge: SkPaint; pip: SkPaint; line: SkPaint; face: SkPaint };
};

/**
 * A run of ivory dominoes on a green felt table seen from above at an angle. Tap the first (or flick
 * any) and the run topples, each tile turning about its leading edge and striking the next; drag
 * across the table to lay a new run along your finger; sweep across a fallen run to stand it back
 * up, last tile first.
 */
export function DominoChain({ autoPlay = false }: Props) {
  const insets = useSafeAreaInsets();
  const reducedMotion = useReducedMotion();
  const auto = autoPlay && !reducedMotion;
  const [size, setSize] = useState({ width: 0, height: 0 });
  const [fallen, setFallen] = useState(false);
  const { width, height } = size;
  const cx = width / 2;
  const cy = height * AIM_AT;
  const railY = insets.top + RAIL_AT;

  const scene = useSharedValue<Scene | null>(null);
  const tick = useSharedValue(0);
  const acc = useSharedValue(0);
  const clicks = useSharedValue(0);
  const wasFallen = useSharedValue(false);

  // The finger: what it is doing (0 none, 1 drawing, 2 flicking, 3 sweeping), where it is, and the
  // domino a flick started on. autoPlay drives the same finger, and shows it.
  const mode = useSharedValue(0);
  const fingerX = useSharedValue(0);
  const fingerY = useSharedValue(0);
  const flickOn = useSharedValue(-1);
  const swept = useSharedValue(false);
  const ghost = useSharedValue(0);
  const ghostOn = useSharedValue(false);

  const clock = useSharedValue(0);
  const hold = useSharedValue(0);
  const looping = useSharedValue(auto);

  useEffect(() => {
    looping.set(auto);
    clock.set(0);
    hold.set(0);
  }, [auto, clock, hold, looping]);

  const onLayout = (e: LayoutChangeEvent) => {
    const { width: w, height: h } = e.nativeEvent.layout;
    setSize({ width: w, height: h });
  };

  const click = () => {
    if (process.env.EXPO_OS !== 'web') Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
  };
  const tipped = () => {
    if (process.env.EXPO_OS !== 'web') Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Medium).catch(() => {});
  };
  const raised = () => {
    if (process.env.EXPO_OS !== 'web') Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Rigid).catch(() => {});
  };

  // ─── Camera ────────────────────────────────────────────────────────────────────────────────────
  const sinP = Math.sin(PITCH);
  const cosP = Math.cos(PITCH);
  const camY = DISTANCE * sinP;
  const camZ = AIM_Z - DISTANCE * cosP;

  /** Screen x, y and depth of a point on or above the table. */
  const project = (x: number, y: number, z: number, out: number[]) => {
    'worklet';
    const py = y - camY;
    const pz = z - camZ;
    const yc = py * cosP + pz * sinP;
    const zc = -py * sinP + pz * cosP;
    out[0] = cx + (FOCAL * x) / zc;
    out[1] = cy - (FOCAL * yc) / zc;
    out[2] = zc;
  };

  /** The point on the table under a screen point (null above the horizon). */
  const unproject = (sx: number, sy: number) => {
    'worklet';
    const xs = (sx - cx) / FOCAL;
    const ys = -(sy - cy) / FOCAL;
    const dx = xs;
    const dy = ys * cosP - sinP;
    const dz = ys * sinP + cosP;
    if (dy >= -1e-4) return null;
    const t = -camY / dy;
    return { x: dx * t, z: camZ + dz * t };
  };

  // The table's far edge, in table units, from where the rail shows on screen.
  const farZ = (() => {
    if (width === 0) return 400;
    const ys = -(railY - cy) / FOCAL;
    const dy = ys * cosP - sinP;
    const dz = ys * sinP + cosP;
    if (dy >= -1e-4) return 400;
    return camZ + dz * (-camY / dy);
  })();

  /** Lays the spiral at once (no pop) or as a drawn path. */
  const laySpiral = (c: Chain) => {
    'worklet';
    const sp = spiralPoints(SPIRAL.x, SPIRAL.z, SPIRAL.outer, SPIRAL.inner, SPIRAL.turns, SPIRAL.start);
    const total = lengthOf(sp.xs, sp.zs);
    for (let s = 0; s <= total; s += SPACING) {
      const a = pointAt(sp.xs, sp.zs, Math.max(0, s - 3));
      const b = pointAt(sp.xs, sp.zs, s + 3);
      const p = pointAt(sp.xs, sp.zs, s);
      const hx = b.x - a.x;
      const hz = b.z - a.z;
      const l = Math.hypot(hx, hz) || 1;
      addDomino(c, p.x, p.z, hx / l, hz / l, -1);
    }
  };

  // ─── Actions, shared by the finger and the script ──────────────────────────────────────────────

  /** The live chain's standing domino nearest a screen point (its top), or -1. */
  const dominoAt = (s: Scene, sx: number, sy: number) => {
    'worklet';
    const c = s.chains[s.live];
    const p = [0, 0, 0];
    let best = -1;
    let bestD = HIT;
    for (let i = 0; i < c.count; i += 1) {
      if (c.sink[i] !== Infinity) continue;
      project(c.x[i], H * 0.6, c.z[i], p);
      const d = Math.hypot(p[0] - sx, p[1] - sy);
      if (d < bestD) {
        bestD = d;
        best = i;
      }
    }
    return best;
  };

  const beginDraw = (s: Scene, sx: number, sy: number) => {
    'worklet';
    const at = unproject(sx, sy);
    if (!at || at.z > farZ - H) return false;
    // The old run sinks away; the new one is laid in the other chain.
    sinkChain(s.chains[s.live]);
    s.live = 1 - s.live;
    const c = s.chains[s.live];
    c.count = 0;
    c.hits = 0;
    s.pathX = [at.x];
    s.pathZ = [at.z];
    s.smoothX = at.x;
    s.smoothZ = at.z;
    s.placed = 0;
    s.pathLength = 0;
    s.lastBorn = c.time - 1;
    s.drawing = true;
    s.traceEnd = Infinity;
    clicks.set(0);
    return true;
  };

  const extendDraw = (s: Scene, sx: number, sy: number) => {
    'worklet';
    if (!s.drawing) return;
    const at = unproject(sx, sy);
    if (!at) return;
    const z = Math.min(at.z, farZ - H);
    s.smoothX += (at.x - s.smoothX) * PATH_SMOOTH;
    s.smoothZ += (z - s.smoothZ) * PATH_SMOOTH;
    const n = s.pathX.length;
    if (n === 0) return;
    const c = s.chains[s.live];
    // The run is full: the path (and its cost per move) stops growing.
    if (c.count >= MAX_DOMINOES || n >= MAX_PATH) return;
    const step = Math.hypot(s.smoothX - s.pathX[n - 1], s.smoothZ - s.pathZ[n - 1]);
    if (!(step >= PATH_STEP)) return;
    s.pathX.push(s.smoothX);
    s.pathZ.push(s.smoothZ);
    s.pathLength += step;
    const total = s.pathLength;
    // Place a domino every SPACING along the path, a little behind the finger so its heading is known.
    while (s.placed + 4 <= total) {
      const p = pointAt(s.pathX, s.pathZ, s.placed);
      const a = pointAt(s.pathX, s.pathZ, Math.max(0, s.placed - 4));
      const b = pointAt(s.pathX, s.pathZ, s.placed + 4);
      const hx = b.x - a.x;
      const hz = b.z - a.z;
      const l = Math.hypot(hx, hz) || 1;
      const delay = Math.max(0, s.lastBorn + POP_GAP - c.time);
      if (!addDomino(c, p.x, p.z, hx / l, hz / l, delay)) break;
      s.lastBorn = c.time + delay;
      // The one before now knows where its path goes next.
      if (c.count > 1) {
        const k = c.count - 2;
        const dx = c.x[k + 1] - c.x[k];
        const dz = c.z[k + 1] - c.z[k];
        const dl = Math.hypot(dx, dz) || 1;
        const mx = (c.hx[k] + dx / dl) / 2;
        const mz = (c.hz[k] + dz / dl) / 2;
        const hl = Math.hypot(mx, mz);
        // A hairpin can cancel the two headings out: then it faces the next one.
        if (hl > 1e-6) aimDomino(c, k, mx / hl, mz / hl);
        else aimDomino(c, k, dx / dl, dz / dl);
      }
      s.placed += SPACING;
    }
  };

  const endDraw = (s: Scene) => {
    'worklet';
    if (!s.drawing) return;
    s.drawing = false;
    s.traceEnd = s.chains[s.live].time;
    // Too short to be a run: it sinks away again.
    if (s.chains[s.live].count < 2) sinkChain(s.chains[s.live]);
  };

  const push = (s: Scene, i: number, spin: number) => {
    'worklet';
    if (pushDomino(s.chains[s.live], i, spin)) scheduleOnRN(tipped);
  };

  const sweep = (s: Scene) => {
    'worklet';
    const c = s.chains[s.live];
    if (!anyFallen(c)) return;
    raiseChain(c, reducedMotion);
    c.hits = 0;
    clicks.set(0);
    scheduleOnRN(raised);
  };

  /** One step of the autoPlay script, from `before` to `t` s into the loop. */
  const script = (s: Scene, before: number, t: number) => {
    'worklet';
    const p = [0, 0, 0];
    if (t >= DRAW_FROM && t <= DRAW_TO) {
      // The finger traces the spiral from its outer end inwards.
      const sp = spiralPoints(SPIRAL.x, SPIRAL.z, SPIRAL.outer, SPIRAL.inner, SPIRAL.turns, SPIRAL.start);
      const total = lengthOf(sp.xs, sp.zs);
      const k = (t - DRAW_FROM) / (DRAW_TO - DRAW_FROM);
      const e = k < 0.5 ? 2 * k * k : 1 - 2 * (1 - k) * (1 - k);
      const at = pointAt(sp.xs, sp.zs, e * (total + 6));
      project(at.x, 0, at.z, p);
      fingerX.set(p[0]);
      fingerY.set(p[1]);
      ghostOn.set(true);
      if (crossed(before, t, DRAW_FROM) || !s.drawing) {
        // Start exactly on the spiral, with no smoothing lag.
        beginDraw(s, p[0], p[1]);
        s.smoothX = at.x;
        s.smoothZ = at.z;
        s.pathX = [at.x];
        s.pathZ = [at.z];
        s.pathLength = 0;
      } else {
        extendDraw(s, p[0], p[1]);
      }
    } else if (s.drawing && t > DRAW_TO) {
      // Finish the tail exactly, then lift.
      const sp = spiralPoints(SPIRAL.x, SPIRAL.z, SPIRAL.outer, SPIRAL.inner, SPIRAL.turns, SPIRAL.start);
      const end = pointAt(sp.xs, sp.zs, lengthOf(sp.xs, sp.zs) + 6);
      project(end.x, 0, end.z, p);
      extendDraw(s, p[0], p[1]);
      endDraw(s);
      ghostOn.set(false);
    }
    const c = s.chains[s.live];
    if (t >= TAP_AT - 0.35 && t < TAP_AT + 0.2 && c.count > 0) {
      project(c.x[0], H * 0.6, c.z[0], p);
      fingerX.set(p[0]);
      fingerY.set(p[1]);
      ghostOn.set(t < TAP_AT);
    }
    if (crossed(before, t, TAP_AT)) push(s, 0, PUSH);
    if (t >= SWEEP_FROM && t <= SWEEP_TO) {
      const k = (t - SWEEP_FROM) / (SWEEP_TO - SWEEP_FROM);
      fingerX.set(width * (0.12 + 0.76 * k));
      fingerY.set(height * 0.78 - 30 * Math.sin(k * Math.PI));
      ghostOn.set(true);
      if (k > 0.45 && anyFallen(c) && !chainBusy(c)) sweep(s);
    } else if (t > SWEEP_TO && t < SWEEP_TO + 0.1) ghostOn.set(false);
    if (crossed(before, t, CLEAR_AT)) sinkChain(c);
  };

  useFrameCallback((frame) => {
    if (width === 0) return;
    let s = scene.get();
    if (s === null) {
      const shadow = Skia.Paint();
      shadow.setColor(Skia.Color(`rgba(4,16,10,${SHADOW_ALPHA})`));
      shadow.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, SHADOW_BLUR, true));
      const contact = Skia.Paint();
      contact.setColor(Skia.Color('rgba(2,10,6,0.5)'));
      contact.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 1.2, true));
      const edge = Skia.Paint();
      edge.setStyle(PaintStyle.Stroke);
      edge.setStrokeWidth(0.6);
      edge.setStrokeJoin(StrokeJoin.Round);
      edge.setColor(Skia.Color('rgba(70,58,40,0.55)'));
      edge.setAntiAlias(true);
      const pip = Skia.Paint();
      pip.setColor(Skia.Color(PIP));
      pip.setAntiAlias(true);
      const line = Skia.Paint();
      line.setStyle(PaintStyle.Stroke);
      line.setStrokeWidth(0.9);
      line.setStrokeCap(StrokeCap.Round);
      line.setColor(Skia.Color('rgba(40,36,30,0.75)'));
      line.setAntiAlias(true);
      const face = Skia.Paint();
      face.setAntiAlias(true);
      s = {
        chains: [createChain(), createChain()],
        live: 0,
        pathX: [],
        pathZ: [],
        drawing: false,
        smoothX: 0,
        smoothZ: 0,
        placed: 0,
        lastBorn: 0,
        traceEnd: -10,
        pathLength: 0,
        tableKey: '',
        table: null,
        paints: { shadow, contact, edge, pip, line, face },
      };
      // Without autoPlay the table starts with the spiral standing, ready to tip.
      if (!auto) laySpiral(s.chains[0]);
      scene.set(s);
    }
    const dt = Math.min((frame.timeSincePreviousFrame ?? 16) / 1000, MAX_TICKS * TICK);
    let pending = acc.get() + dt;
    let ticks = 0;
    while (pending >= TICK && ticks < MAX_TICKS) {
      pending -= TICK;
      ticks += 1;
      if (hold.get() > 0) {
        if (mode.get() === 0) hold.set(hold.get() - TICK);
        if (hold.get() <= 0) clock.set(0);
      } else if (looping.get()) {
        const before = clock.get();
        const t = (before + TICK) % LOOP;
        clock.set(t);
        script(s, before, t);
      }
      stepChain(s.chains[0], TICK);
      stepChain(s.chains[1], TICK);
    }
    acc.set(Math.min(pending, TICK));
    ghost.set(ghost.get() + ((ghostOn.get() ? 1 : 0) - ghost.get()) * Math.min(1, dt * 16));

    // A click every few hits, on the frame the hit lands.
    const c = s.chains[s.live];
    const due = Math.floor(c.hits / CLICK_EVERY);
    if (due > clicks.get()) {
      clicks.set(due);
      scheduleOnRN(click);
    }
    const down = anyFallen(c);
    if (down !== wasFallen.get()) {
      wasFallen.set(down);
      scheduleOnRN(setFallen, down);
    }
    tick.set(tick.get() + 1);
  });

  // ─── Gestures ──────────────────────────────────────────────────────────────────────────────────

  const touched = () => {
    'worklet';
    hold.set(USER_HOLD);
    ghostOn.set(false);
    const s = scene.get();
    if (s && s.drawing && mode.get() === 0) endDraw(s);
  };

  const pan = Gesture.Pan()
    .maxPointers(1)
    .minDistance(6)
    .onStart((e) => {
      const s = scene.get();
      if (!s) return;
      touched();
      const x0 = e.x - e.translationX;
      const y0 = e.y - e.translationY;
      const c = s.chains[s.live];
      const falling = chainBusy(c);
      if (anyFallen(c)) {
        mode.set(falling ? 0 : 3);
        swept.set(false);
        return;
      }
      const hit = dominoAt(s, x0, y0);
      if (hit >= 0) {
        mode.set(2);
        flickOn.set(hit);
        return;
      }
      if (beginDraw(s, x0, y0)) {
        mode.set(1);
        extendDraw(s, e.x, e.y);
      } else mode.set(0);
    })
    .onUpdate((e) => {
      const s = scene.get();
      if (!s) return;
      if (mode.get() === 1) extendDraw(s, e.x, e.y);
      else if (mode.get() === 3 && !swept.get() && Math.hypot(e.translationX, e.translationY) > SWEEP_DISTANCE) {
        swept.set(true);
        sweep(s);
      }
    })
    .onEnd((e) => {
      const s = scene.get();
      if (!s) return;
      if (mode.get() === 2) {
        // A flick along the domino's heading tips it, harder for a faster flick.
        const c = s.chains[s.live];
        const i = flickOn.get();
        if (i < 0 || i >= c.count) return;
        const a = [0, 0, 0];
        const b = [0, 0, 0];
        project(c.x[i], 0, c.z[i], a);
        project(c.x[i] + c.hx[i] * 10, 0, c.z[i] + c.hz[i] * 10, b);
        const dx = b[0] - a[0];
        const dy = b[1] - a[1];
        const l = Math.hypot(dx, dy) || 1;
        const along = (e.velocityX * dx + e.velocityY * dy) / l;
        if (along > FLICK_SPEED) push(s, i, PUSH * Math.min(1.8, Math.max(1, along / 700)));
      }
    })
    .onFinalize(() => {
      const s = scene.get();
      if (s && mode.get() === 1) endDraw(s);
      mode.set(0);
    });

  const tap = Gesture.Tap()
    .maxDuration(300)
    .maxDistance(10)
    .onEnd((e, success) => {
      const s = scene.get();
      if (!success || !s) return;
      touched();
      const i = dominoAt(s, e.x, e.y);
      if (i >= 0) push(s, i, PUSH);
    });

  /** The room, the far rail and the felt. Its paints and shaders are made once per layout. */
  function drawTable(canvas: SkCanvas, s: Scene) {
    'worklet';
    const a = [0, 0, 0];
    const b = [0, 0, 0];
    project(0, RAIL_HEIGHT, farZ, a);
    project(0, RAIL_HEIGHT, farZ + RAIL_DEPTH, b);
    const key = `${width}x${height}x${railY}`;
    let t = s.table;
    if (t === null || s.tableKey !== key) {
      const room = Skia.Paint();
      room.setShader(
        Skia.Shader.MakeLinearGradient({ x: 0, y: 0 }, { x: 0, y: railY }, [Skia.Color('#07090a'), Skia.Color('#141a17')], [0, 1], 0),
      );
      const felt = Skia.Paint();
      const lamp = [0, 0, 0];
      project(SPIRAL.x - 30, 0, SPIRAL.z + 40, lamp);
      if (FELT) felt.setShader(FELT.makeShader([width, height, lamp[0], lamp[1], width * 0.95, railY]));
      else {
        felt.setShader(
          Skia.Shader.MakeRadialGradient({ x: lamp[0], y: lamp[1] }, width * 0.95, [Skia.Color('#215e46'), Skia.Color('#0a2419')], [0, 1], 0),
        );
      }
      const front = Skia.Paint();
      front.setShader(
        Skia.Shader.MakeLinearGradient({ x: 0, y: a[1] }, { x: 0, y: railY }, [Skia.Color('#6b4426'), Skia.Color('#2a170b')], [0, 1], 0),
      );
      const top = Skia.Paint();
      top.setShader(
        Skia.Shader.MakeLinearGradient({ x: 0, y: b[1] }, { x: 0, y: a[1] }, [Skia.Color('#3d2514'), Skia.Color('#8a5a33')], [0, 1], 0),
      );
      const lit = Skia.Paint();
      lit.setColor(Skia.Color('rgba(255,214,160,0.45)'));
      lit.setStrokeWidth(1);
      const lip = Skia.Paint();
      lip.setShader(
        Skia.Shader.MakeLinearGradient({ x: 0, y: railY }, { x: 0, y: railY + 22 }, [Skia.Color('rgba(0,0,0,0.55)'), Skia.Color('rgba(0,0,0,0)')], [0, 1], 0),
      );
      t = { room, felt, front, top, lit, lip };
      s.table = t;
      s.tableKey = key;
    }
    canvas.drawRect(Skia.XYWHRect(0, 0, width, railY + 2), t.room);
    canvas.drawRect(Skia.XYWHRect(0, railY, width, height - railY), t.felt);
    // The rail: its top and its front, with a lit edge between them.
    canvas.drawRect(Skia.XYWHRect(0, a[1], width, railY - a[1] + 0.5), t.front);
    canvas.drawRect(Skia.XYWHRect(0, b[1], width, a[1] - b[1]), t.top);
    canvas.drawLine(0, a[1], width, a[1], t.lit);
    canvas.drawRect(Skia.XYWHRect(0, railY, width, 22), t.lip);
  }

  /** The 3D corners of a domino: index = a·4 + b·2 + c over (back/front, bottom/top, left/right). */
  function corners(c: Chain, i: number, out: number[]) {
    'worklet';
    const pop = popAt(c, i);
    const sc = reducedMotion ? 1 : pop.scale;
    const lift = reducedMotion ? 0 : pop.lift;
    const fx = c.hx[i];
    const fz = c.hz[i];
    // Lateral axis: the heading turned a quarter.
    const lx = -fz;
    const lz = fx;
    const t = c.tilt[i];
    const ct = Math.cos(t);
    const st = Math.sin(t);
    const th = T * sc;
    const hh = H * sc;
    const ww = W * sc;
    // The pivot: the front bottom edge.
    const px = c.x[i] + fx * (th / 2);
    const pz = c.z[i] + fz * (th / 2);
    for (let a = 0; a < 2; a += 1) {
      for (let b = 0; b < 2; b += 1) {
        for (let k = 0; k < 2; k += 1) {
          const la = a === 0 ? -th : 0;
          const lb = b === 0 ? 0 : hh;
          const lc = (k === 0 ? -0.5 : 0.5) * ww;
          // Tip forward about the pivot.
          const ra = la * ct + lb * st;
          const rb = -la * st + lb * ct;
          const n = (a * 4 + b * 2 + k) * 3;
          out[n] = px + fx * ra + lx * lc;
          out[n + 1] = rb + lift;
          out[n + 2] = pz + fz * ra + lz * lc;
        }
      }
    }
    return pop.alpha;
  }

  /** Adds a domino's shadow (its corners cast along the light onto the table) and its contact shade. */
  function addShadow(shadows: SkPathBuilder, contacts: SkPathBuilder, c: Chain, i: number) {
    'worklet';
    const q: number[] = new Array(24).fill(0);
    const alpha = corners(c, i, q);
    if (alpha < 0.3) return;
    // Cast each corner along the light onto the table, then wrap the eight in a hull.
    const xs: number[] = [];
    const ys: number[] = [];
    const p = [0, 0, 0];
    for (let k = 0; k < 8; k += 1) {
      const y = Math.max(0, q[k * 3 + 1]);
      const x = q[k * 3] - (LIGHT[0] / LIGHT[1]) * y;
      const z = q[k * 3 + 2] - (LIGHT[2] / LIGHT[1]) * y;
      project(x, 0, z, p);
      xs.push(p[0]);
      ys.push(p[1]);
    }
    // Gift wrap.
    let start = 0;
    for (let k = 1; k < 8; k += 1) if (xs[k] < xs[start] || (xs[k] === xs[start] && ys[k] < ys[start])) start = k;
    let cur = start;
    let first = true;
    for (let guard = 0; guard < 9; guard += 1) {
      if (first) shadows.moveTo(xs[cur], ys[cur]);
      else shadows.lineTo(xs[cur], ys[cur]);
      first = false;
      let next = (cur + 1) % 8;
      for (let k = 0; k < 8; k += 1) {
        const cross = (xs[next] - xs[cur]) * (ys[k] - ys[cur]) - (ys[next] - ys[cur]) * (xs[k] - xs[cur]);
        if (cross < 0) next = k;
      }
      cur = next;
      if (cur === start) break;
    }
    shadows.close();
    // A tight dark line where the base meets the felt.
    if (q[1] < 1.5) {
      const a = [0, 0, 0];
      const b = [0, 0, 0];
      project(q[0], 0, q[2], a);
      project(q[3], 0, q[5], b);
      const e = [0, 0, 0];
      const f = [0, 0, 0];
      project(q[12], 0, q[14], e);
      project(q[15], 0, q[17], f);
      contacts.moveTo(a[0], a[1]);
      contacts.lineTo(b[0], b[1]);
      contacts.lineTo(f[0], f[1]);
      contacts.lineTo(e[0], e[1]);
      contacts.close();
    }
  }

  /** One domino: its visible faces, lit by the lamp, the pips on its front. */
  function drawDomino(canvas: SkCanvas, s: Scene, c: Chain, i: number) {
    'worklet';
    const q: number[] = new Array(24).fill(0);
    const alpha = corners(c, i, q);
    if (alpha <= 0.01) return;
    const sp: number[] = new Array(24).fill(0);
    const p = [0, 0, 0];
    for (let k = 0; k < 8; k += 1) {
      project(q[k * 3], q[k * 3 + 1], q[k * 3 + 2], p);
      sp[k * 3] = p[0];
      sp[k * 3 + 1] = p[1];
      sp[k * 3 + 2] = p[2];
    }
    // Faces as corner indices (counter-clockwise seen from outside) and their outward normals.
    const fx = c.hx[i];
    const fz = c.hz[i];
    const lx = -fz;
    const lz = fx;
    const ct = Math.cos(c.tilt[i]);
    const st = Math.sin(c.tilt[i]);
    // Forward and up after the tip.
    const ax = fx * ct;
    const ay = -st;
    const az = fz * ct;
    const ux = fx * st;
    const uy = ct;
    const uz = fz * st;
    const faces = [
      // front (a = 1), back (a = 0), top (b = 1), left (c = 0), right (c = 1)
      [4, 5, 7, 6, ax, ay, az],
      [0, 2, 3, 1, -ax, -ay, -az],
      [2, 6, 7, 3, ux, uy, uz],
      [0, 4, 6, 2, -lx, 0, -lz],
      [1, 3, 7, 5, lx, 0, lz],
    ];
    const paint = s.paints.face;
    if (alpha < 1) {
      const fade = Skia.Paint();
      fade.setAlphaf(alpha);
      canvas.saveLayer(fade);
    }
    for (let f = 0; f < faces.length; f += 1) {
      const face = faces[f];
      const a = face[0];
      const b = face[1];
      const d = face[3];
      const nx = face[4];
      const ny = face[5];
      const nz = face[6];
      // Visible when it faces the camera.
      const mx = (q[a * 3] + q[face[2] * 3]) / 2;
      const my = (q[a * 3 + 1] + q[face[2] * 3 + 1]) / 2;
      const mz = (q[a * 3 + 2] + q[face[2] * 3 + 2]) / 2;
      if (nx * (0 - mx) + ny * (camY - my) + nz * (camZ - mz) <= 0) continue;
      const light = Math.max(0, nx * LIGHT[0] + ny * LIGHT[1] + nz * LIGHT[2]);
      const shade = 0.52 + 0.5 * light;
      const r = Math.round(Math.min(1, IVORY[0] * shade) * 255);
      const g = Math.round(Math.min(1, IVORY[1] * shade) * 255);
      const bl = Math.round(Math.min(1, IVORY[2] * shade * 0.98) * 255);
      paint.setColor(Skia.Color(`rgb(${r},${g},${bl})`));
      const path = Skia.PathBuilder.Make();
      path.moveTo(sp[a * 3], sp[a * 3 + 1]);
      path.lineTo(sp[b * 3], sp[b * 3 + 1]);
      path.lineTo(sp[face[2] * 3], sp[face[2] * 3 + 1]);
      path.lineTo(sp[d * 3], sp[d * 3 + 1]);
      path.close();
      const built = path.build();
      canvas.drawPath(built, paint);
      canvas.drawPath(built, s.paints.edge);
      // The big faces: a groove across the middle, and pips on the front.
      if (f <= 1) {
        // Face frame: u across (corner a → the corner beside it on the bottom), v up.
        const ox = sp[a * 3];
        const oy = sp[a * 3 + 1];
        const bottom = f === 0 ? 5 : 1;
        const topK = f === 0 ? 6 : 2;
        const ex = (sp[bottom * 3] - ox) / W;
        const ey = (sp[bottom * 3 + 1] - oy) / W;
        const vx = (sp[topK * 3] - ox) / H;
        const vy = (sp[topK * 3 + 1] - oy) / H;
        canvas.save();
        canvas.concat(Skia.Matrix([ex, vx, ox, ey, vy, oy, 0, 0, 1]));
        canvas.drawLine(3, H / 2, W - 3, H / 2, s.paints.line);
        if (f === 0) {
          const halves = [c.pipsA[i], c.pipsB[i]];
          for (let h = 0; h < 2; h += 1) {
            const layout = PIP_LAYOUTS[halves[h]];
            const v0 = h === 0 ? H * 0.25 : H * 0.75;
            for (let k = 0; k < layout.length; k += 2) {
              canvas.drawCircle(W / 2 + layout[k] * 5.2, v0 + layout[k + 1] * 5.2, PIP_RADIUS, s.paints.pip);
            }
          }
        }
        canvas.restore();
      }
    }
    if (alpha < 1) canvas.restore();
  }

  // ─── Drawing ───────────────────────────────────────────────────────────────────────────────────

  const picture = useDerivedValue(() => {
    tick.get();
    const recorder = Skia.PictureRecorder();
    const canvas = recorder.beginRecording(Skia.XYWHRect(0, 0, width, height));
    const s = scene.get();
    if (!s || width === 0) return recorder.finishRecordingAsPicture();
    drawTable(canvas, s);

    // The trace of a path being drawn, fading once the finger lifts.
    const time = s.chains[s.live].time;
    const traceAlpha = s.drawing ? 1 : Math.max(0, 1 - (time - s.traceEnd) / 0.6);
    if (traceAlpha > 0 && s.pathX.length > 1) {
      const p = [0, 0, 0];
      const b = Skia.PathBuilder.Make();
      for (let k = 0; k < s.pathX.length; k += 1) {
        project(s.pathX[k], 0, s.pathZ[k], p);
        if (k === 0) b.moveTo(p[0], p[1]);
        else b.lineTo(p[0], p[1]);
      }
      const chalk = Skia.Paint();
      chalk.setStyle(PaintStyle.Stroke);
      chalk.setStrokeWidth(2);
      chalk.setStrokeCap(StrokeCap.Round);
      chalk.setStrokeJoin(StrokeJoin.Round);
      chalk.setColor(Skia.Color(`rgba(235,245,225,${0.32 * traceAlpha})`));
      chalk.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 1.4, true));
      canvas.drawPath(b.build(), chalk);
    }

    // Every domino of both chains, far to near.
    const order: number[] = [];
    const depth: number[] = [];
    const p = [0, 0, 0];
    for (let k = 0; k < 2; k += 1) {
      const c = s.chains[k];
      for (let i = 0; i < c.count; i += 1) {
        project(c.x[i] + c.hx[i] * (H / 2) * Math.sin(c.tilt[i]), 0, c.z[i] + c.hz[i] * (H / 2) * Math.sin(c.tilt[i]), p);
        order.push(k * 1000 + i);
        depth.push(p[2]);
      }
    }
    // Insertion sort by depth, farthest first.
    const idx: number[] = [];
    for (let k = 0; k < order.length; k += 1) {
      let j = idx.length;
      idx.push(k);
      while (j > 0 && depth[idx[j - 1]] < depth[k]) {
        idx[j] = idx[j - 1];
        j -= 1;
      }
      idx[j] = k;
    }

    // Shadows first, all in one soft path, then the tiles.
    const shadows = Skia.PathBuilder.Make();
    const contacts = Skia.PathBuilder.Make();
    for (let k = 0; k < idx.length; k += 1) {
      const id = order[idx[k]];
      const c = s.chains[id >= 1000 ? 1 : 0];
      addShadow(shadows, contacts, c, id % 1000);
    }
    canvas.drawPath(shadows.build(), s.paints.shadow);
    canvas.drawPath(contacts.build(), s.paints.contact);
    for (let k = 0; k < idx.length; k += 1) {
      const id = order[idx[k]];
      drawDomino(canvas, s, s.chains[id >= 1000 ? 1 : 0], id % 1000);
    }

    // A soft glow under the first tile while the run waits to be tipped.
    const live = s.chains[s.live];
    if (live.count > 1 && !anyFallen(live) && !s.drawing && live.sink[0] === Infinity) {
      const settle = Math.min(1, Math.max(0, (live.time - live.born[live.count - 1] - 0.4) / 0.5));
      if (settle > 0) {
        const pulse = 0.5 + 0.5 * Math.sin(live.time * 3.2);
        project(live.x[0], 0, live.z[0], p);
        const ring = Skia.Paint();
        ring.setStyle(PaintStyle.Stroke);
        ring.setStrokeWidth(1.4);
        ring.setColor(Skia.Color(`rgba(250,236,190,${(0.22 + 0.3 * pulse) * settle})`));
        ring.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 1.5, true));
        const r = 17 + 4 * pulse;
        canvas.drawOval(Skia.XYWHRect(p[0] - r, p[1] - r * 0.62, r * 2, r * 1.24), ring);
      }
    }

    // The autoPlay finger.
    const g = ghost.get();
    if (g > 0.01) {
      const touch = Skia.Paint();
      touch.setColor(Skia.Color(`rgba(255,255,255,${0.16 * g})`));
      canvas.drawCircle(fingerX.get(), fingerY.get(), 17, touch);
      const rim = Skia.Paint();
      rim.setStyle(PaintStyle.Stroke);
      rim.setStrokeWidth(1.5);
      rim.setColor(Skia.Color(`rgba(255,255,255,${0.5 * g})`));
      canvas.drawCircle(fingerX.get(), fingerY.get(), 17, rim);
    }
    return recorder.finishRecordingAsPicture();
  });

  return (
    <View style={styles.root} onLayout={onLayout}>
      <GestureDetector gesture={Gesture.Race(pan, tap)}>
        <View style={StyleSheet.absoluteFill}>
          <Canvas style={StyleSheet.absoluteFill}>
            <Picture picture={picture} />
          </Canvas>
        </View>
      </GestureDetector>
      <View pointerEvents="none" style={[styles.header, { top: insets.top + 22 }]}>
        <Text style={styles.kicker}>Table No. 4</Text>
        <Text style={styles.title}>Domino Run</Text>
      </View>
      <View pointerEvents="none" style={[styles.footer, { bottom: insets.bottom + 18 }]}>
        <Text style={styles.hint}>
          {fallen ? 'Sweep across to stand them back up' : 'Tap the first tile · drag to lay a new run'}
        </Text>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: '#07090a', overflow: 'hidden' },
  header: { position: 'absolute', left: 24, right: 24, gap: 4 },
  kicker: { color: '#c9a978', fontSize: 11, fontWeight: '700', letterSpacing: 1.6, textTransform: 'uppercase' },
  title: { color: '#f3ead8', fontSize: 28, fontWeight: '600', letterSpacing: -0.2 },
  footer: { position: 'absolute', left: 16, right: 16, alignItems: 'center' },
  hint: {
    color: 'rgba(243,234,216,0.82)',
    fontSize: 13,
    fontWeight: '500',
    letterSpacing: 0.2,
    textShadowColor: 'rgba(0,0,0,0.5)',
    textShadowRadius: 4,
    textShadowOffset: { width: 0, height: 1 },
  },
});
