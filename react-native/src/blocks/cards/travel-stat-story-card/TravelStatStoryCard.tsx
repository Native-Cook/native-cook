import { Canvas, Group, Path, RadialGradient, Rect, vec } from '@shopify/react-native-skia';
import { BlurTargetView, BlurView } from 'expo-blur';
import * as Haptics from 'expo-haptics';
import { Image } from 'expo-image';
import { useEffect, useRef, useState } from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import Animated, {
  type SharedValue,
  cubicBezier,
  useAnimatedProps,
  useAnimatedReaction,
  useAnimatedStyle,
  useDerivedValue,
  useFrameCallback,
  useReducedMotion,
  useSharedValue,
} from 'react-native-reanimated';
import { scheduleOnRN } from 'react-native-worklets';

type Photo = {
  /** Address of the photo. */
  photo: string;
  /** Flat colour shown until the photo arrives. */
  tint: string;
};

type Icon = 'trail' | 'waves' | 'star';

type Story = {
  icon: Icon;
  category: string;
  /** Label of the outlined pill. */
  tag: string;
  /** The number the figure counts up to. */
  figure: number;
  unit?: string;
  headline: string;
  detail: string;
  /** Colour of the glow behind the story, as #rrggbb. */
  glow: string;
  /** The two pill-shaped thumbnails. */
  thumbs: [Photo, Photo];
  /** The photo that fills the card for a moment as the story comes in. */
  cover: Photo;
};

type Props = {
  /** Moves to the next story by itself every few seconds, in a loop. */
  autoPlay?: boolean;
  stories?: Story[];
  /** Called with the story's index when a tap moves on to it. */
  onChange?: (index: number) => void;
};

const WIDTH = 316;
const HEIGHT = 424;
const RADIUS = 32;
// Inset of the header row, and of the text under it.
const EDGE = 13;
const PAD = 26;
// Diameter of the round chips in the header.
const CHIP = 34;

const FIGURE_TOP = 104;
const FIGURE_HEIGHT = 88;
const TEXT_TOP = 219;
const THUMB_TOP = 298;
const THUMB_WIDTH = 108;
const THUMB_HEIGHT = 54;
const BAR_TOP = 392;
const BAR_WIDTH = 236;
const BAR_HEIGHT = 3;
const BAR_GAP = 8;

// autoPlay: a story stays this long, the first change comes FIRST_AT seconds after the card appears.
const STORY = 2.8;
const FIRST_AT = 0.9;
// After a tap the loop waits this long before it moves on from the tapped story.
const RESUME_AFTER = 2;

// A story change, in seconds after the tap.
const LEAVE = 0.26;
const ENTER_AT = 0.32;
const ENTER_STAGGER = 0.06;
const ENTER = 0.46;
const RISE = 12;
const COVER_AT = 0.06;
const COVER_IN = 0.26;
const COVER_OUT_AT = 0.42;
const COVER_OUT = 0.5;
const COVER_ZOOM = 0.12;
// Peak of the blur: a 12-point radius in the native versions, about this much of a system blur here.
const BLUR_INTENSITY = 45;
const BLUR_OUT_AT = 0.38;
const BLUR_OUT = 0.4;
const GLOW = 0.6;
const COUNT = 0.9;
// The figure starts counting at this share of its value.
const COUNT_FROM = 0.15;
const SEGMENT_FADE = 0.3;
// Length of the plain cross-fade under reduced motion.
const CALM_FADE = 0.2;

const CSS_EASE_OUT = cubicBezier(0.23, 1, 0.32, 1);

// The arrow pointing up and to the right, in a 12-point box.
const ARROW = 'M1.5 10.5L10.5 1.5M3.5 1.5L10.5 1.5L10.5 8.5';
const CARET = 'M0 0L7 0L3.5 4.5Z';
// 16-point pictograms for a story's category; the star is filled, the others are lines.
const GLYPHS: Record<Icon, string> = {
  trail: 'M1.5 13L6 4.5L8.6 9.2L10.4 6.4L14.5 13',
  waves: 'M1.5 6.4L4.1 4.6L6.7 6.4L9.3 4.6L11.9 6.4L14.5 4.6M1.5 11.4L4.1 9.6L6.7 11.4L9.3 9.6L11.9 11.4L14.5 9.6',
  star: 'M8 1.5L9.6 6.4L14.5 8L9.6 9.6L8 14.5L6.4 9.6L1.5 8L6.4 6.4Z',
};

