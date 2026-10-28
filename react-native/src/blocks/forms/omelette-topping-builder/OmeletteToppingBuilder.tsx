import {
  BlurStyle,
  Canvas,
  ClipOp,
  matchFont,
  Picture,
  Skia,
  type SkCanvas,
  type SkColor,
  type SkFont,
  type SkPicture,
} from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useEffect, useMemo, useState } from 'react';
import { type LayoutChangeEvent, Platform, StyleSheet, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import { useDerivedValue, useFrameCallback, useReducedMotion, useSharedValue } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';

import { blob, hex, ingredient, linear, omelette, piece, plate, radial, ringShadow, sigma, solid, stroke } from './omeletteArt';
import {
  ANGLE,
  bezier,
  CENTROID_Y,
  CENTS,
  clamp01,
  COLLAPSE,
  type Driver,
  driverFrame,
  driverSession,
  driverTouched,
  easeIn,
  easeInOut,
  easeOut,
  easeOutBack,
  type Frame,
  KINDS,
  LAND,
  landingX,
  landingY,
  lastChange,
  LIFT_DUR,
  makeDriver,
  makeSim,
  money,
  NAMES,
  PIECE_SCALE,
  PIECES,
  PRESS,
  PRICE_ROLL,
  R,
  RESTOCK_AT,
  RESTOCK_DUR,
  round,
  SHAKE_DUR,
  simBeginDrag,
  simDragTo,
  simFrame,
  simRelease,
  simRemoveLast,
  simSelect,
  simTapCentre,
  slot,
  smooth,
  type Topping,
  total,
} from './omeletteMotion';

/*
 Omelette Topping Builder — step 2 of an invented breakfast-ordering app ("Early Fold"): fold
 fillings into your omelette (a port of OmeletteToppingBuilder.swift).

 Look: a light grey screen, a title bar ("Omelette", a round back button, "Step 2 of 3"), a white
 ceramic plate on a pale placemat with a folded yellow omelette, all drawn in code. Below, a shelf
 of five ingredients in a ring (Red Bell Pepper, Baby Spinach, Smoked Bacon, Cherry Tomato, Button
 Mushroom), the centred one on a white disc with its name and add-on price, and a dark "Continue"
 button carrying the total. The still layers (screen, header, plate, omelette) are recorded once
 into Skia pictures; each frame draws them and the moving parts into one picture.

 Hero (points / scatter): the centred ingredient is tossed onto the omelette and bursts into seeded
 pieces — red pepper cubes, spinach leaves, bacon strips, tomato halves, mushroom slices — that hop
 outward, spin to their angle and land with a squash-and-bounce. They stay and accumulate; the
 omelette squashes on each impact and the total rolls up. The ingredient pops back on the shelf with
 a check, and the shelf slides on to the next one.

 autoPlay: LOOP = 9 s (rest, add pepper, spinach and bacon, hold, everything lifts off and the shelf
 glides back to the pepper, rest). omeletteMotion.ts has the script.
 Touch: tap the centred ingredient to add it (an added one gives a small shake); tap a side one to
 centre it; swipe the shelf; tap the plate to lift off the last topping. A light haptic when a
 topping lands and on undo. 2 s after the last touch every topping lifts off, the shelf springs back
 to the pepper and the loop restarts.
 Reduced motion: the rest frame, still; a tap fades the pieces in at their spots, without the flight.
*/

type Props = {
  /** Plays the 9 s add-three-toppings loop until touched. */
  autoPlay?: boolean;
};

const PAPER = 0xededec;
const INK = 0x17181c;
const MUTED = 0x8c8c90;

const SPACING = 124;
const ICON = 80;

type Geo = {
  w: number;
  h: number;
  top: number;
  cx: number;
  plateY: number;
  shelfY: number;
  nameY: number;
  priceY: number;
  buttonY: number;
};

function makeGeo(w: number, h: number, top: number, bottom: number): Geo {
  return {
    w,
    h,
    top,
    cx: w / 2,
    plateY: top + 268,
    shelfY: top + 536,
    nameY: top + 614,
    priceY: top + 637,
    buttonY: h - bottom - 72,
  };
}

type Fonts = { s20: SkFont; r13: SkFont; r15: SkFont; s17: SkFont; r14: SkFont };

// SF Pro's own tracking at these sizes (Core Text applies it, Skia does not), so the copy keeps
// the Swift widths on iOS.
const IOS = Platform.OS === 'ios';
const TRACK = { s20: IOS ? -0.45 : 0, r13: IOS ? -0.08 : 0, r15: IOS ? -0.23 : 0, s17: IOS ? -0.43 : 0, r14: IOS ? -0.15 : 0 };

/** Draws a line of text vertically centred on y: centred on x, starting at x (-1) or ending at x (1). */
function label(c: SkCanvas, s: string, font: SkFont, x: number, y: number, color: SkColor, anchor: number, tracking: number) {
  'worklet';
  const ids = font.getGlyphIDs(s);
  const widths = font.getGlyphWidths(ids);
  const pos = [];
  let cursor = 0;
  for (let i = 0; i < ids.length; i++) {
    pos.push({ x: cursor, y: 0 });
    cursor += widths[i] + tracking;
  }
  const w = cursor - tracking;
  const m = font.getMetrics();
  const x0 = anchor < 0 ? x : anchor > 0 ? x - w : x - w / 2;
  const p = Skia.Paint();
  p.setAntiAlias(true);
  p.setColor(color);
  c.drawGlyphs(ids, pos, x0, y - (m.ascent + m.descent) / 2, font, p);
}

/** Rotates a vector by -a (a screen vector into a frame turned by a): x component. */
function unturnX(x: number, y: number, a: number) {
  'worklet';
  return x * Math.cos(a) + y * Math.sin(a);
}

function unturnY(x: number, y: number, a: number) {
  'worklet';
  return -x * Math.sin(a) + y * Math.cos(a);
}

/** The screen, header and plate: everything that never moves. */
function drawStill(c: SkCanvas, g: Geo, fonts: Fonts) {
  'worklet';
  c.drawRect(Skia.XYWHRect(0, 0, g.w, g.h), solid(PAPER, 1, 1));
  const y = g.top + 28;
  ringShadow(c, 40, y + 2, 19, 25, 0.08);
  c.drawCircle(40, y, 20, solid(0xffffff, 1, 1));
  const chev = Skia.Path.Make();
  chev.moveTo(43.5, y - 6.5);
  chev.lineTo(37, y);
  chev.lineTo(43.5, y + 6.5);
  c.drawPath(chev, stroke(solid(INK, 1, 1), 2, true));
  label(c, 'Omelette', fonts.s20, g.cx, y, hex(INK), 0, TRACK.s20);
  label(c, 'Step 2 of 3', fonts.r13, g.w - 24, y, hex(MUTED), 1, TRACK.r13);
  label(c, 'Tap a filling to fold it in', fonts.r15, g.cx, g.top + 64, hex(MUTED), 0, TRACK.r15);
  plate(c, g.cx, g.plateY);
}

/** The omelette's squash: a damped dip on each impact. */
function squash(f: Frame) {
  'worklet';
  if (f.reduced) return 0;
  let k = 0;
  for (let i = 0; i < f.toppings.length; i++) {
    const tau = f.t - f.toppings[i].ta - LAND;
    if (tau > 0 && tau < 1.5) k += 0.035 * Math.exp(-7 * tau) * Math.sin(20 * tau);
  }
  return k;
}

function drawPieces(c: SkCanvas, f: Frame, tp: Topping) {
  'worklet';
  const k = tp.kind;
  const t = f.t;
  const lx0 = landingX(k);
  const ly0 = landingY(k);
  const tau = t - tp.ta - LAND;
  if (!f.reduced && tau < 0) return;
  const list = PIECES[k];
  for (let i = 0; i < list.length; i++) {
    const p = list[i];
    let alpha = 1;
    let x = p.x;
    let y = p.y;
    let sc = 1;
    let rot = p.rot;
    let hop = 0;
    if (f.reduced) {
      alpha = clamp01((t - tp.ta) / 0.3);
    } else {
      const dist = Math.hypot(p.x - lx0, p.y - ly0);
      const dur = 0.28 + (0.26 * dist) / R;
      const u = clamp01((tau - p.delay) / dur);
      const e = easeOut(u);
      x = lx0 + (p.x - lx0) * e;
      y = ly0 + (p.y - ly0) * e;
      hop = Math.sin(Math.PI * u) * (8 + (18 * dist) / R);
      sc = 1 + hop / 60;
      rot = p.rot + (1 - e) * p.spin;
      const tl = tau - p.delay - dur;
      if (tl > 0) sc *= 1 + 0.16 * Math.exp(-8 * tl) * Math.sin(-26 * tl);
    }
    if (tp.tr >= 0) {
      const w = clamp01((t - tp.tr - (f.reduced ? 0 : p.lift)) / (f.reduced ? 0.3 : LIFT_DUR));
      if (w >= 1) continue;
      if (f.reduced) {
        alpha *= 1 - w;
      } else {
        const lift = easeIn(w);
        alpha *= 1 - lift;
        sc *= 1 + 0.5 * lift;
        x += unturnX(0, -18 * lift, ANGLE);
        y += unturnY(0, -18 * lift, ANGLE);
        hop += 30 * lift;
      }
    }
    if (alpha <= 0.001) continue;
    const s = sc * p.size * PIECE_SCALE[k];
    const turn = ANGLE + rot;
    const shx = unturnX(1 + hop * 0.35, 1.6 + hop * 0.6, turn);
    const shy = unturnY(1 + hop * 0.35, 1.6 + hop * 0.6, turn);
    c.save();
    c.translate(x, y);
    c.rotate((rot * 180) / Math.PI, 0, 0);
    c.scale(s, s);
    piece(c, k, p, unturnX(-0.55, -0.83, turn), unturnY(-0.55, -0.83, turn), shx / s, shy / s, Math.max(0, 1 - hop / 40), alpha);
    c.restore();
  }
}

function drawPlateContents(c: SkCanvas, g: Geo, f: Frame, omelettePic: SkPicture) {
  'worklet';
  const k = squash(f);
  c.save();
  c.translate(g.cx, g.plateY);
  c.scale(1 - k, 1 - k);
  c.rotate((ANGLE * 180) / Math.PI, 0, 0);
  c.translate(0, -CENTROID_Y);
  c.drawPicture(omelettePic);
  if (!f.reduced) {
    for (let i = 0; i < f.toppings.length; i++) {
      // A soft puff where the ingredient lands.
      const tp = f.toppings[i];
      const u = (f.t - tp.ta - LAND) / 0.36;
      if (u < 0 || u >= 1) continue;
      const e = easeOut(u);
      c.drawCircle(landingX(tp.kind), landingY(tp.kind), 10 + 46 * e, stroke(solid(0xfff6d6, 0.55 * (1 - u), 1), 7 * (1 - e) + 0.5, false));
    }
  }
  for (let i = 0; i < f.toppings.length; i++) drawPieces(c, f, f.toppings[i]);
  c.restore();
}

function drawShelf(c: SkCanvas, g: Geo, f: Frame) {
  'worklet';
  const y = g.shelfY;
  ringShadow(c, g.cx, y + 8, 44, 66, 0.07);
  ringShadow(c, g.cx, y + 2, 47, 52, 0.1);
  c.drawCircle(g.cx, y, 48, radial([0xffffff, 1, 0, 0xf7f7f6, 1, 1], g.cx - 14, y - 16, 64, 1));
  for (let i = 0; i < KINDS; i++) {
    const d = slot(i, f.s);
    const ad = Math.abs(d);
    if (ad > 2.1) continue;
    let x = g.cx + d * SPACING;
    const sc0 = 1 - 0.28 * Math.min(1, ad);
    const alpha = 1 - smooth((ad - 1.2) / 0.8);
    let pop = 1;
    let badge = 0;
    let tp: Topping | null = null;
    for (let j = 0; j < f.toppings.length; j++) if (f.toppings[j].kind === i && f.toppings[j].ta <= f.t) tp = f.toppings[j];
    if (tp) {
      const tau = f.t - tp.ta;
      if (f.reduced) {
        badge = clamp01(tau / 0.3);
      } else {
        pop = tau < RESTOCK_AT ? 0 : easeOutBack((tau - RESTOCK_AT) / RESTOCK_DUR);
        badge = pop;
      }
      if (tp.tr >= 0) badge *= 1 - clamp01((f.t - tp.tr) / 0.2);
    }
    if (f.shakeKind === i) {
      const tau = f.t - f.shakeAt;
      if (tau >= 0 && tau < SHAKE_DUR) x += 7 * Math.sin(tau * 42) * (1 - tau / SHAKE_DUR);
    }
    const size = ICON * sc0 * pop;
    if (size < 0.5 || alpha <= 0.001) continue;
    c.save();
    c.translate(x, y);
    c.scale(size, size);
    ingredient(c, i, true, alpha);
    c.restore();
    if (badge > 0.01) {
      const bx = x + 27 * sc0;
      const by = y - 26 * sc0;
      const r = 10 * sc0 * badge;
      c.drawCircle(bx, by + 1, r + 2.5, solid(0x000000, 0.1, alpha));
      c.drawCircle(bx, by, r + 2, solid(0xffffff, 1, alpha));
      c.drawCircle(bx, by, r, solid(INK, 1, alpha));
      const tick = Skia.Path.Make();
      tick.moveTo(bx - r * 0.42, by + r * 0.02);
      tick.lineTo(bx - r * 0.1, by + r * 0.34);
      tick.lineTo(bx + r * 0.45, by - r * 0.32);
      c.drawPath(tick, stroke(solid(0xffffff, 1, alpha), Math.max(0.5, 2 * sc0 * badge), true));
    }
  }
}

function drawLabels(c: SkCanvas, g: Geo, f: Frame, fonts: Fonts) {
  'worklet';
  for (let i = 0; i < KINDS; i++) {
    const d = slot(i, f.s);
    const a = clamp01(1 - Math.abs(d) * 1.8);
    if (a <= 0.001) continue;
    const x = g.cx + 40 * d;
    label(c, NAMES[i], fonts.s17, x, g.nameY, hex(INK, a), 0, TRACK.s17);
    label(c, '+' + money(CENTS[i]), fonts.r14, x, g.priceY, hex(MUTED, a), 0, TRACK.r14);
  }
}

function drawButton(c: SkCanvas, g: Geo, f: Frame, fonts: Fonts) {
  'worklet';
  const rx = 20;
  const ry = g.buttonY;
  const rw = g.w - 40;
  const rh = 56;
  const rr = Skia.RRectXY(Skia.XYWHRect(rx, ry, rw, rh), 18, 18);
  const sh = solid(0x000000, 0.18, 1);
  sh.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, sigma(17), true));
  c.drawRRect(Skia.RRectXY(Skia.XYWHRect(rx, ry + 6, rw, rh), 18, 18), sh);
  c.drawRRect(rr, linear([0x2a2b31, 1, 0, INK, 1, 1], 0, ry, 0, ry + rh, 1));
  c.save();
  c.clipRRect(rr, ClipOp.Intersect, true);
  c.drawRRect(Skia.RRectXY(Skia.XYWHRect(rx, ry + 1, rw, rh), 18, 18), stroke(solid(0xffffff, 0.1, 1), 1.2, false));
  c.restore();
  const my = ry + rh / 2;
  label(c, 'Continue', fonts.s17, rx + 24, my, hex(0xffffff), -1, TRACK.s17);

  const now = total(f, f.t);
  const right = rx + rw - 24;
  c.save();
  c.clipRect(Skia.XYWHRect(rx + rw / 2, ry + 14, rw / 2, rh - 28), ClipOp.Intersect, true);
  const tc = lastChange(f, f.t);
  if (f.t - tc < PRICE_ROLL) {
    const u = easeOut((f.t - tc) / PRICE_ROLL);
    const old = total(f, tc - 1e-6);
    const dir = now >= old ? 1 : -1;
    label(c, money(old), fonts.s17, right, my - 22 * u * dir, hex(0xffffff, 1 - u), 1, 0);
    label(c, money(now), fonts.s17, right, my + 22 * (1 - u) * dir, hex(0xffffff, u), 1, 0);
  } else {
    label(c, money(now), fonts.s17, right, my, hex(0xffffff), 1, 0);
  }
  c.restore();
}

