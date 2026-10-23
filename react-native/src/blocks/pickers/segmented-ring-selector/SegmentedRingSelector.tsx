import MaterialCommunityIcons from '@expo/vector-icons/MaterialCommunityIcons';
import { Canvas, Circle, LinearGradient, Path, RadialGradient, Skia, vec } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { useEffect, useRef, useState } from 'react';
import { StyleSheet, Text, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  cancelAnimation,
  interpolateColor,
  ReduceMotion,
  type SharedValue,
  useAnimatedReaction,
  useAnimatedStyle,
  useDerivedValue,
  useReducedMotion,
  useSharedValue,
  withSpring,
} from 'react-native-reanimated';
import Svg, { Circle as SvgCircle, Path as SvgPath } from 'react-native-svg';
import { scheduleOnRN } from 'react-native-worklets';

/** The picture an item shows on the menu ring while it is not selected. */
type Glyph = 'paint' | 'wheel' | 'steering' | 'lock' | 'battery' | 'gauge' | 'car' | 'music' | 'reset';

type Option = {
  name: string;
  /** The small line under the name. */
  tier: string;
  /** The option's block on the outer ring. */
  swatch: string;
  /** The colour the wedge and the small line take while the option is chosen. */
  accent: string;
};

type Item = {
  label: string;
  glyph: Glyph;
  options: Option[];
};

type Props = {
  items?: Item[];
  /** Index of the item under the index at first, and of its chosen option. */
  initialItem?: number;
  initialOption?: number;
  /** Steps the two rings by itself, in a loop. */
  autoPlay?: boolean;
  /** Called with the item's index and its option's index when either ring is sent to a new place. */
  onSelect?: (item: number, option: number) => void;
};

const SIZE = 340;
const CENTER = SIZE / 2;

// Radii, from the disc outwards.
const DISC_RADIUS = 92;
const RING_OUTER = 134;
const ITEM_RADIUS = 113;
const BAND_INNER = 142;
const BAND_OUTER = 155;
const LIT_OUTER = 168;

// Degrees.
const ITEM_STEP = 40;
const WEDGE_HALF = 22;
const BLOCK_STEP = 12;
const BLOCK_HALF = 5.4;
const LIT_HALF = 6.6;
// The band is whole down to `FADE_START` from 12 o'clock and gone by `FADE_END`.
const FADE_START = 60;
const FADE_END = 150;
const BLOCKS_ASIDE = 13;
// How much later each block further from the index opens, as a share of the fan.
const FAN_STAGGER = 0.03;
const TAB_HALF = 3;
const TAB_RISE = 8;
const NAME_SLIDE = 6;

// Snapping: a spring with a response of 0.4 s and a damping ratio of 0.75.
const SNAP = { mass: 1, stiffness: 246.74, damping: 23.56, reduceMotion: ReduceMotion.Never };
// Seconds of the finger's speed a ring coasts for.
const COAST = 0.2;
// Points a finger may move before a tap becomes a drag.
const TAP_SLOP = 4;

// autoPlay (seconds): when each move starts, which ring it turns and by how many places.
const LOOP = 8;
const SCRIPT = [
  { at: 1.0, menu: false, by: 1 },
  { at: 1.45, menu: false, by: 1 },
  { at: 2.7, menu: true, by: 1 },
  { at: 4.1, menu: false, by: 1 },
  { at: 5.2, menu: true, by: -1 },
  { at: 5.75, menu: false, by: -2 },
];
// After a touch the loop waits this long, then takes `HOME_TIME` for each ring to return.
const RESUME_AFTER = 2;
const HOME_TIME = 0.7;

const SLATE = '#3a4046';
const RING = '#d6d6d6';
const BAND = '#9aaeb0';

