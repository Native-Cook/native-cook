import {
  AlphaType,
  BlurStyle,
  Canvas,
  ClipOp,
  ColorType,
  FilterMode,
  matchFont,
  MipmapMode,
  PaintStyle,
  Picture,
  Skia,
  type SkCanvas,
  type SkFont,
  type SkImage,
  type SkPaint,
  type SkPath,
  StrokeCap,
  TileMode,
} from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { useEffect, useMemo, useState } from 'react';
import { type LayoutChangeEvent, StyleSheet, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import { useDerivedValue, useFrameCallback, useReducedMotion, useSharedValue } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

import {
  doneAge,
  dragRuler,
  endsAtText,
  type Engine,
  flip,
  formatClock,
  grabGlass,
  grabRuler,
  makeEngine,
  MAX_MINUTES,
  MIN_MINUTES,
  releaseGlass,
  releaseRuler,
  RULER_PX,
  rulerLive,
  TICK,
  tickEngine,
  turnGlass,
} from './sandHourglassEngine';
import { CELL, COLS, createSandState, GRAIN_SKSL, GRAINS, glassHalfWidth, paintSand, ROWS, type SandState } from './sandHourglassSim';

type Props = {
  /** Sets a session on the ruler, turns the glass over, lets it run out and rests, on a loop. */
  autoPlay?: boolean;
  /** The heading and the task under it. */
  title?: string;
  task?: string;
  /** Seed of the grains' shades and of the sand's random slides. */
  seed?: number;
};

// A slow frame runs at most this many fixed ticks.
const MAX_TICKS = 3;

// The glass, in pt at scale 1, around its centre. The grid fills the inside; the wall is GLASS_WALL
// thick. Each end: a gap, a narrow collar where the glass is set, then a pale oak cap.
const INNER_HALF_H = (ROWS * CELL) / 2;
const GLASS_WALL = 3;
const CAP_GAP = 3;
const COLLAR_H = 4;
const CAP_H = 15;
const CAP_HALF_W = 96;
const CAP_RADIUS = 6;
const HALF_HEIGHT = INNER_HALF_H + CAP_GAP + COLLAR_H + CAP_H;
// The glass shrinks by up to LIFT while it lies on its side, as if lifted off the table to turn it.
const LIFT = 0.1;
// A drag turns the glass when it starts this far from its centre (pt).
const GRAB_MIN = 26;
const GRAB_MAX = 260;

// Light comes from the upper left of the screen, whichever way up the glass is.
const LIGHT_X = -0.5;
const LIGHT_Y = -0.866;
// The shadow on the table: a point h above the table falls h·SHADOW_RUN to the right and
// h·SHADOW_RISE further back; the glass's depth shows as SHADOW_DEPTH of its width.
const SHADOW_RUN = 0.92;
const SHADOW_RISE = 0.2;
const SHADOW_DEPTH = 0.3;
// The light sweep that marks the end of a session (s).
const SWEEP_FOR = 1.15;

// Ruler: how tall its ticks are and where its labels sit (pt).
const MINOR_TICK = 12;
const MAJOR_TICK = 22;

const WALL_LIGHT = '#f6f0e6';
const WALL = '#ece3d5';
const WALL_DEEP = '#ddd1bf';
const TABLE_NEAR = '#d9cab4';
const TABLE_FAR = '#e4d8c6';
const INK = '#2a2119';
const MUTED = '#8b7a66';
const ACCENT = '#c4532a';
const SHADOW = '#4a3420';

const GRAIN_EFFECT = Skia.RuntimeEffect.Make(GRAIN_SKSL)!;

type Geometry = {
  inner: SkPath;
  outer: SkPath;
  // Highlights for the left half of the lower bulb; drawn mirrored for the other three quarters.
  sheen: SkPath;
  streak: SkPath;
  rim: SkPath;
  window: SkPath;
  window2: SkPath;
  /** Half the glass's silhouette width (caps included) at heights 0 … 2·HALF_HEIGHT from its base. */
  profile: number[];
};

const PROFILE_STEPS = 64;

/** The glass's outline from the grain grid's profile: inner edge, outer edge and highlight strips. */
function buildGeometry(): Geometry {
  const steps = 140;
  const half = (t: number) => (glassHalfWidth(t) + 0.5) * CELL;
  const inner = Skia.PathBuilder.Make();
  const outer = Skia.PathBuilder.Make();
  const right: [number, number][] = [];
  const outerRight: [number, number][] = [];
  for (let i = 0; i <= steps; i += 1) {
    const y = -INNER_HALF_H + (2 * INNER_HALF_H * i) / steps;
    const t = Math.abs(y) / INNER_HALF_H;
    const w = half(t);
    // The wall keeps its thickness along the slope of the funnel.
    const slope = (half(Math.min(1, t + 0.01)) - half(Math.max(0, t - 0.01))) / (0.02 * INNER_HALF_H);
    right.push([w, y]);
    outerRight.push([w + GLASS_WALL * Math.sqrt(1 + slope * slope), y]);
  }
  const trace = (b: ReturnType<typeof Skia.PathBuilder.Make>, side: [number, number][], capOut: number) => {
    b.moveTo(-side[0][0], -INNER_HALF_H - capOut);
    b.lineTo(side[0][0], -INNER_HALF_H - capOut);
    for (const [x, y] of side) b.lineTo(x, y);
    b.lineTo(side[side.length - 1][0], INNER_HALF_H + capOut);
    b.lineTo(-side[side.length - 1][0], INNER_HALF_H + capOut);
    for (let i = side.length - 1; i >= 0; i -= 1) b.lineTo(-side[i][0], side[i][1]);
    b.close();
  };
  trace(inner, right, 0);
  trace(outer, outerRight, CAP_GAP);

  // Strips along the left wall of the lower bulb, following its curve.
  const strip = (from: number, to: number, inset: (w: number) => number) => {
    const b = Skia.PathBuilder.Make();
    for (let i = 0; i <= 40; i += 1) {
      const t = from + ((to - from) * i) / 40;
      const x = -inset(half(t));
      if (i === 0) b.moveTo(x, t * INNER_HALF_H);
      else b.lineTo(x, t * INNER_HALF_H);
    }
    return b.detach();
  };
  const sheen = strip(0.24, 0.9, (w) => w * 0.78);
  const streak = strip(0.34, 0.84, (w) => w - 3.2);
  const rim = strip(0.12, 0.96, (w) => w - 1.4);
  // A window behind the viewer, reflected in the bulb as two tall panes.
  const window = strip(0.36, 0.8, (w) => w * 0.5);
  const window2 = strip(0.38, 0.78, (w) => w * 0.38);

  // The silhouette's half-width from the base up, for the shadow.
  const profile: number[] = [];
  const outerHalf = (y: number) => {
    const t = Math.min(1, Math.abs(y) / INNER_HALF_H);
    return half(t) + GLASS_WALL;
  };
  for (let i = 0; i <= PROFILE_STEPS; i += 1) {
    const h = (2 * HALF_HEIGHT * i) / PROFILE_STEPS;
    const y = Math.abs(HALF_HEIGHT - h);
    let r: number;
    if (y > INNER_HALF_H + CAP_GAP + COLLAR_H) r = CAP_HALF_W;
    else if (y > INNER_HALF_H) r = half(1) + 7;
    else r = outerHalf(y);
    profile.push(r);
  }
  return { inner: inner.detach(), outer: outer.detach(), sheen, streak, rim, window, window2, profile };
}

function paintOf(color: string, alpha: number) {
  'worklet';
  const p = Skia.Paint();
  p.setAntiAlias(true);
  p.setColor(Skia.Color(color));
  p.setAlphaf(Math.max(0, Math.min(1, alpha)));
  return p;
}

function strokeOf(color: string, alpha: number, width: number, blur: number) {
  'worklet';
  const p = paintOf(color, alpha);
  p.setStyle(PaintStyle.Stroke);
  p.setStrokeWidth(width);
  if (blur > 0) p.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, blur, true));
  return p;
}

