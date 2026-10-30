import { BlurStyle, Canvas, PaintStyle, Picture, Skia, matchFont, useFont, useImage, type SkCanvas, type SkFont, type SkImage } from '@shopify/react-native-skia';
import { useFonts } from 'expo-font';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useEffect, useMemo, useState } from 'react';
import { AppState, Platform, StyleSheet, Text, View, useWindowDimensions } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, { useAnimatedStyle, useDerivedValue, useFrameCallback, useReducedMotion, useSharedValue } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

// An invented archive of Swiss posters, Paper Room, on warm paper under a huge condensed
// "POSTER / ARCHIVE". Sixteen small posters ride a thin orbit; they rush in, growing, into a
// loose pile of full-size posters with soft shadows; the top poster is dealt off and tucked under
// the pile four times, each time revealing the next one with its big title and studio below;
// then the pile bursts back out to the orbit. Drag sideways to spin the orbit.
//
// Posters: eight designs, each a generated photograph or print (assets/poster-*.jpg, prompts in
// assets/PROMPTS.md) with its type set in code in Anton, 200 x 268 pt at full size and 0.22 of
// that on the orbit (radius 150 pt).
//
// Layout (393 x 852 pt): headline Anton 88 pt in two 112 pt lines pulled 24 pt together under a 30 pt top row; orbit
// centre at 56 % of the height; caption 196 pt below the centre (Anton 34 pt title, 13 pt meta);
// a 56 pt black button at the bottom.
//
// autoPlay timeline (LOOP = 9 s), t in seconds; the orbit turns once per loop (linear):
//   0.0-1.2  rest: the orbit, the caption reads the archive line (out 1.6-2.0, back 6.3-6.7)
//   1.2-2.4  gather: poster i flies to its place in the pile, delay 0.03 s * i, ease-in-out
//            cubic, scale 0.22 -> 1, turning to its pile tilt (+-8 deg); shadows deepen
//   2.6-5.6  four deals of 0.75 s: the top poster swings 92 pt right, 26 pt up and 16 deg (sine),
//            goes under the pile at the half-way point and comes back; the caption fades out and the
//            next poster's fades in (pile captions in 2.2-2.5, out 5.8-6.1)
//   5.8-7.0  burst: each poster back to its orbit place, delay 0.03 s * (15 - i)
//   7.0-9.0  rest, identical to t = 0
// Dragging spins the orbit with the finger and keeps spinning after release (decay 3 / s); 2 s
// later it eases back to the nearest whole turn over 0.8 s and the loop goes on.

/** Seconds. */
export const LOOP = 9;
const COUNT = 16;
const RING = 150;
const W = 200;
const H = 268;
const SMALL = 0.22;
const DEAL_START = 2.6;
const DEAL = 0.75;
const DEALS = 4;
const HOLD = 2;
const RETURN = 0.8;

const C = {
  paper: '#EEECE6',
  ink: '#121212',
  muted: '#8A877F',
  red: '#E3241B',
  white: '#FFFFFF',
};

const ANTON = require('./assets/Anton-Regular.ttf');
const ANTON_FAMILY = 'OrbitStackArchive-Anton';

/** The eight designs: their art, and the caption shown when one tops the pile. */
const DESIGNS = [
  { title: 'Form & Field', meta: 'Kunsthalle Basel · 1966' },
  { title: 'Ascent', meta: 'Architektur Forum · 1974' },
  { title: 'Yep.', meta: 'Lime Press · 1989' },
  { title: 'Blue Hour', meta: 'Studio Hafen · 1979' },
  { title: 'Draw', meta: 'Werkschule Bern · 1961' },
  { title: 'Bloom', meta: 'Gartenamt Zürich · 1983' },
  { title: 'No. 7', meta: 'Galerie Raster · 1957' },
  { title: 'Loud Type', meta: 'Radio Zürich · 1991' },
];
// The poster on top of the pile after k deals is number 15 - k, design (15 - k) % 8.
const TOPS = [7, 6, 5, 4, 3];

