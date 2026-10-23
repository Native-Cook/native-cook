import { Canvas, Path } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { Image } from 'expo-image';
import { LinearGradient } from 'expo-linear-gradient';
import { useEffect, useState } from 'react';
import { Pressable, StyleSheet, Text, TextInput, View } from 'react-native';
import Animated, {
  type SharedValue,
  useAnimatedProps,
  useAnimatedStyle,
  useFrameCallback,
  useReducedMotion,
  useSharedValue,
} from 'react-native-reanimated';
import { scheduleOnRN } from 'react-native-worklets';

/** A picture attached to the prompt: its address, and the flat colour shown until it arrives. */
export type OrbitPromptPillPhoto = { url: string; tint: string };

type Props = {
  /** The field's text. Leave it out and the component keeps the text itself. */
  value?: string;
  onChangeText?: (text: string) => void;
  /** Types `script`, lets the pictures orbit, sends and starts over, in a loop. */
  autoPlay?: boolean;
  placeholder?: string;
  /** What the autoPlay loop types. */
  script?: string;
  tags?: string[];
  /** The pictures that can be attached; the first `attached` of them are on the ring from the start. */
  photos?: OrbitPromptPillPhoto[];
  attached?: number;
  /** Colour of the tag chips and the caret. */
  accent?: string;
  /** Called with the text and the number of pictures on the ring. */
  onSend?: (text: string, pictures: number) => void;
  /** Called with the new number of pictures when "+" adds one. */
  onAttach?: (pictures: number) => void;
  onRemoveTag?: (tag: string) => void;
};

const WIDTH = 350;
const HEIGHT = 300;
const PILL_WIDTH = 330;
const PILL_HEIGHT = 52;
const PILL_X = (WIDTH - PILL_WIDTH) / 2;
const PILL_Y = (HEIGHT - PILL_HEIGHT) / 2;
// Centre of "+" from the pill's left end, and where the text starts.
const PLUS_X = 27;
const FIELD_X = 50;
const SEND_SIZE = 38;
// Centre of the send button, in the component's box.
const SEND_X = PILL_X + PILL_WIDTH - 7 - SEND_SIZE / 2;
const FIELD_WIDTH = PILL_WIDTH - FIELD_X - SEND_SIZE - 16;
const PRESS_DIP = 0.08;
// The pill grows from this scale as it fades in.
const INTRO_SCALE = 0.92;

// Back to front: far pictures (each shifted by its depth, -1…1), chips, the pill, near pictures.
const BACK_LAYER = 2;
const CHIP_LAYER = 4;
const PILL_LAYER = 5;
const FRONT_LAYER = 8;
// A view's zIndex is a whole number, so the layers are spread out to keep the depth between them.
const LAYER_STEPS = 100;

const MAX_TAGS = 6;
const CHIP_HEIGHT = 22;
// Between the chips and the pill's top edge, and from the pill's left end to the first chip.
const CHIP_GAP = 5;
const CHIP_INSET = 30;
// A chip starts this far down, hidden behind the pill.
const CHIP_TRAVEL = 28;
const CHIP_STAGGER = 0.15;

const MAX_PHOTOS = 7;
const PHOTO_RADIUS = 12;
// The ring: its radii, how far right of the pill's centre it sits, and its slant.
const ORBIT_X = 116;
const ORBIT_Y = 92;
const ORBIT_SHIFT = 6;
const TILT = -0.19;
// Where on the ring the first picture starts, and the direction pictures are nearest in.
const START_ANGLE = -1.12;
const NEAR_ANGLE = -0.7;
// Turns per second.
const SPEED = 0.2;
// A picture's scale halfway between far and near, and how much depth adds or takes.
const MID_SCALE = 0.87;
const DEPTH_SCALE = 0.25;

