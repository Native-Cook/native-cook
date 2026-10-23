import {
  Blur,
  Canvas,
  Circle,
  Group,
  Path,
  RadialGradient,
  RoundedRect,
  rect,
  rrect,
  vec,
} from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { useEffect, useMemo, useRef, useState } from 'react';
import { type LayoutChangeEvent, Pressable, StyleSheet, Text, View } from 'react-native';
import Animated, {
  Easing,
  type SharedValue,
  cancelAnimation,
  cubicBezier,
  useDerivedValue,
  useReducedMotion,
  useSharedValue,
  withRepeat,
  withTiming,
} from 'react-native-reanimated';

type Props = {
  label?: string;
  onPress?: () => void;
  /** Keeps the light circling. When off it appears on press/hover and lingers briefly. */
  autoPlay?: boolean;
  /** How long the light stays after the finger lifts, so it isn't hidden under the thumb. */
  lingerMs?: number;
};

const HEIGHT = 58;
const PAD_X = 46;
// The canvas overflows the button by this much on every side to fit the nebula and its sparkles.
const BLEED = 92;
// One trip of the light around the border.
const LAP_MS = 7000;
// The light travels just inside the border, where the reference's cursor sits.
const LIGHT_INSET = 5;
const NEBULA_R = 86;
const RIM_REACH = 78;
const SPARKLE_GROUPS = 3;
// Where the light rests (as a fraction of a lap) when the system asks for reduced motion.
const STILL_LAP = 0.3;

const EASE_OUT = Easing.bezier(0.23, 1, 0.32, 1);
const CSS_EASE_OUT = cubicBezier(0.23, 1, 0.32, 1);
const TAU = Math.PI * 2;

