import { useEffect } from "react";
import { Platform, Pressable, StyleSheet, Text, View } from "react-native";
import Animated, { Easing, ReduceMotion, useAnimatedStyle, useSharedValue, withTiming } from "react-native-reanimated";
import { auraFlow } from "./aura-flow";
import { auraDesign as D, type AuraSkin } from "./aura-palette";
import { usePreferences } from "./i18n";
import { systemFace } from "./ui";

// The two labels measure themselves once in each language. Only their empty
// background changes width, keeping the letters centred and undistorted.
const AnimatedPressable = Animated.createAnimatedComponent(Pressable);
export function AuraChip({ after, reduced, u, skin }: {
  after: boolean;
  reduced: boolean;
  u: number;
  skin: AuraSkin;
}) {
  const { t } = usePreferences();
  const blend = useSharedValue(0);
  const beforeWidth = useSharedValue(D.chip.w * u);
  const afterWidth = useSharedValue(D.chip.w * u);
  useEffect(() => {
    blend.set(withTiming(after ? 1 : 0, {
      duration: auraFlow.views.chip.fade,
      easing: Easing.bezier(0.77, 0, 0.175, 1),
      reduceMotion: ReduceMotion.Never,
    }));
  }, [after, blend]);
  const background = useAnimatedStyle(() => {
    const p = blend.get();
    const from = beforeWidth.get();
    const to = afterWidth.get();
    const width = reduced ? (p < 0.5 ? from : to) : from + (to - from) * p;
    return { width, transform: [{ translateX: -width / 2 }], opacity: reduced ? Math.abs(2 * p - 1) : 1 };
  });
  const oldLabel = useAnimatedStyle(() => {
    const p = blend.get();
    return { opacity: 1 - p, filter: Platform.OS === "ios" && !reduced ? `blur(${4 * auraFlow.views.chip.blur * p * (1 - p)}px)` : "blur(0px)" };
  });
  const newLabel = useAnimatedStyle(() => {
    const p = blend.get();
    return { opacity: p, filter: Platform.OS === "ios" && !reduced ? `blur(${4 * auraFlow.views.chip.blur * p * (1 - p)}px)` : "blur(0px)" };
  });
  const row = {
    left: (D.chip.cx - D.hero.w / 2) * u,
    top: (D.chip.cy - D.chip.h / 2) * u,
    width: D.hero.w * u,
    height: D.chip.h * u,
  };
  const chip = {
    left: D.hero.w * u / 2,
    height: D.chip.h * u,
    borderRadius: D.chip.h * u / 2,
    backgroundColor: skin.chipFill,
    borderWidth: D.details.chipStroke * u,
    borderColor: skin.chipLine,
  };
  const label = {
    fontSize: D.chip.textSize * u,
    lineHeight: D.lines.chip * u,
    height: D.lines.chip * u,
    letterSpacing: D.chip.tracking * u,
    color: skin.ink,
  };
  return (
    <View pointerEvents="box-none" accessible={false} accessibilityElementsHidden style={[styles.row, row]}>
      <AnimatedPressable onPress={() => {}} style={[styles.background, chip, background]} />
      <Animated.View pointerEvents="none" style={[styles.label, oldLabel]}>
        <Text allowFontScaling={false} numberOfLines={1} style={[styles.text, label]}
          onLayout={(event) => beforeWidth.set(Math.max(D.chip.w * u, event.nativeEvent.layout.width + 2 * D.details.chipPad * u))}>
          {t("YOUR BALANCE", "SỐ DƯ CỦA BẠN")}
        </Text>
      </Animated.View>
      <Animated.View pointerEvents="none" style={[styles.label, newLabel]}>
        <Text allowFontScaling={false} numberOfLines={1} style={[styles.text, label]}
          onLayout={(event) => afterWidth.set(Math.max(D.chip.w * u, event.nativeEvent.layout.width + 2 * D.details.chipPad * u))}>
          {t("BALANCE AFTER", "SỐ DƯ SAU GỬI")}
        </Text>
      </Animated.View>
    </View>
  );
}

const styles = StyleSheet.create({
  row: { position: "absolute", justifyContent: "center" },
  background: { position: "absolute" },
  label: { position: "absolute", alignSelf: "center" },
  text: {
    fontFamily: systemFace,
    fontWeight: "500",
    includeFontPadding: false,
    textAlign: "center",
  },
});
