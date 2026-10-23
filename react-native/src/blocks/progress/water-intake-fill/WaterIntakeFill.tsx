import { BlurMask, Canvas, Group, LinearGradient, Path, Rect, RoundedRect, Skia, vec } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { useEffect, useRef, useState } from 'react';
import { type GestureResponderEvent, Pressable, StyleSheet, Text, View } from 'react-native';
import Animated, {
  ReduceMotion,
  type SharedValue,
  cubicBezier,
  useAnimatedReaction,
  useAnimatedStyle,
  useDerivedValue,
  useFrameCallback,
  useReducedMotion,
  useSharedValue,
  withTiming,
} from 'react-native-reanimated';
import { scheduleOnRN } from 'react-native-worklets';

type Entry = { label: string; time: string };

type Props = {
  /** Controlled share of the daily goal, 0…1. */
  progress?: number;
  /** Pours the day's drinks in one after another up to `amount`, holds, drains and starts over. */
  autoPlay?: boolean;
  title?: string;
  /** Millilitres drunk at the resting frame. */
  amount?: number;
  /** The daily goal in millilitres. */
  goal?: number;
  unit?: string;
  /** The two quick-add chips, in millilitres. */
  quickAmounts?: [number, number];
  customLabel?: string;
  /** What a tap on the custom pill adds. */
  customAmount?: number;
  logTitle?: string;
  /** The latest log rows, newest first. */
  entries?: Entry[];
  /** Called with the amount added and the new total when a chip is tapped or the level is dragged. */
  onAdd?: (added: number, total: number) => void;
  onCustom?: () => void;
};

const WIDTH = 320;
const HEIGHT = 404;
const RADIUS = 34;
// The canvas overflows the card by this much on every side to fit its shadow.
const BLEED = 36;

// Where the water's surface sits when the tank is empty and when the goal is reached.
const Y_EMPTY = 292;
const Y_FULL = 112;
// Top of the lavender "Today" panel.
const PANEL_Y = 304;
// The percentage rides just under the surface, but never sinks below the chips.
const PCT_LEFT = 20;
const PCT_MAX_TOP = 190;
const PCT_X = 74;
// Vertical drag distance that spans the whole goal, and the step the drag snaps to.
const DRAG_SPAN = Y_EMPTY - Y_FULL;
const DRAG_STEP = 50;
const DRAG_SLOP = 6;

// autoPlay loop (seconds): three drinks, a long look at the result, then the tank drains.
const POUR_AT = [0.6, 2.0, 3.4];
const POUR_SHARE = [0.385, 0.615, 1];
const DRAIN_AT = 7.6;
const LOOP = 8.8;
// After a touch the loop waits this long before it drains and starts over.
const USER_HOLD = 6;

// The level follows its target like a critically damped spring.
const OMEGA = 6;

const CSS_EASE_OUT = cubicBezier(0.23, 1, 0.32, 1);

const DROPS = [
  { x: 0.16, vx: -22, vy: 120, r: 2.6 },
  { x: 0.27, vx: 14, vy: 165, r: 2.1 },
  { x: 0.38, vx: -8, vy: 205, r: 3.0 },
  { x: 0.49, vx: 20, vy: 150, r: 1.8 },
  { x: 0.58, vx: -16, vy: 190, r: 2.4 },
  { x: 0.69, vx: 6, vy: 135, r: 2.0 },
  { x: 0.8, vx: 24, vy: 175, r: 2.8 },
  { x: 0.9, vx: -12, vy: 110, r: 1.7 },
];
const DROP_LIFE = 0.85;
const GRAVITY = 520;

const BUBBLES = [
  { x: 0.08, speed: 22, r: 2.2, phase: 10 },
  { x: 0.2, speed: 31, r: 1.4, phase: 120 },
  { x: 0.34, speed: 18, r: 2.8, phase: 60 },
  { x: 0.46, speed: 27, r: 1.6, phase: 170 },
  { x: 0.57, speed: 20, r: 2.0, phase: 95 },
  { x: 0.68, speed: 34, r: 1.3, phase: 30 },
  { x: 0.79, speed: 24, r: 2.5, phase: 150 },
  { x: 0.91, speed: 29, r: 1.7, phase: 75 },
];
const BUBBLE_SPAN = 210;

