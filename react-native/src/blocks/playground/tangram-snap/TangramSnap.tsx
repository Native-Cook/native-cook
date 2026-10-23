import {
  BlendMode,
  BlurStyle,
  Canvas,
  ClipOp,
  matchFont,
  PaintStyle,
  Picture,
  Skia,
  StrokeJoin,
  type SkCanvas,
  type SkPath,
  type SkPicture,
  TileMode,
} from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { useEffect, useMemo, useState } from 'react';
import { type LayoutChangeEvent, StyleSheet, Text, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import { useDerivedValue, useFrameCallback, useReducedMotion, useSharedValue } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

import {
  AUTO_SECOND,
  copyPieces,
  createPieces,
  inset,
  insideConvex,
  LOOP,
  LOOSE,
  LOOSE_HALF_H,
  LOOSE_HALF_W,
  MAX_HALF_H,
  MAX_HALF_W,
  MORPH_AT,
  MORPH_TIME,
  nearestTurn,
  PIECES,
  type Pieces,
  PLACE_EVERY,
  PLACE_FROM,
  PLACE_TIME,
  raise,
  RESET_AT,
  SHAPES,
  SILHOUETTES,
  slotPose,
  stepPieces,
  turnMatches,
} from './tangramModel';

type Props = {
  /** Sets the cat piece by piece, fills it, turns it into the swan, takes it apart, on a loop. */
  autoPlay?: boolean;
};

// One fixed clock for the springs and the autoPlay script.
const TICK = 1 / 60;
const MAX_TICKS = 3;
const QUARTER = Math.PI / 4;

// A piece within SNAP of its slot (pt) at the slot's turn snaps in on release; within MAGNET it
// is already pulled towards it while held, at most MAGNET_PULL of the way.
const SNAP = 34;
const MAGNET = 64;
const MAGNET_PULL = 0.7;
// A turn within this of the slot's (rad) still counts as matching.
const TURN_SLACK = 0.3;
// Seconds of speed a release carries a piece on.
const THROW = 0.09;
// Pieces flying home leave one after another, this far apart (s), lifted for LIFT_TIME s.
const SCATTER_GAP = 0.06;
const LIFT_TIME = 0.28;
// Solving: the colour floods out from the last piece over FILL_TIME s.
const FILL_TIME = 1.1;
// A user's next puzzle: the solved figure turns into it, holds, then comes apart.
const NEXT_HOLD = 1.1;
// After a touch, autoPlay waits this long, then takes the figure apart and starts again.
const USER_HOLD = 5;
// Lacquer: inset of the bevel (pt), gap between pieces (pt), lift scale.
const BEVEL = 3.4;
const GAP = 0.9;
const LIFT_GROW = 0.05;

const SANS = process.env.EXPO_OS === 'ios' ? 'Avenir Next' : 'sans-serif';
const SERIF = process.env.EXPO_OS === 'ios' ? 'Baskerville' : 'serif';
const INK = '#f2e9dc';
const MUTED = '#a29483';
const BOARD = ['#2a231d', '#171310', '#0c0a08'];
// Lacquer colours, in PIECES order.
const LACQUER = [
  [196, 64, 42],
  [44, 62, 116],
  [210, 154, 44],
  [46, 126, 98],
  [122, 50, 88],
  [42, 136, 152],
  [222, 200, 160],
];
const LIGHT = [-0.6, -0.8];

// Lacquered wood: grain rings from value noise, darkening the tint; fixed maths, no loops.
const LACQUER_SKSL = `
uniform float3 tint;
uniform float seed;
uniform float grain;
float hash(float2 p) { return fract(sin(dot(p, float2(127.1, 311.7))) * 43758.5453); }
float noise(float2 p) {
  float2 i = floor(p);
  float2 f = fract(p);
  float2 u = f * f * (3.0 - 2.0 * f);
  return mix(mix(hash(i), hash(i + float2(1.0, 0.0)), u.x), mix(hash(i + float2(0.0, 1.0)), hash(i + float2(1.0, 1.0)), u.x), u.y);
}
half4 main(float2 p) {
  float2 q = p * float2(0.035, 0.008) + float2(seed, seed * 0.37);
  float n = noise(q * float2(3.0, 0.6)) * 0.65 + noise(q * float2(9.0, 1.4)) * 0.35;
  float rings = fract(q.x * 2.6 + n * 1.9);
  float g = smoothstep(0.0, 0.75, rings) * (1.0 - smoothstep(0.75, 1.0, rings));
  float shade = mix(1.0, 0.9 + 0.14 * g + 0.08 * (n - 0.5), grain);
  return half4(half3(tint * shade), 1.0);
}`;
// Null if the effect fails to compile: the pieces then fall back to their flat lacquer colour.
const LACQUER_FX = Skia.RuntimeEffect.Make(LACQUER_SKSL);

type Layout = {
  unit: number;
  boardX: number;
  boardY: number;
  looseX: number;
  looseY: number;
  thumbY: number;
  width: number;
  height: number;
};

type Scene = {
  pieces: Pieces;
  rest: Pieces;
  backdrop: SkPicture;
  /** The silhouette shown, and the morph to another (0…1; 1 when settled). */
  puzzle: number;
  from: number;
  morph: number;
  /** Slot poses at the morph's start and end (pt, rad, flip). */
  ax: Float64Array;
  ay: Float64Array;
  aa: Float64Array;
  af: Float64Array;
  bx: Float64Array;
  by: Float64Array;
  ba: Float64Array;
  bf: Float64Array;
  /** Pieces that ride the morph in their slots, with their own start and end turns. */
  ride: Uint8Array;
  pa: Float64Array;
  pf: Float64Array;
  qa: Float64Array;
  qf: Float64Array;
  /** Solving: whether every piece is in, and the colour's flood (0…1) and where it starts. */
  solved: boolean;
  fill: number;
  revealX: number;
  revealY: number;
  /** Per piece: seconds until it flies home (-1 none), and seconds left lifted. */
  scatter: Float64Array;
  lifted: Float64Array;
  /** Per slot: seconds since a piece snapped in (for the pulse), -1 none. */
  pulse: Float64Array;
  /** The piece in the finger (-1), where it was grabbed, and the slot it is drawn towards. */
  held: number;
  grabX: number;
  grabY: number;
  magnet: number;
  /** The piece two fingers turn, and its turn when they landed. */
  turning: number;
  turnFrom: number;
  /** The piece the script is carrying, from where, to which slot. */
  carry: number;
  carryX: number;
  carryY: number;
  carryA0: number;
  carryA: number;
  carryF: number;
  carryAt: number;
  /** A user's next puzzle: seconds until the solved figure comes apart (-1 none). */
  apartIn: number;
  /** Gesture events already handled. */
  seen: number[];
  /** Fingers on the pan, and the held piece's target before the magnet (re-anchored when they change). */
  fingers: number;
  rawX: number;
  rawY: number;
};

function rgba(c: number[], a: number) {
  'worklet';
  return new Float32Array([c[0] / 255, c[1] / 255, c[2] / 255, Math.max(0, Math.min(1, a))]);
}

function hexRGB(hex: string) {
  'worklet';
  const v = parseInt(hex.slice(1), 16);
  return [(v >> 16) & 255, (v >> 8) & 255, v & 255];
}

function mixRGB(a: number[], b: number[], t: number) {
  'worklet';
  return [a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t];
}

function easeInOut(t: number) {
  'worklet';
  const u = Math.min(1, Math.max(0, t));
  return u < 0.5 ? 4 * u * u * u : 1 - Math.pow(-2 * u + 2, 3) / 2;
}

/** Whether a clock that went from `before` to `t` passed the moment `at`. */
function crossed(before: number, t: number, at: number) {
  'worklet';
  return before < at && t >= at;
}

function z7() {
  'worklet';
  return new Float64Array(7);
}

function smooth(t: number) {
  'worklet';
  const u = Math.min(1, Math.max(0, t));
  return u * u * (3 - 2 * u);
}

/** Corners of shape `k` at a pose, with the flip drawn as a squeeze of the local x (cos π·flip). */
function corners(k: number, x: number, y: number, angle: number, flip: number, unit: number, grow: number) {
  'worklet';
  const shape = SHAPES[k];
  const c = Math.cos(angle);
  const s = Math.sin(angle);
  const sx = Math.cos(Math.PI * flip);
  const out: number[] = [];
  for (let i = 0; i < shape.length; i += 2) {
    const lx = shape[i] * unit * grow * sx;
    const ly = shape[i + 1] * unit * grow;
    out.push(x + lx * c - ly * s, y + lx * s + ly * c);
  }
  return out;
}

function polygon(points: number[]): SkPath {
  'worklet';
  const b = Skia.PathBuilder.Make();
  b.moveTo(points[0], points[1]);
  for (let i = 2; i < points.length; i += 2) b.lineTo(points[i], points[i + 1]);
  b.close();
  return b.build();
}

/** The board: dark lacquered wood, lit in a soft pool over the figure, and a felt-lined well. */
function makeBackdrop(l: Layout): SkPicture {
  'worklet';
  const recorder = Skia.PictureRecorder();
  const canvas = recorder.beginRecording(Skia.XYWHRect(0, 0, l.width, l.height));
  const board = Skia.Paint();
  board.setShader(
    Skia.Shader.MakeRadialGradient(
      Skia.Point(l.boardX, l.boardY),
      Math.max(l.width, l.height) * 0.8,
      BOARD.map((c) => Skia.Color(c)),
      [0, 0.5, 1],
      TileMode.Clamp,
    ),
  );
  canvas.drawRect(Skia.XYWHRect(0, 0, l.width, l.height), board);
  // The well that holds the loose pieces.
  const w = (LOOSE_HALF_W + 0.55) * l.unit;
  const h = (LOOSE_HALF_H + 0.4) * l.unit;
  const well = Skia.RRectXY(Skia.XYWHRect(l.looseX - w, l.looseY - h, w * 2, h * 2), 22, 22);
  const felt = Skia.Paint();
  felt.setAntiAlias(true);
  felt.setColor(Skia.Color('rgba(0, 0, 0, 0.22)'));
  canvas.drawRRect(well, felt);
  canvas.save();
  canvas.clipRRect(well, ClipOp.Intersect, true);
  const inner = Skia.Paint();
  inner.setAntiAlias(true);
  inner.setStyle(PaintStyle.Stroke);
  inner.setStrokeWidth(14);
  inner.setColor(Skia.Color('rgba(0, 0, 0, 0.35)'));
  inner.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 8, true));
  canvas.drawRRect(Skia.RRectXY(Skia.XYWHRect(l.looseX - w - 4, l.looseY - h - 4, w * 2 + 8, h * 2 + 8), 26, 26), inner);
  canvas.restore();
  const lip = Skia.Paint();
  lip.setAntiAlias(true);
  lip.setStyle(PaintStyle.Stroke);
  lip.setStrokeWidth(1);
  lip.setColor(Skia.Color('rgba(255, 236, 210, 0.07)'));
  canvas.drawRRect(well, lip);
  return recorder.finishRecordingAsPicture();
}

