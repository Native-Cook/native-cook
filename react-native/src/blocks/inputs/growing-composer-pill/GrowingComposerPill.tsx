import { Canvas, Circle, Group, LinearGradient, Path, RadialGradient, Rect, vec } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { type ReactNode, memo, useEffect, useEffectEvent, useRef, useState } from 'react';
import { Pressable, StyleSheet, Text, TextInput, type TextLayoutEvent, View } from 'react-native';
import Animated, {
  Easing,
  ReduceMotion,
  type SharedValue,
  type WithSpringConfig,
  cubicBezier,
  useAnimatedStyle,
  useFrameCallback,
  useReducedMotion,
  useSharedValue,
  withDelay,
  withSpring,
  withTiming,
} from 'react-native-reanimated';

type Props = {
  /** The field's value when the caller owns it; left undefined, the component keeps its own. */
  value?: string;
  /** Types `script` by itself, sends it and starts over. Never opens the keyboard. */
  autoPlay?: boolean;
  placeholder?: string;
  /** What autoPlay types. */
  script?: string;
  /** Shown in the collapsed pill after sending. */
  status?: string;
  onChangeText?: (text: string) => void;
  /** Called with the trimmed message when the send button or the keyboard's send key is used. */
  onSend?: (text: string) => void;
  onAttach?: () => void;
  onVoice?: () => void;
};

type Phase = 'compose' | 'sending' | 'thinking';

const PANEL_W = 350;
const PANEL_H = 420;
const PANEL_RADIUS = 40;

const WIDTH = 320;
// Height of the single-line pill and of the status pill.
const PILL = 48;
const STATUS_W = 140;
const CARD_RADIUS = 22;

const FONT_SIZE = 16;
const LINE = 21;
const MAX_LINES = 6;

const PAD_TOP = 13.5;
const PAD_X = 18;
// Where the text starts while it shares the pill with the buttons.
const INLINE_X = 46;
const INLINE_W = WIDTH - INLINE_X * 2;
const FULL_W = WIDTH - PAD_X * 2;
// The card opens this much before the text would touch the button on the right.
const WRAP_MARGIN = 8;
// Everything in the open card that is not text: the padding above it and the row of buttons below.
const CHROME = 67;
const CARET_H = 19;
// Width kept free at the end of a line for the drawn caret.
const CARET_ROOM = 3;

const BUTTON = 44;
// Distance of a button's centre from the card's side and bottom edges.
const BUTTON_INSET = 24;
const SEND_SIZE = 30;
const SWAP_SCALE = 0.4;
const PRESS_SCALE = 0.86;

// Height, width and corner radius of the card: a soft spring with a little overshoot.
const GROW: WithSpringConfig = { mass: 1, stiffness: 260, damping: 24, reduceMotion: ReduceMotion.Never };
// Microphone and send button trading places.
const SWAP: WithSpringConfig = { mass: 1, stiffness: 420, damping: 24, reduceMotion: ReduceMotion.Never };

// The autoPlay loop, in seconds from its start.
const CARET_AT = 0.5;
const TYPE_AT = 0.8;
// The whole script is typed in this time; a short one is typed no slower than `SLOWEST_KEY` per letter.
const TYPE_SPAN = 4.5;
const SLOWEST_KEY = 0.06;
const PRESS_AT = 5.75;
const SEND_AT = 5.87;
const LOOP = 9.4;

const FADE = 0.22;
// The status label leaves faster than it came, before the placeholder returns.
const STATUS_FADE = 0.12;
// From the send to the collapse into the status pill.
const COLLAPSE_AFTER = 0.26;
const THINK_FOR = 1.87;
const SHIMMER_DELAY = 0.25;
const SHIMMER_SWEEP = 0.8;
// After the field is left empty and unfocused, autoPlay waits this long.
const RESUME_AFTER = 3;

const JITTER = [0, 0.45, -0.2, 0.6, 0.1, -0.3, 0.75];

const INK = '#111114';
const SENT = '#a9a9b4';
const PLACEHOLDER = '#9c9ca8';
const ICON = '#4a4a52';
const CARET = '#2d6bff';
const SEND = '#0e0e10';
const SHADOW = 'rgba(90,63,192,0.2)';

const EASE_OUT = Easing.bezier(0, 0, 0.58, 1);
const CSS_EASE_OUT = cubicBezier(0, 0, 0.58, 1);
const CSS_PRESS = cubicBezier(0.23, 1, 0.32, 1);

