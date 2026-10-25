import { StyleSheet } from 'react-native';

/* The screen's styles and palette. */

export const INK = '#3B2418';
export const MUTED = '#8C7462';
export const CARD = '#FFFBF6';
export const CHIP = 38;
export const CHIP_GAP = 14;
export const DIGIT_H = 30;

export const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: '#F1E9DF', overflow: 'hidden' },
  column: { flex: 1, paddingHorizontal: 20 },
  nav: { height: 44, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  navButton: {
    width: 40,
    height: 40,
    borderRadius: 20,
    backgroundColor: 'rgba(255,252,247,0.85)',
    alignItems: 'center',
    justifyContent: 'center',
    boxShadow: '0 1px 2px rgba(70,40,20,0.16), 0 6px 16px rgba(70,40,20,0.08)',
  },
  navGlyph: { fontSize: 26, color: INK, marginTop: -3 },
  navTitle: { fontSize: 15, fontWeight: '600', color: INK, letterSpacing: 0.2 },
  step: { fontSize: 13, fontWeight: '500', color: MUTED, fontVariant: ['tabular-nums'], width: 76, textAlign: 'right' },
  titles: { marginTop: 12, gap: 4 },
  title: { fontSize: 30, lineHeight: 36, fontWeight: '700', color: INK, letterSpacing: -0.4 },
  subtitle: { fontSize: 15, lineHeight: 20, color: MUTED },
  hero: { flex: 1, minHeight: 240 },
  field: {
    height: 64,
    borderRadius: 16,
    borderCurve: 'continuous',
    backgroundColor: CARD,
    paddingHorizontal: 16,
    paddingTop: 10,
    boxShadow: '0 1px 2px rgba(70,40,20,0.14), 0 10px 24px rgba(70,40,20,0.07), inset 0 1px 0 rgba(255,255,255,0.9)',
  },
  fieldLabel: { fontSize: 12, fontWeight: '600', color: MUTED },
  input: { fontSize: 20, fontWeight: '600', color: INK, paddingVertical: 2, paddingRight: 52 },
  letters: { position: 'absolute', right: 16, bottom: 12, fontSize: 13, color: '#B39C8A', fontVariant: ['tabular-nums'] },
  icingRow: { marginTop: 16, height: 48, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  icingLabel: { gap: 2 },
  small: { fontSize: 12, fontWeight: '600', color: MUTED, letterSpacing: 0.4, textTransform: 'uppercase' },
  chips: { flexDirection: 'row', gap: CHIP_GAP },
  chip: {
    width: CHIP,
    height: CHIP,
    borderRadius: CHIP / 2,
    boxShadow: '0 1px 2px rgba(50,25,15,0.3), 0 4px 10px rgba(50,25,15,0.14), inset 0 -3px 6px rgba(0,0,0,0.2), inset 0 2px 3px rgba(255,255,255,0.35)',
  },
  chipShine: { position: 'absolute', left: 8, top: 6, width: 13, height: 7, borderRadius: 4, backgroundColor: 'rgba(255,255,255,0.35)', transform: [{ rotate: '-20deg' }] },
  ring: { position: 'absolute', left: -5, top: -5, width: CHIP + 10, height: CHIP + 10, borderRadius: (CHIP + 10) / 2, borderWidth: 2, borderColor: INK },
  footer: { marginTop: 16, flexDirection: 'row', alignItems: 'flex-end', justifyContent: 'space-between' },
  total: { gap: 2 },
  cakeLine: { fontSize: 14, color: MUTED },
  cta: {
    marginTop: 16,
    height: 56,
    borderRadius: 28,
    backgroundColor: INK,
    alignItems: 'center',
    justifyContent: 'center',
    boxShadow: '0 2px 4px rgba(59,36,24,0.25), 0 12px 24px rgba(59,36,24,0.18), inset 0 1px 0 rgba(255,255,255,0.12)',
  },
  ctaText: { fontSize: 17, fontWeight: '600', color: '#FBF1E6' },
});
