import { BlurMask, Canvas, Circle, Group, Line, LinearGradient, Path, RoundedRect, Skia, vec } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useEffect, useEffectEvent, useMemo, useState } from 'react';
import { type LayoutChangeEvent, StyleSheet, Text, View, useWindowDimensions } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  type SharedValue,
  useAnimatedStyle,
  useDerivedValue,
  useFrameCallback,
  useReducedMotion,
  useSharedValue,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';

import {
  AUTO_TAPS,
  FLASH_IN,
  FLASH_OUT,
  LOOP,
  PRESS_FOR,
  RETURN,
  SPIN,
  type Scene,
  USER_HOLD,
  clamp01,
  fillAt,
  flashAt,
  flexAt,
  settledAt,
  smooth,
  tagPose,
  textPose,
} from './motion';

export type EnergyWaveStep = {
  /** Its lines are broken by hand with "\n", so an emoji never moves a word to the next line. */
  headline: string;
  body?: string;
  /** One emoji a tag: over the first crest, over the second, under the trough. The first step has none. */
  tags?: string[];
};

/** A change of step and what made it: a tap, the loop that plays by itself, or the return to rest. */
type Change = { step: number; how: 'tap' | 'auto' | 'rest' | null };

type Props = {
  /** The three steps: the wave fills with colour, the tags arrive, the tags change. */
  steps?: EnergyWaveStep[];
  footerLabel?: string;
  /** The wave's colours from its crests down to its troughs. */
  waveColors?: string[];
  emptyColor?: string;
  panelColor?: string;
  backgroundColor?: string;
  inkColor?: string;
  muteColor?: string;
  accentColor?: string;
  /** Goes through the steps by itself every LOOP seconds. */
  autoPlay?: boolean;
  onStepChange?: (step: number) => void;
  /** Called when the last step is left. */
  onDone?: () => void;
};

const STEPS: EnergyWaveStep[] = [
  {
    headline: 'Energy in your body\nrises and falls.',
    body: 'Your body runs on a daily rhythm, known as the circadian rhythm.',
  },
  {
    headline: 'This cycle controls when\nyou feel ⚡ energetic or\n🥱 tired.',
    tags: ['⚡', '⚡', '🥱'],
  },
  {
    headline: 'Sync with your rhythm to\nsleep better, feel more\nenergised, and be more\nproductive.',
    body: 'Crest can help you find the ideal time for a 👟 run, 😴 nap or when you should 📵 wind down for sleep.',
    tags: ['👟', '📵', '😴'],
  },
];

const WAVE_COLORS = ['#f5c12e', '#71aaab', '#309bd0'];
const WAVE_STOPS = [0.08, 0.5, 0.88];
const EMPTY = '#c9c9ce';
const PANEL = '#f0eff6';
const BACKGROUND = '#fffcff';
const INK = '#111113';
const MUTE = '#9a9aa0';
const ACCENT = '#3b2f8f';
const FLASH = '#ecebf3';
const SUN = '#f5b301';
const NIGHT = '#4b3fb5';
const SHADOW = 'rgba(53, 45, 110, 0.14)';

// The header is drawn for a screen 393 points wide and scaled to the screen's width.
const DESIGN_WIDTH = 393;
// The panel's lower edge at the sides, and how much deeper it hangs in the middle.
const PANEL_EDGE = 353;
const PANEL_SAG = 10;
const SCENE_HEIGHT = PANEL_EDGE + PANEL_SAG * 2;
// The wave rests on this line, and its turning points are joined by half cosines.
const BASE = 200;
const KNOTS = [
  [67, 200],
  [125, 145],
  [188, 187],
  [251, 158],
  [324, 200],
];
const WAVE_FROM = KNOTS[0][0];
const WAVE_TO = KNOTS[KNOTS.length - 1][0];
// The height of the taller crest: a rise of FLEX points is this share of it.
const CREST = BASE - KNOTS[1][1];
const STROKE = 14;
const SAMPLES = 96;
const DISC = 34;
const DISCS = [34, 357];
const TAG_WIDTH = 64;
const TAG_HEIGHT = 38;
const TAG_RADIUS = 9;
const TAIL = 8;
// A square turned by 45° whose corner sticks out of the tag by TAIL.
const TAIL_SIDE = TAIL * Math.SQRT2;
/** The middle of each tag, and whether its tail points down at the wave. */
const TAGS = [
  { x: 126, y: 95, down: true },
  { x: 269, y: 113, down: true },
  { x: 197, y: 235, down: false },
];
// The leading stretch of the colour is a little lighter, in this many layers of white.
const HEAD = 30;
const HEAD_LAYERS = 5;
const HEAD_LIGHT = 0.05;
// A finger within this band, in the header's own points, takes hold of the colour.
const SCRUB_BAND = [110, 240];

