/**
 * Where each summary line sits for a depth level, which chip becomes which line, and when each line arrives on the
 * flight clock. Pure numbers, shared by the summary layer and the chips.
 */
import { clamp01, easeOut, easeSheet, span } from './geometry';
import { FLIGHT, MARK, TIMING } from './motion';
import { COPY, LAYOUT, TYPE, type PointCopy } from './theme';

export type Level = 0 | 1 | 2;
export type RowKind = 'lead' | 'tldr' | 'title' | 'body';
export type Row = {
  key: string;
  text: string;
  x: number;
  y: number;
  width: number;
  height: number;
  kind: RowKind;
  // The chip that becomes this line (−1: the line rises in at `at`).
  chip: number;
  at: number;
  point?: PointCopy;
  sup?: string;
};
/** A tappable block of the summary (the lead, or one point) and the key sentence it came from. */
export type Group = { x: number; y: number; width: number; height: number; source: number; label: string };
export type ChipTarget = { x: number; y: number; width: number; height: number } | null;
export type SummaryLayout = {
  level: Level;
  rows: Row[];
  groups: Group[];
  targets: ChipTarget[];
  // Whether each chip becomes its line (true) or melts into a line another chip becomes (false).
  owns: boolean[];
  entities: { y: number; at: number; items: number[] } | null;
  height: number;
  // When the last chip lands, and when everything has arrived (ms on the flight clock).
  land: number;
  total: number;
};
/** Measured line breaks: the lead of each level, and each point's body. */
export type SummaryLines = { leads: string[][]; bodies: string[][] };

export const LINE = { lead: TYPE.lead.lineHeight, tldr: TYPE.tldr.lineHeight, title: TYPE.itemTitle.lineHeight, body: TYPE.itemBody.lineHeight } as const;

/**
 * The order the chips leave in, so no chip overtakes another: the one at the front of the travel goes first. Forward
 * (dir 1, upward into the summary) and back down to sentences below the summary (dir −2) the lowest goes first; back
 * up to sentences above it (dir −1) the top one goes first.
 */
function slot(chip: number, dir: number): number {
  'worklet';
  return dir > 0 || dir < -1.5 ? MARK.count - 1 - chip : chip;
}

/** When a chip starts to move on the flight clock: forward at its slot, back after the in-place swap. */
export function chipStart(chip: number, dir: number): number {
  'worklet';
  return slot(chip, dir) * FLIGHT.stagger + (dir < 0 ? FLIGHT.swapMs : 0);
}

/** A chip's own progress 0 → 1 on the flight clock (forward), or 1 → 0 on the way back, on the sheet curve. */
export function chipU(d: number, chip: number, dir: number): number {
  'worklet';
  const e = easeSheet(clamp01((d - chipStart(chip, dir)) / FLIGHT.ms));
  return dir > 0 ? e : 1 - e;
}

/** When a chip lands on its line (forward). */
export function chipLand(chip: number): number {
  'worklet';
  return chipStart(chip, 1) + FLIGHT.ms;
}

/** The way back: every chip has swapped in place, left and landed home by then. */
export const BACK_MS = FLIGHT.swapMs + (MARK.count - 1) * FLIGHT.stagger + FLIGHT.ms;

/**
 * The swap between a chip and the summary line it becomes, 0 = the chip, 1 = the line. It happens only where the
 * line is: forward over the chip's last swapMs, back over the first swapMs (every line at once, before any chip moves).
 */
export function lineSwap(d: number, chip: number, dir: number): number {
  'worklet';
  if (dir < 0) return 1 - span(d, 0, FLIGHT.swapMs);
  const land = chipLand(chip);
  return span(d, land - FLIGHT.swapMs, land);
}

/** A line's opacity at swap progress s (it comes in after its chip has mostly cleared). */
export function swapLineAlpha(s: number): number {
  'worklet';
  return span(s, FLIGHT.swapLine[0], FLIGHT.swapLine[1]);
}

/** A chip's opacity at swap progress s (it clears before its line comes in). */
export function swapChipAlpha(s: number): number {
  'worklet';
  return 1 - span(s, FLIGHT.swapChip[0], FLIGHT.swapChip[1]);
}

