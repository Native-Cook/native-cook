import {
  BlurStyle,
  ClipOp,
  PaintStyle,
  Skia,
  StrokeCap,
  TileMode,
  type SkCanvas,
  type SkImage,
  type SkPath,
} from '@shopify/react-native-skia';

import {
  type Cup,
  type Geo,
  LOOP,
  type Pearl,
  foam,
  halfWidth,
  level,
  pearlAt,
  pour,
  seal,
  straw,
} from './bobaMotion';

/*
 One clear plastic cup, drawn in its own frame (origin at the middle of its base, y down on
 screen, heights negative): back rim, milk tea with a waving surface and brown-sugar streaks,
 the pearls (one drawAtlas call from a pre-rendered sprite), the tea's tint over them, foam, the
 pour, the sealing film and straw, then the front of the glass (highlights, rim, condensation).
 */

const SPRITE = 96;
const SPRITE_R = 44;

function fill(color: string) {
  'worklet';
  const p = Skia.Paint();
  p.setAntiAlias(true);
  p.setColor(Skia.Color(color));
  return p;
}

function stroke(color: string, width: number) {
  'worklet';
  const p = fill(color);
  p.setStyle(PaintStyle.Stroke);
  p.setStrokeWidth(width);
  p.setStrokeCap(StrokeCap.Round);
  return p;
}

/** Two glossy tapioca pearls side by side (front, and a darker one for the back layer), drawn once. */
export function makePearlSprite(): SkImage | null {
  const surface = Skia.Surface.Make(SPRITE * 2, SPRITE);
  if (!surface) return null;
  const c = surface.getCanvas();
  c.clear(Skia.Color('transparent'));
  for (let k = 0; k < 2; k += 1) {
    const cx = SPRITE * k + SPRITE / 2;
    const cy = SPRITE / 2;
    const body = Skia.Paint();
    body.setAntiAlias(true);
    const stops = k === 0 ? ['#6B3A22', '#3E1F11', '#1F0E07'] : ['#4E2918', '#2C150B', '#150904'];
    body.setShader(
      Skia.Shader.MakeRadialGradient(Skia.Point(cx - 12, cy - 14), SPRITE_R * 1.35, stops.map((s) => Skia.Color(s)), [0, 0.5, 1], TileMode.Clamp),
    );
    c.drawCircle(cx, cy, SPRITE_R, body);
    // Translucent rim, a warm bounce at the bottom right, and a sharp window highlight.
    c.drawCircle(cx, cy, SPRITE_R - 1.5, stroke('rgba(150,84,48,0.35)', 3));
    const bounce = fill('rgba(214,140,90,0.28)');
    bounce.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 5, true));
    c.drawCircle(cx + 14, cy + 16, 12, bounce);
    const soft = fill('rgba(255,240,225,0.45)');
    soft.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 4, true));
    c.drawOval(Skia.XYWHRect(cx - 26, cy - 30, 26, 16), soft);
    c.drawOval(Skia.XYWHRect(cx - 21, cy - 26, 12, 7), fill(k === 0 ? 'rgba(255,250,244,0.9)' : 'rgba(255,250,244,0.6)'));
  }
  surface.flush();
  const image = surface.makeImageSnapshot();
  return image.makeNonTextureImage() ?? image;
}

function cupPath(g: Geo, inset: number): SkPath {
  'worklet';
  const rw = g.rimW - inset;
  const bw = g.baseW - inset;
  const p = Skia.PathBuilder.Make();
  p.moveTo(-rw, -g.cupH);
  p.lineTo(-bw, 0);
  p.arcToOval(Skia.XYWHRect(-bw, -g.baseRy, bw * 2, g.baseRy * 2), 180, -180, false);
  p.lineTo(rw, -g.cupH);
  p.close();
  return p.build();
}

function surfaceY(x: number, lvl: number, tilt: number, amp: number, clock: number, u: number) {
  'worklet';
  const w = (2 * Math.PI * clock) / LOOP;
  return -lvl + Math.tan(tilt) * x + amp * (Math.sin((x * 0.09) / u + w * 6) * 0.6 + Math.sin((x * 0.05) / u - w * 4 + 1.3) * 0.4);
}

