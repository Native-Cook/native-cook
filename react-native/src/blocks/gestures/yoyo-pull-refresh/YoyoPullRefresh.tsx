import { useCallback, useEffect, useMemo, useState } from 'react';
import { AccessibilityInfo, AppState, Platform, StyleSheet, useWindowDimensions, View } from 'react-native';
import { Canvas, Picture, Skia, useFont, useImage } from '@shopify/react-native-skia';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import { useDerivedValue, useFrameCallback, useReducedMotion, useSharedValue } from 'react-native-reanimated';
import { scheduleOnRN } from 'react-native-worklets';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';

import {
  CARDS,
  CARD_H,
  CARD_W,
  CARD_X,
  INTER_DISPLAY_BOLD,
  INTER_MEDIUM,
  INTER_SEMIBOLD,
  TITLE_SIZE,
  YOYO,
  cardTop,
  drawCards,
  drawFinger,
  drawHeader,
  drawYoyo,
  makeLayout,
} from './yoyoArt';
import type { Fonts, Layout } from './yoyoArt';
import {
  THRESHOLD,
  canGrab,
  beatOf,
  drag,
  dragD,
  frameAt,
  grab,
  initialTouch,
  reducedFrame,
  refresh,
  release,
  stepTouch,
  takeOver,
  touched,
  withOverflow,
} from './yoyoMotion';

/*
 Yo-yo Pull Refresh — a dark inbox whose pull-to-refresh spinner is a yo-yo. Pull the list down and
 a glossy blue yo-yo comes down on its string from the top of the screen, turning slowly; let go and
 it drops to the end of the string, bounces and "sleeps", spinning fast with a blue glow while the
 inbox refreshes, then climbs back up and the list slides home.

 Loop (LOOP = 6 s): rest (0–0.8) → pull (0.8–1.7) → drop (1.7–2.05) → sleep (2.05–3.6) → climb
 (3.6–4.2), list home (3.75–4.25) → rest (4.25–6.0, the t = 0 frame).
 Touch: pull the list down (rubber band); release past 120 pt to refresh, otherwise it springs back.
 Off the top the list scrolls. 2 s after the last touch the loop restarts at t = 0.
 Reduce Motion: no pull, drop or bounce; the yo-yo fades in at the end of its string and turns
 slowly while refreshing, then fades out.
*/

/** A light hit. On Android expo-haptics' impact is a weak raw pulse, so ask the system for its key click first. */
async function hitHaptic() {
  if (process.env.EXPO_OS === 'web') return;
  const light = () => Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light);
  try {
    if (Platform.OS === 'android') {
      await Haptics.performAndroidHapticsAsync(Haptics.AndroidHaptics.Virtual_Key).catch(light);
    } else {
      await light();
    }
  } catch {}
}

function announceDone() {
  AccessibilityInfo.announceForAccessibility('Up to date');
}

/** Reduce Motion's refresh: the yo-yo is visible for 2.1 s, the status returns at 3.84 s. */
const RM_LENGTH = 3.9;

type Props = {
  /** Plays the pull, the drop, the sleep and the climb on a loop of LOOP s. */
  autoPlay?: boolean;
};

