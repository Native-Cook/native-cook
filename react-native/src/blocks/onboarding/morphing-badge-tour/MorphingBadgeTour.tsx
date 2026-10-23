import { Canvas, ClipOp, Picture, Skia, createPicture } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useEffect, useMemo, useRef, useState } from 'react';
import { type LayoutChangeEvent, Pressable, StyleSheet, Text, View, useWindowDimensions } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  Easing,
  ReduceMotion,
  type SharedValue,
  cancelAnimation,
  useAnimatedReaction,
  useAnimatedStyle,
  useDerivedValue,
  useFrameCallback,
  useReducedMotion,
  useSharedValue,
  withTiming,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';

import { paintStage } from './paint';
import {
  BLEND,
  COUNT,
  FIELDS,
  LOOP,
  MOVE,
  NEXT_AT,
  PIECES,
  SCENES,
  SWIPE,
  USER_HOLD,
  captionShift,
  ease,
  pieceValue,
  runLength,
} from './scenes';

type Props = {
  /** One caption a scene: the welcome, the grid, the constellation and the wallet. */
  captions?: string[];
  /** The small print, in turns plain and highlighted. */
  legal?: string[];
  primaryLabel?: string;
  secondaryLabel?: string;
  /** Goes through the four scenes by itself, every LOOP seconds. */
  autoPlay?: boolean;
  /** Called with the scene's index when the artwork starts to turn into it. */
  onSceneChange?: (index: number) => void;
  onPrimary?: () => void;
  onSecondary?: () => void;
};

const CAPTIONS = ['Welcome to\nPrism Wallet', 'Your home for\neverything onchain', 'Trade 2+ million coins', "Or just save\n(that's cool too)"];
const LEGAL = ['By using Prism Wallet, you agree to the ', 'terms', ' and\n', 'privacy policy'];

const NIGHT = '#0E0D10';
const MUTE = '#99999C';
const LINK = '#3773F5';

/** The stage's centre, as a fraction of the screen's height. */
const STAGE_AT = 0.387;
// The lower third, as centres above the bottom safe area.
const SECONDARY_UP = 9;
const PRIMARY_UP = 83;
const LEGAL_UP = 159;
const CAPTION_UP = 251;
/** The artwork is cut off this far above the caption's first line. */
const STAGE_GAP = 26;
const BUTTON = 56;
const BUTTON_MARGIN = 26.5;

const LINEAR = { easing: Easing.linear, reduceMotion: ReduceMotion.Never };

/** The run in progress, on the UI thread. */
type Run = {
  /** Where the pieces were when the run began: PIECES × FIELDS numbers. */
  from: SharedValue<number[]>;
  /** The scene the run goes to, and whether everything leaves the stage first. */
  to: SharedValue<number>;
  away: SharedValue<boolean>;
  /** Seconds into the run. */
  r: SharedValue<number>;
  /** The scene whose caption leaves. */
  leaving: SharedValue<number>;
  /** 1 while the run's clock is going. */
  active: SharedValue<number>;
};

/** Starts the change to scene `to` from wherever the pieces are. `late`: how long ago it was due. */
function goTo(run: Run, to: number, late: number) {
  'worklet';
  const from = run.from.get();
  const scene = run.to.get();
  const away = run.away.get();
  const r = run.r.get();
  const now: number[] = [];
  for (let k = 0; k < PIECES; k++) for (let i = 0; i < FIELDS; i++) now.push(pieceValue(from, scene, away, r, k, i));
  run.from.set(now);
  run.leaving.set(scene);
  // After the last scene everything leaves the stage, and the first one comes in from outside.
  run.away.set(scene === COUNT - 1 && to === 0);
  run.to.set(to);
  run.r.set(0);
  run.active.set(1);
  const end = runLength(run.away.get());
  run.r.set(
    withTiming(end, { duration: Math.max(0, end - late) * 1000, ...LINEAR }, (finished) => {
      if (finished) run.active.set(0);
    }),
  );
}

function Caption({ run, index, calm, text, top }: { run: Run; index: number; calm: boolean; text: string; top: number }) {
  const style = useAnimatedStyle(() => {
    const arriving = run.to.get();
    const leaving = run.leaving.get();
    if (index !== arriving && index !== leaving) return { opacity: 0, transform: [{ translateY: 0 }] };
    if (arriving === leaving) return { opacity: 1, transform: [{ translateY: 0 }] };
    const now = captionShift(run.r.get(), index === leaving, calm);
    return { opacity: now.opacity, transform: [{ translateY: now.y }] };
  });
  return (
    <Animated.Text pointerEvents="none" style={[styles.caption, { top }, style]}>
      {text}
    </Animated.Text>
  );
}

/**
 * A wallet's intro: a fixed lower third (caption, small print, two buttons) under a stage where
 * one cast of gradient shapes keeps re-forming. A hero badge on a capsule with badges floating
 * round it becomes a grid, the grid a constellation, the constellation a striped wallet a diamond
 * drops into. Nothing cuts: each shape travels and turns into what it is in the next scene.
 * Tap the stage or swipe left for the next scene, swipe right for the one before.
 */
