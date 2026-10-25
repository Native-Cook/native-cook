import { StyleSheet } from 'react-native';

import { SERIF } from './foilStampArt';

export const INK = '#2b2219';
export const MUTE = '#7d6d5c';
export const FAINT = '#9c8a72';
export const LABEL = '#f3e3bf';

export const styles = StyleSheet.create({
  root: { flex: 1, overflow: 'hidden', backgroundColor: '#e6ddcf' },
  header: { position: 'absolute', left: 24, right: 24, gap: 4 },
  kicker: { color: FAINT, fontSize: 11, fontWeight: '700', letterSpacing: 2.4 },
  title: { color: INK, fontSize: 30, fontWeight: '700', letterSpacing: -0.4, fontVariant: ['tabular-nums'] },
  sub: { color: MUTE, fontSize: 14, fontWeight: '500' },
  card: { position: 'absolute', borderRadius: 22, borderCurve: 'continuous' },
  cardTop: { flexDirection: 'row', justifyContent: 'space-between', paddingHorizontal: 18, paddingTop: 16 },
  cardKicker: { color: FAINT, fontSize: 10, fontWeight: '700', letterSpacing: 1.6 },
  cardNumber: { color: FAINT, fontSize: 11, fontWeight: '600', fontVariant: ['tabular-nums'] },
  cardFoot: {
    position: 'absolute',
    bottom: 16,
    left: 0,
    right: 0,
    textAlign: 'center',
    color: '#6f604f',
    fontSize: 14,
    fontStyle: 'italic',
    fontFamily: SERIF,
  },
  rows: { position: 'absolute', gap: 6 },
  row: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'baseline', height: 22 },
  rowName: { color: MUTE, fontSize: 14, fontWeight: '500' },
  rowPrice: { color: INK, fontSize: 14, fontWeight: '600', fontVariant: ['tabular-nums'] },
  rule: { height: StyleSheet.hairlineWidth, backgroundColor: 'rgba(80,60,35,0.25)', marginVertical: 4 },
  totalName: { color: INK, fontSize: 16, fontWeight: '700' },
  totalPrice: { color: INK, fontSize: 16, fontWeight: '700', fontVariant: ['tabular-nums'] },
  hit: { position: 'absolute', alignItems: 'center', justifyContent: 'center', borderRadius: 999 },
  stampLabel: { position: 'absolute', color: LABEL, fontSize: 15, fontWeight: '700', letterSpacing: 0.2 },
  sealedLabel: { marginTop: 22, fontSize: 13 },
  hint: { position: 'absolute', left: 24, right: 24, textAlign: 'center', color: FAINT, fontSize: 12, fontWeight: '500' },
});
