import type { JSX } from "react";
import { useCallback, useRef, useState } from "react";
import { StyleSheet, Text, TextInput, View } from "react-native";
import Animated, { useAnimatedProps, useAnimatedReaction, useAnimatedStyle } from "react-native-reanimated";
import type { DerivedValue, SharedValue } from "react-native-reanimated";
import { scheduleOnRN } from "react-native-worklets";

import { BrewControls } from "./BrewControls";
import { barAt, fillAt, lerp, shotSecondsAt, STATUS, statusAt, tapGhost } from "./espressoMotion";
import { ACCENT, CARD, DOSE_G, HAIR, INK, MUTE, PANEL, RECIPE } from "./espressoScene";

export const GUTTER = 20;
export const PANEL_R = 28;
const TOP = 18;
const HEAD_H = 30;
const GAP = 14;
const CONTROLS_H = 40;
const STATS_H = 104;
const BOTTOM = 16;
export const PANEL_H = TOP + HEAD_H + GAP + CONTROLS_H + GAP + STATS_H + BOTTOM;
const BUTTON_W = 128;
const DOT = 44;
/** The picker may report a value the film just gave it: such a report within this long (ms) is not a finger. */
const ECHO_MS = 700;

Animated.addWhitelistedNativeProps({ text: true });
const AnimatedTextInput = Animated.createAnimatedComponent(TextInput);

type Kind = "time" | "yield" | "bar";

function readingAt(kind: Kind, t: number, dose: number): string {
  "worklet";
  if (kind === "time") return shotSecondsAt(t).toFixed(1);
  if (kind === "yield") return (lerp(DOSE_G[0], DOSE_G[1], dose) * fillAt(t)).toFixed(1);
  return barAt(t).toFixed(1);
}

type ReadingProps = { label: string; unit: string; kind: Kind; t: SharedValue<number>; dose: SharedValue<number> };

/** A number that follows the shot, set on the UI thread. */
function Reading({ label, unit, kind, t, dose }: ReadingProps): JSX.Element {
  const props = useAnimatedProps(() => {
    const text = readingAt(kind, t.get(), dose.get());
    return { text, defaultValue: text } as never;
  });
  return (
    <View style={styles.reading}>
      <Text style={styles.readLabel}>{label}</Text>
      <View style={styles.readRow}>
        <AnimatedTextInput animatedProps={props} caretHidden defaultValue="0.0" editable={false} pointerEvents="none" style={styles.readValue} underlineColorAndroid="transparent" />
        <Text style={styles.readUnit}>{unit}</Text>
      </View>
    </View>
  );
}

function buttonLabel(status: number): string {
  if (status === 0) return "Brew";
  if (status === 3) return "Brew again";
  return "Brewing";
}

export type EspressoPanelProps = {
  width: number;
  bottom: number;
  /** The place on the film's timeline the panel shows (the film's t, or a finger's shot). */
  t: SharedValue<number>;
  filming: DerivedValue<number>;
  /** The selected dose (0 / 1; the film and the return write it) and the liquid's dose (eased, 0..1). */
  doseSel: SharedValue<number>;
  dose: SharedValue<number>;
  initialDose: number;
  initialStatus: number;
  onDose: (dose: number) => void;
  onBrew: () => void;
};

