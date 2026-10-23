import { Canvas, Group, Image as SkiaImage, LinearGradient, Mask, Path as SkiaPath, Rect, Skia, useImage, vec } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { Image } from 'expo-image';
import { useCallback, useEffect, useRef, useState } from 'react';
import { Pressable, StyleSheet, Text, type TextStyle, View } from 'react-native';
import Animated, {
  type SharedValue,
  useAnimatedStyle,
  useDerivedValue,
  useFrameCallback,
  useReducedMotion,
  useSharedValue,
  withSpring,
  withTiming,
} from 'react-native-reanimated';
import Svg, { Path } from 'react-native-svg';

export type ResortWeatherCardResort = {
  name: string;
  region: string;
  /** Degrees Celsius, between -99 and 99: the display ticks to it, and colder resorts get more snow. */
  temperature: number;
  /** The count in the pill on the photo. */
  likes: string;
  /** The three facts under the name. */
  runs: string;
  distance: string;
  lifts: string;
  /** Addresses of the photo and of its small copy for the strip, and the flat colour shown until they arrive. */
  photo: string;
  thumbnail: string;
  tint: string;
};

export type ResortWeatherCardFan = { initials: string; color: string };

type Props = {
  resorts?: ResortWeatherCardResort[];
  /** The three round badges on the photo, smallest first. */
  fans?: ResortWeatherCardFan[];
  /** Picks the next resort by itself every 2.2 seconds. */
  autoPlay?: boolean;
  country?: string;
  mapLabel?: string;
  temperatureLabel?: string;
  temperatureNote?: string;
  /** Called with the index of the resort a thumbnail picks. */
  onSelect?: (index: number) => void;
  onBack?: () => void;
  onMap?: () => void;
};

const WIDTH = 340;
const HEIGHT = 600;
const RADIUS = 34;

const HEADER_Y = 38;
// The strip of thumbnails: their side and corner at rest and when picked.
const THUMB_Y = 96;
const THUMB = 44;
const THUMB_PICKED = 60;
const THUMB_RADIUS = 12;
const THUMB_PICKED_RADIUS = 16;
const THUMB_GAP = 8;

const CARD_TOP = 138;
const CARD = { width: 320, height: 440 };
const CARD_RADIUS = 36;
// The photo inside the card.
const PHOTO = { x: 6, y: 6, width: 308, height: 250 };
const PHOTO_RADIUS = 30;
const DOTS_Y = 272;
const NAME_Y = 302;
const REGION_Y = 330;
const FACTS = { x: 20, y: 364, width: 280, height: 52 };
const FACT = '#6c6f73';

// On the photo: the temperature pill, the fans (smallest first) and the pill with the count.
const PILL = { width: 178, height: 40 };
const FAN_SPOTS = [
  { x: 14, y: 170, size: 36 },
  { x: 60, y: 192, size: 40 },
  { x: 50, y: 142, size: 46 },
];
const LIKES = { width: 118, height: 32 };

// The seven-segment display: a glyph's box, the distance between slots, and the stroke.
const GLYPH = { width: 11, height: 20 };
const GLYPH_PITCH = 16;
const DEGREE_X = 49;
const DEGREE_WIDTH = 6;
const UNIT_X = 60;
const SEGMENT = 1.8;
const SEGMENT_GAP = 1.6;
const DISPLAY = { width: 73, height: 22 };
// Lit segments of 0…9, one bit each: a (top), b, c, d (bottom), e, f, g (middle).
const DIGITS = [0b0111111, 0b0000110, 0b1011011, 0b1001111, 0b1100110, 0b1101101, 0b1111101, 0b0000111, 0b1111111, 0b1101111];
const MINUS = 0b1000000;
const DEGREE = 0b1100011;
const UNIT = 0b0111001;

// A change (seconds from the tap): the photo wipes, the display ticks, the texts roll.
const WIPE_SECONDS = 0.75;
// Height of the soft band at the wipe's edge.
const FEATHER = 90;
const TICK_AT = 0.05;
const TICK_SECONDS = 0.8;
const NAME_AT = 0.1;
const REGION_AT = 0.16;
const FACTS_AT = 0.22;
const FACTS_GAP = 0.05;
const ROLL_SECONDS = 0.4;
const CHANGE_SECONDS = 0.9;
// The selection follows a spring of mass 1.
const PICK_SPRING = { mass: 1, stiffness: 170, damping: 20 };

