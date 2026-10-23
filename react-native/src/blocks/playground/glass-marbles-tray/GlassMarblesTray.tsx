import {
  BlendMode,
  BlurStyle,
  Canvas,
  ClipOp,
  matchFont,
  PaintStyle,
  Picture,
  Skia,
  type SkCanvas,
  type SkImage,
  type SkPicture,
  TileMode,
} from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { useEffect, useMemo, useState } from 'react';
import { type LayoutChangeEvent, StyleSheet, Text, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import { useDerivedValue, useFrameCallback, useReducedMotion, useSharedValue } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

import {
  blendPositions,
  blendRotations,
  copyWorld,
  createWorld,
  CUP_R,
  flashLife,
  FLASHES,
  flick,
  LOOP,
  makeTray,
  type Marble,
  MAX_TICKS,
  scatter,
  scriptPose,
  SHAKE_AT,
  stepWorld,
  TICK,
  type Tray,
  type World,
} from './marblesSim';

type Props = {
  /** Tilts the tray round in a circle, shakes it, lets the marbles settle home, on a loop. */
  autoPlay?: boolean;
  label?: string;
  title?: string;
  subtitle?: string;
};

// The set: glass tint, the two colours of the swirl, and how many vanes it has. The seventh is
// the larger shooter.
const MARBLES: Marble[] = [
  { r: 14, glass: [150, 190, 255], vane: [28, 66, 196], vane2: [120, 200, 255], vanes: 3 },
  { r: 14, glass: [255, 204, 128], vane: [214, 86, 20], vane2: [255, 222, 150], vanes: 3 },
  { r: 14, glass: [140, 228, 170], vane: [248, 248, 236], vane2: [36, 146, 88], vanes: 3 },
  { r: 14, glass: [255, 226, 214], vane: [138, 22, 34], vane2: [240, 148, 118], vanes: 4 },
  { r: 14, glass: [228, 240, 246], vane: [255, 255, 255], vane2: [186, 208, 230], vanes: 3 },
  { r: 14, glass: [212, 182, 255], vane: [106, 48, 188], vane2: [232, 204, 255], vanes: 3 },
  { r: 17.5, glass: [242, 240, 232], vane: [214, 38, 40], vane2: [248, 196, 40], vanes: 4 },
  { r: 14, glass: [150, 238, 232], vane: [0, 136, 148], vane2: [255, 255, 255], vanes: 3 },
  { r: 14, glass: [204, 204, 210], vane: [28, 28, 34], vane2: [160, 160, 172], vanes: 3 },
  { r: 14, glass: [255, 226, 150], vane: [158, 88, 28], vane2: [255, 244, 200], vanes: 3 },
  { r: 14, glass: [255, 202, 216], vane: [218, 58, 108], vane2: [255, 230, 240], vanes: 3 },
  { r: 14, glass: [214, 248, 160], vane: [88, 158, 30], vane2: [244, 255, 220], vanes: 3 },
  { r: 14, glass: [172, 176, 255], vane: [30, 30, 118], vane2: [255, 190, 88], vanes: 3 },
];

// The tray (pt): outer size limits, the frame's width and the depth of its sides.
const TRAY_MAX_W = 372;
const TRAY_RATIO = 1.34;
const FRAME = 24;
const TRAY_DEPTH = 16;
const OUTER_CORNER = 28;
// Below this width (pt) the tray has no room for its cups and is not built.
const MIN_TRAY_W = 150;
// Perspective: the eye is FOCAL pt above the tray.
const FOCAL = 900;
// Tilt: at most MAX_TILT (rad) when dragged; a drag of DRAG_SCALE pt gets about three quarters
// of the way (tanh), and the tray slides with the finger by SLIDE_SHARE, up to SLIDE_MAX pt.
const MAX_TILT = 0.24;
const DRAG_SCALE = 150;
const SLIDE_SHARE = 0.07;
const SLIDE_MAX = 16;
// A shake: the finger reverses SHAKE_REVERSALS times within SHAKE_WINDOW s, each faster than
// SHAKE_SPEED pt/s.
const SHAKE_SPEED = 1000;
const SHAKE_WINDOW = 0.5;
const SHAKE_REVERSALS = 2;
const SHAKE_COOLDOWN = 0.7;
// A tap within this reach (pt) of a marble flicks it away from the finger at FLICK_SPEED pt/s.
const TAP_REACH = 12;
const FLICK_SPEED = 620;
// After a touch, autoPlay waits this long (s).
const USER_HOLD = 3.5;
// Haptic clicks, at most one every CLICK_GAP s.
const CLICK_GAP = 0.09;
// The last REST_BLEND s of a loop ease every marble onto the state the loop started from.
const REST_BLEND = 1.1;

// Light comes from the upper left, in front of the tray.
const LIGHT = [-0.45, -0.62, 0.64];
const SANS = process.env.EXPO_OS === 'ios' ? 'Avenir Next' : 'sans-serif';
const SERIF = process.env.EXPO_OS === 'ios' ? 'Baskerville' : 'serif';
const INK = '#f4e9dc';
const MUTED = '#a8927e';
const TABLE = ['#3b2c22', '#1b130e', '#0d0907'];

// Wood: growth rings warped by value noise, stretched along the grain; fixed loop-free maths.
const WOOD_SKSL = `
uniform float2 stretch;
uniform float3 light;
uniform float3 dark;
uniform float seed;
float hash(float2 p) { return fract(sin(dot(p, float2(127.1, 311.7))) * 43758.5453); }
float noise(float2 p) {
  float2 i = floor(p);
  float2 f = fract(p);
  float2 u = f * f * (3.0 - 2.0 * f);
  return mix(mix(hash(i), hash(i + float2(1.0, 0.0)), u.x), mix(hash(i + float2(0.0, 1.0)), hash(i + float2(1.0, 1.0)), u.x), u.y);
}
half4 main(float2 p) {
  float2 q = p * stretch + float2(seed, seed * 0.37);
  float n = noise(q * float2(0.5, 3.0)) * 0.6 + noise(q * float2(1.1, 8.0)) * 0.3 + noise(q * float2(2.7, 19.0)) * 0.1;
  float rings = fract(q.y * 1.4 + n * 2.4);
  float grain = smoothstep(0.0, 0.8, rings) * (1.0 - smoothstep(0.8, 1.0, rings));
  float fleck = noise(q * float2(30.0, 2.0));
  float t = clamp(0.2 + 0.55 * grain + 0.25 * n + 0.1 * (fleck - 0.5), 0.0, 1.0);
  return half4(half3(mix(dark, light, t)), 1.0);
}`;
// Null if the effect fails to compile: the wood then falls back to a flat mid-tone.
const WOOD = Skia.RuntimeEffect.Make(WOOD_SKSL);

type Pose = {
  cx: number;
  cy: number;
  tiltX: number;
  tiltY: number;
};

type Scene = {
  tray: Tray;
  world: World;
  rest: World;
  backdrop: SkPicture;
  /** Where each marble was last drawn on screen, for taps. */
  sx: Float64Array;
  sy: Float64Array;
  sr: Float64Array;
  /** Seconds since the last click haptic. */
  sinceClick: number;
};

function rgba(c: number[], a: number) {
  'worklet';
  return new Float32Array([c[0] / 255, c[1] / 255, c[2] / 255, Math.max(0, Math.min(1, a))]);
}

function mix(a: number[], b: number[], t: number) {
  'worklet';
  return [a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t];
}

/** A point of the tray's frame (u, v, w; w up out of the floor) turned by the tilt, about its centre. */
function turnTray(p: Pose, u: number, v: number, w: number) {
  'worklet';
  const ca = Math.cos(p.tiltX);
  const sa = Math.sin(p.tiltX);
  const cb = Math.cos(p.tiltY);
  const sb = Math.sin(p.tiltY);
  const y1 = v * ca - w * sa;
  const z1 = v * sa + w * ca;
  return { x: u * cb + z1 * sb, y: y1, z: -u * sb + z1 * cb };
}

/** A point of the tray's frame on screen, and its perspective scale. */
function project(p: Pose, u: number, v: number, w: number) {
  'worklet';
  const t = turnTray(p, u, v, w);
  const s = FOCAL / (FOCAL - t.z);
  return { x: p.cx + t.x * s, y: p.cy + t.y * s, s };
}

/** The homography that draws the tray's floor plane (w = 0) on screen. */
function floorMatrix(p: Pose) {
  'worklet';
  const ca = Math.cos(p.tiltX);
  const sa = Math.sin(p.tiltX);
  const cb = Math.cos(p.tiltY);
  const sb = Math.sin(p.tiltY);
  const F = FOCAL;
  return Skia.Matrix([
    cb + (p.cx * sb) / F,
    sa * sb - (p.cx * sa * cb) / F,
    p.cx,
    (p.cy * sb) / F,
    ca - (p.cy * sa * cb) / F,
    p.cy,
    sb / F,
    -(sa * cb) / F,
    1,
  ]);
}

/** The light in the tray's own frame: it moves across the floor as the tray tilts. */
function trayLight(p: Pose) {
  'worklet';
  const cb = Math.cos(p.tiltY);
  const sb = Math.sin(p.tiltY);
  const ca = Math.cos(p.tiltX);
  const sa = Math.sin(p.tiltX);
  const x1 = LIGHT[0] * cb - LIGHT[2] * sb;
  const z1 = LIGHT[0] * sb + LIGHT[2] * cb;
  const y1 = LIGHT[1];
  return { x: x1, y: y1 * ca + z1 * sa, z: -y1 * sa + z1 * ca };
}

/** The table under the tray: warm dark wood lit from above. */
function makeBackdrop(width: number, height: number, cy: number): SkPicture {
  'worklet';
  const recorder = Skia.PictureRecorder();
  const canvas = recorder.beginRecording(Skia.XYWHRect(0, 0, width, height));
  const table = Skia.Paint();
  table.setShader(
    Skia.Shader.MakeRadialGradient(
      Skia.Point(width * 0.42, cy - 40),
      Math.max(width, height) * 0.75,
      TABLE.map((c) => Skia.Color(c)),
      [0, 0.55, 1],
      TileMode.Clamp,
    ),
  );
  canvas.drawRect(Skia.XYWHRect(0, 0, width, height), table);
  return recorder.finishRecordingAsPicture();
}

/** Fills `path` with wood: grain along x, or along y when `across`. */
function woodPaint(lightRGB: number[], darkRGB: number[], seed: number, across: boolean, scale: number) {
  const paint = Skia.Paint();
  paint.setAntiAlias(true);
  if (!WOOD) {
    paint.setColor(rgba(mix(darkRGB, lightRGB, 0.5), 1));
    return paint;
  }
  const local = Skia.Matrix();
  if (across) local.rotate(Math.PI / 2);
  paint.setShader(
    WOOD.makeShader(
      [
        0.012 * scale,
        0.05 * scale,
        lightRGB[0] / 255,
        lightRGB[1] / 255,
        lightRGB[2] / 255,
        darkRGB[0] / 255,
        darkRGB[1] / 255,
        darkRGB[2] / 255,
        seed,
      ],
      local,
    ),
  );
  return paint;
}

/**
 * Bakes the tray's top once, on a CPU surface at `density` pixels per pt: a mitred walnut frame
 * with bevelled edges and brass pins, an oak floor, and the carved cups.
 */
function bakeTray(trayW: number, trayH: number, tray: Tray, density: number): SkImage | null {
  const surface = Skia.Surface.Make(Math.ceil(trayW * density), Math.ceil(trayH * density));
  if (!surface) return null;
  const canvas = surface.getCanvas();
  canvas.scale(density, density);
  const outer = Skia.RRectXY(Skia.XYWHRect(0, 0, trayW, trayH), OUTER_CORNER, OUTER_CORNER);
  const innerRect = Skia.XYWHRect(FRAME, FRAME, trayW - FRAME * 2, trayH - FRAME * 2);
  const inner = Skia.RRectXY(innerRect, tray.corner, tray.corner);
  const walnutLight = [138, 88, 52];
  const walnutDark = [62, 34, 18];

  // The frame: four mitred boards, the long ones with the grain along them.
  canvas.save();
  canvas.clipRRect(outer, ClipOp.Intersect, true);
  const boards: [number[], boolean][] = [
    [[0, 0, trayW, 0, trayW - FRAME, FRAME, FRAME, FRAME], false],
    [[0, trayH, trayW, trayH, trayW - FRAME, trayH - FRAME, FRAME, trayH - FRAME], false],
    [[0, 0, FRAME, FRAME, FRAME, trayH - FRAME, 0, trayH], true],
    [[trayW, 0, trayW - FRAME, FRAME, trayW - FRAME, trayH - FRAME, trayW, trayH], true],
  ];
  boards.forEach(([p, across], k) => {
    const b = Skia.PathBuilder.Make();
    b.moveTo(p[0], p[1]);
    b.lineTo(p[2], p[3]);
    b.lineTo(p[4], p[5]);
    b.lineTo(p[6], p[7]);
    b.close();
    canvas.drawPath(b.build(), woodPaint(walnutLight, walnutDark, 3.1 + k * 5.7, across, 1));
    // The mitre joints: a hairline at both ends of each long board, so every corner gets one.
    if (across) return;
    const joint = Skia.Paint();
    joint.setAntiAlias(true);
    joint.setStyle(PaintStyle.Stroke);
    joint.setStrokeWidth(0.6);
    joint.setColor(Skia.Color('rgba(30, 14, 6, 0.55)'));
    canvas.drawLine(p[0], p[1], p[6], p[7], joint);
    canvas.drawLine(p[2], p[3], p[4], p[5], joint);
  });
  canvas.restore();

  // Bevels: a lit outer edge on the upper left, a shaded one on the lower right.
  const bevel = Skia.Paint();
  bevel.setAntiAlias(true);
  bevel.setStyle(PaintStyle.Stroke);
  bevel.setStrokeWidth(2.5);
  bevel.setShader(
    Skia.Shader.MakeLinearGradient(
      Skia.Point(0, 0),
      Skia.Point(trayW, trayH),
      [Skia.Color('rgba(255, 226, 190, 0.5)'), Skia.Color('rgba(255, 226, 190, 0.05)'), Skia.Color('rgba(20, 8, 2, 0.5)')],
      [0, 0.5, 1],
      TileMode.Clamp,
    ),
  );
  canvas.drawRRect(Skia.RRectXY(Skia.XYWHRect(1.25, 1.25, trayW - 2.5, trayH - 2.5), OUTER_CORNER - 1, OUTER_CORNER - 1), bevel);
  // The inner chamfer falls towards the floor: shaded on the lit side, lit on the far side.
  const chamfer = Skia.Paint();
  chamfer.setAntiAlias(true);
  chamfer.setStyle(PaintStyle.Stroke);
  chamfer.setStrokeWidth(5);
  chamfer.setShader(
    Skia.Shader.MakeLinearGradient(
      Skia.Point(FRAME, FRAME),
      Skia.Point(trayW - FRAME, trayH - FRAME),
      [Skia.Color('rgba(28, 12, 4, 0.6)'), Skia.Color('rgba(255, 220, 180, 0.35)')],
      null,
      TileMode.Clamp,
    ),
  );
  canvas.drawRRect(Skia.RRectXY(Skia.XYWHRect(FRAME - 2.5, FRAME - 2.5, innerRect.width + 5, innerRect.height + 5), tray.corner + 2.5, tray.corner + 2.5), chamfer);

  // The floor: pale oak, grain running the long way.
  canvas.save();
  canvas.clipRRect(inner, ClipOp.Intersect, true);
  canvas.drawRect(innerRect, woodPaint([228, 190, 140], [186, 136, 88], 11.3, true, 0.6));
  // The walls shade the floor along the lit side.
  const wallShade = Skia.Paint();
  wallShade.setAntiAlias(true);
  wallShade.setStyle(PaintStyle.Stroke);
  wallShade.setStrokeWidth(22);
  wallShade.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 6, true));
  wallShade.setShader(
    Skia.Shader.MakeLinearGradient(
      Skia.Point(FRAME, FRAME),
      Skia.Point(trayW - FRAME, trayH - FRAME),
      [Skia.Color('rgba(50, 24, 8, 0.55)'), Skia.Color('rgba(50, 24, 8, 0.12)')],
      null,
      TileMode.Clamp,
    ),
  );
  canvas.drawRRect(Skia.RRectXY(Skia.XYWHRect(FRAME - 6, FRAME - 6, innerRect.width + 12, innerRect.height + 12), tray.corner + 6, tray.corner + 6), wallShade);

  // Cups: shallow bowls, dark on the side towards the light and lit on the far rim.
  const cx0 = trayW / 2;
  const cy0 = trayH / 2;
  for (let c = 0; c < tray.cupX.length; c += 1) {
    const x = cx0 + tray.cupX[c];
    const y = cy0 + tray.cupY[c];
    const bowl = Skia.Paint();
    bowl.setAntiAlias(true);
    bowl.setShader(
      Skia.Shader.MakeRadialGradient(
        Skia.Point(x + 3, y + 3.5),
        CUP_R * 1.15,
        [Skia.Color('rgba(255, 236, 205, 0.32)'), Skia.Color('rgba(120, 70, 30, 0.12)'), Skia.Color('rgba(70, 36, 12, 0.5)')],
        [0, 0.62, 1],
        TileMode.Clamp,
      ),
    );
    canvas.drawCircle(x, y, CUP_R, bowl);
    const rim = Skia.Paint();
    rim.setAntiAlias(true);
    rim.setStyle(PaintStyle.Stroke);
    rim.setStrokeWidth(1.2);
    rim.setShader(
      Skia.Shader.MakeLinearGradient(
        Skia.Point(x - CUP_R, y - CUP_R),
        Skia.Point(x + CUP_R, y + CUP_R),
        [Skia.Color('rgba(60, 30, 10, 0.55)'), Skia.Color('rgba(255, 240, 215, 0.55)')],
        null,
        TileMode.Clamp,
      ),
    );
    canvas.drawCircle(x, y, CUP_R, rim);
  }
  // A satin varnish sheen across the floor.
  const sheen = Skia.Paint();
  sheen.setShader(
    Skia.Shader.MakeLinearGradient(
      Skia.Point(FRAME, FRAME),
      Skia.Point(trayW * 0.7, trayH * 0.6),
      [Skia.Color('rgba(255, 250, 235, 0.14)'), Skia.Color('rgba(255, 250, 235, 0)')],
      null,
      TileMode.Clamp,
    ),
  );
  canvas.drawRect(innerRect, sheen);
  canvas.restore();

  // Brass pins at the mitres.
  const pins = [
    [FRAME / 2, FRAME / 2],
    [trayW - FRAME / 2, FRAME / 2],
    [FRAME / 2, trayH - FRAME / 2],
    [trayW - FRAME / 2, trayH - FRAME / 2],
  ];
  for (const [x, y] of pins) {
    const pin = Skia.Paint();
    pin.setAntiAlias(true);
    pin.setShader(
      Skia.Shader.MakeRadialGradient(
        Skia.Point(x - 1.2, y - 1.2),
        3.6,
        [Skia.Color('#fff2c2'), Skia.Color('#c99a45'), Skia.Color('#6b4a1c')],
        [0, 0.5, 1],
        TileMode.Clamp,
      ),
    );
    canvas.drawCircle(x, y, 2.8, pin);
  }
  surface.flush();
  const image = surface.makeImageSnapshot();
  surface.dispose();
  return image;
}

