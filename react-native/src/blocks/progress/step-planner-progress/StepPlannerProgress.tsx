import {
  BlurMask,
  Canvas,
  Circle,
  DashPathEffect,
  Group,
  LinearGradient,
  Path,
  RoundedRect,
  Skia,
  rect,
  rrect,
  vec,
} from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { useEffect, useState } from 'react';
import { Pressable, StyleSheet, Text, TextInput, type TextInputProps, type TextStyle, View } from 'react-native';
import Animated, {
  Easing,
  ReduceMotion,
  type SharedValue,
  cancelAnimation,
  cubicBezier,
  interpolateColor,
  useAnimatedProps,
  useAnimatedReaction,
  useAnimatedStyle,
  useDerivedValue,
  useFrameCallback,
  useReducedMotion,
  useSharedValue,
  withRepeat,
  withTiming,
} from 'react-native-reanimated';
import { scheduleOnRN } from 'react-native-worklets';

type Props = {
  /** Share of the steps completed, 0…1. Leave undefined to show the reference's 2 of 5 (40%). */
  progress?: number;
  /** Plays by itself: the card's parts arrive one after another, two steps complete, hold, start over. */
  autoPlay?: boolean;
  title?: string;
  steps?: string[];
  tabs?: string[];
  onTabChange?: (index: number) => void;
  accessibilityLabel?: string;
};

const WIDTH = 330;
const HEIGHT = 374;
const RADIUS = 26;
// The canvas overflows the card by this much on every side to fit its shadow.
const BLEED = 36;

const INSET = 20.5;
const INNER_W = WIDTH - INSET * 2;
const TABS_Y = 61;
const TABS_H = 39;
// Left edge and width of each tab's pill, and where its icon and label sit.
const TAB_FRAMES = [
  { x: 25, w: 86, icon: 46.5, text: 59 },
  { x: 112, w: 98, icon: 131, text: 144 },
  { x: 213, w: 91.5, icon: 233, text: 246 },
];
const PILL_Y = 65;
const PILL_H = 31;
const STEP_Y = 131;
const SEG_Y = 152;
const SEG_H = 7.3;
const SEG_GAP = 3.3;
const LIST_Y = 181.5;
const LIST_H = 137;
const LIST_R = 20;
const ROW_FIRST = 23;
const ROW_PITCH = 45;
const ROW_X = 43.7;
const ROW_R = 12;
const ROW_TEXT_X = 70;
const ROWS_SHOWN = 3;
const FOOTER_Y = 350;

// The reference rests with two of five steps done.
const REST_DONE = 2;
const DEFAULT_STEPS = ['Create Account', 'Business Details', 'Payment Method', 'Team Setup', 'Final Review'];
const DEFAULT_TABS = ['Setup', 'Timeline', 'Goals'];

// The autoPlay run is one clock in seconds: the parts arrive, each step completes, a hold, a fade.
const ENTER_FIRST = 0.1;
const ENTER_STAGGER = 0.09;
const ENTER_LENGTH = 0.5;
const WAVE_FIRST = 0.45;
const WAVE_STAGGER = 0.07;
const WAVE_LENGTH = 0.4;
const STEP_FIRST = 1.5;
const STEP_EVERY = 1.5;
const STEP_LENGTH = 0.7;
const HOLD = 2.6;
const EXIT = 0.4;
const GAP = 0.3;

const GREEN = '#7afa9b';
const EASE_OUT = Easing.bezier(0.23, 1, 0.32, 1);
const CSS_EASE_OUT = cubicBezier(0.23, 1, 0.32, 1);

const CHECK = 'M-3.1 0.2L-1 2.3L3.2 -2.1';
const RING = Skia.Path.Make().addCircle(0, 0, ROW_R + 0.4);

