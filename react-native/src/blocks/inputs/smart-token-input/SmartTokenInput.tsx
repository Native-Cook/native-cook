import { BlurMask, Canvas, Group, Path, Skia } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { type ReactNode, useEffect, useEffectEvent, useRef, useState } from 'react';
import { type LayoutChangeEvent, Pressable, type StyleProp, StyleSheet, Text, TextInput, View, type ViewStyle } from 'react-native';
import Animated, {
  Easing,
  ReduceMotion,
  type SharedValue,
  cancelAnimation,
  cubicBezier,
  useAnimatedProps,
  useAnimatedStyle,
  useDerivedValue,
  useReducedMotion,
  useSharedValue,
  withDelay,
  withRepeat,
  withSequence,
  withSpring,
  withTiming,
} from 'react-native-reanimated';
import Svg, { Circle, Path as Outline } from 'react-native-svg';

type Props = {
  /** Controlled text. Leave it out and the component keeps its own. */
  value?: string;
  /** Types the script, picks a date and a file, clears the field and starts over. */
  autoPlay?: boolean;
  placeholder?: string;
  /** What autoPlay types up to the date word. */
  script?: string;
  /** What autoPlay types after it picked the date, ending in the slash command. */
  scriptMore?: string;
  listLabel?: string;
  scheduleTitle?: string;
  attachTitle?: string;
  attachLabel?: string;
  /** Name of the file the upload row attaches. */
  fileName?: string;
  /** The day date words are counted from; the current day when left out. */
  today?: Date;
  accent?: string;
  onChangeText?: (text: string) => void;
  /** Called with the chosen day when a row of the schedule menu is tapped. */
  onSchedule?: (day: Date) => void;
  /** Called with the file's name when the upload row is tapped. */
  onAttach?: (name: string) => void;
  /** Called with the text when the return key is pressed; the field clears itself afterwards. */
  onSend?: (text: string) => void;
  onCalendar?: () => void;
  onList?: () => void;
};

const WIDTH = 350;
// The box the component is laid out in: the card at its top, the menus under it.
const HEIGHT = 200;
// Height of the card with one line of text.
const REST = 44;
const RADIUS = 14;
const LINE = 20;
const TEXT_TOP = 12;
// Room between two words, and between the last one and the caret after a space.
const GAP = 3.6;
const FONT_SIZE = 13.5;
// Left edge of the sentence inside the card: the card's padding, the checkbox and the gap after it.
const FIELD_X = 12 + 18 + 10;

// The canvas overflows the card by this much on every side to fit its shadow and the bowing edge.
const BLEED = 40;
const SHADOW_BLUR = 10;
const SHADOW_Y = 8;

const MENU_WIDTH = 172;
const MENU_GAP = 8;
// The menu is centred this far into the word it hangs under.
const MENU_LEAD = 12;
const MENU_HEADER = 28;
const MENU_ROW = 34;

const INK = '#1d1d1f';
const ICON = '#3a3f47';
const CHIP = '#f1f3f5';
const COMMAND = '/file';

// autoPlay (seconds). Keys land `KEY` apart on average, stretched by `RHYTHM` in turn.
const TYPE_AT = 0.8;
const KEY = 0.058;
const RHYTHM = [1.0, 0.72, 1.3, 0.85, 1.12, 0.64, 1.42, 0.95];
// From the last key of the date word to the highlight moving down, then to the tap on that row.
const ROW_AFTER = 0.75;
const PICK_AFTER = 0.65;
// From the tap to the next key.
const MORE_AFTER = 0.55;
// From the last key of the slash command to the tap on the upload row.
const FILE_AFTER = 0.8;
// The chip loads, closes its ring and turns into a paperclip before the field clears.
const CLEAR_AFTER = 2.2;
const LOOP = 9.6;
// After the field is left empty, the script waits this long before it starts again.
const RESUME_AFTER = 3.0;

const BLINK = 0.53;
const WIPE = 0.18;
const CHIP_LOAD = 0.8;
const CHIP_CLOSE = 0.3;
const CHIP_HOLD = 0.5;
const SPIN = 0.8;
const DOT_DELAY = 0.12;

// The chip's ring: its radius, the length of its outline and the share of it drawn while it loads.
const RING_RADIUS = 5.25;
const RING_LENGTH = 2 * Math.PI * RING_RADIUS;
const RING_OPEN = 0.72;

// Reduced motion is handled here (springs become short fades), so no animation leaves it to the system switch.
const KEEP = ReduceMotion.Never;
const EASE_OUT = Easing.bezier(0, 0, 0.58, 1);
const CSS_EASE_OUT = cubicBezier(0, 0, 0.58, 1);
const PRESS_EASE = cubicBezier(0.23, 1, 0.32, 1);
const FADE = { duration: 150, easing: EASE_OUT, reduceMotion: KEEP };
const CARET_FADE = { duration: 80, easing: EASE_OUT, reduceMotion: KEEP };