const MARGIN = 30;
const FOOTER_WIDTH = 232;
const FOOTER_HEIGHT = 36;
const FOOTER_GAP = 12;

function waveY(x: number) {
  const last = KNOTS.length - 2;
  let i = 0;
  while (i < last && x > KNOTS[i + 1][0]) i++;
  const [x0, y0] = KNOTS[i];
  const [x1, y1] = KNOTS[i + 1];
  return y0 + ((y1 - y0) * (1 - Math.cos((Math.PI * (x - x0)) / (x1 - x0)))) / 2;
}

const POINTS = Array.from({ length: SAMPLES + 1 }, (_, i) => {
  const x = WAVE_FROM + ((WAVE_TO - WAVE_FROM) * i) / SAMPLES;
  return [x, waveY(x)];
});
// The length of the wave up to each sample: the colour is trimmed by length, a finger moves by x.
const LENGTHS = POINTS.reduce<number[]>((sums, [x, y], i) => {
  sums.push(i === 0 ? 0 : sums[i - 1] + Math.hypot(x - POINTS[i - 1][0], y - POINTS[i - 1][1]));
  return sums;
}, []);
const LENGTH = LENGTHS[SAMPLES];
const WAVE = Skia.Path.MakeFromSVGString(`M${POINTS.map(([x, y]) => `${x} ${y}`).join('L')}`)!;
const PANEL_PATH = Skia.Path.MakeFromSVGString(
  `M0 0H${DESIGN_WIDTH}V${PANEL_EDGE}Q${DESIGN_WIDTH / 2} ${PANEL_EDGE + PANEL_SAG * 2} 0 ${PANEL_EDGE}Z`,
)!;

/** The share of the wave's length that lies left of x. */
function lengthAt(x: number) {
  'worklet';
  const place = clamp01((x - WAVE_FROM) / (WAVE_TO - WAVE_FROM)) * SAMPLES;
  const i = Math.min(SAMPLES - 1, Math.floor(place));
  return (LENGTHS[i] + (LENGTHS[i + 1] - LENGTHS[i]) * (place - i)) / LENGTH;
}

function tick(style: 'select' | 'tap') {
  if (process.env.EXPO_OS === 'web') return;
  if (style === 'select') Haptics.selectionAsync().catch(() => {});
  else Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
}

/** One layer of the lighter head: the last `back` points of the colour. */
function Head({ fill, back, still }: { fill: SharedValue<number>; back: number; still: boolean }) {
  const start = useDerivedValue(() => Math.max(0, fill.get() - back / LENGTH));
  // The head is there while the colour is on its way, and gone once it has arrived.
  const opacity = useDerivedValue(() => (still ? 0 : HEAD_LIGHT * smooth(fill.get(), 0, 0.05) * (1 - smooth(fill.get(), 0.93, 1))));
  return <Path path={WAVE} style="stroke" strokeWidth={STROKE} strokeCap="round" strokeJoin="round" color="#ffffff" start={start} end={fill} opacity={opacity} />;
}

/** A white disc at an end of the wave, with its shadow. */
function Disc({ x }: { x: number }) {
  return (
    <>
      <Circle cx={x} cy={BASE + 2} r={DISC / 2} color={SHADOW}>
        <BlurMask blur={5} style="normal" />
      </Circle>
      <Circle cx={x} cy={BASE} r={DISC / 2} color="#ffffff" />
    </>
  );
}