function seg(t: number, from: number, to: number) {
  'worklet';
  return Math.min(1, Math.max(0, (t - from) / (to - from)));
}
function easeOut(x: number) {
  'worklet';
  return 1 - Math.pow(1 - x, 3);
}
function easeInOut(x: number) {
  'worklet';
  return x < 0.5 ? 4 * x * x * x : 1 - Math.pow(-2 * x + 2, 3) / 2;
}
function easeOutBack(x: number) {
  'worklet';
  const c = 1.7;
  return 1 + (c + 1) * Math.pow(x - 1, 3) + c * Math.pow(x - 1, 2);
}
/** How far the checklist has scrolled: the current step stays on the middle row. */
function scrollOf(done: number, count: number) {
  'worklet';
  return Math.min(Math.max(done - 1, 0), Math.max(count - ROWS_SHOWN, 0)) * ROW_PITCH;
}

type Motion = {
  /** Steps done, a fraction while one is completing. */
  done: SharedValue<number>;
  /** Seconds into the autoPlay run. */
  t: SharedValue<number>;
  /** Free-running seconds for the light that circles the current segment. */
  time: SharedValue<number>;
  playing: boolean;
  /** Seconds into the run when everything fades out. */
  exitAt: number;
  count: number;
};

/** Opacity and rise of the card's `part`-th block: they arrive one after another when a run starts. */
function useEntrance({ t, playing, exitAt }: Motion, part: number) {
  const begin = ENTER_FIRST + ENTER_STAGGER * part;
  const arrived = useDerivedValue(() => (playing ? easeOut(seg(t.get(), begin, begin + ENTER_LENGTH)) : 1));
  const opacity = useDerivedValue(() => arrived.get() * (playing ? 1 - seg(t.get(), exitAt, exitAt + EXIT) : 1));
  const transform = useDerivedValue(() => [{ translateY: 10 * (1 - arrived.get()) }]);
  return { opacity, transform };
}

function useEntranceStyle(motion: Motion, part: number) {
  const { opacity, transform } = useEntrance(motion, part);
  return useAnimatedStyle(() => ({ opacity: opacity.get(), transform: transform.get() }));
}

/** One segment of the step bar: grey, outlined with a circling light while current, or filled. */
function Segment({ index, done, t, time, playing, count, still }: Motion & { index: number; still: boolean }) {
  const w = (INNER_W - SEG_GAP * (count - 1)) / count;
  const x = BLEED + INSET + (w + SEG_GAP) * index;
  const y = BLEED + SEG_Y;
  const shape = rrect(rect(x, y, w, SEG_H), SEG_H / 2, SEG_H / 2);
  const outline = rrect(rect(x + 0.6, y + 0.6, w - 1.2, SEG_H - 1.2), SEG_H / 2, SEG_H / 2);
  const perimeter = 2 * (w - SEG_H) + Math.PI * SEG_H;

  // The segments grow in from the left one after another when a run starts.
  const grown = useDerivedValue(() => {
    const begin = WAVE_FIRST + WAVE_STAGGER * index;
    return [{ scaleX: playing ? easeOutBack(seg(t.get(), begin, begin + WAVE_LENGTH)) : 1 }];
  });
  // 0…1 while this step completes: the fill sweeps across from the left.
  const filled = useDerivedValue(() => Math.min(1, Math.max(0, done.get() - index)));
  const fill = useDerivedValue(() => rect(x, y, w * filled.get(), SEG_H));
  const lit = useDerivedValue(() => Math.min(1, filled.get() * 8));
  // A glint rides the leading edge of the sweep.
  const glintX = useDerivedValue(() => x + w * filled.get());
  const glint = useDerivedValue(() => (filled.get() > 0 && filled.get() < 1 ? Math.sin(Math.PI * filled.get()) : 0));
  const current = useDerivedValue(() => seg(done.get() - index, -0.25, 0) * (1 - seg(done.get() - index, 0.8, 1)));
  const phase = useDerivedValue(() => -time.get() * 42);

  return (
    <Group transform={grown} origin={vec(x, y)}>
      <RoundedRect rect={shape} color="#2c3039" />
      <Group opacity={current}>
        <RoundedRect rect={shape} color="rgba(122,250,155,0.14)" />
        <RoundedRect rect={outline} style="stroke" strokeWidth={3} color="rgba(122,250,155,0.4)">
          <BlurMask blur={3} style="normal" />
        </RoundedRect>
        <RoundedRect rect={outline} style="stroke" strokeWidth={1.2} color="rgba(122,250,155,0.8)" />
        {!still && (
          <RoundedRect rect={outline} style="stroke" strokeWidth={1.4} strokeCap="round" color="#eafff0">
            <DashPathEffect intervals={[perimeter * 0.16, perimeter * 0.84]} phase={phase} />
          </RoundedRect>
        )}
      </Group>
      <Group opacity={lit}>
        <Group clip={fill}>
          <RoundedRect rect={shape} color="rgba(122,250,155,0.55)">
            <BlurMask blur={5} style="normal" />
          </RoundedRect>
        </Group>
        <Group clip={shape}>
          <Group clip={fill}>
            <RoundedRect rect={shape} color={GREEN} />
          </Group>
          <Circle cx={glintX} cy={y + SEG_H / 2} r={7} color="#ffffff" opacity={glint}>
            <BlurMask blur={4} style="normal" />
          </Circle>
        </Group>
      </Group>
    </Group>
  );
}

