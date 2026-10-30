import { Canvas, Picture, Skia, useFont, useImage } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useEffect, useMemo, useState } from 'react';
import { AppState, type LayoutChangeEvent, StyleSheet, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import { ReduceMotion, useDerivedValue, useFrameCallback, useReducedMotion, useSharedValue, withTiming } from 'react-native-reanimated';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';

import { drawScene, type Geo, type Live, makeRes, type Res } from './qjp-draw';
import {
  bearExtra,
  closeB,
  type Driver,
  frameAt,
  inPostcard,
  inRestA,
  jiggle,
  LIFT,
  LOOP,
  makeDriver,
  mix,
  PEAK,
  pressA,
  releaseA,
  stepDriver,
  T,
  touched,
  TURN_LIFTED,
} from './qjp-motion';
import { BEAR, CARD, REF_H, REF_W, SHEET } from './qjp-theme';
import { type Cue, useQjpAudio } from './use-qjp-audio';

/*
 QR Joy Postcard — the pickup sheet of an invented marketplace app that offers to turn an order's
 plain QR code into a postcard for whoever collects it.

 Look (the pin, a still): a dimmed "Orders" page; over it a pink sheet with a vinyl bear on a
 violet cushion sitting on a tilted white QR card, a blurred gift box and sparkles behind it, then
 "Share the joy", two numbered steps and a violet "Decorate QR code" button.

 Motion (ours): the button sinks, the card rises and straightens with the bear riding on it, and
 every dark module of the code lifts off and swirls once round the middle as a pink petal (over
 the bear), landing back in its own cell as a round dot, magenta to violet, while the postcard's
 pink spreads out from the middle under them. The corner squares round off with a playful turn, a
 heart lands in the middle (the code is error-correction H, so it still scans), sparkles fly out
 of it, a tulip sticker slaps onto the card's edge, the bear hops, says "for you" in a bubble that
 writes itself, waves twice, and a light runs over the card. Then everything goes home.

 autoPlay: LOOP = 9 s (timeline in qjp-motion.ts); frame 9 s equals frame 0 (the pin).
 Touch: tap the card or the button to decorate; drag the card to tilt it (the bear lags behind
 on a spring, the gift and sparkles move in parallax); tap the bear to make it hop and wave; on
 the postcard tap "Send postcard" for a happy shake, × to go back. 2 s after the last touch the
 loop resumes from where it is.
 Reduced motion: no loop; taps crossfade between the plain code and the postcard.
*/

/** previewTime pins the timeline at one moment (seconds), for still captures and debugging. */
type Props = { autoPlay?: boolean; previewTime?: number };

type Target = 0 | 1 | 2 | 3 | 4 | 5; // none, card, close, button in A, button on the postcard, bear

let debugState = '';

export function QrJoyPostcard({ autoPlay = false, previewTime }: Props) {
  const reduced = useReducedMotion();
  const auto = autoPlay && !reduced;
  const [size, setSize] = useState<{ width: number; height: number } | null>(null);
  const [active, setActive] = useState(AppState.currentState === 'active');
  useEffect(() => {
    const sub = AppState.addEventListener('change', (s) => setActive(s === 'active'));
    return () => sub.remove();
  }, []);

  const bear = useImage(require('./assets/bear-atlas.webp'));
  const cushion = useImage(require('./assets/cushion.webp'));
  const gift = useImage(require('./assets/gift.webp'));
  const tulips = useImage(require('./assets/tulips.webp'));
  const regular = useFont(require('./assets/fonts/Inter-Regular.ttf'), 100);
  const semi = useFont(require('./assets/fonts/Inter-SemiBold.ttf'), 100);
  const bold = useFont(require('./assets/fonts/Inter-Bold.ttf'), 100);
  const hand = useFont(require('./assets/fonts/CoveredByYourGrace.ttf'), 100);

  const res = useMemo<Res | null>(() => {
    if (!bear || !cushion || !gift || !tulips || !regular || !semi || !bold || !hand) return null;
    const faces = { regular: regular.getTypeface(), semi: semi.getTypeface(), bold: bold.getTypeface(), hand: hand.getTypeface() };
    if (!faces.regular || !faces.semi || !faces.bold || !faces.hand) return null;
    return makeRes({ regular: faces.regular, semi: faces.semi, bold: faces.bold, hand: faces.hand }, { bear, cushion, gift, tulips });
  }, [bear, cushion, gift, tulips, regular, semi, bold, hand]);

  const w = size?.width ?? REF_W;
  const h = size?.height ?? REF_H;
  const geo = useMemo<Geo>(() => {
    const s = w / REF_W;
    return { w, h, s, dy: h - REF_H * s };
  }, [w, h]);

  const play = useQjpAudio(!reduced);
  const cue = (c: Cue) => play(c);
  const buzz = (kind: number) => {
    if (process.env.EXPO_OS === 'web') return;
    if (kind === 2) Haptics.selectionAsync().catch(() => {});
    else Haptics.impactAsync(kind === 1 ? Haptics.ImpactFeedbackStyle.Medium : Haptics.ImpactFeedbackStyle.Light).catch(() => {});
  };

  const driver = useSharedValue<Driver>(makeDriver(reduced ? PEAK : 0));
  const target = useSharedValue<Target>(0);
  const clock = useSharedValue(0);
  const time = useSharedValue(reduced ? PEAK : 0);
  const live = useSharedValue<Live>({ amb: 0, tiltX: 0, tiltY: 0, lean: 0, cta: 0, hop: 0, squash: 0, wave: 0, jiggle: 0 });
  // Reduced motion: the two states crossfade (0 plain … 1 postcard) instead of playing the timeline.
  const fade = useSharedValue(1);
  const ready = res !== null && size !== null;

  useEffect(() => {
    const t0 = previewTime ?? (reduced ? PEAK : 0);
    driver.set(makeDriver(t0));
    clock.set(0);
    time.set(t0);
  }, [auto, reduced, ready, previewTime, driver, clock, time]);

  // Debug (development builds only): `globalThis.__qrJoy.at(2.6)` pins the timeline at a moment,
  // `.play()` lets it run again, `.park(t)` parks it for touch tests, `.state()` reads the driver.
  const debugT = useSharedValue(-1);
  useEffect(() => {
    if (!__DEV__) return;
    const g = globalThis as { __qrJoy?: unknown };
    g.__qrJoy = {
      at: (t: number) => debugT.set(t),
      play: () => debugT.set(-1),
      state: () => debugState,
      park: (t: number) =>
        scheduleOnUI(() => {
          const d = driver.get();
          d.t = t;
          d.mode = 2;
          d.last = clock.get() + 60;
        }),
    };
    return () => {
      delete g.__qrJoy;
    };
  }, [debugT, driver, clock]);
  const mirror = (s: string) => {
    debugState = s;
  };

  const frameCb = useFrameCallback((info) => {
    if (debugT.get() >= 0) {
      time.set(debugT.get());
      return;
    }
    const dt = Math.min((info.timeSincePreviousFrame ?? 16) / 1000, 0.05);
    const now = clock.get() + dt;
    clock.set(now);
    const d = driver.get();
    const before = d.t;
    const t = stepDriver(d, dt, now, auto);
    const b = bearExtra(d, now);
    if (__DEV__ && Math.floor(now * 4) !== Math.floor((now - dt) * 4)) {
      scheduleOnRN(mirror, `t=${t.toFixed(2)} mode=${d.mode} stop=${d.stop} tg=${target.get()} aim=${d.aimX.toFixed(2)} tilt=${d.tiltX.toFixed(2)} lean=${d.lean.toFixed(2)} bear=${d.bearAt.toFixed(1)}`);
    }
    live.set({ amb: d.amb, tiltX: d.tiltX, tiltY: d.tiltY, lean: d.lean, cta: d.ctaP, hop: b.hop, squash: b.squash, wave: b.wave, jiggle: jiggle(d, now) });
    time.set(t);
    // a finger started this: the moments answer with sound and a tap; autoPlay stays silent
    if (d.mode === 1 && d.rate > 0) {
      if (before < T.heartAt && t >= T.heartAt) {
        scheduleOnRN(cue, 'sparkle');
        scheduleOnRN(buzz, 2);
      }
      if (before < T.sticker[1] && t >= T.sticker[1]) {
        scheduleOnRN(cue, 'sticker');
        scheduleOnRN(buzz, 0);
      }
    }
  }, false);

  useEffect(() => {
    frameCb.setActive(ready && active && !reduced && previewTime === undefined);
    return () => frameCb.setActive(false);
  }, [ready, active, reduced, previewTime, frameCb]);

  const picture = useDerivedValue(() => {
    const recorder = Skia.PictureRecorder();
    const c = recorder.beginRecording(Skia.XYWHRect(0, 0, geo.w, geo.h));
    if (res && reduced) {
      const k = fade.get();
      const still = { amb: 0, tiltX: 0, tiltY: 0, lean: 0, cta: 0, hop: 0, squash: 0, wave: 0, jiggle: 0 };
      if (k < 0.999) drawScene(c, res, geo, frameAt(0), still);
      if (k > 0.001) {
        const p = Skia.Paint();
        p.setAlphaf(k);
        c.saveLayer(p);
        drawScene(c, res, geo, frameAt(PEAK), still);
        c.restore();
      }
    } else if (res) {
      drawScene(c, res, geo, frameAt(time.get()), live.get());
    } else c.drawColor(Skia.Color('#F6F6FA'));
    return recorder.finishRecordingAsPicture();
  }, [res, geo, reduced]);

  const onLayout = (e: LayoutChangeEvent) => {
    const { width, height } = e.nativeEvent.layout;
    if (!size || size.width !== width || size.height !== height) setSize({ width, height });
  };

  // ---- touch, in the reference space of the sheet (it stands on the bottom edge)
  const start = useSharedValue({ x: 0, y: 0 });
  const dragging = useSharedValue(0);

  /** The finger in the card's own space at the current lift (the card is turned). */
  const toCardAt = (x: number, y: number, lift: number) => {
    'worklet';
    const ang = mix(CARD.angle, TURN_LIFTED, Math.min(1, Math.max(0, lift)));
    const dx = x - CARD.cx;
    const dy = y - (CARD.cy - LIFT * lift);
    const cos = Math.cos(-ang);
    const sin = Math.sin(-ang);
    return { x: dx * cos - dy * sin + CARD.w / 2, y: dx * sin + dy * cos + CARD.h / 2 };
  };
  const inBox = (x: number, y: number, b: { x: number; y: number; w: number; h: number }, pad: number) => {
    'worklet';
    return x >= b.x - pad && x <= b.x + b.w + pad && y >= b.y - pad && y <= b.y + b.h + pad;
  };
  const toggleReduced = (toB: boolean) => {
    'worklet';
    fade.set(withTiming(toB ? 1 : 0, { duration: 220, reduceMotion: ReduceMotion.Never }));
  };

  const touch = Gesture.Pan()
    .minDistance(0)
    .enabled(previewTime === undefined)
    .onBegin((e) => {
      const d = driver.get();
      const now = clock.get();
      const x = e.x / geo.s;
      const y = (e.y - geo.dy) / geo.s;
      start.set({ x, y });
      dragging.set(0);
      const showingA = reduced ? fade.get() < 0.5 : inRestA(d.t);
      const showingB = reduced ? fade.get() >= 0.5 : inPostcard(d.t) || (d.t >= PEAK && d.t < T.back);
      const lift = frameAt(d.t).lift;
      const p = toCardAt(x, y, lift);
      const onBear = p.x > BEAR.x - 48 && p.x < BEAR.x + 48 && p.y > BEAR.y - BEAR.h + 8 && p.y < BEAR.y - 4;
      const onCard = p.x > -8 && p.x < CARD.w + 8 && p.y > -8 && y < SHEET.heroBottom;
      let tg: Target = 0;
      if (Math.hypot(x - SHEET.close.x, y - SHEET.close.y) < 24 && showingB) tg = 2;
      else if (inBox(x, y, SHEET.cta, 6)) tg = showingB ? 4 : showingA ? 3 : 0;
      else if (onBear && !reduced) tg = 5;
      else if (onCard) tg = 1;
      target.set(tg);
      if (reduced) return;
      touched(d, now);
      if (tg === 3) {
        pressA(d);
        d.ctaAim = 1;
      }
      if (tg === 4) d.ctaAim = 1;
    })
    .onUpdate((e) => {
      if (reduced) return;
      const tg = target.get();
      const d = driver.get();
      d.last = clock.get();
      if (tg !== 1 && tg !== 5) return;
      if (dragging.get() === 0 && Math.hypot(e.translationX, e.translationY) < 8) return;
      dragging.set(1);
      // the card follows the finger round its middle, as far as ±1
      d.aimX = Math.max(-1, Math.min(1, e.translationX / 110));
      d.aimY = Math.max(-1, Math.min(1, e.translationY / 140));
    })
    .onFinalize((e) => {
      const d = driver.get();
      const now = clock.get();
      const tg = target.get();
      const x = e.x / geo.s;
      const y = (e.y - geo.dy) / geo.s;
      const s0 = start.get();
      const tap = Math.hypot(x - s0.x, y - s0.y) < 12 && dragging.get() === 0;
      target.set(0);
      d.ctaAim = 0;
      d.aimX = 0;
      d.aimY = 0;
      if (reduced) {
        if (tap && (tg === 1 || tg === 3)) toggleReduced(true);
        else if (tap && tg === 2) toggleReduced(false);
        return;
      }
      if (tg === 3) {
        const on = tap && inBox(x, y, SHEET.cta, 12);
        releaseA(d, on);
        if (on) scheduleOnRN(cue, 'flip');
      } else if (tg === 1 && tap) {
        if (inRestA(d.t)) {
          pressA(d);
          d.t = T.commit;
          releaseA(d, true);
          scheduleOnRN(cue, 'flip');
          scheduleOnRN(buzz, 0);
        } else if (inPostcard(d.t)) {
          d.jiggleAt = now;
          scheduleOnRN(buzz, 0);
        }
      } else if (tg === 2 && tap) {
        if (closeB(d)) scheduleOnRN(buzz, 0);
      } else if (tg === 4 && tap) {
        d.jiggleAt = now;
        scheduleOnRN(cue, 'sparkle');
        scheduleOnRN(buzz, 1);
      } else if (tg === 5 && tap) {
        d.bearAt = now;
        scheduleOnRN(buzz, 0);
      }
      d.last = now;
    });

  const toggleForAccessibility = () => {
    scheduleOnUI(() => {
      const d = driver.get();
      if (reduced) {
        toggleReduced(fade.get() < 0.5);
        return;
      }
      d.last = clock.get();
      if (inRestA(d.t)) {
        d.t = T.commit;
        releaseA(d, true);
      } else if (!closeB(d)) {
        d.mode = 1;
        d.rate = 1;
        d.stop = d.t < PEAK ? PEAK : LOOP;
      }
    });
  };

  return (
    <View style={styles.screen} onLayout={onLayout}>
      <StatusBar style="dark" />
      <GestureDetector gesture={touch}>
        <View
          style={StyleSheet.absoluteFill}
          accessible
          accessibilityRole="button"
          accessibilityLabel="Share the joy. Decorate a plain QR code with a backdrop and warm words, then send it to whoever picks up the order."
          accessibilityHint="Decorates the QR code into a postcard, or turns it back when it is decorated."
          onAccessibilityTap={toggleForAccessibility}
        >
          {size ? (
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
  screen: { flex: 1, backgroundColor: '#7B7B7D' },
});
