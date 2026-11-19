import { Canvas, ClipOp, createPicture, PaintStyle, Picture, Skia, StrokeCap, TileMode, useFont } from "@shopify/react-native-skia";
import type { SkCanvas, SkFont, SkPaint } from "@shopify/react-native-skia";
import type { JSX } from "react";
import { StyleSheet } from "react-native";
import { useDerivedValue } from "react-native-reanimated";
import type { SharedValue } from "react-native-reanimated";

import {
  blendChannel,
  cameraAt,
  cameraBasis,
  clamp01,
  easeOutCubic,
  filmBoot,
  filmHud,
  filmRadar,
  filmRise,
  filmVoice,
  heading,
  hudFacing,
  pageRise,
  pageWeight,
  project,
  radarAngle,
  rectToQuad,
  seg,
  voiceBar,
  voiceWaveY,
} from "./visorMotion";
import {
  HELMET_Y,
  HFOV_DEG,
  HUD_H,
  HUD_QUAD,
  HUD_W,
  MOUTH,
  MOUTH_AXIS_A,
  MOUTH_AXIS_B,
  PORT_AXIS_A,
  PORT_AXIS_B,
  PORT_CENTER,
  RADAR_R,
} from "./visorScene";
import type { Vec3 } from "./visorScene";

/** HUD amber (sRGB) as r, g, b. */
const AR = 255;
const AG = 194;
const AB = 122;
const READOUT_L = "38.4 m";
const READOUT_R = "640 lx";
const BARS = 30;
/** Radar blips: angle (rad) and radius share of RADAR_R. */
const BLIPS: readonly (readonly [number, number])[] = [
  [0.9, 0.62],
  [2.7, 0.38],
  [4.5, 0.78],
];

function amber(paint: SkPaint, a: number): void {
  "worklet";
  paint.setColor(Skia.Color(`rgba(${AR},${AG},${AB},${clamp01(a)})`));
}

/** Width of a run of text, from glyph widths (measureText rounds each glyph). */
function textWidth(font: SkFont, s: string): number {
  "worklet";
  const w = font.getGlyphWidths(font.getGlyphIDs(s));
  let sum = 0;
  for (let i = 0; i < w.length; i++) sum += w[i];
  return sum;
}

/** A point of the radar's plane: angle ang (rad), radius r, round the world-space centre cw. */
function radarPoint(cw: Vec3, ang: number, r: number): Vec3 {
  "worklet";
  const ca = Math.cos(ang) * r;
  const sa = Math.sin(ang) * r;
  return [
    cw[0] + ca * PORT_AXIS_A[0] + sa * PORT_AXIS_B[0],
    cw[1] + ca * PORT_AXIS_A[1] + sa * PORT_AXIS_B[1],
    cw[2] + ca * PORT_AXIS_A[2] + sa * PORT_AXIS_B[2],
  ];
}

/** A helmet-space point (plus the helmet's lift) to world space. */
function helmetPoint(p: readonly number[], lift: number): Vec3 {
  "worklet";
  return [p[0], p[1] + HELMET_Y + lift, p[2]];
}

