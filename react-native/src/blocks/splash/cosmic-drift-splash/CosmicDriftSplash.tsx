import { useEffect, useMemo, useState } from 'react';
import { AppState, Platform, StyleSheet, useWindowDimensions, View } from 'react-native';
import {
  BlurStyle, Canvas, PaintStyle, Picture, Skia, StrokeCap, TileMode, matchFont, useFont,
  type SkCanvas, type SkFont, type SkImage, type SkPaint,
} from '@shopify/react-native-skia';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import { useDerivedValue, useFrameCallback, useReducedMotion, useSharedValue } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import {
  BLUR_LEVELS, MOON_ANGLE, MOON_RADIUS, ORBITS, PLANET_RADIUS, SPECKS, SPHERES, SPRITE_SPAN, STARS, STROKE,
  TRAILS, VISUAL_OFFSET, buildSprites, smoothstep,
} from './CosmicDriftArt';

/*
 Cosmic Drift Splash: the launch of Orrery, an invented calm web browser. A port of the Swift
 reference (CosmicDriftSplash.swift); the timeline is written out at the top of that file.
 LOOP = 11 s: end card rest 0–1, card out 1–2.1 (the camera flies into the mark), fly-through of
 56 glossy spheres 1.9–4.1, recede into a spiral 4.1–5.5, dot 5.7, blob 5.85–6.35, morph into the
 mark 6.35–7.15, backdrop back to graphite 7.5–8.2, brand in 7.9–9.2, rest to 11.
*/
export const LOOP = 11;
const REST_TIME = 9.6;
const BG = '#13181A';

function seg(t: number, a: number, b: number) { 'worklet'; return Math.max(0, Math.min(1, (t - a) / (b - a))); }
function easeInOut(v: number) {
  'worklet'; const u = Math.max(0, Math.min(1, v)); return u < 0.5 ? 4 * u * u * u : 1 - Math.pow(-2 * u + 2, 3) / 2;
}
function easeOut(v: number) { 'worklet'; const u = Math.max(0, Math.min(1, v)); return 1 - Math.pow(1 - u, 3); }
function easeIn(v: number) { 'worklet'; const u = Math.max(0, Math.min(1, v)); return u * u * u; }
function easeOutBack(v: number, s: number) { 'worklet'; const u = Math.max(0, Math.min(1, v)) - 1; return 1 + u * u * ((s + 1) * u + s); }
function smooth(u: number) { 'worklet'; return u * u * (3 - 2 * u); }
function mix(a: number, b: number, k: number) { 'worklet'; return a + (b - a) * k; }
/** 1 on the end card, 0 while it is away: out over [a, b], back in over [c, d] (ease-in-out, or in/out). */
function card(t: number, a: number, b: number, c: number, d: number, inOut = true) {
  'worklet';
  if (t < c) return 1 - (inOut ? easeInOut(seg(t, a, b)) : easeIn(seg(t, a, b)));
  return inOut ? easeInOut(seg(t, c, d)) : easeOut(seg(t, c, d));
}
function rgba(r: number, g: number, b: number, a: number) {
  'worklet'; return Skia.Color(`rgba(${Math.round(r * 255)},${Math.round(g * 255)},${Math.round(b * 255)},${Math.max(0, Math.min(1, a))})`);
}

type Fonts = { word: SkFont; tag: SkFont; button: SkFont };

/** Width of a run of text, from the glyphs' own advances (measureText rounds each glyph). */
function textWidth(font: SkFont, text: string, tracking = 0) {
  'worklet';
  const widths = font.getGlyphWidths(font.getGlyphIDs(text));
  let sum = 0;
  for (const w of widths) sum += w;
  return sum + tracking * Math.max(0, text.length - 1);
}
/** Text centred on (x, y), drawn glyph by glyph when it is tracked. */
function centred(c: SkCanvas, font: SkFont, text: string, x: number, y: number, paint: SkPaint, tracking = 0) {
  'worklet';
  const m = font.getMetrics();
  const baseline = y - (m.ascent + m.descent) / 2;
  if (tracking === 0) { c.drawText(text, x - textWidth(font, text) / 2, baseline, paint, font); return; }
  let cursor = x - textWidth(font, text, tracking) / 2;
  for (const ch of text) { c.drawText(ch, cursor, baseline, paint, font); cursor += textWidth(font, ch) + tracking; }
}

