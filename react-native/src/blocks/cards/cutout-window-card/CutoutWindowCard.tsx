import * as Haptics from 'expo-haptics';
import { Image } from 'expo-image';
import { useEffect, useRef, useState } from 'react';
import { type GestureResponderEvent, StyleSheet, Text, View } from 'react-native';
import Animated, {
  type SharedValue,
  useAnimatedReaction,
  useAnimatedStyle,
  useFrameCallback,
  useReducedMotion,
  useSharedValue,
} from 'react-native-reanimated';
import Svg, { Circle, Polyline } from 'react-native-svg';
import { scheduleOnRN } from 'react-native-worklets';

export type WindowIcon = 'snow' | 'peak' | 'tree' | 'house' | 'sun' | 'sail' | 'glass' | 'fork';

export type WindowPage = {
  /** One or two lines, separated by a line break. */
  title: string;
  photo: string;
  /** Flat colour shown until the photo arrives. */
  tint: string;
  /** The four small lines at the bottom left. */
  details: { label: string; value: string }[];
  /** The four pictograms at the bottom right. */
  icons: WindowIcon[];
};

type Props = {
  /** The pages, in a ring. At least three. */
  pages?: WindowPage[];
  /** Turns a page every three seconds, as if a finger flicked it. */
  autoPlay?: boolean;
  /** Called with the index of the page the panel is settling on. */
  onChange?: (index: number) => void;
};

const WIDTH = 340;
const HEIGHT = 580;
const RADIUS = 28;
const CARD_WIDTH = 196;
const CARD_HEIGHT = 300;
const CARD_RADIUS = 8;
const CARD_X = (WIDTH - CARD_WIDTH) / 2;
const CARD_Y = (HEIGHT - CARD_HEIGHT) / 2;
const WINDOW_INSET = 10;
const WINDOW_WIDTH = CARD_WIDTH - WINDOW_INSET * 2;
const WINDOW_HEIGHT = 118;
const WINDOW_RADIUS = 3;
// The window's corner, measured in the panel.
const WINDOW_X = CARD_X + WINDOW_INSET;
const WINDOW_Y = CARD_Y + WINDOW_INSET;
// Points the picture lags behind its slide of the window over one page.
const WINDOW_DRIFT = 48;
// Opacity of the dark veil over the photo behind the card.
const SCRIM = 0.16;

// The box the titles roll through, and the one of the four small lines.
const TITLE_Y = 138;
const TITLE_LINE = 30;
const TITLE_BOX = 64;
const DETAIL_LINE = 10.5;
const DETAILS_BOX = DETAIL_LINE * 4;
const ICON = 14;
const ICON_GAP = 4;
const ICONS_WIDTH = ICON * 4 + ICON_GAP * 3;
const DETAILS_WIDTH = CARD_WIDTH - 24 - ICONS_WIDTH - 6;

// Distance of the eye from the card, in points: sets how strong the perspective is.
const PERSPECTIVE = 800;
// Degrees the card turns per page per second of slide, and the most it turns.
const TILT_GAIN = 2.6;
const TILT_MAX = 13;
// How fast the turn follows the speed, per second.
const TILT_RATE = 12;
// Points of the card's side that show per degree of turn.
const EDGE = 0.34;

// The spring that carries the pages to rest (mass 1).
const STIFFNESS = 150;
const DAMPING = 20;
// Finger travel that turns one page.
const DRAG_SPAN = 260;
const DRAG_SLOP = 6;
// A release lands on the page the slide would reach after this many seconds at its speed.
const THROW_TIME = 0.18;

// autoPlay (seconds): the three default pages go round once per LOOP, the first flick at FIRST
// and the next ones every STEP.
const LOOP = 9;
const FIRST = 0.8;
const STEP = LOOP / 3;
// After a touch the loop waits this long before it starts again.
const USER_HOLD = 2.5;

const INK = '#0f3d2a';

const photo = (id: string) => `https://images.unsplash.com/photo-${id}?auto=format&fit=crop&w=680&h=1160&q=80`;

