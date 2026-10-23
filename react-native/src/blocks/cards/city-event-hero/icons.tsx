import Svg, { Circle, Path, Rect } from 'react-native-svg';

/**
 * Icons of the screen, drawn in code. Geometry is in ref px (1206 px = 402 pt) inside each icon's
 * own box; `at` is where the box sits on the pin.
 */
export const iconBox = {
  gift: { x: 627, y: 258, w: 39, h: 40 },
  filter: { x: 768, y: 262, w: 39, h: 36 },
  search: { x: 1088, y: 261, w: 37, h: 37 },
  heart: { x: 97, y: 2260, w: 39, h: 36 },
  heartFill: { x: 97, y: 2260, w: 39, h: 36 },
  share: { x: 285, y: 2256, w: 36, h: 39 },
  more: { x: 409, y: 2274, w: 33, h: 7 },
  chevView: { x: 1090, y: 2263, w: 15, h: 28 },
  chevCard: { x: 1070, y: 2050, w: 20, h: 36 },
  home: { x: 127, y: 2426, w: 65, h: 69 },
  chat: { x: 349, y: 2429, w: 65, h: 65 },
  plus: { x: 568, y: 2427, w: 69, h: 69 },
  bell: { x: 792, y: 2427, w: 65, h: 68 },
  person: { x: 1014, y: 2427, w: 63, h: 66 },
} as const;

export type IconName = keyof typeof iconBox;

const STROKE = { fill: 'none', strokeLinecap: 'round', strokeLinejoin: 'round' } as const;

