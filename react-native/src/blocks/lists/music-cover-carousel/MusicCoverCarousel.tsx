import { BlurView } from 'expo-blur';
import * as Haptics from 'expo-haptics';
import { Image } from 'expo-image';
import { LinearGradient } from 'expo-linear-gradient';
import { StatusBar } from 'expo-status-bar';
import { useCallback, useEffect, useRef, useState } from 'react';
import { Pressable, StyleSheet, Text, useWindowDimensions, View } from 'react-native';
import { Gesture, GestureDetector, GestureHandlerRootView } from 'react-native-gesture-handler';
import Animated, {
  FadeIn, FadeOut,  Easing, ReduceMotion, type SharedValue, cancelAnimation, interpolate, useAnimatedReaction, useAnimatedStyle,
  useReducedMotion, useSharedValue, withRepeat, withSpring, withTiming,
} from 'react-native-reanimated';
import Svg, { Defs, Ellipse, RadialGradient, Stop } from 'react-native-svg';
import { scheduleOnRN } from 'react-native-worklets';

import { Forward, ListGlyph, LyricsGlyph, MoreGlyph, PauseGlyph, PlayGlyph, Rewind, VolumeGlyph, WaveGlyph } from './icons';
import {
  CHANGES, EASE_OUT, FLICK_V, FLOW_SPRING, IDLE_RESUME_MS, LOOP_MS, PLAYHEAD, PRESS, PROJECT_S, SHEEN, TEXT_SWAP,
  flowAt, playheadAt, pressAt,
} from './motion';
import { GLASS, GLYPH, PIN, PIN_CX, PIN_CY, S, START, TRACKS } from './theme';

export { LOOP_MS };

const NEVER = ReduceMotion.Never;
const LAST = TRACKS.length - 1;
type Track = (typeof TRACKS)[number];

function ramp(v: number, a: number, b: number) {
  'worklet';
  return Math.min(1, Math.max(0, (v - a) / (b - a)));
}

function Backdrop({ track, index, flow }: { track: Track; index: number; flow: SharedValue<number> }) {
  // The room takes the colour of the cover in front (the pin's warm wash for the centre album), fading in as it arrives.
  const style = useAnimatedStyle(() => ({ opacity: 1 - Math.min(1, Math.abs(index - flow.get())) }));
  return (
    <Animated.View style={[StyleSheet.absoluteFill, style]}>
      <LinearGradient colors={track.wash} start={{ x: 0, y: 0.5 }} end={{ x: 1, y: 0.5 }} style={StyleSheet.absoluteFill} />
      <Image source={track.cover} style={[StyleSheet.absoluteFill, { opacity: 0.12, transform: [{ scale: 1.5 }] }]} contentFit="cover" blurRadius={60} transition={0} />
    </Animated.View>
  );
}

