import {
  Canvas,
  ClipOp,
  FilterMode,
  matchFont,
  MipmapMode,
  Picture,
  Skia,
  type SkCanvas,
  type SkFont,
  type SkImage,
  type SkShader,
  TileMode,
  vec,
} from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { useEffect, useMemo, useState } from 'react';
import { type LayoutChangeEvent, StyleSheet, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import { useDerivedValue, useFrameCallback, useReducedMotion, useSharedValue } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

type Props = {
  /** Unsplash id of a large, detailed photo; it is fetched 1600 px wide. */
  photo?: string;
  /** Two lines at the bottom: a title and a line of small print. */
  title?: string;
  meta?: string;
  /** Wanders the loupe over the photo on a LOOP-second path, pausing on details. */
  autoPlay?: boolean;
  /** Where the wander stops, as shares of the screen, and the zoom at each stop. */
  stops?: [number, number, number][];
};

// The glass: its radius, the width of its metal rim (pt), and its zoom range.
const RADIUS = 92;
const RIM = 13;
const ZOOM = 2.5;
const MIN_ZOOM = 1.5;
const MAX_ZOOM = 5;
// Barrel distortion: at the edge of the glass the photo is spread by this much more than in the
// middle; the colours split by up to FRINGE there, red outwards and blue inwards.
const BARREL = 0.32;
const FRINGE = 0.04;
// The photo outside the glass is dimmed to this.
const DIM = 0.66;
// The loupe trails the finger on a spring (unit mass), and a release throws it THROW s ahead.
const STIFFNESS = 300;
const DAMPING = 25;
const THROW = 0.1;
const EDGE_BAND = 60;
// The loupe's middle stays this share of its radius inside the screen's edges.
const EDGE_KEEP = 0.62;
// Its highlight slides against its speed, up to TILT_MAX of the radius, and turns with it.
const TILT_PER_SPEED = 0.00012;
const TILT_MAX = 0.16;
// A tap elsewhere jumps the loupe there on an arc, lifting it towards the viewer on the way.
const JUMP_TIME = 0.52;
const JUMP_ARC = 0.35;
const JUMP_LIFT = 0.08;
// A tap on the glass toggles between the resting zoom and a close look.
const CLOSE_ZOOM = 4;
const ZOOM_STIFFNESS = 220;
const ZOOM_DAMPING = 22;
// The loupe's shadow: offset and softness at rest and lifted.
const SHADOW = [8, 14, 18];
const SHADOW_LIFTED = [18, 30, 34];

// The self-playing wander: the loupe glides from stop to stop on a smooth curve (TRAVEL s),
// zooming out a little on the way, and rests on each one (DWELL s). One of the moves is a jump.
const TRAVEL = 1.5;
const DWELL = 1.1;
const TRAVEL_ZOOM = 2.1;
const JUMP_LEG = 2;
const STOPS: [number, number, number][] = [
  [0.42, 0.44, 2.6],
  [0.36, 0.16, 3.2],
  [0.27, 0.3, 3],
  [0.48, 0.7, 3.4],
  [0.74, 0.77, 3],
];
// After a touch the wander waits this long (s) before it starts again.
const USER_HOLD = 3.5;
const IMAGE_FADE = 0.6;

// Brushed metal around the rim, light to dark and back.
const RIM_TONES = ['#f4f3ef', '#8c8b86', '#e2e0da', '#5e5d59', '#d3d1cb', '#7d7c78', '#f4f3ef'];

const DISPLAY = process.env.EXPO_OS === 'ios' ? 'Avenir Next' : 'sans-serif';
const MONO = process.env.EXPO_OS === 'ios' ? 'Menlo' : 'monospace';

const PHOTO = 'photo-1529926691761-20fb82067c71';
const photoUrl = (id: string) => `https://images.unsplash.com/${id}?auto=format&fit=crop&w=1600&q=80`;

// The whole screen in one pass: the photo cover-fitted and dimmed, the loupe's soft shadow, and
// inside the glass the photo magnified with barrel distortion, colour fringes and a dark edge.
const LENS = Skia.RuntimeEffect.Make(`
uniform shader image;
uniform float2 imageSize;
uniform float2 viewSize;
uniform float2 center;
uniform float radius;
uniform float rim;
uniform float zoom;
uniform float dim;
uniform float2 shadowOffset;
uniform float shadowSoft;
uniform float barrel;
uniform float fringe;
uniform float shown;

float2 toImage(float2 p) {
  float s = max(viewSize.x / imageSize.x, viewSize.y / imageSize.y);
  float2 off = (viewSize - imageSize * s) * 0.5;
  return (p - off) / s;
}

half4 main(float2 p) {
  float2 d = p - center;
  float r = length(d) / radius;
  if (r < 1.0) {
    float k = 1.0 + barrel * r * r;
    float split = fringe * r * r * r;
    float2 base = d / zoom;
    half3 col;
    col.r = image.eval(toImage(center + base * (k + split))).r;
    col.g = image.eval(toImage(center + base * k)).g;
    col.b = image.eval(toImage(center + base * (k - split))).b;
    col *= 1.06 * (1.0 - 0.5 * smoothstep(0.74, 1.0, r));
    return half4(col * shown, 1.0);
  }
  half3 col = image.eval(toImage(p)).rgb * dim * shown;
  float2 uv = p / viewSize - 0.5;
  col *= 1.0 - 0.7 * dot(uv, uv);
  float sd = length(p - center - shadowOffset) - (radius + rim);
  col *= 1.0 - 0.5 * (1.0 - smoothstep(-shadowSoft * 0.4, shadowSoft, sd));
  return half4(col, 1.0);
}`);

type Stage = { width: number; height: number; top: number; bottom: number };

function clamp01(x: number) {
  'worklet';
  return Math.min(1, Math.max(0, x));
}

function easeInOut(p: number) {
  'worklet';
  return p < 0.5 ? 4 * p * p * p : 1 - Math.pow(-2 * p + 2, 3) / 2;
}

function rubber(x: number, min: number, max: number, band: number) {
  'worklet';
  if (x < min) return min - band * (1 - 1 / ((min - x) / band + 1));
  if (x > max) return max + band * (1 - 1 / ((x - max) / band + 1));
  return x;
}

/** Catmull-Rom through four values, at `t` between the middle two. */
function spline(a: number, b: number, c: number, d: number, t: number) {
  'worklet';
  const t2 = t * t;
  const t3 = t2 * t;
  return 0.5 * (2 * b + (c - a) * t + (2 * a - 5 * b + 4 * c - d) * t2 + (3 * b - a - 3 * c + d) * t3);
}

/** A point on a quadratic arc from a to b, bowed up by `bow` of their distance. */
function arc(ax: number, ay: number, bx: number, by: number, bow: number, t: number) {
  'worklet';
  const cx = (ax + bx) / 2;
  const cy = (ay + by) / 2 - Math.hypot(bx - ax, by - ay) * bow;
  const u = 1 - t;
  return [u * u * ax + 2 * u * t * cx + t * t * bx, u * u * ay + 2 * u * t * cy + t * t * by];
}

/**
 * Where the wander is at `t` seconds into the loop, in shares of the screen, with its zoom and
 * how lifted it is. Each stop is a dwell then a move to the next stop.
 */
function wander(stops: [number, number, number][], t: number) {
  'worklet';
  const n = stops.length;
  const leg = TRAVEL + DWELL;
  const i = Math.floor(t / leg) % n;
  const local = t - Math.floor(t / leg) * leg;
  const a = stops[i];
  if (local < DWELL) return { x: a[0], y: a[1], zoom: a[2], lift: 0, jump: false };
  const p = (local - DWELL) / TRAVEL;
  const e = easeInOut(p);
  const b = stops[(i + 1) % n];
  // Zoom eases out towards TRAVEL_ZOOM half way, then in to the next stop's.
  const zoom = p < 0.5 ? a[2] + (TRAVEL_ZOOM - a[2]) * easeInOut(p * 2) : TRAVEL_ZOOM + (b[2] - TRAVEL_ZOOM) * easeInOut(p * 2 - 1);
  if (i === JUMP_LEG) {
    const q = arc(a[0], a[1], b[0], b[1], JUMP_ARC, e);
    return { x: q[0], y: q[1], zoom, lift: Math.sin(Math.PI * p), jump: true };
  }
  const z = stops[(i - 1 + n) % n];
  const c = stops[(i + 2) % n];
  return { x: spline(z[0], a[0], b[0], c[0], e), y: spline(z[1], a[1], b[1], c[1], e), zoom, lift: 0, jump: false };
}

/** The metal rim: a brushed ring with bevels and a knurled outer band, and the zoom on a tab. */
function drawRim(canvas: SkCanvas, cx: number, cy: number, r: number, rim: number, spin: number, zoom: number, font: SkFont) {
  'worklet';
  const ring = Skia.Paint();
  ring.setAntiAlias(true);
  ring.setStyle(1);
  ring.setStrokeWidth(rim);
  const metal = [];
  for (const c of RIM_TONES) metal.push(Skia.Color(c));
  ring.setShader(Skia.Shader.MakeSweepGradient(cx, cy, metal, null, TileMode.Clamp));
  // The brushed light turns a little as the loupe swings.
  canvas.save();
  canvas.rotate(spin, cx, cy);
  canvas.drawCircle(cx, cy, r + rim / 2, ring);
  canvas.restore();
  // Knurling on the outer half of the band.
  const tick = Skia.Paint();
  tick.setAntiAlias(true);
  tick.setStrokeWidth(0.9);
  tick.setColor(Skia.Color('#000'));
  tick.setAlphaf(0.2);
  for (let k = 0; k < 90; k += 1) {
    const a = (k / 90) * Math.PI * 2;
    const c = Math.cos(a);
    const s = Math.sin(a);
    canvas.drawLine(cx + c * (r + rim * 0.58), cy + s * (r + rim * 0.58), cx + c * (r + rim * 0.94), cy + s * (r + rim * 0.94), tick);
  }
  // Bevels: dark at the glass and at the outer edge, a bright line just inside the outer edge.
  const line = Skia.Paint();
  line.setAntiAlias(true);
  line.setStyle(1);
  line.setStrokeWidth(1.4);
  line.setColor(Skia.Color('rgba(0, 0, 0, 0.6)'));
  canvas.drawCircle(cx, cy, r, line);
  line.setStrokeWidth(1);
  line.setColor(Skia.Color('rgba(0, 0, 0, 0.45)'));
  canvas.drawCircle(cx, cy, r + rim, line);
  line.setColor(Skia.Color('rgba(255, 255, 255, 0.5)'));
  canvas.drawCircle(cx, cy, r + 2, line);
  line.setColor(Skia.Color('rgba(255, 255, 255, 0.25)'));
  canvas.drawCircle(cx, cy, r + rim - 1.5, line);

  // The zoom on a small dark tab fixed to the rim, up and to the right.
  const label = `${zoom.toFixed(1)}×`;
  const w = font.measureText(label).width + 14;
  const tx = cx + Math.cos(-Math.PI / 4) * (r + rim) - 4;
  const ty = cy + Math.sin(-Math.PI / 4) * (r + rim) - 6;
  const tab = Skia.Paint();
  tab.setAntiAlias(true);
  tab.setColor(Skia.Color('rgba(14, 14, 16, 0.86)'));
  canvas.drawRRect(Skia.RRectXY(Skia.XYWHRect(tx, ty - 11, w, 22), 11, 11), tab);
  const edge = Skia.Paint();
  edge.setAntiAlias(true);
  edge.setStyle(1);
  edge.setStrokeWidth(1);
  edge.setColor(Skia.Color('rgba(255, 255, 255, 0.22)'));
  canvas.drawRRect(Skia.RRectXY(Skia.XYWHRect(tx, ty - 11, w, 22), 11, 11), edge);
  const ink = Skia.Paint();
  ink.setAntiAlias(true);
  ink.setColor(Skia.Color('#f3f1ea'));
  canvas.drawText(label, tx + 7, ty + 4, ink, font);
}

/** The glass's gloss: a soft crescent of light and a pin-point glint, sliding against the speed. */
function drawGloss(canvas: SkCanvas, cx: number, cy: number, r: number, tx: number, ty: number) {
  'worklet';
  canvas.save();
  canvas.clipPath(Skia.PathBuilder.Make().addCircle(cx, cy, r).build(), ClipOp.Intersect, true);
  const hx = cx - r * 0.34 + tx * r;
  const hy = cy - r * 0.42 + ty * r;
  const soft = Skia.Paint();
  soft.setAntiAlias(true);
  soft.setShader(
    Skia.Shader.MakeRadialGradient(vec(hx, hy), r * 0.62, [Skia.Color('rgba(255,255,255,0.34)'), Skia.Color('rgba(255,255,255,0.08)'), Skia.Color('rgba(255,255,255,0)')], [0, 0.55, 1], TileMode.Clamp),
  );
  canvas.save();
  canvas.rotate(-32 + tx * 40, hx, hy);
  canvas.drawOval(Skia.XYWHRect(hx - r * 0.6, hy - r * 0.3, r * 1.2, r * 0.6), soft);
  canvas.restore();
  // A thin bright arc along the lower right of the glass: light leaving through the far side.
  const rimLight = Skia.Paint();
  rimLight.setAntiAlias(true);
  rimLight.setStyle(1);
  rimLight.setStrokeWidth(3);
  rimLight.setColor(Skia.Color('rgba(255,255,255,0.16)'));
  canvas.drawPath(Skia.PathBuilder.Make().addArc(Skia.XYWHRect(cx - r + 5, cy - r + 5, r * 2 - 10, r * 2 - 10), 20 - tx * 30, 70).build(), rimLight);
  const glint = Skia.Paint();
  glint.setAntiAlias(true);
  glint.setColor(Skia.Color('rgba(255,255,255,0.85)'));
  canvas.drawCircle(cx - r * 0.5 + tx * r * 1.2, cy - r * 0.52 + ty * r * 1.2, r * 0.035, glint);
  canvas.restore();
}

/**
 * A large, detailed photo fills the screen, dimmed, with a round magnifying loupe over it. Inside
 * the glass the photo is enlarged 2.5 times through a curved lens: it bulges, its colours split
 * into fringes towards the thick metal rim, and a soft gloss slides across it as it moves. The loupe
 * trails the finger on a spring; a pinch changes the zoom, a tap on the glass looks closer, and a
 * tap anywhere else sends the loupe there on an arc.
 */
export function LoupeMagnifier({ photo = PHOTO, title = 'Harrow Strait Bridge', meta = 'CABLE-STAYED · 471 M SPAN · F/8', autoPlay = false, stops: route = STOPS }: Props) {
  // An empty route (never in the samples) falls back to the sample one.
  const stops = route.length > 0 ? route : STOPS;
  const insets = useSafeAreaInsets();
  const reducedMotion = useReducedMotion();
  const auto = autoPlay && !reducedMotion;
  const [size, setSize] = useState<{ width: number; height: number } | null>(null);
  const fonts = useMemo(
    () => ({
      tab: matchFont({ fontFamily: MONO, fontSize: 11, fontWeight: '600' }),
      title: matchFont({ fontFamily: DISPLAY, fontSize: 22, fontWeight: '700' }),
      meta: matchFont({ fontFamily: MONO, fontSize: 10 }),
    }),
    [],
  );

  // The loupe's middle, where it is heading, and its speed (pt, pt/s).
  const x = useSharedValue(0);
  const y = useSharedValue(0);
  const vx = useSharedValue(0);
  const vy = useSharedValue(0);
  const tx = useSharedValue(0);
  const ty = useSharedValue(0);
  const placed = useSharedValue(false);
  const grabX = useSharedValue(0);
  const grabY = useSharedValue(0);
  const dragging = useSharedValue(false);
  // Zoom on a spring, and the pinch's starting zoom.
  const zoom = useSharedValue(ZOOM);
  const zoomSpeed = useSharedValue(0);
  const zoomTo = useSharedValue(ZOOM);
  const zoomFrom = useSharedValue(ZOOM);
  // A jump under way: from, to, seconds since it began (−1 for none); and how lifted the loupe is.
  const jump = useSharedValue([0, 0, 0, 0]);
  const jumpT = useSharedValue(-1);
  const lift = useSharedValue(0);
  // The highlight's slide, smoothed.
  const tiltX = useSharedValue(0);
  const tiltY = useSharedValue(0);
  const script = useSharedValue(0);
  const hold = useSharedValue(0);
  const elapsed = useSharedValue(0);
  const shader = useSharedValue<{ image: SkShader; w: number; h: number; at: number } | null>(null);
  const stage = useSharedValue<Stage>({ width: 0, height: 0, top: 0, bottom: 0 });

  const width = size?.width ?? 0;
  const height = size?.height ?? 0;

  useEffect(() => {
    stage.set({ width, height, top: insets.top, bottom: insets.bottom });
  }, [width, height, insets.top, insets.bottom, stage]);

  useEffect(() => {
    script.set(0);
    hold.set(0);
  }, [auto, script, hold]);

  useEffect(() => {
    let alive = true;
    Skia.Data.fromURI(photoUrl(photo))
      .then((data) => Skia.Image.MakeImageFromEncoded(data))
      .then((image: SkImage | null) => {
        if (!alive || !image) return;
        const s = image.makeShaderOptions(TileMode.Clamp, TileMode.Clamp, FilterMode.Linear, MipmapMode.Linear);
        shader.set({ image: s, w: image.width(), h: image.height(), at: elapsed.get() });
      })
      .catch(() => {});
    return () => {
      alive = false;
    };
  }, [photo, shader, elapsed]);

  const buzz = (kind: 'tick' | 'land') => {
    if (process.env.EXPO_OS === 'web') return;
    if (kind === 'tick') Haptics.selectionAsync().catch(() => {});
    else Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
  };

  /** Keeps the loupe's middle on the screen. */
  const inside = (px: number, py: number) => {
    'worklet';
    const room = stage.get();
    const m = RADIUS * EDGE_KEEP;
    return [Math.max(m, Math.min(room.width - m, px)), Math.max(room.top + m, Math.min(room.height - room.bottom - m, py))];
  };

  const touched = () => {
    'worklet';
    hold.set(USER_HOLD);
  };

  useFrameCallback((info) => {
    const dt = Math.min(info.timeSincePreviousFrame ?? 16, 64) / 1000;
    if (dt <= 0) return;
    elapsed.set(elapsed.get() + dt);
    const room = stage.get();
    if (room.width <= 0) return;
    if (!placed.get()) {
      // Start resting on the first stop.
      const at = [stops[0][0] * room.width, stops[0][1] * room.height];
      x.set(at[0]);
      y.set(at[1]);
      tx.set(at[0]);
      ty.set(at[1]);
      zoom.set(auto ? stops[0][2] : ZOOM);
      zoomTo.set(zoom.get());
      placed.set(true);
    }

    let lifted = 0;
    if (auto && hold.get() <= 0 && !dragging.get()) {
      const t = (script.get() + dt) % (stops.length * (TRAVEL + DWELL));
      script.set(t);
      const w = wander(stops, t);
      tx.set(w.x * room.width);
      ty.set(w.y * room.height);
      zoomTo.set(w.zoom);
      if (w.jump) {
        // The jump leg flies on its own arc, not the spring.
        x.set(tx.get());
        y.set(ty.get());
        vx.set(0);
        vy.set(0);
        lifted = w.lift;
      }
    } else if (hold.get() > 0 && !dragging.get()) {
      hold.set(hold.get() - dt);
      if (hold.get() <= 0) script.set(0);
    }

    if (jumpT.get() >= 0) {
      const t = jumpT.get() + dt;
      const p = clamp01(t / JUMP_TIME);
      const j = jump.get();
      const q = arc(j[0], j[1], j[2], j[3], JUMP_ARC, easeInOut(p));
      x.set(q[0]);
      y.set(q[1]);
      lifted = Math.sin(Math.PI * p);
      if (p >= 1) {
        jumpT.set(-1);
        tx.set(j[2]);
        ty.set(j[3]);
        vx.set(0);
        vy.set(0);
        scheduleOnRN(buzz, 'land');
      } else {
        jumpT.set(t);
      }
    } else if (!(auto && hold.get() <= 0 && lifted > 0)) {
      if (reducedMotion) {
        x.set(tx.get());
        y.set(ty.get());
        vx.set(0);
        vy.set(0);
      } else {
        // Long frames are split in two: this stiff spring is not stable over a 64 ms step (it would
        // oscillate out to infinity at a sustained 15 fps). At 60 fps nothing changes.
        const steps = dt > 0.034 ? 2 : 1;
        const h = dt / steps;
        for (let k = 0; k < steps; k += 1) {
          vx.set(vx.get() + (STIFFNESS * (tx.get() - x.get()) - DAMPING * vx.get()) * h);
          vy.set(vy.get() + (STIFFNESS * (ty.get() - y.get()) - DAMPING * vy.get()) * h);
          x.set(x.get() + vx.get() * h);
          y.set(y.get() + vy.get() * h);
        }
      }
    }
    lift.set(lifted);

    // The zoom springs to its target; each whole step it passes under a pinch ticks.
    const before = zoom.get();
    zoomSpeed.set(zoomSpeed.get() + (ZOOM_STIFFNESS * (zoomTo.get() - zoom.get()) - ZOOM_DAMPING * zoomSpeed.get()) * dt);
    zoom.set(reducedMotion ? zoomTo.get() : zoom.get() + zoomSpeed.get() * dt);

    // The highlight slides against the loupe's speed.
    const ax = reducedMotion ? 0 : Math.max(-TILT_MAX, Math.min(TILT_MAX, -vx.get() * TILT_PER_SPEED * 10));
    const ay = reducedMotion ? 0 : Math.max(-TILT_MAX, Math.min(TILT_MAX, -vy.get() * TILT_PER_SPEED * 10));
    const ease = 1 - Math.exp(-dt / 0.1);
    tiltX.set(tiltX.get() + (ax - tiltX.get()) * ease);
    tiltY.set(tiltY.get() + (ay - tiltY.get()) * ease);
    if (dragging.get() && Math.floor(before) !== Math.floor(zoom.get())) scheduleOnRN(buzz, 'tick');
  });

  const pan = Gesture.Pan()
    .minDistance(2)
    .averageTouches(true)
    .onStart((event) => {
      touched();
      jumpT.set(-1);
      dragging.set(true);
      // The loupe keeps its distance from the finger, so a finger below it does not hide it.
      grabX.set(tx.get() - event.x);
      grabY.set(ty.get() - event.y);
      if (Math.hypot(x.get() - tx.get(), y.get() - ty.get()) > RADIUS) {
        grabX.set(x.get() - event.x);
        grabY.set(y.get() - event.y);
      }
    })
    .onUpdate((event) => {
      const room = stage.get();
      const m = RADIUS * EDGE_KEEP;
      tx.set(rubber(event.x + grabX.get(), m, room.width - m, EDGE_BAND));
      ty.set(rubber(event.y + grabY.get(), room.top + m, room.height - room.bottom - m, EDGE_BAND));
    })
    .onEnd((event) => {
      // A velocity the recogniser could not measure counts as none.
      const velX = Number.isFinite(event.velocityX) ? event.velocityX : 0;
      const velY = Number.isFinite(event.velocityY) ? event.velocityY : 0;
      const thrown = inside(tx.get() + (reducedMotion ? 0 : velX * THROW), ty.get() + (reducedMotion ? 0 : velY * THROW));
      tx.set(thrown[0]);
      ty.set(thrown[1]);
    })
    .onFinalize(() => {
      dragging.set(false);
      touched();
    });

  const pinch = Gesture.Pinch()
    .onStart(() => {
      touched();
      dragging.set(true);
      zoomFrom.set(zoomTo.get());
    })
    .onUpdate((event) => {
      // A scale that is not a number yet (one finger, no span) leaves the zoom where it is.
      if (!Number.isFinite(event.scale) || event.scale <= 0) return;
      zoomTo.set(rubber(zoomFrom.get() * event.scale, MIN_ZOOM, MAX_ZOOM, 0.6));
    })
    .onEnd(() => {
      zoomTo.set(Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, zoomTo.get())));
    })
    .onFinalize(() => {
      dragging.set(false);
      touched();
    });

  const tap = Gesture.Tap()
    .maxDuration(300)
    .onEnd((event, success) => {
      if (!success) return;
      touched();
      if (Math.hypot(event.x - x.get(), event.y - y.get()) < RADIUS + RIM) {
        // On the glass: a closer look, or back.
        zoomTo.set(zoomTo.get() < (ZOOM + CLOSE_ZOOM) / 2 ? CLOSE_ZOOM : ZOOM);
        scheduleOnRN(buzz, 'tick');
        return;
      }
      const to = inside(event.x, event.y);
      if (reducedMotion) {
        x.set(to[0]);
        y.set(to[1]);
        tx.set(to[0]);
        ty.set(to[1]);
        return;
      }
      jump.set([x.get(), y.get(), to[0], to[1]]);
      jumpT.set(0);
    });

  const picture = useDerivedValue(() => {
    const room = stage.get();
    const recorder = Skia.PictureRecorder();
    const canvas = recorder.beginRecording(Skia.XYWHRect(0, 0, room.width, room.height));
    if (room.width <= 0) return recorder.finishRecordingAsPicture();
    const img = shader.get();
    const l = lift.get();
    const grow = 1 + JUMP_LIFT * l;
    const r = RADIUS * grow;
    const cx = x.get();
    const cy = y.get();
    const z = zoom.get();
    const shadowAt = [SHADOW[0] + (SHADOW_LIFTED[0] - SHADOW[0]) * l, SHADOW[1] + (SHADOW_LIFTED[1] - SHADOW[1]) * l, SHADOW[2] + (SHADOW_LIFTED[2] - SHADOW[2]) * l];
    const paint = Skia.Paint();
    if (img && LENS) {
      const shown = clamp01((elapsed.get() - img.at) / IMAGE_FADE);
      paint.setShader(
        LENS.makeShaderWithChildren(
          [img.w, img.h, room.width, room.height, cx, cy, r, RIM * grow, z, DIM, shadowAt[0], shadowAt[1], shadowAt[2], BARREL, FRINGE, shown],
          [img.image],
        ),
      );
    } else {
      paint.setColor(Skia.Color('#0b0c10'));
    }
    canvas.drawRect(Skia.XYWHRect(0, 0, room.width, room.height), paint);

    // The caption, over a dark fade at the bottom, under the loupe.
    canvas.save();
    canvas.clipPath(Skia.PathBuilder.Make().addCircle(cx, cy, r + RIM * grow).build(), ClipOp.Difference, true);
    const fade = Skia.Paint();
    fade.setShader(Skia.Shader.MakeLinearGradient(vec(0, room.height - 170), vec(0, room.height), [Skia.Color('rgba(0,0,0,0)'), Skia.Color('rgba(0,0,0,0.7)')], null, TileMode.Clamp));
    canvas.drawRect(Skia.XYWHRect(0, room.height - 170, room.width, 170), fade);
    const ink = Skia.Paint();
    ink.setAntiAlias(true);
    ink.setColor(Skia.Color('#f3f1ea'));
    const base = room.height - room.bottom - 18;
    canvas.drawText(title, 22, base - 18, ink, fonts.title);
    ink.setAlphaf(0.6);
    canvas.drawText(meta, 22, base, ink, fonts.meta);
    canvas.restore();

    drawGloss(canvas, cx, cy, r, tiltX.get(), tiltY.get());
    drawRim(canvas, cx, cy, r, RIM * grow, tiltX.get() * 90, z, fonts.tab);
    return recorder.finishRecordingAsPicture();
  });

  return (
    <GestureDetector gesture={Gesture.Race(tap, Gesture.Simultaneous(pan, pinch))}>
      <View
        style={styles.screen}
        accessible
        accessibilityLabel={`${title}. A magnifying loupe over the photo: drag to move it, pinch to zoom, tap elsewhere to send it there.`}
        onLayout={(event: LayoutChangeEvent) => {
          const layout = event.nativeEvent.layout;
          setSize({ width: layout.width, height: layout.height });
        }}
      >
        <Canvas style={StyleSheet.absoluteFill}>
          <Picture picture={picture} />
        </Canvas>
      </View>
    </GestureDetector>
  );
}

const styles = StyleSheet.create({
  screen: {
    flex: 1,
    overflow: 'hidden',
    backgroundColor: '#0b0c10',
  },
});
