import { Canvas, LinearGradient as SkiaLinearGradient, Text as SkiaText, matchFont } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { LinearGradient } from 'expo-linear-gradient';
import { SymbolView } from 'expo-symbols';
import { useMemo } from 'react';
import { Platform, StyleSheet, Text, TextInput, View, useWindowDimensions } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  type SharedValue,
  useAnimatedProps,
  useAnimatedReaction,
  useAnimatedStyle,
  useDerivedValue,
  useFrameCallback,
  useReducedMotion,
  useSharedValue,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

import { StreamingText, measureStreamingText } from './StreamingText';
import {
  BLOCK_PAUSE,
  CHIP_COUNT,
  CHIP_DUR,
  LINE_DUR,
  LOOP,
  ROW_DUR,
  ROW_GAP,
  ROW_RISE,
  SCROLL_BACK_A,
  SCROLL_BACK_B,
  STREAM_AT,
  answerAlphaAt,
  easeInOutCubic,
  easeOutCubic,
  rubber,
  seg,
  streamAt,
  thinkingAt,
} from './streamFadeMotion';

/*
 Stream Fade Answer — one turn of a chat with a travel assistant. The question sits in its
 bubble; the answer streams in word by word (StreamingText: every word fades up from a blur,
 6 pt below, 28 ms after the one before, in a layout that is final before the first word
 shows): a heading, two paragraphs, three itinerary rows that slide in behind a hairline, a
 closing tip and a cost chip that counts up. A grey line with a moving light band stands in
 while the assistant thinks, and the thread scrolls to keep the newest line in view.

 Everything is a function of one stream clock S (streamFadeMotion.ts). The loop moves it; a
 tap jumps it to the end; a horizontal drag scrubs it.
*/

type Props = {
  /** Plays the turn by itself every LOOP seconds; a touch takes over. */
  autoPlay?: boolean;
};

const C = {
  page: '#f6f4ef',
  ink: '#1d1c1a',
  soft: '#55524c',
  mute: '#8a867e',
  line: 'rgba(29,28,26,0.12)',
  bubble: '#2b2a27',
  bubbleInk: '#f6f4ef',
  accent: '#2f6b4f',
  accentSoft: 'rgba(47,107,79,0.11)',
  field: '#fdfcfa',
};

const QUESTION = 'Plan a 3-day trip to Lisbon under €400';
const THINKING = 'Checking fares and places to stay';
const HEADING = 'Three days in Lisbon';
const PARAGRAPHS = [
  'Here is a plan that comes to €372, flights from Berlin included. I picked the second week of May: fares drop once the holidays are over, and the hills are still cool enough to walk all day.',
  'You would stay three nights in a guesthouse in Alfama at €52 a night, a short climb from the river and two minutes from the tram.',
];
const ROWS = [
  { day: '1', title: 'Alfama and the river', detail: 'Tram 28, Santa Luzia lookout, sardines', cost: '€118' },
  { day: '2', title: 'Belém by bike', detail: 'Riverside path, custard tarts, the tower', cost: '€131' },
  { day: '3', title: 'Sintra day trip', detail: 'Train from Rossio, Pena Palace, sunset', cost: '€123' },
];
const TIP = 'Buy the 24-hour transit pass each morning. It covers the trams, the metro and the lift up to Carmo.';
const TOTAL = 372;

const HEADER_H = 44;
const COMPOSER_H = 64;
const SIDE = 20;
const MAX_CONTENT = 560;
const ROW_H = 56;
const CHIP_H = 38;
/** Room kept under the newest line while the thread follows the stream. */
const FOLLOW_PAD = 16;
/** The scroll follows the stream through a short running average, so a new line never jerks it. */
const FOLLOW_LAG = 0.12;

const IDLE = 2;
const MODE_AUTO = 0;
const MODE_TOUCH = 1;
/** A full-width drag covers this share of the stream. */
const SCRUB_SPAN = 0.8;
/** Past either end the answer leans this many points per (rubber-banded) second. */
const LEAN = 36;

const BODY = { fontSize: 16, lineHeight: 23, color: C.ink } as const;

