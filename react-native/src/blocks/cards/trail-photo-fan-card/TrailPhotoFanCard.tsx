import { BlurMask, Canvas, Group, Path, Skia } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { Image } from 'expo-image';
import { useEffect, useMemo, useRef, useState } from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import Animated, {
  Easing,
  ReduceMotion,
  interpolateColor,
  useAnimatedStyle,
  useDerivedValue,
  useReducedMotion,
  useSharedValue,
  withDelay,
  withRepeat,
  withSpring,
  withTiming,
} from 'react-native-reanimated';

type Photo = {
  /** Address of the photo. */
  photo: string;
  /** Flat colour shown until the photo arrives. */
  tint: string;
};

type Icon = 'clock' | 'map' | 'bolt';

type Fact = { icon: Icon; label: string };

type Props = {
  /** Presses the card by itself, holds it open and lets go, in a loop. */
  autoPlay?: boolean;
  /** The last word shares its line with the arrow. */
  title?: string;
  /** The deck, bottom photo first. */
  photos?: [Photo, Photo, Photo];
  facts?: Fact[];
  summary?: string;
  /** Colour of the title while the card is pressed. */
  accent?: string;
  /** Draws the clouds the card floats among. */
  clouds?: boolean;
  /** Called when the card is tapped. */
  onPress?: () => void;
};

const WIDTH = 316;
const HEIGHT = 404;
const RADIUS = 28;
const PAD = 20;
const INK = '#0c0c0c';

const TITLE_TOP = 18;
const TITLE_LINE = 62;
const ARROW_GAP = 10;
// The arrow is drawn in a 44 x 34 box; the window it slides through is a little wider.
const ARROW_BOX = 46;
const ARROW_HEIGHT = 34;
const ARROW_STROKE = 7;
// How far one slide carries the arrow: out of the window on the right, in again from the left.
const ARROW_TRAVEL = 52;

const PHOTO_WIDTH = 82;
const PHOTO_HEIGHT = 110;
const PHOTO_RADIUS = 15;
const PHOTO_BORDER = 4;
const FACTS_TOP = 300;
const SUMMARY_TOP = 324;

// Centre, angle in degrees and scale of each photo, bottom photo first.
const DECK = [
  { x: 61, y: 222, angle: 0, scale: 0.9 },
  { x: 79, y: 222, angle: 0, scale: 0.95 },
  { x: 97, y: 222, angle: 0, scale: 1 },
];
const FAN = [
  { x: 65, y: 224, angle: -5, scale: 1 },
  { x: 152, y: 219, angle: 8, scale: 1 },
  { x: 231, y: 186, angle: 25, scale: 1 },
];

const PRESS_SCALE = 0.98;
const OPEN_SPRING = { mass: 1, stiffness: 260, damping: 19 };
const CLOSE_SPRING = { mass: 1, stiffness: 300, damping: 26 };
const PRESS_SPRING = { mass: 1, stiffness: 320, damping: 24 };
// Milliseconds between one photo and the next.
const STAGGER = 70;
const TINT_MS = 260;
const TINT_EASE = Easing.bezier(0.23, 1, 0.32, 1);
const SLIDE_MS = 520;
const SLIDE_EASE = Easing.bezier(0.65, 0, 0.35, 1);

// autoPlay loop (seconds): a rest, a long press, and a rest again while the deck folds.
const PRESS_AT = 0.8;
const RELEASE_AT = 3.2;
const LOOP = 5;
// After the last touch the loop waits this long before it starts again from rest.
const RESUME_AFTER = 2;

// The sky is drawn this far beyond the card on every side.
const SKY_BLEED = 110;

// A cloud in the card's coordinates: round puffs [x, y, radius] standing on a flat pill.
type Cloud = { puffs: number[][]; base: { x: number; y: number; width: number; height: number } };

const NEAR_CLOUDS: Cloud[] = [
  {
    puffs: [[-28, -18, 30], [14, -40, 40], [66, -52, 46], [118, -30, 36], [156, -10, 26]],
    base: { x: -58, y: -22, width: 240, height: 42 },
  },
  {
    puffs: [[150, 452, 30], [196, 430, 42], [252, 412, 52], [310, 428, 44], [352, 448, 32]],
    base: { x: 118, y: 436, width: 268, height: 46 },
  },
];
const FAR_CLOUDS: Cloud[] = [
  {
    puffs: [[300, -62, 18], [326, -72, 24], [352, -60, 18]],
    base: { x: 280, y: -66, width: 92, height: 22 },
  },
  {
    puffs: [[-10, 448, 16], [14, 440, 22], [40, 450, 16]],
    base: { x: -28, y: 446, width: 86, height: 18 },
  },
];

