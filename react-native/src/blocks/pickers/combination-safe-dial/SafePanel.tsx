import type { JSX } from "react";
import { useState } from "react";
import { StyleSheet, Text, TextInput, View } from "react-native";
import Animated, { useAnimatedProps, useAnimatedReaction, useAnimatedStyle } from "react-native-reanimated";
import type { SharedValue } from "react-native-reanimated";
import { scheduleOnRN } from "react-native-worklets";

import { SafeLockButton } from "./SafeLockButton";
import { dialNumber, doorAt, seg } from "./safeMotion";
import { BRASS, CARD, GREEN, HAIR, INK, MUTE, PANEL, RED, ROWS, VAULT } from "./safeScene";

export const GUTTER = 20;
export const PANEL_R = 28;
const TOP = 16;
const HEAD_H = 32;
const GAP = 12;
const SLOT_H = 64;
const ROW_H = 38;
const BOTTOM = 14;
export const PANEL_H = TOP + HEAD_H + GAP + SLOT_H + GAP + ROW_H * ROWS.length + BOTTOM;

Animated.addWhitelistedNativeProps({ text: true });
const AnimatedTextInput = Animated.createAnimatedComponent(TextInput);

function twoDigits(v: number): string {
  "worklet";
  return v < 10 ? `0${v}` : `${v}`;
}

function slotText(i: number, count: number, entry: number, dial: number): string {
  "worklet";
  if (i < count) return twoDigits(entry);
  if (i === count) return twoDigits(dialNumber(dial));
  return "--";
}

type SlotProps = {
  index: number;
  initial: string;
  count: SharedValue<number>;
  entry: SharedValue<number>;
  dial: SharedValue<number>;
  wrong: SharedValue<number>;
};

/** One number of the combination. Its digits are set on the UI thread: the dial passes numbers too fast for React. */
function Slot({ index, initial, count, entry, dial, wrong }: SlotProps): JSX.Element {
  const props = useAnimatedProps(() => {
    const text = slotText(index, count.get(), entry.get(), dial.get());
    return { text, defaultValue: text } as never;
  });
  const ring = useAnimatedStyle(() => ({ opacity: count.get() === index ? 1 : 0 }));
  const filled = useAnimatedStyle(() => ({ opacity: index < count.get() ? 1 : 0 }));
  const bad = useAnimatedStyle(() => ({ opacity: wrong.get() }));
  return (
    <View style={styles.slot}>
      <Animated.View pointerEvents="none" style={[styles.slotFill, filled]} />
      <Animated.View pointerEvents="none" style={[styles.slotRing, ring]} />
      <Animated.View pointerEvents="none" style={[styles.slotBad, bad]} />
      <AnimatedTextInput
        accessibilityLabel={`Number ${index + 1}`}
        animatedProps={props}
        caretHidden
        defaultValue={initial}
        editable={false}
        pointerEvents="none"
        style={styles.slotText}
        underlineColorAndroid="transparent"
      />
    </View>
  );
}

type RowProps = {
  label: string;
  value: string;
  last: boolean;
  unlock: SharedValue<number>;
};

function Row({ label, value, last, unlock }: RowProps): JSX.Element {
  const shown = useAnimatedStyle(() => ({ opacity: seg(doorAt(unlock.get()), 0.35, 0.9) }));
  const hidden = useAnimatedStyle(() => ({ opacity: 1 - seg(doorAt(unlock.get()), 0.1, 0.55) }));
  return (
    <View style={[styles.row, !last && styles.rowLine]}>
      <Text style={styles.rowLabel}>{label}</Text>
      <View style={styles.rowValue}>
        <Animated.Text style={[styles.rowDots, hidden]}>••••</Animated.Text>
        <Animated.Text style={[styles.rowNumber, shown]}>{value}</Animated.Text>
      </View>
    </View>
  );
}

export type SafePanelProps = {
  width: number;
  bottom: number;
  /** The dial (unwrapped numbers), how many numbers are in, and the three entries. */
  dial: SharedValue<number>;
  count: SharedValue<number>;
  e0: SharedValue<number>;
  e1: SharedValue<number>;
  e2: SharedValue<number>;
  /** The unlock timeline (s), and the wrong code's flash, 0..1. */
  unlock: SharedValue<number>;
  wrong: SharedValue<number>;
  /** 1 while a finger may lock the vault again. */
  canLock: SharedValue<number>;
  initialCount: number;
  initialEntries: readonly [number, number, number];
  onLock: () => void;
};