const photo = (id: string, width: number, height: number) =>
  `https://images.unsplash.com/photo-${id}?auto=format&fit=crop&w=${width}&h=${height}&q=80`;

const STORIES: Story[] = [
  {
    icon: 'trail',
    category: 'Backpacking',
    tag: 'Budget Travel',
    figure: 35,
    headline: 'backpackers visit Southeast Asia',
    detail: 'every year to explore its rich culture and beautiful landscapes.',
    glow: '#f2600c',
    thumbs: [
      { photo: photo('1526772662000-3f88f10405ff', 220, 110), tint: '#778691' },
      { photo: photo('1506905925346-21bda4d32df4', 220, 110), tint: '#6e6f80' },
    ],
    cover: { photo: photo('1528127269322-539801943592', 640, 860), tint: '#849b9b' },
  },
  {
    icon: 'waves',
    category: 'Island Hopping',
    tag: 'Slow Travel',
    figure: 62,
    headline: 'of island trips include a boat day',
    detail: 'with snorkelling stops and sandbank picnics the most booked extras.',
    glow: '#0b9c8f',
    thumbs: [
      { photo: photo('1514282401047-d79a71a590e8', 220, 110), tint: '#4ec6c6' },
      { photo: photo('1506929562872-bb421503ef21', 220, 110), tint: '#5d8f89' },
    ],
    cover: { photo: photo('1505142468610-359e7d316be0', 640, 860), tint: '#588b90' },
  },
  {
    icon: 'star',
    category: 'Stargazing',
    tag: 'Winter Travel',
    figure: 48,
    headline: 'of aurora trips are booked for March',
    detail: 'when long clear nights meet the mildest weather of the season.',
    glow: '#6d3ff0',
    thumbs: [
      { photo: photo('1531366936337-7c912a4589a7', 220, 110), tint: '#546187' },
      { photo: photo('1504829857797-ddff29c27927', 220, 110), tint: '#8f9ea4' },
    ],
    cover: { photo: photo('1519681393784-d120267933ba', 640, 860), tint: '#434f6d' },
  },
];

/** `hex` (#rrggbb) at the given opacity. */
function fade(hex: string, opacity: number) {
  return hex + Math.round(opacity * 255).toString(16).padStart(2, '0');
}

/** The story shown at `since`. While `running`, one more story has passed for every STORY seconds after it. */
type Base = { anchor: number; since: number; running: boolean };

/** The story on screen and the seconds since it came in. */
type Place = { index: number; elapsed: number };

function position(now: number, base: Base, count: number): Place {
  'worklet';
  const passed = now - base.since;
  if (!base.running) return { index: base.anchor, elapsed: passed };
  const turns = Math.floor(passed / STORY);
  return { index: (base.anchor + turns) % count, elapsed: passed - turns * STORY };
}

// --- Where every part of the card stands `e` seconds after a story came in. Under reduced motion
// (`calm`) stories cross-fade and nothing else moves. ---

function ramp(e: number, from: number, over: number) {
  'worklet';
  return Math.min(1, Math.max(0, (e - from) / over));
}

function easeOut(t: number) {
  'worklet';
  return 1 - Math.pow(1 - t, 3);
}

function easeInOut(t: number) {
  'worklet';
  return t * t * (3 - 2 * t);
}

/** Opacity of the story that is going away. */
function leaving(e: number, calm: boolean) {
  'worklet';
  return 1 - (calm ? ramp(e, 0, CALM_FADE) : easeOut(ramp(e, 0, LEAVE)));
}