/** The tray's outer edge, as a loop of points (pt, tray frame) for its sides. */
function rimLoop(halfW: number, halfH: number) {
  'worklet';
  const pts: number[] = [];
  const r = OUTER_CORNER;
  const corners = [
    [halfW - r, -halfH + r, -Math.PI / 2],
    [halfW - r, halfH - r, 0],
    [-halfW + r, halfH - r, Math.PI / 2],
    [-halfW + r, -halfH + r, Math.PI],
  ];
  for (const [x, y, a0] of corners) {
    for (let k = 0; k <= 6; k += 1) {
      const a = a0 + (Math.PI / 2) * (k / 6);
      pts.push(x + Math.cos(a) * r, y + Math.sin(a) * r);
    }
  }
  return pts;
}

/** The tray's sides: the faces that turn towards the eye as it tilts, shaded by the light. */
function drawSides(canvas: SkCanvas, p: Pose, rim: number[]) {
  'worklet';
  const n = rim.length / 2;
  for (let i = 0; i < n; i += 1) {
    const j = (i + 1) % n;
    const ax = rim[i * 2];
    const ay = rim[i * 2 + 1];
    const bx = rim[j * 2];
    const by = rim[j * 2 + 1];
    // The face's outward normal, turned with the tray (a direction: turn it about the origin).
    const l = Math.hypot(by - ay, bx - ax) || 1;
    const normal = turnTray(p, (by - ay) / l, -(bx - ax) / l, 0);
    // Seen when it faces the eye, which sits FOCAL pt above the tray's centre.
    const mid = turnTray(p, (ax + bx) / 2, (ay + by) / 2, -TRAY_DEPTH / 2);
    if (normal.x * -mid.x + normal.y * -mid.y + normal.z * (FOCAL - mid.z) <= 0) continue;
    const a0 = project(p, ax, ay, 0);
    const b0 = project(p, bx, by, 0);
    const b1 = project(p, bx, by, -TRAY_DEPTH);
    const a1 = project(p, ax, ay, -TRAY_DEPTH);
    const face = Skia.PathBuilder.Make();
    face.moveTo(a0.x, a0.y);
    face.lineTo(b0.x, b0.y);
    face.lineTo(b1.x, b1.y);
    face.lineTo(a1.x, a1.y);
    face.close();
    const lit = Math.max(0, normal.x * LIGHT[0] + normal.y * LIGHT[1] + normal.z * LIGHT[2]);
    const shade = 0.4 + 0.6 * lit;
    const paint = Skia.Paint();
    paint.setAntiAlias(true);
    paint.setColor(rgba([96 * shade + 18, 56 * shade + 10, 30 * shade + 6], 1));
    canvas.drawPath(face.build(), paint);
  }
}

