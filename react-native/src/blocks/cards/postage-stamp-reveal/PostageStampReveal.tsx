import { BlurMask, Canvas, DashPathEffect, FillType, Group, Path, Rect, Skia, rect, vec } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { type GestureResponderEvent, StyleSheet, Text, View } from 'react-native';
import Animated, {
  Easing,
  ReduceMotion,
  type SharedValue,
  cancelAnimation,
  useAnimatedStyle,
  useDerivedValue,
  useReducedMotion,
  useSharedValue,
  withSpring,
  withTiming,
} from 'react-native-reanimated';

/** One flat shape of a stamp's illustration. */
export type PostageStampLayer = {
  /**
   * An SVG path (absolute M, L, Q, C and Z) drawn in the stamp's 156 × 204 window. Let it run past
   * the window on both sides: the layers slide against each other.
   */
  path: string;
  color: string;
  /** 0 for the horizon, 1 for the foreground: nearer layers travel further. */
  depth: number;
};

export type PostageStampPlace = {
  /** The two words of the name, set above and below the stamp. */
  top: string;
  bottom: string;
  country: string;
  region: string;
  /** The line of coordinates along the bottom edge. */
  caption: string;
  /** Colour of the panel while this place is shown. */
  paper: string;
  /** Colour of the big name. */
  ink: string;
  /** Colour of the region printed on the illustration. */
  regionInk: string;
  sky: string;
  /** Ground colour of the small stamp that peeks in from a corner. */
  ground: string;
  /** The illustration, back to front. */
  layers: PostageStampLayer[];
};

type Props = {
  places?: PostageStampPlace[];
  /** Plays the interaction by itself: next place, open, close, next, next. */
  autoPlay?: boolean;
  /** The three small labels along the top edge. */
  labels?: [string, string, string];
  /** The letter in the boxed emblem of the stamp. */
  emblem?: string;
  closeLabel?: string;
  /** Called with the index of the place a swipe lands on. */
  onSelect?: (index: number) => void;
  /** Called with the index of the place that was opened or closed. */
  onOpen?: (index: number) => void;
  onClose?: (index: number) => void;
};

const WIDTH = 340;
const HEIGHT = 420;
const RADIUS = 10;

// The stamp with its perforated border, and the window its illustration shows through.
const STAMP = { x: 80, y: 96, width: 180, height: 228 };
const WINDOW = { x: 92, y: 108, width: 156, height: 204 };
// Perforations: circles of this radius, centred on the edge, one per pitch.
const PITCH = 12;
const HOLE = 3.6;
// Soft shadows the stamps cast on the paper.
const SHADOW = { x: 4, y: 7 };
const SHADOW_BLUR = 6;
const MINI_SHADOW = { x: 3, y: 5 };
const MINI_SHADOW_BLUR = 5;
// The small stamps of the neighbouring places in two corners.
const MINI = { width: 96, height: 120 };
const MINI_BORDER = 9;
const MINI_HOLE = 3.2;
const MINI_TOP_LEFT = { x: -34, y: -22 };
const MINI_BOTTOM_RIGHT = { x: 294, y: 350 };
const MINI_TRAVEL = { x: 70, y: 80 };

// Scale at which the window covers the whole panel.
const OPEN_SCALE = 2.2;
// Opening pushes near layers down and towards the viewer more than far ones.
const OPEN_LIFT = 14;
const OPEN_ZOOM = 0.14;
const PIVOT = { x: 78, y: 122 };
// How far a far layer lags behind its scene while it slides in.
const SLIDE_LAG = 44;

// Centre lines of the two words: behind the stamp, and stacked at the top once the place is open.
const TITLE_SIZE = 64;
const TITLE_LINE = 76;
const TITLE_TOP = 89;
const TITLE_BOTTOM = 332;
const OPEN_TITLE_TOP = 68;
const OPEN_TITLE_BOTTOM = 126;
// The words slide further than the illustration, the lower one further still.
const TITLE_SHIFT = 300;
const TITLE_STAGGER = 1.2;

// Finger travel that moves one place, and how far a drag must go before it stops being a tap.
const DRAG_SPAN = 200;
const DRAG_SLOP = 8;
// Seconds of travel a release is credited with, as SwiftUI predicts the end of a drag.
const THROW = 0.25;

const SLIDE_SPRING = { mass: 1, stiffness: 130, damping: 19 };
const OPEN_SPRING = { mass: 1, stiffness: 90, damping: 14 };
const REDUCED_MS = 200;
const EASE_OUT = Easing.bezier(0.23, 1, 0.32, 1);