/**
 * A horizontal cylinder gradient across [x0, x1]: dark at both ends, brightest at `light`
 * (0…1 across), so a turned part looks round and lit from the left of the screen.
 */
function turnedShader(x0: number, x1: number, light: number, dark: string, mid: string, bright: string) {
  'worklet';
  return Skia.Shader.MakeLinearGradient(
    Skia.Point(x0, 0),
    Skia.Point(x1, 0),
    [Skia.Color(dark), Skia.Color(mid), Skia.Color(bright), Skia.Color(mid), Skia.Color(dark)],
    [0, light - 0.22, light, light + 0.26, 1],
    TileMode.Clamp,
  );
}

/** One end: a narrow collar where the glass is set, then a pale oak cap with a little grain. */
function drawCap(canvas: SkCanvas, side: number, light: number, cosA: number) {
  'worklet';
  // `side` is -1 for the cap above the glass's own middle, 1 below. All y here grow outwards.
  const collarNear = INNER_HALF_H + CAP_GAP;
  const capNear = collarNear + COLLAR_H;
  const capFar = capNear + CAP_H;
  const span = (near: number, far: number) => (side < 0 ? -far : near);
  const collarHalf = (glassHalfWidth(1) + 0.5) * CELL + 7;
  const collar = Skia.Paint();
  collar.setAntiAlias(true);
  collar.setShader(turnedShader(-collarHalf, collarHalf, light, '#5a3f27', '#8d6842', '#cfa97c'));
  canvas.drawRRect(Skia.RRectXY(Skia.XYWHRect(-collarHalf, span(collarNear - 1, capNear + 1), 2 * collarHalf, COLLAR_H + 2), 1.5, 1.5), collar);
  const slab = Skia.RRectXY(Skia.XYWHRect(-CAP_HALF_W, span(capNear, capFar), 2 * CAP_HALF_W, CAP_H), CAP_RADIUS, CAP_RADIUS);
  const wood = Skia.Paint();
  wood.setAntiAlias(true);
  wood.setShader(turnedShader(-CAP_HALF_W, CAP_HALF_W, light, '#7a5636', '#b88d60', '#efd6b0'));
  canvas.drawRRect(slab, wood);
  // Grain: a few faint lines along the slab.
  canvas.save();
  canvas.clipRRect(slab, ClipOp.Intersect, true);
  for (let g = 0; g < 3; g += 1) {
    const y = span(capNear, capFar) + 3.5 + g * 4.2;
    canvas.drawLine(-CAP_HALF_W + 8 + g * 11, y, CAP_HALF_W - 14 + g * 5, y + 0.6, strokeOf('#6b4a2c', 0.14, 0.8, 0.3));
  }
  canvas.restore();
  // The edge that faces the top of the screen catches the light; the other falls into shadow.
  const upFacing = Math.max(0, side < 0 ? cosA : -cosA);
  const downFacing = Math.max(0, side < 0 ? -cosA : cosA);
  const outerEdge = side < 0 ? -capFar + 0.7 : capFar - 0.7;
  if (upFacing > 0.01) canvas.drawLine(-CAP_HALF_W + 6, outerEdge, CAP_HALF_W - 6, outerEdge, strokeOf('#fff6e6', 0.7 * upFacing, 1.1, 0.3));
  if (downFacing > 0.01) canvas.drawLine(-CAP_HALF_W + 6, outerEdge, CAP_HALF_W - 6, outerEdge, strokeOf('#3b2715', 0.35 * downFacing, 1.1, 0.4));
}

