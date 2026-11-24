// The demo's screen: Almanac's settings, drawn for one theme. Everything here is plain views, so the
// louver has real content to snapshot; the segmented control and the switches are native on iOS.

import { memo } from 'react';
import { StyleSheet, Text, useWindowDimensions, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { RowToggle, ThemePicker } from './AlmanacControls';
import type { LouverScreenProps, LouverTheme } from './LouverThemeSwitch';

export type AlmanacPrefs = { weekNumbers: boolean; declined: boolean; travel: boolean };

const PALETTE = {
  light: {
    bg: '#F3F0E8',
    surface: '#FFFDF8',
    ink: '#1F1D19',
    mute: '#7F796D',
    line: 'rgba(40,36,28,0.11)',
    accent: '#C4513A',
    onAccent: '#FFF8F2',
    teal: '#2F7F78',
    shadow: '0 10px 24px rgba(66,54,36,0.10), 0 1px 3px rgba(66,54,36,0.12), inset 0 1px 0 rgba(255,255,255,0.9)',
  },
  dark: {
    bg: '#14151B',
    surface: '#202129',
    ink: '#ECE9E2',
    mute: '#918F99',
    line: 'rgba(236,233,226,0.10)',
    accent: '#EC7E63',
    onAccent: '#2A120C',
    teal: '#62BDB3',
    shadow: '0 10px 24px rgba(0,0,0,0.38), 0 1px 3px rgba(0,0,0,0.4), inset 0 1px 0 rgba(255,255,255,0.07)',
  },
} as const;

const WEEK = [
  ['M', 23],
  ['T', 24],
  ['W', 25],
  ['T', 26],
  ['F', 27],
  ['S', 28],
  ['S', 29],
] as const;
const TODAY = 24;
const AGENDA = [
  { title: 'Design review', time: '10:30', tone: 'accent' },
  { title: 'Lunch with Noor', time: '12:45', tone: 'teal' },
] as const;

export type AlmanacExtras = {
  prefs: AlmanacPrefs;
  setPref: (key: keyof AlmanacPrefs, value: boolean) => void;
};

function ValueRow({ label, value, c, last, height }: { label: string; value: string; c: (typeof PALETTE)[LouverTheme]; last?: boolean; height: number }) {
  return (
    <View style={[styles.row, { height }, !last && { borderBottomWidth: StyleSheet.hairlineWidth, borderBottomColor: c.line }]}>
      <Text style={[styles.rowLabel, { color: c.ink }]}>{label}</Text>
      <Text style={[styles.rowValue, { color: c.mute }]}>{value}</Text>
      <View style={[styles.chevron, { borderColor: c.mute }]} />
    </View>
  );
}

export const AlmanacSettings = memo(function AlmanacSettings({ theme, select, controlRef, resetKey, prefs, setPref }: LouverScreenProps & AlmanacExtras) {
  const insets = useSafeAreaInsets();
  const { height } = useWindowDimensions();
  const compact = height < 740;
  const c = PALETTE[theme];
  const rowH = compact ? 40 : 50;

  const toggleRow = (label: string, key: keyof AlmanacPrefs, last?: boolean) => (
    <View style={[styles.row, { height: rowH }, !last && { borderBottomWidth: StyleSheet.hairlineWidth, borderBottomColor: c.line }]}>
      <Text style={[styles.rowLabel, { color: c.ink }]}>{label}</Text>
      <RowToggle key={resetKey} theme={theme} label={label} value={prefs[key]} onChange={(v) => setPref(key, v)} tint={c.accent} />
    </View>
  );

  return (
    <View style={[styles.page, { backgroundColor: c.bg, paddingTop: insets.top + (compact ? 6 : 14), paddingBottom: Math.max(insets.bottom, 12) + 4 }]}>
      <View>
        <Text style={[styles.app, { color: c.mute }]}>Almanac</Text>
        <Text style={[styles.title, compact && styles.titleCompact, { color: c.ink }]}>Settings</Text>
      </View>

      <View style={[styles.card, styles.preview, compact && styles.previewCompact, { backgroundColor: c.surface, boxShadow: c.shadow }]}>
        <View style={styles.previewHead}>
          <Text style={[styles.month, { color: c.ink }]}>November</Text>
          <Text style={[styles.year, { color: c.mute }]}>Week 48</Text>
        </View>
        <View style={styles.week}>
          {WEEK.map(([letter, day], i) => {
            const on = day === TODAY;
            return (
              <View key={day} style={styles.day}>
                <Text style={[styles.dayLetter, { color: c.mute }]}>{letter}</Text>
                <View style={[styles.dayDisc, on && { backgroundColor: c.accent }]}>
                  <Text style={[styles.dayNumber, { color: on ? c.onAccent : i > 4 ? c.mute : c.ink }]}>{day}</Text>
                </View>
              </View>
            );
          })}
        </View>
        {AGENDA.slice(0, compact ? 1 : 2).map((e) => (
          <View key={e.title} style={styles.event}>
            <View style={[styles.eventBar, { backgroundColor: e.tone === 'accent' ? c.accent : c.teal }]} />
            <Text style={[styles.eventTitle, { color: c.ink }]}>{e.title}</Text>
            <Text style={[styles.eventTime, { color: c.mute }]}>{e.time}</Text>
          </View>
        ))}
      </View>

      <View>
        <Text style={[styles.section, { color: c.mute }]}>Appearance</Text>
        <View ref={controlRef} collapsable={false}>
          <ThemePicker key={resetKey} theme={theme} onSelect={select} tint={c.accent} />
        </View>
      </View>

      <View>
        <Text style={[styles.section, { color: c.mute }]}>Calendar</Text>
        <View style={[styles.card, { backgroundColor: c.surface, boxShadow: c.shadow }]}>
          <ValueRow label="Start week on" value="Monday" c={c} height={rowH} />
          <ValueRow label="Default length" value="45 min" c={c} height={rowH} />
          {toggleRow('Week numbers', 'weekNumbers')}
          {toggleRow('Show declined', 'declined', true)}
        </View>
      </View>

      <View>
        <Text style={[styles.section, { color: c.mute }]}>Alerts</Text>
        <View style={[styles.card, { backgroundColor: c.surface, boxShadow: c.shadow }]}>
          <ValueRow label="Morning brief" value="07:30" c={c} height={rowH} />
          {toggleRow('Travel time', 'travel', true)}
        </View>
      </View>
    </View>
  );
});

const styles = StyleSheet.create({
  page: { flex: 1, paddingHorizontal: 20, justifyContent: 'space-between' },
  app: { fontSize: 15, fontWeight: '600' },
  title: { fontSize: 34, lineHeight: 40, fontWeight: '700', letterSpacing: -0.6, marginTop: 2 },
  titleCompact: { fontSize: 28, lineHeight: 33 },
  card: { borderRadius: 18, borderCurve: 'continuous', paddingHorizontal: 16 },
  preview: { paddingVertical: 16 },
  previewCompact: { paddingVertical: 10 },
  previewHead: { flexDirection: 'row', alignItems: 'baseline', justifyContent: 'space-between' },
  month: { fontSize: 17, fontWeight: '700' },
  year: { fontSize: 13, fontWeight: '500', fontVariant: ['tabular-nums'] },
  week: { flexDirection: 'row', justifyContent: 'space-between', marginTop: 10, marginBottom: 8 },
  day: { alignItems: 'center', width: 36 },
  dayLetter: { fontSize: 11, fontWeight: '600', marginBottom: 4 },
  dayDisc: { width: 32, height: 32, borderRadius: 16, alignItems: 'center', justifyContent: 'center' },
  dayNumber: { fontSize: 15, fontWeight: '600', fontVariant: ['tabular-nums'] },
  event: { flexDirection: 'row', alignItems: 'center', height: 26 },
  eventBar: { width: 3, height: 16, borderRadius: 1.5, marginRight: 10 },
  eventTitle: { flex: 1, fontSize: 14, fontWeight: '500' },
  eventTime: { fontSize: 13, fontWeight: '500', fontVariant: ['tabular-nums'] },
  section: { fontSize: 13, fontWeight: '600', marginBottom: 6, marginLeft: 4 },
  row: { flexDirection: 'row', alignItems: 'center' },
  rowLabel: { flex: 1, fontSize: 16, fontWeight: '400' },
  rowValue: { fontSize: 16, fontWeight: '400', fontVariant: ['tabular-nums'], marginRight: 8 },
  chevron: { width: 7, height: 7, borderTopWidth: 1.6, borderRightWidth: 1.6, transform: [{ rotate: '45deg' }], marginRight: 2 },
});