// Snow: how many flakes a resort gets for its temperature.
const FLAKES = 56;
const FLAKES_AT_ZERO = 12;
const FLAKES_PER_DEGREE = 3;
const FLAKES_LEAST = 8;

// autoPlay (seconds): the next resort every LOOP / count, starting at FIRST.
const FIRST = 0.8;
const LOOP = 8.8;
// After a touch the loop waits this long, returns to the first resort, and starts over.
const IDLE = 2.5;
const SETTLE = 1.2;

const TAU = Math.PI * 2;

const BACK_ICON = 'M17 10 L3 10 M8.5 4.5 L3 10 L8.5 15.5';
const PIN_ICON = 'M7 1.5 C9.5 1.5 11.5 3.5 11.5 6 C11.5 8.5 9.5 10.5 7 10.5 C4.5 10.5 2.5 8.5 2.5 6 C2.5 3.5 4.5 1.5 7 1.5 Z M7 10.5 L7 15';
const FLAKE_ICON = 'M7 1 L7 13 M1.8 4 L12.2 10 M1.8 10 L12.2 4';
const HEART_ICON =
  'M6 10.6 C2.6 8.2 1 6.5 1 4.5 C1 3 2.2 1.9 3.6 1.9 C4.6 1.9 5.4 2.4 6 3.3 C6.6 2.4 7.4 1.9 8.4 1.9 C9.8 1.9 11 3 11 4.5 C11 6.5 9.4 8.2 6 10.6 Z';
const RUNS_ICON =
  'M11.5 3.2 C12.3 3.2 12.9 3.8 12.9 4.6 C12.9 5.4 12.3 6 11.5 6 C10.7 6 10.1 5.4 10.1 4.6 C10.1 3.8 10.7 3.2 11.5 3.2 Z M4.5 7 L8.5 8.6 L11.6 7.8 M8.5 8.6 L10.6 12.4 L7 13.6 M2.5 13 L14.5 17 M13.2 16.6 L15.8 15.6';
const DISTANCE_ICON =
  'M2 15.5 L7.2 6 L10.2 11 L12 8.5 L16 15.5 Z M13.6 2.6 C14.6 2.6 15.4 3.4 15.4 4.4 C15.4 5.4 14.6 6.2 13.6 6.2 C12.6 6.2 11.8 5.4 11.8 4.4 C11.8 3.4 12.6 2.6 13.6 2.6 Z';
const LIFTS_ICON =
  'M3.5 4.5 L14.5 4.5 C15.6 4.5 16 5 16 6 L16 12 C16 13 15.6 13.5 14.5 13.5 L3.5 13.5 C2.4 13.5 2 13 2 12 L2 6 C2 5 2.4 4.5 3.5 4.5 Z M2 8 L16 8';

type Flake = {
  /** Where it starts: across the photo and down its fall, both 0…1. */
  x: number;
  start: number;
  /** Whole falls and whole swings per loop, so every flake is back where it began when the loop ends. */
  falls: number;
  swings: number;
  sway: number;
  radius: number;
  phase: number;
};

/** The flakes, from a fixed sequence (Park–Miller): the same snowfall every time. */
function flakes() {
  let seed = 11;
  const next = () => {
    seed = (seed * 16807) % 2147483647;
    return seed / 2147483647;
  };
  const list: Flake[] = [];
  for (let i = 0; i < FLAKES; i++) {
    list.push({
      x: next(),
      start: next(),
      falls: 1 + Math.floor(next() * 3),
      swings: 2 + Math.floor(next() * 4),
      sway: 3 + next() * 7,
      radius: 0.8 + next() * 1.2,
      phase: next() * TAU,
    });
  }
  return list;
}

const SNOW = flakes();
// Three sizes of flake, the small ones fainter.
const SNOW_LAYERS = [
  { below: 1.2, opacity: 0.5 },
  { below: 1.6, opacity: 0.7 },
  { below: 9, opacity: 0.9 },
];

function clamp01(t: number) {
  'worklet';
  return Math.min(1, Math.max(0, t));
}

/**
 * How far the text of resort `i` has rolled, `t` seconds into its turn: `shift` is in line
 * heights, below the line while arriving and above it once it has left.
 */
function rollOf(i: number, from: number, to: number, t: number) {
  'worklet';
  const p = clamp01(t / ROLL_SECONDS);
  const e = 1 - Math.pow(1 - p, 3);
  if (i === to) return { shift: 1 - e, opacity: from === to ? 1 : e };
  if (i === from) return { shift: -e, opacity: 1 - e };
  return { shift: 0, opacity: 0 };
}

