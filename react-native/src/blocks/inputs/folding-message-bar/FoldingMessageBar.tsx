import * as Haptics from 'expo-haptics';
import { LinearGradient } from 'expo-linear-gradient';
import { useEffect, useState } from 'react';
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
import Svg, { Circle, Ellipse, Path } from 'react-native-svg';

type Props = {
  /** The field's text when the parent owns it. Leave it out and the bar keeps its own. */
  value?: string;
  onChangeText?: (text: string) => void;
  /** Writes and sends `script` by itself in a loop, without the keyboard, until the field is tapped. */
  autoPlay?: boolean;
  placeholder?: string;
  /** What autoPlay types. */
  script?: string;
  /** The message already in the thread, with its sender and time. */
  senderName?: string;
  message?: string;
  time?: string;
  /** Label over a sent message. */
  ownName?: string;
  /** Called with the trimmed text when the plane is tapped or the keyboard's send key is pressed. */
  onSend?: (text: string) => void;
  /** Called when the button is tapped while the field is empty. */
  onMic?: () => void;
  onEmoji?: () => void;
  onAttach?: () => void;
  onPhoto?: () => void;
  onCamera?: () => void;
};

const PANEL_W = 370;
const PANEL_H = 330;
const PANEL_R = 34;
// Inset of the bar and of the thread from the panel's edge.
const MARGIN = 12;
const BAR_H = 58;
const BAR_R = 16;
const BAR_PAD = 10;
const FIELD_H = 38;
const FIELD_R = 10;
const GAP = 10;
const TEXT_LEFT = 39;
const TOOL_GAP = 14;
const AVATAR = 22;
const BUBBLE_R = 12;
// A bubble stops this far from the opposite edge of the thread.
const BUBBLE_INSET = 56;
// Space between the bar and the message above it, at rest and for a sent bubble.
const THREAD_GAP = 16;
const SENT_GAP = 14;
// How far the thread moves up for a sent bubble: its label, the bubble and the gap.
const LIFT = 101;
// The sent bubble starts this far down, tucked behind the bar.
const SENT_DROP = 44;
const FOLD_SHIFT = 14;
const FOLD = 260;
const FOLD_STAGGER = 80;
const UNFOLD_STAGGER = 90;
const MIC_DELAY = 300;
const PLACEHOLDER_DELAY = 180;
// How long a sent bubble takes to fade, and how long the thread waits before it drops back.
const FADE = 220;
const DROP_DELAY = 160;
const BLINK = 530;

const CARET = '#5fdbe8';
const TOOL = '#d5e3e3';
const TEAL = '#17b6c5';

const UNFOLD_SPRING = { mass: 1, stiffness: 320, damping: 22 };
const GROW_SPRING = { mass: 1, stiffness: 260, damping: 20 };
const LIFT_SPRING = { mass: 1, stiffness: 200, damping: 22 };

// autoPlay loop, in seconds from its start.
const FOCUS_AT = 0.8;
const TYPE_AT = 1.3;
// The longest the typing may take; a longer script is typed faster.
const TYPE_SPAN = 2.6;
const PRESS_AT = 4.2;
const SEND_AT = 4.4;
const CLEAR_AT = 6.7;
const LOOP = 8;
// After the field is left empty, the loop waits this long before it starts again.
const RESUME = 3;

// Milliseconds before each key, used in turn: uneven like a hand, the same in every loop.
const RHYTHM = [90, 60, 110, 70, 50, 130, 80, 60, 100, 70, 150, 60, 90];

const EASE_OUT = Easing.bezier(0, 0, 0.58, 1);
const EASE_OUT_STRONG = Easing.bezier(0.23, 1, 0.32, 1);
const CSS_EASE_OUT = cubicBezier(0.23, 1, 0.32, 1);

