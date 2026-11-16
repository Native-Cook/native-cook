import { useEffect, useMemo, useState } from 'react';
import { AppState, Platform, StyleSheet, useWindowDimensions, View } from 'react-native';
import {
  BlendMode,
  Canvas,
  ClipOp,
  PaintStyle,
  Picture,
  Skia,
  TileMode,
  matchFont,
  type SkCanvas,
  type SkFont,
} from '@shopify/react-native-skia';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import { useDerivedValue, useFrameCallback, useSharedValue } from 'react-native-reanimated';
import { scheduleOnRN } from 'react-native-worklets';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';

import { useReducedMotionPreference } from '@/shared/useReducedMotionPreference';

export const LOOP = 8;

// The timeline, shared number for number with the SwiftUI reference.
const BUILD_START = 0.8;
const BUILD_END = 4.4;
const SWEEP_START = 4.5;
const SWEEP_LENGTH = 1.0;
const RETURN_START = 6.0;
const RETURN_END = 7.2;
const RETURN = 0.9;

const PLAYING = 0;
const HELD = 1;
const RETURNING = 2;

// Scene, in a 402 × 874 design space scaled to the width it is given.
const DESIGN_WIDTH = 402;
const DESIGN_HEIGHT = 874;
const CENTER_X = 201;
const STACK_Y = 366;
const SLATS = 11;
const PITCH = 44;
const SLAT_HEIGHT = 36;

// Per-slat refraction: sideways displacement (× 22) and blur amount (0 sharp … 1 soft).
const OFFSETS = [-0.62, 0.38, -0.21, 0.74, -0.48, 0.12, 0.57, -0.33, 0.81, -0.70, 0.26];
const BLURS = [0.95, 0.55, 0.80, 0.30, 0.62, 0.18, 0.45, 0.70, 0.35, 0.85, 1.0];

// The aurora's three bodies: cobalt, teal, lime.
type Rgb = readonly [number, number, number];
const COLORS: readonly Rgb[] = [[0.110, 0.220, 1.0], [0.200, 0.740, 0.760], [0.640, 0.980, 0.300]];
const RISE_Y = [-0.52, 0.04, 0.56];
const WANDER_Y = [1, 2, 1];
const PHASE_Y = [0.0, 2.1, 4.0];
const SWAY_X = [0.46, 0.34, 0.50];
const WANDER_X = [2, 1, 3];
const PHASE_X = [0.6, 2.9, 4.4];
const RADIUS_X = [0.86, 0.66, 0.62];

const BACKGROUND: Rgb = [0.071, 0.039, 0.149];
const VIGNETTE_CORE: Rgb = [0.106, 0.063, 0.227];
const VIGNETTE_EDGE: Rgb = [0.043, 0.024, 0.094];
const WHITE: Rgb = [1, 1, 1];
const FLASH: Rgb = [0.90, 0.98, 1.0];
const SYNCED: Rgb = [0.72, 0.98, 0.44];
const BACKGROUND_HEX = '#120A26';

/** Everything one frame of the scene needs. */
type Frame = {
  p: number;
  drift: number;
  pushX: number;
  pushY: number;
  sweep: number;
  percent: number;
  numberAlpha: number;
  syncingAlpha: number;
  syncedAlpha: number;
};

type Fonts = { title: SkFont; label: SkFont; small: SkFont; number: SkFont };

// Worklets are declared before their callers: one declared further down is undefined on the UI thread.

function seg(t: number, a: number, b: number) {
  'worklet';
  return Math.min(Math.max((t - a) / (b - a), 0), 1);
}

function inOut(x: number) {
  'worklet';
  return x < 0.5 ? 4 * x * x * x : 1 - Math.pow(-2 * x + 2, 3) / 2;
}

function smooth(x: number) {
  'worklet';
  const u = Math.min(Math.max(x, 0), 1);
  return u * u * (3 - 2 * u);
}