function Card({ track, index, flow, cx, cy, reduced, onPick }: {
  track: Track; index: number; flow: SharedValue<number>; cx: number; cy: number; reduced: boolean; onPick: (i: number) => void;
}) {
  const press = useSharedValue(0);
  const w = PIN.card.w * S;
  const h = PIN.card.h * S;
  const style = useAnimatedStyle(() => {
    const d = index - flow.get();
    const a = Math.min(3, Math.abs(d));
    const side = d < 0 ? -1 : 1;
    const slots = side > 0 ? PIN.slotsRight : PIN.slots;
    const x = side * interpolate(a, [0, 1, 2, 3], [0, slots[1], slots[2], slots[2] + 40]) * S;
    const scale = interpolate(a, [0, 1, 2, 3], [PIN.scales[0], PIN.scales[1], PIN.scales[2], 0.7]);
    const turn = side * interpolate(a, [0, 1, 2, 3], [PIN.turns[0], PIN.turns[1], PIN.turns[2], 34]);
    return {
      opacity: reduced ? 1 - ramp(a, 0, 3) * 0.6 : 1 - ramp(a, 2.2, 3),
      zIndex: Math.round(100 - a * 10),
      transform: reduced
        ? [{ translateX: Math.round(d) * PIN.slots[1] * S }]
        : [{ perspective: 700 }, { translateX: x }, { scale: scale * (1 - (1 - PRESS.scale) * press.get()) }, { rotateY: `${-turn}deg` }],
    };
  });
  const textStyle = useAnimatedStyle(() => ({ opacity: 1 - 0.25 * ramp(Math.abs(index - flow.get()), 0, 1) }));
  // Light on the glass: placed by the card's angle, as strong as the card is between two slots.
  const bandW = w * SHEEN.width;
  const sheen = useAnimatedStyle(() => {
    if (reduced) return { opacity: 0 };
    const d = index - flow.get();
    const a = Math.min(3, Math.abs(d));
    const turn = (d < 0 ? -1 : 1) * interpolate(a, [0, 1, 2, 3], [PIN.turns[0], PIN.turns[1], PIN.turns[2], 34]);
    const between = Math.abs(d - Math.round(d));
    const place = 0.5 - turn / SHEEN.span;
    return { opacity: Math.sin(Math.PI * between), transform: [{ translateX: place * w - bandW / 2 }, { rotate: `${SHEEN.slant}deg` }] };
  });
  const inset = PIN.card.inset * S;
  const cover = PIN.card.cover * S;
  return (
    <Animated.View style={[styles.card, { left: cx - w / 2, top: cy - h / 2, width: w, height: h, borderRadius: PIN.card.radius * S }, style]}>
      <Pressable style={StyleSheet.absoluteFill} onPress={() => onPick(index)} accessibilityRole="button" pressRetentionOffset={16}
        onPressIn={() => press.set(withTiming(1, { duration: PRESS.ms, easing: EASE_OUT }))}
        onPressOut={() => press.set(withTiming(0, { duration: PRESS.ms, easing: EASE_OUT }))}
        accessibilityLabel={`${track.title} by ${track.artist}`}>
        <BlurView intensity={28} tint="dark" style={StyleSheet.absoluteFill} />
        <View style={[StyleSheet.absoluteFill, { backgroundColor: GLASS.card }]} />
        <Image source={track.cover} transition={0} contentFit="cover"
          style={{ position: 'absolute', left: inset, top: inset, width: w - inset * 2, height: cover, borderRadius: PIN.card.coverRadius * S }} />
        <Animated.View style={[StyleSheet.absoluteFill, textStyle]}>
          <Text numberOfLines={1} style={[styles.cardTitle, { top: PIN.card.titleY * S - 8 }]}>{track.title}</Text>
          <Text numberOfLines={1} style={[styles.cardArtist, { top: PIN.card.artistY * S - 7 }]}>{track.artist}</Text>
        </Animated.View>
        <Animated.View pointerEvents="none" style={[styles.sheen, { width: bandW, height: h * 1.4, top: -h * 0.2 }, sheen]}>
          <LinearGradient colors={['#FFFFFF00', `#FFFFFF${Math.round(SHEEN.alpha * 255).toString(16)}`, '#FFFFFF00']}
            start={{ x: 0, y: 0.5 }} end={{ x: 1, y: 0.5 }} style={StyleSheet.absoluteFill} />
        </Animated.View>
      </Pressable>
    </Animated.View>
  );
}

function IconButton({ x, cy, label, onPress, press, children }: {
  x: number; cy: number; label: string; onPress?: () => void; press?: SharedValue<number>; children: React.ReactNode;
}) {
  const local = useSharedValue(0);
  const style = useAnimatedStyle(() => {
    const p = Math.max(local.get(), press ? press.get() : 0);
    return { opacity: 1 - (1 - PRESS.dim) * p, transform: [{ scale: 1 - (1 - PRESS.scale) * p }] };
  });
  return (
    <Pressable accessibilityRole="button" accessibilityLabel={label} onPress={onPress} hitSlop={6}
      pressRetentionOffset={16} onPressIn={() => local.set(withTiming(1, { duration: PRESS.ms, easing: EASE_OUT }))} onPressOut={() => local.set(withTiming(0, { duration: PRESS.ms, easing: EASE_OUT }))}
      style={[styles.iconBtn, { left: x - 16, top: cy - 16 }]}>
      <Animated.View style={style}>{children}</Animated.View>
    </Pressable>
  );
}

