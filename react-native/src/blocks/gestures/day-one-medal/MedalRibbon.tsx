import {
  BlurStyle, Canvas, ClipOp, PaintStyle, Picture, Skia,
  type SkCanvas, type SkPathBuilder, StrokeCap, StrokeJoin,
} from '@shopify/react-native-skia';
import { useMemo } from 'react';
import { type SharedValue, useDerivedValue, useFrameCallback, useSharedValue } from 'react-native-reanimated';

import { createRibbonState, type RibbonPoint, type RibbonSample, type RibbonState, stepRibbon } from './medal-ribbon-sim';

/** All native drawing objects are kept between frames, including the scratch paths. */
export function createRibbonInk(api: typeof Skia) {
  const paint = (colour: string, stroke = 0) => {
    const p = api.Paint();
    p.setAntiAlias(true);
    p.setColor(api.Color(colour));
    if (stroke) {
      p.setStyle(PaintStyle.Stroke);
      p.setStrokeWidth(stroke);
      p.setStrokeJoin(StrokeJoin.Round);
      p.setStrokeCap(StrokeCap.Round);
    }
    return p;
  };
  const shadow = paint('#000000');
  shadow.setMaskFilter(api.MaskFilter.MakeBlur(BlurStyle.Normal, 2.5, true));
  return {
    // The rounded weave catches the warm source on the left and falls into blue-black on the right.
    face: ['#111826', '#182235', '#202D42', '#29374D', '#2D3B50', '#29384D', '#233147', '#1B283C', '#121C2E']
      .map((colour) => paint(colour)),
    gold: paint('#DDA85A', 1.05),
    goldShade: paint('#73522E', 1.9),
    stitch: paint('#EAC68D', 0.42),
    rib: paint('#8291A7', 0.48),
    ribShade: paint('#030610', 0.5),
    warp: paint('#96A2B5', 0.3),
    shade: paint('#000006'),
    edge: paint('#526176', 0.55),
    shadow,
    metal: paint('#08090C', 4.2),
    metalBody: paint('#343438', 2.8),
    metalLight: paint('#B4A58B', 0.85),
    collar: paint('#222329'),
    collarEdge: paint('#505056', 0.7),
    path: api.PathBuilder.Make(),
    outline: api.PathBuilder.Make(),
    clip: api.PathBuilder.Make(),
    loop: api.RRectXY(api.XYWHRect(-7, -17, 14, 22), 5, 5),
    collarShape: api.RRectXY(api.XYWHRect(-13, -19, 26, 7), 2, 2),
  };
}

type RibbonInk = ReturnType<typeof createRibbonInk>;
export type RibbonDrawOptions = { scale: number; ink: RibbonInk };

function edgePath(path: SkPathBuilder, samples: RibbonSample[], edge: number) {
  'worklet';
  path.reset();
  for (let i = 0; i < samples.length; i++) {
    const p = samples[i];
    const x = p.x + p.nx * p.width * edge;
    const y = p.y + p.ny * p.width * edge;
    if (i === 0) path.moveTo(x, y);
    else path.lineTo(x, y);
  }
}

function stripPath(path: SkPathBuilder, samples: RibbonSample[], left: number, right: number) {
  'worklet';
  edgePath(path, samples, left);
  for (let i = samples.length - 1; i >= 0; i--) {
    const p = samples[i];
    path.lineTo(p.x + p.nx * p.width * right, p.y + p.ny * p.width * right);
  }
  path.close();
}

