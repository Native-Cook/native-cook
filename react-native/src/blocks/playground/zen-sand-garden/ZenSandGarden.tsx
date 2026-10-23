import {
  BlendMode,
  BlurStyle,
  Canvas,
  ClipOp,
  FilterMode,
  MipmapMode,
  Picture,
  Skia,
  type SkCanvas,
  type SkImage,
  type SkPath,
  type SkSurface,
  TileMode,
  VertexMode,
} from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { useEffect, useState } from 'react';
import { type LayoutChangeEvent, StyleSheet, Text, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import { useDerivedValue, useFrameCallback, useReducedMotion, useSharedValue } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';

type Props = {
  /** Rakes a spiral and a field of waves, rings the three stones, then smooths the sand, on a loop. */
  autoPlay?: boolean;
  /** The line of hints along the bottom edge. */
  hint?: string[];
};

// ---------------------------------------------------------------------------------------------
// The height map. The sand's shape lives in an offscreen surface, HEIGHT_SCALE pixels per point,
// as a grey level: BASE is level sand, darker is lower, lighter is higher. Rake strokes, ripples
// and the smoothing sweep are drawn into it a few strips at a time, never redrawn from history.
// The surface is CPU-backed (Skia.Surface.Make), not a GPU one: the Canvas's recorder reads the
// current picture on the JS thread whenever React re-renders the tree (fast refresh, layout), so
// anything the picture holds can be released by the JS thread's garbage collector. A GPU texture
// released there touches the UI thread's GrResourceCache from the wrong thread and asserts; a raster
// image is safe to release on any thread, and Skia uploads it on the thread that draws.
const HEIGHT_SCALE = 2;
const BASE = 0.5;

// The rake: TINES teeth TINE_GAP pt apart. One pass leaves a groove under each tooth, a ridge
// between them, and a berm on either side, RAKE_HALF pt from the middle. The profile is sampled
// at PROFILE_STEPS points across and drawn as a strip of triangles from the last cross-section
// to the new one every STAMP_STEP pt the rake travels.
const TINES = 5;
const TINE_GAP = 9;
const RAKE_HALF = TINE_GAP * 3;
const GROOVE = 0.3;
const PROFILE_STEPS = 48;
const STAMP_STEP = 1.5;
// The rake follows the finger at FOLLOW per second (a fast first-order lag that keeps it off the
// stones), simulated in steps of SUBSTEP s. Its heading turns towards its motion at TURN per s.
const FOLLOW = 32;
const SUBSTEP = 1 / 240;
const TURN = 24;
// The rake keeps this share of its half width clear of a stone's outline, so the grooves bend
// round the stone instead of stopping at it.
const STONE_CLEARANCE = 0.85;
// The wooden rake drawn over the sand: a bar BAR_LENGTH × BAR_THICK pt and a handle trailing
// behind; it stands RAKE_LIFT pt above the sand for its shadow, and fades in or out over FADE s.
const BAR_LENGTH = (TINES - 1) * TINE_GAP + 18;
const BAR_THICK = 8;
const HANDLE_LENGTH = 220;
const HANDLE_THICK = 6;
const RAKE_LIFT = 7;
const FADE = 0.18;

// A tap on a stone sends RINGS grooves out round it, TINE_GAP apart, over RIPPLE_TIME s.
const RINGS = 5;
const RIPPLE_TIME = 1.3;
const RING_STOPS = 72;
// The smoothing sweep: a soft pile of sand PILE pt deep crosses the garden in SWEEP_TIME s and
// leaves faint broom streaks STREAK_PERIOD pt apart behind it.
const PILE = 44;
const SWEEP_TIME = 2.6;
const STREAK_PERIOD = 37;
const STREAKS = [0.0, 0.62, 0.15, 0.9, 0.35, 0.05, 0.7, 0.48, 0.95, 0.22, 0.55, 0.1, 0.8, 0.3];
const STREAK_DEPTH = 0.02;
// A two-finger swipe longer than SMOOTH_SWIPE pt starts a sweep in its direction.
const SMOOTH_SWIPE = 40;
// A touch pauses autoPlay for this long after the finger lifts.
const USER_HOLD = 4;

// Light: a low warm sun whose bearing swings ±SUN_SWING° about SUN_BEARING° and whose height swings
// between SUN_LOW° and SUN_HIGH° once per LOOP, so the time of day drifts and still loops.
const SUN_BEARING = -138;
const SUN_SWING = 26;
const SUN_LOW = 17;
const SUN_HIGH = 25;
const SUN_MORNING = [1.0, 0.9, 0.74];
const SUN_EVENING = [1.0, 0.74, 0.56];
const SKY = [0.52, 0.55, 0.64];

// Three stones, placed as a share of the screen: radius and height in pt, a wobbly outline made of
// three cosines (amplitude, phase), a tint and how much moss grows at its foot.
type Stone = {
  x: number;
  y: number;
  r: number;
  h: number;
  a2: number;
  p2: number;
  a3: number;
  p3: number;
  a5: number;
  p5: number;
  tint: [number, number, number];
  moss: number;
};
const STONES: Stone[] = [
  { x: 0.27, y: 0.42, r: 48, h: 36, a2: 0.12, p2: 0.6, a3: 0.05, p3: 2.1, a5: 0.025, p5: 0.4, tint: [0.4, 0.41, 0.43], moss: 0.85 },
  { x: 0.74, y: 0.6, r: 33, h: 27, a2: 0.09, p2: 2.2, a3: 0.06, p3: 0.9, a5: 0.02, p5: 1.7, tint: [0.56, 0.5, 0.44], moss: 0.4 },
  { x: 0.41, y: 0.79, r: 21, h: 19, a2: 0.14, p2: 1.3, a3: 0.04, p3: 3.0, a5: 0.03, p5: 2.6, tint: [0.31, 0.3, 0.3], moss: 1 },
];

// autoPlay, one LOOP in seconds from smooth sand: a spiral, three rows of waves, the stones rung
// one after another, then the sweep, then rest.
const LOOP = 18;
const SPIRAL_FROM = 0.8;
const SPIRAL_TIME = 4.6;
const SPIRAL_CENTRE = { x: 0.63, y: 0.25 };
const SPIRAL_TURNS = 2.4;
const SPIRAL_INNER = 16;
const SPIRAL_OUTER = 124;
const ROWS_FROM = 5.8;
const ROW_TIME = 1.3;
const ROW_PAUSE = 0.16;
const ROW_TOP = 0.66;
const ROW_GAP = RAKE_HALF * 2;
const ROW_WAVE = 12;
const ROW_WAVELENGTH = 96;
const RING_AT = [10.6, 11.2, 11.8];
const SWEEP_AT = 13.2;

const HINT = ['Drag to rake', 'Tap a stone', 'Two fingers to smooth'];
const INK = '#5a4a3a';

// Lights the height map like sand under a low sun (with ridge shadows), and draws the stones and
// their moss analytically, so the stones' long soft shadows fall across the grooves.
const SAND = Skia.RuntimeEffect.Make(`
uniform shader heightMap;
uniform float2 size;
uniform float scale;
uniform float3 sun;
uniform float3 sunColor;
uniform float3 ambient;
uniform float4 aPos;
uniform float4 aShape;
uniform float2 aShape2;
uniform float4 aTint;
uniform float4 bPos;
uniform float4 bShape;
uniform float2 bShape2;
uniform float4 bTint;
uniform float4 cPos;
uniform float4 cShape;
uniform float2 cShape2;
uniform float4 cTint;
uniform float time;

float hash(float2 p) {
  p = fract(p * float2(123.34, 456.21));
  p += dot(p, p + 45.32);
  return fract(p.x * p.y);
}

float noise(float2 p) {
  float2 i = floor(p);
  float2 f = fract(p);
  float2 u = f * f * (3.0 - 2.0 * f);
  return mix(mix(hash(i), hash(i + float2(1.0, 0.0)), u.x),
             mix(hash(i + float2(0.0, 1.0)), hash(i + float2(1.0, 1.0)), u.x), u.y);
}

float fbm(float2 p) {
  float v = 0.0;
  float a = 0.5;
  for (int i = 0; i < 3; i++) {
    v += a * noise(p);
    p = p * 2.07 + 13.1;
    a *= 0.5;
  }
  return v;
}

float sandH(float2 p) {
  return heightMap.eval(p * scale).r;
}

// Distance from a stone's centre as a share of its outline at that bearing (1 on the outline).
float stoneD(float2 p, float4 pos, float4 shp, float2 shp2) {
  float2 q = p - pos.xy;
  float l = length(q);
  if (l > pos.z * 1.7) return 9.0;
  float th = atan(q.y, q.x);
  float r = pos.z * (1.0 + shp.x * cos(2.0 * th + shp.y) + shp.z * cos(3.0 * th + shp.w) + shp2.x * cos(5.0 * th + shp2.y));
  return l / r;
}

float domeH(float d, float h) {
  return h * pow(clamp(1.0 - d * d, 0.0, 1.0), 0.45);
}

float stonesH(float2 p) {
  float a = domeH(stoneD(p, aPos, aShape, aShape2), aPos.w);
  float b = domeH(stoneD(p, bPos, bShape, bShape2), bPos.w);
  float c = domeH(stoneD(p, cPos, cShape, cShape2), cPos.w);
  return max(a, max(b, c));
}

float3 stoneColor(float2 p, float d, float4 pos, float4 shp, float2 shp2, float3 tint) {
  float e = 0.8;
  float hx0 = domeH(stoneD(p - float2(e, 0.0), pos, shp, shp2), pos.w);
  float hx1 = domeH(stoneD(p + float2(e, 0.0), pos, shp, shp2), pos.w);
  float hy0 = domeH(stoneD(p - float2(0.0, e), pos, shp, shp2), pos.w);
  float hy1 = domeH(stoneD(p + float2(0.0, e), pos, shp, shp2), pos.w);
  float3 n = normalize(float3((hx0 - hx1) / (2.0 * e), (hy0 - hy1) / (2.0 * e), 1.0));
  float grain = fbm(p * 0.08 + pos.xy * 0.013);
  float vein = smoothstep(0.46, 0.5, fbm(p * float2(0.05, 0.16) + pos.yx * 0.02)) * 0.1;
  float3 alb = tint * (0.8 + 0.4 * grain) * (1.0 - vein);
  float speck = hash(floor(p * 1.6) + pos.xy);
  alb *= 1.0 - 0.35 * step(0.92, speck);
  alb = mix(alb, min(alb * 1.55, float3(1.0)), step(0.975, hash(floor(p * 1.6) + pos.yx)));
  float wrapL = clamp((dot(n, sun) + 0.25) / 1.25, 0.0, 1.0);
  float3 hv = normalize(sun + float3(0.0, 0.0, 1.0));
  float spec = pow(max(dot(n, hv), 0.0), 30.0) * 0.2;
  float ao = 0.5 + 0.5 * smoothstep(1.0, 0.72, d);
  return alb * (ambient * (0.45 + 0.55 * n.z) * ao + sunColor * wrapL * 1.05) + sunColor * spec;
}

// Moss at a stone's foot: an irregular fringe a little up the stone and out over the sand, in
// patches, with a fuzzy edge.
float mossMask(float2 p, float d, float amount, float seed) {
  if (amount <= 0.0 || d > 1.7) return 0.0;
  float reach = 1.0 + amount * (0.02 + 0.24 * fbm(p * 0.06 + seed));
  float patches = smoothstep(0.3, 0.52, fbm(p * 0.05 + seed * 1.7));
  float foot = smoothstep(0.82, 0.94, d);
  float fuzz = noise(p * 0.9 + seed) - 0.5;
  return clamp((reach - d) * 14.0 + fuzz * 1.8, 0.0, 1.0) * patches * foot;
}

float3 mossColor(float2 p, float shade, float m) {
  float c = fbm(p * 0.45);
  float3 alb = mix(float3(0.11, 0.15, 0.07), float3(0.34, 0.38, 0.15), c);
  // Drier, browner tips where the moss thins out.
  alb = mix(float3(0.36, 0.33, 0.2), alb, smoothstep(0.0, 0.7, m));
  float b0 = noise(p * 1.4);
  float bx = noise(p * 1.4 + float2(0.6, 0.0)) - b0;
  float by = noise(p * 1.4 + float2(0.0, 0.6)) - b0;
  float3 n = normalize(float3(-bx * 2.0, -by * 2.0, 1.0));
  float lit = max(dot(n, sun), 0.0);
  return alb * (ambient * 0.75 + sunColor * lit * (0.25 + 0.75 * shade) * 1.5);
}

half4 main(float2 p) {
  float dA = stoneD(p, aPos, aShape, aShape2);
  float dB = stoneD(p, bPos, bShape, bShape2);
  float dC = stoneD(p, cPos, cShape, cShape2);
  float dMin = min(dA, min(dB, dC));
  float2 toSun = normalize(sun.xy);
  float tanE = sun.z / max(length(sun.xy), 0.001);

  float stoneShadow = 1.0;
  float3 col = float3(0.0);
  if (dMin > 0.96) {
    // Sand: the normal from the height map, roughened by its grain.
    float D = 5.0;
    float h = sandH(p);
    float hl = sandH(p - float2(1.0, 0.0));
    float hr = sandH(p + float2(1.0, 0.0));
    float hu = sandH(p - float2(0.0, 1.0));
    float hd = sandH(p + float2(0.0, 1.0));
    float3 n = normalize(float3((hl - hr) * D * 0.5, (hu - hd) * D * 0.5, 1.0));
    float g = hash(floor(p * 2.0));
    float g2 = hash(floor(p * 2.0) + 17.0);
    n = normalize(n + float3(g - 0.5, g2 - 0.5, 0.0) * 0.14);
    float lit = max(dot(n, sun), 0.0);
    // Ridges shade the grooves behind them.
    float occl = 0.0;
    for (int i = 1; i <= 5; i++) {
      float s = float(i) * 1.3;
      occl = max(occl, (sandH(p + toSun * s) - h) * D - s * tanE);
    }
    float sandShadow = 1.0 - smoothstep(0.0, 0.8, occl);
    // The stones' long shadows, softer the further they fall.
    for (int i = 1; i <= 12; i++) {
      float s = float(i) * 8.0;
      float pen = 1.5 + s * 0.14;
      stoneShadow = min(stoneShadow, smoothstep(-pen, pen, s * tanE - stonesH(p + toSun * s)));
    }
    float contact = 1.0 - 0.4 * (1.0 - smoothstep(1.0, 1.3, dMin));
    float3 alb = float3(0.9, 0.82, 0.68) * (0.94 + 0.12 * fbm(p * 0.011));
    alb *= 0.93 + 0.07 * g;
    alb *= 0.84 + 0.16 * smoothstep(0.12, 0.55, h);
    float shade = min(sandShadow, stoneShadow);
    col = alb * (ambient * (0.5 + 0.5 * n.z) * contact + sunColor * lit * shade * 1.15 * contact);
    float twinkle = 0.5 + 0.5 * sin(time * 2.3 + g2 * 40.0);
    col += sunColor * step(0.994, g) * lit * shade * 0.6 * twinkle;
  }
  // Each stone over the sand, with a one-point soft edge.
  if (dA < 1.0) col = mix(col, stoneColor(p, dA, aPos, aShape, aShape2, aTint.rgb), clamp((1.0 - dA) * aPos.z / 0.7, 0.0, 1.0));
  if (dB < 1.0) col = mix(col, stoneColor(p, dB, bPos, bShape, bShape2, bTint.rgb), clamp((1.0 - dB) * bPos.z / 0.7, 0.0, 1.0));
  if (dC < 1.0) col = mix(col, stoneColor(p, dC, cPos, cShape, cShape2, cTint.rgb), clamp((1.0 - dC) * cPos.z / 0.7, 0.0, 1.0));
  float moss = max(mossMask(p, dA, aTint.a, 3.1), max(mossMask(p, dB, bTint.a, 7.7), mossMask(p, dC, cTint.a, 12.4)));
  if (moss > 0.0) col = mix(col, mossColor(p, dMin < 1.0 ? 1.0 : stoneShadow, moss), moss * 0.94);

  // Soft highlight roll-off, then a warm vignette.
  col = (1.0 - exp(-col * 1.7)) * 1.1;
  float2 v = (p / size - 0.5) * float2(1.15, 0.85);
  col *= 1.0 - 0.22 * smoothstep(0.3, 0.75, length(v));
  return half4(half3(col), 1.0);
}
`)!;

/** The rake's cross-section: grooves under the teeth, ridges between, berms at the edges. */
function profileAt(u: number) {
  const a = Math.abs(u);
  const edge = TINE_GAP * 2.5;
  if (a <= edge) return BASE - GROOVE * Math.cos((2 * Math.PI * u) / TINE_GAP);
  const t = Math.min(1, (a - edge) / (RAKE_HALF - edge));
  const s = t * t * (3 - 2 * t);
  return BASE + GROOVE * (1 - s);
}
const PROFILE = Array.from({ length: PROFILE_STEPS + 1 }, (_, i) => profileAt(-RAKE_HALF + (2 * RAKE_HALF * i) / PROFILE_STEPS));
// Two triangles per step between the previous cross-section (first half) and the new one.
const STRIP: number[] = [];
for (let i = 0; i < PROFILE_STEPS; i += 1) {
  const n = PROFILE_STEPS + 1;
  STRIP.push(i, i + 1, n + i, i + 1, n + i + 1, n + i);
}

function clamp01(v: number) {
  'worklet';
  return v < 0 ? 0 : v > 1 ? 1 : v;
}

function easeInOut(t: number) {
  'worklet';
  return t < 0.5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2;
}

function easeOut(t: number) {
  'worklet';
  return 1 - Math.pow(1 - t, 3);
}

function grey(v: number) {
  'worklet';
  return Float32Array.of(v, v, v, 1);
}

/** A stone's centre in points. */
function stoneX(s: Stone, w: number) {
  'worklet';
  return s.x * w;
}

function stoneY(s: Stone, h: number) {
  'worklet';
  return s.y * h;
}

/** The stone's outline radius at a bearing (the same three cosines the shader uses). */
function stoneRadius(s: Stone, th: number) {
  'worklet';
  return s.r * (1 + s.a2 * Math.cos(2 * th + s.p2) + s.a3 * Math.cos(3 * th + s.p3) + s.a5 * Math.cos(5 * th + s.p5));
}

/** Distance from the stone's centre as a share of its outline at that bearing. */
function stoneDistance(s: Stone, px: number, py: number, w: number, h: number) {
  'worklet';
  const qx = px - stoneX(s, w);
  const qy = py - stoneY(s, h);
  return Math.hypot(qx, qy) / stoneRadius(s, Math.atan2(qy, qx));
}

/** The outer edge of the stone's outline, for where its ring grooves start. */
function stoneReach(s: Stone) {
  'worklet';
  return s.r * (1 + s.a2 + s.a3 + s.a5);
}

/** All three outlines, slightly grown, as one path: sand is never drawn under a stone. */
function stonesPath(w: number, h: number): SkPath {
  'worklet';
  const b = Skia.PathBuilder.Make();
  for (let k = 0; k < STONES.length; k += 1) {
    const s = STONES[k];
    for (let i = 0; i <= 72; i += 1) {
      const th = (i / 72) * Math.PI * 2;
      const r = stoneRadius(s, th) + 0.5;
      const x = stoneX(s, w) + Math.cos(th) * r;
      const y = stoneY(s, h) + Math.sin(th) * r;
      if (i === 0) b.moveTo(x, y);
      else b.lineTo(x, y);
    }
    b.close();
  }
  return b.build();
}

/** Repeating faint stripes, like a broom's, running in the sweep's direction. */
function streakShader(across: boolean) {
  'worklet';
  const colors: Float32Array[] = [];
  const stops: number[] = [];
  for (let i = 0; i <= STREAKS.length; i += 1) {
    const v = STREAKS[i % STREAKS.length];
    colors.push(grey(BASE + (v - 0.5) * STREAK_DEPTH));
    stops.push(i / STREAKS.length);
  }
  const end = across ? { x: STREAK_PERIOD, y: 0 } : { x: 0, y: STREAK_PERIOD };
  return Skia.Shader.MakeLinearGradient({ x: 0, y: 0 }, end, colors, stops, TileMode.Repeat);
}

/** Rake cross-section at a point, across the heading's normal: x, y pairs. */
function crossSection(hx: number, hy: number, nx: number, ny: number) {
  'worklet';
  const out: number[] = [];
  for (let i = 0; i <= PROFILE_STEPS; i += 1) {
    const u = -RAKE_HALF + (2 * RAKE_HALF * i) / PROFILE_STEPS;
    out.push(hx + nx * u, hy + ny * u);
  }
  return out;
}

/** Fills the strip between two cross-sections with the rake's profile. */
function stampStrip(canvas: SkCanvas, from: number[], to: number[]) {
  'worklet';
  const points: { x: number; y: number }[] = [];
  const colors: Float32Array[] = [];
  for (let k = 0; k < 2; k += 1) {
    const src = k === 0 ? from : to;
    for (let i = 0; i <= PROFILE_STEPS; i += 1) {
      points.push({ x: src[i * 2], y: src[i * 2 + 1] });
      colors.push(grey(PROFILE[i]));
    }
  }
  const vertices = Skia.MakeVertices(VertexMode.Triangles, points, null, colors, STRIP, true);
  const paint = Skia.Paint();
  paint.setAntiAlias(true);
  canvas.drawVertices(vertices, BlendMode.Dst, paint);
}

/** The rings round a stone, out to `front` pt from its centre, as a radial gradient. */
function drawRipple(canvas: SkCanvas, s: Stone, w: number, h: number, front: number) {
  'worklet';
  const inner = stoneReach(s) + 3;
  if (front <= inner) return;
  const colors: Float32Array[] = [];
  const stops: number[] = [];
  for (let i = 0; i <= RING_STOPS; i += 1) {
    const r = (i / RING_STOPS) * front;
    let v = BASE + GROOVE * 0.25;
    if (r >= inner) {
      // Rings start on a ridge, so the sand rises gently from the stone's collar.
      v = BASE + GROOVE * Math.cos((2 * Math.PI * (r - inner)) / TINE_GAP);
      // The leading edge is a berm of pushed sand.
      const lead = clamp01((r - (front - TINE_GAP * 0.6)) / (TINE_GAP * 0.6));
      v = v + (BASE + GROOVE * 1.15 - v) * lead;
    }
    colors.push(grey(v));
    stops.push(i / RING_STOPS);
  }
  const cx = stoneX(s, w);
  const cy = stoneY(s, h);
  const paint = Skia.Paint();
  paint.setAntiAlias(true);
  paint.setShader(Skia.Shader.MakeRadialGradient({ x: cx, y: cy }, front, colors, stops, TileMode.Clamp));
  canvas.drawCircle(cx, cy, front, paint);
}

/**
 * One frame of the sweep: level, streaked sand behind the pile, the pile itself at `front` pt along
 * the sweep's direction (1 down, 2 up, 3 right, 4 left).
 */
function drawSweep(canvas: SkCanvas, dir: number, w: number, h: number, front: number) {
  'worklet';
  const vertical = dir <= 2;
  const extent = vertical ? h : w;
  const back = front - PILE;
  const flat = Skia.Paint();
  flat.setShader(streakShader(vertical));
  const pile = Skia.Paint();
  // Positions along the sweep's axis, measured from the edge it starts at.
  const forward = dir === 1 || dir === 3;
  const a = forward ? back : extent - back;
  const b = forward ? front : extent - front;
  // A low slope of gathered sand that steepens into a crest just behind the front.
  const pileColors = [grey(BASE), grey(BASE + GROOVE * 0.35), grey(BASE + GROOVE * 1.1), grey(0.98), grey(BASE + GROOVE * 0.4)];
  const pileStops = [0, 0.45, 0.76, 0.9, 1];
  if (vertical) {
    const top = Math.min(a, b);
    if (dir === 1) canvas.drawRect(Skia.XYWHRect(0, -2, w, Math.max(0, a + 2)), flat);
    else canvas.drawRect(Skia.XYWHRect(0, a, w, Math.max(0, h + 2 - a)), flat);
    pile.setShader(Skia.Shader.MakeLinearGradient({ x: 0, y: a }, { x: 0, y: b }, pileColors, pileStops, TileMode.Clamp));
    canvas.drawRect(Skia.XYWHRect(0, top, w, PILE), pile);
  } else {
    const left = Math.min(a, b);
    if (dir === 3) canvas.drawRect(Skia.XYWHRect(-2, 0, Math.max(0, a + 2), h), flat);
    else canvas.drawRect(Skia.XYWHRect(a, 0, Math.max(0, w + 2 - a), h), flat);
    pile.setShader(Skia.Shader.MakeLinearGradient({ x: a, y: 0 }, { x: b, y: 0 }, pileColors, pileStops, TileMode.Clamp));
    canvas.drawRect(Skia.XYWHRect(left, 0, PILE, h), pile);
  }
}

/** The wooden rake at (x, y) heading `angle`°, or its blurred shadow. */
function drawRake(canvas: SkCanvas, x: number, y: number, angle: number, alpha: number, shadow: boolean) {
  'worklet';
  canvas.save();
  canvas.translate(x, y);
  canvas.rotate(angle, 0, 0);
  const handle = Skia.Paint();
  const bar = Skia.Paint();
  handle.setAntiAlias(true);
  bar.setAntiAlias(true);
  if (shadow) {
    handle.setColor(Skia.Color(`rgba(40, 26, 12, ${0.3 * alpha})`));
    handle.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 3.5, true));
    bar.setColor(Skia.Color(`rgba(40, 26, 12, ${0.34 * alpha})`));
    bar.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 3, true));
  } else {
    handle.setShader(
      Skia.Shader.MakeLinearGradient(
        { x: 0, y: -HANDLE_THICK / 2 },
        { x: 0, y: HANDLE_THICK / 2 },
        [Skia.Color(`rgba(196, 160, 112, ${alpha})`), Skia.Color(`rgba(150, 108, 66, ${alpha})`), Skia.Color(`rgba(104, 70, 40, ${alpha})`)],
        [0, 0.55, 1],
        TileMode.Clamp,
      ),
    );
    bar.setShader(
      Skia.Shader.MakeLinearGradient(
        { x: -BAR_THICK / 2, y: 0 },
        { x: BAR_THICK / 2, y: 0 },
        [Skia.Color(`rgba(112, 76, 44, ${alpha})`), Skia.Color(`rgba(206, 168, 118, ${alpha})`), Skia.Color(`rgba(150, 106, 62, ${alpha})`), Skia.Color(`rgba(92, 60, 34, ${alpha})`)],
        [0, 0.3, 0.7, 1],
        TileMode.Clamp,
      ),
    );
  }
  // The handle trails behind the head; the bar lies across the heading.
  canvas.drawRRect(Skia.RRectXY(Skia.XYWHRect(-HANDLE_LENGTH, -HANDLE_THICK / 2, HANDLE_LENGTH - 2, HANDLE_THICK), 3, 3), handle);
  canvas.drawRRect(Skia.RRectXY(Skia.XYWHRect(-BAR_THICK / 2, -BAR_LENGTH / 2, BAR_THICK, BAR_LENGTH), 3, 3), bar);
  if (!shadow) {
    const peg = Skia.Paint();
    peg.setAntiAlias(true);
    peg.setColor(Skia.Color(`rgba(70, 46, 26, ${0.8 * alpha})`));
    canvas.drawCircle(0, 0, 1.6, peg);
  }
  canvas.restore();
}

