import {
  BlendMode,
  BlurStyle,
  Canvas,
  ClipOp,
  FillType,
  matchFont,
  PaintStyle,
  Picture,
  Skia,
  type SkCanvas,
  type SkFont,
  type SkImage,
  type SkPaint,
  type SkPath,
  type SkPicture,
  type SkRRect,
  type SkRuntimeEffect,
  StrokeCap,
  StrokeJoin,
  TileMode,
} from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { useEffect, useMemo, useState } from 'react';
import { type LayoutChangeEvent, PixelRatio, Pressable, StyleSheet, Text, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import { useDerivedValue, useFrameCallback, useReducedMotion, useSharedValue } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import Svg, { Circle, Path } from 'react-native-svg';
import { scheduleOnRN } from 'react-native-worklets';

import {
  BOUNDS,
  GEARS,
  gearRadius,
  HOLES,
  holeAt,
  INKS,
  LOOP,
  pattern,
  penAt,
  RING_BODY,
  RING_R,
  RING_TEETH,
  SCRIPT,
  spinRate,
  toothOutline,
} from './spirographModel';

type Props = {
  /** Draws two layered patterns, tears the sheet off and starts again. Off by default. */
  autoPlay?: boolean;
};

// One fixed clock drives rolling, the pen, the script and every transition: ticks of 1/60 s, at
// most MAX_TICKS a frame (a 50 ms cap).
const TICK = 1 / 60;
const MAX_TICKS = 3;

// The pen samples the curve every PEN_STEP rad of rolling; a stroke is cut into chunks of CHUNK
// points, each recorded once into a picture, so the drawing is never rebuilt from scratch.
const PEN_STEP = 0.016;
const CHUNK = 96;
// Once the ink holds FLATTEN_AT chunks they are drawn once into an offscreen image and dropped,
// so however long the drawing runs it costs one image and a few chunks.
const FLATTEN_AT = 24;
// The offscreen image's pixels per model unit.
const INK_SCALE = PixelRatio.get();
// A fine gel-pen line, in model units.
const INK_WIDTH = 1.35;
// The faint pencil preview of the whole pattern.
const GHOST_STEPS_PER_TURN = 150;

// A release keeps the gear rolling at the finger's speed (rad/s, capped), slowed by FRICTION (1/s).
const MAX_SPIN = 14;
const FRICTION = 1.1;
const MIN_SPIN = 0.04;
// The pen moving to another hole: a critically damped spring (stiffness, damping).
const HOLE_STIFFNESS = 170;
const HOLE_DAMPING = 26;
// Changing gear: the old one fades and shrinks as the new one grows in, over SWAP_TIME s.
const SWAP_TIME = 0.45;
// Clear: the drawn sheet slides off the pad, turning, over SHEET_TIME s; under it is a fresh one.
const SHEET_TIME = 1.05;
const SHEET_TURN = -9;
// The coach arrow (shown while the sheet is empty and still): it sweeps COACH_SWEEP rad round
// the ring ahead of the gear, holds and fades, every COACH_PERIOD s; it fades in/out at COACH_RATE /s.
const COACH_PERIOD = 2;
const COACH_SWEEP = 1.2;
const COACH_RATE = 5;
// The preview fades once its pattern is fully traced, at GHOST_RATE /s.
const GHOST_RATE = 4;
// After a touch the loop waits this long before it starts again.
const USER_HOLD = 4;

// Layout (pt): the paper's side margin and the room it keeps inside round the ring; the header
// and the controls take fixed heights.
const MARGIN = 16;
const PAPER_PAD = 12;
const HEADER_H = 66;
const CONTROLS_H = 3 * 48;
const HINT_H = 34;

const SANS = process.env.EXPO_OS === 'ios' ? 'Avenir Next' : 'sans-serif';
const DESK = '#232428';
const TEXT = '#F2EFE7';
const MUTED = '#9E9B93';
const PAPER = '#F6F2E8';

// Cotton drawing paper: warm white with a soft mottle and fine fibres, in paper coordinates.
const PAPER_SKSL = `
float hash(float2 p) { return fract(sin(dot(p, float2(127.1, 311.7))) * 43758.5453); }
float noise(float2 p) {
  float2 i = floor(p);
  float2 f = fract(p);
  float2 u = f * f * (3.0 - 2.0 * f);
  return mix(mix(hash(i), hash(i + float2(1.0, 0.0)), u.x), mix(hash(i + float2(0.0, 1.0)), hash(i + float2(1.0, 1.0)), u.x), u.y);
}
half4 main(float2 p) {
  float3 col = float3(0.965, 0.95, 0.912);
  float m = noise(p * 0.02) * 0.6 + noise(p * 0.07 + 5.0) * 0.4;
  col *= 0.985 + 0.025 * m;
  float fibre = noise(float2(p.x * 0.9 + p.y * 0.3, p.y * 0.08 - p.x * 0.05));
  col -= 0.018 * smoothstep(0.72, 0.95, fibre);
  col += (hash(floor(p * 2.0)) - 0.5) * 0.012;
  return half4(half3(col), 1.0);
}`;

type Chunk = SkPicture;

type Ink = {
  /** Everything older than the chunks, flattened. */
  flat: SkImage | null;
  chunks: Chunk[];
  /** The stroke being drawn, as flat x, y pairs, not yet recorded. */
  live: number[];
  gear: number;
  ink: number;
  down: boolean;
};

function strokePaint(color: string, width: number) {
  'worklet';
  const p = Skia.Paint();
  p.setAntiAlias(true);
  p.setStyle(PaintStyle.Stroke);
  p.setStrokeWidth(width);
  p.setStrokeCap(StrokeCap.Round);
  p.setStrokeJoin(StrokeJoin.Round);
  p.setColor(Skia.Color(color));
  return p;
}

function polyline(points: number[]) {
  'worklet';
  const b = Skia.PathBuilder.Make();
  b.moveTo(points[0], points[1]);
  for (let i = 2; i < points.length; i += 2) b.lineTo(points[i], points[i + 1]);
  return b.detach();
}

function recordStroke(path: SkPath, color: string) {
  'worklet';
  const recorder = Skia.PictureRecorder();
  const canvas = recorder.beginRecording(Skia.XYWHRect(-BOUNDS, -BOUNDS, 2 * BOUNDS, 2 * BOUNDS));
  canvas.drawPath(path, strokePaint(color, INK_WIDTH));
  return recorder.finishRecordingAsPicture();
}

/** Draws `previous` (if any) and then `pictures` into a new offscreen image of the ink's bounds. */
function bake(previous: SkImage | null, pictures: SkPicture[]) {
  'worklet';
  const side = Math.ceil(2 * BOUNDS * INK_SCALE);
  const surface = Skia.Surface.Make(side, side);
  if (surface === null) return previous;
  const canvas = surface.getCanvas();
  if (previous) canvas.drawImage(previous, 0, 0);
  canvas.scale(INK_SCALE, INK_SCALE);
  canvas.translate(BOUNDS, BOUNDS);
  for (const picture of pictures) canvas.drawPicture(picture);
  surface.flush();
  return surface.makeImageSnapshot();
}

/** The flattened ink drawn back at the ink's bounds. */
function drawFlat(canvas: SkCanvas, image: SkImage | null) {
  'worklet';
  if (image === null) return;
  canvas.drawImageRect(
    image,
    Skia.XYWHRect(0, 0, image.width(), image.height()),
    Skia.XYWHRect(-BOUNDS, -BOUNDS, image.width() / INK_SCALE, image.height() / INK_SCALE),
    Skia.Paint(),
  );
}

/** Records the live stroke into a chunk and starts the next one from its last point. */
function commitLive(ink: Ink) {
  'worklet';
  const live = ink.live;
  if (live.length >= 4) {
    ink.chunks.push(recordStroke(polyline(live), INKS[ink.ink].color));
    if (ink.chunks.length >= FLATTEN_AT) {
      ink.flat = bake(ink.flat, ink.chunks);
      ink.chunks = [];
    }
  }
  const n = live.length;
  ink.live = n >= 2 ? [live[n - 2], live[n - 1]] : [];
}

function addPoint(ink: Ink, x: number, y: number) {
  'worklet';
  ink.live.push(x, y);
  if (ink.live.length >= CHUNK * 2) commitLive(ink);
}

function liftPen(ink: Ink) {
  'worklet';
  commitLive(ink);
  ink.live = [];
  ink.down = false;
}

function inkEmpty(ink: Ink) {
  'worklet';
  return ink.flat === null && ink.chunks.length === 0 && ink.live.length <= 2;
}

function easeInOut(t: number) {
  'worklet';
  return t < 0.5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2;
}

function easeOut(t: number) {
  'worklet';
  return 1 - Math.pow(1 - Math.min(1, Math.max(0, t)), 3);
}

/** Whether a clock that went from `before` to `t` passed the moment `at`. */
function crossed(before: number, t: number, at: number) {
  'worklet';
  return before < at && t >= at;
}

/** A hex colour with an alpha, as a Skia colour. */
function tinted(hex: string, alpha: number) {
  'worklet';
  const c = Skia.Color(hex);
  c[3] = Math.max(0, Math.min(1, alpha));
  return c;
}

/** A hex colour mixed toward black by `amount`. */
function darker(hex: string, amount: number, alpha: number) {
  'worklet';
  const c = Skia.Color(hex);
  c[0] *= 1 - amount;
  c[1] *= 1 - amount;
  c[2] *= 1 - amount;
  c[3] = Math.max(0, Math.min(1, alpha));
  return c;
}

/** A toothed outline from flat x, y pairs. */
function outlinePath(points: number[]) {
  const b = Skia.PathBuilder.Make();
  b.moveTo(points[0], points[1]);
  for (let i = 2; i < points.length; i += 2) b.lineTo(points[i], points[i + 1]);
  b.close();
  return b;
}

/** The ring's body (between its outer rim and its inward teeth), its printed ticks, and each gear. */
function buildParts() {
  const ring = outlinePath(toothOutline(RING_TEETH, RING_R, -1));
  ring.addCircle(0, 0, RING_R + RING_BODY);
  ring.setFillType(FillType.EvenOdd);
  const ticks = Skia.PathBuilder.Make();
  for (let k = 0; k < RING_TEETH; k += 1) {
    const a = (k / RING_TEETH) * 2 * Math.PI;
    const long = k % 8 === 0;
    const r0 = RING_R + RING_BODY - (long ? 11 : 7);
    const r1 = RING_R + RING_BODY - 4;
    ticks.moveTo(r0 * Math.cos(a), r0 * Math.sin(a));
    ticks.lineTo(r1 * Math.cos(a), r1 * Math.sin(a));
  }
  const gears = GEARS.map((gear) => {
    const outline = outlinePath(toothOutline(gear.teeth, gearRadius(gear.teeth), 1));
    // The pen holes and the hub are cut through the plastic.
    const radius = gearRadius(gear.teeth);
    for (let k = 0; k < HOLES.length; k += 1) {
      const h = holeAt(k, radius);
      outline.addCircle(h.d * Math.cos(h.psi), h.d * Math.sin(h.psi), 3.3);
    }
    outline.addCircle(0, 0, 5);
    outline.setFillType(FillType.EvenOdd);
    return outline.detach();
  });
  return { ring: ring.detach(), ticks: ticks.detach(), gears };
}

/** One gear at its place on the ring, turned by `spin`, faded by `alpha`, scaled by `scale`. */
function drawGear(
  canvas: SkCanvas,
  outline: SkPath,
  index: number,
  ink: number,
  phi: number,
  spin: number,
  hole: number,
  alpha: number,
  scale: number,
) {
  'worklet';
  if (alpha <= 0.01) return;
  const gear = GEARS[index];
  const radius = gearRadius(gear.teeth);
  const reach = RING_R - radius;
  canvas.save();
  canvas.translate(reach * Math.cos(phi), reach * Math.sin(phi));
  canvas.scale(scale, scale);
  // A soft shadow on the paper, cast down and to the right whatever the gear's turn.
  const shadow = Skia.Paint();
  shadow.setAntiAlias(true);
  shadow.setColor(Skia.Color('#1a1408'));
  shadow.setAlphaf(0.2 * alpha);
  shadow.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 2.6, true));
  canvas.save();
  canvas.translate(1.2, 3);
  canvas.rotate((spin * 180) / Math.PI, 0, 0);
  canvas.drawPath(outline, shadow);
  canvas.restore();
  canvas.rotate((spin * 180) / Math.PI, 0, 0);
  // Tinted plastic, a little brighter toward the light on the upper left.
  const body = Skia.Paint();
  body.setAntiAlias(true);
  body.setColor(tinted(gear.tint, 0.5 * alpha));
  canvas.drawPath(outline, body);
  const lx = -Math.cos(spin + Math.PI / 4) * radius;
  const ly = Math.sin(spin + Math.PI / 4) * radius;
  const sheen = Skia.Paint();
  sheen.setAntiAlias(true);
  sheen.setShader(
    Skia.Shader.MakeLinearGradient(
      Skia.Point(lx, -ly),
      Skia.Point(-lx, ly),
      [Skia.Color(`rgba(255, 255, 255, ${0.3 * alpha})`), Skia.Color('rgba(255, 255, 255, 0)')],
      null,
      TileMode.Clamp,
    ),
  );
  canvas.drawPath(outline, sheen);
  const edge = Skia.Paint();
  edge.setAntiAlias(true);
  edge.setStyle(PaintStyle.Stroke);
  edge.setStrokeWidth(0.9);
  edge.setColor(darker(gear.tint, 0.35, 0.85 * alpha));
  canvas.drawPath(outline, edge);
  // The pen standing in its hole: a round barrel end in its ink, lit from the upper left.
  const pen = holeAt(hole, radius);
  const px = pen.d * Math.cos(pen.psi);
  const py = pen.d * Math.sin(pen.psi);
  canvas.rotate((-spin * 180) / Math.PI, px, py);
  const penShadow = Skia.Paint();
  penShadow.setAntiAlias(true);
  penShadow.setColor(Skia.Color('#000000'));
  penShadow.setAlphaf(0.28 * alpha);
  penShadow.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 2.2, true));
  canvas.drawCircle(px + 1.4, py + 2.4, 5.6, penShadow);
  const barrel = Skia.Paint();
  barrel.setAntiAlias(true);
  barrel.setShader(
    Skia.Shader.MakeRadialGradient(
      Skia.Point(px - 1.8, py - 1.8),
      7,
      [tinted('#ffffff', 0.55 * alpha), tinted(INKS[ink].color, alpha), darker(INKS[ink].color, 0.45, alpha)],
      [0, 0.45, 1],
      TileMode.Clamp,
    ),
  );
  canvas.drawCircle(px, py, 5.6, barrel);
  const rim = Skia.Paint();
  rim.setAntiAlias(true);
  rim.setStyle(PaintStyle.Stroke);
  rim.setStrokeWidth(0.8);
  rim.setColor(darker(INKS[ink].color, 0.6, 0.9 * alpha));
  canvas.drawCircle(px, py, 5.6, rim);
  canvas.restore();
}

