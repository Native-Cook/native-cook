import {
  Blur,
  Canvas,
  Group,
  LinearGradient,
  Path,
  RadialGradient,
  RoundedRect,
  Shader,
  Skia,
  rect,
  rrect,
  vec,
} from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { useMemo, useState } from 'react';
import { type LayoutChangeEvent, Pressable, StyleSheet, Text, View } from 'react-native';
import Animated, {
  Easing,
  type SharedValue,
  cubicBezier,
  useAnimatedStyle,
  useDerivedValue,
  useFrameCallback,
  useReducedMotion,
  useSharedValue,
  withTiming,
} from 'react-native-reanimated';

type Props = {
  label?: string;
  onPress?: () => void;
};

const HEIGHT = 60;
const PAD_X = 42;
// The canvas overflows the button by this much on every side to fit the halo behind it.
const BLEED = 110;
// Where the smoke is frozen when the system asks for reduced motion.
const STILL_TIME = 4;
const GLOW_PAD = 16;

const EASE_OUT = Easing.bezier(0.23, 1, 0.32, 1);
const CSS_EASE_OUT = cubicBezier(0.23, 1, 0.32, 1);

// Domain-warped fbm smoke that thickens towards the right, plus a few drifting dust motes.
// `boost` (0..1, driven by press) lets the smoke spill across the whole capsule.
const SMOKE = Skia.RuntimeEffect.Make(`
uniform vec2 origin;
uniform vec2 size;
uniform float time;
uniform float boost;

float hash(vec2 p) {
  p = fract(p * vec2(123.34, 456.21));
  p += dot(p, p + 45.32);
  return fract(p.x * p.y);
}

float noise(vec2 p) {
  vec2 i = floor(p);
  vec2 f = fract(p);
  vec2 u = f * f * (3.0 - 2.0 * f);
  return mix(
    mix(hash(i), hash(i + vec2(1.0, 0.0)), u.x),
    mix(hash(i + vec2(0.0, 1.0)), hash(i + vec2(1.0, 1.0)), u.x),
    u.y
  );
}

float fbm(vec2 p) {
  float v = 0.0;
  float a = 0.5;
  for (int i = 0; i < 4; i++) {
    v += a * noise(p);
    p = vec2(p.x * 0.8 - p.y * 0.6, p.x * 0.6 + p.y * 0.8) * 2.03 + 11.7;
    a *= 0.46;
  }
  return v;
}

vec4 main(vec2 xy) {
  vec2 l = xy - origin;
  vec2 n = l / size;
  vec2 p = l / size.y;
  float t = time;

  vec2 q = vec2(
    fbm(p * 1.3 + vec2(-0.10 * t, 0.04 * t)),
    fbm(p * 1.3 + vec2(4.1 + 0.07 * t, 2.7 - 0.09 * t))
  );
  float f = fbm(p * 1.7 + 2.4 * q + vec2(-0.16 * t, 0.0));
  float wisps = smoothstep(0.3, 0.8, f);
  wisps = wisps * (0.4 + 1.1 * q.x * q.x);

  float mask = smoothstep(mix(0.14, -0.6, boost), 0.96, n.x);
  float haze = 0.26 * mask * q.y;
  float smoke = wisps * mask * (0.35 + 0.65 * mask) * (1.7 + 0.5 * boost) + haze;
  smoke = min(smoke, 0.9);

  float dust = 0.0;
  for (int i = 0; i < 9; i++) {
    float fi = float(i);
    vec2 base = vec2(hash(vec2(fi, 1.3)), hash(vec2(fi, 7.9)));
    base = vec2(0.2 + 0.74 * base.x, 0.16 + 0.68 * base.y);
    vec2 drift = vec2(sin(t * 0.31 + fi * 2.1) * 0.03, cos(t * 0.23 + fi * 1.3) * 0.07);
    float d = distance(l, (base + drift) * size);
    float twinkle = 0.5 + 0.5 * sin(t * 1.1 + fi * 4.0);
    dust += (0.25 + 0.75 * twinkle) * (smoothstep(1.4, 0.5, d) + 0.22 * smoothstep(4.5, 0.0, d));
  }

  float v = clamp(smoke + dust, 0.0, 1.0);
  return vec4(v, v, v, v);
}
`);

function ellipse(cx: number, cy: number, rx: number, ry: number) {
  return `M${cx - rx} ${cy}a${rx} ${ry} 0 1 0 ${rx * 2} 0a${rx} ${ry} 0 1 0 ${-rx * 2} 0Z`;
}

