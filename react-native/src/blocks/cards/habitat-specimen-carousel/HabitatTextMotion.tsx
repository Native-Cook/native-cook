import { memo, type ReactNode } from 'react';
import { View, type StyleProp, type ViewStyle } from 'react-native';
import Animated, { type SharedValue, useAnimatedStyle } from 'react-native-reanimated';

import { BLUR_EDGE_MAX_BLUR, BLUR_EDGE_MAX_SHIFT } from '../../text/blur-edge-type-in/blurEdgeMotion';

/**
 * Erase-then-type timing over one crossing, sampled from the carousel position so a drag scrubs
 * it, a reversal is exact and the 2 → 0 wrap is seamless. Blur and drift use BlurEdgeTypeIn's numbers.
 */
export function crossingFronts(position: number, count: number) {
  'worklet';
  const wrapped = ((position % count) + count) % count;
  const from = Math.floor(wrapped) % count;
  const f = wrapped - Math.floor(wrapped);
  const erase = 1 - Math.max(0, Math.min(1, f / 0.55));
  const type = Math.max(0, Math.min(1, (f - 0.38) / 0.62));
  return { from, to: (from + 1) % count, erase, type, settled: f < 0.0005 };
}

const SwapLayer = memo(function SwapLayer({ index, count, height, position, reduced, selected, children, layerStyle }: {
  index: number; count: number; height: number; position: SharedValue<number>; reduced: SharedValue<boolean>;
  selected: boolean; children: ReactNode; layerStyle?: StyleProp<ViewStyle>;
}) {
  // The same erase-then-type envelope, for a whole value: it blurs out drifting left, the next one
  // sharpens in from the right, at BlurEdgeTypeIn's blur and drift per em of the value's height.
  const pose = useAnimatedStyle(() => {
    if (reduced.get()) {
      const shown = Math.round(((position.get() % count) + count) % count) % count === index;
      return { opacity: shown ? 1 : 0, filter: 'blur(0px)', transform: [{ translateX: 0 }] };
    }
    const fronts = crossingFronts(position.get(), count);
    let u = 0;
    let direction = 0;
    if (index === fronts.from) { u = fronts.erase; direction = -1; }
    else if (index === fronts.to && !fronts.settled) { u = fronts.type; direction = 1; }
    const eased = u * u * (3 - 2 * u);
    const k = (1 - eased) * (1 - eased);
    const em = Math.min(height * 0.8, 18);
    return {
      opacity: eased,
      filter: `blur(${(BLUR_EDGE_MAX_BLUR * em * k).toFixed(2)}px)`,
      transform: [{ translateX: direction * BLUR_EDGE_MAX_SHIFT * em * k }],
    };
  });
  return <Animated.View pointerEvents={selected ? 'box-none' : 'none'} accessibilityElementsHidden={!selected}
    importantForAccessibility={selected ? 'auto' : 'no-hide-descendants'}
    style={[{ position: 'absolute', left: 0, right: 0, top: 0, height }, layerStyle, pose]}>{children}</Animated.View>;
});

/** Every other value that belongs to a specimen swaps with the same letter motion, as one block. */
export function TextSwap({ position, reduced, height, selectedIndex, items, style, layerStyle }: {
  position: SharedValue<number>; reduced: SharedValue<boolean>; height: number; selectedIndex: number;
  items: readonly ReactNode[]; style?: StyleProp<ViewStyle>; layerStyle?: StyleProp<ViewStyle>;
}) {
  return <View style={[{ height }, style]}>
    {items.map((item, index) => <SwapLayer key={index} index={index} count={items.length} height={height} position={position}
      reduced={reduced} selected={index === selectedIndex} layerStyle={layerStyle}>{item}</SwapLayer>)}
  </View>;
}
