import { useEffect, useState } from 'react';
import { type LayoutChangeEvent, StyleSheet, Text, type TextStyle, View } from 'react-native';
import Animated, {
  Easing,
  type SharedValue,
  useAnimatedReaction,
  useAnimatedStyle,
  useSharedValue,
  withSpring,
  withTiming,
} from 'react-native-reanimated';
import { scheduleOnRN } from 'react-native-worklets';

/*
 Rolling labels for the readouts. Numbers roll digit by digit like an odometer and always come to
 rest on whole digits (the shown value springs to round(value)); phase labels roll inside a clipped
 box (the old one leaves upwards, the new one comes from below) and never cross-fade in place.
 Numbers read shared values only on the UI thread; a label re-renders once per change of phase.
 */

const DIGITS = ['0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '0'];
const ROLL_EASE = Easing.bezier(0.65, 0, 0.35, 1);

function columnPos(shown: number, k: number) {
  'worklet';
  const p = Math.pow(10, k);
  if (k === 0) return ((shown % 10) + 10) % 10;
  const baseV = Math.floor(shown / p);
  const carry = Math.min(1, Math.max(0, shown - baseV * p - (p - 1)));
  return (baseV % 10) + carry;
}

function columnVisible(shown: number, k: number, pad: boolean) {
  'worklet';
  if (k === 0 || pad) return 1;
  return Math.min(1, Math.max(0, shown - (Math.pow(10, k) - 1)));
}

function DigitColumn({ shown, k, line, style, pad }: { shown: SharedValue<number>; k: number; line: number; style: TextStyle; pad: boolean }) {
  const a = useAnimatedStyle(() => ({
    opacity: columnVisible(shown.get(), k, pad),
    transform: [{ translateY: -columnPos(shown.get(), k) * line }],
  }));
  return (
    <View style={{ height: line, overflow: 'hidden' }}>
      <Animated.View style={a}>
        {DIGITS.map((d, i) => (
          <Text key={i} style={[style, { height: line, lineHeight: line }]}>
            {d}
          </Text>
        ))}
      </Animated.View>
    </View>
  );
}

type NumberProps = {
  value: SharedValue<number>;
  /** Number of digit columns (leading zeros are hidden and the row closes up). */
  digits: number;
  line: number;
  style: TextStyle;
  prefix?: string;
  suffix?: string;
  suffixStyle?: TextStyle;
  /** 'center' keeps the visible digits centred where the row is centred (half the shift). */
  align?: 'left' | 'center';
  /** Always show every column (leading zeros too), e.g. hours as 09. */
  pad?: boolean;
};

export function RollNumber({ value, digits, line, style, prefix, suffix, suffixStyle, align = 'left', pad = false }: NumberProps) {
  const shown = useSharedValue(0);
  const [colW, setColW] = useState(0);
  useAnimatedReaction(
    () => Math.round(value.get()),
    (r, prev) => {
      if (prev === null) shown.set(r);
      else if (r !== prev) shown.set(withSpring(r, { duration: 420, dampingRatio: 0.86 }));
    },
  );
  const row = useAnimatedStyle(() => {
    let hidden = 0;
    for (let k = 1; k < digits; k += 1) hidden += 1 - columnVisible(shown.get(), k, pad);
    return { transform: [{ translateX: -hidden * colW * (align === 'center' ? 0.5 : 1) }] };
  });
  const onLayout = (e: LayoutChangeEvent) => {
    const w = e.nativeEvent.layout.width;
    if (Math.abs(w - colW) > 0.5) setColW(w);
  };
  const cols = [];
  for (let k = digits - 1; k >= 0; k -= 1) {
    cols.push(
      <View key={k} onLayout={k === 0 ? onLayout : undefined}>
        <DigitColumn shown={shown} k={k} line={line} style={style} pad={pad} />
      </View>,
    );
  }
  return (
    <Animated.View style={[styles.row, row]}>
      {prefix ? <Text style={[style, suffixStyle, { lineHeight: line }]}>{prefix}</Text> : null}
      {cols}
      {suffix ? <Text style={[style, suffixStyle, { lineHeight: line }]}>{suffix}</Text> : null}
    </Animated.View>
  );
}

function LabelSlot({ slot, front, k, line, style, text }: { slot: number; front: SharedValue<number>; k: SharedValue<number>; line: number; style: TextStyle; text: string }) {
  const a = useAnimatedStyle(() => {
    const entering = front.get() === slot;
    const p = k.get();
    return entering
      ? { opacity: p, transform: [{ translateY: (1 - p) * line }] }
      : { opacity: 1 - p, transform: [{ translateY: -p * line }] };
  });
  return (
    <Animated.Text numberOfLines={1} style={[style, styles.slot, { height: line, lineHeight: line }, a]}>
      {text}
    </Animated.Text>
  );
}

type Slots = { a: number; b: number; front: number; seq: number };

/**
 A label that rolls to labels[index] whenever the index changes (a few times a loop, never per
 frame). Two slots take turns: the new text is written into the hidden slot first, then the roll
 starts, so the incoming text never flashes before it moves.
 */
export function RollLabel({ labels, index, line, style }: { labels: string[]; index: SharedValue<number>; line: number; style: TextStyle }) {
  const [slots, setSlots] = useState<Slots>({ a: 0, b: 0, front: 0, seq: 0 });
  const front = useSharedValue(0);
  const k = useSharedValue(1);
  const show = (i: number) => {
    setSlots((s) => (s.front === 0 ? { ...s, b: i, front: 1, seq: s.seq + 1 } : { ...s, a: i, front: 0, seq: s.seq + 1 }));
  };
  useAnimatedReaction(
    () => index.get(),
    (i, prev) => {
      if ((prev === null && i !== 0) || (prev !== null && i !== prev)) scheduleOnRN(show, i);
    },
  );
  useEffect(() => {
    if (slots.seq === 0) return;
    front.set(slots.front);
    k.set(0);
    k.set(withTiming(1, { duration: 380, easing: ROLL_EASE }));
  }, [slots, front, k]);
  const longest = labels.reduce((m, t) => (t.length > m.length ? t : m), '');
  return (
    <View style={{ height: line, overflow: 'hidden' }}>
      <Text style={[style, { lineHeight: line, opacity: 0 }]} numberOfLines={1}>
        {longest}
      </Text>
      <LabelSlot slot={0} front={front} k={k} line={line} style={style} text={labels[slots.a] ?? ''} />
      <LabelSlot slot={1} front={front} k={k} line={line} style={style} text={labels[slots.b] ?? ''} />
    </View>
  );
}

const styles = StyleSheet.create({
  row: { flexDirection: 'row', alignItems: 'flex-start' },
  slot: { position: 'absolute', left: 0, right: 0, top: 0 },
});
