import {
  Blur,
  Canvas,
  Circle,
  Group,
  LinearGradient,
  Oval,
  Path,
  RadialGradient,
  RoundedRect,
  SweepGradient,
  vec,
} from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { useEffect, useState } from 'react';
import { type LayoutChangeEvent, Pressable, StyleSheet, Text, View } from 'react-native';
import Animated, {
  Easing,
  type SharedValue,
  cubicBezier,
  useDerivedValue,
  useFrameCallback,
  useReducedMotion,
  useSharedValue,
  withTiming,
} from 'react-native-reanimated';

type Props = {
  label?: string;
  onPress?: () => void;
  /** Keeps the flare at full strength. When off the ring idles dimmer and flares up while pressed. */
  autoPlay?: boolean;
};

const HEIGHT = 64;
const PAD_RIGHT = 26;
// Gap between the icon well and the label.
const LABEL_GAP = 14;
// The canvas overflows the button by this much on every side to fit the flare's glow.
const BLEED = 44;
// Thickness of the translucent band around the pill.
const BAND = 2.5;
const RING_R = 23.5;
const RING_WIDTH = 3.4;
const DISC_R = 20.5;
const GLOW_R = 38;
// Seconds per lap: the white flare runs clockwise, the rainbow film creeps the other way.
const FLARE_LAP = 5;
const FILM_LAP = 11;
// Where the clock is frozen when the system asks for reduced motion (flare at the upper right).
const STILL_TIME = 3.6;
// The flare is brightest when it passes this angle (upper right), as in the reference.
const PEAK_ANGLE = -0.6;

const EASE_OUT = Easing.bezier(0.23, 1, 0.32, 1);
const CSS_EASE_OUT = cubicBezier(0.23, 1, 0.32, 1);
const TAU = Math.PI * 2;

const DARK = 'rgba(8,8,10,0)';
// Thin-film colours scattered around the ring with dark gaps between them.
const FILM_COLORS = [
  DARK, '#2b5cff', '#58d6ff', '#fff6d8', '#ffc93d', '#ff6a1a', 'rgba(255,45,45,0)',
  DARK, '#7a3cff', '#2b8cff', '#7ff0e0', 'rgba(127,240,224,0)',
  DARK, '#ff8a2a', '#ffe27a', '#ffffff', '#5ad0ff', '#2f54ff', DARK,
];
const FILM_STOPS = [
  0, 0.05, 0.09, 0.13, 0.17, 0.21, 0.26,
  0.36, 0.42, 0.46, 0.5, 0.55,
  0.64, 0.7, 0.74, 0.78, 0.83, 0.88, 0.94,
];
// The flare: blue tail, white body, warm head. Centred on half a turn.
const FLARE_COLORS = [
  'rgba(58,107,255,0)', '#3a6bff', '#9fe8ff', '#ffffff', '#ffffff', '#ffe9a8', '#ff8a2a', 'rgba(255,60,40,0)',
  'rgba(255,60,40,0)',
];
const FLARE_STOPS = [0.28, 0.34, 0.4, 0.46, 0.56, 0.62, 0.67, 0.72, 1];
const BLOOM_COLORS = [
  'rgba(255,244,228,0)', 'rgba(255,244,228,0)', 'rgba(255,244,228,0.95)', 'rgba(255,244,228,0.95)',
  'rgba(255,244,228,0)', 'rgba(255,244,228,0)',
];
const BLOOM_STOPS = [0, 0.36, 0.46, 0.56, 0.68, 1];
const FLARE_CENTER = 0.51;

// Little house, centred on the origin: rounded roof tip, eaves, body and a door notch.
const HOME =
  'M-0.9 -9.2Q0 -10 0.9 -9.2L10.5 -1Q11.2 0 10 0H7.6V8Q7.6 9.4 6.2 9.4H3.1Q2.4 9.4 2.4 8.7V3.6' +
  'H-2.4V8.7Q-2.4 9.4 -3.1 9.4H-6.2Q-7.6 9.4 -7.6 8V0H-10Q-11.2 0 -10.5 -1Z';

