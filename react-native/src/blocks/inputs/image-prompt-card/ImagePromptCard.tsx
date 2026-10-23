import * as Haptics from 'expo-haptics';
import { Image } from 'expo-image';
import { type ReactNode, useCallback, useEffect, useEffectEvent, useRef, useState } from 'react';
import { Pressable, type StyleProp, StyleSheet, Text, TextInput, View, type ViewStyle } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  Easing,
  ReduceMotion,
  cubicBezier,
  useAnimatedStyle,
  useReducedMotion,
  useSharedValue,
  withDelay,
  withSpring,
  withTiming,
} from 'react-native-reanimated';
import Svg, { Path } from 'react-native-svg';
import { scheduleOnRN } from 'react-native-worklets';

/** A picture attached to the prompt. */
export type ImagePromptCardImage = {
  title: string;
  /** Address of the photo; it is shown square. */
  photo: string;
  /** Flat colour shown until the photo arrives. */
  tint: string;
};

type Props = {
  /** Controlled text. Leave it out and the component keeps its own. */
  value?: string;
  onChangeText?: (text: string) => void;
  /** Adds three images, holds one up, types the script, sends and starts over. */
  autoPlay?: boolean;
  placeholder?: string;
  /** The prompt autoPlay types. */
  script?: string;
  model?: string;
  agent?: string;
  /** The images the plus attaches, in turn. */
  images?: ImagePromptCardImage[];
  /** Colour of the enhance wand. */
  accent?: string;
  /** Called with the prompt and its images when the arrow or the return key is pressed. */
  onSend?: (prompt: string, images: ImagePromptCardImage[]) => void;
  onAdd?: (image: ImagePromptCardImage) => void;
  onRemove?: (image: ImagePromptCardImage) => void;
  onEnhance?: () => void;
  onModel?: () => void;
  onAgent?: () => void;
  /** Called with "settings", "history", "ideas", "explore", "web", "focus" or "mic" for the bare icons. */
  onTool?: (name: string) => void;
};

const IMAGES: ImagePromptCardImage[] = [
  {
    title: 'Temple on a lake, Bali',
    photo: 'https://images.unsplash.com/photo-1537996194471-e657df975ab4?auto=format&fit=crop&w=400&h=400&q=80',
    tint: '#999787',
  },
  {
    title: 'White houses, Santorini',
    photo: 'https://images.unsplash.com/photo-1570077188670-e3a8d69ac5ff?auto=format&fit=crop&w=400&h=400&q=80',
    tint: '#8f9aa2',
  },
  {
    title: 'Balloons over Cappadocia',
    photo: 'https://images.unsplash.com/photo-1641128324972-af3212f0f6bd?auto=format&fit=crop&w=400&h=400&q=80',
    tint: '#7c8986',
  },
];

const WIDTH = 350;
/** The box the component is laid out in: the pickers, and the card with its row of images open. */
const HEIGHT = 208;
const HEADER_HEIGHT = 30;
const CARD_Y = 42;
/** Height of the card without images; the row adds `ROW_HEIGHT`. */
const REST_HEIGHT = 110;
const ROW_HEIGHT = 56;
const RADIUS = 24;

const THUMB = 44;
/** Centre of the first thumbnail, and the distance to the next. */
const THUMB_X = 38;
const THUMB_Y = 38;
const THUMB_STEP = 54;
/** The most thumbnails the row holds. */
const MOST = 5;
const TEXT_X = 18;
const TEXT_Y = 15;
/** Centre of the bottom row from the card's bottom edge. */
const TOOL_Y = 30;
const PLUS_X = 30;
const SEND_X = 319;
const SEND_SIZE = 38;
/** Centre of the send button in the card while the row is open, which it is whenever thumbnails dive. */
const SEND_Y = REST_HEIGHT + ROW_HEIGHT - TOOL_Y;
const PEEK = 136;
/** Room between the preview and the card's top edge. */
const PEEK_GAP = 8;
/** The badge hangs this far off a thumbnail's corner. Its box is that much larger all round, so the badge takes touches inside it. */
const BADGE_OUT = 12;
const THUMB_BOX = THUMB + BADGE_OUT * 2;

const FONT_SIZE = 16;
const LINE = 24;
const INK = '#111111';

// autoPlay (seconds). Keys land `KEY` apart on average, stretched by `RHYTHM` in turn.
const ADD_AT = [0.8, 1.25, 1.7];
const LIFT_AT = 2.6;
const DROP_AT = 3.5;
const TYPE_AT = 4.0;
/** The caret shows in the field this long before the first key. */
const CARET_LEAD = 0.35;
const KEY = 0.048;
const RHYTHM = [1.0, 0.72, 1.3, 0.85, 1.12, 0.64, 1.42, 0.95];
/** From the last key to the tap on the arrow. */
const SEND_AFTER = 0.5;
const LOOP = 9.4;
/** After the last touch, the card empties itself and the script waits this long. */
const RESUME_AFTER = 4.0;
/** Time the row takes to close. */
const ROW_SETTLE = 0.5;

const BLINK = 0.53;
const PRESS = 0.12;
const POP = 0.16;
const WIPE = 0.18;
const HOLD_TIME = 0.3;

// A thumbnail's flight in: from 0.3 scale and a tilt, bulging to the right of a straight path.
const FLY_SCALE = 0.3;
const FLY_TILT = -20;
const FLY_ARC = 26;
const LIFT_SCALE = 1.1;

// A send: the thumbnails close up into a fanned pile, then dive into the button one after another.
const PILE_STEP = 7;
const PILE_TILT = 7;
const STACK_TIME = 0.26;
const SINK_TIME = 0.42;
const SINK_STAGGER = 0.05;
const SINK_SCALE = 0.15;
const SINK_ARC = 22;
/** From the first thumbnail diving to the arrow turning into a ring. */
const RING_AFTER = 0.3;
const SPIN_TIME = 0.7;

