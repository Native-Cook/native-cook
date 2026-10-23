import * as Haptics from 'expo-haptics';
import { useEffect, useEffectEvent, useRef, useState } from 'react';
import { Pressable, StyleSheet, Text, TextInput, View } from 'react-native';
import Animated, {
  Easing,
  ReduceMotion,
  cubicBezier,
  useAnimatedStyle,
  useReducedMotion,
  useSharedValue,
  withDelay,
  withRepeat,
  withSequence,
  withSpring,
  withTiming,
} from 'react-native-reanimated';
import Svg, { Path, Rect } from 'react-native-svg';

type Props = {
  /** The field's value when the caller owns it; left undefined, the component keeps its own. */
  value?: string;
  /** Rotates through the sets and picks one suggestion per loop by itself. Never opens the keyboard. */
  autoPlay?: boolean;
  title?: string;
  placeholder?: string;
  /** Sets of three suggestions, top to bottom. The stack shows one set at a time. */
  suggestions?: string[][];
  footnote?: string;
  /** Parts of the footnote drawn darker, as links. */
  footnoteLinks?: string[];
  onChangeText?: (text: string) => void;
  /** Called with a suggestion when its pill is tapped. */
  onSelect?: (suggestion: string) => void;
  /** Called with the trimmed message when it is sent: by the send button, the keyboard, or a tapped pill. */
  onSend?: (message: string) => void;
  onAttach?: () => void;
};

const SUGGESTIONS = [
  ['Make an image of a rocket', 'Top books of the year', 'Discuss the theory of relativity'],
  ['Show the concept of time travel', 'Top books of the year', 'Explain weather versus climate'],
  ['Capture a serene mountain lake', 'Fashion trends for next season', 'What are the Seven Wonders?'],
  ['Render a superhero in action', 'Current environmental issues', 'How do neural networks work?'],
];
const FOOTNOTE_LINKS = ['Terms', 'Privacy Policy'];

const PANEL_WIDTH = 350;
const PANEL_HEIGHT = 400;
const PANEL_RADIUS = 40;
const TITLE_Y = 36;
const MARK_Y = 108;
const MARK_SIZE = 60;

const SLOTS = 3;
const STACK_TOP = 176;
const PILL_HEIGHT = 34;
const PILL_GAP = 8;
const PILL_PAD_LEFT = 11;
const PILL_PAD_RIGHT = 14;
const ICON_SIZE = 15;
const ICON_GAP = 7;
const PILL_FONT = 14;
// How far a text travels on its way out and on its way in.
const SLIDE = 6;

const PLUS_X = 32;
const FIELD_X = 56;
const FIELD_TOP = 306;
const FIELD_WIDTH = 278;
const FIELD_HEIGHT = 44;
const FIELD_PAD_X = 16;
const FIELD_FONT = 15;
const SEND_SIZE = 32;
const FOOTNOTE_Y = 372;

const LIFT_SCALE = 1.05;
const LIFT_RISE = 5;
const FLOWN_SCALE = 0.6;
const RETURN_SCALE = 0.8;
const POP_SCALE = 0.4;
const PRESS_SCALE = 0.86;
const CLEAR_RISE = 6;

// A picked suggestion, in seconds from the tap.
const LIFT_TIME = 0.16;
const FLY_TIME = 0.3;
// The first letter lands just before the pill has vanished into the field.
const TYPE_AT = 0.4;
const TYPE_SPAN = 0.8;
const SLOWEST_KEY = 0.045;
const PRESS_AT = 1.9;
const PRESS_TIME = 0.12;
const CLEAR_TIME = 0.2;

// The autoPlay loop, in seconds from its start.
const ROTATE_EVERY = 1.4;
// How long one text takes to leave and the next to arrive, and how far apart the pills start.
const CHANGE = 0.35;
const STAGGER = 0.1;
const ROTATE_AT = [0.8, 2.2];
const PICK_AT = 3.2;
const PICK_SLOT = 2;
// The last rotation, back to the first set.
const HOME_AT = 6.85;
const LOOP = 8.4;
const RESUME_AFTER = 3;

// The drawn caret: solid for a while after a key, then one blink a second.
const BLINK_AFTER = 500;
const BLINK_FADE = 120;
const BLINK_HOLD = 380;

const PANEL = '#f7f7f8';
const INK = '#141416';
const MARK = '#e9e9ed';
const PILL_TEXT = '#55555a';
const PILL_ICON = '#8e8e93';
const PLUS = '#8e8e93';
const PLACEHOLDER = '#9a9aa0';
const CARET = '#2d6bff';
const SEND = '#0e0e10';
const FOOTNOTE = '#a4a4aa';
const FOOTNOTE_LINK = '#6e6e73';

