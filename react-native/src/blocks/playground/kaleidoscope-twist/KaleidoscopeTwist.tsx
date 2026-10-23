import {
  BlendMode,
  BlurStyle,
  Canvas,
  FillType,
  FilterMode,
  MipmapMode,
  PaintStyle,
  Picture,
  Skia,
  type SkCanvas,
  type SkImage,
  type SkPaint,
  type SkSurface,
  StrokeCap,
  TileMode,
} from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { useEffect, useState } from 'react';
import { type LayoutChangeEvent, StyleSheet, Text, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import {
  useAnimatedReaction,
  useDerivedValue,
  useFrameCallback,
  useReducedMotion,
  useSharedValue,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';

import {
  addPiece,
  BEAD,
  CELL_R,
  createKaleidoState,
  grownRadius,
  type KaleidoState,
  MAX_TICKS,
  SEED,
  SEG_MAX,
  SEG_MIN,
  SEG_START,
  setSegments,
  SHARD,
  stepKaleido,
  TICK,
  VIEW_SKSL,
} from './kaleidoscopeSim';

type Props = {
  /** Turns the chamber, shakes it and changes the mirrors on a 14 s loop. Off by default. */
  autoPlay?: boolean;
  /** Seed of the pieces in the chamber. */
  seed?: number;
};

// Jewel glass: the colour of each tone in full light, its pale highlight and its deep edge.
const GLASS = ['#ff2d55', '#ffab1f', '#19d08f', '#2f6bff', '#a04dff', '#2fd7ff', '#ff5fb0', '#ffe03a'];
const PALE = ['#ffd0da', '#fff0c8', '#d2ffef', '#d6e3ff', '#ecd9ff', '#d9f8ff', '#ffe0f0', '#fffbd6'];
const DEEP = ['#5c0018', '#6a3300', '#00442c', '#0a1a66', '#2e0866', '#003f55', '#5e0a38', '#5f4a00'];

// The view: its radius (pt, at most), and the window's centre as a share of the safe height.
const VIEW_MAX = 158;
const CENTER_AT = 0.47;
// The wedge of the chamber the mirrors see: its tip, and how far it reaches (cell units).
const APEX_X = 0;
const APEX_Y = 0.05 * CELL_R;
const SPAN = 0.95 * CELL_R;
// While the count morphs the image swirls by up to this much (rad per view radius).
const SWIRL = 0.55;
// The brass: a fixed bezel BEZEL wide, then the knurled ring the hand turns, RING wide.
const BEZEL = 10;
const RING = 22;
const KNURLS = 132;
// Iris blades over the seam of the loop.
const BLADES = 9;
// Light falls on the glass from the upper left of the screen.
const LIGHT_X = -0.55;
const LIGHT_Y = -0.83;
// A drag turns the chamber; on release it keeps spinning at the finger's speed, at most MAX_SPIN.
const MAX_SPIN = 9;
const GRAB_MIN = 18;
// Pinch: the count goes as 1 / scale^PINCH_POWER; past the range it resists.
const PINCH_POWER = 1.4;
const RUBBER = 0.3;
// After a touch the loop waits this long (s), then picks up again.
const USER_HOLD = 4;

const FAMILY = process.env.EXPO_OS === 'ios' ? 'Avenir Next' : 'sans-serif';
const INK = '#e9dcc0';
const MUTED = '#8a7d66';
const FAINT = '#5d5446';

// The chamber's image: CELL_SCALE px per cell unit, CELL_HALF units either side of its centre.
const CELL_SCALE = 2.5;
const CELL_HALF = CELL_R * 1.05;
const CELL_PX = Math.ceil(2 * CELL_HALF * CELL_SCALE);

// May be null if the runtime cannot compile it: the window then shows the chamber unfolded.
const VIEW_EFFECT = Skia.RuntimeEffect.Make(VIEW_SKSL);

function fill(color: string, alpha = 1, blur = 0): SkPaint {
  'worklet';
  const p = Skia.Paint();
  p.setAntiAlias(true);
  p.setColor(Skia.Color(color));
  p.setAlphaf(alpha);
  if (blur > 0) p.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, blur, true));
  return p;
}

