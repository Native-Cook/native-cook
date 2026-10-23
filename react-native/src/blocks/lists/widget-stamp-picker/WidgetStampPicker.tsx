import { useFonts } from 'expo-font';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Pressable, StyleSheet, Text, View, useWindowDimensions } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  ReduceMotion,
  cancelAnimation,
  useAnimatedReaction,
  useAnimatedStyle,
  useFrameCallback,
  useReducedMotion,
  useSharedValue,
  withSequence,
  withSpring,
  withRepeat,
  withTiming,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import Svg, { Path } from 'react-native-svg';
import { scheduleOnRN } from 'react-native-worklets';

import { HintGlyph } from './HintGlyph';
import { Mascot } from './Mascot';
import { FADE_MS, LOOP_MS, PROJECT_S, RESUME_MS, RUBBER, SNAP, START_INDEX, WHEEL, eyesAt, wheelAt } from './motion';
import { Stamp } from './Stamp';
import { STAMPS } from './stamps';
import { colors, fonts, layout } from './theme';

const LAST = STAMPS.length - 1;
const NEVER = ReduceMotion.Never;

// A 1 pt speck one level off the page colour that flickers while autoplaying. The simulator's
// recorder stamps frames late after a fully still screen, which garbles the site video's timing;
// this keeps it streaming. Nobody can see it.
function RecorderPulse() {
  const v = useSharedValue(0);
  useEffect(() => {
    v.set(withRepeat(withTiming(1, { duration: 50, reduceMotion: NEVER }), -1, true));
    return () => cancelAnimation(v);
  }, [v]);
  const style = useAnimatedStyle(() => ({ opacity: v.get() }));
  return <Animated.View style={[styles.pulse, style]} pointerEvents="none" />;
}

// `freezeAt` (dev only): hold the autoplay still at that loop time, for pixel comparisons with the pin.
export type WidgetStampSelection = { index: number; id: string; label: string };
type Props = {
  autoPlay?: boolean;
  freezeAt?: number;
  onBack?: () => void;
  onSkip?: (selection: WidgetStampSelection) => void;
};

