import {
  BlurStyle,
  Canvas,
  ClipOp,
  FillType,
  PaintStyle,
  Picture,
  Skia,
  type SkCanvas,
  type SkPath,
  type SkRuntimeEffect,
  StrokeCap,
  TileMode,
  vec,
} from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { useEffect, useMemo, useState } from 'react';
import { type LayoutChangeEvent, StyleSheet, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  FadeIn,
  FadeOut,
  useDerivedValue,
  useFrameCallback,
  useReducedMotion,
  useSharedValue,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

import {
  CLAY_POINTS,
  type ClaySim,
  clayCancelScripted,
  clayStep,
  clayTouchDown,
  clayTouchMove,
  clayTouchUp,
  clayUserFingers,
  createClay,
  DENT_RADIUS,
  EVENT_PRESS,
  EVENT_RECOLOUR,
  EVENT_RELEASE,
  EVENT_THUD,
} from './ClayPressBlobPhysics';

export type ClayColour = { name: string; hex: string };

type Props = {
  /** The clays a tap cycles through, in order. */
  palette?: ClayColour[];
  /** Pokes, pulls, recolours, throws and pinches the clay on a loop. */
  autoPlay?: boolean;
  /** Called with a clay's name when a tap recolours it. */
  onRecolour?: (colour: ClayColour) => void;
};

const PALETTE: ClayColour[] = [
  { name: 'Terracotta', hex: '#D2623B' },
  { name: 'Cobalt', hex: '#3954B8' },
  { name: 'Moss', hex: '#6C9858' },
  { name: 'Marigold', hex: '#E3A12F' },
  { name: 'Rose', hex: '#DC7290' },
  { name: 'Lilac', hex: '#8E74CC' },
];

// The studio: a seamless paper sweep tinted with the clay's own hue (a tonal product shot), lit
// from the upper left, with a fine grain. Its tones are the clay's hue at these saturations and
// lightnesses (a share of the clay's saturation, then lightness 0…1).
const PAPER_TOP = { s: 0.34, l: 0.86 };
const PAPER_MID = { s: 0.4, l: 0.91 };
const PAPER_FLOOR = { s: 0.3, l: 0.8 };
const PAPER_INK = { s: 0.45, l: 0.24 };
const GRAIN = 0.05;
// The stage the clay can be thrown around in, inside the header and the hint.
const HEADER = 124;
const FOOTER = 66;
// The header: the kicker line, then the clay's name, large, in a sturdy serif.
const NAME_SIZE = 46;
const DISPLAY_SERIF = process.env.EXPO_OS === 'ios' ? 'Georgia' : 'serif';
// The ink turns with the paper, over the same 0.75 s as the clay's colour change.
const INK_TURN = { transitionProperty: 'color', transitionDuration: 750, transitionTimingFunction: 'ease-in-out' } as const;

// Light comes from the upper left: the inner rim darkens SHADE_OFFSET pt toward the lower right
// and brightens LIGHT_OFFSET pt toward the upper left; the sheen sits on the ring point at
// SHEEN_ANGLE degrees, SHEEN_IN of the way in from the centre.
const SHADE_OFFSET = { x: -8, y: -10, blur: 10 };
const LIGHT_OFFSET = { x: 6, y: 7, blur: 7 };
const SHEEN_ANGLE = -125;
const SHEEN_IN = 0.56;
// Contact shadows, in the paper's deep tone: a wide soft one, a tight one where the clay meets
// the floor, and the clay's own colour bounced onto the floor.
const SHADOW_WIDE = { x: 10, y: 18, blur: 22, alpha: 0.24 };
const SHADOW_TIGHT = { x: 2, y: 4, blur: 5, alpha: 0.36 };
const BOUNCE = { x: 4, y: 14, blur: 26, alpha: 0.22 };
// A satin sheen, not a gloss: a broad glow and a small, soft hot spot.
const SHEEN_BROAD = 0.22;
const SHEEN_HOT = 0.6;
// Each fingerprint has RIDGES loops, spaced RIDGE_STEP of the dent's radius.
const RIDGES = 6;
const RIDGE_STEP = 0.12;

// autoPlay (seconds). Each stroke is one finger, placed in units of the clay's radius from its
// home: down at `at`, moving from `from` to `to` over `move` of its `dur`, up at the end. A
// stroke with `tap` recolours the clay to that entry instead of cycling.
const LOOP = 14;
type Stroke = { at: number; dur: number; from: [number, number]; to: [number, number]; move: number; ease: 'inOut' | 'in'; tap?: number };
const SCRIPT: Stroke[] = [
  // Two pokes that leave dents, then one on the right rim that notches it.
  { at: 0.6, dur: 0.6, from: [0.3, -0.34], to: [0.3, -0.34], move: 1, ease: 'inOut' },
  { at: 1.7, dur: 0.5, from: [-0.36, 0.28], to: [-0.36, 0.28], move: 1, ease: 'inOut' },
  { at: 2.7, dur: 0.6, from: [0.86, 0.14], to: [0.86, 0.14], move: 1, ease: 'inOut' },
  // A pull from the upper right, held, then let go.
  { at: 4.0, dur: 1.05, from: [0.72, -0.62], to: [1.18, -0.98], move: 0.72, ease: 'inOut' },
  // A tap to cobalt.
  { at: 5.8, dur: 0.09, from: [0.04, -0.06], to: [0.04, -0.06], move: 1, ease: 'inOut', tap: 1 },
  // A flick to the right: it squashes against the wall and wobbles back.
  { at: 7.0, dur: 0.15, from: [-0.18, 0.1], to: [0.56, 0.02], move: 1, ease: 'in' },
  // A pinch across the middle, held, then let go.
  { at: 9.2, dur: 0.95, from: [-0.9, 0], to: [-0.68, 0], move: 0.5, ease: 'inOut' },
  { at: 9.2, dur: 0.95, from: [0.9, 0], to: [0.68, 0], move: 0.5, ease: 'inOut' },
  // A tap back to terracotta.
  { at: 11.0, dur: 0.09, from: [-0.06, 0.05], to: [-0.06, 0.05], move: 1, ease: 'inOut', tap: 0 },
];
const SCRIPT_ID = 1000;
// After a touch the loop waits this long, then starts over.
const USER_HOLD = 3;

// Grain on paper, mottling in clay: per-half-point noise plus soft value noise, as light and dark
// specks whose strength is built into the colour.
const GRAIN_SKSL = `
uniform float strength;
uniform float scale;
uniform float mottle;
float hash(float2 p) { return fract(sin(dot(p, float2(127.1, 311.7))) * 43758.5453); }
float noise(float2 p) {
  float2 i = floor(p);
  float2 f = fract(p);
  float2 u = f * f * (3.0 - 2.0 * f);
  return mix(mix(hash(i), hash(i + float2(1.0, 0.0)), u.x), mix(hash(i + float2(0.0, 1.0)), hash(i + float2(1.0, 1.0)), u.x), u.y);
}
half4 main(float2 p) {
  float v = (hash(floor(p * 2.0)) - 0.5) * 0.7 + (noise(p / scale) - 0.5) * mottle;
  float a = clamp(abs(v) * strength, 0.0, 1.0);
  return v > 0.0 ? half4(a, a, a, a) : half4(0.0, 0.0, 0.0, a);
}`;

type Hsl = [number, number, number];

function hexToHsl(hex: string): Hsl {
  const n = parseInt(hex.slice(1), 16);
  const r = ((n >> 16) & 255) / 255;
  const g = ((n >> 8) & 255) / 255;
  const b = (n & 255) / 255;
  const max = Math.max(r, g, b);
  const min = Math.min(r, g, b);
  const l = (max + min) / 2;
  if (max === min) return [0, 0, l];
  const d = max - min;
  const s = l > 0.5 ? d / (2 - max - min) : d / (max + min);
  let h = max === r ? (g - b) / d + (g < b ? 6 : 0) : max === g ? (b - r) / d + 2 : (r - g) / d + 4;
  h *= 60;
  return [h, s, l];
}

function clamp01(v: number) {
  'worklet';
  return Math.min(1, Math.max(0, v));
}

function easeInOut(p: number) {
  'worklet';
  const t = clamp01(p);
  return t < 0.5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2;
}

/** A colour from hue (degrees), saturation and lightness (0…1), with alpha. */
function hsla(h: number, s: number, l: number, a: number) {
  'worklet';
  const hh = (((h % 360) + 360) % 360) / 60;
  const c = (1 - Math.abs(2 * l - 1)) * s;
  const x = c * (1 - Math.abs((hh % 2) - 1));
  const m = l - c / 2;
  let r = 0;
  let g = 0;
  let b = 0;
  if (hh < 1) [r, g, b] = [c, x, 0];
  else if (hh < 2) [r, g, b] = [x, c, 0];
  else if (hh < 3) [r, g, b] = [0, c, x];
  else if (hh < 4) [r, g, b] = [0, x, c];
  else if (hh < 5) [r, g, b] = [x, 0, c];
  else [r, g, b] = [c, 0, x];
  return new Float32Array([r + m, g + m, b + m, a]);
}

/** Two colours mixed `p` of the way, straight through RGB (no detour through other hues). */
function mixRgb(a: Float32Array, b: Float32Array, p: number) {
  'worklet';
  return new Float32Array([
    a[0] + (b[0] - a[0]) * p,
    a[1] + (b[1] - a[1]) * p,
    a[2] + (b[2] - a[2]) * p,
    a[3] + (b[3] - a[3]) * p,
  ]);
}

type Tone = (a: number) => Float32Array;
type Tones = { base: Tone; light: Tone; shade: Tone; deep: Tone };

/** The tones one clay is lit with. */
function tones(c: Hsl): Tones {
  'worklet';
  const [h, s, l] = c;
  return {
    base: (a: number) => hsla(h, s, l, a),
    light: (a: number) => hsla(h - 6, s * 0.8, l + (0.96 - l) * 0.5, a),
    shade: (a: number) => hsla(h + 8, Math.min(1, s * 1.08), l * 0.56, a),
    deep: (a: number) => hsla(h + 12, s, l * 0.34, a),
  };
}

/** Two clays' tones mixed `p` of the way. */
function mixTones(a: Tones, b: Tones, p: number): Tones {
  'worklet';
  return {
    base: (x: number) => mixRgb(a.base(x), b.base(x), p),
    light: (x: number) => mixRgb(a.light(x), b.light(x), p),
    shade: (x: number) => mixRgb(a.shade(x), b.shade(x), p),
    deep: (x: number) => mixRgb(a.deep(x), b.deep(x), p),
  };
}

/** The paper tone of a clay: its hue at a share `t.s` of its saturation and lightness `t.l`. */
function paper(c: Hsl, t: { s: number; l: number }, a: number) {
  'worklet';
  return hsla(c[0], Math.min(1, c[1] * t.s), t.l, a);
}

/** The paper tone between two clays, `p` of the way. */
function paperMix(a: Hsl, b: Hsl, t: { s: number; l: number }, p: number, alpha: number) {
  'worklet';
  return mixRgb(paper(a, t, alpha), paper(b, t, alpha), p);
}

/** A hex colour for React Native styles, from a colour as four floats. */
function hexOf(c: Float32Array) {
  const to = (v: number) => Math.round(Math.min(1, Math.max(0, v)) * 255).toString(16).padStart(2, '0');
  return `#${to(c[0])}${to(c[1])}${to(c[2])}`;
}

/** A smooth closed curve through the ring (Catmull-Rom as cubics), added to `b`. */
function traceRing(b: ReturnType<typeof Skia.PathBuilder.Make>, s: ClaySim) {
  'worklet';
  const n = CLAY_POINTS;
  b.moveTo(s.x[0], s.y[0]);
  for (let i = 0; i < n; i += 1) {
    const i0 = (i + n - 1) % n;
    const i2 = (i + 1) % n;
    const i3 = (i + 2) % n;
    b.cubicTo(
      s.x[i] + (s.x[i2] - s.x[i0]) / 6,
      s.y[i] + (s.y[i2] - s.y[i0]) / 6,
      s.x[i2] - (s.x[i3] - s.x[i]) / 6,
      s.y[i2] - (s.y[i3] - s.y[i]) / 6,
      s.x[i2],
      s.y[i2],
    );
  }
  b.close();
}

function blurPaint(color: Float32Array, sigma: number) {
  'worklet';
  const p = Skia.Paint();
  p.setAntiAlias(true);
  p.setColor(color);
  if (sigma > 0) p.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, sigma, true));
  return p;
}