// The pill's shadow at rest and lifted off the stack (y offset, blur, opacity of black).
const SHADOW_REST = { y: 1, blur: 6, alpha: 0.07 };
const SHADOW_LIFTED = { y: 7, blur: 20, alpha: 0.16 };

// Every animation says so itself, because reduced motion is handled here: movement becomes a fade.
const MOTION = { reduceMotion: ReduceMotion.Never };
// The picked pill rising off the stack.
const LIFT_SPRING = { mass: 1, stiffness: 380, damping: 20, ...MOTION };
// The send button appearing, and a pill returning to its slot.
const POP_SPRING = { mass: 1, stiffness: 420, damping: 24, ...MOTION };
const CHANGE_TIMING = { duration: CHANGE * 1000, easing: Easing.bezier(0.4, 0, 0.2, 1), ...MOTION };
const FLY_TIMING = { duration: FLY_TIME * 1000, easing: Easing.bezier(0.5, 0, 0.8, 0.4), ...MOTION };
const FADE_TIMING = { duration: 200, easing: Easing.bezier(0, 0, 0.58, 1), ...MOTION };

const CSS_EASE_OUT = cubicBezier(0.23, 1, 0.32, 1);

// Icons, drawn in a 15 × 15 box. A sparkle is a diamond whose sides curve in to its centre.
const SPARKLE = 'M7.5 0.5Q7.5 7.5 14.5 7.5Q7.5 7.5 7.5 14.5Q7.5 7.5 0.5 7.5Q7.5 7.5 7.5 0.5Z';
const SPARKLES =
  'M6.5 2.5Q6.5 8.5 12.5 8.5Q6.5 8.5 6.5 14.5Q6.5 8.5 0.5 8.5Q6.5 8.5 6.5 2.5Z' +
  'M12 0.4Q12 3.2 14.8 3.2Q12 3.2 12 6Q12 3.2 9.2 3.2Q12 3.2 12 0.4Z';
const BARS = [
  { x: 1.8, y: 8.5, height: 5 },
  { x: 6.2, y: 5.2, height: 8.3 },
  { x: 10.6, y: 1.8, height: 11.7 },
];
const BUBBLE =
  'M4.6 11L4.2 11Q1.6 11 1.6 8.4L1.6 4.8Q1.6 2.2 4.2 2.2L10.8 2.2Q13.4 2.2 13.4 4.8L13.4 8.4Q13.4 11 10.8 11L7.8 11L4.6 13.6Z';
// Up arrow in the 32 × 32 send button.
const ARROW = 'M16 22.2L16 10.4M10.8 15.4L16 10.2L21.2 15.4';

// Drawn for the eye only: screen readers skip it.
const UNREAD = { accessibilityElementsHidden: true, importantForAccessibility: 'no-hide-descendants' } as const;

/** Where the picked pill is on its way into the field. */
type Stage = 'idle' | 'lifted' | 'flown' | 'away';

type Scene = {
  set: number;
  picked: number | null;
  stage: Stage;
  /** The caret drawn while a suggestion is typed into an unfocused field. */
  caretOn: boolean;
  /** The send button's dip when a picked suggestion sends itself. */
  pressed: boolean;
  /** The sent text is on its way out of the field. */
  clearing: boolean;
  focused: boolean;
};

const REST: Scene = { set: 0, picked: null, stage: 'idle', caretOn: false, pressed: false, clearing: false, focused: false };

type Chain = 'rotation' | 'pick' | 'send' | 'resume';

/** The small press every button here gives under a finger. */
function press(down: boolean) {
  return {
    transform: [{ scale: down ? 0.96 : 1 }],
    transitionProperty: 'transform' as const,
    transitionDuration: 130,
    transitionTimingFunction: CSS_EASE_OUT,
  };
}

/** The footnote cut into plain and link parts: the first occurrence of every link is drawn darker. */
function footnoteParts(footnote: string, links: string[]) {
  const marks = links
    .filter((link) => link.length > 0 && footnote.includes(link))
    .map((link) => ({ from: footnote.indexOf(link), to: footnote.indexOf(link) + link.length }))
    .sort((a, b) => a.from - b.from);
  const parts: { text: string; link: boolean }[] = [];
  let cursor = 0;
  for (const mark of marks) {
    if (mark.from < cursor) continue;
    if (mark.from > cursor) parts.push({ text: footnote.slice(cursor, mark.from), link: false });
    parts.push({ text: footnote.slice(mark.from, mark.to), link: true });
    cursor = mark.to;
  }
  if (cursor < footnote.length) parts.push({ text: footnote.slice(cursor), link: false });
  return parts;
}