/** The highlight strips of one quarter of the glass, `lit` 0…1 by how much it faces the light. */
function drawSheen(canvas: SkCanvas, geo: Geometry, lit: number) {
  'worklet';
  const unlit = 1 - lit;
  canvas.drawPath(geo.sheen, strokeOf('#ffffff', 0.32 * lit, 8, 3.5));
  canvas.drawPath(geo.window, strokeOf('#ffffff', 0.5 * lit, 4.5, 1.1));
  canvas.drawPath(geo.window2, strokeOf('#ffffff', 0.32 * lit, 3, 1.1));
  canvas.drawPath(geo.streak, strokeOf('#ffffff', 0.85 * lit + 0.08, 1.4, 0.3));
  canvas.drawPath(geo.rim, strokeOf('#fffaf0', 0.16 + 0.3 * unlit, 1.3, 0.6));
}

/** The glass and everything turned with it, around the origin. `sweep` is 0…1 across, or < 0. */
function drawHourglass(canvas: SkCanvas, geo: Geometry, angle: number, sand: SkImage | null, sweep: number) {
  'worklet';
  const cosA = Math.cos(angle);
  const sinA = Math.sin(angle);
  // Where the turned parts are brightest (0…1 across) and how much each side of the glass faces
  // the light: the light stays on the left of the screen as the glass turns.
  const light = 0.5 - 0.17 * cosA;
  const leftLit = (1 + cosA) / 2;

  // The glass body: a little brighter than the room, darker towards the wall where it is thickest.
  canvas.drawPath(geo.inner, paintOf('#ffffff', 0.2));
  canvas.save();
  canvas.clipPath(geo.inner, ClipOp.Intersect, true);
  canvas.drawPath(geo.inner, strokeOf('#7b6650', 0.2, 12, 7));
  canvas.restore();

  if (sand) {
    const grains = Skia.Paint();
    const lx = cosA * LIGHT_X + sinA * LIGHT_Y;
    const ly = -sinA * LIGHT_X + cosA * LIGHT_Y;
    grains.setShader(
      GRAIN_EFFECT.makeShaderWithChildren(
        [CELL, lx, ly],
        [sand.makeShaderOptions(TileMode.Decal, TileMode.Decal, FilterMode.Nearest, MipmapMode.None)],
      ),
    );
    canvas.save();
    canvas.translate((-COLS * CELL) / 2, -INNER_HALF_H);
    canvas.drawRect(Skia.XYWHRect(0, 0, COLS * CELL, ROWS * CELL), grains);
    canvas.restore();
  }

  // The wall: a crisp outer edge, a fainter inner one, and the strips the light makes.
  canvas.drawPath(geo.outer, strokeOf('#6e5844', 0.5, 1.1, 0));
  canvas.drawPath(geo.inner, strokeOf('#6e5844', 0.2, 0.8, 0));
  for (let q = 0; q < 4; q += 1) {
    const sx = q % 2 === 0 ? 1 : -1;
    const sy = q < 2 ? 1 : -1;
    canvas.save();
    canvas.scale(sx, sy);
    drawSheen(canvas, geo, sx > 0 ? leftLit : 1 - leftLit);
    canvas.restore();
  }
  // A small glint where the glass pinches into the neck.
  const glintX = -4.5 * cosA;
  canvas.drawOval(Skia.XYWHRect(glintX - 1.2, -7, 2.4, 14), strokeOf('#ffffff', 0.8, 1, 1.2));

  // The end of a session: a band of light passes across the glass, left to right on screen.
  if (sweep >= 0 && sweep <= 1) {
    canvas.save();
    canvas.clipPath(geo.outer, ClipOp.Intersect, true);
    const dir = cosA >= 0 ? 1 : -1;
    const x = dir * (-150 + 300 * sweep);
    const band = Skia.Paint();
    band.setShader(
      Skia.Shader.MakeLinearGradient(
        Skia.Point(x - 46 * dir, -60 * dir),
        Skia.Point(x + 46 * dir, 60 * dir),
        [Skia.Color('rgba(255,255,255,0)'), Skia.Color('rgba(255,250,238,0.62)'), Skia.Color('rgba(255,255,255,0)')],
        [0, 0.5, 1],
        TileMode.Clamp,
      ),
    );
    canvas.drawRect(Skia.XYWHRect(-200, -INNER_HALF_H - 10, 400, 2 * INNER_HALF_H + 20), band);
    canvas.restore();
  }

  drawCap(canvas, -1, light, cosA);
  drawCap(canvas, 1, light, cosA);
}