const AnimatedInput = Animated.createAnimatedComponent(TextInput);

type Thread = {
  left: number;
  width: number;
  bubble: { x: number; y: number; w: number; h: number };
  labelY: number;
  answerY: number;
  heading: { y: number; at: number };
  paragraphs: { y: number; at: number }[];
  rows: { y: number; at: number }[];
  tip: { y: number; at: number };
  chip: { y: number; at: number };
  height: number;
  /** Stream time of the finished answer. */
  end: number;
  /** Where each block starts on the stream clock: scrubbing ticks at these. */
  marks: number[];
  /** Bottom of what has been revealed, as (stream time, y) key frames. */
  keyS: number[];
  keyY: number[];
};

/** The whole thread, measured before anything is shown: positions (pt) and start times (s). */
function makeThread(W: number): Thread {
  const width = Math.min(W - SIDE * 2, MAX_CONTENT);
  const left = (W - width) / 2;
  const keyS: number[] = [];
  const keyY: number[] = [];
  const marks: number[] = [];
  let y = 14;

  const textMax = Math.min(width * 0.86, 340) - 28;
  const q = measureStreamingText(QUESTION, { width: textMax, fontSize: 16, lineHeight: 22 });
  const bw = Math.ceil(q.widest) + 28;
  const bubble = { x: left + width - bw, y, w: bw, h: q.height + 20 };
  y += bubble.h + 22;
  const labelY = y;
  y += 22 + 10;
  const answerY = y;

  let at = 0;
  keyS.push(-1);
  keyY.push(answerY + 28);

  const h = measureStreamingText(HEADING, { width, fontSize: 22, lineHeight: 28, fontWeight: '700', seed: 1 });
  const heading = { y, at };
  keyS.push(at, at + h.lastStart);
  keyY.push(y + 28, y + h.height);
  y += h.height + 6;
  at += h.lastStart + BLOCK_PAUSE;

  const paragraphs: { y: number; at: number }[] = [];
  for (let i = 0; i < PARAGRAPHS.length; i += 1) {
    const m = measureStreamingText(PARAGRAPHS[i], { width, ...BODY, seed: 2 + i });
    paragraphs.push({ y, at });
    marks.push(at);
    keyS.push(at, at + m.lastStart);
    keyY.push(y + BODY.lineHeight, y + m.height);
    y += m.height + (i === PARAGRAPHS.length - 1 ? 12 : 10);
    at += m.lastStart + BLOCK_PAUSE;
  }

  const rows: { y: number; at: number }[] = [];
  for (let i = 0; i < ROWS.length; i += 1) {
    rows.push({ y, at });
    marks.push(at);
    keyS.push(at + ROW_DUR * 0.5);
    keyY.push(y + ROW_H);
    y += ROW_H;
    at += ROW_GAP;
  }
  y += 12;
  at += ROW_DUR * 0.5;

  const t = measureStreamingText(TIP, { width, ...BODY, seed: 7 });
  const tip = { y, at };
  marks.push(at);
  keyS.push(at, at + t.lastStart);
  keyY.push(y + BODY.lineHeight, y + t.height);
  y += t.height + 14;
  at += t.lastStart + BLOCK_PAUSE;

  const chip = { y, at };
  marks.push(at);
  keyS.push(at + CHIP_DUR);
  keyY.push(y + CHIP_H);
  y += CHIP_H;
  const end = at + 0.05 + CHIP_COUNT + 0.2;

  return { left, width, bubble, labelY, answerY, heading, paragraphs, rows, tip, chip, height: y, end, marks, keyS, keyY };
}

/** Bottom edge of what the stream has revealed at S: linear between the key frames. */
function revealedAt(S: number, keyS: number[], keyY: number[]) {
  'worklet';
  const n = keyS.length;
  if (S <= keyS[0]) return keyY[0];
  for (let i = 1; i < n; i += 1) {
    if (S < keyS[i]) {
      const u = (S - keyS[i - 1]) / Math.max(0.0001, keyS[i] - keyS[i - 1]);
      return keyY[i - 1] + (keyY[i] - keyY[i - 1]) * u;
    }
  }
  return keyY[n - 1];
}