// autoPlay (seconds): next place, open, close, next, next, which is the first place again.
const SCRIPT = [
  { at: 0.8, opens: false },
  { at: 2.3, opens: true },
  { at: 4.3, opens: true },
  { at: 5.7, opens: false },
  { at: 7.0, opens: false },
];
const LOOP = 8.4;
// After a touch the loop waits this long, returns to the first place, and starts over.
const IDLE = 2.5;
const SETTLE = 1.2;

const GRAIN = 700;
const CREAM = '#efe6cf';
const TRAIL_COLOR = '#f2c64f';

const TRAIL = 'M-10 236 C4 200 22 192 34 216 C44 238 60 240 66 216 C72 190 92 186 110 214';
const MINI_GROUND = 'M0 70 C24 54 60 50 96 66 L96 120 L0 120 Z';

/**
 * A rectangle and a circle on every perforation; filled even-odd and clipped to the rectangle,
 * that leaves a half-round bite at each one.
 */
function perforated(x: number, y: number, width: number, height: number, hole: number) {
  const path = Skia.Path.Make();
  path.addRect(rect(x, y, width, height));
  for (let i = 0; i < Math.round(width / PITCH); i++) {
    const cx = x + PITCH * (i + 0.5);
    path.addCircle(cx, y, hole);
    path.addCircle(cx, y + height, hole);
  }
  for (let i = 0; i < Math.round(height / PITCH); i++) {
    const cy = y + PITCH * (i + 0.5);
    path.addCircle(x, cy, hole);
    path.addCircle(x + width, cy, hole);
  }
  path.setFillType(FillType.EvenOdd);
  return path;
}

const EDGE = perforated(STAMP.x, STAMP.y, STAMP.width, STAMP.height, HOLE);
const MINI_EDGE = perforated(0, 0, MINI.width, MINI.height, MINI_HOLE);

/** Two sets of one-point specks from a fixed sequence (Park–Miller), so the grain never changes. */
function grain() {
  let seed = 7;
  const next = () => {
    seed = (seed * 16807) % 2147483647;
    return seed / 2147483647;
  };
  const light = Skia.Path.Make();
  const dark = Skia.Path.Make();
  for (let i = 0; i < GRAIN * 2; i++) {
    const x = next() * WIDTH;
    const y = next() * HEIGHT;
    (i % 2 === 0 ? light : dark).addRect(rect(x, y, 1, 1));
  }
  return { light, dark };
}

const SPECKS = grain();

/** Signed distance of place `i` from the one on show, wrapped so the last place neighbours the first. */
function relOf(i: number, pos: number, n: number) {
  'worklet';
  let r = (i - pos) % n;
  if (r < -n / 2) r += n;
  else if (r >= n / 2) r -= n;
  return r;
}

function wrap(i: number, n: number) {
  'worklet';
  return ((i % n) + n) % n;
}

function clamp01(t: number) {
  'worklet';
  return Math.min(1, Math.max(0, t));
}

/** 1 at rest on a place, 0 halfway between two. */
function settledOf(pos: number) {
  'worklet';
  const d = Math.min(1, Math.abs(pos - Math.round(pos)) * 2);
  return 1 - d * d * (3 - 2 * d);
}

/** Under reduced motion the stamp jumps open instead of growing. */
function scaleOf(open: number, reduced: boolean) {
  'worklet';
  if (reduced) return open > 0.5 ? OPEN_SCALE : 1;
  return 1 + (OPEN_SCALE - 1) * open;
}

type Motion = {
  /** Index of the place on show; fractional while one slides into the other, and it keeps counting past the last place. */
  pos: SharedValue<number>;
  open: SharedValue<number>;
  /** Under reduced motion nothing slides or grows: scenes cross-fade and the stamp jumps open. */
  reduced: boolean;
};

