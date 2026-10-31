import { BlurMask, Canvas, Circle, Group, Path, Rect, Shader, Skia } from '@shopify/react-native-skia';
import { useFonts } from 'expo-font';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { AppState, type LayoutChangeEvent, PixelRatio, StyleSheet, Text, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  cubicBezier,
  Easing,
  interpolateColor,
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
import { scheduleOnRN } from 'react-native-worklets';

import { PressScale } from '../../../shared/PressScale';
import { useReducedMotionPreference } from '../../../shared/useReducedMotionPreference';
import { flickOffset, frameAt, glints, GLINTS, IDLE_SPIN, LOOP, PITCH } from './gcp-motion';
import { GCP_SKSL } from './gcp-shader';
import { COLOR, CTA_H, FEATURES, FONT, makeLayout, PLAN_H, ROW, SPACE, TIERS } from './gcp-theme';
import { type GcpCue, useGcpAudio } from './use-gcp-audio';

/*
 Gem Cut Paywall — the plans of an invented photo editor, "Facet".

 The screen: close and Restore on top; a gem large in the middle of a violet-black page; the plan's
 name and price; a Free / Plus / Pro selector; what the plan unlocks; the call to action.

 Signature: the selector cuts the gem. Free is a frosted pebble, Plus a single cut, Pro a round
 brilliant, and the thumb's position is the cut itself, so dragging it grinds facets into the stone
 (table first, then the mains and the girdle, then the small facets) and dragging back fills them
 in. The gem is ray-traced exactly in one Skia shader (an ellipsoid intersected with 73 facet planes,
 internal reflections, a refraction per colour for the fire, the caustic it throws on the floor), and
 its glints are computed from where the bezels really mirror the studio's lights. Spin it with a
 finger; it coasts back to its slow turn.

 autoPlay: LOOP = 10 s (drag Free → Pro, a flick of the gem, tap Plus, tap Free); frame 10 s equals
 frame 0. A touch takes over where the loop is; 2 s after the last one the loop eases back.
 Reduced motion: no loop and no spin; the cut changes in 150 ms, no flare.
*/

const effect = Skia.RuntimeEffect.Make(GCP_SKSL);
if (!effect) throw new Error('Gem Cut Paywall: the gem shader failed to compile.');
const pixelRatio = PixelRatio.get();
const EASE_IN_OUT = Easing.bezier(0.77, 0, 0.175, 1);
const EASE_OUT = Easing.bezier(0.23, 1, 0.32, 1);
const CSS_EASE_OUT = cubicBezier(0.23, 1, 0.32, 1);
const NEVER = ReduceMotion.Never;
const LAST = TIERS.length - 1;

// a four-point star, long thin arms, for the glints (unit size)
const STAR = (() => {
  const p = Skia.Path.Make();
  const arm = 1;
  const waist = 0.09;
  p.moveTo(0, -arm);
  p.quadTo(waist, -waist, arm, 0);
  p.quadTo(waist, waist, 0, arm);
  p.quadTo(-waist, waist, -arm, 0);
  p.quadTo(-waist, -waist, 0, -arm);
  p.close();
  return p;
})();

function buzz(kind: 'select' | 'light' | 'success') {
  if (process.env.EXPO_OS === 'web') return;
  if (kind === 'select') Haptics.selectionAsync().catch(() => {});
  else if (kind === 'success') Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success).catch(() => {});
  else Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
}

type Props = { autoPlay?: boolean };

