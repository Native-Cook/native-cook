import { BlurStyle, Canvas, Fill, FilterMode, Group, Image, ImageShader, MipmapMode, PaintStyle, Picture, Shader, Skia, StrokeCap, TileMode, makeImageFromView, rect, rrect, type SkImage, type SkPath, type SkPicture } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useEffect, useMemo, useRef, useState } from 'react';
import { Platform, Pressable, StyleSheet, Text, useWindowDimensions, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, { ReduceMotion, type SharedValue, useAnimatedStyle, useDerivedValue, useFrameCallback, useReducedMotion, useSharedValue, withTiming } from 'react-native-reanimated';
import Svg, { Path as SvgPath } from 'react-native-svg';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';

type Props = { autoPlay?: boolean; previewTime?: number };
type Point = { x: number; y: number };
type Pose = { phase: number; amplitude: number; pointer: Point; pointerStrength: number; velocity: Point; ringOffset: Point; extraRingAngle: number };
export const LOOP = 8;
const WIDTH = 393; const HEIGHT = 852;
const WATER = rect(18, 83, 357, 711); const WATER_CLIP = rrect(WATER, 31, 31);
const SERIF = Platform.OS === 'ios' ? 'ui-serif' : 'serif';
const REST: Pose = { phase: 0, amplitude: 0, pointer: { x: 196.5, y: 420 }, pointerStrength: 0, velocity: { x: 0, y: 0 }, ringOffset: { x: 0, y: 0 }, extraRingAngle: 0 };

function seconds() { 'worklet'; return Date.now() / 1000; }
function clamp(value: number, low = 0, high = 1) { 'worklet'; return Math.min(high, Math.max(low, value)); }
function ease(value: number) { 'worklet'; const u = clamp(value); return u * u * (3 - 2 * u); }
function ringBob(p: Pose): Point { 'worklet'; return { x: 4 * p.amplitude * Math.sin(p.phase), y: 3 * p.amplitude * Math.sin(2 * p.phase) }; }
function ringCenter(p: Pose): Point { 'worklet'; const bob = ringBob(p); return { x: 127 + bob.x + p.ringOffset.x, y: 738 + bob.y + p.ringOffset.y }; }
function duckCenter(p: Pose): Point { 'worklet'; return { x: 224 + 3 * p.amplitude * (Math.cos(p.phase) - 1), y: 353 + 2 * p.amplitude * Math.sin(p.phase) }; }
function clampRing(p: Pose): Pose {
  'worklet'; const center = ringCenter(p);
  return { ...p, ringOffset: { x: p.ringOffset.x + clamp(center.x, 54, 339) - center.x, y: p.ringOffset.y + clamp(center.y, 132, 756) - center.y } };
}
function returning(p: Pose, amount: number): Pose {
  'worklet'; if (amount <= 0) return p; const keep = 1 - amount;
  return { ...p, phase: amount >= 1 ? 0 : p.phase - Math.atan2(Math.sin(p.phase), Math.cos(p.phase)) * amount, amplitude: p.amplitude * keep, pointerStrength: p.pointerStrength * keep, velocity: { x: p.velocity.x * keep, y: p.velocity.y * keep }, ringOffset: { x: p.ringOffset.x * keep, y: p.ringOffset.y * keep }, extraRingAngle: p.extraRingAngle * keep };
}
function loopPose(raw: number): Pose {
  'worklet'; const t = (raw % LOOP + LOOP) % LOOP;
  if (t < 1.4 || t >= 7.4) return REST;
  let amplitude: number;
  if (t < 2.8) amplitude = 0.65 * ease((t - 1.4) / 1.4);
  else if (t < 3.7) amplitude = 0.65 + 0.35 * ease((t - 2.8) / 0.9);
  else if (t < 4.9) amplitude = 1;
  else if (t < 6.5) amplitude = 1 - 0.65 * ease((t - 4.9) / 1.6);
  else amplitude = 0.35 * (1 - ease((t - 6.5) / 0.9));
  return { ...REST, amplitude, phase: 2 * Math.PI * ease((t - 1.4) / 6) };
}

const WATER_EFFECT = Skia.RuntimeEffect.Make(`
uniform shader artwork;
uniform float phase;
uniform float amplitude;
uniform float2 pointer;
uniform float pointerStrength;
uniform float2 velocity;
half4 main(float2 p) {
  float2 world = p + float2(18.0, 83.0);
  float k1 = 0.029 * world.x + 0.018 * world.y - phase;
  float k2 = -0.021 * world.x + 0.031 * world.y + 2.0 * phase;
  float k3 = 0.038 * world.x - 0.012 * world.y - 3.0 * phase;
  float2 slope = float2(0.029*cos(k1)-0.01155*cos(k2)+0.0095*cos(k3),
                        0.018*cos(k1)+0.01705*cos(k2)-0.003*cos(k3));
  float2 delta = p-pointer;
  float lens = exp(-dot(delta,delta)/(115.0*115.0))*pointerStrength;
  float2 shift = slope*(60.0+200.0*amplitude+90.0*lens)+velocity*(4.0*lens);
  float titleBand = exp(-pow((world.y-420.0)/115.0,2.0));
  shift.y += 7.0*titleBand*sin(0.045*world.x+phase);
  float2 q = clamp(p+shift,float2(0.5),float2(356.5,710.5));
  half4 sampled = artwork.eval(q);
  float softLight = pow(max(0.0,0.55+0.25*sin(k1+k2)+0.20*cos(k2-k3)),8.0);
  float ridge = pow(max(0.0,1.0-abs(sin(k1+k2+0.35*sin(k3)))*2.6),4.0);
  float light = (0.65*softLight+0.35*ridge)*(0.03+0.26*amplitude+0.10*lens);
  // Swift's layer samples use encoded sRGB for this blend. Preserve that lift on P3 surfaces.
  float3 linear = max(float3(toLinearSrgb(sampled.rgb)),float3(0.0));
  float3 encoded = mix(12.92*linear,1.055*pow(linear,float3(1.0/2.4))-0.055,step(float3(0.0031308),linear));
  encoded = mix(encoded,float3(0.615,0.897,0.956),min(light,0.30));
  linear = mix(encoded/12.92,pow((encoded+0.055)/1.055,float3(2.4)),step(float3(0.04045),encoded));
  return half4(fromLinearSrgb(half3(linear)),sampled.a);
}`);
if (!WATER_EFFECT) throw new Error('Pool Caustic Welcome: water shader did not compile.');

function palmPath(): SkPath {
  const b = Skia.PathBuilder.Make();
  b.moveTo(-47, 725).cubicTo(10, 670, 79, 516, 168, 323).lineTo(176, 323).cubicTo(94, 522, 32, 680, -30, 737).close();
  const center = { x: 172, y: 323 };
  const ends = [[-22, 257], [11, 189], [91, 172], [190, 174], [287, 218], [350, 298], [329, 388], [268, 469], [165, 524]];
  ends.forEach(([x, y], index) => {
    const dx = x - center.x; const dy = y - center.y; const length = Math.hypot(dx, dy); const nx = -dy / length; const ny = dx / length;
    const bend = index % 2 === 0 ? 18 : -18; const mx = center.x + dx * 0.58 + nx * bend; const my = center.y + dy * 0.58 + ny * bend;
    b.moveTo(center.x, center.y).quadTo(mx + nx * 44, my + ny * 44, x, y).quadTo(mx - nx * 44, my - ny * 44, center.x, center.y).close();
  });
  return b.build();
}

/** All below-water geometry and the native serif title are rasterized once, never per frame. */
function makeBase(title: SkImage | null): SkImage {
  const surface = Skia.Surface.MakeOffscreen(714, 1422);
  if (!surface) throw new Error('Pool Caustic Welcome: could not allocate artwork surface.');
  const c = surface.getCanvas(); c.scale(2, 2); c.translate(-18, -83);
  const fill = Skia.Paint(); fill.setAntiAlias(true);
  fill.setShader(Skia.Shader.MakeLinearGradient({ x: 196.5, y: 83 }, { x: 196.5, y: 794 }, ['#579de9', '#4898e4', '#3586d4'].map(Skia.Color), [0, 0.5, 1], TileMode.Clamp));
  c.drawRect(WATER, fill);
  const grid = Skia.PathBuilder.Make();
  for (let i = 0; i < 9; i++) grid.moveTo(18 + i * 48, 83).lineTo(18 + i * 48, 794);
  for (let i = 0; i < 16; i++) grid.moveTo(18, 83 + i * 48).lineTo(375, 83 + i * 48);
  const pen = Skia.Paint(); pen.setAntiAlias(true); pen.setStyle(PaintStyle.Stroke); pen.setStrokeWidth(0.6); pen.setColor(Skia.Color('rgba(178,217,240,0.13)')); c.drawPath(grid.build(), pen);
  const palm = palmPath();
  for (const shadow of [{ x: 0, y: 0, blur: 9, alpha: 0.74 }, { x: 7, y: 10, blur: 13, alpha: 0.14 }]) {
    const paint = Skia.Paint(); paint.setAntiAlias(true); paint.setColor(Skia.Color(`rgba(23,74,210,${shadow.alpha})`)); paint.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, shadow.blur / 2, true));
    c.save(); c.translate(shadow.x, shadow.y); c.drawPath(palm, paint); c.restore();
  }
  pen.setStrokeCap(StrokeCap.Round);
  for (const y of [552, 590]) {
    const rail = Skia.PathBuilder.Make().moveTo(2, y).cubicTo(52, y - 2, 112, y + 1, 103, y + 12).build();
    pen.setColor(Skia.Color('rgba(55,110,153,0.75)')); pen.setStrokeWidth(6); c.drawPath(rail, pen);
    pen.setColor(Skia.Color('rgba(195,221,229,0.95)')); pen.setStrokeWidth(3); c.drawPath(rail, pen);
  }
  for (const x of [20, 41, 62, 83, 101]) {
    pen.setColor(Skia.Color('#4b718d')); pen.setStrokeWidth(5); c.drawLine(x, 552 + x * 0.08, x - 2, 590 + x * 0.08, pen);
    pen.setColor(Skia.Color('rgba(214,224,223,0.85)')); pen.setStrokeWidth(2.5); c.drawLine(x, 552 + x * 0.08, x - 2, 590 + x * 0.08, pen);
  }
  if (title) c.drawImageRectOptions(title, rect(0, 0, title.width(), title.height()), rect(42.5, 343, 308, 154), FilterMode.Linear, MipmapMode.None, Skia.Paint());
  surface.flush();
  // A raster image can safely cross from the RN cache builder to Skia's UI renderer.
  const snapshot = surface.makeImageSnapshot();
  return snapshot.makeNonTextureImage() ?? snapshot;
}

