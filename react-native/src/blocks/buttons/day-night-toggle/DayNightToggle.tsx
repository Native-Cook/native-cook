import {
  BlurMask,
  Canvas,
  Circle,
  Group,
  Path,
  RadialGradient,
  RoundedRect,
  interpolateColors,
  rect,
  rrect,
  vec,
} from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { useCallback, useEffect, useState } from 'react';
import { Pressable, StyleSheet, View } from 'react-native';
import Animated, {
  Easing,
  ReduceMotion,
  type SharedValue,
  cubicBezier,
  interpolateColor,
  useAnimatedStyle,
  useDerivedValue,
  useSharedValue,
  withTiming,
} from 'react-native-reanimated';

type Props = {
  /** `true` is day (knob on the right). Leave undefined for an uncontrolled toggle. */
  value?: boolean;
  defaultValue?: boolean;
  onValueChange?: (value: boolean) => void;
  /** Flips by itself every few seconds. */
  autoPlay?: boolean;
  /** Also paints the area around the toggle: pale blue at night, cream by day. */
  backdrop?: boolean;
  accessibilityLabel?: string;
};

const WIDTH = 116;
const HEIGHT = 54;
const KNOB = 44;
const INSET = 5;
const TRAVEL = WIDTH - KNOB - INSET * 2;
// The canvas overflows the toggle by this much on every side to fit its shadow.
const BLEED = 24;
const AUTOPLAY_MS = 2600;
const SLIDE_MS = 420;
const FADE_MS = 180;

const EASE_OUT = Easing.bezier(0.23, 1, 0.32, 1);
const CSS_EASE_OUT = cubicBezier(0.23, 1, 0.32, 1);

const NIGHT = { tile: '#e0edf6', track: '#102a38', back: '#5d7b8f', mid: '#3c5c75', front: '#0d1e2b' };
const DAY = { tile: '#f5f1e2', track: '#f8e17b', back: '#f3c552', mid: '#efb93d', front: '#e29c1e' };

// Everything below is in the toggle's own 116 × 54 box.
const STARS: [number, number][] = [
  [54.9, 4.4],
  [73.9, 5.9],
  [88.7, 8.6],
  [61.1, 13.8],
  [77.9, 15],
  [88.7, 18.3],
  [69.4, 25.1],
  [91.2, 37.8],
];
const STAR_RADIUS = 0.75;
// The crescent is a disc with a second, offset disc cut out of it.
const MOON = { x: 90.6, y: 18.6, r: 12.2 };
const MOON_CUT = { x: 87.2, y: 15.6, r: 11.9 };
const SUN = { x: 24.3, y: 20.7, r: 10, glow: 16.5 };

// Three ridge lines, far to near. They run past the bottom edge so they can slide without a gap.
const RIDGE_BACK = 'M50 72L50 45L60 41L69 34L76 37.5L86 41L96 45L106 42L132 46L132 72Z';
const RIDGE_MID = 'M18 72L34 45L47 37L51.5 34.5L60.5 41L69 46L79 48.5L90 53L100 72Z';
const RIDGE_FRONT = 'M-4 72L-4 48L10 45L22 49.5L34 52L47 47.5L57 52.5L70 51L84 54L96 48L105 51.5L122 46L122 72Z';
// How far each ridge drifts (x, y) as the knob crosses: the middle one rises into the day's big dune.
const DRIFT_BACK = [-2, 0];
const DRIFT_MID = [-7, -5];
const DRIFT_FRONT = [-3, 0];

function circle(cx: number, cy: number, r: number) {
  return `M${cx - r} ${cy}a${r} ${r} 0 1 0 ${r * 2} 0a${r} ${r} 0 1 0 ${-r * 2} 0Z`;
}

const STARS_PATH = STARS.map(([sx, sy]) => circle(sx, sy, STAR_RADIUS)).join('');
const MOON_CUT_PATH = circle(MOON_CUT.x, MOON_CUT.y, MOON_CUT.r);

function Ridge({
  path,
  night,
  day,
  drift,
  knob,
  scene,
}: {
  path: string;
  night: string;
  day: string;
  drift: number[];
  knob: SharedValue<number>;
  scene: SharedValue<number>;
}) {
  const color = useDerivedValue(() => interpolateColors(scene.get(), [0, 1], [night, day]));
  const transform = useDerivedValue(() => [
    { translateX: drift[0] * knob.get() },
    { translateY: drift[1] * knob.get() },
  ]);
  return <Path path={path} color={color} transform={transform} />;
}

