import * as Haptics from 'expo-haptics';
import { memo, useEffect, useEffectEvent, useRef, useState } from 'react';
import { type AccessibilityActionEvent, Platform, Pressable, StyleSheet, Text, TextInput, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  Easing,
  ReduceMotion,
  type SharedValue,
  type WithSpringConfig,
  cancelAnimation,
  cubicBezier,
  useAnimatedReaction,
  useAnimatedStyle,
  useFrameCallback,
  useReducedMotion,
  useSharedValue,
  withSpring,
  withTiming,
} from 'react-native-reanimated';
import Svg, { Path } from 'react-native-svg';
import { scheduleOnRN } from 'react-native-worklets';

type Props = {
  /** The field's value when the caller owns it; left undefined, the component keeps its own. */
  value?: string;
  /** Swipes, replies and dismisses by itself in a loop. Never opens the keyboard. */
  autoPlay?: boolean;
  title?: string;
  /** The title after "Branch" is tapped. */
  branchTitle?: string;
  /** The assistant's answer, one swipeable paragraph each. */
  paragraphs?: string[];
  placeholder?: string;
  /** The placeholder while a paragraph is quoted. */
  quotePlaceholder?: string;
  branchLabel?: string;
  /** What autoPlay types as its reply. */
  script?: string;
  onChangeText?: (text: string) => void;
  /** Called with the trimmed message and the index of the quoted paragraph, if any. */
  onSend?: (text: string, quoted: number | null) => void;
  /** Called with the index of the paragraph that was swiped, or null when the quote is dismissed. */
  onQuote?: (index: number | null) => void;
  /** Called with the index of the quoted paragraph when "Branch" is tapped. */
  onBranch?: (index: number) => void;
  onAttach?: () => void;
};

/** What autoPlay is pressing. */
type Press = 'send' | 'dismiss';

const PANEL_WIDTH = 350;
const PANEL_HEIGHT = 412;
const PANEL_RADIUS = 40;
const PAD_X = 22;

const TITLE_TOP = 20;
const TITLE_HEIGHT = 30;
const TITLE_PAD = 12;
const TITLE_GAP = 6;
const LABEL_FONT = 13;
// How far a title travels as it rolls in or out.
const ROLL = 8;
// Side of the branch glyph's box, and the scale it grows from beside the title.
const GLYPH = 14;
const GLYPH_SCALE = 0.5;

const ANSWER_TOP = 68;
const ANSWER_WIDTH = PANEL_WIDTH - PAD_X * 2;
const PARAGRAPH_GAP = 16;
const ANSWER_FONT = 15;
const ANSWER_LINE = 22;
// The serif's own lines are about 18 points tall. The line height spreads the other 4 above and
// below every line; this takes back the half above the first and below the last, so a paragraph
// starts and ends at its letters and the gaps and the bar are measured from there.
const ANSWER_TRIM = 2;

// The finger has to move this far before a swipe starts.
const SLOP = 12;
// The pull approaches this distance but never reaches it.
const PULL_LIMIT = 110;
const RESISTANCE = 0.55;
// Pull at which the reply arrow pops in and a release quotes the paragraph.
const THRESHOLD = 44;
// Where autoPlay's swipe ends.
const SCRIPT_PULL = 56;
const ARROW_SIZE = 28;
// Scale and opacity the arrow reaches just before the threshold.
const ARROW_HINT = 0.55;
const ARROW_FAINT = 0.7;
// The arrow's scale while no paragraph is pulled.
const ARROW_REST = 0.01;
const BAR_WIDTH = 2.5;
const BAR_GAP = 10;
const BAR_INSET = 3;
// How much the other paragraphs fade.
const DIM = 0.68;

const COMPOSER_X = 18;
const COMPOSER_WIDTH = PANEL_WIDTH - COMPOSER_X * 2;
const COMPOSER_TOP = 352;
const COMPOSER_GAP = 6;
const PLUS_SIZE = 38;
const FIELD_HEIGHT = 40;
const FIELD_PAD_X = 14;
const FIELD_FONT = 15;
const SEND_SIZE = 30;
const SEND_SLOT = 40;
const BRANCH_WIDTH = 78;
const BRANCH_SCALE = 0.6;
const FIELD_WIDE = COMPOSER_WIDTH - PLUS_SIZE - COMPOSER_GAP;
const FIELD_NARROW = FIELD_WIDE - BRANCH_WIDTH - COMPOSER_GAP;
const PRESS_SCALE = 0.86;
const CLEAR_RISE = 6;
const CARET_HEIGHT = 18;

const QUOTE_HEIGHT = 36;
const QUOTE_RADIUS = 14;
const QUOTE_TOP = COMPOSER_TOP - 8 - QUOTE_HEIGHT;
// Hidden, the preview sits this far down, behind the composer.
const QUOTE_DROP = 26;
const QUOTE_FLY = 70;
const DISMISS_SLOT = 36;
const DISMISS_SIZE = 22;

const SPRING = { mass: 1, reduceMotion: ReduceMotion.Never };
// The paragraph going back, the preview, the field's width and the Branch pill.
const SETTLE: WithSpringConfig = { ...SPRING, stiffness: 300, damping: 26 };
// The reply arrow popping in past the threshold, with overshoot.
const POP: WithSpringConfig = { ...SPRING, stiffness: 520, damping: 18 };

// The autoPlay loop, in seconds from its start.
const SWIPE_AT = 0.8;
// A scripted swipe: how long the pull takes, when it arms and when the finger lets go.
const DRAG = 0.65;
const ARM_AFTER = 0.3;
const RELEASE_AFTER = 0.95;
const CARET_AT = 2.5;
const TYPE_AT = 2.7;
// The whole reply is typed in this time; a short one is typed no slower than `SLOWEST_KEY` per letter.
const TYPE_SPAN = 1;
const SLOWEST_KEY = 0.075;
const SEND_AT = 4.3;
const SECOND_SWIPE_AT = 5.3;
const DISMISS_AT = 7.3;
const LOOP = 8.8;