/** One lacquered piece: its shadow, the wood under the lacquer, bevels, gloss and edge. */
function drawPiece(canvas: SkCanvas, k: number, points: number[], x: number, y: number, angle: number, flip: number, lift: number, solid: number, solidRGB: number[]) {
  'worklet';
  const body = polygon(points);
  // The shadow, further and softer as the piece lifts.
  const shadow = Skia.Paint();
  shadow.setAntiAlias(true);
  shadow.setColor(rgba([0, 0, 0], 0.5 - 0.15 * lift));
  shadow.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 2.2 + 7 * lift, true));
  canvas.save();
  canvas.translate(2 + 8 * lift, 3 + 13 * lift);
  canvas.drawPath(body, shadow);
  canvas.restore();

  const tint = mixRGB(LACQUER[k], solidRGB, solid);
  const fill = Skia.Paint();
  fill.setAntiAlias(true);
  const local = Skia.Matrix();
  local.translate(x, y);
  local.rotate(angle);
  if (LACQUER_FX) fill.setShader(LACQUER_FX.makeShader([tint[0] / 255, tint[1] / 255, tint[2] / 255, k * 3.7 + 1.3, 1 - solid], local));
  else fill.setColor(rgba(tint, 1));
  canvas.drawPath(body, fill);

  // Bevels: each edge's slope lit or shaded by how it faces the light.
  const fade = 1 - solid;
  // A piece seen edge-on mid-flip is too thin for a bevel (the inset would fold over itself).
  if (fade > 0.01 && Math.abs(Math.cos(Math.PI * flip)) > 0.2) {
    const inner = inset(points, BEVEL);
    const n = points.length / 2;
    let area = 0;
    for (let i = 0; i < n; i += 1) {
      const j = (i + 1) % n;
      area += points[i * 2] * points[j * 2 + 1] - points[j * 2] * points[i * 2 + 1];
    }
    const side = area > 0 ? 1 : -1;
    for (let i = 0; i < n; i += 1) {
      const j = (i + 1) % n;
      const ex = points[j * 2] - points[i * 2];
      const ey = points[j * 2 + 1] - points[i * 2 + 1];
      const l = Math.hypot(ex, ey) || 1;
      // Outward normal.
      const nx = (ey / l) * side;
      const ny = (-ex / l) * side;
      const facing = nx * LIGHT[0] + ny * LIGHT[1];
      const strip = Skia.PathBuilder.Make();
      strip.moveTo(points[i * 2], points[i * 2 + 1]);
      strip.lineTo(points[j * 2], points[j * 2 + 1]);
      strip.lineTo(inner[j * 2], inner[j * 2 + 1]);
      strip.lineTo(inner[i * 2], inner[i * 2 + 1]);
      strip.close();
      const paint = Skia.Paint();
      paint.setAntiAlias(true);
      paint.setColor(facing > 0 ? rgba([255, 244, 226], 0.36 * facing * fade) : rgba([20, 8, 2], 0.42 * -facing * fade));
      canvas.drawPath(strip.build(), paint);
    }
  }
  // Gloss: the lacquer catches the light across its upper left.
  const gloss = Skia.Paint();
  gloss.setAntiAlias(true);
  const bounds = body.computeTightBounds();
  gloss.setShader(
    Skia.Shader.MakeLinearGradient(
      Skia.Point(bounds.x, bounds.y),
      Skia.Point(bounds.x + bounds.width * 0.7, bounds.y + bounds.height * 0.7),
      [Skia.Color('rgba(255, 255, 255, 0.24)'), Skia.Color('rgba(255, 255, 255, 0.04)'), Skia.Color('rgba(255, 255, 255, 0)')],
      [0, 0.45, 1],
      TileMode.Clamp,
    ),
  );
  canvas.drawPath(body, gloss);
  const edge = Skia.Paint();
  edge.setAntiAlias(true);
  edge.setStyle(PaintStyle.Stroke);
  edge.setStrokeWidth(0.8);
  edge.setStrokeJoin(StrokeJoin.Round);
  edge.setColor(rgba([12, 6, 2], 0.45 * fade + 0.1));
  canvas.drawPath(body, edge);
}

