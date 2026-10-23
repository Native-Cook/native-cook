import { useEffect, useState } from "react";
import { type LayoutRectangle, Platform, Pressable, StyleSheet, Text, View } from "react-native";
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
import { referralTone } from "./referral-palette";

// The card's chrome: the curves it shares, the motion settings, and its one
// button.

export const EASE_OUT = Easing.bezier(0.23, 1, 0.32, 1);
export const EASE_IN_OUT = Easing.bezier(0.77, 0, 0.175, 1);

// The same diameter belongs to the collapsed body and the comet's first head.
export const BUTTON_DOT = 12;
export const BUTTON_MOTION = { collapse: 140, fade: 100, reformAfter: 120, reform: 420, focus: 220 };
export const BUTTON_RADIUS = 24;
export type ButtonFx = {
  shape: SharedValue<number>;
  label: SharedValue<number>;
  comet: SharedValue<number>;
  press: SharedValue<number>;
};

/**
 * The button's body at one moment, between its full size and the dot. The
 * glass under the card draws it and the view over it turns into the dot, so
 * both ask here. `hidden` is the stretch in which the comet's head is the dot.
 */
export function buttonBody(
  size: { width: number; height: number },
  shape: number,
  press: number,
  comet: number,
  flightDuration: number,
) {
  "worklet";
  const t = comet * flightDuration;
  const scale = 1 - 0.04 * press * (1 - shape);
  const radius = Math.max(BUTTON_RADIUS, size.height / 2);
  return {
    width: size.width + (BUTTON_DOT - size.width) * shape,
    height: size.height + (BUTTON_DOT - size.height) * shape,
    radius: radius + (BUTTON_DOT / 2 - radius) * shape,
    scale,
    hidden: shape >= 1 || (comet < 1 && t >= 0 && t < BUTTON_MOTION.reformAfter),
  };
}

// Copy uses the platform's system face, as in the reference.
export const systemFace = Platform.OS === "ios" ? "System" : "sans-serif";

export function useMotionPrefs() {
  const reduced = useReducedMotion();
  return { reduced, haptics: true };
}

/**
 * A short fade that still runs with Reduce Motion on. Reanimated's default
 * skips an animation there and jumps to its end, which would turn every
 * state change into a cut.
 */
export function gentle(to: number, duration = 200) {
  "worklet";
  return withTiming(to, { duration, easing: EASE_OUT, reduceMotion: ReduceMotion.Never });
}

/** 1 while the body is at least nine tenths regrown, 0 once it is under six tenths. */
function fits(shape: number) {
  "worklet";
  return 1 - Math.min(Math.max((shape - 0.1) / 0.3, 0), 1);
}

/**
 * The card's action. `label` shows at rest; `alt`, when given, takes its place
 * with a short vertical drift. Both labels reserve room before the swap.
 * Its body is glass, drawn by the card's surface; the view here is
 * only the white dot that body turns into.
 */
