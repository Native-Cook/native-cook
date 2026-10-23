import { Canvas, Circle, Group, Path, RoundedRect } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useEffect, useRef, useState } from 'react';
import {
  Keyboard,
  type LayoutChangeEvent,
  Platform,
  Pressable,
  StyleSheet,
  TextInput,
  type TextStyle,
  View,
  useWindowDimensions,
} from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  Easing,
  ReduceMotion,
  type SharedValue,
  cancelAnimation,
  interpolate,
  interpolateColor,
  useAnimatedReaction,
  useAnimatedStyle,
  useDerivedValue,
  useReducedMotion,
  useSharedValue,
  withDelay,
  withSequence,
  withSpring,
  withTiming,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

export type Mood = {
  /** Under its stop on the slider. */
  label: string;
  /** The giant word behind the slider. */
  word: string;
  /** The whole screen. */
  background: string;
  /** The face, the text and the thumb. */
  ink: string;
  /** The giant word: a shade of the background. */
  shade: string;
  /** The Submit pill. */
  button: string;
};

type Props = {
  /** Three answers, worst first: the face has a shape for each. */
  moods?: [Mood, Mood, Mood];
  /** The answer the form starts on: 0, 1 or 2. */
  initialMood?: number;
  question?: string;
  noteLabel?: string;
  submitLabel?: string;
  thanksTitle?: string;
  thanksText?: string;
  /** Seconds the thank-you stays before the form comes back as it started; 0 keeps it. */
  thanksDuration?: number;
  /** Drags the slider by itself every LOOP seconds. */
  autoPlay?: boolean;
  onMoodChange?: (index: number, mood: Mood) => void;
  /** Called with the answer and the note when Submit is pressed. Nothing is sent from here. */
  onSubmit?: (index: number, note: string) => void;
  onClose?: () => void;
  onInfo?: () => void;
};

const MOODS: [Mood, Mood, Mood] = [
  { label: 'Bad', word: 'BAD', background: '#f67a66', ink: '#6b0b0d', shade: '#e65f4e', button: '#b22423' },
  { label: 'Not bad', word: 'NOT BAD', background: '#f7bc42', ink: '#4a2605', shade: '#e4a62c', button: '#8a5e0f' },
  { label: 'Good', word: 'GOOD', background: '#b8f25a', ink: '#17390a', shade: '#a1db43', button: '#3d6a1c' },
];

// The face at each answer, worst first. Sizes in points; x and y from the centre of the face.
// An eye is a rounded bar: a small disc, a flat bar, a big disc.
const EYE_WIDTH = [62, 106, 136];
const EYE_HEIGHT = [62, 34, 136];
// Each eye's centre, left and right of the middle, and its height on the face.
const EYE_X = [58, 66, 74];
const EYE_Y = [-30, -46, -14];
// The mouth is one stroke: its width, how deep it bows (below zero is a frown) and its centre.
const MOUTH_WIDTH = [66, 62, 62];
const MOUTH_BOW = [-20, -16, 22];
const MOUTH_Y = [12, 18, 68];
const MOUTH_STROKE = 12;
// Half way between the dots and the bars the eyes tilt like sad brows, inner ends up; half way
// between the bars and the discs the mouth tips to one side. Degrees.
const BROW_TILT = 24;
const MOUTH_TILT = 16;
// The smallest an eye gets when its spring overshoots.
const EYE_MIN = 12;
const FACE = 260;

const SIDE = 24;
const HEADER = 56;
const ICON = 44;
const WORD = 60;
const WORD_ROW = 76;
// The slider's line starts and ends this far from the screen's edges.
const TRACK_INSET = 48;
const TRACK = 4;
const STOP = 10;
const THUMB = 44;
// The thumb's own little mouth: width and bow at each answer.
const THUMB_MOUTH = 16;
const THUMB_BOW = [-4.5, -3, 5];
const BAR = 56;
const SUBMIT = 128;
const NOTE = 148;
const NOTE_GAP = 16;
const NOTE_SUBMIT = 44;

// The eyes and the mouth settle on a spring with a little overshoot; the colours and the giant
// word do not overshoot.
const SHAPE_SPRING = { damping: 14, stiffness: 190, mass: 1 };
const TONE_MS = 400;
const WORD_MS = 480;
// Released, the thumb springs to the nearest stop.
const SNAP_SPRING = { damping: 20, stiffness: 260, mass: 1 };
const HELD_SCALE = 1.12;
const NOTE_MS = 320;
const THANKS_MS = 380;
// On Submit the face hops this high, then lands on a spring.
const HOP = 28;
const HOP_SPRING = { damping: 9, stiffness: 240, mass: 1 };