function stroke(color: string, width: number, alpha = 1): SkPaint {
  'worklet';
  const p = fill(color, alpha);
  p.setStyle(PaintStyle.Stroke);
  p.setStrokeWidth(width);
  return p;
}

/** A hash of two integers to [0, 1). */
function hash(a: number, b: number) {
  'worklet';
  let h = (Math.imul(a | 0, 374761393) + Math.imul(b | 0, 668265263)) | 0;
  h = Math.imul(h ^ (h >>> 13), 1274126177);
  h ^= h >>> 16;
  return (h >>> 0) / 4294967296;
}

/** A round glass bead, lit from (lx, ly): a glow on the diffuser, the body, a caustic, a glint. */
function drawBead(c: SkCanvas, x: number, y: number, r: number, tone: number, lx: number, ly: number) {
  'worklet';
  const glow = fill(GLASS[tone], 0.32, r * 0.55);
  glow.setBlendMode(BlendMode.Plus);
  c.drawCircle(x, y, r * 1.3, glow);
  const body = Skia.Paint();
  body.setAntiAlias(true);
  body.setShader(
    Skia.Shader.MakeRadialGradient(
      Skia.Point(x + lx * r * 0.35, y + ly * r * 0.35),
      r * 1.25,
      [Skia.Color(PALE[tone]), Skia.Color(GLASS[tone]), Skia.Color(DEEP[tone])],
      [0, 0.45, 1],
      TileMode.Clamp,
    ),
  );
  body.setAlphaf(0.94);
  c.drawCircle(x, y, r, body);
  // Light focused through the bead lands on its far side.
  const caustic = stroke(PALE[tone], r * 0.16, 0.75);
  caustic.setStrokeCap(StrokeCap.Round);
  const back = (Math.atan2(-ly, -lx) * 180) / Math.PI;
  c.drawArc(Skia.XYWHRect(x - r * 0.66, y - r * 0.66, r * 1.32, r * 1.32), back - 38, 76, false, caustic);
  c.drawCircle(x + lx * r * 0.42, y + ly * r * 0.42, r * 0.2, fill('#ffffff', 0.9));
  c.drawCircle(x, y, r - 0.4, stroke(DEEP[tone], 0.8, 0.6));
}

/** A flat shard of glass: an irregular polygon, its bevelled edges catching the light. */
function drawShard(c: SkCanvas, x: number, y: number, r: number, ang: number, tone: number, shape: number, lx: number, ly: number) {
  'worklet';
  const n = 5 + (shape % 2);
  const b = Skia.PathBuilder.Make();
  for (let k = 0; k < n; k += 1) {
    const a = ang + ((k + (hash(shape, k) - 0.5) * 0.55) * 2 * Math.PI) / n;
    const d = r * (0.72 + 0.3 * hash(shape, k + 9));
    if (k === 0) b.moveTo(x + Math.cos(a) * d, y + Math.sin(a) * d);
    else b.lineTo(x + Math.cos(a) * d, y + Math.sin(a) * d);
  }
  b.close();
  const path = b.detach();
  const glow = fill(GLASS[tone], 0.26, r * 0.5);
  glow.setBlendMode(BlendMode.Plus);
  c.drawPath(path, glow);
  const body = Skia.Paint();
  body.setAntiAlias(true);
  body.setShader(
    Skia.Shader.MakeLinearGradient(
      Skia.Point(x + lx * r, y + ly * r),
      Skia.Point(x - lx * r, y - ly * r),
      [Skia.Color(PALE[tone]), Skia.Color(GLASS[tone]), Skia.Color(DEEP[tone])],
      [0, 0.5, 1],
      TileMode.Clamp,
    ),
  );
  body.setAlphaf(0.82);
  c.drawPath(path, body);
  c.drawPath(path, stroke(PALE[tone], 0.9, 0.7));
}

