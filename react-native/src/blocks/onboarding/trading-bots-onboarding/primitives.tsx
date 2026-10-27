import MaskedView from '@react-native-masked-view/masked-view';
import { Canvas, LinearGradient as SkiaGradient, RoundedRect, vec } from '@shopify/react-native-skia';
import { LinearGradient } from 'expo-linear-gradient';
import { SymbolView, type SymbolViewProps } from 'expo-symbols';
import { type PropsWithChildren, type ReactNode, useEffect, useState } from 'react';
import { Pressable, StyleSheet, Text, View, type StyleProp, type TextStyle, type ViewStyle } from 'react-native';
import Animated, {
  type SharedValue,
  useAnimatedStyle,
  useSharedValue,
  withDelay,
  withSpring,
  withTiming,
} from 'react-native-reanimated';

import { EASE_OUT, REVEAL_CURVE, WIPE_CURVE, hex, palette, spring, white } from './theme';

type SymbolName = SymbolViewProps['name'];

// MARK: - Placement (SwiftUI `.position` is the centre of a view, in canvas points)

type BoxProps = PropsWithChildren<{ x: number; y: number; style?: StyleProp<ViewStyle>; box?: number }>;

/** Centres its content on (x, y), whatever the content's size. */
export function Centered({ x, y, children, style, box = 700 }: BoxProps) {
  return (
    <View
      pointerEvents="box-none"
      style={[{ position: 'absolute', left: x - box / 2, top: y - box / 2, width: box, height: box, alignItems: 'center', justifyContent: 'center' }, style]}>
      {children}
    </View>
  );
}

/** `.frame(width:, alignment: .leading).position(x: left + width / 2, y:)`: left edge at `left`, centred on `y`. */
export function Leading({ left, y, children, style }: PropsWithChildren<{ left: number; y: number; style?: StyleProp<ViewStyle> }>) {
  return (
    <View pointerEvents="box-none" style={[{ position: 'absolute', left, top: y - 100, height: 200, justifyContent: 'center', alignItems: 'flex-start' }, style]}>
      {children}
    </View>
  );
}

/** A fixed-size box whose centre is (x, y). */
export function Frame({ x, y, width, height, children, style }: PropsWithChildren<{ x: number; y: number; width: number; height: number; style?: StyleProp<ViewStyle> }>) {
  return <View pointerEvents="box-none" style={[{ position: 'absolute', left: x - width / 2, top: y - height / 2, width, height }, style]}>{children}</View>;
}

// MARK: - Text

type LabelProps = PropsWithChildren<{
  size: number;
  weight?: TextStyle['fontWeight'];
  color?: string;
  tracking?: number;
  style?: StyleProp<TextStyle>;
}>;

export function Label({ size, weight = '400', color = palette.ink, tracking = 0, style, children }: LabelProps) {
  return <Text style={[{ fontSize: size, fontWeight: weight, color, letterSpacing: tracking, includeFontPadding: false }, style]}>{children}</Text>;
}

/** Number formatted like `value.formatted(.number.precision(.fractionLength(decimals)))` in en_US. */
export function formatAmount(value: number, decimals: number) {
  const fixed = Math.abs(value).toFixed(decimals);
  const [whole, fraction] = fixed.split('.');
  const grouped = whole.replace(/\B(?=(\d{3})+(?!\d))/g, ',');
  return (value < 0 ? '-' : '') + grouped + (fraction ? `.${fraction}` : '');
}

/**
 * Drives a counter the way the reference's numbers settle: most of the distance in a fast
 * first move (`fastShare` with time constant `fast`), then a long tail (`slow`).
 */
export function useSettle(start: number, target: number, fastShare: number, fast: number, slow: number, duration: number, reduced: boolean, delay = 0) {
  const [value, setValue] = useState(reduced ? target : start);
  useEffect(() => {
    if (reduced) return;
    let frame = 0;
    let began = 0;
    const timer = setTimeout(() => {
      began = performance.now();
      const tick = () => {
        const t = (performance.now() - began) / 1000;
        if (t >= duration) {
          setValue(target);
          return;
        }
        setValue(target - (target - start) * (fastShare * Math.exp(-t / fast) + (1 - fastShare) * Math.exp(-t / slow)));
        frame = requestAnimationFrame(tick);
      };
      tick();
    }, delay * 1000);
    return () => {
      clearTimeout(timer);
      cancelAnimationFrame(frame);
    };
  }, [start, target, fastShare, fast, slow, duration, reduced, delay]);
  return reduced ? target : value;
}

