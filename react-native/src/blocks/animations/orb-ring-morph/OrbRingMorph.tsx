import {
  BlurStyle,
  Canvas,
  PaintStyle,
  Picture,
  Skia,
  type SkCanvas,
  type SkPaint,
  StrokeCap,
  StrokeJoin,
  TileMode,
  createPicture,
  vec,
} from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useState } from 'react';
import { type LayoutChangeEvent, Pressable, StyleSheet, Text, View } from 'react-native';
import Animated, {
  useAnimatedStyle,
  useDerivedValue,
  useFrameCallback,
  useReducedMotion,
  useSharedValue,
} from 'react-native-reanimated';
import { scheduleOnRN } from 'react-native-worklets';

type Props = {
  /** The two lines under the orb. */
  title?: string;
  background?: string;
  /** The ring's gradient, from where each arc starts to where it ends. */
  ringColors?: [string, string, string];
  /** Plays the sequence by itself every LOOP seconds. */
  autoPlay?: boolean;
  /** Called when the card shows during a run started by hand. */
  onReveal?: () => void;
  /** Called when a run started by hand is back at rest. */
  onDone?: () => void;
};

/** autoPlay: one run every LOOP seconds; a run by hand lasts as long. */
export const LOOP = 8;
/** After a touch autoPlay waits this long, and for the run to finish, before it starts over. */
const USER_HOLD = 3;
/** Holding a finger down this long freezes the sequence until it is lifted. */
const HOLD_AFTER = 300;

// The timeline, in seconds from the start of a run. The orb rests still until PULSE_AT.
const PULSE_AT = 0.8;
const PULSE_FOR = 0.8;
const TEXT_IN_AT = 1.0;
const TEXT_IN_FOR = 0.5;
const SHRINK_AT = 1.6;
const SHRINK_FOR = 0.6;
const SPIN_AT = 1.6;
const SPIN_FOR = 0.9;
const HALO_AT = 2.0;
const HALO_FOR = 0.8;
const GROW_AT = 2.2;
const GLASS_FOR = 0.6;
const DOTS_AT = 2.8;
const DOT_STAGGER = 0.07;
const DOT_FLOAT = 1.4;
const RING_AT = 3.0;
const RING_STAGGER = 0.08;
const RING_DRAW = 0.7;
// Seconds for one turn of the ring, and for one breath of its arcs.
const RING_TURN = 4;
const BREATH = 1.6;
const CONTRACT_AT = 4.6;
const CONTRACT_FOR = 0.7;
const TEXT_OUT_AT = 4.6;
const TEXT_OUT_FOR = 0.4;
const DOTS_OUT_AT = 4.6;
const DOTS_OUT_FOR = 0.4;
// The pill that the ring becomes hands over to the card over SWAP seconds before CARD_AT.
const SWAP = 0.15;
const CARD_AT = 5.3;
const TONE_FOR = 0.5;
const ICON_AT = 5.55;
const ICON_FOR = 0.5;
const ICON_DOT_AT = 5.95;
const CARD_OUT_AT = 6.4;
const CARD_OUT_FOR = 0.6;
const ORB_IN_AT = 7.2;
const ORB_IN_FOR = 0.8;

// One spring for the sphere's growth, the card's opening and the icon's dot.
const SPRING_MASS = 1;
const SPRING_STIFFNESS = 170;
const SPRING_DAMPING = 16;

// Sizes in points. The orb's centre sits at ORB_Y of the screen's height, the text's at TEXT_Y.
const ORB_Y = 0.45;
const TEXT_Y = 0.78;
const ORB = 56;
const ORB_SMALL = 28;
const SPHERE = 120;
const PULSE = 0.15;
// The orb is two half discs, slid along their seam by this share of the radius.
const SPLIT = 0.1;
const SEAM = 80;
const HALO = 110;
const RING_RADIUS = 84;
const RING_WIDTH = 6;
const ARC = 70;
const BREATH_AMOUNT = 0.15;
// The ring flattens into a vertical pill PILL_WIDTH wide and 2 × PILL_HALF + PILL_WIDTH tall.
const PILL_HALF = 45;
const PILL_WIDTH = 8;
const CARD = 140;
const CARD_RADIUS = 28;
const ICON_RADIUS = 26;
const ICON_WIDTH = 4;
const ICON_DOT = 5;
const DOT_START = 62;
const TEXT_RISE = 8;
// Blur sigmas of the glows.
const ORB_GLOW = 14;
const RING_GLOW = 6;
const CARD_GLOW = 18;
const DOT_GLOW = 3;