function wrap(t: number) {
  'worklet';
  const m = t % LOOP;
  return m < 0 ? m + LOOP : m;
}

/** Finger travel with a rubber band: follows at first, then falls off. */
function rubber(x: number) {
  'worklet';
  return x / (1 + Math.abs(x) / 140);
}

/** Unit step response of the release spring (response 0.45 s, damping ratio 0.78), from 1 to 0. */
function spring(tau: number) {
  'worklet';
  if (tau <= 0) return 1;
  const omega = (2 * Math.PI) / 0.45;
  const zeta = 0.78;
  const damped = omega * Math.sqrt(1 - zeta * zeta);
  return Math.exp(-zeta * omega * tau) * (Math.cos(damped * tau) + ((zeta * omega) / damped) * Math.sin(damped * tau));
}

/** Rest 0–0.8 s, build 0.8–4.4 s, peak 4.4–6 s (sweep 4.5–5.5 s), return 6–7.2 s, rest to 8 s. */
function loopFrame(t: number): Frame {
  'worklet';
  const build = inOut(seg(t, BUILD_START, BUILD_END));
  const back = inOut(seg(t, RETURN_START, RETURN_END));
  return {
    p: build * (1 - back),
    drift: t,
    pushX: 0,
    pushY: 0,
    sweep: t >= SWEEP_START && t <= SWEEP_START + SWEEP_LENGTH ? (t - SWEEP_START) / SWEEP_LENGTH : -1,
    percent: t < 6.55 ? Math.round(build * 100) : 0,
    numberAlpha: t < 6.55 ? 1 - seg(t, 6.0, 6.35) : seg(t, 6.75, 7.1),
    syncedAlpha: seg(t, 4.45, 4.7) * (1 - seg(t, 6.0, 6.35)),
    syncingAlpha: t < 5.5 ? 1 - seg(t, 4.4, 4.6) : seg(t, 6.75, 7.1),
  };
}

/** The state under the finger: the label follows the energy directly. */
function heldFrame(p: number, drift: number, pushX: number, pushY: number, sweep: number): Frame {
  'worklet';
  return {
    p,
    drift,
    pushX,
    pushY,
    sweep,
    percent: Math.round(p * 100),
    numberAlpha: 1,
    syncedAlpha: seg(p, 0.97, 1.0),
    syncingAlpha: 1 - seg(p, 0.94, 0.98),
  };
}

/** Reduced motion: the finished, legible state. */
function finishedFrame(): Frame {
  'worklet';
  return { p: 1, drift: 0, pushX: 0, pushY: 0, sweep: -1, percent: 100, numberAlpha: 1, syncingAlpha: 0, syncedAlpha: 1 };
}

function rgba(color: Rgb, alpha: number) {
  'worklet';
  return Float32Array.of(color[0], color[1], color[2], Math.min(Math.max(alpha, 0), 1));
}

function fillPaint(color: Rgb, alpha: number) {
  'worklet';
  const paint = Skia.Paint();
  paint.setAntiAlias(true);
  paint.setColor(rgba(color, alpha));
  return paint;
}

/** A soft elliptical body of light: a radial falloff squashed to rx × ry. */
function blob(
  canvas: SkCanvas, x: number, y: number, rx: number, ry: number, color: Rgb, alpha: number, plus: boolean,
) {
  'worklet';
  if (alpha <= 0.002 || rx <= 0.5) return;
  const paint = Skia.Paint();
  paint.setAntiAlias(true);
  if (plus) paint.setBlendMode(BlendMode.Plus);
  paint.setShader(Skia.Shader.MakeRadialGradient(
    { x: 0, y: 0 }, 1,
    [rgba(color, alpha), rgba(color, alpha * 0.86), rgba(color, alpha * 0.30), rgba(color, 0)],
    [0, 0.5, 0.8, 1], TileMode.Clamp,
  ));
  canvas.save();
  canvas.translate(x, y);
  canvas.scale(rx, ry);
  canvas.drawCircle(0, 0, 1, paint);
  canvas.restore();
}

