import { BlurStyle, Canvas, PaintStyle, Picture, Skia, StrokeCap, StrokeJoin, createPicture } from '@shopify/react-native-skia';
import { Ionicons } from '@expo/vector-icons';
import * as Haptics from 'expo-haptics';
import { LinearGradient } from 'expo-linear-gradient';
import { StatusBar } from 'expo-status-bar';
import { useEffect, useRef, useState } from 'react';
import { type LayoutChangeEvent, Pressable, StyleSheet, Text, View } from 'react-native';
import Animated, {
  type SharedValue,
  useAnimatedStyle,
  useDerivedValue,
  useFrameCallback,
  useReducedMotion,
  useSharedValue,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';

import { drawPlate } from './Plate';

export type CartOption = { name: string; price: string };

type Props = {
  title?: string;
  description?: string;
  price?: string;
  optionsTitle?: string;
  optionalLabel?: string;
  options?: CartOption[];
  /** Taps "+" by itself every LOOP seconds, singly and in held bursts, and empties the basket at the end. */
  autoPlay?: boolean;
  /** Called with the stepper's count whenever it changes by hand. */
  onChange?: (count: number) => void;
};

/** autoPlay: one round of taps every LOOP seconds. */
export const LOOP = 10;
/** After a touch autoPlay waits this long, empties the basket, and starts over from rest. */
const USER_HOLD = 3;
// Holding "+" or "−" repeats after HOLD_AFTER, every REPEAT seconds.
const HOLD_AFTER = 0.4;
const REPEAT = 0.15;

// Events on the run's clock.
const ADD = 0;
const REMOVE = 1;
const RESET = 2;
const LIFT = 3;
// autoPlay's round, in seconds from its start: single taps, a held burst, more taps, a lift as the
// last token lands, and the counts rolling back to 0.
const ROUND: [number, number][] = [
  [0.8, ADD],
  [1.4, ADD],
  [2.1, ADD],
  [2.28, ADD],
  [2.46, ADD],
  [2.64, ADD],
  [2.82, ADD],
  [3.6, ADD],
  [4.1, ADD],
  [4.7, ADD],
  [4.88, ADD],
  [5.06, ADD],
  [5.6, LIFT],
  [8.4, RESET],
];

// Motion, in seconds.
const FLIGHT = 0.6;
const POP = 0.15;
const ROLL = 0.25;
const RESET_ROLL = 0.4;
const PRESS_FOR = 0.3;
const LIFT_FOR = 0.6;
const BUMP = 0.32;
const BUMP_DECAY = 7;
const BUMP_PERIOD = 0.36;
const BUMP_FOR = 0.6;
// Events older than this have finished every animation they started, and are folded into the counts.
const SETTLE = 1.5;

// Sizes in points.
const SHEET_GAP = 10;
const SHEET_RADIUS = 32;
const SIDE = 20;
const GRABBER_WIDTH = 36;
const GRABBER_HEIGHT = 5;
const PANEL_SHARE = 0.3;
const PANEL_MIN = 160;
const PANEL_MAX = 280;
const PANEL_RADIUS = 22;
const PLATE_SHARE = 0.78;
const ROW_HEIGHT = 60;
const ROW_RADIUS = 16;
const CHECK = 22;
const BAR = 56;
const BAR_MARGIN = 16;
const FADE = 48;
const STEPPER_WIDTH = 220;
const GLYPH_INSET = 32;
const GLYPH = 14;
const GLYPH_STROKE = 2;
const PRESS_RADIUS = 20;
const BASKET_GAP = 40;
const BASKET = 24;
const BASKET_COUNT_GAP = 18;
const TOKEN = 30;
const TOKEN_RADIUS = 9;
const TOKEN_PLATE = 22;
const TOKEN_END_SCALE = 0.45;
// The flight's arc rises this far above the higher of its two ends, at its control point.
const ARC_LIFT = 120;
const COUNT_LINE = 24;
const LIFT_SCALE = 0.04;
const LIFT_RISE = 10;

// A basket in a 24 × 24 box: handles, body, and two slats.
const BASKET_PATH = 'M6 10L9 4M18 10L15 4M3 10H21L19.2 19.4Q18.9 21 17.3 21H6.7Q5.1 21 4.8 19.4Z M9.5 13.5V17.5M14.5 13.5V17.5';

const SCREEN = '#000000';
const SHEET = '#FFFFFF';
const GRABBER = '#D0D0D4';
const PANEL = '#F3F3F5';
const INK = '#111111';
const BODY = '#555555';
const MUTE = '#888888';
const PILL = '#EEEEEE';
const PILL_INK = '#666666';
const ROW = '#F2F2F4';
const CHECK_EDGE = '#C8C8CC';
const DIVIDER = '#ECECEE';
const STEPPER = '#ECECEE';
const PRESS = '#FFFFFF';

const OPTIONS: CartOption[] = [
  { name: 'Vegan', price: '+ 0,00' },
  { name: 'Extra avocado', price: '+ 1,50' },
  { name: 'Chicken instead of salmon', price: '+ 2,00' },
];

type Scene = {
  stepFrom: number;
  stepTo: number;
  stepAt: number;
  stepFor: number;
  basketFrom: number;
  basketTo: number;
  basketAt: number;
  bump: number;
  lift: number;
  pressMinus: number;
  pressPlus: number;
  tokens: number[];
};

function clamp(x: number) {
  'worklet';
  return Math.max(0, Math.min(1, x));
}

function easeOut(x: number) {
  'worklet';
  return 1 - Math.pow(1 - x, 3);
}

function easeIn(x: number) {
  'worklet';
  return x * x;
}

function pressCurve(dt: number) {
  'worklet';
  return dt < 0 || dt > PRESS_FOR ? 0 : Math.sin((Math.PI * dt) / PRESS_FOR);
}

/** The basket's kick when a token lands: up to about 1.25, then settling back to 1. */
function kick(dt: number) {
  'worklet';
  return dt < 0 || dt > BUMP_FOR ? 0 : BUMP * Math.exp(-BUMP_DECAY * dt) * Math.sin((2 * Math.PI * dt) / BUMP_PERIOD);
}

/** Everything the screen shows at `t`, from the events so far and the counts they started from. */
function sceneOf(t: number, events: number[][], baseStep: number, baseBasket: number): Scene {
  'worklet';
  const scene: Scene = {
    stepFrom: baseStep,
    stepTo: baseStep,
    stepAt: -1e9,
    stepFor: ROLL,
    basketFrom: baseBasket,
    basketTo: baseBasket,
    basketAt: -1e9,
    bump: 0,
    lift: 0,
    pressMinus: 0,
    pressPlus: 0,
    tokens: [],
  };
  let step = baseStep;
  let basket = baseBasket;
  for (let i = 0; i < events.length; i++) {
    const at = events[i][0];
    const kind = events[i][1];
    if (at > t) break;
    const before = step;
    if (kind === ADD) {
      step += 1;
      scene.pressPlus = Math.max(scene.pressPlus, pressCurve(t - at));
      const landing = at + FLIGHT;
      if (landing <= t) {
        scene.basketFrom = basket;
        basket += 1;
        scene.basketTo = basket;
        scene.basketAt = landing;
        scene.bump += kick(t - landing);
      } else {
        scene.tokens.push(t - at);
      }
    } else if (kind === REMOVE) {
      step = Math.max(0, step - 1);
      scene.pressMinus = Math.max(scene.pressMinus, pressCurve(t - at));
    } else if (kind === RESET) {
      if (basket !== 0) {
        scene.basketFrom = basket;
        basket = 0;
        scene.basketTo = 0;
        scene.basketAt = at;
      }
      step = 0;
    } else if (kind === LIFT) {
      const x = clamp((t - at) / LIFT_FOR);
      scene.lift = Math.max(scene.lift, x <= 0 || x >= 1 ? 0 : 0.5 - 0.5 * Math.cos(2 * Math.PI * x));
    }
    if (step !== before) {
      scene.stepFrom = before;
      scene.stepTo = step;
      scene.stepAt = at;
      scene.stepFor = kind === RESET ? RESET_ROLL : ROLL;
    }
  }
  scene.bump = Math.max(-0.3, Math.min(0.3, scene.bump));
  return scene;
}

/** The events on screen at `now`: autoPlay's current round, or the ones made by hand. */
function eventsAt(now: number, loopStart: number, hand: number[][]) {
  'worklet';
  if (loopStart < 0 || now < loopStart) return hand;
  const base = loopStart + Math.floor((now - loopStart) / LOOP) * LOOP;
  const events: number[][] = [];
  for (let i = 0; i < ROUND.length; i++) events.push([ROUND[i][0] + base, ROUND[i][1]]);
  return events;
}

function color(hex: string, alpha: number) {
  'worklet';
  const n = parseInt(hex.slice(1), 16);
  return Float32Array.of(((n >> 16) & 255) / 255, ((n >> 8) & 255) / 255, (n & 255) / 255, clamp(alpha));
}

/** A number that rolls from `from` to `to`: up when it grows, down when it shrinks; a fade under reduced motion. */
function RollingCount({
  from,
  to,
  progress,
  reduced,
  style,
}: {
  from: number;
  to: number;
  progress: SharedValue<number>;
  reduced: boolean;
  style: object;
}) {
  const dir = to >= from ? 1 : -1;
  const outStyle = useAnimatedStyle(() => ({
    opacity: 1 - progress.get(),
    transform: [{ translateY: reduced ? 0 : -dir * COUNT_LINE * progress.get() }],
  }));
  const inStyle = useAnimatedStyle(() => ({
    opacity: progress.get(),
    transform: [{ translateY: reduced ? 0 : dir * COUNT_LINE * (1 - progress.get()) }],
  }));
  return (
    <View style={styles.roll}>
      {from !== to && <Animated.Text style={[styles.rollText, style, outStyle]}>{from}</Animated.Text>}
      <Animated.Text style={[styles.rollText, style, from !== to ? inStyle : null]}>{to}</Animated.Text>
    </View>
  );
}

export function CartFlyStepper({
  title = 'Ginger-Soy Salmon Fillet',
  description = 'edamame, cucumber, avocado,\nherbs, white rice',
  price = '$9.95',
  optionsTitle = 'Options',
  optionalLabel = 'Optional',
  options = OPTIONS,
  autoPlay = false,
  onChange,
}: Props) {
  const reduced = useReducedMotion();
  const insets = useSafeAreaInsets();
  const [size, setSize] = useState({ width: 0, height: 0 });
  const [counts, setCounts] = useState([0, 0, 0, 0]);
  const [checked, setChecked] = useState<boolean[]>([]);
  const holdTimer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const repeatTimer = useRef<ReturnType<typeof setInterval> | null>(null);

  const { width, height } = size;
  const sheetTop = insets.top + SHEET_GAP;
  const sheetHeight = height - sheetTop;
  const panel = Math.max(PANEL_MIN, Math.min(PANEL_MAX, height * PANEL_SHARE));
  const barY = sheetHeight - Math.max(insets.bottom, BAR_MARGIN) - 6 - BAR / 2;
  const minusX = SIDE + GLYPH_INSET;
  const plusX = SIDE + STEPPER_WIDTH - GLYPH_INSET;
  const basketX = SIDE + STEPPER_WIDTH + BASKET_GAP;

  // The clock in seconds, autoPlay's round start (-1 when it is off), and the events made by hand.
  const clock = useSharedValue(0);
  const loopStart = useSharedValue(-1);
  const hand = useSharedValue<number[][]>([]);
  const baseStep = useSharedValue(0);
  const baseBasket = useSharedValue(0);
  const touchedAt = useSharedValue(-1e9);
  const held = useSharedValue(false);
  const stepP = useSharedValue(1);
  const basketP = useSharedValue(1);
  const lift = useSharedValue(0);
  const shown = useSharedValue([0, 0, 0, 0]);

  const tick = () => {
    if (process.env.EXPO_OS !== 'web') Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
  };
  const changed = (count: number) => onChange?.(count);

  useFrameCallback((frame) => {
    const now = frame.timestamp / 1000;
    clock.set(now);
    if (autoPlay && loopStart.get() < 0 && !held.get() && now - touchedAt.get() >= USER_HOLD) {
      const s = sceneOf(now, hand.get(), baseStep.get(), baseBasket.get());
      if (s.stepTo !== 0 || s.basketTo !== 0) {
        // Empty the basket first, then start the round again from rest.
        const events = hand.get().slice();
        events.push([now, RESET]);
        hand.set(events);
        loopStart.set(now + RESET_ROLL + 0.2);
      } else {
        hand.set([]);
        baseStep.set(0);
        baseBasket.set(0);
        loopStart.set(now);
      }
    }
    const s = sceneOf(now, eventsAt(now, loopStart.get(), hand.get()), baseStep.get(), baseBasket.get());
    stepP.set(clamp((now - s.stepAt) / s.stepFor));
    basketP.set(clamp((now - s.basketAt) / ROLL));
    lift.set(reduced ? 0 : s.lift);
    const last = shown.get();
    if (last[0] !== s.stepFrom || last[1] !== s.stepTo || last[2] !== s.basketFrom || last[3] !== s.basketTo) {
      const next = [s.stepFrom, s.stepTo, s.basketFrom, s.basketTo];
      shown.set(next);
      scheduleOnRN(setCounts, next);
    }
  });

  /** A touch during autoPlay takes over: the round so far becomes the events made by hand. */
  const takeOver = (now: number) => {
    'worklet';
    touchedAt.set(now);
    const start = loopStart.get();
    if (start < 0) return;
    if (now >= start) {
      const events = eventsAt(now, start, []);
      const kept: number[][] = [];
      for (let i = 0; i < events.length; i++) if (events[i][0] <= now) kept.push(events[i]);
      hand.set(kept);
      baseStep.set(0);
      baseBasket.set(0);
    }
    loopStart.set(-1);
  };

  /** Folds events whose animations are over into the counts, so the list stays short. */
  const fold = (now: number) => {
    'worklet';
    const events = hand.get();
    let step = baseStep.get();
    let basket = baseBasket.get();
    let i = 0;
    while (i < events.length && events[i][0] < now - SETTLE) {
      const kind = events[i][1];
      if (kind === ADD) {
        step += 1;
        basket += 1;
      } else if (kind === REMOVE) {
        step = Math.max(0, step - 1);
      } else if (kind === RESET) {
        step = 0;
        basket = 0;
      }
      i++;
    }
    if (i === 0) return;
    baseStep.set(step);
    baseBasket.set(basket);
    hand.set(events.slice(i));
  };

  const press = (kind: number) => {
    'worklet';
    const now = clock.get();
    takeOver(now);
    const before = sceneOf(now, hand.get(), baseStep.get(), baseBasket.get()).stepTo;
    if (kind === REMOVE && before <= 0) return;
    fold(now);
    const events = hand.get().slice();
    events.push([now, kind]);
    hand.set(events);
    scheduleOnRN(tick);
    scheduleOnRN(changed, kind === ADD ? before + 1 : before - 1);
  };

  const stopRepeat = () => {
    if (holdTimer.current) clearTimeout(holdTimer.current);
    if (repeatTimer.current) clearInterval(repeatTimer.current);
    holdTimer.current = null;
    repeatTimer.current = null;
  };
  useEffect(() => stopRepeat, []);

  const pressIn = (kind: number) => {
    held.set(true);
    scheduleOnUI(press, kind);
    stopRepeat();
    holdTimer.current = setTimeout(() => {
      repeatTimer.current = setInterval(() => scheduleOnUI(press, kind), REPEAT * 1000);
    }, HOLD_AFTER * 1000);
  };
  const pressOut = () => {
    stopRepeat();
    held.set(false);
    scheduleOnUI(takeOver, clock.get());
  };

  const overlay = useDerivedValue(() => {
    const now = clock.get();
    const s = sceneOf(now, eventsAt(now, loopStart.get(), hand.get()), baseStep.get(), baseBasket.get());
    return createPicture((canvas) => {
      if (width <= 0) return;
      const glyph = Skia.Paint();
      glyph.setAntiAlias(true);
      glyph.setStyle(PaintStyle.Stroke);
      glyph.setStrokeCap(StrokeCap.Round);
      glyph.setStrokeJoin(StrokeJoin.Round);
      glyph.setStrokeWidth(GLYPH_STROKE);
      glyph.setColor(color(INK, 1));
      const fill = Skia.Paint();
      fill.setAntiAlias(true);

      // "−" and "+", each pressed in when tapped.
      const buttons: [number, number, boolean][] = [
        [minusX, s.pressMinus, false],
        [plusX, s.pressPlus, true],
      ];
      for (let i = 0; i < buttons.length; i++) {
        const [x, pressed, plus] = buttons[i];
        if (pressed > 0) {
          fill.setColor(color(PRESS, 0.95 * pressed));
          canvas.drawCircle(x, barY, PRESS_RADIUS * (0.8 + 0.2 * pressed), fill);
        }
        const g = (GLYPH / 2) * (1 - 0.12 * pressed);
        canvas.drawLine(x - g, barY, x + g, barY, glyph);
        if (plus) canvas.drawLine(x, barY - g, x, barY + g, glyph);
      }

      // The basket, kicked by each token that lands in it.
      const scale = reduced ? 1 : 1 + s.bump;
      canvas.save();
      canvas.translate(basketX, barY);
      canvas.scale(scale, scale);
      canvas.translate(-BASKET / 2, -BASKET / 2);
      glyph.setStrokeWidth(1.8);
      const basket = Skia.Path.MakeFromSVGString(BASKET_PATH);
      if (basket) canvas.drawPath(basket, glyph);
      canvas.restore();

      // Tokens on their arcs from "+" to the basket.
      if (reduced) return;
      const shadow = Skia.Paint();
      shadow.setAntiAlias(true);
      shadow.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 4, true));
      const sx = plusX;
      const sy = barY;
      const ex = basketX;
      const ey = barY;
      const cx = (sx + ex) / 2;
      const cy = Math.min(sy, ey) - ARC_LIFT;
      for (let i = 0; i < s.tokens.length; i++) {
        const dt = s.tokens[i];
        const q = easeIn(clamp(dt / FLIGHT));
        const x = (1 - q) * (1 - q) * sx + 2 * (1 - q) * q * cx + q * q * ex;
        const y = (1 - q) * (1 - q) * sy + 2 * (1 - q) * q * cy + q * q * ey;
        const pop = easeOut(clamp(dt / POP));
        const k = (0.5 + 0.5 * pop) * (1 + (TOKEN_END_SCALE - 1) * q);
        const alpha = clamp(dt / 0.06) * clamp((FLIGHT - dt) / 0.08);
        canvas.save();
        canvas.translate(x, y);
        canvas.rotate(-12 + 30 * q, 0, 0);
        canvas.scale(k, k);
        shadow.setColor(color('#000000', 0.18 * alpha));
        canvas.drawRRect(Skia.RRectXY(Skia.XYWHRect(-TOKEN / 2, -TOKEN / 2 + 2, TOKEN, TOKEN), TOKEN_RADIUS, TOKEN_RADIUS), shadow);
        fill.setColor(color('#FFFFFF', alpha));
        canvas.drawRRect(Skia.RRectXY(Skia.XYWHRect(-TOKEN / 2, -TOKEN / 2, TOKEN, TOKEN), TOKEN_RADIUS, TOKEN_RADIUS), fill);
        drawPlate(canvas, 0, 0, TOKEN_PLATE, alpha);
        canvas.restore();
      }
    });
  });

  const plate = useDerivedValue(() =>
    createPicture((canvas) => {
      if (width <= 0) return;
      drawPlate(canvas, (width - SIDE * 2) / 2, panel / 2, panel * PLATE_SHARE, 1);
    }),
  );

  const sheetStyle = useAnimatedStyle(() => ({
    transform: [{ translateY: -LIFT_RISE * lift.get() }, { scale: 1 - LIFT_SCALE * lift.get() }],
  }));

  const onLayout = (e: LayoutChangeEvent) => {
    const { width: w, height: h } = e.nativeEvent.layout;
    if (w !== width || h !== height) setSize({ width: w, height: h });
  };

  const toggle = (i: number) => {
    scheduleOnUI(takeOver, clock.get());
    if (process.env.EXPO_OS !== 'web') Haptics.selectionAsync().catch(() => {});
    setChecked((was) => {
      const next = was.slice();
      next[i] = !next[i];
      return next;
    });
  };

  return (
    <View style={styles.screen} onLayout={onLayout}>
      <StatusBar style="light" />
      {width > 0 && (
        <Animated.View style={[styles.sheet, { top: sheetTop, height: sheetHeight }, sheetStyle]}>
          <View style={styles.grabber} />
          <View style={[styles.panel, { height: panel }]}>
            <Canvas style={StyleSheet.absoluteFill}>
              <Picture picture={plate} />
            </Canvas>
          </View>
          <Text style={styles.title}>{title}</Text>
          <Text style={styles.description} lineBreakStrategyIOS="standard">
            {description}
          </Text>
          <Text style={styles.price}>{price}</Text>
          <View style={styles.divider} />
          <View style={styles.optionsHead}>
            <Text style={styles.optionsTitle}>{optionsTitle}</Text>
            <View style={styles.pill}>
              <Text style={styles.pillText}>{optionalLabel}</Text>
            </View>
          </View>
          {options.map((option, i) => (
            <Pressable key={option.name} style={styles.row} onPress={() => toggle(i)} accessibilityRole="checkbox" accessibilityState={{ checked: !!checked[i] }}>
              <View>
                <Text style={styles.rowName}>{option.name}</Text>
                <Text style={styles.rowPrice}>{option.price}</Text>
              </View>
              <View style={[styles.check, checked[i] && styles.checkOn]}>{checked[i] && <Ionicons name="checkmark" size={15} color={SHEET} />}</View>
            </Pressable>
          ))}

          <View style={[styles.bar, { top: barY - BAR / 2 - FADE, height: sheetHeight - (barY - BAR / 2 - FADE) }]} pointerEvents="box-none">
            <LinearGradient colors={['rgba(255,255,255,0)', SHEET]} style={styles.fade} pointerEvents="none" />
            <View style={styles.barBack} pointerEvents="none" />
          </View>
          <View style={[styles.stepper, { top: barY - BAR / 2 }]}>
            <Pressable style={styles.button} onPressIn={() => pressIn(REMOVE)} onPressOut={pressOut} accessibilityRole="button" accessibilityLabel="Remove one" />
            <View style={styles.count}>
              <RollingCount from={counts[0]} to={counts[1]} progress={stepP} reduced={reduced} style={styles.countText} />
            </View>
            <Pressable style={styles.button} onPressIn={() => pressIn(ADD)} onPressOut={pressOut} accessibilityRole="button" accessibilityLabel="Add one" />
          </View>
          <View style={[styles.basketCount, { left: basketX + BASKET_COUNT_GAP, top: barY - COUNT_LINE / 2 }]}>
            <RollingCount from={counts[2]} to={counts[3]} progress={basketP} reduced={reduced} style={styles.basketText} />
          </View>
          <View style={StyleSheet.absoluteFill} pointerEvents="none">
            <Canvas style={StyleSheet.absoluteFill}>
              <Picture picture={overlay} />
            </Canvas>
          </View>
        </Animated.View>
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: SCREEN },
  sheet: {
    position: 'absolute',
    left: 0,
    right: 0,
    backgroundColor: SHEET,
    borderTopLeftRadius: SHEET_RADIUS,
    borderTopRightRadius: SHEET_RADIUS,
    paddingHorizontal: SIDE,
    overflow: 'hidden',
  },
  grabber: {
    alignSelf: 'center',
    marginTop: 8,
    width: GRABBER_WIDTH,
    height: GRABBER_HEIGHT,
    borderRadius: GRABBER_HEIGHT / 2,
    backgroundColor: GRABBER,
  },
  panel: { marginTop: 11, borderRadius: PANEL_RADIUS, backgroundColor: PANEL, overflow: 'hidden' },
  title: { marginTop: 20, fontSize: 20, lineHeight: 26, fontWeight: '600', color: INK, includeFontPadding: false },
  description: { marginTop: 4, fontSize: 14, lineHeight: 19, color: BODY, includeFontPadding: false },
  price: { marginTop: 8, fontSize: 16, lineHeight: 22, fontWeight: '500', color: INK, includeFontPadding: false },
  divider: { marginTop: 16, height: StyleSheet.hairlineWidth * 2, backgroundColor: DIVIDER },
  optionsHead: { marginTop: 16, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  optionsTitle: { fontSize: 18, lineHeight: 24, fontWeight: '600', color: INK, includeFontPadding: false },
  pill: { paddingHorizontal: 8, height: 20, borderRadius: 10, backgroundColor: PILL, justifyContent: 'center' },
  pillText: { fontSize: 11, lineHeight: 14, color: PILL_INK, includeFontPadding: false },
  row: {
    marginTop: 12,
    height: ROW_HEIGHT,
    borderRadius: ROW_RADIUS,
    backgroundColor: ROW,
    paddingHorizontal: 16,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  rowName: { fontSize: 14, lineHeight: 18, fontWeight: '600', color: INK, includeFontPadding: false },
  rowPrice: { marginTop: 2, fontSize: 12, lineHeight: 16, color: MUTE, includeFontPadding: false },
  check: { width: CHECK, height: CHECK, borderRadius: 6, borderWidth: 1.5, borderColor: CHECK_EDGE, alignItems: 'center', justifyContent: 'center' },
  checkOn: { backgroundColor: INK, borderColor: INK },
  bar: { position: 'absolute', left: 0, right: 0 },
  fade: { height: FADE },
  barBack: { flex: 1, backgroundColor: SHEET },
  stepper: {
    position: 'absolute',
    left: SIDE,
    width: STEPPER_WIDTH,
    height: BAR,
    borderRadius: BAR / 2,
    backgroundColor: STEPPER,
    flexDirection: 'row',
    alignItems: 'center',
  },
  button: { width: GLYPH_INSET * 2, height: BAR },
  count: { flex: 1, alignItems: 'center' },
  countText: { fontSize: 18, fontWeight: '500', color: INK },
  basketCount: { position: 'absolute' },
  basketText: { fontSize: 16, fontWeight: '500', color: INK },
  roll: { height: COUNT_LINE, minWidth: 28, overflow: 'hidden' },
  rollText: { position: 'absolute', left: 0, right: 0, textAlign: 'center', lineHeight: COUNT_LINE, includeFontPadding: false },
});
