import { Canvas, Fill, Shader, Skia } from '@shopify/react-native-skia';
import { useFonts } from 'expo-font';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { AppState, type LayoutChangeEvent, PixelRatio, StyleSheet, Text, TextInput, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  Easing,
  ReduceMotion,
  useAnimatedProps,
  useAnimatedReaction,
  useAnimatedStyle,
  useDerivedValue,
  useFrameCallback,
  useSharedValue,
  withSpring,
  withTiming,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

import { useReducedMotionPreference } from '../../../shared/useReducedMotionPreference';
import { eraOf, FIRST, frameAt, LAST, LOOP } from './ies-motion';
import { IES_SKSL } from './ies-shader';
import { APP, C, ERAS, FONT, ICON_HALF, makeLayout, STEP } from './ies-theme';

/*
 Icon Era Scrubber — one app icon through eighteen years of icon design, scrubbed on a year ruler.

 The screen: a dark room lit by two soft coloured lights; the Postbox app's icon in the middle; under it
 the year, the era's name and a line about it; a ruler of years along the bottom.

 Signature: drag the ruler and the icon becomes its own past and future, continuously: 2008's glossy
 tile with a bevel, a curved gloss and an airmail envelope with a stamp; 2013's flat tile and printed
 white envelope; 2019's soft, rounded, lit tile; 2026's clear glass that bends the room's lights behind
 it, where the envelope folds into a paper plane. The changes take a year and a half of the ruler, so
 the finger can stop half-way through one. A tick for every year, the icon leans with the ruler's
 speed; let go and the ruler glides to a year at the finger's speed. Tap a year to go there.

 autoPlay: LOOP = 10 s (a finger drags the ruler forward, holding half-way into Flat and into Glass,
 reaches 2026, then flicks it back to 2008); frame 10 s equals frame 0. A touch takes over; 2 s after
 the last one the ruler glides back to 2008 and the loop starts again.
 Reduced motion: no loop; the ruler moves without the glide's overshoot and the icon does not lean.
*/

const effect = Skia.RuntimeEffect.Make(IES_SKSL);
if (!effect) throw new Error('Icon Era Scrubber: the shader failed to compile.');
const pixelRatio = PixelRatio.get();
const EASE_IN_OUT = Easing.bezier(0.77, 0, 0.175, 1);
const NEVER = ReduceMotion.Never;
const AnimatedInput = Animated.createAnimatedComponent(TextInput);
const YEARS = Array.from({ length: LAST - FIRST + 1 }, (_, i) => FIRST + i);
// unlabelled ticks past both ends, fading out, so the ruler runs off the screen at 2008 and 2026 too
const GHOSTS = 8;
const GHOST_AT = [
  ...Array.from({ length: GHOSTS }, (_, i) => -(i + 1)),
  ...Array.from({ length: GHOSTS }, (_, i) => LAST - FIRST + 1 + i),
];

function tick() {
  if (process.env.EXPO_OS === 'web') return;
  Haptics.selectionAsync().catch(() => {});
}

type Props = { autoPlay?: boolean };

export function IconEraScrubber({ autoPlay = false }: Props) {
  const insets = useSafeAreaInsets();
  const reduced = useReducedMotionPreference();
  const auto = autoPlay && !reduced;
  const [fontsLoaded] = useFonts({
    [FONT.regular]: require('./assets/fonts/Inter-Regular.ttf'),
    [FONT.medium]: require('./assets/fonts/Inter-Medium.ttf'),
    [FONT.semibold]: require('./assets/fonts/Inter-SemiBold.ttf'),
  });
  const [active, setActive] = useState(AppState.currentState === 'active');
  useEffect(() => {
    const sub = AppState.addEventListener('change', (s) => setActive(s === 'active'));
    return () => sub.remove();
  }, []);
  const [size, setSize] = useState({ w: 402, h: 874 });
  const L = useMemo(() => makeLayout(size.w, size.h, insets.top, insets.bottom), [size, insets.top, insets.bottom]);
  const [shown, setShown] = useState(FIRST); // the year, for VoiceOver

  /* ------------------------------------------------------------ state */
  const clock = useSharedValue(0);
  const time = useSharedValue(0); // the room's lights' phase: one turn per loop, never jumping
  const phaseOff = useSharedValue(0);
  const mode = useSharedValue(auto ? 0 : 1);
  const autoStart = useSharedValue(0);
  const lastTouch = useSharedValue(-100);
  const resumeAsked = useSharedValue(0);
  const pin = useSharedValue(-1);
  const stay = useSharedValue(0);

  const year = useSharedValue<number>(FIRST);
  const lean = useSharedValue(0);
  const finger = useSharedValue(0);
  const fingerX = useSharedValue(0);
  const dragging = useSharedValue(0);
  const gliding = useSharedValue(0);
  const y0 = useSharedValue(0);

  /* ------------------------------------------------------------ moving the ruler (above the clock) */
  function glideTo(target: number, velocity: number) {
    'worklet';
    gliding.set(1);
    const done = (fin?: boolean) => {
      'worklet';
      if (fin) gliding.set(0);
    };
    if (reduced) year.set(withTiming(target, { duration: 300, easing: EASE_IN_OUT, reduceMotion: NEVER }, done));
    else year.set(withSpring(target, { duration: 700, dampingRatio: 0.85, velocity, reduceMotion: NEVER }, done));
  }

  /* ------------------------------------------------------------ clock */
  useFrameCallback((f) => {
    const dt = Math.min(0.05, (f.timeSincePreviousFrame ?? 16) / 1000);
    clock.set(clock.get() + dt);
    if (mode.get() === 0) {
      const t = pin.get() >= 0 ? pin.get() : (((clock.get() - autoStart.get()) % LOOP) + LOOP) % LOOP;
      const s = frameAt(t);
      time.set((2 * Math.PI * t) / LOOP + phaseOff.get());
      year.set(s.year);
      lean.set(s.lean);
      finger.set(s.finger);
      fingerX.set(s.fingerX);
      return;
    }
    if (!reduced) time.set(time.get() + (2 * Math.PI * dt) / LOOP);
    if (dragging.get() === 1 || gliding.get() === 1) lastTouch.set(clock.get());
    if (!auto || stay.get() === 1 || clock.get() - lastTouch.get() < 2) return;
    if (resumeAsked.get() === 0) {
      resumeAsked.set(1);
      if (Math.abs(year.get() - FIRST) > 0.001) {
        gliding.set(1);
        year.set(
          withTiming(FIRST, { duration: 1200, easing: EASE_IN_OUT }, (fin) => {
            if (fin) gliding.set(0);
          }),
        );
      }
    } else if (gliding.get() === 0 && Math.abs(year.get() - FIRST) < 0.002) {
      resumeAsked.set(0);
      year.set(FIRST);
      lean.set(0);
      // the room's lights carry on from where they are
      phaseOff.set(time.get() % (2 * Math.PI));
      autoStart.set(clock.get());
      mode.set(0);
    }
  }, active);

  useEffect(() => {
    mode.set(auto ? 0 : 1);
    autoStart.set(clock.get());
  }, [auto, mode, autoStart, clock]);

  useEffect(() => {
    if (!__DEV__) return;
    (globalThis as Record<string, unknown>).__ies = {
      at: (t: number) => pin.set(t),
      play: () => pin.set(-1),
      stay: (s: boolean) => {
        stay.set(s ? 1 : 0);
        if (s) mode.set(1);
      },
      state: () => JSON.stringify({ mode: mode.get(), year: +year.get().toFixed(2) }),
    };
  }, [pin, stay, mode, year]);

  // a tick for every year that passes under the needle; VoiceOver's value follows
  useAnimatedReaction(
    () => Math.round(Math.max(FIRST, Math.min(LAST, year.get()))),
    (now, before) => {
      if (before === null || now === before) return;
      scheduleOnRN(setShown, now);
      if (mode.get() === 1) scheduleOnRN(tick);
    },
  );

  function takeOver() {
    'worklet';
    lastTouch.set(clock.get());
    resumeAsked.set(0);
    if (mode.get() !== 0) return;
    mode.set(1);
    finger.set(withTiming(0, { duration: 150 }));
    lean.set(withTiming(0, { duration: 300 }));
  }

  const screenTouch = Gesture.Manual().onTouchesDown(() => {
    takeOver();
  });

  const ruler = Gesture.Pan()
    .minDistance(2)
    .onBegin(() => {
      takeOver();
      year.set(year.get()); // catch a glide where it is
      gliding.set(0);
    })
    .onStart(() => {
      dragging.set(1);
      y0.set(year.get());
    })
    .onUpdate((e) => {
      lastTouch.set(clock.get());
      let v = y0.get() - e.translationX / STEP;
      if (v < FIRST) v = FIRST - 0.6 * (1 - Math.exp((v - FIRST) / 0.6));
      if (v > LAST) v = LAST + 0.6 * (1 - Math.exp(-(v - LAST) / 0.6));
      year.set(v);
      if (!reduced) lean.set(withSpring(Math.max(-1, Math.min(1, -e.velocityX / STEP / 30)), { duration: 300, dampingRatio: 0.8 }));
    })
    .onFinalize((e) => {
      lastTouch.set(clock.get());
      lean.set(withSpring(0, { duration: 500, dampingRatio: 0.7 }));
      if (dragging.get() === 0) return;
      dragging.set(0);
      const v = -e.velocityX / STEP;
      const target = Math.max(FIRST, Math.min(LAST, Math.round(year.get() + 0.3 * v)));
      glideTo(target, v);
    });
  const tapYear = Gesture.Tap().onEnd((e) => {
    const target = Math.max(FIRST, Math.min(LAST, Math.round(year.get() + (e.x - L.w / 2) / STEP)));
    glideTo(target, 0);
  });
  const rulerGesture = Gesture.Exclusive(ruler, tapYear);

  const step = useCallback(
    (dir: 1 | -1) => {
      takeOver();
      glideTo(Math.max(FIRST, Math.min(LAST, Math.round(year.get()) + dir)), 0);
    },
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [],
  );

  /* ------------------------------------------------------------ drawing */
  const uniforms = useDerivedValue(() => {
    const k = lean.get();
    return {
      uObj: [L.iconX, L.iconY, ICON_HALF, pixelRatio],
      uEra: [eraOf(year.get()), 0.14 + 0.35 * k, 0.1 + 0.05 * Math.abs(k), time.get()],
      uPage: [L.w, L.h, 1, 0],
    };
  });

  const yearProps = useAnimatedProps(() => {
    const y = String(Math.round(Math.max(FIRST, Math.min(LAST, year.get()))));
    return { text: y, defaultValue: y } as never;
  });
  const rulerStyle = useAnimatedStyle(() => ({ transform: [{ translateX: L.w / 2 - (year.get() - FIRST) * STEP }] }));
  const fingerStyle = useAnimatedStyle(() => ({
    opacity: 0.85 * finger.get(),
    transform: [{ translateX: L.w * 0.68 - fingerX.get() * STEP - 22 }, { translateY: L.rulerTop + 30 - 22 }],
  }));

  const onRoot = useCallback((e: LayoutChangeEvent) => {
    const { width, height } = e.nativeEvent.layout;
    setSize((s) => (s.w === width && s.h === height ? s : { w: width, h: height }));
  }, []);

  if (!fontsLoaded) return <View style={styles.root} onLayout={onRoot} />;

  const eraNow = Math.min(3, Math.round(eraOf(shown)));
  return (
    <GestureDetector gesture={screenTouch}>
      <View style={styles.root} onLayout={onRoot}>
        <StatusBar style="light" />
        {active ? (
          <Canvas style={StyleSheet.absoluteFill} pointerEvents="none">
            <Fill>
              <Shader source={effect!} uniforms={uniforms} />
            </Fill>
          </Canvas>
        ) : null}

        <View style={[styles.head, { top: L.titleTop }]}>
          <Text style={styles.sub}>{APP.sub}</Text>
          <Text style={styles.title} accessibilityRole="header">{APP.name}</Text>
        </View>

        {/* the year and the era */}
        <View style={[styles.info, { top: L.yearTop }]} pointerEvents="none">
          <AnimatedInput editable={false} style={styles.year} animatedProps={yearProps} allowFontScaling={false} accessible={false} />
          <View style={styles.eraBox}>
            {ERAS.map((e, i) => (
              <EraWords key={e.name} index={i} name={e.name} line={e.line} year={year} />
            ))}
          </View>
        </View>

        {/* the ruler */}
        <GestureDetector gesture={rulerGesture}>
          <View
            style={[styles.rulerArea, { top: L.rulerTop - 24 }]}
            accessible
            accessibilityRole="adjustable"
            accessibilityLabel={`${APP.name} icon by year`}
            accessibilityValue={{ text: `${shown}, ${ERAS[eraNow].name}` }}
            accessibilityActions={[{ name: 'increment' }, { name: 'decrement' }]}
            onAccessibilityAction={(e) => step(e.nativeEvent.actionName === 'increment' ? 1 : -1)}
          >
            <Animated.View style={[styles.ruler, rulerStyle]}>
              {GHOST_AT.map((i) => {
                const away = i < 0 ? -i : i - (LAST - FIRST);
                return (
                  <View key={`g${i}`} style={[styles.tickBox, { left: i * STEP - STEP / 2, opacity: 0.55 * (1 - away / (GHOSTS + 1)) }]}>
                    <View style={styles.tick} />
                  </View>
                );
              })}
              {YEARS.map((y, i) => (
                <View key={y} style={[styles.tickBox, { left: i * STEP - STEP / 2 }]}>
                  <View style={[styles.tick, y % 2 === 0 ? styles.tickMajor : null]} />
                  {y % 2 === 0 ? <Text style={styles.tickText}>{`'${String(y).slice(2)}`}</Text> : null}
                </View>
              ))}
            </Animated.View>
            <View style={[styles.needle, { left: L.w / 2 - 1 }]} />
          </View>
        </GestureDetector>

        <Animated.View pointerEvents="none" style={[styles.finger, fingerStyle]} />
      </View>
    </GestureDetector>
  );
}

function EraWords({ index, name, line, year }: { index: number; name: string; line: string; year: { get: () => number } }) {
  const style = useAnimatedStyle(() => {
    const d = eraOf(year.get()) - index;
    // the nearer era's words show, swapping in a quick fade at the change's middle: they never sit on
    // each other, and the screen is never left without them while the ruler rests mid-change
    const a = Math.abs(d);
    const k = Math.max(0, Math.min(1, (0.53 - a) / 0.06));
    return { opacity: k * k, transform: [{ translateY: -14 * Math.max(-1, Math.min(1, d)) }] };
  });
  return (
    <Animated.View style={[styles.era, style]}>
      <Text style={styles.eraName}>{name}</Text>
      <Text style={styles.eraLine}>{line}</Text>
    </Animated.View>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: '#09090F' },
  head: { position: 'absolute', left: 0, right: 0, alignItems: 'center', gap: 2 },
  sub: { fontFamily: FONT.medium, fontSize: 13, lineHeight: 18, color: C.mute, letterSpacing: 0.4 },
  title: { fontFamily: FONT.semibold, fontSize: 20, lineHeight: 26, color: C.ink },
  info: { position: 'absolute', left: 0, right: 0, alignItems: 'center' },
  year: { fontFamily: FONT.semibold, fontSize: 56, lineHeight: 64, color: C.ink, padding: 0, textAlign: 'center', minWidth: 200, fontVariant: ['tabular-nums'] },
  eraBox: { height: 52, alignSelf: 'stretch', marginTop: 4 },
  era: { position: 'absolute', left: 0, right: 0, top: 0, alignItems: 'center', gap: 2 },
  eraName: { fontFamily: FONT.semibold, fontSize: 17, lineHeight: 24, color: C.ink },
  eraLine: { fontFamily: FONT.regular, fontSize: 14, lineHeight: 20, color: C.mute },
  rulerArea: { position: 'absolute', left: 0, right: 0, height: 96 },
  ruler: { position: 'absolute', left: 0, top: 24, height: 56 },
  tickBox: { position: 'absolute', top: 0, width: STEP, alignItems: 'center' },
  tick: { width: 1.5, height: 14, borderRadius: 1, backgroundColor: C.tick },
  tickMajor: { height: 24, backgroundColor: C.mute },
  tickText: { marginTop: 8, fontFamily: FONT.medium, fontSize: 12, color: C.mute, fontVariant: ['tabular-nums'] },
  needle: { position: 'absolute', top: 16, width: 2, height: 40, borderRadius: 1, backgroundColor: C.accent },
  finger: { position: 'absolute', left: 0, top: 0, width: 44, height: 44, borderRadius: 22, backgroundColor: 'rgba(255,255,255,0.35)', borderWidth: 1.5, borderColor: 'rgba(255,255,255,0.8)' },
});
