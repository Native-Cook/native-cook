import { MaterialCommunityIcons, MaterialIcons } from '@expo/vector-icons';
import { BlurMask, Canvas, Group, LinearGradient as Shade, Oval, Path, Skia, vec } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { Image } from 'expo-image';
import { LinearGradient } from 'expo-linear-gradient';
import { StatusBar } from 'expo-status-bar';
import { useEffect, useEffectEvent, useMemo, useState } from 'react';
import { type LayoutChangeEvent, Pressable, StyleSheet, Text, View, useWindowDimensions } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
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
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';

import {
  AUTO_FEEDS,
  AUTO_ORBIT,
  AUTO_REST,
  BACK,
  BLINK,
  BOOK_HEIGHT,
  BOOK_TOP,
  BOOK_WIDTH,
  BULGE,
  CENTER_X,
  COLLAPSE,
  DIP,
  type Drop,
  FEED_AFTER,
  type Hold,
  LIFT,
  LINE_SHIFT,
  LOOP,
  type Mode,
  ORBIT_IN,
  ORBIT_Y,
  PEEK,
  POCKET_IN,
  RINGS,
  SHRINK,
  STAGE_HEIGHT,
  STAGE_WIDTH,
  type Tagline,
  TYPE_AFTER,
  USER_HOLD,
  WAIT_AFTER,
  WAIT_IN,
  type Wait,
  bookmarkPose,
  cardPose,
  gulpAt,
  iconPose,
  ringsAt,
  typedAt,
  typedBy,
} from './motion';

export type PocketCard = {
  /** A picture's address, and the colour shown until it has loaded. */
  photo: string;
  tint: string;
  /** The card is 140 points wide and this tall. */
  height: number;
  /** Up to three short lines over the picture; the middle one is the loud one. */
  overlay?: string[];
  badge?: string;
  /** With a headline the card is an article: the picture on top, the words on black below. */
  headline?: string;
};

type Props = {
  name?: string;
  /** What the tagline says under the big bookmark, and among the orbits. */
  taglines?: [string, string];
  buttonLabel?: string;
  /** The saved posts that fall into the bookmark, in turn. */
  cards?: PocketCard[];
  backgroundColor?: string;
  inkColor?: string;
  accentColor?: string;
  /** Feeds three cards, opens the orbits and comes back, every LOOP seconds. */
  autoPlay?: boolean;
  /** Called with the card's place in `cards` when it is let go into the bookmark. */
  onFeed?: (card: number) => void;
  /** Called when the bookmark is tapped: true for the orbits, false for the pocket. */
  onModeChange?: (orbit: boolean) => void;
  onStart?: () => void;
};

const PHOTO = '?auto=format&fit=crop&w=420&h=600&q=80';
const CARDS: PocketCard[] = [
  { photo: `https://images.unsplash.com/photo-1516483638261-f4dbaf036963${PHOTO}`, tint: '#b9a48c', height: 170 },
  {
    photo: `https://images.unsplash.com/photo-1513635269975-59663e0ac1ad${PHOTO}`,
    tint: '#7d8794',
    height: 186,
    overlay: ['Best value', 'RESTAURANTS', 'in London'],
    badge: 'Part 16',
  },
  {
    photo: `https://images.unsplash.com/photo-1502602898657-3e91760cbb34${PHOTO}`,
    tint: '#6f6a86',
    height: 220,
    headline: '53 unmissable\nthings to do in\nParis right now',
  },
];

const BACKGROUND = '#f6f3f7';
const INK = '#1c1a1d';
const TAGLINE = '#3a3a3c';
const ACCENT = '#e5484d';
const BUTTON = '#050505';
const RING = '#dcd8de';
const BOOK_SHADE = ['#484649', '#141113'];
const GLOWS = ['#ff5fa2', '#ffd34d'];

// The bookmark's corners: at the top, at its two tips, and in the notch between them.
const TOP_RADIUS = 12;
const TIP_RADIUS = 8;
const NOTCH = 35;
const NOTCH_RADIUS = 5;
const CARD_WIDTH = 140;
const CARD_RADIUS = 10;
// Cards are cut off at this height, well behind the bookmark, so none shows through its notch.
const CLIP = 430;