/** The number on the display `t` seconds into a change: it counts through every degree in between, slowing down. */
function degreesOf(from: number, to: number, t: number) {
  'worklet';
  const p = clamp01((t - TICK_AT) / TICK_SECONDS);
  const e = 1 - (1 - p) * (1 - p);
  return Math.round(from + (to - from) * e);
}

function flakeCount(temperature: number) {
  'worklet';
  return Math.min(FLAKES, Math.max(FLAKES_LEAST, FLAKES_AT_ZERO - temperature * FLAKES_PER_DEGREE));
}

/** Adds to `path` the segments of a glyph whose box starts at `x` that `mask` lights, in the order a, b, c, d, e, f, g. */
function addSegments(path: ReturnType<typeof Skia.Path.Make>, x: number, width: number, mask: number) {
  'worklet';
  const top = 1;
  const bottom = top + GLYPH.height;
  const middle = (top + bottom) / 2;
  const left = x + 1;
  const right = left + width;
  const gap = SEGMENT_GAP;
  const segments = [
    [left + gap, top, right - gap, top],
    [right, top + gap, right, middle - gap],
    [right, middle + gap, right, bottom - gap],
    [left + gap, bottom, right - gap, bottom],
    [left, middle + gap, left, bottom - gap],
    [left, top + gap, left, middle - gap],
    [left + gap, middle, right - gap, middle],
  ];
  for (let bit = 0; bit < 7; bit++) {
    if ((mask & (1 << bit)) === 0) continue;
    path.moveTo(segments[bit][0], segments[bit][1]);
    path.lineTo(segments[bit][2], segments[bit][3]);
  }
}

/** The three slots also show their unlit segments, faintly, like a real display. */
function ghostSegments() {
  const path = Skia.Path.Make();
  for (let slot = 0; slot < 3; slot++) addSegments(path, slot * GLYPH_PITCH, GLYPH.width, 0b1111111);
  return path;
}

const GHOST = ghostSegments();

type Motion = {
  /** Seconds since the card appeared: the clock the snow falls on. */
  time: SharedValue<number>;
  /** The resort that is leaving, the one that is arriving, and when that started. */
  from: SharedValue<number>;
  to: SharedValue<number>;
  changedAt: SharedValue<number>;
  /** Under reduced motion a change is a jump and the snow stands still. */
  reduced: boolean;
};

/** Seconds since the resort changed. */
function sinceChange(time: number, changedAt: number, reduced: boolean) {
  'worklet';
  return reduced ? CHANGE_SECONDS : Math.min(CHANGE_SECONDS, time - changedAt);
}

/**
 * One resort's photo. The arriving photo is wiped in from the top behind a soft band; when it
 * lies under the leaving one, that one is wiped away instead, which looks the same.
 */
function Photo({ resort, index, time, from, to, changedAt, reduced }: { resort: ResortWeatherCardResort; index: number } & Motion) {
  const image = useImage(resort.photo);
  const loaded = useSharedValue(0);
  useEffect(() => {
    if (image) loaded.set(withTiming(1, { duration: 200 }));
  }, [image, loaded]);

  const opacity = useDerivedValue(() => (index === from.get() || index === to.get() ? 1 : 0));
  const cut = useDerivedValue(() => {
    const p = clamp01(sinceChange(time.get(), changedAt.get(), reduced) / WIPE_SECONDS);
    const wipe = p < 0.5 ? 4 * p * p * p : 1 - Math.pow(-2 * p + 2, 3) / 2;
    return wipe * (PHOTO.height + FEATHER);
  });
  // Opaque above the band and clear below it for the arriving photo, the other way round for the leaving one.
  const start = useDerivedValue(() => {
    if (index === to.get() && to.get() > from.get()) return vec(0, cut.get() - FEATHER);
    if (index === from.get() && to.get() < from.get()) return vec(0, cut.get());
    return vec(0, PHOTO.height);
  });
  const end = useDerivedValue(() => {
    if (index === to.get() && to.get() > from.get()) return vec(0, cut.get());
    if (index === from.get() && to.get() < from.get()) return vec(0, cut.get() - FEATHER);
    return vec(0, PHOTO.height + FEATHER);
  });

  return (
    <Group opacity={opacity}>
      <Mask
        mask={
          <Rect x={0} y={0} width={PHOTO.width} height={PHOTO.height}>
            <LinearGradient start={start} end={end} colors={['#000000', 'rgba(0,0,0,0)']} />
          </Rect>
        }
      >
        <Rect x={0} y={0} width={PHOTO.width} height={PHOTO.height} color={resort.tint} />
        <SkiaImage image={image} x={0} y={0} width={PHOTO.width} height={PHOTO.height} fit="cover" opacity={loaded} />
      </Mask>
    </Group>
  );
}