const PRESS = 0.12;
const FADE = 0.2;
const ROLL_TIME = 0.3;
const FLY = 0.28;
// After the last touch, autoPlay waits this long.
const RESUME_AFTER = 3;

const JITTER = [0, 0.45, -0.2, 0.6, 0.1, -0.3, 0.75];

const PANEL = '#1e1e1c';
const RAISED = '#282826';
const FIELD = '#2e2e2b';
const SEND_IDLE = '#3a3a37';
const INK = '#eceae4';
const QUOTE = '#c9c7c1';
const MUTE = '#8a8a86';
const CORAL = '#e8743b';

const SERIF = Platform.select({ ios: 'ui-serif', default: 'serif' });

const CSS_PRESS = cubicBezier(0.23, 1, 0.32, 1);
const TIMING = { reduceMotion: ReduceMotion.Never };
// What reduced motion gets in place of a spring.
const EASE_TIMING = { ...TIMING, duration: 200, easing: Easing.bezier(0, 0, 0.58, 1) };
const DRAG_TIMING = { ...TIMING, duration: DRAG * 1000, easing: Easing.bezier(0.2, 0.7, 0.3, 1) };
const ROLL_TIMING = { ...TIMING, duration: ROLL_TIME * 1000, easing: Easing.bezier(0.4, 0, 0.2, 1) };
const FLY_TIMING = { ...TIMING, duration: FLY * 1000, easing: Easing.bezier(0.4, 0, 1, 1) };

// Reply arrow in a 16 × 16 box: a stem that rises and turns right into an arrowhead.
const REPLY = 'M3 12.5L3 10.5Q3 6.5 7 6.5L13 6.5M9.6 3L13.1 6.5L9.6 10';
// Branching arrow in a 14 × 14 box: a stem going down with an arrow leaving it to the right.
const BRANCH = 'M3.5 1.5L3.5 12.5M3.5 4.5Q3.5 8.5 7.5 8.5L12 8.5M9.6 6L12.1 8.5L9.6 11';
// Up arrow in the 30 × 30 send button.
const ARROW = 'M15 21L15 9.5M10 14.25L15 9.25L20 14.25';
// Cross in a 9 × 9 box.
const CROSS = 'M0.8 0.8L8.2 8.2M8.2 0.8L0.8 8.2';

const REPLY_ACTION = 'reply';
const REPLY_ACTIONS = [{ name: REPLY_ACTION, label: 'Reply to this part' }];

/** One moment of the autoPlay loop, `at` seconds after it began. */
type Cue =
  | { at: number; kind: 'swipe' | 'arm' | 'quote'; index: number }
  | { at: number; kind: 'key'; text: string }
  | { at: number; kind: 'press'; button: Press }
  | { at: number; kind: 'caret' | 'send' | 'dismiss' };

/** What a swipe shares between the paragraphs, the preview and the composer, all on the UI thread. */
type Motion = {
  /** How far the swiped paragraph has been pulled, after resistance. Kept after the release. */
  pull: SharedValue<number>;
  /** The paragraph `pull` belongs to. */
  active: SharedValue<number>;
  /** 1 while a finger or the script holds the paragraph; goes back to 0 once it is let go. */
  held: SharedValue<number>;
  /** 0…1: the reply arrow popping to its full size past the threshold. */
  arm: SharedValue<number>;
  /** The pull has passed the threshold: releasing now quotes the paragraph. */
  armed: SharedValue<boolean>;
  /** A finger is swiping a paragraph. */
  swiping: SharedValue<boolean>;
  /** 1 when the swipe began with nothing quoted: only then do the other paragraphs dim with the pull. */
  free: SharedValue<number>;
  /** Index of the quoted paragraph, -1 without one. */
  quotedIndex: SharedValue<number>;
  /** 0…1: a paragraph is quoted. */
  quote: SharedValue<number>;
  /** The preview is flying up into a new branch. */
  busy: SharedValue<boolean>;
};

function clamp01(t: number) {
  'worklet';
  return Math.min(1, Math.max(0, t));
}

/** The paragraph's offset for a finger that has moved `distance` to the right: it follows less and less. */
function resist(distance: number) {
  'worklet';
  const travelled = Math.max(0, distance) * RESISTANCE;
  return (1 - 1 / (travelled / PULL_LIMIT + 1)) * PULL_LIMIT;
}

/** Reduced motion trades the springs for a short ease. */
function settle(to: number, reduced: boolean) {
  'worklet';
  return reduced ? withTiming(to, EASE_TIMING) : withSpring(to, SETTLE);
}

function pop(to: number, reduced: boolean) {
  'worklet';
  return reduced ? withTiming(to, EASE_TIMING) : withSpring(to, POP);
}

/** Solid while keys are landing; after half a second without one it blinks like the system caret. */
function blink(idle: number) {
  'worklet';
  if (idle <= 0.5) return 1;
  const t = (idle - 0.5) % 1;
  if (t < 0.12) return 1 - t / 0.12;
  if (t < 0.5) return 0;
  if (t < 0.62) return (t - 0.5) / 0.12;
  return 1;
}

/**
 * Seconds after typing starts at which each letter lands: uneven like a person's typing (a beat
 * after a space, a pause after punctuation) but the same every time.
 */
function rhythm(letters: string[]) {
  const weights = letters.map((_, index) => {
    if (index === 0) return 0;
    const previous = letters[index - 1];
    let weight = 1 + JITTER[index % JITTER.length];
    if (previous === ' ') weight += 0.6;
    if (',.?!'.includes(previous)) weight += 3.2;
    return weight;
  });
  const total = weights.reduce((sum, weight) => sum + weight, 0);
  const unit = Math.min(TYPE_SPAN / Math.max(total, 1), SLOWEST_KEY);
  let at = 0;
  return weights.map((weight) => {
    at += weight * unit;
    return at;
  });
}