/** One step of the checklist: ticked, current, or still to come (scalloped). */
function Row({ index, done, count }: Motion & { index: number }) {
  const y = BLEED + LIST_Y + ROW_FIRST + ROW_PITCH * index;
  const scroll = useDerivedValue(() => [{ translateX: BLEED + ROW_X }, { translateY: y - scrollOf(done.get(), count) }]);
  // 0…1 while this step completes.
  const ticked = useDerivedValue(() => Math.min(1, Math.max(0, done.get() - index)));
  const upcoming = useDerivedValue(() => seg(index - done.get(), 0.3, 0.9));
  const pop = useDerivedValue(() => [{ scale: 0.4 + 0.6 * easeOutBack(seg(ticked.get(), 0.25, 0.85)) }]);
  const shown = useDerivedValue(() => seg(ticked.get(), 0.25, 0.5));
  const drawn = useDerivedValue(() => seg(ticked.get(), 0.55, 1));
  // A ring spreads from the circle as its tick lands.
  const rippleR = useDerivedValue(() => ROW_R + 10 * easeOut(seg(ticked.get(), 0.25, 1)));
  const ripple = useDerivedValue(() => (ticked.get() > 0.25 && ticked.get() < 1 ? 0.7 * (1 - seg(ticked.get(), 0.25, 1)) : 0));

  return (
    <Group transform={scroll}>
      {index < count - 1 && <Path path={`M0 ${ROW_R + 4}V${ROW_PITCH - ROW_R - 4}`} style="stroke" strokeWidth={1} color="#30323d" />}
      <Group opacity={upcoming}>
        <Path path={RING} style="stroke" strokeWidth={2.4} color="#17181d">
          <DashPathEffect intervals={[2.6, 2.27]} />
        </Path>
      </Group>
      <Circle cx={0} cy={0} r={ROW_R} color="#17181d" />
      <Circle cx={0} cy={0} r={ROW_R - 0.5} style="stroke" strokeWidth={1} color="rgba(255,255,255,0.06)" />
      <Circle cx={0} cy={0} r={rippleR} style="stroke" strokeWidth={1.4} color={GREEN} opacity={ripple} />
      <Group opacity={shown}>
        <Circle cx={0} cy={0} r={ROW_R - 0.5} style="stroke" strokeWidth={1} color="rgba(122,250,155,0.28)" />
        <Group transform={pop}>
          <Circle cx={0} cy={0} r={8} color="rgba(122,250,155,0.5)">
            <BlurMask blur={4} style="normal" />
          </Circle>
          <Circle cx={0} cy={0} r={6.7} color={GREEN} />
          <Path path={CHECK} style="stroke" strokeWidth={1.6} strokeCap="round" strokeJoin="round" color="#10311a" start={0} end={drawn} />
        </Group>
      </Group>
    </Group>
  );
}

