import { LOOP, M, T, panel as panelAt, wrap } from './motion';

/**
 * Owns who drives the scene time and the review draft. Independent of React; times are monotonic seconds.
 *
 * - Demo: s = loop time (only while eligible: autoPlay, foreground, Home tab, feed at top, no owned flow).
 * - User: s follows a path of timeline segments, always forwards, so a tap plays the same frames as the
 *   loop (open = TAP → HOLD, close = CLOSE → REST) and an interruption fast-forwards instead of jumping.
 * - Quiet (Reduce Motion / VoiceOver): the hero stays at rest; the composer fades in 120 ms.
 */
export type Owner = 'demo' | 'user';
export type Seg = { from: number; to: number; rate: number };
export type Plan = {
  mode: 'demo' | 'path' | 'static';
  anchor: number;
  startedAt: number;
  segs: Seg[];
  s: number;
  quiet: boolean;
  qFrom: number;
  qTo: number;
  qStart: number;
};
export const QUIET_FADE = 0.12;
export const RESTART_DELAY = 2;

export function graphemes(value: string): string[] {
  const Segmenter = (Intl as typeof Intl & { Segmenter?: new (locales?: string | string[], options?: { granularity: 'grapheme' }) => { segment(input: string): Iterable<{ segment: string }> } }).Segmenter;
  return Segmenter ? Array.from(new Segmenter(undefined, { granularity: 'grapheme' }).segment(value), (part) => part.segment) : Array.from(value);
}
export function limitNote(value: string) { return graphemes(value).slice(0, 280).join(''); }

/** Scene time along a segment path `elapsed` seconds after it started. */
export function pathAt(segs: Seg[], elapsed: number) {
  'worklet';
  let e = Math.max(0, elapsed);
  for (let i = 0; i < segs.length; i++) {
    const g = segs[i];
    const d = (g.to - g.from) / g.rate;
    if (e < d) return g.from + e * g.rate;
    e -= d;
  }
  return segs.length ? segs[segs.length - 1].to : 0;
}
export function pathDuration(segs: Seg[]) { let d = 0; for (const g of segs) d += (g.to - g.from) / g.rate; return d; }
/** Scene time for a plan at clock `now`. */
export function planScene(p: Plan, now: number) {
  'worklet';
  if (p.mode === 'demo') return wrap(now - p.anchor);
  if (p.mode === 'path') return pathAt(p.segs, now - p.startedAt);
  return p.s;
}
/** Composer opacity in quiet mode. */
export function planQuietPanel(p: Plan, now: number) {
  'worklet';
  const u = Math.min(1, Math.max(0, (now - p.qStart) / QUIET_FADE));
  return p.qFrom + (p.qTo - p.qFrom) * u;
}

const atRest = (s: number) => s < T.tap || s >= T.rest;
/** Path from scene time s to the open composer. */
function toHold(s: number, rate: number): Seg[] {
  if (s >= T.hold && s <= T.close) return [{ from: T.hold, to: T.hold, rate: 1 }];
  if (s > T.close && s < T.rest) return [{ from: s, to: T.rest, rate: M.closeRush }, { from: T.tap, to: T.hold, rate }];
  if (atRest(s)) return [{ from: T.tap, to: T.hold, rate }];
  return [{ from: s, to: T.hold, rate }];
}
/** Path from scene time s back to the rest frame. */
function toRest(s: number): Seg[] {
  if (atRest(s)) return [{ from: 0, to: 0, rate: 1 }];
  if (s < T.hold) return [{ from: s, to: T.hold, rate: M.closeRush }, { from: T.close, to: T.rest, rate: 1 }];
  if (s <= T.close) return [{ from: T.close, to: T.rest, rate: 1 }];
  return [{ from: s, to: T.rest, rate: 1 }];
}

export class PitStopReviewController {
  autoPlay: boolean; owner: Owner; anchor: number; restartAt: number | null = null; suspendedAt: number | null = null;
  rating = 0; note = ''; editorOwned = false; destinationOwned = false; sharing = false; keyboardPresented = false;
  foreground = true; homeSelected = true; feedAtTop = true; quiet = false;
  private path: { segs: Seg[]; startedAt: number } | null = null;
  private still = 0;
  private draftPresentation = false;
  private fade = { from: 0, to: 0, start: -1 };

  constructor(autoPlay: boolean, anchor = 0) { this.autoPlay = autoPlay; this.owner = autoPlay ? 'demo' : 'user'; this.anchor = anchor; }

  get eligible() { return this.autoPlay && this.foreground && this.homeSelected && this.feedAtTop && !this.quiet && !this.editorOwned && !this.destinationOwned && !this.sharing && !this.keyboardPresented; }
  get hasOwnedFlow() { return this.editorOwned || this.destinationOwned; }
  private logical(now: number) { return this.suspendedAt ?? now; }
  private shouldDemo(time: number) { return this.eligible && (this.restartAt !== null ? time >= this.restartAt : this.owner === 'demo'); }
  private showsDraft(now: number) { return this.draftPresentation && !this.shouldDemo(this.logical(now)); }
  displayRating(now: number) { return this.showsDraft(now) ? this.rating : 0; }
  displayNote(now: number) { return this.showsDraft(now) ? this.note : ''; }

