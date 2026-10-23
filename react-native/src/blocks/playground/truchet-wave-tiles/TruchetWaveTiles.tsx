import {
  BlurStyle,
  Canvas,
  ClipOp,
  PaintStyle,
  Picture,
  Skia,
  StrokeCap,
  StrokeJoin,
  type SkCanvas,
  type SkPath,
  type SkPathBuilder,
} from '@shopify/react-native-skia';
import { BlurView } from 'expo-blur';
import * as Haptics from 'expo-haptics';
import { useState } from 'react';
import { type LayoutChangeEvent, Pressable, StyleSheet, Text, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  Easing,
  useAnimatedStyle,
  useDerivedValue,
  useFrameCallback,
  useReducedMotion,
  useSharedValue,
  withSpring,
  withTiming,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

type Rgb = [number, number, number];
export type TruchetPalette = { name: string; ground: Rgb; ink: Rgb };

type Props = {
  /** Sends waves from set points, cycles the tile styles and the palettes, on a loop. */
  autoPlay?: boolean;
  /** Two-colour palettes the swatch button steps through. */
  palettes?: TruchetPalette[];
};

const PALETTES: TruchetPalette[] = [
  { name: 'Ink', ground: [16, 26, 46], ink: [239, 228, 206] },
  { name: 'Kiln', ground: [190, 80, 46], ink: [248, 234, 210] },
  { name: 'Moss', ground: [28, 56, 45], ink: [222, 196, 122] },
  { name: 'Cobalt', ground: [240, 234, 222], ink: [38, 58, 182] },
];
const STYLES = ['Arcs', 'Diagonals', 'Triangles'];

// The grid: COLUMNS square tiles across, as many rows as cover the screen, centred.
const COLUMNS = 8;
// Line widths as a share of a tile: thick ribbons for arcs, thinner for diagonals, none once the
// triangles are filled in.
const ARC_WIDTH = 0.2;
const DIAGONAL_WIDTH = 0.13;
// A quarter circle as a cubic Bézier: control points this share of the radius along the tangents.
const KAPPA = 0.5523;

// A flip turns a tile 90° on a spring: angular frequency OMEGA rad/s, damping ratio DAMPING,
// stepped at SUBSTEP s. Mid-turn a tile lifts by up to LIFT of its size, with a shadow.
const OMEGA = (2 * Math.PI) / 0.62;
const DAMPING = 0.52;
const SUBSTEP = 1 / 240;
const LIFT = 0.12;
const LIFT_SHADOW = 7;
// A wave front leaves its origin at WAVE_SPEED pt/s; each tile flips as the front reaches its
// centre. A faint ring marks the front for RING_LIFE s.
const WAVE_SPEED = 520;
const RING_LIFE = 1.6;
const MAX_WAVES = 6;

// The style morphs over STYLE_SPRING; the segmented control's thumb moves on THUMB_SPRING. A palette
// crossfades over PALETTE_TIME ms.
const STYLE_SPRING = { duration: 1100, dampingRatio: 0.92 } as const;
const THUMB_SPRING = { duration: 420, dampingRatio: 0.82 } as const;
const PALETTE_TIME = 1200;
const EASE_IN_OUT = Easing.bezier(0.77, 0, 0.175, 1);
// A touch pauses autoPlay for this long.
const USER_HOLD = 4;

// autoPlay, one LOOP in seconds: four waves (so every tile turns a full 360° and the pattern comes
// back), the styles from arcs through diagonals and triangles back to arcs, four palettes.
const LOOP = 16;
const WAVES_AT = [0.6, 4.4, 8.2, 12.0];
const WAVE_FROM = [
  { x: 0.3, y: 0.34 },
  { x: 0.78, y: 0.68 },
  { x: 0.22, y: 0.8 },
  { x: 0.7, y: 0.18 },
];
const STYLE_AT = [2.4, 6.2, 10.0];
const STYLE_TO = [1, 2, 0];
const PALETTE_AT = [3.3, 7.1, 10.9, 14.6];

const SEGMENT_WIDTH = 92;
const SEGMENT_HEIGHT = 36;
const BAR_PAD = 4;
const SWATCH = SEGMENT_HEIGHT + BAR_PAD * 2;
const CONTROLS_GAP = 10;
const CONTROLS_BOTTOM = 18;
const CONTROLS_WIDTH = SEGMENT_WIDTH * 3 + BAR_PAD * 2 + CONTROLS_GAP + SWATCH;

// A fine print grain over everything: specks a little lighter or darker than what lies beneath.
// Null only if the shader fails to compile: the grain is then left out.
const GRAIN = Skia.RuntimeEffect.Make(`
uniform float strength;

float hash(float2 p) {
  p = fract(p * float2(123.34, 456.21));
  p += dot(p, p + 45.32);
  return fract(p.x * p.y);
}

half4 main(float2 p) {
  float g = hash(floor(p * 1.5));
  float a = abs(g - 0.5) * strength;
  float v = g > 0.5 ? 1.0 : 0.0;
  return half4(v * a, v * a, v * a, a);
}
`);
const GRAIN_STRENGTH = 0.09;

type Sim = { angle: number[]; velocity: number[]; turns: number[] };
type Wave = { x: number; y: number; at: number; hit: number[] };

/** A small seeded generator, so the opening pattern is the same every time. */
function seeded(seed: number) {
  let s = seed >>> 0;
  return () => {
    s = (s + 0x6d2b79f5) >>> 0;
    let t = s;
    t = Math.imul(t ^ (t >>> 15), t | 1);
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

// The opening pattern: each tile turned 0–3 quarters, from a fixed seed.
const OPENING = (() => {
  const random = seeded(7);
  return Array.from({ length: 512 }, () => Math.floor(random() * 4));
})();

function clamp01(v: number) {
  'worklet';
  return v < 0 ? 0 : v > 1 ? 1 : v;
}

function smooth(t: number) {
  'worklet';
  const x = clamp01(t);
  return x * x * x * (x * (x * 6 - 15) + 10);
}

/** The colour `t` of the way from one palette entry to the next, as a Skia colour. */
function mixRgb(a: Rgb, b: Rgb, t: number, alpha = 1) {
  'worklet';
  return Float32Array.of(
    (a[0] + (b[0] - a[0]) * t) / 255,
    (a[1] + (b[1] - a[1]) * t) / 255,
    (a[2] + (b[2] - a[2]) * t) / 255,
    alpha,
  );
}

/** The palette at a position in the list, wrapping round. */
function paletteOf(palettes: TruchetPalette[], i: number) {
  'worklet';
  const n = palettes.length;
  if (n === 0) return PALETTES[0];
  return palettes[((i % n) + n) % n];
}

/**
 * The tile style at `s` (0 arcs, 1 diagonals, 2 triangles): how far along its edges each corner
 * cap reaches (`reach`, a share of the side), how round its edge is (`round`), how much of it is
 * filled in, and the line width (a share of the side).
 */
function styleAt(s: number) {
  'worklet';
  if (s <= 1) {
    const e = smooth(s);
    return { reach: 0.5 + 0.5 * e, round: 1 - e, fill: 0, width: ARC_WIDTH + (DIAGONAL_WIDTH - ARC_WIDTH) * e };
  }
  const e = smooth(s - 1);
  return { reach: 1, round: 0, fill: e, width: DIAGONAL_WIDTH * (1 - e) };
}

/**
 * Adds a tile's motif to a path: the edges of two corner caps (top left and bottom right), turned
 * by (cos, sin) about the centre and scaled by `scale`. `closed` adds the first cap as a filled shape.
 */
function addMotif(
  b: SkPathBuilder,
  cx: number,
  cy: number,
  side: number,
  cos: number,
  sin: number,
  scale: number,
  reach: number,
  round: number,
  closed: boolean,
) {
  'worklet';
  const half = side / 2;
  const r = reach * side;
  // Filled, only the top-left cap counts: with the caps reaching the far corners it is half the
  // tile, the classic triangle tile.
  for (let k = 0; k < (closed ? 1 : 2); k += 1) {
    const sign = k === 0 ? -1 : 1;
    // The cap's corner, and its edge from one side of the tile to the other, in tile space.
    const ox = sign * half;
    const oy = sign * half;
    const p0x = ox - sign * r;
    const p0y = oy;
    const p1x = ox;
    const p1y = oy - sign * r;
    // Control points: along the tangents for a quarter circle, along the chord for a line.
    const a0x = p0x + ((p1x - p0x) / 3) * (1 - round);
    const a0y = p0y + (-sign * KAPPA * r) * round + ((p1y - p0y) / 3) * (1 - round);
    const a1x = p1x + (-sign * KAPPA * r) * round + (((p0x - p1x) / 3) * (1 - round));
    const a1y = p1y + ((p0y - p1y) / 3) * (1 - round);
    const pts = [ox, oy, p0x, p0y, a0x, a0y, a1x, a1y, p1x, p1y];
    const out: number[] = [];
    for (let i = 0; i < pts.length; i += 2) {
      const x = pts[i] * scale;
      const y = pts[i + 1] * scale;
      out.push(cx + x * cos - y * sin, cy + x * sin + y * cos);
    }
    if (closed) {
      b.moveTo(out[0], out[1]);
      b.lineTo(out[2], out[3]);
    } else {
      b.moveTo(out[2], out[3]);
    }
    b.cubicTo(out[4], out[5], out[6], out[7], out[8], out[9]);
    if (closed) b.close();
  }
}

/** Adds a square of half side `half` centred on (cx, cy), turned by (cos, sin). */
function addSquare(b: SkPathBuilder, cx: number, cy: number, half: number, cos: number, sin: number) {
  'worklet';
  const xs = [-half, half, half, -half];
  const ys = [-half, -half, half, half];
  for (let k = 0; k < 4; k += 1) {
    const x = cx + xs[k] * cos - ys[k] * sin;
    const y = cy + xs[k] * sin + ys[k] * cos;
    if (k === 0) b.moveTo(x, y);
    else b.lineTo(x, y);
  }
  b.close();
}

/** Draws a motif path as glazed ink: a soft shadow under it, the ink, and a sheen along its top. */
function drawInk(canvas: SkCanvas, linePath: SkPath, fillPath: SkPath, width: number, ink: Rgb, fill: number) {
  'worklet';
  // The shadow is a darker copy nudged down, unblurred: cheap enough to draw every frame.
  const shadow = Skia.Paint();
  shadow.setAntiAlias(true);
  shadow.setColor(Float32Array.of(0, 0, 0, 0.2));
  const body = Skia.Paint();
  body.setAntiAlias(true);
  body.setColor(mixRgb(ink, ink, 0));
  if (fill > 0.001) {
    canvas.save();
    canvas.translate(0, 1.2);
    canvas.drawPath(fillPath, shadow);
    canvas.restore();
    const solid = body.copy();
    solid.setColor(mixRgb(ink, ink, 0, fill));
    canvas.drawPath(fillPath, solid);
    // A hairline in the same colour closes the seams between neighbouring triangles.
    solid.setStyle(PaintStyle.Stroke);
    solid.setStrokeWidth(1.1);
    canvas.drawPath(fillPath, solid);
  }
  if (width > 0.2) {
    shadow.setStyle(PaintStyle.Stroke);
    shadow.setStrokeWidth(width + 1);
    shadow.setStrokeCap(StrokeCap.Round);
    canvas.save();
    canvas.translate(0, 1.4);
    canvas.drawPath(linePath, shadow);
    canvas.restore();
    body.setStyle(PaintStyle.Stroke);
    body.setStrokeWidth(width);
    body.setStrokeCap(StrokeCap.Round);
    body.setStrokeJoin(StrokeJoin.Round);
    canvas.drawPath(linePath, body);
    const sheen = Skia.Paint();
    sheen.setAntiAlias(true);
    sheen.setStyle(PaintStyle.Stroke);
    sheen.setStrokeWidth(width * 0.28);
    sheen.setStrokeCap(StrokeCap.Round);
    sheen.setColor(Float32Array.of(1, 1, 1, 0.22));
    canvas.save();
    canvas.translate(-width * 0.12, -width * 0.18);
    canvas.drawPath(linePath, sheen);
    canvas.restore();
  }
}

type Scene = {
  lines: SkPath;
  fills: SkPath;
  shadows: SkPath;
  faces: SkPath;
  edges: SkPath;
  liftedLines: SkPath;
  liftedFills: SkPath;
  most: number;
  lifted: boolean;
  fill: number;
  width: number;
};

/** The whole grid in one palette: ground, resting ink, then the turning tiles as lifted slabs. */
function paintScene(canvas: SkCanvas, scene: Scene, ground: Rgb, ink: Rgb, w: number, h: number) {
  'worklet';
  const bg = Skia.Paint();
  bg.setColor(mixRgb(ground, ground, 0));
  canvas.drawRect(Skia.XYWHRect(0, 0, w, h), bg);
  drawInk(canvas, scene.lines, scene.fills, scene.width, ink, scene.fill);
  if (!scene.lifted) return;
  const drop = Skia.Paint();
  drop.setAntiAlias(true);
  drop.setColor(Float32Array.of(0, 0, 0, 0.34));
  drop.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 5, true));
  canvas.drawPath(scene.shadows, drop);
  const face = Skia.Paint();
  face.setAntiAlias(true);
  face.setColor(mixRgb(ground, ink, 0.05 * scene.most));
  canvas.drawPath(scene.faces, face);
  const edge = Skia.Paint();
  edge.setAntiAlias(true);
  edge.setStyle(PaintStyle.Stroke);
  edge.setStrokeWidth(1);
  edge.setColor(mixRgb(ink, ink, 0, 0.14));
  canvas.drawPath(scene.edges, edge);
  drawInk(canvas, scene.liftedLines, scene.liftedFills, scene.width * (1 + LIFT * 0.5), ink, scene.fill);
}

export function TruchetWaveTiles({ autoPlay = false, palettes = PALETTES }: Props) {
  const insets = useSafeAreaInsets();
  const reducedMotion = useReducedMotion();
  const auto = autoPlay && !reducedMotion;
  const [size, setSize] = useState({ width: 0, height: 0 });
  const { width, height } = size;

  const side = width > 0 ? width / COLUMNS : 1;
  const rows = width > 0 ? Math.ceil(height / side) + 1 : 0;
  const count = COLUMNS * rows;
  const top = (height - rows * side) / 2;
  // Where the palette swatch sits, for the reveal to grow from.
  const swatchX = (width - CONTROLS_WIDTH) / 2 + CONTROLS_WIDTH - SWATCH / 2;
  const swatchY = height - insets.bottom - CONTROLS_BOTTOM - SWATCH / 2;

  const sim = useSharedValue<Sim>({ angle: [], velocity: [], turns: [] });
  const waves = useSharedValue<Wave[]>([]);
  const elapsed = useSharedValue(0);
  const tick = useSharedValue(0);
  // The style (0 arcs … 2 triangles) the tiles show, the thumb's position, and the palette.
  const morph = useSharedValue(0);
  const thumb = useSharedValue(0);
  const palette = useSharedValue(0);
  const [selected, setSelected] = useState(0);
  // Dragging: the tile under the finger last frame, and where the finger was.
  const lastTile = useSharedValue(-1);
  const lastX = useSharedValue(0);
  const lastY = useSharedValue(0);
  // autoPlay's clock and how long a touch still holds it.
  const clock = useSharedValue(0);
  const hold = useSharedValue(0);

  const onLayout = (e: LayoutChangeEvent) => {
    const { width: w, height: h } = e.nativeEvent.layout;
    setSize({ width: w, height: h });
  };

  const tapped = () => {
    if (process.env.EXPO_OS !== 'web') Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
  };
  const ticked = () => {
    if (process.env.EXPO_OS !== 'web') Haptics.selectionAsync().catch(() => {});
  };

  /** Starts a tile on its next quarter turn. */
  const flip = (i: number) => {
    'worklet';
    const s = sim.get();
    if (i < 0 || i >= s.angle.length) return;
    if (reducedMotion) {
      // No turning: it snaps a quarter on, its turns kept within 0–3.
      s.turns[i] = (s.turns[i] + 1) % 4;
      s.angle[i] = s.turns[i] * 90;
      s.velocity[i] = 0;
      return;
    }
    // At most a full turn queued: rapid taps or a scribble over one tile never wind it up further.
    if (s.turns[i] * 90 - s.angle[i] >= 360) return;
    s.turns[i] += 1;
  };

  const sendWave = (x: number, y: number) => {
    'worklet';
    const list = waves.get().slice(-(MAX_WAVES - 1));
    const hit: number[] = [];
    for (let i = 0; i < count; i += 1) hit.push(0);
    list.push({ x, y, at: elapsed.get(), hit });
    waves.set(list);
  };

  const chooseStyle = (i: number) => {
    'worklet';
    thumb.set(withSpring(i, THUMB_SPRING));
    morph.set(reducedMotion ? withTiming(i, { duration: 400 }) : withSpring(i, STYLE_SPRING));
    scheduleOnRN(setSelected, i);
  };

  const nextPalette = () => {
    'worklet';
    palette.set(withTiming(Math.floor(palette.get() + 0.5) + 1, { duration: PALETTE_TIME, easing: EASE_IN_OUT }));
  };

  const tileAt = (x: number, y: number) => {
    'worklet';
    const c = Math.floor(x / side);
    const r = Math.floor((y - top) / side);
    if (c < 0 || c >= COLUMNS || r < 0 || r >= rows) return -1;
    return r * COLUMNS + c;
  };

  useFrameCallback((frame) => {
    const dt = Math.min(frame.timeSincePreviousFrame ?? 16, 50) / 1000;
    if (dt <= 0 || count === 0) return;
    const s = sim.get();
    if (s.angle.length !== count) {
      // A fresh grid in the opening pattern.
      const turns: number[] = [];
      const angle: number[] = [];
      const velocity: number[] = [];
      for (let i = 0; i < count; i += 1) {
        const n = OPENING[i % OPENING.length];
        turns.push(n);
        angle.push(n * 90);
        velocity.push(0);
      }
      sim.set({ angle, velocity, turns });
      waves.set([]);
      return;
    }
    const now = elapsed.get() + dt;
    elapsed.set(now);

    if (hold.get() > 0) {
      hold.set(hold.get() - dt);
      if (hold.get() <= 0) clock.set(0);
    } else if (auto) {
      const before = clock.get();
      const t = (before + dt) % LOOP;
      clock.set(t);
      const passed = (at: number) => (before < at && t >= at) || (t < before && (at > before || at <= t));
      for (let k = 0; k < WAVES_AT.length; k += 1) {
        if (passed(WAVES_AT[k])) sendWave(WAVE_FROM[k].x * width, WAVE_FROM[k].y * height);
      }
      for (let k = 0; k < STYLE_AT.length; k += 1) if (passed(STYLE_AT[k])) chooseStyle(STYLE_TO[k]);
      for (let k = 0; k < PALETTE_AT.length; k += 1) if (passed(PALETTE_AT[k])) nextPalette();
    }

    // Wave fronts: every tile the front has reached since last frame starts its turn.
    const list = waves.get();
    if (list.length > 0) {
      const far = Math.hypot(width, height) + side;
      const keep: Wave[] = [];
      for (let k = 0; k < list.length; k += 1) {
        const w = list[k];
        const reach = (now - w.at) * WAVE_SPEED;
        for (let i = 0; i < count; i += 1) {
          if (w.hit[i] === 1) continue;
          const cx = ((i % COLUMNS) + 0.5) * side;
          const cy = top + (Math.floor(i / COLUMNS) + 0.5) * side;
          if (Math.hypot(cx - w.x, cy - w.y) <= reach) {
            w.hit[i] = 1;
            flip(i);
          }
        }
        if (reach < far || now - w.at < RING_LIFE) keep.push(w);
      }
      if (keep.length !== list.length) waves.set(keep);
    }

    // Springs towards each tile's quarter turn; a settled tile has its turns folded back to 0–3.
    if (!reducedMotion) {
      const steps = Math.max(1, Math.ceil(dt / SUBSTEP));
      const h = dt / steps;
      for (let i = 0; i < count; i += 1) {
        const target = s.turns[i] * 90;
        let a = s.angle[i];
        let v = s.velocity[i];
        if (Math.abs(target - a) < 0.01 && Math.abs(v) < 0.05) {
          if (s.turns[i] >= 4) {
            s.turns[i] -= 4;
            s.angle[i] = s.turns[i] * 90;
          }
          continue;
        }
        for (let k = 0; k < steps; k += 1) {
          v += (-OMEGA * OMEGA * (a - target) - 2 * DAMPING * OMEGA * v) * h;
          a += v * h;
        }
        if (Math.abs(target - a) < 0.01 && Math.abs(v) < 0.05) {
          a = target;
          v = 0;
        }
        s.angle[i] = a;
        s.velocity[i] = v;
      }
    }
    tick.set(tick.get() + 1);
  });

  const touched = () => {
    'worklet';
    hold.set(USER_HOLD);
  };

  const tap = Gesture.Tap()
    .maxDistance(10)
    .onEnd((e, success) => {
      if (!success) return;
      touched();
      sendWave(e.x, e.y);
      scheduleOnRN(tapped);
    });

  // One finger: a second one would move the pan's centroid and flip a line of tiles between them.
  const paint = Gesture.Pan()
    .minDistance(4)
    .maxPointers(1)
    .onStart((e) => {
      touched();
      const x0 = e.x - e.translationX;
      const y0 = e.y - e.translationY;
      lastX.set(x0);
      lastY.set(y0);
      lastTile.set(tileAt(x0, y0));
      flip(lastTile.get());
      scheduleOnRN(tapped);
    })
    .onUpdate((e) => {
      touched();
      // Walk from the last point to this one in half-tile steps, so a fast stroke leaves no gaps.
      const dx = e.x - lastX.get();
      const dy = e.y - lastY.get();
      const steps = Math.max(1, Math.ceil(Math.hypot(dx, dy) / (side * 0.5)));
      for (let k = 1; k <= steps; k += 1) {
        const i = tileAt(lastX.get() + (dx * k) / steps, lastY.get() + (dy * k) / steps);
        if (i !== lastTile.get()) {
          lastTile.set(i);
          flip(i);
        }
      }
      lastX.set(e.x);
      lastY.set(e.y);
    });

  const picture = useDerivedValue(() => {
    tick.get();
    const recorder = Skia.PictureRecorder();
    const canvas = recorder.beginRecording(Skia.XYWHRect(0, 0, width, height));
    const s = sim.get();
    if (width === 0 || s.angle.length !== count) return recorder.finishRecordingAsPicture();
    const look = styleAt(morph.get());
    const lineWidth = look.width * side;

    // Resting tiles share one path; turning ones rise as slabs, batched too, so a wave of forty
    // turning tiles still costs a handful of draws.
    const lines = Skia.PathBuilder.Make();
    const fills = Skia.PathBuilder.Make();
    const shadows = Skia.PathBuilder.Make();
    const faces = Skia.PathBuilder.Make();
    const edges = Skia.PathBuilder.Make();
    const liftedLines = Skia.PathBuilder.Make();
    const liftedFills = Skia.PathBuilder.Make();
    let most = 0;
    for (let i = 0; i < count; i += 1) {
      const rad = (s.angle[i] * Math.PI) / 180;
      const cos = Math.cos(rad);
      const sin = Math.sin(rad);
      const cx = ((i % COLUMNS) + 0.5) * side;
      const cy = top + (Math.floor(i / COLUMNS) + 0.5) * side;
      const off = Math.abs(s.angle[i] - s.turns[i] * 90);
      if (off <= 0.5) {
        addMotif(lines, cx, cy, side, cos, sin, 1, look.reach, look.round, false);
        if (look.fill > 0.001) addMotif(fills, cx, cy, side, cos, sin, 1, look.reach, look.round, true);
        continue;
      }
      const lift = Math.sin(Math.PI * clamp01(1 - off / 90));
      most = Math.max(most, lift);
      const scale = 1 + LIFT * lift;
      const half = (side * scale) / 2;
      addSquare(shadows, cx, cy + LIFT_SHADOW * lift, half * (0.94 + 0.06 * lift), cos, sin);
      addSquare(faces, cx, cy, half, cos, sin);
      // Only a tile well off the ground gets a light edge; one settling in blends back in.
      if (lift > 0.3) addSquare(edges, cx, cy, half, cos, sin);
      addMotif(liftedLines, cx, cy, side, cos, sin, scale, look.reach, look.round, false);
      if (look.fill > 0.001) addMotif(liftedFills, cx, cy, side, cos, sin, scale, look.reach, look.round, true);
    }
    const scene: Scene = {
      lines: lines.build(),
      fills: fills.build(),
      shadows: shadows.build(),
      faces: faces.build(),
      edges: edges.build(),
      liftedLines: liftedLines.build(),
      liftedFills: liftedFills.build(),
      most,
      lifted: most > 0,
      fill: look.fill,
      width: lineWidth,
    };

    // A new palette grows out of the swatch button as a circle over the old one.
    const p = palette.get();
    const from = Math.floor(p);
    const grow = p - from;
    const a = paletteOf(palettes, from);
    paintScene(canvas, scene, a.ground, a.ink, width, height);
    if (grow > 0.001) {
      const b = paletteOf(palettes, from + 1);
      const reach = Math.hypot(Math.max(swatchX, width - swatchX), swatchY) + 20;
      const r = reach * grow;
      const circle = Skia.PathBuilder.Make().addCircle(swatchX, swatchY, Math.max(0.1, r)).build();
      canvas.save();
      canvas.clipPath(circle, ClipOp.Intersect, true);
      paintScene(canvas, scene, b.ground, b.ink, width, height);
      canvas.restore();
      const rim = Skia.Paint();
      rim.setAntiAlias(true);
      rim.setStyle(PaintStyle.Stroke);
      rim.setStrokeWidth(3);
      rim.setColor(mixRgb(b.ink, b.ink, 0, 0.5 * (1 - grow)));
      rim.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 4, true));
      canvas.drawCircle(swatchX, swatchY, r, rim);
    }
    const ink = paletteOf(palettes, grow > 0.5 ? from + 1 : from).ink;
    if (GRAIN) {
      const grain = Skia.Paint();
      grain.setShader(GRAIN.makeShader([GRAIN_STRENGTH]));
      canvas.drawRect(Skia.XYWHRect(0, 0, width, height), grain);
    }

    // A faint ring rides each wave front.
    const list = waves.get();
    const now = elapsed.get();
    for (let k = 0; k < list.length; k += 1) {
      const age = now - list[k].at;
      if (age > RING_LIFE) continue;
      const ring = Skia.Paint();
      ring.setAntiAlias(true);
      ring.setStyle(PaintStyle.Stroke);
      ring.setStrokeWidth(10);
      ring.setColor(mixRgb(ink, ink, 0, 0.16 * (1 - age / RING_LIFE)));
      ring.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 8, true));
      canvas.drawCircle(list[k].x, list[k].y, age * WAVE_SPEED, ring);
    }
    return recorder.finishRecordingAsPicture();
  });

  const thumbStyle = useAnimatedStyle(() => ({ transform: [{ translateX: thumb.get() * SEGMENT_WIDTH }] }));
  const swatchStyle = useAnimatedStyle(() => {
    const { ground, ink } = paletteOf(palettes, Math.ceil(palette.get() - 0.001) + 1);
    return {
      backgroundColor: `rgb(${ground[0]}, ${ground[1]}, ${ground[2]})`,
      borderColor: `rgb(${ink[0]}, ${ink[1]}, ${ink[2]})`,
    };
  });

  const pick = (i: number) => {
    if (i === selected) return;
    hold.set(USER_HOLD);
    chooseStyle(i);
    ticked();
  };
  const cycle = () => {
    hold.set(USER_HOLD);
    nextPalette();
    ticked();
  };

  return (
    <View style={styles.root} onLayout={onLayout}>
      <GestureDetector gesture={Gesture.Race(paint, tap)}>
        <View style={StyleSheet.absoluteFill}>
          <Canvas style={StyleSheet.absoluteFill}>
            <Picture picture={picture} />
          </Canvas>
        </View>
      </GestureDetector>
      <View style={[styles.controls, { bottom: insets.bottom + CONTROLS_BOTTOM }]} pointerEvents="box-none">
        <View style={styles.bar}>
          <BlurView intensity={40} tint="dark" style={StyleSheet.absoluteFill} />
          <Animated.View style={[styles.thumb, thumbStyle]} />
          {STYLES.map((label, i) => (
            <Pressable
              key={label}
              onPressIn={() => pick(i)}
              style={styles.segment}
              hitSlop={4}
              accessibilityRole="button"
              accessibilityLabel={label}
              accessibilityState={{ selected: i === selected }}>
              <Text style={[styles.label, i === selected && styles.labelOn]} numberOfLines={1}>
                {label}
              </Text>
            </Pressable>
          ))}
        </View>
        <Pressable onPressIn={cycle} hitSlop={8} style={styles.swatchHit} accessibilityRole="button" accessibilityLabel="Next palette">
          <View style={styles.swatchRing}>
            <BlurView intensity={40} tint="dark" style={StyleSheet.absoluteFill} />
            <Animated.View style={[styles.swatch, swatchStyle]} />
          </View>
        </Pressable>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: '#101a2e' },
  controls: {
    position: 'absolute',
    left: 16,
    right: 16,
    flexDirection: 'row',
    justifyContent: 'center',
    alignItems: 'center',
    gap: CONTROLS_GAP,
  },
  bar: {
    flexDirection: 'row',
    padding: BAR_PAD,
    borderRadius: (SEGMENT_HEIGHT + BAR_PAD * 2) / 2,
    overflow: 'hidden',
    borderWidth: StyleSheet.hairlineWidth,
    borderColor: 'rgba(255,255,255,0.18)',
    backgroundColor: 'rgba(10,12,18,0.28)',
  },
  thumb: {
    position: 'absolute',
    left: BAR_PAD,
    top: BAR_PAD,
    width: SEGMENT_WIDTH,
    height: SEGMENT_HEIGHT,
    borderRadius: SEGMENT_HEIGHT / 2,
    backgroundColor: 'rgba(255,255,255,0.9)',
    boxShadow: '0 2px 8px rgba(0,0,0,0.25)',
  },
  segment: { width: SEGMENT_WIDTH, height: SEGMENT_HEIGHT, alignItems: 'center', justifyContent: 'center' },
  label: { color: 'rgba(255,255,255,0.78)', fontSize: 14, fontWeight: '600', letterSpacing: 0.1 },
  labelOn: { color: '#141821' },
  swatchHit: { width: SWATCH, height: SWATCH },
  swatchRing: {
    flex: 1,
    borderRadius: SWATCH / 2,
    overflow: 'hidden',
    alignItems: 'center',
    justifyContent: 'center',
    borderWidth: StyleSheet.hairlineWidth,
    borderColor: 'rgba(255,255,255,0.18)',
    backgroundColor: 'rgba(10,12,18,0.28)',
  },
  swatch: { width: 24, height: 24, borderRadius: 12, borderWidth: 6 },
});