/** autoPlay: one round every LOOP seconds. Each drag starts at `at`, lasts `lasts`, ends on stop `to`. */
export const LOOP = 8;
const DRAGS = [
  { at: 0.8, to: 1, lasts: 0.6 },
  { at: 2.7, to: 0, lasts: 0.6 },
  { at: 4.7, to: 2, lasts: 1.1 },
];
/** After a touch autoPlay waits this long, and for the form to be idle, before it starts over. */
const USER_HOLD = 3;
const RESET_MS = 500;

const IOS = Platform.OS === 'ios';
const NEVER = ReduceMotion.Never;
const STOPS = [0, 1, 2];
const CLOSE = 'M1 1L13 13M13 1L1 13';
const INFO = 'M10 9.2L10 14.2';
const SEND = 'M1 6L15 6M10.5 1.5L15 6L10.5 10.5';

/** 0 at 0 and at 1, 1 half way, flat at both ends. */
function bump(t: number) {
  'worklet';
  const sine = Math.sin(Math.PI * Math.min(1, Math.max(0, t)));
  return sine * sine;
}

function tick() {
  if (process.env.EXPO_OS !== 'web') Haptics.selectionAsync().catch(() => {});
}

/**
 * A feedback form on one flat colour: a face, a giant word and a three-stop slider. Dragging the
 * thumb to another stop tweens the whole screen to that answer's colour, morphs the eyes (dots,
 * bars, discs) and the mouth on a spring, and slides the giant word across. "Add note" opens a
 * text card above the keyboard; Submit thanks the user. Nothing is sent: `onSubmit` is where the
 * request goes.
 */
