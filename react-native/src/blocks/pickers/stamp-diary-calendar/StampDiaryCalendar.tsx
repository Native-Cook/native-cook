import {
  Blur,
  BlurMask,
  Canvas,
  FilterMode,
  Group,
  ImageShader,
  LinearGradient,
  Mask,
  MipmapMode,
  Rect,
  Shader,
  Skia,
  useImage,
  vec,
  type SkImage,
} from '@shopify/react-native-skia';
import { BlurTargetView, BlurView } from 'expo-blur';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { type RefObject, useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { AccessibilityInfo, AppState, Platform, Pressable, StyleSheet, Text, useWindowDimensions, View } from 'react-native';
import { Gesture, GestureDetector, GestureHandlerRootView } from 'react-native-gesture-handler';
import Animated, { Easing, useAnimatedStyle, useDerivedValue, useFrameCallback, useReducedMotion, useSharedValue, withDelay, withSequence, withTiming, type SharedValue } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import Svg, { Circle, Line } from 'react-native-svg';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';

import {
  addMonths,
  cellCenter,
  cellOf,
  clamp01,
  dayAt,
  daysIn,
  detachCurl,
  fingerPose,
  firstWeekday,
  holesOf,
  holesOfQuad,
  isMay2026,
  landingQuad,
  LOOP,
  MAY_2026,
  memoryIndex,
  mix,
  peelShare,
  PEEL,
  quietPose,
  restPose,
  settle,
  STAMP_PAD,
  storyPose,
  T,
  TOUCH,
  type Curl,
  type Month,
  type Pose,
} from './motion';
import { makePostmark } from './postmark';
import { STAMP_SKSL } from './shaders';
import {
  colors,
  glass,
  grid,
  HERO_BOTTOM,
  MEMORIES,
  MONTH_NAMES,
  PERFORATION,
  PILE,
  INK_MARK,
  menu,
  POSTMARK,
  radii,
  REF,
  SF,
  sizes,
  space,
  type,
  WEEKDAYS,
  type StampQuad,
} from './theme';

const STAMP_EFFECT = Skia.RuntimeEffect.Make(STAMP_SKSL);
const SAMPLING = { filter: FilterMode.Linear, mipmap: MipmapMode.Linear } as const;
const ART_SIZE = 420;
const AUTO_MEMORY = 3; // May 12, the wreath

export type StampDiaryCalendarProps = { autoPlay?: boolean };

/** Controller modes (UI thread). */
const AUTO = 0;
const FINGER = 1;
const SNAP = 2;
const STORY = 3;
const IDLE = 4;
const QUIET = 5;

type Ctl = {
  mode: number;
  memory: number;
  t0: number;
  tau0: number;
  stopAt: number;
  prevTau: number;
  lastTouch: number;
  cornerX: number;
  cornerY: number;
  dx: number;
  dy: number;
  moved: boolean;
  lift: number;
  release: Curl;
  quietShow: boolean;
  restSent: boolean;
  /** Story speed: 1, or RETURN_RATE when a tap sends a stamp home. */
  rate: number;
};

function clock(): number {
  'worklet';
  return performance.now() / 1000;
}

function cropFor(w: number, h: number): number[] {
  'worklet';
  if (w < h) {
    const cw = (ART_SIZE * w) / h;
    return [(ART_SIZE - cw) / 2, 0, cw, ART_SIZE];
  }
  const ch = (ART_SIZE * h) / w;
  return [0, (ART_SIZE - ch) / 2, ART_SIZE, ch];
}

/** 4×4 row-major matrix: translate, shear (y += k·x), perspective tilt, spin, scale. */
function stampMatrix(cx: number, cy: number, k: number, rotX: number, rotY: number, rotZ: number, sx: number, sy: number, depth: number): number[] {
  'worklet';
  const d2r = Math.PI / 180;
  const ax = rotX * d2r;
  const ay = rotY * d2r;
  const az = rotZ * d2r;
  const cX = Math.cos(ax);
  const sX = Math.sin(ax);
  const cY = Math.cos(ay);
  const sY = Math.sin(ay);
  const cZ = Math.cos(az);
  const sZ = Math.sin(az);
  // R = Rx · Ry · Rz applied to (x, y, 0); then perspective; then shear; then translate.
  const r00 = cY * cZ;
  const r01 = -cY * sZ;
  const r10 = sX * sY * cZ + cX * sZ;
  const r11 = -sX * sY * sZ + cX * cZ;
  const r20 = -cX * sY * cZ + sX * sZ;
  const r21 = cX * sY * sZ + sX * cZ;
  // Columns of the 2D part after scale.
  const a = r00 * sx;
  const b = r01 * sy;
  const c = r10 * sx;
  const e = r11 * sy;
  const g = r20 * sx;
  const h = r21 * sy;
  // Perspective: w = 1 - z / depth (z towards the viewer is +), applied to the projected point.
  const pz0 = -g / depth;
  const pz1 = -h / depth;
  // Shear y += k x, then translate (cx, cy); the w row stays.
  return [
    a + cx * pz0, b + cx * pz1, 0, cx,
    c + k * a + cy * pz0, e + k * b + cy * pz1, 0, cy,
    0, 0, 1, 0,
    pz0, pz1, 0, 1,
  ];
}

function selectionHaptic() {
  if (Platform.OS === 'web') return;
  Haptics.selectionAsync().catch(() => {});
}
function lightHaptic() {
  if (Platform.OS === 'web') return;
  Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
}
function landHaptic() {
  if (Platform.OS === 'web') return;
  Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Medium).catch(() => {});
}

