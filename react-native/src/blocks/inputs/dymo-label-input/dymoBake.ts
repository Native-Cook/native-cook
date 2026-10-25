import { BlendMode, BlurStyle, ClipOp, PaintStyle, type SkFont, type SkImage, Skia, StrokeCap, TileMode } from '@shopify/react-native-skia';

import type { Geo } from './dymoArt';
import { STEP, WHEEL } from './dymoMotion';

/* Dymo Label Input — wall and shelf, the jar, the embosser and its wheel, baked once on the JS thread. */

function hexColor(hex: string, a = 1) {
  const v = parseInt(hex.slice(1), 16);
  return Float32Array.of(((v >> 16) & 255) / 255, ((v >> 8) & 255) / 255, (v & 255) / 255, a);
}

export function bakeWall(g: Geo, scale: number): SkImage | null {
  const surface = Skia.Surface.Make(Math.ceil(g.w * scale), Math.ceil(g.h * scale));
  if (!surface) return null;
  const c = surface.getCanvas();
  c.scale(scale, scale);
  const wall = Skia.Paint();
  wall.setShader(
    Skia.Shader.MakeLinearGradient(Skia.Point(0, 0), Skia.Point(0, g.shelfY), [hexColor('#F2EADF'), hexColor('#EADFD0'), hexColor('#DFD1BE')], [0, 0.6, 1], TileMode.Clamp),
  );
  c.drawRect(Skia.XYWHRect(0, 0, g.w, g.h), wall);
  const grain = Skia.Paint();
  grain.setShader(Skia.Shader.MakeFractalNoise(0.8, 0.8, 3, 2, 0, 0));
  grain.setAlphaf(0.05);
  grain.setBlendMode(BlendMode.Multiply);
  c.drawRect(Skia.XYWHRect(0, 0, g.w, g.h), grain);
  // The shelf: a thick oak plank with a lit front edge, and the counter below it.
  const plank = Skia.Paint();
  plank.setShader(
    Skia.Shader.MakeLinearGradient(Skia.Point(0, g.shelfY - 10), Skia.Point(0, g.shelfY + 22), [hexColor('#D2A574'), hexColor('#B88653'), hexColor('#8E6238')], [0, 0.4, 1], TileMode.Clamp),
  );
  const shelfShadow = Skia.Paint();
  shelfShadow.setColor(hexColor('#5B3D22', 0.28));
  shelfShadow.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 12, true));
  c.drawRect(Skia.XYWHRect(-20, g.shelfY + 12, g.w + 40, 18), shelfShadow);
  c.drawRect(Skia.XYWHRect(0, g.shelfY - 8, g.w, 28), plank);
  const woodGrain = Skia.Paint();
  woodGrain.setShader(Skia.Shader.MakeFractalNoise(0.01, 0.35, 3, 6, 0, 0));
  woodGrain.setAlphaf(0.22);
  woodGrain.setBlendMode(BlendMode.Multiply);
  c.drawRect(Skia.XYWHRect(0, g.shelfY - 8, g.w, 28), woodGrain);
  const edge = Skia.Paint();
  edge.setColor(hexColor('#F3D3A7', 0.7));
  c.drawRect(Skia.XYWHRect(0, g.shelfY - 8, g.w, 1.2), edge);
  edge.setColor(hexColor('#6E4826', 0.6));
  c.drawRect(Skia.XYWHRect(0, g.shelfY + 1, g.w, 1), edge);
  // The counter where the embosser sits: warm linen-grey with a soft light pool.
  const counter = Skia.Paint();
  counter.setShader(
    Skia.Shader.MakeRadialGradient(Skia.Point(g.wheelX, g.wheelY - 20), g.w * 0.9, [hexColor('#E7DDCF'), hexColor('#D9CCBA'), hexColor('#C5B59F')], [0, 0.55, 1], TileMode.Clamp),
  );
  c.drawRect(Skia.XYWHRect(0, g.shelfY + 20, g.w, g.h), counter);
  const cg = Skia.Paint();
  cg.setShader(Skia.Shader.MakeFractalNoise(0.6, 0.6, 3, 8, 0, 0));
  cg.setAlphaf(0.06);
  cg.setBlendMode(BlendMode.Multiply);
  c.drawRect(Skia.XYWHRect(0, g.shelfY + 20, g.w, g.h), cg);
  surface.flush();
  return surface.makeImageSnapshot();
}

