import {
  BlendMode,
  BlurStyle,
  Canvas,
  ClipOp,
  PaintStyle,
  Picture,
  Skia,
  type SkCanvas,
  type SkPaint,
  type SkPath,
  TileMode,
  createPicture,
  vec,
} from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { type ReactNode, useState } from 'react';
import { type LayoutChangeEvent, StyleSheet, Text, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  type SharedValue,
  useAnimatedStyle,
  useDerivedValue,
  useFrameCallback,
  useReducedMotion,
  useSharedValue,
} from 'react-native-reanimated';
import { scheduleOnRN } from 'react-native-worklets';

type Props = {
  /** Plays the five morphs by themselves every LOOP seconds. */
  autoPlay?: boolean;
};

/** autoPlay: the five morphs play by themselves every LOOP seconds. */
export const LOOP = 8;

// Layout, in points from the emblem's centre (which sits 40 pt above the screen's middle).
const CENTER_SHIFT = -40;
const RADIUS = 104;
const EYEBROW_Y = -186;
const NAME_Y = 184;
const LINE_Y = 218;
const DOTS_Y = 258;
const HINT_Y = 296;
const DRAG_PER_STATE = 120;
const RETURN_FOR = 0.9;
const USER_HOLD = 2;

// The loop: rest, then five morphs of MORPH_FOR seconds, STEP apart.
const REST_FOR = 0.8;
const MORPH_FOR = 1.2;
const STEP = 1.45;
const SAMPLES = 180;

type ThermalState = {
  name: string;
  line: string;
  /** 0 hot … 1 cool. */
  cool: number;
  /** Direction of the bright side of the fill, degrees (y down). */
  angle: number;
  core: [number, number];
  coreRadius: number;
  coreAlpha: number;
};

const STATES: ThermalState[] = [
  { name: 'Spark', line: 'Quick, bright and ready to move', cool: 0, angle: 100, core: [0, 0.06], coreRadius: 0.78, coreAlpha: 0.95 },
  { name: 'Steady', line: 'Even, grounded and calm', cool: 1, angle: 55, core: [0.25, 0.38], coreRadius: 0.62, coreAlpha: 0.8 },
  { name: 'Glow', line: 'Warm, full and generous', cool: 0, angle: -75, core: [0.12, -0.34], coreRadius: 0.66, coreAlpha: 0.9 },
  { name: 'Bloom', line: 'Open and slowly growing', cool: 1, angle: -160, core: [0, 0], coreRadius: 0.85, coreAlpha: 0.9 },
  { name: 'Drift', line: 'Loose, easy, going with it', cool: 0.8, angle: -320, core: [0.3, 0.28], coreRadius: 0.6, coreAlpha: 0.75 },
];

const HOT = [0x14040e, 0x6e0a1f, 0xd8231c, 0xff7418, 0xffcf3a, 0xfff7da];
const COLD = [0x060619, 0x17168a, 0x3434e0, 0x6e78ff, 0xbfc4ff, 0xf7f6ff];
const RAMP_STOPS = [0, 0.18, 0.4, 0.62, 0.82, 1];
const RIM_DARK = 0xff2a1f;
const RIM_HOT = 0xff7a3a;
const RIM_COOL = 0x8a7bff;
const VIOLET = 0x4a3aff;
const WARM_GLOW = 0xff5a36;
const ACCENT_HOT = 0xff8a3d;
const ACCENT_COOL = 0x8c8cff;
const DOT_DIM = 0x34304a;

/** One moment: segment start state i, outline mix m, colour/caption mix q, loop time t. */
type Pose = { i: number; m: number; q: number; t: number };

/** A move of the morph position started by a finger (or the ease back to rest). */
type Manual = { from: number; to: number; start: number; duration: number; back: boolean; tFrom: number; tTo: number };

// MARK: Math (declared before every worklet that calls them)

