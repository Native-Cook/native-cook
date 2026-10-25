import { LinearGradient } from 'expo-linear-gradient';
import { memo } from 'react';
import { Platform, StyleSheet, Text, View, type ViewStyle } from 'react-native';
import Animated, { type AnimatedStyle } from 'react-native-reanimated';

import { shade } from './bookshelfColor';
import type { ShelfBook } from './bookshelfData';

// The two pages above the shelf: the library's home (at rest) and a section, which the open book
// becomes. Both are static views; only their containers move.

const SERIF = Platform.select({ ios: 'Georgia', default: 'serif' });
const CREAM = '#F4EADB';
const MUTED = 'rgba(244,234,219,0.6)';
const INK = '#2B231D';
const INK_SOFT = 'rgba(43,35,29,0.62)';

/** A small drawn cover: two tones, a foil rule and the title's initials. */
function MiniCover({ tone, label, width, height }: { tone: [string, string]; label: string; width: number; height: number }) {
  return (
    <View style={[styles.mini, { width, height }]}>
      <LinearGradient colors={[shade(tone[0], 0.12), tone[0], tone[1]]} locations={[0, 0.45, 1]} start={{ x: 0, y: 0 }} end={{ x: 1, y: 1 }} style={StyleSheet.absoluteFill} />
      <View style={styles.miniSpine} />
      <View style={styles.miniRule} />
      <Text style={[styles.miniLabel, { fontSize: Math.max(9, width * 0.16) }]} numberOfLines={3}>
        {label}
      </Text>
    </View>
  );
}

export const HomePage = memo(function HomePage() {
  return (
    <View style={styles.home}>
      <View style={styles.reading}>
        <MiniCover tone={['#C96F4A', '#4A2226']} label="The Salt Orchard" width={104} height={152} />
        <View style={styles.readingText}>
          <Text style={styles.readingKicker}>CURRENTLY READING</Text>
          <Text style={styles.readingTitle}>The Salt Orchard</Text>
          <Text style={styles.readingBy}>Ines Calloway</Text>
          <View style={styles.track}>
            <LinearGradient colors={['#F0C27E', '#D9954B']} start={{ x: 0, y: 0 }} end={{ x: 1, y: 0 }} style={[styles.fill, { width: '64%' }]} />
          </View>
          <Text style={styles.readingMeta}>64% · Chapter 11 · 38 min left</Text>
          <View style={styles.continue}>
            <Text style={styles.continueText}>Continue</Text>
          </View>
        </View>
      </View>
      <View style={styles.stats}>
        {[
          ['12', 'day streak'],
          ['23', 'books this year'],
          ['4 h', 'this week'],
        ].map(([n, label]) => (
          <View key={label} style={styles.stat}>
            <Text style={styles.statNumber}>{n}</Text>
            <Text style={styles.statLabel}>{label}</Text>
          </View>
        ))}
      </View>
      <View style={styles.sectionsRow}>
        <Text style={styles.sectionsTitle}>Sections</Text>
        <Text style={styles.sectionsMeta}>Sorted by recent</Text>
      </View>
    </View>
  );
});

type SectionProps = { book: ShelfBook; textStyle: AnimatedStyle<ViewStyle> };

/** A section's page: printed on cream paper in the book's colours. */
export const SectionPage = memo(function SectionPage({ book, textStyle }: SectionProps) {
  return (
    <View style={styles.paper}>
      <LinearGradient colors={['#FBF6EC', '#F4ECDD', '#EADFCB']} locations={[0, 0.6, 1]} style={StyleSheet.absoluteFill} />
      <View style={[styles.band, { backgroundColor: book.color }]}>
        <LinearGradient colors={[shade(book.color, 0.15), book.color, shade(book.color, -0.3)]} style={StyleSheet.absoluteFill} />
      </View>
      <View style={styles.gutter} />
      <Animated.View style={[styles.paperContent, textStyle]}>
        <View style={styles.paperHead}>
          <View>
            <Text style={[styles.paperKicker, { color: shade(book.color, -0.1) }]}>SECTION</Text>
            <Text style={styles.paperTitle}>{book.section}</Text>
            <Text style={styles.paperBlurb}>{book.blurb}</Text>
          </View>
          <View style={[styles.countBadge, { backgroundColor: book.color }]}>
            <Text style={[styles.countText, { color: book.foil }]}>{book.count}</Text>
          </View>
        </View>
        <View style={styles.rule} />
        {book.rows.map((row) => (
          <View key={row.title} style={styles.row}>
            <MiniCover tone={row.tone} label={row.title.replace(/[“”]/g, '')} width={40} height={56} />
            <View style={styles.rowText}>
              <Text style={styles.rowTitle} numberOfLines={1}>
                {row.title}
              </Text>
              <Text style={styles.rowBy} numberOfLines={1}>
                {row.author}
              </Text>
              {row.progress > 0 && row.progress < 1 ? (
                <View style={styles.rowTrack}>
                  <View style={[styles.rowFill, { width: `${Math.round(row.progress * 100)}%`, backgroundColor: book.color }]} />
                </View>
              ) : (
                <Text style={styles.rowState}>{row.progress >= 1 ? 'Finished' : 'Not started'}</Text>
              )}
            </View>
          </View>
        ))}
        <View style={styles.back}>
          <Text style={styles.backText}>Back to the shelf</Text>
        </View>
      </Animated.View>
    </View>
  );
});

