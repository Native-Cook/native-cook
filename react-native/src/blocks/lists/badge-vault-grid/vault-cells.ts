import { BADGES } from './vault-contract';
import { cellHome, type VaultLayout } from './vault-layout';
import { bezier, clamp, ease, spring, springAt } from './vault-motion';
import { emit, pressAmount, ripple } from './vault-actions';
import { blankPose, type CellVisual, type Engine } from './vault-state';

export function advanceFollowers(e: Engine, dt: number, scrollY: number, reduced: boolean) {
  'worklet';
  // Content travels opposite to scrollY; its leading edge leans toward the viewer.
  e.velocity = dt > 0 ? (e.previousScroll - scrollY) / dt : 0;
  e.previousScroll = scrollY;
  const target = reduced ? 0 : clamp(e.velocity / 2400, -1, 1) * 0.42;
  e.followers[0] = spring(e.followers[0], target, Math.min(dt, 0.064), 260, 20);
  e.followers[1] = spring(e.followers[1], target, Math.min(dt, 0.064), 170, 14);
  e.followers[2] = spring(e.followers[2], target, Math.min(dt, 0.064), 110, 10);
}
export function cellFrame(e: Engine, l: VaultLayout, index: number, scrollY: number, recede: number, reduced: boolean): CellVisual {
  'worklet';
  const c = e.cells[index]; const badge = BADGES[index];
  const col = l.columns[index % 3]; const home = cellHome(l, index, scrollY, recede);
  const press = pressAmount(e, index);
  const rattle = clamp((e.now - c.rattleAt) / 460);
  const shake = reduced ? 0 : 7 * Math.sin(5 * Math.PI * rattle) * Math.pow(1 - rattle, 1.5);
  const unlockClock = c.unlockAt >= 0 ? clamp((e.now - c.unlockAt) / (reduced ? 300 : 1150)) : 0;
  let fill = c.earned ? 1 : c.unlockAt >= 0 ? reduced ? unlockClock : bezier(unlockClock, 0.3, 0.7, 0.3, 1) : 0;
  if (unlockClock === 1 && !c.earned) {
    c.earned = true; c.unlocked = true; c.popAt = e.now; c.popGain = 0.14; fill = 1;
    ripple(e, l, index, scrollY, 1); emit(e, 'success', index); emit(e, 'progress');
  }
  const reveal = badge.earned ? 1 : c.earned ? clamp((e.now - c.popAt) / 260) : 0;
  const goalProgress = badge.goal ? c.have / badge.goal.need : 1;
  const progress = c.progressFrom + (goalProgress - c.progressFrom) * ease((e.now - c.progressAt) / 300);
  const h = ((index * 73 + 19) % 101) / 101;
  const swing = reduced ? 0 : e.followers[index % 3].x * (1 - h) + e.followers[(index + 1) % 3].x * h;
  let sheen = -1; let rippleX = 0; let rippleY = 0;
  if (!reduced) {
    const diagonal = (home.x + home.y) / 900;
    const sweep = ((e.now - e.bandAt) / 1000 - diagonal) / 0.7;
    if (sweep >= 0 && sweep <= 1) sheen = sweep;
    if (Math.abs(e.velocity) > 500) sheen = clamp(home.y / l.height);
    const dx = home.x - e.ripple.x; const dy = home.y - e.ripple.y;
    const range = Math.sqrt(dx * dx + dy * dy);
    const travelled = (e.now - e.ripple.at) * 1.1;
    const separation = range - travelled;
    if (Math.abs(separation) < 210 && travelled >= 0) {
      const strength = Math.exp(-0.5 * separation * separation / 4900) * Math.exp(-range / 1000) * e.ripple.gain;
      rippleX = -dy / Math.max(1, range) * 0.30 * strength;
      rippleY = dx / Math.max(1, range) * 0.30 * strength;
      sheen = clamp((travelled - range + 105) / 210);
    }
    if (c.popGain > 0 && e.now - c.popAt < 450) sheen = clamp((e.now - c.popAt) / 450);
  }
  const popAge = (e.now - c.popAt) / 1000;
  const pop = reduced || popAge > 1.5 ? 1 : 1 + c.popGain * (1 - springAt(popAge, c.popGain > 0 ? 300 : 320, c.popGain > 0 ? 14 : 18));
  const rising = !reduced && !c.earned && c.unlockAt >= 0 ? ease((e.now - c.unlockAt) / 300) : 0;
  const size = c.earned ? pop : 1 + 0.14 * rising;
  const lift = reduced ? 0 : c.earned && c.popGain > 0 ? 0.5 * clamp((pop - 1) / 0.14) : 0.5 * rising;
  return { ...blankPose(), cx: Math.round(col.width) / 2, cy: l.badgeY,
    r: Math.min(57 * l.k, l.radius * (1 - 0.05 * press) * size), lift,
    tiltX: swing - c.fingerY * 0.24 * press + rippleX,
    tiltY: c.fingerX * 0.24 * press + shake / 7 * 0.2 + rippleY, roll: shake / 7 * 0.06,
    envX: clamp((home.x - l.width / 2) / (l.width / 2), -1, 1),
    envY: clamp((home.y - l.height / 2) / (l.height / 2), -1, 1),
    sheen, fill, wave: reduced || c.unlockAt < 0 || e.now < c.unlockAt || fill >= 1 ? 0 : 1 - clamp((unlockClock - 0.85) / 0.15),
    time: c.unlockAt >= 0 && unlockClock < 1 ? unlockClock * 1.15 : 0,
    socket: 1, present: e.selected === index ? 0 : 1,
    flash: 1 - clamp((e.now - c.rattleAt) / 500), progress, reveal, shake };
}
const CELL_FIELDS: readonly (keyof CellVisual)[] = ['cx', 'cy', 'r', 'tiltX', 'tiltY', 'spin', 'roll', 'envX', 'envY', 'sheen', 'fill', 'wave', 'time', 'lift', 'present', 'socket', 'flash', 'progress', 'reveal', 'shake'];
export function sameCell(a: CellVisual, b: CellVisual) {
  'worklet';
  for (let i = 0; i < CELL_FIELDS.length; i++) if (a[CELL_FIELDS[i]] !== b[CELL_FIELDS[i]]) return false;
  return true;
}