function hash(i: number, n: number) {
  const x = Math.sin((i + 1) * 17.371 + n * 91.719) * 43758.5453;
  return x - Math.floor(x);
}
/** Each poster's tilt and offset in the pile. */
const PILE = Array.from({ length: COUNT }, (_, i) => ({
  tilt: (hash(i, 1) - 0.5) * 16,
  dx: (hash(i, 2) - 0.5) * 20,
  dy: (hash(i, 3) - 0.5) * 14,
}));

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
function smooth(x: number) {
  'worklet';
  const u = clamp01(x);
  return u * u * (3 - 2 * u);
}

/** How far poster i is into the pile (0 orbit, 1 pile), `t` seconds into the loop. */
function gatherAt(t: number, i: number) {
  'worklet';
  const g = easeInOutCubic(seg(t, 1.2 + 0.03 * i, 2.4 + 0.03 * i));
  const b = easeInOutCubic(seg(t, 5.8 + 0.03 * (COUNT - 1 - i), 7.0 + 0.03 * (COUNT - 1 - i)));
  return g * (1 - b);
}
/** Deals done (fractional during a deal). */
function dealsAt(t: number) {
  'worklet';
  return Math.min(DEALS, Math.max(0, (t - DEAL_START) / DEAL));
}

function fill(color: string, alpha: number) {
  'worklet';
  const p = Skia.Paint();
  p.setAntiAlias(true);
  p.setColor(Skia.Color(color));
  p.setAlphaf(alpha);
  return p;
}

/** Anton text at `size` pt with its left baseline at (x, y); returns its width. */
function word(c: SkCanvas, font: SkFont, text: string, x: number, y: number, size: number, color: string, alignRight: boolean) {
  'worklet';
  const k = size / 100;
  const width = font.getGlyphWidths(font.getGlyphIDs(text)).reduce((s, n) => s + n, 0) * k;
  c.save();
  c.translate(alignRight ? x - width : x, y);
  c.scale(k, k);
  c.drawText(text, 0, 0, fill(color, 1), font);
  c.restore();
  return width;
}

/** Poster design d at full size, its top-left at (0, 0). */
function drawPoster(c: SkCanvas, d: number, art: SkImage | null, anton: SkFont, small: SkFont) {
  'worklet';
  const card = Skia.RRectXY(Skia.XYWHRect(0, 0, W, H), 3, 3);
  c.save();
  c.clipRRect(card, 1, true);
  c.drawRect(Skia.XYWHRect(0, 0, W, H), fill('#D9D6CF', 1));
  if (art) {
    // Cover-fit the 3:4 artwork into the 200 x 268 card.
    const iw = art.width();
    const ih = art.height();
    const scale = Math.max(W / iw, H / ih);
    const sw = W / scale;
    const sh = H / scale;
    c.drawImageRect(art, Skia.XYWHRect((iw - sw) / 2, (ih - sh) / 2, sw, sh), Skia.XYWHRect(0, 0, W, H), fill('#FFFFFF', 1));
  }
  const meta = DESIGNS[d].meta;
  if (d === 0) {
    word(c, anton, 'FORM', 12, H - 14, 86, C.white, false);
    c.drawText(meta, 13, 22, fill(C.white, 0.9), small);
  } else if (d === 1) {
    c.drawRect(Skia.XYWHRect(0, H - 66, W, 66), fill(C.red, 1));
    word(c, anton, 'ASCENT', 12, H - 14, 46, C.white, false);
    c.drawText(meta, 13, 22, fill(C.ink, 0.85), small);
  } else if (d === 2) {
    word(c, anton, 'YEP.', 10, H - 12, 96, C.ink, false);
    c.drawText(meta, 13, 22, fill(C.ink, 0.85), small);
  } else if (d === 3) {
    word(c, anton, 'BLUE', 12, 72, 62, C.white, false);
    word(c, anton, 'HOUR', 12, 132, 62, C.white, false);
    c.drawText(meta, 13, H - 14, fill(C.white, 0.85), small);
  } else if (d === 4) {
    word(c, anton, 'DRAW', W - 12, H - 14, 84, C.red, true);
    c.drawText(meta, 13, 22, fill(C.ink, 0.85), small);
  } else if (d === 5) {
    word(c, anton, 'BLOOM', 12, H - 14, 70, C.white, false);
    c.drawText(meta, 13, 22, fill(C.white, 0.9), small);
  } else if (d === 6) {
    word(c, anton, 'No.7', 12, H - 12, 92, C.ink, false);
    c.drawText(meta, 13, 22, fill(C.ink, 0.85), small);
  } else {
    c.drawRect(Skia.XYWHRect(10, H - 62, W - 20, 52), fill(C.white, 1));
    word(c, anton, 'LOUD TYPE', 20, H - 20, 38, C.ink, false);
    c.drawText(meta, 13, 22, fill(C.white, 0.9), small);
  }
  c.restore();
}