const PAGES: WindowPage[] = [
  {
    title: 'Winter\nBreaks',
    photo: photo('1506905925346-21bda4d32df4'),
    tint: '#6f7391',
    details: [
      { label: 'DATE', value: '11 Apr 2024' },
      { label: 'FROM', value: 'London Gatwick' },
      { label: 'FOR', value: '14 Nights' },
      { label: 'BOARD', value: 'Half Board' },
    ],
    icons: ['snow', 'peak', 'tree', 'house'],
  },
  {
    title: 'Villa\nEscapes',
    photo: photo('1516483638261-f4dbaf036963'),
    tint: '#a98a78',
    details: [
      { label: 'DATE', value: '03 May 2024' },
      { label: 'FROM', value: 'London Gatwick' },
      { label: 'FOR', value: '4 Nights' },
      { label: 'BOARD', value: 'Self-Catering' },
    ],
    icons: ['house', 'sun', 'sail', 'glass'],
  },
  {
    title: 'Summer\nHot Spots',
    photo: photo('1501785888041-af3ef285b470'),
    tint: '#4a9d97',
    details: [
      { label: 'DATE', value: '19 Jul 2024' },
      { label: 'FROM', value: 'London Gatwick' },
      { label: 'FOR', value: '7 Nights' },
      { label: 'BOARD', value: 'Room Only' },
    ],
    icons: ['sun', 'sail', 'tree', 'fork'],
  },
];

// Polylines of each pictogram in a 16 × 16 box, as "x,y x,y" points. The sun also has a circle of
// radius 2.7 in its middle.
const STROKES: Record<WindowIcon, string[]> = {
  snow: ['8,1.8 8,14.2', '2.6,4.9 13.4,11.1', '2.6,11.1 13.4,4.9'],
  peak: ['1.2,13.6 6,4 8.6,8.8 10.6,6.2 14.8,13.6 1.2,13.6'],
  tree: ['8,1.8 3.4,10.6 12.6,10.6 8,1.8', '8,10.6 8,14.2'],
  house: ['1.8,8 8,2.4 14.2,8', '3.6,6.8 3.6,13.8 12.4,13.8 12.4,6.8', '6.8,13.8 6.8,10.2 9.2,10.2 9.2,13.8'],
  sun: [
    '8,1.4 8,3',
    '8,13 8,14.6',
    '1.4,8 3,8',
    '13,8 14.6,8',
    '3.3,3.3 4.5,4.5',
    '12.7,3.3 11.5,4.5',
    '3.3,12.7 4.5,11.5',
    '12.7,12.7 11.5,11.5',
  ],
  sail: ['8,1.8 8,10', '8,2.6 12.8,10 8,10', '6.6,4.8 3.6,10 6.6,10 6.6,4.8', '2,12 14,12 11.8,14.4 4.2,14.4 2,12'],
  glass: ['3,2.6 13,2.6 8,8.4 3,2.6', '8,8.4 8,13.8', '5.2,13.8 10.8,13.8'],
  fork: ['4.6,1.8 4.6,14.2', '2.8,1.8 2.8,5.6 6.4,5.6 6.4,1.8', '11.2,14.2 11.2,1.8 13.2,5 13.2,8.2 11.2,8.2'],
};

function wrapIndex(index: number, count: number) {
  'worklet';
  return ((index % count) + count) % count;
}

/** Pages flicked `clock` seconds into the autoPlay loop. */
function flicks(clock: number, count: number) {
  'worklet';
  let done = 0;
  for (let index = 0; index < count; index++) if (clock >= FIRST + STEP * index) done += 1;
  return done;
}

/** Where page `index` stands: 0 in view, 1 one page to the right, −1 one page to the left. */
function relative(index: number, pos: number, count: number) {
  'worklet';
  let place = (index - pos) % count;
  if (place <= -count / 2) place += count;
  if (place > count / 2) place -= count;
  return place;
}

function smooth(t: number) {
  'worklet';
  const x = Math.min(1, Math.max(0, t));
  return x * x * (3 - 2 * x);
}

