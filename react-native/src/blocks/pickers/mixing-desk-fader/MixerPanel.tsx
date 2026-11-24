import type { JSX } from "react";
import { useCallback, useRef, useState } from "react";
import { StyleSheet, Text, TextInput, View } from "react-native";
import Animated, { useAnimatedProps, useAnimatedReaction, useAnimatedStyle } from "react-native-reanimated";
import type { DerivedValue, SharedValue } from "react-native-reanimated";
import { scheduleOnRN } from "react-native-worklets";

import { dbLabel, LOOP, presetGhost } from "./mixerMotion";
import { CARD, CHANNEL_COLOURS, EPISODE, HAIR, INK, MUTE, NAMES, PANEL, PRESET_NAMES, REC } from "./mixerScene";
import { PresetPicker } from "./PresetPicker";

export const GUTTER = 20;
export const PANEL_R = 28;
const TOP = 18;
const HEAD_H = 30;
const GAP = 14;
const PICKER_H = 36;
const STRIPS_H = 62;
const BOTTOM = 16;
export const PANEL_H = TOP + HEAD_H + GAP + PICKER_H + GAP + STRIPS_H + BOTTOM;
const DOT = 44;
/** The REC light breathes this many times a loop. */
const REC_BREATHS = 3;
/** The picker may report a value the film just gave it: such a report within this long (ms) is not a finger. */
const ECHO_MS = 700;

Animated.addWhitelistedNativeProps({ text: true });
const AnimatedTextInput = Animated.createAnimatedComponent(TextInput);

type StripProps = { c: number; pose: SharedValue<number[]> };

/** One channel's name and level, set on the UI thread. */
function Strip({ c, pose }: StripProps): JSX.Element {
  const props = useAnimatedProps(() => {
    const p = pose.get();
    const text = dbLabel(p[c], p[8 + c]);
    return { text, defaultValue: text } as never;
  });
  return (
    <View style={styles.strip}>
      <View style={styles.nameRow}>
        <View style={[styles.swatch, { backgroundColor: CHANNEL_COLOURS[c] }]} />
        <Text style={styles.name}>{NAMES[c]}</Text>
      </View>
      <AnimatedTextInput animatedProps={props} caretHidden defaultValue="0.0 dB" editable={false} pointerEvents="none" style={styles.value} underlineColorAndroid="transparent" />
    </View>
  );
}

export type MixerPanelProps = {
  width: number;
  bottom: number;
  t: SharedValue<number>;
  filming: DerivedValue<number>;
  pose: SharedValue<number[]>;
  /** The preset the control shows (the film and the return write it). */
  preset: SharedValue<number>;
  onPreset: (preset: number) => void;
};

