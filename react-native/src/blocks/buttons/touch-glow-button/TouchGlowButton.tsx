import {
  Blur,
  BlurMask,
  Canvas,
  Circle,
  Group,
  LinearGradient,
  RadialGradient,
  RoundedRect,
  rect,
  rrect,
  vec,
} from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { useMemo, useState } from 'react';
import { type GestureResponderEvent, type LayoutChangeEvent, Pressable, StyleSheet, Text, View } from 'react-native';
import Animated, {
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
  /** Lets the light wander around the button by itself; a finger still takes it over. */
  autoPlay?: boolean;
};

const HEIGHT = 64;
const PAD_X = 58;
const RADIUS = 14;
// The canvas overflows the button by this much on every side to fit the glow underneath.
const BLEED = 40;
const GLOW_RADIUS = 84;
const RIM_RADIUS = 100;
// Where the wandering light is frozen when the system asks for reduced motion.
const STILL_TIME = 1.2;

const CSS_EASE_OUT = cubicBezier(0.23, 1, 0.32, 1);

const VIOLET = '124,58,237';
const BLUE = '48,86,220';
const RIM = '124,92,255';

function Surface({
  w,
  h,
  px,
  py,
  lit,
}: {
  w: number;
  h: number;
  px: SharedValue<number>;
  py: SharedValue<number>;
  lit: SharedValue<number>;
}) {
  const x = BLEED;
  const y = BLEED;

  const clip = useMemo(() => rrect(rect(x, y, w, h), RADIUS, RADIUS), [x, y, w, h]);

  const pointer = useDerivedValue(() => vec(x + px.get(), y + py.get()));
  const follow = useDerivedValue(() => [{ translateX: x + px.get() }, { translateY: y + py.get() }]);
  // The glow is violet on the left of the button and turns blue towards the right.
  const violet = useDerivedValue(() => lit.get() * (1 - Math.min(1, Math.max(0, px.get() / w))));
  const blue = useDerivedValue(() => lit.get() * Math.min(1, Math.max(0, px.get() / w)));
  const under = useDerivedValue(() => 0.6 + 0.4 * lit.get());
  const bloom = useDerivedValue(() => 0.6 * lit.get());

  const rimColors = ['rgba(206,194,255,1)', `rgba(${RIM},0.55)`, `rgba(${RIM},0)`];
  const rimStops = [0, 0.45, 1];

  return (
    <Canvas style={{ width: w + BLEED * 2, height: h + BLEED * 2 }}>
      <RoundedRect x={x + 8} y={y + 12} width={w - 16} height={h - 4} r={RADIUS} color="rgba(84,44,214,0.34)" opacity={under}>
        <BlurMask blur={16} style="normal" />
      </RoundedRect>
      <RoundedRect x={x} y={y} width={w} height={h} r={RADIUS} color="#0e0428" />

      <Group clip={clip}>
        <Group transform={follow}>
          <Circle cx={0} cy={0} r={GLOW_RADIUS} opacity={violet}>
            <RadialGradient
              c={vec(0, 0)}
              r={GLOW_RADIUS}
              colors={[`rgba(${VIOLET},0.62)`, `rgba(${VIOLET},0.26)`, `rgba(${VIOLET},0)`]}
              positions={[0, 0.45, 1]}
            />
          </Circle>
          <Circle cx={0} cy={0} r={GLOW_RADIUS} opacity={blue}>
            <RadialGradient
              c={vec(0, 0)}
              r={GLOW_RADIUS}
              colors={[`rgba(${BLUE},0.7)`, `rgba(${BLUE},0.28)`, `rgba(${BLUE},0)`]}
              positions={[0, 0.45, 1]}
            />
          </Circle>
        </Group>
      </Group>

      {/* Resting border: a little brighter towards the bottom-left and top-right corners. */}
      <RoundedRect x={x + 0.5} y={y + 0.5} width={w - 1} height={h - 1} r={RADIUS - 0.5} style="stroke" strokeWidth={1}>
        <LinearGradient
          start={vec(x, y + h)}
          end={vec(x + w, y)}
          colors={[`rgba(${RIM},0.5)`, `rgba(${RIM},0.13)`, `rgba(${RIM},0.42)`]}
          positions={[0, 0.5, 1]}
        />
      </RoundedRect>

      {/* The stretch of border nearest the light. */}
      <RoundedRect x={x} y={y} width={w} height={h} r={RADIUS} style="stroke" strokeWidth={4} opacity={bloom}>
        <RadialGradient c={pointer} r={RIM_RADIUS} colors={rimColors} positions={rimStops} />
        <Blur blur={4} />
      </RoundedRect>
      <RoundedRect
        x={x + 0.75}
        y={y + 0.75}
        width={w - 1.5}
        height={h - 1.5}
        r={RADIUS - 0.75}
        style="stroke"
        strokeWidth={1.5}
        opacity={lit}
      >
        <RadialGradient c={pointer} r={RIM_RADIUS} colors={rimColors} positions={rimStops} />
      </RoundedRect>
    </Canvas>
  );
}