/** The clear plastic ring with its printed ticks and size. */
function drawRing(canvas: SkCanvas, ring: SkPath, ticks: SkPath, font: SkFont) {
  'worklet';
  const shadow = Skia.Paint();
  shadow.setAntiAlias(true);
  shadow.setColor(Skia.Color('#1a1408'));
  shadow.setAlphaf(0.2);
  shadow.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 3.5, true));
  canvas.save();
  canvas.translate(1.5, 4);
  canvas.drawPath(ring, shadow);
  canvas.restore();
  const body = Skia.Paint();
  body.setAntiAlias(true);
  body.setShader(
    Skia.Shader.MakeLinearGradient(
      Skia.Point(-BOUNDS, -BOUNDS),
      Skia.Point(BOUNDS, BOUNDS),
      [Skia.Color('rgba(226, 236, 244, 0.62)'), Skia.Color('rgba(196, 212, 226, 0.4)'), Skia.Color('rgba(214, 226, 238, 0.55)')],
      [0, 0.55, 1],
      TileMode.Clamp,
    ),
  );
  canvas.drawPath(ring, body);
  const line = Skia.Paint();
  line.setAntiAlias(true);
  line.setStyle(PaintStyle.Stroke);
  line.setStrokeWidth(0.9);
  line.setColor(Skia.Color('rgba(58, 76, 96, 0.5)'));
  canvas.drawPath(ring, line);
  // A bright bevel just inside the outer rim.
  const bevel = strokePaint('#ffffff', 1.2);
  bevel.setAlphaf(0.75);
  canvas.drawCircle(0, 0, RING_R + RING_BODY - 1.4, bevel);
  const tick = strokePaint('#2e3c4c', 0.7);
  tick.setAlphaf(0.5);
  canvas.drawPath(ticks, tick);
  const label = Skia.Paint();
  label.setAntiAlias(true);
  label.setColor(Skia.Color('rgba(46, 60, 76, 0.62)'));
  const text = `${RING_TEETH}`;
  const w = font.measureText(text).width;
  canvas.drawText(text, -w / 2, -(RING_R + RING_BODY * 0.5) + 3.5, label, font);
}