// The ring opens once the text has been left alone for this long.
const OPEN_DELAY = 0.3;
// Typed letters stay grey until the typing has paused for this long.
const SETTLE = 0.25;
// After the ring opens: when the light starts across the pill, and when the first picture leaves.
const SWEEP_DELAY = 0.55;
const SWEEP_TIME = 0.5;
const SWEEP_WIDTH = 110;
const BURST_DELAY = 0.7;
const BURST_STAGGER = 0.06;
// Sending: each picture takes this long to reach the button, one a little after the other, turning this much more.
const FOLD_TIME = 0.55;
const FOLD_STAGGER = 0.04;
const SPIRAL = 2.4;
// Scale per second the send button is kicked with when the pictures land, and its ring.
const PULSE_KICK = 5;
const RIPPLE_TIME = 0.5;
const RIPPLE_GROW = 0.9;
const BLINK = 1.1;

// autoPlay loop (seconds): typing starts, the send button is pressed, the prompt is sent.
const TYPE_AT = 0.8;
const PRESS_AT = 7.45;
const SEND_AT = 7.6;
const LOOP = 9.4;
// After a touch, or once the field is left empty, the loop waits this long before it starts over.
const RESUME = 3;

/** Seconds between letters as the loop types: uneven like a hand, the same every time. */
const TYPING_RHYTHM = [0.09, 0.07, 0.11, 0.06, 0.13, 0.08, 0.06, 0.1, 0.07, 0.12];

/** Side of each picture at scale 1, in points. */
const ORBIT_SIZES = [84, 66, 78, 62, 74, 68, 72];

const TAU = Math.PI * 2;
// A moment that never comes: no pulse is waiting.
const NEVER = Number.MAX_VALUE;

const PLUS = 'M12 22H32M22 12V32';
const ARROW = 'M19 26.5V12M12.5 18.5L19 12L25.5 18.5';
const CROSS = 'M1 1L7 7M7 1L1 7';

const CLOSED = 0;
const OPEN = 1;
const FOLDING = 2;

function orbitPhoto(id: string) {
  return `https://images.unsplash.com/photo-${id}?auto=format&fit=crop&w=240&h=240&q=80`;
}

const PHOTOS: OrbitPromptPillPhoto[] = [
  { url: orbitPhoto('1441974231531-c6227db76b6e'), tint: '#4a6332' },
  { url: orbitPhoto('1507525428034-b723cf961d3e'), tint: '#c9906c' },
  { url: orbitPhoto('1470071459604-3b5ec3a7fe05'), tint: '#5f7c4c' },
  { url: orbitPhoto('1501785888041-af3ef285b470'), tint: '#2f8f9a' },
  { url: orbitPhoto('1501594907352-04cda38ebc29'), tint: '#b5573c' },
  { url: orbitPhoto('1472214103451-9374bd1c798e'), tint: '#8a8f45' },
  { url: orbitPhoto('1526772662000-3f88f10405ff'), tint: '#6d808c' },
];

/** Per-frame state: the ring, the chips and everything that eases after them. */
type Sim = {
  time: number;
  clock: number;
  looping: boolean;
  /** Seconds the loop still waits after a touch. */
  hold: number;
  /** Letters of the script the loop has typed, and whether the text in the field is the loop's. */
  typed: number;
  scripted: boolean;
  pressed: boolean;
  sent: boolean;
  hasText: boolean;
  lastEdit: number;
  stage: number;
  openAt: number;
  foldAt: number;
  /** The ring folds into the send button (1) or back behind the pill (0). */
  toSend: number;
  base: number;
  count: number;
  bornAt: number[];
  gone: boolean[];
  intro: number;
  ink: number;
  fieldAlpha: number;
  sendPress: number;
  pulseAt: number;
  rippleAt: number;
  plusTurn: number;
  // Springs: a position and its velocity.
  pulse: number;
  pulseV: number;
  plusSpin: number;
  plusSpinV: number;
  chips: number[];
  chipsV: number[];
  out: number[];
  outV: number[];
  slot: number[];
  slotV: number[];
};

