import { Canvas, Group, Image as SkImage, useImage, type Transforms3d } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useCallback, useMemo, useRef, useState } from 'react';
import { Pressable, StyleSheet, Text, View, useWindowDimensions } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  interpolateColor,
  FadeInDown, FadeOut, FadeOutUp, LinearTransition, SensorType, useAnimatedSensor, useAnimatedStyle, useDerivedValue, useFrameCallback, useReducedMotion,
  useSharedValue, type SharedValue,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';

import { EVENT, ORB, POOL, aimArc, initialSim, input, makeGeometry, stepSim, type Input } from './jar-sim';
import { MoodScene, type MoodImages, type MoodValues } from './MoodScene';
import { GHOST, HAPTIC, LID, QA_SPEED, REPLICA_HOLD, RIPPLE, THROW, UNDO } from './motion';
import { COLORS, COPY, LAYOUT, MOODS, REPLICA, SPACE } from './theme';

type Props = { autoPlay?: boolean };
type Entry = { id: number; mood: number; tags: string; time: string; fresh: boolean };

function playHaptic(kind: number) {
  if (kind === HAPTIC.grab) Haptics.selectionAsync();
  else if (kind === HAPTIC.throw) Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light);
  else if (kind === HAPTIC.land) Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Medium);
  else if (kind === HAPTIC.landSoft) Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light);
  else if (kind === HAPTIC.landHard) Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Heavy);
  else if (kind === HAPTIC.undo) Haptics.notificationAsync(Haptics.NotificationFeedbackType.Warning);
}

// Orb artwork, generated from the reference (one per mood, awful to great).
// The source's own orbs (traced from its mood picker), for the picker, new orbs and the avatars.
const ORB_ART = [
  require('./assets/orb-awful-r.png'),
  require('./assets/orb-bad-r.png'),
  require('./assets/orb-neutral-r.png'),
  require('./assets/orb-good-r.png'),
  require('./assets/orb-great-r.png'),
];

const START: Entry[] = COPY.entries.map((e, i) => ({ id: -1 - i, mood: e.mood, tags: e.tags, time: e.time, fresh: false }));

