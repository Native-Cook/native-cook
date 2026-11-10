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
  withTiming,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';

import { PressScale } from '../../../shared/PressScale';
import { useReducedMotionPreference } from '../../../shared/useReducedMotionPreference';
import { clinkLeanAt, frameAt, LOOP, REST_LEAN } from './crs-motion';
import { CRS_SKSL } from './crs-shader';
import { clinkLean, COLOR, COPY, CTA_H, FEET_GAP, FONT, LINK_H, makeLayout, SPACE } from './crs-theme';
import { makeUniforms } from './crs-uniforms';

/*
 Champagne RSVP — the reply card of an invented wedding invitation, Clara & Theo.

 The screen: the venue, the couple's names in a serif, the date; two flutes of champagne; a line;
 Joyfully Accept and Regretfully Decline.

 Signature: accepting is a toast. The two flutes swing together and clink — a ring of light where
 the rims touch, a firm haptic — and bubbles burst up out of both glasses before they are drawn back
 apart; the card answers "See you there". Drag one flute towards the other to clink them yourself;
 the bubbles never stop rising in the glasses.

 autoPlay: LOOP = 10 s (accept, clink, the burst, the reply shown, back to the question); frame 10 s
 equals frame 0. A touch takes over; 2 s after the last one the card asks again and the loop
 restarts. Reduced motion: no loop and no swing; accepting shows the reply with a short glow.
*/

const effect = Skia.RuntimeEffect.Make(CRS_SKSL);
if (!effect) throw new Error('Champagne RSVP: the shader failed to compile.');
const pixelRatio = PixelRatio.get();
const CSS_EASE_OUT = cubicBezier(0.23, 1, 0.32, 1);
const NEVER = ReduceMotion.Never;
const MEET = clinkLean(FEET_GAP);

function buzz(kind: 'heavy' | 'light' | 'success') {
  if (process.env.EXPO_OS === 'web') return;
  if (kind === 'success') Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success).catch(() => {});
  else if (kind === 'heavy') Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Rigid).catch(() => {});
  else Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
}

type Props = { autoPlay?: boolean };

