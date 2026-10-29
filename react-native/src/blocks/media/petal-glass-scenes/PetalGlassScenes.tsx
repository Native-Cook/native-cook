import { useCallback, useEffect, useMemo, useState } from 'react';
import { AppState, Platform, StyleSheet, useWindowDimensions, View } from 'react-native';
import {
  BlurStyle,
  Canvas,
  Fill,
  ImageShader,
  Picture,
  Shader,
  Skia,
  matchFont,
  useImage,
  type SkCanvas,
  type SkFont,
  type SkImage,
  type SkPoint,
} from '@shopify/react-native-skia';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import { useDerivedValue, useFrameCallback, useReducedMotion, useSharedValue } from 'react-native-reanimated';
import { scheduleOnRN } from 'react-native-worklets';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';

import { GLASS_SKSL } from './glassShader';
import {
  SCENES,
  beginTouch,
  bulge,
  cardScale,
  easeInOutCubic,
  endTouch,
  front,
  idleUntil,
  initialTouch,
  moveTouch,
  seg,
  textIn,
  textOut,
  touchFrame,
  warp,
  type Frame,
  type Fx,
} from './glassMotion';

/*
 Petal Glass Scenes — "petalfield", a focus-sound app: its scene switcher. Macro petal scenes drift
 slowly behind a tall glass card named after the scene. The card is a thick slab: it magnifies the
 petals, warps them with a slow liquid ripple and bends them hard along its bevelled rim with a thin
 colour split. Each scene change grows the next scene out of the glass: first inside the card, a
 beat at its rim, then out across the screen behind a lens-like front. Ported from the Swift
 reference (PetalGlassScenes.swift).

 Loop (LOOP = 9 s): Clarity rests 1.6 s → Depth grows out of the glass (1.4 s) → rests → Echo → back
 to Clarity at 9 s. Drag anywhere to move the card and watch the refraction shift (it springs back);
 swipe left or right to change the scene. The loop resumes 2 s after the last touch at the shown
 scene's rest. Reduced motion: Clarity, still; swipes cross-fade.
 */

const EFFECT = Skia.RuntimeEffect.Make(GLASS_SKSL);
const P3 = Platform.OS === 'ios' ? 1 : 0;
const SANS = Platform.OS === 'ios' ? 'System' : 'sans-serif';

const IMAGES = [
  require('./assets/petal-clarity.webp'),
  require('./assets/petal-depth.webp'),
  require('./assets/petal-echo.webp'),
];

const SCENE_COPY = [
  { title: 'Clarity', line: 'Bright air for clear,\nearly focus.', sound: 'Morning air · 25 min' },
  { title: 'Depth', line: 'Low tones that hold\nlong, deep work.', sound: 'Deep hum · 50 min' },
  { title: 'Echo', line: 'Soft layers that fade as\nyou rest.', sound: 'Warm echo · 15 min' },
];

const CARD_W = 240;
const CARD_H = 344;
const CARD_R = 28;

function haptic() {
  if (process.env.EXPO_OS === 'web') return;
  Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
}

/** A line of text shaped once: glyphs with their pen positions (tracking included). */
type Shaped = { glyphs: number[]; positions: SkPoint[]; width: number; ascent: number; descent: number };

function shape(font: SkFont, text: string, tracking = 0): Shaped {
  const glyphs = font.getGlyphIDs(text);
  const widths = font.getGlyphWidths(glyphs);
  const positions: SkPoint[] = [];
  let x = 0;
  for (let i = 0; i < glyphs.length; i++) {
    positions.push({ x, y: 0 });
    x += widths[i] + tracking;
  }
  const metrics = font.getMetrics();
  return { glyphs, positions, width: x - (glyphs.length ? tracking : 0), ascent: -metrics.ascent, descent: metrics.descent };
}


type Fonts = { title: SkFont; body: SkFont; mark: SkFont; small: SkFont; counter: SkFont; label: SkFont; sound: SkFont };

type Texts = {
  titles: Shaped[];
  bodies: Shaped[][];
  sounds: Shaped[];
  counters: Shaped[];
  mark: Shaped;
  focus: Shaped;
  hint: Shaped;
  label: Shaped;
  bodyLine: number;
};

