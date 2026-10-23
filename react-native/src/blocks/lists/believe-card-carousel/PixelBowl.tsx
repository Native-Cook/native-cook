import Svg, { Defs, LinearGradient, Rect, Stop } from 'react-native-svg';

// 8-bit bowl of fruit for the invented "figo" app icon (the pin's food app shows a pixel bowl).
// Each character is one pixel: b brown rim, d dark wood, g green, l light green, y yellow, w white shine.
const MAP = [
  '....yyyll.....',
  '..yyllgglly...',
  '.ygglwlggyyl..',
  'bllggyygglgyyb',
  'bddbbbbbbbbddb',
  '.bddddddddddb.',
  '..bddddddddb..',
  '....bbbbbb....',
];
const FILL: Record<string, string> = {
  b: '#4A2C17',
  d: '#7A4A26',
  g: '#6DBE35',
  l: '#9BD44A',
  y: '#F2D23A',
  w: '#F7F3C8',
};
const PX = 6;

export function PixelBowl({ size }: { size: number }) {
  const cols = MAP[0].length;
  const ox = (size - cols * PX) / 2;
  const oy = size * 0.24;
  return (
    <Svg width={size} height={size}>
      <Defs>
        <LinearGradient id="sea" x1="0" y1="0" x2="0" y2="1">
          <Stop offset="0" stopColor="#2A8FC4" />
          <Stop offset="1" stopColor="#145E8E" />
        </LinearGradient>
      </Defs>
      <Rect x={0} y={0} width={size} height={size} fill="url(#sea)" />
      {MAP.flatMap((line, r) =>
        line
          .split('')
          .map((ch, c) =>
            ch === '.' ? null : (
              <Rect key={`${r}-${c}`} x={ox + c * PX} y={oy + r * PX} width={PX} height={PX} fill={FILL[ch]} />
            ),
          ),
      )}
    </Svg>
  );
}
