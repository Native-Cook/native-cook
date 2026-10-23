import Svg, { Circle, Polygon, Polyline } from 'react-native-svg';

/** Line icons in a 24-point box: open strokes, closed shapes and circles. */
type Drawing = { lines: number[][]; shapes: number[][]; circles: number[][] };

export const ICONS = {
  arrow: { lines: [[5, 12, 19, 12], [13, 6, 19, 12, 13, 18]], shapes: [], circles: [] },
  timer: { lines: [[12, 13.5, 12, 9.5], [9.5, 3, 14.5, 3], [12, 3, 12, 6]], shapes: [], circles: [[12, 13.5, 7.5]] },
  flashOff: { lines: [[4, 4, 20, 20]], shapes: [[13, 3, 6, 13.5, 11.5, 13.5, 10.5, 21, 18, 10.5, 12.5, 10.5]], circles: [] },
  flashOn: { lines: [], shapes: [[13, 3, 6, 13.5, 11.5, 13.5, 10.5, 21, 18, 10.5, 12.5, 10.5]], circles: [] },
  download: { lines: [[12, 4, 12, 15], [7.5, 10.5, 12, 15, 16.5, 10.5], [5, 19.5, 19, 19.5]], shapes: [], circles: [] },
  share: { lines: [[12, 3, 12, 14], [8, 7, 12, 3, 16, 7], [8.5, 10, 6, 10, 6, 20.5, 18, 20.5, 18, 10, 15.5, 10]], shapes: [], circles: [] },
} satisfies Record<string, Drawing>;

function points(list: number[]) {
  const pairs: string[] = [];
  for (let i = 0; i < list.length; i += 2) pairs.push(`${list[i]},${list[i + 1]}`);
  return pairs.join(' ');
}

/** `fill` fills the closed shapes (the flash when it is on). */
export function Icon({ drawing, size, color, stroke, fill = 'none' }: { drawing: Drawing; size: number; color: string; stroke: number; fill?: string }) {
  return (
    <Svg width={size} height={size} viewBox="0 0 24 24">
      {drawing.circles.map(([cx, cy, r]) => (
        <Circle key={`c${cx}${cy}`} cx={cx} cy={cy} r={r} stroke={color} strokeWidth={stroke} fill="none" />
      ))}
      {drawing.shapes.map((shape) => (
        <Polygon key={`s${shape[0]}${shape[1]}`} points={points(shape)} stroke={color} strokeWidth={stroke} strokeLinejoin="round" fill={fill} />
      ))}
      {drawing.lines.map((line) => (
        <Polyline
          key={`l${line.join()}`}
          points={points(line)}
          stroke={color}
          strokeWidth={stroke}
          strokeLinecap="round"
          strokeLinejoin="round"
          fill="none"
        />
      ))}
    </Svg>
  );
}
