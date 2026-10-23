/**
 * Vitals Chip Morph — the hero: one Skia curve that is the Readiness gauge, the Move ring and the
 * heart, plus its accents (progress, dot, inner rings, glyphs), the rendered heart and its halo.
 * Everything is drawn in screen ref px (1206 wide) under one scale, from the shared blend.
 */

import {
  BlurMask,
  Canvas,
  Circle,
  Group,
  Image,
  ImageFilter,
  Paint,
  Path,
  Skia,
  SweepGradient,
  TileMode,
  useImage,
  vec,
  type SkPath,
} from '@shopify/react-native-skia';
import { useMemo } from 'react';
import { interpolateColor, useDerivedValue, type SharedValue } from 'react-native-reanimated';

import { liquidEffect } from './liquid';
import { CLOSED, HEART, MAIN_COLOUR, N, heroFrame, heroPoints, lerp, liquidAmount, rippleOf, wobbleAmount, type Hero as HeroFrame } from './motion';
import { colors, layout, liquidLook } from './theme';

const HEART_ART = require('./assets/heart.png') as number;

const RINGS = layout.rings;
const H = layout.heart;
const HALO_SCALE = 1.22;
const HALO_OPACITY = 0.72;
/** The pin's halo hugs the heart: strongest at its sides, gone ~60 px (3×) further out. */
const HALO_BLUR = 30;

type Props = {
  width: number;
  /** Screen y (pt) of ref y = layout.heroTop. */
  scale: number;
  lo: SharedValue<number>;
  hi: SharedValue<number>;
  u: SharedValue<number>;
  beat: SharedValue<number>;
  /** Reduced motion: 0 = draw the blend, 1 = cut at the midpoint and fade. */
  quiet: SharedValue<boolean>;
  /** The material's clock (s): loop time in the loop, continuous after a touch. */
  fxTime: SharedValue<number>;
  /** The shape's speed (u per second) and the heart's beats since it settled (−1 = not beating). */
  speed: SharedValue<number>;
  beatCount: SharedValue<number>;
};

// Paths use SkPath.moveTo/lineTo (deprecated in Skia 2.6 for PathBuilder): with PathBuilder the
// hero froze in Expo Go on this machine, while SkPath ran; keep it until that is understood.

/** Point on the blended curve at parameter t (t may pass 1 on a closed curve). */
function pointAt(pts: number[], t: number, closedness: number) {
  'worklet';
  const span = N - 1 + closedness;
  let f = t * span;
  if (closedness > 0.5) {
    f = ((f % N) + N) % N;
  } else {
    f = Math.min(Math.max(f, 0), N - 1);
  }
  const i0 = Math.floor(f);
  const i1 = (i0 + 1) % N;
  const w = f - i0;
  return [pts[2 * i0] + (pts[2 * i1] - pts[2 * i0]) * w, pts[2 * i0 + 1] + (pts[2 * i1 + 1] - pts[2 * i0 + 1]) * w];
}

/**
 * A band along the blended curve from t0 to t1: `half` ref px each side of the centre line, its ends
 * pushed `extend` px further along the tangent, its four corners rounded by `corner` px: the same
 * outline is the gauge's soft-cornered segment and (corner = half) a ring's round-capped progress.
 */