/** The session's sheet. Its React state lives here: a preset change never re-renders the 3D stage. */
export function MixerPanel({ width, bottom, t, filming, pose, preset, onPreset }: MixerPanelProps): JSX.Element {
  const [shown, setShown] = useState(0);
  const given = useRef<{ v: number; at: number }[]>([]);
  const give = useCallback((v: number) => {
    given.current.push({ v, at: Date.now() });
    if (given.current.length > 8) given.current.shift();
    setShown(v);
  }, []);
  useAnimatedReaction(
    () => preset.get(),
    (v, prev) => {
      if (prev != null && v !== prev) scheduleOnRN(give, v);
    },
  );
  const change = useCallback(
    (v: number) => {
      const now = Date.now();
      if (given.current.some((g) => g.v === v && now - g.at < ECHO_MS)) return;
      setShown(v);
      onPreset(v);
    },
    [onPreset],
  );
  const inner = Math.min(width, 560) - GUTTER * 2;
  const ghost = useAnimatedStyle(() => {
    const g = [0, 0, 0];
    presetGhost(t.get(), g);
    const cell = inner / PRESET_NAMES.length;
    return {
      opacity: g[0] * 0.9 * filming.get(),
      transform: [{ translateX: cell * (g[2] + 0.5) - DOT / 2 }, { translateY: TOP + HEAD_H + GAP + PICKER_H / 2 - DOT / 2 }, { scale: 1 - 0.16 * g[1] }],
    };
  });
  const rec = useAnimatedStyle(() => ({ opacity: 0.45 + 0.55 * (0.5 + 0.5 * Math.cos((2 * Math.PI * REC_BREATHS * t.get()) / LOOP)) }));
  return (
    <View style={[styles.panel, { height: PANEL_H + bottom, paddingBottom: bottom }]}>
      <View pointerEvents="none" style={styles.edge} />
      <View style={[styles.column, { width: inner }]}>
        <View style={styles.head}>
          <Text style={styles.title}>{EPISODE}</Text>
          <View style={styles.recPill}>
            <Animated.View style={[styles.recDot, rec]} />
            <Text style={styles.recText}>REC</Text>
          </View>
        </View>
        <View style={styles.picker}>
          <PresetPicker onPreset={change} preset={shown} width={inner} />
        </View>
        <View style={styles.strips}>
          {NAMES.map((n, c) => (
            <View key={n} style={styles.stripCell}>
              {c > 0 ? <View style={styles.rule} /> : null}
              <Strip c={c} pose={pose} />
            </View>
          ))}
        </View>
        <Animated.View pointerEvents="none" style={[styles.dot, ghost]} />
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  panel: { position: "absolute", left: 0, right: 0, bottom: 0, alignItems: "center", backgroundColor: PANEL, borderTopLeftRadius: PANEL_R, borderTopRightRadius: PANEL_R, borderCurve: "continuous", boxShadow: "0 -8px 28px rgba(0,0,0,0.45)" },
  edge: { position: "absolute", left: 0, right: 0, top: 0, height: PANEL_R, borderTopLeftRadius: PANEL_R, borderTopRightRadius: PANEL_R, borderTopWidth: StyleSheet.hairlineWidth, borderColor: "rgba(255,255,255,0.14)" },
  column: { paddingTop: TOP },
  head: { height: HEAD_H, flexDirection: "row", alignItems: "center", justifyContent: "space-between" },
  title: { fontSize: 20, lineHeight: 26, fontWeight: "700", letterSpacing: -0.3, color: INK },
  recPill: { height: 26, paddingHorizontal: 10, flexDirection: "row", alignItems: "center", gap: 6, borderRadius: 13, backgroundColor: "rgba(255,90,74,0.14)" },
  recDot: { width: 8, height: 8, borderRadius: 4, backgroundColor: REC },
  recText: { fontSize: 13, lineHeight: 16, fontWeight: "700", color: REC, letterSpacing: 0.4 },
  picker: { marginTop: GAP, height: PICKER_H, justifyContent: "center" },
  strips: { marginTop: GAP, height: STRIPS_H, flexDirection: "row", alignItems: "center", borderRadius: 16, borderCurve: "continuous", backgroundColor: CARD, borderWidth: StyleSheet.hairlineWidth, borderColor: HAIR },
  stripCell: { flex: 1, flexDirection: "row", alignItems: "center", height: STRIPS_H },
  rule: { width: StyleSheet.hairlineWidth, height: 34, backgroundColor: HAIR },
  strip: { flex: 1, paddingLeft: 12 },
  nameRow: { flexDirection: "row", alignItems: "center", gap: 6 },
  swatch: { width: 7, height: 7, borderRadius: 3.5 },
  name: { fontSize: 13, lineHeight: 18, color: MUTE },
  value: { padding: 0, marginTop: 2, fontSize: 15, lineHeight: 20, fontWeight: "600", color: INK, fontVariant: ["tabular-nums"] },
  dot: { position: "absolute", left: 0, top: 0, width: DOT, height: DOT, borderRadius: DOT / 2, backgroundColor: "rgba(243,240,234,0.14)", borderWidth: 1.5, borderColor: "rgba(243,240,234,0.5)" },
});