function arrowAt(x: number) {
  return `M${x} 17L${x + 36.5} 17M${x + 22} 2.5L${x + 36.5} 17L${x + 22} 31.5`;
}
// The arrow twice: one copy leaves on the right while the next comes in from the left.
const ARROWS = arrowAt(1) + arrowAt(1 - ARROW_TRAVEL);

// 12-point pictograms: a grey shape and the lines cut out of it.
const GLYPHS: Record<Icon, { shape: string; cut?: string; cutWidth?: number }> = {
  clock: { shape: 'M6 0.5A5.5 5.5 0 1 1 6 11.5A5.5 5.5 0 1 1 6 0.5Z', cut: 'M6 3.3L6 6L8 7.2', cutWidth: 1.2 },
  map: {
    shape: 'M1 2.6L4.4 1.2L7.6 2.6L11 1.2L11 9.4L7.6 10.8L4.4 9.4L1 10.8Z',
    cut: 'M4.4 1.2L4.4 9.4M7.6 2.6L7.6 10.8',
    cutWidth: 1,
  },
  bolt: { shape: 'M7.2 0.8L2.4 6.9L5.6 6.9L4.8 11.2L9.6 5.1L6.4 5.1Z' },
};

function mix(from: number, to: number, t: number) {
  'worklet';
  return from + (to - from) * t;
}

function cloudPath({ puffs, base }: Cloud) {
  const path = Skia.Path.Make();
  path.addRRect(Skia.RRectXY(Skia.XYWHRect(base.x, base.y, base.width, base.height), base.height / 2, base.height / 2));
  for (const [x, y, r] of puffs) path.addCircle(x, y, r);
  return path;
}

function Clouds({ clouds, blur, opacity }: { clouds: Cloud[]; blur: number; opacity: number }) {
  const paths = useMemo(() => clouds.map(cloudPath), [clouds]);
  return (
    <Canvas style={styles.skyCanvas}>
      <Group transform={[{ translateX: SKY_BLEED }, { translateY: SKY_BLEED }]}>
        {paths.map((path, index) => (
          <Path key={index} path={path} color={`rgba(255,255,255,${opacity})`}>
            <BlurMask blur={blur} style="normal" />
          </Path>
        ))}
      </Group>
    </Canvas>
  );
}

/**
 * Soft clouds behind the card: two near ones it seems to rest on and two far ones, swaying against
 * each other once per loop.
 */
function Sky({ still }: { still: boolean }) {
  const turn = useSharedValue(0);

  useEffect(() => {
    turn.set(still ? 0 : withRepeat(withTiming(1, { duration: LOOP * 1000, easing: Easing.linear }), -1));
  }, [still, turn]);

  const far = useAnimatedStyle(() => {
    const angle = turn.get() * Math.PI * 2;
    return { transform: [{ translateX: -3 * Math.sin(angle) }, { translateY: 1.5 * Math.cos(angle) }] };
  });
  const near = useAnimatedStyle(() => {
    const angle = turn.get() * Math.PI * 2;
    return { transform: [{ translateX: 6 * Math.sin(angle) }, { translateY: -2 * Math.cos(angle) }] };
  });

  return (
    <View pointerEvents="none" style={styles.sky}>
      <Animated.View style={[styles.skyCanvas, far]}>
        <Clouds clouds={FAR_CLOUDS} blur={9} opacity={0.55} />
      </Animated.View>
      <Animated.View style={[styles.skyCanvas, near]}>
        <Clouds clouds={NEAR_CLOUDS} blur={6} opacity={0.96} />
      </Animated.View>
    </View>
  );
}

function Glyph({ icon }: { icon: Icon }) {
  const { shape, cut, cutWidth } = GLYPHS[icon];
  return (
    <Canvas style={styles.glyph}>
      <Path path={shape} color="#8c8c8c" />
      {cut ? <Path path={cut} style="stroke" strokeWidth={cutWidth} strokeCap="round" strokeJoin="round" color="#ffffff" /> : null}
    </Canvas>
  );
}

function DeckPhoto({ item, index, open, reducedMotion }: { item: Photo; index: number; open: boolean; reducedMotion: boolean }) {
  const fan = useSharedValue(0);

  useEffect(() => {
    const to = open ? 1 : 0;
    if (reducedMotion) {
      fan.set(to);
      return;
    }
    // The fan opens from the bottom photo up and folds from the top photo down.
    const order = open ? index : 2 - index;
    fan.set(withDelay(order * STAGGER, withSpring(to, open ? OPEN_SPRING : CLOSE_SPRING)));
  }, [open, index, reducedMotion, fan]);

  const pose = useAnimatedStyle(() => {
    const t = fan.get();
    const from = DECK[index];
    const to = FAN[index];
    return {
      transform: [
        { translateX: mix(from.x, to.x, t) - PHOTO_WIDTH / 2 },
        { translateY: mix(from.y, to.y, t) - PHOTO_HEIGHT / 2 },
        { rotate: `${mix(from.angle, to.angle, t)}deg` },
        { scale: mix(from.scale, to.scale, t) },
      ],
    };
  });
  const lifted = useAnimatedStyle(() => ({ opacity: Math.min(1, Math.max(0, fan.get())) }));

  return (
    <Animated.View pointerEvents="none" style={[styles.photo, pose]}>
      {/* The shadow of a photo that has lifted off the deck. */}
      <Animated.View style={[styles.photoFrame, styles.photoLifted, lifted]} />
      <View style={[styles.photoFrame, styles.photoResting]} />
      <View style={[styles.photoImage, { backgroundColor: item.tint }]}>
        <Image source={{ uri: item.photo }} style={StyleSheet.absoluteFill} contentFit="cover" transition={200} />
      </View>
    </Animated.View>
  );
}

