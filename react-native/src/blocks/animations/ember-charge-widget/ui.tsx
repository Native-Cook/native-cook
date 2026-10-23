import type { ReactNode } from "react";
import { StyleSheet, Text, View, type StyleProp, type ViewStyle } from "react-native";
import { useReducedMotion } from "react-native-reanimated";

// The little chrome around the widget: its typeface, the motion settings and
// a quiet instruction line under it.

// Manrope, in the two weights the block ships (assets/, loaded in index.tsx).
// Its round, open numerals sit well on glass, it has tabular figures and it
// covers Vietnamese. Each weight is its own family name, so styles pick a
// face and leave fontWeight alone; asking for a weight on top would have the
// system fake one.
export const face = { regular: "Manrope_400Regular", semibold: "Manrope_600SemiBold" } as const;

export function useMotionPrefs() {
  const reduced = useReducedMotion();
  return { reduced, haptics: true };
}

export function Hint({ children, style, color }: { children: ReactNode; style?: StyleProp<ViewStyle>; color: string }) {
  return (
    <View pointerEvents="none" style={[styles.hintRow, style]}>
      <Text style={[styles.hint, { color }]} accessibilityLiveRegion="polite">
        {children}
      </Text>
    </View>
  );
}

const styles = StyleSheet.create({
  hintRow: { alignItems: "center", paddingHorizontal: 20 },
  hint: { fontFamily: face.regular, fontSize: 13, lineHeight: 18, textAlign: "center" },
});