export function EmojiMoodFeedback({
  moods = MOODS,
  initialMood = 2,
  question = 'How was your shopping\nexperience?',
  noteLabel = 'Add note',
  submitLabel = 'Submit',
  thanksTitle = 'Thank you!',
  thanksText = 'Your feedback helps us get better.',
  thanksDuration = 2.4,
  autoPlay = false,
  onMoodChange,
  onSubmit,
  onClose,
  onInfo,
}: Props) {
  const reducedMotion = useReducedMotion();
  const insets = useSafeAreaInsets();
  const window = useWindowDimensions();
  // The screen as laid out; the window's size stands in until then.
  const [laidOut, setLaidOut] = useState<{ width: number; height: number } | null>(null);
  const size = laidOut ?? window;
  // Where the face rests: the middle of the room between the question and the giant word.
  const [faceSlot, setFaceSlot] = useState({ y: 0, height: FACE });
  const start = Math.min(2, Math.max(0, Math.round(initialMood)));
  const [index, setIndex] = useState(start);
  const [note, setNote] = useState('');
  const [noteOpen, setNoteOpen] = useState(false);
  const [sent, setSent] = useState(false);
  // Changes on every touch, which restarts the autoPlay loop behind a pause.
  const [touches, setTouches] = useState(0);
  const input = useRef<TextInput>(null);
  // The tallest the screen has been: Android may shrink it to make room for the keyboard.
  const fullHeight = useRef(0);

  // The thumb along the line, in stops: 0 is the worst answer, 2 the best.
  const thumb = useSharedValue(start);
  // The face, the colours and the word each follow the nearest stop in their own way.
  const shape = useSharedValue(start);
  const tone = useSharedValue(start);
  const slide = useSharedValue(start);
  // 1 while the thumb is held, by a finger or by autoPlay.
  const held = useSharedValue(0);
  // A finger moved the thumb: only then does a new stop tick.
  const byHand = useSharedValue(false);
  // Where the finger took the thumb, relative to its centre, in points.
  const grip = useSharedValue(0);
  const noteShown = useSharedValue(0);
  const thanks = useSharedValue(0);
  const hop = useSharedValue(0);
  // How much of the screen's bottom the keyboard covers, in points.
  const keyboard = useSharedValue(0);

  const step = (size.width - TRACK_INSET * 2) / 2;
  const mood = moods[index];
  const backgrounds = moods.map((m) => m.background);
  const inks = moods.map((m) => m.ink);
  const shades = moods.map((m) => m.shade);
  const buttons = moods.map((m) => m.button);

  /** A touch takes over from the autoPlay loop, which starts again from rest a while later. */
  const touched = () => setTouches((n) => n + 1);

  const arrived = (next: number, hand: boolean) => {
    setIndex(next);
    if (hand) tick();
    onMoodChange?.(next, moods[next]);
  };

  // Everything but the thumb follows the stop the thumb is nearest to.
  useAnimatedReaction(
    () => Math.round(thumb.get()),
    (now, old) => {
      if (old === null || now === old) return;
      shape.set(
        reducedMotion
          ? withTiming(now, { duration: 220, reduceMotion: NEVER })
          : withSpring(now, { ...SHAPE_SPRING, reduceMotion: NEVER }),
      );
      tone.set(withTiming(now, { duration: TONE_MS, easing: Easing.out(Easing.quad), reduceMotion: NEVER }));
      slide.set(withTiming(now, { duration: WORD_MS, easing: Easing.bezier(0.3, 0, 0.15, 1), reduceMotion: NEVER }));
      scheduleOnRN(arrived, now, byHand.get());
    },
  );

  /** What autoPlay and the stop labels do: carry the thumb to a stop as a finger would. */
  const dragTo = (to: number, seconds: number) => {
    byHand.set(false);
    held.set(withTiming(1, { duration: 120, reduceMotion: NEVER }));
    thumb.set(
      withTiming(to, { duration: seconds * 1000, easing: Easing.inOut(Easing.cubic), reduceMotion: NEVER }, (finished) => {
        if (finished) held.set(withTiming(0, { duration: 180, reduceMotion: NEVER }));
      }),
    );
  };

  const select = (to: number) => {
    touched();
    byHand.set(true);
    thumb.set(withSpring(to, { ...SNAP_SPRING, reduceMotion: NEVER }));
  };

  const pan = Gesture.Pan()
    .minDistance(0)
    .onBegin((event) => {
      cancelAnimation(thumb);
      byHand.set(true);
      held.set(withTiming(1, { duration: 120, reduceMotion: NEVER }));
      const centre = THUMB / 2 + thumb.get() * step;
      // A touch on the thumb keeps hold of it where it is; a touch on the line calls it over.
      const onThumb = Math.abs(event.x - centre) <= THUMB / 2;
      grip.set(onThumb ? centre - event.x : 0);
      if (!onThumb) {
        const to = Math.min(2, Math.max(0, (event.x - THUMB / 2) / step));
        thumb.set(withTiming(to, { duration: 140, easing: Easing.out(Easing.quad), reduceMotion: NEVER }));
      }
      scheduleOnRN(touched);
    })
    .onUpdate((event) => {
      thumb.set(Math.min(2, Math.max(0, (event.x + grip.get() - THUMB / 2) / step)));
    })
    .onFinalize(() => {
      held.set(withTiming(0, { duration: 180, reduceMotion: NEVER }));
      const to = Math.min(2, Math.max(0, Math.round(thumb.get())));
      thumb.set(withSpring(to, { ...SNAP_SPRING, reduceMotion: NEVER }));
      scheduleOnRN(touched);
    });

  const openNote = () => {
    touched();
    setNoteOpen(true);
    noteShown.set(withTiming(1, { duration: NOTE_MS, easing: Easing.out(Easing.cubic), reduceMotion: NEVER }));
  };

  const closeNote = () => {
    Keyboard.dismiss();
    setNoteOpen(false);
    noteShown.set(withTiming(0, { duration: NOTE_MS, easing: Easing.out(Easing.cubic), reduceMotion: NEVER }));
  };

  // The card's field takes the keyboard once the card is there.
  useEffect(() => {
    if (noteOpen) input.current?.focus();
  }, [noteOpen]);

  const submit = () => {
    if (sent) return;
    touched();
    if (process.env.EXPO_OS !== 'web') Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success).catch(() => {});
    closeNote();
    setSent(true);
    onSubmit?.(index, note.trim());
    thanks.set(withTiming(1, { duration: THANKS_MS, easing: Easing.out(Easing.cubic), reduceMotion: NEVER }));
    if (!reducedMotion) {
      hop.set(
        withDelay(
          120,
          withSequence(
            withTiming(-HOP, { duration: 170, easing: Easing.out(Easing.quad), reduceMotion: NEVER }),
            withSpring(0, { ...HOP_SPRING, reduceMotion: NEVER }),
          ),
        ),
      );
    }
  };

  /** Back to the form as it started. */
  const reset = () => {
    setSent(false);
    setNote('');
    thanks.set(withTiming(0, { duration: THANKS_MS, easing: Easing.out(Easing.cubic), reduceMotion: NEVER }));
    byHand.set(false);
    thumb.set(withTiming(start, { duration: RESET_MS, easing: Easing.inOut(Easing.cubic), reduceMotion: NEVER }));
  };

  // The thank-you leaves by itself.
  const latestReset = useRef(reset);
  useEffect(() => {
    latestReset.current = reset;
  });
  useEffect(() => {
    if (!sent || thanksDuration <= 0) return;
    const timer = setTimeout(() => latestReset.current(), thanksDuration * 1000);
    return () => clearTimeout(timer);
  }, [sent, thanksDuration]);

  useEffect(() => {
    const move = (height: number, duration: number) => {
      // What the screen has already given up to the keyboard does not count twice.
      const covered = Math.max(0, height - Math.max(0, fullHeight.current - size.height));
      keyboard.set(withTiming(covered, { duration: duration || 220, easing: Easing.out(Easing.cubic), reduceMotion: NEVER }));
    };
    const show = Keyboard.addListener(IOS ? 'keyboardWillShow' : 'keyboardDidShow', (event) =>
      move(event.endCoordinates.height, event.duration),
    );
    const hide = Keyboard.addListener(IOS ? 'keyboardWillHide' : 'keyboardDidHide', (event) => move(0, event?.duration ?? 0));
    return () => {
      show.remove();
      hide.remove();
    };
  }, [keyboard, size.height]);

  // The loop reads the latest state when its timers fire.
  const latest = useRef({ dragTo, reset, busy: false });
  useEffect(() => {
    latest.current = { dragTo, reset, busy: noteOpen || sent };
  });

  useEffect(() => {
    if (!autoPlay) return;
    const timers: ReturnType<typeof setTimeout>[] = [];
    const after = (ms: number, run: () => void) => timers.push(setTimeout(run, ms));
    const cycle = (origin: number) => {
      DRAGS.forEach(({ at, to, lasts }) => after(origin + at * 1000 - Date.now(), () => latest.current.dragTo(to, lasts)));
      after(origin + LOOP * 1000 - Date.now(), () => cycle(origin + LOOP * 1000));
    };
    const rest = () => {
      if (latest.current.busy) return after(100, rest);
      // The loop starts over from the answer the form was given.
      latest.current.reset();
      after(RESET_MS + 300, () => cycle(Date.now()));
    };
    if (touches === 0) cycle(Date.now());
    else after(USER_HOLD * 1000, rest);
    return () => timers.forEach(clearTimeout);
  }, [autoPlay, touches]);

  useEffect(
    () => () => {
      cancelAnimation(thumb);
      cancelAnimation(shape);
      cancelAnimation(tone);
      cancelAnimation(slide);
    },
    [thumb, shape, tone, slide],
  );

  const onLayout = (event: LayoutChangeEvent) => {
    const { width, height } = event.nativeEvent.layout;
    fullHeight.current = Math.max(fullHeight.current, height);
    setLaidOut({ width, height });
  };

  const bottom = Math.max(insets.bottom, 16) + 6;
  // The face shrinks on a short screen instead of running into the question.
  const fit = Math.min(1, faceSlot.height / (FACE - 40));
  const faceCentre = faceSlot.y + faceSlot.height / 2;
  // With the note open the face has the room between the header and the card.
  const noteRoomTop = insets.top + 14 + HEADER;
  const thanksCentre = size.height * 0.42;

  const background = useDerivedValue(() => interpolateColor(tone.get(), STOPS, backgrounds));
  const ink = useDerivedValue(() => interpolateColor(tone.get(), STOPS, inks));

  const screenStyle = useAnimatedStyle(() => ({ backgroundColor: background.get() }));
  const inkStyle = useAnimatedStyle(() => ({ color: ink.get() }));
  const inkFill = useAnimatedStyle(() => ({ backgroundColor: ink.get() }));
  const paperStyle = useAnimatedStyle(() => ({ color: background.get() }));
  const shadeStyle = useAnimatedStyle(() => ({ color: interpolateColor(tone.get(), STOPS, shades) }));
  const buttonStyle = useAnimatedStyle(() => ({ backgroundColor: interpolateColor(tone.get(), STOPS, buttons) }));

  // The form steps aside for the note card and for the thank-you.
  const formStyle = useAnimatedStyle(() => ({ opacity: (1 - noteShown.get()) * (1 - thanks.get()) }));
  const headerStyle = useAnimatedStyle(() => ({ opacity: 1 - thanks.get() }));

  const faceStyle = useAnimatedStyle(() => {
    const cardTop = size.height - Math.max(keyboard.get(), bottom) - NOTE_GAP - NOTE;
    const room = cardTop - noteRoomTop;
    const toNote = (noteRoomTop + cardTop) / 2 - faceCentre;
    const toThanks = thanksCentre - faceCentre;
    const n = noteShown.get() * (1 - thanks.get());
    const squeeze = Math.min(1, room / (FACE - 30));
    return {
      transform: [
        { translateY: toNote * n + toThanks * thanks.get() + hop.get() },
        { scale: fit * (1 + (squeeze - 1) * n) },
      ],
    };
  });

  // The eyes and the mouth, read from the tables above at the spring's value: past the ends of
  // the tables the values run on, which is the overshoot.
  const eyeWidth = useDerivedValue(() => Math.max(EYE_MIN, interpolate(shape.get(), STOPS, EYE_WIDTH)));
  const eyeHeight = useDerivedValue(() => Math.max(EYE_MIN, interpolate(shape.get(), STOPS, EYE_HEIGHT)));
  const eyeRadius = useDerivedValue(() => Math.min(eyeWidth.get(), eyeHeight.get()) / 2);
  const eyeTop = useDerivedValue(() => FACE / 2 + interpolate(shape.get(), STOPS, EYE_Y) - eyeHeight.get() / 2);
  const leftEyeX = useDerivedValue(() => size.width / 2 - interpolate(shape.get(), STOPS, EYE_X) - eyeWidth.get() / 2);
  const rightEyeX = useDerivedValue(() => size.width / 2 + interpolate(shape.get(), STOPS, EYE_X) - eyeWidth.get() / 2);
  const leftEyeOrigin = useDerivedValue(() => ({ x: leftEyeX.get() + eyeWidth.get() / 2, y: eyeTop.get() + eyeHeight.get() / 2 }));
  const rightEyeOrigin = useDerivedValue(() => ({ x: rightEyeX.get() + eyeWidth.get() / 2, y: eyeTop.get() + eyeHeight.get() / 2 }));
  // The tilt is a bump between the two lower stops: none on a stop, most half way (sine squared,
  // so the spring's overshoot past a stop barely shows in it).
  const brow = useDerivedValue(() => (reducedMotion ? 0 : (bump(shape.get()) * BROW_TILT * Math.PI) / 180));
  const leftEyeTurn = useDerivedValue(() => [{ rotate: -brow.get() }]);
  const rightEyeTurn = useDerivedValue(() => [{ rotate: brow.get() }]);

  const mouth = useDerivedValue(() => {
    const s = shape.get();
    const half = interpolate(s, STOPS, MOUTH_WIDTH) / 2;
    const bow = interpolate(s, STOPS, MOUTH_BOW);
    const y = FACE / 2 + interpolate(s, STOPS, MOUTH_Y) - bow / 2;
    const x = size.width / 2;
    // A cubic with both handles straight below (or above) its ends: a round U, 3/4 as deep as the handles.
    const handle = y + bow / 0.75;
    return `M${x - half} ${y}C${x - half} ${handle} ${x + half} ${handle} ${x + half} ${y}`;
  });
  const mouthOrigin = useDerivedValue(() => ({ x: size.width / 2, y: FACE / 2 + interpolate(shape.get(), STOPS, MOUTH_Y) }));
  const mouthTurn = useDerivedValue(() => [{ rotate: reducedMotion ? 0 : (bump(shape.get() - 1) * MOUTH_TILT * Math.PI) / 180 }]);

  const thumbStyle = useAnimatedStyle(() => ({
    transform: [{ translateX: thumb.get() * step }, { scale: 1 + (HELD_SCALE - 1) * held.get() }],
  }));
  const thumbMouth = useDerivedValue(() => {
    const bow = interpolate(shape.get(), STOPS, THUMB_BOW);
    const y = THUMB / 2 + 5 - bow / 2;
    const left = THUMB / 2 - THUMB_MOUTH / 2;
    const handle = y + bow / 0.75;
    return `M${left} ${y}C${left} ${handle} ${left + THUMB_MOUTH} ${handle} ${left + THUMB_MOUTH} ${y}`;
  });

  const noteStyle = useAnimatedStyle(() => ({
    opacity: noteShown.get() * (1 - thanks.get()),
    bottom: Math.max(keyboard.get(), bottom) + NOTE_GAP,
    transform: [{ translateY: reducedMotion ? 0 : (1 - noteShown.get()) * 32 }],
  }));
  const thanksStyle = useAnimatedStyle(() => ({
    opacity: thanks.get(),
    transform: [{ translateY: reducedMotion ? 0 : (1 - thanks.get()) * 18 }],
  }));

  const form = !noteOpen && !sent;

  return (
    <Animated.View style={[styles.screen, { paddingTop: insets.top + 14, paddingBottom: bottom }, screenStyle]} onLayout={onLayout}>
      <StatusBar style="dark" />

      <Animated.View style={[styles.header, headerStyle]} pointerEvents={sent ? 'none' : 'auto'}>
        <Pressable
          style={styles.icon}
          hitSlop={6}
          accessibilityRole="button"
          accessibilityLabel="Close"
          onPress={() => {
            touched();
            if (noteOpen) closeNote();
            else onClose?.();
          }}
        >
          <Canvas style={styles.glyph}>
            <Path path={CLOSE} transform={[{ translateX: 3 }, { translateY: 3 }]} style="stroke" strokeWidth={2} strokeCap="round" color={ink} />
          </Canvas>
        </Pressable>
        <Pressable
          style={styles.icon}
          hitSlop={6}
          accessibilityRole="button"
          accessibilityLabel="About this question"
          onPress={() => {
            touched();
            onInfo?.();
          }}
        >
          <Canvas style={styles.glyph}>
            <Circle cx={10} cy={10} r={8.5} style="stroke" strokeWidth={1.8} color={ink} />
            <Path path={INFO} style="stroke" strokeWidth={1.8} strokeCap="round" color={ink} />
            <Circle cx={10} cy={6.1} r={1.1} color={ink} />
          </Canvas>
        </Pressable>
      </Animated.View>

      <Animated.Text style={[styles.question, inkStyle, formStyle]} accessibilityRole="header">
        {question}
      </Animated.Text>

      {/* The room the face rests in. A tap here puts the note away. */}
      <Pressable
        style={styles.faceSlot}
        disabled={!noteOpen}
        accessible={false}
        onPress={closeNote}
        onLayout={(event) => setFaceSlot({ y: event.nativeEvent.layout.y, height: event.nativeEvent.layout.height })}
      >
        <Animated.View style={[styles.face, faceStyle]} accessible accessibilityRole="image" accessibilityLabel={`${mood.label} face`}>
          <Canvas style={styles.fill}>
            <Group transform={leftEyeTurn} origin={leftEyeOrigin}>
              <RoundedRect x={leftEyeX} y={eyeTop} width={eyeWidth} height={eyeHeight} r={eyeRadius} color={ink} />
            </Group>
            <Group transform={rightEyeTurn} origin={rightEyeOrigin}>
              <RoundedRect x={rightEyeX} y={eyeTop} width={eyeWidth} height={eyeHeight} r={eyeRadius} color={ink} />
            </Group>
            <Group transform={mouthTurn} origin={mouthOrigin}>
              <Path path={mouth} style="stroke" strokeWidth={MOUTH_STROKE} strokeCap="round" color={ink} />
            </Group>
          </Canvas>
        </Animated.View>
      </Pressable>

      <Animated.View style={formStyle} pointerEvents={form ? 'auto' : 'none'}>
        {/* The three words lie side by side, a screen apart, in the order of the stops. */}
        <View style={styles.wordRow} accessibilityElementsHidden importantForAccessibility="no-hide-descendants">
          {moods.map((item, i) => (
            <Word key={item.word} index={i} text={item.word} slide={slide} width={size.width} fade={reducedMotion} style={shadeStyle} />
          ))}
        </View>

        <GestureDetector gesture={pan}>
          <View
            style={styles.slider}
            accessible
            accessibilityRole="adjustable"
            accessibilityLabel={question.replace('\n', ' ')}
            accessibilityValue={{ text: mood.label }}
            accessibilityActions={[{ name: 'increment' }, { name: 'decrement' }]}
            onAccessibilityAction={(event) => select(Math.min(2, Math.max(0, index + (event.nativeEvent.actionName === 'increment' ? 1 : -1))))}
          >
            <Animated.View style={[styles.track, inkFill]} />
            {STOPS.map((stop) => (
              <Animated.View key={stop} style={[styles.stop, { left: THUMB / 2 - STOP / 2 + stop * step }, inkFill]} />
            ))}
            <Animated.View style={[styles.thumb, inkFill, thumbStyle]}>
              <Canvas style={styles.fill}>
                <Path path={thumbMouth} style="stroke" strokeWidth={2.6} strokeCap="round" color={background} />
              </Canvas>
            </Animated.View>
          </View>
        </GestureDetector>

        <View style={styles.labels}>
          {moods.map((item, i) => (
            <Pressable
              key={item.label}
              style={[styles.labelSlot, i === 0 && styles.labelFirst, i === 2 && styles.labelLast]}
              hitSlop={10}
              accessibilityRole="button"
              accessibilityState={{ selected: i === index }}
              onPress={() => select(i)}
            >
              <StopLabel index={i} text={item.label} tone={tone} style={inkStyle} />
            </Pressable>
          ))}
        </View>

        <View style={styles.bar}>
          <Animated.View style={[styles.fill, styles.barTint, inkFill]} />
          <Pressable style={styles.noteButton} accessibilityRole="button" onPress={openNote}>
            <Animated.Text style={[styles.noteLabel, inkStyle]} numberOfLines={1}>
              {note.trim() || noteLabel}
            </Animated.Text>
          </Pressable>
          <Pressable accessibilityRole="button" accessibilityLabel={submitLabel} onPress={submit}>
            {({ pressed }) => (
              <Animated.View style={[styles.submit, buttonStyle, { opacity: pressed ? 0.8 : 1 }]}>
                <Animated.Text style={[styles.submitLabel, paperStyle]}>{submitLabel}</Animated.Text>
                <Canvas style={styles.send}>
                  <Path path={SEND} style="stroke" strokeWidth={1.8} strokeCap="round" strokeJoin="round" color={background} />
                </Canvas>
              </Animated.View>
            )}
          </Pressable>
        </View>
      </Animated.View>

      <Animated.View
        style={[styles.note, { borderColor: `${mood.ink}33` }, noteStyle]}
        pointerEvents={noteOpen ? 'auto' : 'none'}
        accessibilityElementsHidden={!noteOpen}
        importantForAccessibility={noteOpen ? 'auto' : 'no-hide-descendants'}
      >
        <TextInput
          ref={input}
          style={[styles.noteInput, { color: mood.ink }]}
          value={note}
          onChangeText={(text) => {
            touched();
            setNote(text);
          }}
          placeholder={noteLabel}
          placeholderTextColor={`${mood.ink}80`}
          selectionColor={mood.ink}
          multiline
          maxLength={280}
          accessibilityLabel={noteLabel}
        />
        <Pressable style={styles.noteSubmitSlot} accessibilityRole="button" accessibilityLabel={submitLabel} onPress={submit}>
          {({ pressed }) => (
            <Animated.View style={[styles.submit, styles.noteSubmit, buttonStyle, { opacity: pressed ? 0.8 : 1 }]}>
              <Animated.Text style={[styles.submitLabel, paperStyle]}>{submitLabel}</Animated.Text>
              <Canvas style={styles.send}>
                <Path path={SEND} style="stroke" strokeWidth={1.8} strokeCap="round" strokeJoin="round" color={background} />
              </Canvas>
            </Animated.View>
          )}
        </Pressable>
      </Animated.View>

      <Animated.View
        style={[styles.thanks, { top: thanksCentre + (FACE / 2) * fit - 4 }, thanksStyle]}
        pointerEvents="none"
        accessible={sent}
        accessibilityLiveRegion="polite"
      >
        <Animated.Text style={[styles.thanksTitle, inkStyle]}>{thanksTitle}</Animated.Text>
        <Animated.Text style={[styles.thanksText, inkStyle]}>{thanksText}</Animated.Text>
      </Animated.View>
    </Animated.View>
  );
}