// Each dot: angle in degrees, distance from the centre, diameter, and orbit speed in radians a second.
const DOTS: [number, number, number, number][] = [
  [-70, 100, 3.5, 0.35],
  [-5, 112, 2.5, -0.3],
  [60, 96, 3, 0.32],
  [125, 116, 2, -0.34],
  [190, 104, 4, 0.28],
  [250, 120, 2.5, -0.3],
];

// Under reduced motion the run jumps between still moments, fading through black at each jump.
const STILLS: [number, number][] = [
  [0, 0],
  [1.6, 4.2],
  [4.6, 6.1],
  [6.6, 0],
];
const DIP = 0.2;

const LINE = 20;

const BACKGROUND = '#05060C';
const INK = 'rgba(255,255,255,0.9)';
const HALO_INNER = '#0D1020';
const HALO_OUTER = '#141A36';
const GLOW = '#2F5BFF';
const ORB_LIGHT = ['#8FB3FF', '#2F5BFF', '#1A3FE6'];
const ORB_DARK = ['#5A86FF', '#1F4BFF', '#0B2BC9'];
const GLASS = ['#26305C', '#0C1024', '#05060C'];
const RIM = ['#8FB0FF', '#5F78FF', '#9B6BFF'];
const DOT = '#7F9BFF';
const CARD_START = '#4D7BFF';
const CARD_FILL = '#1A1D2B';
const ICON_DOT_FILL = '#7FA8FF';
const RING: [string, string, string] = ['#3D6BFF', '#9B6BFF', '#FFE3F4'];

function rgb(hex: string): number[] {
  'worklet';
  const n = parseInt(hex.slice(1), 16);
  return [(n >> 16) & 255, (n >> 8) & 255, n & 255];
}

function color(c: number[], alpha: number) {
  'worklet';
  return Float32Array.of(c[0] / 255, c[1] / 255, c[2] / 255, Math.max(0, Math.min(1, alpha)));
}

function mix(a: number[], b: number[], t: number) {
  'worklet';
  return [a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t];
}

function clamp(x: number) {
  'worklet';
  return Math.max(0, Math.min(1, x));
}

function lerp(a: number, b: number, t: number) {
  'worklet';
  return a + (b - a) * t;
}

function easeOut(x: number) {
  'worklet';
  return 1 - Math.pow(1 - x, 3);
}

function easeIn(x: number) {
  'worklet';
  return x * x * x;
}

function easeInOut(x: number) {
  'worklet';
  return x < 0.5 ? 4 * x * x * x : 1 - Math.pow(-2 * x + 2, 3) / 2;
}

/** 0 at both ends, 1 in the middle, flat where it starts and ends. */
function bump(x: number) {
  'worklet';
  return x <= 0 || x >= 1 ? 0 : 0.5 - 0.5 * Math.cos(2 * Math.PI * x);
}

/** A spring from 0 to 1 released at rest, `t` seconds after it starts. */
function spring(t: number) {
  'worklet';
  if (t <= 0) return 0;
  const w0 = Math.sqrt(SPRING_STIFFNESS / SPRING_MASS);
  const zeta = SPRING_DAMPING / (2 * Math.sqrt(SPRING_STIFFNESS * SPRING_MASS));
  const wd = w0 * Math.sqrt(1 - zeta * zeta);
  return 1 - Math.exp(-zeta * w0 * t) * (Math.cos(wd * t) + ((zeta * w0) / wd) * Math.sin(wd * t));
}