  /** Scene time now. */
  scene(now: number) {
    const time = this.logical(now);
    if (this.shouldDemo(time)) return wrap(time - (this.restartAt ?? this.anchor));
    if (this.path) return pathAt(this.path.segs, time - this.path.startedAt);
    return this.still;
  }
  private quietPanel(now: number) { const u = Math.min(1, Math.max(0, (this.logical(now) - this.fade.start) / QUIET_FADE)); return this.fade.from + (this.fade.to - this.fade.from) * u; }
  panel(now: number) { return this.quiet ? this.quietPanel(now) : panelAt(this.scene(now)); }
  panelVisible(now: number) { return this.editorOwned || this.panel(now) > 0.001; }

  plan(now: number): Plan {
    const time = this.logical(now);
    const base = { anchor: 0, startedAt: 0, segs: [] as Seg[], s: 0, quiet: this.quiet, qFrom: this.fade.from, qTo: this.fade.to, qStart: this.fade.start };
    if (this.quiet) return { ...base, mode: 'static', s: 0 };
    if (this.shouldDemo(time)) return { ...base, mode: 'demo', anchor: this.restartAt ?? this.anchor };
    if (this.path && !this.pathDone(time)) return { ...base, mode: 'path', startedAt: this.path.startedAt, segs: this.path.segs };
    return { ...base, mode: 'static', s: this.scene(now) };
  }
  cadence(now: number): 'stopped' | 'animation' {
    const time = this.logical(now);
    if (this.suspendedAt !== null) return 'stopped';
    if (this.quiet) return time < this.fade.start + QUIET_FADE ? 'animation' : 'stopped';
    if (this.path && !this.pathDone(time)) return 'animation';
    if (!this.shouldDemo(time)) return 'stopped';
    const p = wrap(time - (this.restartAt ?? this.anchor));
    return (p >= T.press && p < T.hold) || (p >= T.close && p < T.rest) ? 'animation' : 'stopped';
  }
  nextWakeDelay(now: number): number | null {
    const time = this.logical(now);
    if (this.suspendedAt !== null) return null;
    if (this.quiet && time < this.fade.start + QUIET_FADE) return this.fade.start + QUIET_FADE - time;
    if (this.path && !this.pathDone(time)) return this.path.startedAt + pathDuration(this.path.segs) - time;
    if (this.restartAt !== null && this.eligible && time < this.restartAt) return this.restartAt - time + T.press;
    if (!this.shouldDemo(time)) return null;
    const p = wrap(time - (this.restartAt ?? this.anchor));
    if (p < T.press) return T.press - p;
    if (p >= T.hold && p < T.close) return T.close - p;
    if (p >= T.rest) return LOOP - p + T.press;
    return null;
  }

  setAutoPlay(enabled: boolean, now: number) {
    if (this.autoPlay === enabled) return;
    this.normalize(now); this.autoPlay = enabled;
    if (!enabled) { this.restartAt = null; if (this.owner === 'demo') { this.owner = 'user'; this.settle(0); } }
    else if (this.eligible) this.scheduleRestart(this.logical(now));
  }
  openEditor(now: number) {
    this.normalize(now);
    if (this.editorOwned) return;
    const time = this.logical(now), s = this.scene(now), panelNow = this.panel(now);
    this.take(); this.editorOwned = true; this.draftPresentation = true;
    if (this.quiet) { this.settle(0); this.fadeTo(panelNow, 1, time); return; }
    this.begin(toHold(s, M.manualRate), time);
  }
  closeEditor(now: number) {
    this.normalize(now);
    if (!this.editorOwned && this.panel(now) <= 0.001) return;
    const time = this.logical(now), s = this.scene(now), panelNow = this.panel(now);
    this.draftPresentation = this.showsDraft(now);
    this.editorOwned = false; this.keyboardPresented = false; this.owner = 'user';
    if (this.quiet) { this.settle(0); this.fadeTo(panelNow, 0, time); this.restartAt = null; return; }
    const segs = toRest(s);
    this.begin(segs, time);
    this.restartAt = this.eligible ? time + pathDuration(segs) + RESTART_DELAY : null;
  }
  setRating(value: number, now: number) { this.normalize(now); this.capture(now); this.rating = Math.min(5, Math.max(0, value)); this.rush(now); }
  setNote(value: string, now: number) { this.normalize(now); this.capture(now); this.note = limitNote(value); this.rush(now); }
  setDestinationOwned(owned: boolean, now: number) {
    this.normalize(now);
    const time = this.logical(now);
    this.destinationOwned = owned; this.owner = 'user'; this.settle(0);
    this.restartAt = !owned && this.eligible ? time + RESTART_DELAY : null;
  }
  setHomeSelected(selected: boolean, now: number) { if (this.homeSelected === selected) return; this.normalize(now); this.homeSelected = selected; this.updateEligibility(now); }
  setFeedAtTop(top: boolean, now: number) { if (this.feedAtTop === top) return; this.normalize(now); this.feedAtTop = top; this.updateEligibility(now); }
  setQuiet(enabled: boolean, now: number) {
    if (this.quiet === enabled) return;
    this.normalize(now);
    const time = this.logical(now), panelNow = this.panel(now);
    this.draftPresentation = this.showsDraft(now);
    this.quiet = enabled; this.owner = 'user';
    this.settle(this.editorOwned ? T.hold : 0);
    this.fadeTo(enabled ? panelNow : this.editorOwned ? 1 : 0, this.editorOwned ? 1 : 0, time);
    this.restartAt = !enabled && this.eligible ? time + RESTART_DELAY : null;
  }
  beginShare(now: number) { this.normalize(now); this.capture(now); if (!this.editorOwned || this.rating === 0) return false; this.sharing = true; this.updateSuspension(now); return true; }
  setForeground(active: boolean, now: number) { if (this.foreground === active) return; if (!active) this.preserve(now); this.foreground = active; this.updateSuspension(now); }
  setSharing(active: boolean, now: number) { if (this.sharing === active) return; if (active) this.preserve(now); this.sharing = active; this.updateSuspension(now); }
  setKeyboardPresented(active: boolean, now: number) {
    this.normalize(now);
    if (active) this.capture(now);
    if (active && this.editorOwned) this.settle(this.quiet ? 0 : T.hold);
    this.keyboardPresented = active; this.updateSuspension(now);
  }