/** How far the thread is scrolled up at S so the newest line stays in the viewport. */
function scrollAt(S: number, keyS: number[], keyY: number[], viewH: number) {
  'worklet';
  let sum = 0;
  for (let k = 0; k < 4; k += 1) sum += revealedAt(S - FOLLOW_LAG * k, keyS, keyY);
  return Math.max(0, sum / 4 + FOLLOW_PAD - viewH);
}

/** The scroll the loop shows at loop time t: at rest, gliding back for the next turn, then following the stream. */
function loopScrollAt(t: number, playing: boolean, end: number, keyS: number[], keyY: number[], viewH: number) {
  'worklet';
  if (!playing || t < SCROLL_BACK_A) return scrollAt(end, keyS, keyY, viewH);
  if (t < SCROLL_BACK_B) return scrollAt(end, keyS, keyY, viewH) * (1 - easeInOutCubic(seg(t, SCROLL_BACK_A, SCROLL_BACK_B)));
  return scrollAt(t - STREAM_AT, keyS, keyY, viewH);
}

function blockIndex(S: number, marks: number[]) {
  'worklet';
  let n = 0;
  for (let i = 0; i < marks.length; i += 1) if (S >= marks[i]) n += 1;
  return n;
}

function tick() {
  Haptics.selectionAsync().catch(() => {});
}

function thud() {
  Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
}

