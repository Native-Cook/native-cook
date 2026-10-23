import { Ionicons } from '@expo/vector-icons';
import { Canvas, Fill, Shader, Skia } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { LinearGradient } from 'expo-linear-gradient';
import { StatusBar } from 'expo-status-bar';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
  AccessibilityInfo,
  AppState,
  Platform,
  Pressable,
  StyleSheet,
  Text,
  useWindowDimensions,
  View,
} from 'react-native';
import {
  Gesture,
  GestureDetector,
  GestureHandlerRootView,
  ScrollView,
} from 'react-native-gesture-handler';
import Animated, {
  Easing,
  type FrameInfo,
  useAnimatedStyle,
  useDerivedValue,
  useFrameCallback,
  useReducedMotion,
  useSharedValue,
  withSpring,
  withTiming,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import Svg, { Path as SvgPath } from 'react-native-svg';
import { runOnUIAsync, scheduleOnRN, scheduleOnUI } from 'react-native-worklets';

import { PAGE_SKSL, SHEET_SKSL, ambientAngle, spillUniform, stepAmbientFollower } from './ambient';
import { EventPass } from './EventPass';
import { GlassSurface } from './Glass';
import { EventPassExportSvg, PosterSvg } from './PosterSvg';
import { link, pass, sheet, shareButton, stage } from './theme';
import {
  CLOCK_ANIMATION,
  CLOCK_STOPPED,
  LOOP,
  PHASE_SETTLING,
  SHEET_DISMISS,
  advanceLogicalClock,
  beginInteraction,
  cancelInteraction,
  confirmCopy,
  createMotionState,
  demoFrame,
  endInteraction,
  sampleMotion,
  setAutoPlay,
  setForeground,
  setQuiet,
  setSharePresented,
  setVisible,
  toggleFace,
  rubberBand,
  updateInteraction,
  type ClockCadence,
  type EventPassFrame,
  type EventPassMotionState,
} from './motion';

export { LOOP };

export type EventPassShareProps = {
  autoPlay?: boolean;
  /** Must resolve only after the host has copied the local event details. */
  onCopyDetails: () => Promise<void>;
  /** Must resolve after the host share presentation/action has completed or been dismissed. */
  onShareEvent: () => Promise<void>;
  /** Receives raw PNG base64 rendered from the local SVG composition. */
  onExportImage: (pngBase64: string) => Promise<void>;
};

type SemanticState = {
  cadence: ClockCadence;
  backVisible: boolean;
  copyConfirmed: boolean;
};

type ActionKind = 'copy' | 'share' | 'export';

const CHARCOAL = '#222122';
/** The light field behind the page and the sheet (`ambient.ts`); null → the flat greys of round 2. */
const PAGE_EFFECT = Skia.RuntimeEffect.Make(PAGE_SKSL);
const SHEET_EFFECT = Skia.RuntimeEffect.Make(SHEET_SKSL);
const P3 = Platform.OS === 'ios' ? 1 : 0;
/** Extra canvas below the sheet's content so an upward rubber-band never shows its end. */
const SHEET_OVERDRAW = 120;
const ACTION_COLOR = 'rgba(255,255,255,0.50)';

/** The pin's two stage actions are 15 pt line icons, smaller than Ionicons' 20 pt glyphs. */
function StageIcon({ kind, size }: { kind: 'copy' | 'download'; size: number }) {
  return (
    <Svg width={size} height={size} viewBox="0 0 15 15" fill="none" accessible={false}>
      {kind === 'copy' ? (
        <>
          <SvgPath d="M3.4 9.6H3.2A1.7 1.7 0 0 1 1.5 7.9V3.2A1.7 1.7 0 0 1 3.2 1.5H7.9A1.7 1.7 0 0 1 9.6 3.2V3.4" stroke={ACTION_COLOR} strokeWidth={1.4} strokeLinecap="round" />
          <SvgPath d="M7.6 5.4H11.4A2.2 2.2 0 0 1 13.6 7.6V11.4A2.2 2.2 0 0 1 11.4 13.6H7.6A2.2 2.2 0 0 1 5.4 11.4V7.6A2.2 2.2 0 0 1 7.6 5.4Z" stroke={ACTION_COLOR} strokeWidth={1.4} strokeLinejoin="round" />
        </>
      ) : (
        <>
          <SvgPath d="M7.5 1.6V9.2M4.6 6.4L7.5 9.3L10.4 6.4" stroke={ACTION_COLOR} strokeWidth={1.4} strokeLinecap="round" strokeLinejoin="round" />
          <SvgPath d="M1.5 9.6V10.5A2.1 2.1 0 0 0 3.6 12.6H11.4A2.1 2.1 0 0 0 13.5 10.5V9.6" stroke={ACTION_COLOR} strokeWidth={1.4} strokeLinecap="round" strokeLinejoin="round" />
        </>
      )}
    </Svg>
  );
}
/** One light tap when a flip or a share commits; never on web, never an error if the device has no engine. */
function hapticCommit() {
  if (Platform.OS === 'web') return;
  Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
}
function hapticCopied() {
  if (Platform.OS === 'web') return;
  Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success).catch(() => {});
}