function MiniStamp({ places, offset, corner, away, pos, open, reduced }: { places: PostageStampPlace[]; offset: number; corner: { x: number; y: number }; away: number } & Motion) {
  const n = places.length;
  const skies = useMemo(() => places.map((place) => place.sky), [places]);
  const grounds = useMemo(() => places.map((place) => place.ground), [places]);
  // It leaves through its corner while a place slides or is open, and comes back for the next one.
  const transform = useDerivedValue(() => {
    const hidden = reduced ? 0 : 1 - settledOf(pos.get()) * (1 - clamp01(open.get()));
    return [{ translateX: corner.x + away * hidden * MINI_TRAVEL.x }, { translateY: corner.y + away * hidden * MINI_TRAVEL.y }];
  });
  const opacity = useDerivedValue(() => (reduced ? settledOf(pos.get()) * (1 - clamp01(open.get())) : 1));
  const sky = useDerivedValue(() => skies[wrap(Math.round(pos.get()) + offset, n)]);
  const ground = useDerivedValue(() => grounds[wrap(Math.round(pos.get()) + offset, n)]);

  return (
    <Group transform={transform} opacity={opacity}>
      <Rect x={MINI_SHADOW.x} y={MINI_SHADOW.y} width={MINI.width} height={MINI.height} color="rgba(0,0,0,0.26)">
        <BlurMask blur={MINI_SHADOW_BLUR} style="normal" />
      </Rect>
      <Group clip={rect(0, 0, MINI.width, MINI.height)}>
        <Path path={MINI_EDGE} color={CREAM} />
      </Group>
      <Group clip={rect(MINI_BORDER, MINI_BORDER, MINI.width - MINI_BORDER * 2, MINI.height - MINI_BORDER * 2)}>
        <Rect x={0} y={0} width={MINI.width} height={MINI.height} color={sky} />
        <Path path={MINI_GROUND} color={ground} />
      </Group>
    </Group>
  );
}

/** The paper, the dotted trail and the two corner stamps: everything behind the name. */
function Back({ places, pos, open, reduced }: { places: PostageStampPlace[] } & Motion) {
  const n = places.length;
  const papers = useMemo(() => places.map((place) => place.paper), [places]);
  // The next place's colour is laid over this one's as it slides in.
  const paper = useDerivedValue(() => papers[wrap(Math.floor(pos.get()), n)]);
  const nextPaper = useDerivedValue(() => papers[wrap(Math.floor(pos.get()) + 1, n)]);
  const mix = useDerivedValue(() => pos.get() - Math.floor(pos.get()));
  const trailMove = useDerivedValue(() => [{ translateX: reduced ? 0 : (Math.round(pos.get()) - pos.get()) * 30 }]);
  const trailOpacity = useDerivedValue(() => settledOf(pos.get()) * (1 - clamp01(open.get())));

  return (
    <Canvas style={styles.fill}>
      <Rect x={0} y={0} width={WIDTH} height={HEIGHT} color={paper} />
      <Rect x={0} y={0} width={WIDTH} height={HEIGHT} color={nextPaper} opacity={mix} />
      <Group transform={trailMove} opacity={trailOpacity}>
        <Path path={TRAIL} style="stroke" strokeWidth={5.5} color={TRAIL_COLOR}>
          <DashPathEffect intervals={[5.5, 6.5]} />
        </Path>
      </Group>
      <MiniStamp places={places} offset={-1} corner={MINI_TOP_LEFT} away={-1} pos={pos} open={open} reduced={reduced} />
      <MiniStamp places={places} offset={1} corner={MINI_BOTTOM_RIGHT} away={1} pos={pos} open={open} reduced={reduced} />
    </Canvas>
  );
}

function SceneLayer({ layer, index, count, pos, open, reduced }: { layer: PostageStampLayer; index: number; count: number } & Motion) {
  const transform = useDerivedValue(() => {
    if (reduced) return [{ translateX: 0 }];
    const r = relOf(index, pos.get(), count);
    const scale = 1 + open.get() * layer.depth * OPEN_ZOOM;
    return [
      { translateX: -r * (1 - layer.depth) * SLIDE_LAG },
      { translateY: open.get() * (layer.depth - 0.5) * OPEN_LIFT },
      { translateX: PIVOT.x },
      { translateY: PIVOT.y },
      { scale },
      { translateX: -PIVOT.x },
      { translateY: -PIVOT.y },
    ];
  });
  return (
    <Group transform={transform}>
      <Path path={layer.path} color={layer.color} />
    </Group>
  );
}

function Scene({ place, index, count, pos, open, reduced }: { place: PostageStampPlace; index: number; count: number } & Motion) {
  const transform = useDerivedValue(() => {
    const r = relOf(index, pos.get(), count);
    return [{ translateX: WINDOW.x + (reduced ? 0 : r * WINDOW.width) }, { translateY: WINDOW.y }];
  });
  const opacity = useDerivedValue(() => {
    const r = Math.abs(relOf(index, pos.get(), count));
    if (r >= 1) return 0;
    return reduced ? 1 - r : 1;
  });

  return (
    <Group transform={transform} opacity={opacity}>
      <Group clip={rect(0, 0, WINDOW.width, WINDOW.height)}>
        <Rect x={0} y={0} width={WINDOW.width} height={WINDOW.height} color={place.sky} />
        {place.layers.map((layer, i) => (
          <SceneLayer key={i} layer={layer} index={index} count={count} pos={pos} open={open} reduced={reduced} />
        ))}
      </Group>
    </Group>
  );
}