// Outlines drawn in a 20 pt box (the tick in a 10 pt one), the same strings in the three stacks.
const SMILEY =
  'M 17.6 10 C 17.6 14.2 14.2 17.6 10 17.6 C 5.8 17.6 2.4 14.2 2.4 10 C 2.4 5.8 5.8 2.4 10 2.4 C 14.2 2.4 17.6 5.8 17.6 10 Z M 7.5 8.4 L 7.51 8.4 M 12.5 8.4 L 12.51 8.4 M 13.53 11.95 C 12.89 13.32 11.52 14.2 10 14.2 C 8.48 14.2 7.11 13.32 6.47 11.95';
const CLIP =
  'M 14.6 9.93 L 9.86 14.67 C 8.61 15.92 6.58 15.92 5.33 14.67 C 4.08 13.42 4.08 11.39 5.33 10.14 L 10.85 4.63 C 11.71 3.77 13.1 3.77 13.96 4.63 C 14.82 5.49 14.82 6.88 13.96 7.74 L 8.8 12.9 C 8.33 13.37 7.57 13.37 7.1 12.9 C 6.63 12.43 6.63 11.67 7.1 11.2 L 10.99 7.31';
const IMAGE =
  'M 5.4 3.6 L 14.6 3.6 C 16.04 3.6 17.2 4.76 17.2 6.2 L 17.2 13.8 C 17.2 15.24 16.04 16.4 14.6 16.4 L 5.4 16.4 C 3.96 16.4 2.8 15.24 2.8 13.8 L 2.8 6.2 C 2.8 4.76 3.96 3.6 5.4 3.6 Z M 8.7 8 C 8.7 8.77 8.07 9.4 7.3 9.4 C 6.53 9.4 5.9 8.77 5.9 8 C 5.9 7.23 6.53 6.6 7.3 6.6 C 8.07 6.6 8.7 7.23 8.7 8 Z M 3.4 14.6 L 7.4 11 L 10.4 13.8 M 9.6 13 L 12.6 10.4 L 16.8 14';
const CAMERA =
  'M 4.8 5.6 L 15.2 5.6 C 16.53 5.6 17.6 6.67 17.6 8 L 17.6 14 C 17.6 15.33 16.53 16.4 15.2 16.4 L 4.8 16.4 C 3.47 16.4 2.4 15.33 2.4 14 L 2.4 8 C 2.4 6.67 3.47 5.6 4.8 5.6 Z M 6.9 5.6 L 8 3.8 L 12 3.8 L 13.1 5.6 M 12.9 10.9 C 12.9 12.5 11.6 13.8 10 13.8 C 8.4 13.8 7.1 12.5 7.1 10.9 C 7.1 9.3 8.4 8 10 8 C 11.6 8 12.9 9.3 12.9 10.9 Z';
const MIC =
  'M 10 2.4 C 11.44 2.4 12.6 3.56 12.6 5 L 12.6 9.2 C 12.6 10.64 11.44 11.8 10 11.8 C 8.56 11.8 7.4 10.64 7.4 9.2 L 7.4 5 C 7.4 3.56 8.56 2.4 10 2.4 Z M 15.2 9.2 C 15.2 12.07 12.87 14.4 10 14.4 C 7.13 14.4 4.8 12.07 4.8 9.2 M 10 14.4 L 10 17.4';
const PLANE = 'M 4.2 3.9 L 16.8 10 L 4.2 16.1 L 6.4 10 Z M 6.4 10 L 10.8 10';
const TICK = 'M 2.6 5.2 L 4.4 7 L 7.5 3.2';

/** The moment each letter of the script lands, in seconds from the start of the loop. */
function keyTimes(count: number) {
  const sums: number[] = [];
  let total = 0;
  for (let i = 0; i < count; i++) {
    total += RHYTHM[i % RHYTHM.length] / 1000;
    sums.push(total);
  }
  const pace = total > TYPE_SPAN ? TYPE_SPAN / total : 1;
  return sums.map((sum) => TYPE_AT + sum * pace);
}

function Icon({ d, color, size = 20, strokeWidth = 1.5 }: { d: string; color: string; size?: number; strokeWidth?: number }) {
  return (
    <Svg width={size} height={size} viewBox={`0 0 ${size} ${size}`}>
      <Path d={d} stroke={color} strokeWidth={strokeWidth} strokeLinecap="round" strokeLinejoin="round" fill="none" />
    </Svg>
  );
}

