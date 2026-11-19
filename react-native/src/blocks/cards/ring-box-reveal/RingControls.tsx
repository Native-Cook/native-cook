import type { JSX } from "react";
import { Pressable, StyleSheet, Text, View } from "react-native";

import type { RingControlsProps } from "./RingControls.types";
import { CARAT_MAX, CARAT_MIN, CARAT_STEP, INK, METALS, MUTE, NAVY, STONES } from "./ringScene";

function Segments({ items, value, onChange }: { items: readonly string[]; value: number; onChange: (i: number) => void }) {
  return (
    <View style={styles.segments}>
      {items.map((name, i) => (
        <Pressable accessibilityRole="button" accessibilityState={{ selected: value === i }} key={name} onPress={() => onChange(i)} style={[styles.segment, value === i && styles.segmentOn]}>
          <Text numberOfLines={1} style={[styles.segmentText, value === i && styles.segmentTextOn]}>
            {name}
          </Text>
        </Pressable>
      ))}
    </View>
  );
}

/** The ring's options where SwiftUI is not available (Android): segments, a carat stepper and the button. */
export function RingControls(p: RingControlsProps): JSX.Element {
  const step = (d: number) => p.onCarat(Math.min(CARAT_MAX, Math.max(CARAT_MIN, p.carat + d * CARAT_STEP)));
  return (
    <View style={{ width: p.width, gap: 12 }}>
      <View style={styles.row}>
        <Text style={styles.label}>Metal</Text>
        <Segments items={METALS.map((m) => m.short)} onChange={p.onMetal} value={p.metal} />
      </View>
      <View style={styles.row}>
        <Text style={styles.label}>Stone</Text>
        <Segments items={STONES.map((s) => s.name)} onChange={p.onStone} value={p.stone} />
      </View>
      <View style={styles.row}>
        <Text style={styles.label}>Carat</Text>
        <View style={styles.stepper}>
          <Pressable accessibilityLabel="Less" accessibilityRole="button" onPress={() => step(-1)} style={styles.stepButton}>
            <Text style={styles.stepText}>−</Text>
          </Pressable>
          <Text style={styles.carat}>{`${p.carat.toFixed(2)} ct`}</Text>
          <Pressable accessibilityLabel="More" accessibilityRole="button" onPress={() => step(1)} style={styles.stepButton}>
            <Text style={styles.stepText}>+</Text>
          </Pressable>
        </View>
      </View>
      <Pressable accessibilityRole="button" onPress={p.onBook} style={styles.button}>
        <Text style={styles.buttonText}>{p.booked ? "Viewing requested" : "Book a private viewing"}</Text>
      </Pressable>
    </View>
  );
}

const styles = StyleSheet.create({
  row: { flexDirection: "row", alignItems: "center", gap: 8 },
  label: { width: 56, fontSize: 13, fontWeight: "500", color: MUTE },
  segments: { flex: 1, flexDirection: "row", height: 32, borderRadius: 9, backgroundColor: "rgba(118,118,128,0.14)", padding: 2 },
  segment: { flex: 1, borderRadius: 7, alignItems: "center", justifyContent: "center" },
  segmentOn: { backgroundColor: "#FFFFFF", elevation: 2 },
  segmentText: { fontSize: 13, fontWeight: "500", color: INK },
  segmentTextOn: { fontWeight: "600" },
  stepper: { flex: 1, flexDirection: "row", alignItems: "center", justifyContent: "space-between" },
  stepButton: { width: 44, height: 32, borderRadius: 9, backgroundColor: "rgba(118,118,128,0.14)", alignItems: "center", justifyContent: "center" },
  stepText: { fontSize: 18, fontWeight: "500", color: INK },
  carat: { fontSize: 15, fontWeight: "600", color: INK, fontVariant: ["tabular-nums"] },
  button: { height: 52, borderRadius: 26, backgroundColor: NAVY, alignItems: "center", justifyContent: "center" },
  buttonText: { fontSize: 17, fontWeight: "600", color: "#FFFFFF" },
});