/**
 * The swap between a chip and its sentence on the page, 0 = the chip, 1 = the page's copy. Forward every sentence
 * lifts off the page as the flight starts (a chip is drawn exactly like its sentence there, and waits in place for
 * its turn while the page shrinks and rewinds under it); back the copy returns over the chip's last homeMs.
 */
export function homeSwap(d: number, chip: number, dir: number): number {
  'worklet';
  if (dir > 0) return d > 0 ? 0 : 1;
  const home = chipStart(chip, dir) + FLIGHT.ms;
  return span(d, home - FLIGHT.homeMs, home);
}

/** Fade and rise of a line or chip that has no chip of its own, from `at`, on the strong ease-out. */
export function riseIn(d: number, at: number, ms: number): number {
  'worklet';
  return easeOut(clamp01((d - at) / ms));
}

/** Opacity (0 → 1) of a summary line for the flight clock d and direction. */
export function rowAlpha(row: Row, d: number, dir: number, reduced: boolean): number {
  'worklet';
  if (reduced) return dir > 0 ? clamp01(d / TIMING.reducedFade) : 1;
  if (dir < 0) return swapLineAlpha(1 - span(d, 0, FLIGHT.swapMs));
  if (row.chip >= 0) return swapLineAlpha(lineSwap(d, row.chip, dir));
  return riseIn(d, row.at, FLIGHT.lineMs);
}

/** Top of the summary's room on screen: under the card. */
export function summaryTop(): number {
  return LAYOUT.summaryTop + LAYOUT.cardHeight + LAYOUT.summaryGap;
}

/** One row per measured line, so each line can arrive on its own. */
export function layoutLevel(lines: SummaryLines, width: number, level: Level): SummaryLayout {
  const copy = COPY.levels[level];
  const rows: Row[] = [];
  const groups: Group[] = [];
  const left = LAYOUT.textInset;
  const textWidth = width - LAYOUT.textInset * 2;
  const leadKind: RowKind = copy.tldr ? 'tldr' : 'lead';
  const leadLine = copy.tldr ? LINE.tldr : LINE.lead;
  // The group each key sentence's chip becomes; for a group fed by several chips, the last one to land owns it.
  const owner = (group: number) => {
    let c = -1;
    copy.chips.forEach((g, i) => { if (g === group && (c < 0 || chipLand(i) > chipLand(c))) c = i; });
    return c;
  };
  const targets: ChipTarget[] = [];
  let y = 0;
  const leadChip = owner(0);
  (lines.leads[level] ?? []).forEach((text, i) => {
    rows.push({ key: `l${i}`, text, x: left, y, width: textWidth, height: leadLine, kind: leadKind, chip: leadChip, at: 0 });
    y += leadLine;
  });
  const leadRect = { x: left, y: 0, width: textWidth, height: y };
  groups.push({ ...leadRect, source: 0, label: copy.lead });
  // When the last chip lands; entity chips and the footer wait for it.
  let land = 0;
  copy.chips.forEach((g, i) => { if (g >= 0) land = Math.max(land, chipLand(i)); });
  // Entity chips: those whose words this level actually says.
  const said = [copy.lead, ...copy.points.map((n) => COPY.points[n].body)].join(' ');
  const items = COPY.entities.map((_, i) => i).filter((i) => said.includes(COPY.entities[i].term));
  let entities: SummaryLayout['entities'] = null;
  if (items.length > 0) {
    y += LAYOUT.entityGap;
    entities = { y, at: land + FLIGHT.entityAfter, items };
    y += LAYOUT.entityHeight;
  }
  copy.points.forEach((n, k) => {
    const point = COPY.points[n];
    // Under the entity chips the first point needs only the usual gap.
    y += k === 0 && !entities ? LAYOUT.leadGap : LAYOUT.itemGap;
    const top = y;
    const x = left + LAYOUT.itemIndent;
    const chip = owner(k + 1);
    // The title rises in once its point's chip has landed (that chip, taller than its line, reaches into it).
    const at = chip >= 0 ? chipLand(chip) : land;
    rows.push({ key: `t${k}`, text: point.title, x, y, width: textWidth - LAYOUT.itemIndent, height: LINE.title, kind: 'title', chip: -1, at, point, sup: COPY.sup[k] });
    y += LINE.title;
    const bodyTop = y;
    (lines.bodies[n] ?? []).forEach((text, i) => {
      rows.push({ key: `b${k}-${i}`, text, x, y, width: textWidth - LAYOUT.itemIndent, height: LINE.body, kind: 'body', chip, at, point });
      y += LINE.body;
    });
    groups.push({ x: left, y: top, width: textWidth, height: y - top, source: point.source, label: `${point.title}. ${point.body}` });
    targets[k + 1] = { x, y: bodyTop, width: textWidth - LAYOUT.itemIndent, height: y - bodyTop };
  });
  targets[0] = leadRect;
  const chipTargets = copy.chips.map((g) => (g >= 0 ? targets[g] ?? null : null));
  const owns = copy.chips.map((g, i) => g >= 0 && owner(g) === i);
  const lastLine = rows.reduce((m, r) => Math.max(m, r.chip >= 0 ? chipLand(r.chip) : r.at + FLIGHT.lineMs), 0);
  const entityEnd = entities ? entities.at + (items.length - 1) * FLIGHT.entityStagger + FLIGHT.entityMs : 0;
  const total = Math.max(lastLine, entityEnd, FLIGHT.barAt + FLIGHT.barMs, land + FLIGHT.rollAfter + FLIGHT.rollMs) + 50;
  return { level, rows, groups, targets: chipTargets, owns, entities, height: y, land, total };
}