// Depth of the bow in the card's bottom edge, how long it takes to form, and the spring that lets it go.
const BOW = 5;
const BOW_IN = 0.12;
const BOW_OUT = { mass: 1, stiffness: 300, damping: 9, reduceMotion: KEEP };
const GROW = { mass: 1, stiffness: 320, damping: 22, reduceMotion: KEEP };
const MENU_IN = { mass: 1, stiffness: 420, damping: 26, reduceMotion: KEEP };
const MENU_OUT = 0.14;
const ROW_MOVE = { mass: 1, stiffness: 480, damping: 34, reduceMotion: KEEP };
const DOT = { mass: 1, stiffness: 520, damping: 15, reduceMotion: KEEP };
const CHIP_IN = { mass: 1, stiffness: 420, damping: 20, reduceMotion: KEEP };
const CLIP_IN = { mass: 1, stiffness: 480, damping: 18, reduceMotion: KEEP };

const DAY_NAMES = ['Sunday', 'Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday'];
const MONTH_NAMES = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];

const SPACE = /\s/;
const EDGE_PUNCTUATION = /^\p{P}+|\p{P}+$/gu;

// Icons, as outlines in a 16 pt box (the chevron in 10 pt, the paperclip in 14 pt).
const CALENDAR_ICON =
  'M5.25 2.75 L10.75 2.75 C12.13 2.75 13.25 3.87 13.25 5.25 L13.25 10.75 C13.25 12.13 12.13 13.25 10.75 13.25 ' +
  'L5.25 13.25 C3.87 13.25 2.75 12.13 2.75 10.75 L2.75 5.25 C2.75 3.87 3.87 2.75 5.25 2.75 Z M2.75 6.5 L13.25 6.5';
const CHEVRON_ICON = 'M2.5 3.75 L5 6.25 L7.5 3.75';
const UPLOAD_ICON = 'M8 10.5 L8 3 M4.75 6.25 L8 3 L11.25 6.25 M3.25 13 L12.75 13';
const CLIP_ICON =
  'M11.03 7.49 L7.71 10.82 C6.46 12.07 4.43 12.07 3.18 10.82 C1.93 9.57 1.93 7.54 3.18 6.29 L6.93 2.55 ' +
  'C7.75 1.73 9.08 1.73 9.9 2.55 C10.72 3.37 10.72 4.69 9.9 5.52 L6.29 9.12 C5.88 9.53 5.22 9.53 4.81 9.12 ' +
  'C4.4 8.71 4.4 8.05 4.81 7.64 L7.78 4.67';

type MenuKind = 'none' | 'schedule' | 'attach';

type Point = { x: number; y: number };

type FileChip = {
  id: number;
  /** Character offset of the text the chip sits at. */
  at: number;
  name: string;
};

/** Which word of the text was confirmed, and what it said then. */
type Picked = { index: number; word: string };

/** A word of the sentence, or a file chip between two of them. */
type Piece = {
  id: string;
  text: string;
  /** Position among the words; -1 for a chip. */
  index: number;
  /** Character offset of the word's first letter. */
  start: number;
  file?: FileChip;
};

type Act = { kind: 'type'; text: string } | { kind: 'row'; row: number } | { kind: 'date' } | { kind: 'file' } | { kind: 'clear' };

type Step = { at: number; act: Act };

function clamp01(t: number) {
  'worklet';
  return Math.min(1, Math.max(0, t));
}

function piecesOf(text: string, files: FileChip[]): Piece[] {
  const pieces: Piece[] = [];
  let word = '';
  let start = 0;
  let words = 0;
  const close = () => {
    if (word === '') return;
    pieces.push({ id: `w${words}`, text: word, index: words, start });
    words += 1;
    word = '';
  };
  const chips = (offset: number) => {
    for (const file of files) {
      if (file.at !== offset) continue;
      close();
      pieces.push({ id: `f${file.id}`, text: file.name, index: -1, start: offset, file });
    }
  };
  for (let offset = 0; offset < text.length; offset++) {
    chips(offset);
    const character = text[offset];
    if (SPACE.test(character)) {
      close();
    } else {
      if (word === '') start = offset;
      word += character;
    }
  }
  chips(text.length);
  close();
  return pieces;
}

/** The autoPlay timeline: every key with the text it leaves behind, then the taps. */
function scriptSteps(script: string, more: string): Step[] {
  const steps: Step[] = [];
  let at = TYPE_AT;
  let typed = '';
  let keys = 0;
  const type = (text: string) => {
    for (const character of text) {
      typed += character;
      steps.push({ at, act: { kind: 'type', text: typed } });
      at += KEY * RHYTHM[keys % RHYTHM.length];
      keys += 1;
    }
  };
  type(script);
  at += ROW_AFTER;
  steps.push({ at, act: { kind: 'row', row: 1 } });
  at += PICK_AFTER;
  steps.push({ at, act: { kind: 'date' } });
  at += MORE_AFTER;
  type(more);
  at += FILE_AFTER;
  steps.push({ at, act: { kind: 'file' } });
  at += CLEAR_AFTER;
  steps.push({ at, act: { kind: 'clear' } });
  return steps;
}

