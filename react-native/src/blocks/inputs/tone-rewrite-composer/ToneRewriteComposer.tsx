import * as Haptics from 'expo-haptics';
import { LinearGradient } from 'expo-linear-gradient';
import { type ReactNode, useCallback, useEffect, useRef, useState } from 'react';
import { type GestureResponderEvent, type LayoutChangeEvent, Pressable, StyleSheet, Text, TextInput, View } from 'react-native';
import Animated, {
  Easing,
  ReduceMotion,
  cancelAnimation,
  cubicBezier,
  scrollTo,
  useAnimatedReaction,
  useAnimatedRef,
  useAnimatedScrollHandler,
  useAnimatedStyle,
  useReducedMotion,
  useSharedValue,
  withDelay,
  withRepeat,
  withSpring,
  withTiming,
} from 'react-native-reanimated';
import Svg, { Defs, LinearGradient as SvgGradient, Path, Stop } from 'react-native-svg';

export type ToneIcon = 'pencil' | 'check' | 'smile' | 'shrink' | 'heart';

export type Tone = { label: string; icon: ToneIcon };

type Props = {
  /** The instruction field's text when the parent owns it. Leave it out and the sheet keeps its own. */
  value?: string;
  onChangeText?: (text: string) => void;
  /** Picks the last tone by itself in a loop, without the keyboard, until the sheet is touched. */
  autoPlay?: boolean;
  title?: string;
  cancelLabel?: string;
  applyLabel?: string;
  /** The message being rewritten. */
  draft?: string;
  /** What the rewrite comes back as. Change it from `onSend` to show another result. */
  rewritten?: string;
  /** The chips. autoPlay picks the last one. */
  tones?: Tone[];
  placeholder?: string;
  /** Seconds the bubble shimmers before the result is written. */
  thinkTime?: number;
  /** Called with the instruction when a chip is chosen or the field is sent. */
  onSend?: (text: string) => void;
  /** Called with the version on screen. */
  onApply?: (text: string) => void;
  onCancel?: () => void;
};

const WIDTH = 354;
const HEIGHT = 332;
const RADIUS = 30;
// Inset of the chips and of the field from the panel's edge.
const INSET = 14;
const BAR_H = 52;
const STAGE_H = 176;
const BUBBLE_MAX = 258;
const BUBBLE_PAD_X = 15;
const BUBBLE_PAD_Y = 9;
const BUBBLE_R = 19;
const CHIP_H = 32;
const CHIP_GAP = 8;
const FIELD_H = 44;
const FIELD_R = 14;
const SEND = 30;

const BLUE = '#2d7ff9';
const ACTION = '#1a73f0';
const INK = '#1c1e21';

// One pass of the band of light, and of the star's pulse, in milliseconds.
const SHIMMER = 1100;
const BAND = 80;
// How far the band travels in one pass, centred on the bubble.
const SWEEP = 360;
const WRITE_DELAY = 200;
// The rewrite appears this many letters at a time, this often.
const WRITE_STEP = 2;
const WRITE_TICK = 40;
// Share of a finger's travel the bubble follows, and the travel that turns the page.
const PULL_SHARE = 0.4;
const SWIPE_DISTANCE = 40;

const SIZE_SPRING = { mass: 1, stiffness: 240, damping: 21 };
const PULL_SPRING = { mass: 1, stiffness: 300, damping: 24 };
const DOT_SPRING = { mass: 1, stiffness: 380, damping: 20 };

// autoPlay loop, in seconds from its start.
const DRIFT_AT = 0.8;
// The chip row takes this long to reach its end, and this long to come back (milliseconds).
const DRIFT = 1400;
const DRIFT_BACK = 600;
const PRESS_AT = 2.65;
const CHOOSE_AT = 2.85;
const SWIPE_AT = 6.6;
const SWIPE_TIME = 0.25;
// How far the script pulls the bubble before it lets go.
const SWIPE_REACH = 38;
const CANCEL_PRESS_AT = 7.5;
const CANCEL_AT = 7.7;
const LOOP = 9;
// After the sheet is left at rest, the loop waits this long before it starts again.
const RESUME = 3;

