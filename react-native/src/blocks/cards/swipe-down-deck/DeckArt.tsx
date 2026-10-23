import Svg, { Circle, Defs, Ellipse, Path, RadialGradient, Rect, Stop } from 'react-native-svg';

export type DeckArtKind = 'barrel' | 'pills' | 'capsules' | 'tablet' | 'plate' | 'bowl' | 'cup';

// One shape of an illustration, in a 100 × 100 box: an ellipse ('e') or a rounded rect ('r') at
// x, y with its size, corner radius, a rotation in degrees about its own centre, colour and alpha.
type Shape = ['e' | 'r', number, number, number, number, number, number, number, number];

const SHADOW: Shape = ['e', 18, 80, 64, 12, 0, 0, 0x000000, 0.22];

/** The stand-in illustrations, drawn the same way in the three stacks. */
export const ART: Record<DeckArtKind, Shape[]> = {
  barrel: [
    SHADOW,
    ['r', 20, 34, 60, 52, 14, 0, 0xc98a4b, 1],
    ['r', 18, 46, 64, 6, 3, 0, 0x9aa3ab, 1],
    ['r', 18, 70, 64, 6, 3, 0, 0x9aa3ab, 1],
    ['e', 17, 24, 66, 22, 0, 0, 0xb07235, 1],
    ['e', 22, 27, 56, 16, 0, 0, 0xcfeaf8, 1],
    ['r', 30, 28, 11, 9, 2.5, 18, 0xffffff, 1],
    ['r', 45, 29, 11, 9, 2.5, -12, 0xf1faff, 1],
    ['r', 58, 30, 10, 8, 2.5, 24, 0xffffff, 1],
  ],
  pills: [
    SHADOW,
    ['r', 12, 40, 58, 26, 13, -32, 0xf3ece2, 1],
    ['r', 21, 50, 40, 6, 3, -32, 0xffffff, 0.8],
    ['r', 32, 46, 58, 26, 13, 18, 0xe8decf, 1],
    ['r', 41, 56, 40, 6, 3, 18, 0xffffff, 0.7],
  ],
  capsules: [
    SHADOW,
    ['r', 16, 34, 62, 26, 13, -40, 0x5cc2ff, 1],
    ['r', 26, 44, 42, 6, 3, -40, 0xb8e6ff, 1],
    ['r', 30, 50, 60, 24, 12, -40, 0x3aa0f0, 1],
    ['r', 40, 59, 40, 6, 3, -40, 0x9ad8ff, 1],
  ],
  tablet: [
    SHADOW,
    ['e', 16, 36, 68, 44, 0, 0, 0xd9c9a6, 1],
    ['e', 16, 28, 68, 44, 0, 0, 0xf4e9d0, 1],
    ['r', 26, 48, 48, 4, 2, 0, 0xd9c9a6, 1],
  ],
  plate: [
    ['e', 12, 76, 76, 14, 0, 0, 0x000000, 0.22],
    ['e', 10, 28, 80, 54, 0, 0, 0xe9e2d6, 1],
    ['e', 16, 32, 68, 44, 0, 0, 0xf6f1e8, 1],
    ['e', 20, 38, 34, 26, 0, -18, 0x6e2f1f, 1],
    ['e', 24, 42, 22, 14, 0, -18, 0x8a4130, 1],
    ['e', 50, 36, 28, 22, 0, 0, 0xffffff, 1],
    ['e', 58, 42, 12, 10, 0, 0, 0xf5a623, 1],
    ['e', 52, 54, 24, 18, 0, 0, 0xffffff, 1],
    ['e', 59, 58, 11, 9, 0, 0, 0xf5a623, 1],
  ],
  bowl: [
    SHADOW,
    ['e', 12, 34, 76, 50, 0, 0, 0xefe8dc, 1],
    ['e', 18, 36, 64, 36, 0, 0, 0x3f7a2c, 1],
    ['e', 22, 38, 22, 16, 0, -20, 0x7bc043, 1],
    ['e', 40, 37, 20, 15, 0, 15, 0x5fa83a, 1],
    ['e', 58, 40, 18, 14, 0, 0, 0xe2483a, 1],
    ['e', 30, 52, 16, 12, 0, 0, 0x9c6b4a, 1],
    ['e', 48, 52, 14, 11, 0, 0, 0xe2483a, 1],
    ['e', 62, 52, 14, 10, 0, 0, 0xc9a27e, 1],
  ],
  cup: [
    ['e', 26, 80, 48, 10, 0, 0, 0x000000, 0.22],
    ['r', 28, 30, 44, 54, 12, 0, 0xf1e9dc, 1],
    ['e', 28, 22, 44, 16, 0, 0, 0xe3d7c3, 1],
    ['e', 31, 24, 38, 11, 0, 0, 0x7fc8ee, 1],
    ['r', 33, 36, 6, 40, 3, 0, 0xffffff, 0.4],
  ],
};