export function StampDiaryCalendar({ autoPlay = false }: StampDiaryCalendarProps) {
  const { width, height } = useWindowDimensions();
  const insets = useSafeAreaInsets();
  const s = width / REF.width;
  const oy = insets.top - REF.safeTop * s;

  const [month, setMonth] = useState<Month>(MAY_2026);
  const [actorMemory, setActorMemory] = useState(AUTO_MEMORY);
  const [emptyDay, setEmptyDay] = useState(0);
  const [reduceMotion, setReduceMotion] = useState(useReducedMotion());
  const [screenReader, setScreenReader] = useState(false);
  const [menuOpen, setMenuOpen] = useState(false);
  /** What the header's and the menu's smoked glass blur on Android (iOS blurs whatever is behind). */
  const blurTarget = useRef<View>(null);
  const may = isMay2026(month);

  const art = [
    useImage(require('./assets/StampDiaryCalendar-stamp-01.jpg')),
    useImage(require('./assets/StampDiaryCalendar-stamp-02.jpg')),
    useImage(require('./assets/StampDiaryCalendar-stamp-03.jpg')),
    useImage(require('./assets/StampDiaryCalendar-stamp-04.jpg')),
    useImage(require('./assets/StampDiaryCalendar-stamp-05.jpg')),
    useImage(require('./assets/StampDiaryCalendar-stamp-06.jpg')),
    useImage(require('./assets/StampDiaryCalendar-stamp-07.jpg')),
    useImage(require('./assets/StampDiaryCalendar-stamp-08.jpg')),
    useImage(require('./assets/StampDiaryCalendar-stamp-09.jpg')),
  ];
  const ready = art.every((image) => image !== null);
  const mark = useMemo(() => makePostmark(MEMORIES[actorMemory].day, 5, 2026), [actorMemory]);

  const ctl = useSharedValue<Ctl>({
    mode: IDLE, memory: AUTO_MEMORY, t0: 0, tau0: 0, stopAt: 0, prevTau: 0, lastTouch: -100,
    cornerX: 0, cornerY: 0, dx: 0, dy: 0, moved: false, lift: 1, release: detachCurl(AUTO_MEMORY),
    quietShow: false, restSent: false, rate: 1,
  });
  const pose = useSharedValue<Pose>(restPose(AUTO_MEMORY));
  const autoUI = useSharedValue(autoPlay);
  const quietUI = useSharedValue(false);
  const mayUI = useSharedValue(true);
  const emptyAt = useSharedValue(0);
  /** The memory the actor layer has rendered: a cell stamp hides only once its actor can show. */
  const shownUI = useSharedValue(AUTO_MEMORY);
  useEffect(() => { shownUI.set(actorMemory); }, [actorMemory, shownUI]);

  useEffect(() => {
    let mounted = true;
    AccessibilityInfo.isReduceMotionEnabled().then((v) => { if (mounted) setReduceMotion(v); }).catch(() => {});
    AccessibilityInfo.isScreenReaderEnabled().then((v) => { if (mounted) setScreenReader(v); }).catch(() => {});
    const sub = AccessibilityInfo.addEventListener('reduceMotionChanged', setReduceMotion);
    const reader = AccessibilityInfo.addEventListener('screenReaderChanged', setScreenReader);
    return () => { mounted = false; sub.remove(); reader.remove(); };
  }, []);

  useEffect(() => {
    // The story loop is a demo: it stops for a screen reader as for Reduce Motion (taps still play a stamp).
    autoUI.set(autoPlay && !reduceMotion && !screenReader);
    quietUI.set(reduceMotion);
    mayUI.set(may);
    scheduleOnUI(() => {
      'worklet';
      const c = ctl.get();
      ctl.set({ ...c, mode: IDLE, memory: AUTO_MEMORY, lastTouch: -100, quietShow: false, restSent: false });
    });
  }, [autoPlay, reduceMotion, screenReader, may, autoUI, quietUI, mayUI, ctl]);

  useEffect(() => {
    const sub = AppState.addEventListener('change', (next) => {
      if (next !== 'active') return;
      scheduleOnUI(() => {
        'worklet';
        const c = ctl.get();
        if (c.mode === AUTO) ctl.set({ ...c, t0: clock() });
      });
    });
    return () => sub.remove();
  }, [ctl]);

  const setActor = useCallback((memory: number) => setActorMemory(memory), []);

  // ------------------------------------------------------------------ the frame
  useFrameCallback(() => {
    'worklet';
    const now = clock();
    const c = ctl.get();
    let next: Pose | null = null;
    if (c.mode === IDLE) {
      if (autoUI.get() && mayUI.get() && now - c.lastTouch > TOUCH.resumeAfter) {
        ctl.set({ ...c, mode: AUTO, memory: AUTO_MEMORY, t0: now, restSent: false });
        if (c.memory !== AUTO_MEMORY) scheduleOnRN(setActor, AUTO_MEMORY);
        return;
      }
      if (c.restSent) return;
      ctl.set({ ...c, restSent: true });
      next = restPose(c.memory);
    } else if (c.mode === AUTO) {
      const tau = (now - c.t0) % LOOP;
      next = storyPose(c.memory, tau, detachCurl(c.memory));
    } else if (c.mode === FINGER) {
      const m = MEMORIES[c.memory];
      next = fingerPose(c.memory, now - c.t0, c.cornerX, c.cornerY, c.cornerX + c.dx, c.cornerY + c.dy);
      if (!c.moved) next.curl = { nx: 1, ny: 0, a: 1e4, r: 0 };
      void m;
    } else if (c.mode === SNAP) {
      const k = settle(now - c.t0);
      next = fingerPose(c.memory, 1, c.cornerX, c.cornerY, c.cornerX + c.dx * k, c.cornerY + c.dy * k);
      next.lift = mix(1, c.lift, k);
      next.cy = cellCenter(MEMORIES[c.memory].day, MAY_2026).y - PEEL.rise * ((next.lift - 1) / (PEEL.lift - 1));
      if (now - c.t0 > TOUCH.snapFor) {
        ctl.set({ ...c, mode: IDLE, restSent: false });
        next = restPose(c.memory);
      }
    } else if (c.mode === STORY) {
      let tau = c.tau0 + (now - c.t0) * c.rate;
      if (tau >= c.stopAt) tau = c.stopAt;
      if (c.prevTau < T.land && tau >= T.land) scheduleOnRN(landHaptic);
      if (c.prevTau < T.rest - 0.12 && tau >= T.rest - 0.12) scheduleOnRN(lightHaptic);
      next = storyPose(c.memory, tau, c.release);
      if (tau >= c.stopAt && c.stopAt >= T.rest) {
        ctl.set({ ...c, mode: IDLE, lastTouch: now, restSent: false, prevTau: tau });
      } else if (tau >= c.stopAt && autoUI.get() && now - c.lastTouch > TOUCH.resumeAfter) {
        // Held after a touch: two seconds later the stamp goes home and the loop restarts.
        ctl.set({ ...c, tau0: T.holdEnd, t0: now, stopAt: T.rest, prevTau: tau, rate: 1 });
      } else {
        ctl.set({ ...c, prevTau: tau });
      }
    } else if (c.mode === QUIET) {
      const v = clamp01((now - c.t0) / TOUCH.fade);
      next = quietPose(c.memory, c.quietShow ? v : 1 - v);
      if (!c.quietShow && v >= 1) ctl.set({ ...c, mode: IDLE, restSent: false });
    }
    if (next) pose.set(next);
  });

  // ------------------------------------------------------------------ touch
  const startStory = useCallback((c: Ctl, memory: number, tau0: number, stopAt: number, now: number, release: Curl) => {
    'worklet';
    ctl.set({ ...c, mode: STORY, memory, tau0, stopAt, t0: now, prevTau: tau0, lastTouch: now, release, restSent: false, rate: 1 });
    if (memory !== c.memory) scheduleOnRN(setActor, memory);
  }, [ctl, setActor]);

  const tapMemory = useCallback((memory: number) => {
    'worklet';
    const now = clock();
    const c = ctl.get();
    if (quietUI.get()) {
      const showing = c.mode === QUIET && c.quietShow && c.memory === memory;
      ctl.set({ ...c, mode: QUIET, memory, t0: now, quietShow: !showing, lastTouch: now, restSent: false });
      if (memory !== c.memory) scheduleOnRN(setActor, memory);
      scheduleOnRN(selectionHaptic);
      return;
    }
    startStory(c, memory, T.peelStart, T.holdEnd, now, detachCurl(memory));
    scheduleOnRN(selectionHaptic);
  }, [ctl, quietUI, setActor, startStory]);

  const sendHome = useCallback(() => {
    'worklet';
    const now = clock();
    const c = ctl.get();
    if (c.mode === QUIET && c.quietShow) {
      ctl.set({ ...c, t0: now, quietShow: false, lastTouch: now });
      return;
    }
    if (c.mode === STORY && c.stopAt === T.holdEnd) {
      const tau = Math.min(c.stopAt, c.tau0 + (now - c.t0) * c.rate);
      if (tau >= T.land) {
        ctl.set({ ...c, tau0: Math.max(tau, T.holdEnd), t0: now, stopAt: T.rest, lastTouch: now, rate: TOUCH.returnRate });
        scheduleOnRN(lightHaptic);
      }
    }
  }, [ctl]);

  /** Is the controller showing the pin (nothing moving)? Touch on a running loop takes it over. */
  const takeOver = useCallback((now: number): boolean => {
    'worklet';
    const c = ctl.get();
    if (c.mode === AUTO) {
      const tau = (now - c.t0) % LOOP;
      if (tau < T.peelStart - 0.05 || tau >= T.rest) {
        ctl.set({ ...c, mode: IDLE, lastTouch: now, restSent: false });
        return true;
      }
      // Mid-story: keep playing to the hold, then wait for the user.
      ctl.set({ ...c, mode: STORY, tau0: tau, t0: now, stopAt: tau < T.holdEnd ? T.holdEnd : T.rest, prevTau: tau, lastTouch: now, release: detachCurl(c.memory), rate: 1 });
      return false;
    }
    if (c.mode === IDLE) {
      ctl.set({ ...c, lastTouch: now });
      return true;
    }
    if (c.mode === QUIET) return true;
    if (c.mode === SNAP) {
      ctl.set({ ...c, mode: IDLE, lastTouch: now, restSent: false });
      return true;
    }
    ctl.set({ ...c, lastTouch: now });
    return false;
  }, [ctl]);

  const gesture = useMemo(() => Gesture.Pan()
    .minDistance(0)
    .onBegin((event) => {
      const now = clock();
      const rx = event.x / s;
      const ry = (event.y - oy) / s;
      const free = takeOver(now);
      const c = ctl.get();
      // While a stamp is out, any tap sends it home once it has landed.
      if (!free) {
        const landed = c.mode === STORY && c.stopAt === T.holdEnd && c.tau0 + (now - c.t0) * c.rate >= T.land;
        ctl.set({ ...c, moved: false, dx: 0, dy: 0, cornerX: landed ? -1 : 0, cornerY: 0 });
        return;
      }
      const m = mayUI.get() ? { year: 2026, month: 5 } : null;
      if (c.mode === QUIET) {
        if (c.quietShow) {
          ctl.set({ ...c, cornerX: -1, moved: false });
          return;
        }
      }
      const day = m ? dayAt(rx, ry, m) : 0;
      const memory = m && day > 0 ? memoryIndex(day, m) : -1;
      if (memory >= 0 && !quietUI.get()) {
        const mem = MEMORIES[memory];
        ctl.set({ ...ctl.get(), mode: FINGER, memory, t0: now, moved: false, dx: 0, dy: 0, cornerX: mem.w / 2, cornerY: mem.h / 2, lastTouch: now, restSent: false });
        if (memory !== c.memory) scheduleOnRN(setActor, memory);
        scheduleOnRN(selectionHaptic);
      } else {
        ctl.set({ ...ctl.get(), cornerX: memory >= 0 ? -2 - memory : 0, cornerY: day, moved: false });
      }
    })
    .onUpdate((event) => {
      const c = ctl.get();
      if (c.mode !== FINGER) {
        if (Math.hypot(event.translationX, event.translationY) > TOUCH.slop * s) ctl.set({ ...c, moved: true });
        return;
      }
      const dxRef = event.translationX / s;
      const dyRef = event.translationY / s;
      let { cornerX, cornerY, moved } = c;
      if (!moved && Math.hypot(dxRef, dyRef) > TOUCH.slop) {
        const mem = MEMORIES[c.memory];
        moved = true;
        // The corner opposite the pull is the one that lifts.
        cornerX = dxRef < 0 ? mem.w / 2 : -mem.w / 2;
        cornerY = dyRef < 0 ? mem.h / 2 : -mem.h / 2;
      }
      const lift = pose.get().lift;
      ctl.set({ ...c, moved, cornerX, cornerY, dx: dxRef / lift, dy: dyRef / lift, lastTouch: clock() });
    })
    .onFinalize((event) => {
      // Release (or cancel). A tap never activates a pan, so everything is decided here.
      const now = clock();
      const c = ctl.get();
      if (c.mode === FINGER) {
        if (!c.moved) {
          // A tap: play the peel from where the press already lifted it.
          const lifted = clamp01((now - c.t0) / TOUCH.liftIn);
          startStory(c, c.memory, T.peelStart + 0.25 * lifted * (T.detach - T.peelStart), T.holdEnd, now, detachCurl(c.memory));
          return;
        }
        const share = peelShare(c.memory, c.dx, c.dy);
        if (share > TOUCH.commitShare || event.velocityY / s < TOUCH.commitVelocity) {
          startStory(c, c.memory, T.detach, T.holdEnd, now, pose.get().curl);
          scheduleOnRN(lightHaptic);
        } else {
          ctl.set({ ...c, mode: SNAP, t0: now, lift: pose.get().lift, lastTouch: now });
        }
        return;
      }
      if (c.moved) return;
      if (c.cornerX === -1) { sendHome(); return; }
      if (c.cornerX <= -2) { tapMemory(-2 - c.cornerX); return; }
      if (c.cornerY > 0) {
        emptyAt.set(withSequence(withTiming(1, { duration: 120 }), withDelay(1200, withTiming(0, { duration: 300 }))));
        scheduleOnRN(setEmptyDay, c.cornerY);
        scheduleOnRN(selectionHaptic);
      }
    }), [ctl, emptyAt, mayUI, oy, pose, quietUI, s, sendHome, setActor, startStory, takeOver, tapMemory]);

  // ------------------------------------------------------------------ month menu and close
  const chooseMonth = useCallback(() => {
    setMenuOpen(true);
    selectionHaptic();
  }, []);
  const closeMenu = useCallback(() => setMenuOpen(false), []);
  const goMonth = useCallback((next: Month) => {
    setMenuOpen(false);
    setMonth(next);
    setEmptyDay(0);
    selectionHaptic();
  }, []);
  const close = useCallback(() => scheduleOnUI(sendHome), [sendHome]);

  const a11ySelect = useCallback((day: number) => {
    const memory = memoryIndex(day, month);
    if (memory >= 0) scheduleOnUI(tapMemory, memory);
    else setEmptyDay(day);
  }, [month, tapMemory]);

  return (
    <GestureHandlerRootView style={styles.root}>
      <StatusBar style="light" />
      <BlurTargetView ref={blurTarget} collapsable={false} style={StyleSheet.absoluteFill}>
        <Backdrop width={width} height={height} s={s} oy={oy} month={month} />
        <GestureDetector gesture={gesture}>
          <View style={StyleSheet.absoluteFill}>
            <CalendarText month={month} s={s} oy={oy} pose={pose} actorMemory={actorMemory} emptyDay={emptyDay} emptyAt={emptyAt} onSelect={a11ySelect} />
          </View>
        </GestureDetector>
        <Title s={s} oy={oy} month={month} onMonth={chooseMonth} />
        {ready && STAMP_EFFECT && (
          <Canvas style={StyleSheet.absoluteFill} pointerEvents="none">
            <Group transform={[{ translateY: oy }, { scale: s }]}>
              <Pile art={art as SkImage[]} pose={pose} s={s} />
              {may && MEMORIES.map((mem, index) => (
                <CellStamp key={'cell-' + mem.day} index={index} image={art[mem.art] as SkImage} pose={pose} shown={shownUI} s={s} />
              ))}
              {may && mark && <Actor image={art[MEMORIES[actorMemory].art] as SkImage} mark={mark} memory={actorMemory} pose={pose} s={s} />}
            </Group>
          </Canvas>
        )}
      </BlurTargetView>
      <Header s={s} oy={oy} onClose={close} onMonth={chooseMonth} blurTarget={blurTarget} />
      <MonthMenu s={s} oy={oy} month={month} open={menuOpen} reduceMotion={reduceMotion} blurTarget={blurTarget} onClose={closeMenu} onPick={goMonth} />
    </GestureHandlerRootView>
  );
}