function Surface({
  w,
  h,
  time,
  boost,
}: {
  w: number;
  h: number;
  time: SharedValue<number>;
  boost: SharedValue<number>;
}) {
  const x = BLEED;
  const y = BLEED;
  const r = h / 2;
  const cy = y + h / 2;
  const hx = x + w * 0.6;

  const clip = useMemo(() => rrect(rect(x, y, w, h), r, r), [x, y, w, h, r]);
  const halo = useMemo(() => ellipse(hx, cy, w * 0.92, h * 1.7), [hx, cy, w, h]);

  const uniforms = useDerivedValue(() => ({
    origin: [x, y],
    size: [w, h],
    time: time.get(),
    boost: boost.get(),
  }));

  const haloOpacity = useDerivedValue(
    () => 0.78 + 0.22 * Math.sin(time.get() * 0.8) + 0.6 * boost.get(),
  );
  // The lit stretch of the rim sways a little, as if the light source were drifting;
  // a press pushes its start past the left cap so the whole rim lights up.
  const rimStart = useDerivedValue(() =>
    vec(x + w * (0.2 + 0.09 * Math.sin(time.get() * 0.55) - 1.1 * boost.get()), cy),
  );
  const rimEnd = vec(x + w * 0.8, cy);
  const rimColors = ['rgba(255,255,255,0)', 'rgba(255,255,255,0.5)', '#ffffff'];
  const rimStops = [0, 0.5, 1];

  return (
    <Canvas style={{ width: w + BLEED * 2, height: h + BLEED * 2 }}>
      <Path path={halo} opacity={haloOpacity}>
        <RadialGradient
          c={vec(hx, cy)}
          r={w * 0.92}
          transform={[{ scaleY: (h * 1.7) / (w * 0.92) }]}
          origin={vec(hx, cy)}
          colors={['rgba(255,255,255,0.2)', 'rgba(255,255,255,0.07)', 'rgba(255,255,255,0)']}
          positions={[0, 0.45, 1]}
        />
      </Path>

      <RoundedRect x={x} y={y} width={w} height={h} r={r}>
        <LinearGradient start={vec(x, cy)} end={vec(x + w, cy)} colors={['#060606', '#141414']} />
      </RoundedRect>

      {SMOKE && (
        <Group clip={clip}>
          <RoundedRect x={x} y={y} width={w} height={h} r={r}>
            <Shader source={SMOKE} uniforms={uniforms} />
          </RoundedRect>
        </Group>
      )}

      <RoundedRect
        x={x + 0.5}
        y={y + 0.5}
        width={w - 1}
        height={h - 1}
        r={r - 0.5}
        style="stroke"
        strokeWidth={1}
        color="rgba(255,255,255,0.05)"
      />

      <RoundedRect x={x} y={y} width={w} height={h} r={r} style="stroke" strokeWidth={4} opacity={0.7}>
        <LinearGradient start={rimStart} end={rimEnd} colors={rimColors} positions={rimStops} />
        <Blur blur={4} />
      </RoundedRect>
      <RoundedRect x={x + 0.9} y={y + 0.9} width={w - 1.8} height={h - 1.8} r={r - 0.9} style="stroke" strokeWidth={1.8}>
        <LinearGradient start={rimStart} end={rimEnd} colors={rimColors} positions={rimStops} />
      </RoundedRect>
      <RoundedRect x={x + 4} y={y + 4} width={w - 8} height={h - 8} r={r - 4} style="stroke" strokeWidth={1} opacity={0.3}>
        <LinearGradient start={rimStart} end={rimEnd} colors={rimColors} positions={rimStops} />
      </RoundedRect>
    </Canvas>
  );
}

export function SmokeButton({ label = 'Get Started', onPress }: Props) {
  const reducedMotion = useReducedMotion();
  const [size, setSize] = useState<{ w: number; h: number } | null>(null);
  const [pressed, setPressed] = useState(false);

  const time = useSharedValue(STILL_TIME);
  const boost = useSharedValue(0);

  // Integrating time (rather than reading a clock) lets a press speed the smoke up without a jump.
  useFrameCallback((frame) => {
    const dt = Math.min(frame.timeSincePreviousFrame ?? 16, 64) / 1000;
    time.set(time.get() + dt * (1 + 2.4 * boost.get()));
  }, !reducedMotion);

  const glowStyle = useAnimatedStyle(() => ({
    opacity: Math.min(1, 0.6 + 0.2 * Math.sin(time.get() * 0.8) + 0.4 * boost.get()),
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
        setPressed(true);
        boost.set(withTiming(1, { duration: 380, easing: EASE_OUT }));
        if (process.env.EXPO_OS !== 'web') {
          Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
        }
      }}
      onPressOut={() => {
        setPressed(false);
        boost.set(withTiming(0, { duration: 1100, easing: EASE_OUT }));
      }}
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
            <Surface w={size.w} h={size.h} time={time} boost={boost} />
          </View>
        )}
        <View>
          <Text style={styles.label} numberOfLines={1}>
            {label}
          </Text>
          <Animated.Text
            accessible={false}
            importantForAccessibility="no"
            style={[styles.label, styles.glow, glowStyle]}
            numberOfLines={1}
          >
            {label}
          </Animated.Text>
        </View>
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
    color: '#ffffff',
    fontSize: 17,
    fontWeight: '600',
    letterSpacing: -0.2,
  },
  glow: {
    position: 'absolute',
    // Room for the shadow to spread; without it the blur is clipped to the text box.
    padding: GLOW_PAD,
    left: -GLOW_PAD,
    top: -GLOW_PAD,
    right: -GLOW_PAD,
    textShadowColor: 'rgba(255,255,255,0.95)',
    textShadowOffset: { width: 0, height: 0 },
    textShadowRadius: 9,
  },
});