// MARK: - Entrances

/** 0 → 1 once, when the view mounts: a timing curve or a SwiftUI spring, after `delay` seconds. */
export function useEntrance(reduced: boolean, options: { delay?: number; duration?: number; spring?: [number, number]; easing?: typeof REVEAL_CURVE }) {
  const progress = useSharedValue(0);
  const { delay = 0, duration = 0.55, spring: springValues, easing = REVEAL_CURVE } = options;
  const response = springValues?.[0];
  const damping = springValues?.[1];
  useEffect(() => {
    if (reduced) {
      progress.set(withTiming(1, { duration: 200, easing: EASE_OUT }));
      return;
    }
    const animation = response !== undefined && damping !== undefined
      ? withSpring(1, spring(response, damping))
      : withTiming(1, { duration: duration * 1000, easing });
    progress.set(withDelay(delay * 1000, animation));
  }, [delay, duration, easing, progress, reduced, response, damping]);
  return progress;
}

type RevealProps = PropsWithChildren<{ reduced: boolean; delay?: number; y?: number; duration?: number; style?: StyleProp<ViewStyle> }>;

/**
 * `tbReveal`: fades in and settles up by `y`. The SwiftUI version also unblurs; React Native
 * cannot blur a live view cheaply, so the blur is left out.
 */
export function Reveal({ reduced, delay = 0, y = 12, duration = 0.55, style, children }: RevealProps) {
  const progress = useEntrance(reduced, { delay, duration });
  const animated = useAnimatedStyle(() => ({
    opacity: progress.get(),
    transform: [{ translateY: reduced ? 0 : (1 - progress.get()) * y }],
  }));
  return <Animated.View style={[style, animated]}>{children}</Animated.View>;
}

/** `tbWipe`: reveals its content left to right behind a soft edge. */
export function Wipe({ reduced, delay = 0, duration = 0.5, children }: PropsWithChildren<{ reduced: boolean; delay?: number; duration?: number }>) {
  const [width, setWidth] = useState(0);
  const sweep = useSharedValue(reduced ? 1 : 0);
  const fade = useSharedValue(0);
  useEffect(() => {
    fade.set(withDelay(reduced ? 0 : delay * 1000, withTiming(1, { duration: reduced ? 200 : 250, easing: EASE_OUT })));
    if (!reduced) sweep.set(withDelay(delay * 1000, withTiming(1, { duration: duration * 1000, easing: WIPE_CURVE })));
  }, [delay, duration, fade, reduced, sweep]);
  const maskStyle = useAnimatedStyle(() => ({ transform: [{ translateX: -(1 - sweep.get()) * width * 1.9 }] }));
  const fadeStyle = useAnimatedStyle(() => ({ opacity: fade.get() }));
  return (
    <Animated.View style={fadeStyle} onLayout={(event) => setWidth(event.nativeEvent.layout.width)}>
      <MaskedView
        maskElement={
          <Animated.View style={[{ width: width * 3, height: '100%' }, maskStyle]}>
            <LinearGradient colors={['#fff', '#fff', 'transparent']} locations={[0, 0.5, 0.62]} start={{ x: 0, y: 0.5 }} end={{ x: 1, y: 0.5 }} style={StyleSheet.absoluteFill} />
          </Animated.View>
        }>
        {children}
      </MaskedView>
    </Animated.View>
  );
}

/** Pops in from 40 % with a lively spring (the little marks next to headings). */
export function PopIn({ reduced, delay, from = 0.4, response = 0.45, damping = 0.6, children, style }: PropsWithChildren<{ reduced: boolean; delay: number; from?: number; response?: number; damping?: number; style?: StyleProp<ViewStyle> }>) {
  const progress = useEntrance(reduced, { delay, spring: [response, damping] });
  const animated = useAnimatedStyle(() => ({
    opacity: Math.min(1, progress.get()),
    transform: [{ scale: reduced ? 1 : from + (1 - from) * progress.get() }],
  }));
  return <Animated.View style={[style, animated]}>{children}</Animated.View>;
}