/** A fresh jar of oats on its own: an image (jarW + 60) × (jarH + 50) pt, the jar's base 20 pt above its bottom edge. */
export function bakeJar(g: Geo, scale: number): SkImage | null {
  const W = g.jarW + 60;
  const H = g.jarH + 50;
  const surface = Skia.Surface.Make(Math.ceil(W * scale), Math.ceil(H * scale));
  if (!surface) return null;
  const c = surface.getCanvas();
  c.scale(scale, scale);
  const x0 = 30;
  const base = 30 + g.jarH;
  const top = 30 + 22;
  const body = Skia.RRectXY(Skia.XYWHRect(x0, top, g.jarW, base - top), 18, 18);
  const shadow = Skia.Paint();
  shadow.setColor(hexColor('#4A301A', 0.32));
  shadow.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 6, true));
  c.drawOval(Skia.XYWHRect(x0 - 4, base - 6, g.jarW + 8, 12), shadow);
  // Oats inside, up to 80 %: a warm base with hundreds of flakes.
  c.save();
  c.clipRRect(body, ClipOp.Intersect, true);
  const fillTop = top + (base - top) * 0.2;
  const oats = Skia.Paint();
  oats.setShader(Skia.Shader.MakeLinearGradient(Skia.Point(0, fillTop), Skia.Point(0, base), [hexColor('#E2CB9E'), hexColor('#CDAE7A'), hexColor('#B08F5D')], [0, 0.5, 1], TileMode.Clamp));
  c.drawRect(Skia.XYWHRect(x0, fillTop, g.jarW, base - fillTop), oats);
  let seed = 7;
  const rnd = () => {
    seed = (seed * 16807) % 2147483647;
    return seed / 2147483647;
  };
  const flake = Skia.Paint();
  flake.setAntiAlias(true);
  for (let k = 0; k < 520; k += 1) {
    const fx = x0 + rnd() * g.jarW;
    const fy = fillTop - 2 + rnd() * (base - fillTop + 2);
    const fw = 4 + rnd() * 5;
    const fh = 2.4 + rnd() * 2.6;
    const shade = rnd();
    flake.setColor(shade > 0.7 ? hexColor('#F4E3BF', 0.9) : shade > 0.35 ? hexColor('#D8BC8A', 0.9) : hexColor('#A9875A', 0.85));
    c.save();
    c.translate(fx, fy);
    c.rotate(rnd() * 180, 0, 0);
    c.drawOval(Skia.XYWHRect(-fw / 2, -fh / 2, fw, fh), flake);
    c.restore();
  }
  // Glass: faint tint, thick base, edges and two tall highlights.
  const tint = Skia.Paint();
  tint.setShader(
    Skia.Shader.MakeLinearGradient(
      Skia.Point(x0, 0),
      Skia.Point(x0 + g.jarW, 0),
      [hexColor('#6E8A86', 0.28), hexColor('#FFFFFF', 0.02), hexColor('#FFFFFF', 0.0), hexColor('#5E7A76', 0.3)],
      [0, 0.25, 0.7, 1],
      TileMode.Clamp,
    ),
  );
  c.drawRRect(body, tint);
  const hi = Skia.Paint();
  hi.setAntiAlias(true);
  hi.setColor(hexColor('#FFFFFF', 0.5));
  c.drawRRect(Skia.RRectXY(Skia.XYWHRect(x0 + g.jarW * 0.12, top + 10, 5, base - top - 26), 2.5, 2.5), hi);
  hi.setColor(hexColor('#FFFFFF', 0.25));
  c.drawRRect(Skia.RRectXY(Skia.XYWHRect(x0 + g.jarW * 0.82, top + 14, 3, base - top - 40), 1.5, 1.5), hi);
  c.restore();
  const rim = Skia.Paint();
  rim.setAntiAlias(true);
  rim.setStyle(PaintStyle.Stroke);
  rim.setStrokeWidth(1.6);
  rim.setColor(hexColor('#8FA6A2', 0.55));
  c.drawRRect(body, rim);
  // Screw lid: brushed brass with ridges.
  const lid = Skia.RRectXY(Skia.XYWHRect(x0 + 6, 30, g.jarW - 12, 24), 5, 5);
  const metal = Skia.Paint();
  metal.setAntiAlias(true);
  metal.setShader(
    Skia.Shader.MakeLinearGradient(Skia.Point(0, 30), Skia.Point(0, 54), [hexColor('#F3DEA4'), hexColor('#C7A15A'), hexColor('#8F6E33'), hexColor('#B89250')], [0, 0.35, 0.75, 1], TileMode.Clamp),
  );
  c.drawRRect(lid, metal);
  const ridge = Skia.Paint();
  ridge.setAntiAlias(true);
  ridge.setStyle(PaintStyle.Stroke);
  ridge.setStrokeWidth(1);
  ridge.setStrokeCap(StrokeCap.Round);
  for (let x = x0 + 10; x < x0 + g.jarW - 10; x += 3.2) {
    ridge.setColor(hexColor(Math.round(x) % 2 === 0 ? '#FFF1C8' : '#7C5C28', 0.45));
    c.drawLine(x, 33, x, 51, ridge);
  }
  surface.flush();
  return surface.makeImageSnapshot();
}

