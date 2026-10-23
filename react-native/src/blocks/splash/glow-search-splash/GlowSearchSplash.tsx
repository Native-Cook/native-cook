import { useEffect, useMemo, useState } from 'react';
import { AppState, PixelRatio, Platform, StyleSheet, useWindowDimensions, View } from 'react-native';
import { BlendMode, Canvas, FilterMode, FillType, matchFont, MipmapMode, PaintStyle, Picture, Skia, TileMode, type SkCanvas, type SkFont, type SkPath } from '@shopify/react-native-skia';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import { useDerivedValue, useFrameCallback, useReducedMotion, useSharedValue } from 'react-native-reanimated';
import { scheduleOnRN } from 'react-native-worklets';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { bakeArt, CARD_SPEEDS, CARDS, disposeArt, hexColor, type Art } from './glowSearchArt';

/*
 Glow Search Splash — the launch of an invented visual-search app, Trove. A port of the SwiftUI block
 (GlowSearchSplash.swift is the reference): the same 393-wide design space, field at 44 % of the height, and the
 same 12 s timeline, written out at the top of the Swift file.
*/
export const LOOP = 12;
const REST_TIME = 5.0;
const BEATS = [5.0, 6.6, 8.3, 10.0];
const WORDS = ['anything', 'illustration', 'typography', '3D shapes'];
/** When each word after the first starts to be erased. */
const ERASES = [5.5, 7.2, 8.9];
const LETTER = 0.045;
const CENTER_X = 196.5;
const PILL_WIDTH = 296;
const PILL_HEIGHT = 54;
const PINK = 0xff4fd8;
const VIOLET = 0xa855f7;
const CYAN = 0x22d3ee;
const TEXT_FAMILY = Platform.OS === 'ios' ? 'Helvetica Neue' : 'sans-serif';

function ease(value: number) { 'worklet'; const u = Math.max(0, Math.min(1, value)); return u * u * (3 - 2 * u); }
function clamp(value: number) { 'worklet'; return Math.max(0, Math.min(1, value)); }
function easeOut(value: number) { 'worklet'; const u = clamp(value); return 1 - Math.pow(1 - u, 3); }
function easeInOut(value: number) { 'worklet'; const u = clamp(value); return u < 0.5 ? 4 * u * u * u : 1 - Math.pow(-2 * u + 2, 3) / 2; }
function easeOutBack(value: number) { 'worklet'; const u = clamp(value) - 1; return 1 + 2.70158 * u * u * u + 1.70158 * u * u; }
function lerp(a: number, b: number, u: number) { 'worklet'; return a + (b - a) * u; }
function color(hex: number, alpha = 1) { 'worklet'; return Skia.Color(hexColor(hex, alpha)); }
function paint(alpha = 1) {
  'worklet'; const p = Skia.Paint(); p.setAntiAlias(true); p.setDither(true); p.setColor(Skia.Color('#ffffff')); p.setAlphaf(Math.max(0, Math.min(1, alpha))); return p;
}
function prefix(text: string, count: number) { 'worklet'; return text.slice(0, Math.max(0, count)); }

type Typed = { text: string; active: boolean; hold: number };
/** The placeholder at time t, whether a letter is being typed or erased, and when the current pause began. */
function typed(t: number): Typed {
  'worklet';
  if (t < 2.4) return { text: '', active: false, hold: 0 };
  if (t < 3.0) return { text: prefix('Search', Math.floor((t - 2.4) / 0.1) + 1), active: true, hold: 0 };
  if (t < 3.75) return { text: 'Search' + prefix(' anything', Math.floor((t - 3.0) / (0.75 / 9)) + 1), active: true, hold: 0 };
  let hold = 3.75;
  for (let k = 1; k <= 3; k++) {
    const e = ERASES[k - 1], previous = WORDS[k - 1], next = WORDS[k];
    if (t < e) return { text: 'Search ' + previous, active: false, hold };
    if (t < e + 0.3) return { text: 'Search ' + prefix(previous, previous.length - Math.floor((t - e) / (0.3 / previous.length)) - 1), active: true, hold: 0 };
    const typing = LETTER * next.length;
    if (t < e + 0.3 + typing) return { text: 'Search ' + prefix(next, Math.floor((t - e - 0.3) / LETTER) + 1), active: true, hold: 0 };
    hold = e + 0.3 + typing;
  }
  if (t < 10.2) return { text: 'Search 3D shapes', active: false, hold };
  if (t < 10.7) return { text: prefix('Search 3D shapes', 16 - Math.floor((t - 10.2) / (0.5 / 16)) - 1), active: true, hold: 0 };
  return { text: '', active: false, hold: 0 };
}