/** The ingredient tossed from the shelf onto the omelette, then collapsing into its pieces. */
function drawFlight(c: SkCanvas, g: Geo, f: Frame, tp: Topping) {
  'worklet';
  if (f.reduced) return;
  const tau = f.t - tp.ta;
  if (tau < 0 || tau >= LAND + COLLAPSE) return;
  const sx = g.cx + tp.sx * SPACING;
  const sy = g.shelfY;
  const lx = landingX(tp.kind);
  const ly = landingY(tp.kind) - CENTROID_Y;
  const wx = g.cx + lx * Math.cos(ANGLE) - ly * Math.sin(ANGLE);
  const wy = g.plateY + lx * Math.sin(ANGLE) + ly * Math.cos(ANGLE);
  let x = sx;
  let y = sy;
  let sc = 1;
  let rot = 0;
  let alpha = 1;
  let lift = 0;
  if (tau < PRESS) {
    sc = 1 - 0.1 * easeOut(tau / PRESS);
  } else if (tau < LAND) {
    const e = easeInOut((tau - PRESS) / (LAND - PRESS));
    x = bezier(sx, wx + 50, wx, e);
    y = bezier(sy, wy - 70, wy, e);
    sc = 0.9 - 0.18 * e + 0.35 * Math.sin(Math.PI * e);
    rot = 25 * e;
    lift = Math.sin(Math.PI * e);
  } else {
    const v = (tau - LAND) / COLLAPSE;
    x = wx;
    y = wy;
    sc = 0.72 * (1 - easeIn(v));
    rot = 25;
    alpha = 1 - v;
  }
  if (sc > 0.001) blob(c, x + 6 + 16 * lift, y + 34 * sc + 22 * lift, 28 * sc, 8 * sc, 0x000000, 0.16 * (1 - 0.5 * lift) * alpha, 1);
  const size = ICON * sc;
  if (size < 0.5) return;
  c.save();
  c.translate(x, y);
  c.rotate(rot, 0, 0);
  c.scale(size, size);
  ingredient(c, tp.kind, false, alpha);
  c.restore();
}

