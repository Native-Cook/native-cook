/**
 * The row's bar and its shares. Pulled right, the line lifts as one strip of heavier paper and breaks into equal
 * shares, each with the person's monogram, name and share; at the first break yours turns to ink. Every position
 * comes from split-geometry, driven by the row's shared values, so nothing renders in React while the finger moves.
 */
import { StyleSheet, Text, View } from 'react-native';
import Animated, {
  interpolateColor, useAnimatedStyle, useDerivedValue, useSharedValue, withTiming, type DerivedValue, type SharedValue,
} from 'react-native-reanimated';

import { Monogram } from './Monogram';
import { SPLIT } from './motion';
import { avatarIn, BAR, compactness, MONO, pieceFrame, seam, structure } from './split-geometry';
import { COLOR, DIDONE_ROOM, LAYOUT, SHADOW, TYPE } from './theme';
import { MAX_PEOPLE, money, PEOPLE, shares } from './transactions';

/** The row's motion state, shared by the row, its bar, its HUD and its actions. */
export type RowMotion = {
  pull: SharedValue<number>; // right pull, pt (finger travel)
  tx: SharedValue<number>; // left swipe: the row's offset (≤ 0)
  lift: SharedValue<number>; // 0–1, the held row
  piecesOn: DerivedValue<number>; // 0–1, the shares' content and the bar's full strength
  veil: DerivedValue<number>; // 0–1, the row's own content hidden
  hud: SharedValue<number>; // 0–1, the HUD (arrives with the first break)
  count: SharedValue<number>; // spring toward target
  target: SharedValue<number>;
  prevTarget: SharedValue<number>;
  dir: SharedValue<number>;
  rollP: SharedValue<number>; // 0 → 1 each time the target changes: the shares roll in
  trail: SharedValue<number>;
  close: SharedValue<number>; // 0–1, the seams heal (commit)
  contentOut: SharedValue<number>; // 0–1, the shares' content fades as they heal
};

export type Flight = { key: SharedValue<string>; on: SharedValue<number> };

function clamp01(v: number): number {
  'worklet';
  return Math.min(Math.max(v, 0), 1);
}

function smooth(v: number): number {
  'worklet';
  const k = clamp01(v);
  return k * k * (3 - 2 * k);
}

/** Piece k now: the rest seam stretched by the pull and closed by the heal, plus the count spring's snap. */
export function frameOf(m: RowMotion, k: number) {
  'worklet';
  const { cs, snap } = structure(m.count.get(), m.target.get(), m.dir.get());
  const gap = (seam(m.pull.get(), m.target.get()) + snap * SPLIT.snap) * (1 - m.close.get());
  return { ...pieceFrame(k, cs, gap, m.trail.get()), cs };
}

/** Your share turns to ink at the first break and back to paper as the shares heal: a quick change, not a long grey. */
function useInk(m: RowMotion) {
  return useDerivedValue(() => withTiming(m.target.get() >= 2 && m.close.get() < 0.5 ? 1 : 0, { duration: SPLIT.ink }));
}

/** A share's corners: 14 on the bar's ends, 4 on the broken faces (0 once healed). */
function corners(k: number, last: boolean, close: number) {
  'worklet';
  const broken = LAYOUT.broken * (1 - close);
  return {
    borderTopLeftRadius: k === 0 ? LAYOUT.outer : broken,
    borderBottomLeftRadius: k === 0 ? LAYOUT.outer : broken,
    borderTopRightRadius: last ? LAYOUT.outer : broken,
    borderBottomRightRadius: last ? LAYOUT.outer : broken,
  };
}

type BarProps = { m: RowMotion; amount: number; rowKey: string; flight: Flight };

export function SplitBar({ m, amount, rowKey, flight }: BarProps) {
  // The strip appears with the pull (a left swipe never shows it) and stays while the shares are held.
  const bar = useAnimatedStyle(() => ({
    opacity: m.lift.get() * Math.max(clamp01(m.pull.get() / SPLIT.barIn), m.piecesOn.get()),
  }));
  // Healing: the shares hand over to one whole strip, so no seam or doubled shadow is left where they met.
  const pieces = useAnimatedStyle(() => ({ opacity: 1 - smooth((m.close.get() - 0.4) / 0.35) }));
  const whole = useAnimatedStyle(() => ({ opacity: smooth((m.close.get() - 0.3) / 0.6) }));
  return (
    <Animated.View style={[styles.bar, bar]} pointerEvents="none">
      <Animated.View style={[styles.whole, whole]} />
      <Animated.View style={[StyleSheet.absoluteFill, pieces]}>
        {PEOPLE.map((person, k) => (
          <Piece key={person.name} k={k} m={m} amount={amount} rowKey={rowKey} flight={flight} />
        ))}
      </Animated.View>
    </Animated.View>
  );
}

