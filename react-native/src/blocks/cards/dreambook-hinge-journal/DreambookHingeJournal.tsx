import { BlurMask, Canvas, ClipOp, FilterMode, Group, Image as SkiaImage, LinearGradient, MipmapMode, Paint, PaintStyle, Path, Points, Rect, RoundedRect, Skia, TileMode, makeImageFromView, type SkImage, type SkPicture, rect, rrect } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useEffect, useMemo, useRef, useState } from 'react';
import { Platform, Pressable, StyleSheet, Text, useWindowDimensions, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, { type SharedValue, useAnimatedReaction, useAnimatedStyle, useDerivedValue, useFrameCallback, useReducedMotion, useSharedValue } from 'react-native-reanimated';
import Svg, { Path as SvgPath } from 'react-native-svg';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';

type Props = { autoPlay?: boolean; previewTime?: number };
type Pose = { openness: number; expansion: number; fullscreen: number; angle: number; lift: number; scale: number; header: number; calendar: number; closing: number };
type Frame = { x: number; y: number; width: number; height: number };
type Matrix16 = [number, number, number, number, number, number, number, number, number, number, number, number, number, number, number, number];
export const LOOP = 8;
const WIDTH = 393;
const HEIGHT = 852;
const SCREEN = '#2a2629';
const SERIF = Platform.OS === 'ios' ? 'ui-serif' : 'serif';
const REST: Pose = { openness: 0, expansion: 0, fullscreen: 0, angle: 0, lift: 0, scale: 1, header: 1, calendar: 0, closing: 0 };

function seconds() { 'worklet'; return Date.now() / 1000; }
function clamp(value: number) { 'worklet'; return Math.min(1, Math.max(0, value)); }
function ease(value: number) { 'worklet'; const u = clamp(value); return u * u * (3 - 2 * u); }
function mix(a: number, b: number, p: number) { 'worklet'; return a + (b - a) * p; }
function hash(value: number) { 'worklet'; const n = Math.sin(value * 12.9898) * 43758.5453; return n - Math.floor(n); }
function manualPose(openness: number, expansion: number, fullscreen: number, extraAngle = 0): Pose {
  'worklet';
  const e = clamp(expansion); const q = clamp(fullscreen);
  return { openness, expansion: e, fullscreen: q, angle: extraAngle, lift: 0, scale: 1, header: 1 - ease((e - 0.05) / 0.6), calendar: ease((e - 0.05) / 0.6), closing: 0 };
}
function readingPose(): Pose { 'worklet'; return manualPose(1, 1, 0); }
function fullPose(): Pose { 'worklet'; return manualPose(1, 1, 1); }
function reducedPose(): Pose { 'worklet'; return { ...readingPose(), header: 1, calendar: 0 }; }
function pageFrame(pose: Pose): Frame {
  'worklet';
  const e = clamp(pose.expansion); const q = clamp(pose.fullscreen);
  return { x: mix(mix(68.41, 10.19, e), 0, q), y: mix(mix(250.36, 189.22, e), 52, q), width: mix(mix(254.72, 372.62, e), 393, q), height: mix(mix(356.61, 481.79, e), 800, q) };
}
function blend(a: Pose, b: Pose, p: number): Pose {
  'worklet';
  return { openness: mix(a.openness, b.openness, p), expansion: mix(a.expansion, b.expansion, p), fullscreen: mix(a.fullscreen, b.fullscreen, p), angle: mix(a.angle, b.angle, p), lift: mix(a.lift, b.lift, p), scale: mix(a.scale, b.scale, p), header: mix(a.header, b.header, p), calendar: mix(a.calendar, b.calendar, p), closing: mix(a.closing, b.closing, p) };
}
function transition(from: Pose, to: Pose, age: number, velocity: number): Pose {
  'worklet';
  const opening = to.openness > from.openness + 0.1;
  const closing = to.openness < from.openness - 0.1;
  const oAge = closing ? Math.max(0, age - 0.14) : age;
  const spring = oAge >= 0.65 ? 1 : 1 - Math.exp(-9 * oAge) * (Math.cos(15 * oAge) + 0.6 * Math.sin(15 * oAge));
  const openness = mix(from.openness, to.openness, spring) + velocity * oAge * Math.exp(-9 * oAge);
  const e = mix(from.expansion, to.expansion, ease((age - (opening ? 0.30 : 0)) / 0.55));
  const q = mix(from.fullscreen, to.fullscreen, ease((age - (opening ? 0.72 : 0)) / 0.50));
  let pose = manualPose(openness, e, q);
  pose.closing = mix(from.closing, closing ? 1 : opening ? 0 : to.closing, ease(age / 0.18));
  const settle = ease(age / 0.22);
  pose.angle = mix(from.angle, pose.angle, settle); pose.lift = mix(from.lift, pose.lift, settle); pose.scale = mix(from.scale, pose.scale, settle);
  pose.header = mix(from.header, pose.header, ease(age / 0.18)); pose.calendar = mix(from.calendar, pose.calendar, ease(age / 0.18));
  if (!opening && !closing) pose = blend(from, to, ease(age / 0.5));
  return pose;
}
function loopPose(raw: number): Pose {
  'worklet';
  const t = (raw % LOOP + LOOP) % LOOP;
  const o = clamp((t - 1.2) / (2 / 3)) * (1 - clamp((t - 6.05) / (2 / 3)));
  const e = ease((o - 0.45) / 0.55);
  const q = ease((t - 3.45) / 0.55) * (1 - ease((t - 4.60) / 0.55));
  const pose = manualPose(o, e, q);
  pose.closing = t >= 6.05 && t < 6.05 + 2 / 3 ? 1 : 0;
  pose.header = Math.min(1, 1 - ease((t - 1.20) / 0.35) + ease((t - 6.35) / 0.40));
  pose.calendar = ease((t - 1.45) / 0.40) * (1 - ease((t - 5.90) / 0.30));
  return pose;
}
function rubberBand(raw: number) {
  'worklet';
  if (raw < 0) return raw / (1 + Math.abs(raw) / 0.2) * 0.25;
  if (raw > 1) { const over = raw - 1; return 1 + over / (1 + over / 0.2) * 0.25; }
  return raw;
}
const REVERSE_DEPTH: number[][] = [[0.4,0],[0.65,0],[0.66666667,12],[0.7,36],[0.73333333,38],[0.8,32],[0.86666667,14],[0.93333333,3],[1.06666667,3]];
const OPENING: number[][] = [[0.4,318.77,606.97,318.77,250.36,320.22,250.36,323.13,250.36,323.13,606.97,320.22,606.97],[0.46666667,314.4,620.07,320.22,260.54,327.5,264.91,331.87,269.28,324.59,615.7,320.22,618.61],[0.53333333,253.27,621.52,295.48,267.82,308.58,282.38,353.7,314.4,311.49,620.07,279.47,636.08],[0.6,142.64,644.81,251.81,276.56,279.47,301.3,382.81,339.14,273.64,669.56,171.76,637.53],[0.66666667,24.74,582.22,196.5,278.01,235.8,298.39,423.57,400.28,234.34,703.03,66.96,605.51],[0.73333333,-32.02,490.52,221.24,243.08,251.81,302.76,435.21,480.33,181.94,727.78,-1.46,550.2],[0.8,-69.87,375.53,231.43,197.96,301.3,339.14,423.57,558.93,122.27,736.51,0.0,516.72],[0.86666667,-65.5,275.1,276.56,158.66,347.88,372.62,413.38,598.23,71.32,714.68,5.82,489.07],[0.93333333,-16.01,216.88,333.32,163.02,358.07,397.37,398.82,636.08,36.39,694.3,10.19,455.59],[1.0,-4.37,197.96,366.8,179.03,369.71,420.66,391.54,665.19,18.92,684.11,16.01,439.58],[1.06666667,10.19,189.22,382.81,189.22,372.62,427.93,382.81,671.01,10.19,671.01,18.92,427.93]];
const CLOSING: number[][] = [[5.3,10.19,189.22,382.81,189.22,372.62,427.93,382.81,671.01,10.19,671.01,18.92,427.93],[5.4,-17.47,267.82,368.26,237.26,368.26,420.66,391.54,660.82,20.38,682.66,16.01,443.94],[5.46666667,-29.11,369.71,355.16,289.66,340.6,385.72,401.73,618.61,49.49,694.3,5.82,471.6],[5.53333333,-13.1,477.42,330.41,342.06,317.31,362.43,410.47,576.4,82.97,707.4,0.0,499.26],[5.6,23.29,592.41,328.96,359.52,334.78,393.0,416.29,483.24,135.37,707.4,55.31,611.33],[5.66666667,123.72,665.19,353.7,365.34,360.98,387.18,397.37,411.92,183.4,700.12,142.64,666.64],[5.8,315.86,606.97,317.31,250.36,318.77,250.36,323.13,250.36,323.13,606.97,318.77,606.97],[5.96666667,318.77,606.97,318.77,250.36,320.22,250.36,323.13,250.36,323.13,606.97,320.22,606.97]];
const RETURN_COVER: number[][] = [[5.5666667,283.8,315.9,324.6,334.8,0.0,524.0,-42.2,505.1],[5.6,257.6,296.9,331.9,353.7,16.0,595.3,-40.8,522.5],[5.6666667,206.7,266.4,362.4,362.4,120.8,673.9,-16.0,542.9],[5.7333333,154.3,247.4,377.0,337.7,221.2,679.7,5.8,573.5],[5.8,112.1,246.0,362.4,302.8,279.5,650.6,32.0,592.4],[5.9,77.1,250.4,329.0,260.5,312.9,615.7,61.1,604.1],[5.9666667,68.4,251.8,323.1,251.8,323.1,607.0,68.4,607.0]];
const FRONT_COVER: number[][] = [[0.4,68.4,251.8,323.1,251.8,323.1,607.0,68.4,607.0],[0.4667,78.6,251.8,323.1,251.8,308.6,624.4,64.0,608.4],[0.5333,103.3,253.3,299.8,262.0,248.9,647.7,53.9,608.4],[0.6,144.1,259.1,257.6,267.8,141.2,652.1,40.8,604.1],[0.6333,170.3,264.9,228.5,266.4,75.7,636.1,32.0,596.8]];
type Point = { x: number; y: number };
type Mesh = { paper: Point[]; cover: Point[]; reverse: Point[]; paperAlpha: number; coverAlpha: number; reverseAlpha: number; ink: number };
type Matrix9 = [number, number, number, number, number, number, number, number, number];
const IDENTITY: Matrix9 = [1, 0, 0, 0, 1, 0, 0, 0, 1];
function tangent(rows: number[][], i: number, column: number) {
  'worklet';
  if (i === 0) return (rows[1][column] - rows[0][column]) / (rows[1][0] - rows[0][0]);
  if (i === rows.length - 1) return (rows[i][column] - rows[i - 1][column]) / (rows[i][0] - rows[i - 1][0]);
  const h0 = rows[i][0] - rows[i - 1][0]; const h1 = rows[i + 1][0] - rows[i][0];
  const d0 = (rows[i][column] - rows[i - 1][column]) / h0; const d1 = (rows[i + 1][column] - rows[i][column]) / h1;
  if (d0 * d1 <= 0) return 0;
  const w0 = 2 * h1 + h0; const w1 = h1 + 2 * h0;
  return (w0 + w1) / (w0 / d0 + w1 / d1);
}
function sample(rows: number[][], t: number): number[] {
  'worklet';
  if (t <= rows[0][0]) return rows[0].slice(1);
  if (t >= rows[rows.length - 1][0]) return rows[rows.length - 1].slice(1);
  let i = 0; while (i + 1 < rows.length && rows[i + 1][0] < t) i++;
  const a = rows[i]; const b = rows[i + 1]; const dt = b[0] - a[0]; const u = (t - a[0]) / dt; const u2 = u * u; const u3 = u2 * u;
  return a.slice(1).map((_, j) => {
    const c = j + 1;
    const v = (2 * u3 - 3 * u2 + 1) * a[c] + (u3 - 2 * u2 + u) * dt * tangent(rows, i, c) + (-2 * u3 + 3 * u2) * b[c] + (u3 - u2) * dt * tangent(rows, i + 1, c);
    return Math.min(Math.max(a[c], b[c]), Math.max(Math.min(a[c], b[c]), v));
  });
}
function transformPoint(p: Point, pose: Pose): Point {
  'worklet';
  const c = Math.cos(pose.angle * Math.PI / 180) * pose.scale; const s = Math.sin(pose.angle * Math.PI / 180) * pose.scale;
  return { x: 196.5 + c * (p.x - 196.5) - s * (p.y - 429), y: 429 + s * (p.x - 196.5) + c * (p.y - 429) + pose.lift };
}
function makeMesh(pose: Pose): Mesh {
  'worklet';
  const o = clamp(pose.openness); const q = clamp(pose.fullscreen); const closing = clamp(pose.closing);
  const source = 0.4 + o * (2 / 3); const returned = 5.3 + (1 - o) * (2 / 3); const f = pageFrame(pose);
  const opening = sample(OPENING, source); const returning = sample(CLOSING, returned);
  const flat = [f.x, f.y, f.x + f.width, f.y, f.x + f.width, f.y + f.height / 2, f.x + f.width, f.y + f.height, f.x, f.y + f.height, f.x, f.y + f.height / 2];
  const paperCoordinates = opening.map((v, i) => mix(mix(v, returning[i], closing), flat[i], ease(q / 0.12)));
  const front = sample(FRONT_COVER, source); const back = sample(RETURN_COVER, returned);
  const coverCoordinates = front.map((v, i) => mix(v, back[i], closing));
  const paper = Array.from({ length: 6 }, (_, i) => ({ x: paperCoordinates[i * 2], y: paperCoordinates[i * 2 + 1] }));
  const cover = Array.from({ length: 4 }, (_, i) => ({ x: coverCoordinates[i * 2], y: coverCoordinates[i * 2 + 1] }));
  const paperAlpha = ease((source - 0.42) / 0.06) * (1 - closing) + (1 - ease((returned - 5.73333333) / (1 / 30))) * closing;
  const coverAlpha = ((1 - ease((source - 0.63333333) / 0.02666667)) * (1 - closing) + ease((returned - 5.55) / (1 / 60)) * closing) * (1 - q);
  const ink = ease((source - 0.93333333) / (2 / 15)) * (1 - closing) + (1 - ease((returned - 5.3) / 0.1)) * closing;
  const edge = { x: paper[1].x - paper[0].x, y: paper[1].y - paper[0].y }; const length = Math.max(0.001, Math.hypot(edge.x, edge.y));
  const depth = sample(REVERSE_DEPTH, source)[0] * (1 - closing) + 3 * closing;
  const normal = { x: edge.y / length * depth, y: -edge.x / length * depth };
  const reverse = [{ x: paper[0].x + normal.x, y: paper[0].y + normal.y }, { x: paper[1].x + normal.x, y: paper[1].y + normal.y }, paper[1], paper[0]];
  return { paper: paper.map(p => transformPoint(p, pose)), cover: cover.map(p => transformPoint(p, pose)), reverse: reverse.map(p => transformPoint(p, pose)), paperAlpha, coverAlpha, reverseAlpha: paperAlpha * (1 - coverAlpha) * (1 - q), ink };
}
/** Skia uses row-major Matrix9 and preserves both projective denominator terms on Android. */
function projection(width: number, height: number, p: Point[]): Matrix9 {
  'worklet';
  const dx1 = p[1].x - p[2].x; const dx2 = p[3].x - p[2].x; const dy1 = p[1].y - p[2].y; const dy2 = p[3].y - p[2].y;
  const dx3 = p[0].x - p[1].x + p[2].x - p[3].x; const dy3 = p[0].y - p[1].y + p[2].y - p[3].y;
  const determinant = dx1 * dy2 - dx2 * dy1;
  let g = 0; let h = 0;
  if (Math.abs(determinant) > 0.000001) { g = (dx3 * dy2 - dx2 * dy3) / determinant; h = (dx1 * dy3 - dx3 * dy1) / determinant; }
  return [(p[1].x - p[0].x + g * p[1].x) / width, (p[3].x - p[0].x + h * p[3].x) / height, p[0].x, (p[1].y - p[0].y + g * p[1].y) / width, (p[3].y - p[0].y + h * p[3].y) / height, p[0].y, g / width, h / height, 1];
}
function polygonPath(points: Point[]) {
  'worklet';
  const b = Skia.PathBuilder.Make(); b.moveTo(points[0].x, points[0].y); for (let i = 1; i < points.length; i++) b.lineTo(points[i].x, points[i].y); return b.close().build();
}
function insidePolygon(x: number, y: number, points: Point[]) {
  'worklet';
  let inside = false;
  for (let i = 0, j = points.length - 1; i < points.length; j = i++) { const a = points[i]; const b = points[j]; if ((a.y > y) !== (b.y > y) && x < (b.x - a.x) * (y - a.y) / (b.y - a.y) + a.x) inside = !inside; }
  return inside;
}
function hitFrame(m: Mesh): Frame {
  'worklet';
  const points = [...(m.paperAlpha > 0.01 ? m.paper : []), ...(m.coverAlpha > 0.01 ? m.cover : [])];
  const x = Math.max(0, Math.min(...points.map(p => p.x))); const y = Math.max(0, Math.min(...points.map(p => p.y)));
  return { x, y, width: Math.max(1, Math.min(393, Math.max(...points.map(p => p.x))) - x), height: Math.max(1, Math.min(852, Math.max(...points.map(p => p.y))) - y) };
}

function makeCloth(): SkPicture {
  const recorder = Skia.PictureRecorder();
  const canvas = recorder.beginRecording(rect(0, 0, 253, 352));
  const pen = Skia.Paint(); pen.setAntiAlias(true); pen.setColor(Skia.Color('rgba(255,255,255,0.03)')); pen.setStrokeWidth(0.45);
  for (let index = 0; index < 240; index++) {
    const x = hash(index + 12) * 253; const y = hash(index + 39) * 352;
    canvas.drawLine(x, y, x + 1 + hash(index) * 4, y + 0.5, pen);
  }
  return recorder.finishRecordingAsPicture();
}
function haptic() { if (Platform.OS !== 'web') void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {}); }

