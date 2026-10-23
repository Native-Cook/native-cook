import { StyleSheet, Text, View } from 'react-native';
import Svg, { Ellipse, Path } from 'react-native-svg';

import { colors, fonts, px } from './theme';

// The cafe's lockup, laid out as the pin's (box 157 x 114 px): a script line, two heavy words, a
// steaming cup and a small sparkle. The name is invented ("Coffee Tales"); the pin's is a real cafe's.
export const LOGO_W = px(157);
export const LOGO_H = px(114);

export function Logo() {
  return (
    <View style={{ width: LOGO_W, height: LOGO_H }}>
      <Text style={styles.script}>Cafe & Restaurant</Text>
      <Text style={[styles.word, { top: px(60) }]}>COFFEE</Text>
      <Text style={[styles.word, { top: px(87) }]}>TALES</Text>
      <Svg width={LOGO_W} height={LOGO_H} style={StyleSheet.absoluteFill}>
        {/* steam */}
        <Path
          d={`M${px(108)} ${px(42)} C${px(98)} ${px(32)} ${px(116)} ${px(24)} ${px(106)} ${px(14)} C${px(100)} ${px(8)} ${px(106)} ${px(3)} ${px(110)} ${px(0)}`}
          stroke={colors.brand}
          strokeWidth={1.1}
          fill="none"
          strokeLinecap="round"
        />
        <Path
          d={`M${px(114)} ${px(44)} C${px(106)} ${px(36)} ${px(120)} ${px(28)} ${px(112)} ${px(20)}`}
          stroke={colors.brand}
          strokeWidth={1.1}
          fill="none"
          strokeLinecap="round"
        />
        {/* cup: a cone with a rim, a looped handle on the right, a saucer */}
        <Path
          d={`M${px(108)} ${px(52)} L${px(142)} ${px(52)} L${px(132)} ${px(88)} Q${px(125)} ${px(94)} ${px(118)} ${px(88)} Z`}
          fill={colors.brand}
        />
        <Path
          d={`M${px(140)} ${px(58)} C${px(156)} ${px(56)} ${px(156)} ${px(76)} ${px(134)} ${px(80)}`}
          stroke={colors.brand}
          strokeWidth={2}
          fill="none"
        />
        <Ellipse cx={px(126)} cy={px(97)} rx={px(26)} ry={px(4)} stroke={colors.brand} strokeWidth={1.4} fill="none" />
        {/* sparkle */}
        <Path
          d={`M${px(95)} ${px(96)} L${px(97)} ${px(101)} L${px(102)} ${px(103)} L${px(97)} ${px(105)} L${px(95)} ${px(110)} L${px(93)} ${px(105)} L${px(88)} ${px(103)} L${px(93)} ${px(101)} Z`}
          fill={colors.brand}
        />
      </Svg>
    </View>
  );
}

const styles = StyleSheet.create({
  script: { position: 'absolute', left: 0, top: px(48), fontFamily: fonts.script, fontSize: 6.2, color: colors.brand },
  word: {
    position: 'absolute',
    left: 0,
    fontFamily: fonts.title,
    fontSize: 24,
    lineHeight: 26,
    color: colors.brand,
    letterSpacing: 0.2,
  },
});