function hiddenFromReaders(hidden: boolean) {
  return {
    accessibilityElementsHidden: hidden,
    importantForAccessibility: hidden ? ('no-hide-descendants' as const) : ('auto' as const),
  };
}

/** A plain head-and-shoulders figure in a disc. */
function Avatar({ fill, figure }: { fill: string; figure: string }) {
  return (
    <View style={styles.avatar}>
      <Svg width={AVATAR} height={AVATAR}>
        <Circle cx={11} cy={11} r={11} fill={fill} />
        <Circle cx={11} cy={8.4} r={3.8} fill={figure} />
        <Ellipse cx={11} cy={20.6} rx={7.5} ry={6.5} fill={figure} />
      </Svg>
    </View>
  );
}

type ToolProps = {
  d: string;
  label: string;
  index: number;
  count: number;
  active: boolean;
  reducedMotion: boolean;
  onPress?: () => void;
};

/** One attachment tool: it slides right and fades while a message is being written. */
function Tool({ d, label, index, count, active, reducedMotion, onPress }: ToolProps) {
  const folded = useSharedValue(active ? 1 : 0);

  useEffect(() => {
    // The camera leaves first and the paperclip comes back first.
    if (active) {
      folded.set(
        withDelay(
          (count - 1 - index) * FOLD_STAGGER,
          withTiming(1, { duration: FOLD, easing: EASE_OUT_STRONG, reduceMotion: ReduceMotion.Never }),
        ),
      );
    } else {
      folded.set(
        withDelay(
          index * UNFOLD_STAGGER,
          reducedMotion
            ? withTiming(0, { duration: FOLD, easing: EASE_OUT_STRONG, reduceMotion: ReduceMotion.Never })
            : withSpring(0, UNFOLD_SPRING),
        ),
      );
    }
  }, [active, count, index, reducedMotion, folded]);

  const style = useAnimatedStyle(() => {
    const t = folded.get();
    return {
      opacity: Math.min(1, Math.max(0, 1 - t)),
      transform: reducedMotion ? [] : [{ translateX: FOLD_SHIFT * t }, { scale: 1 - 0.2 * t }],
    };
  });

  return (
    <Animated.View style={style}>
      <Pressable onPress={onPress} hitSlop={6} accessibilityRole="button" accessibilityLabel={label}>
        <Icon d={d} color={TOOL} />
      </Pressable>
    </Animated.View>
  );
}

/** The bar's own caret, shown while the script types. `beat` restarts the blink at every key. */
function Caret({ beat }: { beat: number }) {
  const on = useSharedValue(1);

  useEffect(() => {
    const jump = { duration: 1, reduceMotion: ReduceMotion.Never };
    on.set(1);
    on.set(withRepeat(withSequence(withDelay(BLINK, withTiming(0, jump)), withDelay(BLINK, withTiming(1, jump))), -1));
  }, [beat, on]);

  const style = useAnimatedStyle(() => ({ opacity: on.get() }));
  return <Animated.View style={[styles.caret, style]} />;
}

/**
 * Chat composer on a teal panel. While a message is written its three attachment tools fold away
 * to the right and the microphone turns into a send plane; sending lifts the text out of the field
 * as a white bubble that grows from the composer's top-right corner, and the tools unfold again.
 */
