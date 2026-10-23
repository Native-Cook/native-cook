import { Ionicons, MaterialCommunityIcons } from '@expo/vector-icons';
import {
  Blur,
  Canvas,
  Group,
  Paint,
  Paragraph,
  RoundedRect,
  Skia,
  type SkFont,
  type SkParagraph,
  Text as Glyphs,
  TextAlign,
  matchFont,
  rect,
} from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { type ReactNode, useEffect, useMemo, useRef, useState } from 'react';
import { type LayoutChangeEvent, Platform, Pressable, StyleSheet, Text, View, useWindowDimensions } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  Easing,
  type SharedValue,
  cancelAnimation,
  useAnimatedReaction,
  useAnimatedStyle,
  useDerivedValue,
  useReducedMotion,
  useSharedValue,
  withDelay,
  withSequence,
  withSpring,
  withTiming,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

import { Ruler, TICK_STEP } from './Ruler';

export type Loan = {
  amount: number;
  months: number;
  /** What is paid every month, and in all, at the yearly rate. */
  monthly: number;
  total: number;
};

type Props = {
  /** The amount the first question starts on, its limits, and what one tick of the ruler is worth. */
  amount?: number;
  minAmount?: number;
  maxAmount?: number;
  amountStep?: number;
  /** The same for the months; a tick is one month. */
  months?: number;
  minMonths?: number;
  maxMonths?: number;
  /** The yearly rate, in percent. */
  rate?: number;
  /** Written before an amount. */
  symbol?: string;
  /** The three questions: how much, for how long, and the summary. */
  questions?: [string, string, string];
  /** The word over each large figure. */
  labels?: [string, string, string];
  monthlyLabel?: string;
  totalLabel?: string;
  amountLabel?: string;
  termLabel?: string;
  rateLabel?: string;
  monthsUnit?: string;
  previousLabel?: string;
  nextLabel?: string;
  confirmLabel?: string;
  sentLabel?: string;
  /** Answers the questions by itself every LOOP seconds, and goes back to the first. */
  autoPlay?: boolean;
  onChange?: (loan: Loan) => void;
  onConfirm?: (loan: Loan) => void;
  /** The two arrows at the top right, which would close the questions into a short form. */
  onCollapse?: () => void;
};

const BACKGROUND = '#FFFFFF';
const INK = '#3A3F58';
const PALE = '#C9CCD6';
const TICKS = '#C9CEEA';
const BLUE = '#3F5BD9';
const STRIP = '#F4F5F8';
const FOOTER = '#D9DBE1';
const TRACK = '#C4C8DC';

const MARGIN = 24;
const BAR = 44;
// The content, from the top of the question: a question of two lines, the label, the large
// figure (the baseline of its digits), the middle of the ruler, and the strip of totals.
const QUESTION = 28;
const QUESTION_LINE = 36;
const LABEL_AT = 98;
const LABEL = 16;
const FIGURE = 80;
const FIGURE_BASE = 198;
const FIGURE_TRACKING = -2;
const RULER_AT = 256;
const STRIP_AT = 300;
const STRIP_HEIGHT = 46;
const STRIP_RADIUS = 8;
const STRIP_GAP = 10;
// The summary has two strips where the ruler and the totals were.
const SUMMARY_AT = 236;
const CONTENT = STRIP_AT + STRIP_HEIGHT;
// How much of the room left over goes above the question.
const LEAD = 0.24;
const FOOTER_HEIGHT = 56;
const TRACK_HEIGHT = 3;

// A step change: `progress` runs 0 to 1 in TURN seconds. The page that leaves travels over the
// first OUT of it, the page that comes over the part from IN_AT on. Each part of a page starts
// STAGGER (of its page's share) after the one above it.
const TURN = 0.9;
const OUT = 0.52;
const IN_AT = 0.42;
const STAGGER = 0.12;
const PARTS = 5;
// A part travels this far and smears vertically by up to this much on the way.
const TRAVEL = 70;
const SMEAR = 7;
// Between the two rulers the ruler only dips: up, then back from below.
const DIP = 14;
// A digit that changes starts this faint and firms up over GHOST seconds, while the digit it
// replaced fades from GHOST_LEFT.
const GHOST = 0.26;
const GHOST_FROM = 0.3;
const GHOST_LEFT = 0.4;
// The ruler: tenths of a tick are shown while it is dragged; a release throws it on by GLIDE
// seconds of the finger's speed and it settles on a tick.
const FINE = 10;
const GLIDE = 0.16;
const SNAP = { stiffness: 190, damping: 24, mass: 1 };
const RUBBER = 0.25;

