import React, { useCallback, useEffect, useMemo } from 'react';
import { AccessibilityInfo, AppState, StyleSheet, Text, TextInput, View, useWindowDimensions } from 'react-native';
import { Canvas, Fill, ImageShader, Shader, Skia, useImage, type DataSource, type SkPath } from '@shopify/react-native-skia';
import { Gesture, GestureDetector, Pressable } from 'react-native-gesture-handler';
import Animated, {
  cancelAnimation,
  useAnimatedProps,
  useAnimatedReaction,
  useAnimatedStyle,
  useDerivedValue,
  useFrameCallback,
  useReducedMotion,
  useSharedValue,
  withSpring,
  withTiming,
  type SharedValue,
} from 'react-native-reanimated';
import { scheduleOnRN } from 'react-native-worklets';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { useFonts } from 'expo-font';
import { StatusBar } from 'expo-status-bar';
import Svg, { Path } from 'react-native-svg';
import { makeFieldGrid, type Preset } from './bvl-fields';
import { packReadout } from './bvl-glyphs';
import { cue } from './bvl-haptics';
import { gridImage, sampleMask } from './bvl-mask';
import {
  LOOP,
  SPRING,
  TOUCH,
  breatheAt,
  clamp,
  coastAt,
  elapsedAt,
  flowAt,
  heldAt,
  litreTimes,
  lastAirAt,
  progressAt,
  resultAt,
  rubberAt,
  smooth,
  springAt,
} from './bvl-motion';
import { BVL_SKSL } from './bvl-shaders';
import { COPY, card, makeLayout, ref, rgb, sys } from './bvl-theme';
import { heatAt } from './bvl-color';

export type DotMatrixDrainProps = {
  shape?: Preset | SkPath | DataSource;
  pile?: 'mound' | SkPath | DataSource;
  progress?: SharedValue<number>;
  defaultProgress?: number;
  mode?: 'hold' | 'scrub' | 'both' | 'display';
  capacity?: number;
  /** A worklet returning up to four decimal digits / a point, e.g. value.toFixed(1). */
  format?: (value: number) => string;
  unit?: string;
  pitch?: number;
  title?: string;
  hint?: string;
  doneHint?: string;
  tokens?: { ground?: string; grid?: string; dot?: string; heat?: [string, string, string]; knob?: string; line?: string; font?: string };
  onFlow?: (unitsPerSecond: number) => void;
  onResult?: (value: number) => void;
  onReset?: () => void;
  autoPlay?: boolean;
};

type DevHook = {
  pin: (t: number) => void;
  at: (t: number) => void;
  play: () => void;
  progress: (p: number) => void;
  state: () => { progress: number; time: number };
};
declare global {
  var __bvl: DevHook | undefined;
}

const EFFECT = Skia.RuntimeEffect.Make(BVL_SKSL);
const AnimatedInput = Animated.createAnimatedComponent(TextInput);
const PRESETS = ['lungs', 'battery', 'bottle', 'heart'];
const FONT_SOURCES = {
  BVLUrbanist: require('./assets/fonts/Urbanist-Regular.ttf'),
  BVLUrbanistMedium: require('./assets/fonts/Urbanist-Medium.ttf'),
};
function defaultFormat(value: number) {
  'worklet';
  return value.toFixed(1);
}
function sourceOf(shape: DotMatrixDrainProps['shape']): DataSource | null {
  if (!shape || (typeof shape === 'string' && (PRESETS.includes(shape) || shape === 'mound'))) return null;
  if (typeof shape === 'object' && '__typename__' in shape) return null;
  return shape as DataSource;
}
function pathOf(shape: DotMatrixDrainProps['shape']): SkPath | null {
  return typeof shape === 'object' && shape && '__typename__' in shape && shape.__typename__ === 'Path' ? (shape as SkPath) : null;
}

function InstrumentButton({
  label,
  onPress,
  children,
  style,
  disabled = false,
}: {
  label: string;
  onPress: () => void;
  children: React.ReactNode;
  style: object;
  disabled?: boolean;
}) {
  const pressed = useSharedValue(0);
  const anim = useAnimatedStyle(() => ({ transform: [{ scale: 1 - pressed.get() * 0.03 }] }));
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={label}
      accessibilityState={{ disabled }}
      disabled={disabled}
      onPress={onPress}
      onPressIn={() => pressed.set(withSpring(1, SPRING.snap))}
      onPressOut={() => pressed.set(withSpring(0, SPRING.snap))}
    >
      <Animated.View style={[style, anim]}>{children}</Animated.View>
    </Pressable>
  );
}

