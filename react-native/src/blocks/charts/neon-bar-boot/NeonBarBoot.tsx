import {
  BackdropBlur,
  Canvas,
  Group,
  LinearGradient,
  Path,
  Picture,
  RadialGradient,
  Rect,
  RoundedRect,
  Skia,
  TileMode,
  createPicture,
  rrect,
  vec,
} from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useEffect, useState } from 'react';
import { type LayoutChangeEvent, StyleSheet, Text, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  useAnimatedReaction,
  useAnimatedStyle,
  useDerivedValue,
  useFrameCallback,
  useReducedMotion,
  useSharedValue,
} from 'react-native-reanimated';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';

import { Odometer } from './Odometer';

/** One set of figures the card can show: the total, and the bars as fractions of the tallest. */
export type SalesSet = { total: number; bars: number[] };

const SETS: SalesSet[] = [
  { total: 29924.95, bars: [0.35, 0.63, 0.9, 0.8, 0.55, 0.62, 0.4, 0.62, 0.76, 0.82, 0.6, 0.55, 0.7, 0.78, 0.66, 0.54, 0.45] },
  { total: 31208.4, bars: [0.48, 0.52, 0.71, 0.93, 0.84, 0.58, 0.66, 0.5, 0.69, 0.88, 0.95, 0.72, 0.6, 0.67, 0.81, 0.74, 0.62] },
  { total: 27412.1, bars: [0.6, 0.44, 0.52, 0.68, 0.73, 0.49, 0.38, 0.57, 0.64, 0.53, 0.71, 0.86, 0.79, 0.58, 0.47, 0.6, 0.69] },
];

const BACKGROUND = '#0C0C10';
const SPOTLIGHT = 'rgba(74,47,166,0.55)';
const CARD_FILL = 'rgba(255,255,255,0.03)';
const CARD_EDGE = 'rgba(91,69,201,0.45)';
const CARD_EDGE_BRIGHT = 'rgba(140,110,255,0.75)';
const CARD_GLOW = 'rgba(110,70,230,0.22)';
const BAR_TOP = '#8A5BFF';
const BAR_MIDDLE = '#5B35D6';
const BAR_TAIL = 'rgba(91,53,214,0)';
const BAR_GLOW = 'rgba(160,120,255,0.7)';
const SWEEP = 'rgba(255,255,255,0.1)';
const VIOLET = '#7B4DE0';
const FROST = 'rgba(255,255,255,0.25)';
const CAPTION = '#A9A6C0';
const INK = '#FFFFFF';
const VALUE_GLOW = 'rgba(157,123,255,0.45)';

// Layout, in points.
const CARD_WIDTH = 345;
const CARD_HEIGHT = 380;
const CARD_RADIUS = 24;
const CARD_PADDING = 28;
const BAR_WIDTH = 4;
const BAR_TALLEST = 190;
// The bars' last share fades into the card.
const BAR_FADE = 0.15;
const SQUARE = 80;
const SQUARE_RADIUS = 18;
// Where the frosted square rests over the violet one.
const OVERLAP = 24;
const FROST_BLUR = 12;
const SWEEP_WIDTH = 60;
// How bright the tip of a bar lights up when it arrives.
const TIP_GLOW = 0.45;
const SPOT_RADIUS = 330;

// Motion, in seconds since the boot started.
const WOBBLE_FOR = 0.8;
const RISE_AT = 0.8;
const RISE_FOR = 0.6;
const CARD_AT = 0.8;
const CARD_FOR = 0.6;
const CARD_DROP = 120;
const GROW_AT = 1.4;
const GROW_FOR = 0.55;
const GROW_GAP = 0.035;
// The bars overshoot their height by this share as they arrive.
const OVERSHOOT = 0.03;
const NUMBER_AT = 1.4;
const SWEEP_AT = 3.0;
const SWEEP_FOR = 0.9;
// Breathing on the bar heights once they stand: amplitude, period, and the lag from bar to bar.
const BREATH = 0.03;
const BREATH_PERIOD = 2.4;
const BREATH_LAG = 0.12;
// A refresh moves every bar to its new height, a little after its left neighbour.
const REFRESH_FOR = 0.7;
const REFRESH_GAP = 0.02;
// Seconds since the power-down started: bars sink right to left, the card drops, the squares return.
const SINK_FOR = 0.35;
const SINK_GAP = 0.025;
const CARD_OUT_AT = 0.35;
const CARD_OUT_FOR = 0.45;
const RETURN_AT = 0.6;
const RETURN_FOR = 0.65;
const DOWN_DONE = RETURN_AT + RETURN_FOR;

