import { Blur, Canvas, RoundedRect } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { LinearGradient } from 'expo-linear-gradient';
import { type ReactNode, useCallback, useEffect, useRef, useState } from 'react';
import { Pressable, StyleSheet, TextInput, View } from 'react-native';
import Animated, {
  type SharedValue,
  cubicBezier,
  useAnimatedStyle,
  useFrameCallback,
  useReducedMotion,
  useSharedValue,
  withDelay,
  withSpring,
  withTiming,
} from 'react-native-reanimated';
import Svg, { Circle, Path, Rect } from 'react-native-svg';

export type JellyAttachCapsuleKind = 'video' | 'camera' | 'photo';

type Props = {
  /** Controlled text; leave it out and the field keeps its own. */
  value?: string;
  onChangeText?: (text: string) => void;
  /** Opens the capsule, picks the camera and closes again in a loop, without opening the keyboard. */
  autoPlay?: boolean;
  placeholder?: string;
  /** Called with the text when the keyboard's send key is pressed; the field clears. */
  onSend?: (text: string) => void;
  /** Called when one of the three attachment buttons is tapped; the capsule closes after its pulse. */
  onPick?: (kind: JellyAttachCapsuleKind) => void;
};

const WIDTH = 262;
const HEIGHT = 64;
const PAD = 12;
const BUTTON = 40;
// The canvas overflows the capsule by this much on every side to fit its shadow.
const BLEED = 64;
const FIELD_X = PAD + BUTTON + 10;
const FIELD_WIDTH = WIDTH - FIELD_X - PAD;
const SHEEN_WIDTH = 70;

/** Centre of the plus (0) and of the three buttons spread evenly to its right. */
function slot(index: number) {
  return PAD + BUTTON / 2 + (index * (WIDTH - 2 * PAD - BUTTON)) / 3;
}

// The kick: a sine that dies away. The tilt peaks near 5 degrees a fifth of a second in.
const TILT = 10.5;
const TILT_OMEGA = 5.24;
const TILT_DECAY = 3;
const SQUASH = 0.1;
const SQUASH_OMEGA = 15;
const SQUASH_DECAY = 6.5;

// The buttons pop out with a little overshoot and come home without one.
const OPEN_SPRING = { mass: 1, stiffness: 260, damping: 23 };
const CLOSE_SPRING = { mass: 1, stiffness: 300, damping: 34 };
const FADE = 0.16;
// The field waits for the buttons to be home before it wipes in.
const WIPE_DELAY = 0.22;
const WIPE = 0.34;
const SHEEN = 0.52;
// A picked button swells by this much, and the capsule closes this long after the tap.
const PULSE = 0.22;
const PULSE_SECONDS = 0.28;
const PICK = 0.3;

// autoPlay (seconds): open, look, pick the camera, close, rest.
const LOOP = 5;
const OPEN_AT = 0.8;
const PICK_AT = 2.5;
const SCRIPT_PICK = 2;
const PRESS = 0.12;
// After a touch the script waits this long, then starts again from rest.
const RESUME_AFTER = 4;

const CAPSULE = '#5b2ff5';
const BUTTON_FILL = '#7b5bfa';
const FIELD_FILL = '#7453f7';
const PLACEHOLDER = '#b5a8fc';

const CSS_EASE_OUT = cubicBezier(0.23, 1, 0.32, 1);

const KINDS: JellyAttachCapsuleKind[] = ['video', 'camera', 'photo'];
const LABELS = { video: 'Video', camera: 'Camera', photo: 'Photo' };

