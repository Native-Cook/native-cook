import type { JSX } from "react";
import { Pressable, StyleSheet, Text, View } from "react-native";

import type { BrewControlsProps } from "./BrewControls.types";
import { ACCENT, CARD, DOSES, INK, MUTE, PANEL } from "./espressoScene";

/** Single / Double and Brew where SwiftUI is not available (Android). iOS: BrewControls.ios.tsx. */
export function BrewControls({ dose, onDose, label, busy, onBrew, pickerWidth, buttonWidth }: BrewControlsProps): JSX.Element {
  return (
    <View style={styles.row}>
      <View accessibilityRole="radiogroup" style={[styles.track, { width: pickerWidth }]}>
        {DOSES.map((d, i) => (
          <Pressable accessibilityLabel={d} accessibilityRole="radio" accessibilityState={{ selected: i === dose }} key={d} onPress={() => onDose(i)} style={[styles.cell, i === dose && styles.cellOn]}>
            <Text style={[styles.label, i === dose && styles.labelOn]}>{d}</Text>
          </Pressable>
        ))}
      </View>
      <View style={[styles.box, { width: buttonWidth }]}>
        <Pressable accessibilityRole="button" accessibilityState={{ disabled: busy }} disabled={busy} hitSlop={6} onPress={onBrew} style={[styles.button, busy && styles.busy]}>
          <Text style={styles.buttonLabel}>{label}</Text>
        </Pressable>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  row: { flexDirection: "row", alignItems: "center", justifyContent: "space-between" },
  track: { height: 36, padding: 2, flexDirection: "row", borderRadius: 10, backgroundColor: "rgba(118,116,110,0.16)" },
  cell: { flex: 1, alignItems: "center", justifyContent: "center", borderRadius: 8 },
  cellOn: { backgroundColor: CARD, boxShadow: "0 1px 3px rgba(34,38,42,0.2)" },
  label: { fontSize: 14, lineHeight: 18, fontWeight: "500", color: MUTE },
  labelOn: { color: INK, fontWeight: "600" },
  box: { alignItems: "center", justifyContent: "center" },
  button: { height: 36, paddingHorizontal: 18, borderRadius: 18, backgroundColor: ACCENT, alignItems: "center", justifyContent: "center" },
  busy: { opacity: 0.55 },
  buttonLabel: { fontSize: 15, lineHeight: 20, fontWeight: "600", color: PANEL },
});
