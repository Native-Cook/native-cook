import {
  BlurMask,
  Canvas,
  Circle,
  Group,
  ImageShader,
  LinearGradient,
  Oval,
  Path,
  RadialGradient,
  Rect,
  RoundedRect,
  Shader,
  Skia,
  useImage,
  vec,
} from '@shopify/react-native-skia';
import { useFonts } from 'expo-font';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { AppState, type LayoutChangeEvent, PixelRatio, Pressable, StyleSheet, Text, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  cubicBezier,
  Easing,
  ReduceMotion,
  useAnimatedReaction,
  useAnimatedStyle,
  useDerivedValue,
  useFrameCallback,
  useSharedValue,
  withSequence,
  withSpring,
  withTiming,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import Svg, { Path as SvgPath } from 'react-native-svg';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';

import { PressScale } from '../../../shared/PressScale';
import { useReducedMotionPreference } from '../../../shared/useReducedMotionPreference';
import { easeInOut, frameAt, FRONT_END, LOOP, sweepProgress } from './aia-motion';
import { AIA_SKSL } from './aia-shader';
import { APP_ICON, APPS, BAR_H, CARD_H, chipCentre, CHIP, COLOR, CTA_H, FONT, GLASSES, LAST_GLASS, makeLayout, SELF, SPACE } from './aia-theme';
import { CARD_R, makeUniforms } from './aia-uniforms';

/*
 App Icon Atelier — the "App Icon" page in the settings of an invented couples' journal, Amora.

 The screen: Settings ‹ and the title; the app's icon large on a pedestal, a glass pebble holding a
 white heart; the glass's name and a line about it; five glasses to choose from; a Home Screen preview
 on a satin wallpaper, with the icon in use in its slot; "Use This Icon".

 Signature: a new glass pours *through* the icon. Drag across the pebble and a liquid front follows
 the finger: the next glass on one side, the current one on the other, a bright meniscus where they
 meet; let go past half way (or flick) and it pours the rest of the way, otherwise it drains back.
 Tapping a glass pours it in on its own. Touch and hold the pebble and it collapses into its Home
 Screen slot, over the satin wallpaper it refracts, until the finger lifts. Everything — the paper,
 the card, the shadows tinted by the glass, both pebbles — is one ray-marched Skia shader.

 autoPlay: LOOP = 10 s (drag Lilac → Pearl, tap Amber, hold to preview, tap Lilac); frame 10 s equals
 frame 0. A touch takes over where the loop is; 2 s after the last touch the original glass pours back
 and the loop starts again. Reduced motion: no loop, no float; a glass changes in 220 ms, the preview
 moves in 200 ms without overshoot.
*/

const effect = Skia.RuntimeEffect.Make(AIA_SKSL);
if (!effect) throw new Error('App Icon Atelier: the glass shader failed to compile.');
const pixelRatio = PixelRatio.get();
const EASE_IN_OUT = Easing.bezier(0.77, 0, 0.175, 1);
const EASE_OUT = Easing.bezier(0.23, 1, 0.32, 1);
const CSS_EASE_OUT = cubicBezier(0.23, 1, 0.32, 1);
const NEVER = ReduceMotion.Never;
const TAU = Math.PI * 2;
const PHASE_SPEED = (TAU * 3) / LOOP; // the front's wobble, three times a loop

// the other apps' glyphs, in a 60 pt icon
const GLYPH = {
  notes: Skia.Path.MakeFromSVGString('M18 22 H42 M18 30 H42 M18 38 H34')!,
  tides: Skia.Path.MakeFromSVGString('M14 28 C20 22 24 22 30 28 S40 34 46 28 M14 37 C20 31 24 31 30 37 S40 43 46 37')!,
  ledger: Skia.Path.MakeFromSVGString('M20 42 V34 M30 42 V24 M40 42 V30')!,
};

function buzz(kind: 'select' | 'light' | 'success') {
  if (process.env.EXPO_OS === 'web') return;
  if (kind === 'select') Haptics.selectionAsync().catch(() => {});
  else if (kind === 'success') Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success).catch(() => {});
  else Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
}

type Props = { autoPlay?: boolean };