function wrap(k: number) {
  'worklet';
  return ((k % 5) + 5) % 5;
}
function clamp(x: number) {
  'worklet';
  return Math.min(Math.max(x, 0), 1);
}
function lerp(a: number, b: number, k: number) {
  'worklet';
  return a + (b - a) * k;
}
function smoothstep(a: number, b: number, x: number) {
  'worklet';
  const k = clamp((x - a) / (b - a));
  return k * k * (3 - 2 * k);
}
function easeInOutCubic(x: number) {
  'worklet';
  return x < 0.5 ? 4 * x * x * x : 1 - Math.pow(-2 * x + 2, 3) / 2;
}
/** About 3 % anticipation and overshoot. */
function easeInOutBack(x: number) {
  'worklet';
  const c2 = 0.9 * 1.525;
  return x < 0.5
    ? (Math.pow(2 * x, 2) * ((c2 + 1) * 2 * x - c2)) / 2
    : (Math.pow(2 * x - 2, 2) * ((c2 + 1) * (x * 2 - 2) + c2) + 2) / 2;
}
/** Lands with about 4 % overshoot. */
function easeOutBack(x: number) {
  'worklet';
  const c1 = 1.1;
  const c3 = c1 + 1;
  return 1 + c3 * Math.pow(x - 1, 3) + c1 * Math.pow(x - 1, 2);
}
function mixRgb(a: number, b: number, k: number) {
  'worklet';
  const m = (s: number) => {
    const ca = (a >> s) & 255;
    const cb = (b >> s) & 255;
    return Math.max(0, Math.min(255, Math.round(ca + (cb - ca) * k)));
  };
  return (m(16) << 16) | (m(8) << 8) | m(0);
}
function color(rgb: number, alpha: number) {
  'worklet';
  return Float32Array.of(((rgb >> 16) & 255) / 255, ((rgb >> 8) & 255) / 255, (rgb & 255) / 255, clamp(alpha));
}

// MARK: Pose

function poseOf(u: number, t: number): Pose {
  'worklet';
  const f = Math.floor(u);
  return { i: wrap(f), m: u - f, q: clamp(u - f), t };
}

function autoPose(t: number): Pose {
  'worklet';
  for (let k = 0; k < 5; k++) {
    const s = REST_FOR + STEP * k;
    if (t < s) return { i: k, m: 0, q: 0, t };
    if (t < s + MORPH_FOR) {
      const p = (t - s) / MORPH_FOR;
      return { i: k, m: easeInOutBack(p), q: easeInOutCubic(p), t };
    }
  }
  return { i: 0, m: 0, q: 0, t };
}

function captionAlpha(pose: Pose, k: number) {
  'worklet';
  if (k === pose.i) return 1 - smoothstep(0.3, 0.5, pose.q);
  if (k === wrap(pose.i + 1)) return smoothstep(0.5, 0.7, pose.q);
  return 0;
}

function captionShift(pose: Pose, k: number) {
  'worklet';
  if (k === pose.i) return -8 * smoothstep(0.3, 0.5, pose.q);
  if (k === wrap(pose.i + 1)) return 8 * (1 - smoothstep(0.5, 0.7, pose.q));
  return 0;
}

function poseAt(now: number, origin: number, manual: Manual | null, autoPlay: boolean, reduced: boolean): Pose {
  'worklet';
  if (manual) {
    const k = clamp((now - manual.start) / Math.max(manual.duration, 0.0001));
    const e = manual.back ? easeOutBack(k) : easeInOutCubic(k);
    const u = manual.from + (manual.to - manual.from) * e;
    let t: number;
    if (reduced) t = 0;
    else if (autoPlay) t = manual.tFrom + (manual.tTo - manual.tFrom) * easeInOutCubic(k);
    else t = (now - origin) % LOOP;
    return poseOf(u, t);
  }
  if (reduced) return { i: 0, m: 0, q: 0, t: 0 };
  const t = (((now - Math.max(origin, 0)) % LOOP) + LOOP) % LOOP;
  return autoPlay ? autoPose(t) : { i: 0, m: 0, q: 0, t };
}

// MARK: Shapes

