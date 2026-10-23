import * as Haptics from 'expo-haptics';
import { LinearGradient } from 'expo-linear-gradient';
import { useCallback, useEffect, useRef, useState } from 'react';
import { type LayoutChangeEvent, Platform, Pressable, ScrollView, StyleSheet, Text, TextInput, View } from 'react-native';
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

export type AttachedFileKind = 'pdf' | 'image' | 'folder';

export type AttachedFile = {
  name: string;
  kind: AttachedFileKind;
  /** The line under the name in the preview: type, size, length. */
  meta: string;
  /** Heading and text of the summary in the preview. */
  title: string;
  summary: string;
};

type Props = {
  /** The field's text when the parent owns it. Leave it out and the composer keeps its own. */
  value?: string;
  onChangeText?: (text: string) => void;
  /**
   * Attaches three files, previews the newest and removes them again in a loop, without the
   * keyboard, until the composer is touched.
   */
  autoPlay?: boolean;
  placeholder?: string;
  /** What the attach button adds, one per tap, in this order. autoPlay adds the first three. */
  files?: AttachedFile[];
  modelLabel?: string;
  summaryLabel?: string;
  /** Called with the trimmed text and the attached files, newest first. */
  onSend?: (text: string, files: AttachedFile[]) => void;
  onAttach?: (file: AttachedFile) => void;
  onRemove?: (file: AttachedFile) => void;
  /** Called when a chip has been held long enough to open its preview. */
  onPreview?: (file: AttachedFile) => void;
  /** Called with the index of the selection tool that was picked. */
  onTool?: (index: number) => void;
  onModel?: () => void;
};

const WIDTH = 350;
// The composer with its tray open; the tray grows upwards inside this box.
const HEIGHT = 174;
const RADIUS = 24;
// Inset of the tray and of the text area from the outer edge.
const PAD = 4;
const TRAY_H = 40;
const TRAY_INSET = 4;
const CHIP_H = 32;
const CHIP_GAP = 6;
const NAME_MAX = 118;
const CARD_R = 20;
const TEXT_H = 48;
const CONTROL = 36;
const TOOL = 30;
const PREVIEW_W = 300;
// The preview hangs from the bottom edge of the chips, over the text area.
const PREVIEW_TOP = 44;
const SUMMARY_H = 138;
// How far above its place a chip starts.
const DROP_HEIGHT = 22;
const HOLD = 300;
const RETRACT = 160;
const FOLD_DELAY = 150;

const INK = '#111111';

const SLOT_SPRING = { mass: 1, stiffness: 300, damping: 26 };
const DROP_SPRING = { mass: 1, stiffness: 380, damping: 20 };
const TRAY_SPRING = { mass: 1, stiffness: 320, damping: 24 };
const PREVIEW_SPRING = { mass: 1, stiffness: 320, damping: 24 };
const TILE_SPRING = { mass: 1, stiffness: 420, damping: 30 };

// autoPlay loop, in seconds from its start.
const ADD_AT = [0.95, 1.85, 2.75];
// A scripted tap holds its button down this long before it acts.
const PRESS_LEAD = 0.15;
const HOLD_AT = 3.9;
const RELEASE_AT = 6.5;
const REMOVE_AT = [7.3, 7.85, 8.4];
const LOOP = 10;
// After the last touch, the loop waits this long before it starts again.
const RESUME = 3;

const EASE_OUT = Easing.bezier(0, 0, 0.58, 1);
const EASE_IN = Easing.bezier(0.42, 0, 1, 1);
// Closing movements end without a bounce.
const SETTLE = { duration: 300, easing: Easing.bezier(0.23, 1, 0.32, 1), reduceMotion: ReduceMotion.Never };
const QUICK = { duration: 200, easing: EASE_OUT, reduceMotion: ReduceMotion.Never };
const CSS_EASE_OUT = cubicBezier(0, 0, 0.58, 1);
const CSS_EASE_OUT_STRONG = cubicBezier(0.23, 1, 0.32, 1);

const MONO = Platform.select({ ios: 'ui-monospace', default: 'monospace' });

// Outlines, the same strings in the three stacks: 20 pt boxes for the toolbar, 18 pt boxes for
// the file icons, 12 pt boxes for the cross and the spark.
const NOTE =
  'M 16.4 9.4 L 16.4 13.4 C 16.4 15.06 15.06 16.4 13.4 16.4 L 6.6 16.4 C 4.94 16.4 3.6 15.06 3.6 13.4 L 3.6 6.6 C 3.6 4.94 4.94 3.6 6.6 3.6 L 10.6 3.6 M 15.2 2.4 L 15.2 7.2 M 12.8 4.8 L 17.6 4.8';
