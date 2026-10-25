import { StyleSheet, Text, View } from 'react-native';
import Animated, { type SharedValue, useAnimatedStyle } from 'react-native-reanimated';

import { type Bread, column } from './breadMotion';

/**
 * The screen's moving labels, outside the canvas: the timer's odometer wheels, the rolling status
 * line, the Proof · Shape · Bake control and the progress bar. All read the loop's pose on the UI
 * thread; none of them re-renders while it plays.
 */

const DIGIT_H = 62;
const STATUS = ['Proofing at 26 °C', 'Shaping the boule', 'Baking at 240 °C', 'Ready to cool', 'Proofing at 26 °C'];
const STAGES = ['Proof', 'Shape', 'Bake'];

export const INK = '#3A2A1C';
export const MUTE = 'rgba(58,42,28,0.58)';
export const ACCENT = '#B4642A';

/** One odometer wheel of the timer. */
function Wheel({ pose, prev, unit, base }: { pose: SharedValue<Bread>; prev: SharedValue<number>; unit: number; base: number }) {
  const style = useAnimatedStyle(() => {
    const pos = column(pose.get().timer, unit, base);
    // A wheel spinning fast dims a little, like a blur.
    const before = column(prev.get(), unit, base);
    let speed = Math.abs(pos - before);
    if (speed > base / 2) speed = base - speed;
    const blur = Math.min(1, Math.max(0, (speed - 0.12) / 0.5));
    return { transform: [{ translateY: -pos * DIGIT_H }], opacity: 1 - 0.55 * blur };
  });
  return (
    <View style={styles.wheel}>
      <Animated.View style={style}>
        {Array.from({ length: base + 1 }, (_, d) => (
          <Text key={d} style={styles.digit}>
            {d % base}
          </Text>
        ))}
      </Animated.View>
    </View>
  );
}

/** A status line that rolls: the old one leaves upward and fades, the next comes in from below. */
export function Status({ pose }: { pose: SharedValue<Bread> }) {
  return (
    <View style={styles.statusBox}>
      {STATUS.map((label, i) => (
        <StatusLine key={i} pose={pose} i={i} label={label} />
      ))}
    </View>
  );
}

function StatusLine({ pose, i, label }: { pose: SharedValue<Bread>; i: number; label: string }) {
  const style = useAnimatedStyle(() => {
    const d = i - pose.get().status;
    return { opacity: Math.max(0, 1 - Math.abs(d) * 1.6), transform: [{ translateY: d * 20 }] };
  });
  return <Animated.Text style={[styles.status, style]}>{label}</Animated.Text>;
}

function StageLabel({ pose, i, label }: { pose: SharedValue<Bread>; i: number; label: string }) {
  const style = useAnimatedStyle(() => {
    const s = pose.get().stage;
    const at = s <= 2 ? s : 2 * (3 - s);
    return { opacity: Math.max(0, 1 - Math.abs(at - i)) };
  });
  return (
    <View style={styles.segment}>
      <Text style={styles.segmentText}>{label}</Text>
      <Animated.Text style={[styles.segmentText, styles.segmentOn, style]}>{label}</Animated.Text>
    </View>
  );
}

/** The timer, H:MM:SS, as five wheels. */
export function Timer({ pose, prev }: { pose: SharedValue<Bread>; prev: SharedValue<number> }) {
  return (
    <View style={styles.timer}>
      <Wheel pose={pose} prev={prev} unit={3600} base={10} />
      <Text style={styles.colon}>:</Text>
      <Wheel pose={pose} prev={prev} unit={600} base={6} />
      <Wheel pose={pose} prev={prev} unit={60} base={10} />
      <Text style={styles.colon}>:</Text>
      <Wheel pose={pose} prev={prev} unit={10} base={6} />
      <Wheel pose={pose} prev={prev} unit={1} base={10} />
    </View>
  );
}

/** Proof · Shape · Bake, with a thumb that slides with the stage (and back to Proof on reset). */
export function Stages({ pose, width }: { pose: SharedValue<Bread>; width: number }) {
  const indicator = useAnimatedStyle(() => {
    const s = pose.get().stage;
    const at = s <= 2 ? s : 2 * (3 - s);
    return { transform: [{ translateX: at * ((width - 8) / 3) }] };
  });
  return (
    <View style={styles.segmented}>
      <Animated.View style={[styles.indicator, { width: (width - 8) / 3 }, indicator]} />
      {STAGES.map((s, i) => (
        <StageLabel key={s} pose={pose} i={i} label={s} />
      ))}
    </View>
  );
}

export function Progress({ pose, width }: { pose: SharedValue<Bread>; width: number }) {
  const fill = useAnimatedStyle(() => ({ transform: [{ translateX: -(1 - pose.get().progress) * width }] }));
  return (
    <View style={styles.track}>
      <Animated.View style={[styles.fill, fill]} />
    </View>
  );
}

const styles = StyleSheet.create({
  timer: { flexDirection: 'row', alignItems: 'center', height: DIGIT_H },
  wheel: { width: 34, height: DIGIT_H, overflow: 'hidden' },
  digit: { height: DIGIT_H, lineHeight: DIGIT_H, fontSize: 54, fontWeight: '700', color: INK, textAlign: 'center', fontVariant: ['tabular-nums'], letterSpacing: -1 },
  colon: { fontSize: 46, fontWeight: '600', color: 'rgba(58,42,28,0.45)', marginHorizontal: 1, marginTop: -6 },
  statusBox: { height: 22, overflow: 'hidden' },
  status: { position: 'absolute', left: 0, right: 0, height: 22, lineHeight: 22, color: ACCENT, fontSize: 15, fontWeight: '700' },
  segmented: { height: 44, borderRadius: 22, padding: 4, flexDirection: 'row', backgroundColor: 'rgba(255,250,240,0.55)', boxShadow: 'inset 0 1px 2px rgba(90,60,30,0.18)' },
  indicator: { position: 'absolute', left: 4, top: 4, bottom: 4, borderRadius: 18, backgroundColor: INK, boxShadow: '0 1px 2px rgba(40,20,5,0.35), 0 6px 14px rgba(40,20,5,0.18)' },
  segment: { flex: 1, alignItems: 'center', justifyContent: 'center' },
  segmentText: { color: MUTE, fontSize: 15, fontWeight: '700' },
  segmentOn: { position: 'absolute', color: '#FBF3E6' },
  track: { height: 6, borderRadius: 3, overflow: 'hidden', backgroundColor: 'rgba(90,60,30,0.14)' },
  fill: { position: 'absolute', left: 0, top: 0, bottom: 0, width: '100%', borderRadius: 3, backgroundColor: ACCENT },
});