/** An alarm clock about the point (x, y): two bells, a face with its hands, two feet. */
function Alarm({ x, y }: { x: number; y: number }) {
  return (
    <>
      <Circle cx={x - 4.6} cy={y - 5.2} r={2.3} color={SUN} />
      <Circle cx={x + 4.6} cy={y - 5.2} r={2.3} color={SUN} />
      <Line p1={vec(x - 3.6, y + 5.6)} p2={vec(x - 4.9, y + 7.2)} strokeWidth={1.6} strokeCap="round" color={SUN} />
      <Line p1={vec(x + 3.6, y + 5.6)} p2={vec(x + 4.9, y + 7.2)} strokeWidth={1.6} strokeCap="round" color={SUN} />
      <Circle cx={x} cy={y + 0.6} r={7} color="#ffffff" />
      <Circle cx={x} cy={y + 0.6} r={5.9} color={SUN} />
      <Line p1={vec(x, y + 0.6)} p2={vec(x, y - 2.8)} strokeWidth={1.4} strokeCap="round" color="#ffffff" />
      <Line p1={vec(x, y + 0.6)} p2={vec(x + 2.4, y + 0.6)} strokeWidth={1.4} strokeCap="round" color="#ffffff" />
    </>
  );
}

/** A bed about the point (x, y): two pillows on a mattress with two legs. */
function Bed({ x, y }: { x: number; y: number }) {
  return (
    <>
      <RoundedRect x={x - 6.6} y={y - 5.6} width={5.8} height={3.6} r={1.3} color={NIGHT} />
      <RoundedRect x={x + 0.8} y={y - 5.6} width={5.8} height={3.6} r={1.3} color={NIGHT} />
      <RoundedRect x={x - 8} y={y - 1.2} width={16} height={4.8} r={1.8} color={NIGHT} />
      <RoundedRect x={x - 8} y={y + 2} width={2.2} height={3.6} r={0.6} color={NIGHT} />
      <RoundedRect x={x + 5.8} y={y + 2} width={2.2} height={3.6} r={0.6} color={NIGHT} />
    </>
  );
}

type TagProps = {
  index: number;
  /** Its emoji on the second step and on the third. */
  early: string;
  late: string;
  scene: SharedValue<Scene>;
  clock: SharedValue<number>;
  jumps: SharedValue<number[]>;
  still: boolean;
};

function Tag({ index, early, late, scene, clock, jumps, still }: TagProps) {
  const spot = TAGS[index];
  const pose = useDerivedValue(() => tagPose(index, scene.get(), clock.get(), jumps.get()[index], still));
  const tagStyle = useAnimatedStyle(() => {
    const now = pose.get();
    return {
      opacity: now.opacity,
      // A scale of exactly zero cannot be undone, which some systems complain about.
      transform: [{ translateX: now.x }, { translateY: now.y }, { scaleX: Math.max(now.scaleX, 0.001) }, { scaleY: Math.max(now.scaleY, 0.001) }],
    };
  });
  const earlyStyle = useAnimatedStyle(() => ({ opacity: pose.get().late ? 0 : 1, transform: [{ rotate: `${pose.get().spin}deg` }] }));
  const lateStyle = useAnimatedStyle(() => ({ opacity: pose.get().late ? 1 : 0, transform: [{ rotate: `${pose.get().spin}deg` }] }));
  const bodyTop = spot.down ? 0 : TAIL;

  return (
    <Animated.View
      pointerEvents="none"
      style={[
        styles.tag,
        {
          left: spot.x - TAG_WIDTH / 2,
          top: spot.y - TAG_HEIGHT / 2 - bodyTop,
          // Everything a tag does starts from the tip of its tail.
          transformOrigin: ['50%', spot.down ? '100%' : '0%', 0],
        },
        tagStyle,
      ]}
    >
      <View style={[styles.tagBody, { top: bodyTop }]} />
      <View style={[styles.tail, { top: (spot.down ? TAG_HEIGHT : TAIL) - TAIL_SIDE / 2 }]} />
      <Animated.Text style={[styles.emoji, { top: bodyTop + (TAG_HEIGHT - 22) / 2 }, earlyStyle]}>{early}</Animated.Text>
      <Animated.Text style={[styles.emoji, { top: bodyTop + (TAG_HEIGHT - 22) / 2 }, lateStyle]}>{late}</Animated.Text>
    </Animated.View>
  );
}

type StepTextProps = {
  index: number;
  step: EnergyWaveStep;
  shown: boolean;
  scene: SharedValue<Scene>;
  clock: SharedValue<number>;
  still: boolean;
  ink: string;
  mute: string;
};

