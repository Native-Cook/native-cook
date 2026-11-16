// Woven Speaker Player — a black dome of two woven strand layers over a round display, and the page under it. The dome is the jog
// wheel: drag round it and the track scrubs 1:1 (360° = `turnSeconds`) while the two layers turn at different rates, so the
// diamonds of the weave shear and settle like cloth; press it and the fabric dents under the finger; play and the music travels
// through the cloth as rings. The three lines of text under the dome are live too: they follow the playing state, and the headline
// is the queue's handle (swipe it to change track).
//
// It is a component for a speaker's now-playing hub (a Sonos-style companion, a podcast or sleep-sound player, a voice-memo or DJ
// tool, a smart-home hub). State is controlled or uncontrolled; values leave through callbacks. Mount it as a full-screen sibling:
// it draws its own page, the dome, the display and the text in one canvas, and only the dome, the three buttons and the headline
// take touches.
//
//   <WovenSpeaker duration={track.seconds} queue={queue} ambient level={bass} onSeekCommit={seek} onIndexChange={skipTo} />
import { type ReactNode, useCallback, useEffect, useMemo, useState } from 'react';
import { type AccessibilityActionEvent, PixelRatio, Platform, StyleSheet, useWindowDimensions, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import {
  Easing,
  type SharedValue,
  useDerivedValue,
  useFrameCallback,
  useSharedValue,
  withDelay,
  withSequence,
  withSpring,
  withTiming,
} from 'react-native-reanimated';
import {
  Canvas,
  Fill,
  Glyphs,
  Group,
  LinearGradient,
  RadialGradient,
  Rect,
  RoundedRect,
  Shader,
  Skia,
  useTypeface,
  vec,
} from '@shopify/react-native-skia';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

import { useReducedMotionPreference } from '../../../shared/useReducedMotionPreference';
import { DisplayBase, DisplayContext, type DisplayCtx, type DisplayRuns, NowPlaying } from './WspDisplay';
import { ringPath } from './wsp-geometry';
import { FIRST_NOTE_DELAY_MS, haptic, PAUSE_BEAT_DELAY_MS, silence } from './wsp-haptics';
import {
  ACCENT_EVERY,
  ANNULUS,
  BEAT,
  BLOOM_REST,
  clamp01,
  DENT,
  GAIN,
  GESTURE,
  glide,
  INNER,
  type Look,
  LOOP,
  lookAt,
  mix,
  PULL,
  REST_LOOK,
  RING,
  ringAt,
  pulseAt,
  SHEAR_FULL,
  smooth,
  snap,
  SPRING,
  TRACK,
  wrapPi,
} from './wsp-motion';
import { WEAVE_SKSL } from './wsp-shaders';
import { cellWidth, makeRun, type Run } from './wsp-text';
import { COPY, DEFAULT_SIZE, DEMO_QUEUE, hexToRgb, makeLayout, ref, resolveTokens, type WovenTokens, type WovenTrack } from './wsp-theme';

export { LOOP };
export type { WovenTokens, WovenTrack };
export { useWovenDisplay } from './WspDisplay';

const weaveFx = Skia.RuntimeEffect.Make(WEAVE_SKSL);
const P3 = Platform.OS === 'ios' ? 1 : 0;
const C = ref.color;
const DOME_BOX = 346; // the shader's square, design pt
const HIT_BOX = 340; // the dome's touch square, design pt
const GLASS_CLIP = ringPath(ref.display.r);

export type WovenSpeakerProps = {
  /** track position, seconds, on the UI thread: read it to drive your page, write it to drive the track */
  position?: SharedValue<number>;
  defaultPosition?: number;
  /** track length, seconds (default 200) */
  duration?: number;
  playing?: boolean;
  defaultPlaying?: boolean;
  onPlayingChange?: (playing: boolean) => void;
  /** while turning, at most 10 Hz */
  onSeek?: (seconds: number) => void;
  /** once the position has settled on a whole second after a turn or a coast */
  onSeekCommit?: (seconds: number) => void;
  /** the low band's envelope 0 … 1 from your audio; without it the demo's beat timeline (`beats`) launches the rings */
  level?: SharedValue<number>;
  beats?: { bpm: number; accentEvery?: number };
  /** track seconds per full turn of the finger round the dome (default `duration`) */
  turnSeconds?: number;
  /** the lamp and the arc (default #FF3B30) */
  accent?: string;
  /** strands per layer round the dome, even (the shot: 128; fewer on a slow phone) */
  strands?: number;
  /** the dome's diameter in pt (default 360, scaled down on a narrow phone) */
  size?: number;
  tokens?: WovenTokens;
  /** the round display's content, as Skia nodes in design pt centred on the display (default: the shot's now-playing) */
  children?: ReactNode;
  /** play the demo loop until a finger arrives, and again 2 s after it leaves */
  autoPlay?: boolean;
  // ---- Pro ----
  /** 0 … 1 irregularity of the cloth (default 0.5) */
  fibre?: number;
  /** the lamp washes the page while playing (default true) */
  ambient?: boolean;
  queue?: WovenTrack[];
  index?: number;
  defaultIndex?: number;
  onIndexChange?: (index: number) => void;
  onOpenQueue?: () => void;
  /** a palm on the cloth (duck or mute) */
  onPressSurface?: (pressed: boolean) => void;
  coast?: { decay: number; maxTurns: number };
  /** default true */
  haptics?: boolean;
  accessibilityLabel?: string;
};
export type WovenSpeakerProProps = WovenSpeakerProps;

type Grip = {
  region: number; // 0 none, 1 mesh, 2 display, 3 outside
  sub: number; // display: 1 centre, 2 previous, 3 next
  a0: number;
  prev: number;
  acc: number;
  turning: boolean;
  v: number;
  vAt: number;
  tickAt: number;
  tickN: number;
  atLimit: boolean;
  byTouchPlay: boolean;
};

const NO_GRIP: Grip = {
  region: 0,
  sub: 0,
  a0: 0,
  prev: 0,
  acc: 0,
  turning: false,
  v: 0,
  vAt: 0,
  tickAt: -9,
  tickN: 0,
  atLimit: false,
  byTouchPlay: false,
};

function differs(a: Look, b: Look) {
  'worklet';
  const x = a as unknown as Record<string, number>;
  const y = b as unknown as Record<string, number>;
  for (const key in x) if (x[key] !== y[key]) return true;
  return false;
}

/** Launches a pulse in the oldest of the four slots (t0, amp pairs). */
function launchInto(slots: number[], now: number, amp: number) {
  'worklet';
  let at = 0;
  let oldest = 1e9;
  for (let i = 0; i < 4; i++) {
    const t0 = slots[i * 2];
    if (t0 < oldest) {
      oldest = t0;
      at = i;
    }
  }
  const next = slots.slice();
  next[at * 2] = now;
  next[at * 2 + 1] = amp;
  return next;
}

function tailOf(accent: string) {
  if (accent.toUpperCase() === C.accent) return C.accentTail;
  const [r, g, b] = hexToRgb(accent);
  const m = (v: number) => Math.round(Math.min(1, v + (1 - v) * 0.32) * 255);
  return `#${[m(r), m(g * 0.92), m(b * 0.8)].map((v) => v.toString(16).padStart(2, '0')).join('')}`;
}

export function WovenSpeaker({
  position,
  defaultPosition = 0,
  duration = TRACK,
  playing: playingProp,
  defaultPlaying = false,
  onPlayingChange,
  onSeek,
  onSeekCommit,
  level,
  beats,
  turnSeconds,
  accent = C.accent,
  strands = 128,
  size = DEFAULT_SIZE,
  tokens,
  children,
  autoPlay = false,
  fibre = 0.5,
  ambient = true,
  queue: queueProp,
  index: indexProp,
  defaultIndex = 0,
  onIndexChange,
  onOpenQueue,
  onPressSurface,
  coast,
  haptics = true,
  accessibilityLabel,
}: WovenSpeakerProps) {
  const { width: W, height: H } = useWindowDimensions();
  const reduce = useReducedMotionPreference();
  const insets = useSafeAreaInsets();
  const L = useMemo(() => makeLayout(W, H, size, insets.top, insets.bottom), [W, H, size, insets.top, insets.bottom]);
  const th = useMemo(() => resolveTokens(tokens), [tokens]);
  const accentHex = /^#[0-9a-fA-F]{6}$/.test(accent) ? accent : C.accent;
  const accentTail = useMemo(() => tailOf(accentHex), [accentHex]);
  const accentRgb = useMemo(() => hexToRgb(accentHex), [accentHex]);
  const inkRgb = useMemo(() => hexToRgb(th.ink), [th.ink]);
  const cue = haptics ? haptic : silence;
  const queue = useMemo(() => (queueProp && queueProp.length > 0 ? queueProp : DEMO_QUEUE).slice(0, 6), [queueProp]);
  const nStrands = Math.min(Math.max(Math.round(strands / 2) * 2, 48), 160);
  const turnSec = turnSeconds ?? duration;
  const coastDecay = coast?.decay ?? GESTURE.coastDecay;
  const coastTurns = coast?.maxTurns ?? GESTURE.coastMaxTurns;
  const beatLen = beats ? 60 / beats.bpm : BEAT;
  const accentEvery = beats?.accentEvery ?? ACCENT_EVERY;

  // plain numbers for the worklets (the layout object also carries functions, which must not be captured)
  const S = L.S;
  const CX = L.cx;
  const CY = L.cy;
  const PIX = PixelRatio.get();
  const D = duration;
  const OX = L.cx - 201 * S;
  const OY = L.y(0);
  const N = queue.length;

  // ---- fonts and text runs ----
  const fBold = useTypeface(require('./assets/fonts/InterDisplay-Bold.ttf'));
  const fReg = useTypeface(require('./assets/fonts/Inter-Regular.ttf'));
  const fMed = useTypeface(require('./assets/fonts/Inter-Medium.ttf'));
  const fSemi = useTypeface(require('./assets/fonts/Inter-SemiBold.ttf'));
  const text = useMemo(() => {
    if (!fBold || !fReg || !fMed || !fSemi) return null;
    const display: DisplayRuns = {
      title: queue.map((q) => makeRun(fSemi, ref.track.size, q.title)),
      artist: queue.map((q) => makeRun(fMed, ref.track.artistSize, (q.subtitle ?? '').toUpperCase(), ref.track.artistTrack)),
      avatar: makeRun(fSemi, ref.row.avatarInk, 'N'),
      digitIds: Array.from(Skia.Font(fMed, ref.time.size).getGlyphIDs('0123456789:')),
      digit: '0123456789:'.split('').map((ch) => cellWidth(fMed, ref.time.size, ch)),
      timeFont: Skia.Font(fMed, ref.time.size),
      cell: cellWidth(fMed, ref.time.size, '0'),
      colon: cellWidth(fMed, ref.time.size, ':'),
    };
    const page = {
      headline: makeRun(fBold, ref.title.size, COPY.headline, -0.021),
      titles: queue.map((q) => makeRun(fBold, ref.title.size, q.title, -0.021)),
      line: makeRun(fReg, ref.line.size, COPY.subtitle, -0.013),
      linePlaying: makeRun(fReg, ref.line.size, COPY.subtitlePlaying, -0.013),
      pill: makeRun(fMed, ref.pill.size, COPY.pill, -0.006),
      pillPlaying: makeRun(fMed, ref.pill.size, COPY.pillPlaying, -0.006),
    };
    return { display, page };
  }, [fBold, fReg, fMed, fSemi, queue]);

  // ---- state: the timeline plays `look`; a finger hands it to the live values ----
  const look = useSharedValue<Look>(REST_LOOK);
  const clock = useSharedValue(0);
  const loopT = useSharedValue(0);
  const auto = useSharedValue(autoPlay && !reduce);
  const pinned = useSharedValue(-1);
  const startPlaying = playingProp ?? defaultPlaying;
  const posOwn = useSharedValue(Math.min(Math.max(defaultPosition, 0), D));
  const posL = position ?? posOwn;
  const xL = useSharedValue(Math.min(Math.max(defaultPosition, 0), D));
  const playingL = useSharedValue(startPlaying);
  const glyphL = useSharedValue(startPlaying ? 1 : 0);
  const bloomL = useSharedValue(startPlaying ? 1 : 0);
  const lampL = useSharedValue(startPlaying ? 1 : 0);
  const titleL = useSharedValue(startPlaying ? 1 : 0);
  const lineL = useSharedValue(startPlaying ? 1 : 0);
  const pillL = useSharedValue(startPlaying ? 1 : 0);
  const reflL = useSharedValue(startPlaying ? 1 : 0);
  const handleL = useSharedValue(startPlaying ? 1 : 0);
  const pressL = useSharedValue(1);
  const turnL = useSharedValue(0);
  const psiOL = useSharedValue(0);
  const shearDL = useSharedValue(0);
  const prevPsi = useSharedValue(0);
  const prevShear = useSharedValue(0);
  const fingerL = useSharedValue(0);
  const dentXL = useSharedValue(0);
  const dentYL = useSharedValue(0);
  const dentL = useSharedValue(0);
  const pullL = useSharedValue(0);
  const pullAtL = useSharedValue(0);
  const headL = useSharedValue(0);
  const ringsL = useSharedValue<number[]>([-9, 0, -9, 0, -9, 0, -9, 0]);
  const strongAt = useSharedValue(-9);
  const playClock = useSharedValue(0);
  const nextBeat = useSharedValue(0.12);
  const beatN = useSharedValue(0);
  const levelPrev = useSharedValue(0);
  const levelAt = useSharedValue(-9);
  const slideL = useSharedValue(0);
  const indexL = useSharedValue(Math.min(Math.max(indexProp ?? defaultIndex, 0), N - 1));
  const touching = useSharedValue(false);
  const touchedAt = useSharedValue(-1e9);
  const coasting = useSharedValue(false);
  const coastV = useSharedValue(0);
  const snapUntil = useSharedValue(0);
  const resuming = useSharedValue(false);
  const grip = useSharedValue<Grip>(NO_GRIP);
  const seekAt = useSharedValue(-9);
  const pauseBeatAt = useSharedValue(-1);
  const pillPress = useSharedValue(1);
  const [a11y, setA11y] = useState({
    pos: Math.round(defaultPosition),
    playing: startPlaying,
    index: Math.min(Math.max(indexProp ?? defaultIndex, 0), N - 1),
  });

  // the host's callbacks cross from the UI thread through these (scheduleOnRN needs a plain function)
  const emitPlaying = useCallback((p: boolean) => onPlayingChange?.(p), [onPlayingChange]);
  const emitSeek = useCallback((s: number) => onSeek?.(s), [onSeek]);
  const emitCommit = useCallback(
    (s: number) => {
      onSeekCommit?.(s);
      setA11y((a) => ({ ...a, pos: s }));
    },
    [onSeekCommit],
  );
  const emitIndex = useCallback(
    (i: number) => {
      setA11y((a) => ({ ...a, index: i }));
      onIndexChange?.(i);
    },
    [onIndexChange],
  );
  const emitPress = useCallback((p: boolean) => onPressSurface?.(p), [onPressSurface]);
  const emitOpen = useCallback(() => onOpenQueue?.(), [onOpenQueue]);
  const emitA11yPlaying = useCallback((p: boolean) => setA11y((a) => ({ ...a, playing: p })), []);

  // Development only: `__wsp.pin(t)` freezes the loop at t s, `__wsp.play()` lets it run, `__wsp.state()` reads it.
  useEffect(() => {
    if (!__DEV__) return;
    const globals = globalThis as Record<string, unknown>;
    const previous = globals.__wsp;
    const hook = {
      pin: (t: number) => pinned.set(t),
      at: (t: number) => pinned.set(t),
      play: () => pinned.set(-1),
      state: () => ({ ...look.get(), loopT: loopT.get(), auto: auto.get() }),
    };
    globals.__wsp = hook;
    return () => {
      if (globals.__wsp === hook) globals.__wsp = previous;
    };
  }, [pinned, look, loopT, auto]);

  useEffect(() => {
    auto.set(autoPlay && !reduce);
  }, [autoPlay, reduce, auto]);

  // ---- the controller (worklets) ----
  /** Plays or pauses: every part follows with its own delay (the concept's choreography), from where it is now. */
  const setPlaying = (next: boolean, byTouch: boolean) => {
    'worklet';
    if (playingL.get() === next) return;
    playingL.set(next);
    const inOut = Easing.bezier(0.65, 0, 0.35, 1);
    const out = Easing.bezier(0.2, 0.8, 0.2, 1);
    const now = clock.get();
    if (reduce) {
      const t = { duration: 200 };
      glyphL.set(withTiming(next ? 1 : 0, t));
      bloomL.set(withTiming(next ? 1 : 0, t));
      lampL.set(withTiming(next ? 1 : 0, t));
      titleL.set(withTiming(next ? 1 : 0, t));
      lineL.set(withTiming(next ? 1 : 0, t));
      pillL.set(withTiming(next ? 1 : 0, t));
      reflL.set(withTiming(next ? 1 : 0, t));
      handleL.set(withTiming(next ? 1 : 0, t));
    } else if (next) {
      glyphL.set(withTiming(1, { duration: 260, easing: inOut }));
      bloomL.set(withDelay(60, withTiming(1, { duration: 300, easing: out })));
      lampL.set(withDelay(120, withTiming(1, { duration: 220, easing: out })));
      titleL.set(withDelay(160, withTiming(1, { duration: 240, easing: inOut })));
      lineL.set(withDelay(200, withTiming(1, { duration: 240, easing: inOut })));
      pillL.set(withDelay(240, withTiming(1, { duration: 240, easing: inOut })));
      reflL.set(withDelay(330, withTiming(1, { duration: 200, easing: out })));
      handleL.set(withDelay(800, withTiming(1, { duration: 250, easing: out })));
      pressL.set(withSequence(withTiming(0.9, { duration: 80, easing: out }), withSpring(1, snap)));
    } else {
      glyphL.set(withTiming(0, { duration: 220, easing: inOut }));
      pillL.set(withTiming(0, { duration: 240, easing: inOut }));
      lineL.set(withDelay(40, withTiming(0, { duration: 240, easing: inOut })));
      titleL.set(withDelay(80, withTiming(0, { duration: 240, easing: inOut })));
      bloomL.set(withTiming(0, { duration: 400, easing: out }));
      lampL.set(withTiming(0, { duration: 400, easing: out }));
      reflL.set(withTiming(0, { duration: 200, easing: out }));
      handleL.set(withTiming(0, { duration: 300, easing: out }));
      pressL.set(withSequence(withTiming(0.9, { duration: 80, easing: out }), withSpring(1, snap)));
      pauseBeatAt.set(now + PAUSE_BEAT_DELAY_MS / 1000);
    }
    if (next) {
      playClock.set(0);
      nextBeat.set(FIRST_NOTE_DELAY_MS / 1000);
      beatN.set(0);
    }
    const g = grip.get();
    grip.set({ ...g, byTouchPlay: byTouch && next });
    scheduleOnRN(emitPlaying, next);
    scheduleOnRN(emitA11yPlaying, next);
  };

  /** A finger (or the host) takes the control: the demo's current frame becomes the live state, no jump. */
  const takeOver = () => {
    'worklet';
    resuming.set(false);
    if (!auto.get()) return;
    auto.set(false);
    const k = look.get();
    posL.set(k.pos);
    xL.set(k.pos);
    const on = k.glyph > 0.5;
    playingL.set(on);
    glyphL.set(k.glyph);
    bloomL.set((k.bloom - BLOOM_REST) / (1 - BLOOM_REST));
    lampL.set((k.lamp - 0.12) / 0.88);
    titleL.set(k.title);
    lineL.set(k.line);
    pillL.set(k.pill);
    reflL.set(k.refl);
    handleL.set(k.handle);
    pressL.set(k.press);
    turnL.set(k.turn);
    psiOL.set(k.psiO);
    shearDL.set(k.psiO - k.psiI);
    prevPsi.set(k.psiO);
    prevShear.set(k.psiO - k.psiI);
    fingerL.set(k.finger);
    dentXL.set(k.dentX);
    dentYL.set(k.dentY);
    dentL.set(k.dent);
    pullL.set(k.pull);
    pullAtL.set(k.pullAt);
    ringsL.set([-9, 0, -9, 0, -9, 0, -9, 0]);
    playClock.set(0);
    nextBeat.set(FIRST_NOTE_DELAY_MS / 1000);
    beatN.set(0);
    scheduleOnRN(emitA11yPlaying, on);
  };

  /** The finger (or the coast) turned the dome by `dA` rad: the position follows 1:1, the weave 0.22 of it, rubber past the ends. */
  const turnBy = (dA: number, shear: boolean) => {
    'worklet';
    const dx = (dA / (2 * Math.PI)) * turnSec;
    const x0 = xL.get();
    const x1 = x0 + dx;
    const p0 = Math.min(Math.max(x0, 0), D);
    const p1 = Math.min(Math.max(x1, 0), D);
    const inDx = p1 - p0;
    const outDx = dx - inDx;
    const eff = ((inDx + GESTURE.rubber * outDx) / turnSec) * 2 * Math.PI;
    xL.set(x1);
    posL.set(p1);
    psiOL.set(psiOL.get() + GAIN * eff);
    if (shear && !reduce) shearDL.set(shearDL.get() + (1 - INNER) * GAIN * eff);
    return x1 < 0 || x1 > D;
  };

  /** Snaps the position to the nearest second with a spring, and tells the host. */
  const snapToSecond = () => {
    'worklet';
    const target = Math.round(Math.min(Math.max(posL.get(), 0), D));
    xL.set(target);
    snapUntil.set(clock.get() + 0.45);
    posL.set(withSpring(target, SPRING.snapSecond));
    scheduleOnRN(cue.settle);
    scheduleOnRN(emitCommit, target);
  };

  const release = (turned: boolean, v: number) => {
    'worklet';
    const now = clock.get();
    touching.set(false);
    touchedAt.set(now);
    dentL.set(withSpring(0, { damping: 9, stiffness: 150, mass: 1 }));
    scheduleOnRN(emitPress, false);
    if (!turned) return;
    turnL.set(withTiming(0, { duration: 400, easing: Easing.bezier(0.65, 0, 0.35, 1) }));
    shearDL.set(withSpring(0, SPRING.relax));
    pullAtL.set(fingerL.get());
    pullL.set(PULL);
    pullL.set(withDelay(60, withTiming(0, { duration: 1200, easing: Easing.bezier(0.2, 0.8, 0.2, 1) })));
    const vMax = coastTurns * 2 * Math.PI * (-Math.log(coastDecay) * 1000);
    const vv = Math.max(-Math.abs(vMax), Math.min(Math.abs(vMax), v));
    if (Math.abs(vv) > 0.6 && xL.get() >= 0 && xL.get() <= D) {
      coastV.set(vv);
      coasting.set(true);
    } else {
      if (xL.get() < 0 || xL.get() > D) xL.set(Math.min(Math.max(xL.get(), 0), D));
      snapToSecond();
    }
  };

  const commitTrack = (dir: number, fromSwipe: boolean) => {
    'worklet';
    const i = indexL.get();
    const j = i + dir;
    if (j < 0 || j >= N) {
      slideL.set(withSequence(withTiming(-dir * 14, { duration: 90 }), withSpring(0, glide)));
      return;
    }
    indexL.set(j);
    slideL.set(slideL.get() + dir * GESTURE.slideSpan);
    slideL.set(withSpring(0, fromSwipe ? glide : snap));
    ringsL.set(launchInto(ringsL.get(), clock.get(), 1.6));
    scheduleOnRN(cue.track);
    scheduleOnRN(emitIndex, j);
  };

  const rest = () => {
    'worklet';
    // 2 s after the last touch: ease back to the shot's rest frame, then the loop starts again at t = 0
    resuming.set(true);
    const t = { duration: 600, easing: Easing.bezier(0.65, 0, 0.35, 1) };
    playingL.set(false);
    posL.set(withTiming(0, t));
    xL.set(0);
    glyphL.set(withTiming(0, t));
    bloomL.set(withTiming(0, t));
    lampL.set(withTiming(0, t));
    titleL.set(withTiming(0, t));
    lineL.set(withTiming(0, t));
    pillL.set(withTiming(0, t));
    reflL.set(withTiming(0, t));
    handleL.set(withTiming(0, t));
    pressL.set(withTiming(1, t));
    turnL.set(withTiming(0, t));
    psiOL.set(withTiming(0, t));
    shearDL.set(withTiming(0, t));
    dentL.set(withTiming(0, t));
    pullL.set(withTiming(0, t));
    slideL.set(withTiming(0, t));
    indexL.set(0);
  };

  const frames = useFrameCallback((frame) => {
    'worklet';
    const dt = Math.min((frame.timeSincePreviousFrame ?? 16.7) / 1000, 0.05);
    const now = clock.get() + dt;
    clock.set(now);
    const pin = pinned.get();
    if (pin >= 0) {
      const k = lookAt(pin);
      if (differs(k, look.get())) look.set(k);
      return;
    }
    if (auto.get()) {
      const t = (loopT.get() + dt) % LOOP;
      loopT.set(t);
      const k = lookAt(t);
      if (differs(k, look.get())) look.set(k);
      return;
    }

    // ---- live ----
    const touch = touching.get();
    const g = grip.get();
    let limitHit = false;
    // the coast: the angular speed decays at 0.996 per ms, the same mapping as the finger
    if (coasting.get()) {
      let v = coastV.get() * Math.pow(coastDecay, dt * 1000);
      coastV.set(v);
      limitHit = turnBy(v * dt, false);
      if (Math.abs(v) < GESTURE.coastStop || limitHit) {
        v = 0;
        coasting.set(false);
        if (limitHit) {
          xL.set(Math.min(Math.max(xL.get(), 0), D));
          scheduleOnRN(cue.limit);
          headL.set(withSequence(withTiming(1, { duration: 120 }), withTiming(0, { duration: 260 })));
        }
        snapToSecond();
      }
      if (now - seekAt.get() >= 0.1) {
        seekAt.set(now);
        scheduleOnRN(emitSeek, posL.get());
      }
    }
    const busy = touch || coasting.get() || now < snapUntil.get();
    // playback: one second per second, nothing while a finger or a coast owns the position
    if (playingL.get() && !busy) {
      const p = posL.get() + dt;
      if (p >= D) {
        posL.set(D);
        xL.set(D);
        setPlaying(false, false);
        scheduleOnRN(cue.limit);
      } else {
        posL.set(p);
        xL.set(p);
      }
    }
    // pulses: on the beat grid, or on the host's low band; none while the cloth is under a finger
    let rings = ringsL.get();
    if (playingL.get() && !busy) {
      if (level) {
        const lv = level.get();
        if (lv > 0.55 && levelPrev.get() <= 0.55 && now - levelAt.get() > 0.18) {
          levelAt.set(now);
          rings = launchInto(rings, now, mix(RING.weak, RING.strong, clamp01((lv - 0.55) / 0.45)));
          if (lv > 0.85) strongAt.set(now);
        }
        levelPrev.set(lv);
      } else {
        playClock.set(playClock.get() + dt);
        while (playClock.get() >= nextBeat.get()) {
          const n = beatN.get();
          const strong = n % accentEvery === 0;
          rings = launchInto(rings, now, strong ? RING.strong : RING.weak);
          if (strong) strongAt.set(now);
          if (n === 0 && g.byTouchPlay) scheduleOnRN(cue.firstNote);
          beatN.set(n + 1);
          nextBeat.set(nextBeat.get() + beatLen);
        }
      }
      ringsL.set(rings);
    }
    if (pauseBeatAt.get() > 0 && now >= pauseBeatAt.get()) {
      pauseBeatAt.set(-1);
      scheduleOnRN(cue.pause);
    }

    // ---- compose the look from the live values ----
    const prev = look.get();
    const k: Look = { ...REST_LOOK };
    const pos = posL.get();
    k.pos = pos;
    k.glyph = glyphL.get();
    k.press = pressL.get();
    const bl = bloomL.get();
    k.bloom = mix(BLOOM_REST, 1, bl);
    k.bloomR = mix(ref.bloom.r0, ref.bloom.r1, bl);
    const lp = lampL.get();
    k.lamp = mix(0.12, 1, lp);
    k.reach = mix(18, 46, lp);
    k.title = titleL.get();
    k.line = lineL.get();
    k.pill = pillL.get();
    k.refl = reflL.get();
    k.turn = turnL.get();
    const psiO = psiOL.get();
    const sd = reduce ? 0 : shearDL.get();
    k.psiO = psiO;
    k.psiI = psiO - sd;
    k.shear = Math.max(-1, Math.min(1, sd / SHEAR_FULL));
    k.omO = Math.abs(psiO - prevPsi.get()) / Math.max(dt, 0.001) / 60;
    k.omI = Math.abs(psiO - sd - (prevPsi.get() - prevShear.get())) / Math.max(dt, 0.001) / 60;
    prevPsi.set(psiO);
    prevShear.set(sd);
    k.finger = fingerL.get();
    k.dentX = dentXL.get();
    k.dentY = dentYL.get();
    k.dent = dentL.get();
    k.pull = pullL.get();
    k.pullAt = pullAtL.get();
    k.handle = handleL.get();
    k.head = headL.get();
    if (!reduce) {
      for (let i = 0; i < 4; i++) {
        const r = ringAt(now, rings[i * 2], rings[i * 2 + 1]);
        if (r.a <= 0) continue;
        if (i === 0) {
          k.r0 = r.r;
          k.a0 = r.a;
        } else if (i === 1) {
          k.r1 = r.r;
          k.a1 = r.a;
        } else if (i === 2) {
          k.r2 = r.r;
          k.a2 = r.a;
        } else {
          k.r3 = r.r;
          k.a3 = r.a;
        }
      }
      k.pulse = pulseAt(now, strongAt.get());
    }
    k.wash = ambient ? bl * (ref.wash.base + (ref.wash.beat - ref.wash.base) * Math.min(k.pulse, 1)) : 0;
    if (differs(k, prev)) look.set(k);

    // idle: 2 s after the last touch the demo comes back
    if (
      autoPlay &&
      !reduce &&
      !touch &&
      !coasting.get() &&
      now >= snapUntil.get() &&
      now - touchedAt.get() > GESTURE.resumeAfter &&
      touchedAt.get() > -1e8
    ) {
      if (!resuming.get()) {
        rest();
      } else {
        const settled =
          Math.abs(posL.get()) < 0.02 &&
          Math.abs(psiOL.get()) < 0.002 &&
          Math.abs(shearDL.get()) < 0.002 &&
          Math.abs(glyphL.get()) < 0.01 &&
          Math.abs(bloomL.get()) < 0.01 &&
          Math.abs(dentL.get()) < 0.05;
        if (settled) {
          resuming.set(false);
          touchedAt.set(-1e9);
          loopT.set(0);
          auto.set(true);
        }
      }
    }
  });

  useEffect(() => {
    frames.setActive(true);
  }, [frames]);

  // controlled props: the host flips them, the control follows from where it is
  useEffect(() => {
    if (playingProp === undefined || playingL.get() === playingProp) return;
    takeOver();
    setPlaying(playingProp, false);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [playingProp]);
  useEffect(() => {
    if (indexProp === undefined || indexProp === indexL.get()) return;
    takeOver();
    const dir = indexProp > indexL.get() ? 1 : -1;
    indexL.set(Math.min(Math.max(indexProp, 0), N - 1));
    slideL.set(slideL.get() + dir * GESTURE.slideSpan);
    slideL.set(withSpring(0, glide));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [indexProp]);

  // ---- what the canvas reads ----
  const uniforms = useDerivedValue(() => {
    const k = look.get();
    const head = -Math.PI / 2 + (k.pos / D) * 2 * Math.PI;
    return {
      uC: [CX, CY],
      uS: S,
      uPx: PIX,
      uAng: [k.psiO, k.psiI, k.omO, k.omI],
      uShear: [k.shear, k.finger, k.turn, k.turn],
      uRingA: [k.r0, k.a0, k.r1, k.a1],
      uRingB: [k.r2, k.a2, k.r3, k.a3],
      uDent: [k.dentX, k.dentY, k.dent, 0],
      uLamp: [head, k.reach, k.lamp, 0],
      uAccent: accentRgb,
      uFibre: [fibre, k.pullAt, k.pull, 1],
      uP3: P3,
      uN: nStrands,
    };
  });
  const washOp = useDerivedValue(() => look.get().wash);
  const designT = [{ translateX: OX }, { translateY: OY }, { scale: S }];
  const domeT = [{ translateX: 201 }, { translateY: L.domeY }];

  // the page's three lines: each crossfades with a 4 pt rise
  const headOp = useDerivedValue(() => 1 - smooth(0, 0.5, look.get().title));
  const headT = useDerivedValue(() => [{ translateY: -4 * look.get().title }]);
  const titleOp = useDerivedValue(() => smooth(0.5, 1, look.get().title));
  const titleT = useDerivedValue(() => [
    { translateX: 201 },
    { translateY: ref.title.y },
    { scaleX: 1 + 0.006 * Math.min(look.get().pulse, 1) },
    { translateY: -ref.title.y },
    { translateX: -201 },
    { translateY: 4 * (1 - look.get().title) },
  ]);
  const titleInk = useDerivedValue(() => {
    const k = look.get();
    const a = ref.wash.title * clamp01(k.wash / ref.wash.base) * (ambient ? 1 : 0);
    const c = (i: number) => Math.round(mix(inkRgb[i], accentRgb[i], a) * 255);
    return [th.ink, `rgb(${c(0)}, ${c(1)}, ${c(2)})`];
  });
  const lineRestOp = useDerivedValue(() => 1 - smooth(0, 0.5, look.get().line));
  const lineRestT = useDerivedValue(() => [{ translateY: -4 * look.get().line }]);
  const linePlayOp = useDerivedValue(() => smooth(0.5, 1, look.get().line));
  const linePlayT = useDerivedValue(() => [{ translateY: 4 * (1 - look.get().line) }]);
  const pillRestOp = useDerivedValue(() => 1 - smooth(0, 0.5, look.get().pill));
  const pillPlayOp = useDerivedValue(() => smooth(0.5, 1, look.get().pill));
  const pillT = useDerivedValue(() => {
    const s = pillPress.get();
    return [
      { translateX: 201 },
      { translateY: ref.pill.y + ref.pill.h / 2 },
      { scale: s },
      { translateX: -201 },
      { translateY: -(ref.pill.y + ref.pill.h / 2) },
    ];
  });
  const rise = (v: number) => {
    'worklet';
    return 4 * (1 - v);
  };
  const pillRestT = useDerivedValue(() => [{ translateY: -4 * look.get().pill }]);
  const pillPlayT = useDerivedValue(() => [{ translateY: rise(look.get().pill) }]);

  // ---- the finger ----
  const rel = (x: number, y: number) => {
    'worklet';
    return { x: x / S - HIT_BOX / 2, y: y / S - HIT_BOX / 2 };
  };
  const tapPlay = (byTouch: boolean) => {
    'worklet';
    takeOver();
    setPlaying(!playingL.get(), byTouch);
  };
  const dome = Gesture.Pan()
    .minDistance(0)
    .onBegin((e) => {
      'worklet';
      const now = clock.get();
      const p = rel(e.x, e.y);
      const r = Math.hypot(p.x, p.y);
      const region = r < ANNULUS.inner ? 2 : r <= ANNULUS.outer ? 1 : 3;
      let sub = 0;
      if (region === 2) {
        if (Math.abs(p.y) < 34 && Math.abs(p.x) < 34) sub = 1;
        else if (Math.abs(p.y) < 22 && Math.abs(p.x + ref.disc.dx) < 22) sub = 2;
        else if (Math.abs(p.y) < 22 && Math.abs(p.x - ref.disc.dx) < 22) sub = 3;
      }
      const a = Math.atan2(p.y, p.x);
      grip.set({ ...NO_GRIP, region, sub, a0: a, prev: a, vAt: now, tickAt: now, tickN: Math.floor(posL.get() / GESTURE.tickEvery) });
      if (region === 3) return;
      takeOver();
      touching.set(true);
      touchedAt.set(now);
      coasting.set(false);
      if (region === 1) {
        xL.set(posL.get());
        const rr = Math.min(r, ref.dome.r - 6);
        const kx = r > 0.001 ? rr / r : 1;
        dentXL.set(p.x * kx);
        dentYL.set(p.y * kx);
        fingerL.set(a);
        dentL.set(withTiming(DENT, { duration: 80, easing: Easing.bezier(0.2, 0.8, 0.2, 1) }));
        scheduleOnRN(cue.onset);
        scheduleOnRN(emitPress, true);
      } else if (sub === 1) {
        pressL.set(withSpring(0.9, snap));
      }
    })
    .onUpdate((e) => {
      'worklet';
      const g0 = grip.get();
      if (g0.region !== 1) return;
      const now = clock.get();
      const g = { ...g0 };
      const p = rel(e.x, e.y);
      const r = Math.hypot(p.x, p.y);
      const a = Math.atan2(p.y, p.x);
      const dA = wrapPi(a - g.prev);
      g.prev = a;
      g.acc += dA;
      const rr = Math.min(Math.max(r, ANNULUS.inner), ref.dome.r - 6);
      const kx = r > 0.001 ? rr / r : 1;
      dentXL.set(p.x * kx);
      dentYL.set(p.y * kx);
      fingerL.set(a);
      const dtv = Math.max(now - g.vAt, 0.004);
      g.v = g.v * 0.6 + 0.4 * (dA / dtv);
      g.vAt = now;
      touchedAt.set(now);
      if (!g.turning && Math.hypot(e.translationX, e.translationY) > GESTURE.slop * S) {
        g.turning = true;
        turnL.set(withSpring(1, SPRING.turn));
        const hit = turnBy(g.acc, true);
        g.atLimit = hit;
      } else if (g.turning) {
        const hit = turnBy(dA, true);
        if (hit && !g.atLimit) {
          scheduleOnRN(cue.limit);
          headL.set(withSequence(withTiming(1, { duration: 120 }), withTiming(0, { duration: 260 })));
        }
        g.atLimit = hit;
        const n = Math.floor(posL.get() / GESTURE.tickEvery);
        if (n !== g.tickN && now - g.tickAt >= GESTURE.tickGap) {
          g.tickAt = now;
          scheduleOnRN(cue.tick);
        }
        g.tickN = n;
        if (now - seekAt.get() >= 0.1) {
          seekAt.set(now);
          scheduleOnRN(emitSeek, posL.get());
        }
      }
      grip.set(g);
    })
    .onFinalize((e, success) => {
      'worklet';
      const g = grip.get();
      if (g.region === 0 || g.region === 3) {
        grip.set(NO_GRIP);
        return;
      }
      const now = clock.get();
      if (g.region === 1) {
        // a turn carries the finger's speed on (the last 80 ms); a press only lets the fabric spring back
        release(g.turning, now - g.vAt < GESTURE.vTail * 3 ? g.v : 0);
      } else {
        touching.set(false);
        touchedAt.set(now);
        pressL.set(withSpring(1, snap));
        const moved = Math.hypot(e.translationX, e.translationY) / S;
        if (success && moved < GESTURE.slop) {
          if (g.sub === 1) tapPlay(true);
          else if (g.sub === 2) commitTrack(-1, false);
          else if (g.sub === 3) commitTrack(1, false);
        }
      }
      grip.set(NO_GRIP);
    });

  // the headline is the queue's handle: swipe it (while playing) to change track
  const titleSwipe = Gesture.Pan()
    .activeOffsetX([-8, 8])
    .failOffsetY([-18, 18])
    .onBegin(() => {
      'worklet';
      takeOver();
      touching.set(true);
      touchedAt.set(clock.get());
    })
    .onUpdate((e) => {
      'worklet';
      if (!playingL.get()) return;
      const idx = indexL.get();
      let s = e.translationX;
      if ((idx === 0 && s > 0) || (idx === N - 1 && s < 0)) s *= GESTURE.titleRubber;
      slideL.set(s);
    })
    .onFinalize((e) => {
      'worklet';
      touching.set(false);
      touchedAt.set(clock.get());
      if (!playingL.get()) return;
      const s = slideL.get();
      const vx = e.velocityX;
      if (s < -GESTURE.swipe || vx < -GESTURE.swipeVelocity) commitTrack(1, true);
      else if (s > GESTURE.swipe || vx > GESTURE.swipeVelocity) commitTrack(-1, true);
      else slideL.set(withSpring(0, glide));
    });

  const pillTap = Gesture.Tap()
    .maxDuration(600)
    .onBegin(() => {
      'worklet';
      pillPress.set(withSpring(0.97, snap));
    })
    .onFinalize((_e, success) => {
      'worklet';
      pillPress.set(withSpring(1, snap));
      if (success && playingL.get()) scheduleOnRN(emitOpen);
    });

  // ---- accessibility ----
  const seekBy = (sec: number) => {
    takeOver();
    const target = Math.min(Math.max(Math.round(posL.get() + sec), 0), D);
    xL.set(target);
    posL.set(withTiming(target, { duration: 200 }));
    emitCommit(target);
  };
  const onA11yDome = (ev: AccessibilityActionEvent) => {
    if (ev.nativeEvent.actionName === 'increment') seekBy(5);
    else if (ev.nativeEvent.actionName === 'decrement') seekBy(-5);
  };
  const onA11yPlay = () => {
    takeOver();
    setPlaying(!playingL.get(), false);
  };
  const onA11yTrack = (dir: number) => {
    takeOver();
    commitTrack(dir, false);
  };
  const words = (s: number) => {
    const m = Math.floor(s / 60);
    const r = s % 60;
    return `${m} ${m === 1 ? 'minute' : 'minutes'} ${r} ${r === 1 ? 'second' : 'seconds'}`;
  };

  // ---- touch layers (screen pt) ----
  const hitStyle = useMemo(
    () => ({
      position: 'absolute' as const,
      left: CX - (HIT_BOX / 2) * S,
      top: CY - (HIT_BOX / 2) * S,
      width: HIT_BOX * S,
      height: HIT_BOX * S,
    }),
    [CX, CY, S],
  );
  const centreStyle = useMemo(
    () => ({ position: 'absolute' as const, left: CX - 28 * S, top: CY - 28 * S, width: 56 * S, height: 56 * S }),
    [CX, CY, S],
  );
  const discStyle = (d: number) => ({
    position: 'absolute' as const,
    left: CX + d * ref.disc.dx * S - 22 * S,
    top: CY - 22 * S,
    width: 44 * S,
    height: 44 * S,
  });
  const titleStyle = useMemo(
    () => ({ position: 'absolute' as const, left: L.x(-190), top: L.y(ref.title.y) - 28 * S, width: 380 * S, height: 88 * S }),
    [L, S],
  );
  const pillStyle = useMemo(
    () => ({
      position: 'absolute' as const,
      left: L.x(-ref.pill.w / 2) - 4,
      top: L.y(ref.pill.y) - 4 * S,
      width: ref.pill.w * S + 8,
      height: ref.pill.h * S + 8 * S,
    }),
    [L, S],
  );

  const ctx: DisplayCtx = useMemo(
    () => ({
      look,
      indexL,
      slideL,
      queue,
      runs: text ? text.display : null,
      tokens: th,
      accent: accentHex,
      accentTail,
      duration: D,
      reduce,
    }),
    [look, indexL, slideL, queue, text, th, accentHex, accentTail, D, reduce],
  );

  const page = text ? text.page : null;
  const titleY = ref.title.y + 0.364 * ref.title.size;
  const lineY = ref.line.y + 0.364 * ref.line.size;
  const pillY = ref.pill.y + ref.pill.h / 2 + 0.364 * ref.pill.size;
  const centred = (r: Run) => 201 - r.width / 2;

  return (
    <View style={StyleSheet.absoluteFill} pointerEvents="box-none">
      <View style={StyleSheet.absoluteFill} pointerEvents="none">
        <Canvas style={StyleSheet.absoluteFill} accessible={false}>
          <Fill color={th.page} />
          {/* the lamp's wash: the display's red reaches the page while it plays (5 %, breathing to 8 % on the accented beats) */}
          <Group opacity={washOp}>
            <Fill>
              <RadialGradient c={vec(CX, CY)} r={ref.wash.r * S} colors={[accentHex, `${accentHex}00`]} />
            </Fill>
          </Group>
          {weaveFx ? (
            <Rect x={CX - (DOME_BOX / 2) * S} y={CY - (DOME_BOX / 2) * S} width={DOME_BOX * S} height={DOME_BOX * S}>
              <Shader source={weaveFx} uniforms={uniforms} />
            </Rect>
          ) : null}
          <DisplayContext.Provider value={ctx}>
            <Group transform={designT}>
              <Group transform={domeT}>
                <DisplayBase />
                <Group clip={GLASS_CLIP}>{children ?? <NowPlaying />}</Group>
              </Group>
              {page ? (
                <>
                  {/* the page's lines (design pt on the 402 × 874 canvas) */}
                  <Group transform={headT} opacity={headOp}>
                    <Glyphs font={page.headline.font} x={centred(page.headline)} y={titleY} glyphs={page.headline.glyphs} color={th.ink} />
                  </Group>
                  <Group transform={titleT}>
                    {page.titles.map((r, i) => (
                      <TrackTitle key={`p${i}`} i={i} run={r} y={titleY} indexL={indexL} slideL={slideL} colors={titleInk} fade={titleOp} />
                    ))}
                  </Group>
                  <Group transform={lineRestT} opacity={lineRestOp}>
                    <Glyphs font={page.line.font} x={centred(page.line)} y={lineY} glyphs={page.line.glyphs} color={th.mute} />
                  </Group>
                  <Group transform={linePlayT} opacity={linePlayOp}>
                    <Glyphs
                      font={page.linePlaying.font}
                      x={centred(page.linePlaying)}
                      y={lineY}
                      glyphs={page.linePlaying.glyphs}
                      color={th.mute}
                    />
                  </Group>
                  <Group transform={pillT}>
                    <RoundedRect
                      x={201 - ref.pill.w / 2}
                      y={ref.pill.y}
                      width={ref.pill.w}
                      height={ref.pill.h}
                      r={ref.pill.h / 2}
                      style="stroke"
                      strokeWidth={ref.pill.stroke}
                      color={C.pill}
                    />
                    <Group transform={pillRestT} opacity={pillRestOp}>
                      <Glyphs font={page.pill.font} x={centred(page.pill)} y={pillY} glyphs={page.pill.glyphs} color={C.pill} />
                    </Group>
                    <Group transform={pillPlayT} opacity={pillPlayOp}>
                      <Glyphs
                        font={page.pillPlaying.font}
                        x={centred(page.pillPlaying)}
                        y={pillY}
                        glyphs={page.pillPlaying.glyphs}
                        color={C.pill}
                      />
                    </Group>
                  </Group>
                </>
              ) : null}
            </Group>
          </DisplayContext.Provider>
        </Canvas>
      </View>

      {/* the dome and the display take the finger: the mesh turns, the cloth dents, the centre and the two buttons tap */}
      <GestureDetector gesture={dome}>
        <View style={hitStyle} collapsable={false} />
      </GestureDetector>
      <View
        pointerEvents="none"
        accessible
        accessibilityRole="adjustable"
        accessibilityLabel={accessibilityLabel ?? COPY.position}
        accessibilityHint={COPY.hintDome}
        accessibilityValue={{ text: `${words(a11y.pos)} of ${words(Math.round(D))}` }}
        accessibilityActions={[{ name: 'increment' }, { name: 'decrement' }]}
        onAccessibilityAction={onA11yDome}
        style={[hitStyle, styles.annulusA11y]}
      />
      <View
        pointerEvents="none"
        accessible
        accessibilityRole="button"
        accessibilityLabel={a11y.playing ? COPY.pause : COPY.play}
        accessibilityActions={[{ name: 'activate' }]}
        onAccessibilityAction={onA11yPlay}
        style={centreStyle}
      />
      <View
        pointerEvents="none"
        accessible
        accessibilityRole="button"
        accessibilityLabel={COPY.previous}
        accessibilityActions={[{ name: 'activate' }]}
        onAccessibilityAction={() => onA11yTrack(-1)}
        style={discStyle(-1)}
      />
      <View
        pointerEvents="none"
        accessible
        accessibilityRole="button"
        accessibilityLabel={COPY.next}
        accessibilityActions={[{ name: 'activate' }]}
        onAccessibilityAction={() => onA11yTrack(1)}
        style={discStyle(1)}
      />
      <GestureDetector gesture={titleSwipe}>
        <View
          collapsable={false}
          style={titleStyle}
          accessible
          accessibilityRole="header"
          accessibilityLabel={a11y.playing ? (queue[a11y.index]?.title ?? queue[0].title) : COPY.headline}
          accessibilityHint={a11y.playing ? COPY.hintTitle : undefined}
        />
      </GestureDetector>
      <GestureDetector gesture={pillTap}>
        <View
          collapsable={false}
          style={pillStyle}
          accessible
          accessibilityRole="button"
          accessibilityLabel={a11y.playing ? COPY.pillPlaying : COPY.pill}
          accessibilityActions={[{ name: 'activate' }]}
          onAccessibilityAction={() => a11y.playing && onOpenQueue?.()}
        />
      </GestureDetector>
    </View>
  );
}

/** One track's headline: it slides with the finger (1:1, the neighbours at ±140 pt) and fades with the distance. */
function TrackTitle({
  i,
  run,
  y,
  indexL,
  slideL,
  colors,
  fade,
}: {
  i: number;
  run: Run;
  y: number;
  indexL: SharedValue<number>;
  slideL: SharedValue<number>;
  colors: SharedValue<string[]>;
  fade: SharedValue<number>;
}) {
  const dist = useDerivedValue(() => (i - indexL.get()) * GESTURE.slideSpan * 2 + slideL.get());
  const t = useDerivedValue(() => [{ translateX: dist.get() }]);
  const op = useDerivedValue(() => clamp01(1 - Math.abs(dist.get()) / (GESTURE.slideSpan * 1.6)) * fade.get());
  return (
    <Group transform={t} opacity={op}>
      <Glyphs font={run.font} x={201 - run.width / 2} y={y} glyphs={run.glyphs}>
        <LinearGradient start={vec(0, ref.title.y - 14)} end={vec(0, ref.title.y + 22)} colors={colors} />
      </Glyphs>
    </Group>
  );
}

const styles = StyleSheet.create({
  annulusA11y: { backgroundColor: 'transparent' },
});