function edgeLine(canvas: SkCanvas, a: number, b: number, slope: number, top: number, alpha: number) {
  'worklet';
  if (alpha <= 0.004 || b <= a) return;
  const start = { x: CENTER_X + a, y: top + slope * a };
  const end = { x: CENTER_X + b, y: top + slope * b };
  const paint = Skia.Paint();
  paint.setAntiAlias(true);
  paint.setStyle(PaintStyle.Stroke);
  paint.setStrokeWidth(1);
  paint.setShader(Skia.Shader.MakeLinearGradient(
    start, end, [rgba(WHITE, 0), rgba(WHITE, alpha), rgba(WHITE, 0)], [0, 0.5, 1], TileMode.Clamp,
  ));
  canvas.drawLine(start.x, start.y, end.x, end.y, paint);
}

function ornaments(canvas: SkCanvas) {
  'worklet';
  // Crosshair on the stack's centre.
  const white = fillPaint(WHITE, 0.9);
  white.setStyle(PaintStyle.Stroke);
  white.setStrokeWidth(1);
  canvas.drawLine(CENTER_X - 11, STACK_Y, CENTER_X + 11, STACK_Y, white);
  canvas.drawLine(CENTER_X, STACK_Y - 11, CENTER_X, STACK_Y + 11, white);

  // Viewfinder corners, top right.
  const l = 346, t = 62, s = 30, a = 8;
  const corners = Skia.PathBuilder.Make();
  const marks = [[l, t, 1, 1], [l + s, t, -1, 1], [l, t + s, 1, -1], [l + s, t + s, -1, -1]];
  for (let i = 0; i < marks.length; i++) {
    const m = marks[i];
    corners.moveTo(m[0] + m[2] * a, m[1]);
    corners.lineTo(m[0], m[1]);
    corners.lineTo(m[0], m[1] + m[3] * a);
  }
  const line = fillPaint(WHITE, 0.55);
  line.setStyle(PaintStyle.Stroke);
  line.setStrokeWidth(1);
  canvas.drawPath(corners.build(), line);

  // Dot grids in two corners: a staircase of small dots.
  const dot = fillPaint(WHITE, 0.42);
  for (let row = 0; row < 4; row++) {
    for (let col = 0; col < 4 - row; col++) {
      canvas.drawCircle(26 + col * 6, 122 + row * 6, 1, dot);
      canvas.drawCircle(376 - col * 6, 626 - row * 6, 1, dot);
    }
  }
}

function textWidth(font: SkFont, text: string) {
  'worklet';
  // getGlyphWidths keeps fractions; measureText rounds each glyph to whole points.
  const widths = font.getGlyphWidths(font.getGlyphIDs(text));
  let sum = 0;
  for (let i = 0; i < widths.length; i++) sum += widths[i];
  return sum;
}

/** Text whose vertical centre sits on y; align 0 = leading at x, 0.5 = centred on x. */
function label(
  canvas: SkCanvas, font: SkFont, text: string, x: number, y: number, align: number, color: Rgb, alpha: number,
) {
  'worklet';
  if (alpha <= 0.003) return;
  const metrics = font.getMetrics();
  canvas.drawText(
    text, x - textWidth(font, text) * align, y - (metrics.ascent + metrics.descent) / 2, fillPaint(color, alpha), font,
  );
}

