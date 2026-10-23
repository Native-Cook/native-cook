import {
  BlendMode,
  BlurStyle,
  Canvas,
  FilterMode,
  MipmapMode,
  PaintStyle,
  Picture,
  Skia,
  type SkCanvas,
  type SkImage,
  type SkPaint,
  type SkPath,
  type SkShader,
  StrokeCap,
  StrokeJoin,
  TileMode,
} from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { useEffect, useMemo, useState } from 'react';
import { type LayoutChangeEvent, PixelRatio, StyleSheet, Text, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import { useDerivedValue, useFrameCallback, useReducedMotion, useSharedValue } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

import {
  createNeonState,
  MAX_TICKS,
  type NeonState,
  setWord,
  stepNeon,
  TICK,
  WALL_SKSL,
  wordLevels,
} from './neonSignSim';
import { buildSign, type NeonPiece } from './neonSignTubes';

type Props = {
  /** Powers the sign on, browns it out, swings it and powers it down on a 12 s loop. Off by default. */
  autoPlay?: boolean;
};

// The colours of the gas, per tone (rose, ice cyan, amber, lime): the bright core of a lit tube,
// its white-hot centre, the colour of its halo, and the coated glass when it is dark.
const CORE = ['#ff6fae', '#45dcff', '#ffc061', '#d4ff70'];
const HOT = ['#ffe6f1', '#c9f6ff', '#fff3da', '#f7ffe2'];
// Cyan reads far brighter than rose at the same strength: each tone's halo is scaled to match.
const HALO_GAIN = [1, 0.5, 0.85, 0.9];
const HALO = ['#ff2d7c', '#14c4ff', '#ff8a1c', '#8cff26'];
const UNLIT = ['#4a3440', '#2f4650', '#4d4234', '#3f4a32'];
// The light each word throws on the wall (rose, cyan, amber), linear 0…1.
const SPILL = [
  [1.0, 0.2, 0.48],
  [0.1, 0.62, 1.0],
  [1.0, 0.5, 0.14],
];
// Which tone lights the wall for each word.
const WORD_NAMES = ['open', 'late', 'cocktail glass'];

// The tube: glass diameter, lit core, white-hot centre (pt).
const TUBE = 6;
const CORE_W = 4.4;
const HOT_W = 1.7;
// The blacked-out glass at a tube's electrode end (pt).
const CAP_LENGTH = 3.6;
// Halo layers baked around each tube: stroke width and blur (pt), and strength.
const HALO_LAYERS = [
  [26, 15, 0.3],
  [12, 5.5, 0.55],
  [6, 2, 0.85],
];
const HALO_PAD = 34;
const HALO_SCALE = 2;
// The light on the wall is baked at this many pixels per pt, this far around the sign (pt).
const SPILL_SCALE = 0.2;
const SPILL_MARGIN = 300;

// The clear acrylic panel the tubes are mounted on, around the lettering (pt).
const PANEL_PAD_X = 30;
const PANEL_PAD_Y = 30;
const PANEL_RADIUS = 14;
// The two chains meet at a hook HANG pt above the panel; they hold it CHAIN_INSET from its corners.
const HANG = 96;
const CHAIN_INSET = 20;
const LINK = 6.4;
// The panel's centre, as a share of the screen's height.
const CENTER_AT = 0.45;
// The swing a finger can give before it resists (rad), and how hard it resists past that.
const MAX_SWING = 0.5;
const RUBBER = 0.35;
const MAX_SPIN = 2;
// After a touch the loop waits this long (s), then starts over.
const USER_HOLD = 4;
// Baked images are disposed this long (ms) after the block lets them go.
const DISPOSE_DELAY = 500;
// The tubes that are allowed to stutter: the "t" of "late".
const FAULTY_WORD = 1;
const FAULTY_ORDER = 2;

const FAMILY = process.env.EXPO_OS === 'ios' ? 'Avenir Next' : 'sans-serif';

// Null only if the shader fails to compile: the wall then stays dark instead of throwing.
const WALL_EFFECT = Skia.RuntimeEffect.Make(WALL_SKSL);

/** Seeded random in [0, 1): mulberry32. */
function seeded(seed: number) {
  let a = seed >>> 0;
  return () => {
    a = (a + 0x6d2b79f5) >>> 0;
    let t = a;
    t = Math.imul(t ^ (t >>> 15), t | 1);
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

function pathOf(seg: number[]): SkPath {
  const b = Skia.PathBuilder.Make();
  b.moveTo(seg[0], seg[1]);
  for (let i = 0; i < seg.length; i += 8) b.cubicTo(seg[i + 2], seg[i + 3], seg[i + 4], seg[i + 5], seg[i + 6], seg[i + 7]);
  return b.detach();
}

function stroke(color: string, width: number, alpha = 1, blur = 0): SkPaint {
  'worklet';
  const p = Skia.Paint();
  p.setAntiAlias(true);
  p.setStyle(PaintStyle.Stroke);
  p.setStrokeWidth(width);
  p.setStrokeCap(StrokeCap.Round);
  p.setStrokeJoin(StrokeJoin.Round);
  p.setColor(Skia.Color(color));
  p.setAlphaf(alpha);
  if (blur > 0) p.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, blur, true));
  return p;
}

function fill(color: string, alpha = 1, blur = 0): SkPaint {
  'worklet';
  const p = Skia.Paint();
  p.setAntiAlias(true);
  p.setColor(Skia.Color(color));
  p.setAlphaf(alpha);
  if (blur > 0) p.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, blur, true));
  return p;
}

