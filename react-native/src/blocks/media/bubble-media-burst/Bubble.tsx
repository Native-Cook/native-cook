import { Canvas, Circle, LinearGradient, Path, PathOp, RadialGradient, Skia, vec } from '@shopify/react-native-skia';
import { Image } from 'expo-image';
import { useMemo } from 'react';
import { type GestureResponderEvent, Pressable, StyleSheet, View } from 'react-native';
import Animated, { type SharedValue, useAnimatedStyle } from 'react-native-reanimated';

import { type Events, PICK_SCALE, type Place, type Slot, clamp01, pickAmount, pose } from './motion';

export type BubbleItem = {
  /** Read out by screen readers and passed to `onSelect`. */
  title: string;
  photo: string;
  /** Shown while the photo loads. */
  tint: string;
  /** The light the glass catches along its bottom edge. */
  glow: string;
};

// The glass, in shares of the bubble's diameter: the rim darkens, a crescent of light lies along the
// top left with a glint in it, and a coloured reflection along the bottom right.
const VIGNETTE_STOPS = [0, 0.55, 0.8, 0.94, 1];
const VIGNETTE = ['rgba(0,0,0,0)', 'rgba(0,0,0,0)', 'rgba(0,0,0,0.1)', 'rgba(0,0,0,0.32)', 'rgba(0,0,0,0.22)'];
const SHINE_OUTER = 0.465;
const SHINE_INNER = { x: 0.628, y: 0.678, r: 0.61 };
const SHINE = ['rgba(255,255,255,0.4)', 'rgba(255,255,255,0)'];
const GLINT = { x: 0.3, y: 0.2, r: 0.07 };
const GLINT_COLORS = ['rgba(255,255,255,0.55)', 'rgba(255,255,255,0)'];
const REFLECT_OUTER = 0.47;
const REFLECT_INNER = { x: 0.45, y: 0.306, r: 0.61 };
const REFLECT_ALPHA = 0.5;
const RIM = 'rgba(255,255,255,0.25)';
// The soft shadow under each bubble.
const SHADOW = 'rgba(0,0,0,0.12)';
const SHADOW_Y = 6;
const SHADOW_BLUR = 24;
// A picked bubble: a white ring on its edge and a check on its rim at the top right.
const RING = 2;
const BADGE = 24;
const BADGE_FILL = '#1a1a1a';
const BADGE_BORDER = 2;
const CHECK = 'M7.5 12.3L10.6 15.3L16.6 9.1';
const BADGE_AT = Math.SQRT1_2 / 2;

/** `#rrggbb` at an opacity. */
function withAlpha(hex: string, alpha: number) {
  const n = parseInt(hex.slice(1), 16);
  return `rgba(${(n >> 16) & 255},${(n >> 8) & 255},${n & 255},${alpha})`;
}

function crescent(size: number, outer: number, inner: { x: number; y: number; r: number }) {
  const a = Skia.Path.Circle(size / 2, size / 2, outer * size);
  const b = Skia.Path.Circle(inner.x * size, inner.y * size, inner.r * size);
  return Skia.Path.MakeFromOp(a, b, PathOp.Difference) ?? a;
}

/** The glass drawn over a photo: rim shade, top light, glint, bottom reflection and a fine rim. */
function Glass({ size, glow }: { size: number; glow: string }) {
  const r = size / 2;
  const paths = useMemo(
    () => ({
      shine: crescent(size, SHINE_OUTER, SHINE_INNER),
      reflect: crescent(size, REFLECT_OUTER, REFLECT_INNER),
    }),
    [size],
  );
  return (
    <Canvas style={{ width: size, height: size }} pointerEvents="none">
      <Circle cx={r} cy={r} r={r}>
        <RadialGradient c={vec(r, r)} r={r} colors={VIGNETTE} positions={VIGNETTE_STOPS} />
      </Circle>
      <Path path={paths.reflect}>
        <LinearGradient
          start={vec(0.62 * size, 0.99 * size)}
          end={vec(0.5 * size, 0.62 * size)}
          colors={[withAlpha(glow, REFLECT_ALPHA), withAlpha(glow, 0)]}
        />
      </Path>
      <Path path={paths.shine}>
        <LinearGradient start={vec(0.16 * size, 0.1 * size)} end={vec(0.5 * size, 0.5 * size)} colors={SHINE} />
      </Path>
      <Circle cx={GLINT.x * size} cy={GLINT.y * size} r={GLINT.r * size}>
        <RadialGradient c={vec(GLINT.x * size, GLINT.y * size)} r={GLINT.r * size} colors={GLINT_COLORS} />
      </Circle>
      <Circle cx={r} cy={r} r={r - 0.5} style="stroke" strokeWidth={1} color={RIM} />
    </Canvas>
  );
}

