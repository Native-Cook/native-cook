import { Canvas, Group, LinearGradient as SkiaLinearGradient, RadialGradient, Rect, vec } from '@shopify/react-native-skia';
import { LinearGradient } from 'expo-linear-gradient';
import { createContext, useContext, useRef, type ReactNode, type RefObject } from 'react';
import { StyleSheet, View, type LayoutChangeEvent } from 'react-native';
import Animated, { useAnimatedStyle, useDerivedValue, useSharedValue, type SharedValue } from 'react-native-reanimated';

import {
  BAND,
  SLANT,
  WIPE_SOFT,
  barAlpha,
  barBloom,
  barMorph,
  clamp01,
  contentAlpha,
  kAt,
  shimmerAlpha,
  shimmerX,
  wipe,
} from './skeletonMotion';

// ---------------------------------------------------------------- the group

export type SkeletonGroupValue = {
  /** Seconds since the content turned into its skeleton (negative: never). */
  sinceHide: SharedValue<number>;
  /** Seconds since the content started to resolve (negative: still loading). */
  sinceShow: SharedValue<number>;
  /** The view every skeleton measures itself against, so the shimmer lines up across them. */
  container: RefObject<View | null>;
  /** The group's width plus SLANT × its height: what the band has to cross. */
  span: number;
  /** Colours */
  bar: string;
  shine: string;
  /** The shine's colour at no opacity (a plain `transparent` would darken the band's edges). */
  shineClear: string;
  /** Opaque: it hides a cover completely. */
  block: string;
  blockClear: string;
};

const SkeletonContext = createContext<SkeletonGroupValue | null>(null);

export function SkeletonGroup({ value, children }: { value: SkeletonGroupValue; children: ReactNode }) {
  return <SkeletonContext.Provider value={value}>{children}</SkeletonContext.Provider>;
}

function useSkeletonGroup() {
  const group = useContext(SkeletonContext);
  if (!group) throw new Error('Skeleton must be inside a SkeletonGroup');
  return group;
}

// ---------------------------------------------------------------- a line (or any block) of content

type SkeletonProps = {
  /** Place in the top-to-bottom stagger (fractions allowed). */
  order: number;
  /** Width of the bar while loading, pt. */
  placeholder: number;
  /** Height of the bar, pt. */
  barHeight: number;
  radius?: number;
  /** A right-aligned child keeps its right edge: the bar grows to the left. */
  align?: 'left' | 'right';
  children: ReactNode;
};

/**
 * Wraps one line of content and measures it. While loading it shows a bar of `placeholder` width
 * with the group's shimmer running through it; to resolve, the bar stretches and settles to the
 * child's exact width, the child fades in inside it, and the bar dissolves.
 */
export function Skeleton({ order, placeholder, barHeight, radius, align = 'left', children }: SkeletonProps) {
  const group = useSkeletonGroup();
  const { sinceHide, sinceShow, container, span } = group;
  const node = useRef<View>(null);
  const contentW = useSharedValue(placeholder);
  const left = useSharedValue(0); // in the group
  const centre = useSharedValue(0);

  // the content's own width, and where it sits in the group (for the shimmer)
  const onLayout = (e: LayoutChangeEvent) => {
    const { width, height } = e.nativeEvent.layout;
    contentW.set(width);
    const root = container.current;
    const self = node.current;
    if (!root || !self) return;
    self.measureLayout(root, (x, y) => {
      left.set(x);
      centre.set(y + height / 2);
    });
  };

  const barStyle = useAnimatedStyle(() => {
    const k = kAt(order, sinceHide.get(), sinceShow.get());
    const width = placeholder + (contentW.get() - placeholder) * barMorph(k);
    return { width: Math.max(barHeight, width), opacity: barAlpha(k), transform: [{ scaleY: barBloom(k) }] };
  });
  const contentStyle = useAnimatedStyle(() => {
    const k = kAt(order, sinceHide.get(), sinceShow.get());
    return { opacity: contentAlpha(k) };
  });
  const bandStyle = useAnimatedStyle(() => {
    const hidden = sinceHide.get();
    const k = kAt(order, hidden, sinceShow.get());
    const width = Math.max(barHeight, placeholder + (contentW.get() - placeholder) * barMorph(k));
    const barLeft = align === 'right' ? left.get() + contentW.get() - width : left.get();
    return {
      opacity: shimmerAlpha(k, hidden),
      transform: [{ translateX: shimmerX(hidden, span) - SLANT * centre.get() - barLeft - BAND / 2 }],
    };
  });

  return (
    <View onLayout={onLayout} ref={node} style={align === 'right' ? styles.wrapRight : styles.wrapLeft}>
      <View pointerEvents="none" style={[styles.holder, align === 'right' ? styles.holderRight : styles.holderLeft]}>
        <Animated.View style={[{ height: barHeight, borderRadius: radius ?? barHeight / 2, backgroundColor: group.bar }, styles.bar, barStyle]}>
          <Animated.View style={[styles.band, bandStyle]}>
            <LinearGradient colors={[group.shineClear, group.shine, group.shineClear]} end={{ x: 1, y: 0.5 }} start={{ x: 0, y: 0.5 }} style={StyleSheet.absoluteFill} />
          </Animated.View>
        </Animated.View>
      </View>
      <Animated.View style={contentStyle}>{children}</Animated.View>
    </View>
  );
}

