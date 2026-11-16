// Flip Focus Badge — the badge itself: a small real 3D scene (scalloped black-glass plate, fat italic numeral that punches
// out of it, an orbit ring with two sparkles, a clock on its back) ray-marched live by one runtime effect, plus everything
// that makes it a thing you can hold: 1:1 drag for yaw and pitch with velocity hand-off to a spring that settles on the
// nearer face, a press-and-hold that presses the numeral flat into the glass, a ring coupled to the plate by a viscous
// joint, glints that run across the sparkles in the order the light reaches them, and a numeral exchange hidden behind the
// plate. The pin's own loop plays when `autoPlay` is set.
//
// Device motion: with `source` 'device' or 'auto' the badge counter-rotates against the phone's turn about its long axis
// (Reanimated's rotation sensor, no extra package); where the sensor reports nothing (a simulator) it falls back to the
// drag. A finger always wins.
//
//   <NumeralBadge value={streakDays} onFaceChange={(face) => face === 'back' && startSession()} />
import { type ReactNode, useCallback, useEffect, useMemo } from 'react';
import { type AccessibilityActionEvent, PixelRatio, Platform, StyleSheet, View, type StyleProp, type ViewStyle } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  cancelAnimation,
  isSharedValue,
  SensorType,
  type SharedValue,
  useAnimatedSensor,
  useAnimatedStyle,
  useDerivedValue,
  useFrameCallback,
  useSharedValue,
  withDelay,
  withSequence,
  withSpring,
  withTiming,
} from 'react-native-reanimated';
import { Canvas, FilterMode, ImageShader, MipmapMode, Path, Rect, Shader, Skia, useImage } from '@shopify/react-native-skia';
import { scheduleOnRN } from 'react-native-worklets';

import { useReducedMotionPreference } from '../../../shared/useReducedMotionPreference';
import { badgeFromLook, badgeUniforms, D2R, hex3 } from './ffb-geometry';
import { haptic, MIN_GAP_S, silence } from './ffb-haptics';
import { GESTURE, jointStep, limitLag, LOOP, lookAt, REST, rubber, SPRING, type Look } from './ffb-motion';
import { BADGE_SKSL } from './ffb-shaders';
import { COPY, ref, REF, SCENE } from './ffb-theme';

const atlasSource = require('./assets/ffb-glyphs.png');
const BADGE_FX = Skia.RuntimeEffect.Make(BADGE_SKSL);
const P3 = Platform.OS === 'ios' ? 1 : 0;
const VIEW_H = 300; // design pt: the badge's viewport (plate centre at its middle, ring and perspective inside)
const VIEW_CY = 150;

export type Face = 'front' | 'back';

export type NumeralBadgeProps = {
  /** the numeral on the front, 0 … 99 */
  value: number;
  /** the small label at the numeral's lower right; the glyph atlas draws "hr" (or nothing) */
  unit?: 'hr' | '';
  /** controlled face; `defaultFace` is the uncontrolled start */
  face?: Face;
  defaultFace?: Face;
  onFaceChange?: (face: Face) => void;
  /** the plate's angle in degrees; give a SharedValue to read or drive it */
  yaw?: SharedValue<number>;
  /** 'device' follows the phone's turn, 'drag' only the finger, 'auto' the device where a sensor reports, else the drag */
  source?: 'auto' | 'device' | 'drag';
  /** the session clock shown on the back, seconds (a SharedValue, so it can tick without rendering) */
  clock?: SharedValue<number>;
  /** the satellite's lap, 0 … 1 (minutes / 60); the ring's sparkle goes round once an hour */
  orbit?: { progress: number | SharedValue<number>; onLap?: () => void };
  /** the numeral's extrusion, 1 = the pin's; give a SharedValue to read it or press the number yourself */
  depth?: SharedValue<number>;
  /** numeral base colour, plate glass, enamel edge (hex); `gloss` is the clearcoat roughness (0.22: dry) */
  tint?: string;
  plate?: string;
  edge?: string;
  gloss?: number;
  /** the scene's width in pt (the pin is 402); the viewport is 300/402 of it tall */
  size?: number;
  haptics?: boolean;
  /** play the pin's own loop until a finger touches; 2 s after the last touch it eases back and plays again */
  autoPlay?: boolean;
  /** output: the badge's look, written every frame (the page reads its sheet, line and hint from it) */
  look?: SharedValue<Look>;
  /** output: true while the loop plays */
  playing?: SharedValue<boolean>;
  onTouchChange?: (down: boolean) => void;
  /** after an exchange: the numeral that has just punched out */
  onValueShown?: (value: number) => void;
  accessibilityLabel?: string;
  style?: StyleProp<ViewStyle>;
};

