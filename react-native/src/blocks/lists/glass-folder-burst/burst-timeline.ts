import type { BurstLayout } from './burst-layout';
import { BURST_EVENT, commandBurst, createBurstState, PHYSICS, STATE, stepBurst, type BurstState } from './burst-sim';
import { clamp01, smooth01 } from './row-pose';
import { bezier, sceneFolder, SPRING } from './scene-appearance';
export { sceneFolder, sceneOrb } from './scene-appearance';
export const SCENE_EVENT = { changed: 1, light: 2, medium: 4, selection: 8, soft: 16 } as const;
export const TIMELINE = { period: 10, folderPress: 0.86, open: 1.0, pressDuration: 0.14, select: 4.3,
  close: 6.2, resume: 3.5, dock: 0.56, lean: 0.12, launch: 0.56, eject: 0.76, suck: 0.09, rise: 24,
  discReturnStiffness: 25, discReturnDamping: 9, fadeOut: 0.12, fadeIn: 0.22 } as const;
export { SPRING } from './scene-appearance';
export type SceneInput = {
  serial: number;
  kind: 'folderDown' | 'folderUp' | 'toggle' | 'rowDown' | 'rowUp' | 'select' | 'touchStart' | 'touchEnd';
  index: number;
};
export type SceneState = {
  sim: BurstState;
  reduced: boolean;
  time: number;
  accumulator: number;
  loop: number;
  auto: boolean;
  idleTime: number;
  idleGain: number;
  paused: boolean;
  resumeAt: number;
  recovering: boolean;
  held: boolean;
  touching: boolean;
  serial: number;
  expanded: boolean;
  age: number;
  rowsStarted: boolean;
  launched: boolean;
  returning: boolean;
  home: boolean;
  dock: number;
  dockFrom: number;
  dockTarget: number;
  dockAge: number;
  /** Position/velocity pairs indexed by SPRING, never numeric slots. */
  springs: number[];
  pressFrom: number;
  pressAge: number;
  rowFrom: number;
  rowAge: number;
  pressed: number;
  selected: number;
  previous: number;
  selectionAge: number;
  selectedMix: number[];
  landed: number;
  events: number;
  sounded: boolean;
  shutSounded: boolean;
  fade: number;
  reducedFrom: number;
  reducedSnapped: boolean;
};
export function createScene(n: number, reduced = false, auto = false): SceneState {
  'worklet';
  const selectedMix: number[] = [];
  for (let i = 0; i < n; i++)
    selectedMix.push(0);
  return { sim: createBurstState(n, reduced), reduced, time: 0, accumulator: 0, loop: 0, auto,
    idleTime: 0, idleGain: auto ? 0 : 1, paused: false, resumeAt: 0, recovering: false, held: false, touching: false, serial: 0,
    expanded: false, age: 0, rowsStarted: false, launched: false, returning: false, home: true,
    dock: 0, dockFrom: 0, dockTarget: 0, dockAge: 1, springs: [0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0],
    pressFrom: 0, pressAge: 1, rowFrom: 0, rowAge: 1, pressed: -1, selected: -1,
    previous: -1, selectionAge: 1, selectedMix, landed: 0, events: 0, sounded: false,
    shutSounded: true, fade: 1, reducedFrom: 1, reducedSnapped: true };
}
function follow(springs: number[], at: number, target: number, k: number, c: number, h: number) {
  'worklet';
  springs[at + 1] += (k * (target - springs[at]) - c * springs[at + 1]) * h;
  springs[at] += springs[at + 1] * h;
  if (Math.abs(springs[at] - target) < 0.0001 && Math.abs(springs[at + 1]) < 0.001) {
    springs[at] = target;
    springs[at + 1] = 0;
  }
}
function dockTo(s: SceneState, goal: number) {
  'worklet';
  s.dockFrom = s.dock;
  s.dockTarget = goal;
  s.dockAge = 0;
}
function select(s: SceneState, index: number) {
  'worklet';
  s.previous = s.selected;
  s.selected = index;
  s.selectionAge = 0;
  s.events |= SCENE_EVENT.changed;
}
function toggle(s: SceneState) {
  'worklet';
  s.expanded = !s.expanded;
  s.age = 0;
  s.rowsStarted = false;
  s.sounded = false;
  s.returning = false;
  s.home = false;
  s.shutSounded = false;
  if (!s.expanded) {
    select(s, -1);
    s.landed = 0;
  }
  if (s.reduced) {
    s.reducedFrom = s.fade;
    s.reducedSnapped = false;
  }
  else if (s.expanded) {
    dockTo(s, 1);
    if (s.springs[SPRING.disc] > 0.001)
      s.launched = true;
  }
  s.events |= SCENE_EVENT.changed;
}
function press(s: SceneState, down: boolean, row = -1) {
  'worklet';
  s.held = down;
  if (row < 0) {
    s.pressed = -1;
    s.pressFrom = s.springs[SPRING.folderPress];
    s.pressAge = 0;
  }
  else {
    s.pressed = row;
    s.rowFrom = s.springs[SPRING.rowPress];
    s.rowAge = 0;
  }
}
function automatic(s: SceneState, before: number) {
  'worklet';
  const t = s.loop;
  if ((before < TIMELINE.folderPress && t >= TIMELINE.folderPress)
    || (before < TIMELINE.close - TIMELINE.pressDuration && t >= TIMELINE.close - TIMELINE.pressDuration))
    press(s, true);
  if ((before < TIMELINE.open && t >= TIMELINE.open) || (before < TIMELINE.close && t >= TIMELINE.close)) {
    press(s, false);
    toggle(s);
  }
  if (before < TIMELINE.select && t >= TIMELINE.select)
    press(s, true, 2);
  if (before < TIMELINE.select + TIMELINE.pressDuration && t >= TIMELINE.select + TIMELINE.pressDuration) {
    press(s, false, 2);
    select(s, 2);
  }
}
/** One fixed clock. Commands and scheduled beats are re-evaluated, never queued timers. */
export function stepScene(previous: SceneState, dt: number, layout: BurstLayout, input?: SceneInput): SceneState {
  'worklet';
  let s: SceneState = { ...previous, sim: { ...previous.sim, rows: previous.sim.rows.slice() },
    springs: previous.springs.slice(), selectedMix: previous.selectedMix.slice(), events: 0 };
  if (input && input.serial > s.serial) {
    if (s.auto && !s.paused)
      s.held = false;
    s.serial = input.serial;
    s.paused = true;
    s.recovering = false;
    s.resumeAt = s.time + TIMELINE.resume;
    if (input.kind === 'touchStart')
      s.touching = true;
    if (input.kind === 'touchEnd')
      s.touching = false;
    if (input.kind === 'folderDown') {
      press(s, true);
      s.events |= SCENE_EVENT.light;
    }
    if (input.kind === 'folderUp')
      press(s, false);
    if (input.kind === 'toggle')
      toggle(s);
    if (input.kind === 'rowDown')
      press(s, true, input.index);
    if (input.kind === 'rowUp')
      press(s, false, input.index);
    if (input.kind === 'select' && s.expanded && (s.landed & (1 << input.index))) {
      select(s, s.selected === input.index ? -1 : input.index);
      s.events |= SCENE_EVENT.selection;
    }
  }
  s.accumulator += Math.max(0, Math.min(PHYSICS.maxDt, dt));
  const h = PHYSICS.step;
  while (s.accumulator + 1e-10 >= h) {
    s.accumulator = Math.max(0, s.accumulator - h);
    s.time += h;
    s.age += h;
    if (s.auto && !s.reduced) {
      if (s.paused && !s.held && !s.touching && s.time >= s.resumeAt) {
        s.recovering = true;
        if (s.expanded)
          toggle(s);
        if (s.home && s.dock === 0) {
          const serial = s.serial, time = s.time, remainder = s.accumulator;
          s = createScene(layout.n, false, true);
          s.serial = serial;
          s.time = time;
          s.accumulator = remainder;
          s.events |= SCENE_EVENT.changed;
        }
      }
      if (!s.paused) {
        const before = s.loop;
        s.loop += h;
        automatic(s, before);
        if (s.loop >= 10 - 1e-9) {
          const serial = s.serial, time = s.time, remainder = s.accumulator;
          s = createScene(layout.n, false, true);
          s.serial = serial;
          s.time = time;
          s.accumulator = remainder;
          s.events |= SCENE_EVENT.changed;
        }
      }
    }
    if (s.auto && !s.paused) {
      s.idleTime = s.loop;
      s.idleGain = smooth01(s.loop / .6) * (1 - smooth01((s.loop - 9.4) / .6));
    }
    else {
      s.idleTime += h;
      s.idleGain += ((s.recovering ? 0 : 1) - s.idleGain) * (1 - Math.exp(-8 * h));
    }
    s.selectionAge += h;
    s.pressAge += h;
    s.rowAge += h;
    for (let i = 0; i < layout.n; i++) {
      const goal = s.selected === i ? 1 : 0;
      s.selectedMix[i] += Math.max(-h / 0.16, Math.min(h / 0.16, goal - s.selectedMix[i]));
    }
    const springs = s.springs;
    if (s.held && s.pressed < 0)
      springs[SPRING.folderPress] = s.pressFrom + (1 - s.pressFrom) * bezier(s.pressAge / 0.12, .23, 1, .32, 1);
    else
      follow(springs, SPRING.folderPress, 0, 300, 20, h);
    if (s.held && s.pressed >= 0)
      springs[SPRING.rowPress] = s.rowFrom + (1 - s.rowFrom) * bezier(s.rowAge / 0.12, .23, 1, .32, 1);
    else {
      follow(springs, SPRING.rowPress, 0, 300, 20, h);
      if (Math.abs(springs[SPRING.rowPress]) < .0001)
        s.pressed = -1;
    }
    if (s.reduced) {
      if (!s.reducedSnapped && s.age >= .12) {
        s.reducedSnapped = true;
        s.dock = s.expanded ? 1 : 0;
        springs[SPRING.lean] = s.expanded ? .72 : 0;
        springs[SPRING.disc] = s.dock;
        s.sim = createBurstState(layout.n, true, false);
        if (s.expanded)
          commandBurst(s.sim, true);
        s.home = !s.expanded;
      }
      s.fade = s.reducedSnapped ? clamp01((s.age - .12) / .22) : s.reducedFrom * (1 - clamp01(s.age / .12));
      if (s.home && (s.serial === 0 || s.age > .34))
        s.fade = 1;
    }
    else {
      if (s.expanded) {
        if (s.age >= TIMELINE.launch && !s.launched) {
          s.launched = true;
          springs[SPRING.recoil] += .6;
        }
        if (s.age >= TIMELINE.eject && !s.rowsStarted) {
          commandBurst(s.sim, true);
          s.rowsStarted = true;
        }
      }
      else if (!s.home) {
        if (s.age >= TIMELINE.suck && !s.rowsStarted) {
          commandBurst(s.sim, false);
          s.rowsStarted = true;
        }
        // The top row is released last; the slower disc follows it into the mouth.
        if (s.age >= TIMELINE.suck + (layout.n - 1) * PHYSICS.closeStagger) {
          s.returning = true;
          s.launched = false;
        }
        if (s.returning && springs[SPRING.disc] < .04 && s.sim.completed && s.sim.target === 0) {
          s.home = true;
          dockTo(s, 0);
          s.events |= SCENE_EVENT.changed;
        }
      }
      const lean = s.home ? 0 : s.expanded ? s.age < TIMELINE.lean ? springs[SPRING.lean] : s.rowsStarted && s.sim.completed ? .72 : 1 : 1;
      follow(springs, SPRING.lean, lean, s.home ? 220 : lean === .72 ? 120 : 170, s.home ? 22 : lean === .72 ? 18 : 16, h);
      if (!s.shutSounded && s.home && springs[SPRING.lean] < .1) {
        s.shutSounded = true;
        s.events |= SCENE_EVENT.soft;
      }
      follow(springs, SPRING.disc, s.launched ? 1 : 0, s.launched ? 90 : TIMELINE.discReturnStiffness, s.launched ? 14 : TIMELINE.discReturnDamping, h);
      // Before it launches the disc climbs until half of it clears the open lip, as in the reference.
      follow(springs, SPRING.rise, s.expanded ? TIMELINE.rise : 0, 70, 16, h);
      follow(springs, SPRING.recoil, 0, 260, 14, h);
      follow(springs, SPRING.kick, 0, 260, 14, h);
      s.dockAge += h;
      s.dock = s.dockAge >= .56 ? s.dockTarget
        : s.dockFrom + (s.dockTarget - s.dockFrom) * bezier(s.dockAge / .56, .77, 0, .175, 1);
    }
    const placement = sceneFolder(s, layout);
    const events = stepBurst(s.sim, h, layout, placement, false);
    if (!s.reduced) {
      if (events & BURST_EVENT.eject) {
        springs[SPRING.recoil] += .35;
        springs[SPRING.kick] += 1.5;
        if (!s.sounded) {
          s.events |= SCENE_EVENT.medium;
          s.sounded = true;
        }
      }
      if (events & BURST_EVENT.gulp) {
        springs[SPRING.recoil] -= .3;
        springs[SPRING.kick] += 1;
      }
    }
    let landed = 0;
    if (s.expanded)
      for (let i = 0; i < layout.n; i++) {
        const o = i * STATE.stride;
        if (s.sim.rows[o] === 1 && s.sim.rows[o + 1] === 0 && (!s.reduced || s.sim.rows[o + STATE.fade] === 1))
          landed |= 1 << i;
      }
    if (landed !== s.landed) {
      s.landed = landed;
      s.events |= SCENE_EVENT.changed;
    }
  }
  return s;
}
