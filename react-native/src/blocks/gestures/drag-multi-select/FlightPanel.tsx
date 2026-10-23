/**
 * The bottom of the screen: two figures (what is planned against the flight) and the one action. While downloading,
 * the button fills the way the rows do. Figures that change roll into place; two values are never on screen together.
 */
import { useState } from 'react';
import { Pressable, StyleSheet, Text, View, type StyleProp, type ViewStyle } from 'react-native';
import Animated, {
  Easing, ReduceMotion, cubicBezier, useAnimatedStyle, withTiming, type EntryAnimationsValues, type SharedValue,
} from 'react-native-reanimated';

import { FLIGHT, MINUTES, formatSize, spokenMinutes } from './episodes';
import { GLYPH } from './glyph-paths';
import { Stroke } from './Glyphs';
import { PHASE, type Model } from './model';
import { EASE_OUT, PRESS, TIMING } from './motion';
import { COLOR, LAYOUT, TYPE, type Layout } from './theme';
import type { Ui } from './use-range-select';

const easeOut = Easing.bezier(EASE_OUT[0], EASE_OUT[1], EASE_OUT[2], EASE_OUT[3]);
const CSS_EASE_OUT = cubicBezier(EASE_OUT[0], EASE_OUT[1], EASE_OUT[2], EASE_OUT[3]);
const NEVER = ReduceMotion.Never;

/** "3h 45m", "52m", "5h". */
export function shortMinutes(min: number): string {
  if (min < 60) return `${min}m`;
  const h = Math.floor(min / 60);
  const m = min % 60;
  return m === 0 ? `${h}h` : `${h}h ${m}m`;
}

function rollIn(v: EntryAnimationsValues) {
  'worklet';
  const cfg = { duration: TIMING.swap, easing: easeOut, reduceMotion: NEVER };
  return {
    initialValues: { opacity: 0, transform: [{ translateY: 6 }], originX: v.targetOriginX, originY: v.targetOriginY },
    animations: { opacity: withTiming(1, cfg), transform: [{ translateY: withTiming(0, cfg) }] },
  };
}
function fadeIn(v: EntryAnimationsValues) {
  'worklet';
  return {
    initialValues: { opacity: 0, originX: v.targetOriginX, originY: v.targetOriginY },
    animations: { opacity: withTiming(1, { duration: TIMING.reduced, easing: easeOut, reduceMotion: NEVER }) },
  };
}

function Swap({ id, reduced, style, children }: { id: string; reduced: boolean; style?: StyleProp<ViewStyle>; children: React.ReactNode }) {
  return (
    <Animated.View key={id} entering={reduced ? fadeIn : rollIn} style={[styles.swap, style]}>
      {children}
    </Animated.View>
  );
}

function Figure({ value, label, reduced, tint }: { value: string; label: string; reduced: boolean; tint?: string }) {
  return (
    <View style={styles.cell}>
      <View style={styles.figureBox}>
        <Swap id={value} reduced={reduced}>
          <Text style={[styles.figure, tint ? { color: tint } : null]} numberOfLines={1}>{value}</Text>
        </Swap>
      </View>
      <View style={styles.labelBox}>
        <Swap id={label} reduced={reduced}>
          <Text style={styles.label} numberOfLines={1}>{label}</Text>
        </Swap>
      </View>
    </View>
  );
}

type Props = { lay: Layout; ui: Ui; model: SharedValue<Model>; reduced: boolean; onDownload: () => void };

