// Reeded Glass — the reusable part. A photo behind 22 flutes of thick reeded glass that a finger (or a button, or a
// `progress` value you drive) combs flat: a front travels across the flutes, each flute lies down on its own spring,
// and `useReededReveal().depthAt(x)` tells your own views exactly where the glass has cleared, so text can resolve as the
// front passes it. One Skia canvas, one SkSL shader (rgr-shaders.ts GLASS); everything it draws is a function of the
// timeline (autoPlay) or of the live physics (rgr-motion.ts).
//
//   <ReededGlass source={photo} revealed={unlocked} onRevealedChange={setUnlocked} peek />
//
// Dropped from the spec, on purpose: `ribs` (the shader's arrays are sized for the pin's 22 flutes) and `children`
// (snapshotting live views needs a device to judge; pass an image).
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
  AccessibilityInfo,
  type ImageSourcePropType,
  type LayoutChangeEvent,
  StyleSheet,
  useWindowDimensions,
  View,
  type ViewStyle,
} from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import {
  type SharedValue,
  useAnimatedReaction,
  useDerivedValue,
  useFrameCallback,
  useReducedMotion,
  useSharedValue,
} from 'react-native-reanimated';
import { Canvas, FilterMode, ImageShader, MipmapMode, Rect, type SkImage, Shader, Skia, TileMode, useImage } from '@shopify/react-native-skia';
import { scheduleOnRN } from 'react-native-worklets';

import {
  edge as cueEdge,
  masked as cueMasked,
  onset as cueOnset,
  revealed as cueRevealed,
  ribTick as cueTick,
  setHapticsEnabled,
} from './rgr-haptics';
import {
  clearedLive,
  decideDirection,
  depthAt as depthAtEdges,
  dragLive,
  finishTouch,
  GESTURE,
  type Live,
  liveAtRest,
  liveFromTimeline,
  liveLook,
  type Look,
  LOOP,
  makeGeo,
  restLive,
  shouldResume,
  startSweep,
  stepLive,
  T,
  timelineLook,
} from './rgr-motion';
import { GLASS_SKSL } from './rgr-shaders';
import { comp, glassLayout, REF, ribEdges } from './rgr-theme';
import { glassUniforms } from './rgr-uniforms';

export { LOOP };

const glassFx = Skia.RuntimeEffect.Make(GLASS_SKSL);
const SAMPLING = { filter: FilterMode.Linear, mipmap: MipmapMode.None };

type Rect4 = { x: number; y: number; w: number; h: number };

export type ReededGlassProps = {
  /** The photo: a decoded SkImage (best: no second decode), a require()d asset or a { uri }. */
  source: SkImage | ImageSourcePropType;
  /** Controlled: true = the glass is clear. */
  revealed?: boolean;
  /** Uncontrolled initial state. */
  defaultRevealed?: boolean;
  /** Called once a reveal or a re-mask has completed (not for the silent autoPlay loop). */
  onRevealedChange?: (revealed: boolean) => void;
  /** The front, 0..1. Read it (every frame) or drive it (scroll, biometrics): writing a different value takes the front there. */
  progress?: SharedValue<number>;
  /** Lens strength, 0..1.2 (1 = the pin's glass). */
  depth?: number;
  /** Which edge the front starts from. 'auto': the first 12 pt of a drag from a resting end decide. */
  direction?: 'ltr' | 'rtl' | 'auto';
  /** A press held 180 ms opens a window that follows the finger. */
  peek?: boolean;
  /** A release whose projected front is past this progress commits to clear (0..1). */
  commitAt?: number;
  /** Hide again this long after clearing (ms; 0 = never). Not used while autoPlay runs: the loop re-masks. */
  autoMaskMs?: number;
  /** Glint strength 0..1. */
  glints?: number;
  /** Light haptics on touch, ticks per flute, edge, success and re-mask. Autoplay is always silent. */
  haptics?: boolean;
  /** Play the 8 s demo loop until a finger touches the glass. */
  autoPlay?: boolean;
  style?: ViewStyle;
  accessibilityLabel?: string;
  /** Image rect inside the box, pt (default: the photo covers the box). */
  imageRect?: { x: number; y: number; width: number; height: number };
  /** The lens' centre: the point the glass magnifies about, pt (default: a third of the way down the photo). */
  focus?: { x: number; y: number };
  /** Where the glass is drawn, pt (default: the whole box). Used to leave out what a panel covers. */
  drawAreas?: { x: number; y: number; w: number; h: number }[];
  /** Height of the touch area, pt (default: the whole box). */
  hitHeight?: number;
  /** Pass the object from useReededReveal() to read the glass from your own views. */
  reveal?: ReededReveal;
  /** The glass has drawn its first frames; show what waits for it. */
  onReady?: () => void;
};