/** Days from `today` to the day a word names, or null when it is not a date word. */
function dayOffset(word: string, today: Date) {
  const key = word.toLowerCase().replace(EDGE_PUNCTUATION, '');
  if (key === 'today') return 0;
  if (key === 'tomorrow') return 1;
  const target = DAY_NAMES.findIndex((name) => key === name.toLowerCase() || key === name.slice(0, 3).toLowerCase());
  if (target < 0) return null;
  const ahead = (target - today.getDay() + 7) % 7;
  return ahead === 0 ? 7 : ahead;
}

function addDays(day: Date, count: number) {
  const next = new Date(day);
  next.setDate(next.getDate() + count);
  return next;
}

function dayLabel(day: Date) {
  return `${DAY_NAMES[day.getDay()].slice(0, 3)}, ${MONTH_NAMES[day.getMonth()]} ${day.getDate()}`;
}

/** The card's outline inside its canvas: a rounded rectangle whose bottom edge can bow. */
function outline(height: number, bow: number) {
  'worklet';
  const left = BLEED;
  const top = BLEED;
  const right = BLEED + WIDTH;
  const bottom = BLEED + height;
  const path = Skia.Path.Make();
  path.moveTo(left + RADIUS, top);
  path.arcToTangent(right, top, right, bottom, RADIUS);
  path.arcToTangent(right, bottom, left, bottom, RADIUS);
  // A curve through the bowed middle instead of a straight bottom edge.
  path.quadTo(left + WIDTH / 2, bottom + bow * 2, left + RADIUS, bottom);
  path.arcToTangent(left, bottom, left, top, RADIUS);
  path.arcToTangent(left, top, right, top, RADIUS);
  path.close();
  return path;
}

/** The white card and its shadow. `reach` is the tallest the card has been, so the canvas always holds it. */
function Surface({ height, bow, reach }: { height: SharedValue<number>; bow: SharedValue<number>; reach: number }) {
  const shape = useDerivedValue(() => outline(height.get(), bow.get()));

  return (
    <Canvas style={{ width: WIDTH + BLEED * 2, height: reach + BLEED * 2 }}>
      <Group transform={[{ translateY: SHADOW_Y }]}>
        <Path path={shape} color="rgba(0,0,0,0.08)">
          <BlurMask blur={SHADOW_BLUR} style="normal" />
        </Path>
      </Group>
      <Path path={shape} color="#ffffff" />
    </Canvas>
  );
}

function Glyph({ d, size, color, width }: { d: string; size: number; color: string; width: number }) {
  return (
    <Svg width={size} height={size} viewBox={`0 0 ${size} ${size}`}>
      <Outline d={d} stroke={color} strokeWidth={width} strokeLinecap="round" strokeLinejoin="round" fill="none" />
    </Svg>
  );
}

/** A button whose content sinks a little while it is held. */
function Press({
  style,
  label,
  onPress,
  children,
}: {
  style?: StyleProp<ViewStyle>;
  label?: string;
  onPress: () => void;
  children: ReactNode;
}) {
  return (
    <Pressable style={style} accessibilityRole="button" accessibilityLabel={label} onPress={onPress}>
      {({ pressed }) => (
        <Animated.View
          style={{
            transform: [{ scale: pressed ? 0.96 : 1 }],
            transitionProperty: 'transform',
            transitionDuration: 130,
            transitionTimingFunction: PRESS_EASE,
          }}
        >
          {children}
        </Animated.View>
      )}
    </Pressable>
  );
}

function Caret({ visible, on }: { visible: boolean; on: SharedValue<number> }) {
  const style = useAnimatedStyle(() => ({ opacity: visible ? on.get() : 0 }));
  return <Animated.View style={[styles.caret, style]} />;
}

const AnimatedCircle = Animated.createAnimatedComponent(Circle);