function bandPath(p: SkPath, pts: number[], t0: number, t1: number, closedness: number, half: number, extend: number, corner: number): SkPath {
  'worklet';
  if (t1 - t0 <= 0.0005 || half <= 0.01) return p;
  const span = N - 1 + closedness;
  const xs: number[] = [];
  const ys: number[] = [];
  const a = pointAt(pts, t0, closedness);
  xs.push(a[0]);
  ys.push(a[1]);
  const first = Math.floor(t0 * span) + 1;
  const last = Math.ceil(t1 * span) - 1;
  for (let k = first; k <= last; k++) {
    const q = pointAt(pts, k / span, closedness);
    xs.push(q[0]);
    ys.push(q[1]);
  }
  const b = pointAt(pts, t1, closedness);
  xs.push(b[0]);
  ys.push(b[1]);
  const n = xs.length;
  if (n < 2) return p;
  const nx: number[] = [];
  const ny: number[] = [];
  for (let i = 0; i < n; i++) {
    const i0 = Math.max(i - 1, 0);
    const i1 = Math.min(i + 1, n - 1);
    let dx = xs[i1] - xs[i0];
    let dy = ys[i1] - ys[i0];
    const len = Math.hypot(dx, dy) || 1;
    dx /= len;
    dy /= len;
    nx.push(-dy);
    ny.push(dx);
    if (i === 0) {
      xs[0] -= dx * extend;
      ys[0] -= dy * extend;
    }
    if (i === n - 1) {
      xs[n - 1] += dx * extend;
      ys[n - 1] += dy * extend;
    }
  }
  // Rounded corners: each end is a straight edge whose two corners are conic quarter-circles of
  // radius c (c = half makes a round cap).
  const c = Math.min(Math.max(corner, 0), half);
  const W = Math.SQRT1_2;
  const dx0 = ny[0];
  const dy0 = -nx[0];
  const dx1 = ny[n - 1];
  const dy1 = -nx[n - 1];
  // Start: on the left side, c in from the start corner.
  const lx0 = xs[0] + nx[0] * half;
  const ly0 = ys[0] + ny[0] * half;
  p.moveTo(lx0 + dx0 * c, ly0 + dy0 * c);
  for (let i = 1; i < n - 1; i++) p.lineTo(xs[i] + nx[i] * half, ys[i] + ny[i] * half);
  const lx1 = xs[n - 1] + nx[n - 1] * half;
  const ly1 = ys[n - 1] + ny[n - 1] * half;
  const rx1 = xs[n - 1] - nx[n - 1] * half;
  const ry1 = ys[n - 1] - ny[n - 1] * half;
  p.lineTo(lx1 - dx1 * c, ly1 - dy1 * c);
  p.conicTo(lx1, ly1, lx1 - nx[n - 1] * c, ly1 - ny[n - 1] * c, W);
  p.lineTo(rx1 + nx[n - 1] * c, ry1 + ny[n - 1] * c);
  p.conicTo(rx1, ry1, rx1 - dx1 * c, ry1 - dy1 * c, W);
  for (let i = n - 2; i >= 1; i--) p.lineTo(xs[i] - nx[i] * half, ys[i] - ny[i] * half);
  const rx0 = xs[0] - nx[0] * half;
  const ry0 = ys[0] - ny[0] * half;
  p.lineTo(rx0 + dx0 * c, ry0 + dy0 * c);
  p.conicTo(rx0, ry0, rx0 + nx[0] * c, ry0 + ny[0] * c, W);
  p.lineTo(lx0 - nx[0] * c, ly0 - ny[0] * c);
  p.conicTo(lx0, ly0, lx0 + dx0 * c, ly0 + dy0 * c, W);
  p.close();
  return p;
}

/** Effective blend for drawing: under reduced motion the shape cuts at the midpoint while it fades. */
function drawBlend(lo: number, hi: number, u: number, quiet: boolean) {
  'worklet';
  if (!quiet) return { lo, hi, u, alpha: 1 };
  const k = u < 0.5 ? lo : hi;
  return { lo: k, hi: k, u: 0, alpha: Math.abs(1 - 2 * u) };
}

function arcPath(p: SkPath, cx: number, cy: number, r: number, progress: number): SkPath {
  'worklet';
  if (progress <= 0.0005) return p;
  p.addArc({ x: cx - r, y: cy - r, width: 2 * r, height: 2 * r }, -90, 360 * Math.min(progress, 0.9999));
  return p;
}

function circlePath(p: SkPath, cx: number, cy: number, r: number): SkPath {
  'worklet';
  p.addCircle(cx, cy, r);
  return p;
}

/**
 * Two of a live path, used in turn: the one not on screen is rewound and refilled, so a morph frame allocates no SkPath
 * (a shared value only notifies on a new reference, hence two). `turn` lives in the worklet's own closure on the UI thread.
 */
type LivePath = { a: SkPath; b: SkPath; turn: { n: number } };
function livePath(): LivePath {
  return { a: Skia.Path.Make(), b: Skia.Path.Make(), turn: { n: 0 } };
}
function nextPath(live: LivePath): SkPath {
  'worklet';
  live.turn.n = 1 - live.turn.n;
  const p = live.turn.n === 1 ? live.b : live.a;
  p.rewind();
  return p;
}

