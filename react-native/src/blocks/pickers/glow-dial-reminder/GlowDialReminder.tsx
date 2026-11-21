import { useFonts } from 'expo-font';
import { StatusBar } from 'expo-status-bar';
import { useEffect, useMemo, useState } from 'react';
import { Pressable, StyleSheet, Text, View, type LayoutChangeEvent, type TextStyle } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, { cancelAnimation, Easing, interpolateColor, useAnimatedReaction, useAnimatedStyle, useDerivedValue, useFrameCallback, useSharedValue, withSpring, withTiming, type SharedValue } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { BlurMask, Canvas, Circle, Group, ImageShader, interpolateColors, LinearGradient, Path, Rect, RoundedRect, Shader, Skia, SweepGradient, Text as SkText, useFont, useImage, vec } from '@shopify/react-native-skia';
import Svg, { Path as SvgPath } from 'react-native-svg';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';

import { useReducedMotionPreference } from '../../../shared/useReducedMotionPreference';
import { gdrHaptic, gdrHapticCancel, gdrHapticHistory, gdrHapticLater } from './gdr-haptics';
import { BEAD_EVENTS, beadsOf, clamp01, DEG_PER_HOUR, dotsAt, emberOf, type Frame, frameAt, gearAt, gOf, HOUR_COL_BASE, HOUR_LABELS, hourIdxOf, LOOP, makeDots, MINUTE_LABELS, mix, numOf, OMEGA_TAU, POOL, poolOfP, pourOf, PROJECT, pumpAt, quad, retarget, RUBBER, RULER_PER_HOUR, shearAt, SHEAR_K, SHEAR_TAU, shearOut, staggerUnit, stepDots, stepGear, STRIDE, SZ, vOfTh, X, Y } from './gdr-motion';
import { GLOW_FX } from './gdr-shaders';
import { C, COPY, FONT, G, METRIC, RANGE, RAMP, SPRING } from './gdr-theme';

export type GlowDialProps = {
  value?: number;
  onChange?: (hours: number) => void;
  /** Limits must stay inside the illustrated 4–12 h range. */
  min?: number;
  max?: number;
  step?: number;
  unitLabel?: (hours: number) => string;
  enabled?: boolean;
  onToggle?: (enabled: boolean) => void;
  onComplete?: (tracking: boolean) => void;
  onBack?: () => void;
  palette?: { surface: string; glow: [string, string, string, string, string]; marker: string };
  autoPlay?: boolean;
};

const rgb = (hex: string) => {
  'worklet';
  return [parseInt(hex.slice(1, 3), 16) / 255, parseInt(hex.slice(3, 5), 16) / 255, parseInt(hex.slice(5, 7), 16) / 255];
};
const capTop = (y: number, size: number) => y - (METRIC.ascent - METRIC.cap) * size;
const smooth01 = (x: number) => {
  'worklet';
  const k = Math.min(Math.max(x, 0), 1);
  return k * k * (3 - 2 * k);
};
/** Beads of the live detents: a ring buffer of [t0, dir] pairs on the UI thread's clock. */
const BEAD_SLOTS = 12;
const EMPTY_BEADS: number[] = new Array<number>(BEAD_SLOTS * 2).fill(0).map((_, i) => (i % 2 === 0 ? -99 : 0));
/** Gear state at rest on `hours`: [hour roll, velocity, minute roll, velocity, hand, velocity]. */
const gearFor = (hours: number) => {
  const k = Math.round(hours * 4);
  return [hourIdxOf(k), 0, k, 0, k, 0];
};
const REST_GEAR = gearFor(RANGE.rest);
/** Pie hands: a line from the pie's centre at a clock angle (degrees clockwise from 12). */
const hand = (cx: number, cy: number, len: number, deg: number) => {
  'worklet';
  const a = (deg * Math.PI) / 180;
  const p = Skia.PathBuilder.Make();
  p.moveTo(cx, cy);
  p.lineTo(cx + Math.sin(a) * len, cy - Math.cos(a) * len);
  return p.detach();
};
const RIM_N = 24;
/** The cap's sweep sheen, fixed in screen space: white at 315° and 135°, #DCD3DA at 45° and 225° (compass), 0° of the sweep at 3 o'clock. */
const SHEEN = ['#EEE9ED', C.sheenLight, C.sheenShade, C.sheenLight, C.sheenShade, '#EEE9ED'];
/** The pool's touch area (canvas pt): the card above the ruler's. */
const POOL_TOUCH = { x: 24, y: 250, w: 354, h: 235 };
const EMPTY = makeDots(RANGE.rest);
const REST = frameAt(0);
const spring = (name: keyof typeof SPRING, velocity = 0) => {
  'worklet';
  return { damping: SPRING[name].d, stiffness: SPRING[name].k, mass: 1, velocity };
};

function DotNumeral({ state, frame }: { state: SharedValue<number[]>; frame: SharedValue<Frame> }) {
  const path = useDerivedValue(() => {
    const p = Skia.PathBuilder.Make();
    const dots = state.get();
    const f = frame.get();
    // the numeral turns with the light: 0.09 · shear rad (at most 8°) about the pool's centre, the outer dots 40 ms later
    const cap = (8 * Math.PI) / 180;
    const inner = Math.max(-cap, Math.min(cap, 0.09 * f.shear));
    const outer = Math.max(-cap, Math.min(cap, 0.09 * f.shearOuter));
    for (let j = 0; j < POOL; j++) {
      const o = j * STRIDE;
      const r = G.numeral.dot / 2 * clamp01(dots[o + SZ]);
      if (r <= 0.5) continue; // a dot that has shrunk to dust is dropped, not drawn as a speck
      const dx = dots[o + X] - G.glow.cx;
      const dy = dots[o + Y] - G.glow.cy;
      const ang = mix(inner, outer, Math.min(1, Math.hypot(dx, dy) / 55));
      const c = Math.cos(ang);
      const s = Math.sin(ang);
      let x = G.glow.cx + dx * c - dy * s;
      let y = G.glow.cy + dx * s + dy * c;
      if (f.fa > 0.01) {
        // a finger on the pool pushes the dots within 60 pt away, by up to 7 pt
        const ex = x - f.fx;
        const ey = y - f.fy;
        const dist = Math.hypot(ex, ey);
        if (dist < 60) {
          const k = 1 - dist / 60;
          const push = 7 * f.fa * k * k;
          x += (ex / Math.max(dist, 0.001)) * push;
          y += (ey / Math.max(dist, 0.001)) * push;
        }
      }
      p.addCircle(x, y, r);
    }
    return p.detach();
  });
  const fill = useDerivedValue(() => 0.92 * (1 - frame.get().off));
  const ink = useDerivedValue(() => interpolateColors(frame.get().f, [0, 1], [C.dot, C.inkCold]));
  const outline = useDerivedValue(() => 0.35 * frame.get().off);
  return <><Path path={path} color={ink} opacity={fill} /><Path path={path} color={C.inkSoft} opacity={outline} style="stroke" strokeWidth={2} /></>;
}