/** The HUD in its own canonical units (HUD_W x HUD_H), drawn under the visor homography. */
function drawHud(canvas: SkCanvas, paint: SkPaint, vis: number, boot: number, run: number, f8: SkFont | null, f11: SkFont | null): void {
  "worklet";
  // the glass tint and a faint lens glow behind everything
  paint.setStyle(PaintStyle.Fill);
  amber(paint, 0.045 * vis);
  canvas.drawRect({ x: 0, y: 0, width: HUD_W, height: HUD_H }, paint);
  // scanlines: strong while it boots, then a whisper
  paint.setStyle(PaintStyle.Stroke);
  paint.setStrokeWidth(0.6);
  const scanA = (0.05 + 0.08 * (1 - seg(boot, 0.25, 0.45))) * vis;
  amber(paint, scanA);
  for (let y = 2; y < HUD_H; y += 3) canvas.drawLine(0, y, HUD_W, y, paint);
  // the boot sweep: a bright band running down the glass
  const sweep = seg(boot, 0, 0.3);
  if (sweep > 0 && sweep < 1) {
    const y = -12 + sweep * (HUD_H + 24);
    paint.setStyle(PaintStyle.Fill);
    for (let k = 0; k < 6; k++) {
      amber(paint, (0.08 + 0.1 * k) * vis);
      canvas.drawRect({ x: 0, y: y - 12 + k * 2, width: HUD_W, height: 2 }, paint);
    }
    amber(paint, 0.9 * vis);
    canvas.drawRect({ x: 0, y: y, width: HUD_W, height: 1.2 }, paint);
  }
  // corner brackets drawing in
  const br = 14 * easeOutCubic(seg(boot, 0.1, 0.35));
  paint.setStyle(PaintStyle.Stroke);
  paint.setStrokeWidth(1.4);
  paint.setStrokeCap(StrokeCap.Square);
  amber(paint, 0.85 * vis);
  if (br > 0.5) {
    const i = 4;
    canvas.drawLine(i, i, i + br, i, paint);
    canvas.drawLine(i, i, i, i + br * 0.7, paint);
    canvas.drawLine(HUD_W - i, i, HUD_W - i - br, i, paint);
    canvas.drawLine(HUD_W - i, i, HUD_W - i, i + br * 0.7, paint);
    canvas.drawLine(i, HUD_H - i, i + br, HUD_H - i, paint);
    canvas.drawLine(i, HUD_H - i, i, HUD_H - i - br * 0.7, paint);
    canvas.drawLine(HUD_W - i, HUD_H - i, HUD_W - i - br, HUD_H - i, paint);
    canvas.drawLine(HUD_W - i, HUD_H - i, HUD_W - i, HUD_H - i - br * 0.7, paint);
  }
  // compass ribbon, opening from the centre
  const reveal = easeOutCubic(seg(boot, 0.3, 0.55));
  const cx = HUD_W / 2;
  if (reveal > 0.01) {
    const half = 96 * reveal;
    canvas.save();
    canvas.clipRect({ x: cx - half, y: 6, width: half * 2, height: 26 }, ClipOp.Intersect, true);
    const hd = heading(run);
    const ppd = 2.6; // canonical units per degree
    paint.setStrokeWidth(1);
    const first = Math.floor((hd - 42) / 5) * 5;
    for (let deg = first; deg <= hd + 42; deg += 5) {
      const x = cx + (deg - hd) * ppd;
      const major = ((deg % 15) + 15) % 15 === 0;
      const edge = 1 - Math.abs(x - cx) / 100;
      amber(paint, (major ? 0.9 : 0.5) * vis * clamp01(edge * 1.6));
      canvas.drawLine(x, 12, x, 12 + (major ? 6 : 3), paint);
      const d360 = ((deg % 360) + 360) % 360;
      if (f8 != null && d360 % 30 === 0) {
        const label = d360 === 0 ? "N" : d360 === 90 ? "E" : d360 === 180 ? "S" : d360 === 270 ? "W" : `${d360 < 100 ? "0" : ""}${d360}`;
        paint.setStyle(PaintStyle.Fill);
        canvas.drawText(label, x - textWidth(f8, label) / 2, 28, paint, f8);
        paint.setStyle(PaintStyle.Stroke);
      }
    }
    canvas.restore();
    // caret and heading readout under it
    paint.setStyle(PaintStyle.Fill);
    amber(paint, 0.95 * vis * reveal);
    const path = Skia.PathBuilder.Make();
    path.moveTo(cx, 10);
    path.lineTo(cx - 3.5, 5);
    path.lineTo(cx + 3.5, 5);
    path.close();
    canvas.drawPath(path.build(), paint);
  }
  // reticle
  const ret = easeOutCubic(seg(boot, 0.25, 0.5));
  if (ret > 0.01) {
    paint.setStyle(PaintStyle.Stroke);
    paint.setStrokeWidth(1);
    amber(paint, 0.7 * vis * ret);
    const ry = 60;
    const rr = 7 + 4 * (1 - ret);
    canvas.drawCircle(cx, ry, rr, paint);
    canvas.drawLine(cx - rr - 9, ry, cx - rr - 3, ry, paint);
    canvas.drawLine(cx + rr + 3, ry, cx + rr + 9, ry, paint);
    canvas.drawLine(cx, ry + rr + 3, cx, ry + rr + 7, paint);
    // horizon ticks either side
    amber(paint, 0.35 * vis * ret);
    canvas.drawLine(cx - 70, ry, cx - 36, ry, paint);
    canvas.drawLine(cx + 36, ry, cx + 70, ry, paint);
  }
  // two readouts typing in
  if (f8 != null && f11 != null) {
    paint.setStyle(PaintStyle.Fill);
    const kl = seg(boot, 0.55, 0.78);
    const kr = seg(boot, 0.72, 0.95);
    if (kl > 0) {
      const n = Math.ceil(kl * READOUT_L.length);
      amber(paint, 0.95 * vis);
      const s = READOUT_L.slice(0, n);
      canvas.drawText(s, 12, 100, paint, f11);
      if (kl < 1) canvas.drawRect({ x: 13 + textWidth(f11, s), y: 90, width: 6, height: 11 }, paint);
    }
    if (kr > 0) {
      const x1 = HUD_W - 12;
      const n = Math.ceil(kr * READOUT_R.length);
      const s = READOUT_R.slice(0, n);
      const full = textWidth(f11, READOUT_R);
      amber(paint, 0.95 * vis);
      canvas.drawText(s, x1 - full, 100, paint, f11);
      if (kr < 1) canvas.drawRect({ x: x1 - full + textWidth(f11, s) + 1, y: 90, width: 6, height: 11 }, paint);
    }
  }
}

