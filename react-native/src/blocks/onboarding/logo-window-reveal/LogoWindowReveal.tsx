import { Canvas, Circle, Group, Image as SkiaImage, LinearGradient as SkiaGradient, Path, Rect, rect, rrect, useImage, vec } from '@shopify/react-native-skia';
import { Image } from 'expo-image';
import { LinearGradient } from 'expo-linear-gradient';
import { StatusBar } from 'expo-status-bar';
import { useEffect, useState } from 'react';
import { type LayoutChangeEvent, Platform, StyleSheet, Text, View, useWindowDimensions } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, { type SharedValue, useAnimatedStyle, useDerivedValue, useFrameCallback, useReducedMotion, useSharedValue } from 'react-native-reanimated';
import { scheduleOnRN } from 'react-native-worklets';

import {
  CARD,
  CARD_RADIUS,
  CLOSE_AT,
  END,
  HALO,
  HANG_AT,
  HERO,
  HERO_ASPECT,
  LIST_AT,
  LOOP,
  MARK,
  ROWS,
  SCROLL_HOLD,
  SHRINK_AT,
  SWELL_AT,
  USER_HOLD,
  hero,
  heroLine,
  letter,
  mark,
  markPath,
  nameOut,
  place,
  stillEnd,
  tagline as taglineAt,
  windowOf,
} from './timeline';

export type CountdownEvent = {
  title: string;
  /** How long until it, written out. */
  when: string;
  /** A photo's address; the card and the screen crop it. */
  photo: string;
};

type Props = {
  /** Eight events, top to bottom. The mark opens on the second; the last hangs in the middle at the end. */
  events?: CountdownEvent[];
  /** The app's name, spelled out letter by letter at the end. */
  name?: string;
  tagline?: string;
  /** Runs the show by itself every LOOP seconds. */
  autoPlay?: boolean;
  /** Called when the show has reached its end, with the name on the screen. */
  onDone?: () => void;
};

function photo(id: string, width = 660, height = 660) {
  return `https://images.unsplash.com/photo-${id}?auto=format&fit=crop&w=${width}&h=${height}&q=80`;
}

const EVENTS: CountdownEvent[] = [
  { title: 'Tokyo Trip', when: 'IN 3 WEEKS, 2 DAYS', photo: photo('1540959733332-eab4deabeeaf') },
  { title: "Kate's Birthday", when: 'IN 6 DAYS', photo: photo('1544005313-94ddf0286df2', 720, 720 / HERO_ASPECT) },
  { title: 'Paris', when: 'IN 78 DAYS', photo: photo('1502602898657-3e91760cbb34') },
  { title: 'New York', when: 'IN 85 DAYS', photo: photo('1496442226666-8d4d0e62e6e9') },
  { title: 'Maldives', when: 'IN 51 DAYS', photo: photo('1573843981267-be1999ff37cd') },
  { title: 'Santorini', when: 'IN 129 DAYS', photo: photo('1570077188670-e3a8d69ac5ff') },
  { title: 'Summit Hike', when: 'IN 1 YEAR', photo: photo('1464822759023-fed622ff2c3b') },
  { title: "Bruno's Birthday", when: 'IN 14 DAYS, 3 HOURS', photo: photo('1506794778202-cad84cf45f1d') },
];
// The events of the side columns' rows, from the second row down.
const LEFT = [3, 5, 0, 6, 2, 1, 4];
const RIGHT = [6, 4, 7, 2, 5, 0, 3];