/** autoPlay, in seconds into each loop. */
export const LOOP = 9.6;
const AUTO_AMOUNT = 47000;
const AUTO_MONTHS = 29;
const DRAG_AMOUNT_AT = 0.8;
const DRAG_AMOUNT_FOR = 1.4;
const NEXT_AT = 2.6;
const DRAG_MONTHS_AT = 3.8;
const DRAG_MONTHS_FOR = 1.1;
const SUMMARY_NEXT_AT = 5.3;
const RESTART_AT = 7.6;
const PRESS_FOR = 0.14;
/** After a touch autoPlay waits this long, goes back to the first question and starts over. */
const USER_HOLD = 3;
// The soft dot that stands for the finger while the loop plays.
const DOT = 56;

const FONT = Platform.select({ ios: '.AppleSystemUIFont', default: 'sans-serif' });

function clamp(value: number, low: number, high: number) {
  'worklet';
  return Math.min(high, Math.max(low, value));
}

function smooth(t: number, from: number, to: number) {
  'worklet';
  const u = clamp((t - from) / (to - from), 0, 1);
  return u * u * (3 - 2 * u);
}

/**
 * Where a part of the screen is in a step change: 0 in place, towards −1 as it leaves upwards,
 * from 1 as it comes from below (the other way round when going back), 2 when it is not shown.
 * `pages` has a bit for every page the part belongs to; `order` is its place from the top.
 */
function placeOf(pages: number, order: number, from: number, to: number, progress: number) {
  'worklet';
  const here = (pages >> from) & 1;
  const there = (pages >> to) & 1;
  if (here && there) return 0;
  const way = to > from ? 1 : -1;
  const spread = 1 + (PARTS - 1) * STAGGER;
  if (here) {
    const u = clamp(clamp(progress / OUT, 0, 1) * spread - order * STAGGER, 0, 1);
    return -way * u * u;
  }
  if (there) {
    const u = 1 - clamp(clamp((progress - IN_AT) / (1 - IN_AT), 0, 1) * spread - order * STAGGER, 0, 1);
    return way * u * u * u;
  }
  return 2;
}

function grouped(value: number) {
  return String(Math.round(value)).replace(/\B(?=(\d{3})+(?!\d))/g, ',');
}

/** "25000" → "25k": the short form at the ends of the ruler. */
function short(value: number) {
  return value >= 1000 ? `${Math.round(value / 100) / 10}k` : String(value);
}

/** The loan's cost with equal monthly payments. */
function cost(amount: number, months: number, rate: number): Loan {
  const monthlyRate = rate / 1200;
  const monthly = monthlyRate === 0 ? amount / months : (amount * monthlyRate) / (1 - Math.pow(1 + monthlyRate, -months));
  return { amount, months, monthly: Math.round(monthly), total: Math.round(monthly * months) };
}

function paragraph(text: string, size: number, line: number, weight: number, color: string, width: number, align: TextAlign) {
  const builder = Skia.ParagraphBuilder.Make({ textAlign: align });
  builder.pushStyle({ color: Skia.Color(color), fontFamilies: [FONT], fontSize: size, fontStyle: { weight }, heightMultiplier: line / size, halfLeading: true });
  builder.addText(text);
  const built = builder.build();
  built.layout(width);
  return built;
}

function tick() {
  if (process.env.EXPO_OS !== 'web') Haptics.selectionAsync().catch(() => {});
}

function tap() {
  if (process.env.EXPO_OS !== 'web') Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
}

/** The step change in progress: from which page to which, and how far along. */
type Turn = { from: SharedValue<number>; to: SharedValue<number>; progress: SharedValue<number> };

type PartProps = {
  turn: Turn;
  /** A bit for each page the part is on. */
  pages: number;
  /** Its place from the top of its page, which sets when it starts to move. */
  order: number;
  /** No travel and no smear: it only fades (reduced motion). */
  still: boolean;
  /** The box it is drawn in, with room above and below for the smear. */
  bounds: { x: number; y: number; width: number; height: number };
  children: ReactNode;
};

/** A part of a page: it travels up and smears away as its page leaves, and rises as its page comes. */
function Part({ turn, pages, order, still, bounds, children }: PartProps) {
  const place = useDerivedValue(() => placeOf(pages, order, turn.from.get(), turn.to.get(), turn.progress.get()));
  const transform = useDerivedValue(() => [{ translateY: still ? 0 : place.get() * TRAVEL }]);
  const opacity = useDerivedValue(() => 1 - Math.min(1, Math.abs(place.get())));
  const smear = useDerivedValue(() => {
    const away = Math.min(1, Math.abs(place.get()));
    return { x: 0, y: still ? 0 : SMEAR * 4 * away * (1 - away) };
  });
  return (
    <Group transform={transform}>
      <Group
        clip={rect(bounds.x, bounds.y, bounds.width, bounds.height)}
        layer={
          <Paint opacity={opacity}>
            <Blur blur={smear} />
          </Paint>
        }
      >
        {children}
      </Group>
    </Group>
  );
}

