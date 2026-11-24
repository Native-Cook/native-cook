import type { JSX } from "react";
import { useCallback, useRef, useState } from "react";
import { StyleSheet, Text, TextInput, View } from "react-native";
import Animated, { useAnimatedProps, useAnimatedReaction, useAnimatedStyle } from "react-native-reanimated";
import type { DerivedValue, SharedValue } from "react-native-reanimated";
import { scheduleOnRN } from "react-native-worklets";

import { FanLevelPicker } from "./FanLevelPicker";
import { rpmAt, tapGhost, wattsAt } from "./fanMotion";
import { CARD, HAIR, INK, LEVELS, MUTE, PANEL, ROOM } from "./fanScene";

export const GUTTER = 20;
export const PANEL_R = 28;
const TOP = 18;
const HEAD_H = 30;
const GAP = 14;
const PICKER_H = 36;
const STATS_H = 74;
const BOTTOM = 16;
export const PANEL_H = TOP + HEAD_H + GAP + PICKER_H + GAP + STATS_H + BOTTOM;
const DOT = 44;
/** The picker may report a value the film just gave it: such a report within this long (ms) is not a finger. */
const ECHO_MS = 700;

Animated.addWhitelistedNativeProps({ text: true });
const AnimatedTextInput = Animated.createAnimatedComponent(TextInput);

type ReadingProps = {
  label: string;
  unit: string;
  speed: SharedValue<number>;
  kind: "rpm" | "watts";
};

/** A number that follows the motor, set on the UI thread. */
function Reading({ label, unit, speed, kind }: ReadingProps): JSX.Element {
  const props = useAnimatedProps(() => {
    const v = kind === "rpm" ? rpmAt(speed.get()) : wattsAt(speed.get());
    const text = `${v}`;
    return { text, defaultValue: text } as never;
  });
  return (
    <View style={styles.reading}>
      <Text style={styles.readLabel}>{label}</Text>
      <View style={styles.readRow}>
        <AnimatedTextInput accessibilityLabel={`${label}, ${unit}`} animatedProps={props} caretHidden defaultValue="0" editable={false} pointerEvents="none" style={styles.readValue} underlineColorAndroid="transparent" />
        <Text style={styles.readUnit}>{unit}</Text>
      </View>
    </View>
  );
}

export type FanPanelProps = {
  width: number;
  bottom: number;
  t: SharedValue<number>;
  filming: DerivedValue<number>;
  /** The selected level (the film and the return write it; this sheet mirrors it into the native control). */
  level: SharedValue<number>;
  speed: SharedValue<number>;
  onLevel: (level: number) => void;
};

/** The fan's sheet. Its React state lives here: a level change never re-renders the 3D stage. */
export function FanPanel({ width, bottom, t, filming, level, speed, onLevel }: FanPanelProps): JSX.Element {
  const [shown, setShown] = useState(0);
  const given = useRef<{ v: number; at: number }[]>([]);
  const give = useCallback((v: number) => {
    given.current.push({ v, at: Date.now() });
    if (given.current.length > 8) given.current.shift();
    setShown(v);
  }, []);
  useAnimatedReaction(
    () => level.get(),
    (v, prev) => {
      if (prev != null && v !== prev) scheduleOnRN(give, v);
    },
  );
  const change = useCallback(
    (v: number) => {
      const now = Date.now();
      if (given.current.some((g) => g.v === v && now - g.at < ECHO_MS)) return;
      setShown(v);
      onLevel(v);
    },
    [onLevel],
  );
  const inner = Math.min(width, 560) - GUTTER * 2;
  const ghost = useAnimatedStyle(() => {
    const g = [0, 0, 0];
    tapGhost(t.get(), g);
    const cell = inner / LEVELS.length;
    return {
      opacity: g[0] * 0.9 * filming.get(),
      transform: [{ translateX: cell * (g[2] + 0.5) - DOT / 2 }, { translateY: TOP + HEAD_H + GAP + PICKER_H / 2 - DOT / 2 }, { scale: 1 - 0.16 * g[1] }],
    };
  });
  return (
    <View style={[styles.panel, { height: PANEL_H + bottom, paddingBottom: bottom }]}>
      <View pointerEvents="none" style={styles.edge} />
      <View style={[styles.column, { width: inner }]}>
        <View style={styles.head}>
          <Text style={styles.title}>{ROOM}</Text>
          <Text style={styles.state}>{shown === 0 ? "Off" : `Speed ${shown}`}</Text>
        </View>
        <View style={styles.picker}>
          <FanLevelPicker level={shown} onLevel={change} width={inner} />
        </View>
        <View style={styles.stats}>
          <Reading kind="rpm" label="Motor" speed={speed} unit="rpm" />
          <View style={styles.rule} />
          <Reading kind="watts" label="Power" speed={speed} unit="W" />
        </View>
        <Animated.View pointerEvents="none" style={[styles.dot, ghost]} />
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  panel: { position: "absolute", left: 0, right: 0, bottom: 0, alignItems: "center", backgroundColor: PANEL, borderTopLeftRadius: PANEL_R, borderTopRightRadius: PANEL_R, borderCurve: "continuous", boxShadow: "0 -8px 24px rgba(60,48,24,0.18)" },
  edge: { position: "absolute", left: 0, right: 0, top: 0, height: PANEL_R, borderTopLeftRadius: PANEL_R, borderTopRightRadius: PANEL_R, borderTopWidth: StyleSheet.hairlineWidth, borderColor: "rgba(255,255,255,0.95)" },
  column: { paddingTop: TOP },
  head: { height: HEAD_H, flexDirection: "row", alignItems: "center", justifyContent: "space-between" },
  title: { fontSize: 20, lineHeight: 26, fontWeight: "700", letterSpacing: -0.3, color: INK },
  state: { fontSize: 15, lineHeight: 20, fontWeight: "500", color: MUTE, fontVariant: ["tabular-nums"] },
  picker: { marginTop: GAP, height: PICKER_H, justifyContent: "center" },
  stats: { marginTop: GAP, height: STATS_H, flexDirection: "row", alignItems: "center", borderRadius: 16, borderCurve: "continuous", backgroundColor: CARD, borderWidth: StyleSheet.hairlineWidth, borderColor: HAIR },
  rule: { width: StyleSheet.hairlineWidth, height: 40, backgroundColor: HAIR },
  reading: { flex: 1, paddingHorizontal: 16 },
  readLabel: { fontSize: 13, lineHeight: 18, color: MUTE },
  readRow: { height: 34, flexDirection: "row", alignItems: "baseline", gap: 4 },
  readValue: { minWidth: 76, padding: 0, fontSize: 26, lineHeight: 32, fontWeight: "700", color: INK, fontVariant: ["tabular-nums"] },
  readUnit: { fontSize: 14, lineHeight: 18, color: MUTE },
  dot: { position: "absolute", left: 0, top: 0, width: DOT, height: DOT, borderRadius: DOT / 2, backgroundColor: "rgba(47,102,87,0.16)", borderWidth: 1.5, borderColor: "rgba(47,102,87,0.34)" },
});
