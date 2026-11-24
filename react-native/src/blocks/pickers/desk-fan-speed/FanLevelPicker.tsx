import type { JSX } from "react";
import { Pressable, StyleSheet, Text, View } from "react-native";

import type { FanLevelPickerProps } from "./FanLevelPicker.types";
import { CARD, INK, LEVELS, MUTE } from "./fanScene";

/** Off, 1, 2, 3 where SwiftUI is not available (Android). iOS: FanLevelPicker.ios.tsx. */
export function FanLevelPicker({ level, onLevel, width }: FanLevelPickerProps): JSX.Element {
  return (
    <View accessibilityRole="radiogroup" style={[styles.track, { width }]}>
      {LEVELS.map((l, i) => (
        <Pressable accessibilityLabel={i === 0 ? "Off" : `Speed ${l}`} accessibilityRole="radio" accessibilityState={{ selected: i === level }} key={l} onPress={() => onLevel(i)} style={[styles.cell, i === level && styles.cellOn]}>
          <Text style={[styles.label, i === level && styles.labelOn]}>{l}</Text>
        </Pressable>
      ))}
    </View>
  );
}

const styles = StyleSheet.create({
  track: { height: 36, padding: 2, flexDirection: "row", borderRadius: 10, backgroundColor: "rgba(118,122,116,0.16)" },
  cell: { flex: 1, alignItems: "center", justifyContent: "center", borderRadius: 8 },
  cellOn: { backgroundColor: CARD, boxShadow: "0 1px 3px rgba(31,42,38,0.2)" },
  label: { fontSize: 14, lineHeight: 18, fontWeight: "500", color: MUTE },
  labelOn: { color: INK, fontWeight: "600" },
});