/** The embosser's body, centred on the wheel: an image of side 2 × (wheelR × 3.6) px-pt. */
export function bakeBody(g: Geo, scale: number): SkImage | null {
  const side = g.wheelR * 3.6 * 2;
  const surface = Skia.Surface.Make(Math.ceil(side * scale), Math.ceil(side * scale));
  if (!surface) return null;
  const c = surface.getCanvas();
  c.scale(scale, scale);
  const o = side / 2;
  const R = g.wheelR;
  const shell = Skia.PathBuilder.Make();
  shell.addCircle(o, o, R + 18);
  shell.addRRect(Skia.RRectXY(Skia.XYWHRect(o, o - R * 0.35, R * 2.9, R * 1.05), R * 0.5, R * 0.5));
  const path = shell.build();
  const amb = Skia.Paint();
  amb.setColor(hexColor('#3D2A18', 0.28));
  amb.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 14, true));
  c.save();
  c.translate(4, 14);
  c.drawPath(path, amb);
  c.restore();
  const contact = Skia.Paint();
  contact.setColor(hexColor('#2E1F12', 0.35));
  contact.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 3, true));
  c.save();
  c.translate(1, 3);
  c.drawPath(path, contact);
  c.restore();
  const plastic = Skia.Paint();
  plastic.setAntiAlias(true);
  plastic.setShader(
    Skia.Shader.MakeLinearGradient(Skia.Point(0, o - R - 18), Skia.Point(0, o + R + 18), [hexColor('#4E6C66'), hexColor('#3B5752'), hexColor('#2B403C'), hexColor('#22332F')], [0, 0.35, 0.75, 1], TileMode.Clamp),
  );
  c.drawPath(path, plastic);
  const sheen = Skia.Paint();
  sheen.setAntiAlias(true);
  sheen.setShader(
    Skia.Shader.MakeRadialGradient(Skia.Point(o - R * 0.4, o - R * 0.8), R * 2.2, [hexColor('#FFFFFF', 0.2), hexColor('#FFFFFF', 0.04), hexColor('#FFFFFF', 0)], [0, 0.5, 1], TileMode.Clamp),
  );
  c.drawPath(path, sheen);
  const edge = Skia.Paint();
  edge.setAntiAlias(true);
  edge.setStyle(PaintStyle.Stroke);
  edge.setStrokeWidth(1);
  edge.setColor(hexColor('#9FC1BA', 0.45));
  c.drawPath(path, edge);
  // The wheel's well.
  const well = Skia.Paint();
  well.setAntiAlias(true);
  well.setShader(Skia.Shader.MakeRadialGradient(Skia.Point(o, o), R + 6, [hexColor('#101816'), hexColor('#18231F'), hexColor('#0C1210')], [0, 0.85, 1], TileMode.Clamp));
  c.drawCircle(o, o, R + 5, well);
  // A small embossed brand plate on the handle.
  const plate = Skia.Paint();
  plate.setAntiAlias(true);
  plate.setColor(hexColor('#C9D6D2', 0.18));
  c.drawRRect(Skia.RRectXY(Skia.XYWHRect(o + R * 1.45, o - R * 0.08, R * 1.1, R * 0.32), R * 0.16, R * 0.16), plate);
  surface.flush();
  return surface.makeImageSnapshot();
}

