import {
  Canvas,
  Glyphs,
  Group,
  Image as SkiaImage,
  LinearGradient,
  matchFont,
  Rect,
  type SkFont,
  type SkImage,
  Text as SkiaText,
  useImage,
  vec,
} from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { useEffect, useMemo, useState } from 'react';
import { type LayoutChangeEvent, StyleSheet, Text, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  cancelAnimation,
  Easing,
  type SharedValue,
  useAnimatedStyle,
  useDerivedValue,
  useFrameCallback,
  useReducedMotion,
  useSharedValue,
  withSpring,
  withTiming,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import Svg, { Path as SvgPath } from 'react-native-svg';
import { scheduleOnRN } from 'react-native-worklets';

type Props = {
  /** Turns to the next scene every few seconds, round and round. */
  autoPlay?: boolean;
  /** Called with the scene that comes into view after a swipe or a tap on the arrow. */
  onChange?: (index: number) => void;
};

type Scene = {
  /** Two short lines: the headline is cut into the strips with the photo. */
  title: [string, string];
  place: string;
  photo: string;
};

const photo = (id: string) => `https://images.unsplash.com/${id}?auto=format&fit=crop&w=1200&q=80`;

const SCENES: Scene[] = [
  { title: ['Stay inside', 'the drawing.'], place: 'Casa Ondara · Porto', photo: photo('photo-1496865534669-25ec2a3a0fd3') },
  { title: ['Concrete,', 'softened.'], place: 'Halle Nord · Basel', photo: photo('photo-1737442981890-0a87ef3ce9f5') },
  { title: ['Light keeps', 'the hours.'], place: 'Torre Luma · Valencia', photo: photo('photo-1522404419647-18cb51cc5c7a') },
  { title: ['Rooms with', 'a spine.'], place: 'Maison Gris · Lyon', photo: photo('photo-1536924491042-b0466800ce46') },
  { title: ['Find your', 'angle.'], place: 'Studio Okra · Kyoto', photo: photo('photo-1543067361-9bf996edf6ff') },
];
const COUNT = SCENES.length;

const SANS = process.env.EXPO_OS === 'ios' ? 'Avenir Next' : 'sans-serif';
const TITLE_SIZE = 40;
const TITLE_LEADING = 44;
const INDEX_SIZE = 56;
const PLACE_SIZE = 11;
const PLACE_TRACKING = 2.2; // pt between letters
const WHITE = '#f6f4ef';

// The blinds: six vertical strips. On the way to the next scene the even strips slide up and the
// odd ones down, each starting STAGGER of the transition after the one to its left, and while
// they move they part by up to GAP pt and darken, so the next scene shows between them.
const STRIPS = 6;
const STAGGER = 0.09;
const GAP = 7;
const SHADE = 0.4;
const STRIP_EASE = Easing.bezierFn(0.62, 0, 0.22, 1);
// The scene underneath starts a little larger and darker, and settles as it is uncovered.
const UNDER_SCALE = 0.08;
const UNDER_DIM = 0.55;

// Dragging: a full transition takes this share of the screen's height of finger travel.
const DRAG_SHARE = 0.55;
const DRAG_SLOP = 10;
const THROW_TIME = 0.25; // s of release speed added before choosing the scene
const FLICK = 1.1; // scenes per second: a release this fast turns the page whatever the distance
const RELEASE_SPRING = { duration: 650, dampingRatio: 1, overshootClamping: true };
const TURN_TIME = 1650; // ms, the arrow and autoPlay
const TURN_EASE = Easing.bezier(0.45, 0, 0.25, 1);

// Layout, in pt.
const SIDE = 28;
const BUTTON = 60;

// autoPlay: one turn every PERIOD seconds, the first after a rest, so a LOOP of all five scenes
// starts and ends on the same picture.
const PERIOD = 3;
const FIRST_TURN = 1.3;
const LOOP = PERIOD * COUNT;
const USER_HOLD = 3.5;

function clamp01(x: number) {
  'worklet';
  return Math.min(1, Math.max(0, x));
}

/** How far strip `k` has gone, 0 at rest and 1 out of the screen, for a transition at `t`. */
function stripAt(t: number, k: number) {
  'worklet';
  return STRIP_EASE(clamp01((t - k * STAGGER) / (1 - (STRIPS - 1) * STAGGER)));
}

/** `n` moved by whole cycles into [0, COUNT). */
function wrap(n: number) {
  'worklet';
  return ((n % COUNT) + COUNT) % COUNT;
}

/** True when the loop clock went past `at` between two frames (it may have wrapped). */
function crossed(before: number, now: number, at: number) {
  'worklet';
  if (now >= before) return before < at && now >= at;
  return at > before || at <= now;
}

type Glyph = { id: number; pos: { x: number; y: number } };

/** The place name in tracked capitals: Skia text has no letter spacing, so glyphs are laid out by hand. */
function tracked(font: SkFont, text: string, x: number, y: number): Glyph[] {
  const ids = font.getGlyphIDs(text);
  const widths = font.getGlyphWidths(ids);
  let at = x;
  return ids.map((id, k) => {
    const glyph = { id, pos: { x: at, y } };
    at += (widths[k] ?? 0) + PLACE_TRACKING;
    return glyph;
  });
}

type Layout = { width: number; height: number; indexY: number; placeY: number; line1Y: number; line2Y: number };

function layoutFor(width: number, height: number, top: number, bottom: number): Layout {
  const line2Y = height - bottom - 34;
  const line1Y = line2Y - TITLE_LEADING;
  return {
    width,
    height,
    indexY: top + 30 + INDEX_SIZE * 0.72,
    placeY: line1Y - TITLE_SIZE * 0.72 - 22,
    line1Y,
    line2Y,
  };
}

/** What one scene draws: what changes with the scene is read from shared values, so the same
 * nodes show any scene and nothing re-renders while the strips move. */
type Look = {
  image: SharedValue<SkImage | null>;
  number: SharedValue<string>;
  line1: SharedValue<string>;
  line2: SharedValue<string>;
  place: SharedValue<Glyph[]>;
};

function useLook(scene: SharedValue<number>, images: (SkImage | null)[], places: Glyph[][]): Look {
  return {
    image: useDerivedValue(() => images[scene.get()] ?? null, [images]),
    number: useDerivedValue(() => `0${scene.get() + 1}`),
    line1: useDerivedValue(() => (SCENES[scene.get()] ?? SCENES[0]).title[0]),
    line2: useDerivedValue(() => (SCENES[scene.get()] ?? SCENES[0]).title[1]),
    place: useDerivedValue(() => places[scene.get()] ?? [], [places]),
  };
}

function SceneContent({ look, layout, fonts }: { look: Look; layout: Layout; fonts: { title: SkFont; index: SkFont; small: SkFont } }) {
  const { width, height } = layout;
  return (
    <Group>
      <Rect x={0} y={0} width={width} height={height} color="#1a1916" />
      <SkiaImage image={look.image} x={0} y={0} width={width} height={height} fit="cover" />
      {/* Shade at the top for the number and at the bottom for the headline. */}
      <Rect x={0} y={0} width={width} height={height}>
        <LinearGradient
          start={vec(0, 0)}
          end={vec(0, height)}
          colors={['#00000073', '#00000000', '#00000000', '#000000c4']}
          positions={[0, 0.26, 0.48, 1]}
        />
      </Rect>
      <SkiaText x={SIDE - 3} y={layout.indexY} text={look.number} font={fonts.index} color={WHITE} />
      <Glyphs glyphs={look.place} font={fonts.small} color={WHITE} opacity={0.78} />
      <SkiaText x={SIDE - 2} y={layout.line1Y} text={look.line1} font={fonts.title} color={WHITE} />
      <SkiaText x={SIDE - 2} y={layout.line2Y} text={look.line2} font={fonts.title} color={WHITE} />
    </Group>
  );
}

/** One strip of the scene on top, clipped to its column and slid up or down out of the way. */
function Strip({ index, look, layout, fonts, phase, reduced }: {
  index: number;
  look: Look;
  layout: Layout;
  fonts: { title: SkFont; index: SkFont; small: SkFont };
  phase: SharedValue<number>;
  reduced: boolean;
}) {
  const { width, height } = layout;
  const column = width / STRIPS;
  const direction = index % 2 === 0 ? -1 : 1;
  const travel = useDerivedValue(() => (reduced ? 0 : stripAt(phase.get(), index)));
  // The clip travels with the strip, since it is drawn in the group's moved coordinates.
  const transform = useDerivedValue(() => [{ translateY: direction * (height + 40) * travel.get() }]);
  const clip = useDerivedValue(() => {
    const gap = GAP * Math.sin(Math.PI * travel.get());
    return { x: index * column + gap / 2, y: 0, width: column - gap, height };
  });
  const shade = useDerivedValue(() => SHADE * Math.sin((Math.PI / 2) * travel.get()));
  return (
    <Group transform={transform} clip={clip}>
      <SceneContent look={look} layout={layout} fonts={fonts} />
      <Rect x={0} y={0} width={width} height={height} color="#000000" opacity={shade} />
    </Group>
  );
}

/**
 * A welcome screen of architecture photos cut into six vertical strips. Turning to the next scene
 * slides the strips away like blinds, alternately up and down and one after another, and the
 * headline, cut by the same strips, goes with them. Dragging up or down scrubs the turn.
 */
export function CurtainSplitWelcome({ autoPlay = false, onChange }: Props) {
  const insets = useSafeAreaInsets();
  const reduced = useReducedMotion();
  const [size, setSize] = useState<{ width: number; height: number } | null>(null);
  const layout = useMemo(
    () => (size ? layoutFor(size.width, size.height, insets.top, insets.bottom) : null),
    [size, insets.top, insets.bottom],
  );

  const titleFont = matchFont({ fontFamily: SANS, fontSize: TITLE_SIZE, fontWeight: '600' });
  const indexFont = matchFont({ fontFamily: SANS, fontSize: INDEX_SIZE, fontWeight: '500' });
  const smallFont = matchFont({ fontFamily: SANS, fontSize: PLACE_SIZE, fontWeight: '700' });
  const fonts = useMemo(() => ({ title: titleFont, index: indexFont, small: smallFont }), [titleFont, indexFont, smallFont]);

  const image0 = useImage(SCENES[0].photo);
  const image1 = useImage(SCENES[1].photo);
  const image2 = useImage(SCENES[2].photo);
  const image3 = useImage(SCENES[3].photo);
  const image4 = useImage(SCENES[4].photo);
  const images = useMemo(() => [image0, image1, image2, image3, image4], [image0, image1, image2, image3, image4]);
  const places = useMemo(
    () => (layout ? SCENES.map((scene) => tracked(smallFont, scene.place.toUpperCase(), SIDE, layout.placeY)) : SCENES.map(() => [])),
    [layout, smallFont],
  );

  // Where we are, in scenes: the whole part is the scene on top, the rest how far its strips
  // have gone. It only grows or shrinks by one per turn and is folded back into one cycle.
  const position = useSharedValue(0);
  const dragFrom = useSharedValue(0);
  const press = useSharedValue(0);
  const clock = useSharedValue(0);
  const looping = useSharedValue(autoPlay);
  const hold = useSharedValue(0);

  useEffect(() => {
    looping.set(autoPlay);
    clock.set(0);
    hold.set(0);
  }, [autoPlay, clock, hold, looping]);

  const topScene = useDerivedValue(() => wrap(Math.floor(position.get())));
  const underScene = useDerivedValue(() => wrap(Math.floor(position.get()) + 1));
  const phase = useDerivedValue(() => position.get() - Math.floor(position.get()));
  const top = useLook(topScene, images, places);
  const under = useLook(underScene, images, places);

  const underTransform = useDerivedValue(() => [{ scale: reduced ? 1 : 1 + UNDER_SCALE * (1 - STRIP_EASE(phase.get())) }]);
  const underDim = useDerivedValue(() => UNDER_DIM * (1 - phase.get()));
  // Under reduced motion the strips stay in place and the scene on top fades out instead.
  const topOpacity = useDerivedValue(() => (reduced ? 1 - phase.get() : 1));

  const commit = (index: number) => {
    if (process.env.EXPO_OS !== 'web') Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
    onChange?.(index);
  };

  /** Folds the position back into the first cycle when it rests on a whole scene. */
  function fold() {
    'worklet';
    const p = position.get();
    if (Math.abs(p - Math.round(p)) < 0.001) position.set(wrap(Math.round(p)));
  }
  function turn(step: number) {
    'worklet';
    fold();
    const target = Math.round(position.get()) + step;
    position.set(withTiming(target, { duration: reduced ? 450 : TURN_TIME, easing: TURN_EASE }));
  }
  function touched() {
    'worklet';
    hold.set(USER_HOLD);
  }

  useFrameCallback((frame) => {
    const dt = Math.min(frame.timeSincePreviousFrame ?? 16, 64) / 1000;
    if (hold.get() > 0) {
      hold.set(hold.get() - dt);
      if (hold.get() <= 0) clock.set(0);
      return;
    }
    if (!looping.get()) return;
    const before = clock.get();
    const now = (before + dt) % LOOP;
    clock.set(now);
    for (let k = 0; k < COUNT; k += 1) {
      if (crossed(before, now, FIRST_TURN + k * PERIOD)) turn(1);
    }
  });

  // Never 0, even before the first layout: the drag divides by it.
  const height = Math.max(1, layout?.height ?? 1);
  const width = layout?.width ?? 1;

  function onButton(x: number, y: number) {
    'worklet';
    return x > width - SIDE - BUTTON - 10 && y > height - insets.bottom - 30 - BUTTON - 10;
  }

  const pan = Gesture.Pan()
    .activeOffsetY([-DRAG_SLOP, DRAG_SLOP])
    .onBegin(() => {
      touched();
    })
    .onStart(() => {
      cancelAnimation(position);
      fold();
      dragFrom.set(position.get());
    })
    .onUpdate((event) => {
      touched();
      const from = dragFrom.get();
      const moved = -event.translationY / (height * DRAG_SHARE);
      // One scene per drag, either way.
      position.set(Math.max(Math.round(from) - 1, Math.min(Math.round(from) + 1, from + moved)));
    })
    .onEnd((event) => {
      const from = Math.round(dragFrom.get());
      const velocity = -event.velocityY / (height * DRAG_SHARE);
      const p = position.get();
      // A flick goes on to the next whole scene in its direction; a slow release to the nearest.
      let thrown = Math.round(p + velocity * THROW_TIME);
      if (velocity > FLICK) thrown = Math.floor(p + 0.001) + 1;
      if (velocity < -FLICK) thrown = Math.ceil(p - 0.001) - 1;
      const target = Math.max(from - 1, Math.min(from + 1, thrown));
      position.set(withSpring(target, { ...RELEASE_SPRING, velocity }));
      if (target !== from) scheduleOnRN(commit, wrap(target));
    });

  const tap = Gesture.Tap()
    .maxDuration(450)
    .onBegin((event) => {
      touched();
      if (onButton(event.x, event.y)) press.set(withTiming(1, { duration: 100 }));
    })
    .onFinalize(() => {
      press.set(withTiming(0, { duration: 180 }));
    })
    .onEnd((event, success) => {
      if (!success || !onButton(event.x, event.y)) return;
      turn(1);
      scheduleOnRN(commit, wrap(Math.round(position.get()) + 1));
    });

  const buttonStyle = useAnimatedStyle(() => ({ transform: [{ scale: 1 - 0.07 * press.get() }] }));

  return (
    <GestureDetector gesture={Gesture.Race(pan, tap)}>
      <View
        style={styles.screen}
        accessible
        accessibilityRole="adjustable"
        accessibilityLabel={SCENES.map((scene) => scene.title.join(' ')).join(', ')}
        onLayout={(event: LayoutChangeEvent) => {
          const { width: w, height: h } = event.nativeEvent.layout;
          setSize({ width: w, height: h });
        }}
      >
        {layout && (
          <Canvas style={StyleSheet.absoluteFill}>
            <Group transform={underTransform} origin={vec(layout.width / 2, layout.height / 2)}>
              <SceneContent look={under} layout={layout} fonts={fonts} />
            </Group>
            <Rect x={0} y={0} width={layout.width} height={layout.height} color="#000000" opacity={underDim} />
            <Group opacity={topOpacity}>
              {Array.from({ length: STRIPS }, (_, k) => (
                <Strip key={k} index={k} look={top} layout={layout} fonts={fonts} phase={phase} reduced={reduced} />
              ))}
            </Group>
          </Canvas>
        )}

        {/* The chrome stays put while the scenes turn behind it. */}
        <View pointerEvents="none" style={[styles.topBar, { top: insets.top + 30 }]}>
          <Text style={styles.total}>/ 0{COUNT}</Text>
          <Text style={styles.mark}>PLINTH</Text>
        </View>
        <Animated.View pointerEvents="none" style={[styles.button, { bottom: insets.bottom + 30 }, buttonStyle]}>
          <Svg width={22} height={22} viewBox="0 0 22 22">
            <SvgPath d="M11 19 V3 M4.5 9.5 L11 3 L17.5 9.5" stroke="#1a1916" strokeWidth={2} strokeLinecap="round" strokeLinejoin="round" fill="none" />
          </Svg>
        </Animated.View>
        <Text pointerEvents="none" style={[styles.hint, { bottom: insets.bottom + 30 + BUTTON + 14 }]}>
          Swipe up
        </Text>
      </View>
    </GestureDetector>
  );
}

const styles = StyleSheet.create({
  screen: {
    flex: 1,
    overflow: 'hidden',
    backgroundColor: '#1a1916',
  },
  topBar: {
    position: 'absolute',
    left: SIDE + 74,
    right: SIDE,
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'flex-start',
  },
  total: {
    marginTop: 4,
    fontFamily: SANS,
    fontSize: 15,
    fontWeight: '500',
    color: WHITE,
    opacity: 0.7,
    fontVariant: ['tabular-nums'],
  },
  mark: {
    marginTop: 6,
    fontFamily: SANS,
    fontSize: 12,
    fontWeight: '700',
    letterSpacing: 4.5,
    color: WHITE,
  },
  button: {
    position: 'absolute',
    right: SIDE,
    width: BUTTON,
    height: BUTTON,
    borderRadius: BUTTON / 2,
    backgroundColor: WHITE,
    alignItems: 'center',
    justifyContent: 'center',
  },
  hint: {
    position: 'absolute',
    right: SIDE,
    width: BUTTON,
    textAlign: 'center',
    fontFamily: SANS,
    fontSize: 11,
    fontWeight: '600',
    letterSpacing: 0.4,
    color: WHITE,
    opacity: 0.7,
  },
});