function rest(base: number): Sim {
  return {
    time: 0,
    clock: 0,
    looping: false,
    hold: 0,
    typed: 0,
    scripted: false,
    pressed: false,
    sent: false,
    hasText: false,
    lastEdit: -10,
    stage: CLOSED,
    openAt: 0,
    foldAt: 0,
    toSend: 0,
    base,
    count: base,
    bornAt: new Array(MAX_PHOTOS).fill(0),
    gone: new Array(MAX_TAGS).fill(false),
    intro: 0,
    ink: 1,
    fieldAlpha: 1,
    sendPress: 0,
    pulseAt: NEVER,
    rippleAt: -10,
    plusTurn: 0,
    pulse: 1,
    pulseV: 0,
    plusSpin: 0,
    plusSpinV: 0,
    chips: new Array(MAX_TAGS).fill(0),
    chipsV: new Array(MAX_TAGS).fill(0),
    out: new Array(MAX_PHOTOS).fill(0),
    outV: new Array(MAX_PHOTOS).fill(0),
    slot: new Array(MAX_PHOTOS).fill(0),
    slotV: new Array(MAX_PHOTOS).fill(0),
  };
}

function touch(s: Sim) {
  'worklet';
  s.hold = RESUME;
}

function edited(s: Sim, hasText: boolean) {
  'worklet';
  s.hasText = hasText;
  s.lastEdit = s.time;
}

function attach(s: Sim) {
  'worklet';
  if (s.count >= MAX_PHOTOS || s.stage === FOLDING) return false;
  s.bornAt[s.count] = s.time;
  s.count += 1;
  s.plusTurn += 90;
  return true;
}

function fold(s: Sim, toSend: number) {
  'worklet';
  s.stage = FOLDING;
  s.foldAt = s.time;
  s.toSend = toSend;
}

function send(s: Sim) {
  'worklet';
  s.hasText = false;
  s.fieldAlpha = 0;
  if (s.stage === OPEN) {
    fold(s, 1);
    s.pulseAt = s.time + FOLD_TIME;
  } else {
    s.pulseAt = s.time;
  }
}

function close(s: Sim) {
  'worklet';
  s.stage = CLOSED;
  s.count = s.base;
  for (let i = 0; i < MAX_PHOTOS; i++) {
    s.out[i] = 0;
    s.outV[i] = 0;
  }
  for (let i = 0; i < MAX_TAGS; i++) s.gone[i] = false;
}

/** When the loop types letter `index` of the script; a new word takes a little longer. */
function typedAt(index: number, script: string[]) {
  'worklet';
  let t = TYPE_AT;
  for (let i = 0; i < index; i++) {
    t += TYPING_RHYTHM[i % TYPING_RHYTHM.length] + (script[i] === ' ' ? 0.08 : 0);
  }
  return t;
}

/** Moves `x` towards `target`, covering the same share of the distance in the same time at any frame rate. */
function approach(x: number, target: number, rate: number, dt: number) {
  'worklet';
  return x + (target - x) * (1 - Math.exp(-rate * dt));
}

/** The new velocity of a spring (mass 1, stiffness omega², damping 2·zeta·omega) after `dt`. */
function sprung(x: number, v: number, target: number, omega: number, zeta: number, dt: number) {
  'worklet';
  return v + (omega * omega * (target - x) - 2 * zeta * omega * v) * dt;
}

