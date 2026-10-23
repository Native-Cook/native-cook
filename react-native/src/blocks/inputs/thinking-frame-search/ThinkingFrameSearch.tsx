import { Blur, Canvas, LinearGradient, RoundedRect, rect, rrect, vec } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { LinearGradient as Tile } from 'expo-linear-gradient';
import { useCallback, useEffect, useRef, useState } from 'react';
import { Pressable, StyleSheet, Text, TextInput, View } from 'react-native';
import Animated, {
  Easing,
  type SharedValue,
  cubicBezier,
  useAnimatedStyle,
  useDerivedValue,
  useFrameCallback,
  useReducedMotion,
  useSharedValue,
  withTiming,
} from 'react-native-reanimated';
import Svg, { Path } from 'react-native-svg';

export type ThinkingFrameSearchResult = {
  title: string;
  detail: string;
  /** The two colours of the row's small gradient tile. */
  tint: [string, string];
};

type Props = {
  /** Controlled text; leave it out and the field keeps its own. */
  value?: string;
  onChangeText?: (text: string) => void;
  /** Types `prompt`, submits it, shows the results and starts over, without opening the keyboard. */
  autoPlay?: boolean;
  placeholder?: string;
  thinkingLabel?: string;
  /** The steps named at the right of the band, one after another. */
  statuses?: string[];
  resultsLabel?: string;
  results?: ThinkingFrameSearchResult[];
  /** What autoPlay types. */
  prompt?: string;
  /** Called with the query when the keyboard's search key is pressed. */
  onSend?: (text: string) => void;
  /** Called with the row that was tapped; the panel closes and the field clears. */
  onSelect?: (result: ThinkingFrameSearchResult) => void;
  onVoice?: () => void;
};

const WIDTH = 350;
// The frame's border on the sides and below the field, and the band and border above it.
const BORDER = 5;
const FRAME_TOP = 26;
const BAND_HEIGHT = 22;
const FIELD_WIDTH = WIDTH - 2 * BORDER;
const FIELD_HEIGHT = 48;
const FIELD_RADIUS = 12;
const TEXT_WIDTH = FIELD_WIDTH - 16 - 46;
// Space between the field and the results panel.
const GAP = 8;
const PANEL_RADIUS = 14;
const PANEL_HEAD = 30;
const PANEL_FOOT = 6;
const ROW = 46;
const MAX_ROWS = 3;
// The canvas overflows the component by this much on every side to fit the glow.
const BLEED = 40;

// The frame: the glow fades in, grows with an overshoot and sharpens; later it melts.
const GLOW = 0.3;
const GROW = 0.7;
const SHARPEN = 0.9;
const BLOOM_BLUR = 18;
const MELT_BLUR = 14;
const MELT_DROP = 20;
// The gradient is twice the component's width and moves this many periods a second.
const FLOW_PERIOD = 700;
const FLOW_START = 0.68;
const FLOW_SPEED = 0.12;
const STATUS = 0.25;
// Seconds from the submit to the results.
const THINK = 3.1;

// autoPlay (seconds): type, submit, think, look at the results, clear, rest.
const LOOP = 9.2;
const TYPE_AT = 0.8;
// The typing never takes longer than this, however long the prompt.
const TYPE_SPAN = 1;
const SUBMIT_AT = 2;
const CLEAR_AT = 7.7;
// After a touch the script waits this long, then starts again from rest.
const RESUME_AFTER = 4;
// Seconds between two letters of the typed prompt: uneven, and the same every loop.
const TYPE_GAPS = [0.09, 0.07, 0.11, 0.06, 0.085, 0.07, 0.12, 0.075];

const INK = '#141414';
const MUTE = '#9a9a9a';
const ORANGE = '#f08a1c';

// Heights of the bars of the voice icon.
const WAVE = [5, 11, 15, 9, 4].map((height, index) => `M${3.6 + 3.2 * index} ${10 - height / 2}V${10 + height / 2}`).join('');
// The three dots in front of the label: centre and radius in a 12-point box.
const SPARK = [
  { x: 6, y: 2.6, r: 2 },
  { x: 2.4, y: 8.8, r: 1.7 },
  { x: 9.6, y: 8.8, r: 1.7 },
];

