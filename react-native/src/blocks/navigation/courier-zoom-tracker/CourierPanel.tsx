import { SymbolView } from "expo-symbols";
import { memo } from "react";
import type { JSX } from "react";
import { StyleSheet, Text, TextInput, View } from "react-native";
import Animated, { useAnimatedProps, useAnimatedStyle, useDerivedValue } from "react-native-reanimated";
import type { SharedValue } from "react-native-reanimated";

import { CourierActions } from "./CourierActions";
import { ACTION, ACTION_GAP } from "./CourierActions.types";
import { distanceText, etaText } from "./courierMotion";

Animated.addWhitelistedNativeProps({ text: true });
const AnimatedTextInput = Animated.createAnimatedComponent(TextInput);

export const INK = "#16181D";
const MUTE = "#6E727B";
const BLUE = "#2F6BFF";
const GREEN = "#1F9D55";

/** The panel's content height (pt) above the home indicator, and where its parts sit (from the panel's top). */
export const PANEL_H = 204;
export const PANEL_PAD = 20;
const ROW_TOP = 132;
/** Centre of the courier row, from the panel's top (the Call / Message buttons sit on it). */
export const ROW_CY = ROW_TOP + 24;

export type CourierPanelProps = {
  width: number;
  bottom: number;
  len: number;
  vanD: SharedValue<number>;
  arrived: SharedValue<number>;
  /** Dims the copy while the screen resets after a touch. */
  dip: SharedValue<number>;
  dialog: number;
  onCall: () => void;
  onMessage: () => void;
  onDismiss: () => void;
  onConfirmCall: () => void;
  onSend: (text: string) => void;
};

/** ETA and distance: computed on the UI thread from the van's progress; React never re-renders for them. */
function Eta({ vanD, len, arrived, dip }: { vanD: SharedValue<number>; len: number; arrived: SharedValue<number>; dip: SharedValue<number> }) {
  // derived strings only wake the text props when the words change, so the texts (and their layout) are committed
  // when the minute or the distance step changes instead of on every frame of the drive
  const titleText = useDerivedValue(() => etaText(len - vanD.get(), len));
  const subText = useDerivedValue(() => distanceText(len - vanD.get()));
  const title = useAnimatedProps(() => {
    const s = titleText.get();
    return { text: s, defaultValue: s } as never;
  });
  const sub = useAnimatedProps(() => {
    const s = subText.get();
    return { text: s, defaultValue: s } as never;
  });
  const going = useAnimatedStyle(() => {
    const a = arrived.get();
    return { opacity: (1 - a) * (1 - dip.get()), transform: [{ translateY: -8 * a }] };
  });
  const done = useAnimatedStyle(() => {
    const a = arrived.get();
    return { opacity: a * (1 - dip.get()), transform: [{ translateY: 8 * (1 - a) }] };
  });
  return (
    <View style={styles.copy}>
      <Animated.View style={[StyleSheet.absoluteFill, going]}>
        <Text style={styles.label}>Arriving in</Text>
        <AnimatedTextInput animatedProps={title} defaultValue="12 min" editable={false} pointerEvents="none" style={styles.title} underlineColorAndroid="transparent" />
        <AnimatedTextInput
          animatedProps={sub}
          defaultValue="3.8 km away · On the way"
          editable={false}
          pointerEvents="none"
          style={styles.sub}
          underlineColorAndroid="transparent"
        />
      </Animated.View>
      <Animated.View style={[StyleSheet.absoluteFill, done]}>
        <Text style={styles.label}>Order 4821 · 2 parcels</Text>
        <View style={styles.arrivedRow}>
          <SymbolView fallback={<View style={styles.tickFallback} />} name="checkmark.circle.fill" size={30} tintColor={GREEN} weight="semibold" />
          <Text style={styles.title}>Arrived</Text>
        </View>
        <Text style={styles.subStatic}>Marco is at your door</Text>
      </Animated.View>
    </View>
  );
}

/** The tracking panel: ETA (or Arrived), the distance, and the courier with Call and Message. */
export const CourierPanel = memo(function CourierPanel(p: CourierPanelProps): JSX.Element {
  return (
    <View style={[styles.panel, { width: p.width, height: PANEL_H + p.bottom }]}>
      <Eta arrived={p.arrived} dip={p.dip} len={p.len} vanD={p.vanD} />
      <View style={styles.divider} />
      <View style={styles.row}>
        <View style={styles.avatar}>
          <Text style={styles.initials}>MR</Text>
        </View>
        <View style={styles.who}>
          <Text numberOfLines={1} style={styles.name}>
            Marco Ruiz
          </Text>
          <Text numberOfLines={1} style={styles.van}>
            Blue van · KX24 TRL
          </Text>
        </View>
        <View style={{ width: ACTION * 2 + ACTION_GAP, height: ACTION }}>
          <CourierActions
            dialog={p.dialog}
            onCall={p.onCall}
            onConfirmCall={p.onConfirmCall}
            onDismiss={p.onDismiss}
            onMessage={p.onMessage}
            onSend={p.onSend}
          />
        </View>
      </View>
    </View>
  );
});

const styles = StyleSheet.create({
  panel: {
    position: "absolute",
    bottom: 0,
    backgroundColor: "#FFFFFF",
    borderTopLeftRadius: 30,
    borderTopRightRadius: 30,
    paddingHorizontal: PANEL_PAD,
    shadowColor: "#0A1A40",
    shadowOpacity: 0.14,
    shadowRadius: 18,
    shadowOffset: { width: 0, height: -4 },
    elevation: 12,
  },
  copy: { marginTop: 18, height: 84 },
  label: { fontSize: 13, lineHeight: 18, fontWeight: "500", color: MUTE },
  title: { fontSize: 34, lineHeight: 42, fontWeight: "700", color: INK, padding: 0, margin: 0, fontVariant: ["tabular-nums"], letterSpacing: -0.4 },
  sub: { fontSize: 15, lineHeight: 20, color: "#3C4049", padding: 0, margin: 0, fontVariant: ["tabular-nums"] },
  subStatic: { fontSize: 15, lineHeight: 20, color: "#3C4049" },
  arrivedRow: { flexDirection: "row", alignItems: "center", gap: 8, height: 42 },
  tickFallback: { width: 30, height: 30, borderRadius: 15, backgroundColor: GREEN },
  divider: { marginTop: 14, height: StyleSheet.hairlineWidth, backgroundColor: "rgba(60,64,73,0.18)" },
  row: { marginTop: 15, height: 48, flexDirection: "row", alignItems: "center" },
  avatar: { width: 48, height: 48, borderRadius: 24, backgroundColor: "#E8EEFC", alignItems: "center", justifyContent: "center" },
  initials: { fontSize: 17, fontWeight: "700", color: BLUE },
  who: { flex: 1, marginLeft: 12, marginRight: 8 },
  name: { fontSize: 17, lineHeight: 22, fontWeight: "600", color: INK },
  van: { fontSize: 13, lineHeight: 18, color: MUTE },
});