// ---------------------------------------------------------------------------------------------- drawing

function stampUniforms(w: number, h: number, aa: number, dim: number, holes: { across: number; down: number }, shadow: number) {
  'worklet';
  return {
    box: [w, h, aa, dim],
    crop: cropFor(w, h),
    holes: [holes.across, holes.down, PERFORATION.radius, shadow],
    curl: [1, 0, 1e4, 0],
    sheen: [0, 0, 0, 0],
    ink: [0, 0, 1, 0],
    inkfx: [0, 1, POSTMARK.ringX, POSTMARK.ringY],
  };
}

function quadMatrix(q: StampQuad): number[] {
  'worklet';
  return stampMatrix(q.x + q.w / 2, q.y + q.h / 2 + q.dy / 2, q.dy / q.w, 0, 0, 0, 1, 1, 1e6);
}

function PileStamp({ quad, image, blur, pose, s }: { quad: StampQuad; image: SkImage; blur: number; pose: SharedValue<Pose>; s: number }) {
  const aa = 0.7 / (3 * s);
  const holes = holesOfQuad(quad);
  const uniforms = useDerivedValue(() => stampUniforms(quad.w, quad.h, aa, pose.get().pileDim, holes, 0));
  const matrix = useMemo(() => quadMatrix(quad), [quad]);
  const shape = (
    <Rect x={-quad.w / 2} y={-quad.h / 2} width={quad.w} height={quad.h}>
      <Shader source={STAMP_EFFECT!} uniforms={uniforms}>
        <ImageShader image={image} sampling={SAMPLING} />
        <ImageShader image={image} sampling={SAMPLING} />
      </Shader>
      {blur > 0 && <Blur blur={blur} mode="decal" />}
    </Rect>
  );
  return <Group matrix={matrix}>{shape}</Group>;
}