// One period of the frame's colours, amber at both ends so it repeats without a seam; drawn twice.
const FLOW = ['#f5a623', '#f08a1c', '#c0306a', '#6b3fa0', '#c0306a', '#f08a1c', '#f5a623'];
const FLOW_AT = [0, 0.18, 0.42, 0.6, 0.74, 0.9, 1];
const FLOW_COLORS = [...FLOW, ...FLOW];
const FLOW_STOPS = [...FLOW_AT.map((at) => at / 2), ...FLOW_AT.map((at) => 0.5 + at / 2)];

const CSS_EASE_OUT = cubicBezier(0.23, 1, 0.32, 1);
const EASE_IN_OUT = Easing.bezier(0.42, 0, 0.58, 1);
const EASE_OUT = Easing.bezier(0, 0, 0.58, 1);
// A slower back-out for the panel: it lands a few points past its height and settles.
const SOFT_BACK = Easing.bezier(0.3, 1.3, 0.6, 1);

function clamp01(t: number) {
  'worklet';
  return Math.min(1, Math.max(0, t));
}

function smooth(t: number) {
  'worklet';
  const u = clamp01(t);
  return u * u * (3 - 2 * u);
}

/** Ease-out that overshoots its target by a tenth before settling. */
function backOut(t: number) {
  'worklet';
  const u = t - 1;
  return 1 + 2.70158 * u * u * u + 1.70158 * u * u;
}

/** How far into its period the gradient is, `since` seconds after the submit. */
function flowPhase(since: number) {
  'worklet';
  const phase = (FLOW_START - FLOW_SPEED * Math.max(since, 0)) % 1;
  return phase < 0 ? phase + 1 : phase;
}

/** 0…1 for a part of the panel that shows once the panel is `after` of the way open. */
function reveal(open: number, after: number) {
  'worklet';
  return clamp01((open - after) / 0.25);
}

/** Where the gradient frame is in its life: growing out of the field, sharp, or melting away. */
function frameAt(since: number, melt: number, still: boolean) {
  'worklet';
  const sharp = smooth(since / SHARPEN);
  // 0…1 with a small overshoot: how far the frame has grown out of the field.
  const grow = still ? 1 : backOut(clamp01(since / GROW));
  return {
    grow,
    blur: still ? 0 : BLOOM_BLUR * (1 - sharp) + MELT_BLUR * melt,
    opacity: clamp01(since / GLOW) * (1 - melt),
    // Opacity of the texts in the band.
    label: clamp01((sharp - 0.6) / 0.3) * (1 - Math.min(1, melt * 3)),
    // Top edge of the frame, and how far it has sunk towards the results.
    top: FRAME_TOP * (1 - grow),
    drop: still ? 0 : MELT_DROP * melt,
  };
}

/** When each letter of the prompt appears, in seconds from the start of the loop. */
function keyTimes(prompt: string) {
  const at: number[] = [];
  let t = 0;
  for (let index = 0; index < prompt.length; index++) {
    at.push(t);
    t += TYPE_GAPS[index % TYPE_GAPS.length];
  }
  const scale = Math.min(1, TYPE_SPAN / Math.max(t, 0.001));
  return at.map((seconds) => TYPE_AT + seconds * scale);
}

/** The drawn caret stays lit for a moment after a letter, then blinks once a second. */
function caretOpacity(sinceKey: number) {
  'worklet';
  return sinceKey % 1 < 0.55 ? 1 : 0;
}

type Motion = {
  /** Seconds since the submit. */
  since: SharedValue<number>;
  melt: SharedValue<number>;
  panel: SharedValue<number>;
  panelHeight: number;
  still: boolean;
};