const ITEMS: Item[] = [
  {
    label: 'Paint',
    glyph: 'paint',
    options: [
      { name: 'Solar Orange', tier: 'SIGNATURE', swatch: '#ffa400', accent: '#f5470a' },
      { name: 'Glacier Blue', tier: 'SIGNATURE', swatch: '#5bc0f5', accent: '#1e7fd6' },
      { name: 'Moss Green', tier: 'HERITAGE', swatch: '#8cc152', accent: '#4e8f2e' },
      { name: 'Graphite', tier: 'CLASSIC', swatch: '#7a828c', accent: '#4a525c' },
    ],
  },
  {
    label: 'Wheels',
    glyph: 'wheel',
    options: [
      { name: 'Ten Spoke', tier: 'FORGED', swatch: '#d9a441', accent: '#b8791f' },
      { name: 'Turbine', tier: 'FORGED', swatch: '#c9ced3', accent: '#6c7a89' },
      { name: 'Mesh', tier: 'CAST', swatch: '#5a6068', accent: '#2f3338' },
    ],
  },
  {
    label: 'Steering',
    glyph: 'steering',
    options: [
      { name: 'Light', tier: 'COMFORT', swatch: '#7ed6c8', accent: '#1fa391' },
      { name: 'Balanced', tier: 'COMFORT', swatch: '#5bc0f5', accent: '#1e7fd6' },
      { name: 'Heavy', tier: 'SPORT', swatch: '#f58a6b', accent: '#e0362c' },
    ],
  },
  {
    label: 'Locks',
    glyph: 'lock',
    options: [
      { name: 'Auto Lock', tier: 'SECURE', swatch: '#8cc152', accent: '#4e8f2e' },
      { name: 'Walk Away', tier: 'SECURE', swatch: '#ffc44d', accent: '#d68a00' },
      { name: 'Valet', tier: 'LIMITED', swatch: '#f58a6b', accent: '#e0362c' },
    ],
  },
  {
    label: 'Charge',
    glyph: 'battery',
    options: [
      { name: 'Daily 80%', tier: 'BATTERY CARE', swatch: '#8cc152', accent: '#4e8f2e' },
      { name: 'Long Trip', tier: 'FULL CHARGE', swatch: '#5bc0f5', accent: '#1e7fd6' },
      { name: 'Off Peak', tier: 'SCHEDULED', swatch: '#b79cf2', accent: '#7a4fd6' },
    ],
  },
  {
    label: 'Drive',
    glyph: 'gauge',
    options: [
      { name: 'Comfort', tier: 'ROAD', swatch: '#5bc0f5', accent: '#1e7fd6' },
      { name: 'Sport', tier: 'ROAD', swatch: '#ffa400', accent: '#f5470a' },
      { name: 'Track', tier: 'CIRCUIT', swatch: '#f5606b', accent: '#d61e3c' },
    ],
  },
  {
    label: 'Body',
    glyph: 'car',
    options: [
      { name: 'Coupe', tier: 'FIXED ROOF', swatch: '#c9ced3', accent: '#6c7a89' },
      { name: 'Spider', tier: 'OPEN ROOF', swatch: '#ffc44d', accent: '#d68a00' },
    ],
  },
  {
    label: 'Sound',
    glyph: 'music',
    options: [
      { name: 'Studio', tier: 'FLAT', swatch: '#b79cf2', accent: '#7a4fd6' },
      { name: 'Concert', tier: 'WIDE', swatch: '#f59ac4', accent: '#d63a8a' },
      { name: 'Lounge', tier: 'WARM', swatch: '#ffc44d', accent: '#d68a00' },
    ],
  },
  {
    label: 'Reset',
    glyph: 'reset',
    options: [
      { name: 'Keep Setup', tier: 'NO CHANGE', swatch: '#c9ced3', accent: '#6c7a89' },
      { name: 'Factory', tier: 'ERASE ALL', swatch: '#f5606b', accent: '#d61e3c' },
    ],
  },
];

const SYMBOLS = {
  paint: 'palette',
  lock: 'lock',
  battery: 'battery-charging',
  gauge: 'speedometer',
  car: 'car-side',
  music: 'music-note',
  reset: 'rotate-left',
} as const;

function clamp01(t: number) {
  'worklet';
  return Math.min(1, Math.max(0, t));
}

function mix(a: number, b: number, t: number) {
  'worklet';
  return a + (b - a) * t;
}

function smooth(t: number) {
  'worklet';
  return t * t * (3 - 2 * t);
}

/** `index` brought into 0..<count, for a ring that goes round. */
function wrap(index: number, count: number) {
  'worklet';
  return ((index % count) + count) % count;
}

/** `value` brought into −span/2…span/2: the short way round. */
function wrapped(value: number, span: number) {
  'worklet';
  return value - span * Math.floor((value + span / 2) / span);
}