/** The same for the curve's points: two arrays used in turn. */
type LiveBuffer = { a: number[]; b: number[]; turn: { n: number } };
function nextBuffer(live: LiveBuffer): number[] {
  'worklet';
  live.turn.n = 1 - live.turn.n;
  return live.turn.n === 1 ? live.b : live.a;
}

/** The ring glyphs at 12 o'clock: → (Move), » (Exercise), ↑ (Stand), as stroked paths (ref px). */
function glyphPath(): SkPath {
  const p = Skia.Path.Make();
  const x = RINGS.cx;
  const unit = 1;
  // Move: a right arrow
  let y = RINGS.cy - RINGS.r[0];
  p.moveTo(x - 14 * unit, y);
  p.lineTo(x + 14 * unit, y);
  p.moveTo(x + 3, y - 11);
  p.lineTo(x + 14, y);
  p.lineTo(x + 3, y + 11);
  // Exercise: a double chevron
  y = RINGS.cy - RINGS.r[1];
  p.moveTo(x - 14, y - 11);
  p.lineTo(x - 3, y);
  p.lineTo(x - 14, y + 11);
  p.moveTo(x + 1, y - 11);
  p.lineTo(x + 12, y);
  p.lineTo(x + 1, y + 11);
  // Stand: an up arrow
  y = RINGS.cy - RINGS.r[2];
  p.moveTo(x, y + 14);
  p.lineTo(x, y - 13);
  p.moveTo(x - 11, y - 2);
  p.lineTo(x, y - 13);
  p.lineTo(x + 11, y - 2);
  return p;
}

function haloPath(): SkPath {
  const p = Skia.Path.Make();
  for (let i = 0; i < N; i++) {
    const x = H.cx + (HEART[2 * i] - H.cx) * HALO_SCALE;
    const y = H.cy + (HEART[2 * i + 1] - H.cy) * HALO_SCALE;
    if (i === 0) p.moveTo(x, y);
    else p.lineTo(x, y);
  }
  p.close();
  return p;
}

const SOFT = Skia.ImageFilter.MakeBlur(liquidLook.softSigma, liquidLook.softSigma, TileMode.Decal, null);
/** At rest the layer gets this no-op filter: no blur, no shader, no allocation per frame. */
const IDENTITY = Skia.ImageFilter.MakeOffset(0, 0, null);