function hex(color: number) {
  return `#${color.toString(16).padStart(6, '0')}`;
}

/** An illustration on its round, lit plate. */
export function DeckArt({ kind, plate, art, id }: { kind: DeckArtKind; plate: number; art: number; id: string }) {
  const scale = art / 100;
  const inset = (plate - art) / 2;
  return (
    <Svg width={plate} height={plate}>
      <Defs>
        <RadialGradient id={id} cx="50%" cy="38%" rx="60%" ry="60%">
          <Stop offset="0" stopColor="#ffffff" stopOpacity={0.26} />
          <Stop offset="1" stopColor="#ffffff" stopOpacity={0.04} />
        </RadialGradient>
      </Defs>
      <Circle cx={plate / 2} cy={plate / 2} r={plate / 2} fill={`url(#${id})`} />
      <Circle cx={plate / 2} cy={plate / 2} r={plate / 2 - 0.5} stroke="#ffffff" strokeOpacity={0.14} fill="none" />
      {ART[kind].map(([type, x, y, w, h, r, deg, color, alpha], i) => {
        const left = inset + x * scale;
        const top = inset + y * scale;
        const width = w * scale;
        const height = h * scale;
        const turn = deg === 0 ? undefined : `rotate(${deg} ${left + width / 2} ${top + height / 2})`;
        return type === 'e' ? (
          <Ellipse
            key={i}
            cx={left + width / 2}
            cy={top + height / 2}
            rx={width / 2}
            ry={height / 2}
            fill={hex(color)}
            fillOpacity={alpha}
            transform={turn}
          />
        ) : (
          <Rect
            key={i}
            x={left}
            y={top}
            width={width}
            height={height}
            rx={r * scale}
            fill={hex(color)}
            fillOpacity={alpha}
            transform={turn}
          />
        );
      })}
    </Svg>
  );
}

// Small icons, each in a 16 × 16 box (strokes) or a 12 × 12 box (the pills' filled marks).
export function PersonIcon({ color }: { color: string }) {
  return (
    <Svg width={12} height={12}>
      <Circle cx={6} cy={3.2} r={2.3} fill={color} />
      <Rect x={3} y={6.4} width={6} height={5.2} rx={2.4} fill={color} />
    </Svg>
  );
}

export function AlertIcon({ color, ink }: { color: string; ink: string }) {
  return (
    <Svg width={12} height={12}>
      <Circle cx={6} cy={6} r={6} fill={color} />
      <Rect x={5.2} y={2.6} width={1.6} height={4.6} rx={0.8} fill={ink} />
      <Circle cx={6} cy={9} r={0.95} fill={ink} />
    </Svg>
  );
}

export function StrokeIcon({ d, color, size = 16, width = 1.7 }: { d: string; color: string; size?: number; width?: number }) {
  const scale = size / 16;
  return (
    <Svg width={size} height={size}>
      <Path
        d={d}
        transform={`scale(${scale})`}
        stroke={color}
        strokeWidth={width / scale}
        strokeLinecap="round"
        strokeLinejoin="round"
        fill="none"
      />
    </Svg>
  );
}

export const ICON_ADD = 'M8 2.5V11.5M4.5 8L8 11.5L11.5 8M3 14H13';
export const ICON_SEARCH = 'M11.6 11.6L14.5 14.5M12.2 7.1A5.1 5.1 0 1 1 2 7.1A5.1 5.1 0 1 1 12.2 7.1Z';
export const ICON_MENU = 'M4 3.5V12.5M8 3.5V12.5M12 3.5V12.5';
export const ICON_TRAY = 'M5 1.5H10A2 2 0 0 1 12 3.5V7M8.5 14H5A2 2 0 0 1 3 12V3.5A2 2 0 0 1 5 1.5M12 9.5V15M9.8 12.8L12 15L14.2 12.8';
export const ICON_UP = 'M8 13V3.5M4.5 7L8 3.5L11.5 7';
