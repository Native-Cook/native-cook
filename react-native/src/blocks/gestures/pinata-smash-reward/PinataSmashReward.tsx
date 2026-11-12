import { Canvas, Picture, Skia, useFont, useImage } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useEffect, useMemo, useState } from 'react';
import { AppState, type LayoutChangeEvent, StyleSheet, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import { useDerivedValue, useFrameCallback, useReducedMotion, useSharedValue } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';

import { type ArtLayout, drawScene, makeRes, type View as ViewGeo } from './psr-draw';
import {
  applyDriver,
  ATLAS_NAMES,
  clamp01,
  type Driver,
  easeOutCubic,
  frameAt,
  type Geo,
  K_GREEN,
  K_LOLLY,
  K_MAGENTA,
  K_MINT,
  K_STAR,
  K_BONBON,
  M,
  makeDriver,
  SCRIPT_STORY,
  stepDriver,
  type Story,
  takeOver,
  tapAt,
} from './psr-motion';
import { C, L, REF_H, REF_TOP_INSET, REF_W } from './psr-theme';

/*
 Piñata Smash Reward — the daily reward of an invented marketplace: a glossy toy piñata hangs on a
 lavender ribbon on a white page, wearing a string of fairy lights. "Keep tapping" sits on its belly.

 Reconstruction of Oleg Meo's pin (Pinterest 622341242302766438): the rest frame, the label → bar
 → "Come back tomorrow" pill, the ripple, the hop up the ribbon, the jolts with sweets and tokens,
 and the burst of the flank keep the pin's composition and timing. The character is our own (no
 saddle, short round ears, a pink mane). Ours, in the pin's language: a body with weight (ribbon
 and body as two lagging angles, a yaw mesh warp, hit-stop, squash, a lagging tail), cracks that
 build, a burst with a flash, a shock ring and half a second of slow motion, sweets that pile up on
 the floor, bulbs that light the body and pass a wave along the wire, tokens that bank into a
 balance chip, and a way back (the burst piñata is hoisted away, a fresh one drops in).

 autoPlay: LOOP = 8.4 s (timeline in psr-motion.ts); the frame at LOOP − 1/60 equals t = 0.
 Touch: tap the piñata to hit it where the finger lands (12 hits burst it; the first after a rest
 hops), drag it to swing it on its ribbon (released, it swings as a pendulum), tap it after the
 burst for a small wobble. 2 s after the last touch it goes back to rest and the loop restarts.
 Reduced motion: the rest frame and no loop; taps step the bar, the 12th shows the burst still.
*/

type Props = { autoPlay?: boolean };

const BODY = require('./assets/pinata-body.png');
const BURST_IMG = require('./assets/pinata-body-burst.png');
const CRACK1 = require('./assets/pinata-crack-1.png');
const CRACK2 = require('./assets/pinata-crack-2.png');
const TAIL = require('./assets/pinata-tail.png');
const ATLAS = require('./assets/loot-atlas.png');
const ATLAS_JSON = require('./assets/loot-atlas.json') as Record<string, { x: number; y: number; w: number; h: number; cx: number; cy: number }>;
const LAYOUT_JSON = require('./assets/layout.json') as {
  figure_box: { x: number; y: number; w: number; h: number };
  ribbon_attach_point: { x: number; y: number };
  flank_center: { x: number; y: number };
  tail_root: { x: number; y: number };
  tail_box: { x: number; y: number; w: number; h: number };
  bulbs: { center: { x: number; y: number }; radius: number; color: string }[];
};

const ART: ArtLayout = {
  figure: LAYOUT_JSON.figure_box,
  attach: LAYOUT_JSON.ribbon_attach_point,
  flank: LAYOUT_JSON.flank_center,
  tailRoot: LAYOUT_JSON.tail_root,
  tailBox: LAYOUT_JSON.tail_box,
  bulbs: LAYOUT_JSON.bulbs.map((b) => ({ x: b.center.x, y: b.center.y, r: b.radius, color: b.color })),
};

const EMPTY: Story = { hits: [], clear: -1, clearKind: 0 };
/** Reduced motion: the six sweets that fade in under the burst piñata. */
const RM_SWEETS = [
  { k: K_GREEN, x: 120, y: 820, rot: 0.3 },
  { k: K_STAR, x: 170, y: 826, rot: -0.2 },
  { k: K_MAGENTA, x: 214, y: 818, rot: -0.4 },
  { k: K_MINT, x: 256, y: 828, rot: 0 },
  { k: K_LOLLY, x: 292, y: 812, rot: 0.5 },
  { k: K_BONBON, x: 330, y: 828, rot: 0 },
];
const RM_TAPS = 12;

export function PinataSmashReward({ autoPlay = false }: Props) {
  const reduced = useReducedMotion();
  const auto = autoPlay && !reduced;
  const insets = useSafeAreaInsets();
  const [size, setSize] = useState<{ width: number; height: number } | null>(null);
  const [active, setActive] = useState(AppState.currentState === 'active');
  useEffect(() => {
    const sub = AppState.addEventListener('change', (s) => setActive(s === 'active'));
    return () => sub.remove();
  }, []);

  const body = useImage(BODY);
  const burst = useImage(BURST_IMG);
  const crack1 = useImage(CRACK1);
  const crack2 = useImage(CRACK2);
  const tail = useImage(TAIL);
  const atlas = useImage(ATLAS);
  const label = useFont(require('./assets/fonts/Onest-Medium.ttf'), L.font);
  const title = useFont(require('./assets/fonts/Onest-SemiBold.ttf'), L.title.size);
  const chipFont = useFont(require('./assets/fonts/Onest-SemiBold.ttf'), L.chip.size);

  const res = useMemo(() => {
    if (!body || !burst || !tail || !atlas || !label || !title || !chipFont) return null;
    return makeRes({ body, burst, crack1, crack2, tail, atlas, atlasJson: ATLAS_JSON, names: ATLAS_NAMES, art: ART, label, title, chipFont });
  }, [body, burst, crack1, crack2, tail, atlas, label, title, chipFont]);

  const w = size?.width ?? REF_W;
  const h = size?.height ?? REF_H;
  const view = useMemo<ViewGeo>(() => {
    const s = w / REF_W;
    return { s, w, h, dy: (h / s - REF_H) / 2, top: insets.top / s - REF_TOP_INSET };
  }, [w, h, insets.top]);

  // Points of the art at rest, in stage space, for the timeline.
  const geo = useMemo<Geo | null>(() => {
    if (!res) return null;
    const P = (x: number, y: number) => ({ x: res.ox + x * res.k, y: res.oy + y * res.k });
    const ch = L.chip;
    const cw = ch.padL + ch.coin * 2 + ch.gap + res.chipW + ch.padR;
    return {
      chipX: REF_W - ch.right - cw + ch.padL + ch.coin,
      chipY: ch.y + ch.h / 2 + view.top - view.dy,
      bulbs: ART.bulbs.map((b) => P(b.x, b.y)),
      flank: P(ART.flank.x, ART.flank.y),
      body: { x: res.cx, y: res.cy },
      attach: P(ART.attach.x, ART.attach.y),
    };
  }, [res, view]);

  const buzz = (kind: number) => {
    if (process.env.EXPO_OS === 'web') return;
    if (kind === 3) {
      Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Heavy).catch(() => {});
      setTimeout(() => Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success).catch(() => {}), 120);
    } else if (kind === 4) Haptics.notificationAsync(Haptics.NotificationFeedbackType.Warning).catch(() => {});
    else if (kind === 2) Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Medium).catch(() => {});
    else if (kind === 1) Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
  };

  const driver = useSharedValue<Driver>(makeDriver());
  const tick = useSharedValue(0);
  const clock = useSharedValue(0);
  // reduced motion: taps so far and when the last one landed (clock time)
  const rm = useSharedValue({ n: 0, at: -10, burstAt: -10 });
  const ready = res !== null && geo !== null && size !== null;

  // Debug (development builds only): the `globalThis.__psr` hooks are set up below the touch handlers.
  const pinT = useSharedValue(-1);

  useEffect(() => {
    driver.set(makeDriver());
  }, [auto, driver]);

  // Touch: tap = a hit where the finger lands; drag (after 8 pt) = hold it on its ribbon.
  // Points are in the reference space (pt of the 402 × 874 stage).
  const start = useSharedValue({ x: 0, y: 0, t: 0, on: 0, drag: 0 });
  const onPinata = (x: number, y: number) => {
    'worklet';
    if (!geo) return false;
    const d = driver.get();
    const drawn = d.lastAx !== 0 || d.lastAy !== 0;
    const ox = drawn ? d.lastAx - geo.attach.x : 0;
    const oy = drawn ? d.lastAy - geo.attach.y : 0;
    const F = L.figure;
    return x >= F.x + ox - 16 && x <= F.x + F.w + ox + 16 && y >= F.y + oy - 16 && y <= F.y + F.h + oy + 16;
  };
  const touchBegin = (x: number, y: number) => {
    'worklet';
    const on = onPinata(x, y) ? 1 : 0;
    start.set({ x, y, t: clock.get(), on, drag: 0 });
    if (reduced || !on) return;
    const d = driver.get();
    takeOver(d);
    d.last = d.t;
    d.pressAim = 1;
  };
  const touchMove = (x: number, y: number) => {
    'worklet';
    const s0 = start.get();
    if (reduced || !s0.on) return;
    const d = driver.get();
    d.last = d.t;
    if (!s0.drag && Math.hypot(x - s0.x, y - s0.y) > 8) {
      start.set({ ...s0, drag: 1 });
      d.drag = 1;
      d.pressAim = 0;
      d.gx = d.ox - s0.x;
      d.gy = d.oy - s0.y;
    }
    if (d.drag) {
      d.fx = x;
      d.fy = y;
    }
  };
  const touchEnd = (vx: number, vy: number) => {
    'worklet';
    const s0 = start.get();
    const d = driver.get();
    d.pressAim = 0;
    if (!s0.on) return;
    if (s0.drag) {
      // released: it swings with the finger's velocity, capped so a hard flick keeps it on screen
      const v = Math.hypot(vx, vy);
      const k = v > M.releaseMax ? M.releaseMax / v : 1;
      d.drag = 0;
      d.vx = vx * k;
      d.vy = vy * k;
      d.last = d.t;
      return;
    }
    if (clock.get() - s0.t > 0.35) return;
    if (reduced) {
      const r = rm.get();
      if (r.burstAt > -10) return;
      const n = r.n + 1;
      rm.set({ n, at: clock.get(), burstAt: n >= RM_TAPS ? clock.get() : -10 });
      scheduleOnRN(buzz, n >= RM_TAPS ? 3 : 1);
      return;
    }
    if (!geo) return;
    const kind = tapAt(d, s0.x, s0.y, geo);
    if (kind > 0) scheduleOnRN(buzz, kind);
  };
  const touch = Gesture.Pan()
    .minDistance(0)
    .onBegin((e) => touchBegin(e.x / view.s, e.y / view.s - view.dy))
    .onUpdate((e) => touchMove(e.x / view.s, e.y / view.s - view.dy))
    .onFinalize((e) => touchEnd(e.velocityX / view.s, e.velocityY / view.s));

  // Debug (development builds only): `globalThis.__psr.pin(t)` freezes the loop at t, `.play()` resumes;
  // `.hit(x, y)` taps at a point (pt of the 402 × 874 stage) and `.drag(dx, dy, vx, vy, holdMs)` grabs the
  // belly, moves it by (dx, dy) over 300 ms, holds, then lets go with (vx, vy) pt/s, through the same
  // handlers as the finger (for checks when the simulator cannot be touched).
  useEffect(() => {
    if (!__DEV__ || !geo) return;
    const g = globalThis as { __psr?: unknown };
    const bx = geo.body.x;
    const by = geo.body.y + 10;
    g.__psr = {
      pin: (t: number) => pinT.set(t),
      play: () => pinT.set(-1),
      hit: (x: number, y: number) =>
        scheduleOnUI(() => {
          touchBegin(x, y);
          touchEnd(0, 0);
        }),
      drag: (dx: number, dy: number, vx = 0, vy = 0, holdMs = 600) => {
        scheduleOnUI(() => touchBegin(bx, by));
        for (let i = 1; i <= 6; i++) setTimeout(() => scheduleOnUI(() => touchMove(bx + (dx * i) / 6, by + (dy * i) / 6)), i * 50);
        setTimeout(() => scheduleOnUI(() => touchEnd(vx, vy)), 300 + holdMs);
      },
    };
    return () => {
      delete g.__psr;
    };
    // the handlers read only shared values and `geo`, so the hooks need refreshing only when geo changes
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [pinT, geo]);

  const frameCb = useFrameCallback((info) => {
    const dt = Math.min((info.timeSincePreviousFrame ?? 16) / 1000, 0.05);
    clock.set(clock.get() + dt);
    if (reduced) {
      const r = rm.get();
      const now = clock.get();
      if (now - r.at < 0.4 || now - r.burstAt < 0.4) tick.set(tick.get() + 1);
      return;
    }
    const d = driver.get();
    if (__DEV__ && pinT.get() >= 0) {
      d.mode = 0;
      d.loopT = pinT.get();
    } else stepDriver(d, dt, auto);
    tick.set(tick.get() + 1);
  }, false);

  useEffect(() => {
    frameCb.setActive(ready && active);
    return () => frameCb.setActive(false);
  }, [ready, active, frameCb]);

  const picture = useDerivedValue(() => {
    tick.get();
    const recorder = Skia.PictureRecorder();
    const c = recorder.beginRecording(Skia.XYWHRect(0, 0, view.w, view.h));
    if (!res || !geo) {
      c.drawColor(Skia.Color(C.page));
      return recorder.finishRecordingAsPicture();
    }
    const d = driver.get();
    if (reduced) {
      const f = frameAt(0, EMPTY, geo, 0);
      const r = rm.get();
      const now = clock.get();
      const n = Math.min(r.n, RM_TAPS);
      f.fill = n / RM_TAPS;
      const done = r.burstAt > -10;
      f.wL = n === 0 ? 1 : 0;
      f.wB = n > 0 && !done ? 1 : 0;
      f.wD = done ? 1 : 0;
      f.pillW = done ? 172.5 : n > 0 ? 120 : 186;
      f.bloom = 0.25 * (1 - clamp01((now - r.at) / 0.12));
      f.bloomX = geo.body.x;
      f.bloomY = geo.body.y;
      if (done) {
        const k = easeOutCubic(clamp01((now - r.burstAt) / 0.25));
        f.burst = k;
        f.doneText = k;
        for (const s of RM_SWEETS) f.loot.push({ k: s.k, x: s.x, y: s.y, rot: s.rot, s: 1, a: k, sx: 1 });
      }
      drawScene(c, res, f, view, 1);
      d.lastAx = f.ax;
      d.lastAy = f.ay;
      return recorder.finishRecordingAsPicture();
    }
    const live = d.mode === 1;
    const t = live ? d.t : d.loopT;
    const f = frameAt(t, live ? d.story : SCRIPT_STORY, geo, t);
    if (d.blendPending) {
      // back to autoPlay: ease out the difference between the live pose and the loop's
      d.blendRoll = d.lastRoll - f.roll;
      d.blendX = d.lastAx - f.ax;
      d.blendY = d.lastAy - f.ay;
      d.blendPending = 0;
    }
    applyDriver(f, d);
    drawScene(c, res, f, view, 1);
    d.lastAx = f.ax;
    d.lastAy = f.ay;
    d.lastRoll = f.roll;
    return recorder.finishRecordingAsPicture();
  }, [res, geo, view, reduced]);

  const onLayout = (e: LayoutChangeEvent) => {
    const { width, height } = e.nativeEvent.layout;
    if (!size || size.width !== width || size.height !== height) setSize({ width, height });
  };

  return (
    <View style={styles.screen} onLayout={onLayout}>
      <StatusBar style="dark" />
      <GestureDetector gesture={touch}>
        <View
          style={StyleSheet.absoluteFill}
          accessible
          accessibilityRole="button"
          accessibilityLabel="Daily piñata. Keep tapping the piñata to break it open."
          accessibilityHint="Hits the piñata."
          onAccessibilityTap={() => {
            if (!geo || reduced) return;
            scheduleOnUI(() => {
              tapAt(driver.get(), geo.body.x, geo.body.y, geo);
            });
          }}
        >
          {ready ? (
            <Canvas style={StyleSheet.absoluteFill}>
              <Picture picture={picture} />
            </Canvas>
          ) : null}
        </View>
      </GestureDetector>
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: C.page },
});