/**
 * Light snow over the photo: the same flakes on the same paths every LOOP seconds; the colder
 * the resort, the more of them are let in.
 */
function SnowLayer({ layer, temperatures, time, from, to, changedAt, reduced }: { layer: number; temperatures: number[] } & Motion) {
  const path = useDerivedValue(() => {
    const snow = Skia.Path.Make();
    const shown = clamp01(sinceChange(time.get(), changedAt.get(), reduced) / CHANGE_SECONDS);
    const before = flakeCount(temperatures[from.get()]);
    const count = before + (flakeCount(temperatures[to.get()]) - before) * shown;
    const turn = (time.get() % LOOP) / LOOP;
    const above = layer === 0 ? 0 : SNOW_LAYERS[layer - 1].below;
    for (let i = 0; i < SNOW.length; i++) {
      const flake = SNOW[i];
      const grown = clamp01(count - i);
      if (grown <= 0 || flake.radius < above || flake.radius >= SNOW_LAYERS[layer].below) continue;
      const fall = (flake.start + flake.falls * turn) % 1;
      const x = flake.x * PHOTO.width + flake.sway * Math.sin(TAU * flake.swings * turn + flake.phase);
      snow.addCircle(x, -6 + fall * (PHOTO.height + 12), flake.radius * grown);
    }
    return snow;
  });
  return <SkiaPath path={path} color="#ffffff" opacity={SNOW_LAYERS[layer].opacity} />;
}

/** Three slots for the sign and the digits, then a degree sign and a C, each drawn from seven segments. */
function Display({ temperatures, time, from, to, changedAt, reduced }: { temperatures: number[] } & Motion) {
  const lit = useDerivedValue(() => {
    const path = Skia.Path.Make();
    const degrees = degreesOf(temperatures[from.get()], temperatures[to.get()], sinceChange(time.get(), changedAt.get(), reduced));
    const size = Math.abs(degrees);
    const tens = size >= 10 ? DIGITS[Math.floor(size / 10) % 10] : degrees < 0 ? MINUS : 0;
    addSegments(path, 0, GLYPH.width, size >= 10 && degrees < 0 ? MINUS : 0);
    addSegments(path, GLYPH_PITCH, GLYPH.width, tens);
    addSegments(path, GLYPH_PITCH * 2, GLYPH.width, DIGITS[size % 10]);
    addSegments(path, DEGREE_X, DEGREE_WIDTH, DEGREE);
    addSegments(path, UNIT_X, GLYPH.width, UNIT);
    return path;
  });
  return (
    <Canvas style={styles.display}>
      <SkiaPath path={GHOST} style="stroke" strokeWidth={SEGMENT} strokeCap="round" color="rgba(255,255,255,0.1)" />
      <SkiaPath path={lit} style="stroke" strokeWidth={SEGMENT} strokeCap="round" color="#ffffff" />
    </Canvas>
  );
}

/**
 * One line of text per resort in the same place: the leaving one rolls up and out while the
 * arriving one rolls in from below, `at` seconds after the change.
 */
function Roll({
  texts,
  at,
  height,
  style,
  time,
  from,
  to,
  changedAt,
  reduced,
}: { texts: string[]; at: number; height: number; style: TextStyle } & Motion) {
  return (
    <>
      {texts.map((text, index) => (
        <RollLine key={index} text={text} index={index} at={at} height={height} style={style} time={time} from={from} to={to} changedAt={changedAt} reduced={reduced} />
      ))}
    </>
  );
}

function RollLine({
  text,
  index,
  at,
  height,
  style,
  time,
  from,
  to,
  changedAt,
  reduced,
}: { text: string; index: number; at: number; height: number; style: TextStyle } & Motion) {
  const animated = useAnimatedStyle(() => {
    const roll = rollOf(index, from.get(), to.get(), sinceChange(time.get(), changedAt.get(), reduced) - at);
    return { opacity: roll.opacity, transform: [{ translateY: roll.shift * height }] };
  });
  return (
    <Animated.Text style={[style, styles.rollLine, { height, lineHeight: height }, animated]} numberOfLines={1}>
      {text}
    </Animated.Text>
  );
}

