/** OKLCH heat evaluated once per UI frame, rather than per fragment. All inputs/outputs are sRGB. */
function linear(v: number) {
  'worklet';
  return v <= 0.04045 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4);
}
function gamma(v: number) {
  'worklet';
  return Math.max(0, Math.min(1, v <= 0.0031308 ? 12.92 * v : 1.055 * Math.pow(Math.max(0, v), 1 / 2.4) - 0.055));
}
function lab(rgb: number[]) {
  'worklet';
  const r = linear(rgb[0]),
    g = linear(rgb[1]),
    b = linear(rgb[2]);
  const l = Math.cbrt(0.41222147 * r + 0.53633254 * g + 0.05144599 * b);
  const m = Math.cbrt(0.2119035 * r + 0.68069955 * g + 0.10739696 * b);
  const s = Math.cbrt(0.08830246 * r + 0.28171884 * g + 0.6299787 * b);
  return [
    0.21045426 * l + 0.79361779 * m - 0.00407205 * s,
    1.9779985 * l - 2.42859221 * m + 0.45059371 * s,
    0.02590404 * l + 0.78277177 * m - 0.80867577 * s,
  ];
}
export function heatAt(p: number, a: number[], b: number[], c: number[]) {
  'worklet';
  const u = lab(p < 0.5 ? a : b),
    v = lab(p < 0.5 ? b : c);
  const f = p < 0.5 ? p * 2 : (p - 0.5) * 2;
  const ha = Math.atan2(u[2], u[1]),
    hb = Math.atan2(v[2], v[1]);
  const dh = ((hb - ha + Math.PI * 3) % (Math.PI * 2)) - Math.PI;
  const h = ha + dh * f;
  const chroma = Math.hypot(u[1], u[2]) * (1 - f) + Math.hypot(v[1], v[2]) * f;
  const L = u[0] * (1 - f) + v[0] * f,
    A = chroma * Math.cos(h),
    B = chroma * Math.sin(h);
  const l = Math.pow(L + 0.39633778 * A + 0.21580376 * B, 3);
  const m = Math.pow(L - 0.10556135 * A - 0.06385417 * B, 3);
  const s = Math.pow(L - 0.08948418 * A - 1.29148555 * B, 3);
  return [
    gamma(4.07674166 * l - 3.30771159 * m + 0.23096993 * s),
    gamma(-1.268438 * l + 2.6097574 * m - 0.3413194 * s),
    gamma(-0.00419609 * l - 0.70341861 * m + 1.7076147 * s),
  ];
}
