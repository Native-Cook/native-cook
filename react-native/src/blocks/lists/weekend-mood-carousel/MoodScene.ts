import { BlurStyle, FillType, type SkPath, type SkPicture, Skia, TileMode } from '@shopify/react-native-skia';
import { interpolate, interpolateColor } from 'react-native-reanimated';

import { drawFace } from './faces';
import { CHARACTERS } from './moods';
import { HAND_GRADIENT, SHAPE_COLORS, SHAPES } from './shapes';
import { CHARACTER, RULER } from './theme';

const PANEL_COLORS = CHARACTERS.map((c) => c.panel);
const TICK_TOP = CHARACTERS.map((c) => c.tick[0]);
const TICK_BOTTOM = CHARACTERS.map((c) => c.tick[1]);
const LOBE_SOFTNESS = 2.5;
/** The pin's illustrations have soft, painted edges (about 1 px of the original 720p frame). */
const EDGE_SOFTNESS = 0.22;
/** The bag's paper edge is softer still. */
const BAG_SOFTNESS = 0.55;
const STOPS = CHARACTERS.map((_, i) => i);
const DIST = RULER.heights.map((_, i) => i);

export type PanelState = {
  width: number;
  height: number;
  charPos: number;
  selChar: number;
  prevChar: number;
  fill: number;
  age: number;
  phase: number;
  reduced: boolean;
};

function paint(color: string) {
  'worklet';
  const p = Skia.Paint();
  p.setAntiAlias(true);
  p.setColor(Skia.Color(color));
  return p;
}

export type Silhouettes = Record<keyof typeof SHAPES, SkPath | null>;

/** Parse the traced silhouettes once (JS thread); the panel picture draws them every frame. */
export function makeSilhouettes(): Silhouettes {
  const out = {} as Silhouettes;
  for (const k of Object.keys(SHAPES) as (keyof typeof SHAPES)[]) {
    const p = Skia.Path.MakeFromSVGString(SHAPES[k]);
    if (p && k === 'shoppingHandles') p.setFillType(FillType.EvenOdd);
    out[k] = p;
  }
  return out;
}

/** Each character's layers, back to front. */
const LAYERS: (keyof typeof SHAPES)[][] = [
  ['sleepy'],
  ['foodie'],
  ['recoveryLobe', 'recovery'],
  ['binge'],
  ['shoppingHandles', 'shopping'],
  ['scroll', 'scrollHand', 'scrollPhone'],
  ['social'],
];

/** Illustration panel: its colour follows the carousel, characters slide, the selected one wears its expression. */
export function drawPanel(s: PanelState, shapes: Silhouettes): SkPicture {
  'worklet';
  const rec = Skia.PictureRecorder();
  const c = rec.beginRecording(Skia.XYWHRect(0, 0, s.width, s.height));
  const bg = interpolateColor(s.charPos, STOPS, PANEL_COLORS);
  c.drawRect(Skia.XYWHRect(0, 0, s.width, s.height), paint(bg as string));
  const mid = s.width / 2;
  const box = CHARACTER.box;
  for (let i = 0; i < CHARACTERS.length; i++) {
    const cx = mid + CHARACTER.positions[i] - interpolate(s.charPos, STOPS, CHARACTER.positions);
    if (Math.abs(cx - mid) > s.width / 2 + box / 2) continue;
    c.save();
    c.translate(cx, 0);
    const layers = LAYERS[i];
    for (let k = 0; k < layers.length; k++) {
      const path = shapes[layers[k]];
      if (!path) continue;
      if (layers[k] === 'scrollHand') {
        const hp = Skia.Paint();
        hp.setAntiAlias(true);
        hp.setShader(
          Skia.Shader.MakeLinearGradient(
            HAND_GRADIENT.from,
            HAND_GRADIENT.to,
            [Skia.Color(HAND_GRADIENT.colors[0]), Skia.Color(HAND_GRADIENT.colors[1])],
            null,
            TileMode.Clamp,
          ),
        );
        c.drawPath(path, hp);
      } else {
        const lp = paint(SHAPE_COLORS[layers[k]]);
        // the shaded back lobe blends softly into the body in front of it, as on the pin
        lp.setMaskFilter(
          Skia.MaskFilter.MakeBlur(
            BlurStyle.Normal,
            layers[k] === 'recoveryLobe' ? LOBE_SOFTNESS : layers[k] === 'shopping' ? BAG_SOFTNESS : EDGE_SOFTNESS,
            true,
          ),
        );
        c.drawPath(path, lp);
      }
    }
    c.restore();
    const kind = CHARACTERS[i].kind;
    let act = 0;
    let age = 9999;
    if (i === s.selChar) {
      act = kind === 'scroll' ? s.fill : s.age >= 0 ? 1 : 0;
      age = s.age;
    } else if (i === s.prevChar && Math.abs(s.charPos - i) < 1.4 && kind !== 'scroll') {
      act = 1; // keep the face until it has left the screen
    }
    c.save();
    c.translate(cx, 0);
    drawFace(c, kind, act, age, s.phase, s.reduced);
    c.restore();
  }
  return rec.finishRecordingAsPicture();
}