/** A key sentence's pulse while the summary is being made: one sentence at a time, 200 ms each, after a grace. */
export function pulse(wait: number, index: number): number {
  'worklet';
  if (wait < MARK.pulseDelay) return 0;
  const phase = (wait - MARK.pulseDelay) / MARK.pulseMs;
  const at = Math.floor(phase);
  if (at % MARK.count !== index) return 0;
  return Math.sin((phase - at) * Math.PI);
}

/** A row near an edge of the summary's room fades out before the edge reaches it, so a line is never shown cut. */
export function edgeFade(rowY: number, rowHeight: number, scrollY: number, room: number, contentHeight: number): number {
  'worklet';
  const band = LAYOUT.rowsBreath;
  const top = rowY - scrollY;
  const rest = Math.max(0, contentHeight + band - room) - scrollY;
  const fromTop = clamp01(top / band + clamp01(1 - scrollY / band));
  const fromBottom = clamp01((room - top - rowHeight) / band + clamp01(1 - rest / band));
  return fromTop * fromBottom;
}

/** The footer's distance from the bottom edge. */
export function footerBottom(insetBottom: number): number {
  return Math.max(LAYOUT.footerMin, insetBottom - LAYOUT.footerLift);
}

/** Splits a line around the entity terms it holds (and the price, which rolls). */
export type Segment = { text: string; entity: number };
// Pieces of one line sit side by side in a row; a space at a piece's edge must not be trimmed away by text layout.
const keepEdges = (s: string) => s.replace(/^ /, '\u00A0').replace(/ $/, '\u00A0');
export function segments(text: string): Segment[] {
  return split(text).map((s) => ({ ...s, text: keepEdges(s.text) }));
}

function split(text: string): Segment[] {
  const out: Segment[] = [];
  let rest = text;
  while (rest.length > 0) {
    let best = -1;
    let at = rest.length;
    COPY.entities.forEach((e, i) => {
      const j = rest.indexOf(e.term);
      if (j >= 0 && j < at) { at = j; best = i; }
    });
    if (best < 0) { out.push({ text: rest, entity: -1 }); break; }
    if (at > 0) out.push({ text: rest.slice(0, at), entity: -1 });
    out.push({ text: COPY.entities[best].term, entity: best });
    rest = rest.slice(at + COPY.entities[best].term.length);
  }
  return out;
}
