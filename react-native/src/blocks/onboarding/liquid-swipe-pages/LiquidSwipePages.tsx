import { Canvas, Circle, Group, Path, Rect } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useEffect, useState } from 'react';
import { type LayoutChangeEvent, Platform, Pressable, StyleSheet, Text, View, useWindowDimensions } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  type SharedValue,
  interpolateColor,
  useAnimatedStyle,
  useDerivedValue,
  useFrameCallback,
  useReducedMotion,
  useSharedValue,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

import { ART_HEIGHT, ART_WIDTH, type ArtKind, Artwork } from './Artwork';
import {
  AMBIENT,
  ARROW_INSET,
  BACKING,
  BUMP,
  BUMP_Y,
  COMPLETE_AT,
  DRAG_AT,
  DRAG_FOR,
  FLICK,
  HAND,
  LEAD,
  LET_GO,
  PERIOD,
  RELEASE,
  REST,
  SCRIPT,
  SKIP_DRAG,
  SKIP_SPEED,
  STRIP,
  type Shape,
  TAP_DRAG,
  USER_HOLD,
  type Wave,
  held,
  outline,
  resolve,
  resting,
  script,
  settle,
} from './wave';

export type LiquidPage = {
  title: string;
  body: string;
  /** The page's colour, and the colour of everything written on it. */
  color: string;
  ink: string;
  art: ArtKind;
};

type Props = {
  /** The pages, in order; the last one's wave leads back to the first. */
  pages?: LiquidPage[];
  /** The button of every page but the last, which goes to the last page. */
  skipLabel?: string;
  /** The last page's button. */
  doneLabel?: string;
  /** Pulls the wave by itself, one page every PERIOD seconds, back at the first page after LOOP. */
  autoPlay?: boolean;
  /** Called when a wave has brought a page in. */
  onPageChange?: (index: number) => void;
  onSkip?: () => void;
  /** Called by the last page's button. */
  onDone?: () => void;
};

const PAGES: LiquidPage[] = [
  { title: 'Best Digital Solution', body: 'Everything your team needs, in one tidy place.', color: '#392eb8', ink: '#35d0f5', art: 'tiles' },
  { title: 'Achieve Your Goal', body: 'Set a target and watch the steps add up.', color: '#32c96f', ink: '#f6f1a7', art: 'flag' },
  { title: 'Increase Your Value', body: 'Small wins compound. We keep the score.', color: '#e76239', ink: '#ffd9c2', art: 'coins' },
];

const MARGIN = 28;
// The texts stop short of the bump.
const TEXT_RIGHT = 96;
// The picture's centre, as a share of the screen's height.
const ART_Y = 0.3;
const TITLE_GAP = 18;
const BODY_HEIGHT = 48;
const PAGER_GAP = 42;
const DASH = { width: 14, wide: 22, height: 3, gap: 6 };
const BUTTON_GAP = 82;
const BUTTON = { width: 152, wide: 180, height: 58, radius: 8, stroke: 1.5 };
const ARROW_INK = 'rgba(255,255,255,0.8)';
const RING = 36;
const CHEVRON = 'M-3 -6L3 0L-3 6';
// The right side of the screen takes the drag; a tap this close to the arrow plays the wave.
const GRIP = 80;
const ARROW_REACH = 28;

function tick() {
  if (process.env.EXPO_OS !== 'web') Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
}

function select() {
  if (process.env.EXPO_OS !== 'web') Haptics.selectionAsync().catch(() => {});
}

type PageProps = {
  index: number;
  count: number;
  page: LiquidPage;
  label: string;
  wide: boolean;
  /** Only the page that is showing takes touches. */
  enabled: boolean;
  shape: SharedValue<Shape>;
  clock: SharedValue<number>;
  still: boolean;
  width: number;
  height: number;
  bottom: number;
  onPress: () => void;
};