const DETAILS = 'Paws, People & Purpose\nSaturday, July 18 · 2:00–4:00 PM\nGoodfolk Coffee · West Los Angeles\nCommunity pass: COMMON-0718';

export function EventPassShare({
  autoPlay = false,
  onCopyDetails,
  onShareEvent,
  onExportImage,
}: EventPassShareProps) {
  const { width, height } = useWindowDimensions();
  const insets = useSafeAreaInsets();
  const initialReduced = useReducedMotion();
  const [reducedMotion, setReducedMotion] = useState(initialReduced);
  const [screenReader, setScreenReader] = useState(false);
  const [solidGlass, setSolidGlass] = useState(false);
  const quiet = reducedMotion || screenReader;
  const scale = width / 402;
  const top = useCallback((reference: number) => insets.top + (reference - 62) * scale, [insets.top, scale]);
  const contentHeight = Math.max(height + 1, top(852) + insets.bottom);
  const initialFrame = demoFrame(0);
  const initialCadence = autoPlay && !quiet ? CLOCK_ANIMATION : CLOCK_STOPPED;
  const [semantic, setSemantic] = useState<SemanticState>({ cadence: initialCadence, backVisible: false, copyConfirmed: false });
  const [foregroundState, setForegroundState] = useState(AppState.currentState === 'active');
  const [sheetOpen, setSheetOpen] = useState(true);
  const [busy, setBusy] = useState<ActionKind | null>(null);
  const busyRef = useRef<ActionKind | null>(null);
  const [error, setError] = useState<string | null>(null);
  const exportRef = useRef<Svg | null>(null);

  const motion = useSharedValue<EventPassMotionState>(createMotionState(0, autoPlay, quiet));
  const frame = useSharedValue<EventPassFrame>(initialFrame);
  const logicalClock = useSharedValue(0);
  const lastRealSample = useSharedValue(-1);
  const foreground = useSharedValue(AppState.currentState === 'active');
  const quietShared = useSharedValue(quiet);
  const publishedCadence = useSharedValue<ClockCadence>(initialCadence);
  const publishedBack = useSharedValue(false);
  const publishedCopied = useSharedValue(false);
  const sheetOffset = useSharedValue(0);
  /** Field drift angle and its follower [x, vx, y, vy, glow, vglow] (see `stepAmbientFollower`). */
  const ambientTheta = useSharedValue(0);
  const follower = useSharedValue<number[]>([0, 0, 0, 0, 0, 0]);

  const touchAccepted = useSharedValue(false);
  const touchIdentifier = useSharedValue(-1);
  const touchStartX = useSharedValue(0);
  const touchStartY = useSharedValue(0);
  const touchLastX = useSharedValue(0);
  const touchLastY = useSharedValue(0);
  const touchLastTime = useSharedValue(0);
  const touchVelocityX = useSharedValue(0);
  const touchVelocityY = useSharedValue(0);

  const publishSemantic = useCallback((cadence: ClockCadence, backVisible: boolean, copyConfirmed: boolean) => {
    setSemantic((current) => current.cadence === cadence && current.backVisible === backVisible && current.copyConfirmed === copyConfirmed
      ? current
      : { cadence, backVisible, copyConfirmed });
  }, []);

  const publishUI = useCallback((nextFrame: EventPassFrame, cadence: ClockCadence) => {
    'worklet';
    const backVisible = nextFrame.backOpacity >= 0.5;
    if (publishedCadence.get() !== cadence || publishedBack.get() !== backVisible || publishedCopied.get() !== nextFrame.copyConfirmed) {
      publishedCadence.set(cadence);
      publishedBack.set(backVisible);
      publishedCopied.set(nextFrame.copyConfirmed);
      scheduleOnRN(publishSemantic, cadence, backVisible, nextFrame.copyConfirmed);
    }
  }, [publishSemantic, publishedBack, publishedCadence, publishedCopied]);

  const sampleUI = useCallback((now: number) => {
    'worklet';
    const sample = sampleMotion(motion.get(), now);
    motion.set(sample.state);
    frame.set(sample.frame);
    publishUI(sample.frame, sample.cadence);
  }, [frame, motion, publishUI]);

  const logicalNowUI = useCallback(() => {
    'worklet';
    const real = performance.now() / 1000;
    const next = advanceLogicalClock(logicalClock.get(), lastRealSample.get(), real, foreground.get());
    logicalClock.set(next.clock);
    lastRealSample.set(next.realSample);
    return next.clock;
  }, [foreground, lastRealSample, logicalClock]);

  const mutateUI = useCallback((kind: number, value = false) => {
    'worklet';
    const now = logicalNowUI();
    if ((kind === 1 && value) || (kind === 2 && !value) || (kind === 3 && value)) {
      touchAccepted.set(false);
      touchIdentifier.set(-1);
    }
    let next = motion.get();
    if (kind === 0) next = setAutoPlay(next, value, now);
    else if (kind === 1) {
      quietShared.set(value);
      next = setQuiet(next, value, now);
    } else if (kind === 2) next = setVisible(next, value, now);
    else if (kind === 3) next = setSharePresented(next, value, now);
    else if (kind === 4) next = confirmCopy(next, now);
    else if (kind === 5) next = toggleFace(next, now, quietShared.get());
    motion.set(next);
    sampleUI(now);
  }, [logicalNowUI, motion, quietShared, sampleUI, touchAccepted, touchIdentifier]);

  const backgroundUI = useCallback(() => {
    'worklet';
    const now = logicalNowUI();
    motion.set(setForeground(motion.get(), false, now));
    touchAccepted.set(false);
    touchIdentifier.set(-1);
    foreground.set(false);
    lastRealSample.set(-1);
    sampleUI(now);
  }, [foreground, lastRealSample, logicalNowUI, motion, sampleUI, touchAccepted, touchIdentifier]);

  const foregroundUI = useCallback(() => {
    'worklet';
    foreground.set(true);
    lastRealSample.set(performance.now() / 1000);
    const now = logicalClock.get();
    motion.set(setForeground(motion.get(), true, now));
    sampleUI(now);
  }, [foreground, lastRealSample, logicalClock, motion, sampleUI]);

  const frameWorklet = useCallback((info: FrameInfo) => {
    'worklet';
    if (!foreground.get()) return;
    const now = logicalNowUI();
    // The pass's clock only samples while it moves; the light field drifts on every frame.
    if (publishedCadence.get() === CLOCK_ANIMATION) sampleUI(now);
    if (quietShared.get()) return;
    ambientTheta.set(ambientAngle(now));
    const current = frame.get();
    const turn = current.quietCrossfade ? 0 : current.flipAngle;
    const target = spillUniform(current.tiltX, current.tiltY, turn);
    const dt = (info.timeSincePreviousFrame ?? 16.7) / 1000;
    follower.modify((state) => {
      'worklet';
      stepAmbientFollower(state, target[0], target[1], target[2], dt);
      return state;
    });
  }, [ambientTheta, follower, foreground, frame, logicalNowUI, publishedCadence, quietShared, sampleUI]);
  const frameCallback = useFrameCallback(frameWorklet, false);

  useEffect(() => {
    frameCallback.setActive(foregroundState && (semantic.cadence === CLOCK_ANIMATION || !quiet));
  }, [foregroundState, frameCallback, quiet, semantic.cadence]);

  /** Reduce Motion: the field holds its rest frame (drift angle 0, no tilt colour). */
  useEffect(() => {
    if (!quiet) return;
    scheduleOnUI(() => {
      'worklet';
      ambientTheta.set(0);
      follower.set([0, 0, 0, 0, 0, 0]);
    });
  }, [ambientTheta, follower, quiet]);

  const pageUniforms = useDerivedValue(() => {
    const state = follower.get();
    return {
      res: [width, height],
      geo: [scale, 62 - insets.top / scale, sheetOffset.get() / scale, P3],
      amb: [ambientTheta.get(), state[0], state[2], state[4]],
    };
  });
  const sheetUniforms = useDerivedValue(() => {
    const state = follower.get();
    const current = frame.get();
    const spill = spillUniform(current.tiltX, current.tiltY, current.quietCrossfade ? 0 : current.flipAngle);
    return {
      res: [width, height],
      geo: [scale, sheet.top, sheetOffset.get() / scale, P3],
      amb: [ambientTheta.get(), state[0], state[2], state[4]],
      card: [spill[0], spill[1], spill[2], 0],
    };
  });

  useEffect(() => { scheduleOnUI(mutateUI, 0, autoPlay); }, [autoPlay, mutateUI]);
  useEffect(() => { scheduleOnUI(mutateUI, 1, quiet); }, [mutateUI, quiet]);

  useEffect(() => {
    const subscription = AccessibilityInfo.addEventListener('reduceMotionChanged', setReducedMotion);
    return () => subscription.remove();
  }, []);

  useEffect(() => {
    let mounted = true;
    void AccessibilityInfo.isReduceTransparencyEnabled().then((enabled) => { if (mounted) setSolidGlass(enabled); });
    const subscription = AccessibilityInfo.addEventListener('reduceTransparencyChanged', setSolidGlass);
    return () => { mounted = false; subscription.remove(); };
  }, []);

  useEffect(() => {
    let mounted = true;
    void AccessibilityInfo.isScreenReaderEnabled().then((enabled) => { if (mounted) setScreenReader(enabled); });
    const subscription = AccessibilityInfo.addEventListener('screenReaderChanged', setScreenReader);
    return () => { mounted = false; subscription.remove(); };
  }, []);

  useEffect(() => {
    const subscription = AppState.addEventListener('change', (next) => {
      const active = next === 'active';
      setForegroundState(active);
      scheduleOnUI(active ? foregroundUI : backgroundUI);
    });
    return () => subscription.remove();
  }, [backgroundUI, foregroundUI]);

  const nativeScroll = useMemo(() => Gesture.Native(), []);
  const passGesture = useMemo(() => Gesture.Manual()
    .simultaneousWithExternalGesture(nativeScroll)
    .onTouchesDown((event, manager) => {
      if (touchAccepted.get() || event.changedTouches.length === 0) return;
      manager.activate();
      const touch = event.changedTouches[0];
      const now = logicalNowUI();
      const next = beginInteraction(motion.get(), now);
      const accepted = next.dragging;
      motion.set(next);
      touchAccepted.set(accepted);
      if (accepted) {
        touchIdentifier.set(touch.id);
        touchStartX.set(touch.x);
        touchStartY.set(touch.y);
        touchLastX.set(touch.x);
        touchLastY.set(touch.y);
        touchLastTime.set(now);
        touchVelocityX.set(0);
        touchVelocityY.set(0);
      }
      sampleUI(now);
    })
    .onTouchesMove((event) => {
      if (!touchAccepted.get()) return;
      const touch = event.changedTouches.find((item) => item.id === touchIdentifier.get());
      if (!touch) return;
      const now = logicalNowUI();
      const delta = Math.max(0.001, now - touchLastTime.get());
      touchVelocityX.set((touch.x - touchLastX.get()) / delta);
      touchVelocityY.set((touch.y - touchLastY.get()) / delta);
      touchLastX.set(touch.x);
      touchLastY.set(touch.y);
      touchLastTime.set(now);
      motion.set(updateInteraction(motion.get(), (touch.x - touchStartX.get()) / scale, (touch.y - touchStartY.get()) / scale));
      sampleUI(now);
    })
    .onTouchesUp((event, manager) => {
      if (!touchAccepted.get()) { manager.end(); return; }
      const touch = event.changedTouches.find((item) => item.id === touchIdentifier.get());
      if (!touch) return;
      const now = logicalNowUI();
      const x = touch.x;
      const y = touch.y;
      const horizontal = (x - touchStartX.get()) / scale;
      const vertical = (y - touchStartY.get()) / scale;
      const before = motion.get();
      const after = endInteraction(
        before,
        horizontal,
        vertical,
        horizontal + touchVelocityX.get() / scale * 0.2,
        vertical + touchVelocityY.get() / scale * 0.2,
        now,
        quietShared.get(),
      );
      motion.set(after);
      // A tap turns the pass (a drag only settles back): that turn is the commit.
      if (after !== before && after.phase !== PHASE_SETTLING) scheduleOnRN(hapticCommit);
      touchAccepted.set(false);
      touchIdentifier.set(-1);
      sampleUI(now);
      manager.end();
    })
    .onTouchesCancelled((event, manager) => {
      if (touchAccepted.get() && !event.changedTouches.some((touch) => touch.id === touchIdentifier.get())) return;
      if (touchAccepted.get()) {
        const now = logicalNowUI();
        motion.set(cancelInteraction(motion.get(), now));
        touchAccepted.set(false);
        touchIdentifier.set(-1);
        sampleUI(now);
      }
      manager.end();
    })
    .onFinalize(() => {
      if (!touchAccepted.get()) return;
      const now = logicalNowUI();
      motion.set(cancelInteraction(motion.get(), now));
      touchAccepted.set(false);
      touchIdentifier.set(-1);
      sampleUI(now);
    }), [logicalNowUI, motion, nativeScroll, quietShared, sampleUI, scale, touchAccepted, touchIdentifier, touchLastTime, touchLastX, touchLastY, touchStartX, touchStartY, touchVelocityX, touchVelocityY]);

  const sheetStyle = useAnimatedStyle(() => ({ transform: [{ translateY: sheetOffset.get() }] }));
  const closeSheet = useCallback(() => {
    setSheetOpen(false);
    scheduleOnUI(mutateUI, 2, false);
    sheetOffset.set(withTiming(height + SHEET_DISMISS.overshoot, { duration: quiet ? 0 : 250, easing: Easing.bezier(0.23, 1, 0.32, 1) }));
  }, [height, mutateUI, quiet, sheetOffset]);
  /** The drag already moved the sheet: only the state and the pass's clock follow. */
  const commitDragClose = useCallback(() => {
    setSheetOpen(false);
    scheduleOnUI(mutateUI, 2, false);
    hapticCommit();
  }, [mutateUI]);
  const sheetDrag = useMemo(() => Gesture.Pan()
    .activeOffsetY([-SHEET_DISMISS.activeOffset, SHEET_DISMISS.activeOffset])
    .onUpdate((event) => {
      const t = event.translationY;
      sheetOffset.set(t >= 0 ? t : -rubberBand(-t));
    })
    .onEnd((event) => {
      const close = event.translationY > height * SHEET_DISMISS.commitShare || event.velocityY > SHEET_DISMISS.commitVelocity;
      if (close) {
        const to = height + SHEET_DISMISS.overshoot;
        sheetOffset.set(quietShared.get() ? to : withSpring(to, { ...SHEET_DISMISS.spring, velocity: event.velocityY }));
        scheduleOnRN(commitDragClose);
      } else {
        sheetOffset.set(quietShared.get() ? 0 : withSpring(0, { ...SHEET_DISMISS.back, velocity: event.velocityY }));
      }
    }), [commitDragClose, height, quietShared, sheetOffset]);
  const reopenSheet = useCallback(() => {
    setSheetOpen(true);
    scheduleOnUI(mutateUI, 2, true);
    sheetOffset.set(withTiming(0, { duration: quiet ? 0 : 300, easing: Easing.bezier(0.23, 1, 0.32, 1) }));
  }, [mutateUI, quiet, sheetOffset]);
  const accessibilityFlip = useCallback(() => {
    hapticCommit();
    scheduleOnUI(mutateUI, 5);
  }, [mutateUI]);

  const runAction = useCallback(async (kind: ActionKind) => {
    if (busyRef.current) return;
    busyRef.current = kind;
    setBusy(kind);
    setError(null);
    const pausesPresentation = kind !== 'copy';
    let paused = false;
    try {
      if (pausesPresentation) {
        await runOnUIAsync(mutateUI, 3, true);
        paused = true;
      }
      if (kind !== 'copy') hapticCommit();
      if (kind === 'copy') {
        await onCopyDetails();
        hapticCopied();
        scheduleOnUI(mutateUI, 4);
      } else if (kind === 'share') {
        await onShareEvent();
      } else {
        const svg = exportRef.current;
        if (!svg) throw new Error('Export renderer is unavailable.');
        const base64 = await new Promise<string>((resolve) => {
          svg.toDataURL(resolve);
        });
        if (!base64) throw new Error('Export renderer returned no image.');
        await onExportImage(base64);
      }
    } catch {
      setError(kind === 'copy' ? 'Couldn’t copy event details. Try again.' : kind === 'share' ? 'Couldn’t share this event. Try again.' : 'Couldn’t export the event image. Try again.');
    } finally {
      try {
        if (paused) await runOnUIAsync(mutateUI, 3, false);
      } finally {
        busyRef.current = null;
        setBusy(null);
      }
    }
  }, [mutateUI, onCopyDetails, onExportImage, onShareEvent]);

  return (
    <GestureHandlerRootView style={styles.screen}>
      <StatusBar style="light" />
      <View
        style={StyleSheet.absoluteFill}
        accessible={false}
        accessibilityElementsHidden={sheetOpen}
        importantForAccessibility={sheetOpen ? 'no-hide-descendants' : 'auto'}
        pointerEvents={sheetOpen ? 'none' : 'auto'}
      >
        {PAGE_EFFECT ? (
          <Canvas style={StyleSheet.absoluteFill} pointerEvents="none">
            <Fill>
              <Shader source={PAGE_EFFECT} uniforms={pageUniforms} />
            </Fill>
          </Canvas>
        ) : (
          <LinearGradient colors={['#646464', '#4E4E4E']} style={StyleSheet.absoluteFill} />
        )}
        <View style={styles.pageContent}>
          <PosterSvg width={Math.min(251, width - 96)} height={Math.min(251, width - 96)} />
          <Text allowFontScaling={false} style={styles.pageTitle}>Paws, People &amp; Purpose</Text>
          <Text allowFontScaling={false} style={styles.pageCaption}>Saturday, July 18 · Los Angeles</Text>
          <Pressable onPress={reopenSheet} accessibilityRole="button" style={[styles.reopen, { width: Math.min(260, width - 64) }]}>
            <Text allowFontScaling={false} style={styles.reopenText}>Share Event</Text>
          </Pressable>
        </View>
      </View>

      <Animated.View
        style={[StyleSheet.absoluteFill, sheetStyle]}
        accessibilityElementsHidden={!sheetOpen}
        importantForAccessibility={sheetOpen ? 'auto' : 'no-hide-descendants'}
        pointerEvents={sheetOpen ? 'auto' : 'none'}
      >
        {SHEET_EFFECT ? null : <View style={[styles.safeBottom, { height: insets.bottom, top: height - insets.bottom }]} />}
        <GestureDetector gesture={nativeScroll}>
          <ScrollView showsVerticalScrollIndicator={false} contentContainerStyle={{ width, height: contentHeight }}>
            <View style={{ width, height: contentHeight }}>
              {/* Charcoal under the glass: what shows until the canvas has drawn its first frame. */}
              <View style={[styles.sheet, { top: top(sheet.top), width, height: contentHeight - top(sheet.top) + insets.bottom, borderTopLeftRadius: sheet.radius * scale, borderTopRightRadius: sheet.radius * scale }]} />
              {SHEET_EFFECT ? (
                <Canvas pointerEvents="none" style={{ position: 'absolute', left: 0, top: top(sheet.top), width, height: contentHeight - top(sheet.top) + insets.bottom + SHEET_OVERDRAW, borderTopLeftRadius: sheet.radius * scale, borderTopRightRadius: sheet.radius * scale, overflow: 'hidden' }}>
                  <Fill>
                    <Shader source={SHEET_EFFECT} uniforms={sheetUniforms} />
                  </Fill>
                </Canvas>
              ) : null}
              <View style={[styles.handle, { left: sheet.handle.left * scale, top: top(sheet.handle.top), width: sheet.handle.width * scale, height: sheet.handle.height * scale, borderRadius: sheet.handle.height * scale / 2 }]} />
              <Text allowFontScaling={false} style={[styles.header, { top: top(sheet.headerTop), left: 111 * scale, width: 180 * scale, height: sheet.close.size * scale, fontSize: sheet.headerFontSize * scale, lineHeight: sheet.close.size * scale }]}>Share Event</Text>
              {/* The grabber band (handle and title row): drag it down to dismiss the sheet. */}
              <GestureDetector gesture={sheetDrag}>
                <View
                  collapsable={false}
                  accessible={false}
                  style={{ position: 'absolute', left: 0, top: top(sheet.top), width, height: top(link.top) - top(sheet.top) }}
                />
              </GestureDetector>
              <Pressable onPress={closeSheet} accessibilityRole="button" accessibilityLabel="Close sharing sheet" style={[styles.close, SHEET_EFFECT ? styles.glassChrome : null, { left: sheet.close.left * scale, top: top(sheet.headerTop), width: sheet.close.size * scale, height: sheet.close.size * scale, borderRadius: sheet.close.size * scale / 2 }]}>
                {SHEET_EFFECT ? <GlassSurface kind="clear" solid={solidGlass} width={sheet.close.size * scale} height={sheet.close.size * scale} radius={sheet.close.size * scale / 2} /> : null}
                <Ionicons name="close" color="#FFFFFF" size={sheet.close.glyph * scale} />
              </Pressable>

              <Pressable
                disabled={busy !== null}
                onPress={() => { void runAction('copy'); }}
                accessibilityRole="button"
                accessibilityLabel={semantic.copyConfirmed ? 'Event details copied' : 'Copy event details'}
                style={[styles.copyTarget, { left: (201 - link.width / 2) * scale, top: top(link.top), width: link.width * scale, height: link.hitHeight * scale }]}
              >
                <View style={[styles.copyPill, SHEET_EFFECT ? styles.glassChrome : null, { width: link.width * scale, height: link.height * scale, borderRadius: link.height * scale / 2, paddingHorizontal: link.padding * scale, gap: link.gap * scale }] }>
                  {SHEET_EFFECT ? <GlassSurface kind="clear" solid={solidGlass} width={link.width * scale} height={link.height * scale} radius={link.height * scale / 2} /> : null}
                  <Text allowFontScaling={false} numberOfLines={1} adjustsFontSizeToFit style={[styles.copyText, { fontSize: link.fontSize * scale }]}>
                    {semantic.copyConfirmed ? 'Copied' : busy === 'copy' ? 'Copying…' : 'Event info · Jul 18'}
                  </Text>
                  <Ionicons name={semantic.copyConfirmed ? 'checkmark' : busy === 'copy' ? 'hourglass-outline' : 'copy-outline'} color="rgba(255,255,255,0.76)" size={link.icon * scale} />
                </View>
              </Pressable>

              {SHEET_EFFECT ? null : (
                <LinearGradient
                  colors={['#656565', '#909090']}
                  style={[styles.stage, { left: stage.left * scale, top: top(stage.top), width: stage.width * scale, height: stage.height * scale, borderRadius: stage.radius * scale }]}
                />
              )}

              <View style={{ position: 'absolute', left: pass.left * scale, top: top(pass.top) }}>
                <EventPass frame={frame} scale={scale} gesture={passGesture} backVisible={semantic.backVisible} onAccessibilityTap={accessibilityFlip} />
              </View>

              <Text allowFontScaling={false} style={[styles.wordmark, { top: top(stage.wordmarkTop), left: 136 * scale, width: 130 * scale, height: 28 * scale, fontSize: 20 * scale, lineHeight: 28 * scale }]}>common✦</Text>

              <Pressable
                disabled={busy !== null}
                onPress={() => { void runAction('copy'); }}
                accessibilityRole="button"
                accessibilityLabel={semantic.copyConfirmed ? 'Event details copied' : 'Copy event details'}
                style={[styles.stageAction, { left: (stage.copyCenterX - stage.actionHit / 2) * scale, top: top(stage.actionCenterY - stage.actionHit / 2), width: stage.actionHit * scale, height: stage.actionHit * scale }]}
              >
                {semantic.copyConfirmed || busy === 'copy'
                  ? <Ionicons name={semantic.copyConfirmed ? 'checkmark' : 'hourglass-outline'} color={ACTION_COLOR} size={stage.actionIcon * scale} />
                  : <StageIcon kind="copy" size={stage.actionIcon * scale} />}
              </Pressable>
              <Pressable
                disabled={busy !== null}
                onPress={() => { void runAction('export'); }}
                accessibilityRole="button"
                accessibilityLabel="Export event image"
                style={[styles.stageAction, { left: (stage.downloadCenterX - stage.actionHit / 2) * scale, top: top(stage.actionCenterY - stage.actionHit / 2), width: stage.actionHit * scale, height: stage.actionHit * scale }]}
              >
                {busy === 'export'
                  ? <Ionicons name="hourglass-outline" color={ACTION_COLOR} size={stage.actionIcon * scale} />
                  : <StageIcon kind="download" size={stage.actionIcon * scale} />}
              </Pressable>

              {error && (
                <Text accessibilityLiveRegion="polite" allowFontScaling={false} style={[styles.error, { left: 30 * scale, top: top(746), width: 342 * scale, fontSize: 12 * scale }]}>{error}</Text>
              )}

              <Pressable
                disabled={busy !== null}
                onPress={() => { void runAction('share'); }}
                accessibilityRole="button"
                style={[styles.shareButton, SHEET_EFFECT ? styles.glassChrome : null, { left: shareButton.left * scale, top: top(shareButton.top), width: shareButton.width * scale, height: shareButton.height * scale, borderRadius: shareButton.height * scale / 2 }]}
              >
                {SHEET_EFFECT ? <GlassSurface kind="prominent" solid={solidGlass} width={shareButton.width * scale} height={shareButton.height * scale} radius={shareButton.height * scale / 2} /> : null}
                {/* Dim the label, not the button: a parent alpha below 1 breaks the glass effect. */}
                <Text allowFontScaling={false} style={[styles.shareText, { fontSize: shareButton.fontSize * scale, opacity: busy && busy !== 'share' ? 0.5 : 1 }]}>{busy === 'share' ? 'Sharing…' : 'Share Event'}</Text>
              </Pressable>

              <View pointerEvents="none" style={styles.exportRenderer} accessibilityElementsHidden importantForAccessibility="no-hide-descendants">
                <EventPassExportSvg svgRef={exportRef} />
              </View>
            </View>
          </ScrollView>
        </GestureDetector>
      </Animated.View>
    </GestureHandlerRootView>
  );
}

