/**
 * The block's glyphs as SVG path data on a 24-point grid (scaled to each glyph's size when drawn),
 * the same paths as the Swift and Kotlin versions. Arcs are polylines of ARC_STEPS segments.
 */

/** The grid every glyph is drawn on. */
export const GLYPH_BOX = 24;
const C = GLYPH_BOX / 2;
const ARC_STEPS = 24;

function pt(x: number, y: number) {
  return `${x.toFixed(2)} ${y.toFixed(2)}`;
}

/** Turns a point of the grid about its centre by `turn` degrees (clockwise). */
function turned(x: number, y: number, turn: number): [number, number] {
  const a = (turn * Math.PI) / 180;
  const dx = x - C;
  const dy = y - C;
  return [C + dx * Math.cos(a) - dy * Math.sin(a), C + dx * Math.sin(a) + dy * Math.cos(a)];
}

/** Lines along an arc of a circle from `from` to `to` degrees (clockwise from 3 o'clock). */
function arc(cx: number, cy: number, r: number, from: number, to: number, turn: number) {
  let d = '';
  for (let i = 0; i <= ARC_STEPS; i++) {
    const a = ((from + ((to - from) * i) / ARC_STEPS) * Math.PI) / 180;
    d += `L${pt(...turned(cx + r * Math.cos(a), cy + r * Math.sin(a), turn))}`;
  }
  return d;
}

/** A paperclip: an inner loop at the bottom, a loop at the top and the outer loop at the bottom, laid along "/". */
export const PAPERCLIP = (() => {
  const turn = 45;
  return (
    `M${pt(...turned(9.5, 8, turn))}` +
    arc(12, 14, 2.5, 180, 0, turn) +
    arc(11, 7, 3.5, 0, -180, turn) +
    arc(12, 16.5, 4.5, 180, 0, turn) +
    `L${pt(...turned(16.5, 8.5, turn))}`
  );
})();

/** Two arrows chasing each other round a circle (Retry): stroke `arcs`, fill `heads`. */
export const ARROWS = (() => {
  const r = 7.5;
  let arcs = '';
  for (const [from, to] of [
    [200, 330],
    [20, 150],
  ]) {
    const a = (from * Math.PI) / 180;
    arcs += `M${pt(C + r * Math.cos(a), C + r * Math.sin(a))}` + arc(C, C, r, from, to, 0);
  }
  let heads = '';
  for (const end of [330, 150]) {
    const a = (end * Math.PI) / 180;
    const x = C + r * Math.cos(a);
    const y = C + r * Math.sin(a);
    // The arcs run clockwise, so each head points along (-sin, cos), a little past the arc's end.
    const [dx, dy] = [-Math.sin(a), Math.cos(a)];
    const [ox, oy] = [Math.cos(a), Math.sin(a)];
    const [bx, by] = [x - dx * 0.6, y - dy * 0.6];
    heads += `M${pt(x + dx * 2.6, y + dy * 2.6)}L${pt(bx + ox * 3, by + oy * 3)}L${pt(bx - ox * 3, by - oy * 3)}Z`;
  }
  return { arcs, heads };
})();

/** The check of the done disc and of "Uploaded". */
export const CHECK = 'M6 12.5L10 16.5L18 7.5';

/** The "!" of the failed pill, filled: a rounded bar and its dot. */
export const BANG =
  'M12 5.5A1.4 1.4 0 0 1 13.4 6.9V12.6A1.4 1.4 0 0 1 10.6 12.6V6.9A1.4 1.4 0 0 1 12 5.5Z' +
  'M12 15.6A1.6 1.6 0 1 1 12 18.8A1.6 1.6 0 1 1 12 15.6Z';
