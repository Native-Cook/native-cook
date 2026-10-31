import { Canvas, Picture, Rect, Shader, Skia, useFont, useImage } from '@shopify/react-native-skia';
import { useFonts } from 'expo-font';
import * as Haptics from 'expo-haptics';
import { LinearGradient } from 'expo-linear-gradient';
import { StatusBar } from 'expo-status-bar';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { AccessibilityInfo, AppState, type LayoutChangeEvent, PixelRatio, Pressable, StyleSheet, Text, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  cubicBezier,
  Easing,
  ReduceMotion,
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
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';

import { useReducedMotionPreference } from '../../../shared/useReducedMotionPreference';
import { type CoinGeo, drawBursts } from './tec-draw';
import {
  BASE,
  boxBump,
  BURST_LIFE,
  clamp01,
  columnPos,
  easeInOut,
  glowOf,
  gleamAt,
  LOOP,
  loopBursts,
  mix,
  RESUME_AFTER,
  REFILL_SECONDS,
  sceneAt,
  seg,
  SHEET_DISMISS,
  SHEET_FLICK,
  STRIDE,
  T,
  TAPS,
} from './tec-motion';
import { TEC_SKSL } from './tec-shader';
import { COLOR, FONT, makeLayout, SPACE, TYPE } from './tec-theme';
import { type TecCue, useTecAudio } from './use-tec-audio';

/*
 Tap Earn Coin — a pay app's "keep tapping to earn" reward page.

 Look (the pin, a screen recording of a Korean pay app's event): a cream-to-white page, an orange
 two-line title and one line of copy, a big gold push button with the currency mark on its cap,
 the money so far in three digit boxes, how many taps are left, and an orange share button. When
 the taps run out, a white sheet asks whether to send a reminder, with a yellow bell.

 Motion. From the pin: a tap pushes the cap into its seat, throws a spray of coins over the page,
 floats a "+N¢" pill and rolls the counter; the last tap opens the reminder sheet. Ours: the
 button and the bell are one object, ray-marched in a shader, so the button squats, lifts out of
 its place and melts into the bell as it flies into the sheet, where it rings; closing the sheet
 flies it home and it melts back into the button. Two coins of every payout fly into the boxes,
 a gleam runs across the gold after each press, and the bell rides the sheet when you drag it.

 autoPlay: LOOP = 9.6 s (timeline in tec-motion.ts), frame 9.6 s equals frame 0. While the sheet
 is up the counter rolls back under the dim, so the loop can start again from 438¢.
 Touch: tap the button (press depth follows the finger, release springs back with a payout);
 three taps empty it and open the sheet; drag the sheet down (or flick) or tap Maybe later to
 close it, Agree and remind me rings the bell; taps refill after three seconds. A touch takes over
 from autoPlay where it is, and 2 s after the last one the loop eases back in.
 Reduced motion: no loop and no flight; taps count without coins, the bell and sheet cross-fade.
*/

const effect = Skia.RuntimeEffect.Make(TEC_SKSL);
if (!effect) throw new Error('Tap Earn Coin: the gold button shader failed to compile.');
const pixelRatio = PixelRatio.get();

const EASE_OUT = Easing.bezier(0.23, 1, 0.32, 1);
const EASE_IN_OUT = Easing.bezier(0.77, 0, 0.175, 1);
const PRESS = { transitionProperty: 'transform', transitionDuration: 120, transitionTimingFunction: cubicBezier(0.23, 1, 0.32, 1) } as const;
const NEVER = ReduceMotion.Never;
const DIGIT_STRIP = [0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 0];

type Props = { autoPlay?: boolean };
type Label = { kind: 'left'; n: number } | { kind: 'refill'; s: number };

// JS-side state of the one mounted block: taps left, the counter's target, the sheet, and timers.
// Gesture callbacks reach it, so it lives outside React (refs read there trip the compiler's rules).
const js = {
  taps: TAPS,
  target: BASE,
  open: false,
  timers: [] as ReturnType<typeof setTimeout>[],
  refill: null as ReturnType<typeof setInterval> | null,
  resume: null as ReturnType<typeof setTimeout> | null,
  lastTap: 0,
  combo: 0,
};
function resetJs() {
  js.timers.forEach(clearTimeout);
  if (js.refill) clearInterval(js.refill);
  if (js.resume) clearTimeout(js.resume);
  js.taps = TAPS;
  js.target = BASE;
  js.open = false;
  js.timers = [];
  js.refill = null;
  js.resume = null;
  js.lastTap = 0;
  js.combo = 0;
}
function after(fn: () => void, ms: number) {
  js.timers.push(setTimeout(fn, ms));
}

function buzz(kind: 'light' | 'medium' | 'rigid' | 'success') {
  if (process.env.EXPO_OS === 'web') return;
  if (kind === 'success') {
    Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success).catch(() => {});
    return;
  }
  const style = kind === 'medium' ? Haptics.ImpactFeedbackStyle.Medium
    : kind === 'rigid' ? Haptics.ImpactFeedbackStyle.Rigid : Haptics.ImpactFeedbackStyle.Light;
  Haptics.impactAsync(style).catch(() => {});
}