/** The stamp: shadow, perforated paper and the illustrations sliding through its window. */
function Stamp({ places, pos, open, reduced }: { places: PostageStampPlace[] } & Motion) {
  const grow = useDerivedValue(() => [{ scale: scaleOf(open.get(), reduced) }]);
  const shadow = useDerivedValue(() => 1 - clamp01(open.get()));

  return (
    <Canvas style={styles.fill}>
      <Group transform={grow} origin={vec(WIDTH / 2, HEIGHT / 2)}>
        <Rect x={STAMP.x + SHADOW.x} y={STAMP.y + SHADOW.y} width={STAMP.width} height={STAMP.height} color="rgba(0,0,0,0.3)" opacity={shadow}>
          <BlurMask blur={SHADOW_BLUR} style="normal" />
        </Rect>
        <Group clip={rect(STAMP.x, STAMP.y, STAMP.width, STAMP.height)}>
          <Path path={EDGE} color={CREAM} />
        </Group>
        <Group clip={rect(WINDOW.x, WINDOW.y, WINDOW.width, WINDOW.height)}>
          {places.map((place, i) => (
            <Scene key={i} place={place} index={i} count={places.length} pos={pos} open={open} reduced={reduced} />
          ))}
        </Group>
      </Group>
      {/* Paper grain over the whole panel, the same specks every frame. */}
      <Path path={SPECKS.light} color="rgba(255,255,255,0.07)" />
      <Path path={SPECKS.dark} color="rgba(0,0,0,0.06)" />
    </Canvas>
  );
}

/** One word of a name behind the stamp. */
function BackWord({ text, color, index, count, top, shift, pos, reduced }: { text: string; color: string; index: number; count: number; top: number; shift: number; pos: SharedValue<number>; reduced: boolean }) {
  const style = useAnimatedStyle(() => {
    const r = relOf(index, pos.get(), count);
    return {
      opacity: reduced ? Math.max(0, 1 - Math.abs(r)) : 1,
      transform: [{ translateX: reduced ? 0 : r * shift }],
    };
  });
  return (
    <Animated.Text style={[styles.word, { top: top - TITLE_LINE / 2, color }, style]} numberOfLines={1}>
      {text}
    </Animated.Text>
  );
}

/** The same word in front of the scene once the place is open. */
function FrontWord({ text, color, index, count, from, to, pos, open, reduced }: { text: string; color: string; index: number; count: number; from: number; to: number } & Motion) {
  const style = useAnimatedStyle(() => {
    const near = wrap(Math.round(pos.get()), count) === index;
    return {
      opacity: near ? clamp01((clamp01(open.get()) - 0.15) / 0.35) : 0,
      transform: [{ translateY: reduced ? 0 : (to - from) * open.get() }],
    };
  });
  return (
    <Animated.Text style={[styles.word, { top: from - TITLE_LINE / 2, color }, style]} numberOfLines={1}>
      {text}
    </Animated.Text>
  );
}

/** What is printed on one place's stamp, and its line of coordinates. */
function Print({ place, index, count, pos, open, reduced }: { place: PostageStampPlace; index: number; count: number } & Motion) {
  const countryStyle = useAnimatedStyle(() => ({ opacity: wrap(Math.round(pos.get()), count) === index ? 1 : 0 }));
  const regionStyle = useAnimatedStyle(() => ({ opacity: Math.max(0, 1 - Math.abs(relOf(index, pos.get(), count)) * 2) }));
  // It grows with the stamp and is gone before the border is.
  const stampStyle = useAnimatedStyle(() => ({
    opacity: Math.max(0, 1 - clamp01(open.get()) * 3),
    transform: [{ scale: scaleOf(open.get(), reduced) }],
  }));
  const captionStyle = useAnimatedStyle(() => ({
    opacity: wrap(Math.round(pos.get()), count) === index ? settledOf(pos.get()) : 0,
  }));

  return (
    <>
      <Animated.View style={[styles.fill, stampStyle]}>
        <Animated.Text style={[styles.country, countryStyle]} numberOfLines={1}>
          {place.country}
        </Animated.Text>
        <Animated.Text style={[styles.region, { color: place.regionInk }, regionStyle]} numberOfLines={1}>
          {place.region}
        </Animated.Text>
      </Animated.View>
      <Animated.Text style={[styles.caption, captionStyle]} numberOfLines={1}>
        {place.caption}
      </Animated.Text>
    </>
  );
}