function Surface({ w, h, time, energy }: { w: number; h: number; time: SharedValue<number>; energy: SharedValue<number> }) {
  const x = BLEED;
  const y = BLEED;
  const r = h / 2;
  const cy = y + r;
  // The well shares the pill's left cap.
  const c = vec(x + r, cy);

  const filmSpin = useDerivedValue(() => [{ rotate: (-TAU * time.get()) / FILM_LAP }]);
  const flareSpin = useDerivedValue(() => [{ rotate: (TAU * time.get()) / FLARE_LAP - TAU * FLARE_CENTER }]);
  // Brightest at the upper right, dimmest on the opposite side; a press keeps it up.
  const flare = useDerivedValue(() => {
    const angle = (TAU * time.get()) / FLARE_LAP;
    const breath = 0.5 + 0.5 * Math.cos(angle - PEAK_ANGLE);
    return (0.45 + 0.55 * energy.get()) * (0.3 + 0.7 * breath);
  });
  const film = useDerivedValue(() => 0.6 + 0.3 * energy.get());
  const glowAt = useDerivedValue(() => {
    const angle = (TAU * time.get()) / FLARE_LAP;
    return [{ translateX: c.x + RING_R * Math.cos(angle) }, { translateY: c.y + RING_R * Math.sin(angle) }];
  });
  const glow = useDerivedValue(() => flare.get() * 0.8);

  return (
    <Canvas style={{ width: w + BLEED * 2, height: h + BLEED * 2 }}>
      <RoundedRect x={x} y={y} width={w} height={h} r={r} color="rgba(40,40,44,0.72)" />
      <RoundedRect x={x + BAND} y={y + BAND} width={w - BAND * 2} height={h - BAND * 2} r={r - BAND}>
        <LinearGradient start={vec(x, y)} end={vec(x, y + h)} colors={['#1e1e20', '#141416']} />
      </RoundedRect>
      <RoundedRect
        x={x + BAND + 0.5}
        y={y + BAND + 0.5}
        width={w - BAND * 2 - 1}
        height={h - BAND * 2 - 1}
        r={r - BAND - 0.5}
        style="stroke"
        strokeWidth={1}
      >
        <LinearGradient
          start={vec(x, y)}
          end={vec(x, y + h)}
          colors={['rgba(255,255,255,0.1)', 'rgba(255,255,255,0.02)', 'rgba(255,255,255,0.05)']}
        />
      </RoundedRect>

      {/* The groove the ring sits in. */}
      <Circle c={c} r={RING_R + RING_WIDTH / 2 + 0.8} color="#060607" />

      {/* Warm light spilling from the flare onto the pill and the background. */}
      <Group transform={glowAt} opacity={glow}>
        <Circle cx={0} cy={0} r={GLOW_R}>
          <RadialGradient
            c={vec(0, 0)}
            r={GLOW_R}
            colors={['rgba(255,236,206,0.7)', 'rgba(255,200,150,0.2)', 'rgba(255,170,110,0)']}
            positions={[0, 0.45, 1]}
          />
        </Circle>
      </Group>

      {/* Rainbow film: a slightly squashed ring turning with its gradient reads as a liquid wobble. */}
      <Group transform={filmSpin} origin={c} opacity={film}>
        <Oval
          x={c.x - RING_R - 0.7}
          y={c.y - RING_R + 0.7}
          width={(RING_R + 0.7) * 2}
          height={(RING_R - 0.7) * 2}
          style="stroke"
          strokeWidth={RING_WIDTH}
        >
          <SweepGradient c={c} colors={FILM_COLORS} positions={FILM_STOPS} />
        </Oval>
      </Group>

      <Group opacity={flare}>
        <Circle c={c} r={RING_R} style="stroke" strokeWidth={7}>
          <SweepGradient c={c} origin={c} transform={flareSpin} colors={BLOOM_COLORS} positions={BLOOM_STOPS} />
          <Blur blur={5} />
        </Circle>
        <Circle c={c} r={RING_R} style="stroke" strokeWidth={RING_WIDTH + 0.4}>
          <SweepGradient c={c} origin={c} transform={flareSpin} colors={FLARE_COLORS} positions={FLARE_STOPS} />
        </Circle>
      </Group>

      {/* Glass disc holding the icon. */}
      <Circle c={c} r={DISC_R}>
        <LinearGradient
          start={vec(c.x - DISC_R * 0.7, c.y - DISC_R * 0.7)}
          end={vec(c.x + DISC_R * 0.7, c.y + DISC_R * 0.7)}
          colors={['#38383b', '#232325', '#19191b']}
        />
      </Circle>
      <Circle c={c} r={DISC_R - 0.5} style="stroke" strokeWidth={1} color="rgba(255,255,255,0.07)" />

      <Group transform={[{ translateX: c.x }, { translateY: c.y - 0.5 }]}>
        <Path path={HOME}>
          <LinearGradient
            start={vec(-8, -10)}
            end={vec(8, 10)}
            colors={['#fbfbfb', '#cfcfd1', '#f0f0f0', '#b2b2b5']}
            positions={[0, 0.45, 0.55, 1]}
          />
        </Path>
      </Group>
    </Canvas>
  );
}