export function StreamFadeAnswer({ autoPlay = true }: Props) {
  const insets = useSafeAreaInsets();
  const { width: W, height: H } = useWindowDimensions();
  const reduced = useReducedMotion();
  const auto = autoPlay && !reduced;
  const T = useMemo(() => makeThread(W), [W]);
  const top = insets.top + HEADER_H;
  const viewH = H - top - (COMPOSER_H + Math.max(insets.bottom, 10));

  /** Loop time. Zero is the rest: the finished answer. */
  const clock = useSharedValue(0);
  const mode = useSharedValue(MODE_AUTO);
  /** Under touch: the stream time, its speed after a release (s/s), and the state of the hand. */
  const manual = useSharedValue(T.end);
  const speed = useSharedValue(0);
  const dragging = useSharedValue(0);
  const skipping = useSharedValue(0);
  const dragFrom = useSharedValue(0);
  const idleFor = useSharedValue(0);
  /** What the loop was showing when the finger arrived, eased away so nothing pops. */
  const heldAlpha = useSharedValue(1);
  const heldThink = useSharedValue(0);
  const heldScroll = useSharedValue(0);

  useFrameCallback((f) => {
    const dt = Math.min(f.timeSincePreviousFrame ?? 16, 50) / 1000;
    if (mode.get() === MODE_AUTO) {
      if (auto) clock.set((clock.get() + dt) % LOOP);
      return;
    }
    const ease = Math.min(1, dt * 10);
    heldAlpha.set(heldAlpha.get() + (1 - heldAlpha.get()) * ease);
    heldThink.set(heldThink.get() * (1 - ease));
    heldScroll.set(heldScroll.get() * (1 - ease));
    if (dragging.get() === 1) return;
    idleFor.set(idleFor.get() + dt);
    let s = manual.get();
    let v = speed.get();
    if (idleFor.get() > IDLE) {
      // Back to rest, which is the first frame of the loop.
      s += (T.end - s) * Math.min(1, dt * 7);
      v = 0;
      if (Math.abs(T.end - s) < 0.01) {
        manual.set(T.end);
        speed.set(0);
        skipping.set(0);
        clock.set(0);
        mode.set(MODE_AUTO);
        return;
      }
    } else if (skipping.get() === 1) {
      s += (T.end - s) * Math.min(1, dt * 9);
      if (T.end - s < 0.01) {
        s = T.end;
        skipping.set(0);
      }
    } else {
      // A release keeps its speed, slowing against drag; past an end a spring brings it back.
      s += v * dt;
      v *= Math.exp(-dt * 4.5);
      if (s < 0) {
        s += (0 - s) * Math.min(1, dt * 12);
        v = 0;
      } else if (s > T.end) {
        s += (T.end - s) * Math.min(1, dt * 12);
        v = 0;
      }
    }
    manual.set(s);
    speed.set(v);
  });

  /** The stream clock everything reads. */
  const stream = useDerivedValue(() => {
    if (!auto && mode.get() === MODE_AUTO) return T.end;
    return mode.get() === MODE_AUTO ? streamAt(clock.get(), T.end) : manual.get();
  });

  const threadStyle = useAnimatedStyle(() => {
    const y = mode.get() === MODE_AUTO ? loopScrollAt(clock.get(), auto, T.end, T.keyS, T.keyY, viewH) : scrollAt(manual.get(), T.keyS, T.keyY, viewH) + heldScroll.get();
    return { transform: [{ translateY: -y }] };
  });

  const answerStyle = useAnimatedStyle(() => {
    if (mode.get() === MODE_AUTO) return { opacity: auto ? answerAlphaAt(clock.get()) : 1, transform: [{ translateX: 0 }] };
    const s = manual.get();
    const over = s < 0 ? s : s > T.end ? s - T.end : 0;
    return { opacity: heldAlpha.get(), transform: [{ translateX: over * LEAN }] };
  });

  /** The thinking line: [opacity, band 0…1]. */
  const thinking = useDerivedValue(() => {
    if (mode.get() === MODE_AUTO) return auto ? thinkingAt(clock.get()) : [0, 0];
    return [heldThink.get(), 0.5];
  });

  // A tick each time a scrub crosses the start of a block.
  useAnimatedReaction(
    () => (mode.get() === MODE_TOUCH && dragging.get() === 1 ? blockIndex(manual.get(), T.marks) : -1),
    (now, before) => {
      if (before === null || now < 0 || before < 0 || now === before) return;
      scheduleOnRN(tick);
    },
  );

  // ---- Touch: tap to finish, drag sideways to scrub ----------------------------------------------
  function takeOver() {
    'worklet';
    if (mode.get() === MODE_AUTO) {
      const t = clock.get();
      const s = auto ? streamAt(t, T.end) : T.end;
      const shown = loopScrollAt(t, auto, T.end, T.keyS, T.keyY, viewH);
      const start = Math.max(0, Math.min(T.end, s));
      heldAlpha.set(auto ? answerAlphaAt(t) : 1);
      heldThink.set(auto ? thinkingAt(t)[0] : 0);
      heldScroll.set(shown - scrollAt(start, T.keyS, T.keyY, viewH));
      manual.set(start);
      speed.set(0);
      mode.set(MODE_TOUCH);
    }
    idleFor.set(0);
  }

  const scrub = Gesture.Pan()
    .enabled(!reduced)
    .activeOffsetX([-8, 8])
    .failOffsetY([-14, 14])
    .onStart(() => {
      takeOver();
      dragging.set(1);
      skipping.set(0);
      dragFrom.set(Math.max(0, Math.min(T.end, manual.get())));
    })
    .onUpdate((e) => {
      const raw = dragFrom.get() + (e.translationX / (W * SCRUB_SPAN)) * T.end;
      manual.set(raw < 0 ? rubber(raw) : raw > T.end ? T.end + rubber(raw - T.end) : raw);
      idleFor.set(0);
    })
    .onFinalize((e) => {
      if (dragging.get() === 0) return;
      dragging.set(0);
      idleFor.set(0);
      const s = manual.get();
      speed.set(s < 0 || s > T.end ? 0 : (e.velocityX / (W * SCRUB_SPAN)) * T.end);
    });

  const skip = Gesture.Tap()
    .enabled(!reduced)
    .maxDistance(10)
    .onEnd((_e, ok) => {
      if (!ok) return;
      takeOver();
      speed.set(0);
      skipping.set(1);
      scheduleOnRN(thud);
    });

  const gesture = Gesture.Exclusive(scrub, skip);

  return (
    <View style={styles.root}>
      <GestureDetector gesture={gesture}>
        <View style={[styles.viewport, { top, height: viewH }]} accessible={false}>
          <Animated.View style={[{ height: T.height }, threadStyle]}>
            <View style={[styles.bubble, { left: T.bubble.x, top: T.bubble.y, width: T.bubble.w, height: T.bubble.h }]}>
              <StreamingText text={QUESTION} width={T.bubble.w - 28} fontSize={16} lineHeight={22} color={C.bubbleInk} settled />
            </View>

            <View style={[styles.label, { left: T.left, top: T.labelY }]}>
              <View style={styles.avatar}>
                <LinearGradient colors={['#4f9272', '#2f6b4f', '#214f3a']} locations={[0, 0.55, 1]} start={{ x: 0.2, y: 0 }} end={{ x: 0.8, y: 1 }} style={StyleSheet.absoluteFill} />
                <View style={styles.avatarDot} />
              </View>
              <Text style={styles.labelText}>Juniper</Text>
            </View>

            <ThinkingLine state={thinking} left={T.left} top={T.answerY} width={T.width} />

            <Animated.View style={[StyleSheet.absoluteFill, answerStyle]}>
              <StreamingText text={HEADING} width={T.width} time={stream} start={T.heading.at} fontSize={22} lineHeight={28} fontWeight="700" color={C.ink} seed={1} style={{ position: 'absolute', left: T.left, top: T.heading.y }} />
              {PARAGRAPHS.map((text, i) => (
                <StreamingText key={i} text={text} width={T.width} time={stream} start={T.paragraphs[i].at} {...BODY} seed={2 + i} style={{ position: 'absolute', left: T.left, top: T.paragraphs[i].y }} />
              ))}
              {ROWS.map((row, i) => (
                <Row key={row.day} row={row} stream={stream} at={T.rows[i].at} left={T.left} top={T.rows[i].y} width={T.width} />
              ))}
              <StreamingText text={TIP} width={T.width} time={stream} start={T.tip.at} {...BODY} color={C.soft} seed={7} style={{ position: 'absolute', left: T.left, top: T.tip.y }} />
              <CostChip stream={stream} at={T.chip.at} left={T.left} top={T.chip.y} />
            </Animated.View>
          </Animated.View>
        </View>
      </GestureDetector>

      <View style={[styles.header, { height: top, paddingTop: insets.top }]}>
        <View style={styles.headerSide}>
          <SymbolView name={{ ios: 'chevron.left', android: 'arrow_back', web: 'arrow_back' }} size={19} weight="semibold" tintColor={C.ink} />
        </View>
        <Text style={styles.headerTitle}>Lisbon trip</Text>
        <View style={[styles.headerSide, styles.headerRight]}>
          <SymbolView name={{ ios: 'square.and.pencil', android: 'edit_square', web: 'edit_square' }} size={20} weight="medium" tintColor={C.ink} />
        </View>
      </View>

      <View pointerEvents="none" style={[styles.composerWrap, { paddingBottom: Math.max(insets.bottom, 10) }]}>
        <LinearGradient colors={['rgba(246,244,239,0)', 'rgba(246,244,239,0.86)', C.page]} locations={[0, 0.5, 1]} style={styles.composerFade} />
        <View style={styles.composerBack}>
          <View style={[styles.composer, { width: Math.min(W - SIDE * 2, MAX_CONTENT) }]}>
            <Text style={styles.placeholder}>Ask a follow-up</Text>
            <View style={styles.send}>
              <SymbolView name={{ ios: 'arrow.up', android: 'arrow_upward', web: 'arrow_upward' }} size={15} weight="bold" tintColor="#f6f4ef" />
            </View>
          </View>
        </View>
      </View>
    </View>
  );
}

