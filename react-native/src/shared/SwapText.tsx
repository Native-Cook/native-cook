import { StyleSheet, Text, View, type StyleProp, type TextStyle, type ViewStyle } from 'react-native';
import Animated, { Easing, FadeIn, FadeInDown, FadeOut, LayoutAnimationConfig, ReduceMotion } from 'react-native-reanimated';

const SETTLE = Easing.bezier(0.23, 1, 0.32, 1);

/**
 * Text that changes in place. The old words leave fast (140 ms) and the new ones
 * settle a beat later from 10 pt below (260 ms), so a headline reads as one sentence
 * being rewritten rather than a cut. Under Reduce Motion it is a plain cross-fade.
 * The first render does not animate.
 */
export function SwapText({ text, style, reduced = false, delay = 0, accessibilityRole, maxFontSizeMultiplier }: { text: string; style?: StyleProp<TextStyle>; reduced?: boolean; delay?: number; accessibilityRole?: 'header'; maxFontSizeMultiplier?: number }) {
  const entering = reduced
    ? FadeIn.duration(180).reduceMotion(ReduceMotion.Never)
    : FadeInDown.duration(260).delay(60 + delay).easing(SETTLE).withInitialValues({ opacity: 0, transform: [{ translateY: 10 }] });
  const exiting = reduced
    ? FadeOut.duration(120).reduceMotion(ReduceMotion.Never)
    : FadeOut.duration(140).easing(SETTLE);
  // Box-level layout (width limits, margins, self-alignment) belongs to the wrapper, so
  // the overlaid copy fills exactly the box the ghost line measured.
  const { alignSelf, maxWidth, width, margin, marginTop, marginBottom, marginHorizontal, marginVertical, marginLeft, marginRight, ...textStyle } = StyleSheet.flatten(style) ?? {};
  const box: ViewStyle = { alignSelf, maxWidth, width, margin, marginTop, marginBottom, marginHorizontal, marginVertical, marginLeft, marginRight } as ViewStyle;
  return <View style={box}>
    {/* Sizes the line; the animated copy sits on top of it. */}
    <Text maxFontSizeMultiplier={maxFontSizeMultiplier} style={[textStyle, styles.ghost]} accessibilityElementsHidden importantForAccessibility="no-hide-descendants">{text}</Text>
    <LayoutAnimationConfig skipEntering>
      <Animated.Text key={text} accessibilityRole={accessibilityRole} maxFontSizeMultiplier={maxFontSizeMultiplier} entering={entering} exiting={exiting} style={[textStyle, StyleSheet.absoluteFill]}>{text}</Animated.Text>
    </LayoutAnimationConfig>
  </View>;
}

const styles = StyleSheet.create({ ghost: { opacity: 0 } });