export function YoyoPullRefresh({ autoPlay = false }: Props) {
  const { width, height } = useWindowDimensions();
  const reduced = useReducedMotion();
  const [active, setActive] = useState(AppState.currentState === 'active');
  const [rmRunning, setRmRunning] = useState(false);
  useEffect(() => {
    const sub = AppState.addEventListener('change', (state) => setActive(state === 'active'));
    return () => sub.remove();
  }, []);

  const layout: Layout = useMemo(() => makeLayout(width, height), [width, height]);
  const yoyo = useImage(YOYO);
  const display = useFont(INTER_DISPLAY_BOLD, TITLE_SIZE);
  const medium = useFont(INTER_MEDIUM, 15);
  const semibold = useFont(INTER_SEMIBOLD, 17);
  const fonts: Fonts | null = useMemo(() => {
    const mediumFace = medium?.getTypeface();
    if (!display || !medium || !semibold || !mediumFace) return null;
    return { title: display, status: medium, cardTitle: semibold, cardMeta: Skia.Font(mediumFace, 14) };
  }, [display, medium, semibold]);

  const clock = useSharedValue(0);
  const startedAt = useSharedValue(-1);
  const clockBase = useSharedValue(0);
  const lastStamp = useSharedValue(0);
  const touch = useSharedValue(initialTouch(layout.overflow));
  const lastBeat = useSharedValue(-1);
  /** Reduce Motion: the clock time its refresh started (−1: none). */
  const rmAt = useSharedValue(-1);
  const pressY = useSharedValue(0);

  useEffect(() => {
    const s = touch.get();
    if (s.overflow !== layout.overflow) touch.set(withOverflow(s, layout.overflow));
  }, [layout.overflow, touch]);

  const tick = useFrameCallback((info) => {
    if (startedAt.get() < 0) {
      startedAt.set(info.timestamp);
      clockBase.set(clock.get());
      lastStamp.set(info.timestamp);
    }
    const now = clockBase.get() + (info.timestamp - startedAt.get()) / 1000;
    const dt = Math.min(0.1, Math.max(0, (info.timestamp - lastStamp.get()) / 1000));
    lastStamp.set(info.timestamp);
    clock.set(now);
    if (reduced) {
      if (rmAt.get() >= 0 && now - rmAt.get() > RM_LENGTH) {
        rmAt.set(-1);
        scheduleOnRN(setRmRunning, false);
      }
      return;
    }
    const s = touch.get();
    const next = stepTouch(s, now, dt, autoPlay);
    if (next) touch.set(next);
    const beat = beatOf(next ?? s, now);
    const prev = lastBeat.get();
    if (beat >= 0 && prev >= 0) {
      if (prev < 2.05 && beat >= 2.05) scheduleOnRN(hitHaptic);
      if (prev < 3.7 && beat >= 3.7) scheduleOnRN(announceDone);
    }
    lastBeat.set(beat);
  }, false);
  const running = active && (reduced ? rmRunning : true);
  useEffect(() => {
    startedAt.set(-1);
    tick.setActive(running);
    return () => tick.setActive(false);
  }, [running, tick, startedAt]);

  const picture = useDerivedValue(() => {
    const recorder = Skia.PictureRecorder();
    const canvas = recorder.beginRecording(Skia.XYWHRect(0, 0, width, height));
    const c = clock.get();
    const frame = reduced ? reducedFrame(rmAt.get() >= 0 ? c - rmAt.get() : -1) : frameAt(touch.get(), c);
    const bg = Skia.Paint();
    bg.setColor(Skia.Color('#000000'));
    canvas.drawRect(Skia.XYWHRect(0, 0, width, height), bg);
    if (fonts) {
      drawHeader(canvas, layout, fonts, frame);
      drawCards(canvas, layout, fonts, frame.d);
    }
    drawYoyo(canvas, layout, yoyo, frame);
    drawFinger(canvas, layout, frame);
    return recorder.finishRecordingAsPicture();
  });

  /** Refresh without a pull (the accessibility action, and Reduce Motion's pull). */
  const startRefresh = useCallback(() => {
    if (reduced) {
      if (rmAt.get() >= 0) return;
      rmAt.set(clock.get());
      setRmRunning(true);
      return;
    }
    const c = clock.get();
    const s = takeOver(touch.get(), c);
    if (!canGrab(s, c)) return;
    touch.set(refresh(s, c));
  }, [reduced, rmAt, clock, touch]);

  const gesture = useMemo(
    () =>
      Gesture.Manual()
        .onTouchesDown((e, manager) => {
          if (touch.get().touching) return;
          manager.activate();
          pressY.set(e.allTouches[0].y);
          if (reduced) return;
          const c = clock.get();
          const s = takeOver(touch.get(), c);
          if (canGrab(s, c)) touch.set(grab(s, c));
          else touch.set(s);
        })
        .onTouchesMove((e) => {
          if (reduced) return;
          const s = touch.get();
          if (!s.touching) return;
          touch.set(drag(s, e.allTouches[0].y - pressY.get(), clock.get()));
        })
        .onTouchesUp((e, manager) => {
          if (e.numberOfTouches > 0) return;
          manager.end();
          if (reduced) {
            const dy = (e.changedTouches[0]?.y ?? pressY.get()) - pressY.get();
            if (dragD(dy, 0) > THRESHOLD) scheduleOnRN(startRefresh);
            return;
          }
          const c = clock.get();
          const s = touch.get();
          if (s.touching) touch.set(release(s, c));
          else touch.set(touched(s, c));
        })
        .onTouchesCancelled(() => {
          if (reduced) return;
          const s = touch.get();
          if (s.touching) touch.set(release(s, clock.get()));
        }),
    [reduced, touch, clock, pressY, startRefresh],
  );

  return (
    <View style={styles.root}>
      <StatusBar style="light" />
      <GestureDetector gesture={gesture}>
        <View style={StyleSheet.absoluteFill}>
          <Canvas style={StyleSheet.absoluteFill}>
            <Picture picture={picture} />
          </Canvas>
          <View
            accessible
            accessibilityRole="header"
            accessibilityLabel="Inbox"
            style={[styles.a11y, { left: layout.ox + 28, top: 64 * layout.fy, width: 120, height: 44 }]}
          />
          {/* The list for screen readers: each card carries the Refresh action (iOS would also
              hand a container's custom actions to every card, listing it twice). */}
          <View accessible={false} accessibilityRole="list" style={StyleSheet.absoluteFill} pointerEvents="none">
            {CARDS.map(([, title, meta], i) => (
              <View
                key={title}
                accessible
                accessibilityLabel={`${title}, ${meta}`}
                accessibilityActions={[{ name: 'refresh', label: 'Refresh' }]}
                onAccessibilityAction={(event) => {
                  if (event.nativeEvent.actionName === 'refresh') startRefresh();
                }}
                style={[
                  styles.a11y,
                  { left: layout.ox + CARD_X, top: cardTop(layout, i), width: CARD_W, height: CARD_H },
                ]}
              />
            ))}
          </View>
        </View>
      </GestureDetector>
    </View>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: '#000000' },
  a11y: { position: 'absolute' },
});
