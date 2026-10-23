import { Canvas, Picture } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useState } from 'react';
import { type LayoutChangeEvent, StyleSheet, Text, View, useWindowDimensions } from 'react-native';
import { Gesture, GestureDetector, GestureHandlerRootView } from 'react-native-gesture-handler';
import {
  Easing,
  ReduceMotion,
  cancelAnimation,
  useAnimatedReaction,
  useDerivedValue,
  useFrameCallback,
  useReducedMotion,
  useSharedValue,
  withTiming,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';

import { CURVE_HELD, LOOP, type Look, RETRACT_START, RETURN_START, STEMS_BACK, drawWave, timeline } from './wave';

export { LOOP } from './wave';

type Props = {
  caption?: string;
  /** Peak height of the wave in points. */
  amplitude?: number;
  /** Stems per period while the stems stand. */
  periodStems?: number;
  background?: string;
  ink?: string;
  /** Plays stems → curve → one period → stems by itself, every LOOP seconds. */
  autoPlay?: boolean;
  /** Called when a tap switches between the stems and the curve. */
  onModeChange?: (mode: 'stems' | 'curve') => void;
};

const BACKGROUND = '#e7e6f0';
const INK = '#1b1b1f';
const CURVE_INK = '#6e6e78';
const LINE_INK = '#8a8a92';

const PITCH = 17;
const STRIP = 14;
const ROW = 8;
const OVERLAP = 2;
const DOT = 2.5;
const AMPLITUDE = 120;
// The strip's centre, as a share of the screen's height.
const AXIS_AT = 0.46;
const CAPTION_GAP = 40;
// The pinch's reach: the zoom may go this far past one period, or below the stems' period.
const ZOOM_MIN = -0.6;
const ZOOM_MAX = 1.6;

const AUTO = 0;
const HAND = 1;
const SETTLING = 2;
const USER_HOLD = 3;
const SETTLE = 0.6;
const BUSY = -2;
const RELEASED = -1;

function tick() {
  if (process.env.EXPO_OS !== 'web') Haptics.selectionAsync().catch(() => {});
}

/**
 * An abstract chart of a cycle: a lollipop chart of stems on a strip of colour blocks, the stems
 * rolling as a wave. It dissolves into a single sine curve through the dots, zooms to one period
 * with its crest and trough marked, and returns to stems. Drag to turn the wave, pinch to change
 * its period, tap to switch between the stems and the curve.
 */
export function StemWaveCycles({
  caption = 'Economic cycles',
  amplitude = AMPLITUDE,
  periodStems = 12,
  background = BACKGROUND,
  ink = INK,
  autoPlay = false,
  onModeChange,
}: Props) {
  const still = useReducedMotion();
  const insets = useSafeAreaInsets();
  const window = useWindowDimensions();
  const [size, setSize] = useState({ width: window.width, height: window.height });

  const look: Look = {
    width: size.width,
    height: size.height,
    axis: size.height * AXIS_AT,
    amplitude: Math.min(amplitude, size.height * 0.16),
    pitch: PITCH,
    periodStems,
    strip: STRIP,
    row: ROW,
    overlap: OVERLAP,
    dot: DOT,
    ink,
    curveInk: CURVE_INK,
    lineInk: LINE_INK,
  };
  const basePeriod = PITCH * periodStems;
  const zoomReach = Math.log(size.width / basePeriod);

  // The loop's clock in seconds; a hand freezes it and turns or stretches the wave on top.
  const t = useSharedValue(0);
  const handPhase = useSharedValue(0);
  const handZoom = useSharedValue(0);
  const mode = useSharedValue(autoPlay ? AUTO : HAND);
  const origin = useSharedValue(-1);
  const touchedAt = useSharedValue(RELEASED);
  const settleFrom = useSharedValue(0);
  const dragFrom = useSharedValue(0);
  const pinchFrom = useSharedValue(0);

  useFrameCallback((frame) => {
    const now = frame.timestamp;
    const m = mode.get();
    if (m === AUTO) {
      if (origin.get() < 0) origin.set(now - t.get() * 1000);
      t.set(((now - origin.get()) / 1000) % LOOP);
      return;
    }
    if (!autoPlay) return;
    if (m === HAND) {
      if (touchedAt.get() === RELEASED) touchedAt.set(now);
      if (touchedAt.get() < 0 || now - touchedAt.get() < USER_HOLD * 1000) return;
      // The hand's turn and stretch ease away (the turn to the nearest whole cycle), then the
      // loop plays on from where it was stopped and comes round to rest.
      const config = { duration: SETTLE * 1000, easing: Easing.inOut(Easing.cubic), reduceMotion: ReduceMotion.Never };
      handPhase.set(withTiming(Math.round(handPhase.get() / (Math.PI * 2)) * Math.PI * 2, config));
      handZoom.set(withTiming(0, config));
      settleFrom.set(now);
      mode.set(SETTLING);
      return;
    }
    if (now - settleFrom.get() < SETTLE * 1000) return;
    handPhase.set(0);
    origin.set(now - t.get() * 1000);
    mode.set(AUTO);
  });

  const turnedByHand = (stems: boolean) => {
    tick();
    onModeChange?.(stems ? 'stems' : 'curve');
  };

  const touch = () => {
    'worklet';
    mode.set(HAND);
    touchedAt.set(BUSY);
    cancelAnimation(handPhase);
    cancelAnimation(handZoom);
  };

  /** Plays on to the zoomed curve, or from it back to the stems. */
  const toggle = () => {
    'worklet';
    touch();
    cancelAnimation(t);
    const now = t.get();
    const curve = now >= RETRACT_START && now < STEMS_BACK;
    const from = curve ? Math.max(now, RETURN_START) : RETRACT_START;
    const to = curve ? STEMS_BACK : CURVE_HELD;
    t.set(from);
    t.set(
      withTiming(to, { duration: (to - from) * 1000, easing: Easing.linear, reduceMotion: ReduceMotion.Never }, (done) => {
        if (!done) return;
        if (curve) t.set(0);
        touchedAt.set(RELEASED);
      }),
    );
    scheduleOnRN(turnedByHand, curve);
  };

  const zoomNow = () => {
    'worklet';
    return Math.min(ZOOM_MAX, Math.max(ZOOM_MIN, timeline(t.get(), still).zoom + handZoom.get()));
  };

  const pan = Gesture.Pan()
    .activeOffsetX([-8, 8])
    .onStart(() => {
      touch();
      dragFrom.set(handPhase.get());
    })
    .onUpdate((event) => {
      // The wave follows the finger: a drag of one period turns it one whole cycle.
      const period = basePeriod * Math.exp(zoomReach * zoomNow());
      handPhase.set(dragFrom.get() - (event.translationX / period) * Math.PI * 2);
    })
    .onFinalize(() => {
      touchedAt.set(RELEASED);
    });
  const pinch = Gesture.Pinch()
    .onStart(() => {
      touch();
      pinchFrom.set(handZoom.get());
    })
    .onUpdate((event) => {
      handZoom.set(pinchFrom.get() + Math.log(Math.max(0.05, event.scale)) / zoomReach);
    })
    .onFinalize(() => {
      touchedAt.set(RELEASED);
    });
  const tap = Gesture.Tap().onEnd(() => {
    toggle();
  });
  const gesture = Gesture.Race(Gesture.Simultaneous(pan, pinch), tap);

  // A selection tick each quarter turn while a hand turns the wave.
  useAnimatedReaction(
    () => Math.floor(handPhase.get() / (Math.PI / 2)),
    (now, old) => {
      if (old !== null && now !== old && mode.get() === HAND && touchedAt.get() === BUSY) scheduleOnRN(tick);
    },
  );

  const picture = useDerivedValue(() => {
    const time = t.get();
    const s = timeline(time, still);
    const zoom = Math.min(ZOOM_MAX, Math.max(ZOOM_MIN, s.zoom + handZoom.get()));
    return drawWave(time, s.phase + handPhase.get(), zoom, still, look);
  });

  const onLayout = (event: LayoutChangeEvent) => {
    const { width, height } = event.nativeEvent.layout;
    setSize({ width, height });
  };

  return (
    <GestureHandlerRootView style={[styles.screen, { backgroundColor: background }]}>
      <StatusBar style="dark" />
      <GestureDetector gesture={gesture}>
        <View
          style={styles.fill}
          onLayout={onLayout}
          accessible
          accessibilityRole="button"
          accessibilityLabel={caption}
          accessibilityHint="Switches between the stems and the curve"
          onAccessibilityTap={() => scheduleOnUI(toggle)}
        >
          <Canvas style={StyleSheet.absoluteFill}>
            <Picture picture={picture} />
          </Canvas>
        </View>
      </GestureDetector>
      <Text pointerEvents="none" style={[styles.caption, { color: ink, bottom: Math.max(insets.bottom, 16) + CAPTION_GAP }]}>
        {caption}
      </Text>
    </GestureHandlerRootView>
  );
}

const styles = StyleSheet.create({
  screen: {
    flex: 1,
  },
  fill: {
    flex: 1,
  },
  caption: {
    position: 'absolute',
    left: 0,
    right: 0,
    textAlign: 'center',
    fontSize: 17,
    lineHeight: 22,
    letterSpacing: -0.2,
  },
});
