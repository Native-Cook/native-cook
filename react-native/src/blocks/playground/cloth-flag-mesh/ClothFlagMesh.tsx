import {
  BlendMode,
  Canvas,
  matchFont,
  PaintStyle,
  Picture,
  Skia,
  type SkCanvas,
  type SkColor,
  type SkPoint,
  type SkVertices,
  TileMode,
  VertexMode,
} from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { useEffect, useMemo, useState } from 'react';
import { type LayoutChangeEvent, Pressable, StyleSheet, Text, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  useAnimatedStyle,
  useDerivedValue,
  useFrameCallback,
  useReducedMotion,
  useSharedValue,
  withSpring,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

import {
  type Air,
  blendCloth,
  type Cloth,
  computeNormals,
  copyCloth,
  createCloth,
  FLAG_H,
  FLAG_W,
  MESH_COLS,
  MESH_ROWS,
  stepCloth,
  stillCloth,
  WEAVE_SKSL,
  windKnots,
} from './clothFlagSim';

type Props = {
  /** Raises the wind, gusts, pulls a corner down and lets it fly, then stills, on a loop. */
  autoPlay?: boolean;
  /** The lines under the banner. */
  label?: string;
  title?: string;
  subtitle?: string;
};

// One fixed clock drives the cloth and the autoPlay script: ticks of 1/60 s, at most MAX_TICKS a
// frame. Every frame draws the cloth between its last two ticks, so it moves evenly at any
// refresh rate; if a frame runs late the cloth slows for a moment rather than jumping.
const TICK = 1 / 60;
const MAX_TICKS = 2;
// Steps of still air run once at the start to hang the banner at rest; in the last STILL_STEPS of
// them every point is stopped after each step, so it comes to a dead stop.
const SETTLE_STEPS = 420;
const STILL_STEPS = 180;

// Layout (pt): the pole's centre from the left, the banner's top below the safe area.
const POLE_X = 40;
// The pole fades over POLE_FADE pt and is gone CAPTION_CLEAR pt above the bottom inset.
const POLE_FADE = 110;
// The mesh rows that carry a ring round the pole.
const RING_ROWS = [0, 4, 9, MESH_ROWS - 1];
const CAPTION_CLEAR = 196;
const POLE_W = 6;
const FLAG_TOP = 100;
// The cloth's hoist sits just right of the pole's centre, on its rings.
const HOIST_GAP = 4;
// Perspective: depth (pt towards the viewer) seen from FOCAL pt away.
const FOCAL = 1100;
// The horizon, as a share of the screen's height.
const HORIZON = 0.6;

// Light from the upper left and in front; the sheen is a soft satin highlight.
const LIGHT = [-0.4, -0.5, 0.77];
const SHEEN_POWER = 40;
const SHEEN = 0.5;

// The wind settings and their strength; the wind eases between them at WIND_RATE /s.
const MODES = ['Calm', 'Breeze', 'Gale'] as const;
const MODE_LEVEL = [0, 0.55, 1];
const WIND_RATE = 1.6;
// A gust fades with this time constant (s). The script gusts by SCRIPT_GUST; a swipe this fast
// (pt/s) that misses the cloth, while the wind is up, gusts by GUST_PER_SPEED per pt/s; together
// never past GUST_MAX.
const GUST_FADE = 1.1;
const SCRIPT_GUST = 0.85;
const GUST_SWIPE = 450;
const GUST_PER_SPEED = 1 / 2200;
const GUST_MAX = 1.5;
// A drag picks the nearest point of the cloth within this reach (pt). The cloth itself is the
// feedback: the held point follows the finger and the folds pull out from it, with no marker.
const GRAB_REACH = 52;

// autoPlay (seconds): still at first; a breeze rises, then a gale, a gust, a hand pulls the
// lower fly corner down and lets go, the wind drops and the banner falls still again.
const LOOP = 14;
const BREEZE_AT = 0.4;
const GALE_AT = 2.4;
const GUST_AT = 4.0;
const GRAB_AT = 5.6;
const PULL_TIME = 1.1;
const RELEASE_AT = 7.4;
const CALM_AT = 8.6;
// For the last LAND_TIME of the loop the hanging banner is eased onto the exact resting state it
// started from, so the loop joins without a jump.
const LAND_TIME = 1.8;
// Where the scripted hand pulls the corner, as a share of the banner from its hoist and top.
const PULL_TO = [0.58, 1.55];
// After a touch the loop waits this long before it starts again.
const USER_HOLD = 4;

const SERIF = process.env.EXPO_OS === 'ios' ? 'Baskerville' : 'serif';
const SANS = process.env.EXPO_OS === 'ios' ? 'Avenir Next' : 'sans-serif';
const INK = '#F3ECE0';
const MUTED = '#B5A79A';
const SKY_TOP = '#0f1626';

type Scene = {
  cloth: Cloth;
  rest: Cloth;
  /** The cloth as drawn this frame (between its last two steps), and its normals. */
  rx: Float64Array;
  ry: Float64Array;
  rz: Float64Array;
  nx: Float64Array;
  ny: Float64Array;
  nz: Float64Array;
  /** The cloth's points on screen, for drawing and for picking the one under a finger. */
  sx: Float64Array;
  sy: Float64Array;
  texs: SkPoint[];
  indices: number[];
  /** Reused every frame: MakeVertices copies them. */
  positions: { x: number; y: number }[];
  colors: SkColor[];
  sheen: SkColor[];
};

function clamp01(v: number) {
  'worklet';
  return Math.min(1, Math.max(0, v));
}

/** Whether a clock that went from `before` to `t` passed the moment `at`. */
function crossed(before: number, t: number, at: number) {
  'worklet';
  return before < at && t >= at;
}

function easeInOut(t: number) {
  'worklet';
  return t < 0.5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2;
}

/** The mesh's triangles and the flat banner coordinates (pt) each point shows. */
function buildMesh() {
  'worklet';
  const texs: SkPoint[] = [];
  const indices: number[] = [];
  for (let r = 0; r < MESH_ROWS; r += 1) {
    for (let c = 0; c < MESH_COLS; c += 1) {
      texs.push(Skia.Point((c * FLAG_W) / (MESH_COLS - 1), (r * FLAG_H) / (MESH_ROWS - 1)));
      if (c < MESH_COLS - 1 && r < MESH_ROWS - 1) {
        const i = r * MESH_COLS + c;
        indices.push(i, i + 1, i + MESH_COLS, i + 1, i + MESH_COLS + 1, i + MESH_COLS);
      }
    }
  }
  return { texs, indices };
}

/** The sky at dusk over a still sea, with a low headland on the horizon. */
function drawScenery(canvas: SkCanvas, width: number, height: number) {
  'worklet';
  const horizon = height * HORIZON;
  const sky = Skia.Paint();
  sky.setShader(
    Skia.Shader.MakeLinearGradient(
      Skia.Point(0, 0),
      Skia.Point(0, horizon),
      [Skia.Color(SKY_TOP), Skia.Color('#1d2a46'), Skia.Color('#4a4a64'), Skia.Color('#9a7062')],
      [0, 0.45, 0.8, 1],
      TileMode.Clamp,
    ),
  );
  canvas.drawRect(Skia.XYWHRect(0, 0, width, horizon), sky);
  // The headland: a long low hill on the right, with a lower spit before it.
  const land = Skia.PathBuilder.Make();
  land.moveTo(width * 0.42, horizon);
  land.cubicTo(width * 0.55, horizon - 4, width * 0.6, horizon - 15, width * 0.74, horizon - 17);
  land.cubicTo(width * 0.86, horizon - 19, width * 0.93, horizon - 11, width + 2, horizon - 12);
  land.lineTo(width + 2, horizon);
  land.close();
  const hill = Skia.Paint();
  hill.setAntiAlias(true);
  hill.setColor(Skia.Color('#2f2c3c'));
  canvas.drawPath(land.detach(), hill);
  const sea = Skia.Paint();
  sea.setShader(
    Skia.Shader.MakeLinearGradient(
      Skia.Point(0, horizon),
      Skia.Point(0, height),
      [Skia.Color('#5d5262'), Skia.Color('#262c40'), Skia.Color('#0c101b')],
      [0, 0.22, 1],
      TileMode.Clamp,
    ),
  );
  canvas.drawRect(Skia.XYWHRect(0, horizon, width, height - horizon), sea);
  // A few long, faint glints of the last light on the water.
  const glint = Skia.Paint();
  glint.setAntiAlias(true);
  glint.setStyle(PaintStyle.Stroke);
  glint.setStrokeWidth(1);
  const lines = [
    [0.18, 0.012, 0.3, 0.22],
    [0.52, 0.02, 0.22, 0.16],
    [0.08, 0.04, 0.2, 0.1],
    [0.66, 0.055, 0.18, 0.08],
  ];
  for (const [x, y, w, a] of lines) {
    glint.setColor(Skia.Color(`rgba(236, 196, 170, ${a})`));
    const yy = horizon + height * y;
    canvas.drawLine(width * x, yy, width * (x + w), yy, glint);
  }
}

/**
 * The pole: a brushed metal shaft with a gold ball on top and a halyard down its side. Its foot
 * fades into the dark water above the caption, so the shaft never runs through the text.
 */
function drawPole(canvas: SkCanvas, top: number, end: number) {
  'worklet';
  const fadeFrom = Math.max(top + 40, end - POLE_FADE);
  canvas.saveLayer();
  const shaft = Skia.Paint();
  shaft.setAntiAlias(true);
  shaft.setShader(
    Skia.Shader.MakeLinearGradient(
      Skia.Point(POLE_X - POLE_W / 2, 0),
      Skia.Point(POLE_X + POLE_W / 2, 0),
      [Skia.Color('#22262e'), Skia.Color('#dfe3ea'), Skia.Color('#7d8490'), Skia.Color('#1c2027')],
      [0, 0.32, 0.6, 1],
      TileMode.Clamp,
    ),
  );
  canvas.drawRect(Skia.XYWHRect(POLE_X - POLE_W / 2, top - 14, POLE_W, end - top + 14), shaft);
  // The truck under the ball.
  canvas.drawRRect(Skia.RRectXY(Skia.XYWHRect(POLE_X - 6, top - 17, 12, 5), 2, 2), shaft);
  const ball = Skia.Paint();
  ball.setAntiAlias(true);
  ball.setShader(
    Skia.Shader.MakeRadialGradient(
      Skia.Point(POLE_X - 2.6, top - 28),
      10,
      [Skia.Color('#fff1c4'), Skia.Color('#d7a64c'), Skia.Color('#6b4815')],
      [0, 0.45, 1],
      TileMode.Clamp,
    ),
  );
  canvas.drawCircle(POLE_X, top - 25, 7.5, ball);
  const halyard = Skia.Paint();
  halyard.setAntiAlias(true);
  halyard.setStyle(PaintStyle.Stroke);
  halyard.setStrokeWidth(0.8);
  halyard.setColor(Skia.Color('#e8e0d4'));
  halyard.setAlphaf(0.35);
  canvas.drawLine(POLE_X + POLE_W / 2 + 1.5, top - 17, POLE_X + POLE_W / 2 + 1.5, end, halyard);
  const fade = Skia.Paint();
  fade.setBlendMode(BlendMode.DstIn);
  fade.setShader(
    Skia.Shader.MakeLinearGradient(
      Skia.Point(0, fadeFrom),
      Skia.Point(0, end),
      [Skia.Color('rgba(0, 0, 0, 1)'), Skia.Color('rgba(0, 0, 0, 0)')],
      null,
      TileMode.Clamp,
    ),
  );
  canvas.drawRect(Skia.XYWHRect(POLE_X - 12, fadeFrom, 24, end - fadeFrom + 2), fade);
  canvas.restore();
}

/**
 * A fabric banner flying from a pole over the sea at dusk, simulated as a verlet cloth with wind.
 * Folds catch the light from their normals and the weave shows in the cloth. A switch sets the
 * wind (calm, breeze, gale); dragging grabs the cloth at the nearest point and pulls it, letting
 * go lets it billow; a fast swipe past the cloth gusts the wind, and a tap stills it or brings it back.
 */
export function ClothFlagMesh({
  autoPlay = false,
  label = 'House banner · No. 7',
  title = 'Saltmarsh Regatta',
  subtitle = 'Flown at the harbour mouth since 1874',
}: Props) {
  const insets = useSafeAreaInsets();
  const reducedMotion = useReducedMotion();
  const auto = autoPlay && !reducedMotion;
  const [size, setSize] = useState<{ width: number; height: number } | null>(null);
  const firstMode = auto ? 0 : 1;
  const [mode, setMode] = useState(firstMode);
  const [segmentWidth, setSegmentWidth] = useState(0);
  const fonts = useMemo(
    () => ({
      knots: matchFont({ fontFamily: SANS, fontSize: 40, fontWeight: '600' }),
      unit: matchFont({ fontFamily: SANS, fontSize: 15, fontWeight: '600' }),
      small: matchFont({ fontFamily: SANS, fontSize: 11, fontWeight: '700' }),
    }),
    [],
  );
  // The paints that never change, made once instead of every frame.
  const paints = useMemo(() => {
    const weave = Skia.RuntimeEffect.Make(WEAVE_SKSL);
    const layer = Skia.Paint();
    layer.setImageFilter(Skia.ImageFilter.MakeDropShadow(10, 18, 12, 12, Skia.Color('rgba(5, 7, 12, 0.3)')));
    const cloth = Skia.Paint();
    cloth.setAntiAlias(true);
    if (weave) cloth.setShader(weave.makeShader([FLAG_W, FLAG_H]));
    else cloth.setColor(Skia.Color('#7a1822'));
    const satin = Skia.Paint();
    satin.setBlendMode(BlendMode.Screen);
    const ring = Skia.Paint();
    ring.setAntiAlias(true);
    ring.setStyle(PaintStyle.Stroke);
    ring.setStrokeWidth(1.6);
    ring.setColor(Skia.Color('#d9b36a'));
    const ink = Skia.Paint();
    ink.setAntiAlias(true);
    ink.setColor(Skia.Color(INK));
    const muted = Skia.Paint();
    muted.setAntiAlias(true);
    muted.setColor(Skia.Color(MUTED));
    return { layer, cloth, satin, ring, ink, muted };
  }, []);

  const width = size?.width ?? 0;
  const height = size?.height ?? 0;
  const top = insets.top + FLAG_TOP;
  const originX = POLE_X + HOIST_GAP;
  // Perspective centre: the middle of the banner at rest.
  const vpx = originX + FLAG_W / 2;
  const vpy = top + FLAG_H / 2;
  // The readout sits on the right, level with the caption's title.
  const readoutBase = height - insets.bottom - 140;
  // The pole's foot fades out just above the caption.
  const poleEnd = height - insets.bottom - CAPTION_CLEAR;
  // Sky, sea and pole never change: record them once per size.
  const backdrop = useMemo(() => {
    if (width === 0) return null;
    const recorder = Skia.PictureRecorder();
    const canvas = recorder.beginRecording(Skia.XYWHRect(0, 0, width, height));
    drawScenery(canvas, width, height);
    drawPole(canvas, top, poleEnd);
    return recorder.finishRecordingAsPicture();
  }, [width, height, top, poleEnd]);

  const scene = useSharedValue<Scene | null>(null);
  const mesh = useSharedValue<SkVertices | null>(null);
  const shine = useSharedValue<SkVertices | null>(null);
  const acc = useSharedValue(0);
  // The air: 0…1 strength easing towards the chosen mode's, and a fading gust.
  const level = useSharedValue(0);
  const gust = useSharedValue(0);
  const windMode = useSharedValue(firstMode);
  const knots = useSharedValue(0);
  // The point being dragged (-1 for none), where the finger holds it, and whether the script holds it.
  const grab = useSharedValue(-1);
  const fingerX = useSharedValue(0);
  const fingerY = useSharedValue(0);
  const scripted = useSharedValue(false);
  // The wind a tap brings back after stilling it: the last setting that was not calm.
  const lastWind = useSharedValue(firstMode === 0 ? 1 : firstMode);
  // The grabbed point's offset from the finger, so it does not jump to it.
  const grabDX = useSharedValue(0);
  const grabDY = useSharedValue(0);
  const pullFromX = useSharedValue(0);
  const pullFromY = useSharedValue(0);
  const clock = useSharedValue(0);
  const hold = useSharedValue(0);
  const looping = useSharedValue(auto);
  // The loop has run untouched since the cloth last hung at rest: at its end it may snap back exactly.
  const pristine = useSharedValue(true);
  // The selected segment's place, for the switch's sliding pill.
  const pill = useSharedValue(firstMode);

  useEffect(() => {
    looping.set(auto);
    clock.set(0);
    hold.set(0);
  }, [auto, clock, hold, looping]);

  useEffect(() => {
    pill.set(reducedMotion ? mode : withSpring(mode, { duration: 380, dampingRatio: 0.82 }));
  }, [mode, pill, reducedMotion]);

  const showMode = (m: number) => setMode(m);
  const grabbed = () => {
    if (process.env.EXPO_OS !== 'web') Haptics.selectionAsync().catch(() => {});
  };
  const gusted = () => {
    if (process.env.EXPO_OS !== 'web') Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
  };
  const toggled = (on: boolean) => {
    if (process.env.EXPO_OS === 'web') return;
    Haptics.impactAsync(on ? Haptics.ImpactFeedbackStyle.Medium : Haptics.ImpactFeedbackStyle.Soft).catch(() => {});
  };

  const setWind = (m: number) => {
    'worklet';
    if (m === windMode.get()) return;
    windMode.set(m);
    if (m > 0) lastWind.set(m);
    scheduleOnRN(showMode, m);
  };

  const blow = (amount: number) => {
    'worklet';
    gust.set(Math.min(GUST_MAX, gust.get() + amount));
  };

  /** The autoPlay script at time `t` after a tick from `before`. */
  const script = (s: Scene, before: number, t: number) => {
    'worklet';
    const corner = MESH_ROWS * MESH_COLS - 1;
    if (crossed(before, t, BREEZE_AT)) setWind(1);
    if (crossed(before, t, GALE_AT)) setWind(2);
    if (crossed(before, t, GUST_AT)) blow(SCRIPT_GUST);
    if (crossed(before, t, GRAB_AT)) {
      grab.set(corner);
      scripted.set(true);
      pullFromX.set(s.cloth.x[corner]);
      pullFromY.set(s.cloth.y[corner]);
    }
    if (scripted.get() && t >= GRAB_AT && t < RELEASE_AT) {
      const k = easeInOut(clamp01((t - GRAB_AT) / PULL_TIME));
      fingerX.set(pullFromX.get() + (originX + FLAG_W * PULL_TO[0] - pullFromX.get()) * k);
      fingerY.set(pullFromY.get() + (top + FLAG_H * PULL_TO[1] - pullFromY.get()) * k);
    }
    if (crossed(before, t, RELEASE_AT) && scripted.get()) {
      grab.set(-1);
      scripted.set(false);
    }
    if (crossed(before, t, CALM_AT)) setWind(0);
  };

  /** One tick: the script, the air, then the cloth. */
  const tick = (s: Scene) => {
    'worklet';
    if (grab.get() < 0 || scripted.get()) {
      if (hold.get() > 0) {
        hold.set(hold.get() - TICK);
        if (hold.get() <= 0) {
          // Pick the loop up where the air already is, rather than dropping the cloth first.
          const m = windMode.get();
          clock.set(m === 2 ? GALE_AT + 0.5 : m === 1 ? BREEZE_AT + 0.5 : 0);
        }
      } else if (looping.get()) {
        let before = clock.get();
        let t = before + TICK;
        if (t >= LOOP) {
          before = 0;
          t -= LOOP;
          // Untouched, the banner has hung still again: start the next loop from exactly the same state.
          if (pristine.get()) copyCloth(s.rest, s.cloth);
          pristine.set(true);
          level.set(0);
          gust.set(0);
          setWind(0);
        }
        clock.set(t);
        script(s, before, t);
        if (pristine.get() && windMode.get() === 0 && t > LOOP - LAND_TIME) {
          const k = (t - (LOOP - LAND_TIME)) / LAND_TIME;
          blendCloth(s.cloth, s.rest, k * k * k * 0.2);
        }
      }
    }
    const goal = MODE_LEVEL[windMode.get()] ?? 0;
    level.set(level.get() + (goal - level.get()) * (1 - Math.exp(-TICK * WIND_RATE)));
    if (Math.abs(goal - level.get()) < 1e-4) level.set(goal);
    gust.set(gust.get() * Math.exp(-TICK / GUST_FADE));
    if (gust.get() < 1e-4) gust.set(0);
    const calm = reducedMotion ? 0.35 : 1;
    const air: Air = { level: level.get() * calm, gust: gust.get() * calm };
    stepCloth(s.cloth, air, TICK, grab.get(), fingerX.get(), fingerY.get());
  };

  useFrameCallback((frame) => {
    if (width === 0) return;
    let s = scene.get();
    if (s === null) {
      const cloth = createCloth(originX, top);
      const rest = createCloth(originX, top);
      for (let i = 0; i < SETTLE_STEPS; i += 1) {
        stepCloth(rest, { level: 0, gust: 0 }, TICK, -1, 0, 0);
        if (i >= SETTLE_STEPS - STILL_STEPS) stillCloth(rest);
      }
      // A user starts in a breeze: let it fill out before the first frame.
      copyCloth(rest, cloth);
      if (!auto) {
        for (let i = 0; i < 150; i += 1) stepCloth(cloth, { level: MODE_LEVEL[1] * (reducedMotion ? 0.35 : 1), gust: 0 }, TICK, -1, 0, 0);
        level.set(MODE_LEVEL[1]);
      }
      const n = MESH_COLS * MESH_ROWS;
      const built = buildMesh();
      const positions: { x: number; y: number }[] = [];
      const colors: SkColor[] = [];
      const sheen: SkColor[] = [];
      for (let i = 0; i < n; i += 1) {
        positions.push({ x: 0, y: 0 });
        colors.push(new Float32Array(4));
        sheen.push(new Float32Array(4));
      }
      s = {
        cloth,
        rest,
        rx: new Float64Array(n),
        ry: new Float64Array(n),
        rz: new Float64Array(n),
        nx: new Float64Array(n),
        ny: new Float64Array(n),
        nz: new Float64Array(n),
        sx: new Float64Array(n),
        sy: new Float64Array(n),
        texs: built.texs,
        indices: built.indices,
        positions,
        colors,
        sheen,
      };
      scene.set(s);
    }
    const dt = Math.min((frame.timeSincePreviousFrame ?? 16) / 1000, MAX_TICKS * TICK);
    let pending = acc.get() + dt;
    let ticks = 0;
    while (pending >= TICK && ticks < MAX_TICKS) {
      tick(s);
      pending -= TICK;
      ticks += 1;
    }
    pending = Math.min(pending, TICK);
    acc.set(pending);
    const alpha = clamp01(pending / TICK);

    // The cloth as it stands between its last two steps; project, shade and hand it to the picture.
    const { cloth, rx, ry, rz, nx, ny, nz, positions, colors, sheen } = s;
    const n = MESH_COLS * MESH_ROWS;
    for (let i = 0; i < n; i += 1) {
      rx[i] = cloth.ox[i] + (cloth.x[i] - cloth.ox[i]) * alpha;
      ry[i] = cloth.oy[i] + (cloth.y[i] - cloth.oy[i]) * alpha;
      rz[i] = cloth.oz[i] + (cloth.z[i] - cloth.oz[i]) * alpha;
    }
    computeNormals(rx, ry, rz, nx, ny, nz);
    const hx = LIGHT[0];
    const hy = LIGHT[1];
    const hz = LIGHT[2] + 1;
    const hl = Math.hypot(hx, hy, hz);
    for (let i = 0; i < n; i += 1) {
      const scale = FOCAL / (FOCAL - Math.min(rz[i], FOCAL * 0.6));
      const x = vpx + (rx[i] - vpx) * scale;
      const y = vpy + (ry[i] - vpy) * scale;
      s.sx[i] = x;
      s.sy[i] = y;
      positions[i].x = x;
      positions[i].y = y;
      const diffuse = Math.max(0, nx[i] * LIGHT[0] + ny[i] * LIGHT[1] + nz[i] * LIGHT[2]);
      const shade = Math.min(1, 0.3 + 0.86 * diffuse);
      const color = colors[i];
      color[0] = shade;
      color[1] = shade * 0.98;
      color[2] = shade * 0.95;
      color[3] = 1;
      const h = Math.max(0, (nx[i] * hx + ny[i] * hy + nz[i] * hz) / hl);
      const spec = Math.pow(h, SHEEN_POWER) * SHEEN;
      const glint = sheen[i];
      glint[0] = spec;
      glint[1] = spec * 0.94;
      glint[2] = spec * 0.82;
      glint[3] = 1;
    }
    mesh.set(Skia.MakeVertices(VertexMode.Triangles, positions, s.texs, colors, s.indices));
    shine.set(Skia.MakeVertices(VertexMode.Triangles, positions, null, sheen, s.indices));
    if (ticks > 0) {
      const target = windKnots({ level: level.get(), gust: gust.get() }, cloth.time);
      knots.set(knots.get() + (target - knots.get()) * 0.08);
    }
  });

  const picture = useDerivedValue(() => {
    const recorder = Skia.PictureRecorder();
    const canvas = recorder.beginRecording(Skia.XYWHRect(0, 0, width, height));
    if (width === 0) return recorder.finishRecordingAsPicture();

    if (backdrop) canvas.drawPicture(backdrop);

    const m = mesh.get();
    const sh = shine.get();
    if (m && sh) {
      // The banner on its own layer, with a soft shade under it so it sits off the sky.
      canvas.saveLayer(paints.layer);
      canvas.drawVertices(m, BlendMode.Modulate, paints.cloth);
      canvas.drawVertices(sh, BlendMode.Dst, paints.satin);
      canvas.restore();
    }

    // Rings holding the hoist to the pole, over the cloth.
    const s = scene.get();
    if (s) {
      for (const r of RING_ROWS) {
        const y = s.sy[r * MESH_COLS];
        canvas.drawOval(Skia.XYWHRect(POLE_X - 6, y - 2.5, 12, 5), paints.ring);
      }
    }

    // The wind readout, on the right of the caption: "WIND", or "CALM" once a tap has stilled it.
    const right = width - 24;
    const value = `${Math.max(0, Math.round(knots.get()))}`;
    const ink = paints.ink;
    const unitW = fonts.unit.measureText('kn').width;
    canvas.drawText('kn', right - unitW, readoutBase, ink, fonts.unit);
    const valueW = fonts.knots.measureText(value).width;
    canvas.drawText(value, right - unitW - 5 - valueW, readoutBase, ink, fonts.knots);
    const muted = paints.muted;
    const word = windMode.get() === 0 ? 'CALM' : 'WIND';
    const wordW = fonts.small.measureText(word).width;
    canvas.drawText(word, right - wordW, readoutBase - 44, muted, fonts.small);
    return recorder.finishRecordingAsPicture();
  });

  const touched = () => {
    'worklet';
    hold.set(USER_HOLD);
    pristine.set(false);
    if (scripted.get()) {
      scripted.set(false);
      grab.set(-1);
    }
  };

  /** A screen point as a point at the cloth's depth `z`, undoing the perspective. */
  const toWorld = (x: number, y: number, z: number) => {
    'worklet';
    const k = (FOCAL - Math.min(z, FOCAL * 0.6)) / FOCAL;
    return { x: vpx + (x - vpx) * k, y: vpy + (y - vpy) * k };
  };

  const pan = Gesture.Pan()
    .minDistance(4)
    .onStart((event) => {
      touched();
      const s = scene.get();
      if (!s) return;
      let best = -1;
      let bestD = GRAB_REACH;
      for (let i = 0; i < MESH_COLS * MESH_ROWS; i += 1) {
        if (i % MESH_COLS === 0) continue;
        const d = Math.hypot(s.sx[i] - event.x, s.sy[i] - event.y);
        if (d < bestD) {
          bestD = d;
          best = i;
        }
      }
      if (best < 0) return;
      // Hold the point where it is and let the finger carry it from there.
      const w = toWorld(event.x, event.y, s.cloth.z[best]);
      grabDX.set(s.cloth.x[best] - w.x);
      grabDY.set(s.cloth.y[best] - w.y);
      fingerX.set(s.cloth.x[best]);
      fingerY.set(s.cloth.y[best]);
      grab.set(best);
      scheduleOnRN(grabbed);
    })
    .onUpdate((event) => {
      const s = scene.get();
      const i = grab.get();
      if (!s || i < 0 || scripted.get()) return;
      const w = toWorld(event.x, event.y, s.cloth.z[i]);
      fingerX.set(w.x + grabDX.get());
      fingerY.set(w.y + grabDY.get());
    })
    .onEnd((event) => {
      if (grab.get() >= 0) {
        grab.set(-1);
        hold.set(USER_HOLD);
        return;
      }
      // A swipe gusts the wind that is already blowing; in still air there is nothing to gust.
      const speed = Math.hypot(event.velocityX, event.velocityY);
      if (!(speed >= GUST_SWIPE) || (windMode.get() === 0 && level.get() < 0.05)) return;
      blow(speed * GUST_PER_SPEED);
      hold.set(USER_HOLD);
      scheduleOnRN(gusted);
    })
    .onFinalize(() => {
      if (grab.get() >= 0 && !scripted.get()) grab.set(-1);
    });

  // A tap stills the wind, or brings back the last wind it stilled.
  const tap = Gesture.Tap()
    .maxDuration(300)
    .onEnd((_event, success) => {
      if (!success) return;
      touched();
      const on = windMode.get() === 0;
      setWind(on ? lastWind.get() : 0);
      scheduleOnRN(toggled, on);
    });

  const pick = (m: number) => {
    hold.set(USER_HOLD);
    pristine.set(false);
    if (m !== windMode.get() && process.env.EXPO_OS !== 'web') Haptics.selectionAsync().catch(() => {});
    windMode.set(m);
    if (m > 0) lastWind.set(m);
    setMode(m);
  };

  const pillStyle = useAnimatedStyle(() => ({
    transform: [{ translateX: pill.get() * segmentWidth }],
  }));

  return (
    <View
      style={styles.screen}
      onLayout={(event: LayoutChangeEvent) => {
        const { width: w, height: h } = event.nativeEvent.layout;
        setSize({ width: w, height: h });
      }}
    >
      <GestureDetector gesture={Gesture.Race(pan, tap)}>
        <View
          style={StyleSheet.absoluteFill}
          accessible
          accessibilityRole="adjustable"
          accessibilityLabel={`${title} banner. Drag the cloth to pull it, swipe past it to gust the wind, tap to still or raise the wind.`}
        >
          {size && (
            <Canvas style={StyleSheet.absoluteFill}>
              <Picture picture={picture} />
            </Canvas>
          )}
        </View>
      </GestureDetector>
      <View pointerEvents="none" style={[styles.caption, { bottom: insets.bottom + 108 }]}>
        <Text style={styles.label}>{label.toUpperCase()}</Text>
        <Text style={styles.title} numberOfLines={1} adjustsFontSizeToFit>
          {title}
        </Text>
        <Text style={styles.subtitle} numberOfLines={2}>
          {subtitle}
        </Text>
      </View>
      <View pointerEvents="box-none" style={[styles.bottom, { bottom: insets.bottom + 14 }]}>
        <Text pointerEvents="none" style={styles.hint} numberOfLines={1} adjustsFontSizeToFit>
          Drag the cloth · Swipe for a gust · Tap to still the wind
        </Text>
        <View
          style={styles.segments}
          onLayout={(event) => setSegmentWidth((event.nativeEvent.layout.width - 8) / MODES.length)}
          accessibilityRole="radiogroup"
        >
          {segmentWidth > 0 && <Animated.View style={[styles.pill, { width: segmentWidth }, pillStyle]} />}
          {MODES.map((name, i) => {
            const selected = i === mode;
            return (
              <Pressable
                key={name}
                accessibilityRole="radio"
                accessibilityState={{ selected }}
                accessibilityLabel={`${name} wind`}
                onPress={() => pick(i)}
                style={({ pressed }) => [styles.segment, pressed && !selected && styles.pressed]}
              >
                <Text style={[styles.segmentText, selected && styles.segmentTextOn]}>{name}</Text>
              </Pressable>
            );
          })}
        </View>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  screen: {
    flex: 1,
    backgroundColor: SKY_TOP,
    overflow: 'hidden',
  },
  caption: {
    position: 'absolute',
    left: 24,
    right: 130,
    gap: 4,
  },
  label: {
    fontFamily: SANS,
    fontSize: 11,
    fontWeight: '700',
    letterSpacing: 2.2,
    color: MUTED,
  },
  title: {
    fontFamily: SERIF,
    fontSize: 30,
    fontWeight: '600',
    color: INK,
  },
  subtitle: {
    fontFamily: SANS,
    fontSize: 13,
    fontWeight: '500',
    color: MUTED,
  },
  bottom: {
    position: 'absolute',
    left: 20,
    right: 20,
    gap: 12,
  },
  hint: {
    textAlign: 'center',
    fontFamily: SANS,
    fontSize: 13,
    fontWeight: '500',
    color: MUTED,
  },
  segments: {
    height: 46,
    flexDirection: 'row',
    padding: 4,
    borderRadius: 23,
    backgroundColor: 'rgba(255, 255, 255, 0.08)',
    borderWidth: StyleSheet.hairlineWidth,
    borderColor: 'rgba(255, 255, 255, 0.16)',
  },
  pill: {
    position: 'absolute',
    top: 4,
    left: 4,
    bottom: 4,
    borderRadius: 19,
    backgroundColor: 'rgba(243, 236, 224, 0.94)',
  },
  segment: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
  },
  segmentText: {
    fontFamily: SANS,
    fontSize: 15,
    fontWeight: '600',
    color: INK,
  },
  segmentTextOn: {
    color: '#1A2030',
  },
  pressed: {
    opacity: 0.7,
  },
});