function MoonStars() {
  return <Svg width={28} height={28} viewBox="0 0 24 24"><SvgPath d="M14.4 3.8A8.8 8.8 0 1 0 20.2 18a8 8 0 0 1-5.8-14.2Zm4-2.8 1.1 3.1 3.1 1.1-3.1 1.1-1.1 3.1-1.1-3.1-3.1-1.1 3.1-1.1Zm3 8.7.6 1.6 1.6.6-1.6.6-.6 1.6-.6-1.6-1.6-.6 1.6-.6Z" fill="#8f8b91" /></Svg>;
}

const ATLAS_WIDTH = 400;
const ATLAS_ROW = 44;
const ATLAS_HEIGHT = ATLAS_ROW * 11;
const PAGE_LINES = ['I found a quiet stairway above', 'the clouds. At the top, a door', 'was already open.', 'The door was already open. I stepped', 'into a room of little stars, and', 'every one of them felt familiar.'];
type Artwork = { typography: SkImage; front: SkImage; back: SkImage };
const COVER_CLIP = Skia.Path.MakeFromSVGString('M2 0H236Q253 0 253 17V335Q253 352 236 352H2Q0 352 0 350V2Q0 0 2 0Z')!;

/** Capture native serif shaping once; no native fonts are looked up through Skia aliases. */
function TypographyAtlas({ onReady }: { onReady: (image: SkImage) => void }) {
  const ref = useRef<View>(null);
  const [laidOut, setLaidOut] = useState(false);
  useEffect(() => {
    if (!laidOut) return;
    let alive = true;
    const timer = setTimeout(() => { makeImageFromView(ref).then(image => { if (image && alive) onReady(image.makeNonTextureImage() ?? image); }).catch(() => {}); }, 250);
    return () => { alive = false; clearTimeout(timer); };
  }, [onReady, laidOut]);
  const rows = [
    { text: 'Night Atlas', size: 24, line: 29, width: 221, color: '#b8b4b9', centered: true, weight: '400' as const },
    { text: 'Notes from the night.', size: 12, line: 14.4, width: 221, color: '#99959b', centered: true, weight: '400' as const },
    { text: 'Thursday, 15 October', size: 12.12, line: 14.544, width: 400, color: '#666267', centered: false, weight: '500' as const },
    ...PAGE_LINES.map(text => ({ text, size: 15.24, line: 21.48, width: 400, color: '#514d52', centered: false, weight: '400' as const })),
    { text: 'Tap to write', size: 9, line: 10.8, width: 150, color: '#969197', centered: false, weight: '400' as const },
    { text: '1 / 1', size: 9, line: 10.8, width: 40, color: '#969197', centered: true, weight: '400' as const },
  ];
  return <View ref={ref} onLayout={() => { if (!laidOut) setLaidOut(true); }} collapsable={false} pointerEvents="none" accessibilityElementsHidden importantForAccessibility="no-hide-descendants" style={{ position: 'absolute', left: 0, top: 0, width: ATLAS_WIDTH, height: ATLAS_HEIGHT }}>
    {rows.map((row, index) => <Text key={index} numberOfLines={1} style={{ position: 'absolute', left: 0, top: index * ATLAS_ROW, width: row.width, height: ATLAS_ROW, fontFamily: SERIF, fontSize: row.size, lineHeight: row.line, fontWeight: row.weight, color: row.color, textAlign: row.centered ? 'center' : 'left', includeFontPadding: false, ...(index === 0 ? { textShadowColor: 'rgba(0,0,0,0.5)', textShadowOffset: { width: 0, height: 1 }, textShadowRadius: 0.3 } : {}) }}>{row.text}</Text>)}
  </View>;
}

