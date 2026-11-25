import { useCallback, useEffect, useMemo, useState } from 'react';
import { AccessibilityInfo, AppState, StyleSheet, useWindowDimensions, View } from 'react-native';
import { Canvas, Picture, Skia, useFont, useImage } from '@shopify/react-native-skia';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import {
  ReduceMotion,
  useDerivedValue,
  useFrameCallback,
  useReducedMotion,
  useSharedValue,
  withTiming,
} from 'react-native-reanimated';
import { scheduleOnRN } from 'react-native-worklets';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';

import {
  BARLOW,
  INTER_MEDIUM,
  INTER_SEMIBOLD,
  PLUG,
  SOCKET,
  TITLE_SIZE,
  buttonHit,
  drawCopy,
  drawPlug,
  drawSocket,
  drawSpark,
  drawTitle,
  drawWall,
  makeLayout,
  plugHit,
} from './backOnlineArt';
import type { Fonts, Layout } from './backOnlineArt';
import {
  OFFLINE,
  ONLINE,
  PLUG_DRAG,
  SCREEN_SEAT,
  canGrab,
  drag,
  frameAt,
  grab,
  initialTouch,
  isOnline,
  release,
  stepTouch,
  takeOver,
  toggle,
  withTouching,
} from './backOnlineMotion';
import type { Frame } from './backOnlineMotion';

/*
 Back Online Plug — the offline state of an invented notes app. A white wall socket faces us on a
 dim slate-green wall under STILL / OFF / LINE, and a round white plug, seen from its back, waits
 beside it on its cable. It moves across onto the socket's axis and pushes straight in, bounces as
 it seats with a ring spark, the wall flashes vivid green and the title flips line by line to
 BACK / ON / LINE while the drafts sync; then it is pulled out toward us and back to its place.

 Loop (LOOP = 7 s): rest, swaying (0–1.0) → lines up (1.0–1.6) → pushes in (1.55–1.85) → seat
 bounce, spark, flash, flip (1.85–2.6) → syncing 1 → 3 of 3 (2.3–4.6) → pulled out (4.8–5.3) and
 back across (5.3–5.9) → rest (5.9–7.0, the t = 0 frame).
 Touch: tap the plug or Try again to plug it in; drag the plug toward the socket and release within
 40 pt to push it in. Online, tap the plug to pull it out. 2 s after the last touch it eases back
 and the loop restarts.
 Reduce Motion: no sway, move or spark; a 200 ms crossfade between the offline and online stills.
*/

function seatFeedback() {
  if (process.env.EXPO_OS !== 'web') {
    Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Medium).catch(() => {});
  }
  AccessibilityInfo.announceForAccessibility('Back online');
}

/** The two stills Reduce Motion crossfades between. */
const STILL_OFFLINE: Frame = {
  a: 1,
  z: 1,
  sway: 0,
  wall: OFFLINE,
  flip: 0,
  chip: 1,
  spark: 0,
};
const STILL_ONLINE: Frame = {
  a: 0,
  z: 0,
  sway: 0,
  wall: ONLINE,
  flip: 1,
  chip: 3,
  spark: 0,
};

type Props = {
  /** Plays the plug-in story on a loop of LOOP s. */
  autoPlay?: boolean;
};