function makeRing(): SkPicture {
  const rec = Skia.PictureRecorder(); const c = rec.beginRecording(rect(-24, -24, 48, 48));
  const pen = Skia.Paint(); pen.setAntiAlias(true); pen.setStyle(PaintStyle.Stroke); pen.setStrokeWidth(1.4); pen.setColor(Skia.Color('#dee4dc')); c.drawCircle(0, 0, 22, pen);
  const body = Skia.PathBuilder.Make().addOval(rect(-19, -19, 38, 38)).addOval(rect(-10, -10, 20, 20)).build(); body.setFillType(1);
  const fill = Skia.Paint(); fill.setAntiAlias(true); fill.setColor(Skia.Color('#a8796b'));
  c.save(); c.translate(0, 3); c.drawPath(body, fill); c.restore();
  fill.setShader(Skia.Shader.MakeLinearGradient({ x: -16, y: -18 }, { x: 18, y: 18 }, [Skia.Color('#fffdf0'), Skia.Color('#d6ddd9')], null, TileMode.Clamp)); c.drawPath(body, fill);
  fill.setShader(null); fill.setColor(Skia.Color('#d76060'));
  for (let i = 0; i < 4; i++) {
    const start = i * 90 - 30; const end = start + 60; const radians = end * Math.PI / 180;
    const b = Skia.PathBuilder.Make().arcToOval(rect(-19, -19, 38, 38), start, 60, true).lineTo(10 * Math.cos(radians), 10 * Math.sin(radians)).arcToOval(rect(-10, -10, 20, 20), end, -60, false).close();
    c.drawPath(b.build(), fill);
  }
  pen.setStrokeWidth(0.8); pen.setColor(Skia.Color('rgba(255,255,255,0.37)')); c.drawCircle(0, 0, 18.5, pen);
  pen.setColor(Skia.Color('rgba(111,131,148,0.35)')); c.drawCircle(0, 0, 10, pen);
  return rec.finishRecordingAsPicture();
}
function makeDuck(): SkPicture {
  const rec = Skia.PictureRecorder(); const c = rec.beginRecording(rect(-31, -29, 62, 58));
  const body = rect(-24, -23, 48, 49); const p = Skia.Paint(); p.setAntiAlias(true);
  p.setShader(Skia.Shader.MakeRadialGradient({ x: -7, y: -8 }, 39, ['#ffe767', '#ffe767', '#ffd52f', '#eab620'].map(Skia.Color), [0, 2 / 39, 20.5 / 39, 1], TileMode.Clamp)); c.drawOval(body, p);
  p.setShader(null); p.setStyle(PaintStyle.Stroke); p.setStrokeWidth(1); p.setColor(Skia.Color('rgba(251,229,97,0.8)')); c.drawOval(body, p);
  p.setStyle(PaintStyle.Fill); p.setColor(Skia.Color('#ffd733')); c.drawOval(rect(-18, -22, 23, 24), p);
  p.setColor(Skia.Color('#e97a20')); c.drawPath(Skia.PathBuilder.Make().moveTo(-18, -14).quadTo(-28, -25, -9, -22).lineTo(-5, -13).close().build(), p);
  p.setColor(Skia.Color('#5f4c20')); c.drawOval(rect(-14, -13, 2.5, 2.5), p);
  p.setColor(Skia.Color('#e6bc22')); p.setStyle(PaintStyle.Stroke); p.setStrokeWidth(1.6); p.setStrokeCap(StrokeCap.Round); c.drawPath(Skia.PathBuilder.Make().moveTo(0, -1).quadTo(19, 1, 11, 17).build(), p);
  p.setStyle(PaintStyle.Fill); p.setColor(Skia.Color('rgba(255,255,255,0.18)')); c.drawOval(rect(-15, -4, 9, 20), p);
  return rec.finishRecordingAsPicture();
}
function makeObjectShadow(width: number, height: number, alpha: number, blur: number): SkPicture {
  const rec = Skia.PictureRecorder(); const c = rec.beginRecording(rect(-width / 2 - blur * 3, -height / 2 - blur * 3, width + blur * 6, height + blur * 6));
  const p = Skia.Paint(); p.setAntiAlias(true); p.setColor(Skia.Color(`rgba(28,96,152,${alpha})`)); p.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, blur, true)); c.drawOval(rect(-width / 2, -height / 2, width, height), p);
  return rec.finishRecordingAsPicture();
}
function FloatingObjects({ pose }: { pose: SharedValue<Pose> }) {
  const artwork = useMemo(() => ({ ring: makeRing(), duck: makeDuck(), ringShadow: makeObjectShadow(59, 23, 0.30, 6), duckShadow: makeObjectShadow(44, 25, 0.22, 5), ringContact: makeObjectShadow(53, 29, 0.12, 2), duckContact: makeObjectShadow(43, 29, 0.13, 1.8) }), []);
  const ring = useDerivedValue(() => { const p = pose.get(); const c = ringCenter(p); return [{ translateX: c.x }, { translateY: c.y }, { rotate: (8 * p.amplitude * Math.sin(p.phase) + p.extraRingAngle - 14) * Math.PI / 180 }, { scaleX: 1.38 }, { scaleY: 0.95 }]; });
  const secondaryRing = useDerivedValue(() => { const p = pose.get(); const c = ringCenter(p); return [{ translateX: c.x + 10 }, { translateY: c.y - 26 }, { rotate: (8 * p.amplitude * Math.sin(p.phase) + p.extraRingAngle + 12) * Math.PI / 180 }, { scaleX: 1.18 }, { scaleY: 0.78 }]; });
  const duck = useDerivedValue(() => { const p = pose.get(); const c = duckCenter(p); return [{ translateX: c.x }, { translateY: c.y }, { rotate: (-6 * p.amplitude * Math.sin(p.phase) - 26) * Math.PI / 180 }]; });
  const ringShadow = useDerivedValue(() => { const c = ringCenter(pose.get()); return [{ translateX: c.x + 3 }, { translateY: c.y + 17 }]; });
  const duckShadow = useDerivedValue(() => { const c = duckCenter(pose.get()); return [{ translateX: c.x + 2 }, { translateY: c.y + 13 }]; });
  const ringContact = useDerivedValue(() => { const c = ringCenter(pose.get()); return [{ translateX: c.x + 1 }, { translateY: c.y + 4 }]; });
  const duckContact = useDerivedValue(() => { const c = duckCenter(pose.get()); return [{ translateX: c.x + 1 }, { translateY: c.y + 4 }]; });
  return <Canvas pointerEvents="none" style={StyleSheet.absoluteFill}><Group clip={WATER_CLIP}>
    <Group transform={ringShadow}><Picture picture={artwork.ringShadow} /></Group><Group transform={duckShadow}><Picture picture={artwork.duckShadow} /></Group>
    <Group transform={ringContact}><Picture picture={artwork.ringContact} /></Group><Group transform={duckContact}><Picture picture={artwork.duckContact} /></Group>
    <Group transform={secondaryRing}><Picture picture={artwork.ring} /></Group><Group transform={ring}><Picture picture={artwork.ring} /></Group><Group transform={duck}><Picture picture={artwork.duck} /></Group>
  </Group></Canvas>;
}
function haptic() { if (Platform.OS !== 'web') void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {}); }