const styles = StyleSheet.create({
  home: { flex: 1, gap: 12, paddingTop: 6 },
  reading: {
    flexDirection: 'row',
    gap: 16,
    padding: 16,
    borderRadius: 22,
    borderCurve: 'continuous',
    backgroundColor: 'rgba(255,240,220,0.06)',
    borderWidth: StyleSheet.hairlineWidth,
    borderColor: 'rgba(255,240,220,0.14)',
    boxShadow: '0 1px 2px rgba(0,0,0,0.35), 0 14px 32px rgba(0,0,0,0.3), inset 0 1px 0 rgba(255,240,220,0.08)',
  },
  readingText: { flex: 1, gap: 3, paddingTop: 4 },
  readingKicker: { color: '#E0B36C', fontSize: 11, fontWeight: '700', letterSpacing: 1.6 },
  readingTitle: { color: CREAM, fontSize: 21, fontWeight: '700', fontFamily: SERIF, marginTop: 2 },
  readingBy: { color: MUTED, fontSize: 14, fontWeight: '500' },
  track: { height: 6, borderRadius: 3, backgroundColor: 'rgba(255,240,220,0.12)', marginTop: 12, overflow: 'hidden' },
  fill: { height: 6, borderRadius: 3 },
  readingMeta: { color: MUTED, fontSize: 12, fontWeight: '500', marginTop: 6, fontVariant: ['tabular-nums'] },
  continue: { alignSelf: 'flex-start', marginTop: 10, height: 32, paddingHorizontal: 16, borderRadius: 16, backgroundColor: '#E8B36A', justifyContent: 'center' },
  continueText: { color: '#2A1808', fontSize: 14, fontWeight: '700' },
  stats: { flexDirection: 'row', gap: 8 },
  stat: {
    flex: 1,
    height: 72,
    borderRadius: 16,
    borderCurve: 'continuous',
    paddingHorizontal: 12,
    justifyContent: 'center',
    backgroundColor: 'rgba(255,240,220,0.05)',
    borderWidth: StyleSheet.hairlineWidth,
    borderColor: 'rgba(255,240,220,0.12)',
  },
  statNumber: { color: CREAM, fontSize: 22, fontWeight: '700', fontVariant: ['tabular-nums'] },
  statLabel: { color: MUTED, fontSize: 12, fontWeight: '500' },
  sectionsRow: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'flex-end', marginTop: 'auto', paddingHorizontal: 4 },
  sectionsTitle: { color: CREAM, fontSize: 17, fontWeight: '700' },
  sectionsMeta: { color: MUTED, fontSize: 12, fontWeight: '500' },
  mini: { borderRadius: 3, overflow: 'hidden', padding: 6, justifyContent: 'flex-end', boxShadow: '0 1px 2px rgba(0,0,0,0.4), 0 6px 14px rgba(0,0,0,0.25)' },
  miniSpine: { position: 'absolute', left: 0, top: 0, bottom: 0, width: 4, backgroundColor: 'rgba(0,0,0,0.25)' },
  miniRule: { position: 'absolute', left: 8, right: 6, top: 10, height: 1, backgroundColor: 'rgba(255,236,200,0.6)' },
  miniLabel: { color: 'rgba(255,240,215,0.92)', fontFamily: SERIF, fontWeight: '700', paddingLeft: 3 },
  paper: { flex: 1, borderRadius: 16, borderCurve: 'continuous', overflow: 'hidden', boxShadow: '0 2px 4px rgba(0,0,0,0.4), 0 24px 48px rgba(0,0,0,0.4)' },
  band: { position: 'absolute', left: 0, right: 0, top: 0, height: 8, overflow: 'hidden' },
  gutter: { position: 'absolute', left: 0, top: 0, bottom: 0, width: 14, backgroundColor: 'rgba(80,55,30,0.06)' },
  paperContent: { flex: 1, paddingTop: 24, paddingHorizontal: 22, paddingBottom: 16, gap: 10 },
  paperHead: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'flex-start' },
  paperKicker: { fontSize: 11, fontWeight: '800', letterSpacing: 1.8 },
  paperTitle: { color: INK, fontSize: 30, fontWeight: '700', fontFamily: SERIF, marginTop: 2 },
  paperBlurb: { color: INK_SOFT, fontSize: 13, fontWeight: '500', fontVariant: ['tabular-nums'] },
  countBadge: { width: 44, height: 44, borderRadius: 22, alignItems: 'center', justifyContent: 'center', boxShadow: 'inset 0 1px 0 rgba(255,255,255,0.2), 0 2px 6px rgba(0,0,0,0.25)' },
  countText: { fontSize: 16, fontWeight: '800', fontVariant: ['tabular-nums'] },
  rule: { height: StyleSheet.hairlineWidth * 2, backgroundColor: 'rgba(43,35,29,0.16)' },
  row: { flexDirection: 'row', alignItems: 'center', gap: 12, height: 62 },
  rowText: { flex: 1, gap: 2 },
  rowTitle: { color: INK, fontSize: 15, fontWeight: '700' },
  rowBy: { color: INK_SOFT, fontSize: 12, fontWeight: '500' },
  rowTrack: { height: 4, borderRadius: 2, backgroundColor: 'rgba(43,35,29,0.1)', marginTop: 6, overflow: 'hidden' },
  rowFill: { height: 4, borderRadius: 2 },
  rowState: { color: INK_SOFT, fontSize: 11, fontWeight: '600', marginTop: 4, letterSpacing: 0.4 },
  back: { marginTop: 'auto', alignSelf: 'center', height: 36, paddingHorizontal: 18, borderRadius: 18, backgroundColor: 'rgba(43,35,29,0.08)', justifyContent: 'center' },
  backText: { color: INK, fontSize: 14, fontWeight: '600' },
});
