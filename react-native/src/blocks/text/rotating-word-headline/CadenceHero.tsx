import { Canvas, Picture, Skia, TileMode, vec } from '@shopify/react-native-skia';
import { LinearGradient } from 'expo-linear-gradient';
import { StatusBar } from 'expo-status-bar';
import { useMemo } from 'react';
import { Pressable, StyleSheet, Text, View, useWindowDimensions } from 'react-native';
import Animated, { type SharedValue, useAnimatedStyle, useReducedMotion, useSharedValue } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { RotatingWordHeadline } from './RotatingWordHeadline';
import { clamp01, wordDistance } from './rotatingWordMotion';

/** Cadence palette: warm paper, soft ink, one accent per word. */
export const PAPER = ['#f8f5ef', '#f3eee6', '#ece5da'] as const;
export const INK = '#1f1c18';
export const MUTE = '#6d665c';
export const WORDS = ['week', 'launch', 'wedding', 'sprint'] as const;
export const ACCENTS = ['#e0663a', '#3a6fd8', '#c64b7c', '#22936a'] as const;

type Task = { title: string; meta: string; who: string; done: boolean };
type Plan = { title: string; sub: string; tasks: Task[] };

/** The preview card under the hero follows the word: one small plan per word. */
const PLANS: Plan[] = [
  {
    title: 'This week',
    sub: 'Nov 24 – 30 · 9 items',
    tasks: [
      { title: 'Groceries and meal prep', meta: 'Mon · 6:30 pm', who: 'AL', done: true },
      { title: 'Dentist for Theo', meta: 'Wed · 9:15 am', who: 'MK', done: false },
      { title: 'Send the rent transfer', meta: 'Fri · due', who: 'AL', done: false },
    ],
  },
  {
    title: 'App launch',
    sub: 'Ships Dec 12 · 18 days left',
    tasks: [
      { title: 'Freeze the release build', meta: 'Dec 3 · Engineering', who: 'JR', done: true },
      { title: 'Store screenshots, 6 sizes', meta: 'Dec 5 · Design', who: 'SP', done: false },
      { title: 'Press note to 40 writers', meta: 'Dec 10 · Marketing', who: 'NO', done: false },
    ],
  },
  {
    title: 'Our wedding',
    sub: 'June 7 · 64 guests',
    tasks: [
      { title: 'Book the florist', meta: 'Deposit $480', who: 'EM', done: true },
      { title: 'Final menu tasting', meta: 'Jan 18 · 2 pm', who: 'DR', done: false },
      { title: 'Seating plan, draft 2', meta: '12 tables', who: 'EM', done: false },
    ],
  },
  {
    title: 'Sprint 23',
    sub: '2 weeks · 31 points',
    tasks: [
      { title: 'Offline sync for notes', meta: '8 pts · In review', who: 'KT', done: true },
      { title: 'Search across calendars', meta: '5 pts · In progress', who: 'BW', done: false },
      { title: 'Fix the reminder drift', meta: '3 pts · To do', who: 'KT', done: false },
    ],
  },
];

function makePaper(W: number, H: number) {
  const rec = Skia.PictureRecorder();
  const c = rec.beginRecording(Skia.XYWHRect(0, 0, W, H));
  const glow = Skia.Paint();
  glow.setShader(
    Skia.Shader.MakeRadialGradient(
      vec(W * 0.92, H * 0.08),
      W * 0.95,
      [Skia.Color('rgba(240,170,120,0.22)'), Skia.Color('rgba(236,196,160,0.08)'), Skia.Color('rgba(255,255,255,0)')],
      [0, 0.55, 1],
      TileMode.Clamp,
    ),
  );
  c.drawRect(Skia.XYWHRect(0, 0, W, H), glow);
  const cool = Skia.Paint();
  cool.setShader(
    Skia.Shader.MakeRadialGradient(
      vec(W * 0.05, H * 0.78),
      W * 0.9,
      [Skia.Color('rgba(130,160,220,0.12)'), Skia.Color('rgba(150,170,210,0.04)'), Skia.Color('rgba(255,255,255,0)')],
      [0, 0.5, 1],
      TileMode.Clamp,
    ),
  );
  c.drawRect(Skia.XYWHRect(0, 0, W, H), cool);
  // Seeded grain, drawn once.
  const dot = Skia.Paint();
  dot.setColor(Skia.Color('rgba(60,45,30,0.05)'));
  for (let i = 0; i < 2600; i += 1) {
    const x = (Math.sin(i * 12.9898) * 43758.5453) % 1;
    const y = (Math.sin(i * 78.233 + 3.1) * 12543.871) % 1;
    c.drawCircle(Math.abs(x) * W, Math.abs(y) * H, 0.55, dot);
  }
  return rec.finishRecordingAsPicture();
}