export function FoldingMessageBar({
  value,
  onChangeText,
  autoPlay = false,
  placeholder = 'Message...',
  script = 'Here is my order ID 1209475822',
  senderName = 'Customer Service',
  message = 'Can you send me your order ID so I can check right away?',
  time = '09:52 PM',
  ownName = 'You',
  onSend,
  onMic,
  onEmoji,
  onAttach,
  onPhoto,
  onCamera,
}: Props) {
  const reducedMotion = useReducedMotion();
  const controlled = value != null;
  const auto = autoPlay && !controlled && !reducedMotion;

  const [inner, setInner] = useState('');
  const [focused, setFocused] = useState(false);
  // A touch on the field hands the bar to the user until it has been left empty for a while.
  const [taken, setTaken] = useState(false);
  // The script's stand-in for focus, and its finger on the button.
  const [engaged, setEngaged] = useState(false);
  const [pressed, setPressed] = useState(false);
  // The last message sent; `count` tells two sends of the same text apart.
  const [outbox, setOutbox] = useState<{ text: string; count: number } | null>(null);
  const [shown, setShown] = useState(false);

  const text = value ?? inner;
  // The script is running: the field is not focused and the bar draws its own caret.
  const playing = auto && !taken;
  // Folded: someone is writing, or has written.
  const active = playing ? engaged : focused || text.length > 0;
  const idle = auto && taken && !focused && text.length === 0;

  const hint = useSharedValue(1);
  const morph = useSharedValue(0);
  const ghost = useSharedValue(1);
  const grow = useSharedValue(0);
  const fade = useSharedValue(0);
  const label = useSharedValue(0);
  const lift = useSharedValue(0);

  useEffect(() => {
    // The placeholder waits for the sent text to clear the field before it returns.
    hint.set(
      withDelay(
        active ? 0 : PLACEHOLDER_DELAY,
        withTiming(active ? 0 : 1, { duration: 150, easing: EASE_OUT, reduceMotion: ReduceMotion.Never }),
      ),
    );
    // The microphone comes back last, after the tools have unfolded.
    morph.set(
      withDelay(
        active ? 0 : MIC_DELAY,
        withTiming(active ? 1 : 0, { duration: 200, easing: EASE_OUT_STRONG, reduceMotion: ReduceMotion.Never }),
      ),
    );
  }, [active, hint, morph]);

  // The text leaves the field and comes back as a bubble above the bar.
  useEffect(() => {
    if (!outbox) return;
    const timing = { easing: EASE_OUT, reduceMotion: ReduceMotion.Never };
    ghost.set(0);
    ghost.set(withTiming(1, { duration: 200, ...timing }));
    grow.set(0);
    grow.set(reducedMotion ? 1 : withSpring(1, GROW_SPRING));
    fade.set(0);
    fade.set(withTiming(1, { duration: 200, ...timing }));
    // The label follows its bubble in.
    label.set(0);
    label.set(withDelay(220, withTiming(1, { duration: 220, ...timing })));
    lift.set(reducedMotion ? withTiming(1, { duration: 250, ...timing }) : withSpring(1, LIFT_SPRING));
  }, [outbox, reducedMotion, ghost, grow, fade, label, lift]);

  useEffect(() => {
    if (shown) return;
    const timing = { easing: EASE_OUT, reduceMotion: ReduceMotion.Never };
    fade.set(withTiming(0, { duration: FADE, ...timing }));
    label.set(withTiming(0, { duration: FADE, ...timing }));
    // On the way down the thread waits for the sent bubble to fade.
    lift.set(
      reducedMotion ? withTiming(0, { duration: 250, ...timing }) : withDelay(DROP_DELAY, withSpring(0, LIFT_SPRING)),
    );
  }, [shown, reducedMotion, fade, label, lift]);

  // One loop after another, every step at its own moment after the loop began.
  useEffect(() => {
    if (!playing) return;
    const keys = keyTimes(script.length);
    let timers: ReturnType<typeof setTimeout>[] = [];
    const run = (start: number) => {
      const at = (seconds: number, step: () => void) => {
        timers.push(setTimeout(step, Math.max(0, start + seconds * 1000 - performance.now())));
      };
      timers = [];
      at(FOCUS_AT, () => setEngaged(true));
      keys.forEach((key, index) => at(key, () => setInner(script.slice(0, index + 1))));
      at(PRESS_AT, () => setPressed(true));
      at(SEND_AT, () => {
        setPressed(false);
        setEngaged(false);
        setInner('');
        setOutbox((last) => ({ text: script, count: (last?.count ?? 0) + 1 }));
        setShown(true);
      });
      at(CLEAR_AT, () => setShown(false));
      at(LOOP, () => run(start + LOOP * 1000));
    };
    run(performance.now());
    return () => timers.forEach(clearTimeout);
  }, [playing, script]);

  // The loop starts over from rest a few seconds after the field is left empty.
  useEffect(() => {
    if (!idle) return;
    let restart: ReturnType<typeof setTimeout> | undefined;
    const wait = setTimeout(() => {
      setShown(false);
      restart = setTimeout(() => setTaken(false), 700);
    }, RESUME * 1000);
    return () => {
      clearTimeout(wait);
      clearTimeout(restart);
    };
  }, [idle]);

  const write = (next: string) => {
    if (!controlled) setInner(next);
    onChangeText?.(next);
  };

  /** A touch on the field ends the script: the field is emptied and the keyboard takes over. */
  const onFocus = () => {
    setFocused(true);
    if (!playing) return;
    setTaken(true);
    setEngaged(false);
    setPressed(false);
    setInner('');
    setShown(false);
  };

  const submit = () => {
    const sentText = text.trim();
    if (sentText.length === 0) return;
    if (process.env.EXPO_OS !== 'web') Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
    onSend?.(sentText);
    write('');
    setOutbox((last) => ({ text: sentText, count: (last?.count ?? 0) + 1 }));
    setShown(true);
  };

  const onButton = () => {
    if (playing) return;
    if (text.length === 0) onMic?.();
    else submit();
  };

  const hintStyle = useAnimatedStyle(() => ({ opacity: hint.get() }));
  // The glyph going out shrinks and turns away.
  const micStyle = useAnimatedStyle(() => {
    const m = morph.get();
    return { opacity: 1 - m, transform: reducedMotion ? [] : [{ rotate: `${30 * m}deg` }, { scale: 1 - 0.5 * m }] };
  });
  const planeStyle = useAnimatedStyle(() => {
    const m = morph.get();
    return { opacity: m, transform: reducedMotion ? [] : [{ rotate: `${-30 * (1 - m)}deg` }, { scale: 0.5 + 0.5 * m }] };
  });
  const ghostStyle = useAnimatedStyle(() => {
    const t = ghost.get();
    return { opacity: 1 - t, transform: [{ translateY: reducedMotion ? 0 : -12 * t }] };
  });
  const liftStyle = useAnimatedStyle(() => ({ transform: [{ translateY: -LIFT * lift.get() }] }));
  const labelStyle = useAnimatedStyle(() => ({ opacity: label.get() }));
  // The bubble starts small at the composer's top-right corner, behind the bar.
  const bubbleStyle = useAnimatedStyle(() => {
    const g = grow.get();
    return {
      opacity: fade.get(),
      transform: [{ translateY: SENT_DROP * (1 - g) }, { scale: 0.3 + 0.7 * g }],
    };
  });

  const tools = [
    { d: CLIP, label: 'Attach a file', onPress: onAttach },
    { d: IMAGE, label: 'Photo library', onPress: onPhoto },
    { d: CAMERA, label: 'Camera', onPress: onCamera },
  ];
  // A trailing space must keep its width in the row that mirrors the typed text.
  const mirror = text.endsWith(' ') ? `${text.slice(0, -1)} ` : text;

  return (
    <View style={styles.panel}>
      <LinearGradient colors={['#010b0e', '#00222a', '#003e4c']} locations={[0, 0.5, 1]} style={StyleSheet.absoluteFill} />

      <Animated.View style={[styles.incoming, liftStyle]} accessible>
        <View style={styles.nameRow}>
          <Avatar fill="#e8f1f2" figure="#1c4a52" />
          <Text style={styles.name} numberOfLines={1}>
            {senderName}
          </Text>
        </View>
        <View style={styles.incomingBubble}>
          <Text style={styles.message}>{message}</Text>
          <View style={styles.meta}>
            <View style={styles.tickBox}>
              <Icon d={TICK} color="#1b3d44" size={10} strokeWidth={1.4} />
            </View>
            <Text style={styles.time}>{time}</Text>
          </View>
        </View>
      </Animated.View>

      <View style={styles.outgoing} pointerEvents="none" {...hiddenFromReaders(!shown)}>
        <Animated.View style={[styles.nameRow, labelStyle]}>
          <Text style={styles.name} numberOfLines={1}>
            {ownName}
          </Text>
          <Avatar fill={TEAL} figure="#ffffff" />
        </Animated.View>
        <Animated.View style={[styles.sentBubble, bubbleStyle]}>
          <Text style={styles.sentText} numberOfLines={1}>
            {outbox?.text ?? ''}
          </Text>
          <Text style={styles.sentTime}>{time}</Text>
        </Animated.View>
      </View>

      <View style={styles.bar}>
        <View style={styles.field}>
          <View style={styles.textArea}>
            <Animated.Text style={[styles.placeholder, hintStyle]} numberOfLines={1}>
              {placeholder}
            </Animated.Text>
            <Animated.Text style={[styles.ghost, ghostStyle]} numberOfLines={1} {...hiddenFromReaders(true)}>
              {outbox?.text ?? ''}
            </Animated.Text>
            <TextInput
              value={text}
              onChangeText={write}
              onFocus={onFocus}
              onBlur={() => setFocused(false)}
              onSubmitEditing={submit}
              returnKeyType="send"
              selectionColor={CARET}
              accessibilityLabel={placeholder}
              // While the script types, the letters are drawn by the row above it, with its caret.
              style={[styles.input, { color: playing ? 'transparent' : '#ffffff' }]}
            />
            {playing && engaged ? (
              <View style={styles.mirror} pointerEvents="none" {...hiddenFromReaders(true)}>
                <Text style={styles.typed} numberOfLines={1}>
                  {mirror}
                </Text>
                <Caret beat={text.length} />
              </View>
            ) : null}
          </View>

          <Pressable style={styles.emoji} onPress={onEmoji} hitSlop={6} accessibilityRole="button" accessibilityLabel="Emoji">
            <Icon d={SMILEY} color={TOOL} />
          </Pressable>

          <View style={styles.tools} pointerEvents={active ? 'none' : 'box-none'} {...hiddenFromReaders(active)}>
            {tools.map((tool, index) => (
              <Tool key={tool.label} {...tool} index={index} count={tools.length} active={active} reducedMotion={reducedMotion} />
            ))}
          </View>
        </View>

        <Pressable onPress={onButton} accessibilityRole="button" accessibilityLabel={active ? 'Send' : 'Record a voice message'}>
          {({ pressed: down }) => (
            <Animated.View
              style={[
                styles.button,
                {
                  // The button dips under a finger, or under the script's.
                  transform: [{ scale: down || pressed ? 0.9 : 1 }],
                  transitionProperty: 'transform',
                  transitionDuration: 130,
                  transitionTimingFunction: CSS_EASE_OUT,
                },
              ]}
            >
              <Animated.View style={[styles.glyph, micStyle]}>
                <Icon d={MIC} color={TEAL} strokeWidth={1.7} />
              </Animated.View>
              <Animated.View style={[styles.glyph, planeStyle]}>
                <Icon d={PLANE} color={TEAL} strokeWidth={1.7} />
              </Animated.View>
            </Animated.View>
          )}
        </Pressable>
      </View>

      <View style={styles.rim} pointerEvents="none" />
    </View>
  );
}

