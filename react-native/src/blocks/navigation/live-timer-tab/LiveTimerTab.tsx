import { Canvas, Path, Skia } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { LinearGradient } from 'expo-linear-gradient';
import { StatusBar } from 'expo-status-bar';
import { useEffect, useRef, useState } from 'react';
import { type LayoutChangeEvent, Pressable, StyleSheet, Text, View, useWindowDimensions } from 'react-native';
import Animated, {
  Easing,
  FadeOut,
  ReduceMotion,
  cancelAnimation,
  interpolateColor,
  scrollTo,
  useAnimatedReaction,
  useAnimatedRef,
  useAnimatedScrollHandler,
  useAnimatedStyle,
  useReducedMotion,
  useSharedValue,
  withTiming,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';

import { RollingTime } from './RollingTime';
import {
  AUTO_SCROLL,
  BACK_AT,
  BODY_GAP,
  BODY_LINE,
  BOX_WIDTH,
  COLLAPSE,
  CONTROL,
  CONTROL_CENTRE,
  CONTROL_GAP,
  CONTROL_RADIUS,
  DIAL,
  DIAL_DROP,
  EASE_OUT,
  FADE,
  FORM,
  GHOST_IN,
  GHOST_SCALE,
  GLIDE,
  HAND,
  HERO,
  HYSTERESIS,
  LOOP,
  MARGIN,
  PILL_HEIGHT,
  PILL_WIDTH,
  SCROLL_AT,
  SCROLL_BACK,
  SCROLL_HOME,
  SCROLL_UP,
  START,
  START_AT,
  STOP,
  STOP_AT,
  SUBTITLE_GAP,
  SUBTITLE_LINE,
  SWELL,
  TAB_BAR,
  TAB_BOTTOM,
  TAB_ICON,
  TAB_INSET,
  TAB_STROKE,
  TICK,
  TICK_GAP,
  TIMER,
  TIME_OUT,
  TITLE_GAP,
  TITLE_LINE,
  USER_HOLD,
  clamp01,
  clock,
  mix,
} from './motion';

type Props = {
  title?: string;
  subtitle?: string;
  /** The text of the page, one string for each paragraph. */
  paragraphs?: string[];
  listTitle?: string;
  list?: string[];
  /** The title of the page behind the third tab. */
  profileTitle?: string;
  startLabel?: string;
  /** What the timer counts down from, in seconds. */
  duration?: number;
  /** Starts the timer, scrolls the page away and back and stops the timer by itself, every LOOP seconds. */
  autoPlay?: boolean;
  onStart?: () => void;
  /** Called with the seconds that were left when the timer is stopped by hand. */
  onStop?: (remaining: number) => void;
  /** Called when the time is up. */
  onDone?: () => void;
  /** Called with 0 for the recipe or 1 for the profile. */
  onTabChange?: (index: number) => void;
};

const PARAGRAPHS = [
  'Few things fill a kitchen the way warm bread and melting cheese do. The smell drifts out of the oven long before the timer ends, and everyone nearby finds a reason to come and look.',
  'Here a whole wheel of brie bakes on a ring of baguette slices rubbed with garlic. A little rosemary and a thread of olive oil are all it needs: the cheese turns soft and glossy while the bread crisps at its edges.',
  'It suits a quiet evening for two as well as a table full of friends. Bring it out in its dish while it still bubbles, and let people tear and dip as they talk.',
];
const LIST = ['1 wheel of brie, about 250 g', '1 baguette, sliced', '3 cloves of garlic', '2 sprigs of rosemary', 'Olive oil and flaky salt'];

const PAGE = '#FCFCFC';
const BAR = '#FFFFFF';
const INK = '#111111';
const MUTE = '#8A8A8A';
const BODY = '#3A3A3A';
const INACTIVE = '#9A9A9A';
const BUTTON_FILL = '#161616';
const BOX_FILL = '#E6E6E6';
const HERO_FILL = '#EFEFEF';
const ROW_FILL = '#EDEDED';
const TAB_FADE = 250;
// The cover behind the status bar fades out over this height under it, and comes in over this much scrolling.
const COVER_FADE = 16;
const COVER_OVER = 40;
const CONTROL_SIZE = 19;
const PILL_SIZE = 17;
// What a line of the body adds to the type's own line. A paragraph gives up the half of it above
// its first line and the half below its last, so that the gaps are measured from the text.
const BODY_LEADING = 7.7;

// The picture at the top of the page, seen from above: a board, a pan with a wheel of brie in a ring
// of bread, and two bowls. Each circle is its middle from the picture's own, its radius and its grey.
const HERO_ART: [number, number, number, string][] = [
  [0, 132, 84, '#DCDCDC'],
  [0, 132, 66, '#1C1C1C'],
  [0, 132, 58, '#2B2B2B'],
  [49, 132, 11, '#CFCFCF'],
  [24.5, 174.4, 11, '#CFCFCF'],
  [-24.5, 174.4, 11, '#CFCFCF'],
  [-49, 132, 11, '#CFCFCF'],
  [-24.5, 89.6, 11, '#CFCFCF'],
  [24.5, 89.6, 11, '#CFCFCF'],
  [49, 132, 7.5, '#E6E6E6'],
  [24.5, 174.4, 7.5, '#E6E6E6'],
  [-24.5, 174.4, 7.5, '#E6E6E6'],
  [-49, 132, 7.5, '#E6E6E6'],
  [-24.5, 89.6, 7.5, '#E6E6E6'],
  [24.5, 89.6, 7.5, '#E6E6E6'],
  [0, 132, 34, '#F4F4F4'],
  [0, 132, 28, '#FFFFFF'],
  [-9, 125, 2.5, '#8A8A8A'],
  [8, 138, 2.5, '#8A8A8A'],
  [3, 120, 2, '#B4B4B4'],
  [-6, 141, 2, '#B4B4B4'],
  [-136, 66, 28, '#DCDCDC'],
  [-136, 66, 21, '#F7F7F7'],
  [134, 196, 32, '#DCDCDC'],
  [134, 196, 24, '#C9C9C9'],
];

// The outer tabs' icons in a 26-point box.
const HAT = Skia.PathBuilder.Make()
  .moveTo(7.5, 17.2)
  .cubicTo(3.2, 16.6, 2.6, 10.2, 7.4, 9.6)
  .cubicTo(8, 5.6, 18, 5.6, 18.6, 9.6)
  .cubicTo(23.4, 10.2, 22.8, 16.6, 18.5, 17.2)
  .lineTo(18.5, 21)
  .quadTo(18.5, 22.5, 17, 22.5)
  .lineTo(9, 22.5)
  .quadTo(7.5, 22.5, 7.5, 21)
  .close()
  .moveTo(7.5, 19)
  .lineTo(18.5, 19)
  .build();
const PERSON = Skia.PathBuilder.Make()
  .addCircle(13, 8.6, 4.2)
  .moveTo(4.8, 22.5)
  .cubicTo(4.8, 17.5, 8.5, 15.6, 13, 15.6)
  .cubicTo(17.5, 15.6, 21.2, 17.5, 21.2, 22.5)
  .build();

function tap() {
  if (process.env.EXPO_OS !== 'web') Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
}

function select() {
  if (process.env.EXPO_OS !== 'web') Haptics.selectionAsync().catch(() => {});
}

function Icon({ path, color }: { path: typeof HAT; color: string }) {
  return (
    <Canvas style={styles.icon}>
      <Path path={path} color={color} style="stroke" strokeWidth={TAB_STROKE} strokeCap="round" strokeJoin="round" />
    </Canvas>
  );
}

/**
 * A recipe page with a timer and a three-tab bar. "Start oven" turns into a box that counts down;
 * once that box has scrolled off the screen, the stopwatch in the tab bar swells into a pill that
 * carries the time, and when the box comes back the pill collapses and hands the time back to it.
 */
export function LiveTimerTab({
  title = 'Baked Brie & Garlic Bread',
  subtitle = '25 min · Serves 4',
  paragraphs = PARAGRAPHS,
  listTitle = 'Ingredients',
  list = LIST,
  profileTitle = 'Profile',
  startLabel = 'Start oven',
  duration = TIMER,
  autoPlay = false,
  onStart,
  onStop,
  onDone,
  onTabChange,
}: Props) {
  const reducedMotion = useReducedMotion();
  const insets = useSafeAreaInsets();
  const window = useWindowDimensions();
  // The screen as laid out; the window's size stands in until then.
  const [laidOut, setLaidOut] = useState<{ width: number; height: number } | null>(null);
  const size = laidOut ?? window;
  // When the timer was started, in milliseconds; null while it is idle.
  const [startedAt, setStartedAt] = useState<number | null>(null);
  // Whole seconds counted since then.
  const [counted, setCounted] = useState(0);
  const [tab, setTab] = useState(0);
  // The timer's control has scrolled out of what shows of the page.
  const [away, setAway] = useState(false);
  // Changes on every touch, which restarts the autoPlay loop behind a pause.
  const [touches, setTouches] = useState(0);

  const running = startedAt !== null;
  // The time lives in the tab bar while its box is out of sight.
  const live = running && (away || tab !== 0);
  const time = clock(duration - 1 - counted);

  const page = useAnimatedRef<Animated.ScrollView>();
  const scrollY = useSharedValue(0);
  // Where the loop holds the page, while `driving` is on.
  const glideY = useSharedValue(0);
  const driving = useSharedValue(false);
  const gone = useSharedValue(false);
  // 0 the black button, 1 the light box.
  const run = useSharedValue(0);
  // 0 the stopwatch, 1 the pill.
  const pill = useSharedValue(0);
  // The pill is handing the time back to the box, and how far under the box it is.
  const ghosting = useSharedValue(false);
  const ghostDrop = useSharedValue(0);
  // 0 the recipe, 1 the profile.
  const turn = useSharedValue(0);

  const bottom = Math.max(insets.bottom, TAB_BOTTOM);
  const barHeight = TAB_BAR + bottom;
  const controlWidth = size.width - MARGIN * 2;
  // What shows of the page: from under the status bar to where the lines start to fade.
  const shownTop = insets.top;
  const shownBottom = size.height - barHeight;
  const pillY = size.height - barHeight + TAB_BAR / 2;

  const start = (at = Date.now()) => {
    if (startedAt !== null) return;
    setCounted(0);
    setStartedAt(at);
    run.set(withTiming(1, { duration: START * 1000, easing: Easing.bezier(...EASE_OUT), reduceMotion: ReduceMotion.Never }));
    onStart?.();
  };

  const stop = () => {
    if (startedAt === null) return;
    setStartedAt(null);
    run.set(withTiming(0, { duration: STOP * 1000, easing: Easing.bezier(...EASE_OUT), reduceMotion: ReduceMotion.Never }));
  };

  /** Scrolls the page to `y` in `seconds`, as the loop does. */
  const glide = (y: number, seconds: number) => {
    scheduleOnUI(
      (to: number, ms: number) => {
        'worklet';
        driving.set(true);
        glideY.set(scrollY.get());
        glideY.set(withTiming(to, { duration: ms, easing: Easing.bezier(...GLIDE), reduceMotion: ReduceMotion.Never }));
      },
      y,
      seconds * 1000,
    );
  };

  const choose = (index: number) => {
    if (index === tab) return;
    setTab(index);
    onTabChange?.(index);
    turn.set(withTiming(index, { duration: TAB_FADE, reduceMotion: ReduceMotion.Never }));
  };

  useAnimatedReaction(
    () => glideY.get(),
    (y) => {
      if (driving.get()) scrollTo(page, 0, y, false);
    },
  );

  // The control leaves when its middle passes an edge of what shows, and returns a little further in.
  useAnimatedReaction(
    () => scrollY.get(),
    (y) => {
      const middle = CONTROL_CENTRE - y;
      const was = gone.get();
      const edge = was ? HYSTERESIS : -HYSTERESIS;
      const now = middle < shownTop + edge || middle > shownBottom - edge;
      if (now === was) return;
      gone.set(now);
      ghostDrop.set(pillY - middle);
      scheduleOnRN(setAway, now);
    },
  );

  const wasLive = useRef(false);
  useEffect(() => {
    if (live === wasLive.current) return;
    wasLive.current = live;
    // The time rises back to its box only when the box is there to take it.
    ghosting.set(!live && startedAt !== null && tab === 0 && !reducedMotion);
    const [x1, y1, x2, y2] = live && !reducedMotion ? SWELL : EASE_OUT;
    pill.set(withTiming(live ? 1 : 0, { duration: (live ? FORM : COLLAPSE) * 1000, easing: Easing.bezier(x1, y1, x2, y2), reduceMotion: ReduceMotion.Never }));
  }, [live, startedAt, tab, reducedMotion, ghosting, pill]);

  // The count: one tick a second, each due at a whole number of seconds from the start.
  const finish = useRef({ stop, onDone });
  useEffect(() => {
    finish.current = { stop, onDone };
  });
  useEffect(() => {
    if (startedAt === null) return;
    let timer: ReturnType<typeof setTimeout>;
    const tick = (n: number) => {
      timer = setTimeout(() => {
        if (n >= duration) {
          finish.current.stop();
          finish.current.onDone?.();
          return;
        }
        setCounted(n);
        tick(n + 1);
      }, startedAt + n * 1000 - Date.now());
    };
    tick(1);
    return () => clearTimeout(timer);
  }, [startedAt, duration]);

  // The loop reads the latest functions when its timers fire.
  const latest = useRef({ start, stop, glide, choose });
  useEffect(() => {
    latest.current = { start, stop, glide, choose };
  });

  useEffect(() => {
    if (!autoPlay) return;
    let timers: ReturnType<typeof setTimeout>[] = [];
    const cycle = (origin: number) => {
      const at = (seconds: number, step: () => void) => setTimeout(step, origin + seconds * 1000 - Date.now());
      timers = [
        at(START_AT, () => latest.current.start(origin + START_AT * 1000)),
        at(SCROLL_AT, () => latest.current.glide(AUTO_SCROLL, SCROLL_UP)),
        at(BACK_AT, () => latest.current.glide(0, SCROLL_BACK)),
        at(STOP_AT, () => latest.current.stop()),
        at(LOOP, () => cycle(origin + LOOP * 1000)),
      ];
    };
    if (touches === 0) cycle(Date.now());
    else {
      timers = [
        setTimeout(() => {
          // Back to rest; the loop's first stretch covers the way there.
          latest.current.stop();
          latest.current.choose(0);
          latest.current.glide(0, SCROLL_HOME);
          cycle(Date.now());
        }, USER_HOLD * 1000),
      ];
    }
    return () => timers.forEach(clearTimeout);
  }, [autoPlay, touches]);

  /** A touch takes over from the autoPlay loop, which starts again from rest a while later. */
  const touched = () => {
    setTouches((n) => n + 1);
    scheduleOnUI(() => {
      'worklet';
      driving.set(false);
      cancelAnimation(glideY);
    });
  };

  const toControl = () => {
    choose(0);
    scheduleOnUI(() => {
      'worklet';
      scrollTo(page, 0, 0, true);
    });
  };

  const onScroll = useAnimatedScrollHandler((event) => {
    scrollY.set(event.contentOffset.y);
  });

  const recipeStyle = useAnimatedStyle(() => ({ opacity: 1 - turn.get() }));
  const profileStyle = useAnimatedStyle(() => ({ opacity: turn.get() }));
  const controlStyle = useAnimatedStyle(() => ({
    width: mix(controlWidth, BOX_WIDTH, run.get()),
    // A straight mix of the two greys: the default, corrected for gamma, is too light half way.
    backgroundColor: interpolateColor(run.get(), [0, 1], [BUTTON_FILL, BOX_FILL], 'RGB', { gamma: 1 }),
    opacity: 1 - clamp01(pill.get()),
  }));
  const startStyle = useAnimatedStyle(() => ({ opacity: 1 - run.get() }));
  // The box's time is not in the box: on its way back from the pill it rises to its place from under
  // the page, growing, and the box fades in around it.
  const timeStyle = useAnimatedStyle(() => {
    const way = clamp01(pill.get());
    const rising = ghosting.get();
    return {
      opacity: run.get() * (rising ? clamp01((1 - way) / GHOST_IN) * mix(1, 0.5, way) : 1 - way),
      transform: [{ translateY: rising ? way * ghostDrop.get() : 0 }, { scale: rising ? mix(1, GHOST_SCALE, way) : 1 }],
    };
  });
  // The cover behind the status bar comes in as the picture's last points pass under it.
  const coverStyle = useAnimatedStyle(() => ({
    opacity: clamp01((scrollY.get() - (HERO - shownTop - COVER_OVER)) / COVER_OVER) * (1 - turn.get()),
  }));

  // The ring of the stopwatch is the pill: it widens, darkens and rises to the middle of the bar.
  const capsuleStyle = useAnimatedStyle(() => {
    const swollen = reducedMotion ? 1 : pill.get();
    const height = mix(DIAL, PILL_HEIGHT, swollen);
    return {
      width: mix(DIAL, PILL_WIDTH, swollen),
      height,
      borderRadius: height / 2,
      backgroundColor: `rgba(17,17,17,${clamp01(swollen)})`,
      opacity: reducedMotion ? clamp01(pill.get()) : 1,
      transform: [{ translateY: mix(DIAL_DROP, 0, swollen) }],
    };
  });
  const tickStyle = useAnimatedStyle(() => {
    const swollen = reducedMotion ? clamp01(pill.get()) : pill.get();
    return { transform: [{ translateY: mix(DIAL_DROP, 0, swollen) - mix(DIAL, PILL_HEIGHT, swollen) / 2 - TICK_GAP }] };
  });
  const handStyle = useAnimatedStyle(() => ({ opacity: 1 - clamp01(pill.get() * 2.5) }));
  // Under reduced motion the ring stays as it is and fades out under the pill.
  const ringStyle = useAnimatedStyle(() => ({ opacity: 1 - clamp01(pill.get()) }));

  const onLayout = (event: LayoutChangeEvent) => {
    const { width, height } = event.nativeEvent.layout;
    setLaidOut({ width, height });
  };

  return (
    <View style={styles.screen} onLayout={onLayout} onTouchStart={touched}>
      <StatusBar style="dark" />

      <Animated.ScrollView
        ref={page}
        style={[styles.fill, recipeStyle]}
        pointerEvents={tab === 0 ? 'auto' : 'none'}
        accessibilityElementsHidden={tab !== 0}
        importantForAccessibility={tab === 0 ? 'auto' : 'no-hide-descendants'}
        contentContainerStyle={{ minHeight: size.height + AUTO_SCROLL, paddingBottom: barHeight + FADE }}
        showsVerticalScrollIndicator={false}
        scrollEventThrottle={16}
        onScroll={onScroll}
      >
        <View style={styles.hero}>
          {HERO_ART.map(([x, y, r, color], index) => (
            <View key={index} style={[styles.round, { left: size.width / 2 + x - r, top: y - r, width: r * 2, height: r * 2, borderRadius: r, backgroundColor: color }]} />
          ))}
        </View>
        <Text style={styles.title} numberOfLines={1} adjustsFontSizeToFit accessibilityRole="header">
          {title}
        </Text>
        <Text style={styles.subtitle} numberOfLines={1}>
          {subtitle}
        </Text>

        <View style={styles.controlSlot}>
          <Pressable
            accessibilityRole="button"
            accessibilityLabel={running ? `${time}, stop the timer` : startLabel}
            onPress={() => {
              tap();
              if (running) {
                stop();
                onStop?.(duration - counted);
              } else start();
            }}
          >
            <Animated.View style={[styles.control, controlStyle]}>
              <Animated.Text style={[styles.start, startStyle]} numberOfLines={1}>
                {startLabel}
              </Animated.Text>
            </Animated.View>
          </Pressable>
          <Animated.View pointerEvents="none" style={[styles.centred, timeStyle]}>
            {running && (
              <Animated.View exiting={FadeOut.duration(TIME_OUT * 1000)}>
                <RollingTime text={time} size={CONTROL_SIZE} color={INK} weight="500" />
              </Animated.View>
            )}
          </Animated.View>
        </View>

        {paragraphs.map((paragraph) => (
          <Text key={paragraph} style={styles.body}>
            {paragraph}
          </Text>
        ))}
        <Text style={styles.listTitle}>{listTitle}</Text>
        {list.map((item) => (
          <Text key={item} style={styles.item} numberOfLines={1}>
            {item}
          </Text>
        ))}
      </Animated.ScrollView>

      <Animated.View
        pointerEvents="none"
        accessibilityElementsHidden={tab !== 1}
        importantForAccessibility={tab === 1 ? 'auto' : 'no-hide-descendants'}
        style={[styles.fill, styles.profile, { paddingTop: insets.top + TITLE_GAP }, profileStyle]}
      >
        <Text style={styles.title} numberOfLines={1} accessibilityRole="header">
          {profileTitle}
        </Text>
        {[150, 190, 120].map((width) => (
          <View key={width} style={styles.row}>
            <View style={styles.thumb} />
            <View>
              <View style={[styles.rowLine, { width }]} />
              <View style={[styles.rowLine, styles.rowShort]} />
            </View>
          </View>
        ))}
      </Animated.View>

      <Animated.View pointerEvents="none" style={[styles.cover, { height: insets.top + COVER_FADE }, coverStyle]}>
        <LinearGradient style={styles.fill} colors={[PAGE, PAGE, 'rgba(252,252,252,0)']} locations={[0, insets.top / (insets.top + COVER_FADE), 1]} />
      </Animated.View>
      <LinearGradient pointerEvents="none" style={[styles.fade, { bottom: barHeight }]} colors={['rgba(255,255,255,0)', BAR]} />
      <View style={[styles.bar, { height: barHeight }]}>
        <Pressable
          style={[styles.tab, { left: TAB_INSET - TAB_BAR / 2 }]}
          accessibilityRole="tab"
          accessibilityLabel="Recipe"
          accessibilityState={{ selected: tab === 0 }}
          onPress={() => {
            if (tab !== 0) select();
            choose(0);
          }}
        >
          <Icon path={HAT} color={tab === 0 ? INK : INACTIVE} />
        </Pressable>

        <Pressable
          style={[styles.timer, { left: (size.width - PILL_WIDTH) / 2 }]}
          accessibilityRole="button"
          accessibilityLabel={live ? `Timer, ${time}` : 'Timer'}
          onPress={() => {
            select();
            toControl();
          }}
        >
          {reducedMotion && (
            <Animated.View style={[styles.ring, ringStyle]}>
              <View style={styles.hand} />
            </Animated.View>
          )}
          <Animated.View style={[styles.tick, tickStyle]} />
          <Animated.View style={[styles.capsule, capsuleStyle]}>
            {!reducedMotion && <Animated.View style={[styles.hand, handStyle]} />}
            {live && (
              <Animated.View exiting={FadeOut.duration(TIME_OUT * 1000)}>
                <RollingTime text={time} size={PILL_SIZE} color="#ffffff" weight="600" />
              </Animated.View>
            )}
          </Animated.View>
        </Pressable>

        <Pressable
          style={[styles.tab, { right: TAB_INSET - TAB_BAR / 2 }]}
          accessibilityRole="tab"
          accessibilityLabel={profileTitle}
          accessibilityState={{ selected: tab === 1 }}
          onPress={() => {
            if (tab !== 1) select();
            choose(1);
          }}
        >
          <Icon path={PERSON} color={tab === 1 ? INK : INACTIVE} />
        </Pressable>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  screen: {
    flex: 1,
    backgroundColor: PAGE,
  },
  fill: {
    position: 'absolute',
    left: 0,
    right: 0,
    top: 0,
    bottom: 0,
  },
  hero: {
    height: HERO,
    backgroundColor: HERO_FILL,
    overflow: 'hidden',
  },
  round: {
    position: 'absolute',
  },
  title: {
    marginTop: TITLE_GAP,
    marginHorizontal: MARGIN,
    height: TITLE_LINE,
    color: INK,
    fontSize: 28,
    lineHeight: TITLE_LINE,
    fontWeight: '700',
    includeFontPadding: false,
  },
  subtitle: {
    marginTop: SUBTITLE_GAP,
    marginHorizontal: MARGIN,
    height: SUBTITLE_LINE,
    color: MUTE,
    fontSize: 15,
    lineHeight: SUBTITLE_LINE,
    includeFontPadding: false,
  },
  controlSlot: {
    marginTop: CONTROL_GAP,
    height: CONTROL,
    alignItems: 'center',
    justifyContent: 'center',
    // The time that rises from the pill is drawn over the text below.
    zIndex: 1,
  },
  control: {
    height: CONTROL,
    borderRadius: CONTROL_RADIUS,
    borderCurve: 'continuous',
    alignItems: 'center',
    justifyContent: 'center',
    overflow: 'hidden',
  },
  start: {
    position: 'absolute',
    color: '#ffffff',
    fontSize: 17,
    lineHeight: 22,
    fontWeight: '500',
    includeFontPadding: false,
  },
  // As large as the slot, so that the time fades out where it stood when it is taken away.
  centred: {
    position: 'absolute',
    left: 0,
    right: 0,
    top: 0,
    bottom: 0,
    alignItems: 'center',
    justifyContent: 'center',
  },
  body: {
    marginTop: BODY_GAP - BODY_LEADING / 2,
    marginBottom: -BODY_LEADING / 2,
    marginHorizontal: MARGIN,
    color: BODY,
    fontSize: 17,
    lineHeight: BODY_LINE,
    includeFontPadding: false,
  },
  listTitle: {
    marginTop: BODY_GAP + 8,
    marginBottom: 8,
    marginHorizontal: MARGIN,
    color: INK,
    fontSize: 20,
    lineHeight: 26,
    fontWeight: '600',
    includeFontPadding: false,
  },
  item: {
    marginHorizontal: MARGIN,
    color: BODY,
    fontSize: 17,
    lineHeight: BODY_LINE + 4,
    includeFontPadding: false,
  },
  profile: {
    backgroundColor: PAGE,
  },
  row: {
    marginTop: 20,
    marginHorizontal: MARGIN,
    flexDirection: 'row',
    alignItems: 'center',
  },
  thumb: {
    width: 56,
    height: 56,
    marginRight: 16,
    borderRadius: 14,
    borderCurve: 'continuous',
    backgroundColor: BOX_FILL,
  },
  rowLine: {
    height: 12,
    borderRadius: 6,
    backgroundColor: BOX_FILL,
  },
  rowShort: {
    width: 90,
    marginTop: 10,
    backgroundColor: ROW_FILL,
  },
  cover: {
    position: 'absolute',
    left: 0,
    right: 0,
    top: 0,
  },
  fade: {
    position: 'absolute',
    left: 0,
    right: 0,
    height: FADE,
  },
  bar: {
    position: 'absolute',
    left: 0,
    right: 0,
    bottom: 0,
    backgroundColor: BAR,
  },
  tab: {
    position: 'absolute',
    top: 0,
    width: TAB_BAR,
    height: TAB_BAR,
    alignItems: 'center',
    justifyContent: 'center',
  },
  icon: {
    width: TAB_ICON,
    height: TAB_ICON,
  },
  timer: {
    position: 'absolute',
    top: 0,
    width: PILL_WIDTH,
    height: TAB_BAR,
    alignItems: 'center',
    justifyContent: 'center',
  },
  capsule: {
    borderWidth: TAB_STROKE,
    borderColor: INK,
    alignItems: 'center',
    justifyContent: 'center',
    overflow: 'hidden',
  },
  ring: {
    position: 'absolute',
    width: DIAL,
    height: DIAL,
    borderRadius: DIAL / 2,
    borderWidth: TAB_STROKE,
    borderColor: INK,
    alignItems: 'center',
    justifyContent: 'center',
    transform: [{ translateY: DIAL_DROP }],
  },
  tick: {
    position: 'absolute',
    width: TICK,
    height: TAB_STROKE,
    borderRadius: TAB_STROKE / 2,
    backgroundColor: INK,
  },
  // The hand points up and to the right from the middle of the ring.
  hand: {
    position: 'absolute',
    width: TAB_STROKE,
    height: HAND,
    borderRadius: TAB_STROKE / 2,
    backgroundColor: INK,
    transform: [{ rotate: '45deg' }, { translateY: -HAND / 2 }],
  },
});