/** Ruler: grey ticks scroll with the chips under a fixed indicator tinted by the current character. */
/** `anchor` is the chip strip position at which a tick sits exactly under the indicator (the opening chip). */
export function drawRuler(chipX: number, anchor: number, charPos: number, width: number): SkPicture {
  'worklet';
  const rec = Skia.PictureRecorder();
  const c = rec.beginRecording(Skia.XYWHRect(0, 0, width, RULER.height));
  const mid = width / 2;
  const pitch = RULER.pitch;
  const off = (((chipX - anchor) % pitch) + pitch) % pitch;
  const base = RULER.baseline;
  const n = Math.ceil(mid / pitch) + 2;
  const maxD = RULER.heights.length - 1;
  for (let k = -n; k <= n; k++) {
    const x = mid + k * pitch - off;
    const d = Math.min(Math.abs(x - mid) / pitch, maxD);
    const h = interpolate(d, DIST, RULER.heights);
    const a = interpolate(d, DIST, RULER.alphas);
    const p = Skia.Paint();
    p.setAntiAlias(true);
    p.setShader(
      Skia.Shader.MakeLinearGradient(
        { x: 0, y: base - h },
        { x: 0, y: base },
        [Skia.Color(`rgba(255,255,255,${a})`), Skia.Color('rgba(255,255,255,0.02)')],
        null,
        TileMode.Clamp,
      ),
    );
    // the pin's ticks are soft-edged, like a printed scale seen through glass
    p.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, RULER.tickSoftness, true));
    const w = RULER.tickWidth;
    c.drawRRect(Skia.RRectXY(Skia.XYWHRect(x - w / 2, base - h, w, h), w / 2, w / 2), p);
  }
  const top = interpolateColor(charPos, STOPS, TICK_TOP) as string;
  const bottom = interpolateColor(charPos, STOPS, TICK_BOTTOM) as string;
  const ip = Skia.Paint();
  ip.setAntiAlias(true);
  ip.setShader(
    Skia.Shader.MakeLinearGradient(
      { x: 0, y: RULER.indicatorTop },
      { x: 0, y: RULER.indicatorBottom },
      [Skia.Color(top), Skia.Color(bottom)],
      null,
      TileMode.Clamp,
    ),
  );
  const iw = RULER.indicatorWidth;
  c.drawRRect(
    Skia.RRectXY(Skia.XYWHRect(mid - iw / 2, RULER.indicatorTop, iw, RULER.indicatorBottom - RULER.indicatorTop), iw / 2, iw / 2),
    ip,
  );
  return rec.finishRecordingAsPicture();
}