function Surface({ knob, scene }: { knob: SharedValue<number>; scene: SharedValue<number> }) {
  const clip = rrect(rect(0, 0, WIDTH, HEIGHT), HEIGHT / 2, HEIGHT / 2);

  const track = useDerivedValue(() => interpolateColors(scene.get(), [0, 1], [NIGHT.track, DAY.track]));
  const shadow = useDerivedValue(() =>
    interpolateColors(scene.get(), [0, 1], ['rgba(16,42,56,0.4)', 'rgba(206,150,34,0.42)']),
  );
  const night = useDerivedValue(() => 1 - scene.get());
  const sunScale = useDerivedValue(() => [{ scale: 0.6 + 0.4 * scene.get() }]);
  const knobX = useDerivedValue(() => INSET + KNOB / 2 + TRAVEL * knob.get());

  return (
    <Canvas style={{ width: WIDTH + BLEED * 2, height: HEIGHT + BLEED * 2 }}>
      <Group transform={[{ translateX: BLEED }, { translateY: BLEED }]}>
        <RoundedRect x={6} y={9} width={WIDTH - 12} height={HEIGHT - 4} r={HEIGHT / 2} color={shadow}>
          <BlurMask blur={8} style="normal" />
        </RoundedRect>

        <Group clip={clip}>
          <RoundedRect x={0} y={0} width={WIDTH} height={HEIGHT} r={HEIGHT / 2} color={track} />

          <Group opacity={night}>
            <Path path={STARS_PATH} color="#ffffff" />
            <Group clip={MOON_CUT_PATH} invertClip>
              <Circle cx={MOON.x} cy={MOON.y} r={MOON.r} color="#f6f6f6" />
            </Group>
          </Group>

          {/* The sun sits where the knob rests at night, so the knob uncovers it as it leaves. */}
          <Group opacity={scene} transform={sunScale} origin={vec(SUN.x, SUN.y)}>
            <Circle cx={SUN.x} cy={SUN.y} r={SUN.glow}>
              <RadialGradient
                c={vec(SUN.x, SUN.y)}
                r={SUN.glow}
                colors={['rgba(255,255,255,0.95)', 'rgba(255,252,226,0.9)', 'rgba(255,246,190,0)']}
                positions={[0, 0.55, 1]}
              />
            </Circle>
            <Circle cx={SUN.x} cy={SUN.y} r={SUN.r} color="#fdfdf6" />
          </Group>

          <Ridge path={RIDGE_BACK} night={NIGHT.back} day={DAY.back} drift={DRIFT_BACK} knob={knob} scene={scene} />
          <Ridge path={RIDGE_MID} night={NIGHT.mid} day={DAY.mid} drift={DRIFT_MID} knob={knob} scene={scene} />
          <Ridge path={RIDGE_FRONT} night={NIGHT.front} day={DAY.front} drift={DRIFT_FRONT} knob={knob} scene={scene} />

          <Circle cx={knobX} cy={HEIGHT / 2 + 1.5} r={KNOB / 2} color="rgba(0,0,0,0.24)">
            <BlurMask blur={3} style="normal" />
          </Circle>
          <Circle cx={knobX} cy={HEIGHT / 2} r={KNOB / 2} color="#f6f6f6" />
        </Group>
      </Group>
    </Canvas>
  );
}

/**
 * Switch that slides from a night scene (crescent moon, stars, blue ridges) to a day scene (sun,
 * golden dunes). The knob hides the moon by day and the sun by night.
 */
export function DayNightToggle({
  value,
  defaultValue = false,
  onValueChange,
  autoPlay = false,
  backdrop = false,
  accessibilityLabel = 'Day mode',
}: Props) {
  const [inner, setInner] = useState(defaultValue);
  const [pressed, setPressed] = useState(false);
  const on = value ?? inner;

  const change = useCallback(
    (next: boolean) => {
      setInner(next);
      onValueChange?.(next);
    },
    [onValueChange],
  );

  // The knob slides while the scenery cross-fades a little faster underneath it.
  const knob = useSharedValue(on ? 1 : 0);
  const scene = useSharedValue(on ? 1 : 0);

  useEffect(() => {
    const target = on ? 1 : 0;
    knob.set(withTiming(target, { duration: SLIDE_MS, easing: EASE_OUT }));
    // A plain fade, so it is kept even when the system asks for reduced motion.
    scene.set(
      withTiming(target, { duration: FADE_MS, easing: Easing.inOut(Easing.quad), reduceMotion: ReduceMotion.Never }),
    );
  }, [on, knob, scene]);

  useEffect(() => {
    if (!autoPlay) return;
    // Restarts on every change, so a tap never gets flipped straight back.
    const timer = setTimeout(() => change(!on), AUTOPLAY_MS);
    return () => clearTimeout(timer);
  }, [autoPlay, on, change]);

  const backdropStyle = useAnimatedStyle(() => ({
    backgroundColor: interpolateColor(scene.get(), [0, 1], [NIGHT.tile, DAY.tile]),
  }));

  return (
    <View>
      {backdrop && <Animated.View pointerEvents="none" style={[styles.backdrop, backdropStyle]} />}
      <Pressable
        accessibilityRole="switch"
        accessibilityLabel={accessibilityLabel}
        accessibilityState={{ checked: on }}
        onPress={() => change(!on)}
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
          style={[
            styles.toggle,
            {
              transform: [{ scale: pressed ? 0.97 : 1 }],
              transitionProperty: 'transform',
              transitionDuration: 130,
              transitionTimingFunction: CSS_EASE_OUT,
            },
          ]}
        >
          <View pointerEvents="none" style={styles.surface}>
            <Surface knob={knob} scene={scene} />
          </View>
        </Animated.View>
      </Pressable>
    </View>
  );
}

const styles = StyleSheet.create({
  toggle: {
    width: WIDTH,
    height: HEIGHT,
  },
  surface: {
    position: 'absolute',
    left: -BLEED,
    top: -BLEED,
  },
  // Far larger than any tile; the container it sits in is expected to clip it.
  backdrop: {
    position: 'absolute',
    left: -1000,
    top: -1000,
    right: -1000,
    bottom: -1000,
  },
});
