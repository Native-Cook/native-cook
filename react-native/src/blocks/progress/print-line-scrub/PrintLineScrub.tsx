// Print Line Scrub — a slicer's layer preview on a phone. One figurine, printed in matte red up to a line and an unprinted
// mesh above it; the frosted glass capsule that says "Printing Progress" is the layer slider: drag it and the cut plane
// moves 1:1 through the figure, exposing a silhouette-derived section (strands round the wall, honeycomb inside, one hot layer line).
// The percentage is the layer's share of the job (layer / 480), so the label's two numbers always agree; live = 44%.
//
// Drag vertically: the line follows your finger, the capsule ducks under it so the cut shows; drag sideways: the figure
// tilts ±4° with a perspective shear and a light shift; let go: the line springs back to the live layer carrying your velocity, and the layers you left hot cool.
import { StatusBar } from 'expo-status-bar';
import { useEffect, useMemo, useState } from 'react';
import { type AccessibilityActionEvent, PixelRatio, Platform, StyleSheet, useWindowDimensions, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  cancelAnimation,
  Easing,
  useAnimatedStyle,
  useDerivedValue,
  useFrameCallback,
  useSharedValue,
  withDelay,
  withSequence,
  withSpring,
  withTiming,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import {
  BackdropFilter,
  Blur,
  BlurMask,
  Canvas,
  Circle,
  Fill,
  Group,
  ImageShader,
  Line,
  LinearGradient,
  Paint,
  Path,
  Rect,
  RoundedRect,
  type SkRRect,
  Shader,
  Skia,
  Text,
  useFont,
  useImage,
  vec,
} from '@shopify/react-native-skia';
import { scheduleOnRN } from 'react-native-worklets';

import { useReducedMotionPreference } from '../../../shared/useReducedMotionPreference';
import { haptic } from './pls-haptics';
import { CHEVRON, CURSOR, HAND_FINGERS, HAND_PALM, MARK_BARS } from './pls-icons';
import { BEAD_PERIOD, clamp01, GESTURE, H_LIVE, HEAT, layerAt, lookAt, LOOP, mix, PCT_LIVE, percentAt, rubber, SPRING } from './pls-motion';
import { BAR_SKSL, SCENE_SKSL } from './pls-shaders';
import { COPY, makeLayout, ref } from './pls-theme';
import { barUniforms, capsuleCy, glassUniforms, makeGeo, pillX, planeY, sceneUniforms } from './pls-uniforms';

export { LOOP };

const sceneFx = Skia.RuntimeEffect.Make(SCENE_SKSL);
const barFx = Skia.RuntimeEffect.Make(BAR_SKSL);
const C = ref.color;
function linePath(x0: number, y0: number, x1: number, y1: number) {
  const pb = Skia.PathBuilder.Make();
  pb.moveTo(x0, y0);
  pb.lineTo(x1, y1);
  return pb.build();
}
const MARK_PATHS = MARK_BARS.map(([x0, y0, x1, y1]) => linePath(x0, y0, x1, y1));
const CHEVRON_PATH = Skia.Path.MakeFromSVGString(CHEVRON)!;
const CURSOR_PATH = Skia.Path.MakeFromSVGString(CURSOR)!;
const HAND_PALM_PATH = Skia.Path.MakeFromSVGString(HAND_PALM)!;
const HAND_FINGER_PATHS = HAND_FINGERS.map(([x0, y0, x1, y1]) => linePath(x0, y0, x1, y1));
const AXIS_RED = linePath(0, 0, ref.gizmo.stub, -ref.gizmo.stub * 0.2);
const AXIS_GREEN = linePath(0, 0, -ref.gizmo.stub * 0.55, ref.gizmo.stub * 0.62);
const ARROW_HEAD = (() => {
  const pb = Skia.PathBuilder.Make();
  pb.moveTo(0, -ref.gizmo.head);
  pb.lineTo(-ref.gizmo.head * 0.55, 0);
  pb.lineTo(ref.gizmo.head * 0.55, 0);
  pb.close();
  return pb.build();
})();

type Props = { autoPlay?: boolean };

type Grip = { h0: number; moved: boolean; tickAt: number; tickLayer: number; atEnd: boolean };

export function PrintLineScrub({ autoPlay = false }: Props) {
  const { width: W, height: Hh } = useWindowDimensions();
  const insets = useSafeAreaInsets();
  const reduce = useReducedMotionPreference();
  const L = useMemo(() => makeLayout(W, Hh, insets.top, insets.bottom), [W, Hh, insets.top, insets.bottom]);
  const g = useMemo(() => makeGeo(L), [L]);
  const S = g.S;
  const P3 = Platform.OS === 'ios' ? 1 : 0;
  const PX = PixelRatio.get();

  const solidImage = useImage(require('./assets/frillwarden-solid.webp'));
  const ghostImage = useImage(require('./assets/frillwarden-slicer.webp'));

  const fontReg = useFont(require('./assets/fonts/Urbanist-Regular.ttf'), ref.capLabel.size * S);
  const fontMed = useFont(require('./assets/fonts/Urbanist-Medium.ttf'), ref.capLabel.size * S);
  const fontSmall = useFont(require('./assets/fonts/Urbanist-Medium.ttf'), ref.file.size * S);

  // ---- state: the loop plays `lookAt`; a finger hands it to the live values ----
  const h = useSharedValue(H_LIVE);
  const duck = useSharedValue(0);
  const yaw = useSharedValue(0);
  const heat = useSharedValue(0);
  const press = useSharedValue(0);
  const held = useSharedValue(0);
  const bead = useSharedValue(0);
  const flash = useSharedValue(-1);
  const over = useSharedValue(0);
  const tail = useSharedValue(0);
  const speed = useSharedValue(0); // signed pt/s of the line, filtered
  const gizDir = useSharedValue(1);
  const prevH = useSharedValue(H_LIVE);
  const prevPill = useSharedValue(pillX(g, H_LIVE));
  const mode = useSharedValue(0); // 0 loop / rest, 1 finger down, 2 released
  const loopT = useSharedValue(0);
  const clock = useSharedValue(0);
  const touchedAt = useSharedValue(-100);
  const arrived = useSharedValue(1);
  const arrivedAt = useSharedValue(-100);
  const pinned = useSharedValue(-1);
  const grip = useSharedValue<Grip>({ h0: H_LIVE, moved: false, tickAt: 0, tickLayer: layerAt(H_LIVE), atEnd: false });
  const [a11yPct, setA11yPct] = useState(PCT_LIVE);
  const [a11yLayer, setA11yLayer] = useState(layerAt(H_LIVE));
  const onArrive = () => {
    haptic.success();
    setA11yPct(PCT_LIVE);
    setA11yLayer(layerAt(H_LIVE));
  };

  // Development only: `__pls.pin(t)` freezes the loop at t s, `.play()` resumes, `.cut(h)` holds the line at h.
  useEffect(() => {
    if (!__DEV__) return;
    (globalThis as Record<string, unknown>).__pls = {
      pin: (t: number) => {
        pinned.set(t);
        return t;
      },
      play: () => {
        pinned.set(-1);
        loopT.set(0);
        mode.set(0);
        return 1;
      },
      LOOP,
    };
    return () => {
      delete (globalThis as Record<string, unknown>).__pls;
    };
  }, [pinned, loopT, mode]);

  useFrameCallback((frame) => {
    const dt = Math.min(frame.timeSincePreviousFrame ?? 16, 50) / 1000;
    const now = clock.get() + dt;
    clock.set(now);
    const pin = pinned.get();
    const m = mode.get();
    if (pin >= 0 || (m === 0 && autoPlay && !reduce)) {
      const t = pin >= 0 ? pin : loopT.get() + dt;
      if (pin < 0) loopT.set(t);
      const k = lookAt(t);
      h.set(k.h);
      duck.set(k.duck);
      yaw.set(k.yaw);
      heat.set(k.heat);
      press.set(k.press);
      held.set(k.held);
      bead.set(k.bead);
      flash.set(k.flash);
      over.set(k.over);
    } else if (m === 0) {
      h.set(H_LIVE);
      duck.set(0);
      yaw.set(0);
      heat.set(0);
      press.set(0);
      held.set(0);
    } else {
      if (!reduce) bead.set(bead.get() + (dt * Math.PI * 2) / BEAD_PERIOD);
      if (m === 2) {
        const hv0 = Math.abs(speed.get());
        if (arrived.get() === 0 && Math.abs(h.get() - H_LIVE) < 0.004 && hv0 < 60) {
          arrived.set(1);
          arrivedAt.set(now);
          scheduleOnRN(onArrive);
        }
        if (arrived.get() === 1) {
          const tau = now - arrivedAt.get();
          flash.set(tau < 0.6 ? tau / 0.6 : -1);
          over.set(tau < 0.7 && !reduce ? 5 * Math.exp(-tau * 7) * Math.cos(tau * 24) : 0);
        }
        if (
          arrived.get() === 1 &&
          autoPlay &&
          !reduce &&
          now - touchedAt.get() > GESTURE.idleToLoop &&
          Math.abs(h.get() - H_LIVE) < 0.003 &&
          duck.get() < 0.6 &&
          Math.abs(yaw.get()) < 0.01 &&
          heat.get() < 0.04 &&
          held.get() < 0.02
        ) {
          mode.set(0);
          loopT.set(0);
        }
      }
    }
    // the dots' inertia: a fast scrub fans the loose dots into a tail behind the pill
    const hNow = h.get();
    const hv = (hNow - prevH.get()) / Math.max(dt, 0.001);
    prevH.set(hNow);
    speed.set(speed.get() + (hv * g.perY - speed.get()) * (1 - Math.exp(-dt / 0.05)));
    const pxNow = pillX(g, hNow);
    const pv = (pxNow - prevPill.get()) / Math.max(dt, 0.001);
    prevPill.set(pxNow);
    const target = Math.max(-DOTS_TAIL, Math.min(DOTS_TAIL, (pv / S) * 0.03));
    tail.set(tail.get() + (target - tail.get()) * (1 - Math.exp(-dt / 0.09)));
    const sp = speed.get();
    const wantDir = Math.abs(sp) > 220 && sp < 0 ? -1 : 1; // the arrow turns down while the line moves down the screen
    gizDir.set(gizDir.get() + (wantDir - gizDir.get()) * (1 - Math.exp(-dt / 0.08)));
  });

  // ---- touch ----
  const stopAll = () => {
    'worklet';
    cancelAnimation(h);
    cancelAnimation(duck);
    cancelAnimation(yaw);
    cancelAnimation(heat);
    cancelAnimation(press);
    cancelAnimation(held);
  };
  const release = (vy: number) => {
    'worklet';
    mode.set(2);
    touchedAt.set(clock.get());
    arrived.set(0);
    const dist = Math.abs(h.get() - H_LIVE);
    const v = Math.max(-1.5, Math.min(1.5, -vy / g.perY));
    const heatNow = Math.min(1, heat.get() + dist * HEAT.gain);
    heat.set(heatNow);
    const tau = HEAT.tau * (1 + Math.abs(v * 500) / HEAT.tauV);
    if (reduce) {
      h.set(withTiming(H_LIVE, { duration: 200 }));
      yaw.set(withTiming(0, { duration: 200 }));
      duck.set(withTiming(0, { duration: 200 }));
      press.set(withTiming(0, { duration: 200 }));
      held.set(withDelay(60, withTiming(0, { duration: 140 })));
      heat.set(0);
      return;
    }
    h.set(withSpring(H_LIVE, { ...SPRING.catchUp, velocity: v }));
    yaw.set(withSpring(0, SPRING.yaw));
    duck.set(withDelay(700, withSpring(0, SPRING.duck)));
    press.set(withSpring(0, SPRING.touch));
    held.set(withDelay(760, withTiming(0, { duration: 140 })));
    heat.set(withTiming(0, { duration: tau * 3000, easing: Easing.out(Easing.exp) }));
  };

  const pan = Gesture.Pan()
    .minDistance(0)
    .onBegin(() => {
      stopAll();
      mode.set(1);
      touchedAt.set(clock.get());
      arrived.set(1);
      flash.set(-1);
      over.set(0);
      grip.set({ h0: h.get(), moved: false, tickAt: clock.get(), tickLayer: layerAt(h.get()), atEnd: false });
      duck.set(withSpring(reduce ? 0 : GESTURE.duckPt, SPRING.duck));
      press.set(withSpring(1, SPRING.touch));
      held.set(withTiming(1, { duration: 120 }));
      scheduleOnRN(haptic.onset);
    })
    .onUpdate((e) => {
      const gr = grip.get();
      const raw = gr.h0 - e.translationY / g.perY;
      let hv = raw;
      let end = false;
      if (raw < 0) {
        hv = -rubber(-raw * g.perY, GESTURE.rubberPt) / g.perY;
        end = true;
      } else if (raw > 1) {
        hv = 1 + rubber((raw - 1) * g.perY, GESTURE.rubberPt) / g.perY;
        end = true;
      }
      h.set(hv);
      if (!reduce) {
        const y = Math.max(-GESTURE.yawMax, Math.min(GESTURE.yawMax, e.translationX * GESTURE.yawPerPt));
        yaw.set((y * Math.PI) / 180);
      }
      const layer = layerAt(clamp01(hv));
      const now = clock.get();
      if (Math.floor(layer / GESTURE.tickEvery) !== Math.floor(gr.tickLayer / GESTURE.tickEvery) && now - gr.tickAt > 0.06) {
        scheduleOnRN(haptic.tick);
        grip.set({ ...gr, moved: true, tickAt: now, tickLayer: layer, atEnd: end });
      } else if (end && !gr.atEnd) {
        scheduleOnRN(haptic.limit);
        grip.set({ ...gr, moved: true, atEnd: true });
      } else if (!end && gr.atEnd) {
        grip.set({ ...gr, atEnd: false });
      }
    })
    .onFinalize((e) => {
      release(e.velocityY ?? 0);
    });

  const a11yStep = (dir: number) => {
    stopAll();
    mode.set(2);
    touchedAt.set(clock.get());
    arrived.set(0);
    const hh = clamp01(h.get() + dir * 0.05);
    setA11yPct(Math.round(percentAt(hh)));
    setA11yLayer(layerAt(hh));
    h.set(withSequence(withTiming(hh, { duration: 200 }), withDelay(1800, withSpring(H_LIVE, SPRING.catchUp))));
    heat.set(withSequence(withTiming(Math.min(1, heat.get() + 0.3), { duration: 120 }), withDelay(2000, withTiming(0, { duration: 900 }))));
    duck.set(withSequence(withSpring(reduce ? 0 : GESTURE.duckPt, SPRING.duck), withDelay(2400, withSpring(0, SPRING.duck))));
    held.set(withSequence(withTiming(1, { duration: 120 }), withDelay(2500, withTiming(0, { duration: 140 }))));
  };
  const onA11yAction = (e: AccessibilityActionEvent) => {
    if (e.nativeEvent.actionName === 'increment') a11yStep(1);
    else if (e.nativeEvent.actionName === 'decrement') a11yStep(-1);
  };

  // ---- derived drawing values ----
  const band = useDerivedValue(() =>
    reduce ? 4 : mix(HEAT.bandMin, HEAT.bandMax, clamp01(Math.max(heat.get(), Math.abs(speed.get()) / HEAT.speedFull))),
  );
  const scene = useDerivedValue(() =>
    sceneUniforms(g, { h: h.get(), yaw: yaw.get(), band: band.get(), heat: reduce ? 0 : heat.get(), bead: bead.get() }, P3, PX),
  );
  const cy = useDerivedValue(() => capsuleCy(g, h.get(), duck.get()));
  const sc = useDerivedValue(() => 1 + 0.02 * press.get());
  const glass = useDerivedValue(() => {
    const u = glassUniforms(
      g,
      { h: h.get(), yaw: yaw.get(), band: band.get(), heat: reduce ? 0 : heat.get(), bead: bead.get() },
      cy.get(),
      P3,
      PX,
    );
    const k = sc.get();
    u.uLens = [u.uLens[0], u.uLens[1], u.uLens[2] * k, u.uLens[3] * k];
    u.uLensR = u.uLensR * k;
    return u;
  });
  const capClip = useDerivedValue(() => {
    const k = sc.get();
    const w = g.capW * k;
    const hh = g.capH * k;
    return Skia.RRectXY(Skia.XYWHRect(g.capL + (g.capW - w) / 2, cy.get() - hh / 2, w, hh), g.capR * k, g.capR * k);
  });
  const capPath = useDerivedValue(() => {
    const pb = Skia.PathBuilder.Make();
    pb.addRRect(capClip.get());
    return pb.build();
  });
  const frostRect = useDerivedValue(() => Skia.XYWHRect(g.capL - 30, cy.get() - g.capH / 2 - 30, g.capW + 60, g.capH + 60));
  const barU = useDerivedValue(() => barUniforms(g, h.get(), cy.get(), over.get() * S, tail.get(), flash.get(), P3));
  const barRect = useDerivedValue(() => Skia.XYWHRect(g.capL + g.barX - 6, cy.get() - g.capH / 2, g.barW + 12, g.capH));
  const planeScreenY = useDerivedValue(() => planeY(g, h.get()));
  const ambXf = useDerivedValue(() => [{ translateY: (12 + 8 * press.get()) * S }]);
  const stripXf = useDerivedValue(() => [{ translateY: cy.get() + g.capH / 2 - 14 * S + 18 * S * (1 + 0.1 * press.get()) }]);
  const tintStart = useDerivedValue(() => vec(0, cy.get() - g.capH / 2));
  const tintEnd = useDerivedValue(() => vec(0, cy.get() + g.capH / 2));
  // rim light from the hot layer: strongest where the capsule's edge is nearest the line
  const rimHeat = useDerivedValue(() => {
    const prox = clamp01(1 - Math.abs(planeScreenY.get() - (cy.get() - g.capH / 2)) / (70 * S));
    return clamp01(0.12 + 0.4 * prox + 0.35 * heat.get());
  });
  const rimStart = useDerivedValue(() => vec(0, planeScreenY.get() - 70 * S));
  const rimEnd = useDerivedValue(() => vec(0, planeScreenY.get() + 70 * S));
  const rimColors = useDerivedValue(() => {
    const a = rimHeat.get();
    return ['rgba(255,106,58,0)', `rgba(255,150,70,${(0.9 * a).toFixed(3)})`, 'rgba(255,106,58,0)'];
  });
  const labelA = useDerivedValue(() => 1 - held.get());
  const labelB = useDerivedValue(() => held.get());
  const labelY = useDerivedValue(() => cy.get() - g.capH / 2 + ref.capLabel.y * S);
  const layerText = useDerivedValue(() => `${COPY.layer} ${layerAt(h.get())} ${COPY.of}`);
  const pctStr = useDerivedValue(() => `${Math.round(percentAt(h.get()))}%`);
  const pillCx = useDerivedValue(() => g.capL + g.barX + pillX(g, h.get()));
  const pillCy = useDerivedValue(() => cy.get() - g.capH / 2 + g.barY + g.barH / 2);
  const pillShadowCy = useDerivedValue(() => pillCy.get() + 2 * S);
  const pctX = useDerivedValue(() => {
    if (!fontMed) return 0;
    const ws = fontMed.getGlyphWidths(fontMed.getGlyphIDs(pctStr.get()));
    let w = 0;
    for (let i = 0; i < ws.length; i++) w += ws[i];
    return pillCx.get() - w / 2;
  });
  const pctY = useDerivedValue(() => pillCy.get() + 0.35 * ref.capLabel.size * S);
  const contentXf = useDerivedValue(() => {
    const k = sc.get();
    const cxc = g.capL + g.capW / 2;
    const cyc = cy.get();
    return [{ translateX: cxc }, { translateY: cyc }, { scale: k }, { translateX: -cxc }, { translateY: -cyc }];
  });
  // the gizmo at the seam: the arrow stretches on touch and with speed and turns with the finger's direction
  const gizLen = useDerivedValue(
    () => Math.min(ref.gizmo.max, ref.gizmo.rest + (ref.gizmo.touch - ref.gizmo.rest) * press.get() + 0.06 * Math.abs(speed.get())) * S,
  );
  const gizXf = useDerivedValue(() => [{ translateX: g.axisX }, { translateY: planeScreenY.get() }, { scaleY: gizDir.get() }]);
  const gizOpacity = useDerivedValue(() => 0.7 + 0.3 * press.get());
  const shaftEnd = useDerivedValue(() => vec(0, -gizLen.get() + ref.gizmo.head * S));
  const headXf = useDerivedValue(() => [{ translateY: -gizLen.get() + ref.gizmo.head * S }, { scale: S }]);
  const handleStyle = useAnimatedStyle(() => ({ transform: [{ translateY: cy.get() - g.capH / 2 }] }));

  // the file name set with tracking (glyph by glyph: Skia text has none)
  const fileGlyphs = useMemo(() => {
    if (!fontSmall) return [];
    const ids = fontSmall.getGlyphIDs(COPY.file);
    const ws = fontSmall.getGlyphWidths(ids);
    const tr = ref.file.tracking * ref.file.size * S;
    let x = 0;
    return COPY.file.split('').map((ch, i) => {
      const gx = x;
      x += ws[i] + tr;
      return { ch, x: gx };
    });
  }, [fontSmall, S]);

  const hdr = ref.header;
  const hdrX = L.x(hdr.x);
  const hdrW = hdr.w * S;
  const hdrH = hdr.h * S;
  const dock = ref.dock;
  const dockW = (ref.dockBtn.x0 * 2 + 3 * ref.dockBtn.d + 2 * ref.dockBtn.gap) * S;
  const ws = ref.workspace;
  const wsW = ws.w * S;
  const wsH = ws.h * S;
  const wsX = hdrX + hdrW - 4 * S - wsW;
  const wsY = g.hdrY + (hdrH - wsH) / 2;
  const chevronX = hdrX + ref.file.x * S + (fileGlyphs.length ? fileGlyphs[fileGlyphs.length - 1].x + 14 * S : 140 * S) + 8 * S;
  const wsLabelW = fontMed ? fontMed.getGlyphWidths(fontMed.getGlyphIDs(COPY.workspace)).reduce((a, b) => a + b, 0) : 0;

  const hdrRR = useMemo(
    () => Skia.RRectXY(Skia.XYWHRect(hdrX, g.hdrY, hdrW, hdrH), hdr.r * S, hdr.r * S),
    [hdrX, g.hdrY, hdrW, hdrH, hdr.r, S],
  );
  const dockRR = useMemo(
    () => Skia.RRectXY(Skia.XYWHRect(L.x(dock.x), g.dockY, dockW, dock.h * S), dock.r * S, dock.r * S),
    [L, g.dockY, dockW, dock.x, dock.h, dock.r, S],
  );

  const ready = !!(sceneFx && barFx && solidImage && ghostImage && fontReg && fontMed && fontSmall);

  return (
    <View style={styles.screen}>
      <StatusBar style="dark" />
      <View style={StyleSheet.absoluteFill} pointerEvents="none">
        {ready ? (
          <Canvas style={StyleSheet.absoluteFill}>
            <Fill>
              <Shader source={sceneFx!} uniforms={scene}>
                <ImageShader image={solidImage} tx="decal" ty="decal" />
                <ImageShader image={ghostImage} tx="decal" ty="decal" />
              </Shader>
            </Fill>
            <GizmoNodes xf={gizXf} opacity={gizOpacity} shaftEnd={shaftEnd} headXf={headXf} S={S} />
            {/* the strip the floating capsule throws on the figure */}
            <Group transform={stripXf}>
              <RoundedRect
                x={g.capL + 10 * S}
                y={0}
                width={g.capW - 20 * S}
                height={g.capH * 0.5}
                r={g.capR * 0.5}
                color="rgba(58,36,32,0.08)"
              >
                <BlurMask blur={12 * S} style="normal" />
              </RoundedRect>
            </Group>

            {/* header strip: glass over the grid */}
            <GlassStrip rr={hdrRR} S={S} />
            <Circle
              cx={hdrX + (ref.logo.x + ref.logo.d / 2) * S}
              cy={g.hdrY + (ref.logo.y + ref.logo.d / 2) * S}
              r={(ref.logo.d / 2) * S}
              color={C.white}
            />
            <Group
              transform={[
                { translateX: hdrX + (ref.logo.x + ref.logo.d / 2) * S },
                { translateY: g.hdrY + (ref.logo.y + ref.logo.d / 2) * S },
                { scale: 0.8 * S },
                { translateX: -12 },
                { translateY: -12 },
              ]}
            >
              {MARK_PATHS.map((p, i) => (
                <Path key={i} path={p} style="stroke" strokeWidth={3.2} strokeCap="round" color={C.iconInk} />
              ))}
            </Group>
            {fileGlyphs.map((gl, i) => (
              <Text
                key={i}
                x={hdrX + ref.file.x * S + gl.x}
                y={g.hdrY + hdrH / 2 + 0.36 * ref.file.size * S}
                text={gl.ch}
                font={fontSmall}
                color={C.inkSoft}
              />
            ))}
            <Group
              transform={[
                { translateX: chevronX },
                { translateY: g.hdrY + hdrH / 2 - 5.2 * S },
                { scale: 0.52 * S },
                { translateX: -12 },
                { translateY: -9 },
              ]}
            >
              <Path path={CHEVRON_PATH} style="stroke" strokeWidth={2.2} strokeCap="round" strokeJoin="round" color={C.inkSoft} />
            </Group>
            <RoundedRect x={wsX} y={wsY} width={wsW} height={wsH} r={ws.r * S} color={C.yellow} />
            <Text
              x={wsX + (wsW - wsLabelW) / 2}
              y={wsY + wsH / 2 + 0.35 * ws.size * S}
              text={COPY.workspace}
              font={fontMed}
              color={C.ink}
            />

            {/* dock: three tools */}
            <GlassStrip rr={dockRR} S={S} />
            <DockButtons x={L.x(dock.x) + ref.dockBtn.x0 * S} y={g.dockY + (dock.h * S - ref.dockBtn.d * S) / 2} S={S} />

            {/* the capsule: soft shadows, the frosted scene seen through a lens, tint, rim */}
            <Group transform={ambXf}>
              <Path path={capPath} color="rgba(58,36,32,0.13)">
                <BlurMask blur={12 * S} style="normal" />
              </Path>
            </Group>
            <Group transform={[{ translateY: 2 * S }]}>
              <Path path={capPath} color="rgba(58,36,32,0.16)">
                <BlurMask blur={2 * S} style="normal" />
              </Path>
            </Group>
            <Group clip={capClip}>
              <Group
                layer={
                  <Paint>
                    <Blur blur={ref.glass.frost * S} mode="clamp" />
                  </Paint>
                }
              >
                <Rect rect={frostRect}>
                  <Shader source={sceneFx!} uniforms={glass}>
                    <ImageShader image={solidImage} tx="decal" ty="decal" />
                    <ImageShader image={ghostImage} tx="decal" ty="decal" />
                  </Shader>
                </Rect>
                <GizmoNodes xf={gizXf} opacity={gizOpacity} shaftEnd={shaftEnd} headXf={headXf} S={S} />
              </Group>
              {/* white tint: brighter at the top, calmer below */}
              <Rect rect={frostRect}>
                <LinearGradient start={tintStart} end={tintEnd} colors={['rgba(255,255,255,0.40)', 'rgba(255,255,255,0.22)']} />
              </Rect>
              {/* inner shadow on the lower rim */}
              <Path path={capPath} style="stroke" strokeWidth={4 * S} color="rgba(58,36,32,0.10)">
                <BlurMask blur={2 * S} style="normal" />
              </Path>
            </Group>
            <Path path={capPath} style="stroke" strokeWidth={S}>
              <LinearGradient
                start={tintStart}
                end={tintEnd}
                colors={['rgba(255,255,255,0.95)', 'rgba(255,255,255,0.38)', 'rgba(255,255,255,0.2)']}
              />
            </Path>
            <Path path={capPath} style="stroke" strokeWidth={1.6 * S}>
              <LinearGradient start={rimStart} end={rimEnd} colors={rimColors} />
            </Path>

            <Group transform={contentXf}>
              <Text x={g.capL + ref.capLabel.x * S} y={labelY} text={COPY.title} font={fontReg} color={C.ink} opacity={labelA} />
              <Text x={g.capL + ref.capLabel.x * S} y={labelY} text={layerText} font={fontReg} color={C.ink} opacity={labelB} />
              <Rect rect={barRect}>
                <Shader source={barFx!} uniforms={barU} />
              </Rect>
              <Circle cx={pillCx} cy={pillShadowCy} r={(ref.pill.d / 2) * S} color="rgba(58,36,32,0.16)">
                <BlurMask blur={4 * S} style="normal" />
              </Circle>
              <Circle cx={pillCx} cy={pillCy} r={(ref.pill.d / 2) * S} color={C.white} />
              <Text x={pctX} y={pctY} text={pctStr} font={fontMed} color={C.ink} />
            </Group>
          </Canvas>
        ) : null}
      </View>
      <GestureDetector gesture={pan}>
        <Animated.View
          style={[styles.handle, { left: g.capL, width: g.capW, height: g.capH }, handleStyle]}
          accessible
          accessibilityRole="adjustable"
          accessibilityLabel={COPY.a11yLabel}
          accessibilityValue={{ text: `${a11yPct} percent, layer ${a11yLayer} of ${COPY.layers}` }}
          accessibilityActions={[{ name: 'increment' }, { name: 'decrement' }]}
          onAccessibilityAction={onA11yAction}
        />
      </GestureDetector>
    </View>
  );
}

const DOTS_TAIL = 22;

/** the blue arrow with its red and green stubs, rising from the seam */
function GizmoNodes({ xf, opacity, shaftEnd, headXf, S }: { xf: any; opacity: any; shaftEnd: any; headXf: any; S: number }) {
  return (
    <Group transform={xf} opacity={opacity}>
      <Group transform={[{ scale: S }]}>
        <Path path={AXIS_RED} style="stroke" strokeWidth={2} strokeCap="round" color={C.axisRed} />
        <Path path={AXIS_GREEN} style="stroke" strokeWidth={2} strokeCap="round" color={C.axisGreen} />
      </Group>
      <Line p1={vec(0, 0)} p2={shaftEnd} color={C.arrow} strokeWidth={ref.gizmo.shaft * S} strokeCap="round" />
      <Group transform={headXf}>
        <Path path={ARROW_HEAD} color={C.arrow} />
      </Group>
    </Group>
  );
}

/** a strip of Liquid Glass over the page: blur, tint, a bright upper rim and a soft shadow */
function GlassStrip({ rr, S }: { rr: SkRRect; S: number }) {
  const path = useMemo(() => {
    const pb = Skia.PathBuilder.Make();
    pb.addRRect(rr);
    return pb.build();
  }, [rr]);
  const top = rr.rect.y;
  return (
    <>
      <Group transform={[{ translateY: 6 * S }]}>
        <Path path={path} color="rgba(58,36,32,0.10)">
          <BlurMask blur={10 * S} style="normal" />
        </Path>
      </Group>
      <Group clip={rr}>
        <BackdropFilter filter={<Blur blur={10 * S} mode="clamp" />} />
        <Fill color="rgba(255,255,255,0.34)" />
      </Group>
      <Path path={path} style="stroke" strokeWidth={S}>
        <LinearGradient
          start={vec(0, top)}
          end={vec(0, top + rr.rect.height)}
          colors={['rgba(255,255,255,0.95)', 'rgba(255,255,255,0.4)', 'rgba(255,255,255,0.25)']}
        />
      </Path>
    </>
  );
}

/** the three tools in the dock: select (yellow), hand (pale blue), more */
function DockButtons({ x, y, S }: { x: number; y: number; S: number }) {
  const d = ref.dockBtn.d * S;
  const step = (ref.dockBtn.d + ref.dockBtn.gap) * S;
  const ic = (cx: number, cy: number, k: number) => [
    { translateX: cx },
    { translateY: cy },
    { scale: k },
    { translateX: -12 },
    { translateY: -12 },
  ];
  return (
    <>
      <Circle cx={x + d / 2} cy={y + d / 2} r={d / 2} color={C.yellow} />
      <Group transform={ic(x + d / 2, y + d / 2, 0.8 * S)}>
        <Path path={CURSOR_PATH} color={C.iconInk} />
        <Path path={CURSOR_PATH} style="stroke" strokeWidth={1.4} strokeJoin="round" color={C.iconInk} />
      </Group>
      <Circle cx={x + step + d / 2} cy={y + d / 2} r={d / 2} color={C.handBlue} />
      <Group transform={ic(x + step + d / 2, y + d / 2, 0.8 * S)}>
        {HAND_FINGER_PATHS.map((p, i) => (
          <Path key={i} path={p} style="stroke" strokeWidth={2.4} strokeCap="round" color={C.iconInk} />
        ))}
        <Path path={HAND_PALM_PATH} style="stroke" strokeWidth={2.4} strokeCap="round" strokeJoin="round" color={C.iconInk} />
      </Group>
      <Circle cx={x + 2 * step + d / 2} cy={y + d / 2} r={d / 2} color="rgba(255,255,255,0.7)" />
      <Circle cx={x + 2 * step + d / 2} cy={y + d / 2} r={2.2 * S} color={C.iconInk} />
    </>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: C.ground },
  handle: { position: 'absolute', top: 0 },
});
