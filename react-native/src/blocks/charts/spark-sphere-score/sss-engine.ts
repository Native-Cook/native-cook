/**
 * Spark Sphere Score — the engine: one frame callback (UI thread) that turns the pace `v` into every channel the sphere and the
 * screen read. Three modes of the same state:
 *   AUTO    (autoPlay, nobody touching): every channel is a closed-form function of the loop time (the script), so any frame can
 *           be pinned and the loop closes exactly;
 *   DRAG / RETURN / REST: `v` is written by a finger or a spring; the lagged paces, the swirl and the agitation are integrated
 *           with the same step function the script's table was built with.
 * A touch while AUTO plays hands the script's values to the live state without a jump; 2 s after the last touch, once the
 * state is back at rest, the loop resumes at its next boundary of the free-running clock (so the periodic parts do not jump).
 */
import { useMemo } from 'react';
import { useFrameCallback, useSharedValue, type SharedValue } from 'react-native-reanimated';
import {
  clamp,
  DEFAULT_PALETTE,
  discUniforms,
  FRAME_REST,
  GESTURE,
  LAG,
  LOOP,
  scriptAgit,
  scriptGrab,
  scriptPulseAge,
  scriptSwirl,
  scriptV,
  stepStir,
  T,
  V,
  type PaletteTable,
  type SssFrame,
} from './sss-motion';

export const PH = { AUTO: 0, DRAG: 1, RETURN: 2, REST: 3 } as const;

export type SssEngineOptions = {
  /** the pace; written here in AUTO and when pinned, by the gesture / spring otherwise */
  v: SharedValue<number>;
  /** the reading the sphere returns to (x) */
  reading: SharedValue<number>;
  autoPlay: boolean;
  reduceMotion: boolean;
  /** disc centre (canvas pt), pt per design unit, device px per pt, 1 on iOS (Display P3 output) */
  geo: { cx: number; cy: number; scale: number; px: number; p3: number };
  palette?: PaletteTable;
  /** membrane pulses per 8 s loop; 8 is 60 bpm */
  beats?: number;
  /** the pace the first frame shows (the reading); default the pin's 0.8 */
  initialV?: number;
};