function caretAlpha(t: number, typing: Typed) {
  'worklet';
  if (t < 1.1 || t >= 11.1) return 0;
  if (t < 2.0) return 1 - (ease((t - 1.4) / 0.05) - ease((t - 1.7) / 0.05));
  if (t < 2.4 || t >= 10.2 || typing.active || typing.hold === 0) return 1;
  const phase = (t - typing.hold) % 1.0;
  return phase < 0.5 ? 1 - ease((phase - 0.45) / 0.05) : ease((phase - 0.95) / 0.05);
}

/** 0 = caret, 1 = the open pill. */
function pillOpen(t: number) {
  'worklet';
  if (t < 2.0 || t >= 11.05) return 0;
  if (t < 2.45) return easeOut((t - 2.0) / 0.45);
  if (t < 10.7) return 1;
  return 1 - easeInOut((t - 10.7) / 0.35);
}

function streakAt(t: number) {
  'worklet';
  return ease((t - 2.0) / 0.18) * (1 - ease((t - 2.3) / 0.6)) + 0.8 * ease((t - 10.7) / 0.15) * (1 - ease((t - 10.9) / 0.3));
}

function prismAt(t: number) {
  'worklet';
  let g = ease((t - 2.3) / 0.3) * (1 - ease((t - 3.6) / 0.45));
  for (let k = 1; k <= 3; k++) {
    const start = ERASES[k - 1] + 0.3, typing = LETTER * WORDS[k].length;
    g += 0.55 * ease((t - start) / 0.15) * (1 - ease((t - start - typing) / 0.3));
  }
  return g;
}

/** 0 = the tag mark, 1 = the caret; scale is the swell before the squeeze. */
function markAt(t: number) {
  'worklet';
  if (t < 0.6) return { morph: 0, scale: 1 };
  if (t < 0.75) return { morph: 0, scale: 1 + 0.07 * ease((t - 0.6) / 0.15) };
  if (t < 1.1) { const u = (t - 0.75) / 0.35; return { morph: easeInOut(u), scale: 1 + 0.07 * (1 - ease(u)) }; }
  if (t < 11.1) return { morph: 1, scale: 1 };
  return { morph: 1 - easeOutBack((t - 11.1) / 0.4), scale: 1 };
}

/**
 * The Trove tag: a rounded rectangle whose top corners are cut at 45° by `cut`, with a punched hole.
 * Once `cut` is no larger than `r` it is a plain rounded rectangle (the caret).
 */
function markPath(cx: number, cy: number, w: number, h: number, r: number, cut: number, hole: number): SkPath {
  'worklet';
  const x = cx - w / 2, y = cy - h / 2;
  const p = Skia.Path.Make();
  p.setFillType(FillType.EvenOdd);
  if (cut > r) { p.moveTo(x + cut, y); p.lineTo(x + w - cut, y); p.lineTo(x + w, y + cut); }
  else { p.moveTo(x + r, y); p.lineTo(x + w - r, y); p.quadTo(x + w, y, x + w, y + r); }
  p.lineTo(x + w, y + h - r);
  p.quadTo(x + w, y + h, x + w - r, y + h);
  p.lineTo(x + r, y + h);
  p.quadTo(x, y + h, x, y + h - r);
  if (cut > r) p.lineTo(x, y + cut);
  else { p.lineTo(x, y + r); p.quadTo(x, y, x + r, y); }
  p.close();
  if (hole > 0.2) p.addCircle(cx, y + 12, hole);
  return p;
}

