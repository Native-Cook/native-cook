import { Canvas, Picture, Skia, TileMode, type SkCanvas, type SkPaint } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { Image } from 'expo-image';
import { SymbolView } from 'expo-symbols';
import { useEffect, useMemo, useState } from 'react';
import { type LayoutChangeEvent, StyleSheet, Text, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  type SharedValue,
  useAnimatedStyle,
  useDerivedValue,
  useFrameCallback,
  useReducedMotion,
  useSharedValue,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

/** One photo in the feed. `shape` is how it is cropped: portrait, square or landscape. */
export type GooSighting = { id: string; shape: 'portrait' | 'square' | 'landscape'; title: string; by: string; likes: number };

type Props = {
  /** The photos already in the feed, newest first. */
  sightings?: GooSighting[];
  /**
   * The photos refreshes bring in, a row of two at a time in the order they arrive. The two of a
   * row share a shape, so both columns move by the same amount.
   */
  incoming?: [GooSighting, GooSighting][];
  /** Pulls to refresh, waits for the new photos, scrolls down the feed and back up. */
  autoPlay?: boolean;
  /** Called when a pull commits to a refresh. */
  onRefresh?: () => void;
};

const FEED: GooSighting[] = [
  { id: 'photo-1536589961747-e239b2abbec2', shape: 'portrait', title: 'Supervising the sofa delivery', by: 'Ines Mora', likes: 2184 },
  { id: 'photo-1616081352935-7daa6487b6ae', shape: 'portrait', title: 'Low pass over the reeds', by: 'Tomas Hale', likes: 932 },
  { id: 'photo-1498100152307-ce63fd6c5424', shape: 'landscape', title: 'Window seat, as always', by: 'Lea Brandt', likes: 3016 },
  { id: 'photo-1591055749071-927e6ddffc82', shape: 'portrait', title: 'Hovered a full minute, then gone', by: 'Kit Okafor', likes: 1207 },
  { id: 'photo-1596854372745-0906a0593bca', shape: 'square', title: 'Caught mid-judgement', by: 'Noor Haddad', likes: 2875 },
  { id: 'photo-1593893722928-17454e6d5fb8', shape: 'landscape', title: 'Gulls riding the cliff updraft', by: 'Tomas Hale', likes: 688 },
  { id: 'photo-1559624989-7b9303bd9792', shape: 'portrait', title: 'Found the one sunbeam in the flat', by: 'Mika Sato', likes: 4410 },
  { id: 'photo-1470167494176-c2e966a4eb3d', shape: 'landscape', title: 'The whole flock turned at once', by: 'Ada Lind', likes: 1540 },
  { id: 'photo-1647806422508-0322f33e270b', shape: 'portrait', title: 'Claimed the laundry basket', by: 'Mika Sato', likes: 6230 },
  { id: 'photo-1626444344029-5c680f7513c1', shape: 'portrait', title: 'Tiny, loud, very sure of itself', by: 'Kit Okafor', likes: 864 },
  { id: 'photo-1583795128727-6ec3642408f8', shape: 'landscape', title: 'Nap number four', by: 'Joel Vance', likes: 5122 },
  { id: 'photo-1549589465-442e6195e54b', shape: 'portrait', title: 'Talons out, eyes on the grass', by: 'Ada Lind', likes: 1993 },
  { id: 'photo-1582725461742-8ecd962c260d', shape: 'portrait', title: 'Back from the vet, still sulking', by: 'Lea Brandt', likes: 3384 },
  { id: 'photo-1554409011-d874157657b8', shape: 'portrait', title: 'Spotted the heron first', by: 'Joel Vance', likes: 1102 },
];

const INCOMING: [GooSighting, GooSighting][] = [
  [
    { id: 'photo-1611843275167-a9bba9aa65dd', shape: 'portrait', title: 'Top of the bookshelf, finally', by: 'Noor Haddad', likes: 214 },
    { id: 'photo-1567187549304-1fe8331879b5', shape: 'portrait', title: 'Same fence post every morning', by: 'Tomas Hale', likes: 230 },
  ],
  [
    { id: 'photo-1597838816882-4435b1977fbe', shape: 'square', title: 'Morning stretch, full length', by: 'Ines Mora', likes: 388 },
    { id: 'photo-1448227922836-6d05b3f8b663', shape: 'square', title: 'Wings folded, straight down', by: 'Ada Lind', likes: 96 },
  ],
  [
    { id: 'photo-1552933529-e359b2477252', shape: 'landscape', title: 'Versus the curtain, round three', by: 'Joel Vance', likes: 402 },
    { id: 'photo-1731650211720-54d314975a7b', shape: 'landscape', title: 'Two finches, one seed', by: 'Kit Okafor', likes: 142 },
  ],
];

const CHIPS = ['For you', 'Cats', 'Birds', 'Nearby', 'Following'];

const unsplash = (id: string, w: number, h: number) => `https://images.unsplash.com/${id}?auto=format&fit=crop&w=${w}&h=${h}&q=80`;

// The page, the header, the text greys and the one accent: the liquid of the refresh drop.
const PAGE = '#f4f2ee';
const HEADER = '#ffffff';
const INK = '#1b1a18';
const MUTED = '#8c877f';
const CHIP = '#efece7';
const AVATAR = '#e6e1d9';
const PHOTO_BLANK = '#e7e3dc';
const ACCENT = '#ff5a2c';

// Layout, in pt: the header under the safe area, the feed's margins and each tile's parts. Tiles
// are a photo of the column's width (its height from SHAPE) over TEXT_H of title and byline.
const HEADER_H = 102;
const LIST_TOP = 12;
const SIDE = 12;
const COL_GAP = 10;
const ROW_GAP = 18;
const TEXT_H = 46;
const SHAPE = { portrait: 1.3, square: 1, landscape: 0.76 } as const;

// The liquid. It lives behind the header's bottom edge: a reservoir whose meniscus sags out under
// the edge as the feed is pulled (from MEN_REST to MEN_PULL pt over the first MEN_FULL pt of
// pull), on a springy surface (MEN_STIFFNESS, MEN_DAMPING per unit mass).
const GOO_BLUR = 4.5;
const GOO_MATRIX = [1, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 18, -7];
const RES_RX = 30;
const RES_RY = 14;
const MEN_REST = -9;
const MEN_PULL = 9;
const MEN_FULL = 40;
const MEN_STIFFNESS = 380;
const MEN_DAMPING = 13;
// Pulling draws a drop out of it, its centre at DROP_TRAVEL of the pull, growing from BEAD_R to
// DROP_R, on a neck of NECK_BEADS overlapping beads that thins from NECK_WIDE to NECK_THIN as the
// pull nears SNAP_PULL, where the neck breaks and the drop hangs free.
const DROP_TRAVEL = 0.62;
const BEAD_R = 5;
const DROP_R = 18;
const NECK_BEADS = 10;
const NECK_WIDE = 11;
const NECK_THIN = 3.4;
const SNAP_PULL = 108;
// After the snap: the neck melts at NECK_CUT/s, the drop gets a SNAP_KICK pt/s push and springs to
// the middle of the gap (DROP_STIFFNESS, DROP_DAMPING), stretching with its speed and jiggling
// (JIGGLE of its size at JIGGLE_HZ, dying at JIGGLE_DECAY/s).
const NECK_CUT = 26;
const SNAP_KICK = 260;
const DROP_STIFFNESS = 260;
const DROP_DAMPING = 18;
const STRETCH_PER_SPEED = 1 / 1800;
const JIGGLE = 0.16;
const JIGGLE_HZ = 7;
const JIGGLE_DECAY = 6;
// Refreshing: the feed waits HOLD below the header while the drop becomes a gooey loader: a core
// of CORE_R with three droplets of SAT_R that swing out to ORBIT on a PULSE s breath, merging and
// pinching off as the trio turns at TURN deg/s. After REFRESH_TIME s they merge back over
// MERGE_TIME s into one drop of MERGED_R, which the header draws back up into itself on a spring
// (RETURN_STIFFNESS, RETURN_DAMPING) before the feed closes and the new row slides in.
const HOLD = 100;
const CORE_R = 13;
const SAT_R = 9;
const ORBIT = 30;
const PULSE = 1.1;
const TURN = 250;
const SPREAD_IN = 0.3;
const REFRESH_TIME = 1.5;
const MERGE_TIME = 0.35;
const MERGED_R = 17;
const RETURN_STIFFNESS = 200;
const RETURN_DAMPING = 20;
// After a return the drop under the header grows back over REGROW s before it can be pulled again.
const REGROW = 0.5;

// Scrolling: iOS-like deceleration, resistance past either end over RUBBER_SPAN of the screen.
const DECELERATION = 2.1;
const EDGE_STIFFNESS = 180;
const EDGE_DAMPING = 26.8;
const RUBBER_SPAN = 0.5;
// The new row's arrival: a spring on how many rows are still hidden above the feed.
const REVEAL_STIFFNESS = 70;
const REVEAL_DAMPING = 15;

// autoPlay (seconds): pull, refresh, read down the feed, back to the top, rest.
const LOOP = 9;
const PULL_FROM = 0.6;
const PULL_TO = 1.8;
const PULL_RAW = 300;
const DOWN_FROM = 5.3;
const DOWN_TO = 6.9;
const DOWN_BY = 1100;
const SWAP_AT = 7.0;
const UP_FROM = 7.2;
const UP_TO = 8.4;
const USER_HOLD = 3.5;

// The drop's phases.
const IDLE = 0;
const FREE = 1;
const LOADING = 2;
const MERGING = 3;
const RETURNING = 4;

function clamp01(x: number) {
  'worklet';
  return Math.min(1, Math.max(0, x));
}

function mix(a: number, b: number, t: number) {
  'worklet';
  return a + (b - a) * t;
}

function easeOut(u: number) {
  'worklet';
  return 1 - Math.pow(1 - clamp01(u), 3);
}

function easeInOut(u: number) {
  'worklet';
  const x = clamp01(u);
  return x < 0.5 ? 4 * x * x * x : 1 - Math.pow(-2 * x + 2, 3) / 2;
}

/** iOS-style resistance: `x` pt past an edge shows as at most `span`. */
function rubber(x: number, span: number) {
  'worklet';
  return (1 - 1 / ((x * 0.55) / span + 1)) * span;
}

/** The finger travel that `rubber` turned into `r`. */
function unrubber(r: number, span: number) {
  'worklet';
  const k = Math.min(0.999, r / span);
  return (span / 0.55) * (1 / (1 - k) - 1);
}

/** How far the hidden rows reach down a column when `h` of them (a fraction mid-arrival) are hidden. */
function hiddenReach(prefix: number[], h: number) {
  'worklet';
  const n = prefix.length - 1;
  if (n <= 0) return 0;
  const x = Math.min(n, Math.max(0, h));
  const k = Math.min(n - 1, Math.floor(x));
  return prefix[k] + (prefix[k + 1] - prefix[k]) * (x - k);
}

/** Where the drop rests in a gap `gap` tall: in its middle, riding up under the header as it closes. */
function dropAim(bar: number, gap: number) {
  'worklet';
  return bar + Math.min(gap / 2, gap - HOLD / 2);
}

/** Beads between the meniscus at `y0` and a drop at `y1`, wide at both ends and thinnest midway. */
function drawNeck(canvas: SkCanvas, paint: SkPaint, cx: number, y0: number, y1: number, width: number) {
  'worklet';
  if (width < 0.3 || y1 <= y0) return;
  for (let k = 1; k <= NECK_BEADS; k += 1) {
    const t = k / (NECK_BEADS + 1);
    const r = width * (0.62 + 0.38 * Math.pow(Math.abs(2 * t - 1), 1.4));
    canvas.drawCircle(cx, mix(y0, y1, t), r, paint);
  }
}

/** A drop of radius `r` squashed by `s` (taller when positive), keeping about the same area. */
function drawDrop(canvas: SkCanvas, paint: SkPaint, cx: number, cy: number, r: number, s: number) {
  'worklet';
  const rx = r * (1 - s * 0.5);
  const ry = r * (1 + s);
  canvas.drawOval(Skia.XYWHRect(cx - rx, cy - ry, rx * 2, ry * 2), paint);
}

/** A soft highlight on the upper left of a drop, so it reads as liquid rather than a flat dot. */
function drawGlint(canvas: SkCanvas, paint: SkPaint, cx: number, cy: number, r: number) {
  'worklet';
  if (r < 6) return;
  canvas.drawOval(Skia.XYWHRect(cx - r * 0.62, cy - r * 0.62, r * 0.6, r * 0.38), paint);
}

function formatLikes(n: number) {
  return n >= 1000 ? `${(n / 1000).toFixed(1).replace(/\.0$/, '')}k` : `${n}`;
}

/** One tile of the masonry: where it sits in its column and which rows it waits behind. */
type Tile = { item: GooSighting; fresh: boolean; x: number; y: number; row: number; photo: number };

/**
 * Lays both columns out: the incoming rows on top (the first to arrive nearest the feed), then the
 * feed, each photo going to the shorter column.
 */
function layoutTiles(feed: GooSighting[], incoming: [GooSighting, GooSighting][], width: number) {
  const col = (width - SIDE * 2 - COL_GAP) / 2;
  const tiles: Tile[] = [];
  const heights = [0, 0];
  const rows = [0, 0];
  const prefix = [0];
  const ordered = incoming.slice().reverse();
  for (const pair of ordered) {
    const photo = Math.round(col * SHAPE[pair[0].shape]);
    for (let c = 0; c < 2; c += 1) {
      tiles.push({ item: pair[c], fresh: true, x: SIDE + c * (col + COL_GAP), y: heights[c], row: rows[c], photo });
      heights[c] += photo + TEXT_H + ROW_GAP;
      rows[c] += 1;
    }
    prefix.push(heights[0]);
  }
  for (const item of feed) {
    const c = heights[1] < heights[0] - 0.5 ? 1 : 0;
    const photo = Math.round(col * SHAPE[item.shape]);
    tiles.push({ item, fresh: false, x: SIDE + c * (col + COL_GAP), y: heights[c], row: rows[c], photo });
    heights[c] += photo + TEXT_H + ROW_GAP;
    rows[c] += 1;
  }
  return { col, tiles, prefix, total: Math.max(heights[0], heights[1]) - ROW_GAP };
}

type TileProps = {
  tile: Tile;
  col: number;
  top: number;
  prefix: number[];
  scroll: SharedValue<number>;
  head: SharedValue<number>;
  reducedMotion: boolean;
};

/** One photo tile. Rows above `head` still wait hidden above the feed for a refresh. */
function SightingTile({ tile, col, top, prefix, scroll, head, reducedMotion }: TileProps) {
  const style = useAnimatedStyle(() => {
    const h = head.get();
    // 0 while hidden, 1 once in: each new row takes its turn as the hidden count falls.
    const shown = tile.fresh ? clamp01(tile.row - h + 1) : 1;
    const y = top + scroll.get() + tile.y - hiddenReach(prefix, h);
    return {
      opacity: shown,
      transform: [{ translateY: y }, { scale: reducedMotion ? 1 : 0.92 + 0.08 * shown }],
    };
  });
  const { item } = tile;
  return (
    <Animated.View style={[styles.tile, { left: tile.x, width: col, height: tile.photo + TEXT_H }, style]}>
      <Image
        source={{ uri: unsplash(item.id, Math.round(col * 2.5), Math.round(tile.photo * 2.5)) }}
        style={[styles.photo, { width: col, height: tile.photo }]}
        contentFit="cover"
        transition={250}
      />
      <View style={styles.titleRow}>
        {tile.fresh && <View style={styles.newDot} />}
        <Text style={styles.title} numberOfLines={1}>
          {item.title}
        </Text>
      </View>
      <View style={styles.byline}>
        <View style={styles.avatar}>
          <Text style={styles.initial}>{item.by.charAt(0)}</Text>
        </View>
        <Text style={styles.by} numberOfLines={1}>
          {item.by}
        </Text>
        <SymbolView name={{ ios: 'heart', android: 'favorite', web: 'favorite' }} size={12} tintColor={MUTED} />
        <Text style={styles.likes}>{formatLikes(item.likes)}</Text>
      </View>
    </Animated.View>
  );
}

/**
 * A two-column feed of cat and bird photos under a white header. Pulling the feed down draws a
 * drop of orange liquid out from behind the header's edge on a neck that thins as you pull; past
 * the threshold the neck snaps with a haptic and the drop hangs free, jiggling. Let go and it
 * becomes a gooey loader, three droplets breathing out of a core and back while the trio turns;
 * when the new photos are in, they merge into one drop that the header draws back in as the feed
 * closes, and the new row slides in on top.
 */
export function GooPullRefresh({ sightings = FEED, incoming = INCOMING, autoPlay = false, onRefresh }: Props) {
  const insets = useSafeAreaInsets();
  const reducedMotion = useReducedMotion();
  const auto = autoPlay && !reducedMotion;
  const [size, setSize] = useState<{ width: number; height: number } | null>(null);
  const width = size?.width ?? 0;
  const height = size?.height ?? 0;
  const barBottom = insets.top + HEADER_H;
  const listTop = barBottom + LIST_TOP;
  const cx = width / 2;
  const hidden = incoming.length;
  const layout = useMemo(() => layoutTiles(sightings, incoming, width), [sightings, incoming, width]);
  const { prefix, total } = layout;

  // The feed's offset: 0 at the top, more when pulled down, less when scrolled.
  const scroll = useSharedValue(0);
  const velocity = useSharedValue(0);
  const mode = useSharedValue<'drag' | 'free' | 'script'>('free');
  const dragFrom = useSharedValue(0);
  // How far the feed may rest below the header: 0, or HOLD while refreshing.
  const ceiling = useSharedValue(0);
  // How many rows are still hidden above the feed, and where that is heading.
  const head = useSharedValue(hidden);
  const headTarget = useSharedValue(hidden);
  const headVelocity = useSharedValue(0);

  const phase = useSharedValue(IDLE);
  const phaseTime = useSharedValue(0);
  const men = useSharedValue(MEN_REST);
  const menV = useSharedValue(0);
  const dropY = useSharedValue(0);
  const dropV = useSharedValue(0);
  const dropR = useSharedValue(DROP_R);
  const neck = useSharedValue(0);
  const jiggleAge = useSharedValue(10);
  const spin = useSharedValue(0);
  const grow = useSharedValue(1);
  const byUser = useSharedValue(false);

  const clock = useSharedValue(0);
  const looping = useSharedValue(auto);
  const hold = useSharedValue(0);
  const segment = useSharedValue(-1);
  const segFrom = useSharedValue(0);
  const stage = useSharedValue({ height: 0, barBottom: 0, total: 0 });

  useEffect(() => {
    stage.set({ height, barBottom, total });
  }, [height, barBottom, total, stage]);
  useEffect(() => {
    looping.set(auto);
    clock.set(0);
    hold.set(0);
  }, [auto, clock, hold, looping]);

  const snapped = () => {
    if (process.env.EXPO_OS !== 'web') Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Medium).catch(() => {});
    onRefresh?.();
  };
  const settled = () => {
    if (process.env.EXPO_OS !== 'web') Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
  };

  /** The lowest the feed can scroll, from how many rows are showing. */
  const floorOf = (h: number) => {
    'worklet';
    const { height: screen, barBottom: bar, total: all } = stage.get();
    const content = LIST_TOP + all - hiddenReach(prefix, h) + insets.bottom + 16;
    return Math.min(0, screen - bar - content);
  };

  /** Where a finger's travel puts the feed, with resistance past either end. */
  const bounded = (raw: number) => {
    'worklet';
    const span = stage.get().height * RUBBER_SPAN;
    const top = ceiling.get();
    const bottom = floorOf(head.get());
    if (raw > top) return top + rubber(raw - top, span);
    if (raw < bottom) return bottom - rubber(bottom - raw, span);
    return raw;
  };

  /** The neck breaks: the drop hangs free, jiggling, and the meniscus springs back behind the edge. */
  const snap = (pull: number) => {
    'worklet';
    phase.set(FREE);
    phaseTime.set(0);
    dropY.set(stage.get().barBottom + pull * DROP_TRAVEL);
    dropV.set(reducedMotion ? 0 : SNAP_KICK);
    dropR.set(DROP_R);
    neck.set(1);
    jiggleAge.set(reducedMotion ? 10 : 0);
    if (byUser.get()) scheduleOnRN(snapped);
  };

  /** The finger lets go: a free drop starts the refresh and the feed waits under the header. */
  const release = (v: number) => {
    'worklet';
    velocity.set(v);
    mode.set('free');
    if (phase.get() === FREE) {
      phase.set(LOADING);
      phaseTime.set(0);
      ceiling.set(HOLD);
    }
  };

  /** Puts the arrived rows back above the feed while they are out of sight, so the loop can repeat. */
  const restock = () => {
    'worklet';
    const h = head.get();
    if (h >= hidden - 0.01) return;
    const bar = stage.get().barBottom;
    // The last of them must be above the header: nothing on screen may move.
    const lowest = bar + LIST_TOP + scroll.get() + prefix[hidden] - ROW_GAP - hiddenReach(prefix, h);
    if (lowest > bar - 4) return;
    scroll.set(scroll.get() + prefix[hidden] - hiddenReach(prefix, h));
    head.set(hidden);
    headTarget.set(hidden);
    headVelocity.set(0);
  };

  useFrameCallback((frame) => {
    const dt = Math.min(frame.timeSincePreviousFrame ?? 16, 64) / 1000;
    if (dt <= 0) return;
    const { barBottom: bar, height: screen } = stage.get();
    if (screen <= 0) return;

    // The script stands in for a finger.
    if (mode.get() !== 'drag' && hold.get() > 0) {
      hold.set(hold.get() - dt);
      if (hold.get() <= 0 && looping.get()) {
        restock();
        clock.set(Math.abs(scroll.get()) > 4 ? UP_FROM : 0);
        segment.set(-1);
      }
    } else if (looping.get() && mode.get() !== 'drag') {
      const before = clock.get();
      const t = (before + dt) % LOOP;
      clock.set(t);
      const active = t >= PULL_FROM && t < PULL_TO ? 0 : t >= DOWN_FROM && t < DOWN_TO ? 1 : t >= UP_FROM && t < UP_TO ? 2 : -1;
      if (active !== segment.get()) {
        if (segment.get() === 0) release(0);
        else if (segment.get() >= 1) {
          velocity.set(0);
          mode.set('free');
        }
        if (active >= 0) {
          byUser.set(false);
          segFrom.set(scroll.get());
          mode.set('script');
        }
        segment.set(active);
      }
      if (before < SWAP_AT && t >= SWAP_AT) restock();
      if (active >= 0) {
        let next = scroll.get();
        if (active === 0) next = bounded(PULL_RAW * easeInOut((t - PULL_FROM) / (PULL_TO - PULL_FROM)));
        else if (active === 1) next = segFrom.get() - DOWN_BY * easeOut((t - DOWN_FROM) / (DOWN_TO - DOWN_FROM));
        else next = segFrom.get() * (1 - easeInOut((t - UP_FROM) / (UP_TO - UP_FROM)));
        velocity.set((next - scroll.get()) / dt);
        scroll.set(next);
      }
    }

    // The feed coasts, and springs back from past either end.
    if (mode.get() === 'free') {
      const top = ceiling.get();
      const bottom = floorOf(head.get());
      const s = scroll.get();
      let v = velocity.get();
      if (s > top + 0.01 || s < bottom - 0.01) {
        const edge = s > top ? top : bottom;
        v += (EDGE_STIFFNESS * (edge - s) - EDGE_DAMPING * v) * dt;
        let next = s + v * dt;
        if ((s > top && next < top) || (s < bottom && next > bottom)) {
          next = edge;
          v = 0;
        }
        scroll.set(reducedMotion ? edge : next);
      } else {
        v *= Math.exp(-DECELERATION * dt);
        if (Math.abs(v) < 4) v = 0;
        scroll.set(s + v * dt);
      }
      velocity.set(v);
    }

    // The new row arrives on a spring.
    const hv = headVelocity.get() + (REVEAL_STIFFNESS * (headTarget.get() - head.get()) - REVEAL_DAMPING * headVelocity.get()) * dt;
    headVelocity.set(hv);
    head.set(reducedMotion ? headTarget.get() : head.get() + hv * dt);

    // The liquid.
    const p = phase.get();
    phaseTime.set(phaseTime.get() + dt);
    const gap = Math.max(0, scroll.get());
    if (p === IDLE && gap >= SNAP_PULL && mode.get() !== 'free' && grow.get() >= 1) snap(gap);

    // The meniscus follows the pull while attached, sags to meet a returning drop, else rests.
    let menGoal = MEN_REST;
    if (p === IDLE) menGoal = mix(MEN_REST, MEN_PULL, clamp01(gap / MEN_FULL) * grow.get());
    else if (p === RETURNING) menGoal = MEN_PULL;
    const mv = menV.get() + (MEN_STIFFNESS * (menGoal - men.get()) - MEN_DAMPING * menV.get()) * dt;
    menV.set(mv);
    men.set(reducedMotion ? menGoal : men.get() + mv * dt);

    if (p !== IDLE) {
      // The drop hangs in the middle of the gap, riding up under the header as the gap closes;
      // returning, it is drawn up behind the header's edge.
      const aim = p === RETURNING ? bar - MERGED_R - 10 : dropAim(bar, gap);
      const k = p === RETURNING ? RETURN_STIFFNESS : DROP_STIFFNESS;
      const c = p === RETURNING ? RETURN_DAMPING : DROP_DAMPING;
      const v = dropV.get() + (k * (aim - dropY.get()) - c * dropV.get()) * dt;
      dropV.set(v);
      dropY.set(reducedMotion ? aim : dropY.get() + v * dt);
      neck.set(neck.get() * Math.exp(-NECK_CUT * dt));
      jiggleAge.set(jiggleAge.get() + dt);
    }
    if (p === LOADING || p === MERGING) spin.set((spin.get() + TURN * dt) % 360);
    if (p === LOADING) {
      dropR.set(mix(DROP_R, CORE_R, easeOut(phaseTime.get() / SPREAD_IN)));
      if (phaseTime.get() >= REFRESH_TIME) {
        phase.set(MERGING);
        phaseTime.set(0);
      }
    } else if (p === MERGING) {
      dropR.set(mix(CORE_R, MERGED_R, easeInOut(phaseTime.get() / MERGE_TIME)));
      if (phaseTime.get() >= MERGE_TIME) {
        // The new row is in: the header draws the drop back in.
        phase.set(RETURNING);
        phaseTime.set(0);
        dropV.set(0);
      }
    } else if (p === RETURNING && dropY.get() + dropR.get() < bar + men.get() - 2) {
      // Swallowed: the feed closes and the new row slides in on top.
      phase.set(IDLE);
      phaseTime.set(0);
      grow.set(0);
      ceiling.set(0);
      headTarget.set(Math.max(0, Math.round(headTarget.get()) - 1));
      if (mode.get() === 'free' && velocity.get() > 0) velocity.set(0);
      if (byUser.get()) scheduleOnRN(settled);
    }
    if (phase.get() === IDLE && grow.get() < 1) grow.set(clamp01(grow.get() + dt / REGROW));
  });

  const pan = Gesture.Pan()
    .minDistance(2)
    .onStart(() => {
      hold.set(USER_HOLD);
      byUser.set(true);
      segment.set(-1);
      // Caught where it is; past an edge, the finger takes up the stretch it would have made.
      const s = scroll.get();
      const span = stage.get().height * RUBBER_SPAN;
      const top = ceiling.get();
      const bottom = floorOf(head.get());
      dragFrom.set(s > top ? top + unrubber(s - top, span) : s < bottom ? bottom - unrubber(bottom - s, span) : s);
      mode.set('drag');
    })
    .onUpdate((event) => {
      if (mode.get() !== 'drag') return;
      hold.set(USER_HOLD);
      scroll.set(bounded(dragFrom.get() + event.translationY));
    })
    .onEnd((event) => {
      if (mode.get() !== 'drag') return;
      hold.set(USER_HOLD);
      release(reducedMotion ? 0 : event.velocityY);
    })
    .onFinalize(() => {
      if (mode.get() === 'drag') release(0);
    });

  const picture = useDerivedValue(() => {
    const recorder = Skia.PictureRecorder();
    const canvas = recorder.beginRecording(Skia.XYWHRect(0, 0, Math.max(1, width), Math.max(1, height)));
    const p = phase.get();
    const gap = Math.max(0, scroll.get());
    const m = men.get();
    if (width <= 0 || (p === IDLE && gap < 0.5 && m < MEN_REST + 3)) return recorder.finishRecordingAsPicture();

    const bar = barBottom;
    const liquid = Skia.Paint();
    liquid.setAntiAlias(true);
    liquid.setColor(Skia.Color(ACCENT));
    const glint = Skia.Paint();
    glint.setAntiAlias(true);
    glint.setColor(Skia.Color('rgba(255,255,255,0.55)'));

    // Everything liquid is drawn into one layer that is blurred and then cut at an alpha
    // threshold, so shapes that come close melt together with a smooth neck.
    const goo = Skia.Paint();
    goo.setImageFilter(
      Skia.ImageFilter.MakeColorFilter(Skia.ColorFilter.MakeMatrix(GOO_MATRIX), Skia.ImageFilter.MakeBlur(GOO_BLUR, GOO_BLUR, TileMode.Decal, null), null),
    );
    const reach = Math.max(gap, dropY.get() - bar + DROP_R * 2) + 40;
    canvas.saveLayer(goo, Skia.XYWHRect(cx - 90, bar - 60, 180, reach + 60));
    canvas.drawOval(Skia.XYWHRect(cx - RES_RX, bar + m - RES_RY * 2, RES_RX * 2, RES_RY * 2), liquid);

    let gx = cx;
    let gy = 0;
    let gr = 0;
    if (p === IDLE) {
      const q = clamp01(gap / SNAP_PULL);
      const r = mix(BEAD_R, DROP_R, q) * grow.get();
      if (r > 0.5) {
        const cy = bar + gap * DROP_TRAVEL;
        const w = Math.min(r * 0.85, mix(NECK_WIDE, NECK_THIN, Math.pow(q, 1.2))) * grow.get();
        drawNeck(canvas, liquid, cx, bar + m, cy - r * 0.6, w);
        drawDrop(canvas, liquid, cx, cy, r, 0);
        gy = cy;
        gr = r;
      }
    } else {
      const y = dropY.get();
      const r = dropR.get();
      const age = jiggleAge.get();
      const jiggle = JIGGLE * Math.exp(-JIGGLE_DECAY * age) * Math.sin(2 * Math.PI * JIGGLE_HZ * age);
      const s = reducedMotion ? 0 : Math.max(-0.25, Math.min(0.3, Math.abs(dropV.get()) * STRETCH_PER_SPEED + jiggle));
      if (p === FREE) {
        drawNeck(canvas, liquid, cx, bar + m, y - r * 0.6, NECK_THIN * neck.get());
      } else if (p === RETURNING) {
        // A neck forms again as the drop nears the meniscus, and pulls it in.
        const near = clamp01(1 - (y - r - (bar + m)) / 36);
        drawNeck(canvas, liquid, cx, bar + m, y - r * 0.6, 7 * near);
      }
      drawDrop(canvas, liquid, cx, y, r, p === LOADING || p === MERGING ? 0 : s);
      if (p === LOADING || p === MERGING) {
        // Three droplets breathe out of the core and back while the trio turns.
        const t = phaseTime.get();
        const spread = p === LOADING ? easeOut(t / SPREAD_IN) : 1 - easeInOut(t / MERGE_TIME);
        const breath = reducedMotion ? 0 : 0.5 - 0.5 * Math.cos((2 * Math.PI * (p === LOADING ? t : REFRESH_TIME + t)) / PULSE);
        const orbit = ORBIT * spread * breath;
        for (let k = 0; k < 3; k += 1) {
          const a = ((spin.get() + k * 120) * Math.PI) / 180;
          const sr = SAT_R * spread * (1 - 0.18 * breath);
          if (sr > 0.3) canvas.drawCircle(cx + Math.cos(a) * orbit, y + Math.sin(a) * orbit, sr, liquid);
        }
      }
      gy = y;
      gr = r;
      gx = cx;
    }
    canvas.restore();
    if (gr > 0) drawGlint(canvas, glint, gx, gy, gr);
    return recorder.finishRecordingAsPicture();
  });

  return (
    <GestureDetector gesture={pan}>
      <View
        style={styles.screen}
        accessibilityLabel="Sightings feed. Pull down to refresh."
        onLayout={(event: LayoutChangeEvent) => {
          const { width: w, height: h } = event.nativeEvent.layout;
          setSize({ width: w, height: h });
        }}
      >
        {size &&
          layout.tiles.map((tile, index) => (
            <SightingTile
              key={`${tile.item.id}-${index}`}
              tile={tile}
              col={layout.col}
              top={listTop}
              prefix={prefix}
              scroll={scroll}
              head={head}
              reducedMotion={reducedMotion}
            />
          ))}
        <Canvas style={StyleSheet.absoluteFill} pointerEvents="none">
          <Picture picture={picture} />
        </Canvas>
        <View pointerEvents="none" style={[styles.header, { height: barBottom, paddingTop: insets.top }]}>
          <View style={styles.titleBar}>
            <Text style={styles.heading}>Sightings</Text>
            <View style={styles.bell}>
              <SymbolView name={{ ios: 'bell', android: 'notifications', web: 'notifications' }} size={18} tintColor={INK} />
            </View>
          </View>
          <View style={styles.chips}>
            {CHIPS.map((chip, i) => (
              <View key={chip} style={[styles.chip, i === 0 && styles.chipOn]}>
                <Text style={[styles.chipText, i === 0 && styles.chipTextOn]}>{chip}</Text>
              </View>
            ))}
          </View>
        </View>
      </View>
    </GestureDetector>
  );
}

