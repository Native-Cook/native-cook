import { StyleSheet, Text, type TextStyle, View } from 'react-native';
import Animated, { type SharedValue, useAnimatedStyle } from 'react-native-reanimated';

/*
 Rolling labels (Hot Air Balloon Welcome). Every change rolls inside a clipped box (the old line goes up and fades, the new
 one comes in from below); nothing cross-fades in place. They read shared values only, so React
 never re-renders while they move.
 */

function RollRow({ text, i, index, height, style, align, lines }: { text: string; i: number; index: SharedValue<number>; height: number; style: TextStyle; align: 'center' | 'flex-start'; lines: number }) {
  const anim = useAnimatedStyle(() => {
    const d = i - index.get();
    return { opacity: Math.max(0, 1 - Math.abs(d) * 1.15), transform: [{ translateY: d * height }] };
  });
  return (
    <Animated.View style={[styles.row, { height, alignItems: align }, anim]}>
      <Text style={style} numberOfLines={lines}>
        {text}
      </Text>
    </Animated.View>
  );
}

/** One line of a list, chosen by a continuous index: 2.4 sits 40 % of the way from line 2 to 3. */
export function RollStack({
  items,
  index,
  height,
  style,
  align = 'center',
  lines = 1,
}: {
  items: string[];
  index: SharedValue<number>;
  height: number;
  style: TextStyle;
  align?: 'center' | 'flex-start';
  lines?: number;
}) {
  return (
    <View style={[styles.clip, { height }]}>
      {items.map((text, i) => (
        <RollRow key={text} text={text} i={i} index={index} height={height} style={style} align={align} lines={lines} />
      ))}
    </View>
  );
}

const styles = StyleSheet.create({
  clip: { overflow: 'hidden', alignSelf: 'stretch' },
  row: { position: 'absolute', left: 0, right: 0, top: 0, justifyContent: 'center' },
});
