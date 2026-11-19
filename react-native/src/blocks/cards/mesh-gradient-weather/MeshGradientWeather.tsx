import { Canvas, Fill, Shader, Skia } from '@shopify/react-native-skia';
import { BlurView } from 'expo-blur';
import { GlassView, isLiquidGlassAvailable } from 'expo-glass-effect';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { SymbolView } from 'expo-symbols';
import type { SFSymbol } from 'sf-symbols-typescript';
import { useCallback, type ReactNode } from 'react';
import { LinearGradient } from 'expo-linear-gradient';
import { PixelRatio, Platform, Pressable, ScrollView, StyleSheet, Text, TextInput, useWindowDimensions, View } from 'react-native';
import Animated, {
  scrollTo,
  useAnimatedProps,
  useAnimatedReaction,
  useAnimatedRef,
  useAnimatedScrollHandler,
  useAnimatedStyle,
  useDerivedValue,
  useFrameCallback,
  useSharedValue,
  type SharedValue,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';

import { useReducedMotionPreference } from '@/shared/useReducedMotionPreference';

import {
  CONDITIONS,
  HOURS,
  LAST_HOUR,
  LOOP,
  RAIN_PCT,
  TEMPS,
  WIND,
  condWeight,
  easeInOut,
  filmHour,
  filmRainGate,
  offsetsAt,
  rainAt,
  sampleTable,
  skyAt,
} from './weatherMotion';
import { WEATHER_SKSL } from './weatherShader';

const EFFECT = Skia.RuntimeEffect.Make(WEATHER_SKSL);
const WIDE = Platform.OS === 'ios' ? 1 : 0;
const DPR = PixelRatio.get();
const LIQUID = isLiquidGlassAvailable();

const ITEM = 62; // one hour in the row, pt
const IDLE_MS = 2000; // after the last touch, before the film comes back
const RETURN_MS = 1100; // the row scrolls back to Now
const MODE_AUTO = 0;
const MODE_MANUAL = 1;
const MODE_RETURN = 2;

/** Haptics live on the JS thread; worklets schedule this. */
function selectionTick() {
  void Haptics.selectionAsync();
}

Animated.addWhitelistedNativeProps({ text: true });
const AnimatedTextInput = Animated.createAnimatedComponent(TextInput);

type Props = { autoPlay?: boolean };

/**
 * A weather screen whose whole background is a living mesh gradient. The hourly row is a real
 * horizontal ScrollView: the hour it is scrolled to is the one number the sky, the temperature, the
 * condition and the tiles are drawn from, so scrubbing the row morphs the mesh through the day
 * (dawn, a sunny noon, an afternoon of rain on the glass, dusk, night).
 */
export function MeshGradientWeather({ autoPlay = true }: Props) {
  const { width, height } = useWindowDimensions();
  const insets = useSafeAreaInsets();
  const reduced = useReducedMotionPreference();
  const film = autoPlay && !reduced;
  const compact = height < 740;

  const contentW = Math.min(width, 480);
  const cardW = contentW - 32;
  const pad = (cardW - ITEM) / 2;

  const scroll = useAnimatedRef<Animated.ScrollView>();
  const hour = useSharedValue(0); // fractional index into HOURS
  const amb = useSharedValue(0); // ambient clock, seconds in [0, LOOP)
  const mode = useSharedValue(film ? MODE_AUTO : MODE_MANUAL);
  const origin = useSharedValue(-1); // timestamp the ambient clock counts from
  const loopStart = useSharedValue(-1);
  const lastActivity = useSharedValue(-1);
  const dragging = useSharedValue(false);
  const retFrom = useSharedValue(0);
  const retStart = useSharedValue(-1);
  const gate = useSharedValue(1); // rain allowed on the glass (off while the row rewinds)

  useFrameCallback((frame) => {
    'worklet';
    const now = frame.timestamp;
    if (origin.get() < 0) origin.set(now);
    // the drift never resets, so the film's restart after a touch is seamless too
    if (!reduced) amb.set((((now - origin.get()) / 1000) % LOOP + LOOP) % LOOP);
    const m = mode.get();
    if (m === MODE_AUTO) {
      if (!film) {
        mode.set(MODE_MANUAL);
        return;
      }
      if (loopStart.get() < 0) loopStart.set(now);
      const t = ((now - loopStart.get()) / 1000) % LOOP;
      // the very first loop shares its origin with the drift, so the recording loops exactly
      const h = filmHour(t);
      hour.set(h);
      gate.set(filmRainGate(t));
      scrollTo(scroll, h * ITEM, 0, false);
      return;
    }
    if (m === MODE_RETURN) {
      const p = Math.min(1, (now - retStart.get()) / RETURN_MS);
      const h = retFrom.get() * (1 - easeInOut(p));
      hour.set(h);
      gate.set(0);
      scrollTo(scroll, h * ITEM, 0, false);
      if (p >= 1) {
        loopStart.set(-1);
        mode.set(MODE_AUTO);
      }
      return;
    }
    if (lastActivity.get() < 0 || dragging.get()) {
      lastActivity.set(now);
      return;
    }
    if (film && now - lastActivity.get() > IDLE_MS) {
      retFrom.set(hour.get());
      retStart.set(now);
      mode.set(MODE_RETURN);
    }
  });

  const pause = useCallback(() => {
    'worklet';
    mode.set(MODE_MANUAL);
    gate.set(1);
    lastActivity.set(-1);
  }, [gate, lastActivity, mode]);

  const onScroll = useAnimatedScrollHandler({
    onScroll: (e) => {
      if (mode.get() !== MODE_MANUAL) return;
      const h = e.contentOffset.x / ITEM;
      hour.set(h < 0 ? 0 : h > LAST_HOUR ? LAST_HOUR : h);
      lastActivity.set(-1);
    },
    onBeginDrag: () => {
      pause();
      dragging.set(true);
    },
    onEndDrag: () => {
      dragging.set(false);
      lastActivity.set(-1);
    },
    onMomentumEnd: () => {
      lastActivity.set(-1);
    },
  });

  const tapHour = useCallback(
    (i: number) => {
      scheduleOnUI(pause);
      scroll.current?.scrollTo({ x: i * ITEM, animated: true });
    },
    [pause, scroll],
  );

  // a light tick for each hour that passes under a finger (never during the film)
  useAnimatedReaction(
    () => Math.round(hour.get()),
    (cur, prev) => {
      if (prev == null || cur === prev || mode.get() !== MODE_MANUAL) return;
      scheduleOnRN(selectionTick);
    },
  );

  const uniforms = useDerivedValue(() => {
    const h = hour.get();
    const rain = rainAt(h) * gate.get();
    const lab: number[] = new Array(48);
    const off: number[] = new Array(32);
    skyAt(h, lab);
    offsetsAt(h, amb.get(), rain, off);
    return { size: [width, height], off, lab, rain, phase: amb.get() / LOOP, wide: WIDE, dpr: DPR };
  });

  const condBlock = compact ? 24 : 26;

  return (
    <View style={styles.screen}>
      <StatusBar style="light" />
      {EFFECT ? (
        <Canvas style={StyleSheet.absoluteFill} pointerEvents="none">
          <Fill>
            <Shader source={EFFECT} uniforms={uniforms} />
          </Fill>
        </Canvas>
      ) : null}

      <ScrollView
        style={{ width: contentW }}
        contentContainerStyle={[styles.column, { paddingTop: insets.top + (compact ? 12 : 24), paddingBottom: insets.bottom + 16 }]}
        showsVerticalScrollIndicator={false}
      >
        <View style={styles.header}>
          <View style={styles.place}>
            <SymbolView name="location.fill" size={13} tintColor="rgba(255,255,255,0.9)" type="monochrome" />
            <Text style={styles.placeSmall}>My Location</Text>
          </View>
          <Text style={[styles.city, compact && styles.cityCompact]}>Porto</Text>
          <Temperature hour={hour} size={compact ? 84 : 100} />
          <View style={{ height: condBlock }}>
            {CONDITIONS.map((c, i) => (
              <Condition key={c} label={c} index={i} hour={hour} />
            ))}
          </View>
          <Text style={styles.hiLo}>H:20°  L:11°</Text>
        </View>

        <Glass radius={26} style={[styles.card, { marginTop: compact ? 18 : 22 }]}>
          <View style={styles.cardHead}>
            <SymbolView name="cloud.rain.fill" size={14} tintColor="rgba(255,255,255,0.75)" type="monochrome" />
            <Text style={styles.cardHeadText}>Rain from 2 PM, clearing by 6 PM.</Text>
          </View>
          <View style={styles.rule} />
          <View style={styles.rowWrap}>
            <View pointerEvents="none" style={[styles.selected, { left: pad + 3 }]} />
            <Animated.ScrollView
              ref={scroll}
              horizontal
              showsHorizontalScrollIndicator={false}
              onScroll={onScroll}
              scrollEventThrottle={16}
              snapToInterval={ITEM}
              decelerationRate="fast"
              contentContainerStyle={{ paddingHorizontal: pad }}
            >
              {HOURS.map((h, i) => (
                <Pressable key={h.label} onPress={() => tapHour(i)} style={styles.item} accessibilityLabel={`${h.label}, ${h.temp} degrees`}>
                  <Text style={styles.itemLabel}>{h.label}</Text>
                  <SymbolView name={h.symbol as SFSymbol} size={26} type="multicolor" style={styles.itemSymbol} />
                  <Text style={styles.itemTemp}>{h.temp}°</Text>
                </Pressable>
              ))}
            </Animated.ScrollView>
          </View>
        </Glass>

        <View style={[styles.tiles, { marginTop: 12 }]}>
          <Tile icon="umbrella.fill" title="Chance of rain" hour={hour} table={RAIN_PCT} unit="%" note="Bring a jacket after lunch." />
          <Tile icon="wind" title="Wind" hour={hour} table={WIND} unit=" km/h" note="From the northwest." />
        </View>

        <Forecast width={cardW} hour={hour} />
      </ScrollView>
    </View>
  );
}

function Temperature({ hour, size }: { hour: SharedValue<number>; size: number }) {
  const props = useAnimatedProps(() => {
    const v = `${Math.round(sampleTable(TEMPS, hour.get()))}°`;
    return { text: v, defaultValue: v } as never;
  });
  return (
    <AnimatedTextInput
      animatedProps={props}
      editable={false}
      underlineColorAndroid="transparent"
      style={[styles.temp, { fontSize: size, lineHeight: size * 1.08, marginLeft: -size * 0.05 }]}
    />
  );
}

function Condition({ label, index, hour }: { label: string; index: number; hour: SharedValue<number> }) {
  const style = useAnimatedStyle(() => {
    const w = condWeight(index, hour.get());
    return { opacity: w, transform: [{ translateY: (1 - w) * 6 }] };
  });
  return <Animated.Text style={[styles.cond, style]}>{label}</Animated.Text>;
}

function Tile({
  icon,
  title,
  hour,
  table,
  unit,
  note,
}: {
  icon: SFSymbol;
  title: string;
  hour: SharedValue<number>;
  table: number[];
  unit: string;
  note: string;
}) {
  const props = useAnimatedProps(() => {
    const v = `${Math.round(sampleTable(table, hour.get()))}${unit}`;
    return { text: v, defaultValue: v } as never;
  });
  return (
    <Glass radius={22} style={styles.tile}>
      <View style={styles.tileHead}>
        <SymbolView name={icon} size={13} tintColor="rgba(255,255,255,0.72)" type="monochrome" />
        <Text style={styles.tileTitle}>{title}</Text>
      </View>
      <AnimatedTextInput animatedProps={props} editable={false} underlineColorAndroid="transparent" style={styles.tileValue} />
      <Text style={styles.tileNote}>{note}</Text>
    </Glass>
  );
}

// ---------------------------------------------------------------- the week

const DAYS: { day: string; symbol: SFSymbol; lo: number; hi: number }[] = [
  { day: 'Today', symbol: 'cloud.sun.rain.fill', lo: 11, hi: 20 },
  { day: 'Fri', symbol: 'sun.max.fill', lo: 12, hi: 22 },
  { day: 'Sat', symbol: 'sun.max.fill', lo: 13, hi: 23 },
  { day: 'Sun', symbol: 'cloud.fill', lo: 12, hi: 19 },
  { day: 'Mon', symbol: 'cloud.rain.fill', lo: 10, hi: 17 },
];
const WEEK_LO = 10;
const WEEK_HI = 23;
/** The week's temperature scale, cool to warm; each day's bar shows its own slice of it. */
const RANGE_COLORS = ['#8FD3E8', '#B5E2B0', '#F3D67F', '#F2A862'] as const;
const DAY_COL = 52;
const NUM_COL = 34;

/**
 * Five days with temperature range bars on one shared scale; today's bar carries a dot at the
 * temperature of the hour the row is scrolled to, so it moves with the scrub too.
 */
function Forecast({ width, hour }: { width: number; hour: SharedValue<number> }) {
  const track = width - 32 - DAY_COL - 30 - NUM_COL * 2 - 8 * 4;
  const x = (v: number) => ((v - WEEK_LO) / (WEEK_HI - WEEK_LO)) * track;
  return (
    <Glass radius={22} style={styles.week}>
      <View style={styles.cardHead}>
        <SymbolView name="calendar" size={14} tintColor="rgba(255,255,255,0.75)" type="monochrome" />
        <Text style={styles.cardHeadText}>5-Day Forecast</Text>
      </View>
      <View style={styles.rule} />
      {DAYS.map((d, i) => (
        <View key={d.day} style={[styles.dayRow, i > 0 && styles.dayRule]}>
          <Text style={styles.dayName}>{d.day}</Text>
          <SymbolView name={d.symbol} size={20} type="multicolor" style={styles.daySymbol} />
          <Text style={styles.dayLo}>{d.lo}°</Text>
          <View style={[styles.rangeTrack, { width: track }]}>
            <View style={[styles.rangeClip, { left: x(d.lo), width: x(d.hi) - x(d.lo) }]}>
              <LinearGradient
                colors={RANGE_COLORS}
                start={{ x: 0, y: 0 }}
                end={{ x: 1, y: 0 }}
                style={{ position: 'absolute', top: 0, bottom: 0, width: track, left: -x(d.lo) }}
              />
            </View>
            {i === 0 ? <NowDot hour={hour} track={track} /> : null}
          </View>
          <Text style={styles.dayHi}>{d.hi}°</Text>
        </View>
      ))}
    </Glass>
  );
}

function NowDot({ hour, track }: { hour: SharedValue<number>; track: number }) {
  const style = useAnimatedStyle(() => {
    const v = sampleTable(TEMPS, hour.get());
    return { transform: [{ translateX: ((v - WEEK_LO) / (WEEK_HI - WEEK_LO)) * track - 5 }] };
  });
  return <Animated.View style={[styles.nowDot, style]} />;
}

/** Liquid Glass on iOS 26; a dark blur before it; a tinted fill on Android. */
function Glass({ radius, style, children }: { radius: number; style?: object; children: ReactNode }) {
  return (
    <View style={[{ borderRadius: radius, overflow: 'hidden' }, style]}>
      {LIQUID ? (
        <GlassView glassEffectStyle="regular" colorScheme="dark" tintColor="rgba(28,34,58,0.16)" style={StyleSheet.absoluteFill} />
      ) : (
        <>
          {Platform.OS === 'ios' ? <BlurView tint="dark" intensity={26} style={StyleSheet.absoluteFill} /> : null}
          <View style={[StyleSheet.absoluteFill, { backgroundColor: 'rgba(28,34,58,0.24)' }]} />
        </>
      )}
      <View pointerEvents="none" style={[StyleSheet.absoluteFill, { borderRadius: radius, borderWidth: StyleSheet.hairlineWidth, borderColor: 'rgba(255,255,255,0.28)' }]} />
      {children}
    </View>
  );
}

const SHADOW = { textShadowColor: 'rgba(16,20,44,0.22)', textShadowRadius: 14, textShadowOffset: { width: 0, height: 1 } };

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: '#7C86C8', alignItems: 'center' },
  column: { paddingHorizontal: 16 },
  header: { paddingHorizontal: 8 },
  place: { flexDirection: 'row', alignItems: 'center', gap: 5 },
  placeSmall: { color: 'rgba(255,255,255,0.88)', fontSize: 13, fontWeight: '600', ...SHADOW },
  city: { color: '#FFFFFF', fontSize: 34, fontWeight: '700', marginTop: 2, ...SHADOW },
  cityCompact: { fontSize: 28 },
  temp: { color: '#FFFFFF', fontWeight: '200', fontVariant: ['tabular-nums'], padding: 0, ...SHADOW },
  cond: { position: 'absolute', left: 0, top: 0, color: '#FFFFFF', fontSize: 21, fontWeight: '600', ...SHADOW },
  hiLo: { color: 'rgba(255,255,255,0.88)', fontSize: 17, fontWeight: '500', marginTop: 2, fontVariant: ['tabular-nums'], ...SHADOW },
  card: {},
  cardHead: { flexDirection: 'row', alignItems: 'center', gap: 7, paddingHorizontal: 16, paddingTop: 13, paddingBottom: 11 },
  cardHeadText: { color: 'rgba(255,255,255,0.86)', fontSize: 14, fontWeight: '500' },
  rule: { height: StyleSheet.hairlineWidth, marginHorizontal: 16, backgroundColor: 'rgba(255,255,255,0.3)' },
  rowWrap: { height: 108, justifyContent: 'center' },
  selected: {
    position: 'absolute',
    top: 8,
    bottom: 8,
    width: ITEM - 6,
    borderRadius: 28,
    backgroundColor: 'rgba(255,255,255,0.16)',
    borderWidth: StyleSheet.hairlineWidth,
    borderColor: 'rgba(255,255,255,0.4)',
  },
  item: { width: ITEM, alignItems: 'center', justifyContent: 'center', paddingVertical: 10 },
  itemLabel: { color: 'rgba(255,255,255,0.9)', fontSize: 13, fontWeight: '600' },
  itemSymbol: { width: 28, height: 28, marginVertical: 8 },
  itemTemp: { color: '#FFFFFF', fontSize: 18, fontWeight: '600', fontVariant: ['tabular-nums'] },
  tiles: { flexDirection: 'row', gap: 12 },
  tile: { flex: 1, minHeight: 112, padding: 14 },
  tileHead: { flexDirection: 'row', alignItems: 'center', gap: 6 },
  tileTitle: { color: 'rgba(255,255,255,0.75)', fontSize: 13, fontWeight: '600' },
  tileValue: { color: '#FFFFFF', fontSize: 28, fontWeight: '600', fontVariant: ['tabular-nums'], padding: 0, marginTop: 8 },
  week: { marginTop: 12, paddingBottom: 4 },
  dayRow: { flexDirection: 'row', alignItems: 'center', gap: 8, height: 33, marginHorizontal: 16 },
  dayRule: { borderTopWidth: StyleSheet.hairlineWidth, borderTopColor: 'rgba(255,255,255,0.18)' },
  dayName: { width: DAY_COL, color: '#FFFFFF', fontSize: 16, fontWeight: '600' },
  daySymbol: { width: 30, height: 22 },
  dayLo: { width: NUM_COL, color: 'rgba(255,255,255,0.6)', fontSize: 16, fontWeight: '600', textAlign: 'right', fontVariant: ['tabular-nums'] },
  dayHi: { width: NUM_COL, color: '#FFFFFF', fontSize: 16, fontWeight: '600', fontVariant: ['tabular-nums'] },
  rangeTrack: { height: 5, borderRadius: 3, backgroundColor: 'rgba(16,20,44,0.22)', justifyContent: 'center' },
  rangeClip: { position: 'absolute', top: 0, bottom: 0, borderRadius: 3, overflow: 'hidden' },
  nowDot: { position: 'absolute', left: 0, top: -2.5, width: 10, height: 10, borderRadius: 5, backgroundColor: '#FFFFFF', borderWidth: 2, borderColor: 'rgba(16,20,44,0.35)' },
  tileNote: { color: 'rgba(255,255,255,0.75)', fontSize: 13, fontWeight: '500', marginTop: 4 },
});