function Surface(motion: Motion & { tab: number; pillX: SharedValue<number>; pillW: SharedValue<number>; still: boolean }) {
  const { count, tab, pillX, pillW } = motion;
  const x = BLEED;
  const y = BLEED;
  const tabs = useEntrance(motion, 1);
  const bar = useEntrance(motion, 2);
  const list = useEntrance(motion, 3);
  const indices = [];
  for (let index = 0; index < count; index++) indices.push(index);
  const listShape = rrect(rect(x + INSET, y + LIST_Y, INNER_W, LIST_H), LIST_R, LIST_R);
  const tint = (index: number) => (index === tab ? '#f0f2f6' : '#b3b7c2');
  // The selected tab's pill slides and resizes to the tab that was chosen.
  const pill = useDerivedValue(() => rrect(rect(x + pillX.get(), y + PILL_Y, pillW.get(), PILL_H), PILL_H / 2, PILL_H / 2));

  return (
    <Canvas style={styles.canvas}>
      <RoundedRect x={x + 8} y={y + 16} width={WIDTH - 16} height={HEIGHT - 16} r={RADIUS} color="rgba(0,0,0,0.55)">
        <BlurMask blur={14} style="normal" />
      </RoundedRect>
      <RoundedRect x={x} y={y} width={WIDTH} height={HEIGHT} r={RADIUS}>
        <LinearGradient start={vec(0, y)} end={vec(0, y + HEIGHT)} colors={['#141416', '#1c1d22']} />
      </RoundedRect>
      <RoundedRect x={x + 0.5} y={y + 0.5} width={WIDTH - 1} height={HEIGHT - 1} r={RADIUS - 0.5} style="stroke" strokeWidth={1} color="rgba(255,255,255,0.05)" />

      <Group opacity={tabs.opacity} transform={tabs.transform}>
        <RoundedRect x={x + INSET} y={y + TABS_Y} width={INNER_W} height={TABS_H} r={TABS_H / 2} color="#20222b" />
        <RoundedRect rect={pill} color="#3a3e4b" />
      </Group>
      {/* Tab icons: a dashboard grid, a calendar and a target. */}
      <Group opacity={tabs.opacity} transform={tabs.transform}>
        <Group transform={[{ translateX: x + TAB_FRAMES[0].icon }, { translateY: y + TABS_Y + TABS_H / 2 }]} color={tint(0)}>
          <RoundedRect x={-5.8} y={-5.8} width={5} height={6.6} r={1.3} />
          <RoundedRect x={0.8} y={-5.8} width={5} height={4} r={1.3} />
          <RoundedRect x={-5.8} y={2.4} width={5} height={3.4} r={1.3} />
          <RoundedRect x={0.8} y={-0.2} width={5} height={6} r={1.3} />
        </Group>
        <Group transform={[{ translateX: x + TAB_FRAMES[1].icon }, { translateY: y + TABS_Y + TABS_H / 2 }]} color={tint(1)}>
          <RoundedRect x={-5.4} y={-4.6} width={10.8} height={10} r={2} style="stroke" strokeWidth={1.3} />
          <Path path="M-5.4 -2.6a2 2 0 0 1 2 -2h6.8a2 2 0 0 1 2 2v1.2h-10.8zM-2.6 -6.4v2M2.6 -6.4v2" style="fill" />
          <Path path="M-2.6 -6.4v2M2.6 -6.4v2" style="stroke" strokeWidth={1.3} strokeCap="round" />
        </Group>
        <Group transform={[{ translateX: x + TAB_FRAMES[2].icon }, { translateY: y + TABS_Y + TABS_H / 2 }]} color={tint(2)} style="stroke" strokeWidth={1.3} strokeCap="round">
          <Path path="M5.6 0.6A5.6 5.6 0 1 1 -0.6 -5.6" />
          <Path path="M2.5 0.4A2.5 2.5 0 1 1 -0.4 -2.5" />
          <Path path="M0 0L5 -5M3 -5.6h2.6v2.6" strokeJoin="round" />
        </Group>
      </Group>

      <Group opacity={bar.opacity} transform={bar.transform}>
        {indices.map((index) => (
          <Segment key={index} index={index} {...motion} />
        ))}
      </Group>

      <Group opacity={list.opacity} transform={list.transform}>
        <RoundedRect rect={listShape}>
          <LinearGradient start={vec(0, y + LIST_Y)} end={vec(0, y + LIST_Y + LIST_H)} colors={['#282a36', '#22242e']} />
        </RoundedRect>
        <Group clip={listShape}>
          {indices.map((index) => (
            <Row key={index} index={index} {...motion} />
          ))}
        </Group>
      </Group>
    </Canvas>
  );
}