function textWidth(font: SkFont, text: string) {
  'worklet';
  if (text.length === 0) return 0;
  return font.getGlyphWidths(font.getGlyphIDs(text)).reduce((sum, n) => sum + n, 0);
}

function radial(c: SkCanvas, x: number, y: number, r: number, hex: number, alpha: number) {
  'worklet';
  if (alpha <= 0.002) return;
  const p = paint();
  p.setShader(Skia.Shader.MakeRadialGradient({ x, y }, r, [color(hex, alpha), color(hex, 0)], null, TileMode.Clamp));
  c.drawCircle(x, y, r, p);
}

function roundRect(x: number, y: number, w: number, h: number, r: number) {
  'worklet'; const p = Skia.Path.Make(); p.addRRect(Skia.RRectXY(Skia.XYWHRect(x, y, w, h), r, r)); return p;
}

function drawCards(c: SkCanvas, t: number, height: number, fy: number, art: Art | null) {
  'worklet';
  if (!art || t < 3.45 || t >= 10.95) return;
  const flow = t - 3.45;
  const image = Skia.Paint(); image.setAntiAlias(true);
  for (let index = 0; index < CARDS.length; index++) {
    const card = CARDS[index];
    const y = card.baseY - CARD_SPEEDS[card.column] * flow;
    const cx = card.x + card.width / 2, cy = y + card.height / 2;
    // Bloom out of the field.
    const delay = Math.min(1, Math.abs(cy - fy) / 500) * 0.45 + card.column * 0.04;
    const p = clamp((t - 3.45 - delay) / 0.8);
    if (p <= 0) continue;
    const grow = easeOut(p);
    const reach = lerp(0.3, 1, grow);
    let x = CENTER_X + (cx - CENTER_X) * reach;
    let yy = fy + (cy - fy) * reach;
    const size = lerp(0.4, 1, grow);
    let alpha = ease(p * 1.4);
    const sharp = ease((p - 0.3) / 0.6);
    // Fly away at the end.
    const exitDelay = card.column * 0.05 + clamp(cy / height) * 0.1;
    const away = clamp((t - 10.2 - exitDelay) / 0.5);
    yy -= 1100 * away * away * away;
    alpha *= 1 - ease((t - 10.55 - exitDelay) / 0.2);
    // Category swaps, top to bottom.
    const swapDelay = clamp(cy / height) * 0.4 + card.column * 0.05;
    let from = 0, swap = 0;
    for (let k = 1; k <= 3; k++) {
      const u = clamp((t - ERASES[k - 1] - 0.25 - swapDelay) / 0.4);
      if (u >= 1) from = k; else { swap = ease(u); break; }
    }
    // The card turns edge-on and back, and shows the next category's artwork once past edge-on.
    const flip = Math.abs(Math.cos(Math.PI * swap));
    const shown = swap < 0.5 ? from : from + 1;
    alpha *= 0.55 + 0.45 * flip;
    const w = card.width * size * flip, h = card.height * size;
    x -= w / 2; yy -= h / 2;
    if (yy > height + 20 || yy + h < -20 || alpha <= 0.002 || w < 0.5) continue;
    const dst = Skia.XYWHRect(x, yy, w, h);
    if (sharp < 1 && shown === 0) {
      const soft = art.soft[index];
      image.setAlphaf(alpha);
      c.drawImageRectOptions(soft, Skia.XYWHRect(0, 0, soft.width(), soft.height()), dst, FilterMode.Linear, MipmapMode.None, image);
      image.setAlphaf(alpha * sharp);
    } else {
      image.setAlphaf(alpha);
    }
    const picture = art.sharp[shown][index];
    c.drawImageRectOptions(picture, Skia.XYWHRect(0, 0, picture.width(), picture.height()), dst, FilterMode.Linear, MipmapMode.None, image);
  }
}