const BACKGROUND = '#fffdff';
const INK = '#010103';
const HALO_INK = '#808083';
const PLACEHOLDER = '#26262b';
const NAME_INK = '#1b1b1b';
const TAGLINE_INK = '#3b4248';
const SHADE = ['rgba(0,0,0,0)', 'rgba(0,0,0,0.7)'] as const;
// A heavy condensed face that both systems carry.
const CONDENSED = Platform.select({ ios: 'HelveticaNeue-CondensedBlack', default: 'sans-serif-condensed' });
// Where the hero's lines, the name and the tagline start, as shares of the screen's height.
const HERO_TEXT_Y = 0.645;
const NAME_Y = 0.505;
const TAGLINE_Y = 0.607;
const TAGLINE_WIDTH = 290;
// The status bar's ink follows what is under it, as the system does by itself for the SwiftUI
// version: white over the mark's disc and over the cards, dark over the page and the photo's sky.
// Where its clock is, how long after the photo starts to shrink the first card is under it, and
// how long after the close starts the hanging cards have left it.
const BAR = { x: 50, y: 22 };
const BAR_IN = 0.1;
const BAR_CLEAR = 0.22;

/** Whether something dark lies under the status bar at `t`. */
function darkUnder(t: number, width: number, height: number) {
  'worklet';
  if (t >= SHRINK_AT) return t >= SHRINK_AT + BAR_IN && t < CLOSE_AT + BAR_CLEAR;
  const { scale } = mark(t, width, height);
  return (MARK / 2) * scale > Math.hypot(width / 2 - BAR.x, height / 2 - BAR.y) && windowOf(scale, width, height).h / 2 < height / 2 - BAR.y;
}

type CardProps = {
  event: CountdownEvent;
  column: number;
  row: number;
  /** The show's time, how far a finger scrolled the list, and how much of the cards shows. */
  time: SharedValue<number>;
  extra: SharedValue<number>;
  shown: SharedValue<number>;
  width: number;
  height: number;
};

/** A square card: the event's photo under a shade, with its two lines at the bottom. */
function Card({ event, column, row, time, extra, shown, width, height }: CardProps) {
  const style = useAnimatedStyle(() => {
    const p = place(column, row, time.get(), width, height, extra.get());
    return {
      opacity: p.opacity * shown.get(),
      transform: [{ translateX: p.x - CARD / 2 }, { translateY: p.y - CARD / 2 }, { scale: p.scale }],
    };
  });
  const lines = useAnimatedStyle(() => ({ opacity: place(column, row, time.get(), width, height, extra.get()).text }));
  return (
    <Animated.View style={[styles.card, style]}>
      <Image source={event.photo} style={styles.fill} contentFit="cover" />
      <LinearGradient colors={SHADE} style={styles.shade} />
      <Animated.View style={[styles.cardLines, lines]}>
        <Text style={styles.cardWhen} numberOfLines={1}>
          {event.when}
        </Text>
        <Text style={styles.cardTitle} numberOfLines={1}>
          {event.title}
        </Text>
      </Animated.View>
    </Animated.View>
  );
}

function Letter({ index, char, time }: { index: number; char: string; time: SharedValue<number> }) {
  const style = useAnimatedStyle(() => {
    const l = letter(time.get(), index);
    return { opacity: l.opacity, transform: [{ translateY: l.rise }] };
  });
  return <Animated.Text style={[styles.letter, style]}>{char}</Animated.Text>;
}

/**
 * An app's intro. Its mark, a disc with a window in it, swells and flies at the viewer until the
 * window has swallowed the screen: what was seen through it is a photo, which then shrinks into
 * one card of a list. The list scrolls, zooms out into three columns, and runs up until its last
 * row hangs over the app's name.
 */
