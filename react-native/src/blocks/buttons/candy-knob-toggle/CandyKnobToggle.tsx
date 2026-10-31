import { BlendMode, Canvas, PaintStyle, Picture, Skia, TileMode, useImage, type SkCanvas, type SkImage } from '@shopify/react-native-skia';
import { useFonts } from 'expo-font';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useEffect, useMemo, useState } from 'react';
import { AppState, StyleSheet, View, useWindowDimensions } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, { useAnimatedStyle, useDerivedValue, useFrameCallback, useReducedMotion, useSharedValue } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

// The settings screen of an invented sweet shop, Sprinkle: one huge glass switch. Its knob is a
// candy — a frosted donut when "Treat mode" is on, a lime gummy ring when it is off — and it
// rolls along the clear glass pill like a real ring, turning as it travels, stretching a little
// with the speed and squashing as it lands. Rainbow smears inside the glass slide the other way
// and the page's colour follows the candy. Tap or drag the switch.
//
// Artwork: assets/knob-donut.png and knob-gummy.png (generated, prompts in assets/PROMPTS.md).
// Layout (393 x 852 pt): 34 pt top row; title Bricolage Grotesque 46 pt; the switch 300 x 128 pt
// (radius 64, knob 112 pt) centred at 50 % of the height; state line under it; a settings card
// and footnote at the bottom.
//
// autoPlay timeline (LOOP = 6 s), on = knob right:
//   0.0-1.2  rest, on
//   1.2-2.0  rolls left (ease-in-out cubic; turn = distance / 56 pt; stretch 1 + 0.14 sin(pi p))
//   2.0-2.4  lands with a squash (0.1 e^-8x sin 18x)
//   2.4-4.2  off
//   4.2-5.0  rolls right, 5.0-5.4 lands
//   5.4-6.0  rest, identical to t = 0
// The candy cross-fades donut <-> gummy over the middle third of the roll.
// Tapping toggles; dragging moves the knob with the finger and snaps to the nearer side on
// release (light haptic); 2 s later the loop starts again from that side.

/** Seconds. */
export const LOOP = 6;
const TW = 300;
const TH = 128;
const KNOB = 112;
const INSET = (TH - KNOB) / 2;
const TRAVEL = TW - TH;
const HOLD = 2;
const SNAP = 0.8;

const BRICOLAGE = require('./assets/BricolageHeadline.ttf');
const FAMILY = 'CandyKnobToggle-Bricolage';
const ON_BG = '#F7C9DD';
const OFF_BG = '#D7EFB4';
const ON_INK = '#5A1233';
const OFF_INK = '#24420E';

function clamp01(x: number) {
  'worklet';
  return Math.min(1, Math.max(0, x));
}
function seg(t: number, a: number, b: number) {
  'worklet';
  return clamp01((t - a) / (b - a));
}
function easeInOutCubic(x: number) {
  'worklet';
  return x < 0.5 ? 4 * x * x * x : 1 - Math.pow(-2 * x + 2, 3) / 2;
}
function mixColor(a: string, b: string, u: number) {
  'worklet';
  const pa = parseInt(a.slice(1), 16);
  const pb = parseInt(b.slice(1), 16);
  const r = Math.round(((pa >> 16) & 255) * (1 - u) + ((pb >> 16) & 255) * u);
  const g = Math.round(((pa >> 8) & 255) * (1 - u) + ((pb >> 8) & 255) * u);
  const bl = Math.round((pa & 255) * (1 - u) + (pb & 255) * u);
  return `rgb(${r}, ${g}, ${bl})`;
}
/** A small decaying wobble `x` seconds after landing. */
function land(x: number) {
  'worklet';
  if (x <= 0 || x > 0.6) return 0;
  return 0.1 * Math.exp(-8 * x) * Math.sin(18 * x);
}

/** Knob position (0 = left/off, 1 = right/on), its roll progress and squash, `t` s into the loop. */
function knobAt(t: number) {
  'worklet';
  const left = easeInOutCubic(seg(t, 1.2, 2.0));
  const right = easeInOutCubic(seg(t, 4.2, 5.0));
  const pos = 1 - left + right;
  const moving = t >= 1.2 && t < 2.0 ? seg(t, 1.2, 2.0) : t >= 4.2 && t < 5.0 ? seg(t, 4.2, 5.0) : 0;
  const squash = land(t - 2.0) + land(t - 5.0);
  return { pos, moving, squash };
}

