import {
  BlurStyle,
  Canvas,
  FilterMode,
  MipmapMode,
  Picture,
  type SkFont,
  type SkImage,
  Skia,
  TileMode,
  useFont,
  useImage,
} from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useEffect, useMemo, useState } from 'react';
import { AppState, type LayoutChangeEvent, PixelRatio, StyleSheet, Text, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  useAnimatedStyle,
  useDerivedValue,
  useFrameCallback,
  useReducedMotion,
  useSharedValue,
  withSpring,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

import { easeInOut, type Jelly, LOOP, makeJelly, POKE, seg, smooth, stepJelly, T } from './zjd-motion';
import { SLAB_SKSL } from './zjd-shaders';
import { C, COPY, LAYOUT, SLAB } from './zjd-theme';
import { type Cue, useZjdAudio } from './use-zjd-audio';

/*
 Zodiac Jelly Dish — the "dish of the week for your sign" card of an invented food-delivery app.

 Look (the pin, a still): a dark sheet with a close button; in the middle a big slab of liquid
 glass, its outline soft and wobbly, pastel jelly colours swimming inside and silver light on its
 bevel; a window in it shows cheese pancakes with raspberry; "CHEESE PANCAKES" in white over it,
 "VIRGO 1.09–7.09" in yellow under it; a joke line; an orange "+ price" button and a dark "Save
 for later".

 Motion (ours, the pin is a still): the slab is jelly. A finger pulls it and lets go and it
 jiggles on a soft spring; the colours inside turn while the light sweeps across the bevel; Add
 is pressed and the slab drops and bounces while the button says it is added. The outline never
 stops breathing (whole sine cycles per loop).

 autoPlay: LOOP = 8 s (marks in zjd-motion.ts); frame 8 s equals frame 0.
 Touch: grab the slab anywhere and pull (rubber-banded to about 70 pt); let go and it jiggles.
 The light follows the pull. Tap Add for the bounce. 2 s after the last touch the loop resumes.
 Reduced motion: the slab rests still; the buttons still work.
*/

const slabFx = Skia.RuntimeEffect.Make(SLAB_SKSL);
let debugState = '';

type Props = { autoPlay?: boolean; previewTime?: number };

/** The label printed on the glass, as an image (it is sampled through the jelly's deformation). */
function makeLabel(title: SkFont, sign: SkFont, dates: SkFont, pd: number): { img: SkImage; ox: number; oy: number } | null {
  const W = SLAB.w;
  const H = SLAB.h;
  const surf = Skia.Surface.MakeOffscreen(Math.ceil(W * pd), Math.ceil(H * pd));
  if (!surf) return null;
  const c = surf.getCanvas();
  c.scale(pd, pd);
  c.translate(W / 2, H / 2);
  const width = (f: SkFont, s: string) => f.getGlyphWidths(f.getGlyphIDs(s)).reduce((a, b) => a + b, 0);
  const put = (f: SkFont, s: string, cx: number, base: number, color: string, glow: string) => {
    const x = cx - width(f, s) / 2;
    const g = Skia.Paint();
    g.setColor(Skia.Color(glow));
    g.setAlphaf(0.75);
    g.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 5, true));
    c.drawText(s, x, base, g, f);
    const p = Skia.Paint();
    p.setColor(Skia.Color(color));
    c.drawText(s, x, base, p, f);
  };
  put(title, COPY.title[0], 27, -148, C.title, C.titleGlow);
  put(title, COPY.title[1], 27, -99, C.title, C.titleGlow);
  put(sign, COPY.sign, 8, 113, C.sign, C.signGlow);
  put(dates, COPY.dates, 15, 146, C.sign, C.signGlow);
  surf.flush();
  const img = surf.makeImageSnapshot().makeNonTextureImage();
  if (!img) return null;
  return { img, ox: -W / 2, oy: -H / 2 };
}

