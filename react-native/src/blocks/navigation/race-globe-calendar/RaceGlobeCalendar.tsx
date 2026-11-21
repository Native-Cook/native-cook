import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useEffect, useMemo, useState } from 'react';
import { type LayoutChangeEvent, PixelRatio, StyleSheet, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import { cancelAnimation, useDerivedValue, useFrameCallback, useSharedValue, withDelay, withSpring, withTiming } from 'react-native-reanimated';
import {
  Canvas,
  createPicture,
  FilterMode,
  Group,
  ImageShader,
  MipmapMode,
  Path,
  Picture,
  Rect,
  Shader,
  type SkFont,
  Skia,
  useFont,
  useImage,
} from '@shopify/react-native-skia';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';

import { useReducedMotionPreference } from '../../../shared/useReducedMotionPreference';
import { COUNT, LAT, LON, ROUNDS, SELECTED_REST, SUN, vecOf, W } from './rgc-data';
import { type Ctx, drawOverlay, type View as OverlayView } from './rgc-draw';
import { FLAG_D, SHUFFLE_D, TROPHY_D } from './rgc-icons';
import {
  angle3,
  AUTO_TRACK,
  axisLon,
  camRows,
  clamp01,
  D,
  easeInOut,
  type Frame,
  frameAt,
  hopDip,
  LOOP,
  latOf,
  lonOf,
  mix,
  project,
  seg,
  slerp3,
  smooth,
  SPIN_V,
  spinAt,
  wrap180,
} from './rgc-motion';
import { GLOBE_FX } from './rgc-shaders';
import { C, G, SPRING, TYPE } from './rgc-theme';

/** Skia runtime effects are read as Display P3 on iOS: re-encode the sRGB colours there. */
const P3 = 1;
const PR = PixelRatio.get();
// Bilinear without mips: the longitude wraps behind a visible pole, and mip selection from screen derivatives drew a
// line there. Minification stays ≤ 2× at the whole-Earth zoom, under the atmosphere rim where it is largest.
const SAMPLING = { filter: FilterMode.Linear, mipmap: MipmapMode.None };
const DEG = Math.PI / 180;

/** 140 stars, seeded, in four brightness bins × two twinkle groups. */
const STARS = (() => {
  let s = 7;
  const rnd = () => {
    s = (s * 16807) % 2147483647;
    return s / 2147483647;
  };
  const bins: { d: string; a: number; g: number }[] = [];
  const parts: string[][] = [[], [], [], [], [], [], [], []];
  for (let i = 0; i < 140; i++) {
    const x = rnd() * 402;
    const y = rnd() * 874;
    const r = 0.5 + rnd() * 0.7;
    const b = Math.min(3, Math.floor(rnd() * 4));
    const g = rnd() < 0.5 ? 0 : 1;
    parts[b * 2 + g].push(`M${(x - r).toFixed(2)} ${y.toFixed(2)}a${r.toFixed(2)} ${r.toFixed(2)} 0 1 0 ${(2 * r).toFixed(2)} 0a${r.toFixed(2)} ${r.toFixed(2)} 0 1 0 ${(-2 * r).toFixed(2)} 0`);
  }
  for (let k = 0; k < 8; k++) bins.push({ d: parts[k].join(''), a: 0.15 + 0.15 * Math.floor(k / 2), g: k % 2 });
  return bins;
})();

/** Rotate a world vector about the polar axis (deg): moves the sun through the hours. */
const yawVec = (v: number[], deg: number) => {
  'worklet';
  const a = deg * DEG;
  const c = Math.cos(a);
  const s = Math.sin(a);
  return [v[0] * c + v[2] * s, v[1], -v[0] * s + v[2] * c];
};

type Flight = { a: number[]; b: number[]; sa: number[]; sb: number[]; za: number; hop: number; from: number; to: number };

type Props = {
  /** Plays the 10.5 s loop until a finger takes over (default true). */
  autoPlay?: boolean;
};

/**
 * A season calendar where the only navigation is one card: swipe it and the Earth turns to the next venue, flying
 * the great circle while the sun moves to that event's local start, so a night race arrives in the dark with the
 * city lit. Drag the globe to spin it (it snaps to the nearest venue), long-press and slide to scrub the hour.
 */
export function RaceGlobeCalendar({ autoPlay = true }: Props) {
  const reduced = useReducedMotionPreference();
  const [size, setSize] = useState<{ w: number; h: number } | null>(null);

  // textures: a 512 px set first, the full set when decoded (useImage decodes off the UI thread)
  const day512 = useImage(require('./assets/earth-day-512.jpg'));
  const night512 = useImage(require('./assets/earth-night-512.jpg'));
  const mask512 = useImage(require('./assets/earth-mask-512.png'));
  const dayHi = useImage(require('./assets/earth-day.jpg'));
  const nightHi = useImage(require('./assets/earth-night-2048.jpg'));
  const maskHi = useImage(require('./assets/earth-mask-2048.png'));
  const dayImg = dayHi ?? day512;
  const nightImg = nightHi ?? night512;
  const maskImg = maskHi ?? mask512;

  const fTitle = useFont(require('./assets/inter-display-semibold.ttf'), TYPE.title);
  const fDate = useFont(require('./assets/inter-regular.ttf'), TYPE.date);
  const fChip = useFont(require('./assets/inter-semibold.ttf'), TYPE.chip);
  const fName = useFont(require('./assets/inter-semibold.ttf'), TYPE.name);
  const fPlace = useFont(require('./assets/inter-regular.ttf'), TYPE.place);
  const fPin = useFont(require('./assets/inter-bold.ttf'), TYPE.pin);

  const ctx = useMemo<Ctx | null>(() => {
    if (!fTitle || !fDate || !fChip || !fName || !fPlace || !fPin) return null;
    // advance widths (getTextWidth measures ink and drops spaces)
    const adv = (f: SkFont, t: string) => {
      const w = f.getGlyphWidths(f.getGlyphIDs(t));
      let sum = 0;
      for (let k = 0; k < w.length; k++) sum += w[k];
      return sum;
    };
    const circ = (d: string) => Skia.Path.MakeFromSVGString(d) ?? Skia.PathBuilder.Make().build();
    const charW: number[] = [];
    for (let d = 0; d < 10; d++) charW.push(adv(fDate, String(d)));
    charW.push(adv(fDate, ':'));
    let digitW = 0;
    for (let d = 0; d < 10; d++) digitW = Math.max(digitW, charW[d]);
    return {
      fTitle,
      fDate,
      fChip,
      fName,
      fPlace,
      fPin,
      titles: ROUNDS.map((r) => r.title),
      chips: ROUNDS.map((r) => r.chip),
      wheres: ROUNDS.map((r) => r.where),
      pre: ROUNDS.map((r) => r.datePre),
      suf: ROUNDS.map((r) => r.dateSuf),
      hour: ROUNDS.map((r) => r.hour),
      chipW: ROUNDS.map((r) => adv(fChip, r.chip)),
      nameW: ROUNDS.map((r) => adv(fName, r.title)),
      placeW: ROUNDS.map((r) => adv(fPlace, r.where)),
      preW: ROUNDS.map((r) => adv(fDate, r.datePre)),
      pinW: ROUNDS.map((r) => adv(fPin, String(r.n))),
      digitW,
      colonW: charW[10],
      charW,
      circuits: ROUNDS.map((r) => circ(r.circuit)),
      shuffle: circ(SHUFFLE_D),
      trophy: circ(TROPHY_D),
      flag: circ(FLAG_D),
    };
  }, [fTitle, fDate, fChip, fName, fPlace, fPin]);

  const W0 = size?.w ?? 402;
  const S = W0 / 402;
  const CH = (size?.h ?? 874) / S;
  const cardTop = CH - (874 - G.card.y);

  // ---- clocks ----
  const T = useSharedValue(0);
  const auto = useSharedValue(autoPlay && !reduced ? 1 : 0);
  const pinned = useSharedValue(0);
  const rate = useSharedValue(1);
  const now = useSharedValue(0);
  const touching = useSharedValue(0);
  const lastTouch = useSharedValue(0);
  const stay = useSharedValue(0); // dev: no resume

  // ---- live state (a finger has taken over) ----
  const fl = useSharedValue<Flight>({ a: W[SELECTED_REST], b: W[SELECTED_REST], sa: SUN[SELECTED_REST], sb: SUN[SELECTED_REST], za: 1, hop: 0, from: SELECTED_REST, to: SELECTED_REST });
  const fu = useSharedValue(1);
  const cardPos = useSharedValue<number>(SELECTED_REST);
  const press = useSharedValue(0);
  const landClock = useSharedValue(9);
  const leaveClock = useSharedValue(9);
  const free = useSharedValue(0);
  const freeLat = useSharedValue(0);
  const freeLon = useSharedValue(0);
  const freeZ = useSharedValue(1);
  const freeSun = useSharedValue<number[]>(SUN[SELECTED_REST]);
  const vLat = useSharedValue(0);
  const vLon = useSharedValue(0);
  const hideCallout = useSharedValue(0);
  const coasting = useSharedValue(0);
  const hourOff = useSharedValue(0);
  const spinS = useSharedValue(-1);
  const spinFrom = useSharedValue<number[]>([0, 0, 0, 0, 0]); // lat, lon, sunx, suny, sunz
  const spinTo = useSharedValue(0);
  const spinDLon = useSharedValue(0);
  const spinCard0 = useSharedValue(0);
  const shufflePress = useSharedValue(0);
  const shuffleRot = useSharedValue(0);
  const pulse = useSharedValue(-1);
  const homing = useSharedValue(0);
  // gesture scratch
  const region = useSharedValue(0); // 0 globe, 1 card, 2 button
  const drag = useSharedValue(0); // 1 card pan, 2 globe pan, 3 hour scrub
  const p0 = useSharedValue(0);
  const tgt = useSharedValue(-1);
  const grabS = useSharedValue<number[]>([0, 0, 0, 0, 0, 0, 1, 0]); // cam xyz, sun xyz, zoom, departing round
  const g0 = useSharedValue<number[]>([0, 0]);
  const hour0 = useSharedValue(0);
  const lastDetent = useSharedValue(0);
  const lastCard = useSharedValue(SELECTED_REST);
  const halfCrossed = useSharedValue(0);

  useEffect(() => {
    auto.set(autoPlay && !reduced ? 1 : 0);
  }, [autoPlay, reduced, auto]);

  const buzz = (k: 'soft' | 'light' | 'medium' | 'rigid' | 'sel') => {
    if (k === 'sel') Haptics.selectionAsync().catch(() => {});
    else {
      const style = k === 'soft' ? Haptics.ImpactFeedbackStyle.Soft : k === 'light' ? Haptics.ImpactFeedbackStyle.Light : k === 'medium' ? Haptics.ImpactFeedbackStyle.Medium : Haptics.ImpactFeedbackStyle.Rigid;
      Haptics.impactAsync(style).catch(() => {});
    }
  };

  // ---- the frame in live mode ----
  const liveFrame = (): Frame => {
    'worklet';
    const f = fl.get();
    let lat = 0;
    let lon = 0;
    let zoom = 1;
    let sun = f.sb;
    let spin = 0;
    let numbered = 1;
    const ss = spinS.get();
    if (ss >= 0) {
      const sp = spinAt(ss);
      const s0 = spinFrom.get();
      const to = spinTo.get();
      lat = mix(s0[0], LAT[to], smooth(sp.p));
      lon = s0[1] + spinDLon.get() * sp.p;
      spin = spinDLon.get() * sp.v;
      zoom = 1 - 0.3 * (sp.v / SPIN_V);
      sun = slerp3([s0[2], s0[3], s0[4]], SUN[to], sp.p);
      numbered = ss < 1.4 ? 1 - seg(ss, 0, 0.2) : seg(ss, 1.4, 1.65);
    } else if (free.get() === 1) {
      lat = freeLat.get();
      lon = freeLon.get();
      zoom = freeZ.get();
      sun = freeSun.get();
    } else {
      const u = fu.get();
      const uc = clamp01(u);
      const c = slerp3(f.a, f.b, u);
      lat = latOf(c);
      lon = lonOf(c);
      zoom = mix(f.za, 1, uc) - 0.4 * Math.sin(Math.PI * uc) * hopDip(f.hop);
      sun = slerp3(f.sa, f.sb, uc);
    }
    const h = hourOff.get();
    if (Math.abs(h) > 1e-4) sun = yawVec(sun, -15 * h);
    const lc = landClock.get();
    const cp = cardPos.get();
    const nowT = now.get();
    return {
      lat,
      lon,
      zoom,
      sun,
      spin,
      cardPos: cp,
      track: 1,
      press: press.get(),
      arcPos: Math.min(Math.max(cp, 0), COUNT - 1),
      comet: 1,
      selF: ss >= 0 ? (ss < 0.3 ? f.from : f.to) : cp,
      numbered,
      from: f.from,
      leave: clamp01(leaveClock.get() / 0.3),
      to: f.to,
      land: lc >= 0 ? lc : -1,
      calloutOut: hideCallout.get(),
      sheen: lc >= 0.1 && lc <= 0.8 ? easeInOut(seg(lc, 0.1, 0.8)) : -1,
      sheenSlot: 0,
      hour: h,
      shuffle: shufflePress.get(),
      shuffleRot: shuffleRot.get(),
      pulse: pulse.get(),
      t: nowT % LOOP,
    };
  };

  const F = useDerivedValue<Frame>(() => {
    now.get(); // recomputed every frame: the live frame reads state through a helper
    if (auto.get() === 1) return frameAt(T.get());
    return liveFrame();
  });

  /** The current camera and base sun. liveFrame applies the hour scrub once when drawing. */
  const snapshot = () => {
    'worklet';
    const f = auto.get() === 1 ? frameAt(T.get()) : liveFrame();
    const sun = Math.abs(f.hour) > 1e-4 ? yawVec(f.sun, 15 * f.hour) : f.sun;
    return { c: vecOf(f.lat, f.lon), sun, zoom: f.zoom, f };
  };

  /** A finger takes over from autoplay: keep the current state, no jump. */
  const takeover = () => {
    'worklet';
    homing.set(0);
    if (auto.get() !== 1) return;
    const s = snapshot();
    const f = s.f;
    auto.set(0);
    // the running flight is frozen where it is; letting go completes it
    fl.set({ a: s.c, b: W[f.to], sa: s.sun, sb: SUN[f.to], za: s.zoom, hop: angle3(s.c, W[f.to]), from: f.from, to: f.to });
    fu.set(0);
    const k = Math.floor(f.cardPos);
    const fr = f.cardPos - k;
    const i0 = AUTO_TRACK[Math.min(k, AUTO_TRACK.length - 1)];
    const i1 = AUTO_TRACK[Math.min(k + 1, AUTO_TRACK.length - 1)];
    cardPos.set(Math.abs(i1 - i0) === 1 ? i0 + fr * (i1 - i0) : fr < 0.5 ? i0 : i1);
    lastCard.set(Math.round(cardPos.get()));
    landClock.set(f.land >= 0 ? f.land : -1);
    leaveClock.set(f.leave * 0.3);
    press.set(f.press);
    hideCallout.set(f.calloutOut);
    spinS.set(-1);
    free.set(0);
    pulse.set(-1);
  };

  const flyTo = (target: number, v0: number, cfg: { d: number; k: number }) => {
    'worklet';
    const s = snapshot();
    const cur = fl.get();
    // the pin that leaves is the one that had landed; a flight cut short keeps its original departure
    const landed = landClock.get() >= 0;
    const fromIdx = landed ? cur.to : cur.from;
    spinS.set(-1);
    free.set(0);
    fl.set({ a: s.c, b: W[target], sa: s.sun, sb: SUN[target], za: s.zoom, hop: angle3(s.c, W[target]), from: fromIdx, to: target });
    cancelAnimation(fu);
    landClock.set(-1);
    if (fromIdx === target) leaveClock.set(9);
    else if (landed) leaveClock.set(0);
    hideCallout.set(withTiming(0, { duration: 1 }));
    if (reduced) {
      fu.set(1);
      cardPos.set(withTiming(target, { duration: 200 }));
      return;
    }
    fu.set(0);
    fu.set(withSpring(1, { damping: cfg.d, stiffness: cfg.k, velocity: v0 }));
    cardPos.set(withSpring(target, { damping: SPRING.glide.d, stiffness: SPRING.glide.k }));
  };

  // ---- the clock ----
  useFrameCallback((info) => {
    const raw = (info.timeSincePreviousFrame ?? 16) / 1000;
    const dt = Math.min(raw, rate.get() < 1 ? 1 : 0.05) * rate.get();
    now.set(now.get() + dt);
    if (pinned.get() === 1) return;
    if (auto.get() === 1) {
      T.set((T.get() + dt) % LOOP);
      return;
    }
    if (landClock.get() >= 0) landClock.set(landClock.get() + dt);
    leaveClock.set(leaveClock.get() + dt);
    if (pulse.get() >= 0) pulse.set(pulse.get() > 1 ? -1 : pulse.get() + dt);

    // live shuffle spin
    const ss = spinS.get();
    if (ss >= 0) {
      const n = ss + dt;
      const to = spinTo.get();
      const c0 = spinCard0.get();
      cardPos.set(mix(c0, to, spinAt(Math.min(1.6, (n * 1.6) / 1.4)).p));
      const card = Math.round(cardPos.get());
      if (card !== lastCard.get()) {
        lastCard.set(card);
        if (now.get() - lastDetent.get() > 0.1) {
          lastDetent.set(now.get());
          scheduleOnRN(buzz, 'sel');
        }
      }
      if (n >= 1.6) {
        spinS.set(-1);
        const cur = fl.get();
        fl.set({ a: W[to], b: W[to], sa: SUN[to], sb: SUN[to], za: 1, hop: 0, from: cur.from, to });
        fu.set(1);
        cardPos.set(to);
        landClock.set(0);
        hideCallout.set(0);
        scheduleOnRN(buzz, 'medium');
        lastTouch.set(now.get());
      } else {
        spinS.set(n);
      }
      return;
    }

    // inertia after a free spin, then snap to the nearest venue within 12°
    if (free.get() === 1 && coasting.get() === 1 && drag.get() !== 2) {
      const k = Math.pow(0.998, dt * 1000);
      vLat.set(vLat.get() * k);
      vLon.set(vLon.get() * k);
      freeLon.set(freeLon.get() + vLon.get() * dt);
      freeLat.set(Math.min(Math.max(freeLat.get() + vLat.get() * dt, -70), 70));
      const sp = Math.sqrt(vLat.get() * vLat.get() + vLon.get() * vLon.get());
      if (sp < 8) {
        const cv = vecOf(freeLat.get(), freeLon.get());
        let best = -1;
        let bestA = 12;
        for (let i = 0; i < COUNT; i++) {
          const a = angle3(cv, W[i]);
          if (a < bestA) {
            bestA = a;
            best = i;
          }
        }
        vLat.set(0);
        vLon.set(0);
        coasting.set(0);
        if (best >= 0) {
          flyTo(best, 0, SPRING.drift);
          scheduleOnRN(buzz, 'sel');
        }
        lastTouch.set(now.get());
      }
    }

    // a flight's landing
    if (free.get() === 0 && landClock.get() < 0 && touching.get() === 0 && fu.get() >= 0.985) {
      landClock.set(0);
      hideCallout.set(0);
      scheduleOnRN(buzz, 'medium');
    }

    // 2 s after the last touch, fly home to R15, then restart the loop
    if (!reduced && autoPlay && stay.get() === 0 && touching.get() === 0 && now.get() - lastTouch.get() > 2) {
      const f = fl.get();
      const parked = free.get() === 1 && vLat.get() === 0 && vLon.get() === 0;
      const settled = (parked || (free.get() === 0 && fu.get() > 0.999)) && Math.abs(hourOff.get()) < 0.005 && Math.abs(cardPos.get() - Math.round(cardPos.get())) < 0.004;
      if (settled) {
        const atHome = !parked && f.to === SELECTED_REST && Math.abs(cardPos.get() - SELECTED_REST) < 0.004 && angle3(vecOf(latOf(slerp3(f.a, f.b, fu.get())), lonOf(slerp3(f.a, f.b, fu.get()))), W[SELECTED_REST]) < 0.05;
        if (atHome && landClock.get() > 1.6) {
          T.set(0);
          auto.set(1);
          homing.set(0);
        } else if (!atHome && homing.get() === 0) {
          homing.set(1);
          flyTo(SELECTED_REST, 0, SPRING.glide);
        } else if (atHome && landClock.get() < 0) {
          landClock.set(0);
        }
      }
    }
  });

  // ---- touch handlers (shared by the gestures and the dev hook) ----
  const regionAt = (x: number, y: number) => {
    'worklet';
    if (y > cardTop - 6 && y < cardTop + G.card.h + 6) return 1;
    return Math.hypot(x - G.shuffle.cx, y - G.shuffle.cy) < 30 ? 2 : 0;
  };
  const touchDown = (x: number, y: number) => {
    'worklet';
    pinned.set(0);
    takeover();
    touching.set(1);
    lastTouch.set(now.get());
    region.set(regionAt(x, y));
    if (region.get() === 1) {
      press.set(withSpring(1, { damping: SPRING.snap.d, stiffness: SPRING.snap.k }));
      scheduleOnRN(buzz, 'soft');
    }
    if (region.get() === 2) shufflePress.set(withSpring(1, { damping: SPRING.snap.d, stiffness: SPRING.snap.k }));
    // A touch freezes a running flight or shuffle where it is.
    if (spinS.get() >= 0 && region.get() !== 2) {
      const s = snapshot();
      const f = fl.get();
      fl.set({ a: s.c, b: W[f.to], sa: s.sun, sb: SUN[f.to], za: s.zoom, hop: angle3(s.c, W[f.to]), from: f.from, to: f.to });
      fu.set(0);
      spinS.set(-1);
    }
    if (free.get() === 1) {
      vLat.set(0);
      vLon.set(0);
      coasting.set(0);
    }
    if (fu.get() < 0.999 && region.get() !== 2) {
      cancelAnimation(fu);
      cancelAnimation(cardPos);
    }
  };
  const touchUp = () => {
    'worklet';
    if (touching.get() === 0 || drag.get() === 3) return;
    const ran = drag.get() === -1;
    drag.set(0);
    touching.set(0);
    lastTouch.set(now.get());
    press.set(withSpring(0, { damping: SPRING.snap.d, stiffness: SPRING.snap.k }));
    shufflePress.set(withSpring(0, { damping: SPRING.snap.d, stiffness: SPRING.snap.k }));
    // a card left between two rounds settles on the nearer one
    if (ran) return;
    if (free.get() === 0 && spinS.get() < 0 && Math.abs(cardPos.get() - Math.round(cardPos.get())) > 0.004) {
      flyTo(Math.round(cardPos.get()), 0, SPRING.weighty);
    } else if (free.get() === 0 && spinS.get() < 0 && fu.get() < 1) {
      fu.set(withSpring(1, { damping: SPRING.weighty.d, stiffness: SPRING.weighty.k }));
    }
  };

  const cardStart = () => {
    'worklet';
    drag.set(1);
    const s = snapshot();
    grabS.set([s.c[0], s.c[1], s.c[2], s.sun[0], s.sun[1], s.sun[2], s.zoom, landClock.get() >= 0 ? fl.get().to : fl.get().from]);
    p0.set(cardPos.get());
    tgt.set(-1);
    halfCrossed.set(0);
    hideCallout.set(withTiming(1, { duration: 100 }));
  };
  const cardMove = (dx: number) => {
    'worklet';
    const start = p0.get();
    let raw = start - dx / G.card.pitch;
    // rubber band past the first and last rounds
    if (raw < 0) raw = -(-raw / (1 + -raw / (120 / G.card.pitch)));
    if (raw > COUNT - 1) {
      const over = raw - (COUNT - 1);
      raw = COUNT - 1 + over / (1 + over / (120 / G.card.pitch));
    }
    cardPos.set(raw);
    const dir = raw >= start ? 1 : -1;
    let t = dir > 0 ? Math.floor(start + 1e-4) + 1 : Math.ceil(start - 1e-4) - 1;
    t = Math.min(Math.max(t, 0), COUNT - 1);
    const span = Math.abs(t - start);
    const u = span < 1e-4 ? 0 : clamp01(Math.abs(raw - start) / span);
    if (t !== tgt.get()) {
      tgt.set(t);
      const g = grabS.get();
      const a = [g[0], g[1], g[2]];
      if (landClock.get() >= 0) leaveClock.set(0);
      fl.set({ a, b: W[t], sa: [g[3], g[4], g[5]], sb: SUN[t], za: g[6], hop: angle3(a, W[t]), from: g[7], to: t });
      landClock.set(-1);
    }
    fu.set(u);
    if (u >= 0.5 && halfCrossed.get() === 0) {
      halfCrossed.set(1);
      scheduleOnRN(buzz, 'light');
    } else if (u < 0.5 && halfCrossed.get() === 1) halfCrossed.set(0);
    if ((raw < -0.05 || raw > COUNT - 0.95) && lastDetent.get() >= 0) {
      lastDetent.set(-1);
      scheduleOnRN(buzz, 'rigid');
    }
  };
  const cardEnd = (vx: number) => {
    'worklet';
    drag.set(-1);
    lastDetent.set(0);
    const v = -vx / G.card.pitch; // pages / s
    const t = tgt.get();
    const start = p0.get();
    if (t < 0) {
      flyTo(Math.round(cardPos.get()), 0, SPRING.weighty);
      return;
    }
    const back = Math.min(Math.max(t > start ? Math.ceil(start - 1e-4 - 1) + 1 : Math.floor(start + 1e-4 + 1) - 1, 0), COUNT - 1);
    const proj = cardPos.get() + v * 0.12;
    const final = Math.abs(proj - t) <= Math.abs(proj - back) ? t : back;
    if (final === t) {
      const span = Math.max(Math.abs(t - start), 0.05);
      if (reduced) {
        fu.set(1);
        cardPos.set(withTiming(t, { duration: 200 }));
      } else {
        fu.set(withSpring(1, { damping: SPRING.weighty.d, stiffness: SPRING.weighty.k, velocity: (v * Math.sign(t - start)) / span }));
        cardPos.set(withSpring(t, { damping: SPRING.glide.d, stiffness: SPRING.glide.k, velocity: v }));
      }
    } else {
      flyTo(final, 0, SPRING.weighty);
    }
  };

  const globeStart = () => {
    'worklet';
    drag.set(2);
    const s = snapshot();
    freeLat.set(latOf(s.c));
    freeLon.set(lonOf(s.c));
    freeZ.set(s.zoom);
    freeSun.set(s.sun);
    g0.set([freeLat.get(), freeLon.get()]);
    free.set(1);
    vLat.set(0);
    vLon.set(0);
    hideCallout.set(withTiming(1, { duration: 120 }));
  };
  const globeMove = (dx: number, dy: number) => {
    'worklet';
    const z = Math.max(freeZ.get(), 0.4);
    const g = g0.get();
    freeLon.set(g[1] - (dx * 0.19) / z);
    freeLat.set(Math.min(Math.max(g[0] + (dy * 0.19) / z, -70), 70));
  };
  const globeEnd = (vx: number, vy: number) => {
    'worklet';
    drag.set(-1);
    const z = Math.max(freeZ.get(), 0.4);
    vLon.set((-vx * 0.19) / z);
    vLat.set((vy * 0.19) / z);
    coasting.set(1);
    if (reduced) {
      vLon.set(0);
      vLat.set(0);
    }
  };

  const scrubStart = () => {
    'worklet';
    drag.set(3);
    cancelAnimation(hourOff);
    hour0.set(hourOff.get());
    scheduleOnRN(buzz, 'soft');
  };
  const scrubMove = (dx: number) => {
    'worklet';
    const before = hourOff.get();
    const h = Math.min(Math.max(hour0.get() + dx / 12, -12), 12);
    hourOff.set(h);
    if (Math.floor(before) !== Math.floor(h) && now.get() - lastDetent.get() > 0.1) {
      lastDetent.set(now.get());
      scheduleOnRN(buzz, 'sel');
    }
  };
  const scrubEnd = () => {
    'worklet';
    drag.set(-1);
    hourOff.set(withDelay(2000, withTiming(0, { duration: 900 })));
  };

  const shuffle = () => {
    'worklet';
    takeover();
    const s = snapshot();
    const cur = fl.get().to;
    // a random round: the clock's fractional digits stand in for a random source
    const r = now.get() * 7919.37;
    let to = Math.min(COUNT - 2, Math.floor((r - Math.floor(r)) * (COUNT - 1)));
    if (to >= cur) to += 1;
    const lat0 = latOf(s.c);
    const lon0 = lonOf(s.c);
    shuffleRot.set(withSpring(shuffleRot.get() + 360, { damping: SPRING.snap.d, stiffness: SPRING.snap.k }));
    if (reduced) {
      flyTo(to, 0, SPRING.glide);
      return;
    }
    // westward, more than a full turn
    const west = ((lon0 - LON[to]) % 360 + 360) % 360;
    spinFrom.set([lat0, lon0, s.sun[0], s.sun[1], s.sun[2]]);
    spinTo.set(to);
    spinDLon.set(-(360 + west));
    spinCard0.set(cardPos.get());
    cancelAnimation(cardPos);
    cancelAnimation(fu);
    free.set(0);
    hideCallout.set(withTiming(1, { duration: 100 }));
    const f = fl.get();
    const landed = landClock.get() >= 0;
    fl.set({ ...f, from: landed ? f.to : f.from, to });
    if (landed) leaveClock.set(0);
    landClock.set(-1);
    spinS.set(0);
  };

  const tapAt = (x: number, y: number) => {
    'worklet';
    if (region.get() === 2) {
      drag.set(-1);
      shuffle();
      return;
    }
    if (region.get() !== 0) return;
    const f = liveFrame();
    const R = G.globe.r * f.zoom;
    const m = camRows(f.lat, f.lon);
    // the callout re-strokes its circuit
    const sel = project(m, W[f.to], G.globe.cx, G.globe.cy, R);
    if (f.land >= 0.4 && Math.abs(x - sel.x) < 110 && y < sel.y - 38 && y > sel.y - 110) {
      pulse.set(0);
      scheduleOnRN(buzz, 'light');
      return;
    }
    let best = -1;
    let bestD = 24;
    for (let i = 0; i < COUNT; i++) {
      const q = project(m, W[i], G.globe.cx, G.globe.cy, R);
      if (q.vis < 0.5 || q.z < 0.3) continue;
      const dd = Math.hypot(q.x - x, q.y - 20 - y);
      if (dd < bestD) {
        bestD = dd;
        best = i;
      }
    }
    if (best >= 0 && best !== f.to) {
      flyTo(best, 0, SPRING.weighty);
      drag.set(-1);
      scheduleOnRN(buzz, 'light');
    }
  };

  // ---- gestures ----
  const pan = Gesture.Pan()
    .minDistance(6)
    .onBegin((e) => {
      touchDown(e.x / S, e.y / S);
    })
    .onStart(() => {
      if (region.get() === 1) cardStart();
      else if (region.get() === 0) globeStart();
    })
    .onUpdate((e) => {
      if (drag.get() === 1) cardMove(e.translationX / S);
      else if (drag.get() === 2) globeMove(e.translationX / S, e.translationY / S);
    })
    .onEnd((e, success) => {
      if (drag.get() === 1) cardEnd(success ? e.velocityX / S : 0);
      else if (drag.get() === 2) globeEnd(success ? e.velocityX / S : 0, success ? e.velocityY / S : 0);
    })
    .onFinalize(() => {
      touchUp();
    });
  const scrub = Gesture.Pan()
    .activateAfterLongPress(380)
    .onStart((e) => {
      const x = e.x / S;
      const y = e.y / S;
      if (regionAt(x, y) === 0 && y < cardTop - 10) {
        if (touching.get() === 0) touchDown(x, y);
        scrubStart();
      }
    })
    .onUpdate((e) => {
      if (drag.get() === 3) scrubMove(e.translationX / S);
    })
    .onFinalize(() => {
      if (drag.get() !== 3) return;
      scrubEnd();
      touchUp();
    });
  const tap = Gesture.Tap()
    .maxDuration(320)
    .onEnd((e, success) => {
      if (!success) return;
      const x = e.x / S;
      const y = e.y / S;
      if (touching.get() === 0) touchDown(x, y);
      tapAt(x, y);
      touchUp();
    });
  const gesture = Gesture.Race(pan, scrub, tap);

  // ---- dev hook ----
  useEffect(() => {
    if (!__DEV__) return;
    const g = globalThis as { __rgc?: unknown };
    g.__rgc = {
      at: (t: number) => {
        pinned.set(1);
        auto.set(1);
        T.set(t);
      },
      play: () => {
        scheduleOnUI(() => {
          'worklet';
          pinned.set(0);
          auto.set(1);
          T.set(0);
        });
      },
      rate: (r: number) => {
        rate.set(r);
      },
      stay: (on: number) => {
        stay.set(on);
      },
      state: () => ({ T: T.get(), auto: auto.get(), pinned: pinned.get(), cardPos: cardPos.get(), fu: fu.get(), to: fl.get().to, free: free.get(), hour: hourOff.get(), spin: spinS.get(), land: landClock.get(), touching: touching.get(), homing: homing.get(), vLat: vLat.get(), vLon: vLon.get(), stay: stay.get(), idle: now.get() - lastTouch.get(), drag: drag.get() }),
      // touch stand-ins (argent's touch injection does not reach the app on this machine); run on the UI thread
      down: (x: number, y: number) => {
        scheduleOnUI(() => {
          'worklet';
          pinned.set(0);
          touchDown(x, y);
        });
      },
      card: (dx: number) => {
        scheduleOnUI(() => {
          'worklet';
          if (drag.get() !== 1) cardStart();
          cardMove(dx);
        });
      },
      cardUp: (vx: number) => {
        scheduleOnUI(() => {
          'worklet';
          cardEnd(vx);
          touchUp();
        });
      },
      globe: (dx: number, dy: number) => {
        scheduleOnUI(() => {
          'worklet';
          if (drag.get() !== 2) globeStart();
          globeMove(dx, dy);
        });
      },
      globeUp: (vx: number, vy: number) => {
        scheduleOnUI(() => {
          'worklet';
          globeEnd(vx, vy);
          touchUp();
        });
      },
      scrub: (dx: number) => {
        scheduleOnUI(() => {
          'worklet';
          if (drag.get() !== 3) {
            touching.set(1);
            scrubStart();
          }
          scrubMove(dx);
        });
      },
      scrubUp: () => {
        scheduleOnUI(() => {
          'worklet';
          scrubEnd();
          touchUp();
        });
      },
      tap: (x: number, y: number) => {
        scheduleOnUI(() => {
          'worklet';
          pinned.set(0);
          touchDown(x, y);
          tapAt(x, y);
          touchUp();
        });
      },
    };
    return () => {
      delete g.__rgc;
    };
  });

  // ---- drawing ----
  const view = useDerivedValue<OverlayView>(() => {
    const f = F.get();
    return { m: camRows(f.lat, f.lon), cx: G.globe.cx, cy: G.globe.cy, R: G.globe.r * f.zoom, cardTop };
  });
  const dSz = useMemo(() => (dayImg ? [dayImg.width(), dayImg.height()] : [1, 1]), [dayImg]);
  const nSz = useMemo(() => (nightImg ? [nightImg.width(), nightImg.height()] : [1, 1]), [nightImg]);
  const mSz = useMemo(() => (maskImg ? [maskImg.width(), maskImg.height()] : [1, 1]), [maskImg]);
  const gSz = useMemo(() => (night512 ? [night512.width(), night512.height()] : [1, 1]), [night512]);
  const px = 1 / (S * PR);
  const globeU = useDerivedValue(() => {
    const f = F.get();
    const v = view.get();
    const m = v.m;
    const latR = f.lat * DEG;
    const blurCap = 20 / (v.R * Math.max(Math.cos(latR), 0.3));
    return {
      uP3: P3,
      uC: [v.cx, v.cy],
      uR: v.R,
      uDist: D,
      uM0: [m[0], m[1], m[2]],
      uM1: [m[3], m[4], m[5]],
      uM2: [m[6], m[7], m[8]],
      uSun: f.sun,
      uLonC: wrap180(axisLon(m) / DEG) * DEG,
      uBlur: Math.min((Math.abs(f.spin) * DEG) / 120, blurCap),
      uTwk: (2 * Math.PI * 7 * f.t) / LOOP,
      uPx: px,
      uDaySz: dSz,
      uNightSz: nSz,
      uMaskSz: mSz,
      uGlowSz: gSz,
    };
  });
  const bounds = useMemo(() => Skia.XYWHRect(0, 0, 402, CH), [CH]);
  const overlay = useDerivedValue(() => {
    const f = F.get();
    const v = view.get();
    return createPicture((c) => {
      if (ctx) drawOverlay(c, f, v, ctx);
    }, bounds);
  });
  const twA = useDerivedValue(() => 0.8 + 0.2 * Math.sin((2 * Math.PI * 4 * F.get().t) / LOOP));
  const twB = useDerivedValue(() => 0.8 + 0.2 * Math.sin((2 * Math.PI * 4 * F.get().t) / LOOP + 2.1));

  const onLayout = (e: LayoutChangeEvent) => setSize({ w: e.nativeEvent.layout.width, h: e.nativeEvent.layout.height });
  const ready = size && dayImg && nightImg && maskImg && night512 && ctx;

  return (
    <GestureDetector gesture={gesture}>
      <View style={styles.root} onLayout={onLayout}>
        <StatusBar style="light" />
        {ready && (
          <Canvas style={{ width: W0, height: size.h }} pointerEvents="none">
            <Group transform={[{ scale: S }]}>
              <Rect x={0} y={0} width={402} height={CH} color={C.space} />
              <Group opacity={twA}>
                {STARS.filter((s) => s.g === 0).map((s, k) => (
                  <Path key={k} path={s.d} color="#FFFFFF" opacity={s.a} />
                ))}
              </Group>
              <Group opacity={twB}>
                {STARS.filter((s) => s.g === 1).map((s, k) => (
                  <Path key={k} path={s.d} color="#FFFFFF" opacity={s.a} />
                ))}
              </Group>
              <Rect x={0} y={0} width={402} height={CH}>
                <Shader source={GLOBE_FX} uniforms={globeU}>
                  <ImageShader image={dayImg} tx="repeat" ty="clamp" fit="none" rect={{ x: 0, y: 0, width: dSz[0], height: dSz[1] }} sampling={SAMPLING} />
                  <ImageShader image={nightImg} tx="repeat" ty="clamp" fit="none" rect={{ x: 0, y: 0, width: nSz[0], height: nSz[1] }} sampling={SAMPLING} />
                  <ImageShader image={maskImg} tx="repeat" ty="clamp" fit="none" rect={{ x: 0, y: 0, width: mSz[0], height: mSz[1] }} sampling={SAMPLING} />
                  <ImageShader image={night512} tx="repeat" ty="clamp" fit="none" rect={{ x: 0, y: 0, width: gSz[0], height: gSz[1] }} sampling={SAMPLING} />
                </Shader>
              </Rect>
              <Picture picture={overlay} />
            </Group>
          </Canvas>
        )}
      </View>
    </GestureDetector>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: C.space, overflow: 'hidden' },
});