type EngineApi = {
  toggle: () => void;
  set: (revealed: boolean) => void;
  hold: () => void;
  release: () => void;
};

export type ReededReveal = {
  /** Per-frame state on the UI thread: depths, front, glyph turn, light, run. */
  look: SharedValue<Look>;
  /** The front, 0..1. */
  progress: SharedValue<number>;
  /** The 23 flute edges (pt) of the glass this belongs to. */
  edges: SharedValue<number[]>;
  /** React mirrors (they change only when a reveal completes, or its target flips). */
  revealed: boolean;
  targetRevealed: boolean;
  ready: boolean;
  /** Worklet: depth of the glass at screen x, 1 (reeded) .. 0 (clear). Unmask your own text with it. */
  depthAt: (x: number) => number;
  /** Sweep to the other end, like the swap button. */
  toggle: () => void;
  set: (revealed: boolean) => void;
  /** A button of yours is pressed: freeze the loop under it. */
  hold: () => void;
  release: () => void;
  /** internals for <ReededGlass> */
  _bind: (api: EngineApi | null) => void;
  _set: { revealed: (v: boolean) => void; target: (v: boolean) => void; ready: (v: boolean) => void };
};

const BLANK_LOOK: Look = {
  depths: Array.from({ length: comp.glass.ribs }, () => 1),
  glyph: 0,
  press: 1,
  front: 0,
  mean: 1,
  lag: 0,
  run: -1,
  light: 0,
  breath: 0,
  peek: 0,
};

export function useReededReveal(): ReededReveal {
  const look = useSharedValue<Look>(BLANK_LOOK);
  const progress = useSharedValue(0);
  const edges = useSharedValue<number[]>(ribEdges(REF.w));
  const [revealed, setRevealed] = useState(false);
  const [targetRevealed, setTargetRevealed] = useState(false);
  const [ready, setReady] = useState(false);
  const api = useRef<EngineApi | null>(null);
  const depthAt = useCallback(
    (x: number) => {
      'worklet';
      return depthAtEdges(look.get().depths, x, edges.get());
    },
    [look, edges],
  );
  const bind = useCallback((a: EngineApi | null) => {
    api.current = a;
  }, []);
  const toggle = useCallback(() => api.current?.toggle(), []);
  const set = useCallback((r: boolean) => api.current?.set(r), []);
  const hold = useCallback(() => api.current?.hold(), []);
  const release = useCallback(() => api.current?.release(), []);
  return useMemo(
    () => ({
      look,
      progress,
      edges,
      revealed,
      targetRevealed,
      ready,
      depthAt,
      toggle,
      set,
      hold,
      release,
      _bind: bind,
      _set: { revealed: setRevealed, target: setTargetRevealed, ready: setReady },
    }),
    [look, progress, edges, revealed, targetRevealed, ready, depthAt, toggle, set, hold, release, bind],
  );
}

