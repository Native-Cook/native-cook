import { useEffect, useMemo } from 'react';
import { Circle, Group, LinearGradient, Path, RadialGradient, Skia, type SkPath, type Transforms3d } from '@shopify/react-native-skia';
import { useDerivedValue, type SharedValue } from 'react-native-reanimated';
import { SIGNATURE, signatureContactInset, signatureGaze, signatureReflection } from './BuzzStreaksSignature';
import { actingAttentionTarget, actingBlink, actingGazeOffset, actingHappy, actingPivot, actingPose, actingTransformPoint } from './BuzzStreaksActing';

type Kind = 'orange' | 'pink';
type Body = {
  paths: number[][];
  primary: number[];
  secondary: Record<'facePatch' | 'frontArm' | 'tail', number[]>;
  whiteFace: Record<'leftEye' | 'rightEye' | 'mouth', number[]>;
};
type Geometry = { pointScale: number; colors: Record<Kind, number[]>; characters: Record<Kind, Body> };
type CharacterRig = {
  faceBaselineAngle: number;
  bodyGradient: number[][];
  key: number[][];
  face: number[][];
  arm: number[][];
};
type Rig = { characters: Record<Kind, CharacterRig> };
type KeyGradient = { start: number[]; end: number[]; colors: number[][] };
type KeyDrawing = {
  baseline_size: number[];
  outer: number[][];
  face: number[][];
  letters: { points: number[][]; hole: boolean }[];
  letterCurves: number[][];
  ink_rgb: number[];
  outer_gradient: KeyGradient;
  face_gradient: KeyGradient;
};
type Sparks = { fps: number; frameCount: number; fillRGB: number[]; frames: { points: number[][]; opacity: number }[][] };
type CachedSpark = { path: SkPath; opacity: number };

const GEOMETRY: Geometry = require('./assets/geometry.json');
const RIG: Rig = require('./assets/rig.json');
const KEY_DRAWINGS: { characters: Record<Kind, KeyDrawing> } = require('./assets/key-vectors.json');
const SPARKS: Sparks = require('./assets/sparks.json');
const DEGREES = Math.PI / 180;
const SMILES = {
  orange: Skia.Path.MakeFromSVGString('M -17 12 Q -10 21 -3 13')!,
  pink: Skia.Path.MakeFromSVGString('M -2 18 Q 5 23 10 16')!,
};
const CLOSED_LID = Skia.Path.MakeFromSVGString('M -8 0 Q 0 3 8 0')!;

function closedCubic(raw: number[], pointScale: number) {
  if (!raw.length) return [];
  const points = raw.map(value => value / pointScale), handles = new Array<number>(raw.length).fill(0);
  const count = points.length / 2;
  for (let i = 0; i < count; i++) {
    const current = i * 2, previous = ((i + count - 1) % count) * 2, next = ((i + 1) % count) * 2;
    const ix = points[current] - points[previous], iy = points[current + 1] - points[previous + 1];
    const ox = points[next] - points[current], oy = points[next + 1] - points[current + 1];
    const incoming = Math.hypot(ix, iy), outgoing = Math.hypot(ox, oy), edge = Math.min(incoming, outgoing);
    if (edge <= 1e-8 || (ix * ox + iy * oy) / (incoming * outgoing) < .42261826174069944) continue;
    let hx = (points[next] - points[previous]) / 6, hy = (points[next + 1] - points[previous + 1]) / 6;
    const length = Math.hypot(hx, hy), cap = .45 * edge;
    if (length > cap) { hx *= cap / length; hy *= cap / length; }
    handles[current] = hx; handles[current + 1] = hy;
  }
  const contour = [points[0], points[1]];
  for (let i = 0; i < count; i++) {
    const current = i * 2, next = ((i + 1) % count) * 2;
    contour.push(points[current] + handles[current], points[current + 1] + handles[current + 1],
      points[next] - handles[next], points[next + 1] - handles[next + 1], points[next], points[next + 1]);
  }
  return contour;
}