function drawShifted(canvas: SkCanvas, path: SkPath, dx: number, dy: number, color: Float32Array, sigma: number) {
  'worklet';
  canvas.save();
  canvas.translate(dx, dy);
  canvas.drawPath(path, blurPaint(color, sigma));
  canvas.restore();
}

/** One finger's dent: a soft hollow lit from the upper left, a raised lip, and fading ridges. */
function drawDent(
  canvas: SkCanvas,
  cx: number,
  cy: number,
  depth: number,
  ridge: number,
  seed: number,
  t: ReturnType<typeof tones>,
) {
  'worklet';
  const d = depth * depth * (3 - 2 * depth);
  if (d < 0.01) return;
  const r = DENT_RADIUS * (0.6 + 0.4 * d);
  // Light direction, toward the upper left.
  const lx = -0.62;
  const ly = -0.78;
  // The lip: clay pushed up around the dent, lit on its outer upper-left side.
  const lip = Skia.Paint();
  lip.setAntiAlias(true);
  lip.setStyle(PaintStyle.Stroke);
  lip.setStrokeWidth(7);
  lip.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 2.6, true));
  lip.setShader(
    Skia.Shader.MakeLinearGradient(
      vec(cx + lx * (r + 4), cy + ly * (r + 4)),
      vec(cx - lx * (r + 4), cy - ly * (r + 4)),
      [t.light(0.55 * d), t.light(0), t.shade(0.35 * d)],
      [0, 0.5, 1],
      TileMode.Clamp,
    ),
  );
  canvas.drawCircle(cx, cy, r + 3, lip);
  // The hollow: in shadow on the side facing the light, catching it on the far wall.
  const hollow = Skia.Paint();
  hollow.setAntiAlias(true);
  hollow.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 3.2, true));
  hollow.setShader(
    Skia.Shader.MakeLinearGradient(
      vec(cx + lx * r, cy + ly * r),
      vec(cx - lx * r, cy - ly * r),
      [t.deep(0.5 * d), t.shade(0.22 * d), t.base(0), t.light(0.5 * d)],
      [0, 0.35, 0.62, 1],
      TileMode.Clamp,
    ),
  );
  canvas.drawCircle(cx, cy, r, hollow);
  if (ridge < 0.02) return;
  // The fingerprint: broken, slightly oval loops, embossed by a dark and a light copy.
  const a = ridge * ridge;
  const b = Skia.PathBuilder.Make();
  for (let k = 1; k <= RIDGES; k += 1) {
    const rr = r * (0.12 + k * RIDGE_STEP);
    const start = seed * 360 + k * 47;
    const sweep = 290 - (k % 2) * 46 - ((seed * 97 * k) % 30);
    b.addArc(Skia.XYWHRect(-rr, -rr * 0.8, rr * 2, rr * 1.6), start, sweep);
  }
  const loops = b.build();
  const line = Skia.Paint();
  line.setAntiAlias(true);
  line.setStyle(PaintStyle.Stroke);
  line.setStrokeWidth(1.1);
  line.setStrokeCap(StrokeCap.Round);
  canvas.save();
  canvas.translate(cx + 0.7, cy + 0.9);
  canvas.rotate(seed * 180 - 30, 0, 0);
  line.setColor(t.light(0.5 * a));
  canvas.drawPath(loops, line);
  canvas.restore();
  canvas.save();
  canvas.translate(cx - 0.4, cy - 0.5);
  canvas.rotate(seed * 180 - 30, 0, 0);
  line.setColor(t.deep(0.34 * a));
  canvas.drawPath(loops, line);
  canvas.restore();
}