const EASE_OUT = Easing.bezier(0, 0, 0.58, 1);
const EASE_IN_OUT = Easing.bezier(0.42, 0, 0.58, 1);
const CSS_EASE_OUT = cubicBezier(0, 0, 0.58, 1);
const CSS_EASE_OUT_STRONG = cubicBezier(0.23, 1, 0.32, 1);

// Outlines, the same strings in the three stacks: 20 pt boxes, and 14 pt boxes for the chips.
const SPARK =
  'M 8.4 3.6 C 8.4 9.06 10.74 11.4 16.2 11.4 C 10.74 11.4 8.4 13.74 8.4 19.2 C 8.4 13.74 6.06 11.4 0.6 11.4 C 6.06 11.4 8.4 9.06 8.4 3.6 Z M 16.4 0.8 C 16.4 3.18 17.42 4.2 19.8 4.2 C 17.42 4.2 16.4 5.22 16.4 7.6 C 16.4 5.22 15.38 4.2 13 4.2 C 15.38 4.2 16.4 3.18 16.4 0.8 Z';
const STAR = 'M 10 1.4 C 10 7.42 12.58 10 18.6 10 C 12.58 10 10 12.58 10 18.6 C 10 12.58 7.42 10 1.4 10 C 7.42 10 10 7.42 10 1.4 Z';
const ARROW = 'M 10 15.2 L 10 5 M 5.8 9.2 L 10 5 L 14.2 9.2';
const AGAIN =
  'M 4.01 8.4 C 4.69 5.88 6.86 4.05 9.46 3.82 C 12.06 3.6 14.52 5.02 15.62 7.38 M 12.95 6.02 L 15.62 7.38 M 16.29 4.46 L 15.62 7.38 M 15.99 11.6 C 15.31 14.12 13.14 15.95 10.54 16.18 C 7.94 16.4 5.48 14.98 4.38 12.62 M 7.05 13.98 L 4.38 12.62 M 3.71 15.54 L 4.38 12.62';
const TONE_ICONS: Record<ToneIcon, string> = {
  pencil: 'M 2.6 11.4 L 2.6 9.2 L 9.5 2.3 L 11.7 4.5 L 4.8 11.4 Z M 8 3.8 L 10.2 6',
  check: 'M 2.4 7.5 L 5.4 10.5 L 11.6 3.7',
  smile:
    'M 12.4 7 C 12.4 9.98 9.98 12.4 7 12.4 C 4.02 12.4 1.6 9.98 1.6 7 C 1.6 4.02 4.02 1.6 7 1.6 C 9.98 1.6 12.4 4.02 12.4 7 Z M 5.2 5.9 L 5.21 5.9 M 8.8 5.9 L 8.81 5.9 M 9.63 8.33 C 9.15 9.35 8.13 10 7 10 C 5.87 10 4.85 9.35 4.37 8.33',
  shrink: 'M 1.2 7 L 5.8 7 M 3.6 4.6 L 6 7 L 3.6 9.4 M 12.8 7 L 8.2 7 M 10.4 4.6 L 8 7 L 10.4 9.4',
  heart:
    'M 7 11.9 C 7 11.9 1.9 8.9 1.9 5.4 C 1.9 3.7 3.2 2.5 4.7 2.5 C 5.7 2.5 6.5 3 7 3.8 C 7.5 3 8.3 2.5 9.3 2.5 C 10.8 2.5 12.1 3.7 12.1 5.4 C 12.1 8.9 7 11.9 7 11.9 Z',
};

const DEFAULT_TONES: Tone[] = [
  { label: 'Rephrase', icon: 'pencil' },
  { label: 'Fix grammar', icon: 'check' },
  { label: 'Make funnier', icon: 'smile' },
  { label: 'Shorter', icon: 'shrink' },
  { label: 'Make supportive', icon: 'heart' },
];

type Phase = 'idle' | 'thinking' | 'done';
type Box = { w: number; h: number };

function Icon({ d, color, size = 20, strokeWidth = 1.5 }: { d: string; color: string; size?: number; strokeWidth?: number }) {
  return (
    <Svg width={size} height={size} viewBox={`0 0 ${size} ${size}`}>
      <Path d={d} stroke={color} strokeWidth={strokeWidth} strokeLinecap="round" strokeLinejoin="round" fill="none" />
    </Svg>
  );
}