function Pile({ art, pose, s }: { art: SkImage[]; pose: SharedValue<Pose>; s: number }) {
  // The landing presses the collage in a little, around the landing point.
  const transform = useDerivedValue(() => {
    const p = pose.get();
    const q = landingQuad(p.memory);
    const lx = q.x + q.w / 2;
    const ly = q.y + q.h / 2;
    const k = 1 - p.pileNudge;
    return [{ translateX: lx }, { translateY: ly }, { scale: k }, { translateX: -lx }, { translateY: -ly }];
  });
  return (
    <Group clip={{ x: -60, y: -120, width: REF.width + 120, height: HERO_BOTTOM + 120 }} transform={transform}>
      {PILE.map((item, index) => {
        if (item.blur[1] <= 0) return <PileStamp key={'pile-' + index} quad={item.quad} image={art[item.art]} blur={0} pose={pose} s={s} />;
        // Depth of field: soft at the top, softer towards the bottom of the hero.
        const top = item.quad.y + item.quad.h * item.soft;
        return (
          <Group key={'pile-' + index}>
            <PileStamp quad={item.quad} image={art[item.art]} blur={item.blur[0]} pose={pose} s={s} />
            <Mask mask={(
              <Rect x={-60} y={top} width={REF.width + 120} height={HERO_BOTTOM - top + 60}>
                <LinearGradient start={vec(0, top)} end={vec(0, top + 50)} colors={['transparent', 'black']} />
              </Rect>
            )}
            >
              <PileStamp quad={item.quad} image={art[item.art]} blur={item.blur[1]} pose={pose} s={s} />
            </Mask>
          </Group>
        );
      })}
    </Group>
  );
}