/** The character wheel: a dark disc with raised white characters round the rim, slot 0 at the top. */
export function bakeWheel(g: Geo, scale: number, font: SkFont): SkImage | null {
  const side = g.wheelR * 2 + 8;
  const surface = Skia.Surface.Make(Math.ceil(side * scale), Math.ceil(side * scale));
  if (!surface) return null;
  const c = surface.getCanvas();
  c.scale(scale, scale);
  const o = side / 2;
  const R = g.wheelR;
  const disc = Skia.Paint();
  disc.setAntiAlias(true);
  disc.setShader(Skia.Shader.MakeRadialGradient(Skia.Point(o, o), R, [hexColor('#3A3E45'), hexColor('#2A2D33'), hexColor('#1B1D21')], [0, 0.75, 1], TileMode.Clamp));
  c.drawCircle(o, o, R, disc);
  const ringP = Skia.Paint();
  ringP.setAntiAlias(true);
  ringP.setStyle(PaintStyle.Stroke);
  ringP.setStrokeWidth(1);
  ringP.setColor(hexColor('#FFFFFF', 0.12));
  c.drawCircle(o, o, R - 0.8, ringP);
  c.drawCircle(o, o, R * 0.62, ringP);
  const letter = Skia.Paint();
  letter.setAntiAlias(true);
  const shadow = Skia.Paint();
  shadow.setAntiAlias(true);
  shadow.setColor(hexColor('#000000', 0.5));
  for (let j = 0; j < WHEEL.length; j += 1) {
    const ch = WHEEL[j];
    if (ch === ' ') continue;
    const a = j * STEP;
    const w = font.getGlyphWidths(font.getGlyphIDs(ch))[0] ?? 7;
    c.save();
    c.rotate(a, o, o);
    letter.setColor(hexColor('#ECE8E0'));
    c.drawText(ch, o - w / 2 + 0.5, o - R * 0.73 + 0.8, shadow, font);
    c.drawText(ch, o - w / 2, o - R * 0.73, letter, font);
    c.restore();
  }
  // Grip cut-outs in the hub.
  const hub = Skia.Paint();
  hub.setAntiAlias(true);
  hub.setShader(Skia.Shader.MakeRadialGradient(Skia.Point(o - 6, o - 8), R * 0.55, [hexColor('#5A5F68'), hexColor('#33363C'), hexColor('#202226')], [0, 0.6, 1], TileMode.Clamp));
  c.drawCircle(o, o, R * 0.5, hub);
  const notch = Skia.Paint();
  notch.setAntiAlias(true);
  notch.setColor(hexColor('#16181B'));
  for (let k = 0; k < 6; k += 1) {
    const a = (k / 6) * Math.PI * 2;
    c.drawCircle(o + Math.cos(a) * R * 0.32, o + Math.sin(a) * R * 0.32, R * 0.075, notch);
  }
  c.drawCircle(o, o, R * 0.1, ringP);
  surface.flush();
  return surface.makeImageSnapshot();
}