/**
 * A place shown as a postage stamp on a panel of coloured paper, its name in huge capitals behind
 * the stamp. A horizontal swipe slides the next illustration into the stamp while the paper and the
 * name change with it; a tap opens the place: the stamp grows until its illustration fills the
 * panel, the perforated border leaving through the edges, and the name comes forward over the scene.
 */
export function PostageStampReveal({
  places = PLACES,
  autoPlay = false,
  labels = ['INDEX', 'ALL', 'ABOUT'],
  emblem = 'B',
  closeLabel = 'CLOSE',
  onSelect,
  onOpen,
  onClose,
}: Props) {
  const reduced = useReducedMotion();
  const auto = autoPlay && !reduced;
  const count = places.length;

  const pos = useSharedValue(0);
  const open = useSharedValue(0);
  // Where the two values are heading, for the code that runs outside the animation.
  const state = useRef({ target: 0, open: false, from: 0, pressed: false, dragging: false, startX: 0, lastX: 0, lastTime: 0, velocity: 0 });
  // Counts touches: a change restarts the autoPlay loop after a pause.
  const [touches, setTouches] = useState(0);

  const slide = useCallback(
    (target: number, velocity = 0) => {
      state.current.target = target;
      pos.set(
        reduced
          ? withTiming(target, { duration: REDUCED_MS, easing: EASE_OUT, reduceMotion: ReduceMotion.Never })
          : withSpring(target, { ...SLIDE_SPRING, velocity }),
      );
    },
    [reduced, pos],
  );

  const toggle = useCallback(() => {
    const opening = !state.current.open;
    state.current.open = opening;
    open.set(
      reduced
        ? withTiming(opening ? 1 : 0, { duration: REDUCED_MS, easing: EASE_OUT, reduceMotion: ReduceMotion.Never })
        : withSpring(opening ? 1 : 0, OPEN_SPRING),
    );
    return opening;
  }, [reduced, open]);

  useEffect(() => {
    if (!auto) return;
    const timers: ReturnType<typeof setTimeout>[] = [];
    const cycle = () => {
      timers.length = 0;
      // The same place under a smaller number, so the counter never grows.
      state.current.target = 0;
      pos.set(0);
      for (const step of SCRIPT) {
        timers.push(
          setTimeout(() => {
            if (step.opens) toggle();
            else slide(state.current.target + 1);
          }, step.at * 1000),
        );
      }
      timers.push(setTimeout(cycle, LOOP * 1000));
    };
    if (touches === 0) {
      cycle();
    } else {
      // After a touch the loop waits, walks back to the first place and starts over.
      timers.push(
        setTimeout(() => {
          if (state.current.pressed) return;
          if (state.current.open) toggle();
          slide(Math.round(state.current.target / count) * count);
          timers.push(setTimeout(cycle, SETTLE * 1000));
        }, IDLE * 1000),
      );
    }
    return () => timers.forEach(clearTimeout);
  }, [auto, touches, count, pos, slide, toggle]);

  const onGrant = (e: GestureResponderEvent) => {
    const s = state.current;
    s.pressed = true;
    s.dragging = false;
    s.startX = e.nativeEvent.pageX;
    s.lastX = e.nativeEvent.pageX;
    s.lastTime = e.nativeEvent.timestamp;
    s.velocity = 0;
    setTouches((n) => n + 1);
  };

  const onMove = (e: GestureResponderEvent) => {
    const s = state.current;
    if (s.open) return;
    const dx = e.nativeEvent.pageX - s.startX;
    if (!s.dragging) {
      if (Math.abs(dx) < DRAG_SLOP) return;
      s.dragging = true;
      s.from = s.target;
      cancelAnimation(pos);
    }
    // Places per second, smoothed over the last few events.
    const dt = e.nativeEvent.timestamp - s.lastTime;
    if (dt > 0) s.velocity = 0.6 * ((-(e.nativeEvent.pageX - s.lastX) / dt) * 1000) / DRAG_SPAN + 0.4 * s.velocity;
    s.lastX = e.nativeEvent.pageX;
    s.lastTime = e.nativeEvent.timestamp;
    pos.set(s.from - dx / DRAG_SPAN);
  };

  const onRelease = (e: GestureResponderEvent) => {
    const s = state.current;
    if (s.dragging) {
      s.dragging = false;
      // A swipe moves one place at most, however far it is thrown.
      const thrown = Math.round(s.from - (e.nativeEvent.pageX - s.startX) / DRAG_SPAN + s.velocity * THROW);
      const target = Math.min(s.from + 1, Math.max(s.from - 1, thrown));
      slide(target, s.velocity);
      if (target !== s.from) {
        if (process.env.EXPO_OS !== 'web') Haptics.selectionAsync().catch(() => {});
        onSelect?.(wrap(target, count));
      }
    } else {
      if (process.env.EXPO_OS !== 'web') Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
      const index = wrap(s.target, count);
      if (toggle()) onOpen?.(index);
      else onClose?.(index);
    }
    s.pressed = false;
    setTouches((n) => n + 1);
  };

  const onTerminate = () => {
    const s = state.current;
    if (s.dragging) {
      s.dragging = false;
      slide(s.from);
    }
    s.pressed = false;
    setTouches((n) => n + 1);
  };

  const closeStyle = useAnimatedStyle(() => ({
    opacity: clamp01((clamp01(open.get()) - 0.6) / 0.4),
    transform: [{ translateY: reduced ? 0 : (1 - open.get()) * 10 }],
  }));
  const emblemStyle = useAnimatedStyle(() => ({
    opacity: Math.max(0, 1 - clamp01(open.get()) * 3),
    transform: [{ scale: scaleOf(open.get(), reduced) }],
  }));

  if (count === 0) return null;
  const motion: Motion = { pos, open, reduced };
  const shown = places[0];

  return (
    <View
      style={styles.panel}
      accessible
      accessibilityRole="button"
      accessibilityLabel={`${shown.top} ${shown.bottom}, ${shown.region}`}
      onStartShouldSetResponder={() => true}
      onResponderTerminationRequest={() => false}
      onResponderGrant={onGrant}
      onResponderMove={onMove}
      onResponderRelease={onRelease}
      onResponderTerminate={onTerminate}
    >
      <View pointerEvents="none" style={styles.fill}>
        <Back places={places} {...motion} />
        {places.map((place, i) => (
          <BackWord key={`top-${i}`} text={place.top} color={place.ink} index={i} count={count} top={TITLE_TOP} shift={TITLE_SHIFT} pos={pos} reduced={reduced} />
        ))}
        {places.map((place, i) => (
          <BackWord
            key={`bottom-${i}`}
            text={place.bottom}
            color={place.ink}
            index={i}
            count={count}
            top={TITLE_BOTTOM}
            shift={TITLE_SHIFT * TITLE_STAGGER}
            pos={pos}
            reduced={reduced}
          />
        ))}
        <Stamp places={places} {...motion} />
        {places.map((place, i) => (
          <Print key={i} place={place} index={i} count={count} {...motion} />
        ))}
        <Animated.View style={[styles.fill, emblemStyle]}>
          <View style={styles.emblem}>
            <Text style={styles.emblemLabel}>{emblem}</Text>
          </View>
        </Animated.View>
        {places.map((place, i) => (
          <FrontWord key={`open-top-${i}`} text={place.top} color={place.ink} index={i} count={count} from={TITLE_TOP} to={OPEN_TITLE_TOP} {...motion} />
        ))}
        {places.map((place, i) => (
          <FrontWord key={`open-bottom-${i}`} text={place.bottom} color={place.ink} index={i} count={count} from={TITLE_BOTTOM} to={OPEN_TITLE_BOTTOM} {...motion} />
        ))}
        <Animated.View style={[styles.close, closeStyle]}>
          <Text style={styles.closeLabel}>{closeLabel}</Text>
        </Animated.View>
        <View style={styles.labels}>
          <View style={styles.index}>
            <Text style={styles.label}>{labels[0]}</Text>
            <View style={styles.dot} />
          </View>
          <Text style={styles.label}>{labels[2]}</Text>
        </View>
        <Text style={[styles.label, styles.middle]}>{labels[1]}</Text>
      </View>
    </View>
  );
}

