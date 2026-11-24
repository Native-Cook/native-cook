import { memo } from 'react';
import { StyleSheet, Text, View, type TextStyle } from 'react-native';
import Animated, { useAnimatedStyle, type SharedValue } from 'react-native-reanimated';

import { digitAt } from './dualRangeMotion';

const DIGITS = ['0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '0'];

type Props = {
  value: SharedValue<number>;
  place: number;
  lowest?: boolean;
  /** Fades the column out while the number has no digit at this place. */
  hideLeading?: boolean;
  width: number;
  height: number;
  textStyle: TextStyle;
};

/** One odometer column: eleven separate rows (a multi-line Text would be clipped to its first line). */
export const RollingDigit = memo(function RollingDigit({ value, place, lowest = false, hideLeading = false, width, height, textStyle }: Props) {
  const strip = useAnimatedStyle(() => {
    const v = value.get();
    return {
      opacity: hideLeading && v < place ? Math.max(0, (v - place * 0.9) / (place * 0.1)) : 1,
      transform: [{ translateY: -digitAt(v, place, lowest) * height }],
    };
  });
  return (
    <View accessibilityElementsHidden importantForAccessibility="no-hide-descendants" style={[styles.column, { width, height }]}>
      <Animated.View style={strip}>
        {DIGITS.map((d, i) => (
          <View key={i} style={[styles.row, { width, height }]}>
            <Text allowFontScaling={false} style={[textStyle, { lineHeight: height }]}>
              {d}
            </Text>
          </View>
        ))}
      </Animated.View>
    </View>
  );
});

const styles = StyleSheet.create({
  column: { overflow: 'hidden' },
  row: { alignItems: 'center', justifyContent: 'center' },
});
