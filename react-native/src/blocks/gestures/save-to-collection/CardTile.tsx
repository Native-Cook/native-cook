/** One card of the pile: it deals in when its pile appears, carries the corner bookmark once saved, dims while lifted. */
import { Image } from 'expo-image';
import { memo, useEffect } from 'react';
import { LinearGradient } from 'expo-linear-gradient';
import { StyleSheet, View } from 'react-native';
import Animated, { ReduceMotion, useAnimatedStyle, useDerivedValue, useSharedValue, withDelay, withTiming, type SharedValue } from 'react-native-reanimated';

import { SOFT_PAD, type Card } from './gallery';
import type { Rect } from './layout';
import { EASE_OUT, SAVE, TIMING } from './motion';
import { COLOR, LAYOUT } from './theme';

type Props = {
  card: Card;
  index: number;
  rect: Rect;
  order: number; // position in the deal
  lifted: SharedValue<number>; // index of the card being dragged, or -1
  press: SharedValue<number>; // press feedback, for the card at pressCard
  pressCard: SharedValue<number>;
  clock: SharedValue<number>; // ambient float
  reduceMotion: boolean;
};

const NEVER = { reduceMotion: ReduceMotion.Never };

export const CardTile = memo(function CardTile({ card, index, rect, order, lifted, press, pressCard, clock, reduceMotion }: Props) {
  const dealt = useSharedValue(0);
  useEffect(() => {
    // The source deals its cards in one after another: each fades up from slightly smaller.
    dealt.set(withDelay(order * TIMING.dealStagger, withTiming(1, { duration: TIMING.deal, easing: EASE_OUT, ...NEVER })));
  }, [dealt, order]);
  const pad = SOFT_PAD * (rect.w / card.w);
  const dim = useDerivedValue(() => withTiming(lifted.get() === index ? 1 : 0, { duration: TIMING.dim, ...NEVER }));
  const style = useAnimatedStyle(() => {
    const d = dealt.get();
    // Ambient: each card drifts on its own phase; far (blurred) cards drift more, as depth would.
    const phase = index * 1.7;
    const amp = SAVE.floatAmp * (1 + card.blur / 10);
    const fy = reduceMotion ? 0 : amp * Math.sin((2 * Math.PI * clock.get()) / SAVE.floatPeriod + phase);
    return {
      opacity: d * (1 - 0.65 * dim.get()),
      transform: reduceMotion ? [] : [{ translateY: (1 - d) * TIMING.dealDrop + fy }, { scale: (0.9 + 0.1 * d) * (pressCard.get() === index ? 1 - 0.03 * press.get() : 1) }],
    };
  });
  return (
    <Animated.View
      style={[styles.shadow, card.blur === 0 && !card.soft && styles.near, { left: rect.x, top: rect.y, width: rect.w, height: rect.h }, style]}
      pointerEvents="none"
    >
      {card.soft ? (
        // At rest the card sits in the source's depth of field: a baked, pre-blurred layer (soft edge included).
        // Lifting it brings it into focus: the dragged copy is drawn sharp.
        <Image
          source={card.soft}
          style={{ position: 'absolute', left: -pad, top: -pad, width: rect.w + 2 * pad, height: rect.h + 2 * pad }}
          contentFit="fill"
          transition={0}
        />
      ) : (
        <View style={styles.card}>
          <Image source={card.art} style={StyleSheet.absoluteFill} contentFit="cover" blurRadius={card.blur} transition={0} />
          {/* Material: a hairline edge and a faint top light, as printed cards under a lamp. */}
          <LinearGradient colors={COLOR.sheen} start={{ x: 0, y: 0 }} end={{ x: 0, y: 0.6 }} style={StyleSheet.absoluteFill} />
          <View style={styles.edge} />
        </View>
      )}
    </Animated.View>
  );
});

const styles = StyleSheet.create({
  shadow: { position: 'absolute', borderRadius: LAYOUT.cardRadius },
  // Near cards cast a soft shadow on the black (depth); far, blurred ones do not.
  near: { shadowColor: '#000000', shadowOpacity: 0.55, shadowRadius: 18, shadowOffset: { width: 0, height: 12 } },
  card: { flex: 1, borderRadius: LAYOUT.cardRadius, overflow: 'hidden', backgroundColor: COLOR.card, borderCurve: 'continuous' },
  edge: { ...StyleSheet.absoluteFill, borderRadius: LAYOUT.cardRadius, borderWidth: StyleSheet.hairlineWidth, borderColor: COLOR.cardEdge },
});
