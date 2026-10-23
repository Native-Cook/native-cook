import * as Haptics from 'expo-haptics';
import { LinearGradient } from 'expo-linear-gradient';
import { StatusBar } from 'expo-status-bar';
import { type ReactNode, useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { type StyleProp, StyleSheet, Text, type TextStyle, View, type ViewStyle } from 'react-native';
import { Gesture, GestureDetector, GestureHandlerRootView, Pressable } from 'react-native-gesture-handler';
import Animated, {
  ReduceMotion, type SharedValue, useAnimatedStyle, useFrameCallback, useReducedMotion, useSharedValue, withDelay, withSpring,
  withTiming,
} from 'react-native-reanimated';
import { scheduleOnRN } from 'react-native-worklets';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { ACTION_COPY, ALL, BALANCE_COPY, BRAND, FILTERS, FRIENDS, type Row, TILE_COPY } from './data';
import {
  ArrowRightGlyph, BrandMark, CardGlyph, ChartBoxGlyph, ChevronGlyph, CommandGlyph, DotsMark, DownGlyph, HoldingMark,
  MoreGlyph, PeopleGlyph, PlusGlyph, PuffMark, RepeatGlyph, SendGlyph, SphereMark,
} from './glyphs';
import {
  chipFadeAt, chipPosAt, CHIP_SPRING, EASE_OUT, FAN, fanAt, LIFT, liftAt, LOOP_MS, PRESS, pressAt, RESUME_AFTER_MS,
  ROW_FADE_MS, ROW_STAGGER_MS,
} from './motion';
import { ACTIONS, BALANCE, CHIPS, COLOR, FILL, HEADER, SHEET, TILES, TOP_SHEET, topOffset } from './theme';

export type WalletTransactionsProps = {
  autoPlay?: boolean;
  /** Dev only: hold the autoplay script at this time (ms) for comparison with the pin. */
  freezeAt?: number;
};

const NEVER = ReduceMotion.Never;
const FILL_ABS = { position: 'absolute', left: 0, right: 0, top: 0, bottom: 0 } as const;
const LISTS: Row[][] = [ALL, ALL.filter(r => r.date), FRIENDS];
// A system-font Text box puts the cap top this fraction of the size below its top.
const CAP_OFFSET = 0.247;

/** Text placed by its measured left edge and cap top, as the pin was measured. */
function T({ x, cap, size, weight = '400', color, style, children, right }: {
  x?: number; cap: number; size: number; weight?: TextStyle['fontWeight']; color: string;
  style?: StyleProp<TextStyle>; children: ReactNode; right?: number;
}) {
  return (
    <Text
      style={[
        { position: 'absolute', top: cap - size * CAP_OFFSET, fontSize: size, fontWeight: weight, color },
        x !== undefined ? { left: x } : { right },
        style,
      ]}>
      {children}
    </Text>
  );
}

function Press({ children, style, label, onPress, selected }: {
  children: ReactNode; style: StyleProp<ViewStyle>; label: string; onPress?: () => void; selected?: boolean;
}) {
  const s = useSharedValue(1);
  const anim = useAnimatedStyle(() => ({ transform: [{ scale: s.get() }] }));
  return (
    <Animated.View style={[style, anim]}>
      <Pressable
        accessibilityRole="button" accessibilityLabel={label} accessibilityState={selected === undefined ? undefined : { selected }}
        hitSlop={6} onPress={onPress} style={StyleSheet.absoluteFill}
        onPressIn={() => s.set(withSpring(PRESS.scale, PRESS.spring))}
        onPressOut={() => s.set(withSpring(1, PRESS.spring))}>
        {children}
      </Pressable>
    </Animated.View>
  );
}

function Sunk({ colors, radius, style, diagonal }: { colors: readonly string[]; radius: number; style?: StyleProp<ViewStyle>; diagonal?: boolean }) {
  return (
    <LinearGradient
      colors={colors as [string, string, ...string[]]}
      start={{ x: diagonal ? 0.15 : 0.5, y: 0 }} end={{ x: diagonal ? 0.85 : 0.5, y: 1 }}
      style={[StyleSheet.absoluteFill, { borderRadius: radius }, style]} />
  );
}

function Header() {
  return (
    <>
      <View style={{ position: 'absolute', left: HEADER.markX, top: HEADER.markY }}>
        <BrandMark color={COLOR.ink} />
      </View>
      <T x={HEADER.wordX} cap={HEADER.wordCapTop} size={HEADER.wordSize} weight="800" color={COLOR.ink} style={styles.word}>{BRAND}</T>
      {HEADER.buttons.map((cx, i) => (
        <Press key={cx} label={i === 0 ? 'Insights' : 'Cards'}
          style={[styles.headerButton, { left: cx - HEADER.button / 2, top: HEADER.buttonY - HEADER.button / 2 }]}>
          <Sunk colors={FILL.button} radius={HEADER.button / 2} diagonal />
          <View style={styles.center}>{i === 0 ? <ChartBoxGlyph color={COLOR.icon} /> : <CardGlyph color={COLOR.icon} />}</View>
        </Press>
      ))}
    </>
  );
}

function Balance() {
  const p = BALANCE.pill;
  return (
    <>
      <T x={BALANCE.labelX} cap={BALANCE.labelCapTop} size={BALANCE.labelSize} weight="500" color={COLOR.label}>Balance</T>
      <Press label="Account, Personal" style={[styles.pill, { left: p.x, top: p.y, width: p.w, height: p.h }]}>
        <View style={styles.pillRow}>
          <Text style={styles.pillText}>Personal</Text>
          <ChevronGlyph color={COLOR.white} w={7} h={12} stroke={2.1} />
        </View>
      </Press>
      <T x={BALANCE.dollarX} cap={BALANCE.dollarTop} size={BALANCE.dollarSize} weight="500" color={COLOR.grey}>$</T>
      <T x={BALANCE.amountX} cap={BALANCE.amountCapTop} size={BALANCE.amountSize} weight="500" color={COLOR.figure} style={styles.amount}>
        {BALANCE_COPY.whole}
        <Text style={{ color: COLOR.grey }}>{BALANCE_COPY.cents}</Text>
      </T>
    </>
  );
}

function Tile({ index, children, label }: { index: number; children: ReactNode; label: string }) {
  return (
    <Press label={label}
      style={[styles.tile, { left: TILES.xs[index], top: TILES.top, width: TILES.widths[index], height: TILES.height }]}>
      <Sunk colors={FILL.tile} radius={TILES.radius} />
      {children}
    </Press>
  );
}

function TileIcon({ children }: { children: ReactNode }) {
  const c = TILES.icon;
  return (
    <View style={[styles.tileIcon, { left: c.x, top: c.y, width: c.size, height: c.size, borderRadius: c.radius }]}>
      <Sunk colors={FILL.iconSquare} radius={c.radius} />
      <View style={styles.center}>{children}</View>
    </View>
  );
}

function TileAmount({ whole, cents }: { whole: string; cents: string }) {
  return (
    <>
      <T x={TILES.dollarX} cap={TILES.dollarTop + 0.8} size={TILES.dollarSize} weight="500" color={COLOR.grey}>$</T>
      <T x={TILES.amountX} cap={TILES.amountCapTop} size={TILES.amountSize} weight="500" color={COLOR.figure} style={styles.amount}>
        {whole}
        {cents ? <Text style={{ color: COLOR.grey }}>{cents}</Text> : null}
      </T>
    </>
  );
}

type Hero = {
  lift: SharedValue<number>; // 0 on the sheet, 1 held up
  press: SharedValue<number>; // the anticipation press, 0..1
  tiltX: SharedValue<number>; // -1..1, where the finger is on the tile
  tiltY: SharedValue<number>;
  fans: readonly SharedValue<number>[]; // per holding, 0..1
  reduced: boolean;
};

function Holding({ k, fan, reduced }: { k: number; fan: SharedValue<number>; reduced: boolean }) {
  'use no memo';
  const h = TILES.holdings;
  const style = useAnimatedStyle(() => {
    const f = reduced ? 0 : fan.get();
    return { transform: [{ translateX: k * (FAN.step - h.step) * f }, { translateY: -FAN.rise * f }] };
  });
  return (
    <Animated.View style={[{ position: 'absolute', left: h.x + k * h.step, top: h.cy - h.size / 2 }, style]}>
      <HoldingMark kind={k as 0 | 1 | 2 | 3} size={h.size} ring={h.ring} />
    </Animated.View>
  );
}

/** The Invest tile: held, it lifts off the sheet towards the finger and fans its holdings out. */
function InvestTile({ hero, gesture }: { hero: Hero; gesture: ReturnType<typeof Gesture.Pan> }) {
  'use no memo';
  const { lift, press, tiltX, tiltY, fans, reduced } = hero;
  const w = TILES.widths[0];
  const tile = useAnimatedStyle(() => {
    const l = reduced ? 0 : lift.get();
    return {
      transform: [
        // the tile leans and drifts a little towards the finger (kept 2D: a 3D turn would cut into
        // the flat sheet behind it)
        { translateX: tiltX.get() * LIFT.drift * l },
        { translateY: -LIFT.rise * l + tiltY.get() * LIFT.drift * 0.5 * l },
        { rotate: `${tiltX.get() * LIFT.tilt * l}deg` },
        { scale: 1 - (1 - LIFT.pressScale) * press.get() + LIFT.scale * l },
      ],
    };
  });
  const shadow = useAnimatedStyle(() => {
    const l = Math.max(0, lift.get());
    const sh = LIFT.shadow;
    return {
      opacity: sh.opacity * Math.min(1, l),
      transform: [
        { translateY: reduced ? sh.dy[0] : sh.dy[0] + (sh.dy[1] - sh.dy[0]) * l },
        { scale: reduced ? 1 : sh.spread[0] + (sh.spread[1] - sh.spread[0]) * l },
      ],
    };
  });
  return (
    <>
      <Animated.View pointerEvents="none"
        style={[styles.tileShadow, { left: TILES.xs[0], top: TILES.top, width: w, height: TILES.height }, shadow]} />
      <GestureDetector gesture={gesture}>
        <Animated.View accessible accessibilityRole="button" accessibilityLabel={`Invest, ${TILE_COPY.invest.whole} dollars`}
          style={[styles.tile, { left: TILES.xs[0], top: TILES.top, width: w, height: TILES.height }, tile]}>
          <Sunk colors={FILL.tile} radius={TILES.radius} />
          <TileIcon><CommandGlyph color={COLOR.invest} /></TileIcon>
          <T x={TILES.labelX} cap={TILES.labelCapTop} size={TILES.labelSize} color={COLOR.label}>{TILE_COPY.invest.label}</T>
          {[0, 1, 2, 3].map(k => <Holding key={k} k={k} fan={fans[k]} reduced={reduced} />)}
          <TileAmount whole={TILE_COPY.invest.whole} cents={TILE_COPY.invest.cents} />
        </Animated.View>
      </GestureDetector>
    </>
  );
}

function Tiles({ hero, gesture }: { hero: Hero; gesture: ReturnType<typeof Gesture.Pan> }) {
  return (
    <>
      <InvestTile hero={hero} gesture={gesture} />
      <Tile index={1} label={`Shared, ${TILE_COPY.shared.whole}${TILE_COPY.shared.cents} dollars`}>
        <TileIcon><PeopleGlyph color={COLOR.shared} /></TileIcon>
        <T x={TILES.labelX} cap={TILES.labelCapTop} size={TILES.labelSize} color={COLOR.label}>{TILE_COPY.shared.label}</T>
        <TileAmount whole={TILE_COPY.shared.whole} cents={TILE_COPY.shared.cents} />
      </Tile>
      <Tile index={2} label="Loans">
        <T x={TILES.loansLabelX} cap={TILES.loansCapTop} size={TILES.labelSize} color={COLOR.label}>{TILE_COPY.loans.label}</T>
        <View style={{ position: 'absolute', left: TILES.arrow.x, top: TILES.arrow.y }}>
          <ArrowRightGlyph color={COLOR.ink} />
        </View>
      </Tile>
    </>
  );
}

function Actions() {
  const glyphs = [<SendGlyph key="s" color={COLOR.actionIcon} />, <DownGlyph key="d" color={COLOR.actionIcon} />, <PlusGlyph key="p" color={COLOR.actionIcon} />];
  return (
    <>
      {ACTIONS.items.map((it, i) => (
        <Press key={it.x} label={i < 3 ? ACTION_COPY[i] : 'More'}
          style={[styles.action, { left: it.x, top: ACTIONS.y, width: it.w, height: ACTIONS.height }]}>
          <LinearGradient colors={[FILL.actionRimTop, COLOR.band, COLOR.band, FILL.actionRimBottom]} locations={[0, 0.4, 0.6, 1]}
            start={{ x: 0.1, y: 0 }} end={{ x: 0.9, y: 1 }} style={[StyleSheet.absoluteFill, { borderRadius: ACTIONS.height / 2 }]} />
          <View style={[styles.actionInner, { borderRadius: ACTIONS.height / 2 - 1 }]} />
          {i < 3 ? (
            <>
              <View style={[styles.actionCircle, { left: ACTIONS.circleInset, top: (ACTIONS.height - ACTIONS.circle) / 2 }]}>
                <LinearGradient colors={['#4C4B4A', '#262626', '#2B2B2A', '#444342']} locations={[0, 0.4, 0.6, 1]}
                  start={{ x: 0, y: 0.2 }} end={{ x: 1, y: 0.8 }} style={[StyleSheet.absoluteFill, { borderRadius: ACTIONS.circle / 2 }]} />
                <LinearGradient colors={FILL.actionCircle as unknown as [string, string]} start={{ x: 0.2, y: 0.2 }} end={{ x: 0.9, y: 0.9 }}
                  style={[StyleSheet.absoluteFill, { margin: 1, borderRadius: ACTIONS.circle / 2 - 1 }]} />
                <View style={styles.center}>{glyphs[i]}</View>
              </View>
              <Text style={[styles.actionText, { left: ACTIONS.circleInset + ACTIONS.circle + ACTIONS.textGap }]}>{ACTION_COPY[i]}</Text>
            </>
          ) : (
            <View style={styles.center}><MoreGlyph color={COLOR.dots} /></View>
          )}
        </Press>
      ))}
    </>
  );
}

function RowMark({ row }: { row: Row }) {
  if (row.icon === 'puff') return <PuffMark size={SHEET.icon} />;
  return (
    <View style={[styles.rowIcon, row.icon === 'initials' && { backgroundColor: row.tint, borderColor: row.tint }]}>
      {row.icon === 'dots' && <DotsMark size={SHEET.icon - 2} />}
      {row.icon === 'sphere' && <SphereMark size={SHEET.icon - 2} />}
      {row.icon === 'word' && <Text style={styles.wordmark}>{row.word}</Text>}
      {row.icon === 'initials' && <Text style={styles.initials}>{row.word}</Text>}
    </View>
  );
}

function TxRow({ row, index }: { row: Row; index: number }) {
  const top = SHEET.rowsTop + index * SHEET.pitch;
  return (
    <View style={StyleSheet.absoluteFill} pointerEvents="box-none"
      accessible accessibilityLabel={`${row.name}, ${row.meta}${row.date ? `, repeats, ${row.date}` : ''}, ${row.amount}${row.cents}`}>
      <View style={{ position: 'absolute', left: 16, top }}><RowMark row={row} /></View>
      <T x={SHEET.nameX} cap={top + 7.7} size={SHEET.nameSize} color={COLOR.ink}>{row.name}</T>
      <View style={[styles.metaRow, { top: top + 30 - SHEET.metaSize * CAP_OFFSET }]}>
        <Text style={styles.meta}>{row.meta}</Text>
        {row.date && (
          <>
            <Text style={styles.meta}>{' • '}</Text>
            <View style={styles.repeat}><RepeatGlyph color={COLOR.meta} /></View>
            <Text style={styles.meta}>{row.date}</Text>
          </>
        )}
      </View>
      <T right={402 - SHEET.amountRight} cap={top + 17.2} size={SHEET.amountSize} weight="400" color={COLOR.figure}>
        {row.amount}<Text style={{ color: COLOR.grey }}>{row.cents}</Text>
      </T>
    </View>
  );
}

function FadeRow({ row, i, index, fade, reduced, hidden }: {
  row: Row; i: number; index: number; fade: SharedValue<number>; reduced: boolean; hidden: boolean;
}) {
  'use no memo';
  const style = useAnimatedStyle(() => ({ opacity: Math.max(0, 1 - Math.abs(fade.get() - index) * (reduced ? 1 : 2)) }));
  return (
    <Animated.View
      style={[StyleSheet.absoluteFill, style]}
      pointerEvents="none"
      accessibilityElementsHidden={hidden}
      importantForAccessibility={hidden ? 'no-hide-descendants' : 'auto'}>
      <TxRow row={row} index={i} />
    </Animated.View>
  );
}

/** One filter's rows; row `i` cross-fades on its own value, a stagger after the one above. */
function RowsLayer({ index, fades, reduced, hidden }: {
  index: number; fades: readonly SharedValue<number>[]; reduced: boolean; hidden: boolean;
}) {
  return (
    <>
      {LISTS[index].map((r, i) => (
        <FadeRow key={`${r.name}-${i}`} row={r} i={i} index={index} fade={fades[i]} reduced={reduced} hidden={hidden} />
      ))}
    </>
  );
}

function ChipHighlight({ pos }: { pos: SharedValue<number> }) {
  'use no memo';
  // Childless and absolute, so its width may follow the slide.
  const style = useAnimatedStyle(() => {
    const c = Math.min(2, Math.max(0, pos.get()));
    const a = Math.min(1, Math.floor(c));
    const t = c - a;
    const x = CHIPS[a].x + (CHIPS[a + 1].x - CHIPS[a].x) * t;
    const w = CHIPS[a].w + (CHIPS[a + 1].w - CHIPS[a].w) * t;
    return { width: w, transform: [{ translateX: x }] };
  });
  return (
    <Animated.View style={[styles.chipHighlight, style]}>
      <Sunk colors={FILL.chipActive} radius={SHEET.chipH / 2} />
    </Animated.View>
  );
}

function Chip({ index, chip, picked, onPick }: { index: number; chip: SharedValue<number>; picked: boolean; onPick: (i: number) => void }) {
  'use no memo';
  const on = useAnimatedStyle(() => ({ opacity: Math.max(0, 1 - Math.abs(chip.get() - index)) }));
  const label = FILTERS[index];
  return (
    <Press label={label} selected={picked} onPress={() => onPick(index)}
      style={[styles.chip, { left: CHIPS[index].x, width: CHIPS[index].w }]}>
      <View style={styles.center}>
        <Text style={[styles.chipText, { color: COLOR.chipText }]}>{label}</Text>
        <Animated.Text style={[styles.chipText, styles.chipTextOn, on]}>{label}</Animated.Text>
      </View>
    </Press>
  );
}

export function WalletTransactions({ autoPlay = false, freezeAt }: WalletTransactionsProps) {
  'use no memo';
  const insets = useSafeAreaInsets();
  const oy = topOffset(insets.top);
  const reduced = useReducedMotion();
  const frozen = freezeAt !== undefined;
  const scripted = autoPlay && !reduced && !frozen;

  // Every animated value. The autoplay writes them each frame from the loop clock (motion.ts);
  // a touch takes them over with springs that start from wherever they are.
  const pos = useSharedValue(0); // the chip highlight
  const f0 = useSharedValue(0); // the rows' cross-fade, one per row (top first)
  const f1 = useSharedValue(0);
  const f2 = useSharedValue(0);
  const f3 = useSharedValue(0);
  const fades = useMemo(() => [f0, f1, f2, f3], [f0, f1, f2, f3]);
  const lift = useSharedValue(0);
  const press = useSharedValue(0);
  const tiltX = useSharedValue<number>(LIFT.scriptTilt.x);
  const tiltY = useSharedValue<number>(LIFT.scriptTilt.y);
  const n0 = useSharedValue(0);
  const n1 = useSharedValue(0);
  const n2 = useSharedValue(0);
  const n3 = useSharedValue(0);
  const fans = useMemo(() => [n0, n1, n2, n3], [n0, n1, n2, n3]);
  const [filter, setFilter] = useState(0);

  const applyTime = useCallback(
    (t: number) => {
      'worklet';
      pos.set(chipPosAt(t));
      for (let r = 0; r < 4; r++) fades[r].set(chipFadeAt(t, r));
      lift.set(liftAt(t));
      press.set(pressAt(t));
      tiltX.set(LIFT.scriptTilt.x);
      tiltY.set(LIFT.scriptTilt.y);
      for (let k = 0; k < 4; k++) fans[k].set(fanAt(t, k));
    },
    [pos, fades, lift, press, tiltX, tiltY, fans],
  );

  // ---- the loop clock, on the UI thread ----
  const running = useSharedValue(false);
  const start = useSharedValue(-1);
  const shownChip = useSharedValue(0);
  const frameDriver = useFrameCallback(f => {
    if (!running.get()) return;
    if (start.get() < 0) start.set(f.timestamp);
    const t = (f.timestamp - start.get()) % LOOP_MS;
    applyTime(t);
    const c = Math.round(chipFadeAt(t, 0));
    if (c !== shownChip.get()) {
      shownChip.set(c);
      scheduleOnRN(setFilter, c);
    }
  }, false);
  const resume = useRef<ReturnType<typeof setTimeout> | null>(null);
  const scriptedRef = useRef(scripted);
  useEffect(() => {
    scriptedRef.current = scripted;
    if (resume.current) {
      clearTimeout(resume.current);
      resume.current = null;
    }
    if (frozen) {
      applyTime(freezeAt);
      running.set(false);
      frameDriver.setActive(false);
      const id = setTimeout(() => setFilter(Math.round(chipFadeAt(freezeAt, 0))), 0);
      return () => clearTimeout(id);
    }
    if (!scripted) {
      running.set(false);
      frameDriver.setActive(false);
      return;
    }
    start.set(-1);
    running.set(true);
    frameDriver.setActive(true);
    return () => {
      running.set(false);
      frameDriver.setActive(false);
    };
  }, [frozen, freezeAt, applyTime, frameDriver, running, scripted, start]);

  // ---- touch: any touch stops the loop; 3.5 s after the last one it starts again from rest ----
  useEffect(() => () => { if (resume.current) clearTimeout(resume.current); }, []);
  const interrupt = useCallback(() => {
    running.set(false);
    frameDriver.setActive(false);
    if (resume.current) clearTimeout(resume.current);
    if (scripted) {
      resume.current = setTimeout(() => {
        if (!scriptedRef.current) return;
        // settle home first, then start the clock at rest
        pos.set(withSpring(0, CHIP_SPRING));
        fades.forEach((v, r) => v.set(withDelay(r * ROW_STAGGER_MS, withTiming(0, { duration: ROW_FADE_MS, reduceMotion: NEVER }))));
        setFilter(0);
        resume.current = setTimeout(() => {
          if (!scriptedRef.current) return;
          shownChip.set(0);
          start.set(-1);
          running.set(true);
          frameDriver.setActive(true);
        }, 500);
      }, RESUME_AFTER_MS);
    }
  }, [fades, frameDriver, pos, running, scripted, shownChip, start]);

  const pick = useCallback((i: number) => {
    void Haptics.selectionAsync();
    setFilter(i);
    pos.set(reduced ? withTiming(i, { duration: ROW_FADE_MS, reduceMotion: NEVER }) : withSpring(i, CHIP_SPRING));
    fades.forEach((v, r) =>
      v.set(withDelay(reduced ? 0 : r * ROW_STAGGER_MS, withTiming(i, { duration: ROW_FADE_MS, reduceMotion: NEVER }))),
    );
  }, [fades, pos, reduced]);

  const onUserPick = useCallback((i: number) => {
    interrupt();
    pick(i);
  }, [interrupt, pick]);

  const lightTick = useCallback(() => {
    void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light);
  }, []);

  // Hold the Invest tile: it presses in, then lifts towards the finger and fans its holdings;
  // the tilt follows the finger 1:1, and letting go settles everything without a bounce.
  const w = TILES.widths[0];
  const tileGesture = useMemo(
    () =>
      Gesture.Pan()
        .minDistance(0)
        .maxPointers(1)
        .onBegin(e => {
          tiltX.set(Math.max(-1, Math.min(1, (e.x / w) * 2 - 1)));
          tiltY.set(Math.max(-1, Math.min(1, (e.y / TILES.height) * 2 - 1)));
          press.set(withTiming(1, { duration: LIFT.pressInMs, easing: EASE_OUT }));
          lift.set(withDelay(LIFT.pressInMs, withSpring(1, { duration: 370, dampingRatio: LIFT.up.zeta })));
          press.set(withDelay(LIFT.pressInMs, withTiming(0, { duration: LIFT.pressOutMs, easing: EASE_OUT })));
          for (let k = 0; k < 4; k++) {
            fans[k].set(withDelay(LIFT.pressInMs + 130 + k * FAN.stagger, withSpring(1, { duration: 320, dampingRatio: FAN.out.zeta })));
          }
          scheduleOnRN(lightTick);
        })
        .onUpdate(e => {
          // rubber-banded: the tilt never passes the tile's edge however far the finger goes
          const nx = (e.x / w) * 2 - 1;
          const ny = (e.y / TILES.height) * 2 - 1;
          tiltX.set(nx / (1 + Math.abs(nx) * 0.5) * 1.5);
          tiltY.set(ny / (1 + Math.abs(ny) * 0.5) * 1.5);
        })
        .onFinalize(() => {
          press.set(withTiming(0, { duration: LIFT.pressOutMs, easing: EASE_OUT }));
          lift.set(withSpring(0, { duration: 400, dampingRatio: 1 }));
          for (let k = 0; k < 4; k++) fans[k].set(withDelay((3 - k) * FAN.backStagger, withSpring(0, { duration: 350, dampingRatio: 1 })));
        }),
    [fans, lift, lightTick, press, tiltX, tiltY, w],
  );
  const hero = useMemo(() => ({ lift, press, tiltX, tiltY, fans, reduced }), [lift, press, tiltX, tiltY, fans, reduced]);

  return (
    <GestureHandlerRootView style={styles.root} onTouchStart={interrupt}>
      <StatusBar style="dark" />
      <View style={[StyleSheet.absoluteFill, { transform: [{ translateY: oy }] }]}>
        <View style={styles.topSheet} />
        <Header />
        <Balance />
        <Tiles hero={hero} gesture={tileGesture} />
        <Actions />
        <View style={styles.sheet} />
        <View style={styles.grabber} />
        <T x={SHEET.titleX} cap={SHEET.titleCapTop} size={SHEET.titleSize} weight="600" color={COLOR.ink} style={styles.title}>Transactions</T>
        <Press label="View all transactions" style={[styles.viewAll, { left: SHEET.viewAll.x, top: SHEET.viewAll.y, width: SHEET.viewAll.w, height: SHEET.viewAll.h }]}>
          <Sunk colors={FILL.chip} radius={SHEET.viewAll.h / 2} />
          <View style={styles.viewAllRow}>
            <Text style={styles.viewAllText}>View all</Text>
            <ChevronGlyph color={COLOR.meta} w={7} h={12} stroke={2} />
          </View>
        </Press>
        {CHIPS.map((c, i) => (
          <View key={c.x} style={[styles.chipBack, { left: c.x, width: c.w }]}>
            <Sunk colors={FILL.chip} radius={SHEET.chipH / 2} />
          </View>
        ))}
        <ChipHighlight pos={pos} />
        {CHIPS.map((c, i) => <Chip key={c.x} index={i} chip={f0} picked={filter === i} onPick={onUserPick} />)}
        {LISTS.map((_, i) => <RowsLayer key={i} index={i} fades={fades} reduced={reduced} hidden={filter !== i} />)}
        <LinearGradient pointerEvents="none" colors={['rgba(255,255,255,0)', 'rgba(255,255,255,0.55)', 'rgba(255,255,255,0.75)']}
          locations={[0, 0.5, 1]} style={styles.fade} />
      </View>
    </GestureHandlerRootView>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: COLOR.band },
  center: { ...FILL_ABS, alignItems: 'center', justifyContent: 'center' },
  topSheet: {
    position: 'absolute', left: 0, right: 0, top: -200, height: TOP_SHEET.bottom + 200, backgroundColor: COLOR.white,
    borderBottomLeftRadius: TOP_SHEET.radius, borderBottomRightRadius: TOP_SHEET.radius, borderCurve: 'continuous',
  },
  word: { fontStyle: 'italic', letterSpacing: 0.4 },
  headerButton: { position: 'absolute', width: HEADER.button, height: HEADER.button },
  pill: {
    position: 'absolute', borderRadius: 14.2, backgroundColor: COLOR.pill, borderWidth: StyleSheet.hairlineWidth, borderColor: COLOR.pillRim,
  },
  pillRow: { ...FILL_ABS, flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: 6 },
  pillText: { color: COLOR.white, fontSize: BALANCE.pill.textSize, fontWeight: '500' },
  amount: { fontVariant: ['tabular-nums'], letterSpacing: -0.6, transform: [{ scaleX: 0.95 }], transformOrigin: 'left center' },
  tile: { position: 'absolute', borderRadius: TILES.radius, borderCurve: 'continuous' },
  tileShadow: {
    position: 'absolute', borderRadius: TILES.radius, borderCurve: 'continuous', backgroundColor: COLOR.white,
    boxShadow: '0px 6px 18px rgba(20, 20, 20, 0.55)',
  },
  tileIcon: { position: 'absolute', overflow: 'hidden', borderCurve: 'continuous' },
  action: { position: 'absolute' },
  actionInner: { ...FILL_ABS, margin: 1, backgroundColor: COLOR.band },
  actionCircle: { position: 'absolute', width: ACTIONS.circle, height: ACTIONS.circle },
  actionText: {
    position: 'absolute', top: ACTIONS.height / 2 - ACTIONS.textSize * 0.6, fontSize: ACTIONS.textSize, fontWeight: '500', color: COLOR.actionText,
  },
  sheet: {
    position: 'absolute', left: 0, right: 0, top: SHEET.top, height: 1200, backgroundColor: COLOR.white,
    borderTopLeftRadius: SHEET.radius, borderTopRightRadius: SHEET.radius, borderCurve: 'continuous',
  },
  grabber: {
    position: 'absolute', left: 201 - SHEET.grabber.w / 2, top: SHEET.grabber.y, width: SHEET.grabber.w, height: SHEET.grabber.h,
    borderRadius: SHEET.grabber.h / 2, backgroundColor: COLOR.grabber,
  },
  title: { letterSpacing: -0.2, transform: [{ scaleX: 0.9 }], transformOrigin: 'left center' },
  viewAll: { position: 'absolute' },
  viewAllRow: { ...FILL_ABS, flexDirection: 'row', alignItems: 'center', paddingLeft: SHEET.viewAll.textX, gap: 4.5 },
  viewAllText: {
    fontSize: SHEET.viewAll.textSize, color: COLOR.meta, transform: [{ scaleX: 0.93 }], transformOrigin: 'left center',
  },
  chipBack: { position: 'absolute', top: SHEET.chipsY, height: SHEET.chipH },
  chipHighlight: { position: 'absolute', left: 0, top: SHEET.chipsY, height: SHEET.chipH },
  chip: { position: 'absolute', top: SHEET.chipsY, height: SHEET.chipH },
  chipText: { fontSize: SHEET.chipSize },
  chipTextOn: { position: 'absolute', color: COLOR.chipActiveText },
  rowIcon: {
    width: SHEET.icon, height: SHEET.icon, borderRadius: SHEET.iconRadius, borderCurve: 'continuous', backgroundColor: COLOR.white,
    borderWidth: 1, borderColor: COLOR.rowIconEdge, alignItems: 'center', justifyContent: 'center',
  },
  wordmark: { fontSize: 16, color: '#6E6D6C', letterSpacing: -0.3 },
  initials: { fontSize: 16, fontWeight: '600', color: COLOR.figure },
  metaRow: { position: 'absolute', left: SHEET.nameX, flexDirection: 'row', alignItems: 'center' },
  meta: { fontSize: SHEET.metaSize, color: COLOR.meta },
  repeat: { marginRight: 3 },
  fade: { position: 'absolute', left: 0, right: 0, top: SHEET.fadeTop, height: 874 - SHEET.fadeTop },
});
