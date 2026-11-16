import { useFonts } from 'expo-font';
import { StatusBar } from 'expo-status-bar';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { AccessibilityInfo, Platform, StyleSheet, useWindowDimensions, View } from 'react-native';
import { Gesture, GestureDetector, ScrollView } from 'react-native-gesture-handler';
import {
  cancelAnimation,
  ReduceMotion,
  useAnimatedReaction,
  useDerivedValue,
  useFrameCallback,
  useReducedMotion,
  useSharedValue,
  withSpring,
  withTiming,
  type SharedValue,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { Canvas, Fill, Group, Shader, Skia } from '@shopify/react-native-skia';
import { scheduleOnRN } from 'react-native-worklets';

import { useNcmStrike } from './ncm-audio';
import {
  explicitFrame,
  fillField,
  FRAME,
  interactiveFrame,
  nearAll,
  newSnap,
  newState,
  playFrame,
  ST,
  type Cfg,
  type Rig,
} from './ncm-engine';
import { haptic, type Cue } from './ncm-haptics';
import {
  buildCrossings,
  clamp,
  CROSSINGS,
  GESTURE,
  hapticWeight,
  LAB,
  labelSettled,
  newEvents,
  newLab,
  newLook,
  PATH,
  projectedStop,
  REDUCED_DIP,
  rubberMood,
  SPRING,
  squeezeFor,
  type Look,
} from './ncm-motion';
import { NcmScene, textWidth, useNcmFonts } from './ncm-scene';
import { CONTOUR_SKSL, newField, packMoods } from './ncm-shaders';
import { comp, COPY, makeLayout, MOODS, ref, resolveTheme, type Mood, type NcmTheme } from './ncm-theme';

export { LOOP } from './ncm-motion';
export { MOODS } from './ncm-theme';
export { ContourField } from './ncm-field';
export type { ContourFieldProps } from './ncm-field';
export type { Mood, MoodShape, NcmTheme } from './ncm-theme';
const contourFx = Skia.RuntimeEffect.Make(CONTOUR_SKSL);
const P3 = Platform.OS === 'ios' ? 1 : 0;
export const FONT_FILES = {
  NcmRegular: require('./assets/fonts/Inter-Regular.ttf'),
  NcmMedium: require('./assets/fonts/Inter-Medium.ttf'),
  NcmSemiBold: require('./assets/fonts/Inter-SemiBold.ttf'),
  NcmNunito: require('./assets/fonts/Nunito-ExtraBold.ttf'),
};
export type ContourMoodPickerProps = {
  /** 3 to 9 stops, mildest to wildest; the default is the seven market moods. */
  moods?: readonly Mood[];
  /** controlled stop index */
  value?: number;
  defaultValue?: number;
  /** continuous 0 .. moods.length - 1: read it for the picker's state or write it to drive the picker */
  progress?: SharedValue<number>;
  /** every stop the finger crosses (the label switches there) */
  onChange?: (index: number, mood: Mood) => void;
  /** the release has come to rest on a stop */
  onChangeEnd?: (index: number, mood: Mood) => void;
  onNext?: (index: number) => void;
  onClose?: () => void;
  title?: string;
  question?: string;
  endLabels?: [string, string];
  nextLabel?: string;
  rings?: number;
  spacing?: number;
  echoMs?: number;
  breathing?: boolean;
  theme?: NcmTheme;
  /** a pan on the hero scrubs the mood and squeezes the rings, a tap sends a pulse; turn it off inside a sheet that dismisses by pan */
  heroGesture?: boolean;
  haptics?: boolean;
  /** one 70 ms cue per strike, finger-driven only */
  sound?: boolean;
  autoPlay?: boolean;
};

/** One shader field, one continuous mood value. Autoplay is silent; gestures run on the UI runtime. */
export function ContourMoodPicker({
  moods = MOODS,
  value,
  defaultValue,
  progress,
  onChange,
  onChangeEnd,
  onNext,
  onClose,
  title = COPY.title,
  question = COPY.question,
  endLabels = [COPY.left, COPY.right],
  nextLabel = COPY.next,
  rings = 7,
  spacing = 26,
  echoMs = GESTURE.echoMs,
  breathing = true,
  theme,
  heroGesture = true,
  haptics = true,
  sound = false,
  autoPlay = false,
}: ContourMoodPickerProps) {
  const { width, height, fontScale } = useWindowDimensions();
  const insets = useSafeAreaInsets();
  const reduced = useReducedMotion();
  const list = useMemo(() => (moods.length >= 2 ? moods.slice(0, 9) : MOODS), [moods]);
  const n = list.length;
  const last = n - 1;
  const restMood = (PATH.rest * last) / 6;
  const startMood = clamp(value ?? defaultValue ?? Math.round(restMood), 0, last);
  const left = endLabels[0];
  const right = endLabels[1];
  const copy = useMemo(() => ({ title, question, left, right }), [title, question, left, right]);
  const { page, ink, track, button } = theme ?? {};
  const colors = useMemo(() => resolveTheme({ page, ink, track, button }), [page, ink, track, button]);
  const L = useMemo(
    () => makeLayout(width, height, insets.top, insets.bottom, fontScale, copy),
    [width, height, insets.top, insets.bottom, fontScale, copy],
  );
  const { S, heroS, heroY, firstX, lastX, sliderY, sliderH } = L;
  const [loaded] = useFonts(FONT_FILES);
  const fonts = useNcmFonts(S, L.fs, heroS, theme);
  const ready = loaded && Object.values(fonts).every(Boolean);
  const [selected, setSelected] = useState(Math.round(startMood));
  const strike = useNcmStrike(sound);

  // Everything the frame loop mutates is allocated once; nothing below allocates per frame.
  const [init] = useState(() => {
    const start = Math.round(startMood);
    const lab = newLab();
    labelSettled(
      lab,
      start,
      new Array<number>(n).fill(0),
      list.map((m) => m.word.length),
    );
    return {
      look: newLook(startMood),
      lab,
      ev: newEvents(),
      st: newState(start),
      hist: new Array<number>(7).fill(startMood),
      snap: newSnap(),
    };
  });
  const mood = useSharedValue(startMood);
  const look = useSharedValue<Look>(init.look);
  const lab = useSharedValue<number[]>(init.lab);
  const field = useSharedValue(newField(colors.page, colors.core));
  const ev = useSharedValue<number[]>(init.ev);
  const st = useSharedValue<number[]>(init.st);
  const hist = useSharedValue<number[]>(init.hist);
  const snap = useSharedValue<number[]>(init.snap);
  const endTarget = useSharedValue(-1);
  const rig = useMemo<Rig>(
    () => ({ mood, look, lab, field, ev, st, hist, snap, endTarget }),
    [mood, look, lab, field, ev, st, hist, snap, endTarget],
  );
  const clock = useSharedValue(0),
    epoch = useSharedValue(-1),
    inited = useSharedValue(false);
  const playing = useSharedValue(autoPlay && !reduced),
    held = useSharedValue(false);
  const touchedAt = useSharedValue(-1e6),
    returnStarted = useSharedValue(false);
  const grab = useSharedValue({ x: 0, y: 0, mood: startMood });
  const press = useSharedValue(0),
    buttonPress = useSharedValue(0),
    closePress = useSharedValue(0);
  const leanX = useSharedValue(0),
    leanY = useSharedValue(0),
    squeeze = useSharedValue(0),
    pulseT0 = useSharedValue(-1);
  const lastProgress = useSharedValue(-1);
  const pinned = useSharedValue(-1),
    explicit = useSharedValue(-1),
    reduceFade = useSharedValue(1);
  const [drawing, setDrawing] = useState(false);
  useEffect(() => {
    if (!ready) return;
    const id = requestAnimationFrame(() => setDrawing(true));
    return () => cancelAnimationFrame(id);
  }, [ready]);

  const tagWidths = useMemo(
    () => list.map((m) => (fonts.tag && m.tag ? textWidth(fonts.tag, m.tag, ref.type.tag.tracking * S) + comp.tag.px * 2 * S : 0)),
    [fonts.tag, list, S],
  );
  const lens = useMemo(() => list.map((m) => m.word.length), [list]);
  const pack = useMemo(() => packMoods(list), [list]);
  const table = useMemo(() => (n === 7 ? CROSSINGS : buildCrossings(n)), [n]);
  const cfg = useMemo<Cfg>(
    () => ({
      n,
      last,
      echoMs,
      rings: clamp(rings, 3, 7),
      spacing,
      breathing,
      reduced,
      cx: width / 2,
      cy: heroY,
      heroS,
      cutoff: L.tagY + (comp.hero.cutoff - comp.tag.centerY) * S,
      fade: comp.hero.fade * S,
      p3: P3,
      ground: [1, 3, 5].map((i) => parseInt(colors.page.slice(i, i + 2), 16) / 255),
      core: [1, 3, 5].map((i) => parseInt(colors.core.slice(i, i + 2), 16) / 255),
      pack,
      widths: tagWidths,
      lens,
      table,
    }),
    [n, last, echoMs, rings, spacing, breathing, reduced, width, heroY, heroS, L.tagY, S, colors, pack, tagWidths, lens, table],
  );
  useEffect(() => {
    // new layout, fonts or colours: refill the uniforms now and let the next frame rebuild the label frame from scratch
    const fresh = newField(colors.page, colors.core);
    fillField(fresh, init.look, cfg, 0, 0, 0, -1);
    field.set(fresh);
    inited.set(false);
  }, [cfg, colors, init, field, inited]);

  const buzz = useCallback(
    (cue: Cue, weight: number) => {
      if (haptics) haptic(cue, weight);
    },
    [haptics],
  );
  const change = useCallback(
    (i: number, live: boolean) => {
      setSelected(i);
      if (live) {
        buzz('detent', hapticWeight(i, n));
        if (sound) strike(i, n);
        onChange?.(i, list[i]);
      }
    },
    [buzz, sound, strike, onChange, list, n],
  );
  const finished = useCallback((i: number) => onChangeEnd?.(i, list[i]), [onChangeEnd, list]);
  // The label (one integer, one word, one tag) is the only thing that says "a stop was crossed".
  useAnimatedReaction(
    () => lab.get()[LAB.to],
    (index, previous) => {
      if (previous === null || index === previous) return;
      scheduleOnRN(change, index, !playing.get() && !returnStarted.get() && pinned.get() < 0 && explicit.get() < 0);
    },
    [change],
  );
  useEffect(() => {
    cancelAnimation(mood);
    mood.set(reduced ? startMood : look.get().mood);
    playing.set(autoPlay && !reduced);
    epoch.set(-1);
  }, [autoPlay, reduced, epoch, look, mood, playing, startMood]);
  useEffect(() => {
    if (value === undefined) return;
    cancelAnimation(mood);
    mood.set(look.get().mood);
    playing.set(false);
    touchedAt.set(clock.get());
    returnStarted.set(false);
    if (reduced) {
      reduceFade.set(0);
      reduceFade.set(withTiming(1, { duration: REDUCED_DIP * 1000, reduceMotion: ReduceMotion.Never }));
    }
    mood.set(reduced ? clamp(value, 0, last) : withSpring(clamp(value, 0, last), SPRING.snap));
  }, [value, last, reduced, clock, look, mood, playing, reduceFade, returnStarted, touchedAt]);
  useEffect(() => {
    if (!__DEV__) return;
    const hook = {
      pin: (t: number) => {
        explicit.set(-1);
        pinned.set(Math.max(0, t));
      },
      at: (t: number) => {
        explicit.set(-1);
        pinned.set(Math.max(0, t));
      },
      play: () => {
        pinned.set(-1);
        explicit.set(-1);
        epoch.set(-1);
        playing.set(autoPlay && !reduced);
      },
      progress: (m: number) => {
        pinned.set(-1);
        explicit.set(clamp(m, 0, last));
      },
      state: () => ({ look: look.get(), lab: lab.get(), playing: playing.get(), reduced }),
    };
    (globalThis as Record<string, unknown>).__ncm = hook;
    return () => {
      if ((globalThis as Record<string, unknown>).__ncm === hook) delete (globalThis as Record<string, unknown>).__ncm;
    };
  }, [autoPlay, reduced, last, epoch, explicit, look, lab, pinned, playing]);

  useFrameCallback((frame) => {
    if (!ready || !drawing) return;
    const now = frame.timestamp / 1000;
    clock.set(now);
    const dt = Math.min((frame.timeSincePreviousFrame ?? 16.67) / 1000, 0.05);
    if (!inited.get()) {
      explicitFrame(rig, cfg, clamp(mood.get(), 0, last));
      inited.set(true);
    }
    if (progress !== undefined) {
      const p = progress.get();
      if (Math.abs(p - lastProgress.get()) > 0.000001) {
        // someone else is driving the progress value: that is a touch
        cancelAnimation(mood);
        mood.set(clamp(p, 0, last));
        playing.set(false);
        touchedAt.set(now);
        returnStarted.set(false);
      }
    }
    let handled = false;
    if (pinned.get() >= 0 && !reduced) {
      playFrame(rig, cfg, pinned.get());
      handled = true;
    } else if (explicit.get() >= 0) {
      explicitFrame(rig, cfg, explicit.get());
      handled = true;
    } else if (playing.get() && !reduced) {
      if (epoch.get() < 0) epoch.set(now);
      playFrame(rig, cfg, now - epoch.get());
      handled = true;
    }
    if (!handled) {
      const flags = interactiveFrame(
        rig,
        cfg,
        now,
        dt,
        held.get(),
        press.get(),
        returnStarted.get(),
        leanX.get(),
        leanY.get(),
        squeeze.get(),
        pulseT0.get(),
      );
      if (flags & FRAME.end) scheduleOnRN(finished, st.get()[ST.endIdx]);
      const m = clamp(mood.get(), 0, last);
      if (autoPlay && !held.get() && now - touchedAt.get() >= GESTURE.resumeAfter && pinned.get() < 0) {
        if (!returnStarted.get()) {
          returnStarted.set(true);
          if (reduced) {
            reduceFade.set(0);
            mood.set(restMood);
            reduceFade.set(withTiming(1, { duration: REDUCED_DIP * 1000, reduceMotion: ReduceMotion.Never }));
          } else mood.set(withSpring(restMood, SPRING.glide));
        }
        if (
          !reduced &&
          now - touchedAt.get() >= GESTURE.resumeAfter + GESTURE.returnDuration &&
          Math.abs(m - restMood) < 0.002 &&
          nearAll(look.get().echoes, restMood, 0.004)
        ) {
          mood.set(restMood);
          epoch.set(-1);
          playing.set(true);
          returnStarted.set(false);
        }
      }
    }
    if (progress !== undefined) {
      const m = look.get().mood;
      progress.set(m);
      lastProgress.set(m);
    }
  });

  const selectStop = useCallback(
    (target: number) => {
      cancelAnimation(mood);
      mood.set(look.get().mood);
      playing.set(false);
      pinned.set(-1);
      explicit.set(-1);
      touchedAt.set(clock.get());
      returnStarted.set(false);
      endTarget.set(target);
      if (reduced) {
        reduceFade.set(0);
        mood.set(target);
        reduceFade.set(withTiming(1, { duration: REDUCED_DIP * 1000, reduceMotion: ReduceMotion.Never }));
      } else mood.set(withSpring(target, SPRING.snap));
    },
    [clock, endTarget, explicit, look, mood, pinned, playing, reduceFade, reduced, returnStarted, touchedAt],
  );
  const slider = useMemo(
    () =>
      Gesture.Pan()
        .minDistance(0)
        .maxPointers(1)
        .onBegin((e) => {
          cancelAnimation(mood);
          const current = look.get().mood;
          mood.set(current);
          playing.set(false);
          pinned.set(-1);
          explicit.set(-1);
          held.set(true);
          touchedAt.set(clock.get());
          returnStarted.set(false);
          endTarget.set(-1);
          grab.set({ x: e.x, y: e.y, mood: current });
          press.set(reduced ? 0 : withSpring(1, SPRING.press));
          scheduleOnRN(buzz, 'onset', 1);
        })
        .onUpdate((e) => {
          const start = grab.get();
          if (!reduced) mood.set(rubberMood(start.mood + (e.x - start.x) / ((lastX - firstX) / last), last));
        })
        .onEnd((e) => {
          const start = grab.get(),
            moved = Math.abs(e.x - start.x) > 5 * S;
          const v = e.velocityX / ((lastX - firstX) / last);
          const target =
            moved && !reduced
              ? projectedStop(mood.get(), v, last)
              : Math.round(clamp(((e.x + L.sliderX - firstX) / (lastX - firstX)) * last, 0, last));
          endTarget.set(target);
          if (reduced) {
            reduceFade.set(0);
            mood.set(target);
            reduceFade.set(withTiming(1, { duration: REDUCED_DIP * 1000, reduceMotion: ReduceMotion.Never }));
          } else mood.set(withSpring(target, { ...SPRING.snap, velocity: moved ? v : 0 }));
          scheduleOnRN(buzz, target === 0 || target === last ? 'limit' : 'release', 1);
        })
        .onFinalize(() => {
          held.set(false);
          touchedAt.set(clock.get());
          press.set(withSpring(0, SPRING.press));
        }),
    [
      S,
      L.sliderX,
      last,
      buzz,
      clock,
      endTarget,
      explicit,
      firstX,
      grab,
      held,
      lastX,
      look,
      mood,
      pinned,
      playing,
      press,
      reduceFade,
      reduced,
      returnStarted,
      touchedAt,
    ],
  );
  // Touch the light: the same mood, 56 pt per stop, and the rings give under the finger.
  const heroPan = useMemo(
    () =>
      Gesture.Pan()
        .minDistance(6)
        .maxPointers(1)
        .onBegin((e) => {
          cancelAnimation(mood);
          const current = look.get().mood;
          mood.set(current);
          playing.set(false);
          pinned.set(-1);
          explicit.set(-1);
          held.set(true);
          touchedAt.set(clock.get());
          returnStarted.set(false);
          endTarget.set(-1);
          grab.set({ x: e.x, y: e.y, mood: current });
          press.set(reduced ? 0 : withSpring(1, SPRING.press));
          scheduleOnRN(buzz, 'onset', 1);
        })
        .onUpdate((e) => {
          if (reduced) return;
          const start = grab.get();
          mood.set(rubberMood(start.mood + (e.x - start.x) / (comp.touch.pointsPerStop * S), last));
          squeeze.set(squeezeFor(e.y - start.y, comp.touch.squeezeMax * spacing));
          const dx = (e.x - width / 2) / S,
            dy = (e.y - (heroY - L.heroTouchTop)) / S;
          const r = Math.sqrt(dx * dx + dy * dy);
          const k = r > 0.000001 ? (comp.touch.leanMax * Math.tanh(r / comp.touch.leanReach)) / r : 0;
          leanX.set(dx * k);
          leanY.set(dy * k);
        })
        .onEnd((e) => {
          const start = grab.get(),
            moved = Math.abs(e.x - start.x) > 5 * S;
          const v = e.velocityX / (comp.touch.pointsPerStop * S);
          const target = moved && !reduced ? projectedStop(mood.get(), v, last) : Math.round(clamp(mood.get(), 0, last));
          endTarget.set(target);
          if (reduced) {
            reduceFade.set(0);
            mood.set(target);
            reduceFade.set(withTiming(1, { duration: REDUCED_DIP * 1000, reduceMotion: ReduceMotion.Never }));
          } else {
            mood.set(withSpring(target, { ...SPRING.snap, velocity: moved ? v : 0 }));
            if (Math.abs(squeeze.get()) > comp.touch.squeezeMax * spacing * 0.3) pulseT0.set(clock.get());
          }
          scheduleOnRN(buzz, target === 0 || target === last ? 'limit' : 'release', 1);
        })
        .onFinalize(() => {
          held.set(false);
          touchedAt.set(clock.get());
          press.set(withSpring(0, SPRING.press));
          squeeze.set(withSpring(0, SPRING.squeeze));
          leanX.set(withSpring(0, SPRING.squeeze));
          leanY.set(withSpring(0, SPRING.squeeze));
        }),
    [
      S,
      L.heroTouchTop,
      width,
      heroY,
      spacing,
      last,
      buzz,
      clock,
      endTarget,
      explicit,
      grab,
      held,
      leanX,
      leanY,
      look,
      mood,
      pinned,
      playing,
      press,
      pulseT0,
      reduceFade,
      reduced,
      returnStarted,
      squeeze,
      touchedAt,
    ],
  );
  const heroTap = useMemo(
    () =>
      Gesture.Tap()
        .maxDistance(10)
        .onBegin(() => {
          cancelAnimation(mood);
          mood.set(look.get().mood);
          playing.set(false);
          pinned.set(-1);
          explicit.set(-1);
          touchedAt.set(clock.get());
          returnStarted.set(false);
        })
        .onEnd((_, success) => {
          if (!success || reduced) return;
          pulseT0.set(clock.get());
          scheduleOnRN(buzz, 'pulse', 1);
        }),
    [buzz, clock, explicit, look, mood, pinned, playing, pulseT0, reduced, returnStarted, touchedAt],
  );
  const hero = useMemo(() => Gesture.Race(heroPan, heroTap), [heroPan, heroTap]);
  const next = useCallback(() => {
    const index = Math.round(clamp(look.get().mood, 0, last));
    selectStop(index);
    buzz('next', 1);
    onNext?.(index);
    if (!onNext) AccessibilityInfo.announceForAccessibility(`${list[index].word.toLowerCase()} selected`);
  }, [look, last, list, onNext, selectStop, buzz]);
  const close = useCallback(() => {
    buzz('release', 1);
    if (onClose) onClose();
    else selectStop(Math.round(restMood));
  }, [onClose, restMood, selectStop, buzz]);
  const nextGesture = useMemo(
    () =>
      Gesture.Tap()
        .onBegin(() => buttonPress.set(reduced ? 0 : 1))
        .onEnd((_, success) => {
          if (success) scheduleOnRN(next);
        })
        .onFinalize(() => buttonPress.set(withSpring(0, SPRING.press))),
    [buttonPress, next, reduced],
  );
  const closeGesture = useMemo(
    () =>
      Gesture.Tap()
        .onBegin(() => closePress.set(reduced ? 0 : 1))
        .onEnd((_, success) => {
          if (success) scheduleOnRN(close);
        })
        .onFinalize(() => closePress.set(withSpring(0, SPRING.press))),
    [close, closePress, reduced],
  );
  const edgeOffset = useDerivedValue(() =>
    reduced || playing.get() ? 0 : ((mood.get() - clamp(mood.get(), 0, last)) * (lastX - firstX)) / last,
  );
  const sceneCopy = useMemo(() => ({ next: nextLabel, close: `Close ${title.toLowerCase()}` }), [nextLabel, title]);
  return (
    <View style={[styles.screen, { backgroundColor: colors.page }]}>
      <StatusBar style="light" />
      <ScrollView scrollEnabled={L.H > height + 1} bounces={false} contentContainerStyle={{ minHeight: height }}>
        <View style={{ width, height: L.H }}>
          <Canvas style={StyleSheet.absoluteFill} accessible={false}>
            <Fill color={colors.page} />
            <Group
              opacity={reduceFade}
              clip={Skia.RRectXY(Skia.XYWHRect(0, L.sheetY, width, L.H), comp.sheet.radius * S, comp.sheet.radius * S)}
            >
              {contourFx && drawing ? (
                <Fill>
                  <Shader source={contourFx} uniforms={field} />
                </Fill>
              ) : (
                <Fill color={ref.color.sheetTop} />
              )}
            </Group>
            {ready && drawing && (
              <NcmScene
                {...{ L, fonts, look, lab, pack, colors, press, buttonPress, closePress, reduceFade, edgeOffset }}
                moods={list}
                copy={sceneCopy}
              />
            )}
          </Canvas>
          <View
            accessible
            accessibilityRole="header"
            accessibilityLabel={title}
            style={{ position: 'absolute', top: L.titleY - 24 * S, left: 48 * S, width: width - 108 * S, height: 28 * S }}
          />
          {heroGesture && (
            <GestureDetector gesture={hero}>
              <View
                accessible={false}
                importantForAccessibility="no-hide-descendants"
                style={{
                  position: 'absolute',
                  left: 0,
                  top: L.heroTouchTop,
                  width,
                  height: Math.max(0, L.heroTouchBottom - L.heroTouchTop),
                }}
              />
            </GestureDetector>
          )}
          <GestureDetector gesture={closeGesture}>
            <View
              accessible
              accessibilityRole="button"
              accessibilityLabel={sceneCopy.close}
              onAccessibilityTap={close}
              style={{
                position: 'absolute',
                left: L.closeX - 2 * S,
                top: L.closeY - 2 * S,
                width: Math.max(48, 48 * S),
                height: Math.max(48, 48 * S),
              }}
            />
          </GestureDetector>
          <GestureDetector gesture={slider}>
            <View
              accessible
              accessibilityRole="adjustable"
              accessibilityLabel={question}
              accessibilityValue={{
                min: 1,
                max: n,
                now: selected + 1,
                text: `${list[selected].word.toLowerCase()}, ${selected + 1} of ${n}`,
              }}
              accessibilityHint="Swipe up or down to choose a mood"
              accessibilityActions={[{ name: 'increment' }, { name: 'decrement' }]}
              onAccessibilityAction={(event) => {
                const delta = event.nativeEvent.actionName === 'increment' ? 1 : -1;
                selectStop(clamp(selected + delta, 0, last));
              }}
              style={{ position: 'absolute', left: L.sliderX, top: sliderY - 4, width: L.sliderW, height: sliderH + 8 }}
            />
          </GestureDetector>
          <GestureDetector gesture={nextGesture}>
            <View
              accessible
              accessibilityRole="button"
              accessibilityLabel={nextLabel}
              accessibilityHint="Use the selected market mood"
              onAccessibilityTap={next}
              style={{
                position: 'absolute',
                left: comp.button.x * L.S,
                top: L.buttonY,
                width: comp.button.w * L.S,
                height: Math.max(48, L.buttonH),
              }}
            />
          </GestureDetector>
        </View>
      </ScrollView>
    </View>
  );
}
export default function NeonContourMoodDemo() {
  return <ContourMoodPicker autoPlay sound />;
}
const styles = StyleSheet.create({ screen: { flex: 1 } });
