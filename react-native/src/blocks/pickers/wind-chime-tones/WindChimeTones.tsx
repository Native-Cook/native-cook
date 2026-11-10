import { useFonts } from 'expo-font';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useEffect, useMemo, useState } from 'react';
import { AppState, type LayoutChangeEvent, PixelRatio, StyleSheet, Text, View } from 'react-native';
import Animated, { useAnimatedReaction, useAnimatedStyle, useFrameCallback, useSharedValue, withTiming } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import Svg, { Circle as SvgCircle, Path as SvgPath, Rect as SvgRect } from 'react-native-svg';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';

import { PressScale } from '../../../shared/PressScale';
import { useReducedMotionPreference } from '../../../shared/useReducedMotionPreference';
import { WctGpu } from './gpu/WctGpu';
import { useWctAudio } from './use-wct-audio';
import { frameAt, kickAt, LOOP, PIECE, pushAt, ringsAt } from './wct-motion';
import { COLOR, COPY, D, FONT, makeLayout } from './wct-theme';

/*
 Wind Chime Tones — a speaker's page in an invented smart-home app, laid out from the reference pin
 (190769734203774879): back, the maker and Speaker, more; the speaker with its power button; AirPlay,
 EQ and Add to pair; the player with what is playing, its progress and the controls.

 The speaker and the room are drawn on WebGPU (TypeGPU-resolved WGSL) behind the cards: a disc of woven
 fabric on a brass yoke and stand. Press play and it plays a piece of wind chimes (synthesised for the
 block): every chime pushes the cone out under the fabric, lifts the light in the room and sends a ring
 of sound out across the screen, and each card jumps as the ring reaches it, the nearest first.
 Power switches the speaker off: the fabric dulls and the music stops.

 autoPlay: LOOP = 10 s (wct-motion.ts); frame 10 s equals frame 0, the speaker at rest. It stays
 silent: the rings and bounces follow the piece's chimes without playing it. A touch takes over; 2 s
 after the last one, once it is paused, the loop restarts. Reduced motion: no loop; the cards stay
 still and the rings are fainter.
*/

const pixelRatio = PixelRatio.get();

function buzz(kind: 'tap' | 'power') {
  if (process.env.EXPO_OS === 'web') return;
  if (kind === 'tap') Haptics.selectionAsync().catch(() => {});
  else Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Medium).catch(() => {});
}

type Props = { autoPlay?: boolean };