const AnimatedTextInput = Animated.createAnimatedComponent(TextInput);

/** Text set from the UI thread, so a counting number costs no React render. */
function LiveText({ text, style }: { text: SharedValue<string>; style: TextStyle }) {
  const animatedProps = useAnimatedProps(() => ({ text: text.get(), defaultValue: text.get() }) as TextInputProps);
  return (
    <AnimatedTextInput
      editable={false}
      pointerEvents="none"
      accessible={false}
      underlineColorAndroid="transparent"
      style={[styles.live, style]}
      animatedProps={animatedProps}
    />
  );
}

/** A checklist label: grey once done, white while current, dim while still to come. */
function RowLabel({ index, label, done }: { index: number; label: string; done: SharedValue<number> }) {
  const style = useAnimatedStyle(() => ({
    color: interpolateColor(done.get() - index, [-1, -0.4, 0, 0.6, 1], ['#4d5060', '#4d5060', '#e9eaf0', '#e9eaf0', '#8a8fa2']),
  }));
  return (
    <Animated.Text style={[styles.row, { top: ROW_FIRST + ROW_PITCH * index - 10 }, style]} numberOfLines={1}>
      {label}
    </Animated.Text>
  );
}

/**
 * "Progress & Planning" panel: tabs, a "STEP 3 OF 5 · 40% Complete" row over a segmented bar, and
 * a checklist that keeps the current step on its middle row. Completing a step sweeps its segment
 * full, moves the outlined segment (with its circling light) on, ticks the row and scrolls the list.
 */
