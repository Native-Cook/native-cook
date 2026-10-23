/**
 * The block's line icon set (24 pt grid, one 1.5 pt stroke, round caps and joins), drawn for the ledger: the
 * paths are the mock's (`lux-1-ledger.html`).
 */
import Svg, { Path, Rect } from 'react-native-svg';

import { ICON } from './theme';

export type GlyphName =
  | 'bag' | 'bolt' | 'dining' | 'tram' | 'ticket' | 'dumbbell' | 'users'
  | 'flag' | 'trash' | 'undo' | 'plus' | 'home' | 'chart' | 'card';

type Props = { name: GlyphName; color: string; size?: number; stroke?: number };

export function Glyph({ name, color, size = ICON.row, stroke = ICON.stroke }: Props) {
  const s = { stroke: color, strokeWidth: stroke, fill: 'none', strokeLinecap: 'round' as const, strokeLinejoin: 'round' as const };
  return (
    <Svg width={size} height={size} viewBox="0 0 24 24">
      {name === 'bag' && (
        <>
          <Path d="M5.5 8.5h13l-.95 10.9a1.7 1.7 0 0 1-1.7 1.6H8.15a1.7 1.7 0 0 1-1.7-1.6z" {...s} />
          <Path d="M9 11V7a3 3 0 0 1 6 0v4" {...s} />
        </>
      )}
      {name === 'bolt' && <Path d="M13.4 2.8 5.6 13.1h5.7l-1.1 8.1 7.8-10.3h-5.7z" {...s} />}
      {name === 'dining' && (
        <>
          <Path d="M6.5 3v5a2.5 2.5 0 0 0 5 0V3" {...s} />
          <Path d="M9 3v4.5" {...s} />
          <Path d="M9 10.5V21" {...s} />
          <Path d="M17.5 21V3c-2.1 1-3.5 3.4-3.5 6.4V14h3.5" {...s} />
        </>
      )}
      {name === 'tram' && (
        <>
          <Rect x={6} y={4.5} width={12} height={12.5} rx={3} {...s} />
          <Path d="M6 11h12" {...s} />
          <Path d="M9.6 20.5 8.4 17M14.4 20.5l1.2-3.5" {...s} />
          <Path d="M10 4.5V3h4v1.5" {...s} />
          <Path d="M9.25 14h.01M14.75 14h.01" {...s} />
        </>
      )}
      {name === 'ticket' && (
        <>
          <Path d="M3.5 7.6A1.6 1.6 0 0 1 5.1 6h13.8a1.6 1.6 0 0 1 1.6 1.6v2.1a2.4 2.4 0 0 0 0 4.6v2.1a1.6 1.6 0 0 1-1.6 1.6H5.1a1.6 1.6 0 0 1-1.6-1.6v-2.1a2.4 2.4 0 0 0 0-4.6z" {...s} />
          <Path d="M14.5 6.5v1.5M14.5 11.25v1.5M14.5 16v1.5" {...s} />
        </>
      )}
      {name === 'dumbbell' && (
        <>
          <Rect x={5} y={6.5} width={3} height={11} rx={1.2} {...s} />
          <Rect x={16} y={6.5} width={3} height={11} rx={1.2} {...s} />
          <Path d="M8 12h8M2.75 10v4M21.25 10v4" {...s} />
        </>
      )}
      {name === 'users' && (
        <>
          <Path d="M15.5 20v-1.5a3.5 3.5 0 0 0-3.5-3.5H6.5A3.5 3.5 0 0 0 3 18.5V20" {...s} />
          <Path d="M9.25 11.5a3.25 3.25 0 1 0 0-6.5 3.25 3.25 0 0 0 0 6.5z" {...s} />
          <Path d="M21 20v-1.5a3.5 3.5 0 0 0-2.6-3.4M15.4 5.1a3.25 3.25 0 0 1 0 6.3" {...s} />
        </>
      )}
      {name === 'flag' && (
        <>
          <Path d="M5.5 21V4" {...s} />
          <Path d="M5.5 4.6c4.2-2 7.4 2 13 0v9c-5.6 2-8.8-2-13 0" {...s} />
        </>
      )}
      {name === 'trash' && (
        <>
          <Path d="M4 6.5h16" {...s} />
          <Path d="M9.5 6.5v-2h5v2" {...s} />
          <Path d="m6.5 6.5.9 12.6A1.6 1.6 0 0 0 9 20.5h6a1.6 1.6 0 0 0 1.6-1.4l.9-12.6" {...s} />
          <Path d="M10.2 10.5v6M13.8 10.5v6" {...s} />
        </>
      )}
      {name === 'undo' && (
        <>
          <Path d="M8.5 14 4 9.5 8.5 5" {...s} />
          <Path d="M4 9.5h10.5a5 5 0 0 1 0 10H11" {...s} />
        </>
      )}
      {name === 'plus' && <Path d="M12 5.5v13M5.5 12h13" {...s} />}
      {name === 'home' && <Path d="M4 10.4 12 4l8 6.4v8.6a1.5 1.5 0 0 1-1.5 1.5H15v-6H9v6H5.5A1.5 1.5 0 0 1 4 19z" {...s} />}
      {name === 'chart' && (
        <>
          <Path d="M4 20.5h16" {...s} />
          <Path d="M7.5 16.5v-5M12 16.5V6.5M16.5 16.5v-8" {...s} />
        </>
      )}
      {name === 'card' && (
        <>
          <Rect x={3} y={5.5} width={18} height={13} rx={2.5} {...s} />
          <Path d="M3 10h18M7 15h3.5" {...s} />
        </>
      )}
    </Svg>
  );
}