/**
 * A white trail card floating among clouds. While it is pressed the title turns olive, its arrow
 * slides out to the right and back in from the left, and the deck of three photos under the title
 * springs open into a rising fan; letting go folds the deck again.
 */
export function TrailPhotoFanCard({
  autoPlay = false,
  title = 'Mountain Hike',
  photos = [
    { photo: 'https://images.unsplash.com/photo-1526772662000-3f88f10405ff?auto=format&fit=crop&w=240&h=320&q=80', tint: '#778691' },
    { photo: 'https://images.unsplash.com/photo-1470071459604-3b5ec3a7fe05?auto=format&fit=crop&w=240&h=320&q=80', tint: '#6e756d' },
    { photo: 'https://images.unsplash.com/photo-1464822759023-fed622ff2c3b?auto=format&fit=crop&w=240&h=320&q=80', tint: '#647c82' },
  ],
  facts = [
    { icon: 'clock', label: '~8 Hours' },
    { icon: 'map', label: '8 km' },
    { icon: 'bolt', label: 'Medium Level' },
  ],
  summary = 'Hiking on a mountain blends physical challenge with natural beauty, offering sweeping views.',
  accent = '#5a5a32',
  clouds = true,
  onPress,
}: Props) {
  const reducedMotion = useReducedMotion();
  const auto = autoPlay && !reducedMotion;

  // A finger is on the card.
  const [held, setHeld] = useState(false);
  // The autoPlay loop is pressing the card.
  const [played, setPlayed] = useState(false);
  // A touch stopped the loop; it starts again from rest a while after the last one.
  const [paused, setPaused] = useState(false);
  const resume = useRef<ReturnType<typeof setTimeout> | null>(null);
  const open = held || played;

  const tint = useSharedValue(0);
  const press = useSharedValue(0);
  // 0…1 over one slide of the arrow; both ends look the same.
  const slide = useSharedValue(0);

  useEffect(() => {
    if (!auto || paused) return;
    const start = Date.now();
    let timer: ReturnType<typeof setTimeout>;
    // Every step is timed from the loop's start, so the period does not drift.
    const at = (seconds: number, step: () => void) => {
      timer = setTimeout(step, start + seconds * 1000 - Date.now());
    };
    const turn = (n: number) => {
      at(n * LOOP + PRESS_AT, () => {
        setPlayed(true);
        at(n * LOOP + RELEASE_AT, () => {
          setPlayed(false);
          turn(n + 1);
        });
      });
    };
    turn(0);
    return () => {
      clearTimeout(timer);
      setPlayed(false);
    };
  }, [auto, paused]);

  useEffect(() => () => {
    if (resume.current) clearTimeout(resume.current);
  }, []);

  useEffect(() => {
    const to = open ? 1 : 0;
    tint.set(withTiming(to, { duration: TINT_MS, easing: TINT_EASE, reduceMotion: ReduceMotion.Never }));
    press.set(reducedMotion ? to : withSpring(to, PRESS_SPRING));
    if (open && !reducedMotion) {
      slide.set(0);
      slide.set(withTiming(1, { duration: SLIDE_MS, easing: SLIDE_EASE }));
    }
  }, [open, reducedMotion, tint, press, slide]);

  const onPressIn = () => {
    if (resume.current) clearTimeout(resume.current);
    setHeld(true);
    setPaused(true);
    if (process.env.EXPO_OS !== 'web') Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
  };

  const onPressOut = () => {
    setHeld(false);
    resume.current = setTimeout(() => setPaused(false), RESUME_AFTER * 1000);
  };

  const cardStyle = useAnimatedStyle(() => ({ transform: [{ scale: mix(1, PRESS_SCALE, press.get()) }] }));
  const titleStyle = useAnimatedStyle(() => ({ color: interpolateColor(tint.get(), [0, 1], [INK, accent]) }));
  const arrowShift = useDerivedValue(() => [{ translateX: slide.get() * ARROW_TRAVEL }]);

  const cut = title.lastIndexOf(' ');
  const first = cut < 0 ? null : title.slice(0, cut);
  const last = title.slice(cut + 1);

  return (
    <View style={styles.root}>
      {clouds ? <Sky still={reducedMotion} /> : null}
      <Animated.View style={[styles.card, cardStyle]}>
        <Pressable
          style={styles.fill}
          onPressIn={onPressIn}
          onPressOut={onPressOut}
          onPress={onPress}
          accessibilityRole="button"
          accessibilityLabel={`${title}. ${facts.map((fact) => fact.label).join(', ')}. ${summary}`}
        >
          <View style={styles.heading}>
            {first == null ? null : (
              <Animated.Text style={[styles.title, titleStyle]} numberOfLines={1}>
                {first}
              </Animated.Text>
            )}
            <View style={styles.titleRow}>
              <Animated.Text style={[styles.title, titleStyle]} numberOfLines={1}>
                {last}
              </Animated.Text>
              <Canvas style={styles.arrow}>
                <Group transform={arrowShift}>
                  <Path path={ARROWS} style="stroke" strokeWidth={ARROW_STROKE} strokeJoin="miter" color={INK} />
                  <Path path={ARROWS} style="stroke" strokeWidth={ARROW_STROKE} strokeJoin="miter" color={accent} opacity={tint} />
                </Group>
              </Canvas>
            </View>
          </View>

          {photos.map((item, index) => (
            <DeckPhoto key={index} item={item} index={index} open={open} reducedMotion={reducedMotion} />
          ))}

          <View style={styles.facts}>
            {facts.map((fact, index) => (
              <View key={index} style={styles.fact}>
                <Glyph icon={fact.icon} />
                <Text style={styles.factLabel} numberOfLines={1}>
                  {fact.label}
                </Text>
              </View>
            ))}
          </View>

          <Text style={styles.summary} numberOfLines={3}>
            {summary}
          </Text>
        </Pressable>
      </Animated.View>
    </View>
  );
}