function CellStamp({ index, image, pose, shown, s }: { index: number; image: SkImage; pose: SharedValue<Pose>; shown: SharedValue<number>; s: number }) {
  const mem = MEMORIES[index];
  const c = cellCenter(mem.day, MAY_2026);
  const holes = holesOf(index);
  const aa = 0.7 / (3 * s);
  const uniforms = useMemo(() => stampUniforms(mem.w, mem.h, aa, 1, holes, 0), [mem, aa, holes]);
  const matrix = useMemo(() => stampMatrix(c.x, c.y, 0, 0, 0, 0, 1, 1, 1e6), [c.x, c.y]);
  const opacity = useDerivedValue(() => {
    const p = pose.get();
    return p.active === 1 && p.memory === index && shown.get() === index ? 0 : 1;
  });
  return (
    <Group matrix={matrix} opacity={opacity}>
      <Rect x={-mem.w / 2} y={-mem.h / 2} width={mem.w} height={mem.h}>
        <Shader source={STAMP_EFFECT!} uniforms={uniforms}>
          <ImageShader image={image} sampling={SAMPLING} />
          <ImageShader image={image} sampling={SAMPLING} />
        </Shader>
      </Rect>
    </Group>
  );
}

function Actor({ image, mark, memory, pose, s }: { image: SkImage; mark: SkImage; memory: number; pose: SharedValue<Pose>; s: number }) {
  const holes = holesOf(memory);
  const matrix = useDerivedValue(() => {
    const p = pose.get();
    return stampMatrix(p.cx, p.cy, p.skew, p.rotX, p.rotY, p.rotZ, p.lift * p.sx, p.lift * p.sy, 520);
  });
  const shadowMatrix = useDerivedValue(() => {
    const p = pose.get();
    const lift = p.height;
    return stampMatrix(p.cx + 5 + 9 * lift, p.cy + 8 + 16 * lift, p.skew, p.rotX * 0.6, p.rotY * 0.6, p.rotZ, p.lift * 0.97, p.lift * 0.97, 520);
  });
  const shadowBlur = useDerivedValue(() => 2 + 12 * pose.get().height);
  const shadowOpacity = useDerivedValue(() => {
    const p = pose.get();
    return p.active === 1 ? p.opacity * (0.2 + 0.35 * p.height) : 0;
  });
  const shadowRect = useDerivedValue(() => {
    const p = pose.get();
    return { x: -p.w / 2, y: -p.h / 2, width: p.w, height: p.h };
  });
  const rect = useDerivedValue(() => {
    const p = pose.get();
    const pad = STAMP_PAD * Math.max(p.w, p.h);
    return { x: -p.w / 2 - pad, y: -p.h / 2 - pad, width: p.w + 2 * pad, height: p.h + 2 * pad };
  });
  const opacity = useDerivedValue(() => {
    const p = pose.get();
    return p.active === 1 && p.memory === memory ? p.opacity : 0;
  });
  const uniforms = useDerivedValue(() => {
    const p = pose.get();
    const aa = 0.7 / (3 * s * Math.max(0.5, p.lift));
    const k = POSTMARK.outer / (INK_MARK.radius * p.w);
    return {
      box: [p.w, p.h, aa, 1],
      crop: cropFor(p.w, p.h),
      holes: [holes.across, holes.down, PERFORATION.radius, p.peelShadow],
      curl: [p.curl.nx, p.curl.ny, p.curl.a, p.curl.r],
      sheen: [p.sheenPos, p.sheenAmt, p.inkWet, p.inkOn],
      ink: [INK_MARK.x * p.w, INK_MARK.y * p.h, k, INK_MARK.rotation],
      inkfx: [p.inkSpread, p.inkThreshold, POSTMARK.ringX, POSTMARK.ringY],
    };
  });
  return (
    <Group opacity={opacity}>
      <Group matrix={shadowMatrix} opacity={shadowOpacity}>
        <Rect rect={shadowRect} color="#000000">
          <BlurMask blur={shadowBlur} style="normal" />
        </Rect>
      </Group>
      <Group matrix={matrix}>
        <Rect rect={rect}>
          <Shader source={STAMP_EFFECT!} uniforms={uniforms}>
            <ImageShader image={image} sampling={SAMPLING} />
            <ImageShader image={mark} sampling={SAMPLING} />
          </Shader>
        </Rect>
      </Group>
    </Group>
  );
}

