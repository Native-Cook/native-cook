import * as Haptics from 'expo-haptics';
import { useRef, useState } from 'react';
import { type LayoutChangeEvent, StyleSheet, Text, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  type SharedValue,
  cancelAnimation,
  interpolateColor,
  useAnimatedReaction,
  useAnimatedStyle,
  useFrameCallback,
  useSharedValue,
  withSpring,
} from 'react-native-reanimated';
import { scheduleOnRN } from 'react-native-worklets';

import {
  IDLE,
  LINE_LAG,
  LOOP,
  RETURN_LEN,
  ROLL_DEG,
  ROLL_RISE,
  WIDTH_LAG,
  WORDS,
  easeInOutCubic,
  ghostAt,
  letterWheel,
  mix,
  morphAmount,
  posAt,
  wrapIndex,
} from './rotatingWordMotion';

const MODE_AUTO = 0;
const MODE_TOUCH = 1;
const MODE_RETURN = 2;

/** A drag of this many font sizes moves one word. */
const DRAG_PER_WORD = 1.15;
const SPRING = { damping: 20, stiffness: 190, mass: 1 };

export type RotatingWordHeadlineProps = {
  /** Words that take turns in the bold slot. The demo uses four; the timeline is written for four. */
  words: readonly string[];
  /** Underline colour per word. */
  accents: readonly string[];
  /** Text before the slot (its own line). */
  lead: string;
  /** Text after the slot on the same line; it follows the word's width. */
  tail: string;
  /** Largest font size; shrinks once if the longest line does not fit. */
  fontSize: number;
  /** Width the headline may use. */
  maxWidth: number;
  ink: string;
  autoPlay?: boolean;
  reducedMotion?: boolean;
  /** Optional: receives the word position (0, 1, 2, … fractional while rolling) so the screen can follow it. */
  progress?: SharedValue<number>;
};

function tick() {
  Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
}

type LetterProps = {
  ch: string;
  k: number;
  i: number;
  pos: SharedValue<number>;
  fs: number;
  ink: string;
  reduced: boolean;
};

/** One character on its own little wheel: it only reads the word position, never React state. */
function Letter({ ch, k, i, pos, fs, ink, reduced }: LetterProps) {
  const style = useAnimatedStyle(() => {
    const w = letterWheel(pos.get(), k, i);
    const a = Math.abs(w);
    const rise = reduced ? 0 : w * fs * ROLL_RISE;
    const turn = reduced ? 0 : -w * ROLL_DEG;
    return {
      opacity: 1 - easeInOutCubic(Math.min(1, a * 1.15)),
      transform: [{ perspective: fs * 9 }, { translateY: rise }, { rotateX: `${turn}deg` }],
    };
  });
  return (
    <Animated.Text style={[styles.letter, { fontSize: fs, lineHeight: fs * 1.2, color: ink, letterSpacing: -fs * 0.03 }, style]}>
      {ch}
    </Animated.Text>
  );
}

type WordProps = {
  word: string;
  k: number;
  pos: SharedValue<number>;
  fs: number;
  ink: string;
  reduced: boolean;
  onWidth: (k: number, w: number) => void;
};

function Word({ word, k, pos, fs, ink, reduced, onWidth }: WordProps) {
  const chars = word.split('');
  return (
    <View style={[styles.word, { top: fs * 0.03 }]} onLayout={(e: LayoutChangeEvent) => onWidth(k, e.nativeEvent.layout.width)} pointerEvents="none">
      {chars.map((ch, i) => (
        <Letter key={`${k}-${i}`} ch={ch} k={k} i={i} pos={pos} fs={fs} ink={ink} reduced={reduced} />
      ))}
    </View>
  );
}

/**
 * The reusable piece: "<lead> / <word> <tail>" where the word rolls letter by letter on vertical wheels, its width
 * morphs so the tail slides along by transform, and a coloured underline stretches to match.
 */
