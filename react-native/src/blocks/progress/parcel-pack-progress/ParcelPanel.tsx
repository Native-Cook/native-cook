import { SymbolView } from "expo-symbols";
import type { JSX } from "react";
import { Pressable, StyleSheet, Text, View } from "react-native";
import Animated, { useAnimatedStyle } from "react-native-reanimated";
import type { SharedValue } from "react-native-reanimated";

import { activeAlpha, activeRow, checkScale, clamp01, doneAt, headlineAlpha, pulseAt } from "./parcelMotion";
import { ARRIVES, CARD, GREEN, GREEN_SOFT, HAIR, HEADLINES, INK, MUTE, PANEL, STEP_N, STEPS, TRACK } from "./parcelScene";

export const GUTTER = 20;
export const PANEL_R = 28;
/** A step's row, and the sheet's height above the bottom safe area. */
export const ROW_H = 56;
const TOP = 22;
const HEAD_H = 34;
const SUB_H = 22;
const CARD_GAP = 16;
const BOTTOM = 16;
export const PANEL_H = TOP + HEAD_H + SUB_H + CARD_GAP + ROW_H * STEP_N + BOTTOM;
/** The step's mark, its inset in the card, and the line that joins two marks. */
const MARK = 28;
const ROW_PAD = 14;
const LINK_GAP = 4;
const LINK_H = ROW_H - MARK - LINK_GAP * 2;
const PILL_INSET = 5;

type RowProps = {
  index: number;
  t: SharedValue<number>;
  onPress: (index: number) => void;
};

/** One step: a ring while it waits, a ring with a beating dot while it is in progress, a check when it is done. */
function StepRow({ index, t, onPress }: RowProps): JSX.Element {
  const step = STEPS[index];
  const doing = useAnimatedStyle(() => {
    const tt = t.get();
    const a = clamp01(1 - Math.abs(activeRow(tt) - index)) * clamp01(activeAlpha(tt)) * (1 - doneAt(index, tt));
    return { opacity: a };
  });
  const beat = useAnimatedStyle(() => ({ transform: [{ scale: 0.72 + 0.28 * pulseAt(t.get()) }] }));
  const done = useAnimatedStyle(() => {
    const tt = t.get();
    return { opacity: doneAt(index, tt), transform: [{ scale: checkScale(index, tt) }] };
  });
  const words = useAnimatedStyle(() => {
    const tt = t.get();
    const near = clamp01(1 - Math.abs(activeRow(tt) - index)) * clamp01(activeAlpha(tt));
    return { opacity: 0.46 + 0.54 * Math.max(doneAt(index, tt), near) };
  });
  const time = useAnimatedStyle(() => ({ opacity: doneAt(index, t.get()) }));
  return (
    <Pressable accessibilityHint="Shows the parcel at this step" accessibilityLabel={step.title} accessibilityRole="button" onPress={() => onPress(index)} style={styles.row}>
      <View style={styles.mark}>
        <View style={styles.ring} />
        <Animated.View style={[styles.ring, styles.ringDoing, doing]}>
          <Animated.View style={[styles.dot, beat]} />
        </Animated.View>
        <Animated.View style={[styles.check, done]}>
          <SymbolView fallback={<Text style={styles.tick}>✓</Text>} name="checkmark" size={13} tintColor="#FFFFFF" weight="bold" />
        </Animated.View>
      </View>
      <Animated.Text style={[styles.title, words]}>{step.title}</Animated.Text>
      <Animated.Text style={[styles.time, time]}>{step.at}</Animated.Text>
    </Pressable>
  );
}

/** The line from step `index` to the next: it fills as the step is done. */
function Link({ index, t }: { index: number; t: SharedValue<number> }): JSX.Element {
  const fill = useAnimatedStyle(() => ({ transform: [{ scaleY: doneAt(index, t.get()) }] }));
  return (
    <View pointerEvents="none" style={[styles.link, { top: index * ROW_H + ROW_H / 2 + MARK / 2 + LINK_GAP }]}>
      <Animated.View style={[styles.linkFill, fill]} />
    </View>
  );
}

function Headline({ index, t }: { index: number; t: SharedValue<number> }): JSX.Element {
  const style = useAnimatedStyle(() => {
    const a = headlineAlpha(index, t.get());
    return { opacity: a, transform: [{ translateY: (1 - a) * 6 }] };
  });
  return (
    <Animated.Text numberOfLines={1} style={[styles.headline, style]}>
      {HEADLINES[index]}
    </Animated.Text>
  );
}

export type ParcelPanelProps = {
  width: number;
  bottom: number;
  t: SharedValue<number>;
  /** A tap on a step. */
  onStep: (index: number) => void;
};