function makeTexts(): { fonts: Fonts; texts: Texts } {
  const font = (size: number, weight: '300' | '400' | '500' | '600') => matchFont({ fontFamily: SANS, fontSize: size, fontWeight: weight });
  const fonts: Fonts = {
    title: font(50, '300'),
    body: font(13, '400'),
    mark: font(17, '600'),
    small: font(12, '400'),
    counter: font(13, '500'),
    label: font(11, '600'),
    sound: font(17, '500'),
  };
  const bodyMetrics = fonts.body.getMetrics();
  const texts: Texts = {
    titles: SCENE_COPY.map((s) => shape(fonts.title, s.title, -0.5)),
    bodies: SCENE_COPY.map((s) => s.line.split('\n').map((line) => shape(fonts.body, line))),
    sounds: SCENE_COPY.map((s) => shape(fonts.sound, s.sound)),
    counters: SCENE_COPY.map((_, i) => shape(fonts.counter, `0${i + 1} / 0${SCENES}`)),
    mark: shape(fonts.mark, 'petalfield', -0.2),
    focus: shape(fonts.small, 'Focus scenes'),
    hint: shape(fonts.small, 'Drag the glass · swipe for a scene'),
    label: shape(fonts.label, 'NOW PLAYING', 1.2),
    bodyLine: bodyMetrics.descent - bodyMetrics.ascent + 3,
  };
  return { fonts, texts };
}

// Drawing (worklets; helpers above their callers)

function cover(image: SkImage, scene: number, f: Frame, w: number, h: number, still: boolean) {
  'worklet';
  const iw = Math.max(image.width(), 1);
  const ih = Math.max(image.height(), 1);
  const phase = scene * 2.1;
  const breathe = still ? 0 : 0.025 * Math.sin(f.theta + phase);
  const scale = Math.max(w / iw, h / ih) * (1.1 + breathe);
  const rw = iw * scale;
  const rh = ih * scale;
  const px = still ? 0 : 14 * Math.sin(f.theta + phase) - f.offsetX * 0.12;
  const py = still ? 0 : 10 * Math.sin(f.theta + phase + 1.9) - f.offsetY * 0.12;
  return [w / 2 - rw / 2 + px, h / 2 - rh / 2 + py, rw, rh];
}

/** How far the front must travel from the card's rim to clear the farthest corner. */
function reach(cx: number, cy: number, hw: number, hh: number, w: number, h: number) {
  'worklet';
  let far = 0;
  const xs = [0, w, 0, w];
  const ys = [0, 0, h, h];
  for (let i = 0; i < 4; i++) {
    const qx = Math.abs(xs[i] - cx) - (hw - CARD_R);
    const qy = Math.abs(ys[i] - cy) - (hh - CARD_R);
    const d = Math.hypot(Math.max(qx, 0), Math.max(qy, 0)) + Math.min(Math.max(qx, qy), 0) - CARD_R;
    far = Math.max(far, d);
  }
  return far + 70;
}

type TextFx = { scene: number; title: Fx; line: Fx };

function textStates(f: Frame): TextFx[] {
  'worklet';
  const none = { alpha: 1, shift: 0, blur: 0 };
  if (f.from === f.to || f.p <= 0) return [{ scene: f.from, title: none, line: none }];
  if (f.fade >= 0) {
    const out = { alpha: 1 - f.fade, shift: 0, blur: 0 };
    const inn = { alpha: f.fade, shift: 0, blur: 0 };
    return [
      { scene: f.from, title: out, line: out },
      { scene: f.to, title: inn, line: inn },
    ];
  }
  return [
    { scene: f.from, title: textOut(f.p, 0), line: textOut(f.p, 0.04) },
    { scene: f.to, title: textIn(f.p, 0), line: textIn(f.p, 0.04) },
  ];
}

function textPaint(alpha: number, shadow: number, radius: number, blur: number) {
  'worklet';
  const paint = Skia.Paint();
  paint.setAntiAlias(true);
  paint.setColor(Skia.Color(`rgba(255,255,255,${Math.max(0, Math.min(1, alpha))})`));
  if (shadow > 0) {
    const a = Math.max(0, Math.min(1, alpha)) * shadow;
    paint.setImageFilter(Skia.ImageFilter.MakeDropShadow(0, 1, radius / 2, radius / 2, Skia.Color(`rgba(0,0,0,${a})`), null));
  }
  if (blur > 0.2) paint.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, blur / 2, true));
  return paint;
}

