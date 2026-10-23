import MaskedView from '@react-native-masked-view/masked-view';
import { LinearGradient } from 'expo-linear-gradient';
import { useCallback, useState } from 'react';
import { StyleSheet, Text, View, type NativeSyntheticEvent, type TextLayoutEventData } from 'react-native';
import Animated, { useAnimatedStyle, useSharedValue, type SharedValue } from 'react-native-reanimated';

import { TIP } from './content';
import { STREAM, lerp, streamHeads } from './motion';
import { colors, layout, type } from './theme';

export type TipFrame = { ghost: number; stream: number; caret: number; shimmer: number };

type Line = { x: number; y: number; width: number; height: number };

/**
 * The AI tip. The text is a mask; under it, an ink layer (the ghost while it regenerates), one ink
 * strip per line whose tinted head writes the line, and a shimmer band. At rest every strip covers
 * its line, so the mask shows the plain ink text of the pin.
 */
export function Tip({ k, top, frame }: { k: number; top: number; frame: SharedValue<TipFrame> }) {
  const width = layout.columnWidth * k;
  const height = layout.tipLines * type.tip.line * k;
  const [lines, setLines] = useState<Line[]>([]);
  const widths = useSharedValue<number[]>([]);
  const textStyle = {
    fontSize: type.tip.size * k,
    lineHeight: type.tip.line * k,
    letterSpacing: type.tip.tracking * k,
  };

  const onLayout = useCallback((event: NativeSyntheticEvent<TextLayoutEventData>) => {
    const measured = event.nativeEvent.lines.map((l) => ({ x: l.x, y: l.y, width: l.width, height: l.height }));
    if (measured.length === 0) return;
    setLines(measured);
    widths.set(measured.map((l) => l.width / k));
  }, [k, widths]);

  const ghostStyle = useAnimatedStyle(() => ({ opacity: frame.get().ghost }));
  const shimmerStyle = useAnimatedStyle(() => {
    const s = frame.get().shimmer;
    return {
      opacity: s < 0 ? 0 : 1,
      transform: [
        { translateX: lerp(-STREAM.shimmerWidth * 1.4 * k, width + STREAM.shimmerWidth * 0.4 * k, Math.max(0, s)) },
        { rotate: `${STREAM.shimmerSkew}deg` },
      ],
    };
  });
  const caretStyle = useAnimatedStyle(() => {
    const f = frame.get();
    const w = widths.get();
    if (w.length === 0 || f.caret <= 0) return { opacity: 0 };
    const heads = streamHeads(f.stream, w);
    let line = w.length - 1;
    for (let i = 0; i < w.length; i += 1) {
      if (heads[i] < w[i]) { line = i; break; }
    }
    const x = Math.min(heads[line], w[line]) * k;
    return {
      opacity: f.caret,
      transform: [
        { translateX: x + STREAM.caretInset * 0.5 * k },
        { translateY: line * type.tip.line * k },
      ],
    };
  });

  return (
    <View
      accessible
      accessibilityLabel={TIP}
      style={{ position: 'absolute', left: layout.column * k, top, width, height }}
    >
      <Text
        style={[styles.text, styles.measure, textStyle, { width }]}
        onTextLayout={onLayout}
        accessibilityElementsHidden
        importantForAccessibility="no-hide-descendants"
      >
        {TIP}
      </Text>
      <MaskedView
        style={StyleSheet.absoluteFill}
        maskElement={<Text style={[styles.text, textStyle, { width }]}>{TIP}</Text>}
      >
        <Animated.View style={[StyleSheet.absoluteFill, { backgroundColor: colors.ink }, ghostStyle]} />
        {lines.map((line, index) => (
          <Strip key={index} index={index} line={line} k={k} frame={frame} widths={widths} />
        ))}
        <Animated.View style={[styles.shimmer, { width: STREAM.shimmerWidth * k, height: height * 1.6, top: -height * 0.3 }, shimmerStyle]}>
          <LinearGradient colors={colors.shimmer} start={{ x: 0, y: 0.5 }} end={{ x: 1, y: 0.5 }} style={StyleSheet.absoluteFill} />
        </Animated.View>
      </MaskedView>
      <Animated.View
        pointerEvents="none"
        style={[
          styles.caret,
          {
            top: 5 * k,
            width: STREAM.caretWidth * k,
            height: (type.tip.line - 9) * k,
            borderRadius: STREAM.caretWidth * k,
            shadowRadius: 7 * k,
          },
          caretStyle,
        ]}
      >
        <LinearGradient colors={[colors.pink, colors.violet]} style={[StyleSheet.absoluteFill, { borderRadius: STREAM.caretWidth * k }]} />
      </Animated.View>
    </View>
  );
}

function Strip({ index, line, k, frame, widths }: {
  index: number; line: Line; k: number; frame: SharedValue<TipFrame>; widths: SharedValue<number[]>;
}) {
  const head = STREAM.head * k;
  const style = useAnimatedStyle(() => {
    const w = widths.get();
    if (w.length <= index) return { transform: [{ translateX: line.width + head }] };
    const heads = streamHeads(frame.get().stream, w);
    return { transform: [{ translateX: heads[index] * k }] };
  });
  return (
    <View style={{ position: 'absolute', left: line.x, top: line.y, width: line.width, height: line.height, overflow: 'hidden' }}>
      <Animated.View style={[{ position: 'absolute', top: 0, bottom: 0, left: -(line.width + head), width: line.width + head, flexDirection: 'row' }, style]}>
        <View style={{ width: line.width, backgroundColor: colors.ink }} />
        <LinearGradient
          colors={[colors.ink, colors.pink, colors.violet]}
          locations={[0, 0.55, 1]}
          start={{ x: 0, y: 0.5 }}
          end={{ x: 1, y: 0.5 }}
          style={{ width: head }}
        />
      </Animated.View>
    </View>
  );
}

const styles = StyleSheet.create({
  text: { color: colors.ink, fontWeight: type.tip.weight },
  measure: { position: 'absolute', left: 0, top: 0, opacity: 0 },
  shimmer: { position: 'absolute', left: 0 },
  caret: {
    position: 'absolute',
    left: 0,
    shadowColor: colors.caretGlow,
    shadowOpacity: 0.9,
    shadowOffset: { width: 0, height: 0 },
  },
});