/** Where autoPlay's finger is at `t` s into the loop, and whether it is down. */
function scriptFinger(t: number, w: number, h: number) {
  'worklet';
  const spiral = (t - SPIRAL_FROM) / SPIRAL_TIME;
  if (spiral >= 0 && spiral <= 1) {
    const e = easeInOut(spiral) * 0.85 + spiral * 0.15;
    const r = SPIRAL_INNER + (SPIRAL_OUTER - SPIRAL_INNER) * e;
    const a = -Math.PI / 2 + e * SPIRAL_TURNS * Math.PI * 2;
    return { down: true, x: SPIRAL_CENTRE.x * w + Math.cos(a) * r, y: SPIRAL_CENTRE.y * h + Math.sin(a) * r };
  }
  for (let row = 0; row < 3; row += 1) {
    const start = ROWS_FROM + row * (ROW_TIME + ROW_PAUSE);
    const p = (t - start) / ROW_TIME;
    if (p >= 0 && p <= 1) {
      const e = p * 0.7 + easeInOut(p) * 0.3;
      const forward = row % 2 === 0;
      const x = forward ? -50 + (w + 100) * e : w + 50 - (w + 100) * e;
      const y = ROW_TOP * h + row * ROW_GAP + ROW_WAVE * Math.sin((2 * Math.PI * x) / ROW_WAVELENGTH);
      return { down: true, x, y };
    }
  }
  return { down: false, x: 0, y: 0 };
}

