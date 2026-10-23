import Svg, { Circle, ClipPath, Defs, Ellipse, G, LinearGradient, Path, RadialGradient, Rect, Stop } from 'react-native-svg';

// Traced to the pin's icons at 3×. Each glyph is drawn in its own measured box (w × h in points).
type P = { color: string };

/** Invented brand mark in the footprint of the pin's: petals radiating round a centre, one large dot. */
export function BrandMark({ color }: P) {
  const petals = [
    { cx: 9.6, cy: 4.6, a: -62 },
    { cx: 4.8, cy: 12.4, a: 8 },
    { cx: 5.7, cy: 21.4, a: 52 },
    { cx: 12.6, cy: 25.0, a: 92 },
    { cx: 20.6, cy: 20.0, a: 146 },
  ];
  return (
    <Svg width={27} height={29.3} viewBox="0 0 27 29.3">
      {petals.map(p => (
        <Ellipse key={p.a} cx={p.cx} cy={p.cy} rx={4.6} ry={2.0} fill={color} transform={`rotate(${p.a} ${p.cx} ${p.cy})`} />
      ))}
      <Circle cx={20.4} cy={11.4} r={4.6} fill={color} />
    </Svg>
  );
}

export function ChartBoxGlyph({ color }: P) {
  return (
    <Svg width={22} height={22} viewBox="0 0 22 22">
      <Rect x={1} y={1} width={20} height={20} rx={6} stroke={color} strokeWidth={1.9} fill="none" />
      <Path d="M7 16 V11.3 M11 16 V6.6 M15 16 V9.6" stroke={color} strokeWidth={2} strokeLinecap="round" />
    </Svg>
  );
}

export function CardGlyph({ color }: P) {
  return (
    <Svg width={23} height={19} viewBox="0 0 23 19">
      <Rect x={1} y={1} width={21} height={17} rx={4.6} stroke={color} strokeWidth={1.9} fill="none" />
      <Path d="M1.5 6.6 H21.5" stroke={color} strokeWidth={1.9} />
      <Path d="M12.4 12.6 H14.4 M16.9 12.6 H18.4" stroke={color} strokeWidth={2.6} strokeLinecap="round" />
    </Svg>
  );
}

export function ChevronGlyph({ color, w = 7, h = 12, stroke = 2 }: P & { w?: number; h?: number; stroke?: number }) {
  return (
    <Svg width={w} height={h} viewBox={`0 0 ${w} ${h}`}>
      <Path d={`M1.2 1.2 L${w - 1.4} ${h / 2} L1.2 ${h - 1.2}`} stroke={color} strokeWidth={stroke} strokeLinecap="round" strokeLinejoin="round" fill="none" />
    </Svg>
  );
}

export function ArrowRightGlyph({ color }: P) {
  return (
    <Svg width={18} height={15} viewBox="0 0 18 15">
      <Path d="M1 7.5 H16.8 M10.6 1.2 L16.9 7.5 L10.6 13.8" stroke={color} strokeWidth={2} strokeLinecap="round" strokeLinejoin="round" fill="none" />
    </Svg>
  );
}

export function SendGlyph({ color }: P) {
  return (
    <Svg width={17} height={17} viewBox="0 0 18 18">
      <Path d="M1.6 9 L16.4 1.6 L12 9.4 L15.2 16.6 Z" stroke={color} strokeWidth={1.9} strokeLinejoin="round" fill="none" />
    </Svg>
  );
}

export function DownGlyph({ color }: P) {
  return (
    <Svg width={17} height={17} viewBox="0 0 18 18">
      <Path d="M9 1.2 V16.4 M2.6 10.2 L9 16.6 L15.4 10.2" stroke={color} strokeWidth={1.9} strokeLinecap="round" strokeLinejoin="round" fill="none" />
    </Svg>
  );
}