type DigitProps = { char: string; x: number; width: number; y: number; font: SkFont; color: string };

/** One character of a large figure. When it changes, the old one lingers as a ghost under the new. */
function Digit({ char, x, width, y, font, color }: DigitProps) {
  const [shown, setShown] = useState({ now: char, before: '' });
  if (shown.now !== char) setShown({ now: char, before: shown.now });
  const fresh = useSharedValue(1);
  useEffect(() => {
    if (shown.before === '') return;
    fresh.set(0);
    fresh.set(withTiming(1, { duration: GHOST * 1000, easing: Easing.out(Easing.quad) }));
  }, [shown, fresh]);
  const firm = useDerivedValue(() => GHOST_FROM + (1 - GHOST_FROM) * fresh.get());
  const ghost = useDerivedValue(() => GHOST_LEFT * (1 - fresh.get()));
  // Each character is centred in its cell, so digits of different widths do not shift the rest.
  const centred = (text: string) => x + (width - font.getGlyphWidths(font.getGlyphIDs(text))[0]) / 2;
  return (
    <>
      {shown.before !== '' && <Glyphs text={shown.before} font={font} x={centred(shown.before)} y={y} color={color} opacity={ghost} />}
      <Glyphs text={shown.now} font={font} x={centred(shown.now)} y={y} color={color} opacity={firm} />
    </>
  );
}

type FigureProps = { text: string; x: number; y: number; font: SkFont; symbol: string };

/** A large figure, a character at a time: the currency symbol pale, digits in cells of one width. */
function Figure({ text, x, y, font, symbol }: FigureProps) {
  const widths = useMemo(() => {
    const advance = (char: string) => font.getGlyphWidths(font.getGlyphIDs(char))[0];
    const digit = Math.max(...Array.from('0123456789', advance)) + FIGURE_TRACKING;
    return (char: string) => (char >= '0' && char <= '9' ? digit : advance(char) + FIGURE_TRACKING);
  }, [font]);
  let cursor = x;
  return (
    <>
      {Array.from(text).map((char, index) => {
        const at = cursor;
        const width = widths(char);
        cursor += width;
        return <Digit key={index} char={char} x={at} width={width} y={y} font={font} color={char === symbol ? PALE : INK} />;
      })}
    </>
  );
}

type StripProps = { x: number; y: number; width: number; left: SkParagraph; right: SkParagraph };

/** Two figures side by side on a pale strip. */
function Strip({ x, y, width, left, right }: StripProps) {
  const top = y + (STRIP_HEIGHT - left.getHeight()) / 2;
  return (
    <>
      <RoundedRect x={x} y={y} width={width} height={STRIP_HEIGHT} r={STRIP_RADIUS} color={STRIP} />
      <RoundedRect x={x + width / 2 - 0.5} y={y} width={1} height={STRIP_HEIGHT} r={0} color={BACKGROUND} />
      <Paragraph paragraph={left} x={x} y={top} width={width / 2} />
      <Paragraph paragraph={right} x={x + width / 2} y={top} width={width / 2} />
    </>
  );
}

/**
 * A loan application, one question to a screen: how much (a large figure over a ruler that is
 * dragged under a fixed marker), over how many months (the same ruler), then a summary to confirm.
 * NEXT sends the question, the label and the figure up and away, smeared, while the next ones rise
 * from below; the ruler and the totals stay between the two rulers, and the line over the footer
 * grows. Nothing is sent: `onConfirm` receives the loan.
 */