function drawSwitch(c: SkCanvas, pos: number, moving: number, squash: number, x0: number, y0: number, donut: SkImage | null, gummy: SkImage | null) {
  'worklet';
  const track = Skia.RRectXY(Skia.XYWHRect(x0, y0, TW, TH), TH / 2, TH / 2);
  // Glass body: a faint fill and soft drop shadow.
  const shadow = Skia.Paint();
  shadow.setAntiAlias(true);
  shadow.setColor(Skia.Color('rgba(40,20,60,0.18)'));
  shadow.setMaskFilter(Skia.MaskFilter.MakeBlur(1, 18, true));
  c.drawRRect(Skia.RRectXY(Skia.XYWHRect(x0 + 6, y0 + 22, TW - 12, TH), TH / 2, TH / 2), shadow);
  const body = Skia.Paint();
  body.setAntiAlias(true);
  body.setColor(Skia.Color('rgba(255,255,255,0.22)'));
  c.drawRRect(track, body);

  c.save();
  c.clipRRect(track, 1, true);
  // Rainbow smears drifting the other way from the knob.
  const drift = (0.5 - pos) * 70;
  const smear = (cx: number, w: number, alpha: number, angle: number) => {
    'worklet';
    const p = Skia.Paint();
    p.setAntiAlias(true);
    p.setAlphaf(alpha);
    p.setMaskFilter(Skia.MaskFilter.MakeBlur(1, 14, true));
    p.setShader(
      Skia.Shader.MakeLinearGradient(
        { x: cx - w / 2, y: 0 },
        { x: cx + w / 2, y: 0 },
        [Skia.Color('#FF8AD8'), Skia.Color('#FFD48A'), Skia.Color('#8AF2FF'), Skia.Color('#B48AFF')],
        null,
        TileMode.Clamp,
      ),
    );
    c.save();
    c.rotate(angle, cx, y0 + TH / 2);
    c.drawRect(Skia.XYWHRect(cx - w / 2, y0 - 30, w, TH + 60), p);
    c.restore();
  };
  smear(x0 + TW * 0.36 + drift, 64, 0.55, 18);
  smear(x0 + TW * 0.62 + drift * 0.6, 34, 0.35, 18);

  // Knob shadow on the glass floor.
  const kx = x0 + INSET + TRAVEL * pos + KNOB / 2;
  const ky = y0 + TH / 2;
  const stretch = 1 + 0.14 * Math.sin(Math.PI * moving) + squash;
  const sx = stretch;
  const sy = 1 / Math.sqrt(stretch) - squash * 0.5;
  const ks = Skia.Paint();
  ks.setAntiAlias(true);
  ks.setColor(Skia.Color('rgba(60,20,50,0.28)'));
  ks.setMaskFilter(Skia.MaskFilter.MakeBlur(1, 8, true));
  c.drawOval(Skia.XYWHRect(kx - (KNOB * sx) / 2 + 4, ky + KNOB * 0.28, KNOB * sx - 8, 22), ks);

  // The candy: rolls (turn = distance / radius), cross-fades donut <-> gummy mid-way.
  const turn = (pos * TRAVEL) / (KNOB / 2);
  const gummyWeight = 1 - easeInOutCubic(seg(pos, 0.33, 0.67));
  const imgs = [donut, gummy];
  const weights = [1 - gummyWeight, gummyWeight];
  for (let k = 0; k < 2; k++) {
    const img = imgs[k];
    if (!img || weights[k] <= 0.001) continue;
    const p = Skia.Paint();
    p.setAntiAlias(true);
    p.setAlphaf(weights[k]);
    c.save();
    c.translate(kx, ky);
    c.scale(sx, sy);
    c.rotate((turn * 180) / Math.PI, 0, 0);
    c.drawImageRect(img, Skia.XYWHRect(0, 0, img.width(), img.height()), Skia.XYWHRect(-KNOB / 2, -KNOB / 2, KNOB, KNOB), p);
    c.restore();
  }
  c.restore();

  // Front of the glass: top sheen, bottom iridescent edge and a rim.
  const sheen = Skia.Paint();
  sheen.setAntiAlias(true);
  sheen.setBlendMode(BlendMode.Screen);
  sheen.setShader(Skia.Shader.MakeLinearGradient({ x: 0, y: y0 }, { x: 0, y: y0 + TH * 0.5 }, [Skia.Color('rgba(255,255,255,0.55)'), Skia.Color('rgba(255,255,255,0)')], null, TileMode.Clamp));
  c.drawRRect(Skia.RRectXY(Skia.XYWHRect(x0 + 14, y0 + 6, TW - 28, TH * 0.42), TH * 0.3, TH * 0.3), sheen);
  const rim = Skia.Paint();
  rim.setAntiAlias(true);
  rim.setStyle(PaintStyle.Stroke);
  rim.setStrokeWidth(3);
  rim.setShader(
    Skia.Shader.MakeLinearGradient(
      { x: x0, y: y0 },
      { x: x0 + TW, y: y0 + TH },
      [Skia.Color('rgba(255,255,255,0.95)'), Skia.Color('rgba(180,230,255,0.7)'), Skia.Color('rgba(255,180,230,0.8)'), Skia.Color('rgba(255,255,255,0.95)')],
      null,
      TileMode.Clamp,
    ),
  );
  c.drawRRect(track, rim);
  const edge = Skia.Paint();
  edge.setAntiAlias(true);
  edge.setStyle(PaintStyle.Stroke);
  edge.setStrokeWidth(2.5);
  edge.setMaskFilter(Skia.MaskFilter.MakeBlur(1, 1.5, true));
  edge.setShader(
    Skia.Shader.MakeLinearGradient(
      { x: x0 + 40 - drift, y: 0 },
      { x: x0 + TW - 40 - drift, y: 0 },
      [Skia.Color('rgba(255,140,220,0)'), Skia.Color('#FF8AD8'), Skia.Color('#8AF2FF'), Skia.Color('rgba(138,242,255,0)')],
      null,
      TileMode.Clamp,
    ),
  );
  const lip = Skia.Path.Make();
  lip.moveTo(x0 + 44, y0 + TH - 9);
  lip.lineTo(x0 + TW - 44, y0 + TH - 9);
  c.drawPath(lip, edge);
}