export function PlusGlyph({ color }: P) {
  return (
    <Svg width={17} height={17} viewBox="0 0 18 18">
      <Path d="M9 1.2 V16.8 M1.2 9 H16.8" stroke={color} strokeWidth={1.9} strokeLinecap="round" />
    </Svg>
  );
}

export function MoreGlyph({ color }: P) {
  return (
    <Svg width={20} height={4} viewBox="0 0 20 4">
      <Circle cx={2} cy={2} r={1.75} fill={color} />
      <Circle cx={10} cy={2} r={1.75} fill={color} />
      <Circle cx={18} cy={2} r={1.75} fill={color} />
    </Svg>
  );
}

/** The pin's recurring mark: an open loop with an arrowhead, its tail dotted. */
export function RepeatGlyph({ color }: P) {
  return (
    <Svg width={12} height={12} viewBox="0 0 12 12">
      <Path d="M6.6 1.4 A4.6 4.6 0 0 0 2.6 9.4" stroke={color} strokeWidth={1.3} strokeLinecap="round" fill="none" />
      <Path d="M1.2 7.6 L2.6 9.6 L4.8 8.6" stroke={color} strokeWidth={1.3} strokeLinecap="round" strokeLinejoin="round" fill="none" />
      {[[8.8, 1.9], [10.4, 3.6], [11, 5.9], [10.4, 8.2], [8.9, 9.9]].map(([x, y]) => (
        <Circle key={x * 10 + y} cx={x} cy={y} r={0.65} fill={color} />
      ))}
    </Svg>
  );
}

export function CommandGlyph({ color }: P) {
  return (
    <Svg width={16} height={16} viewBox="0 0 16 16">
      <Path
        d="M5.6 5.6 H10.4 V10.4 H5.6 Z M5.6 5.6 V4.2 A2.6 2.6 0 1 0 3 6.8 H5.6 M10.4 5.6 V4.2 A2.6 2.6 0 1 1 13 6.8 H10.4 M10.4 10.4 V11.8 A2.6 2.6 0 1 0 13 9.2 H10.4 M5.6 10.4 V11.8 A2.6 2.6 0 1 1 3 9.2 H5.6"
        stroke={color} strokeWidth={1.5} strokeLinejoin="round" fill="none" />
    </Svg>
  );
}

export function PeopleGlyph({ color }: P) {
  return (
    <Svg width={17} height={16} viewBox="0 0 17 16">
      <Circle cx={12.4} cy={4.2} r={3} fill={color} opacity={0.32} />
      <Path d="M10.8 8.6 C14.4 8.6 16.6 10.6 16.6 13.2 C16.6 14.6 15.8 15.4 14.4 15.4 H11.6 Z" fill={color} opacity={0.32} />
      <Circle cx={6.4} cy={4.0} r={3.6} fill={color} />
      <Path d="M0.6 13.4 C0.6 10.4 3.2 8.6 6.4 8.6 C9.6 8.6 12.2 10.4 12.2 13.4 C12.2 14.8 11.4 15.6 10 15.6 H2.8 C1.4 15.6 0.6 14.8 0.6 13.4 Z" fill={color} />
    </Svg>
  );
}