function drawArchive(c: SkCanvas, t: number, spin: number, cx: number, cy: number, arts: (SkImage | null)[], anton: SkFont, small: SkFont) {
  'worklet';
  // The orbit line.
  const orbit = fill(C.ink, 0.14);
  orbit.setStyle(PaintStyle.Stroke);
  orbit.setStrokeWidth(1);
  c.drawCircle(cx, cy, RING, orbit);

  const base = (Math.PI * 2 * t) / LOOP + spin - Math.PI / 2;
  const done = dealsAt(t);
  const shuffles = Math.floor(done);
  const u = done - shuffles;
  // Pile order, bottom to top: the posters as numbered, with the dealt ones moved under.
  const order: number[] = [];
  for (let k = 0; k < COUNT; k++) order.push((k - shuffles + COUNT * 4) % COUNT);
  const dealing = shuffles < DEALS && done > 0 ? (COUNT - 1 - shuffles + COUNT) % COUNT : -1;
  if (dealing >= 0 && u >= 0.5) {
    order.splice(order.indexOf(dealing), 1);
    order.unshift(dealing);
  }
  const items: { i: number; g: number; z: number }[] = [];
  for (let i = 0; i < COUNT; i++) {
    const g = gatherAt(t, i);
    items.push({ i, g, z: g < 0.5 ? g : 1 + order.indexOf(i) });
  }
  items.sort((a, b) => a.z - b.z);
  for (let n = 0; n < items.length; n++) {
    const i = items[n].i;
    const g = items[n].g;
    const a = base + (i * Math.PI * 2) / COUNT;
    const pile = PILE[i];
    let px = cx + Math.cos(a) * RING;
    let py = cy + Math.sin(a) * RING;
    px = px + (cx + pile.dx - px) * g;
    py = py + (cy + pile.dy - py) * g;
    let rot = pile.tilt * g;
    if (i === dealing) {
      const swing = Math.sin(Math.PI * u);
      px += 92 * swing;
      py -= 26 * swing;
      rot += 16 * swing;
    }
    const s = SMALL + (1 - SMALL) * g;
    c.save();
    c.translate(px, py);
    c.rotate(rot, 0, 0);
    c.scale(s, s);
    c.translate(-W / 2, -H / 2);
    const shadow = fill('#000000', 0.08 + 0.14 * g);
    shadow.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 4 + 8 * g, true));
    c.drawRRect(Skia.RRectXY(Skia.XYWHRect(4, 10, W - 4, H - 4), 3, 3), shadow);
    drawPoster(c, i % 8, arts[i % 8], anton, small);
    c.restore();
  }
}

/** The caption's opacity for slot k (0 rest, 1-5 the pile's tops), `t` seconds into the loop. */
function captionOpacity(t: number, k: number) {
  'worklet';
  // One caption at a time: each fades out before the next fades in.
  if (k === 0) return (1 - smooth(seg(t, 1.6, 2.0))) + smooth(seg(t, 6.3, 6.7));
  const pile = smooth(seg(t, 2.2, 2.5)) * (1 - smooth(seg(t, 5.8, 6.1)));
  // Each title holds until its poster is half dealt, then hands over within 0.15 of a deal.
  return pile * (1 - smooth((Math.abs(dealsAt(t) - (k - 1)) - 0.35) / 0.15));
}

type Props = {
  brand?: string;
  line?: string;
  action?: string;
  /** Gathers and deals the posters by itself, every LOOP seconds. */
  autoPlay?: boolean;
};