/** Text with every digit in a slot of the same width, so a count does not jitter. Centred on cx. */
function drawTabular(canvas: SkCanvas, text: string, cx: number, y: number, font: SkFont, digit: number, paint: SkPaint) {
  'worklet';
  let total = 0;
  for (let i = 0; i < text.length; i += 1) {
    const ch = text[i];
    total += ch >= '0' && ch <= '9' ? digit : font.measureText(ch).width;
  }
  let x = cx - total / 2;
  for (let i = 0; i < text.length; i += 1) {
    const ch = text[i];
    const w = font.measureText(ch).width;
    const slot = ch >= '0' && ch <= '9' ? digit : w;
    canvas.drawText(ch, x + (slot - w) / 2, y, paint, font);
    x += slot;
  }
}

/** A label swap in two halves: the old one fades out over the first half of `t`, the new one in over the second. */
function fadeOut(t: number) {
  'worklet';
  return Math.max(0, Math.min(1, 1 - 2 * t));
}

function fadeIn(t: number) {
  'worklet';
  return Math.max(0, Math.min(1, 2 * t - 1));
}

function drawCentred(canvas: SkCanvas, text: string, cx: number, y: number, font: SkFont, paint: SkPaint) {
  'worklet';
  canvas.drawText(text, cx - font.measureText(text).width / 2, y, paint, font);
}

type Layout = {
  cx: number;
  cy: number;
  scale: number;
  baseY: number;
  horizonY: number;
  timerY: number;
  rulerY: number;
};