function cubicPath(contour: number[]) {
  const path = Skia.PathBuilder.Make();
  if (!contour.length) return path.build();
  path.moveTo(contour[0], contour[1]);
  for (let i = 2; i < contour.length; i += 6) path.cubicTo(contour[i], contour[i + 1], contour[i + 2], contour[i + 3], contour[i + 4], contour[i + 5]);
  return path.close().build();
}

function cachePaths(body: Body) {
  // Build the native path bank once per mounted character, never from a frame callback.
  const used = new Set([...body.primary, ...Object.values(body.secondary).flat()]);
  const empty = Skia.Path.Make();
  return body.paths.map((points, index) => {
    if (!used.has(index) || !points.length) return empty;
    // Every edge stays cubic, including zero-handle corners, so equal point counts stay compatible.
    return cubicPath(closedCubic(points, GEOMETRY.pointScale));
  });
}

function keyColor(rgb: number[]) {
  return new Float32Array([rgb[0] / 255, rgb[1] / 255, rgb[2] / 255, 1]);
}

function cacheKeyDrawing(drawing: KeyDrawing) {
  function plate(points: number[][]) {
    const path = Skia.PathBuilder.Make();
    if (!points.length) return path.build();
    const last = points[points.length - 1], first = points[0];
    path.moveTo((last[0] + first[0]) / 2, (last[1] + first[1]) / 2);
    for (let i = 0; i < points.length; i++) {
      const a = points[i], b = points[(i + 1) % points.length];
      path.quadTo(a[0], a[1], (a[0] + b[0]) / 2, (a[1] + b[1]) / 2);
    }
    return path.close().build();
  }
  const letters = Skia.PathBuilder.Make();
  for (const contour of drawing.letterCurves) {
    if (!contour.length) continue;
    letters.moveTo(contour[0], contour[1]);
    for (let i = 2; i < contour.length; i += 6) letters.cubicTo(contour[i], contour[i + 1], contour[i + 2], contour[i + 3], contour[i + 4], contour[i + 5]);
    letters.close();
  }
  return { outer: plate(drawing.outer), face: plate(drawing.face), letters: letters.build() };
}

function sampleTrack(values: number[][], frame: number) {
  'worklet';
  const i = Math.floor(frame), j = Math.min(values.length - 1, i + 1), p = frame - i;
  return values[i].map((a, n) => a + (values[j][n] - a) * p);
}

function samplePath(frames: number[], paths: SkPath[], pointCounts: number[], frame: number) {
  'worklet';
  const i = Math.floor(frame), j = Math.min(frames.length - 1, i + 1), p = frame - i;
  const a = frames[i], b = frames[j];
  if (a === 0 || a === b || p === 0 || pointCounts[a] !== pointCounts[b]) return paths[a];
  // Skia weights the receiver: next.interpolate(current, p) moves forward in time.
  return paths[b].interpolate(paths[a], p) ?? paths[a];
}

function useSampledPath(frames: number[], paths: SkPath[], pointCounts: number[], frame: SharedValue<number>) {
  return useDerivedValue(() => samplePath(frames, paths, pointCounts, frame.get()));
}

function useEyePath(kind: Kind, eye: number, face: SharedValue<number[]>) {
  return useDerivedValue(() => {
    // Clean eye primitives follow the measured rig rather than magnifying compression edges.
    const v = face.get(), n = eye * 4;
    const rect = { x: -v[n + 2] / 2, y: -v[n + 3] / 2, width: v[n + 2], height: v[n + 3] };
    const path = Skia.PathBuilder.Make();
    if (kind === 'orange') {
      const rx = v[n + 2] / 2, h = v[n + 3], bottom = h / 2, k = .5522847498;
      path.moveTo(-rx, bottom)
        .cubicTo(-rx, bottom - k * h, -k * rx, bottom - h, 0, bottom - h)
        .cubicTo(k * rx, bottom - h, rx, bottom - k * h, rx, bottom).close();
    } else if (eye === 0) path.addRRect(Skia.RRectXY(rect, 1, 1));
    else path.addOval(rect);
    return path.transform(Skia.Matrix().translate(v[n], v[n + 1]).rotate(v[13 + eye] * DEGREES)).build();
  });
}

