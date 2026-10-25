import { StyleSheet } from 'react-native';

/* The screen's styles and palette. */

export const INK = '#2E2620';
export const MUTED = '#7D6D5C';
export const CARD = '#FBF7EF';
export const CHIP = 40;
export const CHIP_GAP = 14;
export const DIGIT_H = 22;

export const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: '#EDE3D3', overflow: 'hidden' },
  column: { flex: 1, paddingHorizontal: 20 },
  nav: { height: 44, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  navButton: {
    width: 40,
    height: 40,
    borderRadius: 20,
    backgroundColor: 'rgba(255,251,243,0.8)',
    alignItems: 'center',
    justifyContent: 'center',
    boxShadow: '0 1px 2px rgba(74,50,32,0.18), 0 6px 16px rgba(74,50,32,0.08)',
  },
  navGlyph: { fontSize: 26, color: INK, marginTop: -3 },
  navTitle: { fontSize: 15, fontWeight: '600', color: INK, letterSpacing: 0.2 },
  kit: { height: 28, paddingHorizontal: 12, borderRadius: 14, borderWidth: 1, borderColor: 'rgba(46,38,32,0.18)', justifyContent: 'center' },
  kitText: { fontSize: 11, fontWeight: '700', color: MUTED, letterSpacing: 1.2 },
  titles: { marginTop: 12, gap: 4 },
  title: { fontSize: 30, lineHeight: 36, fontWeight: '700', color: INK, letterSpacing: -0.4 },
  subtitle: { fontSize: 15, lineHeight: 20, color: MUTED },
  hero: { flex: 1, minHeight: 220 },
  stats: { flexDirection: 'row', justifyContent: 'space-between', marginBottom: 12 },
  stat: { gap: 2 },
  statLabel: { fontSize: 12, fontWeight: '600', color: MUTED, letterSpacing: 0.4, textTransform: 'uppercase' },
  statValue: { flexDirection: 'row', alignItems: 'center' },
  slash: { fontSize: 17, fontWeight: '600', color: MUTED, fontVariant: ['tabular-nums'] },
  field: {
    height: 64,
    borderRadius: 16,
    borderCurve: 'continuous',
    backgroundColor: CARD,
    paddingHorizontal: 16,
    paddingTop: 10,
    boxShadow: '0 1px 2px rgba(74,50,32,0.16), 0 10px 24px rgba(74,50,32,0.08), inset 0 1px 0 rgba(255,255,255,0.8)',
  },
  fieldLabel: { fontSize: 12, fontWeight: '600', color: MUTED },
  input: { fontSize: 20, fontWeight: '600', color: INK, paddingVertical: 2, paddingRight: 48 },
  letters: { position: 'absolute', right: 16, bottom: 12, fontSize: 13, color: '#A8988A', fontVariant: ['tabular-nums'] },
  chipsRow: { marginTop: 16, height: 48, justifyContent: 'center' },
  chips: { flexDirection: 'row', gap: CHIP_GAP, alignSelf: 'center' },
  chip: {
    width: CHIP,
    height: CHIP,
    borderRadius: CHIP / 2,
    boxShadow: '0 1px 2px rgba(46,30,20,0.3), 0 4px 10px rgba(46,30,20,0.14), inset 0 -3px 6px rgba(0,0,0,0.22), inset 0 2px 3px rgba(255,255,255,0.28)',
  },
  chipShine: {
    position: 'absolute',
    left: 8,
    top: 6,
    width: 14,
    height: 8,
    borderRadius: 4,
    backgroundColor: 'rgba(255,255,255,0.28)',
    transform: [{ rotate: '-20deg' }],
  },
  ring: {
    position: 'absolute',
    left: -5,
    top: -5,
    width: CHIP + 10,
    height: CHIP + 10,
    borderRadius: (CHIP + 10) / 2,
    borderWidth: 2,
    borderColor: INK,
  },
  cta: {
    marginTop: 16,
    height: 56,
    borderRadius: 28,
    backgroundColor: INK,
    alignItems: 'center',
    justifyContent: 'center',
    boxShadow: '0 2px 4px rgba(46,38,32,0.25), 0 12px 24px rgba(46,38,32,0.18), inset 0 1px 0 rgba(255,255,255,0.12)',
  },
  ctaText: { fontSize: 17, fontWeight: '600', color: '#F6EEDF' },
});