/** A point `radius` from the centre at `angle` degrees, 0 at 12 o'clock and growing clockwise. */
function pointAt(angle: number, radius: number) {
  'worklet';
  const turn = (angle * Math.PI) / 180;
  return { x: CENTER + radius * Math.sin(turn), y: CENTER - radius * Math.cos(turn) };
}

/** A point's angle around the centre in degrees: 0 at 12 o'clock, growing clockwise, −180…180. */
function bearing(x: number, y: number) {
  'worklet';
  return (Math.atan2(x - CENTER, CENTER - y) * 180) / Math.PI;
}

/** The piece of a ring between two angles and two radii. */
function sector(from: number, to: number, inner: number, outer: number) {
  'worklet';
  const steps = Math.max(2, Math.ceil((to - from) / 2));
  const path = Skia.Path.Make();
  for (let n = 0; n <= steps; n++) {
    const at = pointAt(mix(from, to, n / steps), outer);
    if (n === 0) path.moveTo(at.x, at.y);
    else path.lineTo(at.x, at.y);
  }
  for (let n = 0; n <= steps; n++) {
    const at = pointAt(mix(to, from, n / steps), inner);
    path.lineTo(at.x, at.y);
  }
  path.close();
  return path;
}

/** The colour a tenth darker, for the foot of a block. */
function shade(hex: string) {
  'worklet';
  const part = (at: number) =>
    Math.floor(parseInt(hex.slice(at, at + 2), 16) * 0.9)
      .toString(16)
      .padStart(2, '0');
  return `#${part(1)}${part(3)}${part(5)}`;
}

type Motion = {
  /**
   * The menu ring's position as an item index, and the outer ring's as an option index. Both keep
   * counting past the last one: the rings go round.
   */
  menu: SharedValue<number>;
  value: SharedValue<number>;
  /** The item under the index, and each item's chosen place on its outer ring. */
  item: SharedValue<number>;
  chosen: SharedValue<number[]>;
};

/** The item on the other side of the hand-over the menu ring is nearest to, and its chosen option. */
function otherOption(items: Item[], item: number, chosen: number[], off: number) {
  'worklet';
  const next = wrap(item + (off < 0 ? -1 : 1), items.length);
  return items[next].options[wrap(chosen[next] ?? 0, items[next].options.length)];
}

/** One block of the outer ring. The block under the index stands taller, a little wider, in its own colour. */
function Block({ slot, items, menu, value, item, chosen }: Motion & { slot: number; items: Item[] }) {
  const state = useDerivedValue(() => {
    const at = value.get();
    const j = Math.floor(at) + slot;
    const d = j - at;
    const lit = clamp01(1.5 - 2 * Math.abs(d));
    const off = menu.get() - Math.round(menu.get());
    // The blocks fan shut towards 12 as the menu leaves an item, and open again as it reaches the next; those nearer the index open first.
    const fan = smooth(clamp01(1 - 2.5 * Math.abs(off)));
    const open = clamp01((fan - FAN_STAGGER * Math.abs(d)) / (1 - FAN_STAGGER * BLOCKS_ASIDE));
    const angle = d * BLOCK_STEP * (lit >= 1 ? 1 : open);
    const fade = clamp01((FADE_END - Math.abs(d) * BLOCK_STEP) / (FADE_END - FADE_START)) * Math.max(open, lit);
    return { j, lit, off, angle, fade };
  });
  const path = useDerivedValue(() => {
    const { lit, angle } = state.get();
    const half = mix(BLOCK_HALF, LIT_HALF, lit);
    return sector(angle - half, angle + half, BAND_INNER, mix(BAND_OUTER, LIT_OUTER, lit));
  });
  const grey = useDerivedValue(() => {
    const { j, lit, fade } = state.get();
    return (Math.abs(j) % 2 === 0 ? 0.7 : 0.45) * fade * (1 - lit);
  });
  const lit = useDerivedValue(() => state.get().lit);
  const colors = useDerivedValue(() => {
    const { j, off } = state.get();
    const options = items[item.get()].options;
    const own = options[wrap(j, options.length)].swatch;
    const other = otherOption(items, item.get(), chosen.get(), off).swatch;
    return [interpolateColor(Math.abs(off), [0, 1], [own, other]), interpolateColor(Math.abs(off), [0, 1], [shade(own), shade(other)])];
  });
  const top = useDerivedValue(() => {
    const { lit: grown, angle } = state.get();
    const at = pointAt(angle, mix(BAND_OUTER, LIT_OUTER, grown));
    return vec(at.x, at.y);
  });
  const foot = useDerivedValue(() => {
    const at = pointAt(state.get().angle, BAND_INNER);
    return vec(at.x, at.y);
  });
  return (
    <>
      <Path path={path} color={BAND} opacity={grey} />
      <Path path={path} opacity={lit}>
        <LinearGradient start={top} end={foot} colors={colors} />
      </Path>
    </>
  );
}

