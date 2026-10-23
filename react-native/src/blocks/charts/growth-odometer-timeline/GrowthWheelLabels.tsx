import { useState } from 'react';
import { StyleSheet } from 'react-native';
import Animated, { type SharedValue, useAnimatedStyle } from 'react-native-reanimated';
import { type GrowthState, visuals } from './growth-sim';
import { labelAt, MONTHS, type Metrics } from './growth-theme';
const PILL_HALF_HEIGHT_PT = 11;
const LABEL_SCREEN_MARGIN_PT = 4;
const LABEL_BASE_BORDER_ALPHA = 0.09;
const LABEL_FOCUS_BORDER_GAIN = 0.21;
const LABEL_HERO_BORDER_GAIN = 0.6;
const LABEL_WHITE_FOCUS = 0.8;
const PILL_HEIGHT_PT = 22;
const PILL_PADDING_PT = 8;
const LABEL_FONT_PT = 11;
const LABEL_LINE_HEIGHT_PT = 15;

type Props = {
  m: Metrics;
  state: SharedValue<GrowthState>;
};
function MonthPill({
  index,
  m: layout,
  state
}: Props & {
  index: number;
}) {
  const [width, setWidth] = useState(0);
  const style = useAnimatedStyle(() => {
    const wheel = state.get();
    const label = labelAt(index, wheel.p, layout);
    const effects = visuals(wheel);
    const borderAlpha = LABEL_BASE_BORDER_ALPHA
      + label.focus * (LABEL_FOCUS_BORDER_GAIN + LABEL_HERO_BORDER_GAIN * effects.flash);
    return {
      top: label.y - PILL_HALF_HEIGHT_PT,
      right: layout.width - Math.max(label.right, width + LABEL_SCREEN_MARGIN_PT),
      opacity: label.alpha,
      borderColor: `rgba(168,240,60,${borderAlpha})`
    };
  });
  const textStyle = useAnimatedStyle(() => {
    const label = labelAt(index, state.get().p, layout);
    return {
      color: label.focus > LABEL_WHITE_FOCUS ? '#FFFFFF' : '#DDE3E8'
    };
  });
  return <Animated.View
    onLayout={event => setWidth(event.nativeEvent.layout.width)}
    style={[styles.pill, style]}
  >
    <Animated.Text allowFontScaling={false} numberOfLines={1} style={[styles.text, textStyle]}>
      {MONTHS[index]}
    </Animated.Text>
  </Animated.View>;
}

export function GrowthWheelLabels({
  m: layout,
  state
}: Props) {
  return <>{MONTHS.map((_month, index) => <MonthPill
    key={index}
    index={index}
    m={layout}
    state={state}
  />)}</>;
}
const styles = StyleSheet.create({
  pill: {
    position: 'absolute',
    height: PILL_HEIGHT_PT,
    borderRadius: PILL_HALF_HEIGHT_PT,
    paddingHorizontal: PILL_PADDING_PT,
    borderWidth: StyleSheet.hairlineWidth,
    backgroundColor: 'rgba(255,255,255,0.035)',
    justifyContent: 'center'
  },
  text: {
    fontSize: LABEL_FONT_PT,
    fontWeight: '500',
    lineHeight: LABEL_LINE_HEIGHT_PT
  }
});