function advance(s: Sim, dt: number, engaged: boolean, script: string[], still: boolean, setText: (text: string) => void) {
  'worklet';
  s.time += dt;

  if (engaged) s.hold = RESUME;
  if (s.hold > 0) {
    s.hold -= dt;
    if (s.hold <= 0 && s.looping) {
      s.clock = 0;
      s.typed = 0;
      s.sent = false;
    }
  } else if (s.looping) {
    s.clock += dt;
    if (s.clock >= LOOP) {
      s.clock -= LOOP;
      s.typed = 0;
      s.sent = false;
    }
    let letters = 0;
    while (letters < script.length && s.clock >= typedAt(letters, script)) letters += 1;
    if (s.clock < SEND_AT && letters !== s.typed) {
      s.typed = letters;
      s.scripted = true;
      scheduleOnRN(setText, script.slice(0, letters).join(''));
      edited(s, letters > 0);
    }
    s.pressed = s.clock >= PRESS_AT && s.clock < SEND_AT;
    if (s.clock >= SEND_AT && !s.sent) {
      s.sent = true;
      s.scripted = false;
      scheduleOnRN(setText, '');
      send(s);
    }
  }

  if (s.stage === CLOSED && ((s.hasText && s.time - s.lastEdit >= OPEN_DELAY) || s.count > s.base)) {
    s.stage = OPEN;
    s.openAt = s.time;
    // Pictures added to an empty field come out at once; after typing they wait for the light.
    const wait = s.hasText ? BURST_DELAY : 0;
    for (let i = 0; i < s.count; i++) {
      s.slot[i] = i / s.count;
      s.slotV[i] = 0;
      s.bornAt[i] = s.time + wait + i * BURST_STAGGER;
    }
  } else if (s.stage === OPEN && !s.hasText && s.count <= s.base) {
    fold(s, 0);
  } else if (s.stage === FOLDING && s.time - s.foldAt >= FOLD_TIME + FOLD_STAGGER * s.count) {
    close(s);
  }
  if (s.time >= s.pulseAt) {
    s.pulseAt = NEVER;
    s.rippleAt = s.time;
    s.pulseV += PULSE_KICK;
  }

  s.intro = approach(s.intro, 1, 7, dt);
  s.ink = approach(s.ink, s.time - s.lastEdit > SETTLE ? 1 : 0, 10, dt);
  s.fieldAlpha = approach(s.fieldAlpha, 1, 6, dt);
  s.sendPress = approach(s.sendPress, s.pressed ? 1 : 0, 30, dt);
  s.pulseV = sprung(s.pulse, s.pulseV, 1, 22, 0.35, dt);
  s.pulse += s.pulseV * dt;
  s.plusSpinV = sprung(s.plusSpin, s.plusSpinV, s.plusTurn, 16, 0.6, dt);
  s.plusSpin += s.plusSpinV * dt;
  for (let i = 0; i < MAX_TAGS; i++) {
    const up = s.stage === OPEN && !s.gone[i] && s.time >= s.openAt + i * CHIP_STAGGER;
    if (still) {
      s.chips[i] = approach(s.chips[i], up ? 1 : 0, 12, dt);
    } else {
      s.chipsV[i] = sprung(s.chips[i], s.chipsV[i], up ? 1 : 0, 15, 0.6, dt);
      s.chips[i] += s.chipsV[i] * dt;
    }
  }
  for (let i = 0; i < MAX_PHOTOS; i++) {
    const live = s.stage !== CLOSED && i < s.count && s.time >= s.bornAt[i];
    if (still) {
      s.out[i] = approach(s.out[i], live ? 1 : 0, 10, dt);
    } else {
      s.outV[i] = sprung(s.out[i], s.outV[i], live ? 1 : 0, 9.5, 0.55, dt);
      s.out[i] += s.outV[i] * dt;
    }
    s.slotV[i] = sprung(s.slot[i], s.slotV[i], i / Math.max(s.count, 1), 8, 0.8, dt);
    s.slot[i] += s.slotV[i] * dt;
  }
}

/** Where picture `index` is on the ring right now. */
function spot(s: Sim, index: number, still: boolean) {
  'worklet';
  const folding = s.stage === FOLDING;
  const turn = still ? 0 : SPEED * (s.time - s.openAt);
  const folded = folding ? Math.min(1, Math.max(0, (s.time - s.foldAt - index * FOLD_STAGGER) / FOLD_TIME)) : 0;
  const pull = folded * folded * folded;
  const reach = (still ? 1 : s.out[index]) * (1 - pull);
  const angle = TAU * (s.slot[index] + turn) + START_ANGLE + SPIRAL * pull;
  const depth = Math.sin(angle - NEAR_ANGLE);
  const ex = ORBIT_X * Math.cos(angle) * reach;
  const ey = ORBIT_Y * Math.sin(angle) * reach;
  const home = WIDTH / 2 + ORBIT_SHIFT;
  return {
    x: home + (SEND_X - home) * pull * s.toSend + ex * Math.cos(TILT) - ey * Math.sin(TILT),
    y: HEIGHT / 2 + ex * Math.sin(TILT) + ey * Math.cos(TILT),
    scale: (MID_SCALE + DEPTH_SCALE * depth) * (0.3 + 0.7 * Math.min(1, Math.max(0, reach))),
    alpha: Math.min(1, Math.max(0, (s.out[index] * (1 - pull)) / 0.2)),
    depth,
    // Near pictures pass in front of the pill, far ones behind it; nothing is in front until it has left the pill.
    front: folding ? s.toSend > 0.5 : depth > 0 && reach > 0.92,
  };
}