/** The object cell this frame, in its own frame: the frosted diffuser and every piece on it. */
function drawCell(c: SkCanvas, st: KaleidoState) {
  'worklet';
  const R = CELL_R;
  const bg = Skia.Paint();
  bg.setShader(
    Skia.Shader.MakeRadialGradient(
      Skia.Point(0, 0),
      R * 1.4,
      [Skia.Color('#22184a'), Skia.Color('#100c2a'), Skia.Color('#06050f')],
      [0, 0.55, 1],
      TileMode.Clamp,
    ),
  );
  c.drawRect(Skia.XYWHRect(-R * 1.5, -R * 1.5, R * 3, R * 3), bg);
  // Frost on the diffuser glass: a few soft tints that turn with the chamber.
  const frost = [
    [-0.4, -0.5, 0.5, '#4a2a8a'],
    [0.5, -0.2, 0.42, '#123f6a'],
    [-0.1, 0.45, 0.55, '#3a1450'],
    [0.35, 0.6, 0.3, '#0f4a52'],
  ] as const;
  for (const [fx, fy, fr, col] of frost) c.drawCircle(fx * R, fy * R, fr * R, fill(col, 0.35, fr * R * 0.6));
  // Light from the view's upper left, seen from inside the turned chamber.
  const ca = Math.cos(st.angle);
  const sa = Math.sin(st.angle);
  const lx = ca * LIGHT_X + sa * LIGHT_Y;
  const ly = -sa * LIGHT_X + ca * LIGHT_Y;
  for (let pass = 0; pass < 3; pass += 1) {
    const kind = pass === 0 ? SHARD : pass === 1 ? BEAD : SEED;
    for (let i = 0; i < st.count; i += 1) {
      if (st.kind[i] !== kind) continue;
      const r = grownRadius(st, i);
      if (kind === SHARD) drawShard(c, st.x[i], st.y[i], r, st.ang[i], st.tone[i], st.shape[i], lx, ly);
      else if (kind === BEAD) drawBead(c, st.x[i], st.y[i], r, st.tone[i], lx, ly);
      else {
        c.drawCircle(st.x[i], st.y[i], r, fill(GLASS[st.tone[i]], 0.95));
        c.drawCircle(st.x[i] + lx * r * 0.4, st.y[i] + ly * r * 0.4, r * 0.35, fill('#ffffff', 0.85));
      }
    }
  }
}

/** The iris over the window, `open` 0 (shut) … 1 (wide open): BLADES curved steel leaves. */
function drawIris(c: SkCanvas, cx: number, cy: number, viewR: number, open: number) {
  'worklet';
  if (open >= 0.999) return;
  const a = viewR * 1.02 * open;
  const turn = (1 - open) * 0.9;
  const blades = Skia.PathBuilder.Make();
  blades.addCircle(cx, cy, viewR + 1);
  // The opening: a polygon whose sides are the blades' edges, turning as it closes.
  for (let k = 0; k <= BLADES; k += 1) {
    const t = turn + (k * 2 * Math.PI) / BLADES;
    const x = cx + Math.cos(t) * a;
    const y = cy + Math.sin(t) * a;
    if (k === 0) blades.moveTo(x, y);
    else blades.lineTo(x, y);
  }
  blades.close();
  blades.setFillType(FillType.EvenOdd);
  const steel = Skia.Paint();
  steel.setAntiAlias(true);
  steel.setShader(
    Skia.Shader.MakeSweepGradient(
      cx,
      cy,
      [Skia.Color('#26262c'), Skia.Color('#4b4b54'), Skia.Color('#1b1b20'), Skia.Color('#3d3d45'), Skia.Color('#26262c')],
      [0, 0.25, 0.5, 0.75, 1],
      TileMode.Clamp,
    ),
  );
  c.drawPath(blades.detach(), steel);
  // Each blade's leading edge runs from a corner of the opening out to the rim.
  const edge = stroke('#0b0b0e', 1.2, 0.9);
  const shine = stroke('#9a9aa6', 0.6, 0.5);
  for (let k = 0; k < BLADES; k += 1) {
    const t = turn + (k * 2 * Math.PI) / BLADES;
    const x0 = cx + Math.cos(t) * a;
    const y0 = cy + Math.sin(t) * a;
    const t1 = t + 1.25;
    const x1 = cx + Math.cos(t1) * (viewR + 1);
    const y1 = cy + Math.sin(t1) * (viewR + 1);
    const mx = cx + Math.cos(t + 0.55) * (a + (viewR - a) * 0.75);
    const my = cy + Math.sin(t + 0.55) * (a + (viewR - a) * 0.75);
    const p = Skia.PathBuilder.Make();
    p.moveTo(x0, y0);
    p.quadTo(mx, my, x1, y1);
    const path = p.detach();
    c.drawPath(path, edge);
    c.save();
    c.translate(0.6, 0.6);
    c.drawPath(path, shine);
    c.restore();
  }
}