export function AppIconAtelier({ autoPlay = false }: Props) {
  const insets = useSafeAreaInsets();
  const reduced = useReducedMotionPreference();
  const auto = autoPlay && !reduced;
  const [size, setSize] = useState<{ w: number; h: number } | null>(null);
  const [fontsLoaded] = useFonts({
    [FONT.display]: require('./assets/fonts/InstrumentSerif-Regular.ttf'),
    [FONT.regular]: require('./assets/fonts/Inter-Regular.ttf'),
    [FONT.medium]: require('./assets/fonts/Inter-Medium.ttf'),
    [FONT.semibold]: require('./assets/fonts/Inter-SemiBold.ttf'),
  });
  const wallpaper = useImage(require('./assets/wallpaper.jpg'));
  const [active, setActive] = useState(AppState.currentState === 'active');
  useEffect(() => {
    const sub = AppState.addEventListener('change', (s) => setActive(s === 'active'));
    return () => sub.remove();
  }, []);
  const w = size?.w ?? 402;
  const h = size?.h ?? 874;
  const L = useMemo(() => makeLayout(w, h, insets.top, insets.bottom), [w, h, insets.top, insets.bottom]);

  /* ------------------------------------------------------------ state */
  const clock = useSharedValue(0);
  const mode = useSharedValue(auto ? 0 : 1); // 0 the loop, 1 the finger
  const autoStart = useSharedValue(0);
  const lastTouch = useSharedValue(-100);
  const resumeAsked = useSharedValue(0);
  const pin = useSharedValue(-1);

  const aIdx = useSharedValue(0);
  const bIdx = useSharedValue(1);
  const dir = useSharedValue(1);
  const front = useSharedValue(FRONT_END);
  const frontFrom = useSharedValue(FRONT_END);
  const pending = useSharedValue(-1);
  const lean = useSharedValue(0);
  const bob = useSharedValue(0);
  const press = useSharedValue(0);
  const peek = useSharedValue(0);
  const land = useSharedValue(0);
  const phase = useSharedValue(0);
  const applied = useSharedValue(0);
  const pop = useSharedValue(0);
  const holding = useSharedValue(0);
  const dragging = useSharedValue(0);

  const finger = useSharedValue(0);
  const fingerX = useSharedValue(0);
  const fingerY = useSharedValue(0);
  const fingerPress = useSharedValue(0);

  const [shown, setShown] = useState(0);
  const [inUse, setInUse] = useState(0);
  const [updated, setUpdated] = useState(false);

  /* ------------------------------------------------------------ glasses */
  // pour glass i in on its own, from the side its place in the row says
  const startSweep = (i: number) => {
    'worklet';
    const a = aIdx.get();
    if (i === a || i < 0 || i > LAST_GLASS) return;
    const d = i > a ? 1 : -1;
    dir.set(d);
    bIdx.set(i);
    front.set(d * FRONT_END);
    front.set(
      withTiming(-d * FRONT_END, { duration: reduced ? 220 : 900, easing: reduced ? Easing.linear : EASE_IN_OUT, reduceMotion: NEVER }, (fin) => {
        if (!fin) return;
        aIdx.set(i);
        front.set(d * FRONT_END);
        scheduleOnRN(buzz, 'select');
      }),
    );
  };

  const goTo = (i: number) => {
    'worklet';
    const k = sweepProgress(front.get(), dir.get());
    if (k > 0.001 && k < 0.999) {
      // a glass is still pouring: let it finish quickly, then pour the next one
      pending.set(i);
      const d = dir.get();
      front.set(
        withTiming(-d * FRONT_END, { duration: 160, easing: EASE_OUT, reduceMotion: NEVER }, (fin) => {
          if (!fin) return;
          aIdx.set(bIdx.get());
          front.set(d * FRONT_END);
          const n = pending.get();
          pending.set(-1);
          if (n >= 0) startSweep(n);
        }),
      );
      return;
    }
    startSweep(i);
  };

  const takeOver = () => {
    'worklet';
    lastTouch.set(clock.get());
    resumeAsked.set(0);
    if (mode.get() === 0) {
      mode.set(1);
      finger.set(withTiming(0, { duration: 150 }));
      fingerPress.set(0);
      bob.set(withTiming(0, { duration: 500, easing: EASE_OUT }));
      lean.set(withSpring(0, { duration: 500, dampingRatio: 0.85 }));
      press.set(withTiming(0, { duration: 150 }));
      land.set(withTiming(0, { duration: 150 }));
      if (peek.get() > 0.001 && holding.get() === 0) peek.set(withSpring(0, { duration: 600, dampingRatio: 0.75 }));
      const k = sweepProgress(front.get(), dir.get());
      if (k > 0.001 && k < 0.999) {
        // finish the loop's pour from where it is
        const d = dir.get();
        front.set(
          withTiming(-d * FRONT_END, { duration: 260 + 500 * (1 - k), easing: EASE_OUT, reduceMotion: NEVER }, (fin) => {
            if (!fin) return;
            aIdx.set(bIdx.get());
            front.set(d * FRONT_END);
          }),
        );
      }
    }
  };

  /* ------------------------------------------------------------ clock */
  const ticker = useFrameCallback((f) => {
    const dt = Math.min(0.05, (f.timeSincePreviousFrame ?? 16) / 1000);
    clock.set(clock.get() + dt);
    if (mode.get() === 0) {
      const t = pin.get() >= 0 ? pin.get() : (((clock.get() - autoStart.get()) % LOOP) + LOOP) % LOOP;
      const s = frameAt(t);
      aIdx.set(s.a);
      bIdx.set(s.b);
      dir.set(s.dir);
      front.set(s.front);
      lean.set(s.lean);
      bob.set(s.bob);
      press.set(s.press);
      peek.set(s.peek);
      land.set(s.land);
      phase.set((PHASE_SPEED * t) % TAU);
      finger.set(s.finger);
      fingerPress.set(s.fingerPress);
      if (s.fingerAt === 0) {
        fingerX.set(L.heroX + s.fingerX * L.heroS);
        fingerY.set(L.heroY + 0.18 * L.heroS);
      } else {
        fingerX.set(chipCentre(L, s.fingerX));
        fingerY.set(L.chipsTop + CHIP / 2);
      }
      return;
    }
    phase.set((phase.get() + PHASE_SPEED * dt) % TAU);
    if (!auto || holding.get() === 1 || dragging.get() === 1 || clock.get() - lastTouch.get() < 2) return;
    // two seconds after the last touch: the original glass pours back, then the loop starts again
    if (resumeAsked.get() === 0) {
      resumeAsked.set(1);
      if (peek.get() > 0.001) peek.set(withSpring(0, { duration: 600, dampingRatio: 0.8 }));
      if (aIdx.get() !== 0 || pending.get() >= 0) goTo(0);
      return;
    }
    const k = sweepProgress(front.get(), dir.get());
    const still = aIdx.get() === 0 && (k < 0.0005 || k > 0.9995) && Math.abs(peek.get()) < 0.002 && Math.abs(lean.get()) < 0.003 && Math.abs(bob.get()) < 0.001;
    if (still) {
      resumeAsked.set(0);
      autoStart.set(clock.get());
      mode.set(0);
    }
  }, false);
  // autostart only counts at mount, and a block opened from a link mounts while the app is inactive
  useEffect(() => {
    ticker.setActive(active);
  }, [ticker, active]);

  useEffect(() => {
    autoStart.set(clock.get());
    if (auto) {
      mode.set(0);
      return;
    }
    // the loop stops (Reduce Motion turned on, or no autoPlay): settle whatever it was in the middle of
    scheduleOnUI(() => {
      'worklet';
      mode.set(1);
      const k = sweepProgress(front.get(), dir.get());
      if (k > 0.001 && k < 0.999) {
        const d = dir.get();
        const keep = k > 0.5;
        front.set(
          withTiming(keep ? -d * FRONT_END : d * FRONT_END, { duration: 200, reduceMotion: NEVER }, (fin) => {
            if (!fin || !keep) return;
            aIdx.set(bIdx.get());
            front.set(d * FRONT_END);
          }),
        );
      }
      for (const v of [peek, bob, lean, press, land, finger, fingerPress]) v.set(withTiming(0, { duration: 200, reduceMotion: NEVER }));
    });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [auto]);

  const activeRef = active;
  useEffect(() => {
    if (!__DEV__) return;
    (globalThis as Record<string, unknown>).__iconAtelier = {
      at: (t: number) => pin.set(t),
      play: () => pin.set(-1),
      state: () =>
        JSON.stringify({ clock: +clock.get().toFixed(2), active: activeRef, mode: mode.get(), a: aIdx.get(), b: bIdx.get(), dir: dir.get(), front: +front.get().toFixed(3), peek: +peek.get().toFixed(3), applied: applied.get() }),
    };
  }, [pin, mode, aIdx, bIdx, dir, front, peek, applied, clock, activeRef]);

  // the name under the pebble follows the glass that covers most of it
  useAnimatedReaction(
    () => (sweepProgress(front.get(), dir.get()) > 0.5 ? bIdx.get() : aIdx.get()),
    (now, before) => {
      if (now !== before) scheduleOnRN(setShown, now);
    },
  );
  // a landing in the slot: squash, and a light tap
  useAnimatedReaction(
    () => peek.get() > 0.96,
    (now, before) => {
      if (now && before === false && mode.get() === 1) {
        if (!reduced) land.set(withSequence(withTiming(0.07, { duration: 90, easing: EASE_OUT }), withSpring(0, { duration: 420, dampingRatio: 0.5 })));
        scheduleOnRN(buzz, 'light');
      }
    },
  );

  /* ------------------------------------------------------------ gestures */
  const S = L.heroS;
  const drag = Gesture.Pan()
    .activeOffsetX([-10, 10])
    .failOffsetY([-24, 24])
    .onBegin(() => {
      takeOver();
      if (!reduced) press.set(withTiming(1, { duration: 120, easing: EASE_OUT }));
    })
    .onStart((e) => {
      dragging.set(1);
      const k = sweepProgress(front.get(), dir.get());
      if (k <= 0.001 || k >= 0.999) {
        if (k >= 0.999) {
          aIdx.set(bIdx.get());
        }
        // a fresh pour: dragging left brings the next glass in from the right
        const d = e.translationX < 0 ? 1 : -1;
        const a = aIdx.get();
        const target = a + d;
        dir.set(d);
        bIdx.set(target < 0 || target > LAST_GLASS ? a : target);
        front.set(d * FRONT_END);
      } else {
        // grab a pour that is still moving
        front.set(front.get());
        pending.set(-1);
      }
      frontFrom.set(front.get() - e.translationX / S);
    })
    .onUpdate((e) => {
      lastTouch.set(clock.get());
      const d = dir.get();
      const start = d * FRONT_END;
      let f = frontFrom.get() + e.translationX / S;
      if (bIdx.get() === aIdx.get()) {
        // nothing further this way: the front only leans in, with resistance
        const over = (start - f) * d;
        const give = over > 0 ? over / (1 + over / 0.35) : 0;
        f = start - d * give;
      }
      // the front never runs past its two ends
      front.set(Math.max(-FRONT_END, Math.min(FRONT_END, f)));
      if (!reduced) lean.set(lean.get() * 0.8 + 0.2 * Math.max(-0.22, Math.min(0.22, e.velocityX / (S * 22))));
    })
    .onEnd((e) => {
      const d = dir.get();
      const k = sweepProgress(front.get(), d);
      const v = e.velocityX / S;
      const along = -v * d;
      const commit = bIdx.get() !== aIdx.get() && along > -1.2 && (k > 0.45 || along > 1.2);
      const target = commit ? -d * FRONT_END : d * FRONT_END;
      const done = (fin?: boolean) => {
        'worklet';
        if (!fin || !commit) return;
        aIdx.set(bIdx.get());
        front.set(d * FRONT_END);
        scheduleOnRN(buzz, 'select');
      };
      if (reduced) front.set(withTiming(target, { duration: 220, reduceMotion: NEVER }, done));
      else front.set(withSpring(target, { duration: 620, dampingRatio: 0.92, velocity: v }, done));
    })
    .onFinalize(() => {
      dragging.set(0);
      lastTouch.set(clock.get());
      press.set(withTiming(0, { duration: 160, easing: EASE_OUT }));
      lean.set(withSpring(0, { duration: 650, dampingRatio: 0.6 }));
    });

  const hold = Gesture.LongPress()
    .minDuration(380)
    .maxDistance(14)
    .onBegin(() => {
      takeOver();
      if (!reduced) press.set(withTiming(1, { duration: 300, easing: EASE_OUT }));
    })
    .onStart(() => {
      holding.set(1);
      press.set(withTiming(0, { duration: 200 }));
      if (reduced) peek.set(withTiming(1, { duration: 200, reduceMotion: NEVER }));
      else peek.set(withSpring(1, { duration: 650, dampingRatio: 0.7 }));
    })
    .onFinalize(() => {
      lastTouch.set(clock.get());
      press.set(withTiming(0, { duration: 160 }));
      if (holding.get() === 1) {
        holding.set(0);
        if (reduced) peek.set(withTiming(0, { duration: 200, reduceMotion: NEVER }));
        else peek.set(withSpring(0, { duration: 700, dampingRatio: 0.72 }));
      }
    });
  const hero = Gesture.Race(drag, hold);

  const pick = useCallback(
    (i: number) => {
      scheduleOnUI(() => {
        'worklet';
        takeOver();
        goTo(i);
      });
    },
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [reduced, L],
  );
  const step = useCallback(
    (d: number) => {
      scheduleOnUI(() => {
        'worklet';
        takeOver();
        const k = sweepProgress(front.get(), dir.get());
        const now = k > 0.5 ? bIdx.get() : aIdx.get();
        goTo(Math.max(0, Math.min(LAST_GLASS, now + d)));
      });
    },
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [reduced, L],
  );

  const onApplied = useCallback((i: number) => {
    setInUse(i);
    setUpdated(true);
    buzz('success');
    setTimeout(() => setUpdated(false), 1800);
  }, [setInUse, setUpdated]);
  const applyIcon = useCallback(() => {
    scheduleOnUI(() => {
      'worklet';
      takeOver();
      const k = sweepProgress(front.get(), dir.get());
      const now = k > 0.5 ? bIdx.get() : aIdx.get();
      if (now === applied.get()) return;
      applied.set(now);
      if (!reduced) pop.set(withSequence(withTiming(1, { duration: 120, easing: EASE_OUT }), withSpring(0, { duration: 650, dampingRatio: 0.45 })));
      scheduleOnRN(onApplied, now);
    });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [reduced, onApplied]);

  /* ------------------------------------------------------------ drawing */
  const uniforms = useDerivedValue(() =>
    makeUniforms(
      L,
      {
        a: aIdx.get(),
        b: bIdx.get(),
        dir: dir.get(),
        front: front.get(),
        lean: lean.get(),
        bob: bob.get(),
        press: press.get(),
        peek: peek.get(),
        land: land.get(),
        phase: phase.get(),
        applied: applied.get(),
        pop: pop.get(),
      },
      pixelRatio,
    ),
  );
  const ringX = useDerivedValue(() => {
    const k = easeInOut(sweepProgress(front.get(), dir.get()));
    const a = aIdx.get();
    return chipCentre(L, a + (bIdx.get() - a) * k);
  });
  const fingerR = useDerivedValue(() => 22 * (1 - 0.12 * fingerPress.get()));
  const fingerFill = useDerivedValue(() => `rgba(28,22,48,${0.1 * finger.get()})`);
  const fingerRing = useDerivedValue(() => `rgba(28,22,48,${0.32 * finger.get()})`);
  const quiet = useAnimatedStyle(() => ({ opacity: 1 - 0.65 * Math.max(0, Math.min(1, peek.get())) }));

  const onLayout = (e: LayoutChangeEvent) => {
    const { width, height } = e.nativeEvent.layout;
    if (!size || size.w !== width || size.h !== height) setSize({ w: width, h: height });
  };

  const g = GLASSES[shown];
  const current = shown === inUse;
  const ctaLabel = updated ? 'Icon Updated' : current ? 'Current Icon' : 'Use This Icon';
  const chipY = L.chipsTop + CHIP / 2;

  return (
    <View style={styles.screen} onLayout={onLayout}>
      <StatusBar style="dark" />
      {/* the page, the Home Screen card and both pebbles */}
      <Canvas style={StyleSheet.absoluteFill} pointerEvents="none">
        <Rect x={0} y={0} width={w} height={h}>
          <Shader source={effect!} uniforms={uniforms}>
            {wallpaper ? (
              <ImageShader image={wallpaper} fit="cover" rect={{ x: L.cardX, y: L.cardY, width: L.cardW, height: CARD_H }} />
            ) : (
              <LinearGradient start={vec(L.cardX, L.cardY)} end={vec(L.cardX + L.cardW, L.cardY + CARD_H)} colors={['#E7DDFB', '#B9A3EE', '#F6D5C8']} />
            )}
          </Shader>
        </Rect>
        {/* the other apps */}
        {APPS.map((app, i) =>
          i === SELF ? null : (
            <AppTile key={app.name} x={L.appX[i] - APP_ICON / 2} y={L.appsY - APP_ICON / 2} from={app.from} to={app.to} glyph={app.glyph as keyof typeof GLYPH} />
          ),
        )}
        {/* the glasses to choose from, and the ring on the chosen one */}
        {GLASSES.map((gl, i) => (
          <Swatch key={gl.id} cx={chipCentre(L, i)} cy={chipY} light={gl.chip[0]} deep={gl.chip[1]} />
        ))}
        <Circle cx={ringX} cy={chipY} r={CHIP / 2 + 3} color={COLOR.chipRing} style="stroke" strokeWidth={1.5} />
      </Canvas>

      <GestureDetector gesture={hero}>
        <View
          style={[styles.stage, { top: L.barTop + BAR_H, height: L.eyebrowTop - L.barTop - BAR_H }]}
          accessible
          accessibilityRole="adjustable"
          accessibilityLabel="Amora app icon"
          accessibilityValue={{ text: g.name }}
          accessibilityHint="Swipe left or right to change the glass. Touch and hold to preview it on the Home Screen."
          accessibilityActions={[{ name: 'increment' }, { name: 'decrement' }]}
          onAccessibilityAction={(e) => step(e.nativeEvent.actionName === 'increment' ? 1 : -1)}
        />
      </GestureDetector>

      {/* the bar */}
      <View style={[styles.bar, { top: L.barTop }]}>
        {fontsLoaded && (
          <View style={styles.barTitleBox} pointerEvents="none">
            <Text style={styles.barTitle} allowFontScaling={false} accessibilityRole="header">
              App Icon
            </Text>
          </View>
        )}
        <PressScale style={styles.back} reduced={reduced} accessibilityRole="button" accessibilityLabel="Back to Settings" hitSlop={4}>
          <Svg width={12} height={20} viewBox="0 0 12 20">
            <SvgPath d="M10 2 L2 10 L10 18" stroke={COLOR.ink} strokeWidth={2.2} fill="none" strokeLinecap="round" strokeLinejoin="round" />
          </Svg>
          {fontsLoaded && (
            <Text style={styles.backText} allowFontScaling={false}>
              Settings
            </Text>
          )}
        </PressScale>
        <View style={styles.barSpacer} />
      </View>

      {fontsLoaded && (
        <>
          <Animated.View style={[StyleSheet.absoluteFill, quiet]} pointerEvents="box-none">
            {/* the glass's name and a line about it */}
            <View style={[styles.centerRow, { top: L.eyebrowTop, height: 16 }]} pointerEvents="none">
              <Text style={styles.eyebrow} allowFontScaling={false}>
                {`AMORA · ICON ${shown + 1} OF ${GLASSES.length}`}
              </Text>
            </View>
            <View style={[styles.swapBox, { top: L.nameTop, height: 42 }]} pointerEvents="none" accessibilityLiveRegion="polite">
              {GLASSES.map((gl, i) => (
                <Animated.Text key={gl.id} style={[styles.name, StyleSheet.absoluteFill, swap(i === shown, reduced)]} allowFontScaling={false} accessibilityElementsHidden={i !== shown} importantForAccessibility={i === shown ? 'auto' : 'no-hide-descendants'}>
                  {gl.name}
                </Animated.Text>
              ))}
            </View>
            <View style={[styles.swapBox, { top: L.noteTop, height: 20 }]} pointerEvents="none">
              {GLASSES.map((gl, i) => (
                <Animated.Text key={gl.id} style={[styles.note, StyleSheet.absoluteFill, swap(i === shown, reduced)]} allowFontScaling={false} accessibilityElementsHidden={i !== shown} importantForAccessibility={i === shown ? 'auto' : 'no-hide-descendants'}>
                  {gl.note}
                </Animated.Text>
              ))}
            </View>
            {/* the chips' touch targets (drawn in the canvas) */}
            {GLASSES.map((gl, i) => (
              <Pressable
                key={gl.id}
                style={[styles.chip, { left: chipCentre(L, i) - CHIP / 2, top: L.chipsTop }]}
                onPress={() => pick(i)}
                accessibilityRole="button"
                accessibilityLabel={gl.name}
                accessibilityState={{ selected: i === shown }}
                hitSlop={6}
              />
            ))}
          </Animated.View>

          {/* the Home Screen preview */}
          <Text style={[styles.section, { top: L.homeLabelTop, left: L.cardX + SPACE.xs }]} allowFontScaling={false}>
            Home Screen
          </Text>
          <View style={[styles.card, { left: L.cardX, top: L.cardY, width: L.cardW }]} pointerEvents="none" accessible accessibilityLabel={`Home Screen preview. Amora uses ${GLASSES[inUse].name}.`}>
            <Text style={styles.clock} allowFontScaling={false}>
              9:41
            </Text>
            {APPS.map((app, i) => (
              <Text key={app.name} style={[styles.appName, { left: L.appX[i] - L.cardX - 40, top: L.appsY - L.cardY + APP_ICON / 2 + 6 }]} allowFontScaling={false}>
                {app.name}
              </Text>
            ))}
          </View>

          <PressScale
            style={[styles.cta, { top: L.ctaTop }, current && !updated ? styles.ctaIdle : null]}
            reduced={reduced}
            onPress={applyIcon}
            disabled={current && !updated}
            accessibilityRole="button"
            accessibilityLabel={ctaLabel}
            accessibilityState={{ disabled: current && !updated }}
          >
            <Text style={[styles.ctaText, current && !updated ? styles.ctaIdleText : null]} allowFontScaling={false}>
              {ctaLabel}
            </Text>
          </PressScale>
        </>
      )}

      {/* the scripted finger of autoPlay, over everything */}
      <Canvas style={StyleSheet.absoluteFill} pointerEvents="none">
        <Circle cx={fingerX} cy={fingerY} r={fingerR} color={fingerFill} />
        <Circle cx={fingerX} cy={fingerY} r={fingerR} color={fingerRing} style="stroke" strokeWidth={1.5} />
      </Canvas>
    </View>
  );
}

function swap(on: boolean, reduced: boolean) {
  return {
    opacity: on ? 1 : 0,
    transform: [{ translateY: on || reduced ? 0 : 6 }],
    transitionProperty: ['opacity', 'transform'] as ['opacity', 'transform'],
    transitionDuration: 220,
    transitionTimingFunction: CSS_EASE_OUT,
  };
}

/* ------------------------------------------------------------ parts */

function AppTile({ x, y, from, to, glyph }: { x: number; y: number; from: string; to: string; glyph: keyof typeof GLYPH }) {
  const r = 15;
  return (
    <Group transform={[{ translateX: x }, { translateY: y }]}>
      <RoundedRect x={0} y={3} width={APP_ICON} height={APP_ICON} r={r} color="rgba(60,40,110,0.18)">
        <BlurMask blur={5} style="normal" />
      </RoundedRect>
      <RoundedRect x={0} y={0} width={APP_ICON} height={APP_ICON} r={r}>
        <LinearGradient start={vec(0, 0)} end={vec(0, APP_ICON)} colors={[from, to]} />
      </RoundedRect>
      <RoundedRect x={0.5} y={0.5} width={APP_ICON - 1} height={APP_ICON - 1} r={r - 0.5} color="rgba(255,255,255,0.45)" style="stroke" strokeWidth={1} />
      <Path path={GLYPH[glyph]} color="#FFFFFF" style="stroke" strokeWidth={4} strokeCap="round" strokeJoin="round" />
    </Group>
  );
}

function Swatch({ cx, cy, light, deep }: { cx: number; cy: number; light: string; deep: string }) {
  const r = CHIP / 2 - 4;
  return (
    <Group>
      <Circle cx={cx} cy={cy + 2} r={r} color="rgba(40,24,90,0.14)">
        <BlurMask blur={4} style="normal" />
      </Circle>
      <Circle cx={cx} cy={cy} r={r}>
        <RadialGradient c={vec(cx - r * 0.35, cy - r * 0.4)} r={r * 1.5} colors={[light, deep]} />
      </Circle>
      <Oval x={cx - r * 0.55} y={cy - r * 0.78} width={r * 0.8} height={r * 0.42} color="rgba(255,255,255,0.7)">
        <BlurMask blur={2} style="normal" />
      </Oval>
      <Circle cx={cx} cy={cy} r={r - 0.5} color="rgba(255,255,255,0.55)" style="stroke" strokeWidth={1} />
      <Circle cx={cx} cy={cy} r={r + 0.25} color="rgba(28,22,48,0.1)" style="stroke" strokeWidth={0.5} />
    </Group>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: COLOR.pageTop, overflow: 'hidden' },
  stage: { position: 'absolute', left: 0, right: 0 },
  bar: { position: 'absolute', left: SPACE.s, right: SPACE.l, height: BAR_H, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  back: { height: 44, flexDirection: 'row', alignItems: 'center', gap: 6, paddingHorizontal: SPACE.s },
  backText: { fontFamily: FONT.regular, fontSize: 17, color: COLOR.ink },
  barTitleBox: { position: 'absolute', left: -SPACE.s, right: -SPACE.l, top: 0, bottom: 0, alignItems: 'center', justifyContent: 'center' },
  barTitle: { fontFamily: FONT.semibold, fontSize: 17, color: COLOR.ink },
  barSpacer: { width: 44 },
  centerRow: { position: 'absolute', left: 0, right: 0, flexDirection: 'row', justifyContent: 'center', alignItems: 'center' },
  eyebrow: { fontFamily: FONT.medium, fontSize: 12, letterSpacing: 1.2, color: COLOR.mute },
  swapBox: { position: 'absolute', left: SPACE.l, right: SPACE.l },
  name: { fontFamily: FONT.display, fontSize: 38, lineHeight: 42, color: COLOR.ink, textAlign: 'center' },
  note: { fontFamily: FONT.regular, fontSize: 15, lineHeight: 20, color: COLOR.mute, textAlign: 'center' },
  chip: { position: 'absolute', width: CHIP, height: CHIP, borderRadius: CHIP / 2 },
  section: { position: 'absolute', fontFamily: FONT.semibold, fontSize: 13, lineHeight: 18, color: COLOR.mute, letterSpacing: 0.2 },
  card: { position: 'absolute', height: CARD_H, borderRadius: CARD_R },
  clock: { position: 'absolute', left: 20, top: 14, fontFamily: FONT.semibold, fontSize: 13, color: COLOR.onWall },
  appName: {
    position: 'absolute',
    width: 80,
    textAlign: 'center',
    fontFamily: FONT.medium,
    fontSize: 11,
    color: COLOR.onWall,
    textShadowColor: 'rgba(40,20,80,0.35)',
    textShadowRadius: 3,
    textShadowOffset: { width: 0, height: 0.5 },
  },
  cta: { position: 'absolute', left: SPACE.l, right: SPACE.l, height: CTA_H, borderRadius: CTA_H / 2, backgroundColor: COLOR.cta, alignItems: 'center', justifyContent: 'center' },
  ctaText: { fontFamily: FONT.semibold, fontSize: 17, color: COLOR.ctaInk },
  ctaIdle: { backgroundColor: 'transparent', borderWidth: 1, borderColor: COLOR.hair },
  ctaIdleText: { color: COLOR.mute },
});