/** Invented holding marks in the colours and footprint of the pin's four company logos. */
export function HoldingMark({ kind, size, ring }: { kind: 0 | 1 | 2 | 3; size: number; ring: number }) {
  const r = size / 2;
  const id = `hold${kind}`;
  const body = r - ring;
  return (
    <Svg width={size} height={size} viewBox={`0 0 ${size} ${size}`}>
      <Defs>
        <LinearGradient id={`${id}b`} x1="0" y1="0" x2="1" y2="1">
          <Stop offset="0" stopColor="#1C9ACB" />
          <Stop offset="1" stopColor="#0A82B5" />
        </LinearGradient>
        <LinearGradient id={`${id}s`} x1="0" y1="0" x2="1" y2="0">
          <Stop offset="0" stopColor="#4FE3C1" />
          <Stop offset="1" stopColor="#9B5CF0" />
        </LinearGradient>
      </Defs>
      <Circle cx={r} cy={r} r={r} fill="#F7F7F6" />
      {kind === 0 && (
        <>
          <Circle cx={r} cy={r} r={body} fill="#FFFFFF" />
          <Path
            d={`M${r + 4.7} ${r - 6.7} C${r - 3.8} ${r - 6.1} ${r - 7.1} ${r - 1.1} ${r - 4.8} ${r + 4.8} C${r - 1.4} ${r + 8.1} ${r + 4.5} ${r + 4.8} ${r + 4.7} ${r - 6.7} Z`}
            fill="#050505" />
          <Path d={`M${r - 4.3} ${r + 4.6} Q${r - 0.5} ${r + 0.5} ${r + 4.4} ${r - 6.3}`} stroke="#FFFFFF" strokeWidth={1.15} strokeLinecap="round" fill="none" opacity={0.72} />
        </>
      )}
      {kind === 1 && (
        <>
          <Circle cx={r} cy={r} r={body} fill="#FFFFFF" />
          <Path
            d={`M${r - 8} ${r + 2.9} C${r - 5.9} ${r - 0.7} ${r - 3.7} ${r - 2.7} ${r - 1.2} ${r - 2.7} C${r + 1.6} ${r - 2.7} ${r + 2.1} ${r + 2.6} ${r + 5} ${r + 2.6} C${r + 6.4} ${r + 2.6} ${r + 7.3} ${r + 1.5} ${r + 8} ${r + 0.1}`}
            stroke="#065FD6" strokeWidth={2.45} strokeLinecap="round" strokeLinejoin="round" fill="none" />
        </>
      )}
      {kind === 2 && (
        <>
          <Circle cx={r} cy={r} r={body} fill={`url(#${id}b)`} />
          <Path
            d={`M${r} ${r - 7} C${r + 4.6} ${r - 7} ${r + 7.2} ${r - 4.2} ${r + 6.6} ${r - 0.5} C${r + 3.3} ${r - 2.2} ${r + 0.8} ${r - 2.1} ${r} ${r} C${r - 0.8} ${r - 2.1} ${r - 3.3} ${r - 2.2} ${r - 6.6} ${r - 0.5} C${r - 7.2} ${r - 4.2} ${r - 4.6} ${r - 7} ${r} ${r - 7} Z M${r} ${r} C${r + 2.5} ${r + 1.1} ${r + 3.3} ${r + 3.4} ${r + 1.8} ${r + 6.7} C${r + 5.7} ${r + 6.1} ${r + 7.4} ${r + 2.9} ${r + 6.6} ${r - 0.5} C${r + 3.2} ${r + 0.3} ${r + 1.2} ${r - 0.1} ${r} ${r} Z M${r} ${r} C${r - 2.5} ${r + 1.1} ${r - 3.3} ${r + 3.4} ${r - 1.8} ${r + 6.7} C${r - 5.7} ${r + 6.1} ${r - 7.4} ${r + 2.9} ${r - 6.6} ${r - 0.5} C${r - 3.2} ${r + 0.3} ${r - 1.2} ${r - 0.1} ${r} ${r} Z`}
            fill="#FFFFFF" fillRule="evenodd" />
          <Circle cx={r} cy={r} r={1.3} fill="#168FBD" />
        </>
      )}
      {kind === 3 && (
        <>
          <Circle cx={r} cy={r} r={body} fill="#050505" />
          <Rect x={r - 6.4} y={r - 5.7} width={8.6} height={2.8} rx={1.4} fill={`url(#${id}s)`} transform={`rotate(-18 ${r - 2.1} ${r - 4.3})`} />
          <Rect x={r - 2.7} y={r - 1.4} width={9.2} height={2.8} rx={1.4} fill={`url(#${id}s)`} transform={`rotate(18 ${r + 1.9} ${r})`} />
          <Rect x={r - 6.1} y={r + 3} width={8.2} height={2.8} rx={1.4} fill={`url(#${id}s)`} transform={`rotate(-18 ${r - 2} ${r + 4.4})`} />
        </>
      )}
    </Svg>
  );
}