function makeCover(typography: SkImage, cloth: SkPicture, back: boolean): SkImage {
  const surface = Skia.Surface.MakeOffscreen(759, 1056); if (!surface) throw new Error('Dreambook: artwork surface unavailable.');
  const c = surface.getCanvas(); c.clear(Skia.Color('transparent')); c.scale(3, 3);
  const fill = Skia.Paint(); fill.setAntiAlias(true);
  if (!back) c.clipPath(COVER_CLIP, ClipOp.Intersect, true);
  fill.setShader(Skia.Shader.MakeLinearGradient({ x: 0, y: 0 }, { x: 253, y: back ? 0 : 352 }, (back ? ['#514c52', '#413c43'] : ['#4a484b', '#484649', '#454346']).map(Skia.Color), back ? [0, 1] : [0, 0.5, 1], TileMode.Clamp));
  c.drawRect(rect(0, 0, 253, 352), fill); fill.setShader(null); c.drawPicture(cloth);
  fill.setColor(Skia.Color(back ? 'rgba(0,0,0,0.15)' : 'rgba(55,53,56,0.35)')); c.drawRect(rect(0, 0, 12, 352), fill);
  if (back) {
    fill.setStyle(PaintStyle.Stroke); fill.setStrokeWidth(1); fill.setColor(Skia.Color('rgba(255,255,255,0.035)')); c.drawRRect(rrect(rect(13, 13, 227, 326), 4, 4), fill);
  } else {
    fill.setColor(Skia.Color('rgba(255,255,255,0.035)')); c.drawRect(rect(10, 1, 1.2, 350), fill);
    const ratio = typography.width() / ATLAS_WIDTH;
    c.drawImageRectOptions(typography, rect(0, 0, 221 * ratio, ATLAS_ROW * ratio), rect(16, 58.5, 221, ATLAS_ROW), FilterMode.Linear, MipmapMode.None, Skia.Paint());
    c.drawImageRectOptions(typography, rect(0, ATLAS_ROW * ratio, 221 * ratio, ATLAS_ROW * ratio), rect(16, 91.8, 221, ATLAS_ROW), FilterMode.Linear, MipmapMode.None, Skia.Paint());
    const moon = Skia.Path.MakeFromSVGString('M14.4 3.8A8.8 8.8 0 1 0 20.2 18a8 8 0 0 1-5.8-14.2Zm4-2.8 1.1 3.1 3.1 1.1-3.1 1.1-1.1 3.1-1.1-3.1-3.1-1.1 3.1-1.1Zm3 8.7.6 1.6 1.6.6-1.6.6-.6 1.6-.6-1.6-1.6-.6 1.6-.6Z')!;
    c.save(); c.translate(112.5, 301); c.scale(28 / 24, 28 / 24); fill.setColor(Skia.Color('#8f8b91')); c.drawPath(moon, fill); c.restore();
    fill.setStyle(PaintStyle.Stroke); fill.setStrokeWidth(0.7); fill.setColor(Skia.Color('rgba(255,255,255,0.09)')); c.drawPath(COVER_CLIP, fill);
  }
  surface.flush(); const snapshot = surface.makeImageSnapshot(); return snapshot.makeNonTextureImage() ?? snapshot;
}