/** A marble's shadow and the coloured light its glass focuses into it, on the floor. */
function drawShadow(canvas: SkCanvas, w: World, i: number, set: Marble, light: { x: number; y: number; z: number }) {
  'worklet';
  const r = w.r[i];
  const lift = r + w.z[i];
  const ox = (-light.x / light.z) * lift;
  const oy = (-light.y / light.z) * lift;
  const x = w.x[i];
  const y = w.y[i];
  const blur = 2.5 + w.z[i] * 0.08;
  const fade = 1 / (1 + w.z[i] * 0.02);
  // Contact shade right under it.
  const contact = Skia.Paint();
  contact.setAntiAlias(true);
  contact.setColor(rgba([40, 18, 4], 0.4 * fade));
  contact.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 2 + w.z[i] * 0.1, true));
  canvas.drawCircle(x + ox * 0.25, y + oy * 0.25, r * 0.62, contact);
  // The cast shadow, tinted by the glass.
  const tint = mix([46, 22, 6], set.glass, 0.35);
  const cast = Skia.Paint();
  cast.setAntiAlias(true);
  cast.setColor(rgba(tint, 0.58 * fade));
  cast.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, blur, true));
  canvas.save();
  canvas.translate(x + ox * 0.9, y + oy * 0.9);
  canvas.rotate((Math.atan2(oy, ox) * 180) / Math.PI, 0, 0);
  canvas.drawOval(Skia.XYWHRect(-r * 1.25, -r * 0.95, r * 2.5, r * 1.9), cast);
  canvas.restore();
  // The caustic: the glass gathers light into a bright, coloured spot inside its shadow.
  const caustic = Skia.Paint();
  caustic.setAntiAlias(true);
  caustic.setBlendMode(BlendMode.Plus);
  caustic.setShader(
    Skia.Shader.MakeRadialGradient(
      Skia.Point(x + ox * 1.05, y + oy * 1.05),
      r * 0.62,
      [rgba(mix(set.glass, [255, 255, 255], 0.5), 0.8 * fade), rgba(set.glass, 0.35 * fade), rgba(set.glass, 0)],
      [0, 0.4, 1],
      TileMode.Clamp,
    ),
  );
  canvas.drawCircle(x + ox * 1.05, y + oy * 1.05, r * 0.62, caustic);
}