/** Radius (in units of R) of state k at angle a (radians, y down). */
function shapeRadius(k: number, a: number) {
  'worklet';
  switch (wrap(k)) {
    case 0: {
      const p = 0.72;
      return 1.06 * Math.pow(Math.pow(Math.abs(Math.cos(a)), p) + Math.pow(Math.abs(Math.sin(a)), p), -1 / p);
    }
    case 1: {
      const n = 3.4;
      const b = a - Math.PI / 4;
      return 0.8 * Math.pow(Math.pow(Math.abs(Math.cos(b)), n) + Math.pow(Math.abs(Math.sin(b)), n), -1 / n);
    }
    case 2:
      return 0.86;
    case 3:
      return 0.84 * (1 - 0.12 * Math.cos(4 * a) - 0.04 * Math.cos(8 * a));
    default:
      return 0.85 * (1 + 0.075 * Math.cos(2 * a + 0.6) + 0.06 * Math.sin(3 * a + 1.1) + 0.045 * Math.cos(5 * a + 2.0));
  }
}

/** The outline's radii (in points) for this pose, before the glow scales. */
function radiiOf(pose: Pose) {
  'worklet';
  const tau = 2 * Math.PI;
  const w1 = (tau * 2 * pose.t) / LOOP;
  const w2 = (tau * 3 * pose.t) / LOOP;
  const swell = 1 + 0.035 * Math.sin(Math.PI * pose.q);
  const out: number[] = new Array(SAMPLES);
  for (let j = 0; j < SAMPLES; j++) {
    const a = (tau * j) / SAMPLES;
    const r0 = shapeRadius(pose.i, a);
    const r1 = shapeRadius(pose.i + 1, a);
    const living = 1 + 0.01 * Math.sin(3 * a + w1) + 0.006 * Math.sin(5 * a - w2);
    out[j] = (r0 + (r1 - r0) * pose.m) * living * swell * RADIUS;
  }
  return out;
}

/** A smooth closed path through the sampled outline, scaled about the centre. */
function outline(radii: number[], cx: number, cy: number, scale: number): SkPath {
  'worklet';
  const n = radii.length;
  const xs: number[] = new Array(n);
  const ys: number[] = new Array(n);
  for (let j = 0; j < n; j++) {
    const a = (2 * Math.PI * j) / n;
    const r = radii[j] * scale;
    xs[j] = cx + r * Math.cos(a);
    ys[j] = cy + r * Math.sin(a);
  }
  const path = Skia.Path.Make();
  path.moveTo((xs[n - 1] + xs[0]) / 2, (ys[n - 1] + ys[0]) / 2);
  for (let j = 0; j < n; j++) {
    const k = (j + 1) % n;
    path.quadTo(xs[j], ys[j], (xs[j] + xs[k]) / 2, (ys[j] + ys[k]) / 2);
  }
  path.close();
  return path;
}

/** A fresh anti-aliased paint. (No default parameter: a worklet does not capture what one names.) */
function paintOf(stroke: boolean): SkPaint {
  'worklet';
  const p = Skia.Paint();
  p.setAntiAlias(true);
  p.setStyle(stroke ? PaintStyle.Stroke : PaintStyle.Fill);
  return p;
}

// MARK: Draw