export function TapEarnCoin({ autoPlay = false }: Props) {
  const insets = useSafeAreaInsets();
  const reduced = useReducedMotionPreference();
  const auto = autoPlay && !reduced;
  const [size, setSize] = useState<{ w: number; h: number } | null>(null);
  const [fontsLoaded] = useFonts({
    [FONT.regular]: require('./assets/fonts/Inter-Regular.ttf'),
    [FONT.medium]: require('./assets/fonts/Inter-Medium.ttf'),
    [FONT.semibold]: require('./assets/fonts/Inter-SemiBold.ttf'),
    [FONT.bold]: require('./assets/fonts/Inter-Bold.ttf'),
  });
  const pillFont = useFont(require('./assets/fonts/Inter-SemiBold.ttf'), 14);
  // 24 frames of a ray-marched pewter coin turning over, rendered offline from the same studio light
  const coinAtlas = useImage(require('./assets/coin-atlas.png'));
  const [active, setActive] = useState(AppState.currentState === 'active');
  useEffect(() => {
    const sub = AppState.addEventListener('change', (s) => setActive(s === 'active'));
    return () => sub.remove();
  }, []);

  const w = size?.w ?? 402;
  const h = size?.h ?? 874;
  const L = useMemo(() => makeLayout(w, h, insets.top, insets.bottom), [w, h, insets.top, insets.bottom]);
  const play = useTecAudio(!reduced);

  /* ------------------------------------------------------------ the scene */
  const press = useSharedValue(0);
  const hop = useSharedValue(0);
  const morph = useSharedValue(0);
  const lift = useSharedValue(0);
  const travel = useSharedValue(0);
  const dim = useSharedValue(0);
  const sheet = useSharedValue(0);
  const swing = useSharedValue(0);
  const amount = useSharedValue(BASE);
  const later = useSharedValue(0);
  const gleam = useSharedValue(-1);
  const shake = useSharedValue(0);
  const labelPop = useSharedValue(0);
  const fade = useSharedValue(1); // object opacity (reduced motion cross-fade)
  const sheetFade = useSharedValue(1);
  const leanX = useSharedValue(0);
  const leanZ = useSharedValue(0);
  const charge = useSharedValue(0);
  const pressStart = useSharedValue(0);
  const chargeTick = useSharedValue(0);
  const refillStart = useSharedValue(-100);

  const clock = useSharedValue(0);
  const mode = useSharedValue(auto ? 0 : 1); // 0 autoPlay, 1 the finger
  const autoStart = useSharedValue(0);
  const base = useSharedValue(BASE);
  const bursts = useSharedValue<number[]>([]); // manual: [startClock, seed, pay, power, …]
  const lastTouch = useSharedValue(-100);
  const loopLabel = useSharedValue(TAPS);
  const pin = useSharedValue(-1);
  const pressing = useSharedValue(0);
  const shownFrames = useSharedValue(0);
  const [shown, setShown] = useState(false);

  /* ------------------------------------------------------------ JS state */
  const [label, setLabel] = useState<Label>({ kind: 'left', n: TAPS });
  const [earned, setEarned] = useState(BASE);
  const [sheetUp, setSheetUp] = useState(false);
  const [ctaDown, setCtaDown] = useState(false);
  const [blueDown, setBlueDown] = useState(false);
  useEffect(() => {
    resetJs();
    return resetJs;
  }, []);

  const cue = useCallback((c: TecCue, rate = 1) => play(c, rate), [play]);

  const popLabel = useCallback(() => {
    labelPop.set(withSequence(withTiming(1, { duration: 90, reduceMotion: NEVER }), withSpring(0, { duration: 420, dampingRatio: 0.5 })));
  }, [labelPop]);

  const stopRefill = () => {
    if (js.refill) clearInterval(js.refill);
    js.refill = null;
  };
  const startRefill = useCallback(() => {
    stopRefill();
    let s = REFILL_SECONDS;
    setLabel({ kind: 'refill', s });
    js.refill = setInterval(() => {
      s -= 1;
      if (s > 0) {
        setLabel({ kind: 'refill', s });
        if (s === 1) {
          after(() => scheduleOnUI(() => {
            'worklet';
            refillStart.set(clock.get());
          }), 550);
        }
        return;
      }
      stopRefill();
      js.taps = TAPS;
      setLabel({ kind: 'left', n: TAPS });
      popLabel();
      buzz('light');
    }, 1000);
  }, [popLabel, refillStart, clock]);

  /* ------------------------------------------------------------ sheet */
  const openSheet = useCallback(() => {
    js.open = true;
    setSheetUp(true);
    if (reduced) {
      fade.set(withSequence(withTiming(0, { duration: 160, reduceMotion: NEVER }), withTiming(1, { duration: 200, reduceMotion: NEVER })));
      after(() => { morph.set(1); lift.set(1); travel.set(1); sheet.set(1); }, 160);
      sheetFade.set(0);
      sheetFade.set(withTiming(1, { duration: 260, reduceMotion: NEVER }));
      dim.set(withTiming(1, { duration: 260, reduceMotion: NEVER }));
      after(() => cue('ding'), 200);
      return;
    }
    AccessibilityInfo.announceForAccessibility('Out of taps. Want a reminder when you can tap again?');
    cue('morph');
    // squat, rise to the middle of the page at full size, melt into the bell there and ring, then
    // shrink into the rising sheet
    hop.set(withSequence(withTiming(0.55, { duration: 150, easing: EASE_OUT }), withTiming(0, { duration: 230, easing: EASE_IN_OUT })));
    lift.set(withDelay(160, withTiming(1, { duration: 460, easing: EASE_OUT })));
    morph.set(withDelay(300, withTiming(1, { duration: 1100, easing: EASE_IN_OUT })));
    swing.set(withDelay(1400, withSequence(withTiming(0.32, { duration: 110 }), withSpring(0, { duration: 1200, dampingRatio: 0.16 }))));
    dim.set(withDelay(1600, withTiming(1, { duration: 480, easing: EASE_OUT })));
    travel.set(withDelay(1760, withTiming(1, { duration: 740, easing: EASE_IN_OUT })));
    sheet.set(withDelay(1760, withSpring(1, { duration: 640, dampingRatio: 0.82 })));
    after(() => cue('ding'), 1400);
    after(() => {
      buzz('medium');
      // the sheet waits for an answer: count the idle time from when it has landed
      scheduleOnUI(() => {
        'worklet';
        lastTouch.set(clock.get());
      });
    }, 2500);
  }, [reduced, cue, fade, morph, lift, travel, sheet, sheetFade, dim, hop, swing, lastTouch, clock]);

  const closeSheet = useCallback((velocity = 0, refill = true) => {
    if (!js.open) return;
    js.open = false;
    setSheetUp(false);
    if (reduced) {
      fade.set(withSequence(withTiming(0, { duration: 160, reduceMotion: NEVER }), withTiming(1, { duration: 200, reduceMotion: NEVER })));
      after(() => { morph.set(0); lift.set(0); travel.set(0); sheet.set(0); }, 160);
      dim.set(withTiming(0, { duration: 220, reduceMotion: NEVER }));
      sheetFade.set(withTiming(0, { duration: 200, reduceMotion: NEVER }));
    } else {
      // the bell rises out of the sheet and grows back to full size, melts into the button in the
      // open, then drops into its seat
      sheet.set(withSpring(0, { velocity, duration: 460, dampingRatio: 1 }));
      dim.set(withTiming(0, { duration: 420, easing: EASE_IN_OUT }));
      travel.set(withTiming(0, { duration: 640, easing: EASE_IN_OUT }));
      morph.set(withDelay(460, withTiming(0, { duration: 900, easing: EASE_IN_OUT })));
      lift.set(withDelay(1240, withTiming(0, { duration: 450, easing: EASE_IN_OUT })));
      hop.set(withDelay(1690, withSequence(withTiming(0.45, { duration: 80 }), withSpring(0, { duration: 640, dampingRatio: 0.32 }))));
      after(() => cue('morph'), 460);
      after(() => buzz('light'), 1700);
    }
    if (refill && js.taps <= 0) after(startRefill, reduced ? 200 : 700);
  }, [reduced, fade, morph, lift, travel, sheet, sheetFade, dim, hop, startRefill, cue]);

  const remind = useCallback(() => {
    cue('ding');
    buzz('success');
    if (!reduced) swing.set(withSequence(withTiming(-0.4, { duration: 100 }), withSpring(0, { duration: 1300, dampingRatio: 0.12 })));
    after(() => closeSheet(0), 750);
  }, [cue, reduced, swing, closeSheet]);

  /* ------------------------------------------------------------ taps */
  const onPressIn = useCallback(() => buzz('light'), []);

  const addBurst = useCallback((seed: number, pay: number, power: number) => {
    scheduleOnUI((s: number, p: number, pw: number) => {
      'worklet';
      const now = clock.get();
      const prev = bursts.get();
      const next: number[] = [];
      for (let i = 0; i + 3 < prev.length; i += STRIDE) {
        if (now - prev[i] < BURST_LIFE) next.push(prev[i], prev[i + 1], prev[i + 2], prev[i + 3]);
      }
      next.push(now, s, p, pw);
      bursts.set(next);
    }, seed, pay, power);
  }, [clock, bursts]);

  const onTap = useCallback((held = 0) => {
    if (js.open) return;
    if (js.taps <= 0) {
      // out of taps: the button refuses with a short shake
      shake.set(withSequence(withTiming(1, { duration: 50 }), withSpring(0, { duration: 420, dampingRatio: 0.2 })));
      popLabel();
      buzz('rigid');
      return;
    }
    // quick taps chain into a combo: a bigger spray and a higher chink
    const now = Date.now();
    js.combo = now - js.lastTap < 450 ? Math.min(js.combo + 1, 4) : 0;
    js.lastTap = now;
    // a held press pays like a combo: up to the biggest spray and +12¢
    const power = Math.max(Math.min(js.combo, 2), held > 0.5 ? 2 : held > 0.15 ? 1 : 0);
    const pay = 3 + Math.floor(Math.random() * 7) + js.combo + Math.round(held * 12);
    js.target += pay;
    setEarned(js.target);
    if (!reduced) {
      addBurst(1 + Math.random() * 90, pay, power);
    }
    amount.set(withDelay(reduced ? 0 : 120, withTiming(js.target, { duration: reduced ? 0 : 520, easing: EASE_OUT })));
    if (!reduced) gleam.set(withSequence(withTiming(-1, { duration: 0 }), withTiming(1, { duration: 760, easing: EASE_OUT })));
    cue('coin', 1 + 0.07 * Math.max(js.combo, power * 1.5));
    js.taps -= 1;
    setLabel({ kind: 'left', n: js.taps });
    popLabel();
    if (js.taps === 0) after(openSheet, reduced ? 250 : 380);
  }, [reduced, addBurst, amount, gleam, cue, popLabel, shake, openSheet]);

  /* ------------------------------------------------------------ autoPlay hand-over */
  const takeOverJS = useCallback((loopLabelValue: number, ahead: number, opening: boolean) => {
    js.target = ahead;
    js.open = opening;
    setEarned(ahead);
    setSheetUp(opening);
    if (loopLabelValue >= 4) {
      js.taps = 0;
      startRefill();
    } else {
      js.taps = loopLabelValue;
      setLabel({ kind: 'left', n: loopLabelValue });
    }
  }, [startRefill]);

  const takeOver = () => {
    'worklet';
    lastTouch.set(clock.get());
    if (mode.get() === 1) return;
    mode.set(1);
    const t = (((clock.get() - autoStart.get()) % LOOP) + LOOP) % LOOP;
    // coins in flight keep flying: they become bursts on the shared clock
    const lb = loopBursts(t);
    const now = clock.get();
    const next: number[] = [];
    for (let i = 0; i + 3 < lb.length; i += STRIDE) next.push(now - lb[i], lb[i + 1], lb[i + 2], lb[i + 3]);
    bursts.set(next);
    const ahead = Math.round(sceneAt(Math.min(t + 0.75, LOOP), base.get()).amount);
    const opening = t > T.squash && t < T.close0;
    // finish whatever was moving, from where it is
    press.set(withSpring(0, { duration: 420, dampingRatio: 0.5 }));
    leanX.set(withSpring(0, { duration: 420, dampingRatio: 0.5 }));
    leanZ.set(withSpring(0, { duration: 420, dampingRatio: 0.5 }));
    later.set(withTiming(0, { duration: 150 }));
    amount.set(withTiming(ahead, { duration: 420, easing: EASE_OUT }));
    if (opening) {
      lift.set(withTiming(1, { duration: 400, easing: EASE_IN_OUT }));
      morph.set(withTiming(1, { duration: 520, easing: EASE_IN_OUT }));
      travel.set(withTiming(1, { duration: 560, easing: EASE_IN_OUT }));
      dim.set(withTiming(1, { duration: 360 }));
      sheet.set(withSpring(1, { duration: 520, dampingRatio: 0.85 }));
      hop.set(withSpring(0, { duration: 400, dampingRatio: 0.7 }));
    } else {
      lift.set(withTiming(0, { duration: 450, easing: EASE_IN_OUT }));
      morph.set(withTiming(0, { duration: 520, easing: EASE_IN_OUT }));
      travel.set(withTiming(0, { duration: 560, easing: EASE_IN_OUT }));
      dim.set(withTiming(0, { duration: 300 }));
      sheet.set(withSpring(0, { duration: 420, dampingRatio: 1 }));
      hop.set(withSpring(0, { duration: 500, dampingRatio: 0.4 }));
    }
    scheduleOnRN(takeOverJS, loopLabel.get(), ahead, opening);
  };

  const resumeJS = useCallback(() => {
    if (!auto) return;
    const touchedAt = lastTouch.get();
    js.timers.forEach(clearTimeout);
    js.timers = [];
    if (js.open) closeSheet(0, false);
    stopRefill();
    js.taps = TAPS;
    setLabel({ kind: 'left', n: TAPS });
    popLabel();
    if (js.resume) clearTimeout(js.resume);
    js.resume = setTimeout(() => {
      if (lastTouch.get() !== touchedAt) return; // touched again meanwhile
      amount.set(js.target);
      scheduleOnUI((b: number) => {
        'worklet';
        base.set(b);
        autoStart.set(clock.get());
        loopLabel.set(TAPS);
        mode.set(0);
      }, js.target);
    }, 1100);
  }, [auto, lastTouch, closeSheet, popLabel, amount, base, autoStart, clock, loopLabel, mode]);

  const setLoopLabel = useCallback((v: number) => {
    if (v >= 4) setLabel({ kind: 'refill', s: 1 });
    else {
      setLabel({ kind: 'left', n: v });
      popLabel();
    }
  }, [popLabel]);

  /* ------------------------------------------------------------ clock */
  const resumeAsked = useSharedValue(0);
  useFrameCallback((f) => {
    const dt = Math.min(0.05, (f.timeSincePreviousFrame ?? 16) / 1000);
    clock.set(clock.get() + dt);
    if (shownFrames.get() < 4) {
      shownFrames.set(shownFrames.get() + 1);
      if (shownFrames.get() === 4) scheduleOnRN(setShown, true);
    }
    if (mode.get() === 0) {
      const t = pin.get() >= 0 ? pin.get() : (((clock.get() - autoStart.get()) % LOOP) + LOOP) % LOOP;
      const s = sceneAt(t, base.get());
      press.set(s.press);
      hop.set(s.hop);
      morph.set(s.morph);
      lift.set(s.lift);
      travel.set(s.travel);
      dim.set(s.dim);
      sheet.set(s.sheet);
      swing.set(s.swing);
      amount.set(s.amount);
      later.set(s.later);
      leanX.set(s.leanX);
      leanZ.set(s.leanZ);
      gleam.set(gleamAt(t));
      if (s.label !== loopLabel.get()) {
        loopLabel.set(s.label);
        scheduleOnRN(setLoopLabel, s.label);
      }
      resumeAsked.set(0);
    } else if (pressing.get() === 1) {
      // holding the button charges it: deeper, warmer, trembling, a tick every 0.2 s
      const c = clamp01((clock.get() - pressStart.get() - 0.3) / 1.2);
      charge.set(c);
      if (c > 0 && c < 1 && clock.get() - chargeTick.get() > 0.2) {
        chargeTick.set(clock.get());
        scheduleOnRN(buzz, 'light');
      }
    } else if (auto && resumeAsked.get() === 0 && clock.get() - lastTouch.get() > RESUME_AFTER && pressing.get() === 0) {
      resumeAsked.set(1);
      scheduleOnRN(resumeJS);
    }
  }, active);


  useEffect(() => {
    mode.set(auto ? 0 : 1);
    autoStart.set(clock.get());
  }, [auto, mode, autoStart, clock]);

  // Dev only: pin the loop at one moment for still captures.
  useEffect(() => {
    if (!__DEV__) return;
    (globalThis as Record<string, unknown>).__tapEarn = {
      at: (t: number) => pin.set(t),
      play: () => pin.set(-1),
      state: () => JSON.stringify({
        taps: js.taps, open: js.open, target: js.target, mode: mode.get(),
        sheet: +sheet.get().toFixed(2), morph: +morph.get().toFixed(2), amount: +amount.get().toFixed(1),
      }),
    };
  }, [pin, mode, sheet, morph, amount]);

  /* ------------------------------------------------------------ gestures */
  const hitW = L.coinR * 2;
  const hitCapY = L.coinR * 0.85 - 0.3 * L.coinR;
  const tap = Gesture.Tap()
    .maxDuration(60000)
    .maxDistance(80)
    .onBegin((e) => {
      takeOver();
      if (sheet.get() > 0.02 || morph.get() > 0.02 || lift.get() > 0.02) return;
      pressing.set(1);
      pressStart.set(clock.get());
      press.set(withTiming(1, { duration: 90, easing: EASE_OUT }));
      // the side you press goes down first
      const dx = Math.max(-1, Math.min(1, (e.x - hitW / 2) / (hitW * 0.32)));
      const dy = Math.max(-1, Math.min(1, (e.y - hitCapY) / (hitW * 0.2)));
      leanX.set(withTiming(dx * 0.075, { duration: 90, easing: EASE_OUT }));
      leanZ.set(withTiming(dy * 0.06, { duration: 90, easing: EASE_OUT }));
      scheduleOnRN(onPressIn);
    })
    .onFinalize((_, success) => {
      if (pressing.get() === 0) return;
      pressing.set(0);
      lastTouch.set(clock.get());
      resumeAsked.set(0);
      press.set(withSpring(0, { duration: 480, dampingRatio: 0.42 }));
      leanX.set(withSpring(0, { duration: 420, dampingRatio: 0.45 }));
      leanZ.set(withSpring(0, { duration: 420, dampingRatio: 0.45 }));
      const held = charge.get();
      charge.set(withTiming(0, { duration: 220, easing: EASE_OUT }));
      if (success) scheduleOnRN(onTap, held);
    });

  const sheetH = L.sheetH;
  const sheetPan = Gesture.Pan()
    .onBegin(() => {
      takeOver();
    })
    .onUpdate((e) => {
      lastTouch.set(clock.get());
      const dy = e.translationY;
      const v = dy >= 0 ? Math.max(0, 1 - dy / sheetH) : 1 + (-dy / (1 + -dy / 60)) / sheetH;
      sheet.set(v);
      // the bell rides the sheet, then lifts off and melts back toward the button under the finger
      const u = 1 - Math.min(v, 1);
      travel.set(1 - easeInOut(seg(u, 0.25, 0.9)));
      morph.set(1 - easeInOut(seg(u, 0.3, 0.95)));
      dim.set(1 - easeInOut(seg(u, 0.1, 1)));
    })
    .onEnd((e) => {
      lastTouch.set(clock.get());
      resumeAsked.set(0);
      const v = -e.velocityY / sheetH;
      if (e.translationY > SHEET_DISMISS || e.velocityY > SHEET_FLICK) scheduleOnRN(closeSheet, v);
      else {
        sheet.set(withSpring(1, { velocity: v, duration: 420, dampingRatio: 0.8 }));
        travel.set(withSpring(1, { duration: 420, dampingRatio: 0.85 }));
        morph.set(withSpring(1, { duration: 420, dampingRatio: 0.85 }));
        dim.set(withTiming(1, { duration: 260 }));
      }
    });

  const touch = (fn?: () => void) => () => {
    scheduleOnUI(() => {
      'worklet';
      takeOver();
    });
    fn?.();
  };

  /* ------------------------------------------------------------ derived drawing */
  // the bursts alive now, as [age, seed, pay, power] records, from the loop or from the finger
  const live = useDerivedValue(() => {
    if (mode.get() === 0) {
      const t = pin.get() >= 0 ? pin.get() : (((clock.get() - autoStart.get()) % LOOP) + LOOP) % LOOP;
      return loopBursts(t);
    }
    const now = clock.get();
    const b = bursts.get();
    const list: number[] = [];
    for (let i = 0; i + 3 < b.length; i += STRIDE) list.push(now - b[i], b[i + 1], b[i + 2], b[i + 3]);
    return list;
  });
  const unitButton = L.coinR;
  const unitBell = L.bellSize / 1.3;
  const obj = useDerivedValue(() => {
    const tr = travel.get();
    const lf = lift.get();
    const by = L.bellY + (1 - sheet.get()) * L.sheetH;
    // the showcase point: the middle of the page, a little above the seat, at full size
    const sx = L.coinX;
    const sy = mix(L.coinY, L.coinY - 64, lf);
    const x = mix(sx, L.bellX, tr) + shake.get() * 7 * Math.sin(clock.get() * 70);
    const y = mix(sy, by, tr) - Math.sin(Math.PI * tr) * L.coinR * 0.45;
    const u = mix(unitButton * (1 + 0.1 * lf), unitBell, tr);
    return { x, y, u };
  });
  const rect = useDerivedValue(() => {
    const o = obj.get();
    return Skia.XYWHRect(o.x - 1.65 * o.u, o.y - 1.65 * o.u, 3.3 * o.u, 3.3 * o.u);
  });
  const uniforms = useDerivedValue(() => {
    const o = obj.get();
    const tr = travel.get();
    const m = morph.get();
    return {
      uCentre: [o.x, o.y],
      uUnit: o.u,
      uPixelRatio: pixelRatio,
      uTilt: mix(0.56, 0.12, m),
      uPress: press.get() * (1 + 0.3 * charge.get()),
      uHop: hop.get() + charge.get() * 0.07 * Math.sin(clock.get() * 57),
      uMorph: m,
      uSwing: swing.get(),
      uShadow: mix(1, 0.4, tr) * (1 - 0.85 * Math.sin(Math.PI * tr)) * (1 - 0.55 * lift.get() * (1 - tr)),
      uGleam: gleam.get(),
      uAlpha: fade.get(),
      uLean: [leanX.get(), leanZ.get()],
      uGlow: Math.max(glowOf(live.get()), charge.get() * 0.9),
    };
  });

  const geo = useMemo<CoinGeo>(() => {
    const boxesW = 3 * L.digitW + 2 * L.digitGap;
    const left = L.w / 2 - (boxesW + L.digitGap + 18) / 2;
    return {
      w: L.w,
      h: L.h,
      ox: L.coinX,
      oy: L.coinY - 0.32 * L.coinR,
      R: L.coinR,
      tx: [0, 1, 2].map((i) => left + L.digitW / 2 + i * (L.digitW + L.digitGap)),
      ty: L.digitsTop + L.digitH / 2,
      pillY: L.coinY - 1.02 * L.coinR,
      floor: L.ctaTop,
      labelX: L.w / 2 + 34,
      labelY: L.labelTop + 9,
    };
  }, [L]);

  const picture = useDerivedValue(() => {
    const o = obj.get();
    let refillAge = clock.get() - refillStart.get();
    if (mode.get() === 0) {
      const t = pin.get() >= 0 ? pin.get() : (((clock.get() - autoStart.get()) % LOOP) + LOOP) % LOOP;
      refillAge = t - (T.refill - 0.45);
    }
    return drawBursts(live.get(), geo, pillFont, {
      keep: clamp01(1 - 1.2 * dim.get()),
      pull: seg(morph.get(), 0, 0.55),
      px: o.x,
      py: o.y,
      refillAge,
      atlas: coinAtlas,
    });
  });

  /* ------------------------------------------------------------ styles */
  const dimStyle = useAnimatedStyle(() => ({ opacity: dim.get() }));
  // the copy steps back while the button is lifted to the middle of the page
  const headStyle = useAnimatedStyle(() => ({ opacity: 1 - 0.7 * lift.get() * (1 - travel.get()) }));
  const sheetStyle = useAnimatedStyle(() => ({
    opacity: sheetFade.get(),
    transform: [{ translateY: (1 - sheet.get()) * (L.sheetH + 24) }],
  }));
  const laterStyle = useAnimatedStyle(() => ({
    transform: [{ scale: 1 - 0.04 * later.get() }],
    opacity: 1 - 0.35 * later.get(),
  }));
  const labelStyle = useAnimatedStyle(() => ({
    transform: [{ scale: 1 + 0.08 * labelPop.get() }, { translateX: shake.get() * 5 * Math.sin(clock.get() * 70) }],
  }));
  const col0 = useAnimatedStyle(() => ({ transform: [{ translateY: -(columnPos(amount.get(), 0) % 10) * L.digitH }] }));
  const col1 = useAnimatedStyle(() => ({ transform: [{ translateY: -(columnPos(amount.get(), 1) % 10) * L.digitH }] }));
  const col2 = useAnimatedStyle(() => ({ transform: [{ translateY: -(columnPos(amount.get(), 2) % 10) * L.digitH }] }));
  const cols = [col2, col1, col0];
  const bump0 = useAnimatedStyle(() => ({ transform: [{ scale: 1 + 0.07 * boxBump(live.get(), 0) }] }));
  const bump1 = useAnimatedStyle(() => ({ transform: [{ scale: 1 + 0.07 * boxBump(live.get(), 1) }] }));
  const bump2 = useAnimatedStyle(() => ({ transform: [{ scale: 1 + 0.07 * boxBump(live.get(), 2) }] }));
  const bumps = [bump0, bump1, bump2];

  const ready = fontsLoaded && size !== null;
  const onLayout = (e: LayoutChangeEvent) => {
    const { width, height } = e.nativeEvent.layout;
    if (!size || size.w !== width || size.h !== height) setSize({ w: width, h: height });
  };

  const labelText = label.kind === 'left'
    ? <>Taps left: <Text style={styles.labelNum}>{label.n}</Text></>
    : <>Refill in <Text style={styles.labelNum}>00:0{label.s}</Text></>;

  return (
    <View style={styles.screen} onLayout={onLayout}>
      <StatusBar style="dark" />
      <LinearGradient
        colors={[COLOR.pageTop, COLOR.pageMid, COLOR.pageBottom]}
        locations={[0, 0.5, 0.72]}
        style={StyleSheet.absoluteFill}
      />
      {ready && (
        <View style={[StyleSheet.absoluteFill, { opacity: shown ? 1 : 0 }]}>
          {/* title and copy in one flow, so a wrap at large text sizes pushes the copy down */}
          <Animated.View style={[styles.head, { top: L.titleTop }, headStyle]}>
            <Text style={styles.title} accessibilityRole="header" maxFontSizeMultiplier={1.25}>
              {'Keep tapping the button\nto earn 1,000¢'}
            </Text>
            <Text style={[styles.subtitle, { marginTop: L.subtitleTop - L.titleTop - 2 * TYPE.title.line }]} maxFontSizeMultiplier={1.3}>
              Reach 1,000¢ and it’s paid out as cash.
            </Text>
          </Animated.View>

          <View style={[styles.digits, { top: L.digitsTop, gap: L.digitGap }]} accessible accessibilityLabel={`${earned} cents earned`}>
            {cols.map((style, i) => (
              <Animated.View key={i} style={[styles.box, { width: L.digitW, height: L.digitH, borderRadius: 10 * (L.w / 402) }, bumps[i]]}>
                <Animated.View style={style} accessibilityElementsHidden importantForAccessibility="no-hide-descendants">
                  {DIGIT_STRIP.map((d, j) => (
                    <Text key={j} maxFontSizeMultiplier={1} style={[styles.digit, { height: L.digitH, lineHeight: L.digitH }]}>{d}</Text>
                  ))}
                </Animated.View>
              </Animated.View>
            ))}
            <Text style={styles.unit} maxFontSizeMultiplier={1.2}>¢</Text>
          </View>

          <Animated.View style={[styles.labelRow, { top: L.labelTop }, labelStyle]}>
            <Text style={styles.label} accessibilityLiveRegion="polite" maxFontSizeMultiplier={1.4}>{labelText}</Text>
          </Animated.View>

          <Animated.View style={[styles.ctaWrap, PRESS, { top: L.ctaTop, left: L.side, right: L.side, height: L.ctaH, transform: [{ scale: ctaDown ? 0.96 : 1 }] }]}>
            <Pressable
              onPressIn={touch(() => { setCtaDown(true); buzz('light'); })}
              onPressOut={() => setCtaDown(false)}
              style={[styles.cta, ctaDown && { backgroundColor: COLOR.ctaPressed }]}
              accessibilityRole="button"
            >
              <Text style={styles.ctaText} maxFontSizeMultiplier={1.3}>Share to earn more</Text>
            </Pressable>
          </Animated.View>

          <GestureDetector gesture={tap}>
            <View
              style={[styles.hit, { left: L.coinX - L.coinR, top: L.coinY - L.coinR * 0.85, width: L.coinR * 2, height: L.coinR * 1.6 }]}
              accessible
              accessibilityRole="button"
              accessibilityLabel="Tap to earn coins"
              accessibilityHint={label.kind === 'left' ? `Tap to earn coins. ${label.n} taps left.` : 'Out of taps.'}
              onAccessibilityTap={onTap}
            />
          </GestureDetector>

          <Animated.View style={[StyleSheet.absoluteFill, { backgroundColor: COLOR.dim }, dimStyle]} pointerEvents="none" />

          <GestureDetector gesture={sheetPan}>
            <Animated.View
              style={[styles.sheet, { top: L.sheetTop, minHeight: L.sheetH + 40, paddingBottom: 40 }, sheetStyle]}
              onAccessibilityEscape={() => closeSheet(0)}
              accessibilityElementsHidden={!sheetUp}
              importantForAccessibility={sheetUp ? 'auto' : 'no-hide-descendants'}
              accessibilityViewIsModal={sheetUp}
            >
              <View style={{ height: 64 * L.s + L.bellSize + 12 }} />
              <Text style={styles.sheetTitle}>{'Want a reminder when\nyou can tap again?'}</Text>
              <Text style={styles.sheetBody}>Taps refill every hour.</Text>
              <Pressable style={styles.sheetRow} onPress={touch()} accessibilityRole="button">
                <Text style={styles.sheetRowText}>[Optional] Tap-to-earn reminders</Text>
                <Text style={styles.chevron}>›</Text>
              </Pressable>
              <Animated.View style={[styles.blueWrap, PRESS, { marginHorizontal: L.side, height: L.ctaH, transform: [{ scale: blueDown ? 0.96 : 1 }] }]}>
                <Pressable
                  onPressIn={() => setBlueDown(true)}
                  onPressOut={() => setBlueDown(false)}
                  onPress={touch(remind)}
                  style={[styles.blue, blueDown && { backgroundColor: COLOR.bluePressed }]}
                  accessibilityRole="button"
                >
                  <Text style={styles.blueText} maxFontSizeMultiplier={1.3}>Agree and remind me</Text>
                </Pressable>
              </Animated.View>
              <Pressable
                onPressIn={() => later.set(withTiming(1, { duration: 80 }))}
                onPressOut={() => later.set(withTiming(0, { duration: 200 }))}
                onPress={touch(() => closeSheet(0))}
                accessibilityRole="button"
                hitSlop={12}
              >
                <Animated.Text style={[styles.later, laterStyle]}>Maybe later</Animated.Text>
              </Pressable>
            </Animated.View>
          </GestureDetector>

          <Canvas style={StyleSheet.absoluteFill} pointerEvents="none">
            <Rect rect={rect}>
              <Shader source={effect!} uniforms={uniforms} />
            </Rect>
            <Picture picture={picture} />
          </Canvas>
        </View>
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: COLOR.pageBottom, overflow: 'hidden' },
  head: { position: 'absolute', left: SPACE.xxl, right: SPACE.xxl, alignItems: 'center' },
  title: {
    textAlign: 'center',
    fontFamily: FONT.bold,
    fontSize: TYPE.title.size,
    lineHeight: TYPE.title.line,
    letterSpacing: TYPE.title.track,
    color: COLOR.title,
  },
  subtitle: {
    textAlign: 'center',
    fontFamily: FONT.regular,
    fontSize: TYPE.subtitle.size,
    lineHeight: TYPE.subtitle.line,
    letterSpacing: TYPE.subtitle.track,
    color: COLOR.ink,
  },
  digits: {
    position: 'absolute',
    left: 0,
    right: 0,
    flexDirection: 'row',
    justifyContent: 'center',
    alignItems: 'center',
  },
  box: { backgroundColor: COLOR.digitBox, overflow: 'hidden', alignItems: 'center' },
  digit: {
    fontFamily: FONT.semibold,
    fontSize: TYPE.digit.size,
    letterSpacing: TYPE.digit.track,
    color: COLOR.digit,
    textAlign: 'center',
    fontVariant: ['tabular-nums'],
  },
  unit: { fontFamily: FONT.semibold, fontSize: TYPE.unit.size, color: COLOR.unit, marginLeft: 2 },
  labelRow: { position: 'absolute', left: 0, right: 0, alignItems: 'center' },
  label: {
    fontFamily: FONT.medium,
    fontSize: TYPE.label.size,
    lineHeight: TYPE.label.line,
    letterSpacing: TYPE.label.track,
    color: COLOR.ink,
  },
  labelNum: { color: COLOR.labelNum, fontFamily: FONT.semibold, fontVariant: ['tabular-nums'] },
  ctaWrap: { position: 'absolute' },
  cta: {
    flex: 1,
    borderRadius: 14,
    backgroundColor: COLOR.cta,
    alignItems: 'center',
    justifyContent: 'center',
  },
  ctaText: { fontFamily: FONT.semibold, fontSize: TYPE.cta.size, letterSpacing: TYPE.cta.track, color: COLOR.ctaInk },
  hit: { position: 'absolute', borderRadius: 999 },
  sheet: {
    position: 'absolute',
    left: 0,
    right: 0,
    backgroundColor: COLOR.sheet,
    borderTopLeftRadius: 18,
    borderTopRightRadius: 18,
  },
  sheetTitle: {
    marginHorizontal: 22,
    fontFamily: FONT.bold,
    fontSize: TYPE.sheetTitle.size,
    lineHeight: TYPE.sheetTitle.line,
    letterSpacing: TYPE.sheetTitle.track,
    color: COLOR.sheetTitle,
  },
  sheetBody: {
    marginTop: SPACE.s,
    marginHorizontal: 22,
    fontFamily: FONT.regular,
    fontSize: TYPE.sheetBody.size,
    lineHeight: TYPE.sheetBody.line,
    color: COLOR.sheetBody,
  },
  sheetRow: {
    marginTop: SPACE.xl,
    marginHorizontal: 22,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    minHeight: 44,
  },
  sheetRowText: { fontFamily: FONT.regular, fontSize: TYPE.sheetRow.size, lineHeight: TYPE.sheetRow.line, color: COLOR.sheetRow },
  chevron: { fontFamily: FONT.regular, fontSize: 22, color: COLOR.chevron, marginTop: -2 },
  blueWrap: { marginTop: SPACE.xl },
  blue: { flex: 1, borderRadius: 14, backgroundColor: COLOR.blue, alignItems: 'center', justifyContent: 'center' },
  blueText: { fontFamily: FONT.semibold, fontSize: TYPE.cta.size, letterSpacing: TYPE.cta.track, color: '#FFFFFF' },
  later: {
    marginTop: SPACE.xl,
    textAlign: 'center',
    fontFamily: FONT.medium,
    fontSize: 15,
    color: COLOR.sheetRow,
  },
});