/** Row marks: invented apps in the colours and footprint of the pin's. */
export function PuffMark({ size }: { size: number }) {
  // A soft white pillow with a slot, on the grey square of the pin's first row.
  const s = size;
  return (
    <Svg width={s} height={s} viewBox={`0 0 ${s} ${s}`}>
      <Defs>
        <LinearGradient id="puffBg" x1="0" y1="0" x2="0" y2="1">
          <Stop offset="0" stopColor="#B7B6B6" />
          <Stop offset="1" stopColor="#C0BFBF" />
        </LinearGradient>
        <RadialGradient id="puff" cx="0.42" cy="0.36" r="0.7">
          <Stop offset="0" stopColor="#FFFFFF" />
          <Stop offset="0.7" stopColor="#F6F6F6" />
          <Stop offset="1" stopColor="#E2E2E2" />
        </RadialGradient>
      </Defs>
      <Rect x={0} y={0} width={s} height={s} rx={12} fill="url(#puffBg)" />
      <Rect x={s * 0.2} y={s * 0.19} width={s * 0.6} height={s * 0.62} rx={s * 0.24} fill="#000" opacity={0.08} transform="translate(0 1.6)" />
      <Rect x={s * 0.2} y={s * 0.19} width={s * 0.6} height={s * 0.62} rx={s * 0.24} fill="url(#puff)" />
      <Rect x={s * 0.45} y={s * 0.37} width={s * 0.1} height={s * 0.26} rx={s * 0.05} fill="#A9A9A8" />
    </Svg>
  );
}

export function DotsMark({ size }: { size: number }) {
  const c = size / 2;
  const d = 5.2;
  return (
    <Svg width={size} height={size} viewBox={`0 0 ${size} ${size}`}>
      <Rect x={c - 8.4} y={c - 8.4} width={7.6} height={3.4} rx={1.7} fill="#36C5F0" />
      <Circle cx={c - d + 1} cy={c + d - 1.2} r={3.4} fill="#E01E5A" />
      <Rect x={c + 1.4} y={c - 9} width={3.4} height={7.6} rx={1.7} fill="#2EB67D" />
      <Circle cx={c + d - 0.4} cy={c + d - 0.6} r={3.4} fill="#ECB22E" />
    </Svg>
  );
}

export function SphereMark({ size }: { size: number }) {
  const c = size / 2;
  const R = 12.6;
  const cells: { x: number; y: number; w: number; h: number }[] = [];
  const n = 5;
  for (let i = 0; i < n; i++) {
    for (let j = 0; j < n; j++) {
      // Grid on a sphere: cells shrink toward the rim.
      const u = (i + 0.5) / n * 2 - 1;
      const v = (j + 0.5) / n * 2 - 1;
      const x = c + Math.sin(u * 1.2) / Math.sin(1.2) * R * 0.86;
      const y = c + Math.sin(v * 1.2) / Math.sin(1.2) * R * 0.86;
      const w = 4.6 * Math.cos(u * 1.05);
      const h = 4.6 * Math.cos(v * 1.05);
      cells.push({ x, y, w, h });
    }
  }
  return (
    <Svg width={size} height={size} viewBox={`0 0 ${size} ${size}`}>
      <Defs>
        <ClipPath id="sphereClip"><Circle cx={c} cy={c} r={R} /></ClipPath>
      </Defs>
      <G clipPath="url(#sphereClip)">
        {cells.map(k => (
          <Rect key={`${k.x}-${k.y}`} x={k.x - k.w / 2} y={k.y - k.h / 2} width={k.w} height={k.h} rx={Math.min(k.w, k.h) * 0.4} fill="#2B2A2A" />
        ))}
      </G>
    </Svg>
  );
}