function drawStars(c: SkCanvas, w: number, h: number, t: number, visibility: number) {
  'worklet';
  // Bucketed by brightness, as in the Swift version: sixteen paths a frame, not 260 fills.
  const white: ReturnType<typeof Skia.Path.Make>[] = [], blue: ReturnType<typeof Skia.Path.Make>[] = [];
  for (let k = 0; k < 8; k++) { white.push(Skia.Path.Make()); blue.push(Skia.Path.Make()); }
  for (const s of STARS) {
    let b = s.brightness;
    if (s.twinkles) b *= 0.55 + 0.45 * Math.sin(2 * Math.PI * s.rate * t / LOOP + s.phase);
    const k = Math.min(7, Math.max(0, Math.trunc(b * 8)));
    (s.blue ? blue : white)[k].addCircle(s.u * w, s.v * h, s.size);
  }
  const p = Skia.Paint(); p.setAntiAlias(true);
  for (let k = 0; k < 8; k++) {
    const a = (k + 0.5) / 8 * visibility;
    p.setColor(rgba(0.93, 0.95, 1, a)); c.drawPath(white[k], p);
    p.setColor(rgba(0.62, 0.74, 1, a)); c.drawPath(blue[k], p);
  }
}

function camera(t: number) {
  'worklet';
  const tf = t - 1.9;
  if (tf < 2.2) return 4.2 * tf;
  const s = Math.min(tf - 2.2, 1.4);
  return 9.24 + 4.2 * (s - s * s / 2.8);
}
function recede(t: number) { 'worklet'; return easeInOut(seg(t, 4.1, 5.5)); }
function roll(t: number) { 'worklet'; return 0.32 * (t - 1.9) + 4.2 * recede(t); }
/** Continuous index into BLUR_LEVELS for a blur given as a fraction of the diameter. */
function blurLevel(blur: number) {
  'worklet';
  for (let i = 0; i < BLUR_LEVELS.length - 1; i++) {
    if (blur <= BLUR_LEVELS[i + 1]) return i + (blur - BLUR_LEVELS[i]) / (BLUR_LEVELS[i + 1] - BLUR_LEVELS[i]);
  }
  return BLUR_LEVELS.length - 1;
}

type Projected = { x: number; y: number; radius: number; depth: number; alpha: number; blur: number; palette: number; angle: number; distance: number };

