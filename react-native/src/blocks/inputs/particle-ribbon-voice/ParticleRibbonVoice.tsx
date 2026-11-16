// Particle Ribbon Voice — a voice assistant's resting screen: two thin lines of type and one ribbon of light, a sheet of
// particles that twists along its length. Edge-on stretches are knife-thin white lines, face-on stretches open into bands
// of sparks, so the pin's "line here, dust there" is the sheet's projection, not an animation. The user's voice sends
// twists from the left, the assistant's answer sends them back from the right, thinking winds the sheet into a thread,
// and a finger can part the ribbon where it touches it. After a Dribbble shot by AmazingUI (the look, the colours and
// the type are measured from it); the sheet, the voice states, the parting and the captions are ours.
//
// Layers: one Skia runtime effect over a strip of the screen (prv-shaders.ts: stratified particles found by inverting
// their quantile function, so a pixel never loops over the cloud), React Native text above it, one gesture surface.
// Reduce Motion: no loop, no twists; the ribbon is a still band whose width follows the level, thinking is a slow pulse,
// which end is brighter says who is speaking.
import { useFonts } from 'expo-font';
import { StatusBar } from 'expo-status-bar';
import { type ReactNode, useCallback, useEffect, useMemo, useState } from 'react';
import {
  AccessibilityInfo,
  type AccessibilityActionEvent,
  type LayoutChangeEvent,
  Platform,
  StyleSheet,
  Text,
  useWindowDimensions,
  View,
} from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  useAnimatedReaction,
  useAnimatedStyle,
  useDerivedValue,
  useFrameCallback,
  useSharedValue,
  withTiming,
  type SharedValue,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { Canvas, Fill, LinearGradient, Rect, Shader, Skia, vec } from '@shopify/react-native-skia';
import Svg, { Path } from 'react-native-svg';
import { scheduleOnRN } from 'react-native-worklets';

import { useReducedMotionPreference } from '../../../shared/useReducedMotionPreference';
import { cueGate, haptic } from './prv-haptics';
import {
  ASK_LAYERS,
  askAt,
  blendFrame,
  CAPTION_LAYERS,
  MAX_WORDS,
  captionAt,
  frameAt,
  GESTURE,
  liveDown,
  liveFrame,
  liveFromLoop,
  liveMode,
  liveMove,
  liveRest,
  liveStep,
  liveUp,
  lineY,
  LOOP,
  reducedFrame,
  smooth,
  stateAt,
  TINT,
  twistBase,
  type Frame,
  type Live,
} from './prv-motion';
import { RIBBON_SKSL } from './prv-shaders';
import { useRibbonSound } from './prv-sound';
import { bgRgb, COPY, makeLayout, rampRgb, ref, REF } from './prv-theme';
import { ribbonUniforms, rampMix, type Geometry, type Rgb } from './prv-uniforms';

export { LOOP };

const ribbonFx = Skia.RuntimeEffect.Make(RIBBON_SKSL);

export type RibbonState = 'idle' | 'listening' | 'thinking' | 'speaking';
const STATES: readonly RibbonState[] = ['idle', 'listening', 'thinking', 'speaking'];

