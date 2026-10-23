import {
  BlurMask,
  Canvas,
  Circle,
  Group,
  LinearGradient,
  Path,
  RadialGradient,
  RoundedRect,
  rect,
  rrect,
  vec,
} from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { useEffect, useMemo, useState } from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import Animated, {
  type SharedValue,
  cubicBezier,
  useDerivedValue,
  useFrameCallback,
  useReducedMotion,
  useSharedValue,
} from 'react-native-reanimated';

type Props = {
  /** Caption under the percentage. */
  label?: string;
  onPress?: () => void;
  /** Loads from 0 to 100 by itself, rests, and starts over. */
  autoPlay?: boolean;
  /** Controlled progress, 0…1. When omitted a tap runs one load from 0 to 100. */
  progress?: number;
};

const WIDTH = 172;
const HEIGHT = 118;
const RADIUS = 54;
// The canvas overflows the button by this much on every side to fit the shadow.
const BLEED = 28;
const LOAD_MS = 5200;
const REST_MS = 1300;
// Where the colours are frozen when the system asks for reduced motion.
const STILL_TIME = 2.4;

const CSS_EASE_OUT = cubicBezier(0.23, 1, 0.32, 1);

type BlobSpec = {
  rgb: string;
  /** Start angle on the rim (radians, clockwise from 3 o'clock) and angular speed in rad/s. */
  angle: number;
  speed: number;
  /** Brightness swell: rate in rad/s and phase. */
  pulse: number;
  phase: number;
  radius: number;
};

// Blurry colours that travel around the inside of the rim and swell in and out of view.
const BLOBS: BlobSpec[] = [
  { rgb: '58,96,190', angle: 3.4, speed: 0.42, pulse: 0.9, phase: 0, radius: 78 },
  { rgb: '190,62,150', angle: 1.3, speed: 0.34, pulse: 0.7, phase: 2.1, radius: 72 },
  { rgb: '150,48,48', angle: 2.5, speed: -0.27, pulse: 1.1, phase: 4, radius: 66 },
  { rgb: '34,122,92', angle: 0.2, speed: 0.31, pulse: 0.8, phase: 5.2, radius: 60 },
];

function ellipse(cx: number, cy: number, rx: number, ry: number) {
  return `M${cx - rx} ${cy}a${rx} ${ry} 0 1 0 ${rx * 2} 0a${rx} ${ry} 0 1 0 ${-rx * 2} 0Z`;
}

// Loading that surges and stalls a little instead of counting at a constant rate.
function loadCurve(t: number) {
  return t - 0.04 * Math.sin(6 * Math.PI * t);
}

function Blob({
  spec,
  cx,
  cy,
  rx,
  ry,
  time,
}: {
  spec: BlobSpec;
  cx: number;
  cy: number;
  rx: number;
  ry: number;
  time: SharedValue<number>;
}) {
  const transform = useDerivedValue(() => {
    const a = spec.angle + spec.speed * time.get();
    return [{ translateX: cx + rx * Math.cos(a) }, { translateY: cy + ry * Math.sin(a) }];
  });
  const opacity = useDerivedValue(
    () => 0.25 + 0.75 * (0.5 + 0.5 * Math.sin(spec.pulse * time.get() + spec.phase)),
  );
  return (
    <Group transform={transform} opacity={opacity}>
      <Circle cx={0} cy={0} r={spec.radius}>
        <RadialGradient
          c={vec(0, 0)}
          r={spec.radius}
          colors={[`rgba(${spec.rgb},0.85)`, `rgba(${spec.rgb},0.4)`, `rgba(${spec.rgb},0)`]}
          positions={[0, 0.5, 1]}
        />
      </Circle>
    </Group>
  );
}