const MAX_SKEW = 3 * 180;

/** nearest face-on angle (a multiple of 180°) to a projected angle */
function nearestFace(angle: number) {
  'worklet';
  return Math.round(angle / 180) * 180;
}
function faceOf(angle: number): Face {
  'worklet';
  const k = Math.round(angle / 180);
  return ((k % 2) + 2) % 2 === 0 ? 'front' : 'back';
}

/** the Skia children: the shader in a rect, loaded atlas as its child (used inside a canvas by the badge and the screen) */
function Fallback({ w, h, k }: { w: number; h: number; k: number }) {
  // plate and ring as plain paths if the runtime effect cannot compile: the pin's pose, no motion
  const path = useMemo(() => {
    const p = Skia.Path.Make();
    for (let i = 0; i <= 240; i++) {
      const a = (i / 240) * Math.PI * 2;
      const r = (73.3 + 11.1 * Math.sqrt(Math.sin(6 * a) ** 2 + 0.012)) * k;
      const x = w / 2 + 5 * k + Math.cos(a) * r;
      const y = h / 2 + Math.sin(a) * r;
      if (i === 0) p.moveTo(x, y);
      else p.lineTo(x, y);
    }
    p.close();
    return p;
  }, [w, h, k]);
  return <Path path={path} color={ref.color.plate} />;
}