export function ChampagneRsvp({ autoPlay = false }: Props) {
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

  const lean = useSharedValue(-REST_LEAN);
  const clink = useSharedValue(0);
  const burst = useSharedValue(0);
  const phase = useSharedValue(0);
  const clinkedAt = useSharedValue(-1);
  const loopDone = useSharedValue(0);
  const dragging = useSharedValue(0);
  const leanFrom = useSharedValue(-REST_LEAN);
  const finger = useSharedValue(0);
  const fingerPress = useSharedValue(0);
  const [done, setDone] = useState(false);

  /* ------------------------------------------------------------ the toast */
  const toast = () => {
    'worklet';
    clinkedAt.set(clock.get());
    clink.set(0.001);
    clink.set(withTiming(1, { duration: 900, easing: Easing.linear, reduceMotion: NEVER }, (f) => {
      if (f) clink.set(0);
    }));
    if (!reduced) {
      burst.set(0.001);
      burst.set(withTiming(1, { duration: 1400, easing: Easing.linear }, (f) => {
        if (f) burst.set(0);
      }));
    }
    scheduleOnRN(buzz, 'heavy');
    scheduleOnRN(setDone, true);
  };
  const accept = () => {
    'worklet';
    if (reduced) {
      toast();
      return;
    }
    // swing together (ease-in), the clink fires at the touch
    lean.set(withTiming(MEET, { duration: 480, easing: Easing.in(Easing.quad) }, (f) => {
      if (f) toast();
    }));
  };

  const takeOver = () => {
    'worklet';
    lastTouch.set(clock.get());
    resumeAsked.set(0);
    if (mode.get() === 0) {
      mode.set(1);
      finger.set(withTiming(0, { duration: 150 }));
      fingerPress.set(0);
      // a swing back the loop was in carries on under the finger's clock
      if (lean.get() > -REST_LEAN + 0.002) clinkedAt.set(clock.get() - 0.3);
    }
  };

  /* ------------------------------------------------------------ clock */
  const ticker = useFrameCallback((f) => {
    const dt = Math.min(0.05, (f.timeSincePreviousFrame ?? 16) / 1000);
    clock.set(clock.get() + dt);
    if (mode.get() === 0) {
      const t = pin.get() >= 0 ? pin.get() : (((clock.get() - autoStart.get()) % LOOP) + LOOP) % LOOP;
      const s = frameAt(t);
      lean.set(s.lean);
      clink.set(s.clink);
      burst.set(s.burst);
      phase.set(s.phase);
      loopDone.set(s.done);
      finger.set(s.finger);
      fingerPress.set(s.fingerPress);
      return;
    }
    if (!reduced) phase.set(phase.get() + (6 / LOOP) * dt);
    // after a clink the glasses are drawn back apart
    if (clinkedAt.get() >= 0 && dragging.get() === 0) {
      const tau = clock.get() - clinkedAt.get();
      lean.set(clinkLeanAt(tau));
      if (tau > 1.2) clinkedAt.set(-1);
    }
    if (!auto || dragging.get() === 1 || clinkedAt.get() >= 0 || clock.get() - lastTouch.get() < 2) return;
    if (resumeAsked.get() === 0) {
      resumeAsked.set(1);
      scheduleOnRN(setDone, false);
      lean.set(withTiming(-REST_LEAN, { duration: 400 }));
      return;
    }
    if (Math.abs(lean.get() + REST_LEAN) < 0.001 && clink.get() === 0 && burst.get() === 0) {
      resumeAsked.set(0);
      // the loop's bubbles run on their own phase: start it from 0 with the loop
      phase.set(0);
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
      const cfg = { duration: 200, reduceMotion: NEVER };
      lean.set(withTiming(-REST_LEAN, cfg));
      clink.set(0);
      burst.set(0);
      finger.set(0);
      fingerPress.set(0);
    });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [auto]);

  useEffect(() => {
    if (!__DEV__) return;
    (globalThis as Record<string, unknown>).__champagne = {
      at: (t: number) => pin.set(t),
      play: () => pin.set(-1),
      state: () => JSON.stringify({ clock: +clock.get().toFixed(2), mode: mode.get(), lean: +lean.get().toFixed(3), clink: +clink.get().toFixed(3) }),
    };
  }, [pin, clock, mode, lean, clink]);

  // the loop shows the reply from the clink until it asks again
  useAnimatedReaction(
    () => (mode.get() === 0 ? loopDone.get() : -1),
    (now, before) => {
      if (now >= 0 && now !== before) scheduleOnRN(setDone, now === 1);
    },
  );

  /* ------------------------------------------------------------ gestures */
  // drag either glass towards the other: they lean together, and touching clinks
  const drag = Gesture.Pan()
    .minDistance(2)
    .onBegin(() => {
      takeOver();
    })
    .onStart(() => {
      if (reduced) return;
      dragging.set(1);
      clinkedAt.set(-1);
      leanFrom.set(lean.get());
    })
    .onUpdate((e) => {
      lastTouch.set(clock.get());
      if (dragging.get() === 0) return;
      // dragging towards the middle leans both glasses in; the left half drags right, the right half left
      const inward = e.x < L.w / 2 ? e.translationX : -e.translationX;
      const v = Math.max(-0.3, Math.min(MEET, leanFrom.get() + inward / (L.heroS * 2.6)));
      lean.set(v);
      if (v >= MEET - 0.0005 && clink.get() === 0) toast();
    })
    .onFinalize(() => {
      lastTouch.set(clock.get());
      if (dragging.get() === 0) return;
      dragging.set(0);
      // let go: the hands draw them back apart
      clinkedAt.set(clock.get() - 0.12);
    });

  /* ------------------------------------------------------------ drawing */
  const uniforms = useDerivedValue(() => makeUniforms(L, { lean: lean.get(), clink: clink.get(), burst: burst.get(), phase: phase.get() }, pixelRatio));
  const fingerR = useDerivedValue(() => 22 * (1 - 0.12 * fingerPress.get()));
  const fingerFill = useDerivedValue(() => `rgba(42,31,29,${0.1 * finger.get()})`);
  const fingerRing = useDerivedValue(() => `rgba(42,31,29,${0.32 * finger.get()})`);

  const onLayout = (e: LayoutChangeEvent) => {
    const { width, height } = e.nativeEvent.layout;
    if (!size || size.w !== width || size.h !== height) setSize({ w: width, h: height });
  };
  const copy = done ? COPY.done : COPY.idle;

  return (
    <View style={styles.screen} onLayout={onLayout}>
      <StatusBar style="dark" />
      <Canvas style={StyleSheet.absoluteFill} pointerEvents="none">
        <Rect x={0} y={0} width={w} height={h}>
          <Shader source={effect!} uniforms={uniforms} />
        </Rect>
      </Canvas>

      <GestureDetector gesture={drag}>
        <View
          style={[styles.stage, { left: L.w / 2 - L.heroS * 2.4, top: L.feetY - L.heroS * 2.9, width: L.heroS * 4.8, height: L.heroS * 3.0 }]}
          accessible
          accessibilityRole="image"
          accessibilityLabel="Two flutes of champagne"
          accessibilityHint="Drag them together to clink"
        />
      </GestureDetector>

      {fontsLoaded && (
        <>
          <Text style={[styles.venue, { top: L.venueTop }]} allowFontScaling={false}>
            {COPY.venue}
          </Text>
          <Text style={[styles.names, { top: L.namesTop }]} allowFontScaling={false} accessibilityRole="header">
            {COPY.names}
          </Text>
          <Text style={[styles.date, { top: L.dateTop }]} allowFontScaling={false}>
            {COPY.date}
          </Text>
          <View style={[styles.swapBox, { top: L.lineTop, height: 22 }]} accessibilityLiveRegion="polite">
            {[COPY.idle, COPY.done].map((c, i) => (
              <Animated.Text key={c.line} style={[styles.line, StyleSheet.absoluteFill, swap((i === 1) === done, reduced)]} allowFontScaling={false} accessibilityElementsHidden={(i === 1) !== done} importantForAccessibility={(i === 1) === done ? 'auto' : 'no-hide-descendants'}>
                {c.line}
              </Animated.Text>
            ))}
          </View>
          <PressScale
            style={[styles.cta, { top: L.ctaTop }]}
            reduced={reduced}
            onPress={() => {
              if (done) {
                buzz('success');
                return;
              }
              scheduleOnUI(() => {
                'worklet';
                takeOver();
                accept();
              });
            }}
            accessibilityRole="button"
            accessibilityLabel={copy.cta}
          >
            <Text style={styles.ctaText} allowFontScaling={false}>
              {copy.cta}
            </Text>
          </PressScale>
          <PressScale style={[styles.link, { top: L.linkTop }]} reduced={reduced} onPress={() => setDone(false)} accessibilityRole="button" accessibilityLabel={copy.link}>
            <Text style={styles.linkText} allowFontScaling={false}>
              {copy.link}
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
  venue: { position: 'absolute', left: 0, right: 0, textAlign: 'center', fontFamily: FONT.medium, fontSize: 12, lineHeight: 16, letterSpacing: 1.6, color: COLOR.gold },
  names: { position: 'absolute', left: 0, right: 0, textAlign: 'center', fontFamily: FONT.display, fontSize: 44, lineHeight: 48, color: COLOR.ink },
  date: { position: 'absolute', left: 0, right: 0, textAlign: 'center', fontFamily: FONT.regular, fontSize: 15, lineHeight: 22, color: COLOR.mute },
  swapBox: { position: 'absolute', left: SPACE.xl, right: SPACE.xl },
  line: { fontFamily: FONT.medium, fontSize: 15, lineHeight: 22, color: COLOR.ink, textAlign: 'center' },
  cta: { position: 'absolute', left: SPACE.l, right: SPACE.l, height: CTA_H, borderRadius: CTA_H / 2, backgroundColor: COLOR.cta, alignItems: 'center', justifyContent: 'center' },
  ctaText: { fontFamily: FONT.semibold, fontSize: 17, color: COLOR.ctaInk },
  link: { position: 'absolute', left: SPACE.l, right: SPACE.l, height: LINK_H, alignItems: 'center', justifyContent: 'center' },
  linkText: { fontFamily: FONT.medium, fontSize: 15, color: COLOR.mute },
});
