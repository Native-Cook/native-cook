import { Canvas, DashPathEffect, Path, Skia } from '@shopify/react-native-skia';
import { Pressable, StyleSheet, Text, TextInput, type TextInputProps, type TextStyle, View } from 'react-native';
import Animated, { type AnimatedStyle, type SharedValue, useAnimatedProps, useAnimatedStyle, useDerivedValue } from 'react-native-reanimated';

import { CX, HEROES, LETTER_PATH, clamp01, smooth } from './Orbit';

// The three cards the coins open into, in stage points. `reveal` runs from 0 to 1 once a scene is
// in front, and each part of its card comes in over its own stretch of that.

const INK = '#37342E';
const CARD_X = CX - 152;
const CARD_WIDTH = 304;
const SAVINGS = HEROES[0];
const INSIGHTS = HEROES[2];
const SAVINGS_TOP = SAVINGS.y - SAVINGS.h / 2;
const INSIGHTS_TOP = INSIGHTS.y - INSIGHTS.h / 2;
const ROW = 56;
const SPEND_ROW = 48;
const PAY = HEROES[1];
// The balance counts up from this much less.
const COUNT_FROM = 436;

const LINKS = ['Credit Card', 'Loans', 'Investments'];
const SPENDS = [
  { name: 'Shopping', amount: 14140, bar: 100 },
  { name: 'Food', amount: 8890, bar: 34 },
  { name: 'Entertainment', amount: 4103, bar: 24 },
  { name: 'Bill Payments', amount: 2372, bar: 12 },
];
const ARROW = 'M2 7H14M9 2L14 7L9 12';

// The chart: thin lines rising to the right, the first quarter green, the rest dotted grey.
const CHART_LINES = 36;
const CHART_GREEN = 9;
const CHART_LEFT = 24;
const CHART_STEP = 7.3;
const CHART_BASE = 96;

function lineHeight(i: number) {
  'worklet';
  return 14 + 70 * Math.pow(i / (CHART_LINES - 1), 1.5) + 6 * Math.sin(i * 1.7);
}

function chartPath(reveal: number, from: number, to: number) {
  'worklet';
  const builder = Skia.PathBuilder.Make();
  // The lines grow from the baseline, left to right, four at a time.
  const grown = smooth(reveal, 0.1, 0.8) * (CHART_LINES + 4);
  for (let i = from; i < to; i++) {
    const part = clamp01((grown - i) / 4);
    if (part <= 0) continue;
    const x = CHART_LEFT + i * CHART_STEP;
    builder.moveTo(x, CHART_BASE).lineTo(x, CHART_BASE - lineHeight(i) * part);
  }
  return builder.build();
}

export function money(symbol: string, value: number) {
  'worklet';
  const digits = String(Math.abs(Math.round(value)));
  let grouped = '';
  for (let i = 0; i < digits.length; i++) {
    if (i > 0 && (digits.length - i) % 3 === 0) grouped += ',';
    grouped += digits[i];
  }
  return (value < -0.5 ? '-' : '') + symbol + grouped;
}

function easeOut(t: number) {
  'worklet';
  const u = 1 - clamp01(t);
  return 1 - u * u * u;
}

const AnimatedTextInput = Animated.createAnimatedComponent(TextInput);

type CountProps = { reveal: SharedValue<number>; value: number; symbol: string; style: (TextStyle | AnimatedStyle<TextStyle>)[] };

/**
 * The balance while it counts up to `value` over the middle of the reveal. It is written from the
 * UI thread: a React render for every number would hold back the scene that is still sliding in
 * under it, which then moves in jerks.
 */
function Count({ reveal, value, symbol, style }: CountProps) {
  const animatedProps = useAnimatedProps(() => {
    const text = money(symbol, value - COUNT_FROM * (1 - easeOut((reveal.get() - 0.1) / 0.65)));
    return { text, defaultValue: text } as TextInputProps;
  });
  return (
    <AnimatedTextInput
      editable={false}
      pointerEvents="none"
      accessibilityRole="text"
      accessibilityLabel={money(symbol, value)}
      underlineColorAndroid="transparent"
      style={[styles.figure, style]}
      animatedProps={animatedProps}
    />
  );
}

