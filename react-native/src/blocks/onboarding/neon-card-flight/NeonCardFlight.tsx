import {
  Canvas, ClipOp, FilterMode, MipmapMode, PaintStyle, Picture, Skia, StrokeCap, TileMode, useImage,
  type SkCanvas, type SkImage, type SkPaint,
} from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { AccessibilityInfo, type LayoutChangeEvent, Pressable, StyleSheet, Text, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import { useAnimatedReaction, useDerivedValue, useFrameCallback, useReducedMotion, useSharedValue } from 'react-native-reanimated';
import { scheduleOnRN } from 'react-native-worklets';
import { ACCENT, PIECES, cardPose, piecePose, track, type Card, type Point } from './neon-motion';

export const LOOP = 8;
type Props = { autoPlay?: boolean; renderTime?: number };
type Frame = { time: number; reset: number };
type Motion = { now: number; epoch: number; manual: number; dragging: boolean; grab: number; released: number };
type Images = Record<string, SkImage | null>;
function clamp(x: number, a = 0, b = 1) { 'worklet'; return Math.min(b, Math.max(a, x)); }
function seg(t: number, a: number, b: number) { 'worklet'; return clamp((t - a) / (b - a)); }
function ease(x: number) { 'worklet'; return x * x * (3 - 2 * x); }
function fract(x: number) { 'worklet'; return x - Math.floor(x); }
function frame(m: Motion, now: number, autoPlay: boolean, reduced: boolean, renderTime?: number): Frame {
  'worklet';
  if (renderTime !== undefined) return { time: renderTime % LOOP, reset: 0 };
  if (reduced) return { time: 5.4, reset: 0 };
  if (m.manual >= 0) return { time: m.manual, reset: m.released >= 0 ? ease(seg(now - m.released, 2, 2.65)) : 0 };
  return { time: autoPlay ? Math.max(now - m.epoch, 0) % LOOP : 0, reset: 0 };
}
function project(p: Point[]) {
  'worklet'; const dx1 = p[1].x - p[2].x, dx2 = p[3].x - p[2].x, dy1 = p[1].y - p[2].y, dy2 = p[3].y - p[2].y;
  const sx = p[0].x - p[1].x + p[2].x - p[3].x, sy = p[0].y - p[1].y + p[2].y - p[3].y;
  const divisor = dx1 * dy2 - dx2 * dy1;
  const g = Math.abs(divisor) > 0.00001 ? (sx * dy2 - dx2 * sy) / divisor : 0;
  const h = Math.abs(divisor) > 0.00001 ? (dx1 * sy - sx * dy1) / divisor : 0;
  return Skia.Matrix([(p[1].x - p[0].x + g * p[1].x) / 300, (p[3].x - p[0].x + h * p[3].x) / 190, p[0].x,
    (p[1].y - p[0].y + g * p[1].y) / 300, (p[3].y - p[0].y + h * p[3].y) / 190, p[0].y, g / 300, h / 190, 1]);
}
function rgba(hex: number, alpha: number) {
  'worklet'; return Float32Array.of(((hex >> 16) & 255) / 255, ((hex >> 8) & 255) / 255, (hex & 255) / 255, alpha);
}
function bitmap(c: SkCanvas, image: SkImage, x: number, y: number, w: number, h: number, paint: SkPaint) {
  'worklet'; c.drawImageRectOptions(image, Skia.XYWHRect(0, 0, image.width(), image.height()), Skia.XYWHRect(x, y, w, h), FilterMode.Linear, MipmapMode.None, paint);
}
function speedlines(c: SkCanvas, t: number, alpha: number) {
  'worklet'; if (t >= 2.55 || alpha <= 0) return;
  const travel = Math.max(t - 0.65, 0), colors = [0xBAB3C4,0xCF408F,0xD8D3DD,0xC5BDCC,0xBDB5C9,0xE2E800,0xD5D2DB,0xBCB3C2,0xC9C4D0,0xA272AD,0xC8C0CD,0xD2CDD6,0xC1BCC8,0xF36B3F,0xD8D3DE,0xC9C4CF];
  c.save(); c.clipRect(Skia.XYWHRect(0, 198, 393, 654), ClipOp.Intersect, true);
  const paint = Skia.Paint(); paint.setAntiAlias(true); paint.setStyle(PaintStyle.Stroke); paint.setStrokeCap(StrokeCap.Round);
  for (let i = 0; i < 192; i++) {
    const hash = fract(Math.sin(i * 12.9898 + 3.2) * 43758.5453), angle = i * 2.399963 + hash * 0.4;
    const unit = fract(hash + travel * (0.95 + hash * 0.30)), radius = 35 + Math.pow(unit, 1.3) * 185, length = 12 + Math.pow(unit, 1.15) * (115 + 200 * Math.max(Math.sin(angle), 0));
    const x = Math.cos(angle), y = Math.sin(angle), start = { x: 196 + x * radius, y: 352 + y * radius };
    const end = { x: 196 + x * (radius + length), y: 352 + y * (radius + length) };
    const path = Skia.Path.Make(); path.moveTo(start.x, start.y); path.lineTo(end.x, end.y);
    const accent = i % 16 === 1 || i % 16 === 5 || i % 16 === 9 || i % 16 === 13;
    const opacity = (0.35 + 0.60 * unit) * (accent ? 0.90 : 0.85) * alpha;
    paint.setShader(Skia.Shader.MakeLinearGradient(start, end, [rgba(colors[i % 16], opacity * 0.10), rgba(colors[i % 16], opacity)], [0, 1], TileMode.Clamp));
    const width = (accent ? 1.0 : 0.8) + Math.pow(unit, 1.2) * (accent ? 4.8 : 5.5);
    paint.setAlphaf(0.16); paint.setStrokeWidth(width + 2.2); c.drawPath(path, paint);
    paint.setAlphaf(1); paint.setStrokeWidth(width); c.drawPath(path, paint);
  }
  c.restore();
}
function pieces(c: SkCanvas, t: number, foreground: boolean, images: Images) {
  'worklet';
  for (const piece of PIECES) {
    if (piece.foreground !== foreground) continue;
    const pose = piecePose(piece, t); if (pose.alpha <= 0) continue;
    const image = images[`${piece.name}-final`]; if (!image) continue;
    c.save(); c.translate(pose.x,pose.y); c.rotate(pose.roll,0,0); c.scale(pose.scale,pose.scale);
    c.concat(Skia.Matrix([pose.a,pose.c,pose.tx,pose.b,pose.d,pose.ty,0,0,1]));
    const paint=Skia.Paint();paint.setAntiAlias(true);paint.setAlphaf(pose.alpha);
    bitmap(c,image,-piece.w/2,-piece.h/2,piece.w,piece.h,paint);c.restore();
  }
}
function accents(c: SkCanvas, t: number) {
  'worklet'; const s = Math.max(t - 0.65, 0); if (s < 2.4) return;
  const x = track(s, ACCENT, 1), y = track(s, ACCENT, 2), roll = track(s, ACCENT, 3), alpha = ease(seg(s, 2.4, 2.55));
  c.save(); c.translate(x, y); c.rotate(roll, 0, 0);
  const paint = Skia.Paint(); paint.setAntiAlias(true); paint.setStyle(PaintStyle.Stroke); paint.setStrokeCap(StrokeCap.Square);
  for (let i = 0; i < 3; i++) {
    const dx = (i - 1) * 20, path = Skia.Path.Make(); path.moveTo(dx - 8, -8); path.lineTo(dx + 2, 0); path.lineTo(dx - 8, 8);
    paint.setColor(rgba(0xABBF00, alpha)); paint.setStrokeWidth(4.2); c.save(); c.translate(0, 1.4); c.drawPath(path, paint); c.restore();
    paint.setColor(rgba(0xE8FF00, alpha)); paint.setStrokeWidth(3.2); c.drawPath(path, paint);
  }
  c.restore();
}
function drawCard(c: SkCanvas, pose: Card, card: SkImage | null) {
  'worklet'; if (!pose.visible || !card) return;
  const moving = Math.hypot(pose.trail.x, pose.trail.y) > 0.01, weights = moving ? [0.06, 0.10, 0.16, 0.25, 1] : [1];
  const paint = Skia.Paint(); paint.setAntiAlias(true);
  for (let i = 0; i < weights.length; i++) {
    const lag = weights.length - 1 - i, corners = pose.points.map(p => ({ x: p.x - pose.trail.x * lag, y: p.y - pose.trail.y * lag }));
    c.save(); c.concat(project(corners)); paint.setAlphaf(weights[i]); bitmap(c, card, 0, 0, 300, 190, paint); c.restore();
  }
}
function scene(c: SkCanvas, t: number, alpha: number, images: Images, card: SkImage | null) {
  'worklet'; if (alpha <= 0) return;
  const layer = alpha < 1;
  if (layer) { const p = Skia.Paint(); p.setAlphaf(alpha); c.saveLayer(p, Skia.XYWHRect(0, 0, 393, 852)); }
  const source = Math.max(t - 0.65, 0), inFront = source >= 3.35 && source <= 4.05, pose = cardPose(t);
  speedlines(c, t, 1); if (!inFront) accents(c, t);
  pieces(c, t, false, images);
  if (pose.behindForeground) drawCard(c, pose, pose.intro ? images['card-intro'] : card);
  pieces(c, t, true, images);
  if (!pose.behindForeground) drawCard(c, pose, pose.intro ? images['card-intro'] : card);
  if (inFront) accents(c, t);
  if (layer) c.restore();
}
function hero(c: SkCanvas, t: number, alpha: number, images: Images, card: SkImage | null) {
  'worklet'; scene(c, t, alpha * (1 - ease(seg(t, 6.22, 6.72))), images, card);
  if (t >= 6.72) scene(c, 0, alpha * ease(seg(t, 6.72, 7.32)), images, card);
}
function useSprites(): Images {
  const image0=useImage(require('./assets/cap-final.png'));
  const image1=useImage(require('./assets/coin-final.png'));
  const image2=useImage(require('./assets/cup-final.png'));
  const image3=useImage(require('./assets/headphones-final.png'));
  const image4=useImage(require('./assets/links-final.png'));
  const image5=useImage(require('./assets/locks-final.png'));
  const image6=useImage(require('./assets/orange-final.png'));
  const image7=useImage(require('./assets/shield-final.png'));
  const image8=useImage(require('./assets/star-final.png'));
  const image9=useImage(require('./assets/card-intro.png'));
  return useMemo(()=>({'cap-final':image0,'coin-final':image1,'cup-final':image2,'headphones-final':image3,'links-final':image4,'locks-final':image5,'orange-final':image6,'shield-final':image7,'star-final':image8,'card-intro':image9}),[image0,image1,image2,image3,image4,image5,image6,image7,image8,image9]);
}
function tactile() { Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {}); }