export function MoodOrbJar({ autoPlay = false }: Props) {
  const { width, height } = useWindowDimensions();
  const insets = useSafeAreaInsets();
  const reduced = useReducedMotion();
  const g = useMemo(() => makeGeometry(width, height, insets.top), [width, height, insets.top]);
  const [entries, setEntries] = useState<Entry[]>(START);
  const jar = useImage(require('./assets/jar.png'));
  const front = useImage(require('./assets/jar-front.png'));
  const lidImg = useImage(require('./assets/lid.png'));
  const plate = useImage(require('./assets/pile-plate.png'));
  const plateB = useImage(require('./assets/pile-plate-b.png'));
  const jarPlate = useImage(require('./assets/jar-plate.png'));
  const pileAtlas = useImage(require('./assets/pile-atlas.png'));
  const o0 = useImage(ORB_ART[0]);
  const o1 = useImage(ORB_ART[1]);
  const o2 = useImage(ORB_ART[2]);
  const o3 = useImage(ORB_ART[3]);
  const o4 = useImage(ORB_ART[4]);
  const images = useMemo<MoodImages>(() => ({ jar, front, lid: lidImg, orbs: [o0, o1, o2, o3, o4], plate, plateB, jarPlate, pileAtlas }), [jar, front, lidImg, o0, o1, o2, o3, o4, plate, plateB, jarPlate, pileAtlas]);
  const nextId = useRef(1);

  // Settling the pile takes a few hundred steps: once, not on every render.
  const [initial] = useState(() => initialSim(g, reduced, autoPlay));
  const sim = useSharedValue(initial);
  const orbs = useSharedValue<number[]>([]);
  const ghost = useSharedValue<number[]>([0, 0, 1, 0, 0]); // x, y, scale, mood, visible
  const grow = useSharedValue(1); // the new entry card's container morph, 0..1
  const ripple = useSharedValue<number[]>([0, 0, 0, 0, 0]); // x, y, progress, mood, visible
  const aim = useSharedValue<number[]>([]);
  const tiltX = useSharedValue(0);
  const countNow = useSharedValue(-1);
  const [count, setCount] = useState(0);
  const rotation = useAnimatedSensor(SensorType.ROTATION);
  // The new entry's avatar: the first card's orb, where the ghost lands.
  const avatarX = LAYOUT.side * g.s + SPACE.cardPad + LAYOUT.entryOrb;
  const avatarY = g.rowY + SPACE.avatarTop + LAYOUT.entryOrb;
  const slots = useSharedValue<number[]>([1, 1, 1, 1, 1]);
  const cut = useSharedValue(0);
  const lidAngle = useSharedValue(0);
  const rowX = useSharedValue(0);

  const onEvent = useCallback((e: number) => {
    if (e >= EVENT.removed) {
      setEntries((list) => (list[0]?.fresh ? list.slice(1) : list));
    } else if (e >= EVENT.added) {
      const mood = e - EVENT.added;
      const id = nextId.current++;
      setEntries((list) => [{
        id, mood, tags: REPLICA ? COPY.replicaEntry.tags : MOODS[mood].tags, time: REPLICA ? COPY.replicaEntry.time : COPY.now, fresh: true,
      }, ...list.map((x) => ({ ...x, fresh: false }))]);
    } else {
      playHaptic(e);
    }
  }, []);

  const publish = useCallback(() => {
    'worklet';
    const s = sim.get();
    const out: number[] = [];
    const small = LAYOUT.pickerOrb / LAYOUT.orb;
    for (let i = 0; i < POOL; i++) {
      const st = s.state[i];
      let scale = 1;
      if (st === ORB.held) scale = small * THROW.lift;
      else if (st === ORB.returning) scale = small;
      else if (st === ORB.leaving) scale = 1 - (1 - small) * Math.min(1, s.t[i] / (UNDO.rise + UNDO.fly));
      out.push(s.x[i], s.y[i], s.spin[i], scale, st === ORB.off ? 0 : s.fade[i], s.mood[i], s.sq[i], s.vx[i], s.vy[i], st === ORB.flying ? 1 : 0);
    }
    orbs.set(out);
    // Shared element: the landed orb's copy flies to the new entry's avatar on an arc.
    if (s.ghostT >= 0) {
      // Lifts straight out of the jar, then arcs (ease-in-out) to the new entry's avatar, shrinking to its size.
      const u = Math.min(1, s.ghostT / GHOST.duration);
      const l = Math.min(1, u / GHOST.lift);
      const lift = 1 - Math.pow(1 - l, 3);
      const sx = s.ghostX;
      const sy = s.ghostY - GHOST.rise * g.s * lift;
      const a = Math.max(0, (u - GHOST.lift) / (1 - GHOST.lift));
      const e = a < 0.5 ? 4 * a * a * a : 1 - Math.pow(-2 * a + 2, 3) / 2;
      const end = LAYOUT.entryOrb / LAYOUT.orb;
      ghost.set([sx + (avatarX - sx) * e, sy + (avatarY - sy) * e - Math.sin(Math.PI * e) * GHOST.arc * g.s, 1 + 0.08 * lift * (1 - e) + (end - 1) * e, s.ghostMood, 1]);
    } else ghost.set([0, 0, 1, 0, 0]);
    grow.set(s.reduced || s.growT === -1 ? 1 : s.growT < 0 ? 0 : Math.min(1, s.growT / GHOST.grow));
    ripple.set(s.rippleT < 0 ? [0, 0, 0, 0, 0] : [s.rippleX, s.rippleY, s.rippleT / RIPPLE.duration, s.rippleMood, 1]);
    aim.set(aimArc(s, g));
    tiltX.set(s.tilt);
    let n = 0;
    for (let i = 0; i < POOL; i++) if (s.state[i] === ORB.pile) n++;
    if (n !== countNow.get()) {
      countNow.set(n);
      scheduleOnRN(setCount, n);
    }
    slots.set(s.slotScale.slice());
    cut.set(s.cut);
    lidAngle.set(s.lid);
    rowX.set(s.rowX);
    for (let i = 0; i < s.events.length; i++) scheduleOnRN(onEvent, s.events[i]);
    s.events = [];
  }, [sim, orbs, slots, lidAngle, rowX, onEvent, aim, avatarX, avatarY, countNow, g, ghost, ripple, tiltX, grow, cut]);

  useFrameCallback((frame) => {
    'worklet';
    if (frame.timeSincePreviousFrame === null) return;
    const s = sim.get();
    s.reduced = reduced;
    s.tilt = reduced ? 0 : Math.max(-1, Math.min(1, rotation.sensor.value.roll));
    if (REPLICA_HOLD >= 0) {
      // QA only: run to the held time once, then freeze.
      if (s.clock === 0) stepSim(s, g, REPLICA_HOLD, autoPlay, true);
    } else stepSim(s, g, (frame.timeSincePreviousFrame / 1000) * QA_SPEED, autoPlay);
    sim.set(s);
    publish();
  });

  const act = useCallback((ev: Input) => {
    'worklet';
    const s = sim.get();
    input(s, g, ev);
    sim.set(s);
  }, [sim, g]);

  // A touch on a picker orb or on the newest entry becomes this block's drag; anything else is left alone.
  const hits = useCallback((x: number, y: number) => {
    'worklet';
    const s = sim.get();
    for (let m = 0; m < MOODS.length; m++) if (Math.hypot(x - g.slotX[m], y - g.slotY) < 30 * g.s) return true;
    return s.lastAdded >= 0 && y > g.rowY && y < g.rowY + g.rowH && x < g.w - 64 * g.s;
  }, [sim, g]);
  const gesture = useMemo(() => Gesture.Pan()
    .manualActivation(true)
    .maxPointers(1)
    .onTouchesDown((e, manager) => {
      const t = e.changedTouches[0];
      if (hits(t.x, t.y)) {
        act({ type: 'down', x: t.x, y: t.y });
        manager.activate();
      } else {
        manager.fail();
      }
    })
    .onUpdate((e) => act({ type: 'move', x: e.x, y: e.y, vx: e.velocityX, vy: e.velocityY }))
    .onEnd((e) => act({ type: 'up', vx: e.velocityX, vy: e.velocityY }))
    .onFinalize((e, ok) => {
      if (!ok) act({ type: 'up', vx: 0, vy: 0 });
    }), [act, hits]);

  const command = useCallback((action: 'add' | 'undo', mood?: number) => scheduleOnUI(act, { type: 'command', action, mood }), [act]);

  const lid = useDerivedValue<Transforms3d>(() => {
    const hy = g.lidY + LAYOUT.lidHeight * g.s;
    const l = lidAngle.value;
    const cxl = g.lidX + (LAYOUT.lidWidth * g.s) / 2;
    // Lifts straight up; tilts a little only while moving between closed and lifted.
    return [
      { translateY: -LID.lift * g.s * l }, { translateX: cxl }, { translateY: hy }, { rotate: -LID.tilt * l },
      { translateX: -cxl }, { translateY: -hy },
    ];
  });
  const values: MoodValues = { orbs, slots, lid, ghost, ripple, aim, tilt: tiltX, cut };
  const ghostD = LAYOUT.orb * 2 * g.s * (144 / 132);
  const ghostT = useDerivedValue<Transforms3d>(() => [{ translateX: ghost.value[0] }, { translateY: ghost.value[1] }, { scale: ghost.value[2] }]);
  const ghostO = useDerivedValue(() => ghost.value[4]);
  const ghostImg = useDerivedValue(() => images.orbs[ghost.value[3]] ?? null);
  const cardW = width - LAYOUT.side * g.s * 2;

  const listTop = g.rowY;

  return (
    <View style={styles.root}>
      <StatusBar style="dark" hidden={REPLICA} />
      <GestureDetector gesture={gesture}>
        <View style={StyleSheet.absoluteFill} collapsable={false}>
          <Canvas style={StyleSheet.absoluteFill}>
            <MoodScene g={g} v={values} images={images} />
          </Canvas>

          <View pointerEvents="box-none" style={[styles.header, { top: insets.top, height: LAYOUT.header * g.s }]}>
            <Pressable accessibilityRole="button" accessibilityLabel={COPY.back} style={styles.icon}>
              <View style={styles.chevron} />
            </Pressable>
            <View style={styles.headerRight}>
              <Pressable accessibilityRole="button" accessibilityLabel={COPY.calendar} style={styles.icon}>
                <View style={styles.calendar}>
                  <Text style={styles.calendarText}>14</Text>
                </View>
              </Pressable>
              <Pressable accessibilityRole="button" accessibilityLabel={COPY.more} style={styles.icon}>
                <Text style={styles.more}>•••</Text>
              </Pressable>
            </View>
          </View>


          {MOODS.map((m, i) => (
            <Pressable
              key={m.name}
              accessibilityRole="button"
              accessibilityLabel={COPY.add(m.name)}
              onPress={() => command('add', i)}
              style={[styles.slot, { left: g.slotX[i] - 30 * g.s, top: g.slotY - 24 * g.s, width: 60 * g.s, height: LAYOUT.slotLabelH * g.s }]}
            >
              <Text style={styles.slotLabel}>{m.name}</Text>
            </Pressable>
          ))}

          {REPLICA ? null : (
            <View pointerEvents="none" style={[styles.label, { top: g.mouthY + LAYOUT.jarHeight * LAYOUT.labelY * g.s }]}>
              <Text style={styles.labelMonth}>{COPY.month}</Text>
              {/* The count rolls like an odometer when an orb joins or leaves. */}
              <View style={styles.countBox}>
                <Animated.Text key={count} entering={reduced ? undefined : FadeInDown.duration(260)} exiting={reduced ? undefined : FadeOutUp.duration(200)} style={styles.count}>
                  {count}
                </Animated.Text>
              </View>
            </View>
          )}
          <View pointerEvents="box-none" style={[styles.list, { top: listTop, left: LAYOUT.side * g.s, right: LAYOUT.side * g.s }]}>
            {/* The source's cards, one per entry. */}
            <View>
            {entries.slice(0, 3).map((e, i) => (
              <EntryRow key={e.id} entry={e} swipe={i === 0 && e.fresh ? rowX : null} grow={i === 0 && e.fresh ? grow : null} width={cardW} last={i === Math.min(entries.length, 3) - 1} onUndo={() => command('undo')} />
            ))}
            </View>
          </View>
          {/* The shared element flies above the list, so it lands on the new card's avatar. */}
          <Canvas pointerEvents="none" style={StyleSheet.absoluteFill}>
            <Group transform={ghostT} opacity={ghostO}>
              <SkImage image={ghostImg} x={-ghostD / 2} y={-ghostD / 2} width={ghostD} height={ghostD} fit="fill" />
            </Group>
          </Canvas>
        </View>
      </GestureDetector>
    </View>
  );
}