function useFade(reveal: SharedValue<number>, from: number) {
  return useAnimatedStyle(() => ({ opacity: smooth(reveal.get(), from, from + 0.25) }));
}

export type SceneText = { title: string; subtitle: string };

type CaptionProps = SceneText & { color: string; mute: string };

function Caption({ title, subtitle, color, mute }: CaptionProps) {
  return (
    <>
      <Text style={[styles.captionTitle, { color }]} numberOfLines={1}>
        {title}
      </Text>
      <Text style={[styles.captionSubtitle, { color: mute }]} numberOfLines={1}>
        {subtitle}
      </Text>
    </>
  );
}

type SceneProps = { reveal: SharedValue<number>; text: SceneText; balance: number; symbol: string; onAction?: () => void };

function Link({ label, index, reveal }: { label: string; index: number; reveal: SharedValue<number> }) {
  const fade = useFade(reveal, 0.3 + index * 0.08);
  return (
    <Animated.View style={[styles.link, { top: SAVINGS.h + index * ROW, borderBottomColor: index === 0 ? '#FA776B' : '#ECE7E0' }, fade]}>
      <Text style={styles.linkLabel}>{label}</Text>
      <Canvas style={styles.arrow}>
        <Path path={ARROW} style="stroke" strokeWidth={1.6} strokeCap="round" strokeJoin="round" color={INK} />
      </Canvas>
    </Animated.View>
  );
}

/** Scene 1: a savings account on a green card, with three links under it. */
export function Savings({ reveal, text, balance, symbol, onAction }: SceneProps) {
  const first = useFade(reveal, 0);
  const second = useFade(reveal, 0.08);
  const third = useFade(reveal, 0.16);
  return (
    <>
      <View style={[styles.card, { top: SAVINGS_TOP, height: SAVINGS.h + ROW * 3 + 8 }]}>
        <View style={[styles.cardTop, { height: SAVINGS.h, backgroundColor: '#136F63' }]}>
          <Animated.View style={[styles.tag, first]}>
            <Text style={styles.tagLabel}>NORTH BANK</Text>
          </Animated.View>
          <Animated.Text style={[styles.cardTitle, { top: 56 }, first]}>{text.title}</Animated.Text>
          <Count reveal={reveal} value={balance} symbol={symbol} style={[{ top: 84 }, second]} />
          <Animated.View style={[styles.action, third]}>
            <Pressable style={styles.actionPress} onPress={onAction} accessibilityRole="button">
              <Text style={styles.actionLabel}>Add Money</Text>
            </Pressable>
          </Animated.View>
        </View>
        {LINKS.map((label, index) => (
          <Link key={label} label={label} index={index} reveal={reveal} />
        ))}
      </View>
      <Caption {...text} color={INK} mute="#6B655C" />
    </>
  );
}

/** Scene 2: a payment card standing upright, and the offer that pops up over its foot. */
export function PayCard({ reveal, text }: SceneProps) {
  const first = useFade(reveal, 0);
  const pop = useAnimatedStyle(() => {
    const u = smooth(reveal.get(), 0.45, 0.85) - 1;
    // Past its size and back.
    const back = 1 + 2.7 * u * u * u + 1.7 * u * u;
    return { opacity: smooth(reveal.get(), 0.45, 0.6), transform: [{ scale: 0.8 + 0.2 * back }] };
  });
  return (
    <>
      <View style={styles.pay}>
        <View style={styles.payLeft}>
          <Canvas style={StyleSheet.absoluteFill}>
            <Path path={LETTER_PATH} color="rgba(0,0,0,0.07)" transform={[{ translateX: -22 }, { translateY: 150 }, { scale: 3.2 }]} />
          </Canvas>
        </View>
        <View style={styles.payGlass} />
        <Animated.Text style={[styles.payName, first]}>peak</Animated.Text>
        <Animated.View style={[styles.chip, first]}>
          <View style={styles.chipLine} />
        </Animated.View>
      </View>
      <Animated.View style={[styles.offer, pop]}>
        <Text style={styles.offerLabel}>Lifetime Free</Text>
      </Animated.View>
      <Caption {...text} color="#FFFFFF" mute="rgba(255,255,255,0.75)" />
    </>
  );
}

