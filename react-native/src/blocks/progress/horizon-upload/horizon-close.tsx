import { useMemo, useState } from "react";
import { StyleSheet, View } from "react-native";
import { Gesture, GestureDetector } from "react-native-gesture-handler";
import { Easing, ReduceMotion, useAnimatedReaction, withSpring, withTiming } from "react-native-reanimated";
import { scheduleOnRN, scheduleOnUI } from "react-native-worklets";
import { HorizonCloseIcon } from "./horizon-icons";
import type { HorizonFx, Signal } from "./horizon-state";
import { HORIZON_DRAG } from "./horizon-upload";
import { usePreferences } from "./i18n";

// The close button: a 44 pt target around the icon. The icon and its change
// from a cross to a retry arrow live in horizon-icons.tsx; this file owns the
// touch. The tap does not wait for the card's pan to fail: the pan's 8 pt
// threshold and the tap's maxDistance already keep the two apart, and that
// relation made taps fail whenever the percentage re-rendered the card.
export function HorizonClose({ x, y, retry, enabled, fx, form, reduced, onPress, onTouch }: {
  x: number; y: number;
  /** 0 while the button cancels, 1 once it offers to upload again. */
  retry: Signal;
  enabled: Signal;
  fx: HorizonFx;
  /** "button" draws the whole glass button from its image; "glyph" only the mark, over the bead the scene draws. */
  form: "button" | "glyph";
  reduced: boolean;
  /** UI-thread callbacks shared with the upload controller. */
  onPress: () => void; onTouch: () => void;
}) {
  const { t } = usePreferences();
  const [retryLabel, setRetryLabel] = useState(false);
  const pressed = fx.closePress;
  useAnimatedReaction(() => retry.get(), (next, before) => {
    if (next !== before) scheduleOnRN(setRetryLabel, next === 1);
  });
  const tap = useMemo(() => Gesture.Tap().maxDistance(HORIZON_DRAG.threshold).maxDuration(60000)
    .onBegin(() => {
      onTouch();
      if (enabled.get()) pressed.set(withTiming(1, { duration: 90, easing: Easing.out(Easing.quad), reduceMotion: ReduceMotion.Never }));
    })
    .onEnd((_event, success) => { if (success) onPress(); })
    .onFinalize(() => { pressed.set(reduced ? withTiming(0, { duration: 120, reduceMotion: ReduceMotion.Never }) : withSpring(0, { duration: 300, dampingRatio: 0.7 })); }),
  [onPress, onTouch, enabled, pressed, reduced]);
  const label = retryLabel ? t("Upload again", "Tải lên lại") : t("Cancel upload", "Huỷ tải lên");
  return (
    <GestureDetector gesture={tap}>
      <View collapsable={false} accessible style={[styles.target, { left: x - 22, top: y - 22 }]} accessibilityRole="button"
        accessibilityLabel={label} onAccessibilityTap={() => scheduleOnUI(onPress)}
        accessibilityActions={[{ name: "activate", label }]}
        onAccessibilityAction={(event) => { if (event.nativeEvent.actionName === "activate") scheduleOnUI(onPress); }}>
        <HorizonCloseIcon retry={retry} fx={fx} reduced={reduced} form={form} />
      </View>
    </GestureDetector>
  );
}

const styles = StyleSheet.create({
  target: { position: "absolute", width: 44, height: 44, alignItems: "center", justifyContent: "center" },
});
