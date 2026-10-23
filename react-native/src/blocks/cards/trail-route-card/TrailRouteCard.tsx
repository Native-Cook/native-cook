import { Canvas, Circle, Group, Line, LinearGradient as SkiaGradient, Path, Skia, vec } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { Image } from 'expo-image';
import { LinearGradient } from 'expo-linear-gradient';
import { useEffect, useState } from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import Animated, {
  type SharedValue,
  cubicBezier,
  useAnimatedReaction,
  useAnimatedStyle,
  useDerivedValue,
  useFrameCallback,
  useReducedMotion,
  useSharedValue,
} from 'react-native-reanimated';
import { scheduleOnRN } from 'react-native-worklets';

type Props = {
  /** Walks the trail by itself every 8 s. */
  autoPlay?: boolean;
  title?: string;
  range?: string;
  /** Address of a landscape photo, about 4 : 3. */
  photo?: string;
  /** Flat colour shown until the photo arrives. */
  photoTint?: string;
  buttonLabel?: string;
  difficulty?: string;
  /** How many of the three difficulty bars are lit, 0…3. */
  level?: number;
  note?: string;
  distanceKm?: number;
  distanceLabel?: string;
  /** Metres climbed over the whole trail. */
  elevationM?: number;
  elevationLabel?: string;
  durationMin?: number;
  durationLabel?: string;
  /** Colour of the lit bars, the elevation profile and the flag, as `#rrggbb`. */
  accent?: string;
  onDirections?: () => void;
};

const WIDTH = 340;
const HEIGHT = 368;
const RADIUS = 34;
const INK = '#151515';
const MUTE = '#9a9a9a';
// Two Gaussians, of sigma 30 and 4; CSS writes a blur as twice the sigma.
const CARD_SHADOW = '0 22px 60px rgba(0,0,0,0.1), 0 2px 8px rgba(0,0,0,0.04)';

const PHOTO_INSET = 11;
const PHOTO_WIDTH = WIDTH - PHOTO_INSET * 2;
const PHOTO_HEIGHT = 240;
const PHOTO_RADIUS = 26;
const TITLE_LEFT = 30;
const TITLE_TOP = 194;
const PILL_LEFT = 168;
const PILL_TOP = 193;
const PILL_WIDTH = 138;
const PILL_HEIGHT = 36;
const PILL_RADIUS = 12;
const PRESS_SCALE = 0.96;
// How fast the pill follows the loop's finger, per second.
const PRESS_RATE = 22;

const LEFT = 25;
const DIFFICULTY_TOP = 270;
const BARS_GAP = 7;
const BAR_WIDTH = 3.5;
const BAR_SPACING = 2.5;
const BAR_HEIGHTS = [6, 9, 12];
// How much taller a bar gets for a moment as it lights.
const BAR_BUMP = 0.3;
// Progress along the trail at which each bar lights, and the share of it the fade takes.
const BAR_AT = [0.06, 0.42, 0.78];
const BAR_FADE = 0.04;
const NOTE_TOP = 290;
const RULE_TOP = 311;
const STATS_TOP = 319;
const STATS_WIDTH = 171;
const STAT_LEFTS = [25, 88, 146];

// Heights of the trail at even steps along it, 0…1.
const PROFILE = [0.1, 0.22, 0.18, 0.4, 0.55, 0.48, 0.72, 0.9, 1, 0.7, 0.46, 0.3];
const PROFILE_BASE = 352;
const PROFILE_HEIGHT = 38;
// A silhouette without an outline: a line through the labels would read as struck-out text.
const PROFILE_TOP = 0.3;
const PROFILE_BOTTOM = 0.05;

