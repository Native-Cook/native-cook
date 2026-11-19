import type { JSX } from "react";
import { StyleSheet, Text, View } from "react-native";

import { RingControls } from "./RingControls";
import { ATELIER, CARAT_MODEL, INK, METALS, MUTE, PRICE_BASE, PRICE_PER_CT, RING_NAME, STONES } from "./ringScene";

export const GUTTER = 20;

/** The ring's price for a metal, a stone and a weight (invented, believable). */
export function priceOf(metal: number, stone: number, carat: number): string {
  const ctFactor = stone === 0 ? 1 : 0.35;
  const v = PRICE_BASE + METALS[metal].price + STONES[stone].price + (carat - CARAT_MODEL) * PRICE_PER_CT * ctFactor;
  const r = Math.round(v / 50) * 50;
  return `$${r.toLocaleString("en-US")}`;
}

export type RingPanelProps = {
  width: number;
  top: number;
  bottom: number;
  metal: number;
  stone: number;
  carat: number;
  booked: boolean;
  onMetal: (i: number) => void;
  onStone: (i: number) => void;
  onCarat: (ct: number) => void;
  onBook: () => void;
};

/** The atelier's name at the top; the ring's name, price, options and the booking button at the bottom. */
export function RingPanel(p: RingPanelProps): JSX.Element {
  const inner = Math.min(p.width, 560) - GUTTER * 2;
  const metalName = METALS[p.metal].name.toLowerCase();
  return (
    <View pointerEvents="box-none" style={StyleSheet.absoluteFill}>
      <View pointerEvents="none" style={[styles.header, { top: p.top + 8 }]}>
        <Text style={styles.atelier}>{ATELIER}</Text>
        <Text style={styles.headerNote}>Proposal planner</Text>
      </View>
      <View pointerEvents="box-none" style={[styles.panel, { bottom: p.bottom + 12, width: inner }]}>
        <View style={styles.titleRow}>
          <Text style={styles.name}>{RING_NAME}</Text>
          <Text style={styles.price}>{priceOf(p.metal, p.stone, p.carat)}</Text>
        </View>
        <Text style={styles.line}>{`${p.carat.toFixed(2)} ct ${STONES[p.stone].name.toLowerCase()} in ${metalName}`}</Text>
        <View style={styles.controls}>
          <RingControls
            booked={p.booked}
            carat={p.carat}
            metal={p.metal}
            onBook={p.onBook}
            onCarat={p.onCarat}
            onMetal={p.onMetal}
            onStone={p.onStone}
            stone={p.stone}
            width={inner}
          />
        </View>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  header: { position: "absolute", left: GUTTER, right: GUTTER, flexDirection: "row", justifyContent: "space-between", alignItems: "baseline" },
  atelier: { fontSize: 20, fontWeight: "700", color: INK, letterSpacing: -0.2 },
  headerNote: { fontSize: 13, color: MUTE },
  panel: { position: "absolute", left: GUTTER },
  titleRow: { flexDirection: "row", justifyContent: "space-between", alignItems: "baseline", gap: 12 },
  name: { flexShrink: 1, fontSize: 30, fontWeight: "700", color: INK, letterSpacing: -0.5 },
  price: { fontSize: 19, fontWeight: "600", color: INK, fontVariant: ["tabular-nums"] },
  line: { fontSize: 15, color: MUTE, marginTop: 2 },
  controls: { marginTop: 16 },
});