type ChipProps = {
  tone: Tone;
  picked: boolean;
  dimmed: boolean;
  held: boolean;
  onPress: () => void;
};

function Chip({ tone, picked, dimmed, held, onPress }: ChipProps) {
  const color = picked ? ACTION : INK;
  return (
    <Pressable onPress={onPress} accessibilityRole="button" accessibilityState={{ selected: picked }}>
      {({ pressed }) => (
        <Animated.View
          style={[
            styles.chip,
            {
              backgroundColor: picked ? '#e1edff' : '#ffffff',
              borderColor: picked ? 'rgba(45,127,249,0.45)' : 'rgba(30,50,110,0.07)',
              // The other chips step back while the rewrite is made.
              opacity: dimmed ? 0.45 : 1,
              // A chip dips under a finger, or under the script's.
              transform: [{ scale: pressed || held ? 0.94 : 1 }],
              transitionProperty: ['backgroundColor', 'borderColor', 'opacity', 'transform'],
              transitionDuration: [200, 200, 200, 130],
              transitionTimingFunction: [CSS_EASE_OUT, CSS_EASE_OUT, CSS_EASE_OUT, CSS_EASE_OUT_STRONG],
            },
          ]}
        >
          <Icon d={TONE_ICONS[tone.icon]} color={color} size={14} strokeWidth={1.4} />
          <Text style={[styles.chipLabel, { color }]} numberOfLines={1}>
            {tone.label}
          </Text>
        </Animated.View>
      )}
    </Pressable>
  );
}

type DotProps = {
  slot: number;
  active: boolean;
  shown: boolean;
  reducedMotion: boolean;
  onPress: () => void;
};

/** One of the dots that tell the versions apart; they pop in once a rewrite exists. */
function Dot({ slot, active, shown, reducedMotion, onPress }: DotProps) {
  const pop = useSharedValue(0);

  useEffect(() => {
    const fade = { duration: 200, easing: EASE_OUT, reduceMotion: ReduceMotion.Never };
    if (shown && !reducedMotion) pop.set(withDelay(150 + slot * 70, withSpring(1, DOT_SPRING)));
    else pop.set(withTiming(shown ? 1 : 0, fade));
  }, [shown, slot, reducedMotion, pop]);

  const style = useAnimatedStyle(() => {
    const t = pop.get();
    return { opacity: Math.min(1, Math.max(0, t)), transform: [{ scale: reducedMotion ? 1 : 0.3 + 0.7 * t }] };
  });

  return (
    <Animated.View style={style}>
      <Pressable onPress={onPress} hitSlop={6}>
        <Animated.View
          style={[
            styles.dot,
            { opacity: active ? 1 : 0.25, transitionProperty: 'opacity', transitionDuration: 200, transitionTimingFunction: CSS_EASE_OUT },
          ]}
        />
      </Pressable>
    </Animated.View>
  );
}

/**
 * Rewrite sheet for a chat draft. The draft sits in the middle as a blue bubble above a row of
 * tone chips and an instruction field. Choosing a tone (or sending an instruction) dissolves the
 * bubble into a shimmering shape, then the rewrite is written into it and the bubble grows to
 * fit; dots under it tell the versions apart and a swipe on the bubble goes back to the original.
 */