function Piece({ k, m, amount, rowKey, flight }: { k: number; m: RowMotion; amount: number; rowKey: string; flight: Flight }) {
  const person = PEOPLE[k];
  const inked = useInk(m);
  const outer = useAnimatedStyle(() => {
    const p = frameOf(m, k);
    const ink = k === 0 ? inked.get() : 0;
    return {
      width: p.w,
      opacity: p.vis > 0 ? Math.min(1, p.vis * 1.5) : 0,
      backgroundColor: interpolateColor(ink, [0, 1], [COLOR.stock, COLOR.ink]),
      borderColor: interpolateColor(ink, [0, 1], [COLOR.pieceEdge, COLOR.ink]),
      transform: [{ translateX: p.x }],
      ...corners(k, p.last, m.close.get()),
    };
  });
  // A share that is folding away takes its text with it before its edge reaches the text (no clipped digits).
  const content = useAnimatedStyle(() => {
    const vis = frameOf(m, k).vis;
    return { opacity: m.piecesOn.get() * (1 - m.contentOut.get()) * vis * vis * vis };
  });
  const paperSide = useAnimatedStyle(() => ({ opacity: k === 0 ? 1 - inked.get() : 1 }));
  const inkSide = useAnimatedStyle(() => ({ opacity: k === 0 ? inked.get() : 0 }));
  const avatar = useAnimatedStyle(() => {
    const p = frameOf(m, k);
    const a = avatarIn(p.w, compactness(p.cs));
    // The people who are asked to pay leave with the flight; your own monogram fades with the content.
    const away = k > 0 ? (flight.key.get() === rowKey ? flight.on.get() : 0) : m.contentOut.get();
    const born = SPLIT.avatarFrom + (1 - SPLIT.avatarFrom) * p.vis;
    return {
      opacity: m.piecesOn.get() * p.vis * (1 - away),
      transform: [{ translateX: a.x - MONO / 2 }, { translateY: a.y - MONO / 2 }, { scale: a.s * born }],
    };
  });
  const name = useAnimatedStyle(() => ({ opacity: 1 - compactness(frameOf(m, k).cs) }));
  const layers: number[] = [];
  for (let n = Math.max(k + 1, 1); n <= MAX_PEOPLE; n++) layers.push(n);
  const side = (inked: boolean) => (
    <>
      <Animated.Text style={[styles.name, { color: inked ? COLOR.brass : COLOR.brassPaper }, name]} numberOfLines={1}>
        {person.name.toUpperCase()}
      </Animated.Text>
      {layers.map((n) => <Share key={n} k={k} n={n} amount={amount} m={m} inked={inked} />)}
    </>
  );
  const clip = useAnimatedStyle(() => corners(k, frameOf(m, k).last, m.close.get()));
  return (
    <Animated.View style={[styles.piece, outer]}>
      <Animated.View style={[styles.clip, clip]}>
      <Animated.View style={[styles.avatar, avatar]}>
        <Animated.View style={[StyleSheet.absoluteFill, paperSide]}>
          <Monogram person={person} />
        </Animated.View>
        {k === 0 && (
          <Animated.View style={[StyleSheet.absoluteFill, inkSide]}>
            <Monogram person={person} fill={COLOR.brass} ink={COLOR.ink} />
          </Animated.View>
        )}
      </Animated.View>
      <Animated.View style={[StyleSheet.absoluteFill, content]}>
        <Animated.View style={[StyleSheet.absoluteFill, paperSide]}>{side(false)}</Animated.View>
        {k === 0 && <Animated.View style={[StyleSheet.absoluteFill, inkSide]}>{side(true)}</Animated.View>}
      </Animated.View>
      </Animated.View>
    </Animated.View>
  );
}

/** Piece k's share when the bill is split n ways: it rolls in when n becomes the target, out when it stops being it. */
function Share({ k, n, amount, m, inked }: { k: number; n: number; amount: number; m: RowMotion; inked: boolean }) {
  const value = money(shares(amount, n)[k], false);
  const compact = n >= 4;
  const style = useAnimatedStyle(() => {
    const t = m.target.get();
    const prev = m.prevTarget.get();
    const p = m.rollP.get();
    // The old share is gone before the new one is half in: a frame never shows two legible amounts.
    const on = t === n ? clamp01((p - 0.35) / 0.65) : prev === n ? 1 - clamp01(p / 0.45) : 0;
    const y = t === n ? (1 - p) * SPLIT.rollRise : prev === n ? -p * SPLIT.rollRise : 0;
    if (!compact) return { opacity: on, transform: [{ translateX: 0 }, { translateY: y }] };
    const w = frameOf(m, k).w;
    return { opacity: on, transform: [{ translateX: w / 2 - SHARE_BOX / 2 }, { translateY: y }] };
  });
  return (
    <Animated.View style={[compact ? styles.shareCompact : styles.share, style]}>
      <Text style={[TYPE.pieceAmount, styles.pieceRoom, { color: inked ? COLOR.ivory : COLOR.ink }]} numberOfLines={1}>
        {value.whole}
        <Text style={[TYPE.pieceCents, { color: inked ? COLOR.ivory60 : COLOR.ink2 }]}>{value.cents}</Text>
      </Text>
    </Animated.View>
  );
}

