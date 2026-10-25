import { Canvas, Picture } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { LinearGradient } from 'expo-linear-gradient';
import { memo, useMemo, useState } from 'react';
import { type LayoutChangeEvent, Platform, StyleSheet, Text, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  cancelAnimation,
  Easing,
  type SharedValue,
  useAnimatedReaction,
  useAnimatedStyle,
  useFrameCallback,
  useReducedMotion,
  useSharedValue,
  withTiming,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

import { recordRoom } from './bookshelfArt';
import { BOOKS, DEMO_BOOK, type ShelfBook } from './bookshelfData';
import {
  autoTau,
  autoWobble,
  BOARD_Y,
  BOOK_X,
  bookPose,
  COVER_W,
  coverRect,
  DESIGN_H,
  DESIGN_W,
  LOOP,
  neighbourLean,
  OPEN,
  PAGE,
  seg,
} from './bookshelfMotion';
import { HomePage, SectionPage } from './BookshelfPages';
import { shade } from './bookshelfColor';

type Props = {
  /** Pulls "Essays" off the shelf, opens it into its page, holds, and puts it back, every LOOP seconds. */
  autoPlay?: boolean;
};

const SERIF = Platform.select({ ios: 'Georgia', default: 'serif' });
const IDLE = 2;
const PERSPECTIVE = 1100;
/** Dragging a spine up this far (pt) slides it all the way out. */
const PULL = 150;
/** The shelf's touch area starts here (design points), below the page. */
const SHELF_HIT_Y = 440;

type Shelf = {
  owned: SharedValue<number>;
  touching: SharedValue<number>;
  idle: SharedValue<number>;
  returning: SharedValue<number>;
  held: SharedValue<number>;
  tau: SharedValue<number>;
  start: SharedValue<number>;
};

/** A finger lands: the demo stops and the shelf holds still where it is. */
function takeOver(m: Shelf) {
  'worklet';
  m.touching.set(1);
  m.idle.set(0);
  if (m.owned.get() === 0 || m.returning.get() === 1) {
    cancelAnimation(m.held);
    m.held.set(m.tau.get());
  }
  m.returning.set(0);
  m.owned.set(1);
}

function letGo(m: Shelf) {
  'worklet';
  m.touching.set(0);
  m.idle.set(0);
}

/** Runs the open time to `to` at the demo's own pace (one second of τ per second). */
function playTo(m: Shelf, to: number, pace: number) {
  'worklet';
  const from = m.held.get();
  m.held.set(withTiming(to, { duration: Math.max(1, (Math.abs(to - from) * 1000) / pace), easing: Easing.linear }));
}

/** Which spine sits under x (design points), or -1. */
function bookAt(x: number) {
  'worklet';
  for (let i = 0; i < BOOKS.length; i += 1) if (x >= BOOK_X[i] - 1 && x <= BOOK_X[i] + BOOKS[i].w + 1) return i;
  return -1;
}

const thud = () => {
  if (process.env.EXPO_OS !== 'web') Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
};

export function BookshelfSpineNav({ autoPlay = false }: Props) {
  const insets = useSafeAreaInsets();
  const reduced = useReducedMotion();
  const auto = autoPlay && !reduced;
  const [size, setSize] = useState<{ w: number; h: number } | null>(null);
  const [shown, setShown] = useState(DEMO_BOOK);
  const [pageOpen, setPageOpen] = useState(false);

  const room = size ? { w: size.w, h: size.h - insets.top - insets.bottom } : null;
  const k = room ? Math.min(room.w / DESIGN_W, room.h / DESIGN_H, 1.15) : 1;
  const ox = room ? (room.w - DESIGN_W * k) / 2 : 0;
  const oy = room ? insets.top + Math.max(0, (room.h - DESIGN_H * k) / 2) : 0;
  const picture = useMemo(() => (size ? recordRoom(size.w, size.h, ox, oy, k) : null), [size, ox, oy, k]);

  const t = useSharedValue(0);
  const owned = useSharedValue(0);
  const touching = useSharedValue(0);
  const idle = useSharedValue(0);
  const returning = useSharedValue(0);
  const held = useSharedValue(0);
  const start = useSharedValue(0);
  const tau = useSharedValue(0);
  const wobble = useSharedValue(0);
  const active = useSharedValue(DEMO_BOOK);
  const m: Shelf = { owned, touching, idle, returning, held, tau, start };

  useFrameCallback((frame) => {
    const dt = Math.min((frame.timeSincePreviousFrame ?? 16) / 1000, 1 / 20);
    if (owned.get() === 0) {
      if (auto) t.set((t.get() + dt) % LOOP);
      const now = auto ? t.get() : 0;
      tau.set(autoTau(now));
      wobble.set(autoWobble(now));
      return;
    }
    tau.set(held.get());
    wobble.set(0);
    if (touching.get() === 1 || returning.get() === 1) return;
    idle.set(idle.get() + dt);
    if (idle.get() < IDLE) return;
    // Put the book back quickly (at most 0.9 s), then let the demo start over.
    returning.set(1);
    const from = held.get();
    held.set(
      withTiming(0, { duration: Math.min(900, Math.max(1, from * 360)), easing: Easing.bezier(0.65, 0, 0.35, 1) }, (done) => {
        if (!done) return;
        returning.set(0);
        owned.set(0);
        t.set(0);
        active.set(DEMO_BOOK);
      }),
    );
  });

  // The page shows the active book's section; it only changes while the book is on the shelf.
  useAnimatedReaction(
    () => active.get(),
    (now, before) => {
      if (now !== before) scheduleOnRN(setShown, now);
    },
  );
  useAnimatedReaction(
    () => tau.get() > 2.2,
    (now, before) => {
      if (now !== before) scheduleOnRN(setPageOpen, now);
    },
  );
  // A light knock when a pulled book leaves the shelf, and when it is home again (finger only).
  useAnimatedReaction(
    () => (tau.get() > 0.45 ? 1 : 0),
    (now, before) => {
      if (before !== null && now !== before && owned.get() === 1) scheduleOnRN(thud);
    },
  );

  const shelfGesture = useMemo(() => {
    const pan = Gesture.Pan()
      .activeOffsetY([-8, 8])
      .onBegin((e) => {
        takeOver(m);
        if (held.get() > 0.01) return;
        const i = bookAt(e.x);
        if (i >= 0) active.set(i);
        start.set(held.get());
      })
      .onUpdate((e) => {
        if (start.get() > 0.01) return;
        // Up to τ 1.6: the drag tips the book and slides it out; past that the release decides.
        const pulled = Math.max(0, -e.translationY / (PULL * k));
        held.set(Math.min(1.6, pulled * 1.6) + Math.max(0, pulled - 1) * 0.1);
      })
      .onEnd((e) => {
        const out = held.get() > 0.7 || e.velocityY < -600;
        playTo(m, out ? OPEN : 0, out ? 1.2 : 2);
      })
      .onFinalize(() => {
        letGo(m);
      });
    const tap = Gesture.Tap()
      .maxDistance(10)
      .onBegin(() => {
        takeOver(m);
      })
      .onEnd((e) => {
        if (held.get() > 0.01) {
          playTo(m, 0, 1.6);
          return;
        }
        const i = bookAt(e.x);
        if (i < 0) return;
        active.set(i);
        playTo(m, OPEN, 1);
      })
      .onFinalize(() => {
        letGo(m);
      });
    return Gesture.Race(pan, tap);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [k]);

  const pageGesture = useMemo(
    () =>
      Gesture.Tap()
        .onBegin(() => {
          takeOver(m);
        })
        .onEnd(() => {
          playTo(m, 0, 1.6);
        })
        .onFinalize(() => {
          letGo(m);
        }),
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [],
  );

  const pageStyle = useAnimatedStyle(() => {
    const p = bookPose(tau.get(), active.get());
    const r = coverRect(active.get());
    const g = p.grow;
    const sx = r.w / PAGE.w + (1 - r.w / PAGE.w) * g;
    const sy = r.h / PAGE.h + (1 - r.h / PAGE.h) * g;
    return {
      // The page waits, hidden, behind the closed cover; it shows from the moment the cover lifts.
      opacity: tau.get() > 1.62 ? 1 : 0,
      transform: [{ translateX: (r.x - PAGE.x) * (1 - g) }, { translateY: (r.y - PAGE.y) * (1 - g) }, { scaleX: sx }, { scaleY: sy }],
    };
  });
  const pageText = useAnimatedStyle(() => ({ opacity: bookPose(tau.get(), active.get()).text }));
  const homeStyle = useAnimatedStyle(() => {
    const away = seg(tau.get(), 0.6, 1.4);
    return { opacity: 1 - 0.55 * away, transform: [{ scale: 1 - 0.03 * away }] };
  });

  const onLayout = (e: LayoutChangeEvent) => {
    const { width, height } = e.nativeEvent.layout;
    if (!size || size.w !== width || size.h !== height) setSize({ w: width, h: height });
  };

  return (
    <View style={styles.root} onLayout={onLayout}>
      {picture ? (
        <Canvas style={StyleSheet.absoluteFill}>
          <Picture picture={picture} />
        </Canvas>
      ) : null}
      {size ? (
        <View style={[styles.design, { left: ox, top: oy, transform: [{ scale: k }] }]}>
          <View style={styles.header}>
            <Text style={styles.kicker}>SHELF</Text>
            <Text style={styles.title}>Your library</Text>
            <Text style={styles.sub}>9 sections · 245 books</Text>
          </View>

          <Animated.View style={[styles.home, homeStyle]} pointerEvents="none">
            <HomePage />
          </Animated.View>

          <GestureDetector gesture={pageGesture}>
            <Animated.View style={[styles.page, pageStyle]} pointerEvents={pageOpen ? 'auto' : 'none'}>
              <SectionPage book={BOOKS[shown]} textStyle={pageText} />
            </Animated.View>
          </GestureDetector>

          <GestureDetector gesture={shelfGesture}>
            <View
              style={styles.shelfHit}
              accessible
              accessibilityRole="tablist"
              accessibilityLabel="Bookshelf. Tap a spine to open its section, or pull it up."
            >
              {BOOKS.map((b, i) => (
                <Book key={b.section} book={b} index={i} lifted={i === shown} tau={tau} active={active} wobble={wobble} />
              ))}
            </View>
          </GestureDetector>

          <Text style={styles.hint} pointerEvents="none">
            Tap or pull a spine to open its section
          </Text>
        </View>
      ) : null}
    </View>
  );
}

type BookProps = {
  book: ShelfBook;
  index: number;
  lifted: boolean;
  tau: SharedValue<number>;
  active: SharedValue<number>;
  wobble: SharedValue<number>;
};

/**
 * One book: its spine and its front cover, hinged at their shared edge. On the shelf only the spine
 * shows; pulled, the book tips out about its bottom, slides up, turns cover-out and opens.
 */
const Book = memo(function Book({ book, index, lifted, tau, active, wobble }: BookProps) {
  const cw = COVER_W[index];
  const body = useAnimatedStyle(() => {
    const a = active.get();
    if (a !== index) {
      const lean = neighbourLean(index, a, bookPose(tau.get(), a).gap, wobble.get());
      // Leaning about the bottom corner on the gap's far side: lift the centre by the corner's drop.
      const rad = (lean * Math.PI) / 180;
      return {
        opacity: 1,
        transform: [
          { perspective: PERSPECTIVE },
          { translateX: (Math.sign(lean) * book.w * (1 - Math.cos(rad))) / 2 },
          { translateY: (-book.w / 2) * Math.abs(Math.sin(rad)) },
          { rotateZ: `${lean}deg` },
          { scale: 1 },
          { rotateX: '0deg' },
        ],
      };
    }
    const p = bookPose(tau.get(), index);
    return {
      opacity: p.alpha,
      transform: [
        { perspective: PERSPECTIVE },
        { translateX: p.dx },
        { translateY: p.dy },
        { rotateZ: '0deg' },
        { scale: p.scale },
        { rotateX: `${p.tilt}deg` },
      ],
    };
  });
  const spine = useAnimatedStyle(() => {
    const turn = active.get() === index ? bookPose(tau.get(), index).turn : 0;
    return { transform: [{ perspective: PERSPECTIVE }, { rotateY: `${-turn}deg` }] };
  });
  const cover = useAnimatedStyle(() => {
    const on = active.get() === index;
    const p = on ? bookPose(tau.get(), index) : null;
    const turn = p ? p.turn : 0;
    // Edge-on (90°) on the shelf, flat (0°) once turned; opening swings it past -90° to the left.
    const angle = 90 - turn - (p ? p.open * 158 : 0);
    return {
      opacity: turn > 0.5 ? 1 : 0,
      transform: [{ perspective: PERSPECTIVE }, { rotateY: `${angle}deg` }],
    };
  });
  // Seen past its edge, the cover shows its inside: a marbled endpaper.
  const inside = useAnimatedStyle(() => {
    const p = active.get() === index ? bookPose(tau.get(), index) : null;
    return { opacity: p && p.open > 0.57 ? 1 : 0 };
  });
  return (
    <Animated.View
      style={[
        styles.book,
        { zIndex: lifted ? 5 : 1, left: BOOK_X[index], top: BOARD_Y - book.h - SHELF_HIT_Y, width: book.w + cw, height: book.h, transformOrigin: [book.w / 2, book.h, 0] },
        body,
      ]}
    >
      <Animated.View style={[styles.spine, { width: book.w, height: book.h, transformOrigin: [book.w, book.h / 2, 0] }, spine]}>
        <Spine book={book} />
      </Animated.View>
      <Animated.View style={[styles.cover, { left: book.w, width: cw, height: book.h, transformOrigin: [0, book.h / 2, 0] }, cover]}>
        <Cover book={book} width={cw} />
        <Animated.View style={[StyleSheet.absoluteFill, inside]}>
          <LinearGradient colors={[shade(book.color, 0.5), shade(book.color, 0.25), shade(book.color, 0.42)]} locations={[0, 0.5, 1]} style={styles.endpaper} />
        </Animated.View>
      </Animated.View>
    </Animated.View>
  );
});

/** The spine: rounded binding, caps, bands or a label, and the foil title running top to bottom. */
function Spine({ book }: { book: ShelfBook }) {
  const { w, h, color, foil, binding } = book;
  return (
    <View style={[styles.spineFace, { width: w, height: h }]}>
      <LinearGradient
        colors={[shade(color, -0.5), shade(color, 0.14), color, shade(color, -0.42)]}
        locations={[0, 0.32, 0.62, 1]}
        start={{ x: 0, y: 0.5 }}
        end={{ x: 1, y: 0.5 }}
        style={StyleSheet.absoluteFill}
      />
      <View style={[styles.cap, { top: 0, backgroundColor: shade(color, -0.35) }]} />
      <View style={[styles.cap, { bottom: 0, backgroundColor: shade(color, -0.45) }]} />
      {binding === 'leather'
        ? [20, 30, h - 36, h - 26].map((y) => (
            <View key={y} style={[styles.band, { top: y }]}>
              <LinearGradient colors={[shade(color, 0.35), color, shade(color, -0.55)]} style={StyleSheet.absoluteFill} />
            </View>
          ))
        : null}
      {binding !== 'linen'
        ? [14, h - 16].map((y) => <View key={y} style={[styles.foilRule, { top: y, backgroundColor: foil }]} />)
        : null}
      {binding === 'linen' ? <View style={[styles.labelPatch, { top: h / 2 - (h - 70) / 2 - 4, height: h - 62 }]} /> : null}
      <View style={[styles.spineTitleBox, { width: h - 76, left: w / 2 - (h - 76) / 2, top: h / 2 - 10 }]}>
        <Text style={[styles.spineTitle, { color: foil }]} numberOfLines={1}>
          {book.section.toUpperCase()}
        </Text>
      </View>
      <View style={styles.spineSheen} />
    </View>
  );
}

/** The front cover: the same binding, a foil frame and the section's name. */
function Cover({ book, width }: { book: ShelfBook; width: number }) {
  return (
    <View style={[styles.coverFace, { width, height: book.h }]}>
      <LinearGradient
        colors={[shade(book.color, 0.16), book.color, shade(book.color, -0.38)]}
        locations={[0, 0.5, 1]}
        start={{ x: 0, y: 0 }}
        end={{ x: 1, y: 1 }}
        style={StyleSheet.absoluteFill}
      />
      <View style={[styles.coverHinge, { backgroundColor: shade(book.color, -0.4) }]} />
      <View style={[styles.coverFrame, { borderColor: book.foil }]} />
      <View style={[styles.coverFrameInner, { borderColor: book.foil }]} />
      <Text style={[styles.coverTitle, { color: book.foil }]}>{book.section.toUpperCase()}</Text>
      <View style={[styles.coverOrnament, { backgroundColor: book.foil }]} />
      <Text style={[styles.coverSub, { color: book.foil }]}>SHELF EDITIONS</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: '#17120F', overflow: 'hidden' },
  design: { position: 'absolute', width: DESIGN_W, height: DESIGN_H, transformOrigin: 'top left' },
  header: { position: 'absolute', left: 24, right: 24, top: 8, gap: 2 },
  kicker: { color: '#E0B36C', fontSize: 12, fontWeight: '700', letterSpacing: 2.4 },
  title: { color: '#F4EADB', fontSize: 32, fontWeight: '800', letterSpacing: -0.6 },
  sub: { color: 'rgba(244,234,219,0.6)', fontSize: 14, fontWeight: '500', fontVariant: ['tabular-nums'] },
  home: { position: 'absolute', left: PAGE.x, top: PAGE.y, width: PAGE.w, height: PAGE.h },
  page: { position: 'absolute', left: PAGE.x, top: PAGE.y, width: PAGE.w, height: PAGE.h, transformOrigin: 'top left' },
  shelfHit: { position: 'absolute', left: 0, top: SHELF_HIT_Y, width: DESIGN_W, height: BOARD_Y + 30 - SHELF_HIT_Y },
  book: { position: 'absolute' },
  spine: { position: 'absolute', left: 0, top: 0 },
  spineFace: {
    borderRadius: 3,
    overflow: 'hidden',
    boxShadow: '0 1px 2px rgba(0,0,0,0.55), 4px 0 12px rgba(0,0,0,0.3), inset 1px 0 0 rgba(255,255,255,0.14), inset -1px 0 0 rgba(0,0,0,0.4)',
  },
  cap: { position: 'absolute', left: 0, right: 0, height: 5 },
  band: { position: 'absolute', left: 0, right: 0, height: 5, overflow: 'hidden' },
  foilRule: { position: 'absolute', left: 4, right: 4, height: 1.5, opacity: 0.85 },
  labelPatch: { position: 'absolute', left: 5, right: 5, backgroundColor: 'rgba(244,236,214,0.92)', borderRadius: 2, boxShadow: 'inset 0 0 0 1px rgba(60,40,20,0.35)' },
  spineTitleBox: { position: 'absolute', height: 20, alignItems: 'center', justifyContent: 'center', transform: [{ rotate: '90deg' }] },
  spineTitle: {
    fontFamily: SERIF,
    fontSize: 12,
    fontWeight: '700',
    letterSpacing: 2.4,
    textShadowColor: 'rgba(0,0,0,0.55)',
    textShadowOffset: { width: 0, height: 1 },
    textShadowRadius: 0.5,
  },
  spineSheen: { position: 'absolute', top: 0, bottom: 0, left: '28%', width: 3, backgroundColor: 'rgba(255,255,255,0.08)' },
  cover: { position: 'absolute', top: 0 },
  coverFace: { borderTopRightRadius: 4, borderBottomRightRadius: 4, overflow: 'hidden', boxShadow: '0 2px 4px rgba(0,0,0,0.45), 0 18px 40px rgba(0,0,0,0.35)' },
  coverHinge: { position: 'absolute', left: 0, top: 0, bottom: 0, width: 6, opacity: 0.8 },
  coverFrame: { position: 'absolute', left: 12, right: 8, top: 10, bottom: 10, borderWidth: 1.2, borderRadius: 2, opacity: 0.85 },
  coverFrameInner: { position: 'absolute', left: 16, right: 12, top: 14, bottom: 14, borderWidth: 0.6, borderRadius: 1, opacity: 0.6 },
  coverTitle: { position: 'absolute', left: 14, right: 10, top: '38%', textAlign: 'center', fontFamily: SERIF, fontSize: 13, fontWeight: '700', letterSpacing: 2.2 },
  coverOrnament: { position: 'absolute', left: '42%', right: '42%', top: '52%', height: 1.5, opacity: 0.8 },
  coverSub: { position: 'absolute', left: 14, right: 10, bottom: 22, textAlign: 'center', fontSize: 6, fontWeight: '700', letterSpacing: 1.4, opacity: 0.75 },
  endpaper: { position: 'absolute', left: 0, right: 0, top: 0, bottom: 0, borderTopLeftRadius: 4, borderBottomLeftRadius: 4 },
  hint: { position: 'absolute', left: 24, right: 24, top: 740, textAlign: 'center', color: 'rgba(244,234,219,0.5)', fontSize: 12, fontWeight: '500' },
});