export function RotatingWordHeadline({
  words,
  accents,
  lead,
  tail,
  fontSize,
  maxWidth,
  ink,
  autoPlay = true,
  reducedMotion = false,
  progress,
}: RotatingWordHeadlineProps) {
  const [fs, setFs] = useState(fontSize);
  const [slotW, setSlotW] = useState(0);
  const own = useSharedValue(0);
  const pos = progress ?? own;
  const widths = useSharedValue<number[]>(new Array(words.length).fill(0));

  const now = useSharedValue(0);
  const clock = useSharedValue(0);
  const mode = useSharedValue(MODE_AUTO);
  const lastTouch = useSharedValue(0);
  const dragging = useSharedValue(0);
  const dragFrom = useSharedValue(0);
  const returnFrom = useSharedValue(0);
  const returnTo = useSharedValue(0);
  const returnAt = useSharedValue(0);

  const auto = autoPlay && !reducedMotion;
  const gap = fs * 0.27;
  const slotH = fs * 1.26;

  /**
   * Measurement lives in a plain ref (JS only): reading a shared value back on the JS thread right after setting it
   * is not reliable, and that left the slot hidden forever. The shared value is only a mirror for the worklets.
   */
  const meas = useRef({ fs: fontSize, widths: new Array<number>(words.length).fill(0), tail: 0, shrinks: 0 });

  function settle() {
    const m = meas.current;
    if (m.fs !== fs || m.tail <= 0 || !m.widths.every((x) => x > 0)) return;
    const widest = Math.max(...m.widths);
    const line = widest + gap + m.tail;
    if (line > maxWidth + 0.5 && m.shrinks < 2) {
      // Shrink the font so the longest line fits; everything is measured again at the new size.
      const next = Math.floor((fs * maxWidth) / line);
      m.shrinks += 1;
      m.fs = next;
      m.widths = new Array<number>(words.length).fill(0);
      m.tail = 0;
      setSlotW(0);
      setFs(next);
      return;
    }
    widths.set(m.widths.slice());
    setSlotW(widest);
  }

  // A layout from a render at an older font size is ignored.
  function measureWord(k: number, w: number) {
    if (fs !== meas.current.fs) return;
    meas.current.widths[k] = w;
    settle();
  }
  function measureTail(e: LayoutChangeEvent) {
    if (fs !== meas.current.fs) return;
    meas.current.tail = e.nativeEvent.layout.width;
    settle();
  }

  useFrameCallback((frame) => {
    const dt = Math.min(frame.timeSincePreviousFrame ?? 16, 50) / 1000;
    const n = now.get() + dt;
    now.set(n);
    const m = mode.get();
    if (m === MODE_AUTO) {
      if (!auto) return;
      const t = (clock.get() + dt) % LOOP;
      clock.set(t);
      pos.set(posAt(t));
      return;
    }
    if (m === MODE_TOUCH) {
      // Without autoplay the word stays where the finger left it.
      if (auto && dragging.get() === 0 && n - lastTouch.get() > IDLE) {
        cancelAnimation(pos);
        const p = pos.get();
        returnFrom.set(p);
        returnTo.set(Math.round(p / WORDS) * WORDS);
        returnAt.set(n);
        mode.set(MODE_RETURN);
      }
      return;
    }
    const u = Math.min(1, (n - returnAt.get()) / RETURN_LEN);
    pos.set(mix(returnFrom.get(), returnTo.get(), easeInOutCubic(u)));
    if (u >= 1) {
      // Back on the first word: the loop restarts from t = 0.
      pos.set(0);
      clock.set(0);
      mode.set(MODE_AUTO);
    }
  });

  function takeOver() {
    'worklet';
    cancelAnimation(pos);
    lastTouch.set(now.get());
    mode.set(MODE_TOUCH);
  }

  // A light tick each time a new word settles under the finger.
  useAnimatedReaction(
    () => (mode.get() === MODE_TOUCH ? Math.round(pos.get()) : -9999),
    (cur, prev) => {
      if (prev !== null && prev !== -9999 && cur !== -9999 && cur !== prev) scheduleOnRN(tick);
    },
  );

  const tap = Gesture.Tap()
    .maxDuration(400)
    .onEnd(() => {
      takeOver();
      const target = Math.round(pos.get()) + 1;
      if (reducedMotion) pos.set(target);
      else pos.set(withSpring(target, SPRING));
    });

  const pan = Gesture.Pan()
    .activeOffsetY([-8, 8])
    .failOffsetX([-24, 24])
    .onStart(() => {
      takeOver();
      dragging.set(1);
      dragFrom.set(pos.get());
    })
    .onUpdate((e) => {
      lastTouch.set(now.get());
      pos.set(dragFrom.get() - e.translationY / (fs * DRAG_PER_WORD));
    })
    .onFinalize((e) => {
      dragging.set(0);
      lastTouch.set(now.get());
      const v = -e.velocityY / (fs * DRAG_PER_WORD);
      const target = Math.round(pos.get() + v * 0.12);
      if (reducedMotion) pos.set(target);
      else pos.set(withSpring(target, { ...SPRING, velocity: v }));
    });

  const gesture = Gesture.Exclusive(pan, tap);

  /** The slot's live width (between the two measured words). */
  const tailStyle = useAnimatedStyle(() => {
    const p = pos.get();
    const ws = widths.get();
    const a = wrapIndex(Math.floor(p), WORDS);
    const b = wrapIndex(Math.floor(p) + 1, WORDS);
    const w = mix(ws[a], ws[b], morphAmount(p, WIDTH_LAG));
    return { transform: [{ translateX: w + gap }] };
  });

  const lineStyle = useAnimatedStyle(() => {
    const p = pos.get();
    const ws = widths.get();
    const a = wrapIndex(Math.floor(p), WORDS);
    const b = wrapIndex(Math.floor(p) + 1, WORDS);
    const u = morphAmount(p, LINE_LAG);
    const w = mix(ws[a], ws[b], u);
    const full = Math.max(1, slotW);
    return {
      backgroundColor: interpolateColor(u, [0, 1], [accents[a], accents[b]]),
      transform: [{ translateX: -(full - w) / 2 }, { scaleX: w / full }],
    };
  });

  const ghostStyle = useAnimatedStyle(() => {
    const p = pos.get();
    const ws = widths.get();
    const a = wrapIndex(Math.round(p), WORDS);
    const g = mode.get() === MODE_AUTO && auto ? ghostAt(clock.get()) : [0, 1];
    return {
      opacity: g[0] * 0.9,
      transform: [{ translateX: ws[a] * 0.5 - 22 }, { scale: g[1] }],
    };
  });

  const ready = slotW > 0;

  return (
    <View accessible accessibilityRole="header" accessibilityLabel={`${lead} ${words[0]} ${tail}`}>
      <Text style={[styles.lead, { fontSize: fs, lineHeight: fs * 1.12, color: ink, letterSpacing: -fs * 0.03 }]}>{lead}</Text>
      <GestureDetector gesture={gesture}>
        <View style={[styles.line, { height: slotH + 14 }]}>
          <View style={[styles.slot, { width: ready ? slotW : maxWidth, height: slotH, opacity: ready ? 1 : 0 }]}>
            {words.map((word, k) => (
              <Word key={word} word={word} k={k} pos={pos} fs={fs} ink={ink} reduced={reducedMotion} onWidth={measureWord} />
            ))}
          </View>
          <Animated.View pointerEvents="none" style={[styles.underline, { top: slotH + 2, width: slotW, height: Math.max(5, fs * 0.13), borderRadius: fs }, lineStyle]} />
          <Animated.Text
            onLayout={measureTail}
            style={[styles.tail, { fontSize: fs, lineHeight: fs * 1.2, color: ink, letterSpacing: -fs * 0.03, top: (slotH - fs * 1.2) / 2, opacity: ready ? 1 : 0 }, tailStyle]}
          >
            {tail}
          </Animated.Text>
          <Animated.View pointerEvents="none" style={[styles.ghost, { top: slotH / 2 - 22 }, ghostStyle]} />
        </View>
      </GestureDetector>
    </View>
  );
}

const styles = StyleSheet.create({
  lead: { fontWeight: '800' },
  line: { position: 'relative', alignSelf: 'stretch' },
  slot: { overflow: 'hidden', justifyContent: 'center' },
  word: { position: 'absolute', left: 0, flexDirection: 'row', alignItems: 'center' },
  letter: { fontWeight: '800', includeFontPadding: false },
  tail: { position: 'absolute', left: 0, fontWeight: '800' },
  underline: { position: 'absolute', left: 0 },
  ghost: {
    position: 'absolute',
    left: 0,
    width: 44,
    height: 44,
    borderRadius: 22,
    backgroundColor: 'rgba(255,255,255,0.55)',
    borderWidth: 1,
    borderColor: 'rgba(40,36,30,0.18)',
    shadowColor: '#2a241c',
    shadowOpacity: 0.18,
    shadowRadius: 10,
    shadowOffset: { width: 0, height: 4 },
  },
});
