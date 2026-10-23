import { Platform } from "react-native";
import {
  ClipOp,
  PaintStyle,
  Skia,
  TileMode,
  matchFont,
  type SkCanvas,
  type SkFont,
} from "@shopify/react-native-skia";
import { tactile } from "./tactile-theme";

// Surfaces of the lifted console that only exist in 3D, painted by Skia into
// textures three.js samples directly (no copy). Drawing units match DeviceArt:
// the housing is 1000 × 284.
const W = 1000,
  H = 284;

const face = (size: number, weight: "300" | "400" | "500" = "400") =>
  matchFont({
    fontFamily: Platform.select({
      ios: "Helvetica Neue",
      default: "sans-serif",
    }),
    fontSize: size,
    fontWeight: weight,
  });

// Deterministic noise so the brushing is the same on every lift.
function random(seed: number) {
  let s = seed;
  return () => (s = (s * 16807) % 2147483647) / 2147483647;
}

function spaced(
  c: SkCanvas,
  text: string,
  font: SkFont,
  cx: number,
  y: number,
  tracking: number,
  color: string,
) {
  const paint = Skia.Paint();
  paint.setAntiAlias(true);
  paint.setColor(Skia.Color(color));
  const widths = [...text].map((ch) => font.measureText(ch).width);
  const total =
    widths.reduce((a, b) => a + b, 0) + tracking * (text.length - 1);
  let x = cx - total / 2;
  [...text].forEach((ch, i) => {
    c.drawText(ch, x, y, paint, font);
    x += widths[i] + tracking;
  });
}

// Laser-etched text: a dark lip above, a faint catch-light below, then the
// frosted fill, so it reads as cut into the metal.
function engraved(
  c: SkCanvas,
  text: string,
  font: SkFont,
  cx: number,
  y: number,
  tracking: number,
  alpha: number,
) {
  spaced(c, text, font, cx, y - 1, tracking, `rgba(0,0,0,${0.55 * alpha})`);
  spaced(
    c,
    text,
    font,
    cx,
    y + 1,
    tracking,
    `rgba(255,255,255,${0.14 * alpha})`,
  );
  spaced(c, text, font, cx, y, tracking, `rgba(200,200,205,${0.75 * alpha})`);
}

function screw(c: SkCanvas, x: number, y: number) {
  const p = Skia.Paint();
  p.setAntiAlias(true);
  p.setShader(
    Skia.Shader.MakeLinearGradient(
      Skia.Point(x, y - 12),
      Skia.Point(x, y + 12),
      [Skia.Color("#5C5C60"), Skia.Color("#2A2A2D"), Skia.Color("#141416")],
      [0, 0.5, 1],
      TileMode.Clamp,
    ),
  );
  c.drawCircle(x, y, 12, p);
  const ring = Skia.Paint();
  ring.setAntiAlias(true);
  ring.setStyle(PaintStyle.Stroke);
  ring.setStrokeWidth(1.4);
  ring.setColor(Skia.Color("rgba(0,0,0,0.7)"));
  c.drawCircle(x, y, 12.6, ring);
  // Six-lobe drive.
  const drive = Skia.PathBuilder.Make();
  for (let i = 0; i < 12; i++) {
    const a = (i * Math.PI) / 6 - Math.PI / 2;
    const r = i % 2 === 0 ? 5.6 : 3.2;
    if (i === 0) drive.moveTo(x + Math.cos(a) * r, y + Math.sin(a) * r);
    else drive.lineTo(x + Math.cos(a) * r, y + Math.sin(a) * r);
  }
  drive.close();
  const ink = Skia.Paint();
  ink.setAntiAlias(true);
  ink.setColor(Skia.Color("#0C0C0D"));
  c.drawPath(drive.detach(), ink);
}

/** The back plate: dark anodized aluminium, brushed, with an etched mark. */
export function paintConsoleBack(c: SkCanvas) {
  const rr = Skia.RRectXY(Skia.XYWHRect(0, 0, W, H), H / 2, H / 2);
  const base = Skia.Paint();
  base.setAntiAlias(true);
  base.setShader(
    Skia.Shader.MakeLinearGradient(
      Skia.Point(0, 0),
      Skia.Point(0, H),
      [Skia.Color("#5A5A5F"), Skia.Color("#45454A"), Skia.Color("#36363A")],
      [0, 0.5, 1],
      TileMode.Clamp,
    ),
  );
  c.drawRRect(rr, base);

  c.save();
  c.clipRRect(rr, ClipOp.Intersect, true);
  const r = random(11);
  const line = Skia.Paint();
  line.setAntiAlias(true);
  line.setStrokeWidth(0.7);
  for (let i = 0; i < 420; i++) {
    const y = r() * H;
    const light = r() < 0.5;
    line.setColor(
      Skia.Color(
        light
          ? `rgba(255,255,255,${0.012 + r() * 0.03})`
          : `rgba(0,0,0,${0.04 + r() * 0.07})`,
      ),
    );
    const x0 = r() * 300;
    c.drawLine(x0, y, x0 + 500 + r() * 500, y, line);
  }
  c.restore();

  engraved(c, "GLOWKNOB", face(40, "500"), W / 2, 136, 15, 1);
  engraved(
    c,
    "TACTILE CONSOLE  ·  GK-01",
    face(14, "500"),
    W / 2,
    170,
    4.5,
    0.75,
  );
  engraved(
    c,
    "Designed in Saigon  ·  Painted by Skia Graphite  ·  Lit by three.js",
    face(11.5),
    W / 2,
    226,
    1.2,
    0.55,
  );

  // The two channels, as small inlaid dots beside the mark.
  const dot = Skia.Paint();
  dot.setAntiAlias(true);
  dot.setColor(Skia.Color(tactile.warm[1]));
  c.drawCircle(W / 2 - 218, 122, 4.5, dot);
  dot.setColor(Skia.Color(tactile.cool[1]));
  c.drawCircle(W / 2 + 218, 122, 4.5, dot);

  for (const [x, y] of [
    [186, 78],
    [814, 78],
    [186, 206],
    [814, 206],
  ])
    screw(c, x, y);

  // Rim: a soft dark edge where the plate meets the chamfer.
  const rim = Skia.Paint();
  rim.setAntiAlias(true);
  rim.setStyle(PaintStyle.Stroke);
  rim.setStrokeWidth(3);
  rim.setColor(Skia.Color("rgba(0,0,0,0.45)"));
  c.drawRRect(rr, rim);
}

/** Knurled edge of the knob, tiled around the cylinder. Each ridge is a
 * soft gradient, not hard lines: the texture has no mipmaps, so hard edges
 * would shimmer as the knob turns. */
export function paintKnurl(c: SkCanvas, width: number, height: number) {
  const ridges = 16;
  const step = width / ridges;
  const ridge = Skia.Paint();
  ridge.setShader(
    Skia.Shader.MakeLinearGradient(
      Skia.Point(0, 0),
      Skia.Point(step, 0),
      ["#1A1A1C", "#2E2E31", "#4E4E52", "#2E2E31", "#1A1A1C"].map((h) =>
        Skia.Color(h),
      ),
      [0, 0.3, 0.55, 0.8, 1],
      TileMode.Repeat,
    ),
  );
  c.drawRect(Skia.XYWHRect(0, 0, width, height), ridge);
}