const WAND =
  'M 4 16 L 11.4 8.6 M 14.4 2.2 C 14.4 4.58 15.42 5.6 17.8 5.6 C 15.42 5.6 14.4 6.62 14.4 9 C 14.4 6.62 13.38 5.6 11 5.6 C 13.38 5.6 14.4 4.58 14.4 2.2 Z M 7.2 4.6 L 7.21 4.6 M 15.8 12.4 L 15.81 12.4';
const LASSO = 'M 16.4 10 C 16.4 13.53 13.53 16.4 10 16.4 C 6.47 16.4 3.6 13.53 3.6 10 C 3.6 6.47 6.47 3.6 10 3.6 C 13.53 3.6 16.4 6.47 16.4 10 Z';
const MARQUEE =
  'M 10.2 3.8 L 6.2 3.8 C 4.87 3.8 3.8 4.87 3.8 6.2 L 3.8 10.2 M 3.8 13.2 C 3.8 14.53 4.87 15.6 6.2 15.6 L 8.4 15.6 M 13.2 3.8 C 14.53 3.8 15.6 4.87 15.6 6.2 L 15.6 8.4';
const CURSOR = 'M 10.2 10.2 L 17.2 12.6 L 14 14 L 12.6 17.2 Z';
const MARK =
  'M 12.2 10 C 12.2 12.54 10.14 14.6 7.6 14.6 C 5.06 14.6 3 12.54 3 10 C 3 7.46 5.06 5.4 7.6 5.4 C 10.14 5.4 12.2 7.46 12.2 10 Z M 17 10 C 17 12.54 14.94 14.6 12.4 14.6 C 9.86 14.6 7.8 12.54 7.8 10 C 7.8 7.46 9.86 5.4 12.4 5.4 C 14.94 5.4 17 7.46 17 10 Z';
const ARROW = 'M 10 15.2 L 10 5 M 5.8 9.2 L 10 5 L 14.2 9.2';
const CROSS = 'M 3.2 3.2 L 8.8 8.8 M 8.8 3.2 L 3.2 8.8';
const SPARK =
  'M 5 2.8 C 5 5.88 6.32 7.2 9.4 7.2 C 6.32 7.2 5 8.52 5 11.6 C 5 8.52 3.68 7.2 0.6 7.2 C 3.68 7.2 5 5.88 5 2.8 Z M 9.8 0.8 C 9.8 2.34 10.46 3 12 3 C 10.46 3 9.8 3.66 9.8 5.2 C 9.8 3.66 9.14 3 7.6 3 C 9.14 3 9.8 2.34 9.8 0.8 Z';

const PDF_PAGE =
  'M 5.4 1.6 L 10.4 1.6 L 14.6 5.8 L 14.6 14.4 C 14.6 15.5 13.7 16.4 12.6 16.4 L 5.4 16.4 C 4.3 16.4 3.4 15.5 3.4 14.4 L 3.4 3.6 C 3.4 2.5 4.3 1.6 5.4 1.6 Z';
const PDF_FOLD = 'M 10.4 1.6 L 14.6 5.8 L 11.8 5.8 C 11.03 5.8 10.4 5.17 10.4 4.4 Z';
const PDF_LINES = 'M 6.2 9.6 L 11.8 9.6 M 6.2 12.6 L 9.8 12.6';
const IMAGE_TILE =
  'M 6.2 2.2 L 11.8 2.2 C 14.01 2.2 15.8 3.99 15.8 6.2 L 15.8 11.8 C 15.8 14.01 14.01 15.8 11.8 15.8 L 6.2 15.8 C 3.99 15.8 2.2 14.01 2.2 11.8 L 2.2 6.2 C 2.2 3.99 3.99 2.2 6.2 2.2 Z';
const IMAGE_HILLS = 'M 4.8 12.4 L 7.6 9 L 9.8 11.4 L 11.4 9.8 L 13.4 12.4 M 11.6 6.2 L 11.61 6.2';
const FOLDER_TAB =
  'M 1.8 8 L 1.8 4.4 C 1.8 3.52 2.52 2.8 3.4 2.8 L 6.6 2.8 L 8.4 4.8 L 14.6 4.8 C 15.48 4.8 16.2 5.52 16.2 6.4 L 16.2 8 Z';
const FOLDER_BODY =
  'M 4 6 L 14 6 C 15.22 6 16.2 6.98 16.2 8.2 L 16.2 13.6 C 16.2 14.82 15.22 15.8 14 15.8 L 4 15.8 C 2.78 15.8 1.8 14.82 1.8 13.6 L 1.8 8.2 C 1.8 6.98 2.78 6 4 6 Z';