/** The whole scene: the studio sweep, the clay's shadows, its body, dents, rims and sheen. */
function drawScene(
  canvas: SkCanvas,
  s: ClaySim,
  width: number,
  height: number,
  hsl: Hsl[],
  grain: SkRuntimeEffect,
) {
  'worklet';
  const r0 = s.r0;
  // The clay's colour and the paper's, mid-change: the paper turns with the change as a whole.
  const p = s.recolour;
  const from = hsl[s.colourFrom] ?? hsl[0];
  const to = hsl[s.colourTo] ?? hsl[0];
  const e = easeInOut(p);
  // The studio.
  const wall = Skia.Paint();
  wall.setShader(
    Skia.Shader.MakeLinearGradient(
      vec(0, 0),
      vec(0, height),
      [paperMix(from, to, PAPER_TOP, e, 1), paperMix(from, to, PAPER_MID, e, 1), paperMix(from, to, PAPER_FLOOR, e, 1)],
      [0, 0.46, 1],
      TileMode.Clamp,
    ),
  );
  canvas.drawRect(Skia.XYWHRect(0, 0, width, height), wall);
  const pool = Skia.Paint();
  pool.setShader(
    Skia.Shader.MakeRadialGradient(
      vec(s.homeX - r0 * 0.5, s.homeY - r0 * 0.9),
      width * 0.95,
      [Skia.Color('rgba(255,255,255,0.62)'), Skia.Color('rgba(255,255,255,0)')],
      null,
      TileMode.Clamp,
    ),
  );
  canvas.drawRect(Skia.XYWHRect(0, 0, width, height), pool);
  const paper = Skia.Paint();
  paper.setShader(grain.makeShader([GRAIN, 140, 0.5]));
  canvas.drawRect(Skia.XYWHRect(0, 0, width, height), paper);

  // Mid-change, the shading follows the mix while the new colour spreads from the tap.
  const fromTones = tones(from);
  const toTones = tones(to);
  const mixed = mixTones(fromTones, toTones, e);
  const ink = paperMix(from, to, PAPER_INK, e, 1);
  const shadowTone = (a: number) => new Float32Array([ink[0], ink[1], ink[2], a]);

  const bb = Skia.PathBuilder.Make();
  traceRing(bb, s);
  const body = bb.build();
  const ib = Skia.PathBuilder.Make();
  ib.setFillType(FillType.EvenOdd);
  ib.addRect(Skia.XYWHRect(-width, -height, width * 3, height * 3));
  traceRing(ib, s);
  const outside = ib.build();

  // Shadows on the floor.
  drawShifted(canvas, body, SHADOW_WIDE.x, SHADOW_WIDE.y, shadowTone(SHADOW_WIDE.alpha), SHADOW_WIDE.blur);
  drawShifted(canvas, body, BOUNCE.x, BOUNCE.y, mixed.base(BOUNCE.alpha), BOUNCE.blur);
  drawShifted(canvas, body, SHADOW_TIGHT.x, SHADOW_TIGHT.y, shadowTone(SHADOW_TIGHT.alpha), SHADOW_TIGHT.blur);

  // The body, with the new colour spreading from the tap as a soft-edged disc.
  canvas.save();
  canvas.clipPath(body, ClipOp.Intersect, true);
  canvas.drawPath(body, blurPaint(fromTones.base(1), 0));
  if (p < 1) {
    const ox = s.cx + s.recolourX;
    const oy = s.cy + s.recolourY;
    if (s.calm) {
      canvas.drawPath(body, blurPaint(toTones.base(p), 0));
    } else {
      canvas.drawCircle(ox, oy, e * r0 * 2.7, blurPaint(toTones.base(1), 12));
    }
  }

  // Where the sheen sits: part way out toward the upper-left ring point, so it rides the shape.
  const k = Math.round((((SHEEN_ANGLE + 360) % 360) / 360) * CLAY_POINTS) % CLAY_POINTS;
  const ax = s.cx + (s.x[k] - s.cx) * SHEEN_IN - s.cvx * 0.018;
  const ay = s.cy + (s.y[k] - s.cy) * SHEEN_IN - s.cvy * 0.018;
  // Volume: light falls off from the sheen; the far side turns into the shade.
  const vol = Skia.Paint();
  vol.setShader(
    Skia.Shader.MakeRadialGradient(vec(ax, ay), r0 * 2.1, [mixed.light(0.5), mixed.light(0), mixed.shade(0), mixed.shade(0.6)], [0, 0.32, 0.5, 1], TileMode.Clamp),
  );
  canvas.drawPath(body, vol);
  // Clay grain and mottling, carried with the body.
  const clay = Skia.Paint();
  clay.setShader(grain.makeShader([0.09, 16, 0.28], Skia.Matrix().translate(s.cx, s.cy)));
  canvas.drawPath(body, clay);

  for (let i = 0; i < s.dents.length; i += 1) {
    const d = s.dents[i];
    if (d.live) drawDent(canvas, s.cx + d.x, s.cy + d.y, d.depth, d.ridge, d.seed, mixed);
  }

  // The rim: dark toward the lower right, lit toward the upper left, and a thin bounce of floor
  // light right on the lower edge.
  drawShifted(canvas, outside, SHADE_OFFSET.x, SHADE_OFFSET.y, mixed.deep(0.62), SHADE_OFFSET.blur);
  drawShifted(canvas, outside, LIGHT_OFFSET.x, LIGHT_OFFSET.y, mixed.light(0.6), LIGHT_OFFSET.blur);
  drawShifted(canvas, outside, -1.5, -2, mixed.light(0.28), 1.6);

  // The sheen: a broad soft glow and a small hot spot, turned along the surface.
  const tx = s.x[(k + 1) % CLAY_POINTS] - s.x[(k + CLAY_POINTS - 1) % CLAY_POINTS];
  const ty = s.y[(k + 1) % CLAY_POINTS] - s.y[(k + CLAY_POINTS - 1) % CLAY_POINTS];
  const turn = (Math.atan2(ty, tx) * 180) / Math.PI;
  canvas.save();
  canvas.translate(ax, ay);
  canvas.rotate(turn, 0, 0);
  canvas.drawOval(Skia.XYWHRect(-r0 * 0.36, -r0 * 0.17, r0 * 0.72, r0 * 0.34), blurPaint(new Float32Array([1, 1, 1, SHEEN_BROAD]), 11));
  canvas.drawOval(Skia.XYWHRect(-r0 * 0.15, -r0 * 0.12, r0 * 0.24, r0 * 0.1), blurPaint(new Float32Array([1, 1, 1, SHEEN_HOT]), 3.2));
  canvas.restore();
  canvas.restore();
}