/** Where a pill sits, and how large and visible it is, at each stage of its way into the field. */
function pose(stage: Stage, slot: number, reducedMotion: boolean) {
  const still = { scale: 1, dx: 0, dy: 0 };
  switch (stage) {
    case 'idle':
      return { ...still, opacity: 1, lift: 0 };
    case 'lifted':
      return reducedMotion
        ? { ...still, opacity: 1, lift: 1 }
        : { scale: LIFT_SCALE, dx: 0, dy: -LIFT_RISE, opacity: 1, lift: 1 };
    case 'flown': {
      if (reducedMotion) return { ...still, opacity: 0, lift: 0 };
      // It drops onto the start of the field's text.
      const centre = STACK_TOP + slot * (PILL_HEIGHT + PILL_GAP) + PILL_HEIGHT / 2;
      return {
        scale: FLOWN_SCALE,
        dx: FIELD_PAD_X - PILL_PAD_LEFT,
        dy: FIELD_TOP + FIELD_HEIGHT / 2 - centre,
        opacity: 0,
        lift: 0,
      };
    }
    case 'away':
      return { ...still, scale: reducedMotion ? 1 : RETURN_SCALE, opacity: 0, lift: 0 };
  }
}

function PillIcon({ slot }: { slot: number }) {
  const kind = slot % SLOTS;
  return (
    <Svg width={ICON_SIZE} height={ICON_SIZE} viewBox="0 0 15 15">
      {kind === 0 ? (
        <Path d={SPARKLES} fill={PILL_ICON} />
      ) : kind === 1 ? (
        BARS.map((bar) => <Rect key={bar.x} x={bar.x} y={bar.y} width={2.6} height={bar.height} rx={1.2} fill={PILL_ICON} />)
      ) : (
        <Path d={BUBBLE} fill="none" stroke={PILL_ICON} strokeWidth={1.4} strokeLinecap="round" strokeLinejoin="round" />
      )}
    </Svg>
  );
}

type PillProps = {
  slot: number;
  label: string;
  /** The pill's width for `label`; undefined until the label has been measured. */
  width: number | undefined;
  stage: Stage;
  /** Above everything else while it is on its way into the field. */
  raised: boolean;
  enabled: boolean;
  reducedMotion: boolean;
  onPress: () => void;
};