// The three selection tools: the part drawn dashed, the part drawn solid.
const TOOLS = [
  { dashed: '', solid: WAND, label: 'Magic select' },
  { dashed: LASSO, solid: '', label: 'Lasso' },
  { dashed: MARQUEE, solid: CURSOR, label: 'Marquee' },
];

const DEFAULT_FILES: AttachedFile[] = [
  {
    name: 'folder 3',
    kind: 'folder',
    meta: 'FOLDER · 12 ITEMS · 48MB',
    title: 'Trip planning',
    summary:
      'Twelve files: three itineraries, two hotel confirmations, the car rental voucher and a shared budget sheet that was last edited on Jan 24.',
  },
  {
    name: 'cloud.png',
    kind: 'image',
    meta: 'PNG · 1.1MB · 2048 × 1365',
    title: 'Cloud over a ridge',
    summary:
      'A single cumulus cloud above a dark mountain ridge under a clear blue sky, lit from the left in the late afternoon. No people, no text.',
  },
  {
    name: 'contract-agreement brief.pdf',
    kind: 'pdf',
    meta: 'PDF · 2.3MB · 93 LINES',
    title: 'Service Agreement',
    summary:
      'This Service Agreement is entered into as of Jan 27, by and between the Client and the Provider, each with a mailing address on file. The Provider agrees to perform the services described in Exhibit A, in accordance with the terms and conditions of this Agreement, by the dates set out in its schedule.',
  },
];

type Entry = { id: number; file: AttachedFile; leaving: boolean };
type Preview = { file: AttachedFile; left: number; origin: number };

/** A stroked outline. */
function Glyph({ d, color, size = 20, strokeWidth = 1.5, dash }: { d: string; color: string; size?: number; strokeWidth?: number; dash?: number[] }) {
  return (
    <Svg width={size} height={size} viewBox={`0 0 ${size} ${size}`}>
      <Path d={d} stroke={color} strokeWidth={strokeWidth} strokeLinecap="round" strokeLinejoin="round" strokeDasharray={dash} fill="none" />
    </Svg>
  );
}

/** A red page, a blue picture or a yellow folder, drawn in an 18 pt box. */
function FileIcon({ kind, size = 18 }: { kind: AttachedFileKind; size?: number }) {
  const line = { stroke: '#ffffff', strokeWidth: 1.3, strokeLinecap: 'round' as const, strokeLinejoin: 'round' as const, fill: 'none' };
  return (
    <Svg width={size} height={size} viewBox="0 0 18 18">
      {kind === 'pdf' ? (
        <>
          <Path d={PDF_PAGE} fill="#ff5b4f" />
          <Path d={PDF_FOLD} fill="#ffc2bd" />
          <Path d={PDF_LINES} {...line} />
        </>
      ) : kind === 'image' ? (
        <>
          <Path d={IMAGE_TILE} fill="#8ecbff" />
          <Path d={IMAGE_HILLS} {...line} />
        </>
      ) : (
        <>
          <Path d={FOLDER_TAB} fill="#f5a623" />
          <Path d={FOLDER_BODY} fill="#ffc83d" />
        </>
      )}
    </Svg>
  );
}

/** The label's letters run from violet to amber. */
function gradientLetters(label: string) {
  const from = [139, 92, 246];
  const to = [245, 158, 11];
  const last = Math.max(1, label.length - 1);
  return label.split('').map((letter, index) => {
    const [r, g, b] = from.map((channel, at) => Math.round(channel + ((to[at] - channel) * index) / last));
    return (
      <Text key={index} style={{ color: `rgb(${r},${g},${b})` }}>
        {letter}
      </Text>
    );
  });
}

type ChipProps = {
  id: number;
  file: AttachedFile;
  leaving: boolean;
  /** The script is holding the chip, or its cross. */
  held: boolean;
  crossHeld: boolean;
  reducedMotion: boolean;
  register: (view: View | null) => void;
  onHold: (holding: boolean) => void;
  onRemove: () => void;
  /** Called with the chip's id once it has shrunk away. */
  onGone: (id: number) => void;
};

/**
 * One attachment in the tray. It opens its own room in the row, drops into it from above, and
 * gives the room back when it leaves.
 */
