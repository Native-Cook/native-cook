/**
 * Every moving value of the block and the four inputs that move them: down, move, up and the taps.
 * A real finger and the autoplay script call the same functions, so the demo is the interaction.
 */
import { useMemo } from 'react';
import {
  Easing, ReduceMotion, cancelAnimation, useFrameCallback, useSharedValue, withDelay, withSequence, withSpring, withTiming,
} from 'react-native-reanimated';
import { scheduleOnRN } from 'react-native-worklets';

import { clamp01 } from './island-path';
import { FADE, LOOP_MS, MAGNET, PRESS, PULL, QA_HOLD_AT, QA_SLOWMO, QA_SHEET, RESUME_AFTER_MS, SCRIPT, SEND, SPRINGS } from './motion';
import { CONTACTS, FAVOURITES, LAYOUT, SHEET, TOKEN } from './theme';

export const PHASE = { rest: 0, pulling: 1, holding: 2, docked: 3, sending: 4, closing: 5, sent: 6 } as const;
export type Haptic = 'light' | 'medium' | 'select' | 'success';

export function makeGeometry(width: number, height: number) {
  const cardW = width - LAYOUT.side * 2;
  const cardH = LAYOUT.cardHeight;
  const innerX = SHEET.inset + SHEET.favPad;
  const innerW = width - innerX * 2;
  const favY = SHEET.top + SHEET.favTop + SHEET.favSize / 2;
  const x: number[] = [];
  const y: number[] = [];
  const row: boolean[] = [];
  FAVOURITES.forEach((_, i) => { x.push(innerX + innerW * (i + 0.5) / 4); y.push(favY); row.push(false); });
  CONTACTS.forEach((_, i) => {
    x.push(SHEET.inset + SHEET.rowAvatarX);
    y.push(SHEET.top + SHEET.rowsTop + i * SHEET.rowHeight + SHEET.rowHeight / 2);
    row.push(true);
  });
  const homeCx = width / 2;
  const homeCy = LAYOUT.cardTop + cardH / 2;
  return {
    width, height, cardW, cardH, homeCx, homeCy, innerX, innerW,
    targets: { x, y, row },
    // The "Send to" action on the card face, relative to the card centre (unscaled).
    sendAction: { x: -cardW / 2 + 44, y: cardH / 2 - 64, r: 30 },
    script: { x: homeCx + 24, y: LAYOUT.cardTop + 118, target: 2 },
  };
}
export type Geometry = ReturnType<typeof makeGeometry>;

type Callbacks = {
  onPhase: (phase: number) => void;
  onSent: (target: number) => void;
  onUndo: () => void;
  onHaptic: (kind: Haptic) => void;
};

const smooth = (t: number) => {
  'worklet';
  const c = clamp01(t);
  return c * c * (3 - 2 * c);
};
const easeInOut = (t: number) => {
  'worklet';
  const c = clamp01(t);
  return c < 0.5 ? 4 * c * c * c : 1 - Math.pow(-2 * c + 2, 3) / 2;
};