function drawBand(canvas: SkCanvas, samples: RibbonSample[], opacity: number, options: RibbonDrawOptions) {
  'worklet';
  const { ink, scale } = options;
  const { path } = ink;
  for (let lane = 0; lane < ink.face.length; lane++) {
    // Tiny overlaps prevent transparent seams between the fabric's rounded longitudinal fibres.
    stripPath(path, samples, -1 + lane * 2 / 9, Math.min(1, -1 + (lane + 1) * 2 / 9 + 0.008));
    ink.face[lane].setAlphaf(opacity);
    canvas.drawPath(path.build(), ink.face[lane]);
  }
  for (let side = -1; side <= 1; side += 2) {
    edgePath(path, samples, side * 0.8);
    ink.goldShade.setStrokeWidth(1.9 * scale);
    ink.goldShade.setAlphaf(opacity * 0.9);
    canvas.drawPath(path.build(), ink.goldShade);
    ink.gold.setStrokeWidth(0.95 * scale);
    ink.gold.setAlphaf(opacity * 0.84);
    canvas.drawPath(path.build(), ink.gold);
    edgePath(path, samples, side * 0.975);
    ink.edge.setStrokeWidth(0.55 * scale);
    ink.edge.setAlphaf(opacity * (side < 0 ? 0.55 : 0.16));
    canvas.drawPath(path.build(), ink.edge);
  }
  ink.rib.setStrokeWidth(0.48 * scale);
  ink.ribShade.setStrokeWidth(0.5 * scale);
  ink.stitch.setStrokeWidth(0.42 * scale);
  let travel = 0;
  let ribAt = 0;
  let ribIndex = 0;
  for (let i = 1; i < samples.length; i++) {
    const a = samples[i - 1];
    const b = samples[i];
    const span = Math.hypot(b.x - a.x, b.y - a.y);
    while (ribAt <= travel + span) {
      const t = span > 0 ? (ribAt - travel) / span : 0;
      const x = a.x + (b.x - a.x) * t;
      const y = a.y + (b.y - a.y) * t;
      const nx = a.nx + (b.nx - a.nx) * t;
      const ny = a.ny + (b.ny - a.ny) * t;
      const half = (a.width + (b.width - a.width) * t) * 0.93;
      ink.rib.setAlphaf(opacity * (ribIndex % 3 === 0 ? 0.22 : 0.13));
      ink.ribShade.setAlphaf(opacity * 0.24);
      canvas.drawLine(x - nx * half, y - ny * half, x + nx * half, y + ny * half, ink.rib);
      canvas.drawLine(x - nx * half - ny * 0.5 * scale, y - ny * half + nx * 0.5 * scale,
        x + nx * half - ny * 0.5 * scale, y + ny * half + nx * 0.5 * scale, ink.ribShade);
      if (ribIndex % 3 === 0) {
        ink.stitch.setAlphaf(opacity * 0.48);
        for (let side = -1; side <= 1; side += 2) {
          const sx = x + nx * half * 0.86 * side;
          const sy = y + ny * half * 0.86 * side;
          canvas.drawLine(sx, sy, sx - ny * 0.8 * scale, sy + nx * 0.8 * scale, ink.stitch);
        }
      }
      ribAt += 1.45 * scale;
      ribIndex++;
    }
    travel += span;
    path.reset();
    path.moveTo(a.x - a.nx * a.width, a.y - a.ny * a.width);
    path.lineTo(b.x - b.nx * b.width, b.y - b.ny * b.width);
    path.lineTo(b.x + b.nx * b.width, b.y + b.ny * b.width);
    path.lineTo(a.x + a.nx * a.width, a.y + a.ny * a.width);
    path.close();
    ink.shade.setAlphaf(opacity * (1 - (a.light + b.light) * 0.5));
    canvas.drawPath(path.build(), ink.shade);
  }
  // Sparse warp threads break the mechanically perfect cross-ribs at phone resolution.
  for (let lane = -3; lane <= 3; lane++) {
    edgePath(path, samples, lane * 0.2);
    ink.warp.setStrokeWidth(0.3 * scale);
    ink.warp.setAlphaf(opacity * 0.07);
    canvas.drawPath(path.build(), ink.warp);
  }
}

