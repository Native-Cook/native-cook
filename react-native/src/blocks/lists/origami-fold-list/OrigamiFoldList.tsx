import { Image } from 'expo-image';
import * as Haptics from 'expo-haptics';
import { LinearGradient } from 'expo-linear-gradient';
import { useEffect, useState } from 'react';
import { type LayoutChangeEvent, StyleSheet, Text, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  Easing,
  type SharedValue,
  useAnimatedReaction,
  useAnimatedStyle,
  useDerivedValue,
  useFrameCallback,
  useReducedMotion,
  useSharedValue,
  withTiming,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

export type DiveSpot = {
  name: string;
  place: string;
  /** Maximum depth in metres. */
  depth: number;
  /** Water temperature in °C. */
  temperature: number;
  visibility: number;
  current: string;
  level: string;
  /** Months of the season, 0 = January. */
  season: number[];
  /** A line under the photo strip in the last panel. */
  entry: string;
  /** Unsplash photo ids: a square thumbnail and a wide strip. */
  thumb: string;
  strip: string;
};

type Props = {
  spots?: DiveSpot[];
  eyebrow?: string;
  title?: string;
  /** Opens and closes a few rows on a loop of LOOP seconds. */
  autoPlay?: boolean;
  /** Called with the index of the row that opens, or -1 when all are folded. */
  onOpen?: (index: number) => void;
};

const SPOTS: DiveSpot[] = [
  {
    name: 'Blue Corner',
    place: 'Koror · Palau',
    depth: 30,
    temperature: 28,
    visibility: 30,
    current: 'Strong drift',
    level: 'Advanced',
    season: [11, 0, 1, 2, 3],
    entry: 'Reef hook on the wall · 45 min by boat',
    thumb: 'photo-1583212292454-1fe6229603b7',
    strip: 'photo-1494879540385-bc170b0878a7',
  },
  {
    name: 'Barracuda Point',
    place: 'Sipadan · Malaysia',
    depth: 25,
    temperature: 29,
    visibility: 20,
    current: 'Moderate',
    level: 'Intermediate',
    season: [3, 4, 5, 6, 7, 8, 9, 10],
    entry: 'Negative entry off the drop-off · 10 min out',
    thumb: 'photo-1637308107080-82cf40953742',
    strip: 'photo-1507295386538-ddd5e86cd597',
  },
  {
    name: 'Tubbataha North',
    place: 'Sulu Sea · Philippines',
    depth: 40,
    temperature: 27,
    visibility: 35,
    current: 'Mild',
    level: 'Advanced',
    season: [2, 3, 4, 5],
    entry: 'Liveaboard only · twelve hours offshore',
    thumb: 'photo-1514907283155-ea5f4094c70c',
    strip: 'photo-1508624217470-5ef0f947d8be',
  },
  {
    name: 'Crystal Bay',
    place: 'Nusa Penida · Indonesia',
    depth: 18,
    temperature: 22,
    visibility: 25,
    current: 'Surge',
    level: 'Intermediate',
    season: [6, 7, 8, 9],
    entry: 'Sheltered cove · cold upwellings bring the sunfish',
    thumb: 'photo-1701673505069-45a4078044ba',
    strip: 'photo-1517219039361-66f283bce5db',
  },
  {
    name: 'Ras Mohammed',
    place: 'Red Sea · Egypt',
    depth: 35,
    temperature: 25,
    visibility: 30,
    current: 'Mild drift',
    level: 'Advanced',
    season: [3, 4, 5, 6, 7, 8, 9, 10],
    entry: 'Shark and Yolanda reefs · day boat from the marina',
    thumb: 'photo-1637308109237-4ea1a7dd22f5',
    strip: 'photo-1470217407524-b1e77afc6ec5',
  },
  {
    name: 'Cod Hole',
    place: 'Coral Sea · Australia',
    depth: 24,
    temperature: 26,
    visibility: 30,
    current: 'Gentle',
    level: 'Beginner',
    season: [5, 6, 7, 8, 9, 10],
    entry: 'Ribbon reefs · potato cod come to say hello',
    thumb: 'photo-1694661641480-d4e76d6bead3',
    strip: 'photo-1466094899371-97b327dff551',
  },
];

const FAMILY = process.env.EXPO_OS === 'ios' ? 'Avenir Next' : 'sans-serif';
const MONTHS = ['J', 'F', 'M', 'A', 'M', 'J', 'J', 'A', 'S', 'O', 'N', 'D'];

function photoUrl(id: string, width: number) {
  return `https://images.unsplash.com/${id}?auto=format&fit=crop&w=${width}&q=80`;
}

const INK = '#071519';
const SEA = '#0f232a';
const PAPER = '#f1eadc';
const PAPER_INK = '#0c2a31';
const MUTED = '#7c9ea6';
const CORAL = '#ff7d5c';

// Rows, in pt.
const SIDE = 18;
const ROW_HEIGHT = 78;
const ROW_GAP = 10;
const THUMB = 58;
// The fold under a row: three panels of PANEL_HEIGHT pt, and a GAP between the row and the paper.
const PANELS = 3;
const PANEL_HEIGHT = 76;
const FOLD_GAP = 6;
const FOLD_HEIGHT = PANELS * PANEL_HEIGHT;
// Each panel starts to open STAGGER (of the whole opening) after the one above it, so the paper
// unfolds one panel after another. Panels hinge on their top edges, seen through PERSPECTIVE.
const STAGGER = 0.24;
const PERSPECTIVE = 700;
// How dark a fold's shadow is while it is shut.
const SHADE = 0.78;
// Past fully open, the paper stretches with a quarter of the finger's travel.
const RUBBER = 0.25;

// The spring that opens and closes a fold, in fold-lengths: stiffness, damping (mass 1).
const STIFFNESS = 130;
const DAMPING = 20;
// A release opens the fold when the projected amount (now + velocity × THROW) passes half.
const THROW = 0.2;
// Reduced motion: the fold eases to its target at this rate per second, flat and fading.
const REDUCED_RATE = 9;

// autoPlay (seconds): unfold a row, unfold another (the first folds back), a third, fold all.
const LOOP = 11;
const CUES = [
  { at: 0.8, row: 1 },
  { at: 3.4, row: 3 },
  { at: 6.0, row: 0 },
  { at: 8.6, row: -1 },
];
const USER_HOLD = 4;
// Entering the screen: the script waits ENTRY_DELAY s before its first cue, the header fades in and the rows fade up
// LIST_IN ms once the header has been measured, and the folded paper under the rows (hundreds of
// hidden views and the wide photos) is built one row per frame from FOLDS_AFTER ms, after the
// screen has settled, so the first frames only carry what is visible.
const ENTRY_DELAY = 1.2;
const LIST_IN = 320; // ms
const LIST_RISE = 10; // pt
const FOLDS_AFTER = 380; // ms
// The header is gone once the list has slid up this far, in pt.
const HEADER_FADE = 90;

const EASE_OUT = Easing.bezier(0.23, 1, 0.32, 1);

function clamp01(x: number) {
  'worklet';
  return Math.min(1, Math.max(0, x));
}

/** How far panel `k` is open, 0 … 1, for a fold opened by `open`. */
function panelOpen(open: number, k: number) {
  'worklet';
  return clamp01((open - k * STAGGER) / (1 - (PANELS - 1) * STAGGER));
}

/** The height a fold takes on screen: each panel shows its height × sin of how far it has turned. */
function foldHeight(open: number, reduced: boolean) {
  'worklet';
  let h = 0;
  for (let k = 0; k < PANELS; k += 1) {
    const p = panelOpen(open, k);
    h += PANEL_HEIGHT * (reduced ? p : Math.sin((p * Math.PI) / 2));
  }
  const gap = FOLD_GAP * clamp01(open * 4);
  return h + gap + Math.max(0, open - 1) * FOLD_HEIGHT;
}

type Layout = { tops: number[]; folds: number[]; shift: number };

type PanelProps = {
  k: number;
  /** How far this row's fold is open. */
  open: SharedValue<number>;
  reduced: boolean;
  children: React.ReactNode;
};

/** One panel of the fold, hinged at its top edge on the bottom of the panel above. */
function FoldPanel({ k, open: openValue, reduced, children }: PanelProps) {
  const style = useAnimatedStyle(() => {
    const open = openValue.get();
    let top = FOLD_GAP * clamp01(open * 4);
    for (let j = 0; j < k; j += 1) {
      const p = panelOpen(open, j);
      top += PANEL_HEIGHT * (reduced ? p : Math.sin((p * Math.PI) / 2));
    }
    // Stretched past open, the panels part a little.
    top += Math.max(0, open - 1) * FOLD_HEIGHT * (k / PANELS);
    const p = panelOpen(open, k);
    // The paper is folded like an accordion: every other panel leans the other way.
    const angle = reduced ? 0 : (k % 2 === 0 ? -1 : 1) * 90 * (1 - p);
    return {
      opacity: reduced ? p : p > 0.002 ? 1 : 0,
      transform: [{ translateY: top }, { perspective: PERSPECTIVE }, { rotateX: `${angle}deg` }],
    };
  });
  const shadeStyle = useAnimatedStyle(() => ({ opacity: SHADE * (1 - panelOpen(openValue.get(), k)) }));
  return (
    <Animated.View style={[styles.panel, k === PANELS - 1 && styles.panelLast, style]}>
      {children}
      {/* The fold's shadow: deepest along the crease the panel turns about, gone once it is flat. */}
      <Animated.View pointerEvents="none" style={[StyleSheet.absoluteFill, shadeStyle]}>
        <LinearGradient
          colors={k % 2 === 0 ? ['rgba(4,14,18,0.25)', 'rgba(4,14,18,1)'] : ['rgba(4,14,18,1)', 'rgba(4,14,18,0.35)']}
          style={StyleSheet.absoluteFill}
        />
      </Animated.View>
      {k > 0 && <View pointerEvents="none" style={styles.crease} />}
    </Animated.View>
  );
}

type RowProps = {
  spot: DiveSpot;
  index: number;
  opens: SharedValue<number[]>;
  layout: SharedValue<Layout>;
  reduced: boolean;
  /** Whether the folded paper under the row has been built yet. */
  folds: boolean;
};

/** A dive site: its row and, under it, the folded paper with the details. */
function SpotRow({ spot, index, opens, layout, reduced, folds }: RowProps) {
  // This row's own copy of how open its fold is, written only when it changes, so a fold that is
  // still costs nothing while another one moves (every style below reads this, not the array).
  const open = useSharedValue(0);
  useAnimatedReaction(
    () => opens.get()[index] ?? 0,
    (now, before) => {
      if (now !== before) open.set(now);
    },
  );
  const style = useAnimatedStyle(() => {
    const l = layout.get();
    return { transform: [{ translateY: l.tops[index] - l.shift }] };
  });
  const chevron = useAnimatedStyle(() => ({
    transform: [{ rotate: `${clamp01(open.get()) * 180}deg` }],
  }));
  return (
    <Animated.View style={[styles.rowGroup, style]}>
      {folds && <Folds spot={spot} open={open} reduced={reduced} />}

      <View
        style={styles.row}
        accessible
        accessibilityRole="button"
        accessibilityLabel={`${spot.name}, ${spot.place}, ${spot.depth} metres, ${spot.temperature} degrees`}
      >
        <Image
          source={{ uri: photoUrl(spot.thumb, 200) }}
          style={styles.thumb}
          contentFit="cover"
          cachePolicy="memory-disk"
          transition={240}
        />
        <View style={styles.rowText}>
          <Text style={styles.name} numberOfLines={1}>
            {spot.name}
          </Text>
          <Text style={styles.place} numberOfLines={1}>
            {spot.place}
          </Text>
        </View>
        <View style={styles.figures}>
          <Text style={styles.depth}>
            {spot.depth}
            <Text style={styles.unit}> m</Text>
          </Text>
          <Text style={styles.temp}>{spot.temperature}°C</Text>
        </View>
        <Animated.View style={[styles.chevron, chevron]}>
          <View style={[styles.chevronStroke, styles.chevronLeft]} />
          <View style={[styles.chevronStroke, styles.chevronRight]} />
        </Animated.View>
      </View>
    </Animated.View>
  );
}

/** The folded paper under a row: its stats, the season strip and the photo with the way in. */
function Folds({ spot, open, reduced }: { spot: DiveSpot; open: SharedValue<number>; reduced: boolean }) {
  return (
    <View style={styles.folds} pointerEvents="none">
      <FoldPanel k={0} open={open} reduced={reduced}>
        <View style={styles.stats}>
          <Stat label="Visibility" value={`${spot.visibility} m`} />
          <Stat label="Current" value={spot.current} />
          <Stat label="Level" value={spot.level} />
        </View>
      </FoldPanel>
      <FoldPanel k={1} open={open} reduced={reduced}>
        <View style={styles.season}>
          <Text style={styles.statLabel}>BEST MONTHS</Text>
          <View style={styles.months}>
            {MONTHS.map((m, i) => {
              const on = spot.season.includes(i);
              return (
                <View key={i} style={styles.month}>
                  <View style={[styles.monthBar, on && styles.monthBarOn]} />
                  <Text style={[styles.monthText, on && styles.monthTextOn]}>{m}</Text>
                </View>
              );
            })}
          </View>
        </View>
      </FoldPanel>
      <FoldPanel k={2} open={open} reduced={reduced}>
        <Image
          source={{ uri: photoUrl(spot.strip, 900) }}
          style={StyleSheet.absoluteFill}
          contentFit="cover"
          cachePolicy="memory-disk"
          transition={200}
        />
        <LinearGradient
          colors={['rgba(4,14,18,0)', 'rgba(4,14,18,0.72)']}
          locations={[0.25, 1]}
          style={StyleSheet.absoluteFill}
        />
        <Text style={styles.entry} numberOfLines={1}>
          {spot.entry}
        </Text>
      </FoldPanel>
    </View>
  );
}

function Stat({ label, value }: { label: string; value: string }) {
  return (
    <View style={styles.stat}>
      <Text style={styles.statLabel}>{label.toUpperCase()}</Text>
      <Text style={styles.statValue} numberOfLines={1}>
        {value}
      </Text>
    </View>
  );
}

/**
 * A logbook of dive sites. Tapping a row unfolds its details under it like folded paper, one
 * panel after another, pushing the rows below down; tapping it again folds it back. Dragging a
 * row down opens the fold under the finger, and letting go finishes it with a spring.
 */
export function OrigamiFoldList({
  spots = SPOTS,
  eyebrow = 'Logbook · 2026',
  title = 'Where the reef\nopens up',
  autoPlay = false,
  onOpen,
}: Props) {
  const insets = useSafeAreaInsets();
  const reduced = useReducedMotion();
  const count = spots.length;
  const [height, setHeight] = useState(0);
  const [listTop, setListTop] = useState(0);
  // How many rows have their folded paper built (see FOLDS_AFTER).
  const [built, setBuilt] = useState(0);

  // Per row: how far its fold is open (1 = open), its speed, and where it is going.
  const opens = useSharedValue<number[]>(spots.map(() => 0));
  const speeds = useSharedValue<number[]>(spots.map(() => 0));
  const targets = useSharedValue<number[]>(spots.map(() => 0));
  const dragRow = useSharedValue(-1);
  const dragFrom = useSharedValue(0);
  const clock = useSharedValue(0);
  const looping = useSharedValue(autoPlay);
  const hold = useSharedValue(0);
  const top = useSharedValue(0);
  const bottom = useSharedValue(0);

  // The list fades up once, when the header has been measured and the rows know where they sit.
  const shown = useSharedValue(0);

  useEffect(() => {
    looping.set(autoPlay);
    // The first cue waits for the screen to settle; later loops run from 0.
    clock.set(-ENTRY_DELAY);
    hold.set(0);
  }, [autoPlay, clock, hold, looping]);

  // Builds the folded paper one row per frame, once the screen has settled.
  useEffect(() => {
    if (built >= count) return;
    let frame = 0;
    const timer = setTimeout(
      () => {
        frame = requestAnimationFrame(() => setBuilt((n) => Math.min(count, n + 1)));
      },
      built === 0 ? FOLDS_AFTER : 0,
    );
    return () => {
      clearTimeout(timer);
      cancelAnimationFrame(frame);
    };
  }, [built, count]);
  const buildAll = () => setBuilt(count);
  useEffect(() => {
    top.set(listTop);
    bottom.set(height - insets.bottom - 20);
    if (listTop > 0) shown.set(withTiming(1, { duration: reduced ? 200 : LIST_IN, easing: EASE_OUT }));
  }, [listTop, height, insets.bottom, top, bottom, shown, reduced]);

  // Where each row sits: below the rows above it and their folds. When an open fold would run
  // off the bottom, the whole list slides up to keep it in view.
  const layout = useDerivedValue<Layout>(() => {
    const o = opens.get();
    const tops: number[] = [];
    const folds: number[] = [];
    let y = top.get();
    let shift = 0;
    for (let k = 0; k < o.length; k += 1) {
      tops.push(y);
      const f = foldHeight(o[k], reduced);
      folds.push(f);
      if (o[k] > 0.001) shift = Math.max(shift, y + ROW_HEIGHT + f - bottom.get());
      y += ROW_HEIGHT + f + ROW_GAP;
    }
    return { tops, folds, shift: Math.max(0, shift) };
  });

  const haptic = () => {
    if (process.env.EXPO_OS !== 'web') Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
  };
  const opened = (row: number) => onOpen?.(row);

  /** Sends one row's fold open (or `-1` for none) and every other one shut. */
  const openOnly = (row: number) => {
    'worklet';
    const t = targets.get().map((_, k) => (k === row ? 1 : 0));
    targets.set(t);
    scheduleOnRN(opened, row);
  };

  useFrameCallback((frame) => {
    const dt = Math.min(frame.timeSincePreviousFrame ?? 16, 48) / 1000;
    // Nothing moves until the rows have been placed on screen (the header measured): a screen can
    // be mounted a moment before it is shown, and the script must start when it is.
    if (dt <= 0 || top.get() <= 0) return;
    if (dragRow.get() < 0) {
      if (hold.get() > 0) {
        hold.set(hold.get() - dt);
        if (hold.get() <= 0) clock.set(0);
      } else if (looping.get()) {
        const before = clock.get();
        const t = (before + dt) % LOOP;
        clock.set(t);
        for (let k = 0; k < CUES.length; k += 1) {
          const at = CUES[k].at;
          if ((before < at && t >= at) || (t < before && (at > before || at <= t))) openOnly(CUES[k].row);
        }
      }
    }
    const o = opens.get().slice();
    const v = speeds.get().slice();
    const goal = targets.get();
    let moved = false;
    for (let k = 0; k < o.length; k += 1) {
      if (k === dragRow.get()) continue;
      if (Math.abs(goal[k] - o[k]) < 0.0005 && Math.abs(v[k]) < 0.001) {
        if (o[k] !== goal[k]) {
          o[k] = goal[k];
          v[k] = 0;
          moved = true;
        }
        continue;
      }
      if (reduced) {
        o[k] += (goal[k] - o[k]) * (1 - Math.exp(-REDUCED_RATE * dt));
        v[k] = 0;
      } else {
        v[k] += (STIFFNESS * (goal[k] - o[k]) - DAMPING * v[k]) * dt;
        o[k] += v[k] * dt;
      }
      moved = true;
    }
    if (moved) {
      opens.set(o);
      speeds.set(v);
    }
  });

  /** The row under a point of the screen, folds included, or -1. */
  const rowAt = (y: number) => {
    'worklet';
    const l = layout.get();
    for (let k = 0; k < count; k += 1) {
      const t = l.tops[k] - l.shift;
      if (y >= t - ROW_GAP / 2 && y <= t + ROW_HEIGHT + l.folds[k] + ROW_GAP / 2) return k;
    }
    return -1;
  };

  const pan = Gesture.Pan()
    .activeOffsetY([-8, 8])
    .failOffsetX([-24, 24])
    .onStart((event) => {
      const row = rowAt(event.y - event.translationY);
      if (row < 0) return;
      scheduleOnRN(buildAll);
      hold.set(USER_HOLD);
      dragRow.set(row);
      dragFrom.set(opens.get()[row]);
      // Only one fold open at a time: the others start folding away.
      targets.set(targets.get().map((g, k) => (k === row ? g : 0)));
    })
    .onUpdate((event) => {
      const row = dragRow.get();
      if (row < 0) return;
      const raw = dragFrom.get() + event.translationY / FOLD_HEIGHT;
      const next = raw > 1 ? 1 + (raw - 1) * RUBBER : Math.max(0, raw);
      const o = opens.get().slice();
      o[row] = next;
      opens.set(o);
    })
    .onEnd((event) => {
      const row = dragRow.get();
      if (row < 0) return;
      // A velocity the recogniser could not measure counts as none.
      const velocity = Number.isFinite(event.velocityY) ? event.velocityY / FOLD_HEIGHT : 0;
      const open = opens.get()[row] + velocity * THROW > 0.5;
      const v = speeds.get().slice();
      v[row] = reduced ? 0 : velocity;
      speeds.set(v);
      if (open !== dragFrom.get() > 0.5) scheduleOnRN(haptic);
      if (open) openOnly(row);
      else {
        const t = targets.get().slice();
        t[row] = 0;
        targets.set(t);
        scheduleOnRN(opened, -1);
      }
    })
    .onFinalize(() => {
      dragRow.set(-1);
    });

  const tap = Gesture.Tap()
    .maxDuration(300)
    .onEnd((event, success) => {
      if (!success) return;
      const row = rowAt(event.y);
      if (row < 0) return;
      hold.set(USER_HOLD);
      scheduleOnRN(buildAll);
      scheduleOnRN(haptic);
      openOnly(targets.get()[row] > 0.5 ? -1 : row);
    });

  // When the list slides up for a fold near the bottom, the header slides with it and fades
  // before it reaches the status bar.
  const headerStyle = useAnimatedStyle(() => {
    const shift = layout.get().shift;
    return { opacity: (1 - clamp01(shift / HEADER_FADE)) * shown.get(), transform: [{ translateY: -shift }] };
  });
  const listStyle = useAnimatedStyle(() => ({
    opacity: shown.get(),
    transform: [{ translateY: reduced ? 0 : (1 - shown.get()) * LIST_RISE }],
  }));

  return (
    <GestureDetector gesture={Gesture.Race(pan, tap)}>
      <View style={styles.screen} onLayout={(e: LayoutChangeEvent) => setHeight(e.nativeEvent.layout.height)}>
        <Animated.View
          style={[styles.header, { paddingTop: insets.top + 22 }, headerStyle]}
          onLayout={(e: LayoutChangeEvent) => setListTop(e.nativeEvent.layout.y + e.nativeEvent.layout.height + 6)}
        >
          <Text style={styles.eyebrow}>{eyebrow.toUpperCase()}</Text>
          <Text style={styles.title} accessibilityRole="header">
            {title}
          </Text>
          <Text style={styles.hint}>Tap a site to unfold it · drag down to peek</Text>
        </Animated.View>

        <Animated.View style={[StyleSheet.absoluteFill, listStyle]} pointerEvents="none">
          {listTop > 0 &&
            spots.map((spot, index) => (
              <SpotRow
                key={spot.name}
                spot={spot}
                index={index}
                opens={opens}
                layout={layout}
                reduced={reduced}
                folds={index < built}
              />
            ))}
        </Animated.View>

        <LinearGradient
          pointerEvents="none"
          colors={['rgba(7,21,25,0)', INK]}
          style={[styles.fade, { height: insets.bottom + 56 }]}
        />
      </View>
    </GestureDetector>
  );
}

const styles = StyleSheet.create({
  screen: {
    flex: 1,
    overflow: 'hidden',
    backgroundColor: INK,
  },
  header: {
    paddingHorizontal: SIDE + 4,
    paddingBottom: 18,
    gap: 6,
  },
  eyebrow: {
    color: CORAL,
    fontFamily: FAMILY,
    fontSize: 11,
    fontWeight: '700',
    letterSpacing: 2.2,
  },
  title: {
    color: PAPER,
    fontFamily: FAMILY,
    fontSize: 34,
    lineHeight: 39,
    fontWeight: '700',
    letterSpacing: -0.8,
  },
  hint: {
    marginTop: 4,
    color: MUTED,
    fontFamily: FAMILY,
    fontSize: 13,
    fontWeight: '500',
  },
  rowGroup: {
    position: 'absolute',
    top: 0,
    left: SIDE,
    right: SIDE,
  },
  row: {
    height: ROW_HEIGHT,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 14,
    paddingHorizontal: 10,
    borderRadius: 22,
    borderCurve: 'continuous',
    backgroundColor: SEA,
    borderWidth: StyleSheet.hairlineWidth,
    borderColor: 'rgba(160,210,220,0.14)',
  },
  thumb: {
    width: THUMB,
    height: THUMB,
    borderRadius: 15,
    borderCurve: 'continuous',
  },
  rowText: {
    flex: 1,
    gap: 2,
  },
  name: {
    color: PAPER,
    fontFamily: FAMILY,
    fontSize: 17,
    fontWeight: '600',
    letterSpacing: -0.2,
  },
  place: {
    color: MUTED,
    fontFamily: FAMILY,
    fontSize: 12.5,
    fontWeight: '500',
  },
  figures: {
    alignItems: 'flex-end',
  },
  depth: {
    color: PAPER,
    fontFamily: FAMILY,
    fontSize: 22,
    fontWeight: '700',
    fontVariant: ['tabular-nums'],
    letterSpacing: -0.5,
  },
  unit: {
    color: MUTED,
    fontSize: 13,
    fontWeight: '600',
  },
  temp: {
    color: MUTED,
    fontFamily: FAMILY,
    fontSize: 12,
    fontWeight: '600',
    fontVariant: ['tabular-nums'],
  },
  chevron: {
    width: 22,
    height: 22,
    marginRight: 2,
    alignItems: 'center',
    justifyContent: 'center',
  },
  chevronStroke: {
    position: 'absolute',
    width: 8,
    height: 1.8,
    borderRadius: 1,
    backgroundColor: MUTED,
    top: 11,
  },
  chevronLeft: {
    left: 4.6,
    transform: [{ rotate: '40deg' }],
  },
  chevronRight: {
    right: 4.6,
    transform: [{ rotate: '-40deg' }],
  },
  folds: {
    position: 'absolute',
    top: ROW_HEIGHT,
    left: 8,
    right: 8,
  },
  panel: {
    position: 'absolute',
    top: 0,
    left: 0,
    right: 0,
    height: PANEL_HEIGHT,
    backgroundColor: PAPER,
    overflow: 'hidden',
    transformOrigin: 'top',
  },
  panelLast: {
    borderBottomLeftRadius: 18,
    borderBottomRightRadius: 18,
    borderCurve: 'continuous',
  },
  crease: {
    position: 'absolute',
    top: 0,
    left: 0,
    right: 0,
    height: StyleSheet.hairlineWidth,
    backgroundColor: 'rgba(12,42,49,0.28)',
  },
  stats: {
    flex: 1,
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 16,
  },
  stat: {
    flex: 1,
    gap: 4,
  },
  statLabel: {
    color: 'rgba(12,42,49,0.55)',
    fontFamily: FAMILY,
    fontSize: 9.5,
    fontWeight: '700',
    letterSpacing: 1.4,
  },
  statValue: {
    color: PAPER_INK,
    fontFamily: FAMILY,
    fontSize: 15,
    fontWeight: '600',
  },
  season: {
    flex: 1,
    justifyContent: 'center',
    paddingHorizontal: 16,
    gap: 9,
  },
  months: {
    flexDirection: 'row',
    justifyContent: 'space-between',
  },
  month: {
    alignItems: 'center',
    gap: 5,
    width: 18,
  },
  monthBar: {
    width: 14,
    height: 5,
    borderRadius: 2.5,
    backgroundColor: 'rgba(12,42,49,0.14)',
  },
  monthBarOn: {
    backgroundColor: CORAL,
  },
  monthText: {
    color: 'rgba(12,42,49,0.4)',
    fontFamily: FAMILY,
    fontSize: 10.5,
    fontWeight: '600',
  },
  monthTextOn: {
    color: PAPER_INK,
  },
  entry: {
    position: 'absolute',
    left: 16,
    right: 16,
    bottom: 12,
    color: '#ffffff',
    fontFamily: FAMILY,
    fontSize: 13,
    fontWeight: '600',
  },
  fade: {
    position: 'absolute',
    left: 0,
    right: 0,
    bottom: 0,
  },
});