export function ZodiacJellyDish({ autoPlay = false, previewTime }: Props) {
  const reduced = useReducedMotion();
  const auto = autoPlay && !reduced;
  const insets = useSafeAreaInsets();
  const [size, setSize] = useState<{ width: number; height: number } | null>(null);
  const [active, setActive] = useState(AppState.currentState === 'active');
  useEffect(() => {
    const sub = AppState.addEventListener('change', (s) => setActive(s === 'active'));
    return () => sub.remove();
  }, []);

  const dishImg = useImage(require('./assets/dish.webp'));
  const title = useFont(require('./assets/fonts/Nunito-Black.ttf'), 30);
  const sign = useFont(require('./assets/fonts/Nunito-Black.ttf'), 29);
  const dates = useFont(require('./assets/fonts/Nunito-ExtraBold.ttf'), 21);
  const pd = PixelRatio.get();

  const res = useMemo(() => {
    if (!dishImg || !title || !sign || !dates || !slabFx) return null;
    const label = makeLabel(title, sign, dates, pd);
    if (!label) return null;
    return {
      dish: dishImg.makeShaderOptions(TileMode.Clamp, TileMode.Clamp, FilterMode.Linear, MipmapMode.None),
      dishW: dishImg.width(),
      dishH: dishImg.height(),
      label: label.img.makeShaderOptions(TileMode.Decal, TileMode.Decal, FilterMode.Linear, MipmapMode.None),
      lox: label.ox,
      loy: label.oy,
    };
  }, [dishImg, title, sign, dates, pd]);

  const w = size?.width ?? 402;
  const h = size?.height ?? 874;
  const cx = w / 2 - 2;
  const cy = insets.top + LAYOUT.slabFromTop;

  const play = useZjdAudio(!reduced);
  const cue = (c: Cue) => play(c);
  const buzz = (kind: number) => {
    if (process.env.EXPO_OS === 'web') return;
    if (kind === 2) Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success).catch(() => {});
    else Haptics.impactAsync(kind === 1 ? Haptics.ImpactFeedbackStyle.Medium : Haptics.ImpactFeedbackStyle.Light).catch(() => {});
  };

  const time = useSharedValue(previewTime ?? 0);
  const clock = useSharedValue(0);
  const last = useSharedValue(-10);
  const jelly = useSharedValue<Jelly>(makeJelly());
  const tick = useSharedValue(0);
  const fingerHold = useSharedValue(0);
  const addedAt = useSharedValue(-10); // clock of a finger's Add
  const debugT = useSharedValue(-1);

  useEffect(() => {
    if (!__DEV__) return;
    const g = globalThis as { __jellyDish?: unknown };
    g.__jellyDish = { at: (t: number) => debugT.set(t), play: () => debugT.set(-1), state: () => debugState };
    return () => {
      delete g.__jellyDish;
    };
  }, [debugT]);
  const mirror = (s: string) => {
    debugState = s;
  };

  const frameCb = useFrameCallback((info) => {
    const dt = Math.min((info.timeSincePreviousFrame ?? 16) / 1000, 0.05);
    const now = clock.get() + dt;
    clock.set(now);
    const j = jelly.get();
    const dbg = debugT.get();
    const touched = fingerHold.get() === 1 || now - last.get() < 2;
    let t = time.get();
    if (dbg >= 0) t = dbg;
    else if (auto && !touched) {
      const before = t;
      t += dt;
      if (t >= LOOP) t -= LOOP;
      // the autoplay finger: pull out, let go
      if (t >= T.poke[0] && t < T.release) {
        const k = easeInOut(seg(t, T.poke[0], T.release));
        j.hold = 1;
        j.gx = POKE.gx;
        j.gy = POKE.gy;
        j.tx = POKE.px * k;
        j.ty = POKE.py * k;
      } else if (before < T.release && t >= T.release) j.hold = 0;
      // Add: the slab drops
      if (before < T.release2 && t >= T.release2) j.sv += 1.6;
    }
    if (dbg < 0) time.set(t);
    else time.set(dbg);
    stepJelly(j, dt);
    if (__DEV__ && Math.floor(now * 4) !== Math.floor((now - dt) * 4)) {
      scheduleOnRN(mirror, `t=${t.toFixed(2)} pull=${j.px.toFixed(1)},${j.py.toFixed(1)} sq=${j.sq.toFixed(3)}`);
    }
    tick.set(tick.get() + 1);
  }, false);

  useEffect(() => {
    frameCb.setActive(active && !reduced && size !== null && res !== null && previewTime === undefined);
    return () => frameCb.setActive(false);
  }, [active, reduced, size, res, previewTime, frameCb]);

  const picture = useDerivedValue(() => {
    tick.get();
    const rec = Skia.PictureRecorder();
    const c = rec.beginRecording(Skia.XYWHRect(0, 0, w, h));
    c.drawColor(Skia.Color(C.page));
    const t = time.get();
    // stars
    for (let i = 0; i < 70; i++) {
      const s1 = Math.sin(i * 12.9898 + 78.233) * 43758.5453;
      const s2 = Math.sin(i * 7.31 + 3.1) * 24634.6345;
      const x = (s1 - Math.floor(s1)) * w;
      const y = (s2 - Math.floor(s2)) * h;
      const tw = 0.35 + 0.65 * Math.max(0, Math.sin((2 * Math.PI * (1 + (i % 3)) * t) / LOOP + i));
      const p = Skia.Paint();
      p.setColor(Skia.Color('#ffffff'));
      p.setAlphaf(0.08 + 0.3 * tw * ((i % 5) / 5));
      c.drawRect(Skia.XYWHRect(x, y, i % 7 === 0 ? 1.4 : 0.9, i % 7 === 0 ? 1.4 : 0.9), p);
    }
    // the sheet
    const sheet = Skia.Paint();
    sheet.setColor(Skia.Color(C.sheet));
    const st = insets.top + 2;
    c.drawRRect(Skia.RRectXY(Skia.XYWHRect(0, st, w, h - st + 40), 34, 34), sheet);
    if (!res || !slabFx) return rec.finishRecordingAsPicture();
    const j = jelly.get();
    const swirl = easeInOut(seg(t, T.swirl[0], T.swirl[1]));
    const sweep = Math.sin(swirl * Math.PI);
    const lx = -0.55 + sweep * 1.3 + j.px / 70;
    const ly = -0.8 + j.py / 90;
    const fa = addedAt.get();
    const nowc = clock.get();
    const pulseAuto = t >= T.release2 ? Math.exp(-(t - T.release2) * 6) * smooth(seg(t, T.release2, T.release2 + 0.05)) : 0;
    const pulseTouch = nowc - fa >= 0 && nowc - fa < 1 ? Math.exp(-(nowc - fa) * 6) : 0;
    // soft shadow under the slab
    const sh = Skia.Paint();
    sh.setColor(Skia.Color('#000000'));
    sh.setAlphaf(0.55);
    sh.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 26, true));
    c.drawRRect(
      Skia.RRectXY(Skia.XYWHRect(cx - SLAB.w / 2 + 10, cy - SLAB.h / 2 + 28, SLAB.w - 20, SLAB.h - 10), SLAB.corner, SLAB.corner),
      sh,
    );
    const p = Skia.Paint();
    p.setShader(
      slabFx.makeShaderWithChildren(
        [
          cx,
          cy,
          SLAB.w / 2,
          SLAB.h / 2,
          SLAB.corner,
          SLAB.bevel,
          SLAB.window.x,
          SLAB.window.y,
          SLAB.window.w,
          SLAB.window.h,
          SLAB.window.r,
          t,
          LOOP,
          j.gx,
          j.gy,
          j.px,
          j.py,
          j.sq,
          swirl,
          lx,
          ly,
          res.dishW,
          res.dishH,
          pd,
          res.lox,
          res.loy,
          Math.max(pulseAuto, pulseTouch),
        ],
        [res.dish, res.label],
      ),
    );
    c.drawRect(Skia.XYWHRect(cx - SLAB.w / 2 - 90, cy - SLAB.h / 2 - 90, SLAB.w + 180, SLAB.h + 180), p);
    return rec.finishRecordingAsPicture();
  }, [res, w, h, cx, cy, insets.top]);

  // ---------------------------------------------------------------- buttons
  const added = useDerivedValue(() => {
    const t = time.get();
    const auto1 = smooth(seg(t, T.added[0], T.added[0] + 0.25)) * (1 - smooth(seg(t, T.added[1] - 0.25, T.added[1])));
    const fa = clock.get() - addedAt.get();
    const touch = fa >= 0 && fa < 1.8 ? smooth(fa / 0.25) * (1 - smooth((fa - 1.55) / 0.25)) : 0;
    return Math.max(auto1, touch);
  });
  const buyPress = useSharedValue(0);
  const savePress = useSharedValue(0);
  const buyAuto = useDerivedValue(() => {
    const t = time.get();
    return t >= T.press && t < T.release2 + 0.2 ? Math.sin(seg(t, T.press, T.release2 + 0.2) * Math.PI) : 0;
  });
  const buyStyle = useAnimatedStyle(() => ({ transform: [{ scale: 1 - Math.max(buyPress.get(), buyAuto.get()) * 0.04 }] }));
  const saveStyle = useAnimatedStyle(() => ({ transform: [{ scale: 1 - savePress.get() * 0.04 }] }));
  const priceStyle = useAnimatedStyle(() => ({
    opacity: 1 - added.get(),
    transform: [{ translateY: -added.get() * 10 }],
  }));
  const addedStyle = useAnimatedStyle(() => ({
    opacity: added.get(),
    transform: [{ translateY: (1 - added.get()) * 10 }],
  }));

  const toLocal = (x: number, y: number) => {
    'worklet';
    return { x: x - cx, y: y - cy };
  };
  const slabTouch = Gesture.Pan()
    .minDistance(0)
    .enabled(previewTime === undefined)
    .onBegin((e) => {
      const p = toLocal(e.x, e.y);
      if (Math.abs(p.x) > SLAB.w / 2 + 16 || Math.abs(p.y) > SLAB.h / 2 + 16) return;
      const j = jelly.get();
      j.gx = p.x;
      j.gy = p.y;
      j.hold = 1;
      j.tx = j.px;
      j.ty = j.py;
      fingerHold.set(1);
      last.set(clock.get());
    })
    .onUpdate((e) => {
      if (fingerHold.get() !== 1) return;
      const j = jelly.get();
      const d = Math.hypot(e.translationX, e.translationY);
      const k = d > 0 ? (70 * Math.tanh(d / 90)) / d : 0;
      j.tx = e.translationX * k * (reduced ? 0.3 : 1);
      j.ty = e.translationY * k * (reduced ? 0.3 : 1);
      last.set(clock.get());
    })
    .onFinalize(() => {
      if (fingerHold.get() !== 1) return;
      const j = jelly.get();
      j.hold = 0;
      fingerHold.set(0);
      last.set(clock.get());
      if (Math.hypot(j.px, j.py) > 12) {
        scheduleOnRN(cue, 'squish');
        scheduleOnRN(buzz, 0);
      }
    });
  const buyTouch = Gesture.Tap()
    .maxDuration(2000)
    .onBegin(() => buyPress.set(withSpring(1, { duration: 150, dampingRatio: 1 })))
    .onFinalize((_e, ok) => {
      buyPress.set(withSpring(0, { duration: 320, dampingRatio: 0.7 }));
      if (!ok) return;
      addedAt.set(clock.get());
      last.set(clock.get());
      if (!reduced) jelly.get().sv += 1.6;
      scheduleOnRN(cue, 'add');
      scheduleOnRN(buzz, 2);
    });
  const saveTouch = Gesture.Tap()
    .maxDuration(2000)
    .onBegin(() => savePress.set(withSpring(1, { duration: 150, dampingRatio: 1 })))
    .onFinalize((_e, ok) => {
      savePress.set(withSpring(0, { duration: 320, dampingRatio: 0.7 }));
      if (!ok) return;
      scheduleOnRN(cue, 'tap');
      scheduleOnRN(buzz, 0);
    });

  const onLayout = (e: LayoutChangeEvent) => {
    const { width, height } = e.nativeEvent.layout;
    if (!size || size.width !== width || size.height !== height) setSize({ width, height });
  };

  const bottom = insets.bottom;
  const buyBottom = bottom + LAYOUT.buyFromBottom - LAYOUT.btnH;
  const saveBottom = buyBottom - LAYOUT.btnH - LAYOUT.btnGap;

  return (
    <View style={styles.screen} onLayout={onLayout}>
      <StatusBar style="light" />
      <GestureDetector gesture={slabTouch}>
        <View style={StyleSheet.absoluteFill}>
          {size ? (
            <Canvas style={StyleSheet.absoluteFill}>
              <Picture picture={picture} />
            </Canvas>
          ) : null}
        </View>
      </GestureDetector>
      <View style={[styles.close, { top: insets.top + 26 }]} accessible accessibilityRole="button" accessibilityLabel="Close">
        <Text style={styles.closeX} allowFontScaling={false}>
          ×
        </Text>
      </View>
      <Text style={[styles.body, { bottom: bottom + LAYOUT.bodyFromBottom - 38 }]} allowFontScaling={false}>
        {COPY.body}
      </Text>
      <GestureDetector gesture={buyTouch}>
        <Animated.View style={[styles.btn, styles.buy, { bottom: saveBottom + LAYOUT.btnH + LAYOUT.btnGap }, buyStyle]} accessible accessibilityRole="button">
          <Animated.View style={[styles.btnRow, priceStyle]}>
            <Text style={styles.plus} allowFontScaling={false}>
              +
            </Text>
            <Text style={styles.price} allowFontScaling={false}>
              {COPY.price}
            </Text>
            <Text style={styles.was} allowFontScaling={false}>
              {COPY.was}
            </Text>
          </Animated.View>
          <Animated.View style={[styles.btnRow, StyleSheet.absoluteFill, addedStyle]}>
            <Text style={styles.price} allowFontScaling={false}>
              {COPY.added}
            </Text>
          </Animated.View>
        </Animated.View>
      </GestureDetector>
      <GestureDetector gesture={saveTouch}>
        <Animated.View style={[styles.btn, styles.save, { bottom: saveBottom }, saveStyle]} accessible accessibilityRole="button">
          <Text style={styles.saveText} allowFontScaling={false}>
            {COPY.save}
          </Text>
        </Animated.View>
      </GestureDetector>
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: C.page },
  close: {
    position: 'absolute',
    right: 18,
    width: 36,
    height: 36,
    borderRadius: 18,
    backgroundColor: C.close,
    borderWidth: StyleSheet.hairlineWidth,
    borderColor: 'rgba(255,255,255,0.18)',
    alignItems: 'center',
    justifyContent: 'center',
  },
  closeX: { color: '#ffffff', fontSize: 24, lineHeight: 26, fontWeight: '300', marginTop: -2 },
  body: {
    position: 'absolute',
    left: 40,
    right: 40,
    color: C.body,
    fontSize: 13.5,
    lineHeight: 19,
    textAlign: 'center',
  },
  btn: {
    position: 'absolute',
    alignSelf: 'center',
    width: LAYOUT.btnW,
    height: LAYOUT.btnH,
    borderRadius: LAYOUT.btnH / 2,
    alignItems: 'center',
    justifyContent: 'center',
  },
  buy: { backgroundColor: C.buy },
  save: { backgroundColor: C.save, borderWidth: StyleSheet.hairlineWidth, borderColor: 'rgba(255,255,255,0.12)' },
  btnRow: { flexDirection: 'row', alignItems: 'center', justifyContent: 'center' },
  plus: { color: '#ffffff', fontSize: 22, fontWeight: '300', marginRight: 8, marginTop: -2 },
  price: { color: '#ffffff', fontSize: 16, fontWeight: '600' },
  was: { color: 'rgba(255,255,255,0.7)', fontSize: 15, fontWeight: '500', marginLeft: 8, textDecorationLine: 'line-through' },
  saveText: { color: '#ffffff', fontSize: 16, fontWeight: '500' },
});

export { LOOP };
