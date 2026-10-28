import { Canvas, ClipOp, matchFont, Picture, Skia, type SkCanvas } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useEffect, useMemo, useState } from 'react';
import { type LayoutChangeEvent, Platform, StyleSheet, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import { useDerivedValue, useFrameCallback, useReducedMotion, useSharedValue } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';

import {
  blurred,
  drawBead,
  drawBottle,
  drawCart,
  drawDrops,
  drawRed,
  drawStickers,
  drawStream,
  fill,
  type Fonts,
  glyphWidth,
  hex,
  label,
  MUTED_PLUM,
  NOZZLE_Y,
  PINK,
  PLUM,
  roundRect,
  SAUCE,
  stroke,
} from './sauceSqueezeArt';
import {
  cartBump,
  clamp01,
  COVERED_AT,
  type Driver,
  driverBegin,
  driverEnd,
  driverFrame,
  driverHolding,
  driverShoot,
  emptyFrame,
  type Frame,
  makeDriver,
  NO_TAU,
  plusOne,
  redFade,
  streamVisible,
  THRESHOLD,
} from './sauceSqueezeMotion';

/*
 Sauce Squeeze Cart — the add-to-cart moment of an invented grocery app ("Pantry Run")
 (a port of SauceSqueezeCart.swift).

 Look: a pink product page for "Tomato Ketchup" by an invented brand, PLUMPO. A glossy red squeeze
 bottle, drawn in code (a pillowy body with three bulges per side, a white two-tier cap pointing down,
 a label with a tomato), floats over a cream card with the name, chips, the price and "Hold to add".
 Everything is drawn into one Skia picture per frame.

 Hero: holding squeezes the bottle (squash and stretch, a trembling waist, a bead at the nozzle);
 releasing shoots a thick glossy stream that whips down the screen while a red pool rises until the
 whole screen is sauce. Stickers pop on the red ("IN YOUR CART", a smiley, a cart, "+1 ADDED", an
 arrow, a sparkle), then the red retracts up the screen with drips hanging from it, the page comes
 back and the cart in the header bumps with a "+1". sauceSqueezeMotion.ts has the timeline.

 autoPlay: LOOP = 8 s (rest, squeeze, shoot and flood, stickers, drain, rest).
 Touch: press and hold the bottle or the button; the longer the hold, the harder the squeeze. Release
 past 40 % to shoot, below it the bottle springs back. A light haptic when the squeeze is armed, at
 the release and when the screen is red. 2 s after the last touch (and once a shot has played out)
 the loop restarts.
 Reduced motion: the page still; a full hold fades the red and its stickers in and out.
*/

type Props = {
  /** Plays the 8 s squeeze-and-flood loop until touched. */
  autoPlay?: boolean;
};

const CREAM = 0xfff4fa;
const CTA_W = 200;
const BOTTLE_SCALE = 1.12;

type Geo = {
  w: number;
  h: number;
  top: number;
  cx: number;
  cardY: number;
  ctaY: number;
  ctaX: number;
  bottleY: number;
  stickerY: number;
};

function makeGeo(w: number, h: number, top: number, bottom: number): Geo {
  return {
    w,
    h,
    top,
    cx: w / 2,
    cardY: h - 318,
    ctaY: h - Math.max(bottom, 16) - 40,
    ctaX: w - 24 - CTA_W,
    bottleY: top + 250,
    // The stickers gather around the middle of the red screen.
    stickerY: (top + h) / 2 - 30,
  };
}

function bottleCY(g: Geo, f: Frame) {
  'worklet';
  return g.bottleY + f.lift + 3 * f.bob;
}

function bottleAngle(f: Frame) {
  'worklet';
  return -7 + f.spin + 1.5 * f.sway;
}

/** The bottle and the button take the squeeze. */
function hit(g: Geo, x: number, y: number) {
  'worklet';
  const bottle = x >= g.cx - 120 && x <= g.cx + 120 && y >= g.bottleY - 150 && y <= g.bottleY + 180;
  const cta = x >= g.ctaX - 12 && x <= g.ctaX + CTA_W + 12 && y >= g.ctaY - 42 && y <= g.ctaY + 42;
  return bottle || cta;
}

const CHIPS = ['★ 4.8', 'Vegan', 'No added sugar'];

function drawHeader(c: SkCanvas, g: Geo, f: Frame, fonts: Fonts) {
  'worklet';
  const y = g.top + 30;
  const plum = hex(PLUM);
  c.drawCircle(42, y, 22, fill(hex(0xffffff, 0.55)));
  const chev = Skia.Path.Make();
  chev.moveTo(45, y - 7);
  chev.lineTo(38, y);
  chev.lineTo(45, y + 7);
  c.drawPath(chev, stroke(plum, 2.4));
  label(c, 'Pantry Run', fonts.title, g.cx, y, plum, 0, 0);

  // Cart: bumps when the sauce lands in it, a "+1" floats up.
  const cx = g.w - 42;
  const k = 1 + 0.6 * (f.tau !== NO_TAU ? cartBump(f.tau) : 0);
  c.save();
  c.translate(cx, y);
  c.scale(k, k);
  c.drawCircle(0, 0, 22, fill(hex(0xffffff, 0.55)));
  drawCart(c, 0, 0, 0.95, plum);
  c.restore();

  if (f.tau !== NO_TAU) {
    const p = plusOne(f.tau);
    if (p.scale > 0.01 && p.alpha > 0.01) {
      const layer = Skia.Paint();
      layer.setAlphaf(p.alpha);
      c.saveLayer(layer);
      c.translate(cx - 26, y - 12 - p.rise);
      c.scale(p.scale, p.scale);
      c.drawPath(roundRect(-17, -11, 34, 22, 11), fill(hex(SAUCE)));
      label(c, '+1', fonts.plus, 0, 0, hex(0xffffff), 0, 0);
      c.restore();
    }
  }
}

function drawCard(c: SkCanvas, g: Geo, f: Frame, fonts: Fonts) {
  'worklet';
  const top = g.cardY;
  const plum = hex(PLUM);
  const muted = hex(MUTED_PLUM);
  const card = Skia.Path.Make();
  card.addRRect(Skia.RRectXY(Skia.XYWHRect(0, top, g.w, g.h - top + 40), 34, 34));
  c.drawPath(card, fill(hex(CREAM)));

  label(c, 'PLUMPO · 500 ML', fonts.eyebrow, 24, top + 34, muted, -1, 1.4);
  label(c, 'Tomato Ketchup', fonts.name, 24, top + 68, plum, -1, 0);
  label(c, 'Sun-ripe, slow-cooked, a pinch of smoke.', fonts.subtitle, 24, top + 100, hex(0x7a4a70), -1, 0);

  let x = 24;
  for (let i = 0; i < CHIPS.length; i++) {
    const w = glyphWidth(fonts.chip, CHIPS[i], 0) + 24;
    c.drawPath(roundRect(x, top + 125, w, 30, 15), fill(hex(0xf9ddf0)));
    label(c, CHIPS[i], fonts.chip, x + w / 2, top + 140, hex(0x5b1450), 0, 0);
    x += w + 8;
  }

  const cy = g.ctaY;
  label(c, '$3.80', fonts.price, 24, cy - 9, plum, -1, 0);
  label(c, 'per bottle', fonts.small, 24, cy + 17, muted, -1, 0);

  // "Hold to add": pressed in, a darker fill grows from the left with the squeeze.
  const k = 1 - 0.04 * f.press;
  c.save();
  c.translate(g.ctaX + CTA_W / 2, cy);
  c.scale(k, k);
  const pill = roundRect(-CTA_W / 2, -30, CTA_W, 60, 30);
  blurred(c, pill, hex(0x9a0010, 0.28), 12, 0, 0, 8 * (1 - 0.5 * f.press));
  c.drawPath(pill, fill(hex(SAUCE)));
  c.save();
  c.clipPath(pill, ClipOp.Intersect, true);
  c.drawRect(Skia.XYWHRect(-CTA_W / 2, -30, CTA_W * clamp01(f.s), 60), fill(hex(0xb80002)));
  c.drawPath(roundRect(-CTA_W / 2, -28.5, CTA_W, 60, 30), stroke(hex(0xffffff, 0.18), 1.5));
  c.restore();
  label(c, 'Hold to add', fonts.cta, 0, 0, hex(0xffffff), 0, 0);
  c.restore();
}

function drawScreen(c: SkCanvas, g: Geo, f: Frame, fonts: Fonts) {
  'worklet';
  c.drawRect(Skia.XYWHRect(0, 0, g.w, g.h), fill(hex(PINK)));
  drawHeader(c, g, f, fonts);
  drawCard(c, g, f, fonts);

  // The bottle.
  const bx = g.cx + f.shake;
  const by = bottleCY(g, f);
  const angle = bottleAngle(f);
  c.save();
  c.translate(bx, by);
  c.rotate(angle, 0, 0);
  c.scale(BOTTLE_SCALE, BOTTLE_SCALE);
  drawBottle(c, f, fonts);
  if (f.tau === NO_TAU && !f.reduced) drawBead(c, f.s);
  c.restore();

  if (f.tau === NO_TAU) return;
  const tau = f.tau;
  if (f.reduced) {
    const a = redFade(tau);
    if (a > 0.001) c.drawRect(Skia.XYWHRect(0, 0, g.w, g.h), fill(hex(SAUCE, a)));
  } else {
    const rad = (angle * Math.PI) / 180;
    const d = (NOZZLE_Y - 2) * BOTTLE_SCALE;
    const rx = bx - Math.sin(rad) * d;
    const ry = by + Math.cos(rad) * d;
    if (streamVisible(tau)) {
      drawDrops(c, rx, ry, tau);
      drawStream(c, rx, ry, tau, g.h);
    }
    drawRed(c, tau, g.w, g.h);
  }
  drawStickers(c, tau, g.cx, g.stickerY, f.reduced, fonts);
}

export function SauceSqueezeCart({ autoPlay = false }: Props) {
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
    const f = (fontSize: number, fontWeight: '400' | '600' | '700' | '800') => matchFont({ fontFamily: family, fontSize, fontWeight });
    return {
      brand: f(10, '800'),
      tag: f(18, '800'),
      added: f(15, '800'),
      title: f(16, '700'),
      eyebrow: f(12, '600'),
      name: f(30, '800'),
      subtitle: f(15, '400'),
      chip: f(13, '600'),
      price: f(28, '800'),
      small: f(12, '400'),
      cta: f(18, '700'),
      plus: f(13, '800'),
    };
  }, []);

  const driver = useSharedValue<Driver>(makeDriver());
  const frame = useSharedValue<Frame>(emptyFrame());
  const now = useSharedValue(0);
  const grabbed = useSharedValue(false);

  useEffect(() => {
    driver.set(makeDriver());
  }, [auto, reduced, driver]);

  const feel = () => {
    if (process.env.EXPO_OS !== 'web') Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
  };

  useFrameCallback((info) => {
    if (!size) return;
    const t = info.timestamp / 1000;
    now.set(t);
    const d = driver.get();
    const prev = frame.get();
    const f = driverFrame(d, t, auto, reduced);
    // Haptics on the moments of a touch-driven squeeze: armed, and the screen gone red.
    if (d.manual) {
      if (driverHolding(d) && prev.s < THRESHOLD && f.s >= THRESHOLD) scheduleOnRN(feel);
      if (prev.tau !== NO_TAU && f.tau !== NO_TAU && prev.tau < COVERED_AT && f.tau >= COVERED_AT) scheduleOnRN(feel);
    }
    frame.set(f);
  });

  const touch = Gesture.Pan()
    .minDistance(0)
    .onBegin((e) => {
      if (!hit(geo, e.x, e.y)) {
        grabbed.set(false);
        return;
      }
      grabbed.set(true);
      driverBegin(driver.get(), now.get(), auto, reduced);
    })
    .onFinalize(() => {
      if (!grabbed.get()) return;
      grabbed.set(false);
      if (driverEnd(driver.get(), now.get())) scheduleOnRN(feel);
    });

  const onAccessibilityTap = () => {
    scheduleOnUI(() => {
      driverShoot(driver.get(), now.get(), auto, reduced);
    });
  };

  const picture = useDerivedValue(() => {
    const recorder = Skia.PictureRecorder();
    const canvas = recorder.beginRecording(Skia.XYWHRect(0, 0, geo.w, geo.h));
    drawScreen(canvas, geo, frame.get(), fonts);
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
        accessibilityLabel="Tomato Ketchup, 3 dollars 80"
        accessibilityHint="Hold to squeeze the bottle and add it to your cart."
        onAccessibilityTap={onAccessibilityTap}
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
  screen: { flex: 1, backgroundColor: '#F792DC' },
});
