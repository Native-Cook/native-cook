// Card Vacuum Seal — the icons, drawn at the pin's weights (white solid glyphs on the discs, 1.8 pt strokes in the header).
import Svg, { Circle, Path } from 'react-native-svg';

import { ref } from './cvs-theme';

const INK = ref.color.ink;

export function BackArrow({ size }: { size: number }) {
  return (
    <Svg width={size} height={size} viewBox="0 0 24 24">
      <Path
        d="M19.5 12H5.2M11 6.2L5.2 12l5.8 5.8"
        stroke={INK}
        strokeWidth={1.9}
        strokeLinecap="round"
        strokeLinejoin="round"
        fill="none"
      />
    </Svg>
  );
}

export function CloseGlyph({ size }: { size: number }) {
  return (
    <Svg width={size} height={size} viewBox="0 0 24 24">
      <Path d="M7.5 7.5l9 9M16.5 7.5l-9 9" stroke={INK} strokeWidth={2.2} strokeLinecap="round" fill="none" />
    </Svg>
  );
}

/** the eye on "Show details": a white almond with a small iris */
export function EyeGlyph({ size, iris }: { size: number; iris: string }) {
  return (
    <Svg width={size} height={size} viewBox="0 0 24 24">
      <Path d="M2.6 12c2.3-4.1 5.6-6.2 9.4-6.2s7.1 2.1 9.4 6.2c-2.3 4.1-5.6 6.2-9.4 6.2S4.9 16.1 2.6 12z" fill={INK} />
      <Circle cx={12} cy={12} r={3.1} fill={iris} />
      <Circle cx={12} cy={12} r={1.15} fill={INK} />
    </Svg>
  );
}

/** the shield outline shared by both states (the mark inside is drawn separately so it can morph) */
export const SHIELD_D =
  'M12 2.6l7.4 2.9c.5.2.8.6.8 1.1v4.9c0 4.6-3 8.6-7.6 10.1-.4.1-.8.1-1.2 0C6.8 20.1 3.8 16.1 3.8 11.5V6.6c0-.5.3-.9.8-1.1L12 2.6z';
/** the open state's mark: a short upright bar (the alert) */
export const MARK_OPEN_D = 'M12 8.4v5.2';
/** the sealed state's mark: the tick the bar shortens into */
export const MARK_SEALED_D = 'M9.2 12.4l2 2 3.8-4.4';

export function ShieldBase({ size }: { size: number }) {
  return (
    <Svg width={size} height={size} viewBox="0 0 24 24">
      <Path d={SHIELD_D} fill={INK} />
    </Svg>
  );
}

export function ShieldMark({ size, d, color }: { size: number; d: string; color: string }) {
  return (
    <Svg width={size} height={size} viewBox="0 0 24 24">
      <Path d={d} stroke={color} strokeWidth={2.1} strokeLinecap="round" strokeLinejoin="round" fill="none" />
    </Svg>
  );
}

/** "Settings": the pin's rounded nut with a hole that shows the disc */
export function NutGlyph({ size, hole }: { size: number; hole: string }) {
  return (
    <Svg width={size} height={size} viewBox="0 0 24 24">
      <Path
        d="M10.6 3.3c.9-.5 1.9-.5 2.8 0l5.6 3.2c.9.5 1.4 1.4 1.4 2.4v6.2c0 1-.5 1.9-1.4 2.4l-5.6 3.2c-.9.5-1.9.5-2.8 0L5 17.5c-.9-.5-1.4-1.4-1.4-2.4V8.9c0-1 .5-1.9 1.4-2.4l5.6-3.2z"
        fill={INK}
      />
      <Circle cx={12} cy={12} r={3.2} fill={hole} />
    </Svg>
  );
}