export function NumeralBadge({
  value,
  unit = 'hr',
  face,
  defaultFace = 'front',
  onFaceChange,
  yaw,
  source = 'auto',
  clock,
  orbit,
  depth,
  tint,
  plate,
  edge,
  gloss = 0.22,
  size = REF.w,
  haptics = true,
  autoPlay = false,
  look,
  playing,
  onTouchChange,
  onValueShown,
  accessibilityLabel,
  style,
}: NumeralBadgeProps) {
  const reduce = useReducedMotionPreference();
  const atlas = useImage(atlasSource);
  const k = size / REF.w;
  const w = size;
  const h = VIEW_H * k;
  const cx = SCENE.centre.x * k;
  const cy = VIEW_CY * k;
  const dpr = PixelRatio.get();
  const cue = haptics ? haptic : silence;
  const colors = useMemo(
    () => ({ tint: tint ? hex3(tint) : undefined, plate: plate ? hex3(plate) : undefined, edge: edge ? hex3(edge) : undefined }),
    [tint, plate, edge],
  );

  // ---- state (UI thread) ----------------------------------------------------------------------------------------------
  // Initial values are plain numbers: reading a shared value during render trips Reanimated's strict-mode warning.
  const yaw0 = defaultFace === 'back' ? 180 : 0;
  const yawOwn = useSharedValue(yaw0);
  const yawV = yaw ?? yawOwn;
  const pitchV = useSharedValue(0);
  const depthOwn = useSharedValue(1);
  const depthV = depth ?? depthOwn;
  const ringV = useSharedValue(yaw0);
  const ringW = useSharedValue(0);
  const valueV = useSharedValue(value);
  const target = useSharedValue(value);
  const risen = useSharedValue(true);
  const pressV = useSharedValue(1);
  const popPlus = useSharedValue(1);
  const popHr = useSharedValue(1);
  const holdV = useSharedValue(false);
  const touching = useSharedValue(false);
  const ended = useSharedValue(true);
  const lastTx = useSharedValue(0);
  const lastTy = useSharedValue(0);
  const lastTouch = useSharedValue(-1e9);
  const clockOwn = useSharedValue(0);
  const clockV = clock ?? clockOwn;
  const orbitOwn = useSharedValue(typeof orbit?.progress === 'number' ? orbit.progress : 0.4);
  const orbitV: SharedValue<number> = orbit && isSharedValue(orbit.progress) ? (orbit.progress as SharedValue<number>) : orbitOwn;
  const lastK = useSharedValue(Math.floor(yaw0 / 180));
  const lastCue = useSharedValue(-1);
  const lastFace = useSharedValue<Face>(defaultFace);
  const glintAt = useSharedValue(-10);
  const glintOn = useSharedValue(false);
  const fade = useSharedValue(1);
  const clockT = useSharedValue(0);
  const loopT = useSharedValue(0);
  const pinned = useSharedValue(-1);
  const isPlaying = useSharedValue(autoPlay);
  const resuming = useSharedValue(false);
  const lapped = useSharedValue(1e9); // the first frame lowers it to the current lap without a cue
  const devYaw = useSharedValue(0);
  const devOn = useSharedValue(false);
  const lookOwn = useSharedValue<Look>(REST);
  const lookV = look ?? lookOwn;
  const playingOwn = useSharedValue(false);
  const playingV = playing ?? playingOwn;

  useEffect(() => {
    isPlaying.set(autoPlay && !reduce);
    playingV.set(autoPlay && !reduce);
    if (autoPlay && !reduce) loopT.set(0);
  }, [autoPlay, reduce, isPlaying, playingV, loopT]);
  useEffect(() => {
    if (typeof orbit?.progress === 'number') orbitOwn.set(orbit.progress);
  }, [orbit?.progress, orbitOwn]);
  useEffect(() => {
    target.set(value);
  }, [value, target]);
  useEffect(() => {
    if (face === undefined) return;
    const want = face === 'back' ? 180 : 0;
    const cur = yawV.get();
    if (faceOf(cur) === face) return;
    const next = want + Math.round((cur - want) / 360) * 360;
    if (reduce) {
      fade.set(
        withSequence(
          withTiming(0, { duration: 100 }, (done) => {
            'worklet';
            if (done) yawV.set(next);
          }),
          withTiming(1, { duration: 100 }),
        ),
      );
    } else {
      yawV.set(withSpring(next, SPRING.settle));
    }
  }, [face, reduce, yawV, fade]);

  // Development only: `__ffb.pin(t)` freezes the loop at t s, `__ffb.play()` lets it run, `__ffb.state()` reads it.
  useEffect(() => {
    if (!__DEV__) return;
    const globals = globalThis as Record<string, unknown>;
    const previous = globals.__ffb;
    const hook = {
      pin: (t: number) => pinned.set(t),
      at: (t: number) => pinned.set(t),
      play: () => pinned.set(-1),
      state: () => ({ ...lookV.get(), loopT: loopT.get(), playing: isPlaying.get() }),
    };
    globals.__ffb = hook;
    return () => {
      if (globals.__ffb === hook) globals.__ffb = previous;
    };
  }, [pinned, lookV, loopT, isPlaying]);

  const emitFace = useCallback((f: Face) => onFaceChange?.(f), [onFaceChange]);
  const emitTouch = useCallback((down: boolean) => onTouchChange?.(down), [onTouchChange]);
  const emitShown = useCallback((v: number) => onValueShown?.(v), [onValueShown]);
  const emitLap = useCallback(() => orbit?.onLap?.(), [orbit]);

  const sensorOn = source !== 'drag';

  // ---- the frame ----------------------------------------------------------------------------------------------------
  const cueOnce = (fn: () => void, now: number) => {
    'worklet';
    if (now - lastCue.get() < MIN_GAP_S) return;
    lastCue.set(now);
    scheduleOnRN(fn);
  };
  const startRise = () => {
    'worklet';
    depthV.set(withSpring(1, SPRING.rise));
    popPlus.set(withDelay(40, withSpring(1, SPRING.pop)));
    popHr.set(withDelay(80, withSpring(1, SPRING.pop)));
    risen.set(true);
    scheduleOnRN(emitShown, target.get());
  };
  const frames = useFrameCallback((info) => {
    'worklet';
    const dt = Math.min((info.timeSincePreviousFrame ?? 16.7) / 1000, 0.05);
    const now = clockT.get() + dt;
    clockT.set(now);
    const pin = pinned.get();
    if (pin >= 0) {
      lookV.set(lookAt(pin));
      return;
    }
    if (isPlaying.get()) {
      const t = (loopT.get() + dt) % LOOP;
      loopT.set(t);
      lookV.set(lookAt(t));
      return;
    }
    // idle for 2 s after a touch: ease the live badge back to the pin's pose, then the loop plays again
    if (autoPlay && !reduce && !touching.get()) {
      const idle = now - lastTouch.get();
      if (idle > GESTURE.idleResume && !resuming.get()) {
        resuming.set(true);
        const home = Math.round(yawV.get() / 360) * 360;
        yawV.set(withSpring(home, SPRING.glide));
        pitchV.set(withSpring(0, SPRING.glide));
        depthV.set(withSpring(1, SPRING.glide));
        pressV.set(withSpring(1, SPRING.glide));
        popPlus.set(1);
        popHr.set(1);
      }
      if (idle > GESTURE.idleResume + GESTURE.resumeMs / 1000 + 0.5 && resuming.get()) {
        resuming.set(false);
        loopT.set(0);
        isPlaying.set(true);
        playingV.set(true);
        lookV.set(lookAt(0));
        return;
      }
    }
    // device motion: the badge stays put in the room while the phone turns round it
    if (devOn.get() && !touching.get() && !resuming.get()) {
      const y = yawV.get();
      const diff = ((((devYaw.get() - y) % 360) + 540) % 360) - 180; // the short way round
      yawV.set(y + diff * Math.min(dt / 0.06, 1));
      const f = faceOf(yawV.get());
      if (f !== lastFace.get()) {
        lastFace.set(f);
        scheduleOnRN(emitFace, f);
        scheduleOnRN(cue.land);
      }
    }
    const yawNow = yawV.get();
    // the ring chases the plate through a viscous joint and keeps turning a little after the plate stops
    let ringYaw = yawNow;
    if (!reduce) {
      const [th, om] = jointStep(ringV.get(), ringW.get(), yawNow, dt);
      ringV.set(th);
      ringW.set(om);
      ringYaw = limitLag(yawNow, th);
    } else {
      ringV.set(yawNow);
    }
    // a numeral exchange waits until the front is hidden (turned away or edge-on); it punches out again as it returns
    const front = Math.cos(yawNow * D2R);
    if (Math.round(valueV.get()) !== Math.round(target.get()) && risen.get()) {
      if (front < 0.02) {
        valueV.set(target.get());
        cancelAnimation(depthV);
        depthV.set(GESTURE.holdDepth);
        popPlus.set(0.6);
        popHr.set(0.6);
        risen.set(false);
      } else if (front > 0.5 && !holdV.get()) {
        // facing us while it changes: press flat, exchange, punch out
        risen.set(false);
        depthV.set(
          withTiming(GESTURE.holdDepth, { duration: 160 }, (done) => {
            'worklet';
            if (done) {
              valueV.set(target.get());
              popPlus.set(0.6);
              popHr.set(0.6);
              startRise();
            }
          }),
        );
      }
    }
    if (!risen.get() && front > 0.25 && Math.round(valueV.get()) === Math.round(target.get()) && !holdV.get()) {
      startRise();
      if (!touching.get()) scheduleOnRN(cue.success);
    }
    // detents at every face-on angle crossed by a finger or a settling spring
    const kk = Math.floor(yawNow / 180);
    if (kk !== lastK.get()) {
      lastK.set(kk);
      if (touching.get()) cueOnce(cue.detent, now);
    }
    // the satellite's lap
    const lap = Math.floor(orbitV.get());
    if (lap > lapped.get()) {
      lapped.set(lap);
      glintAt.set(now);
      glintOn.set(true);
      scheduleOnRN(emitLap);
      scheduleOnRN(cue.lap);
    } else if (lap < lapped.get()) lapped.set(lap);
    // glints: the sparkles catch the light in turn, 60 ms apart
    const g = now - glintAt.get();
    const pulse = (c: number) => {
      'worklet';
      const x = (g - c) / 0.07;
      return glintOn.get() && !reduce ? Math.exp(-x * x) : 0;
    };
    // sway of the studio: a hand's tremor, 0.12 Hz (reduced motion: still)
    const env = reduce ? 0 : ((5 * Math.PI) / 180) * Math.sin(2 * Math.PI * 0.123 * now);
    const orbitFrac = orbitV.get() - Math.floor(orbitV.get());
    lookV.set({
      ...REST,
      yaw: yawNow,
      pitch: pitchV.get(),
      ringYaw,
      depth: depthV.get(),
      value: valueV.get(),
      clock: clockV.get(),
      sat: orbitFrac,
      glintPlus: pulse(0.1),
      glintHead: pulse(0.16),
      glintSat: pulse(0.22),
      press: pressV.get(),
      popPlus: popPlus.get(),
      popHr: popHr.get(),
      env,
      tPhase: now,
    });
  });
  useEffect(() => {
    frames.setActive(true);
    return () => frames.setActive(false);
  }, [frames]);

  // ---- touch ------------------------------------------------------------------------------------------------------
  const begin = () => {
    'worklet';
    const now = clockT.get();
    if (isPlaying.get()) {
      // the finger takes the badge from wherever the loop has it: no jump
      const l = lookV.get();
      yawV.set(l.yaw);
      pitchV.set(l.pitch);
      depthV.set(l.depth);
      ringV.set(l.ringYaw);
      ringW.set(0);
      valueV.set(l.value);
      popPlus.set(l.popPlus);
      popHr.set(l.popHr);
      pressV.set(l.press);
      lastK.set(Math.floor(l.yaw / 180));
      lastFace.set(faceOf(l.yaw));
      risen.set(true);
    }
    isPlaying.set(false);
    playingV.set(false);
    resuming.set(false);
    touching.set(true);
    cancelAnimation(yawV);
    cancelAnimation(pitchV);
    cancelAnimation(pressV);
    lastTouch.set(now);
    pressV.set(withSpring(1.03, SPRING.snap));
    scheduleOnRN(cue.onset);
    scheduleOnRN(emitTouch, true);
  };
  const finish = () => {
    'worklet';
    touching.set(false);
    lastTouch.set(clockT.get());
    pressV.set(withSpring(1, SPRING.snap));
    scheduleOnRN(emitTouch, false);
  };
  const land = (to: number) => {
    'worklet';
    const f = faceOf(to);
    if (f !== lastFace.get()) {
      lastFace.set(f);
      scheduleOnRN(emitFace, f);
      scheduleOnRN(cue.land);
    }
  };
  // release: carry the finger's velocity into a spring that settles on the nearer face (a tap settles with none)
  const settle = (vx: number, vy: number) => {
    'worklet';
    const v = vx * GESTURE.yawPerPt; // deg/s
    const cur = yawV.get();
    const travel = Math.max(-MAX_SKEW, Math.min(MAX_SKEW, v * GESTURE.project));
    const to = nearestFace(cur + travel);
    yawV.set(
      withSpring(to, { ...SPRING.settle, velocity: v }, (done) => {
        'worklet';
        if (done) land(to);
      }),
    );
    pitchV.set(withSpring(0, { ...SPRING.tilt, velocity: -vy * GESTURE.pitchPerPt }));
  };
  const pan = Gesture.Pan()
    .minDistance(2)
    .onBegin(() => {
      lastTx.set(0);
      lastTy.set(0);
      ended.set(false);
      begin();
    })
    .onUpdate((e) => {
      // 1:1: yaw 0.6° per pt, pitch 0.35° per pt (clamped at ±28° with a rubber band)
      const dx = e.translationX - lastTx.get();
      const dy = e.translationY - lastTy.get();
      lastTx.set(e.translationX);
      lastTy.set(e.translationY);
      yawV.set(yawV.get() + dx * GESTURE.yawPerPt);
      pitchV.set(rubber(pitchV.get() + dy * GESTURE.pitchPerPt, GESTURE.pitchMax, GESTURE.pitchRubber));
    })
    .onEnd((e) => {
      ended.set(true);
      settle(e.velocityX, e.velocityY);
    })
    .onFinalize(() => {
      if (!ended.get()) {
        ended.set(true);
        settle(0, 0);
      }
      finish();
    });
  const hold = Gesture.LongPress()
    .minDuration(GESTURE.holdMs)
    .maxDistance(18)
    .onStart(() => {
      // the numeral presses into the glass while the finger holds it, and pops back out on release
      holdV.set(true);
      cancelAnimation(depthV);
      depthV.set(withSpring(GESTURE.holdDepth, SPRING.snap));
      scheduleOnRN(cue.press);
    })
    .onFinalize(() => {
      if (!holdV.get()) return;
      holdV.set(false);
      depthV.set(withSpring(1, SPRING.rise));
      popPlus.set(withSequence(withTiming(0.6, { duration: 1 }), withDelay(40, withSpring(1, SPRING.pop))));
      popHr.set(withSequence(withTiming(0.6, { duration: 1 }), withDelay(80, withSpring(1, SPRING.pop))));
      scheduleOnRN(cue.press);
    });
  const gesture = Gesture.Simultaneous(pan, hold);

  // ---- the uniforms --------------------------------------------------------------------------------------------------
  const uniforms = useDerivedValue(() => {
    const l = lookV.get();
    const u = badgeUniforms(badgeFromLook(l));
    return {
      ...u,
      uTint: colors.tint ?? u.uTint,
      uEdge: colors.edge ?? u.uEdge,
      uPlate: colors.plate ?? u.uPlate,
      uMat: [u.uMat[0], gloss, u.uMat[2], u.uMat[3]],
      uC: [cx, cy],
      uS: k,
      uPx: 1 / (k * dpr),
      uAux: [P3, 0, reduce ? 1 : 0, unit === 'hr' ? 1 : 0],
    };
  });
  const canvasStyle = useAnimatedStyle(() => ({ opacity: fade.get() }));

  // ---- accessibility: a button in place of the gesture ----------------------------------------------------------------
  const onA11y = (ev: AccessibilityActionEvent) => {
    if (ev.nativeEvent.actionName !== 'activate') return;
    const cur = yawV.get();
    const to = nearestFace(cur) + 180;
    isPlaying.set(false);
    playingV.set(false);
    if (reduce) {
      fade.set(
        withSequence(
          withTiming(0, { duration: 100 }, (done) => {
            'worklet';
            if (done) yawV.set(to);
          }),
          withTiming(1, { duration: 100 }),
        ),
      );
      emitFace(faceOf(to));
    } else {
      yawV.set(
        withSpring(to, SPRING.settle, (done) => {
          'worklet';
          if (done) land(to);
        }),
      );
    }
  };

  const label = accessibilityLabel ?? COPY.badgeA11y(Math.round(value));
  const ready = atlas !== null;
  let canvasBody: ReactNode = null;
  if (ready && BADGE_FX) {
    canvasBody = (
      <Rect x={0} y={0} width={w} height={h}>
        <Shader source={BADGE_FX} uniforms={uniforms}>
          <ImageShader
            image={atlas}
            tx="clamp"
            ty="clamp"
            fit="none"
            rect={{ x: 0, y: 0, width: 1120, height: 1080 }}
            sampling={{ filter: FilterMode.Linear, mipmap: MipmapMode.None }}
          />
        </Shader>
      </Rect>
    );
  } else if (!BADGE_FX) canvasBody = <Fallback w={w} h={h} k={k} />;

  return (
    <View
      style={[{ width: w, height: h }, style]}
      accessible
      accessibilityRole="button"
      accessibilityLabel={label}
      accessibilityHint={COPY.startA11y}
      accessibilityActions={[{ name: 'activate', label: face === 'back' ? COPY.stopA11y : COPY.startA11y }]}
      onAccessibilityAction={onA11y}
    >
      <GestureDetector gesture={gesture}>
        <Animated.View style={[StyleSheet.absoluteFill, canvasStyle]} collapsable={false}>
          <Canvas style={StyleSheet.absoluteFill} accessible={false}>
            {canvasBody}
          </Canvas>
        </Animated.View>
      </GestureDetector>
      {sensorOn ? <DeviceTurn yawOut={devYaw} activeOut={devOn} /> : null}
    </View>
  );
}

/** Counter-rotation against the phone's turn about its long axis, from Reanimated's rotation sensor (no extra package).
 * Mounted only when a sensor may be used; reports `activeOut` only while the sensor delivers values. */
function DeviceTurn({ yawOut, activeOut }: { yawOut: SharedValue<number>; activeOut: SharedValue<boolean> }) {
  const sensor = useAnimatedSensor(SensorType.ROTATION, { interval: 16 });
  const zero = useSharedValue<number | null>(null);
  const seen = useSharedValue(0);
  useFrameCallback(() => {
    'worklet';
    const s = sensor.sensor.get();
    const roll = s.roll;
    if (typeof roll !== 'number' || !Number.isFinite(roll) || (s.qw === 0 && s.qx === 0 && s.qy === 0 && s.qz === 0)) {
      activeOut.set(false);
      return;
    }
    seen.set(seen.get() + 1);
    if (zero.get() === null) zero.set(roll);
    const base = zero.get() ?? roll;
    // about the device's long axis: the badge stays in the room, so it turns the other way
    yawOut.set(-((roll - base) / D2R));
    activeOut.set(seen.get() > 5);
  });
  return null;
}