export function BackOnlinePlug({ autoPlay = false }: Props) {
  const { width, height } = useWindowDimensions();
  const reduced = useReducedMotion();
  const [active, setActive] = useState(AppState.currentState === 'active');
  const [online, setOnline] = useState(false);
  useEffect(() => {
    const sub = AppState.addEventListener('change', (state) => setActive(state === 'active'));
    return () => sub.remove();
  }, []);

  const layout: Layout = useMemo(() => makeLayout(width, height), [width, height]);
  const plug = useImage(PLUG);
  const socket = useImage(SOCKET);
  const barlow = useFont(BARLOW, TITLE_SIZE);
  const interMedium = useFont(INTER_MEDIUM, 16);
  const interSemi = useFont(INTER_SEMIBOLD, 17);
  const fonts: Fonts | null = useMemo(() => {
    const semi = interSemi?.getTypeface();
    if (!barlow || !interMedium || !interSemi || !semi) return null;
    return { title: barlow, line: interMedium, button: interSemi, chip: Skia.Font(semi, 15) };
  }, [barlow, interMedium, interSemi]);

  const clock = useSharedValue(0);
  const startedAt = useSharedValue(-1);
  const clockBase = useSharedValue(0);
  const lastStamp = useSharedValue(0);
  const touch = useSharedValue(initialTouch());
  const wasOnline = useSharedValue(false);
  /** Reduce Motion: 0 offline still, 1 online still. */
  const fade = useSharedValue(0);
  const pressAt = useSharedValue(0);
  const pressX = useSharedValue(0);
  const pressY = useSharedValue(0);
  const moved = useSharedValue(0);
  const onPlug = useSharedValue(false);
  const onButton = useSharedValue(false);

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
    const s = touch.get();
    const next = stepTouch(s, now, dt, autoPlay);
    if (next) touch.set(next);
    const cur = next ?? s;
    const live = isOnline(cur, now) && (!cur.manual || now >= cur.vAt);
    if (live !== wasOnline.get()) {
      wasOnline.set(live);
      scheduleOnRN(setOnline, live);
      if (live && cur.manual) scheduleOnRN(seatFeedback);
    }
  }, false);
  const running = !reduced && active;
  useEffect(() => {
    startedAt.set(-1);
    tick.setActive(running);
    return () => tick.setActive(false);
  }, [running, tick, startedAt]);

  const picture = useDerivedValue(() => {
    const recorder = Skia.PictureRecorder();
    const canvas = recorder.beginRecording(Skia.XYWHRect(0, 0, width, height));
    const drawFrame = (frame: Frame) => {
      drawWall(canvas, layout, frame.wall, width, height);
      drawSocket(canvas, layout, socket);
      drawPlug(canvas, layout, plug, frame);
      drawSpark(canvas, layout, frame);
      if (fonts) {
        drawTitle(canvas, layout, fonts.title, frame.flip);
        drawCopy(canvas, layout, fonts, frame);
      }
    };
    if (reduced) {
      const f = fade.get();
      drawFrame(STILL_OFFLINE);
      if (f > 0) {
        const layer = Skia.Paint();
        layer.setAlphaf(f);
        canvas.saveLayer(layer);
        drawFrame(STILL_ONLINE);
        canvas.restore();
      }
    } else {
      drawFrame(frameAt(touch.get(), clock.get()));
    }
    return recorder.finishRecordingAsPicture();
  });

  /** Seat or pull out (the button's tap and the accessibility action). */
  const flipState = useCallback(() => {
    if (reduced) {
      const to = fade.get() < 0.5 ? 1 : 0;
      fade.set(withTiming(to, { duration: 200, reduceMotion: ReduceMotion.Never }));
      setOnline(to === 1);
      if (to === 1) seatFeedback();
      return;
    }
    const c = clock.get();
    touch.set(toggle(takeOver(touch.get(), c), c));
  }, [reduced, fade, clock, touch]);

  const gesture = useMemo(
    () =>
      Gesture.Manual()
        .onTouchesDown((e, manager) => {
          if (touch.get().touching) return;
          manager.activate();
          const p = e.allTouches[0];
          const c = clock.get();
          pressAt.set(c);
          pressX.set(p.x);
          pressY.set(p.y);
          moved.set(0);
          onButton.set(buttonHit(layout, p.x, p.y));
          if (reduced) {
            onPlug.set(plugHit(layout, fade.get() < 0.5 ? STILL_OFFLINE : STILL_ONLINE, p.x, p.y));
            return;
          }
          let s = takeOver(touch.get(), c);
          const hit = plugHit(layout, frameAt(s, c), p.x, p.y);
          onPlug.set(hit);
          if (hit && canGrab(s, c)) s = grab(s, c);
          touch.set(withTouching(s, true, c));
        })
        .onTouchesMove((e) => {
          const p = e.allTouches[0];
          const dx = p.x - pressX.get();
          const dy = p.y - pressY.get();
          moved.set(Math.max(moved.get(), Math.hypot(dx, dy)));
          if (reduced || !onPlug.get()) return;
          const s = touch.get();
          if (s.pk !== PLUG_DRAG) return;
          touch.set(drag(s, dx, dy, clock.get()));
        })
        .onTouchesUp((e, manager) => {
          if (e.numberOfTouches > 0) return;
          manager.end();
          const c = clock.get();
          const tap = c - pressAt.get() < 0.3 && moved.get() < 10;
          if (reduced) {
            if (tap && ((onButton.get() && fade.get() < 0.5) || onPlug.get())) scheduleOnRN(flipState);
            return;
          }
          let s = touch.get();
          if (onPlug.get() && s.pk === PLUG_DRAG) s = release(s, c, tap);
          else if (tap && onPlug.get()) s = toggle(s, c);
          else if (tap && onButton.get() && s.vk !== SCREEN_SEAT) s = toggle(s, c);
          touch.set(withTouching(s, false, c));
          onPlug.set(false);
        })
        .onTouchesCancelled(() => {
          if (reduced) return;
          const c = clock.get();
          let s = touch.get();
          if (onPlug.get() && s.pk === PLUG_DRAG) s = release(s, c, false);
          touch.set(withTouching(s, false, c));
          onPlug.set(false);
        }),
    [reduced, layout, touch, clock, fade, pressAt, pressX, pressY, moved, onPlug, onButton, flipState],
  );

  const title = online ? 'Back online.' : 'Still offline.';
  const line = online ? 'Your 3 drafts are syncing now.' : 'Plug back in to sync 3 drafts.';

  return (
    <View style={[styles.root, { backgroundColor: rgbColorHex(online ? ONLINE : OFFLINE) }]}>
      <StatusBar style="light" />
      <GestureDetector gesture={gesture}>
        <View style={StyleSheet.absoluteFill}>
          <Canvas style={StyleSheet.absoluteFill}>
            <Picture picture={picture} />
          </Canvas>
          <View
            accessible
            accessibilityRole="header"
            accessibilityLabel={`${title} ${line}`}
            style={[styles.a11y, { left: layout.ox + 30, top: 100 * layout.fy, width: 300, height: 300 * layout.fy }]}
          />
          <View
            accessible
            accessibilityRole="button"
            accessibilityLabel={online ? 'Disconnect' : 'Reconnect'}
            accessibilityValue={{ text: online ? 'Online' : 'Offline' }}
            accessibilityActions={[{ name: 'activate' }]}
            onAccessibilityAction={flipState}
            style={[styles.a11y, { left: layout.ox + 32, top: 772 * layout.fy, width: 329, height: 58 }]}
          />
        </View>
      </GestureDetector>
    </View>
  );
}

function rgbColorHex(c: [number, number, number]) {
  return `rgb(${c[0]},${c[1]},${c[2]})`;
}

const styles = StyleSheet.create({
  root: { flex: 1 },
  a11y: { position: 'absolute' },
});