/** What is written and drawn on one page. It never moves with the wave: the wave uncovers it. */
function Page({ index, count, page, label, wide, enabled, shape, clock, still, width, height, bottom, onPress }: PageProps) {
  // The page that shows lies under the wave, the one coming in over it.
  const layer = useAnimatedStyle(() => {
    const s = shape.get();
    const incoming = s.incoming === index;
    const showing = s.page === index;
    return { opacity: incoming ? s.reveal : showing ? 1 : 0, zIndex: incoming ? 3 : showing ? 1 : 0 };
  });
  const art = useAnimatedStyle(() => {
    const s = shape.get();
    return { transform: [{ scale: s.incoming === index && !still ? 0.9 + 0.1 * s.reveal : 1 }] };
  });

  return (
    <Animated.View
      pointerEvents={enabled ? 'box-none' : 'none'}
      accessibilityElementsHidden={!enabled}
      importantForAccessibility={enabled ? 'auto' : 'no-hide-descendants'}
      style={[styles.fill, layer]}
    >
      <Animated.View
        pointerEvents="none"
        accessibilityElementsHidden
        importantForAccessibility="no-hide-descendants"
        style={[styles.art, { left: (width - ART_WIDTH) / 2, top: height * ART_Y - ART_HEIGHT / 2 }, art]}
      >
        <Artwork kind={page.art} color={page.color} clock={clock} still={still} />
      </Animated.View>

      <View pointerEvents="box-none" style={[styles.column, { bottom }]}>
        <Text style={[styles.title, { color: page.ink }]} numberOfLines={1}>
          {page.title}
        </Text>
        <Text style={[styles.body, { color: page.ink }]} numberOfLines={2} lineBreakStrategyIOS="standard">
          {page.body}
        </Text>
        <View style={styles.pager} accessibilityLabel={`Page ${index + 1} of ${count}`}>
          {Array.from({ length: count }, (_, dash) => (
            <View
              key={dash}
              style={[styles.dash, { backgroundColor: page.ink, width: dash === index ? DASH.wide : DASH.width, opacity: dash === index ? 1 : 0.4 }]}
            />
          ))}
        </View>
        <Pressable onPress={onPress} accessibilityRole="button" style={styles.buttonSlot}>
          {({ pressed }) => (
            <Animated.View
              style={[
                styles.button,
                { width: wide ? BUTTON.wide : BUTTON.width, borderColor: page.ink, opacity: pressed ? 0.6 : 1, transitionProperty: 'opacity', transitionDuration: 120 },
              ]}
            >
              <Text style={[styles.buttonLabel, { color: page.ink }]} numberOfLines={1}>
                {label}
              </Text>
            </Animated.View>
          )}
        </Pressable>
      </View>
    </Animated.View>
  );
}

/**
 * Onboarding pages that change with a liquid wave. The next page waits behind the right edge as a
 * bump with an arrow in it; dragging the bump pulls a wave of the next page's colour across the
 * screen, and letting go past the middle sends it to the left side, where it settles with one
 * soft overshoot. The pages' content stays where it is: the wave covers one and uncovers the next.
 */