const LAND = { mass: 1, stiffness: 210, damping: 19 };
const SLIDE = { mass: 1, stiffness: 320, damping: 26 };
const ROW = { mass: 1, stiffness: 260, damping: 24 };
const LIFT = { mass: 1, stiffness: 300, damping: 20 };
const TURN = { mass: 1, stiffness: 260, damping: 18 };
const SETTLE = { mass: 1, stiffness: 420, damping: 14 };
const SINK = Easing.bezier(0.5, 0, 0.85, 0.6);

const EASE_OUT = Easing.bezier(0, 0, 0.58, 1);
const EASE_IN_OUT = Easing.bezier(0.42, 0, 0.58, 1);
const PRESS_EASE = cubicBezier(0.23, 1, 0.32, 1);

// Icons, as outlines in a 20 pt box (the model's mark in 16 pt, the badge's cross in 10 pt).
const MARK_ICON = 'M8 2.5 L8 13.5 M3.24 5.25 L12.76 10.75 M3.24 10.75 L12.76 5.25';
const X_ICON = 'M3 3 L7 7 M7 3 L3 7';
const PLUS_ICON = 'M10 4.5 L10 15.5 M4.5 10 L15.5 10';
const ARROW_ICON = 'M10 15.5 L10 4.5 M5.5 9 L10 4.5 L14.5 9';
const BULB_ICON =
  'M7 12.3 C5.28 11.01 4.58 8.76 5.26 6.72 C5.94 4.68 7.85 3.3 10 3.3 C12.15 3.3 14.06 4.68 14.74 6.72 ' +
  'C15.42 8.76 14.72 11.01 13 12.3 L13 14.5 L7 14.5 Z M8.25 17 L11.75 17';
const SCOPE_ICON =
  'M3.6 10.2 L11.6 6 L13.1 8.9 L5.1 13.1 Z M11.6 6 L15 4.2 L16.5 7.1 L13.1 8.9 M8.4 11.6 L6 17 M8.4 11.6 L11 17';
const GLOBE_ICON =
  'M16.5 10 C16.5 13.59 13.59 16.5 10 16.5 C6.41 16.5 3.5 13.59 3.5 10 C3.5 6.41 6.41 3.5 10 3.5 C13.59 3.5 16.5 6.41 16.5 10 Z ' +
  'M3.5 10 L16.5 10 M10 3.5 C7.4 5.5 7.4 14.5 10 16.5 M10 3.5 C12.6 5.5 12.6 14.5 10 16.5';
const TARGET_ICON =
  'M16.5 10 C16.5 13.59 13.59 16.5 10 16.5 C6.41 16.5 3.5 13.59 3.5 10 C3.5 6.41 6.41 3.5 10 3.5 C13.59 3.5 16.5 6.41 16.5 10 Z ' +
  'M12.5 10 C12.5 11.38 11.38 12.5 10 12.5 C8.62 12.5 7.5 11.38 7.5 10 C7.5 8.62 8.62 7.5 10 7.5 C11.38 7.5 12.5 8.62 12.5 10 Z';
const MIC_ICON =
  'M10 2.5 C11.38 2.5 12.5 3.62 12.5 5 L12.5 9.5 C12.5 10.88 11.38 12 10 12 C8.62 12 7.5 10.88 7.5 9.5 L7.5 5 ' +
  'C7.5 3.62 8.62 2.5 10 2.5 Z M5 9.5 C5 12.26 7.24 14.5 10 14.5 C12.76 14.5 15 12.26 15 9.5 M10 14.5 L10 17.5';
const WAND_ICON =
  'M3.5 16.5 L13.5 6.5 M15.5 2.5 L15.5 5.5 M14 4 L17 4 M16.75 10 L16.75 12.5 M15.5 11.25 L18 11.25 M8 2.75 L8 5.25 M6.75 4 L9.25 4';
const SLIDERS_ICON =
  'M3.5 6.5 L10.5 6.5 M14.5 6.5 L16.5 6.5 M3.5 13.5 L5.5 13.5 M9.5 13.5 L16.5 13.5 ' +
  'M14.5 6.5 C14.5 7.6 13.6 8.5 12.5 8.5 C11.4 8.5 10.5 7.6 10.5 6.5 C10.5 5.4 11.4 4.5 12.5 4.5 C13.6 4.5 14.5 5.4 14.5 6.5 Z ' +
  'M9.5 13.5 C9.5 14.6 8.6 15.5 7.5 15.5 C6.4 15.5 5.5 14.6 5.5 13.5 C5.5 12.4 6.4 11.5 7.5 11.5 C8.6 11.5 9.5 12.4 9.5 13.5 Z';
const CLOCK_ICON =
  'M16.5 10 C16.5 13.59 13.59 16.5 10 16.5 C6.41 16.5 3.5 13.59 3.5 10 C3.5 6.41 6.41 3.5 10 3.5 C13.59 3.5 16.5 6.41 16.5 10 Z ' +
  'M10 6.5 L10 10 L12.5 11.5';

// The send button's ring: `RING_TRIM` of a circle, drawn clockwise from three o'clock.
const RING = 15;
const RING_TRIM = 0.72;
const RING_END = 2 * Math.PI * RING_TRIM;
const RING_ICON =
  `M${RING} ${RING / 2} A${RING / 2} ${RING / 2} 0 1 1 ` +
  `${(RING / 2) * (1 + Math.cos(RING_END))} ${(RING / 2) * (1 + Math.sin(RING_END))}`;

type Thumb = {
  id: number;
  image: ImagePromptCardImage;
  /** The slot it flew to, and where the plus was when it took off. */
  home: number;
  from: { x: number; y: number };
  /** Popped out by its badge, about to be dropped from the row. */
  leaving: boolean;
};

function lerp(from: number, to: number, t: number) {
  'worklet';
  return from + (to - from) * t;
}

function clamp01(t: number) {
  'worklet';
  return Math.min(1, Math.max(0, t));
}