function Logo() {
  return (
    <View style={styles.logo}>
      <LinearGradient colors={['#2c2822', '#1a1713', '#0f0d0b']} locations={[0, 0.6, 1]} style={StyleSheet.absoluteFill} />
      <View style={styles.logoRing} />
      <View style={styles.logoDot} />
    </View>
  );
}

function PlanView({ plan, k, pos }: { plan: Plan; k: number; pos: SharedValue<number> }) {
  const style = useAnimatedStyle(() => {
    const d = wordDistance(pos.get(), k);
    const o = clamp01(1 - Math.abs(d) * 1.6);
    return { opacity: o, transform: [{ translateY: d * -10 }] };
  });
  return (
    <Animated.View style={[styles.plan, style]} pointerEvents="none">
      <View style={styles.planHead}>
        <View style={[styles.planSwatch, { backgroundColor: ACCENTS[k] }]} />
        <View style={{ flex: 1 }}>
          <Text style={styles.planTitle}>{plan.title}</Text>
          <Text style={styles.planSub}>{plan.sub}</Text>
        </View>
        <Text style={styles.planShare}>Shared</Text>
      </View>
      {plan.tasks.map((task) => (
        <View key={task.title} style={styles.task}>
          <View style={[styles.check, task.done && { backgroundColor: ACCENTS[k], borderColor: ACCENTS[k] }]}>
            {task.done ? <View style={styles.checkMark} /> : null}
          </View>
          <View style={{ flex: 1 }}>
            <Text style={[styles.taskTitle, task.done && styles.taskDone]} numberOfLines={1}>
              {task.title}
            </Text>
            <Text style={styles.taskMeta}>{task.meta}</Text>
          </View>
          <View style={[styles.avatar, { backgroundColor: `${ACCENTS[k]}22` }]}>
            <Text style={[styles.avatarText, { color: ACCENTS[k] }]}>{task.who}</Text>
          </View>
        </View>
      ))}
    </Animated.View>
  );
}