/** Shared by the UI-thread picture and the offline CanvasKit proof. */
export function drawRibbon(canvas: SkCanvas, _api: typeof Skia, state: RibbonState, options: RibbonDrawOptions) {
  'worklet';
  if (!state.active || state.opacity <= 0) return;
  const { ink, scale } = options;
  const alpha = state.opacity;
  const rear = state.bands[0].samples;
  const front = state.bands[1].samples;
  for (let b = 0; b < 2; b++) {
    stripPath(ink.outline, state.bands[b].samples, -1, 1);
    canvas.save();
    canvas.translate(2 * scale, 3 * scale);
    ink.shadow.setAlphaf(alpha * 0.24);
    canvas.drawPath(ink.outline.build(), ink.shadow);
    canvas.restore();
  }
  drawBand(canvas, rear, alpha, options);
  stripPath(ink.clip, rear, -1, 1);
  stripPath(ink.outline, front, -1, 1);
  canvas.save();
  canvas.clipPath(ink.clip.build(), ClipOp.Intersect, true);
  canvas.translate(-2 * scale, 2 * scale);
  ink.shadow.setAlphaf(alpha * 0.7);
  canvas.drawPath(ink.outline.build(), ink.shadow);
  canvas.restore();
  drawBand(canvas, front, alpha, options);

  const x = state.tip.x;
  const y = state.tip.y;
  canvas.save();
  canvas.translate(x, y);
  canvas.scale(scale, scale);
  // The lower loop disappears just into the medal. Only a short warm edge catches the room.
  ink.metal.setAlphaf(alpha);
  ink.metalBody.setAlphaf(alpha);
  ink.metalLight.setAlphaf(alpha * 0.85);
  canvas.drawRRect(ink.loop, ink.metal);
  canvas.drawRRect(ink.loop, ink.metalBody);
  ink.path.reset();
  ink.path.moveTo(-6.5, -2);
  ink.path.lineTo(-6.5, -11);
  ink.path.quadTo(-6.5, -16, -2, -16);
  canvas.drawPath(ink.path.build(), ink.metalLight);
  ink.collar.setAlphaf(alpha);
  ink.collarEdge.setAlphaf(alpha * 0.8);
  canvas.drawRRect(ink.collarShape, ink.collar);
  canvas.drawRRect(ink.collarShape, ink.collarEdge);
  canvas.drawLine(-10, -18, 3, -18, ink.metalLight);
  ink.metalLight.setAlphaf(alpha * 0.3);
  canvas.drawLine(8.5, -15, 10, -15, ink.metalLight);
  canvas.restore();
}

type Props = {
  width: number;
  height: number;
  pivot: RibbonPoint;
  attach: SharedValue<RibbonPoint>;
  worn: SharedValue<number>;
  clock: SharedValue<number>;
  scale: number;
  /** Reduce Motion keeps the cloth straight and uses worn only as opacity. */
  reduced?: boolean;
};

export function MedalRibbon({ width, height, pivot, attach, worn, clock, scale, reduced = false }: Props) {
  const state = useSharedValue(createRibbonState());
  const lastClock = useSharedValue(-1);
  const ink = useMemo(() => createRibbonInk(Skia), []);
  const options = useMemo(() => ({ scale, ink }), [scale, ink]);
  useFrameCallback(() => {
    'worklet';
    const now = clock.get();
    const amount = worn.get();
    const previous = lastClock.get();
    lastClock.set(now);
    if (amount <= 0 && !state.get().active) return;
    const end = attach.get();
    const value = state.get();
    stepRibbon(value, previous < 0 ? 0 : now - previous, amount, pivot, end, scale, reduced);
    state.set({ ...value });
  });
  const picture = useDerivedValue(() => {
    'worklet';
    const value = state.get();
    const recorder = Skia.PictureRecorder();
    const canvas = recorder.beginRecording(Skia.XYWHRect(0, 0, width, height));
    drawRibbon(canvas, Skia, value, options);
    return recorder.finishRecordingAsPicture();
  });

  return (
    <Canvas style={{ position: 'absolute', left: 0, top: 0, width, height }} pointerEvents="none">
      <Picture picture={picture} />
    </Canvas>
  );
}