/** A thumbnail of the strip: larger and outlined when picked. */
function Thumbnail({ resort, index, sel, onPress }: { resort: ResortWeatherCardResort; index: number; sel: SharedValue<number>; onPress: () => void }) {
  const frame = useAnimatedStyle(() => {
    const weight = Math.max(0, 1 - Math.abs(index - sel.get()));
    const side = THUMB + (THUMB_PICKED - THUMB) * weight;
    return {
      width: side,
      height: side,
      borderRadius: THUMB_RADIUS + (THUMB_PICKED_RADIUS - THUMB_RADIUS) * weight,
      opacity: 0.6 + 0.4 * weight,
    };
  });
  const outline = useAnimatedStyle(() => {
    const weight = Math.max(0, 1 - Math.abs(index - sel.get()));
    return { opacity: weight, borderRadius: THUMB_RADIUS + (THUMB_PICKED_RADIUS - THUMB_RADIUS) * weight };
  });
  return (
    <Pressable onPress={onPress} accessibilityRole="button" accessibilityLabel={resort.name}>
      <Animated.View style={[styles.thumb, { backgroundColor: resort.tint }, frame]}>
        <Image source={{ uri: resort.thumbnail }} style={StyleSheet.absoluteFill} contentFit="cover" transition={200} />
        <Animated.View style={[styles.thumbOutline, outline]} />
      </Animated.View>
    </Pressable>
  );
}

/** A page dot: the current one is a short bar. */
function Dot({ index, sel }: { index: number; sel: SharedValue<number> }) {
  const style = useAnimatedStyle(() => {
    const weight = Math.max(0, 1 - Math.abs(index - sel.get()));
    return { width: 4 + 10 * weight, opacity: 0.3 + 0.4 * weight };
  });
  return <Animated.View style={[styles.dot, style]} />;
}

function Icon({ path, width, height, color, stroke = 1.4 }: { path: string; width: number; height: number; color: string; stroke?: number }) {
  return (
    <Svg width={width} height={height}>
      <Path d={path} stroke={color} strokeWidth={stroke} strokeLinecap="round" strokeLinejoin="round" fill="none" />
    </Svg>
  );
}

/**
 * A ski resort on a light card inside a near-black panel: a strip of thumbnails picks the resort,
 * and the card shows its photo under a little snowfall, the temperature on a seven-segment
 * display, a cluster of fans, the name and three facts. Picking another resort wipes its photo in
 * from the top, ticks the display through the degrees in between and rolls the texts.
 */