function shapes(name: IconName, c: string) {
  switch (name) {
    case 'gift':
      return (
        <>
          <Rect x={3} y={9} width={33} height={9} rx={3} {...STROKE} stroke={c} strokeWidth={4.8} />
          <Path d="M6.5 18 V33.5 a4 4 0 0 0 4 4 H28.5 a4 4 0 0 0 4 -4 V18" {...STROKE} stroke={c} strokeWidth={4.8} />
          <Path d="M19.5 9 V37.5" {...STROKE} stroke={c} strokeWidth={4.8} strokeLinecap="butt" />
          <Circle cx={15.2} cy={2.6} r={1.6} fill={c} />
          <Circle cx={23.8} cy={2.6} r={1.6} fill={c} />
        </>
      );
    case 'filter':
      return (
        <>
          <Path d="M18 7.3 H37" {...STROKE} stroke={c} strokeWidth={5} />
          <Circle cx={10.2} cy={7.5} r={5.2} {...STROKE} stroke={c} strokeWidth={4.6} />
          <Path d="M2 28.3 H19.5" {...STROKE} stroke={c} strokeWidth={5} />
          <Circle cx={28.7} cy={28.2} r={5.2} {...STROKE} stroke={c} strokeWidth={4.6} />
        </>
      );
    case 'search':
      return (
        <>
          <Circle cx={14.8} cy={14.8} r={12.9} {...STROKE} stroke={c} strokeWidth={4.6} />
          <Path d="M24.4 24.4 L34.2 34.2" {...STROKE} stroke={c} strokeWidth={4.8} />
        </>
      );
    case 'heart':
      return (
        <Path
          d="M19.5 33 C9 25.5 3.2 20.2 3.2 12.8 C3.2 7.6 6.8 3.2 11.8 3.2 C15 3.2 17.8 5 19.5 8 C21.2 5 24 3.2 27.2 3.2 C32.2 3.2 35.8 7.6 35.8 12.8 C35.8 20.2 30 25.5 19.5 33 Z"
          {...STROKE}
          stroke={c}
          strokeWidth={4.4}
        />
      );
    case 'heartFill':
      return (
        <Path
          d="M19.5 33 C9 25.5 3.2 20.2 3.2 12.8 C3.2 7.6 6.8 3.2 11.8 3.2 C15 3.2 17.8 5 19.5 8 C21.2 5 24 3.2 27.2 3.2 C32.2 3.2 35.8 7.6 35.8 12.8 C35.8 20.2 30 25.5 19.5 33 Z"
          fill={c}
          stroke={c}
          strokeWidth={4.4}
          strokeLinejoin="round"
        />
      );
    case 'share':
      return (
        <>
          <Path d="M3 22 V31.5 a5 5 0 0 0 5 5 H28 a5 5 0 0 0 5 -5 V22" {...STROKE} stroke={c} strokeWidth={4.6} />
          <Path d="M18 24 V3.5" {...STROKE} stroke={c} strokeWidth={4.6} />
          <Path d="M9.5 11.5 L18 3.2 L26.5 11.5" {...STROKE} stroke={c} strokeWidth={4.6} />
        </>
      );
    case 'more':
      return (
        <>
          <Circle cx={3.5} cy={3.5} r={3.5} fill={c} />
          <Circle cx={16.5} cy={3.5} r={3.5} fill={c} />
          <Circle cx={29.5} cy={3.5} r={3.5} fill={c} />
        </>
      );
    case 'chevView':
      return <Path d="M3.2 3.2 L12 14 L3.2 24.8" {...STROKE} stroke={c} strokeWidth={4} />;
    case 'chevCard':
      return <Path d="M3.2 3.2 L16 18 L3.2 32.8" {...STROKE} stroke={c} strokeWidth={4} />;
    case 'home':
      return (
        <>
          <Path
            d="M32.5 0 C34.4 0 36 .7 37.5 1.9 L61 20.5 C63.3 22.3 64.8 25 64.8 28 V54.5 C64.8 62.6 59.5 68.6 52 68.6 H13 C5.6 68.6 .2 62.6 .2 54.5 V28 C.2 25 1.7 22.3 4 20.5 L27.5 1.9 C29 .7 30.6 0 32.5 0 Z"
            fill={c}
          />
          <Path d="M21.8 38.8 Q32.5 47.5 43.2 38.8" {...STROKE} stroke="#000000" strokeWidth={5.2} />
        </>
      );
    case 'chat':
      return (
        <>
          <Path
            d="M32.5 2.4 C49.8 2.4 62.6 15.2 62.6 32.5 C62.6 49.8 49.8 62.6 32.5 62.6 H13.5 C7 62.6 2.4 58 2.4 51.5 V32.5 C2.4 15.2 15.2 2.4 32.5 2.4 Z"
            {...STROKE}
            stroke={c}
            strokeWidth={5}
          />
          <Circle cx={23.7} cy={32} r={3.3} fill={c} />
          <Circle cx={41.2} cy={32} r={3.3} fill={c} />
        </>
      );
    case 'plus':
      return (
        <>
          <Circle cx={34.5} cy={34.5} r={31.6} {...STROKE} stroke={c} strokeWidth={5} />
          <Path d="M34.5 22.5 V46.5 M22.5 34.5 H46.5" {...STROKE} stroke={c} strokeWidth={5} />
        </>
      );
    case 'bell':
      return (
        <>
          <Path d="M2.4 51.5 L6 40.5 V29 A26.5 26.5 0 0 1 59 29 V40.5 L62.6 51.5 Z" {...STROKE} stroke={c} strokeWidth={5} />
          <Path d="M20.5 55.5 A12 11.5 0 0 0 44.5 55.5" {...STROKE} stroke={c} strokeWidth={5} />
        </>
      );
    case 'person':
      return (
        <>
          <Circle cx={31.5} cy={16.3} r={13.8} {...STROKE} stroke={c} strokeWidth={5} />
          <Path d="M3.4 62.6 A28.2 21.5 0 0 1 59.6 62.6" {...STROKE} stroke={c} strokeWidth={5} />
        </>
      );
  }
}

/** An icon scaled by `u` (dp per ref px). */
export function Glyph({ name, u, color = '#FFFFFF' }: { name: IconName; u: number; color?: string }) {
  const b = iconBox[name];
  return (
    <Svg width={b.w * u} height={b.h * u} viewBox={`0 0 ${b.w} ${b.h}`}>
      {shapes(name, color)}
    </Svg>
  );
}