function Spend({ index, reveal, symbol }: { index: number; reveal: SharedValue<number>; symbol: string }) {
  const spend = SPENDS[index];
  const from = 0.3 + index * 0.08;
  // The row slides up into place and its bar grows from the left.
  const row = useAnimatedStyle(() => {
    const shown = smooth(reveal.get(), from, from + 0.3);
    return { opacity: shown, transform: [{ translateY: 8 * (1 - shown) }] };
  });
  const bar = useAnimatedStyle(() => ({ transform: [{ scaleX: smooth(reveal.get(), from + 0.1, from + 0.5) }] }));
  return (
    <Animated.View style={[styles.spend, { top: INSIGHTS.h + 10 + index * SPEND_ROW }, row]}>
      <View>
        <Text style={styles.spendLabel}>{spend.name}</Text>
        <Animated.View style={[styles.bar, { width: spend.bar }, bar]} />
      </View>
      <Text style={styles.spendLabel}>{money(symbol, spend.amount)}</Text>
    </Animated.View>
  );
}

/** Scene 3: where the money went, under a chart that grows from the left. */
export function Insights({ reveal, text, balance, symbol }: SceneProps) {
  const first = useFade(reveal, 0);
  const second = useFade(reveal, 0.08);
  const green = useDerivedValue(() => chartPath(reveal.get(), 0, CHART_GREEN));
  const grey = useDerivedValue(() => chartPath(reveal.get(), CHART_GREEN, CHART_LINES));
  return (
    <>
      <View style={[styles.card, { top: INSIGHTS_TOP, height: INSIGHTS.h + 20 + SPEND_ROW * SPENDS.length }]}>
        <View style={[styles.cardTop, { height: INSIGHTS.h, backgroundColor: '#363B42' }]}>
          <Animated.Text style={[styles.cardTitle, { top: 30 }, first]}>Money</Animated.Text>
          <Count reveal={reveal} value={balance} symbol={symbol} style={[{ top: 56, fontSize: 34 }, second]} />
          <Canvas style={styles.chart}>
            <Path path={green} style="stroke" strokeWidth={2} color="#3BAA5C" />
            <Path path={grey} style="stroke" strokeWidth={2} color="#8A9099">
              <DashPathEffect intervals={[2, 3]} />
            </Path>
          </Canvas>
        </View>
        {SPENDS.map((spend, index) => (
          <Spend key={spend.name} index={index} reveal={reveal} symbol={symbol} />
        ))}
      </View>
      <Caption {...text} color="#4D423B" mute="#4D423B" />
    </>
  );
}