/** The percentage in tabular cells: every digit gets the widest digit's advance, so it never jitters. */
function percentLabel(canvas: SkCanvas, font: SkFont, percent: number, y: number, alpha: number) {
  'worklet';
  if (alpha <= 0.003) return;
  const digits = `${Math.min(100, Math.max(0, Math.round(percent)))}`;
  const widths = font.getGlyphWidths(font.getGlyphIDs('0123456789%'));
  let cell = 0;
  for (let i = 0; i < 10; i++) cell = Math.max(cell, widths[i]);
  const sign = widths[10];
  const metrics = font.getMetrics();
  const baseline = y - (metrics.ascent + metrics.descent) / 2;
  const paint = fillPaint(WHITE, 0.96 * alpha);
  let x = CENTER_X - (digits.length * cell + sign) / 2;
  for (let i = 0; i < digits.length; i++) {
    const glyph = digits[i];
    canvas.drawText(glyph, x + (cell - widths[glyph.charCodeAt(0) - 48]) / 2, baseline, paint, font);
    x += cell;
  }
  canvas.drawText('%', x, baseline, paint, font);
}

function labels(canvas: SkCanvas, fonts: Fonts, f: Frame) {
  'worklet';
  label(canvas, fonts.title, 'Halo Vault', 24, 77, 0, WHITE, 0.94);
  label(canvas, fonts.small, 'Studio library', 24, 98, 0, WHITE, 0.5);
  percentLabel(canvas, fonts.number, f.percent, 672, f.numberAlpha);
  label(canvas, fonts.label, 'Syncing…', CENTER_X, 716 - 6 * (1 - f.syncingAlpha), 0.5, WHITE, 0.9 * f.syncingAlpha);
  label(canvas, fonts.label, 'All synced', CENTER_X, 716 + 6 * (1 - f.syncedAlpha), 0.5, SYNCED, f.syncedAlpha);
  label(canvas, fonts.small, '1,284 files · 3 devices', CENTER_X, 742, 0.5, WHITE, 0.46);
}