type ThinkingProps = { state: SharedValue<number[]>; left: number; top: number; width: number };

const THINK_FONT_SIZE = 16;
const BAND = 70;

/** "Checking fares…": grey text with a lighter band travelling across its letters. */
function ThinkingLine({ state, left, top, width }: ThinkingProps) {
  const font = useMemo(() => matchFont({ fontFamily: Platform.OS === 'ios' ? 'System' : 'sans-serif', fontSize: THINK_FONT_SIZE, fontWeight: '500' }), []);
  const textWidth = useMemo(() => {
    const widths = font.getGlyphWidths(font.getGlyphIDs(THINKING));
    let total = 0;
    for (let i = 0; i < widths.length; i += 1) total += widths[i];
    return total;
  }, [font]);
  const from = useDerivedValue(() => ({ x: -BAND + (textWidth + BAND) * state.get()[1] - BAND, y: 0 }));
  const to = useDerivedValue(() => ({ x: -BAND + (textWidth + BAND) * state.get()[1] + BAND, y: 0 }));
  const style = useAnimatedStyle(() => ({ opacity: state.get()[0] }));
  return (
    <Animated.View pointerEvents="none" style={[{ position: 'absolute', left, top, width, height: 28 }, style]}>
      <Canvas style={StyleSheet.absoluteFill}>
        <SkiaText text={THINKING} font={font} x={0} y={20}>
          <SkiaLinearGradient start={from} end={to} colors={['#a5a199', '#e9e6df', '#a5a199']} />
        </SkiaText>
      </Canvas>
    </Animated.View>
  );
}