export function drawCup(c: SkCanvas, g: Geo, cup: Cup, pearls: Pearl[], sprite: SkImage, clock: number) {
  'worklet';
  const u = g.u;
  const tc = cup.tc;
  const lvl = level(g, tc);
  const [pourIn, pourOut] = pour(tc);
  const pouring = pourIn > 0 && pourOut < 1;
  c.save();
  c.translate(g.cx + cup.dx, g.base);

  // Shadow on the table (outside the rotation), with a little caramel light through the tea.
  const shadow = fill('rgba(92,50,28,0.20)');
  shadow.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 9, true));
  c.drawOval(Skia.XYWHRect(-g.baseW * 1.25 + 8 * u, -4 * u, g.baseW * 2.5, 18 * u), shadow);
  const contact = fill('rgba(92,50,28,0.25)');
  contact.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 2, true));
  c.drawOval(Skia.XYWHRect(-g.baseW * 1.02, -3 * u, g.baseW * 2.04, 7 * u), contact);
  if (lvl > 1) {
    const glow = fill('rgba(214,140,70,0.16)');
    glow.setAlphaf(0.16 * (lvl / g.maxLevel));
    glow.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 10, true));
    c.drawOval(Skia.XYWHRect(g.baseW * 0.2, 0, g.baseW * 1.4, 16 * u), glow);
  }

  c.rotate((cup.rot * 180) / Math.PI, 0, 0);
  const outer = cupPath(g, 0);
  const inner = cupPath(g, 3 * u);
  // Back of the cup: a faint fill and the far half of the rim.
  c.drawPath(outer, fill('rgba(255,255,255,0.13)'));
  const backRim = Skia.PathBuilder.Make();
  backRim.addArc(Skia.XYWHRect(-g.rimW, -g.cupH - g.rimRy, g.rimW * 2, g.rimRy * 2), 180, 180);
  c.drawPath(backRim.build(), stroke('rgba(255,255,255,0.45)', 1.5));

  c.save();
  c.clipPath(inner, ClipOp.Intersect, true);
  const amp = (1.2 + 3 * (pouring ? 1 : 0) + 4 * cup.shake) * u;
  let body: SkPath | null = null;
  if (lvl > 0.5) {
    const bb = Skia.PathBuilder.Make();
    const span = g.rimW + 14 * u;
    bb.moveTo(-span, surfaceY(-span, lvl, cup.tilt, amp, clock, u));
    for (let i = 1; i <= 16; i += 1) {
      const x = -span + (i * span * 2) / 16;
      bb.lineTo(x, surfaceY(x, lvl, cup.tilt, amp, clock, u));
    }
    bb.lineTo(span, 30 * u);
    bb.lineTo(-span, 30 * u);
    bb.close();
    body = bb.build();
    const tea = fill('#C08A5C');
    tea.setShader(
      Skia.Shader.MakeLinearGradient(Skia.Point(0, -lvl), Skia.Point(0, 0), ['#D3A273', '#BC8455', '#94592F'].map((s) => Skia.Color(s)), [0, 0.5, 1], TileMode.Clamp),
    );
    c.drawPath(body, tea);
    // Brown-sugar streaks climbing the walls.
    c.save();
    c.clipPath(body, ClipOp.Intersect, true);
    const streak = stroke('rgba(104,52,20,0.38)', 9 * u);
    streak.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 3.2 * u, false));
    for (let side = -1; side <= 1; side += 2) {
      for (let k = 0; k < 2; k += 1) {
        const p = Skia.PathBuilder.Make();
        for (let j = 0; j <= 8; j += 1) {
          const y = (lvl * (0.95 - k * 0.3) * j) / 8;
          const x = side * (halfWidth(g, y) - (4 + k * 16) * u - 9 * u * Math.sin(j * 0.9 + k * 2.1 + side));
          if (j === 0) p.moveTo(x, -y);
          else p.lineTo(x, -y);
        }
        c.drawPath(p.build(), streak);
      }
    }
    c.restore();
  }

  // Pearls in one batch.
  const srcs = [];
  const xforms = [];
  for (let i = 0; i < pearls.length; i += 1) {
    const p = pearls[i];
    const [px, py] = pearlAt(g, p, tc);
    if (py < 0) continue;
    const settled = Math.max(0, 1 - py / (g.pearlR * 10));
    const jig = cup.shake * g.pearlR * 0.35;
    const jx = Math.sin(cup.phase * 31 + i * 1.7) * jig;
    const jy = Math.abs(Math.sin(cup.phase * 27 + i * 2.3)) * jig;
    const hw = halfWidth(g, py) - p.r;
    const x = Math.max(-hw, Math.min(hw, px + cup.roll * settled * (0.6 + 0.4 * (py / (g.pearlR * 6))) + jx));
    const s = p.r / SPRITE_R;
    srcs.push(Skia.XYWHRect(p.back ? SPRITE : 0, 0, SPRITE, SPRITE));
    xforms.push(Skia.RSXform(s, 0, x - s * (SPRITE / 2), -py - jy - s * (SPRITE / 2)));
  }
  if (xforms.length > 0) {
    const ap = Skia.Paint();
    ap.setAntiAlias(true);
    c.drawAtlas(sprite, srcs, xforms, ap);
  }

  if (body) {
    c.drawPath(body, fill('rgba(196,140,92,0.30)'));
    // Foam from the shake, then the surface seen from a little above.
    const f = foam(tc);
    const rx = halfWidth(g, lvl);
    if (f > 0) c.drawRect(Skia.XYWHRect(-g.rimW, -lvl, g.rimW * 2, 12 * u * f), fill('rgba(240,222,196,0.85)'));
    c.save();
    c.translate(0, surfaceY(0, lvl, cup.tilt, amp, clock, u));
    c.rotate((cup.tilt * 180) / Math.PI, 0, 0);
    const top = fill('#DDB58C');
    top.setShader(
      Skia.Shader.MakeLinearGradient(Skia.Point(-rx, 0), Skia.Point(rx, 0), [f > 0.5 ? '#F3E4CE' : '#DDB58C', f > 0.5 ? '#E6CFAE' : '#C99B6E'].map((s) => Skia.Color(s)), null, TileMode.Clamp),
    );
    c.drawOval(Skia.XYWHRect(-rx, -rx * 0.13, rx * 2, rx * 0.26), top);
    c.drawOval(Skia.XYWHRect(-rx * 0.7, -rx * 0.09, rx * 0.9, rx * 0.07), fill('rgba(255,248,236,0.35)'));
    c.restore();
  }
  c.restore();

  // The pour, from a spout above the frame.
  if (pouring) {
    const topY = -g.cupH - 170 * u;
    const surf = -lvl;
    const y0 = topY + (surf - topY) * pourOut;
    const y1 = topY + (surf - topY) * pourIn;
    if (y1 > y0 + 1) {
      const sp = Skia.PathBuilder.Make();
      const steps = 14;
      for (let i = 0; i <= steps; i += 1) {
        const y = y0 + ((y1 - y0) * i) / steps;
        const x = -16 * u + 1.6 * u * Math.sin(y * 0.06 + (clock * 2 * Math.PI * 9) / LOOP) - 3.5 * u;
        if (i === 0) sp.moveTo(x, y);
        else sp.lineTo(x, y);
      }
      for (let i = steps; i >= 0; i -= 1) {
        const y = y0 + ((y1 - y0) * i) / steps;
        const x = -16 * u + 1.6 * u * Math.sin(y * 0.06 + (clock * 2 * Math.PI * 9) / LOOP) + 3.5 * u + (y - topY) * 0.004;
        sp.lineTo(x, y);
      }
      sp.close();
      const sPaint = fill('#C48E5F');
      sPaint.setShader(
        Skia.Shader.MakeLinearGradient(Skia.Point(-21 * u, 0), Skia.Point(-11 * u, 0), ['#A86E42', '#E2B68A', '#B57B4D'].map((s) => Skia.Color(s)), [0, 0.4, 1], TileMode.Clamp),
      );
      c.drawPath(sp.build(), sPaint);
      if (pourIn >= 1 && pourOut <= 0) c.drawOval(Skia.XYWHRect(-28 * u, surf - 4 * u, 24 * u, 8 * u), fill('rgba(240,214,182,0.9)'));
    }
  }

  // Seal film and straw.
  const [drop, squash] = seal(tc);
  const [come, pop] = straw(tc);
  if (drop > 0) {
    const fy = -g.cupH - (1 - drop) * 40 * u;
    c.save();
    c.translate(0, fy);
    c.scale(1, 1 - 0.08 * squash);
    const film = fill('rgba(252,240,226,0.66)');
    film.setAlphaf(Math.min(1, drop * 3) * 0.66);
    c.drawOval(Skia.XYWHRect(-g.rimW - 2 * u, -g.rimRy - 1 * u, g.rimW * 2 + 4 * u, g.rimRy * 2 + 2 * u), film);
    const ring = stroke('rgba(214,96,120,0.7)', 1.6 * u);
    ring.setAlphaf(Math.min(1, drop * 3) * 0.7);
    c.drawOval(Skia.XYWHRect(-g.rimW * 0.5, -g.rimRy * 0.5, g.rimW, g.rimRy), ring);
    c.drawOval(Skia.XYWHRect(-g.rimW * 0.75, -g.rimRy * 0.55, g.rimW * 0.6, g.rimRy * 0.35), fill('rgba(255,255,255,0.35)'));
    if (pop > 0 && pop < 1) {
      const pr = stroke('rgba(255,255,255,0.8)', 1.2 * u);
      pr.setAlphaf(1 - pop);
      c.drawOval(Skia.XYWHRect(33 * u - (4 + 16 * pop) * u, -(2 + 5 * pop) * u, (8 + 32 * pop) * u, (4 + 10 * pop) * u), pr);
    }
    c.restore();
  }

  if (come > 0) {
    c.save();
    c.translate(0, -(1 - come) * 170 * u);
    const sx0 = 12 * u;
    const sx1 = 40 * u;
    const strawP = Skia.PathBuilder.Make();
    strawP.moveTo(sx0 - 7 * u, -12 * u);
    strawP.lineTo(sx1 - 7 * u, -g.cupH - 104 * u);
    strawP.lineTo(sx1 + 7 * u, -g.cupH - 104 * u);
    strawP.lineTo(sx0 + 7 * u, -12 * u);
    strawP.close();
    const sp = fill('#F2A9B8');
    sp.setShader(
      Skia.Shader.MakeLinearGradient(Skia.Point(sx1 - 7 * u, 0), Skia.Point(sx1 + 7 * u, 0), ['#E58FA2', '#FBD3DB', '#E4899D'].map((s) => Skia.Color(s)), [0, 0.35, 1], TileMode.Clamp),
    );
    // Above the film it is solid; inside the cup it shows dimly through the tea.
    const filmY = -g.cupH + (1 - come) * 170 * u;
    c.save();
    c.clipRect(Skia.XYWHRect(-g.w, filmY - g.h, g.w * 2, g.h), ClipOp.Intersect, true);
    c.drawPath(strawP.build(), sp);
    c.restore();
    c.save();
    c.clipRect(Skia.XYWHRect(-g.w, filmY, g.w * 2, g.h), ClipOp.Intersect, true);
    sp.setAlphaf(0.38);
    c.drawPath(strawP.build(), sp);
    c.restore();
    c.restore();
  }

  // Front of the glass: highlights, rims, condensation where the cold tea is.
  const hl = Skia.PathBuilder.Make();
  hl.moveTo(-g.rimW + 9 * u, -g.cupH + 8 * u);
  hl.lineTo(-g.baseW + 8 * u, -6 * u);
  hl.lineTo(-g.baseW + 17 * u, -6 * u);
  hl.lineTo(-g.rimW + 21 * u, -g.cupH + 8 * u);
  hl.close();
  const hlp = fill('#FFFFFF');
  hlp.setShader(
    Skia.Shader.MakeLinearGradient(Skia.Point(0, -g.cupH), Skia.Point(0, 0), ['rgba(255,255,255,0.42)', 'rgba(255,255,255,0.12)', 'rgba(255,255,255,0.3)'].map((s) => Skia.Color(s)), [0, 0.6, 1], TileMode.Clamp),
  );
  c.drawPath(hl.build(), hlp);
  const edgeR = Skia.PathBuilder.Make();
  edgeR.moveTo(g.rimW - 5 * u, -g.cupH + 10 * u);
  edgeR.lineTo(g.baseW - 4 * u, -8 * u);
  c.drawPath(edgeR.build(), stroke('rgba(255,255,255,0.35)', 2 * u));
  c.drawPath(outer, stroke('rgba(255,255,255,0.55)', 1.2));
  const frontRim = Skia.PathBuilder.Make();
  frontRim.addArc(Skia.XYWHRect(-g.rimW, -g.cupH - g.rimRy, g.rimW * 2, g.rimRy * 2), 0, 180);
  c.drawPath(frontRim.build(), stroke('rgba(255,255,255,0.8)', 2.2));
  const baseRing = Skia.PathBuilder.Make();
  baseRing.addArc(Skia.XYWHRect(-g.baseW + 4 * u, -g.baseRy - 6 * u, (g.baseW - 4 * u) * 2, g.baseRy * 2), 0, 180);
  c.drawPath(baseRing.build(), stroke('rgba(255,255,255,0.4)', 1.4));
  for (let i = 0; i < 26; i += 1) {
    const hy = (((i * 0.6180339) % 1) * 0.9 + 0.04) * g.cupH;
    const cold = Math.max(0, Math.min(1, (lvl - hy) / (20 * u)));
    if (cold <= 0) continue;
    const side = ((i * 0.7548776) % 1) * 2 - 1;
    const hx = side * (halfWidth(g, hy) - 4 * u);
    const r = (0.9 + ((i * 0.5698403) % 1) * 1.4) * u;
    const d1 = fill('rgba(90,50,26,0.16)');
    d1.setAlphaf(0.16 * cold);
    c.drawCircle(hx + 0.5, -hy + 0.8, r, d1);
    const d2 = fill('rgba(255,255,255,0.7)');
    d2.setAlphaf(0.7 * cold);
    c.drawCircle(hx - r * 0.3, -hy - r * 0.3, r * 0.45, d2);
  }
  c.restore();
}