function drawField(c: SkCanvas, w: number, h: number, t: number, sx: number, sy: number, sprites: SkImage[][]) {
  'worklet';
  if (t <= 1.85 || t >= 5.75) return;
  const show = smooth(seg(t, 1.9, 2.6)) * (1 - smooth(seg(t, 5.05, 5.7)));
  const cam = camera(t), e = recede(t), phi = roll(t);
  const spin = (roll(t + 0.01) - phi) / 0.01;
  const centring = easeInOut(seg(t, 4.0, 5.4));
  const vx = w / 2 + w * 0.10 * (1 - centring) + sx;
  const vy = h / 2 - h * 0.08 * (1 - centring) + sy;
  const f = 0.85 * w;

  const items: Projected[] = [];
  for (const s of SPHERES) {
    const d0 = s.z - cam + 1.2;
    if (d0 < 0.25) continue; // passed the camera: it never comes back
    const d = d0 * (1 + 6 * e) + 6 * e;
    if (d < 0.25 || d > 16) continue;
    const a = s.angle + phi;
    const x = vx + f * s.distance * Math.cos(a) / d, y = vy + f * s.distance * Math.sin(a) / d;
    const r = f * s.radius / d;
    const reach = r * SPRITE_SPAN / 2;
    if (x + reach < 0 || x - reach > w || y + reach < 0 || y - reach > h) continue;
    const fog = 1 - smoothstep(6, 14, d), near = smoothstep(0.25, 0.7, d0);
    const focus = 2.2;
    let blur = d < focus ? 0.12 * (focus / d - 1) : 0.05 * (d / focus - 1);
    blur = Math.min(0.17, blur + 0.06 * e);
    items.push({ x, y, radius: r, depth: d, alpha: show * fog * near, blur, palette: s.palette,
      angle: Math.atan2(y - vy, x - vx), distance: Math.hypot(x - vx, y - vy) });
  }
  items.sort((p, q) => q.depth - p.depth);

  // Arc trails while the field spirals away.
  if (e > 0.02) {
    const sweep = Math.min(1.6, spin * 0.35);
    const p = Skia.Paint(); p.setAntiAlias(true); p.setStyle(PaintStyle.Stroke); p.setStrokeCap(StrokeCap.Round);
    for (const q of items) {
      if (q.distance <= q.radius) continue;
      p.setColor(Skia.Color(TRAILS[q.palette])); p.setAlphaf(Math.max(0, Math.min(1, 0.18 * q.alpha * Math.min(1, e * 3))));
      p.setStrokeWidth(Math.max(1, Math.min(q.radius * 0.9, 10)));
      const arc = Skia.Path.Make();
      arc.addArc(Skia.XYWHRect(vx - q.distance, vy - q.distance, q.distance * 2, q.distance * 2), (q.angle - sweep) * 180 / Math.PI, sweep * 180 / Math.PI);
      c.drawPath(arc, p);
    }
  }

  const ip = Skia.Paint(); ip.setAntiAlias(true);
  for (const q of items) {
    if (q.alpha <= 0.005) continue;
    const level = blurLevel(q.blur);
    const lo = Math.trunc(level), hi = Math.min(BLUR_LEVELS.length - 1, lo + 1), k = level - lo;
    const half = q.radius * SPRITE_SPAN;
    const dst = Skia.XYWHRect(q.x - half, q.y - half, half * 2, half * 2);
    const src = Skia.XYWHRect(0, 0, sprites[q.palette][lo].width(), sprites[q.palette][lo].height());
    ip.setAlphaf(q.alpha);
    c.drawImageRect(sprites[q.palette][lo], src, dst, ip);
    if (k > 0.01 && hi !== lo) {
      ip.setAlphaf(q.alpha * k);
      c.drawImageRect(sprites[q.palette][hi], src, dst, ip);
    }
  }

  // Dust glints: bucketed by brightness, with a faint halo on the brightest.
  const dots: ReturnType<typeof Skia.Path.Make>[] = [];
  for (let k = 0; k < 6; k++) dots.push(Skia.Path.Make());
  const halos = Skia.Path.Make();
  const cr = Math.cos(phi), sr = Math.sin(phi);
  for (const s of SPECKS) {
    const d0 = s.z - cam + 1.2;
    if (d0 < 0.3) continue;
    const d = d0 * (1 + 6 * e) + 6 * e;
    if (d < 0.3 || d > 16) continue;
    const rx = s.x * cr - s.y * sr, ry = s.x * sr + s.y * cr;
    const x = vx + f * rx / d, y = vy + f * ry / d;
    if (x < -4 || x > w + 4 || y < -4 || y > h + 4) continue;
    const tw = Math.pow(0.5 + 0.5 * Math.sin(2 * Math.PI * s.rate * t / LOOP + s.phase), 3);
    const a = (0.35 + 0.65 * tw) * (1 - smoothstep(6, 14, d)) * smoothstep(0.3, 0.8, d0);
    const r = Math.min(2.6, Math.max(0.5, f * 0.009 * s.size / d));
    dots[Math.min(5, Math.trunc(a * 6))].addCircle(x, y, r);
    if (a > 0.8) halos.addCircle(x, y, r * 3);
  }
  const dp = Skia.Paint(); dp.setAntiAlias(true);
  dp.setColor(rgba(1, 1, 1, 0.12 * show)); c.drawPath(halos, dp);
  for (let k = 0; k < 6; k++) { dp.setColor(rgba(1, 1, 1, (k + 0.5) / 6 * show)); c.drawPath(dots[k], dp); }
}

/** An orbit drawn from its middle outwards: progress 0 is nothing, 1 the whole arc. */
function orbitPath(orbit: [number, number, number], progress: number) {
  'worklet';
  const path = Skia.Path.Make();
  if (progress <= 0.001) return path;
  const mid = (orbit[1] + orbit[2]) / 2, halfSweep = (orbit[2] - orbit[1]) / 2 * progress;
  const a0 = (mid - halfSweep) * Math.PI / 180, a1 = (mid + halfSweep) * Math.PI / 180;
  const steps = Math.max(2, Math.trunc(48 * progress));
  for (let s = 0; s <= steps; s++) {
    const a = a0 + (a1 - a0) * s / steps;
    const x = orbit[0] * Math.cos(a), y = orbit[0] * Math.sin(a);
    if (s === 0) path.moveTo(x, y); else path.lineTo(x, y);
  }
  return path;
}