/** Draws one frame from the loop time alone. */
function drawScene(c: SkCanvas, font: SkFont, t: number, height: number, art: Art | null) {
  'worklet';
  // The text starts where "Search anything" is centred, like the pin; longer words run on to the right.
  const textX = CENTER_X - textWidth(font, 'Search anything') / 2;
  const fy = Math.round(height * 0.44);
  drawCards(c, t, height, fy, art);
  // A black band keeps the field clear of the cards (it comes in as they bloom); a scrim keeps the status bar legible.
  const band = paint(ease((t - 3.7) / 0.7));
  band.setShader(Skia.Shader.MakeLinearGradient({ x: 0, y: fy - 170 }, { x: 0, y: fy + 170 }, [color(0, 0), color(0), color(0), color(0, 0)], [0, 0.18, 0.82, 1], TileMode.Clamp));
  c.drawRect(Skia.XYWHRect(-20, fy - 170, 433, 340), band);
  const scrim = paint();
  scrim.setShader(Skia.Shader.MakeLinearGradient({ x: 0, y: 0 }, { x: 0, y: 90 }, [color(0, 0.75), color(0, 0)], null, TileMode.Clamp));
  c.drawRect(Skia.XYWHRect(-20, -20, 433, 110), scrim);

  const typing = typed(t);
  const open = pillOpen(t), streak = streakAt(t), prism = prismAt(t);
  const caretX = lerp(CENTER_X, textX + textWidth(font, typing.text) + 2.5, open);

  // Haze and pill glow.
  radial(c, caretX + 60, fy - 70, 240, 0xffffff, 0.1 * prism);
  radial(c, caretX + 30, fy, 120, PINK, 0.12 * prism);
  radial(c, CENTER_X - 70, fy, 150, PINK, 0.22 * streak);
  radial(c, CENTER_X + 70, fy, 150, VIOLET, 0.22 * streak);

  // The field: the caret spreads into a pill that is only a faint glass hint while the glow is on, and nearly invisible at rest.
  if (open > 0) {
    const w = lerp(2.6, PILL_WIDTH, open), h = lerp(22, PILL_HEIGHT, open);
    const pill = roundRect(CENTER_X - w / 2, fy - h / 2, w, h, h / 2);
    const glow = Math.min(1, streak + prism);
    const glass = paint(); glass.setColor(color(0xffffff, lerp(1, 0.01 + 0.035 * glow, ease(open / 0.6)))); c.drawPath(pill, glass);
    const rim = paint(Math.min(1, 0.7 * streak + 0.3 * prism) * open);
    rim.setStyle(PaintStyle.Stroke); rim.setStrokeWidth(1.2);
    rim.setShader(Skia.Shader.MakeLinearGradient({ x: CENTER_X - w / 2, y: fy }, { x: CENTER_X + w / 2, y: fy }, [color(PINK), color(VIOLET), color(CYAN)], null, TileMode.Clamp));
    c.drawPath(pill, rim);
  }

  // The streak flaring across the field.
  if (streak > 0.002) {
    c.save();
    c.translate(CENTER_X, fy);
    c.scale(1, 0.09);
    const flare = paint();
    flare.setShader(Skia.Shader.MakeRadialGradient({ x: 0, y: 0 }, 170, [color(PINK, 0.9 * streak), color(VIOLET, 0.5 * streak), color(VIOLET, 0)], null, TileMode.Clamp));
    c.drawCircle(0, 0, 170, flare);
    c.restore();
    const half = (PILL_WIDTH / 2) * Math.max(open, 0.25);
    const core = paint();
    core.setShader(Skia.Shader.MakeLinearGradient({ x: CENTER_X - half, y: fy }, { x: CENTER_X + half, y: fy },
      [color(PINK, 0), color(PINK, streak), color(0xffffff, streak), color(VIOLET, streak), color(VIOLET, 0)], null, TileMode.Clamp));
    c.drawRect(Skia.XYWHRect(CENTER_X - half, fy - 0.75, 2 * half, 1.5), core);
  }

  // The placeholder.
  if (typing.text.length > 0) {
    const metrics = font.getMetrics();
    const ink = paint(); ink.setColor(color(0xffffff, 0.92));
    c.drawText(typing.text, textX, fy - (metrics.ascent + metrics.descent) / 2, ink, font);
  }

  // The prismatic fan to the right of the caret.
  if (prism > 0.002) {
    const layer = paint(Math.min(1, prism) * 0.95);
    c.saveLayer(layer, Skia.XYWHRect(caretX - 2, fy - 32, 44, 64));
    const quad = Skia.Path.Make();
    quad.moveTo(caretX + 2, fy - 12); quad.lineTo(caretX + 34, fy - 24); quad.lineTo(caretX + 34, fy + 20); quad.lineTo(caretX + 2, fy + 12); quad.close();
    const fan = paint();
    fan.setShader(Skia.Shader.MakeLinearGradient({ x: 0, y: fy - 24 }, { x: 0, y: fy + 20 }, [color(PINK), color(VIOLET), color(CYAN)], null, TileMode.Clamp));
    c.drawPath(quad, fan);
    const fade = paint();
    fade.setBlendMode(BlendMode.DstIn);
    fade.setShader(Skia.Shader.MakeLinearGradient({ x: caretX + 2, y: 0 }, { x: caretX + 34, y: 0 }, [color(0xffffff), color(0xffffff, 0)], null, TileMode.Clamp));
    c.drawRect(Skia.XYWHRect(caretX, fy - 30, 40, 60), fade);
    c.restore();
    radial(c, caretX + 12, fy - 8, 26, PINK, 0.5 * prism);
    radial(c, caretX + 12, fy + 10, 18, CYAN, 0.35 * prism);
    radial(c, caretX, fy, 14, 0xffffff, 0.35 * prism);
  }

  // The caret, or the mark it comes from.
  if (t < 1.1 || t >= 11.1) {
    const { morph: m, scale: s } = markAt(t);
    const w = lerp(38, 2.6, m) * s, h = lerp(50, 22, m) * s, r = lerp(8, 1.3, m) * s;
    const cut = Math.min(13, Math.max(0, 12 * (1 - m * 1.7))) * s;
    const hole = 4.2 * clamp(1 - m * 2) * s;
    c.drawPath(markPath(CENTER_X, fy, w, h, r, cut, hole), paint());
  } else {
    const alpha = caretAlpha(t, typing);
    if (alpha > 0) c.drawPath(roundRect(caretX - 1.3, fy - 11, 2.6, 22, 1.3), paint(alpha));
  }
}