/** One marble on screen: the glass, the swirl that rolls with it, the inner glow and the shine. */
function drawMarble(canvas: SkCanvas, cx: number, cy: number, rs: number, set: Marble, rot: Float64Array, o: number) {
  'worklet';
  const clip = Skia.PathBuilder.Make().addCircle(cx, cy, rs).build();
  // The glass body: clear in the middle, the tint thick at its edges.
  const body = Skia.Paint();
  body.setAntiAlias(true);
  body.setShader(
    Skia.Shader.MakeRadialGradient(
      Skia.Point(cx + rs * 0.18, cy + rs * 0.22),
      rs * 1.08,
      [rgba(mix(set.glass, [255, 255, 255], 0.4), 0.5), rgba(set.glass, 0.62), rgba(mix(set.glass, [20, 20, 30], 0.55), 0.92)],
      [0, 0.62, 1],
      TileMode.Clamp,
    ),
  );
  canvas.drawCircle(cx, cy, rs, body);

  canvas.save();
  canvas.clipPath(clip, ClipOp.Intersect, true);
  // The swirl: leaf-shaped vanes through the centre, each in a plane through the marble's own
  // axis, turned by its orientation and seen straight on. Far halves sit behind the glass.
  for (let k = 0; k < set.vanes; k += 1) {
    const psi = (Math.PI * k) / set.vanes;
    const ex = Math.cos(psi) * 0.74;
    const ey = Math.sin(psi) * 0.74;
    // Two tips on the equator and two on the axis, slightly twisted.
    const tips = [ex, ey, 0, -Math.sin(psi) * 0.16, Math.cos(psi) * 0.16, 0.72, -ex, -ey, 0, Math.sin(psi) * 0.16, -Math.cos(psi) * 0.16, -0.72];
    const px: number[] = [];
    const py: number[] = [];
    let depth = 0;
    for (let t = 0; t < 4; t += 1) {
      const lx = tips[t * 3];
      const ly = tips[t * 3 + 1];
      const lz = tips[t * 3 + 2];
      px.push(cx + (rot[o] * lx + rot[o + 1] * ly + rot[o + 2] * lz) * rs);
      py.push(cy + (rot[o + 3] * lx + rot[o + 4] * ly + rot[o + 5] * lz) * rs);
      depth += rot[o + 6] * lx + rot[o + 7] * ly + rot[o + 8] * lz;
    }
    const leaf = Skia.PathBuilder.Make();
    leaf.moveTo(px[0], py[0]);
    for (let t = 1; t <= 4; t += 1) {
      const a = t - 1;
      const b = t % 4;
      // Bulge each edge outward from the centre, so the vane is a leaf, not a diamond.
      const mx = (px[a] + px[b]) / 2;
      const my = (py[a] + py[b]) / 2;
      leaf.quadTo(cx + (mx - cx) * 1.45, cy + (my - cy) * 1.45, px[b], py[b]);
    }
    leaf.close();
    const path = leaf.build();
    const vane = Skia.Paint();
    vane.setAntiAlias(true);
    const color = k % 2 === 0 ? set.vane : set.vane2;
    vane.setColor(rgba(color, 0.7 + 0.1 * Math.max(-1, Math.min(1, depth))));
    vane.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 0.7, true));
    canvas.drawPath(path, vane);
    const edge = Skia.Paint();
    edge.setAntiAlias(true);
    edge.setStyle(PaintStyle.Stroke);
    edge.setStrokeWidth(0.6);
    edge.setColor(rgba(mix(color, [255, 255, 255], 0.5), 0.5));
    canvas.drawPath(path, edge);
  }
  // The front of the glass lies over the swirl: a faint milky haze towards the light.
  const haze = Skia.Paint();
  haze.setShader(
    Skia.Shader.MakeRadialGradient(
      Skia.Point(cx - rs * 0.3, cy - rs * 0.35),
      rs * 1.1,
      [rgba([255, 255, 255], 0.22), rgba(set.glass, 0.06)],
      null,
      TileMode.Clamp,
    ),
  );
  canvas.drawCircle(cx, cy, rs, haze);
  // Light through the glass gathers on the far side, low right.
  const glow = Skia.Paint();
  glow.setBlendMode(BlendMode.Screen);
  glow.setShader(
    Skia.Shader.MakeRadialGradient(
      Skia.Point(cx + rs * 0.38, cy + rs * 0.44),
      rs * 0.7,
      [rgba(mix(set.glass, [255, 255, 255], 0.6), 0.6), rgba(set.glass, 0)],
      null,
      TileMode.Clamp,
    ),
  );
  canvas.drawCircle(cx, cy, rs, glow);
  // Thick glass at the rim: a darker ring.
  const fresnel = Skia.Paint();
  fresnel.setShader(
    Skia.Shader.MakeRadialGradient(
      Skia.Point(cx, cy),
      rs,
      [rgba(set.glass, 0), rgba(mix(set.glass, [10, 10, 20], 0.6), 0.55)],
      [0.72, 1],
      TileMode.Clamp,
    ),
  );
  canvas.drawCircle(cx, cy, rs, fresnel);
  canvas.restore();

  // The shine: a sharp highlight towards the light and a soft window reflection around it.
  const soft = Skia.Paint();
  soft.setShader(
    Skia.Shader.MakeRadialGradient(
      Skia.Point(cx - rs * 0.36, cy - rs * 0.4),
      rs * 0.55,
      [Skia.Color('rgba(255, 255, 255, 0.5)'), Skia.Color('rgba(255, 255, 255, 0)')],
      null,
      TileMode.Clamp,
    ),
  );
  canvas.drawCircle(cx - rs * 0.36, cy - rs * 0.4, rs * 0.55, soft);
  const spec = Skia.Paint();
  spec.setAntiAlias(true);
  spec.setColor(Skia.Color('rgba(255, 255, 255, 0.95)'));
  spec.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 0.6, true));
  canvas.save();
  canvas.translate(cx - rs * 0.4, cy - rs * 0.44);
  canvas.rotate(-38, 0, 0);
  canvas.drawOval(Skia.XYWHRect(-rs * 0.2, -rs * 0.11, rs * 0.4, rs * 0.22), spec);
  canvas.restore();
  const back = Skia.Paint();
  back.setAntiAlias(true);
  back.setColor(Skia.Color('rgba(255, 255, 255, 0.35)'));
  back.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 1, true));
  canvas.drawCircle(cx + rs * 0.46, cy + rs * 0.5, rs * 0.09, back);
  const rim = Skia.Paint();
  rim.setAntiAlias(true);
  rim.setStyle(PaintStyle.Stroke);
  rim.setStrokeWidth(0.8);
  rim.setColor(rgba(mix(set.glass, [0, 0, 0], 0.6), 0.45));
  canvas.drawCircle(cx, cy, rs - 0.4, rim);
}