function drawShaped(canvas: SkCanvas, s: Shaped, font: SkFont, x: number, baseline: number, alpha: number, shadow: number, radius: number, blur: number) {
  'worklet';
  if (alpha <= 0.003) return;
  canvas.drawGlyphs(s.glyphs, s.positions, x, baseline, font, textPaint(alpha, shadow, radius, blur));
}

function drawCardText(canvas: SkCanvas, f: Frame, cx: number, cy: number, fonts: Fonts, texts: Texts) {
  'worklet';
  const scale = f.fade >= 0 ? 1 : cardScale(f.p);
  const left = cx - CARD_W / 2;
  const top = cy - CARD_H / 2;
  canvas.save();
  canvas.translate(cx, cy);
  canvas.scale(scale, scale);
  canvas.translate(-cx, -cy);
  const states = textStates(f);
  for (let i = 0; i < states.length; i++) {
    const item = states[i];
    const title = texts.titles[item.scene];
    // The rotated line box is 60 wide from the card's left + 18; its baseline sits `descent` in
    // from the box's left side.
    const lineH = title.ascent + title.descent;
    const baseX = 18 + (60 - lineH) / 2 + title.descent;
    canvas.save();
    canvas.translate(left + baseX, top + 24 + item.title.shift);
    canvas.rotate(90, 0, 0);
    drawShaped(canvas, title, fonts.title, 0, 0, item.title.alpha, 0.12, 6, item.title.blur);
    canvas.restore();
    const lines = texts.bodies[item.scene];
    const blockH = lines.length * texts.bodyLine - 3;
    let y = top + CARD_H - 22 - blockH + item.line.shift * 0.5;
    for (let j = 0; j < lines.length; j++) {
      drawShaped(canvas, lines[j], fonts.body, left + 22, y + lines[j].ascent, item.line.alpha * 0.92, 0.12, 6, item.line.blur);
      y += texts.bodyLine;
    }
  }
  canvas.restore();
}

function drawChrome(canvas: SkCanvas, f: Frame, centerY: number, height: number, top: number, bottom: number, reduced: boolean, fonts: Fonts, texts: Texts) {
  'worklet';
  const states = textStates(f);
  // Wordmark.
  drawShaped(canvas, texts.mark, fonts.mark, 24, top + 10 + texts.mark.ascent, 1, 0.14, 8, 0);
  const markH = texts.mark.ascent + texts.mark.descent;
  drawShaped(canvas, texts.focus, fonts.small, 24, top + 10 + markH + 2 + texts.focus.ascent, 0.78, 0.14, 8, 0);

  // Counter.
  for (let i = 0; i < states.length; i++) {
    const item = states[i];
    const c = texts.counters[item.scene];
    drawShaped(canvas, c, fonts.counter, 393 - 24 - c.width, top + 13 + c.ascent + item.title.shift * 0.4, item.title.alpha, 0.14, 8, 0);
  }

  // Page dots.
  const dots = centerY + CARD_H / 2 + 38;
  const p = f.fade >= 0 ? f.fade : easeInOutCubic(seg(f.p, 0.3, 0.9));
  const at = f.from + (f.to - f.from) * (f.from === f.to ? 0 : p);
  const widths = [0, 0, 0];
  let total = 8 * (SCENES - 1);
  for (let i = 0; i < SCENES; i++) {
    widths[i] = 6 + 12 * Math.max(0, 1 - Math.abs(i - at));
    total += widths[i];
  }
  let x = (393 - total) / 2;
  for (let i = 0; i < SCENES; i++) {
    const near = Math.max(0, 1 - Math.abs(i - at));
    const paint = textPaint(0.5 + 0.45 * near, 0.14, 8, 0);
    canvas.drawRRect(Skia.RRectXY(Skia.XYWHRect(x, dots - 3, widths[i], 6), 3, 3), paint);
    x += widths[i] + 8;
  }

  // Hint.
  drawShaped(canvas, texts.hint, fonts.small, (393 - texts.hint.width) / 2, dots + 16 + texts.hint.ascent, 0.72, 0.14, 8, 0);

  // Now playing.
  const barTop = height - bottom - 20 - 56;
  const labelH = texts.label.ascent + texts.label.descent;
  const soundH = texts.sounds[0].ascent + texts.sounds[0].descent;
  const blockTop = barTop + (56 - (labelH + 5 + soundH)) / 2;
  drawShaped(canvas, texts.label, fonts.label, 24 + 13 + 7, blockTop + texts.label.ascent, 0.8, 0.14, 8, 0);
  const eqBottom = blockTop + labelH / 2 + 6;
  const ks = [7, 11, 13];
  for (let i = 0; i < 3; i++) {
    const v = 0.5 + 0.3 * Math.sin(ks[i] * f.theta + i * 1.7) + 0.2 * Math.sin((ks[i] + 4) * f.theta + i);
    const bh = 3 + 9 * (reduced ? 0.6 : v);
    canvas.drawRRect(Skia.RRectXY(Skia.XYWHRect(24 + i * 5, eqBottom - bh, 3, bh), 1, 1), textPaint(1, 0.14, 8, 0));
  }
  for (let i = 0; i < states.length; i++) {
    const item = states[i];
    const s = texts.sounds[item.scene];
    drawShaped(canvas, s, fonts.sound, 24, blockTop + labelH + 5 + s.ascent + item.line.shift * 0.4, item.line.alpha, 0.14, 8, item.line.blur * 0.5);
  }

  // Pause button.
  const bx = 393 - 24 - 28;
  const by = barTop + 28;
  const button = Skia.Paint();
  button.setAntiAlias(true);
  button.setColor(Skia.Color('rgba(255,255,255,0.92)'));
  button.setImageFilter(Skia.ImageFilter.MakeDropShadow(0, 6, 6, 6, Skia.Color('rgba(0,0,0,0.16)'), null));
  canvas.drawCircle(bx, by, 28, button);
  const ink = Skia.Paint();
  ink.setAntiAlias(true);
  ink.setColor(Skia.Color('#1B2233'));
  canvas.drawRRect(Skia.RRectXY(Skia.XYWHRect(bx - 7, by - 8.5, 4.5, 17), 1.5, 1.5), ink);
  canvas.drawRRect(Skia.RRectXY(Skia.XYWHRect(bx + 2.5, by - 8.5, 4.5, 17), 1.5, 1.5), ink);
}

