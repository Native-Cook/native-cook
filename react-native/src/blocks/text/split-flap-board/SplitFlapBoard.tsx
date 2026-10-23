import {
  Canvas,
  Group,
  LinearGradient,
  RadialGradient,
  Rect,
  Skia,
  Text as SkiaText,
  useClock,
  useFont,
  vec,
  type SkFont,
} from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { useFonts } from 'expo-font';
import { StatusBar } from 'expo-status-bar';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Pressable, StyleSheet, Text, View, useWindowDimensions } from 'react-native';
import Animated, { ReduceMotion, useAnimatedStyle, useDerivedValue, useReducedMotion, withSpring, type SharedValue } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

// A split-flap departures board: 4 rows of 7 black flap cells with magenta letters. To change
// message every cell flips through the alphabet towards its new character: the top half of the
// old character folds down over the seam while the new one's top shows behind it, then the new
// bottom half falls into place. A cell takes at most 3 to 8 flips (fixed per cell), each 0.13 s,
// and cells start 60 ms apart left to right and 50 ms apart top to bottom. A port of the SwiftUI
// block, number for number.
//
// autoPlay timeline (LOOP = 9 s):
//   0.0-0.6   rest on the first message (also the first frame on screen)
//   0.6       flips to the second message (all cells settled by about 2.2 s)
//   3.6       flips to the third message (settled by about 5.2 s)
//   6.6       flips back to the first message (settled by about 8.2 s); rest until 9.0
// A flip's top flap folds down over 0.065 s and the bottom flap falls over the next 0.065 s,
// both as progress squared (a hinged flap starting slowly), around the seam with a 260 pt
// perspective, darkening up to 55% as they turn edge-on.
// A tap shows the next message at once; 2 s after the last tap the board flips back to the first
// message and the loop starts over.

/** Seconds. */
export const LOOP = 9;
const SWITCHES: [number, number][] = [
  [0.6, 1],
  [3.6, 2],
  [6.6, 0],
];
const FLIP = 0.13;
const COLUMN_DELAY = 0.06;
const ROW_DELAY = 0.05;
const USER_HOLD = 2;
/** Under reduced motion a message cross-fades over this long instead. */
const FADE = 0.25;

const ALPHABET = ' ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789:';

/** The most flips a cell makes, 3 to 8, fixed per cell so neighbours never move in step. */
const cap = (row: number, column: number) => 3 + ((row * 7 + column * 3) % 6);

/** The characters a cell shows on its way from `a` to `b`: `a`, then the last few letters before `b`, ending on `b`. */
function sequence(a: string, b: string, limit: number): string[] {
  const ia = ALPHABET.indexOf(a);
  const ib = ALPHABET.indexOf(b);
  if (a === b || ia < 0 || ib < 0) return [b];
  const n = ALPHABET.length;
  const distance = (ib - ia + n) % n;
  const flips = Math.min(distance, limit);
  return [a, ...Array.from({ length: flips }, (_, i) => ALPHABET[(ib - flips + 1 + i + n) % n])];
}

const delayOf = (row: number, column: number) => column * COLUMN_DELAY + row * ROW_DELAY;

const C = {
  backdropCentre: '#2B2B2D',
  backdropEdge: '#141415',
  panel: '#1A1A1B',
  panelEdge: '#303032',
  cellTop: ['#313133', '#282829'],
  cellBottom: ['#252526', '#1D1D1E'],
  seam: '#0B0B0C',
  hinge: '#1F1F20',
  hingeEdge: '#3A3A3C',
  letter: '#D4146A',
  label: '#8A8A8F',
  heading: '#EDEDEF',
  dim: '#3A3A3D',
};

// Metrics, pt.
const COLUMNS = 7;
const ROWS = 4;
const SIDE = 16;
const PANEL_PADDING = 10;
const COLUMN_GAP = 6;
const ROW_GAP = 12;
/** Cell height over width. */
const ASPECT = 1.5;
/** Letter size over cell height. */
const LETTER_SCALE = 0.66;
const RADIUS = 5;
/** The gap between a cell's two halves. */
const SEAM_GAP = 1.5;
/** Perspective distance of the flaps. */
const PERSPECTIVE = 260;

type Transition = { id: number; sequences: string[][][]; start: number; duration: number };