function drawMark(c: SkCanvas, w: number, h: number, t: number) {
  'worklet';
  const unit = 0.92; // points per mark unit: a 92 pt mark
  const rise = card(t, 1.05, 1.6, 7.9, 8.6);
  const cy = mix(h * 0.5, h * 0.405, rise);
  const px = w / 2 + VISUAL_OFFSET.x * unit, py = cy + VISUAL_OFFSET.y * unit;

  let scale = 1, alpha = 1, turn = 0, blob = 0, wobble = 0, outer = 1, inner = 1, moon = 1;
  if (t < 5.7) {
    if (t > 2.1) return;
    scale = 1 + 3 * easeIn(seg(t, 1.6, 2.1));
    alpha = 1 - seg(t, 1.75, 2.1);
  } else {
    const dot = easeOut(seg(t, 5.7, 5.85)) * 5;
    const swell = easeOutBack(seg(t, 5.85, 6.35), 1.2);
    const shrink = easeInOut(seg(t, 6.35, 6.95));
    blob = t < 5.85 ? dot : mix(mix(5, 44, swell), PLANET_RADIUS * unit, shrink);
    wobble = 0.07 * Math.sin(Math.PI * seg(t, 5.85, 6.7));
    outer = easeOut(seg(t, 6.38, 6.9)); inner = easeOut(seg(t, 6.5, 7.0));
    moon = easeOutBack(seg(t, 6.85, 7.15), 2.2);
    turn = -0.35 * (1 - easeOut(seg(t, 6.35, 7.2)));
  }
  if (alpha <= 0.002) return;

  const white = rgba(0.96, 0.96, 0.95, 1);
  const layer = Skia.Paint(); layer.setAlphaf(alpha);
  c.saveLayer(layer);
  c.translate(px, py);
  c.scale(scale, scale);
  c.rotate(turn * 180 / Math.PI, 0, 0);
  c.save();
  c.scale(unit, unit);
  const stroke = Skia.Paint(); stroke.setAntiAlias(true); stroke.setColor(white); stroke.setStyle(PaintStyle.Stroke);
  stroke.setStrokeWidth(STROKE); stroke.setStrokeCap(StrokeCap.Round);
  c.drawPath(orbitPath(ORBITS[1], outer), stroke);
  c.drawPath(orbitPath(ORBITS[0], inner), stroke);
  const fill = Skia.Paint(); fill.setAntiAlias(true); fill.setColor(white);
  if (moon > 0.001) {
    const a = MOON_ANGLE * Math.PI / 180, r = ORBITS[1][0];
    c.drawCircle(r * Math.cos(a), r * Math.sin(a), MOON_RADIUS * moon, fill);
  }
  if (t < 5.7) c.drawCircle(0, 0, PLANET_RADIUS, fill);
  c.restore();
  if (t >= 5.7 && blob > 0.01) {
    // The blob: a circle whose radius wobbles in three and five lobes while it swells.
    const path = Skia.Path.Make();
    for (let i = 0; i <= 64; i++) {
      const a = i / 64 * 2 * Math.PI;
      const rr = blob * (1 + wobble * (0.6 * Math.sin(3 * a + 9 * t) + 0.4 * Math.sin(5 * a - 7 * t)));
      if (i === 0) path.moveTo(rr * Math.cos(a), rr * Math.sin(a)); else path.lineTo(rr * Math.cos(a), rr * Math.sin(a));
    }
    path.close();
    c.drawPath(path, fill);
  }
  c.restore();
}