// The autoPlay timeline, in seconds from the start of each loop.
export const LOOP = 9;
const BOOT = 0.8;
const REFRESH = 5.0;
const POWER_DOWN = 7.4;
// Seconds after the last touch before the screen powers down and the loop starts again.
const USER_HOLD = 3;

type Props = {
  title?: string;
  currency?: string;
  sets?: SalesSet[];
  /** Boots, refreshes once and powers down by itself every LOOP seconds. */
  autoPlay?: boolean;
  /** Called with the set the card moves to on a refresh. */
  onRefresh?: (set: SalesSet, index: number) => void;
};

function clamp01(t: number) {
  'worklet';
  return Math.min(1, Math.max(0, t));
}

function easeOut(t: number) {
  'worklet';
  return 1 - (1 - clamp01(t)) ** 3;
}

function easeIn(t: number) {
  'worklet';
  return clamp01(t) ** 3;
}

function easeInOut(t: number) {
  'worklet';
  const x = clamp01(t);
  return x < 0.5 ? 4 * x * x * x : 1 - (-2 * x + 2) ** 3 / 2;
}

/** Ease out with a small overshoot past 1 before settling. */
function arrive(t: number, overshoot: number) {
  'worklet';
  const x = clamp01(t);
  const s = 1.70158 * (overshoot / 0.1);
  return 1 + (s + 1) * (x - 1) ** 3 + s * (x - 1) ** 2;
}

/** A kick that starts and ends at 0: a damped swing, as from a tap. */
function swing(t: number) {
  'worklet';
  if (t <= 0 || t >= WOBBLE_FOR) return 0;
  return Math.exp(-5 * t) * Math.sin(t * 2 * Math.PI * 2.2);
}

function money(value: number, currency: string) {
  const cents = Math.round(value * 100);
  const whole = String(Math.floor(cents / 100)).replace(/\B(?=(\d{3})+(?!\d))/g, ',');
  return `${currency}${whole}.${String(cents % 100).padStart(2, '0')}`;
}

function tap() {
  if (process.env.EXPO_OS !== 'web') Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
}

/**
 * A dashboard boot: two app-icon squares wobble and fly up, a glass card rises with its border
 * drawing round, the total counts up digit by digit and seventeen neon bars grow in a wave, then
 * breathe. Tapping the card refreshes it with the next set of figures; tapping the squares boots it.
 */
