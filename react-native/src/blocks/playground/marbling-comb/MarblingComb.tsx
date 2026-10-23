import {
  BlendMode,
  BlurStyle,
  Canvas,
  ClipOp,
  Picture,
  PaintStyle,
  Skia,
  StrokeCap,
  type SkCanvas,
  type SkPaint,
  type SkPath,
  type SkPicture,
} from '@shopify/react-native-skia';
import { BlurView } from 'expo-blur';
import * as Haptics from 'expo-haptics';
import { useEffect, useState } from 'react';
import { type LayoutChangeEvent, Pressable, StyleSheet, Text, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import { useDerivedValue, useFrameCallback, useReducedMotion, useSharedValue } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

import {
  clearMarble,
  createMarble,
  crossed,
  dropPaint,
  easeInOut,
  LOOP,
  type Marble,
  moveTool,
  PRINT_AT,
  SCRIPT_DROPS,
  SCRIPT_PASSES,
  stepMarble,
  type Tool,
} from './marblingSim';

type Props = {
  /** Drops rings, combs a nonpareil and prints it, on a loop. */
  autoPlay?: boolean;
  /** The size (index 0, the ground) and the paints, as hex colours. */
  palette?: string[];
};

// Ground first, then the paints: an ebru palette of lapis, bone, madder, ochre, cobalt, rose, sage and ink.
const PALETTE = ['#16243c', '#efe4ca', '#b5432f', '#d99a36', '#3d68aa', '#7e9b83', '#e1a693', '#1d1a17'];
const PAPER = '#f2ebdc';
const INK = '#f4ecdb';

// The tick every step of the paint runs on, s, and at most this many per frame (a 1/30 s cap).
const TICK = 1 / 60;
const MAX_TICKS = 2;
// A tool moves through the paint in steps no longer than this, pt.
const SUBSTEP = 6;

// A tapped drop spreads to DROP_RADIUS pt over DROP_TIME s (DROP_TIME_REDUCED with reduced motion).
const DROP_RADIUS = 46;
const DROP_TIME = 0.9;
const DROP_TIME_REDUCED = 0.22;
// Holding a finger still drips a drop every DRIP_EVERY s, cycling the paints, for rings.
const DRIP_EVERY = 0.34;
const DRIP_RADIUS = 30;

// The stylus: one tine. The comb: tines COMB_SPACING pt apart across the whole tray.
const STYLUS: Tool = { spacing: 0, side: 0, axisX: 1, axisY: 0, sharpness: 10, gain: 0.85, wake: 60 };
const COMB_SPACING = 30;
const COMB_SHARPNESS = 6.5;
const COMB_GAIN = 0.78;
const COMB_WAKE = 44;

// The print: the sheet comes down over LAY s, soaks for SOAK s, then peels from its lower right
// corner over PEEL s, pausing about two thirds of the way to show the print on its back.
const LAY = 0.8;
const SOAK = 0.5;
const PEEL = 2.7;
const PRINT_TIME = LAY + SOAK + PEEL;
// Sheet margins, pt from the screen (top and bottom come from the safe area and the controls).
const SHEET_SIDE = 18;
const SHEET_TOP = 64;
const SHEET_BOTTOM = 148;
// Paint seen through the wet sheet before it is lifted.
const SOAK_GHOST = 0.2;
// Paint left on the size after a print, fading over RESIDUE_FADE s.
const RESIDUE = 0.14;
const RESIDUE_FADE = 0.7;

// A touch pauses autoPlay this long, s.
const USER_HOLD = 4;

const TOOLS = ['Stylus', 'Comb'] as const;

// Either shader is null only if it fails to compile: its layer is then left out.
const WATER = Skia.RuntimeEffect.Make(`
uniform float2 size;
uniform float phase;

float hash(float2 p) {
  float3 q = fract(float3(p.x, p.y, p.x) * 0.1031);
  q += dot(q, q.yzx + 33.33);
  return fract((q.x + q.y) * q.z);
}

half4 main(float2 p) {
  float2 uv = p / size;
  // A broad sheen of light on the size, drifting slowly, with a brighter core.
  float band = uv.x * 0.62 + uv.y * 0.42 - 0.36 - 0.05 * sin(phase);
  float sheen = exp(-band * band / 0.02) * 0.075 + exp(-band * band / 0.0012) * 0.035;
  // Pigment grain, and a soft vignette towards the tray's rim.
  float grain = (hash(floor(p * 2.0)) - 0.5) * 0.05;
  float2 c = (uv - 0.5) * float2(1.0, size.y / size.x * 0.62);
  float shade = smoothstep(0.22, 0.86, length(c)) * 0.46;
  float light = sheen + grain;
  float w = max(light, 0.0);
  float k = max(-light, 0.0) + shade;
  return half4(w, w, w, w + (1.0 - w) * min(k, 1.0));
}
`);

const PAPER_GRAIN = Skia.RuntimeEffect.Make(`
uniform float strength;

float hash(float2 p) {
  float3 q = fract(float3(p.x, p.y, p.x) * 0.1031);
  q += dot(q, q.yzx + 33.33);
  return fract((q.x + q.y) * q.z);
}

float noise(float2 p) {
  float2 i = floor(p);
  float2 f = fract(p);
  f = f * f * (3.0 - 2.0 * f);
  float a = hash(i);
  float b = hash(i + float2(1.0, 0.0));
  float c = hash(i + float2(0.0, 1.0));
  float d = hash(i + float2(1.0, 1.0));
  return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
}

half4 main(float2 p) {
  // Fine tooth plus long, faint fibres.
  float tooth = hash(floor(p * 2.0)) - 0.5;
  float fibre = noise(float2(p.x * 0.06, p.y * 0.9)) - 0.5;
  float cloud = noise(p * 0.035) - 0.5;
  float v = (tooth * 0.5 + fibre * 0.7 + cloud * 0.9) * strength;
  float w = max(v, 0.0);
  float k = max(-v, 0.0);
  return half4(w, w, w, w + (1.0 - w) * k);
}
`);

/** `shadeHex` for the UI thread (darkens only), returning a colour string Skia reads. */
function shadeHexWorklet(hex: string, k: number) {
  'worklet';
  const n = parseInt(hex.slice(1), 16);
  const r = Math.round(((n >> 16) & 255) * k);
  const g = Math.round(((n >> 8) & 255) * k);
  const b = Math.round((n & 255) * k);
  return `rgb(${r},${g},${b})`;
}

type Scene = {
  marble: Marble;
  /** Each outline's path, with the outline revision it was built from. */
  paths: Record<number, { rev: number; path: SkPath }>;
  fills: SkPaint[];
  rims: SkPaint[];
  /** The paint as last drawn, and the revision of the marble it shows. */
  paint: SkPicture | null;
  paintRev: number;
  /** The paint taken up by the sheet. */
  print: SkPicture | null;
  /** The sheet's paper and its tooth, made once. */
  paper: SkPaint;
  grain: SkPaint;
};

/** The outline as a closed, smooth path: quadratic curves through the midpoints of its edges. */
function outlinePath(xs: number[], ys: number[]) {
  'worklet';
  const n = xs.length;
  const b = Skia.PathBuilder.Make();
  b.moveTo((xs[n - 1] + xs[0]) / 2, (ys[n - 1] + ys[0]) / 2);
  for (let i = 0; i < n; i += 1) {
    const j = i + 1 < n ? i + 1 : 0;
    b.quadTo(xs[i], ys[i], (xs[i] + xs[j]) / 2, (ys[i] + ys[j]) / 2);
  }
  b.close();
  return b.build();
}

/** Records the ground and every outline, oldest first, each with a fine darker rim. */
function recordPaint(s: Scene, width: number, height: number) {
  'worklet';
  const m = s.marble;
  const next: Record<number, { rev: number; path: SkPath }> = {};
  const recorder = Skia.PictureRecorder();
  const canvas = recorder.beginRecording(Skia.XYWHRect(0, 0, width, height));
  const colors = s.fills.length;
  canvas.drawRect(Skia.XYWHRect(0, 0, width, height), s.fills[m.ground % colors]);
  for (let b = 0; b < m.blots.length; b += 1) {
    const blot = m.blots[b];
    if (blot.x.length < 3) continue;
    let cached = s.paths[blot.id];
    if (!cached || cached.rev !== blot.rev) cached = { rev: blot.rev, path: outlinePath(blot.x, blot.y) };
    next[blot.id] = cached;
    canvas.drawPath(cached.path, s.fills[blot.color % colors]);
    canvas.drawPath(cached.path, s.rims[blot.color % colors]);
  }
  s.paths = next;
  s.paint = recorder.finishRecordingAsPicture();
  s.paintRev = m.rev;
}

/**
 * The sheet's rectangle clipped to one side of the fold line: the part already lifted (s < f, where
 * s runs along `d` from the lower right corner) or the part still down. With `mirror`, the lifted
 * part is folded over the line, which is where the flap lies.
 */
function sheetPart(l: number, t: number, r: number, b: number, dx: number, dy: number, f: number, lifted: boolean, mirror: boolean) {
  'worklet';
  const xs = [r, l, l, r];
  const ys = [b, b, t, t];
  const sides: number[] = [];
  for (let k = 0; k < 4; k += 1) {
    const s = (xs[k] - r) * dx + (ys[k] - b) * dy - f;
    sides.push(lifted ? -s : s);
  }
  const px: number[] = [];
  const py: number[] = [];
  for (let k = 0; k < 4; k += 1) {
    const j = (k + 1) % 4;
    const a = sides[k];
    const c = sides[j];
    if (a >= 0) {
      px.push(xs[k]);
      py.push(ys[k]);
    }
    if (a >= 0 !== c >= 0) {
      const u = a / (a - c);
      px.push(xs[k] + (xs[j] - xs[k]) * u);
      py.push(ys[k] + (ys[j] - ys[k]) * u);
    }
  }
  if (px.length < 3) return null;
  const out = Skia.PathBuilder.Make();
  for (let k = 0; k < px.length; k += 1) {
    let x = px[k];
    let y = py[k];
    if (mirror) {
      // Reflect across the fold: s' = 2f − s.
      const s = (x - r) * dx + (y - b) * dy;
      x += 2 * (f - s) * dx;
      y += 2 * (f - s) * dy;
    }
    if (k === 0) out.moveTo(x, y);
    else out.lineTo(x, y);
  }
  out.close();
  return out.build();
}

/** The fold's travel along the sheet's diagonal (0…1+) at `p` = 0…1 through the peel, with a pause to show the print. */
function peelAt(p: number) {
  'worklet';
  if (p < 0.42) return 0.5 * easeInOut(p / 0.42);
  if (p < 0.7) return 0.5 + 0.05 * ((p - 0.42) / 0.28);
  const k = (p - 0.7) / 0.3;
  return 0.55 + 0.65 * k * k;
}

/** Fills a path with the paper and its grain. */
function drawPaper(canvas: SkCanvas, clip: SkPath, paper: SkPaint, grain: SkPaint) {
  'worklet';
  canvas.save();
  canvas.clipPath(clip, ClipOp.Intersect, true);
  canvas.drawPaint(paper);
  canvas.drawPaint(grain);
  canvas.restore();
}

/** The stylus, a steel pin seen from above, with its shadow and the meniscus round it. */
function drawStylus(canvas: SkCanvas, x: number, y: number, alpha: number) {
  'worklet';
  const shadow = Skia.Paint();
  shadow.setColor(Skia.Color(`rgba(0,0,0,${0.4 * alpha})`));
  shadow.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 3.5, true));
  canvas.drawCircle(x + 4, y + 7, 6, shadow);
  const ring = Skia.Paint();
  ring.setStyle(PaintStyle.Stroke);
  ring.setStrokeWidth(1.2);
  ring.setColor(Skia.Color(`rgba(255,255,255,${0.28 * alpha})`));
  canvas.drawCircle(x, y, 10, ring);
  const body = Skia.Paint();
  body.setShader(
    Skia.Shader.MakeRadialGradient({ x: x - 2, y: y - 2 }, 8, [Skia.Color('#ffffff'), Skia.Color('#b9bec6'), Skia.Color('#5d636d')], [0, 0.45, 1], 0),
  );
  body.setAlphaf(alpha);
  canvas.drawCircle(x, y, 5.5, body);
}