function drawCard(c: SkCanvas, fonts: Fonts, w: number, h: number, top: number, bottom: number, t: number) {
  'worklet';
  const word = card(t, 1.0, 1.45, 8.1, 8.9, false);
  const tag = card(t, 1.0, 1.4, 8.4, 9.1, false);
  const cta = card(t, 1.0, 1.35, 8.5, 9.2, false);
  const close = card(t, 1.0, 1.3, 8.7, 9.2, false);
  const markY = h * 0.405;

  if (word > 0.002) {
    const p = Skia.Paint(); p.setAntiAlias(true); p.setColor(rgba(0.945, 0.949, 0.937, word));
    // SwiftUI's blur radius is the Gaussian's sigma, in points.
    const blur = 14 * (1 - word);
    if (blur > 0.2) p.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, blur, true));
    c.save();
    c.translate(w / 2, markY + 76);
    const s = 1 + 0.06 * (1 - word); c.scale(s, s);
    centred(c, fonts.word, 'orrery', 0, 0, p, 0.5);
    c.restore();
  }
  if (tag > 0.002) {
    const p = Skia.Paint(); p.setAntiAlias(true); p.setColor(rgba(0.55, 0.59, 0.6, tag));
    const blur = 6 * (1 - tag);
    if (blur > 0.2) p.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, blur, true));
    centred(c, fonts.tag, 'A quieter way to explore the web.', w / 2, markY + 120 + 6 * (1 - tag), p);
  }
  if (cta > 0.002) {
    const height = 54, side = 24;
    const y = h - Math.max(bottom, 16) - 10 - height + 10 * (1 - cta);
    const rect = Skia.XYWHRect(side, y, w - side * 2, height);
    // The pill comes up from a dim teal to its full colour.
    const p = Skia.Paint(); p.setAntiAlias(true);
    p.setColor(rgba(mix(0.11, 0.149, cta), mix(0.29, 0.651, cta), mix(0.31, 0.718, cta), cta));
    c.drawRRect(Skia.RRectXY(rect, height / 2, height / 2), p);
    const gloss = Skia.Paint(); gloss.setAntiAlias(true);
    gloss.setShader(Skia.Shader.MakeLinearGradient({ x: 0, y }, { x: 0, y: y + height / 2 },
      [rgba(1, 1, 1, 0.14 * cta), rgba(1, 1, 1, 0)], null, TileMode.Clamp));
    c.drawRRect(Skia.RRectXY(Skia.XYWHRect(side + 1, y + 1, w - side * 2 - 2, height - 2), height / 2 - 1, height / 2 - 1), gloss);
    const tp = Skia.Paint(); tp.setAntiAlias(true); tp.setColor(rgba(0.03, 0.10, 0.11, cta));
    centred(c, fonts.button, 'Get started', w / 2, y + height / 2, tp);
  }
  if (close > 0.002) {
    const r = 17, cx = w - 20 - r, cy = top + 10 + r;
    const p = Skia.Paint(); p.setAntiAlias(true); p.setColor(rgba(1, 1, 1, 0.07 * close));
    c.drawCircle(cx, cy, r, p);
    const x = Skia.Paint(); x.setAntiAlias(true); x.setColor(rgba(1, 1, 1, 0.75 * close)); x.setStyle(PaintStyle.Stroke);
    x.setStrokeWidth(1.7); x.setStrokeCap(StrokeCap.Round);
    const s = 5.5;
    c.drawLine(cx - s, cy - s, cx + s, cy + s, x);
    c.drawLine(cx + s, cy - s, cx - s, cy + s, x);
  }
}

function draw(c: SkCanvas, fonts: Fonts, w: number, h: number, top: number, bottom: number, t: number, sx: number, sy: number, sprites: SkImage[][] | null) {
  'worklet';
  const lit = card(t, 1.4, 2.0, 7.5, 8.2);
  c.drawColor(rgba(mix(0.012, 0.075, lit), mix(0.016, 0.094, lit), mix(0.018, 0.102, lit), 1));
  // A soft pool of light behind the mark on the end card.
  if (lit > 0.01) {
    const glow = Skia.Paint();
    glow.setShader(Skia.Shader.MakeRadialGradient({ x: w / 2, y: h * 0.42 }, w * 0.9,
      [rgba(1, 1, 1, 0.035 * lit), rgba(1, 1, 1, 0)], null, TileMode.Clamp));
    c.drawCircle(w / 2, h * 0.42, w * 0.9, glow);
  }
  drawStars(c, w, h, t, 0.42 + 0.58 * (1 - lit));
  if (sprites) drawField(c, w, h, t, sx, sy, sprites);
  drawMark(c, w, h, t);
  drawCard(c, fonts, w, h, top, bottom, t);
}

/**
 * The Orrery splash: a cinematic fly-through of glossy planets, a starfield, a dot that swells and
 * morphs into the mark, then the brand. previewTime pins one moment for still previews.
 */