export { DETAILS as EVENT_PASS_SHARE_DETAILS };

const styles = StyleSheet.create({
  screen: {
    flex: 1,
    backgroundColor: '#0B1518',
  },
  pageContent: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    gap: 7,
  },
  pageTitle: {
    marginTop: 15,
    color: '#FFFFFF',
    fontSize: 24,
    lineHeight: 30,
    fontWeight: '600',
  },
  pageCaption: {
    color: 'rgba(255,255,255,0.65)',
    fontSize: 15,
    lineHeight: 20,
    fontWeight: '500',
  },
  reopen: {
    height: 52,
    marginTop: 15,
    borderRadius: 26,
    backgroundColor: '#FFFFFF',
    alignItems: 'center',
    justifyContent: 'center',
  },
  reopenText: {
    color: '#000000',
    fontSize: 17,
    fontWeight: '600',
  },
  safeBottom: {
    position: 'absolute',
    left: 0,
    right: 0,
    backgroundColor: CHARCOAL,
  },
  sheet: {
    position: 'absolute',
    backgroundColor: CHARCOAL,
  },
  handle: {
    position: 'absolute',
    backgroundColor: 'rgba(255,255,255,0.13)',
  },
  header: {
    position: 'absolute',
    color: '#FFFFFF',
    fontWeight: '500',
    textAlign: 'center',
  },
  close: {
    position: 'absolute',
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: 'rgba(0,0,0,0.10)',
    borderColor: 'rgba(255,255,255,0.12)',
    borderWidth: 1,
  },
  copyTarget: {
    position: 'absolute',
    alignItems: 'center',
    justifyContent: 'center',
  },
  copyPill: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 8,
    borderWidth: 1,
    borderColor: '#454545',
    backgroundColor: 'rgba(0,0,0,0.04)',
  },
  copyText: {
    flexShrink: 1,
    color: 'rgba(255,255,255,0.76)',
    fontWeight: '400',
  },
  stage: {
    position: 'absolute',
  },
  wordmark: {
    position: 'absolute',
    color: 'rgba(255,255,255,0.31)',
    fontWeight: '600',
    textAlign: 'center',
  },
  stageAction: {
    position: 'absolute',
    alignItems: 'center',
    justifyContent: 'center',
  },
  error: {
    position: 'absolute',
    color: '#FFB4AB',
    textAlign: 'center',
  },
  shareButton: {
    position: 'absolute',
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: '#FFFFFF',
  },
  /** The glass draws the fill and the rim. */
  glassChrome: {
    backgroundColor: 'transparent',
    borderWidth: 0,
  },
  shareText: {
    color: '#000000',
    fontWeight: '500',
  },
  exportRenderer: {
    position: 'absolute',
    left: -500,
    top: 0,
    width: 362,
    height: 541,
    opacity: 0.01,
  },
});