export function Hero({ width, scale, lo, hi, u, beat, quiet, fxTime, speed, beatCount }: Props) {
  const heart = useImage(HEART_ART);
  const glyphs = useMemo(() => glyphPath(), []);
  const halo = useMemo(() => haloPath(), []);
  // Compiled on first render, not at import; null keeps the flat morph. One builder, its uniforms set per frame.
  const builder = useMemo(() => {
    const effect = liquidEffect();
    return effect ? Skia.RuntimeShaderBuilder(effect) : null;
  }, []);
  const live = useMemo(() => ({ main: livePath(), accent: livePath(), track0: livePath(), track1: livePath(), arc0: livePath(), arc1: livePath() }), []);
  const pointBuffers = useMemo<LiveBuffer>(() => ({ a: new Array<number>(2 * N).fill(0), b: new Array<number>(2 * N).fill(0), turn: { n: 0 } }), []);

  const blend = useDerivedValue(() => drawBlend(lo.get(), hi.get(), u.get(), quiet.get()));
  const frame = useDerivedValue<HeroFrame>(() => {
    const b = blend.get();
    return heroFrame(b.lo, b.hi, b.u);
  });
  const points = useDerivedValue(() => {
    const b = blend.get();
    return heroPoints(b.lo, b.hi, frame.get().sh, nextBuffer(pointBuffers));
  });
  const closedness = useDerivedValue(() => {
    const b = blend.get();
    return lerp(CLOSED[b.lo], CLOSED[b.hi], frame.get().sh);
  });

  const mainPath = useDerivedValue(() => {
    const pts = points.get();
    const p = nextPath(live.main);
    p.moveTo(pts[0], pts[1]);
    for (let i = 1; i < N; i++) p.lineTo(pts[2 * i], pts[2 * i + 1]);
    if (closedness.get() > 0.999) p.close();
    return p;
  });
  const mainColour = useDerivedValue(() => {
    const b = blend.get();
    return interpolateColor(frame.get().colourMix, [0, 1], [MAIN_COLOUR[b.lo], MAIN_COLOUR[b.hi]]);
  });
  const mainWidth = useDerivedValue(() => frame.get().width);
  const strokeOpacity = useDerivedValue(() => (frame.get().width > 0.5 ? blend.get().alpha : 0));
  const fillOpacity = useDerivedValue(() => frame.get().fill * (1 - frame.get().heartImage) * blend.get().alpha);

  const accentPath = useDerivedValue(() => {
    const f = frame.get();
    const half = f.accentWidth / 2;
    const corner = lerp(layout.segment.corner, half, f.accentRed);
    return bandPath(nextPath(live.accent), points.get(), f.t0, f.t1, closedness.get(), half, half * f.accentRed, corner);
  });
  const accentBlur = useDerivedValue(() => Math.max(lerp(5, 0, frame.get().accentRed), 0.01));
  const accentOpacity = useDerivedValue(() => frame.get().accentOpacity * blend.get().alpha);
  const accentColours = useDerivedValue(() => {
    const r = frame.get().accentRed;
    return [
      interpolateColor(r, [0, 1], [colors.segment, colors.moveStart]),
      interpolateColor(r, [0, 1], [colors.segment, colors.moveEnd]),
      interpolateColor(r, [0, 1], [colors.segment, colors.moveEnd]),
    ];
  });

  const dotCentre = useDerivedValue(() => {
    const f = frame.get();
    const q = pointAt(points.get(), Math.max(f.t1 - layout.dot.lag * (1 - f.accentRed), f.t0), closedness.get());
    return vec(q[0], q[1]);
  });
  const dotR = useDerivedValue(() => frame.get().dotD / 2);
  const dotGlowR = useDerivedValue(() => (frame.get().dotD / layout.dot.d) * (layout.dot.glow / 2));
  const dotOpacity = useDerivedValue(() => frame.get().dotOpacity * blend.get().alpha);
  const dotGlowOpacity = useDerivedValue(() => 0.9 * frame.get().dotOpacity * blend.get().alpha);

  // Inner rings (Exercise = 0, Stand = 1)
  const ring0R = useDerivedValue(() => RINGS.r[0] * frame.get().ringR0);
  const ring1R = useDerivedValue(() => RINGS.r[0] * frame.get().ringR1);
  const ring0Track = useDerivedValue(() => circlePath(nextPath(live.track0), RINGS.cx, RINGS.cy, ring0R.get()));
  const ring1Track = useDerivedValue(() => circlePath(nextPath(live.track1), RINGS.cx, RINGS.cy, ring1R.get()));
  const ring0Arc = useDerivedValue(() => arcPath(nextPath(live.arc0), RINGS.cx, RINGS.cy, ring0R.get(), frame.get().ringP0));
  const ring1Arc = useDerivedValue(() => arcPath(nextPath(live.arc1), RINGS.cx, RINGS.cy, ring1R.get(), frame.get().ringP1));
  const ring0Opacity = useDerivedValue(() => frame.get().ringO0 * blend.get().alpha);
  const ring1Opacity = useDerivedValue(() => frame.get().ringO1 * blend.get().alpha);
  const glyphOpacity = useDerivedValue(() => 0.86 * frame.get().arrows * blend.get().alpha);

  const heartOpacity = useDerivedValue(() => frame.get().heartImage * blend.get().alpha);
  const haloOpacity = useDerivedValue(() => frame.get().halo * blend.get().alpha * (HALO_OPACITY + 2.5 * beat.get()));
  const beatTransform = useDerivedValue(() => [{ scale: 1 + beat.get() }]);

  // The liquid material over the whole shape (off at rest: the filter then returns its input). The builder is made once;
  // only the image filter wrapping it is new on a frame where the material is on (Skia's filters are immutable).
  const liquidFilter = useDerivedValue(() => {
    const b = blend.get();
    const calm = quiet.get();
    const melt = calm ? 0 : liquidAmount(b.lo, b.hi, b.u);
    const wobble = calm ? 0 : wobbleAmount(speed.get());
    const rip = rippleOf(calm ? -1 : beatCount.get());
    const ripA = rip.amp * frame.get().heartImage;
    if (!builder || (melt <= 0.0005 && wobble <= 0.0005 && ripA <= 0)) return IDENTITY;
    builder.setUniform('clock', [fxTime.get()]);
    builder.setUniform('liquid', [melt]);
    builder.setUniform('wobble', [wobble]);
    builder.setUniform('goo', [melt]);
    builder.setUniform('gooC', [liquidLook.gooCentre[0], liquidLook.gooCentre[1]]);
    builder.setUniform('heartC', [H.cx, H.cy]);
    builder.setUniform('ripT', [rip.age]);
    builder.setUniform('ripA', [ripA]);
    return Skia.ImageFilter.MakeRuntimeShaderWithChildren(builder, liquidLook.sampleRadius, ['image', 'soft'], [null, SOFT]);
  });

  const ringCentre = vec(RINGS.cx, RINGS.cy);
  const top = layout.heroTop;

  return (
    <Canvas style={{ width, height: layout.heroH * scale }} pointerEvents="none">
      <Group transform={[{ scale }, { translateY: -top }]}>
        <Group transform={beatTransform} origin={vec(H.cx, H.cy)}>
          <Path path={halo} color={colors.heartHalo} opacity={haloOpacity}>
            <BlurMask blur={HALO_BLUR} style="normal" />
          </Path>
        </Group>
        <Group
          layer={
            <Paint>
              <ImageFilter filter={liquidFilter} />
            </Paint>
          }
        >
          <Group transform={beatTransform} origin={vec(H.cx, H.cy)}>
            <Path path={mainPath} color={mainColour} opacity={fillOpacity} />
            <Path
              path={mainPath}
              color={mainColour}
              style="stroke"
              strokeWidth={mainWidth}
              strokeCap="round"
              strokeJoin="round"
              opacity={strokeOpacity}
            />
            {heart && <Image image={heart} x={H.x} y={H.y} width={H.w} height={H.h} fit="fill" opacity={heartOpacity} />}
          </Group>

          <Group opacity={ring0Opacity}>
            <Path path={ring0Track} color={colors.exerciseTrack} style="stroke" strokeWidth={RINGS.stroke} />
            <Path path={ring0Arc} style="stroke" strokeWidth={RINGS.stroke} strokeCap="round">
              <SweepGradient c={ringCentre} colors={[colors.exerciseStart, colors.exerciseEnd, colors.exerciseEnd]} positions={[0, 0.63, 1]} transform={[{ rotate: -Math.PI / 2 }]} origin={ringCentre} />
            </Path>
          </Group>
          <Group opacity={ring1Opacity}>
            <Path path={ring1Track} color={colors.standTrack} style="stroke" strokeWidth={RINGS.stroke} />
            <Path path={ring1Arc} style="stroke" strokeWidth={RINGS.stroke} strokeCap="round">
              <SweepGradient c={ringCentre} colors={[colors.standStart, colors.standEnd, colors.standEnd]} positions={[0, 0.33, 1]} transform={[{ rotate: -Math.PI / 2 }]} origin={ringCentre} />
            </Path>
          </Group>

          <Path path={accentPath} opacity={accentOpacity}>
            <BlurMask blur={accentBlur} style="normal" />
            <SweepGradient c={ringCentre} colors={accentColours} positions={[0, 0.75, 1]} transform={[{ rotate: -Math.PI / 2 }]} origin={ringCentre} />
          </Path>

          <Circle c={dotCentre} r={dotGlowR} color={colors.blueGlow} opacity={dotGlowOpacity}>
            <BlurMask blur={10} style="normal" />
          </Circle>
          <Circle c={dotCentre} r={dotR} color={colors.blue} opacity={dotOpacity} />

          <Path path={glyphs} style="stroke" strokeWidth={7.5} strokeCap="round" strokeJoin="round" color={colors.glyph} opacity={glyphOpacity} />
        </Group>
      </Group>
    </Canvas>
  );
}