/** Turned brass: a sweep of warm highlights around the eyepiece, starting at `shift` degrees. */
function brassPaint(cx: number, cy: number, shift: number) {
  'worklet';
  const p = Skia.Paint();
  p.setAntiAlias(true);
  p.setStyle(PaintStyle.Stroke);
  p.setShader(
    Skia.Shader.MakeSweepGradient(
      cx,
      cy,
      [
        Skia.Color('#5b3d15'),
        Skia.Color('#c99a45'),
        Skia.Color('#fff0c0'),
        Skia.Color('#b8862f'),
        Skia.Color('#4a3010'),
        Skia.Color('#a9782a'),
        Skia.Color('#f4d690'),
        Skia.Color('#7b5620'),
        Skia.Color('#5b3d15'),
      ],
      [0, 0.12, 0.2, 0.32, 0.5, 0.64, 0.72, 0.86, 1],
      TileMode.Clamp,
      Skia.Matrix().translate(cx, cy).rotate((shift * Math.PI) / 180).translate(-cx, -cy),
      0,
      0,
      360,
    ),
  );
  return p;
}

/** The brass eyepiece: a fixed bezel and the knurled ring that turns with the chamber. */
function drawBrass(c: SkCanvas, cx: number, cy: number, viewR: number, angle: number) {
  'worklet';
  // Bezel.
  const bezel = brassPaint(cx, cy, -30);
  bezel.setStrokeWidth(BEZEL);
  c.drawCircle(cx, cy, viewR + BEZEL / 2, bezel);
  // The knurled ring, a shade darker and a little proud of the bezel.
  const ringR = viewR + BEZEL + RING / 2;
  const ring = brassPaint(cx, cy, -20);
  ring.setStrokeWidth(RING);
  c.drawCircle(cx, cy, ringR, ring);
  c.drawCircle(cx, cy, ringR, stroke('#000000', RING, 0.22));
  const dark = stroke('#2a1a06', 1.3, 0.55);
  const light = stroke('#fff2c8', 0.8, 0.35);
  for (let k = 0; k < KNURLS; k += 1) {
    const t = angle + (k * 2 * Math.PI) / KNURLS;
    const ct = Math.cos(t);
    const st = Math.sin(t);
    const r0 = viewR + BEZEL + 3;
    const r1 = viewR + BEZEL + RING - 3;
    c.drawLine(cx + ct * r0, cy + st * r0, cx + ct * r1, cy + st * r1, dark);
    const u = t + 0.012;
    c.drawLine(cx + Math.cos(u) * r0, cy + Math.sin(u) * r0, cx + Math.cos(u) * r1, cy + Math.sin(u) * r1, light);
  }
  // An engraved index on the ring, so its turn reads at a glance.
  const tick = angle - Math.PI / 2;
  c.drawCircle(cx + Math.cos(tick) * ringR, cy + Math.sin(tick) * ringR, 3.2, fill('#2a1a06', 0.85));
  c.drawCircle(cx + Math.cos(tick) * ringR - 0.6, cy + Math.sin(tick) * ringR - 0.6, 1.4, fill('#fff2c8', 0.5));
  // Grooves and the rim's catchlights.
  c.drawCircle(cx, cy, viewR + BEZEL, stroke('#1d1206', 1.4, 0.9));
  c.drawCircle(cx, cy, viewR + 0.5, stroke('#120b03', 1.6, 1));
  c.drawCircle(cx, cy, viewR + BEZEL + RING, stroke('#1d1206', 1.4, 0.9));
  const rim = stroke('#fff4d0', 1.1, 0.55);
  rim.setStrokeCap(StrokeCap.Round);
  const outer = viewR + BEZEL + RING + 0.4;
  c.drawArc(Skia.XYWHRect(cx - outer, cy - outer, outer * 2, outer * 2), 200, 70, false, rim);
}

