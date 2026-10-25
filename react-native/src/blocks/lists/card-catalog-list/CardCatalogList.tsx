import { Canvas, Picture } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { LinearGradient } from 'expo-linear-gradient';
import { memo, useMemo, useState } from 'react';
import { type LayoutChangeEvent, Platform, Pressable, StyleSheet, Text, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  cancelAnimation,
  Easing,
  type SharedValue,
  useAnimatedReaction,
  useAnimatedStyle,
  useFrameCallback,
  useReducedMotion,
  useSharedValue,
  withSpring,
  withTiming,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

import { CARD_H, CARD_W, CARD_X, DESIGN_H, DESIGN_W, DRAWER, FRONT, PIVOT_Y, recordBackdrop, recordFront } from './cardCatalogArt';
import { CARDS, type CatalogCard, STATUS_LABEL } from './cardCatalogData';
import { autoPosition, cardPose, clamp01, LOOP, PT_PER_CARD, rubberBand, TARGET } from './cardCatalogMotion';

type Props = {
  /** Thumbs through the drawer to Gaskell and back, every LOOP seconds. */
  autoPlay?: boolean;
};

const TYPE = Platform.select({ ios: 'Courier New', default: 'monospace' });
const SERIF = Platform.select({ ios: 'Georgia', default: 'serif' });
const PAPER = '#F3EBD8';
const INK = '#2A2730';
const CREAM = '#F3E8D4';
const MUTED = 'rgba(243,232,212,0.62)';
const AMBER = '#E4B36A';

/** Seconds after the last touch before the drawer eases back to the first card. */
const IDLE = 2;
/** How long that return takes at most, in ms. */
const RETURN_MS = 850;
/** Line height of the rolling labels under the drawer. */
const ROLL_H = 24;
const DIGIT_H = 20;
const LAST = CARDS.length - 1;
/** The card's perspective: far enough that a card lying forward does not leave the screen. */
const PERSPECTIVE = 1600;

type Drawer = {
  owned: SharedValue<number>;
  touching: SharedValue<number>;
  idle: SharedValue<number>;
  returning: SharedValue<number>;
  held: SharedValue<number>;
  s: SharedValue<number>;
};

/** A finger lands: the drawer stops playing (or returning) and stays where it is, held by the finger. */
function takeOver(m: Drawer) {
  'worklet';
  m.touching.set(1);
  m.idle.set(0);
  if (m.owned.get() === 0 || m.returning.get() === 1) {
    cancelAnimation(m.held);
    m.held.set(m.s.get());
  }
  m.returning.set(0);
  m.owned.set(1);
}

/** The finger lifts: the idle timer starts over. */
function letGo(m: Drawer) {
  'worklet';
  m.touching.set(0);
  m.idle.set(0);
}

const tick = () => {
  if (process.env.EXPO_OS !== 'web') Haptics.selectionAsync().catch(() => {});
};

export function CardCatalogList({ autoPlay = false }: Props) {
  const insets = useSafeAreaInsets();
  const reduced = useReducedMotion();
  const auto = autoPlay && !reduced;
  const [size, setSize] = useState<{ w: number; h: number } | null>(null);

  // Fit the 393 × 759 design between the safe areas; larger screens centre it.
  const room = size ? { w: size.w, h: size.h - insets.top - insets.bottom } : null;
  const k = room ? Math.min(room.w / DESIGN_W, room.h / DESIGN_H, 1.15) : 1;
  const ox = room ? (room.w - DESIGN_W * k) / 2 : 0;
  const oy = room ? insets.top + Math.max(0, (room.h - DESIGN_H * k) / 2) : 0;

  const backdrop = useMemo(() => (size ? recordBackdrop(size.w, size.h, ox, oy, k) : null), [size, ox, oy, k]);
  const front = useMemo(() => recordFront(), []);

  // One clock drives the demo; a touch hands the drawer to `held`, which the finger and its springs move.
  const rest = reduced ? TARGET : 0;
  const t = useSharedValue(0);
  const owned = useSharedValue(0);
  const touching = useSharedValue(0);
  const idle = useSharedValue(0);
  const returning = useSharedValue(0);
  const held = useSharedValue(rest);
  const start = useSharedValue(0);
  const s = useSharedValue(rest);
  const lean = useSharedValue(0);

  useFrameCallback((frame) => {
    const dt = Math.min((frame.timeSincePreviousFrame ?? 16) / 1000, 1 / 20);
    const before = s.get();
    let now = before;
    if (owned.get() === 0) {
      if (auto) t.set((t.get() + dt) % LOOP);
      now = auto ? autoPosition(t.get()) : rest;
    } else {
      now = held.get();
      if (touching.get() === 0 && returning.get() === 0) {
        idle.set(idle.get() + dt);
        if (idle.get() >= IDLE) {
          returning.set(1);
          const far = Math.abs(now - rest);
          held.set(
            withTiming(rest, { duration: Math.min(RETURN_MS, 300 + far * 40), easing: Easing.bezier(0.65, 0, 0.35, 1) }, (done) => {
              if (!done) return;
              returning.set(0);
              owned.set(0);
              t.set(0);
            }),
          );
        }
      }
    }
    s.set(now);
    // Fast thumbing fans the standing cards back; the lean follows the speed with a short lag.
    const speed = Math.abs(now - before) / Math.max(dt, 1e-3);
    const target = Math.min(speed * 1.4, 12);
    lean.set(lean.get() + (target - lean.get()) * Math.min(1, dt * 9));
  });

  useAnimatedReaction(
    () => Math.round(s.get()),
    (now, before) => {
      if (before !== null && now !== before && owned.get() === 1) scheduleOnRN(tick);
    },
  );

  const m: Drawer = { owned, touching, idle, returning, held, s };

  const gesture = useMemo(() => {
    const pan = Gesture.Pan()
      .activeOffsetY([-6, 6])
      .onBegin(() => {
        takeOver(m);
        cancelAnimation(held);
        start.set(held.get());
      })
      .onUpdate((e) => {
        // Pulling down tips the front card toward you, so the drawer moves on.
        held.set(rubberBand(start.get() + e.translationY / (PT_PER_CARD * k), LAST));
      })
      .onEnd((e) => {
        const v = e.velocityY / (PT_PER_CARD * k);
        const aim = Math.min(LAST, Math.max(0, Math.round(held.get() + v * 0.18)));
        held.set(withSpring(aim, { velocity: v, damping: 22, stiffness: 170, mass: 1 }));
      })
      .onFinalize(() => {
        letGo(m);
      });
    // A tap on the drawer tips one card forward.
    const tap = Gesture.Tap()
      .maxDistance(8)
      .onBegin(() => {
        takeOver(m);
      })
      .onEnd(() => {
        const aim = Math.min(LAST, Math.round(held.get()) + 1);
        held.set(withSpring(aim, { damping: 22, stiffness: 170, mass: 1 }));
      })
      .onFinalize(() => {
        letGo(m);
      });
    return Gesture.Race(pan, tap);
    // The shared values are stable; only the scale changes what a point of drag means.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [k]);

  const onLayout = (e: LayoutChangeEvent) => {
    const { width, height } = e.nativeEvent.layout;
    if (!size || size.w !== width || size.h !== height) setSize({ w: width, h: height });
  };

  const current = CARDS[TARGET];

  return (
    <View style={styles.root} onLayout={onLayout}>
      {backdrop ? (
        <Canvas style={StyleSheet.absoluteFill} pointerEvents="none">
          <Picture picture={backdrop} />
        </Canvas>
      ) : null}
      {size ? (
        <View style={[styles.design, { left: ox, top: oy, transform: [{ scale: k }] }]}>
          <View style={styles.header}>
            <Text style={styles.kicker}>ELM STREET LIBRARY</Text>
            <View style={styles.titleRow}>
              <Text style={styles.title}>Fiction, F–G</Text>
              <View style={styles.counter} accessibilityLabel={`Card of ${CARDS.length}`}>
                <Text style={styles.counterLabel}>CARD</Text>
                <DigitRoll value={s} place={10} />
                <DigitRoll value={s} place={1} />
                <Text style={styles.counterOf}>/ {CARDS.length}</Text>
              </View>
            </View>
            <Text style={styles.sub}>Drawer 14 · Card catalogue, ground floor</Text>
          </View>

          <GestureDetector gesture={gesture}>
            <View
              style={styles.drawerHit}
              accessible
              accessibilityRole="adjustable"
              accessibilityLabel="Catalogue drawer. Drag down to thumb forward, up to go back, or tap to tip one card."
            >
              {CARDS.map((card, i) => (
                <CatalogCardView key={card.surname} card={card} index={i} s={s} lean={lean} />
              ))}
            </View>
          </GestureDetector>

          <Canvas style={styles.front} pointerEvents="none">
            <Picture picture={front} />
          </Canvas>
          <View style={styles.label} pointerEvents="none">
            <Text style={styles.labelMain}>FICTION · F – G</Text>
            <Text style={styles.labelSub}>DRAWER 14</Text>
          </View>

          <View style={styles.panel}>
            <View style={styles.panelText}>
              <Roll s={s} height={ROLL_H} lines={TITLES} textStyle={styles.panelTitle} />
              <Roll s={s} height={20} lines={BYLINES} textStyle={styles.panelBy} />
            </View>
            <View style={styles.panelSide}>
              <Roll s={s} height={20} lines={CALLS} textStyle={styles.panelCall} />
              <Roll s={s} height={18} lines={STATUSES} textStyle={styles.panelStatus} />
            </View>
          </View>

          <Pressable
            accessibilityRole="button"
            accessibilityLabel={`Place a hold on the card at the front, for example ${current.title}`}
            style={({ pressed }) => [styles.button, pressed && styles.buttonPressed]}
          >
            <LinearGradient colors={['#F2C98A', '#E0A85C', '#C98A3E']} locations={[0, 0.55, 1]} style={StyleSheet.absoluteFill} />
            <Text style={styles.buttonText}>Place a hold</Text>
          </Pressable>
        </View>
      ) : null}
    </View>
  );
}

const TITLES = CARDS.map((c) => c.title);
const BYLINES = CARDS.map((c) => `${c.given} ${c.surname} · ${c.year}`);
const CALLS = CARDS.map((c) => c.call.replace('\n', ' '));
const STATUSES = CARDS.map((c) => STATUS_LABEL[c.status]);

type CardProps = { card: CatalogCard; index: number; s: SharedValue<number>; lean: SharedValue<number> };

/** One index card. Its pose depends only on how far it is from the reading line. */
const CatalogCardView = memo(function CatalogCardView({ card, index, s, lean }: CardProps) {
  const pose = useAnimatedStyle(() => {
    const p = cardPose(index - s.get(), lean.get());
    return {
      opacity: p.opacity,
      transform: [{ perspective: PERSPECTIVE }, { translateY: p.ty }, { scale: p.scale }, { rotateX: `${p.rot}deg` }],
    };
  });
  const shade = useAnimatedStyle(() => ({ opacity: cardPose(index - s.get(), lean.get()).shade }));
  const back = useAnimatedStyle(() => ({ opacity: cardPose(index - s.get(), lean.get()).back }));
  const tabLeft = 14 + card.tab * 106;
  return (
    <Animated.View style={[styles.card, { zIndex: CARDS.length - index }, pose]} pointerEvents="none">
      <View style={[styles.tab, { left: tabLeft }]}>
        <Text style={styles.tabText}>{card.surname.slice(0, 2)}</Text>
      </View>
      <View style={styles.cardBody}>
        <LinearGradient colors={['#F7F0DF', PAPER, '#E8DCC0']} locations={[0, 0.5, 1]} style={StyleSheet.absoluteFill} />
        <View style={styles.redRule} />
        {RULES.map((y) => (
          <View key={y} style={[styles.blueRule, { top: y }]} />
        ))}
        <Text style={styles.call}>{card.call}</Text>
        <Text style={styles.author} numberOfLines={1}>
          {card.surname}, {card.given}.
        </Text>
        <Text style={styles.cardTitle} numberOfLines={2}>
          {card.title} / {card.given} {card.surname}.
        </Text>
        <Text style={styles.imprint} numberOfLines={1}>
          {card.publisher}, {card.year}.
        </Text>
        <Text style={styles.imprint2} numberOfLines={1}>
          {card.pages} p. ; 21 cm.
        </Text>
        <Text style={styles.accession}>ESL {String(4100 + index * 37).padStart(5, '0')}</Text>
        <View style={styles.rodHole} />
      </View>
      {/* The card's back, seen once it has tipped past upright, and the shade of the cards behind. */}
      <Animated.View style={[styles.backFace, back]}>
        <View style={[styles.tab, styles.backTab, { left: tabLeft }]} />
        <View style={[styles.cardBody, styles.backBody]}>
          {RULES.map((y) => (
            <View key={y} style={[styles.blueRule, styles.backRule, { top: y }]} />
          ))}
          <View style={styles.rodHole} />
        </View>
      </Animated.View>
      <Animated.View style={[styles.shadeBox, shade]}>
        <View style={[styles.tab, styles.shadeTab, { left: tabLeft }]} />
        <View style={[styles.cardBody, styles.shadeBody]} />
      </Animated.View>
    </Animated.View>
  );
});

const RULES = [86, 106, 126, 146, 166];

type RollProps = { s: SharedValue<number>; height: number; lines: string[]; textStyle: object };

/** A column of labels in a clipped box, rolled to the card at the front (fractional while thumbing). */
function Roll({ s, height, lines, textStyle }: RollProps) {
  const style = useAnimatedStyle(() => {
    const at = Math.min(Math.max(s.get(), -0.4), lines.length - 0.6);
    return { transform: [{ translateY: -at * height }] };
  });
  return (
    <View style={{ height, overflow: 'hidden' }}>
      <Animated.View style={style}>
        {lines.map((line, i) => (
          <Text key={i} style={[textStyle, { height, lineHeight: height }]} numberOfLines={1}>
            {line}
          </Text>
        ))}
      </Animated.View>
    </View>
  );
}

const DIGITS = ['0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '0'];

/** One digit of the card number, rolling like a counter wheel; it rests on whole digits when the drawer holds. */
function DigitRoll({ value, place }: { value: SharedValue<number>; place: 1 | 10 }) {
  const style = useAnimatedStyle(() => {
    const v = Math.max(1, value.get() + 1);
    const ones = v % 10;
    const at = place === 1 ? ones : Math.floor(v / 10) + clamp01(ones - 9);
    return { transform: [{ translateY: -at * DIGIT_H }] };
  });
  return (
    <View style={styles.digitBox}>
      <Animated.View style={style}>
        {DIGITS.map((d, i) => (
          <Text key={i} style={styles.digit}>
            {d}
          </Text>
        ))}
      </Animated.View>
    </View>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: '#1C130D', overflow: 'hidden' },
  design: { position: 'absolute', width: DESIGN_W, height: DESIGN_H, transformOrigin: 'top left' },
  header: { position: 'absolute', left: 24, right: 24, top: 8, gap: 4 },
  kicker: { color: AMBER, fontSize: 12, fontWeight: '700', letterSpacing: 2 },
  titleRow: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  title: { color: CREAM, fontSize: 32, fontWeight: '800', letterSpacing: -0.6 },
  sub: { color: MUTED, fontSize: 14, fontWeight: '500' },
  counter: {
    flexDirection: 'row',
    alignItems: 'center',
    height: 32,
    paddingHorizontal: 12,
    borderRadius: 16,
    borderCurve: 'continuous',
    backgroundColor: 'rgba(243,232,212,0.08)',
    borderWidth: StyleSheet.hairlineWidth,
    borderColor: 'rgba(243,232,212,0.22)',
  },
  counterLabel: { color: MUTED, fontSize: 10, fontWeight: '700', letterSpacing: 1.4, marginRight: 6 },
  counterOf: { color: MUTED, fontSize: 14, fontWeight: '600', marginLeft: 4, fontVariant: ['tabular-nums'] },
  digitBox: { height: DIGIT_H, width: 9, overflow: 'hidden' },
  digit: { height: DIGIT_H, lineHeight: DIGIT_H, color: CREAM, fontSize: 15, fontWeight: '700', fontVariant: ['tabular-nums'], textAlign: 'center' },
  drawerHit: { position: 'absolute', left: DRAWER.x, top: DRAWER.y, width: DRAWER.w, height: FRONT.y - DRAWER.y + 40 },
  card: {
    position: 'absolute',
    left: CARD_X - DRAWER.x,
    top: PIVOT_Y - CARD_H - DRAWER.y,
    width: CARD_W,
    height: CARD_H,
    transformOrigin: 'center bottom',
  },
  tab: {
    position: 'absolute',
    top: 0,
    width: 92,
    height: 26,
    borderTopLeftRadius: 8,
    borderTopRightRadius: 8,
    backgroundColor: '#EFE4CB',
    alignItems: 'center',
    justifyContent: 'center',
    paddingBottom: 2,
    boxShadow: 'inset 0 1px 0 rgba(255,255,255,0.7), 0 -1px 2px rgba(40,24,10,0.25)',
  },
  tabText: { color: '#6B4A2A', fontSize: 12, fontWeight: '800', fontFamily: TYPE, letterSpacing: 1 },
  cardBody: {
    position: 'absolute',
    left: 0,
    right: 0,
    top: 22,
    bottom: 0,
    borderRadius: 6,
    borderCurve: 'continuous',
    overflow: 'hidden',
    backgroundColor: PAPER,
    boxShadow: '0 1px 2px rgba(20,10,4,0.35), 0 10px 24px rgba(10,5,2,0.28), inset 0 1px 0 rgba(255,255,255,0.75)',
  },
  redRule: { position: 'absolute', left: 0, right: 0, top: 44, height: 1.5, backgroundColor: 'rgba(196,64,58,0.7)' },
  blueRule: { position: 'absolute', left: 0, right: 0, height: StyleSheet.hairlineWidth * 2, backgroundColor: 'rgba(80,120,180,0.28)' },
  call: { position: 'absolute', left: 14, top: 10, color: INK, fontFamily: TYPE, fontSize: 13, lineHeight: 15, fontWeight: '700' },
  author: { position: 'absolute', left: 70, right: 14, top: 18, color: INK, fontFamily: TYPE, fontSize: 14, fontWeight: '700' },
  cardTitle: { position: 'absolute', left: 86, right: 14, top: 54, color: INK, fontFamily: TYPE, fontSize: 13, lineHeight: 20 },
  imprint: { position: 'absolute', left: 86, right: 14, top: 114, color: INK, fontFamily: TYPE, fontSize: 12, lineHeight: 20, opacity: 0.85 },
  imprint2: { position: 'absolute', left: 86, right: 14, top: 134, color: INK, fontFamily: TYPE, fontSize: 12, lineHeight: 20, opacity: 0.85 },
  accession: {
    position: 'absolute',
    right: 14,
    bottom: 12,
    color: 'rgba(98,62,140,0.78)',
    fontFamily: TYPE,
    fontSize: 11,
    fontWeight: '700',
    letterSpacing: 1,
    transform: [{ rotate: '-4deg' }],
  },
  rodHole: {
    position: 'absolute',
    bottom: 12,
    left: CARD_W / 2 - 7,
    width: 14,
    height: 14,
    borderRadius: 7,
    backgroundColor: '#2B1C12',
    boxShadow: 'inset 0 2px 3px rgba(0,0,0,0.7), 0 1px 0 rgba(255,255,255,0.6)',
  },
  backFace: { position: 'absolute', left: 0, right: 0, top: 0, bottom: 0 },
  backTab: { backgroundColor: '#E4D7BA' },
  backBody: { backgroundColor: '#E6D9BC' },
  backRule: { backgroundColor: 'rgba(120,90,60,0.12)' },
  shadeBox: { position: 'absolute', left: 0, right: 0, top: 0, bottom: 0 },
  shadeTab: { backgroundColor: '#0C0704', boxShadow: 'none' },
  shadeBody: { backgroundColor: '#0C0704', boxShadow: 'none' },
  front: { position: 'absolute', left: FRONT.x, top: FRONT.y, width: FRONT.w + 12, height: FRONT.h + 24 },
  label: { position: 'absolute', left: FRONT.x + FRONT.w / 2 - 80, top: FRONT.y + 24, width: 160, height: 34, alignItems: 'center', justifyContent: 'center' },
  labelMain: { color: '#3A2614', fontFamily: SERIF, fontSize: 15, fontWeight: '700', letterSpacing: 1.2 },
  labelSub: { color: 'rgba(58,38,20,0.7)', fontSize: 8, fontWeight: '700', letterSpacing: 1.6 },
  panel: {
    position: 'absolute',
    left: 16,
    right: 16,
    top: 590,
    height: 96,
    borderRadius: 20,
    borderCurve: 'continuous',
    paddingHorizontal: 18,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 12,
    backgroundColor: 'rgba(24,15,9,0.82)',
    borderWidth: StyleSheet.hairlineWidth,
    borderColor: 'rgba(243,232,212,0.16)',
    boxShadow: '0 1px 2px rgba(0,0,0,0.4), 0 12px 30px rgba(0,0,0,0.35), inset 0 1px 0 rgba(255,236,200,0.08)',
  },
  panelText: { flex: 1, gap: 4 },
  panelTitle: { color: CREAM, fontSize: 18, fontWeight: '700' },
  panelBy: { color: MUTED, fontSize: 14, fontWeight: '500', fontVariant: ['tabular-nums'] },
  panelSide: { width: 104, alignItems: 'flex-end', gap: 6 },
  panelCall: { color: AMBER, fontFamily: TYPE, fontSize: 15, fontWeight: '700', textAlign: 'right', width: 104 },
  panelStatus: { color: 'rgba(170,210,160,0.9)', fontSize: 12, fontWeight: '600', textAlign: 'right', width: 104 },
  button: {
    position: 'absolute',
    left: 16,
    right: 16,
    top: 702,
    height: 52,
    borderRadius: 26,
    overflow: 'hidden',
    alignItems: 'center',
    justifyContent: 'center',
    boxShadow: '0 1px 2px rgba(0,0,0,0.4), 0 10px 22px rgba(201,138,62,0.28), inset 0 1px 0 rgba(255,245,220,0.6)',
  },
  buttonPressed: { transform: [{ scale: 0.97 }] },
  buttonText: { color: '#2A1808', fontSize: 17, fontWeight: '700' },
});