// Outline microphone in a 20 × 20 box.
const MIC =
  'M7.25 5.25A2.75 2.75 0 0 1 12.75 5.25L12.75 9.25A2.75 2.75 0 0 1 7.25 9.25ZM4.5 9.5A5.5 5.5 0 0 0 15.5 9.5M10 15L10 17.75';
// Up arrow in the 30 × 30 send button.
const ARROW = 'M15 21L15 9.5M10 14.25L15 9.25L20 14.25';

// The pastel panel's soft blobs of colour: centre as a share of the panel, radius in points.
const BLOBS = [
  { color: '#a9d4f7', x: 0.22, y: 0.1, radius: 230 },
  { color: '#c9b8f5', x: 0.98, y: 0.22, radius: 240 },
  { color: '#f5b8d8', x: 0.82, y: 0.96, radius: 250 },
  { color: '#fafaff', x: 0.06, y: 0.6, radius: 190 },
  { color: '#b3a2f0', x: 0.3, y: 1.08, radius: 170 },
];

// What the hidden copy of the text reports: its lines at the open card's width, and whether it has
// outgrown the pill.
const UNMEASURED = { rows: [''], wide: false };

function ease(seconds: number) {
  return { duration: seconds * 1000, easing: EASE_OUT, reduceMotion: ReduceMotion.Never };
}

