import { Canvas, FilterMode, MipmapMode, Picture, Skia, type SkSurface, TileMode, useFont, useImage } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useEffect, useMemo, useState } from 'react';
import { AppState, type LayoutChangeEvent, PixelRatio, StyleSheet, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import { useDerivedValue, useFrameCallback, useReducedMotion, useSharedValue } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';

import { cardIcon, drawSoft, drawSolid, drawType, type Frame, frameAt, makeGeo, makeVialPaths, type Res } from './pvu-draw';
import {
  ANCHOR,
  type Driver,
  dropFile,
  EV,
  LOOP,
  makeDriver,
  MODE,
  MOUTH,
  NEAR_R,
  popGem,
  recorkGem,
  stepDriver,
  T,
} from './pvu-motion';
import { LIQUID_SKSL, POST_SKSL, SMOKE_SKSL } from './pvu-shaders';
import { C, LAYOUT, REF_W } from './pvu-theme';
import { type Cue, usePvuAudio } from './use-pvu-audio';

/*
 Potion Vial Upload — the drop zone of an invented spell-pack app ("Grimoire"), drawn as pixel art.

 Look (the reference video, a desktop drop zone): a glass vial lying on its side in the dark, a
 brass-and-steel stopper with an emerald on a gold chain, green smoke, a black widow on the glass,
 a pixel font. Drag a file over it and the emerald pops off and mist pours out ("Release to drop
 it in"); drop it and the potion fills the vial while the spider rides the liquid's edge; at
 100 % the emerald bursts off in sparks and swings on its chain. On the phone the file waits on a
 card at the bottom and the finger carries it up to the vial.

 Ours: the mobile composition (header, file card, the file's flight from the card), the fade that
 closes the loop and the touch handling. From the drop on, the autoplay is the recording's sequence,
 timed at 30 fps: the file slips into the mouth, the emerald snaps home, a green cloud streams down
 the tube, the potion fills with the recording's counter, sparks fountain out at 100 %, the
 emerald swings and the spider climbs down its chain.

 autoPlay: LOOP = 12 s (timeline in pvu-motion.ts); frame 12 s equals frame 0.
 Touch: drag the file from its card to the vial (near the mouth the emerald pops; let go there to
 upload, anywhere else and it flies back); grab the hanging emerald and swing it; tap the vial when
 the upload is done to replay; poke the spider. 2 s after the last touch the loop resumes.
 Reduced motion: no loop; the finished upload shows still.
*/

const liquidFx = Skia.RuntimeEffect.Make(LIQUID_SKSL);
const smokeFx = Skia.RuntimeEffect.Make(SMOKE_SKSL);
const postFx = Skia.RuntimeEffect.Make(POST_SKSL);
const PATHS = makeVialPaths();
let debugState = '';

type Props = { autoPlay?: boolean; previewTime?: number };
type Target = 0 | 1 | 2 | 3 | 4; // none, file, gem, spider, vial

export function PotionVialUpload({ autoPlay = false, previewTime }: Props) {
  const reduced = useReducedMotion();
  const auto = autoPlay && !reduced;
  const insets = useSafeAreaInsets();
  const [size, setSize] = useState<{ width: number; height: number } | null>(null);
  const [active, setActive] = useState(AppState.currentState === 'active');
  useEffect(() => {
    const sub = AppState.addEventListener('change', (s) => setActive(s === 'active'));
    return () => sub.remove();
  }, []);

  const bold = useFont(require('./assets/fonts/PixelifySans-Bold.ttf'), LAYOUT.digit);
  const boldSmall = useFont(require('./assets/fonts/PixelifySans-Bold.ttf'), 17);
  const regular = useFont(require('./assets/fonts/PixelifySans-Regular.ttf'), LAYOUT.sub);
  const medium = useFont(require('./assets/fonts/PixelifySans-Medium.ttf'), 15);
  const small = useFont(require('./assets/fonts/PixelifySans-Regular.ttf'), 12);
  const title = useFont(require('./assets/fonts/PixelifySans-Bold.ttf'), 26);
  const fileImg = useImage(require('./assets/sprites/pvu-file.png'));

  const res = useMemo<Res | null>(() => {
    if (!bold || !boldSmall || !regular || !medium || !small || !title || !fileImg || !liquidFx || !smokeFx) return null;
    return {
      liquid: liquidFx,
      smoke: smokeFx,
      outer: PATHS.outer,
      inner: PATHS.inner,
      bold,
      boldSmall,
      regular,
      medium,
      small,
      title,
      file: fileImg,
    };
  }, [bold, boldSmall, regular, medium, small, title, fileImg]);

  const geo = useMemo(
    () => makeGeo(size?.width ?? REF_W, size?.height ?? 874, insets.top, insets.bottom),
    [size, insets.top, insets.bottom],
  );

  const play = usePvuAudio(!reduced);
  const cue = (c: Cue) => play(c);
  const buzz = (kind: number) => {
    if (process.env.EXPO_OS === 'web') return;
    if (kind === 3) Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success).catch(() => {});
    else if (kind === 2) Haptics.selectionAsync().catch(() => {});
    else
      Haptics.impactAsync(kind === 1 ? Haptics.ImpactFeedbackStyle.Medium : Haptics.ImpactFeedbackStyle.Light).catch(
        () => {},
      );
  };

  const t0 = previewTime ?? (reduced ? T.still : 0);
  const driver = useSharedValue<Driver>(makeDriver(t0));
  const clock = useSharedValue(0);
  const tick = useSharedValue(0); // bumps every frame so the pictures redraw
  const ready = res !== null && size !== null;

  useEffect(() => {
    driver.set(makeDriver(previewTime ?? (reduced ? T.still : 0)));
    clock.set(0);
    tick.set(0);
  }, [auto, reduced, ready, previewTime, driver, clock, tick]);

  // Debug (development builds only): `globalThis.__potionVial.at(4.2)` pins the timeline,
  // `.play()` lets it run, `.state()` reads the driver.
  const debugT = useSharedValue(-1);
  useEffect(() => {
    if (!__DEV__) return;
    const g = globalThis as { __potionVial?: unknown };
    g.__potionVial = {
      at: (t: number) => debugT.set(t),
      play: () => debugT.set(-1),
      state: () => debugState,
      // park at a loop time, with autoPlay held off for a minute (touch tests)
      park: (t: number) =>
        scheduleOnUI(() => {
          const d = driver.get();
          d.t = t;
          d.mode = MODE.hold;
          d.last = clock.get() + 60;
          if (t >= T.done && t < T.recork2 && d.gem.corked === 1) popGem(d.gem, 1);
        }),
    };
    return () => {
      delete g.__potionVial;
    };
  }, [debugT, driver, clock]);
  const mirror = (s: string) => {
    debugState = s;
  };

  const onEvent = (ev: number, withSound: boolean) => {
    if (!withSound) return;
    if (ev === EV.pop) {
      cue('pop');
      buzz(0);
    } else if (ev === EV.gulp) {
      cue('gulp');
      buzz(1);
    } else if (ev === EV.clink) cue('clink');
    else if (ev === EV.chime) {
      cue('chime');
      buzz(3);
    }
  };

  const frameCb = useFrameCallback((info) => {
    const dt = Math.min((info.timeSincePreviousFrame ?? 16) / 1000, 0.05);
    const now = clock.get() + dt;
    clock.set(now);
    const d = driver.get();
    if (debugT.get() >= 0) {
      // pinned: replay the loop from its start in fixed 60 Hz steps up to the pinned time, then hold
      // (the Swift port's debug hook replays the same way, so pinned frames compare one to one)
      const target = debugT.get() % LOOP;
      const r = makeDriver(0);
      let n = 0;
      while (n < target) {
        const h = Math.min(1 / 60, target - n);
        n += h;
        stepDriver(r, h, n, true);
      }
      driver.set(r);
      debugT.set(-2);
      tick.set(tick.get() + 1);
      return;
    }
    if (debugT.get() === -2) return;
    // a file held by the finger: the emerald answers how close it is
    if (d.mode === MODE.file && d.fileX === d.fileX) {
      const dist = Math.hypot(d.fileX - MOUTH.x, d.fileY - MOUTH.y);
      if (dist < NEAR_R && d.gem.corked === 1 && d.gem.homeAt < 0) {
        popGem(d.gem, 0.8);
        scheduleOnRN(onEvent, EV.pop, true);
      } else if (dist > NEAR_R * 1.25 && d.gem.corked === 0) {
        recorkGem(d.gem, now);
        scheduleOnRN(onEvent, EV.clink, true);
      }
    }
    if (d.fileBack < 1) d.fileBack = Math.min(1, (now - d.fileBackAt) / 0.5);
    const ev = stepDriver(d, dt, now, auto);
    if (ev !== EV.none) scheduleOnRN(onEvent, ev, d.sound === 1);
    if (__DEV__ && Math.floor(now * 4) !== Math.floor((now - dt) * 4)) {
      scheduleOnRN(mirror, `t=${d.t.toFixed(2)} mode=${d.mode} corked=${d.gem.corked} gem=${d.gem.x.toFixed(0)},${d.gem.y.toFixed(0)}`);
    }
    tick.set(tick.get() + 1);
  }, false);

  useEffect(() => {
    frameCb.setActive(ready && active && (!reduced || false));
    return () => frameCb.setActive(false);
  }, [ready, active, reduced, frameCb]);

  const frame = useDerivedValue<Frame | null>(() => {
    tick.get();
    if (!res) return null;
    const d = driver.get();
    return frameAt(d.t, d, clock.get(), geo, reduced);
  }, [res, geo, reduced]);

  // The art is drawn at one texel per art pixel (4 device pixels) into an offscreen image, then
  // blown up with hard edges and dithered light by the post shader.
  const pd = PixelRatio.get();
  const lw = Math.max(1, Math.ceil((geo.w * pd) / 4));
  const lh = Math.max(1, Math.ceil((geo.h * pd) / 4));
  const gfx = useSharedValue<{ soft: SkSurface | null; solid: SkSurface | null; w: number; h: number }>({
    soft: null,
    solid: null,
    w: 0,
    h: 0,
  });
  const art = useDerivedValue(() => {
    tick.get();
    const rec = Skia.PictureRecorder();
    const out = rec.beginRecording(Skia.XYWHRect(0, 0, geo.w, geo.h));
    const f = frame.get();
    const holder = gfx.get();
    // the first run of a derived value is on the JS thread: draw nothing there
    const onUI = (globalThis as { _WORKLET?: boolean })._WORKLET === true;
    if (onUI && (holder.w !== lw || holder.h !== lh || !holder.soft || !holder.solid)) {
      holder.soft = Skia.Surface.MakeOffscreen(lw, lh);
      holder.solid = Skia.Surface.MakeOffscreen(lw, lh);
      holder.w = lw;
      holder.h = lh;
    }
    const soft = onUI ? holder.soft : null;
    const solid = onUI ? holder.solid : null;
    if (res && f && soft && solid && postFx) {
      const a = soft.getCanvas();
      a.save();
      a.scale(pd / 4, pd / 4);
      drawSoft(a, res, geo, f);
      a.restore();
      soft.flush();
      const b = solid.getCanvas();
      b.clear(Skia.Color('transparent'));
      b.save();
      b.scale(pd / 4, pd / 4);
      drawSolid(b, res, geo, f);
      b.restore();
      solid.flush();
      const softImg = soft.makeImageSnapshot();
      const solidImg = solid.makeImageSnapshot();
      const p = Skia.Paint();
      p.setShader(
        postFx.makeShaderWithChildren(
          [4 / pd, 9, 10 / 255, 8 / 255, 13 / 255],
          [
            softImg.makeShaderOptions(TileMode.Clamp, TileMode.Clamp, FilterMode.Nearest, MipmapMode.None),
            solidImg.makeShaderOptions(TileMode.Clamp, TileMode.Clamp, FilterMode.Nearest, MipmapMode.None),
          ],
        ),
      );
      out.drawRect(Skia.XYWHRect(0, 0, geo.w, geo.h), p);
    } else out.drawColor(Skia.Color(C.page));
    return rec.finishRecordingAsPicture();
  }, [res, geo, lw, lh, pd]);

  const type = useDerivedValue(() => {
    const rec = Skia.PictureRecorder();
    const c = rec.beginRecording(Skia.XYWHRect(0, 0, geo.w, geo.h));
    const f = frame.get();
    if (res && f) drawType(c, res, geo, f);
    return rec.finishRecordingAsPicture();
  }, [res, geo]);

  const onLayout = (e: LayoutChangeEvent) => {
    const { width, height } = e.nativeEvent.layout;
    if (!size || size.width !== width || size.height !== height) setSize({ width, height });
  };

  // ---------------------------------------------------------------- touch
  const target = useSharedValue<Target>(0);
  const start = useSharedValue({ x: 0, y: 0, at: 0 });
  const toLocal = (x: number, y: number) => {
    'worklet';
    return { x: (x - geo.ox) / geo.s, y: (y - geo.cy) / geo.s };
  };

  const touch = Gesture.Pan()
    .minDistance(0)
    .enabled(previewTime === undefined && !reduced)
    .onBegin((e) => {
      const d = driver.get();
      const now = clock.get();
      const p = toLocal(e.x, e.y);
      start.set({ x: p.x, y: p.y, at: now });
      const f = frame.get();
      let tg: Target = 0;
      const icon = cardIcon(geo);
      const cardTop = geo.cardY;
      const inCard =
        p.y >= cardTop - 8 &&
        p.y <= cardTop + LAYOUT.cardH + 8 &&
        p.x >= REF_W / 2 - LAYOUT.cardW / 2 - 8 &&
        p.x <= REF_W / 2 + LAYOUT.cardW / 2 + 8;
      const fileHome = f ? f.slotEmpty < 0.5 : true;
      const gemFree = d.gem.corked === 0;
      if (gemFree && f && Math.hypot(p.x - f.gemX, p.y - f.gemY) < 34) tg = 2;
      else if (f && Math.hypot(p.x - f.spX, p.y - f.spY) < 26) tg = 3;
      else if (inCard && fileHome) tg = 1;
      else if (Math.abs(p.y) < 40 && p.x > 10 && p.x < 345) tg = 4;
      target.set(tg);
      d.last = now;
      if (tg === 1) {
        d.mode = MODE.file;
        d.t = 0;
        d.fileX = icon.x;
        d.fileY = icon.y;
        if (d.gem.corked === 0) recorkGem(d.gem, now);
      } else if (tg === 2) {
        d.mode = MODE.gem;
        d.gem.holdX = p.x - ANCHOR.x;
        d.gem.holdY = p.y - ANCHOR.y;
      } else if (tg === 3) {
        d.kickAt = now;
        d.kickDir = f && f.spX > 200 ? -1 : 1;
        scheduleOnRN(cue, 'skitter');
        scheduleOnRN(buzz, 2);
      }
    })
    .onUpdate((e) => {
      const tg = target.get();
      const d = driver.get();
      d.last = clock.get();
      const p = toLocal(e.x, e.y);
      if (tg === 1) {
        const icon = cardIcon(geo);
        const s0 = start.get();
        d.fileX = icon.x + (p.x - s0.x);
        d.fileY = icon.y + (p.y - s0.y);
      } else if (tg === 2) {
        d.gem.holdX = p.x - ANCHOR.x;
        d.gem.holdY = p.y - ANCHOR.y;
      }
    })
    .onFinalize((e) => {
      const tg = target.get();
      const d = driver.get();
      const now = clock.get();
      const p = toLocal(e.x, e.y);
      const s0 = start.get();
      const tap = Math.hypot(p.x - s0.x, p.y - s0.y) < 10;
      target.set(0);
      d.last = now;
      if (tg === 1) {
        const dist = d.fileX === d.fileX ? Math.hypot(d.fileX - MOUTH.x, d.fileY - MOUTH.y) : 999;
        if (tap) {
          // a tap on the card plays the upload from the lift
          d.fileX = Number.NaN;
          d.t = T.lift;
          d.mode = MODE.auto;
          d.sound = 1;
        } else if (dist < NEAR_R) {
          dropFile(d, now);
          scheduleOnRN(onEvent, EV.gulp, true);
        } else {
          d.fileFromX = d.fileX;
          d.fileFromY = d.fileY;
          d.fileBack = 0;
          d.fileBackAt = now;
          d.fileX = Number.NaN;
          d.mode = MODE.hold;
          if (d.gem.corked === 0) recorkGem(d.gem, now);
        }
      } else if (tg === 2) {
        d.gem.holdX = Number.NaN;
        d.mode = MODE.hold;
        scheduleOnRN(buzz, 0);
      } else if (tg === 4 && tap) {
        // tap the vial: a finished upload brews again; a resting one starts the demo upload
        if (d.t >= T.done && d.t < T.recork2) {
          d.t = T.recork2 - 0.01;
          d.mode = MODE.auto;
          d.sound = 1;
        } else if (d.t < T.lift || d.mode === MODE.hold) {
          d.t = T.lift;
          d.mode = MODE.auto;
          d.sound = 1;
        }
      }
    });

  const replay = () => {
    scheduleOnUI(() => {
      const d = driver.get();
      d.t = T.lift;
      d.mode = MODE.auto;
      d.sound = 1;
      d.last = clock.get();
    });
  };


  return (
    <View style={styles.screen} onLayout={onLayout}>
      <StatusBar style="light" />
      <GestureDetector gesture={touch}>
        <View
          style={StyleSheet.absoluteFill}
          accessible
          accessibilityRole="button"
          accessibilityLabel="Upload spells.zip, 48.2 megabytes, into the potion vial."
          accessibilityHint="Plays the upload."
          onAccessibilityTap={replay}
        >
          {size ? (
            <Canvas style={StyleSheet.absoluteFill}>
              <Picture picture={art} />
              <Picture picture={type} />
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

export { LOOP };