/** A snowy hill with a hut, rock towers at sunset, and sea stacks. */
const PLACES: PostageStampPlace[] = [
  {
    top: 'MONTE',
    bottom: 'TOMBA',
    country: 'ITALIA',
    region: 'MONTI LESSINI',
    caption: 'BOSCO CHIESANUOVA · N 45°41′31″ · E 11°03′27″',
    paper: '#5d8fc0',
    ink: '#e58bb5',
    regionInk: '#3f6fc4',
    sky: '#5b97ea',
    ground: '#f4f1e8',
    layers: [
      { path: 'M-40 124 C0 106 40 100 80 110 C120 120 160 102 200 98 L200 250 L-40 250 Z', color: '#86b4f1', depth: 0.25 },
      { path: 'M106.4 50 L107.6 50 L107.6 72 L106.4 72 Z', color: '#2e1f6e', depth: 0.6 },
      { path: 'M103 55 L111 55 L111 56.2 L103 56.2 Z', color: '#2e1f6e', depth: 0.6 },
      { path: 'M104.5 60 L109.5 60 L109.5 61.2 L104.5 61.2 Z', color: '#2e1f6e', depth: 0.6 },
      { path: 'M100 80 L114 80 L114 110 L100 110 Z', color: '#5a3fb5', depth: 0.6 },
      { path: 'M114 80 L127 84 L127 110 L114 110 Z', color: '#d77fb4', depth: 0.6 },
      { path: 'M98 81 L107 71 L116 81 Z', color: '#2e1f6e', depth: 0.6 },
      { path: 'M107 71 L120 75 L129 85 L116 81 Z', color: '#40298c', depth: 0.6 },
      { path: 'M-40 140 C10 120 60 100 104 99 C140 98 170 106 200 116 L200 250 L-40 250 Z', color: '#f4f1e8', depth: 1 },
      { path: 'M-40 186 C30 160 90 162 200 184 L200 250 L-40 250 Z', color: '#e2e8f3', depth: 1 },
    ],
  },
  {
    top: 'CINQUE',
    bottom: 'TORRI',
    country: 'ITALIA',
    region: 'DOLOMITI',
    caption: "CORTINA D'AMPEZZO · N 46°30′38″ · E 12°03′07″",
    paper: '#cf7fae',
    ink: '#f2c44e',
    regionInk: '#fbe9d2',
    sky: '#ef9a76',
    ground: '#8a2f55',
    layers: [
      { path: 'M124 40 C131.73 40 138 46.27 138 54 C138 61.73 131.73 68 124 68 C116.27 68 110 61.73 110 54 C110 46.27 116.27 40 124 40 Z', color: '#fde2b4', depth: 0.1 },
      { path: 'M-40 138 L-6 112 L18 126 L46 100 L70 122 L104 104 L134 126 L160 108 L200 130 L200 250 L-40 250 Z', color: '#dc7a80', depth: 0.3 },
      { path: 'M26 160 L30 104 L41 94 L52 102 L54 160 Z', color: '#ee9dbd', depth: 0.65 },
      { path: 'M52 102 L61 109 L62 160 L54 160 Z', color: '#a94a76', depth: 0.65 },
      { path: 'M64 160 L68 70 L81 60 L94 70 L97 160 Z', color: '#ee9dbd', depth: 0.65 },
      { path: 'M94 70 L105 80 L107 160 L97 160 Z', color: '#a94a76', depth: 0.65 },
      { path: 'M112 160 L115 110 L124 102 L133 110 L135 160 Z', color: '#ee9dbd', depth: 0.65 },
      { path: 'M133 110 L141 117 L142 160 L135 160 Z', color: '#a94a76', depth: 0.65 },
      { path: 'M-40 150 C10 140 50 152 90 146 C130 140 160 150 200 142 L200 250 L-40 250 Z', color: '#c4577a', depth: 0.85 },
      { path: 'M-40 178 C20 160 70 176 110 170 C150 164 170 176 200 168 L200 250 L-40 250 Z', color: '#8a2f55', depth: 1 },
    ],
  },
  {
    top: 'ISOLA',
    bottom: 'BELLA',
    country: 'ITALIA',
    region: 'TAORMINA',
    caption: 'BAIA DI TAORMINA · N 37°51′02″ · E 15°18′01″',
    paper: '#3f9c80',
    ink: '#f4a3c6',
    regionInk: '#ffffff',
    sky: '#a8dde0',
    ground: '#2389a0',
    layers: [
      { path: 'M118 43 C126.28 43 133 49.72 133 58 C133 66.28 126.28 73 118 73 C109.72 73 103 66.28 103 58 C103 49.72 109.72 43 118 43 Z', color: '#fff4cf', depth: 0.1 },
      { path: 'M-40 124 L200 124 L200 250 L-40 250 Z', color: '#2389a0', depth: 0.3 },
      { path: 'M22 160 L28 100 L40 88 L58 92 L64 108 L68 160 Z', color: '#3f5c94', depth: 0.65 },
      { path: 'M58 92 L70 100 L78 160 L68 160 L64 108 Z', color: '#24365e', depth: 0.65 },
      { path: 'M28 100 L40 88 L58 92 L52 99 L38 102 Z', color: '#5cc08a', depth: 0.65 },
      { path: 'M88 160 L92 72 L101 62 L110 72 L112 160 Z', color: '#3f5c94', depth: 0.65 },
      { path: 'M110 72 L118 81 L120 160 L112 160 Z', color: '#24365e', depth: 0.65 },
      { path: 'M92 72 L101 62 L110 72 L103 76 Z', color: '#5cc08a', depth: 0.65 },
      { path: 'M126 160 L128 122 L135 115 L142 122 L144 160 Z', color: '#3f5c94', depth: 0.65 },
      { path: 'M142 122 L148 128 L149 160 L144 160 Z', color: '#24365e', depth: 0.65 },
      { path: 'M-40 158 C0 150 30 164 70 157 C110 150 140 164 200 154 L200 250 L-40 250 Z', color: '#45b5b0', depth: 1 },
      { path: 'M-40 158 C0 150 30 164 70 157 C110 150 140 164 200 154 L200 158 C140 168 110 154 70 161 C30 168 0 154 -40 162 Z', color: '#eefaf3', depth: 1 },
    ],
  },
];