/** One scripted swipe: the pull grows as if a finger dragged it, arms on the way, holds, and lets go. */
function swipeCues(at: number, index: number, reduced: boolean): Cue[] {
  const release: Cue = { at: at + RELEASE_AFTER, kind: 'quote', index };
  if (reduced) return [release];
  return [{ at, kind: 'swipe', index }, { at: at + ARM_AFTER, kind: 'arm', index }, release];
}

/** The autoPlay loop: swipe the second paragraph, type a reply, send it, swipe the last one, dismiss it. */
function timeline(script: string, last: number, reduced: boolean): Cue[] {
  const letters = Array.from(script);
  return [
    ...swipeCues(SWIPE_AT, 1, reduced),
    { at: CARET_AT, kind: 'caret' },
    ...rhythm(letters).map((at, index): Cue => ({ at: TYPE_AT + at, kind: 'key', text: letters.slice(0, index + 1).join('') })),
    { at: SEND_AT - PRESS, kind: 'press', button: 'send' },
    { at: SEND_AT, kind: 'send' },
    ...swipeCues(SECOND_SWIPE_AT, last, reduced),
    { at: DISMISS_AT - PRESS, kind: 'press', button: 'dismiss' },
    { at: DISMISS_AT, kind: 'dismiss' },
  ];
}

function impact(style: Haptics.ImpactFeedbackStyle) {
  if (process.env.EXPO_OS !== 'web') Haptics.impactAsync(style).catch(() => {});
}

/** The small press every button here gives under a finger. */
function press(down: boolean) {
  return {
    transform: [{ scale: down ? PRESS_SCALE : 1 }],
    transitionProperty: 'transform' as const,
    transitionDuration: 130,
    transitionTimingFunction: CSS_PRESS,
  };
}

/** The same dip when autoPlay presses a button. */
function dip(down: boolean) {
  return {
    transform: [{ scale: down ? PRESS_SCALE : 1 }],
    transitionProperty: 'transform' as const,
    transitionDuration: PRESS * 1000,
    transitionTimingFunction: 'ease-out' as const,
  };
}

function fade(shown: boolean) {
  return {
    opacity: shown ? 1 : 0,
    transitionProperty: 'opacity' as const,
    transitionDuration: FADE * 1000,
    transitionTimingFunction: 'ease-out' as const,
  };
}

function Glyph({ d, size, color, width }: { d: string; size: number; color: string; width: number }) {
  return (
    <Svg width={size} height={size} viewBox={`0 0 ${size} ${size}`}>
      <Path d={d} fill="none" stroke={color} strokeWidth={width} strokeLinecap="round" strokeLinejoin="round" />
    </Svg>
  );
}

/** The chip at the top: its text rolls to the new title and a branch glyph grows in beside it. */
function TitleChip({ label, branched, reducedMotion }: { label: string; branched: boolean; reducedMotion: boolean }) {
  // Two text layers take turns: the next title is set on the one out of sight, then they trade places.
  const [layers, setLayers] = useState({ texts: [label, ''], front: 0, changes: 0 });
  if (layers.texts[layers.front] !== label) {
    const front = 1 - layers.front;
    const texts = [...layers.texts];
    texts[front] = label;
    setLayers({ texts, front, changes: layers.changes + 1 });
  }
  // Widths of the titles shown so far, as their own layers report them.
  const [widths, setWidths] = useState<Record<string, number>>({});

  // A layer's place: 1 below and unseen, 0 at rest, -1 above and unseen.
  const first = useSharedValue(0);
  const second = useSharedValue(1);
  const glyph = useSharedValue(branched ? 1 : 0);
  const size = useSharedValue(0);
  const sized = useRef(false);

  const measured = widths[label];
  const width = measured === undefined ? undefined : TITLE_PAD * 2 + (branched ? GLYPH + TITLE_GAP : 0) + measured;

  // The chip follows its text on the same curve; the first width is simply taken.
  useEffect(() => {
    if (width === undefined) return;
    size.set(sized.current ? withTiming(width, ROLL_TIMING) : width);
    sized.current = true;
  }, [width, size]);

  useEffect(() => {
    glyph.set(withTiming(branched ? 1 : 0, ROLL_TIMING));
  }, [branched, glyph]);

  useEffect(() => {
    if (layers.changes === 0) return;
    const [incoming, outgoing] = layers.front === 0 ? [first, second] : [second, first];
    incoming.set(1);
    incoming.set(withTiming(0, ROLL_TIMING));
    outgoing.set(withTiming(-1, ROLL_TIMING));
  }, [layers.changes, layers.front, first, second]);

  const measure = (text: string, measuredWidth: number) => {
    setWidths((known) => (known[text] === measuredWidth ? known : { ...known, [text]: measuredWidth }));
  };

  const chipStyle = useAnimatedStyle(() => ({ width: size.get(), opacity: size.get() > 0 ? 1 : 0 }));
  const glyphStyle = useAnimatedStyle(() => ({
    opacity: glyph.get(),
    transform: [{ scale: GLYPH_SCALE + (1 - GLYPH_SCALE) * glyph.get() }],
  }));
  // The glyph pushes the title aside as it comes in; reduced motion keeps the roll as a cross-fade.
  const firstStyle = useAnimatedStyle(() => ({
    opacity: 1 - Math.abs(first.get()),
    transform: [{ translateX: (GLYPH + TITLE_GAP) * glyph.get() }, { translateY: reducedMotion ? 0 : ROLL * first.get() }],
  }));
  const secondStyle = useAnimatedStyle(() => ({
    opacity: 1 - Math.abs(second.get()),
    transform: [{ translateX: (GLYPH + TITLE_GAP) * glyph.get() }, { translateY: reducedMotion ? 0 : ROLL * second.get() }],
  }));

  return (
    <Animated.View style={[styles.chip, chipStyle]} accessible accessibilityLabel={label}>
      <Animated.View style={[styles.chipGlyph, glyphStyle]}>
        <Glyph d={BRANCH} size={GLYPH} color={CORAL} width={1.6} />
      </Animated.View>
      <Animated.View style={[styles.chipLayer, firstStyle]}>
        <Text style={styles.label} numberOfLines={1} onLayout={(event) => measure(layers.texts[0], event.nativeEvent.layout.width)}>
          {layers.texts[0]}
        </Text>
      </Animated.View>
      <Animated.View style={[styles.chipLayer, secondStyle]}>
        <Text style={styles.label} numberOfLines={1} onLayout={(event) => measure(layers.texts[1], event.nativeEvent.layout.width)}>
          {layers.texts[1]}
        </Text>
      </Animated.View>
    </Animated.View>
  );
}

