/*
 Mention Autocomplete — people in the channel and the text model of the composer.

 The composer's text is plain text plus "chips": ranges of it that are a mention of a person.
 Everything here is plain TypeScript shared by the loop's script and by a person's typing.
*/

export type Person = {
  id: string;
  first: string;
  last: string;
  role: string;
  /** Three-stop gradient of the avatar disc. */
  colors: [string, string, string];
  online: boolean;
};

/** Ordered by who spoke last in the channel: an empty query lists them in this order. */
export const PEOPLE: Person[] = [
  { id: 'theo', first: 'Theo', last: 'Lindqvist', role: 'Design', colors: ['#C3CB86', '#97A356', '#727E3A'], online: true },
  { id: 'priya', first: 'Priya', last: 'Raman', role: 'Product', colors: ['#D1A6C9', '#A9739F', '#83507D'], online: true },
  { id: 'maya', first: 'Maya', last: 'Okafor', role: 'Copy lead', colors: ['#F3A882', '#E07A5C', '#C4573F'], online: true },
  { id: 'jonah', first: 'Jonah', last: 'Weiss', role: 'Marketing', colors: ['#F2C978', '#DBA545', '#B88228'], online: false },
  { id: 'marcus', first: 'Marcus', last: 'Bell', role: 'Engineering', colors: ['#8CC0D1', '#5797AD', '#3A768C'], online: true },
  { id: 'mayra', first: 'Mayra', last: 'Diaz', role: 'Support', colors: ['#A6CDAF', '#76AA84', '#548A63'], online: false },
];

export const MAX_ROWS = 4;

export function fullName(p: Person) {
  return `${p.first} ${p.last}`;
}

export function mention(p: Person) {
  return `@${fullName(p)}`;
}

/** First names that start with the query, then last names, then names containing it; at most MAX_ROWS. */
export function rank(query: string, people: Person[] = PEOPLE): number[] {
  const q = query.toLowerCase();
  if (q.length === 0) return people.slice(0, MAX_ROWS).map((_, i) => i);
  const first: number[] = [];
  const last: number[] = [];
  const rest: number[] = [];
  people.forEach((p, i) => {
    if (p.first.toLowerCase().startsWith(q)) first.push(i);
    else if (p.last.toLowerCase().startsWith(q)) last.push(i);
    else if (fullName(p).toLowerCase().includes(q)) rest.push(i);
  });
  return [...first, ...last, ...rest].slice(0, MAX_ROWS);
}

/** The stretch of the person's full name that the query matched, for the bold letters. */
export function matchRange(p: Person, query: string): [number, number] | null {
  const q = query.toLowerCase();
  if (q.length === 0) return null;
  const name = fullName(p).toLowerCase();
  if (name.startsWith(q)) return [0, q.length];
  const lastAt = p.first.length + 1;
  if (name.slice(lastAt).startsWith(q)) return [lastAt, lastAt + q.length];
  const at = name.indexOf(q);
  return at < 0 ? null : [at, at + q.length];
}

// ---- Composer text ---------------------------------------------------------------------------------

export type Chip = { start: number; len: number; person: number };

export type Doc = {
  text: string;
  chips: Chip[];
  /** Index into chips of the chip a first Backspace selected, or -1. */
  selected: number;
};

export const EMPTY_DOC: Doc = { text: '', chips: [], selected: -1 };

/** Plain and chip runs of a doc, in order, for drawing it. */
export type Run = { text: string; chip: number };

export function runs(doc: Doc): Run[] {
  const out: Run[] = [];
  let at = 0;
  doc.chips.forEach((c, i) => {
    if (c.start > at) out.push({ text: doc.text.slice(at, c.start), chip: -1 });
    out.push({ text: doc.text.slice(c.start, c.start + c.len), chip: i });
    at = c.start + c.len;
  });
  if (at < doc.text.length) out.push({ text: doc.text.slice(at), chip: -1 });
  return out;
}

/** The mention being typed before the caret: where its "@" is and what follows it, or null. */
export function activeQuery(doc: Doc, caret: number): { at: number; query: string } | null {
  const before = doc.text.slice(0, caret);
  const m = /(^|\s)@([A-Za-z]*)$/.exec(before);
  if (!m) return null;
  const at = caret - m[2].length - 1;
  if (doc.chips.some((c) => at >= c.start && at < c.start + c.len)) return null;
  return { at, query: m[2] };
}

export type Edit =
  /** A normal edit: the new doc and where the caret ended up. */
  | { kind: 'text'; doc: Doc; caret: number }
  /** Backspace reached a chip: select it (first press) or break it into plain text (second). */
  | { kind: 'select'; chip: number }
  | { kind: 'break'; chip: number; doc: Doc };

/** Interprets a change of the input's plain text against the doc. */
export function applyText(doc: Doc, next: string): Edit {
  const prev = doc.text;
  let p = 0;
  while (p < prev.length && p < next.length && prev[p] === next[p]) p += 1;
  let ep = prev.length;
  let en = next.length;
  while (ep > p && en > p && prev[ep - 1] === next[en - 1]) {
    ep -= 1;
    en -= 1;
  }
  const delta = en - ep;
  const pureDelete = en === p && ep > p;
  const hit = doc.chips.findIndex((c) => c.start < ep && c.start + c.len > p);
  if (hit >= 0 && pureDelete) {
    if (doc.selected !== hit) return { kind: 'select', chip: hit };
    return { kind: 'break', chip: hit, doc: { text: prev, chips: doc.chips.filter((_, i) => i !== hit), selected: -1 } };
  }
  const chips: Chip[] = [];
  for (const c of doc.chips) {
    if (c.start + c.len <= p) chips.push(c);
    else if (c.start >= ep) chips.push({ ...c, start: c.start + delta });
    // A chip the edit overlapped is no longer a mention.
  }
  return { kind: 'text', doc: { text: next, chips, selected: -1 }, caret: en };
}

/** Replaces the typed "@query" (from `at` to the caret) with a chip for the person, and a space. */
export function insertMention(doc: Doc, at: number, caret: number, person: number): { doc: Doc; chip: number; caret: number } {
  const label = mention(PEOPLE[person]);
  const text = `${doc.text.slice(0, at)}${label} ${doc.text.slice(caret)}`;
  const delta = label.length + 1 - (caret - at);
  const chips: Chip[] = [];
  let index = 0;
  for (const c of doc.chips) {
    if (c.start < at) {
      chips.push(c);
      index += 1;
    } else chips.push({ ...c, start: c.start + delta });
  }
  chips.splice(index, 0, { start: at, len: label.length, person });
  return { doc: { text, chips, selected: -1 }, chip: index, caret: at + label.length + 1 };
}
