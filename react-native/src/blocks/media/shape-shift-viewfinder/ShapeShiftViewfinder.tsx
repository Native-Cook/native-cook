import { useFonts } from 'expo-font';
import * as Haptics from 'expo-haptics';
import { LinearGradient as LG } from 'expo-linear-gradient';
import { StatusBar } from 'expo-status-bar';
import { type ReactNode, useEffect, useMemo, useRef, useState } from 'react';
import { type LayoutChangeEvent, Pressable, StyleSheet, Text, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  type SharedValue,
  useAnimatedReaction,
  useAnimatedScrollHandler,
  useAnimatedStyle,
  useDerivedValue,
  useFrameCallback,
  useSharedValue,
  withSpring,
  withTiming,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import {
  AlphaType,
  Blur,
  Canvas,
  Circle,
  ColorType,
  FractalNoise,
  Group,
  ImageShader,
  LinearGradient,
  Path,
  processTransform3d,
  Rect,
  RoundedRect,
  Skia,
  type SkImage,
  type Matrix3,
  type SkPath,
  Text as SkText,
  useFont,
  useImage,
  vec,
} from '@shopify/react-native-skia';
import { SvgXml } from 'react-native-svg';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';

import { useReducedMotionPreference } from '../../../shared/useReducedMotionPreference';
import { deltaE, dynamicTone, lightnessOf, liftTone, shadowTone } from './ssv-color';
import { DRAG_ICON, FLIP_ICON, PINCH_ICON, TAP_ICON } from './ssv-icons';
import {
  clamp,
  easeInOut,
  easeOut,
  ECHOES,
  echoFade,
  FLOOD_DUR,
  floodFill,
  floodFront,
  FLOOD_REACH,
  type Frame,
  frameAt,
  hexOf,
  FRAMINGS,
  IRIS_DUR,
  irisAt,
  lagOf,
  LOOP,
  mix,
  mixFraming,
  mixHex,
  rgbOf,
  scaleHex,
  seg,
  shadeAt,
  shapeAt,
  springStep,
  swayAt,
} from './ssv-motion';
import { createShapeBank, DEFAULT_SHAPES, type Outline, QN, QSTEP, radiiDelayed, restWeights, restWeightsQ } from './ssv-shapes';
import { buildOutline, wallHits } from './ssv-outline';
import { BAND_SPRINGS, C, DYNAMIC_SWATCHES, FONT, G, JELLY, LIMITS, SPRING, WALL } from './ssv-theme';

export { useMorphingOutline } from './ssv-outline';
export type { Outline } from './ssv-shapes';

const SOLID_SWATCHES: string[] = require('./assets/swatches.json');
const FRONT_FRAMING = [0.5, 0.56, 440, 0] as const;
const TAU = Math.PI * 2;
/** The picture sits a little proud of the cut-out (× 1.04), the leaf shadow's image spans 640 pt, the bevel is 14 pt wide. */
const PICTURE_ZOOM = 1.04;
const SHADE_SIZE = 640;
const BEVEL = 14;

/** Normalize native color syntax once at the API boundary. Translucent colors sit over black. */
const normalizeColor = (value: string) => {
  'worklet';
  const c = Skia.Color(value);
  return hexOf(c[0] * c[3] * 255, c[1] * c[3] * 255, c[2] * c[3] * 255);
};


export type ShapeShiftViewfinderProps = {
  /** Plays the 10 s loop until a finger takes over (default true). */
  autoPlay?: boolean;
  /** How much the shape gives under the finger, 0–1 (default 1). */
  jelly?: number;
  shapes?: Readonly<Record<string, Outline>>;
  initialShape?: string;
  source?: Parameters<typeof useImage>[0];
  renderContent?: (size: { w: number; h: number }) => ReactNode;
  scaleLimits?: [number, number];
  background?: 'dynamic' | string;
  dynamicColor?: SharedValue<string>;
  swatches?: string[];
  onShapeChange?: (name: string) => void;
  onColorChange?: (hex: string) => void;
  onCapture?: (e: ViewfinderCapture) => void;
};

export type ViewfinderCapture = { shape: string; path: string; transform: Matrix3; color: string; k: number; rotation: number; x: number; y: number };

const rgba = (hex: string, a: number) => {
  'worklet';
  const c = rgbOf(hex);
  return `rgba(${Math.round(c[0])},${Math.round(c[1])},${Math.round(c[2])},${a})`;
};

/** The stage colour the eye sees: the base with the fronts laid over it by their progress. */
const visibleColour = (f: Frame) => {
  'worklet';
  let c = f.base;
  if (f.f1u >= 0) c = mixHex(c, f.f1c, easeOut(Math.min(f.f1u * 1.4, 1)));
  if (f.f2u >= 0) c = mixHex(c, f.f2c, easeOut(Math.min(f.f2u * 1.4, 1)));
  return c;
};

/** Snapshot the visible crop at press-down, before the iris closes; emit it after the blink. */
const captureOf = (f: Frame, shape: string, bands: number[], cx: number, cy: number): ViewfinderCapture => {
  'worklet';
  const lean = clamp((f.jx / JELLY.perSpeed) * JELLY.leanPerSpeed, -JELLY.leanMax, JELLY.leanMax);
  const rotation = f.rot + lean;
  const scale = f.k * f.press * (1 + 0.03 * clamp(f.lift - 1, 0, 1));
  const raw = f.jx * f.dx + f.jy * f.dy;
  const es = Math.abs(raw) < 0.002 ? 0 : clamp(raw, -JELLY.max, JELLY.max);
  const radii = radiiDelayed(f.w, bands, bands.length / (6 * 192), f.tapA, f.dent / (G.R * Math.max(scale, 0.05)));
  const path = buildOutline(radii, f.iris, f.twist, es, f.dx, f.dy, rotation).toSVGString();
  const a = rotation * Math.PI / 180;
  const c = Math.cos(a) * scale;
  const s = Math.sin(a) * scale;
  const transform: Matrix3 = [c, -s, cx + f.x, s, c, cy + f.y, 0, 0, 1];
  return { shape, path, transform, color: visibleColour(f), k: f.k, rotation: f.rot, x: f.x, y: f.y };
};

/** Reads a plate as a 32 × 32 grid of RGB means (for the Dynamic colour). */
const gridOf = (img: SkImage): number[] => {
  const surface = Skia.Surface.Make(32, 32);
  if (!surface) return [];
  surface.getCanvas().drawImageRect(img, Skia.XYWHRect(0, 0, img.width(), img.height()), Skia.XYWHRect(0, 0, 32, 32), Skia.Paint());
  surface.flush();
  const px = surface.makeImageSnapshot().readPixels(0, 0, { width: 32, height: 32, colorType: ColorType.RGBA_8888, alphaType: AlphaType.Unpremul });
  if (!px) return [];
  const out: number[] = [];
  for (let i = 0; i < 32 * 32; i++) out.push(px[i * 4], px[i * 4 + 1], px[i * 4 + 2]);
  return out;
};

/**
 * A camera whose frame is the product: one shaped cut-out of the live picture on a field of flat colour. Tap and the
 * outline re-forms like jelly, band by band; drag and it squashes with the finger's speed; pinch, twist; the shutter
 * blinks it shut like an iris; and every new stage colour floods outward from the shape's own edge.
 */
export function ShapeShiftViewfinder({ autoPlay = true, jelly = 1, shapes = DEFAULT_SHAPES, initialShape,
  source, renderContent, scaleLimits = [LIMITS.kMin, LIMITS.kMax], background = 'dynamic', dynamicColor,
  swatches, onShapeChange, onColorChange, onCapture }: ShapeShiftViewfinderProps) {
  const bank = useMemo(() => createShapeBank(shapes), [shapes]);
  const names = bank.names;
  const bands = bank.bands;
  const count = bank.count;
  const weightsN = 6 * count;
  const wLen = weightsN * QN; // QN delay copies of the band weights
  const restState = (shape: number) => {
    'worklet';
    return [...restWeightsQ(shape, count), ...new Array(wLen).fill(0)];
  };
  const initial = Math.max(0, names.indexOf(initialShape ?? names[0]));
  const kMin = Math.max(0.1, Math.min(scaleLimits[0], scaleLimits[1]));
  const kMax = Math.max(kMin, Math.max(scaleLimits[0], scaleLimits[1]));
  const colors = useMemo(() => (swatches?.length ? swatches : SOLID_SWATCHES).map(normalizeColor), [swatches]);
  const restColor = background === 'dynamic' ? C.teal : normalizeColor(background);
  const systemReduced = useReducedMotionPreference();
  const [reducedOverride, setReducedOverride] = useState<boolean | null>(null);
  const reduced = reducedOverride ?? systemReduced;
  const insets = useSafeAreaInsets();
  const [size, setSize] = useState<{ w: number; h: number } | null>(null);
  const [viewport, setViewport] = useState<{ w: number; h: number } | null>(null);
  const [fontsOk] = useFonts({
    [FONT.semibold]: require('./assets/fonts/Inter-SemiBold.ttf'),
    [FONT.medium]: require('./assets/fonts/Inter-Medium.ttf'),
  });
  const chipFont = useFont(require('./assets/fonts/Inter-SemiBold.ttf'), 16);
  const plate = useImage(source ?? require('./assets/plate.jpg'));
  const plateFront = useImage(require('./assets/plate-front.jpg'));
  const shade = useImage(require('./assets/ssv-shade.png'));
  const [solidMode, setSolidMode] = useState(false);
  const [inkDark, setInkDark] = useState(false);

  const W = size?.w ?? 402;
  const H = size?.h ?? 874;
  const stageTop = Math.max(insets.top, 20) + 3;
  const bottom = Math.max(insets.bottom, 12);
  // Keep space for the resting outline, hints, and controls when the viewport is wider than the reference.
  const minStageHeight = 450;
  const S = Math.min(W / 402, Math.max(1, H - stageTop - bottom) / (G.ctrlFromBottom + G.stageGapBelow + minStageHeight));
  const offsetX = (W - 402 * S) / 2;
  const ctrlY = H - bottom - G.ctrlFromBottom * S;
  const stageH = ctrlY - G.stageGapBelow * S - stageTop;
  const SH = stageH / S; // stage height in stage units
  const CX = G.restX;
  const CY = SH / 2;
  const sheetTop = H - Math.max(insets.bottom, 12) + G.sheet.bottomGap * S - G.sheet.h * S;
  const sheetTravel = H - sheetTop + 12;

  // ---- clocks ----
  const T = useSharedValue(0);
  const auto = useSharedValue(autoPlay && !reduced ? 1 : 0);
  const pinned = useSharedValue(0);
  const rate = useSharedValue(1);
  const now = useSharedValue(0);
  const touching = useSharedValue(0);
  const lastTouch = useSharedValue(0);
  const returning = useSharedValue(0);
  const retStart = useSharedValue(0);
  const tick = useSharedValue(0);
  const stats = useSharedValue([0, 0, 0]); // dev: callback count, elapsed ms, longest interval

  // ---- live state ----
  const Wt = useSharedValue<number[]>(restState(initial));
  // per delay copy: its target shape, the target a tap has queued for it, and when that takes effect
  const TQ = useSharedValue<number[]>([...new Array(QN).fill(initial), ...new Array(QN).fill(-1), ...new Array(QN).fill(0)]);
  const tapA = useSharedValue(0);
  const dentV = useSharedValue(0);
  const ringsV = useSharedValue<number[]>([0, 0, 1, 0, 0, 1]);
  const ringFade = useSharedValue(1);
  const target = useSharedValue(initial);
  const arrived = useSharedValue(1);
  const px = useSharedValue(0);
  const py = useSharedValue(0);
  const k = useSharedValue(clamp(1, kMin, kMax));
  const rot = useSharedValue(0);
  const lift = useSharedValue(0);
  const press = useSharedValue(1);
  const flip = useSharedValue(0);
  const flipTarget = useSharedValue(0);
  const J = useSharedValue<number[]>([0, 0, 0, 0, 1, 0, 0, 0]); // jx, jy, vjx, vjy, dx, dy, filtered vx, vy
  const prevXY = useSharedValue<number[]>([0, 0]);
  const fr0 = useSharedValue<number[]>([...FRAMINGS.A]);
  const frRet = useSharedValue(0);
  const swT = useSharedValue(0);
  const swayAmp = useSharedValue(1);
  const wallOn = useSharedValue(0);
  const irisT = useSharedValue(-1);
  const holding = useSharedValue(0);
  const irisBuzz = useSharedValue(0);
  const pendingCapture = useSharedValue<ViewfinderCapture | null>(null);
  const events = useRef<{ capture?: ViewfinderCapture; shape?: string; color?: string }>({});
  const base = useSharedValue<string>(restColor);
  const f1c = useSharedValue<string>(restColor);
  const f1t = useSharedValue(-1);
  const f2c = useSharedValue<string>(restColor);
  const f2t = useSharedValue(-1);
  const sheet = useSharedValue(0);
  const segv = useSharedValue(0);
  const ring = useSharedValue(1);
  const dring = useSharedValue(0);
  const pressIdx = useSharedValue(-1);
  const pressS = useSharedValue(1);
  const h0 = useSharedValue(1);
  const h1 = useSharedValue(0);
  const h2 = useSharedValue(0);
  const hintStage = useSharedValue(0);
  const dynAuto = useSharedValue(background === 'dynamic' ? 1 : 0);
  const interacted = useSharedValue(0);
  const dynCand = useSharedValue<string>(restColor);
  const dynSince = useSharedValue(0);
  const xf = useSharedValue(1);
  const prevShape = useSharedValue(initial);
  const gridBack = useSharedValue<number[]>([]);
  const gridFront = useSharedValue<number[]>([]);
  // gesture scratch
  const grab = useSharedValue(0);
  const x0 = useSharedValue(0);
  const y0 = useSharedValue(0);
  const k0 = useSharedValue(1);
  const r0 = useSharedValue(0);
  const atLimit = useSharedValue(0);
  const pinchAnchor = useSharedValue([0, 0]);
  const pinchShift = useSharedValue([0, 0]);
  const sheet0 = useSharedValue(1);

  useEffect(() => {
    auto.set(autoPlay && !reduced ? 1 : 0);
  }, [autoPlay, reduced, auto]);

  useEffect(() => {
    if (plate) gridBack.set(gridOf(plate));
  }, [plate, gridBack]);
  useEffect(() => {
    if (plateFront) gridFront.set(gridOf(plateFront));
  }, [plateFront, gridFront]);

  useEffect(() => {
    target.set(initial);
    Wt.set(restState(initial));
    TQ.set([...new Array(QN).fill(initial), ...new Array(QN).fill(-1), ...new Array(QN).fill(0)]);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [bank, initial, count, weightsN, target, Wt, TQ]);

  // ---- haptics and callbacks (JS) ----
  const buzz = (kind: 'soft' | 'light' | 'medium' | 'rigid' | 'sel') => {
    if (kind === 'sel') Haptics.selectionAsync().catch(() => {});
    else {
      const style =
        kind === 'soft'
          ? Haptics.ImpactFeedbackStyle.Soft
          : kind === 'light'
            ? Haptics.ImpactFeedbackStyle.Light
            : kind === 'medium'
              ? Haptics.ImpactFeedbackStyle.Medium
              : Haptics.ImpactFeedbackStyle.Rigid;
      Haptics.impactAsync(style).catch(() => {});
    }
  };
  const tellShape = (i: number) => { events.current.shape = names[i]; onShapeChange?.(names[i]); };
  const tellColour = (hex: string) => { events.current.color = hex; onColorChange?.(hex); };
  const tellCapture = (capture: ViewfinderCapture) => {
    events.current.capture = capture;
    onCapture?.(capture);
  };

  // ---- worklet helpers (declared before their callers) ----
  const latestColour = () => {
    'worklet';
    return f2t.get() >= 0 ? f2c.get() : f1t.get() >= 0 ? f1c.get() : base.get();
  };
  const startFlood = (color: string) => {
    'worklet';
    const hex = normalizeColor(color);
    if (deltaE(hex, latestColour()) < 0.5) return;
    if (f2t.get() >= 0) {
      // at most two fronts: the older one completes at once
      base.set(f1c.get());
      f1c.set(f2c.get());
      f1t.set(f2t.get());
      f2c.set(hex);
      f2t.set(0);
    } else if (f1t.get() >= 0) {
      f2c.set(hex);
      f2t.set(0);
    } else {
      f1c.set(hex);
      f1t.set(0);
    }
    scheduleOnRN(tellColour, hex);
  };
  const glide = { mass: 1, damping: SPRING.glide.d, stiffness: SPRING.glide.k };
  const snap = { mass: 1, damping: SPRING.snap.d, stiffness: SPRING.snap.k };
  const pop = { mass: 1, damping: SPRING.pop.d, stiffness: SPRING.pop.k };

  const takeOver = () => {
    'worklet';
    interacted.set(1);
    if (auto.get() === 1 || pinned.get() === 1) {
      const t = T.get();
      const f = frameAt(t, count, initial);
      if (background !== 'dynamic') { f.base = restColor; f.dot = restColor; f.f1u = -1; f.f2u = -1; }
      f.k = clamp(f.k, kMin, kMax);
      const g = frameAt(t - 0.004, count, initial);
      const w: number[] = [];
      for (let i = 0; i < wLen; i++) w.push(f.w[i]);
      for (let i = 0; i < wLen; i++) w.push((f.w[i] - g.w[i]) / 0.004);
      Wt.set(w);
      const tq: number[] = [];
      for (let q = 0; q < QN; q++) tq.push(shapeAt(t - q * QSTEP, count, initial));
      for (let q = 0; q < 2 * QN; q++) tq.push(q < QN ? -1 : 0);
      TQ.set(tq);
      tapA.set(f.tapA);
      dentV.set(f.dent / 10);
      dentV.set(withSpring(0, snap));
      ringsV.set(f.rings);
      ringFade.set(1);
      ringFade.set(withTiming(0, { duration: 240 }));
      target.set(shapeAt(t, count, initial));
      arrived.set(1);
      px.set(f.x);
      py.set(f.y);
      prevXY.set([f.x, f.y]);
      k.set(f.k);
      rot.set(f.rot);
      lift.set(f.lift);
      lift.set(withSpring(0, glide));
      press.set(f.press);
      press.set(withSpring(1, pop));
      flip.set(0);
      flipTarget.set(0);
      J.set([f.jx, f.jy, (f.jx - g.jx) / 0.004, (f.jy - g.jy) / 0.004, f.dx, f.dy, (f.x - g.x) / 0.004, (f.y - g.y) / 0.004]);
      fr0.set([f.fr[0], f.fr[1], f.fr[2], f.fr[3]]);
      frRet.set(0);
      swT.set(t);
      swayAmp.set(1);
      irisT.set(t >= 8.3 && t < 8.3 + IRIS_DUR ? t - 8.3 : -1);
      irisBuzz.set(2);
      base.set(f.base);
      f1c.set(f.f1c);
      f1t.set(f.f1u >= 0 ? f.f1u * FLOOD_DUR : -1);
      f2t.set(-1);
      sheet.set(f.sheet);
      if (f.sheet > 0.002) sheet.set(withSpring(t >= 6.5 && t < 7.6 ? 1 : 0, glide));
      segv.set(f.seg);
      segv.set(withSpring(f.seg > 0.5 ? 1 : 0, snap));
      ring.set(f.ring);
      ring.set(withSpring(Math.round(f.ring), snap));
      dring.set(0);
      dynAuto.set(background === 'dynamic' && f.seg <= 0.5 ? 1 : 0);
      scheduleOnRN(setSolidMode, f.seg > 0.5);
      dynCand.set(f.base);
      pressIdx.set(-1);
      pressS.set(1);
      h0.set(f.hint[0]);
      h1.set(f.hint[1]);
      h2.set(f.hint[2]);
      auto.set(0);
      pinned.set(0);
    }
    if (returning.get() === 1) {
      returning.set(0);
      swayAmp.set(withTiming(1, { duration: 400 }));
    }
    touching.set(touching.get() + 1);
    lastTouch.set(now.get());
  };
  const release = () => {
    'worklet';
    touching.set(Math.max(0, touching.get() - 1));
    lastTouch.set(now.get());
  };
  const advanceHint = (done: number) => {
    'worklet';
    if (hintStage.get() !== done) return;
    const next = done + 1;
    hintStage.set(next);
    const fade = { duration: 200 };
    h0.set(withTiming(0, fade));
    h1.set(withTiming(next === 1 ? 1 : 0, fade));
    h2.set(withTiming(next === 2 ? 1 : 0, fade));
  };
  /** The next shape, started where the finger is: `ang` is the tap's angle on the rim (rad, clockwise from the top). */
  const nextShape = (ang: number) => {
    'worklet';
    const from = target.get();
    const to = (from + 1) % count;
    target.set(to);
    arrived.set(0);
    tapA.set(ang);
    if (reduced) {
      // reduced motion: the masks cross-fade in 200 ms, no morph
      prevShape.set(from);
      Wt.set(restState(to));
      TQ.set([...new Array(QN).fill(to), ...new Array(QN).fill(-1), ...new Array(QN).fill(0)]);
      xf.set(0);
      xf.set(withTiming(1, { duration: 200 }));
      return;
    }
    // each delay copy takes the new target QSTEP after the one before it
    const t0 = now.get();
    TQ.modify((a) => {
      'worklet';
      for (let q = 0; q < QN; q++) {
        a[QN + q] = to;
        a[2 * QN + q] = t0 + q * QSTEP;
      }
      return a;
    });
  };
  const openSheet = (open: boolean) => {
    'worklet';
    sheet.set(withSpring(open ? 1 : 0, glide));
  };
  const goHome = () => {
    'worklet';
    // 2 s after the last touch: everything eases to the loop's first frame
    const g600 = { duration: 600 };
    px.set(withSpring(0, glide));
    py.set(withSpring(0, glide));
    k.set(withSpring(clamp(1, kMin, kMax), glide));
    rot.set(withSpring(0, glide));
    lift.set(withSpring(0, glide));
    press.set(withSpring(1, glide));
    flipTarget.set(0);
    flip.set(withTiming(0, g600));
    sheet.set(withSpring(0, glide));
    segv.set(withSpring(0, snap));
    ring.set(withSpring(1, snap));
    dring.set(withSpring(0, snap));
    frRet.set(withTiming(1, g600));
    swayAmp.set(withTiming(0, g600));
    h0.set(withTiming(1, g600));
    h1.set(withTiming(0, g600));
    h2.set(withTiming(0, g600));
    if (target.get() !== initial) {
      target.set(initial);
      arrived.set(1);
      if (reduced) Wt.set(restState(initial));
      TQ.set([...new Array(QN).fill(initial), ...new Array(QN).fill(-1), ...new Array(QN).fill(0)]);
    }
    interacted.set(0);
    dynAuto.set(background === 'dynamic' ? 1 : 0);
    if (deltaE(latestColour(), restColor) > 0.5) startFlood(restColor);
  };
  const settled = () => {
    'worklet';
    const w = Wt.get();
    const r = restWeights(initial, count);
    for (let i = 0; i < wLen; i++) if (Math.abs(w[i] - r[i % weightsN]) > 0.003 || Math.abs(w[wLen + i]) > 0.05) return false;
    const j = J.get();
    return (
      Math.abs(px.get()) < 0.3 &&
      Math.abs(py.get()) < 0.3 &&
      Math.abs(k.get() - clamp(1, kMin, kMax)) < 0.003 &&
      Math.abs(rot.get()) < 0.2 &&
      Math.abs(j[0]) + Math.abs(j[1]) < 0.002 &&
      f1t.get() < 0 &&
      f2t.get() < 0 &&
      sheet.get() < 0.002 &&
      flip.get() < 0.002 &&
      lift.get() < 0.01 &&
      Math.abs(press.get() - 1) < 0.002 &&
      frRet.get() > 0.999 &&
      swayAmp.get() < 0.001
    );
  };

  useEffect(() => {
    if (!reduced) return;
    auto.set(0); pinned.set(0); T.set(0); interacted.set(0);
    target.set(initial); Wt.set(restState(initial));
    px.set(0); py.set(0); k.set(clamp(1, kMin, kMax)); rot.set(0);
    lift.set(0); press.set(1); flip.set(0); flipTarget.set(0); J.set([0, 0, 0, 0, 1, 0, 0, 0]);
    swT.set(0); swayAmp.set(0); irisT.set(-1); holding.set(0);
    base.set(restColor); f1t.set(-1); f2t.set(-1); sheet.set(0); segv.set(0);
    h0.set(1); h1.set(0); h2.set(0); returning.set(0); touching.set(0);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [reduced, initial, count, weightsN, kMin, kMax, restColor, auto, pinned, T, interacted,
    target, Wt, px, py, k, rot, lift, press, flip, flipTarget, J, swT, swayAmp, irisT, holding,
    base, f1t, f2t, sheet, segv, h0, h1, h2, returning, touching]);

  // ---- geometry helpers (the jelly's stretch and lean, the stage's wall) ----
  const esOf = (f: Frame) => {
    'worklet';
    const raw = f.jx * f.dx + f.jy * f.dy;
    return Math.abs(raw) < 0.002 ? 0 : clamp(raw, -JELLY.max, JELLY.max);
  };
  const leanOf = (f: Frame) => {
    'worklet';
    return clamp((f.jx / JELLY.perSpeed) * JELLY.leanPerSpeed, -JELLY.leanMax, JELLY.leanMax);
  };
  const scaleOf = (f: Frame) => {
    'worklet';
    return f.k * f.press * (1 + 0.03 * clamp(f.lift - 1, 0, 1));
  };
  const wallOf = (f: Frame) => {
    'worklet';
    return [CX + f.x, CY + f.y, scaleOf(f), G.stageW, SH, WALL.inset, G.stageR - WALL.inset];
  };

  // ---- the frame being drawn ----
  const Fv = useDerivedValue<Frame>(() => {
    if (auto.get() === 1) {
      const f = frameAt(T.get(), count, initial);
      f.k = clamp(f.k, kMin, kMax);
      f.ring = clamp(f.ring, 0, colors.length - 1);
      if (background !== 'dynamic') return { ...f, base: restColor, dot: restColor, f1u: -1, f2u: -1 };
      return f;
    }
    const w = Wt.get();
    const j = J.get();
    const fr = mixFraming(fr0.get(), FRAMINGS.A, easeInOut(frRet.get()));
    const sw = swayAt(swT.get());
    const amp = reduced ? 0 : swayAmp.get();
    const sh = shadeAt(swT.get());
    const sh0 = shadeAt(0);
    const lag = reduced ? [0, 0] : lagOf(j[6], j[7]);
    const ir = irisT.get() >= 0 ? (reduced ? [1, 0, 0, 0.6, 1, 0, 0] : irisAt(irisT.get())) : [1, 0, 0, 1, 1, 0, 0];
    const fd = reduced ? 0.2 : FLOOD_DUR;
    const rg = ringsV.get();
    const f: Frame = {
      w: w.slice(0, wLen),
      tapA: tapA.get(),
      dent: dentV.get() * 10,
      rings: [rg[0] * ringFade.get(), rg[1], rg[2], rg[3] * ringFade.get(), rg[4], rg[5]],
      press: press.get(),
      x: px.get() + pinchShift.get()[0],
      y: py.get() + pinchShift.get()[1],
      k: k.get(),
      rot: rot.get(),
      jx: j[0],
      jy: j[1],
      dx: j[4],
      dy: j[5],
      lift: lift.get(),
      fr,
      sx: sw[0] * amp,
      sy: sw[1] * amp,
      sr: sw[2] * amp,
      lx: lag[0],
      ly: lag[1],
      shx: mix(sh0[0], sh[0], amp),
      shy: mix(sh0[1], sh[1], amp),
      shr: mix(sh0[2], sh[2], amp),
      flip: flip.get(),
      iris: ir[0],
      twist: ir[1],
      flash: ir[2],
      blinkD: ir[5],
      blinkA: ir[6],
      dim: ir[3],
      disc: ir[4],
      base: base.get(),
      f1c: f1c.get(),
      f1u: f1t.get() >= 0 ? f1t.get() / fd : -1,
      f2c: f2c.get(),
      f2u: f2t.get() >= 0 ? f2t.get() / fd : -1,
      dot: C.teal,
      sheet: sheet.get(),
      seg: segv.get(),
      ring: ring.get(),
      dring: dring.get(),
      pressIdx: pressIdx.get(),
      pressS: pressS.get(),
      hint: [h0.get(), h1.get(), h2.get()],
    };
    f.dot = visibleColour(f);
    return f;
  });

  // ---- the clock and the live simulation ----
  useFrameCallback((info) => {
    const raw = (info.timeSincePreviousFrame ?? 16) / 1000;
    if (__DEV__) stats.modify((v) => { 'worklet'; v[0]++; v[1] += raw * 1000; v[2] = Math.max(v[2], raw * 1000); return v; });
    const dt = Math.max(Math.min(raw, rate.get() < 1 ? 1 : 0.05) * rate.get(), 0.0005);
    now.set(now.get() + dt);
    if (pinned.get() === 1) return;
    if (auto.get() === 1) {
      T.set((T.get() + dt) % LOOP);
      return;
    }

    // band springs (one exact step per band and delay copy; a retarget keeps every band's velocity). A tap queues the new
    // target on each copy, QSTEP later than the one before it, so the morph runs outward from the tap.
    const tg = target.get();
    if (!reduced) {
      const clock = now.get();
      TQ.modify((a) => {
        'worklet';
        for (let q = 0; q < QN; q++) {
          if (a[QN + q] >= 0 && clock >= a[2 * QN + q]) {
            a[q] = a[QN + q];
            a[QN + q] = -1;
          }
        }
        return a;
      });
      const targets = TQ.get();
      Wt.modify((a) => {
        'worklet';
        for (let q = 0; q < QN; q++) {
          const at = q * weightsN;
          for (let b = 0; b < 6; b++) {
            const sp = BAND_SPRINGS[b];
            for (let s = 0; s < count; s++) {
              const i = at + b * count + s;
              const goal = s === targets[q] ? 1 : 0;
              const r = springStep(a[i] - goal, a[wLen + i], sp.d, sp.k, dt);
              a[i] = r[0] + goal;
              a[wLen + i] = r[1];
            }
          }
        }
        return a;
      });
    }
    if (arrived.get() === 0 && Wt.get()[tg] > 0.95) {
      arrived.set(1);
      scheduleOnRN(buzz, 'light');
      scheduleOnRN(tellShape, tg);
    }

    // jelly: a spring that follows the centre's velocity (low-passed 40 ms)
    const x = px.get() + pinchShift.get()[0];
    const y = py.get() + pinchShift.get()[1];
    const p = prevXY.get();
    const vx = (x - p[0]) / dt;
    const vy = (y - p[1]) / dt;
    prevXY.set([x, y]);
    const amt = reduced ? 0 : clamp(jelly, 0, 1);
    J.modify((j) => {
      'worklet';
      const a = 1 - Math.exp(-dt / 0.04);
      j[6] += (vx - j[6]) * a;
      j[7] += (vy - j[7]) * a;
      let tx = j[6] * JELLY.perSpeed * amt;
      let ty = j[7] * JELLY.perSpeed * amt;
      const m = Math.hypot(tx, ty);
      const mx = JELLY.max * Math.max(amt, 0.0001);
      if (m > mx) {
        tx = (tx / m) * mx;
        ty = (ty / m) * mx;
      }
      if (m > 0.01) {
        const directionLength = Math.hypot(tx, ty) || 1;
        j[4] = tx / directionLength;
        j[5] = ty / directionLength;
      }
      const h = Math.min(dt, 0.05) / 4;
      for (let s = 0; s < 4; s++) {
        j[2] += (JELLY.k * (tx - j[0]) - JELLY.d * j[2]) * h;
        j[3] += (JELLY.k * (ty - j[1]) - JELLY.d * j[3]) * h;
        j[0] += j[2] * h;
        j[1] += j[3] * h;
      }
      if (amt === 0) {
        j[0] = 0;
        j[1] = 0;
        j[2] = 0;
        j[3] = 0;
      }
      return j;
    });
    if (!reduced) swT.set(swT.get() + dt);

    // the stage's wall: a haptic when the outline first touches it (Rigid above 900 pt/s, Medium below)
    if (!reduced) {
      const F1 = Fv.get();
      const touch = wallHits(radiiDelayed(F1.w, bands, count, F1.tapA, 0), F1.iris, F1.twist, esOf(F1), F1.dx, F1.dy, F1.rot + leanOf(F1), wallOf(F1)) > 0;
      if (touch && wallOn.get() === 0) {
        wallOn.set(1);
        scheduleOnRN(buzz, Math.hypot(J.get()[6], J.get()[7]) > 900 ? 'rigid' : 'medium');
      } else if (!touch && wallOn.get() === 1) wallOn.set(0);
    }

    // the iris blink (press-and-hold keeps it shut)
    if (irisT.get() >= 0) {
      let s = irisT.get() + dt;
      if (holding.get() === 1 && s > 0.14) s = 0.14;
      if (s >= 0.11 && irisBuzz.get() === 0) {
        irisBuzz.set(1);
        scheduleOnRN(buzz, 'medium');
      }
      if (s >= 0.17 && irisBuzz.get() === 1) {
        irisBuzz.set(2);
        scheduleOnRN(buzz, 'light');
      }
      if (s > (reduced ? 0.12 : IRIS_DUR)) {
        irisT.set(-1);
        if (reduced) scheduleOnRN(buzz, 'light');
        const capture = pendingCapture.get();
        if (capture) scheduleOnRN(tellCapture, capture);
        pendingCapture.set(null);
      } else irisT.set(s);
    }

    // colour fronts
    const fd = reduced ? 0.2 : FLOOD_DUR;
    if (f2t.get() >= 0) f2t.set(f2t.get() + dt);
    if (f1t.get() >= 0) {
      const u = f1t.get() + dt;
      if (u >= fd) {
        base.set(f1c.get());
        if (f2t.get() >= 0) {
          f1c.set(f2c.get());
          f1t.set(f2t.get());
          f2t.set(-1);
        } else f1t.set(-1);
      } else f1t.set(u);
    }

    // Dynamic colour: the mean of the picture under the outline, every 4th frame
    tick.set(tick.get() + 1);
    if (returning.get() === 0 && (interacted.get() === 1 || !!dynamicColor) && dynAuto.get() === 1 && segv.get() < 0.5 && tick.get() % 4 === 0) {
      const front = flip.get() > 0.5;
      const grid = front ? gridFront.get() : gridBack.get();
      if (dynamicColor) {
        const tone = normalizeColor(dynamicColor.get());
        if (deltaE(tone, latestColour()) > 8) {
          if (deltaE(tone, dynCand.get()) > 4) { dynCand.set(tone); dynSince.set(now.get()); }
          else if (now.get() - dynSince.get() > 0.25) startFlood(tone);
        }
      } else if (grid.length === 3072) {
        const F0 = Fv.get();
        const fr = front ? FRONT_FRAMING : F0.fr;
        const radii = radiiDelayed(F0.w, bands, count, F0.tapA, 0);
        const ang = ((fr[3] + F0.sr) * Math.PI) / 180;
        const ca = Math.cos(ang);
        const sa = Math.sin(ang);
        const fs = Math.cos(Math.PI * F0.flip) < 0 ? -1 : 1;
        let sr = 0;
        let sg = 0;
        let sb = 0;
        let cnt = 0;
        for (let gy = 0; gy < 32; gy++) {
          for (let gx = 0; gx < 32; gx++) {
            const ux = ((gx + 0.5) / 32 - fr[0]) * fr[2];
            const uy = ((gy + 0.5) / 32 - fr[1]) * fr[2];
            const lx = (ux * ca - uy * sa + F0.sx) * fs;
            const ly = ux * sa + uy * ca + F0.sy;
            const rho = Math.hypot(lx, ly);
            let ph = Math.atan2(lx, -ly) / TAU;
            ph = ph - Math.floor(ph);
            const rr = radii[Math.floor(ph * 192) % 192] * G.R;
            if (rho <= rr) {
              const o = (gy * 32 + gx) * 3;
              sr += grid[o];
              sg += grid[o + 1];
              sb += grid[o + 2];
              cnt++;
            }
          }
        }
        if (cnt > 8) {
          const tone = dynamicTone(sr / cnt, sg / cnt, sb / cnt);
          if (deltaE(tone, latestColour()) > 8) {
            if (deltaE(tone, dynCand.get()) > 4) {
              dynCand.set(tone);
              dynSince.set(now.get());
            } else if (now.get() - dynSince.get() > 0.25) {
              startFlood(tone);
            }
          } else dynCand.set(tone);
        }
      }
    }

    // 2 s after the last touch: ease to rest, then the loop restarts at t = 0
    if (!reduced && autoPlay && touching.get() === 0 && irisT.get() < 0 && now.get() - lastTouch.get() > 2) {
      if (returning.get() === 0) {
        returning.set(1);
        retStart.set(now.get());
        goHome();
      } else if (now.get() - retStart.get() > 0.6 && settled()) {
        returning.set(0);
        scheduleOnRN(setSolidMode, false);
        base.set(restColor);
        T.set(0);
        auto.set(1);
      }
    }
  });

  // ---- geometry ----
  const scale = useDerivedValue(() => scaleOf(Fv.get()));
  const radii = useDerivedValue(() => {
    const f = Fv.get();
    return radiiDelayed(f.w, bands, count, f.tapA, f.dent / (G.R * Math.max(scale.get(), 0.05)));
  });
  const es = useDerivedValue(() => esOf(Fv.get()));
  const lean = useDerivedValue(() => leanOf(Fv.get()));
  const outline = useDerivedValue(() => {
    const f = Fv.get();
    return buildOutline(radii.get(), f.iris, f.twist, es.get(), f.dx, f.dy, f.rot + lean.get(), wallOf(f));
  });
  const prevOutline = useDerivedValue(() => {
    if (!reduced || xf.get() >= 1) return Skia.PathBuilder.Make().detach();
    return buildOutline(radiiDelayed(restWeightsQ(prevShape.get(), count), bands, count, 0, 0), 1, 0, 0, 1, 0, 0);
  });
  const matrixAt = (f: Frame, leanDeg: number, s: number, dy: number) => {
    'worklet';
    return processTransform3d([{ translateX: CX + f.x }, { translateY: CY + f.y + dy }, { rotate: ((f.rot + leanDeg) * Math.PI) / 180 }, { scale: s }]);
  };
  // Reanimated finds a derived value's inputs in its own closure: read Fv, lean and scale here, not inside matrixAt
  // (a bare `() => matrixAt(0)` has no inputs and never recomputes)
  const shapeM = useDerivedValue(() => matrixAt(Fv.get(), lean.get(), scale.get(), 0));

  // the cut-out's body: always-on shadows [0 2 5 α .24] and [0 14 30 α .16] (blur radius = 2σ), tinted with the stage colour
  // at 25 % lightness; a pinch or drag lifts the long one further
  const shadowTint = useDerivedValue(() => shadowTone(Fv.get().dot));
  const contactM = useDerivedValue(() => matrixAt(Fv.get(), lean.get(), scale.get(), 2));
  const contactBlur = useDerivedValue(() => 2.5 / scale.get());
  const liftK = useDerivedValue(() => clamp(Fv.get().lift, 0, 2) / 2);
  const ambA = useDerivedValue(() => mix(0.16, 0.22, liftK.get()));
  const ambM = useDerivedValue(() => matrixAt(Fv.get(), lean.get(), scale.get(), mix(14, 28, liftK.get())));
  const ambBlur = useDerivedValue(() => mix(15, 20, liftK.get()) / scale.get());

  // picture
  // the picture as an image shader: one matrix from image pixels to the shape's coordinates (framing ×1.04, sway, the
  // trail behind a moving outline, flip)
  const backM = useDerivedValue(() => {
    const f = Fv.get();
    const iw = plate ? plate.width() : 2048;
    const sz = f.fr[2] * PICTURE_ZOOM;
    return processTransform3d([
      { translateX: f.sx + f.lx },
      { translateY: f.sy + f.ly },
      { rotate: ((f.fr[3] + f.sr) * Math.PI) / 180 },
      { scaleX: Math.max(Math.abs(Math.cos(Math.PI * f.flip)), 0.002) },
      { translateX: -f.fr[0] * sz },
      { translateY: -f.fr[1] * sz },
      { scale: sz / iw },
    ]);
  });
  const frontM = useDerivedValue(() => {
    const f = Fv.get();
    const iw = plateFront ? plateFront.width() : 1536;
    const fs = FRONT_FRAMING[2] * PICTURE_ZOOM;
    return processTransform3d([
      { translateX: f.sx + f.lx },
      { translateY: f.sy + f.ly },
      { rotate: (f.sr * Math.PI) / 180 },
      { scaleX: Math.max(Math.abs(Math.cos(Math.PI * f.flip)), 0.002) },
      { translateX: -FRONT_FRAMING[0] * fs },
      { translateY: -FRONT_FRAMING[1] * fs },
      { scale: fs / iw },
    ]);
  });
  // the leaf shadow: multiplied over the picture inside the clip, drifting ±26 pt and ±2.5°
  const shadeM = useDerivedValue(() => {
    const f = Fv.get();
    const iw = shade ? shade.width() : 2048;
    return processTransform3d([
      { translateX: f.shx },
      { translateY: f.shy },
      { rotate: (f.shr * Math.PI) / 180 },
      { translateX: -SHADE_SIZE / 2 },
      { translateY: -SHADE_SIZE / 2 },
      { scale: SHADE_SIZE / iw },
    ]);
  });
  const backA = useDerivedValue<number>(() => (Fv.get().flip < 0.5 ? 1 : 0));
  const frontA = useDerivedValue<number>(() => (Fv.get().flip >= 0.5 ? 1 : 0));
  const dimA = useDerivedValue(() => 1 - Fv.get().dim);
  const flashA = useDerivedValue(() => Fv.get().flash);
  const picA = useDerivedValue(() => (reduced ? xf.get() : 1));
  const prevA = useDerivedValue(() => (reduced ? 1 - xf.get() : 0));
  const rimW = useDerivedValue(() => 1.5 / scale.get());
  const bevelW = useDerivedValue(() => (2 * BEVEL) / scale.get());
  const blinkD = useDerivedValue(() => Fv.get().blinkD);
  const blinkA = useDerivedValue(() => Fv.get().blinkA);

  // stage colour and fronts
  const baseCol = useDerivedValue(() => Fv.get().base);

  // zoom chip
  const chipLabel = useDerivedValue(() => {
    const v = Math.round(Fv.get().k * 10) / 10;
    return Math.abs(v - 1) < 0.05 ? '1×' : `${v.toFixed(1)}×`;
  });
  const chipX = useDerivedValue(() => {
    if (!chipFont) return CX;
    const s = chipLabel.get();
    const ws = chipFont.getGlyphWidths(chipFont.getGlyphIDs(s));
    let t = 0;
    for (const w of ws) t += w;
    return CX - t / 2;
  });
  const chipY = SH - G.chipFromBottom;

  // ---- RN layers ----
  const hintSt = (i: 0 | 1 | 2) => () => {
    'worklet';
    const a = Fv.get().hint[i];
    return { opacity: a, transform: [{ translateY: 4 * (1 - a) * S }] };
  };
  const hint0St = useAnimatedStyle(hintSt(0));
  const hint1St = useAnimatedStyle(hintSt(1));
  const hint2St = useAnimatedStyle(hintSt(2));
  const ctrlSt = useAnimatedStyle(() => ({ opacity: 1 - seg(Fv.get().sheet, 0, 0.6) }));
  const dotSt = useAnimatedStyle(() => {
    const c = Fv.get().dot;
    return { backgroundColor: c, borderColor: scaleHex(c, 0.88) };
  });
  const discSt = useAnimatedStyle(() => ({ transform: [{ scale: Fv.get().disc }] }));
  const sheetSt = useAnimatedStyle(() => {
    const f = Fv.get();
    return {
      transform: [{ translateY: (1 - f.sheet) * sheetTravel }],
      backgroundColor: rgba(scaleHex(f.dot, 0.46), 0.94),
      opacity: f.sheet > 0.001 ? 1 : 0,
    };
  });
  const pillSt = useAnimatedStyle(() => ({ transform: [{ translateX: Fv.get().seg * 168 * S }] }));
  const dynLabelSt = useAnimatedStyle(() => ({ opacity: mix(1, 0.7, clamp(Fv.get().seg, 0, 1)) }));
  const solidLabelSt = useAnimatedStyle(() => ({ opacity: mix(0.7, 1, clamp(Fv.get().seg, 0, 1)) }));
  const dynRowSt = useAnimatedStyle(() => ({ opacity: 1 - clamp(Fv.get().seg, 0, 1) }));
  const solidRowSt = useAnimatedStyle(() => ({ opacity: clamp(Fv.get().seg, 0, 1) }));
  const dyn0St = useAnimatedStyle(() => ({ backgroundColor: Fv.get().seg < 0.5 && dynAuto.get() === 1 ? Fv.get().dot : DYNAMIC_SWATCHES[0] }));
  const ringStep = (G.sheet.swW + G.sheet.swGap) * S;
  const rowW = (G.sheet.w - 2 * G.sheet.pad) * S; // the swatch rows keep the sheet's inset on both sides and fade out as they leave
  // the selection ring is a crisp 2.5 pt ring in the ink colour
  const solidRingSt = useAnimatedStyle(() => ({ transform: [{ translateX: Fv.get().ring * ringStep }], borderColor: C.ink }));
  const dynRingSt = useAnimatedStyle(() => ({ transform: [{ translateX: Fv.get().dring * ringStep }], borderColor: C.ink }));
  const pressSwSt = useAnimatedStyle(() => ({ transform: [{ scale: Fv.get().pressIdx === 0 ? Fv.get().pressS : 1 }] }));

  // ---- touch on the stage ----
  const hitShape = (lx: number, ly: number) => {
    'worklet';
    const f = Fv.get();
    const dx = lx - (CX + f.x);
    const dy = ly - (CY + f.y);
    let ph = (Math.atan2(dx, -dy) - ((f.rot + lean.get()) * Math.PI) / 180) / TAU;
    ph = ph - Math.floor(ph);
    const r = radii.get()[Math.floor(ph * 192) % 192] * G.R * scale.get();
    return Math.hypot(dx, dy) <= r + 14;
  };
  const rubber = (v: number, lo: number, hi: number) => {
    'worklet';
    if (v > hi) {
      const o = v - hi;
      return hi + o / (1 + o / 120);
    }
    if (v < lo) {
      const o = lo - v;
      return lo - o / (1 + o / 120);
    }
    return v;
  };

  const pan = Gesture.Pan()
    .minDistance(4)
    .onBegin((e) => {
      takeOver();
      grab.set(hitShape(e.x / S, e.y / S) && sheet.get() < 0.5 ? 1 : 0);
    })
    .onStart((e) => {
      if (grab.get() === 0 && e.numberOfPointers < 2) return;
      grab.set(1);
      x0.set(px.get());
      y0.set(py.get());
      px.set(px.get());
      py.set(py.get());
      lift.set(withTiming(2, { duration: 120 }));
      scheduleOnRN(buzz, 'soft');
    })
    .onUpdate((e) => {
      if (grab.get() === 0) return;
      px.set(rubber(x0.get() + e.translationX / S, -CX, G.stageW - CX));
      py.set(rubber(y0.get() + e.translationY / S, -CY, SH - CY));
    })
    .onEnd((e) => {
      if (grab.get() === 0) return;
      const vx = e.velocityX / S;
      const vy = e.velocityY / S;
      const tx = px.get() + vx * 0.12;
      const ty = py.get() + vy * 0.12;
      const cx = clamp(tx, -CX + 20, G.stageW - CX - 20);
      const cy = clamp(ty, -CY + 20, SH - CY - 20);
      if (cx !== tx || cy !== ty) scheduleOnRN(buzz, 'rigid');
      px.set(reduced ? clamp(px.get(), -CX, G.stageW - CX) : withSpring(cx, { ...glide, velocity: vx }));
      py.set(reduced ? clamp(py.get(), -CY, SH - CY) : withSpring(cy, { ...glide, velocity: vy }));
      lift.set(withSpring(0, glide));
      advanceHint(2);
    })
    .onFinalize(() => {
      grab.set(0);
      release();
    });

  const pinch = Gesture.Pinch()
    .onBegin(() => {
      takeOver();
    })
    .onStart((e) => {
      k0.set(k.get());
      pinchAnchor.set([CX + px.get() - e.focalX / S, CY + py.get() - e.focalY / S]);
      k.set(k.get());
      atLimit.set(0);
      lift.set(withTiming(Math.max(lift.get(), 1), { duration: 120 }));
    })
    .onUpdate((e) => {
      const raw = k0.get() * e.scale;
      let v = raw;
      if (raw > kMax) v = kMax + (raw - kMax) / 3;
      else if (raw < kMin) v = kMin - (kMin - raw) / 3;
      const prev = k.get();
      if ((prev - 1) * (v - 1) < 0) scheduleOnRN(buzz, 'sel');
      const out = raw > kMax || raw < kMin ? 1 : 0;
      if (out === 1 && atLimit.get() === 0) scheduleOnRN(buzz, 'rigid');
      atLimit.set(out);
      k.set(v);
      const anchor = pinchAnchor.get();
      const ratio = v / Math.max(k0.get(), 0.001);
      pinchShift.set([anchor[0] * (ratio - 1), anchor[1] * (ratio - 1)]);
    })
    .onEnd(() => {
      if (grab.get() === 0) lift.set(withSpring(0, glide));
      advanceHint(1);
    })
    .onFinalize(() => {
      const shift = pinchShift.get();
      const v = k.get();
      const bounded = clamp(v, kMin, kMax);
      const anchor = pinchAnchor.get();
      const ratio = bounded / Math.max(k0.get(), 0.001);
      const goal = [anchor[0] * (ratio - 1), anchor[1] * (ratio - 1)];
      const x = px.get();
      const y = py.get();
      px.set(x + shift[0]);
      py.set(y + shift[1]);
      pinchShift.set([0, 0]);
      if (v !== bounded) {
        k.set(reduced ? bounded : withSpring(bounded, pop));
        px.set(reduced ? x + goal[0] : withSpring(x + goal[0], pop));
        py.set(reduced ? y + goal[1] : withSpring(y + goal[1], pop));
      }
      release();
    });

  const rotation = Gesture.Rotation()
    .onBegin(() => {
      takeOver();
    })
    .onStart(() => {
      r0.set(rot.get());
    })
    .onUpdate((e) => {
      rot.set(r0.get() + (e.rotation * 180) / Math.PI);
    })
    .onEnd(() => {
      const r = rot.get();
      const near = Math.round(r / 90) * 90;
      if (Math.abs(r - near) <= 4) {
        rot.set(reduced ? near : withSpring(near, snap));
        scheduleOnRN(buzz, 'sel');
      }
    })
    .onFinalize(() => {
      release();
    });

  /** The angle (rad, clockwise from the top, in the shape's own frame) of a stage point seen from the shape's centre. */
  const angleOf = (lx: number, ly: number) => {
    'worklet';
    const f = Fv.get();
    return Math.atan2(lx - (CX + f.x), -(ly - (CY + f.y))) - ((f.rot + lean.get()) * Math.PI) / 180;
  };

  const tap = Gesture.Tap()
    .maxDuration(600)
    .maxDistance(10)
    .onBegin((e) => {
      takeOver();
      if (sheet.get() < 0.5 && hitShape(e.x / S, e.y / S)) {
        press.set(withTiming(0.96, { duration: 16 }));
        // the finger dents the rim where it lands (10 pt, σ 26°, 90 ms) under a touch ring
        const ang = angleOf(e.x / S, e.y / S);
        tapA.set(ang);
        dentV.set(withTiming(1, { duration: 90 }));
        ringsV.set([0.45, ang, 1, 0, 0, 1]);
        ringFade.set(1);
        scheduleOnRN(buzz, 'soft');
      }
    })
    .onEnd((e, ok) => {
      if (!ok) return;
      if (sheet.get() > 0.5) {
        openSheet(false);
        scheduleOnRN(buzz, 'soft');
        return;
      }
      if (!hitShape(e.x / S, e.y / S)) return;
      nextShape(angleOf(e.x / S, e.y / S));
      advanceHint(0);
    })
    .onFinalize(() => {
      press.set(withSpring(1, pop));
      dentV.set(withSpring(0, snap));
      ringFade.set(withTiming(0, { duration: 240 }));
      release();
    });

  const doubleTap = Gesture.Tap()
    .numberOfTaps(2)
    .maxDistance(12)
    .onBegin(() => {
      takeOver();
    })
    .onEnd((e, ok) => {
      if (!ok || !hitShape(e.x / S, e.y / S)) return;
      const weighty = { mass: 1, damping: SPRING.weighty.d, stiffness: SPRING.weighty.k };
      px.set(reduced ? 0 : withSpring(0, weighty));
      py.set(reduced ? 0 : withSpring(0, weighty));
      k.set(reduced ? clamp(1, kMin, kMax) : withSpring(clamp(1, kMin, kMax), weighty));
      rot.set(reduced ? 0 : withSpring(0, weighty));
      scheduleOnRN(buzz, 'light');
    })
    .onFinalize(() => {
      release();
    });

  const stageGesture = Gesture.Race(Gesture.Simultaneous(pan, pinch, rotation), Gesture.Exclusive(doubleTap, tap));

  const sheetPan = Gesture.Pan()
    .activeOffsetY([-8, 8])
    .onBegin(() => {
      takeOver();
      sheet0.set(sheet.get());
    })
    .onUpdate((e) => {
      sheet.set(clamp(sheet0.get() - Math.max(e.translationY, 0) / sheetTravel, 0, 1));
    })
    .onEnd((e) => {
      const close = 1 - sheet.get() > 0.4 || e.velocityY > 600;
      sheet.set(withSpring(close ? 0 : 1, { ...glide, velocity: -e.velocityY / sheetTravel }));
      if (close) scheduleOnRN(buzz, 'soft');
    })
    .onFinalize(() => {
      release();
    });

  // ---- buttons (JS → UI) ----
  const hold = () => scheduleOnUI(takeOver);
  const letGo = () => scheduleOnUI(release);
  const onShutterIn = () => {
    scheduleOnUI(() => {
      'worklet';
      takeOver();
      if (irisT.get() < 0) {
        pendingCapture.set(captureOf(Fv.get(), names[target.get()], bands, CX, CY));
        irisT.set(0);
        irisBuzz.set(reduced ? 2 : 0);
        holding.set(1);
        if (reduced) scheduleOnRN(buzz, 'medium');
      }
    });
  };
  const onShutterOut = () => {
    scheduleOnUI(() => {
      'worklet';
      holding.set(0);
      release();
    });
  };
  const onColourButton = () => {
    buzz('soft');
    scheduleOnUI(() => {
      'worklet';
      openSheet(sheet.get() < 0.5);
    });
  };
  const flipCamera = () => {
    'worklet';
    const next = 1 - flipTarget.get();
    flipTarget.set(next);
    flip.set(withTiming(next, { duration: reduced ? 0 : 350 }));
  };
  const onFlip = () => {
    buzz('light');
    scheduleOnUI(() => {
      'worklet';
      takeOver();
      flipCamera();
      release();
    });
  };
  const onSegment = (solid: boolean) => {
    buzz('sel');
    setSolidMode(solid);
    scheduleOnUI(() => {
      'worklet';
      segv.set(withSpring(solid ? 1 : 0, snap));
      dynAuto.set(solid ? 0 : 1);
      dynSince.set(now.get());
      dynCand.set(latestColour());
    });
  };
  const onSwatch = (row: 'dyn' | 'solid', i: number) => {
    buzz('sel');
    scheduleOnUI(() => {
      'worklet';
      if (row === 'solid') {
        ring.set(withSpring(i, snap));
        dynAuto.set(0);
        pressIdx.set(-1);
        startFlood(colors[i]);
      } else {
        dring.set(withSpring(i, snap));
        dynAuto.set(i === 0 ? 1 : 0);
        dynCand.set(latestColour());
        if (i > 0) startFlood(DYNAMIC_SWATCHES[i]);
      }
    });
  };

  // The hook reports the rendered frame, not the last live values when autoplay owns the stage.
  useEffect(() => {
    if (!__DEV__) return;
    const g = globalThis as { __ssv?: Record<string, unknown> };
    g.__ssv = {
      at: (t: number) => scheduleOnUI(() => {
        'worklet';
        touching.set(0);
        returning.set(0);
        pinned.set(1);
        auto.set(1);
        T.set(((t % LOOP) + LOOP) % LOOP);
      }),
      play: () => { pinned.set(0); auto.set(reduced ? 0 : 1); },
      rate: (r: number) => rate.set(clamp(r, 0.05, 4)),
      events: () => events.current,
      normalizeColor: (value: string) => normalizeColor(value),
      reduce: (enabled: boolean | null) => setReducedOverride(enabled),
      viewport: (w?: number, h?: number) => setViewport(w && h ? { w, h } : null),
      metrics: (reset = false) => {
        const v = stats.get();
        if (reset) stats.set([0, 0, 0]);
        return { callbacks: v[0], elapsedMs: v[1], meanHz: v[1] ? v[0] * 1000 / v[1] : 0, longestMs: v[2] };
      },
      state: () => {
        const f = Fv.get();
        return { T: T.get(), auto: auto.get(), pinned: pinned.get(), reduced,
          shape: names[auto.get() === 1 ? shapeAt(T.get(), count, initial) : target.get()],
          x: f.x, y: f.y, k: f.k, rotation: f.rot, iris: f.iris, flip: f.flip,
          sheet: f.sheet, segment: f.seg, color: visibleColour(f),
          layout: { width: W, height: H, scale: S, stageTop, stageH, ctrlY },
        };
      },
    };
    return () => { delete g.__ssv; };
  });
  useAnimatedReaction(() => Fv.get().seg > 0.5, (solid, previous) => {
    if (solid !== previous) scheduleOnRN(setSolidMode, solid);
  });
  // the hints' ink flips to #141416 when the stage is light (OKLab L > 0.75)
  useAnimatedReaction(() => lightnessOf(Fv.get().dot) > 0.75, (light, previous) => {
    if (light !== previous) scheduleOnRN(setInkDark, light);
  });

  // dev only: drive the live paths the gestures use (the same worklets), for checks without touch injection
  useEffect(() => {
    if (!__DEV__) return;
    const g = globalThis as { __ssv?: Record<string, unknown> };
    if (!g.__ssv) return;
    g.__ssv.poke = (kind: string, a = 0, b = 0) => {
      scheduleOnUI(() => {
        'worklet';
        takeOver();
        if (kind === 'tap') {
          nextShape((a * Math.PI) / 180);
          advanceHint(0);
        } else if (kind === 'drag') {
          // a fling: the centre moves to (a, b) in 0.25 s, then glides on with the release velocity
          const v = [(a - px.get()) / 0.25, (b - py.get()) / 0.25];
          px.set(withTiming(a, { duration: 250 }, () => {
            px.set(withSpring(clamp(a + v[0] * 0.12, -CX + 20, G.stageW - CX - 20), { ...glide, velocity: v[0] }));
          }));
          py.set(withTiming(b, { duration: 250 }, () => {
            py.set(withSpring(clamp(b + v[1] * 0.12, -CY + 20, SH - CY - 20), { ...glide, velocity: v[1] }));
          }));
          lift.set(withTiming(2, { duration: 120 }));
        } else if (kind === 'pinch') {
          k.set(reduced ? clamp(a, kMin, kMax) : withSpring(clamp(a, kMin, kMax), pop));
        } else if (kind === 'rotate') {
          rot.set(reduced ? a : withSpring(a, snap));
        } else if (kind === 'shutter' || kind === 'held-shutter') {
          if (irisT.get() < 0) {
            pendingCapture.set(captureOf(Fv.get(), names[target.get()], bands, CX, CY));
            holding.set(kind === 'held-shutter' ? 1 : 0);
            irisT.set(0);
            irisBuzz.set(0);
          }
        } else if (kind === 'release-shutter') {
          holding.set(0);
        } else if (kind === 'sheet') {
          openSheet(a === 1);
        } else if (kind === 'solid') {
          segv.set(withSpring(1, snap));
          ring.set(withSpring(a, snap));
          dynAuto.set(0);
          startFlood(colors[a]);
        } else if (kind === 'color') {
          dynAuto.set(0);
          startFlood(colors[clamp(a, 0, colors.length - 1)]);
        } else if (kind === 'transform') {
          px.set(a);
          py.set(b);
          k.set(1.25);
          rot.set(31);
          advanceHint(1);
          advanceHint(2);
        } else if (kind === 'reset') {
          px.set(withSpring(0, { mass: 1, damping: SPRING.weighty.d, stiffness: SPRING.weighty.k }));
          py.set(withSpring(0, { mass: 1, damping: SPRING.weighty.d, stiffness: SPRING.weighty.k }));
          k.set(withSpring(1, pop));
          rot.set(withSpring(0, snap));
        } else if (kind === 'flip') {
          flipCamera();
        }
        if (kind === 'drag') lift.set(withTiming(2, { duration: 120 }, () => {
          lift.set(withTiming(0, { duration: 400 }));
        }));
        release();
      });
    };
    g.__ssv.live = () => {
      const j = J.get();
      const w = Wt.get();
      return {
        auto: auto.get(),
        touching: touching.get(),
        returning: returning.get(),
        target: target.get(),
        wTarget: [0, 1, 2, 3, 4, 5].map((b) => +w[b * count + target.get()].toFixed(4)),
        x: +px.get().toFixed(1),
        y: +py.get().toFixed(1),
        k: +k.get().toFixed(3),
        rot: +rot.get().toFixed(1),
        jelly: [+j[0].toFixed(3), +j[1].toFixed(3)],
        base: base.get(),
        f1: [f1c.get(), +f1t.get().toFixed(2)],
        f2: [f2c.get(), +f2t.get().toFixed(2)],
        iris: +irisT.get().toFixed(2),
        sheet: +sheet.get().toFixed(2),
        dynAuto: dynAuto.get(),
        hint: [h0.get(), h1.get(), h2.get()].map((v) => +v.toFixed(2)),
      };
    };
  });

  const onLayout = (e: LayoutChangeEvent) => setSize({ w: e.nativeEvent.layout.width, h: e.nativeEvent.layout.height });
  const ff = (f: string) => (fontsOk ? { fontFamily: f } : { fontWeight: f === FONT.semibold ? ('600' as const) : ('500' as const) });
  const swStyle = { width: G.sheet.swW * S, height: G.sheet.swH * S, borderRadius: G.sheet.swR * S, marginRight: G.sheet.swGap * S };
  const ringStyle = {
    left: -5 * S,
    top: 0,
    width: (G.sheet.swW + 10) * S,
    height: (G.sheet.swH + 10) * S,
    borderRadius: (G.sheet.swR + 5) * S,
    borderWidth: 2.5 * S,
  };
  const hintTop = stageTop + (CY - G.hintAbove) * S - 15 * S;
  const ink = inkDark ? C.inkDark : C.ink;
  const icons = useMemo(() => ({ light: [TAP_ICON, PINCH_ICON, DRAG_ICON], dark: [TAP_ICON, PINCH_ICON, DRAG_ICON].map((x) => x.replace(/#FFFFFF/g, C.inkDark)) }), []);

  return (
    <View style={[styles.root, viewport && { flex: 0, width: viewport.w, height: viewport.h, alignSelf: 'center' }]} onLayout={onLayout}>
      <StatusBar style="light" />
      <LG colors={[C.barTop, C.barBottom]} style={StyleSheet.absoluteFill} />

      {/* the stage: colour, fronts, grain, shadow and the shaped picture */}
      {size && (
        <GestureDetector gesture={stageGesture}>
          <View style={{ position: 'absolute', left: offsetX + G.stageX * S, top: stageTop, width: G.stageW * S, height: stageH }}>
            <Canvas style={{ width: G.stageW * S, height: stageH }}>
              <Group transform={[{ scale: S }]}>
                <Group clip={Skia.RRectXY(Skia.XYWHRect(0, 0, G.stageW, SH), G.stageR, G.stageR)}>
                  <Rect x={0} y={0} width={G.stageW} height={SH} color={baseCol} />
                  <Front F={Fv} which={1} outline={outline} matrix={shapeM} scale={scale} reduced={reduced} SH={SH} />
                  <Front F={Fv} which={2} outline={outline} matrix={shapeM} scale={scale} reduced={reduced} SH={SH} />
                  <Rect x={0} y={0} width={G.stageW} height={SH} opacity={0.03} blendMode="softLight">
                    <FractalNoise freqX={0.9} freqY={0.9} octaves={3} seed={7} />
                  </Rect>
                  {/* the cut-out's body: two always-on shadows, from the outline itself */}
                  <Group matrix={ambM} opacity={ambA}>
                    <Path path={outline} color={shadowTint}>
                      <Blur blur={ambBlur} />
                    </Path>
                  </Group>
                  <Group matrix={contactM} opacity={0.24}>
                    <Path path={outline} color={shadowTint}>
                      <Blur blur={contactBlur} />
                    </Path>
                  </Group>
                  <Group matrix={shapeM}>
                    <Picture path={prevOutline} opacity={prevA} plate={plate} front={plateFront} shade={null} backM={backM} frontM={frontM} shadeM={shadeM} backA={backA} frontA={frontA} dimA={dimA} flashA={flashA} content={renderContent?.({ w: G.R * 2, h: G.R * 2 })} />
                    <Picture path={outline} opacity={picA} plate={plate} front={plateFront} shade={shade} backM={backM} frontM={frontM} shadeM={shadeM} backA={backA} frontA={frontA} dimA={dimA} flashA={flashA} content={renderContent?.({ w: G.R * 2, h: G.R * 2 })} />
                    {/* inner bevel: a 14 pt band inside the edge, light from the top left */}
                    <Group clip={outline}>
                      <Path path={outline} style="stroke" strokeWidth={bevelW}>
                        <LinearGradient
                          start={vec(-G.R * 0.75, -G.R * 0.75)}
                          end={vec(G.R * 0.75, G.R * 0.75)}
                          colors={['rgba(255,255,255,0.30)', 'rgba(255,255,255,0)', 'rgba(0,0,0,0.16)']}
                          positions={[0, 0.5, 1]}
                        />
                      </Path>
                    </Group>
                    {/* the rim: 1.5 pt, its highlight set by a gradient across the box so it slides round the lobes */}
                    <Path path={outline} style="stroke" strokeWidth={rimW}>
                      <LinearGradient
                        start={vec(-G.R * 0.9, -G.R * 0.9)}
                        end={vec(G.R * 0.9, G.R * 0.9)}
                        colors={['rgba(255,255,255,0.75)', 'rgba(255,255,255,0)', 'rgba(255,255,255,0)']}
                        positions={[0, 0.45, 1]}
                      />
                    </Path>
                  </Group>
                  {/* the shutter's one white contour */}
                  <Echo path={outline} matrix={shapeM} scale={scale} d={blinkD} w={2} color="#ffffff" alpha={blinkA} SH={SH} />
                  <TouchRing F={Fv} i={0} radii={radii} scale={scale} lean={lean} CX={CX} CY={CY} />
                  <TouchRing F={Fv} i={1} radii={radii} scale={scale} lean={lean} CX={CX} CY={CY} />
                  {/* zoom pill */}
                  <RoundedRect x={CX - G.chipW / 2} y={chipY - G.chip / 2} width={G.chipW} height={G.chip} r={G.chip / 2} color="rgba(0,0,0,0.34)" />
                  {chipFont && <SkText x={chipX} y={chipY + 5.7} text={chipLabel} font={chipFont} color={C.ink} />}
                </Group>
              </Group>
            </Canvas>
          </View>
        </GestureDetector>
      )}

      {/* hints */}
      {size && (
        <View pointerEvents="none" style={[styles.hintBox, { top: hintTop, height: 30 * S }]}>
          {(
            [
              [0, 'Tap to change shape', hint0St],
              [1, 'Pinch to resize', hint1St],
              [2, 'Drag to move', hint2St],
            ] as const
          ).map(([n, label, st]) => (
            <Animated.View key={label} style={[styles.hint, st]}>
              <SvgXml xml={(inkDark ? icons.dark : icons.light)[n]} width={22 * S} height={22 * S} />
              <Text
                allowFontScaling={false}
                style={[
                  styles.hintText,
                  ff(FONT.semibold),
                  { fontSize: 17 * S, marginLeft: 8 * S, color: ink },
                  inkDark && { textShadowColor: 'rgba(255,255,255,0.25)' },
                ]}
              >
                {label}
              </Text>
            </Animated.View>
          ))}
        </View>
      )}

      {/* controls */}
      {size && (
        <Animated.View style={[StyleSheet.absoluteFill, ctrlSt]} pointerEvents="box-none">
          <Pressable
            accessibilityLabel="Background color"
            onPressIn={hold}
            onPressOut={letGo}
            onPress={onColourButton}
            style={[styles.disc, { left: offsetX + (G.ctrlX[0] - G.btn / 2) * S, top: ctrlY - (G.btn / 2) * S, width: G.btn * S, height: G.btn * S, borderRadius: (G.btn / 2) * S }]}
          >
            <Animated.View style={[{ width: G.dot * S, height: G.dot * S, borderRadius: (G.dot / 2) * S, borderWidth: 1.5 * S }, dotSt]} />
          </Pressable>
          <Pressable
            accessibilityLabel="Take photo"
            onPressIn={onShutterIn}
            onPressOut={onShutterOut}
            style={[
              styles.shutter,
              { left: offsetX + (G.ctrlX[1] - G.shutter / 2) * S, top: ctrlY - (G.shutter / 2) * S, width: G.shutter * S, height: G.shutter * S, borderRadius: (G.shutter / 2) * S, borderWidth: G.shutterRing * S },
            ]}
          >
            <Animated.View style={[{ width: G.shutterDisc * S, height: G.shutterDisc * S, borderRadius: (G.shutterDisc / 2) * S, overflow: 'hidden' }, discSt]}>
              <LG colors={['#FFFFFF', '#FAFAF8']} style={StyleSheet.absoluteFill} />
            </Animated.View>
          </Pressable>
          <Pressable
            accessibilityLabel="Flip camera"
            onPressIn={hold}
            onPressOut={letGo}
            onPress={onFlip}
            style={[styles.disc, { left: offsetX + (G.ctrlX[2] - G.btn / 2) * S, top: ctrlY - (G.btn / 2) * S, width: G.btn * S, height: G.btn * S, borderRadius: (G.btn / 2) * S }]}
          >
            <SvgXml xml={FLIP_ICON} width={22 * S} height={22 * S} />
          </Pressable>
        </Animated.View>
      )}

      {/* the Background Color sheet */}
      {size && (
        <GestureDetector gesture={sheetPan}>
          <Animated.View style={[styles.sheet, { left: offsetX + G.sheet.x * S, top: sheetTop, width: G.sheet.w * S, height: G.sheet.h * S, borderRadius: G.sheet.r * S }, sheetSt]}>
            <View style={[styles.grabber, { top: 5 * S, width: 36 * S, height: 5 * S, borderRadius: 2.5 * S, left: (G.sheet.w / 2 - 18) * S }]} />
            <Text allowFontScaling={false} style={[styles.title, ff(FONT.semibold), { top: (G.sheet.title - 14.5) * S, fontSize: 24 * S, lineHeight: 29 * S }]}>
              Background Color
            </Text>
            <View style={[styles.segTrack, { left: G.sheet.pad * S, top: G.sheet.seg * S, width: 336 * S, height: G.sheet.segH * S, borderRadius: (G.sheet.segH / 2) * S }]}>
              <Animated.View style={[styles.segPill, { width: 168 * S, height: G.sheet.segH * S, borderRadius: (G.sheet.segH / 2) * S }, pillSt]} />
              <Pressable onPressIn={hold} onPressOut={letGo} onPress={() => onSegment(false)} style={styles.segHalf}>
                <Animated.Text allowFontScaling={false} style={[styles.segText, ff(FONT.medium), { fontSize: 15 * S }, dynLabelSt]}>
                  Dynamic
                </Animated.Text>
              </Pressable>
              <Pressable onPressIn={hold} onPressOut={letGo} onPress={() => onSegment(true)} style={styles.segHalf}>
                <Animated.Text allowFontScaling={false} style={[styles.segText, ff(FONT.medium), { fontSize: 15 * S }, solidLabelSt]}>
                  Solid
                </Animated.Text>
              </Pressable>
            </View>
            {/* Dynamic row */}
            <Animated.View pointerEvents={solidMode ? 'none' : 'auto'} style={[styles.row, { left: G.sheet.pad * S, width: rowW, top: (G.sheet.row - 5) * S, height: (G.sheet.swH + 10) * S }, dynRowSt]}>
              <SwatchRow items={DYNAMIC_SWATCHES} ringSt={dynRingSt} ringStyle={ringStyle} swStyle={swStyle} swW={G.sheet.swW * S} step={ringStep} rowW={rowW} S={S} hold={hold} letGo={letGo} onPick={(i) => onSwatch('dyn', i)} firstStyle={dyn0St} />
            </Animated.View>
            {/* Solid row */}
            <Animated.View pointerEvents={solidMode ? 'auto' : 'none'} style={[styles.row, { left: G.sheet.pad * S, width: rowW, top: (G.sheet.row - 5) * S, height: (G.sheet.swH + 10) * S }, solidRowSt]}>
              <SwatchRow items={colors} ringSt={solidRingSt} ringStyle={ringStyle} swStyle={swStyle} swW={G.sheet.swW * S} step={ringStep} rowW={rowW} S={S} hold={hold} letGo={letGo} onPick={(i) => onSwatch('solid', i)} firstStyle={pressSwSt} />
            </Animated.View>
          </Animated.View>
        </GestureDetector>
      )}
    </View>
  );
}

type V<T> = SharedValue<T>;

/** One crisp offset outline of the shape, `d` pt out: a stroke 2d + w wide with a stroke 2d − w wide cleared from it, outside the shape only. */
function Echo({ path, matrix, scale, d, w, color, alpha, SH }: { path: V<SkPath>; matrix: V<ReturnType<typeof processTransform3d>>; scale: V<number>; d: V<number>; w: number; color: string | V<string>; alpha: V<number>; SH: number }) {
  const on = useDerivedValue(() => d.get() > 0.5 && alpha.get() > 0.01);
  // an idle echo clips to nothing, so its layer costs nothing
  const area = useDerivedValue(() => (on.get() ? Skia.XYWHRect(0, 0, G.stageW, SH) : Skia.XYWHRect(0, 0, 0, 0)));
  const outer = useDerivedValue(() => (2 * Math.max(d.get(), 0) + w) / scale.get());
  const inner = useDerivedValue(() => Math.max(2 * d.get() - w, 0) / scale.get());
  const p = useDerivedValue(() => (on.get() ? path.get() : Skia.PathBuilder.Make().detach()));
  const op = useDerivedValue(() => (on.get() ? alpha.get() : 0));
  return (
    <Group clip={area} layer opacity={op}>
      <Group matrix={matrix}>
        <Group clip={p} invertClip>
          <Path path={p} style="stroke" strokeJoin="round" strokeWidth={outer} color={color} />
          <Path path={p} style="stroke" strokeJoin="round" strokeWidth={inner} blendMode="clear" />
        </Group>
      </Group>
    </Group>
  );
}

function FloodEcho({ i, front, fade, on, outline, matrix, scale, color, SH }: { i: 0 | 1 | 2; front: V<number>; fade: V<number>; on: V<boolean>; outline: V<SkPath>; matrix: V<ReturnType<typeof processTransform3d>>; scale: V<number>; color: V<string>; SH: number }) {
  const d = useDerivedValue(() => (on.get() ? front.get() - ECHOES[i].off : 0));
  const a = useDerivedValue(() => ECHOES[i].a * fade.get());
  return <Echo path={outline} matrix={matrix} scale={scale} d={d} w={ECHOES[i].w} color={color} alpha={a} SH={SH} />;
}

/**
 * One colour front: the new colour fills outward from the shape's edge, its solid edge trailing three crisp contours
 * (the new colour, +14 % lightness) that lead it and are gone by 260 pt.
 */
function Front({ F, which, outline, matrix, scale, reduced, SH }: { F: V<Frame>; which: 1 | 2; outline: V<SkPath>; matrix: V<ReturnType<typeof processTransform3d>>; scale: V<number>; reduced: boolean; SH: number }) {
  const u = useDerivedValue(() => (which === 1 ? F.get().f1u : F.get().f2u));
  const col = useDerivedValue(() => (which === 1 ? F.get().f1c : F.get().f2c));
  const echoCol = useDerivedValue(() => liftTone(col.get(), 0.14));
  const on = useDerivedValue(() => u.get() >= 0 && !reduced);
  const front = useDerivedValue(() => floodFront(u.get()));
  const fade = useDerivedValue(() => echoFade(front.get()));
  const fill = useDerivedValue(() => floodFill(front.get()));
  const path = useDerivedValue(() => (on.get() && fill.get() > 0.5 ? outline.get() : Skia.PathBuilder.Make().detach()));
  const bandW = useDerivedValue(() => (2 * Math.min(fill.get(), FLOOD_REACH)) / scale.get());
  // reduced motion: the colour cross-fades over the whole stage instead
  const fadeA = useDerivedValue(() => (reduced && u.get() >= 0 ? clamp(u.get(), 0, 1) : 0));
  return (
    <>
      <Group matrix={matrix}>
        <Path path={path} style="stroke" strokeJoin="round" strokeWidth={bandW} color={col} />
        <Path path={path} color={col} />
      </Group>
      <Rect x={0} y={0} width={G.stageW} height={SH} color={col} opacity={fadeA} />
      <FloodEcho i={2} front={front} fade={fade} on={on} outline={outline} matrix={matrix} scale={scale} color={echoCol} SH={SH} />
      <FloodEcho i={1} front={front} fade={fade} on={on} outline={outline} matrix={matrix} scale={scale} color={echoCol} SH={SH} />
      <FloodEcho i={0} front={front} fade={fade} on={on} outline={outline} matrix={matrix} scale={scale} color={echoCol} SH={SH} />
    </>
  );
}

/** A finger's touch: a Ø 26 white ring on the rim at the tap angle (alpha, angle and pop scale from the frame). */
function TouchRing({ F, i, radii, scale, lean, CX, CY }: { F: V<Frame>; i: 0 | 1; radii: V<number[]>; scale: V<number>; lean: V<number>; CX: number; CY: number }) {
  const at = useDerivedValue(() => {
    const f = F.get();
    const a = f.rings[i * 3 + 1];
    const j = Math.round((((a % TAU) + TAU) % TAU) / TAU * 192) % 192;
    const r = radii.get()[j] * G.R * Math.max(f.iris, 0.001);
    const lx = r * Math.sin(a);
    const ly = -r * Math.cos(a);
    const th = ((f.rot + lean.get()) * Math.PI) / 180;
    const s = scale.get();
    return [CX + f.x + s * (lx * Math.cos(th) - ly * Math.sin(th)), CY + f.y + s * (lx * Math.sin(th) + ly * Math.cos(th))];
  });
  const cx = useDerivedValue(() => at.get()[0]);
  const cy = useDerivedValue(() => at.get()[1]);
  const r = useDerivedValue(() => 13 * F.get().rings[i * 3 + 2]);
  const a = useDerivedValue(() => F.get().rings[i * 3]);
  const aFill = useDerivedValue(() => F.get().rings[i * 3] * 0.45);
  return (
    <>
      <Circle cx={cx} cy={cy} r={r} color="#ffffff" opacity={aFill} />
      <Circle cx={cx} cy={cy} r={r} color="#ffffff" style="stroke" strokeWidth={2} opacity={a} />
    </>
  );
}

/** The shaped picture: the plate (or the front camera's plate after a flip) filled through the outline, the leaf shadow multiplied over it. */
function Picture({
  path,
  opacity,
  plate,
  front,
  shade,
  backM,
  frontM,
  shadeM,
  backA,
  frontA,
  dimA,
  flashA,
  content,
}: {
  path: V<SkPath>;
  opacity: V<number>;
  plate: SkImage | null;
  front: SkImage | null;
  shade: SkImage | null;
  backM: V<ReturnType<typeof processTransform3d>>;
  frontM: V<ReturnType<typeof processTransform3d>>;
  shadeM: V<ReturnType<typeof processTransform3d>>;
  backA: V<number>;
  frontA: V<number>;
  dimA: V<number>;
  flashA: V<number>;
  content?: ReactNode;
}) {
  return (
    <Group opacity={opacity}>
      {content && <Group clip={path}><Group transform={[{ translateX: -G.R }, { translateY: -G.R }]}>{content}</Group></Group>}
      {!content && plate && (
        <Path path={path} opacity={backA}>
          <ImageShader image={plate} tx="clamp" ty="clamp" matrix={backM} />
        </Path>
      )}
      {!content && front && (
        <Path path={path} opacity={frontA}>
          <ImageShader image={front} tx="clamp" ty="clamp" matrix={frontM} />
        </Path>
      )}
      {shade && (
        <Path path={path} blendMode="multiply" opacity={0.55}>
          <ImageShader image={shade} tx="mirror" ty="mirror" matrix={shadeM} />
        </Path>
      )}
      <Path path={path} color="#000000" opacity={dimA} />
      <Path path={path} color="#ffffff" opacity={flashA} />
    </Group>
  );
}

type SwatchRowProps = {
  items: readonly string[];
  ringSt: object;
  ringStyle: object;
  swStyle: object;
  swW: number;
  step: number;
  rowW: number;
  S: number;
  hold: () => void;
  letGo: () => void;
  onPick: (i: number) => void;
  firstStyle: object;
};

/** One swatch; it dims as it leaves either end of the row so the row reads as scrollable. */
function SwatchChip({ index, color, x, step, swW, rowW, S, swStyle, extra }: { index: number; color: string; x: V<number>; step: number; swW: number; rowW: number; S: number; swStyle: object; extra?: object }) {
  const st = useAnimatedStyle(() => {
    const left = 5 * S + index * step - x.get();
    // a chip fades over the last 56 pt of the row (to 10 % at the clip), so the row reads as one that scrolls
    const rightK = clamp((rowW - left - 0.5 * swW) / (56 * S), 0, 1);
    const leftK = x.get() > 1 ? clamp((left + 0.5 * swW) / (56 * S), 0, 1) : 1;
    return { opacity: mix(0.1, 1, rightK * rightK) * mix(0.1, 1, leftK * leftK) };
  });
  return <Animated.View style={[swStyle, { backgroundColor: color }, extra, st]} />;
}

function SwatchRow({ items, ringSt, ringStyle, swStyle, swW, step, rowW, S, hold, letGo, onPick, firstStyle }: SwatchRowProps) {
  const x = useSharedValue(0);
  const onScroll = useAnimatedScrollHandler((e) => {
    x.set(e.contentOffset.x);
  });
  return (
    <Animated.ScrollView horizontal showsHorizontalScrollIndicator={false} onScroll={onScroll} scrollEventThrottle={16} contentContainerStyle={{ paddingTop: 5 * S, paddingLeft: 5 * S, paddingRight: 28 * S }}>
      <Animated.View pointerEvents="none" style={[styles.ring, ringStyle, { left: 0 }, ringSt]} />
      {items.map((c, i) => (
        <Pressable key={`${c}-${i}`} onPressIn={hold} onPressOut={letGo} onPress={() => onPick(i)}>
          <SwatchChip index={i} color={c} x={x} step={step} swW={swW} rowW={rowW} S={S} swStyle={swStyle} extra={i === 0 ? firstStyle : undefined} />
        </Pressable>
      ))}
    </Animated.ScrollView>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: C.barTop, overflow: 'hidden' },
  hintBox: { position: 'absolute', left: 0, right: 0, alignItems: 'center', justifyContent: 'center' },
  hint: { position: 'absolute', flexDirection: 'row', alignItems: 'center' },
  hintText: { color: C.ink, letterSpacing: -0.1, textShadowColor: 'rgba(0,0,0,0.26)', textShadowOffset: { width: 0, height: 1 }, textShadowRadius: 6 },
  disc: { position: 'absolute', backgroundColor: C.disc, alignItems: 'center', justifyContent: 'center' },
  shutter: { position: 'absolute', borderColor: C.shutterRing, alignItems: 'center', justifyContent: 'center' },
  sheet: { position: 'absolute', overflow: 'hidden' },
  grabber: { position: 'absolute', backgroundColor: 'rgba(255,255,255,0.30)' },
  title: { position: 'absolute', left: 0, right: 0, textAlign: 'center', color: C.ink, letterSpacing: -0.22 },
  segTrack: { position: 'absolute', backgroundColor: 'rgba(0,0,0,0.45)', flexDirection: 'row', overflow: 'hidden' },
  segPill: { position: 'absolute', left: 0, top: 0, backgroundColor: C.segPill },
  segHalf: { flex: 1, alignItems: 'center', justifyContent: 'center' },
  segText: { color: C.ink },
  row: { position: 'absolute', overflow: 'hidden' },
  ring: { position: 'absolute' },
});