function rgb(c: number[]) {
  return `rgb(${Math.round(c[0])}, ${Math.round(c[1])}, ${Math.round(c[2])})`;
}

/** The brick wall's own colour, without light: running bond, each brick a little different. */
function bakeWall(width: number, height: number, scale: number): SkImage {
  const surface = Skia.Surface.Make(Math.ceil(width * scale), Math.ceil(height * scale))!;
  const c = surface.getCanvas();
  c.scale(scale, scale);
  const rand = seeded(7121);
  c.drawRect(Skia.XYWHRect(0, 0, width, height), fill('#7a7166'));
  // Grit in the mortar.
  for (let i = 0; i < 1600; i += 1) {
    const light = rand() > 0.5;
    c.drawCircle(rand() * width, rand() * height, 0.3 + rand() * 0.6, fill(light ? '#a49a8c' : '#4a443d', 0.5));
  }
  const ROW = 25;
  const MORTAR = 3.4;
  const BRICK = 62;
  const tones = [
    [150, 70, 50],
    [134, 60, 44],
    [162, 84, 60],
    [120, 56, 44],
    [100, 48, 40],
    [172, 98, 72],
    [142, 76, 58],
  ];
  for (let r = 0; r * ROW < height + ROW; r += 1) {
    const y = r * ROW + MORTAR / 2;
    const h = ROW - MORTAR;
    const shift = (r % 2) * (BRICK / 2) + (rand() - 0.5) * 6;
    for (let x = -BRICK + shift; x < width + BRICK; x += BRICK) {
      const w = BRICK - MORTAR + (rand() - 0.5) * 2;
      const tone = tones[Math.floor(rand() * tones.length)];
      const k = 0.85 + rand() * 0.25;
      const base = tone.map((v) => v * k);
      const rect = Skia.XYWHRect(x + MORTAR / 2, y, w, h);
      const brick = Skia.Paint();
      brick.setAntiAlias(true);
      brick.setShader(
        Skia.Shader.MakeLinearGradient(
          Skia.Point(0, y),
          Skia.Point(0, y + h),
          [Skia.Color(rgb(base.map((v) => v * 1.1))), Skia.Color(rgb(base)), Skia.Color(rgb(base.map((v) => v * 0.78)))],
          [0, 0.35, 1],
          TileMode.Clamp,
        ),
      );
      c.drawRRect(Skia.RRectXY(rect, 2.4, 2.4), brick);
      // Pits and flecks, and now and then a darker burn or a chipped corner.
      for (let i = 0; i < 16; i += 1) {
        const dark = rand() > 0.35;
        c.drawCircle(
          x + MORTAR / 2 + rand() * w,
          y + rand() * h,
          0.35 + rand() * 1.1,
          fill(dark ? rgb(base.map((v) => v * 0.6)) : rgb(base.map((v) => Math.min(255, v * 1.35))), 0.45),
        );
      }
      if (rand() > 0.72) {
        c.drawCircle(x + MORTAR / 2 + rand() * w, y + rand() * h, 4 + rand() * 7, fill('#2a1410', 0.22, 4));
      }
      if (rand() > 0.85) {
        const cx = rand() > 0.5 ? x + MORTAR / 2 : x + MORTAR / 2 + w;
        c.drawCircle(cx, y + (rand() > 0.5 ? 0 : h), 2.5 + rand() * 2, fill('#6d655b', 1));
      }
      // The lower edge sits in shadow, the upper one catches the light from above.
      c.drawLine(x + MORTAR / 2 + 2, y + h - 0.4, x + MORTAR / 2 + w - 2, y + h - 0.4, stroke('#000000', 1, 0.32));
      c.drawLine(x + MORTAR / 2 + 2, y + 0.5, x + MORTAR / 2 + w - 2, y + 0.5, stroke('#ffd9c4', 0.8, 0.12));
    }
  }
  // Soot and damp, a few broad stains.
  for (let i = 0; i < 9; i += 1) {
    c.drawCircle(rand() * width, rand() * height, 40 + rand() * 90, fill('#120a08', 0.16, 36));
  }
  const image = surface.makeImageSnapshot();
  surface.dispose();
  return image;
}