export function GemCutPaywall({ autoPlay = false }: Props) {
  const insets = useSafeAreaInsets();
  const reduced = useReducedMotionPreference();
  const auto = autoPlay && !reduced;
  const [size, setSize] = useState<{ w: number; h: number } | null>(null);
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
  const w = size?.w ?? 402;
  const h = size?.h ?? 874;
  const L = useMemo(() => makeLayout(w, h, insets.top, insets.bottom), [w, h, insets.top, insets.bottom]);
  const audio = useGcpAudio(!reduced);
  const cue = useCallback((c: GcpCue) => audio.play(c), [audio]);

  // the selector's geometry
  const segW = (L.planW - 8) / TIERS.length;
  const thumbRun = L.planW - 8 - segW; // thumb travel, cut 0 → 1

  /* ------------------------------------------------------------ state */
  const clock = useSharedValue(0);
  const mode = useSharedValue(auto ? 0 : 1); // 0 the loop, 1 the finger
  const autoStart = useSharedValue(0);
  const lastTouch = useSharedValue(-100);
  const resumeAsked = useSharedValue(0);
  const pin = useSharedValue(-1);

  const cut = useSharedValue(0);
  const yaw = useSharedValue(0);
  const yawBase = useSharedValue(0);
  const yawVel = useSharedValue(IDLE_SPIN);
  const pitch = useSharedValue(PITCH);
  const spark = useSharedValue(0);
  const finger = useSharedValue(0);
  const fingerX = useSharedValue(0);
  const fingerY = useSharedValue(0);
  const fingerPress = useSharedValue(0);
  const spinning = useSharedValue(0); // a finger on the gem
  const sliding = useSharedValue(0); // a finger dragging the thumb
  const slideFrom = useSharedValue(0);
  const prevX = useSharedValue(0);
  const prevY = useSharedValue(0);
  const [tier, setTier] = useState(0);

  /* ------------------------------------------------------------ clock */
  useFrameCallback((f) => {
    const dt = Math.min(0.05, (f.timeSincePreviousFrame ?? 16) / 1000);
    clock.set(clock.get() + dt);
    if (mode.get() === 0) {
      const t = pin.get() >= 0 ? pin.get() : (((clock.get() - autoStart.get()) % LOOP) + LOOP) % LOOP;
      const s = frameAt(t);
      cut.set(s.cut);
      yaw.set(yawBase.get() + s.yaw);
      pitch.set(s.pitch);
      spark.set(s.spark);
      finger.set(s.finger);
      fingerPress.set(s.press);
      if (s.fingerOn === 0) {
        fingerX.set(L.planX + 4 + segW / 2 + s.cut * thumbRun);
        fingerY.set(L.planTop + PLAN_H / 2);
      } else if (s.fingerOn === 1) {
        fingerX.set(L.gemX + flickOffset(t) * L.gemR);
        fingerY.set(L.gemY + L.gemR * 0.05);
      } else {
        const i = s.fingerOn === 2 ? 1 : 0;
        fingerX.set(L.planX + 4 + segW * (i + 0.5));
        fingerY.set(L.planTop + PLAN_H / 2);
      }
      return;
    }
    // the finger's turn: the gem coasts back to its slow idle turn
    if (spinning.get() === 0) {
      const v = yawVel.get();
      const target = reduced ? 0 : IDLE_SPIN;
      yawVel.set(target + (v - target) * Math.exp(-1.4 * dt));
      yaw.set(yaw.get() + yawVel.get() * dt);
    }
    if (auto && resumeAsked.get() === 0 && spinning.get() === 0 && sliding.get() === 0 && clock.get() - lastTouch.get() > 2) {
      resumeAsked.set(1);
      cut.set(withTiming(0, { duration: 500 + 700 * cut.get(), easing: EASE_IN_OUT }));
      pitch.set(withSpring(PITCH, { duration: 600, dampingRatio: 0.9 }));
    } else if (resumeAsked.get() === 1 && cut.get() < 0.002 && Math.abs(yawVel.get() - IDLE_SPIN) < 0.02) {
      resumeAsked.set(0);
      yawBase.set(yaw.get());
      autoStart.set(clock.get());
      mode.set(0);
    }
  }, active);

  useEffect(() => {
    mode.set(auto ? 0 : 1);
    autoStart.set(clock.get());
    yawBase.set(yaw.get());
  }, [auto, mode, autoStart, clock, yawBase, yaw]);

  useEffect(() => {
    if (!__DEV__) return;
    (globalThis as Record<string, unknown>).__gem = {
      at: (t: number) => pin.set(t),
      play: () => pin.set(-1),
      state: () => JSON.stringify({ mode: mode.get(), cut: +cut.get().toFixed(3), yaw: +yaw.get().toFixed(3), spark: +spark.get().toFixed(3) }),
    };
  }, [pin, mode, cut, yaw, spark]);

  /* ------------------------------------------------------------ plans */
  const onCross = useCallback(() => {
    cue('tick');
    buzz('select');
  }, [cue]);
  const onLand = useCallback((i: number) => {
    if (i === LAST) {
      cue('shimmer');
      buzz('success');
    } else {
      cue('ting');
      buzz('light');
    }
  }, [cue]);
  useAnimatedReaction(
    () => Math.round(cut.get() * LAST),
    (now, before) => {
      if (before === null || now === before) return;
      scheduleOnRN(setTier, now);
      if (mode.get() === 1 && sliding.get() === 1) scheduleOnRN(onCross);
    },
  );

  const flare = (i: number) => {
    'worklet';
    if (reduced) return;
    const amp = [0.2, 0.45, 1][i];
    spark.set(withSequence(withTiming(amp, { duration: 120, easing: EASE_OUT }), withTiming(0, { duration: 1300, easing: EASE_OUT })));
  };

  const takeOver = () => {
    'worklet';
    lastTouch.set(clock.get());
    resumeAsked.set(0);
    if (mode.get() === 0) {
      mode.set(1);
      // the loop's turn carries on from its current speed
      yawVel.set(IDLE_SPIN);
      finger.set(withTiming(0, { duration: 150 }));
      fingerPress.set(0);
    }
  };

  const goToTier = (i: number, velocity: number, glide: boolean) => {
    'worklet';
    const target = i / LAST;
    const done = (finished?: boolean) => {
      'worklet';
      if (!finished) return;
      flare(i);
      scheduleOnRN(onLand, i);
    };
    if (reduced) {
      cut.set(withTiming(target, { duration: 150, reduceMotion: NEVER }, done));
    } else if (glide) {
      const span = Math.abs(target - cut.get());
      cut.set(withTiming(target, { duration: 360 + 680 * span, easing: EASE_IN_OUT }, done));
    } else {
      cut.set(withSpring(target, { duration: 420, dampingRatio: 0.8, velocity }, done));
    }
  };

  // the selector: drag the thumb 1:1 (the gem cuts as it moves), or tap a plan
  const plan = Gesture.Pan()
    .minDistance(0)
    .onBegin(() => {
      takeOver();
      // catch the thumb where it is (stops a glide or a spring mid-way)
      cut.set(cut.get());
      slideFrom.set(cut.get());
    })
    .onUpdate((e) => {
      lastTouch.set(clock.get());
      if (sliding.get() === 0 && Math.abs(e.translationX) > 6) sliding.set(1);
      if (sliding.get() === 1) {
        const raw = slideFrom.get() + e.translationX / thumbRun;
        // rubber band past the ends
        const over = raw < 0 ? raw : raw > 1 ? raw - 1 : 0;
        cut.set(raw - over + over * 0.18);
      }
    })
    .onFinalize((e) => {
      lastTouch.set(clock.get());
      if (sliding.get() === 1) {
        const v = e.velocityX / thumbRun;
        const projected = cut.get() + v * 0.18;
        const i = Math.max(0, Math.min(LAST, Math.round(projected * LAST)));
        goToTier(i, v, false);
      } else {
        const i = Math.max(0, Math.min(LAST, Math.floor((e.x - 4) / segW)));
        goToTier(i, 0, true);
      }
      sliding.set(0);
    });

  // the gem: spin it, tap it
  const gem = Gesture.Pan()
    .minDistance(0)
    .onBegin(() => {
      takeOver();
      spinning.set(1);
      yawVel.set(0);
      pitch.set(pitch.get());
      prevX.set(0);
      prevY.set(0);
    })
    .onUpdate((e) => {
      lastTouch.set(clock.get());
      const dx = e.translationX - prevX.get();
      const dy = e.translationY - prevY.get();
      prevX.set(e.translationX);
      prevY.set(e.translationY);
      if (reduced) return;
      yaw.set(yaw.get() + dx / (L.gemR * 0.9));
      pitch.set(Math.max(0.12, Math.min(0.95, pitch.get() + dy / (L.gemR * 2.4))));
    })
    .onFinalize((e) => {
      lastTouch.set(clock.get());
      spinning.set(0);
      const moved = Math.hypot(e.translationX, e.translationY);
      yawVel.set(reduced ? 0 : Math.max(-9, Math.min(9, e.velocityX / (L.gemR * 0.9))));
      if (!reduced) pitch.set(withSpring(PITCH, { duration: 700, dampingRatio: 0.7, velocity: e.velocityY / (L.gemR * 2.4) }));
      if (moved < 8) {
        if (!reduced) spark.set(withSequence(withTiming(0.6, { duration: 100, easing: EASE_OUT }), withTiming(0, { duration: 1000, easing: EASE_OUT })));
        scheduleOnRN(cue, 'ting');
        scheduleOnRN(buzz, 'light');
      }
    });

  const step = useCallback((d: number) => {
    takeOver();
    const i = Math.max(0, Math.min(LAST, Math.round(cut.get() * LAST) + d));
    goToTier(i, 0, true);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [reduced, segW, thumbRun]);

  /* ------------------------------------------------------------ drawing */
  const uniforms = useDerivedValue(() => ({
    uC: [L.gemX, L.gemY],
    uS: L.gemR,
    uCut: cut.get(),
    uYaw: yaw.get(),
    uPitch: pitch.get(),
    uSpark: spark.get(),
    uFloor: [L.gemX, L.floorY],
    uPixelRatio: pixelRatio,
  }));
  const glintList = useDerivedValue(() => glints(yaw.get(), pitch.get(), cut.get(), spark.get()));
  const fingerR = useDerivedValue(() => 22 * (1 - 0.12 * fingerPress.get()));
  const fingerFill = useDerivedValue(() => `rgba(255,255,255,${0.16 * finger.get()})`);
  const fingerRing = useDerivedValue(() => `rgba(255,255,255,${0.55 * finger.get()})`);

  const thumbStyle = useAnimatedStyle(() => ({ transform: [{ translateX: cut.get() * thumbRun }] }));

  const onLayout = (e: LayoutChangeEvent) => {
    const { width, height } = e.nativeEvent.layout;
    if (!size || size.w !== width || size.h !== height) setSize({ w: width, h: height });
  };

  const t = TIERS[tier];
  const stageH = L.titleTop;

  return (
    <View style={styles.screen} onLayout={onLayout}>
      <StatusBar style="light" />
      {/* the gem, its halo and its caustic */}
      <Canvas style={StyleSheet.absoluteFill} pointerEvents="none">
        <Rect x={0} y={0} width={w} height={h}>
          <Shader source={effect!} uniforms={uniforms} />
        </Rect>
        {Array.from({ length: GLINTS }, (_, i) => (
          <Glint key={i} index={i} list={glintList} cx={L.gemX} cy={L.gemY} r={L.gemR} />
        ))}
      </Canvas>
      <GestureDetector gesture={gem}>
        <View
          style={[styles.stage, { height: stageH }]}
          accessible
          accessibilityRole="image"
          accessibilityLabel={['A frosted rough stone', 'A gem with a single cut', 'A round brilliant diamond'][tier]}
          accessibilityHint="Drag to turn the gem"
        />
      </GestureDetector>

      {/* the bar */}
      <View style={[styles.bar, { top: L.barTop }]}>
        <PressScale style={styles.close} reduced={reduced} accessibilityRole="button" accessibilityLabel="Close" hitSlop={4}>
          <Svg width={20} height={20} viewBox="0 0 20 20">
            <SvgPath d="M5 5 L15 15 M15 5 L5 15" stroke={COLOR.ink} strokeWidth={2} strokeLinecap="round" />
          </Svg>
        </PressScale>
        <PressScale style={styles.restore} reduced={reduced} accessibilityRole="button" accessibilityLabel="Restore purchases">
          <Text style={styles.restoreText} allowFontScaling={false}>Restore</Text>
        </PressScale>
      </View>

      {fontsLoaded && (
        <>
          {/* plan name and price */}
          <View style={[styles.centerRow, { top: L.titleTop, height: 34 }]} accessibilityLiveRegion="polite">
            <Text style={styles.title} allowFontScaling={false}>
              Facet{' '}
            </Text>
            <View style={styles.swap}>
              {TIERS.map((p, i) => (
                <Animated.Text
                  key={p.id}
                  style={[styles.title, i === 0 ? null : StyleSheet.absoluteFill, fade(i === tier)]}
                  allowFontScaling={false}
                  accessibilityElementsHidden={i !== tier}
                  importantForAccessibility={i === tier ? 'auto' : 'no-hide-descendants'}
                >
                  {p.name}
                </Animated.Text>
              ))}
            </View>
          </View>
          <View style={[styles.centerRow, { top: L.priceTop, height: 22 }]} accessible accessibilityLabel={`$${t.price} a month`}>
            <Text style={styles.price} allowFontScaling={false}>$</Text>
            <Odometer value={t.price} reduced={reduced} />
            <Text style={[styles.price, styles.per]} allowFontScaling={false}> / month</Text>
          </View>

          {/* the selector */}
          <GestureDetector gesture={plan}>
            <View
              style={[styles.plan, { top: L.planTop, left: L.planX, width: L.planW }]}
              accessible
              accessibilityRole="adjustable"
              accessibilityLabel="Plan"
              accessibilityValue={{ text: `${t.name}, $${t.price} a month` }}
              accessibilityActions={[{ name: 'increment' }, { name: 'decrement' }]}
              onAccessibilityAction={(e) => step(e.nativeEvent.actionName === 'increment' ? 1 : -1)}
            >
              <Animated.View style={[styles.thumb, { width: segW }, thumbStyle]} />
              {TIERS.map((p, i) => (
                <SegmentLabel key={p.id} index={i} label={p.name} cut={cut} width={segW} />
              ))}
            </View>
          </GestureDetector>

          {/* what the plan unlocks */}
          <View style={[styles.features, { top: L.featTop }]}>
            {FEATURES.map((f) => (
              <Feature key={f.label} label={f.label} from={f.from} cut={cut} on={tier >= f.from} />
            ))}
          </View>

          {/* the fine print and the call to action */}
          <View style={[styles.note, { top: L.noteTop }]}>
            {TIERS.map((p, i) => (
              <Animated.Text
                key={p.id}
                style={[styles.noteText, i === 0 ? null : StyleSheet.absoluteFill, fade(i === tier)]}
                allowFontScaling={false}
                accessibilityElementsHidden={i !== tier}
                importantForAccessibility={i === tier ? 'auto' : 'no-hide-descendants'}
              >
                {p.note}
              </Animated.Text>
            ))}
          </View>
          <PressScale
            style={[styles.cta, { top: L.ctaTop }]}
            reduced={reduced}
            accessibilityRole="button"
            accessibilityLabel={t.cta}
          >
            <Animated.Text style={[styles.ctaText, fade(tier === 0)]} allowFontScaling={false}>
              {TIERS[0].cta}
            </Animated.Text>
            <Animated.Text style={[styles.ctaText, StyleSheet.absoluteFill, styles.ctaCentre, fade(tier !== 0)]} allowFontScaling={false}>
              {TIERS[1].cta}
            </Animated.Text>
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

function fade(on: boolean) {
  return { opacity: on ? 1 : 0, transitionProperty: 'opacity', transitionDuration: 200, transitionTimingFunction: CSS_EASE_OUT } as const;
}

/* ------------------------------------------------------------ parts */

type SV = ReturnType<typeof useSharedValue<number>>;

function Glint({ index, list, cx, cy, r }: { index: number; list: { value: number[] }; cx: number; cy: number; r: number }) {
  const transform = useDerivedValue(() => {
    const l = list.value;
    const a = l[index * 3 + 2];
    const s = 4 + 22 * a;
    return [{ translateX: cx + l[index * 3] * r }, { translateY: cy + l[index * 3 + 1] * r }, { scale: s }, { rotate: 0.2 }];
  });
  const opacity = useDerivedValue(() => list.value[index * 3 + 2]);
  return (
    <Group transform={transform} opacity={opacity} blendMode="plus">
      <Path path={STAR} color="#FFFFFF">
        <BlurMask blur={0.04} style="normal" />
      </Path>
      <Circle cx={0} cy={0} r={0.16} color="rgba(220,215,255,0.9)">
        <BlurMask blur={0.12} style="normal" />
      </Circle>
    </Group>
  );
}

function SegmentLabel({ index, label, cut, width }: { index: number; label: string; cut: SV; width: number }) {
  const style = useAnimatedStyle(() => {
    // how much of the thumb is under this label
    const on = Math.max(0, 1 - Math.abs(cut.get() * LAST - index));
    return { color: interpolateColor(on, [0, 1], [COLOR.mute, COLOR.thumbInk]) };
  });
  return (
    <View style={[styles.segment, { left: 4 + index * width, width }]} pointerEvents="none">
      <Animated.Text style={[styles.segmentText, style]} allowFontScaling={false}>
        {label}
      </Animated.Text>
    </View>
  );
}

function Feature({ label, from, cut, on }: { label: string; from: number; cut: SV; on: boolean }) {
  // lights up while the thumb approaches the plan that unlocks it
  const lit = useAnimatedStyle(() => {
    const k = Math.max(0, Math.min(1, (cut.get() * LAST - (from - 0.6)) / 0.6));
    return { color: interpolateColor(k, [0, 1], [COLOR.mute, COLOR.ink]) };
  });
  const check = useAnimatedStyle(() => ({ opacity: Math.max(0, Math.min(1, (cut.get() * LAST - (from - 0.6)) / 0.6)) }));
  const lock = useAnimatedStyle(() => ({ opacity: 1 - Math.max(0, Math.min(1, (cut.get() * LAST - (from - 0.6)) / 0.6)) }));
  return (
    <View style={styles.row} accessible accessibilityLabel={`${label}, ${on ? 'included' : 'not included'}`}>
      <View style={styles.icon}>
        <Animated.View style={[StyleSheet.absoluteFill, check]}>
          <Svg width={20} height={20} viewBox="0 0 20 20">
            <SvgPath d="M4.5 10.5 L8.5 14.5 L15.5 6" stroke={COLOR.ink} strokeWidth={2} fill="none" strokeLinecap="round" strokeLinejoin="round" />
          </Svg>
        </Animated.View>
        <Animated.View style={[StyleSheet.absoluteFill, lock]}>
          <Svg width={20} height={20} viewBox="0 0 20 20">
            <SvgPath d="M6.5 9 V7 a3.5 3.5 0 0 1 7 0 V9" stroke={COLOR.mute} strokeWidth={1.6} fill="none" />
            <SvgPath d="M5 9 h10 a1 1 0 0 1 1 1 v6 a1 1 0 0 1 -1 1 h-10 a1 1 0 0 1 -1 -1 v-6 a1 1 0 0 1 1 -1 z" fill={COLOR.mute} />
          </Svg>
        </Animated.View>
      </View>
      <Animated.Text style={[styles.featureText, lit]} allowFontScaling={false}>
        {label}
      </Animated.Text>
    </View>
  );
}

const DIGIT_H = 22;
function Odometer({ value, reduced }: { value: string; reduced: boolean }) {
  return (
    <View style={styles.odo}>
      {value.split('').map((ch, i) =>
        ch === '.' ? (
          <Text key={i} style={styles.price} allowFontScaling={false}>
            .
          </Text>
        ) : (
          <View key={i} style={styles.digitWindow}>
            <Animated.View
              style={{
                transform: [{ translateY: -Number(ch) * DIGIT_H }],
                transitionProperty: 'transform',
                transitionDuration: reduced ? 0 : 320 + i * 40,
                transitionTimingFunction: CSS_EASE_OUT,
              }}
            >
              {Array.from({ length: 10 }, (_, d) => (
                <Text key={d} style={[styles.price, styles.digit]} allowFontScaling={false}>
                  {d}
                </Text>
              ))}
            </Animated.View>
          </View>
        ),
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: COLOR.page, overflow: 'hidden' },
  stage: { position: 'absolute', left: 0, right: 0, top: 0 },
  bar: { position: 'absolute', left: SPACE.s, right: SPACE.l, height: 44, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  close: { width: 44, height: 44, alignItems: 'center', justifyContent: 'center' },
  restore: { height: 44, justifyContent: 'center', paddingHorizontal: SPACE.xs },
  restoreText: { fontFamily: FONT.medium, fontSize: 15, color: COLOR.mute },
  centerRow: { position: 'absolute', left: 0, right: 0, flexDirection: 'row', justifyContent: 'center', alignItems: 'center' },
  title: { fontFamily: FONT.semibold, fontSize: 28, lineHeight: 34, color: COLOR.ink, letterSpacing: -0.4 },
  swap: { justifyContent: 'center' },
  price: { fontFamily: FONT.medium, fontSize: 17, lineHeight: DIGIT_H, color: COLOR.ink, fontVariant: ['tabular-nums'] },
  per: { color: COLOR.mute },
  odo: { flexDirection: 'row', height: DIGIT_H },
  digitWindow: { height: DIGIT_H, overflow: 'hidden' },
  digit: { height: DIGIT_H },
  plan: { position: 'absolute', height: PLAN_H, borderRadius: PLAN_H / 2, backgroundColor: COLOR.surface, borderWidth: StyleSheet.hairlineWidth, borderColor: COLOR.line },
  thumb: { position: 'absolute', left: 4 - StyleSheet.hairlineWidth, top: 4 - StyleSheet.hairlineWidth, height: PLAN_H - 8, borderRadius: (PLAN_H - 8) / 2, backgroundColor: COLOR.thumb },
  segment: { position: 'absolute', top: 0, height: PLAN_H, alignItems: 'center', justifyContent: 'center' },
  segmentText: { fontFamily: FONT.semibold, fontSize: 15 },
  features: { position: 'absolute', left: SPACE.xxl, right: SPACE.xxl, gap: SPACE.m },
  row: { height: ROW, flexDirection: 'row', alignItems: 'center', gap: SPACE.m },
  icon: { width: 20, height: 20 },
  featureText: { fontFamily: FONT.regular, fontSize: 15 },
  note: { position: 'absolute', left: SPACE.l, right: SPACE.l, height: 18, alignItems: 'center' },
  noteText: { fontFamily: FONT.regular, fontSize: 13, lineHeight: 18, color: COLOR.mute, textAlign: 'center' },
  cta: { position: 'absolute', left: SPACE.l, right: SPACE.l, height: CTA_H, borderRadius: CTA_H / 2, backgroundColor: COLOR.cta, alignItems: 'center', justifyContent: 'center' },
  ctaText: { fontFamily: FONT.semibold, fontSize: 17, color: COLOR.ctaInk },
  ctaCentre: { textAlign: 'center', lineHeight: CTA_H },
});