/** A live halftone instrument. No network, device sensor, WebGPU or authored mask is required. */
export function DotMatrixDrain({
  shape = 'lungs',
  pile = 'mound',
  progress: controlled,
  defaultProgress = 0,
  mode = 'both',
  capacity = 5.5,
  format = defaultFormat,
  unit = COPY.unit,
  pitch = 402 / 32,
  title = COPY.title,
  hint = COPY.caption,
  doneHint = COPY.captionDone,
  tokens,
  onFlow,
  onResult,
  onReset,
  autoPlay = false,
}: DotMatrixDrainProps) {
  const { width, height, fontScale } = useWindowDimensions();
  const insets = useSafeAreaInsets();
  const reduced = useReducedMotion();
  const [fontsReady, fontError] = useFonts(FONT_SOURCES);
  const layout = useMemo(() => makeLayout(width, height, insets.top, insets.bottom), [width, height, insets.top, insets.bottom]);
  const S = layout.S;
  const maxVolume = Math.max(0.1, Math.min(99.9, Number.isFinite(capacity) ? capacity : 5.5));
  const shapeImage = useImage(sourceOf(shape));
  const pileImage = useImage(sourceOf(pile));
  const fields = useMemo(() => {
    const preset = typeof shape === 'string' && PRESETS.includes(shape) ? (shape as Preset) : 'lungs';
    const base = makeFieldGrid(preset, pitch);
    const bodyInput = pathOf(shape) ?? shapeImage;
    const pileInput = pathOf(pile) ?? pileImage;
    if (!bodyInput && !pileInput) return base;
    return makeFieldGrid(
      preset,
      pitch,
      bodyInput ? sampleMask(bodyInput, base.cols, base.rows) : undefined,
      pileInput ? sampleMask(pileInput, base.cols, base.rows, true) : undefined,
    );
  }, [shape, pile, pitch, shapeImage, pileImage]);
  const atlas = useMemo(() => gridImage(fields), [fields]);
  const crossings = useMemo(() => litreTimes(maxVolume), [maxVolume]);
  const localProgress = useSharedValue(clamp(defaultProgress));
  const p = controlled ?? localProgress;
  const clock = useSharedValue(0);
  const clockOrigin = useSharedValue(-1);
  const flow = useSharedValue(0);
  // 0 autoplay/idle, 1 hold, 2 scrub, 3 coast, 4 result, 5 refill, 6 pin, 7 accessible single test.
  const phase = useSharedValue(0);
  const anchor = useSharedValue(0);
  const initialP = useSharedValue(0);
  const releaseVelocity = useSharedValue(0);
  const releaseOver = useSharedValue(0);
  const dragOffset = useSharedValue(0);
  const touched = useSharedValue(false);
  const pressed = useSharedValue(0);
  const overshoot = useSharedValue(0);
  const lastPack = useSharedValue(-10);
  const airPeak = useSharedValue(-10);
  const elapsed = useSharedValue(0);
  const deviceSample = useSharedValue({ at: 0, p: 0, started: 0, callbackAt: 0 });
  const fontAlpha = useSharedValue(0);
  const resultAlpha = useSharedValue(0);
  const announce = useCallback(
    (value: number) => {
      const text = `${value.toFixed(1)} ${unit}`;
      AccessibilityInfo.announceForAccessibility(text);
      onResult?.(value);
    },
    [onResult, unit],
  );

  const frame = useFrameCallback((info) => {
    const dt = Math.min(0.05, (info.timeSincePreviousFrame ?? 0) / 1000);
    const old = clamp(p.get());
    if (clockOrigin.get() < 0) clockOrigin.set(info.timestamp / 1000 - clock.get());
    const now = info.timestamp / 1000 - clockOrigin.get();
    const ph = phase.get();
    clock.set(now);
    if (ph === 0 && autoPlay && !controlled && !reduced) {
      const t = now % LOOP;
      p.set(progressAt(t));
      flow.set(flowAt(t, maxVolume));
      elapsed.set(elapsedAt(t));
    } else if (ph === 1 || ph === 7) {
      p.set(heldAt(initialP.get(), now - anchor.get()));
      elapsed.set(elapsed.get() + dt);
      if (ph === 7 && p.get() >= 1) {
        phase.set(4);
        anchor.set(now);
        scheduleOnRN(announce, maxVolume);
      }
    } else if (ph === 3) {
      const age = now - anchor.get();
      p.set(coastAt(initialP.get(), releaseVelocity.get(), age) + releaseOver.get() * (1 - springAt(age, 0.35, 0.85)));
      if (age > TOUCH.coast) {
        p.set(clamp(p.get()));
        phase.set(4);
      }
    } else if (ph === 4 && controlled && now - anchor.get() > TOUCH.idle) {
      phase.set(0);
      flow.set(0);
    } else if (ph === 4 && now - anchor.get() > TOUCH.idle && !controlled) {
      phase.set(5);
      p.set(
        withSpring(0, SPRING.glide, (finished) => {
          if (finished) {
            phase.set(0);
            clock.set(0);
            clockOrigin.set(-1);
            elapsed.set(0);
            flow.set(0);
            touched.set(false);
          }
        }),
      );
    }
    if (ph === 1 || ph === 3 || ph === 5 || ph === 7)
      flow.set(dt > 0 ? clamp(((clamp(p.get()) - old) / dt) * maxVolume, -TOUCH.flowPeak, TOUCH.flowPeak) : 0);
    else if (ph === 4) flow.set(0);
  }, false);
  const wake = useCallback(() => {
    clockOrigin.set(-1);
    frame.setActive(true);
  }, [frame, clockOrigin]);
  const sleep = useCallback(() => frame.setActive(false), [frame]);
  useEffect(() => {
    frame.setActive(autoPlay && !controlled && !reduced);
    const sub = AppState.addEventListener('change', (s) => {
      clockOrigin.set(-1);
      frame.setActive(s === 'active' && phase.get() !== 6 && ((autoPlay && !controlled && !reduced) || phase.get() > 0));
    });
    return () => {
      sub.remove();
      frame.setActive(false);
    };
  }, [autoPlay, controlled, reduced, frame, phase, clockOrigin]);
  useEffect(() => {
    if (fontsReady || fontError) fontAlpha.set(withTiming(1, { duration: 180 }));
  }, [fontsReady, fontError, fontAlpha]);
  useAnimatedReaction(
    () => phase.get() === 0 && (!autoPlay || !!controlled || reduced),
    (idle) => {
      if (idle) scheduleOnRN(sleep);
    },
  );
  useAnimatedReaction(
    () => phase.get() === 4,
    (done, previous) => {
      resultAlpha.set(withTiming(done ? 1 : 0, { duration: 200 }));
      if (done && previous === false && touched.get()) scheduleOnRN(cue, 'result');
    },
  );
  useAnimatedReaction(
    () => Math.floor(clamp(p.get()) * maxVolume),
    (litre, previous) => {
      if (previous !== null && litre > previous && touched.get()) {
        lastPack.set(clock.get());
        scheduleOnRN(cue, 'litre');
      }
    },
  );
  useAnimatedReaction(
    () => Math.floor(clock.get() * 10),
    (tick, previous) => {
      if (tick !== previous && onFlow && touched.get()) scheduleOnRN(onFlow, flow.get());
    },
  );
  useAnimatedReaction(
    () => (controlled && mode === 'display' ? clamp(p.get()) : -1),
    (value, previous) => {
      if (value < 0) return;
      const now = Date.now();
      const old = deviceSample.get();
      const rate =
        previous === null || old.at === 0 || now === old.at
          ? 0
          : clamp(((value - old.p) * maxVolume * 1000) / (now - old.at), -TOUCH.flowPeak, TOUCH.flowPeak);
      const started = old.started || (value > 0 ? now : 0);
      flow.set(rate);
      elapsed.set(started ? (now - started) / 1000 : 0);
      const emit = !!onFlow && now - old.callbackAt >= 100;
      deviceSample.set({ at: now, p: value, started, callbackAt: emit ? now : old.callbackAt });
      if (emit && onFlow) scheduleOnRN(onFlow, rate);
    },
  );

  useAnimatedReaction(
    () => clamp(p.get()) >= 0.98,
    (nearEmpty, previous) => {
      if (nearEmpty && !previous) airPeak.set(clock.get());
    },
  );
  const k = layout.card.k;
  const cardY = layout.card.y;
  const knobY = cardY + (card.knob.y - card.y) * S * k;
  const knobX0 = card.knob.x0 * S;
  const knobX1 = card.knob.x1 * S;
  const travel = knobX1 - knobX0;
  const interactive = mode !== 'display';
  const drag = useMemo(
    () =>
      Gesture.Pan()
        .enabled(interactive)
        .maxPointers(1)
        .minDistance(0)
        .onBegin(() => {
          cancelAnimation(p);
          initialP.set(p.get());
          dragOffset.set(0);
          anchor.set(clock.get());
          touched.set(true);
          phase.set(mode === 'scrub' ? 2 : 1);
          pressed.set(withSpring(1, SPRING.snap));
          flow.set(0);
          scheduleOnRN(wake);
          scheduleOnRN(cue, 'onset');
        })
        .onUpdate((e) => {
          if (mode === 'hold') return;
          if (phase.get() === 1) {
            if (Math.abs(e.translationX) < 1) return;
            initialP.set(p.get());
            dragOffset.set(e.translationX);
            phase.set(2);
          }
          p.set(rubberAt(initialP.get() + (e.translationX - dragOffset.get()) / travel, travel));
          flow.set(clamp((e.velocityX / travel) * maxVolume, -TOUCH.flowPeak, TOUCH.flowPeak));
        })
        .onEnd((e) => {
          const v = phase.get() === 1 ? flow.get() / maxVolume : e.velocityX / travel;
          const value = clamp(p.get());
          releaseOver.set(p.get() - value);
          if (reduced) p.set(value);
          initialP.set(value);
          releaseVelocity.set(v);
          anchor.set(clock.get());
          phase.set(reduced ? 4 : 3);
          if (v < -1.2 && !reduced) overshoot.set(withSpring(0.03, SPRING.inhale, () => overshoot.set(withSpring(0, SPRING.inhale))));
          scheduleOnRN(announce, value * maxVolume);
        })
        .onFinalize(() => {
          pressed.set(withSpring(0, SPRING.snap));
          if (phase.get() === 2 || phase.get() === 1) {
            phase.set(4);
            anchor.set(clock.get());
          }
        }),
    [
      interactive,
      mode,
      p,
      initialP,
      anchor,
      clock,
      touched,
      phase,
      pressed,
      flow,
      wake,
      travel,
      maxVolume,
      reduced,
      releaseVelocity,
      releaseOver,
      dragOffset,
      overshoot,
      announce,
    ],
  );
  const hold = useMemo(
    () =>
      Gesture.LongPress()
        .enabled(interactive && mode !== 'scrub')
        .minDuration(0)
        .maxDistance(32)
        .onStart(() => {
          cancelAnimation(p);
          initialP.set(clamp(p.get()));
          anchor.set(clock.get());
          touched.set(true);
          phase.set(1);
          pressed.set(withSpring(1, SPRING.snap));
          scheduleOnRN(wake);
          scheduleOnRN(cue, 'onset');
        })
        .onFinalize(() => {
          if (phase.get() === 1) {
            initialP.set(clamp(p.get()));
            releaseVelocity.set(flow.get() / maxVolume);
            releaseOver.set(0);
            anchor.set(clock.get());
            phase.set(reduced ? 4 : 3);
            scheduleOnRN(announce, clamp(p.get()) * maxVolume);
          }
          pressed.set(withSpring(0, SPRING.snap));
        }),
    [
      interactive,
      mode,
      p,
      initialP,
      anchor,
      clock,
      touched,
      phase,
      pressed,
      wake,
      releaseVelocity,
      releaseOver,
      flow,
      maxVolume,
      reduced,
      announce,
    ],
  );

  const finish = useCallback(() => {
    if (!interactive) return;
    phase.set(4);
    anchor.set(clock.get());
    touched.set(true);
    flow.set(0);
    pressed.set(0);
    cue('result');
    announce(clamp(p.get()) * maxVolume);
    wake();
  }, [interactive, phase, anchor, clock, touched, flow, pressed, announce, p, maxVolume, wake]);
  const reset = useCallback(() => {
    if (!interactive) return;
    cancelAnimation(p);
    phase.set(5);
    touched.set(true);
    cue('onset');
    p.set(
      withSpring(0, SPRING.glide, (finished) => {
        if (finished) {
          phase.set(0);
          clock.set(0);
          clockOrigin.set(-1);
          elapsed.set(0);
          flow.set(0);
          touched.set(false);
        }
      }),
    );
    onReset?.();
    wake();
  }, [interactive, p, phase, touched, clock, clockOrigin, elapsed, flow, onReset, wake]);
  const a11yTest = useCallback(() => {
    if (!interactive) return;
    touched.set(true);
    cue('onset');
    if (reduced) {
      phase.set(4);
      anchor.set(clock.get());
      p.set(withTiming(1, { duration: 200 }));
      announce(maxVolume);
    } else {
      cancelAnimation(p);
      initialP.set(clamp(p.get()));
      anchor.set(clock.get());
      phase.set(7);
    }
    wake();
  }, [interactive, touched, reduced, phase, anchor, clock, p, initialP, announce, maxVolume, wake]);
  useEffect(() => {
    if (!__DEV__) return;
    const pin = (t: number) => {
      const at = clamp(t, 0, LOOP);
      phase.set(6);
      clock.set(at % LOOP);
      p.set(progressAt(at));
      flow.set(reduced ? 0 : flowAt(at, maxVolume));
      elapsed.set(elapsedAt(at));
      frame.setActive(false);
    };
    const hook: DevHook = {
      pin,
      at: pin,
      play: () => {
        phase.set(0);
        clock.set(0);
        clockOrigin.set(-1);
        p.set(0);
        frame.setActive(!reduced);
      },
      progress: (value) => {
        phase.set(6);
        p.set(clamp(value));
        flow.set(0);
        frame.setActive(false);
      },
      state: () => ({ progress: p.get(), time: clock.get() }),
    };
    globalThis.__bvl = hook;
    return () => {
      if (globalThis.__bvl === hook) globalThis.__bvl = undefined;
    };
  }, [phase, clock, clockOrigin, p, flow, maxVolume, elapsed, frame, reduced]);

  const colors = useMemo(
    () => ({
      ground: rgb(tokens?.ground ?? ref.color.ground),
      gridInk: rgb(tokens?.grid ?? ref.color.grid),
      dotInk: rgb(tokens?.dot ?? ref.color.dot),
      heatA: rgb(tokens?.heat?.[0] ?? ref.color.heat0),
      heatB: rgb(tokens?.heat?.[1] ?? ref.color.heat1),
      heatC: rgb(tokens?.heat?.[2] ?? ref.color.heat2),
      lineInk: rgb(tokens?.line ?? ref.color.line),
      knobInk: rgb(tokens?.knob ?? ref.color.knobHigh),
    }),
    [tokens],
  );
  // Flatten layout numbers before creating the worklet: never capture rowY (a JS closure) or a Gesture object.
  const panel = [layout.card.x, cardY, layout.card.w, layout.card.h];
  const readout = [layout.digits.cx, layout.digits.cy, layout.digits.dx, layout.digits.dy];
  const lattice = [width / fields.cols, (layout.pitchY * 32) / fields.cols, layout.heroTop, fields.cols];
  const atlasInfo = [
    fields.cols,
    fields.rows,
    fields.width,
    fields.cols === 32 ? ref.dot.core * S : Math.min(2.4 * S, (width / fields.cols) * 0.48),
  ];
  const outputP3 = 0;
  const uniforms = useDerivedValue(() => {
    const value = clamp(p.get()) * maxVolume;
    const now = clock.get() % LOOP;
    const tenth = Math.floor(value * 10 + 0.00001);
    const mix = smooth((value * 10 - tenth) / 0.75);
    const a = format(tenth / 10).slice(-4);
    const b = format(Math.min(maxVolume, (tenth + 1) / 10)).slice(-4);
    const packed = packReadout(a, b, new Array<number>(40).fill(0));
    let packAge = -1;
    if (phase.get() === 0 || phase.get() === 6) {
      for (let i = 0; i < crossings.length; i++) if (now >= crossings[i] && now - crossings[i] < 0.24) packAge = now - crossings[i];
    } else packAge = clock.get() - lastPack.get();
    return {
      ground: colors.ground,
      gridInk: colors.gridInk,
      dotInk: colors.dotInk,
      lineInk: colors.lineInk,
      knobInk: colors.knobInk,
      heatInk: heatAt(clamp(p.get()), colors.heatA, colors.heatB, colors.heatC),
      resolution: [width, height],
      lattice,
      atlasInfo,
      panel,
      readout,
      state: [p.get(), reduced ? 0 : flow.get(), reduced ? 0 : now, reduced ? 0 : breatheAt(now)],
      feedback: [packAge, pressed.get(), reduced ? 0 : overshoot.get(), reduced ? 1 : 0],
      knobInfo: [knobX0, knobX1, knobY, card.knob.r * S],
      glyphRows: packed.slice(0, 32),
      glyphX: packed.slice(32, 36),
      glyphKind: packed.slice(36, 40),
      glyphMix: mix,
      p3: outputP3,
      lastAir: reduced ? 0 : phase.get() === 0 || phase.get() === 6 ? lastAirAt(now) : 1 - smooth((clock.get() - airPeak.get()) / 0.4),
    };
  });
  const alphaStyle = useAnimatedStyle(() => ({ opacity: fontAlpha.get() }));
  const captionAlpha = useDerivedValue(() => (phase.get() === 0 || phase.get() === 6 ? resultAt(clock.get()) : resultAlpha.get()));
  const idleHint = useAnimatedStyle(() => ({ opacity: clamp(1 - 2 * captionAlpha.get()) }));
  const doneStyle = useAnimatedStyle(() => ({ opacity: clamp(2 * captionAlpha.get() - 1) }));
  const leftProps = useAnimatedProps(() => ({ text: elapsed.get().toFixed(2), defaultValue: elapsed.get().toFixed(2) }));
  const rightProps = useAnimatedProps(() => ({
    text: Math.max(0, flow.get()).toFixed(2),
    defaultValue: Math.max(0, flow.get()).toFixed(2),
  }));
  const accessibilityProps = useAnimatedProps(() => ({
    accessibilityValue: { min: 0, max: maxVolume, now: clamp(p.get()) * maxVolume, text: `${format(clamp(p.get()) * maxVolume)} ${unit}` },
  }));
  const font = tokens?.font ?? (fontsReady ? sys.font.regular : undefined);
  const textStyle = { color: ref.color.title, fontFamily: font };
  const captionY = cardY + card.captionTop * S * k - ref.font.caption * S;
  const buttonY = Math.min(cardY + (card.pill.y - card.y) * S * k, height - layout.safeBottom - card.pill.h * S * k);
  const cardNumbersY = cardY + (card.leftTop - ref.font.card) * S * k;
  const cardLabelsY = cardY + (card.labelTop - ref.font.cardSmall) * S * k;
  const fontStyle = { ...textStyle, fontSize: ref.font.card * S * Math.min(1, 1.25 / Math.max(1, fontScale)) };
  return (
    <View style={[styles.screen, { backgroundColor: tokens?.ground ?? ref.color.ground }]}>
      <StatusBar style="light" />
      <View
        style={StyleSheet.absoluteFill}
        pointerEvents="none"
        accessibilityElementsHidden
        importantForAccessibility="no-hide-descendants"
      >
        {/* sRGB surface: on a Display P3 surface Skia colour-converts the data atlas, mixing its channels (the radius
            leaked into the pile density and lit the empty lungs). The shader writes sRGB colours, so p3 stays 0. */}
        <Canvas style={StyleSheet.absoluteFill} colorSpace="srgb">
          {EFFECT && atlas ? (
            <Fill>
              <Shader source={EFFECT} uniforms={uniforms}>
                <ImageShader image={atlas} tx="clamp" ty="clamp" sampling={{ filter: 0, mipmap: 0 }} />
              </Shader>
            </Fill>
          ) : (
            <Fill color={tokens?.ground ?? ref.color.ground} />
          )}
        </Canvas>
      </View>
      <Animated.View pointerEvents="none" style={[StyleSheet.absoluteFill, alphaStyle]} accessibilityElementsHidden>
        <Text
          adjustsFontSizeToFit
          numberOfLines={1}
          style={[
            styles.centerText,
            textStyle,
            {
              top: layout.title.base - layout.title.size,
              fontSize: layout.title.size,
              height: layout.title.size * 1.3,
              fontFamily: tokens?.font ?? (fontsReady ? sys.font.medium : undefined),
              left: 20 * S,
              right: 20 * S,
            },
          ]}
        >
          {title}
        </Text>
        <Text
          adjustsFontSizeToFit
          numberOfLines={1}
          style={[
            styles.centerText,
            textStyle,
            {
              top: layout.label.base - layout.label.size,
              fontSize: layout.label.size,
              height: layout.label.size * 1.5,
              left: layout.label.cx - layout.W / 2 + 80 * S,
              right: layout.W / 2 - layout.label.cx + 80 * S,
            },
          ]}
        >
          {unit}
        </Text>
        <AnimatedInput
          editable={false}
          caretHidden
          underlineColorAndroid="transparent"
          animatedProps={leftProps}
          defaultValue="0.00"
          style={[styles.counter, fontStyle, { left: (card.x + card.inset) * S, top: cardNumbersY, width: 64 * S }]}
        />
        <AnimatedInput
          editable={false}
          caretHidden
          underlineColorAndroid="transparent"
          animatedProps={rightProps}
          defaultValue="0.00"
          style={[styles.counter, fontStyle, { right: (card.x + card.inset) * S, top: cardNumbersY, width: 64 * S, textAlign: 'right' }]}
        />
        <Text
          adjustsFontSizeToFit
          numberOfLines={1}
          style={[
            textStyle,
            styles.meta,
            {
              left: (card.x + card.inset) * S,
              top: cardLabelsY,
              fontSize: Math.max(11, ref.font.cardSmall * S),
              height: 16 * S,
              width: 68 * S,
            },
          ]}
        >
          {COPY.left.label}
        </Text>
        <Text
          adjustsFontSizeToFit
          numberOfLines={1}
          style={[
            textStyle,
            styles.meta,
            {
              right: (card.x + card.inset) * S,
              top: cardLabelsY,
              fontSize: Math.max(11, ref.font.cardSmall * S),
              height: 16 * S,
              width: 64 * S,
              textAlign: 'right',
            },
          ]}
        >
          {COPY.right.label}
        </Text>
        <View style={StyleSheet.absoluteFill}>
          {card.dotMark.xs.map((row, r) =>
            row.map((x, c) => (
              <View
                key={`${r}-${c}`}
                style={{
                  position: 'absolute',
                  left: width / 2 + (card.dotMark.cx - 201 + x * card.dotMark.pitch - card.dotMark.r) * S,
                  top: cardY + card.dotMark.ys[r] * S * k - card.dotMark.r * S,
                  width: 2 * card.dotMark.r * S,
                  height: 2 * card.dotMark.r * S,
                  borderRadius: card.dotMark.r * S,
                  backgroundColor: ref.color.dot,
                }}
              />
            )),
          )}
        </View>
        <Animated.Text
          adjustsFontSizeToFit
          numberOfLines={1}
          style={[styles.centerText, textStyle, idleHint, { top: captionY, left: 104 * S, right: 104 * S, fontSize: ref.font.caption * S }]}
        >
          {hint}
        </Animated.Text>
        <Animated.Text
          adjustsFontSizeToFit
          numberOfLines={1}
          style={[
            styles.centerText,
            textStyle,
            doneStyle,
            { top: captionY, left: 104 * S, right: 104 * S, fontSize: ref.font.caption * S },
          ]}
        >
          {doneHint}
        </Animated.Text>
      </Animated.View>
      <GestureDetector gesture={hold}>
        <Animated.View
          animatedProps={accessibilityProps}
          style={{ position: 'absolute', top: insets.top + 80 * S, bottom: height - knobY + 30 * S, left: 20, right: 20 }}
          accessible
          accessibilityRole="button"
          accessibilityLabel={title}
          accessibilityHint={COPY.a11yHint}
          accessibilityState={{ disabled: !interactive }}
          accessibilityActions={[{ name: 'activate', label: COPY.a11yStart }]}
          onAccessibilityAction={a11yTest}
        />
      </GestureDetector>
      <GestureDetector gesture={drag}>
        <Animated.View
          animatedProps={accessibilityProps}
          style={{ position: 'absolute', left: Math.max(20, knobX0 - 24 * S), right: 20, top: knobY - 28 * S, height: 56 * S }}
          accessible
          accessibilityRole="adjustable"
          accessibilityLabel={title}
          accessibilityHint="Swipe up or down to adjust by half a litre."
          accessibilityState={{ disabled: !interactive }}
          accessibilityActions={[{ name: 'increment' }, { name: 'decrement' }, { name: 'activate', label: COPY.a11yStart }]}
          onAccessibilityAction={(e) => {
            if (!interactive) return;
            if (e.nativeEvent.actionName === 'activate') {
              a11yTest();
              return;
            }
            const value = clamp(p.get() + (e.nativeEvent.actionName === 'increment' ? 0.5 : -0.5) / maxVolume);
            phase.set(4);
            anchor.set(clock.get());
            touched.set(true);
            p.set(withSpring(value, SPRING.snap));
            cue('onset');
            announce(value * maxVolume);
            wake();
          }}
        />
      </GestureDetector>
      <Animated.View
        style={[
          {
            position: 'absolute',
            left: (card.refresh.cx - card.refresh.r) * S,
            top: buttonY,
            flexDirection: 'row',
            gap: card.controlsGap * S,
          },
          alphaStyle,
        ]}
      >
        <InstrumentButton
          label="Reset breath test"
          disabled={!interactive}
          onPress={reset}
          style={[styles.reset, { width: 60 * S, height: 58 * S * k, borderRadius: 30 * S }]}
        >
          <Svg width={26 * S} height={26 * S} viewBox="0 0 28 28" style={{ transform: [{ translateY: card.iconDrop * S }] }}>
            <Path
              d="M23 11A9 9 0 0 0 7 7L4 10M4 4v6h6M5 17a9 9 0 0 0 16 4l3-3m0 6v-6h-6"
              stroke={ref.color.title}
              strokeWidth={1.7}
              strokeLinecap="round"
              strokeLinejoin="round"
              fill="none"
            />
          </Svg>
        </InstrumentButton>
        <InstrumentButton
          label="Finish breath test"
          disabled={!interactive}
          onPress={finish}
          style={[styles.finish, { width: 194 * S, height: 58 * S * k, borderRadius: 29 * S, paddingLeft: 40 * S, paddingRight: 12 * S }]}
        >
          <Text adjustsFontSizeToFit numberOfLines={1} style={[textStyle, { fontSize: ref.font.pill * S, flex: 1 }]}>
            {COPY.finish}
          </Text>
          <View
            style={[
              styles.closeDisc,
              { width: 36 * S, height: 36 * S, borderRadius: 18 * S, transform: [{ translateY: card.iconDrop * S }] },
            ]}
          >
            <Svg width={18 * S} height={18 * S} viewBox="0 0 20 20">
              <Path d="m4 4 12 12M16 4 4 16" stroke={ref.color.xInk} strokeWidth={1.4} strokeLinecap="round" />
            </Svg>
          </View>
        </InstrumentButton>
      </Animated.View>
      {!EFFECT && <Text style={[styles.fallback, textStyle]}>Breath visualization unavailable</Text>}
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1 },
  centerText: { position: 'absolute', textAlign: 'center', includeFontPadding: false },
  counter: { position: 'absolute', margin: 0, padding: 0, includeFontPadding: false, fontVariant: ['tabular-nums'] },
  meta: { position: 'absolute', color: ref.color.cardDim },
  reset: { alignItems: 'center', justifyContent: 'center', backgroundColor: ref.color.reset },
  finish: { flexDirection: 'row', alignItems: 'center', backgroundColor: ref.color.finish },
  closeDisc: { alignItems: 'center', justifyContent: 'center', backgroundColor: ref.color.xDisc },
  fallback: { position: 'absolute', top: '38%', alignSelf: 'center' },
});
