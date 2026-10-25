import { StyleSheet, Text, type TextStyle, View } from 'react-native';
import Animated, { type SharedValue, useAnimatedStyle } from 'react-native-reanimated';

/*
 Rolling labels. Every change rolls inside a clipped box (the old line goes up and fades, the new
 one comes in from below); nothing cross-fades in place. They read shared values only, so React
 never re-renders while they move.
 */

function RollRow({ text, i, index, height, style, align }: { text: string; i: number; index: SharedValue<number>; height: number; style: TextStyle; align: 'center' | 'flex-start' }) {
  const anim = useAnimatedStyle(() => {
    const d = i - index.get();
    return { opacity: Math.max(0, 1 - Math.abs(d) * 1.15), transform: [{ translateY: d * height }] };
  });
  return (
    <Animated.View style={[styles.row, { height, alignItems: align }, anim]}>
      <Text style={style} numberOfLines={1}>
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
}: {
  items: string[];
  index: SharedValue<number>;
  height: number;
  style: TextStyle;
  align?: 'center' | 'flex-start';
}) {
  return (
    <View style={[styles.clip, { height }]}>
      {items.map((text, i) => (
        <RollRow key={text} text={text} i={i} index={index} height={height} style={style} align={align} />
      ))}
    </View>
  );
}

const DIGITS = ['0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '0'];

function Column({ value, place, height, style }: { value: SharedValue<number>; place: number; height: number; style: TextStyle }) {
  const anim = useAnimatedStyle(() => {
    // Odometer: this column turns with the lower digits' carry, and rests on a whole digit
    // whenever the value is whole.
    const v = Math.max(0, value.get());
    const scaled = v / Math.pow(10, place);
    const whole = Math.floor(scaled) % 10;
    const lower = place === 0 ? scaled - Math.floor(scaled) : Math.max(0, (scaled - Math.floor(scaled)) * 10 - 9);
    return { transform: [{ translateY: -(whole + lower) * height }] };
  });
  return (
    <View style={{ height, overflow: 'hidden' }}>
      <Animated.View style={anim}>
        {DIGITS.map((d, i) => (
          <Text key={i} style={[style, { height, lineHeight: height }]}>
            {d}
          </Text>
        ))}
      </Animated.View>
    </View>
  );
}

/** A whole-number odometer with thousands separators: `digits` columns after `prefix`. */
export function Odometer({ value, digits, prefix, height, style }: { value: SharedValue<number>; digits: number; prefix: string; height: number; style: TextStyle }) {
  const cols: React.ReactNode[] = [];
  for (let k = digits - 1; k >= 0; k--) {
    cols.push(<Column key={k} value={value} place={k} height={height} style={style} />);
    if (k % 3 === 0 && k > 0) {
      cols.push(
        <Text key={`s${k}`} style={[style, { height, lineHeight: height }]}>
          ,
        </Text>,
      );
    }
  }
  return (
    <View style={styles.odo}>
      <Text style={[style, { height, lineHeight: height }]}>{prefix}</Text>
      {cols}
    </View>
  );
}

const styles = StyleSheet.create({
  clip: { overflow: 'hidden', alignSelf: 'stretch' },
  row: { position: 'absolute', left: 0, right: 0, top: 0, justifyContent: 'center' },
  odo: { flexDirection: 'row', alignItems: 'center' },
});
