import { BADGES } from './vault-contract';
import { cellHome, type VaultLayout } from './vault-layout';
import { clamp, ease, flickSpin, springAt, TAU } from './vault-motion';
import { type Engine, type HeroVisual, initialCell } from './vault-state';

export type Action = 'press' | 'release' | 'open' | 'close' | 'log' | 'pill-in' | 'pill-out' | 'touch' | 'touch-end' | 'flick' | 'slow-turn' | 'reset';
export function pressAmount(e: Engine, index: number) {
  'worklet';
  const c = e.cells[index];
  const age = e.now - c.pressAt;
  const p = c.pressTo ? ease(age / 110) : springAt(age / 1000, 320, 22);
  return c.pressFrom + (c.pressTo - c.pressFrom) * p;
}
export function pillAmount(e: Engine) {
  'worklet';
  return e.pillFrom + ((e.pillDown ? 1 : 0) - e.pillFrom) * ease((e.now - e.pillAt) / 110);
}
export function emit(e: Engine, kind: Engine['events'][number]['kind'], index = -1) {
  'worklet'; e.events.push({ kind, index });
}
export function ripple(e: Engine, layout: VaultLayout, index: number, scrollY: number, gain: number) {
  'worklet';
  const home = cellHome(layout, index, scrollY, 0);
  e.ripple = { at: e.now, x: home.x, y: home.y, gain };
}
export function act(e: Engine, action: Action, index: number, x: number, y: number,
  layout: VaultLayout, scrollY: number, hero: HeroVisual, reduced: boolean) {
  'worklet';
  if (action === 'touch' || action === 'touch-end') {
    e.demo.touchAt = e.now; e.demo.touching = action === 'touch';
    e.demo.started = false; e.demo.returningAt = -1; e.demo.scrollAt = -1;
    return;
  }
  if (action === 'reset') {
    for (let i = 0; i < BADGES.length; i++) e.cells[i] = initialCell(i);
    emit(e, 'reset'); return;
  }
  if (action === 'pill-in' || action === 'pill-out') {
    e.pillFrom = pillAmount(e); e.pillDown = action === 'pill-in'; e.pillAt = e.now;
    if (e.pillDown) emit(e, 'light');
    return;
  }
  if (action === 'log') {
    let stagger = 0;
    for (let i = 0; i < BADGES.length; i++) {
      const c = e.cells[i]; const goal = BADGES[i].goal;
      if (c.earned || !goal || c.unlockAt >= 0) continue;
      c.progressFrom = c.have / goal.need; c.have = Math.min(goal.need, c.have + 1); c.progressAt = e.now;
      if (c.have === goal.need) { c.unlockAt = e.now + 300 + stagger; stagger += 250; }
    }
    emit(e, 'progress'); return;
  }
  if (action === 'close') {
    if (e.phase === 'rest' || e.phase === 'closing') return;
    e.closePose = hero; e.recedeFrom = hero.recede; e.phase = 'closing'; e.phaseAt = e.now;
    e.dragging = false; e.drag.v = y;
    e.turnTo = Math.round(hero.pose.spin / TAU) * TAU;
    return;
  }
  if (action === 'flick' && e.selected >= 0 && !reduced) {
    e.turn = flickSpin(hero.pose.spin, x, index === 1); e.turnMode = 'free'; return;
  }
  if (action === 'slow-turn' && e.selected >= 0 && !reduced) {
    e.turnMode = 'slow'; e.turnAt = e.now; e.turnFrom = hero.pose.spin;
    e.turnTo = Math.round((hero.pose.spin - Math.PI) / TAU) * TAU + Math.PI; return;
  }
  if (index < 0 || index >= BADGES.length || e.phase !== 'rest') return;
  const c = e.cells[index];
  if (action === 'press' || action === 'release') {
    c.pressFrom = pressAmount(e, index); c.pressTo = action === 'press' ? 1 : 0; c.pressAt = e.now;
    if (action === 'press') { c.fingerX = clamp(x, -1, 1); c.fingerY = clamp(y, -1, 1); }
    return;
  }
  if (action !== 'open') return;
  if (!c.earned) { c.rattleAt = e.now; emit(e, 'warning'); return; }
  const press = pressAmount(e, index);
  const source = e.poses[index];
  e.launchPose = source ?? null;
  e.pressPose = source ? { r: source.r / layout.radius, tiltX: source.tiltX, tiltY: source.tiltY } :
    { r: 1 - 0.05 * press, tiltX: -c.fingerY * 0.24 * press, tiltY: c.fingerX * 0.24 * press };
  e.selected = index; e.phase = 'opening'; e.phaseAt = e.now; e.landed = false;
  e.drag = { x: 0, v: 0 }; e.turnMode = 'launch';
  e.turn = { x: 0, v: 0, target: TAU, friction: 3.8, detent: false, snapped: true };
  ripple(e, layout, index, scrollY, 1); emit(e, 'open', index); emit(e, 'selection');
}