/** Everything still: the hero's graph paper and the calendar's tiles and lines. Drawn once. */
function Backdrop({ width, height, s, oy, month }: { width: number; height: number; s: number; oy: number; month: Month }) {
  const first = firstWeekday(month);
  const count = daysIn(month);
  const paperRows = Math.floor((HERO_BOTTOM - space.paperPhaseY) / space.paperPitch) + 1;
  const paperCols = Math.ceil(REF.width / space.paperPitch) + 1;
  const bottom = grid.top + grid.rows * grid.rowHeight;
  return (
    <Canvas style={StyleSheet.absoluteFill} pointerEvents="none">
      <Rect x={0} y={0} width={width} height={height} color={colors.page} />
      <Group transform={[{ translateY: oy }, { scale: s }]}>
        <Rect x={0} y={-oy / s} width={REF.width} height={HERO_BOTTOM + oy / s}>
          <LinearGradient start={vec(0, 0)} end={vec(0, HERO_BOTTOM)} colors={[colors.heroTop, colors.heroBottom]} />
        </Rect>
        {Array.from({ length: paperCols }, (_, i) => (
          <Rect key={'pv' + i} x={space.paperPhaseX - space.paperPitch + i * space.paperPitch - 0.5} y={-oy / s} width={1} height={HERO_BOTTOM + oy / s} color={colors.heroLine} />
        ))}
        {Array.from({ length: paperRows }, (_, i) => (
          <Rect key={'ph' + i} x={0} y={space.paperPhaseY + i * space.paperPitch - 0.5} width={REF.width} height={1} color={colors.heroLine} />
        ))}
        {Array.from({ length: grid.rows * 7 }, (_, i) => {
          const day = i - first + 1;
          if (day >= 1 && day <= count) return null;
          return <Rect key={'blank' + i} x={(i % 7) * grid.columnWidth} y={grid.top + Math.floor(i / 7) * grid.rowHeight} width={grid.columnWidth} height={grid.rowHeight} color={colors.blankCell} />;
        })}
        {Array.from({ length: 8 }, (_, i) => (
          <Rect key={'gv' + i} x={i * grid.columnWidth - sizes.gridLine / 2} y={grid.weekdayTop} width={sizes.gridLine} height={bottom - grid.weekdayTop} color={colors.gridLine} />
        ))}
        {Array.from({ length: grid.rows + 2 }, (_, i) => {
          const y = i === 0 ? grid.weekdayTop : grid.top + (i - 1) * grid.rowHeight;
          return <Rect key={'gh' + i} x={0} y={y - sizes.gridLine / 2} width={REF.width} height={sizes.gridLine} color={colors.gridLine} />;
        })}
      </Group>
    </Canvas>
  );
}

function baselineTop(size: number, baseline: number): number {
  const lineHeight = size * SF.lineHeight;
  return baseline - ((lineHeight - size * 1.183) / 2 + size * SF.ascent);
}

function MemoryNumber({ index, s, oy, pose }: { index: number; s: number; oy: number; pose: SharedValue<Pose> }) {
  const mem = MEMORIES[index];
  const c = cellCenter(mem.day, MAY_2026);
  const style = useAnimatedStyle(() => {
    const p = pose.get();
    return { opacity: p.memory === index ? p.reveal : 0 };
  });
  return (
    <Animated.View pointerEvents="none" style={[styles.cell, { left: (c.x - grid.columnWidth / 2) * s, top: oy + (c.y - grid.rowHeight / 2) * s, width: grid.columnWidth * s, height: grid.rowHeight * s }, style]}>
      <Text allowFontScaling={false} style={[styles.day, { fontSize: type.day.size * s }]}>{mem.day}</Text>
      <View style={[styles.ring, {
        left: space.selectionInset * s, right: space.selectionInset * s, top: space.selectionInset * s, bottom: space.selectionInset * s,
        borderRadius: radii.selection * s, borderWidth: sizes.selectionLine,
      }]}
      />
    </Animated.View>
  );
}

