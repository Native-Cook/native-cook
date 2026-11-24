// "Folio": a page of study notes that a marker annotates by itself, on real measured text.
// The geometry is ./strokes (built once per layout), the text measurement ./page, the finger's highlights ./marks.
import { BlendMode, Canvas, PaintStyle, Paragraph, Picture, RoundedRect, Shadow, Skia, StrokeCap } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useEffect, useMemo, useState } from 'react';
import { Platform, Pressable, StyleSheet, Text, useWindowDimensions, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, { useAnimatedStyle, useDerivedValue, useFrameCallback, useSharedValue, type SharedValue } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

import { useReducedMotionPreference } from '@/shared/useReducedMotionPreference';

import { COPY, MARKED, TERMS, TEXT, TINTS } from './data';
import { emptyMarks, liftMarks, markOver, putMark, S_AMOUNT, S_FIRST, S_LAST, S_STAMP, S_TINT, S_USED, SLOT, stepMarks, takeSlot } from './marks';
import {
  BAND,
  BRACE,
  CATCH_UP,
  DRAW,
  easeInOutCubic,
  GHOST,
  ghostAt,
  HOLD,
  LIFT_START,
  LOOP,
  MARK,
  markAt,
  mix,
  NOTE,
  parkAt,
  RESUME_AFTER,
  seg,
  TAP_SLOP,
  USER_MARKS,
} from './motion';
import { makePage } from './page';
import { arrowMark, bandHead, bracketMark, circleMark, drawHighlight, drawInk, flatBands, noteMark, underlineMark, wordAt, wordBands } from './strokes';

export { LOOP };
export type ScribbleEmphasisNotesProps = {
  /** Plays the 9 s loop until a finger takes the page; it carries on 2 s after the last touch. */
  autoPlay?: boolean;
};

// Palette: warm paper, one ink, two pens.
const DESK = '#ECE7DD';
const PAPER = '#FBF8F1';
const INK = '#24211C';
const INK_SOFT = '#8A8273';
const HAIRLINE = 'rgba(36,33,28,0.09)';
const PEN_RED = '#D5432E';
const PEN_BLUE = '#2D4FCB';

// Layout, in points.
const SIDE = 16;
const MAX_WIDTH = 560;
const HEADER = 98; // brand row, title and meta
const CARD_RADIUS = 22;
const CARD_PAD = 20;
const CARD_HEAD = 46; // the "Key idea" row above the paragraph
const GUTTER = 34; // right margin inside the card, where the bracket lives
const GUTTER_EDGE = 6;
const NOTE_AREA = 60; // below the paragraph, where the note is written
const NOTE_DROP = 40; // the note's baseline below the paragraph
const NOTE_BACK = 66; // the note starts this far left of the text's right edge
const TRAY_GAP = 14;
const TRAY = 58;
const ROW = 44;
const LIST_HEAD = 32;

const MODE_AUTO = 0;
const MODE_MANUAL = 1;

function tick() {
  if (Platform.OS !== 'web') Haptics.selectionAsync().catch(() => {});
}

function impact() {
  if (Platform.OS !== 'web') Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
}

export function ScribbleEmphasisNotes({ autoPlay = true }: ScribbleEmphasisNotesProps) {
  const { width, height } = useWindowDimensions();
  const insets = useSafeAreaInsets();
  const reducedMotion = useReducedMotionPreference();
  const playing = autoPlay && !reducedMotion;
  const [tint, setTint] = useState(0);

  // Where things sit. The paragraph is laid out and measured once per size.
  const column = Math.min(width, MAX_WIDTH);
  const left = (width - column) / 2 + SIDE;
  const cardWidth = column - SIDE * 2;
  const headTop = insets.top + 8;
  const cardTop = headTop + HEADER;
  const large = column >= 390;
  const fontSize = large ? 18 : 17;
  const lineHeight = large ? 29 : 27;
  const textX = left + CARD_PAD;
  const textY = cardTop + CARD_HEAD;
  const textWidth = cardWidth - CARD_PAD - GUTTER - GUTTER_EDGE;

  const page = useMemo(() => makePage(TEXT, textX, textY, textWidth, fontSize, lineHeight, INK), [textX, textY, textWidth, fontSize, lineHeight]);
  const cardHeight = CARD_HEAD + page.height + NOTE_AREA;
  const trayTop = cardTop + cardHeight + TRAY_GAP;
  const listTop = trayTop + TRAY + TRAY_GAP;
  const fit = Math.floor((height - Math.max(insets.bottom, 12) - listTop - LIST_HEAD - 8) / ROW);
  const rows = fit >= 2 ? Math.min(fit, MARKED.length) : 0;

  const art = useMemo(() => {
    const find = (phrase: string) => {
      const start = Math.max(TEXT.indexOf(phrase), 0);
      return page.bands(start, start + phrase.length);
    };
    const right = textX + textWidth;
    const sentence = Math.min(TERMS.bracketSentence, page.sentenceFirst.length - 1);
    const firstLine = page.wordLine[page.sentenceFirst[sentence]] ?? 0;
    const lastLine = page.wordLine[page.sentenceLast[sentence]] ?? firstLine;
    const bracket = bracketMark(right + BRACE.gap, page.lineBase[firstLine] - fontSize * 0.86, page.lineBase[lastLine] + fontSize * 0.26, 29);
    const noteX = right - NOTE_BACK;
    const noteBase = textY + page.height + NOTE_DROP;
    const note = noteMark('exam!', noteX, noteBase, 37);
    const noteEnd = { x: noteX + note.width * Math.cos(NOTE.tilt), y: noteBase + note.width * Math.sin(NOTE.tilt) };
    const from = { x: noteEnd.x + 3, y: noteEnd.y - NOTE.xHeight * 2.1 };
    const to = { x: bracket.nose.x + 4.5, y: bracket.nose.y + 6 };
    const circleBand = find(TERMS.circle)[0] ?? { x0: textX, x1: textX + 80, base: page.lineBase[0] ?? textY + fontSize };
    return {
      circle: circleMark(circleBand, fontSize, 11),
      highlight: flatBands(find(TERMS.highlight)),
      underline: underlineMark(find(TERMS.underline), 23),
      bracket: bracket.data,
      note: note.data,
      arrow: arrowMark([from, { x: to.x + 6, y: mix(from.y, to.y, 0.5) }, { x: to.x + 4, y: mix(from.y, to.y, 0.82) }, to], 53),
    };
  }, [page, textX, textY, textWidth, fontSize]);

  const paints = useMemo(() => {
    const pen = (color: string) => {
      const paint = Skia.Paint();
      paint.setAntiAlias(true);
      paint.setStyle(PaintStyle.Stroke);
      paint.setStrokeCap(StrokeCap.Round);
      paint.setColor(Skia.Color(color));
      return paint;
    };
    const fills = TINTS.map((entry) => {
      const paint = Skia.Paint();
      paint.setAntiAlias(true);
      paint.setColor(Skia.Color(entry.color));
      paint.setAlphaf(BAND.opacity);
      paint.setBlendMode(BlendMode.Multiply);
      return paint;
    });
    const streaks = TINTS.map((entry) => {
      const paint = Skia.Paint();
      paint.setAntiAlias(true);
      paint.setStyle(PaintStyle.Stroke);
      paint.setStrokeCap(StrokeCap.Butt);
      paint.setColor(Skia.Color(entry.color));
      paint.setAlphaf(BAND.streak);
      paint.setBlendMode(BlendMode.Multiply);
      return paint;
    });
    return { red: pen(PEN_RED), blue: pen(PEN_BLUE), fills, streaks };
  }, []);

  // One clock. `cap` freezes the drawing where a finger took over (LOOP = no cap).
  const clock = useSharedValue(reducedMotion ? HOLD : 0);
  const cap = useSharedValue(LOOP);
  const mode = useSharedValue(MODE_AUTO);
  const idle = useSharedValue(0);
  const touches = useSharedValue(0);
  const seen = useSharedValue(0);
  const held = useSharedValue(0);
  // The finger's highlights and the touch that is making one.
  const marks = useSharedValue<number[]>(emptyMarks());
  const stamp = useSharedValue(1);
  const tintIndex = useSharedValue(0);
  const startWord = useSharedValue(-1);
  const dragSlot = useSharedValue(-1);
  const dragWord = useSharedValue(-1);

  useEffect(() => {
    // Reduced motion: the finished page, loop stopped.
    clock.set(reducedMotion ? HOLD : 0);
    cap.set(LOOP);
    mode.set(MODE_AUTO);
  }, [reducedMotion, clock, cap, mode]);

  useFrameCallback((frame) => {
    const dt = Math.min(frame.timeSincePreviousFrame ?? 16, 48) / 1000;
    if (dt <= 0) return;
    const stepped = stepMarks(marks.get(), dt, reducedMotion);
    if (stepped) marks.set(stepped);
    if (reducedMotion) return;

    // A touch takes the page: the pen finishes the mark it is on and stops.
    if (touches.get() !== seen.get()) {
      seen.set(touches.get());
      idle.set(0);
      if (mode.get() === MODE_AUTO) {
        mode.set(MODE_MANUAL);
        cap.set(Math.min(cap.get(), parkAt(clock.get())));
      }
    }
    let t = clock.get();
    if (mode.get() === MODE_MANUAL) {
      const limit = cap.get();
      if (t >= LIFT_START) {
        // Caught lifting off: let it finish, then wait on the clean page.
        t += dt;
        if (t >= LOOP) {
          t = 0;
          cap.set(0);
        }
        clock.set(t);
      } else if (t < limit) {
        clock.set(Math.min(limit, t + dt * CATCH_UP));
      }
      if (!playing) return;
      if (held.get() === 0) idle.set(idle.get() + dt);
      if (idle.get() >= RESUME_AFTER && t < LIFT_START) {
        marks.set(liftMarks(marks.get(), -1));
        if (limit > DRAW[0][0] && t > 0) {
          // Something is on the page: lift it off. The jump cannot be seen, since the drawing reads the cap.
          clock.set(LIFT_START);
        } else {
          cap.set(LOOP);
        }
        mode.set(MODE_AUTO);
      }
      return;
    }
    if (!playing) return;
    t += dt;
    if (t >= LOOP) {
      t -= LOOP;
      cap.set(LOOP);
    }
    clock.set(t);
  });

  const { wordX, wordW, wordLine, wordSentence, sentenceFirst, sentenceLast, lineBase, lineLeft, lineRight } = page;
  const textBottom = textY + page.height;

  const gesture = Gesture.Pan()
    .minDistance(0)
    .maxPointers(1)
    .onBegin((event) => {
      touches.set(touches.get() + 1);
      held.set(1);
      dragSlot.set(-1);
      const y = event.y + cardTop;
      const inside = y > textY - 10 && y < textBottom + 10;
      const word = inside ? wordAt(event.x + left, y, fontSize, wordX, wordW, wordLine, lineBase) : -1;
      startWord.set(word);
      dragWord.set(word);
    })
    .onUpdate((event) => {
      const from = startWord.get();
      if (from < 0) return;
      if (dragSlot.get() < 0) {
        if (Math.hypot(event.translationX, event.translationY) < TAP_SLOP) return;
        // The touch has become a drag: a highlight starts under the first word and follows the finger.
        const list = marks.get();
        const slot = takeSlot(list);
        stamp.set(stamp.get() + 1);
        marks.set(putMark(list, slot, from, from, 1, tintIndex.get(), stamp.get()));
        dragSlot.set(slot);
        scheduleOnRN(impact);
      }
      const word = wordAt(event.x + left, event.y + cardTop, fontSize, wordX, wordW, wordLine, lineBase);
      if (word < 0 || word === dragWord.get()) return;
      dragWord.set(word);
      const list = marks.get().slice();
      const o = dragSlot.get() * SLOT;
      list[o + S_FIRST] = Math.min(from, word);
      list[o + S_LAST] = Math.max(from, word);
      marks.set(list);
      scheduleOnRN(tick);
    })
    .onFinalize(() => {
      held.set(0);
      idle.set(0);
      const word = startWord.get();
      startWord.set(-1);
      if (word < 0 || dragSlot.get() >= 0) return;
      // A tap: the sentence's highlight is swiped on, or the highlight under the finger lifts off.
      const list = marks.get();
      const over = markOver(list, word);
      if (over >= 0) {
        marks.set(liftMarks(list, over));
      } else {
        const sentence = wordSentence[word];
        stamp.set(stamp.get() + 1);
        marks.set(putMark(list, takeSlot(list), sentenceFirst[sentence], sentenceLast[sentence], 0, tintIndex.get(), stamp.get()));
      }
      scheduleOnRN(impact);
    });

  const picture = useDerivedValue(() => {
    const t = clock.get();
    const limit = cap.get();
    const list = marks.get();
    const recorder = Skia.PictureRecorder();
    const canvas = recorder.beginRecording(Skia.XYWHRect(0, 0, width, height));
    // Highlighter first (multiplied over the printed text), pens on top.
    for (let i = 0; i < USER_MARKS; i += 1) {
      const o = i * SLOT;
      if (list[o + S_USED] === 0) continue;
      const bands = wordBands(list[o + S_FIRST], list[o + S_LAST], wordX, wordW, wordLine, lineLeft, lineRight, lineBase);
      const ink = list[o + S_TINT];
      drawHighlight(canvas, paints.fills[ink], paints.streaks[ink], bands, fontSize, 100 + list[o + S_STAMP] * 17, easeInOutCubic(list[o + S_AMOUNT]));
    }
    drawHighlight(canvas, paints.fills[0], paints.streaks[0], art.highlight, fontSize, 7, markAt(t, limit, MARK.highlight));
    drawInk(canvas, paints.red, art.circle, markAt(t, limit, MARK.circle));
    drawInk(canvas, paints.blue, art.underline, markAt(t, limit, MARK.underline));
    drawInk(canvas, paints.red, art.bracket, markAt(t, limit, MARK.bracket));
    drawInk(canvas, paints.red, art.note, markAt(t, limit, MARK.note));
    drawInk(canvas, paints.red, art.arrow, markAt(t, limit, MARK.arrow));
    return recorder.finishRecordingAsPicture();
  });

  const ghost = useAnimatedStyle(() => {
    const t = clock.get();
    const limit = cap.get();
    const head = bandHead(art.highlight, markAt(t, limit, MARK.highlight));
    const shown = mode.get() === MODE_AUTO && limit >= LOOP && playing ? ghostAt(t) : 0;
    const pressed = seg(t, GHOST.shown, DRAW[MARK.highlight][0]) * (1 - seg(t, GHOST.lift, GHOST.gone));
    return {
      opacity: shown,
      transform: [
        { translateX: head[0] - GHOST.size / 2 },
        { translateY: head[1] - fontSize * 0.3 - GHOST.size / 2 },
        { scale: 1 - 0.14 * pressed },
      ],
    };
  });

  const pickTint = (index: number) => {
    touches.set(touches.get() + 1);
    tintIndex.set(index);
    setTint(index);
    tick();
  };

  return (
    <View style={styles.screen}>
      <StatusBar style="dark" />
      <Canvas style={StyleSheet.absoluteFill} pointerEvents="none">
        <RoundedRect x={left} y={cardTop} width={cardWidth} height={cardHeight} r={CARD_RADIUS} color={PAPER}>
          <Shadow dx={0} dy={1} blur={1.5} color="rgba(64,50,28,0.12)" />
          <Shadow dx={0} dy={12} blur={16} color="rgba(64,50,28,0.10)" />
        </RoundedRect>
        <Paragraph paragraph={page.paragraph} x={textX} y={textY} width={textWidth} />
        <Picture picture={picture} />
      </Canvas>

      <View style={[styles.header, { top: headTop, left, width: cardWidth }]} pointerEvents="none">
        <View style={styles.brandRow}>
          <Text style={styles.brand}>{COPY.brand}</Text>
          <Text style={styles.course}>{COPY.course}</Text>
        </View>
        <Text style={styles.title} accessibilityRole="header" numberOfLines={1} adjustsFontSizeToFit>
          {COPY.title}
        </Text>
        <Text style={styles.meta}>{COPY.meta}</Text>
      </View>

      <View style={[styles.cardHead, { top: cardTop, left, width: cardWidth }]} pointerEvents="none">
        <Text style={styles.label}>{COPY.label}</Text>
        <Text style={styles.pageNumber}>{COPY.page}</Text>
      </View>

      <GestureDetector gesture={gesture}>
        <View style={{ position: 'absolute', left, top: cardTop, width: cardWidth, height: cardHeight }} accessible accessibilityRole="text" accessibilityLabel={TEXT} accessibilityHint={COPY.hint} />
      </GestureDetector>

      <View style={[styles.tray, { top: trayTop, left, width: cardWidth }]}>
        <View style={styles.trayText}>
          <Text style={styles.trayTitle}>{COPY.tray}</Text>
          <Text style={styles.trayHint} numberOfLines={1}>
            {COPY.hint}
          </Text>
        </View>
        <View style={styles.swatches}>
          {TINTS.map((entry, i) => (
            <Pressable
              key={entry.key}
              onPress={() => pickTint(i)}
              hitSlop={6}
              accessibilityRole="button"
              accessibilityLabel={`${entry.name} highlighter`}
              accessibilityState={{ selected: i === tint }}
              style={[styles.swatchRing, i === tint ? styles.swatchRingOn : null]}>
              <View style={[styles.swatch, { backgroundColor: entry.color }]} />
            </Pressable>
          ))}
        </View>
      </View>

      {rows > 0 ? (
        <View style={[styles.list, { top: listTop, left, width: cardWidth }]} pointerEvents="none">
          <Text style={styles.listHead}>{COPY.marked}</Text>
          {MARKED.slice(0, rows).map((entry, i) => (
            <MarkedRow key={entry.key} term={entry.term} how={entry.how} mark={entry.mark} first={i === 0} clock={clock} cap={cap} />
          ))}
        </View>
      ) : null}

      <Animated.View pointerEvents="none" style={[styles.ghost, ghost]} />
    </View>
  );
}

/** One line of the summary: it is faint until the loop has drawn its mark. */
function MarkedRow({ term, how, mark, first, clock, cap }: { term: string; how: string; mark: number; first: boolean; clock: SharedValue<number>; cap: SharedValue<number> }) {
  const lit = useAnimatedStyle(() => ({ opacity: mix(0.42, 1, markAt(clock.get(), cap.get(), mark)) }));
  const dot = useAnimatedStyle(() => {
    const on = markAt(clock.get(), cap.get(), mark);
    return { opacity: on, transform: [{ scale: mix(0.5, 1, on) }] };
  });
  return (
    <Animated.View style={[styles.row, first ? null : styles.rowLine, lit]}>
      <View style={styles.rowDot}>
        <Animated.View style={[styles.rowDotOn, dot]} />
      </View>
      <Text style={styles.rowTerm} numberOfLines={1}>
        {term}
      </Text>
      <Text style={styles.rowHow}>{how}</Text>
    </Animated.View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: DESK },
  header: { position: 'absolute' },
  brandRow: { flexDirection: 'row', alignItems: 'baseline', justifyContent: 'space-between', paddingHorizontal: 4 },
  brand: { fontSize: 15, fontWeight: '700', color: PEN_RED, letterSpacing: -0.2 },
  course: { fontSize: 13, fontWeight: '500', color: INK_SOFT },
  title: { marginTop: 6, paddingHorizontal: 4, fontSize: 31, lineHeight: 36, fontWeight: '800', color: INK, letterSpacing: -0.7 },
  meta: { marginTop: 3, paddingHorizontal: 4, fontSize: 14, fontWeight: '500', color: INK_SOFT, fontVariant: ['tabular-nums'] },
  cardHead: {
    position: 'absolute',
    height: CARD_HEAD,
    paddingHorizontal: CARD_PAD,
    paddingTop: 6,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  label: { fontSize: 13, fontWeight: '700', color: INK, letterSpacing: -0.1 },
  pageNumber: { fontSize: 13, fontWeight: '500', color: INK_SOFT, fontVariant: ['tabular-nums'] },
  tray: {
    position: 'absolute',
    height: TRAY,
    borderRadius: 20,
    borderCurve: 'continuous',
    backgroundColor: PAPER,
    paddingLeft: CARD_PAD,
    paddingRight: 12,
    flexDirection: 'row',
    alignItems: 'center',
    boxShadow: '0 1px 2px rgba(64,50,28,0.10), 0 8px 18px rgba(64,50,28,0.07)',
  },
  trayText: { flex: 1, paddingRight: 8 },
  trayTitle: { fontSize: 15, fontWeight: '700', color: INK, letterSpacing: -0.2 },
  trayHint: { marginTop: 1, fontSize: 12.5, fontWeight: '500', color: INK_SOFT },
  swatches: { flexDirection: 'row', gap: 4 },
  swatchRing: { width: 38, height: 38, borderRadius: 19, alignItems: 'center', justifyContent: 'center', borderWidth: 2, borderColor: 'transparent' },
  swatchRingOn: { borderColor: INK },
  swatch: { width: 26, height: 26, borderRadius: 13, borderWidth: StyleSheet.hairlineWidth, borderColor: 'rgba(36,33,28,0.18)' },
  list: { position: 'absolute', paddingHorizontal: 4 },
  listHead: { height: LIST_HEAD, fontSize: 13, fontWeight: '700', color: INK_SOFT, letterSpacing: -0.1 },
  row: { height: ROW, flexDirection: 'row', alignItems: 'center' },
  rowLine: { borderTopWidth: StyleSheet.hairlineWidth, borderTopColor: HAIRLINE },
  rowDot: { width: 18, height: 18, borderRadius: 9, borderWidth: 1.5, borderColor: 'rgba(36,33,28,0.28)', alignItems: 'center', justifyContent: 'center', marginRight: 12 },
  rowDotOn: { width: 10, height: 10, borderRadius: 5, backgroundColor: PEN_RED },
  rowTerm: { flex: 1, fontSize: 16, fontWeight: '600', color: INK, letterSpacing: -0.2 },
  rowHow: { fontSize: 14, fontWeight: '500', color: INK_SOFT },
  ghost: {
    position: 'absolute',
    left: 0,
    top: 0,
    width: GHOST.size,
    height: GHOST.size,
    borderRadius: GHOST.size / 2,
    backgroundColor: 'rgba(36,33,28,0.18)',
    borderWidth: 1.5,
    borderColor: 'rgba(255,255,255,0.9)',
  },
});