type Props = {
  /** Up to 4 lines of up to 7 characters (A–Z, 0–9, colon, space); each line is centred. */
  messages?: string[][];
  title?: string;
  heading?: string;
  hint?: string;
  /** Flips through the messages by itself, every LOOP seconds. */
  autoPlay?: boolean;
};

const DEFAULT_MESSAGES = [
  ['GATE 4', 'OPENS', 'AT', '09:41'],
  ['NEXT', 'TRAIN', 'HARBOR', '09:55'],
  ['HAVE A', 'GOOD', 'TRIP', ''],
];

export function SplitFlapBoard({
  messages = DEFAULT_MESSAGES,
  title = 'Departures',
  heading = 'Platform 2',
  hint = 'Tap the board for the next message',
  autoPlay = false,
}: Props) {
  const insets = useSafeAreaInsets();
  const { width, height } = useWindowDimensions();
  const reduced = useReducedMotion();
  const [fontsLoaded] = useFonts({ 'Barlow-SemiBold': require('./assets/Barlow-SemiBold.ttf') });

  const cellW = Math.floor((width - 2 * SIDE - 2 * PANEL_PADDING - (COLUMNS - 1) * COLUMN_GAP) / COLUMNS);
  const cellH = Math.round(cellW * ASPECT);
  const letterSize = Math.round(cellH * LETTER_SCALE);
  const font = useFont(require('./assets/Barlow-SemiBold.ttf'), letterSize);

  const clock = useClock();
  const [shown, setShown] = useState(0);
  const [transition, setTransition] = useState<Transition | null>(null);
  const [touches, setTouches] = useState(0);

  /** A message as a grid of characters, each line centred. */
  const grid = useCallback(
    (index: number) => {
      const lines = messages[index];
      return Array.from({ length: ROWS }, (_, r) => {
        const line = [...(r < lines.length ? lines[r] : '').toUpperCase()].slice(0, COLUMNS);
        const left = Math.floor((COLUMNS - line.length) / 2);
        return Array.from({ length: COLUMNS }, (_, c) => {
          const i = c - left;
          const ch = i >= 0 && i < line.length ? line[i] : ' ';
          return ALPHABET.includes(ch) ? ch : ' ';
        });
      });
    },
    [messages],
  );

  const latest = useRef({ shown, transition, reduced, grid, messages });
  useEffect(() => {
    latest.current = { shown, transition, reduced, grid, messages };
  });

  const serial = useRef(0);
  const show = useCallback(
    (index: number) => {
      const { transition: t, shown: s, reduced: r, grid: g } = latest.current;
      const now = clock.get();
      const target = g(index);
      const rest = g(s);
      /** The character a cell shows now (the one being revealed, mid-flip). */
      const showing = (row: number, column: number) => {
        if (!t) return rest[row][column];
        const seq = t.sequences[row][column];
        if (r) return seq[seq.length - 1];
        const e = (now - t.start) / 1000 - delayOf(row, column);
        if (e <= 0) return seq[0];
        return seq[Math.min(seq.length - 1, Math.floor(e / FLIP) + 1)];
      };
      let longest = 0;
      const sequences = Array.from({ length: ROWS }, (_, row) =>
        Array.from({ length: COLUMNS }, (_, column) => {
          const seq = sequence(showing(row, column), target[row][column], cap(row, column));
          longest = Math.max(longest, delayOf(row, column) + (seq.length - 1) * FLIP);
          return seq;
        }),
      );
      serial.current += 1;
      const next = { id: serial.current, sequences, start: now, duration: r ? FADE : longest };
      latest.current = { ...latest.current, transition: next, shown: index };
      setTransition(next);
      setShown(index);
    },
    [clock],
  );

  // The transition is dropped once every cell has landed.
  useEffect(() => {
    if (!transition) return;
    const id = transition.id;
    const timer = setTimeout(() => setTransition((t) => (t && t.id === id ? null : t)), (transition.duration + 0.05) * 1000);
    return () => clearTimeout(timer);
  }, [transition]);

  // The board shows (and the loop starts) once both faces are loaded, so no frame has empty cells.
  const ready = fontsLoaded && font != null;
  useEffect(() => {
    if (!autoPlay || reduced || !ready) return;
    const timers: ReturnType<typeof setTimeout>[] = [];
    const after = (ms: number, run: () => void) => timers.push(setTimeout(run, Math.max(0, ms)));
    const cycle = (origin: number) => {
      for (const [at, to] of SWITCHES) {
        if (to >= latest.current.messages.length) continue;
        after(origin + at * 1000 - Date.now(), () => show(to));
      }
      after(origin + LOOP * 1000 - Date.now(), () => cycle(origin + LOOP * 1000));
    };
    if (touches === 0) cycle(Date.now());
    else
      after(USER_HOLD * 1000, () => {
        if (latest.current.shown !== 0) {
          show(0);
          after(((latest.current.transition?.duration ?? 0) + 0.6) * 1000, () => cycle(Date.now()));
        } else cycle(Date.now());
      });
    return () => timers.forEach(clearTimeout);
  }, [autoPlay, reduced, ready, touches, show]);

  const tapped = () => {
    setTouches((n) => n + 1);
    if (process.env.EXPO_OS !== 'web') Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
    show((latest.current.shown + 1) % latest.current.messages.length);
  };

  const rest = useMemo(() => grid(shown), [grid, shown]);
  const cellsW = COLUMNS * cellW + (COLUMNS - 1) * COLUMN_GAP;
  const cellsH = ROWS * cellH + (ROWS - 1) * ROW_GAP;
  const panelW = cellsW + 2 * PANEL_PADDING;
  const panelH = cellsH + 2 * PANEL_PADDING;

  const backdrop = (
    <Canvas style={StyleSheet.absoluteFill}>
      {/* Charcoal, lighter in the middle, out to 62% of the height. */}
      <Rect x={0} y={0} width={width} height={height}>
        <RadialGradient c={vec(width / 2, height / 2)} r={height * 0.62} colors={[C.backdropCentre, C.backdropEdge]} />
      </Rect>
    </Canvas>
  );

  if (!ready) {
    return (
      <View style={styles.screen}>
        <StatusBar style="light" />
        {backdrop}
      </View>
    );
  }

  return (
    <View style={styles.screen}>
      <StatusBar style="light" />
      {backdrop}

      <Text style={[styles.title, { marginTop: insets.top + 18 }]}>{title.toUpperCase()}</Text>
      <Text style={[styles.heading, { fontFamily: 'Barlow-SemiBold' }]}>{heading}</Text>

      <View style={{ flex: 1 }} />

      <Pressable
        onPress={tapped}
        accessibilityRole="button"
        accessibilityLabel={messages[shown].filter(Boolean).join(' ')}
        accessibilityHint="Shows the next message"
        style={[styles.panel, { width: panelW, height: panelH }]}
      >
        <Canvas style={{ width: panelW, height: panelH }}>
          {font ? (
            <Group transform={[{ translateX: PANEL_PADDING }, { translateY: PANEL_PADDING }]}>
              {Array.from({ length: ROWS }, (_, r) => (
                <Group key={r}>
                  {Array.from({ length: COLUMNS }, (_, c) => (
                    <FlapCell
                      key={`${r}-${c}-${transition?.id ?? 'rest'}`}
                      x={c * (cellW + COLUMN_GAP)}
                      y={r * (cellH + ROW_GAP)}
                      w={cellW}
                      h={cellH}
                      font={font}
                      sequence={transition ? transition.sequences[r][c] : [rest[r][c]]}
                      start={transition ? transition.start : null}
                      delay={delayOf(r, c)}
                      clock={clock}
                      reduced={reduced}
                    />
                  ))}
                  <RowSeam y={r * (cellH + ROW_GAP) + cellH / 2} cellW={cellW} />
                </Group>
              ))}
            </Group>
          ) : null}
        </Canvas>
        <View style={styles.panelEdge} pointerEvents="none" />
      </Pressable>

      <View style={styles.dots}>
        {messages.map((_, i) => (
          <Dot key={i} on={i === shown} reduced={reduced} />
        ))}
      </View>

      <View style={{ flex: 1 }} />

      <Text style={[styles.hint, { marginBottom: insets.bottom + 28 }]}>{hint}</Text>
    </View>
  );
}