/** Deterministic scene; previewTime is optional for still previews. */
export function GlowSearchSplash({ autoPlay = false, previewTime }: { autoPlay?: boolean; previewTime?: number }) {
  const { width, height } = useWindowDimensions();
  const reduced = useReducedMotion();
  const [active, setActive] = useState(AppState.currentState === 'active');
  useEffect(() => { const sub = AppState.addEventListener('change', state => setActive(state === 'active')); return () => sub.remove(); }, []);
  const time = useSharedValue(0), blend = useSharedValue(0), elapsed = useSharedValue(0), held = useSharedValue(0);
  const dragStart = useSharedValue(0), touching = useSharedValue(false), released = useSharedValue(-1);
  useEffect(() => { time.set(previewTime !== undefined ? previewTime % LOOP : reduced ? REST_TIME : 0); blend.set(0); }, [previewTime, reduced, time, blend]);
  const frame = useFrameCallback(info => {
    const dt = (info.timeSincePreviousFrame ?? 0) / 1000;
    elapsed.set(elapsed.get() + dt);
    let mix = 0;
    if (touching.get()) { time.set(held.get()); }
    else if (released.get() >= 0) {
      const age = elapsed.get() - released.get();
      if (age < 2) time.set(held.get());
      else if (age < 2.6) { time.set(held.get()); mix = ease((age - 2) / 0.6); }
      else time.set(autoPlay ? (age - 2.6) % LOOP : 0);
    } else { time.set(autoPlay ? elapsed.get() % LOOP : 0); }
    blend.set(mix);
  }, false);
  useEffect(() => { frame.setActive(active && !reduced && previewTime === undefined); return () => frame.setActive(false); }, [active, reduced, previewTime, frame]);

  // The card images, made once and disposed a moment after they are let go, so a frame being drawn never meets a dead image.
  const art = useSharedValue<Art | null>(null);
  useEffect(() => {
    let made: Art;
    try { made = bakeArt(Math.max(1, Math.min(3, (width / 393) * PixelRatio.get()))); } catch { return; }
    art.set(made);
    return () => { art.set(null); setTimeout(() => disposeArt(made), 500); };
  }, [art, width]);

  const font = useMemo(() => matchFont({ fontFamily: TEXT_FAMILY, fontSize: 17, fontWeight: '400' }), []);
  const picture = useDerivedValue(() => {
    const recorder = Skia.PictureRecorder();
    const canvas = recorder.beginRecording(Skia.XYWHRect(0, 0, width, height));
    canvas.drawColor(Skia.Color('#000000'));
    const scale = width / 393, virtual = height / scale;
    canvas.scale(scale, scale);
    const t = time.get(), mix = blend.get(), images = art.get();
    if (mix <= 0) {
      drawScene(canvas, font, t, virtual, images);
    } else {
      canvas.saveLayer(paint(1 - mix)); drawScene(canvas, font, t, virtual, images); canvas.restore();
      canvas.saveLayer(paint(mix)); drawScene(canvas, font, 0, virtual, images); canvas.restore();
    }
    return recorder.finishRecordingAsPicture();
  }, [width, height, font]);

  const haptic = (strong: boolean) => { void Haptics.impactAsync(strong ? Haptics.ImpactFeedbackStyle.Soft : Haptics.ImpactFeedbackStyle.Light); };
  const pan = Gesture.Pan().minDistance(0).enabled(!reduced && previewTime === undefined)
    .onBegin(() => { dragStart.set(time.get()); held.set(time.get()); touching.set(true); scheduleOnRN(haptic, false); })
    .onUpdate(e => { held.set(Math.max(0, Math.min(LOOP - 0.01, dragStart.get() + (e.translationX / width) * 8))); })
    // A tap never activates the pan (it does not move), so it is read here: onEnd only runs after a drag.
    .onFinalize(e => {
      if (Math.abs(e.translationX) < 8 && Math.abs(e.translationY) < 8) {
        const start = dragStart.get();
        let next = BEATS[0];
        for (let i = 0; i < BEATS.length; i++) { if (BEATS[i] > start + 0.05) { next = BEATS[i]; break; } }
        held.set(next);
        scheduleOnRN(haptic, true);
      }
      touching.set(false); released.set(elapsed.get());
    });
  return <GestureDetector gesture={pan}><View style={styles.screen} accessible accessibilityLabel="Trove. Search anything: illustration, typography, 3D shapes. Tap to skip ahead, drag to scrub.">
    <StatusBar style="light" />
    <Canvas style={StyleSheet.absoluteFill}><Picture picture={picture} /></Canvas>
  </View></GestureDetector>;
}
const styles = StyleSheet.create({ screen: { flex: 1, backgroundColor: '#000000' } });