function drawScene(canvas: SkCanvas, w: number, h: number, pose: Pose) {
  'worklet';
  const cx = w / 2;
  const cy = h / 2 + CENTER_SHIFT;
  const a = STATES[pose.i];
  const b = STATES[wrap(pose.i + 1)];
  const q = pose.q;
  const tau = 2 * Math.PI;
  const cool = lerp(a.cool, b.cool, q);
  // Angles keep turning one way round the loop: the last state's 100° is written as −260°.
  const angleB = pose.i === 4 ? -260 : b.angle;
  const angle = lerp(a.angle, angleB, q) + 8 * Math.sin((tau * pose.t) / LOOP);
  const coreX = lerp(a.core[0], b.core[0], q) + 0.07 * Math.sin((tau * pose.t) / LOOP);
  const coreY = lerp(a.core[1], b.core[1], q) + 0.07 * Math.cos((tau * 2 * pose.t) / LOOP);
  const coreRadius = lerp(a.coreRadius, b.coreRadius, q);
  const coreAlpha = lerp(a.coreAlpha, b.coreAlpha, q);
  const ramp = HOT.map((hot, k) => mixRgb(hot, COLD[k], cool));
  const breath = 1 + 0.018 * Math.sin((tau * 2 * pose.t) / LOOP);

  // Stage: a dark violet vignette.
  const stage = paintOf(false);
  stage.setShader(
    Skia.Shader.MakeRadialGradient(
      vec(cx, cy),
      h * 0.75,
      [color(0x120e1f, 1), color(0x07060b, 1), color(0x040307, 1)],
      [0, 0.55, 1],
      TileMode.Clamp,
    ),
  );
  canvas.drawRect(Skia.XYWHRect(0, 0, w, h), stage);

  const radii = radiiOf(pose);
  const shape = outline(radii, cx, cy, 1);
  const rad = (angle * Math.PI) / 180;
  const dx = Math.cos(rad);
  const dy = Math.sin(rad);
  const dark = vec(cx - dx * RADIUS * 0.9, cy - dy * RADIUS * 0.9);
  const bright = vec(cx + dx * RADIUS * 0.95, cy + dy * RADIUS * 0.95);

  // Glows, added together in their own layer, which then sits on the stage.
  canvas.saveLayer();
  const warm = paintOf(false);
  warm.setColor(color(WARM_GLOW, lerp(0.42, 0.32, cool)));
  warm.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 44, true));
  warm.setBlendMode(BlendMode.Plus);
  canvas.drawPath(outline(radii, cx, cy, 1.3 * breath), warm);
  const halo = paintOf(false);
  halo.setColor(color(VIOLET, 1));
  halo.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 24, true));
  halo.setBlendMode(BlendMode.Plus);
  canvas.drawPath(outline(radii, cx, cy, 1.09 * breath), halo);
  const hug = paintOf(true);
  hug.setColor(color(VIOLET, 1));
  hug.setStrokeWidth(16);
  hug.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 9, true));
  hug.setBlendMode(BlendMode.Plus);
  canvas.drawPath(shape, hug);
  canvas.restore();

  // Body: the thermal ramp, the hot core and a cooler band along the edge.
  canvas.save();
  canvas.clipPath(shape, ClipOp.Intersect, true);
  const body = paintOf(false);
  body.setShader(
    Skia.Shader.MakeLinearGradient(dark, bright, ramp.map((c) => color(c, 1)), RAMP_STOPS, TileMode.Clamp),
  );
  canvas.drawPath(shape, body);
  const coreCx = cx + coreX * RADIUS;
  const coreCy = cy + coreY * RADIUS;
  const cr = coreRadius * RADIUS;
  const core = paintOf(false);
  core.setShader(
    Skia.Shader.MakeRadialGradient(
      vec(coreCx, coreCy),
      cr,
      [
        color(ramp[5], coreAlpha),
        color(ramp[5], coreAlpha * 0.9),
        color(ramp[4], coreAlpha * 0.7),
        color(ramp[3], coreAlpha * 0.25),
        color(ramp[3], 0),
      ],
      [0, 0.22, 0.5, 0.78, 1],
      TileMode.Clamp,
    ),
  );
  canvas.drawCircle(coreCx, coreCy, cr, core);
  const edge = paintOf(true);
  edge.setColor(color(ramp[1], 0.6));
  edge.setStrokeWidth(24);
  edge.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 10, true));
  canvas.drawPath(shape, edge);
  canvas.restore();

  // Rim: a soft glow and a thin hot line, red on the dark side.
  const rimBright = mixRgb(RIM_HOT, RIM_COOL, cool);
  const rimColors = [color(RIM_DARK, 1), color(mixRgb(RIM_DARK, rimBright, 0.5), 1), color(rimBright, 1)];
  const rimGlow = paintOf(true);
  rimGlow.setShader(Skia.Shader.MakeLinearGradient(dark, bright, rimColors, null, TileMode.Clamp));
  rimGlow.setAlphaf(0.9);
  rimGlow.setStrokeWidth(6);
  rimGlow.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 4, true));
  canvas.drawPath(shape, rimGlow);
  const rim = paintOf(true);
  rim.setShader(Skia.Shader.MakeLinearGradient(dark, bright, rimColors, null, TileMode.Clamp));
  rim.setStrokeWidth(2);
  canvas.drawPath(shape, rim);

  // Dots: one per state, the shown one stretched into a pill.
  const uC = pose.i + q;
  const accent = mixRgb(ACCENT_HOT, ACCENT_COOL, cool);
  const dotsY = cy + DOTS_Y;
  const base = 6;
  const grow = 14;
  const gap = 10;
  const on: number[] = [];
  const widths: number[] = [];
  let total = gap * 4;
  for (let j = 0; j < 5; j++) {
    let d = Math.abs(uC - j);
    d = Math.min(d, 5 - d);
    on.push(Math.max(0, 1 - d));
    widths.push(base + grow * on[j]);
    total += widths[j];
  }
  let x = cx - total / 2;
  for (let j = 0; j < 5; j++) {
    const dot = paintOf(false);
    dot.setColor(color(mixRgb(DOT_DIM, accent, on[j]), 1));
    canvas.drawRRect(Skia.RRectXY(Skia.XYWHRect(x, dotsY - base / 2, widths[j], base), base / 2, base / 2), dot);
    x += widths[j] + gap;
  }
}