function Dot({ on, reduced }: { on: boolean; reduced: boolean }) {
  const style = useAnimatedStyle(() => {
    const config = { mass: 1, stiffness: 322, damping: 2 * 0.8 * Math.sqrt(322), reduceMotion: ReduceMotion.Never };
    return {
      width: reduced ? (on ? 18 : 6) : withSpring(on ? 18 : 6, config),
      backgroundColor: on ? C.letter : C.dim,
    };
  });
  return <Animated.View style={[styles.dot, style]} />;
}

/** The seam line across a row, with a hinge block at both ends and between cells. */
function RowSeam({ y, cellW }: { y: number; cellW: number }) {
  const cellsW = COLUMNS * cellW + (COLUMNS - 1) * COLUMN_GAP;
  const xs = Array.from({ length: COLUMNS + 1 }, (_, i) =>
    i === 0 ? -1 : i === COLUMNS ? cellsW + 1 : i * (cellW + COLUMN_GAP) - COLUMN_GAP / 2,
  );
  return (
    <Group>
      <Rect x={-4} y={y - 0.5} width={cellsW + 8} height={1} color={C.seam} />
      {xs.map((x) => {
        const rr = Skia.RRectXY(Skia.XYWHRect(x - 2.5, y - 4.5, 5, 9), 1.5, 1.5);
        const inner = Skia.RRectXY(Skia.XYWHRect(x - 2.125, y - 4.125, 4.25, 8.25), 1.2, 1.2);
        return (
          <Group key={x}>
            <Group clip={rr}>
              <Rect x={x - 2.5} y={y - 4.5} width={5} height={9} color={C.hingeEdge} />
            </Group>
            <Group clip={inner}>
              <Rect x={x - 2.5} y={y - 4.5} width={5} height={9} color={C.hinge} />
            </Group>
          </Group>
        );
      })}
    </Group>
  );
}