const MARGIN = 16;
const NAME_LINE = 32;
const TAG_LINE = 27;
const TEXT_GAP = 3;
const BUTTON_HEIGHT = 43;
const BUTTON_GAP = 6;

/** A card was fed, or the picture changed; by a finger or by the loop that plays by itself. */
type StageEvent = { fed?: number; orbit?: boolean; byHand: boolean };

type Glyph = 'camera' | 'globe' | 'bulb' | 'arrow' | 'music' | 'film' | 'fork' | 'run';

/** The places things are saved from: which ring, where on it in degrees, its tile and its glyph. */
const ICONS: { ring: number; angle: number; size: number; radius: number; colors: [string, string]; ink: string; glyph: Glyph; glyphSize: number }[] = [
  { ring: 0, angle: -131, size: 64, radius: 15, colors: ['#d62f8a', '#f7a23b'], ink: '#ffffff', glyph: 'camera', glyphSize: 34 },
  { ring: 0, angle: 19, size: 66, radius: 33, colors: ['#39b5f5', '#0960ea'], ink: '#ffffff', glyph: 'globe', glyphSize: 38 },
  { ring: 0, angle: 164, size: 44, radius: 22, colors: ['#fff3c4', '#fff3c4'], ink: '#f2b600', glyph: 'bulb', glyphSize: 23 },
  { ring: 0, angle: -39, size: 44, radius: 22, colors: ['#dcebff', '#dcebff'], ink: '#1877f2', glyph: 'arrow', glyphSize: 23 },
  { ring: 1, angle: -54, size: 58, radius: 13, colors: ['#18181a', '#050505'], ink: '#ffffff', glyph: 'music', glyphSize: 32 },
  { ring: 1, angle: -140, size: 44, radius: 22, colors: ['#ecdffb', '#ecdffb'], ink: '#8e44ec', glyph: 'film', glyphSize: 23 },
  { ring: 1, angle: 145, size: 44, radius: 22, colors: ['#fde0e0', '#fde0e0'], ink: '#f0443f', glyph: 'fork', glyphSize: 23 },
  { ring: 1, angle: 36, size: 44, radius: 22, colors: ['#daf5e0', '#daf5e0'], ink: '#22b455', glyph: 'run', glyphSize: 23 },
];

const MATERIAL: Record<Exclude<Glyph, 'camera'>, keyof typeof MaterialIcons.glyphMap> = {
  globe: 'language',
  bulb: 'lightbulb',
  arrow: 'near-me',
  music: 'music-note',
  film: 'theaters',
  fork: 'restaurant',
  run: 'directions-run',
};

/**
 * The bookmark with its top edge on y = 0 and its middle on x = 0. `bulge` is how far each side
 * bows out; the top edge dips in proportion.
 */
function bookmarkPath(bulge: number) {
  'worklet';
  const w = BOOK_WIDTH / 2;
  const h = BOOK_HEIGHT;
  const dip = (DIP * bulge) / BULGE;
  // The unit step from a tip towards the notch.
  const run = Math.hypot(w, NOTCH);
  const ux = w / run;
  const uy = NOTCH / run;
  const path = Skia.Path.Make();
  path.moveTo(-w + TOP_RADIUS, 0);
  path.quadTo(0, 2 * dip, w - TOP_RADIUS, 0);
  path.quadTo(w, 0, w, TOP_RADIUS);
  path.quadTo(w + 2 * bulge, h / 2, w, h - TIP_RADIUS);
  path.quadTo(w, h, w - TIP_RADIUS * ux, h - TIP_RADIUS * uy);
  path.lineTo(NOTCH_RADIUS * ux, h - NOTCH + NOTCH_RADIUS * uy);
  path.quadTo(0, h - NOTCH, -NOTCH_RADIUS * ux, h - NOTCH + NOTCH_RADIUS * uy);
  path.lineTo(-w + TIP_RADIUS * ux, h - TIP_RADIUS * uy);
  path.quadTo(-w, h, -w, h - TIP_RADIUS);
  path.quadTo(-w - 2 * bulge, h / 2, -w, TOP_RADIUS);
  path.quadTo(-w, 0, -w + TOP_RADIUS, 0);
  path.close();
  return path;
}

function tick() {
  if (process.env.EXPO_OS !== 'web') Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
}