export type ParticleRibbonProps = {
  /** controlled state; leave out for the uncontrolled demo flow (hold → thinks → answers → idle) */
  state?: RibbonState;
  /** the state to start in when uncontrolled */
  defaultState?: RibbonState;
  onStateChange?: (state: RibbonState) => void;
  /** 0 … 1 from a microphone or a player (UI thread). Left out: the finger's height over the press sets it. */
  level?: SharedValue<number>;
  /** hold the screen to talk (default) or tap it to start and stop */
  mode?: 'holdToTalk' | 'tapToToggle';
  /** true (default): the ribbon can be parted by hand */
  touchable?: boolean;
  /** the two lines; `reply` is shown one word per twist while speaking (up to 8 words, one short line) */
  caption?: { greeting?: string; idle?: string; listening?: string; reply?: readonly string[]; ask?: readonly string[] };
  /** true (default): the lower half shows the request as it is heard and the talk control (a ring, filled while held) */
  control?: boolean;
  /** replaces the built-in lines; receives the state as a number (0 idle, 1 listening, 2 thinking, 3 speaking) */
  renderCaption?: (state: SharedValue<number>) => ReactNode;
  /** the ramp along the ribbon, left to right: the pin's gold, green and cyan */
  colors?: readonly [string, string, string];
  /** the sheet's half width at level 0 and at level 1, in design pt (402 wide): the pin's 26 and 46 */
  width?: readonly [number, number];
  /** the centre line's height as a fraction of the screen (the pin: 0.545) */
  centerY?: number;
  /** pt/s a twist travels (live use; the loop is fixed at 260) */
  twistSpeed?: number;
  /** 'auto' lowers the resolution when frames take over 24 ms for half a second */
  quality?: 'auto' | 'high' | 'low';
  /** 0 … 1.5, the glow round the light (the pin's 1) */
  bloom?: number;
  background?: string;
  /** a font family for both lines (the pin's thin grotesk is bundled) */
  font?: string;
  paused?: boolean;
  /** the touch cues: two soft tones, off by default under autoPlay's silence */
  sound?: boolean;
  onPressIn?: () => void;
  onPressOut?: () => void;
  autoPlay?: boolean;
};

const FONTS = {
  'InterTight-ExtraLight': require('./assets/fonts/InterTight-ExtraLight.ttf'),
  'InterTight-Light': require('./assets/fonts/InterTight-Light.ttf'),
};

const NO_FRAME = frameAt(0);
const TEXT_MARGIN = 16; // the caption block's side margin, pt

function CapLayer({
  cap,
  index,
  rise,
  style,
  onLayout,
  children,
}: {
  cap: SharedValue<number[]>;
  index: number;
  rise: number;
  style?: object;
  onLayout?: (e: LayoutChangeEvent) => void;
  children: ReactNode;
}) {
  const anim = useAnimatedStyle(() => {
    const v = cap.get()[index];
    return { opacity: v, transform: [{ translateY: (1 - v) * rise }] };
  });
  return (
    <Animated.View style={[style, anim]} pointerEvents="none" onLayout={onLayout}>
      {children}
    </Animated.View>
  );
}

const INK = bgRgb(ref.color.ink);

/** A reply word: white, except for 240 ms after it arrives, when it takes the ribbon's colour beneath it at 35 % (Pro layer 3). */
function Word({
  cap,
  index,
  wordX,
  ramp,
  style,
  children,
}: {
  cap: SharedValue<number[]>;
  index: number;
  wordX: SharedValue<number[]>;
  ramp: readonly [Rgb, Rgb, Rgb];
  style: object;
  children: string;
}) {
  const anim = useAnimatedStyle(() => {
    const k = TINT * cap.get()[5 + MAX_WORDS + index];
    const c = rampMix(ramp, wordX.get()[index] / REF.w);
    const r = Math.round(255 * (INK[0] + (c[0] - INK[0]) * k));
    const g = Math.round(255 * (INK[1] + (c[1] - INK[1]) * k));
    const b = Math.round(255 * (INK[2] + (c[2] - INK[2]) * k));
    return { color: `rgb(${r}, ${g}, ${b})` };
  });
  return (
    <Animated.Text style={[style, anim]} maxFontSizeMultiplier={1.5}>
      {children}
    </Animated.Text>
  );
}

function Dot({ cap, index, size }: { cap: SharedValue<number[]>; index: number; size: number }) {
  const anim = useAnimatedStyle(() => ({ opacity: cap.get()[2 + index] }));
  return <Animated.View style={[{ width: size, height: size, borderRadius: size / 2, backgroundColor: ref.color.ink }, anim]} />;
}

/** A word of the request: rises 4 pt into place as it is heard. */
function AskWord({ ask, index, style, children }: { ask: SharedValue<number[]>; index: number; style: object; children: string }) {
  const anim = useAnimatedStyle(() => {
    const v = ask.get()[index];
    return { opacity: v, transform: [{ translateY: (1 - v) * 4 }] };
  });
  return (
    <Animated.Text style={[style, anim]} maxFontSizeMultiplier={1.3}>
      {children}
    </Animated.Text>
  );
}

const MIC_PATH = 'M12 3.2a3 3 0 0 0-3 3v5.6a3 3 0 0 0 6 0V6.2a3 3 0 0 0-3-3Z M5.8 11.2a6.2 6.2 0 0 0 12.4 0 M12 17.4v3.4';