/** Opacity of one part of the story that is coming in, the parts following each other. */
function entering(e: number, calm: boolean, part: number) {
  'worklet';
  if (calm) return ramp(e, 0, CALM_FADE);
  return easeOut(ramp(e, ENTER_AT + ENTER_STAGGER * part, ENTER));
}

/** How far below its place a part of the incoming story still is. */
function lift(e: number, calm: boolean, part: number) {
  'worklet';
  return calm ? 0 : RISE * (1 - entering(e, calm, part));
}

function coverOpacity(e: number, calm: boolean) {
  'worklet';
  if (calm) return 0;
  return easeInOut(ramp(e, COVER_AT, COVER_IN)) * (1 - easeInOut(ramp(e, COVER_OUT_AT, COVER_OUT)));
}

function coverScale(e: number) {
  'worklet';
  return 1 + COVER_ZOOM * (1 - easeOut(ramp(e, COVER_AT, COVER_OUT_AT + COVER_OUT - COVER_AT)));
}

/** Share of the full blur, 0…1. */
function blurShare(e: number, calm: boolean) {
  'worklet';
  if (calm) return 0;
  return easeInOut(ramp(e, 0, LEAVE)) * (1 - easeInOut(ramp(e, BLUR_OUT_AT, BLUR_OUT)));
}

/** Opacity of story `i`'s glow. Glows are stacked in order, so the one on top does the fading. */
function glowOpacity(e: number, calm: boolean, i: number, index: number, previous: number) {
  'worklet';
  const mixed = easeInOut(ramp(e, 0, calm ? CALM_FADE : GLOW));
  if (i === index) return i > previous ? mixed : 1;
  if (i === previous) return i > index ? 1 - mixed : mixed < 1 ? 1 : 0;
  return 0;
}

function countTo(e: number, calm: boolean, figure: number) {
  'worklet';
  if (calm) return figure;
  const from = Math.round(figure * COUNT_FROM);
  return Math.round(from + (figure - from) * easeOut(ramp(e, ENTER_AT, COUNT)));
}

type Shared = {
  at: SharedValue<Place>;
  /** Index of the story this layer belongs to. */
  i: number;
  count: number;
  calm: boolean;
};

function Glow({ story, at, i, count, calm }: Shared & { story: Story }) {
  const style = useAnimatedStyle(() => {
    const { index, elapsed } = at.get();
    return { opacity: glowOpacity(elapsed, calm, i, index, (index + count - 1) % count) };
  });
  return (
    <Animated.View style={[styles.fill, style]}>
      <Canvas style={styles.fill}>
        <Rect x={0} y={0} width={WIDTH} height={HEIGHT}>
          <RadialGradient
            c={vec(WIDTH * 0.3, 0)}
            r={330}
            colors={[story.glow, fade(story.glow, 0.55), fade(story.glow, 0.12), fade(story.glow, 0)]}
            positions={[0, 0.42, 0.75, 1]}
          />
        </Rect>
        <Rect x={0} y={0} width={WIDTH} height={HEIGHT}>
          <RadialGradient c={vec(WIDTH * 0.2, HEIGHT * 1.05)} r={200} colors={[fade(story.glow, 0.16), fade(story.glow, 0)]} />
        </Rect>
      </Canvas>
    </Animated.View>
  );
}

function Picture({ item }: { item: Photo }) {
  return (
    <View style={[styles.fill, { backgroundColor: item.tint }]}>
      <Image source={{ uri: item.photo }} style={styles.fill} contentFit="cover" transition={200} />
    </View>
  );
}

function Cover({ story, at, i, calm }: Shared & { story: Story }) {
  const style = useAnimatedStyle(() => {
    const { index, elapsed } = at.get();
    return {
      opacity: i === index ? coverOpacity(elapsed, calm) : 0,
      transform: [{ scale: coverScale(elapsed) }],
    };
  });
  return (
    <Animated.View style={[styles.fill, style]}>
      <Picture item={story.cover} />
      <View style={[styles.fill, { backgroundColor: fade(story.glow, 0.5) }]} />
    </Animated.View>
  );
}