type Glow = { image: SkImage; x: number; y: number; w: number; h: number };

/** One piece's halo at full brightness, in its own small image. */
function bakeGlow(piece: NeonPiece, paths: SkPath[]): Glow {
  const [bx, by, bw, bh] = piece.box;
  const w = bw + 2 * HALO_PAD;
  const h = bh + 2 * HALO_PAD;
  const surface = Skia.Surface.Make(Math.ceil(w * HALO_SCALE), Math.ceil(h * HALO_SCALE))!;
  const c = surface.getCanvas();
  c.scale(HALO_SCALE, HALO_SCALE);
  c.translate(HALO_PAD - bx, HALO_PAD - by);
  for (let k = 0; k < HALO_LAYERS.length; k += 1) {
    const [width, blur, alpha] = HALO_LAYERS[k];
    const color = k === HALO_LAYERS.length - 1 ? CORE[piece.tone] : HALO[piece.tone];
    const p = stroke(color, width, alpha * HALO_GAIN[piece.tone], blur);
    p.setBlendMode(BlendMode.Plus);
    for (const path of paths) c.drawPath(path, p);
  }
  const image = surface.makeImageSnapshot();
  surface.dispose();
  return { image, x: bx - HALO_PAD, y: by - HALO_PAD, w, h };
}

/** The light the sign throws on the wall, one word per channel, in the sign's frame. */
function bakeSpill(pieces: NeonPiece[], paths: SkPath[][], box: number[]) {
  const x0 = box[0] - SPILL_MARGIN;
  const y0 = box[1] - SPILL_MARGIN;
  const w = box[2] + 2 * SPILL_MARGIN;
  const h = box[3] + 2 * SPILL_MARGIN;
  const surface = Skia.Surface.Make(Math.ceil(w * SPILL_SCALE), Math.ceil(h * SPILL_SCALE))!;
  const c = surface.getCanvas();
  c.drawColor(Skia.Color('#000000'));
  c.scale(SPILL_SCALE, SPILL_SCALE);
  c.translate(-x0, -y0);
  const channel = ['#ff0000', '#00ff00', '#0000ff'];
  for (let word = 0; word < 3; word += 1) {
    let wx0 = Infinity;
    let wy0 = Infinity;
    let wx1 = -Infinity;
    let wy1 = -Infinity;
    const near = stroke(channel[word], 18, 0.55, 20);
    const far = stroke(channel[word], 34, 0.5, 52);
    near.setBlendMode(BlendMode.Plus);
    far.setBlendMode(BlendMode.Plus);
    pieces.forEach((piece, i) => {
      if (piece.word !== word) return;
      for (const path of paths[i]) {
        c.drawPath(path, far);
        c.drawPath(path, near);
      }
      wx0 = Math.min(wx0, piece.box[0]);
      wy0 = Math.min(wy0, piece.box[1]);
      wx1 = Math.max(wx1, piece.box[0] + piece.box[2]);
      wy1 = Math.max(wy1, piece.box[1] + piece.box[3]);
    });
    // A broad wash that reaches across the wall.
    const wash = Skia.Paint();
    wash.setBlendMode(BlendMode.Plus);
    const cx = (wx0 + wx1) / 2;
    const cy = (wy0 + wy1) / 2;
    wash.setShader(
      Skia.Shader.MakeRadialGradient(
        Skia.Point(cx, cy),
        260,
        [Skia.Color(channel[word]), Skia.Color('#000000')],
        [0, 1],
        TileMode.Clamp,
      ),
    );
    wash.setAlphaf(0.3);
    c.drawCircle(cx, cy, 260, wash);
  }
  const image = surface.makeImageSnapshot();
  surface.dispose();
  const shader = image.makeShaderOptions(
    TileMode.Decal,
    TileMode.Decal,
    FilterMode.Linear,
    MipmapMode.None,
    Skia.Matrix().translate(x0, y0).scale(1 / SPILL_SCALE, 1 / SPILL_SCALE),
  );
  return { image, shader };
}