function Chip({ id, file, leaving, held, crossHeld, reducedMotion, register, onHold, onRemove, onGone }: ChipProps) {
  const [holding, setHolding] = useState(false);
  const natural = useSharedValue(0);
  // Share of its width the chip takes in the row, how far it has dropped in, how far it has gone.
  const room = useSharedValue(0);
  const drop = useSharedValue(0);
  const gone = useSharedValue(0);

  useEffect(() => {
    // The row opens first, then the chip lands in the gap.
    room.set(reducedMotion ? withTiming(1, QUICK) : withSpring(1, SLOT_SPRING));
    drop.set(reducedMotion ? withTiming(1, QUICK) : withDelay(60, withSpring(1, DROP_SPRING)));
  }, [reducedMotion, room, drop]);

  useEffect(() => {
    if (!leaving) return;
    gone.set(withTiming(1, { duration: 180, easing: EASE_IN, reduceMotion: ReduceMotion.Never }));
    room.set(withDelay(80, withTiming(0, SETTLE)));
    const done = setTimeout(() => onGone(id), 450);
    return () => clearTimeout(done);
  }, [leaving, id, onGone, gone, room]);

  const roomStyle = useAnimatedStyle(() => ({ width: Math.max(0, (natural.get() + CHIP_GAP) * room.get()) }));
  const chipStyle = useAnimatedStyle(() => {
    const d = drop.get();
    const g = gone.get();
    return {
      opacity: Math.min(1, Math.max(0, d)) * (1 - g),
      transform: reducedMotion ? [] : [{ translateY: -DROP_HEIGHT * (1 - d) }, { scale: (0.8 + 0.2 * d) * (1 - 0.4 * g) }],
    };
  });

  const onLayout = (e: LayoutChangeEvent) => natural.set(e.nativeEvent.layout.width);

  return (
    <Animated.View style={[styles.room, roomStyle]}>
      <Animated.View ref={register} onLayout={onLayout} style={[styles.chipSlot, chipStyle]}>
        <Pressable
          // Held long enough, the chip shows its preview until the finger lifts.
          delayLongPress={HOLD}
          onLongPress={() => {
            setHolding(true);
            onHold(true);
          }}
          onPressOut={() => {
            if (!holding) return;
            setHolding(false);
            onHold(false);
          }}
          accessibilityLabel={file.name}
          accessibilityHint="Hold for a preview"
        >
          <Animated.View
            style={[
              styles.chip,
              {
                backgroundColor: holding || held ? '#f0f0f0' : '#ffffff',
                transitionProperty: 'backgroundColor',
                transitionDuration: 150,
                transitionTimingFunction: CSS_EASE_OUT,
              },
            ]}
          >
            <FileIcon kind={file.kind} />
            <Text style={styles.chipName} numberOfLines={1}>
              {file.name}
            </Text>
            <Pressable onPress={onRemove} accessibilityRole="button" accessibilityLabel={`Remove ${file.name}`}>
              {({ pressed }) => (
                <Animated.View
                  style={[
                    styles.cross,
                    {
                      transform: [{ scale: pressed || crossHeld ? 0.8 : 1 }],
                      transitionProperty: 'transform',
                      transitionDuration: 130,
                      transitionTimingFunction: CSS_EASE_OUT_STRONG,
                    },
                  ]}
                >
                  <Glyph d={CROSS} color={INK} size={12} />
                </Animated.View>
              )}
            </Pressable>
          </Animated.View>
        </Pressable>
      </Animated.View>
    </Animated.View>
  );
}

/**
 * Prompt composer with a tray of attachments. Files drop into a strip that grows out of the top
 * of the text area, each one pushing the earlier chips along; holding a chip drops a preview card
 * out of it, and a chip's cross shrinks it away, the tray folding shut after the last one.
 */