function Arrow({ size, opacity = 1 }: { size: number; opacity?: number }) {
  return (
    <Canvas style={{ width: size, height: size }}>
      <Group transform={[{ scale: size / 12 }]}>
        <Path path={ARROW} style="stroke" strokeWidth={1.4 * (12 / size)} strokeCap="round" strokeJoin="round" color="#ffffff" opacity={opacity} />
      </Group>
    </Canvas>
  );
}

/** One story, in four parts that come in one after another: the header, the figure, the text, the thumbnails. */
function Content({ story, at, i, count, calm }: Shared & { story: Story }) {
  const [shown, setShown] = useState(story.figure);

  useAnimatedReaction(
    () => {
      const { index, elapsed } = at.get();
      return i === index ? countTo(elapsed, calm, story.figure) : story.figure;
    },
    (now, old) => {
      if (now !== old) scheduleOnRN(setShown, now);
    },
  );

  // Only the story that comes in moves: each part rises into place as it appears.
  const part = (n: number) => {
    'worklet';
    const { index, elapsed } = at.get();
    if (i === index) return { opacity: entering(elapsed, calm, n), transform: [{ translateY: lift(elapsed, calm, n) }] };
    return { opacity: i === (index + count - 1) % count ? leaving(elapsed, calm) : 0, transform: [{ translateY: 0 }] };
  };
  const header = useAnimatedStyle(() => part(0));
  const figure = useAnimatedStyle(() => part(1));
  const text = useAnimatedStyle(() => part(2));
  const thumbs = useAnimatedStyle(() => part(3));

  return (
    <View style={styles.fill}>
      <Animated.View style={[styles.header, header]}>
        <View style={styles.chip}>
          <Canvas style={styles.glyph}>
            {story.icon === 'star' ? (
              <Path path={GLYPHS.star} color="#ffffff" />
            ) : (
              <Path path={GLYPHS[story.icon]} style="stroke" strokeWidth={1.4} strokeCap="round" strokeJoin="round" color="#ffffff" />
            )}
          </Canvas>
        </View>
        <Text style={styles.category} numberOfLines={1}>
          {story.category}
        </Text>
        <View style={styles.spacer} />
        <View style={styles.tag}>
          <Text style={styles.tagLabel} numberOfLines={1}>
            {story.tag}
          </Text>
          <Canvas style={styles.caret}>
            <Path path={CARET} color="rgba(255,255,255,0.9)" />
          </Canvas>
        </View>
      </Animated.View>

      <Animated.View style={[styles.figureRow, figure]}>
        <Text style={styles.figure} numberOfLines={1}>
          {shown}
          {story.unit ?? '%'}
        </Text>
        <View style={styles.figureArrow}>
          <Arrow size={13} opacity={0.9} />
        </View>
      </Animated.View>

      <Animated.View style={[styles.text, text]}>
        <Text style={styles.headline} numberOfLines={1}>
          {story.headline}
        </Text>
        <Text style={styles.detail} numberOfLines={2}>
          {story.detail}
        </Text>
      </Animated.View>

      <Animated.View style={[styles.thumbs, thumbs]}>
        {story.thumbs.map((thumb, n) => (
          <View key={n} style={styles.thumb}>
            <Picture item={thumb} />
          </View>
        ))}
      </Animated.View>
    </View>
  );
}

function Segment({ at, i, count, calm, width }: Shared & { width: number }) {
  // The story on screen fills its segment; the one before it fades back to dim.
  const lit = useAnimatedStyle(() => {
    const { index, elapsed } = at.get();
    if (i === index) return { opacity: 1, width: Math.max(BAR_HEIGHT, width * (calm ? 1 : ramp(elapsed, 0, STORY))) };
    return { opacity: i === (index + count - 1) % count ? 1 - ramp(elapsed, 0, SEGMENT_FADE) : 0, width };
  });
  return (
    <View style={[styles.segment, { width }]}>
      <Animated.View style={[styles.segmentLit, lit]} />
    </View>
  );
}

const AnimatedBlurView = Animated.createAnimatedComponent(BlurView);