const MAP_LEFT = 220;
const MAP_TOP = 269;
const MAP_WIDTH = 104;
const MAP_HEIGHT = 81;
const MAP_RADIUS = 26;
const MAP_COLOR = '#f3f3f3';
// The route in the map's own points: its start, then three points for each cubic curve.
const ROUTE = [
  24, 54, 16, 50, 16, 44, 22, 40, 28, 34, 30, 24, 40, 26, 48, 28, 48, 36, 56, 34, 64, 32, 68, 26, 76, 30, 84, 34, 88,
  40, 84, 44, 80, 50, 74, 58, 66, 56, 58, 54, 52, 48, 44, 52,
];
// Straight pieces each curve is cut into.
const ROUTE_STEPS = 16;
const ROUTE_STROKE = 1.8;
const ROUTE_FAINT = 0.14;
const DOT_RADIUS = 3.2;
const DOT_RING = 4.8;
const FLAG_POLE = 13;
const FLAG_WIDTH = 8;
const FLAG_DROP = 6;

// A walk, in seconds from the tap: the figures drop to zero, the trail is walked, the flag pops,
// and after a look at the result the route fades back to its outline.
const HIKE_AT = 0.1;
const HIKE_TIME = 4;
// Share of the walk spent speeding up, and again slowing down.
const PACE_RAMP = 0.15;
// The flag goes up as the dot comes to a stop.
const FLAG_AT = 3.8;
const RESET_AT = 5.4;
const RESET_TIME = 0.7;
const RUN_TIME = RESET_AT + RESET_TIME;
const INK_FADE = 0.25;
const RISE_TIME = 0.6;
// The figures blink down to this opacity as they restart from zero.
const STATS_DIP = 0.35;
const STATS_FADE = 0.3;
const ZOOM = 0.08;

// The flag's pop: a spring from 0 to 1.
const FLAG_MASS = 1;
const FLAG_STIFFNESS = 260;
const FLAG_DAMPING = 14;

// autoPlay loop (seconds): a finger presses the pill, lifts, and the walk plays out.
const PRESS_AT = 0.8;
const PRESS_TIME = 0.14;
const LOOP = 8;
// After a walk started by hand the loop waits this long before it restarts.
const USER_HOLD = 2;

// The figures travel between threads as the share of their full values, in steps of this.
const SHARE_STEPS = 2000;

const CSS_EASE_OUT = cubicBezier(0.23, 1, 0.32, 1);

/** The route as a polyline: each curve of ROUTE cut into short straight pieces. */
const ROUTE_X: number[] = [ROUTE[0]];
const ROUTE_Y: number[] = [ROUTE[1]];
/** Length of the route up to each of its points. */
const ROUTE_AT: number[] = [0];
for (let index = 2; index + 5 < ROUTE.length; index += 6) {
  const fromX = ROUTE_X[ROUTE_X.length - 1];
  const fromY = ROUTE_Y[ROUTE_Y.length - 1];
  for (let step = 1; step <= ROUTE_STEPS; step++) {
    const t = step / ROUTE_STEPS;
    const u = 1 - t;
    const x = u * u * u * fromX + 3 * u * u * t * ROUTE[index] + 3 * u * t * t * ROUTE[index + 2] + t * t * t * ROUTE[index + 4];
    const y = u * u * u * fromY + 3 * u * u * t * ROUTE[index + 1] + 3 * u * t * t * ROUTE[index + 3] + t * t * t * ROUTE[index + 5];
    const last = ROUTE_X.length - 1;
    ROUTE_AT.push(ROUTE_AT[last] + Math.hypot(x - ROUTE_X[last], y - ROUTE_Y[last]));
    ROUTE_X.push(x);
    ROUTE_Y.push(y);
  }
}
const ROUTE_LAST = ROUTE_X.length - 1;
const ROUTE_OUTLINE = ROUTE_X.map((x, index) => `${index === 0 ? 'M' : 'L'}${x} ${ROUTE_Y[index]}`).join('');
const FLAG_CLOTH = `M${ROUTE_X[ROUTE_LAST]} ${ROUTE_Y[ROUTE_LAST] - FLAG_POLE}l${FLAG_WIDTH} ${FLAG_DROP / 2}l${-FLAG_WIDTH} ${FLAG_DROP / 2}Z`;

function clamp01(t: number) {
  'worklet';
  return Math.min(1, Math.max(0, t));
}

function smooth(t: number) {
  'worklet';
  const p = clamp01(t);
  return p * p * (3 - 2 * p);
}