/** The two glyphs no icon set shares: a spoked wheel and a steering wheel. */
function DrawnGlyph({ glyph }: { glyph: 'wheel' | 'steering' }) {
  if (glyph === 'wheel') {
    let spokes = '';
    for (let n = 0; n < 8; n++) {
      const turn = (n / 8) * Math.PI * 2;
      spokes += `M0 0L${(11 * Math.sin(turn)).toFixed(2)} ${(-11 * Math.cos(turn)).toFixed(2)}`;
    }
    return (
      <Svg width={30} height={30} viewBox="-15 -15 30 30">
        <SvgCircle cx={0} cy={0} r={11} stroke={SLATE} strokeWidth={2.6} fill="none" />
        <SvgPath d={spokes} stroke={SLATE} strokeWidth={1.3} />
        <SvgCircle cx={0} cy={0} r={2.8} fill={SLATE} />
      </Svg>
    );
  }
  return (
    <Svg width={30} height={30} viewBox="-15 -15 30 30">
      <SvgCircle cx={0} cy={0} r={11} stroke={SLATE} strokeWidth={2.8} fill="none" />
      <SvgPath d="M-11 -1L11 -1M0 0L0 11" stroke={SLATE} strokeWidth={2.6} />
      <SvgCircle cx={0} cy={-1} r={4} fill={SLATE} />
    </Svg>
  );
}

/** An item on the menu ring. Under the index its glyph gives way to its name in white. */
function ItemView({ index, item, count, menu }: { index: number; item: Item; count: number; menu: SharedValue<number> }) {
  const place = useAnimatedStyle(() => {
    const at = pointAt(wrapped(index - menu.get(), count) * ITEM_STEP, ITEM_RADIUS);
    return { transform: [{ translateX: at.x - 40 }, { translateY: at.y - 15 }] };
  });
  const glyph = useAnimatedStyle(() => ({ opacity: 1 - clamp01(1 - Math.abs(wrapped(index - menu.get(), count)) * 2.2) }));
  const name = useAnimatedStyle(() => ({ opacity: clamp01(1 - Math.abs(wrapped(index - menu.get(), count)) * 2.2) }));
  return (
    <Animated.View pointerEvents="none" style={[styles.item, place]}>
      <Animated.View style={[styles.glyph, glyph]}>
        {item.glyph === 'wheel' || item.glyph === 'steering' ? (
          <DrawnGlyph glyph={item.glyph} />
        ) : (
          <MaterialCommunityIcons name={SYMBOLS[item.glyph]} size={25} color={SLATE} />
        )}
      </Animated.View>
      <Animated.Text style={[styles.label, name]} numberOfLines={1}>
        {item.label}
      </Animated.Text>
    </Animated.View>
  );
}

/**
 * An option's name and the small line under it, on the disc. Between two options one leaves upwards
 * as the next arrives from below; half-way between two items the disc is empty.
 */
function Name({ index, option, count, tone, menu, value }: { index: number; option: Option; count: number; tone: SharedValue<string>; menu: SharedValue<number>; value: SharedValue<number> }) {
  const style = useAnimatedStyle(() => {
    const d = wrapped(index - value.get(), count);
    const seen = clamp01(1 - 2.5 * Math.abs(menu.get() - Math.round(menu.get())));
    return {
      opacity: clamp01(1 - 2 * Math.abs(d)) * seen,
      transform: [{ translateY: Math.sign(d) * NAME_SLIDE * Math.min(1, 2 * Math.abs(d)) }],
    };
  });
  const tier = useAnimatedStyle(() => ({ color: tone.get() }));
  return (
    <Animated.View pointerEvents="none" style={[styles.names, style]}>
      <Text style={styles.name} numberOfLines={1}>
        {option.name}
      </Text>
      <Animated.Text style={[styles.tier, tier]} numberOfLines={1}>
        {option.tier}
      </Animated.Text>
    </Animated.View>
  );
}

