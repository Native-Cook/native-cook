import type { JSX } from "react";
import { Pressable, StyleSheet, Text, View } from "react-native";

import { CARD, INK, MUTE, PRESET_NAMES } from "./mixerScene";
import type { PresetPickerProps } from "./PresetPicker.types";

/** Intro, Interview, Outro where SwiftUI is not available (Android). iOS: PresetPicker.ios.tsx. */
export function PresetPicker({ preset, onPreset, width }: PresetPickerProps): JSX.Element {
  return (
    <View accessibilityRole="radiogroup" style={[styles.track, { width }]}>
      {PRESET_NAMES.map((p, i) => (
        <Pressable accessibilityLabel={`${p} preset`} accessibilityRole="radio" accessibilityState={{ selected: i === preset }} key={p} onPress={() => onPreset(i)} style={[styles.cell, i === preset && styles.cellOn]}>
          <Text style={[styles.label, i === preset && styles.labelOn]}>{p}</Text>
        </Pressable>
      ))}
    </View>
  );
}

const styles = StyleSheet.create({
  track: { height: 36, padding: 2, flexDirection: "row", borderRadius: 10, backgroundColor: "rgba(118,116,110,0.24)" },
  cell: { flex: 1, alignItems: "center", justifyContent: "center", borderRadius: 8 },
  cellOn: { backgroundColor: CARD, boxShadow: "0 1px 3px rgba(0,0,0,0.4)" },
  label: { fontSize: 14, lineHeight: 18, fontWeight: "500", color: MUTE },
  labelOn: { color: INK, fontWeight: "600" },
});
