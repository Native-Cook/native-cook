import { authored, clamp, coalesced, manualBaked, mixed, recovering, rest, smooth, stamped, turn, type Field, type Frame, type Point } from './motion';

export const COLORS = [0xC7BDA0, 0xA88444, 0x7C253E, 0x073C68, 0xD3A5AD, 0x438D97, 0x41485C, 0x737A43];
export const NAMES = ['Oat', 'Ochre', 'Claret', 'Midnight', 'Blush', 'Lagoon', 'Slate', 'Moss'];
export type Tint = [number, number, number];
export type Session = { id: number; from: Frame; target: Field | null; stamp: number; settled: boolean; lastPoint: Point; samplePoint: Point; lastDate: number; angle: number; samples: Point[] };
type Event = { stamp: number; session: Session };
type Control = { stamp: number; frame: Frame };
type Palette = { stamp: number; from: Tint; to: Tint };
export type Motion = { now: number; epoch: number; visible: boolean; session: Session | null; event: Event | null; control: Control | null; palette: Palette | null; frozenTint: Tint | null; baseTint: Tint; controlHeld: boolean; selected: number; yards: number; liked: boolean; addedAt: number | null; lastAngle: number; nextFieldID: number; ignored: number[] };
export function initial(): Motion { 'worklet'; return { now: 0, epoch: 0, visible: true, session: null, event: null, control: null, palette: null, frozenTint: null, baseTint: [1, 1, 1], controlHeld: false, selected: 3, yards: 2, liked: false, addedAt: null, lastAngle: Math.atan2(-.6, .8), nextFieldID: 1, ignored: [] }; }
export function paletteTint(index: number): Tint { 'worklet'; if (index === 3) return [1, 1, 1]; const c = COLORS[index]; return [((c >>> 16) & 255) / 255 / .028, ((c >>> 8) & 255) / 255 / .235, (c & 255) / 255 / .408]; }
function sameTint(a: Tint, b: Tint) { 'worklet'; return a[0] === b[0] && a[1] === b[1] && a[2] === b[2]; }
export function tintAt(m: Motion): Tint {
  'worklet'; if (m.frozenTint) return m.frozenTint;
  if (m.palette) { const u = smooth(Math.max(0, m.now - m.palette.stamp) / .18), a = m.palette.from, b = m.palette.to; return [a[0] + (b[0] - a[0]) * u, a[1] + (b[1] - a[1]) * u, a[2] + (b[2] - a[2]) * u]; }
  return m.baseTint;
}
function freezeTint(m: Motion, visible: Tint): Motion { 'worklet'; return { ...m, frozenTint: visible, baseTint: visible, palette: null }; }
function resumeTint(m: Motion, visible: Tint, target: Tint): Motion { 'worklet'; return { ...m, frozenTint: null, baseTint: target, palette: sameTint(visible, target) ? null : { stamp: m.now, from: visible, to: target } }; }
export function held(s: Session, now: number, reduced: boolean): Frame { 'worklet'; if (!s.target) return s.from; return mixed(s.from, s.target, reduced || s.settled ? 1 : smooth(Math.max(0, now - s.stamp) / .09)); }
export function drive(m: Motion, autoPlay: boolean, reduced: boolean, preview?: number): Frame {
  'worklet'; if (preview !== undefined) return reduced ? rest() : authored(preview);
  if (m.session) return held(m.session, m.now, reduced);
  if (m.control) { const age = Math.max(0, m.now - m.control.stamp); if (m.controlHeld || age < 2) return m.control.frame; if (!reduced && age < 2.6) return recovering(m.control.frame, age - 2); return autoPlay && !reduced ? authored(age - (reduced ? 2 : 2.6)) : rest(); }
  if (m.event) { const age = Math.max(0, m.now - m.event.stamp); if (age < 2) return held(m.event.session, m.now, reduced); if (age < 2.6 && !reduced) return recovering(held(m.event.session, m.event.stamp + 2, reduced), age - 2); return autoPlay && !reduced ? authored(age - 2.6) : rest(); }
  return autoPlay && !reduced ? authored(Math.max(0, m.now - m.epoch)) : rest();
}
function tag(m: Motion, field: Field): Field { 'worklet'; const id = m.nextFieldID++; return { ...field, id }; }
function captureCoalesced(m: Motion, from: Frame): Frame { 'worklet'; const f = coalesced(from); return f === from ? from : { ...f, fields: f.fields.map(field => tag(m, field)) }; }
export function begin(old: Motion, id: number, point: Point, now: number, captured: Frame, visibleTint: Tint): Motion {
  'worklet'; if (!old.visible || old.ignored.includes(id)) return old;
  if (old.session) return { ...old, ignored: [...old.ignored, id] };
  let m: Motion = { ...old, now, control: null, event: null }; m = freezeTint(m, visibleTint);
  const from = captureCoalesced(m, captured), base = manualBaked(from), target = tag(m, stamped(base, point, point, m.lastAngle, 44, from));
  return { ...m, session: { id, from, target, stamp: now, settled: false, lastPoint: point, samplePoint: point, lastDate: now, angle: m.lastAngle, samples: [point] } };
}
export function move(old: Motion, id: number, point: Point, now: number, reduced: boolean): Motion {
  'worklet'; const current = old.session; if (!current || current.id !== id || (point.x === current.lastPoint.x && point.y === current.lastPoint.y)) return old;
  const m = { ...old, now }, s = { ...current }, dx = point.x - s.lastPoint.x, dy = point.y - s.lastPoint.y, dt = Math.max(.001, now - s.lastDate), speed = Math.hypot(dx, dy) / dt;
  s.angle = turn(s.angle, Math.atan2(dy, dx), 1 - Math.exp(-dt / .045)); m.lastAngle = s.angle; s.lastPoint = point; s.lastDate = now;
  if (Math.hypot(point.x - s.samplePoint.x, point.y - s.samplePoint.y) >= 4) {
    const f = held(s, now, reduced), base = tag(m, manualBaked(f)); s.from = { ...rest(), time: f.time, authorWeight: f.authorWeight * f.strength, fields: [base], weights: [1] };
    s.target = tag(m, stamped(base, s.samplePoint, point, s.angle, 52 + Math.min(14, speed * .02), s.from)); s.samplePoint = point; s.stamp = now; s.settled = false; s.samples = [...s.samples, point];
    if (s.samples.length > 64) s.samples = s.samples.filter((_, i) => i % 2 === 0 || i === s.samples.length - 1);
  } return { ...m, session: s };
}
export function end(old: Motion, id: number, point: Point | null, now: number, reduced: boolean, cancelled: boolean, captured: Frame, visibleTint: Tint): Motion {
  'worklet'; if (!old.session || old.session.id !== id) return { ...old, ignored: old.ignored.filter(i => i !== id) };
  let m = !cancelled && point ? move(old, id, point, now, reduced) : old; const session = m.session!; m = { ...m, now, session: null };
  if (m.controlHeld) return { ...m, control: { stamp: now, frame: captured }, event: null };
  return resumeTint({ ...m, event: { stamp: now, session } }, m.frozenTint ?? visibleTint, paletteTint(m.selected));
}
export function captureControl(old: Motion, now: number, captured: Frame, visibleTint: Tint): Motion {
  'worklet'; let m: Motion = { ...old, now, event: null };
  if (m.session) { m = { ...m, session: { ...m.session, from: captured, target: null, stamp: now, settled: true }, control: null }; return freezeTint(m, visibleTint); }
  return resumeTint({ ...m, control: { stamp: now, frame: captured } }, visibleTint, paletteTint(m.selected));
}
export function controlPressed(old: Motion, pressed: boolean, now: number, captured: Frame, visibleTint: Tint): Motion {
  'worklet'; if (!old.visible) return old; let m = captureControl({ ...old, controlHeld: pressed }, now, captured, visibleTint); if (pressed) m = freezeTint(m, visibleTint); return m;
}
export function choose(old: Motion, index: number, now: number, captured: Frame, visibleTint: Tint): Motion {
  'worklet'; let m = captureControl(old, now, captured, visibleTint); if (m.selected === index) return m; m = { ...m, selected: index }; return m.session ? m : resumeTint(m, visibleTint, paletteTint(index));
}
export function save(old: Motion, now: number, captured: Frame, visibleTint: Tint): Motion { 'worklet'; return { ...captureControl(old, now, captured, visibleTint), addedAt: now }; }
export function accessibleBrush(old: Motion, up: boolean, now: number, captured: Frame, visibleTint: Tint): Motion {
  'worklet'; let m: Motion = { ...old, now, control: null, session: null }; const from = captureCoalesced(m, captured), p = { x: 153.5, y: 137 };
  m = resumeTint(m, visibleTint, paletteTint(m.selected)); const base = manualBaked(from), target = tag(m, stamped(base, { x: p.x - 45, y: p.y + 30 }, { x: p.x + 45, y: p.y - 30 }, Math.atan2(-.6, .8) + (up ? 0 : Math.PI), 58, from));
  return { ...m, event: { stamp: now, session: { id: -1, from, target, stamp: now, settled: false, lastPoint: p, samplePoint: p, lastDate: now, angle: 0, samples: [] } } };
}
export function confirmation(m: Motion) { 'worklet'; return m.addedAt !== null && Math.max(0, m.now - m.addedAt) < 1.2; }
export function complete(old: Motion, now: number, reduced: boolean): Motion {
  'worklet'; let m = { ...old, now };
  if (m.palette && now - m.palette.stamp >= .18) m.palette = null;
  if (m.session && !m.session.settled && now - m.session.stamp >= .095) m.session = { ...m.session, settled: true };
  if (m.event && !m.event.session.settled && now - m.event.session.stamp >= .095) m.event = { ...m.event, session: { ...m.event.session, settled: true } };
  const duration = reduced ? 2 : 2.6;
  if (m.control && !m.controlHeld && now - m.control.stamp >= duration) { m.epoch = m.control.stamp + duration; m.control = null; }
  if (m.event && now - m.event.stamp >= duration) { m.epoch = m.event.stamp + duration; m.event = null; }
  if (m.addedAt !== null && now - m.addedAt >= 1.2) m.addedAt = null; return m;
}
export function needsClock(m: Motion, autoPlay: boolean, reduced: boolean, preview?: number) {
  'worklet'; if (!m.visible || preview !== undefined) return false; if (m.palette) return true; if (m.controlHeld) return false;
  if (m.session) return !reduced && !m.session.settled;
  if (m.event && !reduced && !m.event.session.settled && m.now - m.event.session.stamp < .095) return true;
  const stamp = m.control?.stamp ?? m.event?.stamp; if (stamp !== undefined) return !reduced && m.now - stamp >= 2 && m.now - stamp < 2.6;
  return autoPlay && !reduced;
}
export function reset(old: Motion, now: number, visible: boolean): Motion { 'worklet'; return { ...initial(), now, epoch: now, visible, selected: old.selected, yards: old.yards, liked: old.liked, nextFieldID: old.nextFieldID, baseTint: paletteTint(old.selected) }; }
export function currentFields(m: Motion): Field[] {
  'worklet'; const s = m.session ?? m.event?.session; const fields = s ? [...s.from.fields, ...(s.target ? [s.target] : [])] : m.control?.frame.fields ?? [], out: Field[] = [];
  for (const f of fields) if (!out.some(existing => existing.id === f.id)) out.push(f); return out.slice(0, 3);
}
const TOUCH_POINTS = (() => {
  const p: Point[] = [{ x: 30, y: 45 }], quad = (a: Point, b: Point, c: Point) => { for (let i = 1; i <= 32; i++) { const t = i / 32, u = 1 - t; p.push({ x: u * u * a.x + 2 * u * t * b.x + t * t * c.x, y: u * u * a.y + 2 * u * t * b.y + t * t * c.y }); } };
  for (let i = 1; i <= 32; i++) { const t = i / 32, u = 1 - t; p.push({ x: u * u * u * 30 + 3 * u * u * t * 105 + 3 * u * t * t * 196 + t * t * t * 244, y: u * u * u * 45 + 3 * u * u * t * 42 + 3 * u * t * t * 30 + t * t * t * 22 }); }
  quad({ x: 244, y: 22 }, { x: 260, y: 116 }, { x: 271, y: 223 }); quad({ x: 271, y: 223 }, { x: 159, y: 244 }, { x: 61, y: 258 }); quad({ x: 61, y: 258 }, { x: 28, y: 237 }, { x: 20, y: 217 }); quad({ x: 20, y: 217 }, { x: 22, y: 112 }, { x: 30, y: 45 }); return p;
})();
export function touchHit(p: Point) {
  'worklet'; let inside = false;
  for (let i = 0, j = TOUCH_POINTS.length - 1; i < TOUCH_POINTS.length; j = i++) { const a = TOUCH_POINTS[i], b = TOUCH_POINTS[j]; if (((a.y > p.y) !== (b.y > p.y)) && p.x < (b.x - a.x) * (p.y - a.y) / (b.y - a.y) + a.x) inside = !inside;
    const dx = b.x - a.x, dy = b.y - a.y, length = dx * dx + dy * dy, u = length > 0 ? clamp(((p.x - a.x) * dx + (p.y - a.y) * dy) / length) : 0; if (Math.hypot(p.x - a.x - dx * u, p.y - a.y - dy * u) <= 12) return true;
  } return inside;
}