export function LiquidSwipePages({
  pages = PAGES,
  skipLabel = 'SKIP',
  doneLabel = 'GET STARTED',
  autoPlay = false,
  onPageChange,
  onSkip,
  onDone,
}: Props) {
  const still = useReducedMotion();
  const insets = useSafeAreaInsets();
  const window = useWindowDimensions();
  // The screen as laid out; the window's size stands in until then.
  const [laidOut, setLaidOut] = useState<{ width: number; height: number } | null>(null);
  const { width, height } = laidOut ?? window;
  const count = pages.length;
  const colors = pages.map((page) => page.color);
  // The page that takes touches. What is drawn follows `wave`, on the UI thread.
  const [current, setCurrent] = useState(0);

  // Seconds since the first frame.
  const clock = useSharedValue(0);
  const firstFrame = useSharedValue(-1);
  const wave = useSharedValue<Wave>(resting(0, -LEAD));
  // autoPlay: when the next wave starts, and when the loop may start over after a touch.
  const next = useSharedValue(Infinity);
  const resume = useSharedValue(Infinity);
  // The travel a drag started from, less the finger's offset at that moment.
  const grabbed = useSharedValue(0);

  const shape = useDerivedValue(() => resolve(wave.get(), clock.get(), width, height, count, still));

  const arrived = (index: number) => {
    setCurrent(index);
    onPageChange?.(index);
  };

  useEffect(() => {
    // Periods start on the ambient beat, so that what moves by itself looks the same in each.
    next.set(autoPlay ? Math.ceil(clock.get() / AMBIENT) * AMBIENT + DRAG_AT : Infinity);
    resume.set(Infinity);
  }, [autoPlay, clock, next, resume]);

  useFrameCallback((frame) => {
    if (firstFrame.get() < 0) firstFrame.set(frame.timestamp);
    const now = (frame.timestamp - firstFrame.get()) / 1000;
    clock.set(now);
    const before = wave.get();
    const w = settle(before, now);
    if (w !== before) {
      wave.set(w);
      if (w.page !== before.page) scheduleOnRN(arrived, w.page);
    }
    if (!autoPlay || w.mode !== REST) return;
    if (now >= resume.get()) {
      // Back to the first page if a touch left another one showing, then on with the loop.
      resume.set(Infinity);
      let free = Math.max(now, w.t0 + LEAD);
      if (w.page !== 0) {
        wave.set(script(w.page, 0, now, SKIP_DRAG, SKIP_SPEED));
        free = now + SKIP_DRAG + RELEASE / SKIP_SPEED + LEAD;
      }
      next.set(Math.ceil(free / AMBIENT) * AMBIENT + DRAG_AT);
    } else if (now >= next.get()) {
      // Timed from when it was due, not from this frame: every loop is the same length.
      wave.set(script(w.page, (w.page + 1) % count, next.get(), DRAG_FOR, 1));
      next.set(next.get() + PERIOD);
    }
  });

  /** A touch takes over from the autoPlay loop, which starts again from rest a while later. */
  const touched = (now: number) => {
    'worklet';
    if (!autoPlay) return;
    next.set(Infinity);
    resume.set(now + USER_HOLD);
    const w = wave.get();
    // A wave the loop is still dragging out is dropped; one it has let go runs to its end.
    if (w.mode === SCRIPT && now < w.t0 + w.dragFor) wave.set({ ...w, ...held(w, now, width, height, still), mode: BACKING, t0: now });
  };

  /** Plays a whole wave to `target`, as the autoPlay loop does. */
  const play = (target: number, dragFor: number, speed: number) => {
    'worklet';
    const now = clock.get();
    touched(now);
    const w = settle(wave.get(), now);
    if (w.mode !== REST || target === w.page) return false;
    wave.set(script(w.page, target, now, dragFor, speed));
    return true;
  };

  const pan = Gesture.Pan()
    .onStart((event) => {
      const now = clock.get();
      touched(now);
      const w = settle(wave.get(), now);
      if (w.mode !== REST && w.mode !== BACKING) return;
      const from = held(w, now, width, height, still);
      grabbed.set(from.travel + event.translationX);
      wave.set({ ...w, mode: HAND, target: (w.page + 1) % count, t0: now, speed: 1, travel: from.travel, cy: event.y, cyFrom: from.cy });
    })
    .onUpdate((event) => {
      const w = wave.get();
      if (w.mode === HAND) wave.set({ ...w, travel: Math.max(0, grabbed.get() - event.translationX), cy: event.y });
    })
    .onEnd((event) => {
      const w = wave.get();
      if (w.mode !== HAND) return;
      const now = clock.get();
      const complete = w.travel > width * COMPLETE_AT || event.velocityX < -FLICK;
      wave.set({ ...w, ...held(w, now, width, height, still), mode: complete ? LET_GO : BACKING, t0: now });
      if (complete) scheduleOnRN(select);
    })
    .onFinalize(() => {
      // A drag the system took away springs back.
      const w = wave.get();
      if (w.mode === HAND) wave.set({ ...w, ...held(w, clock.get(), width, height, still), mode: BACKING, t0: clock.get() });
    });

  const tap = Gesture.Tap().onEnd((event, success) => {
    const near = Math.abs(event.x - (GRIP - STRIP - BUMP + ARROW_INSET)) < ARROW_REACH && Math.abs(event.y - height * BUMP_Y) < ARROW_REACH;
    if (success && near && play((wave.get().page + 1) % count, TAP_DRAG, 1)) scheduleOnRN(select);
  });

  const press = () => {
    tick();
    if (current === count - 1) {
      touched(clock.get());
      onDone?.();
    } else if (play(count - 1, SKIP_DRAG, SKIP_SPEED)) {
      onSkip?.();
    }
  };

  const ground = useAnimatedStyle(() => ({ backgroundColor: colors[shape.get().page] }));
  const path = useDerivedValue(() => outline(shape.get(), width, height));
  const tint = useDerivedValue(() => {
    const s = shape.get();
    return interpolateColor(s.mix, [0, 1], [colors[(s.page + 1) % count], colors[s.tint]]);
  });
  // Reduced motion: the next page's colour fades in over the screen.
  const flat = useDerivedValue(() => colors[shape.get().tint]);
  const fade = useDerivedValue(() => shape.get().fade);
  const arrowAt = useDerivedValue(() => {
    const s = shape.get();
    return [{ translateX: width - s.reach - s.depth + ARROW_INSET }, { translateY: s.cy }];
  });
  const arrow = useDerivedValue(() => shape.get().arrow);

  const onLayout = (event: LayoutChangeEvent) => {
    const { width: w, height: h } = event.nativeEvent.layout;
    setLaidOut({ width: w, height: h });
  };

  const bottom = Math.max(insets.bottom - 5, 24);

  return (
    <View style={styles.screen} onLayout={onLayout}>
      <StatusBar style="light" />
      <Animated.View style={[styles.fill, ground]} />

      {pages.map((page, index) => (
        <Page
          key={index}
          index={index}
          count={count}
          page={page}
          label={index === count - 1 ? doneLabel : skipLabel}
          wide={index === count - 1}
          enabled={index === current}
          shape={shape}
          clock={clock}
          still={still}
          width={width}
          height={height}
          bottom={bottom}
          onPress={press}
        />
      ))}

      <Canvas style={[styles.fill, styles.wave]} pointerEvents="none">
        <Path path={path} color={tint} />
        <Rect x={0} y={0} width={width} height={height} color={flat} opacity={fade} />
        <Group transform={arrowAt} opacity={arrow}>
          <Circle cx={0} cy={0} r={(RING - 1.5) / 2} style="stroke" strokeWidth={1.5} color={ARROW_INK} />
          <Path path={CHEVRON} style="stroke" strokeWidth={1.5} strokeCap="round" strokeJoin="round" color={ARROW_INK} />
        </Group>
      </Canvas>

      <GestureDetector gesture={Gesture.Race(pan, tap)}>
        <View
          collapsable={false}
          style={styles.grip}
          accessible
          accessibilityRole="button"
          accessibilityLabel="Next page"
          onAccessibilityTap={() => play((current + 1) % count, TAP_DRAG, 1)}
        />
      </GestureDetector>
    </View>
  );
}