/** Opacity of a text that rolls through its box with the page at `place`: it fades only as it leaves the box. */
function rollFade(place: number) {
  'worklet';
  return smooth((0.75 - Math.abs(place)) / 0.3);
}

/** Reduced motion: the pages stay in place and the one further away fades over the nearer one. */
function crossFade(place: number) {
  'worklet';
  return Math.abs(place) <= 0.5 ? 1 : Math.max(0, 1 - Math.abs(place));
}

/**
 * Opacity of pictogram number `order` of the page at `place`: gone before the page is half-way
 * out, and the further right the later it comes.
 */
function popFade(place: number, order: number) {
  'worklet';
  return smooth((0.47 - 0.03 * order - Math.abs(place)) / 0.2);
}

type Motion = {
  pos: SharedValue<number>;
  index: number;
  count: number;
  reduced: boolean;
};

/** The photo over a flat tint of its main colour, faded in when it arrives. */
function Photo({ page }: { page: WindowPage }) {
  return (
    <View style={[styles.photo, { backgroundColor: page.tint }]}>
      <Image source={page.photo} style={StyleSheet.absoluteFill} contentFit="cover" transition={200} accessible={false} />
    </View>
  );
}

/** One page's photo across the whole panel. */
function Backdrop({ page, pos, index, count, reduced }: { page: WindowPage } & Motion) {
  const style = useAnimatedStyle(() => {
    const place = relative(index, pos.get(), count);
    return {
      opacity: reduced ? crossFade(place) : 1,
      zIndex: Math.abs(place) > 0.5 ? 1 : 0,
      transform: [{ translateX: reduced ? 0 : place * WIDTH }],
    };
  });
  return (
    <Animated.View style={[styles.backdrop, style]}>
      <Photo page={page} />
    </Animated.View>
  );
}

/** One page's slide of the window, holding the photo where the panel shows it. */
function WindowSlide({ page, pos, index, count, reduced }: { page: WindowPage } & Motion) {
  const slide = useAnimatedStyle(() => {
    const place = relative(index, pos.get(), count);
    return {
      opacity: reduced ? crossFade(place) : 1,
      zIndex: Math.abs(place) > 0.5 ? 1 : 0,
      transform: [{ translateX: reduced ? 0 : place * WINDOW_WIDTH }],
    };
  });
  const picture = useAnimatedStyle(() => ({
    transform: [{ translateX: reduced ? 0 : -relative(index, pos.get(), count) * WINDOW_DRIFT }],
  }));
  return (
    <Animated.View style={[styles.windowSlide, slide]}>
      <Animated.View style={[styles.windowPicture, picture]}>
        <Photo page={page} />
      </Animated.View>
    </Animated.View>
  );
}

function Title({ page, pos, index, count, reduced }: { page: WindowPage } & Motion) {
  const style = useAnimatedStyle(() => {
    const place = relative(index, pos.get(), count);
    return { opacity: rollFade(place), transform: [{ translateY: reduced ? 0 : place * TITLE_BOX }] };
  });
  return (
    <Animated.Text style={[styles.title, style]} numberOfLines={2}>
      {page.title}
    </Animated.Text>
  );
}

function Details({ page, pos, index, count, reduced }: { page: WindowPage } & Motion) {
  const style = useAnimatedStyle(() => {
    const place = relative(index, pos.get(), count);
    return { opacity: rollFade(place), transform: [{ translateY: reduced ? 0 : place * DETAILS_BOX }] };
  });
  return (
    <Animated.View style={[styles.details, style]}>
      {page.details.map((detail) => (
        <Text key={detail.label} style={styles.detail} numberOfLines={1}>
          <Text style={styles.detailLabel}>{detail.label}</Text> {detail.value}
        </Text>
      ))}
    </Animated.View>
  );
}

