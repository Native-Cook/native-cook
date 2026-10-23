import { Canvas, Group, Path } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useEffect, useState } from 'react';
import { type LayoutChangeEvent, Pressable, StyleSheet, Text, View, useWindowDimensions } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  type SharedValue,
  interpolateColor,
  useAnimatedStyle,
  useDerivedValue,
  useFrameCallback,
  useReducedMotion,
  useSharedValue,
  withTiming,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

import { CHANGE, COUNT, PIECES, STEP, USER_HOLD, dragLeft, outline, pieceAt } from './scene';

type Props = {
  title?: string;
  subtitle?: string;
  buttonLabel?: string;
  /** The button's colour. */
  tint?: string;
  /** Rebuilds the still life by itself, one composition every STEP seconds, back at the first after LOOP. */
  autoPlay?: boolean;
  onStart?: () => void;
  onMore?: () => void;
};

const BACKGROUND = '#ffffff';
const INK = '#202124';
const GLYPH = '#3c4043';
// Where the title's middle and the ground the pieces stand on are, as shares of the screen's height.
const TITLE_Y = 0.196;
const GROUND_Y = 0.587;
const SUBTITLE_GAP = 20;
// The subtitle's lines are 18 points apart; SwiftUI sets the first one this much higher than React Native does.
const SUBTITLE_LIFT = 1.25;
const BUTTON = { width: 108, height: 32, radius: 4, right: 22, bottom: 9 };
const MORE = { right: 13, top: 25, dot: 3, gap: 3 };
// The still life is drawn in a box this large round the scene's middle on the ground, with room for a throw.
const STAGE = { width: 340, above: 300, below: 60 };
// A touch within this of the scene counts as one on the still life.
const REACH = { side: 130, above: 180, below: 24 };
const PRESS = 0.96;
// Reduced motion: one composition fades out, the next in.
const FADE = 0.3;

/** What shows: the change from one composition to another that began at `t0`, on the screen's clock. */
type Stage = { from: number; to: number; t0: number };
/** A piece taken out of its place by a finger: by how much, and when it was let go (negative while held). */
type Drag = { index: number; dx: number; dy: number; released: number };

function tick() {
  if (process.env.EXPO_OS !== 'web') Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
}

function select() {
  if (process.env.EXPO_OS !== 'web') Haptics.selectionAsync().catch(() => {});
}

type PieceProps = { index: number; stage: SharedValue<Stage>; clock: SharedValue<number>; drag: SharedValue<Drag>; still: boolean };

function Piece({ index, stage, clock, drag, still }: PieceProps) {
  const piece = PIECES[index];
  const now = useDerivedValue(() => {
    const s = stage.get();
    const e = clock.get() - s.t0;
    if (!still) return pieceAt(index, s.from, s.to, e);
    // Reduced motion: no travel. The pieces change places while the still life is faded out.
    const shown = e < FADE ? s.from : s.to;
    return pieceAt(index, shown, shown, 0);
  });
  const path = useDerivedValue(() => {
    const p = now.get();
    return outline(piece.steps, p.w, p.h, p.top, p.bottom, p.taper, p.cut);
  });
  const transform = useDerivedValue(() => {
    const p = now.get();
    const d = drag.get();
    const left = d.index === index ? dragLeft(d.released < 0 ? -1 : clock.get() - d.released) : 0;
    return [
      { translateX: STAGE.width / 2 + p.x + d.dx * left },
      { translateY: STAGE.above - p.y + d.dy * left },
      { rotate: (p.rotation * Math.PI) / 180 },
    ];
  });
  const color = useDerivedValue(() => {
    const s = stage.get();
    if (still) return piece.spots[clock.get() - s.t0 < FADE ? s.from : s.to][10];
    return interpolateColor(now.get().blend, [0, 1], [piece.spots[s.from][10], piece.spots[s.to][10]]);
  });
  return (
    <Group transform={transform}>
      <Path path={path} color={color} />
    </Group>
  );
}

/**
 * A welcome screen whose only picture is a small still life of blocks that keeps rebuilding
 * itself: the same eleven pieces tumble, stretch, round off and change colour into four different
 * buildings, each landing with a springy overshoot.
 */
