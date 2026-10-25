import { LinearGradient } from 'expo-linear-gradient';
import { Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';
import Animated, { type SharedValue, useAnimatedStyle } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import Svg, { Path, Rect } from 'react-native-svg';

import { revealOf } from './zipperMotion';

/*
 The journal under the denim.
 The header, the week, three note cards, a memory and the New note button; each comes in 40 ms after the one before it (0.36 s ease-out, 18 pt rise, from scale 0.98),
 driven by the seconds since the halves started to open; nothing here re-renders while it plays.
*/

const NOTES = [
  { title: 'Things the sea taught me', body: 'Tide pools at seven. Bring the green notebook and the good pencil.', meta: '10:12 · Morning', tint: '#3A5A8C' },
  { title: 'Gift list', body: 'Fountain pen for Mara, a linen scarf, the plum jam from the Saturday market.', meta: 'Yesterday · Lists', tint: '#C2852F' },
  { title: 'Half a poem', body: 'Some doors are opened by the hand that waits. Some by the one that knocks twice.', meta: 'Thu · Drafts', tint: '#6E8B68' },
];

const WEEK = [
  { day: 'Mon', num: 20, notes: true },
  { day: 'Tue', num: 21, notes: false },
  { day: 'Wed', num: 22, notes: true },
  { day: 'Thu', num: 23, notes: true },
  { day: 'Fri', num: 24, notes: true },
  { day: 'Sat', num: 25, notes: true, today: true },
  { day: 'Sun', num: 26, notes: false },
];

const PAPER = '#F3EEE4';
const INK = '#1F2433';
const INK_SOFT = 'rgba(31,36,51,0.6)';

function Reveal({ since, index, children, style }: { since: SharedValue<number>; index: number; children: React.ReactNode; style?: object }) {
  const anim = useAnimatedStyle(() => {
    const r = revealOf(since.get(), index);
    return { opacity: r, transform: [{ translateY: (1 - r) * 18 }, { scale: 0.98 + 0.02 * r }] };
  });
  return <Animated.View style={[style, anim]}>{children}</Animated.View>;
}

function LockGlyph() {
  return (
    <Svg width={18} height={18} viewBox="0 0 18 18">
      <Rect x={3.5} y={8} width={11} height={8} rx={2.2} fill={INK} />
      <Path d="M6 8 V5.8 a3 3 0 0 1 6 0 V8" stroke={INK} strokeWidth={1.8} fill="none" strokeLinecap="round" />
    </Svg>
  );
}

export function ZipperJournal({ since, onLock }: { since: SharedValue<number>; onLock: () => void }) {
  const insets = useSafeAreaInsets();
  return (
    <View style={StyleSheet.absoluteFill}>
      <LinearGradient colors={['#F6F1E8', PAPER, '#E9E1D3']} locations={[0, 0.5, 1]} style={StyleSheet.absoluteFill} />
      <ScrollView
        style={StyleSheet.absoluteFill}
        contentContainerStyle={[styles.content, { paddingTop: insets.top + 24, paddingBottom: insets.bottom + 96 }]}
        showsVerticalScrollIndicator={false}
      >
        <Reveal since={since} index={0} style={styles.header}>
          <View style={{ flex: 1, gap: 2 }}>
            <Text style={styles.kicker}>SAT 25 OCT</Text>
            <Text style={styles.title}>Pocket</Text>
            <Text style={styles.secondary}>3 notes · last edit 10:12</Text>
          </View>
          <Pressable onPress={onLock} hitSlop={8} accessibilityRole="button" accessibilityLabel="Lock the journal" style={styles.lock}>
            <LockGlyph />
          </Pressable>
        </Reveal>
        <Reveal since={since} index={1} style={styles.week}>
          {WEEK.map((d) => (
            <View key={d.day} style={[styles.day, d.today && styles.dayToday]}>
              <Text style={[styles.dayName, d.today && styles.dayTodayText]}>{d.day}</Text>
              <Text style={[styles.dayNum, d.today && styles.dayTodayText]}>{d.num}</Text>
              <View style={[styles.dayDot, { opacity: d.notes ? 1 : 0 }, d.today && { backgroundColor: '#F3EEE4' }]} />
            </View>
          ))}
        </Reveal>
        <View style={styles.cards}>
          {NOTES.map((n, i) => (
            <Reveal key={n.title} since={since} index={i + 2} style={styles.card}>
              <View style={styles.cardTop}>
                <View style={[styles.dot, { backgroundColor: n.tint }]} />
                <Text style={styles.cardTitle} numberOfLines={1}>
                  {n.title}
                </Text>
              </View>
              <Text style={styles.cardBody} numberOfLines={2}>
                {n.body}
              </Text>
              <Text style={styles.cardMeta}>{n.meta}</Text>
            </Reveal>
          ))}
        </View>
        <Reveal since={since} index={5} style={styles.memory}>
          <Text style={styles.memoryKicker}>A YEAR AGO TODAY</Text>
          <Text style={styles.memoryText}>&ldquo;Rain on the ferry, and the best soup I have ever had in a paper cup.&rdquo;</Text>
        </Reveal>
      </ScrollView>
      <Reveal since={since} index={6} style={[styles.newRow, { bottom: insets.bottom + 20 }]}>
        <View style={styles.newButton}>
          <Text style={styles.newPlus}>+</Text>
          <Text style={styles.newText}>New note</Text>
        </View>
      </Reveal>
    </View>
  );
}

const styles = StyleSheet.create({
  content: { paddingHorizontal: 20, gap: 20 },
  week: { flexDirection: 'row', justifyContent: 'space-between' },
  day: { width: 44, height: 64, borderRadius: 22, alignItems: 'center', justifyContent: 'center', gap: 2, backgroundColor: 'rgba(80,60,30,0.05)' },
  dayToday: { backgroundColor: '#22365E', boxShadow: '0 1px 2px rgba(20,30,60,0.3), 0 8px 18px rgba(20,30,60,0.2)' },
  dayName: { color: INK_SOFT, fontSize: 11, fontWeight: '600' },
  dayNum: { color: INK, fontSize: 17, fontWeight: '600', fontVariant: ['tabular-nums'] },
  dayTodayText: { color: '#F3EEE4' },
  dayDot: { width: 4, height: 4, borderRadius: 2, backgroundColor: '#C2852F' },
  memory: {
    borderRadius: 20,
    borderCurve: 'continuous',
    padding: 16,
    gap: 6,
    backgroundColor: 'rgba(194,133,47,0.10)',
    borderWidth: StyleSheet.hairlineWidth,
    borderColor: 'rgba(194,133,47,0.3)',
  },
  memoryKicker: { color: '#9A6B2F', fontSize: 11, fontWeight: '700', letterSpacing: 1.2 },
  memoryText: { color: INK, fontSize: 16, lineHeight: 22, fontStyle: 'italic' },
  header: { flexDirection: 'row', alignItems: 'flex-start' },
  kicker: { color: '#9A6B2F', fontSize: 12, fontWeight: '700', letterSpacing: 1.4 },
  title: { color: INK, fontSize: 34, lineHeight: 40, fontWeight: '700', letterSpacing: -0.5 },
  secondary: { color: INK_SOFT, fontSize: 15, fontWeight: '500', fontVariant: ['tabular-nums'] },
  lock: {
    width: 44,
    height: 44,
    borderRadius: 22,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: '#FBF8F2',
    boxShadow: '0 1px 2px rgba(60,44,20,0.18), 0 8px 18px rgba(60,44,20,0.10)',
  },
  cards: { gap: 12 },
  card: {
    borderRadius: 20,
    borderCurve: 'continuous',
    padding: 16,
    gap: 6,
    backgroundColor: '#FCFAF5',
    borderWidth: StyleSheet.hairlineWidth,
    borderColor: 'rgba(80,60,30,0.12)',
    boxShadow: '0 1px 2px rgba(60,44,20,0.14), 0 12px 28px rgba(60,44,20,0.10)',
  },
  cardTop: { flexDirection: 'row', alignItems: 'center', gap: 8 },
  dot: { width: 8, height: 8, borderRadius: 4 },
  cardTitle: { flex: 1, color: INK, fontSize: 17, fontWeight: '600' },
  cardBody: { color: 'rgba(31,36,51,0.78)', fontSize: 15, lineHeight: 21 },
  cardMeta: { color: INK_SOFT, fontSize: 12, fontWeight: '500', fontVariant: ['tabular-nums'] },
  newRow: { position: 'absolute', left: 0, right: 0, alignItems: 'center' },
  newButton: {
    height: 48,
    paddingHorizontal: 24,
    borderRadius: 24,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
    backgroundColor: '#22365E',
    boxShadow: '0 1px 2px rgba(20,30,60,0.3), 0 10px 22px rgba(20,30,60,0.22)',
  },
  newPlus: { color: '#F3EEE4', fontSize: 20, fontWeight: '500', marginTop: -2 },
  newText: { color: '#F3EEE4', fontSize: 16, fontWeight: '600' },
});
