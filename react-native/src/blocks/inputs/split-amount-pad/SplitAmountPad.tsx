// Split Amount Pad — "Split the bill" in an invented payments app, Kindly.
//
// A custom number pad types an amount whose digits are tiles: each press drops one in on a spring,
// the amount re-centres, a thousands comma pops in, and the four shares under the avatars roll to
// their new values. Backspace flicks the last digit away (held, it clears), a key that would pass
// $2,500 shakes the amount instead, and tapping a friend takes them out of the split.
//
// autoPlay: LOOP = 9 s (SCRIPT in sap-motion.ts). A ghost finger types 1 2 8 . 4 0, the shares settle
// on $32.10, Tobi is tapped out ($42.80 each), backspace is held until the amount clears, Tobi comes
// back, rest. Frame 9 s equals frame 0. A real touch takes over at once and keeps what was typed;
// IDLE seconds after the last touch the amount clears and the loop restarts at t = 0.
// Reduced motion: the finished state ($128.40, $32.10 each) with no loop; keys still work, without
// the springs.
//
// Thread model: the amount, the shares and every key's state are shared values. A key press is a
// gesture worklet that edits the typed codes and writes one table (SapAmount.tsx); nothing is React
// state, so presses as fast as fingers allow never wait for JS. The only JS calls are haptics.

