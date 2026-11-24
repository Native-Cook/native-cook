import type { JSX } from "react";
import { useCallback, useState } from "react";
import { StyleSheet, Text, View } from "react-native";
import { useAnimatedReaction } from "react-native-reanimated";
import type { SharedValue } from "react-native-reanimated";
import { scheduleOnRN } from "react-native-worklets";

import { SwitchControls } from "./SwitchControls";
import { CARD, INK, LIGHT_NAME, MUTE, OTHER_LIGHTS_ON, PANEL, ROOM } from "./switchScene";

export const GUTTER = 20;
/** The panel's height above the bottom safe area. */
export const PANEL_H = 252;
/** The panel's top corners, and how far it overlaps the stage. */
export const PANEL_R = 28;

export type SwitchPanelProps = {
  width: number;
  bottom: number;
  /** The rocker, 0..1: the light is on from 0.5. */
  press: SharedValue<number>;
  bright: SharedValue<number>;
  sliding: SharedValue<boolean>;
  initialOn: boolean;
  onBrightness: (v: number) => void;
  onSliding: (active: boolean) => void;
};

/**
 * The room's sheet: its name, how many lights are on, and the sconce's row with the brightness slider. Its React
 * state lives here, not in the screen: the film's slider steps and the on/off label never re-render the 3D stage.
 */
export function SwitchPanel({ width, bottom, press, bright, sliding, initialOn, onBrightness, onSliding }: SwitchPanelProps): JSX.Element {
  const [lit, setLit] = useState(initialOn);
  const [value, setValue] = useState(1);
  useAnimatedReaction(
    () => press.get() >= 0.5,
    (cur, prev) => {
      if (prev != null && cur !== prev) scheduleOnRN(setLit, cur);
    },
  );
  // the film moves the slider in steps of 2 %; a finger's own drag is left alone
  useAnimatedReaction(
    () => (sliding.get() ? -1 : Math.round(bright.get() * 50) * 2),
    (v, prev) => {
      if (v >= 0 && v !== prev) scheduleOnRN(setValue, v / 100);
    },
  );
  const change = useCallback(
    (v: number) => {
      setValue(v);
      onBrightness(v);
    },
    [onBrightness],
  );
  const inner = Math.min(width, 560) - GUTTER * 2;
  const count = OTHER_LIGHTS_ON + (lit ? 1 : 0);
  return (
    <View style={[styles.panel, { height: PANEL_H + bottom, paddingBottom: bottom }]}>
      <View style={styles.edge} />
      <View style={[styles.column, { width: inner }]}>
        <Text style={styles.room}>{ROOM}</Text>
        <Text style={styles.count}>{`${count} lights on`}</Text>
        <View style={styles.card}>
          <View style={styles.row}>
            <Text style={styles.name}>{LIGHT_NAME}</Text>
            <Text style={[styles.state, lit && styles.stateOn]}>{lit ? "On" : "Off"}</Text>
          </View>
          <View style={[styles.row, styles.brightRow]}>
            <Text style={styles.label}>Brightness</Text>
            <Text style={styles.value}>{`${Math.round(value * 100)}%`}</Text>
          </View>
          <SwitchControls onChange={change} onEditing={onSliding} value={value} width={inner - 32} />
        </View>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  panel: {
    position: "absolute",
    left: 0,
    right: 0,
    bottom: 0,
    backgroundColor: PANEL,
    borderTopLeftRadius: PANEL_R,
    borderTopRightRadius: PANEL_R,
    alignItems: "center",
    // the sheet's long soft shadow up the wall
    shadowColor: "#0B0D14",
    shadowOpacity: 0.28,
    shadowRadius: 24,
    shadowOffset: { width: 0, height: -8 },
    elevation: 12,
  },
  edge: { position: "absolute", left: 0, right: 0, top: 0, height: PANEL_R, borderTopLeftRadius: PANEL_R, borderTopRightRadius: PANEL_R, borderTopWidth: StyleSheet.hairlineWidth, borderColor: "rgba(255,255,255,0.9)" },
  column: { paddingTop: 22 },
  room: { fontSize: 28, lineHeight: 34, fontWeight: "700", letterSpacing: -0.4, color: INK },
  count: { marginTop: 2, fontSize: 15, lineHeight: 20, color: MUTE, fontVariant: ["tabular-nums"] },
  card: {
    marginTop: 18,
    paddingHorizontal: 16,
    paddingTop: 14,
    paddingBottom: 12,
    borderRadius: 20,
    backgroundColor: CARD,
    borderWidth: StyleSheet.hairlineWidth,
    borderColor: "rgba(29,27,24,0.07)",
    // the card's tight contact shadow
    shadowColor: "#3A2E1E",
    shadowOpacity: 0.08,
    shadowRadius: 10,
    shadowOffset: { width: 0, height: 3 },
    elevation: 2,
  },
  row: { flexDirection: "row", alignItems: "baseline", justifyContent: "space-between" },
  brightRow: { marginTop: 12, marginBottom: 4 },
  name: { fontSize: 17, lineHeight: 22, fontWeight: "600", color: INK },
  state: { fontSize: 15, lineHeight: 20, fontWeight: "500", color: MUTE },
  stateOn: { color: "#B36F0E" },
  label: { fontSize: 15, lineHeight: 20, color: MUTE },
  value: { fontSize: 15, lineHeight: 20, fontWeight: "500", color: INK, fontVariant: ["tabular-nums"] },
});