/** The comb: a walnut rod across the tray along its axis, its steel tines in the size, and its shadow. */
function drawComb(canvas: SkCanvas, x: number, y: number, ax: number, ay: number, spacing: number, side: number, alpha: number) {
  'worklet';
  const reach = spacing * (side + 0.5);
  const angle = (Math.atan2(ay, ax) * 180) / Math.PI;
  canvas.save();
  canvas.translate(x, y);
  canvas.rotate(angle, 0, 0);
  const shadow = Skia.Paint();
  shadow.setColor(Skia.Color(`rgba(0,0,0,${0.42 * alpha})`));
  shadow.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 5, true));
  canvas.drawRect(Skia.XYWHRect(-reach + 5, -2, reach * 2, 12), shadow);
  const tine = Skia.Paint();
  tine.setAlphaf(alpha);
  const wake = Skia.Paint();
  wake.setStyle(PaintStyle.Stroke);
  wake.setStrokeWidth(1);
  wake.setColor(Skia.Color(`rgba(255,255,255,${0.22 * alpha})`));
  for (let j = -side; j <= side; j += 1) {
    canvas.drawCircle(j * spacing, 0, 4.5, wake);
  }
  const rod = Skia.Paint();
  rod.setShader(
    Skia.Shader.MakeLinearGradient({ x: 0, y: -5 }, { x: 0, y: 5 }, [Skia.Color('#8a5d38'), Skia.Color('#5b3a1f'), Skia.Color('#2f1d0e')], [0, 0.45, 1], 0),
  );
  rod.setAlphaf(alpha);
  canvas.drawRRect(Skia.RRectXY(Skia.XYWHRect(-reach, -9, reach * 2, 8), 3, 3), rod);
  const shine = Skia.Paint();
  shine.setColor(Skia.Color(`rgba(255,228,190,${0.35 * alpha})`));
  shine.setStrokeWidth(1);
  shine.setStrokeCap(StrokeCap.Round);
  canvas.drawLine(-reach + 4, -7.6, reach - 4, -7.6, shine);
  // Each tine catches the light on its own upper left.
  for (let j = -side; j <= side; j += 1) {
    tine.setShader(
      Skia.Shader.MakeRadialGradient({ x: j * spacing - 0.6, y: -0.6 }, 2.6, [Skia.Color('#ffffff'), Skia.Color('#8c929b')], [0, 1], 0),
    );
    canvas.drawCircle(j * spacing, 0, 2.2, tine);
  }
  canvas.restore();
}