/** A sleep goal whose duration is a pool of pigment stirred by the dial's angular velocity. */
export function GlowDialReminder({ autoPlay = true, value = 8, onChange, min = 4, max = 12, step = 0.25, unitLabel = COPY.unit, enabled = true, onToggle, onComplete, onBack, palette }: GlowDialProps) {
  const reduced = useReducedMotionPreference();
  const insets = useSafeAreaInsets();
  const [size, setSize] = useState({ w: 402, h: 874 });
  const [fontsOk] = useFonts({
    [FONT.regular]: require('./assets/Urbanist-Regular.ttf'),
    [FONT.medium]: require('./assets/Urbanist-Medium.ttf'),
    [FONT.doto]: require('./assets/Doto-Bold.ttf'),
  });
  const grain = useImage(require('./assets/gd-grain.png'));
  const odoFont = useFont(require('./assets/Urbanist-Medium.ttf'), G.time.size);
  const [labels, setLabels] = useState({ hours: value, on: enabled, tracking: false });
  // Keep a tappable CTA between the dial's full hit area and the card's lower edge.
  const ctaMinHeight = 44;
  const ctaMinY = G.knob.cy + G.knob.hit + 18;
  const ctaBottomGap = 874 - G.cta.y - G.cta.h - 34;
  const ctaSafeBottom = Math.max(12, insets.bottom);
  const availableHeight = size.h - ctaSafeBottom;
  const S = Math.min(
    size.w / 402,
    Math.max(1, availableHeight) / (ctaMinY + G.cta.h + ctaBottomGap),
    Math.max(1, availableHeight - ctaMinHeight) / (ctaMinY + ctaBottomGap),
  );
  const contentWidth = 402 * S;
  const H = size.h / S;
  // Preserve top and bottom safe areas; the hero retains the reference's width-derived proportions.
  const topShift = Math.max(0, insets.top / S - 62);
  const ctaHeight = Math.max(ctaMinHeight, G.cta.h * S);
  const ctaTop = availableHeight - ctaBottomGap * S - ctaHeight;
  const ctaInset = (ctaHeight - G.cta.h * S) / 2;
  const ctaCenter = (ctaTop + ctaHeight / 2) / S;
  const low = Math.max(RANGE.min, Math.min(min, RANGE.max));
  const high = Math.max(low, Math.min(max, RANGE.max));
  const detent = Math.max(0.25, step);
  const surface = palette?.surface ?? C.card;
  const colors = palette?.glow ?? RAMP.slice(0, 5).map((s) => s[1]);
  const marker = palette?.marker ?? C.marker;
  const emitted = useSharedValue(value);
  const notifyValue = (hours: number) => { emitted.set(hours); onChange?.(hours); };
  const emittedEnabled = useSharedValue(enabled);
  const notifyToggle = (on: boolean) => { emittedEnabled.set(on); onToggle?.(on); };

  const clock = useSharedValue(0);
  const automatic = useSharedValue(autoPlay && !reduced ? 1 : 0);
  const pinned = useSharedValue(0);
  const speed = useSharedValue(1);
  const now = useSharedValue(0);
  const lastTouch = useSharedValue(0);
  const held = useSharedValue(0); // 1 dial, 2 ruler, 3 toggle, 4 CTA
  const theta = useSharedValue((value - RANGE.rest) * DEG_PER_HOUR);
  const off = useSharedValue(enabled ? 0 : 1); // the pour's progress (0 in the pool .. 1 in the knob)
  const offTarget = useSharedValue(enabled ? 0 : 1);
  const thumbPos = useSharedValue(enabled ? 0 : 1); // the switch's thumb, quicker than the pour
  const tracking = useSharedValue(0);
  const trackingTarget = useSharedValue(0);
  const cap = useSharedValue(0);
  const press = useSharedValue(0);
  const rawShear = useSharedValue(0);
  const omega = useSharedValue(0);
  const previousTheta = useSharedValue((value - RANGE.rest) * DEG_PER_HOUR);
  const dots = useSharedValue(EMPTY.slice());
  const start = useSharedValue(0);
  const grabAngle = useSharedValue(0);
  const angleReady = useSharedValue(0);
  const previousDetent = useSharedValue(Math.round((value - low) / detent));
  const lastHaptic = useSharedValue(-10);
  const cueWindow = useSharedValue(0);
  const cueCount = useSharedValue(0);
  const limited = useSharedValue(0);
  const returning = useSharedValue(0);
  const handoff = useSharedValue(1);
  const handoffFrame = useSharedValue(REST);
  const reduceOverride = useSharedValue(-1);
  const colorTest = useSharedValue(0); // Dev calibration: odd modes P3, even modes raw; surface / core / magenta.
  const restartEvidence = useSharedValue({ count: 0, frame: REST });
  const gear = useSharedValue(gearFor(value)); // odometer labels and pie hands (live)
  const veinLive = useSharedValue(0); // the pump's alpha, 300 ms low-pass of |ω|
  const beadList = useSharedValue(EMPTY_BEADS.slice()); // live beads: [t0, dir] pairs
  const outerShear = useSharedValue(0); // the shear 40 ms later, for the outer dots
  const pieLag = useSharedValue(0); // the minute pie follows the pool 200 ms later
  const fingerX = useSharedValue<number>(G.glow.cx); // a finger on the pool
  const fingerY = useSharedValue<number>(G.glow.cy);
  const fingerDown = useSharedValue(0);
  const fingerAmt = useSharedValue(0);
  const fingerAngle = useSharedValue(0);
  const fingerOmega = useSharedValue(0);
  const fingerRing = useSharedValue({ x: 0, y: 0, t0: -99 });
  const isReduced = useDerivedValue(() => reduceOverride.get() < 0 ? reduced : reduceOverride.get() === 1);

  const F = useDerivedValue<Frame>(() => {
    if (automatic.get() === 1 && !isReduced.get()) return frameAt(clock.get());
    const cold = clamp01(off.get());
    const home = returning.get() === 1 ? clamp01((now.get() - lastTouch.get() - 2) / 0.6) : 0;
    const calm = isReduced.get();
    const bd = beadsOf(beadList.get(), BEAD_SLOTS, now.get());
    const gs = gear.get();
    const ember = emberOf(cold);
    const ringAge = now.get() - fingerRing.get().t0;
    const ringOn = ringAge >= 0 && ringAge < 0.6;
    const live: Frame = { ...REST, th: theta.get(), g: gOf(vOfTh(theta.get())), gulp: calm ? 0 : bd.gulp, shear: calm ? 0 : shearOut(rawShear.get()) * (1 - home), shearOuter: calm ? 0 : shearOut(outerShear.get()) * (1 - home), breath: calm ? 1 : 1 + (1 - home) * 0.03 * Math.sin(2 * Math.PI * now.get() / LOOP), d: 1 - poolOfP(cold), pool: poolOfP(cold), plug: cold, vein: calm ? 0 : veinLive.get() * (1 - home), beads: calm ? REST.beads : bd.beads, ember, emberB: ember * (1 + (calm ? 0 : 0.06 * Math.sin(2 * Math.PI * now.get()))), lit: 36 * clamp01((cold - 0.71) / 0.29), stain: 0.22 * clamp01((pourOf(cold).vol - 0.6) / 0.4), hRoll: gs[0], kRoll: gs[2], kHand: gs[4], pieCold: pieLag.get(), f: tracking.get(), amt: 1, bloomR: 1, thumb: thumbPos.get(), off: cold, offMarker: cold, offTicks: cold, halo: ember, cap: cap.get(), cta: tracking.get(), ctaPress: press.get(), fx: fingerX.get(), fy: fingerY.get(), fa: fingerAmt.get(), ringFx: fingerRing.get().x, ringFy: fingerRing.get().y, ringFr: ringOn ? ringAge * 420 : 0, ringFa: ringOn ? Math.sin(Math.PI * ringAge / 0.6) : 0 };
    // The loop's staggered drain/bloom may be between states at grab time. Preserve those fields on hand-off.
    const from = handoffFrame.get(), h = handoff.get();
    return { ...live, g: mix(from.g, live.g, h), breath: mix(from.breath, live.breath, h), d: mix(from.d, live.d, h), pool: mix(from.pool, live.pool, h), plug: mix(from.plug, live.plug, h), ember: mix(from.ember, live.ember, h), emberB: mix(from.emberB, live.emberB, h), lit: mix(from.lit, live.lit, h), stain: mix(from.stain, live.stain, h), pieCold: mix(from.pieCold, live.pieCold, h), off: mix(from.off, cold, h), offTicks: mix(from.offTicks, cold, h), offMarker: mix(from.offMarker, cold, h), bloomR: mix(from.bloomR, 1, h), amt: mix(from.amt, 1, h), halo: mix(from.halo, live.halo, h), ringA: from.ringA * (1 - h), ringU: from.ringU };
  });
  const drawnDots = useDerivedValue(() => {
    if (automatic.get() !== 1 || isReduced.get()) return dots.get();
    const result = EMPTY.slice();
    dotsAt(clock.get(), result);
    return result;
  });

  const takeOver = (kind: number) => {
    'worklet';
    const f = F.get();
    if (kind === 1 || kind === 2) cancelAnimation(theta);
    if (kind === 3) {
      cancelAnimation(off);
      cancelAnimation(thumbPos);
    }
    if (automatic.get() === 1) {
      theta.set(f.th); previousTheta.set(f.th);
      off.set(f.plug); thumbPos.set(f.thumb); tracking.set(f.cta);
      offTarget.set(f.thumb >= 0.5 ? 1 : 0);
      trackingTarget.set(f.cta >= 0.5 ? 1 : 0);
      rawShear.set(shearAt(clock.get()).raw); omega.set(shearAt(clock.get()).w);
      outerShear.set(shearAt(clock.get() - 0.04).raw); veinLive.set(pumpAt(clock.get())); pieLag.set(f.pieCold);
      const g0 = REST_GEAR.slice(); gearAt(clock.get(), g0); gear.set(g0);
      const bl = EMPTY_BEADS.slice();
      for (let i = 0, k = 0; i < BEAD_EVENTS.length && k < BEAD_SLOTS; i += 2) if (clock.get() - BEAD_EVENTS[i] >= 0 && clock.get() - BEAD_EVENTS[i] < 0.3) { bl[2 * k] = now.get() - (clock.get() - BEAD_EVENTS[i]); bl[2 * k + 1] = BEAD_EVENTS[i + 1]; k++; }
      beadList.set(bl);
      const st = EMPTY.slice(); dotsAt(clock.get(), st); dots.set(st);
      handoffFrame.set(f); handoff.set(0);
      handoff.set(withTiming(1, { duration: isReduced.get() ? 0 : 180 }));
    }
    automatic.set(0); pinned.set(0); held.set(kind); returning.set(0);
    lastTouch.set(now.get());
  };
  const changeAngle = (degrees: number) => {
    'worklet';
    const lower = (low - 8) * DEG_PER_HOUR;
    const upper = (high - 8) * DEG_PER_HOUR;
    const edge = Math.max(lower, Math.min(upper, degrees));
    const over = degrees - edge;
    theta.set(edge + over / (1 + Math.abs(over) / RUBBER));
    const v = Math.max(low, Math.min(high, vOfTh(theta.get())));
    const n = Math.round((v - low) / detent);
    if (over !== 0 && limited.get() === 0) {
      limited.set(1); scheduleOnRN(gdrHaptic, 'limit'); lastHaptic.set(now.get());
    } else if (over === 0) limited.set(0);
    if (n !== previousDetent.get()) {
      const whole = Math.floor(low + n * detent) !== Math.floor(low + previousDetent.get() * detent);
      if (now.get() - cueWindow.get() >= 1) { cueWindow.set(now.get()); cueCount.set(0); }
      if ((whole || cueCount.get() < 10) && now.get() - lastHaptic.get() >= (whole ? 0.06 : 0.1)) {
        scheduleOnRN(gdrHaptic, whole ? 'hour' : 'detent'); lastHaptic.set(now.get());
        cueCount.set(cueCount.get() + 1);
      }
      const bl = beadList.get().slice(2);
      bl.push(now.get(), n > previousDetent.get() ? 1 : -1);
      beadList.set(bl);
      previousDetent.set(n);
      scheduleOnRN(notifyValue, Math.min(high, low + n * detent));
    }
  };
  const finish = (velocity: number) => {
    'worklet';
    const v = vOfTh(theta.get()) + velocity / DEG_PER_HOUR * PROJECT;
    const snapped = Math.max(low, Math.min(high, low + Math.round((v - low) / detent) * detent));
    theta.set(isReduced.get() ? withTiming((snapped - 8) * DEG_PER_HOUR, { duration: 200 }) : withSpring((snapped - 8) * DEG_PER_HOUR, spring('detent', velocity)));
    scheduleOnRN(notifyValue, snapped);
    cap.set(withTiming(0, { duration: isReduced.get() ? 0 : 120 }));
    held.set(0); lastTouch.set(now.get());
  };
  const closeToggle = (target: number) => {
    'worklet';
    offTarget.set(target);
    // the pour runs at a steady pace (its own beats are shaped in pourOf), so the drain is the autoplay's, time for time
    off.set(withTiming(target, { duration: isReduced.get() ? 200 : 1200 * Math.abs(target - off.get()), easing: Easing.linear }));
    thumbPos.set(isReduced.get() ? withTiming(target, { duration: 200 }) : withSpring(target, spring('snap')));
    scheduleOnRN(notifyToggle, target < 0.5);
    // Medium when the last light has entered the knob (not at the toggle); Soft when the pool starts back, Light when whole
    scheduleOnRN(gdrHapticCancel);
    if (target > 0.5) scheduleOnRN(gdrHapticLater, 'close', isReduced.get() ? 200 : Math.round(1190 * (1 - off.get())));
    else { scheduleOnRN(gdrHaptic, 'bloom'); scheduleOnRN(gdrHapticLater, 'full', isReduced.get() ? 200 : 1000); }
    held.set(0); lastTouch.set(now.get());
  };
  const confirm = () => {
    'worklet';
    const target = 1 - trackingTarget.get();
    trackingTarget.set(target);
    tracking.set(withTiming(target, { duration: isReduced.get() ? 200 : 700, easing: Easing.bezier(0.05, 0.7, 0.1, 1) }));
    press.set(withTiming(0, { duration: 140 }));
    if (onComplete) scheduleOnRN(onComplete, target === 1);
    scheduleOnRN(gdrHaptic, target === 1 ? 'success' : 'bloom');
    held.set(0); lastTouch.set(now.get());
  };

  useFrameCallback((info) => {
    const dt = Math.min((info.timeSincePreviousFrame ?? 16.67) / 1000, 0.05);
    now.set(now.get() + dt);
    if (pinned.get() === 1) return;
    if (automatic.get() === 1 && !isReduced.get()) {
      clock.set((clock.get() + dt * speed.get()) % LOOP); return;
    }
    const th = theta.get();
    const w = (th - previousTheta.get()) / dt * Math.PI / 180;
    previousTheta.set(th);
    omega.set(omega.get() + (w - omega.get()) * (1 - Math.exp(-dt / OMEGA_TAU)));
    // a finger circling the pool stirs it too (gain 0.6), without touching the value
    if (fingerDown.get() === 1) {
      const fa = Math.atan2(fingerY.get() - G.glow.cy, fingerX.get() - G.glow.cx);
      const far = Math.hypot(fingerX.get() - G.glow.cx, fingerY.get() - G.glow.cy) > 24;
      const turn = Math.atan2(Math.sin(fa - fingerAngle.get()), Math.cos(fa - fingerAngle.get()));
      fingerAngle.set(fa);
      fingerOmega.set(fingerOmega.get() + ((far ? turn / dt : 0) - fingerOmega.get()) * (1 - Math.exp(-dt / 0.06)));
    } else fingerOmega.set(fingerOmega.get() * Math.exp(-dt / 0.06));
    rawShear.set(rawShear.get() + ((omega.get() + 0.6 * fingerOmega.get()) * SHEAR_K - rawShear.get() / SHEAR_TAU) * dt);
    outerShear.set(outerShear.get() + (rawShear.get() - outerShear.get()) * (1 - Math.exp(-dt / 0.03)));
    veinLive.set(veinLive.get() + (0.5 * Math.min(1, Math.abs(omega.get()) / 2.5) - veinLive.get()) * (1 - Math.exp(-dt / 0.3)));
    pieLag.set(pieLag.get() + (clamp01(off.get()) - pieLag.get()) * (1 - Math.exp(-dt / 0.1)));
    fingerAmt.set(fingerAmt.get() + (fingerDown.get() - fingerAmt.get()) * (1 - Math.exp(-dt / 0.09)));
    const gs = gear.get().slice(); stepGear(gs, dt, vOfTh(th)); gear.set(gs);
    const st = dots.get().slice();
    const target = numOf(vOfTh(th));
    retarget(st, target, Math.sign(w), staggerUnit(w / Math.PI * 6), isReduced.get());
    stepDots(st, dt, isReduced.get()); dots.set(st);
    if (!isReduced.get() && autoPlay && held.get() === 0 && now.get() - lastTouch.get() > 2) {
      if (returning.get() === 0) {
        returning.set(1);
        offTarget.set(0); trackingTarget.set(0);
        theta.set(withTiming(0, { duration: 600, easing: Easing.bezier(0.2, 0.8, 0.2, 1) }));
        off.set(withTiming(0, { duration: 600 })); thumbPos.set(withTiming(0, { duration: 300 })); tracking.set(withTiming(0, { duration: 600 }));
      } else if (now.get() - lastTouch.get() > 2.8) {
        if (__DEV__) restartEvidence.set({ count: restartEvidence.get().count + 1, frame: F.get() });
        clock.set(0); automatic.set(1); returning.set(0);
      }
    }
  });

  useAnimatedReaction(() => ({ hours: Math.min(high, low + Math.round((Math.max(low, Math.min(high, vOfTh(F.get().th))) - low) / detent) * detent), on: F.get().thumb < 0.5, tracking: F.get().cta > 0.5 }), (next, previous) => {
    if (!previous || next.hours !== previous.hours || next.on !== previous.on || next.tracking !== previous.tracking) scheduleOnRN(setLabels, next);
  });

  useEffect(() => {
    // A controlled parent echoes onChange. Do not cancel the running release spring for that echo.
    if (value !== emitted.get() && held.get() === 0) theta.set((Math.max(low, Math.min(high, value)) - 8) * DEG_PER_HOUR);
  }, [value, low, high, theta, held, emitted]);
  useEffect(() => {
    if (enabled !== emittedEnabled.get() && held.get() !== 3) {
      const target = enabled ? 0 : 1;
      offTarget.set(target); off.set(target); thumbPos.set(target);
    }
  }, [enabled, offTarget, off, thumbPos, held, emittedEnabled]);
  useEffect(() => {
    automatic.set(autoPlay && !reduced ? 1 : 0);
    if (reduced) { theta.set(0); offTarget.set(0); off.set(0); thumbPos.set(0); trackingTarget.set(0); tracking.set(0); cap.set(0); press.set(0); held.set(0); returning.set(0); handoff.set(1); dots.set(EMPTY.slice()); rawShear.set(0); }
  }, [autoPlay, reduced, automatic, theta, offTarget, off, thumbPos, trackingTarget, tracking, cap, press, held, returning, handoff, dots, rawShear]);

  // These commands use the same UI-thread handlers as Gesture Handler; no production debug controls.
  useEffect(() => {
    if (!__DEV__) return;
    const g = globalThis as { __gdr?: unknown };
    g.__gdr = {
      at: (t: number) => { pinned.set(1); automatic.set(1); clock.set(((t % LOOP) + LOOP) % LOOP); },
      play: () => { automatic.set(1); pinned.set(0); },
      rate: (r: number) => speed.set(Math.max(0, r)),
      haptics: gdrHapticHistory,
      calibrate: (mode: number) => colorTest.set(mode),
      state: () => ({ t: clock.get(), auto: automatic.get(), pinned: pinned.get(), held: held.get(), reduced: isReduced.get(), frame: F.get(), hours: vOfTh(F.get().th), restart: restartEvidence.get(), dots: drawnDots.get().filter((_, i) => i % STRIDE < 2 || i % STRIDE === SZ) }),
      begin: (kind = 1) => scheduleOnUI(() => { takeOver(kind); cap.set(kind === 1 ? 1 : 0); }),
      turn: (delta: number) => scheduleOnUI(() => changeAngle(theta.get() + delta)),
      ruler: (dx: number) => scheduleOnUI(() => changeAngle(theta.get() - dx / RULER_PER_HOUR * DEG_PER_HOUR)),
      release: (velocity = 0) => scheduleOnUI(() => finish(velocity)),
      scrub: (progress: number) => scheduleOnUI(() => { if (held.get() !== 3) takeOver(3); off.set(clamp01(progress)); thumbPos.set(clamp01(progress)); }),
      toggle: (target: number) => scheduleOnUI(() => { takeOver(3); closeToggle(clamp01(target)); }),
      cta: () => scheduleOnUI(() => { takeOver(4); confirm(); }),
      pool: (x: number, y: number, down: boolean) => scheduleOnUI(() => { if (down && fingerDown.get() === 0) { takeOver(5); fingerAngle.set(Math.atan2(y - G.glow.cy, x - G.glow.cx)); } if (!down && fingerDown.get() === 1) { fingerRing.set({ x: fingerX.get(), y: fingerY.get(), t0: now.get() }); held.set(0); lastTouch.set(now.get()); } fingerX.set(x); fingerY.set(y); fingerDown.set(down ? 1 : 0); }),
      reduce: (flag: boolean) => scheduleOnUI(() => { reduceOverride.set(flag ? 1 : 0); takeOver(0); theta.set(0); previousTheta.set(0); offTarget.set(0); off.set(0); thumbPos.set(0); trackingTarget.set(0); tracking.set(0); cap.set(0); press.set(0); returning.set(0); handoff.set(1); rawShear.set(0); dots.set(EMPTY.slice()); }),
    };
    return () => { delete g.__gdr; };
  });

  const uniforms = useDerivedValue(() => {
    const f = F.get();
    const funnel = quad(G.glow.cx, G.glow.cy, G.ctaPath.c1x, G.ctaPath.c1y, 208, ctaCenter, f.f);
    const mode = colorTest.get();
    const calibrated = mode > 4 ? colors[2] : mode > 2 ? colors[0] : surface;
    const pr = pourOf(f.plug);
    const pool = Math.max(0, f.pool);
    // On this Graphite development build, raw sRGB matches a native RN View. P3 conversion shifts lilac by 6/255.
    // Keep toOut available for a renderer that actually exposes a P3 output surface.
    return {
      uP3: mode > 0 && mode % 2 === 1 ? 1 : 0, center: [funnel.x, funnel.y],
      radius: Math.max(0.0001, f.g * f.breath * (1 + f.gulp) * pool * (1 - f.f)), shear: f.shear,
      amount: f.amt * (1 - f.f) * smooth01(pool / 0.12), ring: f.ringU, ringAlpha: f.ringA,
      halo: f.halo, haloAlpha: mix(0.45, 0.9, f.halo) * (f.ember > 0.001 ? f.emberB / f.ember : 1),
      vein: f.vein, plug: [pr.head, pr.tail], plugAlpha: 0.85 * smooth01(f.plug / 0.03) * (1 - smooth01((f.plug - 0.985) / 0.015)),
      stain: f.stain, dent: [f.fx, f.fy, f.fa], fring: [f.ringFx, f.ringFy, f.ringFr, f.ringFa],
      b0: [f.beads[0], f.beads[1], f.beads[2]], b1: [f.beads[3], f.beads[4], f.beads[5]], b2: [f.beads[6], f.beads[7], f.beads[8]],
      b3: [f.beads[9], f.beads[10], f.beads[11]], b4: [f.beads[12], f.beads[13], f.beads[14]], b5: [f.beads[15], f.beads[16], f.beads[17]],
      surface: rgb(mode > 0 ? calibrated : surface), c0: rgb(colors[0]), c1: rgb(colors[1]), c2: rgb(colors[2]), c3: rgb(colors[3]), c4: rgb(colors[4]),
    };
  });
  // the ruler: one set of evenly spaced ticks through the marker; the quarter hours lit are the first ticks right of it
  const rulerBuild = (lit: boolean) => {
    'worklet';
    const p = Skia.PathBuilder.Make();
    const th = F.get().th;
    const shift = -th / DEG_PER_HOUR * RULER_PER_HOUR;
    const v = vOfTh(th);
    const q = Math.floor((v - Math.floor(v)) * 4 + 0.05);
    for (let i = -70; i <= 70; i++) {
      const x = G.glow.cx + i * G.ruler.pitch + shift;
      if (x < G.ruler.x0 || x > G.ruler.x1) continue;
      const on = q > 0 && x > G.marker.x + 1.5 && x < G.marker.x + 1.5 + q * G.ruler.pitch;
      if (on !== lit) continue;
      p.moveTo(x, G.ruler.bottom); p.lineTo(x, G.ruler.bottom - (i % 4 === 0 ? G.ruler.tall : G.ruler.short));
    }
    return p.detach();
  };
  const rulerBase = useDerivedValue(() => rulerBuild(false));
  const rulerLit = useDerivedValue(() => rulerBuild(true));
  const rulerAlpha = useDerivedValue(() => mix(0.6, 0.2, F.get().offTicks) * (1 + Math.abs(F.get().shear) * 0.15));
  // the knob's ticks (36, 2 × 8 pt at r 50–58): the one under the slit is 13 pt; the light tints them clockwise
  const knobBuild = (tint: boolean) => {
    'worklet';
    const f = F.get();
    const p = Skia.PathBuilder.Make();
    const first = (((Math.round(-f.th / 10)) % G.knob.ticks) + G.knob.ticks) % G.knob.ticks;
    for (let i = 0; i < G.knob.ticks; i++) {
      const lit = ((i - first + G.knob.ticks) % G.knob.ticks) < f.lit;
      if (lit !== tint) continue;
      const a = i * Math.PI * 2 / G.knob.ticks;
      const r1 = i === 0 ? G.knob.tickLong : G.knob.tickR1;
      p.moveTo(Math.sin(a) * G.knob.tickR0, -Math.cos(a) * G.knob.tickR0);
      p.lineTo(Math.sin(a) * r1, -Math.cos(a) * r1);
    }
    return p.detach();
  };
  const knobBase = useDerivedValue(() => knobBuild(false));
  const knobTint = useDerivedValue(() => knobBuild(true));
  // the pies: bedtime = 06:00 − v. The hour hand moves 30° per hour, the minute hand 90° per detent
  const hourHand = useDerivedValue(() => hand(G.hourPie.cx, G.hourPie.cy, 10, (6 - F.get().kHand / 4) * 30));
  const minuteHand = useDerivedValue(() => hand(G.minutePie.cx, G.minutePie.cy, 14, -F.get().kHand * 90));
  const minutePieColor = useDerivedValue(() => interpolateColors(clamp01(F.get().pieCold), [0, 1], [C.minutePie, C.pieCold]));
  const knobTransform = useDerivedValue(() => [{ translateX: G.knob.cx }, { translateY: G.knob.cy }, { rotate: F.get().th * Math.PI / 180 }]);
  const thumbSt = useAnimatedStyle(() => ({ transform: [{ translateX: -50 * S * clamp01(F.get().thumb) }, { scaleX: 1 + F.get().thumbPress * 0.2 }, { scaleY: 1 - F.get().thumbPress * 0.08 }] }));
  const trackSt = useAnimatedStyle(() => ({ backgroundColor: interpolateColor(clamp01(F.get().thumb), [0, 1], [C.trackOn, C.trackOff]) }));
  const onSt = useAnimatedStyle(() => ({ opacity: 1 - clamp01((F.get().thumb - 0.3) / 0.4) }));
  const offSt = useAnimatedStyle(() => ({ opacity: clamp01((F.get().thumb - 0.3) / 0.4) }));
  const capSt = useAnimatedStyle(() => ({ transform: [{ scale: 1 - 0.03 * F.get().cap }] }));
  // the cap: white, a sweep sheen fixed in screen space, an ember underglow (a pink face under a white veil) while it holds
  // the light, the slit that shows the turn, and the upper-left of the rim lit by the pool
  const pinkOp = useDerivedValue(() => clamp01(F.get().emberB - 0.06 * F.get().ember));
  const veilOp = useDerivedValue(() => 0.3 * clamp01(F.get().ember)); // 0.3, not 0.45: the cap must read ember-pink at thumbnail scale
  const slitColor = useDerivedValue(() => interpolateColors(F.get().ember, [0, 1], [C.slitOn, C.slitHold]));
  const slitTransform = useDerivedValue(() => [{ translateX: G.knob.cap * S }, { translateY: G.knob.cap * S }, { rotate: F.get().th * Math.PI / 180 }]);
  const rimAlpha = useDerivedValue(() => (0.15 + 0.35 * (F.get().g - 0.62) / 0.76) * clamp01(F.get().pool));
  const rimPath = useMemo(() => {
    const p = Skia.PathBuilder.Make();
    for (let i = 0; i <= RIM_N; i++) {
      const a = ((255 + (120 * i) / RIM_N) * Math.PI) / 180; // compass degrees: the upper-left 120°, centred on 315°
      const x = (G.knob.cap + Math.sin(a) * (G.knob.cap - 1.5)) * S;
      const y = (G.knob.cap - Math.cos(a) * (G.knob.cap - 1.5)) * S;
      if (i === 0) p.moveTo(x, y); else p.lineTo(x, y);
    }
    return p.detach();
  }, [S]);
  const LH = G.time.size * 1.2;
  // the odometers: the outgoing label rolls up and fades as the incoming one rises into place (no half-clipped glyphs)
  const odo = (r: number, labels: readonly string[], base: number) => {
    'worklet';
    const i = Math.max(0, Math.min(labels.length - 2, Math.floor(r)));
    const k = Math.min(Math.max(r - i, 0), 1);
    const e = k * k * (3 - 2 * k);
    const travel = 0.62 * LH;
    return { a: labels[i], b: labels[i + 1], ya: base - k * travel, yb: base + (1 - k) * travel, oa: 1 - e, ob: e };
  };
  const hourOdo = useDerivedValue(() => odo(F.get().hRoll + HOUR_COL_BASE, HOUR_LABELS, G.time.hourCap + METRIC.cap * G.time.size));
  const minOdo = useDerivedValue(() => odo(F.get().kRoll, MINUTE_LABELS, G.time.minCap + METRIC.cap * G.time.size));
  const hA = useDerivedValue(() => hourOdo.get().a);
  const hB = useDerivedValue(() => hourOdo.get().b);
  const hYA = useDerivedValue(() => hourOdo.get().ya);
  const hYB = useDerivedValue(() => hourOdo.get().yb);
  const hOA = useDerivedValue(() => hourOdo.get().oa);
  const hOB = useDerivedValue(() => hourOdo.get().ob);
  const mA = useDerivedValue(() => minOdo.get().a);
  const mB = useDerivedValue(() => minOdo.get().b);
  const mYA = useDerivedValue(() => minOdo.get().ya);
  const mYB = useDerivedValue(() => minOdo.get().yb);
  const mOA = useDerivedValue(() => minOdo.get().oa);
  const mOB = useDerivedValue(() => minOdo.get().ob);
  const markerSt = useAnimatedStyle(() => ({ backgroundColor: interpolateColor(F.get().offMarker, [0, 1], [marker, C.markerCold]), transform: [{ scaleY: 1 + F.get().pulse * 0.18 }] }));
  const hoursSt = useAnimatedStyle(() => ({ color: interpolateColor(Math.max(F.get().d, F.get().f), [0, 0.7, 1], [C.inkSoft, C.inkCold, C.inkCold]) }));
  const ctaSt = useAnimatedStyle(() => {
    const mode = colorTest.get();
    return { backgroundColor: mode > 0 ? (mode > 4 ? colors[2] : mode > 2 ? colors[0] : surface) : interpolateColor(F.get().cta, [0, 1], [C.cta, C.ctaOn]), transform: [{ scale: 1 - F.get().ctaPress * 0.03 }] };
  });
  const ctaGlyph = useDerivedValue(() => {
    const p = Skia.PathBuilder.Make();
    const k = clamp01(F.get().cta);
    p.moveTo(mix(-6, -6, k), mix(0, 0, k)); p.lineTo(mix(6, -1.5, k), mix(0, 4.5, k)); p.lineTo(mix(6, 7, k), mix(0, -5, k));
    p.moveTo(0, -6 * (1 - k)); p.lineTo(0, 6 * (1 - k));
    return p.detach();
  });

  const dial = Gesture.Pan().minDistance(0).onBegin((e) => {
    takeOver(1); cap.set(withTiming(1, { duration: 16 }));
    const x = e.x - G.knob.hit * S, y = e.y - G.knob.hit * S;
    angleReady.set(Math.hypot(x, y) >= 12 * S ? 1 : 0);
    grabAngle.set(Math.atan2(y, x)); scheduleOnRN(gdrHaptic, 'grab');
  }).onUpdate((e) => {
    const x = e.x - G.knob.hit * S, y = e.y - G.knob.hit * S;
    if (Math.hypot(x, y) < 12 * S) { angleReady.set(0); return; }
    const a = Math.atan2(y, x);
    // atan2 is undefined at the pivot: establish a fresh grab angle on leaving the central dead zone.
    if (angleReady.get() === 0) { grabAngle.set(a); angleReady.set(1); return; }
    const delta = Math.atan2(Math.sin(a - grabAngle.get()), Math.cos(a - grabAngle.get()));
    grabAngle.set(a); changeAngle(theta.get() + delta * 180 / Math.PI);
  }).onEnd((e) => {
    const x = e.x - G.knob.hit * S, y = e.y - G.knob.hit * S;
    finish((x * e.velocityY - y * e.velocityX) / Math.max(x * x + y * y, 144 * S * S) * 180 / Math.PI);
  }).onFinalize(() => { if (held.get() === 1) finish(0); });
  // a finger on the pool dents it, repels the dots and stirs the light (gain 0.6), without changing the value; the release
  // sends a ring from the finger. The touch area is the pool above the ruler.
  const releasePool = () => {
    'worklet';
    if (fingerDown.get() === 0) return;
    fingerRing.set({ x: fingerX.get(), y: fingerY.get(), t0: now.get() });
    fingerDown.set(0); held.set(0); lastTouch.set(now.get());
  };
  const pool = Gesture.Pan().minDistance(0).onBegin((e) => {
    takeOver(5);
    const x = POOL_TOUCH.x + e.x / S, y = POOL_TOUCH.y + e.y / S;
    fingerX.set(x); fingerY.set(y); fingerAngle.set(Math.atan2(y - G.glow.cy, x - G.glow.cx)); fingerOmega.set(0); fingerDown.set(1);
    scheduleOnRN(gdrHaptic, 'grab');
  }).onUpdate((e) => { fingerX.set(POOL_TOUCH.x + e.x / S); fingerY.set(POOL_TOUCH.y + e.y / S); }).onEnd(releasePool).onFinalize(releasePool);
  const ruler = Gesture.Pan().minDistance(0).onBegin(() => { takeOver(2); start.set(theta.get()); }).onUpdate((e) => changeAngle(start.get() - e.translationX / S / RULER_PER_HOUR * DEG_PER_HOUR)).onEnd((e) => finish(-e.velocityX / S / RULER_PER_HOUR * DEG_PER_HOUR)).onFinalize(() => { if (held.get() === 2) finish(0); });
  const togglePan = Gesture.Pan()
    .activeOffsetX([-4 * S, 4 * S])
    .onStart(() => { takeOver(3); start.set(thumbPos.get()); })
    .onUpdate((e) => {
      const v = clamp01(start.get() - e.translationX / (50 * S));
      thumbPos.set(v);
      off.set(withTiming(v, { duration: 90 }));
    })
    .onEnd((e, ok) => {
      if (ok) closeToggle(thumbPos.get() - e.velocityX / (50 * S) * PROJECT >= 0.5 ? 1 : 0);
    })
    .onFinalize(() => { if (held.get() === 3) closeToggle(offTarget.get()); });
  const toggleTap = Gesture.Tap().onEnd((_, ok) => {
    if (!ok) return;
    takeOver(3);
    closeToggle(1 - offTarget.get());
  });
  const toggle = Gesture.Exclusive(togglePan, toggleTap);
  const cta = Gesture.Tap().onBegin(() => { takeOver(4); press.set(withTiming(1, { duration: 80 })); }).onEnd((_, ok) => { if (ok) confirm(); }).onFinalize(() => { press.set(withTiming(0, { duration: 140 })); held.set(0); lastTouch.set(now.get()); });

  const text = (x: number, y: number, fontSize: number, family: string = FONT.medium): TextStyle => ({ position: 'absolute', left: x * S, top: capTop(y, fontSize) * S, fontSize: fontSize * S, lineHeight: fontSize * 1.2 * S, color: C.ink, fontFamily: fontsOk ? family : undefined, fontVariant: ['tabular-nums'], includeFontPadding: false });
  // the odometer windows of the gear train (16 pt labels); the bedtime for the screen reader is 06:00 − hours
  const bed = (((360 - Math.round(labels.hours * 60)) % 1440) + 1440) % 1440;
  const bedtime = `${Math.floor(bed / 60) % 12 || 12}:${String(bed % 60).padStart(2, '0')}`;
  const onLayout = (e: LayoutChangeEvent) => setSize({ w: e.nativeEvent.layout.width, h: e.nativeEvent.layout.height });
  return (
    <View onLayout={onLayout} style={styles.root}>
      <StatusBar style="light" />
      <View pointerEvents="box-none" style={{ flex: 1, width: contentWidth, alignSelf: 'center' }}>
        <Pressable accessibilityRole="button" accessibilityLabel="Back" onPress={onBack} style={{ position: 'absolute', left: 32 * S, top: (75 + topShift) * S, width: 48 * S, height: 48 * S, borderRadius: 24 * S, backgroundColor: C.backDisc, alignItems: 'center', justifyContent: 'center' }}>
          <Svg width={18 * S} height={18 * S} viewBox="0 0 18 18"><SvgPath d="M15 9H3M8 4L3 9L8 14" fill="none" stroke={C.backArrow} strokeWidth={2} strokeLinecap="round" strokeLinejoin="round" /></Svg>
        </Pressable>
        <Text allowFontScaling={false} style={[text(G.title.x, G.title.capTop + topShift, G.title.size, FONT.regular), { color: C.title, letterSpacing: -0.48 * S }]}>{COPY.title}</Text>
        <View pointerEvents="none" style={{ position: 'absolute', left: G.card.x * S, top: (G.card.y + topShift) * S, width: G.card.w * S, height: size.h - (G.card.y + topShift) * S, borderTopLeftRadius: G.card.r * S, borderTopRightRadius: G.card.r * S, borderBottomLeftRadius: G.card.r * S, borderBottomRightRadius: G.card.r * S, borderCurve: 'continuous', backgroundColor: surface, overflow: 'hidden' }}>
          {grain && <Canvas style={{ position: 'absolute', left: -G.card.x * S, top: -(G.card.y + topShift) * S, width: contentWidth, height: size.h }}><Group transform={[{ scale: S }]}>
            <Rect x={0} y={0} width={402} height={H}><Shader source={GLOW_FX!} uniforms={uniforms}><ImageShader image={grain} tx="repeat" ty="repeat" fit="fill" rect={{ x: 0, y: 0, width: 256 / 3, height: 256 / 3 }} /></Shader></Rect>
            <DotNumeral state={drawnDots} frame={F} />
            <Path path={rulerBase} style="stroke" strokeWidth={2} strokeCap="round" opacity={rulerAlpha}><LinearGradient start={vec(G.ruler.x0, 0)} end={vec(G.ruler.x1, 0)} colors={['rgba(243,195,218,0)', C.tick, C.tick, 'rgba(243,195,218,0)']} positions={[0, 0.24, 0.76, 1]} /></Path>
            <Path path={rulerLit} style="stroke" strokeWidth={2} strokeCap="round" color={C.tickLit} opacity={rulerAlpha} />
            {[0, 1, 2, 3].map((i) => <Circle key={i} cx={G.pageDots.x + i * G.pageDots.pitch} cy={G.pageDots.cy} r={i === 0 ? 2.5 : 1.5} color={i === 0 ? C.pageDotOn : C.pageDotOff} opacity={i === 0 ? 1 : 0.7} />)}
            <Circle cx={G.hourPie.cx} cy={G.hourPie.cy} r={G.hourPie.r} color={C.hourPie} />
            <Path path={hourHand} style="stroke" strokeWidth={2} strokeCap="round" color={surface} />
            <Circle cx={G.minutePie.cx} cy={G.minutePie.cy} r={G.minutePie.r} color={minutePieColor} />
            <Path path={minuteHand} style="stroke" strokeWidth={2} strokeCap="round" color={C.hourPie} />
            {odoFont && <>
              <SkText x={G.time.x} y={hYA} text={hA} font={odoFont} color={C.inkCold} opacity={hOA} />
              <SkText x={G.time.x} y={hYB} text={hB} font={odoFont} color={C.inkCold} opacity={hOB} />
              <SkText x={G.time.x} y={mYA} text={mA} font={odoFont} color={C.inkCold} opacity={mOA} />
              <SkText x={G.time.x} y={mYB} text={mB} font={odoFont} color={C.inkCold} opacity={mOB} />
            </>}
            <Group transform={knobTransform}>
              <Path path={knobBase} style="stroke" strokeWidth={G.knob.tickW} strokeCap="round" color={C.knobTick} />
              <Path path={knobTint} style="stroke" strokeWidth={4} strokeCap="round" color={C.ember0} opacity={0.55}><BlurMask blur={3} style="normal" /></Path>
              <Path path={knobTint} style="stroke" strokeWidth={G.knob.tickW} strokeCap="round" color={C.tickHold} />
            </Group>
          </Group></Canvas>}
        </View>
        <Text allowFontScaling={false} style={[text(G.reminder.x, G.reminder.capTop + topShift, G.reminder.size, FONT.regular), { color: C.inkCold, letterSpacing: -0.48 * S }]}>{COPY.reminder}</Text>
        <GestureDetector gesture={toggle}><Animated.View accessible accessibilityRole="switch" accessibilityLabel="Reminder" accessibilityState={{ checked: labels.on }} accessibilityActions={[{ name: 'activate' }]} onAccessibilityAction={() => scheduleOnUI(() => { takeOver(3); closeToggle(1 - offTarget.get()); })} style={[{ position: 'absolute', left: G.track.x * S, top: (G.track.y + topShift) * S, width: G.track.w * S, height: G.track.h * S, borderRadius: G.track.r * S }, trackSt]}>
          <Animated.Text allowFontScaling={false} style={[text(14, 16, G.toggleLabel.size), { color: C.toggleLabel }, onSt]}>{COPY.on}</Animated.Text>
          <Animated.Text allowFontScaling={false} style={[text(51, 16, G.toggleLabel.size), { color: C.ctaGlyph }, offSt]}>{COPY.off}</Animated.Text>
          <Animated.View style={[styles.solid, { left: 56 * S, top: 6 * S, width: 30 * S, height: 30 * S, borderRadius: 15 * S }, thumbSt]} />
        </Animated.View></GestureDetector>
        <Animated.Text allowFontScaling={false} style={[text(151, G.hours.xTop - 3, G.hours.size), { width: 100 * S, textAlign: 'center' }, hoursSt]}>{unitLabel(labels.hours)}</Animated.Text>
        <Animated.View pointerEvents="none" style={[{ position: 'absolute', left: (G.marker.x - G.marker.w / 2) * S, top: G.marker.y * S, width: G.marker.w * S, height: G.marker.h * S, borderRadius: G.marker.w * S }, markerSt]} />
        <Text allowFontScaling={false} style={[text(G.tue.x, G.tue.xTop, G.tue.size), { color: C.inkCold }]}>{COPY.day}</Text>
        <Text accessible={false} allowFontScaling={false} style={[text(G.date.x, G.date.capTop, G.date.size, FONT.doto), { color: C.inkCold }]}>{COPY.date}</Text>
        <View accessible accessibilityLabel={`Bedtime ${bedtime}`} style={{ position: 'absolute', left: G.time.x * S, top: capTop(G.time.hourCap, G.time.size) * S, width: G.time.win * S, height: (G.time.minCap - G.time.hourCap + LH) * S }} />
        <View pointerEvents="none" style={[styles.ambient, { left: (G.knob.cx - G.knob.cap) * S, top: (G.knob.cy - G.knob.cap) * S, width: 64 * S, height: 64 * S, borderRadius: 32 * S }]}>
          <Animated.View style={[styles.solid, { width: 64 * S, height: 64 * S, borderRadius: 32 * S, overflow: 'hidden', left: 0, top: 0 }, capSt]}>
            <Canvas style={StyleSheet.absoluteFill}>
              <Circle cx={32 * S} cy={32 * S} r={32 * S}><LinearGradient start={vec(9 * S, 5 * S)} end={vec(57 * S, 64 * S)} colors={[C.capLight, C.thumb, C.capShade]} positions={[0, 0.38, 1]} /></Circle>
              <Circle cx={32 * S} cy={32 * S} r={32 * S} opacity={pinkOp}><LinearGradient start={vec(9 * S, 5 * S)} end={vec(57 * S, 64 * S)} colors={[C.ember0, C.ember1]} /></Circle>
              <Circle cx={32 * S} cy={32 * S} r={32 * S} color="#FFFFFF" opacity={veilOp} />
              <Circle cx={32 * S} cy={32 * S} r={32 * S} opacity={0.5}><SweepGradient c={vec(32 * S, 32 * S)} colors={SHEEN} positions={[0, 0.125, 0.375, 0.625, 0.875, 1]} /></Circle>
              <Circle cx={32 * S} cy={32 * S} r={31 * S} color={C.capLight} style="stroke" strokeWidth={S} />
              <Path path={rimPath} style="stroke" strokeWidth={1.5 * S} strokeCap="round" color={C.rim} opacity={rimAlpha} />
              <Group transform={slitTransform}><RoundedRect x={-G.slit.w / 2 * S} y={-G.slit.r1 * S} width={G.slit.w * S} height={(G.slit.r1 - G.slit.r0) * S} r={G.slit.w / 2 * S} color={slitColor} /></Group>
            </Canvas>
          </Animated.View>
        </View>
        <GestureDetector gesture={dial}><View accessibilityRole="adjustable" accessibilityLabel="Sleep goal" accessibilityValue={{ min: low, max: high, now: labels.hours, text: `${labels.hours} hours` }} accessibilityActions={[{ name: 'increment' }, { name: 'decrement' }]} onAccessibilityAction={(e) => { const sign = e.nativeEvent.actionName === 'increment' ? 1 : -1; scheduleOnUI(() => { takeOver(1); changeAngle(theta.get() + sign * detent * DEG_PER_HOUR); finish(0); }); }} style={{ position: 'absolute', left: (G.knob.cx - G.knob.hit) * S, top: (G.knob.cy - G.knob.hit) * S, width: G.knob.hit * 2 * S, height: G.knob.hit * 2 * S }} /></GestureDetector>
        <GestureDetector gesture={pool}><View accessible={false} importantForAccessibility="no" style={{ position: 'absolute', left: POOL_TOUCH.x * S, top: POOL_TOUCH.y * S, width: POOL_TOUCH.w * S, height: POOL_TOUCH.h * S }} /></GestureDetector>
        <GestureDetector gesture={ruler}><View accessibilityLabel="Duration ruler" style={{ position: 'absolute', left: 20 * S, top: 485 * S, width: 362 * S, height: 70 * S }} /></GestureDetector>
        <GestureDetector gesture={cta}><Animated.View accessible accessibilityRole="button" accessibilityLabel={labels.tracking ? COPY.ctaDone : COPY.cta} accessibilityActions={[{ name: 'activate' }]} onAccessibilityAction={() => scheduleOnUI(() => { takeOver(4); confirm(); })} style={[{ position: 'absolute', left: G.cta.x * S, top: ctaTop, width: G.cta.w * S, height: ctaHeight, borderRadius: ctaHeight / 2 }, ctaSt]}>
          <Text allowFontScaling={false} style={[text(32, 19 + ctaInset / S, G.cta.size), { color: C.ink }]}>{labels.tracking ? COPY.ctaDone : COPY.cta}</Text>
          <View pointerEvents="none" style={[styles.solid, { left: 161 * S, top: 7 * S + ctaInset, width: 34 * S, height: 34 * S, borderRadius: 17 * S }]}><Canvas style={StyleSheet.absoluteFill}><Group transform={[{ translateX: 17 * S }, { translateY: 17 * S }, { scale: S }]}><Path path={ctaGlyph} style="stroke" strokeWidth={2} strokeCap="round" strokeJoin="round" color={C.ctaGlyph} /></Group></Canvas></View>
        </Animated.View></GestureDetector>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: C.page, overflow: 'hidden' },
  solid: { position: 'absolute', backgroundColor: C.thumb, borderWidth: 1, borderColor: C.capLight, shadowColor: C.capShadow, shadowOffset: { width: 0, height: 1.5 }, shadowRadius: 3, shadowOpacity: 0.3 },
  ambient: { position: 'absolute', backgroundColor: C.capShade, shadowColor: C.capShadow, shadowOffset: { width: 0, height: 8 }, shadowRadius: 18, shadowOpacity: 0.22 },
});
