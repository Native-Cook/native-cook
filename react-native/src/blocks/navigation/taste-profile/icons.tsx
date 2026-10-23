import { type ReactNode } from 'react';
import Svg, { Circle, Ellipse, G, Path, Polygon } from 'react-native-svg';

/**
 * Icons of the profile screen, drawn in code. Each one is drawn in "ref px" of its own measured
 * box (w × h) and the box is scaled by `u` (screen width / 1206), so strokes stay in ref px too.
 */
type IconProps = { w: number; h: number; u: number; color?: string };

function Box({ w, h, u, children }: { w: number; h: number; u: number; children: ReactNode }) {
  return (
    <Svg width={w * u} height={h * u} viewBox={`0 0 ${w} ${h}`} fill="none">
      {children}
    </Svg>
  );
}

const ROUND = { strokeLinecap: 'round', strokeLinejoin: 'round' } as const;

export function ChevronLeft({ w, h, u, color = '#000', stroke = 8 }: IconProps & { stroke?: number }) {
  const s = stroke / 2;
  return (
    <Box w={w} h={h} u={u}>
      <Path d={`M${w - s} ${s} L${s} ${h / 2} L${w - s} ${h - s}`} stroke={color} strokeWidth={stroke} {...ROUND} />
    </Box>
  );
}

export function UserPlus({ w, h, u, color = '#000' }: IconProps) {
  return (
    <Box w={w} h={h} u={u}>
      <G stroke={color} strokeWidth={5} {...ROUND}>
        <Circle cx="17" cy="8.8" r="6.2" />
        <Path d="M4.5 37.5 V34 C4.5 28.5 9 27.5 18.5 27.5 C28 27.5 32.5 28.5 32.5 34 V37.5" />
        <Path d="M34.7 19 H47 M40.9 12.8 V25.2" />
      </G>
    </Box>
  );
}

export function Send({ w, h, u, color = '#000' }: IconProps) {
  return (
    <Box w={w} h={h} u={u}>
      <G stroke={color} strokeWidth={5} {...ROUND}>
        <Path d="M2.5 16 L39.5 2.5 L26 39.5 L18.5 22.5 Z" />
        <Path d="M39.5 2.5 L18.5 22.5" />
      </G>
    </Box>
  );
}

export function Share({ w, h, u, color = '#000' }: IconProps) {
  return (
    <Box w={w} h={h} u={u}>
      <G stroke={color} strokeWidth={5} {...ROUND}>
        <Path d="M2.5 16 V33 A7.5 7.5 0 0 0 10 40.5 H33 A7.5 7.5 0 0 0 40.5 33 V16" />
        <Path d="M21.5 28 V3.5 M12 12.5 L21.5 3 L31 12.5" />
      </G>
    </Box>
  );
}

export function Star({ w, h, u, color = '#000' }: IconProps) {
  // Five-point star, outer radius 18.5, inner 9.3, in a 40 × 40 box (stroke 5.5 → 2.75 inset).
  const pts: string[] = [];
  for (let i = 0; i < 10; i++) {
    const r = i % 2 === 0 ? 18.5 : 9.3;
    const a = (-90 + i * 36) * (Math.PI / 180);
    pts.push(`${(w / 2 + r * Math.cos(a)).toFixed(2)},${(2.75 + 18.5 + r * Math.sin(a)).toFixed(2)}`);
  }
  return (
    <Box w={w} h={h} u={u}>
      <Polygon points={pts.join(' ')} stroke={color} strokeWidth={5.5} {...ROUND} />
    </Box>
  );
}

export function ThumbUp({ w, h, u, color = '#000', down = false }: IconProps & { down?: boolean }) {
  return (
    <Box w={w} h={h} u={u}>
      <G stroke={color} strokeWidth={5.5} {...ROUND} transform={down ? `translate(0 ${h}) scale(1 -1)` : undefined}>
        <Path d="M3 18 H11 V37 H3 Z" />
        <Path d="M11 19 L17.5 18.5 L20.5 4.5 C21 2.8 23.5 3 24.5 4.5 C26.5 7.5 26 10.5 25.5 13.5 L25 16.5 H33.5 C36 16.5 37.5 18.5 37 21 L35 32.5 C34.5 35.5 32.5 37 30 37 H11" />
      </G>
    </Box>
  );
}

export function Meh({ w, h, u, color = '#000' }: IconProps) {
  const c = w / 2;
  return (
    <Box w={w} h={h} u={u}>
      <G stroke={color} strokeWidth={5} {...ROUND}>
        <Circle cx={c} cy={h / 2} r={c - 2.5} />
        <Path d={`M${c - 6.5} ${h / 2 + 7.5} H${c + 6.5}`} />
      </G>
      <Circle cx={c - 6.5} cy={h / 2 - 4} r={2.6} fill={color} />
      <Circle cx={c + 6.5} cy={h / 2 - 4} r={2.6} fill={color} />
    </Box>
  );
}

export function Globe({ w, h, u, color = '#FFF' }: IconProps) {
  const c = w / 2;
  const r = c - 2.5;
  return (
    <Box w={w} h={h} u={u}>
      <G stroke={color} strokeWidth={5} {...ROUND}>
        <Circle cx={c} cy={h / 2} r={r} />
        <Ellipse cx={c} cy={h / 2} rx={r * 0.44} ry={r} />
        <Path d={`M${c - r} ${h / 2} H${c + r} M${c - r * 0.87} ${h / 2 - r * 0.5} H${c + r * 0.87} M${c - r * 0.87} ${h / 2 + r * 0.5} H${c + r * 0.87}`} />
      </G>
    </Box>
  );
}

export function Pin({ w, h, u, color = '#525252' }: IconProps) {
  return (
    <Box w={w} h={h} u={u}>
      <G stroke={color} strokeWidth={4.5} {...ROUND}>
        <Path d="M21 48.5 C11 38 3 30 3 20.5 A18 18 0 0 1 39 20.5 C39 30 31 38 21 48.5 Z" />
        <Circle cx="21" cy="20.5" r="6.5" />
      </G>
    </Box>
  );
}

/** The pin's "1": a stem with a flag, no foot. Drawn in a 17 × 36 box and stretched to w × h. */
export function One({ w, h, u, color = '#000' }: IconProps) {
  return (
    <Svg width={w * u} height={h * u} viewBox="0 0 17 36" preserveAspectRatio="none">
      <Polygon points="16.5,0 8,0 0,4.5 0,13 8.5,13 8.5,36 16.5,36" fill={color} />
    </Svg>
  );
}