type RowProps = { row: (typeof ROWS)[number]; stream: SharedValue<number>; at: number; left: number; top: number; width: number };

/** One day of the itinerary: a hairline draws in from the left, the row rises in behind it. */
function Row({ row, stream, at, left, top, width }: RowProps) {
  const lineStyle = useAnimatedStyle(() => {
    const p = easeOutCubic(seg(stream.get(), at, at + LINE_DUR));
    return { opacity: p > 0 ? 1 : 0, transform: [{ translateX: (-width * (1 - p)) / 2 }, { scaleX: Math.max(0.0001, p) }] };
  });
  const bodyStyle = useAnimatedStyle(() => {
    const p = easeOutCubic(seg(stream.get(), at + 0.06, at + 0.06 + ROW_DUR));
    return { opacity: p, transform: [{ translateY: ROW_RISE * (1 - p) }] };
  });
  return (
    <View style={{ position: 'absolute', left, top, width, height: ROW_H }}>
      <Animated.View style={[styles.hairline, lineStyle]} />
      <Animated.View style={[styles.row, bodyStyle]}>
        <View style={styles.day}>
          <Text style={styles.dayText}>{row.day}</Text>
        </View>
        <View style={styles.rowText}>
          <Text style={styles.rowTitle} numberOfLines={1}>
            {row.title}
          </Text>
          <Text style={styles.rowDetail} numberOfLines={1}>
            {row.detail}
          </Text>
        </View>
        <Text style={styles.rowCost}>{row.cost}</Text>
      </Animated.View>
    </View>
  );
}

type ChipProps = { stream: SharedValue<number>; at: number; left: number; top: number };