export function AttachmentTrayInput({
  value,
  onChangeText,
  autoPlay = false,
  placeholder = 'Add instructions from attached images...',
  files = DEFAULT_FILES,
  modelLabel = 'Nova 2.0',
  summaryLabel = 'Summary by AI',
  onSend,
  onAttach,
  onRemove,
  onPreview,
  onTool,
  onModel,
}: Props) {
  const reducedMotion = useReducedMotion();
  const controlled = value != null;
  const auto = autoPlay && !controlled && !reducedMotion;

  const [inner, setInner] = useState('');
  const [focused, setFocused] = useState(false);
  // A touch hands the composer to the user until it has been left alone for a while.
  const [taken, setTaken] = useState(false);
  const [activity, setActivity] = useState(0);
  // The chips in the tray, newest first; a chip on its way out stays until it has shrunk.
  const [entries, setEntries] = useState<Entry[]>([]);
  // The file in the preview card, and where the card hangs: its left edge and the point of its
  // top edge that sits under the chip.
  const [preview, setPreview] = useState<Preview | null>(null);
  const [previewOpen, setPreviewOpen] = useState(false);
  const [tool, setTool] = useState(0);
  // The script's finger.
  const [heldAdd, setHeldAdd] = useState(false);
  const [heldChip, setHeldChip] = useState<number | null>(null);
  const [heldCross, setHeldCross] = useState<number | null>(null);

  const text = value ?? inner;
  // The script is running.
  const playing = auto && !taken;
  const attached = entries.filter((entry) => !entry.leaving);
  const trayOpen = attached.length > 0;
  const canSend = text.trim().length > 0 || trayOpen;
  const idle = auto && taken && !focused && text.length === 0;

  const open = useSharedValue(0);
  const card = useSharedValue(0);
  const tile = useSharedValue(0);
  const root = useRef<View>(null);
  const scroller = useRef<ScrollView>(null);
  const chips = useRef(new Map<number, View>());
  const serial = useRef(0);
  const nextFile = useRef(0);

  // The strip grows out of the top edge of the text area, and folds back into it.
  useEffect(() => {
    if (reducedMotion) open.set(withTiming(trayOpen ? 1 : 0, QUICK));
    else if (trayOpen) open.set(withSpring(1, TRAY_SPRING));
    else open.set(withDelay(FOLD_DELAY, withTiming(0, SETTLE)));
  }, [trayOpen, reducedMotion, open]);

  // The preview drops out of the chip it belongs to, and is pulled back into it.
  useEffect(() => {
    if (reducedMotion) card.set(withTiming(previewOpen ? 1 : 0, QUICK));
    else if (previewOpen) card.set(withSpring(1, PREVIEW_SPRING));
    else card.set(withTiming(0, { duration: RETRACT, easing: EASE_IN, reduceMotion: ReduceMotion.Never }));
  }, [previewOpen, reducedMotion, card]);

  // The white tile slides under the tool that is picked.
  useEffect(() => {
    tile.set(reducedMotion ? withTiming(tool, QUICK) : withSpring(tool, TILE_SPRING));
  }, [tool, reducedMotion, tile]);

  const add = useCallback((file: AttachedFile) => {
    serial.current += 1;
    const id = serial.current;
    setEntries((list) => [{ id, file, leaving: false }, ...list]);
    // A new chip lands at the front, so the row goes back to its start.
    scroller.current?.scrollTo({ x: 0, animated: true });
    return id;
  }, []);

  const leave = useCallback((id: number) => {
    setEntries((list) => list.map((entry) => (entry.id === id ? { ...entry, leaving: true } : entry)));
  }, []);

  const forget = useCallback((id: number) => {
    setEntries((list) => list.filter((entry) => entry.id !== id));
  }, []);

  const openPreview = useCallback((id: number, file: AttachedFile) => {
    const chip = chips.current.get(id);
    if (!chip || !root.current) return;
    root.current.measureInWindow((rootX) => {
      chip.measureInWindow((chipX, _chipY, chipWidth) => {
        const left = Math.min(Math.max(chipX - rootX - 6, 0), WIDTH - PREVIEW_W);
        const origin = Math.min(Math.max(chipX - rootX + chipWidth / 2 - left, 0), PREVIEW_W);
        setPreview({ file, left, origin });
        setPreviewOpen(true);
      });
    });
  }, []);

  // One loop after another, every step at its own moment after the loop began.
  useEffect(() => {
    if (!playing || files.length === 0) return;
    let timers: ReturnType<typeof setTimeout>[] = [];
    const run = (start: number) => {
      const at = (seconds: number, step: () => void) => {
        timers.push(setTimeout(step, Math.max(0, start + seconds * 1000 - performance.now())));
      };
      // The chips this loop has added, newest first.
      const added: { id: number; file: AttachedFile }[] = [];
      timers = [];
      ADD_AT.forEach((moment, index) => {
        at(moment - PRESS_LEAD, () => setHeldAdd(true));
        at(moment, () => {
          setHeldAdd(false);
          const file = files[index % files.length];
          added.unshift({ id: add(file), file });
        });
      });
      at(HOLD_AT, () => setHeldChip(added[0].id));
      at(HOLD_AT + HOLD / 1000, () => openPreview(added[0].id, added[0].file));
      at(RELEASE_AT, () => {
        setHeldChip(null);
        setPreviewOpen(false);
      });
      REMOVE_AT.forEach((moment, index) => {
        at(moment - PRESS_LEAD, () => setHeldCross(added[index].id));
        at(moment, () => {
          setHeldCross(null);
          leave(added[index].id);
        });
      });
      at(LOOP, () => run(start + LOOP * 1000));
    };
    nextFile.current = ADD_AT.length;
    run(performance.now());
    return () => timers.forEach(clearTimeout);
  }, [playing, files, add, leave, openPreview]);

  // The loop starts over from an empty tray a few seconds after the last touch.
  useEffect(() => {
    if (!idle) return;
    let restart: ReturnType<typeof setTimeout> | undefined;
    const wait = setTimeout(() => {
      setPreviewOpen(false);
      setEntries((list) => list.map((entry) => ({ ...entry, leaving: true })));
      restart = setTimeout(() => setTaken(false), 900);
    }, RESUME * 1000);
    return () => {
      clearTimeout(wait);
      clearTimeout(restart);
    };
  }, [idle, activity]);

  const tap = (style = Haptics.ImpactFeedbackStyle.Light) => {
    if (process.env.EXPO_OS !== 'web') Haptics.impactAsync(style).catch(() => {});
  };

  /** Any touch takes the composer away from the script. */
  const interrupt = () => {
    setActivity((count) => count + 1);
    if (!playing) return;
    setTaken(true);
    setHeldAdd(false);
    setHeldChip(null);
    setHeldCross(null);
    setPreviewOpen(false);
  };

  const write = (next: string) => {
    if (!controlled) setInner(next);
    onChangeText?.(next);
  };

  const attach = () => {
    interrupt();
    if (files.length === 0) return;
    const file = files[nextFile.current % files.length];
    nextFile.current += 1;
    add(file);
    tap();
    onAttach?.(file);
  };

  const send = () => {
    interrupt();
    if (!canSend) return;
    tap();
    onSend?.(
      text.trim(),
      attached.map((entry) => entry.file),
    );
    write('');
    attached.forEach((entry) => leave(entry.id));
  };

  const trayStyle = useAnimatedStyle(() => ({ height: Math.max(0, TRAY_H * open.get()) }));
  const tileStyle = useAnimatedStyle(() => ({ transform: [{ translateX: tile.get() * TOOL }] }));
  const cardStyle = useAnimatedStyle(() => {
    const c = card.get();
    return {
      opacity: Math.min(1, Math.max(0, c)),
      transform: reducedMotion ? [] : [{ translateY: -10 * (1 - c) }, { scale: 0.6 + 0.4 * c }],
    };
  });
  // The rows of the preview come up one after another behind the card.
  const rise = (row: number) => ({
    opacity: previewOpen ? 1 : 0,
    transform: [{ translateY: previewOpen || reducedMotion ? 0 : 8 }],
    transitionProperty: ['opacity', 'transform'] as ('opacity' | 'transform')[],
    transitionDuration: previewOpen ? 260 : 100,
    transitionDelay: previewOpen ? 100 + row * 60 : 0,
    transitionTimingFunction: CSS_EASE_OUT,
  });
  const dip = (down: boolean) => ({
    transform: [{ scale: down ? 0.92 : 1 }],
    transitionProperty: 'transform' as const,
    transitionDuration: 130,
    transitionTimingFunction: CSS_EASE_OUT_STRONG,
  });

  return (
    <View ref={root} style={styles.box}>
      <View style={styles.composer}>
        <Animated.View style={[styles.tray, trayStyle]}>
          <ScrollView
            ref={scroller}
            horizontal
            showsHorizontalScrollIndicator={false}
            keyboardShouldPersistTaps="handled"
            style={styles.scroller}
            contentContainerStyle={styles.row}
          >
            {entries.map((entry) => (
              <Chip
                key={entry.id}
                id={entry.id}
                file={entry.file}
                leaving={entry.leaving}
                held={heldChip === entry.id}
                crossHeld={heldCross === entry.id}
                reducedMotion={reducedMotion}
                register={(view) => {
                  if (view) chips.current.set(entry.id, view);
                  else chips.current.delete(entry.id);
                }}
                onHold={(holding) => {
                  interrupt();
                  if (!holding) {
                    setPreviewOpen(false);
                    return;
                  }
                  tap(Haptics.ImpactFeedbackStyle.Medium);
                  onPreview?.(entry.file);
                  openPreview(entry.id, entry.file);
                }}
                onRemove={() => {
                  interrupt();
                  tap();
                  onRemove?.(entry.file);
                  leave(entry.id);
                }}
                onGone={forget}
              />
            ))}
          </ScrollView>
        </Animated.View>

        <View style={styles.card}>
          <TextInput
            value={text}
            onChangeText={write}
            onFocus={() => {
              setFocused(true);
              interrupt();
            }}
            onBlur={() => {
              setFocused(false);
              setActivity((count) => count + 1);
            }}
            placeholder={placeholder}
            placeholderTextColor="#b5b5b5"
            selectionColor={INK}
            multiline
            style={styles.input}
          />

          <View style={styles.toolbar}>
            <Pressable onPress={attach} accessibilityRole="button" accessibilityLabel="Attach a file">
              {({ pressed }) => (
                <Animated.View style={[styles.bezel, styles.square, dip(pressed || heldAdd)]}>
                  <Glyph d={NOTE} color={INK} />
                </Animated.View>
              )}
            </Pressable>

            <View style={styles.tools}>
              <Animated.View style={[styles.tile, tileStyle]} />
              {TOOLS.map((glyph, slot) => {
                const color = slot === tool ? INK : '#8d8d8d';
                return (
                  <Pressable
                    key={glyph.label}
                    style={styles.tool}
                    onPress={() => {
                      setTool(slot);
                      tap();
                      onTool?.(slot);
                    }}
                    accessibilityRole="button"
                    accessibilityLabel={glyph.label}
                    accessibilityState={{ selected: slot === tool }}
                  >
                    <View style={styles.layer}>
                      <Glyph d={glyph.dashed} color={color} dash={[2.3, 2.5]} />
                    </View>
                    <Glyph d={glyph.solid} color={color} />
                  </Pressable>
                );
              })}
            </View>

            <Pressable onPress={onModel} accessibilityRole="button">
              {({ pressed }) => (
                <Animated.View style={[styles.bezel, styles.model, dip(pressed)]}>
                  <Glyph d={MARK} color={INK} />
                  <Text style={styles.modelLabel} numberOfLines={1}>
                    {modelLabel}
                  </Text>
                </Animated.View>
              )}
            </Pressable>

            <View style={styles.spacer} />

            <Pressable onPress={send} disabled={!canSend} accessibilityRole="button" accessibilityLabel="Send">
              {({ pressed }) => (
                <Animated.View
                  style={[
                    styles.send,
                    {
                      backgroundColor: canSend ? INK : '#d6d6d6',
                      transform: [{ scale: pressed ? 0.92 : 1 }],
                      transitionProperty: ['backgroundColor', 'transform'],
                      transitionDuration: [200, 130],
                      transitionTimingFunction: [CSS_EASE_OUT, CSS_EASE_OUT_STRONG],
                    },
                  ]}
                >
                  <Glyph d={ARROW} color="#ffffff" strokeWidth={1.9} />
                </Animated.View>
              )}
            </Pressable>
          </View>
        </View>
      </View>

      {/* The preview hangs over the text area and past the composer's bottom edge. */}
      <Animated.View
        pointerEvents="none"
        accessibilityElementsHidden={!previewOpen}
        importantForAccessibility={previewOpen ? 'auto' : 'no-hide-descendants'}
        style={[styles.preview, { left: preview?.left ?? 0, transformOrigin: [preview?.origin ?? 40, 0, 0] }, cardStyle]}
      >
        <Animated.Text style={[styles.previewName, rise(0)]} numberOfLines={1}>
          {preview?.file.name ?? ''}
        </Animated.Text>
        <Animated.View style={[styles.previewMeta, rise(1)]}>
          <FileIcon kind={preview?.file.kind ?? 'pdf'} size={14} />
          <Text style={styles.metaText} numberOfLines={1}>
            {preview?.file.meta ?? ''}
          </Text>
        </Animated.View>
        <Animated.View style={[styles.summary, rise(2)]}>
          <View style={styles.summaryLabel}>
            <Svg width={12} height={12} viewBox="0 0 12 12">
              <Path d={SPARK} fill="#8b5cf6" />
            </Svg>
            <Text style={styles.summaryLabelText}>{gradientLetters(summaryLabel)}</Text>
          </View>
          <Text style={styles.summaryTitle} numberOfLines={1}>
            {preview?.file.title ?? ''}
          </Text>
          <Text style={styles.summaryText}>{preview?.file.summary ?? ''}</Text>
          {/* The text runs out of the box and fades before its edge. */}
          <LinearGradient colors={['rgba(255,255,255,0)', '#ffffff']} style={styles.summaryFade} />
        </Animated.View>
      </Animated.View>
    </View>
  );
}

