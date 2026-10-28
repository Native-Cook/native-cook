// Fixed source-visible placements measured in the HQ reference, normalized to 393×852.
// This portable table has no hash/random placement. The moving pink curl around (40,154)
// is deliberately absent: the streamer renderer is its only owner, including at rest.
type PaperKind = 'chip' | 'arc' | 'speck' | 'yellowCurl' | 'greenCurl';
export type TrophyReferencePaperPiece = readonly [x: number, y: number, length: number, width: number, angle: number, bend: number, color: number, kind: PaperKind, opacity: number];
type PaperPiece = TrophyReferencePaperPiece;
export const TROPHY_PAPER_COLORS = ['#F37E87', '#EF98CD', '#68BFE8', '#A5DC63', '#F5D264'];
const COLORS = TROPHY_PAPER_COLORS;
export const TROPHY_REFERENCE_PAPER: readonly PaperPiece[] = [
  [169.5, 3.5, 9, 3.5, 18, 1, 4, 'chip', .85],
  [266.5, 5.5, 11, 5.5, 25, 2, 2, 'chip', .86],
  [114.5, 6.5, 12, 5, -8, 1.5, 3, 'chip', .83],
  [312, 6.5, 7, 2.5, 25, 1, 3, 'chip', .74],
  [230, 7.5, 7, 2.5, 29, .6, 3, 'chip', .72],
  [15, 8.5, 7, 4, 35, 1, 0, 'chip', .86],
  [375, 12, 12, 6, -35, 2, 0, 'chip', .88],
  [56, 17, 11, 5.5, -50, 2.5, 1, 'chip', .80],
  [347, 37.5, 11, 5, 43, 1.5, 2, 'chip', .87],
  [133, 38.5, 10, 6, 25, 1.5, 2, 'chip', .84],
  [4.5, 42, 6, 3, -55, .7, 4, 'chip', .55],
  [238, 45, 6, 2.4, -8, .5, 2, 'chip', .73],
  [79.5, 48, 4, 1.6, 32, .3, 4, 'chip', .63],
  [306, 49, 4, 1.4, -25, .3, 4, 'chip', .61],
  [155, 52, 58, 1.35, 0, 0, 4, 'yellowCurl', .80],
  [197, 52.5, 9, 3.2, 18, 1.3, 0, 'chip', .88],
  [378.5, 57, 3, 1.2, -40, .2, 3, 'speck', .35],
  [38.5, 62.5, 9, 3.2, -20, 1.9, 1, 'arc', .78],
  [375.5, 65, 12, 3.7, 37, 1.5, 0, 'chip', .85],
  [42, 68.5, 2, 1, 20, 0, 1, 'speck', .29],
  [157, 69, 2, 1, 0, 0, 2, 'speck', .28],
  [105.5, 69.5, 3, 1, -10, .2, 2, 'chip', .38],
  [100, 71.5, 9, 2.3, 18, 1.1, 2, 'chip', .78],
  [270.5, 74, 4, 1.8, -33, .5, 4, 'chip', .71],
  [346.5, 82, 8, 3.6, -4, 1.3, 1, 'chip', .82],
  [310.5, 82.5, 2, .9, 0, 0, 4, 'speck', .28],
  [231.5, 85, 14, 4.8, 15, 2.6, 0, 'arc', .87],
  [12, 88, 11, 5, 22, 1.9, 4, 'chip', .87],
  [2, 91.5, 3, 1, 0, 0, 2, 'chip', .35],
  [56.5, 97, 13, 6, -38, 1.7, 0, 'chip', .88],
  [311.5, 98.5, 47, 1.45, 0, 0, 3, 'greenCurl', .83],
  [380.5, 103, 8, 2.7, -10, .8, 1, 'chip', .82],
  [331.5, 113, 3, 1, 20, .2, 4, 'chip', .39],
  [334.5, 113, 6, 2, -15, .6, 2, 'chip', .77],
  [96.5, 125, 14, 4.5, -12, 2.1, 3, 'arc', .82],
  [44.5, 130.5, 8, 3, -18, 2.2, 0, 'arc', .83],
  [18, 139, 7, 2.8, 42, .9, 4, 'chip', .70],
  [71.5, 146, 4, 1.5, -18, .4, 1, 'chip', .65],
  [346.5, 158, 13, 6.5, -35, 2.6, 3, 'chip', .85],
  [311.5, 165, 7, 2.7, 12, 1.5, 1, 'arc', .71],
  [80.5, 176.5, 5, 2.8, -23, .5, 2, 'chip', .72],
  [9, 183, 8, 3.5, -45, 1, 3, 'chip', .78],
  [384, 187.5, 9, 3.6, -32, 1, 3, 'chip', .75],
  [362.5, 199.5, 3, 1.8, -25, .2, 1, 'chip', .62],
  [325.5, 203.5, 11, 5.5, 28, 2.2, 2, 'chip', .88],
  [43.5, 212, 12, 4.7, 20, 2.7, 0, 'arc', .86],
  [17, 231.5, 9, 3.6, 24, 1, 4, 'chip', .77],
  [372, 234.5, 7, 2.4, -20, .7, 0, 'chip', .78],
  [14.5, 11.5, 1.2, 1.2, 0, 0, 4, 'speck', .25],
  [114, 14.5, 1.3, 1, 0, 0, 2, 'speck', .25],
  [341.5, 33, 2, 1, -20, 0, 2, 'speck', .28],
  [340.5, 43, 1.2, 1, 0, 0, 4, 'speck', .25],
  [315, 87.5, 1.4, 1.2, 0, 0, 4, 'speck', .25],
  [324, 96.5, 1.2, 1.8, 0, 0, 3, 'speck', .23],
  [329, 113.5, 1, 1.8, 0, 0, 2, 'speck', .23],
  [371, 143.5, 5, 1.9, -70, .5, 4, 'chip', .66],
  [381.5, 191.5, 1, 1, 0, 0, 4, 'speck', .23],
  [325, 210.5, 1.5, 1, 0, 0, 4, 'speck', .23],
];