type Tint = ReturnType<typeof useAnimatedStyle<TextStyle>>;

/** One giant word on the strip: it rides past as `slide` goes from one stop to the next. */
function Word({
  index,
  text,
  slide,
  width,
  fade,
  style,
}: {
  index: number;
  text: string;
  slide: SharedValue<number>;
  width: number;
  fade: boolean;
  style: Tint;
}) {
  // The thumb going left brings the word on its left in from the left edge. Reduced motion
  // cross-fades the words in place.
  const moved = useAnimatedStyle(() => {
    const away = index - slide.get();
    return fade ? { opacity: 1 - Math.min(1, Math.abs(away)) } : { transform: [{ translateX: away * width }] };
  });
  return (
    <Animated.Text style={[styles.word, style, moved]} numberOfLines={1} adjustsFontSizeToFit>
      {text}
    </Animated.Text>
  );
}

/** A stop's name: full ink on the chosen stop, faint on the others. */
function StopLabel({ index, text, tone, style }: { index: number; text: string; tone: SharedValue<number>; style: Tint }) {
  const lit = useAnimatedStyle(() => ({ opacity: 1 - 0.55 * Math.min(1, Math.abs(tone.get() - index)) }));
  return (
    <Animated.Text style={[styles.label, style, lit]} numberOfLines={1}>
      {text}
    </Animated.Text>
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
  header: {
    height: HEADER,
    paddingHorizontal: SIDE,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  icon: {
    width: ICON,
    height: ICON,
    borderRadius: ICON / 2,
    backgroundColor: 'rgba(255,255,255,0.12)',
    alignItems: 'center',
    justifyContent: 'center',
  },
  glyph: {
    width: 20,
    height: 20,
  },
  question: {
    marginTop: 30,
    paddingHorizontal: 40,
    fontSize: 22,
    lineHeight: 28,
    fontWeight: '500',
    letterSpacing: -0.2,
    textAlign: 'center',
  },
  faceSlot: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
  },
  face: {
    alignSelf: 'stretch',
    height: FACE,
  },
  wordRow: {
    height: WORD_ROW,
  },
  word: {
    position: 'absolute',
    left: 12,
    right: 12,
    height: WORD_ROW,
    lineHeight: WORD_ROW,
    fontSize: WORD,
    fontWeight: '900',
    letterSpacing: -2.5,
    textAlign: 'center',
  },
  slider: {
    marginTop: 40,
    marginHorizontal: TRACK_INSET - THUMB / 2,
    height: THUMB,
    justifyContent: 'center',
  },
  track: {
    position: 'absolute',
    left: THUMB / 2,
    right: THUMB / 2,
    height: TRACK,
    borderRadius: TRACK / 2,
    opacity: 0.16,
  },
  stop: {
    position: 'absolute',
    width: STOP,
    height: STOP,
    borderRadius: STOP / 2,
    opacity: 0.22,
  },
  thumb: {
    position: 'absolute',
    left: 0,
    width: THUMB,
    height: THUMB,
    borderRadius: THUMB / 2,
  },
  labels: {
    marginTop: 8,
    marginHorizontal: TRACK_INSET - THUMB / 2,
    flexDirection: 'row',
    justifyContent: 'space-between',
  },
  labelSlot: {
    flex: 1,
    alignItems: 'center',
  },
  labelFirst: {
    alignItems: 'flex-start',
  },
  labelLast: {
    alignItems: 'flex-end',
  },
  label: {
    fontSize: 14,
    lineHeight: 20,
    fontWeight: '500',
  },
  bar: {
    marginTop: 64,
    marginHorizontal: SIDE,
    height: BAR,
    borderRadius: BAR / 2,
    flexDirection: 'row',
    alignItems: 'center',
    overflow: 'hidden',
  },
  barTint: {
    opacity: 0.08,
  },
  noteButton: {
    flex: 1,
    height: BAR,
    paddingLeft: 26,
    paddingRight: 12,
    justifyContent: 'center',
  },
  noteLabel: {
    fontSize: 15,
    lineHeight: 20,
    fontWeight: '500',
  },
  submit: {
    width: SUBMIT,
    height: BAR,
    borderRadius: BAR / 2,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 10,
  },
  submitLabel: {
    fontSize: 15,
    lineHeight: 20,
    fontWeight: '600',
  },
  send: {
    width: 16,
    height: 12,
  },
  note: {
    position: 'absolute',
    left: SIDE,
    right: SIDE,
    height: NOTE,
    borderRadius: 28,
    borderCurve: 'continuous',
    borderWidth: 1.5,
    backgroundColor: 'rgba(255,255,255,0.2)',
  },
  noteInput: {
    flex: 1,
    paddingHorizontal: 20,
    paddingTop: 16,
    paddingBottom: NOTE_SUBMIT + 16,
    fontSize: 16,
    lineHeight: 22,
    textAlignVertical: 'top',
  },
  noteSubmitSlot: {
    position: 'absolute',
    right: 10,
    bottom: 10,
  },
  noteSubmit: {
    width: 112,
    height: NOTE_SUBMIT,
  },
  thanks: {
    position: 'absolute',
    left: 32,
    right: 32,
    alignItems: 'center',
  },
  thanksTitle: {
    fontSize: 34,
    lineHeight: 40,
    fontWeight: '800',
    letterSpacing: -0.6,
    textAlign: 'center',
  },
  thanksText: {
    marginTop: 8,
    fontSize: 16,
    lineHeight: 22,
    fontWeight: '500',
    textAlign: 'center',
    opacity: 0.75,
  },
});