/** Each source row contains one native-shaped line at the exact q=.12 native-editor handoff font size. */
function PageInk({ pose, image, row, line = 0 }: { pose: SharedValue<Pose>; image: SkImage; row: number; line?: number }) {
  const transform = useDerivedValue(() => {
    const p = pose.get(); const f = pageFrame(p); const inset = mix(18, 24, p.expansion);
    let x = inset; let y: number; let factor = 1;
    if (row === 2) { const size = mix(mix(9, 12, p.expansion), 13, p.fullscreen); factor = size / 12.12; y = mix(mix(23, 27, p.expansion), 25, p.fullscreen); }
    else if (row === 9) y = f.height - 34;
    else if (row === 10) { x = f.width - 52; y = f.height - 32.4; }
    else { const size = mix(mix(11, 15, p.expansion), 17, p.fullscreen); const height = mix(mix(15, 21, p.expansion), 25, p.fullscreen); factor = size / 15.24; y = mix(mix(54, 59, p.expansion), 62, p.fullscreen) - 2.5 + line * height + (height - 21.48 * factor) / 2; }
    return [{ translateX: x }, { translateY: y }, { scale: factor }];
  });
  const alpha = useDerivedValue(() => row >= 9 ? 1 - pose.get().fullscreen : 1);
  return <Group transform={transform} opacity={alpha} clip={rect(0, 0, ATLAS_WIDTH, ATLAS_ROW)}><SkiaImage image={image} x={0} y={-row * ATLAS_ROW} width={ATLAS_WIDTH} height={ATLAS_HEIGHT} sampling={{ filter: FilterMode.Linear, mipmap: MipmapMode.None }} /></Group>;
}

function ProjectedPaper({ pose, mesh, artwork, reflected, lower }: { pose: SharedValue<Pose>; mesh: SharedValue<Mesh>; artwork: Artwork; reflected: boolean; lower: boolean }) {
  const matrix = useDerivedValue(() => { const f = pageFrame(pose.get()); const p = mesh.get().paper; return projection(f.width, f.height / 2, lower ? [p[5], p[2], p[3], p[4]] : [p[0], p[1], p[2], p[5]]); });
  const panelClip = useDerivedValue(() => { const f = pageFrame(pose.get()); return rect(0, 0, f.width, f.height / 2); });
  const contentTransform = useDerivedValue(() => [{ translateY: lower ? -pageFrame(pose.get()).height / 2 : 0 }]);
  const alpha = useDerivedValue(() => pose.get().fullscreen < 0.12 ? mesh.get().paperAlpha : 0);
  const ink = useDerivedValue(() => mesh.get().ink);
  const bounds = useDerivedValue(() => { const f = pageFrame(pose.get()); return rrect(rect(0, 0, f.width, f.height), 17 + 7 * pose.get().fullscreen, 17 + 7 * pose.get().fullscreen); });
  const end = useDerivedValue(() => { const f = pageFrame(pose.get()); return { x: f.width, y: f.height }; });
  const points = useDerivedValue(() => { const f = pageFrame(pose.get()); return Array.from({ length: 90 }, (_, i) => ({ x: hash(i + 8) * f.width + 0.3, y: hash(i + 63) * f.height + 0.3 })); });
  const foldY = useDerivedValue(() => pageFrame(pose.get()).height / 2 - 60);
  const foldStart = useDerivedValue(() => ({ x: 0, y: foldY.get() })); const foldEnd = useDerivedValue(() => ({ x: 0, y: foldY.get() + 120 }));
  const width = useDerivedValue(() => pageFrame(pose.get()).width);
  const foldAlpha = useDerivedValue(() => 1 - pose.get().fullscreen);
  const foldColors = useDerivedValue(() => { const strength = 1 + 0.12 * Math.sin(Math.PI * clamp(pose.get().openness)); return ['rgba(0,0,0,0)', `rgba(0,0,0,${(0.025 * strength).toFixed(6)})`, `rgba(0,0,0,${(0.13 * strength).toFixed(6)})`, `rgba(255,255,255,${(0.025 * strength).toFixed(6)})`, 'rgba(255,255,255,0)']; });
  return <Group matrix={matrix} clip={panelClip} layer={<Paint opacity={alpha} />}><Group transform={contentTransform} clip={bounds}>
    <RoundedRect dither rect={bounds}><LinearGradient start={{ x: 0, y: 0 }} end={end} colors={['#c7c4c7', '#bdbabd', '#c6c3c6']} /></RoundedRect>
    <Points points={points} mode="points" strokeWidth={0.6} strokeCap="round" color="rgba(117,111,119,0.025)" />
    <Group opacity={ink}><PageInk pose={pose} image={artwork.typography} row={2} />{[0, 1, 2].map(i => <PageInk key={i} pose={pose} image={artwork.typography} row={(reflected ? 6 : 3) + i} line={i} />)}</Group>
    <Rect dither x={0} y={foldY} width={width} height={120} opacity={foldAlpha}><LinearGradient start={foldStart} end={foldEnd} colors={foldColors} positions={[0, 0.30, 0.49, 0.56, 1]} /></Rect>
    <Group opacity={ink}><PageInk pose={pose} image={artwork.typography} row={9} /><PageInk pose={pose} image={artwork.typography} row={10} /></Group>
    <RoundedRect rect={bounds} style="stroke" strokeWidth={0.65} color="rgba(255,255,255,0.35)" />
  </Group></Group>;
}

