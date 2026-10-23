import {
  BlurMask,
  Canvas,
  DashPathEffect,
  Group,
  Path,
  Text as SkiaText,
  Skia,
  type SkFont,
  matchFont,
} from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { LinearGradient } from 'expo-linear-gradient';
import { StatusBar } from 'expo-status-bar';
import { useMemo, useState } from 'react';
import { type LayoutChangeEvent, Platform, StyleSheet, Text, View, useWindowDimensions } from 'react-native';
import { Gesture, GestureDetector, GestureHandlerRootView } from 'react-native-gesture-handler';
import Animated, {
  Easing,
  FadeIn,
  FadeOut,
  ReduceMotion,
  type SharedValue,
  cancelAnimation,
  useAnimatedReaction,
  useAnimatedStyle,
  useDerivedValue,
  useFrameCallback,
  useReducedMotion,
  useSharedValue,
  withTiming,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';

import {
  ARROW_DRAW,
  BOX_IN,
  BREAK_FROM,
  BREAK_OUT,
  BURST,
  BURST_FOR,
  CANDLES,
  CHAPTER_STARTS,
  CIRCLE_IN,
  CONFIRM,
  CONFIRM_MARK,
  ENTRY,
  FADE,
  type Geometry,
  LINE_TO,
  LOOP,
  NOTES_OUT,
  PEAK,
  RATIO,
  RETEST,
  RETEST_LABEL,
  STOP,
  STOPS,
  SWING_AT,
  SWING_HIGHS,
  SWING_OUT,
  SWING_STEP,
  TREND_DRAW,
  TREND_TYPE,
  ZIGZAG_DRAW,
  ZONE_CANDLES,
  ZONE_FROM,
  ZONE_RIGHT,
  ZONE_UP,
  LAST,
  camera,
  candlePath,
  chapterAt,
  clamp01,
  confettiPath,
  lineAt,
  smooth,
  spring,
  toX,
  toY,
} from './walkthrough';

export { LOOP } from './walkthrough';

type Props = {
  title?: string;
  hint?: string;
  /** One title per chapter, in order: swing highs, trend line, breakout, re-test, confirmation, risk, target. */
  chapters?: string[];
  swingHighLabel?: string;
  trendLineLabel?: string;
  breakoutLabel?: string;
  retestLabel?: string;
  confirmationLabel?: string;
  /** Written before the reward multiple in the risk box: "RR=1:" reads "RR=1:3". */
  ratioPrefix?: string;
  upColor?: string;
  downColor?: string;
  /** Plays the walkthrough by itself, one pass every LOOP seconds, and starts again after a touch. */
  autoPlay?: boolean;
  /** Called with the chapter's index whenever another chapter shows. */
  onChapterChange?: (index: number) => void;
};

const CHAPTERS = ['Swing highs', 'Trend line', 'Breakout', 'Re-test', 'Confirmation', 'Risk to reward', 'Target'];

const BACKGROUND = '#000000';
const INK = '#ffffff';
const MUTE = '#7d7d7d';
const TRACK = '#262626';
const GRID = '#151515';
const RISK = '#7a2e3e';
const REWARD = 'rgba(31,77,69,0.85)';
const CONFETTI_COLORS = ['#3de0ff', '#d86bff', '#ffb03b', '#ffffff'];

// The candles' size at zoom 1 when the screen is wide enough; a narrow one gets smaller candles.
const PITCH = 13;
const BODY_RATIO = 0.58;
const WICK = 1.5;
const UNIT = 11;
const MIN_CANDLES_ACROSS = 30;
const MIN_PRICE_SPAN = 54;
const GLOW_BLUR = 6;
const GLOW_ALPHA = 0.45;
const LINE_WIDTH = 2.5;
const CIRCLE_RADIUS = 7;
const CIRCLE_WIDTH = 1.5;
const ARROW_WIDTH = 2;
const ARROW_HEAD = 9;
const DOT = 3.5;
const DASH = [7, 6];
const CONFETTI_WIDTH = 2.6;
const GRID_STEP = 16;
const GRID_DOT = 0.9;

const HEADER = 72;
const FOOTER = 92;
const EDGE_FADE = 36;
const MARGIN = 20;
const SEGMENT_GAP = 4;

const FONT = Platform.select({ ios: '.AppleSystemUIFont', default: 'sans-serif' });
const LABEL_SIZE = 17;
const LABEL_LINE = 18;
const BIG_SIZE = 21;
const SMALL_SIZE = 13;
const RATIO_SIZE = 26;

// Swing labels pop in on a spring; the risk box slides in on a softer one.
const POP = { omega: 22, damping: 0.5 };
const SLIDE = { omega: 11, damping: 0.78 };
const LABEL_FADE = 0.15;

// Who drives the clock: the loop, a hand, or the short fade back to the loop after a hand let go.
const AUTO = 0;
const HAND = 1;
const RETURNING = 2;
/** After the last touch autoPlay waits this long, then fades to black and reprints the chart. */
const USER_HOLD = 3;
const VEIL = 0.35;
// The clock's touch stamp while a finger is down or a tapped chapter plays, and right after.
const BUSY = -2;
const RELEASED = -1;

function tick() {
  if (process.env.EXPO_OS !== 'web') Haptics.selectionAsync().catch(() => {});
}

type Placement = { x: number; y: number; rotate: number; scale: number; opacity: number };

/** A label set in Skia at a placement that follows the camera; lines are centred on x. */
function Label({
  font,
  lines,
  place,
  typed,
  color = INK,
}: {
  font: SkFont;
  lines: string[];
  place: SharedValue<Placement>;
  typed?: SharedValue<string>;
  color?: string;
}) {
  const transform = useDerivedValue(() => {
    const p = place.get();
    return [{ translateX: p.x }, { translateY: p.y }, { rotate: p.rotate }, { scale: p.scale }];
  });
  const opacity = useDerivedValue(() => place.get().opacity);
  const widths = lines.map((line) => font.measureText(line).width);
  return (
    <Group transform={transform} opacity={opacity}>
      {lines.map((line, k) => (
        <SkiaText
          key={`${k}-${line}`}
          font={font}
          // A typed label grows from the left of where the whole label sits.
          text={typed ?? line}
          x={-widths[k] / 2}
          y={-(lines.length - 1 - k) * LABEL_LINE}
          color={color}
        />
      ))}
    </Group>
  );
}

/**
 * A trading lesson told on a candlestick chart: swing highs are circled, a trend line is drawn
 * through them, price breaks it, re-tests it and confirms, a risk-to-reward box slides in and the
 * rally runs to the target in a burst of confetti. Tap for the next chapter, swipe to scrub.
 */
export function CandlePatternWalkthrough({
  title = 'Trend line breakout',
  hint = 'Tap for the next step · swipe to scrub',
  chapters = CHAPTERS,
  swingHighLabel = 'swing\nhigh',
  trendLineLabel = 'trend line',
  breakoutLabel = 'breakout',
  retestLabel = 're-test',
  confirmationLabel = 'confirmation',
  ratioPrefix = 'RR=1:',
  upColor = '#3bff3b',
  downColor = '#ff2d2d',
  autoPlay = false,
  onChapterChange,
}: Props) {
  const still = useReducedMotion();
  const insets = useSafeAreaInsets();
  const window = useWindowDimensions();
  const [laidOut, setLaidOut] = useState<{ width: number; height: number } | null>(null);
  const size = laidOut ?? { width: window.width, height: window.height - insets.top - HEADER - insets.bottom - FOOTER };
  const [chapter, setChapter] = useState(0);

  const fonts = useMemo(
    () => ({
      label: matchFont({ fontFamily: FONT, fontSize: LABEL_SIZE, fontWeight: '800' }),
      big: matchFont({ fontFamily: FONT, fontSize: BIG_SIZE, fontWeight: '800' }),
      small: matchFont({ fontFamily: FONT, fontSize: SMALL_SIZE, fontWeight: '800' }),
      ratio: matchFont({ fontFamily: FONT, fontSize: RATIO_SIZE, fontWeight: '900' }),
    }),
    [],
  );

  const pitch = Math.min(PITCH, size.width / MIN_CANDLES_ACROSS);
  const unit = Math.min(UNIT, size.height / MIN_PRICE_SPAN);
  const geo: Geometry = [size.width / 2, size.height / 2, pitch, unit];
  const ratioOf = pitch / PITCH;

  // The faint dot grid behind the chart: it stays put while the camera moves.
  const grid = useMemo(() => {
    const path = Skia.Path.Make();
    for (let x = GRID_STEP / 2; x < size.width; x += GRID_STEP)
      for (let y = GRID_STEP / 2; y < size.height; y += GRID_STEP) path.addCircle(x, y, GRID_DOT);
    return path;
  }, [size.width, size.height]);

  // The walkthrough's clock in seconds, 0 to LOOP; every picture below is a function of it.
  const t = useSharedValue(0);
  const mode = useSharedValue(autoPlay ? AUTO : HAND);
  const origin = useSharedValue(-1);
  const touchedAt = useSharedValue(RELEASED);
  const returnFrom = useSharedValue(0);
  const veil = useSharedValue(1);
  const scrubFrom = useSharedValue(0);

  useFrameCallback((frame) => {
    const now = frame.timestamp;
    const m = mode.get();
    if (m === AUTO) {
      // The loop is keyed to the first frame's timestamp, so every pass is the same.
      if (origin.get() < 0) origin.set(now - t.get() * 1000);
      t.set(((now - origin.get()) / 1000) % LOOP);
      return;
    }
    if (!autoPlay) return;
    if (touchedAt.get() === RELEASED) touchedAt.set(now);
    if (m === HAND) {
      if (touchedAt.get() < 0 || now - touchedAt.get() < USER_HOLD * 1000) return;
      const time = t.get();
      // At rest or already fading, the loop just carries on; mid-lesson, it fades to black first.
      if (time < SWING_AT || time >= FADE[0]) {
        origin.set(now - time * 1000);
        mode.set(AUTO);
      } else {
        returnFrom.set(now);
        mode.set(RETURNING);
      }
      return;
    }
    const e = (now - returnFrom.get()) / 1000;
    if (e < VEIL) {
      veil.set(1 - e / VEIL);
      return;
    }
    veil.set(1);
    t.set(FADE[1]);
    origin.set(now - FADE[1] * 1000);
    mode.set(AUTO);
  });

  const reportChapter = (index: number, byHand: boolean) => {
    setChapter(index);
    if (byHand) tick();
    onChapterChange?.(index);
  };

  useAnimatedReaction(
    () => chapterAt(t.get()),
    (now, old) => {
      if (old !== null && now !== old) scheduleOnRN(reportChapter, now, mode.get() === HAND);
    },
  );

  const touch = () => {
    'worklet';
    mode.set(HAND);
    cancelAnimation(t);
    veil.set(1);
    touchedAt.set(BUSY);
  };

  /** Plays on to the end of the chapter in progress; from the last one, through the reprint to rest. */
  const next = () => {
    'worklet';
    touch();
    const time = t.get();
    let stop = LOOP;
    for (let k = 0; k < STOPS.length; k++) {
      if (STOPS[k] > time + 0.01) {
        stop = STOPS[k];
        break;
      }
    }
    t.set(
      withTiming(stop, { duration: (stop - time) * 1000, easing: Easing.linear, reduceMotion: ReduceMotion.Never }, (done) => {
        if (!done) return;
        if (stop >= LOOP) t.set(0);
        touchedAt.set(RELEASED);
      }),
    );
  };

  const tap = Gesture.Tap().onEnd(() => {
    next();
  });
  const pan = Gesture.Pan()
    .activeOffsetX([-12, 12])
    .onStart(() => {
      touch();
      scrubFrom.set(t.get());
    })
    .onUpdate((event) => {
      // One swipe across the screen scrubs the whole lesson.
      t.set(Math.min(LOOP - 0.001, Math.max(0, scrubFrom.get() + (event.translationX / size.width) * LOOP)));
    })
    .onFinalize(() => {
      touchedAt.set(RELEASED);
    });
  const gesture = Gesture.Race(pan, tap);

  const view = useDerivedValue(() => camera(t.get(), still));

  const ups = useDerivedValue(() => candlePath(t.get(), view.get(), geo, true, still, BODY_RATIO, WICK * ratioOf));
  const downs = useDerivedValue(() => candlePath(t.get(), view.get(), geo, false, still, BODY_RATIO, WICK * ratioOf));

  // The chart fades out at the end; the reprinted candles come back at full strength.
  const chartOpacity = useDerivedValue(() => {
    const time = t.get();
    return (time >= FADE[1] ? 1 : 1 - smooth(time, FADE[0], FADE[1])) * veil.get();
  });
  const notesOpacity = useDerivedValue(() => {
    const time = t.get();
    return (time >= FADE[1] ? 0 : 1 - smooth(time, FADE[0], FADE[1])) * veil.get();
  });

  // Swing highs: a circle drawn round each high, one after another.
  const circles = useDerivedValue(() => {
    const time = t.get();
    const v = view.get();
    const path = Skia.Path.Make();
    const r = CIRCLE_RADIUS * v[2] * ratioOf;
    for (let k = 0; k < SWING_HIGHS.length; k++) {
      const p = clamp01((time - SWING_AT - k * SWING_STEP) / CIRCLE_IN);
      if (p <= 0) continue;
      const i = SWING_HIGHS[k];
      const x = toX(i, v, geo);
      const y = toY(CANDLES[i][1], v, geo);
      path.addArc({ x: x - r, y: y - r, width: r * 2, height: r * 2 }, -90, still ? 359.9 : Math.min(359.9, 360 * p));
    }
    return path;
  });
  const circlesOpacity = useDerivedValue(() => 1 - smooth(t.get(), SWING_OUT[0], SWING_OUT[1]));

  /** The label over swing high k; the clock and the view are read by the hook that calls it. */
  const swingPlace = (k: number, time: number, v: number[]) => {
    'worklet';
    const i = SWING_HIGHS[k];
    const at = SWING_AT + k * SWING_STEP;
    const shown = still ? smooth(time, at, at + LABEL_FADE) : clamp01((time - at) / 0.06);
    const pop = still ? 1 : spring(time - at, POP.omega, POP.damping);
    return {
      x: toX(i, v, geo),
      y: toY(CANDLES[i][1], v, geo) - (CIRCLE_RADIUS + 7) * v[2] * ratioOf,
      rotate: 0,
      scale: v[2] * ratioOf * (0.4 + 0.6 * pop),
      opacity: shown * (1 - smooth(time, SWING_OUT[0], SWING_OUT[1])),
    };
  };
  const swing0 = useDerivedValue(() => swingPlace(0, t.get(), view.get()));
  const swing1 = useDerivedValue(() => swingPlace(1, t.get(), view.get()));
  const swing2 = useDerivedValue(() => swingPlace(2, t.get(), view.get()));
  const swing3 = useDerivedValue(() => swingPlace(3, t.get(), view.get()));

  // The trend line: drawn from the first high, then its label types in along it.
  const trendLine = useDerivedValue(() => {
    const v = view.get();
    const path = Skia.Path.Make();
    path.moveTo(toX(SWING_HIGHS[0], v, geo), toY(lineAt(SWING_HIGHS[0]), v, geo));
    path.lineTo(toX(LINE_TO, v, geo), toY(lineAt(LINE_TO), v, geo));
    return path;
  });
  const trendEnd = useDerivedValue(() => {
    const time = t.get();
    if (still) return time >= TREND_DRAW[0] ? 1 : 0;
    const u = clamp01((time - TREND_DRAW[0]) / (TREND_DRAW[1] - TREND_DRAW[0]));
    return 1 - (1 - u) * (1 - u);
  });
  const trendOpacity = useDerivedValue(() => (still ? smooth(t.get(), TREND_DRAW[0], TREND_DRAW[0] + LABEL_FADE) : 1));
  const angle = Math.atan2(-(lineAt(1) - lineAt(0)) * unit, pitch);

  /** A point on the trend line at a candle index, pushed off it by `offset` points (up is negative). */
  const onLine = (index: number, offset: number, opacity: number, v: number[]) => {
    'worklet';
    return {
      x: toX(index, v, geo) + Math.sin(angle) * -offset * v[2] * ratioOf,
      y: toY(lineAt(index), v, geo) + Math.cos(angle) * offset * v[2] * ratioOf,
      rotate: angle,
      scale: v[2] * ratioOf,
      opacity,
    };
  };
  const trendPlace = useDerivedValue(() => {
    const time = t.get();
    const shown = still ? smooth(time, TREND_TYPE[0], TREND_TYPE[0] + LABEL_FADE) : time >= TREND_TYPE[0] ? 1 : 0;
    return onLine(7.4, -10, shown, view.get());
  });
  const trendTyped = useDerivedValue(() => {
    if (still) return trendLineLabel;
    const u = clamp01((t.get() - TREND_TYPE[0]) / (TREND_TYPE[1] - TREND_TYPE[0]));
    return trendLineLabel.slice(0, Math.ceil(u * trendLineLabel.length));
  });

  // The breakout: an arrow from under the last low up through the line, with its label.
  const arrow = useDerivedValue(() => {
    const v = view.get();
    const ax = toX(BREAK_FROM + 0.4, v, geo);
    const ay = toY(CANDLES[BREAK_FROM][2] - 1.6, v, geo);
    const bx = toX(PEAK + 1.4, v, geo);
    const by = toY(CANDLES[PEAK][1] + 3.5, v, geo);
    const length = Math.hypot(bx - ax, by - ay);
    const ux = (bx - ax) / length;
    const uy = (by - ay) / length;
    const head = ARROW_HEAD * v[2] * ratioOf;
    const path = Skia.Path.Make();
    path.moveTo(ax, ay);
    path.lineTo(bx, by);
    // The head is drawn last, so it shows only once the shaft has arrived.
    path.moveTo(bx - head * (ux - uy * 0.7), by - head * (uy + ux * 0.7));
    path.lineTo(bx, by);
    path.lineTo(bx - head * (ux + uy * 0.7), by - head * (uy - ux * 0.7));
    return path;
  });
  const arrowEnd = useDerivedValue(() => {
    const time = t.get();
    if (still) return time >= ARROW_DRAW[0] ? 1 : 0;
    return smooth(time, ARROW_DRAW[0], ARROW_DRAW[1]);
  });
  const breakShown = useDerivedValue(() => {
    const time = t.get();
    const on = still ? smooth(time, ARROW_DRAW[0], ARROW_DRAW[0] + LABEL_FADE) : time >= ARROW_DRAW[0] ? 1 : 0;
    return on * (1 - smooth(time, BREAK_OUT[0], BREAK_OUT[1]));
  });
  const dot = useDerivedValue(() => {
    const v = view.get();
    const path = Skia.Path.Make();
    path.addCircle(toX(BREAK_FROM + 0.4, v, geo), toY(CANDLES[BREAK_FROM][2] - 1.6, v, geo), DOT * v[2] * ratioOf);
    return path;
  });
  const breakPlace = useDerivedValue(() => {
    const time = t.get();
    const v = view.get();
    const pop = still ? 1 : spring(time - ARROW_DRAW[0] - 0.1, POP.omega, POP.damping);
    return {
      x: toX(BREAK_FROM + 0.4, v, geo),
      y: toY(CANDLES[BREAK_FROM][2] - 1.6, v, geo) + 28 * v[2] * ratioOf,
      rotate: 0,
      scale: v[2] * ratioOf * (0.4 + 0.6 * pop),
      opacity: breakShown.get() * (still ? 1 : clamp01((time - ARROW_DRAW[0] - 0.1) / 0.06)),
    };
  });

  // The re-test: a dashed zig-zag from the line to the peak and back down to it.
  const zigzag = useDerivedValue(() => {
    const v = view.get();
    const path = Skia.Path.Make();
    path.moveTo(toX(BREAK_FROM + 1.6, v, geo), toY(lineAt(BREAK_FROM + 1.6), v, geo));
    path.lineTo(toX(PEAK, v, geo), toY(CANDLES[PEAK][1], v, geo));
    path.lineTo(toX(RETEST, v, geo), toY(CANDLES[RETEST][2], v, geo));
    return path;
  });
  const zigzagEnd = useDerivedValue(() => {
    const time = t.get();
    if (still) return time >= ZIGZAG_DRAW[0] ? 1 : 0;
    return smooth(time, ZIGZAG_DRAW[0], ZIGZAG_DRAW[1]);
  });
  const notesOut = useDerivedValue(() => 1 - smooth(t.get(), NOTES_OUT[0], NOTES_OUT[1]));
  const zigzagOpacity = useDerivedValue(() => (still ? smooth(t.get(), ZIGZAG_DRAW[0], ZIGZAG_DRAW[0] + LABEL_FADE) : 1) * notesOut.get());
  const retestPlace = useDerivedValue(() => {
    const time = t.get();
    const shown = still ? smooth(time, RETEST_LABEL, RETEST_LABEL + LABEL_FADE) : clamp01((time - RETEST_LABEL) / 0.12);
    return onLine(PEAK - 0.6, 14, shown * notesOut.get(), view.get());
  });

  // The confirming candle: boxed, with its label above.
  const confirmBox = useDerivedValue(() => {
    const v = view.get();
    const [open, high, , close] = CANDLES[CONFIRM];
    const pad = 3 * v[2] * ratioOf;
    const half = (geo[2] * v[2] * BODY_RATIO) / 2 + pad;
    const x = toX(CONFIRM, v, geo);
    const top = toY(Math.min(high, close), v, geo) - pad;
    const path = Skia.Path.Make();
    path.addRect(Skia.XYWHRect(x - half, top, half * 2, toY(open, v, geo) + pad - top));
    return path;
  });
  const confirmShown = useDerivedValue(() => {
    const time = t.get();
    const on = still ? smooth(time, CONFIRM_MARK, CONFIRM_MARK + LABEL_FADE) : clamp01((time - CONFIRM_MARK) / 0.1);
    return on * notesOut.get();
  });
  const confirmPlace = useDerivedValue(() => {
    const v = view.get();
    const time = t.get();
    const rise = still ? 0 : (1 - smooth(time, CONFIRM_MARK, CONFIRM_MARK + 0.25)) * 8;
    return {
      x: toX(CONFIRM, v, geo),
      y: toY(CANDLES[CONFIRM][1], v, geo) - (10 - rise) * v[2] * ratioOf,
      rotate: 0,
      scale: v[2] * ratioOf,
      opacity: confirmShown.get(),
    };
  });

  // Risk to reward: the risk box slides in from the right, the reward zone opens right, then up.
  const box = useDerivedValue(() => {
    const time = t.get();
    const v = view.get();
    const slide = still ? 1 : spring(time - BOX_IN, SLIDE.omega, SLIDE.damping);
    // It starts just past the right edge.
    const home = toX(ZONE_FROM, v, geo);
    const x = home + (1 - slide) * Math.max(0, geo[0] * 2 - home);
    const width = ZONE_CANDLES * geo[2] * v[2];
    const right = smooth(time, ZONE_RIGHT[0], ZONE_RIGHT[1]);
    const up = smooth(time, ZONE_UP[0], ZONE_UP[1]);
    const multiple = 1 + (RATIO - 1) * up;
    const entry = toY(ENTRY, v, geo);
    const risk = Skia.Path.Make();
    risk.addRect(Skia.XYWHRect(x, entry, width, toY(STOP, v, geo) - entry));
    const reward = Skia.Path.Make();
    const top = toY(ENTRY + (ENTRY - STOP) * multiple, v, geo);
    if (right > 0) reward.addRect(Skia.XYWHRect(x, top, width * right, entry - top));
    // The multiple counts up a whole step at a time as the zone climbs.
    const count = Math.min(RATIO, 1 + Math.floor(up * (RATIO - 1) + 0.5));
    return { risk, reward, x: x + width / 2, y: (entry + toY(STOP, v, geo)) / 2, scale: v[2] * ratioOf, count };
  });
  const boxOpacity = useDerivedValue(() => {
    const time = t.get();
    return still ? smooth(time, BOX_IN, BOX_IN + LABEL_FADE) : time >= BOX_IN ? 1 : 0;
  });
  const riskPath = useDerivedValue(() => box.get().risk);
  const rewardPath = useDerivedValue(() => box.get().reward);
  const ratioPlace = useDerivedValue(() => {
    const b = box.get();
    return { x: b.x, y: b.y + (RATIO_SIZE * 0.36) * b.scale, rotate: 0, scale: b.scale, opacity: boxOpacity.get() };
  });
  const ratioText = useDerivedValue(() => `${ratioPrefix}${box.get().count}`);

  // The target reached: confetti bursts from the top of the last candle.
  const burst = useDerivedValue(() => {
    const time = t.get();
    const v = view.get();
    const elapsed = still ? BURST_FOR * 0.45 : time - BURST;
    const ox = toX(LAST, v, geo);
    const oy = toY(CANDLES[LAST][1], v, geo);
    const paths = [];
    for (let slot = 0; slot < CONFETTI_COLORS.length; slot++) paths.push(confettiPath(slot, Math.max(0, elapsed), ox, oy));
    return paths;
  });
  const burstOpacity = useDerivedValue(() => {
    const u = t.get() - BURST;
    if (u < 0 || u > BURST_FOR) return 0;
    return still ? Math.min(smooth(u, 0, 0.15), 1 - smooth(u, BURST_FOR - 0.2, BURST_FOR)) : 1 - smooth(u, BURST_FOR * 0.55, BURST_FOR);
  });
  const burst0 = useDerivedValue(() => burst.get()[0]);
  const burst1 = useDerivedValue(() => burst.get()[1]);
  const burst2 = useDerivedValue(() => burst.get()[2]);
  const burst3 = useDerivedValue(() => burst.get()[3]);
  const burstPaths = [burst0, burst1, burst2, burst3];

  // The chapter bar under the chart: one segment per chapter fills as it plays.
  const fills = chapters.map((_, k) => [CHAPTER_STARTS[k], STOPS[k]]);
  const segment0 = useSegment(t, fills[0]);
  const segment1 = useSegment(t, fills[1]);
  const segment2 = useSegment(t, fills[2]);
  const segment3 = useSegment(t, fills[3]);
  const segment4 = useSegment(t, fills[4]);
  const segment5 = useSegment(t, fills[5]);
  const segment6 = useSegment(t, fills[6]);
  const segments = [segment0, segment1, segment2, segment3, segment4, segment5, segment6];

  const onLayout = (event: LayoutChangeEvent) => {
    const { width, height } = event.nativeEvent.layout;
    setLaidOut({ width, height });
  };

  const swingLines = swingHighLabel.split('\n');

  return (
    <GestureHandlerRootView style={styles.screen}>
      <StatusBar style="light" />
      <View style={[styles.header, { paddingTop: insets.top + 12 }]}>
        <Text style={styles.title} numberOfLines={1}>
          {title}
        </Text>
        <Text style={styles.hint} numberOfLines={1}>
          {hint}
        </Text>
      </View>

      <GestureDetector gesture={gesture}>
        <View
          style={styles.stage}
          onLayout={onLayout}
          accessible
          accessibilityRole="adjustable"
          accessibilityLabel={title}
          accessibilityValue={{ text: chapters[chapter] }}
          accessibilityActions={[{ name: 'increment' }]}
          onAccessibilityAction={(event) => {
            if (event.nativeEvent.actionName === 'increment') scheduleOnUI(next);
          }}
        >
          <Canvas style={StyleSheet.absoluteFill}>
            <Path path={grid} color={GRID} />

            <Group opacity={notesOpacity}>
              <Path path={rewardPath} color={REWARD} opacity={boxOpacity} />
              <Path path={riskPath} color={RISK} opacity={boxOpacity} />
            </Group>

            <Group opacity={chartOpacity}>
              <Group opacity={GLOW_ALPHA}>
                <Path path={ups} color={upColor}>
                  <BlurMask blur={GLOW_BLUR} style="normal" />
                </Path>
                <Path path={downs} color={downColor}>
                  <BlurMask blur={GLOW_BLUR} style="normal" />
                </Path>
              </Group>
              <Path path={ups} color={upColor} />
              <Path path={downs} color={downColor} />
            </Group>

            <Group opacity={notesOpacity}>
              <Path path={circles} style="stroke" strokeWidth={CIRCLE_WIDTH} color={INK} opacity={circlesOpacity} />
              <Label font={fonts.label} lines={swingLines} place={swing0} />
              <Label font={fonts.label} lines={swingLines} place={swing1} />
              <Label font={fonts.label} lines={swingLines} place={swing2} />
              <Label font={fonts.label} lines={swingLines} place={swing3} />

              <Group opacity={trendOpacity}>
                <Path path={trendLine} style="stroke" strokeWidth={LINE_WIDTH * 2} strokeCap="round" color={INK} end={trendEnd} opacity={0.35}>
                  <BlurMask blur={GLOW_BLUR} style="normal" />
                </Path>
                <Path path={trendLine} style="stroke" strokeWidth={LINE_WIDTH} strokeCap="round" color={INK} end={trendEnd} />
              </Group>
              <Group>
                <Label font={fonts.big} lines={[trendLineLabel]} place={trendPlace} typed={trendTyped} />
              </Group>

              <Group opacity={breakShown}>
                <Path path={arrow} style="stroke" strokeWidth={ARROW_WIDTH} strokeCap="round" strokeJoin="round" color={INK} end={arrowEnd} />
                <Path path={dot} color={INK} />
              </Group>
              <Label font={fonts.big} lines={[breakoutLabel]} place={breakPlace} />

              <Path path={zigzag} style="stroke" strokeWidth={ARROW_WIDTH} strokeCap="round" color={INK} end={zigzagEnd} opacity={zigzagOpacity}>
                <DashPathEffect intervals={DASH} />
              </Path>
              <Label font={fonts.small} lines={[retestLabel]} place={retestPlace} />

              <Path path={confirmBox} style="stroke" strokeWidth={CIRCLE_WIDTH} color={INK} opacity={confirmShown} />
              <Label font={fonts.small} lines={[confirmationLabel]} place={confirmPlace} />

              <Label font={fonts.ratio} lines={[`${ratioPrefix}${RATIO}`]} place={ratioPlace} typed={ratioText} />

              <Group opacity={burstOpacity}>
                {burstPaths.map((path, slot) => (
                  <Group key={CONFETTI_COLORS[slot]}>
                    <Path path={path} style="stroke" strokeWidth={CONFETTI_WIDTH * 2} strokeJoin="round" color={CONFETTI_COLORS[slot]} opacity={0.5}>
                      <BlurMask blur={4} style="normal" />
                    </Path>
                    <Path path={path} style="stroke" strokeWidth={CONFETTI_WIDTH} strokeJoin="round" strokeCap="round" color={CONFETTI_COLORS[slot]} />
                  </Group>
                ))}
              </Group>
            </Group>
          </Canvas>
          {/* The chart runs under the header and the chapter bar: soft edges instead of a cut. */}
          <LinearGradient pointerEvents="none" colors={[BACKGROUND, `${BACKGROUND}00`]} style={[styles.edge, styles.edgeTop]} />
          <LinearGradient pointerEvents="none" colors={[`${BACKGROUND}00`, BACKGROUND]} style={[styles.edge, styles.edgeBottom]} />
        </View>
      </GestureDetector>

      <View style={[styles.footer, { paddingBottom: Math.max(insets.bottom, 16) }]}>
        <View style={styles.segments}>
          {segments.map((style, k) => (
            <View key={chapters[k]} style={styles.segment}>
              <Animated.View style={[styles.segmentFill, style]} />
            </View>
          ))}
        </View>
        <View style={styles.caption}>
          <Text style={styles.step}>{String(chapter + 1).padStart(2, '0')}</Text>
          <Animated.Text key={chapter} entering={FadeIn.duration(180)} exiting={FadeOut.duration(120)} style={styles.chapter} numberOfLines={1}>
            {chapters[chapter]}
          </Animated.Text>
        </View>
      </View>
    </GestureHandlerRootView>
  );
}

/** One chapter segment's fill: empty before the chapter, full after, and cleared with the fade. */
function useSegment(t: SharedValue<number>, span: number[]) {
  return useAnimatedStyle(() => {
    const time = t.get();
    const fill = time >= FADE[1] ? 0 : clamp01((time - span[0]) / (span[1] - span[0])) * (1 - smooth(time, FADE[0], FADE[1]));
    return { transform: [{ scaleX: fill }] };
  });
}

const styles = StyleSheet.create({
  screen: {
    flex: 1,
    backgroundColor: BACKGROUND,
  },
  header: {
    paddingHorizontal: MARGIN,
    paddingBottom: 12,
    zIndex: 1,
  },
  title: {
    color: INK,
    fontSize: 22,
    lineHeight: 28,
    fontWeight: '800',
    letterSpacing: -0.4,
  },
  hint: {
    marginTop: 4,
    color: MUTE,
    fontSize: 13,
    lineHeight: 18,
  },
  stage: {
    flex: 1,
  },
  edge: {
    position: 'absolute',
    left: 0,
    right: 0,
    height: EDGE_FADE,
  },
  edgeTop: {
    top: 0,
  },
  edgeBottom: {
    bottom: 0,
  },
  footer: {
    paddingTop: 16,
    paddingHorizontal: MARGIN,
  },
  segments: {
    flexDirection: 'row',
    gap: SEGMENT_GAP,
  },
  segment: {
    flex: 1,
    height: 3,
    borderRadius: 1.5,
    backgroundColor: TRACK,
    overflow: 'hidden',
  },
  segmentFill: {
    flex: 1,
    backgroundColor: INK,
    transformOrigin: 'left',
  },
  caption: {
    flexDirection: 'row',
    alignItems: 'baseline',
    marginTop: 14,
    height: 26,
  },
  step: {
    width: 32,
    color: MUTE,
    fontSize: 13,
    lineHeight: 18,
    fontWeight: '600',
    fontVariant: ['tabular-nums'],
  },
  chapter: {
    color: INK,
    fontSize: 19,
    lineHeight: 24,
    fontWeight: '700',
  },
});