function drawScene(canvas: SkCanvas, fonts: Fonts, width: number, height: number, f: Frame) {
  'worklet';
  canvas.drawColor(rgba(BACKGROUND, 1));
  const k = width / DESIGN_WIDTH;
  if (k <= 0) return;
  canvas.save();
  canvas.scale(k, k);
  canvas.translate(0, (height / k - DESIGN_HEIGHT) / 2);

  // Vignette: a slightly lifted indigo behind the stack, darker towards the corners.
  const vignette = Skia.Paint();
  vignette.setShader(Skia.Shader.MakeRadialGradient(
    { x: CENTER_X, y: STACK_Y }, 560,
    [rgba(VIGNETTE_CORE, 1), rgba(BACKGROUND, 1), rgba(VIGNETTE_EDGE, 1)], [0, 0.55, 1], TileMode.Clamp,
  ));
  canvas.drawCircle(CENTER_X, STACK_Y, 1200, vignette);

  const p = f.p;
  const eased = smooth(p);
  const energy = 0.86 + 0.14 * p;
  const amps = [0.80 + 0.20 * p, 0.88, 0.34 + 0.66 * p];
  const turn = (2 * Math.PI) / LOOP;
  const centersY = [0, 0, 0];
  for (let b = 0; b < 3; b++) {
    centersY[b] = RISE_Y[b] * p + 0.10 * Math.sin(turn * WANDER_Y[b] * f.drift + PHASE_Y[b]) + f.pushY / 240;
  }

  // The light that escapes the glass: a wide, faint bloom behind the stack.
  for (let b = 0; b < 3; b++) {
    blob(
      canvas, CENTER_X + f.pushX * 0.3, STACK_Y + centersY[b] * 5 * PITCH,
      150 + 90 * p, 120 + 70 * p, COLORS[b], (0.05 + 0.10 * p) * amps[b], true,
    );
  }

  const sweepAt = f.sweep < 0 ? -99 : -1.5 + 13 * smooth(f.sweep);
  const sweepAmp = f.sweep < 0 ? 0 : Math.sin(Math.PI * Math.min(f.sweep, 1));
  const sigma = 0.42 + 0.30 * p;

  for (let i = 0; i < SLATS; i++) {
    const di = i - 5;
    const u = di / 5;
    const half = 180 * Math.sqrt(1 - 0.70 * u * u);
    const dim = 1 - 0.5 * u * u;
    const top = STACK_Y + di * PITCH - SLAT_HEIGHT / 2;
    const slope = (-3.5 * u) / half;
    const lit = smooth(1 - (Math.abs(di) - (1.9 + 3.0 * p)) / 1.6);
    const blur = BLURS[i];
    const band = half * (0.46 + 0.54 * eased) * (0.75 + 0.25 * lit);
    const flash = sweepAmp * Math.exp((-(i - sweepAt) * (i - sweepAt)) / 1.6);
    const shift = OFFSETS[i] * 22 * (0.5 + 0.5 * p) + f.pushX * (0.55 + 0.6 * blur);

    // The slat: a strip of glass seen slightly from the side, so its edges fan out.
    const x0 = -half - 26;
    const x1 = half + 26;
    const quad = Skia.PathBuilder.Make()
      .moveTo(CENTER_X + x0, top + slope * x0)
      .lineTo(CENTER_X + x1, top + slope * x1)
      .lineTo(CENTER_X + x1, top + SLAT_HEIGHT + 0.4 * slope * x1)
      .lineTo(CENTER_X + x0, top + SLAT_HEIGHT + 0.4 * slope * x0)
      .close()
      .build();
    const body = Skia.Paint();
    body.setAntiAlias(true);
    body.setShader(Skia.Shader.MakeLinearGradient(
      { x: CENTER_X + x0, y: top }, { x: CENTER_X + x1, y: top },
      [rgba(WHITE, 0), rgba(WHITE, 0.034 * dim), rgba(WHITE, 0.034 * dim), rgba(WHITE, 0)],
      [0, 0.2, 0.8, 1], TileMode.Clamp,
    ));
    canvas.drawPath(quad, body);

    // Inside it the aurora is displaced, stretched and blurred by this slat's own amount:
    // brightest under the sharp top edge, gone before the bottom one.
    const pane = Skia.PathBuilder.Make()
      .moveTo(CENTER_X + x0 - 70, top + slope * (x0 - 70))
      .lineTo(CENTER_X + x1 + 70, top + slope * (x1 + 70))
      .lineTo(CENTER_X + x1 + 70, top + SLAT_HEIGHT + 4)
      .lineTo(CENTER_X + x0 - 70, top + SLAT_HEIGHT + 4)
      .close()
      .build();
    canvas.save();
    canvas.clipPath(pane, ClipOp.Intersect, true);
    canvas.translate(CENTER_X, top);
    canvas.skew(0, slope);
    for (let b = 0; b < 3; b++) {
      const d = (u - centersY[b]) / sigma;
      const alpha = energy * lit * dim * Math.exp(-d * d) * amps[b] * (1 - 0.25 * blur);
      const x = SWAY_X[b] * band * Math.sin(turn * WANDER_X[b] * f.drift + PHASE_X[b] + i * 0.55) + shift;
      blob(
        canvas, x, SLAT_HEIGHT * 0.10,
        band * RADIUS_X[b] * (1 + 0.45 * blur), SLAT_HEIGHT * (1.9 + 0.5 * blur),
        COLORS[b], Math.min(1, alpha * 1.7), false,
      );
    }
    if (flash > 0.003) {
      blob(canvas, shift * 0.4, SLAT_HEIGHT * 0.10, half * 1.05, SLAT_HEIGHT * 1.9, FLASH, 0.38 * flash * dim, true);
    }
    canvas.restore();

    // The soft bottom edge of reeded glass: the image falls away into the dark.
    const shade = Skia.Paint();
    shade.setAntiAlias(true);
    shade.setShader(Skia.Shader.MakeLinearGradient(
      { x: CENTER_X, y: top + SLAT_HEIGHT * 0.5 }, { x: CENTER_X, y: top + SLAT_HEIGHT + 2 },
      [rgba(BACKGROUND, 0), rgba(BACKGROUND, 0.42 + 0.2 * blur), rgba(BACKGROUND, 0.92)],
      [0, 0.6, 1], TileMode.Clamp,
    ));
    canvas.drawPath(pane, shade);

    // Edges catch light: a hairline on the glass, brighter where the aurora sits under it.
    edgeLine(canvas, x0 + 20, x1 - 20, slope, top, 0.09 * dim);
    edgeLine(
      canvas, shift - band * 1.1, shift + band * 1.1, slope, top,
      Math.min(1, 0.55 * lit * energy * dim + 0.7 * flash * dim),
    );
  }

  ornaments(canvas);
  labels(canvas, fonts, f);
  canvas.restore();
}