export function MusicCoverCarousel({ autoPlay = false, freezeAt }: { autoPlay?: boolean; freezeAt?: number }) {
  const { width, height } = useWindowDimensions();
  const reduced = useReducedMotion();
  const px = (x: number) => (x - PIN_CX) * S + width / 2;
  const py = (y: number) => (y - PIN_CY) * S + height / 2;

  const frozen = freezeAt !== undefined;
  const canAutoPlay = autoPlay && !reduced && !frozen;
  const flow = useSharedValue(frozen ? flowAt(freezeAt / 1000) : START);
  const start = useSharedValue(START);
  const clock = useSharedValue(0);
  const playing = useSharedValue(1);
  const nextPress = useSharedValue(0);
  const prevPress = useSharedValue(0);
  const scripted = useSharedValue(canAutoPlay);
  const [current, setCurrent] = useState(frozen ? Math.round(flowAt(freezeAt / 1000)) : START);
  const [isPlaying, setPlaying] = useState(true);

  // By hand, the playhead restarts with each new track (in the loop it is a function of the clock).
  const changedAt = useSharedValue(0);
  useAnimatedReaction(() => Math.round(flow.get()), (i, prev) => {
    if (prev !== null && i !== prev) {
      changedAt.set(clock.get());
      scheduleOnRN(setCurrent, Math.max(0, Math.min(LAST, i)));
    }
  });

  const goTo = useCallback((to: number, velocity = 0) => {
    const target = Math.max(0, Math.min(LAST, to));
    if (!scripted.get()) Haptics.selectionAsync().catch(() => {});
    flow.set(reduced ? target : withSpring(target, { ...FLOW_SPRING, velocity, reduceMotion: NEVER }));
  }, [flow, reduced, scripted]);

  const idle = useRef<ReturnType<typeof setTimeout> | null>(null);
  const resume = useRef<ReturnType<typeof setTimeout> | null>(null);
  const togglePlay = useCallback(() => {
    const next = playing.get() < 0.5;
    playing.set(withTiming(next ? 1 : 0, { duration: 200, easing: EASE_OUT, reduceMotion: NEVER }));
    setPlaying(next);
  }, [playing]);

  // The loop: flow and button presses are pure functions of the clock while the script runs.
  useAnimatedReaction(() => clock.get(), (ms) => {
    if (!scripted.get()) return;
    const t = ms / 1000;
    flow.set(flowAt(t));
    nextPress.set(pressAt(t, 'next'));
    prevPress.set(pressAt(t, 'prev'));
  });
  const startClock = useCallback(() => {
    cancelAnimation(clock);
    clock.set(0);
    clock.set(withRepeat(withTiming(LOOP_MS, { duration: LOOP_MS, easing: Easing.linear, reduceMotion: NEVER }), -1, false));
  }, [clock]);
  const startLoop = useCallback(() => {
    if (!canAutoPlay) return;
    scripted.set(true);
    startClock();
  }, [canAutoPlay, scripted, startClock]);

  useEffect(() => {
    if (frozen) {
      // Dev freeze: hold the loop's frame at freezeAt.
      cancelAnimation(clock);
      clock.set(freezeAt);
      flow.set(flowAt(freezeAt / 1000));
      return;
    }
    if (canAutoPlay) {
      startLoop();
    } else if (reduced) {
      cancelAnimation(clock);
      scripted.set(false);
      flow.set(Math.max(0, Math.min(LAST, Math.round(flow.get()))));
      nextPress.set(0);
      prevPress.set(0);
    } else {
      startClock();
    }
    return () => {
      cancelAnimation(clock);
      if (idle.current) clearTimeout(idle.current);
      if (resume.current) clearTimeout(resume.current);
    };
  }, [canAutoPlay, clock, flow, freezeAt, frozen, nextPress, prevPress, reduced, scripted, startClock, startLoop]);

  const takeOver = useCallback(() => {
    if (idle.current) clearTimeout(idle.current);
    if (resume.current) clearTimeout(resume.current);
    if (scripted.get()) {
      scripted.set(false);
      changedAt.set(clock.get() - (playheadAt(clock.get() / 1000, CHANGES) - PLAYHEAD.from) / PLAYHEAD.perLoop * LOOP_MS);
      nextPress.set(0);
      prevPress.set(0);
    }
  }, [changedAt, clock, nextPress, prevPress, scripted]);
  const scheduleResume = useCallback(() => {
    if (!canAutoPlay) return;
    if (idle.current) clearTimeout(idle.current);
    idle.current = setTimeout(() => {
      flow.set(withTiming(START, { duration: 300, easing: EASE_OUT, reduceMotion: NEVER }));
      playing.set(withTiming(1, { duration: 200 }));
      setPlaying(true);
      resume.current = setTimeout(startLoop, 320);
    }, IDLE_RESUME_MS);
  }, [canAutoPlay, flow, playing, startLoop]);

  // The pan can swallow the touch end, so a finished pan also re-arms the idle timer.
  const released = useSharedValue(0);
  useAnimatedReaction(() => released.get(), (v, prev) => {
    if (prev !== null && v !== prev) scheduleOnRN(scheduleResume);
  });
  const slot = PIN.slots[1] * S;
  const pan = Gesture.Pan().activeOffsetX([-8, 8])
    .onBegin(() => { start.set(flow.get()); })
    .onUpdate((e) => {
      const raw = start.get() - e.translationX / slot;
      const over = raw < 0 ? raw : raw > LAST ? raw - LAST : 0;
      flow.set(raw - over + over * 0.3);
    })
    .maxPointers(1)
    .onEnd((e) => {
      const v = -e.velocityX / slot;
      const flick = Math.abs(e.velocityX) / 1000 > FLICK_V ? Math.sign(v) : 0;
      const target = flick !== 0 ? Math.round(start.get()) + flick : Math.round(flow.get() + v * PROJECT_S);
      scheduleOnRN(goTo, target, v);
    })
    .onFinalize(() => { released.set(released.get() + 1); });

  const progress = useAnimatedStyle(() => {
    const ms = clock.get();
    const f = scripted.get() || frozen
      ? playheadAt(ms / 1000, CHANGES)
      : Math.min(1, PLAYHEAD.from + PLAYHEAD.perLoop * (((ms - changedAt.get() + LOOP_MS * 10) % (LOOP_MS * 10)) / LOOP_MS));
    return { width: PIN.progress.w * S * f };
  });
  const pauseStyle = useAnimatedStyle(() => ({ opacity: playing.get(), transform: [{ scale: 0.7 + 0.3 * playing.get() }] }));
  const playStyle = useAnimatedStyle(() => ({ opacity: 1 - playing.get(), transform: [{ scale: 1 - 0.3 * playing.get() }] }));

  const track = TRACKS[current];
  const cy = py(PIN.cardCY);
  const bar = PIN.player;
  const iconY = py(PIN.iconY);
  return (
    <GestureHandlerRootView style={styles.root}>
      <StatusBar style="light" hidden={false} />
      <View style={styles.root} onTouchStart={takeOver} onTouchEnd={scheduleResume} onTouchCancel={scheduleResume}>
        {TRACKS.map((t, i) => <Backdrop key={t.id} track={t} index={i} flow={flow} />)}
        {/* Soft contact shadows only, behind and under the stack (the pin's landscape crop edges and the dark pool under the bar are left out: on a full screen they read as a hard edge and a smudge). */}
        <Svg style={StyleSheet.absoluteFill} width={width} height={height}>
          <Defs>
            <RadialGradient id="halo" cx="50%" cy="50%" r="50%">
              <Stop offset="0" stopColor="#000" stopOpacity={PIN.halo.alpha} />
              <Stop offset="0.5" stopColor="#000" stopOpacity={PIN.halo.alpha * 0.75} />
              <Stop offset="1" stopColor="#000" stopOpacity={0} />
            </RadialGradient>
            <RadialGradient id="drop" cx="50%" cy="50%" r="50%">
              <Stop offset="0" stopColor="#000" stopOpacity={PIN.drop.alpha} />
              <Stop offset="0.5" stopColor="#000" stopOpacity={PIN.drop.alpha * 0.7} />
              <Stop offset="1" stopColor="#000" stopOpacity={0} />
            </RadialGradient>
            <RadialGradient id="shade" cx="50%" cy="50%" r="50%">
              <Stop offset="0" stopColor="#000" stopOpacity={PIN.shade.alpha} />
              <Stop offset="0.55" stopColor="#000" stopOpacity={PIN.shade.alpha * 0.8} />
              <Stop offset="1" stopColor="#000" stopOpacity={0} />
            </RadialGradient>
          </Defs>
          <Ellipse cx={px(PIN.halo.x)} cy={py(PIN.halo.y)} rx={PIN.halo.rx * S} ry={PIN.halo.ry * S} fill="url(#halo)" />
          <Ellipse cx={px(PIN.drop.x)} cy={py(PIN.drop.y)} rx={PIN.drop.rx * S} ry={PIN.drop.ry * S} fill="url(#drop)" />
          <Ellipse cx={px(PIN.shade.x)} cy={py(PIN.shade.y)} rx={PIN.shade.rx * S} ry={PIN.shade.ry * S} fill="url(#shade)" />
        </Svg>

        <GestureDetector gesture={pan}>
          <View style={[styles.flow, { top: cy - 140, height: 280 }]}>
            {TRACKS.map((t, i) => (
              <Card key={t.id} track={t} index={i} flow={flow} cx={px(PIN.cardCX)} cy={140} reduced={reduced}
                onPick={(k) => goTo(k)} />
            ))}
          </View>
        </GestureDetector>

        <View style={[styles.bar, { left: px(bar.x), top: py(bar.y), width: bar.w * S, height: bar.h * S, borderRadius: (bar.h * S) / 2 }]}>
          <BlurView intensity={40} tint="light" style={StyleSheet.absoluteFill} />
          <View style={[StyleSheet.absoluteFill, { backgroundColor: GLASS.bar }]} />
        </View>
        <IconButton x={px(PIN.prevX)} cy={iconY} label="Previous" press={prevPress} onPress={() => goTo(Math.round(flow.get()) - 1)}>
          <Rewind color={GLASS.ink} />
        </IconButton>
        <IconButton x={px(PIN.pauseX)} cy={iconY} label={isPlaying ? 'Pause' : 'Play'} onPress={togglePlay}>
          <View style={{ width: GLYPH.pause[0], height: GLYPH.pause[1] }}>
            <Animated.View style={[StyleSheet.absoluteFill, pauseStyle]}><PauseGlyph color={GLASS.ink} /></Animated.View>
            <Animated.View style={[StyleSheet.absoluteFill, playStyle]}><PlayGlyph color={GLASS.ink} /></Animated.View>
          </View>
        </IconButton>
        <IconButton x={px(PIN.nextX)} cy={iconY} label="Next" press={nextPress} onPress={() => goTo(Math.round(flow.get()) + 1)}>
          <Forward color={GLASS.ink} />
        </IconButton>

        <View style={[styles.capsule, { left: px(PIN.capsule.x), top: py(PIN.capsule.y), width: PIN.capsule.w * S, height: PIN.capsule.h * S, borderRadius: PIN.capsule.radius * S }]}>
          <Image source={track.cover} transition={180} contentFit="cover"
            style={{ position: 'absolute', left: (PIN.mini.x - PIN.capsule.x) * S, top: (PIN.mini.y - PIN.capsule.y) * S, width: PIN.mini.size * S, height: PIN.mini.size * S, borderRadius: PIN.mini.radius * S }} />
          <Animated.Text key={`t${track.id}`} entering={FadeIn.duration(TEXT_SWAP.inMs)} exiting={FadeOut.duration(TEXT_SWAP.outMs)} numberOfLines={1} style={[styles.miniTitle, { left: (PIN.miniTitle.x - PIN.capsule.x) * S, top: (PIN.miniTitle.y - PIN.capsule.y) * S - 6 }]}>{track.title}</Animated.Text>
          <Animated.Text key={`a${track.id}`} entering={FadeIn.duration(TEXT_SWAP.inMs)} exiting={FadeOut.duration(TEXT_SWAP.outMs)} numberOfLines={1} style={[styles.miniArtist, { left: (PIN.miniTitle.x - PIN.capsule.x) * S, top: (PIN.miniArtist.y - PIN.capsule.y) * S - 5 }]}>{track.artist}</Animated.Text>
          <View style={[styles.track, { left: (PIN.progress.x - PIN.capsule.x) * S, top: (PIN.progress.y - PIN.capsule.y) * S, width: PIN.progress.w * S }]}>
            <Animated.View style={[styles.trackFill, progress]} />
          </View>
          <View style={{ position: 'absolute', left: (PIN.waveX - PIN.capsule.x) * S - GLYPH.wave[0] / 2, top: (PIN.capsule.h * S - GLYPH.wave[1]) / 2 }}>
            <WaveGlyph color={GLASS.ink} />
          </View>
          <View style={{ position: 'absolute', left: (PIN.moreX - PIN.capsule.x) * S - 6, top: (PIN.capsule.h * S) / 2 - 6 }}>
            <MoreGlyph size={12} color={GLASS.ink} />
          </View>
        </View>

        <IconButton x={px(PIN.lyricsX)} cy={iconY} label="Lyrics"><LyricsGlyph color={GLASS.ink} /></IconButton>
        <IconButton x={px(PIN.listX)} cy={iconY} label="Queue"><ListGlyph color={GLASS.ink} /></IconButton>
        <IconButton x={px(PIN.volumeX)} cy={iconY} label="Volume"><VolumeGlyph color={GLASS.ink} /></IconButton>
      </View>
    </GestureHandlerRootView>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: '#2A1607' },
  sheen: { position: 'absolute', left: 0 },
  flow: { position: 'absolute', left: 0, right: 0 },
  card: { position: 'absolute', overflow: 'hidden', borderWidth: StyleSheet.hairlineWidth * 2, borderColor: GLASS.cardEdge },
  cardTitle: { position: 'absolute', left: 4, right: 4, textAlign: 'center', fontSize: PIN.card.titleSize * S, lineHeight: 16, fontWeight: '500', color: GLASS.ink },
  cardArtist: { position: 'absolute', left: 4, right: 4, textAlign: 'center', fontSize: PIN.card.artistSize * S, lineHeight: 14, color: GLASS.inkSoft },
  bar: { position: 'absolute', overflow: 'hidden', borderWidth: StyleSheet.hairlineWidth * 2, borderColor: GLASS.barEdge },
  iconBtn: { position: 'absolute', width: 32, height: 32, alignItems: 'center', justifyContent: 'center' },
  capsule: { position: 'absolute', backgroundColor: GLASS.capsule, overflow: 'hidden' },
  miniTitle: { position: 'absolute', fontSize: PIN.miniTitle.size * S, lineHeight: 11, fontWeight: '600', color: GLASS.ink, maxWidth: 80 },
  miniArtist: { position: 'absolute', fontSize: PIN.miniArtist.size * S, lineHeight: 10, color: GLASS.inkSoft, maxWidth: 80 },
  track: { position: 'absolute', height: 1.5, borderRadius: 1, backgroundColor: GLASS.track, overflow: 'hidden' },
  trackFill: { height: '100%', backgroundColor: GLASS.ink },
});