export function OmeletteToppingBuilder({ autoPlay = false }: Props) {
  const insets = useSafeAreaInsets();
  const reduced = useReducedMotion();
  const auto = autoPlay && !reduced;
  const [size, setSize] = useState<{ width: number; height: number } | null>(null);
  const geo = useMemo(
    () => makeGeo(size?.width ?? 393, size?.height ?? 852, insets.top, insets.bottom),
    [size, insets.top, insets.bottom],
  );
  const fonts = useMemo<Fonts>(() => {
    const family = Platform.select({ ios: 'System', default: 'sans-serif' });
    const f = (fontSize: number, fontWeight: '400' | '600') => matchFont({ fontFamily: family, fontSize, fontWeight });
    return { s20: f(20, '600'), r13: f(13, '400'), r15: f(15, '400'), s17: f(17, '600'), r14: f(14, '400') };
  }, []);

  const driver = useSharedValue<Driver>(makeDriver());
  const frame = useSharedValue<Frame>(simFrame(makeSim(), 0));
  const now = useSharedValue(0);
  const grab = useSharedValue({ mode: 0, s0: 0, x: 0 });

  useEffect(() => {
    driver.set(makeDriver());
  }, [auto, reduced, driver]);

  const feel = () => {
    if (process.env.EXPO_OS !== 'web') Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
  };
  const feelLater = (ms: number) => {
    setTimeout(feel, ms);
  };

  useFrameCallback((info) => {
    if (!size) return;
    const t = info.timestamp / 1000;
    now.set(t);
    frame.set(driverFrame(driver.get(), t, auto, reduced));
  });

  const touch = Gesture.Pan()
    .minDistance(0)
    .onBegin((e) => {
      const onShelf = Math.abs(e.y - geo.shelfY) < 66;
      const onPlate = !onShelf && Math.hypot(e.x - geo.cx, e.y - geo.plateY) < 150;
      if (!onShelf && !onPlate) {
        grab.set({ mode: 0, s0: 0, x: 0 });
        return;
      }
      const d = driver.get();
      const t = now.get();
      const s = driverSession(d, t, auto, reduced);
      let s0 = 0;
      if (onShelf) {
        simBeginDrag(s, t);
        s0 = s.drag ?? 0;
      }
      d.touching = true;
      driverTouched(d, t);
      grab.set({ mode: onShelf ? 1 : 2, s0, x: e.x });
    })
    .onUpdate((e) => {
      const gr = grab.get();
      const d = driver.get();
      if (gr.mode === 0 || !d.live) return;
      if (gr.mode === 1 && Math.abs(e.translationX) > 8) simDragTo(d.live, gr.s0 - e.translationX / SPACING);
      driverTouched(d, now.get());
    })
    .onFinalize((e) => {
      const gr = grab.get();
      const d = driver.get();
      if (gr.mode === 0 || !d.live) return;
      const t = now.get();
      const moved = Math.hypot(e.translationX, e.translationY);
      const sim = d.live;
      if (gr.mode === 1) {
        if (moved < 8) {
          simRelease(sim, t, 0);
          const dx = gr.x - geo.cx;
          if (Math.abs(dx) < 56) {
            if (simTapCentre(sim, t)) scheduleOnRN(feelLater, reduced ? 0 : LAND * 1000);
          } else {
            const k = Math.max(-2, Math.min(2, round(dx / SPACING)));
            simSelect(sim, k === 0 ? (dx > 0 ? 1 : -1) : k, t);
          }
        } else {
          if (Math.abs(e.translationX) > 8) simDragTo(sim, gr.s0 - e.translationX / SPACING);
          simRelease(sim, t, Math.min(12, Math.max(-12, -e.velocityX / SPACING)));
        }
      } else if (moved < 10 && simRemoveLast(sim, t)) {
        scheduleOnRN(feel);
      }
      d.touching = false;
      driverTouched(d, t);
      grab.set({ mode: 0, s0: 0, x: 0 });
    });

  const act = (kind: number) => {
    scheduleOnUI(() => {
      const d = driver.get();
      const t = now.get();
      const s = driverSession(d, t, auto, reduced);
      if (kind === 0) simTapCentre(s, t);
      else if (kind === 1) simSelect(s, 1, t);
      else simRemoveLast(s, t);
      driverTouched(d, t);
    });
  };

  const still = useDerivedValue(() => {
    const recorder = Skia.PictureRecorder();
    const canvas = recorder.beginRecording(Skia.XYWHRect(0, 0, geo.w, geo.h));
    drawStill(canvas, geo, fonts);
    return recorder.finishRecordingAsPicture();
  });

  const omelettePic = useDerivedValue(() => {
    const recorder = Skia.PictureRecorder();
    const canvas = recorder.beginRecording(Skia.XYWHRect(-R - 40, -40, 2 * R + 80, R + 80));
    omelette(
      canvas,
      unturnX(2, 5, ANGLE),
      unturnY(2, 5, ANGLE),
      unturnX(0, 1.5, ANGLE),
      unturnY(0, 1.5, ANGLE),
    );
    return recorder.finishRecordingAsPicture();
  });

  const picture = useDerivedValue(() => {
    const recorder = Skia.PictureRecorder();
    const canvas = recorder.beginRecording(Skia.XYWHRect(0, 0, geo.w, geo.h));
    const f = frame.get();
    canvas.drawPicture(still.get());
    drawPlateContents(canvas, geo, f, omelettePic.get());
    drawShelf(canvas, geo, f);
    drawLabels(canvas, geo, f, fonts);
    drawButton(canvas, geo, f, fonts);
    for (let i = 0; i < f.toppings.length; i++) drawFlight(canvas, geo, f, f.toppings[i]);
    return recorder.finishRecordingAsPicture();
  });

  const onLayout = (e: LayoutChangeEvent) => {
    const { width, height } = e.nativeEvent.layout;
    if (!size || size.width !== width || size.height !== height) setSize({ width, height });
  };

  return (
    <GestureDetector gesture={touch}>
      <View
        style={styles.screen}
        onLayout={onLayout}
        accessible
        accessibilityRole="button"
        accessibilityLabel="Omelette fillings"
        accessibilityHint="Adds the centred filling to the omelette."
        accessibilityActions={[
          { name: 'activate' },
          { name: 'next', label: 'Next filling' },
          { name: 'undo', label: 'Remove last filling' },
        ]}
        onAccessibilityAction={(e) => {
          const name = e.nativeEvent.actionName;
          act(name === 'activate' ? 0 : name === 'next' ? 1 : 2);
        }}
      >
        <StatusBar style="dark" />
        {size ? (
          <Canvas style={StyleSheet.absoluteFill}>
            <Picture picture={picture} />
          </Canvas>
        ) : null}
      </View>
    </GestureDetector>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: '#EDEDEC' },
});