/**
 * A kaleidoscope seen through its brass eyepiece. Coloured glass beads and shards lie loose in a
 * round chamber and tumble as it turns; mirrors fold one wedge of it into a mandala of 6 to 12
 * segments. Drag around the window to turn the chamber, pinch to change the mirrors, tap to drop
 * another bead in.
 */
export function KaleidoscopeTwist({ autoPlay = false, seed = 20261014 }: Props) {
  const insets = useSafeAreaInsets();
  const reducedMotion = useReducedMotion();
  const auto = autoPlay && !reducedMotion;
  const [size, setSize] = useState<{ width: number; height: number } | null>(null);
  const [facets, setFacets] = useState(SEG_START);

  const width = size?.width ?? 0;
  const height = size?.height ?? 0;
  // At least 1 pt, so nothing divides by it or draws a negative radius before the first layout.
  const viewR = Math.max(1, Math.min(VIEW_MAX, width / 2 - BEZEL - RING - 16));
  const cx = width / 2;
  const cy = insets.top + (height - insets.top - insets.bottom) * CENTER_AT;
  const outerR = viewR + BEZEL + RING;

  const sim = useSharedValue<KaleidoState | null>(null);
  const cellSurface = useSharedValue<SkSurface | null>(null);
  const cellImage = useSharedValue<SkImage | null>(null);
  const frame = useSharedValue(0);
  const acc = useSharedValue(0);
  const looping = useSharedValue(auto);
  const grabAngle = useSharedValue(0);
  const fingerLast = useSharedValue(0);
  const pinchFrom = useSharedValue(SEG_START);
  const shownEven = useSharedValue(SEG_START);

  // The offscreen surface lives on the UI thread; it is let go there when the block unmounts.
  useEffect(() => {
    return () => {
      scheduleOnUI(() => {
        'worklet';
        const surface = cellSurface.get();
        cellSurface.set(null);
        if (surface) surface.dispose();
        const image = cellImage.get();
        cellImage.set(null);
        if (image) image.dispose();
      });
    };
  }, [cellSurface, cellImage]);

  useEffect(() => {
    looping.set(auto);
    const st = sim.get();
    if (st) st.hold = 0;
  }, [auto, looping, sim]);

  const tick = () => {
    if (process.env.EXPO_OS !== 'web') Haptics.selectionAsync().catch(() => {});
  };
  const dropped = () => {
    if (process.env.EXPO_OS !== 'web') Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
  };

  useFrameCallback((info) => {
    let st = sim.get();
    if (st === null) {
      st = createKaleidoState(seed, looping.get(), reducedMotion);
      sim.set(st);
    }
    st.looping = looping.get();
    st.reduced = reducedMotion;
    const dt = Math.min((info.timeSincePreviousFrame ?? 16) / 1000, MAX_TICKS * TICK);
    let pending = acc.get() + dt;
    let ticks = 0;
    while (pending >= TICK && ticks < MAX_TICKS) {
      stepKaleido(st);
      pending -= TICK;
      ticks += 1;
    }
    acc.set(Math.min(pending, TICK));
    if (ticks === 0 && cellImage.get() !== null) return;
    // The chamber is drawn into one offscreen surface, kept for the block's life.
    let surface = cellSurface.get();
    if (surface === null) {
      surface = Skia.Surface.MakeOffscreen(CELL_PX, CELL_PX);
      if (surface === null) return;
      cellSurface.set(surface);
    }
    const c = surface.getCanvas();
    c.save();
    c.scale(CELL_SCALE, CELL_SCALE);
    c.translate(CELL_HALF, CELL_HALF);
    drawCell(c, st);
    c.restore();
    surface.flush();
    const previous = cellImage.get();
    cellImage.set(surface.makeImageSnapshot());
    // The picture that drew the last image keeps its own reference to it.
    if (previous) previous.dispose();
    frame.set(frame.get() + 1);
  });

  // The readout under the eyepiece follows the count the mirrors are heading for.
  useAnimatedReaction(
    () => {
      frame.get();
      const st = sim.get();
      if (!st) return SEG_START;
      return st.pinching ? Math.max(SEG_MIN, Math.min(SEG_MAX, Math.round(st.seg / 2) * 2)) : st.segTarget;
    },
    (now, before) => {
      if (before !== null && now !== before) scheduleOnRN(setFacets, now);
    },
  );

  const picture = useDerivedValue(() => {
    frame.get();
    const recorder = Skia.PictureRecorder();
    const canvas = recorder.beginRecording(Skia.XYWHRect(0, 0, width, height));
    const st = sim.get();
    canvas.drawColor(Skia.Color('#0b090d'));
    if (width === 0 || st === null) return recorder.finishRecordingAsPicture();

    // The room: a warm dark with a little light around the eyepiece.
    const room = Skia.Paint();
    room.setShader(
      Skia.Shader.MakeRadialGradient(
        Skia.Point(cx, cy - 20),
        Math.max(width, height) * 0.75,
        [Skia.Color('#241c22'), Skia.Color('#120e13'), Skia.Color('#070608')],
        [0, 0.4, 1],
        TileMode.Clamp,
      ),
    );
    canvas.drawRect(Skia.XYWHRect(0, 0, width, height), room);
    canvas.drawCircle(cx, cy + 16, outerR + 6, fill('#000000', 0.6, 22));

    // The object cell, drawn once this frame into its own image, handed to the mirrors as a shader.
    const cell = cellImage.get();
    if (cell === null) return recorder.finishRecordingAsPicture();
    const cellShader = cell.makeShaderOptions(
      TileMode.Decal,
      TileMode.Decal,
      FilterMode.Linear,
      MipmapMode.None,
      Skia.Matrix().translate(-CELL_HALF, -CELL_HALF).scale(1 / CELL_SCALE, 1 / CELL_SCALE),
    );

    // Two even counts either side of the morph, blended, with a swirl that peaks halfway.
    const seg = Math.max(SEG_MIN - 1.5, Math.min(SEG_MAX + 1.5, st.seg));
    const lower = Math.max(4, Math.floor(seg / 2) * 2);
    const t = (seg - lower) / 2;
    const swirl = reducedMotion ? 0 : SWIRL * Math.sin(Math.PI * t);
    const view = Skia.Paint();
    view.setAntiAlias(true);
    if (VIEW_EFFECT) {
      view.setShader(
        VIEW_EFFECT.makeShaderWithChildren(
          [cx, cy, viewR, APEX_X, APEX_Y, SPAN, lower, lower + 2, t, swirl, Math.cos(st.angle), Math.sin(st.angle)],
          [cellShader],
        ),
      );
    } else {
      // No runtime effect: the chamber itself, scaled to the window, turned with the hand.
      const k = viewR / CELL_R;
      view.setShader(
        cell.makeShaderOptions(
          TileMode.Decal,
          TileMode.Decal,
          FilterMode.Linear,
          MipmapMode.None,
          Skia.Matrix()
            .translate(cx, cy)
            .rotate(st.angle)
            .scale(k / CELL_SCALE, k / CELL_SCALE)
            .translate(-CELL_HALF * CELL_SCALE, -CELL_HALF * CELL_SCALE),
        ),
      );
    }
    canvas.drawCircle(cx, cy, viewR + 1, view);

    // Fall-off towards the window's edge, and the eyepiece's glass.
    const vignette = Skia.Paint();
    vignette.setShader(
      Skia.Shader.MakeRadialGradient(
        Skia.Point(cx, cy),
        viewR,
        [Skia.Color('rgba(0,0,0,0)'), Skia.Color('rgba(0,0,0,0)'), Skia.Color('rgba(0,0,0,0.45)'), Skia.Color('rgba(0,0,0,0.92)')],
        [0, 0.6, 0.88, 1],
        TileMode.Clamp,
      ),
    );
    canvas.drawCircle(cx, cy, viewR + 1, vignette);
    const sheen = stroke('#ffffff', 5, 0.07);
    sheen.setStrokeCap(StrokeCap.Round);
    sheen.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 2.5, true));
    canvas.drawArc(Skia.XYWHRect(cx - viewR * 0.82, cy - viewR * 0.82, viewR * 1.64, viewR * 1.64), 198, 52, false, sheen);

    drawIris(canvas, cx, cy, viewR, st.iris);
    drawBrass(canvas, cx, cy, viewR, st.angle);
    return recorder.finishRecordingAsPicture();
  });

  // A named worklet (not an inline arrow) so the React Compiler cannot hoist it out of the gestures.
  function touched() {
    'worklet';
    const st = sim.get();
    if (st) st.hold = USER_HOLD;
  }

  const pan = Gesture.Pan()
    .maxPointers(1)
    .minDistance(4)
    .onStart((event) => {
      const st = sim.get();
      if (!st) return;
      if (Math.hypot(event.x - cx, event.y - cy) < GRAB_MIN) return;
      touched();
      st.dragging = true;
      st.angVel = 0;
      grabAngle.set(st.angle);
      fingerLast.set(Math.atan2(event.y - cy, event.x - cx));
    })
    .onUpdate((event) => {
      const st = sim.get();
      if (!st || !st.dragging) return;
      const now = Math.atan2(event.y - cy, event.x - cx);
      let d = now - fingerLast.get();
      if (d > Math.PI) d -= 2 * Math.PI;
      if (d < -Math.PI) d += 2 * Math.PI;
      fingerLast.set(now);
      st.angle += d;
    })
    .onEnd((event) => {
      const st = sim.get();
      if (!st || !st.dragging) return;
      const rx = event.x - cx;
      const ry = event.y - cy;
      const spin = (rx * event.velocityY - ry * event.velocityX) / Math.max(rx * rx + ry * ry, 900);
      st.angVel = reducedMotion ? 0 : Math.max(-MAX_SPIN, Math.min(MAX_SPIN, spin));
      st.dragging = false;
      touched();
    })
    .onFinalize(() => {
      const st = sim.get();
      if (st) st.dragging = false;
    });

  const pinch = Gesture.Pinch()
    .onStart(() => {
      const st = sim.get();
      if (!st) return;
      touched();
      st.pinching = true;
      pinchFrom.set(st.seg);
      shownEven.set(st.segTarget);
    })
    .onUpdate((event) => {
      const st = sim.get();
      if (!st || !st.pinching) return;
      const raw = pinchFrom.get() / Math.pow(Math.max(0.05, event.scale), PINCH_POWER);
      let seg = raw;
      if (raw < SEG_MIN) seg = SEG_MIN - (SEG_MIN - raw) * RUBBER;
      if (raw > SEG_MAX) seg = SEG_MAX + (raw - SEG_MAX) * RUBBER;
      st.seg = seg;
      st.segVel = 0;
      const even = Math.max(SEG_MIN, Math.min(SEG_MAX, Math.round(seg / 2) * 2));
      if (even !== shownEven.get()) {
        shownEven.set(even);
        scheduleOnRN(tick);
      }
    })
    .onEnd(() => {
      const st = sim.get();
      if (!st) return;
      setSegments(st, st.seg);
    })
    .onFinalize(() => {
      const st = sim.get();
      if (!st) return;
      st.pinching = false;
      touched();
    });

  const tap = Gesture.Tap()
    .maxDuration(300)
    .onEnd((event, success) => {
      const st = sim.get();
      if (!success || !st) return;
      const dx = (event.x - cx) / viewR;
      const dy = (event.y - cy) / viewR;
      const r = Math.hypot(dx, dy);
      if (r > 1) return;
      touched();
      // The same fold the mirrors use, so the bead lands under the finger (and in every mirror).
      const w = (2 * Math.PI) / Math.max(2, Math.round(st.seg));
      const p = 2 * w;
      const a = Math.atan2(dy, dx);
      let m = a - p * Math.floor(a / p);
      if (m > w) m = p - m;
      m += Math.PI / 2 - w / 2;
      const vx = APEX_X + Math.cos(m) * r * SPAN;
      const vy = APEX_Y + Math.sin(m) * r * SPAN;
      const c = Math.cos(st.angle);
      const s = Math.sin(st.angle);
      addPiece(st, c * vx + s * vy, -s * vx + c * vy);
      scheduleOnRN(dropped);
    });

  return (
    <GestureDetector gesture={Gesture.Race(pinch, pan, tap)}>
      <View
        style={styles.screen}
        accessible
        accessibilityRole="adjustable"
        accessibilityLabel={`Kaleidoscope with ${facets} mirror segments. Drag around it to turn, pinch to change the mirrors, tap to drop a bead.`}
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
        <Text pointerEvents="none" style={[styles.title, { top: insets.top + 18 }]}>
          KALEIDOSCOPE
        </Text>
        {size && (
          <View pointerEvents="none" style={[styles.readout, { top: cy + outerR + 30 }]}>
            <Text style={styles.count}>{String(facets).padStart(2, '0')}</Text>
            <Text style={styles.label}>mirror segments</Text>
          </View>
        )}
        <Text pointerEvents="none" style={[styles.hint, { bottom: insets.bottom + 22 }]}>
          Drag to turn · Pinch for mirrors · Tap to drop a bead
        </Text>
      </View>
    </GestureDetector>
  );
}

const styles = StyleSheet.create({
  screen: {
    flex: 1,
    backgroundColor: '#0b090d',
    overflow: 'hidden',
  },
  title: {
    position: 'absolute',
    left: 0,
    right: 0,
    textAlign: 'center',
    fontFamily: FAMILY,
    fontSize: 12,
    fontWeight: '600',
    letterSpacing: 3.2,
    color: MUTED,
  },
  readout: {
    position: 'absolute',
    left: 0,
    right: 0,
    alignItems: 'center',
    gap: 2,
  },
  count: {
    fontFamily: FAMILY,
    fontSize: 40,
    fontWeight: '600',
    letterSpacing: 2,
    color: INK,
    fontVariant: ['tabular-nums'],
  },
  label: {
    fontFamily: FAMILY,
    fontSize: 12,
    fontWeight: '500',
    letterSpacing: 1.6,
    color: MUTED,
    textTransform: 'uppercase',
  },
  hint: {
    position: 'absolute',
    left: 24,
    right: 24,
    textAlign: 'center',
    fontFamily: FAMILY,
    fontSize: 13,
    fontWeight: '500',
    color: FAINT,
  },
});