export function ClayPressBlob({ palette: given, autoPlay = false, onRecolour }: Props) {
  // The palette, never empty: an empty one falls back to the six clays.
  const palette = given && given.length > 0 ? given : PALETTE;
  const insets = useSafeAreaInsets();
  const reducedMotion = useReducedMotion();
  const auto = autoPlay && !reducedMotion;
  const [size, setSize] = useState<{ width: number; height: number } | null>(null);
  const [colour, setColour] = useState(0);
  const hsl = useMemo(() => palette.map((c) => hexToHsl(c.hex)), [palette]);
  const grain = useMemo(() => Skia.RuntimeEffect.Make(GRAIN_SKSL)!, []);

  const width = size?.width ?? 0;
  const height = size?.height ?? 0;
  const top = insets.top + HEADER;
  const bottom = height - insets.bottom - FOOTER;

  const sim = useSharedValue<ClaySim | null>(null);
  const tick = useSharedValue(0);
  const stage = useSharedValue({ width: 0, height: 0, top: 0, bottom: 0 });
  const clock = useSharedValue(0);
  const looping = useSharedValue(auto);
  const hold = useSharedValue(0);
  // Which scripted strokes have their finger down.
  const strokeDown = useSharedValue<boolean[]>(SCRIPT.map(() => false));

  useEffect(() => {
    if (width > 0) stage.set({ width, height, top, bottom });
  }, [width, height, top, bottom, stage]);

  useEffect(() => {
    looping.set(auto);
    clock.set(0);
    hold.set(0);
  }, [auto, clock, hold, looping]);

  const feel = (events: number) => {
    if (process.env.EXPO_OS === 'web') return;
    if (events & EVENT_THUD) Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Medium).catch(() => {});
    else if (events & EVENT_RECOLOUR) Haptics.selectionAsync().catch(() => {});
    else if (events & EVENT_PRESS) Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Soft).catch(() => {});
    else if (events & EVENT_RELEASE) Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
  };
  const recoloured = (index: number, byUser: boolean) => {
    setColour(index);
    if (byUser && palette[index]) onRecolour?.(palette[index]);
  };

  useFrameCallback((frame) => {
    // The script and the simulation share one clock, capped so a slow frame cannot skip ahead.
    const dt = Math.min(frame.timeSincePreviousFrame ?? 16, 1000 / 30) / 1000;
    const st = stage.get();
    if (st.width === 0) return;
    let s = sim.get();
    if (!s || s.right !== st.width - 20 || s.bottom !== st.bottom || s.top !== st.top) {
      // A new stage (first frame, a new layout) lays a fresh body down in the colour it had.
      const kept = s ? Math.min(s.colourTo, hsl.length - 1) : 0;
      s = createClay(st.width, st.height, st.top, st.bottom, hsl.length, reducedMotion);
      s.colourFrom = kept;
      s.colourTo = kept;
      sim.set(s);
    }
    // The script: each stroke's finger is down while the loop is inside it.
    if (hold.get() > 0) {
      hold.set(hold.get() - dt);
      if (hold.get() <= 0) clock.set(0);
    } else if (looping.get()) {
      const t = (clock.get() + dt) % LOOP;
      clock.set(t);
      const down = strokeDown.get();
      for (let i = 0; i < SCRIPT.length; i += 1) {
        const k = SCRIPT[i];
        const u = (t - k.at) / k.dur;
        const m = clamp01(u / k.move);
        const e = k.ease === 'in' ? m * m : easeInOut(m);
        const x = s.homeX + (k.from[0] + (k.to[0] - k.from[0]) * e) * s.r0;
        const y = s.homeY + (k.from[1] + (k.to[1] - k.from[1]) * e) * s.r0;
        if (u >= 0 && u < 1) {
          if (!down[i]) {
            clayTouchDown(s, SCRIPT_ID + i, x, y, true, k.tap ?? -1);
            down[i] = true;
          } else clayTouchMove(s, SCRIPT_ID + i, x, y);
        } else if (down[i]) {
          clayTouchMove(s, SCRIPT_ID + i, x, y);
          clayTouchUp(s, SCRIPT_ID + i, false);
          down[i] = false;
        }
      }
    }
    clayStep(s, dt);
    if (s.events !== 0) {
      if (s.events & EVENT_RECOLOUR) scheduleOnRN(recoloured, s.colourTo, s.eventsByUser);
      if (s.eventsByUser) scheduleOnRN(feel, s.events);
      s.events = 0;
    }
    tick.set(tick.get() + 1);
  });

  const touched = () => {
    'worklet';
    hold.set(USER_HOLD);
    const s = sim.get();
    if (!s) return;
    clayCancelScripted(s);
    const down = strokeDown.get();
    for (let i = 0; i < down.length; i += 1) down[i] = false;
  };

  // One manual gesture sees every finger from the moment it lands, so a press starts at once.
  const touch = Gesture.Manual()
    .onTouchesDown((event, manager) => {
      manager.activate();
      touched();
      const s = sim.get();
      if (!s) return;
      for (const t of event.changedTouches) clayTouchDown(s, t.id, t.x, t.y, false, -1);
    })
    .onTouchesMove((event) => {
      const s = sim.get();
      if (!s) return;
      hold.set(USER_HOLD);
      for (const t of event.changedTouches) clayTouchMove(s, t.id, t.x, t.y);
    })
    .onTouchesUp((event, manager) => {
      const s = sim.get();
      if (s) for (const t of event.changedTouches) clayTouchUp(s, t.id, false);
      if (!s || clayUserFingers(s) === 0) manager.end();
    })
    .onTouchesCancelled((event, manager) => {
      const s = sim.get();
      if (s) for (const t of event.changedTouches) clayTouchUp(s, t.id, true);
      manager.end();
    });

  const picture = useDerivedValue(() => {
    tick.get();
    const recorder = Skia.PictureRecorder();
    const canvas = recorder.beginRecording(Skia.XYWHRect(0, 0, width, height));
    const s = sim.get();
    if (s && width > 0) drawScene(canvas, s, width, height, hsl, grain);
    return recorder.finishRecordingAsPicture();
  });

  const onLayout = (e: LayoutChangeEvent) => {
    const { width: w, height: h } = e.nativeEvent.layout;
    setSize({ width: w, height: h });
  };

  const current = palette[colour] ?? palette[0];
  const tone = hsl[colour] ?? hsl[0];
  const inkHex = tone ? hexOf(paper(tone, PAPER_INK, 1)) : '#3a3330';
  const paperHex = tone ? hexOf(paper(tone, PAPER_MID, 1)) : '#f2eee8';

  return (
    <View style={[styles.root, { backgroundColor: paperHex }]} onLayout={onLayout}>
      <GestureDetector gesture={touch}>
        <View style={StyleSheet.absoluteFill}>
          {size ? (
            <Canvas style={StyleSheet.absoluteFill}>
              <Picture picture={picture} />
            </Canvas>
          ) : null}
        </View>
      </GestureDetector>
      <View pointerEvents="none" style={[styles.header, { top: insets.top + 18 }]}>
        <View style={styles.kickerRow}>
          <Animated.Text style={[styles.kicker, INK_TURN, { color: inkHex }]}>CLAY STUDY</Animated.Text>
          <Animated.Text style={[styles.kicker, INK_TURN, { color: inkHex }]}>Nº 04</Animated.Text>
        </View>
        <View style={styles.nameBox}>
          <Animated.Text
            key={current.name}
            entering={FadeIn.duration(300).delay(120)}
            exiting={FadeOut.duration(180)}
            numberOfLines={1}
            style={[styles.name, { color: inkHex }]}
          >
            {current.name}
          </Animated.Text>
        </View>
      </View>
      <Animated.Text pointerEvents="none" style={[styles.hint, INK_TURN, { bottom: insets.bottom + 26, color: inkHex }]}>
        Press · Pull · Flick · Pinch · Tap to recolour
      </Animated.Text>
    </View>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, overflow: 'hidden' },
  header: {
    position: 'absolute',
    left: 24,
    right: 24,
    gap: 10,
  },
  kickerRow: { flexDirection: 'row', justifyContent: 'space-between' },
  kicker: {
    fontSize: 11,
    fontWeight: '700',
    letterSpacing: 2.6,
    opacity: 0.72,
  },
  nameBox: { height: NAME_SIZE * 1.18 },
  name: {
    position: 'absolute',
    left: 0,
    right: 0,
    top: 0,
    fontFamily: DISPLAY_SERIF,
    fontSize: NAME_SIZE,
    lineHeight: NAME_SIZE * 1.18,
    fontWeight: '700',
    letterSpacing: -1,
  },
  hint: {
    position: 'absolute',
    left: 24,
    right: 24,
    textAlign: 'center',
    fontSize: 12,
    fontWeight: '500',
    letterSpacing: 0.3,
    opacity: 0.6,
  },
});