export function ShapeShuffleWelcome({
  title = 'Welcome home',
  subtitle = 'Control your content and devices from one place.',
  buttonLabel = 'Get Started',
  tint = '#4f46e5',
  autoPlay = false,
  onStart,
  onMore,
}: Props) {
  const still = useReducedMotion();
  const insets = useSafeAreaInsets();
  const window = useWindowDimensions();
  // The screen as laid out; the window's size stands in until then.
  const [laidOut, setLaidOut] = useState<{ width: number; height: number } | null>(null);
  const { width, height } = laidOut ?? window;
  const ground = height * GROUND_Y;

  // Seconds since the first frame.
  const clock = useSharedValue(0);
  const firstFrame = useSharedValue(-1);
  // The first composition, as if it had just been arrived at: every loop begins like this one.
  const stage = useSharedValue<Stage>({ from: COUNT - 1, to: 0, t0: -CHANGE });
  // autoPlay: when the next change starts, and when the loop may start over after a touch.
  const next = useSharedValue(Infinity);
  const resume = useSharedValue(Infinity);
  const drag = useSharedValue<Drag>({ index: -1, dx: 0, dy: 0, released: 0 });
  const pressed = useSharedValue(0);

  useEffect(() => {
    next.set(autoPlay ? stage.get().t0 + STEP : Infinity);
    resume.set(Infinity);
  }, [autoPlay, next, resume, stage]);

  useFrameCallback((frame) => {
    if (firstFrame.get() < 0) firstFrame.set(frame.timestamp);
    const now = (frame.timestamp - firstFrame.get()) / 1000;
    clock.set(now);
    if (!autoPlay) return;
    const s = stage.get();
    if (now >= resume.get() && now >= s.t0 + CHANGE) {
      // The touches are over: back to the first composition, and on with the loop from there.
      resume.set(Infinity);
      stage.set(s.to === 0 ? { from: 0, to: 0, t0: now - CHANGE } : { from: s.to, to: 0, t0: now });
      next.set(stage.get().t0 + STEP);
    } else if (now >= next.get()) {
      // Timed from when it was due, not from this frame: every loop is the same length.
      stage.set({ from: s.to, to: (s.to + 1) % COUNT, t0: next.get() });
      next.set(next.get() + STEP);
    }
  });

  /** A touch takes over from the autoPlay loop, which starts again from the first composition a while later. */
  const touched = () => {
    'worklet';
    if (!autoPlay) return;
    next.set(Infinity);
    resume.set(clock.get() + USER_HOLD);
  };

  const inScene = (x: number, y: number) => {
    'worklet';
    return Math.abs(x - width / 2) < REACH.side && y > ground - REACH.above && y < ground + REACH.below;
  };

  // A tap on the still life rebuilds it at once.
  const tap = Gesture.Tap().onEnd((event, success) => {
    if (!success || !inScene(event.x, event.y)) return;
    touched();
    const s = stage.get();
    if (clock.get() < s.t0 + CHANGE) return;
    stage.set({ from: s.to, to: (s.to + 1) % COUNT, t0: clock.get() });
    scheduleOnRN(select);
  });

  // A piece can be pulled out of its place; it springs back.
  const pan = Gesture.Pan()
    .onStart((event) => {
      touched();
      const s = stage.get();
      const x = event.x - event.translationX - width / 2;
      const y = ground - (event.y - event.translationY);
      // The piece in front, among those the finger is on.
      let hit = -1;
      for (let i = 0; i < PIECES.length; i++) {
        const spot = PIECES[i].spots[s.to];
        const reach = Math.max(spot[2], spot[3]) / 2 + 6;
        if (spot[2] > 0 && Math.abs(x - spot[0]) < reach && Math.abs(y - spot[1]) < reach) hit = i;
      }
      drag.set({ index: hit, dx: event.translationX, dy: event.translationY, released: -1 });
    })
    .onUpdate((event) => {
      const d = drag.get();
      if (d.released < 0) drag.set({ ...d, dx: event.translationX, dy: event.translationY });
    })
    .onFinalize(() => {
      const d = drag.get();
      if (d.released < 0) drag.set({ ...d, released: clock.get() });
    });

  // Reduced motion: the still life fades out and in round a change.
  const sceneStyle = useAnimatedStyle(() => {
    const e = clock.get() - stage.get().t0;
    return { opacity: still && e < FADE * 2 && e >= 0 ? Math.abs(e - FADE) / FADE : 1 };
  });
  const buttonStyle = useAnimatedStyle(() => ({ transform: [{ scale: 1 + (PRESS - 1) * pressed.get() }] }));

  const onLayout = (event: LayoutChangeEvent) => {
    const { width: w, height: h } = event.nativeEvent.layout;
    setLaidOut({ width: w, height: h });
  };

  return (
    <GestureDetector gesture={Gesture.Race(pan, tap)}>
      <View style={styles.screen} onLayout={onLayout}>
        <StatusBar style="dark" />

        <Text style={[styles.title, { top: height * TITLE_Y - 12 }]} numberOfLines={1}>
          {title}
        </Text>
        <Text style={[styles.subtitle, { top: height * TITLE_Y + SUBTITLE_GAP - SUBTITLE_LIFT }]} numberOfLines={2}>
          {subtitle}
        </Text>

        <Animated.View
          pointerEvents="none"
          accessibilityElementsHidden
          importantForAccessibility="no-hide-descendants"
          style={[styles.stage, { left: (width - STAGE.width) / 2, top: ground - STAGE.above }, sceneStyle]}
        >
          <Canvas style={styles.canvas}>
            {PIECES.map((piece, index) => (
              <Piece key={piece.name} index={index} stage={stage} clock={clock} drag={drag} still={still} />
            ))}
          </Canvas>
        </Animated.View>

        <Pressable
          style={[styles.more, { top: insets.top + MORE.top }]}
          hitSlop={12}
          accessibilityRole="button"
          accessibilityLabel="More"
          onPress={() => {
            touched();
            onMore?.();
          }}
        >
          {[0, 1, 2].map((dot) => (
            <View key={dot} style={styles.dot} />
          ))}
        </Pressable>

        <Pressable
          style={[styles.buttonSlot, { bottom: Math.max(insets.bottom, 16) + BUTTON.bottom }]}
          accessibilityRole="button"
          onPressIn={() => pressed.set(withTiming(1, { duration: 100 }))}
          onPressOut={() => pressed.set(withTiming(0, { duration: 160 }))}
          onPress={() => {
            touched();
            tick();
            onStart?.();
          }}
        >
          <Animated.View style={[styles.button, { backgroundColor: tint }, buttonStyle]}>
            <Text style={styles.buttonLabel} numberOfLines={1}>
              {buttonLabel}
            </Text>
          </Animated.View>
        </Pressable>
      </View>
    </GestureDetector>
  );
}

