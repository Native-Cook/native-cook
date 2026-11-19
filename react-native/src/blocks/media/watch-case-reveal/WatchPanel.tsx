import type { JSX } from "react";
import { StyleSheet, Text, TextInput, View } from "react-native";
import Animated, { useAnimatedProps, useAnimatedStyle } from "react-native-reanimated";
import type { SharedValue } from "react-native-reanimated";

import { WatchControls } from "./WatchControls";
import { clamp01, specsOf } from "./watchMotion";
import { BRAND, INK, LINE, MODEL, MUTE, PRICE, SPECS } from "./watchScene";

Animated.addWhitelistedNativeProps({ text: true });
const AnimatedTextInput = Animated.createAnimatedComponent(TextInput);

export const GUTTER = 20;

/** One spec: its number counts up as the pillow rises (a tiny animated text, so the count never re-renders React). */
function Spec({ i, open }: { i: number; open: SharedValue<number> }) {
  const s = SPECS[i];
  const props = useAnimatedProps(() => {
    const k = clamp01((specsOf(open.get()) - i * 0.08) / 0.84);
    const v = `${Math.round(s.value * k)}`;
    return { text: v, defaultValue: v } as never;
  });
  const fade = useAnimatedStyle(() => {
    const k = clamp01((specsOf(open.get()) - i * 0.08) / 0.84);
    return { opacity: 0.35 + 0.65 * k };
  });
  return (
    <Animated.View style={[styles.spec, fade]}>
      <View style={styles.specRow}>
        <AnimatedTextInput animatedProps={props} editable={false} style={[styles.specValue, { width: String(s.value).length * 17 + 6 }]} underlineColorAndroid="transparent" />
        <Text style={styles.specUnit}>{s.unit}</Text>
      </View>
      <Text style={styles.specLabel}>{s.label}</Text>
    </Animated.View>
  );
}

export type WatchPanelProps = {
  width: number;
  top: number;
  bottom: number;
  open: SharedValue<number>;
  dial: number;
  onDial: (i: number) => void;
  reserved: boolean;
  onReserve: () => void;
};

/** The house name at the top; the watch's name, price, specs, the dial picker and the reserve button at the bottom. */
export function WatchPanel({ width, top, bottom, open, dial, onDial, reserved, onReserve }: WatchPanelProps): JSX.Element {
  const inner = Math.min(width, 560) - GUTTER * 2;
  return (
    <View pointerEvents="box-none" style={StyleSheet.absoluteFill}>
      <View pointerEvents="none" style={[styles.header, { top: top + 8 }]}>
        <Text style={styles.brand}>{BRAND}</Text>
        <Text style={styles.headerNote}>New arrival</Text>
      </View>
      <View pointerEvents="box-none" style={[styles.panel, { bottom: bottom + 12, width: inner }]}>
        <View style={styles.titleRow}>
          <Text style={styles.model}>{MODEL}</Text>
          <Text style={styles.price}>{PRICE}</Text>
        </View>
        <Text style={styles.line}>{LINE}</Text>
        <View style={styles.specs}>
          {SPECS.map((s, i) => (
            <Spec i={i} key={s.label} open={open} />
          ))}
        </View>
        <WatchControls dial={dial} onDial={onDial} onReserve={onReserve} reserved={reserved} width={inner} />
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  header: { position: "absolute", left: GUTTER, right: GUTTER, flexDirection: "row", justifyContent: "space-between", alignItems: "baseline" },
  brand: { fontSize: 22, fontWeight: "700", color: INK, letterSpacing: -0.2 },
  headerNote: { fontSize: 13, color: MUTE },
  panel: { position: "absolute", left: GUTTER },
  titleRow: { flexDirection: "row", justifyContent: "space-between", alignItems: "baseline" },
  model: { fontSize: 32, fontWeight: "700", color: INK, letterSpacing: -0.5 },
  price: { fontSize: 19, fontWeight: "600", color: INK, fontVariant: ["tabular-nums"] },
  line: { fontSize: 15, color: MUTE, marginTop: 2 },
  specs: { flexDirection: "row", marginTop: 16, marginBottom: 18 },
  spec: { flex: 1 },
  specRow: { flexDirection: "row", alignItems: "baseline", gap: 2 },
  specValue: { fontSize: 24, fontWeight: "600", color: INK, fontVariant: ["tabular-nums"], padding: 0, paddingHorizontal: 0, margin: 0 },
  specUnit: { fontSize: 15, fontWeight: "500", color: INK },
  specLabel: { fontSize: 13, color: MUTE, marginTop: 1 },
});