/** A file in the sentence: a ring that spins while it loads, closes, and turns into a paperclip. */
function Chip({ name, still }: { name: string; still: boolean }) {
  const shown = useSharedValue(0);
  const spin = useSharedValue(0);
  // Share of the ring that is drawn.
  const trim = useSharedValue(RING_OPEN);
  // 0 while the ring shows, 1 once the paperclip has taken its place.
  const clip = useSharedValue(0);

  useEffect(() => {
    shown.set(still ? withTiming(1, FADE) : withSpring(1, CHIP_IN));
    if (!still) {
      spin.set(withRepeat(withTiming(360, { duration: SPIN * 1000, easing: Easing.linear, reduceMotion: KEEP }), -1, false, undefined, KEEP));
    }
    trim.set(withDelay(CHIP_LOAD * 1000, withTiming(1, { duration: CHIP_CLOSE * 1000, easing: EASE_OUT, reduceMotion: KEEP }), KEEP));
    clip.set(
      withDelay(
        (CHIP_LOAD + CHIP_HOLD) * 1000,
        still
          ? withTiming(1, FADE)
          : withSpring(1, CLIP_IN, (finished) => {
              'worklet';
              // The ring is gone by now, so it has no reason to keep turning.
              if (finished) cancelAnimation(spin);
            }),
        KEEP,
      ),
    );
  }, [still, shown, spin, trim, clip]);

  const chipStyle = useAnimatedStyle(() => ({
    opacity: clamp01(shown.get()),
    transform: [{ scale: still ? 1 : 0.8 + 0.2 * shown.get() }],
  }));
  const ringStyle = useAnimatedStyle(() => ({
    opacity: 1 - clamp01(clip.get()),
    transform: [{ rotate: `${spin.get()}deg` }, { scale: still ? 1 : 1 - 0.6 * clip.get() }],
  }));
  const ringProps = useAnimatedProps(() => ({ strokeDashoffset: RING_LENGTH * (1 - trim.get()) }));
  const clipStyle = useAnimatedStyle(() => ({
    opacity: clamp01(clip.get()),
    transform: [{ scale: still ? 1 : 0.4 + 0.6 * clip.get() }],
  }));

  return (
    <Animated.View style={[styles.chip, chipStyle]}>
      <View style={styles.chipIcon}>
        <Animated.View style={[styles.chipLayer, ringStyle]}>
          <Svg width={14} height={14} viewBox="0 0 14 14">
            <AnimatedCircle
              cx={7}
              cy={7}
              r={RING_RADIUS}
              stroke="#8a9099"
              strokeWidth={1.5}
              strokeLinecap="round"
              strokeDasharray={[RING_LENGTH, RING_LENGTH]}
              fill="none"
              animatedProps={ringProps}
            />
          </Svg>
        </Animated.View>
        <Animated.View style={[styles.chipLayer, clipStyle]}>
          <Glyph d={CLIP_ICON} size={14} color="#6b717a" width={1.3} />
        </Animated.View>
      </View>
      <Text style={styles.chipName} numberOfLines={1}>
        {name}
      </Text>
    </Animated.View>
  );
}

type MenuProps = {
  title: string;
  rows: string[];
  icon: string;
  open: boolean;
  highlighted: number;
  still: boolean;
  /** Its top left corner inside the component. */
  at: Point;
  onPick: (index: number) => void;
};

/** A small menu hanging under the card: a grey heading and rows, one of them highlighted. */
function Menu({ title, rows, icon, open, highlighted, still, at, onPick }: MenuProps) {
  const shown = useSharedValue(0);
  const slide = useSharedValue(highlighted * MENU_ROW);

  useEffect(() => {
    shown.set(
      open && !still
        ? withSpring(1, MENU_IN)
        : withTiming(open ? 1 : 0, { duration: MENU_OUT * 1000, easing: EASE_OUT, reduceMotion: KEEP }),
    );
  }, [open, still, shown]);

  useEffect(() => {
    const y = highlighted * MENU_ROW;
    slide.set(still ? y : withSpring(y, ROW_MOVE));
  }, [highlighted, still, slide]);

  const menuStyle = useAnimatedStyle(() => ({
    opacity: clamp01(shown.get()),
    transform: [{ scale: still ? 1 : 0.95 + 0.05 * shown.get() }],
  }));
  const highlightStyle = useAnimatedStyle(() => ({ transform: [{ translateY: slide.get() }] }));

  return (
    <Animated.View
      // A closed menu is still mounted (it fades), so it must let touches and screen readers through.
      pointerEvents={open ? 'auto' : 'none'}
      accessibilityElementsHidden={!open}
      importantForAccessibility={open ? 'auto' : 'no-hide-descendants'}
      style={[styles.menu, { left: at.x, top: at.y }, menuStyle]}
    >
      <View style={styles.menuHeader}>
        <Text style={styles.menuTitle} numberOfLines={1}>
          {title}
        </Text>
      </View>
      <View style={styles.menuRows}>
        <Animated.View style={[styles.menuHighlight, highlightStyle]} />
        {rows.map((label, index) => (
          <Press key={index} onPress={() => onPick(index)}>
            <View style={styles.menuRow}>
              <Glyph d={icon} size={16} color={ICON} width={1.5} />
              <Text style={styles.menuLabel} numberOfLines={1}>
                {label}
              </Text>
            </View>
          </Press>
        ))}
      </View>
    </Animated.View>
  );
}

/**
 * A task composer that reads what is typed. A date word ("Wed", "tomorrow") turns blue and opens a
 * schedule menu under it; "/" opens a file menu whose row drops a file chip into the sentence. The
 * card grows a line at a time with a wobble of its bottom edge.
 */