// A tumbler ghosted into the background, as in the reference.
const GLASS = 'M96 304L96 152Q96 130 118 130L202 130Q224 130 224 152L224 304';
const GLASS_RIM = 'M112 130L208 130';

function clamp01(t: number) {
  'worklet';
  return Math.min(1, Math.max(0, t));
}

/** Y of the resting surface for `level` millilitres. */
function baseline(level: number, goal: number) {
  'worklet';
  return Y_EMPTY - (Y_EMPTY - Y_FULL) * clamp01(level / goal);
}

/**
 * Y of the surface at `x`. `energy` (0…1) is how hard the water was just disturbed: it raises the
 * waves and rocks the whole surface from side to side. `layer` offsets the phase of each wave.
 */
function surfaceAt(x: number, base: number, time: number, energy: number, layer: number) {
  'worklet';
  const amp = 1.7 + 6.5 * energy;
  const slope = 0.085 * energy * Math.sin(time * 5.4);
  const dir = layer === 1 ? -1 : 1;
  return (
    base +
    slope * (x - WIDTH / 2) +
    amp * Math.sin(x * 0.026 + time * 2.2 * dir + layer * 1.9) +
    amp * 0.55 * Math.sin(x * 0.047 - time * 3.1 * dir + layer * 0.7)
  );
}

function wave(ox: number, oy: number, base: number, time: number, energy: number, layer: number, lift: number, closed: boolean) {
  'worklet';
  const path = Skia.Path.Make();
  for (let i = 0; i <= 32; i++) {
    const x = (WIDTH * i) / 32;
    const y = oy + surfaceAt(x, base, time, energy, layer) + lift;
    if (i === 0) path.moveTo(ox + x, y);
    else path.lineTo(ox + x, y);
  }
  if (closed) {
    path.lineTo(ox + WIDTH, oy + HEIGHT);
    path.lineTo(ox, oy + HEIGHT);
    path.close();
  }
  return path;
}

/** Millilitres the autoPlay loop wants in the tank at `t` seconds. */
function scriptTarget(t: number, amount: number) {
  'worklet';
  if (t >= DRAIN_AT) return 0;
  let share = 0;
  for (let i = 0; i < POUR_AT.length; i++) if (t >= POUR_AT[i]) share = POUR_SHARE[i];
  return Math.round((amount * share) / 10) * 10;
}

function percentOf(ml: number, goal: number) {
  return Math.min(100, Math.floor((ml * 100 + goal / 2) / goal));
}

function grouped(n: number) {
  return String(n).replace(/\B(?=(\d{3})+(?!\d))/g, ',');
}

type Motion = {
  level: SharedValue<number>;
  time: SharedValue<number>;
  energy: SharedValue<number>;
  splashAt: SharedValue<number>;
  goal: number;
};

/** Card, sky, ghosted tumbler and the body of the water: everything behind the percentage. */
function Back({ level, time, energy, goal }: Motion) {
  const x = BLEED;
  const y = BLEED;
  const card = { rect: { x, y, width: WIDTH, height: HEIGHT }, rx: RADIUS, ry: RADIUS };
  const far = useDerivedValue(() => wave(x, y, baseline(level.get(), goal), time.get(), energy.get(), 1, -5, true));
  const body = useDerivedValue(() => wave(x, y, baseline(level.get(), goal), time.get(), energy.get(), 0, 0, true));

  return (
    <Canvas style={styles.backCanvas}>
      <RoundedRect x={x + 14} y={y + 22} width={WIDTH - 28} height={HEIGHT - 14} r={RADIUS} color="rgba(4,30,48,0.5)">
        <BlurMask blur={16} style="normal" />
      </RoundedRect>
      <Group clip={card}>
        <Rect x={x} y={y} width={WIDTH} height={HEIGHT}>
          <LinearGradient start={vec(x, y)} end={vec(x + WIDTH * 0.25, y + HEIGHT)} colors={['#082c47', '#0f4f6c', '#1b7890']} positions={[0, 0.45, 1]} />
        </Rect>
        {/* Two soft shafts of light falling across the screen. */}
        <Path path={`M${x + 150} ${y}L${x + 236} ${y}L${x + 96} ${y + HEIGHT}L${x + 10} ${y + HEIGHT}Z`}>
          <LinearGradient start={vec(x, y)} end={vec(x, y + HEIGHT)} colors={['rgba(255,255,255,0.07)', 'rgba(255,255,255,0.015)']} />
        </Path>
        <Path path={`M${x + 268} ${y}L${x + 300} ${y}L${x + 160} ${y + HEIGHT}L${x + 128} ${y + HEIGHT}Z`} color="rgba(255,255,255,0.035)" />
        <Group transform={[{ translateX: x }, { translateY: y }]}>
          <Path path={GLASS} style="stroke" strokeWidth={1.5} color="rgba(255,255,255,0.13)" />
          <Path path={GLASS_RIM} style="stroke" strokeWidth={2} strokeCap="round" color="rgba(255,255,255,0.3)" />
        </Group>
        <Path path={far} color="rgba(104,208,226,0.42)" />
        <Path path={body}>
          <LinearGradient start={vec(x, y + Y_FULL)} end={vec(x, y + PANEL_Y)} colors={['#3fb0c9', '#1f89aa']} />
        </Path>
      </Group>
    </Canvas>
  );
}