const styles = StyleSheet.create({
  screen: {
    flex: 1,
    backgroundColor: BACKGROUND,
  },
  title: {
    position: 'absolute',
    left: 24,
    right: 24,
    color: INK,
    fontSize: 19,
    lineHeight: 24,
    textAlign: 'center',
    includeFontPadding: false,
  },
  subtitle: {
    position: 'absolute',
    left: 32,
    right: 32,
    color: INK,
    fontSize: 13,
    lineHeight: 18,
    textAlign: 'center',
    includeFontPadding: false,
  },
  stage: {
    position: 'absolute',
    width: STAGE.width,
    height: STAGE.above + STAGE.below,
  },
  canvas: {
    width: STAGE.width,
    height: STAGE.above + STAGE.below,
  },
  more: {
    position: 'absolute',
    right: MORE.right,
    flexDirection: 'row',
    gap: MORE.gap,
    padding: 6,
  },
  dot: {
    width: MORE.dot,
    height: MORE.dot,
    borderRadius: MORE.dot / 2,
    backgroundColor: GLYPH,
  },
  buttonSlot: {
    position: 'absolute',
    right: BUTTON.right,
  },
  button: {
    width: BUTTON.width,
    height: BUTTON.height,
    borderRadius: BUTTON.radius,
    alignItems: 'center',
    justifyContent: 'center',
  },
  buttonLabel: {
    color: '#ffffff',
    fontSize: 12,
    lineHeight: 16,
    fontWeight: '500',
    includeFontPadding: false,
  },
});
