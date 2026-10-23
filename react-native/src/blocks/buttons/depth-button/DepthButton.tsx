import { Canvas, Path } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { useEffect, useState } from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import Animated, { Easing, useAnimatedStyle, useSharedValue, withTiming } from 'react-native-reanimated';

export type DepthButtonIcon = 'share' | 'plus' | 'heart' | 'play';

type Props = {
  label?: string;
  /** Colour of the face. */
  color?: string;
  /** Colour of the side wall the face sinks into. */
  sideColor?: string;
  icon?: DepthButtonIcon;
  width?: number;
  onPress?: () => void;
  /** Presses itself once per cycle, as if a finger kept tapping it. */
  autoPlay?: boolean;
  /** Where in the cycle the self-press happens, so a group of buttons can take turns. */
  autoPlayDelay?: number;
};

const FACE_HEIGHT = 46;
// Height of the side wall, and how far the face travels when pressed.
const DEPTH = 9;
const RADIUS = 5;
const ICON = 18;
const AUTOPLAY_CYCLE_MS = 4400;
const AUTOPLAY_HOLD_MS = 240;

const EASE_OUT = Easing.bezier(0.23, 1, 0.32, 1);

// Generic glyphs on an 18-point grid, built from moves, lines and curves only so the same data
// can be drawn on every platform.
const ICONS: Record<DepthButtonIcon, { path: string; stroke?: number }> = {
  share: {
    path: 'M9 11.5L9 2.6M5.4 6L9 2.4L12.6 6M3 10L3 14.2Q3 15.6 4.4 15.6L13.6 15.6Q15 15.6 15 14.2L15 10',
    stroke: 2,
  },
  plus: { path: 'M9 3.2L9 14.8M3.2 9L14.8 9', stroke: 2.4 },
  heart: {
    path:
      'M9 15.5C3 11.5 1.5 8.5 1.5 6C1.5 3.8 3.2 2.3 5.2 2.3C6.8 2.3 8.2 3.2 9 4.6' +
      'C9.8 3.2 11.2 2.3 12.8 2.3C14.8 2.3 16.5 3.8 16.5 6C16.5 8.5 15 11.5 9 15.5Z',
  },
  play: { path: 'M5 2.8L15.2 9L5 15.2Z' },
};

function Glyph({ icon }: { icon: DepthButtonIcon }) {
  const { path, stroke } = ICONS[icon];
  return (
    <Canvas style={styles.icon}>
      {stroke ? (
        <Path path={path} style="stroke" strokeWidth={stroke} strokeCap="round" strokeJoin="round" color="#ffffff" />
      ) : (
        <>
          <Path path={path} color="#ffffff" />
          {/* A thin same-colour outline rounds the filled shape's corners. */}
          <Path path={path} style="stroke" strokeWidth={1.4} strokeJoin="round" color="#ffffff" />
        </>
      )}
    </Canvas>
  );
}

/**
 * Chunky push button: a flat coloured face sitting on a darker side wall. Pressing it sinks the
 * face into the wall; letting go pops it back up.
 */
export function DepthButton({
  label = 'Button',
  color = '#1361ef',
  sideColor = '#1d2f61',
  icon,
  width = 148,
  onPress,
  autoPlay = false,
  autoPlayDelay = 0,
}: Props) {
  const [held, setHeld] = useState(false);
  const [auto, setAuto] = useState(false);
  const down = held || (autoPlay && auto);

  const sink = useSharedValue(0);
  useEffect(() => {
    // Quick on the way down, a touch slower coming back up. With reduced motion Reanimated
    // snaps instead, which is exactly how the reference behaves.
    sink.set(withTiming(down ? 1 : 0, { duration: down ? 90 : 150, easing: EASE_OUT }));
  }, [down, sink]);

  useEffect(() => {
    if (!autoPlay) return;
    let release: ReturnType<typeof setTimeout> | undefined;
    const press = () => {
      setAuto(true);
      release = setTimeout(() => setAuto(false), AUTOPLAY_HOLD_MS);
    };
    let cycle: ReturnType<typeof setInterval> | undefined;
    const first = setTimeout(() => {
      press();
      cycle = setInterval(press, AUTOPLAY_CYCLE_MS);
    }, 900 + autoPlayDelay);
    return () => {
      clearTimeout(first);
      clearTimeout(release);
      clearInterval(cycle);
    };
  }, [autoPlay, autoPlayDelay]);

  const faceStyle = useAnimatedStyle(() => ({ transform: [{ translateY: sink.get() * DEPTH }] }));

  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={label}
      onPress={onPress}
      onPressIn={() => {
        setHeld(true);
        if (process.env.EXPO_OS !== 'web') {
          Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
        }
      }}
      onPressOut={() => setHeld(false)}
      pressRetentionOffset={16}
      style={[styles.button, { width }]}
    >
      <View style={[styles.side, { backgroundColor: sideColor }]} />
      <Animated.View style={[styles.face, { backgroundColor: color }, faceStyle]}>
        {icon && <Glyph icon={icon} />}
        <Text style={styles.label} numberOfLines={1}>
          {label}
        </Text>
      </Animated.View>
    </Pressable>
  );
}

const GROUP: { label: string; icon: DepthButtonIcon; color: string; sideColor: string }[] = [
  { label: 'Share', icon: 'share', color: '#1361ef', sideColor: '#1d2f61' },
  { label: 'Follow', icon: 'plus', color: '#198cec', sideColor: '#233d6b' },
  { label: 'Like', icon: 'heart', color: '#ee542c', sideColor: '#8a0a24' },
  { label: 'Play', icon: 'play', color: '#cf161e', sideColor: '#8a0005' },
];
// The order the reference's cursor visits the buttons in: clockwise from the bottom left.
const GROUP_TURN = [2, 3, 0, 1];

/** The four buttons from the reference as a 2×2 group. With `autoPlay` they press in turn. */
export function DepthButtonGroup({ autoPlay = false }: { autoPlay?: boolean }) {
  return (
    <View style={styles.group}>
      {GROUP.map((item, i) => (
        <DepthButton
          key={item.label}
          {...item}
          autoPlay={autoPlay}
          autoPlayDelay={(GROUP_TURN[i] * AUTOPLAY_CYCLE_MS) / GROUP.length}
        />
      ))}
    </View>
  );
}

const styles = StyleSheet.create({
  button: {
    height: FACE_HEIGHT + DEPTH,
  },
  side: {
    position: 'absolute',
    left: 0,
    right: 0,
    top: DEPTH,
    height: FACE_HEIGHT,
    borderRadius: RADIUS,
  },
  face: {
    height: FACE_HEIGHT,
    borderRadius: RADIUS,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 8,
  },
  icon: {
    width: ICON,
    height: ICON,
  },
  label: {
    color: '#ffffff',
    fontSize: 17,
    fontWeight: '500',
    letterSpacing: 0.5,
  },
  group: {
    width: 148 * 2 + 12,
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 12,
  },
});