export function MorphingBadgeTour({
  captions = CAPTIONS,
  legal = LEGAL,
  primaryLabel = 'Create new wallet',
  secondaryLabel = 'I already have a wallet',
  autoPlay = false,
  onSceneChange,
  onPrimary,
  onSecondary,
}: Props) {
  const calm = useReducedMotion();
  const insets = useSafeAreaInsets();
  const window = useWindowDimensions();
  // The screen as laid out; the window's size stands in until then.
  const [laidOut, setLaidOut] = useState<{ width: number; height: number } | null>(null);
  const { width, height } = laidOut ?? window;
  const [shown, setShown] = useState(0);
  const [active, setActive] = useState(false);
  // Changes on every touch, which restarts the autoPlay loop behind a pause.
  const [touches, setTouches] = useState(0);

  // The block opens on the first scene at rest.
  const from = useSharedValue(SCENES[0].flat());
  const to = useSharedValue(0);
  const away = useSharedValue(false);
  const r = useSharedValue(MOVE);
  const leaving = useSharedValue(0);
  const running = useSharedValue(0);
  const run: Run = useMemo(() => ({ from, to, away, r, leaving, active: running }), [from, to, away, r, leaving, running]);
  // Seconds since the block appeared, for the bobbing, the breathing and the sparkles.
  const clock = useSharedValue(0);
  const byHand = useSharedValue(false);

  useFrameCallback((frame) => {
    clock.set(frame.timeSinceFirstFrame / 1000);
  }, !calm);

  useEffect(() => () => cancelAnimation(run.r), [run]);

  const turned = (index: number, hand: boolean) => {
    setShown(index);
    if (hand && process.env.EXPO_OS !== 'web') Haptics.selectionAsync().catch(() => {});
    onSceneChange?.(index);
  };
  useAnimatedReaction(
    () => run.to.get(),
    (now, old) => {
      if (old !== null && now !== old) scheduleOnRN(turned, now, byHand.get());
    },
  );
  useAnimatedReaction(
    () => run.active.get(),
    (now, old) => {
      if (now !== old) scheduleOnRN(setActive, now === 1);
    },
  );

  const go = (index: number, hand: boolean, late = 0) => {
    byHand.set(hand);
    scheduleOnUI(goTo, run, index, late);
  };

  /** A touch takes over from the autoPlay loop, which starts again from the first scene a while later. */
  const touched = () => setTouches((n) => n + 1);

  // The loop reads the latest state when its timers fire.
  const latest = useRef({ go, shown, active });
  useEffect(() => {
    latest.current = { go, shown, active };
  });

  useEffect(() => {
    if (!autoPlay) return;
    const timers: ReturnType<typeof setTimeout>[] = [];
    const after = (ms: number, step: () => void) => timers.push(setTimeout(step, ms));
    // Every step is due at a fixed time after the loop began, and is told how late it runs.
    const cycle = (origin: number) => {
      NEXT_AT.forEach((seconds) => {
        const due = origin + seconds * 1000;
        after(due - Date.now(), () => latest.current.go((latest.current.shown + 1) % COUNT, false, Math.max(0, Date.now() - due) / 1000));
      });
      after(origin + LOOP * 1000 - Date.now(), () => cycle(origin + LOOP * 1000));
    };
    // After a touch: USER_HOLD seconds of rest, then back to the first scene if need be, and the
    // loop starts over once it has formed.
    const rest = (quiet: number) => {
      if (latest.current.active) return after(100, () => rest(0));
      if (quiet < USER_HOLD * 1000) return after(100, () => rest(quiet + 100));
      if (latest.current.shown === 0) return cycle(Date.now());
      latest.current.go(0, false);
      cycle(Date.now() + runLength(latest.current.shown === COUNT - 1) * 1000);
    };
    if (touches === 0) cycle(Date.now());
    else after(100, () => rest(100));
    return () => timers.forEach(clearTimeout);
  }, [autoPlay, touches]);

  const step = (by: number) => {
    'worklet';
    byHand.set(true);
    goTo(run, (run.to.get() + by + COUNT) % COUNT, 0);
  };
  const stageGesture = Gesture.Exclusive(
    Gesture.Pan()
      .activeOffsetX([-12, 12])
      .onEnd((event) => {
        if (Math.abs(event.translationX) > SWIPE) step(event.translationX < 0 ? 1 : -1);
      }),
    Gesture.Tap().onEnd(() => step(1)),
  );

  const bottom = Math.max(insets.bottom, 12);
  const cx = width / 2;
  const cy = Math.round(height * STAGE_AT);
  const captionY = height - bottom - CAPTION_UP;
  const stageBottom = captionY - STAGE_GAP;

  const picture = useDerivedValue(() => {
    const start = run.from.get();
    const scene = run.to.get();
    const leaves = run.away.get();
    const now = run.r.get();
    const time = clock.get();
    return createPicture(
      (canvas) => {
        canvas.clipRect(Skia.XYWHRect(0, 0, width, stageBottom), ClipOp.Intersect, true);
        if (calm) {
          // Nothing travels: the scene the pieces come from fades into the one they go to.
          const u = ease(now / BLEND);
          paintStage(canvas, (k, i) => { 'worklet'; return start[k * FIELDS + i]; }, 1 - u, null, cx, cy);
          paintStage(canvas, (k, i) => { 'worklet'; return SCENES[scene][k][i]; }, u, null, cx, cy);
          return;
        }
        paintStage(canvas, (k, i) => { 'worklet'; return pieceValue(start, scene, leaves, now, k, i); }, 1, time, cx, cy);
      },
      { width, height },
    );
  });

  const onLayout = (event: LayoutChangeEvent) => {
    const { width: w, height: h } = event.nativeEvent.layout;
    setLaidOut({ width: w, height: h });
  };

  const tap = () => {
    if (process.env.EXPO_OS !== 'web') Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
  };

  return (
    <View style={styles.screen} onLayout={onLayout} onTouchStart={touched}>
      <StatusBar style="light" />
      <View pointerEvents="none" style={styles.fill}>
        <Canvas style={styles.fill}>
          <Picture picture={picture} />
        </Canvas>
      </View>
      <GestureDetector gesture={stageGesture}>
        <View
          style={[styles.stage, { height: stageBottom }]}
          accessible
          accessibilityRole="button"
          accessibilityLabel={captions[shown]?.replace('\n', ' ')}
          accessibilityHint="Shows the next scene"
          accessibilityActions={[{ name: 'activate' }]}
          onAccessibilityAction={() => go((shown + 1) % COUNT, true)}
        />
      </GestureDetector>

      {captions.slice(0, COUNT).map((text, index) => (
        <Caption key={index} run={run} index={index} calm={calm} text={text} top={captionY - 15} />
      ))}

      <Text style={[styles.legal, { top: height - bottom - LEGAL_UP - 8 }]}>
        {legal.map((part, index) => (
          <Text key={index} style={index % 2 === 1 ? styles.link : undefined}>
            {part}
          </Text>
        ))}
      </Text>

      <Pressable
        style={[styles.primarySlot, { top: height - bottom - PRIMARY_UP - BUTTON / 2 }]}
        accessibilityRole="button"
        onPress={() => {
          tap();
          onPrimary?.();
        }}
      >
        {({ pressed }) => (
          <Animated.View style={[styles.primary, { opacity: pressed ? 0.8 : 1, transitionProperty: 'opacity', transitionDuration: 120 }]}>
            <Text style={styles.primaryLabel} numberOfLines={1}>
              {primaryLabel}
            </Text>
          </Animated.View>
        )}
      </Pressable>
      <Pressable
        style={[styles.secondarySlot, { top: height - bottom - SECONDARY_UP - 22 }]}
        accessibilityRole="button"
        onPress={() => {
          tap();
          onSecondary?.();
        }}
      >
        {({ pressed }) => (
          <Animated.Text
            style={[styles.secondaryLabel, { opacity: pressed ? 0.6 : 1, transitionProperty: 'opacity', transitionDuration: 120 }]}
            numberOfLines={1}
          >
            {secondaryLabel}
          </Animated.Text>
        )}
      </Pressable>
    </View>
  );
}