function layoutGlass(width: number, height: number, top: number, bottom: number): Layout {
  const rulerY = height - bottom - 70;
  const bandTop = top + 96;
  const bandBottom = rulerY - 54 - 112;
  const scale = Math.max(0.5, Math.min(1.08, (bandBottom - bandTop) / (2 * HALF_HEIGHT)));
  const spare = bandBottom - bandTop - 2 * HALF_HEIGHT * scale;
  const cy = bandTop + spare * 0.4 + HALF_HEIGHT * scale;
  const baseY = cy + HALF_HEIGHT * scale;
  return { cx: width / 2, cy, scale, baseY, horizonY: baseY - 64 * scale, timerY: baseY + 82, rulerY };
}

/** The room: a warm wall lit from the left and the table the glass stands on. */
function buildBackdrop(width: number, height: number, l: Layout, title: string, task: string, fonts: { title: SkFont; task: SkFont }, top: number) {
  const recorder = Skia.PictureRecorder();
  const canvas = recorder.beginRecording(Skia.XYWHRect(0, 0, width, height));
  const wall = Skia.Paint();
  wall.setShader(
    Skia.Shader.MakeRadialGradient(
      Skia.Point(width * 0.1, l.cy - 120),
      Math.max(width, height) * 0.9,
      [Skia.Color(WALL_LIGHT), Skia.Color(WALL), Skia.Color(WALL_DEEP)],
      [0, 0.5, 1],
      TileMode.Clamp,
    ),
  );
  canvas.drawRect(Skia.XYWHRect(0, 0, width, height), wall);
  const table = Skia.Paint();
  table.setShader(
    Skia.Shader.MakeLinearGradient(
      Skia.Point(0, l.horizonY),
      Skia.Point(0, height),
      [Skia.Color(TABLE_FAR), Skia.Color(TABLE_NEAR)],
      null,
      TileMode.Clamp,
    ),
  );
  canvas.drawRect(Skia.XYWHRect(0, l.horizonY, width, height - l.horizonY), table);
  // The table's back edge, softened.
  canvas.drawLine(0, l.horizonY, width, l.horizonY, strokeOf('#bfae96', 0.45, 1, 0.6));
  canvas.drawLine(0, l.horizonY + 1.2, width, l.horizonY + 1.2, strokeOf('#fffaf2', 0.5, 1, 0.4));

  const ink = paintOf(INK, 1);
  canvas.drawText(title, 24, top + 44, ink, fonts.title);
  canvas.drawText(task, 24, top + 68, paintOf(MUTED, 1), fonts.task);
  return recorder.finishRecordingAsPicture();
}

/**
 * A focus timer with an hourglass as its clock: set the minutes on the ruler, turn the glass over and
 * a few thousand grains of terracotta sand, simulated one cell at a time, run through the neck at the
 * rate that empties it on time. The glass stands on a table in afternoon light, casting a long shadow
 * that brightens where light passes through the empty bulb. Turning it mid-session runs the sand back.
 */