type CellProps = {
  x: number;
  y: number;
  w: number;
  h: number;
  font: SkFont;
  sequence: string[];
  start: number | null;
  delay: number;
  clock: SharedValue<number>;
  reduced: boolean;
};

/**
 * A cell at rest on one character. A worklet of its own at module level: written as an arrow inside the
 * cell's worklet, the React Compiler moved it out as a plain function, which the UI thread cannot call
 * (Expo Go crashed on the board's first frame).
 */
function still(ch: string) {
  'worklet';
  return { top: ch, bottom: ch, flapTop: '', flapBottom: '', topAngle: 0, bottomAngle: 0, topShade: 0, bottomShade: 0, showTop: false, showBottom: false, fade: 1, from: ch };
}

/** One flap cell: the static top and bottom halves, and while it flips, the falling flap. */
function FlapCell({ x, y, w, h, font, sequence: seq, start, delay, clock, reduced }: CellProps) {
  const halfH = (h - SEAM_GAP) / 2;
  const size = font.getSize();

  // Where the flip is: the static characters, the flap's characters, angle and shade.
  const state = useDerivedValue(() => {
    const last = seq[seq.length - 1];
    if (start === null) return still(last);
    const since = (clock.get() - start) / 1000;
    if (reduced) return { ...still(last), fade: Math.min(1, Math.max(0, since / FADE)), from: seq[0] };
    const e = since - delay;
    if (e <= 0 || seq.length < 2) return still(seq[0]);
    const k = Math.floor(e / FLIP);
    if (k >= seq.length - 1) return still(last);
    const f = (e - k * FLIP) / FLIP;
    const q1 = Math.min(1, f / 0.5);
    const q2 = Math.max(0, (f - 0.5) / 0.5);
    return {
      top: seq[k + 1],
      bottom: seq[k],
      flapTop: seq[k],
      flapBottom: seq[k + 1],
      topAngle: (-90 * q1 * q1 * Math.PI) / 180,
      bottomAngle: (90 * (1 - q2 * q2) * Math.PI) / 180,
      topShade: 0.55 * q1,
      bottomShade: 0.55 * (1 - q2 * q2),
      showTop: f < 0.5,
      showBottom: f >= 0.5,
      fade: 1,
      from: seq[k],
    };
  });

  const top = useDerivedValue(() => state.get().top);
  const bottom = useDerivedValue(() => state.get().bottom);
  const flapTop = useDerivedValue(() => state.get().flapTop);
  const flapBottom = useDerivedValue(() => state.get().flapBottom);
  const from = useDerivedValue(() => state.get().from);
  const fade = useDerivedValue(() => state.get().fade);
  const fadeOut = useDerivedValue(() => (state.get().fade < 1 ? 1 - state.get().fade : 0));
  const topFlapOpacity = useDerivedValue(() => (state.get().showTop ? 1 : 0));
  const bottomFlapOpacity = useDerivedValue(() => (state.get().showBottom ? 1 : 0));
  const topTransform = useDerivedValue(() => [{ perspective: PERSPECTIVE }, { rotateX: state.get().topAngle }]);
  const bottomTransform = useDerivedValue(() => [{ perspective: PERSPECTIVE }, { rotateX: state.get().bottomAngle }]);
  const topShade = useDerivedValue(() => state.get().topShade);
  const bottomShade = useDerivedValue(() => state.get().bottomShade);

  const seamY = y + h / 2;
  return (
    <Group>
      <Group opacity={fade}>
        <Half x={x} y={y} w={w} h={h} halfH={halfH} isTop char={top} font={font} size={size} />
        <Half x={x} y={y} w={w} h={h} halfH={halfH} isTop={false} char={bottom} font={font} size={size} />
      </Group>
      {reduced ? (
        <Group opacity={fadeOut}>
          <Half x={x} y={y} w={w} h={h} halfH={halfH} isTop char={from} font={font} size={size} />
          <Half x={x} y={y} w={w} h={h} halfH={halfH} isTop={false} char={from} font={font} size={size} />
        </Group>
      ) : null}
      {/* The top half of the old character, folding down over the seam. */}
      <Group opacity={topFlapOpacity} origin={vec(x + w / 2, y + halfH)} transform={topTransform}>
        <Half x={x} y={y} w={w} h={h} halfH={halfH} isTop char={flapTop} font={font} size={size} shade={topShade} />
      </Group>
      {/* The bottom half of the new character, falling into place. */}
      <Group opacity={bottomFlapOpacity} origin={vec(x + w / 2, seamY + SEAM_GAP / 2)} transform={bottomTransform}>
        <Half x={x} y={y} w={w} h={h} halfH={halfH} isTop={false} char={flapBottom} font={font} size={size} shade={bottomShade} />
      </Group>
    </Group>
  );
}