export function ResortWeatherCard({
  resorts = RESORTS,
  fans = FANS,
  autoPlay = false,
  country = 'Austria',
  mapLabel = 'Map',
  temperatureLabel = 'Temperature',
  temperatureNote = 'Currently',
  onSelect,
  onBack,
  onMap,
}: Props) {
  const reduced = useReducedMotion();
  const auto = autoPlay && !reduced;
  const count = resorts.length;

  const time = useSharedValue(0);
  const from = useSharedValue(0);
  const to = useSharedValue(0);
  const changedAt = useSharedValue(-CHANGE_SECONDS);
  // The resort on show, as a number on a spring: thumbnails and page dots size themselves by it.
  const sel = useSharedValue(0);
  const picked = useRef(0);
  const [shown, setShown] = useState(0);
  // Counts touches: a change restarts the autoPlay loop after a pause.
  const [touches, setTouches] = useState(0);

  useFrameCallback((frame) => {
    time.set(time.get() + Math.min(frame.timeSincePreviousFrame ?? 16, 64) / 1000);
  }, !reduced);

  const select = useCallback(
    (index: number) => {
      if (index === picked.current || index < 0 || index >= count) return false;
      from.set(picked.current);
      to.set(index);
      changedAt.set(time.get());
      sel.set(reduced ? index : withSpring(index, PICK_SPRING));
      picked.current = index;
      return true;
    },
    [changedAt, count, from, reduced, sel, time, to],
  );

  useEffect(() => {
    if (!auto || count < 2) return;
    const timers: ReturnType<typeof setTimeout>[] = [];
    const gap = LOOP / count;
    const cycle = () => {
      timers.length = 0;
      for (let i = 1; i <= count; i++) {
        timers.push(
          setTimeout(() => {
            if (select(i % count)) setShown(i % count);
          }, (FIRST + gap * (i - 1)) * 1000),
        );
      }
      timers.push(setTimeout(cycle, LOOP * 1000));
    };
    if (touches === 0) {
      cycle();
    } else {
      // After a touch the loop waits, returns to the first resort and starts over.
      timers.push(
        setTimeout(() => {
          if (select(0)) {
            setShown(0);
            timers.push(setTimeout(cycle, SETTLE * 1000));
          } else {
            cycle();
          }
        }, IDLE * 1000),
      );
    }
    return () => timers.forEach(clearTimeout);
  }, [auto, touches, count, select]);

  if (count === 0) return null;
  const motion: Motion = { time, from, to, changedAt, reduced };
  const temperatures = resorts.map((resort) => resort.temperature);

  const pick = (index: number) => {
    setTouches((n) => n + 1);
    if (!select(index)) return;
    if (process.env.EXPO_OS !== 'web') Haptics.selectionAsync().catch(() => {});
    setShown(index);
    onSelect?.(index);
  };

  return (
    <View style={styles.panel}>
      <View style={styles.header}>
        <Pressable style={styles.back} onPress={() => onBack?.()} accessibilityRole="button" accessibilityLabel={`Back, ${country}`}>
          <Icon path={BACK_ICON} width={20} height={20} color="#ffffff" stroke={1.7} />
          <Text style={styles.country}>{country}</Text>
        </Pressable>
        <Pressable style={styles.map} onPress={() => onMap?.()} accessibilityRole="button" accessibilityLabel={mapLabel}>
          <Icon path={PIN_ICON} width={14} height={16} color="#ffffff" />
          <Text style={styles.mapLabel}>{mapLabel}</Text>
        </Pressable>
      </View>

      {/* The strip: the selected thumbnail is larger and outlined, and its neighbours make room. */}
      <View style={styles.thumbs}>
        {resorts.map((resort, index) => (
          <Thumbnail key={index} resort={resort} index={index} sel={sel} onPress={() => pick(index)} />
        ))}
      </View>

      <View style={styles.card}>
        <View style={styles.photo}>
          <Canvas style={styles.photoCanvas}>
            {resorts.map((resort, index) => (
              <Photo key={index} resort={resort} index={index} {...motion} />
            ))}
            {SNOW_LAYERS.map((_, layer) => (
              <SnowLayer key={layer} layer={layer} temperatures={temperatures} {...motion} />
            ))}
          </Canvas>

          {/* The temperature: a black pill with a seven-segment display and its label. */}
          <View style={styles.pill} accessible accessibilityLabel={`${temperatureLabel} ${resorts[shown].temperature} °C`}>
            <Display temperatures={temperatures} {...motion} />
            <View>
              <Text style={styles.temperatureLabel} numberOfLines={1}>
                {temperatureLabel}
              </Text>
              <Text style={styles.temperatureNote} numberOfLines={1}>
                {temperatureNote}
              </Text>
            </View>
          </View>

          {fans.slice(0, FAN_SPOTS.length).map((fan, index) => {
            const spot = FAN_SPOTS[index];
            return (
              <View key={index} style={[styles.fan, { left: spot.x, top: spot.y, width: spot.size, height: spot.size }]}>
                <View style={[styles.fanDisc, { borderRadius: spot.size / 2, backgroundColor: fan.color }]}>
                  <Text style={[styles.initials, { fontSize: spot.size * 0.34 }]}>{fan.initials}</Text>
                </View>
                <View style={styles.fanHeart}>
                  <Svg width={12} height={12}>
                    <Path d={HEART_ICON} fill="#f08a3c" />
                  </Svg>
                </View>
              </View>
            );
          })}

          <View style={styles.likes}>
            <Icon path={FLAKE_ICON} width={14} height={14} color="#ffffff" />
            <View style={styles.likesRoll}>
              <Roll texts={resorts.map((resort) => resort.likes)} at={NAME_AT} height={18} style={styles.likesLabel} {...motion} />
            </View>
          </View>
        </View>

        <View style={styles.dots}>
          {resorts.map((_, index) => (
            <Dot key={index} index={index} sel={sel} />
          ))}
        </View>

        <View style={[styles.line, { top: NAME_Y - 15, height: 30 }]}>
          <Roll texts={resorts.map((resort) => resort.name)} at={NAME_AT} height={30} style={styles.name} {...motion} />
        </View>
        <View style={[styles.line, { top: REGION_Y - 10, height: 20 }]}>
          <Roll texts={resorts.map((resort) => resort.region)} at={REGION_AT} height={20} style={styles.region} {...motion} />
        </View>

        <View style={styles.facts}>
          <View style={styles.fact}>
            <Icon path={RUNS_ICON} width={18} height={18} color={FACT} />
            <View style={[styles.factRoll, { width: 78 }]}>
              <Roll texts={resorts.map((resort) => resort.runs)} at={FACTS_AT} height={18} style={styles.factLabel} {...motion} />
            </View>
          </View>
          <View style={styles.fact}>
            <Icon path={DISTANCE_ICON} width={18} height={18} color={FACT} />
            <View style={[styles.factRoll, { width: 56 }]}>
              <Roll texts={resorts.map((resort) => resort.distance)} at={FACTS_AT + FACTS_GAP} height={18} style={styles.factLabel} {...motion} />
            </View>
          </View>
          <View style={styles.fact}>
            <Icon path={LIFTS_ICON} width={18} height={18} color={FACT} />
            <View style={[styles.factRoll, { width: 56 }]}>
              <Roll texts={resorts.map((resort) => resort.lifts)} at={FACTS_AT + FACTS_GAP * 2} height={18} style={styles.factLabel} {...motion} />
            </View>
          </View>
        </View>
      </View>
    </View>
  );
}