/** A glint where two marbles clicked: a tiny four-pointed star that flares and fades. */
function drawFlash(canvas: SkCanvas, x: number, y: number, life: number, power: number) {
  'worklet';
  const k = 1 - life;
  const size = 3 + 9 * power * Math.sin(Math.PI * Math.min(1, life * 1.6 + 0.2));
  const paint = Skia.Paint();
  paint.setAntiAlias(true);
  paint.setBlendMode(BlendMode.Plus);
  paint.setStyle(PaintStyle.Stroke);
  paint.setStrokeWidth(1.1);
  paint.setColor(rgba([255, 246, 225], 0.9 * k * power));
  canvas.drawLine(x - size, y, x + size, y, paint);
  canvas.drawLine(x, y - size, x, y + size, paint);
  const dot = Skia.Paint();
  dot.setAntiAlias(true);
  dot.setBlendMode(BlendMode.Plus);
  dot.setColor(rgba([255, 240, 210], 0.7 * k * power));
  dot.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 2.5, true));
  canvas.drawCircle(x, y, 2.5 + 2 * power, dot);
}

/**
 * Thirteen glass marbles in a walnut tray with carved cups. Dragging tilts the tray in
 * perspective and the marbles roll downhill, click off each other and fall back into cups; a fast
 * shake scatters them into the air; a tap flicks one. Each throws a shadow lit through with its
 * own colour, and the light moves across the floor as the tray tilts.
 */
