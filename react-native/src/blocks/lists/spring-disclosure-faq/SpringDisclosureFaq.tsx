import { Canvas, Fill, Shader, Skia } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { SymbolView } from 'expo-symbols';
import { useCallback, useEffect, useState } from 'react';
import { PixelRatio, ScrollView, StyleSheet, Text, useWindowDimensions, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  interpolateColor,
  useAnimatedStyle,
  useFrameCallback,
  useSharedValue,
  withTiming,
  type SharedValue,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

import { useReducedMotionPreference } from '@/shared/useReducedMotionPreference';

import {
  EVENT_ROW,
  EVENT_T,
  GROUP_SIZE,
  LINE_BLUR,
  LINE_RISE,
  LINE_STAGGER,
  LOOP,
  MODE_AUTO,
  MODE_MANUAL,
  MODE_RETURN,
  ROW_COUNT,
  clamp01,
  lineReveal,
  openAt,
  pushAt,
  restTransitions,
  retarget,
  touchAlpha,
  touchPress,
  type Transitions,
} from './faqMotion';

export { LOOP };

// ---------------------------------------------------------------- content

type Faq = { q: string; a: string };

const GROUPS: { title: string; items: Faq[] }[] = [
  {
    title: 'Cards',
    items: [
      {
        q: 'How do I freeze my card?',
        a: 'Open Cards, pick the card and switch on Freeze. Payments stop straight away, and you can unfreeze it the same way when it turns up.',
      },
      {
        q: 'What are my daily limits?',
        a: 'You can spend up to £5,000 and take out £400 in cash each day. Change either one under Cards, then Limits.',
      },
      {
        q: 'Why was my payment declined?',
        a: 'Usually the card is frozen, a limit was reached, or the shop asked for a check we could not confirm. The reason sits next to the payment in Activity.',
      },
    ],
  },
  {
    title: 'Transfers and account',
    items: [
      {
        q: 'When will my transfer arrive?',
        a: 'Transfers to UK accounts arrive within two hours, most of them in seconds. Payments abroad take one to three working days.',
      },
      {
        q: 'Are there fees abroad?',
        a: 'Paying by card abroad is free, at the exchange rate of the day. Cash is free up to £200 a month, then 2% of what you take out.',
      },
      {
        q: 'How do I close my account?',
        a: 'Move your money out, then go to Profile, Account, Close account. We keep your statements available for six years.',
      },
    ],
  },
];
const ITEMS: Faq[] = [...GROUPS[0].items, ...GROUPS[1].items];

// ---------------------------------------------------------------- look and layout (pt)

const PAGE = '#F1EFE9';
const CARD = '#FCFBF8';
const INK = '#1B211E';
const MUTE = '#69706B';
const BODY = '#414A45'; // answers: darker than the labels, they are what gets read
const OPEN_TINT = 'rgba(43,106,77,0.07)'; // behind the open question and its answer
const ACCENT = '#2B6A4D';
const HAIRLINE = 'rgba(27,33,30,0.10)';
const FIELD = 'rgba(27,33,30,0.06)';

const SIDE = 16; // screen margin
const ROW_H = 56; // a closed row
const LABEL_H = 30; // a group's title above its card
const GROUP_GAP = 22; // between the first card and the second title
const FOOT_GAP = 20; // between the second card and the contact row
const FOOT_H = 64;
const CARD_BASE = GROUP_SIZE * ROW_H;
const CARD2_TOP = LABEL_H + CARD_BASE + GROUP_GAP + LABEL_H;
const FOOT_TOP = CARD2_TOP + CARD_BASE + FOOT_GAP;
const ANSWER_X = 16; // answer text inset, left
const ANSWER_RIGHT = 20;
const ANSWER_TOP = 0; // answer text starts right under the question row
const ANSWER_BOTTOM = 18; // space under the last line
const ANSWER_FONT = 16;
const ANSWER_LINE = 22; // line height asked for; the measured one is used
const DOT = 44; // the ghost finger
const DOT_X = 0.62; // its place across the card

const IDLE_S = 2; // after the last touch, before the rows ease back to rest
const RETURN_S = 1.1; // the ease back, then the film restarts at t = 0

/** Rows before measuring: about 41 characters to a line. */
function estimateHeight(text: string) {
  return Math.ceil(text.length / 41) * ANSWER_LINE + ANSWER_TOP + ANSWER_BOTTOM;
}
const HEIGHTS_ESTIMATE = ITEMS.map((it) => estimateHeight(it.a));
const REST: Transitions = restTransitions();

/** Haptics live on the JS thread; worklets schedule this. */
function selectionTick() {
  void Haptics.selectionAsync();
}

// A static paper grain over the page so the flat colour does not look vector-made.
const GRAIN = Skia.RuntimeEffect.Make(`
uniform float dpr;
float h21(float2 p) { return fract(sin(dot(p, float2(127.1, 311.7))) * 43758.5453); }
half4 main(float2 p) {
  float n = h21(floor(p * dpr)) - 0.5;
  float a = abs(n) * 0.07;
  return n > 0.0 ? half4(a) : half4(0.0, 0.0, 0.0, a);
}
`);
const DPR = PixelRatio.get();

/** The shared values every moving part reads. */
type Motion = {
  mode: SharedValue<number>;
  tr: SharedValue<Transitions>;
  tau: SharedValue<number>;
  heights: SharedValue<number[]>;
  reduced: boolean;
};

/** Top of row `index` inside the stage at time tau (pt). */
function rowTopAt(mode: number, tr: Transitions, reduced: boolean, heights: number[], index: number, tau: number) {
  'worklet';
  const first = index < GROUP_SIZE ? 0 : GROUP_SIZE;
  const cardTop = index < GROUP_SIZE ? LABEL_H : CARD2_TOP + pushAt(mode, tr, reduced, heights, 0, GROUP_SIZE - 1, GROUP_SIZE, tau);
  return cardTop + (index - first) * ROW_H + pushAt(mode, tr, reduced, heights, first, index - 1, index, tau);
}

type Props = { autoPlay?: boolean };

/**
 * A help centre whose questions open on a spring. One row is open at a time: its answer's lines rise
 * out of a blur one after another, the "+" turns into a "−", the rows below are pushed down each a
 * beat after the one above, and the row that was open closes in the same move.
 */
export function SpringDisclosureFaq({ autoPlay = true }: Props) {
  const { width, height } = useWindowDimensions();
  const insets = useSafeAreaInsets();
  const reduced = useReducedMotionPreference();
  const film = autoPlay && !reduced;
  const compact = height < 740;

  const contentW = Math.min(width, 480);
  const cardW = contentW - SIDE * 2;
  const answerW = cardW - ANSWER_X - ANSWER_RIGHT;

  // Each answer is laid out once by a hidden Text; its lines are then drawn one by one.
  const [measured, setMeasured] = useState<Record<number, { key: string; lines: string[]; lineH: number }>>({});
  const measureKey = `${answerW}`;
  const onMeasured = useCallback(
    (index: number, lines: string[], lineH: number) => {
      setMeasured((prev) => {
        const old = prev[index];
        if (old && old.key === measureKey && old.lines.length === lines.length && old.lineH === lineH) return prev;
        return { ...prev, [index]: { key: measureKey, lines, lineH } };
      });
    },
    [measureKey],
  );

  const [expanded, setExpanded] = useState(0); // for screen readers; -1 = none

  const mode = useSharedValue(film ? MODE_AUTO : MODE_MANUAL);
  const tr = useSharedValue<Transitions>(REST);
  const tau = useSharedValue(0); // loop time in the film, the frame clock under a finger
  const clock = useSharedValue(0); // the frame clock, seconds
  const loopStart = useSharedValue(-1);
  const lastTouch = useSharedValue(0);
  const returnAt = useSharedValue(0);
  const ready = useSharedValue(false); // answers measured: the film may start
  const heights = useSharedValue<number[]>(HEIGHTS_ESTIMATE);

  useEffect(() => {
    const next: number[] = [];
    let all = true;
    for (let j = 0; j < ROW_COUNT; j++) {
      const m = measured[j];
      if (m && m.key === measureKey) next.push(m.lines.length * m.lineH + ANSWER_TOP + ANSWER_BOTTOM);
      else {
        next.push(HEIGHTS_ESTIMATE[j]);
        all = false;
      }
    }
    heights.set(next);
    if (all) ready.set(true);
  }, [heights, measured, measureKey, ready]);

  const onRest = useCallback(() => setExpanded(0), []);

  useFrameCallback((frame) => {
    'worklet';
    const now = frame.timestamp / 1000;
    clock.set(now);
    const m = mode.get();
    if (m === MODE_AUTO) {
      if (!film) {
        tr.set(restTransitions());
        mode.set(MODE_MANUAL);
        tau.set(now);
        return;
      }
      if (!ready.get()) {
        tau.set(0);
        return;
      }
      if (loopStart.get() < 0) loopStart.set(now);
      tau.set((now - loopStart.get()) % LOOP);
      return;
    }
    tau.set(now);
    if (!film) return;
    if (m === MODE_MANUAL && now - lastTouch.get() > IDLE_S) {
      tr.set(retarget(MODE_MANUAL, tr.get(), reduced, now, now, -1));
      returnAt.set(now);
      mode.set(MODE_RETURN);
      scheduleOnRN(onRest);
      return;
    }
    if (m === MODE_RETURN && now - returnAt.get() > RETURN_S) {
      loopStart.set(now);
      tau.set(0);
      mode.set(MODE_AUTO);
    }
  });

  const onToggled = useCallback((index: number, opened: boolean) => {
    selectionTick();
    setExpanded(opened ? index : -1);
  }, []);

  /** A finger on a row: the film stops and the row toggles from wherever everything is. */
  const touch = useCallback(() => {
    'worklet';
    lastTouch.set(clock.get());
  }, [clock, lastTouch]);

  const toggle = useCallback(
    (index: number) => {
      'worklet';
      const now = clock.get();
      const next = retarget(mode.get(), tr.get(), reduced, tau.get(), now, index);
      tr.set(next);
      mode.set(MODE_MANUAL);
      tau.set(now);
      lastTouch.set(now);
      scheduleOnRN(onToggled, index, next.to[index] > 0.5);
    },
    [clock, lastTouch, mode, onToggled, reduced, tau, tr],
  );

  const motion: Motion = { mode, tr, tau, heights, reduced };
  const maxH = Math.max(...HEIGHTS_ESTIMATE, ...Object.values(measured).map((m) => m.lines.length * m.lineH + ANSWER_TOP + ANSWER_BOTTOM));
  const stageH = FOOT_TOP + FOOT_H + maxH + 8;

  return (
    <View style={styles.screen}>
      <StatusBar style="dark" />
      {GRAIN ? (
        <Canvas style={StyleSheet.absoluteFill} pointerEvents="none">
          <Fill>
            <Shader source={GRAIN} uniforms={{ dpr: DPR }} />
          </Fill>
        </Canvas>
      ) : null}

      <ScrollView
        style={{ width: contentW }}
        contentContainerStyle={{ paddingTop: insets.top + (compact ? 2 : 6), paddingBottom: insets.bottom + 16, paddingHorizontal: SIDE }}
        showsVerticalScrollIndicator={false}
      >
        <View style={styles.nav}>
          <View style={styles.navButton}>
            <SymbolView name="chevron.left" size={17} weight="semibold" tintColor={INK} type="monochrome" />
          </View>
          {compact ? <Text style={styles.navTitle}>Help</Text> : null}
          <View style={styles.navButton}>
            <SymbolView name="ellipsis" size={17} weight="semibold" tintColor={INK} type="monochrome" />
          </View>
        </View>
        {compact ? null : (
          <Text style={styles.title} accessibilityRole="header">
            Help
          </Text>
        )}

        <View style={[styles.search, { marginTop: compact ? 8 : 14 }]}>
          <SymbolView name="magnifyingglass" size={16} weight="medium" tintColor={MUTE} type="monochrome" />
          <Text style={styles.searchText}>Search Alder help</Text>
        </View>

        <View style={{ height: stageH, marginTop: compact ? 14 : 20 }}>
          <Text style={[styles.label, { top: 0 }]}>{GROUPS[0].title}</Text>
          <Card group={0} width={cardW} motion={motion}>
            {GROUPS[0].items.map((it, i) => (
              <Row key={it.q} index={i} item={it} measured={measured[i]} expanded={expanded === i} motion={motion} onToggle={toggle} onTouch={touch} />
            ))}
          </Card>

          <Pushed target={GROUP_SIZE} top={LABEL_H + CARD_BASE + GROUP_GAP} motion={motion}>
            <Text style={styles.labelText}>{GROUPS[1].title}</Text>
          </Pushed>
          <Card group={1} width={cardW} motion={motion}>
            {GROUPS[1].items.map((it, i) => (
              <Row
                key={it.q}
                index={GROUP_SIZE + i}
                item={it}
                measured={measured[GROUP_SIZE + i]}
                expanded={expanded === GROUP_SIZE + i}
                motion={motion}
                onToggle={toggle}
                onTouch={touch}
              />
            ))}
          </Card>

          <Pushed target={ROW_COUNT} top={FOOT_TOP} motion={motion}>
            <View style={[styles.foot, { width: cardW }]}>
              <View style={styles.footIcon}>
                <SymbolView name="bubble.left.fill" size={17} tintColor={CARD} type="monochrome" />
              </View>
              <View style={styles.footText}>
                <Text style={styles.footTitle}>Chat with us</Text>
                <Text style={styles.footNote}>Replies in about 4 minutes</Text>
              </View>
              <SymbolView name="chevron.right" size={13} weight="semibold" tintColor={MUTE} type="monochrome" />
            </View>
          </Pushed>

          <GhostTouch x={cardW * DOT_X} motion={motion} />

          {/* The answers, laid out once and never shown: they give each answer its lines. */}
          <View style={[styles.measure, { width: answerW }]} pointerEvents="none" accessibilityElementsHidden importantForAccessibility="no-hide-descendants">
            {ITEMS.map((it, i) => (
              <Text
                key={`${measureKey}-${it.q}`}
                style={styles.answer}
                onTextLayout={(e) => {
                  const ls = e.nativeEvent.lines;
                  if (ls.length > 0) onMeasured(i, ls.map((l) => l.text.replace(/\s+$/, '')), ls[0].height > 0 ? ls[0].height : ANSWER_LINE);
                }}
              >
                {it.a}
              </Text>
            ))}
          </View>
        </View>
      </ScrollView>
    </View>
  );
}

// ---------------------------------------------------------------- the grouped card

/**
 * One grouped card. Its plate (background, shadows) and its clip grow with the answers inside it; the
 * second card is also pushed down by the first one's. Only this height is a layout animation: the
 * card holds three absolutely placed rows, so nothing else is laid out again.
 */
function Card({ group, width, motion, children }: { group: number; width: number; motion: Motion; children: React.ReactNode }) {
  const { mode, tr, tau, heights, reduced } = motion;
  const first = group * GROUP_SIZE;
  const last = first + GROUP_SIZE - 1;
  const top = group === 0 ? LABEL_H : CARD2_TOP;
  const style = useAnimatedStyle(() => {
    const m = mode.get();
    const t = tr.get();
    const h = heights.get();
    const now = tau.get();
    const shift = group === 0 ? 0 : pushAt(m, t, reduced, h, 0, GROUP_SIZE - 1, GROUP_SIZE, now);
    // the bottom edge travels with the last row: its own answer plus what pushes it
    const grown = pushAt(m, t, reduced, h, first, last - 1, last, now) + h[last] * openAt(m, t, reduced, last, now);
    return { height: Math.max(CARD_BASE, CARD_BASE + grown), transform: [{ translateY: shift }] };
  });
  return (
    <>
      <Animated.View style={[styles.plate, { top, width }, style]} />
      <Animated.View style={[styles.clip, { top, width }, style]}>{children}</Animated.View>
    </>
  );
}

/** Something under the rows (the second title, the contact row) that the answers push down. */
function Pushed({ target, top, motion, children }: { target: number; top: number; motion: Motion; children: React.ReactNode }) {
  const { mode, tr, tau, heights, reduced } = motion;
  const style = useAnimatedStyle(() => {
    const m = mode.get();
    const t = tr.get();
    const h = heights.get();
    const now = tau.get();
    let shift = pushAt(m, t, reduced, h, 0, GROUP_SIZE - 1, GROUP_SIZE, now);
    if (target > GROUP_SIZE) shift += pushAt(m, t, reduced, h, GROUP_SIZE, ROW_COUNT - 1, ROW_COUNT, now);
    return { transform: [{ translateY: shift }] };
  });
  return <Animated.View style={[styles.pushed, { top }, style]}>{children}</Animated.View>;
}

// ---------------------------------------------------------------- a question

type RowProps = {
  index: number;
  item: Faq;
  measured?: { lines: string[]; lineH: number };
  expanded: boolean;
  motion: Motion;
  onToggle: (index: number) => void;
  onTouch: () => void;
};

function Row({ index, item, measured, expanded, motion, onToggle, onTouch }: RowProps) {
  const { mode, tr, tau, heights, reduced } = motion;
  const first = index < GROUP_SIZE ? 0 : GROUP_SIZE;
  const local = index - first;
  const pressed = useSharedValue(0);

  const tap = Gesture.Tap()
    .maxDistance(12)
    .onBegin(() => {
      onTouch();
      pressed.set(withTiming(1, { duration: 90 }));
    })
    .onEnd((_e, success) => {
      if (success) onToggle(index);
    })
    .onFinalize(() => {
      onTouch();
      pressed.set(withTiming(0, { duration: 240 }));
    });

  const rowStyle = useAnimatedStyle(() => {
    const y = local * ROW_H + pushAt(mode.get(), tr.get(), reduced, heights.get(), first, index - 1, index, tau.get());
    return { transform: [{ translateY: y }] };
  });

  const tintStyle = useAnimatedStyle(() => {
    let p = pressed.get();
    if (mode.get() === MODE_AUTO) {
      const t = tau.get();
      for (let k = 0; k < EVENT_T.length; k++) {
        if (EVENT_ROW[k] === index) p = Math.max(p, touchPress(k, t));
      }
    }
    return { opacity: p };
  });

  const questionStyle = useAnimatedStyle(() => {
    const o = clamp01(openAt(mode.get(), tr.get(), reduced, index, tau.get()));
    return { color: interpolateColor(o, [0, 1], [INK, ACCENT]) };
  });

  // the upright stroke of the "+" lies down onto the other one and fades: "−"
  const strokeStyle = useAnimatedStyle(() => {
    const o = openAt(mode.get(), tr.get(), reduced, index, tau.get());
    return { opacity: 1 - clamp01(o), transform: [{ rotate: `${90 * o}deg` }] };
  });

  const plateStyle = useAnimatedStyle(() => ({ opacity: clamp01(openAt(mode.get(), tr.get(), reduced, index, tau.get())) }));
  // the bar grows down from the question's top edge with the same spring (overshoot included)
  const barStyle = useAnimatedStyle(() => {
    const o = openAt(mode.get(), tr.get(), reduced, index, tau.get());
    return { opacity: clamp01(o * 2), transform: [{ scaleY: Math.max(0, o) }] };
  });

  const lines = measured ? measured.lines : [];
  const openH = ROW_H + (measured ? measured.lines.length * measured.lineH + ANSWER_TOP + ANSWER_BOTTOM : estimateHeight(item.a));
  const lineH = measured ? measured.lineH : ANSWER_LINE;

  return (
    <GestureDetector gesture={tap}>
      <Animated.View
        style={[styles.row, rowStyle]}
        accessible
        accessibilityRole="button"
        accessibilityState={{ expanded }}
        accessibilityLabel={expanded ? `${item.q} ${item.a}` : item.q}
      >
        {local > 0 ? <View style={styles.rule} /> : null}
        {/* the open row's plate and accent bar reach down behind its answer; the row below covers what is not open */}
        <Animated.View pointerEvents="none" style={[styles.openPlate, { height: openH }, plateStyle]} />
        <Animated.View pointerEvents="none" style={[styles.openBar, { height: openH }, barStyle]} />
        <Animated.View style={[styles.tint, tintStyle]} />
        <Animated.Text style={[styles.question, questionStyle]} numberOfLines={1} maxFontSizeMultiplier={1.2}>
          {item.q}
        </Animated.Text>
        <View style={styles.sign}>
          <View style={styles.stroke} />
          <Animated.View style={[styles.stroke, styles.strokeUp, strokeStyle]} />
        </View>
        <View style={styles.answerBox} pointerEvents="none">
          {lines.map((text, k) => (
            <AnswerLine key={`${k}-${text}`} row={index} k={k} text={text} lineH={lineH} motion={motion} />
          ))}
        </View>
      </Animated.View>
    </GestureDetector>
  );
}

/** One line of an answer: it samples its row's `open` a little later than the line above. */
function AnswerLine({ row, k, text, lineH, motion }: { row: number; k: number; text: string; lineH: number; motion: Motion }) {
  const { mode, tr, tau, reduced } = motion;
  const style = useAnimatedStyle(() => {
    const lag = reduced ? 0 : LINE_STAGGER * k;
    const c = lineReveal(openAt(mode.get(), tr.get(), reduced, row, tau.get() - lag));
    if (reduced) return { opacity: c };
    const moving = c > 0 && c < 1;
    return {
      opacity: c,
      transform: [{ translateY: (1 - c) * LINE_RISE }],
      filter: [{ blur: moving ? (1 - c) * LINE_BLUR : 0 }],
    };
  });
  return (
    <Animated.View style={[{ height: lineH }, style]}>
      <Text style={styles.answer} numberOfLines={1} ellipsizeMode="clip">
        {text}
      </Text>
    </Animated.View>
  );
}

// ---------------------------------------------------------------- the ghost finger

/** In the film a soft dot shows where the finger taps; it stays where it landed while the rows move. */
function GhostTouch({ x, motion }: { x: number; motion: Motion }) {
  const { mode, tr, tau, heights, reduced } = motion;
  const style = useAnimatedStyle(() => {
    const m = mode.get();
    if (m !== MODE_AUTO) return { opacity: 0, transform: [{ translateX: x - DOT / 2 }, { translateY: 0 }, { scale: 1 }] };
    const t = tau.get();
    let best = 0;
    let alpha = 0;
    for (let k = 0; k < EVENT_T.length; k++) {
      const a = touchAlpha(k, t);
      if (a > alpha) {
        alpha = a;
        best = k;
      }
    }
    const y = rowTopAt(m, tr.get(), reduced, heights.get(), EVENT_ROW[best], EVENT_T[best]) + ROW_H / 2;
    return {
      opacity: alpha,
      transform: [{ translateX: x - DOT / 2 }, { translateY: y - DOT / 2 }, { scale: 1 - 0.16 * touchPress(best, t) }],
    };
  });
  return <Animated.View pointerEvents="none" style={[styles.dot, style]} />;
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: PAGE, alignItems: 'center' },
  nav: { height: 44, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  navButton: {
    width: 36,
    height: 36,
    borderRadius: 18,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: CARD,
    boxShadow: '0 1px 2px rgba(27,33,30,0.10), 0 6px 16px rgba(27,33,30,0.06)',
  },
  navTitle: { color: INK, fontSize: 17, fontWeight: '600' },
  title: { color: INK, fontSize: 34, fontWeight: '700', letterSpacing: 0.3, marginTop: 6 },
  search: {
    height: 44,
    borderRadius: 14,
    borderCurve: 'continuous',
    backgroundColor: FIELD,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
    paddingHorizontal: 12,
  },
  searchText: { color: MUTE, fontSize: 17 },
  label: { position: 'absolute', left: 4, color: MUTE, fontSize: 13, fontWeight: '600' },
  labelText: { marginLeft: 4, color: MUTE, fontSize: 13, fontWeight: '600' },
  pushed: { position: 'absolute', left: 0 },
  plate: {
    position: 'absolute',
    left: 0,
    borderRadius: 20,
    borderCurve: 'continuous',
    backgroundColor: CARD,
    borderWidth: StyleSheet.hairlineWidth,
    borderColor: 'rgba(255,255,255,0.9)',
    boxShadow: '0 1px 2px rgba(27,33,30,0.09), 0 12px 28px rgba(27,33,30,0.07)',
  },
  clip: { position: 'absolute', left: 0, borderRadius: 20, borderCurve: 'continuous', overflow: 'hidden' },
  row: { position: 'absolute', left: 0, right: 0, top: 0, height: ROW_H, backgroundColor: CARD, justifyContent: 'center' },
  rule: { position: 'absolute', left: ANSWER_X, right: 0, top: 0, height: StyleSheet.hairlineWidth, backgroundColor: HAIRLINE },
  openPlate: { position: 'absolute', left: 0, right: 0, top: 0, backgroundColor: OPEN_TINT },
  openBar: {
    position: 'absolute',
    left: 0,
    top: 0,
    width: 3,
    borderTopRightRadius: 2,
    borderBottomRightRadius: 2,
    backgroundColor: ACCENT,
    transformOrigin: 'top',
  },
  tint: { position: 'absolute', left: 0, right: 0, top: 0, bottom: 0, backgroundColor: 'rgba(27,33,30,0.05)' },
  question: { marginLeft: ANSWER_X, marginRight: 56, fontSize: 16, fontWeight: '600', letterSpacing: -0.2 },
  sign: {
    position: 'absolute',
    right: 14,
    top: (ROW_H - 28) / 2,
    width: 28,
    height: 28,
    borderRadius: 14,
    backgroundColor: FIELD,
    alignItems: 'center',
    justifyContent: 'center',
  },
  stroke: { position: 'absolute', width: 12, height: 2, borderRadius: 1, backgroundColor: INK },
  strokeUp: { width: 2, height: 12 },
  // under the question row; the row below (or the card's edge) covers what is not open yet
  answerBox: { position: 'absolute', left: ANSWER_X, right: ANSWER_RIGHT, top: ROW_H + ANSWER_TOP },
  answer: { color: BODY, fontSize: ANSWER_FONT, lineHeight: ANSWER_LINE, letterSpacing: -0.1 },
  measure: { position: 'absolute', left: 0, top: 0, opacity: 0 },
  foot: {
    height: FOOT_H,
    borderRadius: 20,
    borderCurve: 'continuous',
    backgroundColor: CARD,
    borderWidth: StyleSheet.hairlineWidth,
    borderColor: 'rgba(255,255,255,0.9)',
    boxShadow: '0 1px 2px rgba(27,33,30,0.09), 0 12px 28px rgba(27,33,30,0.07)',
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 14,
    gap: 12,
  },
  footIcon: { width: 36, height: 36, borderRadius: 18, backgroundColor: ACCENT, alignItems: 'center', justifyContent: 'center' },
  footText: { flex: 1 },
  footTitle: { color: INK, fontSize: 16, fontWeight: '600', letterSpacing: -0.2 },
  footNote: { color: MUTE, fontSize: 13, marginTop: 1 },
  dot: {
    position: 'absolute',
    left: 0,
    top: 0,
    width: DOT,
    height: DOT,
    borderRadius: DOT / 2,
    backgroundColor: 'rgba(27,33,30,0.14)',
    borderWidth: 1.5,
    borderColor: 'rgba(255,255,255,0.75)',
  },
});