type PassageProps = {
  index: number;
  text: string;
  motion: Motion;
  reducedMotion: boolean;
  /** A finger has started to drag this paragraph. */
  onGrab: () => void;
  /** The pull has just passed the threshold under a finger. */
  onArm: () => void;
  /** The paragraph was let go past the threshold. */
  onQuote: (index: number) => void;
  /** The swipe is over, quoted or not. */
  onLetGo: () => void;
  /** The same reply, asked for by a screen reader. */
  onReply: (index: number) => void;
};

/** One paragraph of the answer: it follows a swipe to the right and shows the reply arrow in the gap. */
function Passage({ index, text, motion, reducedMotion, onGrab, onArm, onQuote, onLetGo, onReply }: PassageProps) {
  const { pull, active, held, arm, armed, swiping, free, quotedIndex, quote, busy } = motion;
  // 0…1: this paragraph is the quoted one.
  const mark = useSharedValue(0);
  // Where the finger came down: the pull is measured from there, not from where the swipe was recognised.
  const origin = useSharedValue(0);

  useAnimatedReaction(
    () => quotedIndex.get() === index,
    (marked, before) => {
      if (before !== null && marked !== before) mark.set(settle(marked ? 1 : 0, reducedMotion));
    },
  );

  const swipe = Gesture.Pan()
    .activeOffsetX(SLOP)
    .failOffsetY([-SLOP, SLOP])
    .onBegin((event) => {
      origin.set(event.absoluteX);
    })
    .onStart(() => {
      scheduleOnRN(onGrab);
    })
    .onUpdate((event) => {
      // The quoted paragraph stays put, as does everything while a quote flies off; one swipe at a time.
      if (quotedIndex.get() === index || busy.get()) return;
      if (swiping.get() && active.get() !== index) return;
      const reach = resist(event.absoluteX - origin.get());
      if (!swiping.get()) {
        swiping.set(true);
        armed.set(false);
        arm.set(0);
        free.set(quotedIndex.get() < 0 ? 1 : 0);
      }
      active.set(index);
      held.set(1);
      pull.set(reach);
      const isArmed = reach >= THRESHOLD;
      if (isArmed !== armed.get()) {
        armed.set(isArmed);
        if (isArmed) scheduleOnRN(onArm);
        arm.set(pop(isArmed ? 1 : 0, reducedMotion));
      }
    })
    .onFinalize(() => {
      if (!swiping.get() || active.get() !== index) return;
      swiping.set(false);
      if (armed.get()) {
        quotedIndex.set(index);
        scheduleOnRN(onQuote, index);
      }
      // Everything the pull moved goes back together: `pull` keeps its value and `held` lets go of it.
      held.set(settle(0, reducedMotion));
      scheduleOnRN(onLetGo);
    });

  const arrowStyle = useAnimatedStyle(() => {
    const hold = active.get() === index ? held.get() : 0;
    const progress = Math.min(1, pull.get() / THRESHOLD);
    // Small and faint until the pull arms, then full size.
    const scale = ARROW_HINT * progress + (1 - ARROW_HINT * progress) * arm.get();
    const opacity = ARROW_FAINT * progress + (1 - ARROW_FAINT * progress) * arm.get();
    const rest = reducedMotion ? 1 : ARROW_REST;
    // Centred in the gap the paragraph has opened to the left of its bar.
    const centred = (pull.get() - BAR_GAP - ARROW_SIZE) / 2;
    return {
      opacity: clamp01(opacity * hold),
      transform: [{ translateX: -ARROW_SIZE / 2 + (centred + ARROW_SIZE / 2) * hold }, { scale: rest + (scale - rest) * hold }],
    };
  });

  const passageStyle = useAnimatedStyle(() => {
    const mine = active.get() === index;
    const progress = Math.min(1, pull.get() / THRESHOLD);
    // The others dim as the pull approaches the threshold, and stay dim while a paragraph is quoted.
    const dimmed = clamp01(quote.get() - mark.get() + (mine ? 0 : progress * held.get() * free.get()));
    return {
      opacity: 1 - DIM * dimmed,
      transform: [{ translateX: mine ? pull.get() * held.get() : 0 }],
    };
  });

  const barStyle = useAnimatedStyle(() => {
    const hold = active.get() === index ? held.get() : 0;
    return { opacity: clamp01(mark.get() + Math.min(1, pull.get() / THRESHOLD) * hold) };
  });

  const onAccessibilityAction = (event: AccessibilityActionEvent) => {
    if (event.nativeEvent.actionName === REPLY_ACTION) onReply(index);
  };

  return (
    <GestureDetector gesture={swipe}>
      <View style={styles.row} accessible accessibilityActions={REPLY_ACTIONS} onAccessibilityAction={onAccessibilityAction}>
        <View style={styles.arrowSlot} pointerEvents="none">
          <Animated.View style={[styles.arrow, arrowStyle]}>
            <Glyph d={REPLY} size={16} color={PANEL} width={1.8} />
          </Animated.View>
        </View>
        <Animated.View style={passageStyle}>
          <Animated.View style={[styles.bar, barStyle]} />
          <Text style={styles.answerText}>{text}</Text>
        </Animated.View>
      </View>
    </GestureDetector>
  );
}