export function NeonBarBoot({ title = 'Total Sales', currency = '$', sets = SETS, autoPlay = false, onRefresh }: Props) {
  const reducedMotion = useReducedMotion();
  const [size, setSize] = useState({ width: 0, height: 0 });
  // Changes on each boot, so the total rolls in from zeros again.
  const [boots, setBoots] = useState(0);
  const [setIndex, setSetIndex] = useState(0);
  const [refreshed, setRefreshed] = useState(false);
  const [touches, setTouches] = useState(0);

  // Seconds on the UI thread's clock; when the boot, the last refresh and the power-down began (-1: not).
  const now = useSharedValue(0);
  const bootAt = useSharedValue(-1);
  const refreshAt = useSharedValue(-1);
  const downAt = useSharedValue(-1);
  const fromSet = useSharedValue(0);
  const toSet = useSharedValue(0);
  const bootCount = useSharedValue(0);
  // When the autoPlay loop started (-1: it is not running) and the loop time it last acted on.
  const loopAt = useSharedValue(-1);
  const seen = useSharedValue(-1);

  const { width, height } = size;
  const cardWidth = Math.min(CARD_WIDTH, width - 48);
  const cardHeight = Math.min(CARD_HEIGHT, height * 0.5);
  const scale = cardHeight / CARD_HEIGHT;
  const card = { x: (width - cardWidth) / 2, y: height / 2 - cardHeight / 2 + 24, width: cardWidth, height: cardHeight };
  const barsLeft = card.x + CARD_PADDING;
  const pitch = (cardWidth - CARD_PADDING * 2) / 17;
  const base = card.y + cardHeight - CARD_PADDING;
  const tallest = BAR_TALLEST * scale;
  const centre = { x: width / 2, y: height / 2 };
  const barSets = sets.map((set) => set.bars);
  const count = sets[0].bars.length;

  const boot = () => {
    'worklet';
    if (bootAt.get() >= 0) return;
    bootAt.set(now.get());
    refreshAt.set(-1);
    downAt.set(-1);
    fromSet.set(0);
    toSet.set(0);
    bootCount.set(bootCount.get() + 1);
  };

  const refresh = () => {
    'worklet';
    const b = bootAt.get();
    if (b < 0 || downAt.get() >= 0 || now.get() - b < GROW_AT) return;
    fromSet.set(toSet.get());
    toSet.set((toSet.get() + 1) % barSets.length);
    refreshAt.set(now.get());
  };

  const powerDown = () => {
    'worklet';
    if (bootAt.get() < 0 || downAt.get() >= 0) return;
    downAt.set(now.get());
  };

  const startLoop = () => {
    'worklet';
    loopAt.set(now.get());
    seen.set(-1);
  };

  useFrameCallback((frame) => {
    const t = frame.timestamp / 1000;
    now.set(t);
    // A finished power-down leaves the screen at rest.
    if (downAt.get() >= 0 && t - downAt.get() > DOWN_DONE) {
      bootAt.set(-1);
      downAt.set(-1);
    }
    if (loopAt.get() < 0) return;
    const at = (t - loopAt.get()) % LOOP;
    const was = seen.get() > at ? -1 : seen.get();
    seen.set(at);
    if (was < BOOT && at >= BOOT) boot();
    if (was < REFRESH && at >= REFRESH) refresh();
    if (was < POWER_DOWN && at >= POWER_DOWN) powerDown();
  });

  // The total's text follows the set and each boot, on the JS side.
  const booted = (n: number) => {
    setBoots(n);
    setSetIndex(0);
    setRefreshed(false);
  };
  const moved = (index: number, hand: boolean) => {
    setSetIndex(index);
    setRefreshed(true);
    if (hand) tap();
    onRefresh?.(sets[index], index);
  };
  useAnimatedReaction(
    () => bootCount.get(),
    (n, before) => {
      if (before !== null && n !== before) scheduleOnRN(booted, n);
    },
  );
  useAnimatedReaction(
    () => refreshAt.get(),
    (at, before) => {
      if (before !== null && at !== before && at >= 0) scheduleOnRN(moved, toSet.get(), loopAt.get() < 0);
    },
  );

  useEffect(() => {
    const timers: ReturnType<typeof setTimeout>[] = [];
    if (!autoPlay) {
      // Without the loop the screen boots once, a moment after it opens, and stays on the card.
      if (touches === 0) timers.push(setTimeout(() => scheduleOnUI(boot), 400));
    } else if (touches === 0) {
      scheduleOnUI(startLoop);
    } else {
      timers.push(
        setTimeout(() => {
          scheduleOnUI(powerDown);
          timers.push(setTimeout(() => scheduleOnUI(startLoop), DOWN_DONE * 1000 + 200));
        }, USER_HOLD * 1000),
      );
    }
    return () => {
      timers.forEach(clearTimeout);
      loopAt.set(-1);
    };
    // The worklets read shared values only; the effect restarts on a touch or a change of mode.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [autoPlay, touches]);

  const touched = () => setTouches((n) => n + 1);

  // A tap on the squares boots the screen; a tap on the card refreshes it.
  const press = Gesture.Tap().onEnd(() => {
    loopAt.set(-1);
    if (bootAt.get() < 0) boot();
    else refresh();
    scheduleOnRN(touched);
  });

  const sinceBoot = useDerivedValue(() => (bootAt.get() < 0 ? -1 : now.get() - bootAt.get()));
  const sinceDown = useDerivedValue(() => (downAt.get() < 0 ? -1 : now.get() - downAt.get()));

  // The card: up from below and drawn round on boot, down and gone on power-down.
  const cardIn = useDerivedValue(() => (sinceBoot.get() < 0 ? 0 : easeOut((sinceBoot.get() - CARD_AT) / CARD_FOR)));
  const cardOut = useDerivedValue(() => (sinceDown.get() < 0 ? 0 : easeIn((sinceDown.get() - CARD_OUT_AT) / CARD_OUT_FOR)));
  const cardShift = useDerivedValue(() => (reducedMotion ? 0 : CARD_DROP * (1 - cardIn.get()) + CARD_DROP * 1.3 * cardOut.get()));
  const cardOpacity = useDerivedValue(() => cardIn.get() * (1 - cardOut.get()));
  const cardTransform = useDerivedValue(() => [{ translateY: cardShift.get() }]);
  const border = useDerivedValue(() => (reducedMotion ? cardIn.get() : sinceBoot.get() < 0 ? 0 : easeInOut((sinceBoot.get() - CARD_AT) / CARD_FOR)));
  const sweepX = useDerivedValue(() => {
    const b = sinceBoot.get();
    if (reducedMotion || b < SWEEP_AT || b > SWEEP_AT + SWEEP_FOR) return -SWEEP_WIDTH * 2;
    return card.x - SWEEP_WIDTH + (card.width + SWEEP_WIDTH) * easeInOut((b - SWEEP_AT) / SWEEP_FOR);
  });

  const sweepFrom = useDerivedValue(() => vec(sweepX.get(), 0));
  const sweepTo = useDerivedValue(() => vec(sweepX.get() + SWEEP_WIDTH, 0));

  // The squares: a tap's wobble, then up and out of sight; back down after the power-down.
  const away = -(centre.y + SQUARE * 2);
  const squaresY = useDerivedValue(() => {
    const b = sinceBoot.get();
    const d = sinceDown.get();
    if (reducedMotion) return 0;
    if (d >= 0) return away * (1 - arrive((d - RETURN_AT) / RETURN_FOR, 0.08));
    if (b < 0) return 0;
    return away * easeIn((b - RISE_AT) / RISE_FOR);
  });
  const squaresOpacity = useDerivedValue(() => {
    if (!reducedMotion) return 1;
    const b = sinceBoot.get();
    const d = sinceDown.get();
    if (d >= 0) return clamp01((d - RETURN_AT) / 0.3);
    return b < 0 ? 1 : 1 - clamp01((b - RISE_AT) / 0.3);
  });
  const kick = useDerivedValue(() => (reducedMotion || sinceDown.get() >= 0 ? 0 : swing(sinceBoot.get())));
  const violetTransform = useDerivedValue(() => [
    { translateY: squaresY.get() },
    { rotate: (-18 * Math.PI * kick.get()) / 180 },
    { scale: 1 - 0.15 * kick.get() },
  ]);
  const frostTransform = useDerivedValue(() => [
    { translateX: 20 * kick.get() },
    { translateY: squaresY.get() * 1.08 + 20 * kick.get() },
    { rotate: (22 * Math.PI * kick.get()) / 180 },
  ]);

  const bars = useDerivedValue(() => {
    const b = sinceBoot.get();
    const d = sinceDown.get();
    const t = now.get();
    const r = refreshAt.get();
    const from = barSets[fromSet.get()];
    const to = barSets[toSet.get()];
    return createPicture((canvas) => {
      if (b < 0) return;
      const paint = Skia.Paint();
      paint.setAntiAlias(true);
      const glow = Skia.Paint();
      glow.setAntiAlias(true);
      glow.setMaskFilter(Skia.MaskFilter.MakeBlur(1, 5, true));
      glow.setColor(Skia.Color(BAR_GLOW));
      const haze = Skia.Paint();
      haze.setAntiAlias(true);
      haze.setMaskFilter(Skia.MaskFilter.MakeBlur(1, 4, true));
      const breathIn = clamp01((b - GROW_AT - 1.2) / 0.6);
      for (let i = 0; i < count; i++) {
        const grown = reducedMotion ? 1 : arrive((b - GROW_AT - i * GROW_GAP) / GROW_FOR, OVERSHOOT);
        const shown = reducedMotion ? clamp01((b - GROW_AT) / 0.3) : 1;
        if (b < GROW_AT + i * GROW_GAP && !reducedMotion) continue;
        const k = r < 0 ? 1 : reducedMotion ? 1 : arrive((t - r - i * REFRESH_GAP) / REFRESH_FOR, 0.05);
        const target = from[i] + (to[i] - from[i]) * k;
        const breath = reducedMotion ? 1 : 1 + BREATH * breathIn * Math.sin((2 * Math.PI * (b - GROW_AT - i * BREATH_LAG)) / BREATH_PERIOD);
        const sink = d < 0 ? 1 : reducedMotion ? 1 - clamp01((d - CARD_OUT_AT) / 0.3) : 1 - easeIn((d - (count - 1 - i) * SINK_GAP) / SINK_FOR);
        const h = tallest * target * grown * breath * (reducedMotion ? 1 : sink);
        const alpha = shown * (reducedMotion ? sink : 1);
        if (h < 1 || alpha <= 0) continue;
        const x = barsLeft + pitch * (i + 0.5);
        const top = base - h;
        const shader = Skia.Shader.MakeLinearGradient(
          vec(0, top),
          vec(0, base),
          [Skia.Color(BAR_TOP), Skia.Color(BAR_MIDDLE), Skia.Color(BAR_TAIL)],
          [0, 1 - BAR_FADE * 2, 1],
          TileMode.Clamp,
        );
        haze.setShader(shader);
        haze.setAlphaf(0.35 * alpha);
        canvas.drawRRect(Skia.RRectXY(Skia.XYWHRect(x - BAR_WIDTH, top, BAR_WIDTH * 2, h), BAR_WIDTH, BAR_WIDTH), haze);
        paint.setShader(shader);
        paint.setAlphaf(alpha);
        canvas.drawRRect(Skia.RRectXY(Skia.XYWHRect(x - BAR_WIDTH / 2, top, BAR_WIDTH, h), BAR_WIDTH / 2, BAR_WIDTH / 2), paint);
        // The top lights up as the bar arrives.
        const lit = clamp01((grown - 0.85) / 0.15) * alpha * (d < 0 ? 1 : sink);
        if (lit > 0) {
          glow.setAlphaf(TIP_GLOW * lit);
          canvas.drawCircle(x, top + BAR_WIDTH / 2, BAR_WIDTH, glow);
        }
      }
    });
  });

  const textStyle = useAnimatedStyle(() => ({ opacity: cardOpacity.get(), transform: [{ translateY: cardShift.get() }] }));

  const cardShape = rrect(Skia.XYWHRect(card.x, card.y, card.width, card.height), CARD_RADIUS, CARD_RADIUS);
  const outline = Skia.PathBuilder.Make().addRRect(cardShape).build();
  const violet = rrect(Skia.XYWHRect(centre.x - SQUARE / 2 - OVERLAP / 2, centre.y - SQUARE / 2 - OVERLAP / 2, SQUARE, SQUARE), SQUARE_RADIUS, SQUARE_RADIUS);
  const frost = rrect(Skia.XYWHRect(centre.x - SQUARE / 2 + OVERLAP / 2, centre.y - SQUARE / 2 + OVERLAP / 2, SQUARE, SQUARE), SQUARE_RADIUS, SQUARE_RADIUS);

  const onLayout = (event: LayoutChangeEvent) => {
    const { width: w, height: h } = event.nativeEvent.layout;
    setSize({ width: w, height: h });
  };

  return (
    <GestureDetector gesture={press}>
      <View style={styles.screen} onLayout={onLayout} accessibilityRole="button" accessibilityLabel={`${title} ${money(sets[setIndex].total, currency)}`}>
        <StatusBar style="light" />
        {width > 0 && (
          <>
            <Canvas style={StyleSheet.absoluteFill}>
              <Rect x={0} y={0} width={width} height={height} color={BACKGROUND} />
              <Rect x={0} y={0} width={width} height={height}>
                <RadialGradient c={vec(width / 2, 0)} r={SPOT_RADIUS} colors={[SPOTLIGHT, 'rgba(74,47,166,0)']} />
              </Rect>

              <Group opacity={squaresOpacity}>
                <Group transform={violetTransform} origin={vec(centre.x - OVERLAP / 2, centre.y - OVERLAP / 2)}>
                  <RoundedRect rect={violet} color={VIOLET} />
                </Group>
                <Group transform={frostTransform} origin={vec(centre.x + OVERLAP / 2, centre.y + OVERLAP / 2)}>
                  <BackdropBlur blur={FROST_BLUR} clip={frost}>
                    <RoundedRect rect={frost} color={FROST} />
                  </BackdropBlur>
                </Group>
              </Group>

              <Group transform={cardTransform} opacity={cardOpacity}>
                <Group clip={cardShape}>
                  <RoundedRect rect={cardShape} color={CARD_FILL} />
                  <Rect x={card.x} y={card.y} width={card.width} height={card.height}>
                    <RadialGradient c={vec(card.x + card.width / 2, card.y)} r={card.width * 0.7} colors={[CARD_GLOW, 'rgba(110,70,230,0)']} />
                  </Rect>
                  <Rect x={sweepX} y={card.y} width={SWEEP_WIDTH} height={card.height}>
                    <LinearGradient start={sweepFrom} end={sweepTo} colors={['rgba(255,255,255,0)', SWEEP, 'rgba(255,255,255,0)']} />
                  </Rect>
                </Group>
                <Path path={outline} style="stroke" strokeWidth={1} end={border}>
                  <LinearGradient
                    start={vec(card.x, card.y)}
                    end={vec(card.x + card.width, card.y + card.height)}
                    colors={[CARD_EDGE_BRIGHT, CARD_EDGE, CARD_EDGE]}
                    positions={[0, 0.45, 1]}
                  />
                </Path>
              </Group>
              <Picture picture={bars} />
            </Canvas>

            <Animated.View pointerEvents="none" style={[styles.text, { left: card.x + CARD_PADDING, top: card.y + CARD_PADDING }, textStyle]}>
              <Text style={styles.caption}>{title}</Text>
              <Odometer
                key={boots}
                text={money(sets[setIndex].total, currency)}
                style={styles.value}
                delay={refreshed ? 0 : NUMBER_AT * 1000}
                still={reducedMotion}
              />
            </Animated.View>
          </>
        )}
      </View>
    </GestureDetector>
  );
}

const styles = StyleSheet.create({
  screen: {
    flex: 1,
    backgroundColor: BACKGROUND,
  },
  text: {
    position: 'absolute',
  },
  caption: {
    marginBottom: 2,
    color: CAPTION,
    fontSize: 13,
    lineHeight: 18,
  },
  value: {
    color: INK,
    fontSize: 30,
    lineHeight: 38,
    fontWeight: '300',
    fontVariant: ['tabular-nums'],
    textShadowColor: VALUE_GLOW,
    textShadowRadius: 10,
  },
});