const styles = StyleSheet.create({
  panel: {
    width: WIDTH,
    height: HEIGHT,
    borderRadius: RADIUS,
    overflow: 'hidden',
  },
  fill: {
    position: 'absolute',
    left: 0,
    top: 0,
    width: WIDTH,
    height: HEIGHT,
  },
  word: {
    position: 'absolute',
    left: -WIDTH / 2,
    width: WIDTH * 2,
    height: TITLE_LINE,
    textAlign: 'center',
    fontSize: TITLE_SIZE,
    lineHeight: TITLE_LINE,
    fontWeight: '900',
    letterSpacing: -1.5,
  },
  country: {
    position: 'absolute',
    left: WINDOW.x + 9,
    top: WINDOW.y + 9,
    width: 100,
    color: '#ffffff',
    fontSize: 11,
    lineHeight: 14,
    fontWeight: '800',
    letterSpacing: 0.4,
  },
  region: {
    position: 'absolute',
    left: WINDOW.x + 9,
    top: WINDOW.y + WINDOW.height - 10 - 13,
    width: 138,
    fontSize: 10,
    lineHeight: 13,
    fontWeight: '800',
    letterSpacing: 0.6,
  },
  emblem: {
    position: 'absolute',
    left: WINDOW.x + WINDOW.width - 9 - 15,
    top: WINDOW.y + 9,
    width: 15,
    height: 15,
    borderRadius: 2.5,
    borderWidth: 1.3,
    borderColor: '#ffffff',
    alignItems: 'center',
    justifyContent: 'center',
  },
  emblemLabel: {
    color: '#ffffff',
    fontSize: 10,
    lineHeight: 12,
    fontWeight: '800',
  },
  caption: {
    position: 'absolute',
    left: 14,
    top: HEIGHT - 15 - 4.5,
    width: WIDTH - 28,
    textAlign: 'center',
    color: 'rgba(255,255,255,0.92)',
    fontSize: 6.5,
    lineHeight: 9,
    fontWeight: '700',
    letterSpacing: 0.4,
  },
  close: {
    position: 'absolute',
    left: WIDTH / 2 - 31,
    top: HEIGHT - 50 - 12,
    width: 62,
    height: 24,
    borderRadius: 12,
    backgroundColor: CREAM,
    alignItems: 'center',
    justifyContent: 'center',
  },
  closeLabel: {
    color: '#2c2a26',
    fontSize: 8,
    lineHeight: 10,
    fontWeight: '800',
    letterSpacing: 0.8,
  },
  labels: {
    position: 'absolute',
    left: 14,
    top: 11,
    width: WIDTH - 28,
    height: 10,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  index: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 4,
  },
  dot: {
    width: 4,
    height: 4,
    borderRadius: 2,
    backgroundColor: 'rgba(255,255,255,0.92)',
  },
  label: {
    color: 'rgba(255,255,255,0.92)',
    fontSize: 7.5,
    lineHeight: 10,
    fontWeight: '800',
    letterSpacing: 0.5,
  },
  middle: {
    position: 'absolute',
    left: 14,
    top: 11,
    width: WIDTH - 28,
    textAlign: 'center',
  },
});
