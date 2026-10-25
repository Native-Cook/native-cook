import { StyleSheet } from 'react-native';

/* The screen's styles and palette. */

export const INK = '#2B2722';
export const MUTED = '#857867';
export const CARD = '#FFFCF7';
export const CHIP = 36;
export const CHIP_GAP = 12;

export const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: '#EDE4D7', overflow: 'hidden' },
  column: { flex: 1, paddingHorizontal: 20 },
  nav: { height: 44, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  navButton: {
    width: 40,
    height: 40,
    borderRadius: 20,
    backgroundColor: 'rgba(255,252,247,0.85)',
    alignItems: 'center',
    justifyContent: 'center',
    boxShadow: '0 1px 2px rgba(60,40,20,0.16), 0 6px 16px rgba(60,40,20,0.08)',
  },
  navGlyph: { fontSize: 26, color: INK, marginTop: -3 },
  navTitle: { fontSize: 15, fontWeight: '600', color: INK },
  where: { fontSize: 13, fontWeight: '500', color: MUTED },
  titles: { marginTop: 12, gap: 4 },
  title: { fontSize: 30, lineHeight: 36, fontWeight: '700', color: INK, letterSpacing: -0.4 },
  subtitle: { fontSize: 15, lineHeight: 20, color: MUTED },
  hero: { flex: 1, minHeight: 280 },
  fieldRow: { flexDirection: 'row', gap: 12, alignItems: 'center' },
  field: {
    flex: 1,
    height: 64,
    borderRadius: 16,
    borderCurve: 'continuous',
    backgroundColor: CARD,
    paddingHorizontal: 16,
    paddingTop: 10,
    boxShadow: '0 1px 2px rgba(60,40,20,0.14), 0 10px 24px rgba(60,40,20,0.07), inset 0 1px 0 rgba(255,255,255,0.9)',
  },
  fieldLabel: { fontSize: 12, fontWeight: '600', color: MUTED },
  input: { fontSize: 20, fontWeight: '700', color: INK, paddingVertical: 2, letterSpacing: 2 },
  cut: {
    width: 64,
    height: 64,
    borderRadius: 16,
    borderCurve: 'continuous',
    backgroundColor: '#3B5752',
    alignItems: 'center',
    justifyContent: 'center',
    boxShadow: '0 1px 2px rgba(20,30,28,0.3), 0 10px 20px rgba(20,30,28,0.16), inset 0 1px 0 rgba(255,255,255,0.18)',
  },
  cutText: { fontSize: 15, fontWeight: '700', color: '#F7F1E8' },
  tapeRow: { marginTop: 16, height: 48, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  small: { fontSize: 12, fontWeight: '600', color: MUTED, letterSpacing: 0.4, textTransform: 'uppercase' },
  chips: { flexDirection: 'row', gap: CHIP_GAP },
  chip: {
    width: CHIP,
    height: CHIP,
    borderRadius: 10,
    borderCurve: 'continuous',
    boxShadow: '0 1px 2px rgba(30,20,10,0.3), 0 4px 10px rgba(30,20,10,0.14), inset 0 -3px 5px rgba(0,0,0,0.22), inset 0 2px 3px rgba(255,255,255,0.3)',
  },
  chipShine: { position: 'absolute', left: 5, right: 5, top: 5, height: 4, borderRadius: 2, backgroundColor: 'rgba(255,255,255,0.32)' },
  ring: { position: 'absolute', left: -5, top: -5, width: CHIP + 10, height: CHIP + 10, borderRadius: 14, borderCurve: 'continuous', borderWidth: 2, borderColor: INK },
  cta: {
    marginTop: 16,
    height: 56,
    borderRadius: 28,
    backgroundColor: INK,
    alignItems: 'center',
    justifyContent: 'center',
    boxShadow: '0 2px 4px rgba(43,39,34,0.25), 0 12px 24px rgba(43,39,34,0.18), inset 0 1px 0 rgba(255,255,255,0.12)',
  },
  ctaText: { fontSize: 17, fontWeight: '600', color: '#F7F1E8' },
});