/**
 * A dark statistics card that tells one travel fact at a time, like a story: a glow in the story's
 * colour, a category and a tag, a large thin figure, two lines of text, two photo pills and a
 * segmented bar. A tap moves to the next story: the card blurs, a photo washes over it in the new
 * colour, and the new figure counts up while its segment starts to fill.
 */
export function TravelStatStoryCard({ autoPlay = false, stories = STORIES, onChange }: Props) {
  const reducedMotion = useReducedMotion();
  const auto = autoPlay && !reducedMotion;
  const count = stories.length;

  // Seconds since the card appeared.
  const now = useSharedValue(0);
  // The loop opens on a story that has long settled and moves on FIRST_AT seconds later.
  const base = useSharedValue<Base>({ anchor: 0, since: -(STORY - (auto ? FIRST_AT : 0)), running: auto });
  const at = useDerivedValue(() => position(now.get(), base.get(), count));
  const [current, setCurrent] = useState(0);
  const resume = useRef<ReturnType<typeof setTimeout> | null>(null);
  const blurred = useRef<View>(null);

  useFrameCallback((frame) => {
    now.set(frame.timeSinceFirstFrame / 1000);
  });

  useEffect(() => {
    base.set({
      anchor: position(now.get(), base.get(), count).index,
      since: now.get() - (STORY - (auto ? FIRST_AT : 0)),
      running: auto,
    });
    return () => {
      if (resume.current) clearTimeout(resume.current);
    };
  }, [auto, count, base, now]);

  useAnimatedReaction(
    () => at.get().index,
    (index, old) => {
      if (index !== old) scheduleOnRN(setCurrent, index);
    },
  );

  const advance = () => {
    if (process.env.EXPO_OS !== 'web') Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
    const next = (position(now.get(), base.get(), count).index + 1) % count;
    base.set({ anchor: next, since: now.get(), running: false });
    // A touch takes over: the loop picks up from the tapped story once it has rested.
    if (resume.current) clearTimeout(resume.current);
    resume.current = setTimeout(() => base.set({ ...base.get(), running: auto }), RESUME_AFTER * 1000);
    onChange?.(next);
  };

  const blur = useAnimatedProps(() => ({ intensity: BLUR_INTENSITY * blurShare(at.get().elapsed, reducedMotion) }));

  const shared = { at, count, calm: reducedMotion };
  const story = stories[current] ?? stories[0];
  const segmentWidth = (BAR_WIDTH - BAR_GAP * (count - 1)) / count;

  return (
    <Pressable
      onPress={advance}
      accessibilityRole="button"
      accessibilityLabel={`${story.category}. ${story.figure}${story.unit ?? '%'} ${story.headline} ${story.detail}`}
      accessibilityHint="Shows the next story"
    >
      {({ pressed }) => (
        <Animated.View
          style={[
            styles.card,
            {
              transform: [{ scale: pressed ? 0.98 : 1 }],
              transitionProperty: 'transform',
              transitionDuration: 130,
              transitionTimingFunction: CSS_EASE_OUT,
            },
          ]}
        >
          <View style={styles.clip}>
            {/* What a story change blurs: the glows, the cover photos and the stories themselves. */}
            <BlurTargetView ref={blurred} style={styles.fill}>
              {stories.map((item, i) => (
                <Glow key={i} story={item} i={i} {...shared} />
              ))}
              {stories.map((item, i) => (
                <Cover key={i} story={item} i={i} {...shared} />
              ))}
              {stories.map((item, i) => (
                <Content key={i} story={item} i={i} {...shared} />
              ))}
            </BlurTargetView>
            <AnimatedBlurView
              style={styles.fill}
              tint="dark"
              blurTarget={blurred}
              blurMethod="dimezisBlurViewSdk31Plus"
              animatedProps={blur}
            />

            <View style={styles.open}>
              <Arrow size={12} />
            </View>

            <View style={styles.bar}>
              {stories.map((_, i) => (
                <Segment key={i} i={i} width={segmentWidth} {...shared} />
              ))}
            </View>
          </View>
        </Animated.View>
      )}
    </Pressable>
  );
}