/**
 * A sync hero: an aurora seen through eleven slats of reeded glass. Drag to push the light and
 * scrub the sync; two seconds after the last touch it settles and the eight-second loop resumes.
 */
export function AuroraSlatSync({ autoPlay = false }: { autoPlay?: boolean }) {
  // The size the block is given (on Android the window height leaves out the status bar).
  const screen = useWindowDimensions();
  const [size, setSize] = useState<{ width: number; height: number } | null>(null);
  const width = size?.width ?? screen.width;
  const height = size?.height ?? screen.height;
  const reduced = useReducedMotionPreference();
  const [active, setActive] = useState(AppState.currentState === 'active');
  useEffect(() => {
    const sub = AppState.addEventListener('change', state => setActive(state === 'active'));
    return () => sub.remove();
  }, []);

  // Seconds since the first frame; every drawing value is derived from it and the touch state.
  const clock = useSharedValue(0);
  const origin = useSharedValue(0);
  const mode = useSharedValue(PLAYING);
  const dragging = useSharedValue(false);
  const energy = useSharedValue(0);
  const startP = useSharedValue(0);
  const holdDrift = useSharedValue(0);
  const holdStart = useSharedValue(0);
  const pushX = useSharedValue(0);
  const pushY = useSharedValue(0);
  const releaseAt = useSharedValue(0);
  const sweepAt = useSharedValue(-100);
  const fromP = useSharedValue(0);
  const fromDrift = useSharedValue(0);
  const toDrift = useSharedValue(0);
  const returnStart = useSharedValue(0);
  const tick = useSharedValue(-1);

  const haptic = () => { void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light); };

  const frame = useFrameCallback(info => {
    const now = clock.get() + (info.timeSincePreviousFrame ?? 0) / 1000;
    clock.set(now);
    const state = mode.get();
    if (state === HELD) {
      if (!dragging.get() && now - releaseAt.get() >= 2) {
        const drift = wrap(holdDrift.get() + now - holdStart.get());
        fromP.set(energy.get());
        fromDrift.set(drift);
        toDrift.set(drift < LOOP / 2 ? 0 : LOOP);
        returnStart.set(now);
        mode.set(RETURNING);
      }
    } else if (state === RETURNING) {
      if (now - returnStart.get() >= RETURN) {
        origin.set(now);
        tick.set(-1);
        mode.set(PLAYING);
      }
    } else if (autoPlay) {
      // Changes once per loop, when the sweep starts; never at the loop seam.
      const next = Math.floor((now - origin.get() + LOOP - SWEEP_START) / LOOP);
      if (tick.get() >= 0 && next > tick.get()) scheduleOnRN(haptic);
      tick.set(next);
    }
  }, false);

  useEffect(() => {
    // Restart from rest whenever the block starts, stops or returns to the foreground.
    mode.set(PLAYING);
    dragging.set(false);
    pushX.set(0);
    pushY.set(0);
    tick.set(-1);
    origin.set(clock.get());
    frame.setActive(active && !reduced);
    return () => frame.setActive(false);
  }, [active, reduced, autoPlay, frame, mode, dragging, pushX, pushY, tick, origin, clock]);

  const fonts = useMemo<Fonts>(() => {
    const fontFamily = Platform.OS === 'ios' ? 'Helvetica Neue' : 'sans-serif';
    return {
      title: matchFont({ fontFamily, fontSize: 17, fontWeight: '600' }),
      label: matchFont({ fontFamily, fontSize: 17, fontWeight: '600' }),
      small: matchFont({ fontFamily, fontSize: 13, fontWeight: '400' }),
      number: matchFont({ fontFamily, fontSize: 46, fontWeight: '300' }),
    };
  }, []);

  const picture = useDerivedValue(() => {
    const now = clock.get();
    let f: Frame;
    if (reduced) {
      f = finishedFrame();
    } else if (mode.get() === HELD) {
      const settle = dragging.get() ? 1 : spring(now - releaseAt.get());
      const sweep = (now - sweepAt.get()) / SWEEP_LENGTH;
      f = heldFrame(
        energy.get(), holdDrift.get() + now - holdStart.get(), pushX.get() * settle, pushY.get() * settle,
        sweep >= 0 && sweep <= 1 ? sweep : -1,
      );
    } else if (mode.get() === RETURNING) {
      const e = inOut(seg(now - returnStart.get(), 0, RETURN));
      f = heldFrame(fromP.get() * (1 - e), fromDrift.get() + (toDrift.get() - fromDrift.get()) * e, 0, 0, -1);
    } else {
      f = loopFrame(autoPlay ? wrap(Math.max(0, now - origin.get())) : 0);
    }
    const recorder = Skia.PictureRecorder();
    const canvas = recorder.beginRecording(Skia.XYWHRect(0, 0, width, height));
    drawScene(canvas, fonts, width, height, f);
    return recorder.finishRecordingAsPicture();
  }, [width, height, fonts, reduced, autoPlay]);

  const pan = Gesture.Pan().minDistance(0).enabled(!reduced)
    .onBegin(() => {
      const now = clock.get();
      // Freeze on what is on screen: the loop's energy, or the state a return was passing through.
      let p = 0;
      let drift = 0;
      if (mode.get() === HELD) {
        p = energy.get();
        drift = holdDrift.get() + now - holdStart.get();
      } else if (mode.get() === RETURNING) {
        const e = inOut(seg(now - returnStart.get(), 0, RETURN));
        p = fromP.get() * (1 - e);
        drift = fromDrift.get() + (toDrift.get() - fromDrift.get()) * e;
      } else {
        const frozen = loopFrame(autoPlay ? wrap(Math.max(0, now - origin.get())) : 0);
        p = frozen.p;
        drift = frozen.drift;
      }
      dragging.set(true);
      startP.set(p);
      energy.set(p);
      holdDrift.set(drift);
      holdStart.set(now);
      pushX.set(0);
      pushY.set(0);
      mode.set(HELD);
    })
    .onUpdate(event => {
      if (!dragging.get()) return;
      pushX.set(rubber(event.translationX));
      pushY.set(rubber(event.translationY));
      const next = Math.min(1, Math.max(0, startP.get() + event.translationX / 300 - event.translationY / 460));
      if (next >= 1 && energy.get() < 1) {
        sweepAt.set(clock.get());
        scheduleOnRN(haptic);
      }
      energy.set(next);
    })
    .onFinalize(() => {
      if (!dragging.get()) return;
      dragging.set(false);
      releaseAt.set(clock.get());
    });

  return (
    <GestureDetector gesture={pan}>
      <View
        style={styles.screen}
        onLayout={event => {
          const { width: w, height: h } = event.nativeEvent.layout;
          setSize(current => (current && current.width === w && current.height === h ? current : { width: w, height: h }));
        }}
        accessible
        accessibilityLabel="Halo Vault is syncing 1,284 files across 3 devices. Drag to push the light and scrub the sync."
      >
        <StatusBar style="light" />
        <Canvas style={StyleSheet.absoluteFill}>
          <Picture picture={picture} />
        </Canvas>
      </View>
    </GestureDetector>
  );
}

const styles = StyleSheet.create({ screen: { flex: 1, backgroundColor: BACKGROUND_HEX } });