function CalendarText({ month, s, oy, pose, actorMemory, emptyDay, emptyAt, onSelect }: {
  month: Month; s: number; oy: number; pose: SharedValue<Pose>; actorMemory: number; emptyDay: number;
  emptyAt: SharedValue<number>; onSelect: (day: number) => void;
}) {
  const count = daysIn(month);
  const may = isMay2026(month);
  const emptyStyle = useAnimatedStyle(() => {
    return { opacity: emptyAt.get() };
  });
  const emptyCell = emptyDay > 0 && emptyDay <= count ? cellOf(emptyDay, month) : null;
  void actorMemory;
  return (
    <View style={StyleSheet.absoluteFill}>
      <View style={[styles.weekdays, { top: oy + grid.weekdayTop * s, height: (grid.top - grid.weekdayTop) * s }]}>
        {WEEKDAYS.map((w, i) => (
          <Text key={'wd' + i} allowFontScaling={false} style={[styles.weekday, { width: grid.columnWidth * s, fontSize: type.weekday.size * s }]}>{w}</Text>
        ))}
      </View>
      {Array.from({ length: count }, (_, i) => {
        const day = i + 1;
        const cell = cellOf(day, month);
        const memory = may ? memoryIndex(day, month) : -1;
        const label = MONTH_NAMES[month.month - 1] + ' ' + day + ', ' + month.year + (memory >= 0 ? ', memory' : '');
        return (
          <View
            key={'d' + day}
            accessible
            accessibilityRole="button"
            accessibilityLabel={label}
            onAccessibilityTap={() => onSelect(day)}
            style={[styles.cell, { left: cell.column * grid.columnWidth * s, top: oy + (grid.top + cell.row * grid.rowHeight) * s, width: grid.columnWidth * s, height: grid.rowHeight * s }]}
          >
            {memory < 0 && <Text allowFontScaling={false} style={[styles.day, { fontSize: type.day.size * s }]}>{day}</Text>}
          </View>
        );
      })}
      {may && MEMORIES.map((mem, index) => <MemoryNumber key={'mn' + mem.day} index={index} s={s} oy={oy} pose={pose} />)}
      {emptyCell && (
        <Animated.View pointerEvents="none" style={[styles.ring, emptyStyle, {
          left: (emptyCell.column * grid.columnWidth + space.selectionInset) * s,
          top: oy + (grid.top + emptyCell.row * grid.rowHeight + space.selectionInset) * s,
          width: (grid.columnWidth - 2 * space.selectionInset) * s,
          height: (grid.rowHeight - 2 * space.selectionInset) * s,
          borderRadius: radii.selection * s, borderWidth: sizes.selectionLine,
        }]}
        />
      )}
    </View>
  );
}

function Header({ s, oy, onClose, onMonth, blurTarget }: { s: number; oy: number; onClose: () => void; onMonth: () => void; blurTarget: RefObject<View | null> }) {
  const size = sizes.header * s;
  const top = oy + (space.headerCenterY - sizes.header / 2) * s;
  const g = sizes.closeGlyph / 2;
  const circle = { top, width: size, height: size, borderRadius: radii.header * s };
  return (
    <View pointerEvents="box-none" style={StyleSheet.absoluteFill}>
      <Pressable
        accessibilityRole="button"
        accessibilityLabel="Close"
        onPress={onClose}
        hitSlop={4}
        style={({ pressed }) => [styles.circle, circle, { left: (space.headerCenterInset - sizes.header / 2) * s, transform: [{ scale: pressed ? 0.97 : 1 }] }]}
      >
        <SmokedGlass blurTarget={blurTarget} />
        <Svg width={size} height={size} viewBox={`0 0 ${sizes.header} ${sizes.header}`}>
          <Line x1={22 - g} y1={22 - g} x2={22 + g} y2={22 + g} stroke={colors.headerIcon} strokeWidth={sizes.closeStroke} strokeLinecap="round" />
          <Line x1={22 + g} y1={22 - g} x2={22 - g} y2={22 + g} stroke={colors.headerIcon} strokeWidth={sizes.closeStroke} strokeLinecap="round" />
        </Svg>
      </Pressable>
      <Pressable
        accessibilityRole="button"
        accessibilityLabel="Choose month"
        onPress={onMonth}
        hitSlop={4}
        style={({ pressed }) => [styles.circle, circle, { left: (REF.width - space.headerCenterInset - sizes.header / 2) * s, transform: [{ scale: pressed ? 0.97 : 1 }] }]}
      >
        <SmokedGlass blurTarget={blurTarget} />
        <Svg width={size} height={size} viewBox={`0 0 ${sizes.header} ${sizes.header}`}>
          {[-1, 0, 1].map((i) => <Circle key={'dot' + i} cx={22 + i * sizes.dotGap} cy={21.5} r={sizes.dot / 2} fill={colors.headerIcon} />)}
        </Svg>
      </Pressable>
    </View>
  );
}

/** The pin's smoked glass: a dark blur of what is behind, under a dark tint. Fills its (clipped) parent. */
function SmokedGlass({ blurTarget, fill = colors.headerFill }: { blurTarget: RefObject<View | null>; fill?: string }) {
  return (
    <>
      <BlurView pointerEvents="none" blurTarget={blurTarget} blurMethod="dimezisBlurViewSdk31Plus" intensity={glass.intensity} tint={glass.tint} style={StyleSheet.absoluteFill} />
      <View pointerEvents="none" style={[StyleSheet.absoluteFill, { backgroundColor: fill }]} />
    </>
  );
}

