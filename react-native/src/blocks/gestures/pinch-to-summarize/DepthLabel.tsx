/**
 * Under the page: while pinching, how deep the summary will be ("Key points" → "Brief" → "TL;DR", a lens slides to
 * the depth the fingers ask for); once released, "Summarizing for you…" in the same place, only if the wait is long
 * enough to notice.
 */
import { useCallback, useState } from 'react';
import { StyleSheet, View, type LayoutChangeEvent } from 'react-native';
import Animated, { interpolate, interpolateColor, useAnimatedStyle } from 'react-native-reanimated';

import { span, type Stage } from './geometry';
import { FLIGHT, MARK } from './motion';
import { COLOR, COPY, LAYOUT, TYPE } from './theme';
import type { SummaryValues } from './use-summary-motion';

const NAMES = COPY.levels.map((l) => l.name);
const STEPS = NAMES.map((_, i) => i);

function Segment({ name, index, values, onWidth }: { name: string; index: number; values: SummaryValues; onWidth: (i: number, w: number) => void }) {
  const { levelPos } = values;
  const style = useAnimatedStyle(() => ({
    color: interpolateColor(Math.min(1, Math.abs(levelPos.get() - index)), [0, 1], [COLOR.depthInkActive, COLOR.depthInk]),
  }));
  return <View style={styles.segment} onLayout={(e: LayoutChangeEvent) => onWidth(index, e.nativeEvent.layout.width)}>
    <Animated.Text style={[TYPE.depth, style]}>{name}</Animated.Text>
  </View>;
}

export function DepthLabel({ stage, insetBottom, values }: { stage: Stage; insetBottom: number; values: SummaryValues }) {
  const [widths, setWidths] = useState<number[]>(() => NAMES.map(() => 0));
  const onWidth = useCallback((i: number, w: number) => {
    setWidths((prev) => (prev[i] === w ? prev : prev.map((v, j) => (j === i ? w : v))));
  }, []);
  const measured = widths.every((w) => w > 0);
  const lefts = widths.map((_, i) => widths.slice(0, i).reduce((s, w) => s + w, 0));
  const centerY = stage.height - insetBottom - LAYOUT.depthLift;
  const { depthAlpha, levelPos, think, wait, flightD } = values;
  const pill = useAnimatedStyle(() => ({ opacity: measured ? depthAlpha.get() : 0 }));
  // The lens is an absolute, childless view: its width may follow the segment it sits under.
  const lens = useAnimatedStyle(() => {
    const t = levelPos.get();
    return { left: interpolate(t, STEPS, lefts) + LAYOUT.depthPad, width: interpolate(t, STEPS, widths) };
  });
  // It leaves as soon as the answer arrives (the flight clock starts), before the first chip can rise through its words.
  const thinking = useAnimatedStyle(() => ({
    opacity: think.get() * span(wait.get(), MARK.pulseDelay, MARK.pulseDelay + MARK.pulseMs)
      * (1 - span(flightD.get(), 0, FLIGHT.labelOutMs)),
  }));
  return <View style={StyleSheet.absoluteFill} pointerEvents="none" accessibilityElementsHidden importantForAccessibility="no-hide-descendants">
    <Animated.View style={[styles.pill, { top: centerY - LAYOUT.depthHeight / 2 }, pill]}>
      <Animated.View style={[styles.lens, lens]} />
      {NAMES.map((name, i) => <Segment key={name} name={name} index={i} values={values} onWidth={onWidth} />)}
    </Animated.View>
    <Animated.Text style={[TYPE.thinking, styles.thinking, { top: centerY - TYPE.thinking.lineHeight / 2 }, thinking]}>{COPY.thinking}</Animated.Text>
  </View>;
}

const styles = StyleSheet.create({
  pill: {
    position: 'absolute', alignSelf: 'center', height: LAYOUT.depthHeight, padding: LAYOUT.depthPad, borderRadius: LAYOUT.depthHeight / 2,
    flexDirection: 'row', backgroundColor: COLOR.depthGlass, borderWidth: StyleSheet.hairlineWidth, borderColor: COLOR.depthBorder,
  },
  lens: { position: 'absolute', top: LAYOUT.depthPad, bottom: LAYOUT.depthPad, borderRadius: (LAYOUT.depthHeight - LAYOUT.depthPad * 2) / 2, backgroundColor: COLOR.depthLens },
  segment: { height: LAYOUT.depthHeight - LAYOUT.depthPad * 2, paddingHorizontal: LAYOUT.depthSegPadX, justifyContent: 'center' },
  thinking: { position: 'absolute', left: 0, right: 0, textAlign: 'center' },
});