function StepText({ index, step, shown, scene, clock, still, ink, mute }: StepTextProps) {
  const style = useAnimatedStyle(() => {
    const pose = textPose(index, scene.get(), clock.get(), still);
    return { opacity: pose.opacity, transform: [{ translateY: pose.y }] };
  });
  return (
    <Animated.View
      pointerEvents="none"
      accessibilityElementsHidden={!shown}
      importantForAccessibility={shown ? 'auto' : 'no-hide-descendants'}
      style={[styles.text, style]}
    >
      {/* The lines are the headline's own, so it shrinks before it would wrap. */}
      <Text style={[styles.headline, { color: ink }]} numberOfLines={step.headline.split('\n').length} adjustsFontSizeToFit minimumFontScale={0.7}>
        {step.headline}
      </Text>
      {step.body ? <Text style={[styles.body, { color: mute }]}>{step.body}</Text> : null}
    </Animated.View>
  );
}

/**
 * A three-step explainer under a wave. The wave is grey, then colour runs along it; a tap brings
 * three emoji tags out of its crests and trough; another changes them. On the first step a finger
 * dragged along the wave moves the colour, and a tapped tag hops.
 */
export function EnergyWaveStory({
  steps = STEPS,
  footerLabel = 'Tap to continue',
  waveColors = WAVE_COLORS,
  emptyColor = EMPTY,
  panelColor = PANEL,
  backgroundColor = BACKGROUND,
  inkColor = INK,
  muteColor = MUTE,
  accentColor = ACCENT,
  autoPlay = false,
  onStepChange,
  onDone,
}: Props) {
  const still = useReducedMotion();
  const insets = useSafeAreaInsets();
  const window = useWindowDimensions();
  // The screen as laid out; the window's size stands in until then.
  const [laidOut, setLaidOut] = useState<{ width: number; height: number } | null>(null);
  const size = laidOut ?? window;
  const scale = size.width / DESIGN_WIDTH;
  // The last change of step, for screen readers and the callbacks; the pictures follow `scene`.
  const [change, setChange] = useState<Change>({ step: 0, how: null });
  const shown = change.step;
  // Changes on every touch, which restarts the autoPlay loop behind a pause.
  const [touches, setTouches] = useState(0);

  // Seconds since the first frame: `now` always, `clock` only while something moves.
  const now = useSharedValue(0);
  const clock = useSharedValue(0);
  // The story opens as it is a moment after the last step was left: grey, with the colour about to set off.
  const scene = useSharedValue<Scene>({ step: 0, from: 2, at: -RETURN, fill: 1 });
  const settled = useSharedValue(settledAt({ step: 0, from: 2, at: -RETURN, fill: 1 }, still));
  // Where a finger left the colour on the first step; negative while the colour runs by itself.
  const scrub = useSharedValue(-1);
  const dragging = useSharedValue(false);
  const press = useSharedValue({ at: -10, until: -10 });
  const jumps = useSharedValue([-10, -10, -10]);

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

  const report = useEffectEvent(({ step, how }: Change) => {
    if (how === null) return;
    if (how === 'tap') tick('select');
    onStepChange?.(step);
    if (step === 0 && how !== 'rest') onDone?.();
  });
  useEffect(() => report(change), [change]);

  const actions = useMemo(() => {
    const touched = () => setTouches((n) => n + 1);
    /** Shows a step. Taps, the loop that plays by itself and the return to rest all come through here. */
    const goTo = (step: number, how: 'tap' | 'auto' | 'rest') => {
      'worklet';
      const t = now.get();
      const old = scene.get();
      const held = scrub.get();
      const next = { step, from: old.step, at: t, fill: old.step === 0 && held >= 0 ? held : fillAt(old, t) };
      scene.set(next);
      scrub.set(-1);
      settled.set(settledAt(next, still));
      scheduleOnRN(setChange, { step, how });
    };
    const advance = (how: 'tap' | 'auto') => {
      'worklet';
      goTo((scene.get().step + 1) % 3, how);
    };
    const rest = () => {
      'worklet';
      goTo(0, 'rest');
    };
    /** Lights the pill behind the footer for a while. */
    const flash = (seconds: number) => {
      'worklet';
      const t = now.get();
      press.set({ at: t, until: t + seconds });
      settled.set(Math.max(settled.get(), t + seconds + FLASH_OUT));
    };
    return { touched, advance, rest, flash };
  }, [now, scene, scrub, settled, press, still]);

  const gesture = useMemo(() => {
    const { touched, advance, flash } = actions;
    const tap = Gesture.Tap()
      .maxDuration(1000)
      .onEnd((event, success) => {
        if (!success) return;
        scheduleOnRN(touched);
        const t = now.get();
        const here = scene.get();
        const x = event.x / scale;
        const y = event.y / scale;
        if (here.step !== 0) {
          for (let i = 0; i < TAGS.length; i++) {
            const pose = tagPose(i, here, t, jumps.get()[i], still);
            if (Math.abs(x - TAGS[i].x - pose.x) <= TAG_WIDTH / 2 + 6 && Math.abs(y - TAGS[i].y - pose.y) <= TAG_HEIGHT / 2 + 8) {
              jumps.set(jumps.get().map((at, index) => (index === i ? t : at)));
              settled.set(Math.max(settled.get(), t + SPIN));
              scheduleOnRN(tick, 'tap');
              return;
            }
          }
        }
        flash(FLASH_IN);
        advance('tap');
      });
    const pan = Gesture.Pan()
      .minDistance(6)
      .onStart((event) => {
        const y = event.y / scale;
        if (scene.get().step !== 0 || y < SCRUB_BAND[0] || y > SCRUB_BAND[1]) return;
        dragging.set(true);
        scheduleOnRN(touched);
      })
      .onChange((event) => {
        if (dragging.get()) scrub.set(lengthAt(event.x / scale));
      })
      .onFinalize(() => {
        dragging.set(false);
      });
    return Gesture.Exclusive(pan, tap);
  }, [actions, now, scene, scrub, dragging, jumps, settled, scale, still]);

  useEffect(() => {
    if (!autoPlay) return;
    const timers: ReturnType<typeof setTimeout>[] = [];
    const after = (ms: number, run: () => void) => timers.push(setTimeout(run, ms));
    const cycle = (origin: number) => {
      for (const at of AUTO_TAPS) {
        after(origin + (at - PRESS_FOR) * 1000 - Date.now(), () => scheduleOnUI(actions.flash, PRESS_FOR));
        after(origin + at * 1000 - Date.now(), () => scheduleOnUI(actions.advance, 'auto'));
      }
      after(origin + LOOP * 1000 - Date.now(), () => cycle(origin + LOOP * 1000));
    };
    if (touches === 0) cycle(Date.now());
    else {
      after(USER_HOLD * 1000, () => {
        // The way back to rest takes RETURN, and the loop starts from rest.
        scheduleOnUI(actions.rest);
        cycle(Date.now() + RETURN * 1000);
      });
    }
    return () => timers.forEach(clearTimeout);
  }, [autoPlay, touches, actions]);

  const fill = useDerivedValue(() => {
    const here = scene.get();
    const held = scrub.get();
    return here.step === 0 && held >= 0 ? held : fillAt(here, clock.get());
  });
  // Without motion the colour fades in over the whole wave.
  const colourEnd = useDerivedValue(() => (still ? 1 : fill.get()));
  const colourOpacity = useDerivedValue(() => (still ? fill.get() : fill.get() > 0 ? 1 : 0));
  const flex = useDerivedValue(() => [{ scaleY: 1 + flexAt(scene.get(), clock.get(), still) / CREST }]);
  const flashStyle = useAnimatedStyle(() => ({ opacity: flashAt(press.get(), clock.get()) }));

  const onLayout = (event: LayoutChangeEvent) => {
    const { width, height } = event.nativeEvent.layout;
    setLaidOut({ width, height });
  };

  const bottom = Math.max(insets.bottom - 2, 14);
  const [, second, third] = steps;

  return (
    <GestureDetector gesture={gesture}>
      <View style={[styles.screen, { backgroundColor }]} onLayout={onLayout}>
        <StatusBar style="dark" />

        <Canvas style={[styles.scene, { width: size.width, height: SCENE_HEIGHT * scale }]}>
          <Group transform={[{ scale }]}>
            <Path path={PANEL_PATH} color={panelColor} />
            {/* The wave swells from the line it rests on. */}
            <Group transform={flex} origin={vec(0, BASE)}>
              <Path path={WAVE} style="stroke" strokeWidth={STROKE} strokeCap="round" strokeJoin="round" color={emptyColor} />
              <Path path={WAVE} style="stroke" strokeWidth={STROKE} strokeCap="round" strokeJoin="round" start={0} end={colourEnd} opacity={colourOpacity}>
                <LinearGradient start={vec(0, BASE - CREST - STROKE / 2)} end={vec(0, BASE + STROKE / 2)} colors={waveColors} positions={waveColors.length === WAVE_STOPS.length ? WAVE_STOPS : undefined} />
              </Path>
              {Array.from({ length: HEAD_LAYERS }, (_, layer) => (
                <Head key={layer} fill={fill} back={(HEAD * (HEAD_LAYERS - layer)) / HEAD_LAYERS} still={still} />
              ))}
            </Group>
            <Disc x={DISCS[0]} />
            <Alarm x={DISCS[0]} y={BASE} />
            <Disc x={DISCS[1]} />
            <Bed x={DISCS[1]} y={BASE} />
          </Group>
        </Canvas>

        <View pointerEvents="none" style={[styles.tags, { transform: [{ scale }] }]} accessibilityElementsHidden importantForAccessibility="no-hide-descendants">
          {TAGS.map((_, index) => (
            <Tag
              key={index}
              index={index}
              early={second.tags?.[index] ?? ''}
              late={third.tags?.[index] ?? ''}
              scene={scene}
              clock={clock}
              jumps={jumps}
              still={still}
            />
          ))}
        </View>

        <View style={[styles.texts, { top: SCENE_HEIGHT * scale, bottom: bottom + FOOTER_HEIGHT + FOOTER_GAP }]}>
          {steps.slice(0, 3).map((step, index) => (
            <StepText key={index} index={index} step={step} shown={shown === index} scene={scene} clock={clock} still={still} ink={inkColor} mute={muteColor} />
          ))}
        </View>

        <View
          style={[styles.footer, { bottom }]}
          accessible
          accessibilityRole="button"
          accessibilityLabel={footerLabel}
          onAccessibilityTap={() => {
            actions.touched();
            scheduleOnUI(actions.advance, 'tap');
          }}
        >
          <Animated.View style={[styles.flash, flashStyle]} />
          <Text style={[styles.footerLabel, { color: accentColor }]} numberOfLines={1}>
            {footerLabel}
          </Text>
        </View>
      </View>
    </GestureDetector>
  );
}