export function StepPlannerProgress({
  progress,
  autoPlay = false,
  title = 'Progress & Planning',
  steps = DEFAULT_STEPS,
  tabs = DEFAULT_TABS,
  onTabChange,
  accessibilityLabel = 'Setup progress',
}: Props) {
  const reducedMotion = useReducedMotion();
  const count = steps.length;
  const controlled = progress !== undefined;
  const playing = autoPlay && !controlled && !reducedMotion;
  const rest = Math.min(REST_DONE, count);
  const initial = controlled ? Math.min(1, Math.max(0, progress)) * count : rest;
  const [tab, setTab] = useState(0);
  const [pressedTab, setPressedTab] = useState(-1);
  const [run, setRun] = useState(0);
  // Index of the step being worked on: what "STEP n OF m" and the footer name.
  const [current, setCurrent] = useState(Math.min(Math.floor(initial + 0.001), count - 1));
  const [completeWidth, setCompleteWidth] = useState(62);

  const target = useSharedValue(initial);
  const pillX = useSharedValue(TAB_FRAMES[0].x);
  const pillW = useSharedValue(TAB_FRAMES[0].w);
  const t = useSharedValue(0);
  const time = useSharedValue(0);

  const exitAt = STEP_FIRST + (rest - 1) * STEP_EVERY + STEP_LENGTH + HOLD;
  const loop = exitAt + EXIT + GAP;

  useEffect(() => {
    if (progress === undefined) return;
    // Reanimated drops animations under reduced motion; value changes keep a short tween.
    target.set(
      withTiming(Math.min(1, Math.max(0, progress)) * count, {
        duration: reducedMotion ? 200 : 700,
        easing: EASE_OUT,
        reduceMotion: ReduceMotion.Never,
      }),
    );
  }, [progress, count, reducedMotion, target]);

  useEffect(() => {
    if (!playing) return;
    t.set(0);
    t.set(withRepeat(withTiming(loop, { duration: loop * 1000, easing: Easing.linear }), -1));
    return () => cancelAnimation(t);
  }, [playing, run, loop, t]);

  useFrameCallback((frame) => {
    time.set(frame.timeSinceFirstFrame / 1000);
  }, !reducedMotion);

  const done = useDerivedValue(() => {
    if (controlled) return target.get();
    if (!playing) return rest;
    let d = 0;
    for (let k = 0; k < rest; k++) d += easeInOut(seg(t.get(), STEP_FIRST + k * STEP_EVERY, STEP_FIRST + k * STEP_EVERY + STEP_LENGTH));
    return d;
  });
  const percent = useDerivedValue(() => `${Math.round((done.get() / count) * 100)}%`);
  const scroll = useAnimatedStyle(() => ({ transform: [{ translateY: -scrollOf(done.get(), count) }] }));

  useAnimatedReaction(
    () => Math.min(Math.floor(done.get() + 0.001), count - 1),
    (now, before) => {
      if (now !== before) scheduleOnRN(setCurrent, now);
    },
  );

  const motion: Motion = { done, t, time, playing, exitAt, count };
  const titleStyle = useEntranceStyle(motion, 0);
  const tabsStyle = useEntranceStyle(motion, 1);
  const barStyle = useEntranceStyle(motion, 2);
  const listStyle = useEntranceStyle(motion, 3);
  const footerStyle = useEntranceStyle(motion, 4);

  const name = steps[current] ?? '';
  const shown = Math.min(1, Math.max(0, progress ?? rest / count));

  return (
    <View
      style={styles.card}
      accessible
      accessibilityRole="progressbar"
      accessibilityLabel={accessibilityLabel}
      accessibilityValue={{ min: 0, max: 100, now: Math.round(shown * 100), text: `Step ${current + 1} of ${count}, ${name}` }}
    >
      <View pointerEvents="none" style={styles.surface}>
        <Surface {...motion} tab={tab} pillX={pillX} pillW={pillW} still={reducedMotion} />
      </View>

      <Animated.View pointerEvents="none" style={[styles.part, titleStyle]}>
        <Text style={styles.title}>{title}</Text>
      </Animated.View>

      <Animated.View style={[styles.part, tabsStyle]}>
        {TAB_FRAMES.map((item, index) => (
          <Pressable
            key={index}
            accessibilityRole="tab"
            accessibilityState={{ selected: index === tab }}
            accessibilityLabel={tabs[index]}
            style={[styles.tab, { left: item.x, width: item.w }]}
            onPressIn={() => {
              setPressedTab(index);
              if (process.env.EXPO_OS !== 'web') {
                Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
              }
            }}
            onPressOut={() => setPressedTab(-1)}
            onPress={() => {
              const glide = { duration: reducedMotion ? 150 : 320, easing: EASE_OUT, reduceMotion: ReduceMotion.Never };
              pillX.set(withTiming(item.x, glide));
              pillW.set(withTiming(item.w, glide));
              setTab(index);
              onTabChange?.(index);
              // Choosing a tab also restarts the demo run.
              setRun((n) => n + 1);
            }}
          >
            <Animated.View
              style={[
                styles.tabPress,
                {
                  transform: [{ scale: pressedTab === index ? 0.97 : 1 }],
                  transitionProperty: 'transform',
                  transitionDuration: 130,
                  transitionTimingFunction: CSS_EASE_OUT,
                },
              ]}
            >
              <Text style={[styles.tabLabel, { left: item.text - item.x }, index === tab && styles.tabSelected]} numberOfLines={1}>
                {tabs[index]}
              </Text>
            </Animated.View>
          </Pressable>
        ))}
      </Animated.View>

      <Animated.View pointerEvents="none" style={[styles.part, barStyle]}>
        <Text style={styles.step}>
          STEP {current + 1} OF {count}
        </Text>
        <LiveText text={percent} style={{ ...styles.percent, right: INSET + completeWidth + 4 }} />
        <Text style={styles.complete} onLayout={(e) => setCompleteWidth(e.nativeEvent.layout.width)}>
          Complete
        </Text>
      </Animated.View>

      <Animated.View pointerEvents="none" style={[styles.part, listStyle]}>
        <View style={styles.list}>
          <Animated.View style={scroll}>
            {steps.map((label, index) => (
              <RowLabel key={index} index={index} label={label} done={done} />
            ))}
          </Animated.View>
        </View>
      </Animated.View>

      <Animated.View pointerEvents="none" style={[styles.part, styles.footer, footerStyle]}>
        <Text style={styles.footerName} numberOfLines={1}>
          {name.charAt(0) + name.slice(1).toLowerCase()}
        </Text>
        <View style={styles.badge}>
          <Text style={styles.badgeLabel}>CURRENT</Text>
        </View>
      </Animated.View>
    </View>
  );
}