export function ZenSandGarden({ autoPlay = false, hint = HINT }: Props) {
  const insets = useSafeAreaInsets();
  const reducedMotion = useReducedMotion();
  const auto = autoPlay && !reducedMotion;
  const [size, setSize] = useState({ width: 0, height: 0 });
  const { width, height } = size;

  const stage = useSharedValue({ width: 0, height: 0 });
  const surface = useSharedValue<SkSurface | null>(null);
  // The stage size the surface was made for, so a new layout makes a new one.
  const surfaceFor = useSharedValue({ width: 0, height: 0 });
  const clip = useSharedValue<SkPath | null>(null);
  const heightMap = useSharedValue<SkImage | null>(null);
  const elapsed = useSharedValue(0);
  const tick = useSharedValue(0);

  // The rake: the finger (target), the head that follows it, its heading and the last section.
  const fingerDown = useSharedValue(false);
  const targetX = useSharedValue(0);
  const targetY = useSharedValue(0);
  const headX = useSharedValue(0);
  const headY = useSharedValue(0);
  const dirX = useSharedValue(1);
  const dirY = useSharedValue(0);
  const rakeAlpha = useSharedValue(0);
  const lastSection = useSharedValue<number[]>([]);
  const lastStampX = useSharedValue(0);
  const lastStampY = useSharedValue(0);
  const newStroke = useSharedValue(true);

  // Ripples (start time per stone, < 0 when idle) and the sweep (direction 0 when idle).
  const ripples = useSharedValue<number[]>([-1, -1, -1]);
  const sweepDir = useSharedValue(0);
  const sweepFrom = useSharedValue(0);

  // autoPlay's clock, and how long a touch still holds it.
  const clock = useSharedValue(0);
  const hold = useSharedValue(0);
  const autoFinger = useSharedValue(false);

  const onLayout = (e: LayoutChangeEvent) => {
    const { width: w, height: h } = e.nativeEvent.layout;
    setSize({ width: w, height: h });
    stage.set({ width: w, height: h });
  };

  const tapped = () => {
    if (process.env.EXPO_OS !== 'web') Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Medium).catch(() => {});
  };
  const grabbed = () => {
    if (process.env.EXPO_OS !== 'web') Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
  };
  const smoothed = () => {
    if (process.env.EXPO_OS !== 'web') Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success).catch(() => {});
  };

  /** Puts the head outside every stone's clearance ring, sliding it round the outline. */
  const keepOffStones = (w: number, h: number) => {
    'worklet';
    let x = headX.get();
    let y = headY.get();
    for (let k = 0; k < STONES.length; k += 1) {
      const s = STONES[k];
      const qx = x - stoneX(s, w);
      const qy = y - stoneY(s, h);
      const l = Math.hypot(qx, qy);
      const keep = stoneRadius(s, Math.atan2(qy, qx)) + RAKE_HALF * STONE_CLEARANCE;
      if (l < keep) {
        const k2 = l < 0.001 ? 0 : keep / l;
        x = l < 0.001 ? stoneX(s, w) + keep : stoneX(s, w) + qx * k2;
        y = l < 0.001 ? stoneY(s, h) : stoneY(s, h) + qy * k2;
      }
    }
    headX.set(x);
    headY.set(y);
  };

  const press = (x: number, y: number) => {
    'worklet';
    const { width: w, height: h } = stage.get();
    fingerDown.set(true);
    targetX.set(x);
    targetY.set(y);
    headX.set(x);
    headY.set(y);
    keepOffStones(w, h);
    newStroke.set(true);
    lastStampX.set(headX.get());
    lastStampY.set(headY.get());
  };

  const ring = (index: number) => {
    'worklet';
    const next = ripples.get().slice();
    next[index] = elapsed.get();
    ripples.set(next);
  };

  const sweep = (dir: number) => {
    'worklet';
    sweepDir.set(dir);
    sweepFrom.set(elapsed.get());
  };

  /** Lets go of the height map's surface, image and clip (on the UI thread, which made them). */
  const releaseSand = () => {
    'worklet';
    const oldSurface = surface.get();
    const oldMap = heightMap.get();
    const oldClip = clip.get();
    surface.set(null);
    heightMap.set(null);
    clip.set(null);
    surfaceFor.set({ width: 0, height: 0 });
    if (oldMap) oldMap.dispose();
    if (oldSurface) oldSurface.dispose();
    if (oldClip) oldClip.dispose();
  };

  useFrameCallback((frame) => {
    const dt = Math.min(frame.timeSincePreviousFrame ?? 16, 50) / 1000;
    if (dt <= 0) return;
    const { width: w, height: h } = stage.get();
    if (w === 0) return;
    let target = surface.get();
    let dirty = false;
    const made = surfaceFor.get();
    if (target && (made.width !== w || made.height !== h)) {
      // The garden was laid out again at another size: start over with smooth sand.
      releaseSand();
      target = null;
    }
    if (!target) {
      target = Skia.Surface.Make(Math.ceil(w * HEIGHT_SCALE), Math.ceil(h * HEIGHT_SCALE));
      if (!target) return;
      surface.set(target);
      surfaceFor.set({ width: w, height: h });
      clip.set(stonesPath(w, h));
      lastSection.set([]);
      newStroke.set(true);
      const c = target.getCanvas();
      c.save();
      c.scale(HEIGHT_SCALE, HEIGHT_SCALE);
      const flat = Skia.Paint();
      flat.setShader(streakShader(true));
      c.drawRect(Skia.XYWHRect(-2, -2, w + 4, h + 4), flat);
      c.restore();
      dirty = true;
    }
    const now = elapsed.get() + dt;
    elapsed.set(now);

    // autoPlay drives the same finger, ripples and sweep a touch would.
    if (hold.get() > 0) {
      if (!fingerDown.get() || autoFinger.get()) hold.set(hold.get() - dt);
      if (hold.get() <= 0) clock.set(0);
    } else if (auto) {
      const before = clock.get();
      const t = (before + dt) % LOOP;
      clock.set(t);
      const passed = (at: number) => (before < at && t >= at) || (t < before && (at > before || at <= t));
      const f = scriptFinger(t, w, h);
      if (f.down && !autoFinger.get()) {
        autoFinger.set(true);
        press(f.x, f.y);
      } else if (f.down) {
        targetX.set(f.x);
        targetY.set(f.y);
      } else if (autoFinger.get()) {
        autoFinger.set(false);
        fingerDown.set(false);
      }
      for (let k = 0; k < RING_AT.length; k += 1) if (passed(RING_AT[k])) ring(k);
      if (passed(SWEEP_AT)) sweep(1);
    }

    const canvas = target.getCanvas();
    canvas.save();
    canvas.scale(HEIGHT_SCALE, HEIGHT_SCALE);
    const stones = clip.get();
    if (stones) canvas.clipPath(stones, ClipOp.Difference, true);

    // The head chases the finger in small steps, never entering a stone.
    const down = fingerDown.get();
    if (down) {
      const steps = Math.max(1, Math.ceil(dt / SUBSTEP));
      const k = Math.min(1, FOLLOW * (dt / steps));
      for (let i = 0; i < steps; i += 1) {
        headX.set(headX.get() + (targetX.get() - headX.get()) * k);
        headY.set(headY.get() + (targetY.get() - headY.get()) * k);
        keepOffStones(w, h);
      }
      const mx = headX.get() - lastStampX.get();
      const my = headY.get() - lastStampY.get();
      const moved = Math.hypot(mx, my);
      if (moved >= STAMP_STEP) {
        const ux = mx / moved;
        const uy = my / moved;
        let dx = dirX.get();
        let dy = dirY.get();
        const turned = newStroke.get() || dx * ux + dy * uy < -0.2;
        if (turned) {
          dx = ux;
          dy = uy;
        } else {
          const turn = Math.min(1, TURN * dt);
          dx += (ux - dx) * turn;
          dy += (uy - dy) * turn;
          const l = Math.hypot(dx, dy) || 1;
          dx /= l;
          dy /= l;
        }
        dirX.set(dx);
        dirY.set(dy);
        const section = crossSection(headX.get(), headY.get(), -dy, dx);
        if (!turned && lastSection.get().length > 0) {
          stampStrip(canvas, lastSection.get(), section);
          dirty = true;
        }
        lastSection.set(section);
        lastStampX.set(headX.get());
        lastStampY.set(headY.get());
        newStroke.set(false);
      }
    }
    const fade = Math.min(1, dt / FADE);
    rakeAlpha.set(rakeAlpha.get() + ((down ? 1 : 0) - rakeAlpha.get()) * fade);

    // Rings travelling out from struck stones.
    const live = ripples.get();
    for (let k = 0; k < STONES.length; k += 1) {
      if (live[k] < 0) continue;
      const p = clamp01((now - live[k]) / (reducedMotion ? 0.01 : RIPPLE_TIME));
      const s = STONES[k];
      const inner = stoneReach(s) + 3;
      drawRipple(canvas, s, w, h, inner + (RINGS * TINE_GAP + TINE_GAP * 0.6) * easeOut(p));
      dirty = true;
      if (p >= 1) {
        const next = live.slice();
        next[k] = -1;
        ripples.set(next);
      }
    }

    // The sweep: across the whole garden, then done.
    const dir = sweepDir.get();
    if (dir !== 0) {
      const p = clamp01((now - sweepFrom.get()) / (reducedMotion ? 0.01 : SWEEP_TIME));
      const extent = dir <= 2 ? h : w;
      drawSweep(canvas, dir, w, h, -PILE + (extent + PILE * 2 + 4) * easeInOut(p));
      dirty = true;
      if (p >= 1) sweepDir.set(0);
    }
    canvas.restore();

    if (dirty) {
      target.flush();
      const before = heightMap.get();
      heightMap.set(target.makeImageSnapshot());
      if (before) before.dispose();
    }
    tick.set(tick.get() + 1);
  });

  // Unmount (and a fast-refresh remount): free the height map with the component, not whenever a
  // garbage collector gets to it. If the frame callback runs again (StrictMode re-runs effects) it
  // simply makes a fresh surface.
  useEffect(() => {
    return () => {
      scheduleOnUI(releaseSand);
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const touched = () => {
    'worklet';
    hold.set(USER_HOLD);
    if (autoFinger.get()) {
      autoFinger.set(false);
      fingerDown.set(false);
    }
  };

  const rake = Gesture.Pan()
    .maxPointers(1)
    .minDistance(4)
    .onStart((e) => {
      touched();
      press(e.x - e.translationX, e.y - e.translationY);
      targetX.set(e.x);
      targetY.set(e.y);
      scheduleOnRN(grabbed);
    })
    .onUpdate((e) => {
      targetX.set(e.x);
      targetY.set(e.y);
    })
    .onFinalize(() => {
      fingerDown.set(false);
    });

  const tap = Gesture.Tap()
    .maxDistance(10)
    .onEnd((e, success) => {
      if (!success) return;
      const { width: w, height: h } = stage.get();
      for (let k = 0; k < STONES.length; k += 1) {
        if (stoneDistance(STONES[k], e.x, e.y, w, h) < 1.2) {
          touched();
          ring(k);
          scheduleOnRN(tapped);
          return;
        }
      }
    });

  const swept = useSharedValue(false);
  const smooth = Gesture.Pan()
    .minPointers(2)
    .minDistance(10)
    .onStart(() => {
      touched();
      swept.set(false);
    })
    .onUpdate((e) => {
      if (swept.get()) return;
      const tx = e.translationX;
      const ty = e.translationY;
      if (Math.hypot(tx, ty) < SMOOTH_SWIPE) return;
      swept.set(true);
      sweep(Math.abs(ty) >= Math.abs(tx) ? (ty > 0 ? 1 : 2) : tx > 0 ? 3 : 4);
      scheduleOnRN(smoothed);
    });

  const picture = useDerivedValue(() => {
    tick.get();
    const recorder = Skia.PictureRecorder();
    const canvas = recorder.beginRecording(Skia.XYWHRect(0, 0, width, height));
    const map = heightMap.get();
    if (!map || width === 0) return recorder.finishRecordingAsPicture();

    // The light of the moment.
    const day = Math.sin((elapsed.get() / LOOP) * Math.PI * 2);
    const bearing = ((SUN_BEARING + SUN_SWING * day) * Math.PI) / 180;
    const rise = ((SUN_LOW + (SUN_HIGH - SUN_LOW) * (0.5 + 0.5 * Math.cos((elapsed.get() / LOOP) * Math.PI * 2))) * Math.PI) / 180;
    const sx = Math.cos(rise) * Math.cos(bearing);
    const sy = Math.cos(rise) * Math.sin(bearing);
    const sz = Math.sin(rise);
    const warm = 0.5 + 0.5 * day;
    const uniforms: number[] = [width, height, HEIGHT_SCALE, sx, sy, sz];
    for (let i = 0; i < 3; i += 1) uniforms.push(SUN_MORNING[i] + (SUN_EVENING[i] - SUN_MORNING[i]) * warm);
    for (let i = 0; i < 3; i += 1) uniforms.push(SKY[i]);
    for (let k = 0; k < STONES.length; k += 1) {
      const s = STONES[k];
      uniforms.push(stoneX(s, width), stoneY(s, height), s.r, s.h, s.a2, s.p2, s.a3, s.p3, s.a5, s.p5);
      uniforms.push(s.tint[0], s.tint[1], s.tint[2], s.moss);
    }
    uniforms.push(elapsed.get());
    const child = map.makeShaderOptions(TileMode.Clamp, TileMode.Clamp, FilterMode.Linear, MipmapMode.None);
    const paint = Skia.Paint();
    paint.setShader(SAND.makeShaderWithChildren(uniforms, [child]));
    canvas.drawRect(Skia.XYWHRect(0, 0, width, height), paint);

    // The rake, standing a little above the sand, its shadow cast away from the sun.
    const alpha = rakeAlpha.get();
    if (alpha > 0.01) {
      const angle = (Math.atan2(dirY.get(), dirX.get()) * 180) / Math.PI;
      const reach = RAKE_LIFT / Math.max(0.2, Math.tan(rise));
      const ox = (sx / Math.cos(rise)) * reach;
      const oy = (sy / Math.cos(rise)) * reach;
      drawRake(canvas, headX.get() - ox, headY.get() - oy, angle, alpha, true);
      drawRake(canvas, headX.get(), headY.get(), angle, alpha, false);
    }
    return recorder.finishRecordingAsPicture();
  });

  return (
    <View style={styles.root} onLayout={onLayout}>
      <GestureDetector gesture={Gesture.Race(smooth, rake, tap)}>
        <View style={StyleSheet.absoluteFill}>
          <Canvas style={StyleSheet.absoluteFill}>
            <Picture picture={picture} />
          </Canvas>
        </View>
      </GestureDetector>
      <View pointerEvents="none" style={[styles.hints, { bottom: insets.bottom + 14 }]}>
        {hint.map((line, i) => (
          <View key={line} style={styles.hintItem}>
            {i > 0 ? <View style={styles.dot} /> : null}
            <Text style={styles.hint} numberOfLines={1}>
              {line}
            </Text>
          </View>
        ))}
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: '#d9cdb8' },
  hints: {
    position: 'absolute',
    left: 16,
    right: 16,
    flexDirection: 'row',
    justifyContent: 'center',
    alignItems: 'center',
    gap: 10,
  },
  hintItem: { flexDirection: 'row', alignItems: 'center', gap: 10 },
  dot: { width: 3, height: 3, borderRadius: 1.5, backgroundColor: INK, opacity: 0.35 },
  hint: { color: INK, opacity: 0.62, fontSize: 12, letterSpacing: 0.2, fontWeight: '500' },
});