function photoUrl(id: string, width: number) {
  return `https://images.unsplash.com/photo-${id}?auto=format&fit=crop&w=${width}&q=80`;
}

const FANS: ResortWeatherCardFan[] = [
  { initials: 'LM', color: '#6f8fd8' },
  { initials: 'AK', color: '#d99a4e' },
  { initials: 'JT', color: '#58a98c' },
];

const RESORTS: ResortWeatherCardResort[] = [
  {
    name: 'Obertauern Ski Resort',
    region: 'Austria, Salzburg',
    temperature: -8,
    likes: '198 frosties',
    runs: '26 ski runs',
    distance: '112 km',
    lifts: '8 types',
    photo: photoUrl('1454496522488-7a8e488e8606', 800),
    thumbnail: photoUrl('1454496522488-7a8e488e8606', 160),
    tint: '#8ea3bf',
  },
  {
    name: 'St. Anton Ski Resort',
    region: 'Austria, Tyrol',
    temperature: -14,
    likes: '342 frosties',
    runs: '88 ski runs',
    distance: '305 km',
    lifts: '9 types',
    photo: photoUrl('1519681393784-d120267933ba', 800),
    thumbnail: photoUrl('1519681393784-d120267933ba', 160),
    tint: '#3a4a78',
  },
  {
    name: 'Kitzsteinhorn Glacier',
    region: 'Austria, Salzburg',
    temperature: 2,
    likes: '87 frosties',
    runs: '17 ski runs',
    distance: '61 km',
    lifts: '5 types',
    photo: photoUrl('1506905925346-21bda4d32df4', 800),
    thumbnail: photoUrl('1506905925346-21bda4d32df4', 160),
    tint: '#7c8fb0',
  },
  {
    name: 'Hintertux Ski Resort',
    region: 'Austria, Tyrol',
    temperature: -11,
    likes: '256 frosties',
    runs: '34 ski runs',
    distance: '196 km',
    lifts: '7 types',
    photo: photoUrl('1531366936337-7c912a4589a7', 800),
    thumbnail: photoUrl('1531366936337-7c912a4589a7', 160),
    tint: '#2f5a6e',
  },
];