type Scene = {
  paths: SkPath[][];
  caps: SkPath;
  mounts: number[][];
  panel: { x: number; y: number; w: number; h: number };
  pieces: NeonPiece[];
};

/** The sign's geometry: the tubes as paths, their electrode ends, the mounts and the panel. */
function buildScene(): Scene {
  const { pieces, width, height } = buildSign();
  const paths = pieces.map((p) => p.tubes.map(pathOf));
  const panel = { x: -width / 2 - PANEL_PAD_X, y: -height / 2 - PANEL_PAD_Y, w: width + 2 * PANEL_PAD_X, h: height + 2 * PANEL_PAD_Y };
  // Where a tube passes through the panel the glass is painted black (the electrode ends), and
  // each piece sits on one clear standoff.
  const caps = Skia.PathBuilder.Make();
  const mounts: number[][] = [];
  for (const piece of pieces) {
    const t = piece.tubes[0];
    const n = t.length;
    const ends: number[][] = [];
    if (piece.ends[0]) ends.push([t[0], t[1], t[2] - t[0], t[3] - t[1]]);
    if (piece.ends[1]) ends.push([t[n - 2], t[n - 1], t[n - 4] - t[n - 2], t[n - 3] - t[n - 1]]);
    for (const [x, y, dx, dy] of ends) {
      const len = Math.hypot(dx, dy) || 1;
      caps.moveTo(x, y);
      caps.lineTo(x + (dx / len) * CAP_LENGTH, y + (dy / len) * CAP_LENGTH);
    }
    const mid = Math.floor(t.length / 16) * 8;
    mounts.push([t[mid], t[mid + 1]]);
  }
  return { paths, caps: caps.detach(), mounts, panel, pieces };
}

type Bakes = {
  glows: Glow[];
  spill: { image: SkImage; shader: SkShader };
  wall: { image: SkImage; shader: SkShader };
};

/** Every image the sign is drawn with, rendered once on the CPU. */
function bake(scene: Scene, width: number, height: number, wallScale: number): Bakes {
  const glows = scene.pieces.map((p, i) => bakeGlow(p, scene.paths[i]));
  const { panel } = scene;
  const spill = bakeSpill(scene.pieces, scene.paths, [panel.x + PANEL_PAD_X, panel.y + PANEL_PAD_Y, panel.w - 2 * PANEL_PAD_X, panel.h - 2 * PANEL_PAD_Y]);
  const image = bakeWall(width, height, wallScale);
  const shader = image.makeShaderOptions(
    TileMode.Clamp,
    TileMode.Clamp,
    FilterMode.Linear,
    MipmapMode.None,
    Skia.Matrix().scale(1 / wallScale, 1 / wallScale),
  );
  return { glows, spill, wall: { image, shader } };
}

function disposeBakes(b: Bakes) {
  b.glows.forEach((g) => g.image.dispose());
  b.spill.image.dispose();
  b.wall.image.dispose();
}

function mix(a: number, b: number, t: number) {
  'worklet';
  return a + (b - a) * t;
}

/** Moves the canvas into the sign's frame: everything that hangs turns `deg` about the hook. */
function enterSign(canvas: SkCanvas, px: number, py: number, deg: number, lx: number, ly: number) {
  'worklet';
  canvas.translate(px, py);
  canvas.rotate(deg, 0, 0);
  canvas.translate(-lx, -ly);
}

/** The angle of (x, y) around the hook at (px, py), measured from straight down, clockwise. */
function angleAround(x: number, y: number, px: number, py: number) {
  'worklet';
  return Math.atan2(-(x - px), y - py);
}

/**
 * A hand-bent neon sign, "open late" in rose and ice-blue script beside an amber cocktail glass,
 * hanging on two chains in front of a dark brick wall. The tubes buzz on letter by letter and warm
 * up, one letter never quite works, and the wall takes the colour of whatever is lit. Tap a word
 * to switch it; drag the sign to swing it on its chains.
 */