export function WidgetStampPicker({ autoPlay = false, freezeAt, onBack, onSkip }: Props) {
  const [loaded] = useFonts({
    [fonts.display]: require('./assets/fonts/TitanOne-Regular.ttf'),
    [fonts.text]: require('./assets/fonts/Inter-Regular.ttf'),
  });
  const insets = useSafeAreaInsets();
  const { width } = useWindowDimensions();
  const reduced = useReducedMotion();
  const frozen = freezeAt !== undefined;
  const scripted = autoPlay && !reduced && !frozen && loaded;

  const pos = useSharedValue(frozen ? wheelAt(freezeAt) : START_INDEX); // continuous index at the centre
  const start = useSharedValue(START_INDEX);
  const byUser = useSharedValue(false);
  const fade = useSharedValue(1);
  const [index, setIndex] = useState(START_INDEX);
  const [blinks, setClosed] = useState<boolean[]>([false, false, false]);
  const closed = frozen ? eyesAt(freezeAt) : scripted ? blinks : [false, false, false];
  useEffect(() => {
    if (freezeAt !== undefined) pos.set(wheelAt(freezeAt));
  }, [freezeAt, pos]);

  // Move to `target`: a spring carrying the release velocity, or a cross-fade under Reduce Motion.
  const settleTo = useCallback(
    (target: number, velocity: number) => {
      'worklet';
      if (reduced) {
        fade.set(
          withSequence(
            withTiming(0, { duration: FADE_MS / 2, reduceMotion: NEVER }),
            withTiming(1, { duration: FADE_MS / 2, reduceMotion: NEVER }),
          ),
        );
        pos.set(
          withSequence(
            withTiming(pos.get(), { duration: FADE_MS / 2, reduceMotion: NEVER }),
            withTiming(target, { duration: 0, reduceMotion: NEVER }),
          ),
        );
        return;
      }
      pos.set(withSpring(target, { ...SNAP, velocity, reduceMotion: NEVER }));
    },
    [reduced, fade, pos],
  );

  // ---- autoplay: the wheel is a pure function of loop time (motion.ts), on the UI thread ----
  const [playing, setPlaying] = useState(autoPlay && !frozen);
  const clock = useSharedValue(0);
  const running = useSharedValue(false);
  const frameDriver = useFrameCallback((f) => {
    if (!running.get()) return;
    // wall-clock time since the loop started, so a dropped frame never stretches the loop
    if (clock.get() < 0) clock.set(f.timestamp);
    const t = (f.timestamp - clock.get()) % LOOP_MS;
    const p = wheelAt(t);
    pos.set(p);
  }, false);
  const resumeTimer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const scriptedRef = useRef(scripted);
  useEffect(() => {
    const wasScripted = scriptedRef.current;
    scriptedRef.current = scripted;
    if (scripted) {
      if (!wasScripted) {
        const startTimer = setTimeout(() => setPlaying(true), 0);
        return () => clearTimeout(startTimer);
      }
      return;
    }
    if (resumeTimer.current) {
      clearTimeout(resumeTimer.current);
      resumeTimer.current = null;
    }
    running.set(false);
    frameDriver.setActive(false);
    cancelAnimation(pos);
    if (!frozen) pos.set(Math.max(0, Math.min(LAST, Math.round(pos.get()))));
    fade.set(1);
  }, [fade, frameDriver, frozen, pos, running, scripted]);
  useEffect(() => {
    if (!playing || !scripted) return;
    clock.set(-1);
    byUser.set(false);
    running.set(true);
    frameDriver.setActive(true);
    // the creatures blink on the same clock
    const t0 = Date.now();
    let last = '';
    const id = setInterval(() => {
      const eyes = eyesAt((Date.now() - t0) % LOOP_MS);
      const key = eyes.join();
      if (key !== last) {
        last = key;
        setClosed(eyes);
      }
    }, 33);
    return () => {
      running.set(false);
      frameDriver.setActive(false);
      clearInterval(id);
    };
  }, [byUser, clock, frameDriver, playing, running, scripted]);

  useEffect(
    () => () => {
      if (resumeTimer.current) clearTimeout(resumeTimer.current);
    },
    [],
  );
  const stopAutoplay = useCallback(() => {
    if (resumeTimer.current) clearTimeout(resumeTimer.current);
    running.set(false);
    byUser.set(true);
    setPlaying(false);
  }, [byUser, running]);
  // 3.5 s after the last touch the wheel returns to rest and the script starts over.
  const scheduleResume = useCallback(() => {
    if (!scripted) return;
    if (resumeTimer.current) clearTimeout(resumeTimer.current);
    resumeTimer.current = setTimeout(() => {
      if (!scriptedRef.current) return;
      byUser.set(false);
      if (pos.get() !== START_INDEX) settleTo(START_INDEX, 0);
      resumeTimer.current = setTimeout(() => {
        if (scriptedRef.current) setPlaying(true);
      }, 900);
    }, RESUME_MS);
  }, [byUser, pos, scripted, settleTo]);

  const tick = useCallback(() => {
    Haptics.selectionAsync().catch(() => {});
  }, []);

  useAnimatedReaction(
    () => Math.max(0, Math.min(LAST, Math.round(pos.get()))),
    (now, before) => {
      if (before === null || now === before) return;
      scheduleOnRN(setIndex, now);
      if (byUser.get()) scheduleOnRN(tick);
    },
  );

  // ---- touch ----
  const gesture = useMemo(() => {
    const pan = Gesture.Pan()
      .maxPointers(1)
      .activeOffsetX([-8, 8])
      .failOffsetY([-24, 24])
      .onBegin(() => {
        cancelAnimation(pos);
        start.set(pos.get());
      })
      .onUpdate((e) => {
        let next = start.get() - e.translationX / WHEEL.stepX;
        // rubber band past the first and last stamp
        if (next < 0) next = next * RUBBER;
        if (next > LAST) next = LAST + (next - LAST) * RUBBER;
        pos.set(next);
      })
      .onEnd((e) => {
        const v = -e.velocityX / WHEEL.stepX;
        // where the wheel would coast to: a flick is enough
        settleTo(Math.max(0, Math.min(LAST, Math.round(pos.get() + v * PROJECT_S))), v);
      });
    const tap = Gesture.Tap().onEnd((e) => {
      // a tap on a neighbour brings it to the centre
      const dx = e.absoluteX - width / 2;
      if (Math.abs(dx) < layout.stamp.width / 2) return;
      settleTo(Math.max(0, Math.min(LAST, Math.round(pos.get()) + (dx > 0 ? 1 : -1))), 0);
    });
    return Gesture.Race(pan, tap);
  }, [pos, start, width, settleTo]);

  const wheelStyle = useAnimatedStyle(() => ({ opacity: fade.get() }));

  const top = insets.top + layout.cropTopFromSafe;
  const navTop = top + layout.navCenterY - layout.backSize / 2;

  if (!loaded) return <View style={styles.screen} />;

  return (
    <GestureDetector gesture={gesture}>
      <View style={styles.screen} onTouchStart={frozen ? undefined : stopAutoplay} onTouchEnd={scheduleResume}>
        <StatusBar style="dark" />
        {playing && scripted && <RecorderPulse />}

        <Animated.View
          style={[styles.wheel, { left: width / 2, top: top + layout.stamp.centerY }, wheelStyle]}
          accessible
          accessibilityRole="adjustable"
          accessibilityLabel="Widget pack"
          accessibilityValue={{ text: `${STAMPS[index].label}, ${index + 1} of ${STAMPS.length}` }}
          accessibilityActions={[{ name: 'increment' }, { name: 'decrement' }]}
          onAccessibilityAction={(e) => {
            stopAutoplay();
            const dir = e.nativeEvent.actionName === 'increment' ? 1 : -1;
            settleTo(Math.max(0, Math.min(LAST, index + dir)), 0);
          }}
        >
          {STAMPS.map((item, i) => (
            <Stamp key={item.id} item={item} index={i} pos={pos} />
          ))}
        </Animated.View>

        <Pressable
          style={[styles.back, { top: navTop, left: layout.sideMargin }]}
          accessibilityRole="button"
          accessibilityLabel="Back"
          accessibilityState={{ disabled: !onBack }}
          disabled={!onBack}
          onPress={onBack}
          hitSlop={4}
        >
          <Svg width={14} height={14} viewBox="0 0 14 14">
            <Path
              d="M9 1.9 L2.5 7 L9 12.1"
              stroke={colors.ink}
              strokeWidth={2.2}
              fill="none"
              strokeLinecap="round"
              strokeLinejoin="round"
            />
          </Svg>
        </Pressable>
        <Pressable
          style={[styles.skip, { top: top + layout.navCenterY - layout.skip.height / 2, right: layout.sideMargin }]}
          accessibilityRole="button"
          accessibilityLabel="Skip"
          accessibilityState={{ disabled: !onSkip }}
          disabled={!onSkip}
          onPress={() => onSkip?.({ index, id: STAMPS[index].id, label: STAMPS[index].label })}
          hitSlop={4}
        >
          <Text style={styles.skipText}>Skip</Text>
        </Pressable>

        <View style={[styles.mascot, { top: top + layout.mascot.top, left: width / 2 - 201 + layout.mascot.left }]}>
          <Mascot closed={closed} />
        </View>

        <Text style={[styles.title, { top: top + layout.title.top }]} accessibilityRole="header">
          {'Choose your\nstarting widgets'}
        </Text>

        <View style={[styles.hint, { top: top + layout.hint.centerY - layout.hint.height / 2 }]} pointerEvents="none">
          <HintGlyph />
        </View>

        <Text style={[styles.caption, { top: top + layout.caption.top }]}>
          Swipe through and pick the pack that best suits you.
        </Text>
      </View>
    </GestureDetector>
  );
}