const styles = StyleSheet.create({
  panel: {
    width: WIDTH,
    height: HEIGHT,
    borderRadius: RADIUS,
    backgroundColor: '#0f1114',
    borderWidth: 1,
    borderColor: 'rgba(255,255,255,0.07)',
  },
  header: {
    position: 'absolute',
    left: 19,
    top: HEADER_Y - 16,
    width: WIDTH - 40,
    height: 30,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  back: {
    height: 30,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
  },
  country: {
    color: '#ffffff',
    fontSize: 20,
    lineHeight: 24,
    fontWeight: '500',
  },
  map: {
    width: 74,
    height: 30,
    borderRadius: 15,
    backgroundColor: 'rgba(255,255,255,0.1)',
    borderWidth: 1,
    borderColor: 'rgba(255,255,255,0.28)',
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 6,
  },
  mapLabel: {
    color: '#ffffff',
    fontSize: 13,
    lineHeight: 16,
    fontWeight: '500',
  },
  thumbs: {
    position: 'absolute',
    left: -1,
    top: THUMB_Y - THUMB_PICKED / 2 - 1,
    width: WIDTH,
    height: THUMB_PICKED,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: THUMB_GAP,
  },
  thumb: {
    overflow: 'hidden',
  },
  thumbOutline: {
    position: 'absolute',
    left: 0,
    top: 0,
    right: 0,
    bottom: 0,
    borderWidth: 2,
    borderColor: 'rgba(255,255,255,0.5)',
  },
  card: {
    position: 'absolute',
    left: (WIDTH - CARD.width) / 2 - 1,
    top: CARD_TOP - 1,
    width: CARD.width,
    height: CARD.height,
    borderRadius: CARD_RADIUS,
    backgroundColor: '#e9e9e9',
  },
  photo: {
    position: 'absolute',
    left: PHOTO.x,
    top: PHOTO.y,
    width: PHOTO.width,
    height: PHOTO.height,
    borderRadius: PHOTO_RADIUS,
    overflow: 'hidden',
  },
  photoCanvas: {
    position: 'absolute',
    left: 0,
    top: 0,
    width: PHOTO.width,
    height: PHOTO.height,
  },
  pill: {
    position: 'absolute',
    left: (PHOTO.width - PILL.width) / 2,
    top: 12,
    width: PILL.width,
    height: PILL.height,
    borderRadius: 14,
    backgroundColor: '#0b0d10',
    flexDirection: 'row',
    alignItems: 'center',
    paddingLeft: 16,
    gap: 10,
  },
  display: {
    width: DISPLAY.width,
    height: DISPLAY.height,
  },
  temperatureLabel: {
    color: '#ffffff',
    fontSize: 10,
    lineHeight: 13,
    fontWeight: '600',
  },
  temperatureNote: {
    color: '#8d9096',
    fontSize: 10,
    lineHeight: 13,
    fontWeight: '400',
  },
  fan: {
    position: 'absolute',
  },
  fanDisc: {
    flex: 1,
    borderWidth: 2,
    borderColor: 'rgba(255,255,255,0.9)',
    alignItems: 'center',
    justifyContent: 'center',
  },
  initials: {
    color: '#ffffff',
    fontWeight: '700',
  },
  fanHeart: {
    position: 'absolute',
    right: -3,
    bottom: -2,
    transform: [{ scale: 1.2 }],
  },
  likes: {
    position: 'absolute',
    left: PHOTO.width - 12 - LIKES.width,
    top: PHOTO.height - 12 - LIKES.height,
    width: LIKES.width,
    height: LIKES.height,
    borderRadius: LIKES.height / 2,
    backgroundColor: 'rgba(28,32,36,0.5)',
    borderWidth: 1,
    borderColor: 'rgba(255,255,255,0.3)',
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 6,
  },
  likesRoll: {
    width: 78,
    height: 18,
    overflow: 'hidden',
  },
  likesLabel: {
    color: '#ffffff',
    fontSize: 13,
    fontWeight: '500',
    fontVariant: ['tabular-nums'],
  },
  rollLine: {
    position: 'absolute',
    left: 0,
    top: 0,
    right: 0,
  },
  dots: {
    position: 'absolute',
    left: 0,
    top: DOTS_Y - 2,
    width: CARD.width,
    height: 4,
    flexDirection: 'row',
    justifyContent: 'center',
    gap: 4,
  },
  dot: {
    height: 4,
    borderRadius: 2,
    backgroundColor: '#1d1f22',
  },
  line: {
    position: 'absolute',
    left: 0,
    width: CARD.width,
    overflow: 'hidden',
  },
  name: {
    textAlign: 'center',
    color: '#1d1f22',
    fontSize: 23,
    fontWeight: '600',
    letterSpacing: -0.3,
  },
  region: {
    textAlign: 'center',
    color: '#86898d',
    fontSize: 14,
    fontWeight: '400',
  },
  facts: {
    position: 'absolute',
    left: FACTS.x,
    top: FACTS.y,
    width: FACTS.width,
    height: FACTS.height,
    borderRadius: FACTS.height / 2,
    backgroundColor: '#d8d8d8',
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: 16,
  },
  fact: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 5,
  },
  factRoll: {
    height: 18,
    overflow: 'hidden',
  },
  factLabel: {
    color: FACT,
    fontSize: 12.5,
    fontWeight: '500',
    fontVariant: ['tabular-nums'],
  },
});