export function CosmicDriftSplash({ autoPlay = false, previewTime }: { autoPlay?: boolean; previewTime?: number }) {
  const { width, height } = useWindowDimensions();
  const insets = useSafeAreaInsets();
  const reduced = useReducedMotion();
  const [active, setActive] = useState(AppState.currentState === 'active');
  useEffect(() => { const sub = AppState.addEventListener('change', state => setActive(state === 'active')); return () => sub.remove(); }, []);

  // Sprites are built once after the first frame; the loop opens on the still end card.
  const [sprites, setSprites] = useState<SkImage[][] | null | undefined>(undefined);
  useEffect(() => {
    const id = setTimeout(() => setSprites(buildSprites()), 50);
    return () => clearTimeout(id);
  }, []);

  const wordFont = useFont(require('./assets/Questrial-Regular.ttf'), 46);
  const tagFont = useFont(require('./assets/Questrial-Regular.ttf'), 15);
  const fonts = useMemo<Fonts | null>(() => {
    if (!wordFont || !tagFont) return null;
    const button = matchFont({ fontFamily: Platform.OS === 'ios' ? 'Helvetica' : 'sans-serif', fontSize: 17, fontWeight: '600' });
    return { word: wordFont, tag: tagFont, button };
  }, [wordFont, tagFont]);

  const time = useSharedValue(0), elapsed = useSharedValue(0), held = useSharedValue(0), scrubFrom = useSharedValue(0);
  const touching = useSharedValue(false), released = useSharedValue(-1);
  const steerX = useSharedValue(0), steerY = useSharedValue(0), sx = useSharedValue(0), sy = useSharedValue(0);
  useEffect(() => { time.set(previewTime !== undefined ? previewTime % LOOP : reduced ? REST_TIME : 0); }, [previewTime, reduced, time]);
  const frame = useFrameCallback(info => {
    const dt = (info.timeSincePreviousFrame ?? 0) / 1000;
    elapsed.set(elapsed.get() + dt);
    let k = 0;
    if (touching.get()) { time.set(held.get()); k = 1; }
    else if (released.get() >= 0) {
      const age = elapsed.get() - released.get();
      const h0 = held.get();
      if (age < 2) { time.set(h0); k = 1; }
      else if (age < 2.8) {
        const target = h0 >= 6 ? LOOP : 0;
        time.set(h0 + (target - h0) * easeInOut((age - 2) / 0.8));
        k = 1 - easeInOut((age - 2) / 0.8);
      } else time.set(autoPlay ? (age - 2.8) % LOOP : 0);
    } else time.set(autoPlay ? elapsed.get() % LOOP : 0);
    sx.set(steerX.get() * k); sy.set(steerY.get() * k);
  }, false);
  // The clock starts once the fonts and the sprites are in, so the first loop opens on its full rest on the end card.
  const ready = fonts !== null && sprites !== undefined; // null: the sprites failed, the loop runs without the field
  useEffect(() => { if (ready) elapsed.set(0); }, [ready, elapsed]);
  useEffect(() => { frame.setActive(active && !reduced && previewTime === undefined && ready); return () => frame.setActive(false); }, [active, reduced, previewTime, frame, ready]);

  const top = insets.top, bottom = insets.bottom;
  const picture = useDerivedValue(() => {
    const recorder = Skia.PictureRecorder();
    const canvas = recorder.beginRecording(Skia.XYWHRect(0, 0, width, height));
    if (fonts) draw(canvas, fonts, width, height, top, bottom, time.get(), sx.get(), sy.get(), sprites ?? null);
    else canvas.drawColor(Skia.Color(BG));
    return recorder.finishRecordingAsPicture();
  }, [width, height, fonts, top, bottom, sprites]);

  const light = () => { void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light); };
  const soft = () => { void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Soft); };
  const pan = Gesture.Pan().minDistance(0).enabled(!reduced && previewTime === undefined)
    .onBegin(() => {
      const current = time.get();
      scrubFrom.set(current < 9.2 ? current : 0);
      held.set(current);
      touching.set(true);
      scheduleOnRN(light);
    })
    .onUpdate(e => {
      held.set(Math.max(0, Math.min(LOOP - 0.01, scrubFrom.get() + e.translationX / Math.max(1, width) * 5)));
      steerX.set(Math.max(-60, Math.min(60, e.translationX * 0.15)));
      steerY.set(Math.max(-140, Math.min(140, e.translationY * 0.4)));
    })
    .onFinalize(e => {
      if (Math.abs(e.translationX) < 8 && Math.abs(e.translationY) < 8) {
        held.set(9.3); steerX.set(0); steerY.set(0);
        scheduleOnRN(soft);
      }
      touching.set(false);
      released.set(elapsed.get());
    });

  return <GestureDetector gesture={pan}>
    <View style={styles.screen} accessible accessibilityLabel="Orrery. A quieter way to explore the web. Get started. Drag to fly through the planets, tap to skip.">
      <StatusBar style="light" />
      <Canvas style={StyleSheet.absoluteFill}><Picture picture={picture} /></Canvas>
    </View>
  </GestureDetector>;
}

const styles = StyleSheet.create({ screen: { flex: 1, backgroundColor: BG } });