export function ToneRewriteComposer({
  value,
  onChangeText,
  autoPlay = false,
  title = 'Rewrite',
  cancelLabel = 'Cancel',
  applyLabel = 'Apply',
  draft = "It's getting ugly out here",
  rewritten = 'Things are getting tough out here, but we can get through this together.',
  tones = DEFAULT_TONES,
  placeholder = 'Describe your changes',
  thinkTime = 1.25,
  onSend,
  onApply,
  onCancel,
}: Props) {
  const reducedMotion = useReducedMotion();
  const controlled = value != null;
  const auto = autoPlay && !controlled && !reducedMotion;

  const [inner, setInner] = useState('');
  const [focused, setFocused] = useState(false);
  // A touch hands the sheet to the user until it has been left at rest for a while.
  const [taken, setTaken] = useState(false);
  const [phase, setPhase] = useState<Phase>('idle');
  // A rewrite exists next to the original.
  const [hasResult, setHasResult] = useState(false);
  // The version on screen: 0 is the draft, 1 the rewrite.
  const [index, setIndex] = useState(0);
  // Letters of the rewrite written so far, and how many rewrites have come back.
  const [written, setWritten] = useState(0);
  const [answers, setAnswers] = useState(0);
  const [chosen, setChosen] = useState<number | null>(null);
  // The instruction the sheet is working on, or has answered.
  const [instruction, setInstruction] = useState<string | null>(null);
  // Sizes of the two versions' bubbles, measured from their text.
  const [sizes, setSizes] = useState<(Box | null)[]>([null, null]);
  // The script's finger.
  const [heldChip, setHeldChip] = useState<number | null>(null);
  const [heldCancel, setHeldCancel] = useState(false);
  // The chip row has been scrolled to its end by the script.
  const [drifted, setDrifted] = useState(false);
  const [chipsWidth, setChipsWidth] = useState(0);

  const text = value ?? inner;
  // The script is running.
  const playing = auto && !taken;
  const thinking = phase === 'thinking';
  // The field holds the instruction being worked on: its button turns into "again".
  const answered = phase !== 'idle' && instruction === text;
  const idle = auto && taken && !focused && text.length === 0 && phase === 'idle';
  const last = tones.length - 1;
  const lastLabel = tones[last]?.label ?? '';

  const bubbleW = useSharedValue(0);
  const bubbleH = useSharedValue(0);
  const pull = useSharedValue(0);
  const beat = useSharedValue(0);
  const spin = useSharedValue(0);
  const scrollX = useSharedValue(0);
  const driveX = useSharedValue(0);
  const driving = useSharedValue(false);
  const chipsRef = useAnimatedRef<Animated.ScrollView>();
  const touch = useRef({ x: 0, moved: false });

  /** Back to the draft alone, with an empty field. */
  const reset = useCallback(() => {
    setPhase('idle');
    setHasResult(false);
    setIndex(0);
    setChosen(null);
    setInstruction(null);
    setDrifted(false);
    setInner('');
  }, []);

  /** The bubble dissolves, and the result arrives `thinkTime` later. */
  const begin = useCallback((request: string, slot: number | null) => {
    setChosen(slot);
    setInstruction(request);
    setPhase('thinking');
  }, []);

  // The bubble takes the size of the version it shows.
  const target = sizes[index];
  useEffect(() => {
    if (!target) return;
    if (bubbleW.get() === 0) {
      bubbleW.set(target.w);
      bubbleH.set(target.h);
    } else if (reducedMotion) {
      const quick = { duration: 200, easing: EASE_OUT, reduceMotion: ReduceMotion.Never };
      bubbleW.set(withTiming(target.w, quick));
      bubbleH.set(withTiming(target.h, quick));
    } else {
      bubbleW.set(withSpring(target.w, SIZE_SPRING));
      bubbleH.set(withSpring(target.h, SIZE_SPRING));
    }
  }, [target, reducedMotion, bubbleW, bubbleH]);

  // While the rewrite is made a band of light crosses the bubble and the field's arrows turn twice.
  useEffect(() => {
    if (!thinking) return;
    const always = { reduceMotion: ReduceMotion.Never };
    beat.set(0);
    beat.set(withRepeat(withTiming(1, { duration: SHIMMER, easing: Easing.linear, ...always }), -1));
    spin.set(withTiming(spin.get() + 720, { duration: thinkTime * 1000, easing: EASE_IN_OUT, ...always }));
    const done = setTimeout(() => {
      setWritten(0);
      setHasResult(true);
      setIndex(1);
      setPhase('done');
      setAnswers((count) => count + 1);
    }, thinkTime * 1000);
    return () => {
      clearTimeout(done);
      cancelAnimation(beat);
    };
  }, [thinking, thinkTime, beat, spin]);

  // The result is written a few letters at a time, once the bubble has opened.
  const letters = rewritten.length;
  useEffect(() => {
    if (answers === 0) return;
    let count = 0;
    let tick: ReturnType<typeof setInterval> | undefined;
    const wait = setTimeout(() => {
      tick = setInterval(() => {
        count += WRITE_STEP;
        setWritten(count);
        if (count >= letters) clearInterval(tick);
      }, WRITE_TICK);
    }, WRITE_DELAY - WRITE_TICK);
    return () => {
      clearTimeout(wait);
      clearInterval(tick);
    };
  }, [answers, letters]);

  // The script moves the chip row by hand: a timed scroll to its end, and back.
  const maxScroll = Math.max(0, chipsWidth - WIDTH);
  useEffect(() => {
    const end = drifted ? maxScroll : 0;
    if (maxScroll === 0 || scrollX.get() === end) return;
    driveX.set(scrollX.get());
    driving.set(true);
    driveX.set(
      withTiming(
        end,
        { duration: drifted ? DRIFT : DRIFT_BACK, easing: EASE_IN_OUT, reduceMotion: ReduceMotion.Never },
        () => driving.set(false),
      ),
    );
  }, [drifted, maxScroll, driveX, driving, scrollX]);

  useAnimatedReaction(
    () => driveX.get(),
    (x) => {
      if (driving.get()) scrollTo(chipsRef, x, 0, false);
    },
  );

  const onScroll = useAnimatedScrollHandler((event) => {
    scrollX.set(event.contentOffset.x);
  });

  // One loop after another, every step at its own moment after the loop began.
  useEffect(() => {
    if (!playing) return;
    let timers: ReturnType<typeof setTimeout>[] = [];
    const run = (start: number) => {
      const at = (seconds: number, step: () => void) => {
        timers.push(setTimeout(step, Math.max(0, start + seconds * 1000 - performance.now())));
      };
      timers = [];
      at(DRIFT_AT, () => setDrifted(true));
      at(PRESS_AT, () => setHeldChip(last));
      at(CHOOSE_AT, () => {
        setHeldChip(null);
        setInner(lastLabel);
        begin(lastLabel, last);
      });
      at(SWIPE_AT, () => {
        pull.set(withTiming(SWIPE_REACH, { duration: SWIPE_TIME * 1000, easing: EASE_OUT }));
      });
      at(SWIPE_AT + SWIPE_TIME, () => {
        setIndex(0);
        pull.set(withSpring(0, PULL_SPRING));
      });
      at(CANCEL_PRESS_AT, () => setHeldCancel(true));
      at(CANCEL_AT, () => {
        setHeldCancel(false);
        reset();
      });
      at(LOOP, () => run(start + LOOP * 1000));
    };
    run(performance.now());
    return () => timers.forEach(clearTimeout);
  }, [playing, last, lastLabel, begin, reset, pull]);

  // The loop starts over a few seconds after the sheet is left at rest.
  useEffect(() => {
    if (!idle) return;
    const wait = setTimeout(() => setTaken(false), RESUME * 1000);
    return () => clearTimeout(wait);
  }, [idle]);

  const tick = () => {
    if (process.env.EXPO_OS !== 'web') Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
  };

  /** Any touch takes the sheet away from the script. */
  const interrupt = () => {
    if (!playing) return;
    setTaken(true);
    setHeldChip(null);
    setHeldCancel(false);
  };

  const write = (next: string) => {
    if (!controlled) setInner(next);
    onChangeText?.(next);
  };

  const rewrite = (raw: string, slot: number | null) => {
    const request = raw.trim();
    if (request.length === 0 || thinking) return;
    tick();
    onSend?.(request);
    begin(request, slot ?? (chosen != null && tones[chosen]?.label === request ? chosen : null));
  };

  const choose = (slot: number) => {
    interrupt();
    write(tones[slot].label);
    rewrite(tones[slot].label, slot);
  };

  const show = (slot: number) => {
    if (!hasResult || thinking || slot === index || slot < 0 || slot > 1) return;
    setIndex(slot);
    if (process.env.EXPO_OS !== 'web') Haptics.selectionAsync().catch(() => {});
  };

  const cancel = () => {
    interrupt();
    onCancel?.();
    reset();
    if (text.length > 0) onChangeText?.('');
  };

  const apply = () => {
    interrupt();
    tick();
    onApply?.(index === 1 ? rewritten : draft);
  };

  /** A touch on the field ends the script: the sheet goes back to rest for the keyboard. */
  const onFocus = () => {
    setFocused(true);
    if (!playing) return;
    interrupt();
    reset();
  };

  // --- A sideways pull on the bubble turns to the other version. ---
  const onGrant = (e: GestureResponderEvent) => {
    touch.current = { x: e.nativeEvent.pageX, moved: false };
  };

  const onMove = (e: GestureResponderEvent) => {
    const dx = e.nativeEvent.pageX - touch.current.x;
    if (!touch.current.moved && Math.abs(dx) < 8) return;
    touch.current.moved = true;
    interrupt();
    pull.set(dx * PULL_SHARE);
  };

  const onRelease = (e: GestureResponderEvent) => {
    if (!touch.current.moved) return;
    const dx = e.nativeEvent.pageX - touch.current.x;
    if (dx > SWIPE_DISTANCE) show(index - 1);
    else if (dx < -SWIPE_DISTANCE) show(index + 1);
    pull.set(
      reducedMotion ? withTiming(0, { duration: 200, easing: EASE_OUT, reduceMotion: ReduceMotion.Never }) : withSpring(0, PULL_SPRING),
    );
  };

  const measure = (slot: number) => (e: LayoutChangeEvent) => {
    const { width, height } = e.nativeEvent.layout;
    setSizes((old) => {
      const before = old[slot];
      if (before && before.w === width && before.h === height) return old;
      const next = [...old];
      next[slot] = { w: width, h: height };
      return next;
    });
  };

  const pullStyle = useAnimatedStyle(() => ({ transform: [{ translateX: pull.get() }] }));
  const backStyle = useAnimatedStyle(() => ({ width: bubbleW.get(), height: bubbleH.get() }));
  const bandStyle = useAnimatedStyle(() => ({
    transform: [{ translateX: reducedMotion ? 0 : (beat.get() - 0.5) * SWEEP }],
  }));
  const starStyle = useAnimatedStyle(() => {
    const t = beat.get();
    return {
      transform: reducedMotion ? [] : [{ rotate: `${90 * t}deg` }, { scale: 1 + 0.18 * Math.sin(t * Math.PI * 2) }],
    };
  });
  const againStyle = useAnimatedStyle(() => ({ transform: [{ rotate: `${reducedMotion ? 0 : spin.get()}deg` }] }));

  const fade = (ms: number) => ({ transitionProperty: 'opacity' as const, transitionDuration: ms, transitionTimingFunction: CSS_EASE_OUT });
  // The glyph going out of the round button shrinks away.
  const swap = (shown: boolean) => ({
    opacity: shown ? 1 : 0,
    transform: [{ scale: shown || reducedMotion ? 1 : 0.5 }],
    transitionProperty: ['opacity', 'transform'] as ('opacity' | 'transform')[],
    transitionDuration: 200,
    transitionTimingFunction: CSS_EASE_OUT_STRONG,
  });

  const version = (slot: number, content: ReactNode) => {
    const shown = slot === index && !thinking;
    return (
      <View style={styles.center} pointerEvents="none">
        <Animated.View
          onLayout={measure(slot)}
          style={[styles.version, { opacity: shown ? 1 : 0, transitionDelay: shown ? 120 : 0, ...fade(180) }]}
        >
          <Text style={styles.bubbleText}>{content}</Text>
        </Animated.View>
      </View>
    );
  };

  return (
    <View style={styles.panel}>
      <LinearGradient colors={['#e9f2ff', '#f3ecfb']} style={[styles.fill, styles.sheet]} />

      <View style={styles.bar}>
        <View style={styles.title}>
          <Svg width={20} height={20} viewBox="0 0 20 20">
            <Defs>
              <SvgGradient id="spark" x1="0" y1="0" x2="1" y2="1">
                <Stop offset="0" stopColor={BLUE} />
                <Stop offset="1" stopColor="#a35cff" />
              </SvgGradient>
            </Defs>
            <Path d={SPARK} fill="url(#spark)" />
          </Svg>
          <Text style={styles.titleText} numberOfLines={1}>
            {title}
          </Text>
        </View>
        <Pressable onPress={cancel} hitSlop={8} accessibilityRole="button">
          {({ pressed }) => (
            <Animated.Text style={[styles.cancel, { opacity: pressed || heldCancel ? 0.35 : 1, ...fade(130) }]} numberOfLines={1}>
              {cancelLabel}
            </Animated.Text>
          )}
        </Pressable>
        <Pressable onPress={apply} disabled={!hasResult} hitSlop={8} accessibilityRole="button">
          {({ pressed }) => (
            <Animated.Text
              style={[
                styles.apply,
                {
                  // Pale until there is something to apply.
                  color: hasResult ? ACTION : '#aebbd2',
                  opacity: pressed ? 0.35 : 1,
                  transitionProperty: ['color', 'opacity'],
                  transitionDuration: [250, 130],
                  transitionTimingFunction: CSS_EASE_OUT,
                },
              ]}
              numberOfLines={1}
            >
              {applyLabel}
            </Animated.Text>
          )}
        </Pressable>
      </View>

      <View
        style={styles.stage}
        accessible
        accessibilityLabel={index === 1 ? rewritten : draft}
        onStartShouldSetResponder={() => true}
        onResponderGrant={onGrant}
        onResponderMove={onMove}
        onResponderRelease={onRelease}
        onResponderTerminate={onRelease}
      >
        <Animated.View style={[styles.fill, pullStyle]} pointerEvents="none">
          <View style={styles.center}>
            <Animated.View style={[styles.back, backStyle]}>
              <Animated.View style={[styles.fill, styles.blue, { opacity: thinking ? 0 : 1, ...fade(220) }]} />
              <Animated.View style={[styles.fill, styles.pale, { opacity: thinking ? 1 : 0, ...fade(220) }]}>
                <Animated.View style={[styles.band, bandStyle]}>
                  <LinearGradient
                    colors={['rgba(255,255,255,0)', 'rgba(255,255,255,0.85)', 'rgba(255,255,255,0)']}
                    start={{ x: 0, y: 0.5 }}
                    end={{ x: 1, y: 0.5 }}
                    style={styles.fill}
                  />
                </Animated.View>
                <Animated.View style={starStyle}>
                  <Svg width={20} height={20} viewBox="0 0 20 20">
                    <Path d={STAR} fill="rgba(45,127,249,0.75)" />
                  </Svg>
                </Animated.View>
              </Animated.View>
            </Animated.View>
          </View>
          {version(0, draft)}
          {version(
            1,
            // The letters still to come hold their place, so the lines do not move.
            <>
              {rewritten.slice(0, written)}
              <Text style={styles.unwritten}>{rewritten.slice(written)}</Text>
            </>,
          )}
        </Animated.View>

        <View style={styles.dots} pointerEvents={hasResult ? 'box-none' : 'none'}>
          {[0, 1].map((slot) => (
            <Dot
              key={slot}
              slot={slot}
              active={slot === index}
              shown={hasResult}
              reducedMotion={reducedMotion}
              onPress={() => {
                interrupt();
                show(slot);
              }}
            />
          ))}
        </View>
      </View>

      <Animated.ScrollView
        ref={chipsRef}
        horizontal
        showsHorizontalScrollIndicator={false}
        onScroll={onScroll}
        scrollEventThrottle={16}
        keyboardShouldPersistTaps="handled"
        style={styles.chips}
        contentContainerStyle={styles.chipRow}
        onContentSizeChange={(width) => setChipsWidth(width)}
      >
        {tones.map((tone, slot) => (
          <Chip
            key={tone.label}
            tone={tone}
            picked={chosen === slot}
            dimmed={thinking && chosen !== slot}
            held={heldChip === slot}
            onPress={() => choose(slot)}
          />
        ))}
      </Animated.ScrollView>

      <View style={styles.field}>
        <TextInput
          value={text}
          onChangeText={write}
          onFocus={onFocus}
          onBlur={() => setFocused(false)}
          onSubmitEditing={() => {
            interrupt();
            rewrite(text, null);
          }}
          placeholder={placeholder}
          placeholderTextColor="#8a8f99"
          returnKeyType="send"
          selectionColor={ACTION}
          style={styles.input}
        />
        <Pressable
          onPress={() => {
            interrupt();
            rewrite(text, null);
          }}
          disabled={text.length === 0 || thinking}
          accessibilityRole="button"
          accessibilityLabel={answered ? 'Rewrite again' : 'Rewrite'}
        >
          {({ pressed }) => (
            <Animated.View
              style={[
                styles.send,
                {
                  transform: [{ scale: pressed ? 0.94 : 1 }],
                  transitionProperty: 'transform',
                  transitionDuration: 130,
                  transitionTimingFunction: CSS_EASE_OUT_STRONG,
                },
              ]}
            >
              <Animated.View
                style={[
                  styles.fill,
                  styles.disc,
                  {
                    backgroundColor: text.length === 0 ? '#dbe3f1' : BLUE,
                    opacity: answered ? 0 : 1,
                    transitionProperty: ['backgroundColor', 'opacity'],
                    transitionDuration: [150, 200],
                    transitionTimingFunction: CSS_EASE_OUT,
                  },
                ]}
              />
              <Animated.View style={[styles.glyph, swap(!answered)]}>
                <Icon d={ARROW} color="#ffffff" strokeWidth={1.9} />
              </Animated.View>
              <Animated.View style={[styles.glyph, swap(answered)]}>
                <Animated.View style={againStyle}>
                  <Icon d={AGAIN} color={ACTION} strokeWidth={1.7} />
                </Animated.View>
              </Animated.View>
            </Animated.View>
          )}
        </Pressable>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  panel: {
    width: WIDTH,
    height: HEIGHT,
    borderRadius: RADIUS,
    boxShadow: '0 16px 44px rgba(52,72,130,0.2)',
  },
  fill: {
    position: 'absolute',
    left: 0,
    right: 0,
    top: 0,
    bottom: 0,
  },
  sheet: {
    borderRadius: RADIUS,
  },
  center: {
    position: 'absolute',
    left: 0,
    right: 0,
    top: 0,
    bottom: 0,
    alignItems: 'center',
    justifyContent: 'center',
  },
  bar: {
    height: BAR_H,
    paddingHorizontal: 18,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  title: {
    position: 'absolute',
    left: 0,
    right: 0,
    top: 0,
    bottom: 0,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 6,
  },
  titleText: {
    color: INK,
    fontSize: 15,
    fontWeight: '600',
  },
  cancel: {
    color: ACTION,
    fontSize: 15,
  },
  apply: {
    fontSize: 15,
    fontWeight: '600',
  },
  stage: {
    height: STAGE_H,
  },
  back: {
    borderRadius: BUBBLE_R,
    overflow: 'hidden',
  },
  blue: {
    backgroundColor: BLUE,
  },
  pale: {
    backgroundColor: '#cfdcf7',
    alignItems: 'center',
    justifyContent: 'center',
  },
  band: {
    position: 'absolute',
    top: 0,
    bottom: 0,
    width: BAND,
  },
  version: {
    maxWidth: BUBBLE_MAX,
    paddingHorizontal: BUBBLE_PAD_X,
    paddingVertical: BUBBLE_PAD_Y,
  },
  bubbleText: {
    color: '#ffffff',
    fontSize: 15,
    lineHeight: 20,
  },
  unwritten: {
    color: 'transparent',
  },
  dots: {
    position: 'absolute',
    left: 0,
    right: 0,
    bottom: 16,
    flexDirection: 'row',
    justifyContent: 'center',
    gap: 6,
  },
  dot: {
    width: 6,
    height: 6,
    borderRadius: 3,
    backgroundColor: BLUE,
  },
  chips: {
    flexGrow: 0,
    height: CHIP_H + 2,
  },
  chipRow: {
    paddingHorizontal: INSET,
    alignItems: 'center',
    gap: CHIP_GAP,
  },
  chip: {
    height: CHIP_H,
    // The hairline takes one point of the padding.
    paddingHorizontal: 11,
    borderRadius: CHIP_H / 2,
    borderWidth: 1,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
  },
  chipLabel: {
    fontSize: 13,
    fontWeight: '500',
  },
  field: {
    marginHorizontal: INSET,
    marginTop: 12,
    height: FIELD_H,
    paddingLeft: 14,
    paddingRight: 7,
    borderRadius: FIELD_R,
    backgroundColor: '#ffffff',
    boxShadow: '0 2px 10px rgba(52,72,130,0.08)',
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
  },
  input: {
    flex: 1,
    height: FIELD_H,
    paddingVertical: 0,
    color: INK,
    fontSize: 15,
  },
  send: {
    width: SEND,
    height: SEND,
  },
  disc: {
    borderRadius: SEND / 2,
  },
  glyph: {
    position: 'absolute',
    left: (SEND - 20) / 2,
    top: (SEND - 20) / 2,
  },
});