function easeOut(t: number) {
  'worklet';
  const p = 1 - clamp01(t);
  return 1 - p * p * p;
}

/** The walk's pace, 0…1: a short run-up, a steady stride and a short slow-down. */
function pace(t: number) {
  'worklet';
  const p = clamp01(t);
  if (p < PACE_RAMP) return (p * p) / (2 * PACE_RAMP * (1 - PACE_RAMP));
  if (p > 1 - PACE_RAMP) return 1 - ((1 - p) * (1 - p)) / (2 * PACE_RAMP * (1 - PACE_RAMP));
  return (p - PACE_RAMP / 2) / (1 - PACE_RAMP);
}

/** A spring let go at 0 and settling at 1, `t` seconds on: it overshoots by about a fifth. */
function pop(t: number) {
  'worklet';
  if (t <= 0) return 0;
  const decay = FLAG_DAMPING / (2 * FLAG_MASS);
  const omega = Math.sqrt(FLAG_STIFFNESS / FLAG_MASS - decay * decay);
  return 1 - Math.exp(-decay * t) * (Math.cos(omega * t) + (decay / omega) * Math.sin(omega * t));
}

/** Share of the whole climb done at `progress` along the trail: only the way up counts. */
function climbShare(progress: number) {
  const at = clamp01(progress) * (PROFILE.length - 1);
  let climbed = 0;
  let total = 0;
  for (let index = 0; index < PROFILE.length - 1; index++) {
    const gain = Math.max(0, PROFILE[index + 1] - PROFILE[index]);
    total += gain;
    climbed += gain * clamp01(at - index);
  }
  return climbed / total;
}

function distanceText(km: number) {
  const tenths = Math.round(km * 10);
  return `${Math.floor(tenths / 10)}.${tenths % 10}km`;
}

function durationText(minutes: number) {
  return `${Math.floor(minutes / 60)}h ${minutes % 60}m`;
}

/** `#rrggbb` with an alpha. */
function withAlpha(hex: string, alpha: number) {
  const n = parseInt(hex.slice(1), 16);
  return `rgba(${(n >> 16) & 255},${(n >> 8) & 255},${n & 255},${alpha})`;
}

/** Everything the card draws, worked out from how far into the walk it is; `run` is negative at rest. */
function cardState(run: number, reduced: boolean) {
  'worklet';
  if (run < 0) {
    return { running: false, progress: 0, share: 1, statsOpacity: 1, ink: 0, rise: 1, dot: 1, dotAtStart: true, flag: 0, zoom: 1 };
  }
  // Under reduced motion nothing travels: the finished route fades in, then out.
  const progress = reduced ? 1 : pace((run - HIKE_AT) / HIKE_TIME);
  const reset = smooth((run - RESET_AT) / RESET_TIME);
  return {
    running: true,
    // 0…1 along the trail.
    progress,
    // Share of the figures shown: they stand at their full values at rest.
    share: progress,
    statsOpacity: reduced ? 1 : STATS_DIP + (1 - STATS_DIP) * smooth(run / STATS_FADE),
    // Opacity of what the walk draws: the black route and the elevation profile.
    ink: smooth(run / INK_FADE) * (1 - reset),
    // 0…1: the profile's height as it rises from its baseline.
    rise: reduced ? 1 : easeOut(run / RISE_TIME),
    dot: Math.abs(1 - 2 * reset),
    dotAtStart: reset >= 0.5,
    // Scale of the flag, past 1 while it pops.
    flag: (reduced ? smooth(run / INK_FADE) : pop(run - FLAG_AT)) * (1 - reset),
    zoom: reduced ? 1 : 1 + ZOOM * progress * (1 - reset),
  };
}

type State = SharedValue<ReturnType<typeof cardState>>;