const styles = StyleSheet.create({
  root: {
    width: WIDTH,
    height: HEIGHT,
  },
  sky: {
    position: 'absolute',
    left: -SKY_BLEED,
    top: -SKY_BLEED,
  },
  skyCanvas: {
    position: 'absolute',
    left: 0,
    top: 0,
    width: WIDTH + SKY_BLEED * 2,
    height: HEIGHT + SKY_BLEED * 2,
  },
  card: {
    width: WIDTH,
    height: HEIGHT,
    borderRadius: RADIUS,
    borderCurve: 'continuous',
    backgroundColor: '#ffffff',
    boxShadow: '0 20px 44px rgba(6, 58, 88, 0.34)',
  },
  fill: {
    flex: 1,
  },
  heading: {
    position: 'absolute',
    left: PAD,
    top: TITLE_TOP,
  },
  titleRow: {
    height: TITLE_LINE,
    flexDirection: 'row',
    alignItems: 'center',
    gap: ARROW_GAP,
  },
  title: {
    height: TITLE_LINE,
    fontSize: 58,
    lineHeight: TITLE_LINE,
    fontWeight: '700',
    letterSpacing: -2.6,
  },
  arrow: {
    width: ARROW_BOX,
    height: ARROW_HEIGHT,
  },
  photo: {
    position: 'absolute',
    left: 0,
    top: 0,
    width: PHOTO_WIDTH,
    height: PHOTO_HEIGHT,
  },
  photoFrame: {
    position: 'absolute',
    left: 0,
    top: 0,
    right: 0,
    bottom: 0,
    borderRadius: PHOTO_RADIUS,
    borderCurve: 'continuous',
    backgroundColor: '#ffffff',
  },
  photoLifted: {
    boxShadow: '0 12px 24px rgba(14, 38, 52, 0.3)',
  },
  photoResting: {
    boxShadow: '0 3px 8px rgba(14, 38, 52, 0.22)',
  },
  photoImage: {
    position: 'absolute',
    left: PHOTO_BORDER,
    top: PHOTO_BORDER,
    right: PHOTO_BORDER,
    bottom: PHOTO_BORDER,
    borderRadius: PHOTO_RADIUS - PHOTO_BORDER,
    borderCurve: 'continuous',
    overflow: 'hidden',
  },
  facts: {
    position: 'absolute',
    left: PAD,
    top: FACTS_TOP,
    height: 14,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 14,
  },
  fact: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 5,
  },
  glyph: {
    width: 12,
    height: 12,
  },
  factLabel: {
    color: '#1a1a1a',
    fontSize: 10.5,
    lineHeight: 14,
    fontWeight: '600',
  },
  summary: {
    position: 'absolute',
    left: PAD,
    top: SUMMARY_TOP,
    width: WIDTH - PAD * 2,
    color: '#737373',
    fontSize: 14,
    lineHeight: 20,
  },
});