export function SandHourglass({ autoPlay = false, title = 'Focus', task = 'Draft the quarterly letter', seed = 20261014 }: Props) {
  const insets = useSafeAreaInsets();
  const reducedMotion = useReducedMotion();
  const auto = autoPlay && !reducedMotion;
  const [size, setSize] = useState<{ width: number; height: number } | null>(null);
  const geo = useMemo(() => buildGeometry(), []);
  const fonts = useMemo(() => {
    const timer = matchFont({ fontSize: 64, fontWeight: '600' });
    return {
      title: matchFont({ fontSize: 30, fontWeight: '700' }),
      task: matchFont({ fontSize: 15, fontWeight: '500' }),
      timer,
      timerDigit: Math.max(...timer.getGlyphWidths(timer.getGlyphIDs('0123456789'))),
      sub: matchFont({ fontSize: 15, fontWeight: '600' }),
      ruler: matchFont({ fontSize: 12, fontWeight: '600' }),
    };
  }, []);

  const width = size?.width ?? 0;
  const height = size?.height ?? 0;
  const layout = useMemo(() => layoutGlass(width, height, insets.top, insets.bottom), [width, height, insets.top, insets.bottom]);
  const backdrop = useMemo(
    () => (size ? buildBackdrop(size.width, size.height, layout, title, task, fonts, insets.top) : null),
    [size, layout, title, task, fonts, insets.top],
  );
  const { cx, cy, scale, baseY, timerY, rulerY } = layout;

  const wallMinutes = () => {
    const d = new Date();
    return d.getHours() * 60 + d.getMinutes();
  };
  const wall = useSharedValue(wallMinutes());
  const engine = useSharedValue<Engine>(makeEngine(auto, reducedMotion));
  const sim = useSharedValue<SandState | null>(null);
  const sand = useSharedValue<SkImage | null>(null);
  const acc = useSharedValue(0);
  const answered = useSharedValue({ ticks: 0, completions: 0 });
  // What the current one-finger drag is doing: 0 nothing, 1 the ruler, 2 the glass.
  const panMode = useSharedValue(0);
  const fingerLast = useSharedValue(0);
  const fingerTurn = useSharedValue(0);

  useEffect(() => {
    engine.modify((e) => {
      'worklet';
      e.looping = auto;
      e.still = reducedMotion;
      e.clock = 0;
      e.hold = 0;
      return e;
    });
  }, [auto, reducedMotion, engine]);

  useEffect(() => {
    const id = setInterval(() => wall.set(wallMinutes()), 1000);
    return () => clearInterval(id);
  }, [wall]);

  const landed = () => {
    if (process.env.EXPO_OS !== 'web') Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Medium).catch(() => {});
  };
  const ticked = () => {
    if (process.env.EXPO_OS !== 'web') Haptics.selectionAsync().catch(() => {});
  };
  const finished = () => {
    if (process.env.EXPO_OS !== 'web') Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success).catch(() => {});
  };

  useFrameCallback((frame) => {
    let st = sim.get();
    if (st === null) {
      st = createSandState(seed);
      paintSand(st);
      sim.set(st);
    }
    const dt = Math.min((frame.timeSincePreviousFrame ?? 16) / 1000, MAX_TICKS * TICK);
    let pending = acc.get() + (Number.isFinite(dt) ? Math.max(0, dt) : 0);
    let ticks = 0;
    let hit = false;
    const minutes = wall.get();
    const e = engine.get();
    while (pending >= TICK && ticks < MAX_TICKS) {
      if (tickEngine(e, st, minutes)) hit = true;
      pending -= TICK;
      ticks += 1;
    }
    // The engine was changed in place; this tells what draws it.
    engine.modify((v) => {
      'worklet';
      return v;
    });
    acc.set(Math.min(pending, TICK));
    if (hit) scheduleOnRN(landed);
    const seen = answered.get();
    if (e.userTicks > seen.ticks) scheduleOnRN(ticked);
    if (e.userCompletions > seen.completions) scheduleOnRN(finished);
    if (e.userTicks !== seen.ticks || e.userCompletions !== seen.completions) {
      answered.set({ ticks: e.userTicks, completions: e.userCompletions });
    }
    if (ticks === 0 && sand.get() !== null) return;
    paintSand(st);
    const data = Skia.Data.fromBytes(st.pixels);
    const image = Skia.Image.MakeImage(
      { width: COLS, height: ROWS, colorType: ColorType.RGBA_8888, alphaType: AlphaType.Premul },
      data,
      COLS * 4,
    );
    const previous = sand.get();
    sand.set(image);
    // The picture that drew the last image keeps its own reference to it.
    if (previous) previous.dispose();
    data.dispose();
  });

  const picture = useDerivedValue(() => {
    const recorder = Skia.PictureRecorder();
    const canvas = recorder.beginRecording(Skia.XYWHRect(0, 0, Math.max(1, width), Math.max(1, height)));
    if (width <= 0 || height <= 0) return recorder.finishRecordingAsPicture();
    const e = engine.get();
    const st = sim.get();
    const a = e.angle;
    const sin2 = Math.sin(a) * Math.sin(a);
    const lift = e.still ? 1 : 1 - LIFT * sin2;
    const s = scale * lift;

    // The shadow on the table, lying to the right, and the light the empty glass lets through it.
    const shade = 1 - 0.85 * sin2;
    if (shade > 0.02) {
      const bottomY = cy + HALF_HEIGHT * s;
      const rise = Math.max(0, baseY - bottomY);
      const outline = Skia.PathBuilder.Make();
      const n = geo.profile.length;
      for (let i = 0; i < n; i += 1) {
        const h = rise + ((2 * HALF_HEIGHT * i) / (n - 1)) * s;
        const r = geo.profile[i] * s * SHADOW_DEPTH;
        const x = cx + h * SHADOW_RUN;
        const y = baseY - h * SHADOW_RISE - r;
        if (i === 0) outline.moveTo(x, y);
        else outline.lineTo(x, y);
      }
      for (let i = n - 1; i >= 0; i -= 1) {
        const h = rise + ((2 * HALF_HEIGHT * i) / (n - 1)) * s;
        const r = geo.profile[i] * s * SHADOW_DEPTH;
        outline.lineTo(cx + h * SHADOW_RUN, baseY - h * SHADOW_RISE + r);
      }
      outline.close();
      const cast = paintOf(SHADOW, 0.24 * shade);
      cast.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 4.5 + 6 * sin2, true));
      canvas.drawPath(outline.build(), cast);
      // Where light passes through an empty bulb it gathers into a warm, brighter patch.
      if (st) {
        const upIsFrameTop = Math.cos(a) >= 0;
        const upper = upIsFrameTop ? st.upper : st.lower;
        const lower = upIsFrameTop ? st.lower : st.upper;
        const bulbs = [
          [HALF_HEIGHT + INNER_HALF_H * 0.5, 1 - upper / GRAINS],
          [HALF_HEIGHT - INNER_HALF_H * 0.5, 1 - lower / GRAINS],
        ];
        for (let b = 0; b < 2; b += 1) {
          const h = rise + bulbs[b][0] * s;
          const glow = paintOf('#fff4dc', 0.55 * bulbs[b][1] * shade);
          glow.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 5, true));
          const gx = cx + h * SHADOW_RUN;
          const gy = baseY - h * SHADOW_RISE;
          canvas.drawOval(Skia.XYWHRect(gx - 34 * s, gy - 7 * s, 68 * s, 14 * s), glow);
        }
      }
      // Contact shadow under the base.
      const contact = paintOf(SHADOW, 0.34 * shade * shade);
      contact.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 3, true));
      canvas.drawOval(Skia.XYWHRect(cx - CAP_HALF_W * s * 1.02, baseY - 4, CAP_HALF_W * s * 2.04, 9), contact);
    }

    canvas.save();
    canvas.translate(cx, cy);
    canvas.rotate((a * 180) / Math.PI, 0, 0);
    canvas.scale(s, s);
    const age = doneAge(e);
    drawHourglass(canvas, geo, a, sand.get(), e.still || age < 0 ? -1 : age / SWEEP_FOR);
    canvas.restore();

    // The timer: what the ruler sets, then what is left above the neck, then a word of thanks.
    const done = e.doneBlend;
    const ink = paintOf(INK, fadeOut(done));
    drawTabular(canvas, formatClock(e.secondsLeft), cx, timerY, fonts.timer, fonts.timerDigit, ink);
    drawCentred(canvas, 'Well done', cx, timerY, fonts.timer, paintOf(INK, fadeIn(done)));
    const run = e.runBlend;
    const idleSub = 'Turn the glass to begin';
    const runSub = endsAtText(e);
    const doneSub = `${e.duration} minutes of focus`;
    drawCentred(canvas, idleSub, cx, timerY + 32, fonts.sub, paintOf(MUTED, fadeOut(run)));
    drawCentred(canvas, runSub, cx, timerY + 32, fonts.sub, paintOf(MUTED, fadeIn(run) * fadeOut(done)));
    drawCentred(canvas, doneSub, cx, timerY + 32, fonts.sub, paintOf(ACCENT, fadeIn(done)));

    // The ruler: a tick a minute, a longer one and a number every five, under a fixed needle.
    const show = 1 - run;
    if (show > 0.01) {
      const halfSpan = width / 2 - 16;
      for (let m = MIN_MINUTES; m <= MAX_MINUTES; m += 1) {
        const x = cx + (m - e.minutes) * RULER_PX;
        const d = Math.abs(x - cx) / halfSpan;
        if (d >= 1) continue;
        const fade = (1 - d * d) * show;
        const major = m % 5 === 0;
        const h = major ? MAJOR_TICK : MINOR_TICK;
        const tick = strokeOf(INK, (major ? 0.78 : 0.4) * fade, major ? 2 : 1.4, 0);
        tick.setStrokeCap(StrokeCap.Round);
        canvas.drawLine(x, rulerY - h / 2, x, rulerY + h / 2, tick);
        if (major) drawCentred(canvas, `${m}`, x, rulerY + 34, fonts.ruler, paintOf(MUTED, fade));
      }
      const needle = strokeOf(ACCENT, show, 3, 0);
      needle.setStrokeCap(StrokeCap.Round);
      canvas.drawLine(cx, rulerY - 20, cx, rulerY + 20, needle);
      canvas.drawCircle(cx, rulerY - 25, 3.2, paintOf(ACCENT, show));
    }
    return recorder.finishRecordingAsPicture();
  });

  const rotate = Gesture.Rotation()
    .onStart(() => {
      engine.modify((e) => {
        'worklet';
        grabGlass(e, true);
        return e;
      });
    })
    .onUpdate((event) => {
      engine.modify((e) => {
        'worklet';
        turnGlass(e, event.rotation);
        return e;
      });
    })
    .onEnd((event) => {
      const minutes = wall.get();
      engine.modify((e) => {
        'worklet';
        releaseGlass(e, event.velocity, true, minutes);
        return e;
      });
    })
    .onFinalize(() => {
      const minutes = wall.get();
      engine.modify((e) => {
        'worklet';
        releaseGlass(e, 0, true, minutes);
        return e;
      });
    });

  const pan = Gesture.Pan()
    .maxPointers(1)
    .minDistance(4)
    .onStart((event) => {
      const x0 = event.x - event.translationX;
      const y0 = event.y - event.translationY;
      const e = engine.get();
      if (rulerLive(e) && Math.abs(y0 - rulerY) < 48) {
        panMode.set(1);
        engine.modify((en) => {
          'worklet';
          grabRuler(en, true);
          return en;
        });
        return;
      }
      const r = Math.hypot(x0 - cx, y0 - cy);
      if (r < GRAB_MIN || r > GRAB_MAX) {
        panMode.set(0);
        return;
      }
      panMode.set(2);
      fingerLast.set(Math.atan2(event.y - cy, event.x - cx));
      fingerTurn.set(0);
      engine.modify((en) => {
        'worklet';
        grabGlass(en, true);
        return en;
      });
    })
    .onUpdate((event) => {
      const mode = panMode.get();
      if (mode === 1) {
        engine.modify((e) => {
          'worklet';
          dragRuler(e, event.translationX, true);
          return e;
        });
      } else if (mode === 2) {
        const now = Math.atan2(event.y - cy, event.x - cx);
        let d = now - fingerLast.get();
        if (d > Math.PI) d -= 2 * Math.PI;
        if (d < -Math.PI) d += 2 * Math.PI;
        fingerLast.set(now);
        fingerTurn.set(fingerTurn.get() + d);
        const turn = fingerTurn.get();
        engine.modify((e) => {
          'worklet';
          turnGlass(e, turn);
          return e;
        });
      }
    })
    .onEnd((event) => {
      const mode = panMode.get();
      const minutes = wall.get();
      if (mode === 1) {
        engine.modify((e) => {
          'worklet';
          releaseRuler(e, event.velocityX, true);
          return e;
        });
      } else if (mode === 2) {
        // The finger's speed across the radius is the glass's spin.
        const rx = event.x - cx;
        const ry = event.y - cy;
        const spin = (rx * event.velocityY - ry * event.velocityX) / Math.max(rx * rx + ry * ry, 900);
        engine.modify((e) => {
          'worklet';
          releaseGlass(e, spin, true, minutes);
          return e;
        });
      }
    })
    .onFinalize(() => {
      const minutes = wall.get();
      engine.modify((e) => {
        'worklet';
        releaseRuler(e, 0, true);
        releaseGlass(e, 0, true, minutes);
        return e;
      });
      panMode.set(0);
    });

  const tap = Gesture.Tap()
    .maxDuration(350)
    .onEnd((event, success) => {
      if (!success) return;
      if (Math.abs(event.x - cx) > CAP_HALF_W * scale + 20 || Math.abs(event.y - cy) > HALF_HEIGHT * scale + 20) return;
      const minutes = wall.get();
      engine.modify((e) => {
        'worklet';
        flip(e, true, minutes);
        return e;
      });
    });

  return (
    <GestureDetector gesture={Gesture.Race(rotate, pan, tap)}>
      <View
        style={styles.screen}
        accessible
        accessibilityRole="adjustable"
        accessibilityLabel={`Focus timer. Set the minutes on the ruler, then turn the hourglass over to start.`}
        onLayout={(event: LayoutChangeEvent) => {
          const { width: w, height: h } = event.nativeEvent.layout;
          if (w > 0 && h > 0) setSize({ width: w, height: h });
        }}
      >
        {size && backdrop && (
          <Canvas style={StyleSheet.absoluteFill}>
            <Picture picture={backdrop} />
            <Picture picture={picture} />
          </Canvas>
        )}
      </View>
    </GestureDetector>
  );
}

const styles = StyleSheet.create({
  screen: {
    flex: 1,
    backgroundColor: WALL,
    overflow: 'hidden',
  },
});