function Picture({ sim, index, photo, still }: { sim: SharedValue<Sim>; index: number; photo: OrbitPromptPillPhoto; still: boolean }) {
  const size = ORBIT_SIZES[index];
  const style = useAnimatedStyle(() => {
    const s = sim.get();
    const at = spot(s, index, still);
    return {
      opacity: at.alpha * s.intro,
      zIndex: Math.round(((at.front ? FRONT_LAYER : BACK_LAYER) + at.depth) * LAYER_STEPS),
      boxShadow: `0px 5px 16px rgba(0, 0, 0, ${0.1 + 0.1 * Math.max(0, at.depth)})`,
      transform: [{ translateX: at.x - size / 2 }, { translateY: at.y - size / 2 }, { scale: at.scale }],
    };
  });

  return (
    <Animated.View pointerEvents="none" style={[styles.picture, { width: size, height: size, backgroundColor: photo.tint }, style]}>
      <Image source={photo.url} style={styles.photo} contentFit="cover" transition={200} />
    </Animated.View>
  );
}

function Chip({
  sim,
  index,
  tag,
  accent,
  still,
  onRemove,
}: {
  sim: SharedValue<Sim>;
  index: number;
  tag: string;
  accent: string;
  still: boolean;
  onRemove: () => void;
}) {
  const style = useAnimatedStyle(() => {
    const up = sim.get().chips[index];
    return {
      opacity: Math.min(1, Math.max(0, up * 3)),
      transform: [{ translateY: still ? 0 : (1 - up) * CHIP_TRAVEL }],
    };
  });
  const props = useAnimatedProps(() => ({
    pointerEvents: sim.get().chips[index] > 0.9 ? ('auto' as const) : ('none' as const),
  }));

  return (
    <Animated.View style={style} animatedProps={props}>
      <Pressable style={[styles.chip, { backgroundColor: accent }]} onPress={onRemove} accessibilityRole="button" accessibilityLabel={`Remove ${tag}`}>
        <Text style={styles.chipText} numberOfLines={1}>
          {tag}
        </Text>
        <Canvas style={styles.chipCross}>
          <Path path={CROSS} style="stroke" strokeWidth={1.6} strokeCap="round" color="#ffffff" />
        </Canvas>
      </Pressable>
    </Animated.View>
  );
}

const AnimatedInput = Animated.createAnimatedComponent(TextInput);

/**
 * A prompt field whose attachments circle it. Once something is typed, tag chips rise from behind
 * the pill's top edge, a cyan light crosses it and the attached pictures burst out onto a tilted
 * orbit: behind the pill on the far side, in front of it on the near side, larger the nearer they
 * are. "+" adds a picture to the ring; sending spirals them all into the send button.
 */