export function FlightPanel({ lay, ui, model, reduced, onDownload }: Props) {
  const [pressed, setPressed] = useState(false);
  const count = ui.rows.length;
  const offline = new Set([...ui.rows, ...ui.done]);
  let minutes = 0;
  for (const r of offline) minutes += MINUTES[r];
  const ready = ui.phase === PHASE.ready;
  const selecting = ui.phase === PHASE.selecting;
  const busy = ui.phase === PHASE.downloading;
  const finished = ui.phase === PHASE.done || ready;
  const enabled = selecting && count > 0;
  const gap = FLIGHT.minutes - minutes;

  const left = { value: shortMinutes(minutes), label: finished ? 'Offline' : 'Planned' };
  const right = finished
    ? { value: `${ui.done.length}`, label: 'Episodes', tint: undefined }
    : ui.phase === PHASE.idle || count === 0
      ? { value: shortMinutes(FLIGHT.minutes), label: 'Flight time', tint: undefined }
      : gap > 0
        ? { value: shortMinutes(gap), label: 'Still to fill', tint: undefined }
        : { value: `+${shortMinutes(-gap)}`, label: 'Spare on landing', tint: COLOR.green };
  const label = finished
    ? 'Ready for takeoff'
    : busy
      ? `Downloading ${count} episode${count === 1 ? '' : 's'}`
      : enabled
        ? `Download ${count} · ${formatSize(minutes)}`
        : 'Hold an episode, then drag';

  const fill = useAnimatedStyle(() => {
    const m = model.get();
    let s = 0;
    for (let k = 0; k < m.dlRows.length; k++) s += m.prog[m.dlRows[k]];
    const p = m.phase === PHASE.downloading ? s / Math.max(1, m.dlRows.length) : m.phase === PHASE.idle || m.phase === PHASE.selecting ? 0 : 1;
    return { transform: [{ translateX: -(1 - p) * lay.panelWidth }] };
  });

  return (
    <View style={[styles.panel, { left: LAYOUT.panelSide, width: lay.panelWidth, bottom: lay.panelBottom }]}>
      <View style={styles.cells} accessible accessibilityLabel={`${spokenMinutes(minutes)} planned of a ${spokenMinutes(FLIGHT.minutes)} flight`} accessibilityLiveRegion="polite">
        <Figure value={left.value} label={left.label} reduced={reduced} />
        <View style={styles.divider} />
        <Figure value={right.value} label={right.label} reduced={reduced} tint={right.tint} />
      </View>
      <Pressable
        onPress={onDownload}
        onPressIn={() => setPressed(true)}
        onPressOut={() => setPressed(false)}
        pressRetentionOffset={16}
        disabled={!enabled}
        accessibilityRole="button"
        accessibilityLabel={label}
        accessibilityState={{ disabled: !enabled }}
      >
        <Animated.View
          style={[
            styles.button,
            enabled ? styles.buttonOn : busy ? styles.buttonBusy : finished ? styles.buttonDone : styles.buttonOff,
            {
              transform: [{ scale: pressed && enabled && !reduced ? PRESS.scale : 1 }],
              opacity: pressed && enabled && reduced ? PRESS.dim : 1,
              transitionProperty: ['transform', 'opacity', 'backgroundColor'],
              transitionDuration: [PRESS.ms, PRESS.ms, TIMING.state],
              transitionTimingFunction: CSS_EASE_OUT,
            },
          ]}
        >
          {busy && <Animated.View style={[StyleSheet.absoluteFill, styles.buttonFill, fill]} />}
          <Swap id={label} reduced={reduced} style={styles.center}>
            <View style={styles.row}>
              {finished && <Stroke d={GLYPH.check} size={18} color={COLOR.green} weight={2.4} />}
              <Text
                style={[styles.buttonText, enabled ? styles.inkOn : busy ? styles.inkBusy : finished ? styles.inkDone : styles.inkOff]}
                numberOfLines={1}
              >
                {label}
              </Text>
            </View>
          </Swap>
        </Animated.View>
      </Pressable>
    </View>
  );
}

const styles = StyleSheet.create({
  panel: { position: 'absolute', height: LAYOUT.panelHeight },
  cells: {
    height: LAYOUT.cells, flexDirection: 'row', borderRadius: 18, borderCurve: 'continuous', backgroundColor: COLOR.card,
    borderWidth: StyleSheet.hairlineWidth, borderColor: COLOR.line, marginBottom: LAYOUT.cellsGap,
  },
  cell: { flex: 1, paddingHorizontal: 16, paddingTop: 12 },
  divider: { width: StyleSheet.hairlineWidth, backgroundColor: COLOR.line },
  // 12 · figure 30 · 2 · label 16 · 12 = 72.
  figureBox: { height: 30 },
  labelBox: { height: 16, marginTop: 2 },
  swap: { position: 'absolute', left: 0, top: 0, bottom: 0, right: 0, justifyContent: 'center' },
  center: { alignItems: 'center' },
  figure: { ...TYPE.figure, color: COLOR.ink },
  label: { ...TYPE.figureLabel, color: COLOR.mute },
  button: { height: LAYOUT.button, borderRadius: LAYOUT.buttonRadius, overflow: 'hidden', borderCurve: 'continuous' },
  buttonOn: { backgroundColor: COLOR.violet },
  buttonOff: { backgroundColor: COLOR.buttonOff },
  buttonBusy: { backgroundColor: COLOR.violetTrack },
  buttonDone: { backgroundColor: COLOR.buttonOff },
  buttonFill: { backgroundColor: COLOR.violet },
  row: { flexDirection: 'row', alignItems: 'center', gap: 6 },
  buttonText: { ...TYPE.button },
  inkOn: { color: COLOR.violetInk },
  inkBusy: { color: COLOR.ink },
  inkDone: { color: COLOR.ink },
  inkOff: { color: COLOR.mute },
});
