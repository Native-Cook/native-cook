import { BlurStyle, Canvas, PaintStyle, Picture, type SkCanvas, type SkFont, Skia, StrokeCap, StrokeJoin, TileMode, matchFont } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useMemo, useState } from 'react';
import { type LayoutChangeEvent, Platform, StyleSheet, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import { useDerivedValue, useFrameCallback, useReducedMotion, useSharedValue } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

import {
  LOOP,
  MODES,
  RINGS,
  SOURCES,
  type Scene,
  type View as OrbitView,
  auto,
  beat,
  easeInOut,
  idleAngle,
  manualScene,
  mix,
  newManual,
  polar,
  posmod,
  project,
  readout,
  ringNear,
  unpolar,
  unproject,
} from './orbit-model';

/*
 Spatial Orbit Mixer — five instruments as glowing orbs on rings round a listener's head. A port
 of the SwiftUI block (SpatialOrbitMixer.swift); the numbers are the same.

 Look: deep indigo-black (#07070F) with a vignette and still star dust. The floor is seen from
 40° above (rings 72, 112 and 152 pt, white 18 % hairlines; ticks every 10° and 0 · 90 · 180 ·
 270 on the outer one; two dashed arches over the head), scaled to the screen's width. In the
 middle a listener seen from behind (a grey head on shoulders, dark over-ear headphones with
 lime accents) on a soft shadow and a lime under-glow. Five sources orbit: Vocals (lime
 #B6F23A, inner ring), Keys (violet #A98BFF) and Guitar (amber #FFC247) on the middle ring,
 Drums (coral #FF6A5C) and Bass (cyan #35D6FF) outside. Each orb is a lit sphere with a halo, a
 white pictogram, a ring that pulses out on its beat, a comet tail of 20 dots (where it was over
 the last 0.8 s), a stem down to a dot on the floor and a faint wedge of light towards the head.
 Above: "Spatial Mix" and "Head tracking on"; below: a segmented control (Stereo, Spatial,
 Fixed), five level bars, a play button and the track.

 Motion (orbit-model.ts): every source turns a whole number of times per loop (Keys twice, Vocals
 and Guitar once, Bass once the other way; Drums sway ±12°), bobs ±6 pt and pulses (radius
 +25 %, a ring out to 2.6×) on its own beat; the orbs are drawn back to front around the head.

 autoPlay timeline (LOOP = 10 s):
   0.0 – 1.2   idle orbits.
   1.0 – 3.6   a finger (a soft white disc) takes the Drums (it grows 30 %, its wedge lights, a
               readout shows level and direction), drags it from the outer ring to the inner ring
               on the left (1.3 – 2.9, ease in-out), holds, and lets go at 3.3.
   4.0 – 5.6   the view turns 25° and back, as if the phone turned.
   6.0 – 6.6   STEREO: every orb slides onto a line in front of the head (ease-out-back).
   7.4 – 8.0   SPATIAL again: they spring back out to their orbits (the Drums to its own ring).
   8.0 – 10.0  idle; the orbits have made whole turns, so 10.0 is the frame at 0.0.

 Touch: drag an orb across the floor (it grows, its wedge lights and a readout follows it, a
 selection haptic every 15° round the head); dropped within 12 pt of a ring it settles onto it
 (0.25 s) and orbits from there. Drag the empty floor to turn the view (0.35° per point) and
 tilt it (24° – 56°); Fixed stops the turning. Tap Stereo, Spatial or Fixed. 2 s after the last
 touch everything eases back over 1 s and the loop starts again.
 Reduced motion: no loop, orbits, pulses or tails; the sources rest on their rings. Dragging and
 the segments still work.
 */

const SANS = Platform.select({ ios: 'Helvetica Neue', default: 'sans-serif' });
const MONO = Platform.select({ ios: 'Menlo', default: 'monospace' });

type Fonts = { title: SkFont; medium11: SkFont; semi10: SkFont; mono8: SkFont; mono11: SkFont; medium13: SkFont; semi13: SkFont; regular11: SkFont };

function selection() {
  Haptics.selectionAsync().catch(() => {});
}

function rgb(hex: number, a = 1) {
  'worklet';
  return Float32Array.of(((hex >> 16) & 0xff) / 255, ((hex >> 8) & 0xff) / 255, (hex & 0xff) / 255, Math.min(Math.max(a, 0), 1));
}
function fill(c: Float32Array) {
  'worklet';
  const p = Skia.Paint();
  p.setAntiAlias(true);
  p.setColor(c);
  return p;
}
function stroke(c: Float32Array, w: number, round = false) {
  'worklet';
  const p = fill(c);
  p.setStyle(PaintStyle.Stroke);
  p.setStrokeWidth(w);
  if (round) {
    p.setStrokeCap(StrokeCap.Round);
    p.setStrokeJoin(StrokeJoin.Round);
  }
  return p;
}
/** A string's advance. SkFont.getTextWidth rounds every glyph's advance to whole points, which puts centred and right-aligned text off by several points; the glyph widths are exact. */
function textWidth(font: SkFont, s: string) {
  'worklet';
  const ws = font.getGlyphWidths(font.getGlyphIDs(s));
  let w = 0;
  for (let i = 0; i < ws.length; i++) w += ws[i];
  return w;
}

/** Text centred vertically on y; hx 0/0.5/1 for left, centre, right; tracking adds space after each letter. */
function text(c: SkCanvas, s: string, x: number, y: number, font: SkFont, color: Float32Array, hx = 0.5, tracking = 0) {
  'worklet';
  const m = font.getMetrics();
  const widths = tracking > 0 ? s.split('').map((ch) => textWidth(font, ch)) : [];
  const w = tracking > 0 ? widths.reduce((a, b) => a + b, 0) + tracking * (s.length - 1) : textWidth(font, s);
  const baseline = y - (m.ascent + m.descent) / 2;
  const paint = fill(color);
  let left = x - w * hx;
  if (tracking > 0) {
    for (let i = 0; i < s.length; i++) {
      c.drawText(s[i], left, baseline, paint, font);
      left += widths[i] + tracking;
    }
  } else {
    c.drawText(s, left, baseline, paint, font);
  }
  return w;
}

export function SpatialOrbitMixer({ autoPlay = false }: { autoPlay?: boolean }) {
  const reduced = useReducedMotion();
  const insets = useSafeAreaInsets();
  const [size, setSize] = useState({ width: 0, height: 0 });
  const fonts = useMemo<Fonts>(() => {
    const sans = (fontSize: number, fontWeight: '400' | '500' | '600') => matchFont({ fontFamily: SANS, fontSize, fontWeight });
    return {
      title: sans(22, '600'),
      medium11: sans(11, '500'),
      semi10: sans(10, '600'),
      // Menlo has only Regular and Bold, and 500 picks Bold; Regular is closer to SF Mono Medium.
      mono8: matchFont({ fontFamily: MONO, fontSize: 8, fontWeight: '400' }),
      mono11: matchFont({ fontFamily: MONO, fontSize: 11, fontWeight: '400' }),
      medium13: sans(13, '500'),
      semi13: sans(13, '600'),
      regular11: sans(11, '400'),
    };
  }, []);

  const w = size.width;
  const h = size.height;
  const top = insets.top;
  const rowY = h - Math.max(insets.bottom, 16) - 30;
  const segY = rowY - 70;
  const sceneTop = top + 76;
  const cx = w / 2;
  const cy = (sceneTop + segY - 30) / 2 + 16;
  const scale = Math.min(1.2, (w - 30) / 344);

  // The block's clock: seconds since it appeared (-1 before the first frame).
  const clk = useSharedValue(-1);
  const origin = useSharedValue(-1);
  /** On the block's clock: when the loop starts (-1: no loop), and when the ease back to it began (-1: none). */
  const autoStart = useSharedValue(autoPlay && !reduced ? 0 : -1);
  const returnStart = useSharedValue(-1);
  const manual = useSharedValue(newManual());
  const resumeAt = useSharedValue(-1);
  /** The current drag: [orb (−1 the camera), dx, dz, yaw, elevation, last 15° tick], or []. */
  const grab = useSharedValue<number[]>([]);

  useFrameCallback((info) => {
    const now = info.timestamp / 1000;
    if (origin.get() < 0) origin.set(now);
    const t = now - origin.get();
    clk.set(t);
    const r = resumeAt.get();
    if (r >= 0 && t >= r) {
      resumeAt.set(-1);
      returnStart.set(t);
      autoStart.set(t + 1);
    }
  });

  const viewOf = (s: Scene): OrbitView => {
    'worklet';
    return { cx, cy, scale, yaw: s.yaw, elevation: s.elevation };
  };

  /** The scene at a clock time, and the clock its beats and bobs follow. */
  const sceneAt = (t: number): [Scene, number] => {
    'worklet';
    if (reduced) return [manualScene(manual.get(), t), 0];
    const a = autoStart.get();
    if (a >= 0) {
      const lt = posmod(t - a, LOOP);
      if (t >= a) return [auto(lt), lt];
      const rs = returnStart.get();
      if (rs >= 0) return [mix(manualScene(manual.get(), t), auto(lt), easeInOut((t - rs) / (a - rs))), lt];
    }
    return [manualScene(manual.get(), t), t - manual.get().anchor];
  };

  /** Freezes whatever plays into the touch scene. */
  const takeOver = (t: number) => {
    'worklet';
    resumeAt.set(-1);
    const a = autoStart.get();
    if (a < 0) return;
    const [sc] = sceneAt(t);
    const m = newManual();
    m.anchor = a;
    for (let i = 0; i < 5; i++) {
      const o = sc.orbs[i];
      m.radius[i] = o.r;
      m.snapFrom[i] = o.r;
      m.offset[i] = o.a - idleAngle(i, t - a);
    }
    m.yaw = sc.yaw;
    m.elevation = sc.elevation;
    m.mode = sc.segment < 0.5 ? 0 : 1;
    m.stereoFrom = sc.stereo;
    m.stereoTo = m.mode === 0 ? 1 : 0;
    m.segmentFrom = sc.segment;
    m.segmentTo = m.mode === 0 ? 0 : 1;
    m.modeAt = t;
    if (sc.held >= 0) {
      // The loop's finger lets go.
      const i = sc.held;
      m.radius[i] = ringNear(m.radius[i]);
      m.snapAt[i] = t;
      m.dropped = i;
      m.droppedAt = t;
    }
    manual.set(m);
    autoStart.set(-1);
    returnStart.set(-1);
  };

  const scheduleResume = () => {
    'worklet';
    if (autoPlay && !reduced) resumeAt.set(clk.get() + 2);
  };

  const drag = useMemo(
    () =>
      Gesture.Pan()
        .minDistance(0)
        .maxPointers(1)
        .onBegin((e) => {
          const t = Math.max(clk.get(), 0);
          takeOver(t);
          const [sc] = sceneAt(t);
          const v = viewOf(sc);
          const x = e.x;
          const y = e.y + sceneTop;
          let best = -1;
          let bestD = 34;
          for (let i = 0; i < 5; i++) {
            const o = sc.orbs[i];
            const p = project(v, o.x, o.z, o.h);
            const d = Math.hypot(p[0] - x, p[1] - y);
            if (d < bestD) {
              best = i;
              bestD = d;
            }
          }
          if (best >= 0) {
            const f = unproject(v, x, y);
            const o = sc.orbs[best];
            grab.set([best, o.x - f[0], o.z - f[1], sc.yaw, sc.elevation, Math.floor(unpolar(o.x, o.z)[1] / 15)]);
            manual.modify((m) => {
              'worklet';
              m.held = best;
              m.heldX = o.x;
              m.heldZ = o.z;
              m.heldAt = t;
              return m;
            });
            scheduleOnRN(selection);
          } else {
            grab.set([-1, 0, 0, sc.yaw, sc.elevation, 0]);
          }
        })
        .onUpdate((e) => {
          const g = grab.get();
          if (g.length === 0) return;
          if (g[0] >= 0) {
            const [sc] = sceneAt(Math.max(clk.get(), 0));
            const f = unproject(viewOf(sc), e.x, e.y + sceneTop);
            let x = f[0] + g[1];
            let z = f[1] + g[2];
            const r = Math.hypot(x, z);
            if (r > 175) {
              x *= 175 / r;
              z *= 175 / r;
            }
            manual.modify((m) => {
              'worklet';
              m.heldX = x;
              m.heldZ = z;
              return m;
            });
            const tick = Math.floor(unpolar(x, z)[1] / 15);
            if (tick !== g[5]) {
              grab.set([g[0], g[1], g[2], g[3], g[4], tick]);
              scheduleOnRN(selection);
            }
          } else {
            manual.modify((m) => {
              'worklet';
              if (m.mode !== 2) {
                m.yaw = g[3] + e.translationX * 0.35;
                m.yawAt = -100;
              }
              m.elevation = Math.min(Math.max(g[4] - e.translationY * 0.12, 24), 56);
              return m;
            });
          }
        })
        .onFinalize(() => {
          const g = grab.get();
          const t = Math.max(clk.get(), 0);
          if (g.length > 0 && g[0] >= 0) {
            const i = g[0];
            manual.modify((m) => {
              'worklet';
              const [r, a] = unpolar(m.heldX, m.heldZ);
              m.radius[i] = ringNear(r);
              m.snapFrom[i] = r;
              m.snapAt[i] = t;
              m.offset[i] = a - idleAngle(i, t - m.anchor);
              m.held = -1;
              m.dropped = i;
              m.droppedAt = t;
              return m;
            });
          }
          grab.set([]);
          scheduleResume();
        }),
    // The shared values are stable; the gesture only depends on these.
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [reduced, autoPlay, cx, cy, scale, sceneTop],
  );

  const segmentTap = useMemo(
    () =>
      Gesture.Tap().onEnd((e) => {
        const mode = Math.min(Math.max(Math.floor(e.x / ((w - 80) / 3)), 0), 2);
        const t = Math.max(clk.get(), 0);
        takeOver(t);
        const [sc] = sceneAt(t);
        scheduleOnRN(selection);
        manual.modify((m) => {
          'worklet';
          m.mode = mode;
          m.stereoFrom = sc.stereo;
          m.stereoTo = mode === 0 ? 1 : 0;
          m.segmentFrom = sc.segment;
          m.segmentTo = mode;
          m.modeAt = t;
          if (mode === 2) {
            m.yawFrom = sc.yaw;
            m.yaw = 0;
            m.yawAt = t;
          }
          return m;
        });
        scheduleResume();
      }),
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [reduced, autoPlay, w, cx, cy, scale],
  );

  const picture = useDerivedValue(() => {
    const recorder = Skia.PictureRecorder();
    const c = recorder.beginRecording(Skia.XYWHRect(0, 0, w, h));
    if (w <= 0) return recorder.finishRecordingAsPicture();
    const t = Math.max(clk.get(), 0);
    const [sc, clock] = sceneAt(t);
    const v = viewOf(sc);
    c.drawRect(Skia.XYWHRect(0, 0, w, h), fill(rgb(0x07070f)));
    const glow = Skia.Paint();
    glow.setShader(Skia.Shader.MakeRadialGradient({ x: cx, y: cy }, w * 0.8, [rgb(0x1a1a3a, 0.55), rgb(0x1a1a3a, 0)], null, TileMode.Clamp));
    c.drawRect(Skia.XYWHRect(0, 0, w, h), glow);
    // Star dust.
    for (let i = 0; i < 70; i++) {
      const fx = (i * 0.618034 + 0.11) % 1;
      const fy = (i * 0.754877 + 0.37) % 1;
      c.drawCircle(fx * w, fy * h, i % 7 === 0 ? 1.1 : 0.6, fill(Float32Array.of(1, 1, 1, 0.12 + (0.3 * (i % 5)) / 4)));
    }
    // Vignette: clear inside 0.3 h of the centre, 60 % black at 0.75 h.
    const vig = Skia.Paint();
    vig.setShader(Skia.Shader.MakeRadialGradient({ x: w / 2, y: h / 2 }, h * 0.75, [rgb(0, 0), rgb(0, 0), rgb(0, 0.6)], [0, 0.4, 1], TileMode.Clamp));
    c.drawRect(Skia.XYWHRect(0, 0, w, h), vig);

    // Header.
    text(c, 'Spatial Mix', 24, top + 26, fonts.title, rgb(0xffffff), 0);
    c.drawCircle(27, top + 51, 3, fill(sc.tracking ? rgb(0xb6f23a) : rgb(0x5a5a70)));
    text(c, sc.tracking ? 'Head tracking on' : 'Head tracking off', 36, top + 51, fonts.medium11, rgb(0x8a8aa0), 0);
    c.drawRRect(Skia.RRectXY(Skia.XYWHRect(w - 24 - 72 + 0.5, top + 14.5, 71, 25), 13, 13), stroke(Float32Array.of(1, 1, 1, 0.18), 1));
    text(c, '5 STEMS', w - 24 - 36, top + 27, fonts.semi10, Float32Array.of(1, 1, 1, 0.7), 0.5, 1.2);

    // The floor: a soft glow, three rings, two arches, ticks and bearings on the outer one.
    const fc = project(v, 0, 0, 0);
    const e = (sc.elevation * Math.PI) / 180;
    const gr = 170 * scale;
    const floor = Skia.Paint();
    floor.setShader(Skia.Shader.MakeRadialGradient({ x: fc[0], y: fc[1] }, gr, [rgb(0x6c6cff, 0.08), rgb(0x6c6cff, 0)], null, TileMode.Clamp));
    c.drawOval(Skia.XYWHRect(fc[0] - gr, fc[1] - gr * Math.sin(e), 2 * gr, 2 * gr * Math.sin(e)), floor);
    for (const r of RINGS) {
      const ring = Skia.Path.Make();
      for (let k = 0; k <= 72; k++) {
        const [x, z] = polar(r, k * 5);
        const p = project(v, x, z, 0);
        if (k === 0) ring.moveTo(p[0], p[1]);
        else ring.lineTo(p[0], p[1]);
      }
      c.drawPath(ring, stroke(Float32Array.of(1, 1, 1, 0.18), 1));
    }
    for (let side = 0; side < 2; side++) {
      const arch = Skia.Path.Make();
      for (let k = 0; k <= 48; k++) {
        const phi = (k / 48) * Math.PI;
        const p = side === 0 ? project(v, 0, 152 * Math.cos(phi), 152 * Math.sin(phi)) : project(v, 152 * Math.cos(phi), 0, 152 * Math.sin(phi));
        if (k === 0) arch.moveTo(p[0], p[1]);
        else arch.lineTo(p[0], p[1]);
      }
      const dashed = stroke(Float32Array.of(1, 1, 1, 0.16), 1);
      dashed.setPathEffect(Skia.PathEffect.MakeDash([2, 5], 0));
      c.drawPath(arch, dashed);
    }
    const ticks = Skia.Path.Make();
    for (let k = 0; k < 36; k++) {
      const [x0, z0] = polar(152, k * 10);
      const [x1, z1] = polar(k % 3 === 0 ? 159 : 156, k * 10);
      const p0 = project(v, x0, z0, 0);
      const p1 = project(v, x1, z1, 0);
      ticks.moveTo(p0[0], p0[1]);
      ticks.lineTo(p1[0], p1[1]);
    }
    c.drawPath(ticks, stroke(Float32Array.of(1, 1, 1, 0.22), 0.8));
    for (const a of [0, 90, 180, 270]) {
      const [x, z] = polar(172, a);
      const p = project(v, x, z, 0);
      text(c, String(a), p[0], p[1], fonts.mono8, Float32Array.of(1, 1, 1, 0.4));
    }

    // Stems, floor dots and the wedges of light towards the head.
    const head = project(v, 0, 0, 40);
    const orbSize = (grow: number, depth: number, pulse: number) => 15 * scale * grow * (1 + (0.15 * depth) / 152) * (1 + 0.25 * pulse);
    for (let i = 0; i < 5; i++) {
      const o = sc.orbs[i];
      const p = project(v, o.x, o.z, o.h);
      const g = project(v, o.x, o.z, 0);
      c.drawLine(p[0], p[1], g[0], g[1], stroke(Float32Array.of(1, 1, 1, 0.15), 0.7));
      c.drawOval(Skia.XYWHRect(g[0] - 2, g[1] - 1.2, 4, 2.4), fill(rgb(SOURCES[i].color, 0.6)));
      const s = orbSize(o.grow, p[2], 0);
      const dx = p[0] - head[0];
      const dy = p[1] - head[1];
      const len = Math.max(Math.hypot(dx, dy), 1);
      const nx = (-dy / len) * s * 1.1;
      const ny = (dx / len) * s * 1.1;
      const wedge = Skia.Path.Make();
      wedge.moveTo(head[0], head[1]);
      wedge.lineTo(p[0] + nx, p[1] + ny);
      wedge.lineTo(p[0] - nx, p[1] - ny);
      wedge.close();
      c.drawPath(wedge, fill(rgb(o.lit > 0.01 ? SOURCES[i].color : 0xffffff, 0.035 + 0.12 * o.lit)));
    }

    // Tails: where each orb was over the last 0.8 s.
    if (!reduced) {
      for (let k = 20; k >= 1; k--) {
        const [past] = sceneAt(t - k * 0.04);
        const pv = viewOf(past);
        for (let i = 0; i < 5; i++) {
          if (past.held === i && sc.held === i) continue;
          const o = past.orbs[i];
          const p = project(pv, o.x, o.z, o.h);
          const f = 1 - k / 21;
          c.drawCircle(p[0], p[1], 3.2 * scale * f, fill(rgb(SOURCES[i].color, 0.45 * f)));
        }
      }
    }

    const drawOrb = (i: number) => {
      const o = sc.orbs[i];
      const p = project(v, o.x, o.z, o.h);
      const [pulse, phase] = reduced ? [0, 0.5] : beat(i, clock);
      const s = orbSize(o.grow, p[2], pulse);
      const color = SOURCES[i].color;
      const halo = fill(rgb(color, 0.3 + 0.25 * o.lit));
      halo.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 8, true));
      c.drawCircle(p[0], p[1], 2.2 * s, halo);
      if (!reduced) c.drawCircle(p[0], p[1], s * (1 + 1.6 * phase), stroke(rgb(color, 0.5 * (1 - phase)), 1.5));
      const body = Skia.Paint();
      body.setAntiAlias(true);
      body.setShader(
        Skia.Shader.MakeRadialGradient({ x: p[0] - 0.3 * s, y: p[1] - 0.35 * s }, 1.3 * s, [Float32Array.of(1, 1, 1, 0.95), rgb(color), rgb(color, 0.85)], [0, 0.45, 1], TileMode.Clamp),
      );
      c.drawCircle(p[0], p[1], s, body);
      // The pictogram.
      const k = s / 15;
      const x = p[0];
      const y = p[1];
      const gl = Skia.Path.Make();
      if (i === 0) {
        gl.addRRect(Skia.RRectXY(Skia.XYWHRect(x - 2.5 * k, y - 6 * k, 5 * k, 8 * k), 2.5 * k, 2.5 * k));
        gl.moveTo(x, y + 2 * k);
        gl.lineTo(x, y + 6 * k);
      } else if (i === 1) {
        for (const d of [-3.5, 0, 3.5]) gl.addRRect(Skia.RRectXY(Skia.XYWHRect(x + d * k - 1.2 * k, y - 5 * k, 2.4 * k, 10 * k), 0.8 * k, 0.8 * k));
      } else if (i === 2) {
        gl.moveTo(x - 4.5 * k, y - 4 * k);
        gl.quadTo(x, y - 7 * k, x + 4.5 * k, y - 4 * k);
        gl.quadTo(x + 3 * k, y + 2 * k, x, y + 6 * k);
        gl.quadTo(x - 3 * k, y + 2 * k, x - 4.5 * k, y - 4 * k);
      } else if (i === 3) {
        gl.addOval(Skia.XYWHRect(x - 4.5 * k, y - 2 * k, 9 * k, 7 * k));
        gl.moveTo(x - 5 * k, y - 6 * k);
        gl.lineTo(x - 1 * k, y - 1 * k);
        gl.moveTo(x + 5 * k, y - 6 * k);
        gl.lineTo(x + 1 * k, y - 1 * k);
      } else {
        for (const [d, hh] of [
          [-4, 3],
          [-1.3, 6],
          [1.3, 4.5],
          [4, 2],
        ]) {
          gl.moveTo(x + d * k, y - hh * k);
          gl.lineTo(x + d * k, y + hh * k);
        }
      }
      c.drawPath(gl, stroke(Float32Array.of(1, 1, 1, 0.95), 1.3 * k, true));
    };

    // The listener: a grey head on shoulders with over-ear headphones, the far cup behind it and the near one in front.
    const listener = () => {
      const k = scale;
      const shadow = fill(Float32Array.of(0, 0, 0, 0.7));
      shadow.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 6, true));
      c.drawOval(Skia.XYWHRect(fc[0] - 30 * k, fc[1] - 9 * k, 60 * k, 18 * k), shadow);
      const under = fill(rgb(0xb6f23a, 0.12));
      under.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 6, true));
      c.drawOval(Skia.XYWHRect(fc[0] - 40 * k, fc[1] - 12 * k, 80 * k, 24 * k), under);
      const hx = head[0];
      const hy = head[1];
      const cups = [project(v, -22, 0, 38), project(v, 22, 0, 38)];
      const cup = (p: number[]) => {
        const cp = Skia.Paint();
        cp.setAntiAlias(true);
        cp.setShader(Skia.Shader.MakeLinearGradient({ x: p[0] - 7 * k, y: p[1] - 12 * k }, { x: p[0] + 7 * k, y: p[1] + 12 * k }, [rgb(0x34343f), rgb(0x18181f)], null, TileMode.Clamp));
        c.drawRRect(Skia.RRectXY(Skia.XYWHRect(p[0] - 7 * k, p[1] - 12 * k, 14 * k, 24 * k), 7 * k, 7 * k), cp);
        c.drawLine(p[0], p[1] - 7 * k, p[0], p[1] + 7 * k, stroke(rgb(0xb6f23a, 0.9), 1.5 * k, true));
      };
      // Shoulders under the head.
      const bust = Skia.Path.Make();
      bust.moveTo(hx - 38 * k, hy + 38 * k);
      bust.quadTo(hx - 37 * k, hy + 15 * k, hx - 13 * k, hy + 13 * k);
      bust.lineTo(hx + 13 * k, hy + 13 * k);
      bust.quadTo(hx + 37 * k, hy + 15 * k, hx + 38 * k, hy + 38 * k);
      bust.quadTo(hx, hy + 44 * k, hx - 38 * k, hy + 38 * k);
      bust.close();
      const bp = Skia.Paint();
      bp.setAntiAlias(true);
      bp.setShader(Skia.Shader.MakeLinearGradient({ x: hx, y: hy + 12 * k }, { x: hx, y: hy + 40 * k }, [rgb(0x5c5c6c), rgb(0x26262f)], null, TileMode.Clamp));
      c.drawPath(bust, bp);
      const far = cups[0][2] < cups[1][2] ? 0 : 1;
      cup(cups[far]);
      const egg = Skia.Paint();
      egg.setAntiAlias(true);
      egg.setShader(Skia.Shader.MakeRadialGradient({ x: hx - 7 * k, y: hy - 12 * k }, 34 * k, [rgb(0xf1f1f5), rgb(0xb9b9c6), rgb(0x6e6e7e)], [0, 0.5, 1], TileMode.Clamp));
      c.drawOval(Skia.XYWHRect(hx - 20 * k, hy - 26 * k, 40 * k, 48 * k), egg);
      // The headband over the top, from cup to cup.
      const band = Skia.Path.Make();
      band.moveTo(cups[0][0], cups[0][1] - 10 * k);
      band.quadTo(hx, hy - 50 * k, cups[1][0], cups[1][1] - 10 * k);
      c.drawPath(band, stroke(rgb(0x23232b), 5 * k, true));
      c.drawPath(band, stroke(rgb(0xb6f23a, 0.5), 1 * k, true));
      cup(cups[1 - far]);
    };

    // Orbs behind the head, the head, then the orbs in front.
    const order = [0, 1, 2, 3, 4].map((i) => [i, project(v, sc.orbs[i].x, sc.orbs[i].z, sc.orbs[i].h)[2]]).sort((a, b) => a[1] - b[1]);
    for (const [i, d] of order) if (d < 0) drawOrb(i);
    listener();
    for (const [i, d] of order) if (d >= 0) drawOrb(i);

    // The readout beside a held orb, and the loop's finger.
    if (sc.held >= 0 && sc.chip > 0.01) {
      const o = sc.orbs[sc.held];
      const p = project(v, o.x, o.z, o.h);
      const label = readout(o.x, o.z);
      const tw = textWidth(fonts.mono11, label);
      const bx = p[0] - (tw + 20) / 2;
      const by = p[1] - 52 * scale - 26;
      c.drawRRect(Skia.RRectXY(Skia.XYWHRect(bx, by, tw + 20, 26), 13, 13), fill(rgb(0x15152a, 0.92 * sc.chip)));
      c.drawRRect(Skia.RRectXY(Skia.XYWHRect(bx + 0.5, by + 0.5, tw + 19, 25), 12.5, 12.5), stroke(rgb(SOURCES[sc.held].color, 0.6 * sc.chip), 1));
      text(c, label, bx + (tw + 20) / 2, by + 13, fonts.mono11, Float32Array.of(1, 1, 1, sc.chip));
    }
    if (sc.finger > 0.01) {
      const o = sc.orbs[3];
      const p = project(v, o.x, o.z, o.h);
      c.drawCircle(p[0] + 6, p[1] + 10, 22, fill(Float32Array.of(1, 1, 1, 0.16 * sc.finger)));
      c.drawCircle(p[0] + 6, p[1] + 10, 21.25, stroke(Float32Array.of(1, 1, 1, 0.5 * sc.finger), 1.5));
    }

    // The segmented control.
    const segW = w - 80;
    c.drawRRect(Skia.RRectXY(Skia.XYWHRect(40, segY - 20, segW, 40), 20, 20), fill(Float32Array.of(1, 1, 1, 0.07)));
    const ix = 40 + 3 + ((segW - 6) / 3) * Math.min(Math.max(sc.segment, 0), 2);
    c.drawRRect(Skia.RRectXY(Skia.XYWHRect(ix, segY - 17, (segW - 6) / 3, 34), 17, 17), fill(Float32Array.of(1, 1, 1, 0.14)));
    MODES.forEach((name, i) => {
      const on = Math.max(0, 1 - Math.abs(sc.segment - i));
      text(c, name, 40 + (segW / 6) * (2 * i + 1), segY, fonts.medium13, Float32Array.of(1, 1, 1, 0.5 + 0.5 * on));
    });

    // Levels, play, and the track.
    for (let i = 0; i < 5; i++) {
      const lv = reduced ? 0.5 : 0.3 + 0.7 * beat(i, clock)[0];
      const bh = 4 + 24 * lv;
      c.drawRRect(Skia.RRectXY(Skia.XYWHRect(24 + i * 10, rowY + 14 - bh, 5, bh), 2.5, 2.5), fill(rgb(SOURCES[i].color)));
    }
    c.drawCircle(w / 2, rowY, 26, fill(rgb(0xffffff)));
    c.drawRRect(Skia.RRectXY(Skia.XYWHRect(w / 2 - 7, rowY - 8, 5, 16), 1.5, 1.5), fill(rgb(0x07070f)));
    c.drawRRect(Skia.RRectXY(Skia.XYWHRect(w / 2 + 2, rowY - 8, 5, 16), 1.5, 1.5), fill(rgb(0x07070f)));
    text(c, 'Low Orbit', w - 24, rowY - 8, fonts.semi13, rgb(0xffffff), 1);
    text(c, 'Kaito Vale', w - 24, rowY + 9, fonts.regular11, rgb(0x8a8aa0), 1);
    return recorder.finishRecordingAsPicture();
  });

  const onLayout = (e: LayoutChangeEvent) => {
    const { width, height } = e.nativeEvent.layout;
    if (width !== size.width || height !== size.height) setSize({ width, height });
  };

  return (
    <View style={styles.screen} onLayout={onLayout}>
      <StatusBar style="light" />
      {w > 0 ? (
        <>
          <Canvas style={StyleSheet.absoluteFill} pointerEvents="none">
            <Picture picture={picture} />
          </Canvas>
          <GestureDetector gesture={drag}>
            <View
              style={[styles.hit, { left: 0, top: sceneTop, width: w, height: Math.max(0, segY - 30 - sceneTop) }]}
              accessible
              accessibilityLabel="Spatial mix: five sources round the listener"
            />
          </GestureDetector>
          <GestureDetector gesture={segmentTap}>
            <View style={[styles.hit, { left: 40, top: segY - 20, width: w - 80, height: 40 }]} accessible accessibilityRole="tablist" accessibilityLabel="Stereo, Spatial, Fixed" />
          </GestureDetector>
        </>
      ) : null}
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: '#07070F' },
  hit: { position: 'absolute' },
});