/** Everything behind the texts: the halo of the results, the gradient frame, and the white field. */
function Backdrop({ since, melt, panel, panelHeight, still }: Motion) {
  const o = BLEED;
  const fieldX = o + BORDER;
  const fieldY = o + FRAME_TOP;
  const panelY = fieldY + FIELD_HEIGHT + GAP;

  // The gradient is two periods long and slides to the right, purple arriving from the left.
  const flowStart = useDerivedValue(() => vec(o - flowPhase(still ? 0 : since.get()) * FLOW_PERIOD, 0));
  const flowEnd = useDerivedValue(() => vec(o + (2 - flowPhase(still ? 0 : since.get())) * FLOW_PERIOD, 0));

  const shown = useDerivedValue(() => clamp01(panel.get()));
  const haloOpacity = useDerivedValue(() => 0.42 * shown.get());
  const height = useDerivedValue(() => (still ? panelHeight : panelHeight * Math.max(panel.get(), 0)));
  // What is left of the frame: a soft halo of its colours around the results.
  const halo = useDerivedValue(() => rrect(rect(fieldX + 2, panelY + 2, FIELD_WIDTH - 4, Math.max(height.get() - 4, 0)), PANEL_RADIUS, PANEL_RADIUS));
  const shadow = useDerivedValue(() => rrect(rect(fieldX + 10, panelY + 14, FIELD_WIDTH - 20, Math.max(height.get() - 12, 0)), PANEL_RADIUS, PANEL_RADIUS));

  const frame = useDerivedValue(() => frameAt(since.get(), melt.get(), still));
  const frameOpacity = useDerivedValue(() => frame.get().opacity);
  const frameBlur = useDerivedValue(() => frame.get().blur);
  const frameShape = useDerivedValue(() => {
    const { grow, top, drop } = frame.get();
    const radius = FIELD_RADIUS + BORDER * grow;
    return rrect(
      rect(
        o + BORDER * (1 - grow),
        o + top + drop,
        FIELD_WIDTH + 2 * BORDER * grow,
        // A melting frame sinks a little and runs down twice as far, towards the results.
        FIELD_HEIGHT + (FRAME_TOP + BORDER) * grow + 2 * drop,
      ),
      radius,
      radius,
    );
  });

  return (
    <Canvas style={[styles.canvas, { height: FRAME_TOP + FIELD_HEIGHT + GAP + panelHeight + BLEED * 2 }]}>
      <RoundedRect rect={halo} opacity={haloOpacity}>
        <LinearGradient start={flowStart} end={flowEnd} colors={FLOW_COLORS} positions={FLOW_STOPS} />
        <Blur blur={18} />
      </RoundedRect>
      <RoundedRect rect={shadow} color="rgba(0,0,0,0.1)" opacity={shown}>
        <Blur blur={12} />
      </RoundedRect>

      <RoundedRect rect={frameShape} opacity={frameOpacity}>
        <LinearGradient start={flowStart} end={flowEnd} colors={FLOW_COLORS} positions={FLOW_STOPS} />
        <Blur blur={frameBlur} />
      </RoundedRect>

      <RoundedRect x={fieldX + 6} y={fieldY + 8} width={FIELD_WIDTH - 12} height={FIELD_HEIGHT - 8} r={FIELD_RADIUS} color="rgba(0,0,0,0.08)">
        <Blur blur={6} />
      </RoundedRect>
      <RoundedRect x={fieldX} y={fieldY} width={FIELD_WIDTH} height={FIELD_HEIGHT} r={FIELD_RADIUS} color="#ffffff" />
    </Canvas>
  );
}

function Dot({ index, since, still }: { index: number; since: SharedValue<number>; still: boolean }) {
  const { x, y, r } = SPARK[index];
  const pulse = useAnimatedStyle(() => ({ opacity: still ? 1 : 0.6 + 0.4 * Math.sin(since.get() * 6 - index * 1.4) }));
  return <Animated.View style={[styles.dot, { left: x - r, top: y - r, width: r * 2, height: r * 2, borderRadius: r }, pulse]} />;
}

