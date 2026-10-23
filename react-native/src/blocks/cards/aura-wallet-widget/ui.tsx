import { useEffect, useRef, useState } from "react";
import { Platform, StyleSheet, Text, View, useWindowDimensions } from "react-native";
import Svg, { Path } from "react-native-svg";
import Animated, {
  type SharedValue,
  Easing,
  ReduceMotion,
  useAnimatedStyle,
  useReducedMotion,
  useSharedValue,
  withSpring,
  withTiming,
} from "react-native-reanimated";
import { AURA_ICON_GRID } from "./aura-icons";
import { auraFlow } from "./aura-flow";
import type { AuraHintKind } from "./aura-machine";
import { auraDesign as D, auraMotion } from "./aura-palette";

// The wallet's small shared pieces: system type, line boxes and touch feedback.
// The page's hint can grow with Dynamic Type; the drawing inside stays fixed.
export const systemFace = Platform.OS === "ios" ? "System" : "sans-serif";

// Keep the existing exports for consumers; the drawing owns their values now.
export const AURA_LINES = D.lines;
export const AURA_PAGE = D.page;
export const AURA_TOUCH = D.touch;
export const AURA_DETAILS = D.details;

// Centre in design points before scaling, so fractional widths keep the same
// rounding as the fixed drawing.
export function centeredLine(cy: number, height: number, u: number) {
  return { top: (cy - height / 2) * u, height: height * u };
}

// Grid-unit strokes retain their weight when the info dot and action marks
// use different sizes. The info mark keeps the SVG's default line join.
export function AuraIcon({
  path,
  side,
  u,
  color,
  rounded = false,
}: {
  path: string;
  side: number;
  u: number;
  color: string;
  rounded?: boolean;
}) {
  return (
    <Svg
      width={side * u}
      height={side * u}
      viewBox={`0 0 ${AURA_ICON_GRID} ${AURA_ICON_GRID}`}
      accessible={false}
    >
      <Path
        d={path}
        fill="none"
        stroke={color}
        strokeWidth={D.actions.stroke * AURA_ICON_GRID / side}
        strokeLinecap="round"
        {...(rounded ? { strokeLinejoin: "round" as const } : {})}
      />
    </Svg>
  );
}

export function useMotionPrefs() {
  const reduced = useReducedMotion();
  return { reduced, haptics: true };
}

export function touchInsets(width: number, height = width) {
  const horizontal = Math.max(0, (D.touch.minimum - width) / 2);
  const vertical = Math.max(0, (D.touch.minimum - height) / 2);
  return {
    left: horizontal,
    right: horizontal,
    top: vertical,
    bottom: vertical,
  };
}

/** The face and the material answer the same touch, including cancellation. */
export function useAuraPress(press: SharedValue<number>) {
  const { reduced } = useMotionPrefs();
  const contentStyle = useAnimatedStyle(() => ({
    transform: [{ scale: reduced ? 1 : 1 + (auraMotion.pressScale - 1) * press.get() }],
  }));
  return {
    contentStyle,
    onPressIn: () => press.set(withTiming(1, {
      duration: auraMotion.pressIn,
      easing: Easing.bezier(...auraMotion.easeOut),
      reduceMotion: ReduceMotion.Never,
    })),
    // Reduce Motion keeps the content still, but the glass must still answer.
    onPressOut: () => press.set(withSpring(0, { ...auraMotion.release, reduceMotion: ReduceMotion.Never })),
  };
}

// A new sentence crossfades; amount steps replace only its number. Reserve
// two scalable lines so translated copy never shifts the card.
export function Hint({ children, color, kind }: { children: string; color: string; kind: AuraHintKind }) {
  const [copy, setCopy] = useState({ current: children, previous: children, kind });
  const fade = useSharedValue(1);
  const { fontScale } = useWindowDimensions();
  const lastKind = useRef(kind);
  if (children !== copy.current || kind !== copy.kind) {
    setCopy({ current: children, previous: kind === copy.kind ? copy.previous : copy.current, kind });
  }
  useEffect(() => {
    if (kind === lastKind.current) return;
    lastKind.current = kind;
    fade.set(0);
    fade.set(withTiming(1, {
      duration: auraFlow.views.hint.fade,
      easing: Easing.bezier(...auraMotion.easeOut),
      reduceMotion: ReduceMotion.Never,
    }));
  }, [kind, fade]);
  const oldStyle = useAnimatedStyle(() => ({ opacity: 1 - fade.get() }));
  const newStyle = useAnimatedStyle(() => ({ opacity: fade.get() }));
  const line = { color, height: D.page.hintLine * fontScale * 2 };
  return (
    <View accessible accessibilityLabel={children} accessibilityLiveRegion="polite" style={{ height: line.height }}>
      <Animated.View accessible={false} accessibilityElementsHidden importantForAccessibility="no-hide-descendants" style={[StyleSheet.absoluteFill, oldStyle]}>
        <Text numberOfLines={2} style={[styles.hint, line]}>{copy.previous}</Text>
      </Animated.View>
      <Animated.View accessible={false} accessibilityElementsHidden importantForAccessibility="no-hide-descendants" style={[StyleSheet.absoluteFill, newStyle]}>
        <Text numberOfLines={2} style={[styles.hint, line]}>{copy.current}</Text>
      </Animated.View>
    </View>
  );
}

const styles = StyleSheet.create({
  hint: {
    fontFamily: systemFace,
    fontSize: D.page.hintSize,
    lineHeight: D.page.hintLine,
    textAlign: "center",
  },
});
