import { useFonts } from 'expo-font';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useEffect, useState } from 'react';
import { type LayoutChangeEvent, StyleSheet, Text, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, { useAnimatedStyle, useDerivedValue, useFrameCallback, useSharedValue, withSpring } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import {
  Canvas,
  Group,
  Image,
  ImageShader,
  LinearGradient,
  Rect,
  RoundedRect,
  Shader,
  Skia,
  useImage,
  vec,
  Blur,
  processTransform3d,
} from '@shopify/react-native-skia';
import { SvgXml } from 'react-native-svg';
import { scheduleOnRN } from 'react-native-worklets';

import { useReducedMotionPreference } from '../../../shared/useReducedMotionPreference';
import { EMPTY_PRINTS, FLASH_OFF_ICON, FLIP_ICON } from './ipc-icons';
import { clamp01, easeOut, type Frame, frameAt, LOOP, liveFrameAt, mix, seg } from './ipc-motion';
import { BODY_FX, DEVELOP_FX, DOME_FX, SHELL_FX } from './ipc-shaders';
import { C, FONT, G, SPRING } from './ipc-theme';
import { type IpcCue, useIpcAudio } from './use-ipc-audio';

/** Skia runtime effects are read as Display P3 on iOS: re-encode the sRGB colours there. */
const P3 = 1;

const hex3 = (h: string) => [parseInt(h.slice(1, 3), 16) / 255, parseInt(h.slice(3, 5), 16) / 255, parseInt(h.slice(5, 7), 16) / 255];
const SHELL = hex3(C.shell);
const ORANGE = hex3(C.orange);
const LED_OFF = hex3(C.ledOff);
const LED_ON = hex3(C.ledOn);
const LED_BUSY = hex3(C.ledBusy);

type Props = {
  /** Plays the 11 s loop until a finger takes over (default true). */
  autoPlay?: boolean;
};

/**
 * A camera app's red header that becomes a toy camera: pull the page down and the Dynamic Island grows into the
 * viewfinder while the controls rise out of the plastic; press the shutter and the print is fed out of the island
 * into the gallery, where it develops dye layer by dye layer.
 */
export function IslandPrintCamera({ autoPlay = true }: Props) {
  const reduced = useReducedMotionPreference();
  const insets = useSafeAreaInsets();
  const [size, setSize] = useState<{ w: number; h: number } | null>(null);
  const [fontsOk] = useFonts({ [FONT.regular]: require('./assets/fonts/Inter-Regular.ttf') });
  const plate = useImage(require('./assets/plate-back.jpg'));

  const W = size?.w ?? 402;
  const S = W / 402;
  // the island is the hardware cut-out: on Dynamic Island phones it sits 11 pt from the top; elsewhere the pill is
  // drawn just under the status bar
  const islandTop = insets.top >= 54 ? G.islandTop : insets.top + 4;

  // ---- clocks and live state ----
  const T = useSharedValue(0);
  const auto = useSharedValue(autoPlay && !reduced ? 1 : 0);
  const pinned = useSharedValue(0);
  const rate = useSharedValue(1);
  const pLive = useSharedValue(0);
  const depthLive = useSharedValue(0);
  const focusLock = useSharedValue(0);
  const seq = useSharedValue(-1); // time since the shutter fired (s), -1 when idle
  const touching = useSharedValue(0);
  const lastTouch = useSharedValue(0);
  const now = useSharedValue(0);
  const flashOn = useSharedValue(0);
  const p0 = useSharedValue(0);
  // the print under the finger: 0 none, 1 pulling it out of the slot, 2 holding the landed print
  const grab = useSharedValue(0);
  const lastY = useSharedValue(0);
  const dragStartX = useSharedValue(0);
  const dragStartY = useSharedValue(0);
  const dragX = useSharedValue(0);
  const dragY = useSharedValue(0);
  const domesUp = useSharedValue(0);
  const { play: playCue } = useIpcAudio(true);

  useEffect(() => {
    auto.set(autoPlay && !reduced ? 1 : 0);
  }, [autoPlay, reduced, auto]);

  useEffect(() => {
    if (!__DEV__) return;
    const g = globalThis as { __ipc?: unknown };
    g.__ipc = {
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
      state: () => ({ T: T.get(), auto: auto.get(), pinned: pinned.get(), seq: seq.get(), p: pLive.get() }),
    };
    return () => {
      delete g.__ipc;
    };
  }, [T, auto, pinned, rate, seq, pLive]);

  const buzz = (k: 'soft' | 'light' | 'medium' | 'rigid' | 'sel') => {
    if (k === 'sel') Haptics.selectionAsync().catch(() => {});
    else {
      const style = k === 'soft' ? Haptics.ImpactFeedbackStyle.Soft : k === 'light' ? Haptics.ImpactFeedbackStyle.Light : k === 'medium' ? Haptics.ImpactFeedbackStyle.Medium : Haptics.ImpactFeedbackStyle.Rigid;
      Haptics.impactAsync(style).catch(() => {});
    }
  };
  const cue = (c: IpcCue) => {
    if (auto.get() === 0) playCue(c);
  };

  useFrameCallback((info) => {
    const raw = (info.timeSincePreviousFrame ?? 16) / 1000;
    const dt = Math.min(raw, rate.get() < 1 ? 1 : 0.05) * rate.get();
    now.set(now.get() + dt);
    if (pinned.get() === 1) return;
    if (auto.get() === 1) {
      T.set((T.get() + dt) % LOOP);
      return;
    }
    if (seq.get() >= 0) {
      const s0 = seq.get();
      // a held print waits for the finger before it may leave
      const s1 = grab.get() === 2 && s0 >= 5.6 ? s0 : s0 + dt;
      seq.set(s1);
      if (s0 < 1.35 && s1 >= 1.35) scheduleOnRN(cue, 'motor');
      if (s0 < 3.41 && s1 >= 3.41) {
        scheduleOnRN(cue, 'land');
        scheduleOnRN(buzz, 'light');
      }
      // the sequence is the autoplay timeline from the moment the shutter fires (3.5 s) to rest (10 s)
      if (seq.get() > 6.5) {
        seq.set(-1);
        pLive.set(0);
        dragX.set(0);
        dragY.set(0);
        lastTouch.set(now.get());
      }
      return;
    }
    // 2 s after the last touch, ease home and restart the loop
    if (!reduced && autoPlay && touching.get() === 0 && now.get() - lastTouch.get() > 2) {
      if (pLive.get() > 0.004) {
        pLive.set(withSpring(0, { damping: SPRING.glide.d, stiffness: SPRING.glide.k }));
        lastTouch.set(now.get() - 1.4); // check again in 0.6 s
      } else {
        T.set(0);
        auto.set(1);
      }
    }
  });

  // ---- the frame being drawn ----
  const F = useDerivedValue<Frame>(() => {
    if (auto.get() === 1) return frameAt(T.get());
    if (seq.get() >= 0) return frameAt(3.5 + seq.get());
    return liveFrameAt(pLive.get(), depthLive.get(), focusLock.get());
  });
  const swayT = useDerivedValue(() => (auto.get() === 1 ? T.get() : now.get()));

  // ---- geometry derived from the frame (canvas units = pt on the 402-wide canvas) ----
  const islandTopC = islandTop / S;
  const body = useDerivedValue(() => {
    const f = F.get();
    const w0 = mix(G.islandW, G.slotW, f.slot) + 6 * f.antic;
    const h0 = G.islandH - 2 * f.antic;
    const pp = Math.max(f.p, 0);
    const w = mix(w0, G.bodyW, easeOut(Math.min(pp / 0.7, 1)));
    const h = mix(h0, G.bodyH, Math.pow(Math.min(pp, 1.02), 1.15));
    const r = mix(G.islandR, G.bodyR, Math.min(pp, 1));
    return { x: 201 - w / 2, y: islandTopC, w, h, r };
  });
  const bodyUniforms = useDerivedValue(() => {
    const b = body.get();
    return { uP3: P3, uBody: [b.x, b.y, b.w, b.h], uRad: b.r, uBloom: F.get().bloom, uLip: 1, uDip: F.get().slotDip, uShellFx: 0 };
  });
  const slotUniforms = useDerivedValue(() => ({ ...bodyUniforms.get(), uShellFx: 0 }));
  // the live view stays in the lens while it shrinks on the close, so the body is never an empty black blob
  const lensA = useDerivedValue(() => seg(F.get().p, 0.03, 0.22));
  const lens = useDerivedValue(() => {
    const b = body.get();
    const inset = G.lensInset * Math.min(Math.max(F.get().p, 0), 1);
    return Skia.RRectXY(Skia.XYWHRect(b.x + inset, b.y + inset, Math.max(b.w - 2 * inset, 1), Math.max(b.h - 2 * inset, 1)), Math.max(b.r - inset, 4), Math.max(b.r - inset, 4));
  });
  const plateRect = useDerivedValue(() => {
    const b = body.get();
    const cx = b.x + b.w / 2;
    const cy = b.y + b.h / 2;
    const sz = 190 * 1.35 * F.get().focus;
    const t = swayT.get();
    const sx = 5 * Math.sin((2 * Math.PI * 3 * t) / LOOP) + 2 * Math.sin((2 * Math.PI * 5 * t) / LOOP);
    const sy = 4 * Math.sin((2 * Math.PI * 2 * t) / LOOP + 1.3);
    return { x: cx - sz / 2 + sx, y: cy - sz / 2 + sy, w: sz, h: sz };
  });
  const plateX = useDerivedValue(() => plateRect.get().x);
  const plateY = useDerivedValue(() => plateRect.get().y);
  const plateW = useDerivedValue(() => plateRect.get().w);
  const plateH = useDerivedValue(() => plateRect.get().h);
  const blur = useDerivedValue(() => F.get().blur);
  const darken = useDerivedValue(() => clamp01(1 - F.get().exposure) * lensA.get());
  const brighten = useDerivedValue(() => clamp01(F.get().exposure - 1) * 1.6);
  const blackA = useDerivedValue(() => F.get().black);
  const flashA = useDerivedValue(() => F.get().flash);
  const lensRect = useDerivedValue(() => lens.get().rect);
  const lensX = useDerivedValue(() => lens.get().rect.x);
  const lensY = useDerivedValue(() => lens.get().rect.y);
  const lensW = useDerivedValue(() => lens.get().rect.width);
  const lensH = useDerivedValue(() => lens.get().rect.height);
  const glintA = useDerivedValue(() => (F.get().glint > 0 && F.get().glint < 1 ? 1 : 0));
  const glintM = useDerivedValue(() => {
    const r = lens.get().rect;
    const x = r.x + mix(-40, r.width + 40, F.get().glint);
    return processTransform3d([{ translateX: x }, { translateY: r.y + r.height / 2 }, { rotate: (20 * Math.PI) / 180 }]);
  });

  const dome = (i: 0 | 1 | 2) => {
    const spec = i === 0 ? G.led : i === 1 ? G.shutter : G.flip;
    return { x: spec.x, r: spec.d / 2 };
  };
  const domeUniforms = (i: 0 | 1 | 2) => {
    const { x, r } = dome(i);
    return () => {
      'worklet';
      const f = F.get();
      const ledCol = f.led === 2 ? LED_BUSY : f.led === 1 ? LED_ON : flashOn.get() === 1 ? hex3(C.ledFlash) : LED_OFF;
      return {
        uP3: P3,
        uC: [x, G.rowY],
        uR: r,
        uRise: f.rise[i],
        uDepth: i === 1 ? f.depth : 0,
        uFlood: f.flood[i],
        uDome: i === 0 ? ledCol : i === 1 ? ORANGE : SHELL,
        uShell: SHELL,
        uDim: f.dim,
        uHalo: i === 0 ? f.ledHalo : 0,
        uHaloCol: ledCol,
        uRing: i === 2 ? 1 : 0,
        uDot: i === 0 ? 1 : 0,
      };
    };
  };
  const ledU = useDerivedValue(domeUniforms(0));
  const shutterU = useDerivedValue(domeUniforms(1));
  const flipU = useDerivedValue(domeUniforms(2));

  // ---- the print ----
  const P = G.print;
  const slotBottom = useDerivedValue(() => islandTopC + G.islandH + F.get().slotDip);
  const printClip = useDerivedValue(() => {
    const top = F.get().printOut ? -100 : slotBottom.get();
    return Skia.XYWHRect(0, top, 402, 1200);
  });
  const printM = useDerivedValue(() => {
    const f = F.get();
    const cx = P.x + P.w / 2;
    return processTransform3d([
      { translateX: cx + dragX.get() },
      { translateY: slotBottom.get() + dragY.get() },
      { perspective: 600 },
      { rotateX: (-f.printTilt * Math.PI) / 180 },
      { translateY: f.printTop - slotBottom.get() + P.h / 2 },
      { rotate: (f.printRot * Math.PI) / 180 },
      { scaleY: f.printScaleY },
      { translateX: -P.w / 2 },
      { translateY: -P.h / 2 },
    ]);
  });
  const printVisible = useDerivedValue(() => (F.get().printTop < 880 && F.get().f > 0 ? 1 : 0));
  const shadowA = useDerivedValue(() => mix(0.14, 0.22, F.get().printShadow));
  const shadowBlur = useDerivedValue(() => mix(12, 20, F.get().printShadow));
  const devU = useDerivedValue(() => ({ uP3: P3, uD: F.get().d, uSize: [P.photo, P.photo] }));
  const sheenA = useDerivedValue(() => (F.get().sheen > 0 && F.get().sheen < 1 ? 1 : 0));
  const sheenM = useDerivedValue(() => processTransform3d([{ translateX: P.photoX + mix(-30, P.photo + 30, F.get().sheen) }, { translateY: P.photoY + P.photo / 2 }, { rotate: (25 * Math.PI) / 180 }]));
  // the slot is redrawn over the paper only while there is paper: before the feed it would hide the closing lens
  const slotOverlayA = useDerivedValue(() => (F.get().slot > 0.001 && F.get().f > 0 ? 1 : 0));
  const lipShadeY = slotBottom;

  // ---- RN layers ----
  const panelSt = useAnimatedStyle(() => {
    const f = F.get();
    return { transform: [{ translateY: (-4 * f.antic + G.pull * Math.max(f.p, 0)) * S }] };
  });
  const emptySt = useAnimatedStyle(() => ({ opacity: F.get().empty }));
  const stripSt = useAnimatedStyle(() => ({ transform: [{ translateY: G.pull * Math.max(F.get().p, 0) * S }] }));
  const wordSt = useAnimatedStyle(() => {
    const f = F.get();
    const pp = Math.max(f.p, 0);
    const y = mix(G.wordmarkY, G.wordmarkOpenY, pp) - 4 * f.antic;
    const s = mix(1, G.wordmarkOpenScale, Math.pow(Math.min(pp, 1), 0.8));
    return { transform: [{ translateY: (y - G.wordmarkY) * S }, { scale: s }] };
  });
  const flipIconSt = useAnimatedStyle(() => {
    const f = F.get();
    return { opacity: clamp01(f.rise[2] * 3) * f.dim, transform: [{ translateY: -3 * clamp01(f.rise[2]) }] };
  });
  const ledIconSt = useAnimatedStyle(() => {
    const f = F.get();
    return { opacity: flashOn.get() === 1 ? 0 : clamp01(f.flood[0] * 1.2) * (f.led === 0 ? 1 : 0.35), transform: [{ translateY: -3 * clamp01(f.rise[0]) }] };
  });

  // ---- touch ----
  const takeOver = () => {
    'worklet';
    if (auto.get() === 1) {
      // keep what the loop shows: the header's progress becomes the live one
      const f = frameAt(T.get());
      if (T.get() > 3.5 && T.get() < 10) {
        seq.set(T.get() - 3.5);
      } else {
        pLive.set(f.p);
      }
      auto.set(0);
    }
    pinned.set(0);
    touching.set(touching.get() + 1);
    lastTouch.set(now.get());
  };
  const release = () => {
    'worklet';
    touching.set(Math.max(0, touching.get() - 1));
    lastTouch.set(now.get());
  };
  const toCanvas = (x: number, y: number) => {
    'worklet';
    return { x: x / S, y: y / S };
  };
  const hit = (x: number, y: number, cx: number, cy: number, r: number) => {
    'worklet';
    return (x - cx) * (x - cx) + (y - cy) * (y - cy) <= r * r;
  };

  const pan = Gesture.Pan()
    .activeOffsetY([-6, 6])
    .onBegin(() => {
      takeOver();
    })
    .onStart((e) => {
      const s = seq.get();
      if (s >= 0) {
        const q = toCanvas(e.x, e.y);
        const f = frameAt(3.5 + s);
        const onPrint = q.x > P.x - 10 + dragX.get() && q.x < P.x + P.w + 10 + dragX.get() && q.y > f.printTop - 10 + dragY.get() && q.y < f.printTop + P.h + 10 + dragY.get();
        if (onPrint && s >= 1.35 && s < 3.15) grab.set(1);
        else if (onPrint && s >= 3.41 && s < 5.7) {
          grab.set(2);
          dragStartX.set(dragX.get());
          dragStartY.set(dragY.get());
          dragX.set(dragStartX.get());
          dragY.set(dragStartY.get());
        }
        lastY.set(0);
        if (grab.get() > 0) scheduleOnRN(buzz, 'soft');
        return;
      }
      p0.set(pLive.get());
      domesUp.set(pLive.get() > 0.95 ? 1 : 0);
      scheduleOnRN(buzz, 'soft');
    })
    .onUpdate((e) => {
      if (seq.get() >= 0) {
        if (grab.get() === 1) {
          // pulling the paper out speeds the feed (gain 0.55, 137 pt/s nominal), never backwards
          const dy = (e.translationY - lastY.get()) / S;
          lastY.set(e.translationY);
          if (dy > 0) seq.set(Math.min(seq.get() + (0.55 * dy) / 137, 3.149));
        } else if (grab.get() === 2) {
          const dy = e.translationY / S;
          dragX.set(dragStartX.get() + e.translationX / S);
          dragY.set(dragStartY.get() + (dy > 0 ? dy : dy / (1 - dy / 80)));
        }
        return;
      }
      let p = p0.get() + e.translationY / (G.pull * S);
      if (p > 1) {
        const over = (p - 1) * G.pull;
        p = 1 + over / (1 + over / 120) / G.pull;
      }
      pLive.set(Math.max(p, 0));
      if (domesUp.get() === 0 && p > 0.95) {
        domesUp.set(1);
        scheduleOnRN(buzz, 'sel');
      } else if (p < 0.9) domesUp.set(0);
    })
    .onEnd((e, success) => {
      const vx = success ? e.velocityX / S : 0;
      const vy = success ? e.velocityY / S : 0;
      if (seq.get() >= 0) {
        if (grab.get() === 2) {
          if (success && (vy > 700 || e.translationY / S > 140)) {
            // filed: the print drops out of the gallery from where the finger let it go
            seq.set(Math.max(seq.get(), 5.7));
            scheduleOnRN(buzz, 'light');
          } else {
            dragX.set(withSpring(0, { damping: SPRING.pop.d, stiffness: SPRING.pop.k, velocity: vx }));
            dragY.set(withSpring(0, { damping: SPRING.pop.d, stiffness: SPRING.pop.k, velocity: vy }));
          }
        }
        return;
      }
      const v = vy / G.pull;
      const target = (success ? pLive.get() + v * 0.12 : p0.get()) >= 0.5 ? 1 : 0;
      pLive.set(withSpring(target, { damping: SPRING.glide.d, stiffness: SPRING.glide.k, velocity: v }));
      if (success) scheduleOnRN(buzz, target === 1 ? 'rigid' : 'light');
    })
    .onFinalize(() => {
      grab.set(0);
      release();
    });

  const press = Gesture.Tap()
    .maxDuration(8000)
    .maxDistance(12)
    .onBegin((e) => {
      takeOver();
      const q = toCanvas(e.x, e.y);
      const open = pLive.get() > 0.9 && seq.get() < 0;
      if (open && hit(q.x, q.y, G.shutter.x, G.rowY, G.shutter.d / 2 + 6)) {
        depthLive.set(withSpring(0.45, { damping: SPRING.snap.d, stiffness: SPRING.snap.k }));
        focusLock.set(withSpring(1, { damping: SPRING.glide.d, stiffness: SPRING.glide.k }));
        scheduleOnRN(buzz, 'soft');
      }
    })
    .onEnd((e, ok) => {
      const q = toCanvas(e.x, e.y);
      const open = pLive.get() > 0.9 && seq.get() < 0;
      if (ok && open && hit(q.x, q.y, G.shutter.x, G.rowY, G.shutter.d / 2 + 12)) {
        seq.set(0); // fire: the rest of the timeline plays from 3.5 s
        depthLive.set(0);
        focusLock.set(0);
        scheduleOnRN(buzz, 'medium');
        scheduleOnRN(cue, 'shutter');
      } else if (ok && open && hit(q.x, q.y, G.led.x, G.rowY, G.led.d / 2 + 8)) {
        flashOn.set(flashOn.get() === 1 ? 0 : 1);
        scheduleOnRN(buzz, 'sel');
      } else if (ok && seq.get() < 0 && q.y > G.wordmarkY - 30 + Math.max(pLive.get(), 0) * (G.wordmarkOpenY - G.wordmarkY) && q.y < G.wordmarkY + 30 + Math.max(pLive.get(), 0) * (G.wordmarkOpenY - G.wordmarkY) && Math.abs(q.x - 201) < 60) {
        const target = pLive.get() > 0.5 ? 0 : 1;
        pLive.set(withSpring(target, { damping: SPRING.glide.d, stiffness: SPRING.glide.k }));
        scheduleOnRN(buzz, 'light');
      }
    })
    .onFinalize(() => {
      if (depthLive.get() > 0 && seq.get() < 0) {
        depthLive.set(withSpring(0, { damping: SPRING.pop.d, stiffness: SPRING.pop.k }));
        focusLock.set(withSpring(0, { damping: SPRING.glide.d, stiffness: SPRING.glide.k }));
      }
      release();
    });

  const gesture = Gesture.Simultaneous(pan, press);

  const onLayout = (e: LayoutChangeEvent) => setSize({ w: e.nativeEvent.layout.width, h: e.nativeEvent.layout.height });
  const H = size?.h ?? 874;

  return (
    <GestureDetector gesture={gesture}>
      <View style={styles.root} onLayout={onLayout}>
        <StatusBar style="dark" />
        {/* the shell's plastic: sheen and grain over the whole red page */}
        {size && (
          <Canvas style={[StyleSheet.absoluteFill, { width: W, height: H }]} pointerEvents="none">
            <Rect x={0} y={0} width={W} height={H}>
              <Shader source={SHELL_FX} uniforms={{ uH: H }} />
            </Rect>
          </Canvas>
        )}
        {/* the header: island → camera body, lens, domes */}
        {size && (
          <Canvas style={{ position: 'absolute', left: 0, top: 0, width: W, height: G.headerH * S }} pointerEvents="none">
            <Group transform={[{ scale: S }]}>
              <Rect x={0} y={0} width={402} height={G.headerH}>
                <Shader source={BODY_FX} uniforms={bodyUniforms} />
              </Rect>
              <Group clip={lens} opacity={lensA}>
                {plate && (
                  <Image image={plate} x={plateX} y={plateY} width={plateW} height={plateH} fit="cover">
                    <Blur blur={blur} mode="clamp" />
                  </Image>
                )}
                <Rect rect={lensRect} color="#000000" opacity={darken} />
                <Rect rect={lensRect} color="#ffffff" opacity={brighten} />
                {/* vignette and the inner shadow under the bezel's top edge */}
                <Rect x={lensX} y={lensY} width={lensW} height={lensH} opacity={0.5}>
                  <LinearGradient start={vec(0, 0)} end={vec(0, 8)} colors={['rgba(0,0,0,0.55)', 'rgba(0,0,0,0)']} />
                </Rect>
                <Group matrix={glintM} opacity={glintA}>
                  <Rect x={-12} y={-200} width={24} height={400}>
                    <LinearGradient start={vec(-12, 0)} end={vec(12, 0)} colors={['rgba(255,255,255,0)', 'rgba(255,255,255,0.22)', 'rgba(255,255,255,0)']} />
                  </Rect>
                </Group>
                <Rect rect={lensRect} color="#000000" opacity={blackA} />
                <Rect rect={lensRect} color="#ffffff" opacity={flashA} />
              </Group>
              {/* Fresnel line on the lens' lower inner edge */}
              <RoundedRect rect={lens} style="stroke" strokeWidth={1} color="rgba(255,255,255,0.12)" opacity={lensA} />
              <Rect x={G.led.x - 44} y={G.rowY - 44} width={88} height={92}>
                <Shader source={DOME_FX} uniforms={ledU} />
              </Rect>
              <Rect x={G.shutter.x - 62} y={G.rowY - 62} width={124} height={130}>
                <Shader source={DOME_FX} uniforms={shutterU} />
              </Rect>
              <Rect x={G.flip.x - 44} y={G.rowY - 44} width={88} height={92}>
                <Shader source={DOME_FX} uniforms={flipU} />
              </Rect>
            </Group>
          </Canvas>
        )}

        {/* dome icons */}
        <Animated.View pointerEvents="none" style={[styles.icon, { left: (G.flip.x - 11) * S, top: (G.rowY - 11) * S }, flipIconSt]}>
          <SvgXml xml={FLIP_ICON} width={22 * S} height={22 * S} />
        </Animated.View>
        <Animated.View pointerEvents="none" style={[styles.icon, { left: (G.led.x - 8) * S, top: (G.rowY - 8) * S }, ledIconSt]}>
          <SvgXml xml={FLASH_OFF_ICON} width={16 * S} height={16 * S} />
        </Animated.View>

        {/* the gallery panel with its empty state: above the controls, which it uncovers as it slides down */}
        <Animated.View style={[styles.panel, { left: G.panelX * S, top: G.panelTop * S, width: G.panelW * S, height: G.panelH * S, borderRadius: G.panelR * S }, panelSt]}>
          <Animated.View style={[styles.empty, { top: (418 - G.panelTop) * S }, emptySt]}>
            <SvgXml xml={EMPTY_PRINTS} width={48 * S} height={62 * S} />
            <Text allowFontScaling={false} style={[styles.t1, fontsOk && { fontFamily: FONT.regular }, { marginTop: 18 * S, fontSize: 17 * S }]}>
              No pics yet
            </Text>
            <Text allowFontScaling={false} style={[styles.t2, fontsOk && { fontFamily: FONT.regular }, { marginTop: 6 * S, fontSize: 12 * S, lineHeight: 18 * S }]}>
              {'Tap logo or drag down\nto open cam'}
            </Text>
          </Animated.View>
        </Animated.View>

        {/* the wordmark */}
        <Animated.Image
          source={require('./assets/pomo-wordmark.png')}
          style={[styles.word, { left: (201 - G.wordmarkW / 2) * S, top: (G.wordmarkY - G.wordmarkH / 2) * S, width: G.wordmarkW * S, height: G.wordmarkH * S }, wordSt]}
          resizeMode="contain"
        />

        {/* the print, fed out of the island */}
        {size && (
          <Canvas style={[StyleSheet.absoluteFill, { width: W, height: H }]} pointerEvents="none">
            <Group transform={[{ scale: S }]}>
              <Group clip={printClip} opacity={printVisible}>
                <Group matrix={printM}>
                  <RoundedRect x={2} y={4} width={P.w - 4} height={P.h - 2} r={3} color={`rgba(18,10,16,${0.25})`} opacity={shadowA}>
                    <Blur blur={shadowBlur} />
                  </RoundedRect>
                  <RoundedRect x={0} y={0} width={P.w} height={P.h} r={2} color={C.paper} />
                  <Rect x={0} y={0} width={P.w} height={1} color="rgba(255,255,255,0.9)" />
                  <Rect x={0} y={P.h - 0.75} width={P.w} height={0.75} color={C.paperEdge} />
                  {plate && (
                    <Rect x={P.photoX} y={P.photoY} width={P.photo} height={P.photo}>
                      <Shader source={DEVELOP_FX} uniforms={devU}>
                        <ImageShader image={plate} fit="cover" rect={{ x: P.photoX, y: P.photoY, width: P.photo, height: P.photo }} />
                      </Shader>
                    </Rect>
                  )}
                  <Group clip={Skia.XYWHRect(P.photoX, P.photoY, P.photo, P.photo)}>
                    <Group matrix={sheenM} opacity={sheenA}>
                      <Rect x={-20} y={-200} width={40} height={400}>
                        <LinearGradient start={vec(-20, 0)} end={vec(20, 0)} colors={['rgba(255,255,255,0)', 'rgba(255,255,255,0.14)', 'rgba(255,255,255,0)']} />
                      </Rect>
                    </Group>
                  </Group>
                </Group>
                {/* the slot's shadow on the paper */}
                <Rect x={0} y={lipShadeY} width={402} height={10} opacity={slotOverlayA}>
                  <LinearGradient start={vec(0, 0)} end={vec(0, 10)} colors={['rgba(0,0,0,0.35)', 'rgba(0,0,0,0)']} />
                </Rect>
              </Group>
              {/* the slot is drawn again above the paper so it really leaves the cut-out */}
              <Group opacity={slotOverlayA}>
                <Rect x={0} y={0} width={402} height={120}>
                  <Shader source={BODY_FX} uniforms={slotUniforms} />
                </Rect>
              </Group>
            </Group>
          </Canvas>
        )}

        {/* the red strip under the panel (the print slides behind it on its way out) */}
        <Animated.View pointerEvents="none" style={[styles.strip, { top: G.stripTop * S, height: (874 - G.stripTop) * S + 60 }, stripSt]} />
      </View>
    </GestureDetector>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: C.shell, overflow: 'hidden' },
  panel: { position: 'absolute', backgroundColor: C.panel, overflow: 'hidden' },
  empty: { position: 'absolute', left: 0, right: 0, alignItems: 'center' },
  t1: { color: C.ink, letterSpacing: -0.22, textAlign: 'center' },
  t2: { color: C.ink2, textAlign: 'center' },
  icon: { position: 'absolute' },
  word: { position: 'absolute' },
  strip: { position: 'absolute', left: 0, right: 0, backgroundColor: C.shell },
});
