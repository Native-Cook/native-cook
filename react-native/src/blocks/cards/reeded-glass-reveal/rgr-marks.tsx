// Reeded Glass Reveal — vector marks, drawn at the pin's sizes and stroke weights (design.md).
// Invented marks replace the pin's real platform and card-network logos.
import Svg, { Circle, Path, Rect } from 'react-native-svg';

import { comp, ref } from './rgr-theme';

const C = ref.color;

/** ⇋ two half-arrows (the swap glyph), 18 × 12 pt, 1.4 pt round strokes. */
export function SwapGlyph({ size }: { size: number }) {
  const w = comp.swap.glyphW;
  const h = comp.swap.glyphH;
  return (
    <Svg width={w * size} height={h * size} viewBox={`0 0 ${w} ${h}`}>
      <Path
        d="M1 4.3 H17 M1 4.3 L5.2 0.9 M17 7.7 H1 M17 7.7 L12.8 11.1"
        stroke={C.swapGlyph}
        strokeWidth={comp.swap.stroke}
        strokeLinecap="round"
        strokeLinejoin="round"
        fill="none"
      />
    </Svg>
  );
}

/** + glyph, 12.3 pt, 1.4 pt strokes. */
export function PlusGlyph({ size }: { size: number }) {
  const g = comp.plus.glyph;
  return (
    <Svg width={g * size} height={g * size} viewBox={`0 0 ${g} ${g}`}>
      <Path
        d={`M${g / 2} 0.7 V${g - 0.7} M0.7 ${g / 2} H${g - 0.7}`}
        stroke={C.ink}
        strokeWidth={comp.plus.stroke}
        strokeLinecap="round"
        fill="none"
      />
    </Svg>
  );
}

/** "Vidly": a red rounded tile with a white folded V (stands where the pin's video-platform logo was). */
export function VidlyMark({ size }: { size: number }) {
  const { w, h, r } = comp.mark1;
  return (
    <Svg width={w * size} height={h * size} viewBox={`0 0 ${w} ${h}`}>
      <Rect x={0} y={0} width={w} height={h} rx={r} fill={C.vidly} />
      <Path d="M8 5.2 L11.5 11 L15 5.2" stroke={C.ink} strokeWidth={2.1} strokeLinecap="round" strokeLinejoin="round" fill="none" />
    </Svg>
  );
}

/** "Loop": a black disc with a white loop and its cyan / pink offset prints. */
export function LoopMark({ size }: { size: number }) {
  const d = comp.mark2.d;
  const loop = 'M7 11 C7 8.6 9.4 8.6 11 11 C12.6 13.4 15 13.4 15 11 C15 8.6 12.6 8.6 11 11 C9.4 13.4 7 13.4 7 11 Z';
  return (
    <Svg width={d * size} height={d * size} viewBox={`0 0 ${d} ${d}`}>
      <Circle cx={d / 2} cy={d / 2} r={d / 2} fill={C.loopDisc} />
      <Path d={loop} transform="translate(-0.7 -0.6)" stroke={C.loopCyan} strokeWidth={1.7} fill="none" />
      <Path d={loop} transform="translate(0.7 0.6)" stroke={C.loopPink} strokeWidth={1.7} fill="none" />
      <Path d={loop} stroke={C.ink} strokeWidth={1.7} fill="none" />
    </Svg>
  );
}

/** Sable's mark on the card thumbnail: two slanted strokes (an S cut in two), 5 × 5 pt. */
export function SableMark({ size }: { size: number }) {
  return (
    <Svg width={5 * size} height={5 * size} viewBox="0 0 5 5">
      <Path
        d="M3.9 0.6 L1.6 2.3 H3.4 L1.1 4.4"
        stroke={C.thumbInk}
        strokeWidth={0.75}
        strokeLinecap="round"
        strokeLinejoin="round"
        fill="none"
      />
    </Svg>
  );
}

/** The card network's mark (invented): a lozenge split coral / amber, 9 × 5.5 pt. */
export function NetworkMark({ size }: { size: number }) {
  return (
    <Svg width={9 * size} height={5.5 * size} viewBox="0 0 9 5.5">
      <Path d="M2.75 0 H5.4 L3.6 5.5 H2.75 A2.75 2.75 0 0 1 2.75 0 Z" fill={C.netA} />
      <Path d="M5.4 0 H6.25 A2.75 2.75 0 0 1 6.25 5.5 H3.6 Z" fill={C.netB} />
    </Svg>
  );
}