/** Caustic light refracts the cached title, tiles and palm below the water. */
export function PoolCausticWelcome({ autoPlay = false, previewTime }: Props) {
  const window = useWindowDimensions(); const [size, setSize] = useState({ width: window.width, height: window.height });
  const [started] = useState(seconds); const [manualLive, setManualLive] = useState(false); const [slowDayStarted, setSlowDayStarted] = useState(false);
  const [title, setTitle] = useState<SkImage | null>(null); const titleView = useRef<View>(null); const captureTimer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const base = useMemo(() => makeBase(title), [title]); const reduced = useReducedMotion();
  const clock = useSharedValue(started); const epoch = useSharedValue(started); const lastTouch = useSharedValue(-1); const touchBegan = useSharedValue(-1); const releasedAt = useSharedValue(-1);
  const dragStart = useSharedValue<Pose>(REST); const held = useSharedValue<Pose>(REST); const released = useSharedValue<Pose>(REST); const ringStart = useSharedValue<Point>({ x: 127, y: 738 }); const ringVelocity = useSharedValue<Point>({ x: 0, y: 0 });
  const dragging = useSharedValue(false); const draggingRing = useSharedValue(false); const live = useSharedValue(false); const crest = useSharedValue(false); const lastHaptic = useSharedValue(-1);
  const ctaDone = useSharedValue(0); const absoluteStart = useSharedValue<Point>({ x: 0, y: 0 });
  const scale = Math.min(size.width / WIDTH, size.height / HEIGHT); const left = (size.width - WIDTH * scale) / 2; const top = (size.height - HEIGHT * scale) / 2;
  const pose = useDerivedValue<Pose>(() => {
    if (previewTime !== undefined) return reduced ? REST : loopPose(previewTime);
    if (reduced) return { ...REST, ringOffset: dragging.get() ? held.get().ringOffset : released.get().ringOffset };
    if (dragging.get()) {
      const p = held.get(); const activation = ease((clock.get() - touchBegan.get()) / 0.25);
      const caught = ringCenter(p);
      const shown = { ...p, amplitude: p.amplitude + (Math.max(0.85, p.amplitude) - p.amplitude) * activation, pointerStrength: Math.max(p.pointerStrength, activation * 0.75) };
      if (draggingRing.get()) { const center = ringCenter(shown); shown.ringOffset = { x: shown.ringOffset.x + caught.x - center.x, y: shown.ringOffset.y + caught.y - center.y }; }
      return shown;
    }
    const age = clock.get() - lastTouch.get();
    if (lastTouch.get() >= 0 && age < 2.6) {
      const releaseAge = Math.max(0, clock.get() - releasedAt.get()); const from = released.get();
      const spring = releaseAge >= 0.8 ? 0 : Math.exp(-8 * releaseAge) * (Math.cos(14 * releaseAge) + 8 / 14 * Math.sin(14 * releaseAge));
      const p: Pose = { ...from, pointerStrength: from.pointerStrength * Math.exp(-releaseAge * 1.1), velocity: { x: from.velocity.x * Math.exp(-releaseAge * 4), y: from.velocity.y * Math.exp(-releaseAge * 4) }, ringOffset: { x: from.ringOffset.x * spring + ringVelocity.get().x * releaseAge * Math.exp(-8 * releaseAge), y: from.ringOffset.y * spring + ringVelocity.get().y * releaseAge * Math.exp(-8 * releaseAge) }, extraRingAngle: from.extraRingAngle * spring };
      if (crest.get()) { p.amplitude += (1 - p.amplitude) * ease(releaseAge / 0.55); p.phase += Math.PI * ease(releaseAge / 1.2); }
      return returning(clampRing(p), ease((age - 2) / 0.6));
    }
    return autoPlay ? loopPose(lastTouch.get() < 0 ? clock.get() - epoch.get() : age - 2.6) : REST;
  });
  const frames = useFrameCallback(() => {
    const now = seconds(); clock.set(now);
    if (live.get() && !dragging.get() && now - lastTouch.get() >= 2.65) { live.set(false); scheduleOnRN(setManualLive, false); }
  }, false);
  useEffect(() => { frames.setActive(!reduced && previewTime === undefined && (autoPlay || manualLive)); return () => frames.setActive(false); }, [autoPlay, reduced, previewTime, manualLive, frames]);
  useEffect(() => () => { if (captureTimer.current) clearTimeout(captureTimer.current); }, []);
  const captureTitle = () => {
    if (captureTimer.current) clearTimeout(captureTimer.current);
    captureTimer.current = setTimeout(() => { makeImageFromView(titleView).then((image) => { if (image) setTitle(image); }).catch(() => {}); }, 250);
  };
  const tick = () => { 'worklet'; const now = seconds(); if (now - lastHaptic.get() > 0.16) { lastHaptic.set(now); scheduleOnRN(haptic); } };
  const update = (x: number, y: number, dx: number, dy: number, vx: number, vy: number) => {
    'worklet'; const now = seconds(); const source = dragStart.get(); const activation = ease(Math.hypot(dx, dy) / 12);
    const p: Pose = { ...held.get(), phase: source.phase + dx * 0.013 + dy * 0.007, amplitude: source.amplitude, pointer: { x: clamp(x, 26, 367), y: clamp(y, 91, 786) }, pointerStrength: activation, velocity: { x: clamp(vx / 800, -1, 1), y: clamp(vy / 800, -1, 1) } };
    if (draggingRing.get()) { const bob = ringBob(p); p.ringOffset = { x: clamp(ringStart.get().x + dx, 54, 339) - 127 - bob.x, y: clamp(ringStart.get().y + dy, 132, 756) - 738 - bob.y }; p.extraRingAngle = clamp(dx * 0.09, -14, 14); }
    held.set(p); lastTouch.set(now); clock.set(now); live.set(true);
  };
  const begin = (absoluteX: number, absoluteY: number) => {
    'worklet'; const now = seconds(); const shown = pose.get(); const center = ringCenter(shown); const x = (absoluteX - left) / scale; const y = (absoluteY - top) / scale;
    dragStart.set(shown); held.set(shown); ringStart.set(center); draggingRing.set(Math.hypot(x - center.x, y - center.y) < 40); touchBegan.set(now); dragging.set(true); crest.set(false); absoluteStart.set({ x: absoluteX, y: absoluteY });
    if (draggingRing.get()) tick(); update(x, y, 0, 0, 0, 0); scheduleOnRN(setManualLive, true);
  };
  const end = (vx: number, vy: number) => {
    'worklet'; const now = seconds(); clock.set(now); released.set(pose.get()); ringVelocity.set(draggingRing.get() && !reduced ? { x: clamp(vx, -1400, 1400), y: clamp(vy, -1400, 1400) } : { x: 0, y: 0 }); releasedAt.set(now); lastTouch.set(now); dragging.set(false); live.set(true); scheduleOnRN(setManualLive, true); if (draggingRing.get()) tick(); draggingRing.set(false);
  };
  const makeDrag = () => Gesture.Pan().minDistance(0).onBegin((e) => begin(e.absoluteX, e.absoluteY))
    .onUpdate((e) => update((e.absoluteX - left) / scale, (e.absoluteY - top) / scale, (e.absoluteX - absoluteStart.get().x) / scale, (e.absoluteY - absoluteStart.get().y) / scale, e.velocityX / scale, e.velocityY / scale))
    .onEnd((e) => end(e.velocityX / scale, e.velocityY / scale)).onFinalize((_e, success) => { if (!success && dragging.get()) end(0, 0); });
  const waterDrag = makeDrag(); const ringDrag = makeDrag();
  const startSlowDay = () => {
    'worklet'; const now = seconds(); released.set({ ...pose.get(), pointerStrength: 0 }); releasedAt.set(now); lastTouch.set(now); clock.set(now); ringVelocity.set({ x: 0, y: 0 }); live.set(true); crest.set(true); scheduleOnRN(setManualLive, true); scheduleOnRN(setSlowDayStarted, true); if (reduced) ctaDone.set(withTiming(1, { duration: 150, reduceMotion: ReduceMotion.Never })); tick();
  };
  const uniforms = useDerivedValue(() => { const p = pose.get(); return { phase: p.phase, amplitude: p.amplitude, pointer: [p.pointer.x - 18, p.pointer.y - 83], pointerStrength: p.pointerStrength, velocity: [p.velocity.x, p.velocity.y] }; });
  const ringHit = useAnimatedStyle(() => { const c = ringCenter(pose.get()); return { left: c.x - 36, top: c.y - 36 }; });
  const ctaStart = useAnimatedStyle(() => ({ opacity: reduced ? 1 - ctaDone.get() : 1 })); const ctaFinal = useAnimatedStyle(() => ({ opacity: reduced ? ctaDone.get() : 0 }));

  return <View style={styles.screen} onLayout={(event) => { const { width, height } = event.nativeEvent.layout; setSize((previous) => previous.width === width && previous.height === height ? previous : { width, height }); }}>
    <StatusBar style="dark" />
    <View style={[styles.design, { left, top, transform: [{ scale }] }]}>
      <View pointerEvents="none" style={styles.poolShadow} />
      <View pointerEvents="none" style={styles.water}>
        <Canvas style={StyleSheet.absoluteFill}>{reduced ? <Image image={base} x={0} y={0} width={357} height={711} fit="fill" /> : <Fill><Shader source={WATER_EFFECT!} uniforms={uniforms}><ImageShader image={base} fit="fill" rect={rect(0, 0, 357, 711)} tx="clamp" ty="clamp" /></Shader></Fill>}</Canvas>
      </View>
      <View ref={titleView} collapsable={false} onLayout={captureTitle} pointerEvents="none" style={[styles.titleCapture, { opacity: title ? 0 : 1 }]} accessibilityElementsHidden importantForAccessibility="no-hide-descendants"><Text style={styles.title} adjustsFontSizeToFit minimumFontScale={0.5} numberOfLines={1}>afloat</Text></View>
      <FloatingObjects pose={pose} />
      <View pointerEvents="none" style={styles.innerStroke} />
      <View pointerEvents="none" style={styles.labels} accessibilityElementsHidden importantForAccessibility="no-hide-descendants">
        <Text style={styles.subtitle}>{'A slower kind\nof day.'}</Text>
      </View>
      <GestureDetector gesture={waterDrag}><View style={styles.waterHit} accessible accessibilityLabel="Afloat pool water" accessibilityHint="Drag to bend the light through the water" /></GestureDetector>
      <Pressable style={styles.ctaHit} accessibilityRole="button" accessibilityLabel={slowDayStarted && reduced ? 'Your slow day begins' : 'Start a slow day'} onPress={() => scheduleOnUI(startSlowDay)}>
        <View style={styles.cta}><Animated.Text style={[styles.ctaText, ctaStart]}>Start a slow day</Animated.Text><Animated.Text style={[styles.ctaText, ctaFinal]}>Your slow day begins</Animated.Text><Svg style={styles.arrow} width={12} height={12} viewBox="0 0 14 14"><SvgPath d="M2 7h10M8 3l4 4-4 4" stroke="rgba(255,255,255,0.95)" strokeWidth={1} fill="none" strokeLinecap="round" strokeLinejoin="round" /></Svg></View>
      </Pressable>
      <GestureDetector gesture={ringDrag}><Animated.View style={[styles.ringHit, ringHit]} accessible accessibilityLabel="Floating life ring" accessibilityHint="Drag the ring across the pool" /></GestureDetector>
      <View pointerEvents="none" style={styles.outerStroke} />
    </View>
  </View>;
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: '#f1f0e9', overflow: 'hidden' }, design: { position: 'absolute', width: WIDTH, height: HEIGHT, transformOrigin: 'top left', overflow: 'hidden' },
  poolShadow: { position: 'absolute', left: 18, top: 83, width: 357, height: 711, borderRadius: 31, backgroundColor: '#4d9de1', boxShadow: [{ offsetX: 0, offsetY: 1, blurRadius: 2, color: 'rgba(0,0,0,0.08)' }, { offsetX: 0, offsetY: 8, blurRadius: 13, color: 'rgba(0,0,0,0.10)' }] }, water: { position: 'absolute', left: 18, top: 83, width: 357, height: 711, borderRadius: 31, overflow: 'hidden' },
  titleCapture: { position: 'absolute', left: 42.5, top: 343, width: 308, height: 154 }, title: { width: 308, height: 154, fontFamily: SERIF, fontSize: 126, lineHeight: 154, letterSpacing: -8, color: 'rgba(220,236,232,0.66)', textAlign: 'center', includeFontPadding: false },
  innerStroke: { position: 'absolute', left: 18, top: 83, width: 357, height: 711, borderRadius: 31, borderWidth: 2, borderTopColor: 'rgba(255,255,255,0.24)', borderLeftColor: 'rgba(255,255,255,0.16)', borderRightColor: 'rgba(34,107,174,0.12)', borderBottomColor: 'rgba(34,107,174,0.20)' }, outerStroke: { position: 'absolute', left: 17, top: 82, width: 359, height: 713, borderRadius: 32, borderWidth: 2, borderColor: '#fdfdf6' },
  labels: { ...StyleSheet.absoluteFill }, subtitle: { position: 'absolute', left: 106.5, top: 488.8, width: 180, height: 44, fontFamily: SERIF, fontStyle: 'italic', fontSize: 16, lineHeight: 18.2, color: 'rgba(220,236,232,0.88)', textAlign: 'center', includeFontPadding: false },
  waterHit: { position: 'absolute', left: 18, top: 83, width: 357, height: 711, borderRadius: 31, backgroundColor: 'transparent' }, ringHit: { position: 'absolute', width: 72, height: 72, borderRadius: 36, backgroundColor: 'transparent' },
  ctaHit: { position: 'absolute', left: 85, top: 728.5, width: 222, height: 44, justifyContent: 'center' }, cta: { height: 35, width: 222, borderRadius: 17.5, backgroundColor: 'rgba(255,255,255,0.13)', borderWidth: 1, borderColor: 'rgba(255,255,255,0.45)', justifyContent: 'center' }, ctaText: { position: 'absolute', left: 68, right: 32, top: 10.9, fontSize: 11, lineHeight: 13.2, fontWeight: '500', color: 'rgba(255,255,255,0.95)', includeFontPadding: false }, arrow: { position: 'absolute', right: 15, top: 11.5 },
});