import * as Haptics from 'expo-haptics';
import { LinearGradient } from 'expo-linear-gradient';
import { StatusBar } from 'expo-status-bar';
import { useEffect, useState } from 'react';
import { StyleSheet, Text, useWindowDimensions, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, { type SharedValue, useAnimatedStyle, useFrameCallback, useSharedValue, withDelay, withSequence, withSpring, withTiming } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import Svg, { Path } from 'react-native-svg';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';

import { useReducedMotionPreference } from '../../../shared/useReducedMotionPreference';
import { K, LAY, LEAVING, LIVE, type Metrics, SapAmount, SapMeasure, TILE, amountLayout } from './SapAmount';
import { SapGrain } from './SapGrain';
import { SapFriend } from './SapPeople';
import {
  DO_CLEAR,
  DO_FRIEND,
  DO_KEY,
  FRIENDS,
  HOLD_MS,
  IDLE,
  KEY_BACK,
  KEY_CLEAR,
  KEY_DOT,
  LIMIT_CENTS,
  LOOP,
  MAX_DECIMALS,
  MAX_INT_DIGITS,
  RETURN,
  SCRIPT,
  SHAKE_HARD,
  SHAKE_SOFT,
  SPRING,
  TIME,
  cellOf,
  centsOf,
  dotAt,
  ghostAlpha,
  ghostGlide,
  ghostPress,
  ghostPrev,
  ghostRow,
  splitShares,
} from './sap-motion';
import { C, COPY, DOLLAR_GAP, GUESS, KEY_CODES, KEY_LABELS, POOL, SIDE, layoutFor } from './sap-theme';

export { LOOP };

const MODE_AUTO = 0;
const MODE_TOUCH = 1;
const MODE_RETURN = 2;
const GHOST = 44;
/** what a press came to: typed, ignored, refused at the limit */
const DONE = 1;
const IGNORED = 0;
const REFUSED = -1;

type Props = { autoPlay?: boolean };

function tick() {
  void Haptics.selectionAsync().catch(() => {});
}
function warn() {
  void Haptics.notificationAsync(Haptics.NotificationFeedbackType.Warning).catch(() => {});
}

/** the next pooled tile that is not on screen as a live digit */
function takeTile(rows: number[], cursor: SharedValue<number>) {
  'worklet';
  const from = cursor.get();
  for (let n = 0; n < POOL; n++) {
    const i = (from + n) % POOL;
    if (rows[i * K + TILE.state] !== LIVE) {
      cursor.set(i + 1);
      return i;
    }
  }
  return from % POOL;
}

type KeyProps = {
  cell: number;
  width: number;
  height: number;
  /** worklets */
  onPress: (code: number) => void;
  onRelease: () => void;
  autoKey: SharedValue<number>;
  autoPress: SharedValue<number>;
};

function PadKey({ cell, width, height, onPress, onRelease, autoKey, autoPress }: KeyProps) {
  const code = KEY_CODES[cell];
  const press = useSharedValue(0);
  const tap = Gesture.Tap()
    .maxDuration(100000)
    .onBegin(() => {
      // the digit is committed as the finger lands, not when it lifts
      press.set(withTiming(1, { duration: TIME.keyDown }));
      onPress(code);
    })
    .onFinalize(() => {
      press.set(withTiming(0, { duration: TIME.keyUp }));
      onRelease();
    });
  const hold = Gesture.LongPress()
    .minDuration(HOLD_MS)
    .maxDistance(60)
    .enabled(code === KEY_BACK)
    .onStart(() => {
      onPress(KEY_CLEAR);
    });
  const gesture = Gesture.Simultaneous(tap, hold);

  const face = useAnimatedStyle(() => {
    const p = Math.max(press.get(), autoKey.get() === cell ? autoPress.get() : 0);
    return { transform: [{ translateY: 2 * p }, { scaleX: 1 + 0.012 * p }, { scaleY: 1 - 0.05 * p }] };
  });
  // lifted, the key throws a soft shadow; pressed, only the tight contact shadow is left
  const ambient = useAnimatedStyle(() => {
    const p = Math.max(press.get(), autoKey.get() === cell ? autoPress.get() : 0);
    return { opacity: 1 - 0.8 * p, transform: [{ scale: 1 - 0.03 * p }] };
  });
  const shade = useAnimatedStyle(() => ({ opacity: Math.max(press.get(), autoKey.get() === cell ? autoPress.get() : 0) }));

  return (
    <GestureDetector gesture={gesture}>
      <View style={{ width, height }}>
        <Animated.View style={[styles.keyAmbient, ambient]} />
        <Animated.View style={[styles.keyFace, face]}>
          {code === KEY_BACK ? (
            <Svg width={30} height={30} viewBox="0 0 30 30">
              <Path d="M11.2 7.5h11.3a3 3 0 0 1 3 3v9a3 3 0 0 1-3 3H11.2a3 3 0 0 1-2.3-1.1L4 15l4.9-6.4a3 3 0 0 1 2.3-1.1Z" stroke={C.ink} strokeWidth={1.8} strokeLinejoin="round" fill="none" />
              <Path d="m14 12 5.6 6M19.6 12 14 18" stroke={C.ink} strokeWidth={1.8} strokeLinecap="round" />
            </Svg>
          ) : (
            <Text allowFontScaling={false} style={styles.keyLabel}>
              {KEY_LABELS[cell]}
            </Text>
          )}
          <Animated.View pointerEvents="none" style={[styles.keyShade, shade]} />
        </Animated.View>
      </View>
    </GestureDetector>
  );
}

const CELLS = [0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11];
const SEATS = [0, 1, 2, 3];

export function SplitAmountPad({ autoPlay = true }: Props) {
  const insets = useSafeAreaInsets();
  const { width, height } = useWindowDimensions();
  const reduced = useReducedMotionPreference();
  const auto = autoPlay && !reduced;
  const L = layoutFor(width, height);

  // the amount's glyph widths: a guess until the hidden row has been measured
  const [measured, setMeasured] = useState<Partial<Metrics>>({});
  const metrics: Metrics = {
    digit: measured.digit ?? L.amount * GUESS.digit,
    dot: measured.dot ?? L.amount * GUESS.dot,
    comma: measured.comma ?? L.amount * GUESS.comma,
    dollar: measured.dollar ?? L.amount * GUESS.dollar,
  };
  const onMetrics = (key: keyof Metrics, value: number) => {
    setMeasured((m) => (Math.abs((m[key] ?? 0) - value) < 0.25 ? m : { ...m, [key]: value }));
  };

  /* ------------------------------------------------------------ the amount, on the UI thread */
  const typed = useSharedValue<number[]>([]);
  /** the pooled tile showing each typed code */
  const tiles = useSharedValue<number[]>([]);
  const table = useSharedValue<number[]>(new Array<number>(POOL * K).fill(0));
  const cursor = useSharedValue(0);
  const births = useSharedValue(0);
  const dollarX = useSharedValue(-(metrics.dollar + DOLLAR_GAP + metrics.digit) / 2);
  const commaX = useSharedValue(0);
  const commaOn = useSharedValue(0);
  const empty = useSharedValue(1);
  const shake = useSharedValue(0);
  const limit = useSharedValue(0);
  const cents = useSharedValue(0);
  const active = useSharedValue<number[]>([1, 1, 1, 1]);
  const shares = useSharedValue<number[]>([0, 0, 0, 0]);

  /* ------------------------------------------------------------ the clock and the hand-over */
  const clock = useSharedValue(0);
  const mode = useSharedValue(MODE_AUTO);
  const autoStart = useSharedValue(0);
  const prevT = useSharedValue(-1);
  const lastTouch = useSharedValue(0);
  const returnAt = useSharedValue(0);
  const held = useSharedValue(0);
  const ghostX = useSharedValue(0);
  const ghostY = useSharedValue(0);
  const ghostA = useSharedValue(0);
  const autoKey = useSharedValue(-1);
  const autoFriend = useSharedValue(-1);
  const autoPress = useSharedValue(0);
  /** where the pad and the avatars start, pt from the screen's top */
  const padY = useSharedValue(0);
  const seatsY = useSharedValue(0);

  /** put these codes on screen: tiles of codes that are gone leave, new codes (tile -1) are born, the rest slide */
  const show = (codes: number[], seats: number[]) => {
    'worklet';
    const rows = table.get().slice();
    const before = tiles.get();
    let leaving = 0;
    for (let i = before.length - 1; i >= 0; i--) {
      let kept = false;
      for (let j = 0; j < seats.length; j++) if (seats[j] === before[i]) kept = true;
      if (!kept) {
        rows[before[i] * K + TILE.state] = LEAVING;
        rows[before[i] * K + TILE.order] = leaving;
        leaving += 1;
      }
    }
    const lay = amountLayout(codes, metrics);
    let arriving = 0;
    let born = births.get();
    for (let i = 0; i < codes.length; i++) {
      if (seats[i] < 0) {
        const tile = takeTile(rows, cursor);
        seats[i] = tile;
        born += 1;
        rows[tile * K + TILE.state] = LIVE;
        rows[tile * K + TILE.glyph] = codes[i];
        rows[tile * K + TILE.born] = born;
        rows[tile * K + TILE.order] = arriving;
        arriving += 1;
      }
      rows[seats[i] * K + TILE.centre] = lay[LAY.first + i];
    }
    births.set(born);
    table.set(rows);
    typed.set(codes);
    tiles.set(seats);

    const hasComma = lay[LAY.hasComma];
    if (reduced) {
      dollarX.set(lay[LAY.dollar]);
      commaX.set(lay[LAY.comma]);
      commaOn.set(hasComma);
      empty.set(codes.length === 0 ? 1 : 0);
    } else {
      dollarX.set(withSpring(lay[LAY.dollar], SPRING.slide));
      // a comma that was hidden appears in place; one on screen slides with the digits
      if (hasComma > 0) commaX.set(commaOn.get() < 0.05 ? lay[LAY.comma] : withSpring(lay[LAY.comma], SPRING.slide));
      commaOn.set(hasComma > 0 ? withSpring(1, SPRING.drop) : withTiming(0, { duration: 120 }));
      empty.set(withTiming(codes.length === 0 ? 1 : 0, { duration: codes.length === 0 ? 220 : 90 }));
    }
    const total = centsOf(codes);
    cents.set(total);
    shares.set(splitShares(total, active.get()));
  };

  const refuse = (hard: boolean) => {
    'worklet';
    if (reduced) return;
    const d = TIME.shake;
    if (hard) {
      shake.set(withSequence(withTiming(SHAKE_HARD[0], { duration: d }), withTiming(SHAKE_HARD[1], { duration: d }), withTiming(SHAKE_HARD[2], { duration: d }), withTiming(SHAKE_HARD[3], { duration: d }), withTiming(SHAKE_HARD[4], { duration: d })));
      limit.set(withSequence(withTiming(1, { duration: 120 }), withDelay(900, withTiming(0, { duration: 400 }))));
    } else {
      shake.set(withSequence(withTiming(SHAKE_SOFT[0], { duration: d }), withTiming(SHAKE_SOFT[1], { duration: d }), withTiming(SHAKE_SOFT[2], { duration: d }), withTiming(SHAKE_SOFT[3], { duration: d })));
    }
  };

  /** one key: the amount's rules. Returns DONE, IGNORED or REFUSED (over the limit). */
  const pressKey = (code: number) => {
    'worklet';
    const codes = typed.get();
    const seats = tiles.get();
    if (code === KEY_CLEAR) {
      if (codes.length === 0) return IGNORED;
      show([], []);
      return DONE;
    }
    if (code === KEY_BACK) {
      if (codes.length === 0) {
        refuse(false);
        return IGNORED;
      }
      show(codes.slice(0, codes.length - 1), seats.slice(0, seats.length - 1));
      return DONE;
    }
    const dot = dotAt(codes);
    if (code === KEY_DOT) {
      if (dot >= 0) {
        refuse(false);
        return IGNORED;
      }
      // a dot on an empty amount brings its zero with it
      if (codes.length === 0) show([0, KEY_DOT], [-1, -1]);
      else show(codes.concat([KEY_DOT]), seats.concat([-1]));
      return DONE;
    }
    let nextCodes: number[];
    let nextSeats: number[];
    if (dot < 0 && codes.length === 1 && codes[0] === 0) {
      // a lone zero is replaced, not followed
      if (code === 0) {
        refuse(false);
        return IGNORED;
      }
      nextCodes = [code];
      nextSeats = [-1];
    } else {
      if (dot >= 0 && codes.length - dot - 1 >= MAX_DECIMALS) {
        refuse(false);
        return IGNORED;
      }
      if (dot < 0 && codes.length >= MAX_INT_DIGITS) {
        refuse(true);
        return REFUSED;
      }
      nextCodes = codes.concat([code]);
      nextSeats = seats.concat([-1]);
    }
    if (centsOf(nextCodes) > LIMIT_CENTS) {
      refuse(true);
      return REFUSED;
    }
    show(nextCodes, nextSeats);
    return DONE;
  };

  /** tap a friend in or out of the split; the payer (seat 0) always stays in */
  const toggleFriend = (index: number) => {
    'worklet';
    if (index === 0) return IGNORED;
    const next = active.get().slice();
    next[index] = next[index] > 0 ? 0 : 1;
    active.set(next);
    shares.set(splitShares(cents.get(), next));
    return DONE;
  };

  /** back to the resting screen: nothing typed, everyone in */
  const rest = () => {
    'worklet';
    if (typed.get().length > 0) show([], []);
    let all = true;
    const now = active.get();
    for (let i = 0; i < FRIENDS; i++) if (now[i] === 0) all = false;
    if (!all) {
      active.set([1, 1, 1, 1]);
      shares.set(splitShares(cents.get(), [1, 1, 1, 1]));
    }
  };

  const takeOver = () => {
    'worklet';
    lastTouch.set(clock.get());
    if (mode.get() === MODE_TOUCH) return;
    mode.set(MODE_TOUCH);
    ghostA.set(withTiming(0, { duration: 150 }));
    autoPress.set(0);
    autoKey.set(-1);
    autoFriend.set(-1);
  };
  const userKey = (code: number) => {
    'worklet';
    takeOver();
    held.set(held.get() + (code === KEY_CLEAR ? 0 : 1));
    const result = pressKey(code);
    if (result === DONE) scheduleOnRN(tick);
    else if (result === REFUSED) scheduleOnRN(warn);
  };
  const userFriend = (index: number) => {
    'worklet';
    takeOver();
    held.set(held.get() + 1);
    if (toggleFriend(index) === DONE) scheduleOnRN(tick);
  };
  const userRelease = () => {
    'worklet';
    held.set(Math.max(0, held.get() - 1));
    lastTouch.set(clock.get());
  };

  /** where the ghost finger aims for a script row, pt */
  const aimX = (row: number) => {
    'worklet';
    if (SCRIPT[row][1] === DO_FRIEND) return SIDE + (SCRIPT[row][2] + 0.5) * L.column;
    return SIDE + (cellOf(SCRIPT[row][2]) % 3) * (L.keyW + L.gap) + L.keyW / 2;
  };
  const aimY = (row: number) => {
    'worklet';
    if (SCRIPT[row][1] === DO_FRIEND) return seatsY.get() + L.disc / 2;
    return padY.get() + Math.floor(cellOf(SCRIPT[row][2]) / 3) * (L.keyH + L.gap) + L.keyH / 2;
  };

  const ticker = useFrameCallback((fi) => {
    const dt = Math.min(0.05, (fi.timeSincePreviousFrame ?? 16) / 1000);
    clock.set(clock.get() + dt);
    const now = clock.get();
    if (mode.get() === MODE_AUTO) {
      if (!auto || padY.get() === 0) {
        // not playing, or not laid out yet: the loop waits at t = 0
        ghostA.set(0);
        autoStart.set(now);
        prevT.set(-1);
        return;
      }
      const t = (((now - autoStart.get()) % LOOP) + LOOP) % LOOP;
      if (t < prevT.get()) rest();
      for (let i = 0; i < SCRIPT.length; i++) {
        const at = SCRIPT[i][0];
        if (prevT.get() < at && t >= at) {
          if (SCRIPT[i][1] === DO_KEY) pressKey(SCRIPT[i][2]);
          else if (SCRIPT[i][1] === DO_FRIEND) toggleFriend(SCRIPT[i][2]);
          else if (SCRIPT[i][1] === DO_CLEAR) pressKey(KEY_CLEAR);
        }
      }
      // the ghost finger: glides to its next target, presses on the beat
      const row = ghostRow(t);
      const from = ghostPrev(row);
      const g = ghostGlide(t, row);
      ghostX.set(aimX(from) + (aimX(row) - aimX(from)) * g);
      ghostY.set(aimY(from) + (aimY(row) - aimY(from)) * g);
      ghostA.set(ghostAlpha(t));
      autoPress.set(ghostPress(t, row));
      autoKey.set(SCRIPT[row][1] === DO_KEY ? cellOf(SCRIPT[row][2]) : -1);
      autoFriend.set(SCRIPT[row][1] === DO_FRIEND ? SCRIPT[row][2] : -1);
      prevT.set(t);
    } else if (mode.get() === MODE_TOUCH) {
      if (auto && held.get() === 0 && now - lastTouch.get() >= IDLE) {
        mode.set(MODE_RETURN);
        returnAt.set(now);
        rest();
      }
    } else if (now - returnAt.get() >= RETURN) {
      autoStart.set(now);
      prevT.set(-1);
      mode.set(MODE_AUTO);
    }
  }, false);

  useEffect(() => {
    ticker.setActive(true);
    return () => ticker.setActive(false);
  }, [ticker]);

  // the measured glyph widths arrive a frame after mount: lay the amount out again with them
  const relayout = () => {
    'worklet';
    show(typed.get().slice(), tiles.get().slice());
  };
  // reduced motion: the finished split, standing still
  const showFinal = () => {
    'worklet';
    if (typed.get().length === 0) show([1, 2, 8, KEY_DOT, 4, 0], [-1, -1, -1, -1, -1, -1]);
  };
  const { digit, dot, comma, dollar } = metrics;
  useEffect(() => {
    scheduleOnUI(relayout);
    // `relayout` is rebuilt whenever the metrics change, which is exactly when this must run
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [digit, dot, comma, dollar]);
  useEffect(() => {
    if (reduced) scheduleOnUI(showFinal);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [reduced]);

  const ghost = useAnimatedStyle(() => ({
    opacity: ghostA.get(),
    transform: [{ translateX: ghostX.get() - GHOST / 2 }, { translateY: ghostY.get() - GHOST / 2 }, { scale: 1 - 0.14 * autoPress.get() }],
  }));
  const limitStyle = useAnimatedStyle(() => ({ opacity: 0.62 + 0.38 * limit.get(), transform: [{ scale: 1 + 0.04 * limit.get() }] }));
  const ctaStyle = useAnimatedStyle(() => ({ opacity: 0.42 + 0.58 * (1 - empty.get()) }));

  return (
    <View style={[styles.screen, { paddingTop: insets.top, paddingBottom: Math.max(insets.bottom, 12) + 8 }]}>
      <StatusBar style="dark" />
      <LinearGradient colors={C.paper} locations={[0, 0.5, 1]} style={StyleSheet.absoluteFill} />
      <SapGrain />

      <View style={styles.nav}>
        <View style={styles.navButton}>
          <Svg width={18} height={18} viewBox="0 0 18 18">
            <Path d="M11 3.5 5.5 9l5.5 5.5" stroke={C.ink} strokeWidth={2} strokeLinecap="round" strokeLinejoin="round" fill="none" />
          </Svg>
        </View>
        <Text allowFontScaling={false} style={styles.navTitle}>
          {COPY.title}
        </Text>
        <View style={styles.navSpace} />
      </View>

      <View style={styles.amountBlock}>
        <Text allowFontScaling={false} style={styles.place}>
          {COPY.place}
        </Text>
        <View style={{ marginTop: L.compact ? 4 : 8 }}>
          <SapAmount table={table} dollarX={dollarX} commaX={commaX} commaOn={commaOn} empty={empty} shake={shake} width={width} size={L.amount} line={L.line} metrics={metrics} instant={reduced} />
        </View>
        <Animated.Text allowFontScaling={false} style={[styles.limit, { marginTop: L.compact ? 2 : 6 }, limitStyle]}>
          {COPY.limit}
        </Animated.Text>
        <SapMeasure size={L.amount} line={L.line} onMetrics={onMetrics} />
      </View>

      <Text allowFontScaling={false} style={styles.between}>
        {COPY.between}
      </Text>
      <View style={styles.seats} onLayout={(e) => seatsY.set(e.nativeEvent.layout.y)}>
        {SEATS.map((i) => (
          <SapFriend key={i} index={i} width={L.column} disc={L.disc} active={active} shares={shares} onToggle={userFriend} onRelease={userRelease} autoFriend={autoFriend} autoPress={autoPress} instant={reduced} />
        ))}
      </View>

      <View style={[styles.pad, { gap: L.gap, marginTop: L.block }]} onLayout={(e) => padY.set(e.nativeEvent.layout.y)}>
        {CELLS.map((cell) => (
          <PadKey key={cell} cell={cell} width={L.keyW} height={L.keyH} onPress={userKey} onRelease={userRelease} autoKey={autoKey} autoPress={autoPress} />
        ))}
      </View>

      <Animated.View style={[styles.cta, { height: L.cta, borderRadius: L.cta / 2, marginTop: L.block }, ctaStyle]}>
        <Text allowFontScaling={false} style={styles.ctaLabel}>
          {COPY.cta}
        </Text>
      </Animated.View>

      <Animated.View pointerEvents="none" style={[styles.ghost, ghost]} />
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: C.paper[1] },
  nav: { height: 44, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', paddingHorizontal: SIDE },
  navButton: {
    width: 36,
    height: 36,
    borderRadius: 18,
    backgroundColor: C.key,
    alignItems: 'center',
    justifyContent: 'center',
    boxShadow: '0 1px 1.5px rgba(40,32,20,0.14), 0 4px 10px rgba(40,32,20,0.07), inset 0 1px 0 rgba(255,255,255,0.9)',
  },
  navTitle: { color: C.ink, fontSize: 17, fontWeight: '600', letterSpacing: -0.2 },
  navSpace: { width: 36 },
  amountBlock: { flex: 1, alignItems: 'center', justifyContent: 'center' },
  place: { color: C.mute, fontSize: 14, fontWeight: '500' },
  limit: { color: C.ink, fontSize: 13, fontWeight: '500', fontVariant: ['tabular-nums'] },
  between: { color: C.mute, fontSize: 13, fontWeight: '500', marginBottom: 10, marginHorizontal: SIDE },
  seats: { flexDirection: 'row', paddingHorizontal: SIDE },
  pad: { flexDirection: 'row', flexWrap: 'wrap', paddingHorizontal: SIDE },
  keyAmbient: {
    position: 'absolute', left: 0, right: 0, top: 0, bottom: 0,
    borderRadius: 18,
    borderCurve: 'continuous',
    backgroundColor: C.key,
    boxShadow: '0 7px 16px rgba(40,32,20,0.10)',
  },
  keyFace: {
    position: 'absolute', left: 0, right: 0, top: 0, bottom: 0,
    borderRadius: 18,
    borderCurve: 'continuous',
    backgroundColor: C.key,
    alignItems: 'center',
    justifyContent: 'center',
    overflow: 'hidden',
    boxShadow: '0 1px 1.5px rgba(40,32,20,0.18), inset 0 1px 0 rgba(255,255,255,0.95), inset 0 -1px 0 rgba(40,32,20,0.05)',
  },
  keyShade: { position: 'absolute', left: 0, right: 0, top: 0, bottom: 0, backgroundColor: C.keyPressed },
  keyLabel: { color: C.ink, fontFamily: 'ui-rounded', fontSize: 26, fontWeight: '500', fontVariant: ['tabular-nums'] },
  cta: {
    marginHorizontal: SIDE,
    backgroundColor: C.accent,
    alignItems: 'center',
    justifyContent: 'center',
    boxShadow: '0 1px 2px rgba(18,60,44,0.30), 0 8px 18px rgba(18,60,44,0.18), inset 0 1px 0 rgba(255,255,255,0.18)',
  },
  ctaLabel: { color: C.accentInk, fontSize: 17, fontWeight: '600', letterSpacing: -0.2 },
  ghost: {
    position: 'absolute',
    left: 0,
    top: 0,
    width: GHOST,
    height: GHOST,
    borderRadius: GHOST / 2,
    backgroundColor: 'rgba(30,27,22,0.14)',
    borderWidth: 1,
    borderColor: 'rgba(30,27,22,0.26)',
  },
});