/** What lies in front of the percentage: a tinted front wave, bubbles, the splash, and the log panel. */
function Front({ level, time, energy, splashAt, goal }: Motion) {
  const near = useDerivedValue(() => wave(0, 0, baseline(level.get(), goal), time.get(), energy.get(), 2, 4, true));
  const crest = useDerivedValue(() => wave(0, 0, baseline(level.get(), goal), time.get(), energy.get(), 0, 0, false));

  const bubbles = useDerivedValue(() => {
    const path = Skia.Path.Make();
    const base = baseline(level.get(), goal);
    const now = time.get();
    for (const b of BUBBLES) {
      const bx = b.x * WIDTH + 4 * Math.sin(now * 1.3 + b.phase);
      const by = PANEL_Y - ((now * b.speed + b.phase) % BUBBLE_SPAN);
      // A bubble exists only under water: it vanishes as it reaches the surface.
      if (by > surfaceAt(bx, base, now, energy.get(), 0) + b.r + 5) path.addCircle(bx, by, b.r);
    }
    return path;
  });

  const drops = useDerivedValue(() => {
    const path = Skia.Path.Make();
    const s = time.get() - splashAt.get();
    if (s <= 0 || s >= DROP_LIFE) return path;
    const base = baseline(level.get(), goal);
    for (const d of DROPS) {
      const dx = d.x * WIDTH + d.vx * s;
      const rise = d.vy * s - 0.5 * GRAVITY * s * s;
      if (rise > 0) path.addCircle(dx, surfaceAt(dx, base, time.get(), energy.get(), 0) - rise, d.r * (1 - 0.5 * (s / DROP_LIFE)));
    }
    return path;
  });

  return (
    <Canvas style={styles.frontCanvas}>
      <Path path={near} color="rgba(74,196,220,0.3)" />
      <Path path={crest} style="stroke" strokeWidth={1.3} color="rgba(226,250,255,0.6)" />
      <Path path={bubbles} style="stroke" strokeWidth={1} color="rgba(255,255,255,0.38)" />
      <Path path={drops} color="rgba(204,244,252,0.92)" />
      <RoundedRect x={0} y={PANEL_Y} width={WIDTH} height={HEIGHT - PANEL_Y + 40} r={30}>
        <LinearGradient start={vec(0, PANEL_Y)} end={vec(WIDTH, HEIGHT)} colors={['#f1e6ef', '#dccfe8']} />
      </RoundedRect>
    </Canvas>
  );
}

function Chip({ label, onPress, accessibilityLabel }: { label: string; onPress: () => void; accessibilityLabel: string }) {
  return (
    <Pressable onPress={onPress} hitSlop={6} accessibilityRole="button" accessibilityLabel={accessibilityLabel}>
      {({ pressed }) => (
        <Animated.View
          style={[
            styles.chip,
            {
              transform: [{ scale: pressed ? 0.94 : 1 }],
              transitionProperty: 'transform',
              transitionDuration: 130,
              transitionTimingFunction: CSS_EASE_OUT,
            },
          ]}
        >
          <View style={styles.chipDisc}>
            <View style={styles.plusH} />
            <View style={styles.plusV} />
          </View>
          <Text style={styles.chipLabel} numberOfLines={1}>
            {label}
          </Text>
        </Animated.View>
      )}
    </Pressable>
  );
}

