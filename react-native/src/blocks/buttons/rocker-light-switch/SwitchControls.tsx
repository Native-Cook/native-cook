import type { JSX } from "react";
import { StyleSheet, View } from "react-native";
import type { GestureResponderEvent } from "react-native";

import type { SwitchControlsProps } from "./SwitchControls.types";
import { BRIGHT_MIN } from "./switchMotion";
import { AMBER } from "./switchScene";

/** The brightness slider where SwiftUI is not available (Android): a plain bar a finger can drag. iOS:
 * SwitchControls.ios.tsx. */
export function SwitchControls({ value, onChange, onEditing, width }: SwitchControlsProps): JSX.Element {
  const k = (value - BRIGHT_MIN) / (1 - BRIGHT_MIN);
  const at = (e: GestureResponderEvent) => {
    const x = Math.min(1, Math.max(0, e.nativeEvent.locationX / width));
    onChange(BRIGHT_MIN + (1 - BRIGHT_MIN) * x);
  };
  return (
    <View
      accessibilityLabel="Brightness"
      accessibilityRole="adjustable"
      onMoveShouldSetResponder={() => true}
      onResponderGrant={(e) => {
        onEditing(true);
        at(e);
      }}
      onResponderMove={at}
      onResponderRelease={() => onEditing(false)}
      onResponderTerminate={() => onEditing(false)}
      onStartShouldSetResponder={() => true}
      style={[styles.hit, { width }]}
    >
      <View pointerEvents="none" style={styles.track}>
        <View style={[styles.fill, { width: `${k * 100}%` }]} />
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  hit: { height: 30, justifyContent: "center" },
  track: { height: 6, borderRadius: 3, backgroundColor: "rgba(120,116,108,0.22)", overflow: "hidden" },
  fill: { height: 6, borderRadius: 3, backgroundColor: AMBER },
});