type Props = {
  /** Plays the scene changes on a loop of LOOP s. */
  autoPlay?: boolean;
};

export function PetalGlassScenes({ autoPlay = false }: Props) {
  const { width, height } = useWindowDimensions();
  const insets = useSafeAreaInsets();
  const reduced = useReducedMotion();
  const [active, setActive] = useState(AppState.currentState === 'active');
  const [awake, setAwake] = useState(false);
  const wake = useCallback(() => setAwake(true), []);
  const sleep = useCallback(() => setAwake(false), []);
  useEffect(() => {
    const sub = AppState.addEventListener('change', (state) => setActive(state === 'active'));
    return () => sub.remove();
  }, []);

  const k = width / 393;
  const designHeight = height / k;
  const top = insets.top / k;
  const bottom = insets.bottom / k;
  const centerX = 196.5;
  const centerY = Math.round(designHeight * 0.465);

  const image0 = useImage(IMAGES[0]);
  const image1 = useImage(IMAGES[1]);
  const image2 = useImage(IMAGES[2]);
  const { fonts, texts } = useMemo(() => makeTexts(), []);

  const playing = autoPlay && !reduced;
  const clock = useSharedValue(0);
  const startedAt = useSharedValue(-1);
  const clockBase = useSharedValue(0);
  const touch = useSharedValue(initialTouch());
  const asleep = useSharedValue(false);

  const tick = useFrameCallback((info) => {
    if (startedAt.get() < 0) {
      startedAt.set(info.timestamp);
      clockBase.set(clock.get());
    }
    const now = clockBase.get() + (info.timestamp - startedAt.get()) / 1000;
    clock.set(now);
    const s = touch.get();
    if (s.haptic > s.hapticTaken) {
      touch.set({ ...s, hapticTaken: s.haptic });
      scheduleOnRN(haptic);
    }
    if (!playing && !asleep.get() && !s.touching && now > idleUntil(s)) {
      asleep.set(true);
      scheduleOnRN(sleep);
    }
  }, false);
  const running = active && (playing || awake);
  useEffect(() => {
    startedAt.set(-1);
    tick.setActive(running);
    return () => tick.setActive(false);
  }, [running, tick, startedAt]);

  const frame = useDerivedValue(() => touchFrame(touch.get(), clock.get(), playing, reduced));

  const ready = image0 && image1 && image2;
  const uniforms = useDerivedValue(() => {
    const f = frame.get();
    const images = [image0, image1, image2];
    const a = images[f.from];
    const b = images[f.to];
    const scale = f.fade >= 0 ? 1 : cardScale(f.p);
    const hw = (CARD_W / 2) * scale;
    const hh = (CARD_H / 2) * scale;
    const cx = centerX + f.offsetX;
    const cy = centerY + f.offsetY;
    const rectA = a ? cover(a, f.from, f, 393, designHeight, reduced) : [0, 0, 1, 1];
    const rectB = b ? cover(b, f.to, f, 393, designHeight, reduced) : [0, 0, 1, 1];
    return {
      k,
      size: [393, designHeight],
      rectA,
      pixelsA: [a ? a.width() : 1, a ? a.height() : 1],
      rectB,
      pixelsB: [b ? b.width() : 1, b ? b.height() : 1],
      card: [cx, cy, hw, hh],
      change: [front(f.p, reach(cx, cy, hw, hh, 393, designHeight)), bulge(f.p), reduced ? 9 : warp(f.p), f.fade],
      lightU: [reduced ? 0 : f.theta, f.offsetX * 0.012 + f.offsetY * 0.008, -0.55 + f.offsetX / 160, -0.83 + f.offsetY / 220],
      extra: [CARD_R * scale, -40 + f.offsetX * 0.6 - f.offsetY * 0.3, 1, 0],
      scenes: [f.from, f.to],
      p3: P3,
    };
  });

  const picture = useDerivedValue(() => {
    const f = frame.get();
    const recorder = Skia.PictureRecorder();
    const canvas = recorder.beginRecording(Skia.XYWHRect(0, 0, width, height));
    canvas.save();
    canvas.scale(k, k);
    drawCardText(canvas, f, centerX + f.offsetX, centerY + f.offsetY, fonts, texts);
    drawChrome(canvas, f, centerY, designHeight, top, bottom, reduced, fonts, texts);
    canvas.restore();
    return recorder.finishRecordingAsPicture();
  });

  const step = useCallback(
    (dx: number) => {
      const c = clock.get();
      const begun = beginTouch(touch.get(), c, playing, reduced);
      touch.set(endTouch(begun, c, dx * 120, 0, dx * 1000, 0, reduced));
      asleep.set(false);
      setAwake(true);
    },
    [playing, reduced, touch, clock, asleep],
  );

  const gesture = useMemo(
    () =>
      Gesture.Pan()
        .minDistance(4)
        .onStart(() => {
          touch.set(beginTouch(touch.get(), clock.get(), playing, reduced));
          asleep.set(false);
          scheduleOnRN(wake);
        })
        .onUpdate((e) => {
          // Under reduced motion the card stays put; only the swipe counts.
          if (reduced) return;
          touch.set(moveTouch(touch.get(), clock.get(), e.translationX / k, e.translationY / k));
        })
        .onEnd((e) => {
          touch.set(endTouch(touch.get(), clock.get(), e.translationX / k, e.translationY / k, e.velocityX / k, e.velocityY / k, reduced));
        }),
    [playing, reduced, k, touch, clock, wake, asleep],
  );

  return (
    <View style={styles.root}>
      <StatusBar style="light" />
      <GestureDetector gesture={gesture}>
        <View
          style={StyleSheet.absoluteFill}
          accessible
          accessibilityRole="adjustable"
          accessibilityLabel="petalfield focus scenes: Clarity, Depth and Echo, macro petals behind a glass card."
          accessibilityHint="Drag the glass, swipe for a scene"
          accessibilityActions={[{ name: 'increment' }, { name: 'decrement' }]}
          onAccessibilityAction={(event) => step(event.nativeEvent.actionName === 'increment' ? -1 : 1)}
        >
          <Canvas style={StyleSheet.absoluteFill}>
            {ready && EFFECT ? (
              <Fill>
                <Shader source={EFFECT} uniforms={uniforms}>
                  <ImageShader image={image0} fit="fill" rect={{ x: 0, y: 0, width: image0.width(), height: image0.height() }} tx="clamp" ty="clamp" />
                  <ImageShader image={image1} fit="fill" rect={{ x: 0, y: 0, width: image1.width(), height: image1.height() }} tx="clamp" ty="clamp" />
                  <ImageShader image={image2} fit="fill" rect={{ x: 0, y: 0, width: image2.width(), height: image2.height() }} tx="clamp" ty="clamp" />
                </Shader>
              </Fill>
            ) : null}
            <Picture picture={picture} />
          </Canvas>
        </View>
      </GestureDetector>
    </View>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: '#6BA8DB' },
});