function ResultRow({
  result,
  index,
  panel,
  still,
  onPress,
}: {
  result: ThinkingFrameSearchResult;
  index: number;
  panel: SharedValue<number>;
  still: boolean;
  onPress: () => void;
}) {
  const style = useAnimatedStyle(() => {
    const seen = reveal(panel.get(), 0.3 + 0.18 * index);
    return { opacity: seen, transform: [{ translateY: still ? 0 : -8 * (1 - seen) }] };
  });
  return (
    <Animated.View style={style}>
      <Pressable
        style={({ pressed }) => [styles.row, pressed && styles.rowPressed]}
        onPress={onPress}
        onPressIn={() => {
          if (process.env.EXPO_OS !== 'web') Haptics.selectionAsync().catch(() => {});
        }}
        accessibilityRole="button"
      >
        <Tile colors={result.tint} start={{ x: 0, y: 0 }} end={{ x: 1, y: 1 }} style={styles.tile} />
        <Text style={styles.rowTitle} numberOfLines={1}>
          {result.title}
        </Text>
        <Text style={styles.rowDetail} numberOfLines={1}>
          {result.detail}
        </Text>
      </Pressable>
    </Animated.View>
  );
}

/**
 * Search field that thinks out loud. Submitting a query makes a blurred orange glow bloom behind
 * the field and sharpen into a flowing gradient frame, with "Thinking…" and the current step in a
 * band on top; the frame then melts downwards into a soft panel of results, which unrolls below
 * the field row by row.
 */