export function LogoWindowReveal({
  events = EVENTS,
  name = 'UNTIL',
  tagline = 'Countdown to all of your special moments',
  autoPlay = false,
  onDone,
}: Props) {
  const still = useReducedMotion();
  const window = useWindowDimensions();
  // The screen as laid out; the window's size stands in until then.
  const [laidOut, setLaidOut] = useState<{ width: number; height: number } | null>(null);
  const { width, height } = laidOut ?? window;
  const at = (index: number) => events[index % events.length];
  const picture = useImage(at(HERO).photo);
  const [headline, ...rest] = at(HERO).title.split(' ');

  // Seconds since the first frame, and the stretch of the show being played against them:
  // from `from` at `t0` until `to`, where it stops.
  const clock = useSharedValue(0);
  const firstFrame = useSharedValue(-1);
  const run = useSharedValue({ t0: 0, from: 0, to: autoPlay ? LOOP : 0 });
  // When autoPlay may go on after a touch; infinite while no touch is waited out.
  const resume = useSharedValue(Infinity);
  const extra = useSharedValue(0);
  const grabbed = useSharedValue(0);
  // The status bar's ink, which React Native has to be told.
  const [bar, setBar] = useState<'dark' | 'light'>('dark');
  const lit = useSharedValue(false);

  const position = useDerivedValue(() => {
    const r = run.get();
    return Math.min(r.to, r.from + Math.max(0, clock.get() - r.t0));
  });
  // Reduced motion shows the mark and the end only, and fades between the two.
  const time = useDerivedValue(() => (still ? END : position.get()));
  const shown = useDerivedValue(() => (still ? stillEnd(position.get()) : 1));
  const markTime = useDerivedValue(() => (still ? 0 : position.get()));

  useEffect(() => {
    run.set({ t0: clock.get(), from: 0, to: autoPlay ? LOOP : 0 });
    resume.set(Infinity);
    extra.set(0);
  }, [autoPlay, clock, run, resume, extra]);

  const arrived = () => onDone?.();

  useFrameCallback((frame) => {
    if (firstFrame.get() < 0) firstFrame.set(frame.timestamp);
    const now = (frame.timestamp - firstFrame.get()) / 1000;
    const before = clock.get();
    clock.set(now);
    const r = run.get();
    const end = r.t0 + r.to - r.from;
    const t = Math.min(r.to, r.from + Math.max(0, now - r.t0));
    const light = still ? stillEnd(t) > 0.5 : darkUnder(t, width, height);
    if (light !== lit.get()) {
      lit.set(light);
      scheduleOnRN(setBar, light ? 'light' : 'dark');
    }
    if (r.to === END && before < end && now >= end) scheduleOnRN(arrived);
    if (r.to === LOOP && now >= end) {
      // Closed: the mark again. The loop goes on from when it was due, so that every one is LOOP long.
      extra.set(0);
      run.set({ t0: end, from: 0, to: autoPlay && resume.get() === Infinity ? LOOP : 0 });
    } else if (autoPlay && now >= resume.get() && now >= end) {
      // The touches are over: close the show if it is open, and start again.
      resume.set(Infinity);
      run.set(r.to === END ? { t0: now, from: CLOSE_AT, to: LOOP } : { t0: now, from: 0, to: LOOP });
    }
  });

  /** A touch takes over: the show stops at its end, and autoPlay starts again a while later. */
  const touched = () => {
    'worklet';
    const r = run.get();
    const t = position.get();
    if (autoPlay) resume.set(clock.get() + USER_HOLD);
    if (r.to === LOOP && t < END) run.set({ ...r, to: END });
  };

  const tap = Gesture.Tap().onEnd((_event, success) => {
    if (!success) return;
    const t = position.get();
    const idle = run.get().to === 0;
    touched();
    // The mark starts the show; the end closes it.
    if (idle) run.set({ t0: clock.get(), from: SWELL_AT, to: END });
    else if (t >= END && t < CLOSE_AT) run.set({ t0: clock.get(), from: CLOSE_AT, to: LOOP });
  });

  // A finger on the list scrolls it, and holds the show where it is.
  const pan = Gesture.Pan()
    .activeOffsetY([-8, 8])
    .onStart(() => {
      touched();
      const t = position.get();
      grabbed.set(t >= LIST_AT && t < HANG_AT ? extra.get() : NaN);
      if (!Number.isNaN(grabbed.get())) run.set({ t0: Infinity, from: t, to: run.get().to });
    })
    .onUpdate((event) => {
      if (!Number.isNaN(grabbed.get())) extra.set(grabbed.get() - event.translationY);
    })
    .onFinalize(() => {
      const r = run.get();
      if (r.t0 === Infinity) run.set({ ...r, t0: clock.get() + SCROLL_HOLD });
    });

  const disc = useDerivedValue(() => markPath(mark(markTime.get(), width, height).scale, width, height));
  const discOpacity = useDerivedValue(() => mark(markTime.get(), width, height).opacity * (still ? 1 - shown.get() : 1));
  const haloRadius = useDerivedValue(() => (MARK / 2) * HALO * mark(markTime.get(), width, height).scale);
  const haloOpacity = useDerivedValue(() => mark(markTime.get(), width, height).halo);
  const frame = useDerivedValue(() => {
    const h = hero(markTime.get(), width, height);
    return rrect(rect(h.cx - h.w / 2, h.cy - h.h / 2, h.w, h.h), h.r, h.r);
  });
  // The photo covers the screen with its whole width, and shrinks with the box when that becomes a card.
  const photoBox = useDerivedValue(() => {
    const k = hero(markTime.get(), width, height).k;
    const w = Math.max(width, height * HERO_ASPECT) * k;
    return rect(width / 2 - w / 2, height / 2 - w / HERO_ASPECT / 2, w, w / HERO_ASPECT);
  });
  const photoOpacity = useDerivedValue(() => hero(markTime.get(), width, height).opacity);
  const shadeBox = useDerivedValue(() => {
    const h = hero(markTime.get(), width, height);
    return rect(h.cx - h.w / 2, h.cy, h.w, h.h / 2);
  });
  const shadeFrom = useDerivedValue(() => vec(0, hero(markTime.get(), width, height).cy));
  const shadeTo = useDerivedValue(() => {
    const h = hero(markTime.get(), width, height);
    return vec(0, h.cy + h.h / 2);
  });

  const whenStyle = useAnimatedStyle(() => {
    const l = heroLine(markTime.get(), 0);
    return { opacity: l.opacity, transform: [{ translateY: l.rise }] };
  });
  const titleStyle = useAnimatedStyle(() => {
    const l = heroLine(markTime.get(), 1);
    return { opacity: l.opacity, transform: [{ translateY: l.rise }] };
  });
  const nameStyle = useAnimatedStyle(() => {
    const n = nameOut(time.get());
    return { opacity: n.opacity * shown.get(), transform: [{ scale: n.scale }] };
  });
  const taglineStyle = useAnimatedStyle(() => ({ opacity: taglineAt(time.get()) * shown.get() }));

  const onLayout = (event: LayoutChangeEvent) => {
    const { width: w, height: h } = event.nativeEvent.layout;
    setLaidOut({ width: w, height: h });
  };

  return (
    <GestureDetector gesture={Gesture.Race(pan, tap)}>
      <View style={styles.screen} onLayout={onLayout} accessible accessibilityRole="button" accessibilityLabel={`${name}. ${tagline}`}>
        <StatusBar style={bar} />

        {Array.from({ length: ROWS }, (_, row) => (
          <Card key={row} event={at(row)} column={0} row={row} time={time} extra={extra} shown={shown} width={width} height={height} />
        ))}
        {LEFT.map((index, i) => (
          <Card key={`l${i}`} event={at(index)} column={-1} row={i + 1} time={time} extra={extra} shown={shown} width={width} height={height} />
        ))}
        {RIGHT.map((index, i) => (
          <Card key={`r${i}`} event={at(index)} column={1} row={i + 1} time={time} extra={extra} shown={shown} width={width} height={height} />
        ))}

        <Animated.View style={[styles.name, { top: height * NAME_Y }, nameStyle]}>
          {name.split('').map((char, index) => (
            <Letter key={index} index={index} char={char} time={time} />
          ))}
        </Animated.View>
        <Animated.Text
          lineBreakStrategyIOS="standard"
          style={[styles.tagline, { top: height * TAGLINE_Y, left: (width - TAGLINE_WIDTH) / 2 }, taglineStyle]}
        >
          {tagline}
        </Animated.Text>

        {/* The mark, and the photo seen through its window: drawn, so that no size is too large for it. */}
        <Canvas style={styles.fill} pointerEvents="none">
          <Circle cx={width / 2} cy={height / 2} r={haloRadius} color={HALO_INK} opacity={haloOpacity} />
          <Group clip={frame} opacity={photoOpacity}>
            <Rect x={0} y={0} width={width} height={height} color={PLACEHOLDER} />
            {picture && <SkiaImage image={picture} rect={photoBox} fit="cover" />}
            <Rect rect={shadeBox}>
              <SkiaGradient start={shadeFrom} end={shadeTo} colors={[...SHADE]} />
            </Rect>
          </Group>
          <Path path={disc} fillType="evenOdd" color={INK} opacity={discOpacity} />
        </Canvas>

        <View pointerEvents="none" style={[styles.heroLines, { top: height * HERO_TEXT_Y }]}>
          <Animated.Text style={[styles.heroWhen, whenStyle]} numberOfLines={1}>
            {at(HERO).when}
          </Animated.Text>
          <Animated.Text style={[styles.heroTitle, titleStyle]}>{rest.length > 0 ? `${headline}\n${rest.join(' ')}` : headline}</Animated.Text>
        </View>
      </View>
    </GestureDetector>
  );
}

