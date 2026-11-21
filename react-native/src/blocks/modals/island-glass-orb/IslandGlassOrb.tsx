import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useEffect, useMemo, useState } from 'react';
import { type LayoutChangeEvent, PixelRatio, StyleSheet, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import { type SharedValue, useDerivedValue, useFrameCallback, useSharedValue, withSpring, withTiming } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import {
  Blur,
  Canvas,
  Circle,
  drawAsImage,
  FilterMode,
  Group,
  Image,
  ImageShader,
  interpolateColors,
  LinearGradient,
  MipmapMode,
  Paint,
  Paragraph,
  Path,
  Rect,
  RoundedRect,
  Shader,
  Skia,
  type SkImage,
  type SkParagraph,
  useFonts,
  useImage,
  vec,
} from '@shopify/react-native-skia';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';

import { useReducedMotionPreference } from '../../../shared/useReducedMotionPreference';
import {
  BLEND_KEYS,
  clamp01,
  easeOut,
  type Frame,
  frameAt,
  LOOP,
  liveFrameAt,
  mix,
  sampleAmpAt,
  sequenceFrameAt,
  seg,
  shapesAt,
  smooth,
} from './igo-motion';
import { makePageText, makeSvgs, PageTree, para, topFor } from './igo-page';
import { ISLAND_FX, RELIGHT_FX } from './igo-shaders';
import { ART_TOP, artworkAt, C, COPY, FAMILY, FAMILY_DISPLAY, G, SPRING, TIMES } from './igo-theme';

/** Skia runtime effects are read as Display P3 on iOS: the shader re-encodes its sRGB constants. */
const P3 = 1;

const AUTO = 0;
const LIVE = 1;
const SEQ = 2;

const R_NONE = 0;
const R_ISLAND = 1;
const R_DROP = 2;
const R_BANNER = 3;
const R_PILL = 4;

/** Where the free lens is: over nothing, the Nori widget, the Calendar widget or the ring (a selection tick on every crossing). */
const Z_NONE = 0;
const Z_NORI = 1;
const Z_CAL = 2;
const Z_RING = 3;
const zoneAt = (x: number, y: number, art: ReturnType<typeof artworkAt>) => {
  'worklet';
  if (x > G.wL.x && x < G.wL.x + G.wL.w && y > G.wL.y && y < G.wL.y + G.wL.h) return Z_NORI;
  if (x > G.wR.x && x < G.wR.x + G.wR.w && y > G.wR.y && y < G.wR.y + G.wR.h) return Z_CAL;
  const rx = ((x - art.x) / art.scale - 201) / 160;
  const ry = ((y - art.y) / art.scale - 490) / 215;
  if (rx * rx + ry * ry < 1) return Z_RING;
  return Z_NONE;
};

const SAMPLING = { filter: FilterMode.Linear, mipmap: MipmapMode.None };

const WIFI = Skia.Path.MakeFromSVGString('M324.64 25.64A9 9 0 0 1 337.36 25.64M326.97 27.97A5.7 5.7 0 0 1 335.03 27.97');

type Props = {
  /** Plays the 10 s loop until a finger takes over (default true). */
  autoPlay?: boolean;
  /** Voice level 0–1 while listening (from your microphone meter); without it the finger on the drop drives it. */
  level?: number;
  /** The answer the banner speaks. */
  reply?: string;
  /** How long the reply stays without a touch before it collapses (ms, default 6000). */
  replyHoldMs?: number;
  onListen?: () => void;
  onSubmit?: () => void;
  onCancel?: () => void;
  onReplyEnd?: () => void;
};

/**
 * A voice assistant that lives in the Dynamic Island: long-press the island and it pours out into a drop of liquid
 * glass that refracts the screen under it and carries a spectrum line driven by the voice; it tightens into a
 * thinking pill, then widens into a banner that speaks the answer and lights up what it is about.
 */
export function IslandGlassOrb({ autoPlay = true, level, reply = COPY.reply, replyHoldMs = 6000, onListen, onSubmit, onCancel, onReplyEnd }: Props) {
  const reduced = useReducedMotionPreference();
  const insets = useSafeAreaInsets();
  const [size, setSize] = useState<{ w: number; h: number } | null>(null);
  const fonts = useFonts({
    [FAMILY]: [
      require('./assets/fonts/Inter-Regular.ttf'),
      require('./assets/fonts/Inter-Medium.ttf'),
      require('./assets/fonts/Inter-SemiBold.ttf'),
    ],
    [FAMILY_DISPLAY]: [require('./assets/fonts/Inter-Display-Light.ttf')],
  });
  const wall = useImage(require('./assets/wallpaper.jpg'));
  const ringMatte = useImage(require('./assets/igo-ring-matte.png'));
  const ringSpec = useImage(require('./assets/igo-ring-spec.png'));
  const floorMatte = useImage(require('./assets/igo-floor-matte.png'));
  const [page, setPage] = useState<{ img: SkImage; blur: SkImage } | null>(null);

  const W = size?.w ?? 402;
  const S = W / 402;
  const Hc = (size?.h ?? 874) / S;
  const art = artworkAt(Hc);
  // the island is the hardware cut-out: on Dynamic Island phones it sits 11 pt from the top; elsewhere the pill is
  // drawn just under the status bar
  const oy = insets.top >= 54 || insets.top === 0 ? 0 : (insets.top + 4) / S - G.island.y;

  const text = useMemo(() => (fonts ? makePageText(fonts) : null), [fonts]);
  const svgs = useMemo(() => makeSvgs(), []);
  const words = useMemo(() => {
    if (!fonts) return [];
    const parts = reply.split(/\s+/).filter(Boolean);
    const run = (t: string) => para(fonts, [{ text: t, size: 24, weight: 400, track: -0.013 }]);
    const space = run('a a').getLongestLine() - 2 * run('a').getLongestLine();
    let x: number = G.reply.x;
    let cy: number = G.reply.y;
    return parts.map((w, i) => {
      const p = run(w);
      if (x > G.reply.x && (x + p.getLongestLine() > 357 || /[.!?]$/.test(parts[i - 1]))) {
        x = G.reply.x;
        cy += 29;
      }
      const item = { p, x, cy };
      x += p.getLongestLine() + space;
      return item;
    });
  }, [fonts, reply]);

  // ---- the page, drawn once into an image the glass can refract ----
  useEffect(() => {
    if (!wall || !text || !size) return;
    let alive = true;
    const scale = S * PixelRatio.get();
    (async () => {
      const img = await drawAsImage(
        <Group transform={[{ scale }]}>
          <PageTree wall={wall} text={text} svgs={svgs} H={Hc} />
        </Group>,
        { width: Math.round(402 * scale), height: Math.round(Hc * scale) },
      );
      if (!img) return;
      const blur = await drawAsImage(
        <Image image={img} x={0} y={0} width={402} height={Hc} fit="fill">
          <Blur blur={18} mode="clamp" />
        </Image>,
        { width: 402, height: G.shaderH },
      );
      if (alive && blur) setPage({ img, blur });
    })();
    return () => {
      alive = false;
    };
  }, [wall, text, svgs, size, S, Hc]);

  // ---- clocks and live state ----
  const T = useSharedValue(0);
  const mode = useSharedValue(autoPlay && !reduced ? AUTO : LIVE);
  const pinned = useSharedValue(0);
  const rate = useSharedValue(1);
  const now = useSharedValue(0);
  const clockT = useSharedValue(0);
  const refr = useSharedValue(1);
  const levelSV = useSharedValue(-1);
  const holdS = useSharedValue(replyHoldMs / 1000);
  const autoSV = useSharedValue(autoPlay ? 1 : 0);
  const reducedSV = useSharedValue(reduced ? 1 : 0);

  // live values (a finger or the live flow owns them)
  const lM = useSharedValue(0);
  const lPill = useSharedValue(0);
  const lBan = useSharedValue(0);
  const lDx = useSharedValue(0);
  const lDy = useSharedValue(0);
  const lAmp = useSharedValue(0);
  const lAmpLag = useSharedValue(0);
  const lCaustic = useSharedValue(0);
  const lE = useSharedValue(0);
  const lBroken = useSharedValue(0); // 1 from the pinch until the bead has merged again
  const lPinchAt = useSharedValue(-10);
  const lMergeAt = useSharedValue(-10);
  const lRow = useSharedValue(0);
  const zone = useSharedValue(Z_NONE);
  const takeoverAudit = useSharedValue<{ before: Frame; after: Frame } | null>(null);
  const ampHistory = useSharedValue<{ t: number; a: number }[]>([]);
  const lMic = useSharedValue(0);
  const lSpin = useSharedValue(0);
  const lPluckX = useSharedValue(0);
  const lPluckD = useSharedValue(0);
  const listening = useSharedValue(0);
  const silentFor = useSharedValue(0);
  const pluckRel = useSharedValue(-1);
  const pluckD0 = useSharedValue(0);

  // the sequence (thinking → reply → rest) played live, with the hand-over blended out over 350 ms
  const seqT = useSharedValue(0);
  const seqStart = useSharedValue(0);
  const seqBlend = useSharedValue<number[]>(BLEND_KEYS.map(() => 0));
  const seqFrom = useSharedValue<Frame>(frameAt(0));
  const replyAt = useSharedValue(-1);

  // touch
  const touching = useSharedValue(0);
  const lastTouch = useSharedValue(-10);
  const region = useSharedValue(R_NONE);
  const downT = useSharedValue(0);
  const moved = useSharedValue(0);
  const grab = useSharedValue(0);
  const grab0 = useSharedValue([0, 0]);
  const grabT = useSharedValue([0, 0]);
  const lastTr = useSharedValue([0, 0]);
  const plucking = useSharedValue(0);
  const lineSide = useSharedValue(0);
  const lastSel = useSharedValue(0);
  const scrub = useSharedValue(0);
  const fired = useSharedValue(0);

  useEffect(() => {
    autoSV.set(autoPlay ? 1 : 0);
    reducedSV.set(reduced ? 1 : 0);
    if (!autoPlay || reduced) {
      if (mode.get() === AUTO) mode.set(LIVE);
    }
  }, [autoPlay, reduced, autoSV, reducedSV, mode]);
  useEffect(() => {
    levelSV.set(level === undefined ? -1 : Math.min(Math.max(level, 0), 1));
  }, [level, levelSV]);
  useEffect(() => {
    holdS.set(replyHoldMs / 1000);
  }, [replyHoldMs, holdS]);

  // ---- JS side: haptics and callbacks ----
  const buzz = (k: 'soft' | 'light' | 'sel' | 'rigid' | 'medium') => {
    if (k === 'sel') Haptics.selectionAsync().catch(() => {});
    else {
      const style = { soft: Haptics.ImpactFeedbackStyle.Soft, light: Haptics.ImpactFeedbackStyle.Light, rigid: Haptics.ImpactFeedbackStyle.Rigid, medium: Haptics.ImpactFeedbackStyle.Medium }[k];
      Haptics.impactAsync(style).catch(() => {});
    }
  };
  const emit = (k: 'listen' | 'submit' | 'cancel' | 'replyEnd') => {
    if (k === 'listen') onListen?.();
    else if (k === 'submit') onSubmit?.();
    else if (k === 'cancel') onCancel?.();
    else onReplyEnd?.();
  };

  // ---- springs (a 200 ms linear morph under reduced motion) ----
  const spr = (to: number, cfg: { d: number; k: number }, velocity = 0) => {
    'worklet';
    if (reducedSV.get() === 1) return withTiming(to, { duration: 200 });
    return withSpring(to, { damping: cfg.d, stiffness: cfg.k, mass: 1, velocity });
  };

  const liveNow = (): Frame => {
    'worklet';
    return liveFrameAt({
      m: lM.get(),
      pill: lPill.get(),
      ban: lBan.get(),
      dx: lDx.get(),
      dy: lDy.get(),
      amp: lAmp.get(),
      ampLag: lAmpLag.get(),
      causticAmp: lCaustic.get(),
      e: lE.get(),
      mic: lMic.get(),
      spin: lSpin.get(),
      pluckX: lPluckX.get(),
      pluckD: lPluckD.get(),
      broken: lBroken.get(),
      ts: now.get() - lPinchAt.get(),
      tm: lMergeAt.get() < 0 ? -1 : now.get() - lMergeAt.get(),
      row: lRow.get(),
      clockT: clockT.get(),
    });
  };

  const seqFrame = (): Frame => {
    'worklet';
    return sequenceFrameAt(seqT.get(), seqStart.get(), seqFrom.get(), seqBlend.get(), reducedSV.get() === 1);
  };

  const autoFrame = (t: number): Frame => {
    'worklet';
    const frame = frameAt(t);
    if (art.scale === 1) return frame;
    const cy = G.drop.cy + frame.dy;
    const x = (G.drop.cx + frame.dx - 201) * (art.scale - 1) * smooth(250, 300, cy);
    const y = Math.max(0, cy - ART_TOP) * (art.scale - 1);
    const m = clamp01(frame.m);
    return { ...frame, dx: frame.dx + x, dy: frame.dy + y, lineX: frame.lineX + x * m, lineY: frame.lineY + y * m };
  };

  // ---- the frame being drawn ----
  const F = useDerivedValue<Frame>(() => {
    const md = mode.get();
    if (md === AUTO) return autoFrame(T.get());
    // Read shared inputs here: Reanimated cannot discover dependencies hidden inside helper closures.
    if (md === SEQ) return sequenceFrameAt(seqT.get(), seqStart.get(), seqFrom.get(), seqBlend.get(), reducedSV.get() === 1);
    return liveFrameAt({
      m: lM.get(), pill: lPill.get(), ban: lBan.get(), dx: lDx.get(), dy: lDy.get(),
      amp: lAmp.get(), ampLag: lAmpLag.get(), causticAmp: lCaustic.get(), e: lE.get(),
      mic: lMic.get(), spin: lSpin.get(), pluckX: lPluckX.get(), pluckD: lPluckD.get(), broken: lBroken.get(), ts: now.get() - lPinchAt.get(),
      tm: lMergeAt.get() < 0 ? -1 : now.get() - lMergeAt.get(), row: lRow.get(), clockT: clockT.get(),
    });
  });
  const lobeT = useDerivedValue(() => (mode.get() === AUTO ? T.get() : mode.get() === SEQ ? seqT.get() : clockT.get()));

  // ---- state changes (UI thread) ----
  const setLiveFrom = (f: Frame) => {
    'worklet';
    lM.set(f.m);
    lPill.set(f.pill);
    lBan.set(f.ban);
    lDx.set(f.dx);
    lDy.set(f.dy);
    lAmp.set(f.amp);
    lAmpLag.set(f.ampLag);
    lCaustic.set(f.causticAmp);
    lE.set(f.e);
    ampHistory.set([
      { t: now.get() - 0.08, a: f.causticAmp },
      { t: now.get() - 0.06, a: f.ampLag },
      { t: now.get(), a: f.amp },
    ]);
    lMic.set(f.mic);
    lSpin.set(f.spinA);
    lPluckX.set(f.pluckX);
    lPluckD.set(0);
    lRow.set(0);
    lMergeAt.set(-10);
    // mid tear-off: carry the break with it (the bead is free from the pinch until it has merged again)
    const broken = f.neck < 0.05 && Math.hypot(f.dx, f.dy) > 150 ? 1 : 0;
    lBroken.set(broken);
    lPinchAt.set(broken ? now.get() - TIMES.pinchDur * 2 : -10);
    zone.set(Z_NONE);
  };
  const toIdle = () => {
    'worklet';
    lM.set(spr(0, SPRING.snap));
    lPill.set(spr(0, SPRING.snap));
    lBan.set(spr(0, SPRING.glide));
    lDx.set(spr(0, SPRING.glide));
    lDy.set(spr(0, SPRING.glide));
    lE.set(spr(0, SPRING.glide));
    lRow.set(spr(0, SPRING.glide));
    lMic.set(withTiming(0, { duration: 150 }));
    lSpin.set(withTiming(0, { duration: 150 }));
    listening.set(0);
  };
  const startListening = () => {
    'worklet';
    lM.set(spr(1, SPRING.pop));
    lE.set(spr(0, SPRING.pop));
    lPill.set(spr(0, SPRING.snap));
    lBan.set(spr(0, SPRING.pop));
    lMic.set(withTiming(1, { duration: 150 }));
    lSpin.set(withTiming(0, { duration: 150 }));
    listening.set(1);
    silentFor.set(0);
    mode.set(LIVE);
    scheduleOnRN(buzz, 'soft');
    scheduleOnRN(emit, 'listen');
  };
  const submit = () => {
    'worklet';
    const lf = liveNow();
    const f0 = frameAt(TIMES.arrive);
    const blend: number[] = [];
    for (let i = 0; i < BLEND_KEYS.length; i++) blend.push(lf[BLEND_KEYS[i]] - f0[BLEND_KEYS[i]]);
    seqBlend.set(blend);
    seqFrom.set(lf);
    seqT.set(TIMES.arrive);
    seqStart.set(TIMES.arrive);
    replyAt.set(-1);
    listening.set(0);
    mode.set(SEQ);
    scheduleOnRN(emit, 'submit');
  };
  const cancel = () => {
    'worklet';
    setLiveFrom(seqFrame());
    mode.set(LIVE);
    toIdle();
    scheduleOnRN(buzz, 'light');
    scheduleOnRN(emit, 'cancel');
  };
  const collapse = () => {
    'worklet';
    lBan.set(spr(0, SPRING.glide));
    lPill.set(0);
    lM.set(0);
    scheduleOnRN(emit, 'replyEnd');
  };

  /** A finger takes over from the loop: keep what it shows. */
  const takeOver = () => {
    'worklet';
    pinned.set(0);
    if (mode.get() !== AUTO) return;
    const t = T.get();
    const f = autoFrame(t);
    if (t >= TIMES.arrive && t < 9.3 + TIMES.shift) {
      seqT.set(t);
      seqStart.set(t);
      seqBlend.set(new Array<number>(BLEND_KEYS.length).fill(0));
      seqFrom.set(f);
      replyAt.set(t >= 6 + TIMES.shift ? now.get() - (t - 6 - TIMES.shift) : -1);
      mode.set(SEQ);
      return;
    }
    setLiveFrom(f);
    clockT.set(t);
    mode.set(LIVE);
    if (t >= 0.62 && t < TIMES.arrive) {
      listening.set(1);
      silentFor.set(0);
      lM.set(spr(1, SPRING.pop));
      lE.set(spr(0, SPRING.pop));
      // a bead that has torn off flies home; one still on its thread is drawn back (honey)
      const home = lBroken.get() === 1 ? SPRING.fly : SPRING.honey;
      lDx.set(spr(0, home));
      lDy.set(spr(0, home));
      lMic.set(withTiming(1, { duration: 150 }));
    } else {
      toIdle();
    }
  };

  useFrameCallback((info) => {
    const raw = (info.timeSincePreviousFrame ?? 16) / 1000;
    const dt = Math.min(raw, rate.get() < 1 ? 1 : 0.05) * rate.get();
    now.set(now.get() + dt);
    if (pinned.get() === 1) return;
    const md = mode.get();
    if (md === AUTO) {
      T.set((T.get() + dt) % LOOP);
      return;
    }
    if (reducedSV.get() === 0) clockT.set((clockT.get() + dt) % LOOP);
    const t = now.get();

    // long-press: the island (or the banner) held still for 220 ms
    if (touching.get() === 1 && fired.get() === 0 && moved.get() < 10 && t - downT.get() >= 0.22) {
      const r = region.get();
      if (r === R_ISLAND || r === R_BANNER) {
        fired.set(1);
        if (r === R_BANNER) lM.set(0);
        startListening();
        if (r === R_ISLAND) {
          grab.set(1);
          grab0.set([0, 0]);
          grabT.set(lastTr.get());
          lDx.set(0);
          lDy.set(0);
          lE.set(spr(0, SPRING.honey));
        }
      } else if (r === R_DROP && plucking.get() === 0 && grab.get() === 0) {
        fired.set(1);
        grab.set(1);
        grab0.set([lDx.get(), lDy.get()]);
        grabT.set(lastTr.get());
        lDx.set(lDx.get());
        lDy.set(lDy.get());
      }
    }

    // the tear-off: past 250 pt the thread pinches (Rigid); once it has flown home the bead merges (Medium)
    if (mode.get() === LIVE && lM.get() > 0.5) {
      const travel = Math.hypot(lDx.get(), lDy.get());
      if (lBroken.get() === 0 && travel >= G.tear.travel) {
        lBroken.set(1);
        lPinchAt.set(t);
        lMergeAt.set(-10);
        lE.set(-0.07);
        scheduleOnRN(buzz, 'rigid');
      } else if (lBroken.get() === 1 && grab.get() === 0 && travel < 30) {
        lBroken.set(0);
        lMergeAt.set(t);
        scheduleOnRN(buzz, 'medium');
      }
      // the lens over a widget or the ring: a selection tick on every crossing; over Calendar the row is pre-lit
      if (lBroken.get() === 1) {
        const z = zoneAt(G.drop.cx + lDx.get(), G.drop.cy + lDy.get(), art);
        if (z !== zone.get()) {
          zone.set(z);
          if (z !== Z_NONE && touching.get() === 1) scheduleOnRN(buzz, 'sel');
          lRow.set(spr(z === Z_CAL ? 0.7 : 0, SPRING.pop));
        }
      } else if (zone.get() !== Z_NONE) {
        zone.set(Z_NONE);
        lRow.set(spr(0, SPRING.glide));
      }
    }

    // the voice: the level prop, or 0.6 while a finger plucks the line; 60 ms attack, 180 ms release
    if (listening.get() === 1) {
      const lv = levelSV.get();
      const target = plucking.get() === 1 ? 0.6 : lv >= 0 ? lv : 0;
      const a = lAmp.get();
      const tau = target > a ? 0.06 : 0.18;
      lAmp.set(a + (target - a) * (1 - Math.exp(-dt / tau)));
      if (lAmp.get() < 0.08 && touching.get() === 0) silentFor.set(silentFor.get() + dt);
      else silentFor.set(0);
      if (silentFor.get() >= 1.2 && lM.get() > 0.9 && lBroken.get() === 0 && Math.hypot(lDx.get(), lDy.get()) < 20) submit();
    } else if (md === LIVE) {
      lAmp.set(lAmp.get() * Math.exp(-dt / 0.18));
    }

    // Sample the same voice history at fixed delays; autoplay uses the identical 60/80 ms offsets.
    if (mode.get() === LIVE) {
      // Loops keep React Compiler from hoisting callbacks to the JS runtime.
      const previous = ampHistory.get();
      const history: { t: number; a: number }[] = [];
      for (let i = 0; i < previous.length; i++) {
        if (previous[i].t >= t - 0.2) history.push(previous[i]);
      }
      history.push({ t, a: lAmp.get() });
      ampHistory.set(history);
      lAmpLag.set(sampleAmpAt(history, t - 0.06, lAmp.get()));
      lCaustic.set(sampleAmpAt(history, t - 0.08, lAmp.get()));
    }

    // the plucked line rings at 6 Hz for 400 ms
    if (pluckRel.get() >= 0) {
      const tau = t - pluckRel.get();
      if (tau > 0.5) {
        pluckRel.set(-1);
        lPluckD.set(0);
      } else {
        lPluckD.set(pluckD0.get() * Math.cos(2 * Math.PI * 6 * tau) * Math.exp(-tau / 0.11));
      }
    }

    if (mode.get() === SEQ) {
      let s = seqT.get() + dt;
      if (s >= 6.0 + TIMES.shift && replyAt.get() < 0) replyAt.set(t);
      if (s > 8.9 && seqT.get() <= 8.9 && t - Math.max(replyAt.get(), lastTouch.get()) < holdS.get()) s = 8.9;
      seqT.set(s);
      if (s >= LOOP) {
        setLiveFrom(frameAt(0));
        mode.set(LIVE);
        lastTouch.set(t);
        scheduleOnRN(emit, 'replyEnd');
      }
      return;
    }

    // a banner left open by a scrub collapses after the hold
    if (listening.get() === 0 && touching.get() === 0 && lBan.get() > 0.5 && t - Math.max(lastTouch.get(), replyAt.get()) > holdS.get()) {
      collapse();
      lastTouch.set(t);
    }
    // 2 s after the last touch, at rest: the loop starts again
    const idle = Math.abs(lM.get()) < 0.01 && Math.abs(lBan.get()) < 0.01 && Math.abs(lPill.get()) < 0.01 && Math.abs(lDx.get()) + Math.abs(lDy.get()) < 0.5 && lSpin.get() < 0.01;
    if (autoSV.get() === 1 && reducedSV.get() === 0 && touching.get() === 0 && listening.get() === 0 && lBan.get() < 0.01 && t - lastTouch.get() > 2) {
      if (idle) {
        T.set(0);
        mode.set(AUTO);
      } else {
        toIdle();
        lastTouch.set(t - 1.4); // look again in 0.6 s
      }
    }
  });

  // ---- touch ----
  const toC = (x: number, y: number) => {
    'worklet';
    return { x: x / S, y: y / S };
  };
  const frameNow = (): Frame => {
    'worklet';
    const md = mode.get();
    if (md === AUTO) return autoFrame(T.get());
    if (md === SEQ) return seqFrame();
    return liveNow();
  };
  const regionAt = (x: number, y: number) => {
    'worklet';
    const f = frameNow();
    const { box, drop } = shapesAt(f, oy);
    const md = mode.get();
    const inBox = x > box.x - 10 && x < box.x + box.w + 10 && y > box.y - 10 && y < box.y + box.h + 10;
    const inIsland = Math.abs(x - 201) < 85 && y < G.island.y + oy + 60;
    const qx = (x - drop.cx) / (drop.a + 12);
    const qy = (y - drop.cy) / (drop.b + 12);
    const inDrop = f.m > 0.4 && qx * qx + qy * qy < 1;
    if (md === SEQ) {
      const s = seqT.get();
      if (s >= TIMES.arrive && s < 5.7 + TIMES.shift && inBox) return R_PILL;
      if (s >= 6.9 + TIMES.shift && s < 8.7 + TIMES.shift && inBox) return R_BANNER;
      return R_NONE;
    }
    if (listening.get() === 1 && (inDrop || (inIsland && lBroken.get() === 0))) return R_DROP;
    if (lBan.get() > 0.5 && inBox) return R_BANNER;
    if (lM.get() < 0.3 && lBan.get() < 0.3 && inIsland) return R_ISLAND;
    return R_NONE;
  };

  const pan = Gesture.Pan()
    .minDistance(0)
    .shouldCancelWhenOutside(false)
    .onBegin((e) => {
      takeOver();
      touching.set(1);
      lastTouch.set(now.get());
      downT.set(now.get());
      moved.set(0);
      fired.set(0);
      plucking.set(0);
      scrub.set(0);
      lastTr.set([0, 0]);
      const q = toC(e.x, e.y);
      let r = regionAt(q.x, q.y);
      if (r === R_BANNER && mode.get() === SEQ) {
        // the banner is the finger's now
        setLiveFrom(seqFrame());
        lPill.set(0);
        lM.set(0);
        mode.set(LIVE);
      }
      if (r === R_DROP && Math.abs(lDx.get()) + Math.abs(lDy.get()) > 3) {
        // touch-down during the wobble grabs the drop where it is
        grab.set(1);
        fired.set(1);
        grab0.set([lDx.get(), lDy.get()]);
        grabT.set([0, 0]);
        lDx.set(lDx.get());
        lDy.set(lDy.get());
      }
      region.set(r);
      const f = frameNow();
      lineSide.set(q.y > f.lineY ? 1 : -1);
    })
    .onUpdate((e) => {
      lastTr.set([e.translationX / S, e.translationY / S]);
      moved.set(Math.max(moved.get(), Math.hypot(e.translationX, e.translationY)));
      const q = toC(e.x, e.y);
      const r = region.get();
      if (grab.get() === 1) {
        // the drop follows the finger 1:1 (down to y 700); past 250 pt the thread pinches (frame callback)
        const g0 = grab0.get();
        const gt = grabT.get();
        const ax = Math.min(Math.max(G.drop.cx + g0[0] + e.translationX / S - gt[0], 40), 362);
        const ay = Math.min(Math.max(G.drop.cy + g0[1] + e.translationY / S - gt[1], G.drop.cy - 10), 700);
        lDx.set(ax - G.drop.cx);
        lDy.set(ay - G.drop.cy);
        lE.set(lBroken.get() === 1 ? 0 : -0.07 * clamp01(Math.hypot(ax - G.drop.cx, ay - G.drop.cy) / G.tear.travel));
        return;
      }
      if (r === R_DROP && moved.get() > 6) {
        plucking.set(1);
        fired.set(1);
        const f = F.get();
        lPluckX.set(q.x);
        lPluckD.set(Math.min(Math.max(q.y - f.lineY, -18), 18));
        pluckRel.set(-1);
        const side = q.y > f.lineY ? 1 : -1;
        if (side !== lineSide.get() && now.get() - lastSel.get() > 0.06) {
          lastSel.set(now.get());
          scheduleOnRN(buzz, 'sel');
        }
        lineSide.set(side);
        return;
      }
      if (r === R_BANNER && moved.get() > 6) {
        const dy = e.translationY / S;
        if (dy > 24 && fired.get() === 0) {
          fired.set(1);
          lM.set(0);
          startListening();
          return;
        }
        if (fired.get() === 0 && dy < 0) {
          scrub.set(1);
          lBan.set(Math.min(Math.max(1 + dy / 83, 0), 1));
        }
      }
    })
    .onFinalize((e, success) => {
      const r = region.get();
      if (grab.get() === 1) {
        grab.set(0);
        const vx = success ? e.velocityX / S : 0;
        const vy = success ? e.velocityY / S : 0;
        // released: a torn-off bead flies home (d 16, k 210), one still on its thread is drawn back (honey)
        const home = lBroken.get() === 1 ? SPRING.fly : SPRING.honey;
        lDx.set(spr(0, home, vx));
        lDy.set(spr(0, home, vy));
        lE.set(spr(0, SPRING.honey));
        if (success && lBroken.get() === 0) scheduleOnRN(buzz, 'soft');
      } else if (plucking.get() === 1) {
        plucking.set(0);
        pluckD0.set(lPluckD.get());
        pluckRel.set(now.get());
      } else if (r === R_BANNER && fired.get() === 0 && scrub.get() === 1) {
        const v = success ? e.velocityY / S / 83 : 0;
        if (!success || lBan.get() + v * 0.12 >= 0.5) {
          lBan.set(spr(1, SPRING.glide));
          replyAt.set(now.get());
        } else {
          collapse();
          scheduleOnRN(buzz, 'light');
        }
      }
      touching.set(0);
      lastTouch.set(now.get());
      region.set(R_NONE);
    });
  const tap = Gesture.Tap()
    .maxDuration(250)
    .maxDistance(10)
    .onEnd((e, success) => {
      if (!success || fired.get() === 1 || scrub.get() === 1) return;
      const q = toC(e.x, e.y);
      const r = regionAt(q.x, q.y);
      if (r === R_DROP && listening.get() === 1 && lBroken.get() === 0) {
        scheduleOnRN(buzz, 'light');
        submit();
      } else if (r === R_PILL) {
        cancel();
      } else if (r === R_BANNER) {
        collapse();
        scheduleOnRN(buzz, 'light');
      }
    });
  const gesture = Gesture.Simultaneous(pan, tap);

  // dev only: drive the live flow without a finger (the batch simulator cannot inject touches)
  useEffect(() => {
    if (!__DEV__) return;
    const g = globalThis as { __igoAct?: unknown };
    g.__igoAct = (k: 'listen' | 'pull' | 'release' | 'submit' | 'cancel' | 'collapse' | 'relisten' | 'takeover' | 'voice' | 'pluck' | 'scrub' | 'reduced', x = 0, y = 0) => {
      scheduleOnUI(() => {
        'worklet';
        pinned.set(0);
        lastTouch.set(now.get());
        if (k === 'takeover') {
          const before = frameNow();
          takeOver();
          takeoverAudit.set({ before, after: frameNow() });
          touching.set(1);
        } else if (k === 'voice') levelSV.set(clamp01(x));
        else if (k === 'reduced') {
          reducedSV.set(x);
          autoSV.set(x === 1 ? 0 : autoPlay ? 1 : 0);
          setLiveFrom(frameAt(0));
          clockT.set(0);
          mode.set(LIVE);
          listening.set(0);
          touching.set(0);
        } else if (k === 'pluck') {
          touching.set(1);
          plucking.set(1);
          lPluckX.set(x || G.drop.cx);
          lPluckD.set(Math.min(Math.max(y, -18), 18));
        } else if (k === 'scrub') {
          takeOver();
          if (mode.get() === SEQ) setLiveFrom(seqFrame());
          mode.set(LIVE);
          lM.set(0);
          lPill.set(0);
          lBan.set(clamp01(x));
          lastTouch.set(now.get());
        } else if (k === 'listen') {
          takeOver();
          startListening();
        } else if (k === 'pull') {
          touching.set(1);
          // a finger drawing the drop to (x, y) from its rest (default: the tear-off point 250 pt down)
          lDx.set(x || 1);
          lDy.set(y || G.tear.travel);
          lE.set(lBroken.get() === 1 ? 0 : -0.07 * clamp01(Math.hypot(x || 1, y || G.tear.travel) / G.tear.travel));
        } else if (k === 'release') {
          touching.set(0);
          lastTouch.set(now.get());
          if (plucking.get() === 1) {
            plucking.set(0);
            pluckD0.set(lPluckD.get());
            pluckRel.set(now.get());
          }
          lE.set(spr(0, SPRING.honey));
          lDx.set(spr(0, lBroken.get() === 1 ? SPRING.fly : SPRING.honey));
          lDy.set(spr(0, lBroken.get() === 1 ? SPRING.fly : SPRING.honey));
        } else if (k === 'submit') submit();
        else if (k === 'cancel') cancel();
        else if (k === 'collapse') {
          if (mode.get() === SEQ) setLiveFrom(seqFrame());
          mode.set(LIVE);
          collapse();
        }
        else if (k === 'relisten') {
          setLiveFrom(frameNow());
          mode.set(LIVE);
          startListening();
        }
      });
    };
    return () => {
      delete g.__igoAct;
    };
  });

  // ---- shader uniforms ----
  const uniforms = useDerivedValue(() => {
    const f = F.get();
    const { box, drop } = shapesAt(f, oy);
    const mc = clamp01(f.m);
    const ban = clamp01(f.ban);
    const neckA = [201, G.island.y + oy + 26];
    // the thread: thinnest in the middle (Ø f.neck), a little wider at both ends; none once it has pinched
    const midR = (f.neck / 2) * mc;
    return {
      uP3: P3,
      uBox: [box.x, box.y, box.w, box.h],
      uDrop: [drop.cx, drop.cy, Math.max(drop.a, 1), Math.max(drop.b, 1)],
      uN: drop.n,
      uM: Math.max(f.m, 0),
      uK: f.kJoin * smooth(0, 0.25, f.m),
      uNeck: [neckA[0], neckA[1], midR > 0.05 ? midR * 1.2 + 1 : 0, midR],
      uCap: f.cap,
      uFree: f.free,
      uBan: ban,
      uAmp: f.amp,
      uAmpLag: f.ampLag,
      uLine: [f.lineX, f.lineY + oy, f.lineLen * (1 + 0.08 * f.amp), 3],
      uT: lobeT.get(),
      uPluck: [f.pluckX, f.pluckD],
      uArc: [f.arcA, f.arcW, f.arcHue],
      uRefr: refr.get(),
      uRim: [0.3 * mc * mc, Math.max(0.88 * mc, 0.25 * clamp01(f.pill) * (1 - ban), 0.45 * ban)],
      // shadow α / offset, caustic α; the free lens: shadow [0, 10, 26, α 0.22], caustic 80 × 14 at α 0.28
      uShade: [mix(0.25 * mc, 0.22 * mc, f.free) + 0.22 * ban, mix(mix(6, 8, ban), 10, f.free), mix((0.1 + 0.25 * clamp01(f.causticAmp)) * mc, 0.28 * (0.85 + 0.3 * clamp01(f.causticAmp)) * mc, f.free)],
      uShade2: [mix(mix(18, 24, ban), 26, f.free), mix(30, 40, f.free), mix(5, 7, f.free)],
    };
  });

  // ---- live overlays: light through the mattes (colour layer in the reply's band, additive layer always) ----
  const relightColor = useDerivedValue(() => {
    const f = F.get();
    return { uP3: P3, uBand: [f.bandY, f.bandA, f.floorY, f.floorA], uHue: [f.bandHue, f.floorHue, 0, clamp01(f.pill) * (1 - clamp01(f.ban))], uT: lobeT.get(), uAmp: clamp01(f.causticAmp) };
  });
  const relightLight = useDerivedValue(() => {
    const f = F.get();
    return { uP3: P3, uBand: [f.bandY, f.bandA, f.floorY, f.floorA], uHue: [f.bandHue, f.floorHue, 1, clamp01(f.pill) * (1 - clamp01(f.ban))], uT: lobeT.get(), uAmp: clamp01(f.causticAmp) };
  });
  // the colour layer is only drawn while the band (or the floor behind it) is lit
  const relightRect = useDerivedValue(() => {
    const f = F.get();
    return { x: 0, y: 230, width: 402, height: f.bandA + f.floorA > 0.001 ? 520 : 0 };
  });

  const clockA = useDerivedValue(() => F.get().clock);
  const micA = useDerivedValue(() => F.get().mic);
  const spinA = useDerivedValue(() => F.get().spinA);
  const spinBlur = useDerivedValue(() => F.get().spinBlur);
  const spinX = useDerivedValue(() => shapesAt(F.get(), oy).box.x + 30);
  const spinY = G.spinner.y + oy;
  const rimLA = useDerivedValue(() => F.get().rimL);
  const rimRA = useDerivedValue(() => F.get().rimR);
  const rowM = useDerivedValue(() => {
    const s = 1 + 0.04 * F.get().row;
    return [{ translateX: G.row.x }, { translateY: 190 }, { scale: s }, { translateX: -G.row.x }, { translateY: -190 }];
  });
  const barCol = useDerivedValue(() => interpolateColors(clamp01(F.get().row), [0, 1], ['#F7F7F5', C.cyan]));
  const rangeA = useDerivedValue(() => mix(0.6, 1, clamp01(F.get().row)));

  useEffect(() => {
    if (!__DEV__) return;
    const g = globalThis as { __igo?: unknown };
    g.__igo = {
      at: (t: number) => { pinned.set(1); mode.set(AUTO); T.set(t); },
      play: () => { pinned.set(0); mode.set(AUTO); },
      rate: (r: number) => { rate.set(r); },
      refr: (v: number) => { refr.set(v); },
      state: () => ({ now: now.get(), clockT: clockT.get(), rate: rate.get(), level: levelSV.get(), touching: touching.get(), T: T.get(), mode: mode.get(), pinned: pinned.get(), seqT: seqT.get(), m: lM.get(), ban: lBan.get(), listening: listening.get(), reduced: reducedSV.get(), takeover: takeoverAudit.get(), frame: F.get(), shapes: shapesAt(F.get(), oy) }),
    };
    return () => { delete g.__igo; };
  }, [now, clockT, levelSV, touching, T, mode, pinned, rate, refr, seqT, lM, lBan, listening, reducedSV, takeoverAudit, F, oy]);

  const onLayout = (e: LayoutChangeEvent) => setSize({ w: e.nativeEvent.layout.width, h: e.nativeEvent.layout.height });
  const ready = size && page && text && ringMatte && ringSpec && floorMatte;

  return (
    <GestureDetector gesture={gesture}>
      <View style={styles.root} onLayout={onLayout}>
        <StatusBar hidden />
        {ready && (
          <Canvas style={StyleSheet.absoluteFill}>
            <Group transform={[{ scale: S }]}>
              <Image image={page.img} x={0} y={0} width={402} height={Hc} fit="fill" />

              {/* light on the glass: the reply's hue in a band (colour blend), the floor 120 ms behind; ridges, shimmer and glint (additive) */}
              <Group transform={[{ translateX: art.x }, { translateY: art.y }, { scale: art.scale }]}>
                <Group blendMode="color">
                  <Rect rect={relightRect}>
                    <Shader source={RELIGHT_FX} uniforms={relightColor}>
                      <ImageShader image={ringMatte} x={0} y={0} width={402} height={874} fit="fill" tx="clamp" ty="clamp" sampling={SAMPLING} />
                      <ImageShader image={ringSpec} x={0} y={0} width={402} height={874} fit="fill" tx="clamp" ty="clamp" sampling={SAMPLING} />
                      <ImageShader image={floorMatte} x={0} y={0} width={402} height={874} fit="fill" tx="clamp" ty="clamp" sampling={SAMPLING} />
                    </Shader>
                  </Rect>
                </Group>
                <Group blendMode="screen">
                  <Rect x={0} y={230} width={402} height={520}>
                    <Shader source={RELIGHT_FX} uniforms={relightLight}>
                      <ImageShader image={ringMatte} x={0} y={0} width={402} height={874} fit="fill" tx="clamp" ty="clamp" sampling={SAMPLING} />
                      <ImageShader image={ringSpec} x={0} y={0} width={402} height={874} fit="fill" tx="clamp" ty="clamp" sampling={SAMPLING} />
                      <ImageShader image={floorMatte} x={0} y={0} width={402} height={874} fit="fill" tx="clamp" ty="clamp" sampling={SAMPLING} />
                    </Shader>
                  </Rect>
                </Group>
              </Group>

              {/* the reply's light: the widgets' rims catch it, the calendar row lifts */}
              <WidgetRim w={G.wL} a={rimLA} />
              <WidgetRim w={G.wR} a={rimRA} />
              <Group transform={rowM}>
                <RoundedRect x={G.row.x} y={G.row.barY} width={2} height={G.row.barH} r={1} color={barCol} />
                <Paragraph paragraph={text.events} x={G.row.x + 8} y={topFor(text.events, 181)} width={1000} />
                <Group opacity={rangeA}>
                  <Paragraph paragraph={text.range} x={G.row.x + 8} y={topFor(text.range, 200)} width={1000} />
                </Group>
              </Group>
              {/* status bar (ours: the banner covers it) */}
              <Group opacity={clockA}>
                <Paragraph paragraph={text.time} x={36} y={topFor(text.time, 26)} width={1000} />
              </Group>
              <StatusIcons />
              <Circle cx={G.micDot.x} cy={G.micDot.y} r={G.micDot.d / 2} color={C.mic} opacity={micA} />

              {/* the island, the drop, the pill and the banner: one shader */}
              <Rect x={0} y={0} width={402} height={Hc}>
                <Shader source={ISLAND_FX} uniforms={uniforms}>
                  <ImageShader image={page.img} x={0} y={0} width={402} height={Hc} fit="fill" tx="clamp" ty="clamp" sampling={SAMPLING} />
                  <ImageShader image={page.blur} x={0} y={0} width={402} height={G.shaderH} fit="fill" tx="clamp" ty="clamp" sampling={SAMPLING} />
                </Shader>
              </Rect>

              {/* thinking */}
              <Group opacity={spinA} layer={<Paint><Blur blur={spinBlur} /></Paint>}>
                {[0, 1, 2, 3, 4, 5].map((i) => (
                  <SpinDot key={i} i={i} F={F} cx={spinX} cy={spinY} />
                ))}
              </Group>

              {/* the reply, word by word */}
              {words.map((w, i) => (
                <Word key={`${i}-${w.x}`} p={w.p} x={w.x} cy={w.cy + oy} i={i} F={F} />
              ))}
            </Group>
          </Canvas>
        )}
      </View>
    </GestureDetector>
  );
}

function WidgetRim({ w, a }: { w: { x: number; y: number; w: number; h: number; r: number }; a: SharedValue<number> }) {
  return (
    <RoundedRect x={w.x + 0.5} y={w.y + 0.5} width={w.w - 1} height={w.h - 1} r={w.r - 0.5} style="stroke" strokeWidth={2} opacity={a}>
      <LinearGradient start={vec(0, w.y)} end={vec(0, w.y + w.h * 0.6)} colors={['rgba(255,255,255,0.25)', 'rgba(255,255,255,0)']} />
    </RoundedRect>
  );
}

function SpinDot({ i, F, cx, cy }: { i: number; F: SharedValue<Frame>; cx: SharedValue<number>; cy: number }) {
  const ang = (i / 6) * 2 * Math.PI - Math.PI / 2;
  const x = useDerivedValue(() => cx.get() + G.spinner.r * Math.cos(ang));
  const y = cy + G.spinner.r * Math.sin(ang);
  const op = useDerivedValue(() => {
    // the head turns once a second; each dot is brightest as it passes, then fades back to 30 %
    const head = (((F.get().spinT % 1) + 1) % 1) * 6;
    const behind = (((head - i) % 6) + 6) % 6;
    return 0.3 + 0.7 * Math.max(0, 1 - behind / 3.2);
  });
  return <Circle cx={x} cy={y} r={G.spinner.dot / 2} color="#F7F7F5" opacity={op} />;
}

function Word({ p, x, cy, i, F }: { p: SkParagraph; x: number; cy: number; i: number; F: SharedValue<Frame> }) {
  const k = useDerivedValue(() => easeOut(seg(F.get().textT, 0.09 * i, 0.09 * i + 0.3)));
  // The Pro reveal wipes each word through the glass blur; it never fades its opacity in.
  const op = useDerivedValue(() => 1 - F.get().textOut);
  const width = p.getLongestLine();
  const height = p.getHeight();
  const clip = useDerivedValue(() => ({ x: x - 2, y: cy - height / 2 - 8, width: (width + 4) * k.get(), height: height + 16 }));
  const blur = useDerivedValue(() => 8 * (1 - k.get()) + 8 * F.get().textOut);
  const tr = useDerivedValue(() => [{ translateY: 4 * (1 - k.get()) }]);
  return (
    <Group clip={clip}>
      <Group transform={tr} layer={<Paint opacity={op}><Blur blur={blur} mode="decal" /></Paint>}>
        <Paragraph paragraph={p} x={x} y={topFor(p, cy)} width={1000} />
      </Group>
    </Group>
  );
}

/** Signal, wi-fi and battery, drawn so the reply banner can cover them. */
function StatusIcons() {
  const bars = [4, 6, 8.5, 11];
  return (
    <Group color="#F7F7F5">
      {bars.map((h, i) => (
        <RoundedRect key={i} x={298 + i * 4.6} y={31.5 - h} width={3} height={h} r={1} color="#F7F7F5" />
      ))}
      {WIFI && <Path path={WIFI} style="stroke" strokeWidth={2.1} strokeCap="round" color="#F7F7F5" />}
      <Circle cx={331} cy={30.4} r={1.5} color="#F7F7F5" />
      <RoundedRect x={346} y={20.5} width={25} height={11.5} r={3.6} style="stroke" strokeWidth={1} color="rgba(247,247,245,0.4)" />
      <RoundedRect x={348} y={22.5} width={8} height={7.5} r={1.8} color="#F7F7F5" />
      <RoundedRect x={372.2} y={24.3} width={1.6} height={4} r={0.8} color="rgba(247,247,245,0.4)" />
    </Group>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: C.sage, overflow: 'hidden' },
});
