import { useState } from "react";
import { Pressable, StyleSheet, View, useWindowDimensions } from "react-native";
import * as Haptics from "expo-haptics";
import {
  GestureDetector,
  type GestureType,
} from "react-native-gesture-handler";
import Animated, {
  type SharedValue,
  useAnimatedStyle,
  useSharedValue,
} from "react-native-reanimated";
import { NumericText } from "expo-content-transition";
import { Canvas, Group } from "@shopify/react-native-skia";
import { Txt, useMotionPrefs } from "../ui";
import { usePreferences } from "../i18n";
import { space } from "../theme";
import { tactile } from "./tactile-theme";
import { referenceGeometry as g } from "./reference-motion";
import { type Channel, type TactileControl } from "./use-tactile-control";
import { allEffects, type Effects } from "./effects";
import { DeviceArt, specimen, type Tilt } from "./device-art";
import { ReferenceGlyph } from "./glyphs";
import { Readout } from "./readout";
import { artText } from "./typography";

export { DeviceArt, specimen, type Tilt };

const TOP = specimen.top,
  HEIGHT = specimen.height;

function ControlDrawing({
  control,
  scale,
  fx,
  tilt,
}: {
  control: TactileControl;
  scale: number;
  fx: Effects;
  tilt: SharedValue<Tilt>;
}) {
  return (
    <Canvas
      style={StyleSheet.absoluteFill}
      pointerEvents="none"
      accessible={false}
      accessibilityElementsHidden
      importantForAccessibility="no-hide-descendants"
    >
      <Group transform={[{ scale }, { translateY: TOP }]}>
        <DeviceArt control={control} fx={fx} tilt={tilt} />
      </Group>
    </Canvas>
  );
}

