import {
  Blur,
  BlurMask,
  Canvas,
  Group,
  LinearGradient,
  Path,
  RadialGradient,
  RoundedRect,
  SweepGradient,
  rect,
  rrect,
  vec,
} from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { useEffect, useMemo, useRef, useState } from 'react';
import { type LayoutChangeEvent, Pressable, StyleSheet, View } from 'react-native';
import Animated, {
  Easing,
  type SharedValue,
  cancelAnimation,
  cubicBezier,
  interpolateColor,
  useAnimatedStyle,
  useDerivedValue,
  useReducedMotion,
  useSharedValue,
  withRepeat,
  withTiming,
} from 'react-native-reanimated';

type Props = {
  label?: string;
  onPress?: () => void;
  /** Force the lit state. When omitted the button lights up on press/hover and lingers briefly. */
  active?: boolean;
  /** How long the glow stays after the finger lifts, so it isn't hidden under the thumb. */
  lingerMs?: number;
};

const HEIGHT = 52;
const PAD_LEFT = 20;
const PAD_RIGHT = 22;
const ICON_SLOT = 24;
const ICON_GAP = 10;
const RING = 4;
// The canvas overflows the button by this much on every side to fit the aura and the star field.
const AURA = 120;
const LOOP_MS = 6000;

const EASE_OUT = Easing.bezier(0.23, 1, 0.32, 1);
const CSS_EASE_OUT = cubicBezier(0.23, 1, 0.32, 1);
const TAU = Math.PI * 2;

function sparkle(cx: number, cy: number, r: number, k = 0.24) {
  const c = r * k;
  return (
    `M${cx} ${cy - r}Q${cx + c} ${cy - c} ${cx + r} ${cy}` +
    `Q${cx + c} ${cy + c} ${cx} ${cy + r}` +
    `Q${cx - c} ${cy + c} ${cx - r} ${cy}` +
    `Q${cx - c} ${cy - c} ${cx} ${cy - r}Z`
  );
}

function dot(cx: number, cy: number, r: number) {
  return `M${cx - r} ${cy}a${r} ${r} 0 1 0 ${r * 2} 0a${r} ${r} 0 1 0 ${-r * 2} 0Z`;
}