const styles = StyleSheet.create({
  card: {
    width: WIDTH,
    height: HEIGHT,
    borderRadius: RADIUS,
    borderCurve: 'continuous',
    backgroundColor: '#151413',
    boxShadow: '0 20px 44px rgba(46, 30, 18, 0.3)',
  },
  clip: {
    flex: 1,
    borderRadius: RADIUS,
    borderCurve: 'continuous',
    overflow: 'hidden',
  },
  fill: {
    position: 'absolute',
    left: 0,
    top: 0,
    right: 0,
    bottom: 0,
  },
  header: {
    position: 'absolute',
    left: EDGE,
    top: EDGE,
    width: WIDTH - EDGE * 2 - CHIP - 4,
    height: CHIP,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 9,
  },
  chip: {
    width: CHIP,
    height: CHIP,
    borderRadius: CHIP / 2,
    backgroundColor: 'rgba(255,255,255,0.18)',
    alignItems: 'center',
    justifyContent: 'center',
  },
  glyph: {
    width: 16,
    height: 16,
  },
  category: {
    color: 'rgba(255,255,255,0.92)',
    fontSize: 12,
    lineHeight: 16,
    fontWeight: '500',
  },
  spacer: {
    flex: 1,
  },
  tag: {
    height: CHIP,
    paddingHorizontal: 14,
    borderRadius: CHIP / 2,
    borderWidth: 1,
    borderColor: 'rgba(255,255,255,0.42)',
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
  },
  tagLabel: {
    color: '#ffffff',
    fontSize: 11.5,
    lineHeight: 15,
    fontWeight: '500',
  },
  caret: {
    width: 7,
    height: 4.5,
  },
  figureRow: {
    position: 'absolute',
    left: PAD - 3,
    top: FIGURE_TOP,
    height: FIGURE_HEIGHT,
    flexDirection: 'row',
    alignItems: 'flex-start',
    gap: 7,
  },
  figure: {
    color: '#ffffff',
    fontSize: 78,
    lineHeight: FIGURE_HEIGHT,
    fontWeight: '300',
    letterSpacing: -2.5,
    fontVariant: ['tabular-nums'],
  },
  figureArrow: {
    marginTop: 21,
  },
  text: {
    position: 'absolute',
    left: PAD,
    top: TEXT_TOP,
    width: WIDTH - PAD * 2,
  },
  headline: {
    color: '#ffffff',
    fontSize: 13.5,
    lineHeight: 16,
    fontWeight: '600',
  },
  detail: {
    marginTop: 2,
    color: 'rgba(255,255,255,0.56)',
    fontSize: 13,
    lineHeight: 17.5,
  },
  thumbs: {
    position: 'absolute',
    left: PAD,
    top: THUMB_TOP,
    flexDirection: 'row',
    gap: 8,
  },
  thumb: {
    width: THUMB_WIDTH,
    height: THUMB_HEIGHT,
    borderRadius: THUMB_HEIGHT / 2,
    overflow: 'hidden',
  },
  open: {
    position: 'absolute',
    right: EDGE,
    top: EDGE,
    width: CHIP,
    height: CHIP,
    borderRadius: CHIP / 2,
    borderWidth: 1,
    borderColor: 'rgba(255,255,255,0.42)',
    alignItems: 'center',
    justifyContent: 'center',
  },
  bar: {
    position: 'absolute',
    left: (WIDTH - BAR_WIDTH) / 2,
    top: BAR_TOP,
    width: BAR_WIDTH,
    height: BAR_HEIGHT,
    flexDirection: 'row',
    gap: BAR_GAP,
  },
  segment: {
    height: BAR_HEIGHT,
    borderRadius: BAR_HEIGHT / 2,
    backgroundColor: 'rgba(255,255,255,0.22)',
  },
  segmentLit: {
    height: BAR_HEIGHT,
    borderRadius: BAR_HEIGHT / 2,
    backgroundColor: '#ffffff',
  },
});
