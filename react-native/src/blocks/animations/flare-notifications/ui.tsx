import type { ReactNode } from "react";
import { Platform, Pressable, StyleSheet, Text, View, type StyleProp, type ViewStyle } from "react-native";
import { useReducedMotion } from "react-native-reanimated";
import { SymbolView } from "expo-symbols";

// The small amount of chrome Flare shares with the other showcase specimens:
// a quiet instruction line, a graphite pill button, and the motion settings.

// Copy uses the platform's system face, as in the reference.
export const systemFace = Platform.OS === "ios" ? "System" : "sans-serif";

const graphite = "#2C2C2C";
const onGraphite = "#EEEEEE";

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

export function Pill({ label, onPress }: { label: string; onPress: () => void }) {
  return (
    <Pressable
      accessibilityRole="button"
      onPress={onPress}
      style={({ pressed }) => [styles.pill, pressed && { transform: [{ scale: 0.96 }] }]}
    >
      {Platform.OS === "ios" ? (
        <SymbolView name="arrow.counterclockwise" tintColor={onGraphite} size={13} accessible={false} />
      ) : (
        <Text style={styles.glyph} accessible={false}>
          ↺
        </Text>
      )}
      <Text style={styles.pillText}>{label}</Text>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  hintRow: { position: "absolute", left: 20, right: 20, alignItems: "center" },
  hint: { fontFamily: systemFace, fontSize: 13, lineHeight: 18, textAlign: "center" },
  pill: {
    flexDirection: "row",
    alignItems: "center",
    gap: 7,
    minHeight: 36,
    paddingHorizontal: 15,
    borderRadius: 999,
    backgroundColor: graphite,
  },
  glyph: { fontSize: 15, lineHeight: 18, color: onGraphite },
  pillText: { fontFamily: systemFace, fontSize: 13, fontWeight: "500", color: onGraphite },
});