const styles = StyleSheet.create({
  screen: {
    flex: 1,
  },
  scene: {
    position: 'absolute',
    left: 0,
    top: 0,
  },
  tags: {
    position: 'absolute',
    left: 0,
    top: 0,
    width: DESIGN_WIDTH,
    height: SCENE_HEIGHT,
    transformOrigin: 'left top',
  },
  tag: {
    position: 'absolute',
    width: TAG_WIDTH,
    height: TAG_HEIGHT + TAIL,
  },
  tagBody: {
    position: 'absolute',
    left: 0,
    width: TAG_WIDTH,
    height: TAG_HEIGHT,
    borderRadius: TAG_RADIUS,
    borderCurve: 'continuous',
    backgroundColor: '#ffffff',
    boxShadow: [{ offsetX: 0, offsetY: 3, blurRadius: 10, color: SHADOW }],
  },
  tail: {
    position: 'absolute',
    left: (TAG_WIDTH - TAIL_SIDE) / 2,
    width: TAIL_SIDE,
    height: TAIL_SIDE,
    borderRadius: 2,
    backgroundColor: '#ffffff',
    transform: [{ rotate: '45deg' }],
  },
  emoji: {
    position: 'absolute',
    left: 0,
    width: TAG_WIDTH,
    height: 22,
    lineHeight: 22,
    fontSize: 16,
    textAlign: 'center',
    color: '#000000',
    includeFontPadding: false,
  },
  texts: {
    position: 'absolute',
    left: 0,
    right: 0,
  },
  text: {
    position: 'absolute',
    left: MARGIN,
    right: MARGIN,
    top: 0,
    bottom: 0,
    justifyContent: 'center',
  },
  headline: {
    fontSize: 27,
    lineHeight: 37,
    fontWeight: '700',
    letterSpacing: -0.2,
    includeFontPadding: false,
  },
  body: {
    marginTop: 7,
    fontSize: 14,
    lineHeight: 19,
    includeFontPadding: false,
  },
  footer: {
    position: 'absolute',
    alignSelf: 'center',
    width: FOOTER_WIDTH,
    height: FOOTER_HEIGHT,
    alignItems: 'center',
    justifyContent: 'center',
  },
  flash: {
    position: 'absolute',
    left: 0,
    right: 0,
    top: 0,
    bottom: 0,
    borderRadius: FOOTER_HEIGHT / 2,
    backgroundColor: FLASH,
  },
  footerLabel: {
    fontSize: 14,
    lineHeight: 18,
    fontWeight: '600',
    includeFontPadding: false,
  },
});