export type VisorHudProps = {
  width: number;
  height: number;
  t: SharedValue<number>;
  page: SharedValue<number>;
  m: SharedValue<number>;
  yaw: SharedValue<number>;
  pitch: SharedValue<number>;
  /** Running time for periodic motion (film: t; under a finger it keeps running from where the film was). */
  run: SharedValue<number>;
  /** Nothing of the voice module goes below this y (the copy starts there). */
  limitY: number;
};

/**
 * The three 2D modules that live on the helmet, drawn over the 3D view and projected with the stage's own camera:
 * the visor HUD (a perspective-correct quad on the visor plate), the radar round the ear sensor, and the voice
 * module (rings at the mouthpiece and a waveform under the helmet; its transcript is VisorVoiceCaption).
 */
export function VisorHud(p: VisorHudProps): JSX.Element {
  const { width: w, height: h, limitY } = p;
  const f8 = useFont(require("./assets/fonts/IBMPlexMono-Medium.ttf"), 8.5);
  const f11 = useFont(require("./assets/fonts/IBMPlexMono-Medium.ttf"), 13);
  const label = useFont(require("./assets/fonts/IBMPlexMono-Medium.ttf"), 11);

  const picture = useDerivedValue(() => {
    const t = p.t.get();
    const pg = p.page.get();
    const m = p.m.get();
    const run = p.run.get();
    const c = [0, 0, 0, 0, 0, 0];
    cameraAt(t, pg, m, p.yaw.get(), p.pitch.get(), c);
    const b = [0, 0, 0, 0, 0, 0, 0, 0, 0];
    cameraBasis(c, w, HFOV_DEG, b);
    const lift = blendChannel(filmRise(t), pageRise(pg), m);

    const hudVis = blendChannel(filmHud(t), pageWeight(pg, 1), m) * hudFacing(c, HELMET_Y + lift);
    const boot = blendChannel(filmBoot(t), 1, m);
    const radarVis = blendChannel(filmRadar(t), pageWeight(pg, 2), m);
    const voiceVis = blendChannel(filmVoice(t), pageWeight(pg, 3), m);

    return createPicture((canvas) => {
      const paint = Skia.Paint();
      paint.setAntiAlias(true);
      const o = [0, 0, 0];

      // ---- HUD on the visor
      if (hudVis > 0.004) {
        const q = [0, 0, 0, 0, 0, 0, 0, 0];
        let ok = true;
        for (let k = 0; k < 4; k++) {
          if (!project(c, b, w, h, helmetPoint(HUD_QUAD[k], lift), o)) ok = false;
          q[k * 2] = o[0];
          q[k * 2 + 1] = o[1];
        }
        if (ok) {
          // a soft amber bloom on the visor plate under the glass
          const gx = (q[0] + q[2] + q[4] + q[6]) / 4;
          const gy = (q[1] + q[3] + q[5] + q[7]) / 4;
          const gr = Math.hypot(q[2] - q[0], q[3] - q[1]) * 0.7;
          paint.setShader(
            Skia.Shader.MakeRadialGradient(
              { x: gx, y: gy },
              Math.max(1, gr),
              [Skia.Color(`rgba(${AR},${AG},${AB},${0.16 * hudVis})`), Skia.Color(`rgba(${AR},${AG},${AB},0)`)],
              [0, 1],
              TileMode.Clamp,
            ),
          );
          canvas.drawCircle(gx, gy, Math.max(1, gr), paint);
          paint.setShader(null);
          const mtx = [0, 0, 0, 0, 0, 0, 0, 0, 1];
          rectToQuad(q, HUD_W, HUD_H, mtx);
          canvas.save();
          canvas.concat(Skia.Matrix(mtx));
          drawHud(canvas, paint, hudVis, boot, run, f8, f11);
          canvas.restore();
        }
      }

      // ---- radar round the ear sensor
      if (radarVis > 0.004) {
        const grow = 0.72 + 0.28 * easeOutCubic(radarVis);
        const cw = helmetPoint(PORT_CENTER, lift);
        paint.setStyle(PaintStyle.Stroke);
        paint.setStrokeWidth(1);
        // three rings and a tick every 30 deg on the outer one
        for (let ring = 1; ring <= 3; ring++) {
          const r = (RADAR_R * ring * grow) / 3;
          const path = Skia.PathBuilder.Make();
          let first = true;
          for (let k = 0; k <= 64; k++) {
            if (!project(c, b, w, h, radarPoint(cw, (k / 64) * Math.PI * 2, r), o)) continue;
            if (first) path.moveTo(o[0], o[1]);
            else path.lineTo(o[0], o[1]);
            first = false;
          }
          amber(paint, (ring === 3 ? 0.55 : 0.28) * radarVis);
          canvas.drawPath(path.build(), paint);
        }
        amber(paint, 0.5 * radarVis);
        for (let k = 0; k < 12; k++) {
          const ang = (k / 12) * Math.PI * 2;
          const o2 = [0, 0, 0];
          if (project(c, b, w, h, radarPoint(cw, ang, RADAR_R * grow), o) && project(c, b, w, h, radarPoint(cw, ang, RADAR_R * grow * 1.07), o2)) {
            canvas.drawLine(o[0], o[1], o2[0], o2[1], paint);
          }
        }
        // the sweep: a fading wedge behind a bright leading edge
        const a = radarAngle(run);
        paint.setStyle(PaintStyle.Fill);
        const R = RADAR_R * grow;
        const oc = [0, 0, 0];
        const okC = project(c, b, w, h, cw, oc);
        const SL = 18;
        for (let k = 0; k < SL; k++) {
          const a0 = a - 1.1 * (1 - k / SL);
          const a1 = a - 1.1 * (1 - (k + 1) / SL);
          const o1 = [0, 0, 0];
          const o2 = [0, 0, 0];
          if (!okC || !project(c, b, w, h, radarPoint(cw, a0, R), o1) || !project(c, b, w, h, radarPoint(cw, a1, R), o2)) continue;
          const path = Skia.PathBuilder.Make();
          path.moveTo(oc[0], oc[1]);
          path.lineTo(o1[0], o1[1]);
          path.lineTo(o2[0], o2[1]);
          path.close();
          amber(paint, 0.22 * Math.pow((k + 1) / SL, 2) * radarVis);
          canvas.drawPath(path.build(), paint);
        }
        if (okC && project(c, b, w, h, radarPoint(cw, a, R), o)) {
          paint.setStyle(PaintStyle.Stroke);
          paint.setStrokeWidth(1.4);
          amber(paint, 0.9 * radarVis);
          canvas.drawLine(oc[0], oc[1], o[0], o[1], paint);
        }
        // blips flare as the sweep passes them, then fade
        paint.setStyle(PaintStyle.Fill);
        for (let k = 0; k < BLIPS.length; k++) {
          const [ang, rs] = BLIPS[k];
          let since = (a - ang) % (Math.PI * 2);
          if (since < 0) since += Math.PI * 2;
          const age = (since / (Math.PI * 2)) * 2.1;
          const glow = Math.exp(-age * 2.2);
          if (!project(c, b, w, h, radarPoint(cw, ang, R * rs), o)) continue;
          const rad = Math.max(1.5, (0.035 * b[8]) / o[2]);
          amber(paint, (0.25 + 0.75 * glow) * radarVis);
          canvas.drawCircle(o[0], o[1], rad, paint);
          amber(paint, 0.25 * glow * radarVis);
          canvas.drawCircle(o[0], o[1], rad * 3.2, paint);
        }
        // a small screen-space label off the top of the outer ring
        if (label != null && project(c, b, w, h, radarPoint(cw, Math.PI * 0.25, R * 1.12), o)) {
          amber(paint, 0.85 * radarVis);
          canvas.drawText("3 nearby", o[0] + 6, o[1] - 4, paint, label);
          amber(paint, 0.6 * radarVis);
          canvas.drawText("closest 11.6 m", o[0] + 6, o[1] + 10, paint, label);
        }
      }

      // ---- voice module at the mouthpiece
      if (voiceVis > 0.004) {
        const mw = helmetPoint(MOUTH, lift);
        const okM = project(c, b, w, h, mw, o);
        const ax = o[0];
        const ay = o[1];
        paint.setStyle(PaintStyle.Stroke);
        paint.setStrokeWidth(1.2);
        // three ripples in the mouthpiece's plane
        for (let k = 0; k < 3; k++) {
          const ph = (run / 1.6 + k / 3) % 1;
          const r = 0.1 + 0.55 * ph;
          const path = Skia.PathBuilder.Make();
          let first = true;
          for (let s = 0; s <= 40; s++) {
            const ang = (s / 40) * Math.PI * 2;
            const ca = Math.cos(ang) * r;
            const sa = Math.sin(ang) * r;
            const pt: Vec3 = [
              mw[0] + ca * MOUTH_AXIS_A[0] + sa * MOUTH_AXIS_B[0],
              mw[1] + ca * MOUTH_AXIS_A[1] + sa * MOUTH_AXIS_B[1],
              mw[2] + ca * MOUTH_AXIS_A[2] + sa * MOUTH_AXIS_B[2],
            ];
            if (!project(c, b, w, h, pt, o)) continue;
            if (first) path.moveTo(o[0], o[1]);
            else path.lineTo(o[0], o[1]);
            first = false;
          }
          amber(paint, 0.6 * (1 - ph) * voiceVis);
          canvas.drawPath(path.build(), paint);
        }
        if (okM) {
          // waveform under the helmet, a leader up to the mouthpiece
          const wy = voiceWaveY(ay, h, limitY);
          const bw = 3;
          const gap = 3.2;
          const total = BARS * bw + (BARS - 1) * gap;
          const x0 = w / 2 - total / 2;
          const rise = easeOutCubic(voiceVis);
          paint.setStyle(PaintStyle.Stroke);
          paint.setStrokeWidth(1);
          amber(paint, 0.45 * voiceVis);
          canvas.drawLine(ax, ay + 6, ax, ay + 6 + (wy - 26 - ay - 6) * rise, paint);
          paint.setStyle(PaintStyle.Fill);
          amber(paint, 0.9 * voiceVis);
          canvas.drawCircle(ax, ay, 2.6, paint);
          for (let i = 0; i < BARS; i++) {
            const v = voiceBar(i, BARS, run);
            const bh = 3 + 30 * v * rise;
            const x = x0 + i * (bw + gap);
            amber(paint, (0.45 + 0.5 * v) * voiceVis);
            canvas.drawRRect({ rect: { x, y: wy - bh / 2, width: bw, height: bh }, rx: 1.5, ry: 1.5 }, paint);
          }
        }
      }
    });
  });

  return (
    <Canvas pointerEvents="none" style={[StyleSheet.absoluteFill, { width: w, height: h }]}>
      <Picture picture={picture} />
    </Canvas>
  );
}
