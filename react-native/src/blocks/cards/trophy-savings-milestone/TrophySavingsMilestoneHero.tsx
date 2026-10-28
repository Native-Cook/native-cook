import * as Haptics from 'expo-haptics';
import { useImage } from '@shopify/react-native-skia';
import { forwardRef, useCallback, useEffect, useImperativeHandle, useMemo, useRef, useState } from 'react';
import { AppState, StyleSheet, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, { Easing, ReduceMotion, cancelAnimation, useAnimatedStyle, useFrameCallback, useSharedValue, withSequence, withTiming } from 'react-native-reanimated';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';

import { useReducedMotionPreference } from '@/shared/useReducedMotionPreference';

import { TrophySavingsMilestoneArtwork } from './TrophySavingsMilestoneArtwork';
import { TrophySavingsMilestoneCupLight, TrophySavingsMilestoneFrontApplause, TrophySavingsMilestoneRearApplause } from './TrophySavingsMilestoneApplause';
import { TROPHY_APPLAUSE_SETTLE, advanceApplause, applauseIsSettled, galleryApplause, restApplause, type TrophyApplauseFrame } from './TrophySavingsMilestoneCelebration';
import { PEAK_HOLD, beginEvent, clamp, endTime, loopPose, loopProgress, makeMotion, peakTime, presentedPose, presentedProgress } from './TrophySavingsMilestoneMotion';
import { TrophySavingsMilestoneRibbon, useTrophyRibbonGeometry } from './TrophySavingsMilestoneRibbon';
import { TrophySavingsMilestoneShimmer } from './TrophySavingsMilestoneShimmer';

export type TrophySavingsMilestoneHeroHandle = { pauseForOverlay: () => void; resumeFromOverlay: () => void };
type Props = { autoPlay: boolean; previewTime?: number; previewReducedMotion: boolean; scale: number };

export const TrophySavingsMilestoneHero = forwardRef<TrophySavingsMilestoneHeroHandle, Props>(function TrophySavingsMilestoneHero({ autoPlay, previewTime, previewReducedMotion, scale }, ref) {
  const reduced = useReducedMotionPreference() || previewReducedMotion;
  const [appActive, setAppActive] = useState(AppState.currentState === 'active');
  const runtime = useRef({ mounted: true, active: appActive, reduced, previewTime, overlay: false });
  const frameHandle = useRef<{ setActive: (active: boolean) => void } | null>(null);
  const resumeTimer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const motion = useSharedValue(makeMotion(0, 0));
  const progress = useSharedValue(reduced ? 0 : previewTime == null ? 0 : loopProgress(previewTime));
  const paperPose = useSharedValue(reduced ? 0 : previewTime == null ? 0 : loopPose(previewTime));
  const opacity = useSharedValue(1);
  const activeShared = useSharedValue(appActive);
  const reducedShared = useSharedValue(reduced);
  const previewShared = useSharedValue(previewTime != null);
  const autoPlayShared = useSharedValue(autoPlay);
  const overlayShared = useSharedValue(false);
  const celebration = useSharedValue<TrophyApplauseFrame>(reduced ? restApplause(0, true) : previewTime == null ? restApplause() : galleryApplause(previewTime, 0));
  const sceneEpoch = useSharedValue(0);
  const geometry = useTrophyRibbonGeometry(progress, paperPose);
  const rollImage = useImage(require('./assets/paper-applause-open-roll-right.png'));

  const clearResumeTimer = useCallback(() => {
    if (resumeTimer.current != null) clearTimeout(resumeTimer.current);
    resumeTimer.current = null;
  }, []);

  const wakeFrames = useCallback((generation: number) => {
    const settings = runtime.current;
    const current = motion.get();
    if (!settings.mounted || !settings.active || settings.reduced || settings.overlay || settings.previewTime != null || current.generation !== generation) return;
    if (current.mode === 'event' || current.mode === 'loop' || current.mode === 'drag') frameHandle.current?.setActive(true);
  }, [motion]);

  const stopForContact = useCallback((generation: number) => {
    const current = motion.get();
    if (current.generation !== generation || current.mode !== 'drag') return;
    clearResumeTimer();
    frameHandle.current?.setActive(true);
  }, [clearResumeTimer, motion]);

  // This is one guarded read per requested impact, never a frame bridge/readback.
  const emitPeak = useCallback((generation: number) => {
    const settings = runtime.current;
    const current = motion.get();
    if (!settings.mounted || !settings.active || settings.reduced || settings.overlay || settings.previewTime != null || current.generation !== generation || current.mode !== 'event' || !current.eventUserPeak || !current.hapticSent) return;
    // Expo exposes no prepare API. A stale generation never reaches the native impact call.
    void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
  }, [motion]);

  const waitForResume = useCallback((generation: number, deadline: number) => {
    const current = motion.get();
    if (!runtime.current.mounted || current.generation !== generation || current.mode !== 'waiting') return;
    frameHandle.current?.setActive(false);
    clearResumeTimer();
    resumeTimer.current = setTimeout(() => {
      resumeTimer.current = null;
      if (!runtime.current.mounted) return;
      scheduleOnUI((token: number, epoch: number) => {
        const latest = motion.get();
        if (latest.generation !== token || latest.mode !== 'waiting' || !activeShared.get() || reducedShared.get() || previewShared.get() || overlayShared.get()) return;
        const loop = autoPlayShared.get();
        motion.set({ ...latest, mode: loop ? 'loop' : 'idle', progress: 0, pose: 0, direction: 0, epoch });
        progress.set(0);
        paperPose.set(0);
        celebration.set(restApplause(token));
        sceneEpoch.set(epoch);
        if (loop) scheduleOnRN(wakeFrames, token);
      }, generation, deadline);
    }, Math.max(0, (deadline - Date.now() / 1000) * 1000));
  }, [activeShared, autoPlayShared, celebration, clearResumeTimer, sceneEpoch, motion, overlayShared, paperPose, previewShared, progress, reducedShared, wakeFrames]);

  const frame = useFrameCallback(() => {
    if (!activeShared.get() || reducedShared.get() || previewShared.get() || overlayShared.get()) return;
    let current = motion.get();
    const now = Date.now() / 1000;
    if (current.mode === 'loop') {
      const scene = galleryApplause(now - current.epoch, current.generation);
      celebration.set(scene);
      progress.set(scene.progress);
      paperPose.set(loopPose(now - current.epoch));
      sceneEpoch.set(now - scene.clock);
      return;
    }
    if (current.mode !== 'event' && current.mode !== 'drag') return;
    const previous = celebration.get();
    const sceneClock = Math.max(previous.clock, now - sceneEpoch.get());
    const p = presentedProgress(current, now);
    const scene = advanceApplause(previous, sceneClock, p, current.generation);
    celebration.set(scene);
    progress.set(p);
    paperPose.set(presentedPose(current, now));
    if (current.mode === 'drag') return;
    const age = Math.max(0, now - current.eventStarted);
    if (current.eventUserPeak && !current.hapticSent && age >= peakTime(current)) {
      current = { ...current, hapticSent: true };
      motion.set(current);
      scheduleOnRN(emitPeak, current.generation);
    }
    const end = endTime(current);
    let lastWake = -1;
    for (const start of scene.wakeStarts) lastWake = Math.max(lastWake, start);
    const effectEnd = Math.max(lastWake, scene.contactStart) + TROPHY_APPLAUSE_SETTLE * (scene.cadence ?? 1);
    if (age >= end && scene.clock >= effectEnd) {
      motion.set({ ...current, mode: 'waiting', progress: 0, pose: 0, direction: 0 });
      progress.set(0);
      paperPose.set(0);
      celebration.set(restApplause(current.generation));
      scheduleOnRN(waitForResume, current.generation, current.eventStarted + Math.max(2, end));
    }
  }, false);

  useEffect(() => {
    frameHandle.current = frame;
    runtime.current = { ...runtime.current, mounted: true, active: appActive, reduced, previewTime };
    clearResumeTimer();
    activeShared.set(appActive);
    reducedShared.set(reduced);
    previewShared.set(previewTime != null);
    autoPlayShared.set(autoPlay);
    const overlay = runtime.current.overlay;
    scheduleOnUI((loop: boolean, reduce: boolean, active: boolean, preview: number | undefined, obscured: boolean) => {
      cancelAnimation(opacity);
      opacity.set(1);
      const next = makeMotion(motion.get().generation + 1, Date.now() / 1000, loop && active && !reduce && preview == null && !obscured ? 'loop' : obscured ? 'paused' : 'idle');
      next.progress = reduce ? 0 : preview == null ? 0 : loopProgress(preview);
      next.pose = reduce ? 0 : preview == null ? 0 : loopPose(preview);
      motion.set(next);
      progress.set(next.progress);
      paperPose.set(next.pose);
      celebration.set(preview == null ? restApplause(next.generation, reduce) : galleryApplause(preview, next.generation, reduce));
      sceneEpoch.set(Date.now() / 1000);
    }, autoPlay, reduced, appActive, previewTime, overlay);
    frame.setActive(autoPlay && appActive && !reduced && previewTime == null && !overlay);
    return () => frame.setActive(false);
  }, [activeShared, appActive, autoPlay, autoPlayShared, celebration, clearResumeTimer, frame, sceneEpoch, motion, opacity, paperPose, previewShared, previewTime, progress, reduced, reducedShared]);

  useEffect(() => {
    const subscription = AppState.addEventListener('change', state => {
      const active = state === 'active';
      runtime.current.active = active;
      activeShared.set(active);
      if (!active) {
        clearResumeTimer();
        frameHandle.current?.setActive(false);
      }
      setAppActive(active);
    });
    return () => subscription.remove();
  }, [activeShared, clearResumeTimer]);

  useEffect(() => () => {
    runtime.current.mounted = false;
    clearResumeTimer();
    frameHandle.current?.setActive(false);
    scheduleOnUI(() => {
      cancelAnimation(opacity);
      motion.set(makeMotion(motion.get().generation + 1, Date.now() / 1000));
      progress.set(0);
      paperPose.set(0);
      celebration.set(restApplause(motion.get().generation, reducedShared.get()));
    });
  }, [celebration, clearResumeTimer, motion, opacity, paperPose, progress, reducedShared]);

  const pauseForOverlay = useCallback(() => {
    if (runtime.current.overlay) return;
    runtime.current.overlay = true;
    overlayShared.set(true);
    clearResumeTimer();
    frameHandle.current?.setActive(false);
    scheduleOnUI(() => {
      const current = motion.get();
      const p = reducedShared.get() ? 0 : presentedProgress(current, Date.now() / 1000);
      const pose = reducedShared.get() ? 0 : presentedPose(current, Date.now() / 1000);
      motion.set({ ...current, generation: current.generation + 1, mode: 'paused', progress: p, pose, eventUserPeak: false });
      progress.set(p);
      paperPose.set(pose);
      celebration.set({ ...celebration.get(), progress: p });
      sceneEpoch.set(0);
    });
  }, [celebration, clearResumeTimer, sceneEpoch, motion, overlayShared, paperPose, progress, reducedShared]);

  const resumeFromOverlay = useCallback(() => {
    if (!runtime.current.overlay) return;
    runtime.current.overlay = false;
    overlayShared.set(false);
    const settings = runtime.current;
    if (settings.reduced || !settings.active || settings.previewTime != null) return;
    scheduleOnUI(() => {
      if (overlayShared.get() || reducedShared.get() || !activeShared.get() || previewShared.get()) return;
      const current = motion.get();
      const next = beginEvent(current, current.progress, false, false, Date.now() / 1000);
      motion.set(next);
      progress.set(next.progress);
      paperPose.set(next.pose);
      sceneEpoch.set(Date.now() / 1000 - celebration.get().clock);
      scheduleOnRN(wakeFrames, next.generation);
    });
  }, [activeShared, celebration, sceneEpoch, motion, overlayShared, paperPose, previewShared, progress, reducedShared, wakeFrames]);

  useImperativeHandle(ref, () => ({ pauseForOverlay, resumeFromOverlay }), [pauseForOverlay, resumeFromOverlay]);

  const demonstrate = useCallback(() => {
    'worklet';
    if (!activeShared.get() || previewShared.get() || overlayShared.get()) return;
    if (reducedShared.get()) {
      cancelAnimation(opacity);
      const easing = Easing.bezier(0, 0, 0.58, 1);
      opacity.set(withSequence(
        withTiming(0.88, { duration: 120, easing, reduceMotion: ReduceMotion.Never }),
        withTiming(1, { duration: 100, easing, reduceMotion: ReduceMotion.Never }),
      ));
      return;
    }
    const now = Date.now() / 1000;
    const current = motion.get();
    if (current.mode === 'event' && current.eventDemo && now - current.eventStarted < peakTime(current) + PEAK_HOLD) return;
    const next = beginEvent(current, presentedProgress(current, now), true, true, now);
    if (next.progress <= 0.000001 && applauseIsSettled(celebration.get())) celebration.set(restApplause(next.generation));
    sceneEpoch.set(now - celebration.get().clock);
    motion.set(next);
    progress.set(next.progress);
    paperPose.set(next.pose);
    scheduleOnRN(wakeFrames, next.generation);
  }, [activeShared, celebration, sceneEpoch, motion, opacity, overlayShared, paperPose, previewShared, progress, reducedShared, wakeFrames]);

  const releaseContact = useCallback((cancelled: boolean) => {
    'worklet';
    const current = motion.get();
    if (current.mode !== 'drag' || overlayShared.get() || !activeShared.get() || reducedShared.get() || previewShared.get()) return;
    const next = beginEvent(current, current.progress, false, !cancelled && current.progress >= 0.985, Date.now() / 1000, cancelled ? 0 : current.contactVelocity);
    motion.set(next);
    progress.set(next.progress);
    paperPose.set(next.pose);
    scheduleOnRN(wakeFrames, next.generation);
  }, [activeShared, motion, overlayShared, paperPose, previewShared, progress, reducedShared, wakeFrames]);

  const enabled = appActive && previewTime == null;
  const gesture = useMemo(() => {
    const unitScale = Math.max(0.01, scale);
    const pan = Gesture.Pan().minDistance(6 * unitScale).maxPointers(1).enabled(enabled && !reduced)
      .onStart(event => {
        if (!activeShared.get() || reducedShared.get() || previewShared.get() || overlayShared.get()) return;
        const current = motion.get();
        const p = presentedProgress(current, Date.now() / 1000);
        const rawPose = presentedPose(current, Date.now() / 1000);
        const pose = p <= 0.000001 && rawPose >= 1.999999 ? 0 : rawPose;
        const dx = event.translationX / unitScale;
        let direction = p > 0 ? current.direction === 0 ? 1 : current.direction : 0;
        if (direction === 0 && Math.abs(dx) > 0.5) direction = dx < 0 ? -1 : 1;
        const next = { ...current, mode: 'drag' as const, generation: current.generation + 1, progress: p, pose, contactPoseOrigin: pose, contactOrigin: p, contactDirection: direction, contactVelocity: 0, translationOrigin: dx, direction: direction || current.direction, eventUserPeak: false };
        motion.set(next);
        progress.set(p);
        paperPose.set(pose);
        if (p <= 0.000001 && applauseIsSettled(celebration.get())) celebration.set(restApplause(next.generation));
        sceneEpoch.set(Date.now() / 1000 - celebration.get().clock);
        scheduleOnRN(stopForContact, next.generation);
      })
      .onUpdate(event => {
        const current = motion.get();
        if (current.mode !== 'drag' || overlayShared.get() || reducedShared.get() || !activeShared.get() || previewShared.get()) return;
        const dx = event.translationX / unitScale;
        let direction = current.contactDirection;
        if (direction === 0 && Math.abs(dx) > 0.5) direction = dx < 0 ? -1 : 1;
        const p = clamp(current.contactOrigin + direction * (dx - current.translationOrigin) / 120);
        const pose = (current.contactPoseOrigin ?? current.contactOrigin) >= 1 ? 2 - p : p;
        motion.set({ ...current, progress: p, pose, contactDirection: direction, contactVelocity: clamp(direction * event.velocityX / unitScale / 120, -4, 4), direction: direction || current.direction });
        progress.set(p);
        paperPose.set(pose);
      })
      .onEnd(() => releaseContact(false))
      .onFinalize((_event, success) => { if (!success) releaseContact(true); });
    // Recognized pans own the whole contact; they never fall through into a tap.
    // Disabling the pan for reduced motion preserves this independent tap recognizer.
    const tap = Gesture.Tap().maxDistance(6 * unitScale).maxDuration(60000).enabled(enabled)
      .onEnd((_event, success) => { if (success) demonstrate(); });
    return Gesture.Exclusive(pan, tap);
  }, [activeShared, celebration, demonstrate, enabled, sceneEpoch, motion, overlayShared, paperPose, previewShared, progress, reduced, reducedShared, releaseContact, scale, stopForContact]);

  const trophyStyle = useAnimatedStyle(() => ({ opacity: opacity.get() }));
  const accessibilityActivate = useCallback(() => scheduleOnUI(demonstrate), [demonstrate]);
  return (
    <View style={styles.hero} pointerEvents="box-none">
      <TrophySavingsMilestoneRearApplause frame={celebration} />
      <TrophySavingsMilestoneRibbon geometry={geometry} foreground={false} rollImage={rollImage} />
      <Animated.View style={[styles.trophy, trophyStyle]} pointerEvents="none" accessible={false}>
        <TrophySavingsMilestoneArtwork />
      </Animated.View>
      <TrophySavingsMilestoneCupLight frame={celebration} />
      <TrophySavingsMilestoneShimmer frame={celebration} />
      <TrophySavingsMilestoneRibbon geometry={geometry} foreground rollImage={rollImage} />
      <TrophySavingsMilestoneFrontApplause frame={celebration} />
      <GestureDetector gesture={gesture}>
        <View collapsable={false} style={styles.hit} accessible accessibilityRole="button" accessibilityLabel="Savings trophy"
          accessibilityHint={reduced ? "Replay brief celebration feedback." : "Replay the celebration. Drag horizontally to feed the paper streamer."}
          accessibilityActions={[{ name: 'activate', label: 'Replay the celebration' }]}
          onAccessibilityTap={accessibilityActivate}
          onAccessibilityAction={event => { if (event.nativeEvent.actionName === 'activate') accessibilityActivate(); }} />
      </GestureDetector>
    </View>
  );
});

const styles = StyleSheet.create({
  hero: { position: 'absolute', left: 0, top: 0, width: 393, height: 350 },
  trophy: { position: 'absolute', left: 108, top: 96, width: 176, height: 214 },
  hit: { position: 'absolute', left: 19.5, top: 73, width: 353, height: 260, backgroundColor: 'transparent' },
});