export function OrbitStackArchive({
  brand = 'PAPER ROOM',
  line = 'Swiss posters 1957–1992',
  action = 'Browse the archive',
  autoPlay = false,
}: Props) {
  const insets = useSafeAreaInsets();
  const { width, height } = useWindowDimensions();
  const reduced = useReducedMotion();
  const [fontsLoaded] = useFonts({ [ANTON_FAMILY]: ANTON });
  const anton = useFont(ANTON, 100);
  const small = useMemo(
    () => matchFont({ fontFamily: Platform.select({ ios: 'Helvetica Neue', default: 'sans-serif' }) ?? 'sans-serif', fontSize: 10, fontWeight: '600' }),
    [],
  );
  const bust = useImage(require('./assets/poster-bust.jpg'));
  const stair = useImage(require('./assets/poster-stair.jpg'));
  const blob = useImage(require('./assets/poster-blob.jpg'));
  const sphere = useImage(require('./assets/poster-sphere.jpg'));
  const hand = useImage(require('./assets/poster-hand.jpg'));
  const flower = useImage(require('./assets/poster-flower.jpg'));
  const grid = useImage(require('./assets/poster-grid.jpg'));
  const waves = useImage(require('./assets/poster-waves.jpg'));
  const arts = useMemo(() => [bust, stair, blob, sphere, hand, flower, grid, waves], [bust, stair, blob, sphere, hand, flower, grid, waves]);
  const [active, setActive] = useState(AppState.currentState === 'active');
  useEffect(() => {
    const sub = AppState.addEventListener('change', (state) => setActive(state === 'active'));
    return () => sub.remove();
  }, []);

  const cx = width / 2;
  const cy = height * 0.56;

  const elapsed = useSharedValue(0);
  const origin = useSharedValue(0);
  const time = useSharedValue(0);
  const spin = useSharedValue(0);
  const velocity = useSharedValue(0);
  const touching = useSharedValue(false);
  const manual = useSharedValue(false);
  const released = useSharedValue(-1);
  const settleFrom = useSharedValue(0);
  const lastX = useSharedValue(0);

  const frame = useFrameCallback((info) => {
    const dt = (info.timeSincePreviousFrame ?? 0) / 1000;
    elapsed.set(elapsed.get() + dt);
    const now = elapsed.get();
    if (touching.get()) {
      origin.set(origin.get() + dt);
      return;
    }
    if (manual.get()) {
      origin.set(origin.get() + dt);
      const since = now - released.get();
      if (since < HOLD) {
        spin.set(spin.get() + velocity.get() * dt);
        velocity.set(velocity.get() * Math.exp(-3 * dt));
        settleFrom.set(spin.get());
      } else {
        const target = Math.round(settleFrom.get() / (Math.PI * 2)) * Math.PI * 2;
        const k = easeInOutCubic(clamp01((since - HOLD) / RETURN));
        spin.set(settleFrom.get() + (target - settleFrom.get()) * k);
        if (since > HOLD + RETURN) {
          spin.set(0);
          manual.set(false);
        }
      }
      return;
    }
    if (autoPlay) time.set((now - origin.get()) % LOOP);
  }, false);
  useEffect(() => {
    frame.setActive(active && !reduced);
    return () => frame.setActive(false);
  }, [active, reduced, frame]);

  const picture = useDerivedValue(() => {
    const recorder = Skia.PictureRecorder();
    const canvas = recorder.beginRecording(Skia.XYWHRect(0, 0, width, height));
    if (anton) drawArchive(canvas, time.get(), spin.get(), cx, cy, arts, anton, small);
    return recorder.finishRecordingAsPicture();
  }, [width, height, cx, cy, arts, anton, small]);

  const c0 = useAnimatedStyle(() => ({ opacity: captionOpacity(time.get(), 0) }));
  const c1 = useAnimatedStyle(() => ({ opacity: captionOpacity(time.get(), 1) }));
  const c2 = useAnimatedStyle(() => ({ opacity: captionOpacity(time.get(), 2) }));
  const c3 = useAnimatedStyle(() => ({ opacity: captionOpacity(time.get(), 3) }));
  const c4 = useAnimatedStyle(() => ({ opacity: captionOpacity(time.get(), 4) }));
  const c5 = useAnimatedStyle(() => ({ opacity: captionOpacity(time.get(), 5) }));
  const tops = [c1, c2, c3, c4, c5];

  const haptic = () => {
    if (process.env.EXPO_OS === 'web') return;
    Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
  };
  const pan = Gesture.Pan()
    .enabled(!reduced)
    .minDistance(4)
    .onBegin((e) => {
      touching.set(true);
      manual.set(true);
      velocity.set(0);
      lastX.set(e.x);
      scheduleOnRN(haptic);
    })
    .onUpdate((e) => {
      // Moving along the bottom of the orbit turns it the other way from the top.
      const side = e.y > cy ? -1 : 1;
      spin.set(spin.get() + (side * (e.x - lastX.get())) / RING);
      lastX.set(e.x);
      velocity.set((side * e.velocityX) / RING);
    })
    .onFinalize(() => {
      touching.set(false);
      released.set(elapsed.get());
    });

  if (!fontsLoaded) return <View style={styles.screen} />;

  const captionTop = cy + 196;

  return (
    <View style={styles.screen}>
      <StatusBar style="dark" />
      <GestureDetector gesture={pan}>
        <View style={StyleSheet.absoluteFill}>
          <View style={[styles.head, { top: insets.top + 8 }]} pointerEvents="none">
            <View style={styles.row}>
              <Text style={styles.brand}>{brand}</Text>
              <Text style={styles.count}>16 / 16</Text>
            </View>
            {/* Two lines, each in a box tall enough for Anton's ascent, pulled together. */}
            <Text style={styles.headline}>POSTER</Text>
            <Text style={[styles.headline, styles.second]}>ARCHIVE</Text>
          </View>
          <Canvas style={StyleSheet.absoluteFill} accessible accessibilityLabel={`${brand}. ${line}. Drag to spin the orbit.`}>
            <Picture picture={picture} />
          </Canvas>
          <View style={[styles.captions, { top: captionTop }]} pointerEvents="none">
            <Animated.View style={[styles.caption, c0]}>
              <Text style={styles.title}>Sixteen posters</Text>
              <Text style={styles.meta}>{line}</Text>
            </Animated.View>
            {TOPS.map((d, k) => (
              <Animated.View key={k} style={[styles.caption, tops[k]]}>
                <Text style={styles.title}>{DESIGNS[d].title}</Text>
                <Text style={styles.meta}>{DESIGNS[d].meta}</Text>
              </Animated.View>
            ))}
          </View>
        </View>
      </GestureDetector>
      <View style={[styles.footer, { paddingBottom: insets.bottom + 14 }]}>
        <View style={styles.button} accessible accessibilityRole="button">
          <Text style={styles.action}>{action}</Text>
          <Text style={styles.arrow}>→</Text>
        </View>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: C.paper },
  head: { position: 'absolute', left: 20, right: 20 },
  row: { height: 30, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', borderBottomWidth: 1.5, borderBottomColor: C.ink },
  brand: { fontFamily: ANTON_FAMILY, fontSize: 18, letterSpacing: 1, color: C.ink },
  count: { fontSize: 12, fontWeight: '700', color: C.ink, letterSpacing: 0.5 },
  headline: { marginTop: 2, fontFamily: ANTON_FAMILY, fontSize: 88, lineHeight: 112, color: C.ink, letterSpacing: -0.5 },
  second: { marginTop: -24 },
  captions: { position: 'absolute', left: 20, right: 20, height: 60 },
  caption: { position: 'absolute', left: 0, right: 0, alignItems: 'center' },
  title: { fontFamily: ANTON_FAMILY, fontSize: 34, lineHeight: 40, color: C.ink, textTransform: 'uppercase' },
  meta: { marginTop: 2, fontSize: 13, lineHeight: 18, fontWeight: '600', color: C.muted, letterSpacing: 0.3 },
  footer: { position: 'absolute', left: 20, right: 20, bottom: 0 },
  button: { height: 56, borderRadius: 4, backgroundColor: C.ink, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', paddingHorizontal: 20 },
  action: { fontSize: 16, fontWeight: '700', color: C.paper, letterSpacing: 0.2 },
  arrow: { fontSize: 20, fontWeight: '700', color: C.paper },
});