// ---------------------------------------------------------------- a cover

type SkeletonCoverProps = {
  order: number;
  size: number;
  radius: number;
  /** Skia elements drawing the cover in a size × size box. */
  children: ReactNode;
};

/**
 * A square of Skia artwork under a placeholder. The placeholder carries the same shimmer band as
 * the bars and opens with a soft radial wipe from the centre.
 */
export function SkeletonCover({ order, size, radius, children }: SkeletonCoverProps) {
  const group = useSkeletonGroup();
  const { sinceHide, sinceShow, container, span } = group;
  const node = useRef<View>(null);
  const left = useSharedValue(0);
  const top = useSharedValue(0);

  const onLayout = () => {
    const root = container.current;
    const self = node.current;
    if (!root || !self) return;
    self.measureLayout(root, (x, y) => {
      left.set(x);
      top.set(y);
    });
  };

  // the wipe: clear inside the first stop, the placeholder outside the second
  const stops = useDerivedValue(() => {
    const p = wipe(kAt(order, sinceHide.get(), sinceShow.get())) * (1 + WIPE_SOFT);
    return [0, clamp01(p - WIPE_SOFT), clamp01(p), 1];
  });
  const coverOpacity = useDerivedValue(() => (kAt(order, sinceHide.get(), sinceShow.get()) >= 1 ? 0 : 1));
  const bandOpacity = useDerivedValue(() => {
    const hidden = sinceHide.get();
    return shimmerAlpha(kAt(order, hidden, sinceShow.get()), hidden);
  });
  const bandTransform = useDerivedValue(() => [{ translateX: shimmerX(sinceHide.get(), span) - SLANT * top.get() - left.get() }, { skewX: -SLANT }]);

  return (
    <View onLayout={onLayout} ref={node} style={{ width: size, height: size, borderRadius: radius, borderCurve: 'continuous', overflow: 'hidden', backgroundColor: group.block }}>
      <Canvas style={{ width: size, height: size }}>
        {children}
        <Rect height={size} opacity={coverOpacity} width={size} x={0} y={0}>
          <RadialGradient c={vec(size / 2, size / 2)} colors={[group.blockClear, group.blockClear, group.block, group.block]} positions={stops} r={size * 0.75} />
        </Rect>
        <Group opacity={bandOpacity} transform={bandTransform}>
          <Rect height={size} width={BAND} x={-BAND / 2} y={0}>
            <SkiaLinearGradient colors={[group.shineClear, group.shine, group.shineClear]} end={vec(BAND / 2, 0)} start={vec(-BAND / 2, 0)} />
          </Rect>
        </Group>
      </Canvas>
    </View>
  );
}

const styles = StyleSheet.create({
  wrapLeft: { alignSelf: 'flex-start', maxWidth: '100%' },
  wrapRight: { alignSelf: 'flex-end', maxWidth: '100%' },
  holder: { position: 'absolute', top: 0, bottom: 0, justifyContent: 'center' },
  holderLeft: { left: 0, alignItems: 'flex-start' },
  holderRight: { right: 0, alignItems: 'flex-end' },
  bar: { overflow: 'hidden' },
  band: { position: 'absolute', left: 0, top: 0, bottom: 0, width: BAND },
});