// The wedge with the tab at the middle of its outer edge, and the same shape a little darker towards the disc.
const WEDGE = (() => {
  const path = sector(-WEDGE_HALF, WEDGE_HALF, DISC_RADIUS - 1, RING_OUTER);
  const left = pointAt(-TAB_HALF, RING_OUTER - 0.5);
  const tip = pointAt(0, RING_OUTER + TAB_RISE);
  const right = pointAt(TAB_HALF, RING_OUTER - 0.5);
  path.moveTo(left.x, left.y);
  path.lineTo(tip.x, tip.y);
  path.lineTo(right.x, right.y);
  path.close();
  return path;
})();

const SLOTS = Array.from({ length: BLOCKS_ASIDE * 2 + 2 }, (_, n) => n - BLOCKS_ASIDE);

/**
 * Two concentric wheels around a white disc, with one fixed index at 12 o'clock. The inner, grey ring
 * carries the items as glyphs; the one under the index sits in a coloured wedge with its name in
 * white. The outer ring is a band of small blocks, the options of that item: the block under the
 * index stands taller in its own colour, and the disc names it. Dragging either ring turns it, and it
 * snaps on a spring; when the item changes, the outer blocks fan shut towards 12 and open again.
 */
export function SegmentedRingSelector({ items = ITEMS, initialItem = 0, initialOption = 0, autoPlay = false, onSelect }: Props) {
  const still = useReducedMotion();
  const auto = autoPlay && !still && items.length > 1;
  const home = Math.min(Math.max(initialItem, 0), items.length - 1);

  const menu = useSharedValue(home);
  const value = useSharedValue(initialOption);
  const item = useSharedValue(home);
  const chosen = useSharedValue(items.map((_, index) => (index === home ? initialOption : 0)));
  // 0 no ring held, 1 the menu ring, 2 the outer ring.
  const zone = useSharedValue(0);
  // The finger's angle at the last event, how far it has turned and the ring's position when it went down.
  const angle = useSharedValue(0);
  const turned = useSharedValue(0);
  const dragFrom = useSharedValue(0);
  const downX = useSharedValue(0);
  const downY = useSharedValue(0);
  // The turns come from a finger, so they tick; the script's do not.
  const felt = useSharedValue(false);
  const motion: Motion = { menu, value, item, chosen };

  // The item whose options the disc names.
  const [active, setActive] = useState(home);
  // Counts touches: each one stops the loop and restarts the wait before it resumes.
  const [touches, setTouches] = useState(0);

  const tick = () => {
    if (process.env.EXPO_OS !== 'web') Haptics.selectionAsync().catch(() => {});
  };
  const touch = () => setTouches((count) => count + 1);
  const notify = (index: number, option: number) => onSelect?.(index, option);

  const snap = (ring: SharedValue<number>, to: number, velocity: number) => {
    'worklet';
    if (still) ring.set(to);
    else ring.set(withSpring(to, { ...SNAP, velocity }));
  };

  /** Turns the menu ring until place `position` is under the index. */
  const selectItem = (position: number, velocity: number) => {
    'worklet';
    snap(menu, position, velocity);
    const index = wrap(position, items.length);
    scheduleOnRN(notify, index, wrap(chosen.get()[index] ?? 0, items[index].options.length));
  };

  /** Turns the outer ring until place `position` is under the index, and keeps it as the item's choice. */
  const selectOption = (position: number, velocity: number) => {
    'worklet';
    const list = chosen.get().slice();
    list[item.get()] = position;
    chosen.set(list);
    snap(value, position, velocity);
    scheduleOnRN(notify, item.get(), wrap(position, items[item.get()].options.length));
  };

  // Ticks as items pass the index, and hands the outer ring over to the item that has come under it.
  useAnimatedReaction(
    () => Math.round(menu.get()),
    (now, before) => {
      if (before === null || now === before) return;
      const index = wrap(now, items.length);
      item.set(index);
      cancelAnimation(value);
      value.set(chosen.get()[index] ?? 0);
      scheduleOnRN(setActive, index);
      if (felt.get()) scheduleOnRN(tick);
    },
  );
  useAnimatedReaction(
    () => Math.round(value.get()),
    (now, before) => {
      if (before !== null && now !== before && felt.get()) scheduleOnRN(tick);
    },
  );

  const pan = Gesture.Pan()
    .minDistance(TAP_SLOP)
    .onBegin((event) => {
      const reach = Math.hypot(event.x - CENTER, event.y - CENTER);
      downX.set(event.x);
      downY.set(event.y);
      if (reach <= DISC_RADIUS) {
        zone.set(0);
        return;
      }
      // The finger catches the ring it lands on, wherever that ring is.
      zone.set(reach > RING_OUTER + 3 ? 2 : 1);
      felt.set(true);
      cancelAnimation(zone.get() === 1 ? menu : value);
      scheduleOnRN(touch);
    })
    .onStart((event) => {
      angle.set(bearing(event.x, event.y));
      turned.set(0);
      dragFrom.set(zone.get() === 1 ? menu.get() : value.get());
    })
    .onUpdate((event) => {
      if (zone.get() === 0) return;
      const now = bearing(event.x, event.y);
      turned.set(turned.get() + wrapped(now - angle.get(), 360));
      angle.set(now);
      if (zone.get() === 1) menu.set(dragFrom.get() - turned.get() / ITEM_STEP);
      else value.set(dragFrom.get() - turned.get() / BLOCK_STEP);
    })
    .onFinalize((event, success) => {
      if (zone.get() === 0) return;
      const ring = zone.get();
      zone.set(0);
      const step = ring === 1 ? ITEM_STEP : BLOCK_STEP;
      const position = ring === 1 ? menu.get() : value.get();
      let target = Math.round(position);
      let speed = 0;
      if (success) {
        // Degrees a second around the centre, from the finger's speed across its radius.
        const armX = event.x - CENTER;
        const armY = event.y - CENTER;
        const reach = Math.max(40, Math.hypot(armX, armY));
        const spin = (((armX * event.velocityY - armY * event.velocityX) / (reach * reach)) * 180) / Math.PI;
        speed = -spin / step;
        target = Math.round(position + speed * COAST);
      } else {
        // A tap brings the item or the block under it to the index.
        target = Math.round(position + bearing(downX.get(), downY.get()) / step);
      }
      if (ring === 1) selectItem(target, speed);
      else selectOption(target, speed);
      scheduleOnRN(touch);
    });

  /** One move of the script: the menu ring or the outer ring, `by` places on. */
  const stepBy = (onMenu: boolean, by: number) => {
    'worklet';
    if (onMenu) selectItem(Math.round(menu.get()) + by, 0);
    else selectOption((chosen.get()[item.get()] ?? 0) + by, 0);
  };
  /** Both rings back to the resting choice: first the menu ring, then (with `options`) the outer one. */
  const goHome = (options: boolean) => {
    'worklet';
    if (!options) {
      const at = Math.round(menu.get());
      selectItem(at + Math.round(wrapped(home - at, items.length)), 0);
    } else {
      const at = Math.round(value.get());
      selectOption(at + Math.round(wrapped(initialOption - at, items[home].options.length)), 0);
    }
  };
  /** The items that are out of sight go back to their first option. */
  const forget = () => {
    'worklet';
    chosen.set(chosen.get().map((place, index) => (index === item.get() ? place : 0)));
  };

  // The timers below call whatever these are at the time they fire.
  const steps = useRef({ stepBy, goHome, forget });
  useEffect(() => {
    steps.current = { stepBy, goHome, forget };
  });

  // The script a finger would follow: two options on, the next item, one option on, and back.
  useEffect(() => {
    if (!auto) return;
    let timer: ReturnType<typeof setTimeout>;
    const run = (begin: number, n: number) => {
      const step = SCRIPT[n % SCRIPT.length];
      // Every step is timed from the script's start, so the loop keeps its period.
      const due = begin + (Math.floor(n / SCRIPT.length) * LOOP + step.at) * 1000;
      timer = setTimeout(() => {
        if (n % SCRIPT.length === 0) steps.current.forget();
        steps.current.stepBy(step.menu, step.by);
        run(begin, n + 1);
      }, due - Date.now());
    };
    if (touches === 0) {
      run(Date.now(), 0);
    } else {
      // A touch took over: wait, turn both rings back to the resting choice, and start over from there.
      timer = setTimeout(() => {
        if (zone.get() !== 0) return;
        felt.set(false);
        steps.current.goHome(false);
        timer = setTimeout(() => {
          steps.current.goHome(true);
          run(Date.now() + HOME_TIME * 1000, 0);
        }, HOME_TIME * 1000);
      }, RESUME_AFTER * 1000);
    }
    return () => clearTimeout(timer);
  }, [auto, touches, zone, felt]);

  // The wedge's colour: the chosen option's, cross-fading with the next option's as the outer ring
  // turns and with the next item's as the menu ring does.
  const tone = useDerivedValue(() => {
    const options = items[item.get()].options;
    const k = Math.floor(value.get());
    const off = menu.get() - Math.round(menu.get());
    const own = interpolateColor(smooth(value.get() - k), [0, 1], [options[wrap(k, options.length)].accent, options[wrap(k + 1, options.length)].accent]);
    return interpolateColor(Math.abs(off), [0, 1], [own, otherOption(items, item.get(), chosen.get(), off).accent]);
  });

  const options = items[active].options;

  return (
    <GestureDetector gesture={pan}>
      <View style={styles.root}>
        <Canvas style={StyleSheet.absoluteFill} pointerEvents="none">
          {SLOTS.map((slot) => (
            <Block key={slot} slot={slot} items={items} {...motion} />
          ))}
          <Circle cx={CENTER} cy={CENTER} r={(DISC_RADIUS + RING_OUTER) / 2} style="stroke" strokeWidth={RING_OUTER - DISC_RADIUS} color={RING} />
          <Path path={WEDGE} color={tone} />
          <Path path={WEDGE}>
            <LinearGradient start={vec(CENTER, CENTER - RING_OUTER)} end={vec(CENTER, CENTER - DISC_RADIUS)} colors={['rgba(0,0,0,0)', 'rgba(0,0,0,0.08)']} />
          </Path>
          <Circle cx={CENTER} cy={CENTER + 6} r={DISC_RADIUS + 24}>
            <RadialGradient
              c={vec(CENTER, CENTER + 6)}
              r={DISC_RADIUS + 24}
              colors={['rgba(0,0,0,0.2)', 'rgba(0,0,0,0.2)', 'rgba(0,0,0,0)']}
              positions={[0, 0.72, 1]}
            />
          </Circle>
          <Circle cx={CENTER} cy={CENTER} r={DISC_RADIUS} color="#ffffff" />
        </Canvas>

        {items.map((entry, index) => (
          <ItemView key={index} index={index} item={entry} count={items.length} menu={menu} />
        ))}
        {options.map((option, index) => (
          <Name key={`${active}-${index}`} index={index} option={option} count={options.length} tone={tone} menu={menu} value={value} />
        ))}
      </View>
    </GestureDetector>
  );
}

const styles = StyleSheet.create({
  root: {
    width: SIZE,
    height: SIZE,
  },
  item: {
    position: 'absolute',
    left: 0,
    top: 0,
    width: 80,
    height: 30,
    alignItems: 'center',
    justifyContent: 'center',
  },
  glyph: {
    position: 'absolute',
    width: 30,
    height: 30,
    alignItems: 'center',
    justifyContent: 'center',
  },
  label: {
    color: '#ffffff',
    fontSize: 14,
    fontWeight: '700',
  },
  names: {
    position: 'absolute',
    left: CENTER - DISC_RADIUS,
    top: CENTER + 2 - 26,
    width: DISC_RADIUS * 2,
    height: 52,
    alignItems: 'center',
    justifyContent: 'center',
    gap: 5,
  },
  name: {
    color: SLATE,
    fontSize: 22,
    fontWeight: '300',
    letterSpacing: 0.4,
  },
  tier: {
    fontSize: 12,
    fontWeight: '700',
    letterSpacing: 0.8,
  },
});