// Outline icons in an 18-point box: a rounded box, an optional round lens, and one open line.
const GLYPHS = [
  // Video camera: the body and the flared lens at its right.
  { box: [2.2, 5, 9.6, 8, 2.2], lens: null, line: 'M11.8 8.2L15.8 5.9L15.8 12.1L11.8 9.8' },
  // Photo camera: the body, the hump of the viewfinder, the lens.
  { box: [2.2, 5.4, 13.6, 9.4, 2.4], lens: [9, 10.1, 2.5], line: 'M6.4 5.4L7.4 3.4L10.6 3.4L11.6 5.4' },
  // Picture: the frame, the sun, the hills.
  { box: [2.8, 2.8, 12.4, 12.4, 2.8], lens: [6.7, 6.7, 1.3], line: 'M3.2 12.6L6.9 9.3L9.8 11.9L11.7 10.4L14.8 13' },
];
const PLUS = 'M9 3.8V14.2M3.8 9H14.2';

function clamp01(t: number) {
  'worklet';
  return Math.min(1, Math.max(0, t));
}

/** A kick that swings out and dies away: 0 before it, and back to 0 after a second or so. */
function wobble(t: number, omega: number, decay: number) {
  'worklet';
  return t <= 0 ? 0 : Math.sin(omega * t) * Math.exp(-decay * t);
}

function tick() {
  if (process.env.EXPO_OS !== 'web') Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
}

function Glyph({ index }: { index: number }) {
  const { box, lens, line } = GLYPHS[index];
  return (
    <Svg width={18} height={18} viewBox="0 0 18 18" fill="none" stroke="#ffffff" strokeWidth={1.5} strokeLinecap="round" strokeLinejoin="round">
      <Rect x={box[0]} y={box[1]} width={box[2]} height={box[3]} rx={box[4]} />
      {lens && <Circle cx={lens[0]} cy={lens[1]} r={lens[2]} />}
      <Path d={line} />
    </Svg>
  );
}

/** A round button of the capsule: dips while pressed (or while the script presses it), with a light haptic. */
function Round({ label, held, onPress, children }: { label: string; held: boolean; onPress: () => void; children: ReactNode }) {
  return (
    <Pressable onPress={onPress} onPressIn={tick} accessibilityRole="button" accessibilityLabel={label}>
      {({ pressed }) => (
        <Animated.View
          style={[
            styles.round,
            {
              transform: [{ scale: pressed || held ? 0.88 : 1 }],
              transitionProperty: 'transform',
              transitionDuration: 130,
              transitionTimingFunction: CSS_EASE_OUT,
            },
          ]}
        >
          {children}
        </Animated.View>
      )}
    </Pressable>
  );
}

type Clock = {
  time: SharedValue<number>;
  pickAt: SharedValue<number>;
  picked: SharedValue<number>;
};

/** One of the three buttons that live under the plus until the capsule opens. */
function AttachButton({
  index,
  pop,
  time,
  pickAt,
  picked,
  open,
  held,
  still,
  onPress,
}: Clock & { index: number; pop: SharedValue<number>; open: boolean; held: boolean; still: boolean; onPress: () => void }) {
  const style = useAnimatedStyle(() => {
    const out = pop.get();
    const pulse = picked.get() === index ? clamp01((time.get() - pickAt.get()) / PULSE_SECONDS) : 1;
    return {
      opacity: clamp01(out),
      transform: [
        { translateX: still ? slot(index) - slot(0) : (slot(index) - slot(0)) * out },
        { scale: still ? 1 : (0.4 + 0.6 * out) * (1 + PULSE * Math.sin(Math.PI * pulse)) },
      ],
    };
  });
  // A ring leaves the button that was picked.
  const ring = useAnimatedStyle(() => {
    const pulse = picked.get() === index ? clamp01((time.get() - pickAt.get()) / PULSE_SECONDS) : 1;
    return { opacity: 0.6 * (1 - pulse), transform: [{ scale: 1 + 0.5 * pulse }] };
  });
  return (
    <Animated.View style={[styles.slot, style]} pointerEvents={open ? 'auto' : 'none'}>
      <Round label={LABELS[KINDS[index - 1]]} held={held} onPress={onPress}>
        <Animated.View style={[styles.ring, ring]} />
        <Glyph index={index - 1} />
      </Round>
    </Animated.View>
  );
}