/** A fade over `seconds`. It also runs under reduced motion, where the fades are all that is left. */
function easeOut(seconds: number) {
  return { duration: seconds * 1000, easing: EASE_OUT, reduceMotion: ReduceMotion.Never };
}

/** Where a `size` wide square goes to have its centre at `x`, `y`. */
function centred(x: number, y: number, size: number): ViewStyle {
  return { position: 'absolute', left: x - size / 2, top: y - size / 2 };
}

function sameImage(a: ImagePromptCardImage, b: ImagePromptCardImage) {
  return a.title === b.title && a.photo === b.photo && a.tint === b.tint;
}

function tap(style: Haptics.ImpactFeedbackStyle) {
  if (process.env.EXPO_OS !== 'web') Haptics.impactAsync(style).catch(() => {});
}

function Icon({ d, size = 20, color, strokeWidth }: { d: string; size?: number; color: string; strokeWidth: number }) {
  return (
    <Svg width={size} height={size} viewBox={`0 0 ${size} ${size}`}>
      <Path d={d} fill="none" stroke={color} strokeWidth={strokeWidth} strokeLinecap="round" strokeLinejoin="round" />
    </Svg>
  );
}

type PressProps = {
  onPress?: () => void;
  accessibilityLabel?: string;
  /** Where the button goes in its parent. */
  place?: StyleProp<ViewStyle>;
  style: StyleProp<ViewStyle>;
  children: ReactNode;
};

/** A button that gives a little under the finger. */
function Press({ onPress, accessibilityLabel, place, style, children }: PressProps) {
  return (
    <Pressable onPress={onPress} accessibilityRole="button" accessibilityLabel={accessibilityLabel} style={place}>
      {({ pressed }) => (
        <Animated.View
          style={[
            style,
            {
              transform: [{ scale: pressed ? 0.94 : 1 }],
              transitionProperty: 'transform',
              transitionDuration: 130,
              transitionTimingFunction: PRESS_EASE,
            },
          ]}
        >
          {children}
        </Animated.View>
      )}
    </Pressable>
  );
}

type ToolProps = {
  d: string;
  label: string;
  place?: StyleProp<ViewStyle>;
  onPress: () => void;
};

/** One of the bare icons. */
function Tool({ d, label, place, onPress }: ToolProps) {
  return (
    <Press onPress={onPress} accessibilityLabel={label} place={place} style={styles.tool}>
      <Icon d={d} color="#2b2b33" strokeWidth={1.5} />
    </Press>
  );
}

/** A remote photo over its flat tint, faded in when it arrives. */
function Photo({ image, style }: { image: ImagePromptCardImage; style: StyleProp<ViewStyle> }) {
  return (
    <View style={[style, { backgroundColor: image.tint }]}>
      <Image source={image.photo} contentFit="cover" transition={200} style={StyleSheet.absoluteFill} />
    </View>
  );
}

type ThumbnailProps = {
  thumb: Thumb;
  /** Its place among the thumbnails that stay. */
  slot: number;
  /** Held up by a long press. */
  held: boolean;
  stacked: boolean;
  departed: boolean;
  reducedMotion: boolean;
  onLift: (id: number) => void;
  onDrop: () => void;
  onRemove: (id: number) => void;
  /** Called once it has popped out, so it can be dropped from the row. */
  onGone: (id: number) => void;
};