// MARK: Component

/**
 * An energy check-in emblem for an invented wellness app, Vessa: one glowing shape filled like a
 * thermal camera image morphs star → diamond → circle → clover → blob and back, swapping its hot
 * and cool palettes, while the state's name crossfades under it. Tap for the next state, drag
 * sideways to scrub the morph.
 */
export default function ThermalShapeMorph({ autoPlay = false }: Props) {
  const reduced = useReducedMotion();
  const [size, setSize] = useState({ width: 0, height: 0 });

  // The clock, in seconds of the frame callback's time base. Everything below is a function of it.
  const now = useSharedValue(0);
  const origin = useSharedValue(-1);
  const manual = useSharedValue<Manual | null>(null);
  const dragging = useSharedValue(false);
  const returning = useSharedValue(false);
  const lastTouch = useSharedValue(-1e9);
  const dragBase = useSharedValue(0);
  const lastWhole = useSharedValue(0);
  const moved = useSharedValue(false);

  useFrameCallback((frame) => {
    const s = frame.timestamp / 1000;
    if (origin.get() < 0) origin.set(s);
    now.set(s);
    const man = manual.get();
    if (!autoPlay || !man || dragging.get()) return;
    if (returning.get()) {
      if (s >= man.start + man.duration) {
        manual.set(null);
        returning.set(false);
        origin.set(s);
      }
    } else if (s - lastTouch.get() >= USER_HOLD) {
      // 2 s after the last touch: ease back to the resting star, then restart the loop.
      const u = poseAt(s, origin.get(), man, autoPlay, reduced);
      const at = u.i + u.m;
      returning.set(true);
      manual.set({
        from: at,
        to: Math.round(at / 5) * 5,
        start: s,
        duration: reduced ? 0 : RETURN_FOR,
        back: false,
        tFrom: man.tTo,
        tTo: man.tTo < LOOP / 2 ? 0 : LOOP,
      });
    }
  });

  const pose = useDerivedValue(() => poseAt(now.get(), origin.get(), manual.get(), autoPlay, reduced));

  const { width, height } = size;
  const picture = useDerivedValue(() => {
    const p = pose.get();
    return createPicture((canvas) => {
      if (width <= 0) return;
      drawScene(canvas, width, height, p);
    });
  });

  const tick = () => {
    if (process.env.EXPO_OS !== 'web') Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
  };

  const settle = (target: number, duration: number) => {
    'worklet';
    const p = pose.get();
    const man = manual.get();
    const t = man ? man.tTo : p.t;
    lastTouch.set(now.get());
    manual.set({
      from: p.i + p.m,
      to: target,
      start: now.get(),
      duration: reduced ? 0 : duration,
      back: true,
      tFrom: t,
      tTo: t,
    });
  };

  const tap = () => {
    'worklet';
    const p = pose.get();
    returning.set(false);
    settle(Math.floor(p.i + p.m + 0.02) + 1, 0.75);
  };

  const gesture = Gesture.Pan()
    .minDistance(0)
    .onBegin(() => {
      const p = pose.get();
      const u = p.i + p.m;
      dragging.set(true);
      returning.set(false);
      moved.set(false);
      lastTouch.set(now.get());
      dragBase.set(u);
      lastWhole.set(Math.floor(u + 0.5));
      manual.set({ from: u, to: u, start: now.get(), duration: 0, back: false, tFrom: p.t, tTo: p.t });
    })
    .onUpdate((e) => {
      if (Math.abs(e.translationX) > 6) moved.set(true);
      if (!moved.get()) return;
      const man = manual.get();
      if (!man) return;
      lastTouch.set(now.get());
      const u = dragBase.get() - e.translationX / DRAG_PER_STATE;
      manual.set({ from: u, to: u, start: now.get(), duration: 0, back: false, tFrom: man.tFrom, tTo: man.tTo });
      const whole = Math.floor(u + 0.5);
      if (whole !== lastWhole.get()) {
        lastWhole.set(whole);
        scheduleOnRN(tick);
      }
    })
    .onFinalize(() => {
      dragging.set(false);
      if (moved.get()) {
        const p = pose.get();
        settle(Math.round(p.i + p.m), 0.45);
      } else {
        scheduleOnRN(tick);
        tap();
      }
      moved.set(false);
    });

  const onLayout = (e: LayoutChangeEvent) => {
    const { width: w, height: h } = e.nativeEvent.layout;
    if (w !== width || h !== height) setSize({ width: w, height: h });
  };

  const cy = height / 2 + CENTER_SHIFT;

  return (
    <View
      style={styles.screen}
      onLayout={onLayout}
      accessible
      accessibilityRole="button"
      accessibilityLabel="Energy check-in emblem"
      accessibilityHint="Tap for the next state"
      onAccessibilityTap={() => tap()}
    >
      <StatusBar style="light" />
      <GestureDetector gesture={gesture}>
        <View style={StyleSheet.absoluteFill}>
          <Canvas style={StyleSheet.absoluteFill}>
            <Picture picture={picture} />
          </Canvas>
          {width > 0 ? (
            <View style={StyleSheet.absoluteFill} pointerEvents="none">
              <Line top={cy + EYEBROW_Y} height={14}>
                <Text style={styles.eyebrow}>VESSA · ENERGY CHECK-IN</Text>
              </Line>
              {STATES.map((s, k) => (
                <Caption key={s.name} index={k} pose={pose} cy={cy} state={s} />
              ))}
              <Line top={cy + HINT_Y} height={16}>
                <Text style={styles.hint}>Tap to shift · drag to scrub</Text>
              </Line>
            </View>
          ) : null}
        </View>
      </GestureDetector>
    </View>
  );
}