/** A small line pictogram, drawn from the same strokes in every stack. */
function Glyph({ icon, order, pos, index, count, reduced }: { icon: WindowIcon; order: number } & Motion) {
  const style = useAnimatedStyle(() => {
    const shown = popFade(relative(index, pos.get(), count), order);
    return { opacity: shown, transform: [{ scale: reduced ? 1 : 0.6 + 0.4 * shown }] };
  });
  return (
    <Animated.View style={style}>
      <Svg width={ICON} height={ICON} viewBox="0 0 16 16" fill="none">
        {STROKES[icon].map((points) => (
          <Polyline key={points} points={points} stroke={INK} strokeWidth={1.71} strokeLinecap="round" strokeLinejoin="round" />
        ))}
        {icon === 'sun' ? <Circle cx={8} cy={8} r={2.7} stroke={INK} strokeWidth={1.71} /> : null}
      </Svg>
    </Animated.View>
  );
}

/**
 * A photo panel with a cream card standing in its middle. The top of the card is a window onto the
 * same photo, lined up with the one behind. A sideways swipe turns the page: the photos slide, the
 * card turns around its vertical axis with the speed of the slide and straightens as it settles,
 * the picture in the window slides at its own slower rate, and the texts roll over.
 */
export function CutoutWindowCard({ pages = PAGES, autoPlay = false, onChange }: Props) {
  const reducedMotion = useReducedMotion();
  const auto = autoPlay && !reducedMotion;
  const count = pages.length;

  // The page in view, with fractions while it slides.
  const pos = useSharedValue(0);
  const target = useSharedValue(0);
  // Pages per second.
  const velocity = useSharedValue(0);
  // Degrees the card is turned around its vertical axis.
  const tilt = useSharedValue(0);
  const looping = useSharedValue(auto);
  // Where the loop started counting from, and its clock.
  const base = useSharedValue(0);
  const clock = useSharedValue(0);
  // Seconds the loop still waits after a touch.
  const hold = useSharedValue(0);
  const touching = useSharedValue(false);
  const previous = useSharedValue(0);

  useEffect(() => {
    looping.set(auto);
    hold.set(0);
    clock.set(0);
    base.set(target.get());
  }, [auto, base, clock, hold, looping, target]);

  useFrameCallback((frame) => {
    const dt = Math.min(frame.timeSincePreviousFrame ?? 16, 64) / 1000;
    let p = pos.get();
    let goal = target.get();
    let v = velocity.get();
    let before = previous.get();

    if (looping.get() && hold.get() <= 0) {
      const loop = STEP * count;
      let t = clock.get() + dt;
      if (t >= loop) {
        // Once round the ring: the same pages under smaller numbers.
        t -= loop;
        p -= count;
        before -= count;
      }
      clock.set(t);
      goal = base.get() + flicks(t, count);
    } else if (hold.get() > 0 && !touching.get()) {
      hold.set(hold.get() - dt);
      if (hold.get() <= 0) {
        if (p === goal) {
          p = wrapIndex(Math.round(p), count);
          before = p;
          goal = p;
          base.set(p);
          clock.set(0);
        } else {
          hold.set(0.01);
        }
      }
    }

    if (touching.get()) {
      // The finger moves the pages; keep a smoothed speed for the release and the turn.
      v += ((p - before) / dt - v) * Math.min(1, dt * 30);
    } else {
      v += (STIFFNESS * (goal - p) - DAMPING * v) * dt;
      p += v * dt;
      if (Math.abs(goal - p) < 0.0004 && Math.abs(v) < 0.004) {
        p = goal;
        v = 0;
      }
    }

    const lean = Math.min(TILT_MAX, Math.max(-TILT_MAX, -v * TILT_GAIN));
    let turned = tilt.get() + (lean - tilt.get()) * (1 - Math.exp(-dt * TILT_RATE));
    if (v === 0 && Math.abs(turned) < 0.01) turned = 0;

    pos.set(p);
    previous.set(p);
    target.set(goal);
    velocity.set(v);
    tilt.set(turned);
  });

  const [current, setCurrent] = useState(0);
  const changed = (index: number) => {
    setCurrent(index);
    onChange?.(index);
  };
  useAnimatedReaction(
    () => wrapIndex(Math.round(target.get()), count),
    (now, old) => {
      if (old !== null && now !== old) scheduleOnRN(changed, now);
    },
  );

  const cardStyle = useAnimatedStyle(() => ({
    transform: [{ perspective: PERSPECTIVE }, { rotateY: `${reducedMotion ? 0 : tilt.get()}deg` }],
  }));
  // The card's side: it shows on the edge that turns towards the viewer.
  const edgeStyle = useAnimatedStyle(() => ({ transform: [{ translateX: reducedMotion ? 0 : -tilt.get() * EDGE }] }));
  // Turned away from the light, the face gets a little darker.
  const shadeStyle = useAnimatedStyle(() => ({ opacity: reducedMotion ? 0 : Math.abs(tilt.get()) * 0.006 }));

  const finger = useRef({ x: 0, grabbed: 0, moved: false });

  const onGrant = (e: GestureResponderEvent) => {
    finger.current = { x: e.nativeEvent.pageX, grabbed: 0, moved: false };
  };

  const onMove = (e: GestureResponderEvent) => {
    const f = finger.current;
    const dx = e.nativeEvent.pageX - f.x;
    if (!f.moved) {
      if (Math.abs(dx) < DRAG_SLOP) return;
      // A touch takes over from the loop, which starts again from rest a little later.
      f.moved = true;
      f.grabbed = pos.get();
      touching.set(true);
    }
    hold.set(USER_HOLD);
    // One page at most per swipe.
    const home = Math.round(f.grabbed);
    pos.set(Math.min(home + 1, Math.max(home - 1, f.grabbed - dx / DRAG_SPAN)));
  };

  const onEnd = () => {
    const f = finger.current;
    if (!f.moved) return;
    f.moved = false;
    touching.set(false);
    hold.set(USER_HOLD);
    const home = Math.round(f.grabbed);
    const landing = Math.round(pos.get() + velocity.get() * THROW_TIME);
    const next = Math.min(home + 1, Math.max(home - 1, landing));
    if (next !== Math.round(target.get()) && process.env.EXPO_OS !== 'web') Haptics.selectionAsync().catch(() => {});
    target.set(next);
  };

  const turn = (by: number) => {
    hold.set(USER_HOLD);
    target.set(Math.round(target.get()) + by);
  };

  const motion = { pos, count, reduced: reducedMotion };

  return (
    <View
      style={styles.panel}
      accessible
      accessibilityRole="adjustable"
      accessibilityLabel={pages[current].title.replace('\n', ' ')}
      accessibilityValue={{ text: pages[current].details.map((detail) => `${detail.label} ${detail.value}`).join(', ') }}
      accessibilityActions={[{ name: 'increment' }, { name: 'decrement' }]}
      onAccessibilityAction={(event) => turn(event.nativeEvent.actionName === 'increment' ? 1 : -1)}
      onStartShouldSetResponder={() => true}
      onResponderTerminationRequest={() => false}
      onResponderGrant={onGrant}
      onResponderMove={onMove}
      onResponderRelease={onEnd}
      onResponderTerminate={onEnd}
    >
      <View pointerEvents="none" style={StyleSheet.absoluteFill}>
        {pages.map((page, index) => (
          <Backdrop key={index} page={page} index={index} {...motion} />
        ))}
      </View>
      {/* The photo behind is a little darker than the picture in the window. */}
      <View pointerEvents="none" style={styles.scrim} />

      <Animated.View pointerEvents="none" style={[styles.card, cardStyle]}>
        <Animated.View style={[styles.edge, edgeStyle]} />
        <View style={styles.face} />

        <View style={styles.window}>
          {pages.map((page, index) => (
            <WindowSlide key={index} page={page} index={index} {...motion} />
          ))}
        </View>
        <View style={styles.windowRim} />

        {/* The title rolls up and out of its box as the next one rolls in from below. */}
        <View style={styles.titleBox}>
          {pages.map((page, index) => (
            <Title key={index} page={page} index={index} {...motion} />
          ))}
        </View>

        <View style={styles.detailsBox}>
          {pages.map((page, index) => (
            <Details key={index} page={page} index={index} {...motion} />
          ))}
        </View>

        {/* The pictograms change one after another. */}
        {pages.map((page, index) => (
          <View key={index} style={styles.icons}>
            {page.icons.map((icon, order) => (
              <Glyph key={order} icon={icon} order={order} index={index} {...motion} />
            ))}
          </View>
        ))}

        <Animated.View style={[styles.shade, shadeStyle]} />
      </Animated.View>
    </View>
  );
}