function useActingEyeTransform(eye: number, face: SharedValue<number[]>, openness: SharedValue<number>) {
  return useDerivedValue<Transforms3d>(() => {
    const sy = openness.get();
    if (sy === 1) return [];
    const v = face.get(), n = eye * 4, angle = v[13 + eye] * DEGREES;
    return [{ translateX: v[n] }, { translateY: v[n + 1] }, { rotate: angle }, { scaleY: sy },
      { rotate: -angle }, { translateX: -v[n] }, { translateY: -v[n + 1] }];
  });
}

function useActingLidTransform(eye: number, face: SharedValue<number[]>) {
  return useDerivedValue<Transforms3d>(() => {
    const v = face.get(), n = eye * 4;
    return [{ translateX: v[n] }, { translateY: v[n + 1] }, { rotate: v[13 + eye] * DEGREES },
      { scaleX: v[n + 2] / 20 }, { scaleY: v[n + 3] / 12 }];
  });
}

function BuzzStreaksSpark({ slot, frames, frame, color }: { slot: number; frames: CachedSpark[][]; frame: SharedValue<number>; color: string }) {
  const empty = useMemo(() => Skia.Path.Make(), []);
  const path = useDerivedValue(() => frames[frame.get()][slot]?.path ?? empty);
  const opacity = useDerivedValue(() => frames[frame.get()][slot]?.opacity ?? 0);
  return <Path antiAlias path={path} color={color} opacity={opacity}/>;
}

/** Source lightning bursts retain their measured 60 Hz contours and opacity. */
export function BuzzStreaksSparks({ clock }: { clock: SharedValue<number> }) {
  const { fps, frameCount, fillRGB: rgb } = SPARKS;
  const frames = useMemo(() => SPARKS.frames.map(glyphs => glyphs.map(glyph => ({
    path: Skia.Path.Polygon(glyph.points.map(([x, y]) => ({ x, y })), true), opacity: glyph.opacity,
  }))), []);
  const frame = useDerivedValue(() => Math.max(0, Math.min(frameCount - 1, Math.floor(clock.get() * fps + 1e-7))));
  const color = `rgb(${rgb[0]},${rgb[1]},${rgb[2]})`;
  return <Group>{[0, 1, 2].map(slot => <BuzzStreaksSpark key={slot} slot={slot} frames={frames} frame={frame} color={color}/>)}</Group>;
}