/**
 * The caret autoPlay draws after the last letter, since the field itself is not focused. Memoised:
 * every render registers the frame clock again, which costs it a frame.
 */
const Caret = memo(function Caret({ idle }: { idle: SharedValue<number> }) {
  useFrameCallback((frame) => {
    idle.set(idle.get() + (frame.timeSincePreviousFrame ?? 0) / 1000);
  });
  const style = useAnimatedStyle(() => ({ opacity: blink(idle.get()) }));
  return <Animated.View style={[styles.caret, style]} />;
});

/**
 * A dark chat panel where any paragraph of the assistant's answer can be swiped right to reply to
 * it. The paragraph follows the finger with resistance, a coral reply arrow pops in beside it past
 * a threshold and the rest of the answer dims; on release it springs back with a coral bar that
 * marks it as quoted, a quote preview slides up out of the composer, the placeholder changes and a
 * "Branch" pill appears beside the field. The quote can be answered, dismissed, or branched into a
 * new thread.
 */
export function SwipeReplyComposer({
  value,
  autoPlay = false,
  title = 'Weekend in Lisbon',
  branchTitle = 'New branch',
  paragraphs = [
    'Two days is enough for Lisbon if you stay central and walk.',
    'Start in Alfama before the trams fill up, then ride the 28 up to Graça for the best light over the river.',
    'Keep Belém for the second morning: the tower, the monastery and a warm custard tart.',
  ],
  placeholder = 'Reply to Atlas',
  quotePlaceholder = 'Reply to this part…',
  branchLabel = 'Branch',
  script = 'And if it rains?',
  onChangeText,
  onSend,
  onQuote,
  onBranch,
  onAttach,
}: Props) {
  const reducedMotion = useReducedMotion();
  const input = useRef<TextInput>(null);

  const [draft, setDraft] = useState('');
  // What autoPlay has typed so far; the caller's value is never touched by the script.
  const [typed, setTyped] = useState('');
  // True while autoPlay owns the component.
  const [playing, setPlaying] = useState(autoPlay);
  const [caretOn, setCaretOn] = useState(false);
  const [quoted, setQuoted] = useState<number | null>(null);
  // The preview's text, kept while the preview slides away.
  const [quoteText, setQuoteText] = useState('');
  // The preview is flying up into a new branch.
  const [flying, setFlying] = useState(false);
  const [branched, setBranched] = useState(false);
  // The sent text is on its way out of the field.
  const [clearing, setClearing] = useState(false);
  const [pressed, setPressed] = useState<Press | null>(null);
  const [focused, setFocused] = useState(false);
  // Counts the touches that put off autoPlay's return.
  const [touches, setTouches] = useState(0);
  const [seen, setSeen] = useState(autoPlay);

  const pull = useSharedValue(0);
  const active = useSharedValue(-1);
  const held = useSharedValue(0);
  const arm = useSharedValue(0);
  const armed = useSharedValue(false);
  const swiping = useSharedValue(false);
  const free = useSharedValue(1);
  const quotedIndex = useSharedValue(-1);
  const quote = useSharedValue(0);
  const busy = useSharedValue(false);
  // 0…1: the preview on its way up into a branch. It stays there, unseen, until the next quote.
  const fly = useSharedValue(0);
  // Seconds since the script's last key landed.
  const idle = useSharedValue(0);
  const motion: Motion = { pull, active, held, arm, armed, swiping, free, quotedIndex, quote, busy };

  const current = value ?? draft;
  const shown = playing ? typed : current;
  const hasText = shown !== '';
  const quoting = quoted !== null;

  // The preview, the field's width, the Branch pill and the dimming all follow the quote from here.
  useAnimatedReaction(
    () => quotedIndex.get(),
    (index, before) => {
      if (before === null || index === before) return;
      if (index >= 0) fly.set(0);
      if (index >= 0 !== before >= 0) quote.set(settle(index >= 0 ? 1 : 0, reducedMotion));
      if (index < 0) free.set(settle(1, reducedMotion));
    },
  );

  const change = (next: string) => {
    if (value === undefined) setDraft(next);
    onChangeText?.(next);
  };

  const touched = () => setTouches((count) => count + 1);

  const mark = (index: number | null) => {
    setQuoted(index);
    quotedIndex.set(index ?? -1);
  };

  /** The state at rest. */
  const rest = () => {
    setTyped('');
    setCaretOn(false);
    setPressed(null);
    setClearing(false);
    setFlying(false);
    setBranched(false);
    setQuoted(null);
  };

  /** The shared values at rest. A swipe that asks for it keeps hold of the paragraph it has just grabbed. */
  const calm = (bySwipe = false) => {
    busy.set(false);
    quotedIndex.set(-1);
    if (bySwipe) return;
    cancelAnimation(pull);
    held.set(settle(0, reducedMotion));
  };

  const reset = (bySwipe = false) => {
    rest();
    calm(bySwipe);
  };

  // When autoPlay is switched on or off, everything starts over from rest.
  if (seen !== autoPlay) {
    setSeen(autoPlay);
    setPlaying(autoPlay);
    rest();
  }

  const calmDown = useEffectEvent(() => calm());

  useEffect(() => {
    calmDown();
  }, [autoPlay]);

  /** A touch stops the script and puts the panel back at rest. */
  const takeOver = (bySwipe = false) => {
    if (!playing) return;
    setPlaying(false);
    reset(bySwipe);
  };

  /** For buttons: true when the touch only stopped autoPlay, so the button should do nothing else. */
  const handOver = () => {
    if (!playing) return false;
    takeOver();
    touched();
    return true;
  };

  /** What a quote changes off the UI thread: the preview's text and the composer. */
  const showQuote = (index: number, byHand: boolean) => {
    if (byHand) onQuote?.(index);
    setQuoteText(paragraphs[index]);
    setQuoted(index);
  };

  /** The paragraph springs back, marked as quoted, and the composer changes to answer it. */
  const quoteParagraph = (index: number, byHand: boolean) => {
    if (index < 0 || index >= paragraphs.length) return;
    showQuote(index, byHand);
    quotedIndex.set(index);
    held.set(settle(0, reducedMotion));
  };

  const dismissQuote = (byHand: boolean) => {
    if (byHand) {
      if (handOver()) return;
      impact(Haptics.ImpactFeedbackStyle.Light);
      onQuote?.(null);
      touched();
    }
    mark(null);
  };

  const branch = () => {
    if (handOver() || quoted === null || flying) return;
    impact(Haptics.ImpactFeedbackStyle.Light);
    onBranch?.(quoted);
    fly.set(withTiming(1, reducedMotion ? EASE_TIMING : FLY_TIMING));
    busy.set(true);
    setFlying(true);
  };

  const send = () => {
    const message = shown.trim();
    if (clearing || message === '') return;
    if (!playing) {
      impact(Haptics.ImpactFeedbackStyle.Light);
      onSend?.(message, quoted);
    }
    setCaretOn(false);
    setClearing(true);
  };

  // The sent text has left the field: empty it, and the quote goes with it.
  const finishSend = useEffectEvent(() => {
    if (playing) setTyped('');
    else change('');
    setClearing(false);
    mark(null);
    touched();
  });

  useEffect(() => {
    if (!clearing) return;
    const timer = setTimeout(() => finishSend(), FADE * 1000);
    return () => clearTimeout(timer);
  }, [clearing]);

  // The preview has flown off: the quote is over and the panel is now the new branch.
  const finishBranch = useEffectEvent(() => {
    mark(null);
    setBranched(true);
    setFlying(false);
    busy.set(false);
    touched();
  });

  useEffect(() => {
    if (!flying) return;
    const timer = setTimeout(() => finishBranch(), FLY * 1000);
    return () => clearTimeout(timer);
  }, [flying]);

  const cues = useEffectEvent(() => (paragraphs.length > 1 ? timeline(script, paragraphs.length - 1, reducedMotion) : []));

  const perform = useEffectEvent((cue: Cue) => {
    switch (cue.kind) {
      case 'swipe':
        active.set(cue.index);
        arm.set(0);
        free.set(quoted === null ? 1 : 0);
        pull.set(0);
        held.set(1);
        pull.set(withTiming(SCRIPT_PULL, DRAG_TIMING));
        break;
      case 'arm':
        arm.set(pop(1, reducedMotion));
        break;
      case 'quote':
        quoteParagraph(cue.index, false);
        break;
      case 'caret':
        idle.set(0);
        setCaretOn(true);
        break;
      case 'key':
        setTyped(cue.text);
        idle.set(0);
        break;
      case 'press':
        setPressed(cue.button);
        break;
      case 'send':
        setPressed(null);
        send();
        break;
      case 'dismiss':
        setPressed(null);
        dismissQuote(false);
        break;
    }
  });

  // The autoPlay loop. Every cue waits for its own moment after the loop began, so the period stays `LOOP`.
  useEffect(() => {
    if (!playing) return;
    let timer: ReturnType<typeof setTimeout>;
    const wait = (start: number, list: Cue[], index: number) => {
      const over = index === list.length;
      const due = start + (over ? LOOP : list[index].at) * 1000;
      timer = setTimeout(
        () => {
          if (over) {
            wait(due, cues(), 0);
          } else {
            perform(list[index]);
            wait(start, list, index + 1);
          }
        },
        Math.max(0, due - performance.now()),
      );
    };
    const list = cues();
    if (list.length > 0) wait(performance.now(), list, 0);
    return () => clearTimeout(timer);
  }, [playing]);

  const resume = useEffectEvent(() => {
    if (current !== '' || quoted !== null || swiping.get()) return;
    setPlaying(true);
    reset();
  });

  // autoPlay comes back once nothing has been touched for a while and the panel is at rest.
  useEffect(() => {
    if (!autoPlay || playing || focused) return;
    const timer = setTimeout(() => resume(), RESUME_AFTER * 1000);
    return () => clearTimeout(timer);
  }, [autoPlay, playing, focused, touches]);

  const previewStyle = useAnimatedStyle(() => ({
    opacity: clamp01(quote.get()) * (1 - fly.get()),
    // Hidden, it waits behind the composer; flying into a branch, it leaves upwards.
    transform: [{ translateY: reducedMotion ? 0 : QUOTE_DROP * (1 - quote.get()) - QUOTE_FLY * fly.get() }],
  }));
  const fieldStyle = useAnimatedStyle(() => ({ width: FIELD_WIDE - (FIELD_WIDE - FIELD_NARROW) * quote.get() }));
  const branchStyle = useAnimatedStyle(() => ({
    opacity: clamp01(quote.get()),
    transform: [{ scale: reducedMotion ? 1 : BRANCH_SCALE + (1 - BRANCH_SCALE) * quote.get() }],
  }));

  // The sent text rises and fades; once the field is empty it is back in place at once.
  const leaving = {
    transform: [{ translateY: clearing && !reducedMotion ? -CLEAR_RISE : 0 }],
    transitionProperty: ['opacity', 'transform'],
    transitionDuration: clearing ? FADE * 1000 : 0,
    transitionTimingFunction: 'ease-in' as const,
  };

  return (
    <View style={styles.panel}>
      <TitleChip label={branched ? branchTitle : title} branched={branched} reducedMotion={reducedMotion} />

      <View style={styles.answer}>
        {paragraphs.map((text, index) => (
          <Passage
            key={index}
            index={index}
            text={text}
            motion={motion}
            reducedMotion={reducedMotion}
            onGrab={() => takeOver(true)}
            onArm={() => impact(Haptics.ImpactFeedbackStyle.Medium)}
            onQuote={(swiped) => showQuote(swiped, true)}
            onLetGo={touched}
            onReply={(asked) => quoteParagraph(asked, true)}
          />
        ))}
      </View>

      {/* Drawn before the composer, so it comes out from behind it. */}
      <Animated.View style={[styles.preview, previewStyle]} pointerEvents={quoting && !flying ? 'auto' : 'none'}>
        <View style={styles.previewBar} />
        <Text style={styles.previewText} numberOfLines={1}>
          {quoteText}
        </Text>
        <Animated.View style={dip(pressed === 'dismiss')}>
          <Pressable style={styles.dismiss} onPress={() => dismissQuote(true)} accessibilityRole="button" accessibilityLabel="Dismiss quote">
            {({ pressed: down }) => (
              <Animated.View style={[styles.dismissFace, press(down)]}>
                <Glyph d={CROSS} size={9} color={MUTE} width={1.6} />
              </Animated.View>
            )}
          </Pressable>
        </Animated.View>
      </Animated.View>

      <View style={styles.composer}>
        <Pressable
          style={styles.plusSlot}
          onPress={() => {
            if (!handOver()) onAttach?.();
          }}
          accessibilityRole="button"
          accessibilityLabel="Attach"
        >
          {({ pressed: down }) => (
            <Animated.View style={[styles.plus, press(down)]}>
              <View style={styles.plusH} />
              <View style={styles.plusV} />
            </Animated.View>
          )}
        </Pressable>

        <Animated.View style={[styles.field, fieldStyle]}>
          <View style={[styles.hint, { opacity: hasText || caretOn ? 0 : 1 }]} pointerEvents="none">
            <Animated.View style={[styles.hintLayer, styles.hintWide, fade(!quoting)]}>
              <Text style={styles.placeholder} numberOfLines={1} adjustsFontSizeToFit minimumFontScale={0.85}>
                {placeholder}
              </Text>
            </Animated.View>
            <Animated.View style={[styles.hintLayer, styles.hintNarrow, fade(quoting)]}>
              <Text style={styles.placeholder} numberOfLines={1} adjustsFontSizeToFit minimumFontScale={0.85}>
                {quotePlaceholder}
              </Text>
            </Animated.View>
          </View>

          <Animated.View style={[styles.entry, { opacity: playing || clearing ? 0 : 1 }, leaving]}>
            <TextInput
              ref={input}
              style={styles.input}
              value={current}
              onChangeText={change}
              returnKeyType="send"
              onSubmitEditing={() => send()}
              onFocus={() => {
                setFocused(true);
                takeOver();
                touched();
              }}
              onBlur={() => {
                setFocused(false);
                touched();
              }}
              selectionColor={CORAL}
              cursorColor={CORAL}
              underlineColorAndroid="transparent"
            />
          </Animated.View>

          {playing && (
            <Animated.View style={[styles.mirror, { opacity: clearing ? 0 : 1 }, leaving]} pointerEvents="none">
              <Text style={styles.typed} numberOfLines={1}>
                {typed}
              </Text>
              {caretOn && <Caret idle={idle} />}
            </Animated.View>
          )}

          {/* While autoPlay runs, a touch on the field hands it to the keyboard. */}
          {playing && (
            <Pressable
              style={styles.cover}
              accessible={false}
              onPress={() => {
                takeOver();
                input.current?.focus();
              }}
            />
          )}

          <Animated.View style={[styles.sendSlot, dip(pressed === 'send')]}>
            <Pressable
              style={styles.sendHit}
              onPress={() => {
                if (!handOver()) send();
              }}
              accessibilityRole="button"
              accessibilityLabel="Send"
            >
              {({ pressed: down }) => (
                <Animated.View style={[styles.send, press(down)]}>
                  <Glyph d={ARROW} size={SEND_SIZE} color={MUTE} width={2} />
                  {/* The lit button fades in over the idle one with the first letter. */}
                  <Animated.View style={[styles.sendLit, fade(hasText)]}>
                    <Glyph d={ARROW} size={SEND_SIZE} color={PANEL} width={2} />
                  </Animated.View>
                </Animated.View>
              )}
            </Pressable>
          </Animated.View>
        </Animated.View>

        <Animated.View style={[styles.branchSlot, branchStyle]} pointerEvents={quoting && !flying ? 'auto' : 'none'}>
          <Pressable onPress={branch} accessibilityRole="button" accessibilityLabel={branchLabel}>
            {({ pressed: down }) => (
              <Animated.View style={[styles.branch, press(down)]}>
                <Glyph d={BRANCH} size={GLYPH} color={CORAL} width={1.6} />
                <Text style={styles.label} numberOfLines={1}>
                  {branchLabel}
                </Text>
              </Animated.View>
            )}
          </Pressable>
        </Animated.View>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  panel: {
    width: PANEL_WIDTH,
    height: PANEL_HEIGHT,
    borderRadius: PANEL_RADIUS,
    backgroundColor: PANEL,
    overflow: 'hidden',
  },
  chip: {
    position: 'absolute',
    left: PAD_X,
    top: TITLE_TOP,
    height: TITLE_HEIGHT,
    borderRadius: TITLE_HEIGHT / 2,
    backgroundColor: RAISED,
    overflow: 'hidden',
  },
  chipGlyph: {
    position: 'absolute',
    left: TITLE_PAD,
    top: (TITLE_HEIGHT - GLYPH) / 2,
  },
  // Wide enough for any title, so a text is never squeezed while the chip changes width.
  chipLayer: {
    position: 'absolute',
    left: TITLE_PAD,
    top: 0,
    width: PANEL_WIDTH,
    height: TITLE_HEIGHT,
    alignItems: 'flex-start',
    justifyContent: 'center',
  },
  label: {
    color: INK,
    fontSize: LABEL_FONT,
    fontWeight: '500',
    includeFontPadding: false,
  },
  answer: {
    position: 'absolute',
    left: PAD_X,
    top: ANSWER_TOP,
    width: ANSWER_WIDTH,
    gap: PARAGRAPH_GAP,
  },
  row: {
    width: ANSWER_WIDTH,
    minHeight: ARROW_SIZE,
    justifyContent: 'center',
  },
  arrowSlot: {
    position: 'absolute',
    left: 0,
    top: 0,
    bottom: 0,
    justifyContent: 'center',
  },
  arrow: {
    width: ARROW_SIZE,
    height: ARROW_SIZE,
    borderRadius: ARROW_SIZE / 2,
    backgroundColor: CORAL,
    alignItems: 'center',
    justifyContent: 'center',
  },
  bar: {
    position: 'absolute',
    left: -BAR_GAP,
    top: BAR_INSET,
    bottom: BAR_INSET,
    width: BAR_WIDTH,
    borderRadius: BAR_WIDTH / 2,
    backgroundColor: CORAL,
  },
  answerText: {
    width: ANSWER_WIDTH,
    color: INK,
    fontFamily: SERIF,
    fontSize: ANSWER_FONT,
    lineHeight: ANSWER_LINE,
    marginVertical: -ANSWER_TRIM,
    includeFontPadding: false,
  },
  preview: {
    position: 'absolute',
    left: COMPOSER_X,
    top: QUOTE_TOP,
    width: COMPOSER_WIDTH,
    height: QUOTE_HEIGHT,
    paddingLeft: 14,
    borderRadius: QUOTE_RADIUS,
    backgroundColor: RAISED,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 9,
  },
  previewBar: {
    width: BAR_WIDTH,
    height: 16,
    borderRadius: BAR_WIDTH / 2,
    backgroundColor: CORAL,
  },
  previewText: {
    flex: 1,
    color: QUOTE,
    fontSize: LABEL_FONT,
    includeFontPadding: false,
  },
  dismiss: {
    width: DISMISS_SLOT,
    height: QUOTE_HEIGHT,
    alignItems: 'center',
    justifyContent: 'center',
  },
  dismissFace: {
    width: DISMISS_SIZE,
    height: DISMISS_SIZE,
    borderRadius: DISMISS_SIZE / 2,
    backgroundColor: FIELD,
    alignItems: 'center',
    justifyContent: 'center',
  },
  composer: {
    position: 'absolute',
    left: COMPOSER_X,
    top: COMPOSER_TOP,
    width: COMPOSER_WIDTH,
    height: FIELD_HEIGHT,
  },
  plusSlot: {
    position: 'absolute',
    left: 0,
    top: (FIELD_HEIGHT - PLUS_SIZE) / 2,
  },
  plus: {
    width: PLUS_SIZE,
    height: PLUS_SIZE,
    borderRadius: PLUS_SIZE / 2,
    backgroundColor: FIELD,
    alignItems: 'center',
    justifyContent: 'center',
  },
  plusH: {
    position: 'absolute',
    width: 14,
    height: 1.6,
    borderRadius: 0.8,
    backgroundColor: QUOTE,
  },
  plusV: {
    position: 'absolute',
    width: 1.6,
    height: 14,
    borderRadius: 0.8,
    backgroundColor: QUOTE,
  },
  field: {
    position: 'absolute',
    left: PLUS_SIZE + COMPOSER_GAP,
    top: 0,
    height: FIELD_HEIGHT,
    borderRadius: FIELD_HEIGHT / 2,
    backgroundColor: FIELD,
  },
  hint: {
    position: 'absolute',
    left: FIELD_PAD_X,
    top: 0,
    height: FIELD_HEIGHT,
  },
  // Each placeholder keeps the width of the field it belongs to, so neither reflows while the field resizes.
  hintLayer: {
    position: 'absolute',
    left: 0,
    top: 0,
    height: FIELD_HEIGHT,
    justifyContent: 'center',
  },
  hintWide: {
    width: FIELD_WIDE - FIELD_PAD_X - SEND_SLOT,
  },
  hintNarrow: {
    width: FIELD_NARROW - FIELD_PAD_X - SEND_SLOT,
  },
  placeholder: {
    color: MUTE,
    fontSize: FIELD_FONT,
    includeFontPadding: false,
  },
  entry: {
    position: 'absolute',
    left: FIELD_PAD_X,
    right: SEND_SLOT,
    top: 0,
    height: FIELD_HEIGHT,
  },
  input: {
    height: FIELD_HEIGHT,
    padding: 0,
    color: INK,
    fontSize: FIELD_FONT,
    includeFontPadding: false,
    textAlignVertical: 'center',
  },
  // Wider than any reply, so the script's text is never cut short.
  mirror: {
    position: 'absolute',
    left: FIELD_PAD_X,
    top: 0,
    width: PANEL_WIDTH,
    height: FIELD_HEIGHT,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 1,
  },
  typed: {
    color: INK,
    fontSize: FIELD_FONT,
    includeFontPadding: false,
  },
  caret: {
    width: 2,
    height: CARET_HEIGHT,
    borderRadius: 1,
    backgroundColor: CORAL,
  },
  cover: {
    position: 'absolute',
    left: 0,
    top: 0,
    right: 0,
    bottom: 0,
  },
  sendSlot: {
    position: 'absolute',
    right: 0,
    top: 0,
  },
  sendHit: {
    width: SEND_SLOT,
    height: FIELD_HEIGHT,
    alignItems: 'center',
    justifyContent: 'center',
  },
  send: {
    width: SEND_SIZE,
    height: SEND_SIZE,
    borderRadius: SEND_SIZE / 2,
    backgroundColor: SEND_IDLE,
  },
  sendLit: {
    position: 'absolute',
    left: 0,
    top: 0,
    width: SEND_SIZE,
    height: SEND_SIZE,
    borderRadius: SEND_SIZE / 2,
    backgroundColor: CORAL,
  },
  branchSlot: {
    position: 'absolute',
    right: 0,
    top: 0,
    transformOrigin: 'right center',
  },
  branch: {
    width: BRANCH_WIDTH,
    height: FIELD_HEIGHT,
    borderRadius: FIELD_HEIGHT / 2,
    backgroundColor: FIELD,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 5,
  },
});