/**
 * Hydration tracker: a tank that fills with rippling water as the day's drinks are logged. The big
 * percentage rides the surface (and sinks under it once there is room), the millilitres in the
 * heading count along, every drink throws a splash and rocks the water, and bubbles keep rising.
 * Tap a chip or drag up and down on the tank to add water.
 */
export function WaterIntakeFill({
  progress,
  autoPlay = false,
  title = 'Water',
  amount = 650,
  goal = 2000,
  unit = 'ml',
  quickAmounts = [150, 250],
  customLabel = 'Custom',
  customAmount = 500,
  logTitle = 'Today',
  entries = [
    { label: 'Water (+250)', time: '12:31' },
    { label: 'Water (+150)', time: '12:31' },
  ],
  onAdd,
  onCustom,
}: Props) {
  const reducedMotion = useReducedMotion();
  const controlled = progress != null;
  const auto = autoPlay && !controlled && !reducedMotion;
  const rest = controlled ? Math.min(1, Math.max(0, progress)) * goal : amount;
  const initial = auto ? 0 : rest;

  const time = useSharedValue(0);
  const clock = useSharedValue(0);
  const looping = useSharedValue(auto);
  // Seconds the loop still waits after a touch.
  const hold = useSharedValue(0);
  const target = useSharedValue(initial);
  const level = useSharedValue(initial);
  const velocity = useSharedValue(0);
  const energy = useSharedValue(0);
  const splashAt = useSharedValue(-10);

  const [shownMl, setShownMl] = useState(Math.round(initial));
  // Drinks added by hand, newest first: they push the given entries down the log.
  const [added, setAdded] = useState<Entry[]>([]);

  const moveTo = (ml: number) => {
    target.set(ml);
    if (reducedMotion) level.set(withTiming(ml, { duration: 250, reduceMotion: ReduceMotion.Never }));
  };

  useEffect(() => {
    looping.set(auto);
    hold.set(0);
    clock.set(0);
    target.set(auto ? 0 : rest);
    if (reducedMotion) level.set(withTiming(rest, { duration: 250, reduceMotion: ReduceMotion.Never }));
  }, [auto, rest, reducedMotion, clock, hold, level, looping, target]);

  useFrameCallback((frame) => {
    const dt = Math.min(frame.timeSincePreviousFrame ?? 16, 64) / 1000;
    const now = time.get() + dt;
    if (hold.get() > 0) {
      hold.set(hold.get() - dt);
      // Time is up: the loop takes over again by draining what was added.
      if (hold.get() <= 0 && looping.get()) clock.set(DRAIN_AT);
    } else if (looping.get()) {
      let t = clock.get() + dt;
      if (t >= LOOP) t -= LOOP;
      clock.set(t);
      const next = scriptTarget(t, amount);
      if (next > target.get()) splashAt.set(now);
      target.set(next);
    }
    const x = level.get();
    const v = velocity.get() + (OMEGA * OMEGA * (target.get() - x) - 2 * OMEGA * velocity.get()) * dt;
    velocity.set(v);
    level.set(x + v * dt);
    // Fast changes disturb the water; it calms down by itself.
    energy.set(Math.max(energy.get() * Math.exp(-dt * 1.5), Math.min(1, (Math.abs(v) / goal) * 2.2)));
    time.set(now);
  }, !reducedMotion);

  useAnimatedReaction(
    () => Math.round(level.get() / 10) * 10,
    (now, old) => {
      if (now !== old) scheduleOnRN(setShownMl, now);
    },
  );

  const percentStyle = useAnimatedStyle(() => {
    const base = baseline(level.get(), goal);
    const y = surfaceAt(PCT_X, base, time.get(), energy.get(), 0);
    const tilt = 0.085 * energy.get() * Math.sin(time.get() * 5.4);
    return {
      transform: [{ translateY: Math.min(y + 6, PCT_MAX_TOP) }, { rotate: `${Math.atan(tilt) * 0.7}rad` }],
    };
  });

  const tick = () => {
    if (process.env.EXPO_OS !== 'web') Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
  };

  const add = (ml: number) => {
    tick();
    if (controlled) {
      onAdd?.(ml, Math.round(rest) + ml);
      return;
    }
    const total = Math.min(goal, Math.round(target.get()) + ml);
    moveTo(total);
    splashAt.set(time.get());
    hold.set(USER_HOLD);
    setAdded((list) => [{ label: `${title} (+${ml})`, time: entries[0]?.time ?? '' }, ...list].slice(0, 2));
    onAdd?.(ml, total);
  };

  // --- Dragging up and down on the tank raises and lowers the level in 50 ml steps. ---
  const drag = useRef({ y: 0, from: 0, last: 0, moved: false });

  const onGrant = (e: GestureResponderEvent) => {
    const from = Math.round(target.get());
    drag.current = { y: e.nativeEvent.pageY, from, last: from, moved: false };
  };

  const onMove = (e: GestureResponderEvent) => {
    if (controlled) return;
    const d = drag.current;
    const dy = e.nativeEvent.pageY - d.y;
    if (!d.moved && Math.abs(dy) < DRAG_SLOP) return;
    d.moved = true;
    const raw = d.from - (dy / DRAG_SPAN) * goal;
    const next = Math.min(goal, Math.max(0, Math.round(raw / DRAG_STEP) * DRAG_STEP));
    if (next === d.last) return;
    d.last = next;
    moveTo(next);
    hold.set(USER_HOLD);
    if (process.env.EXPO_OS !== 'web') Haptics.selectionAsync().catch(() => {});
  };

  const onEnd = () => {
    const d = drag.current;
    if (d.moved && d.last !== d.from) onAdd?.(d.last - d.from, d.last);
  };

  const percent = percentOf(shownMl, goal);
  const rows = [...added, ...entries].slice(0, 2);
  const motion: Motion = { level, time, energy, splashAt, goal };

  return (
    <View style={styles.card}>
      <View pointerEvents="none" style={styles.back}>
        <Back {...motion} />
      </View>

      <View pointerEvents="none" style={styles.clip}>
        <Animated.Text style={[styles.percent, percentStyle]} numberOfLines={1}>
          {percent}%
        </Animated.Text>
        <Front {...motion} />
      </View>

      <View
        style={styles.heading}
        accessible
        accessibilityRole="progressbar"
        accessibilityLabel={`${title} today`}
        accessibilityValue={{ min: 0, max: goal, now: shownMl, text: `${shownMl} of ${goal} ${unit}, ${percent}%` }}
      >
        <Text style={styles.title} numberOfLines={1}>
          {title}
        </Text>
        <Text style={[styles.title, styles.dim]} numberOfLines={1}>
          today: <Text style={styles.figure}>{grouped(shownMl)}</Text> of {grouped(goal)}
        </Text>
        <Text style={[styles.title, styles.dim]} numberOfLines={1}>
          {unit}
        </Text>
      </View>

      <View
        style={styles.tank}
        onStartShouldSetResponder={() => !controlled}
        onResponderTerminationRequest={() => false}
        onResponderGrant={onGrant}
        onResponderMove={onMove}
        onResponderRelease={onEnd}
        onResponderTerminate={onEnd}
      />

      <View style={styles.chips}>
        {quickAmounts.map((ml) => (
          <Chip key={ml} label={`${ml} ${unit}`} accessibilityLabel={`Add ${ml} ${unit}`} onPress={() => add(ml)} />
        ))}
      </View>
      <Pressable
        style={styles.customSlot}
        accessibilityRole="button"
        accessibilityLabel={customLabel}
        onPress={() => {
          onCustom?.();
          add(customAmount);
        }}
      >
        {({ pressed }) => (
          <Animated.View
            style={[
              styles.custom,
              {
                transform: [{ scale: pressed ? 0.97 : 1 }],
                transitionProperty: 'transform',
                transitionDuration: 130,
                transitionTimingFunction: CSS_EASE_OUT,
              },
            ]}
          >
            <Text style={styles.customLabel} numberOfLines={1}>
              {customLabel}
            </Text>
          </Animated.View>
        )}
      </Pressable>

      <View pointerEvents="none" style={styles.log}>
        <Text style={styles.logTitle} numberOfLines={1}>
          {logTitle}
        </Text>
        {rows.map((row, index) => (
          <View key={index} style={styles.logRow}>
            <Text style={styles.logText} numberOfLines={1}>
              {row.label}
            </Text>
            <Text style={styles.logText} numberOfLines={1}>
              {row.time}
            </Text>
          </View>
        ))}
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  card: {
    width: WIDTH,
    height: HEIGHT,
  },
  back: {
    position: 'absolute',
    left: -BLEED,
    top: -BLEED,
  },
  backCanvas: {
    width: WIDTH + BLEED * 2,
    height: HEIGHT + BLEED * 2,
  },
  // The percentage and everything in front of it, clipped to the card.
  clip: {
    position: 'absolute',
    left: 0,
    top: 0,
    width: WIDTH,
    height: HEIGHT,
    borderRadius: RADIUS,
    overflow: 'hidden',
  },
  frontCanvas: {
    position: 'absolute',
    left: 0,
    top: 0,
    width: WIDTH,
    height: HEIGHT,
  },
  percent: {
    position: 'absolute',
    left: PCT_LEFT,
    top: 0,
    width: 200,
    color: 'rgba(255,255,255,0.94)',
    fontSize: 54,
    lineHeight: 60,
    fontWeight: '300',
    letterSpacing: -1.5,
    fontVariant: ['tabular-nums'],
  },
  heading: {
    position: 'absolute',
    left: 22,
    top: 20,
    width: WIDTH - 44,
  },
  title: {
    color: '#ffffff',
    fontSize: 24,
    lineHeight: 27,
    fontWeight: '500',
    letterSpacing: -0.4,
  },
  dim: {
    color: 'rgba(214,240,248,0.5)',
  },
  figure: {
    color: '#ffffff',
    fontVariant: ['tabular-nums'],
  },
  tank: {
    position: 'absolute',
    left: 0,
    top: 104,
    width: WIDTH,
    height: 140,
  },
  chips: {
    position: 'absolute',
    left: 20,
    top: 254,
    height: 28,
    flexDirection: 'row',
    gap: 14,
  },
  chip: {
    height: 28,
    flexDirection: 'row',
    alignItems: 'center',
  },
  chipDisc: {
    width: 28,
    height: 28,
    borderRadius: 14,
    backgroundColor: 'rgba(12,44,62,0.62)',
    alignItems: 'center',
    justifyContent: 'center',
  },
  plusH: {
    position: 'absolute',
    width: 9,
    height: 1.4,
    borderRadius: 1,
    backgroundColor: 'rgba(255,255,255,0.85)',
  },
  plusV: {
    position: 'absolute',
    width: 1.4,
    height: 9,
    borderRadius: 1,
    backgroundColor: 'rgba(255,255,255,0.85)',
  },
  chipLabel: {
    marginLeft: 7,
    color: 'rgba(255,255,255,0.82)',
    fontSize: 11.5,
    lineHeight: 16,
    fontWeight: '500',
    fontVariant: ['tabular-nums'],
  },
  customSlot: {
    position: 'absolute',
    right: 18,
    top: 249,
  },
  custom: {
    width: 80,
    height: 38,
    borderRadius: 19,
    backgroundColor: '#f1e40c',
    alignItems: 'center',
    justifyContent: 'center',
  },
  customLabel: {
    color: '#1d3a2c',
    fontSize: 12.5,
    lineHeight: 17,
    fontWeight: '600',
  },
  log: {
    position: 'absolute',
    left: 24,
    top: PANEL_Y + 13,
    width: WIDTH - 48,
  },
  logTitle: {
    color: '#1b1a24',
    fontSize: 23,
    lineHeight: 28,
    fontWeight: '500',
    letterSpacing: -0.3,
    marginBottom: 7,
  },
  logRow: {
    height: 24,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  logText: {
    color: '#2a2834',
    fontSize: 11.5,
    lineHeight: 16,
    fontWeight: '500',
    fontVariant: ['tabular-nums'],
  },
});
