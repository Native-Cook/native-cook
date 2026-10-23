import { useCallback, useState } from 'react';
import { StyleSheet, Text, View } from 'react-native';
import Animated, { type SharedValue, useAnimatedReaction, useAnimatedStyle, useDerivedValue } from 'react-native-reanimated';
import { scheduleOnRN } from 'react-native-worklets';

import { PRISM } from './prism-theme';
import { prismCaption, TUMBLE } from './prism-timeline';

/** Extra height above and below the line, so the blur is not cut off at the layer's edge. */
const BLEED = 16;
/**
 * iOS has no blur filter for a view, but a text shadow is a blurred copy of the glyphs. A soft copy
 * is therefore a line of text set this far above its clipped layer, whose shadow falls back into it.
 */
const SHIFT = 120;

export function PrismCaption({ clock, reduced, fontSize, lineHeight, k }: {
  clock: SharedValue<number>; reduced: boolean; fontSize: number; lineHeight: number; k: number;
}) {
  const [index, setIndex] = useState(0);
  const changeCaption = useCallback((tumble: number) => setIndex(tumble % PRISM.captions.length), []);
  useAnimatedReaction(() => Math.floor(clock.get() / TUMBLE), (next, previous) => {
    if (next !== previous) scheduleOnRN(changeCaption, next);
  });
  const envelope = useDerivedValue(() => prismCaption(clock.get(), reduced));
  const sharpStyle = useAnimatedStyle(() => ({ opacity: PRISM.captionAlpha * envelope.get().opacity *
    Math.max(0, 1 - 2 * envelope.get().progress) }));
  const softStyle = useAnimatedStyle(() => ({ opacity: PRISM.captionAlpha * envelope.get().opacity *
    (1 - Math.abs(2 * envelope.get().progress - 1)) }));
  const blurredStyle = useAnimatedStyle(() => ({ opacity: PRISM.captionAlpha * envelope.get().opacity *
    Math.max(0, 2 * envelope.get().progress - 1) }));
  const type = { fontSize, lineHeight, letterSpacing: PRISM.caption.tracking * k };
  const copy = PRISM.captions[index];
  const line = (radius: number, fade: object) => <Animated.View pointerEvents="none"
    accessibilityElementsHidden importantForAccessibility="no-hide-descendants"
    style={[styles.layer, { height: lineHeight + 2 * BLEED }, fade]}>
    <Text allowFontScaling={false} numberOfLines={1} accessible={false} style={[styles.text, type, radius > 0 ?
      { top: -SHIFT, height: SHIFT + lineHeight + 2 * BLEED, paddingTop: BLEED, textShadowColor: PRISM.text,
        textShadowOffset: { width: 0, height: SHIFT }, textShadowRadius: radius * k } :
      { top: BLEED }]}>{copy}</Text>
  </Animated.View>;
  return <View pointerEvents="none" accessible accessibilityLabel={copy} accessibilityLiveRegion="polite"
    style={{ height: lineHeight }}>
    {line(0, sharpStyle)}
    {line(4, softStyle)}
    {line(9, blurredStyle)}
  </View>;
}
const styles = StyleSheet.create({
  layer: { position: 'absolute', left: 0, right: 0, top: -BLEED, overflow: 'hidden' },
  text: { position: 'absolute', left: 0, right: 0, textAlign: 'center', color: PRISM.text, fontWeight: '400',
    includeFontPadding: false },
});