const styles = StyleSheet.create({
  screen: {
    flex: 1,
    overflow: 'hidden',
    backgroundColor: PAGE,
  },
  header: {
    position: 'absolute',
    left: 0,
    right: 0,
    top: 0,
    backgroundColor: HEADER,
    borderBottomWidth: StyleSheet.hairlineWidth,
    borderBottomColor: 'rgba(27,26,24,0.12)',
  },
  titleBar: {
    height: 52,
    paddingHorizontal: 18,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  heading: {
    color: INK,
    fontSize: 30,
    fontWeight: '700',
    letterSpacing: -0.6,
  },
  bell: {
    width: 38,
    height: 38,
    borderRadius: 19,
    backgroundColor: CHIP,
    alignItems: 'center',
    justifyContent: 'center',
  },
  chips: {
    height: 50,
    paddingHorizontal: 18,
    paddingTop: 6,
    flexDirection: 'row',
    gap: 8,
    overflow: 'hidden',
  },
  chip: {
    height: 34,
    paddingHorizontal: 15,
    borderRadius: 17,
    backgroundColor: CHIP,
    justifyContent: 'center',
  },
  chipOn: {
    backgroundColor: INK,
  },
  chipText: {
    color: INK,
    fontSize: 14,
    fontWeight: '600',
  },
  chipTextOn: {
    color: '#ffffff',
  },
  tile: {
    position: 'absolute',
    top: 0,
  },
  photo: {
    borderRadius: 16,
    borderCurve: 'continuous',
    backgroundColor: PHOTO_BLANK,
  },
  titleRow: {
    marginTop: 8,
    height: 18,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
  },
  newDot: {
    width: 7,
    height: 7,
    borderRadius: 3.5,
    backgroundColor: ACCENT,
  },
  title: {
    flex: 1,
    color: INK,
    fontSize: 14,
    fontWeight: '600',
    letterSpacing: -0.1,
  },
  byline: {
    marginTop: 3,
    height: 17,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 5,
  },
  avatar: {
    width: 17,
    height: 17,
    borderRadius: 8.5,
    backgroundColor: AVATAR,
    alignItems: 'center',
    justifyContent: 'center',
  },
  initial: {
    color: INK,
    fontSize: 9,
    fontWeight: '700',
  },
  by: {
    flex: 1,
    color: MUTED,
    fontSize: 12,
    fontWeight: '500',
  },
  likes: {
    color: MUTED,
    fontSize: 12,
    fontWeight: '500',
    fontVariant: ['tabular-nums'],
  },
});