/** Dark glass "Home" pill with an icon well whose rim carries an iridescent film and a circling white flare. */
export function IridescentHomeButton({ label = 'Home', onPress, autoPlay = false }: Props) {
  const reducedMotion = useReducedMotion();
  const [size, setSize] = useState<{ w: number; h: number } | null>(null);
  const [pressed, setPressed] = useState(false);

  const time = useSharedValue(STILL_TIME);
  // 0 = idling, 1 = full flare.
  const energy = useSharedValue(0);

  useFrameCallback((frame) => {
    time.set(time.get() + Math.min(frame.timeSincePreviousFrame ?? 16, 64) / 1000);
  }, !reducedMotion);

  const lit = autoPlay || pressed;
  useEffect(() => {
    // Rises quickly, and fades slowly after release so the flare isn't hidden under the thumb.
    energy.set(withTiming(lit ? 1 : 0, { duration: lit ? 380 : 1400, easing: EASE_OUT }));
  }, [lit, energy]);

  const onLayout = (e: LayoutChangeEvent) => {
    const { width, height } = e.nativeEvent.layout;
    setSize((prev) => (prev && prev.w === width && prev.h === height ? prev : { w: width, h: height }));
  };

  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={label}
      onPress={onPress}
      onPressIn={() => {
        setPressed(true);
        if (process.env.EXPO_OS !== 'web') {
          Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
        }
      }}
      onPressOut={() => setPressed(false)}
      pressRetentionOffset={16}
    >
      <Animated.View
        onLayout={onLayout}
        style={[
          styles.button,
          {
            transform: [{ scale: pressed ? 0.97 : 1 }],
            transitionProperty: 'transform',
            transitionDuration: 130,
            transitionTimingFunction: CSS_EASE_OUT,
          },
        ]}
      >
        {size && (
          <View pointerEvents="none" style={styles.surface}>
            <Surface w={size.w} h={size.h} time={time} energy={energy} />
          </View>
        )}
        <Text style={styles.label} numberOfLines={1}>
          {label}
        </Text>
      </Animated.View>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  button: {
    height: HEIGHT,
    // The icon well fills the left cap.
    paddingLeft: HEIGHT + LABEL_GAP - 6,
    paddingRight: PAD_RIGHT,
    alignItems: 'center',
    justifyContent: 'center',
  },
  surface: {
    position: 'absolute',
    left: -BLEED,
    top: -BLEED,
  },
  label: {
    color: '#ffffff',
    fontSize: 19,
    fontWeight: '500',
  },
});
