import type { JSX } from "react";
import { Pressable, StyleSheet, Text, View } from "react-native";

import type { WatchControlsProps } from "./WatchControls.types";
import { DIALS, INK } from "./watchScene";

/** The dial picker and the reserve button where SwiftUI is not available (Android). iOS: WatchControls.ios.tsx. */
export function WatchControls({ dial, onDial, reserved, onReserve, width }: WatchControlsProps): JSX.Element {
  return (
    <View style={{ width, gap: 14 }}>
      <View style={styles.segments}>
        {DIALS.map((d, i) => (
          <Pressable accessibilityRole="button" accessibilityState={{ selected: dial === i }} key={d.name} onPress={() => onDial(i)} style={[styles.segment, dial === i && styles.segmentOn]}>
            <Text style={[styles.segmentText, dial === i && styles.segmentTextOn]}>{d.name}</Text>
          </Pressable>
        ))}
      </View>
      <Pressable accessibilityRole="button" onPress={onReserve} style={styles.button}>
        <Text style={styles.buttonText}>{reserved ? "Reserved" : "Reserve in a boutique"}</Text>
      </Pressable>
    </View>
  );
}

const styles = StyleSheet.create({
  segments: { flexDirection: "row", height: 34, borderRadius: 9, backgroundColor: "rgba(118,118,128,0.14)", padding: 2 },
  segment: { flex: 1, borderRadius: 7, alignItems: "center", justifyContent: "center" },
  segmentOn: { backgroundColor: "#FFFFFF", shadowColor: "#000", shadowOpacity: 0.1, shadowRadius: 4, shadowOffset: { width: 0, height: 1 }, elevation: 2 },
  segmentText: { fontSize: 14, fontWeight: "500", color: INK },
  segmentTextOn: { fontWeight: "600" },
  button: { height: 52, borderRadius: 26, backgroundColor: INK, alignItems: "center", justifyContent: "center" },
  buttonText: { fontSize: 17, fontWeight: "600", color: "#FFFFFF" },
});