/** A single line centred on x, its middle at `top`. */
function Line({ top, height, children }: { top: number; height: number; children: ReactNode }) {
  return <View style={[styles.line, { top: top - height / 2, height }]}>{children}</View>;
}

/** One state's name and line, shown while the morph is at (or passing) that state. */
function Caption({ index, pose, cy, state }: { index: number; pose: SharedValue<Pose>; cy: number; state: ThermalState }) {
  const nameStyle = useAnimatedStyle(() => {
    const p = pose.get();
    return { opacity: captionAlpha(p, index), transform: [{ translateY: captionShift(p, index) }] };
  });
  const lineStyle = useAnimatedStyle(() => {
    const p = pose.get();
    return { opacity: captionAlpha(p, index), transform: [{ translateY: captionShift(p, index) * 0.6 }] };
  });
  return (
    <>
      <Animated.View style={[styles.line, { top: cy + NAME_Y - 18, height: 36 }, nameStyle]}>
        <Text style={styles.name}>{state.name}</Text>
      </Animated.View>
      <Animated.View style={[styles.line, { top: cy + LINE_Y - 10, height: 20 }, lineStyle]}>
        <Text style={styles.text}>{state.line}</Text>
      </Animated.View>
    </>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: '#07060B' },
  line: { position: 'absolute', left: 0, right: 0, alignItems: 'center', justifyContent: 'center' },
  eyebrow: { color: '#7D7896', fontSize: 11, lineHeight: 14, fontWeight: '600', letterSpacing: 2.2 },
  name: { color: '#F3F0FA', fontSize: 30, lineHeight: 36, fontWeight: '600', letterSpacing: -0.3 },
  text: { color: '#9D98B5', fontSize: 15, lineHeight: 20 },
  hint: { color: '#4F4A66', fontSize: 12, lineHeight: 16, fontWeight: '500' },
});