export function WindChimeTones({ autoPlay = false }: Props) {
  const insets = useSafeAreaInsets();
  const reduced = useReducedMotionPreference();
  const auto = autoPlay && !reduced;
  const [size, setSize] = useState<{ w: number; h: number } | null>(null);
  const [fontsLoaded] = useFonts({
    [FONT.regular]: require('./assets/fonts/Inter-Regular.ttf'),
    [FONT.medium]: require('./assets/fonts/Inter-Medium.ttf'),
    [FONT.semibold]: require('./assets/fonts/Inter-SemiBold.ttf'),
  });
  const [active, setActive] = useState(AppState.currentState === 'active');
  useEffect(() => {
    const sub = AppState.addEventListener('change', (s) => setActive(s === 'active'));
    return () => sub.remove();
  }, []);
  const [gpuOk, setGpuOk] = useState(false);
  const audio = useWctAudio(true);
  const w = size?.w ?? 402;
  const h = size?.h ?? 874;
  const L = useMemo(() => makeLayout(w, h, insets.top, insets.bottom), [w, h, insets.top, insets.bottom]);
  const geo = useMemo(() => {
    const sx = L.sx;
    const cx = D.speaker.x * sx;
    const cy = L.Y(D.speaker.y);
    const dist = (x: number, y: number) => Math.hypot(x - cx, y - cy);
    const tools = D.tools.xs.map((x) => ({ x: x * sx, y: L.Y(D.tools.y), w: D.tools.w * sx, h: D.tools.h * L.k }));
    // each card as the wave meets it: its distance from the speaker, the way out from it, its half width
    // and its mass (the player is a big, heavy slab; the power button a light disc)
    const body = (x: number, y: number, half: number, mass: number) => {
      const d = Math.max(1, dist(x, y));
      return { d, ux: (x - cx) / d, uy: (y - cy) / d, half, mass };
    };
    const powerX = D.power.x * sx;
    const powerY = L.Y(D.power.y);
    const playerW = D.player.w * sx;
    return {
      sx,
      cx,
      cy,
      r: D.speaker.r * sx,
      tools,
      toolBody: tools.map((t) => body(t.x + t.w / 2, t.y + t.h / 2, t.w / 2, 1)),
      playerBody: body(D.player.x * sx + playerW / 2, L.Y(D.player.y) + 40, playerW / 2, 2.4),
      powerBody: body(powerX, powerY, D.power.r, 0.6),
      playX: D.controls.xs[1] * sx,
      playY: L.Y(D.controls.y),
    };
  }, [L]);

  /* ------------------------------------------------------------ state */
  const clock = useSharedValue(0);
  const mode = useSharedValue(auto ? 0 : 1);
  const autoStart = useSharedValue(0);
  const lastTouch = useSharedValue(-100);
  const pin = useSharedValue(-1);
  const rate = useSharedValue(1);

  const playing = useSharedValue(0);
  const power = useSharedValue(1);
  const pieceT = useSharedValue(-1); // s since the piece started (−1 not yet)
  const cut = useSharedValue(PIECE);
  const laps = useSharedValue(1);
  const playStart = useSharedValue(0);
  const pausedAt = useSharedValue(-1); // the piece's time when paused
  const pauseClock = useSharedValue(0);
  const glow = useSharedValue(0);
  const finger = useSharedValue(0);
  const fingerPress = useSharedValue(0);
  const frame = useSharedValue<number[]>([]);
  const [isPlaying, setIsPlaying] = useState(false);
  const [isOn, setIsOn] = useState(true);

  /* ------------------------------------------------------------ actions */
  const setPlay = (on: boolean) => {
    'worklet';
    const now = clock.get();
    if (on) {
      const from = pausedAt.get() >= 0 ? pausedAt.get() : 0;
      playStart.set(now - from);
      pausedAt.set(-1);
      playing.set(1);
      scheduleOnRN(audio, true, from % PIECE);
    } else if (playing.get() === 1) {
      pausedAt.set(now - playStart.get());
      pauseClock.set(now);
      playing.set(0);
      scheduleOnRN(audio, false, 0);
    }
    scheduleOnRN(buzz, 'tap');
  };
  const takeOver = () => {
    'worklet';
    lastTouch.set(clock.get());
    if (mode.get() !== 0) return;
    mode.set(1);
    finger.set(withTiming(0, { duration: 150 }));
    fingerPress.set(0);
    // the loop's rings run out; the piece is paused where the loop had it
    const t = pieceT.get();
    pausedAt.set(t >= 0 ? Math.min(t, cut.get()) : -1);
    pauseClock.set(clock.get());
    playing.set(0);
  };

  /* ------------------------------------------------------------ clock */
  const ticker = useFrameCallback((fi) => {
    const dt = Math.min(0.05, (fi.timeSincePreviousFrame ?? 16) / 1000) * rate.get();
    clock.set(clock.get() + dt);
    const now = clock.get();
    if (mode.get() === 0) {
      const t = pin.get() >= 0 ? pin.get() : (((now - autoStart.get()) % LOOP) + LOOP) % LOOP;
      const s = frameAt(t);
      playing.set(s.playing);
      pieceT.set(s.music);
      cut.set(s.cut);
      laps.set(1);
      finger.set(s.finger);
      fingerPress.set(s.press);
    } else if (playing.get() === 1) {
      const t = now - playStart.get();
      pieceT.set(t % PIECE);
      cut.set(PIECE);
      laps.set(t >= PIECE ? 2 : 1);
    } else if (pausedAt.get() >= 0) {
      // paused: the rings already on their way run on
      const p = pausedAt.get();
      pieceT.set((p % PIECE) + (now - pauseClock.get()));
      cut.set(p % PIECE);
      laps.set(p >= PIECE ? 2 : 1);
    } else pieceT.set(-1);
    const t = pieceT.get();
    const on = power.get();
    const kick = t >= 0 ? pushAt(t, cut.get(), laps.get()) : 0;
    glow.set(glow.get() + (Math.min(1, Math.abs(kick)) - glow.get()) * Math.min(1, dt * 6));
    const rings = ringsAt(t, cut.get(), laps.get());
    if (reduced) for (let i = 1; i < rings.length; i += 2) rings[i] *= 0.4;
    frame.set([L.w, L.h, pixelRatio, now % 600, geo.cx, geo.cy, geo.r, kick, on, glow.get(), 0, 0, ...rings]);
    if (mode.get() === 0) return;
    if (!auto || playing.get() === 1 || clock.get() - lastTouch.get() < 2) return;
    // the rings have run out: back to the loop
    if (t < 0 || t - cut.get() > 2.4) {
      pausedAt.set(-1);
      power.set(1);
      autoStart.set(now);
      mode.set(0);
    }
  }, false);
  useEffect(() => {
    ticker.setActive(active);
  }, [ticker, active]);

  useEffect(() => {
    autoStart.set(clock.get());
    if (auto) {
      mode.set(0);
      return;
    }
    scheduleOnUI(() => {
      'worklet';
      mode.set(1);
      playing.set(0);
      pausedAt.set(-1);
      finger.set(0);
    });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [auto]);

  useEffect(() => {
    if (!__DEV__) return;
    (globalThis as Record<string, unknown>).__windChime = {
      at: (t: number) => pin.set(t),
      play: () => pin.set(-1),
      rate: (k: number) => rate.set(k),
      restart: () => {
        autoStart.set(clock.get());
        pin.set(-1);
      },
      state: () => JSON.stringify({ clock: +clock.get().toFixed(2), mode: mode.get(), playing: playing.get(), piece: +pieceT.get().toFixed(2), power: power.get() }),
    };
  }, [pin, rate, autoStart, clock, mode, playing, pieceT, power]);

  useAnimatedReaction(
    () => playing.get() === 1,
    (now, before) => {
      if (now !== before) scheduleOnRN(setIsPlaying, now);
    },
  );

  const togglePlay = () =>
    scheduleOnUI(() => {
      'worklet';
      takeOver();
      if (power.get() < 0.5) return;
      setPlay(playing.get() === 0);
    });
  const togglePower = () => {
    buzz('power');
    const next = !isOn;
    setIsOn(next);
    scheduleOnUI(() => {
      'worklet';
      takeOver();
      power.set(withTiming(next ? 1 : 0, { duration: 400 }));
      if (!next) setPlay(false);
    });
  };

  /* ------------------------------------------------------------ the bounces */
  // A card rides the wave out from the speaker and back on its spring (kickAt). The wavefront reaches its
  // near edge before its far one, so a card off to the side rocks as the wave crosses it; and it stretches
  // a little along its speed, flattening as it turns back.
  type Body = { d: number; ux: number; uy: number; half: number; mass: number };
  const ride = (b: Body) => {
    'worklet';
    const t = pieceT.get();
    const p = power.get();
    if (reduced || t < 0 || p < 0.001) return [{ translateX: 0 }, { translateY: 0 }, { rotate: '0rad' }, { scaleX: 1 }, { scaleY: 1 }];
    const k = kickAt(t, b.d, cut.get(), laps.get(), b.mass);
    const near = kickAt(t, b.d - b.half * Math.abs(b.ux), cut.get(), laps.get(), b.mass).x;
    const far = kickAt(t, b.d + b.half * Math.abs(b.ux), cut.get(), laps.get(), b.mass).x;
    // the edges' moves across the card's width, as a turn: the near edge (toward the speaker) goes first
    const rock = (b.uy * (far - near) * Math.sign(b.ux)) / (2 * b.half);
    const st = 1 + Math.max(-0.035, Math.min(0.035, (k.v / 3000) * p));
    return [{ translateX: k.x * b.ux * p }, { translateY: k.x * b.uy * p }, { rotate: `${rock * p}rad` }, { scaleX: 1 / Math.sqrt(st) }, { scaleY: st }];
  };
  const tool0 = useAnimatedStyle(() => ({ transform: ride(geo.toolBody[0]) }));
  const tool1 = useAnimatedStyle(() => ({ transform: ride(geo.toolBody[1]) }));
  const tool2 = useAnimatedStyle(() => ({ transform: ride(geo.toolBody[2]) }));
  const playerStyle = useAnimatedStyle(() => ({ transform: ride(geo.playerBody) }));
  const powerStyle = useAnimatedStyle(() => ({ transform: ride(geo.powerBody) }));
  const toolStyles = [tool0, tool1, tool2];
  const progress = useAnimatedStyle(() => {
    const t = pieceT.get();
    const p = t < 0 ? 0.52 : 0.52 + (((t % PIECE) + PIECE) % PIECE) / PIECE * 0.3;
    return { width: `${Math.min(1, p) * 100}%` };
  });
  const fingerStyle = useAnimatedStyle(() => ({
    opacity: finger.get(),
    transform: [{ translateX: geo.playX - 22 }, { translateY: geo.playY - 22 }, { scale: 1 - 0.12 * fingerPress.get() }],
  }));

  const onLayout = (e: LayoutChangeEvent) => {
    const { width, height } = e.nativeEvent.layout;
    if (!size || size.w !== width || size.h !== height) setSize({ w: width, h: height });
  };
  const Y = L.Y;
  const sx = geo.sx;

  return (
    <View style={styles.screen} onLayout={onLayout}>
      <StatusBar style="light" />
      {!gpuOk && <View style={[styles.flat, { left: geo.cx - geo.r, top: geo.cy - geo.r, width: geo.r * 2, height: geo.r * 2, borderRadius: geo.r }]} />}
      {size && <WctGpu frame={frame} width={L.w} height={L.h} disabled={false} onAvailable={setGpuOk} />}

      <PressScale style={[styles.icon, { left: D.back.x * sx - 22, top: Y(D.back.y) - 22 }]} reduced={reduced} accessibilityRole="button" accessibilityLabel="Back">
        <Svg width={10} height={16} viewBox="0 0 10 16">
          <SvgPath d="M8 1.5 L1.8 8 L8 14.5" stroke="#FFFFFF" strokeWidth={1.9} fill="none" strokeLinecap="round" strokeLinejoin="round" />
        </Svg>
      </PressScale>
      <PressScale style={[styles.icon, { left: D.more.x * sx - 22, top: Y(D.more.y) - 22 }]} reduced={reduced} accessibilityRole="button" accessibilityLabel="More">
        <Svg width={4} height={16} viewBox="0 0 4 16">
          <SvgCircle cx={2} cy={3} r={1.8} fill="#FFFFFF" />
          <SvgCircle cx={2} cy={13} r={1.8} fill="#FFFFFF" />
        </Svg>
      </PressScale>
      <Animated.View style={[styles.power, { left: D.power.x * sx - D.power.r, top: Y(D.power.y) - D.power.r, width: D.power.r * 2, height: D.power.r * 2 }, powerStyle]}>
        <PressScale style={[styles.powerBtn, { borderRadius: D.power.r, backgroundColor: isOn ? COLOR.power : '#5A5A5C' }]} reduced={reduced} onPress={togglePower} accessibilityRole="switch" accessibilityState={{ checked: isOn }} accessibilityLabel="Power">
          <Svg width={22} height={22} viewBox="0 0 22 22">
            <SvgPath d="M11 2.5 V10.5 M6.2 5.3 A7.5 7.5 0 1 0 15.8 5.3" stroke={isOn ? COLOR.red : '#FFFFFF'} strokeWidth={2} fill="none" strokeLinecap="round" />
          </Svg>
        </PressScale>
      </Animated.View>

      {fontsLoaded && (
        <>
          <Text style={[styles.title, { top: Y(D.title.y) - 10 }]} allowFontScaling={false} accessibilityRole="header">
            {COPY.maker}
          </Text>
          <Text style={[styles.title, { top: Y(D.title.sub) - 10 }]} allowFontScaling={false}>
            {COPY.kind}
          </Text>
          {geo.tools.map((t, i) => (
            <Animated.View key={i} style={[styles.tool, { left: t.x, top: t.y, width: t.w, height: t.h }, toolStyles[i]]}>
              <PressScale style={styles.toolInner} reduced={reduced} accessibilityRole="button" accessibilityLabel={COPY.tools[i]}>
                <ToolIcon kind={i} />
                <Text style={styles.toolText} allowFontScaling={false}>
                  {COPY.tools[i]}
                </Text>
              </PressScale>
            </Animated.View>
          ))}
          <Animated.View style={[styles.player, { left: D.player.x * sx, top: Y(D.player.y), width: D.player.w * sx, height: D.player.h * L.k }, playerStyle]}>
            <Svg width={30} height={30} viewBox="0 0 30 30" style={[styles.abs, { left: (D.icon.x - D.player.x) * sx - 15, top: Y(D.icon.y) - Y(D.player.y) - 15 }]}>
              <SvgPath d="M4 11 H9 L16 5 V25 L9 19 H4 Z" fill="#C9C9CC" />
              <SvgPath d="M20 10 A6 6 0 0 1 20 20 M23 7 A10 10 0 0 1 23 23" stroke="#C9C9CC" strokeWidth={1.8} fill="none" strokeLinecap="round" />
            </Svg>
            <Text style={[styles.now, { left: (D.now.x - D.player.x) * sx, top: Y(D.now.y) - Y(D.player.y) - 10 }]} allowFontScaling={false}>
              {COPY.playing}
            </Text>
            <Text style={[styles.track, { left: (D.now.x - D.player.x) * sx, top: Y(D.now.sub) - Y(D.player.y) - 9, width: 200 * sx }]} numberOfLines={1} allowFontScaling={false}>
              {COPY.track}
            </Text>
            <View style={[styles.bar, { left: (D.bar.x0 - D.player.x) * sx, top: Y(D.bar.y) - Y(D.player.y), width: (D.bar.x1 - D.bar.x0) * sx }]}>
              <Animated.View style={[styles.barOn, progress]} />
            </View>
            {[0, 1, 2].map((i) => (
              <PressScale
                key={i}
                style={[styles.control, { left: (D.controls.xs[i] - D.player.x) * sx - 24, top: Y(D.controls.y) - Y(D.player.y) - 24 }]}
                reduced={reduced}
                onPress={i === 1 ? togglePlay : undefined}
                accessibilityRole="button"
                accessibilityLabel={i === 0 ? 'Previous' : i === 2 ? 'Next' : isPlaying ? 'Pause' : 'Play'}
              >
                <ControlIcon kind={i === 1 ? (isPlaying ? 3 : 1) : i} />
              </PressScale>
            ))}
          </Animated.View>
        </>
      )}

      <Animated.View style={[styles.finger, fingerStyle]} pointerEvents="none" />
    </View>
  );
}

function ToolIcon({ kind }: { kind: number }) {
  if (kind === 0)
    return (
      <Svg width={22} height={20} viewBox="0 0 22 20">
        <SvgRect x={1.5} y={1.5} width={19} height={13} rx={2} stroke="#FFFFFF" strokeWidth={1.6} fill="none" />
        <SvgPath d="M11 11 L16 18.5 H6 Z" fill="#FFFFFF" />
      </Svg>
    );
  if (kind === 1)
    return (
      <Svg width={24} height={20} viewBox="0 0 24 20">
        {[2, 6, 10, 14, 18, 22].map((x, i) => (
          <SvgPath key={x} d={`M${x} ${10 - [3, 6, 9, 5, 7, 2][i]} V${10 + [3, 6, 9, 5, 7, 2][i]}`} stroke="#FFFFFF" strokeWidth={1.8} strokeLinecap="round" />
        ))}
      </Svg>
    );
  return (
    <Svg width={20} height={20} viewBox="0 0 20 20">
      <SvgPath d="M8.5 11.5 L11.5 8.5 M7 8 L5 10 A3.5 3.5 0 0 0 10 15 L12 13 M13 12 L15 10 A3.5 3.5 0 0 0 10 5 L8 7" stroke="#FFFFFF" strokeWidth={1.7} fill="none" strokeLinecap="round" />
    </Svg>
  );
}

function ControlIcon({ kind }: { kind: number }) {
  if (kind === 0)
    return (
      <Svg width={30} height={22} viewBox="0 0 30 22">
        <SvgPath d="M14 2 L3 11 L14 20 Z M27 2 L16 11 L27 20 Z" fill="#FFFFFF" strokeLinejoin="round" />
      </Svg>
    );
  if (kind === 2)
    return (
      <Svg width={30} height={22} viewBox="0 0 30 22">
        <SvgPath d="M3 2 L14 11 L3 20 Z M16 2 L27 11 L16 20 Z" fill="#FFFFFF" />
      </Svg>
    );
  if (kind === 3)
    return (
      <Svg width={24} height={28} viewBox="0 0 24 28">
        <SvgRect x={3} y={2} width={6} height={24} rx={1.5} fill="#FFFFFF" />
        <SvgRect x={15} y={2} width={6} height={24} rx={1.5} fill="#FFFFFF" />
      </Svg>
    );
  return (
    <Svg width={24} height={30} viewBox="0 0 24 30">
      <SvgPath d="M3 2 L22 15 L3 28 Z" fill="#FFFFFF" strokeLinejoin="round" />
    </Svg>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: COLOR.page, overflow: 'hidden' },
  abs: { position: 'absolute' },
  flat: { position: 'absolute', backgroundColor: '#1A1A1B', borderWidth: 1, borderColor: '#555' },
  icon: { position: 'absolute', width: 44, height: 44, alignItems: 'center', justifyContent: 'center' },
  power: { position: 'absolute' },
  powerBtn: { flex: 1, alignItems: 'center', justifyContent: 'center', shadowColor: '#000', shadowOpacity: 0.35, shadowRadius: 12, shadowOffset: { width: 0, height: 6 } },
  title: { position: 'absolute', left: 0, right: 0, textAlign: 'center', fontFamily: FONT.semibold, fontSize: D.title.size, color: COLOR.ink },
  tool: { position: 'absolute', borderRadius: D.tools.r, backgroundColor: COLOR.card },
  toolInner: { flex: 1, alignItems: 'center', justifyContent: 'center', gap: 12 },
  toolText: { fontFamily: FONT.medium, fontSize: 11.5, color: COLOR.ink },
  player: { position: 'absolute', borderRadius: D.player.r, backgroundColor: COLOR.card },
  now: { position: 'absolute', fontFamily: FONT.semibold, fontSize: D.now.size, color: COLOR.ink },
  track: { position: 'absolute', fontFamily: FONT.regular, fontSize: 13, color: COLOR.mute },
  bar: { position: 'absolute', height: 2, borderRadius: 1, backgroundColor: COLOR.track, overflow: 'hidden' },
  barOn: { height: 2, backgroundColor: '#E6E6E8' },
  control: { position: 'absolute', width: 48, height: 48, alignItems: 'center', justifyContent: 'center' },
  finger: { position: 'absolute', left: 0, top: 0, width: 44, height: 44, borderRadius: 22, borderWidth: 1.5, borderColor: 'rgba(255,255,255,0.6)', backgroundColor: 'rgba(255,255,255,0.1)' },
});