export function SmartTokenInput({
  value,
  autoPlay = false,
  placeholder = 'Add a task',
  script = 'Send email to John on Wed',
  scriptMore = ' with a redesign proposal /f',
  listLabel = 'No list',
  scheduleTitle = 'Schedule',
  attachTitle = 'Attachment',
  attachLabel = 'File upload',
  fileName = 'Redesign.doc',
  today,
  accent = '#2f9be0',
  onChangeText,
  onSchedule,
  onAttach,
  onSend,
  onCalendar,
  onList,
}: Props) {
  const reducedMotion = useReducedMotion();
  const controlled = value != null;
  const [opened] = useState(() => new Date());
  const from = today ?? opened;

  const [inner, setInner] = useState('');
  // File chips, each sitting at a character offset of the text.
  const [files, setFiles] = useState<FileChip[]>([]);
  const nextFile = useRef(0);
  // The date word that was confirmed.
  const [pick, setPick] = useState<Picked | null>(null);
  // Highlighted row of the schedule menu.
  const [row, setRow] = useState(0);
  // The two days the schedule menu offers; kept while it fades out.
  const [days, setDays] = useState<Date[]>([]);
  const [playing, setPlaying] = useState(autoPlay && !controlled);
  const [focused, setFocused] = useState(false);
  // The sentence is faded out, just before the field empties.
  const [wiped, setWiped] = useState(false);
  const [textHeight, setTextHeight] = useState(LINE);
  // The tallest the card has been.
  const [reach, setReach] = useState(HEIGHT);
  // Where each word of the sentence starts inside the component.
  const [origins, setOrigins] = useState<Record<string, number>>({});
  const [seenMenu, setSeenMenu] = useState<MenuKind>('none');
  // Each menu keeps its place while it fades out.
  const [menuAt, setMenuAt] = useState<{ schedule: Point; attach: Point }>({
    schedule: { x: 0, y: REST + MENU_GAP },
    attach: { x: 0, y: REST + MENU_GAP },
  });

  const height = useSharedValue(REST);
  // How far the middle of the card's bottom edge hangs below its corners.
  const bow = useSharedValue(0);
  const caretOn = useSharedValue(1);
  const dot = useSharedValue(0);

  const text = value ?? inner;
  const pieces = piecesOf(text, files);
  const empty = pieces.length === 0;
  const last = pieces[pieces.length - 1];
  const endsInSpace = SPACE.test(text.slice(-1));
  const cardHeight = textHeight + REST - LINE;

  // The word the caret is in.
  const open = last && !last.file && !endsInSpace ? last : null;
  const menu: MenuKind = !open
    ? 'none'
    : open.text.startsWith('/')
      ? 'attach'
      : pick?.index !== open.index && dayOffset(open.text, from) != null
        ? 'schedule'
        : 'none';

  // A schedule menu that opens gets its two days and starts on its first row.
  if (menu !== seenMenu) {
    setSeenMenu(menu);
    const offset = menu === 'schedule' && open ? dayOffset(open.text, from) : null;
    if (offset != null) {
      setRow(0);
      setDays([0, 7].map((more) => addDays(from, offset + more)));
    }
  }

  // The open menu hangs under the word the caret is in, kept inside the card's width.
  const origin = open ? origins[open.id] : undefined;
  // A menu waits until its word has been measured, so it never shows up in the wrong place first.
  const placed = origin != null;
  if (menu !== 'none' && origin != null) {
    const x = Math.min(Math.max(origin + MENU_LEAD - MENU_WIDTH / 2, 4), WIDTH - MENU_WIDTH - 4);
    const y = cardHeight + MENU_GAP;
    if (menuAt[menu].x !== x || menuAt[menu].y !== y) setMenuAt({ ...menuAt, [menu]: { x, y } });
  }

  const edit = (next: string, chips = files) => {
    if (next === text) return;
    // A chip goes with the text in front of it.
    const kept = chips.filter((file) => file.at <= next.length);
    setFiles(kept);
    if (pick && piecesOf(next, kept).find((piece) => piece.index === pick.index)?.text !== pick.word) setPick(null);
    if (!controlled) setInner(next);
    onChangeText?.(next);
  };

  const reset = () => {
    setFiles([]);
    setPick(null);
    setWiped(false);
    edit('', []);
  };

  const tick = () => {
    if (process.env.EXPO_OS !== 'web') Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
  };

  const choose = (chosen: number, byHand: boolean) => {
    if (menu !== 'schedule' || !open || chosen < 0 || chosen >= days.length) return;
    setRow(chosen);
    setPick({ index: open.index, word: open.text });
    if (byHand) tick();
    onSchedule?.(days[chosen]);
  };

  /** Swaps the slash command for a file chip. */
  const attach = (byHand: boolean) => {
    if (menu !== 'attach' || !open) return;
    const kept = text.slice(0, open.start);
    const file = { id: nextFile.current, at: kept.length, name: fileName };
    nextFile.current += 1;
    edit(kept, [...files, file]);
    if (byHand) tick();
    onAttach?.(fileName);
  };

  const send = () => {
    if (empty) return;
    onSend?.(text);
    setWiped(true);
  };

  // Once the sentence has faded out, the field empties.
  const finishWipe = useEffectEvent(reset);
  useEffect(() => {
    if (!wiped) return;
    const timer = setTimeout(() => finishWipe(), WIPE * 1000);
    return () => clearTimeout(timer);
  }, [wiped]);

  const perform = useEffectEvent((act: Act) => {
    if (act.kind === 'type') edit(act.text);
    else if (act.kind === 'row') setRow(act.row);
    else if (act.kind === 'date') choose(row, false);
    else if (act.kind === 'file') attach(false);
    else setWiped(true);
  });

  useEffect(() => {
    if (!playing) return;
    const steps = scriptSteps(script, scriptMore);
    const period = Math.max(LOOP, steps[steps.length - 1].at + TYPE_AT);
    // Every step is timed from the start of its loop, so the period does not drift.
    let started = Date.now();
    let index = 0;
    let timer: ReturnType<typeof setTimeout>;
    const wait = () => {
      const at = index < steps.length ? steps[index].at : period;
      timer = setTimeout(
        () => {
          if (index < steps.length) {
            perform(steps[index].act);
            index += 1;
          } else {
            started += period * 1000;
            index = 0;
          }
          wait();
        },
        Math.max(0, started + at * 1000 - Date.now()),
      );
    };
    wait();
    return () => clearTimeout(timer);
  }, [playing, script, scriptMore]);

  // The script resumes once the field has been left empty for a while.
  const resume = useEffectEvent(() => {
    if (text === '' && files.length === 0) setPlaying(true);
  });
  useEffect(() => {
    if (!autoPlay || controlled || focused || playing) return;
    const timer = setTimeout(() => resume(), RESUME_AFTER * 1000);
    return () => clearTimeout(timer);
  }, [autoPlay, controlled, focused, playing]);

  // The caret stays solid while keys arrive and blinks once they stop: every change of the text starts this over.
  useEffect(() => {
    caretOn.set(withTiming(1, CARET_FADE));
    if (!focused && !playing) return;
    let on = true;
    const timer = setInterval(() => {
      on = !on;
      caretOn.set(withTiming(on ? 1 : 0, CARET_FADE));
    }, BLINK * 1000);
    return () => clearInterval(timer);
  }, [text, files.length, focused, playing, caretOn]);

  // The dot that says a day is set pops in a moment after the tap.
  const picked = pick != null;
  useEffect(() => {
    dot.set(picked && !reducedMotion ? withDelay(DOT_DELAY * 1000, withSpring(1, DOT), KEEP) : withTiming(picked ? 1 : 0, FADE));
  }, [picked, reducedMotion, dot]);

  const onFocus = () => {
    setFocused(true);
    // A touch takes the field over from the script.
    if (playing) {
      setPlaying(false);
      reset();
    }
  };

  const onFlowLayout = (event: LayoutChangeEvent) => {
    const next = Math.max(LINE, event.nativeEvent.layout.height);
    if (next === textHeight) return;
    const target = next + REST - LINE;
    setTextHeight(next);
    setReach((tallest) => Math.max(tallest, target));
    if (reducedMotion) {
      height.set(target);
      return;
    }
    height.set(withSpring(target, GROW));
    // The bottom edge bows out as the card gains a line, then rings back to straight.
    bow.set(
      withSequence(
        KEEP,
        withTiming(next > textHeight ? BOW : -BOW / 2, { duration: BOW_IN * 1000, easing: EASE_OUT, reduceMotion: KEEP }),
        withSpring(0, BOW_OUT),
      ),
    );
  };

  const place = (id: string, x: number) => {
    setOrigins((known) => (known[id] === FIELD_X + x ? known : { ...known, [id]: FIELD_X + x }));
  };

  const cardStyle = useAnimatedStyle(() => ({ height: height.get() }));
  const dotStyle = useAnimatedStyle(() => ({
    opacity: clamp01(dot.get()),
    transform: [{ scale: reducedMotion ? 1 : Math.max(0, dot.get()) }],
  }));

  const caret = focused || (playing && !empty);
  // The rest of the slash command, shown in grey after the caret.
  const word = open?.text.toLowerCase() ?? '';
  const ghost = word.startsWith('/') && COMMAND.startsWith(word) ? COMMAND.slice(word.length) : '';
  // Caret and ghost completion hang off the last piece without taking room in the line.
  const tail = (
    <View style={[styles.tail, { marginLeft: endsInSpace || last?.file ? GAP : 0.5 }]}>
      <Caret visible={caret} on={caretOn} />
      <Text style={styles.ghost} numberOfLines={1}>
        {ghost}
      </Text>
    </View>
  );

  return (
    <View style={styles.root}>
      <View pointerEvents="none" style={styles.surface}>
        <Surface height={height} bow={bow} reach={reach} />
      </View>

      <Animated.View style={[styles.card, cardStyle]}>
        <View style={styles.checkbox} />

        <View style={styles.field}>
          <Animated.View
            pointerEvents="none"
            style={[
              styles.placeholder,
              {
                opacity: empty ? 1 : 0,
                transitionProperty: 'opacity',
                // It fades back in, but makes way for the first letter at once.
                transitionDuration: empty ? 200 : 0,
                transitionTimingFunction: CSS_EASE_OUT,
              },
            ]}
          >
            <Text style={styles.placeholderText} numberOfLines={1}>
              {placeholder}
            </Text>
            <View style={styles.lead}>
              <Caret visible={caret && empty} on={caretOn} />
            </View>
          </Animated.View>

          <Animated.View
            pointerEvents="none"
            onLayout={onFlowLayout}
            style={[
              styles.flow,
              {
                opacity: wiped ? 0 : 1,
                transitionProperty: 'opacity',
                transitionDuration: wiped ? WIPE * 1000 : 0,
                transitionTimingFunction: CSS_EASE_OUT,
              },
            ]}
          >
            {pieces.map((piece) =>
              piece.file ? (
                <View key={piece.id} style={styles.piece}>
                  <Chip name={piece.file.name} still={reducedMotion} />
                  {piece === last && tail}
                </View>
              ) : (
                <View key={piece.id} style={styles.piece} onLayout={(event) => place(piece.id, event.nativeEvent.layout.x)}>
                  <Text
                    style={[styles.word, { color: (piece === open && menu !== 'none') || pick?.index === piece.index ? accent : INK }]}
                    numberOfLines={1}
                  >
                    {piece.text}
                  </Text>
                  {piece === last && tail}
                </View>
              ),
            )}
          </Animated.View>

          {/* The real field: it takes the keyboard, the sentence above is drawn from its text. */}
          <TextInput
            style={styles.input}
            value={text}
            onChangeText={(next) => edit(next)}
            // The drawn caret is always after the last piece, so the real one stays there too.
            selection={focused ? { start: text.length, end: text.length } : undefined}
            caretHidden
            selectionColor="transparent"
            cursorColor="transparent"
            selectionHandleColor="transparent"
            autoCorrect={false}
            returnKeyType="done"
            submitBehavior="blurAndSubmit"
            onSubmitEditing={send}
            onFocus={onFocus}
            onBlur={() => setFocused(false)}
            accessibilityLabel={placeholder}
          />
        </View>

        <Press style={styles.calendarSlot} label="Schedule" onPress={() => onCalendar?.()}>
          <View style={styles.calendar}>
            <Glyph d={CALENDAR_ICON} size={16} color={ICON} width={1.5} />
          </View>
          <Animated.View style={[styles.dot, dotStyle]}>
            <View style={[styles.dotFill, { backgroundColor: accent }]} />
          </Animated.View>
        </Press>

        <Press style={styles.listSlot} onPress={() => onList?.()}>
          <View style={styles.list}>
            <View style={styles.listRing}>
              <View style={styles.listRingStroke} />
            </View>
            <Text style={styles.listLabel} numberOfLines={1}>
              {listLabel}
            </Text>
            <Glyph d={CHEVRON_ICON} size={10} color={ICON} width={1.6} />
          </View>
        </Press>
      </Animated.View>

      <Menu
        title={scheduleTitle}
        rows={days.map(dayLabel)}
        icon={CALENDAR_ICON}
        open={menu === 'schedule' && placed}
        highlighted={row}
        still={reducedMotion}
        at={menuAt.schedule}
        onPick={(index) => choose(index, true)}
      />
      <Menu
        title={attachTitle}
        rows={[attachLabel]}
        icon={UPLOAD_ICON}
        open={menu === 'attach' && placed}
        highlighted={0}
        still={reducedMotion}
        at={menuAt.attach}
        onPick={() => attach(true)}
      />
    </View>
  );
}