export function useSphereEngine(o: SssEngineOptions) {
  const { v, reading, autoPlay, reduceMotion, geo, palette } = o;
  const beats = Math.max(1, Math.round(o.beats ?? 8));
  const v0 = o.initialV ?? V.rest;
  const pal = palette ?? DEFAULT_PALETTE;
  const phase = useSharedValue<number>(autoPlay && !reduceMotion ? PH.AUTO : PH.REST);
  const time = useSharedValue(0);
  const cyc = useSharedValue(0); // seconds, 0 … LOOP, free running
  const touchedAt = useSharedValue(-99);
  const pinT = useSharedValue(-1); // ≥ 0: the loop is frozen at this time
  const pinV = useSharedValue(-99); // > −50: the pace is frozen here, every lag snapped
  const prevV = useSharedValue<number>(v0);
  const vRim = useSharedValue<number>(v0);
  const vHol = useSharedValue<number>(v0);
  const vFilm = useSharedValue<number>(v0);
  const vCard = useSharedValue<number>(v0);
  const vScore = useSharedValue<number>(v0);
  const grab = useSharedValue(0);
  const stir = useSharedValue(0);
  const swirl = useSharedValue(0);
  const agit = useSharedValue(0);
  const stirIn = useSharedValue(0);
  const probeX = useSharedValue(0);
  const probeY = useSharedValue(0);
  const probeHeat = useSharedValue(0);
  const dimple = useSharedValue(0);
  const dimpleAngle = useSharedValue(0);
  const pulseT0 = useSharedValue(-99);
  const tapT0 = useSharedValue(-99);
  const pulseAge = useSharedValue(99);
  const tapAge = useSharedValue(99);
  // a valid record from the first render, so the shader never sees a missing uniform
  const discU = useSharedValue<Record<string, number | number[]>>(
    discUniforms({ ...FRAME_REST, vRim: v0, vHol: v0, vFilm: v0, cx: geo.cx, cy: geo.cy, scale: geo.scale, px: geo.px, p3: geo.p3 }, pal),
  );

  useFrameCallback((frame) => {
    'worklet';
    const dt = clamp((frame.timeSincePreviousFrame ?? 16.7) / 1000, 0.001, 0.05);
    const now = time.get() + dt;
    time.set(now);
    let c = cyc.get() + dt;
    let wrapped = false;
    if (c >= LOOP) {
      c -= LOOP;
      wrapped = true;
    }
    cyc.set(c);
    const pinned = pinT.get() >= 0;
    const pinnedV = pinV.get() > -50;
    let ph = phase.get();
    if (reduceMotion && ph === PH.AUTO) ph = PH.REST;

    if (pinned || (ph === PH.AUTO && autoPlay)) {
      const tl = pinned ? pinT.get() : c;
      const vv = scriptV(tl);
      v.set(vv);
      vRim.set(scriptV(tl - LAG.rim));
      vHol.set(scriptV(tl - LAG.hollow));
      vFilm.set(scriptV(tl - LAG.film));
      vCard.set(scriptV(tl - LAG.card));
      vScore.set(scriptV(tl - LAG.score));
      grab.set(scriptGrab(tl));
      swirl.set(scriptSwirl(tl));
      agit.set(scriptAgit(tl));
      stir.set(0);
      prevV.set(vv);
      pulseAge.set(scriptPulseAge(tl));
      tapAge.set(tl >= T.tap ? tl - T.tap : 99);
      probeHeat.set(0);
      dimple.set(0);
    } else if (pinnedV) {
      const vv = pinV.get();
      v.set(vv);
      vRim.set(vv);
      vHol.set(vv);
      vFilm.set(vv);
      vCard.set(vv);
      vScore.set(vv);
      prevV.set(vv);
    } else {
      const vv = v.get();
      const pv = prevV.get();
      const vd = (vv - pv) / dt;
      prevV.set(vv);
      vRim.set(vRim.get() + (vv - vRim.get()) * (1 - Math.exp(-dt / LAG.rim)));
      vHol.set(vHol.get() + (vv - vHol.get()) * (1 - Math.exp(-dt / LAG.hollow)));
      vFilm.set(vFilm.get() + (vv - vFilm.get()) * (1 - Math.exp(-dt / LAG.film)));
      vCard.set(vCard.get() + (vv - vCard.get()) * (1 - Math.exp(-dt / LAG.card)));
      vScore.set(vScore.get() + (vv - vScore.get()) * (1 - Math.exp(-dt / LAG.score)));
      const extra = stirIn.get();
      stirIn.set(extra * Math.exp(-dt / 0.9));
      const st = [stir.get(), swirl.get(), agit.get()];
      stepStir(st, vv, reduceMotion ? 0 : vd, dt, reduceMotion ? 0 : extra);
      stir.set(st[0]);
      swirl.set(reduceMotion ? 0 : st[1]);
      agit.set(reduceMotion ? 0 : st[2]);
      // a crossing of 1.0x: this frame's pace on one side, the previous frame's on the other
      if ((pv - 1) * (vv - 1) < 0) pulseT0.set(now);
      pulseAge.set(now - pulseT0.get());
      tapAge.set(now - tapT0.get());
      // back in AUTO once the state is at rest, 2 s after the last touch, at the next loop boundary
      if (autoPlay && !reduceMotion && ph !== PH.DRAG && ph !== PH.AUTO && wrapped) {
        const idle = now - touchedAt.get();
        const settled =
          Math.abs(vv - reading.get()) < 0.004 &&
          Math.abs(vd) < 0.05 &&
          Math.abs(swirl.get()) < 0.03 &&
          probeHeat.get() < 0.02 &&
          grab.get() < 0.02;
        if (idle > GESTURE.idleResume && settled) {
          phase.set(PH.AUTO);
          cyc.set(0);
          swirl.set(0);
          agit.set(0);
          stir.set(0);
          stirIn.set(0);
          c = 0;
        }
      } else if (ph === PH.RETURN && Math.abs(vv - reading.get()) < 0.004 && Math.abs(vd) < 0.05) {
        phase.set(PH.REST);
      }
    }

    const tf = pinned ? pinT.get() : c;
    const frameRec: SssFrame = {
      vRim: vRim.get(),
      vHol: vHol.get(),
      vFilm: vFilm.get(),
      cyc: tf / LOOP,
      swirl: swirl.get(),
      agit: agit.get(),
      probeX: probeX.get(),
      probeY: probeY.get(),
      probeHeat: probeHeat.get(),
      dimple: dimple.get(),
      dimpleAngle: dimpleAngle.get(),
      pulseAge: pulseAge.get(),
      tapAge: tapAge.get(),
      reduceMotion,
      beats,
      cx: geo.cx,
      cy: geo.cy,
      scale: geo.scale,
      px: geo.px,
      p3: geo.p3,
    };
    discU.set(discUniforms(frameRec, pal));
  });

  // the shared values never change identity, so the bundle is stable: gestures built from it are not rebuilt on every render
  return useMemo(
    () => ({
      phase,
      time,
      cyc,
      touchedAt,
      pinT,
      pinV,
      prevV,
      vRim,
      vHol,
      vFilm,
      vCard,
      vScore,
      grab,
      stir,
      swirl,
      agit,
      stirIn,
      probeX,
      probeY,
      probeHeat,
      dimple,
      dimpleAngle,
      pulseT0,
      tapT0,
      pulseAge,
      tapAge,
      discU,
    }),
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [],
  );
}
export type SssEngine = ReturnType<typeof useSphereEngine>;
