import { authored, cancelled, collecting, dragged, ease, recovering, reduced, released, rest, zeroPose, type Frame, type Point, type Support } from './motion';

export type Session = { id: number; generation: number; base: Frame; anchor: Point; lastPoint: Point; lastDate: number; supportDate: number; pointer: Point; translation: Point; yawSupport: Support; pitchSupport: Support; supportActive: boolean; changed: boolean; terminal: Frame | null; hapticView: number; hapticArmed: boolean };
export type Event = { id: number; stamp: number; from: Frame; kind: 'release' | 'cancel' | 'collect' | 'reduced'; view: number; base: Frame; collection: boolean; phase: 0 | 1 | 2 };
export type Motion = { now: number; epoch: number; visible: boolean; generation: number; session: Session | null; event: Event | null; ignored: number[]; reducedBase: Frame; reducedTargetView: number };

export function initialMotion(): Motion {
  'worklet'; return { now: 0, epoch: 0, visible: true, generation: 0, session: null, event: null, ignored: [], reducedBase: { pose: zeroPose(), angularVelocity: zeroPose(), explicitViewB: 0, collectionMix: 0 }, reducedTargetView: 0 };
}
export function settleDuration(event: Event) { 'worklet'; return event.kind === 'release' ? 0.75 : event.kind === 'cancel' ? 0.24 : 0.18; }
export function hasCollection(event: Event) { 'worklet'; return event.kind === 'reduced' && (event.collection || event.from.collectionMix > 0); }
export function eventFrame(event: Event, age: number): Frame {
  'worklet';
  if (event.kind === 'release') return released(event.from, event.view, age);
  if (event.kind === 'cancel') return cancelled(event.from, event.base, age);
  if (event.kind === 'collect') return collecting(event.from, age);
  const result = reduced(event.from, event.view, age);
  if (event.collection) result.collectionMix = event.from.collectionMix + (1 - event.from.collectionMix) * ease(age / 0.18);
  if (age >= 2) result.collectionMix *= 1 - ease((age - 2) / 0.18);
  return result;
}
export function heldFrame(session: Session, now: number): Frame {
  'worklet';
  if (session.terminal && !session.supportActive) return session.terminal;
  if (!session.changed) return { ...session.base, angularVelocity: zeroPose() };
  return dragged(session.base, session.translation, session.yawSupport, session.pitchSupport, Math.max(0, now - session.supportDate));
}
export function drive(m: Motion, autoPlay: boolean, reduceMotion: boolean, previewTime?: number): Frame {
  'worklet';
  const event = m.event, age = event ? (event.phase === 1 ? settleDuration(event) : Math.max(0, m.now - event.stamp)) : 0;
  if (reduceMotion) return event ? eventFrame(event, age) : m.reducedBase;
  if (previewTime !== undefined) return authored(previewTime);
  if (m.session) return heldFrame(m.session, m.now);
  if (event) {
    if (age < 2) return eventFrame(event, age);
    if (age < 2.65) return recovering(eventFrame(event, 2), age - 2);
    return autoPlay ? authored(age - 2.65) : rest();
  }
  return autoPlay ? authored(Math.max(0, m.now - m.epoch)) : rest();
}
export function completeEvent(m: Motion, id: number, phase: 1 | 2 | 3, now: number): Motion {
  'worklet';
  const event = m.event;
  if (!event || event.id !== id || !m.visible) return m;
  if (phase === 1) {
    if (event.phase !== 0) return m;
    if (event.kind === 'reduced' && !hasCollection(event)) return { ...m, now, reducedBase: eventFrame(event, 0.18), event: null };
    return { ...m, now, event: { ...event, phase: 1 } };
  }
  if (phase === 2) return { ...m, now, event: { ...event, phase: 2 } };
  const end = event.kind === 'reduced' ? 2.18 : 2.65;
  return { ...m, now, reducedBase: event.kind === 'reduced' ? eventFrame(event, end) : m.reducedBase, epoch: event.stamp + end, event: null };
}
export function resetMotion(m: Motion, now: number, visible: boolean, reduceMotion: boolean, resetView = false): Motion {
  'worklet';
  const target = resetView ? 0 : m.reducedTargetView;
  return { ...m, now, epoch: now, visible, generation: m.generation + 1, session: null, event: null, ignored: [], reducedTargetView: target,
    reducedBase: reduceMotion ? { pose: zeroPose(), angularVelocity: zeroPose(), explicitViewB: target, collectionMix: 0 } : { ...m.reducedBase, collectionMix: 0 } };
}
