// Coin Pile Collect — the engine hook shared by the screen and the CoinHopper tile: the simulation's shared state, the
// frame callback that steps it (autoplay replay, live thumb, restore after a touch, reduced motion), the pan gesture
// and the dev pin hook. Everything per-frame runs on the UI thread; JS only hears about meaningful beats.

import { useEffect, useMemo } from 'react';
import { Platform } from 'react-native';
import { Gesture } from 'react-native-gesture-handler';
import { SensorType, useAnimatedSensor, useFrameCallback, useSharedValue } from 'react-native-reanimated';
import { scheduleOnRN } from 'react-native-worklets';

import {
  C_BULGE,
  C_BULGEV,
  C_CENTS,
  C_EMPTYT,
  C_KICKAT,
  C_LATCH,
  C_LIT,
  C_MODE,
  C_PRESS,
  C_STEP,
  C_TARGET,
  C_TOTAL,
  C_VALVE,
  CTL,
  DT,
  EVENT_SIZE,
  EVX_EMPTY,
  EVX_LATCH,
  EVX_REFILLED,
  IN_CMD,
  IN_HOLD,
  IN_REDUCED,
  IN_SLIDE,
  MODE_LIVE,
  autoStep,
  beginReturn,
  clockOf,
  engineStep,
  initState,
  makeEvents,
  makeInputs,
  makeState,
  reducedStep,
  replayTo,
} from './cpc-engine';
import { CUE } from './cpc-haptics';
import { LOOP, RESTORE_IDLE, GESTURE, XCOIN, xcoinAngle, xcoinEase, xcoinTarget } from './cpc-motion';
import {
  EV_COLLECTED,
  EV_CENTS,
  EV_FLICK,
  EV_IMPACT,
  IN_FINGER,
  IN_FR,
  IN_FX,
  IN_FY,
  IN_GX,
  makeCfg,
  orderOf,
  settleHeap,
  makeState as makeSolverState,
  type Cfg,
} from './cpc-physics';
import { HEAP } from './cpc-heap';
import { comp } from './cpc-theme';

export type HopperHandle = { pour(): void; stop(): void; refill(): void };

export type HopperOptions = {
  totalCents: number;
  coins?: number;
  seed?: number;
  autoPlay: boolean;
  touchable: boolean;
  tilt: boolean;
  reduce: boolean;
  ready: boolean;
  scale: number; // window points per design point
  onCue?: (kind: number, a: number, b: number) => void;
};

/** the cfg of the teal tile: the baked heap for the default tile, a fresh settle (JS thread, once) for any other count or seed */
export function makeHopperCfg(coins: number | undefined, seed: number | undefined) {
  const t = comp.teal;
  const n = Math.max(8, Math.min(32, Math.round(coins ?? comp.coin.count)));
  const s = seed ?? comp.seed;
  const cfg = makeCfg(t.w, t.h, n, comp.coin.r, comp.coin.h, comp.mouth.x, comp.mouth.y, comp.keepOut.w, comp.keepOut.h, s);
  if (n === comp.coin.count && s === comp.seed) {
    cfg.heap = HEAP.slice();
  } else {
    const S = makeSolverState(n);
    settleHeap(cfg, S, n * 14 + 300, 14);
  }
  orderOf(cfg);
  return cfg;
}

function noop(_kind: number, _a: number, _b: number) {}

export type Sim = { A: number[]; inp: number[]; ev: number[] };

