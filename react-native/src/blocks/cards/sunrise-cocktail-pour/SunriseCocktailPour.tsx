import { Canvas, Circle, Rect, Shader, Skia } from '@shopify/react-native-skia';
import { useFonts } from 'expo-font';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useEffect, useMemo, useState } from 'react';
import { AppState, type LayoutChangeEvent, PixelRatio, StyleSheet, Text, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  cubicBezier,
  Easing,
  ReduceMotion,
  useAnimatedReaction,
  useDerivedValue,
  useFrameCallback,
  useSharedValue,
  withSequence,
  withTiming,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import Svg, { Path as SvgPath } from 'react-native-svg';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';

import { PressScale } from '../../../shared/PressScale';
import { useReducedMotionPreference } from '../../../shared/useReducedMotionPreference';
import { frameAt, LEVEL, LOOP, pourAt, stirAt } from './scp-motion';
import { SCP_SKSL } from './scp-shader';
import { BAR_H, COLOR, COPY, CTA_H, FONT, makeLayout, SPACE, STEPS } from './scp-theme';
import { makeUniforms } from './scp-uniforms';

/*
 Sunrise Cocktail Pour — a recipe card in an invented bar's menu: the Tequila Sunrise.

 The screen: back and the price; a highball of orange juice and ice on a dark bar; the recipe's
 number, the name in a serif, its ingredients, the step's tip; one button that walks the recipe.

 Signature: the grenadine really sinks. Pour it and a thin red stream falls into the glass; being
 denser it slides to the bottom and climbs back up as a red-to-orange gradient — the sunrise —
 while the ice bobs and bubbles rise. Stir (or drag round the glass) and the colours blend into one
 coral as the ice turns. Serve slides the glass away and a fresh one in.

 autoPlay: LOOP = 10 s (pour, the sunrise, stir, serve, a fresh glass); frame 10 s equals frame 0.
 A touch takes over; 2 s after the last one a fresh glass is served and the loop restarts. Reduced
 motion: no loop and no bobbing; each step changes the drink in 300 ms.
*/

const effect = Skia.RuntimeEffect.Make(SCP_SKSL);
if (!effect) throw new Error('Sunrise Cocktail Pour: the shader failed to compile.');
const pixelRatio = PixelRatio.get();
const CSS_EASE_OUT = cubicBezier(0.23, 1, 0.32, 1);
const NEVER = ReduceMotion.Never;
const TAU = Math.PI * 2;
const PHASE_SPEED = (TAU * 3) / LOOP;

function buzz(kind: 'select' | 'light' | 'success') {
  if (process.env.EXPO_OS === 'web') return;
  if (kind === 'select') Haptics.selectionAsync().catch(() => {});
  else if (kind === 'success') Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success).catch(() => {});
  else Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
}

type Props = { autoPlay?: boolean };

