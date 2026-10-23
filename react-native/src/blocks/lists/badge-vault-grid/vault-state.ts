import { BADGES, type BadgePose, type FloodPose } from './vault-contract';
import { cellHome, type VaultLayout } from './vault-layout';
import { type Spin, type Spring } from './vault-motion';

export type CellState = {
  have: number; earned: boolean; pressAt: number; pressFrom: number; pressTo: number;
  fingerX: number; fingerY: number; rattleAt: number; progressAt: number; progressFrom: number;
  unlockAt: number; popAt: number; popGain: number; unlocked: boolean;
};
export type CellVisual = BadgePose & { flash: number; progress: number; reveal: number; shake: number };
export type HeroVisual = {
  index: number; pose: BadgePose; flood: FloodPose; recede: number; alpha: number;
  age: number; details: number; nameX: number; nameY: number; nameScale: number;
};
export type VaultEvent = { kind: 'open' | 'close' | 'progress' | 'reset' | 'selection' | 'medium' | 'light' | 'warning' | 'success'; index: number };
export type Engine = {
  poses: CellVisual[]; launchPose: BadgePose | null; now: number; previousScroll: number; velocity: number; followers: Spring[]; cells: CellState[];
  selected: number; phase: 'rest' | 'opening' | 'held' | 'closing'; phaseAt: number;
  pressPose: { r: number; tiltX: number; tiltY: number }; lift: number; recedeFrom: number;
  closePose: HeroVisual | null; turn: Spin; turnMode: 'launch' | 'free' | 'drag' | 'slow';
  turnAt: number; turnFrom: number; turnTo: number; drag: Spring; dragging: boolean;
  landed: boolean; ripple: { at: number; x: number; y: number; gain: number };
  bandAt: number; lastIdleBand: number; pillAt: number; pillDown: boolean; pillFrom: number;
  demo: { time: number; cursor: number; enabled: boolean; started: boolean; readyAt: number;
    touchAt: number; touching: boolean; returningAt: number; scrollAt: number; scrollFrom: number;
    scrollTo: number; scrollDuration: number };
  events: VaultEvent[];
};
export function initialCell(index: number): CellState {
  'worklet';
  const badge = BADGES[index];
  return { have: badge.goal?.have ?? 0, earned: badge.earned !== null, pressAt: -10000,
    pressFrom: 0, pressTo: 0, fingerX: 0, fingerY: 0, rattleAt: -10000,
    progressAt: -10000, progressFrom: badge.goal ? badge.goal.have / badge.goal.need : 1,
    unlockAt: -1, popAt: -10000, popGain: 0, unlocked: false };
}
export function initialEngine(autoPlay: boolean): Engine {
  'worklet';
  const cells: CellState[] = [];
  for (let i = 0; i < BADGES.length; i++) cells.push(initialCell(i));
  return { poses: [], launchPose: null, now: 0, previousScroll: 0, velocity: 0, followers: [{ x: 0, v: 0 }, { x: 0, v: 0 }, { x: 0, v: 0 }],
    cells, selected: -1, phase: 'rest', phaseAt: 0, pressPose: { r: 1, tiltX: 0, tiltY: 0 },
    lift: 0, recedeFrom: 0, closePose: null, turn: { x: 0, v: 0, target: 0, friction: 3.8, detent: false, snapped: true },
    turnMode: 'launch', turnAt: 0, turnFrom: 0, turnTo: 0, drag: { x: 0, v: 0 }, dragging: false,
    landed: false, ripple: { at: -10000, x: 0, y: 0, gain: 0 }, bandAt: -10000, lastIdleBand: 0,
    pillAt: -10000, pillDown: false, pillFrom: 0,
    demo: { time: 0, cursor: 0, enabled: autoPlay, started: false, readyAt: -1,
      touchAt: -1, touching: false, returningAt: -1, scrollAt: -1, scrollFrom: 0, scrollTo: 0, scrollDuration: 1500 }, events: [] };
}
export function blankPose(): BadgePose {
  'worklet';
  return { cx: 0, cy: 0, r: 1, tiltX: 0, tiltY: 0, spin: 0, roll: 0, envX: 0, envY: 0,
    sheen: -1, fill: 1, wave: 0, time: 0, lift: 0, present: 0, socket: 0 };
}
export function initialVisual(layout: VaultLayout, index: number): CellVisual {
  'worklet';
  const col = layout.columns[index % 3];
  return { ...blankPose(), cx: Math.round(col.width) / 2, cy: layout.badgeY, r: layout.radius,
    present: 1, socket: 1, fill: BADGES[index].earned ? 1 : 0, flash: 0,
    progress: BADGES[index].goal ? BADGES[index].goal!.have / BADGES[index].goal!.need : 1,
    reveal: BADGES[index].earned ? 1 : 0, shake: 0 };
}
export function initialHero(layout: VaultLayout): HeroVisual {
  'worklet';
  const home = cellHome(layout, 0, 0, 0);
  return { index: -1, pose: blankPose(), alpha: 0, recede: 0, age: 0, details: 0,
    nameX: home.x, nameY: home.nameY, nameScale: 13 / layout.titleSize,
    flood: { w: layout.width, h: layout.height, ox: 0, oy: 0, cx: 0, cy: 0, r: 1,
      spread: 0, power: 0, rays: 0, since: -1, time: 0 } };
}

/** Give each UI transaction its own mutable working copy; published poses stay immutable. */
export function copyEngine(source: Engine): Engine {
  'worklet';
  const cells: CellState[] = [];
  const followers: Spring[] = [];
  for (let i = 0; i < source.cells.length; i++) cells.push({ ...source.cells[i] });
  for (let i = 0; i < source.followers.length; i++) followers.push({ ...source.followers[i] });
  return { ...source, cells, followers, poses: [...source.poses], events: [...source.events],
    turn: { ...source.turn }, drag: { ...source.drag }, demo: { ...source.demo }, ripple: { ...source.ripple } };
}
