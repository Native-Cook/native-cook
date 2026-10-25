/** Lightens (amount > 0, toward a warm white) or darkens (amount < 0, toward a warm black) a hex colour. */
export function shade(hex: string, amount: number) {
  const n = parseInt(hex.slice(1), 16);
  const c = [(n >> 16) & 255, (n >> 8) & 255, n & 255];
  const to = amount > 0 ? [255, 244, 226] : [14, 9, 6];
  const a = Math.min(1, Math.abs(amount));
  const out = c.map((v, i) => Math.round(v + (to[i] - v) * a));
  return `#${out.map((v) => v.toString(16).padStart(2, '0')).join('')}`;
}
