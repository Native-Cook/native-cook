/** A single non-interactive caption, exchanged after its old line has left. */
import { useEffect } from 'react';
import { StyleSheet } from 'react-native';
import Animated, { Easing, ReduceMotion, useAnimatedStyle, useSharedValue, withDelay, withTiming } from 'react-native-reanimated';

import { HINT_Y, stageFit } from './tear-contract';

export const TEAR_FONT = { ArchivoExpanded: require('./assets/ArchivoExpanded-Regular.ttf') };
export type HintKind = 'tear' | 'peek' | 'revealed' | 'quiet';
const CAPTIONS = { tear: 'DRAG ACROSS THE TOP TO TEAR', peek: 'PULL THE CARD OUT', revealed: 'TAP THE CARD TO COLLECT' };
const TIMING = { easing: Easing.bezier(0.23, 1, 0.32, 1), reduceMotion: ReduceMotion.Never };

type Props = { width: number; height: number; kind: HintKind; reduceMotion: boolean };

function HintLine({ text, active, k, reduceMotion }: { text: string; active: boolean; k: number; reduceMotion: boolean }) {
  const opacity = useSharedValue(0);
  const y = useSharedValue(6);
  useEffect(() => {
    if (active) {
      opacity.set(0);
      y.set(reduceMotion ? 0 : 6);
      opacity.set(withDelay(160, withTiming(1, { ...TIMING, duration: 240 }), ReduceMotion.Never));
      y.set(withDelay(160, withTiming(0, { ...TIMING, duration: 240 }), ReduceMotion.Never));
    } else {
      opacity.set(withTiming(0, { ...TIMING, duration: 160 }));
      y.set(withTiming(reduceMotion ? 0 : -6, { ...TIMING, duration: 160 }));
    }
  }, [active, opacity, reduceMotion, y]);
  const motion = useAnimatedStyle(() => ({ opacity: opacity.get(), transform: [{ translateY: y.get() }] }));
  return <Animated.Text accessible={false} selectable={false} numberOfLines={1} allowFontScaling={false}
    style={[styles.text, { fontSize: 11 * Math.max(k, 0.9), lineHeight: 18 * Math.max(k, 0.9) }, motion]}>{text}</Animated.Text>;
}

export function TearHint({ width, height, kind, reduceMotion }: Props) {
  const fit = stageFit(width, height);
  return <Animated.View pointerEvents="none" accessible={false} accessibilityElementsHidden importantForAccessibility="no-hide-descendants"
    style={[styles.row, { top: fit.y + HINT_Y * fit.k - 9 * Math.max(fit.k, 0.9) }]}>
    <HintLine text={CAPTIONS.tear} active={kind === 'tear'} k={fit.k} reduceMotion={reduceMotion} />
    <HintLine text={CAPTIONS.peek} active={kind === 'peek'} k={fit.k} reduceMotion={reduceMotion} />
    <HintLine text={CAPTIONS.revealed} active={kind === 'revealed'} k={fit.k} reduceMotion={reduceMotion} />
  </Animated.View>;
}

const styles = StyleSheet.create({
  row: { position: 'absolute', left: 8, right: 8, height: 24 },
  text: { position: 'absolute', left: 0, right: 0, textAlign: 'center', fontFamily: 'ArchivoExpanded',
    letterSpacing: 1.4, color: 'rgba(196,214,255,0.56)' },
});