/** The vault's sheet: the three numbers, whether it is locked, and what the vault holds. */
export function SafePanel({ width, bottom, dial, count, e0, e1, e2, unlock, wrong, canLock, initialCount, initialEntries, onLock }: SafePanelProps): JSX.Element {
  const [lockable, setLockable] = useState(false);
  useAnimatedReaction(
    () => canLock.get() > 0.5,
    (v, prev) => {
      if (v !== prev) scheduleOnRN(setLockable, v);
    },
  );
  const locked = useAnimatedStyle(() => ({ opacity: (1 - seg(doorAt(unlock.get()), 0.1, 0.5)) * (1 - wrong.get()) }));
  const opened = useAnimatedStyle(() => ({ opacity: seg(doorAt(unlock.get()), 0.3, 0.8) }));
  const bad = useAnimatedStyle(() => ({ opacity: wrong.get() }));
  const inner = Math.min(width, 560) - GUTTER * 2;
  const entries = [e0, e1, e2];
  return (
    <View style={[styles.panel, { height: PANEL_H + bottom, paddingBottom: bottom }]}>
      <View pointerEvents="none" style={styles.edge} />
      <View style={[styles.column, { width: inner }]}>
        <View style={styles.head}>
          <Text style={styles.title}>{VAULT}</Text>
          {lockable ? (
            <SafeLockButton onPress={onLock} />
          ) : (
            <View style={styles.status}>
              <Animated.Text style={[styles.statusText, locked]}>Locked</Animated.Text>
              <Animated.Text style={[styles.statusText, styles.statusOpen, opened]}>Unlocked</Animated.Text>
              <Animated.Text style={[styles.statusText, styles.statusBad, bad]}>Wrong code</Animated.Text>
            </View>
          )}
        </View>
        <View style={styles.slots}>
          {entries.map((entry, i) => (
            <Slot
              count={count}
              dial={dial}
              entry={entry}
              index={i}
              initial={i < initialCount ? String(initialEntries[i]).padStart(2, "0") : i === initialCount ? "00" : "--"}
              key={i}
              wrong={wrong}
            />
          ))}
        </View>
        <View style={styles.rows}>
          {ROWS.map((r, i) => (
            <Row key={r.label} label={r.label} last={i === ROWS.length - 1} unlock={unlock} value={r.value} />
          ))}
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
    alignItems: "center",
    backgroundColor: PANEL,
    borderTopLeftRadius: PANEL_R,
    borderTopRightRadius: PANEL_R,
    borderCurve: "continuous",
    boxShadow: "0 -8px 24px rgba(38,32,20,0.2)",
  },
  edge: { position: "absolute", left: 0, right: 0, top: 0, height: PANEL_R, borderTopLeftRadius: PANEL_R, borderTopRightRadius: PANEL_R, borderTopWidth: StyleSheet.hairlineWidth, borderColor: "rgba(255,255,255,0.95)" },
  column: { paddingTop: TOP },
  head: { height: HEAD_H, flexDirection: "row", alignItems: "center", justifyContent: "space-between" },
  title: { fontSize: 20, lineHeight: 26, fontWeight: "700", letterSpacing: -0.3, color: INK },
  status: { width: 120, height: 22 },
  statusText: { position: "absolute", right: 0, top: 0, fontSize: 15, lineHeight: 22, fontWeight: "500", color: MUTE },
  statusOpen: { color: GREEN, fontWeight: "600" },
  statusBad: { color: RED, fontWeight: "600" },
  slots: { marginTop: GAP, height: SLOT_H, flexDirection: "row", gap: 8 },
  slot: { flex: 1, height: SLOT_H, borderRadius: 16, borderCurve: "continuous", backgroundColor: CARD, borderWidth: StyleSheet.hairlineWidth, borderColor: HAIR, alignItems: "center", justifyContent: "center", overflow: "hidden" },
  slotFill: { ...StyleSheet.absoluteFill, backgroundColor: "rgba(31,66,56,0.07)" },
  slotRing: { ...StyleSheet.absoluteFill, borderRadius: 16, borderCurve: "continuous", borderWidth: 2, borderColor: BRASS },
  slotBad: { ...StyleSheet.absoluteFill, backgroundColor: "rgba(179,58,43,0.14)" },
  slotText: { width: "100%", padding: 0, textAlign: "center", fontSize: 32, lineHeight: 38, fontWeight: "700", color: INK, fontVariant: ["tabular-nums"] },
  rows: { marginTop: GAP, borderRadius: 16, borderCurve: "continuous", backgroundColor: CARD, borderWidth: StyleSheet.hairlineWidth, borderColor: HAIR, paddingHorizontal: 16 },
  row: { height: ROW_H - 0.34, flexDirection: "row", alignItems: "center", justifyContent: "space-between" },
  rowLine: { borderBottomWidth: StyleSheet.hairlineWidth, borderBottomColor: HAIR },
  rowLabel: { fontSize: 15, lineHeight: 20, color: INK },
  rowValue: { width: 64, height: 20 },
  rowDots: { position: "absolute", right: 0, fontSize: 15, lineHeight: 20, letterSpacing: 1, color: MUTE },
  rowNumber: { position: "absolute", right: 0, fontSize: 15, lineHeight: 20, fontWeight: "600", color: INK, fontVariant: ["tabular-nums"] },
});