/** One difficulty bar: lit at rest, lighting in its turn along the walk. */
function Bar({ index, lit, state, accent }: { index: number; lit: boolean; state: State; accent: string }) {
  const light = useDerivedValue(() => {
    if (!lit) return 0;
    const s = state.get();
    return s.running ? clamp01((s.progress - BAR_AT[index]) / BAR_FADE) : 1;
  });
  const barStyle = useAnimatedStyle(() => ({ transform: [{ scaleY: 1 + BAR_BUMP * Math.sin(Math.PI * light.get()) }] }));
  const litStyle = useAnimatedStyle(() => ({ opacity: light.get() }));
  return (
    <Animated.View style={[styles.bar, { height: BAR_HEIGHTS[index] }, barStyle]}>
      <Animated.View style={[styles.barLit, { backgroundColor: accent }, litStyle]} />
    </Animated.View>
  );
}

/** The climb so far, drawn behind the figures. */
function Profile({ state, accent }: { state: State; accent: string }) {
  const base = PROFILE_HEIGHT + 2;
  const area = useDerivedValue(() => {
    const rise = state.get().rise;
    const path = Skia.Path.Make();
    const last = PROFILE.length - 1;
    const x = (index: number) => (STATS_WIDTH * index) / last;
    const y = (index: number) => base - PROFILE_HEIGHT * PROFILE[index] * rise;
    path.moveTo(x(0), y(0));
    for (let index = 1; index < last; index++) {
      path.quadTo(x(index), y(index), (x(index) + x(index + 1)) / 2, (y(index) + y(index + 1)) / 2);
    }
    path.lineTo(x(last), y(last));
    path.lineTo(STATS_WIDTH, base);
    path.lineTo(0, base);
    path.close();
    return path;
  });
  // Only the part already walked shows.
  const walked = useDerivedValue(() => ({ x: 0, y: 0, width: STATS_WIDTH * state.get().progress, height: base }));
  const opacity = useDerivedValue(() => state.get().ink);
  return (
    <Canvas style={styles.profile} pointerEvents="none">
      <Group clip={walked} opacity={opacity}>
        <Path path={area}>
          <SkiaGradient
            start={vec(0, base - PROFILE_HEIGHT)}
            end={vec(0, base)}
            colors={[withAlpha(accent, PROFILE_TOP), withAlpha(accent, PROFILE_BOTTOM)]}
          />
        </Path>
      </Group>
    </Canvas>
  );
}

/** The route's outline, the part already walked, the dot and the flag at its end. */
function RouteMap({ state, accent }: { state: State; accent: string }) {
  // The route from its start to where the walk has got to; the dot sits on its last point.
  const walked = useDerivedValue(() => {
    const reach = clamp01(state.get().progress) * ROUTE_AT[ROUTE_LAST];
    const path = Skia.Path.Make();
    path.moveTo(ROUTE_X[0], ROUTE_Y[0]);
    for (let index = 1; index <= ROUTE_LAST; index++) {
      if (ROUTE_AT[index] <= reach) {
        path.lineTo(ROUTE_X[index], ROUTE_Y[index]);
      } else {
        const t = (reach - ROUTE_AT[index - 1]) / (ROUTE_AT[index] - ROUTE_AT[index - 1]);
        path.lineTo(
          ROUTE_X[index - 1] + (ROUTE_X[index] - ROUTE_X[index - 1]) * t,
          ROUTE_Y[index - 1] + (ROUTE_Y[index] - ROUTE_Y[index - 1]) * t,
        );
        break;
      }
    }
    return path;
  });
  const ink = useDerivedValue(() => state.get().ink);
  const dotX = useDerivedValue(() => (state.get().dotAtStart ? ROUTE_X[0] : walked.get().getLastPt().x));
  const dotY = useDerivedValue(() => (state.get().dotAtStart ? ROUTE_Y[0] : walked.get().getLastPt().y));
  const dotOpacity = useDerivedValue(() => state.get().dot);
  const flagScale = useDerivedValue(() => [{ scale: state.get().flag }]);
  const flagOpacity = useDerivedValue(() => Math.min(1, state.get().flag));
  const end = vec(ROUTE_X[ROUTE_LAST], ROUTE_Y[ROUTE_LAST]);

  return (
    <Canvas style={styles.mapCanvas}>
      <Path
        path={ROUTE_OUTLINE}
        style="stroke"
        strokeWidth={ROUTE_STROKE}
        strokeCap="round"
        strokeJoin="round"
        color={`rgba(0,0,0,${ROUTE_FAINT})`}
      />
      <Path path={walked} style="stroke" strokeWidth={ROUTE_STROKE} strokeCap="round" strokeJoin="round" color={INK} opacity={ink} />
      <Group transform={flagScale} origin={end} opacity={flagOpacity}>
        <Line p1={end} p2={vec(end.x, end.y - FLAG_POLE)} strokeWidth={1.6} strokeCap="round" color={INK} />
        <Path path={FLAG_CLOTH} color={accent} />
      </Group>
      <Group opacity={dotOpacity}>
        <Circle cx={dotX} cy={dotY} r={DOT_RING} color={MAP_COLOR} />
        <Circle cx={dotX} cy={dotY} r={DOT_RADIUS} color={INK} />
      </Group>
    </Canvas>
  );
}