const styles = StyleSheet.create({
  screen: {
    flex: 1,
    overflow: 'hidden',
  },
  fill: {
    position: 'absolute',
    left: 0,
    right: 0,
    top: 0,
    bottom: 0,
  },
  wave: {
    zIndex: 2,
  },
  grip: {
    position: 'absolute',
    right: 0,
    top: 0,
    bottom: 0,
    width: GRIP,
    zIndex: 4,
  },
  art: {
    position: 'absolute',
    width: ART_WIDTH,
    height: ART_HEIGHT,
  },
  column: {
    position: 'absolute',
    left: MARGIN,
    right: TEXT_RIGHT,
    alignItems: 'flex-start',
  },
  title: {
    fontFamily: Platform.select({ ios: 'ui-rounded' }),
    fontSize: 24,
    lineHeight: 30,
    fontWeight: '700',
    includeFontPadding: false,
  },
  body: {
    marginTop: TITLE_GAP,
    height: BODY_HEIGHT,
    fontSize: 15,
    lineHeight: 24,
    opacity: 0.85,
    includeFontPadding: false,
  },
  pager: {
    marginTop: PAGER_GAP,
    height: DASH.height,
    flexDirection: 'row',
    gap: DASH.gap,
  },
  dash: {
    height: DASH.height,
    borderRadius: DASH.height / 2,
  },
  buttonSlot: {
    marginTop: BUTTON_GAP,
  },
  button: {
    height: BUTTON.height,
    borderRadius: BUTTON.radius,
    borderWidth: BUTTON.stroke,
    alignItems: 'center',
    justifyContent: 'center',
  },
  buttonLabel: {
    fontFamily: Platform.select({ ios: 'ui-rounded' }),
    fontSize: 14,
    lineHeight: 18,
    fontWeight: '700',
    letterSpacing: 0.4,
    includeFontPadding: false,
  },
});