/** One suggestion: an icon and a text that slides out below as the next one comes in from above. */
function Pill({ slot, label, width, stage, raised, enabled, reducedMotion, onPress }: PillProps) {
  // Two text layers take turns: the next text is set on the one out of sight, then they trade places.
  const [layers, setLayers] = useState({ texts: [label, ''], front: 0, changes: 0 });
  if (layers.texts[layers.front] !== label) {
    const front = 1 - layers.front;
    const texts = [...layers.texts];
    texts[front] = label;
    setLayers({ texts, front, changes: layers.changes + 1 });
  }

  // A layer's place: -1 above and unseen, 0 at rest, 1 below and unseen.
  const first = useSharedValue(0);
  const second = useSharedValue(1);
  const size = useSharedValue(0);
  const scale = useSharedValue(1);
  const dx = useSharedValue(0);
  const dy = useSharedValue(0);
  const opacity = useSharedValue(1);
  const lift = useSharedValue(0);
  // The stage the motion below last acted on, and whether the pill has had a width yet.
  const last = useRef({ stage, sized: false });

  // The width follows the text on the same curve and delay. It jumps the first time, and while the
  // pill is inside the field, where its next text is set unseen.
  useEffect(() => {
    if (width === undefined) return;
    if (!last.current.sized || last.current.stage === 'away') size.set(width);
    else size.set(withDelay(slot * STAGGER * 1000, withTiming(width, CHANGE_TIMING), ReduceMotion.Never));
    last.current.sized = true;
  }, [width, slot, size]);

  useEffect(() => {
    if (layers.changes === 0) return;
    const [incoming, outgoing] = layers.front === 0 ? [first, second] : [second, first];
    if (last.current.stage === 'away') {
      incoming.set(0);
      outgoing.set(1);
      return;
    }
    const delay = slot * STAGGER * 1000;
    incoming.set(-1);
    incoming.set(withDelay(delay, withTiming(0, CHANGE_TIMING), ReduceMotion.Never));
    outgoing.set(withDelay(delay, withTiming(1, CHANGE_TIMING), ReduceMotion.Never));
  }, [layers.changes, layers.front, slot, first, second]);

  // After the two effects above, which need to know whether the pill was still inside the field.
  useEffect(() => {
    const to = pose(stage, slot, reducedMotion);
    const move = (value: number) => {
      // Invisible by now: it goes back to its slot at once, ready to return.
      if (stage === 'away') return value;
      if (reducedMotion) return withTiming(value, FADE_TIMING);
      if (stage === 'flown') return withTiming(value, FLY_TIMING);
      return withSpring(value, stage === 'lifted' ? LIFT_SPRING : POP_SPRING);
    };
    scale.set(move(to.scale));
    dx.set(move(to.dx));
    dy.set(move(to.dy));
    opacity.set(move(to.opacity));
    lift.set(move(to.lift));
    last.current.stage = stage;
  }, [stage, slot, reducedMotion, scale, dx, dy, opacity, lift]);

  // The picked pill rises a little, then drops into the field, shrinking towards its leading edge.
  const flightStyle = useAnimatedStyle(() => ({
    opacity: Math.min(1, Math.max(0, opacity.get())),
    transform: [{ translateX: dx.get() }, { translateY: dy.get() }, { scale: scale.get() }],
  }));

  const bodyStyle = useAnimatedStyle(() => {
    const raise = Math.max(0, lift.get());
    const alpha = SHADOW_REST.alpha + (SHADOW_LIFTED.alpha - SHADOW_REST.alpha) * raise;
    return {
      width: size.get(),
      opacity: size.get() > 0 ? 1 : 0,
      boxShadow: [
        {
          offsetX: 0,
          offsetY: SHADOW_REST.y + (SHADOW_LIFTED.y - SHADOW_REST.y) * raise,
          blurRadius: SHADOW_REST.blur + (SHADOW_LIFTED.blur - SHADOW_REST.blur) * raise,
          color: `rgba(0, 0, 0, ${alpha})`,
        },
      ],
    };
  });

  const firstStyle = useAnimatedStyle(() => ({
    opacity: 1 - Math.abs(first.get()),
    transform: [{ translateY: reducedMotion ? 0 : SLIDE * first.get() }],
  }));
  const secondStyle = useAnimatedStyle(() => ({
    opacity: 1 - Math.abs(second.get()),
    transform: [{ translateY: reducedMotion ? 0 : SLIDE * second.get() }],
  }));

  return (
    <Animated.View
      pointerEvents={enabled ? 'auto' : 'none'}
      style={[
        styles.slot,
        { top: STACK_TOP + slot * (PILL_HEIGHT + PILL_GAP), zIndex: raised ? 1 : 0 },
        flightStyle,
      ]}
    >
      <Pressable onPress={onPress} accessibilityRole="button" accessibilityLabel={label}>
        {({ pressed }) => (
          <Animated.View style={press(pressed)}>
            <Animated.View style={[styles.pill, bodyStyle]}>
              {/* The capsule clips its text while it resizes; the shadow stays outside the clip. */}
              <View style={styles.pillClip}>
                <View style={styles.pillIcon}>
                  <PillIcon slot={slot} />
                </View>
                <Animated.View style={[styles.layer, firstStyle]}>
                  <Text style={styles.pillText} numberOfLines={1}>
                    {layers.texts[0]}
                  </Text>
                </Animated.View>
                <Animated.View style={[styles.layer, secondStyle]}>
                  <Text style={styles.pillText} numberOfLines={1}>
                    {layers.texts[1]}
                  </Text>
                </Animated.View>
              </View>
            </Animated.View>
          </Animated.View>
        )}
      </Pressable>
    </Animated.View>
  );
}

/** Solid while letters are landing; after half a second without one it blinks like the system caret. */
function Caret({ beat }: { beat: number }) {
  const on = useSharedValue(1);

  useEffect(() => {
    const fade = { duration: BLINK_FADE, easing: Easing.linear, ...MOTION };
    const hold = { duration: BLINK_HOLD, ...MOTION };
    const blink = withSequence(
      ReduceMotion.Never,
      withTiming(0, fade),
      withTiming(0, hold),
      withTiming(1, fade),
      withTiming(1, hold),
    );
    on.set(1);
    on.set(withDelay(BLINK_AFTER, withRepeat(blink, -1, false, undefined, ReduceMotion.Never), ReduceMotion.Never));
  }, [beat, on]);

  const style = useAnimatedStyle(() => ({ opacity: on.get() }));
  return <Animated.View style={[styles.caret, style]} />;
}

/**
 * Assistant composer with three suggestion pills stacked above the field. The suggestions rotate by
 * themselves: each pill's text slides down and fades while the next one comes in from just above,
 * the three pills a beat apart, and every pill resizes to fit its new text. Tapping a pill lifts it,
 * drops it into the field, where its text appears letter by letter beside a send button, and sends it.
 */