/** A frame-for-frame port of the Swift card projection, sculpture and eight-second clock. */
export function NeonCardFlight({ autoPlay = false, renderTime }: Props) {
  const initialReduced = useReducedMotion();
  const [reduced, setReduced] = useState(initialReduced), [manualClock, setManualClock] = useState(false);
  const [size, setSize] = useState({ width: 393, height: 852 });
  const card = useImage(require('./assets/card.png'));
  const images = useSprites();
  const ready = !!card && Object.values(images).every(Boolean);
  const scale = Math.min(size.width / 393, size.height / 852), left = (size.width - 393 * scale) / 2, top = (size.height - 852 * scale) / 2;
  const motion = useSharedValue<Motion>({ now: 0, epoch: 0, manual: -1, dragging: false, grab: 0, released: -1 });
  useEffect(() => { const s = AccessibilityInfo.addEventListener('reduceMotionChanged', setReduced); return () => s.remove(); }, []);
  useEffect(() => {
    const now = Date.now() / 1000; motion.set({ now, epoch: now, manual: -1, dragging: false, grab: 0, released: -1 });
  }, [motion, ready, reduced]);
  const ticker = useFrameCallback(() => {
    const now = Date.now() / 1000, old = motion.get();
    if (old.released >= 0 && !old.dragging && now - old.released >= 2.65) {
      motion.set({ ...old, now, epoch: old.released + 2.65, manual: -1, released: -1 }); scheduleOnRN(setManualClock, false);
    } else motion.set({ ...old, now });
  }, false);
  useEffect(() => {
    ticker.setActive(ready && !reduced && renderTime === undefined && (autoPlay || manualClock));
    return () => ticker.setActive(false);
  }, [autoPlay, manualClock, ready, reduced, renderTime, ticker]);
  useAnimatedReaction(() => ({ dragging: motion.get().dragging, threshold: motion.get().manual >= 3.5 }), (next, previous) => {
    if (next.dragging && previous?.dragging && next.threshold !== previous.threshold) scheduleOnRN(tactile);
  });
  const pose = useDerivedValue(() => frame(motion.get(), motion.get().now, autoPlay, reduced, renderTime));
  const picture = useDerivedValue(() => {
    const recorder = Skia.PictureRecorder(), c = recorder.beginRecording(Skia.XYWHRect(0, 0, size.width, size.height));
    c.translate(left, top); c.scale(scale, scale);
    const p = pose.get(); hero(c, p.time, 1 - p.reset, images, card); if (p.reset > 0) hero(c, 0, p.reset, images, card);
    return recorder.finishRecordingAsPicture();
  });
  const begin = useCallback(() => {
    'worklet'; if (reduced || renderTime !== undefined) return;
    const now = Date.now() / 1000, old = motion.get(), p = frame(old, now, autoPlay, reduced);
    motion.set({ ...old, now, manual: p.time, grab: p.time, dragging: true, released: -1 }); scheduleOnRN(setManualClock, true);
  }, [autoPlay, motion, reduced, renderTime]);
  const pan = Gesture.Pan().minDistance(0).onBegin(begin).onUpdate(event => {
    if (reduced || renderTime !== undefined) return;
    motion.set(old => ({ ...old, now: Date.now() / 1000, manual: clamp(old.grab + event.translationX / scale / 55, 0, 6.15) }));
  }).onFinalize(() => {
    if (!motion.get().dragging) return;
    motion.set(old => ({ ...old, now: Date.now() / 1000, dragging: false, released: Date.now() / 1000 }));
  });
  const finish = () => {
    if (renderTime !== undefined) return;
    const now = Date.now() / 1000; motion.set(old => ({ ...old, now, manual: 5.4, dragging: false, released: now })); setManualClock(true); tactile();
  };
  const onLayout = useCallback((e: LayoutChangeEvent) => setSize(e.nativeEvent.layout), []);
  const box = (x: number, y: number, width: number, height: number) => ({ position: 'absolute' as const, left: left + x * scale, top: top + y * scale, width: width * scale, height: height * scale });
  return (
    <View style={styles.screen} onLayout={onLayout}>
      <StatusBar style="dark" />
      <Canvas style={StyleSheet.absoluteFill} pointerEvents="none"><Picture picture={picture} /></Canvas>
      <View style={[box(10, 64, 373, 0.5), { backgroundColor: '#00000052' }]} />
      <View style={[box(19, 73, 355, 18), styles.brand, { gap: 7 * scale }]} pointerEvents="none">
        <Text allowFontScaling={false} style={{ color: '#131619', fontSize: 15 * scale, fontWeight: '900' }}>N</Text>
        <Text allowFontScaling={false} style={{ color: '#131619', fontSize: 12 * scale }}>Welcome to Nova &lt;18</Text>
      </View>
      <Text allowFontScaling={false} style={[box(21, 111, 355, 70), styles.headline, { fontSize: 27 * scale, lineHeight: 28.5 * scale, letterSpacing: -0.65 * scale }]}>{'INDEPENDENCE STARTS\nHERE'}</Text>
      <GestureDetector gesture={pan}>
        <View style={box(0, 193, 393, 483)} accessible accessibilityRole="adjustable" accessibilityLabel="Card flight"
          accessibilityHint="Drag right to fly the card and assemble the objects. Drag left to rewind."
          accessibilityActions={[{ name: 'increment' }, { name: 'decrement' }]}
          onAccessibilityAction={event => {
            if (renderTime !== undefined || reduced) return;
            const now = Date.now() / 1000, old = motion.get(), current = frame(old, now, autoPlay, reduced).time;
            motion.set({ ...old, now, manual: clamp(current + (event.nativeEvent.actionName === 'increment' ? 0.7 : -0.7), 0, 6.15), released: now }); setManualClock(true);
          }} />
      </GestureDetector>
      <Pressable accessibilityRole="button" onPress={finish} style={[box(17, 691, 359, 47), styles.button, { borderRadius: 23.5 * scale, backgroundColor: '#131619' }]}>
        <Text allowFontScaling={false} style={{ color: '#FFFFFF', fontSize: 13 * scale, fontWeight: '500' }}>Log in to Nova &lt;18</Text>
      </Pressable>
      <Pressable accessibilityRole="button" onPress={finish} style={[box(17, 751, 359, 47), styles.button, styles.secondary, { borderRadius: 23.5 * scale }]}>
        <Text allowFontScaling={false} style={{ color: '#131619', fontSize: 13 * scale, fontWeight: '500' }}>Create a new account</Text>
      </Pressable>
    </View>
  );
}
const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: '#FFFDFF', overflow: 'hidden' },
  brand: { flexDirection: 'row', alignItems: 'center', gap: 7 },
  headline: { color: '#131619', fontWeight: '800', includeFontPadding: false },
  button: { alignItems: 'center', justifyContent: 'center' },
  secondary: { backgroundColor: '#FFFFFF', boxShadow: '0px 2px 3px #0000001A, 0px 4px 8px #00000014' },
});