const styles = StyleSheet.create({
  root: {
    width: WIDTH,
    height: HEIGHT,
  },
  surface: {
    position: 'absolute',
    left: -BLEED,
    top: -BLEED,
  },
  // Everything on the card, clipped to its growing height.
  card: {
    width: WIDTH,
    flexDirection: 'row',
    alignItems: 'flex-start',
    paddingLeft: 12,
    paddingRight: 6,
    borderRadius: RADIUS,
    overflow: 'hidden',
  },
  checkbox: {
    width: 18,
    height: 18,
    marginTop: 13,
    borderRadius: 5,
    backgroundColor: '#edeff1',
  },
  field: {
    flex: 1,
    minHeight: LINE,
    marginLeft: 10,
    marginRight: 6,
    marginTop: TEXT_TOP,
  },
  placeholder: {
    position: 'absolute',
    left: 0,
    right: 0,
    top: 0,
    height: LINE,
  },
  placeholderText: {
    color: '#a3a8b0',
    fontSize: FONT_SIZE,
    lineHeight: LINE,
    includeFontPadding: false,
  },
  lead: {
    position: 'absolute',
    left: 0,
    top: 0,
    height: LINE,
    justifyContent: 'center',
  },
  // Words laid out like text: left to right, wrapping whole pieces, every line `LINE` tall.
  flow: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    columnGap: GAP,
  },
  piece: {
    height: LINE,
    flexDirection: 'row',
    alignItems: 'center',
  },
  word: {
    fontSize: FONT_SIZE,
    lineHeight: LINE,
    includeFontPadding: false,
  },
  tail: {
    position: 'absolute',
    left: '100%',
    top: 0,
    // Given a width of its own: an absolute box is otherwise measured against the word it hangs off.
    width: WIDTH,
    height: LINE,
    flexDirection: 'row',
    alignItems: 'center',
  },
  ghost: {
    color: '#b4b9c0',
    fontSize: FONT_SIZE,
    lineHeight: LINE,
    includeFontPadding: false,
  },
  caret: {
    width: 1.5,
    height: 17,
    borderRadius: 0.75,
    backgroundColor: INK,
  },
  // Covers the whole text area so a tap anywhere on it focuses the field; its own text and caret are never seen.
  input: {
    position: 'absolute',
    left: 0,
    right: 0,
    top: 0,
    bottom: 0,
    padding: 0,
    color: 'transparent',
    fontSize: FONT_SIZE,
  },
  calendarSlot: {
    marginTop: 8,
  },
  calendar: {
    width: 28,
    height: 28,
    borderRadius: 7,
    backgroundColor: CHIP,
    alignItems: 'center',
    justifyContent: 'center',
  },
  // A 7 pt dot under a 1.5 pt white outline, sitting on the button's top right corner.
  dot: {
    position: 'absolute',
    left: 21.25,
    top: -1.75,
    width: 8.5,
    height: 8.5,
    borderRadius: 4.25,
    backgroundColor: '#ffffff',
    alignItems: 'center',
    justifyContent: 'center',
  },
  dotFill: {
    width: 5.5,
    height: 5.5,
    borderRadius: 2.75,
  },
  listSlot: {
    marginTop: 8,
    marginLeft: 5,
  },
  list: {
    height: 28,
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 7,
    borderRadius: 8,
    backgroundColor: CHIP,
  },
  listRing: {
    width: 8.5,
    height: 8.5,
  },
  // The outline is centred on the edge of the 8.5 pt ring, so it reaches 0.75 pt past it.
  listRingStroke: {
    position: 'absolute',
    left: -0.75,
    top: -0.75,
    width: 10,
    height: 10,
    borderRadius: 5,
    borderWidth: 1.5,
    borderColor: '#b4b9c0',
  },
  listLabel: {
    marginLeft: 6,
    marginRight: 5,
    color: INK,
    fontSize: 12.5,
    fontWeight: '500',
    includeFontPadding: false,
  },
  chip: {
    height: LINE,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 4,
    paddingLeft: 5,
    paddingRight: 7,
    borderRadius: 6,
    backgroundColor: CHIP,
  },
  chipIcon: {
    width: 14,
    height: 14,
  },
  chipLayer: {
    position: 'absolute',
    left: 0,
    top: 0,
    width: 14,
    height: 14,
  },
  chipName: {
    color: '#555a62',
    fontSize: 12,
    fontWeight: '500',
    includeFontPadding: false,
  },
  menu: {
    position: 'absolute',
    width: MENU_WIDTH,
    paddingBottom: 4,
    borderRadius: 12,
    backgroundColor: '#ffffff',
    boxShadow: '0 8px 24px rgba(0, 0, 0, 0.12)',
    transformOrigin: 'top',
  },
  menuHeader: {
    height: MENU_HEADER,
    paddingLeft: 12,
    paddingTop: 2,
    justifyContent: 'center',
  },
  menuTitle: {
    color: '#9aa0a8',
    fontSize: 11,
    fontWeight: '500',
    includeFontPadding: false,
  },
  menuRows: {
    minHeight: MENU_ROW,
    marginHorizontal: 4,
  },
  menuHighlight: {
    position: 'absolute',
    left: 0,
    right: 0,
    top: 0,
    height: MENU_ROW,
    borderRadius: 8,
    backgroundColor: CHIP,
  },
  menuRow: {
    height: MENU_ROW,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 9,
    paddingLeft: 9,
  },
  menuLabel: {
    flexShrink: 1,
    color: INK,
    fontSize: 13.5,
    fontVariant: ['tabular-nums'],
    includeFontPadding: false,
  },
});
