// Object Sign-In Dissolve — a real pear on a sign-in screen. One drag turns it (x, 1:1) and holds a plane across it (y):
// above the finger the fruit is a magenta shell of scan lines, below it real skin, a 3 pt seam between. The theme toggle
// wipes from the bottom-left corner as a disc; the pear is lit for the page on each side of the arc.
// Exports: `ObjectSignIn` (the screen) and `DissolveObject` (the hero alone).
import { StatusBar } from 'expo-status-bar';
import { useEffect, useMemo, useRef, useState } from 'react';
import { type AccessibilityActionEvent, Platform, Pressable, StyleSheet, useWindowDimensions, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import { type SharedValue, useAnimatedReaction, useDerivedValue, useFrameCallback, useSharedValue } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import {
  Canvas,
  Circle,
  Group,
  LinearGradient,
  Paint,
  Path,
  RadialGradient,
  Rect,
  RoundedRect,
  Shader,
  Skia,
  Text,
  type SkFont,
  useFont,
  vec,
} from '@shopify/react-native-skia';
import { scheduleOnRN } from 'react-native-worklets';

import { useReducedMotionPreference } from '../../../shared/useReducedMotionPreference';
import { haptic } from './osd-haptics';
import {
  clamp,
  GESTURE,
  heroNear,
  type Live,
  liveFrom,
  type Look,
  lookAt,
  lookOfLive,
  LOOP,
  mix,
  PLANE,
  planeY,
  PULSE,
  stepLive,
  WIPE,
  wipeRadius,
  wipeReach,
  zipSince,
  seamOf,
  YAW0,
  LAG,
} from './osd-motion';
import { PEAR_SKSL } from './osd-shaders';
import { COPY, makeLayout, ref, type Layout } from './osd-theme';

export { LOOP };

const pearFx = Skia.RuntimeEffect.Make(PEAR_SKSL);
const P3 = Platform.OS === 'ios' ? 1 : 0;
const C = ref.color;
const D2R = Math.PI / 180;

type ProviderIcon = 'mail' | 'key' | 'phone';
export type Provider = { id: string; label: string; icon: ProviderIcon; primary?: boolean };
export const PROVIDERS: Provider[] = [
  { id: 'email', label: COPY.continue, icon: 'mail', primary: true },
  { id: 'passkey', label: COPY.google, icon: 'key' },
  { id: 'phone', label: COPY.wallet, icon: 'phone' },
];

// ---- colour helpers (module level, plain JS) ----
function hexToRgb(h: string) {
  const v = parseInt(h.slice(1), 16);
  return [(v >> 16) & 255, (v >> 8) & 255, v & 255];
}
function mixHex(a: string, b: string, t: number) {
  const x = hexToRgb(a);
  const y = hexToRgb(b);
  const m = x.map((c, i) => Math.round(c + (y[i] - c) * t));
  return `#${m.map((c) => c.toString(16).padStart(2, '0')).join('')}`;
}
const rgb01 = (h: string) => hexToRgb(h).map((c) => c / 255);
const SHELL = rgb01(C.shell);
const SEAM = rgb01(C.seam);
const SEAM_CORE = rgb01(C.seamCore);

// ---- the engine: timeline + finger -> one `look` the canvas reads ----
type Engine = {
  look: SharedValue<Look>;
  clock: SharedValue<number>;
  glow: SharedValue<{ pool: number; word: number; pill: number; zip: number }>;
  gesture: ReturnType<typeof Gesture.Pan>;
  setTheme: (light: boolean) => void;
  lightMix: SharedValue<number>;
  a11yBump: (dir: number) => void;
};
type Config = {
  autoPlay: boolean;
  reduce: boolean;
  box: { x: number; y: number; w: number; h: number }; // the pear's touch box
  cy: number; // pear centre, canvas y
  S: number;
  onTheme?: (light: boolean) => void;
  defaultLight?: boolean;
};

function useEngine(cfg: Config): Engine {
  const { autoPlay, reduce, box, cy, S } = cfg;
  const clock = useSharedValue(0);
  const loopT = useSharedValue(0);
  const playing = useSharedValue(autoPlay && !reduce);
  const pinned = useSharedValue(-1);
  const initial = useMemo(() => {
    const l = lookAt(0);
    return cfg.defaultLight ? { ...l, wipeP: 1 } : l;
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);
  const look = useSharedValue<Look>(initial);
  const live = useSharedValue<Live>(liveFrom(initial));
  const glow = useSharedValue({ pool: 0, word: 0, pill: 0, zip: -1 });
  const lightMix = useSharedValue(cfg.defaultLight ? 1 : 0);
  const held = useSharedValue(false);
  const touchedAt = useSharedValue(-1e9);
  const fingerC = useSharedValue(0);
  const grip = useSharedValue({
    on: 0,
    x0: 0,
    y0: 0,
    yaw0: 0,
    t0: 0,
    lastX: 0,
    lastT: 0,
    v: 0,
    moved: 0,
    tickC: 0,
    ticked: 0,
    lastTick: 0,
    spun: 0,
  });
  const hint = useSharedValue(-1);
  const wasOver = useSharedValue(false);
  const resuming = useSharedValue(false);

  useEffect(() => {
    playing.set(autoPlay && !reduce);
  }, [autoPlay, reduce, playing]);

  // Development only: `__osd.pin(t)` freezes the loop at t s, `__osd.play()` lets it run, `__osd.state()` reads the look.
  useEffect(() => {
    if (!__DEV__) return;
    (globalThis as Record<string, unknown>).__osd = {
      pin: (t: number) => pinned.set(t),
      at: (t: number) => pinned.set(t),
      play: () => pinned.set(-1),
      state: () => ({ ...look.get(), loopT: loopT.get(), playing: playing.get() }),
      theme: (p: number) => {
        pinned.set(-2);
        look.set({ ...look.get(), wipeP: p });
      },
    };
  }, [pinned, look, loopT, playing]);

  const closeHaptic = () => haptic.close();
  const tickHaptic = () => haptic.tick();
  const spinHaptic = () => haptic.spin();

  const takeOver = () => {
    'worklet';
    resuming.set(false);
    if (playing.get()) {
      live.set(liveFrom(look.get()));
      playing.set(false);
    }
  };

  useFrameCallback((frame) => {
    'worklet';
    const dt = Math.min((frame.timeSincePreviousFrame ?? 16.7) / 1000, 0.05);
    const now = clock.get() + dt;
    clock.set(now);
    const pin = pinned.get();
    let k: Look;
    if (pin >= 0) {
      k = lookAt(pin);
      const t = pin % LOOP;
      const lag = (s: number) => lookAt(t - s).c;
      glow.set({ pool: lag(LAG.pool), word: lag(LAG.word), pill: lag(LAG.pill), zip: zipSince(t) });
      look.set(k);
      lightMix.set(k.wipeP > 0.5 ? 1 : 0);
      return;
    }
    if (pin === -2) return;
    if (playing.get()) {
      const t = (loopT.get() + dt) % LOOP;
      loopT.set(t);
      k = lookAt(t);
    } else {
      let s = live.get();
      const isHeld = held.get();
      if (!isHeld && autoPlay && !reduce && now - touchedAt.get() > GESTURE.resumeAfter) {
        // 2 s after the last touch: ease to the rest pose, then start the loop again at t = 0
        if (!resuming.get()) {
          resuming.set(true);
          const target = YAW0 + Math.round((s.yaw - YAW0) / 360) * 360;
          live.set({ ...s, yawTarget: target, qTarget: 0 });
          s = live.get();
        }
        const done =
          Math.abs(s.yaw - s.yawTarget) < 0.4 && Math.abs(s.yawV) < 4 && s.c < 0.004 && s.q <= 0.0005 && Math.abs(s.scale - 1) < 0.001;
        if (done) {
          resuming.set(false);
          loopT.set(0);
          playing.set(true);
          k = lookAt(0);
          look.set(k);
          return;
        }
      }
      const h = hint.get();
      let breathe = 0;
      if (isHeld && !reduce) {
        const g = grip.get();
        if (now - g.lastT > GESTURE.breatheAfter)
          breathe = GESTURE.breathe * Math.sin(2 * Math.PI * GESTURE.breatheHz * (now - g.lastT - GESTURE.breatheAfter));
      }
      s = stepLive(s, dt, isHeld, fingerC.get(), reduce, breathe);
      if (h >= 0) {
        // the hint: the pear turns 18 deg and back while a scan line dips 30 pt from the stalk
        const u = h / 1.1;
        if (u >= 1) hint.set(-1);
        else {
          hint.set(h + dt);
          const b = Math.sin(Math.PI * u);
          s = { ...s, yaw: s.yaw + 0, c: Math.max(s.c, 0.073 * b * b) };
        }
      }
      live.set(s);
      k = lookOfLive(s, now);
      if (h >= 0 && h / 1.1 < 1) k = { ...k, yaw: k.yaw + 18 * Math.sin(Math.PI * (h / 1.1)) * (reduce ? 0 : 1) };
      k = { ...k, seam: seamOf(k.c) };
      // haptics: the skin closing at the stalk, the spin passing the half-way
      const over = s.c > 0.15;
      if (wasOver.get() && s.c < 0.02) {
        wasOver.set(false);
        scheduleOnRN(closeHaptic);
      } else if (over) wasOver.set(true);
      const spinNow = Math.abs(s.yawV) > (GESTURE.spinLo + GESTURE.spinHi) / 2 ? 1 : 0;
      const gg = grip.get();
      if (spinNow !== gg.spun) {
        grip.set({ ...gg, spun: spinNow });
        if (!reduce) scheduleOnRN(spinHaptic);
      }
    }
    // the page answers the shell: one-pole lags of 0 / 60 / 120 ms, and one pulse when the skin closes
    const g0 = glow.get();
    const c = k.c;
    const f = (cur: number, tau: number) => (tau <= 0 ? c : cur + (c - cur) * (1 - Math.exp(-dt / tau)));
    let zip = g0.zip >= 0 ? g0.zip + dt : -1;
    if (zip > 1) zip = -1;
    if (playing.get()) zip = zipSince(loopT.get());
    else if (g0.pool > 0.2 && c < 0.02 && g0.zip < 0) zip = 0;
    glow.set({ pool: f(g0.pool, LAG.pool), word: f(g0.word, LAG.word), pill: f(g0.pill, LAG.pill), zip });
    look.set(k);
    // Reduce Motion: the light layer cross-fades (200 ms) instead of wiping
    const target = k.wipeP > 0.5 ? 1 : 0;
    lightMix.set(lightMix.get() + (target - lightMix.get()) * (1 - Math.exp(-dt / 0.07)));
  });

  const cOfY = (y: number) => {
    'worklet';
    return (y - cy - PLANE.top * S) / ((PLANE.bottom - PLANE.top) * S);
  };

  const gesture = Gesture.Pan()
    .minDistance(0)
    .onBegin((e) => {
      'worklet';
      const inBox = e.x >= box.x && e.x <= box.x + box.w && e.y >= box.y && e.y <= box.y + box.h;
      if (!inBox) {
        grip.set({ ...grip.get(), on: 0 });
        return;
      }
      takeOver();
      const s = live.get();
      held.set(true);
      touchedAt.set(clock.get());
      fingerC.set(cOfY(e.y));
      grip.set({
        on: 1,
        x0: e.x,
        y0: e.y,
        yaw0: s.yaw,
        t0: clock.get(),
        lastX: e.x,
        lastT: clock.get(),
        v: 0,
        moved: 0,
        tickC: fingerC.get(),
        ticked: 0,
        lastTick: clock.get(),
        spun: 0,
      });
      live.set({ ...s, yawV: 0 });
      if (!reduce) scheduleOnRN(haptic.onset);
    })
    .onUpdate((e) => {
      'worklet';
      const g = grip.get();
      if (!g.on) return;
      const now = clock.get();
      const s = live.get();
      const yaw = g.yaw0 + e.translationX * GESTURE.degPerPt;
      const dtt = Math.max(now - g.lastT, 1e-3);
      const v = ((e.x - g.lastX) * GESTURE.degPerPt) / dtt;
      const vf = g.v + (v - g.v) * 0.35; // a smoothed finger rate
      live.set({ ...s, yaw, yawV: vf });
      fingerC.set(cOfY(e.y));
      const moved = Math.max(g.moved, Math.hypot(e.translationX, e.translationY));
      let { tickC, lastTick } = g;
      const travelled = Math.abs(fingerC.get() - tickC) * (PLANE.bottom - PLANE.top) * S;
      if (travelled >= GESTURE.tickEvery * S && now - lastTick >= GESTURE.tickGap) {
        tickC = fingerC.get();
        lastTick = now;
        scheduleOnRN(tickHaptic);
      }
      touchedAt.set(now);
      grip.set({ ...g, lastX: e.x, lastT: now, v: vf, moved, tickC, lastTick });
    })
    .onEnd((e) => {
      'worklet';
      const g = grip.get();
      if (!g.on) return;
      const s = live.get();
      const tap = g.moved < GESTURE.tapSlop && clock.get() - g.t0 < GESTURE.tapTime;
      const fling = reduce ? 0 : clamp(e.velocityX * GESTURE.degPerPt, -GESTURE.maxTurns * 360 * 6.02, GESTURE.maxTurns * 360 * 6.02);
      const v = tap ? 0 : fling;
      live.set({ ...s, yawV: v, yawTarget: reduce ? heroNear(s.yaw, 0) : heroNear(s.yaw, v) });
      if (tap && !reduce) hint.set(0);
    })
    .onFinalize(() => {
      'worklet';
      held.set(false);
      touchedAt.set(clock.get());
      grip.set({ ...grip.get(), on: 0 });
    });

  const setTheme = (light: boolean) => {
    'worklet';
    takeOver();
    live.set({ ...live.get(), qTarget: light ? 1 : 0, q: reduce ? (light ? 1 : 0) : live.get().q });
    touchedAt.set(clock.get());
    if (cfg.onTheme) scheduleOnRN(cfg.onTheme, light);
  };
  const a11yBump = (dir: number) => {
    'worklet';
    takeOver();
    const s = live.get();
    live.set({ ...s, yawTarget: s.yaw + dir * 180, yawV: 0 });
    touchedAt.set(clock.get());
  };

  // first-open hint, 1.2 s after mount, for a screen that is not on autoPlay
  useEffect(() => {
    if (autoPlay || reduce) return;
    const id = setTimeout(() => hint.set(0), 1200);
    return () => clearTimeout(id);
  }, [autoPlay, reduce, hint]);

  return { look, clock, glow, gesture, setTheme, lightMix, a11yBump };
}

// ---- drawing helpers ----
const textW = (font: SkFont | null, s: string) => (font ? font.getGlyphWidths(font.getGlyphIDs(s)).reduce((a, b) => a + b, 0) : 0);

function iconPath(kind: ProviderIcon, x: number, y: number) {
  const p = Skia.PathBuilder.Make();
  if (kind === 'mail') {
    p.addRRect({ rect: { x: x - 8, y: y - 6, width: 16, height: 12 }, rx: 2.5, ry: 2.5 });
    p.moveTo(x - 7, y - 4.2)
      .lineTo(x, y + 1)
      .lineTo(x + 7, y - 4.2);
  } else if (kind === 'key') {
    p.addCircle(x - 4, y, 4);
    p.moveTo(x, y).lineTo(x + 8, y);
    p.moveTo(x + 5, y).lineTo(x + 5, y + 3.5);
    p.moveTo(x + 8, y).lineTo(x + 8, y + 3);
  } else {
    p.addRRect({ rect: { x: x - 5, y: y - 8, width: 10, height: 16 }, rx: 2.6, ry: 2.6 });
    p.moveTo(x - 1.6, y + 5).lineTo(x + 1.6, y + 5);
  }
  return p.build();
}
function sunPath(x: number, y: number) {
  const p = Skia.PathBuilder.Make();
  for (let i = 0; i < 8; i++) {
    const a = (i * Math.PI) / 4;
    p.moveTo(x + Math.cos(a) * 8.6, y + Math.sin(a) * 8.6).lineTo(x + Math.cos(a) * 11.2, y + Math.sin(a) * 11.2);
  }
  return p.build();
}

// ---- the page, once per theme ----
type Fonts = { word: SkFont | null; tag: SkFont | null; btn: SkFont | null; half: SkFont | null; legal: SkFont | null };

function mixHex2(a: string, b: string, t: number) {
  'worklet';
  const pa = parseInt(a.slice(1), 16);
  const pb = parseInt(b.slice(1), 16);
  const ch = (s: number) => {
    const x = (pa >> s) & 255;
    const y = (pb >> s) & 255;
    return Math.round(x + (y - x) * clamp(t, 0, 1));
  };
  const r = ch(16);
  const g = ch(8);
  const bl = ch(0);
  return `rgb(${r},${g},${bl})`;
}

function PageBack({ i, L, glow, cy }: { i: 0 | 1; L: Layout; glow: Engine['glow']; cy: number }) {
  const ground = C.ground[i];
  const base = C.pool[i];
  const tinted = mixHex(base, C.poolGlow[i], i === 0 ? 0.16 : 0.35);
  const poolColor = useDerivedValue(() => {
    const g = glow.get().pool;
    return mixHex2(base, tinted, g);
  });
  const pulse = useDerivedValue(() => {
    const z = glow.get().zip;
    if (z < 0 || z > PULSE.pool) return 0;
    const u = z / PULSE.pool;
    return PULSE.a * (1 - u) * (1 - u);
  });
  const pulseR = useDerivedValue(() => {
    const z = glow.get().zip;
    const u = clamp(z / PULSE.pool, 0, 1);
    return (PULSE.r0 + (PULSE.r1 - PULSE.r0) * (1 - (1 - u) * (1 - u))) * L.S;
  });
  const colors = useDerivedValue(() => [poolColor.get(), ground]);
  const pulseColors = useMemo(() => [C.poolGlow[i], 'rgba(0,0,0,0)'], [i]);
  return (
    <Group>
      <Rect x={0} y={0} width={L.W} height={L.H} color={ground} />
      <Circle cx={L.W / 2} cy={cy} r={ref.pool.r * L.S * 1.55} dither>
        <RadialGradient c={vec(L.W / 2, cy)} r={ref.pool.r * L.S * 1.55} colors={colors} />
      </Circle>
      <Group opacity={pulse}>
        <Circle cx={L.W / 2} cy={cy} r={pulseR} dither>
          <RadialGradient c={vec(L.W / 2, cy)} r={pulseR} colors={pulseColors} />
        </Circle>
      </Group>
    </Group>
  );
}

function PageFront({
  i,
  L,
  glow,
  fonts,
  pressed,
}: {
  i: 0 | 1;
  L: Layout;
  glow: Engine['glow'];
  fonts: Fonts;
  pressed: SharedValue<number>;
}) {
  const ink = C.ink[i];
  const W = L.W;
  const wordY = L.yt(ref.wordmark.baseline);
  const tagY = L.yt(ref.tagline.baseline);
  const pill = { x: L.x(ref.pill.x), y: L.yb(ref.pill.y), w: ref.pill.w * L.S, h: ref.pill.h * L.S, r: ref.pill.r * L.S };
  const hw = ref.half.w * L.S;
  const hy = L.yb(ref.half.y);
  const hh = ref.half.h * L.S;
  const hx0 = L.x(ref.pill.x);
  const hx1 = L.x(ref.pill.x) + pill.w - hw;
  const wordW = textW(fonts.word, COPY.wordmark);
  const wordTop = wordY - ref.type.wordmark.size * L.S * 0.7;
  const wordColors = useMemo(() => [C.shellWord, 'rgba(255,140,200,0)'], []);
  const wordOpacity = useDerivedValue(() => clamp(glow.get().word, 0, 1) * 0.5);
  const sweepX = useDerivedValue(() => {
    const z = glow.get().zip;
    if (z < 0 || z > PULSE.word) return -999;
    return W / 2 - wordW / 2 - 40 + (z / PULSE.word) * (wordW + 80);
  });
  const pillEdge = useDerivedValue(() => clamp(glow.get().pill, 0, 1) * 0.6);
  const t0 = useDerivedValue(() => [{ scale: pressed.get() === 0 ? 0.97 : 1 }]);
  const t1 = useDerivedValue(() => [{ scale: pressed.get() === 1 ? 0.97 : 1 }]);
  const t2 = useDerivedValue(() => [{ scale: pressed.get() === 2 ? 0.97 : 1 }]);
  const sweepOpacity = useDerivedValue(() =>
    glow.get().zip >= 0 && glow.get().zip <= PULSE.word ? 0.5 * Math.sin((Math.PI * glow.get().zip) / PULSE.word) : 0,
  );
  const sweepStart = useDerivedValue(() => vec(sweepX.get() - 40, 0));
  const sweepEnd = useDerivedValue(() => vec(sweepX.get() + 40, 0));
  const icon0 = useMemo(
    () => iconPath('mail', pill.x + pill.w / 2 - (textW(fonts.btn, COPY.continue) / 2 + 17), pill.y + pill.h / 2),
    [pill.x, pill.y, pill.w, pill.h, fonts.btn],
  );
  const w1 = textW(fonts.half, COPY.google);
  const w2 = textW(fonts.half, COPY.wallet);
  const icon1 = useMemo(() => iconPath('key', hx0 + hw / 2 - (w1 / 2 + 14), hy + hh / 2), [hx0, hw, hy, hh, w1]);
  const icon2 = useMemo(() => iconPath('phone', hx1 + hw / 2 - (w2 / 2 + 14), hy + hh / 2), [hx1, hw, hy, hh, w2]);
  const themeX = L.x(ref.theme.cx);
  const themeY = L.yt(ref.theme.cy);
  const glyph = useMemo(() => sunPath(themeX, themeY), [themeX, themeY]);
  // legal
  const l1 = [COPY.legalA, COPY.terms, COPY.and, COPY.privacy];
  const widths = l1.map((s) => textW(fonts.legal, s));
  const total = widths.reduce((a, b) => a + b, 0);
  let lx = W / 2 - total / 2;
  const legalY1 = L.yb(ref.legal.y1);
  const legalY2 = L.yb(ref.legal.y2);
  const pieces = l1.map((s, k) => {
    const x = lx;
    lx += widths[k];
    return { s, x, link: k === 1 || k === 3, w: widths[k] };
  });
  const wB = textW(fonts.legal, COPY.legalB);
  return (
    <Group>
      {/* theme toggle */}
      <Circle cx={themeX} cy={themeY} r={(ref.theme.d / 2) * L.S} color={C.themeBtn[i]} />
      {i === 0 ? (
        <Group>
          <Circle cx={themeX} cy={themeY} r={8.5} color={C.moon} />
          <Circle cx={themeX + 5} cy={themeY - 3.5} r={7.4} color={C.themeBtn[0]} />
        </Group>
      ) : (
        <Group>
          <Circle cx={themeX} cy={themeY} r={5.4} color={C.sun} />
          <Path path={glyph} style="stroke" strokeWidth={1.6} strokeCap="round" color={C.sun} />
        </Group>
      )}
      {/* wordmark, tagline */}
      {fonts.word ? <Text text={COPY.wordmark} font={fonts.word} x={W / 2 - wordW / 2} y={wordY} color={ink} /> : null}
      {fonts.word ? (
        <Group opacity={wordOpacity}>
          <Text text={COPY.wordmark} font={fonts.word} x={W / 2 - wordW / 2} y={wordY}>
            <LinearGradient start={vec(0, wordTop)} end={vec(0, wordTop + ref.type.wordmark.size * L.S * 0.9)} colors={wordColors} />
          </Text>
        </Group>
      ) : null}
      {fonts.word ? (
        <Group>
          <Group opacity={sweepOpacity}>
            <Text text={COPY.wordmark} font={fonts.word} x={W / 2 - wordW / 2} y={wordY}>
              <LinearGradient start={sweepStart} end={sweepEnd} colors={['rgba(255,255,255,0)', '#FFFFFF', 'rgba(255,255,255,0)']} />
            </Text>
          </Group>
        </Group>
      ) : null}
      {fonts.tag ? (
        <Text text={COPY.tagline} font={fonts.tag} x={W / 2 - textW(fonts.tag, COPY.tagline) / 2} y={tagY} color={C.mute[i]} />
      ) : null}
      {/* primary pill */}
      <Group origin={vec(pill.x + pill.w / 2, pill.y + pill.h / 2)} transform={t0}>
        <RoundedRect x={pill.x} y={pill.y} width={pill.w} height={pill.h} r={pill.r} color={C.pill[i]} />
        <Group clip={Skia.RRectXY(Skia.XYWHRect(pill.x, pill.y, pill.w, pill.h), pill.r, pill.r)}>
          <Rect x={pill.x} y={pill.y} width={pill.w} height={1.2} color={C.shellPill} opacity={pillEdge} />
        </Group>
        <Path path={icon0} style="stroke" strokeWidth={1.7} strokeCap="round" strokeJoin="round" color={C.pillInk[i]} />
        {fonts.btn ? (
          <Text
            text={COPY.continue}
            font={fonts.btn}
            x={pill.x + pill.w / 2 - textW(fonts.btn, COPY.continue) / 2 + 12}
            y={pill.y + pill.h / 2 + 5.6 * L.S}
            color={C.pillInk[i]}
          />
        ) : null}
      </Group>
      {/* two half buttons */}
      <Group origin={vec(hx0 + hw / 2, hy + hh / 2)} transform={t1}>
        <RoundedRect x={hx0} y={hy} width={hw} height={hh} r={ref.half.r * L.S} color={C.half[i]} />
        <Path path={icon1} style="stroke" strokeWidth={1.6} strokeCap="round" strokeJoin="round" color={C.halfInk[i]} />
        {fonts.half ? (
          <Text text={COPY.google} font={fonts.half} x={hx0 + hw / 2 - w1 / 2 + 12} y={hy + hh / 2 + 5 * L.S} color={C.halfInk[i]} />
        ) : null}
      </Group>
      <Group origin={vec(hx1 + hw / 2, hy + hh / 2)} transform={t2}>
        <RoundedRect x={hx1} y={hy} width={hw} height={hh} r={ref.half.r * L.S} color={C.half[i]} />
        <Path path={icon2} style="stroke" strokeWidth={1.6} strokeCap="round" strokeJoin="round" color={C.halfInk[i]} />
        {fonts.half ? (
          <Text text={COPY.wallet} font={fonts.half} x={hx1 + hw / 2 - w2 / 2 + 12} y={hy + hh / 2 + 5 * L.S} color={C.halfInk[i]} />
        ) : null}
      </Group>
      {/* legal */}
      {fonts.legal
        ? pieces.map((p) => (
            <Group key={p.s}>
              <Text text={p.s} font={fonts.legal!} x={p.x} y={legalY1} color={p.link ? C.mute[i] : C.legal[i]} />
              {p.link ? <Rect x={p.x} y={legalY1 + 2.2} width={p.w} height={0.9} color={C.legal[i]} /> : null}
            </Group>
          ))
        : null}
      {fonts.legal ? <Text text={COPY.legalB} font={fonts.legal} x={W / 2 - wB / 2} y={legalY2} color={C.legal[i]} /> : null}
    </Group>
  );
}

// ---- the pear's layer ----
function PearLayer({
  L,
  look,
  cy,
  lightMix,
  reduce,
  clock,
}: {
  L: Layout;
  look: Engine['look'];
  cy: number;
  lightMix: SharedValue<number>;
  reduce: boolean;
  clock: SharedValue<number>;
}) {
  const cx = L.W / 2;
  const S = L.S;
  const reach = wipeReach(L.W, L.H);
  const uniforms = useDerivedValue(() => {
    const k = look.get();
    const r = reduce ? 0 : wipeRadius(k.wipeP, reach);
    return {
      uC: [cx, cy],
      uAa: 1 / 3,
      uP3: P3,
      uYaw: k.yaw * D2R,
      uScale: k.scale * S,
      uKey: k.key,
      uPlane: planeY(k.c) * S,
      uRoll: k.roll,
      uPitchP: k.pitchP,
      uSeam: k.seam,
      uTime: (clock.get() * 8 * S) % (5 * S),
      uWipe: [WIPE.cx, L.H + 80, r],
      uLight: reduce ? lightMix.get() : 0,
      uShell: SHELL,
      uSeamC: SEAM,
      uSeamCore: SEAM_CORE,
      uLine: [5 * S, 1.2 * S],
    };
  });
  if (!pearFx) return null;
  return (
    <Rect x={cx - 112 * S} y={cy - 218 * S} width={224 * S} height={396 * S}>
      <Shader source={pearFx} uniforms={uniforms} />
    </Rect>
  );
}

// ---- the hero alone ----
export type DissolveObjectProps = {
  autoPlay?: boolean;
  light?: boolean;
  width?: number;
  onDissolveChange?: (amount: number) => void;
};
export function DissolveObject({ autoPlay = false, light = false, width }: DissolveObjectProps) {
  const win = useWindowDimensions();
  const W = width ?? win.width;
  const reduce = useReducedMotionPreference();
  const S = Math.min(W / 402, 1.12);
  const H = 396 * S;
  const cy = 218 * S;
  const eng = useEngine({ autoPlay, reduce, box: { x: 0, y: 0, w: W, h: H }, cy, S, defaultLight: light });
  // Worklets read these shared values, never `eng` itself: it also holds the Pan gesture, which a worklet cannot copy.
  const { clock: engClock, lightMix: engLightMix, look: engLook } = eng;
  const L = useMemo(() => ({ W, H, S }) as unknown as Layout, [W, H, S]);
  return (
    <GestureDetector gesture={eng.gesture}>
      <View style={{ width: W, height: H }} accessible={false}>
        <Canvas style={{ width: W, height: H }}>
          <PearLayer L={L} look={engLook} cy={cy} lightMix={engLightMix} reduce={reduce} clock={engClock} />
        </Canvas>
      </View>
    </GestureDetector>
  );
}

// ---- the screen ----
type Props = {
  autoPlay?: boolean;
  onContinue?: (id: string) => void;
  onThemeChange?: (theme: 'dark' | 'light') => void;
  defaultTheme?: 'dark' | 'light';
  providers?: Provider[];
};

export function ObjectSignIn({ autoPlay = false, onContinue, onThemeChange, defaultTheme = 'dark' }: Props) {
  const { width: W, height: H } = useWindowDimensions();
  const insets = useSafeAreaInsets();
  const reduce = useReducedMotionPreference();
  const L = useMemo(() => makeLayout(W, H, insets.top, insets.bottom), [W, H, insets.top, insets.bottom]);
  const S = L.S;
  const cy = L.yc(ref.pear.cy);
  const box = useMemo(() => ({ x: W / 2 - 120 * S, y: cy - 220 * S, w: 240 * S, h: 400 * S }), [W, S, cy]);
  const [isLight, setIsLight] = useState(defaultTheme === 'light');
  const lightRef = useRef(isLight);
  const onTheme = useMemo(
    () => (light: boolean) => {
      lightRef.current = light;
      setIsLight(light);
      onThemeChange?.(light ? 'light' : 'dark');
    },
    [onThemeChange],
  );
  const eng = useEngine({ autoPlay, reduce, box, cy, S, onTheme, defaultLight: defaultTheme === 'light' });
  // Worklets read these shared values, never `eng` itself: it also holds the Pan gesture, which a worklet cannot copy.
  const { clock: engClock, glow: engGlow, lightMix: engLightMix, look: engLook } = eng;
  const pressed = useSharedValue(-1);

  const word = useFont(require('./assets/InstrumentSerif-Italic.ttf'), ref.type.wordmark.size * S);
  const tag = useFont(require('./assets/Inter-Regular.ttf'), ref.type.tagline.size * S);
  const btn = useFont(require('./assets/Inter-SemiBold.ttf'), ref.type.button.size * S);
  const half = useFont(require('./assets/Inter-Medium.ttf'), ref.type.half.size * S);
  const legal = useFont(require('./assets/Inter-Regular.ttf'), ref.type.legal.size * S);
  const fonts: Fonts = { word, tag, btn, half, legal };

  // the light page is a disc that grows from the bottom-left corner. The disc's edge is the edge of a drawn circle used as a mask
  // (analytic coverage, smooth at 3x); a path clip a few points wider only skips the work while the disc is closed.
  const reach = useMemo(() => wipeReach(W, H), [W, H]);
  const wipeR = useDerivedValue(() => {
    const k = engLook.get();
    return reduce ? (k.wipeP > 0.5 || engLightMix.get() > 0.01 ? reach : 0) : wipeRadius(k.wipeP, reach);
  });
  const clip = useDerivedValue(() => {
    const r = wipeR.get();
    const p = Skia.PathBuilder.Make();
    if (r > 0.5) p.addCircle(WIPE.cx, H + 80, r + 3);
    return p.build();
  });
  const lightOpacity = useDerivedValue(() => (reduce ? engLightMix.get() : 1));

  // the status bar's ink follows the page: it flips just before the disc reaches the bar (iOS cross-fades it over ~0.3 s), and
  // flips back just before the disc leaves it. `bar`: 1 = dark ink on the light page.
  const dBar = Math.hypot(W / 2 + 80, H + 80 - insets.top * 0.6);
  const lead = reduce ? 0 : 260;
  const bar = useSharedValue(defaultTheme === 'light' ? 1 : 0);
  const [barDark, setBarDark] = useState(defaultTheme === 'light');
  useAnimatedReaction(
    () => (reduce ? engLightMix.get() * reach : wipeR.get()),
    (r, prev) => {
      if (prev === null) return;
      let next = bar.get();
      if (r > prev && r > dBar - lead) next = 1;
      else if (r < prev && r < dBar + lead) next = 0;
      if (reduce) next = r > reach * 0.5 ? 1 : 0;
      if (next !== bar.get()) {
        bar.set(next);
        scheduleOnRN(setBarDark, next === 1);
      }
    },
  );

  const pill = { x: L.x(ref.pill.x), y: L.yb(ref.pill.y), w: ref.pill.w * L.S, h: ref.pill.h * L.S };
  const hw = ref.half.w * L.S;
  const hy = L.yb(ref.half.y);
  const hh = ref.half.h * L.S;
  const hx0 = L.x(ref.pill.x);
  const hx1 = pill.x + pill.w - hw;
  const press = (idx: number, id: string) => ({
    onPressIn: () => pressed.set(idx),
    onPressOut: () => pressed.set(-1),
    onPress: () => {
      haptic.press();
      onContinue?.(id);
    },
  });
  const onA11y = (ev: AccessibilityActionEvent) => {
    if (ev.nativeEvent.actionName === 'increment') eng.a11yBump(1);
    if (ev.nativeEvent.actionName === 'decrement') eng.a11yBump(-1);
  };

  return (
    <View style={[styles.screen, { backgroundColor: isLight ? C.ground[1] : C.ground[0] }]}>
      <StatusBar style={barDark ? 'dark' : 'light'} animated />
      <GestureDetector gesture={eng.gesture}>
        <View style={StyleSheet.absoluteFill}>
          <Canvas style={StyleSheet.absoluteFill} accessible={false}>
            <PageBack i={0} L={L} glow={engGlow} cy={cy} />
            <PageFront i={0} L={L} glow={engGlow} fonts={fonts} pressed={pressed} />
            {/* the whole light page (ground and furniture) as one layer, cut to the disc by a circle drawn into a dstIn layer: nothing of
                the dark page can peek out at its edge, and the edge is the circle's own analytic coverage */}
            <Group clip={clip} opacity={lightOpacity}>
              <Group layer>
                <PageBack i={1} L={L} glow={engGlow} cy={cy} />
                <PageFront i={1} L={L} glow={engGlow} fonts={fonts} pressed={pressed} />
                <Group layer={<Paint blendMode="dstIn" />}>
                  <Circle cx={WIPE.cx} cy={H + 80} r={wipeR} />
                </Group>
              </Group>
            </Group>
            <PearLayer L={L} look={engLook} cy={cy} lightMix={engLightMix} reduce={reduce} clock={engClock} />
          </Canvas>
        </View>
      </GestureDetector>
      {/* the controls: real views over the drawing, so they are focusable and labelled */}
      <View
        accessible
        accessibilityRole="adjustable"
        accessibilityLabel="Pear"
        accessibilityHint="Swipe up or down to turn it"
        accessibilityActions={[{ name: 'increment' }, { name: 'decrement' }]}
        onAccessibilityAction={onA11y}
        style={[styles.abs, { left: box.x, top: box.y, width: box.w, height: box.h }]}
        pointerEvents="none"
        importantForAccessibility="no-hide-descendants"
      />
      <Pressable
        accessibilityRole="switch"
        accessibilityLabel={isLight ? COPY.toDark : COPY.toLight}
        accessibilityState={{ checked: isLight }}
        hitSlop={4}
        onPress={() => {
          haptic.toggle();
          eng.setTheme(!lightRef.current);
        }}
        style={[styles.abs, { left: L.x(ref.theme.cx) - 22, top: L.yt(ref.theme.cy) - 22, width: 44, height: 44 }]}
      />
      <Pressable
        accessibilityRole="button"
        accessibilityLabel={COPY.continue}
        {...press(0, 'email')}
        style={[styles.abs, { left: pill.x, top: pill.y, width: pill.w, height: pill.h }]}
      />
      <Pressable
        accessibilityRole="button"
        accessibilityLabel={COPY.google}
        {...press(1, 'passkey')}
        style={[styles.abs, { left: hx0, top: hy, width: hw, height: hh }]}
      />
      <Pressable
        accessibilityRole="button"
        accessibilityLabel={COPY.wallet}
        {...press(2, 'phone')}
        style={[styles.abs, { left: hx1, top: hy, width: hw, height: hh }]}
      />
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1 },
  abs: { position: 'absolute' },
});

void mix;
void Circle;
