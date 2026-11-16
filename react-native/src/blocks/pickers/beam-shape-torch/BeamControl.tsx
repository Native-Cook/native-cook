// Beam Control — a black glass capsule, grown out of the Dynamic Island, holding a light whose beam obeys one law: the
// same flux through a narrower cone burns hotter and throws further. One finger sets both axes: drag up/down for
// brightness, toward/away from the beam's axis (or pinch) to focus it, or take the small sun at the end of the arc and
// drag it; tap to switch the light on/off; tap outside to close the capsule into the island, tap the island to open it.
//
// It is a component for a connected light (a film or studio light with a motorised spot/flood, a track spot, a bike or
// head lamp, a camera's torch): brightness and spread are SharedValues you can read and write on the UI thread
// (controlled), or leave to the component (uncontrolled); values leave through callbacks; the glyph, the colours and the
// anchor are props. Mount it as an absolute-fill sibling of your screen: its canvas fills the parent so the glass can
// cast its shadow and spill its light onto the page, and only the capsule (or the island) takes touches.
//
//   <BeamControl brightness={b} spread={w} onChangeEnd={(v) => lamp.set(v.brightness, v.beamAngle)} renderGlyph={SpotGlyph} />
import { type ReactNode, useCallback, useEffect, useMemo, useState } from 'react';
import { type AccessibilityActionEvent, Platform, StyleSheet, useWindowDimensions, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  cancelAnimation,
  Easing,
  type SharedValue,
  useAnimatedReaction,
  useAnimatedStyle,
  useDerivedValue,
  useFrameCallback,
  useSharedValue,
  withDelay,
  withSequence,
  withSpring,
  withTiming,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { BlurMask, Canvas, DashPathEffect, Group, Path, RadialGradient, Rect, Shader, Skia, vec } from '@shopify/react-native-skia';
import { scheduleOnRN } from 'react-native-worklets';

import { useReducedMotionPreference } from '../../../shared/useReducedMotionPreference';
import { type RenderGlyph, TorchGlyph } from './BeamGlyphs';
import { arcPath, buildSun, squirclePath } from './bst-geometry';
import { FOCUS_LOCK_DELAY_MS, haptic, silence } from './bst-haptics';
import {
  type AngleRange,
  arcGeometry,
  clamp01,
  GESTURE,
  landing,
  type Look,
  lookAt,
  LOOP,
  mix,
  opticsOf,
  pop,
  REST,
  rubber,
  smooth,
  snap,
  solveSun,
  SPILL,
  SPRING,
  springAt,
  springCfg,
  sunOffset,
} from './bst-motion';
import { BEAM_SKSL } from './bst-shaders';
import { type BeamAnchor, type BeamTheme, COPY, makeLayout, ref, resolveTheme } from './bst-theme';

export { LOOP };
export type { AngleRange, BeamAnchor, BeamTheme };

const beamFx = Skia.RuntimeEffect.Make(BEAM_SKSL);
const SUN = buildSun();
const P3 = Platform.OS === 'ios' ? 1 : 0;
const C = ref.color;
const AFTER_REACH = 205; // design pt: how long the afterimage of a held pencil is

/** What the callbacks carry: both axes 0 … 1, and the beam's half-angle in degrees. */
export type BeamValue = { brightness: number; spread: number; beamAngle: number };

export type BeamControlProps = {
  /** brightness 0 … 1 on the UI thread: read it to drive your page, write it to drive the light */
  brightness?: SharedValue<number>;
  /** spread 0 (pencil) … 1 (flood) on the UI thread */
  spread?: SharedValue<number>;
  /** the start values when `brightness` / `spread` are not given */
  defaultValue?: { brightness: number; spread: number };
  /** the light is on (brightness above 0); give it to control the switch */
  on?: boolean;
  onToggle?: (on: boolean) => void;
  /** while the finger or a spring moves either axis, at most every 60 ms */
  onChange?: (v: BeamValue) => void;
  /** once the axes have settled after an interaction */
  onChangeEnd?: (v: BeamValue) => void;
  /** the half-angle the spread runs through, degrees (default 4 … 40) */
  angle?: AngleRange;
  /** the capsule is open (true) or closed into the island (false); give it to control it */
  expanded?: boolean;
  onExpandedChange?: (open: boolean) => void;
  /** where the capsule hangs: under the Dynamic Island (default), or in this rectangle */
  anchor?: BeamAnchor;
  /** the glyph under the lens, drawn with Skia in a 28 × 79 pt box (default: the torch) */
  renderGlyph?: RenderGlyph;
  theme?: BeamTheme;
  /** default true */
  haptics?: boolean;
  /** play the demo loop until a finger arrives, and again 2 s after it leaves */
  autoPlay?: boolean;
  /** tapping the page while the capsule is open closes it (default true) */
  closeOnOutsideTap?: boolean;
  accessibilityLabel?: string;
};

type Grip = {
  region: number; // 0 none, 1 capsule, 2 island, 3 outside
  x0: number;
  y0: number;
  t0: number;
  b0: number;
  w0: number;
  d0: number;
  axis: number; // 0 undecided, 1 horizontal leads, 2 vertical leads
  moved: number;
  rawB: number;
  pinch: boolean;
  wp0: number;
  tickB: number;
  tickT: number;
  tickAt: number;
  wasOff: boolean;
  handle: boolean; // the finger took the sun
  glide: boolean; // … and landed far from it: both values glide to it first
  degPrev: number;
  degAt: number;
  degSpeed: number;
};

const NO_GRIP: Grip = {
  region: 0,
  x0: 0,
  y0: 0,
  t0: 0,
  b0: 1,
  w0: 1,
  d0: 0,
  axis: 0,
  moved: 0,
  rawB: 1,
  pinch: false,
  wp0: 1,
  tickB: 10,
  tickT: 8,
  tickAt: -1,
  wasOff: false,
  handle: false,
  glide: false,
  degPrev: 0,
  degAt: -1,
  degSpeed: 0,
};

export function BeamControl({
  brightness,
  spread,
  defaultValue,
  on,
  onToggle,
  onChange,
  onChangeEnd,
  angle,
  expanded,
  onExpandedChange,
  anchor = 'island',
  renderGlyph,
  theme,
  haptics = true,
  autoPlay = false,
  closeOnOutsideTap = true,
  accessibilityLabel,
}: BeamControlProps) {
  const { width: W, height: H } = useWindowDimensions();
  const insets = useSafeAreaInsets();
  const reduce = useReducedMotionPreference();
  const L = useMemo(() => makeLayout(W, H, insets.top, anchor), [W, H, insets.top, anchor]);
  const th = useMemo(() => resolveTheme(theme), [theme]);
  const cue = haptics ? haptic : silence;
  const S = L.S;
  const cx = L.cap.x + L.cap.w / 2;
  const top = L.cap.y;
  // plain numbers for the worklets (the layout object also carries functions, which must not be captured)
  const CAP = L.cap;
  const ISL = L.isl;
  const lensY = L.iy(ref.lens.y);
  const glyphX = L.ix(ref.glyph.x);
  const glyphY = L.iy(ref.glyph.y);
  const aMin = Math.min(Math.max(angle?.min ?? 4, 1.2), 39);
  const aMax = Math.min(Math.max(angle?.max ?? 40, aMin + 1), 40);
  const start = defaultValue ?? { brightness: 1, spread: 1 };
  const startOpen = expanded ?? true;
  const initialB = on === false ? 0 : clamp01(start.brightness);

  // ---- state: the timeline plays `look`; a finger hands it to the live values ----
  const bOwn = useSharedValue(initialB);
  const wOwn = useSharedValue(clamp01(start.spread));
  const bL = brightness ?? bOwn; // controlled by the host's shared value, or the component's own
  const wL = spread ?? wOwn;
  const mirror = brightness !== undefined || spread !== undefined; // the demo loop writes through to the host's values
  const look = useSharedValue<Look>({
    ...REST,
    b: initialB,
    w: clamp01(start.spread),
    m: startOpen ? 1 : 0,
    mw: startOpen ? 1 : 0,
  });
  const clock = useSharedValue(0);
  const loopT = useSharedValue(0);
  const playing = useSharedValue(autoPlay);
  const pinned = useSharedValue(-1);
  const mL = useSharedValue(startOpen ? 1 : 0);
  const mwL = useSharedValue(startOpen ? 1 : 0);
  const openT = useSharedValue(startOpen);
  const pressL = useSharedValue(1);
  const reducedFade = useSharedValue(1);
  const hotL = useSharedValue(initialB);
  const lastOn = useSharedValue(clamp01(start.brightness) || 1);
  const touching = useSharedValue(false);
  const touchedAt = useSharedValue(-1e9);
  const resuming = useSharedValue(false);
  const grip = useSharedValue<Grip>(NO_GRIP);
  const handleOn = useSharedValue(false);
  const fx = useSharedValue(0);
  const fy = useSharedValue(0);
  const sunGrab = useSharedValue(0);
  const prevB = useSharedValue(initialB);
  const prevW = useSharedValue(clamp01(start.spread));
  const vB = useSharedValue(0);
  const vW = useSharedValue(0);
  const afterL = useSharedValue(0);
  const dwell = useSharedValue(0);
  const focusAt = useSharedValue(-1);
  const ceilOn = useSharedValue(opticsOf(initialB, start.spread, aMin, aMax).reach >= GESTURE.ceilingOn);
  const lastB = useSharedValue(initialB);
  const lastW = useSharedValue(clamp01(start.spread));
  const lastEmit = useSharedValue(-1);
  const moveAt = useSharedValue(-1);
  const pend = useSharedValue(false);
  const endPending = useSharedValue(false);
  const lastOnState = useSharedValue(initialB > 0.01);
  const awake = useSharedValue(true);
  const [running, setRunning] = useState(true);
  const [a11y, setA11y] = useState({ b: initialB, w: clamp01(start.spread), open: startOpen });
  const isOpen = expanded ?? a11y.open; // a controlled capsule is whatever the host says
  useEffect(() => {
    awake.set(true);
    queueMicrotask(() => setRunning(true));
  }, [on, expanded, reduce, awake]);

  // the host's callbacks cross from the UI thread through these (scheduleOnRN needs a plain function)
  const emitChange = useCallback(
    (b: number, w: number, deg: number) => onChange?.({ brightness: b, spread: w, beamAngle: deg }),
    [onChange],
  );
  const emitEnd = useCallback(
    (b: number, w: number, deg: number) => onChangeEnd?.({ brightness: b, spread: w, beamAngle: deg }),
    [onChangeEnd],
  );
  const emitToggle = useCallback((isOn: boolean) => onToggle?.(isOn), [onToggle]);
  const emitExpanded = useCallback((open: boolean) => onExpandedChange?.(open), [onExpandedChange]);

  // Development only: `__bst.pin(t)` freezes the loop at t s, `__bst.play()` lets it run, `__bst.state()` reads it.
  useEffect(() => {
    if (!__DEV__) return;
    const globals = globalThis as Record<string, unknown>;
    const previous = globals.__bst;
    const hook = {
      pin: (t: number) => pinned.set(t),
      at: (t: number) => pinned.set(t),
      play: () => pinned.set(-1),
      state: () => ({ ...look.get(), loopT: loopT.get(), playing: playing.get() }),
    };
    globals.__bst = hook;
    return () => {
      if (globals.__bst === hook) globals.__bst = previous;
    };
  }, [pinned, look, loopT, playing]);

  useEffect(() => {
    playing.set(autoPlay && !reduce);
  }, [autoPlay, reduce, playing]);

  const optics = useDerivedValue(() => opticsOf(look.get().b, look.get().w, aMin, aMax));

  /** ticks: every 10 % of brightness, every 5° of half-angle (denser above 60°/s); a click when the lamp comes on from off */
  const tickCheck = (b: number, w: number) => {
    'worklet';
    const g = grip.get();
    const now = clock.get();
    const deg = (opticsOf(b, w, aMin, aMax).theta * 180) / Math.PI;
    const tb = Math.floor(clamp01(b) * 10 + 1e-4);
    const tt = Math.floor(deg / GESTURE.tickTheta);
    const inst = g.degAt >= 0 ? Math.abs(deg - g.degPrev) / Math.max(now - g.degAt, 0.001) : 0;
    const speed = g.degAt >= 0 ? g.degSpeed * 0.7 + inst * 0.3 : 0;
    const gap = speed > GESTURE.tickFastDeg ? GESTURE.tickGapFast : GESTURE.tickGap;
    let tickAt = g.tickAt;
    let wasOff = g.wasOff;
    if ((tb !== g.tickB || tt !== g.tickT) && now - g.tickAt > gap) {
      tickAt = now;
      if (g.wasOff && b > 0.02) {
        wasOff = false;
        scheduleOnRN(cue.click);
      } else scheduleOnRN(cue.tick);
    }
    grip.set({ ...g, tickB: tb, tickT: tt, tickAt, wasOff, degPrev: deg, degAt: now, degSpeed: speed });
  };

  // External UI-thread writes wake a dormant control; steady values do not schedule any JS work.
  useAnimatedReaction(
    () => [bL.get(), wL.get(), mL.get(), mwL.get(), pressL.get(), afterL.get(), pinned.get(), playing.get(), touching.get()],
    (next, prev) => {
      if ((!prev || next.some((v, i) => v !== prev[i])) && !awake.get()) {
        awake.set(true);
        scheduleOnRN(setRunning, true);
      }
    },
  );

  const frames = useFrameCallback((frame) => {
    'worklet';
    const dt = Math.min((frame.timeSincePreviousFrame ?? 16.7) / 1000, 0.05);
    const now = clock.get() + dt;
    clock.set(now);
    const pin = pinned.get();
    if (pin >= 0) {
      look.set(lookAt(pin));
      awake.set(false);
      scheduleOnRN(setRunning, false);
      return;
    }
    if (playing.get()) {
      const t = (loopT.get() + dt) % LOOP;
      loopT.set(t);
      const k = lookAt(t);
      if (on === false) {
        k.b = 0;
        k.hot = 0;
      }
      if (expanded !== undefined) {
        k.m = expanded ? 1 : 0;
        k.mw = k.m;
      }
      look.set(k);
      if (mirror) {
        bL.set(k.b);
        wL.set(k.w);
      }
      return;
    }

    // the sun as the handle: the finger is the clock, both values follow its position (after a short glide when it
    // landed far from the sun)
    if (handleOn.get()) {
      const g = grip.get();
      const s = mix(0.55, 1, Math.min(mL.get(), mwL.get())) * pressL.get();
      const lensPx = top + (lensY - top) * s;
      const sol = solveSun(Math.abs(fx.get() - cx) / (S * s), (lensPx - fy.get()) / (S * s), g.b0, g.w0, aMin, aMax, ref.sun.along);
      const x = g.glide ? Math.min((now - g.t0) / GESTURE.sunGlide, 1) : 1;
      const e =
        x >= 1
          ? 1
          : springAt(x * GESTURE.sunGlide, SPRING.press.response, SPRING.press.zeta) /
            springAt(GESTURE.sunGlide, SPRING.press.response, SPRING.press.zeta);
      const rawB = mix(g.b0, sol.b, e);
      const rawW = mix(g.w0, sol.w, e);
      bL.set(rubber(rawB));
      wL.set(rubber(rawW));
      grip.set({ ...g, rawB });
      tickCheck(rubber(rawB), rubber(rawW));
      touchedAt.set(now);
    }

    const b = on === false ? 0 : bL.get();
    const w = wL.get();
    const bC = clamp01(b);
    const wC = clamp01(w);
    let hot = hotL.get();
    const tau = b > hot ? 0.06 : 0.14; // incandescent lag: quick to heat, slow to cool
    hot += (b - hot) * (1 - Math.exp(-dt / tau));
    hotL.set(hot);
    // the speed of both axes, for the release of the sun
    vB.set(vB.get() * 0.6 + 0.4 * ((b - prevB.get()) / Math.max(dt, 0.001)));
    vW.set(vW.get() * 0.6 + 0.4 * ((w - prevW.get()) / Math.max(dt, 0.001)));
    prevB.set(b);
    prevW.set(w);

    if (focusAt.get() >= 0 && now >= focusAt.get()) {
      focusAt.set(-1);
      if (!touching.get() && !reduce) scheduleOnRN(cue.focusLock);
    }

    // a held pencil leaves an afterimage in the fan as it opens
    if (!reduce) {
      const held = wC < 0.12 && bC > 0.5;
      if (held) dwell.set(dwell.get() + dt);
      else {
        if (dwell.get() >= GESTURE.dwell && bC > 0.3) {
          afterL.set(
            withSequence(
              withTiming(1, { duration: 80 }),
              withTiming(0, { duration: GESTURE.afterFade * 1000, easing: Easing.out(Easing.quad) }),
            ),
          );
        }
        dwell.set(0);
      }
      // the pencil lands on the ceiling: one Rigid beat, re-armed below 190 pt
      const reach = optics.get().reach;
      if (!ceilOn.get() && reach >= GESTURE.ceilingOn && bC > 0.9 && wC < 0.1) {
        ceilOn.set(true);
        scheduleOnRN(cue.ceiling);
      } else if (ceilOn.get() && (reach < GESTURE.ceilingOff || wC > 0.15)) ceilOn.set(false);
    }

    // what the host hears: at most every 60 ms while either axis moves, once more when they settle
    if (Math.abs(bC - lastB.get()) + Math.abs(wC - lastW.get()) > 0.0008) {
      lastB.set(bC);
      lastW.set(wC);
      moveAt.set(now);
      pend.set(true);
    }
    const deg = (optics.get().theta * 180) / Math.PI;
    if (pend.get() && now - lastEmit.get() >= 0.06) {
      pend.set(false);
      lastEmit.set(now);
      scheduleOnRN(emitChange, bC, wC, deg);
    }
    const isOn = bC > 0.01;
    if (on === undefined && isOn !== lastOnState.get() && !touching.get()) {
      lastOnState.set(isOn);
      scheduleOnRN(emitToggle, isOn);
    }
    if (endPending.get() && !touching.get() && !pend.get() && now - moveAt.get() > 0.15) {
      endPending.set(false);
      scheduleOnRN(emitEnd, bC, wC, deg);
    }

    // nothing moving, nothing drawn: the look is only rewritten (and the canvas only redrawn) when a value changed
    if (Math.abs(hot - b) < 1e-4) hot = b;
    hotL.set(hot);
    const prev = look.get();
    const shimmers = !reduce && bC > 0.01 && 1 - wC > 0.02 && (touching.get() || now - touchedAt.get() < GESTURE.resumeAfter); // the heat shimmer only breathes in a focused beam
    const next = {
      b,
      w,
      m: mL.get(),
      mw: mwL.get(),
      press: pressL.get(),
      hot: reduce ? b : hot,
      phase: shimmers ? (now / LOOP) % 1 : prev.phase,
      after: reduce ? 0 : afterL.get(),
    };
    if (
      shimmers ||
      next.b !== prev.b ||
      next.w !== prev.w ||
      next.m !== prev.m ||
      next.mw !== prev.mw ||
      next.press !== prev.press ||
      next.hot !== prev.hot ||
      next.after !== prev.after
    ) {
      look.set(next);
    }
    if (!autoPlay || reduce || on !== undefined || expanded !== undefined) {
      const still =
        !touching.get() &&
        focusAt.get() < 0 &&
        !endPending.get() &&
        !pend.get() &&
        Math.abs(b - prev.b) +
          Math.abs(w - prev.w) +
          Math.abs(hot - b) +
          Math.abs(next.m - prev.m) +
          Math.abs(next.mw - prev.mw) +
          Math.abs(next.press - prev.press) +
          Math.abs(next.after - prev.after) <
          1e-5;
      if (still && !shimmers && now - touchedAt.get() > GESTURE.resumeAfter) {
        awake.set(false);
        scheduleOnRN(setRunning, false);
      }
      return;
    }
    if (touching.get() || now - touchedAt.get() < GESTURE.resumeAfter) return;
    if (!resuming.get()) {
      // ease back to the rest frame, then the loop starts again at t = 0
      resuming.set(true);
      const ease = { duration: 600, easing: Easing.bezier(0.65, 0, 0.35, 1) };
      bL.set(withTiming(1, ease));
      wL.set(withTiming(1, ease));
      mL.set(withTiming(1, ease));
      mwL.set(withTiming(1, ease));
      pressL.set(withTiming(1, ease));
      openT.set(true);
      return;
    }
    const settled =
      Math.abs(b - 1) < 0.002 &&
      Math.abs(wL.get() - 1) < 0.002 &&
      Math.abs(mL.get() - 1) < 0.002 &&
      Math.abs(mwL.get() - 1) < 0.002 &&
      Math.abs(hot - 1) < 0.01;
    if (settled) {
      resuming.set(false);
      loopT.set(0);
      playing.set(true);
    }
  });

  useEffect(() => {
    frames.setActive(running);
  }, [frames, running]);

  // ---- what the canvas reads ----
  const capRect = useDerivedValue(() => {
    const k = look.get();
    const h = mix(ISL.h, CAP.h, k.m) * k.press;
    const w = mix(ISL.w, CAP.w, k.mw) * k.press;
    return { x: cx - w / 2, y: top, w, h, r: mix(ISL.r, CAP.r, Math.min(k.m, k.mw)) };
  });
  const capsule = useDerivedValue(() => {
    const c = capRect.get();
    return squirclePath(c.x, c.y, c.w, c.h, c.r);
  });
  // the content shrinks toward the island with the capsule, and fades before it reaches it
  const inner = useDerivedValue(() => {
    const k = look.get();
    return { s: mix(0.55, 1, Math.min(k.m, k.mw)) * k.press, show: smooth(0.4, 0.92, Math.min(k.m, k.mw)) };
  });
  const innerT = useDerivedValue(() => {
    const s = inner.get().s;
    return [{ translateX: cx }, { translateY: top }, { scale: s }, { translateX: -cx }, { translateY: -top }];
  });
  const innerShow = useDerivedValue(() => inner.get().show);

  const uniforms = useDerivedValue(() => {
    const k = look.get();
    const o = optics.get();
    const c = capRect.get();
    const s = inner.get().s;
    return {
      uLens: [cx, top + (lensY - top) * s],
      uS: S * s,
      uB: clamp01(k.b),
      uHot: clamp01(k.hot),
      uTheta: o.theta,
      uReach: o.reach,
      uGain: o.gain,
      uWide: clamp01(k.w),
      uCap: [c.x, c.y, c.w, c.h],
      uCapR: c.r,
      uShow: inner.get().show,
      uPhase: k.phase,
      uStill: reduce ? 1 : 0,
      uGlass: th.glass,
      uLight: th.light,
      uFringe: th.fringe,
      uAfter: k.after,
      uAfterReach: AFTER_REACH,
      uP3: P3,
    };
  });

  const arc = useDerivedValue(() => {
    const o = optics.get();
    return arcPath(cx, lensY - o.reach * S, o.hw * S, o.sag * S);
  });
  const dotArc = useDerivedValue(() => {
    const o = optics.get();
    return arcPath(cx, lensY - o.reachMax * S, o.hwMax * S, o.sagMax * S);
  });
  const arcAlpha = useDerivedValue(() => optics.get().arcAlpha * 0.78);
  const dotAlpha = useDerivedValue(() => optics.get().dots * 0.85);
  const sunT = useDerivedValue(() => {
    const o = optics.get();
    const g = arcGeometry(cx, lensY - o.reach * S, o.hw * S, o.sag * S);
    const along = ref.sun.along * S;
    return [{ translateX: g.ex + g.tx * along }, { translateY: g.ey + g.ty * along }, { scale: S * (1 + 0.25 * sunGrab.get()) }];
  });
  const sunAlpha = useDerivedValue(() => Math.min(optics.get().arcAlpha * 0.95 + 0.25 * sunGrab.get(), 1));
  const glyphLit = useDerivedValue(() => clamp01((optics.get().glyph - 0.45) / 0.55));
  const glyphHot = useDerivedValue(() => clamp01(look.get().hot));
  const glyphT = [{ translateX: glyphX }, { translateY: glyphY }, { scale: S }];
  const shadowY = ref.elevation.ambient[1] * S;
  const contactY = ref.elevation.contact[1] * S;
  const shadowT = useDerivedValue(() => [{ translateY: shadowY * smooth(0.2, 1, look.get().m) }]);
  const contactT = [{ translateY: contactY }];
  // The page follows its single light source: a white veil, a ceiling halo, a shade on switch-off.
  const glowA = useDerivedValue(() => SPILL.glow * th.spill * clamp01(look.get().b) * clamp01(look.get().w) * inner.get().show);
  const haloA = useDerivedValue(() => {
    const k = look.get();
    return SPILL.halo * th.spill * landing(optics.get().reach, k.w, k.b) * inner.get().show;
  });
  const darkA = useDerivedValue(() => SPILL.dark * th.spill * (1 - clamp01(look.get().hot)));
  const shadowA = useDerivedValue(() => mix(SPILL.shadowOff, SPILL.shadowOn, clamp01(look.get().b)));
  // only the capsule (or the island) takes touches, with a little slop; the rest of the screen is the host's
  const hitStyle = useAnimatedStyle(() => {
    const c = capRect.get();
    const slop = look.get().m > 0.9 ? 6 * S : 14;
    return { position: 'absolute' as const, left: c.x - slop, top: c.y - slop, width: c.w + 2 * slop, height: c.h + 2 * slop };
  });

  // ---- the finger ----
  const takeOver = () => {
    'worklet';
    resuming.set(false);
    const k = look.get();
    // Cancel every in-flight spring before reading it: re-grabbing or reversing never fights a previous release.
    cancelAnimation(bL);
    cancelAnimation(wL);
    cancelAnimation(mL);
    cancelAnimation(mwL);
    cancelAnimation(pressL);
    if (!playing.get()) return;
    playing.set(false);
    bL.set(k.b);
    wL.set(k.w);
    mL.set(k.m);
    mwL.set(k.mw);
    pressL.set(k.press);
    hotL.set(k.hot);
    openT.set(k.m > 0.5);
    prevB.set(k.b);
    prevW.set(k.w);
    lastB.set(clamp01(k.b));
    lastW.set(clamp01(k.w));
    lastOnState.set(k.b > 0.01);
  };
  const report = () => {
    'worklet';
    endPending.set(true);
    scheduleOnRN(setA11y, { b: clamp01(bL.get()), w: clamp01(wL.get()), open: openT.get() });
  };

  const toggle = () => {
    'worklet';
    if (on !== undefined) {
      scheduleOnRN(emitToggle, !on);
      scheduleOnRN(cue.click);
      return;
    }
    if (bL.get() > 0.01) {
      lastOn.set(Math.max(bL.get(), 0.35));
      bL.set(withTiming(0, { duration: reduce ? 200 : 220, easing: Easing.bezier(0.32, 0, 0.67, 0) }));
    } else {
      bL.set(reduce ? withTiming(lastOn.get(), { duration: 200 }) : withSpring(lastOn.get(), springCfg(0.45, 1)));
    }
    scheduleOnRN(cue.click);
  };
  const setOpen = (open: boolean, user: boolean) => {
    'worklet';
    if (expanded !== undefined && user) {
      scheduleOnRN(emitExpanded, open);
      return;
    }
    const cfg = open ? springCfg(SPRING.expand.response, SPRING.expand.zeta) : springCfg(SPRING.collapse.response, SPRING.collapse.zeta);
    openT.set(open);
    if (reduce) {
      // Fade out, replace the geometry while invisible, fade in: no island travel under Reduce Motion.
      reducedFade.set(
        withTiming(0, { duration: 100 }, (finished) => {
          if (!finished) return;
          mL.set(open ? 1 : 0);
          mwL.set(open ? 1 : 0);
          reducedFade.set(withTiming(1, { duration: 100 }));
        }),
      );
    } else {
      mL.set(withSpring(open ? 1 : 0, cfg));
      mwL.set(withDelay(open ? 0 : 50, withSpring(open ? 1 : 0, cfg)));
    }
    if (open && bL.get() < 0.01) bL.set(withDelay(250, withSpring(lastOn.get(), springCfg(0.9, 1))));
    scheduleOnRN(cue.reveal);
    if (user) scheduleOnRN(emitExpanded, open);
  };

  // controlled props: the host flips them, the capsule follows from where it is
  useEffect(() => {
    if (on === undefined) return;
    const isOn = bL.get() > 0.01;
    if (on === isOn) return;
    takeOver();
    if (on) bL.set(reduce ? withTiming(lastOn.get(), { duration: 200 }) : withSpring(lastOn.get(), springCfg(0.45, 1)));
    else {
      lastOn.set(Math.max(bL.get(), 0.35));
      bL.set(withTiming(0, { duration: 220, easing: Easing.bezier(0.32, 0, 0.67, 0) }));
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [on]);
  useEffect(() => {
    if (expanded === undefined || expanded === openT.get()) return;
    takeOver();
    setOpen(expanded, false);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [expanded]);

  const pan = Gesture.Pan()
    .minDistance(0)
    .onBegin((e) => {
      'worklet';
      focusAt.set(-1);
      touching.set(true);
      touchedAt.set(clock.get());
      const k = look.get();
      const c = capRect.get();
      const open = k.m > 0.9;
      const slop = 6 * S;
      const px = e.absoluteX;
      const py = e.absoluteY;
      const inCap = px > c.x - slop && px < c.x + c.w + slop && py > c.y - slop && py < c.y + c.h + slop;
      const inIsland = px > ISL.x - 14 && px < ISL.x + ISL.w + 14 && py > ISL.y - 14 && py < ISL.y + ISL.h + 14;
      const region = open && inCap ? 1 : !open && inIsland ? 2 : 3;
      const g = { ...NO_GRIP, region, x0: px, y0: py, t0: clock.get() };
      if (region === 1) {
        takeOver();
        const b = bL.get();
        const w = wL.get();
        const deg = (opticsOf(b, w, aMin, aMax).theta * 180) / Math.PI;
        g.b0 = b;
        g.w0 = w;
        g.rawB = b;
        g.d0 = Math.abs(px - cx);
        g.tickB = Math.floor(clamp01(b) * 10 + 1e-4);
        g.tickT = Math.floor(deg / GESTURE.tickTheta);
        g.wasOff = b < 0.01;
        g.degPrev = deg;
        // the sun is the handle: the hit test at touch-down decides, so the relative drag keeps everything else
        const s = inner.get().s;
        const so = sunOffset(clamp01(b), clamp01(w), aMin, aMax, ref.sun.along);
        const sunX = cx + so.x * S * s;
        const sunY = top + (lensY - top) * s - so.y * S * s;
        const away = Math.hypot(px - sunX, py - sunY);
        if (away <= GESTURE.sunHit * S) {
          g.handle = true;
          g.glide = away > GESTURE.sunNear * S;
          handleOn.set(true);
          fx.set(px);
          fy.set(py);
          if (!reduce) sunGrab.set(withSpring(1, snap));
        }
        if (!reduce) pressL.set(withSpring(GESTURE.pressScale, snap));
        scheduleOnRN(cue.onset);
      }
      grip.set(g);
    })
    .onUpdate((e) => {
      'worklet';
      const g = { ...grip.get() };
      const dist = Math.hypot(e.translationX, e.translationY);
      g.moved = Math.max(g.moved, dist);
      if (g.region !== 1) {
        grip.set(g);
        return;
      }
      if (g.handle) {
        fx.set(e.absoluteX);
        fy.set(e.absoluteY);
        grip.set({ ...grip.get(), moved: g.moved });
        return;
      }
      if (g.axis === 0 && dist > GESTURE.axisLead * S) g.axis = Math.abs(e.translationX) > Math.abs(e.translationY) ? 1 : 2;
      const gy = g.axis === 1 ? GESTURE.follow : 1;
      const gx = g.axis === 2 ? GESTURE.follow : 1;
      g.rawB = g.b0 - (e.translationY * gy) / (GESTURE.ptPerBrightness * S);
      const b = rubber(g.rawB);
      bL.set(b);
      if (!g.pinch) {
        const rawW = g.w0 + ((Math.abs(e.absoluteX - cx) - g.d0) * gx) / (GESTURE.ptPerSpread * S);
        wL.set(rubber(rawW));
      }
      touchedAt.set(clock.get());
      grip.set(g);
      tickCheck(b, wL.get());
    })
    .onEnd((e) => {
      'worklet';
      const g = grip.get();
      const tap = g.moved < GESTURE.tapSlop * S && clock.get() - g.t0 < GESTURE.tapTime;
      if (g.region === 1 && g.handle) {
        handleOn.set(false);
        sunGrab.set(withSpring(0, snap));
        if (g.rawB < GESTURE.offBelow) {
          lastOn.set(Math.max(g.b0, 0.35));
          bL.set(withTiming(0, { duration: 180, easing: Easing.bezier(0.23, 1, 0.32, 1) }));
          scheduleOnRN(cue.click);
        } else {
          const b = clamp01(bL.get());
          const w = clamp01(wL.get());
          const bT = clamp01(b + vB.get() * GESTURE.projectB);
          const wT = clamp01(w + vW.get() * GESTURE.projectW);
          bL.set(withTiming(bT, { duration: 140, easing: Easing.bezier(0.23, 1, 0.32, 1) }));
          wL.set(reduce ? withTiming(wT, { duration: 0 }) : withSpring(wT, { ...pop, velocity: vW.get() }));
          if (Math.abs(wT - g.w0) > 0.08 && !reduce) focusAt.set(clock.get() + FOCUS_LOCK_DELAY_MS / 1000);
        }
      } else if (g.region === 1) {
        if (tap) {
          toggle();
        } else if (g.rawB < GESTURE.offBelow) {
          lastOn.set(Math.max(g.b0, 0.35));
          bL.set(withTiming(0, { duration: 180, easing: Easing.bezier(0.23, 1, 0.32, 1) }));
          scheduleOnRN(cue.click);
        } else {
          const gy = g.axis === 1 ? GESTURE.follow : 1;
          const gx = g.axis === 2 ? GESTURE.follow : 1;
          const vb = (-e.velocityY * gy) / (GESTURE.ptPerBrightness * S);
          const bT = clamp01(clamp01(bL.get()) + vb * GESTURE.projectB);
          bL.set(withTiming(bT, { duration: 140, easing: Easing.bezier(0.23, 1, 0.32, 1) }));
          if (!g.pinch) {
            const side = e.absoluteX >= cx ? 1 : -1;
            const vw = (side * e.velocityX * gx) / (GESTURE.ptPerSpread * S);
            const w = wL.get();
            const wT = clamp01(clamp01(w) + vw * GESTURE.projectW);
            wL.set(reduce ? withTiming(wT, { duration: 0 }) : withSpring(wT, { ...pop, velocity: vw }));
            if (Math.abs(wT - g.w0) > 0.08 && !reduce) focusAt.set(clock.get() + FOCUS_LOCK_DELAY_MS / 1000);
          }
        }
      } else if (tap && g.region === 2) {
        takeOver();
        setOpen(true, true);
      }
    })
    .onFinalize(() => {
      'worklet';
      const g = grip.get();
      handleOn.set(false);
      if (g.handle) sunGrab.set(withSpring(0, snap));
      if (g.region === 1 && !reduce) pressL.set(withSpring(1, snap));
      touching.set(false);
      touchedAt.set(clock.get());
      if (g.region !== 0) report();
      grip.set({ ...g, region: 0, handle: false });
    });

  const pinch = Gesture.Pinch()
    .onBegin(() => {
      'worklet';
      const g = grip.get();
      if (g.region !== 1 || g.handle) return;
      grip.set({ ...g, pinch: true, wp0: wL.get() });
    })
    .onUpdate((e) => {
      'worklet';
      const g = grip.get();
      if (!g.pinch) return;
      wL.set(rubber(g.wp0 + (e.scale - 1) * GESTURE.pinchGain));
    })
    .onEnd((e) => {
      'worklet';
      const g = grip.get();
      if (!g.pinch) return;
      const vw = e.velocity * GESTURE.pinchGain;
      const wT = clamp01(clamp01(wL.get()) + vw * GESTURE.projectW);
      wL.set(reduce ? withTiming(wT, { duration: 0 }) : withSpring(wT, { ...pop, velocity: vw }));
      if (!reduce) focusAt.set(clock.get() + FOCUS_LOCK_DELAY_MS / 1000);
    });

  const gesture = Gesture.Simultaneous(pan, pinch);

  // a tap on the page while the capsule is open closes it into the island
  const closeTap = Gesture.Tap()
    .maxDuration(400)
    .onEnd((_e, ok) => {
      'worklet';
      if (!ok || look.get().m < 0.9) return;
      touchedAt.set(clock.get());
      takeOver();
      setOpen(false, true);
      report();
    });

  // ---- VoiceOver: one adjustable control, with actions for focus and on/off ----
  const stop = () => {
    playing.set(false);
    resuming.set(false);
    touchedAt.set(clock.get());
    endPending.set(true);
  };
  const onA11y = (ev: AccessibilityActionEvent) => {
    takeOver();
    stop();
    const b = clamp01(bL.get());
    const w = clamp01(wL.get());
    const fade = { duration: 200 };
    const next = { ...a11y };
    switch (ev.nativeEvent.actionName) {
      case 'increment':
        next.b = Math.min(1, Math.round(b * 10 + 1) / 10);
        bL.set(withTiming(next.b, fade));
        break;
      case 'decrement':
        next.b = Math.max(0, Math.round(b * 10 - 1) / 10);
        bL.set(withTiming(next.b, fade));
        break;
      case 'narrow':
        next.w = Math.max(0, w - 0.25);
        wL.set(withTiming(next.w, fade));
        break;
      case 'widen':
        next.w = Math.min(1, w + 0.25);
        wL.set(withTiming(next.w, fade));
        break;
      case 'activate':
        if (!isOpen) {
          if (expanded === undefined) {
            setOpen(true, false);
            next.open = true;
          }
          emitExpanded(true);
        } else {
          if (on !== undefined) {
            emitToggle(!on);
            break;
          }
          next.b = b > 0.01 ? 0 : lastOn.get();
          if (b > 0.01) lastOn.set(Math.max(b, 0.35));
          bL.set(withTiming(next.b, fade));
        }
        break;
      case 'escape':
        if (expanded === undefined) {
          setOpen(false, false);
          next.open = false;
        }
        emitExpanded(false);
        break;
    }
    setA11y(next);
  };
  const percent = (v: number) => `${Math.round(v * 100)} percent`;
  const a11yValue = a11y.b < 0.01 ? 'Off' : `Brightness ${percent(a11y.b)}, beam ${a11y.w < 0.15 ? 'focused' : `width ${percent(a11y.w)}`}`;

  const Glyph = renderGlyph ?? TorchGlyph;
  const glyph: ReactNode = <Glyph lit={glyphLit} hot={glyphHot} />;

  return (
    <View style={StyleSheet.absoluteFill} pointerEvents="box-none">
      <View style={StyleSheet.absoluteFill} pointerEvents="none">
        <Canvas style={StyleSheet.absoluteFill} accessible={false}>
          <Group opacity={reducedFade}>
            {th.spill > 0 ? (
              <>
                {/* the page follows the light: shade when the lamp is off, a veil round the capsule while it burns, a halo
                  at the top edge where the pencil lands */}
                <Group opacity={darkA} blendMode="multiply">
                  <Rect x={0} y={0} width={W} height={H} color={C.shadow} />
                </Group>
                <Group opacity={glowA} blendMode="screen">
                  <Rect x={0} y={0} width={W} height={H}>
                    <RadialGradient
                      c={vec(cx, top + SPILL.glowFrom * S)}
                      r={SPILL.glowRadius * S}
                      colors={[C.spill, C.spill, `${C.spill}00`]}
                      positions={[0, 0.5, 1]}
                    />
                  </Rect>
                </Group>
                <Group opacity={haloA} blendMode="screen">
                  <Rect x={0} y={0} width={W} height={H}>
                    <RadialGradient c={vec(cx, top)} r={SPILL.haloRadius * S} colors={[C.ceiling, `${C.ceiling}00`]} />
                  </Rect>
                </Group>
              </>
            ) : null}
            {/* the capsule floats: an ambient shadow that sinks as it opens and deepens with the lamp off, and a tight contact shadow */}
            <Group transform={shadowT}>
              <Path path={capsule} color={C.shadow} opacity={shadowA}>
                <BlurMask blur={ref.elevation.ambient[2] * S * 0.5} style="normal" />
              </Path>
            </Group>
            <Group transform={contactT}>
              <Path path={capsule} color={C.shadow} opacity={ref.elevation.contact[3]}>
                <BlurMask blur={ref.elevation.contact[2] * S * 0.5} style="normal" />
              </Path>
            </Group>
            <Path path={capsule}>
              <Shader source={beamFx!} uniforms={uniforms} />
            </Path>
            <Path path={capsule} style="stroke" strokeWidth={StyleSheet.hairlineWidth} color={C.rim} opacity={0.06} />
            <Group clip={capsule}>
              <Group transform={innerT} opacity={innerShow}>
                {/* screen blend: where the pencil lands on the ceiling the arc melts into the light instead of reading as a dark bar */}
                <Path
                  path={dotArc}
                  style="stroke"
                  strokeWidth={ref.dots.size * S}
                  strokeCap="round"
                  color={th.arc}
                  opacity={dotAlpha}
                  blendMode="screen"
                >
                  <DashPathEffect intervals={[0.01, ref.dots.pitch * S]} />
                </Path>
                <Path
                  path={arc}
                  style="stroke"
                  strokeWidth={ref.arc.stroke * S}
                  strokeCap="round"
                  color={th.arc}
                  opacity={arcAlpha}
                  blendMode="screen"
                />
                <Group transform={sunT} opacity={sunAlpha}>
                  <Path path={SUN} style="stroke" strokeWidth={ref.sun.stroke} strokeCap="round" color={th.arc} />
                </Group>
                <Group transform={glyphT}>{glyph}</Group>
              </Group>
            </Group>
          </Group>
        </Canvas>
      </View>
      {closeOnOutsideTap && isOpen ? (
        <GestureDetector gesture={closeTap}>
          <View style={StyleSheet.absoluteFill} />
        </GestureDetector>
      ) : null}
      <GestureDetector gesture={gesture}>
        <Animated.View style={hitStyle}>
          <View
            accessible
            accessibilityRole="adjustable"
            accessibilityLabel={accessibilityLabel ?? COPY.label}
            accessibilityHint={isOpen ? COPY.hintOpen : COPY.hintClosed}
            accessibilityValue={{ text: isOpen ? a11yValue : 'Closed' }}
            accessibilityActions={[
              { name: 'increment' },
              { name: 'decrement' },
              { name: 'activate' },
              { name: 'narrow', label: COPY.narrow },
              { name: 'widen', label: COPY.widen },
              { name: 'escape', label: COPY.close },
            ]}
            onAccessibilityAction={onA11y}
            style={StyleSheet.absoluteFill}
          />
        </Animated.View>
      </GestureDetector>
    </View>
  );
}