export function ActionButton({
  label,
  alt,
  showAlt,
  onPress,
  reduced,
  fx,
  flightDuration,
  maxWidth,
  onLayout,
}: {
  label: string;
  alt: string;
  showAlt: boolean;
  onPress: () => void;
  reduced: boolean;
  fx: ButtonFx;
  flightDuration: number;
  maxWidth: number;
  onLayout: (layout: LayoutRectangle) => void;
}) {
  const [size, setSize] = useState({ width: 0, height: 48 });
  const [labelWidths, setLabelWidths] = useState({ first: 0, second: 0 });
  const [labelHeights, setLabelHeights] = useState({ first: 22, second: 22 });
  // Even point widths keep both edges and the launch centre on whole pixels.
  const measuredWidth = Math.max(labelWidths.first, labelWidths.second);
  const width = measuredWidth === 0 ? maxWidth : Math.min(maxWidth, Math.ceil((measuredWidth + 48) / 2) * 2);
  const height = Math.max(48, Math.ceil((Math.max(labelHeights.first, labelHeights.second) + 24) / 2) * 2);
  const press = fx.press;
  const swap = useSharedValue(showAlt ? 1 : 0);

  useEffect(() => {
    swap.set(reduced ? gentle(showAlt ? 1 : 0, 120) : withTiming(showAlt ? 1 : 0, { duration: 240, easing: EASE_OUT }));
  }, [showAlt, reduced, swap]);

  const body = useAnimatedStyle(() => {
    const shape = reduced ? 0 : fx.shape.get();
    const b = buttonBody(size, shape, press.get(), fx.comet.get(), flightDuration);
    return {
      width: b.width,
      height: b.height,
      left: (size.width - b.width) / 2,
      top: (size.height - b.height) / 2,
      borderRadius: b.radius,
      // White only close to the dot: as the body grows back it is clear glass
      // again by half its size, not a white pill.
      opacity: !reduced && b.hidden ? 0 : Math.pow(Math.min(Math.max(shape, 0), 1), 4),
      transform: [{ scale: b.scale }],
    };
  });
  // The label waits for room: it is not shown while the body is narrower than it.
  const first = useAnimatedStyle(() => ({
    opacity: (1 - swap.get()) * (reduced ? 1 : (1 - fx.label.get()) * fits(fx.shape.get())),
    filter: `blur(${reduced ? 0 : 4 * fx.label.get()}px)`,
    transform: [{ translateY: reduced ? 0 : swap.get() * -4 }],
  }));
  const second = useAnimatedStyle(() => ({
    opacity: swap.get() * (reduced ? 1 : (1 - fx.label.get()) * fits(fx.shape.get())),
    filter: `blur(${reduced ? 0 : 4 * fx.label.get()}px)`,
    transform: [{ translateY: reduced ? 0 : (1 - swap.get()) * 4 }],
  }));

  return (
    <Pressable
      style={{ width, height }}
      accessibilityRole="button"
      accessibilityLabel={showAlt ? alt : label}
      hitSlop={8}
      pressRetentionOffset={24}
      onPressIn={() => press.set(withTiming(1, { duration: 120, easing: EASE_OUT, reduceMotion: ReduceMotion.Never }))}
      onPressOut={() =>
        press.set(
          reduced ? gentle(0, 120) : withSpring(0, { duration: 300, dampingRatio: 0.8 }),
        )
      }
      onPress={onPress}
      onLayout={(e) => {
        const layout = e.nativeEvent.layout;
        setSize({ width: layout.width, height: layout.height });
        onLayout(layout);
      }}
    >
      {/* Only this childless body changes size. The hit area and copy stay put. */}
      <Animated.View pointerEvents="none" style={[styles.body, body]} />
      {/* Intrinsic measurements stay independent of the snapped button width. */}
      <View pointerEvents="none" accessible={false} accessibilityElementsHidden importantForAccessibility="no-hide-descendants" style={[styles.measure, { width: maxWidth }]}>
        <Text style={styles.label} onLayout={(e) => { const first = e.nativeEvent.layout.width; setLabelWidths((was) => ({ ...was, first })); }}>{label}</Text>
        <Text style={styles.label} onLayout={(e) => { const second = e.nativeEvent.layout.width; setLabelWidths((was) => ({ ...was, second })); }}>{alt}</Text>
      </View>
      <View style={styles.button}>
        <Animated.Text style={[styles.label, first]} onLayout={(e) => { const first = e.nativeEvent.layout.height; setLabelHeights((was) => ({ ...was, first })); }}>
          {label}
        </Animated.Text>
        <View pointerEvents="none" style={styles.alt}>
          <Animated.Text style={[styles.label, second]} onLayout={(e) => { const second = e.nativeEvent.layout.height; setLabelHeights((was) => ({ ...was, second })); }}>
            {alt}
          </Animated.Text>
        </View>
      </View>
    </Pressable>
  );
}

export function Hint({ children }: { children: string }) {
  return (
    <Text style={styles.hint} accessibilityLiveRegion="polite">
      {children}
    </Text>
  );
}

const styles = StyleSheet.create({
  button: { flex: 1, paddingHorizontal: 24, alignItems: "center", justifyContent: "center" },
  measure: { position: "absolute", alignItems: "flex-start", opacity: 0 },
  body: { position: "absolute", backgroundColor: referralTone.ring },
  label: {
    fontFamily: systemFace,
    fontSize: 16,
    lineHeight: 22,
    fontWeight: "500",
    letterSpacing: -0.2,
    color: referralTone.onButton,
    textAlign: "center",
    includeFontPadding: false,
  },
  alt: { position: "absolute", left: 24, right: 24, top: 0, bottom: 0, alignItems: "center", justifyContent: "center" },
  hint: {
    fontFamily: systemFace,
    fontSize: 13,
    lineHeight: 18,
    textAlign: "center",
    color: referralTone.pageHint,
  },
});