/**
 * Violet message capsule made of jelly. A tap on its plus tilts and squashes the whole capsule,
 * turns the plus into a cross, dissolves the message field and pops three attachment buttons out
 * of the plus one after another; closing wobbles it the other way and wipes the field back in.
 */
export function JellyAttachCapsule({ value, onChangeText, autoPlay = false, placeholder = 'Message', onSend, onPick }: Props) {
  const reducedMotion = useReducedMotion();
  const controlled = value != null;
  const auto = autoPlay && !controlled && !reducedMotion;

  const [draft, setDraft] = useState('');
  const [open, setOpen] = useState(false);
  // The button the autoPlay script is pressing: 0 is the plus.
  const [scriptPress, setScriptPress] = useState<number | null>(null);
  // False once a touch has taken the capsule from the autoPlay script.
  const [handsOff, setHandsOff] = useState(true);
  const [touches, setTouches] = useState(0);
  const input = useRef<TextInput>(null);
  const focused = useRef(false);
  const isOpen = useRef(false);
  const pickTimer = useRef<ReturnType<typeof setTimeout> | null>(null);

  const playing = auto && handsOff;
  const text = value ?? draft;

  // Seconds since the capsule appeared, and the moments its wobble and its field are timed from.
  const time = useSharedValue(0);
  // The last two kicks, so a quick second tap adds to the first wobble instead of cutting it.
  const kickAt = useSharedValue(-100);
  const kickTurn = useSharedValue(0);
  const earlierAt = useSharedValue(-100);
  const earlierTurn = useSharedValue(0);
  const openAt = useSharedValue(-100);
  const closeAt = useSharedValue(-100);
  const pickAt = useSharedValue(-100);
  const picked = useSharedValue(0);
  const opened = useSharedValue(false);
  const spin = useSharedValue(0);
  const pop1 = useSharedValue(0);
  const pop2 = useSharedValue(0);
  const pop3 = useSharedValue(0);

  useFrameCallback((frame) => {
    time.set(frame.timeSinceFirstFrame / 1000);
  });

  /** Opens or closes, and kicks the jelly: the right end swings up to open and dips to close. */
  const move = useCallback(
    (next: boolean) => {
      if (next === isOpen.current) return;
      isOpen.current = next;
      const now = time.get();
      earlierAt.set(kickAt.get());
      earlierTurn.set(kickTurn.get());
      kickAt.set(now);
      kickTurn.set(next ? -1 : 1);
      if (next) {
        openAt.set(now);
        input.current?.blur();
      } else {
        closeAt.set(now);
      }
      opened.set(next);
      setOpen(next);
      const to = next ? 1 : 0;
      if (reducedMotion) {
        for (const pop of [spin, pop1, pop2, pop3]) pop.set(withTiming(to, { duration: 150 }));
        return;
      }
      spin.set(withSpring(to, OPEN_SPRING));
      // They leave one after another, and come home in the opposite order.
      [pop1, pop2, pop3].forEach((pop, index) => {
        pop.set(withDelay(next ? 60 + 80 * index : 70 * (2 - index), withSpring(to, next ? OPEN_SPRING : CLOSE_SPRING)));
      });
    },
    [reducedMotion, closeAt, earlierAt, earlierTurn, kickAt, kickTurn, openAt, opened, pop1, pop2, pop3, spin, time],
  );

  /** The picked button pulses, then the capsule closes. */
  const pick = useCallback(
    (index: number) => {
      picked.set(index);
      pickAt.set(time.get());
      if (pickTimer.current) clearTimeout(pickTimer.current);
      pickTimer.current = setTimeout(() => move(false), PICK * 1000);
    },
    [move, pickAt, picked, time],
  );

  useEffect(
    () => () => {
      if (pickTimer.current) clearTimeout(pickTimer.current);
    },
    [],
  );

  useEffect(() => {
    if (!playing) return;
    const timers: ReturnType<typeof setTimeout>[] = [];
    const epoch = Date.now();
    const loop = (lap: number) => {
      const at = (seconds: number, step: () => void) => {
        timers.push(setTimeout(step, Math.max(0, epoch + (lap * LOOP + seconds) * 1000 - Date.now())));
      };
      at(OPEN_AT, () => setScriptPress(0));
      at(OPEN_AT + PRESS, () => {
        setScriptPress(null);
        move(true);
      });
      at(PICK_AT, () => setScriptPress(SCRIPT_PICK));
      at(PICK_AT + PRESS, () => {
        setScriptPress(null);
        pick(SCRIPT_PICK);
      });
      at(LOOP, () => {
        timers.length = 0;
        loop(lap + 1);
      });
    };
    loop(0);
    return () => timers.forEach(clearTimeout);
  }, [playing, move, pick]);

  // Any touch stops the script; it comes back a few seconds after the last one.
  useEffect(() => {
    if (!auto || handsOff) return;
    const timer = setTimeout(() => {
      if (focused.current) return;
      move(false);
      setDraft('');
      setHandsOff(true);
    }, RESUME_AFTER * 1000);
    return () => clearTimeout(timer);
  }, [auto, handsOff, touches, text, move]);

  const touch = () => {
    setHandsOff(false);
    setScriptPress(null);
    setTouches((count) => count + 1);
  };

  const edit = (next: string) => {
    if (!controlled) setDraft(next);
    onChangeText?.(next);
  };

  const send = () => {
    if (!text) return;
    edit('');
    onSend?.(text);
  };

  // The jelly swings about the plus, where the finger is, and squashes about its middle.
  const tiltStyle = useAnimatedStyle(() => {
    const now = time.get();
    const degrees = reducedMotion
      ? 0
      : TILT *
        (kickTurn.get() * wobble(now - kickAt.get(), TILT_OMEGA, TILT_DECAY) +
          earlierTurn.get() * wobble(now - earlierAt.get(), TILT_OMEGA, TILT_DECAY));
    const pivot = slot(0) - WIDTH / 2;
    return { transform: [{ translateX: pivot }, { rotate: `${degrees}deg` }, { translateX: -pivot }] };
  });
  const squashStyle = useAnimatedStyle(() => {
    const now = time.get();
    const squash = reducedMotion
      ? 0
      : SQUASH * (wobble(now - kickAt.get(), SQUASH_OMEGA, SQUASH_DECAY) + wobble(now - earlierAt.get(), SQUASH_OMEGA, SQUASH_DECAY));
    return { transform: [{ scaleX: 1 + 0.6 * squash }, { scaleY: 1 - squash }] };
  });
  // Opening dissolves the field; closing wipes it back in from the left under a sheen.
  const fieldStyle = useAnimatedStyle(() => {
    const sinceClose = time.get() - closeAt.get() - WIPE_DELAY;
    const wipe = opened.get() || reducedMotion ? 1 : 1 - Math.pow(1 - clamp01(sinceClose / WIPE), 3);
    return {
      width: FIELD_WIDTH * wipe,
      opacity: opened.get() ? 1 - clamp01((time.get() - openAt.get()) / FADE) : 1,
    };
  });
  const sheenStyle = useAnimatedStyle(() => {
    const sheen = opened.get() || reducedMotion ? 1 : clamp01((time.get() - closeAt.get() - WIPE_DELAY) / SHEEN);
    return { transform: [{ translateX: -SHEEN_WIDTH + (FIELD_WIDTH + SHEEN_WIDTH) * sheen }] };
  });
  const spinStyle = useAnimatedStyle(() => ({ transform: [{ rotate: `${45 * spin.get()}deg` }] }));

  const pops = [pop1, pop2, pop3];

  return (
    <Animated.View style={[styles.capsule, tiltStyle]}>
      <Animated.View style={[styles.capsule, squashStyle]}>
        <View pointerEvents="none" style={styles.surface}>
          <Canvas style={styles.canvas}>
            <RoundedRect x={BLEED + 14} y={BLEED + 24} width={WIDTH - 28} height={HEIGHT - 12} r={(HEIGHT - 12) / 2} color="rgba(91,47,245,0.45)">
              <Blur blur={15} />
            </RoundedRect>
            <RoundedRect x={BLEED} y={BLEED} width={WIDTH} height={HEIGHT} r={HEIGHT / 2} color={CAPSULE} />
          </Canvas>
        </View>

        <Animated.View style={[styles.field, fieldStyle]} pointerEvents={open ? 'none' : 'auto'}>
          <TextInput
            ref={input}
            style={styles.text}
            value={text}
            onChangeText={(next) => {
              touch();
              edit(next);
            }}
            onFocus={() => {
              focused.current = true;
              touch();
            }}
            onBlur={() => {
              focused.current = false;
              touch();
            }}
            onSubmitEditing={send}
            placeholder={placeholder}
            placeholderTextColor={PLACEHOLDER}
            returnKeyType="send"
            submitBehavior="blurAndSubmit"
            selectionColor="#ffffff"
            cursorColor="#ffffff"
            underlineColorAndroid="transparent"
          />
          <Animated.View pointerEvents="none" style={[styles.sheen, sheenStyle]}>
            <LinearGradient
              colors={['rgba(255,255,255,0)', 'rgba(255,255,255,0.3)', 'rgba(255,255,255,0)']}
              start={{ x: 0, y: 0.5 }}
              end={{ x: 1, y: 0.5 }}
              style={StyleSheet.absoluteFill}
            />
          </Animated.View>
        </Animated.View>

        {KINDS.map((kind, index) => (
          <AttachButton
            key={kind}
            index={index + 1}
            pop={pops[index]}
            time={time}
            pickAt={pickAt}
            picked={picked}
            open={open}
            held={scriptPress === index + 1}
            still={reducedMotion}
            onPress={() => {
              touch();
              pick(index + 1);
              onPick?.(kind);
            }}
          />
        ))}

        <View style={styles.slot}>
          <Round
            label={open ? 'Close attachments' : 'Attach'}
            held={scriptPress === 0}
            onPress={() => {
              touch();
              move(!open);
            }}
          >
            <Animated.View style={spinStyle}>
              <Svg width={18} height={18} viewBox="0 0 18 18">
                <Path d={PLUS} stroke="#ffffff" strokeWidth={1.8} strokeLinecap="round" />
              </Svg>
            </Animated.View>
          </Round>
        </View>
      </Animated.View>
    </Animated.View>
  );
}

const styles = StyleSheet.create({
  capsule: {
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
  field: {
    position: 'absolute',
    left: FIELD_X,
    top: PAD,
    height: BUTTON,
    borderRadius: BUTTON / 2,
    backgroundColor: FIELD_FILL,
    overflow: 'hidden',
  },
  text: {
    width: FIELD_WIDTH,
    height: BUTTON,
    paddingVertical: 0,
    paddingHorizontal: 16,
    color: '#ffffff',
    fontSize: 15,
  },
  sheen: {
    position: 'absolute',
    left: 0,
    top: 0,
    width: SHEEN_WIDTH,
    height: BUTTON,
  },
  // Every button starts on the plus; the attachment buttons are moved from there.
  slot: {
    position: 'absolute',
    left: PAD,
    top: PAD,
  },
  round: {
    width: BUTTON,
    height: BUTTON,
    borderRadius: BUTTON / 2,
    backgroundColor: BUTTON_FILL,
    alignItems: 'center',
    justifyContent: 'center',
  },
  ring: {
    ...StyleSheet.absoluteFill,
    borderRadius: BUTTON / 2,
    borderWidth: 1.5,
    borderColor: '#ffffff',
  },
});