export function OrbitPromptPill({
  value,
  onChangeText,
  autoPlay = false,
  placeholder = 'Ask anything',
  script = "What's today's run?",
  tags = ['Daily', 'Trail', 'Run'],
  photos = PHOTOS,
  attached = 5,
  accent = '#2f7bea',
  onSend,
  onAttach,
  onRemoveTag,
}: Props) {
  const reducedMotion = useReducedMotion();
  const auto = autoPlay && !reducedMotion;
  const [own, setOwn] = useState('');
  const [focused, setFocused] = useState(false);
  const current = value ?? own;
  const base = Math.min(attached, photos.length, MAX_PHOTOS);
  const letters = Array.from(script);

  const sim = useSharedValue<Sim>(rest(base));
  const focusedOn = useSharedValue(false);

  useEffect(() => {
    sim.modify((s) => {
      'worklet';
      s.looping = auto;
      if (s.base !== base) {
        s.base = base;
        s.count = base;
      }
      return s;
    });
  }, [auto, base, sim]);

  useEffect(() => {
    focusedOn.set(focused);
  }, [focused, focusedOn]);

  const store = (text: string) => {
    if (value == null) setOwn(text);
    onChangeText?.(text);
  };

  const buzz = (style: Haptics.ImpactFeedbackStyle) => {
    if (process.env.EXPO_OS !== 'web') Haptics.impactAsync(style).catch(() => {});
  };

  useFrameCallback((frame) => {
    const dt = Math.min(frame.timeSincePreviousFrame ?? 16, 64) / 1000;
    const held = focusedOn.get();
    sim.modify((s) => {
      'worklet';
      advance(s, dt, held || (!s.scripted && (s.hasText || s.count > s.base)), letters, reducedMotion, store);
      return s;
    });
  });

  const attachedOne = (pictures: number) => {
    buzz(Haptics.ImpactFeedbackStyle.Light);
    onAttach?.(pictures);
  };

  const add = () => {
    sim.modify((s) => {
      'worklet';
      touch(s);
      if (attach(s)) scheduleOnRN(attachedOne, s.count);
      return s;
    });
  };

  const wasSent = (text: string, pictures: number) => {
    store('');
    buzz(Haptics.ImpactFeedbackStyle.Medium);
    onSend?.(text, pictures);
  };

  const submit = () => {
    const text = current.trim();
    sim.modify((s) => {
      'worklet';
      if (text.length === 0 && s.count <= s.base) return s;
      const pictures = s.stage === OPEN ? s.count : 0;
      touch(s);
      s.scripted = false;
      send(s);
      scheduleOnRN(wasSent, text, pictures);
      return s;
    });
  };

  const remove = (index: number, tag: string) => {
    sim.modify((s) => {
      'worklet';
      touch(s);
      s.gone[index] = true;
      return s;
    });
    buzz(Haptics.ImpactFeedbackStyle.Light);
    onRemoveTag?.(tag);
  };

  const clearLoopText = () => store('');

  // A tap on the field takes it over from the loop: what the loop typed is cleared.
  const takeOver = () => {
    setFocused(true);
    sim.modify((s) => {
      'worklet';
      if (!s.scripted) return s;
      s.scripted = false;
      edited(s, false);
      scheduleOnRN(clearLoopText);
      return s;
    });
  };

  const typedByHand = (text: string) => {
    store(text);
    const hasText = text.length > 0;
    sim.modify((s) => {
      'worklet';
      s.scripted = false;
      edited(s, hasText);
      return s;
    });
  };

  const pressSend = (down: boolean) => {
    sim.modify((s) => {
      'worklet';
      touch(s);
      s.pressed = down;
      return s;
    });
  };

  const rootStyle = useAnimatedStyle(() => ({
    transform: [{ scale: reducedMotion ? 1 : INTRO_SCALE + (1 - INTRO_SCALE) * sim.get().intro }],
  }));

  const pillStyle = useAnimatedStyle(() => ({ opacity: sim.get().intro }));

  const sweepStyle = useAnimatedStyle(() => {
    const s = sim.get();
    const sweep = reducedMotion || s.stage !== OPEN ? -1 : (s.time - s.openAt - SWEEP_DELAY) / SWEEP_TIME;
    return {
      opacity: sweep > 0 && sweep < 1 ? 1 : 0,
      transform: [{ translateX: -SWEEP_WIDTH + (PILL_WIDTH + SWEEP_WIDTH) * sweep }],
    };
  });

  const plusStyle = useAnimatedStyle(() => ({ transform: [{ rotate: `${sim.get().plusSpin}deg` }] }));

  const fieldStyle = useAnimatedStyle(() => ({ opacity: sim.get().fieldAlpha }));

  const inkStyle = useAnimatedStyle(() => {
    const grey = Math.round(34 + 126 * (1 - sim.get().ink));
    return { color: `rgb(${grey}, ${grey}, ${grey})` };
  });

  const caretStyle = useAnimatedStyle(() => {
    const s = sim.get();
    const sinceEdit = s.time - s.lastEdit;
    const blink = sinceEdit < 0.5 ? 1 : Math.min(1, Math.max(0, 0.5 + 3 * Math.sin((TAU * sinceEdit) / BLINK)));
    return { opacity: s.scripted && s.hasText && !focused ? blink : 0 };
  });

  const rippleStyle = useAnimatedStyle(() => {
    const s = sim.get();
    const ripple = (s.time - s.rippleAt) / RIPPLE_TIME;
    return {
      opacity: ripple > 0 && ripple < 1 ? 0.5 * Math.max(0, 1 - ripple) : 0,
      transform: [{ scale: 1 + RIPPLE_GROW * Math.min(1, Math.max(0, ripple)) }],
    };
  });

  const sendStyle = useAnimatedStyle(() => {
    const s = sim.get();
    return { transform: [{ scale: s.pulse * (1 - PRESS_DIP * s.sendPress) }] };
  });

  return (
    <Animated.View style={[styles.root, rootStyle]}>
      {photos.slice(0, MAX_PHOTOS).map((photo, index) => (
        <Picture key={index} sim={sim} index={index} photo={photo} still={reducedMotion} />
      ))}

      <View style={styles.chips} pointerEvents="box-none">
        {tags.slice(0, MAX_TAGS).map((tag, index) => (
          <Chip key={index} sim={sim} index={index} tag={tag} accent={accent} still={reducedMotion} onRemove={() => remove(index, tag)} />
        ))}
      </View>

      <Animated.View style={[styles.pill, pillStyle]}>
        <View style={styles.pillBody} />
        {/* The light that crosses the pill just before the pictures come out. */}
        <View style={styles.sweepClip} pointerEvents="none">
          <Animated.View style={[styles.sweep, sweepStyle]}>
            <LinearGradient
              colors={['rgba(127,227,255,0)', 'rgba(127,227,255,0.55)', 'rgba(200,246,255,0.85)', 'rgba(127,227,255,0.55)', 'rgba(127,227,255,0)']}
              locations={[0, 0.35, 0.5, 0.65, 1]}
              start={{ x: 0, y: 0.5 }}
              end={{ x: 1, y: 0.5 }}
              style={styles.sweep}
            />
          </Animated.View>
        </View>
        <View style={styles.pillEdge} pointerEvents="none" />

        <Pressable style={styles.plus} onPress={add} accessibilityRole="button" accessibilityLabel="Attach a picture">
          <Animated.View style={plusStyle}>
            <Canvas style={styles.plusCanvas}>
              <Path path={PLUS} style="stroke" strokeWidth={1.4} strokeCap="round" color="#b4b4b4" />
            </Canvas>
          </Animated.View>
        </Pressable>

        <Animated.View style={[styles.field, fieldStyle]}>
          <AnimatedInput
            style={[styles.input, inkStyle]}
            value={current}
            onChangeText={typedByHand}
            placeholder={placeholder}
            placeholderTextColor="#a8a8a8"
            selectionColor={accent}
            cursorColor={accent}
            returnKeyType="send"
            onSubmitEditing={submit}
            onFocus={takeOver}
            onBlur={() => setFocused(false)}
          />
        </Animated.View>

        {/* The loop's own caret, right after the last letter it typed. */}
        <View style={styles.caretRow} pointerEvents="none">
          <Text style={styles.ghost} numberOfLines={1}>
            {current}
          </Text>
          <Animated.View style={[styles.caret, { backgroundColor: accent }, caretStyle]} />
        </View>

        <Animated.View pointerEvents="none" style={[styles.ripple, rippleStyle]} />
        <Pressable
          style={styles.send}
          onPress={submit}
          onPressIn={() => pressSend(true)}
          onPressOut={() => pressSend(false)}
          accessibilityRole="button"
          accessibilityLabel="Send"
        >
          <Animated.View style={[styles.sendDisc, sendStyle]}>
            <Canvas style={styles.sendCanvas}>
              <Path path={ARROW} style="stroke" strokeWidth={2.4} strokeCap="round" strokeJoin="round" color="#ffffff" />
            </Canvas>
          </Animated.View>
        </Pressable>
      </Animated.View>
    </Animated.View>
  );
}