/** One thumbnail, placed by its slot and moved by its flight in and its way out: the photo in a white frame, and its badge. */
function Thumbnail({ thumb, slot, held, stacked, departed, reducedMotion, onLift, onDrop, onRemove, onGone }: ThumbnailProps) {
  const { id, image, leaving } = thumb;
  const piled = stacked && !reducedMotion;
  const restX = THUMB_X + slot * (piled ? PILE_STEP : THUMB_STEP);
  const restTilt = piled ? (slot - 1) * PILE_TILT : 0;
  const homeX = THUMB_X + thumb.home * THUMB_STEP;
  const fromX = thumb.from.x;
  const fromY = thumb.from.y;

  // The flight in from the plus (0…1), the dive into the send button (0…1), the pop out (0…1) and the lift (0…1).
  const landed = useSharedValue(0);
  const sunk = useSharedValue(0);
  const gone = useSharedValue(0);
  const lift = useSharedValue(0);
  const x = useSharedValue(restX);
  const tilt = useSharedValue(0);
  /** The badge makes way while the thumbnails are in a pile. */
  const pile = useSharedValue(0);

  // It is laid at the plus first; a frame later it takes off for its slot.
  useEffect(() => {
    landed.set(reducedMotion ? withTiming(1, easeOut(POP)) : withSpring(1, LAND));
  }, [reducedMotion, landed]);

  useEffect(() => {
    if (!departed) return;
    sunk.set(
      reducedMotion
        ? withTiming(1, easeOut(POP))
        : withDelay(slot * SINK_STAGGER * 1000, withTiming(1, { duration: SINK_TIME * 1000, easing: SINK })),
    );
  }, [departed, slot, reducedMotion, sunk]);

  useEffect(() => {
    x.set(reducedMotion ? restX : withSpring(restX, SLIDE));
    tilt.set(reducedMotion ? restTilt : withSpring(restTilt, SLIDE));
  }, [restX, restTilt, reducedMotion, x, tilt]);

  useEffect(() => {
    if (!leaving) return;
    gone.set(
      withTiming(1, easeOut(POP), (finished) => {
        if (finished) scheduleOnRN(onGone, id);
      }),
    );
  }, [leaving, id, onGone, gone]);

  useEffect(() => {
    lift.set(reducedMotion ? withTiming(held ? 1 : 0, easeOut(POP)) : withSpring(held ? 1 : 0, LIFT));
  }, [held, reducedMotion, lift]);

  useEffect(() => {
    pile.set(withTiming(stacked ? 1 : 0, easeOut(POP)));
  }, [stacked, pile]);

  const flight = useAnimatedStyle(() => {
    const a = landed.get();
    const d = sunk.get();
    // Under reduced motion a thumbnail fades in and out where it stands.
    const away = reducedMotion ? 0 : 1 - a;
    const down = reducedMotion ? 0 : d;
    const pileX = THUMB_X + slot * PILE_STEP;
    return {
      opacity: clamp01(a * 4) * clamp01((1 - d) / 0.25) * clamp01(1 - gone.get()),
      // Read from the end: the flight's scale and the two tilts turn the thumbnail only; its offsets and the pop follow.
      transform: [
        { translateX: x.get() },
        { translateY: THUMB_Y },
        { scale: lerp(1, 0.6, gone.get()) },
        {
          translateX:
            (fromX - homeX) * away + FLY_ARC * Math.sin(Math.PI * clamp01(1 - away)) * (reducedMotion ? 0 : 1) + (SEND_X - pileX) * down,
        },
        { translateY: (fromY - THUMB_Y) * away + (SEND_Y - THUMB_Y) * down - SINK_ARC * Math.sin(Math.PI * clamp01(down)) },
        { rotate: `${FLY_TILT * away + tilt.get()}deg` },
        { scale: lerp(1, FLY_SCALE, away) * lerp(1, SINK_SCALE, down) },
      ],
    };
  });
  const frameStyle = useAnimatedStyle(() => ({ transform: [{ scale: reducedMotion ? 1 : lerp(1, LIFT_SCALE, lift.get()) }] }));
  // The shadow deepens under a held thumbnail: one shadow fades into the other.
  const restShade = useAnimatedStyle(() => ({ opacity: clamp01(1 - lift.get()) }));
  const heldShade = useAnimatedStyle(() => ({ opacity: clamp01(lift.get()) }));
  const badgeStyle = useAnimatedStyle(() => ({ opacity: clamp01(1 - lift.get()) * (1 - pile.get()) }));

  const hold = Gesture.LongPress()
    .minDuration(HOLD_TIME * 1000)
    .runOnJS(true)
    .onStart(() => onLift(id))
    .onEnd(() => onDrop());

  return (
    <Animated.View pointerEvents="box-none" style={[styles.thumb, { zIndex: held ? 10 : slot }, flight]}>
      <GestureDetector gesture={hold}>
        <Animated.View accessible accessibilityRole="image" accessibilityLabel={image.title} style={[styles.frame, frameStyle]}>
          <Animated.View style={[styles.shade, styles.shadeRest, restShade]} />
          <Animated.View style={[styles.shade, styles.shadeHeld, heldShade]} />
          <View style={styles.frameFill}>
            <Photo image={image} style={styles.thumbPhoto} />
          </View>
        </Animated.View>
      </GestureDetector>
      <Animated.View style={[styles.badge, badgeStyle]}>
        <Press onPress={() => onRemove(id)} accessibilityLabel={`Remove ${image.title}`} style={styles.badgeHit}>
          <View style={styles.badgeDisc}>
            <Icon d={X_ICON} size={10} color="#2b2b33" strokeWidth={1.4} />
          </View>
        </Press>
      </Animated.View>
    </Animated.View>
  );
}

/**
 * A prompt card that takes reference images. Each one flies in from the plus along a short arc and
 * lands in a row that opens above the text; a badge pops it out again and a long press holds it up
 * larger above the card. On send the thumbnails pile up and sink into the send button, which turns
 * a ring once.
 */
