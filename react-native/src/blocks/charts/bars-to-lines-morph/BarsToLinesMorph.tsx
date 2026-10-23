import MaterialCommunityIcons from '@expo/vector-icons/MaterialCommunityIcons';
import { Canvas, DashPathEffect, Group, Path, Text as SkiaText, matchFont } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useMemo, useState } from 'react';
import { type LayoutChangeEvent, Platform, Pressable, StyleSheet, Text, View, useWindowDimensions } from 'react-native';
import Animated, {
  Easing,
  FadeIn,
  FadeOut,
  ReduceMotion,
  interpolateColor,
  useAnimatedStyle,
  useDerivedValue,
  useFrameCallback,
  useReducedMotion,
  useSharedValue,
  withTiming,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';

import { B_END, C_END, FADE, HOURS, LABEL_IN, LABEL_OUT, type Look, TOTAL, ease, limitClips, scene, yOf } from './morph';
import { VITALS, type Vital, extremes, toPlot } from './vitals';

export type { Vital, VitalChart } from './vitals';

type Props = {
  title?: string;
  subtitle?: string;
  /** The tiles, each with the chart it opens. */
  vitals?: Vital[];
  /** The tile selected at rest. */
  initialVital?: number;
  /** Hour labels under the chart, every four hours from midnight. */
  hours?: string[];
  footnote?: string;
  /** Opens another tile's chart by itself every LOOP seconds, and comes back. */
  autoPlay?: boolean;
  onSelect?: (vital: Vital) => void;
};

const BACKGROUND = '#0f0f10';
const CARD = '#181819';
const CARD_EDGE = '#242426';
// A faint light over the selected tile.
const SELECTED = 'rgba(255,255,255,0.04)';
const SELECTED_EDGE = '#3a3a3c';
const INK = '#ffffff';
const SOFT = '#a1a1a6';
const MUTE = '#6b6b70';
const GRID = '#2c2c30';
const RED = '#e5384b';
const RED_INK = '#ff4d5e';

const MARGIN = 16;
const GAP = 8;
const TILE_HEIGHT = 76;
const TILE_RADIUS = 16;
const AXIS = 32;
const HOUR_LABELS = 30;
const LOOK: Look = { barMax: 8, barShare: 0.6, barRadius: 2, dot: 3, gap: 1 };
const LINE_WIDTH = 1.75;
const LABEL_SIZE = 11;
// The peak's label sits this far above its reading, the low's this far below.
const PEAK_RISE = 8;
const LOW_DROP = 16;
// The axis values' heights, bottom to top, as fractions of the chart.
const TICK_HEIGHTS = [0.1, 0.5, 0.9];
const FONT = Platform.select({ ios: '.AppleSystemUIFont', default: 'sans-serif' });
const SELECT_MOVE = 200;

/** autoPlay: the other chart opens at SWITCH_AT and the first comes back at RETURN_AT. */
export const LOOP = 9;
const SWITCH_AT = 1;
const RETURN_AT = 5.2;
const USER_HOLD = 3;

const AUTO = 0;
const HAND = 1;
const RELEASED = -1;

function tick() {
  if (process.env.EXPO_OS !== 'web') Haptics.selectionAsync().catch(() => {});
}

function smooth(t: number, from: number, to: number) {
  'worklet';
  return ease((t - from) / (to - from));
}

function Tile({ vital, onPress, selected }: { vital: Vital; onPress: () => void; selected: boolean }) {
  return (
    <Pressable onPress={onPress} style={styles.tile} accessibilityRole="button" accessibilityState={{ selected }}>
      <View style={styles.tileTop}>
        <MaterialCommunityIcons name={vital.icon} size={15} color={vital.iconColor} />
        <Text style={[styles.tileName, selected && styles.tileNameOn]} numberOfLines={1}>
          {vital.name}
        </Text>
        {vital.warning ? <MaterialCommunityIcons name="alert" size={14} color={RED_INK} /> : null}
      </View>
      <View style={styles.tileBottom}>
        <Text style={[styles.tileValue, vital.warning && styles.warning]} numberOfLines={1} adjustsFontSizeToFit minimumFontScale={0.8}>
          {vital.value}
          <Text style={[styles.tileUnit, vital.warning && styles.warning]}> {vital.unit}</Text>
        </Text>
        <Text style={styles.tileTime}>{vital.time}</Text>
      </View>
    </Pressable>
  );
}

/**
 * A vitals screen: six tiles and the selected one's chart. Heart rate is a column of range bars
 * per hour; blood pressure two lines with a dot per hour. Choosing another tile folds the chart
 * into dots, flies the dots to the new readings and unfolds them into bars or lines, with the
 * readings past the limits in red and the peak and the low labelled.
 */
export function BarsToLinesMorph({
  title = 'Vitals',
  subtitle = 'Today · updated 9:48 AM',
  vitals = VITALS,
  initialVital = 0,
  hours = ['12 AM', '4 AM', '8 AM', '12 PM', '4 PM', '8 PM'],
  footnote = 'Sample data',
  autoPlay = false,
  onSelect,
}: Props) {
  const still = useReducedMotion();
  const insets = useSafeAreaInsets();
  const window = useWindowDimensions();
  const [size, setSize] = useState({ width: window.width - 4 * MARGIN - AXIS, height: 300 });
  const [tileWidth, setTileWidth] = useState((window.width - 2 * MARGIN - GAP) / 2);
  const [selected, setSelected] = useState(initialVital);

  const font = useMemo(() => matchFont({ fontFamily: FONT, fontSize: LABEL_SIZE, fontWeight: '600' }), []);
  const plots = useMemo(() => vitals.map(toPlot), [vitals]);
  const labels = useMemo(() => vitals.map(extremes), [vitals]);
  const labelWidths = labels.map((pair) => pair.map((text) => font.measureText(text).width));
  // autoPlay opens a tile drawn the other way: two lines if there are, else any other kind.
  const twoLines = plots.findIndex((plot) => plot.lines === 2);
  const other = Math.max(0, twoLines >= 0 && twoLines !== initialVital ? twoLines : plots.findIndex((plot) => plot.bars !== plots[initialVital].bars));

  const fromIndex = useSharedValue(initialVital);
  const toIndex = useSharedValue(initialVital);
  const phase = useSharedValue(TOTAL);
  const mode = useSharedValue(autoPlay ? AUTO : HAND);
  const origin = useSharedValue(-1);
  const lastTime = useSharedValue(0);
  const touchedAt = useSharedValue(RELEASED);

  const width = size.width;
  const height = size.height;

  const applied = (index: number, byHand: boolean) => {
    setSelected(index);
    if (byHand) tick();
    onSelect?.(vitals[index]);
  };

  /** Every switch, by touch or by autoPlay, starts here on the UI thread with its own clock. */
  const select = (index: number, byHand: boolean) => {
    'worklet';
    if (index === toIndex.get()) return;
    // A switch that has not unfolded yet starts again from the chart it was leaving.
    if (phase.get() >= B_END) fromIndex.set(toIndex.get());
    toIndex.set(index);
    phase.set(0);
    phase.set(withTiming(TOTAL, { duration: TOTAL * 1000, easing: Easing.linear, reduceMotion: ReduceMotion.Never }));
    scheduleOnRN(applied, index, byHand);
  };

  useFrameCallback((frame) => {
    if (!autoPlay) return;
    const now = frame.timestamp;
    if (mode.get() === HAND) {
      if (touchedAt.get() === RELEASED) touchedAt.set(now);
      if (now - touchedAt.get() < USER_HOLD * 1000) return;
      let wait = 0;
      if (toIndex.get() !== initialVital) {
        select(initialVital, false);
        wait = TOTAL * 1000;
      }
      origin.set(now + wait);
      lastTime.set(-1);
      mode.set(AUTO);
      return;
    }
    if (origin.get() < 0) origin.set(now);
    const elapsed = (now - origin.get()) / 1000;
    if (elapsed < 0) return;
    const time = elapsed % LOOP;
    const before = lastTime.get();
    lastTime.set(time);
    if (before < SWITCH_AT && time >= SWITCH_AT) select(other, false);
    if (before < RETURN_AT && time >= RETURN_AT) select(initialVital, false);
  });

  const pressTile = (index: number) => {
    scheduleOnUI(() => {
      'worklet';
      mode.set(HAND);
      touchedAt.set(RELEASED);
      select(index, true);
    });
  };

  const now = useDerivedValue(() =>
    scene(phase.get(), plots[fromIndex.get()], plots[toIndex.get()], width, height, LOOK, still),
  );
  const bars = useDerivedValue(() => now.get().bars);
  const dots1 = useDerivedValue(() => now.get().dots1);
  const dots2 = useDerivedValue(() => now.get().dots2);
  const line1 = useDerivedValue(() => now.get().line1);
  const line2 = useDerivedValue(() => now.get().line2);
  const trim = useDerivedValue(() => now.get().trim);
  const opacity = useDerivedValue(() => now.get().opacity);
  const secondLine = useDerivedValue(() => (now.get().lineCount === 2 ? 1 : 0));
  const color1 = useDerivedValue(() => {
    const from = plots[fromIndex.get()].colors[0];
    const to = plots[toIndex.get()].colors[0];
    return interpolateColor(now.get().mix, [0, 1], [from, to], 'RGB', { gamma: 1 });
  });
  const color2 = useDerivedValue(() => {
    const from = plots[fromIndex.get()].colors[1];
    const to = plots[toIndex.get()].colors[1];
    return interpolateColor(now.get().mix, [0, 1], [from, to], 'RGB', { gamma: 1 });
  });
  // Bars break with a small gap where they cross a limit; lines just change colour.
  const barClips = useDerivedValue(() => limitClips(now.get().above, now.get().below, width, height, LOOK.gap));
  const lineClips = useDerivedValue(() => limitClips(now.get().above, now.get().below, width, height, 0));
  const barRed = useDerivedValue(() => barClips.get()[0]);
  const barPlain = useDerivedValue(() => barClips.get()[1]);
  const lineRed = useDerivedValue(() => lineClips.get()[0]);
  const linePlain = useDerivedValue(() => lineClips.get()[1]);

  /** The peak's (0) or the low's (1) label: the chart it belongs to, where it goes, how clear. */
  const extreme = (which: number, p: number, from: number, to: number) => {
    'worklet';
    const leaving = still ? p < FADE : p < B_END;
    const index = leaving ? from : to;
    const plot = plots[index];
    const hour = which === 0 ? plot.peak : plot.trough;
    const top = plot.bars ? plot.b : plot.a;
    const bottom = plot.bars ? plot.a : plot.b;
    const reading = which === 0 ? top[hour] : bottom[hour];
    const textWidth = labelWidths[index][which];
    const x = Math.min(width - textWidth, Math.max(0, ((hour + 0.5) * width) / HOURS - textWidth / 2));
    const y = yOf(reading, plot, height) + (which === 0 ? -PEAK_RISE : LOW_DROP);
    let shown;
    if (still) shown = leaving ? 1 - p / FADE : smooth(p, FADE, FADE * 2);
    else shown = leaving ? 1 - smooth(p, 0, LABEL_OUT) : smooth(p, C_END, C_END + LABEL_IN);
    const red = which === 0 ? plot.peakAbove : plot.troughBelow;
    return { text: labels[index][which], x, y, shown, color: red ? RED_INK : plot.colors[which === 0 ? 0 : plot.lines === 2 ? 1 : 0] };
  };
  const peak = useDerivedValue(() => extreme(0, phase.get(), fromIndex.get(), toIndex.get()));
  const low = useDerivedValue(() => extreme(1, phase.get(), fromIndex.get(), toIndex.get()));
  const peakText = useDerivedValue(() => peak.get().text);
  const peakX = useDerivedValue(() => peak.get().x);
  const peakY = useDerivedValue(() => peak.get().y);
  const peakShown = useDerivedValue(() => peak.get().shown);
  const peakColor = useDerivedValue(() => peak.get().color);
  const lowText = useDerivedValue(() => low.get().text);
  const lowX = useDerivedValue(() => low.get().x);
  const lowY = useDerivedValue(() => low.get().y);
  const lowShown = useDerivedValue(() => low.get().shown);
  const lowColor = useDerivedValue(() => low.get().color);

  const grid = useMemo(() => {
    let d = '';
    for (const at of TICK_HEIGHTS) d += `M0 ${Math.round(height * (1 - at)) + 0.5}H${width}`;
    return d;
  }, [height, width]);

  const frameStyle = useAnimatedStyle(() => {
    const config = { duration: still ? 0 : SELECT_MOVE, easing: Easing.out(Easing.cubic) };
    return {
      width: tileWidth,
      transform: [
        { translateX: withTiming((selected % 2) * (tileWidth + GAP), config) },
        { translateY: withTiming(Math.floor(selected / 2) * (TILE_HEIGHT + GAP), config) },
      ],
    };
  });

  const onChartLayout = (event: LayoutChangeEvent) => {
    const { width: w, height: h } = event.nativeEvent.layout;
    setSize({ width: w - AXIS, height: h - HOUR_LABELS });
  };

  const vital = vitals[selected];
  const caption =
    vital.chart.kind === 'bars' ? 'Hourly low to high' : vital.chart.second ? 'Systolic and diastolic, hourly' : 'Hourly average';

  return (
    <View style={styles.screen}>
      <StatusBar style="light" />
      <View style={[styles.body, { paddingTop: insets.top + 12, paddingBottom: Math.max(insets.bottom, 16) }]}>
        <Text style={styles.title}>{title}</Text>
        <Text style={styles.subtitle}>{subtitle}</Text>

        <View style={styles.tiles} onLayout={(event) => setTileWidth((event.nativeEvent.layout.width - GAP) / 2)}>
          {vitals.map((item, index) => (
            <View key={item.key} style={{ width: tileWidth }}>
              <Tile vital={item} selected={index === selected} onPress={() => pressTile(index)} />
            </View>
          ))}
          {/* The selection is one frame that moves between the tiles, drawn over them. */}
          <Animated.View pointerEvents="none" style={[styles.frame, frameStyle]} />
        </View>

        <View style={styles.card}>
          <View style={styles.cardHeader}>
            <Animated.Text key={vital.key} entering={FadeIn.duration(200)} exiting={FadeOut.duration(120)} style={styles.cardTitle}>
              {vital.name}
            </Animated.Text>
            <Animated.Text key={`${vital.key}-caption`} entering={FadeIn.duration(200)} style={styles.cardCaption}>
              {caption}
            </Animated.Text>
          </View>

          <View style={styles.chart} onLayout={onChartLayout} accessibilityLabel={`${vital.name}, ${caption}`}>
            <Animated.View key={vital.key} entering={FadeIn.duration(200).delay(still ? 0 : 300)} exiting={FadeOut.duration(150)} style={styles.axis}>
              {vital.ticks.map((value, k) => (
                <Text key={value} style={[styles.axisLabel, { top: height * (1 - TICK_HEIGHTS[k]) - 7 }]}>
                  {value}
                </Text>
              ))}
            </Animated.View>
            <Canvas style={[styles.canvas, { width, height: height + 20 }]}>
              <Path path={grid} style="stroke" strokeWidth={1} color={GRID}>
                <DashPathEffect intervals={[2, 5]} />
              </Path>
              <Group opacity={opacity}>
                <Group clip={barPlain}>
                  <Path path={bars} color={color1} />
                </Group>
                <Group clip={barRed}>
                  <Path path={bars} color={RED} />
                </Group>
                <Group clip={linePlain}>
                  <Path path={line1} style="stroke" strokeWidth={LINE_WIDTH} strokeJoin="round" strokeCap="round" color={color1} end={trim} />
                  <Path path={line2} style="stroke" strokeWidth={LINE_WIDTH} strokeJoin="round" strokeCap="round" color={color2} end={trim} opacity={secondLine} />
                  <Path path={dots2} color={color2} />
                  <Path path={dots1} color={color1} />
                </Group>
                <Group clip={lineRed}>
                  <Path path={line1} style="stroke" strokeWidth={LINE_WIDTH} strokeJoin="round" strokeCap="round" color={RED_INK} end={trim} />
                  <Path path={line2} style="stroke" strokeWidth={LINE_WIDTH} strokeJoin="round" strokeCap="round" color={RED_INK} end={trim} opacity={secondLine} />
                  <Path path={dots2} color={RED_INK} />
                  <Path path={dots1} color={RED_INK} />
                </Group>
              </Group>
              <SkiaText font={font} text={peakText} x={peakX} y={peakY} color={peakColor} opacity={peakShown} />
              <SkiaText font={font} text={lowText} x={lowX} y={lowY} color={lowColor} opacity={lowShown} />
            </Canvas>
            <View style={[styles.hours, { top: height + 8 }]}>
              {hours.map((label, k) => (
                <View key={label} style={[styles.hour, { left: AXIS + ((k * 4 + 0.5) * width) / HOURS - 30 }]}>
                  <View style={styles.hourTick} />
                  <Text style={styles.hourLabel}>{label}</Text>
                </View>
              ))}
            </View>
          </View>
          <Text style={styles.footnote}>{footnote}</Text>
        </View>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  screen: {
    flex: 1,
    backgroundColor: BACKGROUND,
  },
  body: {
    flex: 1,
    paddingHorizontal: MARGIN,
  },
  title: {
    color: INK,
    fontSize: 30,
    lineHeight: 36,
    fontWeight: '700',
    letterSpacing: -0.6,
  },
  subtitle: {
    marginTop: 2,
    color: MUTE,
    fontSize: 13,
    lineHeight: 18,
  },
  tiles: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: GAP,
    marginTop: 20,
  },
  frame: {
    position: 'absolute',
    left: 0,
    top: 0,
    height: TILE_HEIGHT,
    borderRadius: TILE_RADIUS,
    borderCurve: 'continuous',
    borderWidth: 1,
    borderColor: SELECTED_EDGE,
    backgroundColor: SELECTED,
  },
  tile: {
    height: TILE_HEIGHT,
    paddingHorizontal: 12,
    paddingVertical: 12,
    borderRadius: TILE_RADIUS,
    borderCurve: 'continuous',
    backgroundColor: CARD,
    justifyContent: 'space-between',
  },
  tileTop: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
  },
  tileName: {
    flex: 1,
    color: SOFT,
    fontSize: 12,
    lineHeight: 16,
  },
  tileNameOn: {
    color: INK,
  },
  tileBottom: {
    flexDirection: 'row',
    alignItems: 'baseline',
    justifyContent: 'space-between',
  },
  tileValue: {
    flexShrink: 1,
    color: INK,
    fontSize: 22,
    lineHeight: 27,
    fontWeight: '600',
    fontVariant: ['tabular-nums'],
  },
  tileUnit: {
    color: MUTE,
    fontSize: 11,
    fontWeight: '400',
  },
  warning: {
    color: RED_INK,
  },
  tileTime: {
    marginLeft: 6,
    color: MUTE,
    fontSize: 10,
    lineHeight: 13,
  },
  card: {
    flex: 1,
    marginTop: 16,
    padding: 16,
    borderRadius: TILE_RADIUS,
    borderCurve: 'continuous',
    backgroundColor: CARD,
    borderWidth: 1,
    borderColor: CARD_EDGE,
  },
  cardHeader: {
    height: 42,
  },
  cardTitle: {
    color: INK,
    fontSize: 16,
    lineHeight: 21,
    fontWeight: '600',
  },
  cardCaption: {
    color: MUTE,
    fontSize: 12,
    lineHeight: 16,
  },
  chart: {
    flex: 1,
    marginTop: 16,
  },
  axis: {
    position: 'absolute',
    left: 0,
    top: 0,
    bottom: 0,
    width: AXIS,
  },
  axisLabel: {
    position: 'absolute',
    left: 0,
    color: MUTE,
    fontSize: 10,
    lineHeight: 14,
    fontVariant: ['tabular-nums'],
  },
  canvas: {
    position: 'absolute',
    left: AXIS,
    top: 0,
  },
  hours: {
    position: 'absolute',
    left: 0,
    right: 0,
    height: HOUR_LABELS,
  },
  hour: {
    position: 'absolute',
    top: 0,
    width: 60,
    alignItems: 'center',
  },
  hourTick: {
    width: 2,
    height: 4,
    borderRadius: 1,
    backgroundColor: GRID,
  },
  hourLabel: {
    marginTop: 4,
    color: MUTE,
    fontSize: 10,
    lineHeight: 13,
  },
  footnote: {
    marginTop: 4,
    color: MUTE,
    fontSize: 10,
    lineHeight: 13,
  },
});