/** The top sheet of the pad with its ink, in screen space. */
function drawSheet(
  canvas: SkCanvas,
  rect: SkRRect,
  paper: SkPaint,
  state: Ink | null,
  live: SkPath | null,
  cx: number,
  cy: number,
  scale: number,
) {
  'worklet';
  canvas.drawRRect(rect, paper);
  if (!state) return;
  canvas.save();
  canvas.clipRRect(rect, ClipOp.Intersect, true);
  canvas.translate(cx, cy);
  canvas.scale(scale, scale);
  // Ink soaks into the paper: the layer multiplies, so crossing colours darken like real ink.
  const layer = Skia.Paint();
  layer.setBlendMode(BlendMode.Multiply);
  canvas.saveLayer(layer);
  drawFlat(canvas, state.flat);
  for (const chunk of state.chunks) canvas.drawPicture(chunk);
  if (live) canvas.drawPath(live, strokePaint(INKS[state.ink].color, INK_WIDTH));
  canvas.restore();
  canvas.restore();
}

/**
 * A spirograph on a pad of drawing paper: a clear plastic ring with inward teeth and a tinted gear
 * inside it. Dragging round the ring rolls the gear and its pen draws a hypotrochoid in real ink
 * that builds up into a pattern, while the rest of the pattern shows faintly in pencil; a release
 * keeps it rolling until friction stops it. Below, the gear, the pen hole and the ink are picked
 * from three rows (a tap on the paper also moves the pen to the next hole); Clear tears the sheet off.
 */