/** The cursor after the last letter: steady for a moment after each keystroke, then blinking. */
function Cursor({ color, still }: { color: string; still: boolean }) {
  const [on, setOn] = useState(true);
  useEffect(() => {
    if (still) return;
    const timer = setInterval(() => setOn((now) => !now), BLINK * 1000);
    return () => clearInterval(timer);
  }, [still]);
  return <Text style={{ color: on ? color : 'transparent' }}>_</Text>;
}

/** What a saved post looks like: a picture, a picture under a few words, or an article. */
function CardFace({ card }: { card: PocketCard }) {
  if (card.headline) {
    return (
      <View style={[styles.face, styles.article]}>
        <Image source={{ uri: card.photo }} style={[styles.articlePhoto, { backgroundColor: card.tint }]} contentFit="cover" transition={200} />
        <Text style={styles.headline}>{card.headline}</Text>
        <View style={[styles.bar, { width: 104 }]} />
        <View style={[styles.bar, { width: 112 }]} />
        <View style={[styles.bar, { width: 72 }]} />
        <View style={styles.outline} />
      </View>
    );
  }
  return (
    <View style={[styles.face, { backgroundColor: card.tint }]}>
      <Image source={{ uri: card.photo }} style={StyleSheet.absoluteFill} contentFit="cover" transition={200} />
      {card.overlay ? (
        <View style={styles.overlay}>
          {card.overlay.map((line, index) => (
            <Text key={index} style={index === 1 ? styles.overlayLoud : styles.overlayLine} numberOfLines={1}>
              {line}
            </Text>
          ))}
          {card.badge ? (
            <View style={styles.badge}>
              <Text style={styles.badgeLabel}>{card.badge}</Text>
            </View>
          ) : null}
        </View>
      ) : null}
    </View>
  );
}

type Stage = {
  clock: SharedValue<number>;
  mode: SharedValue<Mode>;
  drops: SharedValue<Drop[]>;
  wait: SharedValue<Wait>;
  hold: SharedValue<Hold>;
  next: SharedValue<number>;
  turn: SharedValue<number>;
  /** The stage's y at the top of the screen. */
  top: number;
  still: boolean;
};

function Card({ index, card, stage }: { index: number; card: PocketCard; stage: Stage }) {
  const { clock, drops, wait, hold, next, top, still } = stage;
  const pose = useDerivedValue(() => cardPose(card.height, next.get() === index, wait.get(), hold.get(), drops.get()[index], top, clock.get(), still));
  const style = useAnimatedStyle(() => {
    const now = pose.get();
    return { opacity: now.opacity, transform: [{ translateX: now.x }, { translateY: now.bottom - top }, { scale: now.scale }] };
  });
  const darkStyle = useAnimatedStyle(() => ({ opacity: pose.get().dark }));
  return (
    <Animated.View style={[styles.card, { height: card.height, top: -card.height }, style]}>
      <CardFace card={card} />
      <Animated.View style={[styles.dark, darkStyle]} />
    </Animated.View>
  );
}

function Ring({ index, stage }: { index: number; stage: Stage }) {
  const { clock, mode, still } = stage;
  const radius = RINGS[index];
  const style = useAnimatedStyle(() => {
    const open = ringsAt(mode.get(), clock.get(), still);
    return { opacity: Math.min(1, open * 3), transform: [{ scale: still ? 1 : Math.max(open, 0.001) }] };
  });
  return (
    <Animated.View
      style={[styles.ring, { left: CENTER_X - radius, top: ORBIT_Y - radius, width: radius * 2, height: radius * 2, borderRadius: radius }, style]}
    />
  );
}

function OrbitIcon({ index, stage }: { index: number; stage: Stage }) {
  const { clock, mode, turn, still } = stage;
  const icon = ICONS[index];
  const style = useAnimatedStyle(() => {
    const now = iconPose(index, icon.ring, icon.angle, mode.get(), clock.get(), turn.get(), still);
    return { opacity: now.opacity, transform: [{ translateX: now.x }, { translateY: now.y }, { scale: Math.max(now.scale, 0.001) }] };
  });
  return (
    <Animated.View style={[styles.icon, { left: CENTER_X - icon.size / 2, top: ORBIT_Y - icon.size / 2, width: icon.size, height: icon.size }, style]}>
      <LinearGradient
        colors={icon.colors}
        start={{ x: 0.2, y: 0 }}
        end={{ x: 0.8, y: 1 }}
        style={[styles.tile, { borderRadius: icon.radius, borderCurve: icon.radius * 2 === icon.size ? 'circular' : 'continuous' }]}
      >
        {icon.glyph === 'camera' ? (
          <MaterialCommunityIcons name="camera-outline" size={icon.glyphSize} color={icon.ink} />
        ) : (
          <MaterialIcons name={MATERIAL[icon.glyph]} size={icon.glyphSize} color={icon.ink} />
        )}
      </LinearGradient>
    </Animated.View>
  );
}