/** The talk control: a hairline ring with a microphone; held, it fills with the ink and a soft halo breathes round it. */
function TalkControl({
  ask,
  clock,
  d,
  hintStyle,
  hold,
  release,
}: {
  ask: SharedValue<number[]>;
  clock: SharedValue<number>;
  d: number;
  hintStyle: object;
  hold: string;
  release: string;
}) {
  const press = () => {
    'worklet';
    return ask.get()[MAX_WORDS];
  };
  const body = useAnimatedStyle(() => ({ transform: [{ scale: 1 - 0.05 * press() }] }));
  const fill = useAnimatedStyle(() => ({ opacity: press() }));
  const halo = useAnimatedStyle(() => {
    const v = press();
    return { opacity: 0.45 * v, transform: [{ scale: 1.16 + 0.05 * Math.sin((2 * Math.PI * clock.get()) / 1.1) * v }] };
  });
  const light = useAnimatedStyle(() => ({ opacity: 1 - press() }));
  const holdA = useAnimatedStyle(() => ({ opacity: 1 - press() }));
  const releaseA = useAnimatedStyle(() => ({ opacity: press() }));
  const ringStyle = { position: 'absolute' as const, width: d, height: d, borderRadius: d / 2 };
  const glyph = (color: string) => (
    <Svg width={d * 0.4} height={d * 0.4} viewBox="0 0 24 24">
      <Path d={MIC_PATH} stroke={color} strokeWidth={1.4} strokeLinecap="round" strokeLinejoin="round" fill="none" />
    </Svg>
  );
  return (
    <View style={{ alignItems: 'center' }} pointerEvents="none" accessibilityElementsHidden importantForAccessibility="no-hide-descendants">
      <Animated.View style={[{ width: d, height: d, alignItems: 'center', justifyContent: 'center' }, body]}>
        <Animated.View style={[ringStyle, { borderWidth: StyleSheet.hairlineWidth * 2, borderColor: ref.color.ink }, halo]} />
        <View style={[ringStyle, { borderWidth: StyleSheet.hairlineWidth * 2, borderColor: `rgba(253, 253, 254, ${ref.control.ring})` }]} />
        <Animated.View style={[ringStyle, { backgroundColor: ref.color.ink }, fill]} />
        <Animated.View style={[{ position: 'absolute' }, light]}>{glyph(ref.color.ink)}</Animated.View>
        <Animated.View style={[{ position: 'absolute' }, fill]}>{glyph(ref.color.page)}</Animated.View>
      </Animated.View>
      <View style={{ marginTop: ref.control.gap, alignItems: 'center' }}>
        <Animated.Text style={[hintStyle, holdA]} maxFontSizeMultiplier={1.3}>
          {hold}
        </Animated.Text>
        <Animated.Text style={[hintStyle, { position: 'absolute' }, releaseA]} maxFontSizeMultiplier={1.3}>
          {release}
        </Animated.Text>
      </View>
    </View>
  );
}

