import { BlurStyle, type SkPicture, Skia } from '@shopify/react-native-skia';

/*
 Folded Map Card — the screen behind the map (a forest-dark gradient in three stops with a soft
 light where the map lies, a vignette and grain) and the summit pin, recorded at its tip.
*/

export const BACKDROP_SKSL = `
uniform float2 size;
uniform float p3;

float h2(float2 q) { return fract(sin(dot(q, float2(127.1, 311.7))) * 43758.5453); }

float3 toP3(float3 c) {
  float3 l = pow(clamp(c, 0.0, 1.0), float3(2.2));
  float3 m = float3(
    dot(l, float3(0.8225, 0.1774, 0.0)),
    dot(l, float3(0.0332, 0.9669, 0.0)),
    dot(l, float3(0.0171, 0.0724, 0.9108)));
  return pow(m, float3(1.0 / 2.2));
}

half4 main(float2 p) {
  float2 uv = p / size;
  float3 a = float3(0.105, 0.170, 0.135);
  float3 b = float3(0.080, 0.140, 0.108);
  float3 c0 = float3(0.050, 0.090, 0.070);
  float3 c = mix(a, b, smoothstep(0.0, 0.45, uv.y));
  c = mix(c, c0, smoothstep(0.45, 1.0, uv.y));
  float2 g = (uv - float2(0.5, 0.4)) * float2(1.0, size.y / size.x);
  c += float3(0.05, 0.06, 0.04) * exp(-dot(g, g) * 4.0);
  float2 v = uv - 0.5;
  c *= 1.0 - 0.4 * smoothstep(0.3, 0.9, length(v * float2(1.15, 0.9)));
  c += (h2(floor(p)) - 0.5) * 0.04;
  return half4(half3(p3 > 0.5 ? toP3(c) : c), 1.0);
}
`;

/** A map pin with its tip at (0, 0): a red head with a cream centre, lit from the upper left. */
export function recordPin(): SkPicture {
  const r = Skia.PictureRecorder();
  const c = r.beginRecording(Skia.XYWHRect(-20, -40, 40, 46));
  const p = Skia.Paint();
  p.setAntiAlias(true);
  const head = Skia.PathBuilder.Make();
  head.moveTo(0, 0);
  head.cubicTo(-3, -7, -10, -13, -10, -21);
  head.cubicTo(-10, -27, -5.5, -31, 0, -31);
  head.cubicTo(5.5, -31, 10, -27, 10, -21);
  head.cubicTo(10, -13, 3, -7, 0, 0);
  head.close();
  p.setColor(Skia.Color('rgba(30,10,4,0.4)'));
  p.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 2, true));
  c.save();
  c.translate(1.5, 2);
  c.drawPath(head.build(), p);
  c.restore();
  p.setMaskFilter(null);
  p.setShader(
    Skia.Shader.MakeLinearGradient({ x: -10, y: -31 }, { x: 10, y: -4 }, ['#FF9A72', '#E0643F', '#A8381F'].map((x) => Skia.Color(x)), [0, 0.5, 1], 0),
  );
  c.drawPath(head.build(), p);
  p.setShader(null);
  p.setColor(Skia.Color('#FFF4E6'));
  c.drawCircle(0, -21, 4, p);
  p.setColor(Skia.Color('rgba(255,236,224,0.55)'));
  c.drawCircle(-4.5, -25.5, 2, p);
  return r.finishRecordingAsPicture();
}