export function useSendMotion(geo: Geometry, autoPlay: boolean, reduced: boolean, cb: Callbacks) {
  const g = useSharedValue(0);
  /** Wall clock (ms) for ambient light. */
  const clock = useSharedValue(0);
  const veil = useSharedValue(0);
  const cardX = useSharedValue(0);
  const cardY = useSharedValue(0);
  const cardScale = useSharedValue(1);
  const press = useSharedValue(1);
  const cardOpacity = useSharedValue(1);
  const tilt = useSharedValue(0);
  const sway = useSharedValue(0);
  const follow = useSharedValue(0);
  const fx = useSharedValue(0);
  const fy = useSharedValue(0);
  const ox = useSharedValue(0);
  const oy = useSharedValue(0);
  const magX = useSharedValue(0);
  const magY = useSharedValue(0);
  const hoverIdx = useSharedValue(-1);
  const hoverAmt = useSharedValue(0);
  const prevIdx = useSharedValue(-1);
  const prevAmt = useSharedValue(0);
  const ring = useSharedValue(0);
  const check = useSharedValue(0);
  const sendIdx = useSharedValue(-1);
  const sent = useSharedValue(0);
  const undo = useSharedValue(0);
  const phase = useSharedValue<number>(PHASE.rest);
  const active = useSharedValue(0);
  const scripted = useSharedValue(false);
  const down0 = useSharedValue({ x: 0, y: 0, cardX: 0, cardY: 0, travel: 0 });
  const script = useSharedValue({ on: autoPlay, t: 0, quiet: 0, touching: false, lastX: 0, lastY: 0 });

  const { onPhase, onSent, onUndo, onHaptic } = cb;
  const never = ReduceMotion.Never;

  const setPhase = (p: number) => {
    'worklet';
    if (phase.get() === p) return;
    phase.set(p);
    scheduleOnRN(onPhase, p);
  };
  const haptic = (kind: Haptic) => {
    'worklet';
    if (!scripted.get()) scheduleOnRN(onHaptic, kind);
  };
  const tokenTx = () => {
    'worklet';
    return follow.get() ? fx.get() - ox.get() * cardScale.get() - geo.homeCx + magX.get() : cardX.get();
  };
  const tokenTy = () => {
    'worklet';
    return follow.get() ? fy.get() - oy.get() * cardScale.get() - geo.homeCy + magY.get() : cardY.get();
  };
  /** Stop following the finger without a jump: the spring then starts where the card is. */
  const freeze = () => {
    'worklet';
    cardX.set(tokenTx());
    cardY.set(tokenTy());
    follow.set(0);
    magX.set(0);
    magY.set(0);
  };
  const setHover = (i: number) => {
    'worklet';
    if (i === hoverIdx.get()) return;
    if (hoverIdx.get() >= 0) {
      prevIdx.set(hoverIdx.get());
      prevAmt.set(hoverAmt.get());
      prevAmt.set(withSpring(0, SPRINGS.hover));
    }
    hoverIdx.set(i);
    if (i < 0) return;
    if (i === prevIdx.get()) { hoverAmt.set(prevAmt.get()); prevIdx.set(-1); } else hoverAmt.set(0);
    hoverAmt.set(withSpring(1, SPRINGS.hover));
    haptic('select');
  };

  const updateHover = () => {
    'worklet';
    if (g.get() < 1.6 && veil.get() < 0.9) return;
    const tcx = fx.get() - ox.get() * TOKEN.scale;
    const tcy = fy.get() - oy.get() * TOKEN.scale;
    let best = -1;
    let bestD = 1e9;
    for (let i = 0; i < geo.targets.x.length; i += 1) {
      const row = geo.targets.row[i];
      const inside = Math.abs(tcx - geo.width / 2) < geo.innerW / 2 + 8;
      const d = row ? (inside ? Math.abs(tcy - geo.targets.y[i]) * 1.6 : 1e9)
        : Math.hypot(tcx - geo.targets.x[i], tcy - geo.targets.y[i]);
      if (d < bestD) { bestD = d; best = i; }
    }
    if (best >= 0 && bestD < MAGNET.radius) {
      const k = 1 - smooth((bestD - MAGNET.over * 0.4) / (MAGNET.radius - MAGNET.over * 0.4));
      const aimX = geo.targets.row[best] ? tcx : geo.targets.x[best];
      magX.set(withSpring((aimX - tcx) * MAGNET.pull * k, SPRINGS.hover));
      magY.set(withSpring((geo.targets.y[best] - tcy) * MAGNET.pull * k, SPRINGS.hover));
      setHover(bestD < MAGNET.over ? best : -1);
    } else {
      magX.set(withSpring(0, SPRINGS.hover));
      magY.set(withSpring(0, SPRINGS.hover));
      setHover(-1);
    }
  };

  const showSheet = () => {
    'worklet';
    if (reduced) {
      g.set(2);
      veil.set(withTiming(1, { duration: FADE.reducedMs, reduceMotion: never }));
    } else g.set(withSpring(2, SPRINGS.open));
  };

  const commit = () => {
    'worklet';
    setPhase(PHASE.holding);
    haptic('medium');
    const s = cardScale.get();
    ox.set((fx.get() - geo.homeCx - cardX.get()) / s);
    oy.set((fy.get() - geo.homeCy - cardY.get()) / s);
    follow.set(1);
    tilt.set(withSpring(0, SPRINGS.card));
    cardScale.set(withSpring(TOKEN.scale, SPRINGS.token));
    showSheet();
  };

  const endSent = () => {
    'worklet';
    cancelAnimation(undo);
    undo.set(withTiming(0, { duration: SEND.pillSwapMs }));
    sent.set(withTiming(0, { duration: SEND.pillSwapMs, reduceMotion: never }));
    if (phase.get() === PHASE.sent) setPhase(PHASE.rest);
  };

  const afterSend = (i: number) => {
    'worklet';
    g.set(0);
    veil.set(0);
    hoverIdx.set(-1);
    hoverAmt.set(0);
    prevIdx.set(-1);
    ring.set(0);
    check.set(0);
    sendIdx.set(-1);
    cardX.set(0);
    cardY.set(0);
    sway.set(0);
    cardScale.set(reduced ? 1 : SEND.enterFrom);
    cardScale.set(withSpring(1, SPRINGS.enter));
    cardOpacity.set(withTiming(1, { duration: SEND.fadeMs, reduceMotion: never }));
    setPhase(PHASE.sent);
    scheduleOnRN(onSent, i);
    undo.set(1);
    undo.set(withTiming(0, { duration: SEND.undoMs, easing: Easing.linear, reduceMotion: never }, (done) => {
      if (done) endSent();
    }));
  };

  const send = (i: number) => {
    'worklet';
    setPhase(PHASE.sending);
    freeze();
    sendIdx.set(i);
    if (hoverIdx.get() !== i) { hoverIdx.set(i); hoverAmt.set(1); }
    sway.set(withSpring(0, SPRINGS.card));
    const tx = geo.targets.x[i] - geo.homeCx;
    const ty = geo.targets.y[i] - geo.homeCy;
    const absorb = { duration: SEND.absorbMs, easing: SEND.absorbEase };
    if (reduced) {
      cardOpacity.set(withTiming(0, { duration: FADE.reducedMs, reduceMotion: never }));
    } else {
      cardX.set(withTiming(tx, absorb));
      cardY.set(withTiming(ty, absorb));
      cardScale.set(withTiming(SEND.absorbScale, absorb));
      cardOpacity.set(withDelay(SEND.absorbMs * 0.6, withTiming(0, { duration: SEND.absorbMs * 0.4 })));
      // The target swells as it drinks the card, then settles.
      hoverAmt.set(withDelay(SEND.absorbMs * 0.7, withSequence(withSpring(1.7, SPRINGS.check), withSpring(1, SPRINGS.hover))));
    }
    ring.set(0);
    ring.set(withDelay(SEND.absorbMs, withTiming(1, { duration: SEND.ringMs, easing: SEND.ringEase })));
    check.set(withDelay(SEND.absorbMs + SEND.ringMs, withSpring(1, SPRINGS.check, (done) => {
      if (done) haptic('success');
    })));
    const closeAt = SEND.absorbMs + SEND.ringMs + SEND.holdMs;
    sent.set(withDelay(closeAt + SEND.closeMs * 0.55, withTiming(1, { duration: SEND.pillSwapMs, reduceMotion: never })));
    if (reduced) {
      veil.set(withDelay(closeAt, withTiming(0, { duration: FADE.reducedMs, reduceMotion: never }, (done) => {
        if (done) afterSend(i);
      })));
    } else {
      g.set(withDelay(closeAt, withTiming(0, { duration: SEND.closeMs, easing: SEND.closeEase }, (done) => {
        if (done) afterSend(i);
      })));
    }
  };

  /** Released away from everyone: the card goes back behind the sheet; pick a person and Next, or close. */
  const dock = (vx: number, vy: number) => {
    'worklet';
    freeze();
    setHover(-1);
    setPhase(PHASE.docked);
    sway.set(withSpring(0, SPRINGS.card));
    cardOpacity.set(withTiming(0, { duration: SEND.fadeMs, reduceMotion: never }));
    if (reduced) { cardX.set(0); cardY.set(0); cardScale.set(1); return; }
    cardX.set(withSpring(0, { ...SPRINGS.card, velocity: vx }));
    cardY.set(withSpring(0, { ...SPRINGS.card, velocity: vy }));
    cardScale.set(withSpring(1, SPRINGS.token));
  };

  const goHome = (vx: number, vy: number) => {
    'worklet';
    cardOpacity.set(withTiming(1, { duration: SEND.fadeMs, reduceMotion: never }));
    cardX.set(withSpring(0, { ...SPRINGS.card, velocity: vx }));
    cardY.set(withSpring(0, { ...SPRINGS.card, velocity: vy }));
    cardScale.set(withSpring(1, SPRINGS.card));
    tilt.set(withSpring(0, SPRINGS.card));
    sway.set(withSpring(0, SPRINGS.card));
  };

  const down = (x: number, y: number, fromScript: boolean) => {
    'worklet';
    const ph = phase.get();
    active.set(0);
    if (ph === PHASE.sending || ph === PHASE.closing || ph === PHASE.docked) return;
    const s = cardScale.get();
    const cx = geo.homeCx + tokenTx();
    const cy = geo.homeCy + tokenTy();
    if (Math.abs(x - cx) > (geo.cardW * s) / 2 || Math.abs(y - cy) > (geo.cardH * s) / 2) return;
    scripted.set(fromScript);
    active.set(1);
    fx.set(x);
    fy.set(y);
    press.set(withTiming(PRESS.scale, { duration: PRESS.duration }));
    if (ph === PHASE.docked || ph === PHASE.holding) {
      freeze();
      cancelAnimation(cardX);
      cancelAnimation(cardY);
      ox.set((x - cx) / s);
      oy.set((y - cy) / s);
      follow.set(1);
      cardScale.set(withSpring(TOKEN.scale, SPRINGS.token));
      setPhase(PHASE.holding);
      updateHover();
      return;
    }
    if (ph === PHASE.sent) endSent();
    cancelAnimation(cardX);
    cancelAnimation(cardY);
    cancelAnimation(g);
    down0.set({ x, y, cardX: cardX.get(), cardY: cardY.get(), travel: 0 });
    ox.set((x - cx) / s);
    oy.set((y - cy) / s);
    setPhase(PHASE.pulling);
  };

  const move = (x: number, y: number, vx: number) => {
    'worklet';
    if (!active.get()) return;
    fx.set(x);
    fy.set(y);
    const ph = phase.get();
    if (ph === PHASE.pulling) {
      const d = down0.get();
      let dy = d.cardY + (y - d.y);
      if (dy < 0) dy *= PULL.upResist;
      down0.set({ ...d, travel: Math.max(d.travel, Math.abs(x - d.x) + Math.abs(y - d.y)) });
      cardY.set(dy);
      cardX.set(d.cardX + (x - d.x) * PULL.sideFollow);
      tilt.set(clamp01(dy / PULL.full));
      if (!reduced) g.set(clamp01(dy / PULL.full));
      if (dy >= PULL.commit) commit();
    } else if (ph === PHASE.holding) {
      sway.set(withSpring(Math.max(-7, Math.min(7, vx * 0.006)), SPRINGS.hover));
      updateHover();
    }
  };

  const openSheet = () => {
    'worklet';
    const ph = phase.get();
    if (ph !== PHASE.rest && ph !== PHASE.sent) return;
    if (ph === PHASE.sent) endSent();
    setPhase(PHASE.docked);
    showSheet();
    cardOpacity.set(withTiming(0, { duration: SEND.fadeMs, reduceMotion: never }));
  };

  const up = (vx: number, vy: number) => {
    'worklet';
    if (!active.get()) return;
    active.set(0);
    press.set(withSpring(1, SPRINGS.hover));
    const ph = phase.get();
    if (ph === PHASE.pulling) {
      const d = down0.get();
      setPhase(PHASE.rest);
      if (d.travel < 6) {
        // A tap. On "Send to" it opens the sheet; anywhere else the card nods down to teach the pull.
        const lx = (d.x - geo.homeCx) - geo.sendAction.x;
        const ly = (d.y - geo.homeCy) - geo.sendAction.y;
        if (Math.hypot(lx, ly) < geo.sendAction.r) { openSheet(); return; }
        if (!reduced) {
          cardY.set(withSequence(withTiming(14, { duration: 160, easing: Easing.out(Easing.quad) }), withSpring(0, SPRINGS.retract)));
          g.set(withSequence(withTiming(0.16, { duration: 160 }), withSpring(0, SPRINGS.retract)));
        }
        return;
      }
      if (cardY.get() > 20) haptic('light');
      goHome(vx * PULL.sideFollow, vy);
      if (reduced) g.set(0); else g.set(withSpring(0, SPRINGS.retract));
    } else if (ph === PHASE.holding) {
      if (hoverIdx.get() >= 0) send(hoverIdx.get());
      else dock(vx, vy);
    }
  };

  const close = () => {
    'worklet';
    const ph = phase.get();
    if (ph !== PHASE.docked && ph !== PHASE.holding) return;
    active.set(0);
    freeze();
    setHover(-1);
    setPhase(PHASE.closing);
    haptic('light');
    const finish = (done?: boolean) => {
      'worklet';
      if (done) setPhase(PHASE.rest);
    };
    if (reduced) {
      cardX.set(0); cardY.set(0); cardScale.set(1);
      veil.set(withTiming(0, { duration: FADE.reducedMs, reduceMotion: never }, (done) => {
        if (done) g.set(0);
        finish(done);
      }));
      return;
    }
    goHome(0, 0);
    g.set(withTiming(0, { duration: SEND.closeMs, easing: SEND.closeEase }, finish));
  };

  /** Tapping a person on the open sheet selects them (as the source's list does); Next sends. */
  const select = (i: number) => {
    'worklet';
    if (phase.get() !== PHASE.docked) return;
    scripted.set(false);
    setHover(i);
  };

  const tapTarget = (i: number) => {
    'worklet';
    if (phase.get() !== PHASE.docked) return;
    scripted.set(false);
    send(i);
  };

  const tapUndo = (fromScript: boolean) => {
    'worklet';
    if (phase.get() !== PHASE.sent) return;
    scripted.set(fromScript);
    haptic('light');
    endSent();
    scheduleOnRN(onUndo);
  };

  /** A touch on the screen: the script lets go at once, and comes back 3.5 s after the screen is at rest. */
  const takeOver = () => {
    'worklet';
    const s = script.get();
    if (s.on && scripted.get() && active.get()) {
      // The scripted finger lets go without completing anything: a held token docks instead of sending.
      setHover(-1);
      up(0, 0);
    }
    script.set({ ...s, on: false, quiet: 0, touching: true });
  };
  const letGo = () => {
    'worklet';
    script.set({ ...script.get(), touching: false, quiet: 0 });
  };

  useFrameCallback((info) => {
    clock.set(clock.get() + Math.min(info.timeSincePreviousFrame ?? 16, 64));
    if (QA_SHEET) {
      if (phase.get() === PHASE.rest && g.get() < 0.01) openSheet();
      return;
    }
    // Targets also react to the sheet arriving under a finger that has stopped moving.
    if (phase.get() === PHASE.holding && active.get()) updateHover();
    if (!autoPlay) return;
    const dt = Math.min(info.timeSincePreviousFrame ?? 16, 64);
    const s = script.get();
    if (!s.on) {
      const docked = phase.get() === PHASE.docked;
      const resting = !s.touching && ((phase.get() === PHASE.rest && g.get() < 0.01) || docked);
      const quiet = resting ? s.quiet + dt : 0;
      if (docked && quiet >= RESUME_AFTER_MS) {
        // Left open: close the sheet first, the demo starts again once the screen is back at rest.
        close();
        script.set({ ...s, quiet: 0 });
        return;
      }
      script.set({ ...s, quiet, on: quiet >= RESUME_AFTER_MS, t: 0 });
      return;
    }
    const prev = s.t;
    const t = QA_HOLD_AT !== null && prev >= QA_HOLD_AT ? prev : prev + dt / QA_SLOWMO;
    const P = geo.script;
    let lastX = s.lastX;
    let lastY = s.lastY;
    // A plain closure (no 'worklet' directive) so it can update lastX / lastY of this frame.
    const go = (x: number, y: number) => {
      move(x, y, ((x - lastX) / dt) * 1000);
      lastX = x;
      lastY = y;
    };
    if (prev < SCRIPT.down && t >= SCRIPT.down) { down(P.x, P.y, true); lastX = P.x; lastY = P.y; }
    if (t > SCRIPT.down && prev < SCRIPT.pullEnd) {
      // Press, a beat of stillness, then a pull that accelerates and eases into the hold.
      const u = clamp01((t - SCRIPT.down - 100) / (SCRIPT.pullEnd - SCRIPT.down - 100));
      go(P.x + 6 * easeInOut(u), P.y + SCRIPT.pullBy * easeInOut(u));
    }
    if (t > SCRIPT.hoverStart && prev < SCRIPT.hoverArrive) {
      const u = easeInOut((t - SCRIPT.hoverStart) / (SCRIPT.hoverArrive - SCRIPT.hoverStart));
      const x0 = P.x + 6;
      const y0 = P.y + SCRIPT.pullBy;
      const x1 = geo.targets.x[P.target] + ox.get() * TOKEN.scale;
      const y1 = geo.targets.y[P.target] + oy.get() * TOKEN.scale;
      // A quadratic arc that swings out to the right before coming in to the avatar.
      const cx = x1 + 70;
      const cy = (y0 + y1) / 2 + 30;
      const a = (1 - u) * (1 - u);
      const b = 2 * (1 - u) * u;
      const c = u * u;
      go(a * x0 + b * cx + c * x1, a * y0 + b * cy + c * y1);
    }
    if (prev < SCRIPT.release && t >= SCRIPT.release) up(0, 0);
    if (prev < SCRIPT.undoTap && t >= SCRIPT.undoTap) tapUndo(true);
    script.set({ ...s, t: t >= LOOP_MS ? t - LOOP_MS : t, lastX, lastY });
  });

  return useMemo(() => ({
    values: {
      g, veil, cardX, cardY, cardScale, press, cardOpacity, tilt, sway, follow, fx, fy, ox, oy, magX, magY,
      hoverIdx, hoverAmt, prevIdx, prevAmt, ring, check, sendIdx, sent, undo, phase, clock,
    },
    input: { down, move, up, takeOver, letGo, close, openSheet, select, tapTarget, tapUndo },
  // The worklets close over shared values that never change identity.
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }), []);
}

export type SendMotion = ReturnType<typeof useSendMotion>;
export type SendValues = SendMotion['values'];
