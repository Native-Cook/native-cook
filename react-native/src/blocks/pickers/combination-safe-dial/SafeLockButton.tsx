import type { JSX } from "react";
import { Pressable, StyleSheet, Text } from "react-native";

import type { SafeLockButtonProps } from "./SafeLockButton.types";
import { GREEN, PANEL } from "./safeScene";

/** Locks the vault again (Android; iOS uses the system's button in SafeLockButton.ios.tsx). */
export function SafeLockButton({ onPress }: SafeLockButtonProps): JSX.Element {
  return (
    <Pressable accessibilityLabel="Lock the vault" accessibilityRole="button" hitSlop={8} onPress={onPress} style={styles.button}>
      <Text style={styles.label}>Lock</Text>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  button: { height: 32, paddingHorizontal: 16, borderRadius: 16, backgroundColor: GREEN, alignItems: "center", justifyContent: "center" },
  label: { fontSize: 15, lineHeight: 20, fontWeight: "600", color: PANEL },
});