const styles = StyleSheet.create({
  pulse: { position: 'absolute', right: 0, bottom: 0, width: 1, height: 1, backgroundColor: '#FEFBFE' },
  screen: { flex: 1, backgroundColor: colors.bg, overflow: 'hidden' },
  wheel: { position: 'absolute', width: 0, height: 0 },
  back: {
    position: 'absolute',
    width: layout.backSize,
    height: layout.backSize,
    borderRadius: layout.backSize / 2,
    backgroundColor: colors.backBg,
    alignItems: 'center',
    justifyContent: 'center',
  },
  skip: {
    position: 'absolute',
    width: layout.skip.width,
    height: layout.skip.height,
    borderRadius: layout.skip.height / 2,
    backgroundColor: colors.skipBg,
    alignItems: 'center',
    justifyContent: 'center',
  },
  skipText: { fontFamily: fonts.display, fontSize: 17, color: colors.skipInk },
  mascot: {
    position: 'absolute',
    width: layout.mascot.width,
    height: layout.mascot.height,
  },
  title: {
    position: 'absolute',
    left: 0,
    right: 0,
    textAlign: 'center',
    fontFamily: fonts.display,
    fontSize: layout.title.fontSize,
    lineHeight: layout.title.lineHeight,
    color: colors.ink,
    transform: [{ scaleX: layout.title.stretch }],
  },
  hint: { position: 'absolute', alignSelf: 'center' },
  caption: {
    position: 'absolute',
    alignSelf: 'center',
    width: layout.caption.width,
    textAlign: 'center',
    fontFamily: fonts.text,
    fontSize: layout.caption.fontSize,
    lineHeight: layout.caption.lineHeight,
    color: colors.caption,
  },
});
