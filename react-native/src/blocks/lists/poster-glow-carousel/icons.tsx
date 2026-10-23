import Svg, { Circle, ClipPath, Defs, G, Path, Rect } from 'react-native-svg';

type IconProps = { size: number; color: string };

export function BackIcon({ size, color }: IconProps) {
  return (
    <Svg width={size} height={size} viewBox="0 0 24 24">
      <Path d="M20 12H5M11 5l-7 7 7 7" stroke={color} strokeWidth={2.2} fill="none" strokeLinecap="round" strokeLinejoin="round" />
    </Svg>
  );
}

// Eight-tooth cog built once from polar points.
const GEAR_PATH = (() => {
  const teeth = 8;
  const pts: string[] = [];
  for (let i = 0; i < teeth * 4; i++) {
    const a = (i / (teeth * 4)) * Math.PI * 2 - Math.PI / 2;
    const r = i % 4 === 0 || i % 4 === 1 ? 11 : 8.4;
    pts.push(`${(12 + Math.cos(a + Math.PI / (teeth * 4)) * r).toFixed(2)} ${(12 + Math.sin(a + Math.PI / (teeth * 4)) * r).toFixed(2)}`);
  }
  return `M${pts.join(' L')} Z`;
})();

export function GearIcon({ size, color }: IconProps) {
  return (
    <Svg width={size} height={size} viewBox="0 0 24 24">
      <Path d={GEAR_PATH} fill={color} strokeLinejoin="round" stroke={color} strokeWidth={1.2} />
      <Circle cx={12} cy={12} r={3.4} fill="#4A4F57" />
    </Svg>
  );
}

const STAR = 'M12 1.6l3.1 6.6 7.2.9-5.3 4.9 1.4 7.1L12 17.6l-6.4 3.5 1.4-7.1L1.7 9.1l7.2-.9z';

/** fill 0..1: the left part painted in `color`, the rest in `off`. */
export function StarIcon({ size, color, off, fill }: IconProps & { off: string; fill: number }) {
  const id = `s${Math.round(fill * 100)}`;
  return (
    <Svg width={size} height={size} viewBox="0 0 24 24">
      <Defs>
        <ClipPath id={id}><Rect x={0} y={0} width={24 * fill} height={24} /></ClipPath>
      </Defs>
      <Path d={STAR} fill={off} />
      <G clipPath={`url(#${id})`}><Path d={STAR} fill={color} /></G>
    </Svg>
  );
}

export function PlayGlyph({ size, color }: IconProps) {
  return (
    <Svg width={size} height={size} viewBox="0 0 24 24">
      <Path d="M7 4.6c0-1.1 1.2-1.8 2.2-1.2l11 6.9c.9.6.9 1.9 0 2.5l-11 6.9C8.2 20.4 7 19.7 7 18.6z" fill={color} />
    </Svg>
  );
}

export function PlayCircleIcon({ size, color }: IconProps) {
  return (
    <Svg width={size} height={size} viewBox="0 0 24 24">
      <Circle cx={12} cy={12} r={10} stroke={color} strokeWidth={1.8} fill="none" />
      <Path d="M10 8.2l6 3.8-6 3.8z" fill={color} />
    </Svg>
  );
}

export function HomeIcon({ size, color }: IconProps) {
  return (
    <Svg width={size} height={size} viewBox="0 0 24 24">
      <Path d="M3 10.6c0-.6.3-1.2.8-1.6l7-5.6a2 2 0 0 1 2.4 0l7 5.6c.5.4.8 1 .8 1.6V19a2 2 0 0 1-2 2h-3.6v-5.4a1.4 1.4 0 0 0-1.4-1.4h-2a1.4 1.4 0 0 0-1.4 1.4V21H5a2 2 0 0 1-2-2z" fill={color} />
    </Svg>
  );
}

export function SearchIcon({ size, color }: IconProps) {
  return (
    <Svg width={size} height={size} viewBox="0 0 24 24">
      <Circle cx={10.6} cy={10.6} r={7.2} stroke={color} strokeWidth={2} fill="none" />
      <Path d="M16 16l5 5" stroke={color} strokeWidth={2} strokeLinecap="round" />
    </Svg>
  );
}

export function SeatIcon({ size, color }: IconProps) {
  return (
    <Svg width={size} height={size} viewBox="0 0 24 24">
      <Path d="M6 11V6.5A2.5 2.5 0 0 1 8.5 4h7A2.5 2.5 0 0 1 18 6.5V11" stroke={color} strokeWidth={1.6} fill="none" />
      <Path d="M3.5 11h3v4.5h11V11h3v6.5h-17z" stroke={color} strokeWidth={1.6} fill="none" strokeLinejoin="round" />
      <Path d="M6 17.5V20M18 17.5V20M9 13h6" stroke={color} strokeWidth={1.6} strokeLinecap="round" />
    </Svg>
  );
}

export function DownloadIcon({ size, color }: IconProps) {
  return (
    <Svg width={size} height={size} viewBox="0 0 24 24">
      <Path d="M12 3.5v12M6.8 10.5l5.2 5.2 5.2-5.2M5 20.5h14" stroke={color} strokeWidth={2} fill="none" strokeLinecap="round" strokeLinejoin="round" />
    </Svg>
  );
}

export function UserIcon({ size, color }: IconProps) {
  return (
    <Svg width={size} height={size} viewBox="0 0 24 24">
      <Circle cx={12} cy={7.2} r={5.2} fill={color} />
      <Path d="M2.5 20.2c0-3.3 4.3-5.6 9.5-5.6s9.5 2.3 9.5 5.6c0 1-.8 1.8-1.8 1.8H4.3c-1 0-1.8-.8-1.8-1.8z" fill={color} />
    </Svg>
  );
}