/**
 * A welcome screen. Saved posts fall into a big bookmark, which gulps each one; a tap on it turns
 * it into the hub of two rings of the places things are saved from, and the tagline is typed
 * again for each picture. By hand the card at the top edge is dragged into the bookmark, and a
 * finger turns the rings.
 */
export function BookmarkPocketIntro({
  name = 'Stash',
  taglines = ['A new home for your internet hoarding tendencies', 'A new way to explore a new city'],
  buttonLabel = 'Get Started',
  cards = CARDS,
  backgroundColor = BACKGROUND,
  inkColor = INK,
  accentColor = ACCENT,
  autoPlay = false,
  onFeed,
  onModeChange,
  onStart,
}: Props) {
  const still = useReducedMotion();
  const insets = useSafeAreaInsets();
  const window = useWindowDimensions();
  // The screen as laid out; the window's size stands in until then.
  const [laidOut, setLaidOut] = useState<{ width: number; height: number } | null>(null);
  const size = laidOut ?? window;
  const scale = size.width / STAGE_WIDTH;
  // The stage's middle is on the screen's middle, so on most screens its top is a little off the edge.
  const offset = (size.height - STAGE_HEIGHT * scale) / 2;
  const top = -offset / scale;
  const [pocketLine, orbitLine] = taglines;

  // The tagline as typed so far, and on how many lines.
  const [typed, setTyped] = useState(autoPlay ? '' : still ? pocketLine : '');
  const [lines, setLines] = useState(1);
  // Changes on every touch, which restarts the autoPlay loop behind a pause.
  const [touches, setTouches] = useState(0);
  // The last thing that happened on the stage, for the callbacks and the haptics.
  const [event, setEvent] = useState<StageEvent | null>(null);

  // Seconds since the first frame: `now` always, `clock` only while something moves.
  const now = useSharedValue(0);
  const clock = useSharedValue(0);
  const mode = useSharedValue<Mode>({ orbit: false, at: -100, spun: 0 });
  const drops = useSharedValue<Drop[]>(cards.map(() => ({ at: -100, bottom: 0, dx: 0, glow: 0 })));
  // By itself the screen has no card waiting; one comes once a finger has touched it.
  const wait = useSharedValue<Wait>({ on: !autoPlay, at: autoPlay ? -100 : 0 });
  const hold = useSharedValue<Hold>({ dx: 0, dy: 0, at: -100 });
  const next = useSharedValue(0);
  // How many cards the pocket has had since it was last at rest: the glow grows with it.
  const fed = useSharedValue(0);
  const turn = useSharedValue(0);
  // What the pan has hold of: 1 the waiting card, 2 the rings; and the last angle of the finger.
  const grip = useSharedValue(0);
  const angle = useSharedValue(0);
  // At rest the tagline is empty when the screen plays by itself, and types itself otherwise.
  const tagline = useSharedValue<Tagline>({ from: '', to: autoPlay ? '' : pocketLine, at: autoPlay ? -100 : TYPE_AFTER });
  const settled = useSharedValue(autoPlay ? 0 : TYPE_AFTER + pocketLine.length / 20);
  // The name sits one line lower while the tagline has one line.
  const shift = useSharedValue(0);

  // The timestamp of the first frame. Every render registers the frame callback anew, which
  // starts its `timeSinceFirstFrame` over, so the time is counted from here instead.
  const born = useSharedValue(-1);

  useFrameCallback((frame) => {
    if (born.get() < 0) born.set(frame.timestamp);
    const t = (frame.timestamp - born.get()) / 1000;
    now.set(t);
    const moving = Math.min(t, settled.get());
    if (moving !== clock.get()) clock.set(moving);
  });

  useAnimatedReaction(
    () => typedAt(tagline.get(), clock.get(), still),
    (text, old) => {
      if (text !== old) scheduleOnRN(setTyped, text);
    },
  );

  useEffect(() => {
    shift.set(withTiming(lines > 1 ? 1 : 0, { duration: still ? 0 : LINE_SHIFT * 1000 }));
  }, [lines, shift, still]);

  const report = useEffectEvent((last: StageEvent | null) => {
    if (!last) return;
    if (last.byHand) tick();
    if (last.fed !== undefined) onFeed?.(last.fed);
    if (last.orbit !== undefined) onModeChange?.(last.orbit);
  });
  useEffect(() => report(event), [event]);

  const actions = useMemo(() => {
    const touchedOnce = () => setTouches((n) => n + 1);
    /** Runs the clock until nothing moves any more; the rings never stop. */
    const settle = () => {
      'worklet';
      const here = mode.get();
      let until = Math.max(here.at + ORBIT_IN, typedBy(tagline.get()), wait.get().at + WAIT_IN, now.get() + BACK);
      for (const drop of drops.get()) until = Math.max(until, drop.at + 1.5);
      settled.set(here.orbit && !still ? Infinity : until);
    };
    /** Changes what the tagline is going to say, from `at` on. */
    const retag = (to: string, at: number) => {
      'worklet';
      tagline.set({ from: typedAt(tagline.get(), at, still), to, at });
    };
    /** Lets a card go with its lower edge at `bottom`, `dx` aside of the middle. */
    const feed = (card: number, bottom: number, dx: number, byHand: boolean) => {
      'worklet';
      const t = now.get();
      const count = fed.get() + 1;
      fed.set(count);
      const glow = Math.pow(Math.min(1, count / 3), 2);
      drops.set(drops.get().map((drop, index) => (index === card ? { at: t, bottom, dx, glow } : drop)));
      if (tagline.get().to === '') retag(pocketLine, t + TYPE_AFTER);
      settle();
      scheduleOnRN(setEvent, { fed: card, byHand });
    };
    /** Goes to the orbits or back to the pocket; `rest` also empties the tagline. */
    const show = (orbit: boolean, how: 'tap' | 'auto' | 'rest') => {
      'worklet';
      const t = now.get();
      const old = mode.get();
      // One change at a time: a tap during a change is ignored.
      if (old.orbit === orbit || t < old.at + (old.orbit ? ORBIT_IN : POCKET_IN)) return;
      mode.set({ orbit, at: t, spun: old.orbit ? Math.max(0, t - old.at - SHRINK - LIFT) : 0 });
      fed.set(0);
      if (orbit) {
        turn.set(0);
        wait.set({ on: false, at: t });
        retag(orbitLine, t + ORBIT_IN);
      } else if (how !== 'rest') {
        if (how === 'tap') wait.set({ on: true, at: t + POCKET_IN - WAIT_IN });
        retag(pocketLine, t + COLLAPSE);
      }
      settle();
      scheduleOnRN(setEvent, { orbit, byHand: how === 'tap' });
    };
    /** Back to the screen as it first appears. */
    const rest = () => {
      'worklet';
      const t = now.get();
      show(false, 'rest');
      wait.set({ on: false, at: t });
      hold.set({ dx: 0, dy: 0, at: -100 });
      fed.set(0);
      retag('', t);
      settle();
    };
    /** A finger is on the screen: the card it can feed comes to the top edge. */
    const touched = () => {
      'worklet';
      const t = now.get();
      if (!wait.get().on && !mode.get().orbit) {
        // The card that fell longest ago is the one to wait.
        const list = drops.get();
        let oldest = 0;
        for (let i = 1; i < list.length; i++) if (list[i].at < list[oldest].at) oldest = i;
        next.set(oldest);
        wait.set({ on: true, at: t });
        settle();
      }
      scheduleOnRN(touchedOnce);
    };
    return { touched, touchedOnce, feed, show, rest, settle };
  }, [now, mode, drops, wait, hold, next, fed, turn, tagline, settled, still, pocketLine, orbitLine]);

  const gesture = useMemo(() => {
    const { touched, feed, show, settle } = actions;
    const count = cards.length;
    /** A point of the screen in the stage's own points. */
    const onStage = (x: number, y: number) => {
      'worklet';
      return { x: x / scale, y: (y - offset) / scale };
    };
    const tap = Gesture.Tap()
      .maxDuration(1000)
      .onEnd((event, success) => {
        if (!success) return;
        touched();
        const point = onStage(event.x, event.y);
        const here = mode.get();
        const hit = here.orbit
          ? Math.hypot(point.x - CENTER_X, point.y - ORBIT_Y) <= 56
          : Math.abs(point.x - CENTER_X) <= BOOK_WIDTH / 2 && point.y >= BOOK_TOP && point.y <= BOOK_TOP + BOOK_HEIGHT;
        if (hit) show(!here.orbit, 'tap');
      });
    const pan = Gesture.Pan()
      .minDistance(4)
      .onBegin(() => {
        touched();
      })
      .onStart((event) => {
        const point = onStage(event.x - event.translationX, event.y - event.translationY);
        const here = mode.get();
        const t = now.get();
        if (here.orbit) {
          // Anywhere above the words turns the rings, once they are open.
          if (t < here.at + ORBIT_IN || point.y > BOOK_TOP + BOOK_HEIGHT + 40) return;
          grip.set(2);
          angle.set(Math.atan2(point.y - ORBIT_Y, point.x - CENTER_X));
        } else if (wait.get().on && t >= wait.get().at + WAIT_IN && Math.abs(point.x - CENTER_X) <= CARD_WIDTH / 2 + 12 && point.y <= top + PEEK + 12) {
          grip.set(1);
        }
      })
      .onChange((event) => {
        if (grip.get() === 1) {
          hold.set({ dx: event.translationX / scale, dy: Math.max(-PEEK / 2, event.translationY / scale), at: Infinity });
        } else if (grip.get() === 2) {
          const point = onStage(event.x, event.y);
          const here = Math.atan2(point.y - ORBIT_Y, point.x - CENTER_X);
          // The short way round from the last angle to this one.
          const step = Math.atan2(Math.sin(here - angle.get()), Math.cos(here - angle.get()));
          angle.set(here);
          turn.set(turn.get() + (step * 180) / Math.PI);
        }
      })
      .onFinalize(() => {
        if (grip.get() === 1) {
          const t = now.get();
          const held = hold.get();
          if (held.dy > FEED_AFTER) {
            const card = next.get();
            next.set((card + 1) % count);
            hold.set({ dx: 0, dy: 0, at: -100 });
            wait.set({ on: true, at: t + WAIT_AFTER });
            feed(card, top + PEEK + held.dy, held.dx, true);
          } else {
            hold.set({ dx: held.dx, dy: held.dy, at: t });
            settle();
          }
        }
        grip.set(0);
      });
    return Gesture.Exclusive(pan, tap);
  }, [actions, cards.length, now, mode, wait, hold, next, turn, grip, angle, scale, offset, top]);

  useEffect(() => {
    if (!autoPlay) return;
    const timers: ReturnType<typeof setTimeout>[] = [];
    const after = (ms: number, run: () => void) => timers.push(setTimeout(run, ms));
    const cycle = (origin: number) => {
      AUTO_FEEDS.forEach((at, card) => {
        // By itself a card falls from just above the screen.
        after(origin + at * 1000 - Date.now(), () => scheduleOnUI(actions.feed, card % cards.length, top, 0, false));
      });
      after(origin + AUTO_ORBIT * 1000 - Date.now(), () => scheduleOnUI(actions.show, true, 'auto'));
      after(origin + AUTO_REST * 1000 - Date.now(), () => scheduleOnUI(actions.rest));
      after(origin + LOOP * 1000 - Date.now(), () => cycle(origin + LOOP * 1000));
    };
    if (touches === 0) cycle(Date.now());
    else {
      after(USER_HOLD * 1000, () => {
        // The way back to rest takes what is left of a loop after AUTO_REST.
        scheduleOnUI(actions.rest);
        cycle(Date.now() + (LOOP - AUTO_REST) * 1000);
      });
    }
    return () => timers.forEach(clearTimeout);
  }, [autoPlay, touches, actions, cards.length, top]);

  const stage: Stage = { clock, mode, drops, wait, hold, next, turn, top, still };

  const gulp = useDerivedValue(() => gulpAt(drops.get(), clock.get(), still));
  const outline = useDerivedValue(() => bookmarkPath(gulp.get().bulge));
  const glow = useDerivedValue(() => 0.6 * gulp.get().glow);
  const book = useDerivedValue(() => bookmarkPose(mode.get(), clock.get(), still));
  const bookOpacity = useDerivedValue(() => book.get().opacity);
  const bookTransform = useDerivedValue(() => {
    const pose = book.get();
    const middle = BOOK_TOP + BOOK_HEIGHT / 2;
    return [
      { translateX: CENTER_X },
      { translateY: middle + (ORBIT_Y - middle) * pose.lift },
      { rotate: (pose.tilt * Math.PI) / 180 },
      { scale: pose.scale },
      { translateY: -BOOK_HEIGHT / 2 },
    ];
  });
  const textStyle = useAnimatedStyle(() => ({ transform: [{ translateY: TAG_LINE * (1 - shift.get()) }] }));

  const onLayout = (event: LayoutChangeEvent) => {
    const { width, height } = event.nativeEvent.layout;
    setLaidOut({ width, height });
  };

  const bottom = Math.max(insets.bottom + 23, 28);

  return (
    <GestureDetector gesture={gesture}>
      <View style={[styles.screen, { backgroundColor }]} onLayout={onLayout}>
        <StatusBar style="dark" />

        <View
          pointerEvents="none"
          accessibilityElementsHidden
          importantForAccessibility="no-hide-descendants"
          style={[styles.stage, { top: offset, transform: [{ scale }] }]}
        >
          <Ring index={0} stage={stage} />
          <Ring index={1} stage={stage} />
          <View style={[styles.cards, { top, height: CLIP - top }]}>
            {cards.map((card, index) => (
              <Card key={index} index={index} card={card} stage={stage} />
            ))}
          </View>
          {ICONS.map((_, index) => (
            <OrbitIcon key={index} index={index} stage={stage} />
          ))}
        </View>

        <Canvas style={styles.canvas} pointerEvents="none">
          <Group transform={[{ translateY: offset }, { scale }]}>
            <Group transform={bookTransform} opacity={bookOpacity}>
              {/* Colour leaks from under the bookmark when it has had its fill. */}
              <Group opacity={glow}>
                <Oval x={-BOOK_WIDTH / 2 - 14} y={BOOK_HEIGHT - 70} width={110} height={80} color={GLOWS[0]}>
                  <BlurMask blur={30} style="normal" />
                </Oval>
                <Oval x={BOOK_WIDTH / 2 - 96} y={BOOK_HEIGHT - 70} width={110} height={80} color={GLOWS[1]}>
                  <BlurMask blur={30} style="normal" />
                </Oval>
              </Group>
              <Group transform={[{ translateY: 10 }]}>
                <Path path={outline} color="rgba(0, 0, 0, 0.18)">
                  <BlurMask blur={12} style="normal" />
                </Path>
              </Group>
              <Path path={outline}>
                <Shade start={vec(0, 0)} end={vec(0, BOOK_HEIGHT)} colors={BOOK_SHADE} />
              </Path>
            </Group>
          </Group>
        </Canvas>

        <View pointerEvents="none" style={[styles.words, { bottom: bottom + BUTTON_HEIGHT + BUTTON_GAP }]}>
          <Animated.View style={textStyle}>
            <Text style={[styles.name, { color: inkColor }]} numberOfLines={1}>
              {name}
              <Text style={{ color: accentColor }}>.</Text>
            </Text>
            <Text
              style={styles.tagline}
              numberOfLines={2}
              accessibilityLabel={typed}
              onTextLayout={(event) => setLines(Math.max(1, event.nativeEvent.lines.length))}
            >
              {typed}
              <Cursor key={typed} color={TAGLINE} still={still} />
            </Text>
          </Animated.View>
        </View>

        <Pressable
          style={[styles.buttonSlot, { bottom }]}
          accessibilityRole="button"
          onPressIn={actions.touchedOnce}
          onPress={() => {
            tick();
            onStart?.();
          }}
        >
          {({ pressed }) => (
            <Animated.View
              style={[
                styles.button,
                { opacity: pressed ? 0.8 : 1, transform: [{ scale: pressed && !still ? 0.98 : 1 }], transitionProperty: ['opacity', 'transform'], transitionDuration: 120 },
              ]}
            >
              <Text style={styles.buttonLabel} numberOfLines={1}>
                {buttonLabel}
              </Text>
            </Animated.View>
          )}
        </Pressable>
      </View>
    </GestureDetector>
  );
}