/**
 * The month menu (••• or the title): a small smoked-glass card under the ••• circle with the neighbouring months
 * and, away from May 2026, a way back. A tap outside or "Cancel" closes it. Reduce Motion: a fade only.
 */
function MonthMenu({ s, oy, month, open, reduceMotion, blurTarget, onClose, onPick }: {
  s: number; oy: number; month: Month; open: boolean; reduceMotion: boolean; blurTarget: RefObject<View | null>;
  onClose: () => void; onPick: (next: Month) => void;
}) {
  const shown = useSharedValue(0);
  useEffect(() => {
    shown.set(withTiming(open ? 1 : 0, { duration: open ? menu.inMs : menu.outMs, easing: Easing.bezier(0.23, 1, 0.32, 1) }));
  }, [open, shown]);
  const style = useAnimatedStyle(() => {
    const p = shown.get();
    const k = reduceMotion ? 1 : menu.scaleFrom + (1 - menu.scaleFrom) * p;
    return { opacity: p, transform: [{ scale: k }] };
  });
  const name = (m: Month) => MONTH_NAMES[m.month - 1] + ' ' + m.year;
  const prev = addMonths(month, -1);
  const next = addMonths(month, 1);
  const items: { label: string; a11y: string; onPress: () => void }[] = [
    { label: 'Previous month', a11y: 'Previous month, ' + name(prev), onPress: () => onPick(prev) },
    { label: 'Next month', a11y: 'Next month, ' + name(next), onPress: () => onPick(next) },
  ];
  if (!isMay2026(month)) items.push({ label: 'Back to May 2026', a11y: 'Back to May 2026', onPress: () => onPick(MAY_2026) });
  items.push({ label: 'Cancel', a11y: 'Cancel', onPress: onClose });
  const top = oy + (space.headerCenterY + sizes.header / 2 + menu.gap) * s;
  const right = (space.headerCenterInset - sizes.header / 2) * s;
  return (
    <View pointerEvents={open ? 'auto' : 'none'} style={StyleSheet.absoluteFill} accessibilityElementsHidden={!open} importantForAccessibility={open ? 'auto' : 'no-hide-descendants'}>
      <Pressable accessibilityLabel="Close menu" style={StyleSheet.absoluteFill} onPress={onClose} />
      <Animated.View
        accessibilityViewIsModal={open}
        accessibilityRole="menu"
        style={[styles.menu, { top, right, width: menu.width * s, borderRadius: menu.radius * s, transformOrigin: 'top right' }, style]}
      >
        <SmokedGlass blurTarget={blurTarget} fill={menu.fill} />
        {items.map((item, i) => (
          <Pressable
            key={item.label}
            accessibilityRole="menuitem"
            accessibilityLabel={item.a11y}
            onPress={item.onPress}
            style={({ pressed }) => [
              styles.menuRow,
              { height: menu.row * s, paddingHorizontal: menu.padX * s, borderTopWidth: i === 0 ? 0 : StyleSheet.hairlineWidth, opacity: pressed ? 0.6 : 1 },
            ]}
          >
            <Text allowFontScaling={false} style={[styles.menuText, { fontSize: menu.textSize * s, fontWeight: item.label === 'Cancel' ? '400' : '600' }]}>{item.label}</Text>
          </Pressable>
        ))}
      </Animated.View>
    </View>
  );
}

/** Year and month, right aligned on the pin's baselines. Under the stamps: a stamp may fly over it. */
function Title({ s, oy, month, onMonth }: { s: number; oy: number; month: Month; onMonth: () => void }) {
  const yearTop = baselineTop(type.year.size, type.year.baseline);
  const monthTop = baselineTop(type.month.size, type.month.baseline);
  const title = MONTH_NAMES[month.month - 1];
  return (
    <Pressable
        accessibilityRole="button"
        accessibilityLabel={title + ' ' + month.year + '. Choose month.'}
        onPress={onMonth}
        style={[styles.title, { right: space.titleInset * s, top: oy + yearTop * s }]}
      >
        <Text allowFontScaling={false} style={[styles.year, { fontSize: type.year.size * s, lineHeight: type.year.size * SF.lineHeight * s }]}>{String(month.year)}</Text>
        <Text allowFontScaling={false} style={[styles.month, { fontSize: type.month.size * s, lineHeight: type.month.size * SF.lineHeight * s, marginTop: (monthTop - yearTop - type.year.size * SF.lineHeight) * s }]}>{title}</Text>
      </Pressable>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: colors.page },
  weekdays: { position: 'absolute', left: 0, right: 0, flexDirection: 'row', alignItems: 'center' },
  weekday: { color: colors.weekday, fontWeight: type.weekday.weight, textAlign: 'center' },
  cell: { position: 'absolute', alignItems: 'center', justifyContent: 'center' },
  day: { color: colors.day, fontWeight: type.day.weight, fontVariant: ['tabular-nums'] },
  ring: { position: 'absolute', borderColor: colors.selection },
  circle: { position: 'absolute', overflow: 'hidden', alignItems: 'center', justifyContent: 'center' },
  menu: { position: 'absolute', overflow: 'hidden' },
  menuRow: { justifyContent: 'center', borderColor: menu.divider },
  menuText: { color: menu.text },
  title: { position: 'absolute', alignItems: 'flex-end' },
  year: { color: colors.year, fontWeight: type.year.weight },
  month: { color: colors.month, fontWeight: type.month.weight },
});