export function useHopper(cfg: Cfg, o: HopperOptions) {
  const { totalCents, autoPlay, touchable, tilt, reduce, ready, scale, onCue } = o;
  const sim = useSharedValue<Sim>({ A: makeState(), inp: makeInputs(), ev: makeEvents() });
  const theta = useSharedValue(0);
  const tick = useSharedValue(0);
  const uiMode = useSharedValue(autoPlay && !reduce ? 0 : 1); // 0 autoplay, 1 live, 2 restoring to rest
  const acc = useSharedValue(0);
  const idleT = useSharedValue(0);
  const restoreT = useSharedValue(0);
  const th0 = useSharedValue(0);
  const th1 = useSharedValue(0);
  const pinned = useSharedValue(-1);
  const pinDone = useSharedValue(-2);
  const tDown = useSharedValue(0); // 0 none, 1 on the pill, 2 in the pile
  const tX = useSharedValue(0);
  const tY = useSharedValue(0);
  const tSlide = useSharedValue(0);
  const cmd = useSharedValue(0);
  const lastCue = useSharedValue(0);
  const cueClock = useSharedValue(0);
  const coinSeq = useSharedValue(0);
  const collectedAcc = useSharedValue(0);
  const lastCb = useSharedValue(-1);
  const sensor = useAnimatedSensor(SensorType.GRAVITY, { interval: 'auto' });
  const sensorValue = sensor.sensor;

  // the resting state, once the resources exist
  useEffect(() => {
    sim.modify((s) => {
      'worklet';
      initState(cfg, s.A, totalCents);
      return s;
    });
    uiMode.set(autoPlay && !reduce ? 0 : 1);
    acc.set(0);
    theta.set(0);
    tick.set(tick.get() + 1);
  }, [cfg, totalCents, autoPlay, reduce, sim, uiMode, acc, theta, tick]);

  useEffect(() => {
    if (!tilt) sensor.unregister();
    return () => sensor.unregister();
  }, [tilt, sensor]);

  const emit = onCue ?? noop;
  const run = ready;

  const frame = useFrameCallback((info) => {
    'worklet';
    const dt = Math.min((info.timeSincePreviousFrame ?? 16.7) / 1000, 0.05);
    const pin = pinned.get();
    if (pin >= 0) {
      if (pinDone.get() !== pin) {
        pinDone.set(pin);
        sim.modify((s) => {
          'worklet';
          replayTo(cfg, s.A, s.inp, s.ev, pin, totalCents);
          return s;
        });
        const A = sim.get().A;
        theta.set(xcoinAngle(clockOf(A), A[CTL + C_KICKAT]));
        tick.set(tick.get() + 1);
      }
      return;
    }
    const mode = uiMode.get();
    acc.set(acc.get() + dt);
    const steps = Math.min(Math.floor(acc.get() / DT), 8);
    acc.set(acc.get() - Math.floor(acc.get() / DT) * DT);
    if (steps <= 0) return;

    // what the finger is doing, for the steps of this frame
    const down = tDown.get();
    const fx = tX.get();
    const fy = tY.get();
    const slide = tSlide.get();
    let gx = 0;
    if (tilt && mode === 1) {
      // sideways gravity from the device's own gravity vector (iOS reports g, Android m/s²), with a dead zone
      const v = sensorValue.get();
      const mag = Math.sqrt(v.x * v.x + v.y * v.y + v.z * v.z);
      let g = mag > 2 ? v.x / 9.81 : v.x;
      if (Platform.OS === 'android') g = -g;
      g = Math.max(-GESTURE.tiltG, Math.min(GESTURE.tiltG, g));
      gx = Math.abs(g) < 0.04 ? 0 : g * 2600;
    }
    const command = cmd.get();
    cmd.set(0);
    let collected = 0;
    let cents = 0;
    let impact = 0;
    let flick = 0;
    let emptied = 0;
    let refilled = 0;
    let latch = 0;
    sim.modify((s) => {
      'worklet';
      const A = s.A;
      const inp = s.inp;
      const ev = s.ev;
      for (let k = 0; k < steps; k++) {
        if (mode === 0) {
          if (A[CTL + C_STEP] >= Math.round(LOOP / DT)) {
            initState(cfg, A, totalCents);
            for (let q = 0; q < EVENT_SIZE; q++) ev[q] = 0;
          }
          autoStep(cfg, A, inp, ev);
        } else {
          inp[IN_HOLD] = down === 1 ? 1 : 0;
          inp[IN_SLIDE] = slide;
          inp[IN_FINGER] = down === 2 ? 1 : 0;
          inp[IN_FX] = fx;
          inp[IN_FY] = fy;
          inp[IN_FR] = GESTURE.fingerRadius;
          inp[IN_GX] = mode === 1 ? gx : 0;
          inp[IN_REDUCED] = reduce ? 1 : 0;
          inp[IN_CMD] = k === 0 ? command : 0;
          if (reduce) reducedStep(cfg, A, inp, ev);
          else engineStep(cfg, A, inp, ev);
        }
        collected += ev[EV_COLLECTED];
        cents += ev[EV_CENTS];
        if (ev[EV_IMPACT] > impact) impact = ev[EV_IMPACT];
        flick += ev[EV_FLICK];
        emptied += ev[EVX_EMPTY];
        refilled += ev[EVX_REFILLED];
        if (ev[EVX_LATCH] !== 0) latch = ev[EVX_LATCH];
      }
      return s;
    });

    const A = sim.get().A;
    const clock = clockOf(A);
    const kick = A[CTL + C_KICKAT];

    if (mode === 0) {
      theta.set(reduce ? 0 : xcoinAngle(clock, kick));
    } else if (mode === 1) {
      theta.set(reduce ? 0 : xcoinAngle(clock, kick));
      if (down === 0) idleT.set(idleT.get() + dt);
      else idleT.set(0);
      const quiet = A[CTL + C_MODE] === MODE_LIVE && A[CTL + C_EMPTYT] < 0 && A[CTL + C_VALVE] < 0.01 && A[CTL + C_LATCH] < 0.5;
      if (autoPlay && !reduce && idleT.get() >= RESTORE_IDLE && quiet) {
        // two seconds without a touch: the coins glide (or rain) back to their places and autoplay resumes
        sim.modify((s) => {
          'worklet';
          beginReturn(cfg, s.A, false);
          return s;
        });
        const w = (2 * Math.PI) / XCOIN.turn;
        const start = xcoinAngle(clock, kick);
        th0.set(start);
        th1.set(xcoinTarget(start, w));
        restoreT.set(0);
        uiMode.set(2);
      }
    } else if (mode === 2) {
      restoreT.set(restoreT.get() + dt);
      theta.set(xcoinEase(th0.get(), th1.get(), (2 * Math.PI) / XCOIN.turn, restoreT.get() / XCOIN.restWindow));
      if (A[CTL + C_MODE] === MODE_LIVE && restoreT.get() > 0.2) {
        sim.modify((s) => {
          'worklet';
          initState(cfg, s.A, totalCents);
          return s;
        });
        theta.set(0);
        uiMode.set(0);
        acc.set(0);
        idleT.set(0);
      }
    }
    tick.set(tick.get() + 1);

    // what the host hears: only touches make cues, autoplay is silent
    if (mode !== 0 && !reduce) {
      cueClock.set(cueClock.get() + dt);
      const now = cueClock.get();
      if (collected > 0) {
        coinSeq.set(coinSeq.get() + collected);
        collectedAcc.set(collectedAcc.get() + cents);
        if (coinSeq.get() % GESTURE.coinTick < collected && now - lastCue.get() > GESTURE.hapticGap) {
          lastCue.set(now);
          scheduleOnRN(emit, CUE.coin, 0, 0);
        }
      }
      if (flick > 0) scheduleOnRN(emit, CUE.coin, 1, 0);
      if (impact > GESTURE.impactMin && now - lastCue.get() > GESTURE.clickGap) {
        lastCue.set(now);
        scheduleOnRN(emit, CUE.click, impact, 0);
      }
      if (latch !== 0) scheduleOnRN(emit, CUE.latch, latch, 0);
      if (collectedAcc.get() > 0 && (lastCb.get() < 0 || now - lastCb.get() > 0.1)) {
        scheduleOnRN(emit, CUE.collect, collectedAcc.get(), A[CTL + C_TARGET]);
        collectedAcc.set(0);
        lastCb.set(now);
      }
      if (emptied > 0) scheduleOnRN(emit, CUE.empty, A[CTL + C_TOTAL], 0);
      if (refilled > 0) scheduleOnRN(emit, CUE.refill, 0, 0);
    } else if (mode === 0) {
      coinSeq.set(0);
      collectedAcc.set(0);
    }
  }, false);

  useEffect(() => {
    frame.setActive(run);
    return () => frame.setActive(false);
  }, [run, frame]);

  // development only: pin the loop at t seconds, resume, read the state
  useEffect(() => {
    if (!__DEV__) return;
    const globals = globalThis as Record<string, unknown>;
    const previous = globals.__cpc;
    const hook = {
      pin: (t: number) => {
        uiMode.set(0);
        pinned.set(t);
        pinDone.set(-2);
      },
      at: (t: number) => {
        uiMode.set(0);
        pinned.set(t);
        pinDone.set(-2);
      },
      play: () => {
        pinned.set(-1);
        sim.modify((s) => {
          'worklet';
          initState(cfg, s.A, totalCents);
          return s;
        });
        acc.set(0);
        uiMode.set(0);
      },
      state: () => {
        const A = sim.get().A;
        return {
          t: clockOf(A),
          mode: A[CTL + C_MODE],
          cents: A[CTL + C_CENTS],
          valve: A[CTL + C_VALVE],
          press: A[CTL + C_PRESS],
          lit: A[CTL + C_LIT],
          bulge: A[CTL + C_BULGE],
          bulgeV: A[CTL + C_BULGEV],
          ui: uiMode.get(),
        };
      },
    };
    globals.__cpc = hook;
    return () => {
      if (globals.__cpc === hook) globals.__cpc = previous;
    };
  }, [cfg, totalCents, sim, uiMode, pinned, pinDone, acc]);

  const api = useMemo<HopperHandle>(
    () => ({
      pour: () => cmd.set(1),
      stop: () => cmd.set(2),
      refill: () => {
        // a refill is the rain: stop the valve and empty/refill through the engine
        cmd.set(3);
      },
    }),
    [cmd],
  );

  // the gesture: a thumb on the pill works the valve (hold, tap to latch, slide to throttle); a finger in the pile is a collider
  const pan = useMemo(() => {
    const pl = comp.pill;
    const S = scale;
    return Gesture.Pan()
      .minDistance(0)
      .enabled(touchable)
      .onBegin((e) => {
        'worklet';
        const x = e.x / S;
        const y = e.y / S;
        const onPill =
          x >= pl.x - GESTURE.hitSlopPill &&
          x <= pl.x + pl.w + GESTURE.hitSlopPill &&
          y >= pl.y - GESTURE.hitSlopPill &&
          y <= pl.y + pl.h + GESTURE.hitSlopPill;
        tDown.set(onPill ? 1 : 2);
        tX.set(x);
        tY.set(y);
        tSlide.set(Math.sqrt((x - (pl.x + pl.w / 2)) * (x - (pl.x + pl.w / 2)) + (y - (pl.y + pl.h / 2)) * (y - (pl.y + pl.h / 2))));
        idleT.set(0);
        if (uiMode.get() !== 1) uiMode.set(1);
        const A = sim.get().A;
        if (onPill && A[CTL + C_MODE] === MODE_LIVE && A[CTL + C_EMPTYT] < 0) scheduleOnRN(emit, CUE.press, 0, 0);
      })
      .onUpdate((e) => {
        'worklet';
        const x = e.x / S;
        const y = e.y / S;
        tX.set(x);
        tY.set(y);
        tSlide.set(Math.sqrt((x - (pl.x + pl.w / 2)) * (x - (pl.x + pl.w / 2)) + (y - (pl.y + pl.h / 2)) * (y - (pl.y + pl.h / 2))));
      })
      .onFinalize(() => {
        'worklet';
        tDown.set(0);
        tSlide.set(0);
        idleT.set(0);
      });
  }, [touchable, scale, tDown, tX, tY, tSlide, idleT, uiMode, sim, emit]);

  return { sim, theta, tick, pan, api, uiMode };
}

export { EVX_EMPTY, EVX_LATCH, EVX_REFILLED, IN_CMD };