const styles = StyleSheet.create({
  screen: {
    flex: 1,
    overflow: 'hidden',
  },
  stage: {
    position: 'absolute',
    left: 0,
    width: STAGE_WIDTH,
    height: STAGE_HEIGHT,
    transformOrigin: 'left top',
  },
  canvas: {
    position: 'absolute',
    left: 0,
    right: 0,
    top: 0,
    bottom: 0,
  },
  ring: {
    position: 'absolute',
    borderWidth: 1,
    borderColor: RING,
  },
  cards: {
    position: 'absolute',
    left: 0,
    right: 0,
    overflow: 'hidden',
  },
  card: {
    position: 'absolute',
    left: CENTER_X - CARD_WIDTH / 2,
    width: CARD_WIDTH,
    borderRadius: CARD_RADIUS,
    borderCurve: 'continuous',
    overflow: 'hidden',
    // Everything a card does is measured from its lower edge.
    transformOrigin: ['50%', '100%', 0],
  },
  face: {
    flex: 1,
  },
  dark: {
    position: 'absolute',
    left: 0,
    right: 0,
    top: 0,
    bottom: 0,
    backgroundColor: '#000000',
  },
  article: {
    backgroundColor: '#0b0b0c',
    paddingHorizontal: 9,
  },
  articlePhoto: {
    height: 92,
    marginHorizontal: -9,
    marginBottom: 9,
  },
  headline: {
    color: '#ffffff',
    fontSize: 11,
    lineHeight: 14,
    fontWeight: '700',
    marginBottom: 8,
    includeFontPadding: false,
  },
  bar: {
    height: 3,
    borderRadius: 1.5,
    marginBottom: 4,
    backgroundColor: 'rgba(255, 255, 255, 0.28)',
  },
  outline: {
    width: 62,
    height: 16,
    marginTop: 6,
    borderRadius: 8,
    borderWidth: 1,
    borderColor: 'rgba(255, 255, 255, 0.5)',
  },
  overlay: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: 'rgba(0, 0, 0, 0.28)',
  },
  overlayLine: {
    color: '#ffffff',
    fontSize: 9,
    lineHeight: 12,
    fontWeight: '600',
    includeFontPadding: false,
  },
  overlayLoud: {
    color: '#ffffff',
    fontSize: 12,
    lineHeight: 16,
    fontWeight: '800',
    letterSpacing: 0.6,
    includeFontPadding: false,
  },
  badge: {
    position: 'absolute',
    bottom: 10,
    height: 16,
    paddingHorizontal: 7,
    borderRadius: 8,
    backgroundColor: '#ffffff',
    justifyContent: 'center',
  },
  badgeLabel: {
    color: '#1c1a1d',
    fontSize: 7,
    lineHeight: 10,
    fontWeight: '700',
    includeFontPadding: false,
  },
  icon: {
    position: 'absolute',
  },
  tile: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
  },
  words: {
    position: 'absolute',
    left: MARGIN,
    right: MARGIN,
    height: NAME_LINE + TEXT_GAP + TAG_LINE * 2,
    // The second line of the tagline comes up from under this edge.
    overflow: 'hidden',
  },
  name: {
    height: NAME_LINE,
    lineHeight: NAME_LINE,
    fontSize: 26,
    fontWeight: '700',
    letterSpacing: -0.4,
    marginBottom: TEXT_GAP,
    includeFontPadding: false,
  },
  tagline: {
    color: TAGLINE,
    fontSize: 20,
    lineHeight: TAG_LINE,
    letterSpacing: -0.2,
    includeFontPadding: false,
  },
  buttonSlot: {
    position: 'absolute',
    left: MARGIN,
    right: MARGIN,
    height: BUTTON_HEIGHT,
  },
  button: {
    flex: 1,
    borderRadius: 14,
    borderCurve: 'continuous',
    backgroundColor: BUTTON,
    alignItems: 'center',
    justifyContent: 'center',
  },
  buttonLabel: {
    color: '#ffffff',
    fontSize: 14,
    lineHeight: 18,
    fontWeight: '600',
    includeFontPadding: false,
  },
});