/**
 * The order's sheet: what is happening now, when it arrives, and the four steps. Everything in it is drawn from the
 * film's time on the UI thread; it holds no React state.
 */
export function ParcelPanel({ width, bottom, t, onStep }: ParcelPanelProps): JSX.Element {
  const inner = Math.min(width, 560) - GUTTER * 2;
  const pill = useAnimatedStyle(() => {
    const tt = t.get();
    return { opacity: clamp01(activeAlpha(tt)), transform: [{ translateY: activeRow(tt) * ROW_H }] };
  });
  return (
    <View style={[styles.panel, { height: PANEL_H + bottom, paddingBottom: bottom }]}>
      <View pointerEvents="none" style={styles.edge} />
      <View style={[styles.column, { width: inner }]}>
        <View style={styles.head}>
          {HEADLINES.map((h, j) => (
            <Headline index={j} key={h} t={t} />
          ))}
        </View>
        <Text style={styles.sub}>{ARRIVES}</Text>
        <View style={styles.card}>
          <Animated.View pointerEvents="none" style={[styles.pill, pill]} />
          {STEPS.slice(0, STEP_N - 1).map((s, i) => (
            <Link index={i} key={s.title} t={t} />
          ))}
          {STEPS.map((s, i) => (
            <StepRow index={i} key={s.title} onPress={onStep} t={t} />
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
    backgroundColor: PANEL,
    borderTopLeftRadius: PANEL_R,
    borderTopRightRadius: PANEL_R,
    alignItems: "center",
    // the sheet's long soft shadow up the studio floor
    shadowColor: "#3B2F1E",
    shadowOpacity: 0.16,
    shadowRadius: 24,
    shadowOffset: { width: 0, height: -8 },
    elevation: 12,
  },
  edge: { position: "absolute", left: 0, right: 0, top: 0, height: PANEL_R, borderTopLeftRadius: PANEL_R, borderTopRightRadius: PANEL_R, borderTopWidth: StyleSheet.hairlineWidth, borderColor: "rgba(255,255,255,0.95)" },
  column: { paddingTop: TOP },
  head: { height: HEAD_H, justifyContent: "center" },
  headline: { position: "absolute", left: 0, right: 0, fontSize: 26, lineHeight: HEAD_H, fontWeight: "700", letterSpacing: -0.4, color: INK },
  sub: { height: SUB_H, fontSize: 15, lineHeight: SUB_H, color: MUTE, fontVariant: ["tabular-nums"] },
  card: {
    marginTop: CARD_GAP,
    height: ROW_H * STEP_N,
    borderRadius: 20,
    backgroundColor: CARD,
    borderWidth: StyleSheet.hairlineWidth,
    borderColor: HAIR,
    // the card's tight contact shadow
    shadowColor: "#3A2E1E",
    shadowOpacity: 0.07,
    shadowRadius: 10,
    shadowOffset: { width: 0, height: 3 },
    elevation: 2,
  },
  pill: { position: "absolute", left: PILL_INSET, right: PILL_INSET, top: PILL_INSET, height: ROW_H - PILL_INSET * 2, borderRadius: 15, backgroundColor: GREEN_SOFT },
  row: { height: ROW_H, flexDirection: "row", alignItems: "center", paddingHorizontal: ROW_PAD },
  mark: { width: MARK, height: MARK, alignItems: "center", justifyContent: "center" },
  ring: { position: "absolute", left: 0, top: 0, width: MARK, height: MARK, borderRadius: MARK / 2, borderWidth: 2, borderColor: TRACK, alignItems: "center", justifyContent: "center" },
  ringDoing: { borderColor: GREEN },
  dot: { width: 10, height: 10, borderRadius: 5, backgroundColor: GREEN },
  check: { position: "absolute", left: 0, top: 0, width: MARK, height: MARK, borderRadius: MARK / 2, backgroundColor: GREEN, alignItems: "center", justifyContent: "center" },
  tick: { fontSize: 14, fontWeight: "800", color: "#FFFFFF" },
  title: { flex: 1, marginLeft: 14, fontSize: 17, lineHeight: 22, fontWeight: "600", color: INK },
  time: { fontSize: 15, lineHeight: 20, color: MUTE, fontVariant: ["tabular-nums"] },
  link: { position: "absolute", left: ROW_PAD + MARK / 2 - 1, width: 2, height: LINK_H, borderRadius: 1, backgroundColor: TRACK, overflow: "hidden" },
  linkFill: { width: 2, height: LINK_H, backgroundColor: GREEN, transformOrigin: "top" },
});