/** Sharp source geometry and independent native keycaps, driven by the scene's one clock. */
export function BuzzStreaksMascot({ kind, clock, onReady, signatureEnabled = false, characterEnabled = false }: { kind: Kind; clock: SharedValue<number>; onReady: (kind: Kind) => void; signatureEnabled?: boolean; characterEnabled?: boolean }) {
  const body = GEOMETRY.characters[kind], rig = RIG.characters[kind], drawing = KEY_DRAWINGS.characters[kind];
  const frameCount = body.primary.length;
  const faceBaselineAngle = rig.faceBaselineAngle;
  const keyFrames = rig.key, faceFrames = rig.face, armFrames = rig.arm, gradientFrames = rig.bodyGradient;
  const paths = useMemo(() => cachePaths(body), [body]);
  const pointCounts = useMemo(() => body.paths.map(points => points.length / 2), [body]);
  const keyPaths = useMemo(() => cacheKeyDrawing(drawing), [drawing]);
  useEffect(() => { onReady(kind); }, [keyPaths, kind, onReady]);
  const frame = useDerivedValue(() => Math.max(0, Math.min(frameCount - 1, clock.get() * 60)));
  const primary = useSampledPath(body.primary, paths, pointCounts, frame);
  const facePatch = useSampledPath(body.secondary.facePatch, paths, pointCounts, frame);
  const frontArm = useSampledPath(body.secondary.frontArm, paths, pointCounts, frame);
  const tail = useSampledPath(body.secondary.tail, paths, pointCounts, frame);
  const silhouettes = [primary, facePatch, frontArm, tail];
  const gradient = useDerivedValue(() => gradientFrames[Math.floor(frame.get() + 1e-7)]);
  const gradientStart = useDerivedValue(() => ({ x: gradient.get()[0], y: gradient.get()[1] }));
  const gradientEnd = useDerivedValue(() => ({ x: gradient.get()[2], y: gradient.get()[3] }));
  const gradientColors = useDerivedValue(() => {
    const v = gradient.get();
    return [new Float32Array([v[4] / 255, v[5] / 255, v[6] / 255, 1]),
      new Float32Array([v[7] / 255, v[8] / 255, v[9] / 255, 1])];
  });
  const key = useDerivedValue(() => sampleTrack(keyFrames, frame.get()));
  const actorPose = useDerivedValue(() => actingPose(clock.get(), kind, characterEnabled));
  const actorPivot = useDerivedValue(() => actingPivot(clock.get(), kind, key.get()));
  const actorTransform = useDerivedValue<Transforms3d>(() => {
    const p = actorPose.get();
    if (p.x === 0 && p.y === 0 && p.rotation === 0 && p.sy === 1) return [];
    const pivot = actorPivot.get();
    return [{ translateX: p.x + pivot.x }, { translateY: p.y + pivot.y }, { rotate: p.rotation },
      { scaleX: 1 / Math.pow(p.sy, .6) }, { scaleY: p.sy }, { translateX: -pivot.x }, { translateY: -pivot.y }];
  });
  const keyOpacity = useDerivedValue(() => key.get()[5] > .001 ? key.get()[5] : 0);
  const baselineSize = drawing.baseline_size;
  const keyTransform = useDerivedValue<Transforms3d>(() => {
    const v = key.get();
    return [{ translateX: v[0] }, { translateY: v[1] }, { rotate: v[4] * DEGREES },
      { scaleX: v[2] / baselineSize[0] }, { scaleY: v[3] / baselineSize[1] }];
  });
  // Only the face and lettering sink; stage-space depth is compensated for the source key scale.
  const keyFaceTransform = useDerivedValue<Transforms3d>(() => [{
    translateY: signatureContactInset(clock.get(), kind, signatureEnabled) / Math.max(.001, key.get()[3] / baselineSize[1]),
  }]);
  const reflectionOpacity = useDerivedValue(() => signatureReflection(clock.get(), kind, signatureEnabled));
  const keyReflectionTransform = useDerivedValue<Transforms3d>(() => {
    const v = key.get();
    return [{ scaleX: 1 / Math.max(.001, v[2] / baselineSize[0]) },
      { scaleY: 1 / Math.max(.001, v[3] / baselineSize[1]) }];
  });
  const armClip = useDerivedValue(() => {
    const v = sampleTrack(armFrames, frame.get());
    return { x: Math.min(v[0], v[2]) - 7, y: Math.min(v[1], v[3]), width: Math.abs(v[2] - v[0]) + 14, height: Math.abs(v[3] - v[1]) + 8 };
  });
  const frontOpacity = useDerivedValue(() => key.get()[5] > .001 ? 1 : 0);
  const face = useDerivedValue(() => sampleTrack(faceFrames, frame.get()));
  const leftEye = useEyePath(kind, 0, face);
  const rightEye = useEyePath(kind, 1, face);
  const friendKind = kind === 'orange' ? 'pink' : 'orange';
  const friendKeyFrames = RIG.characters[friendKind].key;
  const attentionTarget = useDerivedValue(() => {
    if (!characterEnabled) return { x: 0, y: 0 };
    const time = clock.get(), own = key.get(), friend = sampleTrack(friendKeyFrames, frame.get());
    const ownBody = kind === 'orange' ? { x: 89.056, y: 318 } : { x: 270.75, y: 539.5 };
    const friendBody = friendKind === 'orange' ? { x: 89.056, y: 318 } : { x: 270.75, y: 539.5 };
    const ownWorld = actingTransformPoint(own[5] > .001 ? { x: own[0], y: own[1] } : ownBody, actorPose.get(), actorPivot.get());
    const friendWorld = actingTransformPoint(friend[5] > .001 ? { x: friend[0], y: friend[1] } : friendBody,
      actingPose(time, friendKind), actingPivot(time, friendKind, friend));
    return actingAttentionTarget(time, kind, ownWorld, friendWorld);
  });
  const leftPupil = useDerivedValue(() => {
    const v = face.get(), time = clock.get();
    const gaze = characterEnabled
      ? actingGazeOffset(time, { x: v[0], y: v[1] }, attentionTarget.get(), actorPose.get(), actorPivot.get())
      : { x: signatureGaze(time, kind, 0, signatureEnabled), y: signatureGaze(time, kind, 1, signatureEnabled) };
    return { x: v[8] + gaze.x, y: v[9] + gaze.y };
  });
  const rightPupil = useDerivedValue(() => {
    const v = face.get(), time = clock.get();
    const gaze = characterEnabled
      ? actingGazeOffset(time, { x: v[4], y: v[5] }, attentionTarget.get(), actorPose.get(), actorPivot.get())
      : { x: signatureGaze(time, kind, 0, signatureEnabled), y: signatureGaze(time, kind, 1, signatureEnabled) };
    return { x: v[10] + gaze.x, y: v[11] + gaze.y };
  });
  const happy = useDerivedValue(() => actingHappy(clock.get(), kind, characterEnabled));
  const blink = useDerivedValue(() => actingBlink(clock.get(), kind, characterEnabled));
  const eyeOpenness = useDerivedValue(() => Math.max(.001, (1 - blink.get()) * (1 - .16 * happy.get())));
  const eyeOpacity = useDerivedValue(() => Math.min(1, (1 - blink.get()) * 20));
  const eyeBackingOpacity = useDerivedValue(() => Math.min(1, blink.get() * 8 + happy.get() * .5));
  const closedLidOpacity = useDerivedValue(() => Math.max(0, Math.min(1, (blink.get() - .7) / .3)));
  const leftEyeTransform = useActingEyeTransform(0, face, eyeOpenness);
  const rightEyeTransform = useActingEyeTransform(1, face, eyeOpenness);
  const leftLidTransform = useActingLidTransform(0, face);
  const rightLidTransform = useActingLidTransform(1, face);
  const leftRadius = useDerivedValue(() => face.get()[3] * (kind === 'orange' ? .35 : .29));
  const rightRadius = useDerivedValue(() => face.get()[7] * (kind === 'orange' ? .35 : .29));
  const smileTransform = useDerivedValue<Transforms3d>(() => {
    const v = face.get(), scale = (v[2] + v[6]) / (kind === 'orange' ? 34.24 : 33.42);
    const transform: Transforms3d = [{ translateX: (v[0] + v[4]) / 2 }, { translateY: (v[1] + v[5]) / 2 },
      { rotate: (v[12] - faceBaselineAngle) * DEGREES }, { scale }];
    if (characterEnabled && happy.get() > 0) transform.push({ scaleY: 1 + .22 * happy.get() });
    return transform;
  });
  return <Group transform={characterEnabled ? actorTransform : undefined}>
    {silhouettes.map((path, i) => <Path antiAlias key={i} path={path}><LinearGradient start={gradientStart} end={gradientEnd} colors={gradientColors}/></Path>)}
    {signatureEnabled && kind === 'orange' && <Group clip={primary} opacity={reflectionOpacity}>
      <Circle cx={75} cy={325} r={SIGNATURE.reflectionRadius}><RadialGradient c={{ x: 75, y: 325 }} r={SIGNATURE.reflectionRadius} colors={['white', '#ffffff00']}/></Circle>
    </Group>}
    <Group transform={keyTransform} opacity={keyOpacity}>
      <Path antiAlias path={keyPaths.outer}><LinearGradient start={{ x: drawing.outer_gradient.start[0], y: drawing.outer_gradient.start[1] }} end={{ x: drawing.outer_gradient.end[0], y: drawing.outer_gradient.end[1] }} colors={drawing.outer_gradient.colors.map(keyColor)}/></Path>
      <Group transform={keyFaceTransform}>
        <Path antiAlias path={keyPaths.face}><LinearGradient start={{ x: drawing.face_gradient.start[0], y: drawing.face_gradient.start[1] }} end={{ x: drawing.face_gradient.end[0], y: drawing.face_gradient.end[1] }} colors={drawing.face_gradient.colors.map(keyColor)}/></Path>
        {signatureEnabled && kind === 'pink' && <Group clip={keyPaths.face} opacity={reflectionOpacity}>
          <Group transform={keyReflectionTransform}><Circle cx={0} cy={0} r={SIGNATURE.reflectionRadius}>
            <RadialGradient c={{ x: 0, y: 0 }} r={SIGNATURE.reflectionRadius} colors={['white', '#ffffff00']}/>
          </Circle></Group>
        </Group>}
        <Path antiAlias path={keyPaths.letters} color={keyColor(drawing.ink_rgb)} fillType="evenOdd"/>
      </Group>
    </Group>
    <Group clip={armClip} opacity={frontOpacity}>
      {silhouettes.map((path, i) => <Path antiAlias key={i} path={path}><LinearGradient start={gradientStart} end={gradientEnd} colors={gradientColors}/></Path>)}
    </Group>
    {characterEnabled ? <>
      <Group opacity={eyeBackingOpacity}>
        <Path antiAlias path={leftEye}><LinearGradient start={gradientStart} end={gradientEnd} colors={gradientColors}/></Path>
        <Path antiAlias path={rightEye}><LinearGradient start={gradientStart} end={gradientEnd} colors={gradientColors}/></Path>
      </Group>
      <Group transform={leftEyeTransform} opacity={eyeOpacity}><Path antiAlias path={leftEye} color="white"/><Group clip={leftEye}><Circle c={leftPupil} r={leftRadius} color="black"/></Group></Group>
      <Group transform={rightEyeTransform} opacity={eyeOpacity}><Path antiAlias path={rightEye} color="white"/><Group clip={rightEye}><Circle c={rightPupil} r={rightRadius} color="black"/></Group></Group>
      <Group opacity={closedLidOpacity}>
        <Group transform={leftLidTransform}><Path antiAlias path={CLOSED_LID} color={kind === 'pink' ? '#9e1758' : '#a8460b'} style="stroke" strokeWidth={1.1} strokeCap="round"/></Group>
        <Group transform={rightLidTransform}><Path antiAlias path={CLOSED_LID} color={kind === 'pink' ? '#9e1758' : '#a8460b'} style="stroke" strokeWidth={1.1} strokeCap="round"/></Group>
      </Group>
    </> : <>
      <Path antiAlias path={leftEye} color="white"/><Group clip={leftEye}><Circle c={leftPupil} r={leftRadius} color="black"/></Group>
      <Path antiAlias path={rightEye} color="white"/><Group clip={rightEye}><Circle c={rightPupil} r={rightRadius} color="black"/></Group>
    </>}
    <Group transform={smileTransform}><Path antiAlias path={SMILES[kind]} style="stroke" strokeWidth={1.25} strokeCap="round" color="white"/></Group>
  </Group>;
}
