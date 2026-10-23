import { cellHome, type VaultLayout } from './vault-layout';
import { advanceSpin, bezier, clamp, ease, flightSpring, launchTurn, lerp, settleTail, spring, TAU } from './vault-motion';
import { emit, ripple } from './vault-actions';
import { blankPose, type Engine, type HeroVisual, initialHero } from './vault-state';

export function heroFrame(e: Engine, l: VaultLayout, scrollY: number, dt: number, reduced: boolean): HeroVisual {
  'worklet';
  if (e.selected < 0) return initialHero(l);
  const age = e.now - e.phaseAt;
  const seconds = age / 1000;
  const closing = e.phase === 'closing';
  const closingFrom = e.closePose;
  const recess = bezier(age / (reduced ? 200 : 500), 0.32, 0.72, 0, 1);
  const recede = closing ? e.recedeFrom * (1 - recess) : recess;
  const home = cellHome(l, e.selected, scrollY, recede);
  if (!e.dragging && !closing) e.drag = spring(e.drag, 0, dt, 240, 24);
  const drag = reduced ? 0 : e.drag.x;
  const shrink = 1 - 0.3 * clamp(drag / 300);
  const xp = reduced ? 1 : flightSpring(seconds, 80, 15, 0.35, 0.45);
  const yp = reduced ? 1 : flightSpring(seconds, 42, 11, 0.50, 0.62);
  const rp = reduced ? 1 : flightSpring(seconds, 52, 8.8, 0.72, 0.9);
  let cx = lerp(home.x, l.heldX, xp);
  let cy = lerp(home.y, l.heldY, yp) + drag;
  let r = lerp(home.r * e.pressPose.r, l.heldR, rp) * shrink;
  let tiltX = reduced ? 0 : e.pressPose.tiltX * (1 - clamp(yp)) + 0.25 * Math.sin(Math.PI * clamp(age / 600));
  let tiltY = reduced ? 0 : e.pressPose.tiltY * (1 - clamp(xp));
  let nameX = cx;
  let nameY = lerp(home.nameY, l.title.y + 19, yp) + drag * 0.3;
  let nameScale = lerp(13 / l.titleSize * home.scale, 1, rp);
  let lift = reduced ? 1 : ease(age / 180);
  let details = (1 - clamp(drag / 300));
  let alpha = reduced ? clamp(age / 200) : 1;
  let spread = reduced ? alpha : ease((age - 60) / 700);
  let power = details;
  let spin = 0;
  let driftX = 0; let driftY = 0;
  if (!reduced && !closing) {
    if (e.turnMode === 'launch') e.turn.x = launchTurn(seconds);
    else if (e.turnMode === 'free') {
      const snapped = e.turn.snapped;
      e.turn = advanceSpin(e.turn, dt);
      if (!snapped && e.turn.snapped) emit(e, 'light');
    } else if (e.turnMode === 'slow') {
      e.turn.x = lerp(e.turnFrom, e.turnTo, clamp((e.now - e.turnAt) / 700));
      if (e.now - e.turnAt >= 700) { e.turnMode = 'drag'; emit(e, 'light'); }
    }
    spin = e.turn.x;
    if (age > 900 && !closing) {
      const sway = Math.min(1, (age - 900) / 500);
      const phase = (age - 900) / 5500 * TAU;
      driftX = Math.sin(phase) * sway;
      driftY = Math.cos(phase) * sway;
      tiltX += driftX * 0.09;
      tiltY += driftY * 0.09;
    }
  }
  if (!closing && age >= (reduced ? 200 : 620) && !e.landed) { e.landed = true; emit(e, 'medium'); }
  if (!closing && age >= 1600) e.phase = 'held';
  if (closing && closingFrom) {
    const p = reduced ? 0 : flightSpring(seconds, 75, 15, 0.42, 0.56);
    cx = lerp(closingFrom.pose.cx, home.x, p);
    // A velocity impulse preserves the releasing finger's downward momentum.
    const impulse = reduced ? 0 : spring({ x: 0, v: e.drag.v }, 0, seconds, 75, 15).x * settleTail(seconds, 0.42, 0.56);
    cy = lerp(closingFrom.pose.cy, home.y, p) + impulse;
    r = lerp(closingFrom.pose.r, home.r, p) * (reduced ? 1 : 1 - 0.04 * ease((age - 500) / 60));
    spin = reduced ? 0 : lerp(closingFrom.pose.spin, e.turnTo, p);
    tiltX = reduced ? 0 : closingFrom.pose.tiltX * (1 - clamp(p)) - 0.3 * Math.sin(Math.PI * clamp(age / 560));
    tiltY = closingFrom.pose.tiltY * (1 - clamp(p));
    nameX = lerp(closingFrom.nameX, home.x, p);
    nameY = lerp(closingFrom.nameY, home.nameY, p);
    nameScale = lerp(closingFrom.nameScale, 13 / l.titleSize * home.scale, p);
    details = closingFrom.details * (1 - clamp(age / 140));
    spread = closingFrom.flood.spread * (1 - ease(age / 420));
    power = closingFrom.flood.power * (1 - ease(age / 420));
    lift = closingFrom.pose.lift * (1 - ease((age - 380) / 180));
    alpha = reduced ? 1 - clamp(age / 200) : 1;
    if (age >= (reduced ? 200 : 560)) {
      const c = e.cells[e.selected]; c.popAt = e.now; c.popGain = -0.04;
      ripple(e, l, e.selected, scrollY, 0.5); emit(e, 'light'); emit(e, 'close');
      e.selected = -1; e.phase = 'rest'; e.closePose = null;
      return initialHero(l);
    }
  }
  const sourceSheen = closing ? closingFrom?.pose.sheen ?? -1 : e.launchPose?.sheen ?? -1;
  const band = reduced ? -1 : closing ? sourceSheen < 0 ? -1 : sourceSheen + age / 500 : (age - 620) / 500;
  const envX = clamp((cx - l.width / 2) / (l.width / 2), -1, 1);
  const envY = clamp((cy - l.height / 2) / (l.height / 2), -1, 1);
  const envReturn = reduced ? 0 : flightSpring(seconds, 75, 15, 0.42, 0.56);
  return { index: e.selected, recede, alpha, age: closing ? closingFrom?.age ?? 0 : age,
    details, nameX, nameY, nameScale,
    pose: { ...blankPose(), cx, cy, r, tiltX, tiltY, spin,
      roll: closing && closingFrom ? closingFrom.pose.roll * (1 - envReturn) : (e.launchPose?.roll ?? 0) * (1 - clamp(xp)),
      fill: 1, lift, present: 1,
      envX: closing && closingFrom ? lerp(closingFrom.pose.envX, envX, envReturn) : clamp(envX + 0.35 * driftX, -1, 1),
      envY: closing && closingFrom ? lerp(closingFrom.pose.envY, envY, envReturn) : clamp(envY + 0.35 * driftY, -1, 1),
      sheen: band >= 0 && band <= 1 ? band : !closing && age < 100 ? sourceSheen : -1 },
    flood: { w: l.width, h: l.height, ox: home.x, oy: home.y, cx, cy, r, spread,
      power, rays: closing && closingFrom ? closingFrom.flood.rays * (1 - ease(age / 420)) :
        e.landed ? ease((age - 620) / 400) : 0,
      since: reduced || !e.landed ? -1 : closing && closingFrom ? closingFrom.flood.since + seconds : Math.max(0, age - 620) / 1000,
      time: reduced ? 0 : closing && closingFrom ? closingFrom.flood.time + seconds : seconds },
  };
}