const styles = StyleSheet.create({
  screen: {
    flex: 1,
    backgroundColor: NIGHT,
    overflow: 'hidden',
  },
  fill: {
    position: 'absolute',
    left: 0,
    right: 0,
    top: 0,
    bottom: 0,
  },
  stage: {
    position: 'absolute',
    left: 0,
    right: 0,
    top: 0,
  },
  caption: {
    position: 'absolute',
    left: 0,
    right: 0,
    textAlign: 'center',
    color: '#FFFFFF',
    fontSize: 22,
    lineHeight: 30,
    letterSpacing: -0.4,
    includeFontPadding: false,
  },
  legal: {
    position: 'absolute',
    left: 0,
    right: 0,
    textAlign: 'center',
    color: MUTE,
    fontSize: 11,
    lineHeight: 16,
    includeFontPadding: false,
  },
  link: {
    color: LINK,
  },
  primarySlot: {
    position: 'absolute',
    left: BUTTON_MARGIN,
    right: BUTTON_MARGIN,
    height: BUTTON,
  },
  primary: {
    height: BUTTON,
    borderRadius: BUTTON / 2,
    backgroundColor: '#FFFFFF',
    alignItems: 'center',
    justifyContent: 'center',
  },
  primaryLabel: {
    color: NIGHT,
    fontSize: 14,
    lineHeight: 18,
    fontWeight: '600',
    includeFontPadding: false,
  },
  secondarySlot: {
    position: 'absolute',
    left: BUTTON_MARGIN,
    right: BUTTON_MARGIN,
    height: 44,
    alignItems: 'center',
    justifyContent: 'center',
  },
  secondaryLabel: {
    color: '#FFFFFF',
    fontSize: 14,
    lineHeight: 18,
    fontWeight: '600',
    includeFontPadding: false,
  },
});