const styles = StyleSheet.create({
  panel: {
    width: WIDTH,
    height: HEIGHT,
    borderRadius: RADIUS,
    borderCurve: 'continuous',
    overflow: 'hidden',
    backgroundColor: '#000000',
  },
  backdrop: {
    position: 'absolute',
    left: 0,
    top: 0,
  },
  photo: {
    width: WIDTH,
    height: HEIGHT,
  },
  scrim: {
    ...StyleSheet.absoluteFill,
    backgroundColor: `rgba(8,14,11,${SCRIM})`,
  },
  card: {
    position: 'absolute',
    left: CARD_X,
    top: CARD_Y,
    width: CARD_WIDTH,
    height: CARD_HEIGHT,
  },
  edge: {
    ...StyleSheet.absoluteFill,
    borderRadius: CARD_RADIUS,
    borderCurve: 'continuous',
    backgroundColor: '#d3cbb7',
  },
  face: {
    ...StyleSheet.absoluteFill,
    borderRadius: CARD_RADIUS,
    borderCurve: 'continuous',
    backgroundColor: '#f7f3ea',
    boxShadow: '0 14px 44px rgba(0,0,0,0.34)',
  },
  window: {
    position: 'absolute',
    left: WINDOW_INSET,
    top: WINDOW_INSET,
    width: WINDOW_WIDTH,
    height: WINDOW_HEIGHT,
    borderRadius: WINDOW_RADIUS,
    borderCurve: 'continuous',
    overflow: 'hidden',
  },
  windowSlide: {
    ...StyleSheet.absoluteFill,
    overflow: 'hidden',
  },
  windowPicture: {
    position: 'absolute',
    left: -WINDOW_X,
    top: -WINDOW_Y,
  },
  windowRim: {
    position: 'absolute',
    left: WINDOW_INSET,
    top: WINDOW_INSET,
    width: WINDOW_WIDTH,
    height: WINDOW_HEIGHT,
    borderRadius: WINDOW_RADIUS,
    borderCurve: 'continuous',
    borderWidth: 1,
    borderColor: 'rgba(15,61,42,0.16)',
  },
  titleBox: {
    position: 'absolute',
    left: 12,
    top: TITLE_Y,
    width: CARD_WIDTH - 24,
    height: TITLE_BOX,
    overflow: 'hidden',
  },
  title: {
    position: 'absolute',
    left: 0,
    top: 2,
    color: INK,
    fontSize: 31,
    lineHeight: TITLE_LINE,
    fontWeight: '800',
    letterSpacing: -1.3,
  },
  detailsBox: {
    position: 'absolute',
    left: 12,
    top: CARD_HEIGHT - 14 - DETAILS_BOX,
    width: DETAILS_WIDTH,
    height: DETAILS_BOX,
    overflow: 'hidden',
  },
  details: {
    position: 'absolute',
    left: 0,
    top: 0,
  },
  detail: {
    color: INK,
    fontSize: 7.5,
    lineHeight: DETAIL_LINE,
    fontWeight: '500',
  },
  detailLabel: {
    fontWeight: '800',
  },
  icons: {
    position: 'absolute',
    left: CARD_WIDTH - 12 - ICONS_WIDTH,
    top: CARD_HEIGHT - 14 - ICON,
    flexDirection: 'row',
    gap: ICON_GAP,
  },
  shade: {
    ...StyleSheet.absoluteFill,
    borderRadius: CARD_RADIUS,
    borderCurve: 'continuous',
    backgroundColor: '#000000',
  },
});