/** The moment of the run shown under reduced motion. */
function stillAt(seq: number) {
  'worklet';
  let shown = 0;
  for (let i = 0; i < STILLS.length; i++) if (seq >= STILLS[i][0]) shown = STILLS[i][1];
  return shown;
}

/** The screen's opacity under reduced motion: it fades through black around each jump. */
function dipAt(seq: number) {
  'worklet';
  let alpha = 1;
  for (let i = 1; i < STILLS.length; i++) alpha = Math.min(alpha, clamp(Math.abs(seq - STILLS[i][0]) / DIP));
  return alpha;
}

/** A path through `steps` points of an ellipse's arc, angles in degrees. */
function arcPath(cx: number, cy: number, rx: number, ry: number, from: number, sweep: number, close: boolean) {
  'worklet';
  const path = Skia.Path.Make();
  const steps = 32;
  for (let i = 0; i <= steps; i++) {
    const a = ((from + (sweep * i) / steps) * Math.PI) / 180;
    const x = cx + rx * Math.cos(a);
    const y = cy + ry * Math.sin(a);
    if (i === 0) path.moveTo(x, y);
    else path.lineTo(x, y);
  }
  if (close) path.close();
  return path;
}

function blurred(sigma: number) {
  'worklet';
  const paint = Skia.Paint();
  paint.setAntiAlias(true);
  paint.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, sigma, true));
  return paint;
}

function radial(paint: SkPaint, x: number, y: number, r: number, colors: Float32Array[], stops: number[]) {
  'worklet';
  paint.setShader(Skia.Shader.MakeRadialGradient(vec(x, y), Math.max(r, 0.01), colors, stops, TileMode.Clamp));
}

function linear(paint: SkPaint, x0: number, y0: number, x1: number, y1: number, colors: Float32Array[]) {
  'worklet';
  paint.setShader(Skia.Shader.MakeLinearGradient(vec(x0, y0), vec(x1, y1), colors, null, TileMode.Clamp));
}

/** The orb: two half discs, slid apart along their seam, that merge into a dark glass sphere. */
function drawOrb(canvas: SkCanvas, cx: number, cy: number, d: number, rot: number, glass: number, alpha: number, glow: number) {
  'worklet';
  if (alpha <= 0.001 || d <= 0.5) return;
  const r = d / 2;
  const aura = blurred(ORB_GLOW);
  aura.setColor(color(rgb(GLOW), alpha * (glow * (1 - glass) + 0.3 * glass)));
  canvas.drawCircle(cx, cy, r * 1.15, aura);

  const paint = Skia.Paint();
  paint.setAntiAlias(true);
  if (glass < 1) {
    const seam = SEAM + rot;
    const ux = Math.cos((seam * Math.PI) / 180);
    const uy = Math.sin((seam * Math.PI) / 180);
    const slide = SPLIT * r * (1 - glass);
    const halves = [ORB_LIGHT, ORB_DARK];
    for (let k = 0; k < 2; k++) {
      const sign = k === 0 ? -1 : 1;
      const hx = cx + ux * slide * sign;
      const hy = cy + uy * slide * sign;
      const c = halves[k];
      radial(paint, hx - 0.35 * r, hy - 0.4 * r, 1.3 * r, [color(rgb(c[0]), alpha * (1 - glass)), color(rgb(c[1]), alpha * (1 - glass)), color(rgb(c[2]), alpha * (1 - glass))], [0, 0.6, 1]);
      canvas.drawPath(arcPath(hx, hy, r, r, seam + (k === 0 ? 0 : 180), 180, true), paint);
    }
  }
  if (glass > 0) {
    const a = alpha * glass;
    radial(paint, cx - 0.3 * r, cy - 0.35 * r, 1.25 * r, [color(rgb(GLASS[0]), a), color(rgb(GLASS[1]), a), color(rgb(GLASS[2]), a)], [0, 0.55, 1]);
    canvas.drawCircle(cx, cy, r, paint);
    // Light bouncing up from below, and a small highlight up on the left.
    const bounce = blurred(6);
    bounce.setColor(color(rgb(RING[0]), 0.25 * a));
    canvas.drawOval(Skia.XYWHRect(cx - 0.6 * r, cy + 0.55 * r - 0.18 * r, 1.2 * r, 0.36 * r), bounce);
    const shine = blurred(3);
    shine.setColor(color([255, 255, 255], 0.22 * a));
    canvas.save();
    canvas.translate(cx - 0.38 * r, cy - 0.48 * r);
    canvas.rotate(-35, 0, 0);
    canvas.drawOval(Skia.XYWHRect(-0.22 * r, -0.12 * r, 0.44 * r, 0.24 * r), shine);
    canvas.restore();
    const rim = Skia.Paint();
    rim.setAntiAlias(true);
    rim.setStyle(PaintStyle.Stroke);
    rim.setStrokeWidth(1.5);
    linear(rim, cx - 0.7 * r, cy - 0.7 * r, cx + 0.7 * r, cy + 0.7 * r, [color(rgb(RIM[0]), 0.95 * a), color(rgb(RIM[1]), 0.15 * a), color(rgb(RIM[2]), 0.6 * a)]);
    canvas.drawCircle(cx, cy, r - 0.75, rim);
  }
}