export function trophyPaperPieceSVG(piece: PaperPiece) {
  const [x, y, length, width, angle, bend, color, kind, opacity] = piece;
  let shape: string;
  if (kind === 'yellowCurl') {
    shape = `<path d="M10-29C-2-28-7-23-2-14C13 6 7 9-5 16C-10 20-12 23-12 29" fill="none" stroke="${COLORS[color]}" stroke-width="${width}" stroke-linecap="round"/>`;
  } else if (kind === 'greenCurl') {
    shape = `<path d="M2-23C-7-22-10-8 0-12C11-18 13-6 3-6C-10-7-12 9 0 8C12 7 11 19 9 23" fill="none" stroke="${COLORS[color]}" stroke-width="${width}" stroke-linecap="round"/>`;
  } else if (kind === 'speck') {
    shape = `<ellipse rx="${width / 2}" ry="${length / 2}" fill="${COLORS[color]}"/>`;
  } else if (kind === 'arc') {
    const half = length / 2;
    const side = width / 2;
    shape = `<path d="M${-half} ${-side}Q0 ${-bend - side} ${half} ${-side}L${half} ${side}Q0 ${-bend + side} ${-half} ${side}Z" fill="${COLORS[color]}"/>`;
  } else {
    const half = length / 2;
    const side = width / 2;
    shape = `<path d="M${-side} ${-half}Q0 ${-half + bend} ${side} ${-half}C${side * .9} ${-half * .28} ${side * .3} ${half * .36} ${side * .8} ${half}Q0 ${half - bend} ${-side} ${half}C${-side * .3} ${half * .26} ${-side * 1.3} ${-half * .24} ${-side} ${-half}Z" fill="${COLORS[color]}"/><path d="M${-side} ${-half}Q0 ${-half + bend} ${side} ${-half}L${side * .3} ${-half * .4}Z" fill="white" fill-opacity=".18"/>`;
  }
  return `<g transform="translate(${x} ${y}) rotate(${angle})" opacity="${opacity}">${shape}</g>`;
}

// The SVG is generated and parsed once by the artwork module; no per-frame parsing.
// Stable wake ordering matches TrophySavingsMilestoneCelebration's 18 thresholds.
export const TROPHY_APPLAUSE_NEAR_IDS = [29, 34, 27, 45, 38, 44] as const;
export const TROPHY_APPLAUSE_MID_IDS = [9, 26, 15, 33, 39, 24, 17, 47, 7, 43, 22, 36] as const;
export const TROPHY_APPLAUSE_IDS = [...TROPHY_APPLAUSE_NEAR_IDS, ...TROPHY_APPLAUSE_MID_IDS];
const MOVING_IDS = new Set<number>(TROPHY_APPLAUSE_IDS);
export const TROPHY_STATIC_PAPER = TROPHY_REFERENCE_PAPER.filter((_, index) => !MOVING_IDS.has(index));
export const TROPHY_REFERENCE_PAPER_SVG = `<svg xmlns="http://www.w3.org/2000/svg" width="393" height="350" viewBox="0 0 393 350">${TROPHY_STATIC_PAPER.map(trophyPaperPieceSVG).join('')}</svg>`;

export function trophyPaperFacePath(piece: PaperPiece) {
  const [, , length, width, , bend, , kind] = piece;
  const half = length / 2;
  const side = width / 2;
  if (kind === 'arc') return `M${-half} ${-side}Q0 ${-bend - side} ${half} ${-side}L${half} ${side}Q0 ${-bend + side} ${-half} ${side}Z`;
  return `M${-side} ${-half}Q0 ${-half + bend} ${side} ${-half}C${side * .9} ${-half * .28} ${side * .3} ${half * .36} ${side * .8} ${half}Q0 ${half - bend} ${-side} ${half}C${-side * .3} ${half * .26} ${-side * 1.3} ${-half * .24} ${-side} ${-half}Z`;
}