// MARK: - Headings

const HEADING: TextStyle = { fontSize: 32.8, fontWeight: '500', letterSpacing: -0.8, color: palette.ink, textAlign: 'center', includeFontPadding: false };
export const headingText = HEADING;

/** Two-line centred heading used by four of the six scenes (SF Pro Medium 32.8). */
export function Heading({ first, second, reduced, x = 200, y = 603.5 }: { first: string; second: string; reduced: boolean; x?: number; y?: number }) {
  return (
    <Centered x={x} y={y}>
      <Reveal reduced={reduced} delay={0.05} y={8}>
        <Text style={HEADING}>{first}</Text>
      </Reveal>
      <Reveal reduced={reduced} delay={0.22} y={10} style={{ marginTop: -3 }}>
        <Text style={HEADING}>{second}</Text>
      </Reveal>
    </Centered>
  );
}

export function Caption({ text, color = palette.caption }: { text: string; color?: string }) {
  return <Text style={{ fontSize: 14.4, letterSpacing: -0.15, lineHeight: 20, color, textAlign: 'center' }}>{text}</Text>;
}

// MARK: - Buttons

/** `TradingBotsPressStyle`: dims and shrinks a little while pressed. */
export function PressScale({ onPress, children, style, accessibilityLabel, selected, onLongPress }: PropsWithChildren<{
  onPress?: () => void;
  onLongPress?: () => void;
  style?: StyleProp<ViewStyle>;
  accessibilityLabel?: string;
  selected?: boolean;
}>) {
  return (
    <Pressable
      onPress={onPress}
      onLongPress={onLongPress}
      delayLongPress={800}
      accessibilityRole="button"
      accessibilityLabel={accessibilityLabel}
      accessibilityState={selected === undefined ? undefined : { selected }}
      style={({ pressed }) => [style, { opacity: pressed ? 0.9 : 1, transform: [{ scale: pressed ? 0.97 : 1 }] }]}>
      {children}
    </Pressable>
  );
}

type PrimaryProps = {
  title: string;
  pro: boolean;
  /** 0…1, the grey veil while a scene changes. */
  dim: SharedValue<number>;
  /** Shows the pressed swell without a touch (the QA walkthrough). */
  pressed: boolean;
  onPress: () => void;
  icon?: ReactNode;
};

const CTA_W = 343;
const CTA_H = 62;

/**
 * The big white CTA: a pill with a pale lower lip, so it reads as a raised key. Pressing
 * swells it 6 %, as the reference does under the finger.
 */
export function PrimaryButton({ title, pro, dim, pressed, onPress, icon }: PrimaryProps) {
  const [touching, setTouching] = useState(false);
  const swell = useSharedValue(0);
  useEffect(() => {
    swell.set(withSpring(touching || pressed ? 1 : 0, spring(0.28, 0.7)));
  }, [pressed, swell, touching]);
  const outer = useAnimatedStyle(() => ({
    transform: [{ scale: 1 + 0.06 * swell.get() }],
    shadowOpacity: (pro ? 0.04 : 0.07) + 0.15 * swell.get(),
    shadowRadius: 14 + 4 * swell.get(),
  }));
  const veil = useAnimatedStyle(() => ({ opacity: 0.42 * dim.get() }));
  const lip = pro ? [hex('#d8c9a8'), hex('#9fb8cc')] : [hex('#e4e4e4'), hex('#cdcdcd')];
  const face = pro ? [hex('#f1e2c4'), hex('#dcdcd2'), hex('#b6cfe2')] : [hex('#fefefe'), hex('#fdfdfd'), hex('#f3f3f3')];
  return (
    <Pressable
      onPress={onPress}
      onPressIn={() => setTouching(true)}
      onPressOut={() => setTouching(false)}
      accessibilityRole="button"
      accessibilityLabel={title}
      testID="trading-primary-cta">
      <Animated.View style={[{ width: CTA_W, height: CTA_H, shadowColor: '#fff', shadowOffset: { width: 0, height: 0 } }, outer]}>
        <Canvas style={StyleSheet.absoluteFill}>
          <RoundedRect x={0} y={0} width={CTA_W} height={CTA_H} r={27}>
            <SkiaGradient start={pro ? vec(0, 0) : vec(0, 0)} end={pro ? vec(CTA_W, 0) : vec(0, CTA_H)} colors={lip} />
          </RoundedRect>
          <RoundedRect x={0} y={0} width={CTA_W} height={CTA_H - 3} r={26}>
            <SkiaGradient start={vec(0, 0)} end={pro ? vec(CTA_W, 0) : vec(0, CTA_H - 3)} colors={face} />
          </RoundedRect>
          <RoundedRect x={0.5} y={0.5} width={CTA_W - 1} height={CTA_H - 4} r={25.5} style="stroke" strokeWidth={1}>
            <SkiaGradient start={vec(0, 0)} end={vec(0, (CTA_H - 3) / 2)} colors={[white(0.9), white(0)]} />
          </RoundedRect>
        </Canvas>
        <View style={[StyleSheet.absoluteFill, { flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: 14, paddingBottom: 2 }]}>
          <Text style={{ fontSize: pro ? 15.6 : 17, fontWeight: pro ? '700' : '600', letterSpacing: -0.2, color: pro ? '#000' : palette.ctaText }}>{title}</Text>
          {icon}
        </View>
        <Animated.View pointerEvents="none" style={[StyleSheet.absoluteFill, { borderRadius: 27, borderCurve: 'continuous', backgroundColor: '#000' }, veil]} />
      </Animated.View>
    </Pressable>
  );
}