const styles = StyleSheet.create({
  screen: {
    flex: 1,
    backgroundColor: BACKGROUND,
    overflow: 'hidden',
  },
  fill: {
    position: 'absolute',
    left: 0,
    right: 0,
    top: 0,
    bottom: 0,
  },
  card: {
    position: 'absolute',
    left: 0,
    top: 0,
    width: CARD,
    height: CARD,
    borderRadius: CARD_RADIUS,
    backgroundColor: PLACEHOLDER,
    overflow: 'hidden',
  },
  shade: {
    position: 'absolute',
    left: 0,
    right: 0,
    bottom: 0,
    height: CARD / 2,
  },
  cardLines: {
    position: 'absolute',
    left: 16,
    right: 16,
    bottom: 30,
    alignItems: 'center',
  },
  cardWhen: {
    color: 'rgba(255,255,255,0.85)',
    fontSize: 10,
    lineHeight: 14,
    fontWeight: '500',
    letterSpacing: 1,
    includeFontPadding: false,
  },
  cardTitle: {
    marginTop: 6,
    color: '#ffffff',
    fontFamily: CONDENSED,
    fontSize: 32,
    lineHeight: 36,
    fontWeight: Platform.select({ ios: 'normal', default: 'bold' }),
    includeFontPadding: false,
  },
  heroLines: {
    position: 'absolute',
    left: 0,
    right: 0,
    alignItems: 'center',
  },
  heroWhen: {
    color: 'rgba(255,255,255,0.85)',
    fontSize: 12,
    lineHeight: 16,
    fontWeight: '500',
    letterSpacing: 1.2,
    includeFontPadding: false,
  },
  heroTitle: {
    marginTop: 22,
    color: '#ffffff',
    fontFamily: CONDENSED,
    fontSize: 52,
    lineHeight: 56,
    fontWeight: Platform.select({ ios: 'normal', default: 'bold' }),
    textAlign: 'center',
    includeFontPadding: false,
  },
  name: {
    position: 'absolute',
    left: 0,
    right: 0,
    height: 56,
    flexDirection: 'row',
    justifyContent: 'center',
    gap: 9,
  },
  letter: {
    color: NAME_INK,
    fontSize: 46,
    lineHeight: 56,
    fontWeight: '800',
    includeFontPadding: false,
  },
  tagline: {
    position: 'absolute',
    width: TAGLINE_WIDTH,
    color: TAGLINE_INK,
    fontSize: 17,
    lineHeight: 25,
    textAlign: 'center',
    includeFontPadding: false,
  },
});