  private pathDone(time: number) { return !this.path || time >= this.path.startedAt + pathDuration(this.path.segs); }
  private begin(segs: Seg[], time: number) { this.path = { segs, startedAt: time }; }
  private settle(s: number) { this.path = null; this.still = s; }
  private take() { this.owner = 'user'; this.destinationOwned = false; this.restartAt = null; }
  private fadeTo(from: number, to: number, time: number) { this.fade = { from, to, start: time }; }
  private normalize(now: number) {
    const time = this.logical(now);
    if (this.path && this.pathDone(time)) this.settle(pathAt(this.path.segs, Infinity));
    if (this.restartAt !== null && this.eligible && time >= this.restartAt) { this.owner = 'demo'; this.anchor = this.restartAt; this.restartAt = null; this.settle(0); }
  }
  /** A touch on the composer while the demo shows it takes it over (finishing the opening quickly). */
  private capture(now: number) {
    if (this.editorOwned) return;
    if (this.panel(now) <= 0.001) return;
    const s = this.scene(now);
    this.take(); this.editorOwned = true; this.draftPresentation = true;
    if (this.quiet) { this.settle(0); this.fadeTo(1, 1, this.logical(now)); return; }
    if (s >= T.hold && s <= T.close) this.settle(T.hold);
    else if (s > T.close) this.settle(T.hold);
    else this.settle(s);
  }
  /** Input while the opening still plays finishes it at once (fast-forward, no jump). */
  private rush(now: number) {
    if (!this.editorOwned || this.quiet) return;
    const time = this.logical(now), s = this.scene(now);
    this.restartAt = null; this.owner = 'user';
    if (s < T.hold) this.begin([{ from: Math.max(s, T.tap), to: T.hold, rate: M.rush }], time);
  }
  private updateEligibility(now: number) {
    const time = this.logical(now);
    if (this.eligible) { if (this.owner !== 'demo' && !this.hasOwnedFlow) this.scheduleRestart(time); }
    else { this.restartAt = null; if (this.owner === 'demo') { this.owner = 'user'; this.settle(0); } }
  }
  private scheduleRestart(time: number) { this.owner = 'user'; this.settle(0); this.restartAt = time + RESTART_DELAY; }
  private preserve(now: number) {
    this.normalize(now);
    if (this.suspendedAt !== null) return;
    const visible = this.showsDraft(now);
    if (this.owner === 'demo' && !this.editorOwned) { this.draftPresentation = visible; return; }
    this.draftPresentation = visible;
  }
  private updateSuspension(now: number) {
    const should = !this.foreground || this.sharing || this.keyboardPresented;
    if (should) { if (this.suspendedAt === null) this.suspendedAt = now; return; }
    if (this.suspendedAt === null) return;
    const pause = Math.max(0, now - this.suspendedAt);
    this.suspendedAt = null;
    this.anchor += pause;
    if (this.restartAt !== null) this.restartAt += pause;
    if (this.path) this.path.startedAt += pause;
    this.fade.start += pause;
    if (this.eligible && this.owner === 'user' && !this.hasOwnedFlow && this.restartAt === null) this.scheduleRestart(now);
  }
}