function Badge() {
  return (
    <Canvas style={styles.badgeCanvas}>
      <Circle cx={BADGE / 2} cy={BADGE / 2} r={BADGE / 2 - BADGE_BORDER / 2} color={BADGE_FILL} />
      <Circle cx={BADGE / 2} cy={BADGE / 2} r={BADGE / 2 - BADGE_BORDER / 2} style="stroke" strokeWidth={BADGE_BORDER} color="#ffffff" />
      <Path path={CHECK} style="stroke" strokeWidth={2} strokeCap="round" strokeJoin="round" color="#ffffff" />
    </Canvas>
  );
}

type Props = {
  index: number;
  item: BubbleItem;
  slot: Slot;
  place: Place;
  /** Its diameter on this screen. */
  size: number;
  clock: SharedValue<number>;
  events: SharedValue<Events>;
  still: boolean;
  open: boolean;
  picked: boolean;
  onPress: () => void;
};

/** One photo in a glass bubble, placed and sized by the clock. */
export function Bubble({ index, item, slot, place, size, clock, events, still, open, picked, onPress }: Props) {
  const bubbleStyle = useAnimatedStyle(() => {
    const now = clock.get();
    const e = events.get();
    const p = pose(slot, place, now, e.openAt, e.closeAt, still);
    const pick = still ? 0 : pickAmount(e.pickOn[index], e.pickAt[index], now, still);
    return {
      opacity: p.opacity,
      transform: [{ translateX: p.x - place.restX }, { translateY: p.y - place.restY }, { scale: p.scale * (1 + (PICK_SCALE - 1) * pick) }],
    };
  });
  const ringStyle = useAnimatedStyle(() => {
    const e = events.get();
    return {
      opacity: clamp01(pickAmount(e.pickOn[index], e.pickAt[index], clock.get(), still)),
    };
  });
  const badgeStyle = useAnimatedStyle(() => {
    const e = events.get();
    const pick = pickAmount(e.pickOn[index], e.pickAt[index], clock.get(), still);
    return {
      opacity: clamp01(pick * 2),
      transform: [{ scale: still ? 1 : Math.max(0, pick) }],
    };
  });

  // Only a touch inside the circle counts: the corners of its square belong to its neighbours.
  const press = (event: GestureResponderEvent) => {
    const { locationX, locationY } = event.nativeEvent;
    if ((locationX - size / 2) ** 2 + (locationY - size / 2) ** 2 <= (size / 2) ** 2) onPress();
  };

  return (
    <Animated.View
      pointerEvents={open ? 'box-none' : 'none'}
      style={[
        styles.bubble,
        {
          left: place.restX - size / 2,
          top: place.restY - size / 2,
          width: size,
          height: size,
        },
        bubbleStyle,
      ]}
    >
      <Pressable
        style={StyleSheet.absoluteFill}
        onPress={press}
        accessibilityRole="button"
        accessibilityLabel={item.title}
        accessibilityState={{ selected: picked }}
      >
        <View style={[styles.shadow, { borderRadius: size / 2, backgroundColor: item.tint }]} />
        <View style={[styles.photo, { borderRadius: size / 2, backgroundColor: item.tint }]}>
          <Image source={{ uri: item.photo }} style={StyleSheet.absoluteFill} contentFit="cover" transition={200} />
        </View>
        <View style={StyleSheet.absoluteFill} pointerEvents="none">
          <Glass size={size} glow={item.glow} />
        </View>
        <Animated.View pointerEvents="none" style={[styles.ring, { borderRadius: size / 2 }, ringStyle]} />
        <Animated.View
          pointerEvents="none"
          style={[
            styles.badge,
            {
              left: size / 2 + size * BADGE_AT - BADGE / 2,
              top: size / 2 - size * BADGE_AT - BADGE / 2,
            },
            badgeStyle,
          ]}
        >
          <Badge />
        </Animated.View>
      </Pressable>
    </Animated.View>
  );
}

const styles = StyleSheet.create({
  bubble: {
    position: 'absolute',
  },
  shadow: {
    position: 'absolute',
    left: 0,
    right: 0,
    top: 0,
    bottom: 0,
    boxShadow: [{ offsetX: 0, offsetY: SHADOW_Y, blurRadius: SHADOW_BLUR, color: SHADOW }],
  },
  photo: {
    position: 'absolute',
    left: 0,
    right: 0,
    top: 0,
    bottom: 0,
    overflow: 'hidden',
  },
  ring: {
    position: 'absolute',
    left: 0,
    right: 0,
    top: 0,
    bottom: 0,
    borderWidth: RING,
    borderColor: '#ffffff',
  },
  badge: {
    position: 'absolute',
    width: BADGE,
    height: BADGE,
  },
  badgeCanvas: {
    width: BADGE,
    height: BADGE,
  },
});