function ProjectedBook({ pose, mesh, artwork, reflected }: { pose: SharedValue<Pose>; mesh: SharedValue<Mesh>; artwork: Artwork; reflected: boolean }) {
  const paper = useDerivedValue(() => polygonPath(mesh.get().paper)); const cover = useDerivedValue(() => polygonPath(mesh.get().cover));
  const paperAlpha = useDerivedValue(() => mesh.get().paperAlpha); const coverAlpha = useDerivedValue(() => mesh.get().coverAlpha);
  const reverseAlpha = useDerivedValue(() => mesh.get().reverseAlpha);
  const reverseMatrix = useDerivedValue(() => { const m = mesh.get(); return m.reverseAlpha > 0.001 ? projection(253, 352, m.reverse) : IDENTITY; });
  const coverMatrix = useDerivedValue(() => projection(253, 352, mesh.get().cover));
  const edgeAlpha = useDerivedValue(() => (1 - pose.get().fullscreen) * mesh.get().paperAlpha);
  const crease = useDerivedValue(() => { const p = mesh.get().paper; return Skia.PathBuilder.Make().moveTo(p[5].x, p[5].y).lineTo(p[2].x, p[2].y).build(); });
  const creaseWidth = useDerivedValue(() => 0.6 + 1.8 * Math.sin(Math.PI * clamp(pose.get().openness)));
  const creaseColor = useDerivedValue(() => `rgba(142,137,144,${((0.16 + 0.22 * Math.sin(Math.PI * clamp(pose.get().openness))) * mesh.get().paperAlpha * (1 - pose.get().fullscreen)).toFixed(6)})`);
  const sheen = useDerivedValue(() => [{ translateX: 74 + 180 * pose.get().openness - 68 }]);
  const sheenAlpha = useDerivedValue(() => Math.max(0, Math.sin(Math.PI * pose.get().openness)));
  return <Canvas pointerEvents="none" colorSpace="srgb" style={StyleSheet.absoluteFill}>
    <Group opacity={paperAlpha} transform={[{ translateY: 16 }]}><Path path={paper} color="rgba(0,0,0,0.20)"><BlurMask blur={26} style="normal" /></Path></Group>
    <Group opacity={paperAlpha} transform={[{ translateY: 2 }]}><Path path={paper} color="rgba(0,0,0,0.22)"><BlurMask blur={3} style="normal" /></Path></Group>
    <Group opacity={edgeAlpha}>{[5, 4, 3, 2, 1].map(i => <Group key={i} transform={[{ translateX: i * 0.38 }, { translateY: i * 0.45 }]}><Path path={paper} color={i % 2 === 0 ? '#68656a' : '#79767a'} /></Group>)}</Group>
    <Group opacity={coverAlpha} transform={[{ translateY: 12 }]}><Path path={cover} color="rgba(0,0,0,0.25)"><BlurMask blur={20} style="normal" /></Path></Group>
    <Group matrix={reverseMatrix} opacity={reverseAlpha}><SkiaImage image={artwork.back} x={0} y={0} width={253} height={352} sampling={{ filter: FilterMode.Linear, mipmap: MipmapMode.None }} /></Group>
    <ProjectedPaper pose={pose} mesh={mesh} artwork={artwork} reflected={reflected} lower={false} /><ProjectedPaper pose={pose} mesh={mesh} artwork={artwork} reflected={reflected} lower />
    <Path path={crease} style="stroke" strokeWidth={creaseWidth} color={creaseColor} />
    <Group matrix={coverMatrix} layer={<Paint opacity={coverAlpha} />} clip={COVER_CLIP}>
      <SkiaImage image={artwork.front} x={0} y={0} width={253} height={352} sampling={{ filter: FilterMode.Linear, mipmap: MipmapMode.None }} />
      <Group transform={sheen} opacity={sheenAlpha}><Rect dither x={0} y={0} width={105} height={352}><LinearGradient start={{ x: 0, y: 0 }} end={{ x: 105, y: 0 }} colors={['rgba(255,255,255,0)', 'rgba(255,255,255,0.10)', 'rgba(255,255,255,0)']} /></Rect></Group>
    </Group>
  </Canvas>;
}


function JournalPage({ pose, visible, reflected }: { pose: SharedValue<Pose>; visible: SharedValue<Pose>; reflected: boolean }) {
  const style = useAnimatedStyle(() => {
    const p = pose.get(); const f = pageFrame(p);
    const c = Math.cos(p.angle * Math.PI / 180) * p.scale; const sn = Math.sin(p.angle * Math.PI / 180) * p.scale;
    const tx = 196.5 + c * (f.x - 196.5) - sn * (f.y - 429); const ty = 429 + sn * (f.x - 196.5) + c * (f.y - 429) + p.lift;
    return { left: 0, top: 0, width: f.width, height: f.height, borderRadius: 17 + 7 * p.fullscreen, opacity: visible.get().fullscreen >= 0.12 ? 1 : 0, transformOrigin: 'top left', transform: [{ matrix: [c, sn, 0, 0, -sn, c, 0, 0, 0, 0, 1, 0, tx, ty, 0, 1] as Matrix16 }] };
  });
  const bounds = useDerivedValue(() => { const f = pageFrame(pose.get()); return rect(0, 0, f.width, f.height); });
  const border = useAnimatedStyle(() => ({ borderRadius: 17 + 7 * pose.get().fullscreen }));
  const gradientEnd = useDerivedValue(() => { const f = pageFrame(pose.get()); return { x: f.width, y: f.height }; });
  const rules = useDerivedValue(() => {
    const p = pose.get(); const f = pageFrame(p); const e = p.expansion;
    const inset = mix(18, 24, e); const start = mix(140, 190, e);
    const builder = Skia.PathBuilder.Make();
    for (let index = 0; index < 8; index++) { const y = start + index * 104; if (y < f.height - 30) builder.moveTo(inset, y).lineTo(f.width - inset, y); }
    return builder.build();
  });
  const rulesColor = useDerivedValue(() => `rgba(168,164,169,${(0.18 * pose.get().fullscreen).toFixed(6)})`);
  const flecks = useDerivedValue(() => { const f = pageFrame(pose.get()); return Array.from({ length: 90 }, (_, index) => ({ x: hash(index + 8) * f.width + 0.3, y: hash(index + 63) * f.height + 0.3 })); });
  const pageWidth = useDerivedValue(() => pageFrame(pose.get()).width);
  const foldY = useDerivedValue(() => pageFrame(pose.get()).height * 0.5 - 60);
  const foldStart = useDerivedValue(() => ({ x: 0, y: foldY.get() }));
  const foldEnd = useDerivedValue(() => ({ x: 0, y: foldY.get() + 120 }));
  const foldAlpha = useDerivedValue(() => 1 - pose.get().fullscreen);
  const foldColors = useDerivedValue(() => { const strength = 1 + 0.12 * Math.sin(Math.PI * clamp(pose.get().openness)); return ['rgba(0,0,0,0)', `rgba(0,0,0,${(0.025 * strength).toFixed(6)})`, `rgba(0,0,0,${(0.13 * strength).toFixed(6)})`, `rgba(255,255,255,${(0.025 * strength).toFixed(6)})`, 'rgba(255,255,255,0)']; });
  const creaseY = useDerivedValue(() => pageFrame(pose.get()).height / 2 - 0.3);
  const creaseColor = useDerivedValue(() => `rgba(142,137,144,${(0.16 * (1 - pose.get().fullscreen)).toFixed(6)})`);
  const date = useAnimatedStyle(() => {
    const p = pose.get(); const f = pageFrame(p); const inset = mix(18, 24, p.expansion); const size = mix(mix(9, 12, p.expansion), 13, p.fullscreen);
    return { left: inset, top: mix(mix(23, 27, p.expansion), 25, p.fullscreen), width: f.width - 2 * inset, height: size * 1.5, fontSize: size, lineHeight: size * 1.2 };
  });
  const body = useAnimatedStyle(() => {
    const p = pose.get(); const f = pageFrame(p); const inset = mix(18, 24, p.expansion); const size = mix(mix(11, 15, p.expansion), 17, p.fullscreen); const lineHeight = mix(mix(15, 21, p.expansion), 25, p.fullscreen);
    return { left: inset, top: mix(mix(54, 59, p.expansion), 62, p.fullscreen) - 2.5, width: f.width - 2 * inset, height: lineHeight * 4, fontSize: size, lineHeight };
  });
  const edit = useAnimatedStyle(() => { const p = pose.get(); const f = pageFrame(p); return { left: mix(18, 24, p.expansion), top: f.height - 34, opacity: 1 - p.fullscreen }; });
  const count = useAnimatedStyle(() => { const p = pose.get(); const f = pageFrame(p); return { left: f.width - 52, top: f.height - 32.4, opacity: 1 - p.fullscreen }; });
  return <Animated.View pointerEvents="none" style={[styles.page, style]} accessibilityElementsHidden importantForAccessibility="no-hide-descendants">
    <Canvas colorSpace="srgb" style={StyleSheet.absoluteFill}>
      <Rect dither rect={bounds}><LinearGradient start={{ x: 0, y: 0 }} end={gradientEnd} colors={['#c7c4c7', '#bdbabd', '#c6c3c6']} /></Rect>
      <Path path={rules} style="stroke" strokeWidth={0.5} color={rulesColor} />
      <Points points={flecks} mode="points" strokeWidth={0.6} strokeCap="round" color="rgba(117,111,119,0.025)" />
      <Rect dither x={0} y={foldY} width={pageWidth} height={120} opacity={foldAlpha}><LinearGradient start={foldStart} end={foldEnd} colors={foldColors} positions={[0, 0.30, 0.49, 0.56, 1]} /></Rect>
      <Rect x={0} y={creaseY} width={pageWidth} height={0.6} color={creaseColor} />
    </Canvas>
    <Animated.Text style={[styles.pageDate, date]}>Thursday, 15 October</Animated.Text>
    <Animated.Text style={[styles.pageBody, body]}>{reflected ? 'The door was already open. I stepped\ninto a room of little stars, and\nevery one of them felt familiar.' : 'I found a quiet stairway above\nthe clouds. At the top, a door\nwas already open.'}</Animated.Text>
    <Animated.Text style={[styles.edit, edit]}>Tap to write</Animated.Text><Animated.Text style={[styles.pageCount, count]}>1 / 1</Animated.Text>
    <Animated.View style={[styles.pageBorder, border]} />
  </Animated.View>;
}