function mulberry32(seed: number) {
  let a = seed;
  return () => {
    a = (a + 0x6d2b79f5) | 0;
    let t = Math.imul(a ^ (a >>> 15), 1 | a);
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

// Dust scattered around the light (the origin), split into groups that twinkle out of phase.
function sparkles() {
  const rand = mulberry32(23);
  const groups = Array.from({ length: SPARKLE_GROUPS }, () => '');
  for (let i = 0; i < 33; i++) {
    const angle = rand() * TAU;
    const distance = 22 + rand() * 58;
    const r = 0.4 + rand() * rand() * 1.2;
    const cx = Math.cos(angle) * distance;
    const cy = Math.sin(angle) * distance;
    groups[i % SPARKLE_GROUPS] += `M${cx - r} ${cy}a${r} ${r} 0 1 0 ${r * 2} 0a${r} ${r} 0 1 0 ${-r * 2} 0Z`;
  }
  return groups;
}

const SPARKLES = sparkles();

/** A point `lap` (0..1, clockwise from the top-left) of the way around a capsule inset from the button. */
function capsulePoint(lap: number, x: number, y: number, w: number, h: number, inset: number) {
  'worklet';
  const r = h / 2 - inset;
  const cy = y + h / 2;
  const straight = w - h;
  const arc = Math.PI * r;
  let d = (lap - Math.floor(lap)) * (2 * straight + 2 * arc);
  if (d < straight) return { x: x + h / 2 + d, y: cy - r };
  d -= straight;
  if (d < arc) return { x: x + w - h / 2 + r * Math.sin(d / r), y: cy - r * Math.cos(d / r) };
  d -= arc;
  if (d < straight) return { x: x + w - h / 2 - d, y: cy + r };
  d -= straight;
  return { x: x + h / 2 - r * Math.sin(d / r), y: cy + r * Math.cos(d / r) };
}

function SparkleGroup({
  path,
  index,
  p,
  loop,
}: {
  path: string;
  index: number;
  p: SharedValue<number>;
  loop: SharedValue<number>;
}) {
  // Each group wanders a few points around the light and twinkles at its own pace.
  const drift = useDerivedValue(() => [
    { translateX: 4 * Math.sin(TAU * (index + 2) * loop.get() + index * 2.1) },
    { translateY: 4 * Math.cos(TAU * (index + 3) * loop.get() + index * 1.3) },
  ]);
  const opacity = useDerivedValue(() => {
    const wave = 0.5 + 0.5 * Math.sin(TAU * (index + 4) * loop.get() + index * 1.9);
    return p.get() * (0.15 + 0.55 * wave);
  });
  return (
    <Group transform={drift}>
      <Path path={path} color="#a9d8f4" opacity={opacity} />
    </Group>
  );
}

function Surface({ w, h, p, loop }: { w: number; h: number; p: SharedValue<number>; loop: SharedValue<number> }) {
  const x = BLEED;
  const y = BLEED;
  const r = h / 2;
  const clip = useMemo(() => rrect(rect(x, y, w, h), r, r), [x, y, w, h, r]);

  const light = useDerivedValue(() => capsulePoint(loop.get(), x, y, w, h, LIGHT_INSET));
  const atLight = useDerivedValue(() => [{ translateX: light.get().x }, { translateY: light.get().y }]);
  const lightCenter = useDerivedValue(() => vec(light.get().x, light.get().y));

  return (
    <Canvas style={{ width: w + BLEED * 2, height: h + BLEED * 2 }}>
      <Group opacity={p} transform={atLight}>
        <Circle cx={0} cy={0} r={NEBULA_R}>
          <RadialGradient
            c={vec(0, 0)}
            r={NEBULA_R}
            colors={['rgba(48,128,172,0.46)', 'rgba(30,86,132,0.18)', 'rgba(20,60,110,0)']}
            positions={[0, 0.5, 1]}
          />
        </Circle>
        {SPARKLES.map((path, i) => (
          <SparkleGroup key={i} path={path} index={i} p={p} loop={loop} />
        ))}
      </Group>

      <RoundedRect x={x} y={y} width={w} height={h} r={r} color="#050308" />
      {/* The light tints the glass from the inside. */}
      <Group clip={clip} opacity={p}>
        <Group transform={atLight}>
          <Circle cx={0} cy={0} r={RIM_REACH}>
            <RadialGradient
              c={vec(0, 0)}
              r={RIM_REACH}
              colors={['rgba(26,58,74,0.9)', 'rgba(22,48,64,0.4)', 'rgba(20,44,60,0)']}
              positions={[0, 0.5, 1]}
            />
          </Circle>
        </Group>
      </Group>
      <RoundedRect
        x={x + 0.75}
        y={y + 0.75}
        width={w - 1.5}
        height={h - 1.5}
        r={r - 0.75}
        style="stroke"
        strokeWidth={1.5}
        color="rgba(60,54,78,0.8)"
      />

      {/* Only the stretch of border near the light is lit: a radial gradient centred on it paints the stroke. */}
      <Group opacity={p}>
        <RoundedRect x={x} y={y} width={w} height={h} r={r} style="stroke" strokeWidth={4}>
          <RadialGradient
            c={lightCenter}
            r={RIM_REACH}
            colors={['rgba(64,170,240,0.75)', 'rgba(64,170,240,0.3)', 'rgba(64,170,240,0)']}
            positions={[0, 0.45, 1]}
          />
          <Blur blur={3.5} />
        </RoundedRect>
        <RoundedRect x={x + 1} y={y + 1} width={w - 2} height={h - 2} r={r - 1} style="stroke" strokeWidth={2}>
          <RadialGradient
            c={lightCenter}
            r={RIM_REACH}
            colors={['#56b8f6', 'rgba(60,164,236,0.85)', 'rgba(60,164,236,0)']}
            positions={[0, 0.5, 1]}
          />
        </RoundedRect>
      </Group>
    </Canvas>
  );
}

/** "JOIN TODAY!" pill on deep navy: a cyan light travels around the border inside a soft nebula with drifting dust. */
export function GalaxyButton({ label = 'JOIN TODAY!', onPress, autoPlay = false, lingerMs = 1600 }: Props) {
  const reducedMotion = useReducedMotion();
  const [size, setSize] = useState<{ w: number; h: number } | null>(null);
  const [pressed, setPressed] = useState(false);
  const [hovered, setHovered] = useState(false);
  const [lingering, setLingering] = useState(false);
  const lingerTimer = useRef<ReturnType<typeof setTimeout> | null>(null);

  const lit = autoPlay || pressed || hovered || lingering;

  const p = useSharedValue(0);
  // Measured in laps around the border.
  const loop = useSharedValue(STILL_LAP);

  useEffect(() => {
    if (lit) {
      if (!reducedMotion) {
        // Restarting from the current value keeps the light where it was.
        loop.set(withRepeat(withTiming(loop.get() + 1, { duration: LAP_MS, easing: Easing.linear }), -1, false));
      }
      p.set(withTiming(1, { duration: 420, easing: EASE_OUT }));
    } else {
      p.set(
        withTiming(0, { duration: 600, easing: EASE_OUT }, (finished) => {
          if (finished) cancelAnimation(loop);
        }),
      );
    }
  }, [lit, reducedMotion, p, loop]);

  useEffect(
    () => () => {
      if (lingerTimer.current) clearTimeout(lingerTimer.current);
    },
    [],
  );

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
        if (lingerTimer.current) clearTimeout(lingerTimer.current);
        setPressed(true);
        setLingering(true);
        if (process.env.EXPO_OS !== 'web') {
          Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
        }
      }}
      onPressOut={() => {
        setPressed(false);
        lingerTimer.current = setTimeout(() => setLingering(false), lingerMs);
      }}
      onHoverIn={() => setHovered(true)}
      onHoverOut={() => setHovered(false)}
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
            <Surface w={size.w} h={size.h} p={p} loop={loop} />
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
    paddingHorizontal: PAD_X,
    alignItems: 'center',
    justifyContent: 'center',
  },
  surface: {
    position: 'absolute',
    left: -BLEED,
    top: -BLEED,
  },
  label: {
    color: '#dcdade',
    fontSize: 15,
    fontWeight: '400',
    letterSpacing: 1,
  },
});
