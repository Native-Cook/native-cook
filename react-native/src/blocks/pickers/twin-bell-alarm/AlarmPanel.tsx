import type { JSX } from "react";
import { useCallback, useRef, useState } from "react";
import { StyleSheet, Text, View } from "react-native";
import Animated, { useAnimatedReaction, useAnimatedStyle } from "react-native-reanimated";
import type { DerivedValue, SharedValue } from "react-native-reanimated";
import { scheduleOnRN } from "react-native-worklets";

import { AlarmSwitch, AlarmWheel } from "./AlarmControls";
import { toggleGhost, wheelGhost } from "./alarmMotion";
import { CARD, HAIR, INK, MUTE, NOW_MIN, PANEL } from "./alarmScene";

export const GUTTER = 20;
export const PANEL_R = 28;
/** The sheet's rows, and its height above the bottom safe area. */
const TOP = 20;
const READ_H = 46;
const SUB_H = 20;
const WHEEL_GAP = 6;
const WHEEL_H = 148;
const CARD_GAP = 8;
const CARD_H = 56;
const CARD_PAD = 16;
const BOTTOM = 14;
export const PANEL_H = TOP + READ_H + SUB_H + WHEEL_GAP + WHEEL_H + CARD_GAP + CARD_H + BOTTOM;
const WHEEL_TOP = TOP + READ_H + SUB_H + WHEEL_GAP;
const CARD_TOP = WHEEL_TOP + WHEEL_H + CARD_GAP;
/** The film's finger: how far it drags on the wheel (pt), where the minutes column and the switch are. */
const DRAG_PT = 64;
const MINUTES_DX = 8;
const SWITCH_DX = 26;
const DOT = 44;
/** The wheel may report a value the film just gave it: such a report within this long (ms) is not a finger. */
const ECHO_MS = 900;

function clockText(minutes: number): string {
  const h = Math.floor(minutes / 60) % 24;
  const m = minutes % 60;
  return `${((h + 11) % 12) + 1}:${String(m).padStart(2, "0")}`;
}

function ringsIn(minutes: number): string {
  const d = (minutes - NOW_MIN + 1440) % 1440;
  return `Rings in ${Math.floor(d / 60)} h ${d % 60} min`;
}

export type AlarmPanelProps = {
  width: number;
  bottom: number;
  /** The film's time and whether the film is playing (its finger is only drawn then). */
  t: SharedValue<number>;
  filming: DerivedValue<number>;
  /** What the controls show: the alarm's time (minutes of the day) and whether it is on (0 / 1). The film and the
   * return after a touch write them; this sheet mirrors them into the native controls. */
  wheel: SharedValue<number>;
  armed: SharedValue<number>;
  initialMinutes: number;
  initialOn: boolean;
  /** A finger changed the time / flipped the switch. */
  onMinutes: (minutes: number) => void;
  onToggle: (on: boolean) => void;
};

/**
 * The alarm's sheet: its time, how long until it rings, the native wheel and the native switch. Its React state
 * lives here, not in the screen: the film's wheel steps never re-render the 3D stage.
 */