function Surface({ time }: { time: SharedValue<number> }) {
  const x = BLEED;
  const y = BLEED;
  const w = WIDTH;
  const h = HEIGHT;
  const cx = x + w / 2;
  const cy = y + h / 2;

  const clip = useMemo(() => rrect(rect(x, y, w, h), RADIUS, RADIUS), [x, y, w, h]);
  const shade = useMemo(() => ellipse(cx, cy, w * 0.52, h * 0.52), [cx, cy, w, h]);

  return (
    <Canvas style={{ width: w + BLEED * 2, height: h + BLEED * 2 }}>
      <RoundedRect x={x} y={y + 8} width={w} height={h} r={RADIUS} color="rgba(0,0,0,0.4)">
        <BlurMask blur={12} style="normal" />
      </RoundedRect>
      <RoundedRect x={x} y={y} width={w} height={h} r={RADIUS} color="#1b1b1b" />

      <Group clip={clip}>
        {BLOBS.map((spec) => (
          <Blob key={spec.rgb} spec={spec} cx={cx} cy={cy} rx={w / 2 - 12} ry={h / 2 - 6} time={time} />
        ))}
        {/* Keeps the middle dark so the colours read as light caught in the rim of the glass. */}
        <Path path={shade}>
          <RadialGradient
            c={vec(cx, cy)}
            r={w * 0.52}
            transform={[{ scaleY: h / w }]}
            origin={vec(cx, cy)}
            colors={['rgba(24,24,24,0.94)', 'rgba(24,24,24,0.72)', 'rgba(24,24,24,0)']}
            positions={[0, 0.42, 1]}
          />
        </Path>
        <RoundedRect x={x} y={y} width={w} height={h} r={RADIUS}>
          <LinearGradient
            start={vec(cx, y)}
            end={vec(cx, y + h)}
            colors={['rgba(255,255,255,0.07)', 'rgba(255,255,255,0)', 'rgba(255,255,255,0.02)']}
            positions={[0, 0.5, 1]}
          />
        </RoundedRect>
      </Group>

      <RoundedRect x={x + 0.5} y={y + 0.5} width={w - 1} height={h - 1} r={RADIUS - 0.5} style="stroke" strokeWidth={1}>
        <LinearGradient
          start={vec(cx, y)}
          end={vec(cx, y + h)}
          colors={['rgba(255,255,255,0.3)', 'rgba(255,255,255,0.07)', 'rgba(255,255,255,0.16)']}
          positions={[0, 0.5, 1]}
        />
      </RoundedRect>
    </Canvas>
  );
}

/**
 * Dark liquid-glass capsule with a large percentage and a caption. Blurry blue, magenta, red and
 * green light drifts around the inside of its rim while the number counts up.
 */
export function GlassLoadingButton({ label = 'LOADING', onPress, autoPlay = false, progress }: Props) {
  const reducedMotion = useReducedMotion();
  const [pressed, setPressed] = useState(false);
  const [percent, setPercent] = useState(0);
  // Bumped to (re)start a load; 0 means nothing has been started yet.
  const [run, setRun] = useState(0);

  const time = useSharedValue(STILL_TIME);

  useFrameCallback((frame) => {
    const dt = Math.min(frame.timeSincePreviousFrame ?? 16, 64) / 1000;
    time.set(time.get() + dt);
  }, !reducedMotion);

  useEffect(() => {
    if (run === 0 && !autoPlay) return;
    let rest: ReturnType<typeof setTimeout> | null = null;
    const startedAt = Date.now();
    // The number is real text, so it is updated on a timer (20×/s), not per frame.
    const tick = setInterval(() => {
      const t = Math.min(1, (Date.now() - startedAt) / LOAD_MS);
      setPercent(Math.round(100 * loadCurve(t)));
      if (t < 1) return;
      clearInterval(tick);
      if (autoPlay) rest = setTimeout(() => setRun((n) => n + 1), REST_MS);
    }, 50);
    return () => {
      clearInterval(tick);
      if (rest) clearTimeout(rest);
    };
  }, [run, autoPlay]);

  const shown = progress == null ? percent : Math.round(100 * Math.min(1, Math.max(0, progress)));

  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={`${label} ${shown}%`}
      onPress={() => {
        onPress?.();
        setRun((n) => n + 1);
      }}
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
          styles.button,
          {
            transform: [{ scale: pressed ? 0.97 : 1 }],
            transitionProperty: 'transform',
            transitionDuration: 130,
            transitionTimingFunction: CSS_EASE_OUT,
          },
        ]}
      >
        <View pointerEvents="none" style={styles.surface}>
          <Surface time={time} />
        </View>
        <View style={styles.value}>
          <Text style={styles.number}>{shown}</Text>
          <Text style={styles.percent}>%</Text>
        </View>
        <Text style={styles.label} numberOfLines={1}>
          {label}
        </Text>
      </Animated.View>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  button: {
    width: WIDTH,
    height: HEIGHT,
    alignItems: 'center',
    justifyContent: 'center',
  },
  surface: {
    position: 'absolute',
    left: -BLEED,
    top: -BLEED,
  },
  value: {
    flexDirection: 'row',
    alignItems: 'baseline',
  },
  number: {
    color: '#dcdcde',
    fontSize: 48,
    lineHeight: 50,
    fontWeight: '600',
    fontVariant: ['tabular-nums'],
    textShadowColor: 'rgba(255,255,255,0.28)',
    textShadowOffset: { width: 0, height: 0 },
    textShadowRadius: 8,
  },
  percent: {
    color: '#cfcfd2',
    fontSize: 29,
    fontWeight: '600',
    marginLeft: 1,
  },
  label: {
    color: '#ececec',
    fontSize: 13,
    lineHeight: 15,
    fontWeight: '700',
    letterSpacing: 0.3,
    marginTop: -3,
    marginBottom: 4,
  },
});
