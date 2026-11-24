import type { JSX } from "react";
import { useEffect, useRef } from "react";
import { ScrollView, StyleSheet, Switch, Text, View } from "react-native";
import type { NativeScrollEvent, NativeSyntheticEvent } from "react-native";

import type { AlarmSwitchProps, AlarmWheelProps } from "./AlarmControls.types";
import { INK, MUTE, WHEEL_STEP, YELLOW } from "./alarmScene";

const ROW = 34;
const HOURS = Array.from({ length: 24 }, (_, i) => i);
const MINUTES = Array.from({ length: 60 / WHEEL_STEP }, (_, i) => i * WHEEL_STEP);

type ColumnProps = {
  values: number[];
  value: number;
  onValue: (v: number) => void;
  height: number;
};

/** One snapping column. */
function Column({ values, value, onValue, height }: ColumnProps): JSX.Element {
  const ref = useRef<ScrollView>(null);
  const index = Math.max(0, values.indexOf(value));
  useEffect(() => {
    ref.current?.scrollTo({ y: index * ROW, animated: true });
  }, [index]);
  const settle = (e: NativeSyntheticEvent<NativeScrollEvent>) => {
    const i = Math.min(values.length - 1, Math.max(0, Math.round(e.nativeEvent.contentOffset.y / ROW)));
    if (values[i] !== value) onValue(values[i]);
  };
  return (
    <ScrollView
      contentContainerStyle={{ paddingVertical: (height - ROW) / 2 }}
      decelerationRate="fast"
      onMomentumScrollEnd={settle}
      ref={ref}
      showsVerticalScrollIndicator={false}
      snapToInterval={ROW}
      style={[styles.column, { height }]}
    >
      {values.map((v) => (
        <View key={v} style={styles.cell}>
          <Text style={[styles.digits, v !== value && styles.dim]}>{String(v).padStart(2, "0")}</Text>
        </View>
      ))}
    </ScrollView>
  );
}

/** The time wheel where SwiftUI is not available (Android): two snapping columns, 24-hour. iOS: AlarmControls.ios.tsx. */
export function AlarmWheel({ minutes, onMinutes, width, height }: AlarmWheelProps): JSX.Element {
  const h = Math.floor(minutes / 60);
  const m = Math.round((minutes % 60) / WHEEL_STEP) * WHEEL_STEP;
  return (
    <View accessibilityLabel="Alarm time" style={[styles.wheel, { width, height }]}>
      <View pointerEvents="none" style={[styles.band, { top: (height - ROW) / 2 }]} />
      <Column height={height} onValue={(v) => onMinutes(v * 60 + (m % 60))} value={h} values={HOURS} />
      <Text style={styles.colon}>:</Text>
      <Column height={height} onValue={(v) => onMinutes(h * 60 + v)} value={m % 60} values={MINUTES} />
    </View>
  );
}

/** The alarm's switch (Android). */
export function AlarmSwitch({ on, onToggle, width }: AlarmSwitchProps): JSX.Element {
  return (
    <View style={[styles.switchRow, { width }]}>
      <Text style={styles.label}>Alarm</Text>
      <Switch accessibilityLabel="Alarm" onValueChange={onToggle} trackColor={{ true: YELLOW, false: "rgba(120,122,134,0.3)" }} value={on} />
    </View>
  );
}

const styles = StyleSheet.create({
  wheel: { flexDirection: "row", alignItems: "center", justifyContent: "center" },
  band: { position: "absolute", left: 0, right: 0, height: ROW, borderRadius: 10, backgroundColor: "rgba(118,122,134,0.12)" },
  column: { width: 72, flexGrow: 0 },
  cell: { height: ROW, alignItems: "center", justifyContent: "center" },
  digits: { fontSize: 23, lineHeight: 28, color: INK, fontVariant: ["tabular-nums"] },
  dim: { color: MUTE, opacity: 0.6 },
  colon: { fontSize: 23, lineHeight: 28, color: INK, marginHorizontal: 4 },
  switchRow: { height: 32, flexDirection: "row", alignItems: "center", justifyContent: "space-between" },
  label: { fontSize: 17, lineHeight: 22, color: INK },
});