/**
 * A tangram on a dark lacquered board: seven wooden pieces and the faint outline of a figure
 * (a cat, a boat, a runner, a swan). Drag a piece and it is drawn into its place when close;
 * tap to turn it 45°, double-tap to flip it, turn it with two fingers. When the last piece is in,
 * colour floods the figure and the pieces melt into one solid shape. The figures below morph the
 * outline, or the finished figure itself, into the next.
 */
export function TangramSnap({ autoPlay = false }: Props) {
  const insets = useSafeAreaInsets();
  const reducedMotion = useReducedMotion();
  const auto = autoPlay && !reducedMotion;
  const [size, setSize] = useState<{ width: number; height: number } | null>(null);
  const [puzzleIndex, setPuzzleIndex] = useState(0);
  const [isSolved, setIsSolved] = useState(false);
  const fonts = useMemo(
    () => ({
      count: matchFont({ fontFamily: SANS, fontSize: 22, fontWeight: '600' }),
      small: matchFont({ fontFamily: SANS, fontSize: 10.5, fontWeight: '600' }),
    }),
    [],
  );

  const width = size?.width ?? 0;
  const height = size?.height ?? 0;
  const layout = useMemo<Layout>(() => {
    const headerBottom = insets.top + 104;
    const thumbY = height - insets.bottom - 38;
    const room = thumbY - 34 - headerBottom;
    // The board and the well stacked between the header and the figures, as large as fits.
    const unit = Math.max(
      20,
      Math.min((width - 26) / (LOOSE_HALF_W * 2 + 0.9), (width - 40) / (MAX_HALF_W * 2), room / (MAX_HALF_H * 2 + LOOSE_HALF_H * 2 + 1.4)),
    );
    const boardY = headerBottom + MAX_HALF_H * unit;
    const looseY = (boardY + MAX_HALF_H * unit + 0.5 * unit + thumbY - 34) / 2;
    return { unit, boardX: width / 2, boardY, looseX: width / 2 + 0.05 * unit, looseY, thumbY, width, height };
  }, [width, height, insets.top, insets.bottom]);

  const scene = useSharedValue<Scene | null>(null);
  const frameNo = useSharedValue(0);
  const acc = useSharedValue(0);
  const clock = useSharedValue(0);
  const hold = useSharedValue(0);
  const looping = useSharedValue(auto);
  // Gesture events, as counters with their data; the tick handles each once.
  const downN = useSharedValue(0);
  const downX = useSharedValue(0);
  const downY = useSharedValue(0);
  const fingerX = useSharedValue(0);
  const fingerY = useSharedValue(0);
  const fingers = useSharedValue(1);
  const upN = useSharedValue(0);
  const upVX = useSharedValue(0);
  const upVY = useSharedValue(0);
  const tapN = useSharedValue(0);
  const tapX = useSharedValue(0);
  const tapY = useSharedValue(0);
  const dblN = useSharedValue(0);
  const dblX = useSharedValue(0);
  const dblY = useSharedValue(0);
  const turnN = useSharedValue(0);
  const turnEndN = useSharedValue(0);
  const turnX = useSharedValue(0);
  const turnY = useSharedValue(0);
  const turnBy = useSharedValue(0);

  useEffect(() => {
    looping.set(auto);
    hold.set(0);
    clock.set(0);
  }, [auto, clock, hold, looping]);

  useEffect(() => {
    scene.set(null);
  }, [layout, scene]);

  const buzz = (kind: number) => {
    if (process.env.EXPO_OS === 'web') return;
    if (kind === 0) Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
    else if (kind === 1) Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success).catch(() => {});
    else if (kind === 2) Haptics.selectionAsync().catch(() => {});
    else Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Soft).catch(() => {});
  };
  const showPuzzle = (index: number) => setPuzzleIndex(index);
  const showSolved = (solved: boolean) => setIsSolved(solved);

  const { unit, boardX, boardY, looseX, looseY, thumbY } = layout;

  /** Slot `k` of silhouette `p` on screen, at its first listed pose. */
  const slotAt = (p: number, k: number) => {
    'worklet';
    const slot = SILHOUETTES[p].slots[k];
    return { x: boardX + slot.x * unit, y: boardY + slot.y * unit, angle: (slot.turns[0] * Math.PI) / 4, flip: slot.flips[0] };
  };

  /** Where slot `k` is drawn now: settled, or partway through a morph. */
  const slotNow = (s: Scene, k: number) => {
    'worklet';
    if (s.morph >= 1) return slotAt(s.puzzle, k);
    const e = reducedMotion ? smooth(s.morph) : easeInOut(s.morph);
    return {
      x: s.ax[k] + (s.bx[k] - s.ax[k]) * e,
      y: s.ay[k] + (s.by[k] - s.ay[k]) * e,
      angle: s.aa[k] + (s.ba[k] - s.aa[k]) * e,
      flip: s.af[k] + (s.bf[k] - s.af[k]) * e,
    };
  };

  /** Sends piece `i` home to the well, after `delay` s. */
  const sendHome = (s: Scene, i: number, delay: number) => {
    'worklet';
    const p = s.pieces;
    p.slot[i] = -1;
    s.scatter[i] = delay;
  };

  /** Takes piece `i` out of its slot (the figure is no longer whole). */
  const unsnap = (s: Scene, i: number) => {
    'worklet';
    const p = s.pieces;
    if (p.slot[i] < 0) return;
    p.slot[i] = -1;
    if (s.solved) {
      s.solved = false;
      scheduleOnRN(showSolved, false);
    }
  };

  /** Snaps piece `i` into a matching free slot near (x, y) at its turn, if there is one. */
  const trySnap = (s: Scene, i: number, x: number, y: number, user: boolean) => {
    'worklet';
    if (s.morph < 1) return false;
    const p = s.pieces;
    const sil = SILHOUETTES[s.puzzle];
    const flip = p.tf[i] > 0.5 ? 1 : 0;
    let best = -1;
    let bestD = SNAP;
    for (let k = 0; k < sil.slots.length; k += 1) {
      if (PIECES[k] !== PIECES[i]) continue;
      let taken = false;
      for (let j = 0; j < p.slot.length; j += 1) if (j !== i && p.slot[j] === k) taken = true;
      if (taken || !turnMatches(sil.slots[k], p.ta[i], flip, TURN_SLACK)) continue;
      const at = slotAt(s.puzzle, k);
      const d = Math.hypot(at.x - x, at.y - y);
      if (d < bestD) {
        bestD = d;
        best = k;
      }
    }
    if (best < 0) return false;
    const pose = slotPose(sil.slots[best], p.ta[i], flip, false);
    if (!pose) return false;
    const at = slotAt(s.puzzle, best);
    p.slot[i] = best;
    p.mode[i] = 2;
    p.tx[i] = at.x;
    p.ty[i] = at.y;
    p.ta[i] = pose.angle;
    p.tl[i] = 0;
    s.pulse[best] = 0;
    if (user) scheduleOnRN(buzz, 0);
    // Every piece in: the figure is whole.
    let all = true;
    for (let j = 0; j < p.slot.length; j += 1) if (p.slot[j] < 0) all = false;
    if (all && !s.solved) {
      s.solved = true;
      s.revealX = at.x;
      s.revealY = at.y;
      scheduleOnRN(showSolved, true);
      if (user) scheduleOnRN(buzz, 1);
    }
    return true;
  };

  /** Morphs to silhouette `to`. A solved figure carries its pieces along; otherwise they go home. */
  const startMorph = (s: Scene, to: number, keepSolved: boolean) => {
    'worklet';
    const p = s.pieces;
    const from = s.puzzle;
    for (let k = 0; k < 7; k += 1) {
      const a = slotNow(s, k);
      s.ax[k] = a.x;
      s.ay[k] = a.y;
      s.aa[k] = a.angle;
      s.af[k] = a.flip;
      const b = slotAt(to, k);
      const pose = slotPose(SILHOUETTES[to].slots[k], a.angle, a.flip > 0.5 ? 1 : 0, true)!;
      s.bx[k] = b.x;
      s.by[k] = b.y;
      s.ba[k] = pose.angle;
      s.bf[k] = pose.flip;
    }
    const carry = keepSolved && s.solved;
    for (let i = 0; i < 7; i += 1) {
      s.ride[i] = 0;
      if (carry && p.slot[i] >= 0) {
        const k = p.slot[i];
        s.ride[i] = 1;
        s.pa[i] = p.ta[i];
        s.pf[i] = p.tf[i];
        const pose = slotPose(SILHOUETTES[to].slots[k], p.ta[i], p.tf[i] > 0.5 ? 1 : 0, true)!;
        s.qa[i] = pose.angle;
        s.qf[i] = pose.flip;
        p.mode[i] = 1;
      } else {
        unsnap(s, i);
        sendHome(s, i, SCATTER_GAP * i);
      }
    }
    if (!carry && s.solved) {
      s.solved = false;
      scheduleOnRN(showSolved, false);
    }
    s.from = from;
    s.puzzle = to;
    s.morph = to === from && !carry ? 1 : 0;
    if (to !== from) scheduleOnRN(showPuzzle, to);
  };

  /** The topmost piece under (x, y), or the nearest within reach. */
  const pieceAt = (s: Scene, x: number, y: number) => {
    'worklet';
    const p = s.pieces;
    for (let k = p.order.length - 1; k >= 0; k -= 1) {
      const i = p.order[k];
      if (insideConvex(corners(i, p.x[i], p.y[i], p.angle[i], p.flip[i], unit, 1.08), x, y)) return i;
    }
    let best = -1;
    let bestD = unit * 0.55;
    for (let i = 0; i < 7; i += 1) {
      const d = Math.hypot(p.x[i] - x, p.y[i] - y);
      if (d < bestD) {
        bestD = d;
        best = i;
      }
    }
    return best;
  };

  /** Lets go of the scripted piece where it is (a touch interrupts the script). */
  const dropCarry = (s: Scene) => {
    'worklet';
    const i = s.carry;
    if (i < 0) return;
    const p = s.pieces;
    p.mode[i] = 0;
    p.tx[i] = p.x[i];
    p.ty[i] = p.y[i];
    p.ta[i] = Math.round(p.ta[i] / QUARTER) * QUARTER;
    p.tf[i] = p.tf[i] > 0.5 ? 1 : 0;
    p.tl[i] = 0;
    s.carry = -1;
  };

  const touched = (s: Scene) => {
    'worklet';
    hold.set(USER_HOLD);
    dropCarry(s);
  };

  /** Gesture events, in the order they happened. */
  const handleInput = (s: Scene) => {
    'worklet';
    const p = s.pieces;
    if (downN.get() !== s.seen[0]) {
      s.seen[0] = downN.get();
      touched(s);
      const i = pieceAt(s, downX.get(), downY.get());
      if (i >= 0 && s.morph >= 1) {
        unsnap(s, i);
        s.scatter[i] = -1;
        s.held = i;
        s.grabX = p.x[i] - downX.get();
        s.grabY = p.y[i] - downY.get();
        fingerX.set(downX.get());
        fingerY.set(downY.get());
        s.fingers = fingers.get();
        s.rawX = p.x[i];
        s.rawY = p.y[i];
        p.mode[i] = 1;
        p.tl[i] = 1;
        raise(p, i);
      }
    }
    if (turnN.get() !== s.seen[3]) {
      s.seen[3] = turnN.get();
      touched(s);
      // Not during a morph: a piece riding it must keep its slot.
      const i = s.morph < 1 ? -1 : s.held >= 0 ? s.held : pieceAt(s, turnX.get(), turnY.get());
      s.turning = i;
      if (i >= 0) {
        unsnap(s, i);
        s.turnFrom = p.ta[i];
      }
    }
    if (s.turning >= 0) {
      p.ta[s.turning] = s.turnFrom + (reducedMotion ? 0 : 1) * turnBy.get();
      if (reducedMotion) p.ta[s.turning] = s.turnFrom + Math.round(turnBy.get() / QUARTER) * QUARTER;
    }
    if (turnEndN.get() !== s.seen[4]) {
      s.seen[4] = turnEndN.get();
      const i = s.turning;
      s.turning = -1;
      if (i >= 0) {
        p.ta[i] = Math.round(p.ta[i] / QUARTER) * QUARTER;
        scheduleOnRN(buzz, 2);
        if (s.held !== i) trySnap(s, i, p.tx[i], p.ty[i], true);
      }
    }
    if (upN.get() !== s.seen[1]) {
      s.seen[1] = upN.get();
      const i = s.held;
      s.held = -1;
      s.magnet = -1;
      if (i >= 0) {
        p.mode[i] = 0;
        p.tl[i] = 0;
        if (s.turning !== i) p.ta[i] = Math.round(p.ta[i] / QUARTER) * QUARTER;
        // The finger's speed carries on into the spring, and decides where it settles.
        const vx = reducedMotion ? 0 : upVX.get();
        const vy = reducedMotion ? 0 : upVY.get();
        p.vx[i] = vx;
        p.vy[i] = vy;
        const gx = p.tx[i] + vx * THROW;
        const gy = p.ty[i] + vy * THROW;
        if (!trySnap(s, i, p.tx[i], p.ty[i], true) && !trySnap(s, i, gx, gy, true)) {
          const m = unit * 0.6;
          p.tx[i] = Math.min(width - m, Math.max(m, gx));
          p.ty[i] = Math.min(thumbY - 30, Math.max(insets.top + 40, gy));
        }
      }
    }
    if (tapN.get() !== s.seen[2]) {
      s.seen[2] = tapN.get();
      touched(s);
      // The figures along the bottom: tap one to morph into it.
      if (Math.abs(tapY.get() - thumbY) < 30) {
        const k = Math.round((tapX.get() - width / 2) / 64 + 1.5);
        if (k >= 0 && k < SILHOUETTES.length && Math.abs(tapX.get() - (width / 2 + (k - 1.5) * 64)) < 30 && k !== s.puzzle && s.morph >= 1) {
          const solved = s.solved;
          startMorph(s, k, true);
          if (solved) s.apartIn = MORPH_TIME + NEXT_HOLD;
          scheduleOnRN(buzz, 3);
        }
      } else {
        const i = pieceAt(s, tapX.get(), tapY.get());
        if (i >= 0 && s.morph >= 1) {
          unsnap(s, i);
          p.ta[i] = Math.round(p.ta[i] / QUARTER) * QUARTER + QUARTER;
          raise(p, i);
          scheduleOnRN(buzz, 2);
          trySnap(s, i, p.tx[i], p.ty[i], true);
        }
      }
    }
    if (dblN.get() !== s.seen[5]) {
      s.seen[5] = dblN.get();
      touched(s);
      const i = pieceAt(s, dblX.get(), dblY.get());
      if (i >= 0 && s.morph >= 1) {
        unsnap(s, i);
        p.tf[i] = p.tf[i] > 0.5 ? 0 : 1;
        raise(p, i);
        scheduleOnRN(buzz, 2);
        trySnap(s, i, p.tx[i], p.ty[i], true);
      }
    }
    // The held piece follows the finger, pulled towards a matching slot when it is close.
    if (s.held >= 0) {
      const i = s.held;
      // A finger landing or lifting moves the pan's centroid: keep the piece where it is.
      if (fingers.get() !== s.fingers) {
        s.fingers = fingers.get();
        s.grabX = s.rawX - fingerX.get();
        s.grabY = s.rawY - fingerY.get();
      }
      let tx = fingerX.get() + s.grabX;
      let ty = fingerY.get() + s.grabY;
      s.rawX = tx;
      s.rawY = ty;
      s.magnet = -1;
      const sil = SILHOUETTES[s.puzzle];
      const flip = p.tf[i] > 0.5 ? 1 : 0;
      for (let k = 0; k < sil.slots.length && s.morph >= 1; k += 1) {
        if (PIECES[k] !== PIECES[i] || !turnMatches(sil.slots[k], p.ta[i], flip, TURN_SLACK)) continue;
        let taken = false;
        for (let j = 0; j < 7; j += 1) if (j !== i && p.slot[j] === k) taken = true;
        if (taken) continue;
        const at = slotAt(s.puzzle, k);
        const d = Math.hypot(at.x - tx, at.y - ty);
        if (d < MAGNET) {
          const pull = MAGNET_PULL * Math.pow(1 - d / MAGNET, 1.5);
          tx += (at.x - tx) * pull;
          ty += (at.y - ty) * pull;
          s.magnet = k;
          break;
        }
      }
      p.tx[i] = tx;
      p.ty[i] = ty;
    }
  };

  /** autoPlay: the script between `before` and `t`. */
  const script = (s: Scene, before: number, t: number) => {
    'worklet';
    const p = s.pieces;
    for (let i = 0; i < 7; i += 1) {
      const at = PLACE_FROM + PLACE_EVERY * i;
      if (crossed(before, t, at) && s.puzzle === 0 && s.morph >= 1 && p.slot[i] < 0) {
        const pose = slotPose(SILHOUETTES[0].slots[i], p.ta[i], p.tf[i] > 0.5 ? 1 : 0, true)!;
        s.carry = i;
        s.carryX = p.x[i];
        s.carryY = p.y[i];
        s.carryA0 = p.ta[i];
        s.carryA = pose.angle;
        s.carryF = pose.flip;
        s.carryAt = at;
        p.mode[i] = 1;
        p.tl[i] = 1;
        p.tf[i] = pose.flip;
        raise(p, i);
      }
    }
    if (s.carry >= 0) {
      const i = s.carry;
      const u = (t - s.carryAt) / PLACE_TIME;
      const at = slotAt(0, i);
      if (u >= 1) {
        p.ta[i] = s.carryA;
        s.carry = -1;
        p.mode[i] = 0;
        p.tx[i] = at.x;
        p.ty[i] = at.y;
        trySnap(s, i, at.x, at.y, false);
      } else {
        const e = easeInOut(u);
        // An arc: up off the well and down into the slot.
        p.tx[i] = s.carryX + (at.x - s.carryX) * e;
        p.ty[i] = s.carryY + (at.y - s.carryY) * e - Math.sin(Math.PI * e) * unit * 0.6;
        p.ta[i] = s.carryA0 + (s.carryA - s.carryA0) * Math.min(1, e * 1.3);
      }
    }
    if (crossed(before, t, MORPH_AT) && s.solved) startMorph(s, AUTO_SECOND, true);
    if (crossed(before, t, RESET_AT)) startMorph(s, 0, false);
  };

  const tick = (s: Scene) => {
    'worklet';
    const p = s.pieces;
    handleInput(s);
    if (hold.get() > 0) {
      if (s.held < 0 && s.turning < 0) hold.set(hold.get() - TICK);
      // Back to the loop: take the figure apart and start from the empty cat again.
      if (hold.get() <= 0 && looping.get()) clock.set(RESET_AT - TICK);
    } else if (looping.get()) {
      const before = clock.get();
      let t = before + TICK;
      if (t >= LOOP) {
        t -= LOOP;
        copyPieces(s.rest, p);
        s.puzzle = 0;
        s.morph = 1;
        s.fill = 0;
        s.solved = false;
        s.scatter.fill(-1);
        s.lifted.fill(0);
        s.pulse.fill(-1);
        s.ride.fill(0);
        s.carry = -1;
        s.apartIn = -1;
      }
      clock.set(t);
      script(s, before, t);
    }
    if (s.apartIn > 0) {
      s.apartIn -= TICK;
      if (s.apartIn <= 0) startMorph(s, s.puzzle, false);
    }
    // The morph, and the pieces riding it.
    if (s.morph < 1) {
      s.morph = Math.min(1, s.morph + TICK / (reducedMotion ? 0.3 : MORPH_TIME));
      const e = reducedMotion ? smooth(s.morph) : easeInOut(s.morph);
      for (let i = 0; i < 7; i += 1) {
        if (!s.ride[i]) continue;
        const k = p.slot[i];
        if (k < 0) {
          s.ride[i] = 0;
          p.mode[i] = 0;
          continue;
        }
        const at = slotNow(s, k);
        p.tx[i] = at.x;
        p.ty[i] = at.y;
        p.ta[i] = s.pa[i] + (s.qa[i] - s.pa[i]) * e;
        p.tf[i] = s.pf[i] + (s.qf[i] - s.pf[i]) * e;
        if (s.morph >= 1) {
          p.mode[i] = 2;
          p.ta[i] = s.qa[i];
          p.tf[i] = s.qf[i];
          s.ride[i] = 0;
        }
      }
    }
    // Pieces flying home, one after another, lifted on the way.
    for (let i = 0; i < 7; i += 1) {
      if (s.scatter[i] >= 0) {
        s.scatter[i] -= TICK;
        if (s.scatter[i] < 0) {
          const home = LOOSE[i];
          p.mode[i] = 0;
          p.tx[i] = looseX + home[0] * unit;
          p.ty[i] = looseY + home[1] * unit;
          p.ta[i] = nearestTurn((home[2] * Math.PI) / 4, p.ta[i]);
          p.tf[i] = home[3];
          p.tl[i] = 1;
          s.lifted[i] = LIFT_TIME;
          s.scatter[i] = -1;
        }
      }
      if (s.lifted[i] > 0) {
        s.lifted[i] -= TICK;
        if (s.lifted[i] <= 0 && s.held !== i && s.carry !== i) p.tl[i] = 0;
      }
      if (s.pulse[i] >= 0) {
        s.pulse[i] += TICK;
        if (s.pulse[i] > 0.6) s.pulse[i] = -1;
      }
    }
    const goal = s.solved ? 1 : 0;
    s.fill = goal > s.fill ? Math.min(1, s.fill + TICK / FILL_TIME) : Math.max(0, s.fill - TICK / (FILL_TIME * 0.6));
    stepPieces(p, TICK, reducedMotion);
  };

  useFrameCallback((frame) => {
    if (width === 0) return;
    let s = scene.get();
    if (s === null) {
      const pieces = createPieces();
      for (let i = 0; i < 7; i += 1) {
        const home = LOOSE[i];
        pieces.x[i] = looseX + home[0] * unit;
        pieces.y[i] = looseY + home[1] * unit;
        pieces.angle[i] = (home[2] * Math.PI) / 4;
        pieces.flip[i] = home[3];
        pieces.tx[i] = pieces.x[i];
        pieces.ty[i] = pieces.y[i];
        pieces.ta[i] = pieces.angle[i];
        pieces.tf[i] = pieces.flip[i];
      }
      const rest = createPieces();
      copyPieces(pieces, rest);
      s = {
        pieces,
        rest,
        backdrop: makeBackdrop(layout),
        puzzle: 0,
        from: 0,
        morph: 1,
        ax: z7(),
        ay: z7(),
        aa: z7(),
        af: z7(),
        bx: z7(),
        by: z7(),
        ba: z7(),
        bf: z7(),
        ride: new Uint8Array(7),
        pa: z7(),
        pf: z7(),
        qa: z7(),
        qf: z7(),
        solved: false,
        fill: 0,
        revealX: boardX,
        revealY: boardY,
        scatter: new Float64Array(7).fill(-1),
        lifted: z7(),
        pulse: new Float64Array(7).fill(-1),
        held: -1,
        grabX: 0,
        grabY: 0,
        magnet: -1,
        turning: -1,
        turnFrom: 0,
        carry: -1,
        carryX: 0,
        carryY: 0,
        carryA0: 0,
        carryA: 0,
        carryF: 0,
        carryAt: 0,
        apartIn: -1,
        seen: [downN.get(), upN.get(), tapN.get(), turnN.get(), turnEndN.get(), dblN.get()],
        fingers: 1,
        rawX: 0,
        rawY: 0,
      };
      scene.set(s);
      clock.set(0);
      scheduleOnRN(showPuzzle, 0);
      scheduleOnRN(showSolved, false);
    }
    const dt = Math.min((frame.timeSincePreviousFrame ?? 16) / 1000, MAX_TICKS * TICK);
    let pending = acc.get() + dt;
    let ticks = 0;
    while (pending >= TICK && ticks < MAX_TICKS) {
      tick(s);
      pending -= TICK;
      ticks += 1;
    }
    acc.set(Math.min(pending, TICK));
    if (ticks > 0) frameNo.set(frameNo.get() + 1);
  });

  const picture = useDerivedValue(() => {
    frameNo.get();
    const recorder = Skia.PictureRecorder();
    const canvas = recorder.beginRecording(Skia.XYWHRect(0, 0, width, height));
    const s = scene.get();
    if (!s || width === 0) return recorder.finishRecordingAsPicture();
    const p = s.pieces;
    canvas.drawPicture(s.backdrop);

    // The figure: the union of its slots, wherever the morph has them.
    const union = Skia.PathBuilder.Make();
    for (let k = 0; k < 7; k += 1) {
      const at = slotNow(s, k);
      const c = corners(k, at.x, at.y, at.angle, at.flip, unit, 1);
      union.moveTo(c[0], c[1]);
      for (let i = 2; i < c.length; i += 2) union.lineTo(c[i], c[i + 1]);
      union.close();
    }
    const figure = union.build();
    const fillShow = smooth(s.fill);
    const from = hexRGB(SILHOUETTES[s.from].color);
    const to = hexRGB(SILHOUETTES[s.puzzle].color);
    const solidRGB = s.morph < 1 ? mixRGB(from, to, easeInOut(s.morph)) : to;

    // The empty outline: a faint inlay and a fine line around its outer edge only.
    const outlineA = 1 - fillShow;
    if (outlineA > 0.01) {
      const inlay = Skia.Paint();
      inlay.setAntiAlias(true);
      inlay.setColor(rgba([255, 238, 214], 0.06 * outlineA));
      canvas.drawPath(figure, inlay);
      const layer = Skia.Paint();
      layer.setAlphaf(outlineA);
      canvas.saveLayer(layer);
      const line = Skia.Paint();
      line.setAntiAlias(true);
      line.setStyle(PaintStyle.Stroke);
      line.setStrokeWidth(2.6);
      line.setStrokeJoin(StrokeJoin.Round);
      line.setColor(rgba([255, 232, 204], 0.55));
      canvas.drawPath(figure, line);
      const cut = Skia.Paint();
      cut.setAntiAlias(true);
      cut.setBlendMode(BlendMode.Clear);
      canvas.drawPath(figure, cut);
      canvas.restore();
    }
    // The slot a held piece is being drawn into glows.
    if (s.magnet >= 0) {
      const at = slotNow(s, s.magnet);
      const glow = Skia.Paint();
      glow.setAntiAlias(true);
      glow.setColor(rgba(LACQUER[s.held >= 0 ? s.held : 0], 0.28));
      glow.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 6, true));
      canvas.drawPath(polygon(corners(s.magnet, at.x, at.y, at.angle, at.flip, unit, 1)), glow);
    }

    // Pieces, bottom first.
    for (let o = 0; o < 7; o += 1) {
      const i = p.order[o];
      const grow = 1 + LIFT_GROW * p.lift[i] - (GAP * (1 - fillShow)) / unit;
      const pts = corners(i, p.x[i], p.y[i], p.angle[i], p.flip[i], unit, grow);
      drawPiece(canvas, i, pts, p.x[i], p.y[i], p.angle[i], p.flip[i], p.lift[i], p.slot[i] >= 0 ? fillShow : 0, solidRGB);
    }

    // A snap sends a ring of light out from the slot.
    for (let k = 0; k < 7; k += 1) {
      if (s.pulse[k] < 0) continue;
      const u = s.pulse[k] / 0.6;
      const at = slotNow(s, k);
      const ring = Skia.Paint();
      ring.setAntiAlias(true);
      ring.setStyle(PaintStyle.Stroke);
      ring.setStrokeWidth(2);
      ring.setStrokeJoin(StrokeJoin.Round);
      ring.setColor(rgba([255, 240, 214], 0.6 * (1 - u)));
      canvas.drawPath(polygon(corners(k, at.x, at.y, at.angle, at.flip, unit, 1 + 0.18 * smooth(u))), ring);
    }

    // Solved: the colour floods out from the last piece and the figure becomes one shape.
    if (fillShow > 0.001) {
      const reach = Math.max(MAX_HALF_W, MAX_HALF_H) * unit * 2.6;
      canvas.save();
      if (fillShow < 1) {
        const circle = Skia.PathBuilder.Make().addCircle(s.revealX, s.revealY, reach * fillShow).build();
        canvas.clipPath(circle, ClipOp.Intersect, true);
      }
      const shade = Skia.Paint();
      shade.setAntiAlias(true);
      shade.setColor(rgba([0, 0, 0], 0.4 * fillShow));
      shade.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 9, true));
      canvas.save();
      canvas.translate(0, 6);
      canvas.drawPath(figure, shade);
      canvas.restore();
      const solid = Skia.Paint();
      solid.setAntiAlias(true);
      solid.setShader(
        Skia.Shader.MakeLinearGradient(
          Skia.Point(boardX - MAX_HALF_W * unit, boardY - MAX_HALF_H * unit),
          Skia.Point(boardX + MAX_HALF_W * unit, boardY + MAX_HALF_H * unit),
          [rgba(mixRGB(solidRGB, [255, 255, 255], 0.16), smooth(fillShow * 1.4)), rgba(mixRGB(solidRGB, [0, 0, 0], 0.18), smooth(fillShow * 1.4))],
          null,
          TileMode.Clamp,
        ),
      );
      canvas.drawPath(figure, solid);
      // A lacquer sheen over the whole figure.
      const sheen = Skia.Paint();
      sheen.setAntiAlias(true);
      sheen.setShader(
        Skia.Shader.MakeLinearGradient(
          Skia.Point(boardX - MAX_HALF_W * unit, boardY - MAX_HALF_H * unit),
          Skia.Point(boardX, boardY),
          [Skia.Color('rgba(255, 255, 255, 0.22)'), Skia.Color('rgba(255, 255, 255, 0)')],
          null,
          TileMode.Clamp,
        ),
      );
      canvas.drawPath(figure, sheen);
      canvas.restore();
    }

    // The figures to choose from, along the bottom.
    for (let f = 0; f < SILHOUETTES.length; f += 1) {
      const cx = width / 2 + (f - 1.5) * 64;
      const scale = 40 / (2 * Math.max(MAX_HALF_W, MAX_HALF_H));
      const mini = Skia.PathBuilder.Make();
      const sil = SILHOUETTES[f];
      for (let k = 0; k < 7; k += 1) {
        const slot = sil.slots[k];
        const c = corners(k, cx + slot.x * scale, thumbY + slot.y * scale, (slot.turns[0] * Math.PI) / 4, slot.flips[0], scale, 1);
        mini.moveTo(c[0], c[1]);
        for (let i = 2; i < c.length; i += 2) mini.lineTo(c[i], c[i + 1]);
        mini.close();
      }
      const current = f === s.puzzle;
      const paint = Skia.Paint();
      paint.setAntiAlias(true);
      paint.setColor(current ? rgba(hexRGB(sil.color), 0.95) : rgba([242, 233, 220], 0.28));
      canvas.drawPath(mini.build(), paint);
      if (current) {
        const dot = Skia.Paint();
        dot.setAntiAlias(true);
        dot.setColor(rgba([242, 233, 220], 0.8));
        canvas.drawCircle(cx, thumbY + 27, 2, dot);
      }
    }

    // Pieces in place, top right.
    let placedCount = 0;
    for (let i = 0; i < 7; i += 1) if (p.slot[i] >= 0) placedCount += 1;
    const value = `${placedCount} / 7`;
    const ink = Skia.Paint();
    ink.setAntiAlias(true);
    ink.setColor(Skia.Color(INK));
    const right = width - 24;
    const readTop = insets.top + 12;
    canvas.drawText(value, right - fonts.count.measureText(value).width, readTop + 38, ink, fonts.count);
    const muted = Skia.Paint();
    muted.setAntiAlias(true);
    muted.setColor(Skia.Color(MUTED));
    canvas.drawText('IN PLACE', right - fonts.small.measureText('IN PLACE').width, readTop + 11, muted, fonts.small);
    return recorder.finishRecordingAsPicture();
  });

  const pan = Gesture.Pan()
    .minDistance(2)
    .onBegin((event) => {
      downX.set(event.x);
      downY.set(event.y);
      fingers.set(event.numberOfPointers);
      downN.set(downN.get() + 1);
    })
    .onUpdate((event) => {
      fingerX.set(event.x);
      fingerY.set(event.y);
      fingers.set(event.numberOfPointers);
      upVX.set(event.velocityX);
      upVY.set(event.velocityY);
    })
    .onEnd((event) => {
      upVX.set(event.velocityX);
      upVY.set(event.velocityY);
    })
    .onFinalize((_event, success) => {
      if (!success) {
        upVX.set(0);
        upVY.set(0);
      }
      upN.set(upN.get() + 1);
    });

  const rotate = Gesture.Rotation()
    .onStart((event) => {
      turnX.set(event.anchorX);
      turnY.set(event.anchorY);
      turnBy.set(0);
      turnN.set(turnN.get() + 1);
    })
    .onUpdate((event) => {
      turnBy.set(event.rotation);
    })
    .onFinalize(() => {
      turnEndN.set(turnEndN.get() + 1);
    });

  const tap = Gesture.Tap()
    .maxDuration(500)
    .onEnd((event, success) => {
      if (!success) return;
      tapX.set(event.x);
      tapY.set(event.y);
      tapN.set(tapN.get() + 1);
    });

  const doubleTap = Gesture.Tap()
    .numberOfTaps(2)
    .maxDelay(260)
    .onEnd((event, success) => {
      if (!success) return;
      dblX.set(event.x);
      dblY.set(event.y);
      dblN.set(dblN.get() + 1);
    });

  const sil = SILHOUETTES[puzzleIndex];
  return (
    <GestureDetector gesture={Gesture.Race(Gesture.Simultaneous(pan, rotate), Gesture.Exclusive(doubleTap, tap))}>
      <View
        style={styles.screen}
        accessible
        accessibilityRole="adjustable"
        accessibilityLabel={`Tangram, ${sil.name}. Drag the pieces into the outline; tap to turn a piece, double-tap to flip it.`}
        onLayout={(event: LayoutChangeEvent) => {
          const { width: w, height: h } = event.nativeEvent.layout;
          setSize({ width: w, height: h });
        }}
      >
        {size && (
          <Canvas style={StyleSheet.absoluteFill}>
            <Picture picture={picture} />
          </Canvas>
        )}
        <View pointerEvents="none" style={[styles.header, { top: insets.top + 18 }]}>
          <Text style={styles.label}>{`Tangram · No. ${puzzleIndex + 1} of ${SILHOUETTES.length}`.toUpperCase()}</Text>
          <Text style={styles.title}>{sil.name}</Text>
          <Text style={styles.hint}>{isSolved ? 'Seven pieces, one shape.' : 'Drag · Tap to turn · Double-tap to flip'}</Text>
        </View>
      </View>
    </GestureDetector>
  );
}

const styles = StyleSheet.create({
  screen: {
    flex: 1,
    backgroundColor: BOARD[2],
    overflow: 'hidden',
  },
  header: {
    position: 'absolute',
    left: 24,
    right: 120,
    gap: 3,
  },
  label: {
    fontFamily: SANS,
    fontSize: 10.5,
    fontWeight: '600',
    letterSpacing: 2.4,
    color: MUTED,
  },
  title: {
    fontFamily: SERIF,
    fontSize: 30,
    fontWeight: '600',
    color: INK,
  },
  hint: {
    fontFamily: SANS,
    fontSize: 12.5,
    fontWeight: '500',
    color: MUTED,
  },
});