/**
 * Paper marbling (ebru). Paint dropped on size spreads into rings, pushing every colour already
 * floating outwards so the area of each is kept; a stylus or a comb dragged through the paint pulls
 * it into feathered, combed patterns. Print lays a sheet over the tray and peels it back to show
 * the pattern taken up on its underside, leaving the size clean.
 */
export function MarblingComb({ autoPlay = false, palette = PALETTE }: Props) {
  const insets = useSafeAreaInsets();
  const reducedMotion = useReducedMotion();
  const auto = autoPlay && !reducedMotion;
  const [size, setSize] = useState({ width: 0, height: 0 });
  const [tool, setTool] = useState(0);
  const [ink, setInk] = useState(2);
  const [printing, setPrinting] = useState(false);
  const { width, height } = size;
  const sheetTop = insets.top + SHEET_TOP;
  const sheetBottom = height - insets.bottom - SHEET_BOTTOM;

  const scene = useSharedValue<Scene | null>(null);
  const tick = useSharedValue(0);
  const elapsed = useSharedValue(0);
  const acc = useSharedValue(0);

  // The tool in the paint: where it was last applied, where the finger (or the script) has it.
  const toolKind = useSharedValue(0);
  const toolOn = useSharedValue(false);
  const toolX = useSharedValue(0);
  const toolY = useSharedValue(0);
  const appliedX = useSharedValue(0);
  const appliedY = useSharedValue(0);
  const toolAlpha = useSharedValue(0);
  // The comb's axis and its tines: set at the start of each stroke.
  const combAX = useSharedValue(1);
  const combAY = useSharedValue(0);
  const combSpacing = useSharedValue(COMB_SPACING);
  const combSide = useSharedValue(8);
  const combSharp = useSharedValue(COMB_SHARPNESS);
  const combGain = useSharedValue(COMB_GAIN);
  const combWake = useSharedValue(COMB_WAKE);
  const scripted = useSharedValue(false);

  const inkIndex = useSharedValue(2);
  const dripOn = useSharedValue(false);
  const dripX = useSharedValue(0);
  const dripY = useSharedValue(0);
  const dripTime = useSharedValue(0);
  const dripColor = useSharedValue(2);

  // The print: < 0 when no sheet is down, else seconds since it started.
  const printT = useSharedValue(-1);
  const residue = useSharedValue(0);

  const clock = useSharedValue(0);
  const hold = useSharedValue(0);
  const looping = useSharedValue(auto);

  useEffect(() => {
    looping.set(auto);
    clock.set(0);
    hold.set(0);
  }, [auto, clock, hold, looping]);

  useEffect(() => {
    inkIndex.set(ink);
  }, [ink, inkIndex]);

  useEffect(() => {
    toolKind.set(tool);
  }, [tool, toolKind]);

  const onLayout = (e: LayoutChangeEvent) => {
    const { width: w, height: h } = e.nativeEvent.layout;
    setSize({ width: w, height: h });
  };

  const dropped = () => {
    if (process.env.EXPO_OS !== 'web') Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
  };
  const stroked = () => {
    if (process.env.EXPO_OS !== 'web') Haptics.selectionAsync().catch(() => {});
  };
  const pressed = () => {
    if (process.env.EXPO_OS !== 'web') Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Medium).catch(() => {});
  };
  const printDone = () => setPrinting(false);
  const printStart = () => setPrinting(true);

  /** Lays the sheet down: the print runs from here on the shared tick. */
  const startPrint = () => {
    'worklet';
    if (printT.get() >= 0) return;
    printT.set(0);
    toolOn.set(false);
    dripOn.set(false);
    scheduleOnRN(printStart);
  };

  /** Moves the tool to (x, y) through the paint in short steps. */
  const applyTool = (m: Marble, x: number, y: number) => {
    'worklet';
    const fx = appliedX.get();
    const fy = appliedY.get();
    const len = Math.hypot(x - fx, y - fy);
    if (len < 0.05) return;
    const steps = Math.min(400, Math.max(1, Math.ceil(len / SUBSTEP)));
    const comb = toolKind.get() === 1;
    const t: Tool = comb
      ? {
          spacing: combSpacing.get(),
          side: combSide.get(),
          axisX: combAX.get(),
          axisY: combAY.get(),
          sharpness: combSharp.get(),
          gain: combGain.get(),
          wake: combWake.get(),
        }
      : STYLUS;
    let px = fx;
    let py = fy;
    for (let i = 1; i <= steps; i += 1) {
      const nx = fx + ((x - fx) * i) / steps;
      const ny = fy + ((y - fy) * i) / steps;
      moveTool(m, t, px, py, nx, ny);
      px = nx;
      py = ny;
    }
    appliedX.set(x);
    appliedY.set(y);
  };

  /** One step of the autoPlay script, from `before` to `t` seconds into the loop. */
  const script = (m: Marble, before: number, t: number) => {
    'worklet';
    const w = m.width;
    const h = m.height;
    for (let k = 0; k < SCRIPT_DROPS.length; k += 1) {
      const d = SCRIPT_DROPS[k];
      if (crossed(before, t, d.at)) dropPaint(m, d.x * w, d.y * h, d.r * w, d.color, DROP_TIME);
    }
    let active = -1;
    for (let k = 0; k < SCRIPT_PASSES.length; k += 1) {
      const p = SCRIPT_PASSES[k];
      if (t >= p.from && t <= p.to) active = k;
    }
    if (active >= 0) {
      const p = SCRIPT_PASSES[active];
      const e = easeInOut((t - p.from) / (p.to - p.from));
      const x = (p.x0 + (p.x1 - p.x0) * e) * w;
      const y = (p.y0 + (p.y1 - p.y0) * e) * h;
      if (!toolOn.get() || !scripted.get()) {
        scripted.set(true);
        toolOn.set(true);
        toolKind.set(1);
        combAX.set(p.horizontal ? 1 : 0);
        combAY.set(p.horizontal ? 0 : 1);
        combSpacing.set(p.spacing * w);
        combSide.set(Math.ceil((p.horizontal ? w : h) / (p.spacing * w)) + 1);
        combSharp.set(p.sharpness);
        combGain.set(p.gain);
        combWake.set(p.wake);
        appliedX.set(x);
        appliedY.set(y);
      }
      toolX.set(x);
      toolY.set(y);
    } else if (scripted.get()) {
      scripted.set(false);
      toolOn.set(false);
      toolKind.set(tool);
    }
    if (crossed(before, t, PRINT_AT)) startPrint();
  };

  useFrameCallback((frame) => {
    if (width === 0) return;
    let s = scene.get();
    if (s === null) {
      const fills: SkPaint[] = [];
      const rims: SkPaint[] = [];
      for (let k = 0; k < palette.length; k += 1) {
        const fill = Skia.Paint();
        fill.setAntiAlias(true);
        fill.setColor(Skia.Color(palette[k]));
        fills.push(fill);
        const rim = Skia.Paint();
        rim.setAntiAlias(true);
        rim.setStyle(PaintStyle.Stroke);
        rim.setStrokeWidth(0);
        rim.setColor(Skia.Color(shadeHexWorklet(palette[k], 0.62)));
        rim.setAlphaf(0.85);
        rims.push(rim);
      }
      const paper = Skia.Paint();
      paper.setColor(Skia.Color(PAPER));
      const grain = Skia.Paint();
      if (PAPER_GRAIN) grain.setShader(PAPER_GRAIN.makeShader([0.09]));
      else grain.setColor(Skia.Color('rgba(0,0,0,0)'));
      s = { marble: createMarble(width, height, 0), paths: {}, fills, rims, paint: null, paintRev: -1, print: null, paper, grain };
      scene.set(s);
    }
    const m = s.marble;
    // The tray follows the layout (a rotation or a resize keeps the paint where it is).
    m.width = width;
    m.height = height;
    const dt = Math.min((frame.timeSincePreviousFrame ?? 16) / 1000, MAX_TICKS * TICK);
    let pending = acc.get() + dt;
    let ticks = 0;
    while (pending >= TICK && ticks < MAX_TICKS) {
      pending -= TICK;
      ticks += 1;
      elapsed.set(elapsed.get() + TICK);

      if (hold.get() > 0) {
        hold.set(hold.get() - TICK);
        if (hold.get() <= 0) clock.set(0);
      } else if (looping.get()) {
        const before = clock.get();
        const t = (before + TICK) % LOOP;
        clock.set(t);
        script(m, before, t);
      }

      if (toolOn.get()) applyTool(m, toolX.get(), toolY.get());

      if (dripOn.get()) {
        dripTime.set(dripTime.get() + TICK);
        if (dripTime.get() >= DRIP_EVERY) {
          dripTime.set(dripTime.get() - DRIP_EVERY);
          const c = dripColor.get();
          dropPaint(m, dripX.get(), dripY.get(), DRIP_RADIUS, c, reducedMotion ? DROP_TIME_REDUCED : DROP_TIME * 0.8);
          dripColor.set((c % Math.max(1, palette.length - 1)) + 1);
          scheduleOnRN(dropped);
        }
      }

      stepMarble(m, TICK);

      // The print: the sheet takes up the paint the moment it lies flat.
      const p = printT.get();
      if (p >= 0) {
        const next = p + TICK;
        if (p < LAY && next >= LAY) {
          if (s.paint === null || s.paintRev !== m.rev) recordPaint(s, width, height);
          s.print = s.paint;
          clearMarble(m, 0);
          residue.set(1);
          scheduleOnRN(pressed);
        }
        if (next >= PRINT_TIME) {
          printT.set(-1);
          scheduleOnRN(printDone);
        } else printT.set(next);
      }
      if (residue.get() > 0 && p < 0) residue.set(Math.max(0, residue.get() - TICK / RESIDUE_FADE));
    }
    acc.set(Math.min(pending, TICK));
    toolAlpha.set(toolAlpha.get() + ((toolOn.get() ? 1 : 0) - toolAlpha.get()) * Math.min(1, dt * 14));
    if (s.paint === null || s.paintRev !== m.rev) recordPaint(s, width, height);
    tick.set(tick.get() + 1);
  });

  const touched = () => {
    'worklet';
    hold.set(USER_HOLD);
    if (scripted.get()) {
      scripted.set(false);
      toolOn.set(false);
    }
  };

  const pan = Gesture.Pan()
    .maxPointers(1)
    .minDistance(6)
    .onStart((e) => {
      if (printT.get() >= 0) return;
      touched();
      const x0 = e.x - e.translationX;
      const y0 = e.y - e.translationY;
      toolKind.set(tool);
      // The comb lies across the stroke: drawn sideways, its axis runs up and down.
      const sideways = Math.abs(e.translationX) > Math.abs(e.translationY);
      combAX.set(sideways ? 0 : 1);
      combAY.set(sideways ? 1 : 0);
      combSpacing.set(COMB_SPACING);
      combSide.set(Math.ceil(Math.max(width, height) / COMB_SPACING) + 1);
      combSharp.set(COMB_SHARPNESS);
      combGain.set(COMB_GAIN);
      combWake.set(COMB_WAKE);
      appliedX.set(x0);
      appliedY.set(y0);
      toolX.set(e.x);
      toolY.set(e.y);
      toolOn.set(true);
      scheduleOnRN(stroked);
    })
    .onUpdate((e) => {
      if (!toolOn.get() || scripted.get()) return;
      toolX.set(e.x);
      toolY.set(e.y);
    })
    .onFinalize(() => {
      if (!scripted.get()) toolOn.set(false);
    });

  const tap = Gesture.Tap()
    .maxDuration(280)
    .maxDistance(10)
    .onEnd((e, success) => {
      const s = scene.get();
      if (!success || !s || printT.get() >= 0) return;
      touched();
      dropPaint(s.marble, e.x, e.y, DROP_RADIUS, inkIndex.get(), reducedMotion ? DROP_TIME_REDUCED : DROP_TIME);
      scheduleOnRN(dropped);
    });

  const drip = Gesture.LongPress()
    .minDuration(300)
    .maxDistance(12)
    .onStart((e) => {
      if (printT.get() >= 0) return;
      touched();
      dripX.set(e.x);
      dripY.set(e.y);
      dripColor.set(inkIndex.get());
      // The first drop lands now; the rest follow every DRIP_EVERY s.
      dripTime.set(DRIP_EVERY);
      dripOn.set(true);
    })
    .onFinalize(() => {
      dripOn.set(false);
    });

  const picture = useDerivedValue(() => {
    tick.get();
    const recorder = Skia.PictureRecorder();
    const canvas = recorder.beginRecording(Skia.XYWHRect(0, 0, width, height));
    const s = scene.get();
    if (!s || width === 0 || !s.paint) {
      const bg = Skia.Paint();
      bg.setColor(Skia.Color(palette[0]));
      canvas.drawRect(Skia.XYWHRect(0, 0, width, height), bg);
      return recorder.finishRecordingAsPicture();
    }
    canvas.drawPicture(s.paint);

    // What the last print left behind on the size, fading.
    const left = residue.get();
    if (left > 0 && s.print) {
      const fade = Skia.Paint();
      fade.setAlphaf(RESIDUE * left);
      canvas.saveLayer(fade);
      canvas.drawPicture(s.print);
      canvas.restore();
    }

    // Light on the size: sheen, grain and a vignette.
    if (WATER) {
      const water = Skia.Paint();
      // The sheen drifts once per loop, so a recording of one loop joins up.
      water.setShader(WATER.makeShader([width, height, ((elapsed.get() % LOOP) / LOOP) * Math.PI * 2]));
      canvas.drawRect(Skia.XYWHRect(0, 0, width, height), water);
    }

    // The tool.
    const a = toolAlpha.get();
    if (a > 0.01) {
      if (toolKind.get() === 1) {
        drawComb(canvas, toolX.get(), toolY.get(), combAX.get(), combAY.get(), combSpacing.get(), combSide.get(), a);
      } else drawStylus(canvas, toolX.get(), toolY.get(), a);
    }

    // The sheet.
    const p = printT.get();
    if (p >= 0) {
      const l = SHEET_SIDE;
      const r = width - SHEET_SIDE;
      const t = sheetTop;
      const b = sheetBottom;
      const paper = s.paper;
      const grain = s.grain;
      const shadow = Skia.Paint();
      if (p < LAY) {
        // Coming down: from a little above and larger, its shadow tightening as it lands.
        const k = easeInOut(p / LAY);
        const lift = reducedMotion ? 0 : 1 - k;
        const scale = 1 + 0.06 * lift;
        const cx = (l + r) / 2;
        const cy = (t + b) / 2;
        const sheet = Skia.PathBuilder.Make().addRRect(Skia.RRectXY(Skia.XYWHRect(l, t, r - l, b - t), 2, 2)).build();
        canvas.save();
        canvas.translate(cx, cy - 26 * lift);
        canvas.scale(scale, scale);
        canvas.translate(-cx, -cy);
        shadow.setColor(Skia.Color(`rgba(0,0,0,${0.45 * Math.min(1, k * 2)})`));
        shadow.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 2 + 18 * lift, true));
        canvas.save();
        canvas.translate(0, 3 + 34 * lift);
        canvas.drawPath(sheet, shadow);
        canvas.restore();
        const fade = Skia.Paint();
        fade.setAlphaf(Math.min(1, k * 2.5));
        canvas.saveLayer(fade);
        drawPaper(canvas, sheet, paper, grain);
        canvas.restore();
        canvas.restore();
      } else {
        const print = s.print;
        const soak = Math.min(1, (p - LAY) / SOAK);
        const peel = Math.max(0, (p - LAY - SOAK) / PEEL);
        const wx = l - r;
        const wy = t - b;
        const diag = Math.hypot(wx, wy) || 1;
        const dx = wx / diag;
        const dy = wy / diag;
        if (reducedMotion) {
          // No peel: the sheet fades to its print, then away.
          const show = Math.min(1, peel * 3);
          const gone = Math.max(0, (peel - 0.75) / 0.25);
          const sheet = Skia.PathBuilder.Make().addRect(Skia.XYWHRect(l, t, r - l, b - t)).build();
          const fade = Skia.Paint();
          fade.setAlphaf(1 - gone);
          canvas.saveLayer(fade);
          drawPaper(canvas, sheet, paper, grain);
          if (print) {
            const ink2 = Skia.Paint();
            ink2.setAlphaf(Math.max(SOAK_GHOST * soak, show * 0.92));
            canvas.save();
            canvas.clipPath(sheet, ClipOp.Intersect, true);
            canvas.saveLayer(ink2);
            canvas.translate(l + r, 0);
            canvas.scale(-1, 1);
            canvas.drawPicture(print);
            canvas.restore();
            canvas.drawPaint(grain);
            canvas.restore();
          }
          canvas.restore();
        } else {
          const f = peelAt(peel) * diag;
          // Under the lifting edge, a soft shadow on the size.
          const rest = sheetPart(l, t, r, b, dx, dy, f, false, false);
          const lifted = sheetPart(l, t, r, b, dx, dy, f, true, false);
          if (lifted && f > 0) {
            const fx = r + dx * f;
            const fy = b + dy * f;
            const under = Skia.Paint();
            under.setShader(
              Skia.Shader.MakeLinearGradient(
                { x: fx, y: fy },
                { x: fx - dx * 46, y: fy - dy * 46 },
                [Skia.Color('rgba(0,0,0,0.38)'), Skia.Color('rgba(0,0,0,0)')],
                [0, 1],
                0,
              ),
            );
            canvas.save();
            canvas.clipPath(lifted, ClipOp.Intersect, true);
            canvas.drawPaint(under);
            canvas.restore();
          }
          // The sheet still lying on the size: its back, the paint showing through where it is wet.
          if (rest) {
            drawPaper(canvas, rest, paper, grain);
            if (print) {
              const ghost = Skia.Paint();
              ghost.setAlphaf(SOAK_GHOST * soak);
              canvas.save();
              canvas.clipPath(rest, ClipOp.Intersect, true);
              canvas.saveLayer(ghost);
              canvas.drawPicture(print);
              canvas.restore();
              canvas.restore();
            }
          }
          // The flap: the peeled part folded over the fold line, its printed face up.
          if (lifted && f > 0) {
            // Reflection across the fold line through F = corner + f·d, normal d.
            const fx = r + dx * f;
            const fy = b + dy * f;
            const fd = fx * dx + fy * dy;
            const mirror = Skia.Matrix([1 - 2 * dx * dx, -2 * dx * dy, 2 * fd * dx, -2 * dx * dy, 1 - 2 * dy * dy, 2 * fd * dy, 0, 0, 1]);
            const flap = sheetPart(l, t, r, b, dx, dy, f, true, true) ?? lifted;
            // Its shadow on the sheet below.
            const cast = Skia.Paint();
            cast.setColor(Skia.Color('rgba(40,26,10,0.32)'));
            cast.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 7, true));
            canvas.save();
            canvas.translate(dx * 7 + 2, dy * 7 + 5);
            canvas.drawPath(flap, cast);
            canvas.restore();
            canvas.save();
            canvas.clipPath(flap, ClipOp.Intersect, true);
            canvas.drawPaint(paper);
            if (print) {
              canvas.save();
              canvas.concat(mirror);
              canvas.drawPicture(print);
              canvas.restore();
            }
            // The paper's tooth through the print, and the light rolling over the fold.
            const tone = Skia.Paint();
            tone.setColor(Skia.Color('rgba(242,235,220,0.10)'));
            canvas.drawPaint(tone);
            canvas.drawPaint(grain);
            const curl = Skia.Paint();
            curl.setShader(
              Skia.Shader.MakeLinearGradient(
                { x: fx, y: fy },
                { x: fx + dx * 60, y: fy + dy * 60 },
                [Skia.Color('rgba(255,255,255,0.34)'), Skia.Color('rgba(255,255,255,0.06)'), Skia.Color('rgba(0,0,0,0.12)')],
                [0, 0.35, 1],
                0,
              ),
            );
            curl.setBlendMode(BlendMode.SrcOver);
            canvas.drawPaint(curl);
            canvas.restore();
          }
        }
      }
    }
    return recorder.finishRecordingAsPicture();
  });

  const hintTop = insets.top + 18;
  const swatches = palette.slice(1);

  return (
    <View style={[styles.root, { backgroundColor: palette[0] }]} onLayout={onLayout}>
      <GestureDetector gesture={Gesture.Race(pan, drip, tap)}>
        <View style={StyleSheet.absoluteFill}>
          <Canvas style={StyleSheet.absoluteFill}>
            <Picture picture={picture} />
          </Canvas>
        </View>
      </GestureDetector>

      <View pointerEvents="none" style={[styles.header, { top: hintTop }]}>
        <Text style={styles.title}>Ebru</Text>
        <Text style={styles.subtitle}>Tap to drop · hold for rings · drag to comb</Text>
      </View>

      <View pointerEvents={printing ? 'none' : 'box-none'} style={[styles.controls, { bottom: insets.bottom + 16, opacity: printing ? 0.5 : 1 }]}>
        <View style={styles.swatches}>
          {swatches.map((color, k) => {
            const index = k + 1;
            const on = index === ink;
            return (
              <Pressable
                key={color}
                accessibilityRole="button"
                accessibilityLabel={`Paint ${index}`}
                accessibilityState={{ selected: on }}
                hitSlop={6}
                onPress={() => {
                  setInk(index);
                  if (process.env.EXPO_OS !== 'web') Haptics.selectionAsync().catch(() => {});
                }}
                style={[styles.swatchRing, on && styles.swatchRingOn]}
              >
                <View style={[styles.swatch, { backgroundColor: color }]} />
              </Pressable>
            );
          })}
        </View>
        <View style={styles.bar}>
          <BlurView intensity={36} tint="dark" style={styles.segment}>
            {TOOLS.map((label, k) => (
              <Pressable
                key={label}
                accessibilityRole="button"
                accessibilityState={{ selected: tool === k }}
                onPress={() => {
                  setTool(k);
                  if (process.env.EXPO_OS !== 'web') Haptics.selectionAsync().catch(() => {});
                }}
                style={[styles.segmentItem, tool === k && styles.segmentOn]}
              >
                <Text style={[styles.segmentText, tool === k && styles.segmentTextOn]}>{label}</Text>
              </Pressable>
            ))}
          </BlurView>
          <Pressable
            accessibilityRole="button"
            accessibilityLabel="Print the pattern"
            onPress={() => {
              hold.set(USER_HOLD + PRINT_TIME);
              scripted.set(false);
              startPrint();
            }}
            style={({ pressed: down }) => [styles.print, down && styles.printDown]}
          >
            <Text style={styles.printText}>Print</Text>
          </Pressable>
        </View>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, overflow: 'hidden' },
  header: { position: 'absolute', left: 22, right: 22, gap: 3 },
  title: {
    color: INK,
    fontSize: 22,
    fontWeight: '600',
    letterSpacing: 0.3,
    textShadowColor: 'rgba(0,0,0,0.45)',
    textShadowRadius: 6,
    textShadowOffset: { width: 0, height: 1 },
  },
  subtitle: {
    color: INK,
    opacity: 0.78,
    fontSize: 12.5,
    fontWeight: '500',
    letterSpacing: 0.2,
    textShadowColor: 'rgba(0,0,0,0.5)',
    textShadowRadius: 5,
    textShadowOffset: { width: 0, height: 1 },
  },
  controls: { position: 'absolute', left: 16, right: 16, gap: 14, alignItems: 'center' },
  swatches: { flexDirection: 'row', gap: 10, alignItems: 'center' },
  swatchRing: {
    width: 30,
    height: 30,
    borderRadius: 15,
    alignItems: 'center',
    justifyContent: 'center',
    borderWidth: 1.5,
    borderColor: 'transparent',
  },
  swatchRingOn: { borderColor: 'rgba(244,236,219,0.9)' },
  swatch: {
    width: 20,
    height: 20,
    borderRadius: 10,
    boxShadow: '0 1px 3px rgba(0,0,0,0.45), inset 0 1px 1px rgba(255,255,255,0.35)',
  },
  bar: { flexDirection: 'row', gap: 10, alignItems: 'center' },
  segment: {
    flexDirection: 'row',
    padding: 4,
    borderRadius: 22,
    overflow: 'hidden',
    backgroundColor: 'rgba(14,20,32,0.35)',
  },
  segmentItem: { height: 36, paddingHorizontal: 18, borderRadius: 18, justifyContent: 'center' },
  segmentOn: { backgroundColor: 'rgba(244,236,219,0.92)' },
  segmentText: { color: 'rgba(244,236,219,0.8)', fontSize: 14, fontWeight: '600' },
  segmentTextOn: { color: '#16243c' },
  print: {
    height: 44,
    paddingHorizontal: 24,
    borderRadius: 22,
    justifyContent: 'center',
    backgroundColor: '#f2ebdc',
    boxShadow: '0 4px 14px rgba(0,0,0,0.35)',
    transform: [{ scale: 1 }],
  },
  printDown: { transform: [{ scale: 0.97 }] },
  printText: { color: '#16243c', fontSize: 15, fontWeight: '700', letterSpacing: 0.2 },
});