export function ImagePromptCard({
  value: controlledValue,
  onChangeText,
  autoPlay = false,
  placeholder = 'Describe what to make',
  script = 'Make a moodboard from these photos',
  model = 'Nova 2.1',
  agent = 'Creative Agent',
  images = IMAGES,
  accent = '#6b4ee6',
  onSend,
  onAdd,
  onRemove,
  onEnhance,
  onModel,
  onAgent,
  onTool,
}: Props) {
  const reducedMotion = useReducedMotion();
  const controlled = controlledValue != null;
  const input = useRef<TextInput>(null);

  const [inner, setInner] = useState('');
  const [focused, setFocused] = useState(false);
  const [playing, setPlaying] = useState(autoPlay && !controlled);
  /** autoPlay has put its caret in the field. */
  const [caretShown, setCaretShown] = useState(false);
  const [thumbs, setThumbs] = useState<Thumb[]>([]);
  /** Counts the images attached so far: it names the thumbnail, picks the next image and turns the plus. */
  const added = useRef(0);
  /** The thumbnail held up by a long press, and the one the preview shows while it fades. */
  const [lifted, setLifted] = useState<number | null>(null);
  const [peek, setPeek] = useState<{ image: ImagePromptCardImage; x: number } | null>(null);
  /** A send is under way: the thumbnails pile up, then sink into the button. */
  const busy = useRef(false);
  /** Whether that send has a pile to sink, and how long its dive takes. */
  const plan = useRef({ had: false, dive: SINK_TIME });
  const [stacked, setStacked] = useState(false);
  const [departed, setDeparted] = useState(false);
  /** Counts sends and resets, to start and cancel what follows a send. */
  const [run, setRun] = useState(0);
  /** Counts touches, to restart the wait before autoPlay resumes. */
  const [touches, setTouches] = useState(0);

  const value = controlledValue ?? inner;
  const empty = value.length === 0;
  const live = thumbs.filter((thumb) => !thumb.leaving);
  const rowOpen = live.length > 0;
  const caret = playing && caretShown && !focused;
  const shown = lifted != null;

  /** The row of images, from closed (0) to open (1): it moves the text and the bottom row down and grows the card. */
  const row = useSharedValue(0);
  /** Quarter turns of the plus. */
  const turn = useSharedValue(0);
  /** The arrow is held down by the script. */
  const hold = useSharedValue(0);
  const gulp = useSharedValue(0);
  /** The arrow (0) gives way to the ring (1), which turns once. */
  const sending = useSharedValue(0);
  const spin = useSharedValue(0);
  /** The prompt is faded out, just before the field empties. */
  const wipe = useSharedValue(1);
  const blink = useSharedValue(1);
  const peekShown = useSharedValue(0);

  const slotOf = (index: number) => thumbs.slice(0, index).filter((thumb) => !thumb.leaving).length;

  /** Any touch outside the field stops the script where it is. */
  const touch = () => {
    setPlaying(false);
    setCaretShown(false);
    hold.set(withTiming(0, easeOut(PRESS)));
    setLifted(null);
    setTouches((count) => count + 1);
  };

  const edit = (next: string) => {
    if (next === value) return;
    if (!controlled) setInner(next);
    onChangeText?.(next);
  };

  /** Attaches the next image: it starts at the plus and flies to the end of the row. */
  const add = (byHand: boolean) => {
    if (busy.current || images.length === 0 || live.length >= MOST) return;
    const image = images[added.current % images.length];
    const from = { x: PLUS_X, y: REST_HEIGHT + (rowOpen ? ROW_HEIGHT : 0) - TOOL_Y };
    const thumb = { id: added.current, image, home: live.length, from, leaving: false };
    setThumbs((list) => [...list, thumb]);
    added.current += 1;
    turn.set(reducedMotion ? added.current : withSpring(added.current, TURN));
    if (byHand) tap(Haptics.ImpactFeedbackStyle.Light);
    onAdd?.(image);
  };

  /** Pops a thumbnail out; the ones after it slide over, and the row closes after the last. */
  const remove = (id: number, byHand: boolean) => {
    if (busy.current) return;
    const thumb = thumbs.find((entry) => entry.id === id && !entry.leaving);
    if (!thumb) return;
    if (byHand) tap(Haptics.ImpactFeedbackStyle.Light);
    onRemove?.(thumb.image);
    setThumbs((list) => list.map((entry) => (entry.id === id ? { ...entry, leaving: true } : entry)));
  };

  const drop = useCallback((id: number) => setThumbs((list) => list.filter((entry) => entry.id !== id)), []);

  const lift = (id: number, byHand: boolean) => {
    if (busy.current) return;
    const index = thumbs.findIndex((entry) => entry.id === id && !entry.leaving);
    if (index < 0) return;
    setPeek({ image: thumbs[index].image, x: THUMB_X + slotOf(index) * THUMB_STEP });
    setLifted(id);
    if (byHand) tap(Haptics.ImpactFeedbackStyle.Medium);
  };

  const send = (byHand: boolean) => {
    const prompt = value.trim();
    input.current?.blur();
    if (busy.current || (prompt.length === 0 && live.length === 0)) return;
    busy.current = true;
    hold.set(withTiming(0, easeOut(PRESS)));
    setCaretShown(false);
    setLifted(null);
    if (byHand) tap(Haptics.ImpactFeedbackStyle.Light);
    onSend?.(
      prompt,
      live.map((thumb) => thumb.image),
    );
    // The prompt fades and the thumbnails close up at once; the rest follows on its own clock.
    const had = thumbs.length > 0 && !reducedMotion;
    plan.current = { had, dive: SINK_TIME + Math.max(0, live.length - 1) * SINK_STAGGER };
    wipe.set(withTiming(0, easeOut(WIPE)));
    if (had) setStacked(true);
    setRun((count) => count + 1);
  };

  /** Back to the empty card at once, whatever was going on. */
  const reset = () => {
    busy.current = false;
    hold.set(withTiming(0, easeOut(PRESS)));
    setCaretShown(false);
    setLifted(null);
    setThumbs([]);
    setStacked(false);
    setDeparted(false);
    sending.set(withTiming(0, easeOut(0.15)));
    spin.set(0);
    gulp.set(withSpring(0, SETTLE));
    wipe.set(1);
    setRun((count) => count + 1);
    edit('');
  };

  const addScript = useEffectEvent(() => add(false));
  const liftScript = useEffectEvent(() => {
    if (live.length > 1) lift(live[1].id, false);
  });
  const typeScript = useEffectEvent((typed: string) => edit(typed));
  const sendScript = useEffectEvent(() => send(false));

  // autoPlay: one loop after another, every step at its own moment after the loop began.
  useEffect(() => {
    if (!playing) return;
    const keys = Array.from(script);
    let timers: ReturnType<typeof setTimeout>[] = [];
    const loop = (origin: number) => {
      const at = (seconds: number, step: () => void) => {
        timers.push(setTimeout(step, Math.max(0, origin + seconds * 1000 - performance.now())));
      };
      timers = [];
      ADD_AT.forEach((when) => at(when, () => addScript()));
      at(LIFT_AT, () => liftScript());
      at(DROP_AT, () => setLifted(null));
      let when = TYPE_AT;
      at(when - CARET_LEAD, () => setCaretShown(true));
      keys.forEach((_, index) => {
        const typed = keys.slice(0, index + 1).join('');
        at(when, () => typeScript(typed));
        when += KEY * RHYTHM[index % RHYTHM.length];
      });
      when += SEND_AFTER;
      at(when - PRESS, () => hold.set(withTiming(1, easeOut(PRESS))));
      at(when, () => sendScript());
      // The ring has turned and the row has closed before the loop is over.
      const period = Math.max(LOOP, when + STACK_TIME + RING_AFTER + SPIN_TIME + ROW_SETTLE + ADD_AT[0]);
      at(period, () => loop(origin + period * 1000));
    };
    loop(performance.now());
    return () => timers.forEach(clearTimeout);
  }, [playing, script, hold]);

  const clear = useEffectEvent(() => edit(''));

  // What a send sets off: the pile, its dive into the button, the ring, and the empty card.
  useEffect(() => {
    if (!busy.current) return;
    const { had, dive } = plan.current;
    const diveAt = had ? STACK_TIME : 0;
    const ringAt = diveAt + (had ? RING_AFTER : WIPE);
    const gulpAt = ringAt + (had ? dive - RING_AFTER : POP);
    const at = (seconds: number, step: () => void) => setTimeout(step, seconds * 1000);
    const timers = [
      at(diveAt, () => setDeparted(true)),
      at(ringAt, () => {
        clear();
        wipe.set(1);
        sending.set(withTiming(1, easeOut(0.15)));
        spin.set(withTiming(1, { duration: SPIN_TIME * 1000, easing: EASE_IN_OUT, reduceMotion: ReduceMotion.Never }));
      }),
      // The pile is inside the button: it swallows, and the row closes.
      at(gulpAt, () => {
        if (had) gulp.set(withTiming(1, easeOut(PRESS)));
        setThumbs([]);
        setStacked(false);
        setDeparted(false);
      }),
      at(gulpAt + PRESS, () => gulp.set(withSpring(0, SETTLE))),
      at(ringAt + SPIN_TIME, () => {
        sending.set(withTiming(0, easeOut(0.15)));
        // Unseen: a whole turn later the ring stands as it did.
        spin.set(0);
        busy.current = false;
      }),
    ];
    return () => timers.forEach(clearTimeout);
  }, [run, gulp, sending, spin, wipe]);

  /** Empties the card if it was left alone with an empty field, and says whether the script may resume. */
  const tidy = useEffectEvent(() => {
    if (!empty || busy.current) return false;
    live.forEach((thumb) => remove(thumb.id, false));
    return true;
  });

  // Left alone with an empty field, the card empties itself and the script resumes.
  useEffect(() => {
    if (!autoPlay || controlled || playing || focused) return;
    let timer = setTimeout(() => {
      if (!tidy()) return;
      timer = setTimeout(() => setPlaying(true), ROW_SETTLE * 1000);
    }, RESUME_AFTER * 1000);
    return () => clearTimeout(timer);
  }, [autoPlay, controlled, playing, focused, touches]);

  // The caret stays solid while keys arrive and blinks once they stop.
  useEffect(() => {
    blink.set(withTiming(1, easeOut(0.08)));
    if (!playing || !caretShown) return;
    let on = true;
    const timer = setInterval(() => {
      on = !on;
      blink.set(withTiming(on ? 1 : 0, easeOut(0.08)));
    }, BLINK * 1000);
    return () => clearInterval(timer);
  }, [value, playing, caretShown, blink]);

  useEffect(() => {
    row.set(reducedMotion ? (rowOpen ? 1 : 0) : withSpring(rowOpen ? 1 : 0, ROW));
  }, [rowOpen, reducedMotion, row]);

  useEffect(() => {
    peekShown.set(shown && !reducedMotion ? withSpring(1, LIFT) : withTiming(shown ? 1 : 0, easeOut(POP)));
  }, [shown, reducedMotion, peekShown]);

  const sheetStyle = useAnimatedStyle(() => ({ height: REST_HEIGHT + ROW_HEIGHT * row.get() }));
  const fieldStyle = useAnimatedStyle(() => ({
    // Under the row the text has the card's whole width; beside the wand it stops 30 points short.
    width: WIDTH - TEXT_X * 2 - 30 * (1 - row.get()),
    transform: [{ translateY: TEXT_Y + ROW_HEIGHT * row.get() }],
  }));
  const toolbarStyle = useAnimatedStyle(() => ({
    transform: [{ translateY: REST_HEIGHT - TOOL_Y * 2 + ROW_HEIGHT * row.get() }],
  }));
  const caretStyle = useAnimatedStyle(() => ({ opacity: caret ? blink.get() : 0 }));
  const wipeStyle = useAnimatedStyle(() => ({ opacity: wipe.get() }));
  const plusStyle = useAnimatedStyle(() => ({ transform: [{ rotate: `${turn.get() * 90}deg` }] }));
  const sendStyle = useAnimatedStyle(() => ({
    transform: [{ scale: lerp(1, 0.9, hold.get()) * lerp(1, 1.1, gulp.get()) }],
  }));
  const arrowStyle = useAnimatedStyle(() => {
    const t = sending.get();
    return { opacity: clamp01(1 - t), transform: [{ scale: reducedMotion ? 1 : lerp(1, 0.5, t) }] };
  });
  const ringStyle = useAnimatedStyle(() => {
    const t = sending.get();
    return {
      opacity: clamp01(t),
      transform: [{ scale: reducedMotion ? 1 : lerp(0.5, 1, t) }, { rotate: `${spin.get() * 360}deg` }],
    };
  });
  const previewStyle = useAnimatedStyle(() => {
    const t = peekShown.get();
    return { opacity: clamp01(t), transform: [{ scale: reducedMotion ? 1 : lerp(0.3, 1, t) }] };
  });

  const onFocus = () => {
    setFocused(true);
    // A touch takes the field over from the script and starts from an empty card.
    if (playing) {
      setPlaying(false);
      reset();
    }
  };

  const tool = (name: string) => () => {
    touch();
    onTool?.(name);
  };

  return (
    <View style={styles.box}>
      {/* The pickers above the card: model and agent on the left, two bare icons on the right. */}
      <View style={styles.header}>
        <Press onPress={onModel} style={styles.chip}>
          <Icon d={MARK_ICON} size={16} color={INK} strokeWidth={1.7} />
          <Text style={styles.chipLabel} numberOfLines={1}>
            {model}
          </Text>
        </Press>
        <Press onPress={onAgent} style={styles.chip}>
          {/* A small lilac avatar: a head and shoulders. */}
          <View style={styles.avatar}>
            <View style={styles.avatarHead} />
            <View style={styles.avatarShoulders} />
          </View>
          <Text style={styles.chipLabel} numberOfLines={1}>
            {agent}
          </Text>
        </Press>
        <View style={styles.spacer} />
        <Tool d={SLIDERS_ICON} label="Settings" onPress={tool('settings')} />
        <Tool d={CLOCK_ICON} label="History" onPress={tool('history')} />
      </View>

      <View pointerEvents="box-none" style={styles.card}>
        <Animated.View style={[styles.sheet, sheetStyle]} />

        <Press
          onPress={() => {
            touch();
            onEnhance?.();
          }}
          accessibilityLabel="Enhance the prompt"
          place={centred(WIDTH - 28, 28, 36)}
          style={styles.button}
        >
          <Icon d={WAND_ICON} color={accent} strokeWidth={1.6} />
        </Press>

        {/* The real field, with autoPlay's caret hanging off an invisible copy of the text. */}
        <Animated.View style={[styles.field, fieldStyle]}>
          <View pointerEvents="none" accessibilityElementsHidden importantForAccessibility="no-hide-descendants" style={styles.hintLine}>
            <Text style={[styles.text, styles.hint, { opacity: empty ? 1 : 0 }]} numberOfLines={1}>
              {placeholder}
            </Text>
          </View>
          <View pointerEvents="none" accessibilityElementsHidden importantForAccessibility="no-hide-descendants" style={styles.copyLine}>
            {/* Non-breaking spaces, so a space typed last moves the caret too. */}
            <Text style={[styles.text, styles.copy]} numberOfLines={1}>
              {value.replace(/ /g, ' ')}
            </Text>
            {/* Before the placeholder's first letter, not on it. */}
            <Animated.View style={[styles.caret, { marginLeft: empty ? -2 : 1 }, caretStyle]} />
          </View>
          <Animated.View style={[styles.inputSlot, wipeStyle]}>
            <TextInput
              ref={input}
              value={value}
              onChangeText={edit}
              onFocus={onFocus}
              onBlur={() => setFocused(false)}
              onSubmitEditing={() => send(true)}
              returnKeyType="send"
              selectionColor={accent}
              cursorColor={accent}
              selectionHandleColor={accent}
              underlineColorAndroid="transparent"
              accessibilityLabel={placeholder}
              style={styles.input}
            />
          </Animated.View>
        </Animated.View>

        {/* The bottom row: the plus, three tools, and on the right two more and the send button. */}
        <Animated.View pointerEvents="box-none" style={[styles.toolbar, toolbarStyle]}>
          <Press
            onPress={() => {
              touch();
              add(true);
            }}
            accessibilityLabel="Add an image"
            place={centred(PLUS_X, TOOL_Y, 36)}
            style={styles.button}
          >
            <Animated.View style={plusStyle}>
              <Icon d={PLUS_ICON} color={INK} strokeWidth={1.6} />
            </Animated.View>
          </Press>

          <View style={styles.divider} />
          <Tool d={BULB_ICON} label="Ideas" place={centred(78, TOOL_Y, 34)} onPress={tool('ideas')} />
          <Tool d={SCOPE_ICON} label="Explore" place={centred(114, TOOL_Y, 34)} onPress={tool('explore')} />
          <Tool d={GLOBE_ICON} label="Search the web" place={centred(150, TOOL_Y, 34)} onPress={tool('web')} />
          <Tool d={TARGET_ICON} label="Focus" place={centred(244, TOOL_Y, 34)} onPress={tool('focus')} />
          <Tool d={MIC_ICON} label="Dictate" place={centred(280, TOOL_Y, 34)} onPress={tool('mic')} />

          <Press
            onPress={() => {
              touch();
              send(true);
            }}
            accessibilityLabel="Send"
            place={centred(SEND_X, TOOL_Y, SEND_SIZE)}
            style={styles.send}
          >
            <Animated.View style={[styles.sendFill, sendStyle]}>
              <Animated.View style={[styles.arrow, arrowStyle]}>
                <Icon d={ARROW_ICON} color="#ffffff" strokeWidth={2} />
              </Animated.View>
              <Animated.View style={[styles.ring, ringStyle]}>
                <Svg width={RING + 2} height={RING + 2} viewBox={`-1 -1 ${RING + 2} ${RING + 2}`}>
                  <Path d={RING_ICON} fill="none" stroke="#ffffff" strokeWidth={2} strokeLinecap="round" />
                </Svg>
              </Animated.View>
            </Animated.View>
          </Press>
        </Animated.View>

        <View pointerEvents="box-none" style={styles.thumbs}>
          {thumbs.map((thumb, index) => (
            <Thumbnail
              key={thumb.id}
              thumb={thumb}
              slot={slotOf(index)}
              held={lifted === thumb.id}
              stacked={stacked}
              departed={departed}
              reducedMotion={reducedMotion}
              onLift={(id) => {
                touch();
                lift(id, true);
              }}
              onDrop={() => setLifted(null)}
              onRemove={(id) => {
                touch();
                remove(id, true);
              }}
              onGone={drop}
            />
          ))}
        </View>
      </View>

      {/* The held thumbnail shown larger, standing on the card's top edge above it. */}
      <Animated.View
        pointerEvents="none"
        accessibilityElementsHidden
        importantForAccessibility="no-hide-descendants"
        style={[
          styles.preview,
          { left: Math.min(Math.max(peek?.x ?? 0, PEEK / 2 + 4), WIDTH - PEEK / 2 - 4) - PEEK / 2 },
          previewStyle,
        ]}
      >
        {/* Every image waits here unseen, so the one that is held up is already loaded and does not fade in. */}
        {images.map((image, index) => (
          <View key={index} style={[styles.previewSlot, { opacity: peek && sameImage(image, peek.image) ? 1 : 0 }]}>
            <Photo image={image} style={styles.previewPhoto} />
          </View>
        ))}
      </Animated.View>
    </View>
  );
}