export function LoanStepQuestions({
  amount: startAmount = 25000,
  minAmount = 1000,
  maxAmount = 100000,
  amountStep = 1000,
  months: startMonths = 12,
  minMonths = 6,
  maxMonths = 96,
  rate = 12.9,
  symbol = '$',
  questions = ['How much would you like to borrow?', 'Over how many months would you like to repay?', 'Here is your loan. Does it look right?'],
  labels = ['Amount', 'Months', 'Monthly payment'],
  monthlyLabel = 'Monthly',
  totalLabel = 'Total',
  amountLabel = 'Amount',
  termLabel = 'Term',
  rateLabel = 'Rate',
  monthsUnit = 'months',
  previousLabel = 'PREV',
  nextLabel = 'NEXT',
  confirmLabel = 'CONFIRM',
  sentLabel = 'SENT',
  autoPlay = false,
  onChange,
  onConfirm,
  onCollapse,
}: Props) {
  const reducedMotion = useReducedMotion();
  const insets = useSafeAreaInsets();
  const window = useWindowDimensions();
  // The screen as laid out; the window's size stands in until then.
  const [laidOut, setLaidOut] = useState<{ width: number; height: number } | null>(null);
  const size = laidOut ?? window;
  // The page shown, or the one the step change is going to: 0 the amount, 1 the months, 2 the summary.
  const [step, setStep] = useState(0);
  const [turning, setTurning] = useState(false);
  const [amount, setAmount] = useState(clamp(startAmount, minAmount, maxAmount));
  const [months, setMonths] = useState(clamp(startMonths, minMonths, maxMonths));
  const [sent, setSent] = useState(false);
  // What the summary shows: the loan as it was when the summary was opened, so that it does not
  // change under the eyes while the form goes back to its first question.
  const [summed, setSummed] = useState<Loan | null>(null);
  // NEXT held down by the autoPlay loop.
  const [autoPressed, setAutoPressed] = useState(false);
  // Changes on every touch, which restarts the autoPlay loop behind a pause.
  const [touches, setTouches] = useState(0);

  // How far each ruler is pulled, in points: TICK_STEP for every step above the least value.
  const amountPull = (value: number) => ((value - minAmount) / amountStep) * TICK_STEP;
  const monthsPull = (value: number) => (value - minMonths) * TICK_STEP;
  const amountOffset = useSharedValue(amountPull(clamp(startAmount, minAmount, maxAmount)));
  const monthsOffset = useSharedValue(monthsPull(clamp(startMonths, minMonths, maxMonths)));
  const amountReach = amountPull(maxAmount);
  const monthsReach = monthsPull(maxMonths);
  const from = useSharedValue(0);
  const to = useSharedValue(0);
  const progress = useSharedValue(1);
  const dragFrom = useSharedValue(0);
  // A finger moved the ruler last, so its ticks are felt.
  const felt = useSharedValue(0);
  // The line over the footer, 0 to 1.
  const line = useSharedValue(0);
  // The dot that stands for the finger in autoPlay: where it is, whether it rides the ruler, how much it shows.
  const dotX = useSharedValue(0);
  const dotY = useSharedValue(0);
  const dotRides = useSharedValue(0);
  const dotShown = useSharedValue(0);

  const turn = useMemo(() => ({ from, to, progress }), [from, to, progress]);
  const still = reducedMotion;
  const loan = cost(amount, months, rate);
  const summary = summed ?? loan;

  const width = size.width;
  const centre = width / 2;
  const footer = FOOTER_HEIGHT + Math.max(insets.bottom, 12);
  const room = size.height - insets.top - BAR - footer - CONTENT;
  const top = insets.top + BAR + Math.max(8, room * LEAD);
  const inner = width - MARGIN * 2;
  const rulerY = top + RULER_AT;

  const fonts = useMemo(
    () => ({
      figure: matchFont({ fontFamily: FONT, fontSize: FIGURE, fontWeight: '300' }),
      end: matchFont({ fontFamily: FONT, fontSize: 11, fontWeight: '500' }),
    }),
    [],
  );

  const texts = useMemo(
    () => ({
      questions: questions.map((question) => paragraph(question, QUESTION, QUESTION_LINE, 300, INK, inner, TextAlign.Left)),
      labels: labels.map((label) => paragraph(label, LABEL, 22, 400, INK, inner, TextAlign.Left)),
    }),
    [questions, labels, inner],
  );

  const strips = useMemo(() => {
    const cell = (text: string, color: string) => paragraph(text, 13, 18, 500, color, inner / 2, TextAlign.Center);
    return {
      monthly: cell(`${monthlyLabel}: ${symbol}${grouped(loan.monthly)}`, INK),
      total: cell(`${totalLabel}: ${symbol}${grouped(loan.total)}`, BLUE),
      amount: cell(`${amountLabel}: ${symbol}${grouped(summary.amount)}`, INK),
      term: cell(`${termLabel}: ${summary.months} ${monthsUnit}`, INK),
      rate: cell(`${rateLabel}: ${rate}%`, INK),
      sum: cell(`${totalLabel}: ${symbol}${grouped(summary.total)}`, BLUE),
    };
  }, [loan.monthly, loan.total, summary.amount, summary.months, summary.total, rate, symbol, inner, monthlyLabel, totalLabel, amountLabel, termLabel, rateLabel, monthsUnit]);

  /** A touch takes over from the autoPlay loop, which starts again from rest a while later. */
  const touched = () => {
    setAutoPressed(false);
    cancelAnimation(dotShown);
    dotShown.set(0);
    setTouches((n) => n + 1);
  };

  const settled = () => setTurning(false);

  /** Changes page: the one shown leaves, the other comes, forwards or backwards. */
  const go = (next: number) => {
    if (next === step || turning) return;
    setTurning(true);
    setStep(next);
    if (next === 2) setSummed(loan);
    from.set(step);
    to.set(next);
    progress.set(0);
    progress.set(
      withTiming(1, { duration: (still ? TURN / 2 : TURN) * 1000, easing: Easing.linear }, (finished) => {
        if (finished) scheduleOnRN(settled);
      }),
    );
  };

  const next = (auto: boolean) => {
    if (turning) return;
    if (step < 2) {
      if (!auto) tap();
      go(step + 1);
    } else if (!sent) {
      if (process.env.EXPO_OS !== 'web') Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success).catch(() => {});
      setSent(true);
      onConfirm?.(summary);
    }
  };

  const previous = () => {
    if (turning || step === 0) return;
    tap();
    setSent(false);
    go(step - 1);
  };

  /** Back to the first question with the values the form was given. */
  const restart = () => {
    setSent(false);
    const firstAmount = amountPull(clamp(startAmount, minAmount, maxAmount));
    const firstMonths = monthsPull(clamp(startMonths, minMonths, maxMonths));
    // On the summary the rulers are out of sight and are simply put back; otherwise they slide.
    amountOffset.set(step === 2 ? firstAmount : withSpring(firstAmount, SNAP));
    monthsOffset.set(step === 2 ? firstMonths : withSpring(firstMonths, SNAP));
    go(0);
  };

  useEffect(() => {
    line.set(withTiming((step + (sent ? 1 : 0)) / 3, { duration: 500, easing: Easing.out(Easing.cubic) }));
  }, [step, sent, line]);

  // The figures follow the rulers, to a tenth of a tick while they move.
  const setAmountFrom = (tenths: number) => setAmount(clamp(minAmount + (tenths * amountStep) / FINE, minAmount, maxAmount));
  const setMonthsFrom = (ticks: number) => setMonths(clamp(minMonths + ticks, minMonths, maxMonths));
  useAnimatedReaction(
    () => Math.round((amountOffset.get() / TICK_STEP) * FINE),
    (now, old) => {
      if (now === old) return;
      scheduleOnRN(setAmountFrom, now);
      if (felt.get() === 1 && old !== null && Math.floor(now / FINE) !== Math.floor(old / FINE)) scheduleOnRN(tick);
    },
  );
  useAnimatedReaction(
    () => Math.round(monthsOffset.get() / TICK_STEP),
    (now, old) => {
      if (now === old) return;
      scheduleOnRN(setMonthsFrom, now);
      if (felt.get() === 1 && old !== null) scheduleOnRN(tick);
    },
  );

  const reported = useRef({ amount, months });
  useEffect(() => {
    if (reported.current.amount === amount && reported.current.months === months) return;
    reported.current = { amount, months };
    onChange?.(cost(amount, months, rate));
  }, [amount, months, rate, onChange]);

  const pan = Gesture.Pan()
    .enabled(step < 2 && !turning)
    .activeOffsetX([-6, 6])
    .failOffsetY([-16, 16])
    .onStart(() => {
      const offset = to.get() === 0 ? amountOffset : monthsOffset;
      cancelAnimation(offset);
      dragFrom.set(offset.get());
      felt.set(1);
      scheduleOnRN(touched);
    })
    .onUpdate((event) => {
      const offset = to.get() === 0 ? amountOffset : monthsOffset;
      const reach = to.get() === 0 ? amountReach : monthsReach;
      const pulled = dragFrom.get() - event.translationX;
      // Past either end the ruler follows only a little.
      offset.set(pulled < 0 ? pulled * RUBBER : pulled > reach ? reach + (pulled - reach) * RUBBER : pulled);
    })
    .onEnd((event) => {
      const offset = to.get() === 0 ? amountOffset : monthsOffset;
      const reach = to.get() === 0 ? amountReach : monthsReach;
      const thrown = offset.get() - event.velocityX * GLIDE;
      const rest = clamp(Math.round(thrown / TICK_STEP) * TICK_STEP, 0, reach);
      offset.set(
        withSpring(rest, SNAP, () => {
          felt.set(0);
        }),
      );
    });

  /** One tick up or down, for the accessibility actions. */
  const nudge = (by: number) => {
    const offset = step === 0 ? amountOffset : monthsOffset;
    const pulled = step === 0 ? amountPull(amount) : monthsPull(months);
    offset.set(withSpring(clamp(Math.round(pulled / TICK_STEP + by) * TICK_STEP, 0, step === 0 ? amountReach : monthsReach), SNAP));
  };

  const nextX = width * 0.75;
  const nextY = size.height - footer + TRACK_HEIGHT + (FOOTER_HEIGHT - TRACK_HEIGHT) / 2;

  /** A drag by the loop: the dot lands beside the marker and pulls the ruler of `page` to its value. */
  const autoDrag = (page: number, seconds: number) => {
    const offset = page === 0 ? amountOffset : monthsOffset;
    const start = page === 0 ? amountPull(amount) : monthsPull(months);
    const rest = page === 0 ? amountPull(clamp(AUTO_AMOUNT, minAmount, maxAmount)) : monthsPull(clamp(AUTO_MONTHS, minMonths, maxMonths));
    dragFrom.set(start);
    dotRides.set(1);
    // The finger starts as far right of the marker as it ends left of it.
    dotX.set(centre + clamp(rest - start, -inner * 0.7, inner * 0.7) / 2);
    dotY.set(rulerY);
    dotShown.set(withSequence(withTiming(1, { duration: 140 }), withDelay(seconds * 1000, withTiming(0, { duration: 220 }))));
    offset.set(withDelay(140, withTiming(rest, { duration: seconds * 1000, easing: Easing.inOut(Easing.cubic) })));
  };

  /** A tap by the loop on NEXT: the dot shows there and the button is held for a moment. */
  const autoPress = () => {
    dotRides.set(0);
    dotX.set(nextX);
    dotY.set(nextY);
    dotShown.set(withSequence(withTiming(1, { duration: 120 }), withDelay(180, withTiming(0, { duration: 240 }))));
    setAutoPressed(true);
  };

  // The loop reads the latest state when its timers fire.
  const latest = useRef({ next, restart, autoDrag, autoPress, turning });
  useEffect(() => {
    latest.current = { next, restart, autoDrag, autoPress, turning };
  });

  useEffect(() => {
    if (!autoPlay) return;
    const timers: ReturnType<typeof setTimeout>[] = [];
    const after = (ms: number, run: () => void) => timers.push(setTimeout(run, ms));
    const cycle = (origin: number) => {
      const at = (seconds: number, run: () => void) => after(origin + seconds * 1000 - Date.now(), run);
      const press = (seconds: number) => {
        at(seconds, () => latest.current.autoPress());
        at(seconds + PRESS_FOR, () => {
          setAutoPressed(false);
          latest.current.next(true);
        });
      };
      at(DRAG_AMOUNT_AT, () => latest.current.autoDrag(0, DRAG_AMOUNT_FOR));
      press(NEXT_AT);
      at(DRAG_MONTHS_AT, () => latest.current.autoDrag(1, DRAG_MONTHS_FOR));
      press(SUMMARY_NEXT_AT);
      at(RESTART_AT, () => latest.current.restart());
      at(LOOP, () => cycle(origin + LOOP * 1000));
    };
    const resume = () => {
      if (latest.current.turning) return after(100, resume);
      latest.current.restart();
      after((TURN + 0.5) * 1000, () => cycle(Date.now()));
    };
    if (touches === 0) cycle(Date.now());
    else after(USER_HOLD * 1000, resume);
    return () => timers.forEach(clearTimeout);
  }, [autoPlay, touches]);

  // The ruler shows the months' pull and ends by this share: it slides from one to the other
  // in the middle of a step change between the two.
  const monthsShare = useDerivedValue(() => {
    const start = from.get() === 2 ? to.get() : from.get();
    const end = to.get() === 2 ? from.get() : to.get();
    return start + (end - start) * smooth(progress.get(), 0.3, 0.7);
  });
  const shownOffset = useDerivedValue(() => amountOffset.get() + (monthsOffset.get() - amountOffset.get()) * monthsShare.get());
  const amountEnds = useDerivedValue(() => 1 - monthsShare.get());
  const dip = useDerivedValue(() => {
    const between = from.get() !== to.get() && from.get() < 2 && to.get() < 2;
    if (!between || still) return [{ translateY: 0 }];
    return [{ translateY: (from.get() < to.get() ? -1 : 1) * DIP * Math.sin(2 * Math.PI * smooth(progress.get(), 0, 1)) }];
  });

  const fillStyle = useAnimatedStyle(() => ({ width: `${line.get() * 100}%` }));
  const dotStyle = useAnimatedStyle(() => {
    const offset = to.get() === 0 ? amountOffset : monthsOffset;
    const ridden = dotRides.get() === 1 ? offset.get() - dragFrom.get() : 0;
    return {
      opacity: dotShown.get(),
      transform: [{ translateX: dotX.get() - ridden - DOT / 2 }, { translateY: dotY.get() - DOT / 2 }, { scale: 0.7 + 0.3 * dotShown.get() }],
    };
  });

  const onLayout = (event: LayoutChangeEvent) => {
    setLaidOut({ width: event.nativeEvent.layout.width, height: event.nativeEvent.layout.height });
  };

  const figures = [`${symbol}${grouped(amount)}`, String(months), `${symbol}${grouped(summary.monthly)}`];
  const ends = [
    [short(minAmount), short(maxAmount)],
    [String(minMonths), String(maxMonths)],
  ];
  const endWidth = (text: string) => fonts.end.getGlyphWidths(fonts.end.getGlyphIDs(text)).reduce((sum, advance) => sum + advance, 0);
  const rulerLeft = MARGIN + 26;
  const rulerRight = width - MARGIN - 34;
  const questionBounds = { x: 0, y: top - 30, width, height: QUESTION_LINE * 2 + 60 };
  const labelBounds = { x: 0, y: top + LABEL_AT - 30, width, height: 82 };
  const figureBounds = { x: 0, y: top + FIGURE_BASE - FIGURE - 16, width, height: FIGURE + 60 };
  const rulerBounds = { x: 0, y: rulerY - 56, width, height: 112 };
  const stripBounds = (y: number) => ({ x: 0, y: top + y - 30, width, height: STRIP_HEIGHT + 60 });
  const last = step === 2;

  return (
    <View style={styles.screen} onLayout={onLayout}>
      <StatusBar style="dark" />

      <Canvas style={StyleSheet.absoluteFill}>
        {[0, 1, 2].map((page) => (
          <Group key={page}>
            <Part turn={turn} pages={1 << page} order={0} still={still} bounds={questionBounds}>
              <Paragraph paragraph={texts.questions[page]} x={MARGIN} y={top} width={inner} />
            </Part>
            <Part turn={turn} pages={1 << page} order={1} still={still} bounds={labelBounds}>
              <Paragraph paragraph={texts.labels[page]} x={MARGIN} y={top + LABEL_AT} width={inner} />
            </Part>
            <Part turn={turn} pages={1 << page} order={2} still={still} bounds={figureBounds}>
              <Figure text={figures[page]} x={MARGIN - 3} y={top + FIGURE_BASE} font={fonts.figure} symbol={symbol} />
            </Part>
          </Group>
        ))}

        {/* The ruler and the totals belong to both questions, so they stay between them. */}
        <Part turn={turn} pages={0b011} order={3} still={still} bounds={rulerBounds}>
          <Group transform={dip}>
            <Ruler
              offset={shownOffset}
              left={rulerLeft}
              right={rulerRight}
              centre={centre}
              y={rulerY}
              tickColor={TICKS}
              markerColor={BLUE}
              background={BACKGROUND}
            />
            <Glyphs text={ends[0][0]} font={fonts.end} x={MARGIN} y={rulerY + 4} color={INK} opacity={amountEnds} />
            <Glyphs text={ends[0][1]} font={fonts.end} x={width - MARGIN - endWidth(ends[0][1])} y={rulerY + 4} color={INK} opacity={amountEnds} />
            <Glyphs text={ends[1][0]} font={fonts.end} x={MARGIN} y={rulerY + 4} color={INK} opacity={monthsShare} />
            <Glyphs text={ends[1][1]} font={fonts.end} x={width - MARGIN - endWidth(ends[1][1])} y={rulerY + 4} color={INK} opacity={monthsShare} />
          </Group>
        </Part>
        <Part turn={turn} pages={0b011} order={4} still={still} bounds={stripBounds(STRIP_AT)}>
          <Strip x={MARGIN} y={top + STRIP_AT} width={inner} left={strips.monthly} right={strips.total} />
        </Part>

        <Part turn={turn} pages={0b100} order={3} still={still} bounds={stripBounds(SUMMARY_AT)}>
          <Strip x={MARGIN} y={top + SUMMARY_AT} width={inner} left={strips.amount} right={strips.term} />
        </Part>
        <Part turn={turn} pages={0b100} order={4} still={still} bounds={stripBounds(SUMMARY_AT + STRIP_HEIGHT + STRIP_GAP)}>
          <Strip x={MARGIN} y={top + SUMMARY_AT + STRIP_HEIGHT + STRIP_GAP} width={inner} left={strips.rate} right={strips.sum} />
        </Part>
      </Canvas>

      {/* What the canvas shows, for a screen reader. */}
      <View
        pointerEvents="none"
        style={[styles.reading, { top, height: FIGURE_BASE }]}
        accessible
        accessibilityRole="header"
        accessibilityLabel={`${questions[step]} ${labels[step]}: ${step === 1 ? `${months} ${monthsUnit}` : figures[step]}`}
      />

      <GestureDetector gesture={pan}>
        <View
          style={[styles.ruler, { top: rulerY - 36 }]}
          pointerEvents={last ? 'none' : 'auto'}
          accessible={!last}
          accessibilityRole="adjustable"
          accessibilityLabel={labels[Math.min(step, 1)]}
          accessibilityValue={{ text: step === 0 ? figures[0] : `${months} ${monthsUnit}` }}
          accessibilityActions={[{ name: 'increment' }, { name: 'decrement' }]}
          onAccessibilityAction={(event) => nudge(event.nativeEvent.actionName === 'increment' ? 1 : -1)}
        />
      </GestureDetector>

      <Pressable
        style={[styles.collapse, { top: insets.top }]}
        accessibilityRole="button"
        accessibilityLabel="Short form"
        onPress={() => {
          touched();
          onCollapse?.();
        }}
      >
        <MaterialCommunityIcons name="arrow-collapse" size={22} color={INK} />
      </Pressable>

      <View style={[styles.footer, { height: footer }]}>
        <View style={styles.track}>
          <Animated.View style={[styles.fill, fillStyle]} />
        </View>
        <View style={styles.actions}>
          <Pressable
            style={styles.action}
            disabled={step === 0}
            accessibilityRole="button"
            accessibilityState={{ disabled: step === 0 }}
            onPress={() => {
              touched();
              previous();
            }}
          >
            {({ pressed }) => (
              <View style={[styles.actionRow, { opacity: pressed ? 0.5 : 1 }]}>
                <Ionicons name="chevron-up" size={18} color={step === 0 ? PALE : BLUE} />
                <Text style={[styles.actionLabel, { color: step === 0 ? PALE : BLUE }]}>{previousLabel}</Text>
              </View>
            )}
          </Pressable>
          <Pressable
            style={styles.action}
            accessibilityRole="button"
            onPress={() => {
              touched();
              next(false);
            }}
          >
            {({ pressed }) => (
              // On the summary NEXT becomes a blue bar that confirms.
              <Animated.View
                style={[
                  styles.actionRow,
                  styles.actionFill,
                  {
                    backgroundColor: last ? BLUE : FOOTER,
                    opacity: pressed || autoPressed ? 0.5 : 1,
                    transitionProperty: ['backgroundColor', 'opacity'],
                    transitionDuration: [350, 100],
                  },
                ]}
              >
                <Text style={[styles.actionLabel, { color: last ? '#FFFFFF' : BLUE }]}>{last ? (sent ? sentLabel : confirmLabel) : nextLabel}</Text>
                <Ionicons name={last ? (sent ? 'checkmark-done' : 'checkmark') : 'chevron-down'} size={18} color={last ? '#FFFFFF' : BLUE} />
              </Animated.View>
            )}
          </Pressable>
        </View>
      </View>

      <Animated.View pointerEvents="none" style={[styles.dot, dotStyle]} />
    </View>
  );
}

