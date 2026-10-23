import { Ionicons } from '@expo/vector-icons';
import { Canvas, Circle, Path, Skia } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useEffect, useRef, useState } from 'react';
import { Pressable, StyleSheet, Text, View, useWindowDimensions } from 'react-native';
import Animated, {
  Easing,
  ReduceMotion,
  cancelAnimation,
  interpolateColor,
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

import { type FileItem, FileRow, TRASH_INK, TRASH_SIZE } from './FileRow';

type Props = {
  /** Presses a trash button by itself, confirms, and brings the file back, in a loop. */
  autoPlay?: boolean;
  files?: FileItem[];
  title?: string;
  deleteTitle?: string;
  deleteMessage?: string;
  successTitle?: string;
  successMessage?: string;
  cancelLabel?: string;
  confirmLabel?: string;
  /** Called when the file's deletion is confirmed and the dialog is closed. */
  onDelete?: (file: FileItem) => void;
};

const FILES: FileItem[] = [
  { name: 'Project brief.pdf', size: '2.4 MB', detail: 'Edited 2h ago', tint: '#fde8e8', ink: '#e5484d', icon: 'document-text' },
  {
    name: 'Team offsite.png',
    size: '5.1 MB',
    detail: 'Added yesterday',
    tint: '#e3ecfd',
    ink: '#3b6fe0',
    icon: 'image',
    photo: 'https://images.unsplash.com/photo-1522202176988-66273c2fd55f?auto=format&fit=crop&w=140&h=140&q=80',
  },
  { name: 'Budget 2026.xlsx', size: '860 KB', detail: 'Shared with 4', tint: '#e3f6ea', ink: '#22a35a', icon: 'grid' },
  { name: 'Meeting notes.txt', size: '12 KB', detail: 'Edited Monday', tint: '#fff2d9', ink: '#d98a00', icon: 'reader' },
];

// The storage card at the top: what each kind of file takes, as a share of the bar.
const STORAGE = [
  { label: 'Documents', share: 0.34, color: '#e5484d' },
  { label: 'Photos', share: 0.26, color: '#3b6fe0' },
  { label: 'Sheets', share: 0.12, color: '#22a35a' },
  { label: 'Other', share: 0.08, color: '#f0a100' },
];
const TABS: { label: string; icon: keyof typeof Ionicons.glyphMap }[] = [
  { label: 'Recents', icon: 'time' },
  { label: 'Shared', icon: 'people-outline' },
  { label: 'Browse', icon: 'folder-outline' },
];

// One loop, in seconds: the trash of the second row is pressed, the dialog grows out of it, the
// icon shakes, Confirm is pressed, the dialog turns into its success version, closes, and the row
// collapses; at the end the row returns.
export const LOOP = 9;
const TARGET_ROW = 1;
const PRESS_AT = 0.8;
const OPEN_AT = 1.1;
const SHAKE_AT = 2.6;
const CONFIRM_PRESS_AT = 3.6;
const CONFIRM_AT = 3.8;
const CLOSE_AT = 6.2;
const DELETE_AT = 6.4;
const RETURN_AT = 8.2;
// After a touch the loop waits this long, then starts again from the list.
const USER_HOLD = 3;

const GROW_SPRING = { duration: 500, dampingRatio: 0.82, reduceMotion: ReduceMotion.Never };
const SHRINK_SPRING = { duration: 420, dampingRatio: 0.9, reduceMotion: ReduceMotion.Never };
const CONTENT_AFTER = 250;
const CONTENT_MS = 700;
const SWAP_MS = 400;
const DRAW_MS = 400;
const RIPPLE_MS = 600;
const CLOSE_MS = 500;
const FADE_MS = 200;
const SHAKE_DEGREES = 8;
const SHAKE_MS = 70;
const CLOSE_SCALE = 0.9;
const SCRIM_OPACITY = 0.4;

const DIALOG_WIDTH = 320;
const DIALOG_HEIGHT = 248;
const RADIUS_FROM = 11;
const RADIUS_TO = 28;
const TILE = 58;
const BUTTON_HEIGHT = 50;
const BUTTON_GAP = 12;
const SIDE = 24;
const FULL_WIDTH = DIALOG_WIDTH - SIDE * 2;
const BUTTON_WIDTH = (FULL_WIDTH - BUTTON_GAP) / 2;

const RED = TRASH_INK;
const RED_TILE = '#fdeced';
const GREEN = '#52b992';
const GREEN_INK = '#4cb890';
const GREEN_TILE = '#e8f8f1';
const PAGE = '#f5f5f7';
const INK = '#111318';
const MUTE = '#71757e';

// The check inside the success ring, in a 30 point box.
const CHECK = Skia.Path.MakeFromSVGString('M9.5 15.5L13.2 19L20.5 11')!;

const stage = (value: number, start: number, length: number) => {
  'worklet';
  return Math.min(1, Math.max(0, (value - start) / length));
};

const backOut = (p: number) => {
  'worklet';
  return 1 + 2.70158 * (p - 1) ** 3 + 1.70158 * (p - 1) ** 2;
};

/**
 * A list of files. A trash button grows into a confirmation dialog: the dialog starts as the
 * button's own rounded square, measured on screen, and spreads to the centre while its content pops
 * in. Confirm turns the icon into a check that draws itself and the two buttons into one; closing
 * the dialog collapses the deleted row. Cancel folds the dialog back into the button it came from.
 */
export function GrowDeleteDialog({
  autoPlay = false,
  files = FILES,
  title = 'Files',
  deleteTitle = 'Delete',
  deleteMessage = 'Are you sure you want to delete?',
  successTitle = 'Success',
  successMessage = 'Action is done successfully!',
  cancelLabel = 'Cancel',
  confirmLabel = 'Confirm',
  onDelete,
}: Props) {
  const reducedMotion = useReducedMotion();
  const insets = useSafeAreaInsets();
  const { width, height } = useWindowDimensions();
  const [removed, setRemoved] = useState<number[]>([]);
  const [target, setTarget] = useState<number | null>(null);
  const [pressed, setPressed] = useState<number | null>(null);
  const [confirmPressed, setConfirmPressed] = useState(false);
  const [succeeded, setSucceeded] = useState(false);
  // Changes on every touch, which restarts the autoPlay loop behind a pause.
  const [touches, setTouches] = useState(0);
  const trashes = useRef<(View | null)[]>([]);

  // Where the dialog grows from: the trash button's rect in the window.
  const originX = useSharedValue(0);
  const originY = useSharedValue(0);
  const grow = useSharedValue(0);
  const content = useSharedValue(0);
  const done = useSharedValue(0);
  const draw = useSharedValue(0);
  const ripple = useSharedValue(0);
  const shake = useSharedValue(0);
  const out = useSharedValue(0);

  const open = (index: number) => {
    const button = trashes.current[index];
    if (!button) return;
    button.measureInWindow((x, y) => {
      originX.set(x);
      originY.set(y);
      setTarget(index);
      setSucceeded(false);
      out.set(0);
      done.set(0);
      draw.set(0);
      ripple.set(0);
      if (reducedMotion) {
        grow.set(withTiming(1, { duration: FADE_MS, reduceMotion: ReduceMotion.Never }));
        content.set(withTiming(1, { duration: FADE_MS, reduceMotion: ReduceMotion.Never }));
        return;
      }
      grow.set(withSpring(1, GROW_SPRING));
      content.set(withDelay(CONTENT_AFTER, withTiming(1, { duration: CONTENT_MS, easing: Easing.linear, reduceMotion: ReduceMotion.Never })));
    });
  };

  /** The dialog is closed: the next one starts from the confirmation. */
  const finish = () => {
    grow.set(0);
    content.set(0);
    out.set(0);
    done.set(0);
    draw.set(0);
    setTarget(null);
    setSucceeded(false);
  };

  const fold = () => {
    content.set(withTiming(0, { duration: 150, reduceMotion: ReduceMotion.Never }));
    grow.set(
      withDelay(
        100,
        reducedMotion
          ? withTiming(0, { duration: FADE_MS, reduceMotion: ReduceMotion.Never }, (ok) => {
              if (ok) scheduleOnRN(finish);
            })
          : withSpring(0, SHRINK_SPRING, (ok) => {
              if (ok) scheduleOnRN(finish);
            }),
      ),
    );
  };

  const confirm = () => {
    if (succeeded || target === null) return;
    setSucceeded(true);
    done.set(withTiming(1, { duration: SWAP_MS, reduceMotion: ReduceMotion.Never }));
    draw.set(withDelay(SWAP_MS * 0.4, withTiming(1, { duration: DRAW_MS, easing: Easing.out(Easing.cubic), reduceMotion: ReduceMotion.Never })));
    if (reducedMotion) return;
    ripple.set(0);
    ripple.set(withTiming(1, { duration: RIPPLE_MS, easing: Easing.out(Easing.quad), reduceMotion: ReduceMotion.Never }));
  };

  const removeFile = (index: number) => {
    setRemoved((list) => (list.includes(index) ? list : [...list, index]));
    onDelete?.(files[index]);
  };

  /** Close the success dialog; the file it was about goes. */
  const close = (deleteNow: boolean) => {
    const index = target;
    out.set(
      withTiming(1, { duration: reducedMotion ? FADE_MS : CLOSE_MS, easing: Easing.in(Easing.quad), reduceMotion: ReduceMotion.Never }, (ok) => {
        if (ok) scheduleOnRN(finish);
      }),
    );
    if (index !== null && deleteNow) removeFile(index);
  };

  const shakeIcon = () => {
    if (reducedMotion) return;
    shake.set(
      withSequence(
        withTiming(SHAKE_DEGREES, { duration: SHAKE_MS }),
        withTiming(-SHAKE_DEGREES, { duration: SHAKE_MS * 2 }),
        withTiming(SHAKE_DEGREES, { duration: SHAKE_MS * 2 }),
        withTiming(-SHAKE_DEGREES, { duration: SHAKE_MS * 2 }),
        withTiming(SHAKE_DEGREES, { duration: SHAKE_MS * 2 }),
        withTiming(0, { duration: SHAKE_MS }),
      ),
    );
  };

  useEffect(
    () => () => {
      for (const value of [grow, content, done, draw, ripple, shake, out]) cancelAnimation(value);
    },
    [grow, content, done, draw, ripple, shake, out],
  );

  // The loop reads the latest state when its timers fire.
  const latest = useRef({ open, confirm, close, removeFile, shakeIcon });
  useEffect(() => {
    latest.current = { open, confirm, close, removeFile, shakeIcon };
  });

  useEffect(() => {
    if (!autoPlay) return;
    const timers: ReturnType<typeof setTimeout>[] = [];
    const at = (origin: number, seconds: number, run: () => void) =>
      timers.push(setTimeout(run, origin + seconds * 1000 - Date.now()));
    const cycle = (origin: number) => {
      at(origin, PRESS_AT, () => setPressed(TARGET_ROW));
      at(origin, OPEN_AT, () => {
        setPressed(null);
        latest.current.open(TARGET_ROW);
      });
      at(origin, SHAKE_AT, () => latest.current.shakeIcon());
      at(origin, CONFIRM_PRESS_AT, () => setConfirmPressed(true));
      at(origin, CONFIRM_AT, () => {
        setConfirmPressed(false);
        latest.current.confirm();
      });
      at(origin, CLOSE_AT, () => latest.current.close(false));
      at(origin, DELETE_AT, () => latest.current.removeFile(TARGET_ROW));
      at(origin, RETURN_AT, () => setRemoved([]));
      at(origin, LOOP, () => cycle(origin + LOOP * 1000));
    };
    if (touches === 0) {
      cycle(Date.now());
    } else {
      timers.push(
        setTimeout(() => {
          setPressed(null);
          setConfirmPressed(false);
          setRemoved([]);
          latest.current.close(false);
          cycle(Date.now() + 800);
        }, USER_HOLD * 1000),
      );
    }
    return () => timers.forEach(clearTimeout);
  }, [autoPlay, touches]);

  const byHand = autoPlay === false || touches > 0;

  const trash = (index: number) => {
    if (target !== null) return;
    Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
    open(index);
  };

  const confirmByHand = () => {
    Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Medium).catch(() => {});
    if (!succeeded) confirm();
    else close(true);
  };

  const cx = (width - DIALOG_WIDTH) / 2;
  const cy = Math.max(insets.top + 20, (height - DIALOG_HEIGHT) / 2);

  const scrimStyle = useAnimatedStyle(() => ({
    opacity: SCRIM_OPACITY * Math.min(1, Math.max(0, grow.get() * 1.5)) * (1 - out.get()),
  }));
  const frameStyle = useAnimatedStyle(() => {
    const g = grow.get();
    const e = out.get();
    if (reducedMotion) return { left: cx, top: cy, width: DIALOG_WIDTH, height: DIALOG_HEIGHT, borderRadius: RADIUS_TO, opacity: Math.min(1, Math.max(0, g)) * (1 - e), transform: [{ scale: 1 }] };
    return {
      left: originX.get() + (cx - originX.get()) * g,
      top: originY.get() + (cy - originY.get()) * g,
      width: TRASH_SIZE + (DIALOG_WIDTH - TRASH_SIZE) * g,
      height: TRASH_SIZE + (DIALOG_HEIGHT - TRASH_SIZE) * g,
      borderRadius: RADIUS_FROM + (RADIUS_TO - RADIUS_FROM) * Math.min(g, 1.1),
      opacity: Math.min(1, Math.max(0, g) * 3) * (1 - e),
      transform: [{ scale: 1 - (1 - CLOSE_SCALE) * e }],
    };
  });
  // The content keeps its own size and sits in the middle of the growing frame.
  const holderStyle = useAnimatedStyle(() => {
    const g = grow.get();
    return { left: -(DIALOG_WIDTH - (TRASH_SIZE + (DIALOG_WIDTH - TRASH_SIZE) * g)) / 2, top: -(DIALOG_HEIGHT - (TRASH_SIZE + (DIALOG_HEIGHT - TRASH_SIZE) * g)) / 2 };
  });
  const tileStyle = useAnimatedStyle(() => {
    const p = reducedMotion ? (content.get() > 0 ? 1 : 0) : backOut(stage(content.get(), 0, 0.4));
    return {
      backgroundColor: interpolateColor(done.get(), [0, 1], [RED_TILE, GREEN_TILE], 'RGB', { gamma: 1 }),
      opacity: Math.min(1, p * 2),
      transform: [{ scale: Math.max(p, 0) }, { rotate: `${shake.get()}deg` }],
    };
  });
  const rippleStyle = useAnimatedStyle(() => ({
    opacity: 0.45 * (1 - ripple.get()) * (ripple.get() > 0 ? 1 : 0),
    transform: [{ scale: 1 + ripple.get() * 0.9 }],
  }));
  const trashIconStyle = useAnimatedStyle(() => ({ opacity: 1 - done.get(), transform: [{ scale: 1 - done.get() * 0.4 }] }));
  const checkStyle = useAnimatedStyle(() => ({ opacity: Math.min(1, done.get() * 3) }));
  const checkEnd = useDerivedValue(() => draw.get());
  const titleStyle = useAnimatedStyle(() => {
    const p = stage(content.get(), 0.09, 0.4);
    return { opacity: reducedMotion ? (content.get() > 0 ? 1 : 0) : p, transform: [{ translateY: reducedMotion ? 0 : (1 - p) * 8 }] };
  });
  const bodyStyle = useAnimatedStyle(() => {
    const p = stage(content.get(), 0.18, 0.4);
    return { opacity: reducedMotion ? (content.get() > 0 ? 1 : 0) : p, transform: [{ translateY: reducedMotion ? 0 : (1 - p) * 8 }] };
  });
  const buttonsStyle = useAnimatedStyle(() => {
    const p = stage(content.get(), 0.27, 0.4);
    return { opacity: reducedMotion ? (content.get() > 0 ? 1 : 0) : p, transform: [{ translateY: reducedMotion ? 0 : (1 - p) * 8 }] };
  });
  const messageStyle = useAnimatedStyle(() => ({ opacity: 1 - done.get() }));
  const successTextStyle = useAnimatedStyle(() => ({ opacity: done.get() }));
  const cancelStyle = useAnimatedStyle(() => ({ width: BUTTON_WIDTH * (1 - done.get()), opacity: 1 - done.get() * 1.5 }));
  const confirmButtonStyle = useAnimatedStyle(() => ({
    left: (BUTTON_WIDTH + BUTTON_GAP) * (1 - done.get()),
    width: BUTTON_WIDTH + (FULL_WIDTH - BUTTON_WIDTH) * done.get(),
    backgroundColor: interpolateColor(done.get(), [0, 1], [RED, GREEN], 'RGB', { gamma: 1 }),
    transform: [{ scale: confirmPressed ? 0.96 : 1 }],
  }));
  const closeStyle = useAnimatedStyle(() => ({ opacity: 1 - done.get() }));

  return (
    <View style={styles.screen} onTouchStart={() => autoPlay && setTouches((n) => n + 1)}>
      <StatusBar style="dark" />
      <View style={[styles.page, { paddingTop: insets.top + 8 }]} accessibilityElementsHidden={target !== null}>
        <View style={styles.header}>
          <Text style={styles.title}>{title}</Text>
          {byHand && removed.length > 0 ? (
            <Pressable onPress={() => setRemoved([])} hitSlop={10} accessibilityRole="button">
              <Text style={styles.restore}>Restore files</Text>
            </Pressable>
          ) : (
            <View style={styles.headerButton}>
              <Ionicons name="add" size={24} color={INK} />
            </View>
          )}
        </View>
        <View style={styles.storage}>
          <View style={styles.storageTop}>
            <View>
              <Text style={styles.storageLabel}>Storage</Text>
              <Text style={styles.storageValue}>
                12.8 GB <Text style={styles.storageOf}>of 16 GB used</Text>
              </Text>
            </View>
            <View style={styles.storageIcon}>
              <Ionicons name="cloud" size={20} color="#3b6fe0" />
            </View>
          </View>
          <View style={styles.bar}>
            {STORAGE.map((part) => (
              <View key={part.label} style={{ flex: part.share, backgroundColor: part.color }} />
            ))}
            <View style={{ flex: 1 - STORAGE.reduce((sum, part) => sum + part.share, 0) }} />
          </View>
          <View style={styles.legend}>
            {STORAGE.map((part) => (
              <View key={part.label} style={styles.legendItem}>
                <View style={[styles.legendDot, { backgroundColor: part.color }]} />
                <Text style={styles.legendText}>{part.label}</Text>
              </View>
            ))}
          </View>
        </View>
        <View style={styles.sectionRow}>
          <Text style={styles.section}>Recent files</Text>
          <Text style={styles.seeAll}>See all</Text>
        </View>
        {files.map((file, index) => (
          <FileRow
            key={file.name}
            file={file}
            removed={removed.includes(index)}
            pressed={pressed === index}
            trashRef={(node) => {
              trashes.current[index] = node;
            }}
            onTrash={() => trash(index)}
          />
        ))}
      </View>

      <View style={[styles.tabBar, { paddingBottom: insets.bottom }]}>
        {TABS.map((tab, i) => (
          <View key={tab.label} style={styles.tab}>
            <Ionicons name={tab.icon} size={24} color={i === 0 ? '#3b6fe0' : '#9a9da5'} />
            <Text style={[styles.tabLabel, i === 0 && { color: '#3b6fe0' }]}>{tab.label}</Text>
          </View>
        ))}
      </View>

      <Animated.View style={[StyleSheet.absoluteFill, { backgroundColor: '#0b0d12' }, scrimStyle]} pointerEvents="none" />

      {target !== null && (
        <>
          <Pressable style={StyleSheet.absoluteFill} onPress={succeeded ? () => close(true) : fold} accessibilityLabel="Dismiss" />
          <Animated.View style={[styles.frame, frameStyle]}>
            <Animated.View style={[styles.holder, holderStyle]}>
              <View style={styles.tileSlot}>
                <Animated.View style={[styles.ripple, rippleStyle]} pointerEvents="none" />
                <Animated.View style={[styles.tile, tileStyle]}>
                  <Animated.View style={[StyleSheet.absoluteFill, styles.center, trashIconStyle]}>
                    <Ionicons name="trash-outline" size={27} color={RED} />
                  </Animated.View>
                  <Animated.View style={[StyleSheet.absoluteFill, styles.center, checkStyle]}>
                    <Canvas style={styles.check}>
                      <Circle cx={15} cy={15} r={12.5} style="stroke" strokeWidth={2.2} color={GREEN_INK} />
                      <Path path={CHECK} style="stroke" strokeWidth={2.4} strokeCap="round" strokeJoin="round" color={GREEN_INK} start={0} end={checkEnd} />
                    </Canvas>
                  </Animated.View>
                </Animated.View>
              </View>

              <Animated.View style={[styles.copy, titleStyle]}>
                <Animated.Text style={[styles.heading, messageStyle]}>{deleteTitle}</Animated.Text>
                <Animated.Text style={[styles.heading, styles.over, successTextStyle]}>{successTitle}</Animated.Text>
              </Animated.View>
              <Animated.View style={[styles.copy, styles.bodyTop, bodyStyle]}>
                <Animated.Text style={[styles.body, messageStyle]}>{deleteMessage}</Animated.Text>
                <Animated.Text style={[styles.body, styles.over, successTextStyle]}>{successMessage}</Animated.Text>
              </Animated.View>

              <Animated.View style={[styles.buttons, buttonsStyle]}>
                <Animated.View style={[styles.button, styles.cancel, cancelStyle]}>
                  <Pressable style={styles.hit} onPress={fold} disabled={succeeded} accessibilityRole="button">
                    <Text style={styles.cancelText} numberOfLines={1}>
                      {cancelLabel}
                    </Text>
                  </Pressable>
                </Animated.View>
                <Animated.View style={[styles.button, styles.confirm, confirmButtonStyle]}>
                  <Pressable style={styles.hit} onPress={confirmByHand} accessibilityRole="button">
                    <Text style={styles.confirmText} numberOfLines={1}>
                      {confirmLabel}
                    </Text>
                  </Pressable>
                </Animated.View>
              </Animated.View>

              <Animated.View style={[styles.close, closeStyle]} pointerEvents={succeeded ? 'none' : 'auto'}>
                <Pressable style={styles.hit} onPress={fold} hitSlop={8} accessibilityRole="button" accessibilityLabel="Close">
                  <Ionicons name="close" size={15} color="#ffffff" />
                </Pressable>
              </Animated.View>
            </Animated.View>
          </Animated.View>
        </>
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: PAGE },
  page: { flex: 1, paddingHorizontal: 20, backgroundColor: PAGE },
  header: { height: 48, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', marginBottom: 16 },
  title: { color: INK, fontSize: 34, lineHeight: 40, fontWeight: '800', letterSpacing: -1 },
  restore: { color: RED, fontSize: 15, lineHeight: 20, fontWeight: '600' },
  headerButton: {
    width: 40,
    height: 40,
    borderRadius: 20,
    backgroundColor: '#ffffff',
    alignItems: 'center',
    justifyContent: 'center',
    boxShadow: [{ offsetX: 0, offsetY: 2, blurRadius: 8, color: 'rgba(17,19,24,0.08)' }],
  },
  storage: {
    borderRadius: 22,
    borderCurve: 'continuous',
    backgroundColor: '#ffffff',
    padding: 18,
    boxShadow: [
      { offsetX: 0, offsetY: 1, blurRadius: 2, color: 'rgba(17,19,24,0.04)' },
      { offsetX: 0, offsetY: 6, blurRadius: 18, color: 'rgba(17,19,24,0.05)' },
    ],
  },
  storageTop: { flexDirection: 'row', alignItems: 'flex-start', justifyContent: 'space-between' },
  storageLabel: { color: MUTE, fontSize: 13, lineHeight: 17, fontWeight: '600' },
  storageValue: { color: INK, fontSize: 22, lineHeight: 28, fontWeight: '800', letterSpacing: -0.5, marginTop: 2 },
  storageOf: { color: MUTE, fontSize: 15, fontWeight: '500', letterSpacing: 0 },
  storageIcon: { width: 36, height: 36, borderRadius: 11, backgroundColor: '#e3ecfd', alignItems: 'center', justifyContent: 'center' },
  bar: { height: 8, borderRadius: 4, overflow: 'hidden', flexDirection: 'row', gap: 2, backgroundColor: '#eef0f3', marginTop: 14 },
  legend: { flexDirection: 'row', gap: 14, marginTop: 12 },
  legendItem: { flexDirection: 'row', alignItems: 'center', gap: 6 },
  legendDot: { width: 8, height: 8, borderRadius: 4 },
  legendText: { color: MUTE, fontSize: 12, lineHeight: 16, fontWeight: '500' },
  sectionRow: { flexDirection: 'row', alignItems: 'baseline', justifyContent: 'space-between', marginTop: 24, marginBottom: 12 },
  section: { color: INK, fontSize: 20, lineHeight: 25, fontWeight: '800', letterSpacing: -0.4 },
  seeAll: { color: '#3b6fe0', fontSize: 15, lineHeight: 20, fontWeight: '600' },
  tabBar: {
    position: 'absolute',
    left: 0,
    right: 0,
    bottom: 0,
    flexDirection: 'row',
    paddingTop: 8,
    backgroundColor: 'rgba(255,255,255,0.96)',
    borderTopWidth: StyleSheet.hairlineWidth,
    borderTopColor: 'rgba(60,60,67,0.2)',
  },
  tab: { flex: 1, alignItems: 'center', gap: 2 },
  tabLabel: { color: '#9a9da5', fontSize: 11, lineHeight: 14, fontWeight: '600' },

  frame: {
    position: 'absolute',
    overflow: 'hidden',
    backgroundColor: '#ffffff',
    borderCurve: 'continuous',
    boxShadow: [
      { offsetX: 0, offsetY: 4, blurRadius: 10, color: 'rgba(11,13,18,0.10)' },
      { offsetX: 0, offsetY: 24, blurRadius: 56, color: 'rgba(11,13,18,0.28)' },
    ],
  },
  holder: { position: 'absolute', width: DIALOG_WIDTH, height: DIALOG_HEIGHT },
  tileSlot: { position: 'absolute', top: 28, left: (DIALOG_WIDTH - TILE) / 2, width: TILE, height: TILE },
  tile: { width: TILE, height: TILE, borderRadius: 18, borderCurve: 'continuous' },
  ripple: { position: 'absolute', width: TILE, height: TILE, borderRadius: 18, borderWidth: 2, borderColor: GREEN_INK },
  center: { alignItems: 'center', justifyContent: 'center' },
  check: { width: 30, height: 30 },
  copy: { position: 'absolute', top: 102, left: SIDE, right: SIDE, height: 26, alignItems: 'center' },
  bodyTop: { top: 134, height: 21 },
  heading: { color: INK, fontSize: 21, lineHeight: 26, fontWeight: '700', letterSpacing: -0.4, textAlign: 'center' },
  body: { color: MUTE, fontSize: 15, lineHeight: 21, textAlign: 'center' },
  over: { position: 'absolute', left: 0, right: 0, top: 0 },
  buttons: { position: 'absolute', top: 176, left: SIDE, width: FULL_WIDTH, height: BUTTON_HEIGHT },
  button: { position: 'absolute', top: 0, height: BUTTON_HEIGHT, borderRadius: 15, borderCurve: 'continuous', overflow: 'hidden' },
  cancel: { left: 0, backgroundColor: '#f2f3f5' },
  confirm: { backgroundColor: RED },
  hit: { flex: 1, alignItems: 'center', justifyContent: 'center' },
  cancelText: { color: INK, fontSize: 16, lineHeight: 21, fontWeight: '600', letterSpacing: -0.2 },
  confirmText: { color: '#ffffff', fontSize: 16, lineHeight: 21, fontWeight: '600', letterSpacing: -0.2 },
  close: { position: 'absolute', top: 14, right: 14, width: 26, height: 26, borderRadius: 13, backgroundColor: '#5f636b', overflow: 'hidden' },
});