export function GlassMarblesTray({
  autoPlay = false,
  label = 'Parlour games · No. 4',
  title = 'Glass Marbles',
  subtitle = 'Twelve cat’s-eyes and a shooter in a walnut tray.',
}: Props) {
  const insets = useSafeAreaInsets();
  const reducedMotion = useReducedMotion();
  const auto = autoPlay && !reducedMotion;
  const [size, setSize] = useState<{ width: number; height: number } | null>(null);
  const fonts = useMemo(
    () => ({
      value: matchFont({ fontFamily: SANS, fontSize: 22, fontWeight: '600' }),
      small: matchFont({ fontFamily: SANS, fontSize: 10.5, fontWeight: '600' }),
    }),
    [],
  );

  const width = size?.width ?? 0;
  const height = size?.height ?? 0;
  // The tray: as wide as fits, centred between the header and the caption.
  const top = insets.top + 70;
  const bottom = height - insets.bottom - 118;
  const trayW = Math.min(TRAY_MAX_W, width - 30);
  const trayH = Math.min(trayW * TRAY_RATIO, Math.max(200, bottom - top));
  const cx = width / 2;
  const cy = (top + bottom) / 2;

  // No tray until there is room for its cups (a transient tiny layout would give it none).
  const tray = useMemo(
    () => (width > 0 && trayW >= MIN_TRAY_W ? makeTray(trayW / 2 - FRAME, trayH / 2 - FRAME, MARBLES.length) : null),
    [width, trayW, trayH],
  );
  const image = useSharedValue<SkImage | null>(null);
  useEffect(() => {
    if (!tray) return undefined;
    // Bake the tray's top once per size, at the screen's density, on the JS thread; the UI
    // thread only ever draws the finished image.
    const baked = bakeTray(trayW, trayH, tray, 3);
    image.set(baked);
    return () => {
      // Let the UI thread drop the image before it is freed.
      image.set(null);
      setTimeout(() => baked?.dispose(), 1000);
    };
  }, [tray, trayW, trayH, image]);

  const scene = useSharedValue<Scene | null>(null);
  const frameNo = useSharedValue(0);
  const acc = useSharedValue(0);
  // What the finger asks of the tray.
  const dragging = useSharedValue(false);
  const wantTiltX = useSharedValue(0);
  const wantTiltY = useSharedValue(0);
  const wantSlideX = useSharedValue(0);
  const wantSlideY = useSharedValue(0);
  // Shake detection: the last strong direction, when it was seen, and reversals so far.
  const lastDirX = useSharedValue(0);
  const lastDirY = useSharedValue(0);
  const lastStrongAt = useSharedValue(-10);
  const reversals = useSharedValue(0);
  const lastShakeAt = useSharedValue(-10);
  const now = useSharedValue(0);
  // Requests carried out on the next tick.
  const shakeNow = useSharedValue(false);
  const flickWho = useSharedValue(-1);
  const flickDX = useSharedValue(0);
  const flickDY = useSharedValue(0);
  const clock = useSharedValue(0);
  const hold = useSharedValue(0);
  const looping = useSharedValue(auto);

  useEffect(() => {
    looping.set(auto);
    hold.set(0);
    clock.set(0);
  }, [auto, clock, hold, looping]);

  useEffect(() => {
    scene.set(null);
  }, [tray, scene]);

  const clicked = (hard: number) => {
    if (process.env.EXPO_OS === 'web') return;
    if (hard > 420) Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Rigid).catch(() => {});
    else Haptics.selectionAsync().catch(() => {});
  };
  const shaken = () => {
    if (process.env.EXPO_OS !== 'web') Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Medium).catch(() => {});
  };
  const flicked = () => {
    if (process.env.EXPO_OS !== 'web') Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
  };

  const tick = (s: Scene) => {
    'worklet';
    const w = s.world;
    now.set(now.get() + TICK);
    s.sinceClick += TICK;
    if (shakeNow.get()) {
      shakeNow.set(false);
      scatter(w, reducedMotion ? 0.35 : 1);
    }
    const who = flickWho.get();
    if (who >= 0) {
      flickWho.set(-1);
      flick(w, who, flickDX.get(), flickDY.get(), reducedMotion ? FLICK_SPEED * 0.5 : FLICK_SPEED);
    }
    const input = { tiltX: 0, tiltY: 0, slideX: 0, slideY: 0, guide: 0 };
    if (dragging.get()) {
      input.tiltX = wantTiltX.get();
      input.tiltY = wantTiltY.get();
      input.slideX = wantSlideX.get();
      input.slideY = wantSlideY.get();
    }
    if (hold.get() > 0) {
      if (!dragging.get()) hold.set(hold.get() - TICK);
      if (hold.get() <= 0) clock.set(0);
    } else if (looping.get()) {
      const before = clock.get();
      let t = before + TICK;
      if (t >= LOOP) {
        t -= LOOP;
        // Home again: the loop starts over from exactly where it began.
        copyWorld(s.rest, w);
      }
      clock.set(t);
      const pose = scriptPose(t);
      input.tiltX = pose.tiltX;
      input.tiltY = pose.tiltY;
      input.slideX = pose.slideX;
      input.guide = pose.guide;
      if (before < SHAKE_AT && t >= SHAKE_AT) scatter(w, 1);
      if (t > LOOP - REST_BLEND) {
        const k = (t - (LOOP - REST_BLEND)) / REST_BLEND;
        blendPositions(w, s.rest, k * k * 0.25);
        blendRotations(w, s.rest, k * k * 0.12);
      }
    }
    stepWorld(w, s.tray, input, TICK);
    if (w.clicks > 0 && hold.get() > 0 && s.sinceClick > CLICK_GAP) {
      s.sinceClick = 0;
      scheduleOnRN(clicked, w.hardest);
    }
  };

  useFrameCallback((frame) => {
    if (width === 0 || !tray) return;
    let s = scene.get();
    if (s === null) {
      const world = createWorld(tray, MARBLES);
      const rest = createWorld(tray, MARBLES);
      s = {
        tray,
        world,
        rest,
        backdrop: makeBackdrop(width, height, cy),
        sx: new Float64Array(MARBLES.length),
        sy: new Float64Array(MARBLES.length),
        sr: new Float64Array(MARBLES.length),
        sinceClick: 1,
      };
      scene.set(s);
      clock.set(0);
    }
    const dt = Math.min((frame.timeSincePreviousFrame ?? 16) / 1000, MAX_TICKS * TICK);
    let pending = acc.get() + dt;
    let ticks = 0;
    while (pending >= TICK && ticks < MAX_TICKS) {
      tick(s);
      pending -= TICK;
      ticks += 1;
    }
    acc.set(Math.min(pending, TICK));
    if (ticks > 0) frameNo.set(frameNo.get() + 1);
  });

  const picture = useDerivedValue(() => {
    frameNo.get();
    const recorder = Skia.PictureRecorder();
    const canvas = recorder.beginRecording(Skia.XYWHRect(0, 0, width, height));
    const s = scene.get();
    const img = image.get();
    if (!s || width === 0) return recorder.finishRecordingAsPicture();
    canvas.drawPicture(s.backdrop);
    const w = s.world;
    const pose: Pose = { cx: cx + w.slideX, cy: cy + w.slideY, tiltX: w.tiltX, tiltY: w.tiltY };
    const halfW = trayW / 2;
    const halfH = trayH / 2;

    // The tray's shadow on the table, pushed away from the light and spread by the tilt.
    const tiltSize = Math.hypot(w.tiltX, w.tiltY);
    const shadow = Skia.Paint();
    shadow.setColor(rgba([4, 2, 1], 0.6));
    shadow.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 16 + tiltSize * 40, false));
    canvas.save();
    canvas.concat(floorMatrix(pose));
    canvas.drawRRect(
      Skia.RRectXY(Skia.XYWHRect(-halfW + 10, -halfH + 22, trayW, trayH), OUTER_CORNER, OUTER_CORNER),
      shadow,
    );
    canvas.restore();

    drawSides(canvas, pose, rimLoop(halfW, halfH));

    canvas.save();
    canvas.concat(floorMatrix(pose));
    if (img) {
      const paint = Skia.Paint();
      paint.setAntiAlias(true);
      canvas.drawImageRect(img, Skia.XYWHRect(0, 0, img.width(), img.height()), Skia.XYWHRect(-halfW, -halfH, trayW, trayH), paint);
    }
    const light = trayLight(pose);
    for (let i = 0; i < w.n; i += 1) drawShadow(canvas, w, i, MARBLES[i], light);
    canvas.restore();

    // Marbles, lowest first so a hopping one passes over the rest.
    const order: number[] = [];
    for (let i = 0; i < w.n; i += 1) {
      // Insertion by height: the list is short and nearly always already in order.
      let at = order.length;
      while (at > 0 && w.z[order[at - 1]] > w.z[i]) at -= 1;
      order.splice(at, 0, i);
    }
    for (let k = 0; k < order.length; k += 1) {
      const i = order[k];
      const p = project(pose, w.x[i], w.y[i], w.r[i] + w.z[i]);
      const rs = w.r[i] * p.s;
      s.sx[i] = p.x;
      s.sy[i] = p.y;
      s.sr[i] = rs;
      drawMarble(canvas, p.x, p.y, rs, MARBLES[i], w.rot, i * 9);
    }
    const life = flashLife();
    for (let f = 0; f < FLASHES; f += 1) {
      if (w.flashAge[f] >= life) continue;
      const p = project(pose, w.flashX[f], w.flashY[f], 12);
      drawFlash(canvas, p.x, p.y, w.flashAge[f] / life, w.flashPower[f]);
    }

    // The tilt readout, top right.
    const degrees = (tiltSize * 180) / Math.PI;
    const value = `${degrees.toFixed(1)}°`;
    const ink = Skia.Paint();
    ink.setAntiAlias(true);
    ink.setColor(Skia.Color(INK));
    const right = width - 24;
    const readTop = insets.top + 12;
    canvas.drawText(value, right - fonts.value.measureText(value).width, readTop + 38, ink, fonts.value);
    const muted = Skia.Paint();
    muted.setAntiAlias(true);
    muted.setColor(Skia.Color(MUTED));
    canvas.drawText('TILT', right - fonts.small.measureText('TILT').width, readTop + 11, muted, fonts.small);
    return recorder.finishRecordingAsPicture();
  });

  const touched = () => {
    'worklet';
    hold.set(USER_HOLD);
  };

  // One finger tilts: a second would move the pan's centroid and jerk the tray.
  const pan = Gesture.Pan()
    .minDistance(4)
    .maxPointers(1)
    .onStart(() => {
      touched();
      dragging.set(true);
      reversals.set(0);
    })
    .onUpdate((event) => {
      const limit = reducedMotion ? MAX_TILT * 0.45 : MAX_TILT;
      // Pressing the tray down where the finger goes: that side dips away and the marbles follow.
      wantTiltY.set(limit * Math.tanh(event.translationX / DRAG_SCALE));
      wantTiltX.set(-limit * Math.tanh(event.translationY / DRAG_SCALE));
      const slide = reducedMotion ? 0 : SLIDE_MAX;
      wantSlideX.set(slide * Math.tanh((event.translationX * SLIDE_SHARE) / SLIDE_MAX));
      wantSlideY.set(slide * Math.tanh((event.translationY * SLIDE_SHARE) / SLIDE_MAX));
      // A shake: strong strokes that keep reversing.
      const speed = Math.hypot(event.velocityX, event.velocityY);
      if (speed < SHAKE_SPEED) return;
      const dx = event.velocityX / speed;
      const dy = event.velocityY / speed;
      const t = now.get();
      if (dx * lastDirX.get() + dy * lastDirY.get() < -0.5 && t - lastStrongAt.get() < SHAKE_WINDOW) {
        reversals.set(reversals.get() + 1);
        if (reversals.get() >= SHAKE_REVERSALS && t - lastShakeAt.get() > SHAKE_COOLDOWN) {
          reversals.set(0);
          lastShakeAt.set(t);
          shakeNow.set(true);
          scheduleOnRN(shaken);
        }
      } else if (t - lastStrongAt.get() >= SHAKE_WINDOW) {
        reversals.set(0);
      }
      lastDirX.set(dx);
      lastDirY.set(dy);
      lastStrongAt.set(t);
    })
    .onFinalize(() => {
      dragging.set(false);
      hold.set(USER_HOLD);
    });

  const tap = Gesture.Tap()
    .maxDuration(500)
    .onEnd((event, success) => {
      if (!success) return;
      const s = scene.get();
      if (!s) return;
      let best = -1;
      let bestD = Infinity;
      for (let i = 0; i < MARBLES.length; i += 1) {
        const d = Math.hypot(event.x - s.sx[i], event.y - s.sy[i]);
        if (d < s.sr[i] + TAP_REACH && d < bestD) {
          bestD = d;
          best = i;
        }
      }
      if (best < 0) return;
      touched();
      // Away from the finger; a tap dead centre sends it up the tray.
      let dx = s.sx[best] - event.x;
      let dy = s.sy[best] - event.y;
      if (Math.hypot(dx, dy) < s.sr[best] * 0.3) {
        dx = 0;
        dy = -1;
      }
      flickDX.set(dx);
      flickDY.set(dy);
      flickWho.set(best);
      scheduleOnRN(flicked);
    });

  return (
    <GestureDetector gesture={Gesture.Race(pan, tap)}>
      <View
        style={styles.screen}
        accessible
        accessibilityRole="adjustable"
        accessibilityLabel={`${title}. Drag to tilt the tray, shake it to scatter the marbles, tap a marble to flick it.`}
        onLayout={(event: LayoutChangeEvent) => {
          const { width: w, height: h } = event.nativeEvent.layout;
          setSize({ width: w, height: h });
        }}
      >
        {size && (
          <Canvas style={StyleSheet.absoluteFill}>
            <Picture picture={picture} />
          </Canvas>
        )}
        <Text pointerEvents="none" style={[styles.label, { top: insets.top + 23 }]}>
          {label.toUpperCase()}
        </Text>
        <View pointerEvents="none" style={[styles.caption, { bottom: insets.bottom + 52 }]}>
          <Text style={styles.title}>{title}</Text>
          <Text style={styles.subtitle}>{subtitle}</Text>
        </View>
        <Text pointerEvents="none" style={[styles.hint, { bottom: insets.bottom + 18 }]}>
          Drag to tilt · Shake to scatter · Tap a marble
        </Text>
      </View>
    </GestureDetector>
  );
}

const styles = StyleSheet.create({
  screen: {
    flex: 1,
    backgroundColor: TABLE[2],
    overflow: 'hidden',
  },
  label: {
    position: 'absolute',
    left: 24,
    fontFamily: SANS,
    fontSize: 10.5,
    fontWeight: '600',
    letterSpacing: 2.4,
    color: MUTED,
  },
  caption: {
    position: 'absolute',
    left: 24,
    right: 24,
    gap: 2,
  },
  title: {
    fontFamily: SERIF,
    fontSize: 30,
    fontWeight: '600',
    color: INK,
  },
  subtitle: {
    fontFamily: SANS,
    fontSize: 13.5,
    fontWeight: '500',
    color: MUTED,
  },
  hint: {
    position: 'absolute',
    left: 20,
    right: 20,
    textAlign: 'center',
    fontFamily: SANS,
    fontSize: 12,
    fontWeight: '500',
    color: MUTED,
    opacity: 0.75,
  },
});