const styles = StyleSheet.create({
  card: {
    width: WIDTH,
    height: HEIGHT,
  },
  surface: {
    position: 'absolute',
    left: -BLEED,
    top: -BLEED,
  },
  canvas: {
    width: WIDTH + BLEED * 2,
    height: HEIGHT + BLEED * 2,
  },
  part: {
    ...StyleSheet.absoluteFill,
  },
  live: {
    padding: 0,
  },
  title: {
    position: 'absolute',
    left: 20,
    top: 21.5,
    color: '#dcd8d3',
    fontSize: 17.5,
    lineHeight: 22,
    fontWeight: '600',
  },
  tabPress: {
    ...StyleSheet.absoluteFill,
    justifyContent: 'center',
  },
  tab: {
    position: 'absolute',
    top: PILL_Y,
    height: PILL_H,
    justifyContent: 'center',
  },
  tabLabel: {
    position: 'absolute',
    color: '#c6c9d2',
    fontSize: 14,
    lineHeight: 18,
  },
  tabSelected: {
    color: '#f0f2f6',
    fontWeight: '700',
  },
  step: {
    position: 'absolute',
    left: 21,
    top: STEP_Y - 9,
    color: '#e6e6ea',
    fontSize: 11.5,
    lineHeight: 18,
    fontWeight: '500',
    letterSpacing: 1,
    fontVariant: ['tabular-nums'],
  },
  percent: {
    position: 'absolute',
    top: STEP_Y - 10,
    width: 50,
    height: 20,
    textAlign: 'right',
    color: GREEN,
    fontSize: 13.5,
    fontWeight: '700',
    fontVariant: ['tabular-nums'],
  },
  complete: {
    position: 'absolute',
    right: INSET,
    top: STEP_Y - 9,
    color: '#eceef2',
    fontSize: 13.5,
    lineHeight: 18,
  },
  list: {
    position: 'absolute',
    left: INSET,
    top: LIST_Y,
    width: INNER_W,
    height: LIST_H,
    borderRadius: LIST_R,
    overflow: 'hidden',
  },
  row: {
    position: 'absolute',
    left: ROW_TEXT_X - INSET,
    fontSize: 14,
    lineHeight: 20,
  },
  footer: {
    left: 20,
    top: FOOTER_Y - 11,
    bottom: undefined,
    right: undefined,
    height: 22,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 9,
  },
  footerName: {
    color: '#e0dcd6',
    fontSize: 15.5,
    lineHeight: 20,
    fontWeight: '600',
  },
  badge: {
    height: 18,
    paddingHorizontal: 8,
    borderRadius: 9,
    borderWidth: 1,
    borderColor: '#383a44',
    backgroundColor: '#1f2127',
    justifyContent: 'center',
  },
  badgeLabel: {
    color: '#747884',
    fontSize: 9.5,
    lineHeight: 12,
    fontWeight: '600',
    letterSpacing: 0.6,
  },
});