export function SunriseCocktailPour({ autoPlay = false }: Props) {
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
  const mode = useSharedValue(auto ? 0 : 1);
  const autoStart = useSharedValue(0);
  const lastTouch = useSharedValue(-100);
  const resumeAsked = useSharedValue(0);
  const pin = useSharedValue(-1);

  const level = useSharedValue(LEVEL);
  const red = useSharedValue(0);
  const rise = useSharedValue(0);
  const mix = useSharedValue(0);
  const stream = useSharedValue(0);
  const swirl = useSharedValue(0);
  const shift = useSharedValue(0);
  const phase = useSharedValue(0);
  const step = useSharedValue(0);
  const pouredAt = useSharedValue(-1);
  const stirredAt = useSharedValue(-1);
  const stirring = useSharedValue(0);
  const finger = useSharedValue(0);
  const fingerPress = useSharedValue(0);
  const [stepShown, setStepShown] = useState(0);

  /* ------------------------------------------------------------ the recipe */
  const fresh = () => {
    'worklet';
    level.set(LEVEL);
    red.set(0);
    rise.set(0);
    mix.set(0);
    stream.set(0);
    swirl.set(0);
    pouredAt.set(-1);
    stirredAt.set(-1);
    step.set(0);
  };
  const pour = () => {
    'worklet';
    if (step.get() !== 0) return;
    step.set(1);
    if (reduced) {
      const cfg = { duration: 300, reduceMotion: NEVER };
      red.set(withTiming(1, cfg));
      rise.set(withTiming(1, cfg));
      return;
    }
    pouredAt.set(clock.get());
    scheduleOnRN(buzz, 'light');
  };
  const stir = () => {
    'worklet';
    if (step.get() !== 1) return;
    step.set(2);
    if (reduced) {
      mix.set(withTiming(1, { duration: 300, reduceMotion: NEVER }));
      return;
    }
    stirredAt.set(clock.get());
    scheduleOnRN(buzz, 'light');
  };
  const serve = () => {
    'worklet';
    if (step.get() !== 2) return;
    scheduleOnRN(buzz, 'success');
    if (reduced) {
      fresh();
      return;
    }
    step.set(3);
    shift.set(withSequence(withTiming(-3.4, { duration: 700, easing: Easing.bezier(0.77, 0, 0.175, 1) }, (fin) => {
      if (fin) fresh();
    }), withTiming(3.4, { duration: 0 }), withTiming(0, { duration: 900, easing: Easing.bezier(0.23, 1, 0.32, 1) })));
  };
  const next = () => {
    'worklet';
    const s = step.get();
    if (s === 0) pour();
    else if (s === 1) stir();
    else if (s === 2) serve();
  };

  const takeOver = () => {
    'worklet';
    lastTouch.set(clock.get());
    resumeAsked.set(0);
    if (mode.get() === 0) {
      mode.set(1);
      finger.set(withTiming(0, { duration: 150 }));
      fingerPress.set(0);
      // the loop's pour or stir carries on under the finger's clock
      const s = step.get();
      if (Math.abs(shift.get()) > 0.001) {
        shift.set(withTiming(0, { duration: 400 }));
        if (shift.get() > 0) fresh();
      }
      if (s === 1 && red.get() < 0.999) pouredAt.set(clock.get() - 1.2);
      if (s === 2 && mix.get() < 0.999) stirredAt.set(clock.get() - 0.4);
      stream.set(0);
    }
  };

  /* ------------------------------------------------------------ clock */
  const ticker = useFrameCallback((f) => {
    const dt = Math.min(0.05, (f.timeSincePreviousFrame ?? 16) / 1000);
    clock.set(clock.get() + dt);
    if (mode.get() === 0) {
      const t = pin.get() >= 0 ? pin.get() : (((clock.get() - autoStart.get()) % LOOP) + LOOP) % LOOP;
      const s = frameAt(t);
      level.set(s.level);
      red.set(s.red);
      rise.set(s.rise);
      mix.set(s.mix);
      stream.set(s.stream);
      swirl.set(s.swirl);
      shift.set(s.shift);
      step.set(s.step);
      phase.set((PHASE_SPEED * t) % TAU);
      finger.set(s.finger);
      fingerPress.set(s.fingerPress);
      return;
    }
    if (!reduced) phase.set((phase.get() + PHASE_SPEED * dt) % TAU);
    if (pouredAt.get() >= 0) {
      const p = pourAt(clock.get() - pouredAt.get());
      level.set(p.level);
      red.set(p.red);
      rise.set(p.rise);
      stream.set(p.stream);
      if (p.rise >= 1) pouredAt.set(-1);
    }
    if (stirredAt.get() >= 0) {
      const s = stirAt(clock.get() - stirredAt.get());
      mix.set(Math.max(mix.get(), s.mix));
      swirl.set(s.swirl);
      if (clock.get() - stirredAt.get() > 1.6) stirredAt.set(-1);
    }
    if (!auto || stirring.get() === 1 || clock.get() - lastTouch.get() < 2) return;
    if (resumeAsked.get() === 0) {
      resumeAsked.set(1);
      // a fresh glass: through the serve if a drink is made, otherwise straight back
      if (step.get() === 2) serve();
      else if (step.get() === 1) {
        mix.set(withTiming(1, { duration: 300 }, (fin) => {
          if (fin) {
            step.set(2);
            serve();
          }
        }));
      }
      return;
    }
    if (step.get() === 0 && Math.abs(shift.get()) < 0.001 && pouredAt.get() < 0) {
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
    scheduleOnUI(() => {
      'worklet';
      mode.set(1);
      fresh();
      shift.set(0);
      finger.set(0);
      fingerPress.set(0);
    });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [auto]);

  useEffect(() => {
    if (!__DEV__) return;
    (globalThis as Record<string, unknown>).__sunrisePour = {
      at: (t: number) => pin.set(t),
      play: () => pin.set(-1),
      state: () => JSON.stringify({ clock: +clock.get().toFixed(2), mode: mode.get(), step: step.get(), red: +red.get().toFixed(3), mix: +mix.get().toFixed(3), shift: +shift.get().toFixed(3) }),
    };
  }, [pin, clock, mode, step, red, mix, shift]);

  useAnimatedReaction(
    () => Math.min(2, step.get()),
    (now, before) => {
      if (now !== before) scheduleOnRN(setStepShown, now);
    },
  );

  /* ------------------------------------------------------------ gestures */
  // a circular drag round the glass stirs it
  const stirGesture = Gesture.Pan()
    .minDistance(6)
    .onBegin(() => {
      takeOver();
    })
    .onStart(() => {
      stirring.set(1);
    })
    .onUpdate((e) => {
      lastTouch.set(clock.get());
      if (step.get() === 1 && Math.hypot(e.translationX, e.translationY) > 40) stir();
      if (step.get() === 2 && !reduced) swirl.set(swirl.get() + (e.velocityX / (L.heroS * 60)) * 0.016 * 4);
    })
    .onFinalize(() => {
      stirring.set(0);
      lastTouch.set(clock.get());
    });

  /* ------------------------------------------------------------ drawing */
  const uniforms = useDerivedValue(() =>
    makeUniforms(L, { level: level.get(), red: red.get(), rise: rise.get(), mix: mix.get(), stream: stream.get(), swirl: swirl.get(), shift: shift.get(), phase: phase.get() }, pixelRatio),
  );
  const fingerR = useDerivedValue(() => 22 * (1 - 0.12 * fingerPress.get()));
  const fingerFill = useDerivedValue(() => `rgba(245,238,228,${0.14 * finger.get()})`);
  const fingerRing = useDerivedValue(() => `rgba(245,238,228,${0.5 * finger.get()})`);

  const onLayout = (e: LayoutChangeEvent) => {
    const { width, height } = e.nativeEvent.layout;
    if (!size || size.w !== width || size.h !== height) setSize({ w: width, h: height });
  };

  return (
    <View style={styles.screen} onLayout={onLayout}>
      <StatusBar style="light" />
      <Canvas style={StyleSheet.absoluteFill} pointerEvents="none">
        <Rect x={0} y={0} width={w} height={h}>
          <Shader source={effect!} uniforms={uniforms} />
        </Rect>
      </Canvas>

      <GestureDetector gesture={stirGesture}>
        <View
          style={[styles.stage, { left: L.heroX - L.heroS * 0.9, top: L.heroY - L.heroS * 1.2, width: L.heroS * 1.8, height: L.heroS * 2.5 }]}
          accessible
          accessibilityRole="image"
          accessibilityLabel={['A highball of orange juice on ice', 'A tequila sunrise, red settling under orange', 'A stirred tequila sunrise'][stepShown]}
          accessibilityHint={stepShown === 1 ? 'Drag round the glass to stir' : undefined}
        />
      </GestureDetector>

      <View style={[styles.bar, { top: L.barTop }]}>
        <PressScale style={styles.back} reduced={reduced} accessibilityRole="button" accessibilityLabel="Back" hitSlop={4}>
          <Svg width={12} height={20} viewBox="0 0 12 20">
            <SvgPath d="M10 2 L2 10 L10 18" stroke={COLOR.ink} strokeWidth={2.2} fill="none" strokeLinecap="round" strokeLinejoin="round" />
          </Svg>
        </PressScale>
        {fontsLoaded && (
          <Text style={styles.price} allowFontScaling={false} accessibilityLabel="14 dollars">
            {COPY.price}
          </Text>
        )}
      </View>

      {fontsLoaded && (
        <>
          <Text style={[styles.eyebrow, { top: L.eyebrowTop }]} allowFontScaling={false}>
            {COPY.eyebrow}
          </Text>
          <Text style={[styles.name, { top: L.nameTop }]} allowFontScaling={false} accessibilityRole="header">
            {COPY.name}
          </Text>
          <Text style={[styles.recipe, { top: L.recipeTop }]} allowFontScaling={false}>
            {COPY.recipe}
          </Text>
          <View style={[styles.swapBox, { top: L.noteTop, height: 20 }]} accessibilityLiveRegion="polite">
            {STEPS.map((s, i) => (
              <Animated.Text key={s.note} style={[styles.note, StyleSheet.absoluteFill, swap(i === stepShown, reduced)]} allowFontScaling={false} accessibilityElementsHidden={i !== stepShown} importantForAccessibility={i === stepShown ? 'auto' : 'no-hide-descendants'}>
                {s.note}
              </Animated.Text>
            ))}
          </View>
          <PressScale
            style={[styles.cta, { top: L.ctaTop }]}
            reduced={reduced}
            onPress={() =>
              scheduleOnUI(() => {
                'worklet';
                takeOver();
                next();
              })
            }
            accessibilityRole="button"
            accessibilityLabel={STEPS[stepShown].cta}
          >
            <Text style={styles.ctaText} allowFontScaling={false}>
              {STEPS[stepShown].cta}
            </Text>
          </PressScale>
        </>
      )}

      <Canvas style={StyleSheet.absoluteFill} pointerEvents="none">
        <Circle cx={L.w / 2} cy={L.ctaTop + CTA_H / 2} r={fingerR} color={fingerFill} />
        <Circle cx={L.w / 2} cy={L.ctaTop + CTA_H / 2} r={fingerR} color={fingerRing} style="stroke" strokeWidth={1.5} />
      </Canvas>
    </View>
  );
}

function swap(on: boolean, reduced: boolean) {
  return {
    opacity: on ? 1 : 0,
    transform: [{ translateY: on || reduced ? 0 : 6 }],
    transitionProperty: ['opacity', 'transform'] as ['opacity', 'transform'],
    transitionDuration: 260,
    transitionTimingFunction: CSS_EASE_OUT,
  };
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: COLOR.page, overflow: 'hidden' },
  stage: { position: 'absolute' },
  bar: { position: 'absolute', left: SPACE.s, right: SPACE.l, height: BAR_H, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  back: { width: 44, height: 44, alignItems: 'center', justifyContent: 'center' },
  price: { fontFamily: FONT.semibold, fontSize: 17, color: COLOR.brass, fontVariant: ['tabular-nums'] },
  eyebrow: { position: 'absolute', left: 0, right: 0, textAlign: 'center', fontFamily: FONT.medium, fontSize: 12, lineHeight: 16, letterSpacing: 1.4, color: COLOR.brass },
  name: { position: 'absolute', left: 0, right: 0, textAlign: 'center', fontFamily: FONT.display, fontSize: 40, lineHeight: 44, color: COLOR.ink },
  recipe: { position: 'absolute', left: 0, right: 0, textAlign: 'center', fontFamily: FONT.regular, fontSize: 15, lineHeight: 20, color: COLOR.mute },
  swapBox: { position: 'absolute', left: SPACE.xl, right: SPACE.xl },
  note: { fontFamily: FONT.medium, fontSize: 14, lineHeight: 20, color: COLOR.brass, textAlign: 'center' },
  cta: { position: 'absolute', left: SPACE.l, right: SPACE.l, height: CTA_H, borderRadius: CTA_H / 2, backgroundColor: COLOR.cta, alignItems: 'center', justifyContent: 'center' },
  ctaText: { fontFamily: FONT.semibold, fontSize: 17, color: COLOR.ctaInk },
});