/** Cadence's landing hero around the reusable headline. */
export function CadenceHero({ autoPlay = true }: { autoPlay?: boolean }) {
  const insets = useSafeAreaInsets();
  const { width: W, height: H } = useWindowDimensions();
  const reduced = useReducedMotion();
  const paper = useMemo(() => makePaper(W, H), [W, H]);
  const pos = useSharedValue(0);
  const side = 24;
  const fontSize = Math.min(46, Math.round(W * 0.112));
  const compact = H < 720;

  return (
    <View style={styles.root}>
      <StatusBar style="dark" />
      <LinearGradient colors={PAPER} locations={[0, 0.55, 1]} style={StyleSheet.absoluteFill} />
      <Canvas style={StyleSheet.absoluteFill} pointerEvents="none">
        <Picture picture={paper} />
      </Canvas>

      <View style={[styles.content, { paddingTop: insets.top + 8, paddingBottom: insets.bottom + 12, paddingHorizontal: side }]}>
        <View style={styles.nav}>
          <Logo />
          <Text style={styles.brand}>Cadence</Text>
          <View style={{ flex: 1 }} />
          <Text style={styles.signIn}>Sign in</Text>
        </View>

        <View style={[styles.chip, { marginTop: compact ? 18 : 30 }]}>
          <View style={styles.chipBadge}>
            <Text style={styles.chipBadgeText}>New</Text>
          </View>
          <Text style={styles.chipText}>Shared timelines for small teams</Text>
        </View>

        <View style={{ marginTop: compact ? 14 : 22 }}>
          <RotatingWordHeadline
            words={WORDS}
            accents={ACCENTS}
            lead="Plan your"
            tail="calmly."
            fontSize={fontSize}
            maxWidth={W - side * 2}
            ink={INK}
            autoPlay={autoPlay}
            reducedMotion={reduced}
            progress={pos}
          />
        </View>

        <Text style={[styles.body, { marginTop: compact ? 8 : 14 }]}>
          Calendars, checklists and timelines in one shared plan, so nobody has to ask what happens next.
        </Text>

        <View style={[styles.ctaRow, { marginTop: compact ? 16 : 24 }]}>
          <Pressable style={styles.primary} accessibilityRole="button">
            <LinearGradient colors={['#2e2a24', '#1f1c18', '#151310']} locations={[0, 0.5, 1]} style={StyleSheet.absoluteFill} />
            <View style={styles.primaryEdge} />
            <Text style={styles.primaryText}>Start free</Text>
          </Pressable>
          <Pressable style={styles.secondary} accessibilityRole="button">
            <Text style={styles.secondaryText}>Take the tour</Text>
          </Pressable>
        </View>

        <View style={[styles.card, { marginTop: compact ? 18 : 28 }]}>
          <View style={styles.cardEdge} />
          {PLANS.map((plan, k) => (
            <PlanView key={plan.title} plan={plan} k={k} pos={pos} />
          ))}
        </View>

        <Text style={styles.foot}>Free for up to 5 people · No card needed</Text>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: PAPER[1] },
  content: { flex: 1 },
  nav: { flexDirection: 'row', alignItems: 'center', height: 44 },
  logo: { width: 28, height: 28, borderRadius: 8, overflow: 'hidden', alignItems: 'center', justifyContent: 'center' },
  logoRing: { width: 14, height: 14, borderRadius: 7, borderWidth: 2.5, borderColor: '#f3eee6', borderRightColor: 'transparent', transform: [{ rotate: '-30deg' }] },
  logoDot: { position: 'absolute', right: 6, top: 7, width: 4, height: 4, borderRadius: 2, backgroundColor: ACCENTS[0] },
  brand: { marginLeft: 9, fontSize: 18, fontWeight: '700', color: INK, letterSpacing: -0.3 },
  signIn: { fontSize: 16, fontWeight: '600', color: INK },
  chip: {
    alignSelf: 'flex-start',
    flexDirection: 'row',
    alignItems: 'center',
    paddingLeft: 4,
    paddingRight: 12,
    height: 30,
    borderRadius: 15,
    backgroundColor: 'rgba(255,253,249,0.85)',
    borderWidth: StyleSheet.hairlineWidth,
    borderColor: 'rgba(60,45,30,0.16)',
  },
  chipBadge: { height: 22, paddingHorizontal: 8, borderRadius: 11, backgroundColor: '#1f1c18', justifyContent: 'center', marginRight: 8 },
  chipBadgeText: { color: '#f8f5ef', fontSize: 12, fontWeight: '700' },
  chipText: { color: INK, fontSize: 13, fontWeight: '500' },
  body: { fontSize: 17, lineHeight: 24, color: MUTE, maxWidth: 360 },
  ctaRow: { flexDirection: 'row', alignItems: 'center', gap: 12 },
  primary: {
    height: 52,
    paddingHorizontal: 26,
    borderRadius: 26,
    overflow: 'hidden',
    alignItems: 'center',
    justifyContent: 'center',
  },
  primaryEdge: { position: 'absolute', left: 1, right: 1, top: 0, height: 1, backgroundColor: 'rgba(255,255,255,0.18)' },
  primaryText: { color: '#faf7f1', fontSize: 17, fontWeight: '600' },
  secondary: { height: 52, paddingHorizontal: 20, borderRadius: 26, alignItems: 'center', justifyContent: 'center', borderWidth: 1, borderColor: 'rgba(31,28,24,0.16)' },
  secondaryText: { color: INK, fontSize: 17, fontWeight: '600' },
  card: {
    flex: 1,
    minHeight: 196,
    maxHeight: 268,
    borderRadius: 22,
    backgroundColor: '#fffdf9',
    borderWidth: StyleSheet.hairlineWidth,
    borderColor: 'rgba(60,45,30,0.14)',
    shadowColor: '#3a2a1a',
    shadowOpacity: 0.12,
    shadowRadius: 28,
    shadowOffset: { width: 0, height: 14 },
    elevation: 6,
  },
  cardEdge: { position: 'absolute', left: 14, right: 14, top: 0, height: 1, backgroundColor: 'rgba(255,255,255,0.9)' },
  plan: { ...StyleSheet.absoluteFill, padding: 16, overflow: 'hidden' },
  planHead: { flexDirection: 'row', alignItems: 'center', marginBottom: 8 },
  planSwatch: { width: 10, height: 30, borderRadius: 5, marginRight: 12 },
  planTitle: { fontSize: 17, fontWeight: '700', color: INK, letterSpacing: -0.2 },
  planSub: { fontSize: 13, color: MUTE, marginTop: 1, fontVariant: ['tabular-nums'] },
  planShare: { fontSize: 13, fontWeight: '600', color: MUTE },
  task: { flexDirection: 'row', alignItems: 'center', height: 50, borderTopWidth: StyleSheet.hairlineWidth, borderTopColor: 'rgba(60,45,30,0.12)' },
  check: { width: 22, height: 22, borderRadius: 11, borderWidth: 1.5, borderColor: 'rgba(60,45,30,0.3)', marginRight: 12, alignItems: 'center', justifyContent: 'center' },
  checkMark: { width: 9, height: 5, borderLeftWidth: 2, borderBottomWidth: 2, borderColor: '#fff', transform: [{ rotate: '-45deg' }, { translateY: -1 }] },
  taskTitle: { fontSize: 15, fontWeight: '600', color: INK },
  taskDone: { color: MUTE, textDecorationLine: 'line-through' },
  taskMeta: { fontSize: 12.5, color: MUTE, marginTop: 1, fontVariant: ['tabular-nums'] },
  avatar: { width: 30, height: 30, borderRadius: 15, alignItems: 'center', justifyContent: 'center', marginLeft: 10 },
  avatarText: { fontSize: 11.5, fontWeight: '700' },
  foot: { marginTop: 12, textAlign: 'center', fontSize: 13, color: MUTE },
});