type HalfProps = {
  x: number;
  y: number;
  w: number;
  h: number;
  halfH: number;
  isTop: boolean;
  char: SharedValue<string>;
  font: SkFont;
  size: number;
  shade?: SharedValue<number>;
};

/**
 * Half a cell: its own piece of the rounded card and the matching half of the letter, set with
 * the middle of its capitals (0.35 em above the baseline) on the seam.
 */
function Half({ x, y, w, h, halfH, isTop, char, font, size, shade }: HalfProps) {
  const top = isTop ? y : y + h - halfH;
  const clip = Skia.RRectXY(Skia.XYWHRect(x, isTop ? y : y - h, w, isTop ? h * 2 : h * 2), RADIUS, RADIUS);
  const half = Skia.XYWHRect(x, top, w, halfH);
  const baseline = y + h / 2 + 0.35 * size;
  const textX = useDerivedValue(() => x + (w - font.getTextWidth(char.get())) / 2);
  const colors = isTop ? C.cellTop : C.cellBottom;
  return (
    <Group clip={half}>
      <Group clip={clip}>
        <Rect x={x} y={top} width={w} height={halfH}>
          <LinearGradient start={vec(x, top)} end={vec(x, top + halfH)} colors={colors} />
        </Rect>
        {isTop ? <Rect x={x} y={top} width={w} height={1} color="rgba(255,255,255,0.07)" /> : null}
        <SkiaText x={textX} y={baseline} text={char} font={font} color={C.letter} />
        {shade ? <Rect x={x} y={top} width={w} height={halfH} color="black" opacity={shade} /> : null}
      </Group>
    </Group>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, alignItems: 'center', backgroundColor: C.backdropEdge },
  title: { fontSize: 13, fontWeight: '600', letterSpacing: 2.4, color: C.label },
  heading: { marginTop: 6, fontSize: 34, color: C.heading },
  panel: {
    borderRadius: 18,
    borderCurve: 'continuous',
    backgroundColor: C.panel,
    boxShadow: '0 14px 24px rgba(0,0,0,0.5)',
  },
  panelEdge: { position: 'absolute', left: 0, top: 0, right: 0, bottom: 0, borderRadius: 18, borderCurve: 'continuous', borderWidth: 1, borderColor: C.panelEdge },
  dots: { marginTop: 28, flexDirection: 'row', gap: 8 },
  dot: { height: 6, borderRadius: 3 },
  hint: { fontSize: 13, fontWeight: '500', color: C.label },
});