export function NeonSignFlicker({ autoPlay = false }: Props) {
  const insets = useSafeAreaInsets();
  const reducedMotion = useReducedMotion();
  const auto = autoPlay && !reducedMotion;
  const [size, setSize] = useState<{ width: number; height: number } | null>(null);
  const scene = useMemo(() => buildScene(), []);
  const wallScale = Math.min(3, PixelRatio.get());
  // The baked images belong to this effect: made once the size is known, and disposed a moment
  // after they are let go, so a frame being drawn on the UI thread never meets a dead image.
  const bakes = useSharedValue<Bakes | null>(null);
  useEffect(() => {
    if (!size || size.width < 1 || size.height < 1) return;
    let made: Bakes;
    try {
      made = bake(scene, size.width, size.height, wallScale);
    } catch {
      // A surface that could not be made: the sign stays dark rather than crashing.
      return;
    }
    bakes.set(made);
    return () => {
      bakes.set(null);
      setTimeout(() => disposeBakes(made), DISPOSE_DELAY);
    };
  }, [bakes, scene, size, wallScale]);

  const width = size?.width ?? 0;
  const height = size?.height ?? 0;
  const { panel } = scene;
  // The hook on the wall, and where it sits in the sign's own frame.
  const pivotX = width / 2;
  const pivotY = Math.max(insets.top + 34, height * CENTER_AT - panel.h / 2 - HANG);
  const pivotLX = 0;
  const pivotLY = panel.y - HANG;
  const anchors = [
    [panel.x + CHAIN_INSET, panel.y + 11],
    [panel.x + panel.w - CHAIN_INSET, panel.y + 11],
  ];

  const pieceWord = scene.pieces.map((p) => p.word);
  const pieceOrder = scene.pieces.map((p) => p.order);
  const faulty = scene.pieces.findIndex((p) => p.word === FAULTY_WORD && p.order === FAULTY_ORDER);

  const sim = useSharedValue<NeonState | null>(null);
  const frame = useSharedValue(0);
  const acc = useSharedValue(0);
  const looping = useSharedValue(auto);
  const grabTheta = useSharedValue(0);
  const grabPhi = useSharedValue(0);

  // The frame callback restarts the script when this flips (the state lives on the UI thread).
  useEffect(() => {
    looping.set(auto);
  }, [auto, looping]);

  const toggled = () => {
    if (process.env.EXPO_OS !== 'web') Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
  };

  useFrameCallback((info) => {
    let st = sim.get();
    if (st === null) {
      st = createNeonState(pieceWord, pieceOrder, faulty, looping.get(), reducedMotion);
      sim.set(st);
    }
    const loop = looping.get();
    if (st.looping !== loop) {
      // autoPlay switched: the script starts over from its beginning.
      st.clock = 0;
      st.hold = 0;
    }
    st.looping = loop;
    st.reduced = reducedMotion;
    const dt = Math.min((info.timeSincePreviousFrame ?? 16) / 1000, MAX_TICKS * TICK);
    let pending = acc.get() + dt;
    let ticks = 0;
    while (pending >= TICK && ticks < MAX_TICKS) {
      stepNeon(st);
      pending -= TICK;
      ticks += 1;
    }
    acc.set(Math.min(pending, TICK));
    if (ticks > 0) frame.set(frame.get() + 1);
  });

  const picture = useDerivedValue(() => {
    frame.get();
    const recorder = Skia.PictureRecorder();
    const canvas = recorder.beginRecording(Skia.XYWHRect(0, 0, width, height));
    const st = sim.get();
    const baked = bakes.get();
    if (width === 0 || baked === null || st === null) {
      canvas.drawColor(Skia.Color('#0c0908'));
      return recorder.finishRecordingAsPicture();
    }
    const levels = [0, 0, 0];
    wordLevels(st, levels);
    const theta = st.theta;
    const deg = (theta * 180) / Math.PI;
    const cos = Math.cos(theta);
    const sin = Math.sin(theta);

    // The wall, lit by the street and by the sign.
    if (WALL_EFFECT === null) {
      canvas.drawColor(Skia.Color('#0c0908'));
    } else {
      const wallPaint = Skia.Paint();
      wallPaint.setShader(
        WALL_EFFECT.makeShaderWithChildren(
          [
            pivotX,
            pivotY,
            pivotLX,
            pivotLY,
            cos,
            sin,
            levels[0],
            levels[1],
            levels[2],
            ...SPILL[0],
            ...SPILL[1],
            ...SPILL[2],
            width,
            height,
          ],
          [baked.wall.shader, baked.spill.shader],
        ),
      );
      canvas.drawRect(Skia.XYWHRect(0, 0, width, height), wallPaint);
    }

    // The light the sign itself gives off, as a colour: tints its chains and the panel's edge.
    let lr = 0;
    let lg = 0;
    let lb = 0;
    for (let w = 0; w < 3; w += 1) {
      lr += SPILL[w][0] * levels[w];
      lg += SPILL[w][1] * levels[w];
      lb += SPILL[w][2] * levels[w];
    }
    const glowAmount = Math.min(1, (levels[0] + levels[1] + levels[2]) / 1.6);
    const peak = Math.max(lr, lg, lb, 0.001);
    const tint = [lr / peak, lg / peak, lb / peak];

    // Its soft shadow from the street light above.
    canvas.save();
    canvas.translate(0, 14);
    enterSign(canvas, pivotX, pivotY, deg, pivotLX, pivotLY);
    canvas.drawRRect(Skia.RRectXY(Skia.XYWHRect(panel.x, panel.y, panel.w, panel.h), PANEL_RADIUS, PANEL_RADIUS), fill('#000000', 0.32, 12));
    canvas.restore();

    canvas.save();
    enterSign(canvas, pivotX, pivotY, deg, pivotLX, pivotLY);

    // Chains: links alternately seen face-on and edge-on.
    const steel = Float32Array.of(
      mix(0.42, tint[0], 0.55 * glowAmount),
      mix(0.43, tint[1], 0.55 * glowAmount),
      mix(0.46, tint[2], 0.55 * glowAmount),
      1,
    );
    const linkFace = Skia.Paint();
    linkFace.setAntiAlias(true);
    linkFace.setStyle(PaintStyle.Stroke);
    linkFace.setStrokeWidth(1.5);
    linkFace.setColor(steel);
    const linkEdge = Skia.Paint();
    linkEdge.setAntiAlias(true);
    linkEdge.setStyle(PaintStyle.Stroke);
    linkEdge.setStrokeWidth(2.1);
    linkEdge.setStrokeCap(StrokeCap.Round);
    linkEdge.setColor(steel);
    const linkShine = stroke('#ffffff', 0.6, 0.35);
    for (const [ax, ay] of anchors) {
      const dx = ax - pivotLX;
      const dy = ay - pivotLY;
      const len = Math.hypot(dx, dy);
      const ux = dx / len;
      const uy = dy / len;
      const count = Math.floor(len / LINK);
      for (let k = 0; k < count; k += 1) {
        const cx = pivotLX + ux * (k + 0.5) * LINK;
        const cy = pivotLY + uy * (k + 0.5) * LINK;
        if (k % 2 === 0) {
          canvas.save();
          canvas.translate(cx, cy);
          canvas.rotate((Math.atan2(uy, ux) * 180) / Math.PI, 0, 0);
          const oval = Skia.XYWHRect(-4.6, -2.6, 9.2, 5.2);
          canvas.drawOval(oval, linkFace);
          canvas.drawArc(Skia.XYWHRect(-4.0, -2.1, 8, 4.2), 200, 90, false, linkShine);
          canvas.restore();
        } else {
          canvas.drawLine(cx - ux * 3.6, cy - uy * 3.6, cx + ux * 3.6, cy + uy * 3.6, linkEdge);
        }
      }
    }

    // The clear acrylic panel: barely there, but its edges carry the neon's light.
    const panelRect = Skia.RRectXY(Skia.XYWHRect(panel.x, panel.y, panel.w, panel.h), PANEL_RADIUS, PANEL_RADIUS);
    canvas.drawRRect(panelRect, fill('#ffffff', 0.035));
    const inner = Skia.RRectXY(Skia.XYWHRect(panel.x + 3, panel.y + 3, panel.w - 6, panel.h - 6), PANEL_RADIUS - 3, PANEL_RADIUS - 3);
    canvas.drawRRect(inner, stroke('#ffffff', 0.7, 0.08));
    const edge = Skia.Paint();
    edge.setAntiAlias(true);
    edge.setStyle(PaintStyle.Stroke);
    edge.setStrokeWidth(1.4);
    edge.setColor(Float32Array.of(mix(0.8, tint[0], glowAmount), mix(0.82, tint[1], glowAmount), mix(0.86, tint[2], glowAmount), 0.16 + 0.5 * glowAmount));
    canvas.drawRRect(panelRect, edge);
    // The holes the chains go through, with steel grommets.
    for (const [ax, ay] of anchors) {
      canvas.drawCircle(ax, ay, 4.2, fill('#0a0a0c', 0.9));
      canvas.drawCircle(ax, ay, 4.2, stroke('#9a9ca2', 1.3, 0.9));
      canvas.drawLine(ax, ay - 4.2, ax, ay - 1, stroke('#b9bbc0', 2, 1));
    }

    // Standoffs under the tubes.
    for (const [mx, my] of scene.mounts) {
      canvas.drawCircle(mx, my, 4.4, fill('#c9d2da', 0.16));
      canvas.drawCircle(mx, my, 4.4, stroke('#ffffff', 0.6, 0.25));
    }

    // The tubes: coated glass, the lit gas inside it, its white-hot centre and a glint on the glass.
    const glass = stroke('#000000', TUBE);
    const core = stroke('#000000', CORE_W);
    const hot = stroke('#000000', HOT_W);
    const glint = stroke('#ffffff', 0.9);
    const n = scene.pieces.length;
    for (let i = 0; i < n; i += 1) {
      const piece = scene.pieces[i];
      const l = st.level[i];
      const unlit = Skia.Color(UNLIT[piece.tone]);
      const lit = Skia.Color(CORE[piece.tone]);
      glass.setColor(unlit);
      core.setColor(Float32Array.of(mix(unlit[0], lit[0], l), mix(unlit[1], lit[1], l), mix(unlit[2], lit[2], l), 1));
      hot.setColor(Skia.Color(HOT[piece.tone]));
      hot.setAlphaf(Math.min(1, l * l * 1.1));
      glint.setAlphaf(0.34 * (1 - 0.75 * l));
      const paths = scene.paths[i];
      for (const path of paths) {
        canvas.drawPath(path, glass);
        canvas.drawPath(path, core);
        if (l > 0.02) canvas.drawPath(path, hot);
      }
      canvas.save();
      canvas.translate(-0.9, -1.2);
      for (const path of paths) canvas.drawPath(path, glint);
      canvas.restore();
    }
    canvas.drawPath(scene.caps, stroke('#141216', TUBE + 0.4));

    // The halos, added on top as light.
    const halo = Skia.Paint();
    halo.setBlendMode(BlendMode.Plus);
    for (let i = 0; i < n; i += 1) {
      const l = st.level[i];
      if (l < 0.01) continue;
      const g = baked.glows[i];
      halo.setAlphaf(Math.min(1, Math.pow(l, 1.4)));
      canvas.drawImageRectOptions(
        g.image,
        Skia.XYWHRect(0, 0, g.image.width(), g.image.height()),
        Skia.XYWHRect(g.x, g.y, g.w, g.h),
        FilterMode.Linear,
        MipmapMode.None,
        halo,
      );
    }
    canvas.restore();

    // The hook: a small iron plate screwed to the wall, and the ring the chains hang from.
    const plate = Skia.RRectXY(Skia.XYWHRect(pivotX - 18, pivotY - 13, 36, 12), 3, 3);
    canvas.drawRRect(Skia.RRectXY(Skia.XYWHRect(pivotX - 18, pivotY - 10, 36, 12), 3, 3), fill('#000000', 0.5, 3));
    const iron = Skia.Paint();
    iron.setAntiAlias(true);
    iron.setShader(
      Skia.Shader.MakeLinearGradient(
        Skia.Point(0, pivotY - 13),
        Skia.Point(0, pivotY - 1),
        [Skia.Color('#4a4b50'), Skia.Color('#26272b'), Skia.Color('#151518')],
        [0, 0.5, 1],
        TileMode.Clamp,
      ),
    );
    canvas.drawRRect(plate, iron);
    for (const sx of [-11, 11]) {
      canvas.drawCircle(pivotX + sx, pivotY - 7, 2.2, fill('#6b6d72'));
      canvas.drawLine(pivotX + sx - 1.5, pivotY - 7, pivotX + sx + 1.5, pivotY - 7, stroke('#1a1a1d', 0.8));
    }
    canvas.drawCircle(pivotX, pivotY - 1, 4.6, stroke('#8c8f95', 2));
    canvas.drawArc(Skia.XYWHRect(pivotX - 4.6, pivotY - 5.6, 9.2, 9.2), 200, 80, false, stroke('#ffffff', 0.7, 0.5));
    return recorder.finishRecordingAsPicture();
  });

  const touched = () => {
    'worklet';
    const st = sim.get();
    if (st) st.hold = USER_HOLD;
  };

  const release = (spin: number) => {
    'worklet';
    const st = sim.get();
    if (!st || !st.dragging) return;
    st.dragging = false;
    st.omega = Math.max(-MAX_SPIN, Math.min(MAX_SPIN, spin));
    touched();
  };

  const pan = Gesture.Pan()
    .minDistance(4)
    // One finger swings the sign: a second one would move the averaged point and jerk it.
    .maxPointers(1)
    .onStart((event) => {
      const st = sim.get();
      if (!st || reducedMotion) return;
      if (Math.hypot(event.x - pivotX, event.y - pivotY) < 40) return;
      touched();
      st.dragging = true;
      st.omega = 0;
      grabTheta.set(st.theta);
      grabPhi.set(angleAround(event.x, event.y, pivotX, pivotY));
    })
    .onUpdate((event) => {
      const st = sim.get();
      if (!st || !st.dragging) return;
      let d = angleAround(event.x, event.y, pivotX, pivotY) - grabPhi.get();
      if (d > Math.PI) d -= 2 * Math.PI;
      if (d < -Math.PI) d += 2 * Math.PI;
      const raw = grabTheta.get() + d;
      const over = Math.abs(raw) - MAX_SWING;
      st.theta = over > 0 ? Math.sign(raw) * (MAX_SWING + over * RUBBER) : raw;
    })
    .onEnd((event) => {
      const rx = event.x - pivotX;
      const ry = event.y - pivotY;
      release((rx * event.velocityY - ry * event.velocityX) / Math.max(rx * rx + ry * ry, 900));
    })
    .onFinalize(() => release(0));

  const tap = Gesture.Tap()
    .maxDuration(350)
    .onEnd((event, success) => {
      const st = sim.get();
      if (!success || !st) return;
      // The finger in the sign's frame.
      const dx = event.x - pivotX;
      const dy = event.y - pivotY;
      const c = Math.cos(st.theta);
      const s = Math.sin(st.theta);
      const lx = pivotLX + c * dx + s * dy;
      const ly = pivotLY - s * dx + c * dy;
      let hit = -1;
      let best = Infinity;
      for (let i = 0; i < scene.pieces.length; i += 1) {
        const [bx, by, bw, bh] = scene.pieces[i].box;
        const ox = Math.max(bx - lx, 0, lx - (bx + bw));
        const oy = Math.max(by - ly, 0, ly - (by + bh));
        const off = Math.hypot(ox, oy);
        if (off < 14 && off < best) {
          best = off;
          hit = scene.pieces[i].word;
        }
      }
      if (hit < 0) return;
      touched();
      setWord(st, hit, st.on[hit] === 1 ? 0 : 1);
      scheduleOnRN(toggled);
    });

  return (
    <GestureDetector gesture={Gesture.Race(pan, tap)}>
      <View
        style={styles.screen}
        accessible
        accessibilityRole="adjustable"
        accessibilityLabel={`Neon sign: ${WORD_NAMES.join(', ')}. Tap a word to switch it, drag to swing the sign.`}
        onLayout={(event: LayoutChangeEvent) => {
          const { width: w, height: h } = event.nativeEvent.layout;
          // Ignore empty and repeated layouts, so the images are not baked at 0 × 0 or twice.
          if (w < 1 || h < 1) return;
          setSize((prev) => (prev && prev.width === w && prev.height === h ? prev : { width: w, height: h }));
        }}
      >
        {size && (
          <Canvas style={StyleSheet.absoluteFill}>
            <Picture picture={picture} />
          </Canvas>
        )}
        <Text pointerEvents="none" style={[styles.hint, { bottom: insets.bottom + 22 }]}>
          Tap a word to switch it · Drag to swing
        </Text>
      </View>
    </GestureDetector>
  );
}

const styles = StyleSheet.create({
  screen: {
    flex: 1,
    backgroundColor: '#0c0908',
    overflow: 'hidden',
  },
  hint: {
    position: 'absolute',
    left: 24,
    right: 24,
    textAlign: 'center',
    fontFamily: FAMILY,
    fontSize: 13,
    fontWeight: '500',
    letterSpacing: 0.3,
    color: '#7d6e66',
  },
});