/** Reduced motion trades the springs for a short ease. */
function spring(to: number, config: WithSpringConfig, reduced: boolean) {
  return reduced ? withTiming(to, ease(0.2)) : withSpring(to, config);
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

/** Opacity of one status letter while the band of light, `sweep` 0…1 across the word, passes it. */
function shimmer(index: number, count: number, sweep: number) {
  'worklet';
  if (sweep < 0 || count <= 0) return 1;
  const x = (index + 0.5) / count;
  const centre = -0.25 + 1.5 * sweep;
  const distance = (x - centre) / 0.2;
  return 1 - 0.72 * Math.exp(-distance * distance);
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

/** One of the two buttons on the right: `shown` runs 0…1, past 1 while its spring overshoots. */
function swapStyle(shown: number, reduced: boolean) {
  'worklet';
  return {
    opacity: Math.min(1, Math.max(0, shown)),
    transform: [{ scale: reduced ? 1 : SWAP_SCALE + (1 - SWAP_SCALE) * shown }],
  };
}

/** A shared value that goes after `target` on the card's spring. */
function useGrow(target: number, reduced: boolean) {
  const value = useSharedValue(target);
  useEffect(() => {
    value.set(spring(target, GROW, reduced));
  }, [reduced, target, value]);
  return value;
}

/** The pastel panel the composer floats on: a lavender-to-sky wash with soft blobs of colour. */
function Backdrop() {
  const panel = { rect: { x: 0, y: 0, width: PANEL_W, height: PANEL_H }, rx: PANEL_RADIUS, ry: PANEL_RADIUS };
  return (
    <Canvas style={styles.backdrop}>
      <Group clip={panel}>
        <Rect x={0} y={0} width={PANEL_W} height={PANEL_H}>
          <LinearGradient start={vec(0, 0)} end={vec(PANEL_W, PANEL_H)} colors={['#cfc2f8', '#b4d0f8']} />
        </Rect>
        {BLOBS.map((blob) => (
          <Rect key={blob.color} x={0} y={0} width={PANEL_W} height={PANEL_H}>
            <RadialGradient
              c={vec(blob.x * PANEL_W, blob.y * PANEL_H)}
              r={blob.radius}
              colors={[blob.color, `${blob.color}00`]}
            />
          </Rect>
        ))}
      </Group>
    </Canvas>
  );
}

function IconButton({ label, onPress, children }: { label: string; onPress?: () => void; children: ReactNode }) {
  return (
    <Pressable style={styles.button} onPress={onPress} accessibilityRole="button" accessibilityLabel={label}>
      {({ pressed }) => (
        <Animated.View
          pointerEvents="none"
          style={[
            styles.face,
            {
              transform: [{ scale: pressed ? PRESS_SCALE : 1 }],
              transitionProperty: 'transform',
              transitionDuration: 130,
              transitionTimingFunction: CSS_PRESS,
            },
          ]}
        >
          {children}
        </Animated.View>
      )}
    </Pressable>
  );
}

/**
 * The caret autoPlay draws after the last letter, since the field itself is not focused. Memoised,
 * like the status: every render registers the frame clock again, which costs it a frame.
 */
const Caret = memo(function Caret({ idle }: { idle: SharedValue<number> }) {
  useFrameCallback((frame) => {
    idle.set(idle.get() + (frame.timeSincePreviousFrame ?? 0) / 1000);
  });
  const style = useAnimatedStyle(() => ({ opacity: blink(idle.get()) }));
  return <Animated.View style={[styles.caret, style]} />;
});

function Letter({ char, index, count, elapsed }: { char: string; index: number; count: number; elapsed: SharedValue<number> }) {
  const style = useAnimatedStyle(() => {
    const t = elapsed.get() - SHIMMER_DELAY;
    return { opacity: shimmer(index, count, t > 0 ? (t / SHIMMER_SWEEP) % 1 : -1) };
  });
  return <Animated.Text style={[styles.status, style]}>{char}</Animated.Text>;
}

/** The status, one letter at a time so a band of light can sweep across it while `active`. */
const Status = memo(function Status({ text, active }: { text: string; active: boolean }) {
  // Seconds since the status appeared.
  const elapsed = useSharedValue(0);
  const clock = useFrameCallback((frame) => {
    elapsed.set(elapsed.get() + (frame.timeSincePreviousFrame ?? 0) / 1000);
  }, false);

  useEffect(() => {
    if (active) elapsed.set(0);
    clock.setActive(active);
  }, [active, clock, elapsed]);

  const letters = Array.from(text);
  return (
    <View style={styles.statusRow}>
      {letters.map((char, index) => (
        <Letter key={index} char={char} index={index} count={letters.length} elapsed={elapsed} />
      ))}
    </View>
  );
});

/**
 * Chat composer that starts as a white pill and grows with its text. While the text fits beside the
 * buttons it stays a pill; once it does not, the pill opens into a card whose height springs one
 * line at a time, the corners ease from a full pill to a rounded rectangle and the buttons drop
 * onto their own row. The send button replaces the microphone with the first character. Sending
 * fades the text, collapses the card into a short status pill with a shimmer, then brings the empty
 * field back.
 */
export function GrowingComposerPill({
  value,
  autoPlay = false,
  placeholder = 'Ask anything',
  script = 'Can you help me find a great housewarming gift for my friend? maybe handmade, ceramic dinnerware, in white and tan under $100',
  status = 'Thinking…',
  onChangeText,
  onSend,
  onAttach,
  onVoice,
}: Props) {
  const reducedMotion = useReducedMotion();
  const input = useRef<TextInput>(null);

  const [draft, setDraft] = useState('');
  // What autoPlay has typed so far; the caller's value is never touched by the script.
  const [typed, setTyped] = useState('');
  // True while autoPlay owns the field.
  const [playing, setPlaying] = useState(autoPlay);
  const [caretOn, setCaretOn] = useState(false);
  const [phase, setPhase] = useState<Phase>('compose');
  // The send button's dip when autoPlay presses it.
  const [pressed, setPressed] = useState(false);
  const [focused, setFocused] = useState(false);
  // Lines the keyboard's text takes, measured from the field itself.
  const [fieldLines, setFieldLines] = useState(1);
  const [measured, setMeasured] = useState(UNMEASURED);
  const [seen, setSeen] = useState({ autoPlay, script });

  const reset = () => {
    setTyped('');
    setMeasured(UNMEASURED);
    setCaretOn(false);
    setPressed(false);
    setPhase('compose');
  };

  // A change of `autoPlay`, or a new script, starts over from the empty field.
  if (seen.autoPlay !== autoPlay || seen.script !== script) {
    setSeen({ autoPlay, script });
    if (seen.autoPlay !== autoPlay) setPlaying(autoPlay);
    reset();
  }

  const current = value ?? draft;
  const shown = playing ? typed : current;
  const hasText = shown !== '';
  const composing = phase === 'compose';
  const thinking = phase === 'thinking';
  // The text no longer fits beside the buttons: it moves to the top and the buttons to their own
  // row. The measurement arrives a frame after the text, so an emptied field is answered without it.
  const expanded = !thinking && hasText && measured.wide;
  // The script's text line by line, so the drawn caret can follow the last one.
  const rows = expanded ? measured.rows.slice(-MAX_LINES) : [typed];
  const lines = playing ? rows.length : fieldLines;
  const micOn = !hasText && !thinking;
  const sendOn = hasText && !thinking;

  const width = useGrow(thinking ? STATUS_W : WIDTH, reducedMotion);
  const height = useGrow(expanded ? Math.min(lines, MAX_LINES) * LINE + CHROME : PILL, reducedMotion);
  const radius = useGrow(expanded ? CARD_RADIUS : PILL / 2, reducedMotion);
  const textX = useGrow(expanded ? PAD_X : INLINE_X, reducedMotion);
  const plus = useGrow(thinking ? 0 : 1, reducedMotion);
  const mic = useSharedValue(micOn ? 1 : 0);
  const arrow = useSharedValue(sendOn ? 1 : 0);
  const editor = useSharedValue(1);
  const label = useSharedValue(0);
  // Seconds since the script's last key landed.
  const idle = useSharedValue(0);

  const hadText = useRef(hasText);
  useEffect(() => {
    // The two buttons trade places on their own spring when the text appears or goes; coming back
    // from the status they ride the card's.
    const config = hadText.current === hasText ? GROW : SWAP;
    hadText.current = hasText;
    mic.set(spring(micOn ? 1 : 0, config, reducedMotion));
    arrow.set(spring(sendOn ? 1 : 0, config, reducedMotion));
  }, [hasText, micOn, sendOn, reducedMotion, mic, arrow]);

  useEffect(() => {
    // Coming back from the status, the field waits for the status to clear.
    editor.set(composing ? withDelay(STATUS_FADE * 1000, withTiming(1, ease(FADE)), ReduceMotion.Never) : withTiming(0, ease(FADE)));
  }, [composing, editor]);

  useEffect(() => {
    label.set(thinking ? withDelay(STATUS_FADE * 1000, withTiming(1, ease(FADE)), ReduceMotion.Never) : withTiming(0, ease(STATUS_FADE)));
  }, [thinking, label]);

  const change = (next: string) => {
    if (value === undefined) setDraft(next);
    onChangeText?.(next);
  };

  // The text has faded: clear it and show the status.
  const collapse = useEffectEvent(() => {
    if (playing) {
      setTyped('');
      setMeasured(UNMEASURED);
    } else {
      change('');
    }
    setCaretOn(false);
    setPhase('thinking');
  });

  useEffect(() => {
    if (phase === 'compose') return;
    const timer = setTimeout(
      () => {
        if (phase === 'sending') collapse();
        else setPhase('compose');
      },
      (phase === 'sending' ? COLLAPSE_AFTER : THINK_FOR) * 1000,
    );
    return () => clearTimeout(timer);
  }, [phase]);

  // The autoPlay loop. Every step waits for its own moment after the loop began, so the period stays `LOOP`.
  useEffect(() => {
    if (!playing) return;
    const letters = Array.from(script);
    const steps = [
      {
        at: CARET_AT,
        run: () => {
          idle.set(0);
          setCaretOn(true);
        },
      },
      ...rhythm(letters).map((at, index) => ({
        at: TYPE_AT + at,
        run: () => {
          setTyped(letters.slice(0, index + 1).join(''));
          idle.set(0);
        },
      })),
      { at: PRESS_AT, run: () => setPressed(true) },
      {
        at: SEND_AT,
        run: () => {
          setPressed(false);
          if (script.trim() !== '') setPhase('sending');
        },
      },
    ];

    let timer: ReturnType<typeof setTimeout>;
    const wait = (start: number, index: number) => {
      const over = index === steps.length;
      const due = start + (over ? LOOP : steps[index].at) * 1000;
      timer = setTimeout(
        () => {
          if (over) {
            wait(due, 0);
          } else {
            steps[index].run();
            wait(start, index + 1);
          }
        },
        Math.max(0, due - performance.now()),
      );
    };
    wait(performance.now(), 0);
    return () => clearTimeout(timer);
  }, [playing, script, idle]);

  const resume = useEffectEvent(() => {
    if (current === '' && composing) setPlaying(true);
  });

  // autoPlay comes back once the field has been left empty and unfocused for a while.
  useEffect(() => {
    if (!autoPlay || playing || focused) return;
    const timer = setTimeout(() => resume(), RESUME_AFTER * 1000);
    return () => clearTimeout(timer);
  }, [autoPlay, playing, focused]);

  /** A touch stops the script and leaves an empty field behind. */
  const takeOver = () => {
    if (!playing) return;
    setPlaying(false);
    reset();
  };

  const send = () => {
    const message = current.trim();
    if (!composing || message === '') return;
    if (process.env.EXPO_OS !== 'web') Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
    onSend?.(message);
    setPhase('sending');
  };

  const onMeasure = (event: TextLayoutEvent) => {
    const { lines: found } = event.nativeEvent;
    const texts = found.map((line) => line.text);
    // A text that ends in a line break has one more, empty line, which is not always reported.
    if (texts.length === 0 || texts[texts.length - 1].endsWith('\n')) texts.push('');
    setMeasured({
      rows: texts.map((text) => text.replace(/\n$/, '')),
      wide: texts.length > 1 || (found[0]?.width ?? 0) > INLINE_W - WRAP_MARGIN,
    });
  };

  const cardStyle = useAnimatedStyle(() => ({ width: width.get(), height: height.get(), borderRadius: radius.get() }));
  const clipStyle = useAnimatedStyle(() => ({ borderRadius: radius.get() }));
  const editorStyle = useAnimatedStyle(() => ({ opacity: editor.get(), transform: [{ translateX: textX.get() }] }));
  const plusStyle = useAnimatedStyle(() => ({ opacity: Math.min(1, Math.max(0, plus.get())) }));
  const micStyle = useAnimatedStyle(() => swapStyle(mic.get(), reducedMotion));
  const arrowStyle = useAnimatedStyle(() => swapStyle(arrow.get(), reducedMotion));
  const labelStyle = useAnimatedStyle(() => ({ opacity: label.get() }));

  const color = composing ? INK : SENT;

  return (
    <View style={styles.panel}>
      <Backdrop />
      <Animated.View style={[styles.card, cardStyle]}>
        <Animated.View style={[styles.clip, clipStyle]}>
          {/* A hidden copy of the text at the open card's width: where it breaks, and whether it still fits in the pill. */}
          <Text
            style={[styles.text, styles.measure]}
            onTextLayout={onMeasure}
            accessible={false}
            importantForAccessibility="no"
            pointerEvents="none"
          >
            {shown}
          </Text>

          <Animated.View style={[styles.editor, editorStyle]}>
            <Text style={[styles.text, styles.placeholder, { opacity: hasText || caretOn ? 0 : 1 }]} numberOfLines={1}>
              {placeholder}
            </Text>
            <TextInput
              ref={input}
              style={[styles.text, styles.field, { color, opacity: playing ? 0 : 1 }]}
              value={current}
              onChangeText={change}
              multiline
              scrollEnabled={fieldLines > MAX_LINES}
              submitBehavior="submit"
              returnKeyType="send"
              onSubmitEditing={send}
              onContentSizeChange={(event) => setFieldLines(Math.max(1, Math.round(event.nativeEvent.contentSize.height / LINE)))}
              onFocus={() => {
                setFocused(true);
                takeOver();
              }}
              onBlur={() => setFocused(false)}
              selectionColor={CARET}
              cursorColor={CARET}
              underlineColorAndroid="transparent"
            />
            {playing && (
              <View pointerEvents="none" style={styles.mirror}>
                {rows.map((row, index) => (
                  <View key={index} style={styles.row}>
                    <Text style={[styles.text, { color }]} numberOfLines={1} ellipsizeMode="clip">
                      {row}
                    </Text>
                    {caretOn && index === rows.length - 1 && <Caret idle={idle} />}
                  </View>
                ))}
              </View>
            )}
          </Animated.View>

          <Animated.View style={[styles.left, plusStyle]} pointerEvents={thinking ? 'none' : 'auto'}>
            <IconButton label="Attach" onPress={onAttach}>
              <View style={styles.plusH} />
              <View style={styles.plusV} />
            </IconButton>
          </Animated.View>

          <Animated.View style={[styles.right, micStyle]} pointerEvents={micOn ? 'auto' : 'none'}>
            <IconButton label="Dictate" onPress={onVoice}>
              <Canvas style={styles.mic}>
                <Path path={MIC} style="stroke" strokeWidth={1.6} strokeCap="round" strokeJoin="round" color={ICON} />
              </Canvas>
            </IconButton>
          </Animated.View>

          <Animated.View style={[styles.right, arrowStyle]} pointerEvents={sendOn && composing ? 'auto' : 'none'}>
            <Animated.View
              style={{
                transform: [{ scale: pressed ? PRESS_SCALE : 1 }],
                transitionProperty: 'transform',
                transitionDuration: 120,
                transitionTimingFunction: CSS_EASE_OUT,
              }}
            >
              <IconButton label="Send" onPress={send}>
                <Canvas style={styles.send}>
                  <Circle cx={SEND_SIZE / 2} cy={SEND_SIZE / 2} r={SEND_SIZE / 2} color={SEND} />
                  <Path path={ARROW} style="stroke" strokeWidth={2} strokeCap="round" strokeJoin="round" color="#ffffff" />
                </Canvas>
              </IconButton>
            </Animated.View>
          </Animated.View>

          <Animated.View pointerEvents="none" style={[styles.statusSlot, labelStyle]}>
            <Status text={status} active={thinking && !reducedMotion} />
          </Animated.View>

          {/* While autoPlay runs, a touch anywhere on the composer hands the field to the keyboard. */}
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
        </Animated.View>
      </Animated.View>
    </View>
  );
}

const styles = StyleSheet.create({
  panel: {
    width: PANEL_W,
    height: PANEL_H,
    alignItems: 'center',
    justifyContent: 'center',
  },
  backdrop: {
    position: 'absolute',
    left: 0,
    top: 0,
    width: PANEL_W,
    height: PANEL_H,
  },
  card: {
    backgroundColor: '#ffffff',
    boxShadow: `0 10px 28px ${SHADOW}`,
  },
  // Everything on the card, cut to its outline; the shadow stays outside on the card itself.
  clip: {
    position: 'absolute',
    left: 0,
    top: 0,
    right: 0,
    bottom: 0,
    overflow: 'hidden',
  },
  text: {
    color: INK,
    fontSize: FONT_SIZE,
    lineHeight: LINE,
    includeFontPadding: false,
  },
  measure: {
    position: 'absolute',
    left: 0,
    top: 0,
    width: FULL_W - CARET_ROOM,
    opacity: 0,
  },
  // The field keeps the open card's width in the pill too, where the text is short anyway: a letter
  // that lands a frame before the card opens then never wraps inside the pill.
  editor: {
    position: 'absolute',
    left: 0,
    top: PAD_TOP,
    width: FULL_W,
  },
  placeholder: {
    position: 'absolute',
    left: 0,
    top: 0,
    width: INLINE_W,
    color: PLACEHOLDER,
  },
  field: {
    maxHeight: MAX_LINES * LINE,
    padding: 0,
    // iOS gives a multiline field 5 points of top padding unless this edge is named.
    paddingTop: 0,
    textAlignVertical: 'top',
  },
  // Wider than any line, so a space that hangs past the end of one is not cut off.
  mirror: {
    position: 'absolute',
    left: 0,
    top: 0,
    width: WIDTH,
  },
  row: {
    height: LINE,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 1,
  },
  caret: {
    width: 2,
    height: CARET_H,
    borderRadius: 1,
    backgroundColor: CARET,
  },
  left: {
    position: 'absolute',
    left: BUTTON_INSET - BUTTON / 2,
    bottom: BUTTON_INSET - BUTTON / 2,
  },
  right: {
    position: 'absolute',
    right: BUTTON_INSET - BUTTON / 2,
    bottom: BUTTON_INSET - BUTTON / 2,
  },
  button: {
    width: BUTTON,
    height: BUTTON,
  },
  face: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
  },
  plusH: {
    position: 'absolute',
    width: 16,
    height: 1.7,
    borderRadius: 0.85,
    backgroundColor: ICON,
  },
  plusV: {
    position: 'absolute',
    width: 1.7,
    height: 16,
    borderRadius: 0.85,
    backgroundColor: ICON,
  },
  mic: {
    width: 20,
    height: 20,
  },
  send: {
    width: SEND_SIZE,
    height: SEND_SIZE,
  },
  statusSlot: {
    position: 'absolute',
    left: 0,
    top: 0,
    right: 0,
    bottom: 0,
    alignItems: 'center',
    justifyContent: 'center',
  },
  statusRow: {
    flexDirection: 'row',
  },
  status: {
    color: INK,
    fontSize: FONT_SIZE,
    lineHeight: LINE,
    fontWeight: '500',
    includeFontPadding: false,
  },
  cover: {
    position: 'absolute',
    left: 0,
    top: 0,
    right: 0,
    bottom: 0,
  },
});