export function SpirographDraw({ autoPlay = false }: Props) {
  const insets = useSafeAreaInsets();
  const reducedMotion = useReducedMotion();
  const auto = autoPlay && !reducedMotion;
  const [size, setSize] = useState<{ width: number; height: number } | null>(null);
  const parts = useMemo(() => buildParts(), []);
  const paperEffect = useMemo<SkRuntimeEffect | null>(() => Skia.RuntimeEffect.Make(PAPER_SKSL), []);
  const font = useMemo(() => matchFont({ fontFamily: SANS, fontSize: 9, fontWeight: '600' }), []);
  const [shown, setShown] = useState({ gear: SCRIPT.startGear, hole: SCRIPT.startHole, ink: SCRIPT.startInk });

  const width = size?.width ?? 0;
  const height = size?.height ?? 0;
  // The paper: a square under the header, as large as the width and the room above the controls allow.
  const roomTop = insets.top + HEADER_H;
  const roomBottom = height - insets.bottom - CONTROLS_H - HINT_H - 16;
  const side = Math.max(0, Math.min(width - 2 * MARGIN, roomBottom - roomTop - 8));
  const paperX = (width - side) / 2;
  const paperY = roomTop + Math.max(0, (roomBottom - roomTop - side) / 2);
  const cx = paperX + side / 2;
  const cy = paperY + side / 2;
  const scale = side > 0 ? (side / 2 - PAPER_PAD) / (RING_R + RING_BODY) : 1;

  const ink = useSharedValue<Ink | null>(null);
  const frameNo = useSharedValue(0);
  const acc = useSharedValue(0);
  // Rolling: the gear's angle round the ring, its own turn, and its coasting speed (rad/s).
  const phi = useSharedValue(0);
  const spin = useSharedValue(0);
  const lastPhi = useSharedValue(0);
  const omega = useSharedValue(0);
  const dragging = useSharedValue(false);
  const fingerLast = useSharedValue(0);
  // Gear and swap progress (1 = settled); the pen's hole, its target and its speed; the ink.
  const gear = useSharedValue(SCRIPT.startGear);
  const gearFrom = useSharedValue(SCRIPT.startGear);
  const swap = useSharedValue(1);
  const hole = useSharedValue(SCRIPT.startHole);
  const holeTarget = useSharedValue(SCRIPT.startHole);
  const holeSpeed = useSharedValue(0);
  const inkIndex = useSharedValue(SCRIPT.startInk);
  // How much of the current pattern the pen has covered (the span of φ it has drawn over).
  const spanLo = useSharedValue(0);
  const spanHi = useSharedValue(0);
  const spanSet = useSharedValue(false);
  const complete = useSharedValue(false);
  const ghost = useSharedValue(1);
  // The sheet tearing off (0…1), or -1.
  const tear = useSharedValue(-1);
  // The coach arrow's strength and its own clock.
  const coach = useSharedValue(1);
  const coachClock = useSharedValue(0);
  // Requests from the controls, handled on the next tick.
  const pendingGear = useSharedValue(-1);
  const pendingHole = useSharedValue(-1);
  const pendingInk = useSharedValue(-1);
  const pendingClear = useSharedValue(false);
  // The script: its clock, a pause after a touch, and a scripted roll in progress.
  const clock = useSharedValue(0);
  const hold = useSharedValue(0);
  const looping = useSharedValue(auto);
  const rollFrom = useSharedValue(0);
  const rollBy = useSharedValue(0);
  const rollAt = useSharedValue(0);
  const rollTime = useSharedValue(0);

  useEffect(() => {
    looping.set(auto);
    clock.set(0);
    hold.set(0);
  }, [auto, clock, hold, looping]);

  const show = (g: number, h: number, i: number) => setShown({ gear: g, hole: h, ink: i });
  const buzz = (kind: 'select' | 'gear' | 'clear' | 'done') => {
    if (process.env.EXPO_OS === 'web') return;
    if (kind === 'select') Haptics.selectionAsync().catch(() => {});
    else if (kind === 'gear') Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
    else if (kind === 'clear') Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Medium).catch(() => {});
    else Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success).catch(() => {});
  };

  /** A new pattern starts: forget how much of the old one was covered. */
  const resetSpan = () => {
    'worklet';
    spanSet.set(false);
    complete.set(false);
  };

  const changeGear = (index: number) => {
    'worklet';
    if (index === gear.get() && swap.get() >= 1) return;
    gearFrom.set(gear.get());
    gear.set(index);
    swap.set(reducedMotion ? 1 : 0);
    resetSpan();
    scheduleOnRN(show, index, holeTarget.get(), inkIndex.get());
  };

  const changeHole = (index: number) => {
    'worklet';
    if (index === holeTarget.get()) return;
    holeTarget.set(index);
    if (reducedMotion) hole.set(index);
    resetSpan();
    scheduleOnRN(show, gear.get(), index, inkIndex.get());
  };

  const changeInk = (index: number) => {
    'worklet';
    if (index === inkIndex.get()) return;
    inkIndex.set(index);
    scheduleOnRN(show, gear.get(), holeTarget.get(), index);
  };

  const startTear = () => {
    'worklet';
    if (tear.get() >= 0) return;
    tear.set(0);
  };

  /** The autoPlay script, from `before` to `t` seconds. */
  const script = (before: number, t: number) => {
    'worklet';
    for (const cue of SCRIPT.steps) {
      if (!crossed(before, t, cue.at)) continue;
      if (cue.ink !== undefined) changeInk(cue.ink);
      if (cue.gear !== undefined) changeGear(cue.gear);
      if (cue.hole !== undefined) changeHole(cue.hole);
      if (cue.clear) startTear();
      if (cue.roll !== undefined) {
        rollFrom.set(phi.get());
        rollBy.set(cue.roll * 2 * Math.PI);
        rollAt.set(cue.at);
        rollTime.set(cue.time ?? 1);
        omega.set(0);
      }
    }
  };

  /** One tick: requests, the script, rolling, the pen's hole and the gear swap, then the pen. */
  const step = (state: Ink) => {
    'worklet';
    if (pendingGear.get() >= 0) {
      changeGear(pendingGear.get());
      pendingGear.set(-1);
    }
    if (pendingHole.get() >= 0) {
      changeHole(pendingHole.get());
      pendingHole.set(-1);
    }
    if (pendingInk.get() >= 0) {
      changeInk(pendingInk.get());
      pendingInk.set(-1);
    }
    if (pendingClear.get()) {
      startTear();
      pendingClear.set(false);
    }

    if (!dragging.get()) {
      if (hold.get() > 0) {
        hold.set(hold.get() - TICK);
        if (hold.get() <= 0) clock.set(0);
      } else if (looping.get()) {
        const before = clock.get();
        let t = before + TICK;
        if (t >= LOOP) {
          t -= LOOP;
          // A whole turn of either angle looks the same: keep the numbers small.
          const turnsPhi = Math.round(phi.get() / (2 * Math.PI)) * 2 * Math.PI;
          phi.set(phi.get() - turnsPhi);
          lastPhi.set(lastPhi.get() - turnsPhi);
          spin.set(spin.get() - Math.round(spin.get() / (2 * Math.PI)) * 2 * Math.PI);
          resetSpan();
        }
        clock.set(t);
        script(t < before ? 0 : before, t);
        if (rollTime.get() > 0) {
          const k = Math.min(1, (t - rollAt.get()) / rollTime.get());
          phi.set(rollFrom.get() + rollBy.get() * easeInOut(Math.max(0, k)));
          if (k >= 1) rollTime.set(0);
        }
      }
      if (rollTime.get() <= 0 && Math.abs(omega.get()) > 0) {
        phi.set(phi.get() + omega.get() * TICK);
        omega.set(omega.get() * Math.exp(-FRICTION * TICK));
        if (Math.abs(omega.get()) < MIN_SPIN) omega.set(0);
      }
    }

    const h = hole.get();
    const hv = holeSpeed.get() + (HOLE_STIFFNESS * (holeTarget.get() - h) - HOLE_DAMPING * holeSpeed.get()) * TICK;
    holeSpeed.set(hv);
    hole.set(h + hv * TICK);
    if (Math.abs(holeTarget.get() - hole.get()) < 0.002 && Math.abs(hv) < 0.01) {
      hole.set(holeTarget.get());
      holeSpeed.set(0);
    }
    if (swap.get() < 1) swap.set(Math.min(1, swap.get() + TICK / SWAP_TIME));
    if (tear.get() >= 0) {
      const w = tear.get() + TICK / (reducedMotion ? 0.35 : SHEET_TIME);
      if (w >= 1) {
        state.chunks = [];
        state.flat = null;
        state.live = [];
        state.down = false;
        tear.set(-1);
        resetSpan();
      } else {
        tear.set(w);
      }
    }

    // Roll the gear by however far `phi` moved, and let the pen trace the way it went.
    const g = gear.get();
    const sw = easeInOut(swap.get());
    const radius = gearRadius(GEARS[gearFrom.get()].teeth) * (1 - sw) + gearRadius(GEARS[g].teeth) * sw;
    const from = lastPhi.get();
    const to = phi.get();
    const delta = to - from;
    const rate = spinRate(radius);
    const spinFrom = spin.get();
    spin.set(spinFrom + delta * rate);
    lastPhi.set(to);

    // The coach shows while the sheet is empty and nothing moves; the preview fades once traced.
    const still = !dragging.get() && omega.get() === 0 && rollTime.get() <= 0 && tear.get() < 0;
    const coachGoal = still && inkEmpty(state) ? 1 : 0;
    coach.set(coach.get() + (coachGoal - coach.get()) * (1 - Math.exp(-TICK * COACH_RATE)));
    coachClock.set((coachClock.get() + TICK) % COACH_PERIOD);
    const ghostGoal = complete.get() ? 0 : 1;
    ghost.set(ghost.get() + (ghostGoal - ghost.get()) * (1 - Math.exp(-TICK * GHOST_RATE)));

    const settled = swap.get() >= 1 && hole.get() === holeTarget.get() && tear.get() < 0;
    if (!settled) {
      if (state.down) liftPen(state);
      return;
    }
    const pen = holeAt(hole.get(), radius);
    const point = { x: 0, y: 0 };
    if (!state.down || state.gear !== g || state.ink !== inkIndex.get()) {
      if (state.down) liftPen(state);
      state.gear = g;
      state.ink = inkIndex.get();
      state.down = true;
      penAt(to, spin.get(), radius, pen.d, pen.psi, point);
      state.live = [point.x, point.y];
      return;
    }
    if (delta === 0) return;
    const n = Math.min(400, Math.ceil(Math.abs(delta) / PEN_STEP));
    for (let k = 1; k <= n; k += 1) {
      const f = k / n;
      penAt(from + delta * f, spinFrom + delta * rate * f, radius, pen.d, pen.psi, point);
      addPoint(state, point.x, point.y);
    }
    // How much of the closed pattern the pen has now been over.
    if (!spanSet.get()) {
      spanLo.set(from);
      spanHi.set(from);
      spanSet.set(true);
    }
    spanLo.set(Math.min(spanLo.get(), to));
    spanHi.set(Math.max(spanHi.get(), to));
    const full = pattern(GEARS[g].teeth).turns * 2 * Math.PI;
    if (!complete.get() && spanHi.get() - spanLo.get() >= full - 0.02) {
      complete.set(true);
      if (!looping.get() || hold.get() > 0) scheduleOnRN(buzz, 'done');
    }
  };

  useFrameCallback((frame) => {
    let state = ink.get();
    if (state === null) {
      state = { flat: null, chunks: [], live: [], gear: gear.get(), ink: inkIndex.get(), down: false };
      ink.set(state);
    }
    const dt = Math.min((frame.timeSincePreviousFrame ?? 16) / 1000, MAX_TICKS * TICK);
    let pending = acc.get() + dt;
    let ticks = 0;
    while (pending >= TICK && ticks < MAX_TICKS) {
      step(state);
      pending -= TICK;
      ticks += 1;
    }
    acc.set(Math.min(pending, TICK));
    if (ticks > 0) frameNo.set(frameNo.get() + 1);
  });

  const picture = useDerivedValue(() => {
    const recorder = Skia.PictureRecorder();
    const canvas = recorder.beginRecording(Skia.XYWHRect(0, 0, width, height));
    // `frameNo` changes every frame the clock ticks: the ink lives in a mutable object it cannot see.
    if (width === 0 || side <= 0 || frameNo.get() < 0) return recorder.finishRecordingAsPicture();

    // The desk: dark, a little lighter under the pad.
    const desk = Skia.Paint();
    desk.setShader(
      Skia.Shader.MakeRadialGradient(
        Skia.Point(cx, cy),
        Math.max(width, height) * 0.75,
        [Skia.Color('#303136'), Skia.Color('#232428'), Skia.Color('#18191c')],
        [0, 0.55, 1],
        TileMode.Clamp,
      ),
    );
    canvas.drawRect(Skia.XYWHRect(0, 0, width, height), desk);

    // The pad: two sheets' edges under the top one, and its shadow on the desk.
    const sheetRect = Skia.RRectXY(Skia.XYWHRect(paperX, paperY, side, side), 5, 5);
    const padShadow = Skia.Paint();
    padShadow.setColor(Skia.Color('rgba(0, 0, 0, 0.5)'));
    padShadow.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 16, true));
    canvas.save();
    canvas.translate(0, 12);
    canvas.drawRRect(sheetRect, padShadow);
    canvas.restore();
    const under = Skia.Paint();
    under.setAntiAlias(true);
    under.setColor(Skia.Color('#C9C3B6'));
    canvas.drawRRect(Skia.RRectXY(Skia.XYWHRect(paperX + 1, paperY + 5, side - 2, side), 5, 5), under);
    under.setColor(Skia.Color('#E1DBCE'));
    canvas.drawRRect(Skia.RRectXY(Skia.XYWHRect(paperX + 0.5, paperY + 2.5, side - 1, side), 5, 5), under);

    const paperPaint = Skia.Paint();
    paperPaint.setAntiAlias(true);
    if (paperEffect) paperPaint.setShader(paperEffect.makeShader([]));
    else paperPaint.setColor(Skia.Color(PAPER));

    const state = ink.get();
    const live = state && state.live.length >= 4 ? polyline(state.live) : null;
    const w = tear.get();
    if (w < 0) {
      drawSheet(canvas, sheetRect, paperPaint, state, live, cx, cy, scale);
    } else {
      // A fresh sheet waits under the one being torn off.
      canvas.drawRRect(sheetRect, paperPaint);
      canvas.save();
      if (reducedMotion) {
        const fade = Skia.Paint();
        fade.setAlphaf(1 - w);
        canvas.saveLayer(fade);
        drawSheet(canvas, sheetRect, paperPaint, state, live, cx, cy, scale);
        canvas.restore();
      } else {
        const e = easeInOut(w);
        canvas.translate(-e * (side + 90), e * 46);
        canvas.rotate(SHEET_TURN * e, cx, paperY);
        const lift = Skia.Paint();
        lift.setColor(Skia.Color(`rgba(0, 0, 0, ${0.28 * Math.sin(Math.PI * Math.min(1, w * 1.6))})`));
        lift.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 12, true));
        canvas.save();
        canvas.translate(4, 10);
        canvas.drawRRect(sheetRect, lift);
        canvas.restore();
        drawSheet(canvas, sheetRect, paperPaint, state, live, cx, cy, scale);
      }
      canvas.restore();
    }

    canvas.save();
    canvas.translate(cx, cy);
    canvas.scale(scale, scale);

    // The whole pattern the pen is on, faint in pencil: it morphs as the hole or the gear changes.
    const g = gear.get();
    const sw = easeInOut(swap.get());
    const radius = gearRadius(GEARS[gearFrom.get()].teeth) * (1 - sw) + gearRadius(GEARS[g].teeth) * sw;
    const pen = holeAt(hole.get(), radius);
    const p0 = phi.get();
    const s0 = spin.get();
    const ghostAlpha = ghost.get();
    if (ghostAlpha > 0.01) {
      const turns = pattern(GEARS[g].teeth).turns;
      const steps = turns * GHOST_STEPS_PER_TURN;
      const rate = spinRate(radius);
      const point = { x: 0, y: 0 };
      const path = Skia.PathBuilder.Make();
      for (let k = 0; k <= steps; k += 1) {
        const d = (k / steps) * turns * 2 * Math.PI;
        penAt(p0 + d, s0 + d * rate, radius, pen.d, pen.psi, point);
        if (k === 0) path.moveTo(point.x, point.y);
        else path.lineTo(point.x, point.y);
      }
      const pencil = strokePaint('#4a4740', 0.7);
      pencil.setAlphaf(0.2 * ghostAlpha);
      canvas.drawPath(path.detach(), pencil);
    }

    drawRing(canvas, parts.ring, parts.ticks, font);

    // The coach: an arrow sweeping round the ring ahead of the gear, while nothing is drawn yet.
    const strength = coach.get();
    if (strength > 0.01) {
      const reach = RING_R - radius;
      const rho = RING_R - 15;
      const cosStart = (rho * rho + reach * reach - radius * radius) / Math.max(1, 2 * rho * reach);
      const start = p0 + Math.acos(Math.max(-1, Math.min(1, cosStart))) + 0.14;
      const local = looping.get() && hold.get() <= 0 ? clock.get() % COACH_PERIOD : coachClock.get();
      const grow = easeOut(local / 1.1);
      const fade = local > 1.6 ? 1 - (local - 1.6) / 0.4 : 1;
      const sweep = Math.max(0.02, COACH_SWEEP * grow);
      const end = start + sweep;
      const arc = Skia.PathBuilder.Make();
      const segments = 32;
      for (let i = 0; i <= segments; i += 1) {
        const a = start + (sweep * i) / segments;
        if (i === 0) arc.moveTo(rho * Math.cos(a), rho * Math.sin(a));
        else arc.lineTo(rho * Math.cos(a), rho * Math.sin(a));
      }
      const tx = -Math.sin(end);
      const ty = Math.cos(end);
      const hx = rho * Math.cos(end);
      const hy = rho * Math.sin(end);
      for (const side2 of [-1, 1]) {
        const ax = -tx * 0.86 + side2 * -ty * 0.5;
        const ay = -ty * 0.86 + side2 * tx * 0.5;
        arc.moveTo(hx, hy);
        arc.lineTo(hx + ax * 8, hy + ay * 8);
      }
      const arrow = strokePaint('#2b2a30', 2.2);
      arrow.setAlphaf(0.5 * strength * Math.max(0, fade));
      canvas.drawPath(arc.detach(), arrow);
    }

    // The gear over the ink: while it changes, the old one fades out as the new one grows in.
    const from = gearFrom.get();
    const ci = inkIndex.get();
    if (sw < 1 && from !== g) drawGear(canvas, parts.gears[from], from, ci, p0, s0, hole.get(), 1 - sw, 1 - 0.08 * sw);
    drawGear(canvas, parts.gears[g], g, ci, p0, s0, hole.get(), sw < 1 && from !== g ? sw : 1, sw < 1 && from !== g ? 0.92 + 0.08 * sw : 1);
    canvas.restore();
    return recorder.finishRecordingAsPicture();
  });

  const touched = () => {
    'worklet';
    hold.set(USER_HOLD);
    rollTime.set(0);
  };

  const onPaper = (x: number, y: number) => {
    'worklet';
    return x >= paperX && x <= paperX + side && y >= paperY && y <= paperY + side;
  };

  const pan = Gesture.Pan()
    .minDistance(2)
    .onTouchesDown(() => {
      // A finger on the paper stops a coasting gear.
      omega.set(0);
      touched();
    })
    .onStart((event) => {
      if (!onPaper(event.x, event.y)) return;
      dragging.set(true);
      fingerLast.set(Math.atan2(event.y - cy, event.x - cx));
    })
    .onUpdate((event) => {
      if (!dragging.get()) return;
      const dx = event.x - cx;
      const dy = event.y - cy;
      const a = Math.atan2(dy, dx);
      let d = a - fingerLast.get();
      if (d > Math.PI) d -= 2 * Math.PI;
      if (d < -Math.PI) d += 2 * Math.PI;
      fingerLast.set(a);
      // Right over the centre the angle is meaningless: skip it rather than spin the gear.
      if (Math.hypot(dx, dy) < 18) return;
      phi.set(phi.get() + d);
    })
    .onEnd((event) => {
      if (!dragging.get()) return;
      const rx = event.x - cx;
      const ry = event.y - cy;
      const w = (rx * event.velocityY - ry * event.velocityX) / Math.max(rx * rx + ry * ry, 2500);
      omega.set(reducedMotion || !Number.isFinite(w) ? 0 : Math.max(-MAX_SPIN, Math.min(MAX_SPIN, w)));
    })
    .onFinalize(() => {
      dragging.set(false);
      hold.set(USER_HOLD);
    });

  const tap = Gesture.Tap()
    .maxDuration(300)
    .onEnd((event, success) => {
      if (!success || !onPaper(event.x, event.y)) return;
      touched();
      changeHole((holeTarget.get() + 1) % HOLES.length);
      scheduleOnRN(buzz, 'select');
    });

  const userPick = () => {
    hold.set(USER_HOLD);
    rollTime.set(0);
  };
  const pickGear = (index: number) => {
    userPick();
    if (index !== shown.gear) buzz('gear');
    pendingGear.set(index);
  };
  const pickHole = (index: number) => {
    userPick();
    if (index !== shown.hole) buzz('select');
    pendingHole.set(index);
  };
  const pickInk = (index: number) => {
    userPick();
    if (index !== shown.ink) buzz('select');
    pendingInk.set(index);
  };
  const clear = () => {
    userPick();
    buzz('clear');
    pendingClear.set(true);
  };

  const current = GEARS[shown.gear];
  const loops = pattern(current.teeth).petals;
  const inkColor = INKS[shown.ink].color;

  return (
    <View
      style={styles.screen}
      onLayout={(event: LayoutChangeEvent) => {
        const { width: w, height: h } = event.nativeEvent.layout;
        setSize({ width: w, height: h });
      }}
    >
      <GestureDetector gesture={Gesture.Race(pan, tap)}>
        <View
          style={StyleSheet.absoluteFill}
          accessible
          accessibilityRole="adjustable"
          accessibilityLabel="Spirograph. Drag round the ring to roll the gear and draw; tap the paper to move the pen to the next hole."
        >
          {size && (
            <Canvas style={StyleSheet.absoluteFill}>
              <Picture picture={picture} />
            </Canvas>
          )}
        </View>
      </GestureDetector>

      <View pointerEvents="box-none" style={[styles.header, { top: insets.top + 8 }]}>
        <View pointerEvents="none" style={styles.titles}>
          <Text style={styles.title}>Spirograph</Text>
          <Text style={styles.subtitle} numberOfLines={1}>
            {`${current.teeth}-tooth gear · hole ${shown.hole + 1} · ${loops} loops`}
          </Text>
        </View>
        <Pressable
          accessibilityRole="button"
          accessibilityLabel="Clear the sheet"
          onPress={clear}
          hitSlop={8}
          style={({ pressed }) => [styles.clear, pressed && styles.pressed]}
        >
          <Text style={styles.clearText}>Clear</Text>
        </Pressable>
      </View>

      {size && (
        <View pointerEvents="box-none" style={[styles.bottom, { bottom: insets.bottom + 10 }]}>
          <Text pointerEvents="none" style={styles.hint} numberOfLines={1} adjustsFontSizeToFit>
            Drag around the ring to draw · Tap the paper for the next hole
          </Text>
          <View style={styles.row}>
            <Text style={styles.rowLabel}>GEAR</Text>
            <View style={styles.options}>
              {GEARS.map((g, i) => {
                const selected = i === shown.gear;
                return (
                  <Pressable
                    key={g.teeth}
                    accessibilityRole="button"
                    accessibilityState={{ selected }}
                    accessibilityLabel={`${g.teeth} tooth gear`}
                    onPress={() => pickGear(i)}
                    style={({ pressed }) => [styles.option, selected && styles.optionOn, pressed && styles.pressed]}
                  >
                    <GearGlyph index={i} tint={g.tint} />
                    <Text style={[styles.optionText, selected && styles.optionTextOn]}>{g.teeth}</Text>
                  </Pressable>
                );
              })}
            </View>
          </View>
          <View style={styles.row}>
            <Text style={styles.rowLabel}>HOLE</Text>
            <View style={styles.options}>
              {HOLES.map((_, i) => {
                const selected = i === shown.hole;
                return (
                  <Pressable
                    key={i}
                    accessibilityRole="button"
                    accessibilityState={{ selected }}
                    accessibilityLabel={`Pen hole ${i + 1}`}
                    onPress={() => pickHole(i)}
                    style={({ pressed }) => [
                      styles.option,
                      selected && { backgroundColor: inkColor, borderColor: 'rgba(255, 255, 255, 0.3)' },
                      pressed && styles.pressed,
                    ]}
                  >
                    <Text style={[styles.optionText, selected && styles.optionTextOn]}>{i + 1}</Text>
                  </Pressable>
                );
              })}
            </View>
          </View>
          <View style={styles.row}>
            <Text style={styles.rowLabel}>INK</Text>
            <View style={styles.options}>
              {INKS.map((k, i) => {
                const selected = i === shown.ink;
                return (
                  <Pressable
                    key={k.name}
                    accessibilityRole="button"
                    accessibilityState={{ selected }}
                    accessibilityLabel={`${k.name} ink`}
                    onPress={() => pickInk(i)}
                    style={({ pressed }) => [styles.swatchHit, pressed && styles.pressed]}
                  >
                    <View style={[styles.swatchRing, selected && styles.swatchRingOn]}>
                      <View style={[styles.swatch, { backgroundColor: k.color }]} />
                    </View>
                  </Pressable>
                );
              })}
            </View>
          </View>
        </View>
      )}
    </View>
  );
}