const styles = StyleSheet.create({
  screen: {
    flex: 1,
    backgroundColor: BACKGROUND,
  },
  reading: {
    position: 'absolute',
    left: MARGIN,
    right: MARGIN,
  },
  ruler: {
    position: 'absolute',
    left: 0,
    right: 0,
    height: 72,
  },
  collapse: {
    position: 'absolute',
    right: MARGIN - 11,
    width: BAR,
    height: BAR,
    alignItems: 'center',
    justifyContent: 'center',
  },
  footer: {
    position: 'absolute',
    left: 0,
    right: 0,
    bottom: 0,
    backgroundColor: FOOTER,
  },
  track: {
    height: TRACK_HEIGHT,
    backgroundColor: TRACK,
  },
  fill: {
    height: TRACK_HEIGHT,
    backgroundColor: BLUE,
  },
  actions: {
    flex: 1,
    flexDirection: 'row',
  },
  action: {
    flex: 1,
  },
  actionRow: {
    height: FOOTER_HEIGHT - TRACK_HEIGHT,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 8,
  },
  // The right half is filled down to the bottom of the screen.
  actionFill: {
    flex: 1,
    height: undefined,
    alignItems: 'flex-start',
    paddingTop: (FOOTER_HEIGHT - TRACK_HEIGHT - 18) / 2,
  },
  actionLabel: {
    fontSize: 13,
    lineHeight: 18,
    fontWeight: '700',
    letterSpacing: 0.6,
  },
  dot: {
    position: 'absolute',
    left: 0,
    top: 0,
    width: DOT,
    height: DOT,
    borderRadius: DOT / 2,
    backgroundColor: 'rgba(63,91,217,0.16)',
  },
});