/** The shot's sheet. Its React state lives here: a status change never re-renders the 3D stage. */
export function EspressoPanel({ width, bottom, t, filming, doseSel, dose, initialDose, initialStatus, onDose, onBrew }: EspressoPanelProps): JSX.Element {
  const [shown, setShown] = useState(initialDose);
  const [status, setStatus] = useState(initialStatus);
  const given = useRef<{ v: number; at: number }[]>([]);
  const give = useCallback((v: number) => {
    given.current.push({ v, at: Date.now() });
    if (given.current.length > 8) given.current.shift();
    setShown(v);
  }, []);
  useAnimatedReaction(
    () => doseSel.get(),
    (v, prev) => {
      if (prev != null && v !== prev) scheduleOnRN(give, v);
    },
  );
  useAnimatedReaction(
    () => statusAt(t.get()),
    (v, prev) => {
      if (prev != null && v !== prev) scheduleOnRN(setStatus, v);
    },
  );
  const change = useCallback(
    (v: number) => {
      const now = Date.now();
      if (given.current.some((g) => g.v === v && now - g.at < ECHO_MS)) return;
      setShown(v);
      onDose(v);
    },
    [onDose],
  );
  const inner = Math.min(width, 560) - GUTTER * 2;
  const pickerW = inner - BUTTON_W - 12;
  const ghost = useAnimatedStyle(() => {
    const g = [0, 0, 0];
    tapGhost(t.get(), g);
    const x = g[2] === 2 ? inner - BUTTON_W / 2 : pickerW * (g[2] === 0 ? 0.25 : 0.75);
    return {
      opacity: g[0] * 0.9 * filming.get(),
      transform: [{ translateX: x - DOT / 2 }, { translateY: TOP + HEAD_H + GAP + CONTROLS_H / 2 - DOT / 2 }, { scale: 1 - 0.16 * g[1] }],
    };
  });
  const bar = useAnimatedStyle(() => ({ transform: [{ scaleX: Math.max(0.0001, fillAt(t.get())) }] }));
  const busy = status === 1 || status === 2 || status === 4;
  return (
    <View style={[styles.panel, { height: PANEL_H + bottom, paddingBottom: bottom }]}>
      <View pointerEvents="none" style={styles.edge} />
      <View style={[styles.column, { width: inner }]}>
        <View style={styles.head}>
          <Text style={styles.title}>{RECIPE}</Text>
          <Text style={[styles.state, status === 2 && styles.stateLive]}>{STATUS[status]}</Text>
        </View>
        <View style={styles.controls}>
          <BrewControls busy={busy} buttonWidth={BUTTON_W} dose={shown} label={buttonLabel(status)} onBrew={onBrew} onDose={change} pickerWidth={pickerW} />
        </View>
        <View style={styles.stats}>
          <View style={styles.readings}>
            <Reading dose={dose} kind="time" label="Time" t={t} unit="s" />
            <View style={styles.rule} />
            <Reading dose={dose} kind="yield" label="Yield" t={t} unit="g" />
            <View style={styles.rule} />
            <Reading dose={dose} kind="bar" label="Pressure" t={t} unit="bar" />
          </View>
          <View style={styles.track}>
            <Animated.View style={[styles.fill, { width: inner - 32 }, bar]} />
          </View>
          <View style={styles.foot}>
            <Text style={styles.footText}>{`1 : 2 ratio · ${DOSE_G[shown] / 2} g in`}</Text>
            <Text style={styles.footText}>{`Target ${DOSE_G[shown]} g`}</Text>
          </View>
        </View>
        <Animated.View pointerEvents="none" style={[styles.dot, ghost]} />
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  panel: { position: "absolute", left: 0, right: 0, bottom: 0, alignItems: "center", backgroundColor: PANEL, borderTopLeftRadius: PANEL_R, borderTopRightRadius: PANEL_R, borderCurve: "continuous", boxShadow: "0 -8px 24px rgba(60,44,24,0.16)" },
  edge: { position: "absolute", left: 0, right: 0, top: 0, height: PANEL_R, borderTopLeftRadius: PANEL_R, borderTopRightRadius: PANEL_R, borderTopWidth: StyleSheet.hairlineWidth, borderColor: "rgba(255,255,255,0.95)" },
  column: { paddingTop: TOP },
  head: { height: HEAD_H, flexDirection: "row", alignItems: "center", justifyContent: "space-between" },
  title: { fontSize: 20, lineHeight: 26, fontWeight: "700", letterSpacing: -0.3, color: INK },
  state: { fontSize: 15, lineHeight: 20, fontWeight: "500", color: MUTE },
  stateLive: { color: ACCENT },
  controls: { marginTop: GAP, height: CONTROLS_H, justifyContent: "center" },
  stats: { marginTop: GAP, height: STATS_H, paddingHorizontal: 16, paddingTop: 12, borderRadius: 16, borderCurve: "continuous", backgroundColor: CARD, borderWidth: StyleSheet.hairlineWidth, borderColor: HAIR },
  readings: { height: 52, flexDirection: "row", alignItems: "center" },
  rule: { width: StyleSheet.hairlineWidth, height: 36, marginHorizontal: 12, backgroundColor: HAIR },
  reading: { flex: 1 },
  readLabel: { fontSize: 13, lineHeight: 18, color: MUTE },
  readRow: { height: 30, flexDirection: "row", alignItems: "baseline", gap: 3 },
  readValue: { minWidth: 44, padding: 0, fontSize: 22, lineHeight: 28, fontWeight: "700", color: INK, fontVariant: ["tabular-nums"] },
  readUnit: { fontSize: 13, lineHeight: 18, color: MUTE },
  track: { marginTop: 10, height: 4, borderRadius: 2, overflow: "hidden", backgroundColor: "rgba(181,101,43,0.14)" },
  fill: { height: 4, borderRadius: 2, backgroundColor: ACCENT, transformOrigin: "left center" },
  foot: { marginTop: 8, flexDirection: "row", justifyContent: "space-between" },
  footText: { fontSize: 12, lineHeight: 16, color: MUTE, fontVariant: ["tabular-nums"] },
  dot: { position: "absolute", left: 0, top: 0, width: DOT, height: DOT, borderRadius: DOT / 2, backgroundColor: "rgba(181,101,43,0.16)", borderWidth: 1.5, borderColor: "rgba(181,101,43,0.38)" },
});