const styles = StyleSheet.create({
  root: {
    width: WIDTH,
    height: HEIGHT,
  },
  picture: {
    position: 'absolute',
    left: 0,
    top: 0,
    borderRadius: PHOTO_RADIUS,
  },
  photo: {
    flex: 1,
    borderRadius: PHOTO_RADIUS,
  },
  chips: {
    position: 'absolute',
    left: PILL_X + CHIP_INSET,
    top: PILL_Y - CHIP_GAP - CHIP_HEIGHT,
    height: CHIP_HEIGHT,
    flexDirection: 'row',
    gap: 5,
    zIndex: CHIP_LAYER * LAYER_STEPS,
  },
  chip: {
    height: CHIP_HEIGHT,
    paddingLeft: 9,
    paddingRight: 8,
    borderRadius: CHIP_HEIGHT / 2,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 5,
  },
  chipText: {
    color: '#ffffff',
    fontSize: 11,
    lineHeight: 14,
    fontWeight: '700',
  },
  chipCross: {
    width: 8,
    height: 8,
  },
  pill: {
    position: 'absolute',
    left: PILL_X,
    top: PILL_Y,
    width: PILL_WIDTH,
    height: PILL_HEIGHT,
    zIndex: PILL_LAYER * LAYER_STEPS,
  },
  pillBody: {
    position: 'absolute',
    left: 0,
    top: 0,
    width: PILL_WIDTH,
    height: PILL_HEIGHT,
    borderRadius: PILL_HEIGHT / 2,
    backgroundColor: '#ffffff',
    boxShadow: '0px 6px 20px rgba(0, 0, 0, 0.07)',
  },
  sweepClip: {
    position: 'absolute',
    left: 0,
    top: 0,
    width: PILL_WIDTH,
    height: PILL_HEIGHT,
    borderRadius: PILL_HEIGHT / 2,
    overflow: 'hidden',
  },
  sweep: {
    width: SWEEP_WIDTH,
    height: PILL_HEIGHT,
  },
  pillEdge: {
    position: 'absolute',
    left: 0,
    top: 0,
    width: PILL_WIDTH,
    height: PILL_HEIGHT,
    borderRadius: PILL_HEIGHT / 2,
    borderWidth: 1,
    borderColor: '#e6e6e6',
  },
  plus: {
    position: 'absolute',
    left: PLUS_X - 22,
    top: PILL_HEIGHT / 2 - 22,
    width: 44,
    height: 44,
  },
  plusCanvas: {
    width: 44,
    height: 44,
  },
  field: {
    position: 'absolute',
    left: FIELD_X,
    top: 0,
    width: FIELD_WIDTH,
    height: PILL_HEIGHT,
  },
  input: {
    flex: 1,
    padding: 0,
    fontSize: 15,
    fontWeight: '500',
    textAlignVertical: 'center',
    includeFontPadding: false,
  },
  caretRow: {
    position: 'absolute',
    left: FIELD_X,
    top: 0,
    width: FIELD_WIDTH,
    height: PILL_HEIGHT,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 1,
  },
  ghost: {
    flexShrink: 1,
    opacity: 0,
    fontSize: 15,
    fontWeight: '500',
  },
  caret: {
    width: 2,
    height: 19,
    borderRadius: 1,
  },
  ripple: {
    position: 'absolute',
    left: SEND_X - PILL_X - SEND_SIZE / 2,
    top: (PILL_HEIGHT - SEND_SIZE) / 2,
    width: SEND_SIZE,
    height: SEND_SIZE,
    borderRadius: SEND_SIZE / 2,
    borderWidth: 2,
    borderColor: '#0b0b0b',
  },
  send: {
    position: 'absolute',
    left: SEND_X - PILL_X - SEND_SIZE / 2,
    top: (PILL_HEIGHT - SEND_SIZE) / 2,
    width: SEND_SIZE,
    height: SEND_SIZE,
  },
  sendDisc: {
    width: SEND_SIZE,
    height: SEND_SIZE,
    borderRadius: SEND_SIZE / 2,
    backgroundColor: '#0b0b0b',
  },
  sendCanvas: {
    width: SEND_SIZE,
    height: SEND_SIZE,
  },
});