/** A small gear, sized by its place in the set, for the gear buttons. */
function GearGlyph({ index, tint }: { index: number; tint: string }) {
  const d = useMemo(() => {
    const pts = toothOutline(8 + index * 2, 5.4 + index * 1.25, 1, 2.2);
    let s = `M${(pts[0] + 12).toFixed(2)} ${(pts[1] + 12).toFixed(2)}`;
    for (let i = 2; i < pts.length; i += 2) s += `L${(pts[i] + 12).toFixed(2)} ${(pts[i + 1] + 12).toFixed(2)}`;
    return `${s}Z`;
  }, [index]);
  return (
    <Svg width={24} height={24} viewBox="0 0 24 24">
      <Path d={d} fill={tint} fillOpacity={0.75} stroke={tint} strokeWidth={0.8} />
      <Circle cx={12} cy={12} r={1.6} fill={DESK} />
    </Svg>
  );
}

const styles = StyleSheet.create({
  screen: {
    flex: 1,
    backgroundColor: DESK,
    overflow: 'hidden',
  },
  header: {
    position: 'absolute',
    left: 20,
    right: 20,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    gap: 12,
  },
  titles: {
    flexShrink: 1,
    gap: 2,
  },
  title: {
    fontFamily: SANS,
    fontSize: 28,
    fontWeight: '700',
    color: TEXT,
  },
  subtitle: {
    fontFamily: SANS,
    fontSize: 14,
    fontWeight: '500',
    color: MUTED,
    fontVariant: ['tabular-nums'],
  },
  clear: {
    paddingHorizontal: 18,
    height: 38,
    borderRadius: 19,
    justifyContent: 'center',
    backgroundColor: 'rgba(255, 255, 255, 0.1)',
    borderWidth: StyleSheet.hairlineWidth,
    borderColor: 'rgba(255, 255, 255, 0.18)',
  },
  clearText: {
    fontFamily: SANS,
    fontSize: 15,
    fontWeight: '600',
    color: TEXT,
  },
  pressed: {
    opacity: 0.75,
    transform: [{ scale: 0.97 }],
  },
  bottom: {
    position: 'absolute',
    left: MARGIN,
    right: MARGIN,
  },
  hint: {
    height: HINT_H,
    textAlign: 'center',
    fontFamily: SANS,
    fontSize: 13,
    fontWeight: '500',
    color: MUTED,
  },
  row: {
    height: 48,
    flexDirection: 'row',
    alignItems: 'center',
    borderTopWidth: StyleSheet.hairlineWidth,
    borderTopColor: 'rgba(255, 255, 255, 0.1)',
  },
  rowLabel: {
    width: 56,
    fontFamily: SANS,
    fontSize: 12,
    fontWeight: '600',
    letterSpacing: 1.4,
    color: MUTED,
  },
  options: {
    flex: 1,
    flexDirection: 'row',
    gap: 8,
  },
  option: {
    flex: 1,
    height: 36,
    borderRadius: 10,
    borderCurve: 'continuous',
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 4,
    backgroundColor: 'rgba(255, 255, 255, 0.06)',
    borderWidth: 1,
    borderColor: 'transparent',
  },
  optionOn: {
    backgroundColor: 'rgba(255, 255, 255, 0.15)',
    borderColor: 'rgba(255, 255, 255, 0.3)',
  },
  optionText: {
    fontFamily: SANS,
    fontSize: 15,
    fontWeight: '600',
    color: MUTED,
    fontVariant: ['tabular-nums'],
  },
  optionTextOn: {
    color: '#FFFFFF',
  },
  swatchHit: {
    flex: 1,
    height: 40,
    alignItems: 'center',
    justifyContent: 'center',
  },
  swatchRing: {
    width: 34,
    height: 34,
    borderRadius: 17,
    alignItems: 'center',
    justifyContent: 'center',
    borderWidth: 2,
    borderColor: 'transparent',
  },
  swatchRingOn: {
    borderColor: TEXT,
  },
  swatch: {
    width: 24,
    height: 24,
    borderRadius: 12,
    // Graphite is nearly the desk's colour: a clear rim keeps every swatch readable.
    borderWidth: 1.5,
    borderColor: 'rgba(255, 255, 255, 0.4)',
  },
});
