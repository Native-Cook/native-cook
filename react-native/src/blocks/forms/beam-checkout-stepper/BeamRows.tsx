import { Text, View } from 'react-native';
import { Pressable } from 'react-native-gesture-handler';
import Animated, { useAnimatedStyle } from 'react-native-reanimated';
import { C, F, FONT, G, STEPS, nodeY, rowScale, type BeamMotion } from './beam-theme';
import { rowSummaries, type FormModel } from './beam-form';

type Props = { motion: BeamMotion; model: FormModel; onGo: (target: number) => void; onPress: (index: number, down: boolean) => void };
function Row({ index, motion, model, onGo, onPress }: Props & { index: number }) {
  const { frame } = motion;
  const group = useAnimatedStyle(() => {
    const f = frame.get(); const scale = rowScale(f[F.ROW_RISE + index]) * (1 - 0.025 * f[F.ROW_PRESS + index]);
    return { transform: [{ scale }] };
  });
  // The two weights have different widths, so they hand over quickly instead of overlapping as a ghost.
  const active = useAnimatedStyle(() => ({ opacity: Math.max(0, Math.min(1, (frame.get()[F.ROW_ACTIVE + index] - 0.35) / 0.4)) }));
  const quiet = useAnimatedStyle(() => {
    const f = frame.get(); return { opacity: (1 - Math.min(1, f[F.ROW_ACTIVE + index] / 0.5)) * (0.38 + 0.28 * f[F.ROW_DONE + index]) };
  });
  const summary = useAnimatedStyle(() => ({ opacity: frame.get()[F.ROW_DONE + index] }));
  const disabled = model.paid || index > model.furthest || index === model.step;
  const status = index === model.step ? 'current' : index < model.furthest || model.paid ? 'completed' : 'not started';
  return (
    <Animated.View style={[{ position: 'absolute', left: 0, top: nodeY(index) - 34, width: 402, height: 68, transformOrigin: [G.railX, 34, 0] }, group]}>
      <Animated.View pointerEvents="none" accessibilityElementsHidden importantForAccessibility="no-hide-descendants" style={[{ position: 'absolute', left: 122, top: 23 }, quiet]}>
        <Text allowFontScaling={false} numberOfLines={1} style={{ fontFamily: FONT.regular, color: C.text, fontSize: 16, lineHeight: 22 }}>{STEPS[index].label}</Text>
      </Animated.View>
      <Animated.View pointerEvents="none" accessibilityElementsHidden importantForAccessibility="no-hide-descendants" style={[{ position: 'absolute', left: 122, top: 23 }, active]}>
        <Text allowFontScaling={false} numberOfLines={1} style={{ fontFamily: FONT.bold, color: C.text, fontSize: 16, lineHeight: 22 }}>{STEPS[index].label}</Text>
      </Animated.View>
      <Animated.View pointerEvents="none" accessibilityElementsHidden importantForAccessibility="no-hide-descendants" style={[{ position: 'absolute', right: 48, top: 24, maxWidth: 142 }, summary]}>
        <Text allowFontScaling={false} numberOfLines={1} ellipsizeMode="tail" style={{ fontFamily: FONT.regular, color: C.textMute, fontSize: 13, lineHeight: 20, textAlign: 'right' }}>{rowSummaries(model)[index]}</Text>
      </Animated.View>
      <Pressable disabled={disabled} accessibilityRole="button" accessibilityLabel={`Step ${index + 1} of 5, ${STEPS[index].label}, ${status}`} accessibilityState={{ disabled, selected: index === model.step }} accessibilityHint={disabled ? undefined : 'Goes back to this step'} onPressIn={() => onPress(index, true)} onPressOut={() => onPress(index, false)} onPress={() => onGo(index)} style={{ position: 'absolute', left: 32, top: 4, width: 338, height: 60 }} />
    </Animated.View>
  );
}
/** Draws row typography and reached-step touch targets over the glass picture. */
export function BeamRows(props: Props) {
  return <View pointerEvents="box-none" style={{ position: 'absolute', inset: 0 }}>{STEPS.map((_, index) => <Row key={index} {...props} index={index} />)}</View>;
}