/** The total: pops in, and the number counts up to it. */
function CostChip({ stream, at, left, top }: ChipProps) {
  const style = useAnimatedStyle(() => {
    const p = easeOutCubic(seg(stream.get(), at, at + CHIP_DUR));
    return { opacity: p, transform: [{ translateY: 6 * (1 - p) }, { scale: 0.94 + 0.06 * p }] };
  });
  const amount = useAnimatedProps(() => {
    const v = `${Math.round(TOTAL * easeOutCubic(seg(stream.get(), at + 0.05, at + 0.05 + CHIP_COUNT)))}`;
    return { text: v, defaultValue: v } as never;
  });
  return (
    <Animated.View style={[styles.chip, { left, top }, style]}>
      <Text style={styles.chipStrong}>€</Text>
      <AnimatedInput animatedProps={amount} editable={false} pointerEvents="none" scrollEnabled={false} underlineColorAndroid="transparent" style={styles.chipAmount} />
      <Text style={styles.chipSoft}>of €400</Text>
      <View style={styles.chipDot} />
      <Text style={styles.chipSpare}>€28 to spare</Text>
    </Animated.View>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: C.page, overflow: 'hidden' },
  viewport: { position: 'absolute', left: 0, right: 0, overflow: 'hidden' },
  bubble: {
    position: 'absolute',
    borderRadius: 20,
    borderCurve: 'continuous',
    backgroundColor: C.bubble,
    paddingHorizontal: 14,
    paddingVertical: 10,
    boxShadow: '0 1px 2px rgba(29,28,26,0.22), 0 10px 24px rgba(29,28,26,0.12), inset 0 1px 0 rgba(255,255,255,0.1)',
  },
  label: { position: 'absolute', height: 22, flexDirection: 'row', alignItems: 'center', gap: 8 },
  avatar: { width: 22, height: 22, borderRadius: 11, overflow: 'hidden', alignItems: 'center', justifyContent: 'center' },
  avatarDot: { width: 7, height: 7, borderRadius: 3.5, backgroundColor: 'rgba(246,244,239,0.92)' },
  labelText: { color: C.soft, fontSize: 14, fontWeight: '600' },
  hairline: { position: 'absolute', left: 0, right: 0, top: 0, height: StyleSheet.hairlineWidth, backgroundColor: C.line },
  row: { flex: 1, flexDirection: 'row', alignItems: 'center', gap: 12 },
  day: { width: 34, height: 34, borderRadius: 10, borderCurve: 'continuous', backgroundColor: C.accentSoft, alignItems: 'center', justifyContent: 'center' },
  dayText: { color: C.accent, fontSize: 16, fontWeight: '700', fontVariant: ['tabular-nums'] },
  rowText: { flex: 1, gap: 1 },
  rowTitle: { color: C.ink, fontSize: 16, fontWeight: '600' },
  rowDetail: { color: C.mute, fontSize: 14, fontWeight: '400' },
  rowCost: { color: C.ink, fontSize: 16, fontWeight: '500', fontVariant: ['tabular-nums'] },
  chip: {
    position: 'absolute',
    height: CHIP_H,
    borderRadius: CHIP_H / 2,
    paddingHorizontal: 14,
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: C.field,
    borderWidth: StyleSheet.hairlineWidth,
    borderColor: C.line,
    boxShadow: '0 1px 2px rgba(29,28,26,0.08), 0 8px 20px rgba(29,28,26,0.07), inset 0 1px 0 rgba(255,255,255,0.9)',
  },
  chipStrong: { color: C.ink, fontSize: 15, fontWeight: '700' },
  chipAmount: { color: C.ink, fontSize: 15, fontWeight: '700', fontVariant: ['tabular-nums'], width: 32, padding: 0, margin: 0 },
  chipSoft: { color: C.mute, fontSize: 15, fontWeight: '500', fontVariant: ['tabular-nums'] },
  chipDot: { width: 3, height: 3, borderRadius: 1.5, backgroundColor: C.mute, marginHorizontal: 9, opacity: 0.7 },
  chipSpare: { color: C.accent, fontSize: 15, fontWeight: '600', fontVariant: ['tabular-nums'] },
  header: {
    position: 'absolute',
    left: 0,
    right: 0,
    top: 0,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: 14,
    backgroundColor: C.page,
    borderBottomWidth: StyleSheet.hairlineWidth,
    borderBottomColor: C.line,
  },
  headerSide: { width: 44, height: 44, justifyContent: 'center' },
  headerRight: { alignItems: 'flex-end' },
  headerTitle: { color: C.ink, fontSize: 17, fontWeight: '600' },
  composerWrap: { position: 'absolute', left: 0, right: 0, bottom: 0 },
  composerFade: { height: 26 },
  composerBack: { backgroundColor: C.page, height: COMPOSER_H - 26 + 8, alignItems: 'center', justifyContent: 'flex-start' },
  composer: {
    height: 46,
    marginTop: -8,
    borderRadius: 23,
    backgroundColor: C.field,
    borderWidth: StyleSheet.hairlineWidth,
    borderColor: C.line,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingLeft: 18,
    paddingRight: 7,
    boxShadow: '0 1px 2px rgba(29,28,26,0.07), 0 10px 26px rgba(29,28,26,0.09), inset 0 1px 0 rgba(255,255,255,0.9)',
  },
  placeholder: { color: C.mute, fontSize: 16, fontWeight: '400' },
  send: { width: 32, height: 32, borderRadius: 16, backgroundColor: C.accent, alignItems: 'center', justifyContent: 'center' },
});