export function OrbRingMorph({
  title = 'Your face,\nis your wallet',
  background = BACKGROUND,
  ringColors = RING,
  autoPlay = false,
  onReveal,
  onDone,
}: Props) {
  const reduced = useReducedMotion();
  const [size, setSize] = useState({ width: 0, height: 0 });
  const ring = [rgb(ringColors[0]), rgb(ringColors[1]), rgb(ringColors[2])];

  // The run's clock in seconds, and what drives it: 0 at rest, 1 for one run by hand, 2 for autoPlay.
  const time = useSharedValue(0);
  const mode = useSharedValue(0);
  const last = useSharedValue(-1);
  const held = useSharedValue(false);
  // A touch is noted here by the press handlers and stamped with the frame's time by the clock.
  const request = useSharedValue(0);
  const touchedAt = useSharedValue(-1e9);

  const revealed = () => {
    if (process.env.EXPO_OS !== 'web') Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Soft).catch(() => {});
    onReveal?.();
  };
  const done = () => onDone?.();

  useFrameCallback((frame) => {
    const now = frame.timestamp;
    const before = last.get();
    last.set(now);
    let dt = before < 0 || held.get() ? 0 : (now - before) / 1000;
    const asked = request.get();
    if (asked !== 0) {
      request.set(0);
      touchedAt.set(now);
      // A tap starts a run unless one by hand is going; a hold during autoPlay finishes its run once.
      if (asked === 1 && mode.get() !== 1) {
        mode.set(1);
        time.set(0);
        dt = 0;
      } else if (asked === 2 && mode.get() === 2) {
        mode.set(1);
      }
    }
    const m = mode.get();
    if (m === 0) {
      if (autoPlay && !held.get() && now - touchedAt.get() >= USER_HOLD * 1000) mode.set(2);
      return;
    }
    const previous = time.get();
    let next = previous + dt;
    if (m === 1 && previous < CARD_AT && next >= CARD_AT) scheduleOnRN(revealed);
    if (m === 2 && next >= LOOP) next -= LOOP;
    if (m === 1 && next >= LOOP) {
      next = 0;
      mode.set(0);
      scheduleOnRN(done);
    }
    time.set(next);
  });

  const { width, height } = size;
  const picture = useDerivedValue(() => {
    const t = reduced ? stillAt(time.get()) : time.get();
    return createPicture((canvas) => {
      if (width <= 0) return;
      const cx = width / 2;
      const cy = height * ORB_Y;

      const contract = easeInOut(clamp((t - CONTRACT_AT) / CONTRACT_FOR));

      // The halo the sphere sits in.
      const halo = easeOut(clamp((t - HALO_AT) / HALO_FOR));
      const haloAlpha = 0.6 * halo * (1 - contract);
      if (haloAlpha > 0.001) {
        const r = HALO * (0.85 + 0.15 * halo) * (1 - 0.1 * contract);
        const fill = Skia.Paint();
        fill.setAntiAlias(true);
        radial(fill, cx, cy, r, [color(rgb(HALO_INNER), haloAlpha), color(rgb(HALO_OUTER), haloAlpha)], [0.4, 1]);
        canvas.drawCircle(cx, cy, r, fill);
        const edge = Skia.Paint();
        edge.setAntiAlias(true);
        edge.setStyle(PaintStyle.Stroke);
        edge.setStrokeWidth(1);
        edge.setColor(color(rgb(GLOW), 0.2 * haloAlpha));
        canvas.drawCircle(cx, cy, r - 0.5, edge);
      }

      // The orb, then the sphere it becomes.
      if (t >= ORB_IN_AT) {
        const back = easeOut(clamp((t - ORB_IN_AT) / ORB_IN_FOR));
        drawOrb(canvas, cx, cy, ORB * (0.6 + 0.4 * back), 0, 0, back, 0.5);
      } else {
        const pulse = bump((t - PULSE_AT) / PULSE_FOR);
        const shrink = easeInOut(clamp((t - SHRINK_AT) / SHRINK_FOR));
        const grow = spring(t - GROW_AT);
        const glass = easeInOut(clamp((t - GROW_AT) / GLASS_FOR));
        const rot = 360 * easeInOut(clamp((t - SPIN_AT) / SPIN_FOR));
        let d = t < GROW_AT ? lerp(ORB, ORB_SMALL, shrink) : lerp(ORB_SMALL, SPHERE, grow);
        d *= (1 - PULSE * pulse) * (1 - 0.35 * contract);
        drawOrb(canvas, cx, cy, d, rot, glass, 1 - contract, 0.5 + 0.3 * pulse);
      }

      // Dots that drift out from the sphere on slow orbits.
      const dotsOut = 1 - easeInOut(clamp((t - DOTS_OUT_AT) / DOTS_OUT_FOR));
      if (t >= DOTS_AT && dotsOut > 0) {
        const glow = blurred(DOT_GLOW);
        const dot = Skia.Paint();
        dot.setAntiAlias(true);
        for (let i = 0; i < DOTS.length; i++) {
          const [angle, distance, diameter, speed] = DOTS[i];
          const f = easeOut(clamp((t - DOTS_AT - i * DOT_STAGGER) / DOT_FLOAT));
          if (f <= 0) continue;
          const a = (angle * Math.PI) / 180 + speed * (t - DOTS_AT);
          const r = lerp(DOT_START, distance, f);
          const alpha = clamp(f * 3) * dotsOut;
          const x = cx + Math.cos(a) * r;
          const y = cy + Math.sin(a) * r;
          glow.setColor(color(rgb(DOT), 0.6 * alpha));
          canvas.drawCircle(x, y, diameter * 0.75, glow);
          dot.setColor(color(rgb(DOT), alpha));
          canvas.drawCircle(x, y, diameter / 2, dot);
        }
      }

      // The ring: four arcs on an ellipse that flattens into a vertical pill.
      const ringAlpha = 1 - clamp((t - (CARD_AT - SWAP)) / SWAP);
      if (t >= RING_AT && ringAlpha > 0) {
        const rx = RING_RADIUS * (1 - contract);
        const ry = lerp(RING_RADIUS, PILL_HALF, contract);
        const turn = (360 * (t - RING_AT)) / RING_TURN;
        const stroke = lerp(RING_WIDTH, PILL_WIDTH, contract);
        const glow = blurred(RING_GLOW);
        glow.setStyle(PaintStyle.Stroke);
        glow.setStrokeCap(StrokeCap.Round);
        glow.setStrokeJoin(StrokeJoin.Round);
        glow.setStrokeWidth(stroke + 4);
        const line = Skia.Paint();
        line.setAntiAlias(true);
        line.setStyle(PaintStyle.Stroke);
        line.setStrokeCap(StrokeCap.Round);
        line.setStrokeJoin(StrokeJoin.Round);
        line.setStrokeWidth(stroke);
        for (let i = 0; i < 4; i++) {
          const drawn = easeOut(clamp((t - RING_AT - i * RING_STAGGER) / RING_DRAW));
          const breath = 1 + BREATH_AMOUNT * Math.sin((2 * Math.PI * (t - RING_AT)) / BREATH + (i * Math.PI) / 2) * (1 - contract);
          const span = lerp(ARC * drawn * breath, 90, contract);
          if (span < 0.5) continue;
          const middle = -90 + i * 90 + turn;
          const from = middle - span / 2;
          const a0 = (from * Math.PI) / 180;
          const a1 = ((from + span) * Math.PI) / 180;
          const x0 = cx + rx * Math.cos(a0);
          const y0 = cy + ry * Math.sin(a0);
          const x1 = cx + rx * Math.cos(a1);
          const y1 = cy + ry * Math.sin(a1);
          const path = arcPath(cx, cy, rx, ry, from, span, false);
          linear(glow, x0, y0, x1, y1, [color(ring[0], 0.55 * ringAlpha), color(ring[1], 0.55 * ringAlpha), color(ring[2], 0.55 * ringAlpha)]);
          canvas.drawPath(path, glow);
          linear(line, x0, y0, x1, y1, [color(ring[0], ringAlpha), color(ring[1], ringAlpha), color(ring[2], ringAlpha)]);
          canvas.drawPath(path, line);
        }
      }

      // The card the pill opens into, with a ring icon drawn in it.
      const cardIn = clamp((t - (CARD_AT - SWAP)) / SWAP);
      const cardOut = easeIn(clamp((t - CARD_OUT_AT) / CARD_OUT_FOR));
      const cardAlpha = cardIn * (1 - cardOut);
      if (cardAlpha > 0.001 && t < ORB_IN_AT) {
        const s = spring(t - CARD_AT);
        const scale = 1 - 0.06 * cardOut;
        const w = lerp(PILL_WIDTH, CARD, s) * scale;
        const h = lerp(PILL_HALF * 2 + PILL_WIDTH, CARD, s) * scale;
        const r = Math.min(lerp(PILL_WIDTH / 2, CARD_RADIUS, clamp(s)) * scale, w / 2, h / 2);
        const tone = easeInOut(clamp((t - CARD_AT) / TONE_FOR));
        const box = Skia.RRectXY(Skia.XYWHRect(cx - w / 2, cy - h / 2, w, h), r, r);
        const glow = blurred(CARD_GLOW);
        glow.setColor(color(rgb(GLOW), cardAlpha * lerp(0.7, 0.22, tone)));
        canvas.drawRRect(box, glow);
        const fill = Skia.Paint();
        fill.setAntiAlias(true);
        fill.setColor(color(mix(rgb(CARD_START), rgb(CARD_FILL), tone), cardAlpha));
        canvas.drawRRect(box, fill);
        const sheen = Skia.Paint();
        sheen.setAntiAlias(true);
        linear(sheen, cx, cy - h / 2, cx, cy, [color([255, 255, 255], 0.08 * cardAlpha), color([255, 255, 255], 0)]);
        canvas.drawRRect(box, sheen);
        const edge = Skia.Paint();
        edge.setAntiAlias(true);
        edge.setStyle(PaintStyle.Stroke);
        edge.setStrokeWidth(1);
        edge.setColor(color([255, 255, 255], 0.08 * cardAlpha * tone));
        canvas.drawRRect(Skia.RRectXY(Skia.XYWHRect(cx - w / 2 + 0.5, cy - h / 2 + 0.5, w - 1, h - 1), r, r), edge);

        const icon = easeInOut(clamp((t - ICON_AT) / ICON_FOR));
        if (icon > 0) {
          const ir = ICON_RADIUS * scale;
          const path = arcPath(cx, cy, ir, ir, -90, 360 * icon, false);
          const iconGlow = blurred(5);
          iconGlow.setStyle(PaintStyle.Stroke);
          iconGlow.setStrokeCap(StrokeCap.Round);
          iconGlow.setStrokeJoin(StrokeJoin.Round);
          iconGlow.setStrokeWidth(ICON_WIDTH + 3);
          linear(iconGlow, cx - ir, cy - ir, cx + ir, cy + ir, [color(ring[0], 0.6 * cardAlpha), color(ring[1], 0.6 * cardAlpha), color(ring[2], 0.6 * cardAlpha)]);
          canvas.drawPath(path, iconGlow);
          const stroke = Skia.Paint();
          stroke.setAntiAlias(true);
          stroke.setStyle(PaintStyle.Stroke);
          stroke.setStrokeCap(StrokeCap.Round);
          stroke.setStrokeJoin(StrokeJoin.Round);
          stroke.setStrokeWidth(ICON_WIDTH);
          linear(stroke, cx - ir, cy - ir, cx + ir, cy + ir, [color(ring[0], cardAlpha), color(ring[1], cardAlpha), color(ring[2], cardAlpha)]);
          canvas.drawPath(path, stroke);
        }
        const pop = spring(t - ICON_DOT_AT);
        if (pop > 0) {
          const dot = Skia.Paint();
          dot.setAntiAlias(true);
          dot.setColor(color(rgb(ICON_DOT_FILL), cardAlpha));
          canvas.drawCircle(cx, cy, ICON_DOT * pop * scale, dot);
        }
      }
    });
  });

  const textStyle = useAnimatedStyle(() => {
    const t = reduced ? stillAt(time.get()) : time.get();
    const shown = easeOut(clamp((t - TEXT_IN_AT) / TEXT_IN_FOR));
    const gone = easeInOut(clamp((t - TEXT_OUT_AT) / TEXT_OUT_FOR));
    return { opacity: shown * (1 - gone), transform: [{ translateY: TEXT_RISE * (1 - shown) }] };
  });
  const dipStyle = useAnimatedStyle(() => ({ opacity: reduced ? dipAt(time.get()) : 1 }));

  const onLayout = (e: LayoutChangeEvent) => {
    const { width: w, height: h } = e.nativeEvent.layout;
    if (w !== width || h !== height) setSize({ width: w, height: h });
  };

  return (
    <View style={[styles.screen, { backgroundColor: background }]} onLayout={onLayout}>
      <StatusBar style="light" />
      <Pressable
        style={StyleSheet.absoluteFill}
        accessibilityRole="button"
        accessibilityLabel="Play the animation"
        accessibilityHint="Hold to pause it"
        delayLongPress={HOLD_AFTER}
        onPress={() => {
          if (process.env.EXPO_OS !== 'web') Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
          request.set(1);
        }}
        onLongPress={() => {
          held.set(true);
          request.set(2);
        }}
        onPressOut={() => {
          if (!held.get()) return;
          held.set(false);
          request.set(2);
        }}
      >
        <Animated.View style={[StyleSheet.absoluteFill, dipStyle]} pointerEvents="none">
          <Canvas style={StyleSheet.absoluteFill}>
            <Picture picture={picture} />
          </Canvas>
          <Animated.View style={[styles.text, { top: height * TEXT_Y - LINE }, textStyle]}>
            <Text style={styles.title} lineBreakStrategyIOS="standard">
              {title}
            </Text>
          </Animated.View>
        </Animated.View>
      </Pressable>
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1 },
  text: { position: 'absolute', left: 32, right: 32, alignItems: 'center' },
  title: {
    color: INK,
    fontSize: 15,
    lineHeight: LINE,
    fontWeight: '600',
    textAlign: 'center',
    includeFontPadding: false,
  },
});