function mulberry32(seed: number) {
  let a = seed;
  return () => {
    a = (a + 0x6d2b79f5) | 0;
    let t = Math.imul(a ^ (a >>> 15), 1 | a);
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

const STAR_GROUPS = 4;

// Scatter sparkles around (never on top of) the button, split into groups that twinkle out of phase.
function starField(w: number, h: number) {
  const rand = mulberry32(7);
  const groups = Array.from({ length: STAR_GROUPS }, () => '');
  const cw = w + AURA * 2;
  const ch = h + AURA * 2;
  let placed = 0;
  while (placed < 34) {
    const x = rand() * cw;
    const y = rand() * ch;
    const insideButton =
      x > AURA - 14 && x < AURA + w + 14 && y > AURA - 14 && y < AURA + h + 14;
    // Keep stars inside the aura ellipse so they fade with it instead of hitting the canvas edge.
    const nx = (x - cw / 2) / (cw / 2);
    const ny = (y - ch / 2) / (ch / 2);
    if (insideButton || nx * nx + ny * ny > 0.82) continue;
    const size = rand();
    groups[placed % STAR_GROUPS] +=
      size > 0.55 ? sparkle(x, y, 1.4 + size * 2.4) : dot(x, y, 0.5 + size * 1.2);
    placed++;
  }
  return groups;
}

function StarGroup({
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
  const opacity = useDerivedValue(() => {
    const wave = 0.5 + 0.5 * Math.sin(TAU * ((index % 2) + 2) * loop.get() + index * 1.7);
    return p.get() * (0.18 + 0.72 * wave);
  });
  return <Path path={path} color="#c4a8ff" opacity={opacity} />;
}

function Surface({
  w,
  h,
  p,
  loop,
}: {
  w: number;
  h: number;
  p: SharedValue<number>;
  loop: SharedValue<number>;
}) {
  const cw = w + AURA * 2;
  const ch = h + AURA * 2;
  const x = AURA;
  const y = AURA;
  const cx = x + w / 2;
  const cy = y + h / 2;
  const r = h / 2;

  const stars = useMemo(() => starField(w, h), [w, h]);
  const clip = useMemo(() => rrect(rect(x, y, w, h), r, r), [x, y, w, h, r]);

  const ix = x + PAD_LEFT + ICON_SLOT / 2 + 3;
  const icon = useMemo(
    () => ({
      main: sparkle(ix, cy, 7.4),
      low: sparkle(ix - 8.4, cy + 7.4, 3.8),
      high: sparkle(ix - 8.2, cy - 8, 1.9),
    }),
    [ix, cy],
  );

  // One lap of the rim highlight every 3s.
  const shine = useDerivedValue(() => [{ rotate: TAU * 2 * loop.get() }]);
  const mainStar = useDerivedValue(() => [
    { scale: 1 - 0.14 * p.get() * (0.5 + 0.5 * Math.sin(TAU * 4 * loop.get())) },
  ]);
  const lowStar = useDerivedValue(() => [
    { scale: 1 + 0.3 * p.get() * Math.sin(TAU * 4 * loop.get() + 2.2) },
  ]);
  const highStar = useDerivedValue(() => [
    { scale: 1 + 0.55 * p.get() * Math.sin(TAU * 4 * loop.get() + 4.4) },
  ]);
  const smallStarLit = useDerivedValue(() => p.get() * 0.75);

  return (
    <Canvas style={{ width: cw, height: ch }}>
      <Group opacity={p}>
        <Path path={dot(cx, cy, 1)} transform={[{ scaleX: cw / 2 }, { scaleY: ch / 2 }]} origin={vec(cx, cy)}>
          <RadialGradient
            c={vec(cx, cy)}
            r={1}
            colors={['rgba(104,58,240,0.62)', 'rgba(72,36,190,0.3)', 'rgba(44,20,128,0)']}
            positions={[0, 0.45, 1]}
          />
        </Path>
        <RoundedRect x={x - 2} y={y - 2} width={w + 4} height={h + 4} r={r + 2} color="rgba(120,74,255,0.85)">
          <BlurMask blur={18} style="normal" />
        </RoundedRect>
      </Group>

      {stars.map((path, i) => (
        <StarGroup key={i} path={path} index={i} p={p} loop={loop} />
      ))}

      <Group opacity={p}>
        <RoundedRect
          x={x - RING}
          y={y - RING}
          width={w + RING * 2}
          height={h + RING * 2}
          r={r + RING}
          color="rgba(112,74,246,0.42)"
        />
      </Group>

      <RoundedRect x={x} y={y} width={w} height={h} r={r} color="#1c1c1c" />

      <Group opacity={p} clip={clip}>
        <RoundedRect x={x} y={y} width={w} height={h} r={r}>
          <LinearGradient start={vec(cx, y)} end={vec(cx, y + h)} colors={['#5a2ff0', '#6a37f7']} />
        </RoundedRect>
        <Path path={dot(cx, y + h + 4, 1)} transform={[{ scaleX: w * 0.46 }, { scaleY: h * 0.62 }]} origin={vec(cx, y + h + 4)}>
          <RadialGradient
            c={vec(cx, y + h + 4)}
            r={1}
            colors={['rgba(196,138,255,0.95)', 'rgba(160,110,255,0.4)', 'rgba(130,90,255,0)']}
            positions={[0, 0.5, 1]}
          />
        </Path>
        <RoundedRect x={x + 0.5} y={y + 0.5} width={w - 1} height={h - 1} r={r - 0.5} style="stroke" strokeWidth={1}>
          <LinearGradient
            start={vec(cx, y)}
            end={vec(cx, y + h)}
            colors={['rgba(214,196,255,0.5)', 'rgba(214,196,255,0.06)', 'rgba(214,196,255,0.22)']}
          />
        </RoundedRect>
      </Group>

      <Group opacity={p}>
        <RoundedRect x={x} y={y} width={w} height={h} r={r} style="stroke" strokeWidth={3}>
          <SweepGradient
            c={vec(cx, cy)}
            origin={vec(cx, cy)}
            transform={shine}
            colors={['rgba(255,255,255,0)', 'rgba(255,255,255,0.8)', 'rgba(255,255,255,0)', 'rgba(255,255,255,0)']}
            positions={[0.62, 0.75, 0.88, 1]}
          />
          <Blur blur={3} />
        </RoundedRect>
        <RoundedRect x={x + 0.5} y={y + 0.5} width={w - 1} height={h - 1} r={r - 0.5} style="stroke" strokeWidth={1.2}>
          <SweepGradient
            c={vec(cx, cy)}
            origin={vec(cx, cy)}
            transform={shine}
            colors={['rgba(255,255,255,0)', '#ffffff', 'rgba(255,255,255,0)', 'rgba(255,255,255,0)']}
            positions={[0.64, 0.75, 0.86, 1]}
          />
        </RoundedRect>
      </Group>

      <Group transform={mainStar} origin={vec(ix, cy)}>
        <Path path={icon.main} color="#5e5e5e" />
        <Path path={icon.main} color="#ffffff" opacity={p} />
      </Group>
      <Group transform={lowStar} origin={vec(ix - 8.4, cy + 7.4)}>
        <Path path={icon.low} color="#484848" />
        <Path path={icon.low} color="#cdb6ff" opacity={smallStarLit} />
      </Group>
      <Group transform={highStar} origin={vec(ix - 8.2, cy - 8)}>
        <Path path={icon.high} color="#3c3c3c" />
        <Path path={icon.high} color="#cdb6ff" opacity={smallStarLit} />
      </Group>
    </Canvas>
  );
}

export function GenerateButton({ label = 'Generate Site', onPress, active, lingerMs = 1600 }: Props) {
  const reducedMotion = useReducedMotion();
  const [size, setSize] = useState<{ w: number; h: number } | null>(null);
  const [pressed, setPressed] = useState(false);
  const [hovered, setHovered] = useState(false);
  const [lingering, setLingering] = useState(false);
  const lingerTimer = useRef<ReturnType<typeof setTimeout> | null>(null);

  const lit = active ?? (pressed || hovered || lingering);

  const p = useSharedValue(0);
  const loop = useSharedValue(0);

  useEffect(() => {
    if (lit) {
      if (!reducedMotion) {
        // Restarting from the current value keeps every sin(2πn·loop) seamless across activations.
        loop.set(
          withRepeat(withTiming(loop.get() + 1, { duration: LOOP_MS, easing: Easing.linear }), -1, false),
        );
      }
      p.set(withTiming(1, { duration: 420, easing: EASE_OUT }));
    } else {
      p.set(
        withTiming(0, { duration: 520, easing: EASE_OUT }, (finished) => {
          if (finished) cancelAnimation(loop);
        }),
      );
    }
  }, [lit, reducedMotion, p, loop]);

  useEffect(() => () => {
    if (lingerTimer.current) clearTimeout(lingerTimer.current);
  }, []);

  const labelStyle = useAnimatedStyle(() => ({
    color: interpolateColor(p.get(), [0, 1], ['#6b6b6b', '#ffffff']),
  }));

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
        <View style={styles.iconSlot} />
        <Animated.Text style={[styles.label, labelStyle]} numberOfLines={1}>
          {label}
        </Animated.Text>
      </Animated.View>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  button: {
    height: HEIGHT,
    paddingLeft: PAD_LEFT,
    paddingRight: PAD_RIGHT,
    flexDirection: 'row',
    alignItems: 'center',
  },
  surface: {
    position: 'absolute',
    left: -AURA,
    top: -AURA,
  },
  iconSlot: {
    width: ICON_SLOT,
    marginRight: ICON_GAP,
  },
  label: {
    fontSize: 17,
    fontWeight: '600',
    letterSpacing: 0.1,
  },
});