// MARK: - Surfaces

type PanelProps = PropsWithChildren<{
  width: number;
  height: number;
  radius: number;
  /** Top-to-bottom (or `horizontal`) fill colours. */
  fill: string[];
  fillLocations?: number[];
  horizontal?: boolean;
  /** Border colours from top to bottom; drawn inside the edge like SwiftUI's `strokeBorder`. */
  stroke?: string[];
  strokeWidth?: number;
  diagonalStroke?: boolean;
  shadow?: { color: string; opacity: number; radius: number; y: number };
  style?: StyleProp<ViewStyle>;
}>;

/** A rounded card: gradient fill, gradient hairline border, optional drop shadow, content on top. */
export function Panel({ width, height, radius, fill, fillLocations, horizontal, stroke, strokeWidth = 1, diagonalStroke, shadow, style, children }: PanelProps) {
  const inset = strokeWidth / 2;
  return (
    <View style={[{ width, height }, style]}>
      {shadow && (
        <View style={{ position: 'absolute', left: 0, top: 0, width, height, borderRadius: radius, borderCurve: 'continuous', backgroundColor: fill[fill.length - 1],
          shadowColor: shadow.color, shadowOpacity: shadow.opacity, shadowRadius: shadow.radius, shadowOffset: { width: 0, height: shadow.y } }} />
      )}
      <Canvas style={{ position: 'absolute', left: 0, top: 0, width, height }} pointerEvents="none">
        <RoundedRect x={0} y={0} width={width} height={height} r={radius}>
          <SkiaGradient start={vec(0, 0)} end={horizontal ? vec(width, 0) : vec(0, height)} colors={fill} positions={fillLocations} />
        </RoundedRect>
        {stroke && (
          <RoundedRect x={inset} y={inset} width={width - strokeWidth} height={height - strokeWidth} r={Math.max(0, radius - inset)} style="stroke" strokeWidth={strokeWidth}>
            <SkiaGradient start={vec(0, 0)} end={diagonalStroke ? vec(width, height) : vec(0, height)} colors={stroke} />
          </RoundedRect>
        )}
      </Canvas>
      {children}
    </View>
  );
}

/** An SF SFSymbol on iOS; a small dot elsewhere, where the glyph is decorative. */
export function SFSymbol({ name, size, color = palette.ink, weight = 'regular', style }: { name: SymbolName; size: number; color?: string; weight?: 'regular' | 'medium' | 'semibold' | 'bold'; style?: StyleProp<ViewStyle> }) {
  return (
    <SymbolView name={name} size={size} weight={weight} tintColor={color} style={[{ width: size * 1.25, height: size * 1.25 }, style]}
      fallback={<View style={[{ width: size * 0.6, height: size * 0.6, borderRadius: size, borderWidth: 1.2, borderColor: color, margin: size * 0.3 }, style]} />} />
  );
}
