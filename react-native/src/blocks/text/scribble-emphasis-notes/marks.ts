// The finger's own highlights: a fixed number of slots in one flat array, so the UI thread can step and draw
// them without objects. Every function here is a worklet and returns a new array (or null when nothing changed).
import { USER_DRAW, USER_LIFT, USER_MARKS } from './motion';

/** Slot layout: first word, last word, amount on the page 0…1, target 0|1, tint index, used 0|1, age stamp. */
export const SLOT = 7;
export const S_FIRST = 0;
export const S_LAST = 1;
export const S_AMOUNT = 2;
export const S_TARGET = 3;
export const S_TINT = 4;
export const S_USED = 5;
export const S_STAMP = 6;

export function emptyMarks(): number[] {
  const out: number[] = [];
  for (let i = 0; i < USER_MARKS * SLOT; i += 1) out.push(0);
  return out;
}

/** Moves every mark toward its target; a mark that has lifted off frees its slot. */
export function stepMarks(list: number[], dt: number, snap: boolean): number[] | null {
  'worklet';
  let changed = false;
  for (let i = 0; i < USER_MARKS; i += 1) {
    const o = i * SLOT;
    if (list[o + S_USED] === 1 && (list[o + S_AMOUNT] !== list[o + S_TARGET] || list[o + S_TARGET] === 0)) changed = true;
  }
  if (!changed) return null;
  const out = list.slice();
  for (let i = 0; i < USER_MARKS; i += 1) {
    const o = i * SLOT;
    if (out[o + S_USED] === 0) continue;
    const target = out[o + S_TARGET];
    let amount = out[o + S_AMOUNT];
    if (snap) amount = target;
    else if (amount < target) amount = Math.min(target, amount + dt / USER_DRAW);
    else if (amount > target) amount = Math.max(target, amount - dt / USER_LIFT);
    out[o + S_AMOUNT] = amount;
    if (target === 0 && amount <= 0) out[o + S_USED] = 0;
  }
  return out;
}

/** The slot of a mark that is staying and covers `word`, or -1. */
export function markOver(list: number[], word: number) {
  'worklet';
  let found = -1;
  let newest = -1;
  for (let i = 0; i < USER_MARKS; i += 1) {
    const o = i * SLOT;
    if (list[o + S_USED] === 1 && list[o + S_TARGET] === 1 && word >= list[o + S_FIRST] && word <= list[o + S_LAST] && list[o + S_STAMP] > newest) {
      newest = list[o + S_STAMP];
      found = i;
    }
  }
  return found;
}

/** A free slot, or the oldest one. */
export function takeSlot(list: number[]) {
  'worklet';
  let oldest = 0;
  for (let i = 0; i < USER_MARKS; i += 1) {
    const o = i * SLOT;
    if (list[o + S_USED] === 0) return i;
    if (list[o + S_STAMP] < list[oldest * SLOT + S_STAMP]) oldest = i;
  }
  return oldest;
}

/** Writes a mark into a slot. `amount` 0 swipes it on; 1 puts it down whole (a live drag). */
export function putMark(list: number[], slot: number, first: number, last: number, amount: number, tint: number, stamp: number): number[] {
  'worklet';
  const out = list.slice();
  const o = slot * SLOT;
  out[o + S_FIRST] = first;
  out[o + S_LAST] = last;
  out[o + S_AMOUNT] = amount;
  out[o + S_TARGET] = 1;
  out[o + S_TINT] = tint;
  out[o + S_USED] = 1;
  out[o + S_STAMP] = stamp;
  return out;
}

/** Sends one mark (or, with slot -1, all of them) off the page. */
export function liftMarks(list: number[], slot: number): number[] {
  'worklet';
  const out = list.slice();
  for (let i = 0; i < USER_MARKS; i += 1) {
    if (slot >= 0 && i !== slot) continue;
    out[i * SLOT + S_TARGET] = 0;
  }
  return out;
}