const SHARE_BOX = 80;

type HudProps = { m: RowMotion; amount: number; top: number };

/** "$96.60 ÷ 2 3 4 5", in ink over the row above: the count the bar is broken into, on a brass disc. */
export function SplitHud({ m, amount, top }: HudProps) {
  const total = money(amount, false);
  const full = useSharedValue(0);
  // The pill extends from its left end, opaque from the first frames: the row it covers never shows through it.
  const box = useAnimatedStyle(() => {
    const h = m.hud.get();
    const w = full.get();
    return { opacity: clamp01(h * 5), width: w > 0 ? LAYOUT.hud + (w - LAYOUT.hud) * h : 0 };
  });
  const pill = useAnimatedStyle(() => {
    const { cs } = structure(m.count.get(), m.target.get(), m.dir.get());
    const c = Math.min(Math.max(cs, 2), 5);
    return { opacity: clamp01(cs - 1), transform: [{ translateX: (c - 2) * (STEP + STEP_GAP) }] };
  });
  return (
    <Animated.View style={[styles.hudClip, { top }, box]} pointerEvents="none">
      <View style={styles.hud} onLayout={(e) => full.set(e.nativeEvent.layout.width)}>
        <Text style={[TYPE.hudSum, styles.hudSum]}>
          {total.whole}
          <Text style={[TYPE.hudCents, styles.hudCents]}>{total.cents}</Text>
        </Text>
        <Text style={[TYPE.hudDivide, styles.hudDivide]}>÷</Text>
        <View style={styles.steps}>
          <Animated.View style={[styles.stepPill, pill]} />
          {[2, 3, 4, 5].map((n) => <Step key={n} n={n} m={m} />)}
        </View>
      </View>
    </Animated.View>
  );
}

function Step({ n, m }: { n: number; m: RowMotion }) {
  const on = useAnimatedStyle(() => {
    const { cs } = structure(m.count.get(), m.target.get(), m.dir.get());
    return { opacity: clamp01(1 - Math.abs(cs - n)) };
  });
  const idle = useAnimatedStyle(() => {
    const { cs } = structure(m.count.get(), m.target.get(), m.dir.get());
    return { opacity: 1 - clamp01(1 - Math.abs(cs - n)) };
  });
  return (
    <View style={styles.step}>
      <Animated.Text style={[TYPE.hudStep, styles.stepIdle, idle]}>{n}</Animated.Text>
      <Animated.Text style={[TYPE.hudStepOn, styles.stepOn, on]}>{n}</Animated.Text>
    </View>
  );
}

const STEP = 28;
const STEP_GAP = 2;

const styles = StyleSheet.create({
  bar: { position: 'absolute', left: 0, top: LAYOUT.inset, width: BAR, height: LAYOUT.bar },
  whole: {
    ...StyleSheet.absoluteFill, borderRadius: LAYOUT.outer, backgroundColor: COLOR.stock,
    borderWidth: 1, borderColor: COLOR.pieceEdge, boxShadow: SHADOW.piece,
  },
  piece: { position: 'absolute', left: 0, top: 0, height: LAYOUT.bar, borderWidth: 1, boxShadow: SHADOW.piece },
  clip: { ...StyleSheet.absoluteFill, overflow: 'hidden' },
  avatar: { position: 'absolute', left: -1, top: -1, width: MONO, height: MONO },
  name: { ...TYPE.pieceName, position: 'absolute', left: 45, top: 13, right: 2 },
  share: { position: 'absolute', left: 45, top: 29 },
  shareCompact: { position: 'absolute', left: -1, top: 37, width: SHARE_BOX, alignItems: 'center' },
  hudClip: {
    position: 'absolute', left: 0, height: LAYOUT.hud, borderRadius: LAYOUT.hud / 2, backgroundColor: COLOR.ink,
    overflow: 'hidden', boxShadow: SHADOW.hud,
  },
  hud: { position: 'absolute', left: 0, top: 0, height: LAYOUT.hud, flexDirection: 'row', alignItems: 'center', paddingLeft: 16, paddingRight: 6 },
  hudSum: { color: COLOR.ivory, marginRight: 8, marginTop: -DIDONE_ROOM.hud },
  pieceRoom: { marginTop: -DIDONE_ROOM.piece },
  hudCents: { color: COLOR.ivory60 },
  hudDivide: { color: COLOR.brass, marginRight: 10 },
  steps: { flexDirection: 'row', gap: STEP_GAP },
  stepPill: { position: 'absolute', left: 0, top: 0, width: STEP, height: STEP, borderRadius: STEP / 2, backgroundColor: COLOR.brass },
  step: { width: STEP, height: STEP, alignItems: 'center', justifyContent: 'center' },
  stepIdle: { color: COLOR.ivory42 },
  stepOn: { color: COLOR.ink, position: 'absolute' },
});
