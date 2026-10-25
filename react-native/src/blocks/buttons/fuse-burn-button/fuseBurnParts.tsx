import { StyleSheet } from 'react-native';
import Svg, { Path as SvgPath } from 'react-native-svg';

export const CREAM = '#ffe3b8';
const INK = '#eef0fa';
const MUTE = 'rgba(214,220,245,0.62)';
/** The count's line height (it rolls by one line). */
export const COUNT_H = 22;
/** Friends going, shown as initials. */
export const AVATARS = [
  { initials: 'JR', color: '#c46b4a' },
  { initials: 'AM', color: '#5f7fbf' },
  { initials: 'SK', color: '#8c6bb8' },
];

export function Flame() {
  return (
    <Svg width={16} height={18} viewBox="0 0 16 18">
      <SvgPath
        d="M8 1.2c.6 2.4 3.9 4.3 3.9 8.4A3.9 3.9 0 0 1 8 13.5a3.9 3.9 0 0 1-3.9-3.9c0-1.7.8-2.9 1.7-3.8.2 1.1.8 1.9 1.6 2.2C7 6 6.9 3.4 8 1.2z"
        fill={CREAM}
      />
      <SvgPath d="M3 16.2h10" stroke={CREAM} strokeWidth={1.6} strokeLinecap="round" opacity={0.6} />
    </Svg>
  );
}

export function Check() {
  return (
    <Svg width={18} height={18} viewBox="0 0 18 18">
      <SvgPath d="M3.5 9.5l3.6 3.6 7.4-8" stroke="#3a1606" strokeWidth={2.4} strokeLinecap="round" strokeLinejoin="round" fill="none" />
    </Svg>
  );
}

export const styles = StyleSheet.create({
  root: { flex: 1, overflow: 'hidden', backgroundColor: '#0d1226' },
  header: { position: 'absolute', left: 24, right: 24, gap: 4 },
  kicker: { color: '#ffb46b', fontSize: 11, fontWeight: '700', letterSpacing: 2.4 },
  title: { color: INK, fontSize: 32, fontWeight: '800', letterSpacing: -0.6 },
  sub: { color: MUTE, fontSize: 14, fontWeight: '500', fontVariant: ['tabular-nums'] },
  posterInfo: { position: 'absolute', gap: 6 },
  chip: {
    alignSelf: 'flex-start',
    paddingHorizontal: 8,
    height: 20,
    justifyContent: 'center',
    borderRadius: 10,
    backgroundColor: 'rgba(10,10,30,0.55)',
  },
  chipText: { color: '#ffd9a8', fontSize: 10, fontWeight: '700', letterSpacing: 1.4 },
  lineup: {
    color: '#fbf3ea',
    fontSize: 16,
    fontWeight: '700',
    textShadowColor: 'rgba(10,6,30,0.6)',
    textShadowRadius: 8,
    textShadowOffset: { width: 0, height: 1 },
  },
  rows: { position: 'absolute', gap: 14 },
  row: { flexDirection: 'row', alignItems: 'center', height: 30 },
  avatars: { flexDirection: 'row', marginRight: 10 },
  avatar: {
    width: 28,
    height: 28,
    borderRadius: 14,
    borderWidth: 2,
    borderColor: '#111833',
    alignItems: 'center',
    justifyContent: 'center',
  },
  avatarText: { color: '#fff6ee', fontSize: 10, fontWeight: '700' },
  countClip: { height: COUNT_H, overflow: 'hidden', marginRight: 5 },
  count: { color: INK, fontSize: 17, lineHeight: COUNT_H, height: COUNT_H, fontWeight: '700', fontVariant: ['tabular-nums'] },
  going: { color: MUTE, fontSize: 15, fontWeight: '500' },
  ticket: { flex: 1, color: MUTE, fontSize: 15, fontWeight: '500' },
  price: { color: INK, fontSize: 17, fontWeight: '700', fontVariant: ['tabular-nums'] },
  hit: { position: 'absolute', alignItems: 'center', justifyContent: 'center' },
  label: { position: 'absolute', flexDirection: 'row', alignItems: 'center', gap: 8 },
  labelText: { color: CREAM, fontSize: 18, fontWeight: '700', letterSpacing: 0.2 },
  inText: { color: '#3a1606' },
  hint: { position: 'absolute', left: 24, right: 24, textAlign: 'center', color: 'rgba(214,220,245,0.45)', fontSize: 12, fontWeight: '500' },
});