function EntryRow({ entry, swipe, grow, width, last, onUndo }: {
  entry: Entry; swipe: SharedValue<number> | null; grow: SharedValue<number> | null; width: number; last: boolean; onUndo: () => void;
}) {
  const mood = MOODS[entry.mood];
  // Container morph: the card's surface grows out of the avatar (an absolute, childless view: width is allowed),
  // then the text fades in.
  const avatarBox = LAYOUT.entryOrb * 2 + 32;
  const bg = useAnimatedStyle(() => {
    const k = grow ? grow.value : 1;
    const e = 1 - Math.pow(1 - k, 3);
    // Transform only: the full-width surface scales out from its left edge (no layout per frame).
    return {
      transform: [{ scaleX: (avatarBox + (width - avatarBox) * e) / width }],
      opacity: grow ? Math.min(1, k * 4) : 1,
      backgroundColor: REPLICA ? COLORS.card : interpolateColor(k, [0, 0.6, 1], [mood.light, mood.light, COLORS.card]), // the new card grows in the mood's tint
    };
  });
  const content = useAnimatedStyle(() => ({ opacity: grow ? Math.max(0, (grow.value - 0.45) / 0.55) : 1 }));
  const avatar = useAnimatedStyle(() => ({ opacity: grow ? (grow.value > 0 ? 1 : 0) : 1 }));
  const style = useAnimatedStyle(() => ({ transform: [{ translateX: swipe ? swipe.value : 0 }] }));
  const under = useAnimatedStyle(() => ({ opacity: swipe ? Math.min(1, -swipe.value / 60) : 0 }));
  return (
    <Animated.View entering={grow ? undefined : FadeInDown.duration(260)} exiting={FadeOut.duration(160)} layout={LinearTransition.duration(220)} style={styles.entryWrap}>
      <Animated.View style={[styles.under, under]}>
        <Text style={styles.underText}>{COPY.undo}</Text>
      </Animated.View>
      <Animated.View
        style={[styles.entry, styles.rEntry, style]}
        accessible
        accessibilityLabel={`${mood.name}, ${entry.tags}, ${entry.time}`}
        accessibilityActions={entry.fresh ? [{ name: 'delete', label: COPY.undo }] : undefined}
        onAccessibilityAction={entry.fresh ? onUndo : undefined}
      >
        <Animated.View style={[styles.entryBg, bg]} />
        <Animated.Image source={ORB_ART[entry.mood]} style={[styles.entryOrb, styles.rEntryOrb, avatar]} />
        {/* The source's card: name, tags and time on three lines. */}
        <Animated.View style={[styles.entryText, styles.rEntryText, content]}>
          <Text style={[styles.entryTitle, styles.rEntryTitle]}>{mood.name}</Text>
          <Text style={[styles.entryTags, styles.rEntryLine]} numberOfLines={1}>{entry.tags}</Text>
          <Text style={[styles.entryTags, styles.rEntryLine]}>{entry.time}</Text>
        </Animated.View>
        <Animated.View style={[styles.dots, styles.rDots, content]}>
          {[0, 1, 2].map((d) => <View key={d} style={[styles.dot, styles.rDot]} />)}
        </Animated.View>
      </Animated.View>
    </Animated.View>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: COLORS.bg },
  header: { position: 'absolute', left: 8, right: 8, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  headerRight: { flexDirection: 'row' },
  icon: { width: LAYOUT.icon, height: LAYOUT.icon, alignItems: 'center', justifyContent: 'center' },
  chevron: { width: 12, height: 12, borderLeftWidth: 2.2, borderBottomWidth: 2.2, borderColor: COLORS.ink, transform: [{ rotate: '45deg' }], marginLeft: 4 },
  calendar: { width: 22, height: 22, borderRadius: 5, borderWidth: 2, borderColor: COLORS.ink, alignItems: 'center', justifyContent: 'center' },
  calendarText: { fontSize: 10, fontWeight: '700', color: COLORS.ink, marginTop: 1 },
  more: { fontSize: 13, letterSpacing: 1, color: COLORS.ink, fontWeight: '700' },
  slot: { position: 'absolute', height: 72, justifyContent: 'flex-end', alignItems: 'center' },
  slotLabel: { fontSize: LAYOUT.labelSize, color: COLORS.label },
  list: { position: 'absolute' },
  label: { position: 'absolute', left: 0, right: 0, alignItems: 'center' },
  labelMonth: { fontSize: 11, letterSpacing: 2.2, fontWeight: '700', color: COLORS.mute },
  countBox: { height: 30, marginTop: SPACE.xs, overflow: 'hidden', alignItems: 'center' },
  count: { fontSize: 24, lineHeight: 30, fontWeight: '800', color: COLORS.ink, fontVariant: ['tabular-nums'] },
  entryWrap: { marginBottom: LAYOUT.entryGap, height: LAYOUT.entryHeight },
  under: {
    position: 'absolute', left: 0, right: 0, top: 0, bottom: 0, borderRadius: LAYOUT.cardRadius, backgroundColor: COLORS.danger, alignItems: 'flex-end',
    justifyContent: 'center', paddingRight: 24,
  },
  underText: { color: COLORS.dangerInk, fontWeight: '600', fontSize: 15 },
  entry: {
    flex: 1, borderRadius: LAYOUT.cardRadius, flexDirection: 'row', alignItems: 'center', paddingHorizontal: 16,
  },
  entryBg: { position: 'absolute', left: 0, right: 0, top: 0, bottom: 0, borderRadius: LAYOUT.cardRadius, backgroundColor: COLORS.card, transformOrigin: 'left' },
  entryOrb: { width: LAYOUT.entryOrb * 2, height: LAYOUT.entryOrb * 2, borderRadius: LAYOUT.entryOrb },
  entryText: { marginLeft: 14, flex: 1 },
  entryTitle: { color: COLORS.ink, fontSize: 16, fontWeight: '600', letterSpacing: -0.2 },
  entryTags: { color: COLORS.mute, fontSize: 13, marginTop: 3 },
  dots: {
    position: 'absolute', right: 12, top: 12, width: 28, height: 24, borderRadius: 8, backgroundColor: COLORS.dotsBg,
    flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: 2.5,
  },
  dot: { width: 3.5, height: 3.5, borderRadius: 1.75, backgroundColor: COLORS.dotsInk },
  // Replica: the source's entry card, measured on the reference frame.
  rEntry: { alignItems: 'flex-start', paddingHorizontal: SPACE.cardPad },
  rEntryOrb: { marginTop: SPACE.avatarTop },
  rEntryText: { marginLeft: 16.3, marginTop: 27.4 },
  rEntryTitle: { fontWeight: '700', lineHeight: 20 },
  rEntryLine: { fontSize: 14, lineHeight: 18, marginTop: 4.1, color: COLORS.secondary },
  rDots: {
    right: 11.3, top: 14.3, width: 27, height: 27, backgroundColor: '#FFFFFF', borderWidth: StyleSheet.hairlineWidth * 2,
    borderColor: 'rgba(22, 35, 58, 0.1)', gap: 3,
  },
  rDot: { width: 3, height: 3, borderRadius: 1.5, backgroundColor: '#2A2A2E' },
});