export function ParticleRibbonVoice({
  state,
  defaultState = 'idle',
  onStateChange,
  level,
  mode = 'holdToTalk',
  touchable = true,
  caption,
  renderCaption,
  colors,
  width,
  centerY,
  twistSpeed = GESTURE.speed,
  quality = 'auto',
  bloom = 1,
  background,
  font,
  paused = false,
  sound = true,
  onPressIn,
  onPressOut,
  autoPlay = false,
  control = true,
}: ParticleRibbonProps) {
  const { width: W, height: H } = useWindowDimensions();
  const insets = useSafeAreaInsets();
  const reduce = useReducedMotionPreference();
  const [fontsLoaded] = useFonts(FONTS);
  const lines = {
    greeting: caption?.greeting ?? COPY.greeting,
    idle: (caption?.idle ?? COPY.idle).replace(/ (\S+)$/, '\u00A0$1'), // the last two words stay together: no widow at large text sizes
    listening: caption?.listening ?? COPY.listening,
    reply: (caption?.reply && caption.reply.length > 0 ? caption.reply : COPY.reply).slice(0, MAX_WORDS),
    ask: (caption?.ask ?? COPY.ask).slice(0, MAX_WORDS),
  };
  const centerPt = centerY === undefined ? ref.ribbon.y0 : centerY * REF.h; // the pin's 476 of 874 = 0.545
  const L = useMemo(() => makeLayout(W, H, insets.top, centerPt), [W, H, insets.top, centerPt]);
  const S = L.S;
  const yc0 = L.yc(0); // screen y of design y 0: the design ↔ screen map is y = yc0 + S·yd
  const demoFlow = state === undefined;
  const controlled = state !== undefined;
  const bgc = useMemo(() => bgRgb(background), [background]);
  const ramp = useMemo(() => rampRgb(colors), [colors]);
  const wdLo = width?.[0] ?? ref.ribbon.wdIdle;
  const wdHi = width?.[1] ?? ref.ribbon.wdIdle + ref.ribbon.wdLevel;
  const [lowTier, setLowTier] = useState(quality === 'low');
  const q = quality === 'high' ? 1 : lowTier ? 0.66 : 1;
  const geom: Geometry = useMemo(
    () => ({
      w: W * q,
      h: L.stripH * q,
      s: S * q,
      oy: ref.ribbon.top,
      p3: Platform.OS === 'ios' ? 1 : 0,
      ramp,
      bg: bgc,
      bloom,
      gain: 1,
      dust: 1,
      width: [wdLo, wdHi] as const,
    }),
    [W, L.stripH, S, q, ramp, bgc, bloom, wdLo, wdHi],
  );

  // ---- state: the loop plays `frame`; a finger or a prop hands it to the live engine ----
  const frame = useSharedValue<Frame>(NO_FRAME);
  const cap = useSharedValue<number[]>(captionAt(0));
  const askSV = useSharedValue<number[]>(askAt(0));
  const live = useSharedValue<Live>(liveRest());
  const loopT = useSharedValue(0);
  const playing = useSharedValue(autoPlay);
  const pinned = useSharedValue(-1);
  const resuming = useSharedValue(-1); // progress of the ease back to the loop, -1 = not easing
  const resumeFrom = useSharedValue<Frame>(NO_FRAME);
  const modeSV = useSharedValue(0);
  const wordsSV = useSharedValue(0);
  const rLevel = useSharedValue(0);
  const clock = useSharedValue(0);
  const slow = useSharedValue(0);
  const frames = useSharedValue(0);
  const crossSV = useSharedValue(0);
  const wordX = useSharedValue<number[]>(new Array(MAX_WORDS).fill(REF.w / 2));
  const tapStart = useSharedValue(-1);
  const tapMoved = useSharedValue(0);

  useEffect(() => {
    playing.set(autoPlay && !reduce && !controlled);
    if (!autoPlay || reduce) {
      frame.set(reduce ? reducedFrame(0, 0, 1) : frameAt(0));
      cap.set(captionAt(0));
    }
  }, [autoPlay, reduce, controlled, playing, frame, cap]);

  // Development only: `__prv.pin(t)` freezes the loop at t s, `__prv.play()` lets it run, `__prv.state()` reads it.
  useEffect(() => {
    if (!__DEV__) return;
    (globalThis as Record<string, unknown>).__prv = {
      pin: (t: number) => pinned.set(t),
      at: (t: number) => pinned.set(t),
      play: () => pinned.set(-1),
      state: () => ({ loopT: loopT.get(), playing: playing.get(), mode: modeSV.get(), words: wordsSV.get(), live: live.get() }),
    };
  }, [pinned, loopT, playing, modeSV, wordsSV, live]);

  // ---- cues (touch only; none within 60 ms of the previous) ----
  const playCue = useRibbonSound(sound);
  const cue = useCallback(
    (name: 'onset' | 'release' | 'part' | 'answer' | 'cross') => {
      if (!cueGate()) return;
      haptic[name]();
      if (name === 'onset') playCue('open');
      if (name === 'release') playCue('close');
    },
    [playCue],
  );
  const press = useCallback(
    (down: boolean) => {
      if (down) onPressIn?.();
      else onPressOut?.();
    },
    [onPressIn, onPressOut],
  );
  const [a11yState, setA11yState] = useState<number>(0);
  const modeChanged = useCallback(
    (m: number) => {
      setA11yState(m);
      onStateChange?.(STATES[m]);
      if (m > 0) AccessibilityInfo.announceForAccessibility(COPY.states[STATES[m]]);
    },
    [onStateChange],
  );
  useAnimatedReaction(
    () => modeSV.get(),
    (m, prev) => {
      if (prev !== null && m !== prev) scheduleOnRN(modeChanged, m);
    },
  );
  useAnimatedReaction(
    () => crossSV.get(),
    (n, prev) => {
      if (prev !== null && n > prev) scheduleOnRN(cue, 'cross');
    },
  );
  useAnimatedReaction(
    () => wordsSV.get(),
    (w, prev) => {
      if (prev !== null && w === 1 && prev === 0 && !playing.get() && !reduce) scheduleOnRN(cue, 'answer');
    },
  );

  // a controlled `state` drives the engine's mode
  useEffect(() => {
    const idx = STATES.indexOf(state ?? defaultState);
    if (state === undefined && idx === 0) return;
    playing.set(false);
    live.modify((s) => {
      'worklet';
      liveMode(s, idx as 0 | 1 | 2 | 3);
      return s;
    });
  }, [state, defaultState, live, playing]);

  // ---- the clock: one place moves everything ----
  const fcb = useFrameCallback((fi) => {
    'worklet';
    const dt = Math.min((fi.timeSincePreviousFrame ?? 16.7) / 1000, 0.05);
    clock.set(clock.get() + dt);
    const pin = pinned.get();
    if (pin >= 0) {
      frame.set(frameAt(pin));
      cap.set(captionAt(pin));
      askSV.set(askAt(pin));
      modeSV.set(stateAt(pin));
      return;
    }
    // quality: half a second of slow frames lowers the resolution once
    if (quality === 'auto') {
      slow.set(dt > 0.024 ? slow.get() + 1 : 0);
      if (slow.get() === 30) scheduleOnRN(setLowTier, true);
    }
    if (playing.get() && !reduce) {
      const t = (loopT.get() + dt) % LOOP;
      loopT.set(t);
      frame.set(frameAt(t));
      cap.set(captionAt(t));
      askSV.set(askAt(t));
      const m = stateAt(t);
      if (modeSV.get() !== m) modeSV.set(m);
      return;
    }
    // the live engine
    const ext = level ? level.get() : -1;
    const tr = resuming.get();
    live.modify((s) => {
      'worklet';
      liveStep(s, dt, demoFlow, ext, twistSpeed);
      return s;
    });
    const s = live.get();
    // live: no transcript (the words are the host's), the control follows the finger or the listening state
    const prevAsk = askSV.get();
    const nextAsk = new Array<number>(ASK_LAYERS).fill(0);
    const held = s.talk > 0 || s.mode === 1 ? 1 : 0;
    const ease = 1 - Math.exp(-dt / 0.06);
    for (let i = 0; i < MAX_WORDS; i++) nextAsk[i] = prevAsk[i] * (1 - ease);
    nextAsk[MAX_WORDS] = prevAsk[MAX_WORDS] + (held - prevAsk[MAX_WORDS]) * ease;
    askSV.set(nextAsk);
    if (modeSV.get() !== s.mode) modeSV.set(s.mode);
    if (wordsSV.get() !== s.words) wordsSV.set(s.words);
    if (crossSV.get() !== s.cross) crossSV.set(s.cross);
    // under Reduce Motion: a still band, the level eased over 200 ms, no twists
    if (reduce) {
      rLevel.set(rLevel.get() + (s.level - rLevel.get()) * (1 - Math.exp(-dt / 0.07)));
      frame.set(reducedFrame(s.mode, rLevel.get(), 0.5 + 0.5 * Math.sin((2 * Math.PI * clock.get()) / 2.4)));
      cap.set(s.cap.slice());
      return;
    }
    // idle for 2 s after the last touch: ease back to the loop's rest, then play it again
    if (autoPlay && !controlled && s.mode === 0 && s.quiet >= GESTURE.resumeAfter && s.talk === 0) {
      if (tr < 0) {
        resumeFrom.set(liveFrame(s, null));
        resuming.set(0);
      } else {
        const next = tr + dt / GESTURE.resumeEase;
        if (next >= 1) {
          resuming.set(-1);
          loopT.set(0);
          playing.set(true);
          return;
        }
        resuming.set(next);
        const k = smooth(0, 1, next);
        frame.set(blendFrame(resumeFrom.get(), frameAt(0), k));
        const c0 = captionAt(0);
        const sc = s.cap;
        const cc = c0.slice();
        for (let i = 0; i < CAPTION_LAYERS; i++) cc[i] = sc[i] + (c0[i] - sc[i]) * k;
        cap.set(cc);
        return;
      }
    } else if (tr >= 0) {
      resuming.set(-1);
    }
    frames.set(frames.get() + 1);
    const asleep = s.mode === 0 && s.quiet > 4 && s.talk === 0 && !controlled && Math.abs(s.level) < 0.01;
    if (asleep && frames.get() % 2 === 1) return; // idle for 4 s: 30 fps
    frame.set(liveFrame(s, null));
    cap.set(s.cap.slice());
  }, true);
  useEffect(() => {
    fcb.setActive(!paused);
  }, [paused, fcb]);

  // ---- what the canvas reads ----
  const uniforms = useDerivedValue(() => ribbonUniforms(frame.get(), geom));

  // ---- the fingers (design pt in, the engine out) ----
  const takeOver = () => {
    'worklet';
    resuming.set(-1);
    if (playing.get()) {
      playing.set(false);
      live.modify((s) => {
        'worklet';
        liveFromLoop(s, loopT.get());
        return s;
      });
    }
  };
  const down = (id: number, x: number, y: number) => {
    'worklet';
    if (pinned.get() >= 0) return;
    takeOver();
    const dx = x / S;
    const dy = (y - yc0) / S;
    const f = frame.get();
    const c = lineY(dx, f);
    const tw = twistBase(dx, f);
    const parts = touchable && !reduce;
    live.modify((s) => {
      'worklet';
      s.res = liveDown(s, id, dx, dy, c, parts, tw);
      return s;
    });
    const kind = live.get().res;
    if (kind === 1) {
      scheduleOnRN(cue, 'onset');
      scheduleOnRN(press, true);
    } else if (kind === 2) scheduleOnRN(cue, 'part');
  };
  const move = (id: number, x: number, y: number) => {
    'worklet';
    const dx = x / S;
    const dy = (y - yc0) / S;
    const c = lineY(dx, frame.get());
    live.modify((s) => {
      'worklet';
      liveMove(s, id, dx, dy, c, 0);
      return s;
    });
  };
  const up = (id: number) => {
    'worklet';
    live.modify((s) => {
      'worklet';
      const wasTalk = s.talk === id + 1 ? 10 : 0;
      s.res = wasTalk + liveUp(s, id, demoFlow);
      return s;
    });
    const r = live.get().res;
    if (r >= 10) scheduleOnRN(press, false);
    if (r === 11) scheduleOnRN(cue, 'release');
  };
  // tap-to-toggle: start listening, or stop it
  const toggleListening = () => {
    'worklet';
    takeOver();
    const on = live.get().mode === 1;
    live.modify((s) => {
      'worklet';
      if (on) {
        liveMode(s, demoFlow ? 2 : 0);
      } else {
        s.talkLevel = 0.6;
        liveMode(s, 1);
      }
      return s;
    });
    scheduleOnRN(cue, on ? 'release' : 'onset');
    scheduleOnRN(press, !on);
  };
  const gesture = Gesture.Manual()
    .onTouchesDown((e) => {
      'worklet';
      for (let i = 0; i < e.changedTouches.length; i++) {
        const t = e.changedTouches[i];
        if (mode === 'tapToToggle') {
          tapStart.set(clock.get());
          tapMoved.set(0);
        } else down(t.id, t.x, t.y);
      }
    })
    .onTouchesMove((e) => {
      'worklet';
      for (let i = 0; i < e.changedTouches.length; i++) {
        const t = e.changedTouches[i];
        if (mode === 'tapToToggle') tapMoved.set(1);
        else move(t.id, t.x, t.y);
      }
    })
    .onTouchesUp((e) => {
      'worklet';
      for (let i = 0; i < e.changedTouches.length; i++) {
        const t = e.changedTouches[i];
        if (mode === 'tapToToggle') {
          if (tapMoved.get() === 0 && clock.get() - tapStart.get() < 0.35) toggleListening();
        } else up(t.id);
      }
    })
    .onTouchesCancelled((e) => {
      'worklet';
      if (mode === 'tapToToggle') return;
      for (let i = 0; i < e.changedTouches.length; i++) up(e.changedTouches[i].id);
    });

  // VoiceOver: one button; the action toggles listening
  const onA11y = (ev: AccessibilityActionEvent) => {
    if (ev.nativeEvent.actionName === 'activate' || ev.nativeEvent.actionName === 'start' || ev.nativeEvent.actionName === 'stop') {
      runA11yToggle();
    }
  };
  const runA11yToggle = () => {
    playing.set(false);
    resuming.set(-1);
    live.modify((s) => {
      'worklet';
      if (s.mode === 1) liveMode(s, demoFlow ? 2 : 0);
      else {
        s.talkLevel = 0.6;
        s.talk = 0;
        liveMode(s, 1);
      }
      return s;
    });
  };

  // ---- text ----
  const fs = ref.type.body.size * S;
  const lh = ref.type.body.lineHeight * S;
  const fam = font ?? undefined;
  const greetingStyle = {
    fontFamily: fam ?? ref.type.greeting.family,
    fontSize: ref.type.greeting.size * S,
    lineHeight: ref.type.greeting.lineHeight * S,
    letterSpacing: ref.type.greeting.tracking * ref.type.greeting.size * S,
    color: ref.color.greeting,
    textAlign: 'center' as const,
  };
  const bodyStyle = {
    fontFamily: fam ?? ref.type.body.family,
    fontSize: fs,
    lineHeight: lh,
    letterSpacing: ref.type.body.tracking * fs,
    color: ref.color.ink,
    textAlign: 'center' as const,
  };
  const askStyle = {
    fontFamily: fam ?? ref.type.body.family,
    fontSize: ref.ask.size * S,
    lineHeight: ref.ask.lineHeight * S,
    letterSpacing: ref.ask.tracking * ref.ask.size * S,
    color: ref.color.greeting,
  };
  const hintStyle = {
    fontFamily: fam ?? ref.type.body.family,
    fontSize: ref.control.hint * S,
    lineHeight: ref.control.hint * 1.3 * S,
    color: ref.color.greeting,
    textAlign: 'center' as const,
  };
  const ready = fontsLoaded || font !== undefined;
  const showText = useSharedValue(0);
  useEffect(() => {
    if (ready) showText.set(withTiming(1, { duration: reduce ? 0 : 320 }));
  }, [ready, showText, reduce]);
  const textShow = useAnimatedStyle(() => ({ opacity: showText.get() }));
  const stateIdx = useDerivedValue(() => modeSV.get());
  // where each reply word sits (design pt), so a new word can take the ribbon's colour beneath it
  const onWordLayout = useCallback(
    (i: number, e: LayoutChangeEvent) => {
      const { x, width: w } = e.nativeEvent.layout;
      const next = wordX.get().slice();
      next[i] = (TEXT_MARGIN + x + w / 2) / S;
      wordX.set(next);
    },
    [S, wordX],
  );

  const a11yLabel = a11yState === 0 ? `${lines.greeting} ${lines.idle}` : COPY.states[STATES[a11yState]];
  const fallback = !ribbonFx;

  return (
    <View style={[styles.screen, { backgroundColor: background ?? ref.color.page }]}>
      <StatusBar style="light" />
      <GestureDetector gesture={gesture}>
        <View style={StyleSheet.absoluteFill}>
          <View pointerEvents="none" style={{ position: 'absolute', left: 0, top: L.stripTop, width: W, height: L.stripH }}>
            <Canvas
              style={{ width: W * q, height: L.stripH * q, transformOrigin: 'top left', transform: [{ scale: 1 / q }] }}
              accessible={false}
            >
              {fallback ? (
                <Rect x={0} y={(L.stripH * q) / 2 - 1} width={W * q} height={2}>
                  <LinearGradient start={vec(0, 0)} end={vec(W * q, 0)} colors={[ref.color.gold, ref.color.green, ref.color.cyan]} />
                </Rect>
              ) : (
                <Fill>
                  <Shader source={ribbonFx} uniforms={uniforms} />
                </Fill>
              )}
            </Canvas>
          </View>
          <Animated.View pointerEvents="none" style={[styles.textBlock, { bottom: H - L.textBottom }, textShow]}>
            {renderCaption ? (
              renderCaption(stateIdx)
            ) : (
              <>
                <Text style={greetingStyle} maxFontSizeMultiplier={1.5} accessibilityElementsHidden importantForAccessibility="no">
                  {lines.greeting}
                </Text>
                <View style={{ minHeight: lh, alignItems: 'center', justifyContent: 'flex-start' }}>
                  <CapLayer cap={cap} index={0} rise={6 * S}>
                    <Text style={bodyStyle} maxFontSizeMultiplier={1.5}>
                      {lines.idle}
                    </Text>
                  </CapLayer>
                  <CapLayer cap={cap} index={1} rise={6 * S} style={styles.layer}>
                    <Text style={bodyStyle} maxFontSizeMultiplier={1.5}>
                      {lines.listening}
                    </Text>
                  </CapLayer>
                  <View style={[styles.layer, { height: lh, justifyContent: 'center' }]} pointerEvents="none">
                    <View style={{ flexDirection: 'row', justifyContent: 'center', columnGap: 12.8 * S, marginTop: 2 * S }}>
                      {[0, 1, 2].map((i) => (
                        <Dot key={i} cap={cap} index={i} size={6.4 * S} />
                      ))}
                    </View>
                  </View>
                  <View style={[styles.layer, { flexDirection: 'row', flexWrap: 'wrap', justifyContent: 'center', columnGap: 0.24 * fs }]}>
                    {lines.reply.map((w, i) => (
                      <CapLayer key={`${w}${i}`} cap={cap} index={5 + i} rise={0} onLayout={(e) => onWordLayout(i, e)}>
                        <Word cap={cap} index={i} wordX={wordX} ramp={ramp} style={bodyStyle}>
                          {w}
                        </Word>
                      </CapLayer>
                    ))}
                  </View>
                </View>
              </>
            )}
          </Animated.View>
          {control && (
            <>
              <Animated.View
                pointerEvents="none"
                accessibilityElementsHidden
                importantForAccessibility="no-hide-descendants"
                style={[styles.askRow, { top: yc0 + ref.ask.y * S, columnGap: 0.26 * ref.ask.size * S }, textShow]}
              >
                {lines.ask.map((w, i) => (
                  <AskWord key={`${w}${i}`} ask={askSV} index={i} style={askStyle}>
                    {w}
                  </AskWord>
                ))}
              </Animated.View>
              <Animated.View
                pointerEvents="none"
                style={[styles.controlDock, { bottom: Math.max(insets.bottom, 12) + ref.control.bottom * S }, textShow]}
              >
                <TalkControl
                  ask={askSV}
                  clock={clock}
                  d={ref.control.d * S}
                  hintStyle={hintStyle}
                  hold={COPY.hold}
                  release={COPY.release}
                />
              </Animated.View>
            </>
          )}
          <View
            accessible
            accessibilityRole="button"
            accessibilityLabel={`${COPY.label}. ${a11yLabel}`}
            accessibilityHint={mode === 'tapToToggle' ? COPY.hintToggle : COPY.hint}
            accessibilityValue={{ text: COPY.states[STATES[a11yState]] }}
            accessibilityActions={[{ name: 'activate', label: a11yState === 1 ? COPY.stop : COPY.start }]}
            onAccessibilityAction={onA11y}
            style={StyleSheet.absoluteFill}
          />
        </View>
      </GestureDetector>
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1 },
  textBlock: { position: 'absolute', left: TEXT_MARGIN, right: TEXT_MARGIN, alignItems: 'center' },
  layer: { position: 'absolute', left: 0, right: 0, top: 0, alignItems: 'center' },
  askRow: { position: 'absolute', left: TEXT_MARGIN, right: TEXT_MARGIN, flexDirection: 'row', flexWrap: 'wrap', justifyContent: 'center' },
  controlDock: { position: 'absolute', left: 0, right: 0, alignItems: 'center' },
});