export function AlarmPanel({ width, bottom, t, filming, wheel, armed, initialMinutes, initialOn, onMinutes, onToggle }: AlarmPanelProps): JSX.Element {
  const [minutes, setMinutes] = useState(initialMinutes);
  const [on, setOn] = useState(initialOn);
  const given = useRef<{ v: number; at: number }[]>([]);
  const shownOn = useRef(initialOn);

  const giveMinutes = useCallback((v: number) => {
    given.current.push({ v, at: Date.now() });
    if (given.current.length > 16) given.current.shift();
    setMinutes(v);
  }, []);
  const giveOn = useCallback((v: boolean) => {
    shownOn.current = v;
    setOn(v);
  }, []);
  useAnimatedReaction(
    () => wheel.get(),
    (v, prev) => {
      if (prev != null && v !== prev) scheduleOnRN(giveMinutes, v);
    },
  );
  useAnimatedReaction(
    () => armed.get() >= 0.5,
    (v, prev) => {
      if (prev != null && v !== prev) scheduleOnRN(giveOn, v);
    },
  );

  const changeMinutes = useCallback(
    (v: number) => {
      const now = Date.now();
      // the wheel reporting what the film (or the return after a touch) just set is not a finger
      if (given.current.some((g) => g.v === v && now - g.at < ECHO_MS)) return;
      setMinutes(v);
      onMinutes(v);
    },
    [onMinutes],
  );
  const changeOn = useCallback(
    (v: boolean) => {
      if (v === shownOn.current) return;
      shownOn.current = v;
      setOn(v);
      onToggle(v);
    },
    [onToggle],
  );

  const inner = Math.min(width, 560) - GUTTER * 2;
  const wheelDot = useAnimatedStyle(() => {
    const g = [0, 0, 0];
    wheelGhost(t.get(), g);
    return {
      opacity: g[0] * 0.9 * filming.get(),
      transform: [{ translateX: inner / 2 + MINUTES_DX - DOT / 2 }, { translateY: WHEEL_TOP + WHEEL_H / 2 + g[2] * DRAG_PT - DOT / 2 }, { scale: 1 - 0.16 * g[1] }],
    };
  });
  const switchDot = useAnimatedStyle(() => {
    const g = [0, 0];
    toggleGhost(t.get(), g);
    return {
      opacity: g[0] * 0.9 * filming.get(),
      transform: [{ translateX: inner - CARD_PAD - SWITCH_DX - DOT / 2 }, { translateY: CARD_TOP + CARD_H / 2 - DOT / 2 }, { scale: 1 - 0.16 * g[1] }],
    };
  });

  return (
    <View style={[styles.panel, { height: PANEL_H + bottom, paddingBottom: bottom }]}>
      <View pointerEvents="none" style={styles.edge} />
      <View style={[styles.column, { width: inner }]}>
        <View style={styles.read}>
          <Text style={styles.time}>{clockText(minutes)}</Text>
          <Text style={styles.half}>{minutes % 1440 < 720 ? "AM" : "PM"}</Text>
        </View>
        <Text style={[styles.sub, on && styles.subOn]}>{on ? ringsIn(minutes) : "Alarm is off"}</Text>
        <View style={styles.wheel}>
          <AlarmWheel height={WHEEL_H} minutes={minutes} onMinutes={changeMinutes} width={inner} />
        </View>
        <View style={styles.card}>
          <AlarmSwitch on={on} onToggle={changeOn} width={inner - CARD_PAD * 2} />
        </View>
        <Animated.View pointerEvents="none" style={[styles.dot, wheelDot]} />
        <Animated.View pointerEvents="none" style={[styles.dot, switchDot]} />
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
    shadowColor: "#1B2238",
    shadowOpacity: 0.2,
    shadowRadius: 24,
    shadowOffset: { width: 0, height: -8 },
    elevation: 12,
  },
  edge: { position: "absolute", left: 0, right: 0, top: 0, height: PANEL_R, borderTopLeftRadius: PANEL_R, borderTopRightRadius: PANEL_R, borderTopWidth: StyleSheet.hairlineWidth, borderColor: "rgba(255,255,255,0.95)" },
  column: { paddingTop: TOP },
  read: { height: READ_H, flexDirection: "row", alignItems: "baseline" },
  time: { fontSize: 42, lineHeight: READ_H, fontWeight: "700", letterSpacing: -0.8, color: INK, fontVariant: ["tabular-nums"] },
  half: { marginLeft: 6, fontSize: 20, lineHeight: 26, fontWeight: "600", color: MUTE },
  sub: { height: SUB_H, fontSize: 15, lineHeight: SUB_H, color: MUTE, fontVariant: ["tabular-nums"] },
  subOn: { color: "#9A6A05" },
  wheel: { marginTop: WHEEL_GAP, height: WHEEL_H, overflow: "hidden" },
  card: {
    marginTop: CARD_GAP,
    height: CARD_H,
    paddingHorizontal: CARD_PAD,
    justifyContent: "center",
    borderRadius: 18,
    backgroundColor: CARD,
    borderWidth: StyleSheet.hairlineWidth,
    borderColor: HAIR,
    // the card's tight contact shadow
    shadowColor: "#1B2238",
    shadowOpacity: 0.07,
    shadowRadius: 10,
    shadowOffset: { width: 0, height: 3 },
    elevation: 2,
  },
  dot: {
    position: "absolute",
    left: 0,
    top: 0,
    width: DOT,
    height: DOT,
    borderRadius: DOT / 2,
    backgroundColor: "rgba(40,44,60,0.16)",
    borderWidth: 1.5,
    borderColor: "rgba(40,44,60,0.3)",
  },
});
