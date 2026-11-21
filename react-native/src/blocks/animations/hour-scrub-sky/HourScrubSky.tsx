import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useEffect, useMemo, useState } from 'react';
import { type LayoutChangeEvent, StyleSheet, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import { cancelAnimation, Easing, useDerivedValue, useFrameCallback, useSharedValue, withSpring, withTiming } from 'react-native-reanimated';
import { BackdropBlur, Canvas, createPicture, Fill, Group, ImageShader, Picture, Rect, Shader, Skia, useFonts, useImage } from '@shopify/react-native-skia';
import { scheduleOnRN } from 'react-native-worklets';

import { useReducedMotionPreference } from '../../../shared/useReducedMotionPreference';
import { drawScene, hex, makeTexts, type Scene } from './hs-draw';
import {
  cloudDay,
  type Frame,
  frameAt,
  INK_HI,
  INK_I,
  INK_LO,
  INK_Y,
  LOOP,
  lightness,
  MAROON,
  mix,
  pillIndex,
  SALMON,
  skyAtP,
  skyParams,
  smooth,
  sunAt,
  sunColour,
  timeForHour,
  yInkAt,
} from './hs-motion';
import { SKY_FX } from './hs-shaders';
import { CLOUD_NIGHT, FAMILY, L, PT_PER_H, SPRING } from './hs-theme';

/** Skia runtime effects are read as Display P3 on iOS: re-encode the sRGB colours there. */
const P3 = 1;
const RIM_N = hex(CLOUD_NIGHT[0]);
const BELLY_N = hex(CLOUD_NIGHT[1]);
const DEG = Math.PI / 180;

type Props = {
  /** Plays the 12 s day until a finger takes over (default true). */
  autoPlay?: boolean;
  /** Called (on the JS thread) with the hour, 0..24 from noon, whenever the hour pill changes. */
  onHourChange?: (h: number) => void;
};

/**
 * The "Nearby" screen of an air-and-weather app where the whole screen is the sky: scrub the tick row (or hold the
 * sun and drag it along its arc) through 24 hours and real light moves through it. Dusk comes down from the zenith
 * as a terminator that crosses the type, the stars wheel about the pole in trails as long as the scrub is fast, and
 * dawn climbs back up from the horizon.
 */
export function HourScrubSky({ autoPlay = true, onHourChange }: Props) {
  const reduced = useReducedMotionPreference();
  const [size, setSize] = useState<{ w: number; h: number } | null>(null);
  const fonts = useFonts({
    [FAMILY]: [
      require('./assets/fonts/Figtree-Light.ttf'),
      require('./assets/fonts/Figtree-Regular.ttf'),
      require('./assets/fonts/Figtree-Medium.ttf'),
      require('./assets/fonts/Figtree-SemiBold.ttf'),
    ],
  });
  const lowImg = useImage(require('./assets/hs-cloud-low-rg.png'));
  const highImg = useImage(require('./assets/hs-cloud-high-rg.png'));
  const texts = useMemo(() => (fonts ? makeTexts(fonts) : null), [fonts]);
  const [barLight, setBarLight] = useState(false);

  const W = size?.w ?? 402;
  const H = size?.h ?? 874;
  const S = Math.min(W / 402, H / (L.tab.y + L.tab.h + 12));
  const CH = H / S;
  const offsetX = (W - 402 * S) / 2;

  // ---- clocks ----
  const T = useSharedValue(0);
  const auto = useSharedValue(autoPlay && !reduced ? 1 : 0);
  const pinned = useSharedValue(0);
  const clock = useSharedValue(1);
  const now = useSharedValue(0);
  // the play disc's own run of the timeline: 0 off, 1 playing, 2 held at the end (reset arrow), 3 resetting
  const tl = useSharedValue(0);

  // ---- live state (a finger, or the idle return) ----
  const hL = useSharedValue(0);
  const hTarget = useSharedValue(0);
  const hpL = useSharedValue(0);
  const trail = useSharedValue(0); // dh/dt for trails and smear
  const hPrev = useSharedValue(0);
  const csL = useSharedValue(0);
  const rsL = useSharedValue(0);
  const pillL = useSharedValue(0);
  const pillDarkL = useSharedValue(0);
  const discL = useSharedValue(1);
  const gPlayL = useSharedValue(1);
  const gPauseL = useSharedValue(0);
  const hq = useSharedValue(0); // reduced motion: the half-hour step being shown
  const drag = useSharedValue(0); // 0 none, 1 tick row, 2 the sun, 3 sun handed over to horizontal scrubbing
  const h0 = useSharedValue(0);
  const x0 = useSharedValue(0);
  const vel = useSharedValue(0);
  const decaying = useSharedValue(0);
  const homing = useSharedValue(0); // 0 none, 1 easing the sky home, 2 restoring the sentence
  const homeAt = useSharedValue(0);
  const touching = useSharedValue(0);
  const lastTouch = useSharedValue(-10); // -1: stamp on the next frame
  const hold = useSharedValue(0);
  const finger = useSharedValue<[number, number]>([0, 0]);
  const unit = useSharedValue(0);
  const pressChip = useSharedValue(1);
  const pressArrow = useSharedValue(1);
  const pressTab = useSharedValue(1);
  // inks of the small elements: 0 dark, 1 light; targets carry the hysteresis
  const ink = useSharedValue<number[]>([0, 0, 0, 0, 0, 0, 0, 0]);
  const inkT = useSharedValue<number[]>([0, 0, 0, 0, 0, 0, 0, 0]);
  // haptic and label bookkeeping
  const lastHalf = useSharedValue(0);
  const lastHalfAt = useSharedValue(0);
  const lastSunUp = useSharedValue(1);
  const lastInkSide = useSharedValue(0);
  const lastPill = useSharedValue(-1);
  const bound = useSharedValue(0);

  useEffect(() => {
    auto.set(autoPlay && !reduced ? 1 : 0);
  }, [autoPlay, reduced, auto]);

  const buzz = (k: 'soft' | 'light' | 'medium' | 'rigid' | 'sel') => {
    if (k === 'sel') Haptics.selectionAsync().catch(() => {});
    else {
      const style = k === 'soft' ? Haptics.ImpactFeedbackStyle.Soft : k === 'light' ? Haptics.ImpactFeedbackStyle.Light : k === 'medium' ? Haptics.ImpactFeedbackStyle.Medium : Haptics.ImpactFeedbackStyle.Rigid;
      Haptics.impactAsync(style).catch(() => {});
    }
  };
  const hourChanged = (i: number) => onHourChange?.(i);

  /** The frame being drawn: the timeline while it runs, else the live state. */
  const frameNow = (): Frame => {
    'worklet';
    if (auto.get() === 1 || tl.get() > 0) return frameAt(T.get());
    const h = reduced ? hq.get() : hL.get();
    return {
      h,
      rate: reduced ? 0 : trail.get(),
      hp: reduced ? hq.get() : hpL.get(),
      cs: csL.get(),
      rs: rsL.get(),
      pill: pillL.get(),
      pillDark: pillDarkL.get(),
      disc: discL.get(),
      gPlay: gPlayL.get(),
      gPause: gPauseL.get(),
      gReset: 0,
      resetRot: 0,
      flare: 0,
      sway: reduced ? 0 : now.get(),
    };
  };

  useEffect(() => {
    if (!__DEV__) return;
    const g = globalThis as { __hss?: unknown };
    g.__hss = {
      at: (t: number) => {
        pinned.set(1);
        tl.set(0);
        auto.set(1);
        T.set(t);
      },
      play: () => {
        pinned.set(0);
        auto.set(1);
      },
      rate: (r: number) => {
        clock.set(r);
      },
      /** Scrub to hour h (0..24) as a finger would, without touch injection. */
      scrub: (h: number, rate = 0) => {
        pinned.set(0);
        auto.set(0);
        tl.set(0);
        hL.set(h);
        hpL.set(h);
        hTarget.set(h);
        trail.set(rate);
        csL.set(0.5);
        pillL.set(1);
        pillDarkL.set(1);
        gPlayL.set(1);
        gPauseL.set(0);
        touching.set(1);
      },
      release: () => {
        touching.set(0);
        lastTouch.set(-1);
      },
      /** Release a scrub at hour h with velocity v (h/s): exercises the decay, the trails and the detent. */
      fling: (h: number, v: number) => {
        pinned.set(0);
        auto.set(0);
        tl.set(0);
        hL.set(h);
        hpL.set(h);
        hPrev.set(h);
        csL.set(0.5);
        pillL.set(1);
        pillDarkL.set(1);
        touching.set(0);
        lastTouch.set(-1);
        vel.set(v);
        decaying.set(1);
      },
      state: () => ({ T: T.get(), auto: auto.get(), tl: tl.get(), h: hL.get(), hp: hpL.get(), ink: ink.get(), homing: homing.get() }),
    };
    return () => {
      delete g.__hss;
    };
  }, [T, auto, pinned, clock, tl, hL, hpL, hTarget, hPrev, trail, csL, pillL, pillDarkL, gPlayL, gPauseL, touching, lastTouch, now, ink, homing, vel, decaying]);

  /** Move the inks toward the light under each element (hysteresis 0.42 / 0.50, 120 ms). */
  const updateInks = (f: Frame, dt: number, snap: boolean) => {
    'worklet';
    const sp = skyParams(f.h);
    const tgt = inkT.get().slice();
    const cur = ink.get().slice();
    // reduced motion: everything flips together, on the light in the middle of the screen
    const lRef = reduced ? lightness(skyAtP(450, sp, CH)) : 0;
    for (let i = 0; i < INK_Y.length; i++) {
      const l = reduced ? lRef : lightness(skyAtP(INK_Y[i], sp, CH));
      if (snap) tgt[i] = l < 0.46 ? 1 : 0;
      else if (l < INK_LO) tgt[i] = 1;
      else if (l > INK_HI) tgt[i] = 0;
      const step = snap ? 1 : dt / 0.12;
      cur[i] = cur[i] < tgt[i] ? Math.min(cur[i] + step, tgt[i]) : Math.max(cur[i] - step, tgt[i]);
    }
    if (tgt[INK_I.status] !== inkT.get()[INK_I.status]) scheduleOnRN(setBarLight, tgt[INK_I.status] === 1);
    inkT.set(tgt);
    ink.set(cur);
  };

  /** Back to the rest frame, then the loop (2 s after the last touch). */
  const goRest = () => {
    'worklet';
    tl.set(0);
    homing.set(0);
    hL.set(0);
    hpL.set(0);
    hTarget.set(0);
    hq.set(0);
    trail.set(0);
    csL.set(0);
    rsL.set(0);
    pillL.set(0);
    pillDarkL.set(0);
    discL.set(1);
    gPlayL.set(1);
    gPauseL.set(0);
    lastTouch.set(now.get());
  };

  useFrameCallback((info) => {
    const raw = (info.timeSincePreviousFrame ?? 16) / 1000;
    const dt = Math.min(raw, clock.get() < 1 ? 1 : 0.05) * clock.get();
    now.set(now.get() + dt);
    // the dev hook cannot read the UI clock: it asks for the stamp to be taken here
    if (lastTouch.get() === -1) lastTouch.set(now.get());
    if (pinned.get() === 1) {
      updateInks(frameAt(T.get()), dt, true);
      return;
    }
    if (auto.get() === 1) {
      T.set((T.get() + dt) % LOOP);
    } else if (tl.get() === 1) {
      const t1 = T.get() + dt;
      if (t1 >= 10.6) {
        T.set(10.6);
        tl.set(2);
      } else T.set(t1);
      if (Math.floor(T.get() * 2) !== Math.floor((T.get() - dt) * 2)) scheduleOnRN(buzz, 'sel');
    } else if (tl.get() === 3) {
      const t1 = T.get() + dt;
      if (t1 >= 11.8) goRest();
      else T.set(t1);
    } else if (tl.get() === 0) {
      // ---- live physics ----
      if (drag.get() === 2) {
        // the held sun follows the finger on a 30 ms spring
        hL.set(hL.get() + (hTarget.get() - hL.get()) * (1 - Math.exp(-dt / 0.03)));
        hpL.set(hL.get());
      }
      if (decaying.get() === 1) {
        const v = vel.get() * Math.pow(0.995, dt * 1000);
        vel.set(v);
        let h = hL.get() + v * dt;
        if (h < 0 || h > 24) {
          h = h < 0 ? 0 : 24;
          decaying.set(0);
          scheduleOnRN(buzz, 'rigid');
        } else if (Math.abs(v) < 0.4) {
          decaying.set(0);
          const tgt = Math.round(h * 2) / 2;
          hL.set(withSpring(tgt, { damping: SPRING.detent.d, stiffness: SPRING.detent.k, velocity: v }));
          hpL.set(withSpring(tgt, { damping: SPRING.detent.d, stiffness: SPRING.detent.k, velocity: v }));
          trail.set(withTiming(0, { duration: 400, easing: Easing.out(Easing.cubic) }));
        }
        if (decaying.get() === 1 || h === 0 || h === 24) {
          hL.set(h);
          hpL.set(h);
        }
      }
      // trails follow the measured speed while a finger or the decay drives h; the shutter closes over 400 ms after
      if ((touching.get() === 1 || decaying.get() === 1 || homing.get() === 1) && dt > 0) {
        const r = (hL.get() - hPrev.get()) / dt;
        trail.set(trail.get() + (Math.max(Math.min(r, 8), -8) - trail.get()) * 0.3);
      } else if (touching.get() === 0 && decaying.get() === 0 && homing.get() === 0 && Math.abs(trail.get()) > 0 && trail.get() !== 0) {
        // left alone (e.g. after the detent spring): let the trail contract
        trail.set(trail.get() * Math.exp(-dt / 0.12));
        if (Math.abs(trail.get()) < 0.01) trail.set(0);
      }
      hPrev.set(hL.get());
      if (reduced) {
        const q = Math.round(hL.get() * 2) / 2;
        if (Math.abs(q - hq.get()) > 0.01 && touching.get() === 1) hq.set(withTiming(q, { duration: 200 }));
      }
      // ---- the idle return: ease the sky home, fold the pill into its tick, bring the sentence back ----
      const idle = touching.get() === 0 && decaying.get() === 0 && now.get() - lastTouch.get() > 2;
      if (idle && autoPlay && !reduced) {
        if (homing.get() === 0) {
          const atRest = csL.get() === 0 && pillL.get() < 0.001 && (hL.get() < 0.001 || hL.get() > 23.999);
          if (atRest) {
            goRest();
            T.set(0);
            auto.set(1);
          } else {
            homing.set(1);
            const tgt = hL.get() > 12 ? 24 : 0;
            const ms = 500 + 90 * Math.abs(tgt - hL.get());
            hL.set(withTiming(tgt, { duration: ms, easing: Easing.inOut(Easing.cubic) }));
            hpL.set(withTiming(tgt, { duration: ms, easing: Easing.inOut(Easing.cubic) }));
          }
        } else if (homing.get() === 1 && (hL.get() < 0.001 || hL.get() > 23.999)) {
          homing.set(2);
          homeAt.set(now.get());
          trail.set(withTiming(0, { duration: 400 }));
          if (csL.get() > 0) rsL.set(withTiming(0.8, { duration: 800, easing: Easing.linear }));
          pillL.set(withSpring(0, { damping: SPRING.glide.d, stiffness: SPRING.glide.k }));
          pillDarkL.set(withTiming(0, { duration: 160 }));
          hpL.set(hpL.get());
          hpL.set(withSpring(0, { damping: SPRING.glide.d, stiffness: SPRING.glide.k }));
        } else if (homing.get() === 2 && now.get() - homeAt.get() > 1.0) {
          goRest();
          T.set(0);
          auto.set(1);
        }
      }
    }

    const f = frameNow();
    updateInks(f, dt, false);

    // the hour pill's label (for onHourChange)
    const pi = pillIndex(f.hp);
    if (pi !== lastPill.get()) {
      lastPill.set(pi);
      if (onHourChange) scheduleOnRN(hourChanged, pi);
    }
    // haptics while a finger drives the hour
    if (auto.get() === 0 && tl.get() === 0 && (touching.get() === 1 || decaying.get() === 1)) {
      const half = Math.floor(f.h * 2);
      if (half !== lastHalf.get()) {
        const fast = Math.abs(f.rate) > 5;
        if (!fast || half % 2 === 0 || now.get() - lastHalfAt.get() > 0.1) {
          scheduleOnRN(buzz, 'sel');
          lastHalfAt.set(now.get());
        }
        lastHalf.set(half);
      }
      const up = sunAt(f.h).y < L.hazeY ? 1 : 0;
      if (up !== lastSunUp.get()) {
        lastSunUp.set(up);
        scheduleOnRN(buzz, 'medium');
      }
      const side = yInkAt(skyParams(f.h), CH) > 590 ? 1 : 0;
      if (side !== lastInkSide.get()) {
        lastInkSide.set(side);
        scheduleOnRN(buzz, 'sel');
      }
    } else {
      lastHalf.set(Math.floor(f.h * 2));
      lastSunUp.set(sunAt(f.h).y < L.hazeY ? 1 : 0);
      lastInkSide.set(yInkAt(skyParams(f.h), CH) > 590 ? 1 : 0);
    }
  });

  // ---- the frame being drawn ----
  const F = useDerivedValue<Frame>(() => {
    // read the clocks so the frame recomputes every tick
    now.get();
    return frameNow();
  });

  const lowW = lowImg?.width() ?? 1;
  const lowH = lowImg?.height() ?? 1;
  const highW = highImg?.width() ?? 1;
  const highH = highImg?.height() ?? 1;
  const skyU = useDerivedValue(() => {
    const f = F.get();
    const sp = skyParams(f.h);
    const sun = sunAt(f.h);
    const held = hold.get();
    const cl = cloudDay(f.h);
    const sLow = lowH / CH; // the low plate covers the screen's height
    const sHigh = highH / 600; // the high plate is finer and covers the upper 600 pt
    const lowWpt = lowW / sLow;
    const highWpt = highW / sHigh;
    const sway = reduced ? 0 : f.sway;
    const ar = Math.abs(f.rate);
    // exposure 0.36 s, with a dead zone so a slow dusk shows points, not dashes
    const arc = Math.min(15 * DEG * Math.max(ar - 0.9, 0) * 0.36, 40 * DEG);
    return {
      uP3: P3,
      uCH: CH,
      uD0: sp.day[0],
      uD1: sp.day[1],
      uD2: sp.day[2],
      uD3: sp.day[3],
      uN0: sp.night[0],
      uN1: sp.night[1],
      uN2: sp.night[2],
      uN3: sp.night[3],
      uYT: sp.yT,
      uFW: sp.w,
      uBandCol: sp.band.dawn > 0.5 ? SALMON : MAROON,
      uBand: [sp.band.k, sp.band.dy, sp.band.s],
      uSun: [sun.x, sun.y],
      uSunCol: sunColour(f.h),
      uSquash: mix(1, 0.95, smooth(440, 540, sun.y)),
      uBloomK: 1 + 0.25 * held,
      uBreath: reduced ? 0 : 0.08 * Math.sin(Math.PI * sway),
      uFlare: f.flare,
      uRimD: cl[0],
      uBellyD: cl[1],
      uRimN: RIM_N,
      uBellyN: BELLY_N,
      uOff: [(f.h / 24) * lowWpt + (reduced ? 0 : 10 * Math.sin((2 * Math.PI * sway) / LOOP)), (f.h / 24) * highWpt + (reduced ? 0 : 6 * Math.sin((2 * Math.PI * sway) / LOOP + 1.3))],
      uScale: [sLow, sHigh],
      uPlateW: [lowW, highW],
      uSmear: reduced || ar < 1.5 ? 0 : Math.min(ar * 2.4, 10),
      uStarRot: (f.h * 15 * DEG) % (2 * Math.PI),
      uArc: reduced ? 0 : Math.sign(f.rate) * arc,
      uTrailDim: 1 / (1 + arc / (20 * DEG)) + 0.15,
      uHold: [finger.get()[0], finger.get()[1], held],
    };
  });

  const scene = useDerivedValue<Scene>(() => {
    const f = F.get();
    const sp = skyParams(f.h);
    return {
      f,
      ch: CH,
      yInk: reduced ? (ink.get()[INK_I.controls] > 0.5 ? CH + 200 : -200) : yInkAt(sp, CH),
      ink: ink.get(),
      sky84: skyAtP(84, sp, CH),
      sunCol: sunColour(f.h),
      unit: unit.get(),
      press: [pressChip.get(), pressArrow.get(), pressTab.get()],
      reduced,
    };
  });
  const bounds = useMemo(() => Skia.XYWHRect(0, 0, 402, CH), [CH]);
  const underPic = useDerivedValue(() => createPicture((c) => drawScene(c, scene.get(), texts, true), bounds));
  const topPic = useDerivedValue(() => createPicture((c) => drawScene(c, scene.get(), texts, false), bounds));
  const tabFill = useDerivedValue(() => {
    const k = ink.get()[INK_I.tab];
    return `rgba(${Math.round(mix(255, 26, k))},${Math.round(mix(255, 29, k))},${Math.round(mix(255, 38, k))},${mix(0.72, 0.7, k)})`;
  });
  const tabRR = useDerivedValue(() => {
    const sc = pressTab.get();
    const w = L.tab.w * sc;
    const h = L.tab.h * sc;
    return Skia.RRectXY(Skia.XYWHRect(L.tab.x + (L.tab.w - w) / 2, L.tab.y + (L.tab.h - h) / 2, w, h), 33 * sc, 33 * sc);
  });

  // ---- touch ----
  const toCanvas = (x: number, y: number) => {
    'worklet';
    return { x: (x - offsetX) / S, y: y / S };
  };
  const takeOver = () => {
    'worklet';
    cancelAnimation(hL);
    cancelAnimation(hpL);
    cancelAnimation(rsL);
    cancelAnimation(pillL);
    cancelAnimation(pillDarkL);
    if (auto.get() === 1 || tl.get() > 0) {
      const f = frameAt(T.get());
      hL.set(f.h);
      hpL.set(f.hp);
      hTarget.set(hL.get());
      hPrev.set(hL.get());
      trail.set(f.rate);
      const restored = f.rs >= 0.8 || f.cs === 0;
      csL.set(restored ? 0 : f.cs);
      rsL.set(restored ? 0 : f.rs);
      pillL.set(Math.max(f.pill, 0));
      pillDarkL.set(f.pillDark);
      discL.set(f.disc);
      discL.set(withSpring(1, { damping: SPRING.pop.d, stiffness: SPRING.pop.k }));
      gPlayL.set(f.gPlay);
      gPauseL.set(f.gPause);
      gPlayL.set(withSpring(1, { damping: SPRING.pop.d, stiffness: SPRING.pop.k }));
      gPauseL.set(withTiming(0, { duration: 100 }));
      auto.set(0);
      tl.set(0);
    }
    pinned.set(0);
    homing.set(0);
    decaying.set(0);
    touching.set(1);
    lastTouch.set(now.get());
  };
  const release = () => {
    'worklet';
    touching.set(0);
    lastTouch.set(now.get());
  };
  /** First scrub: fold the sentence and grow the pill out of its tick. */
  const engage = () => {
    'worklet';
    if (csL.get() === 0 || rsL.get() > 0) {
      rsL.set(0);
      csL.set(0);
      csL.set(withTiming(0.5, { duration: 500, easing: Easing.linear }));
    }
    if (pillL.get() !== 1) {
      pillL.set(withSpring(1, { damping: SPRING.pop.d, stiffness: SPRING.pop.k }));
    }
    if (pillDarkL.get() !== 1) {
      pillDarkL.set(withTiming(1, { duration: 160 }));
    }
  };
  const rubber = (h: number) => {
    'worklet';
    if (h > 24) {
      const over = (h - 24) * PT_PER_H;
      return 24 + over / (1 + over / 120) / PT_PER_H;
    }
    if (h < 0) {
      const over = -h * PT_PER_H;
      return -(over / (1 + over / 120)) / PT_PER_H;
    }
    return h;
  };

  const pan = Gesture.Pan()
    .minDistance(2)
    .onStart((e) => {
      const q = toCanvas(e.x - e.translationX, e.y - e.translationY);
      const sun = sunAt(frameNow().h);
      const dSun = Math.hypot(q.x - sun.x, q.y - sun.y);
      if (dSun < 44 && sun.y < 520) {
        takeOver();
        drag.set(2);
        hTarget.set(hL.get());
        finger.set([q.x, q.y]);
        hold.set(withSpring(1, { damping: SPRING.pop.d, stiffness: SPRING.pop.k }));
        scheduleOnRN(buzz, 'soft');
      } else if (q.y > 700 && q.y < 775 && q.x > 60 && q.x < 390) {
        takeOver();
        drag.set(1);
        h0.set(hpL.get());
        x0.set(q.x);
        hL.set(hpL.get());
        scheduleOnRN(buzz, 'light');
      } else return;
      engage();
    })
    .onUpdate((e) => {
      const q = toCanvas(e.x, e.y);
      const d = drag.get();
      if (d === 1 || d === 3) {
        const h = rubber(h0.get() + (q.x - x0.get()) / PT_PER_H);
        hL.set(h);
        hpL.set(h);
        const out = h < 0 || h > 24 ? 1 : 0;
        if (out === 1 && bound.get() === 0) scheduleOnRN(buzz, 'rigid');
        bound.set(out);
      } else if (d === 2) {
        finger.set([q.x, q.y]);
        // project the finger onto the arc: the nearest hour within ±1.5 h of the current one (never jumps)
        const hc = hTarget.get();
        let best = hc;
        let bd = 1e9;
        for (let i = -30; i <= 30; i++) {
          const h = Math.min(Math.max(hc + i * 0.05, 0), 24);
          const s = sunAt(h);
          const dd = (s.x - q.x) * (s.x - q.x) + (s.y - q.y) * (s.y - q.y);
          if (dd < bd) {
            bd = dd;
            best = h;
          }
        }
        hTarget.set(best);
        // under the haze the sun hands over to horizontal scrubbing, so the night is reachable without lifting
        if (q.y > L.hazeY || sunAt(best).y > 520) {
          drag.set(3);
          hL.set(best);
          hpL.set(best);
          h0.set(best);
          x0.set(q.x);
          hold.set(withSpring(0, { damping: SPRING.glide.d, stiffness: SPRING.glide.k }));
        }
      }
    })
    .onEnd((e, ok) => {
      const d = drag.get();
      if (d === 0) return;
      const v = !ok ? 0 : d === 2 ? trail.get() : e.velocityX / S / PT_PER_H;
      const h = hL.get();
      if (h < 0 || h > 24) {
        const tgt = h < 0 ? 0 : 24;
        hL.set(withSpring(tgt, { damping: SPRING.detent.d, stiffness: SPRING.detent.k }));
        hpL.set(withSpring(tgt, { damping: SPRING.detent.d, stiffness: SPRING.detent.k }));
      } else if (Math.abs(v) > 0.4 && !reduced) {
        vel.set(Math.max(Math.min(v, 8), -8));
        decaying.set(1);
      } else {
        const tgt = Math.round(h * 2) / 2;
        hL.set(withSpring(tgt, { damping: SPRING.detent.d, stiffness: SPRING.detent.k }));
        hpL.set(withSpring(tgt, { damping: SPRING.detent.d, stiffness: SPRING.detent.k }));
      }
    })
    .onFinalize(() => {
      if (drag.get() === 0) return;
      drag.set(0);
      bound.set(0);
      hold.set(withSpring(0, { damping: SPRING.glide.d, stiffness: SPRING.glide.k }));
      release();
    });

  const hit = (x: number, y: number, cx: number, cy: number, w: number, h: number) => {
    'worklet';
    return Math.abs(x - cx) <= w / 2 && Math.abs(y - cy) <= h / 2;
  };
  const pressOf = (q: { x: number; y: number }) => {
    'worklet';
    if (hit(q.x, q.y, L.chip.x + L.chip.w / 2, L.chip.y + L.chip.h / 2, L.chip.w + 8, L.chip.h + 8)) return pressChip;
    if (hit(q.x, q.y, L.arrow.x + L.arrow.w / 2, L.arrow.y + L.arrow.h / 2, L.arrow.w + 8, L.arrow.h + 8)) return pressArrow;
    if (hit(q.x, q.y, L.tab.x + L.tab.w / 2, L.tab.y + L.tab.h / 2, L.tab.w, L.tab.h)) return pressTab;
    return null;
  };
  const tap = Gesture.Tap()
    .maxDistance(10)
    .maxDuration(8000)
    .onBegin((e) => {
      const q = toCanvas(e.x, e.y);
      const p = pressOf(q);
      if (p) p.set(withTiming(0.97, { duration: 90 }));
    })
    .onEnd((e, ok) => {
      if (!ok) return;
      const q = toCanvas(e.x, e.y);
      if (hit(q.x, q.y, L.disc.cx, L.disc.cy, 52, 52)) {
        const mode = tl.get();
        if (reduced) {
          takeOver();
          goRest();
          release();
        } else if (mode === 1) {
          // pause: the live state keeps what the timeline shows
          takeOver();
          release();
        } else if (mode === 2) {
          tl.set(3);
        } else {
          takeOver();
          release();
          const h = hL.get() >= 23.95 ? 0 : hL.get();
          T.set(h <= 0.001 && csL.get() === 0 ? 0.2 : Math.max(timeForHour(h), 0.62));
          tl.set(1);
        }
        scheduleOnRN(buzz, 'sel');
      } else if (q.x > 10 && q.x < 220 && q.y > 535 && q.y < 640) {
        takeOver();
        release();
        unit.set(withSpring(unit.get() > 0.5 ? 0 : 1, { damping: SPRING.snap.d, stiffness: SPRING.snap.k }));
        scheduleOnRN(buzz, 'sel');
      } else if (pressOf(q)) {
        takeOver();
        release();
        scheduleOnRN(buzz, 'light');
      } else {
        takeOver();
        release();
      }
    })
    .onFinalize(() => {
      pressChip.set(withSpring(1, { damping: SPRING.snap.d, stiffness: SPRING.snap.k }));
      pressArrow.set(withSpring(1, { damping: SPRING.snap.d, stiffness: SPRING.snap.k }));
      pressTab.set(withSpring(1, { damping: SPRING.snap.d, stiffness: SPRING.snap.k }));
    });

  const gesture = Gesture.Race(pan, tap);
  const onLayout = (e: LayoutChangeEvent) => setSize({ w: e.nativeEvent.layout.width, h: e.nativeEvent.layout.height });
  const ready = size && lowImg && highImg && texts;

  return (
    <GestureDetector gesture={gesture}>
      <View style={styles.root} onLayout={onLayout}>
        <StatusBar style={barLight ? 'light' : 'dark'} />
        {ready && (
          <Canvas style={{ width: W, height: H }} pointerEvents="none">
            <Group transform={[{ translateX: offsetX }, { scale: S }]}>
              <Rect x={-offsetX / S} y={0} width={W / S} height={CH}>
                <Shader source={SKY_FX} uniforms={skyU}>
                  <ImageShader image={lowImg} tx="repeat" ty="repeat" />
                  <ImageShader image={highImg} tx="repeat" ty="repeat" />
                </Shader>
              </Rect>
              <Picture picture={underPic} />
              <BackdropBlur blur={24} clip={tabRR}>
                <Fill color={tabFill} />
              </BackdropBlur>
              <Picture picture={topPic} />
            </Group>
          </Canvas>
        )}
      </View>
    </GestureDetector>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: '#3D7FE6', overflow: 'hidden' },
});