export function TactileConsole({
  control,
  fx = allEffects,
  tilt,
  highContrast = false,
  pickUp,
  standIn,
}: {
  control: TactileControl;
  fx?: Effects;
  tilt?: SharedValue<Tilt>;
  /** iOS "Increase Contrast" / Android high-contrast text. */
  highContrast?: boolean;
  /** Hold-to-lift gesture on the bare housing (behind the controls). */
  pickUp?: GestureType;
  /** 1 while the lifted 3D object stands in for this drawing. */
  standIn?: SharedValue<number>;
}) {
  const prefs = useMotionPrefs();
  const { brightPress, volumePress } = control;
  // The pressed glyph sinks with its well.
  const brightSink = useAnimatedStyle(() => ({
    transform: [{ scale: 1 - brightPress.get() * 0.08 }],
  }));
  const volumeSink = useAnimatedStyle(() => ({
    transform: [{ scale: 1 - volumePress.get() * 0.08 }],
  }));
  const inactive = highContrast ? tactile.inactiveHigh : tactile.inactive;
  const { t } = usePreferences();
  const { fontScale } = useWindowDimensions();
  const [width, setWidth] = useState(335);
  const scale = width / g.width;
  const { labelAlpha, selectorMix } = control;
  const largeText = fontScale >= 1.35;
  const labelStyle = useAnimatedStyle(() => ({ opacity: labelAlpha.get() }));
  const { fineShown } = control;
  const fineStyle = useAnimatedStyle(() => ({ opacity: fineShown.get() }));
  const standInStyle = useAnimatedStyle(() => ({
    opacity: standIn ? 1 - standIn.get() : 1,
  }));
  const stillTilt = useSharedValue<Tilt>({ x: 0, y: 0 });
  // The lit glyph crossfades with the light's color handoff.
  const brightGlyph = useAnimatedStyle(() => ({
    opacity: 1 - selectorMix.get(),
  }));
  const volumeGlyph = useAnimatedStyle(() => ({ opacity: selectorMix.get() }));
  const title =
    control.mode === 0 ? t("Brightness", "Độ sáng") : t("Volume", "Âm lượng");
  const adjustable = {
    accessible: true,
    accessibilityRole: "adjustable" as const,
    accessibilityState: { disabled: !control.enabled },
    accessibilityValue: {
      min: 0,
      max: 100,
      now: control.snapshot[control.mode],
    },
    accessibilityActions: [{ name: "increment" }, { name: "decrement" }],
    onAccessibilityAction: ({
      nativeEvent,
    }: {
      nativeEvent: { actionName: string };
    }) => {
      if (nativeEvent.actionName === "increment") control.adjust(5);
      if (nativeEvent.actionName === "decrement") control.adjust(-5);
    },
  };
  // The in-artwork selectors are the only mode controls on this page, so they
  // stay in the accessibility tree.
  const modeAction = (mode: Channel) => {
    const c = mode === 0 ? g.brightness : g.volume;
    const size = (mode === 0 ? 50 : 46) * scale;
    return (
      <Pressable
        key={mode}
        accessibilityRole="button"
        accessibilityLabel={
          mode === 0 ? t("Brightness", "Độ sáng") : t("Volume", "Âm lượng")
        }
        accessibilityState={{
          selected: control.mode === mode,
          disabled: !control.enabled,
        }}
        disabled={!control.enabled}
        hitSlop={8}
        onPressIn={() => control.pressSelector(mode, true)}
        onPressOut={() => control.pressSelector(mode, false)}
        onPress={() => {
          if (prefs.haptics) void Haptics.selectionAsync().catch(() => {});
          control.select(mode);
        }}
        style={{
          position: "absolute",
          left: (c.x - c.radius) * scale,
          top: (TOP + c.y - c.radius) * scale,
          width: c.radius * 2 * scale,
          height: c.radius * 2 * scale,
          alignItems: "center",
          justifyContent: "center",
        }}
      >
        <Animated.View style={mode === 0 ? brightSink : volumeSink}>
          <ReferenceGlyph mode={mode} size={size} tint={inactive} />
          <Animated.View
            style={[
              StyleSheet.absoluteFill,
              mode === 0 ? brightGlyph : volumeGlyph,
            ]}
          >
            <ReferenceGlyph mode={mode} size={size} tint={tactile.text} />
          </Animated.View>
        </Animated.View>
      </Pressable>
    );
  };
  return (
    <View style={{ gap: space.md }}>
      {largeText && (
        <View style={styles.largeReadout}>
          <Txt variant="label" style={{ color: tactile.pageText }}>
            {title}
          </Txt>
          <Txt variant="label" style={{ color: tactile.pageText }}>
            {control.snapshot[control.mode]}%
          </Txt>
        </View>
      )}
      <Animated.View
        testID="tactile-specimen"
        onLayout={({ nativeEvent: { layout } }) => {
          setWidth(layout.width);
          control.width.set((g.track.width * layout.width) / g.width);
        }}
        style={[{ width: "100%", height: HEIGHT * scale }, standInStyle]}
      >
        {pickUp && (
          <GestureDetector gesture={pickUp}>
            <View
              collapsable={false}
              accessible={false}
              style={{
                position: "absolute",
                left: 0,
                top: TOP * scale,
                width: "100%",
                height: g.height * scale,
              }}
            />
          </GestureDetector>
        )}
        <ControlDrawing
          control={control}
          scale={scale}
          fx={fx}
          tilt={tilt ?? stillTilt}
        />
        {!largeText && (
          <View
            pointerEvents="none"
            accessible={false}
            accessibilityElementsHidden
            importantForAccessibility="no-hide-descendants"
            style={StyleSheet.absoluteFill}
          >
            {/* One title whose glyphs swap natively, in the direction of
                the selector that was pressed (Volume sits below Brightness).
                Its left-to-right glyph stagger ends inside the 380ms light
                handoff. */}
            <Animated.View
              style={[
                {
                  position: "absolute",
                  left: 118 * scale,
                  top: (TOP + 36) * scale,
                  width: 360 * scale,
                  height: 40 * scale,
                },
                labelStyle,
              ]}
            >
              <NumericText
                value={control.mode === 0 ? "Brightness" : "Volume"}
                direction={control.mode === 1 ? "up" : "down"}
                animated={!control.reduced}
                duration={300}
                bounce={0.18}
                maxBlurRadius={3}
                style={{
                  ...StyleSheet.flatten(artText.label),
                  position: "relative",
                  width: 360 * scale,
                  height: 40 * scale,
                  fontSize: 31 * scale,
                  textAlign: "left",
                }}
              />
            </Animated.View>
            <Readout
              control={control}
              scale={scale}
              highContrast={highContrast}
            />
            {fx.fineScrub && (
              <Animated.Text
                style={[
                  artText.label,
                  {
                    left: 300 * scale,
                    top: (TOP + 44) * scale,
                    fontSize: 20 * scale,
                    color: tactile.inactive,
                  },
                  fineStyle,
                ]}
              >
                FINE ×¼
              </Animated.Text>
            )}
          </View>
        )}
        <GestureDetector gesture={control.trackGesture}>
          <View
            collapsable={false}
            testID="tactile-track"
            {...adjustable}
            accessibilityLabel={title}
            accessibilityHint={t(
              "Drag to adjust, or use increment and decrement.",
              "Kéo để điều chỉnh, hoặc dùng thao tác tăng và giảm.",
            )}
            style={{
              position: "absolute",
              left: g.track.x * scale,
              top:
                (TOP + g.track.y + 52) * scale - Math.max(48, 104 * scale) / 2,
              width: g.track.width * scale,
              height: Math.max(48, 104 * scale),
            }}
          />
        </GestureDetector>
        <GestureDetector gesture={control.dialGesture}>
          <View
            collapsable={false}
            testID="tactile-dial"
            {...adjustable}
            accessibilityLabel={t(
              `${title} dial`,
              `Núm ${title.toLowerCase()}`,
            )}
            accessibilityHint={t(
              "Drag left or right to adjust.",
              "Kéo sang trái hoặc phải để điều chỉnh.",
            )}
            style={{
              position: "absolute",
              left: (g.dial.x - 100) * scale,
              top: (TOP + g.dial.y - 100) * scale,
              width: 200 * scale,
              height: 200 * scale,
            }}
          />
        </GestureDetector>
        <View style={StyleSheet.absoluteFill} pointerEvents="box-none">
          {modeAction(0)}
          {modeAction(1)}
        </View>
      </Animated.View>
    </View>
  );
}
const styles = StyleSheet.create({
  largeReadout: {
    flexDirection: "row",
    flexWrap: "wrap",
    justifyContent: "space-between",
    gap: space.sm,
  },
});