const styles = StyleSheet.create({
  panel: {
    width: PANEL_W,
    height: PANEL_H,
    borderRadius: PANEL_R,
    overflow: 'hidden',
  },
  rim: {
    position: 'absolute',
    left: 0,
    right: 0,
    top: 0,
    bottom: 0,
    borderRadius: PANEL_R,
    borderWidth: 1,
    borderColor: 'rgba(255,255,255,0.06)',
  },
  incoming: {
    position: 'absolute',
    left: MARGIN,
    bottom: MARGIN + BAR_H + THREAD_GAP,
    maxWidth: PANEL_W - MARGIN * 2 - BUBBLE_INSET,
    alignItems: 'flex-start',
    gap: 8,
  },
  outgoing: {
    position: 'absolute',
    right: MARGIN,
    bottom: MARGIN + BAR_H + SENT_GAP,
    maxWidth: PANEL_W - MARGIN * 2 - BUBBLE_INSET,
    alignItems: 'flex-end',
    gap: 8,
  },
  nameRow: {
    height: AVATAR,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
  },
  avatar: {
    width: AVATAR,
    height: AVATAR,
    borderRadius: AVATAR / 2,
    overflow: 'hidden',
  },
  name: {
    color: '#ffffff',
    fontSize: 12.5,
    fontWeight: '500',
  },
  incomingBubble: {
    // One point less than the sent bubble's padding: the hairline takes it.
    paddingHorizontal: 12,
    paddingVertical: 9,
    borderRadius: BUBBLE_R,
    borderWidth: 1,
    borderColor: 'rgba(255,255,255,0.06)',
    backgroundColor: 'rgba(255,255,255,0.11)',
    gap: 5,
  },
  message: {
    color: '#ffffff',
    fontSize: 13.5,
    lineHeight: 19,
  },
  meta: {
    height: 14,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 5,
  },
  tickBox: {
    width: 10,
    height: 10,
    borderRadius: 2.5,
    backgroundColor: '#7f9ba2',
  },
  time: {
    color: '#7f9ba2',
    fontSize: 10.5,
  },
  sentBubble: {
    paddingHorizontal: 13,
    paddingVertical: 10,
    borderRadius: BUBBLE_R,
    backgroundColor: '#ffffff',
    gap: 4,
    transformOrigin: 'right bottom',
  },
  sentText: {
    color: '#0c8f9d',
    fontSize: 13.5,
    lineHeight: 19,
    fontWeight: '500',
  },
  sentTime: {
    color: '#7cc7d0',
    fontSize: 10.5,
    lineHeight: 14,
  },
  bar: {
    position: 'absolute',
    left: MARGIN,
    bottom: MARGIN,
    width: PANEL_W - MARGIN * 2,
    height: BAR_H,
    // The hairline takes one point of the padding.
    padding: BAR_PAD - 1,
    borderRadius: BAR_R,
    borderWidth: 1,
    borderColor: 'rgba(255,255,255,0.07)',
    backgroundColor: '#1c454d',
    flexDirection: 'row',
    gap: GAP,
  },
  field: {
    flex: 1,
    height: FIELD_H,
    borderRadius: FIELD_R,
    backgroundColor: '#375c64',
  },
  textArea: {
    position: 'absolute',
    left: TEXT_LEFT,
    right: 12,
    top: 0,
    bottom: 0,
    justifyContent: 'center',
  },
  placeholder: {
    position: 'absolute',
    left: 0,
    right: 0,
    color: '#c9dadd',
    fontSize: 14,
  },
  input: {
    height: FIELD_H,
    paddingVertical: 0,
    fontSize: 14,
  },
  mirror: {
    position: 'absolute',
    left: 0,
    right: 0,
    top: 0,
    bottom: 0,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 1,
  },
  typed: {
    flexShrink: 1,
    color: '#ffffff',
    fontSize: 14,
  },
  caret: {
    width: 2,
    height: 18,
    borderRadius: 1,
    backgroundColor: CARET,
  },
  ghost: {
    position: 'absolute',
    left: 0,
    right: 0,
    color: '#ffffff',
    fontSize: 14,
  },
  emoji: {
    position: 'absolute',
    left: 10,
    top: (FIELD_H - 20) / 2,
  },
  tools: {
    position: 'absolute',
    right: 12,
    top: (FIELD_H - 20) / 2,
    flexDirection: 'row',
    gap: TOOL_GAP,
  },
  button: {
    width: FIELD_H,
    height: FIELD_H,
    borderRadius: FIELD_R,
    backgroundColor: '#ffffff',
  },
  glyph: {
    position: 'absolute',
    left: (FIELD_H - 20) / 2,
    top: (FIELD_H - 20) / 2,
  },
});