/** A clothbound dream journal that opens at its spine and becomes a writing page. */
export function DreambookHingeJournal({ autoPlay = false, previewTime }: Props) {
  const window = useWindowDimensions(); const [size, setSize] = useState({ width: window.width, height: window.height });
  const [started] = useState(seconds); const [manualLive, setManualLive] = useState(false); const [reflected, setReflected] = useState(false);
  const [flags, setFlags] = useState({ open: false, footer: true, close: false });
  const reduced = useReducedMotion();
  const clock = useSharedValue(started); const epoch = useSharedValue(started); const lastTouch = useSharedValue(-1);
  const transitionStarted = useSharedValue(-1); const transitionFrom = useSharedValue<Pose>(REST); const transitionTo = useSharedValue<Pose>(REST);
  const heldPose = useSharedValue<Pose>(REST); const dragStart = useSharedValue<Pose>(REST); const dragging = useSharedValue(false); const live = useSharedValue(false);
  const releaseVelocity = useSharedValue(0); const lastHaptic = useSharedValue(-1); const crossedOpen = useSharedValue(false); const reflection = useSharedValue(false);
  const absoluteStart = useSharedValue({ x: 0, y: 0 }); const delta = useSharedValue({ x: 0, y: 0 });
  const cloth = useMemo(() => makeCloth(), []);
  const [typography, setTypography] = useState<SkImage | null>(null);
  const artwork = useMemo<Artwork | null>(() => typography ? { typography, front: makeCover(typography, cloth, false), back: makeCover(typography, cloth, true) } : null, [typography, cloth]);
  useEffect(() => { if (artwork) scheduleOnUI(() => { 'worklet'; const now = seconds(); clock.set(now); epoch.set(now); }); }, [artwork, clock, epoch]);
  const scale = Math.min(size.width / WIDTH, size.height / HEIGHT);
  const pose = useDerivedValue<Pose>(() => {
    if (previewTime !== undefined) return reduced ? reducedPose() : loopPose(previewTime);
    if (reduced) return reducedPose();
    if (dragging.get()) return heldPose.get();
    const touchAge = Math.max(0, clock.get() - lastTouch.get());
    if (lastTouch.get() >= 0 && touchAge < 2.6) {
      const age = Math.max(0, clock.get() - transitionStarted.get());
      return blend(transition(transitionFrom.get(), transitionTo.get(), age, releaseVelocity.get()), { ...REST, closing: 1 }, ease((touchAge - 2) / 0.6));
    }
    if (!autoPlay) return REST;
    return loopPose(lastTouch.get() < 0 ? clock.get() - epoch.get() : touchAge - 2.6);
  });
  const mesh = useDerivedValue(() => makeMesh(pose.get()));
  const nativePose = useDerivedValue(() => pose.get().fullscreen >= 0.12 ? pose.get() : readingPose());
  const frames = useFrameCallback(() => {
    const now = seconds(); clock.set(now);
    if (live.get() && !dragging.get() && now - lastTouch.get() >= 2.65) { live.set(false); scheduleOnRN(setManualLive, false); }
  }, false);
  useEffect(() => { frames.setActive(!!artwork && !reduced && previewTime === undefined && (autoPlay || manualLive)); return () => frames.setActive(false); }, [autoPlay, reduced, previewTime, manualLive, frames, artwork]);
  useAnimatedReaction(() => { const p = pose.get(); return (p.openness >= 0.5 ? 1 : 0) + (p.header > 0.45 ? 2 : 0) + (p.fullscreen > 0.4 ? 4 : 0); }, (value, previous) => {
    if (value !== previous) scheduleOnRN(setFlags, { open: (value & 1) !== 0, footer: (value & 2) !== 0, close: (value & 4) !== 0 });
  });
  const tick = () => {
    'worklet';
    const now = seconds(); if (now - lastHaptic.get() <= 0.16) return; lastHaptic.set(now); scheduleOnRN(haptic);
  };
  const animate = (target: Pose, supplied?: Pose, preserveVelocity = false) => {
    'worklet';
    const now = seconds(); transitionFrom.set(supplied ?? pose.get()); transitionTo.set(target); transitionStarted.set(now); lastTouch.set(now); clock.set(now); live.set(true);
    if (!preserveVelocity) releaseVelocity.set(0); scheduleOnRN(setManualLive, true); tick();
  };
  const toggle = (supplied?: Pose) => {
    'worklet';
    const shown = supplied ?? pose.get();
    if (reduced) { reflection.set(!reflection.get()); scheduleOnRN(setReflected, reflection.get()); animate(readingPose()); }
    else if (shown.openness < 0.5) animate(readingPose(), shown);
    else animate(shown.fullscreen > 0.5 ? readingPose() : fullPose(), shown);
  };
  const updateDrag = (dx: number, dy: number) => {
    'worklet';
    delta.set({ x: dx, y: dy }); const now = seconds();
    if (!reduced) {
      const start = dragStart.get(); const openness = rubberBand(start.openness - dx / 180); const activation = ease(Math.abs(dx) / 24); const expanded = ease((openness - 0.66) / 0.34);
      const held = manualPose(openness, start.expansion + (expanded - start.expansion) * activation, start.fullscreen * (1 - activation), Math.min(4, Math.max(-4, dy * 0.035)));
      held.closing = start.closing; held.header = mix(start.header, held.header, activation); held.calendar = mix(start.calendar, held.calendar, activation); held.angle = mix(start.angle, held.angle, activation); held.lift = mix(start.lift, held.lift, activation); held.scale = mix(start.scale, 1, activation); heldPose.set(held);
      if (openness > 0.86 && !crossedOpen.get()) { crossedOpen.set(true); tick(); }
      if (openness < 0.08 && crossedOpen.get()) { crossedOpen.set(false); tick(); }
    }
    lastTouch.set(now); clock.set(now); live.set(true);
  };
  const pan = Gesture.Pan().minDistance(0).manualActivation(true)
    .onTouchesDown((event, manager) => {
      const touch = event.allTouches[0]; if (!touch) { manager.fail(); return; }
      const x = (touch.absoluteX - (size.width - WIDTH * scale) / 2) / scale; const y = (touch.absoluteY - (size.height - HEIGHT * scale) / 2) / scale;
      const m = mesh.get(); const inside = (m.paperAlpha > 0.01 && insidePolygon(x, y, m.paper)) || (m.coverAlpha > 0.01 && insidePolygon(x, y, m.cover));
      if (!inside) { manager.fail(); return; }
      const shown = pose.get(); dragStart.set(shown); heldPose.set(shown); crossedOpen.set(shown.openness > 0.86); dragging.set(true); absoluteStart.set({ x: touch.absoluteX, y: touch.absoluteY });
      updateDrag(0, 0); scheduleOnRN(setManualLive, true); manager.activate();
    })
    .onUpdate((event) => updateDrag((event.absoluteX - absoluteStart.get().x) / scale, (event.absoluteY - absoluteStart.get().y) / scale))
    .onEnd((event) => {
      dragging.set(false); const distance = Math.hypot(delta.get().x, delta.get().y);
      if (distance < 6) { toggle(heldPose.get()); return; }
      const velocity = event.velocityX / scale; const projected = heldPose.get().openness - velocity * 0.0008; releaseVelocity.set(Math.min(2, Math.max(-2, -velocity / 180)));
      animate(projected > 0.5 ? readingPose() : REST, heldPose.get(), true);
    })
    .onFinalize((_event, success) => { if (!success && dragging.get()) { dragging.set(false); animate(readingPose(), heldPose.get()); } });
  const headerStyle = useAnimatedStyle(() => ({ opacity: 1 - pose.get().fullscreen }));
  const introStyle = useAnimatedStyle(() => ({ opacity: pose.get().header, transform: [{ translateY: 120 * (1 - pose.get().header) }] }));
  const titleStyle = useAnimatedStyle(() => ({ opacity: ease((pose.get().header - 0.45) / 0.55) }));
  const footerHitStyle = useAnimatedStyle(() => ({ transform: [{ translateY: 120 * (1 - pose.get().header) }] }));
  const readingTitleStyle = useAnimatedStyle(() => ({ opacity: 1 - ease(pose.get().header / 0.55) }));
  const calendarStyle = useAnimatedStyle(() => ({ opacity: pose.get().calendar * (1 - pose.get().fullscreen) }));
  const closeStyle = useAnimatedStyle(() => ({ opacity: pose.get().fullscreen }));
  const hitStyle = useAnimatedStyle(() => { const f = hitFrame(mesh.get()); return { left: f.x, top: f.y, width: f.width, height: f.height }; });
  const write = () => { 'worklet'; if (reduced) { reflection.set(!reflection.get()); scheduleOnRN(setReflected, reflection.get()); } animate(fullPose()); };
  const reflect = () => { 'worklet'; reflection.set(!reflection.get()); scheduleOnRN(setReflected, reflection.get()); animate(fullPose()); };

  return <View style={styles.screen} onLayout={(event) => { const { width, height } = event.nativeEvent.layout; setSize((before) => before.width === width && before.height === height ? before : { width, height }); }}>
    <TypographyAtlas onReady={setTypography} />
    <View pointerEvents="none" style={[StyleSheet.absoluteFill, { backgroundColor: SCREEN }]} />
    <StatusBar style="light" />
    <View style={[styles.design, { left: (size.width - WIDTH * scale) / 2, top: (size.height - HEIGHT * scale) / 2, transform: [{ scale }] }]}>
      <Canvas pointerEvents="none" style={StyleSheet.absoluteFill}>
        <Rect dither x={0} y={0} width={WIDTH} height={HEIGHT}><LinearGradient start={{ x: 0, y: 0 }} end={{ x: 0, y: HEIGHT }} colors={['rgba(36,34,37,0)', 'rgba(36,34,37,0)', 'rgba(36,34,37,0.65)']} /></Rect>
      </Canvas>
      <Animated.View pointerEvents="none" style={[styles.header, headerStyle]}>
        <Text style={styles.brand}>Thursday, 15 October</Text><Svg style={styles.settings} width={20} height={20} viewBox="0 0 24 24" fill="none"><SvgPath d="M2 5h12m6 0h2M2 12h3m6 0h11M2 19h10m6 0h4M14 3h6v4h-6ZM5 10h6v4H5Zm7 7h6v4h-6Z" stroke="#a5a1a5" strokeWidth={0.8} strokeLinecap="round" /></Svg>
        <Animated.Text style={[styles.heading, titleStyle]}>{'Give your dreams\na place to stay.'}</Animated.Text>
        <Animated.Text style={[styles.readingTitle, readingTitleStyle]}>Pages from the night</Animated.Text>
      </Animated.View>
      {artwork ? <ProjectedBook pose={pose} mesh={mesh} artwork={artwork} reflected={reflected} /> : <View pointerEvents="none" style={{ position: 'absolute', left: 68.4, top: 251.8, width: 254.7, height: 355.2, borderRadius: 17, backgroundColor: '#484649' }}><Text style={styles.coverTitle}>Night Atlas</Text><Text style={styles.coverSubtitle}>Notes from the night.</Text><View style={styles.coverMoon}><MoonStars /></View></View>}
      <JournalPage pose={nativePose} visible={pose} reflected={reflected} />
      <Animated.View pointerEvents="none" style={[styles.calendar, calendarStyle]}>
        <View style={styles.calendarHandle} /><Text style={styles.month}>October 2026 ›</Text>
        <Svg style={styles.calendarIcon} width={16} height={16} viewBox="0 0 16 16"><SvgPath d="M8 1a7 7 0 1 0 0 14A7 7 0 0 0 8 1Zm0 4a3 3 0 1 0 0 6 3 3 0 0 0 0-6Z" fill="#cdc9cd" fillRule="evenodd" /></Svg>
        <View style={styles.weekdays}>{['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'].map((day) => <Text key={day} style={styles.weekday}>{day}</Text>)}</View>
        <View style={styles.days}>{Array.from({ length: 7 }, (_, index) => <View key={index} style={styles.day}>{index === 3 && <View style={styles.chosenDay} />}<Text style={[styles.dayText, { color: index === 3 ? '#d8d4d8' : '#8c888d', fontWeight: index === 3 ? '500' : '400' }]}>{12 + index}</Text></View>)}</View>
      </Animated.View>
      <Animated.View pointerEvents="none" style={[StyleSheet.absoluteFill, introStyle]} accessibilityElementsHidden importantForAccessibility="no-hide-descendants">
        {!reduced && <Text style={styles.note}>{'Save a dream while it is still near.\nReturn when the day grows quiet.'}</Text>}
        <View style={styles.actions}><Canvas style={StyleSheet.absoluteFill}><RoundedRect dither x={0} y={0} width={333} height={94} r={47}><LinearGradient start={{ x: 0, y: 0 }} end={{ x: 0, y: 94 }} colors={['#858286', '#626064', '#4b484d']} /></RoundedRect><RoundedRect x={0.75} y={0.75} width={331.5} height={92.5} r={46.25} style="stroke" strokeWidth={1.5}><LinearGradient start={{ x: 0, y: 0 }} end={{ x: 0, y: 94 }} colors={['rgba(255,255,255,0.60)', 'rgba(255,255,255,0.04)']} /></RoundedRect></Canvas></View>
        <View style={styles.divider} /><View style={styles.dividerLight} />
        <View style={[styles.legend, { left: 30 }]}><Svg width={24} height={30} viewBox="0 0 16 16"><SvgPath d="m2.2 12.8 1-3.1 7.4-7.4 1.3 1.3-7.4 7.4Zm5.5.2H14" stroke="#dbd7dc" strokeWidth={0.55} fill="#dbd7dc" strokeLinecap="round" /></Svg><Text style={styles.legendText}>Write</Text></View>
        <View style={[styles.legend, { left: 196.5 }]}><Svg width={24} height={30} viewBox="0 0 16 16"><SvgPath d="M8 1c.5 4 2.5 6 7 7-4.5 1-6.5 3-7 7-.5-4-2.5-6-7-7 4.5-1 6.5-3 7-7Z" stroke="#dbd7dc" strokeWidth={0.5} fill="#dbd7dc" /></Svg><Text style={styles.legendText}>Reflect</Text></View>
      </Animated.View>
      <GestureDetector gesture={pan}><Animated.View style={[styles.pageHit, hitStyle]} accessible accessibilityRole="button" accessibilityLabel={flags.open ? 'Dream journal page' : 'Night Atlas journal'} accessibilityValue={{ text: flags.open ? 'Open' : 'Closed' }} accessibilityHint="Drag left to unfold the journal, or tap to look closer" onAccessibilityTap={() => scheduleOnUI(toggle)} /></GestureDetector>
      {flags.footer && <Animated.View style={[styles.footerHits, footerHitStyle]}><Pressable style={styles.footerHit} accessibilityRole="button" accessibilityLabel="Write" onPress={() => scheduleOnUI(write)} /><Pressable style={styles.footerHit} accessibilityRole="button" accessibilityLabel="Reflect" onPress={() => scheduleOnUI(reflect)} /></Animated.View>}
      {flags.close && <Animated.View style={[styles.closeHit, closeStyle]}><Pressable style={styles.closePress} accessibilityRole="button" accessibilityLabel="Close writing page" onPress={() => scheduleOnUI(animate, readingPose())}><View style={styles.closeDisc}><Svg width={11} height={11} viewBox="0 0 12 12"><SvgPath d="m2 2 8 8M10 2l-8 8" stroke="#777378" strokeWidth={0.9} strokeLinecap="round" /></Svg></View></Pressable></Animated.View>}
    </View>
  </View>;
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: SCREEN, overflow: 'hidden' }, design: { position: 'absolute', width: WIDTH, height: HEIGHT, transformOrigin: 'top left', overflow: 'hidden', backgroundColor: SCREEN },
  header: { position: 'absolute', width: WIDTH, height: 205 }, brand: { position: 'absolute', left: 36.5, top: 62.4, width: 320, fontFamily: SERIF, fontSize: 16, lineHeight: 19.2, color: '#a5a1a5', textAlign: 'center', includeFontPadding: false }, settings: { position: 'absolute', left: 355, top: 62 },
  heading: { position: 'absolute', left: 24, top: 104, width: 345, height: 76, fontFamily: SERIF, fontSize: 31, lineHeight: 37, color: '#dedcde', textAlign: 'center', includeFontPadding: false }, readingTitle: { position: 'absolute', left: 24, top: 105.4, width: 345, height: 40, fontFamily: SERIF, fontSize: 26, lineHeight: 31.2, color: '#dedcde', textAlign: 'center', includeFontPadding: false },
  calendar: { position: 'absolute', left: 10, top: 711, width: 373, height: 132, borderRadius: 27, backgroundColor: '#454246', borderWidth: 1, borderColor: 'rgba(255,255,255,0.045)' }, calendarHandle: { position: 'absolute', left: 165.5, top: 9, width: 42, height: 3, borderRadius: 1.5, backgroundColor: 'rgba(255,255,255,0.25)' }, calendarIcon: { position: 'absolute', left: 330, top: 26 }, month: { position: 'absolute', left: 21, top: 24, fontFamily: SERIF, fontSize: 15, lineHeight: 18, fontWeight: '500', color: '#cdc9cd', includeFontPadding: false }, weekdays: { position: 'absolute', left: 15, top: 63, flexDirection: 'row' }, weekday: { width: 49, height: 17, fontFamily: SERIF, fontSize: 10, lineHeight: 17, color: '#aba7ab', textAlign: 'center', includeFontPadding: false }, days: { position: 'absolute', left: 15, top: 82, flexDirection: 'row' }, day: { width: 49, height: 28, alignItems: 'center', justifyContent: 'center' }, chosenDay: { position: 'absolute', left: 11.5, top: 1, width: 26, height: 26, borderRadius: 13, backgroundColor: 'rgba(255,255,255,0.08)' }, dayText: { fontFamily: SERIF, fontSize: 14, lineHeight: 28, textAlign: 'center', includeFontPadding: false },
  paperBlock: { position: 'absolute', borderRadius: 7, backgroundColor: '#363337' }, backCover: { position: 'absolute', left: -3, top: -3, right: -3, bottom: -3, borderRadius: 8, backgroundColor: '#363337' },
  page: { position: 'absolute', overflow: 'hidden', backgroundColor: '#bdbabd' }, pageBorder: { ...StyleSheet.absoluteFill, borderWidth: 0.65, borderColor: 'rgba(255,255,255,0.35)' }, pageDate: { position: 'absolute', fontFamily: SERIF, fontWeight: '500', color: '#666267', includeFontPadding: false }, pageBody: { position: 'absolute', fontFamily: SERIF, color: '#514d52', includeFontPadding: false }, edit: { position: 'absolute', fontFamily: SERIF, fontSize: 9, lineHeight: 10.8, color: '#969197', includeFontPadding: false }, pageCount: { position: 'absolute', width: 40, fontFamily: SERIF, fontSize: 9, lineHeight: 10.8, textAlign: 'center', color: '#969197', includeFontPadding: false },
  coverProjection: { position: 'absolute', left: 0, top: 0, width: 253, height: 352, transformOrigin: 'top left', borderTopLeftRadius: 2, borderBottomLeftRadius: 2, borderTopRightRadius: 17, borderBottomRightRadius: 17, backgroundColor: '#484649' }, coverClip: { flex: 1, borderTopLeftRadius: 2, borderBottomLeftRadius: 2, borderTopRightRadius: 17, borderBottomRightRadius: 17, overflow: 'hidden' }, coverBorder: { ...StyleSheet.absoluteFill, borderTopLeftRadius: 2, borderBottomLeftRadius: 2, borderTopRightRadius: 17, borderBottomRightRadius: 17, borderWidth: 0.7 }, backInset: { position: 'absolute', left: 13, top: 13, right: 13, bottom: 13, borderRadius: 4, borderWidth: 1, borderColor: 'rgba(255,255,255,0.035)' },
  coverTitle: { position: 'absolute', left: 16, top: 58.5, width: 221, height: 40, fontFamily: SERIF, fontSize: 24, lineHeight: 29, color: '#b8b4b9', textAlign: 'center', includeFontPadding: false, textShadowColor: 'rgba(0,0,0,0.5)', textShadowOffset: { width: 0, height: 1 }, textShadowRadius: 0.3 }, coverSubtitle: { position: 'absolute', left: 16, top: 91.8, width: 221, fontFamily: SERIF, fontSize: 12, lineHeight: 14.4, textAlign: 'center', color: '#99959b', includeFontPadding: false }, coverMoon: { position: 'absolute', left: 112.5, top: 301 }, sheen: { position: 'absolute', left: 74, top: 0, width: 105, height: 352 },
  note: { position: 'absolute', left: 24, top: 660.4, width: 345, height: 48, fontFamily: SERIF, fontSize: 18, lineHeight: 22.6, color: '#b2aeb3', textAlign: 'center', includeFontPadding: false }, actions: { position: 'absolute', left: 30, top: 720, width: 333, height: 94, borderRadius: 47, boxShadow: '0 2px 3px rgba(0,0,0,0.16)' }, divider: { position: 'absolute', left: 196, top: 722, width: 1, height: 90, backgroundColor: 'rgba(0,0,0,0.22)' }, dividerLight: { position: 'absolute', left: 196.95, top: 723, width: 0.7, height: 88, backgroundColor: 'rgba(255,255,255,0.08)' }, legend: { position: 'absolute', top: 739.7, width: 166.5, height: 54.6, alignItems: 'center', gap: 3 }, legendText: { fontFamily: SERIF, fontSize: 18, lineHeight: 21.6, color: '#dbd7dc', includeFontPadding: false },
  pageHit: { position: 'absolute', backgroundColor: 'transparent' }, footerHits: { position: 'absolute', left: 30, top: 720, width: 333, height: 94, flexDirection: 'row' }, footerHit: { flex: 1 }, closeHit: { position: 'absolute', left: 333, top: 62, width: 44, height: 44 }, closePress: { flex: 1, alignItems: 'center', justifyContent: 'center' }, closeDisc: { width: 28, height: 28, borderRadius: 14, backgroundColor: 'rgba(0,0,0,0.04)', alignItems: 'center', justifyContent: 'center' },
});