const styles = StyleSheet.create({
  card: {
    position: 'absolute',
    left: CARD_X,
    width: CARD_WIDTH,
    borderRadius: 14,
    backgroundColor: '#FFFFFF',
    boxShadow: [{ offsetX: 0, offsetY: 8, blurRadius: 20, color: 'rgba(0,0,0,0.12)' }],
  },
  cardTop: {
    borderTopLeftRadius: 14,
    borderTopRightRadius: 14,
  },
  tag: {
    position: 'absolute',
    right: 14,
    top: 16,
    height: 14,
    paddingHorizontal: 5,
    borderRadius: 2,
    backgroundColor: '#FFFFFF',
    justifyContent: 'center',
  },
  tagLabel: {
    color: '#136F63',
    fontSize: 7,
    lineHeight: 10,
    fontWeight: '700',
    letterSpacing: 0.4,
    includeFontPadding: false,
  },
  cardTitle: {
    position: 'absolute',
    left: 24,
    color: '#FFFFFF',
    fontSize: 15,
    lineHeight: 20,
    fontWeight: '500',
    includeFontPadding: false,
  },
  // A text input, so that its text can change without a render; it centres its one line in its height.
  figure: {
    position: 'absolute',
    left: 24,
    right: 24,
    height: 44,
    padding: 0,
    color: '#FFFFFF',
    fontSize: 36,
    fontVariant: ['tabular-nums'],
    includeFontPadding: false,
  },
  action: {
    position: 'absolute',
    left: 22,
    right: 22,
    top: 176,
    height: 45,
    borderRadius: 4,
    backgroundColor: '#FFFFFF',
  },
  actionPress: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
  },
  actionLabel: {
    color: INK,
    fontSize: 14,
    lineHeight: 18,
    includeFontPadding: false,
  },
  link: {
    position: 'absolute',
    left: 22,
    right: 22,
    height: ROW,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    borderBottomWidth: 1,
  },
  linkLabel: {
    color: INK,
    fontSize: 14,
    lineHeight: 18,
    fontWeight: '500',
    includeFontPadding: false,
  },
  arrow: {
    width: 16,
    height: 14,
  },
  pay: {
    position: 'absolute',
    left: PAY.x - PAY.w / 2,
    top: PAY.y - PAY.h / 2,
    width: PAY.w,
    height: PAY.h,
    borderRadius: PAY.r,
    overflow: 'hidden',
  },
  payLeft: {
    position: 'absolute',
    left: 0,
    top: 0,
    bottom: 0,
    width: 144,
    backgroundColor: '#E86262',
  },
  payGlass: {
    position: 'absolute',
    left: 144,
    right: 0,
    top: 0,
    bottom: 0,
    backgroundColor: 'rgba(255,255,255,0.18)',
  },
  payName: {
    position: 'absolute',
    left: 20,
    top: 24,
    color: '#FFFFFF',
    fontSize: 30,
    lineHeight: 36,
    fontWeight: '700',
    includeFontPadding: false,
  },
  chip: {
    position: 'absolute',
    left: 158,
    top: 28,
    width: 28,
    height: 40,
    borderRadius: 5,
    backgroundColor: '#D9DCE0',
    justifyContent: 'center',
  },
  chipLine: {
    height: 14,
    borderTopWidth: 1,
    borderBottomWidth: 1,
    borderColor: '#A9AEB5',
  },
  offer: {
    position: 'absolute',
    left: CX - 150,
    top: 500,
    width: 300,
    height: 76,
    borderRadius: 6,
    backgroundColor: '#FFFFFF',
    alignItems: 'center',
    justifyContent: 'center',
    boxShadow: [{ offsetX: 0, offsetY: 8, blurRadius: 20, color: 'rgba(0,0,0,0.18)' }],
  },
  offerLabel: {
    color: '#388370',
    fontSize: 20,
    lineHeight: 26,
    fontWeight: '600',
    includeFontPadding: false,
  },
  chart: {
    position: 'absolute',
    left: 0,
    top: 110,
    width: CARD_WIDTH,
    height: 100,
  },
  spend: {
    position: 'absolute',
    left: 24,
    right: 24,
    height: SPEND_ROW,
    flexDirection: 'row',
    justifyContent: 'space-between',
    paddingTop: 6,
  },
  spendLabel: {
    color: INK,
    fontSize: 13,
    lineHeight: 18,
    fontWeight: '500',
    fontVariant: ['tabular-nums'],
    includeFontPadding: false,
  },
  bar: {
    marginTop: 5,
    height: 3,
    borderRadius: 1.5,
    backgroundColor: INK,
    transformOrigin: 'left',
  },
  captionTitle: {
    position: 'absolute',
    left: 0,
    right: 0,
    top: 651,
    fontSize: 28,
    lineHeight: 34,
    fontWeight: '300',
    textAlign: 'center',
    includeFontPadding: false,
  },
  captionSubtitle: {
    position: 'absolute',
    left: 0,
    right: 0,
    top: 693,
    fontSize: 20,
    lineHeight: 26,
    fontWeight: '300',
    textAlign: 'center',
    includeFontPadding: false,
  },
});