export function RotatingSuggestionsInput({
  value,
  autoPlay = false,
  title = 'Nova',
  placeholder = 'Ask anything',
  suggestions = SUGGESTIONS,
  footnote = 'By continuing you agree to the Terms and Privacy Policy',
  footnoteLinks = FOOTNOTE_LINKS,
  onChangeText,
  onSelect,
  onSend,
  onAttach,
}: Props) {
  const reducedMotion = useReducedMotion();
  const [draft, setDraft] = useState('');
  const [scene, setScene] = useState(REST);
  // Rotation starts over whenever this changes: the script was stopped or is due back.
  const [run, setRun] = useState(0);
  // Text widths of the suggestions, measured once by the hidden labels at the end of the panel.
  const [widths, setWidths] = useState<Record<string, number>>({});

  const text = value ?? draft;
  const hasText = text.length > 0;

  // The timer chains below outlive the render that started them, so they read the scene, the text
  // and the props from here: always the current ones, also between two renders.
  const live = useRef({
    scene: REST,
    text,
    /** The `autoPlay` the rotation last started for; none before it first starts. */
    startedFor: undefined as boolean | undefined,
    /** True while autoPlay follows its script; otherwise the sets simply rotate. */
    scripted: autoPlay,
    pickedByHand: false,
    rotatedAt: 0,
  });
  const latest = useRef({ value, autoPlay, suggestions, onChangeText, onSelect, onSend });
  const timers = useRef<Record<Chain, ReturnType<typeof setTimeout>[]>>({ rotation: [], pick: [], send: [], resume: [] });

  useEffect(() => {
    latest.current = { value, autoPlay, suggestions, onChangeText, onSelect, onSend };
    if (value !== undefined) live.current.text = value;
  });

  const shown = useSharedValue(hasText ? 1 : 0);

  const show = (patch: Partial<Scene>) => {
    live.current.scene = { ...live.current.scene, ...patch };
    setScene(live.current.scene);
  };

  const cancel = (chain: Chain) => {
    timers.current[chain].forEach(clearTimeout);
    timers.current[chain] = [];
  };

  /** Runs `step` on `chain`, `seconds` after `start` (a `performance.now()` reading). */
  const at = (chain: Chain, start: number, seconds: number, step: () => void) => {
    timers.current[chain].push(setTimeout(step, Math.max(0, start + seconds * 1000 - performance.now())));
  };

  const tick = () => {
    if (process.env.EXPO_OS !== 'web') Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
  };

  const write = (next: string) => {
    live.current.text = next;
    if (latest.current.value === undefined) setDraft(next);
    latest.current.onChangeText?.(next);
  };

  const advance = (to?: number) => {
    live.current.rotatedAt = performance.now();
    const count = latest.current.suggestions.length;
    if (count === 0) return;
    show({ set: to ?? (live.current.scene.set + 1) % count });
  };

  const stopScript = () => {
    if (!live.current.scripted) return;
    live.current.scripted = false;
    cancel('rotation');
    setRun((count) => count + 1);
  };

  const cancelPick = () => {
    // Only a suggestion that was on its way into the field is taken out again.
    const typing = live.current.scene.picked !== null;
    cancel('pick');
    show({ picked: null, stage: 'idle', caretOn: false, pressed: false, clearing: false });
    if (typing && live.current.text.length > 0) write('');
  };

  /** autoPlay's script comes back once nothing has been touched for a while and the field is empty and unfocused. */
  const resumeWhenIdle = () => {
    cancel('resume');
    if (!latest.current.autoPlay || live.current.scripted || live.current.scene.focused) return;
    at('resume', performance.now(), RESUME_AFTER, () => {
      const { focused, picked } = live.current.scene;
      if (focused || live.current.text.length > 0 || picked !== null) return;
      live.current.scripted = true;
      setRun((count) => count + 1);
    });
  };

  const send = (byHand: boolean) => {
    const message = live.current.text.trim();
    if (live.current.scene.clearing || message.length === 0) return;
    if (byHand) {
      if (live.current.scene.picked === null) tick();
      latest.current.onSend?.(message);
    }
    show({ clearing: true });
    cancel('send');
    // The sent text has left the field: empty it and let the placeholder return.
    at('send', performance.now(), CLEAR_TIME, () => {
      write('');
      show({ clearing: false });
      if (live.current.scene.picked === null) resumeWhenIdle();
    });
  };

  /** A picked pill lifts, drops into the field, types itself there, waits a beat and sends. */
  const pick = (slot: number, byHand: boolean) => {
    const { picked, clearing, set } = live.current.scene;
    const suggestion = latest.current.suggestions[set]?.[slot] ?? '';
    if (picked !== null || clearing || suggestion.length === 0) return;
    if (byHand) {
      stopScript();
      tick();
      latest.current.onSelect?.(suggestion);
    }
    live.current.pickedByHand = byHand;
    show({ picked: slot, stage: 'lifted' });

    cancel('pick');
    const start = performance.now();
    const then = (seconds: number, step: () => void) => at('pick', start, seconds, step);
    const letters = Array.from(suggestion);
    const key = Math.min(SLOWEST_KEY, TYPE_SPAN / letters.length);
    const sendAt = PRESS_AT + PRESS_TIME;
    // The next set arrives just after the sent text has cleared.
    const nextAt = sendAt + CLEAR_TIME + 0.02;

    then(LIFT_TIME, () => show({ stage: 'flown' }));
    then(TYPE_AT, () => show({ caretOn: true }));
    letters.forEach((_, index) => then(TYPE_AT + key * index, () => write(letters.slice(0, index + 1).join(''))));
    then(TYPE_AT + key * letters.length, () => show({ stage: 'away' }));
    then(PRESS_AT, () => show({ pressed: true }));
    then(sendAt, () => {
      show({ pressed: false, caretOn: false });
      send(live.current.pickedByHand);
    });
    then(nextAt, () => advance());
    // The pill returns to its slot with its new text, in step with the others.
    then(nextAt + slot * STAGGER, () => {
      show({ picked: null, stage: 'idle' });
      resumeWhenIdle();
    });
  };

  // Rotation. Under autoPlay every step waits for its own moment after the loop began, so the
  // period stays LOOP; otherwise the sets change every ROTATE_EVERY seconds.
  const rotate = useEffectEvent((auto: boolean) => {
    cancel('rotation');
    if (live.current.startedFor !== auto) {
      // On appearing, and when autoPlay is switched on or off: everything starts over from the first set.
      live.current.startedFor = auto;
      live.current.scripted = auto;
      cancelPick();
      show({ set: 0 });
    }

    if (!live.current.scripted) {
      const turn = () => {
        cancel('rotation');
        const wait = ROTATE_EVERY - (performance.now() - live.current.rotatedAt) / 1000;
        if (wait > 0) {
          at('rotation', performance.now(), wait, turn);
        } else if (live.current.scene.picked === null) {
          advance();
          at('rotation', performance.now(), ROTATE_EVERY, turn);
        } else {
          at('rotation', performance.now(), 0.2, turn);
        }
      };
      turn();
      return;
    }

    const loop = (start: number) => {
      cancel('rotation');
      ROTATE_AT.forEach((seconds) => at('rotation', start, seconds, () => advance()));
      at('rotation', start, PICK_AT, () => pick(PICK_SLOT, false));
      at('rotation', start, HOME_AT, () => advance(0));
      at('rotation', start, LOOP, () => loop(start + LOOP * 1000));
    };
    if (live.current.scene.set === 0) {
      loop(performance.now());
    } else {
      advance(0);
      at('rotation', performance.now(), ROTATE_EVERY, () => loop(performance.now()));
    }
  });

  useEffect(() => {
    const chains = timers.current;
    live.current.rotatedAt = performance.now();
    return () => Object.values(chains).forEach((chain) => chain.forEach(clearTimeout));
  }, []);

  useEffect(() => {
    rotate(autoPlay);
  }, [run, autoPlay]);

  // The send button pops in with the first letter and out when the field empties.
  useEffect(() => {
    const to = hasText ? 1 : 0;
    shown.set(reducedMotion ? withTiming(to, FADE_TIMING) : withSpring(to, POP_SPRING));
  }, [hasText, reducedMotion, shown]);

  const popStyle = useAnimatedStyle(() => ({
    opacity: Math.min(1, Math.max(0, shown.get())),
    transform: [{ scale: reducedMotion ? 1 : POP_SCALE + (1 - POP_SCALE) * shown.get() }],
  }));

  const onFocus = () => {
    show({ focused: true });
    if (live.current.scripted) {
      stopScript();
      cancelPick();
    }
    resumeWhenIdle();
  };

  const onBlur = () => {
    show({ focused: false });
    resumeWhenIdle();
  };

  const labels = Array.from({ length: SLOTS }, (_, slot) => suggestions[scene.set]?.[slot] ?? '');
  const measured = [...new Set(suggestions.flatMap((set) => set.slice(0, SLOTS)))];
  const pillWidth = (label: string) => {
    const width = label.length === 0 ? 0 : widths[label];
    if (width === undefined) return undefined;
    return Math.min(FIELD_WIDTH, PILL_PAD_LEFT + ICON_SIZE + ICON_GAP + PILL_PAD_RIGHT + Math.ceil(width));
  };
  const measure = (label: string, width: number) => {
    setWidths((known) => (known[label] === width ? known : { ...known, [label]: width }));
  };

  return (
    <View style={styles.panel}>
      <Text style={styles.title} numberOfLines={1}>
        {title}
      </Text>

      <View style={styles.mark} pointerEvents="none" {...UNREAD}>
        <Svg width={MARK_SIZE} height={MARK_SIZE} viewBox="0 0 15 15">
          <Path d={SPARKLE} fill={MARK} />
        </Svg>
      </View>

      {labels.map((label, slot) => (
        <Pill
          key={slot}
          slot={slot}
          label={label}
          width={pillWidth(label)}
          stage={scene.picked === slot ? scene.stage : 'idle'}
          raised={scene.picked === slot}
          enabled={scene.picked === null && !scene.clearing}
          reducedMotion={reducedMotion}
          onPress={() => pick(slot, true)}
        />
      ))}

      <Pressable style={styles.plusSlot} onPress={onAttach} accessibilityRole="button" accessibilityLabel="Attach">
        {({ pressed }) => (
          <Animated.View style={[styles.plus, press(pressed)]}>
            <View style={styles.plusH} />
            <View style={styles.plusV} />
          </Animated.View>
        )}
      </Pressable>

      <View style={styles.field}>
        <Animated.View
          pointerEvents="none"
          style={[
            styles.hint,
            { opacity: hasText ? 0 : 1, transitionProperty: 'opacity', transitionDuration: 200, transitionTimingFunction: 'ease-out' },
          ]}
          {...UNREAD}
        >
          <Text style={styles.placeholder} numberOfLines={1}>
            {placeholder}
          </Text>
        </Animated.View>

        {/* The sent text rises and fades; once the field is empty it is back in place at once. */}
        <Animated.View
          style={[
            styles.entry,
            {
              opacity: scene.clearing ? 0 : 1,
              transform: [{ translateY: scene.clearing && !reducedMotion ? -CLEAR_RISE : 0 }],
              transitionProperty: ['opacity', 'transform'],
              transitionDuration: scene.clearing ? CLEAR_TIME * 1000 : 0,
              transitionTimingFunction: 'ease-in',
            },
          ]}
        >
          <TextInput
            value={text}
            onChangeText={write}
            onFocus={onFocus}
            onBlur={onBlur}
            onSubmitEditing={() => send(true)}
            returnKeyType="send"
            selectionColor={CARET}
            cursorColor={CARET}
            accessibilityLabel={placeholder}
            style={styles.input}
          />
        </Animated.View>

        {scene.caretOn && !scene.focused ? (
          // An unseen copy of the text pushes the caret to its end.
          <View style={styles.mirror} pointerEvents="none" {...UNREAD}>
            <Text style={styles.typed} numberOfLines={1}>
              {text}
            </Text>
            <Caret beat={text.length} />
          </View>
        ) : null}

        <Animated.View style={[styles.sendSlot, popStyle]} pointerEvents={hasText && !scene.clearing ? 'auto' : 'none'}>
          <Animated.View
            style={{
              transform: [{ scale: scene.pressed ? PRESS_SCALE : 1 }],
              transitionProperty: 'transform',
              transitionDuration: PRESS_TIME * 1000,
              transitionTimingFunction: 'ease-out',
            }}
          >
            <Pressable onPress={() => send(true)} accessibilityRole="button" accessibilityLabel="Send">
              {({ pressed }) => (
                <Animated.View style={[styles.sendHit, press(pressed)]}>
                  <View style={styles.send}>
                    <Svg width={SEND_SIZE} height={SEND_SIZE} viewBox="0 0 32 32">
                      <Path d={ARROW} fill="none" stroke="#ffffff" strokeWidth={2} strokeLinecap="round" strokeLinejoin="round" />
                    </Svg>
                  </View>
                </Animated.View>
              )}
            </Pressable>
          </Animated.View>
        </Animated.View>
      </View>

      <Text style={styles.footnote} numberOfLines={1} adjustsFontSizeToFit minimumFontScale={0.8}>
        {footnoteParts(footnote, footnoteLinks).map((part, index) => (
          <Text key={index} style={part.link ? styles.footnoteLink : undefined}>
            {part.text}
          </Text>
        ))}
      </Text>

      <View style={styles.ruler} pointerEvents="none" {...UNREAD}>
        {measured.map((label) => (
          <Text
            key={label}
            style={[styles.pillText, styles.measured]}
            numberOfLines={1}
            onLayout={(event) => measure(label, event.nativeEvent.layout.width)}
          >
            {label}
          </Text>
        ))}
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
  },
  title: {
    position: 'absolute',
    left: 0,
    top: TITLE_Y - 10,
    width: PANEL_WIDTH,
    height: 20,
    color: INK,
    fontSize: 15,
    lineHeight: 20,
    fontWeight: '600',
    textAlign: 'center',
  },
  mark: {
    position: 'absolute',
    left: (PANEL_WIDTH - MARK_SIZE) / 2,
    top: MARK_Y - MARK_SIZE / 2,
  },
  slot: {
    position: 'absolute',
    left: FIELD_X,
    transformOrigin: 'left center',
  },
  pill: {
    height: PILL_HEIGHT,
    borderRadius: PILL_HEIGHT / 2,
    backgroundColor: '#ffffff',
  },
  pillClip: {
    position: 'absolute',
    left: 0,
    top: 0,
    right: 0,
    bottom: 0,
    borderRadius: PILL_HEIGHT / 2,
    overflow: 'hidden',
  },
  pillIcon: {
    position: 'absolute',
    left: PILL_PAD_LEFT,
    top: (PILL_HEIGHT - ICON_SIZE) / 2,
  },
  // Wide enough for any text the pill can show, so a text is never squeezed while the pill grows.
  layer: {
    position: 'absolute',
    left: PILL_PAD_LEFT + ICON_SIZE + ICON_GAP,
    top: 0,
    width: FIELD_WIDTH,
    height: PILL_HEIGHT,
    alignItems: 'flex-start',
    justifyContent: 'center',
  },
  pillText: {
    color: PILL_TEXT,
    fontSize: PILL_FONT,
    includeFontPadding: false,
  },
  plusSlot: {
    position: 'absolute',
    left: PLUS_X - 22,
    top: FIELD_TOP,
  },
  plus: {
    width: 44,
    height: 44,
    alignItems: 'center',
    justifyContent: 'center',
  },
  plusH: {
    position: 'absolute',
    width: 15,
    height: 1.6,
    borderRadius: 0.8,
    backgroundColor: PLUS,
  },
  plusV: {
    position: 'absolute',
    width: 1.6,
    height: 15,
    borderRadius: 0.8,
    backgroundColor: PLUS,
  },
  field: {
    position: 'absolute',
    left: FIELD_X,
    top: FIELD_TOP,
    width: FIELD_WIDTH,
    height: FIELD_HEIGHT,
    borderRadius: FIELD_HEIGHT / 2,
    backgroundColor: '#ffffff',
    boxShadow: '0 2px 10px rgba(0, 0, 0, 0.06)',
  },
  hint: {
    position: 'absolute',
    left: FIELD_PAD_X,
    top: 0,
    right: 0,
    height: FIELD_HEIGHT,
    justifyContent: 'center',
  },
  placeholder: {
    color: PLACEHOLDER,
    fontSize: FIELD_FONT,
    includeFontPadding: false,
  },
  // The text stops short of the send button.
  entry: {
    position: 'absolute',
    left: FIELD_PAD_X,
    top: 0,
    width: FIELD_WIDTH - FIELD_PAD_X - FIELD_HEIGHT,
    height: FIELD_HEIGHT,
  },
  input: {
    height: FIELD_HEIGHT,
    padding: 0,
    color: INK,
    fontSize: FIELD_FONT,
  },
  mirror: {
    position: 'absolute',
    left: FIELD_PAD_X,
    top: 0,
    height: FIELD_HEIGHT,
    flexDirection: 'row',
    alignItems: 'center',
  },
  typed: {
    opacity: 0,
    fontSize: FIELD_FONT,
    includeFontPadding: false,
  },
  caret: {
    marginLeft: 1,
    width: 2,
    height: 18,
    borderRadius: 1,
    backgroundColor: CARET,
  },
  sendSlot: {
    position: 'absolute',
    left: FIELD_WIDTH - FIELD_HEIGHT,
    top: 0,
  },
  sendHit: {
    width: FIELD_HEIGHT,
    height: FIELD_HEIGHT,
    alignItems: 'center',
    justifyContent: 'center',
  },
  send: {
    width: SEND_SIZE,
    height: SEND_SIZE,
    borderRadius: SEND_SIZE / 2,
    backgroundColor: SEND,
  },
  footnote: {
    position: 'absolute',
    left: 0,
    top: FOOTNOTE_Y - 7,
    width: PANEL_WIDTH,
    height: 14,
    color: FOOTNOTE,
    fontSize: 11,
    lineHeight: 14,
    textAlign: 'center',
  },
  footnoteLink: {
    color: FOOTNOTE_LINK,
  },
  ruler: {
    position: 'absolute',
    left: 0,
    top: 0,
    width: PANEL_WIDTH,
    opacity: 0,
  },
  measured: {
    position: 'absolute',
    left: 0,
    top: 0,
  },
});