const styles = StyleSheet.create({
  box: {
    width: WIDTH,
    height: HEIGHT,
    justifyContent: 'flex-end',
  },
  composer: {
    // The hairline takes one point of the padding.
    padding: PAD - 1,
    borderRadius: RADIUS,
    borderWidth: 1,
    borderColor: '#e8e8e8',
    backgroundColor: '#f5f5f5',
    boxShadow: '0 6px 24px rgba(0,0,0,0.05)',
  },
  tray: {
    overflow: 'hidden',
  },
  scroller: {
    position: 'absolute',
    left: 0,
    right: 0,
    bottom: 0,
    height: TRAY_H,
  },
  row: {
    paddingLeft: TRAY_INSET,
  },
  room: {
    height: TRAY_H,
  },
  chipSlot: {
    position: 'absolute',
    left: 0,
    top: (TRAY_H - CHIP_H) / 2,
  },
  chip: {
    height: CHIP_H,
    // The hairline takes one point of the padding on each side.
    paddingLeft: 7,
    paddingRight: 2,
    borderRadius: CHIP_H / 2,
    borderWidth: 1,
    borderColor: '#ebebeb',
    boxShadow: '0 1px 3px rgba(0,0,0,0.06)',
    flexDirection: 'row',
    alignItems: 'center',
  },
  chipName: {
    maxWidth: NAME_MAX,
    marginLeft: 6,
    color: INK,
    fontSize: 14,
    fontWeight: '500',
  },
  cross: {
    width: 26,
    height: 26,
    alignItems: 'center',
    justifyContent: 'center',
  },
  card: {
    borderRadius: CARD_R,
    borderWidth: 1,
    borderColor: '#e6e6e6',
    backgroundColor: '#ffffff',
    boxShadow: '0 1px 6px rgba(0,0,0,0.04)',
  },
  input: {
    height: TEXT_H,
    // One point less than the design's 16 and 14: the card's hairline takes it.
    marginHorizontal: 15,
    marginTop: 13,
    paddingTop: 0,
    paddingBottom: 0,
    color: INK,
    fontSize: 17,
    textAlignVertical: 'top',
  },
  toolbar: {
    padding: 11,
    paddingTop: 12,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
  },
  bezel: {
    height: CONTROL,
    borderRadius: 10,
    borderWidth: 1,
    borderColor: '#e8e8e8',
    backgroundColor: '#ffffff',
    boxShadow: '0 1px 3px rgba(0,0,0,0.05)',
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
  },
  square: {
    width: CONTROL,
  },
  tools: {
    padding: 3,
    borderRadius: 10,
    backgroundColor: '#f3f3f3',
    flexDirection: 'row',
  },
  tile: {
    position: 'absolute',
    left: 3,
    top: 3,
    width: TOOL,
    height: TOOL,
    borderRadius: 8,
    backgroundColor: '#ffffff',
    boxShadow: '0 1px 3px rgba(0,0,0,0.1)',
  },
  tool: {
    width: TOOL,
    height: TOOL,
    alignItems: 'center',
    justifyContent: 'center',
  },
  layer: {
    position: 'absolute',
  },
  model: {
    paddingLeft: 7,
    paddingRight: 10,
    gap: 6,
  },
  modelLabel: {
    color: INK,
    fontSize: 15,
    fontWeight: '500',
  },
  spacer: {
    flex: 1,
  },
  send: {
    width: CONTROL,
    height: CONTROL,
    borderRadius: CONTROL / 2,
    alignItems: 'center',
    justifyContent: 'center',
  },
  preview: {
    position: 'absolute',
    top: PREVIEW_TOP,
    width: PREVIEW_W,
    // The hairline takes one point of the padding.
    padding: 11,
    borderRadius: 20,
    borderWidth: 1,
    borderColor: '#ededed',
    backgroundColor: '#ffffff',
    boxShadow: '0 12px 40px rgba(0,0,0,0.12)',
  },
  previewName: {
    height: 22,
    marginLeft: 4,
    color: INK,
    fontSize: 16,
    lineHeight: 22,
    fontWeight: '500',
  },
  previewMeta: {
    height: 16,
    marginTop: 4,
    marginLeft: 4,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
  },
  metaText: {
    color: '#8a8a8a',
    fontFamily: MONO,
    fontSize: 12,
    letterSpacing: 0.4,
  },
  summary: {
    height: SUMMARY_H,
    marginTop: 10,
    padding: 13,
    borderRadius: 14,
    borderWidth: 1,
    borderColor: '#ececec',
    overflow: 'hidden',
  },
  summaryLabel: {
    height: 14,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 4,
  },
  summaryLabelText: {
    fontSize: 11,
    fontWeight: '500',
  },
  summaryTitle: {
    height: 20,
    marginTop: 8,
    color: '#2a2a2e',
    fontSize: 15,
    lineHeight: 20,
    fontWeight: '600',
  },
  summaryText: {
    marginTop: 6,
    color: '#6f6f73',
    fontSize: 12.5,
    lineHeight: 17,
  },
  summaryFade: {
    position: 'absolute',
    left: 0,
    right: 0,
    bottom: 0,
    height: 44,
  },
});
