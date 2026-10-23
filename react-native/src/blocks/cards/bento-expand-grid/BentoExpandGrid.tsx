import { Canvas, createPicture, PaintStyle, Picture, type SkCanvas, Skia, StrokeCap } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { Image } from 'expo-image';
import { LinearGradient } from 'expo-linear-gradient';
import { type ReactNode, useEffect, useMemo, useState } from 'react';
import { type LayoutChangeEvent, Pressable, StyleSheet, Text, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  Easing,
  type SharedValue,
  scrollTo,
  useAnimatedReaction,
  useAnimatedRef,
  useAnimatedScrollHandler,
  useAnimatedStyle,
  useDerivedValue,
  useFrameCallback,
  useReducedMotion,
  useSharedValue,
  withSpring,
  withTiming,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import Svg, { Path } from 'react-native-svg';
import { scheduleOnRN } from 'react-native-worklets';

import { type Face, GRID_COLUMNS, GRID_ROWS, type Note, NOTES, photoUrl } from './BentoExpandGridNotes';

type Props = {
  /** The seven notes on the grid, each with its place on a 4 × 4 grid. */
  notes?: Note[];
  /** Opens a tile, scrolls its page a little, closes it, opens another, and starts over. */
  autoPlay?: boolean;
  /** The paper behind the grid and the pages. */
  background?: string;
  ink?: string;
  /** The one accent: kickers, the map's pin, the stat's dot and the pull quote's rule. */
  accent?: string;
};

const SERIF = process.env.EXPO_OS === 'ios' ? 'Georgia' : 'serif';
const MUTED = '#8b8173';
const STAT_PAPER = '#1f1c18';
const QUOTE_PAPER = '#e5dbc9';
const MAP_PAPER = '#e9e1d1';
const PHOTO_PAPER = '#ddd3c2';

// The grid: margins and gaps in pt, the corner radius of a tile, the title block above it.
const MARGIN = 16;
const GAP = 10;
const TILE_RADIUS = 22;
const TITLE_BLOCK = 92;
// Tiles narrower than this set their caption smaller, on two lines.
const NARROW = 130;
// Photos: the grid and the page's header share one copy; the pair inside the page is smaller.
const HEADER_PX = 1200;
const PAIR_PX = 600;

// The page: its header takes HEADER_SHARE of the screen; the sheet below overlaps it by OVERLAP
// and the header slides up at PARALLAX of the scroll, so the sheet glides over the photo.
const HEADER_SHARE = 0.48;
const OVERLAP = 28;
const PARALLAX = 0.45;
// A stat or quote face is drawn at its page size and scaled down into its tile, so its type
// stays sharp: up to FACE_GROW times the tile's size, never wider than the screen.
const FACE_GROW = 1.75;

// Opening: the tile's frame grows to the screen on a spring, its corners closing to 0. The other
// tiles are pushed away from it by PUSH pt, shrink by SHRINK and fade, nearest first, each
// starting STAGGER later. The page's sections rise REVEAL_RISE pt as they fade in, one by one.
const OPEN_SPRING = { duration: 800, dampingRatio: 0.88 };
const CLOSE_SPRING = { duration: 680, dampingRatio: 0.9 };
const PUSH = 44;
const SHRINK = 0.1;
const STAGGER = 0.07;
const REVEAL_FROM = 0.42;
const REVEAL_STEP = 0.06;
const REVEAL_SPAN = 0.3;
const REVEAL_RISE = 18;
// The sheet's paper comes in quickly, early in the flight, so it never sits half-seen over a dark tile.
const SHEET_FROM = 0.22;
const SHEET_TO = 0.42;
// The header fills the frame until HEADER_GIVE_FROM of the way, and is down to its own height by
// HEADER_GIVE_TO, the sheet sliding up under it; so a closing page never shows an empty card.
const HEADER_GIVE_FROM = 0.2;
const HEADER_GIVE_TO = 0.8;
const FADE_TIME = 220;

// Closing by hand: the page follows a drag down, shrinking by up to DISMISS_SHRINK over
// DISMISS_SPAN of the screen and rounding its corners to DISMISS_RADIUS, the grid peeking back
// in behind it. Past DISMISS_DISTANCE or DISMISS_SPEED it closes into its tile.
const DISMISS_SPAN = 0.5;
const DISMISS_SHRINK = 0.34;
const DISMISS_RADIUS = 36;
const DISMISS_DISTANCE = 110;
const DISMISS_SPEED = 800;
const FOLLOW_X = 0.6;
const FOLLOW_Y = 0.75;
const PEEK = 0.8;
const PEEK_SPAN = 0.25;
// The page lifts off the paper on a soft shadow while it flies or is dragged.
const LIFT_SHADOW = '0px 18px 44px rgba(31, 28, 24, 0.22)';
const DRAG_BACK = { duration: 420, dampingRatio: 0.82 };
// Press feedback on a tile.
const PRESS_SCALE = 0.97;
const PRESS_TIME = 120;

// autoPlay loop (seconds): the big photo opens, its page scrolls down and back, it is dragged
// down and closes into its tile; the stat opens, scrolls, closes from its button; a rest.
const LOOP = 14;
const OPEN_A = 1.0;
const SCROLL_A = 2.3;
const BACK_A = 3.9;
const DRAG_A = 5.0;
const CLOSE_A = 5.5;
const OPEN_B = 7.1;
const SCROLL_B = 8.5;
const BACK_B = 10.0;
const CLOSE_B = 11.0;
const NOTE_A = 0;
const NOTE_B = 1;
const SCROLL_DEPTH = 240;
const SCROLL_TIME = 1100;
const BACK_TIME = 800;
const DRAG_PULL = 150;
// After a touch the loop waits this long; longer while a page is open, so it can be read.
const USER_HOLD = 3;
const PAGE_HOLD = 7;

const EASE_IN_OUT = Easing.bezier(0.77, 0, 0.175, 1);

type Rect = { x: number; y: number; w: number; h: number };

function clamp01(x: number) {
  'worklet';
  return Math.min(1, Math.max(0, x));
}

function smooth(from: number, to: number, x: number) {
  'worklet';
  const u = clamp01((x - from) / (to - from));
  return u * u * (3 - 2 * u);
}

function mix(a: number, b: number, t: number) {
  'worklet';
  return a + (b - a) * t;
}

/** True when the clock went past `at` between `before` and `now`, wrapping round the loop. */
function passedAt(before: number, now: number, at: number) {
  'worklet';
  return (before < at && now >= at) || (now < before && (at > before || at <= now));
}

/** How far a drag down has pulled the page, 0…1. */
function pullOf(dy: number, height: number) {
  'worklet';
  return clamp01(dy / (height * DISMISS_SPAN));
}

/** How far the grid has come back in behind a page dragged down, 0…1. */
function peekOf(dy: number, height: number) {
  'worklet';
  return clamp01(dy / (height * PEEK_SPAN));
}

/** The page's frame: between its tile (p = 0) and the screen (p = 1), shrunk by a drag down. */
function frameOf(tile: Rect, p: number, dx: number, dy: number, width: number, height: number) {
  'worklet';
  const pull = pullOf(dy, height);
  const s = 1 - DISMISS_SHRINK * pull;
  const fw = width * s;
  const fh = height * s;
  const fx = (width - fw) / 2 + dx * FOLLOW_X;
  const fy = Math.max(0, dy) * FOLLOW_Y;
  const fr = DISMISS_RADIUS * clamp01(pull * 4);
  return {
    x: mix(tile.x, fx, p),
    y: mix(tile.y, fy, p),
    w: Math.max(1, mix(tile.w, fw, p)),
    h: Math.max(1, mix(tile.h, fh, p)),
    r: Math.max(0, mix(TILE_RADIUS, fr, p)),
    s,
  };
}

/** How big a stat or quote face is drawn, relative to its tile. */
function growOf(tile: Rect, width: number) {
  return Math.min(FACE_GROW, width / tile.w);
}

/** The tiles on the grid, from the space between the title block and the bottom edge. */
function layoutGrid(notes: Note[], width: number, top: number, bottom: number): Rect[] {
  const colW = (width - MARGIN * 2 - GAP * (GRID_COLUMNS - 1)) / GRID_COLUMNS;
  const rowH = (bottom - top - GAP * (GRID_ROWS - 1)) / GRID_ROWS;
  return notes.map(({ span }) => ({
    x: MARGIN + span.col * (colW + GAP),
    y: top + span.row * (rowH + GAP),
    w: span.cols * colW + (span.cols - 1) * GAP,
    h: span.rows * rowH + (span.rows - 1) * GAP,
  }));
}

/**
 * For every pair of tiles: which way the second is pushed when the first opens (a unit vector
 * from centre to centre) and how late it starts, nearest first.
 */
function pushPlan(rects: Rect[]) {
  return rects.map((from) => {
    const fx = from.x + from.w / 2;
    const fy = from.y + from.h / 2;
    const away = rects.map((to) => {
      const dx = to.x + to.w / 2 - fx;
      const dy = to.y + to.h / 2 - fy;
      const d = Math.hypot(dx, dy) || 1;
      return { x: dx / d, y: dy / d, d };
    });
    const order = away.map((a, i) => ({ d: a.d, i })).sort((a, b) => a.d - b.d);
    const delay = rects.map(() => 0);
    order.forEach(({ i }, rank) => {
      delay[i] = Math.max(0, rank - 1) * STAGGER;
    });
    return away.map((a, i) => ({ x: a.x, y: a.y, delay: delay[i] }));
  });
}

/** The little map: streets on a grid, a river, a dashed walk joining four stops, a pin. */
function drawMap(canvas: SkCanvas, w: number, h: number, ink: string, accent: string) {
  'worklet';
  // The map is drawn on a 400 × 300 board, scaled to cover the box; lines keep their width.
  const k = Math.max(w / 400, h / 300);
  const ox = (w - 400 * k) / 2;
  const oy = (h - 300 * k) / 2;
  const X = (x: number) => ox + x * k;
  const Y = (y: number) => oy + y * k;
  const paint = Skia.Paint();
  paint.setAntiAlias(true);
  paint.setStyle(PaintStyle.Stroke);
  paint.setColor(Skia.Color(ink));
  paint.setAlphaf(0.12);
  paint.setStrokeWidth(1);
  for (let x = -40; x <= 440; x += 34) {
    canvas.drawLine(X(x), Y(-20), X(x + 46), Y(320), paint);
  }
  for (let y = -20; y <= 320; y += 30) {
    canvas.drawLine(X(-20), Y(y), X(420), Y(y - 18), paint);
  }
  // Two main roads.
  paint.setAlphaf(0.28);
  paint.setStrokeWidth(2.2);
  canvas.drawLine(X(-20), Y(96), X(420), Y(70), paint);
  canvas.drawLine(X(250), Y(-20), X(290), Y(320), paint);
  // The river.
  const river = Skia.PathBuilder.Make()
    .moveTo(X(-20), Y(250))
    .cubicTo(X(90), Y(210), X(120), Y(120), X(210), Y(150))
    .cubicTo(X(300), Y(180), X(330), Y(40), X(420), Y(20))
    .build();
  paint.setColor(Skia.Color('#8fb2bf'));
  paint.setAlphaf(0.7);
  paint.setStrokeWidth(9);
  paint.setStrokeCap(StrokeCap.Round);
  canvas.drawPath(river, paint);
  // The walk, dashed, with its stops.
  const stops = [
    [70, 70],
    [150, 120],
    [235, 205],
    [330, 130],
  ];
  const route = Skia.PathBuilder.Make().moveTo(X(stops[0][0]), Y(stops[0][1]));
  for (let i = 1; i < stops.length; i += 1) route.lineTo(X(stops[i][0]), Y(stops[i][1]));
  const walk = route.build();
  const dash = Skia.Paint();
  dash.setAntiAlias(true);
  dash.setStyle(PaintStyle.Stroke);
  dash.setColor(Skia.Color(ink));
  dash.setAlphaf(0.8);
  dash.setStrokeWidth(1.6);
  dash.setPathEffect(Skia.PathEffect.MakeDash([4, 4], 0));
  canvas.drawPath(walk, dash);
  const dot = Skia.Paint();
  dot.setAntiAlias(true);
  for (let i = 0; i < stops.length - 1; i += 1) {
    dot.setColor(Skia.Color(MAP_PAPER));
    canvas.drawCircle(X(stops[i][0]), Y(stops[i][1]), 5, dot);
    dot.setColor(Skia.Color(ink));
    canvas.drawCircle(X(stops[i][0]), Y(stops[i][1]), 3, dot);
  }
  // The pin on the last stop, with a soft halo.
  const [px, py] = stops[stops.length - 1];
  dot.setColor(Skia.Color(accent));
  dot.setAlphaf(0.18);
  canvas.drawCircle(X(px), Y(py), 14, dot);
  dot.setAlphaf(1);
  canvas.drawCircle(X(px), Y(py), 6, dot);
  dot.setColor(Skia.Color('#ffffff'));
  canvas.drawCircle(X(px), Y(py), 2.2, dot);
}

const haptic = {
  open: () => {
    if (process.env.EXPO_OS !== 'web') Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
  },
};

/**
 * A bento of seven field notes on warm paper: photos, a reading stat, a quote and a little map.
 * Tapping a tile grows it in place into its page while the other tiles are pushed away; the
 * photo becomes the page's header and slides under the text as it scrolls. Dragging the page
 * down shrinks it under the finger; letting go puts it back in its tile.
 */
export function BentoExpandGrid({
  notes = NOTES,
  autoPlay = false,
  background = '#f1ebe0',
  ink = '#1f1c18',
  accent = '#c4582f',
}: Props) {
  const insets = useSafeAreaInsets();
  const reducedMotion = useReducedMotion();
  const auto = autoPlay && !reducedMotion;
  const [size, setSize] = useState<{ width: number; height: number } | null>(null);
  const [openIndex, setOpenIndex] = useState<number | null>(null);

  const width = size?.width ?? 0;
  const height = size?.height ?? 0;
  const rects = useMemo(
    () => (size ? layoutGrid(notes, width, insets.top + TITLE_BLOCK, height - insets.bottom - MARGIN) : []),
    [size, notes, width, height, insets.top, insets.bottom],
  );
  const plan = useMemo(() => pushPlan(rects), [rects]);
  const headerH = Math.round(height * HEADER_SHARE);

  const stage = useSharedValue({ width: 0, height: 0 });
  const tiles = useSharedValue<Rect[]>([]);
  // The page: 0 in its tile, 1 full screen; which tile is open (-1 for none).
  const open = useSharedValue(0);
  const active = useSharedValue(-1);
  const closing = useSharedValue(false);
  const dragX = useSharedValue(0);
  const dragY = useSharedValue(0);
  const dragging = useSharedValue(false);
  const scrollY = useSharedValue(0);
  const scrollRef = useAnimatedRef<Animated.ScrollView>();
  // autoPlay.
  const autoScroll = useSharedValue(0);
  const clock = useSharedValue(0);
  const looping = useSharedValue(auto);
  const hold = useSharedValue(0);

  useEffect(() => {
    tiles.set(rects);
  }, [rects, tiles]);

  useEffect(() => {
    looping.set(auto);
    clock.set(0);
    hold.set(0);
  }, [auto, clock, hold, looping]);

  // The pages' photo pairs are fetched ahead, so a page never opens onto empty frames.
  useEffect(() => {
    Image.prefetch(
      notes.flatMap((n) => n.pair.map((id) => photoUrl(id, PAIR_PX))),
      'memory-disk',
    ).catch(() => {});
  }, [notes]);

  // The page mounts in its tile at 0, then grows on the next frame.
  useEffect(() => {
    if (openIndex === null) return;
    open.set(reducedMotion ? withTiming(1, { duration: FADE_TIME }) : withSpring(1, OPEN_SPRING));
  }, [openIndex, open, reducedMotion]);

  const openNote = (index: number, user: boolean) => {
    if (active.get() >= 0 || index < 0 || index >= notes.length) return;
    active.set(index);
    closing.set(false);
    open.set(0);
    dragX.set(0);
    dragY.set(0);
    scrollY.set(0);
    autoScroll.set(0);
    if (user) {
      hold.set(PAGE_HOLD);
      haptic.open();
    }
    setOpenIndex(index);
  };

  const ended = () => setOpenIndex(null);

  // Worklet helpers are named function declarations, defined before the worklets that call them.
  function closeNote(user: boolean, velocity: number) {
    'worklet';
    if (active.get() < 0 || closing.get()) return;
    closing.set(true);
    const done = (finished?: boolean) => {
      'worklet';
      // Cut short (the page was sent open again): it can still be closed later, never stuck.
      if (!finished) {
        closing.set(false);
        return;
      }
      active.set(-1);
      closing.set(false);
      dragX.set(0);
      dragY.set(0);
      scheduleOnRN(ended);
    };
    open.set(
      reducedMotion
        ? withTiming(0, { duration: FADE_TIME }, done)
        : withSpring(0, { ...CLOSE_SPRING, velocity: -velocity }, done),
    );
    if (user) scheduleOnRN(haptic.open);
  }

  function touched() {
    'worklet';
    hold.set(active.get() >= 0 ? PAGE_HOLD : USER_HOLD);
  }

  useFrameCallback((frame) => {
    const dt = Math.min(frame.timeSincePreviousFrame ?? 16, 64) / 1000;
    if (dt <= 0 || stage.get().width === 0) return;
    if (hold.get() > 0) {
      if (!dragging.get()) hold.set(hold.get() - dt);
      if (hold.get() <= 0) {
        // The loop starts over from the grid: a page left open closes first.
        clock.set(0);
        if (looping.get()) closeNote(false, 0);
      }
      return;
    }
    if (!looping.get()) return;
    const before = clock.get();
    const t = (before + dt) % LOOP;
    clock.set(t);
    if (passedAt(before, t, OPEN_A)) scheduleOnRN(openNote, NOTE_A, false);
    if (passedAt(before, t, OPEN_B)) scheduleOnRN(openNote, NOTE_B, false);
    if (passedAt(before, t, SCROLL_A) || passedAt(before, t, SCROLL_B)) {
      autoScroll.set(withTiming(SCROLL_DEPTH, { duration: SCROLL_TIME, easing: EASE_IN_OUT }));
    }
    if (passedAt(before, t, BACK_A) || passedAt(before, t, BACK_B)) {
      autoScroll.set(withTiming(0, { duration: BACK_TIME, easing: EASE_IN_OUT }));
    }
    if (passedAt(before, t, DRAG_A) && active.get() >= 0) {
      dragY.set(withTiming(DRAG_PULL, { duration: 460, easing: EASE_IN_OUT }));
      dragX.set(withTiming(-14, { duration: 460, easing: EASE_IN_OUT }));
    }
    if (passedAt(before, t, CLOSE_A)) closeNote(false, 1.2);
    if (passedAt(before, t, CLOSE_B)) closeNote(false, 0);
  });

  // The loop scrolls the open page by moving the scroll view itself.
  useAnimatedReaction(
    () => autoScroll.get(),
    (y, last) => {
      if (last === null || y === last || hold.get() > 0 || active.get() < 0) return;
      scrollTo(scrollRef, 0, y, false);
    },
  );

  const onScroll = useAnimatedScrollHandler({
    onScroll: (event) => {
      scrollY.set(event.contentOffset.y);
    },
    onBeginDrag: () => {
      touched();
    },
  });

  const nativeScroll = Gesture.Native();
  // Dragging the page down from the top of its scroll shrinks it under the finger.
  const dismiss = Gesture.Pan()
    .activeOffsetY(10)
    .failOffsetX([-24, 24])
    .simultaneousWithExternalGesture(nativeScroll)
    .onStart(() => {
      if (active.get() < 0 || closing.get() || scrollY.get() > 2) return;
      dragging.set(true);
      touched();
    })
    .onUpdate((event) => {
      if (!dragging.get() || reducedMotion) return;
      dragX.set(event.translationX);
      dragY.set(Math.max(0, event.translationY));
    })
    .onEnd((event) => {
      if (!dragging.get()) return;
      const { height: h } = stage.get();
      if (event.translationY > DISMISS_DISTANCE || event.velocityY > DISMISS_SPEED) {
        closeNote(true, Math.max(0, event.velocityY) / (h * 0.6));
      } else {
        dragX.set(withSpring(0, { ...DRAG_BACK, velocity: event.velocityX }));
        dragY.set(withSpring(0, { ...DRAG_BACK, velocity: event.velocityY }));
      }
    })
    .onFinalize(() => {
      dragging.set(false);
    });

  // The grid's title block fades as a page covers it, and back as the page is dragged down.
  const titleStyle = useAnimatedStyle(() => {
    const { height: h } = stage.get();
    const covered = active.get() < 0 ? 0 : clamp01(open.get()) * (1 - PEEK * peekOf(dragY.get(), h));
    return { opacity: 1 - covered };
  });

  const note = openIndex === null ? null : notes[openIndex];

  return (
    <View
      style={[styles.screen, { backgroundColor: background }]}
      onLayout={(event: LayoutChangeEvent) => {
        const box = event.nativeEvent.layout;
        // A zero first layout would give tiles negative sizes and the page a 0 width to divide by.
        if (box.width <= 0 || box.height <= 0) return;
        setSize({ width: box.width, height: box.height });
        stage.set({ width: box.width, height: box.height });
      }}
    >
      <Animated.View
        accessibilityElementsHidden={openIndex !== null}
        style={[styles.titleBlock, { top: insets.top + 14 }, titleStyle]}
      >
        <View>
          <Text style={[styles.kicker, { color: accent }]}>FIELD NOTES · NO. 07</Text>
          <Text style={[styles.gridTitle, { color: ink }]}>Slow crafts</Text>
        </View>
        <Text style={styles.issue}>Autumn{'\n'}2026</Text>
      </Animated.View>

      {size &&
        notes.map((n, i) => (
          <Tile
            key={n.id}
            note={n}
            index={i}
            rect={rects[i]}
            plan={plan}
            width={width}
            ink={ink}
            accent={accent}
            open={open}
            active={active}
            dragY={dragY}
            height={height}
            reducedMotion={reducedMotion}
            onPress={() => {
              touched();
              openNote(i, true);
            }}
          />
        ))}

      {note && openIndex !== null && size && (
        <GestureDetector gesture={dismiss}>
          <View pointerEvents="box-none" style={StyleSheet.absoluteFill}>
            <Page
            note={note}
            tile={rects[openIndex]}
            width={width}
            height={height}
            headerH={headerH}
            insets={insets}
            background={background}
            ink={ink}
            accent={accent}
            open={open}
            dragX={dragX}
            dragY={dragY}
            scrollY={scrollY}
            scrollRef={scrollRef}
            onScroll={onScroll}
            nativeScroll={nativeScroll}
            reducedMotion={reducedMotion}
            index={openIndex}
            count={notes.length}
            onClose={() => {
              touched();
              closeNote(true, 0);
              }}
            />
          </View>
        </GestureDetector>
      )}
    </View>
  );
}

type TileProps = {
  note: Note;
  index: number;
  rect: Rect;
  plan: { x: number; y: number; delay: number }[][];
  width: number;
  height: number;
  ink: string;
  accent: string;
  open: SharedValue<number>;
  active: SharedValue<number>;
  dragY: SharedValue<number>;
  reducedMotion: boolean;
  onPress: () => void;
};

/** One tile on the grid: pushed away, shrunk and faded while another tile is open. */
function Tile({ note, index, rect, plan, width, height, ink, accent, open, active, dragY, reducedMotion, onPress }: TileProps) {
  const pressed = useSharedValue(1);
  const style = useAnimatedStyle(() => {
    const k = active.get();
    if (k < 0) return { opacity: 1, transform: [{ translateX: 0 }, { translateY: 0 }, { scale: 1 }] };
    if (k === index) return { opacity: 0, transform: [{ translateX: 0 }, { translateY: 0 }, { scale: 1 }] };
    const move = plan[k][index];
    const p = clamp01(open.get()) * (1 - PEEK * peekOf(dragY.get(), height));
    const maxDelay = STAGGER * (plan.length - 2);
    const q = smooth(move.delay, move.delay + 1 - maxDelay, p);
    const push = reducedMotion ? 0 : PUSH * q;
    return {
      opacity: 1 - q,
      transform: [
        { translateX: move.x * push },
        { translateY: move.y * push },
        { scale: reducedMotion ? 1 : 1 - SHRINK * q },
      ],
    };
  });
  const pressStyle = useAnimatedStyle(() => ({ transform: [{ scale: pressed.get() }] }));
  const label = note.face.kind === 'photo' ? `${note.kicker}: ${note.title}` : note.title;
  return (
    <Animated.View style={[styles.tile, { left: rect.x, top: rect.y, width: rect.w, height: rect.h }, style]}>
      <Pressable
        accessibilityRole="button"
        accessibilityLabel={label}
        onPressIn={() => pressed.set(withTiming(PRESS_SCALE, { duration: PRESS_TIME }))}
        onPressOut={() => pressed.set(withTiming(1, { duration: PRESS_TIME * 1.6 }))}
        onPress={onPress}
        style={StyleSheet.absoluteFill}
      >
        <Animated.View style={[styles.tileClip, { backgroundColor: paperOf(note.face) }, pressStyle]}>
          <TileFace note={note} rect={rect} width={width} ink={ink} accent={accent} />
        </Animated.View>
      </Pressable>
    </Animated.View>
  );
}

function paperOf(face: Face) {
  switch (face.kind) {
    case 'photo':
      return PHOTO_PAPER;
    case 'stat':
      return STAT_PAPER;
    case 'quote':
      return QUOTE_PAPER;
    case 'map':
      return MAP_PAPER;
  }
}

/** What a tile shows at rest: the same drawing the page's header grows from. */
function TileFace({ note, rect, width, ink, accent }: { note: Note; rect: Rect; width: number; ink: string; accent: string }) {
  const face = note.face;
  if (face.kind === 'photo') {
    return (
      <>
        <Image
          style={StyleSheet.absoluteFill}
          source={{ uri: photoUrl(face.photo, HEADER_PX) }}
          contentFit="cover"
          cachePolicy="memory-disk"
          transition={300}
        />
        <PhotoLabel note={note} narrow={rect.w < NARROW} />
      </>
    );
  }
  if (face.kind === 'map') {
    return <MapFace rect={rect} ink={ink} accent={accent} place={face.place} stops={face.stops} />;
  }
  const grow = growOf(rect, width);
  return (
    <View
      style={{
        position: 'absolute',
        left: 0,
        top: 0,
        width: rect.w * grow,
        height: rect.h * grow,
        transformOrigin: 'left top',
        transform: [{ scale: 1 / grow }],
      }}
    >
      <TypeFace face={face} grow={grow} ink={ink} accent={accent} />
    </View>
  );
}

/** A photo tile's caption, on a soft shade at its foot; a narrow tile sets it smaller, on two lines. */
function PhotoLabel({ note, narrow }: { note: Note; narrow: boolean }) {
  return (
    <View pointerEvents="none" style={[styles.photoLabel, narrow && styles.photoLabelNarrow]}>
      <LinearGradient colors={['rgba(20,17,13,0)', 'rgba(20,17,13,0.55)']} style={StyleSheet.absoluteFill} />
      <Text style={styles.photoKicker}>{note.kicker.toUpperCase()}</Text>
      <Text numberOfLines={2} style={[styles.photoTitle, narrow && styles.photoTitleNarrow]}>
        {note.title}
      </Text>
    </View>
  );
}

/** The stat and the quote, set at `grow` times their tile's size. */
function TypeFace({ face, grow, ink, accent }: { face: Face; grow: number; ink: string; accent: string }) {
  const pad = 14 * grow;
  if (face.kind === 'stat') {
    return (
      <View style={{ flex: 1, padding: pad, justifyContent: 'space-between' }}>
        <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' }}>
          <Text style={{ fontSize: 10 * grow, fontWeight: '600', letterSpacing: 1.4 * grow, color: 'rgba(241,235,224,0.6)' }}>
            {face.label.toUpperCase()}
          </Text>
          <View style={{ width: 7 * grow, height: 7 * grow, borderRadius: 4 * grow, backgroundColor: accent }} />
        </View>
        <View style={{ flexDirection: 'row', alignItems: 'baseline', gap: 6 * grow }}>
          <Text style={{ fontFamily: SERIF, fontSize: 40 * grow, color: '#f1ebe0', letterSpacing: -1 * grow, fontVariant: ['tabular-nums'] }}>
            {face.value}
          </Text>
          <Text style={{ fontSize: 13 * grow, fontWeight: '500', color: 'rgba(241,235,224,0.7)' }}>{face.unit}</Text>
        </View>
      </View>
    );
  }
  if (face.kind === 'quote') {
    return (
      <View style={{ flex: 1, padding: pad, justifyContent: 'space-between' }}>
        <Text style={{ fontFamily: SERIF, fontSize: 18 * grow, lineHeight: 23 * grow, fontStyle: 'italic', color: ink }}>
          <Text style={{ color: accent }}>“</Text>
          {face.text}
        </Text>
        <Text style={{ fontSize: 10 * grow, fontWeight: '600', letterSpacing: 1.2 * grow, color: MUTED }}>
          {face.by.toUpperCase()}
        </Text>
      </View>
    );
  }
  return null;
}

/** The map tile at rest: the drawing and its caption. */
function MapFace({ rect, ink, accent, place, stops }: { rect: Rect; ink: string; accent: string; place: string; stops: number }) {
  const picture = useMemo(
    () => createPicture((canvas) => drawMap(canvas, rect.w, rect.h, ink, accent), { width: rect.w, height: rect.h }),
    [rect.w, rect.h, ink, accent],
  );
  return (
    <>
      <Canvas style={StyleSheet.absoluteFill}>
        <Picture picture={picture} />
      </Canvas>
      <MapCaption place={place} stops={stops} ink={ink} />
    </>
  );
}

function MapCaption({ place, stops, ink }: { place: string; stops: number; ink: string }) {
  return (
    <View pointerEvents="none" style={styles.mapCaption}>
      <Text style={[styles.mapPlace, { color: ink }]}>{place}</Text>
      <Text style={styles.mapStops}>{stops} stops</Text>
    </View>
  );
}

type PageProps = {
  note: Note;
  tile: Rect;
  width: number;
  height: number;
  headerH: number;
  insets: { top: number; bottom: number };
  background: string;
  ink: string;
  accent: string;
  open: SharedValue<number>;
  dragX: SharedValue<number>;
  dragY: SharedValue<number>;
  scrollY: SharedValue<number>;
  scrollRef: ReturnType<typeof useAnimatedRef<Animated.ScrollView>>;
  onScroll: ReturnType<typeof useAnimatedScrollHandler>;
  nativeScroll: ReturnType<typeof Gesture.Native>;
  reducedMotion: boolean;
  index: number;
  count: number;
  onClose: () => void;
};

/**
 * The open page. Its frame is one clipped box between the tile and the screen; inside it the
 * header (photo, map or type) fills the top and the sheet of text scrolls over it.
 */
function Page({
  note,
  tile,
  width,
  height,
  headerH,
  insets,
  background,
  ink,
  accent,
  open,
  dragX,
  dragY,
  scrollY,
  scrollRef,
  onScroll,
  nativeScroll,
  reducedMotion,
  index,
  count,
  onClose,
}: PageProps) {
  const full: Rect = { x: 0, y: 0, w: width, h: height };
  const from = reducedMotion ? full : tile;
  const face = note.face;
  const grow = growOf(tile, width);

  const frame = useDerivedValue(() => {
    const p = Math.min(1, open.get());
    return frameOf(from, p, dragX.get(), dragY.get(), width, height);
  });
  /** The header's height: the tile's at first, the header's (shrunk with the frame) once open. */
  const header = useDerivedValue(() => {
    // The face fills the whole frame for the first part of the flight, then gives way to the sheet.
    const f = frame.get();
    return mix(f.h, headerH * f.s, smooth(HEADER_GIVE_FROM, HEADER_GIVE_TO, open.get()));
  });

  const box = useAnimatedStyle(() => {
    const f = frame.get();
    return {
      left: f.x,
      top: f.y,
      width: f.w,
      height: f.h,
      borderRadius: f.r,
      opacity: reducedMotion ? clamp01(open.get()) : 1,
    };
  });
  const headerStyle = useAnimatedStyle(() => ({
    height: header.get(),
    transform: [{ translateY: reducedMotion ? 0 : -Math.max(0, scrollY.get()) * PARALLAX * clamp01(open.get()) }],
  }));
  const labelStyle = useAnimatedStyle(() => ({ opacity: 1 - smooth(0, 0.3, open.get()) }));
  // The stat and quote grow from their tile's size, anchored at the header's foot.
  const typeStyle = useAnimatedStyle(() => {
    const p = Math.min(1, open.get());
    const scale = mix(1 / grow, frame.get().s, p);
    // Lifted clear of the sheet's rounded top once open.
    const lift = OVERLAP * 0.8 * smooth(HEADER_GIVE_FROM, HEADER_GIVE_TO, open.get());
    return {
      transform: [{ translateY: header.get() - tile.h * grow * scale - lift }, { scale }],
    };
  });
  const mapPicture = useDerivedValue(() => {
    const f = frame.get();
    const h = header.get();
    return createPicture((canvas) => drawMap(canvas, f.w, h, ink, accent), { width, height: headerH });
  });
  const mapCaptionStyle = useAnimatedStyle(() => ({
    opacity: 1 - smooth(0, 0.3, open.get()),
    transform: [{ translateY: header.get() - tile.h }],
  }));
  // The sheet keeps its page width and is scaled with the frame, so its lines never re-wrap.
  const sheetStyle = useAnimatedStyle(() => {
    const f = frame.get();
    const scale = f.w / width;
    return {
      opacity: reducedMotion ? 1 : smooth(SHEET_FROM, SHEET_TO, open.get()),
      transform: [{ translateY: header.get() - headerH * scale }, { scale }],
    };
  });
  // The shadow is a layer of its own under the clipped page, shown in flight and while dragged.
  const liftStyle = useAnimatedStyle(() => {
    const f = frame.get();
    const p = clamp01(open.get());
    const lifted = reducedMotion ? 0 : clamp01(Math.sin(Math.PI * p) * 0.8 + pullOf(dragY.get(), height) * 4);
    return { left: f.x, top: f.y, width: f.w, height: f.h, borderRadius: f.r, opacity: lifted };
  });
  const chromeStyle = useAnimatedStyle(() => ({
    opacity: smooth(0.6, 1, open.get()) * (1 - clamp01(pullOf(dragY.get(), height) * 3)),
  }));

  return (
    <>
    <Animated.View pointerEvents="none" style={[styles.lift, { backgroundColor: paperOf(face) }, liftStyle]} />
    <Animated.View style={[styles.page, { backgroundColor: paperOf(face) }, box]}>
      <Animated.View style={[styles.header, headerStyle]}>
        {face.kind === 'photo' && (
          <>
            <Image
              style={StyleSheet.absoluteFill}
              source={{ uri: photoUrl(face.photo, HEADER_PX) }}
              contentFit="cover"
              cachePolicy="memory-disk"
              transition={0}
            />
            <Animated.View style={[StyleSheet.absoluteFill, labelStyle]}>
              <PhotoLabel note={note} narrow={tile.w < NARROW} />
            </Animated.View>
          </>
        )}
        {face.kind === 'map' && (
          <>
            <Canvas style={{ position: 'absolute', left: 0, top: 0, width, height: headerH }}>
              <Picture picture={mapPicture} />
            </Canvas>
            <Animated.View style={[{ position: 'absolute', left: 0, top: 0, width: tile.w, height: tile.h }, mapCaptionStyle]}>
              <MapCaption place={face.place} stops={face.stops} ink={ink} />
            </Animated.View>
          </>
        )}
        {(face.kind === 'stat' || face.kind === 'quote') && (
          <Animated.View
            style={[
              { position: 'absolute', left: 0, top: 0, width: tile.w * grow, height: tile.h * grow, transformOrigin: 'left top' },
              typeStyle,
            ]}
          >
            <TypeFace face={face} grow={grow} ink={ink} accent={accent} />
          </Animated.View>
        )}
      </Animated.View>

      <GestureDetector gesture={nativeScroll}>
        <Animated.ScrollView
          ref={scrollRef}
          onScroll={onScroll}
          scrollEventThrottle={16}
          bounces={false}
          showsVerticalScrollIndicator={false}
          style={[{ position: 'absolute', left: 0, top: 0, width, height, transformOrigin: 'left top' }, sheetStyle]}
          contentContainerStyle={{ paddingTop: headerH - OVERLAP }}
        >
          <View style={[styles.sheet, { backgroundColor: background, paddingBottom: insets.bottom + 40 }]}>
            <View style={styles.grabber} />
            <Section order={0} open={open} reducedMotion={reducedMotion}>
              <Text style={[styles.kicker, { color: accent }]}>
                {note.kicker.toUpperCase()} · {String(index + 1).padStart(2, '0')} / {String(count).padStart(2, '0')}
              </Text>
              <Text style={[styles.pageTitle, { color: ink }]}>{note.title}</Text>
              <Text style={styles.byline}>{note.byline}</Text>
            </Section>
            <Section order={1} open={open} reducedMotion={reducedMotion}>
              <Text style={[styles.lead, { color: ink }]}>{note.lead}</Text>
            </Section>
            <Section order={2} open={open} reducedMotion={reducedMotion}>
              {note.body.map((paragraph) => (
                <Text key={paragraph.slice(0, 16)} style={[styles.body, { color: ink }]}>
                  {paragraph}
                </Text>
              ))}
            </Section>
            <Section order={3} open={open} reducedMotion={reducedMotion}>
              <View style={[styles.pull, { borderLeftColor: accent }]}>
                <Text style={[styles.pullText, { color: ink }]}>{note.pull}</Text>
              </View>
            </Section>
            <Section order={4} open={open} reducedMotion={reducedMotion}>
              <View style={styles.pair}>
                {note.pair.map((id) => (
                  <Image
                    key={id}
                    style={styles.pairPhoto}
                    source={{ uri: photoUrl(id, PAIR_PX) }}
                    contentFit="cover"
                    cachePolicy="memory-disk"
                    transition={250}
                  />
                ))}
              </View>
              <Text style={[styles.body, { color: ink }]}>{note.outro}</Text>
              <Text style={styles.end}>End of note</Text>
            </Section>
          </View>
        </Animated.ScrollView>
      </GestureDetector>

      <Animated.View style={[styles.close, { top: insets.top + 8 }, chromeStyle]}>
        <Pressable
          accessibilityRole="button"
          accessibilityLabel="Close"
          onPress={onClose}
          hitSlop={8}
          style={({ pressed }) => [styles.closeButton, pressed && styles.closePressed]}
        >
          <Svg width={14} height={14} viewBox="0 0 24 24">
            <Path d="M5 5l14 14M19 5L5 19" stroke={ink} strokeWidth={2.6} strokeLinecap="round" />
          </Svg>
        </Pressable>
      </Animated.View>
    </Animated.View>
    </>
  );
}

/** A block of the page that rises in after the frame has mostly grown, in its turn. */
function Section({
  order,
  open,
  reducedMotion,
  children,
}: {
  order: number;
  open: SharedValue<number>;
  reducedMotion: boolean;
  children: ReactNode;
}) {
  const style = useAnimatedStyle(() => {
    const from = REVEAL_FROM + order * REVEAL_STEP;
    const shown = smooth(from, from + REVEAL_SPAN, open.get());
    return {
      opacity: shown,
      transform: [{ translateY: reducedMotion ? 0 : (1 - shown) * REVEAL_RISE }],
    };
  });
  return <Animated.View style={style}>{children}</Animated.View>;
}

const styles = StyleSheet.create({
  screen: {
    flex: 1,
    overflow: 'hidden',
  },
  titleBlock: {
    position: 'absolute',
    left: MARGIN + 4,
    right: MARGIN + 4,
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'flex-end',
  },
  kicker: {
    fontSize: 10,
    fontWeight: '700',
    letterSpacing: 1.6,
  },
  gridTitle: {
    marginTop: 6,
    fontFamily: SERIF,
    fontSize: 36,
    letterSpacing: -0.8,
  },
  issue: {
    fontSize: 11,
    fontWeight: '600',
    lineHeight: 14,
    textAlign: 'right',
    color: MUTED,
    marginBottom: 6,
  },
  tile: {
    position: 'absolute',
  },
  tileClip: {
    flex: 1,
    borderRadius: TILE_RADIUS,
    borderCurve: 'continuous',
    overflow: 'hidden',
  },
  photoLabel: {
    position: 'absolute',
    left: 0,
    right: 0,
    bottom: 0,
    paddingHorizontal: 14,
    paddingTop: 26,
    paddingBottom: 12,
  },
  photoLabelNarrow: {
    paddingHorizontal: 11,
    paddingBottom: 10,
  },
  photoTitleNarrow: {
    fontSize: 13,
    lineHeight: 17,
  },
  photoKicker: {
    fontSize: 9,
    fontWeight: '700',
    letterSpacing: 1.4,
    color: 'rgba(255,255,255,0.75)',
  },
  photoTitle: {
    marginTop: 5,
    fontSize: 15,
    fontWeight: '600',
    color: '#ffffff',
  },
  mapCaption: {
    position: 'absolute',
    left: 10,
    bottom: 9,
  },
  mapPlace: {
    fontSize: 13,
    fontWeight: '700',
  },
  mapStops: {
    fontSize: 10,
    fontWeight: '500',
    color: MUTED,
  },
  lift: {
    position: 'absolute',
    borderCurve: 'continuous',
    boxShadow: LIFT_SHADOW,
  },
  page: {
    position: 'absolute',
    overflow: 'hidden',
    borderCurve: 'continuous',
  },
  header: {
    position: 'absolute',
    left: 0,
    right: 0,
    top: 0,
    overflow: 'hidden',
  },
  sheet: {
    borderTopLeftRadius: OVERLAP,
    borderTopRightRadius: OVERLAP,
    borderCurve: 'continuous',
    paddingHorizontal: 24,
    paddingTop: 12,
    gap: 22,
  },
  grabber: {
    alignSelf: 'center',
    width: 36,
    height: 5,
    borderRadius: 3,
    backgroundColor: 'rgba(31,28,24,0.14)',
    marginBottom: -6,
  },
  pageTitle: {
    marginTop: 10,
    fontFamily: SERIF,
    fontSize: 32,
    lineHeight: 37,
    letterSpacing: -0.6,
  },
  byline: {
    marginTop: 8,
    fontSize: 13,
    fontWeight: '500',
    color: MUTED,
  },
  lead: {
    fontFamily: SERIF,
    fontSize: 19,
    lineHeight: 28,
  },
  body: {
    fontSize: 16,
    lineHeight: 25,
    marginBottom: 12,
  },
  pull: {
    borderLeftWidth: 3,
    paddingLeft: 16,
    paddingVertical: 4,
  },
  pullText: {
    fontFamily: SERIF,
    fontSize: 22,
    lineHeight: 29,
    fontStyle: 'italic',
  },
  pair: {
    flexDirection: 'row',
    gap: 10,
    marginBottom: 18,
  },
  pairPhoto: {
    flex: 1,
    height: 180,
    borderRadius: 16,
    borderCurve: 'continuous',
    backgroundColor: PHOTO_PAPER,
  },
  end: {
    marginTop: 6,
    fontSize: 11,
    fontWeight: '600',
    letterSpacing: 1.4,
    color: MUTED,
    textAlign: 'center',
    textTransform: 'uppercase',
  },
  close: {
    position: 'absolute',
    right: 16,
  },
  closeButton: {
    width: 38,
    height: 38,
    borderRadius: 19,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: 'rgba(250,247,241,0.88)',
  },
  closePressed: {
    opacity: 0.7,
    transform: [{ scale: 0.95 }],
  },
});