/**
 * A trail card: a mountain photo with the trail's name and a frosted "Directions" pill on its dark
 * lower band, then the difficulty, three figures and a small drawing of the route. Tapping the
 * pill walks the trail: the route draws itself with a dot travelling along it, the figures count
 * up in step, an elevation profile rises behind them, the photo slowly zooms and a flag pops at
 * the end, before the route fades back to its faint outline.
 */
export function TrailRouteCard({
  autoPlay = false,
  title = 'Embercrest Ridge',
  range = 'Silverpine Mountains',
  photo = 'https://images.unsplash.com/photo-1506905925346-21bda4d32df4?auto=format&fit=crop&w=636&h=480&q=80',
  photoTint = '#656778',
  buttonLabel = 'Directions',
  difficulty = 'Hard',
  level = 3,
  note = 'First climbed in 1886',
  distanceKm = 12.4,
  distanceLabel = 'Distance',
  elevationM = 870,
  elevationLabel = 'Elevation',
  durationMin = 285,
  durationLabel = 'Duration',
  accent = '#e0672f',
  onDirections,
}: Props) {
  const reducedMotion = useReducedMotion();
  const auto = autoPlay && !reducedMotion;

  const clock = useSharedValue(0);
  const looping = useSharedValue(auto);
  // Seconds into the walk, or -1 at rest.
  const run = useSharedValue(-1);
  // The loop's own finger is on the pill.
  const scripted = useSharedValue(false);
  // 0…1: how far the loop's finger has pressed the pill in.
  const press = useSharedValue(0);
  // A touch started this walk: the loop waits for it to end, then `hold` more seconds.
  const touched = useSharedValue(false);
  const hold = useSharedValue(0);

  const [share, setShare] = useState(1);

  useEffect(() => {
    looping.set(auto);
    clock.set(0);
    scripted.set(false);
    touched.set(false);
    if (auto) run.set(-1);
  }, [auto, clock, looping, run, scripted, touched]);

  const arrive = () => {
    if (process.env.EXPO_OS !== 'web') Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success).catch(() => {});
  };

  useFrameCallback((frame) => {
    const dt = Math.min(frame.timeSincePreviousFrame ?? 16, 64) / 1000;
    if (dt <= 0) return;
    if (run.get() >= 0) {
      const before = run.get();
      const now = before + dt;
      // The flag of a walk started by hand lands with a haptic.
      const flagAt = reducedMotion ? 0 : FLAG_AT;
      if (touched.get() && before <= flagAt && now > flagAt) scheduleOnRN(arrive);
      run.set(now < RUN_TIME ? now : -1);
    }
    if (touched.get()) {
      if (run.get() < 0) {
        hold.set(hold.get() - dt);
        if (hold.get() <= 0) {
          touched.set(false);
          clock.set(0);
        }
      }
    } else if (looping.get()) {
      let t = clock.get() + dt;
      if (t >= LOOP) t -= LOOP;
      clock.set(t);
      const down = t >= PRESS_AT && t < PRESS_AT + PRESS_TIME;
      // The loop's finger lifts: the walk starts, as it does after a tap.
      if (scripted.get() && !down && run.get() < 0) run.set(Math.max(0, t - PRESS_AT - PRESS_TIME));
      scripted.set(down);
    }
    press.set(press.get() + ((scripted.get() ? 1 : 0) - press.get()) * (1 - Math.exp(-dt * PRESS_RATE)));
  });

  const state = useDerivedValue(() => cardState(run.get(), reducedMotion));

  useAnimatedReaction(
    () => Math.round(state.get().share * SHARE_STEPS),
    (now, old) => {
      if (now !== old) scheduleOnRN(setShare, now / SHARE_STEPS);
    },
  );

  const photoStyle = useAnimatedStyle(() => ({ transform: [{ scale: state.get().zoom }] }));
  const pillStyle = useAnimatedStyle(() => ({ transform: [{ scale: 1 - (1 - PRESS_SCALE) * press.get() }] }));
  const statsStyle = useAnimatedStyle(() => ({ opacity: state.get().statsOpacity }));

  /** A tap on the pill: the walk starts unless one is under way, and the loop waits for it. */
  const onPress = () => {
    onDirections?.();
    if (run.get() >= 0) return;
    run.set(0);
    touched.set(true);
    hold.set(USER_HOLD);
  };

  const stats = [
    { value: distanceText(distanceKm * share), label: distanceLabel },
    { value: `${Math.round(elevationM * climbShare(share))}m`, label: elevationLabel },
    { value: durationText(Math.round(durationMin * share)), label: durationLabel },
  ];

  return (
    <View style={styles.card}>
      <View style={[styles.photo, { backgroundColor: photoTint }]}>
        <Animated.View style={[StyleSheet.absoluteFill, photoStyle]}>
          <Image source={{ uri: photo }} style={StyleSheet.absoluteFill} contentFit="cover" transition={200} />
        </Animated.View>
        {/* The band the title and the pill sit on. */}
        <LinearGradient
          colors={['rgba(18,15,12,0)', 'rgba(18,15,12,0.6)', 'rgba(18,15,12,0.94)']}
          locations={[0.42, 0.68, 1]}
          style={StyleSheet.absoluteFill}
        />
      </View>

      <View style={styles.heading}>
        <Text style={styles.title} numberOfLines={1}>
          {title}
        </Text>
        <Text style={styles.range} numberOfLines={1}>
          {range}
        </Text>
      </View>

      <Animated.View style={[styles.pillSlot, pillStyle]}>
        <Pressable
          accessibilityRole="button"
          accessibilityLabel={buttonLabel}
          onPressIn={() => {
            if (process.env.EXPO_OS !== 'web') Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
          }}
          onPress={onPress}
        >
          {({ pressed }) => (
            <Animated.View
              style={[
                styles.pill,
                {
                  transform: [{ scale: pressed ? PRESS_SCALE : 1 }],
                  transitionProperty: 'transform',
                  transitionDuration: 130,
                  transitionTimingFunction: CSS_EASE_OUT,
                },
              ]}
            >
              <Text style={styles.pillLabel} numberOfLines={1}>
                {buttonLabel}
              </Text>
            </Animated.View>
          )}
        </Pressable>
      </Animated.View>

      <View style={styles.difficulty} accessible accessibilityLabel={difficulty}>
        <Text style={styles.difficultyText} numberOfLines={1}>
          {difficulty}
        </Text>
        <View style={styles.bars}>
          {BAR_HEIGHTS.map((_, index) => (
            <Bar key={index} index={index} lit={index < level} state={state} accent={accent} />
          ))}
        </View>
      </View>
      <Text style={styles.note} numberOfLines={1}>
        {note}
      </Text>
      <View style={styles.rule} />

      <Profile state={state} accent={accent} />

      <View
        accessible
        accessibilityLabel={`${distanceText(distanceKm)}, ${elevationM}m, ${durationText(durationMin)}`}
        style={StyleSheet.absoluteFill}
        pointerEvents="none"
      >
        {stats.map((stat, index) => (
          <Animated.View key={index} style={[styles.stat, { left: STAT_LEFTS[index] }, statsStyle]}>
            <Text style={styles.statValue} numberOfLines={1}>
              {stat.value}
            </Text>
            <Text style={styles.statLabel} numberOfLines={1}>
              {stat.label}
            </Text>
          </Animated.View>
        ))}
      </View>

      <View style={styles.map} accessibilityElementsHidden importantForAccessibility="no-hide-descendants">
        <RouteMap state={state} accent={accent} />
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  card: {
    width: WIDTH,
    height: HEIGHT,
    borderRadius: RADIUS,
    backgroundColor: '#ffffff',
    boxShadow: CARD_SHADOW,
  },
  photo: {
    position: 'absolute',
    left: PHOTO_INSET,
    top: PHOTO_INSET,
    width: PHOTO_WIDTH,
    height: PHOTO_HEIGHT,
    borderRadius: PHOTO_RADIUS,
    overflow: 'hidden',
  },
  heading: {
    position: 'absolute',
    left: TITLE_LEFT,
    top: TITLE_TOP,
    width: PILL_LEFT - TITLE_LEFT - 8,
    gap: 1,
  },
  title: {
    color: '#ffffff',
    fontSize: 13.5,
    lineHeight: 18,
    fontWeight: '500',
  },
  range: {
    color: 'rgba(255,255,255,0.6)',
    fontSize: 10.5,
    lineHeight: 14,
  },
  pillSlot: {
    position: 'absolute',
    left: PILL_LEFT,
    top: PILL_TOP,
  },
  pill: {
    width: PILL_WIDTH,
    height: PILL_HEIGHT,
    borderRadius: PILL_RADIUS,
    borderWidth: 1,
    borderColor: 'rgba(255,255,255,0.12)',
    backgroundColor: 'rgba(255,255,255,0.22)',
    alignItems: 'center',
    justifyContent: 'center',
  },
  pillLabel: {
    color: '#ffffff',
    fontSize: 14,
    lineHeight: 18,
    fontWeight: '500',
    letterSpacing: 0.4,
  },
  difficulty: {
    position: 'absolute',
    left: LEFT,
    top: DIFFICULTY_TOP,
    flexDirection: 'row',
    alignItems: 'flex-end',
    gap: BARS_GAP,
  },
  difficultyText: {
    color: INK,
    fontSize: 14,
    lineHeight: 18,
    fontWeight: '600',
  },
  bars: {
    flexDirection: 'row',
    alignItems: 'flex-end',
    gap: BAR_SPACING,
    marginBottom: 4,
  },
  bar: {
    width: BAR_WIDTH,
    borderRadius: 1,
    backgroundColor: 'rgba(0,0,0,0.12)',
    overflow: 'hidden',
    transformOrigin: 'bottom',
  },
  barLit: {
    flex: 1,
  },
  note: {
    position: 'absolute',
    left: LEFT,
    top: NOTE_TOP,
    width: STATS_WIDTH,
    color: MUTE,
    fontSize: 10.5,
    lineHeight: 14,
  },
  rule: {
    position: 'absolute',
    left: LEFT,
    top: RULE_TOP,
    width: STATS_WIDTH,
    height: 1,
    backgroundColor: '#e6e6e6',
  },
  profile: {
    position: 'absolute',
    left: LEFT,
    top: PROFILE_BASE - PROFILE_HEIGHT - 2,
    width: STATS_WIDTH,
    height: PROFILE_HEIGHT + 2,
  },
  stat: {
    position: 'absolute',
    top: STATS_TOP,
    gap: 1,
  },
  statValue: {
    color: INK,
    fontSize: 14,
    lineHeight: 18,
    fontWeight: '500',
    fontVariant: ['tabular-nums'],
  },
  statLabel: {
    color: MUTE,
    fontSize: 9.5,
    lineHeight: 12,
  },
  map: {
    position: 'absolute',
    left: MAP_LEFT,
    top: MAP_TOP,
    width: MAP_WIDTH,
    height: MAP_HEIGHT,
    borderRadius: MAP_RADIUS,
    backgroundColor: MAP_COLOR,
  },
  mapCanvas: {
    width: MAP_WIDTH,
    height: MAP_HEIGHT,
  },
});