export function ThinkingFrameSearch({
  value,
  onChangeText,
  autoPlay = false,
  placeholder = 'Search or ask',
  thinkingLabel = 'Thinking…',
  statuses = ['Reading notes', 'Checking calendar', 'Looking for files'],
  resultsLabel = 'Top results',
  results = [
    { title: 'Standup notes', detail: 'Yesterday, 09:30', tint: ['#6b3fa0', '#c0306a'] },
    { title: 'Design review', detail: 'Yesterday, 14:00', tint: ['#c0306a', '#f08a1c'] },
    { title: 'yesterday.pdf', detail: 'Edited 18:12', tint: ['#f08a1c', '#f5a623'] },
  ],
  prompt = 'Yesterday',
  onSend,
  onSelect,
  onVoice,
}: Props) {
  const reducedMotion = useReducedMotion();
  const controlled = value != null;
  const auto = autoPlay && !controlled && !reducedMotion;

  const [draft, setDraft] = useState('');
  const [phase, setPhase] = useState<'idle' | 'thinking' | 'results'>('idle');
  // Counts the submits, so a second one during the first restarts the steps.
  const [run, setRun] = useState(0);
  const [status, setStatus] = useState(0);
  const [lastStatus, setLastStatus] = useState(-1);
  // False once a touch has taken the field from the autoPlay script.
  const [handsOff, setHandsOff] = useState(true);
  const [touches, setTouches] = useState(0);
  // The query that was just cleared, fading where it stood.
  const [ghost, setGhost] = useState('');
  const focused = useRef(false);

  const playing = auto && handsOff;
  const text = value ?? draft;
  const hasText = text.length > 0;
  const rows = results.slice(0, MAX_ROWS);
  const panelHeight = PANEL_HEAD + ROW * rows.length + PANEL_FOOT;
  const steps = statuses.length;

  // Seconds since the field appeared, and the moments its drawing is timed from.
  const time = useSharedValue(0);
  const thinkAt = useSharedValue(-100);
  const statusAt = useSharedValue(-100);
  const keyAt = useSharedValue(-100);
  // 0…1: the frame letting go of its shape, on the way to the results or back to rest.
  const melt = useSharedValue(1);
  // 0…1: how far the results panel is unrolled.
  const panel = useSharedValue(0);
  const ghostFade = useSharedValue(0);
  const since = useDerivedValue(() => time.get() - thinkAt.get());

  useFrameCallback((frame) => {
    time.set(frame.timeSinceFirstFrame / 1000);
  });

  /** The glow blooms and sharpens into the frame; the results follow by themselves. */
  const submit = useCallback(() => {
    setPhase('thinking');
    setRun((count) => count + 1);
    setLastStatus(-1);
    setStatus(0);
    thinkAt.set(time.get());
    statusAt.set(time.get());
    melt.set(0);
    panel.set(withTiming(0, { duration: 250, easing: EASE_IN_OUT }));
  }, [melt, panel, statusAt, thinkAt, time]);

  /** Lets go of the frame and rolls the panel back up. */
  const dismiss = useCallback(() => {
    setPhase('idle');
    melt.set(withTiming(1, { duration: 300, easing: EASE_IN_OUT }));
    panel.set(withTiming(0, { duration: 350, easing: EASE_IN_OUT }));
  }, [melt, panel]);

  /** Back to the empty field: `gone` is the query that fades where it stood. */
  const clear = useCallback(
    (gone: string) => {
      dismiss();
      setGhost(gone);
      ghostFade.set(1);
      ghostFade.set(withTiming(0, { duration: 200, easing: EASE_OUT }));
      setDraft('');
    },
    [dismiss, ghostFade],
  );

  useEffect(() => {
    if (phase !== 'thinking') return;
    const timers: ReturnType<typeof setTimeout>[] = [];
    for (let index = 1; index < steps; index++) {
      timers.push(
        setTimeout(
          () => {
            setLastStatus(index - 1);
            setStatus(index);
            statusAt.set(time.get());
          },
          ((THINK * index) / steps) * 1000,
        ),
      );
    }
    timers.push(
      setTimeout(() => {
        setPhase('results');
        melt.set(withTiming(1, { duration: 500, easing: EASE_IN_OUT }));
        panel.set(withTiming(1, { duration: 700, easing: SOFT_BACK }));
      }, THINK * 1000),
    );
    return () => timers.forEach(clearTimeout);
  }, [phase, run, steps, melt, panel, statusAt, time]);

  useEffect(() => {
    if (!playing) return;
    const keys = keyTimes(prompt);
    const timers: ReturnType<typeof setTimeout>[] = [];
    const epoch = Date.now();
    const loop = (lap: number) => {
      const at = (seconds: number, step: () => void) => {
        timers.push(setTimeout(step, Math.max(0, epoch + (lap * LOOP + seconds) * 1000 - Date.now())));
      };
      keys.forEach((seconds, index) =>
        at(seconds, () => {
          setDraft(prompt.slice(0, index + 1));
          keyAt.set(time.get());
        }),
      );
      at(SUBMIT_AT, submit);
      at(CLEAR_AT, () => clear(prompt));
      at(LOOP, () => {
        timers.length = 0;
        loop(lap + 1);
      });
    };
    loop(0);
    return () => timers.forEach(clearTimeout);
  }, [playing, prompt, clear, submit, keyAt, time]);

  // Any touch stops the script; it comes back a few seconds after the last one, or after results
  // that nobody touches.
  useEffect(() => {
    if (!auto || handsOff || phase === 'thinking') return;
    const timer = setTimeout(() => {
      if (focused.current) return;
      clear(text);
      setHandsOff(true);
    }, RESUME_AFTER * 1000);
    return () => clearTimeout(timer);
  }, [auto, handsOff, phase, touches, text, clear]);

  const touch = () => {
    setHandsOff(false);
    setTouches((count) => count + 1);
  };

  const edit = (next: string) => {
    if (!controlled) setDraft(next);
    onChangeText?.(next);
  };

  const send = () => {
    if (!hasText) return;
    touch();
    submit();
    onSend?.(text);
  };

  const bandStyle = useAnimatedStyle(() => {
    const frame = frameAt(since.get(), melt.get(), reducedMotion);
    return { opacity: frame.label, transform: [{ translateY: frame.top + frame.drop + 2 }] };
  });
  const slide = reducedMotion ? 0 : 4;
  // The step that just ended leaves upwards while the next one arrives from below.
  const statusOut = useAnimatedStyle(() => {
    const swap = clamp01((time.get() - statusAt.get()) / STATUS);
    return { opacity: 1 - swap, transform: [{ translateY: -slide * swap }] };
  });
  const statusIn = useAnimatedStyle(() => {
    const swap = clamp01((time.get() - statusAt.get()) / STATUS);
    return { opacity: swap, transform: [{ translateY: slide * (1 - swap) }] };
  });
  // The panel unrolls from its top edge, so the rows are uncovered one after another.
  const panelStyle = useAnimatedStyle(() => ({
    height: reducedMotion ? panelHeight : panelHeight * Math.max(panel.get(), 0),
    opacity: Math.min(1, clamp01(panel.get()) * 3),
  }));
  const headStyle = useAnimatedStyle(() => ({ opacity: reveal(panel.get(), 0.15) }));
  const ghostStyle = useAnimatedStyle(() => ({ opacity: ghostFade.get() }));
  const caretStyle = useAnimatedStyle(() => ({ opacity: caretOpacity(time.get() - keyAt.get()) }));
  // It makes way at once, and comes back after the cleared query has faded.
  const placeholderStyle = {
    opacity: hasText ? 0 : 1,
    transitionProperty: 'opacity' as const,
    transitionDuration: hasText ? 0 : 180,
    transitionDelay: hasText ? 0 : 150,
    transitionTimingFunction: 'ease-out' as const,
  };

  return (
    <View style={[styles.root, { height: FRAME_TOP + FIELD_HEIGHT + GAP + panelHeight }]}>
      <View pointerEvents="none" style={styles.surface}>
        <Backdrop since={since} melt={melt} panel={panel} panelHeight={panelHeight} still={reducedMotion} />
      </View>

      {/* The band on top of the frame: the pulsing dots and the label at the left, the step at the right. */}
      <Animated.View pointerEvents="none" style={[styles.band, bandStyle]}>
        <View style={styles.spark}>
          {SPARK.map((_, index) => (
            <Dot key={index} index={index} since={since} still={reducedMotion} />
          ))}
        </View>
        <Text style={styles.thinking} numberOfLines={1}>
          {thinkingLabel}
        </Text>
        <View style={styles.statusSlot}>
          <Animated.Text style={[styles.status, statusOut]} numberOfLines={1}>
            {statuses[lastStatus] ?? ''}
          </Animated.Text>
          <Animated.Text style={[styles.status, statusIn]} numberOfLines={1}>
            {statuses[status] ?? ''}
          </Animated.Text>
        </View>
      </Animated.View>

      <Animated.View style={[styles.panel, panelStyle]} pointerEvents={phase === 'results' ? 'auto' : 'none'}>
        <View style={{ height: panelHeight }}>
          <Animated.Text style={[styles.head, headStyle]} numberOfLines={1}>
            {resultsLabel}
          </Animated.Text>
          {rows.map((result, index) => (
            <ResultRow
              key={index}
              result={result}
              index={index}
              panel={panel}
              still={reducedMotion}
              onPress={() => {
                touch();
                clear(text);
                if (controlled) onChangeText?.('');
                onSelect?.(result);
              }}
            />
          ))}
        </View>
      </Animated.View>

      <Animated.Text pointerEvents="none" style={[styles.ghost, ghostStyle]} numberOfLines={1}>
        {ghost}
      </Animated.Text>

      <View style={styles.field}>
        <View style={styles.input}>
          <Animated.Text style={[styles.placeholder, placeholderStyle]} numberOfLines={1}>
            {placeholder}
          </Animated.Text>
          <TextInput
            style={styles.text}
            value={text}
            onChangeText={(next) => {
              touch();
              edit(next);
            }}
            onFocus={() => {
              // A tap on the field takes it over from the script and starts from empty; a query
              // typed by hand stays, and only its results go away.
              if (playing) clear(text);
              else if (phase !== 'idle') dismiss();
              focused.current = true;
              touch();
            }}
            onBlur={() => {
              focused.current = false;
              touch();
            }}
            onSubmitEditing={send}
            returnKeyType="search"
            submitBehavior="blurAndSubmit"
            selectionColor={ORANGE}
            cursorColor={ORANGE}
            underlineColorAndroid="transparent"
            accessibilityLabel={placeholder}
          />
          {playing && hasText && phase === 'idle' && (
            // The script does not focus the field, so until it submits it draws the caret itself:
            // an invisible copy of the text pushes it to the end of the line.
            <View pointerEvents="none" style={styles.caretRow}>
              <Text style={styles.sizer} numberOfLines={1}>
                {text}
              </Text>
              <Animated.View style={[styles.caret, caretStyle]} />
            </View>
          )}
        </View>
        <Pressable
          style={styles.voice}
          onPress={onVoice}
          onPressIn={() => {
            if (process.env.EXPO_OS !== 'web') Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
          }}
          accessibilityRole="button"
          accessibilityLabel="Voice search"
        >
          {({ pressed }) => (
            <Animated.View
              style={{
                transform: [{ scale: pressed ? 0.9 : 1 }],
                transitionProperty: 'transform',
                transitionDuration: 130,
                transitionTimingFunction: CSS_EASE_OUT,
              }}
            >
              <Svg width={20} height={20} viewBox="0 0 20 20">
                <Path d={WAVE} stroke={MUTE} strokeWidth={1.8} strokeLinecap="round" />
              </Svg>
            </Animated.View>
          )}
        </Pressable>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  root: {
    width: WIDTH,
  },
  surface: {
    position: 'absolute',
    left: -BLEED,
    top: -BLEED,
  },
  canvas: {
    width: WIDTH + BLEED * 2,
  },
  band: {
    position: 'absolute',
    left: 0,
    top: 0,
    width: WIDTH,
    height: BAND_HEIGHT,
    paddingHorizontal: 14,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
  },
  spark: {
    width: 12,
    height: 12,
  },
  dot: {
    position: 'absolute',
    backgroundColor: '#ffffff',
  },
  thinking: {
    color: '#ffffff',
    fontSize: 12,
    lineHeight: 16,
    fontWeight: '600',
  },
  statusSlot: {
    flex: 1,
    height: 16,
    marginLeft: 2,
  },
  status: {
    position: 'absolute',
    right: 0,
    top: 0,
    color: 'rgba(255,255,255,0.78)',
    fontSize: 12,
    lineHeight: 16,
    fontWeight: '500',
  },
  panel: {
    position: 'absolute',
    left: BORDER,
    top: FRAME_TOP + FIELD_HEIGHT + GAP,
    width: FIELD_WIDTH,
    borderRadius: PANEL_RADIUS,
    backgroundColor: '#ffffff',
    overflow: 'hidden',
  },
  head: {
    height: PANEL_HEAD,
    paddingLeft: 14,
    paddingTop: 10,
    color: MUTE,
    fontSize: 12,
    lineHeight: 16,
    fontWeight: '500',
  },
  row: {
    height: ROW,
    paddingLeft: 12,
    paddingRight: 14,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 10,
  },
  rowPressed: {
    backgroundColor: '#f2f2f2',
  },
  tile: {
    width: 30,
    height: 30,
    borderRadius: 8,
  },
  rowTitle: {
    flex: 1,
    color: INK,
    fontSize: 15,
    lineHeight: 20,
    fontWeight: '500',
  },
  rowDetail: {
    color: MUTE,
    fontSize: 13,
    lineHeight: 18,
  },
  ghost: {
    position: 'absolute',
    left: BORDER + 16,
    top: FRAME_TOP + (FIELD_HEIGHT - 22) / 2,
    width: TEXT_WIDTH,
    color: INK,
    fontSize: 16,
    lineHeight: 22,
  },
  field: {
    position: 'absolute',
    left: BORDER,
    top: FRAME_TOP,
    width: FIELD_WIDTH,
    height: FIELD_HEIGHT,
    paddingLeft: 16,
    paddingRight: 6,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 4,
  },
  input: {
    flex: 1,
    height: FIELD_HEIGHT,
    justifyContent: 'center',
    overflow: 'hidden',
  },
  placeholder: {
    position: 'absolute',
    left: 0,
    color: MUTE,
    fontSize: 16,
    lineHeight: 22,
  },
  text: {
    height: FIELD_HEIGHT,
    padding: 0,
    color: INK,
    fontSize: 16,
  },
  caretRow: {
    ...StyleSheet.absoluteFill,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 1,
  },
  sizer: {
    opacity: 0,
    fontSize: 16,
    lineHeight: 22,
  },
  caret: {
    width: 2,
    height: 19,
    borderRadius: 1,
    backgroundColor: ORANGE,
  },
  voice: {
    width: 36,
    height: 36,
    alignItems: 'center',
    justifyContent: 'center',
  },
});