const styles = StyleSheet.create({
  box: {
    width: WIDTH,
    height: HEIGHT,
  },
  header: {
    position: 'absolute',
    left: 0,
    top: 0,
    width: WIDTH,
    height: HEADER_HEIGHT,
    paddingRight: 2,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
  },
  chip: {
    height: HEADER_HEIGHT,
    paddingHorizontal: 10,
    borderRadius: 10,
    backgroundColor: '#ffffff',
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
  },
  chipLabel: {
    color: INK,
    fontSize: 13,
    fontWeight: '500',
    includeFontPadding: false,
  },
  avatar: {
    width: 18,
    height: 18,
    borderRadius: 9,
    backgroundColor: '#ddd6fe',
    overflow: 'hidden',
  },
  avatarHead: {
    position: 'absolute',
    left: 5.8,
    top: 3.8,
    width: 6.4,
    height: 6.4,
    borderRadius: 3.2,
    backgroundColor: '#8b7cf6',
  },
  // A 12 by 8 ellipse: a circle pressed flat.
  avatarShoulders: {
    position: 'absolute',
    left: 3,
    top: 10,
    width: 12,
    height: 12,
    borderRadius: 6,
    backgroundColor: '#8b7cf6',
    transform: [{ scaleY: 8 / 12 }],
  },
  spacer: {
    flex: 1,
  },
  tool: {
    width: 34,
    height: 34,
    alignItems: 'center',
    justifyContent: 'center',
  },
  button: {
    width: 36,
    height: 36,
    alignItems: 'center',
    justifyContent: 'center',
  },
  // As tall as the card gets, so everything in it is laid out in the card's own coordinates.
  card: {
    position: 'absolute',
    left: 0,
    top: CARD_Y,
    width: WIDTH,
    height: REST_HEIGHT + ROW_HEIGHT,
  },
  sheet: {
    position: 'absolute',
    left: 0,
    top: 0,
    width: WIDTH,
    borderRadius: RADIUS,
    borderWidth: 1,
    borderColor: '#e4e2f0',
    backgroundColor: '#ffffff',
    boxShadow: '0 4px 14px rgba(0, 0, 0, 0.04)',
  },
  field: {
    position: 'absolute',
    left: TEXT_X,
    top: 0,
    height: LINE,
  },
  hintLine: {
    position: 'absolute',
    left: 0,
    top: 0,
    right: 0,
    height: LINE,
    justifyContent: 'center',
  },
  copyLine: {
    position: 'absolute',
    left: 0,
    top: 0,
    height: LINE,
    flexDirection: 'row',
    alignItems: 'center',
  },
  text: {
    color: INK,
    fontSize: FONT_SIZE,
    includeFontPadding: false,
  },
  hint: {
    color: '#a2a2ad',
  },
  copy: {
    opacity: 0,
  },
  caret: {
    width: 1.5,
    height: 20,
    borderRadius: 0.75,
    backgroundColor: INK,
  },
  inputSlot: {
    position: 'absolute',
    left: 0,
    top: 0,
    right: 0,
    height: LINE,
  },
  input: {
    height: LINE,
    padding: 0,
    color: INK,
    fontSize: FONT_SIZE,
    includeFontPadding: false,
    textAlignVertical: 'center',
  },
  toolbar: {
    position: 'absolute',
    left: 0,
    top: 0,
    width: WIDTH,
    height: TOOL_Y * 2,
  },
  divider: {
    position: 'absolute',
    left: 54 - 0.5,
    top: TOOL_Y - 10,
    width: 1,
    height: 20,
    backgroundColor: '#e6e6ec',
  },
  send: {
    width: SEND_SIZE,
    height: SEND_SIZE,
  },
  sendFill: {
    width: SEND_SIZE,
    height: SEND_SIZE,
    borderRadius: 12,
    backgroundColor: '#0f1115',
  },
  arrow: {
    position: 'absolute',
    left: (SEND_SIZE - 20) / 2,
    top: (SEND_SIZE - 20) / 2,
  },
  // The stroke lies half outside the ring's 15 points, so its drawing is a point larger all round.
  ring: {
    position: 'absolute',
    left: (SEND_SIZE - RING) / 2 - 1,
    top: (SEND_SIZE - RING) / 2 - 1,
  },
  thumbs: {
    position: 'absolute',
    left: 0,
    top: 0,
    right: 0,
    bottom: 0,
  },
  // Centred on the card's corner, and moved from there to its place in the row.
  thumb: {
    position: 'absolute',
    left: -THUMB_BOX / 2,
    top: -THUMB_BOX / 2,
    width: THUMB_BOX,
    height: THUMB_BOX,
  },
  frame: {
    position: 'absolute',
    left: BADGE_OUT,
    top: BADGE_OUT,
    width: THUMB,
    height: THUMB,
  },
  shade: {
    position: 'absolute',
    left: 0,
    top: 0,
    right: 0,
    bottom: 0,
    borderRadius: 8,
    backgroundColor: '#ffffff',
  },
  shadeRest: {
    boxShadow: '0 2px 8px rgba(0, 0, 0, 0.14)',
  },
  shadeHeld: {
    boxShadow: '0 6px 18px rgba(0, 0, 0, 0.24)',
  },
  frameFill: {
    flex: 1,
    padding: 2,
    borderRadius: 8,
    backgroundColor: '#ffffff',
  },
  thumbPhoto: {
    flex: 1,
    borderRadius: 6.5,
    overflow: 'hidden',
  },
  badge: {
    position: 'absolute',
    right: 0,
    top: 0,
  },
  badgeHit: {
    width: 28,
    height: 28,
    alignItems: 'center',
    justifyContent: 'center',
  },
  badgeDisc: {
    width: 18,
    height: 18,
    borderRadius: 9,
    backgroundColor: '#ffffff',
    boxShadow: '0 1px 4px rgba(0, 0, 0, 0.18)',
    alignItems: 'center',
    justifyContent: 'center',
  },
  preview: {
    position: 'absolute',
    top: CARD_Y - PEEK_GAP - PEEK,
    width: PEEK,
    height: PEEK,
    borderRadius: 22,
    backgroundColor: '#ffffff',
    boxShadow: '0 12px 28px rgba(0, 0, 0, 0.22)',
    // It grows out of its bottom edge, which stands on the card.
    transformOrigin: 'bottom',
  },
  previewSlot: {
    position: 'absolute',
    left: 3,
    top: 3,
  },
  previewPhoto: {
    width: PEEK - 6,
    height: PEEK - 6,
    borderRadius: 19,
    overflow: 'hidden',
  },
});