type Props = {
  brand?: string;
  title?: string;
  /** Flips the switch by itself, every LOOP seconds. */
  autoPlay?: boolean;
};

export function CandyKnobToggle({ brand = 'Sprinkle', title = 'Treat\nmode', autoPlay = false }: Props) {
  const insets = useSafeAreaInsets();
  const { width, height } = useWindowDimensions();
  const reduced = useReducedMotion();
  const [fontsLoaded] = useFonts({ [FAMILY]: BRICOLAGE });
  const donut = useImage(require('./assets/knob-donut.png'));
  const gummy = useImage(require('./assets/knob-gummy.png'));
  const images = useMemo(() => ({ donut, gummy }), [donut, gummy]);
  const [active, setActive] = useState(AppState.currentState === 'active');
  useEffect(() => {
    const sub = AppState.addEventListener('change', (state) => setActive(state === 'active'));
    return () => sub.remove();
  }, []);

  const x0 = (width - TW) / 2;
  const y0 = height * 0.5 - TH / 2;

  const elapsed = useSharedValue(0);
  const origin = useSharedValue(0);
  const pos = useSharedValue(1);
  const moving = useSharedValue(0);
  const squash = useSharedValue(0);
  const touching = useSharedValue(false);
  const manual = useSharedValue(false);
  const released = useSharedValue(-1);
  const from = useSharedValue(1);
  const to = useSharedValue(1);
  const grab = useSharedValue(1);

  const frame = useFrameCallback((info) => {
    const dt = (info.timeSincePreviousFrame ?? 0) / 1000;
    elapsed.set(elapsed.get() + dt);
    const now = elapsed.get();
    if (touching.get()) {
      squash.set(0);
      return;
    }
    if (manual.get()) {
      const since = now - released.get();
      const p = clamp01(since / SNAP);
      pos.set(from.get() + (to.get() - from.get()) * easeInOutCubic(p));
      moving.set(Math.abs(to.get() - from.get()) > 0.05 && p < 1 ? p : 0);
      squash.set(land(since - SNAP) * Math.min(1, Math.abs(to.get() - from.get()) * 2));
      if (since > SNAP + HOLD) {
        manual.set(false);
        // Pick up the loop at the rest on this side.
        origin.set(to.get() > 0.5 ? now : now - 2.4);
      }
      return;
    }
    if (!autoPlay) {
      pos.set(1);
      moving.set(0);
      squash.set(0);
      return;
    }
    const k = knobAt((now - origin.get()) % LOOP);
    pos.set(k.pos);
    moving.set(k.moving);
    squash.set(k.squash);
  }, false);
  useEffect(() => {
    frame.setActive(active && !reduced);
    return () => frame.setActive(false);
  }, [active, reduced, frame]);

  const picture = useDerivedValue(() => {
    const recorder = Skia.PictureRecorder();
    const canvas = recorder.beginRecording(Skia.XYWHRect(0, 0, width, height));
    drawSwitch(canvas, pos.get(), moving.get(), squash.get(), x0, y0, images.donut, images.gummy);
    return recorder.finishRecordingAsPicture();
  }, [width, height, x0, y0, images]);

  const bgStyle = useAnimatedStyle(() => ({ backgroundColor: mixColor(OFF_BG, ON_BG, pos.get()) }));
  const inkStyle = useAnimatedStyle(() => ({ color: mixColor(OFF_INK, ON_INK, pos.get()) }));
  const onStyle = useAnimatedStyle(() => ({ opacity: clamp01((pos.get() - 0.5) * 3), transform: [{ translateY: 8 * (1 - clamp01((pos.get() - 0.5) * 3)) }] }));
  const offStyle = useAnimatedStyle(() => ({ opacity: clamp01((0.5 - pos.get()) * 3), transform: [{ translateY: 8 * (1 - clamp01((0.5 - pos.get()) * 3)) }] }));

  const haptic = () => {
    if (process.env.EXPO_OS === 'web') return;
    Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
  };
  const release = (target: number) => {
    'worklet';
    touching.set(false);
    from.set(pos.get());
    to.set(target);
    released.set(elapsed.get());
    scheduleOnRN(haptic);
  };
  const tap = Gesture.Tap()
    .enabled(!reduced)
    .onEnd(() => {
      manual.set(true);
      release(pos.get() > 0.5 ? 0 : 1);
    });
  const pan = Gesture.Pan()
    .enabled(!reduced)
    .minDistance(6)
    .onBegin(() => {
      grab.set(pos.get());
    })
    .onStart(() => {
      touching.set(true);
      manual.set(true);
    })
    .onUpdate((e) => {
      pos.set(clamp01(grab.get() + e.translationX / TRAVEL));
      moving.set(0);
    })
    .onEnd(() => release(pos.get() > 0.5 ? 1 : 0));

  if (!fontsLoaded) return <View style={[styles.screen, { backgroundColor: ON_BG }]} />;

  return (
    <Animated.View style={[styles.screen, bgStyle]}>
      <StatusBar style="dark" />
      <View style={[styles.head, { top: insets.top + 8 }]} pointerEvents="none">
        <View style={styles.row}>
          <Animated.Text style={[styles.brand, inkStyle]}>{brand}</Animated.Text>
          <Animated.Text style={[styles.meta, inkStyle]}>Settings</Animated.Text>
        </View>
        <Animated.Text style={[styles.title, inkStyle]}>{title}</Animated.Text>
        <Animated.Text style={[styles.sub, inkStyle]}>Let the app show you the sweet stuff first.</Animated.Text>
      </View>

      <GestureDetector gesture={Gesture.Exclusive(pan, tap)}>
        <View style={[styles.hit, { left: x0 - 10, top: y0 - 20, width: TW + 20, height: TH + 40 }]} accessible accessibilityRole="switch" accessibilityLabel="Treat mode" />
      </GestureDetector>
      <Canvas style={StyleSheet.absoluteFill} pointerEvents="none">
        <Picture picture={picture} />
      </Canvas>
      <View style={[styles.states, { top: y0 + TH + 34 }]} pointerEvents="none">
        <Animated.Text style={[styles.state, inkStyle, onStyle]}>On · sprinkles everywhere</Animated.Text>
        <Animated.Text style={[styles.state, styles.stateOver, inkStyle, offStyle]}>Off · just the gummies</Animated.Text>
      </View>

      <View style={[styles.footer, { paddingBottom: insets.bottom + 18 }]} pointerEvents="none">
        <View style={styles.card}>
          <Animated.Text style={[styles.cardTitle, inkStyle]}>Daily treat reminder</Animated.Text>
          <Animated.Text style={[styles.cardMeta, inkStyle]}>Every day at 15:30</Animated.Text>
        </View>
        <Animated.Text style={[styles.foot, inkStyle]}>You can change this any time.</Animated.Text>
      </View>
    </Animated.View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1 },
  head: { position: 'absolute', left: 24, right: 24 },
  row: { height: 34, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  brand: { fontFamily: FAMILY, fontSize: 22 },
  meta: { fontSize: 14, fontWeight: '600', opacity: 0.7 },
  title: { marginTop: 18, fontFamily: FAMILY, fontSize: 64, lineHeight: 64, letterSpacing: -2 },
  sub: { marginTop: 12, fontSize: 17, lineHeight: 23, opacity: 0.75, maxWidth: 280 },
  hit: { position: 'absolute' },
  states: { position: 'absolute', left: 24, right: 24, height: 30, alignItems: 'center' },
  state: { fontFamily: FAMILY, fontSize: 22 },
  stateOver: { position: 'absolute' },
  footer: { position: 'absolute', left: 20, right: 20, bottom: 0 },
  card: { padding: 18, borderRadius: 24, backgroundColor: 'rgba(255,255,255,0.45)' },
  cardTitle: { fontSize: 17, fontWeight: '700' },
  cardMeta: { marginTop: 4, fontSize: 15, opacity: 0.7 },
  foot: { marginTop: 12, textAlign: 'center', fontSize: 13, opacity: 0.6 },
});
