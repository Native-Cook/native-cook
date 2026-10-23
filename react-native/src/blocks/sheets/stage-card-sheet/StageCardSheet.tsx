import { Ionicons } from '@expo/vector-icons';
import { LinearGradient } from 'expo-linear-gradient';
import { StatusBar } from 'expo-status-bar';
import { type ComponentProps, type ReactNode, useEffect, useRef, useState } from 'react';
import { Platform, Pressable, StyleSheet, Text, View, useWindowDimensions } from 'react-native';
import Animated, {
  Easing,
  ReduceMotion,
  cancelAnimation,
  interpolate,
  interpolateColor,
  useAnimatedProps,
  useAnimatedStyle,
  useReducedMotion,
  useSharedValue,
  withRepeat,
  withSpring,
  withDelay,
  withTiming,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import Svg, { ClipPath, Defs, G, Line, LinearGradient as SvgGradient, Path, Rect, Stop } from 'react-native-svg';

import { useAutoPlay } from './useAutoPlay';

type Permission = { icon: ComponentProps<typeof Ionicons>['name']; title: string; caption: string };

type Props = {
  title?: string;
  caption?: string;
  enableLabel?: string;
  footnote?: string;
  /** Written under "Reset link". */
  generatedOn?: string;
  permissions?: Permission[];
  /** Opens the sheet and closes it again by itself every LOOP seconds. */
  autoPlay?: boolean;
  /** Called when the sheet has opened, i.e. the link exists. */
  onEnable?: () => void;
  /** Called when "Continue" in the dialog makes the project private. */
  onMakePrivate?: () => void;
  onPermissionChange?: (index: number, on: boolean) => void;
};

/** Seconds for one pass of the autoPlay loop. */
export const LOOP = 9;
// The timeline, in seconds from the start of a loop.
const TAP_AT = 0.9;
const DIALOG_AT = 4.2;
const CONTINUE_PRESS_AT = 5.5;
const CONFIRM_AT = 5.75;

const PERMISSIONS: Permission[] = [
  { icon: 'people-outline', title: 'Allow collaboration', caption: 'Anyone with the link can edit and add tracks' },
  { icon: 'download-outline', title: 'Allow downloads', caption: 'Anyone with the link can download audio' },
  { icon: 'person-circle-outline', title: 'Require account', caption: 'Only people with an account will be able to listen' },
];

const PAGE = '#fdfcfd';
const BACKDROP = '#000000';
const BEHIND = '#c9c8cd';
const INK = '#111113';
const BODY = '#3c3c43';
const MUTE = '#7c7c83';
const FAINT = '#9a9aa0';
const WELL = '#f2f2f4';
const LINE = 'rgba(60,60,67,0.12)';
const DANGER = '#e5252a';
const ACCENT = '#2b7be8';
const ON = '#34c759';
const ART_COLORS = ['#f9a8dc', '#ee72d6', '#d43be0', '#b81ad2'] as const;
const GLOW = 'rgba(226,72,240,0.42)';
const MONO = Platform.select({ ios: 'Menlo', default: 'monospace' });

const MARGIN = 20;
const CLOSE = 36;
// The page is a sheet over a darkened screen, with the page it came from peeking out behind it.
const SHEET_GAP = 10;
const SHEET_RADIUS = 14;
// The artwork as a hero, and as the small cover above the panel (from the sheet's top).
const ART_CLOSED_MAX = 330;
const ART_OPEN = 120;
const ART_RADIUS_CLOSED = 22;
const ART_RADIUS_OPEN = 14;
const ART_TOP_CLOSED = 104;
const ART_TOP_OPEN = 24;
const TITLE_GAP = 24;
const TITLE_GAP_OPEN = 14;
const TITLE_HEIGHT = 32;
const CAPTION_HEIGHT = 20;
// The title shrinks with the artwork, from its top edge.
const TITLE_SCALE_OPEN = 0.78;
const PANEL_GAP = 20;
const BUTTON_HEIGHT = 52;
const FOOTNOTE_HEIGHT = 32;
const ROW_HEIGHT = 64;
const SWITCH_WIDTH = 51;
const SWITCH_HEIGHT = 31;
const DIALOG_WIDTH = 270;

// One value moves the artwork and reveals the panel, so they read as one object.
const SPRING = { damping: 19, stiffness: 130, mass: 1, reduceMotion: ReduceMotion.Never };
const DIALOG_SPRING = { damping: 17, stiffness: 260, mass: 1, reduceMotion: ReduceMotion.Never };
const FADE = 180;
// Seconds the spinner shows before the sheet opens.
const LOADING = 0.7;
const BUSY_IN = 160;
const SPIN = 700;
// Rows of the panel rise one after another.
const ROWS = 8;
const ROW_IN = 450;
const ROW_STAGGER = 60;
const ROW_RISE = 40;
const PANEL_IN = ROW_IN + ROW_STAGGER * ROWS;
const PANEL_OUT = 200;
// The artwork waits for the panel to clear before it grows back.
const GROW_DELAY = 110;
const STREAK_WIPE = 520;
const REST_FADE = 350;

// Rays of light across the artwork, fanning out from its lower left, in a 100 x 100 box:
// x1, y1, x2, y2, width, peak opacity.
const STREAKS = [
  [-6, 80, 104, 30, 1.5, 0.95],
  [6, 76, 102, 46, 0.7, 0.9],
  [14, 84, 98, 56, 0.5, 0.8],
  [0, 72, 90, 22, 0.45, 0.85],
  [22, 70, 106, 38, 1.0, 0.9],
  [-8, 88, 80, 64, 1.1, 0.75],
  [30, 64, 100, 26, 0.4, 0.8],
  [10, 92, 94, 72, 0.45, 0.6],
  [42, 60, 106, 18, 0.65, 0.85],
  [16, 80, 74, 58, 3.2, 0.28],
  [-4, 74, 96, 36, 5, 0.16],
];
// Four-pointed glints near the rays' ends: x, y, size.
const GLINTS = [
  [80, 20, 3.2],
  [66, 31, 1.8],
  [90, 46, 1.4],
];
const glint = (x: number, y: number, r: number) => {
  const k = r * 0.22;
  return `M${x} ${y - r}L${x + k} ${y - k}L${x + r} ${y}L${x + k} ${y + k}L${x} ${y + r}L${x - k} ${y + k}L${x - r} ${y}L${x - k} ${y - k}Z`;
};

const AnimatedRect = Animated.createAnimatedComponent(Rect);

function Reveal({ index, panel, children }: { index: number; panel: { get(): number }; children: ReactNode }) {
  const style = useAnimatedStyle(() => {
    const t = Math.min(1, Math.max(0, (panel.get() * PANEL_IN - index * ROW_STAGGER) / ROW_IN));
    return { opacity: t, transform: [{ translateY: (1 - t) * ROW_RISE }] };
  });
  return <Animated.View style={style}>{children}</Animated.View>;
}

function Toggle({ on, onPress }: { on: boolean; onPress: () => void }) {
  return (
    <Pressable onPress={onPress} accessibilityRole="switch" accessibilityState={{ checked: on }} hitSlop={8}>
      <View style={[styles.switch, { backgroundColor: on ? ON : '#e9e9eb' }]}>
        <Animated.View style={[styles.knob, { transform: [{ translateX: on ? SWITCH_WIDTH - SWITCH_HEIGHT : 0 }], transitionProperty: 'transform', transitionDuration: 200 }]} />
      </View>
    </Pressable>
  );
}

/**
 * A project page whose artwork is a hero until "Enable link sharing" is pressed: then it shrinks
 * into a cover with a streak of light across it while the sharing panel rises below. "Make project
 * private" asks first in an alert, and Continue folds everything back. The autoPlay loop presses
 * the same buttons; nothing is shared or deleted here.
 */
export function StageCardSheet({
  title = 'First',
  caption = '60fps · 1 track',
  enableLabel = 'Enable link sharing',
  footnote = 'This will create a shareable link. Anyone with the link will be able to listen and share this project.',
  generatedOn = 'September 24, 2024',
  permissions = PERMISSIONS,
  autoPlay = false,
  onEnable,
  onMakePrivate,
  onPermissionChange,
}: Props) {
  const reducedMotion = useReducedMotion();
  const insets = useSafeAreaInsets();
  const { width, height } = useWindowDimensions();
  const [stage, setStage] = useState<'closed' | 'loading' | 'open'>('closed');
  const [dialog, setDialog] = useState(false);
  const [autoPressed, setAutoPressed] = useState(false);
  const [on, setOn] = useState<boolean[]>(() => permissions.map(() => false));

  // 0 closed, 1 open: the artwork's size and the panel come from it.
  const sheet = useSharedValue(0);
  const panel = useSharedValue(0);
  const streak = useSharedValue(0);
  const busy = useSharedValue(0);
  const spin = useSharedValue(0);
  const alert = useSharedValue(0);
  const pending = useRef<ReturnType<typeof setTimeout> | null>(null);

  const sheetTop = insets.top + SHEET_GAP;
  const sheetHeight = height - sheetTop;
  const artClosed = Math.min(ART_CLOSED_MAX, width - 2 * (MARGIN + 16));
  // Centre the hero in the room between the close button and the button at the bottom.
  const artTopClosed = Math.max(64, Math.min(ART_TOP_CLOSED, (sheetHeight - artClosed - 260) / 2));
  const artTopOpen = ART_TOP_OPEN;
  const titleTopOpen = artTopOpen + ART_OPEN + TITLE_GAP_OPEN;
  const panelTop = titleTopOpen + (TITLE_HEIGHT + 2 + CAPTION_HEIGHT) * TITLE_SCALE_OPEN + PANEL_GAP;
  const bottom = Math.max(insets.bottom, 16);

  const move = (value: { set(v: unknown): void }, to: number, spring = SPRING) =>
    value.set(reducedMotion ? withTiming(to, { duration: FADE, reduceMotion: ReduceMotion.Never }) : withSpring(to, spring));

  const open = () => {
    pending.current = null;
    setStage('open');
    cancelAnimation(spin);
    spin.set(0);
    move(sheet, 1);
    panel.set(withTiming(1, { duration: reducedMotion ? FADE : PANEL_IN, easing: Easing.out(Easing.quad), reduceMotion: ReduceMotion.Never }));
    streak.set(withTiming(1, { duration: reducedMotion ? 0 : STREAK_WIPE, easing: Easing.out(Easing.cubic), reduceMotion: ReduceMotion.Never }));
    onEnable?.();
  };

  const enable = () => {
    if (stage !== 'closed') return;
    setStage('loading');
    busy.set(withTiming(1, { duration: BUSY_IN, reduceMotion: ReduceMotion.Never }));
    spin.set(withRepeat(withTiming(1, { duration: SPIN, easing: Easing.linear, reduceMotion: ReduceMotion.Never }), -1));
    pending.current = setTimeout(open, LOADING * 1000);
  };

  const closeDialog = () => {
    setDialog(false);
    setAutoPressed(false);
    move(alert, 0, DIALOG_SPRING);
  };

  const makePrivate = () => {
    closeDialog();
    setStage('closed');
    sheet.set(
      reducedMotion
        ? withTiming(0, { duration: FADE, reduceMotion: ReduceMotion.Never })
        : withDelay(GROW_DELAY, withSpring(0, SPRING)),
    );
    panel.set(withTiming(0, { duration: reducedMotion ? FADE : PANEL_OUT, easing: Easing.in(Easing.quad), reduceMotion: ReduceMotion.Never }));
    streak.set(withTiming(0, { duration: FADE, reduceMotion: ReduceMotion.Never }));
    busy.set(withTiming(0, { duration: reducedMotion ? FADE : 500, reduceMotion: ReduceMotion.Never }));
    onMakePrivate?.();
  };

  const askPrivate = () => {
    setDialog(true);
    move(alert, 1, DIALOG_SPRING);
  };

  const rest = () => {
    if (pending.current) clearTimeout(pending.current);
    pending.current = null;
    cancelAnimation(spin);
    spin.set(0);
    setStage('closed');
    setDialog(false);
    setAutoPressed(false);
    setOn(permissions.map(() => false));
    for (const value of [sheet, panel, streak, busy, alert]) value.set(withTiming(0, { duration: REST_FADE, reduceMotion: ReduceMotion.Never }));
  };

  useEffect(
    () => () => {
      if (pending.current) clearTimeout(pending.current);
    },
    [],
  );

  const touched = useAutoPlay(
    autoPlay,
    LOOP,
    [
      [TAP_AT, enable],
      [DIALOG_AT, askPrivate],
      [CONTINUE_PRESS_AT, () => setAutoPressed(true)],
      [CONFIRM_AT, makePrivate],
    ],
    rest,
  );

  const artStyle = useAnimatedStyle(() => {
    const p = sheet.get();
    const size = interpolate(p, [0, 1], [artClosed, ART_OPEN]);
    return {
      width: size,
      height: size,
      left: (width - size) / 2,
      top: interpolate(p, [0, 1], [artTopClosed, artTopOpen]),
      borderRadius: interpolate(p, [0, 1], [ART_RADIUS_CLOSED, ART_RADIUS_OPEN]),
    };
  });
  const artClipStyle = useAnimatedStyle(() => ({
    borderRadius: interpolate(sheet.get(), [0, 1], [ART_RADIUS_CLOSED, ART_RADIUS_OPEN]),
  }));
  const titleStyle = useAnimatedStyle(() => {
    const p = sheet.get();
    return {
      top: interpolate(p, [0, 1], [artTopClosed + artClosed + TITLE_GAP, titleTopOpen]),
      transform: [{ scale: interpolate(p, [0, 1], [1, TITLE_SCALE_OPEN]) }],
    };
  });
  const flashStyle = useAnimatedStyle(() => ({ opacity: 0.32 * Math.sin(Math.PI * streak.get()) }));
  const streakProps = useAnimatedProps(() => ({ width: streak.get() * 116 }));
  const dockStyle = useAnimatedStyle(() => {
    const p = Math.min(1, sheet.get() * 2);
    return { opacity: 1 - p, transform: [{ translateY: p * ROW_RISE }] };
  });
  const buttonStyle = useAnimatedStyle(() => ({
    backgroundColor: interpolateColor(sheet.get(), [0, 0.4], [INK, '#d6d6db'], 'RGB', { gamma: 1 }),
  }));
  const labelStyle = useAnimatedStyle(() => ({ opacity: 1 - busy.get() }));
  const spinnerStyle = useAnimatedStyle(() => ({
    opacity: busy.get(),
    transform: [{ rotate: `${spin.get() * 360}deg` }],
  }));
  const scrimStyle = useAnimatedStyle(() => ({ opacity: alert.get() }));
  const dialogStyle = useAnimatedStyle(() => ({
    opacity: Math.min(1, alert.get() * 1.4),
    transform: [{ scale: 1.12 - 0.12 * alert.get() }],
  }));

  const toggle = (index: number) => {
    const next = !on[index];
    setOn((list) => list.map((value, at) => (at === index ? next : value)));
    onPermissionChange?.(index, next);
  };

  const isOpen = stage === 'open';

  return (
    <View style={styles.screen} onTouchStart={touched}>
      <StatusBar style="light" />
      <View style={[styles.behind, { top: insets.top }]} />

      <View style={[styles.sheet, { top: sheetTop }]}>
        <LinearGradient colors={['rgba(232,96,246,0.10)', 'rgba(232,96,246,0)']} style={[styles.glowPage, { height: sheetHeight * 0.6 }]} pointerEvents="none" />

        <View style={styles.close}>
          <Ionicons name="close" size={20} color={INK} />
        </View>

        {/* Under the artwork, so the rows rise from behind the shrinking cover. */}
        <View style={[styles.panel, { top: panelTop }]} pointerEvents={isOpen ? 'auto' : 'none'}>
          <Reveal index={0} panel={panel}>
            <View style={styles.pair}>
              <View style={styles.action}>
                <Ionicons name="copy-outline" size={18} color={INK} />
                <Text style={styles.actionLabel}>Copy link</Text>
              </View>
              <View style={styles.action}>
                <Ionicons name="share-outline" size={18} color={INK} />
                <Text style={styles.actionLabel}>Share</Text>
              </View>
            </View>
          </Reveal>
          <Reveal index={1} panel={panel}>
            <View style={styles.resetRow}>
              <Ionicons name="refresh" size={15} color={BODY} />
              <Text style={styles.reset}>Reset link</Text>
            </View>
          </Reveal>
          <Reveal index={2} panel={panel}>
            <Text style={styles.generated}>Last generated on {generatedOn}</Text>
          </Reveal>
          <Reveal index={3} panel={panel}>
            <Pressable style={styles.private} onPress={askPrivate} hitSlop={8} accessibilityRole="button">
              <Ionicons name="lock-closed" size={13} color={DANGER} />
              <Text style={styles.privateLabel}>Make project private</Text>
            </Pressable>
          </Reveal>
          <Reveal index={4} panel={panel}>
            <Text style={styles.heading}>Permissions</Text>
          </Reveal>
          {permissions.map((permission, index) => (
            <Reveal key={permission.title} index={5 + index} panel={panel}>
              <View style={[styles.permission, index > 0 && styles.permissionLine]}>
                <View style={styles.permissionIcon}>
                  <Ionicons name={permission.icon} size={18} color={BODY} />
                </View>
                <View style={styles.permissionText}>
                  <Text style={styles.permissionTitle}>{permission.title}</Text>
                  <Text style={styles.permissionCaption} numberOfLines={2}>
                    {permission.caption}
                  </Text>
                </View>
                <Toggle on={on[index]} onPress={() => toggle(index)} />
              </View>
            </Reveal>
          ))}
        </View>

        <Animated.View style={[styles.art, artStyle]}>
          <Animated.View style={[StyleSheet.absoluteFill, styles.artClip, artClipStyle]}>
            <LinearGradient colors={ART_COLORS} locations={[0, 0.38, 0.74, 1]} start={{ x: 0, y: 0 }} end={{ x: 1, y: 1 }} style={StyleSheet.absoluteFill} />
            {/* Soft light from the upper left and a little weight in the lower right. */}
            <LinearGradient colors={['rgba(255,255,255,0.42)', 'rgba(255,255,255,0)']} start={{ x: 0, y: 0 }} end={{ x: 0.55, y: 0.6 }} style={StyleSheet.absoluteFill} />
            <LinearGradient colors={['rgba(120,0,150,0)', 'rgba(120,0,150,0.22)']} start={{ x: 0.45, y: 0.45 }} end={{ x: 1, y: 1 }} style={StyleSheet.absoluteFill} />
            <Svg style={StyleSheet.absoluteFill} viewBox="0 0 100 100" preserveAspectRatio="none">
              <Defs>
                <ClipPath id="wipe">
                  <AnimatedRect x={-8} y={0} height={100} animatedProps={streakProps} />
                </ClipPath>
                {STREAKS.map(([x1, y1, x2, y2, , peak], index) => (
                  <SvgGradient key={`g${index}`} id={`ray${index}`} gradientUnits="userSpaceOnUse" x1={x1} y1={y1} x2={x2} y2={y2}>
                    <Stop offset={0} stopColor="#ffffff" stopOpacity={0} />
                    <Stop offset={0.62} stopColor="#ffffff" stopOpacity={peak} />
                    <Stop offset={1} stopColor="#ffffff" stopOpacity={0} />
                  </SvgGradient>
                ))}
              </Defs>
              <G clipPath="url(#wipe)">
                {STREAKS.map(([x1, y1, x2, y2, w], index) => (
                  <Line key={`r${index}`} x1={x1} y1={y1} x2={x2} y2={y2} stroke={`url(#ray${index})`} strokeWidth={w} strokeLinecap="round" />
                ))}
                {GLINTS.map(([x, y, r]) => (
                  <Path key={`s${x}`} d={glint(x, y, r)} fill="#ffffff" fillOpacity={0.9} />
                ))}
              </G>
            </Svg>
            <Animated.View style={[StyleSheet.absoluteFill, styles.flash, flashStyle]} pointerEvents="none" />
          </Animated.View>
          <Animated.View style={[StyleSheet.absoluteFill, styles.artEdge, artClipStyle]} pointerEvents="none" />
        </Animated.View>

        <Animated.View style={[styles.titleBlock, titleStyle]} pointerEvents="none">
          <Text style={styles.title}>{title}</Text>
          <View style={styles.captionRow}>
            <View style={styles.avatar}>
              <Ionicons name="lock-closed" size={9} color="#ffffff" />
            </View>
            <Text style={styles.caption}>{caption}</Text>
          </View>
        </Animated.View>

        <Animated.View style={[styles.dock, { bottom, paddingHorizontal: MARGIN }, dockStyle]} pointerEvents={stage === 'closed' ? 'auto' : 'none'}>
          <Pressable onPress={enable} accessibilityRole="button" accessibilityLabel={enableLabel}>
            <Animated.View style={[styles.enable, buttonStyle]}>
              <Animated.View style={[styles.enableContent, labelStyle]}>
                <Ionicons name="link" size={18} color="#ffffff" />
                <Text style={styles.enableLabel}>{enableLabel}</Text>
              </Animated.View>
              <Animated.View style={[styles.spinner, spinnerStyle]} />
            </Animated.View>
          </Pressable>
          <Text style={styles.footnote}>{footnote}</Text>
        </Animated.View>
      </View>

      <View style={StyleSheet.absoluteFill} pointerEvents={dialog ? 'auto' : 'none'}>
        <Animated.View style={[StyleSheet.absoluteFill, styles.scrim, scrimStyle]} />
        <View style={styles.dialogSlot} pointerEvents="box-none">
          <Animated.View style={[styles.dialog, dialogStyle]}>
            <View style={styles.dialogBody}>
              <Text style={styles.dialogTitle}>Make project private?</Text>
              <Text style={styles.dialogText}>This will disable the link and remove everyone from the project.</Text>
            </View>
            <View style={styles.dialogButtons}>
              <Pressable style={styles.dialogButton} onPress={closeDialog} accessibilityRole="button">
                <Text style={[styles.dialogButtonLabel, { color: ACCENT }]}>Cancel</Text>
              </Pressable>
              <View style={styles.hairline} />
              <Pressable
                style={[styles.dialogButton, autoPressed && styles.dialogPressed]}
                onPress={makePrivate}
                accessibilityRole="button"
              >
                {({ pressed }) => (
                  <View style={[StyleSheet.absoluteFill, styles.dialogButton, pressed && styles.dialogPressed]}>
                    <Text style={[styles.dialogButtonLabel, { color: DANGER, fontWeight: '600' }]}>Continue</Text>
                  </View>
                )}
              </Pressable>
            </View>
          </Animated.View>
        </View>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: BACKDROP },
  behind: {
    position: 'absolute',
    left: 18,
    right: 18,
    height: 40,
    borderRadius: SHEET_RADIUS,
    borderCurve: 'continuous',
    backgroundColor: BEHIND,
  },
  sheet: {
    position: 'absolute',
    left: 0,
    right: 0,
    bottom: 0,
    backgroundColor: PAGE,
    borderTopLeftRadius: SHEET_RADIUS,
    borderTopRightRadius: SHEET_RADIUS,
    borderCurve: 'continuous',
    overflow: 'hidden',
  },
  glowPage: { position: 'absolute', left: 0, right: 0, top: 0 },
  close: {
    position: 'absolute',
    top: 16,
    right: 16,
    width: CLOSE,
    height: CLOSE,
    borderRadius: 12,
    borderCurve: 'continuous',
    backgroundColor: WELL,
    alignItems: 'center',
    justifyContent: 'center',
  },
  art: {
    position: 'absolute',
    borderCurve: 'continuous',
    boxShadow: [
      { offsetX: 0, offsetY: 24, blurRadius: 72, spreadDistance: 6, color: GLOW },
      { offsetX: 0, offsetY: 4, blurRadius: 10, color: 'rgba(110,0,130,0.16)' },
    ],
  },
  artClip: { overflow: 'hidden', borderCurve: 'continuous' },
  artEdge: { borderCurve: 'continuous', borderWidth: StyleSheet.hairlineWidth * 2, borderColor: 'rgba(255,255,255,0.28)' },
  flash: { backgroundColor: '#ffffff' },
  titleBlock: { position: 'absolute', left: 0, right: 0, alignItems: 'center', transformOrigin: 'top' },
  title: { color: INK, fontSize: 28, lineHeight: TITLE_HEIGHT, fontWeight: '700', letterSpacing: -0.6 },
  captionRow: { marginTop: 2, height: CAPTION_HEIGHT, flexDirection: 'row', alignItems: 'center', gap: 6 },
  avatar: { width: 16, height: 16, borderRadius: 8, backgroundColor: '#8e8e93', alignItems: 'center', justifyContent: 'center' },
  caption: { color: MUTE, fontSize: 15, lineHeight: 20, fontWeight: '500' },
  panel: { position: 'absolute', left: MARGIN, right: MARGIN },
  pair: { flexDirection: 'row', gap: 10 },
  action: {
    flex: 1,
    height: 48,
    borderRadius: 14,
    borderCurve: 'continuous',
    backgroundColor: WELL,
    alignItems: 'center',
    justifyContent: 'center',
    gap: 2,
  },
  actionLabel: { color: INK, fontSize: 13, lineHeight: 16, fontWeight: '600' },
  resetRow: { marginTop: 18, flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: 6 },
  reset: { color: BODY, fontSize: 14, lineHeight: 20, fontFamily: MONO },
  generated: { marginTop: 4, textAlign: 'center', color: FAINT, fontSize: 12, lineHeight: 16 },
  private: { marginTop: 14, flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: 6 },
  privateLabel: { color: DANGER, fontSize: 14, lineHeight: 20, fontFamily: MONO },
  heading: { marginTop: 24, marginBottom: 2, color: FAINT, fontSize: 12, lineHeight: 16, fontFamily: MONO, letterSpacing: 0.4 },
  permission: { height: ROW_HEIGHT, flexDirection: 'row', alignItems: 'center', gap: 12 },
  permissionLine: { borderTopWidth: StyleSheet.hairlineWidth, borderTopColor: LINE },
  permissionIcon: {
    width: 36,
    height: 36,
    borderRadius: 10,
    borderCurve: 'continuous',
    backgroundColor: WELL,
    alignItems: 'center',
    justifyContent: 'center',
  },
  permissionText: { flex: 1 },
  permissionTitle: { color: INK, fontSize: 15, lineHeight: 20, fontWeight: '600', letterSpacing: -0.2 },
  permissionCaption: { color: MUTE, fontSize: 13, lineHeight: 17 },
  switch: { width: SWITCH_WIDTH, height: SWITCH_HEIGHT, borderRadius: SWITCH_HEIGHT / 2, padding: 2 },
  knob: {
    width: SWITCH_HEIGHT - 4,
    height: SWITCH_HEIGHT - 4,
    borderRadius: (SWITCH_HEIGHT - 4) / 2,
    backgroundColor: '#ffffff',
    boxShadow: [
      { offsetX: 0, offsetY: 3, blurRadius: 8, color: 'rgba(0,0,0,0.15)' },
      { offsetX: 0, offsetY: 1, blurRadius: 1, color: 'rgba(0,0,0,0.06)' },
    ],
  },
  dock: { position: 'absolute', left: 0, right: 0, gap: 12 },
  enable: {
    height: BUTTON_HEIGHT,
    borderRadius: BUTTON_HEIGHT / 2,
    alignItems: 'center',
    justifyContent: 'center',
    boxShadow: [{ offsetX: 0, offsetY: 6, blurRadius: 16, color: 'rgba(17,17,19,0.18)' }],
  },
  enableContent: { flexDirection: 'row', alignItems: 'center', gap: 8 },
  enableLabel: { color: '#ffffff', fontSize: 16, lineHeight: 20, fontWeight: '600', letterSpacing: -0.2 },
  spinner: {
    position: 'absolute',
    width: 20,
    height: 20,
    borderRadius: 10,
    borderWidth: 2,
    borderColor: 'rgba(255,255,255,0.25)',
    borderTopColor: '#ffffff',
  },
  footnote: { height: FOOTNOTE_HEIGHT, textAlign: 'center', color: FAINT, fontSize: 12, lineHeight: 16, paddingHorizontal: 12 },
  scrim: { backgroundColor: 'rgba(0,0,0,0.32)' },
  dialogSlot: { position: 'absolute', left: 0, right: 0, top: 0, bottom: 0, alignItems: 'center', justifyContent: 'center' },
  dialog: {
    width: DIALOG_WIDTH,
    borderRadius: 16,
    borderCurve: 'continuous',
    backgroundColor: 'rgba(250,250,252,0.97)',
    boxShadow: [
      { offsetX: 0, offsetY: 20, blurRadius: 48, color: 'rgba(0,0,0,0.22)' },
      { offsetX: 0, offsetY: 2, blurRadius: 6, color: 'rgba(0,0,0,0.08)' },
    ],
  },
  dialogBody: { paddingHorizontal: 20, paddingTop: 20, paddingBottom: 18, alignItems: 'center', gap: 4 },
  dialogTitle: { color: INK, fontSize: 17, lineHeight: 22, fontWeight: '600', textAlign: 'center', letterSpacing: -0.3 },
  dialogText: { color: BODY, fontSize: 13, lineHeight: 18, textAlign: 'center' },
  dialogButtons: {
    height: 46,
    flexDirection: 'row',
    borderTopWidth: StyleSheet.hairlineWidth,
    borderTopColor: 'rgba(60,60,67,0.3)',
    borderBottomLeftRadius: 16,
    borderBottomRightRadius: 16,
    overflow: 'hidden',
  },
  dialogButton: { flex: 1, alignItems: 'center', justifyContent: 'center' },
  dialogPressed: { backgroundColor: 'rgba(0,0,0,0.08)' },
  dialogButtonLabel: { fontSize: 17, lineHeight: 22 },
  hairline: { width: StyleSheet.hairlineWidth, backgroundColor: 'rgba(60,60,67,0.3)' },
});