/** The system's Reduce Motion setting, live. */
export function useReduceMotionSetting() {
  const initial = useReducedMotion();
  const [reduce, setReduce] = useState(initial);
  useEffect(() => {
    let mounted = true;
    AccessibilityInfo.isReduceMotionEnabled().then((enabled) => {
      if (mounted) setReduce(enabled);
    });
    const subscription = AccessibilityInfo.addEventListener('reduceMotionChanged', setReduce);
    return () => {
      mounted = false;
      subscription.remove();
    };
  }, []);
  return reduce;
}

function isSkImage(x: unknown): x is SkImage {
  return !!x && typeof (x as SkImage).width === 'function' && typeof (x as SkImage).makeShaderOptions === 'function';
}

export function ReededGlass({
  source,
  revealed: revealedProp,
  defaultRevealed = false,
  onRevealedChange,
  progress: progressProp,
  depth = 1,
  direction = 'auto',
  peek = true,
  commitAt = 0.5,
  autoMaskMs = 0,
  glints = 1,
  haptics = true,
  autoPlay = false,
  style,
  accessibilityLabel = 'Photo behind reeded glass. Drag across it to clear the glass.',
  imageRect,
  focus,
  drawAreas,
  hitHeight,
  reveal: revealProp,
  onReady,
}: ReededGlassProps) {
  const own = useReededReveal();
  const reveal = revealProp ?? own;
  const sourceIsImage = isSkImage(source);
  const loaded = useImage(
    (sourceIsImage ? null : typeof source === 'object' && source !== null && 'uri' in source ? source.uri : source) as
      string | number | null,
  );
  const portrait = sourceIsImage ? source : loaded;

  const win = useWindowDimensions();
  const [box, setBox] = useState<{ w: number; h: number } | null>(null);
  const W = box?.w ?? win.width;
  const H = box?.h ?? win.height;
  const onLayout = (e: LayoutChangeEvent) => {
    const { width, height } = e.nativeEvent.layout;
    if (width > 0 && height > 0 && (!box || Math.abs(box.w - width) > 0.5 || Math.abs(box.h - height) > 0.5))
      setBox({ w: width, h: height });
  };
  const aspect = portrait ? portrait.width() / portrait.height() : 1206 / 2622;
  const ix = imageRect?.x;
  const iy = imageRect?.y;
  const iw = imageRect?.width;
  const ih = imageRect?.height;
  const fx0 = focus?.x;
  const fy0 = focus?.y;
  const G = useMemo(
    () =>
      glassLayout(
        W,
        H,
        ix === undefined ? undefined : { x: ix, y: iy ?? 0, width: iw ?? W, height: ih ?? H },
        fx0 === undefined ? undefined : { x: fx0, y: fy0 ?? 0 },
        aspect,
      ),
    [W, H, ix, iy, iw, ih, fx0, fy0, aspect],
  );
  const geo = useMemo(() => makeGeo(G), [G]);
  useEffect(() => {
    reveal.edges.set(G.edges);
  }, [G, reveal.edges]);

  // Cache only optical defocus. Refraction, cylindrical shading and glints remain live per flute.
  const portraitSoft = useMemo(() => {
    if (!portrait) return null;
    const width = portrait.width();
    const height = portrait.height();
    const surface = Skia.Surface.MakeOffscreen(width, height, { colorSpace: 'srgb' }) ?? Skia.Surface.Make(width, height);
    if (!surface) return null;
    const paint = Skia.Paint();
    const filter = Skia.ImageFilter.MakeBlur(
      (comp.glass.smear * G.S * width) / G.img.w,
      (comp.glass.smearY * G.S * height) / G.img.h,
      TileMode.Clamp,
    );
    paint.setImageFilter(filter);
    surface.getCanvas().drawImage(portrait, 0, 0, paint);
    surface.flush();
    // Skia 2.6: a snapshot is a texture of this offscreen context, which the canvas thread cannot sample; copy it to the CPU.
    const snap = surface.makeImageSnapshot();
    const image = snap.makeNonTextureImage() ?? snap;
    paint.dispose();
    filter.dispose();
    surface.dispose();
    return image;
  }, [portrait, G.S, G.img.w, G.img.h]);

  const reduce = useReduceMotionSetting();
  const [framesRunning, setFramesRunning] = useState(true);
  useEffect(() => {
    setHapticsEnabled(haptics);
  }, [haptics]);

  // ---- state on the UI thread ----
  const look = reveal.look;
  const progressOut = reveal.progress;
  const setRevealedRN = reveal._set.revealed;
  const setTargetRN = reveal._set.target;
  const setReadyRN = reveal._set.ready;
  const live = useSharedValue<Live>(restLive(geo));
  const playing = useSharedValue(autoPlay);
  const loopT = useSharedValue(0);
  const clock = useSharedValue(0);
  const touchedAt = useSharedValue(-1e9);
  const revealedAt = useSharedValue(-1e9);
  const cmd = useSharedValue(0);
  const cmdSeen = useSharedValue(0);
  const cmdTo = useSharedValue(0); // 1: clear, -1: mask, 0: none (a controlled `revealed` or reveal.set())
  const touch = useSharedValue({ startX: 0, frontStart: G.front0, dragged: false });
  const reducedSeen = useSharedValue(reduce);
  const uiRevealed = useSharedValue(false);
  const uiTargetRevealed = useSharedValue(false);
  const sleepRequested = useSharedValue(false);
  const lastProgress = useSharedValue(0);
  // Development only: `__rgr.pin(t)` freezes the loop at t s, `__rgr.play()` restarts it, `__rgr.progress(x)` holds the front at x (0..1).
  const pinned = useSharedValue(-1);

  useAnimatedReaction(
    () => uiRevealed.get(),
    (value, previous) => {
      if (value !== previous) scheduleOnRN(setRevealedRN, value);
    },
  );
  useAnimatedReaction(
    () => uiTargetRevealed.get(),
    (value, previous) => {
      if (value !== previous) scheduleOnRN(setTargetRN, value);
    },
  );

  const toggle = () => {
    setFramesRunning(true);
    sleepRequested.set(false);
    cmd.set(cmd.get() + 1);
  };
  const setTo = (r: boolean) => {
    setFramesRunning(true);
    sleepRequested.set(false);
    cmdTo.set(r ? 1 : -1);
  };
  const holdInput = () => {
    setFramesRunning(true);
    sleepRequested.set(false);
    const previous = live.get();
    const m = playing.get()
      ? liveFromTimeline(loopT.get(), geo)
      : { ...previous, depth: previous.depth.slice(), vel: previous.vel.slice() };
    playing.set(false);
    m.touching = true;
    m.touchX = -1e9;
    m.mode = 'hold';
    m.fv = 0;
    m.dragged = true; // a button is not a long press: no peek window
    live.set(m);
    const targetRevealed = m.target >= geo.front1 - 1;
    uiTargetRevealed.set(targetRevealed);
    setTargetRN(targetRevealed);
    touchedAt.set(clock.get());
  };
  const releaseInput = () => {
    const previous = live.get();
    live.set({ ...previous, depth: previous.depth.slice(), vel: previous.vel.slice(), touching: false, mode: 'spring', dragged: false });
    touchedAt.set(clock.get());
  };
  const bindApi = reveal._bind;
  useEffect(() => {
    bindApi({ toggle, set: setTo, hold: holdInput, release: releaseInput });
    return () => bindApi(null);
  });

  useEffect(() => {
    if (!__DEV__) return;
    const pin = (t: number) => {
      setFramesRunning(true);
      sleepRequested.set(false);
      pinned.set(t);
    };
    const controls = {
      pin,
      at: pin,
      play: () => {
        setFramesRunning(true);
        sleepRequested.set(false);
        pinned.set(-1);
        loopT.set(0);
        playing.set(true);
      },
      progress: (x: number) => {
        setFramesRunning(true);
        sleepRequested.set(false);
        pinned.set(-1);
        playing.set(false);
        touchedAt.set(1e9);
        const m = restLive(geo);
        m.mode = 'hold';
        m.fx = geo.front0 + Math.min(1, Math.max(0, x)) * (geo.front1 - geo.front0);
        m.target = m.fx;
        live.set(m);
      },
      state: () => ({ loopT: loopT.get(), playing: playing.get(), fx: look.get().front, revealed: uiRevealed.get() }),
    };
    const globals = globalThis as Record<string, unknown>;
    globals.__rgr = controls;
    return () => {
      if (globals.__rgr === controls) delete globals.__rgr;
    };
  }, [pinned, loopT, playing, touchedAt, live, geo, look, sleepRequested, uiRevealed]);

  // A controlled `revealed` flips the glass when it differs from where the glass is going.
  const lastControlled = useRef<boolean | undefined>(undefined);
  useEffect(() => {
    if (revealedProp === undefined || revealedProp === lastControlled.current) return;
    const first = lastControlled.current === undefined;
    lastControlled.current = revealedProp;
    if (first && !revealedProp) return;
    if (first && revealedProp && !autoPlay) {
      // start cleared, no sweep
      playing.set(false);
      live.set(clearedLive(geo));
      look.set(liveLook(clearedLive(geo), 1));
      uiRevealed.set(true);
      uiTargetRevealed.set(true);
      return;
    }
    if (revealedProp === uiTargetRevealed.get()) return;
    // the frame loop may be asleep: wake it from a timer, not from the effect body
    const id = setTimeout(() => setTo(revealedProp), 0);
    return () => clearTimeout(id);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [revealedProp]);
  const startedCleared = useRef(false);
  useEffect(() => {
    if (!defaultRevealed || startedCleared.current || revealedProp !== undefined) return;
    startedCleared.current = true;
    playing.set(false);
    live.set(clearedLive(geo));
    look.set(liveLook(clearedLive(geo), 1));
    uiRevealed.set(true);
    uiTargetRevealed.set(true);
  }, [defaultRevealed, revealedProp, geo, playing, live, look, uiRevealed, uiTargetRevealed]);

  // The canvas compiles its shaders before its first frame: what waits for it shows after the image and 8 smooth frames.
  const canDraw = portrait !== null && portraitSoft !== null && glassFx !== null;
  const gate = useSharedValue({ armed: false, waited: 0, smooth: 0, done: false });
  useEffect(() => {
    gate.set({ armed: canDraw, waited: 0, smooth: 0, done: false });
  }, [canDraw, gate]);
  const markReady = () => {
    setReadyRN(true);
    onReady?.();
  };

  const announce = (r: boolean) => {
    setRevealedRN(r);
    onRevealedChange?.(r);
  };

  const frameLoop = useFrameCallback((frame) => {
    'worklet';
    const ms = frame.timeSincePreviousFrame ?? 16.7;
    const g0 = gate.get();
    if (g0.armed && !g0.done) {
      const smooth = ms < 40 ? g0.smooth + 1 : 0;
      const waited = g0.waited + ms;
      const done = smooth >= 8 && waited > 300;
      gate.set({ armed: true, waited, smooth, done });
      if (done) scheduleOnRN(markReady);
    }
    if (!gate.get().done) {
      // Start the story only when its first fully rendered frame is ready.
      loopT.set(0);
      if (uiTargetRevealed.get()) {
        look.set(liveLook(live.get(), 1));
      } else {
        live.set(restLive(geo));
        look.set(timelineLook(0, geo));
      }
      return;
    }
    if (reducedSeen.get() !== reduce) {
      reducedSeen.set(reduce);
      playing.set(autoPlay && !reduce);
      loopT.set(0);
      live.set(restLive(geo));
      look.set(timelineLook(0, geo));
      uiRevealed.set(false);
      scheduleOnRN(setRevealedRN, false);
    }
    const pin = pinned.get();
    if (pin >= 0) {
      const t = ((pin % LOOP) + LOOP) % LOOP;
      look.set(timelineLook(t, geo));
      const r = t >= T.glyph1 && t < T.glyph2;
      if (uiRevealed.get() !== r) {
        uiRevealed.set(r);
        scheduleOnRN(setRevealedRN, r);
      }
      const targetRevealed = look.get().front >= (geo.front0 + geo.front1) / 2;
      if (uiTargetRevealed.get() !== targetRevealed) {
        uiTargetRevealed.set(targetRevealed);
        scheduleOnRN(setTargetRN, targetRevealed);
      }
      return;
    }
    const elapsed = Math.max(0, ms / 1000);
    const dt = Math.min(elapsed, 0.05);
    const now = clock.get() + elapsed;
    clock.set(now);

    // a value written to `progress` from outside takes the front there
    if (progressProp) {
      const pv = progressProp.get();
      if (Math.abs(pv - lastProgress.get()) > 0.004 && !live.get().touching) {
        const m = playing.get()
          ? liveFromTimeline(loopT.get(), geo)
          : { ...live.get(), depth: live.get().depth.slice(), vel: live.get().vel.slice() };
        playing.set(false);
        m.mode = 'hold';
        m.fx = geo.front0 + Math.min(1, Math.max(0, pv)) * (geo.front1 - geo.front0);
        m.target = m.fx;
        m.fv = 0;
        live.set(m);
        touchedAt.set(now);
        lastProgress.set(pv);
      }
    }

    // the swap button (or reveal.toggle() / set()): sweep to the other end from wherever the glass is
    const wantTo = cmdTo.get();
    if (cmd.get() !== cmdSeen.get() || wantTo !== 0) {
      let m: Live;
      if (playing.get()) {
        playing.set(false);
        m = liveFromTimeline(loopT.get(), geo);
      } else {
        m = { ...live.get(), depth: live.get().depth.slice(), vel: live.get().vel.slice() };
      }
      const toEnd = wantTo !== 0 ? (wantTo > 0 ? geo.front1 : geo.front0) : m.target >= geo.front1 - 1 ? geo.front0 : geo.front1;
      cmdSeen.set(cmd.get());
      cmdTo.set(0);
      if (direction === 'rtl') m.flip = true;
      if (reduce) m.target = toEnd;
      else if (Math.abs(toEnd - m.fx) > 1) startSweep(m, geo, toEnd);
      live.set(m);
      touchedAt.set(now);
    }

    if (playing.get()) {
      if (!autoPlay || reduce) {
        playing.set(false);
        live.set(restLive(geo));
      } else {
        const t = (loopT.get() + elapsed) % LOOP;
        loopT.set(t);
        look.set(timelineLook(t, geo));
        const r = t >= T.glyph1 && t < T.glyph2;
        if (uiRevealed.get() !== r) {
          uiRevealed.set(r);
          scheduleOnRN(setRevealedRN, r);
        }
        const targetRevealed = look.get().front >= (geo.front0 + geo.front1) / 2;
        if (uiTargetRevealed.get() !== targetRevealed) {
          uiTargetRevealed.set(targetRevealed);
          scheduleOnRN(setTargetRN, targetRevealed);
        }
        const pl = (look.get().front - geo.front0) / (geo.front1 - geo.front0);
        if (progressProp) progressProp.set(pl);
        progressOut.set(pl);
        lastProgress.set(pl);
        return;
      }
    }

    const m = { ...live.get(), depth: live.get().depth.slice(), vel: live.get().vel.slice() };
    const ev = stepLive(m, dt, geo, reduce, now, peek);
    if (haptics) {
      if (ev.edge) scheduleOnRN(cueEdge);
      else if (ev.tick) scheduleOnRN(cueTick);
    }
    if (ev.revealed) {
      revealedAt.set(now);
      uiRevealed.set(true);
      if (haptics) scheduleOnRN(cueRevealed);
      scheduleOnRN(announce, true);
    }
    if (ev.masked) {
      uiRevealed.set(false);
      if (haptics) scheduleOnRN(cueMasked);
      scheduleOnRN(announce, false);
    }
    const idle = !m.touching && m.mode !== 'drag' && m.mode !== 'sweep' && m.mode !== 'hold';
    if (autoPlay && !reduce) {
      // 2 s after the last touch the glass eases back to rest, then the loop starts again at t = 0
      if (shouldResume(m, now, touchedAt.get())) {
        if (m.target !== geo.front0) {
          m.mode = 'spring';
          m.target = geo.front0;
        }
        if (liveAtRest(m, geo)) {
          live.set(restLive(geo));
          loopT.set(0);
          playing.set(true);
          look.set(timelineLook(0, geo));
          if (uiRevealed.get()) {
            uiRevealed.set(false);
            scheduleOnRN(setRevealedRN, false);
          }
          return;
        }
      }
    } else if (
      idle &&
      m.revealed &&
      autoMaskMs > 0 &&
      now - revealedAt.get() > autoMaskMs / 1000 &&
      now - touchedAt.get() > GESTURE.resumeAfter
    ) {
      if (reduce) m.target = geo.front0;
      else if (m.target !== geo.front0) startSweep(m, geo, geo.front0);
    }
    live.set(m);
    const lk = liveLook(m, 1);
    look.set(lk);
    const pl = (lk.front - geo.front0) / (geo.front1 - geo.front0);
    progressOut.set(pl);
    if (progressProp) {
      progressProp.set(pl);
      lastProgress.set(pl);
    }
    const targetRevealed = m.target >= geo.front1 - 1;
    if (uiTargetRevealed.get() !== targetRevealed) {
      uiTargetRevealed.set(targetRevealed);
      scheduleOnRN(setTargetRN, targetRevealed);
    }
    if (idle && (!autoPlay || reduce) && liveAtRest(m, geo) && !sleepRequested.get() && !progressProp) {
      // A static pane needs no per-frame allocations or shader updates. A real gesture/button wakes it.
      sleepRequested.set(true);
      scheduleOnRN(setFramesRunning, false);
    }
  });

  useEffect(() => {
    sleepRequested.set(false);
    frameLoop.setActive(true);
  }, [canDraw, reduce, autoPlay, geo, sleepRequested, frameLoop]);
  useEffect(() => {
    // a fresh layout restarts the story at its first frame (a cleared glass stays cleared)
    if (uiTargetRevealed.get() && !autoPlay) {
      live.set(clearedLive(geo));
      look.set(liveLook(clearedLive(geo), 1));
    } else {
      live.set(restLive(geo));
      look.set(timelineLook(0, geo));
      uiRevealed.set(false);
      uiTargetRevealed.set(false);
    }
    loopT.set(0);
    playing.set(autoPlay && !reduce);
  }, [geo, live, look, loopT, playing, uiRevealed, uiTargetRevealed, autoPlay, reduce]);
  useEffect(() => {
    frameLoop.setActive(framesRunning);
  }, [frameLoop, framesRunning]);

  // ---- the finger on the glass ----
  const pan = useMemo(() => {
    const takeOver = (): Live => {
      'worklet';
      if (playing.get()) {
        playing.set(false);
        return liveFromTimeline(loopT.get(), geo);
      }
      const c = live.get();
      return { ...c, depth: c.depth.slice(), vel: c.vel.slice() };
    };

    return Gesture.Pan()
      .minDistance(0)
      .onBegin((e) => {
        'worklet';
        const m = takeOver();
        scheduleOnRN(setFramesRunning, true);
        sleepRequested.set(false);
        m.touching = true;
        m.touchX = e.x;
        m.mode = 'hold';
        m.fv = 0;
        m.dragged = false;
        m.holdT = 0;
        m.peekRel = -1;
        if (direction === 'rtl') m.flip = true;
        if (direction === 'ltr') m.flip = false;
        live.set(m);
        touch.set({ startX: e.x, frontStart: m.fx, dragged: false });
        touchedAt.set(clock.get());
        if (haptics) scheduleOnRN(cueOnset);
      })
      .onUpdate((e) => {
        'worklet';
        const t = touch.get();
        const m = { ...live.get(), depth: live.get().depth.slice(), vel: live.get().vel.slice() };
        m.touchX = e.x;
        const dx = e.x - t.startX;
        if (m.peek > 0.3) {
          // an open peek window follows the finger instead of moving the front
        } else if (!reduce) {
          const dead = direction === 'auto' && m.fx <= geo.front0 + 1 && !m.revealed ? GESTURE.directionDecide : 6;
          if (!t.dragged && Math.abs(dx) > dead) {
            if (direction === 'auto') decideDirection(m, geo, t.startX, dx);
            m.dragged = true;
            m.peek = 0;
            touch.set({ ...t, dragged: true });
          }
          if (t.dragged || Math.abs(dx) > dead) dragLive(m, geo, t.frontStart, e.translationX, e.velocityX);
        } else if (!t.dragged && Math.abs(dx) > 6) {
          m.dragged = true;
          touch.set({ ...t, dragged: true });
        }
        live.set(m);
        touchedAt.set(clock.get());
      })
      .onFinalize((e, success) => {
        'worklet';
        const m = { ...live.get(), depth: live.get().depth.slice(), vel: live.get().vel.slice() };
        const t = touch.get();
        finishTouch(m, geo, t.dragged, e.velocityX, reduce, success, commitAt);
        live.set(m);
        touchedAt.set(clock.get());
      });
  }, [playing, loopT, geo, live, sleepRequested, touch, touchedAt, clock, reduce, direction, haptics, commitAt]);

  // ---- uniforms (numbers only: the layout's functions must not reach a worklet) ----
  const imgSk = useMemo(() => ({ x: G.img.x, y: G.img.y, width: G.img.w, height: G.img.h }), [G]);
  const ctx = useMemo(
    () => ({ img: [G.img.x, G.img.y, G.img.w, G.img.h], edges: G.edges, magC: G.magC, S: G.S, strength: depth, glints }),
    [G, depth, glints],
  );
  const glassU = useDerivedValue(() => glassUniforms(look.get(), ctx, reduce));
  const areas: Rect4[] = useMemo(() => drawAreas ?? [{ x: 0, y: 0, w: W, h: H }], [drawAreas, W, H]);
  const ready = canDraw;

  return (
    <View style={[styles.box, style]} onLayout={onLayout}>
      <Canvas style={StyleSheet.absoluteFill} accessible={false}>
        {ready
          ? areas.map((r, i) => (
              <Rect key={i} x={r.x} y={r.y} width={r.w} height={r.h}>
                <Shader source={glassFx!} uniforms={glassU}>
                  <ImageShader image={portrait} fit="fill" rect={imgSk} sampling={SAMPLING} />
                  <ImageShader image={portraitSoft} fit="fill" rect={imgSk} sampling={SAMPLING} />
                </Shader>
              </Rect>
            ))
          : null}
      </Canvas>
      <GestureDetector gesture={pan}>
        <View
          style={[styles.hit, hitHeight === undefined ? styles.hitFull : { height: hitHeight }]}
          accessible
          accessibilityRole="image"
          accessibilityLabel={accessibilityLabel}
          accessibilityActions={[{ name: 'activate' }]}
          onAccessibilityAction={toggle}
        />
      </GestureDetector>
    </View>
  );
}

const styles = StyleSheet.create({
  box: { overflow: 'hidden' },
  hit: { position: 'absolute', left: 0, top: 0, right: 0 },
  hitFull: { bottom: 0 },
});