/**
 * Dark indigo button whose border and inner glow light up around a point: under the finger while
 * it is held (drag to move the light), or wandering by itself with `autoPlay`.
 */
export function TouchGlowButton({ label = 'Button', onPress, autoPlay = false }: Props) {
  const reducedMotion = useReducedMotion();
  const [size, setSize] = useState<{ w: number; h: number } | null>(null);
  const [pressed, setPressed] = useState(false);

  const time = useSharedValue(STILL_TIME);
  // Finger position in the button's own coordinates, and how much it has taken the light over.
  const fx = useSharedValue(0);
  const fy = useSharedValue(0);
  const touch = useSharedValue(0);

  const w = size?.w ?? 0;
  const h = size?.h ?? HEIGHT;

  useFrameCallback((frame) => {
    const dt = Math.min(frame.timeSincePreviousFrame ?? 16, 64) / 1000;
    time.set(time.get() + dt);
  }, autoPlay && !reducedMotion);

  // By itself the light sweeps side to side while bobbing between the top and bottom edges.
  const px = useDerivedValue(() => {
    if (!autoPlay) return fx.get();
    const ax = w / 2 + (w / 2 - 6) * Math.sin(0.9 * time.get());
    return ax + (fx.get() - ax) * touch.get();
  });
  const py = useDerivedValue(() => {
    if (!autoPlay) return fy.get();
    const ay = h / 2 + (h / 2 - 4) * Math.sin(1.7 * time.get() + 1);
    return ay + (fy.get() - ay) * touch.get();
  });
  const lit = useDerivedValue(() => (autoPlay ? 1 : touch.get()));

  const track = (e: GestureResponderEvent) => {
    fx.set(e.nativeEvent.locationX);
    fy.set(e.nativeEvent.locationY);
  };

  const onLayout = (e: LayoutChangeEvent) => {
    const { width, height } = e.nativeEvent.layout;
    setSize((prev) => (prev && prev.w === width && prev.h === height ? prev : { w: width, h: height }));
  };

  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={label}
      onPress={onPress}
      onPressIn={(e) => {
        track(e);
        setPressed(true);
        touch.set(withTiming(1, { duration: 180 }));
        if (process.env.EXPO_OS !== 'web') {
          Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
        }
      }}
      onPressMove={track}
      onPressOut={() => {
        setPressed(false);
        // Lingers so the glow isn't hidden under the thumb the whole time.
        touch.set(withTiming(0, { duration: 700 }));
      }}
      pressRetentionOffset={16}
    >
      {/* No pointer events inside, so touch locations are always relative to the button itself. */}
      <Animated.View
        pointerEvents="none"
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
          <View style={styles.surface}>
            <Surface w={size.w} h={size.h} px={px} py={py} lit={lit} />
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
    color: '#fafaff',
    fontSize: 22,
    fontWeight: '600',
  },
});
