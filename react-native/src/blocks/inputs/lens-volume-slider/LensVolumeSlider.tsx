import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useEffect, useMemo, useState } from 'react';
import { type LayoutChangeEvent, PixelRatio, StyleSheet, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import { cancelAnimation, Easing, useDerivedValue, useFrameCallback, useSharedValue, withSequence, withSpring, withTiming } from 'react-native-reanimated';
import {
  Blur,
  Canvas,
  drawAsImage,
  FilterMode,
  Group,
  Image,
  ImageShader,
  ImageSVG,
  MipmapMode,
  Rect,
  Shader,
  Skia,
  type SkImage,
  Text as SkText,
  useFont,
  useImage,
} from '@shopify/react-native-skia';
import { scheduleOnRN } from 'react-native-worklets';

import { useReducedMotionPreference } from '../../../shared/useReducedMotionPreference';
import { GLYPH } from './lvs-icons';
import { clamp, detent, driftAt, type Frame, frameAt, LOOP, mix, stretchFor } from './lvs-motion';
import { GLASS_FX } from './lvs-shaders';
import { FILL_H, fillW, G, PT_PER_UNIT, SPRING } from './lvs-theme';

/** Skia runtime effects are read as Display P3 on iOS: the shader re-encodes its sRGB result there. */
const P3 = 1;
const SAMPLING = { filter: FilterMode.Linear, mipmap: MipmapMode.None };
/** Atlas resolution (px per pt): the lens magnifies the glyph and digits ×1.25, so they are drawn at 4×. */
const A = 4;
const DH = 32; // digit cell height (pt): the odometer window
const GS = G.glyph.size;
const REST = 52;

type Props = {
  /** Plays the 8 s loop until a finger takes over (default true). */
  autoPlay?: boolean;
  /** Called on the JS thread whenever the value lands on a new integer. */
  onChange?: (value: number) => void;
};

type Atlas = { img: SkImage; dw: number; w: number; h: number };

/**
 * The now-playing volume control of a speaker app: a drop of clear glass slides inside a smoked capsule over a
 * full-bleed photograph and bends it. Its end is an inverting ball lens (the room upside-down, a blue fringe on its rim)
 * that throws a caustic ahead of it; dragging stretches it like a liquid, and at 0 it snaps into a glass marble.
 */
export function LensVolumeSlider({ autoPlay = true, onChange }: Props) {
  const reduced = useReducedMotionPreference();
  const [size, setSize] = useState<{ w: number; h: number } | null>(null);
  const plate = useImage(require('./assets/plate-v2.jpg'));
  const font = useFont(require('./assets/fonts/InterDisplay-SemiBold.ttf'), G.num.size * A);
  const [blurredPlate, setBlurredPlate] = useState<{ img: SkImage; height: number } | null>(null);
  const [atlas, setAtlas] = useState<Atlas | null>(null);

  const W = size?.w ?? 402;
  const H = size?.h ?? 874;
  const S = W / 402;
  const Hc = H / S;
  const trackY = Hc - G.rowFromBottom;
  const cy = trackY + G.track.h / 2;
  const x0 = G.track.x + G.inset;
  const innerRight = G.track.x + G.track.w - G.inset;

  // ---- the blurred plate, made once (the control appears only when it exists: no flash of a clear track) ----
  useEffect(() => {
    if (!plate || !size) return;
    let alive = true;
    (async () => {
      const img = await drawAsImage(
        <Image image={plate} x={0} y={0} width={402} height={Hc} fit="cover">
          <Blur blur={14} mode="clamp" />
        </Image>,
        { width: 402, height: Math.round(Hc) },
      );
      if (alive && img) setBlurredPlate({ img, height: Hc });
    })();
    return () => {
      alive = false;
    };
  }, [plate, size, Hc]);
  const blurred = blurredPlate?.height === Hc ? blurredPlate.img : null;

  // ---- the atlas: ten digits and the glyph's parts, white on clear, drawn once ----
  const svgs = useMemo(
    () => ({
      body: Skia.SVG.MakeFromString(GLYPH.body),
      arc1: Skia.SVG.MakeFromString(GLYPH.arc1),
      arc2: Skia.SVG.MakeFromString(GLYPH.arc2),
      slash: Skia.SVG.MakeFromString(GLYPH.slash),
    }),
    [],
  );
  useEffect(() => {
    if (!font) return;
    let alive = true;
    const digits = '0123456789';
    const widths = font.getGlyphWidths(font.getGlyphIDs(digits));
    const box = font.measureText(digits);
    const dw = Math.ceil(Math.max(...widths) / A + G.num.size * G.num.track);
    const w = Math.max(10 * dw, 4 * GS);
    const h = DH + GS;
    const base = (DH * A) / 2 - (box.y + box.height / 2);
    (async () => {
      const img = await drawAsImage(
        <Group>
          {digits.split('').map((ch, i) => (
            <SkText key={ch} text={ch} font={font} x={i * dw * A + (dw * A - widths[i]) / 2} y={base} color="white" />
          ))}
          {(['body', 'arc1', 'arc2', 'slash'] as const).map((k, i) => (
            <ImageSVG key={k} svg={svgs[k]} x={i * GS * A} y={DH * A} width={GS * A} height={GS * A} />
          ))}
        </Group>,
        { width: w * A, height: h * A },
      );
      if (alive && img) setAtlas({ img, dw, w, h });
    })();
    return () => {
      alive = false;
    };
  }, [font, svgs]);

  // ---- clocks ----
  const T = useSharedValue(0);
  const auto = useSharedValue(autoPlay && !reduced ? 1 : 0);
  const pinned = useSharedValue(0);
  const rate = useSharedValue(1);
  const now = useSharedValue(0);
  const phase = useSharedValue(0); // the photo's drift clock while a finger owns the control
  const driftHoldX = useSharedValue(0);
  const driftHoldY = useSharedValue(0);
  const driftBlend = useSharedValue(0);
  const touching = useSharedValue(0);
  const lastTouch = useSharedValue(0);
  const homing = useSharedValue(0);
  const reducedSV = useSharedValue(reduced ? 1 : 0);

  // ---- live state (a finger, or the springs it let go of) ----
  const vL = useSharedValue(REST);
  const vPrev = useSharedValue(REST);
  const vTensL = useSharedValue(REST);
  const vHundL = useSharedValue(REST);
  const stretchL = useSharedValue(0);
  const liftL = useSharedValue(0);
  const fingerX = useSharedValue(0);
  const dimpleXL = useSharedValue(0);
  const dimpleL = useSharedValue(0);
  const sweepL = useSharedValue(-1);
  const squashL = useSharedValue(1);
  const honeyL = useSharedValue(0);
  const causticOffset = useSharedValue(0);
  const trackL = useSharedValue(0);
  const flashL = useSharedValue(0);
  const arc1L = useSharedValue(1);
  const arc2L = useSharedValue(1);
  const arc2Target = useSharedValue(1);
  const slashL = useSharedValue(0);
  const marbleL = useSharedValue(0);
  const muteTarget = useSharedValue(0);
  const dimL = useSharedValue(0);
  const zoomL = useSharedValue(REST);
  const glintL = useSharedValue(0);
  const v0 = useSharedValue(REST);
  const lastNonZero = useSharedValue(REST);
  const lastInt = useSharedValue(REST);
  const lastTick = useSharedValue(0);
  const atEnd = useSharedValue(0); // 1 while pressed past an end (one Rigid per hit)

  useEffect(() => {
    auto.set(autoPlay && !reduced ? 1 : 0);
    reducedSV.set(reduced ? 1 : 0);
  }, [autoPlay, reduced, auto, reducedSV]);

  useEffect(() => {
    if (!__DEV__) return;
    const g = globalThis as { __lvs?: unknown };
    g.__lvs = {
      at: (t: number) => {
        pinned.set(1);
        auto.set(1);
        T.set(t);
      },
      play: () => {
        pinned.set(0);
      },
      rate: (r: number) => {
        rate.set(r);
      },
      state: () => ({ T: T.get(), auto: auto.get(), pinned: pinned.get(), v: vL.get(), lift: liftL.get(), stretch: stretchL.get(), marble: marbleL.get() }),
    };
    return () => {
      delete g.__lvs;
    };
  }, [T, auto, pinned, rate, vL, liftL, stretchL, marbleL]);

  const buzz = (k: 'soft' | 'light' | 'medium' | 'rigid' | 'sel') => {
    if (k === 'sel') Haptics.selectionAsync().catch(() => {});
    else {
      const style = k === 'soft' ? Haptics.ImpactFeedbackStyle.Soft : k === 'light' ? Haptics.ImpactFeedbackStyle.Light : k === 'medium' ? Haptics.ImpactFeedbackStyle.Medium : Haptics.ImpactFeedbackStyle.Rigid;
      Haptics.impactAsync(style).catch(() => {});
    }
  };
  const emit = (v: number) => onChange?.(v);
  const hasOnChange = !!onChange;

  const spring = (to: number, s: { d: number; k: number }, velocity = 0) => {
    'worklet';
    return reducedSV.get() === 1 ? withTiming(to, { duration: 200 }) : withSpring(to, { damping: s.d, stiffness: s.k, velocity });
  };

  // ---- the clock and the live physics ----
  useFrameCallback((info) => {
    const raw = (info.timeSincePreviousFrame ?? 16) / 1000;
    const dt = Math.min(raw, rate.get() < 1 ? 1 : 0.05) * rate.get();
    now.set(now.get() + dt);
    if (pinned.get() === 1) return;
    if (auto.get() === 1) {
      T.set((T.get() + dt) % LOOP);
      return;
    }
    phase.set((phase.get() + dt) % LOOP);
    if (reducedSV.get() === 0) glintL.set((glintL.get() + dt / 4) % 1);
    if (dt <= 0) return;
    const v = vL.get();
    const vc = clamp(v, 0, 100);
    // the end leads by the speed, low-passed over 80 ms (none under reduced motion)
    const speed = (vc - vPrev.get()) / dt;
    vPrev.set(vc);
    const k80 = 1 - Math.exp(-dt / 0.08);
    const target = reducedSV.get() === 1 ? 0 : stretchFor(speed);
    stretchL.set(stretchL.get() + (target - stretchL.get()) * k80);
    vTensL.set(vTensL.get() + (vc - vTensL.get()) * (1 - Math.exp(-dt / 0.03)));
    vHundL.set(vHundL.get() + (vc - vHundL.get()) * (1 - Math.exp(-dt / 0.06)));
    zoomL.set(zoomL.get() + (vc - zoomL.get()) * (1 - Math.exp(-dt / 0.15)));
    dimpleXL.set(dimpleXL.get() + (fingerX.get() - dimpleXL.get()) * (1 - Math.exp(-dt / 0.04)));

    // the glyph and the marble follow the value
    const a2 = vc >= 34 ? 1 : 0;
    if (arc2Target.get() !== a2) {
      arc2Target.set(a2);
      arc2L.set(withTiming(a2, { duration: 130 }));
    }
    const muted = vc < 0.5 ? 1 : 0;
    if (muteTarget.get() !== muted) {
      muteTarget.set(muted);
      marbleL.set(withTiming(muted, { duration: 160 }));
      dimL.set(withTiming(muted, { duration: 500 }));
      arc1L.set(withTiming(1 - muted, { duration: 120 }));
      slashL.set(withTiming(muted, { duration: 160 }));
      if (muted) {
        squashL.set(reducedSV.get() === 1 ? 1 : withSequence(withTiming(1.08, { duration: 70 }), withSpring(1, { damping: SPRING.pop.d, stiffness: SPRING.pop.k })));
        scheduleOnRN(buzz, 'medium');
      } else scheduleOnRN(buzz, 'light');
    }
    // haptic texture: a tick every 5 units when slow, every 10 when fast
    if (touching.get() === 1) {
      const step = Math.abs(speed) < 60 ? 5 : 10;
      const tick = Math.floor(vc / step);
      if (tick !== lastTick.get()) {
        lastTick.set(tick);
        scheduleOnRN(buzz, 'sel');
      }
    }
    const n = Math.round(vc);
    if (n !== lastInt.get()) {
      lastInt.set(n);
      if (hasOnChange) scheduleOnRN(emit, n);
    }

    // 2 s after the last touch: home to 52, then the loop takes over again from its first frame
    if (autoPlay && reducedSV.get() === 0 && touching.get() === 0 && now.get() - lastTouch.get() > 2) {
      if (homing.get() === 0) {
        homing.set(1);
        vL.set(withSpring(REST, { damping: SPRING.glide.d, stiffness: SPRING.glide.k }));
      } else if (Math.abs(vL.get() - REST) < 0.02 && Math.abs(stretchL.get()) < 0.05 && Math.abs(liftL.get()) < 0.01 && dimL.get() < 0.001) {
        const d = driftAt(phase.get());
        driftHoldX.set(d.x);
        driftHoldY.set(d.y);
        driftBlend.set(1);
        driftBlend.set(withTiming(0, { duration: 1400 }));
        homing.set(0);
        T.set(0);
        auto.set(1);
      }
    }
  });

  // ---- the frame being drawn ----
  const F = useDerivedValue<Frame>(() => {
    if (auto.get() === 1) return frameAt(T.get());
    const v = clamp(vL.get(), 0, 100);
    const st = stretchL.get();
    return {
      v,
      vTens: vTensL.get(),
      vHund: vHundL.get(),
      stretch: st,
      lift: liftL.get(),
      dimple: dimpleL.get(),
      dimpleX: dimpleXL.get(),
      sweep: sweepL.get(),
      glint: glintL.get(),
      squash: squashL.get(),
      honey: honeyL.get(),
      trackStretch: trackL.get(),
      flash: flashL.get(),
      arc1: arc1L.get(),
      arc2: arc2L.get(),
      slash: slashL.get(),
      caustic: 0.8 + 0.4 * Math.min(Math.abs(st) / 6, 1) + causticOffset.get(),
      marble: marbleL.get(),
      dim: dimL.get(),
      zoomV: zoomL.get(),
    };
  });

  const dw = atlas?.dw ?? 17;
  const px = 1 / (S * PixelRatio.get());
  const uniforms = useDerivedValue(() => {
    const f = F.get();
    const tAuto = auto.get() === 1 ? T.get() : phase.get();
    const dr = reducedSV.get() === 1 ? { x: 0, y: 0 } : driftAt(tAuto);
    const db = driftBlend.get();
    const dx = mix(dr.x, driftHoldX.get(), db) + G.plate.shiftX;
    const dy = mix(dr.y, driftHoldY.get(), db);
    const tr = f.trackStretch;
    const absSt = Math.abs(f.stretch);
    const w = Math.max(fillW(f.v) + f.stretch, FILL_H * 0.97);
    const xEnd = Math.min(x0 + w, innerRight + Math.max(tr, 0));
    const h = FILL_H * (1 - (0.08 * absSt) / G.maxStretch) + (G.liftH - FILL_H) * f.lift - f.honey;
    // the ball's inverted magnification at its centre: ×1.8 at rest, ×2.1 lifted (the marble samples the room itself)
    const gain = mix(1.8, 2.1, clamp(f.lift, 0, 1));
    // odometer rolls
    const rollW = 0.28 * (1 - clamp(absSt / 5, 0, 1));
    // the tens and hundreds lag only on the way up; on the way down a lagging 1 would read 193
    const du = detent(f.v, rollW);
    const dt = detent(Math.min(f.vTens, f.v), rollW);
    const dh = detent(Math.min(f.vHund, f.v), rollW);
    const rt = Math.floor(dt / 10) + clamp(dt - Math.floor(dt / 10) * 10 - 9, 0, 1);
    const rh = Math.floor(dh / 100) + clamp(dh - Math.floor(dh / 100) * 100 - 99, 0, 1);
    const count = 1 + clamp(dt - 9, 0, 1) + clamp(dh - 99, 0, 1);
    const dimpleX = Math.min(Math.max(f.dimpleX, x0 + 30), xEnd - 30);
    return {
      uP3: P3,
      uPx: px,
      uRes: [402, Hc],
      uPlate: [dx, dy, G.plate.scale + (G.plate.zoom * clamp(f.zoomV, 0, 100)) / 100, f.dim],
      uExpo: f.flash,
      uTrack: [G.track.x, trackY, G.track.w + tr, G.track.h],
      uFill: [x0, xEnd, h, (0.1 * absSt) / G.maxStretch],
      uLens: [gain, f.lift, f.squash, f.marble],
      uDimple: [dimpleX, clamp(f.dimple, 0, 1), G.dimple, 0],
      uLight: [f.sweep, f.glint, f.flash, f.caustic],
      uGlyph: [f.arc1, f.arc2, f.slash, A],
      // in the marble the glyph moves to the ball's centre and the numeral rolls away
      uGlyphBox: [G.glyph.x + (x0 + FILL_H / 2 - GS / 2 - G.glyph.x) * f.marble, cy - GS / 2, GS, DH],
      uArc: [GLYPH.arcCentre[0] * (GS / 24), GLYPH.arcCentre[1] * (GS / 24), GLYPH.arcSpan, 0],
      uSlash: [GLYPH.slashFrom[0] * (GS / 24), GLYPH.slashFrom[1] * (GS / 24), GLYPH.slashTo[0] * (GS / 24), GLYPH.slashTo[1] * (GS / 24)],
      uRoll: [du, rt, rh, count],
      // the numeral leaves (and comes back) in the middle of the marble's ramp, so no half digit sits under the ball
      uShow: 1 - clamp((f.marble - 0.35) / 0.3, 0, 1),
      uRoom: [G.room.x, (G.room.y * Hc) / 874, G.room.r, 0],
      // the numeral gives way to the left when a fast drop's end comes close (never onto the glyph)
      uNum: [clamp(xEnd - 12 - count * dw, G.glyph.x + GS + 4, G.num.x), cy, dw, DH],
    };
  });

  // ---- touch ----
  const toCanvas = (x: number, y: number) => {
    'worklet';
    return { x: x / S, y: y / S };
  };
  const onTrack = (x: number, y: number) => {
    'worklet';
    return x > G.track.x - 12 && x < G.track.x + G.track.w + 12 && y > trackY - 12 && y < trackY + G.track.h + 12;
  };
  const takeOver = () => {
    'worklet';
    cancelAnimation(vL);
    if (auto.get() === 1) {
      // keep what the loop shows: its frame becomes the live state
      const f = frameAt(T.get());
      vL.set(f.v);
      vPrev.set(f.v);
      vTensL.set(f.vTens);
      vHundL.set(f.vHund);
      stretchL.set(f.stretch);
      liftL.set(f.lift);
      dimpleL.set(f.dimple);
      dimpleXL.set(f.dimpleX);
      fingerX.set(f.dimpleX);
      sweepL.set(f.sweep);
      if (f.sweep >= 0) {
        sweepL.set(withSequence(
          withTiming(1, { duration: 300 * (1 - f.sweep), easing: Easing.linear }),
          withTiming(-1, { duration: 0 }),
        ));
      }
      squashL.set(f.squash);
      honeyL.set(f.honey);
      if (f.honey > 0) honeyL.set(withTiming(0, { duration: 180 }));
      causticOffset.set(f.caustic - (0.8 + 0.4 * Math.min(Math.abs(f.stretch) / 6, 1)));
      causticOffset.set(withTiming(0, { duration: 180 }));
      trackL.set(f.trackStretch);
      flashL.set(f.flash);
      if (f.flash > 0) flashL.set(withTiming(0, { duration: 300 }));
      const muted = f.v < 0.5 ? 1 : 0;
      const arc2 = f.v >= 34 ? 1 : 0;
      muteTarget.set(muted);
      arc2Target.set(arc2);
      arc1L.set(f.arc1);
      arc2L.set(f.arc2);
      slashL.set(f.slash);
      marbleL.set(f.marble);
      dimL.set(f.dim);
      if (f.arc1 !== 1 - muted) arc1L.set(withTiming(1 - muted, { duration: 120 }));
      if (f.arc2 !== arc2) arc2L.set(withTiming(arc2, { duration: 130 }));
      if (f.slash !== muted) slashL.set(withTiming(muted, { duration: 160 }));
      if (f.marble !== muted) marbleL.set(withTiming(muted, { duration: 160 }));
      if (f.dim !== muted) dimL.set(withTiming(muted, { duration: 500 }));
      zoomL.set(f.zoomV);
      glintL.set(f.glint);
      phase.set(T.get());
      lastInt.set(Math.round(f.v));
      lastTick.set(Math.floor(f.v / 5));
      auto.set(0);
    }
    pinned.set(0);
    homing.set(0);
    touching.set(1);
    lastTouch.set(now.get());
  };
  const letGo = () => {
    'worklet';
    touching.set(0);
    lastTouch.set(now.get());
    liftL.set(reducedSV.get() === 1 ? withTiming(0, { duration: 200 }) : withSpring(0, { damping: SPRING.sink.d, stiffness: SPRING.sink.k }));
    dimpleL.set(withTiming(0, { duration: 180 }));
    // the dent slides into the lens as it goes
    fingerX.set(x0 + fillW(clamp(vL.get(), 0, 100)) - FILL_H / 2);
    if (trackL.get() !== 0) trackL.set(spring(0, SPRING.weighty));
    if (squashL.get() !== 1) squashL.set(spring(1, SPRING.pop));
    atEnd.set(0);
  };

  const pan = Gesture.Pan()
    .activeOffsetX([-4, 4])
    .failOffsetY([-24, 24])
    .onBegin((e) => {
      const q = toCanvas(e.x, e.y);
      if (!onTrack(q.x, q.y)) return;
      takeOver();
      liftL.set(reducedSV.get() === 1 ? withTiming(1, { duration: 120 }) : withSpring(1, { damping: 20, stiffness: 420 }));
      fingerX.set(q.x);
      dimpleXL.set(q.x);
      dimpleL.set(withTiming(1, { duration: 100 }));
      scheduleOnRN(buzz, 'soft');
    })
    .onStart(() => {
      if (touching.get() === 0) return;
      v0.set(clamp(vL.get(), 0, 100));
    })
    .onUpdate((e) => {
      if (touching.get() === 0) return;
      const q = toCanvas(e.x, e.y);
      fingerX.set(q.x);
      const raw = v0.get() + e.translationX / S / PT_PER_UNIT;
      // past the ends: a rubber band; at 100 the capsule stretches, at 0 the marble squashes against the cap
      if (raw > 100) {
        const over = (raw - 100) * PT_PER_UNIT;
        const rb = over / (1 + over / 120);
        vL.set(100);
        trackL.set(Math.min(G.trackStretchMax, 0.5 * rb));
        if (atEnd.get() === 0) {
          atEnd.set(1);
          flashL.set(withSequence(withTiming(1, { duration: 30 }), withTiming(0, { duration: 300 })));
          scheduleOnRN(buzz, 'rigid');
        }
      } else if (raw < 0) {
        const over = -raw * PT_PER_UNIT;
        const rb = over / (1 + over / 120);
        vL.set(0);
        squashL.set(1 + Math.min(0.06, rb / 400));
        if (atEnd.get() === 0) {
          atEnd.set(1);
          scheduleOnRN(buzz, 'rigid');
        }
      } else {
        vL.set(raw);
        if (trackL.get() !== 0) trackL.set(0);
        if (vL.get() > 0.5 && squashL.get() !== 1) squashL.set(1);
        atEnd.set(0);
      }
      if (raw > 0.5) lastNonZero.set(Math.min(raw, 100));
    })
    .onEnd((e, ok) => {
      if (touching.get() === 0) return;
      const vel = ok ? e.velocityX / S / PT_PER_UNIT : 0;
      const v = clamp(vL.get(), 0, 100);
      const to = Math.round(clamp(v + vel * 0.12, 0, 100));
      vL.set(spring(to, SPRING.release, vel));
      if (to > 0) lastNonZero.set(to);
      if (ok && reducedSV.get() === 0) sweepL.set(withSequence(withTiming(0, { duration: 0 }), withTiming(1, { duration: 300 }), withTiming(-1, { duration: 0 })));
    })
    .onFinalize(() => {
      if (touching.get() === 1) letGo();
    });

  const tap = Gesture.Tap()
    .maxDistance(8)
    .onEnd((e, ok) => {
      if (!ok) return;
      const q = toCanvas(e.x, e.y);
      if (!onTrack(q.x, q.y)) return;
      takeOver();
      const v = clamp(vL.get(), 0, 100);
      if (v < 0.5 && q.x < x0 + FILL_H + 8) {
        // the marble: un-mute to the last level
        vL.set(spring(lastNonZero.get(), SPRING.glide));
      } else if (v >= 0.5 && q.x >= G.glyph.x - 8 && q.x <= G.glyph.x + GS + 8) {
        // the glyph: mute, remembering the level
        lastNonZero.set(v);
        vL.set(spring(0, SPRING.glide));
      } else {
        // anywhere else: the lens end comes to the finger
        const to = Math.round(clamp((q.x + FILL_H / 2 - x0 - FILL_H) / PT_PER_UNIT, 0, 100));
        vL.set(spring(to, SPRING.snap));
        if (to > 0) lastNonZero.set(to);
      }
      letGo();
    });

  const gesture = Gesture.Simultaneous(pan, tap);
  const onLayout = (e: LayoutChangeEvent) => setSize({ w: e.nativeEvent.layout.width, h: e.nativeEvent.layout.height });
  const ready = !!(size && plate && blurred && atlas);

  return (
    <GestureDetector gesture={gesture}>
      <View style={styles.root} onLayout={onLayout}>
        <StatusBar style="light" />
        {ready && (
          <Canvas style={[StyleSheet.absoluteFill, { width: W, height: H }]} pointerEvents="none">
            <Group transform={[{ scale: S }]}>
              <Rect x={0} y={0} width={402} height={Hc}>
                <Shader source={GLASS_FX} uniforms={uniforms}>
                  <ImageShader image={plate} x={0} y={0} width={402} height={Hc} fit="cover" tx="mirror" ty="clamp" sampling={SAMPLING} />
                  <ImageShader image={blurred} x={0} y={0} width={402} height={Hc} fit="fill" tx="mirror" ty="clamp" sampling={SAMPLING} />
                  <ImageShader image={atlas.img} x={0} y={0} width={atlas.w * A} height={atlas.h * A} fit="fill" tx="decal" ty="decal" sampling={SAMPLING} />
                </Shader>
              </Rect>
            </Group>
          </Canvas>
        )}
      </View>
    </GestureDetector>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: '#1F2022', overflow: 'hidden' },
});
