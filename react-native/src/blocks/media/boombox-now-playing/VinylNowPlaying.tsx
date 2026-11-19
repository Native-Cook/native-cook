import * as Haptics from "expo-haptics";
import { LinearGradient } from "expo-linear-gradient";
import { StatusBar } from "expo-status-bar";
import { useCallback, useState } from "react";
import { StyleSheet, Text, TextInput, useWindowDimensions, View } from "react-native";
import type { TextInputProps } from "react-native";
import { Gesture, GestureDetector } from "react-native-gesture-handler";
import Animated, {
  cancelAnimation,
  Easing,
  useAnimatedProps,
  useAnimatedReaction,
  useAnimatedStyle,
  useDerivedValue,
  useFrameCallback,
  useSharedValue,
  withTiming,
  type SharedValue,
} from "react-native-reanimated";
import { useSafeAreaInsets } from "react-native-safe-area-context";
import { scheduleOnRN } from "react-native-worklets";

import { useReducedMotionPreference } from "@/shared/useReducedMotionPreference";

import { INK, NativeSlider, SECONDARY, Transport, VolumeRow } from "./VinylControls";
import {
  angleDelta,
  barAt,
  basis,
  CENTRED_FROM,
  CENTRED_TO,
  CLOSE_T,
  clock,
  IDLE_MS,
  LOOP,
  OMEGA,
  platterAt,
  playingAt,
  project,
  R_VINYL,
  sceneAt,
  SEEK_PER_RAD,
  SKIP_S,
  songAt,
  speedAt,
  STILL_T,
  TRACK_SECONDS,
  VOLUME,
  volumeAt,
} from "./vinylMotion";
import { PAPER_BOTTOM, PAPER_TOP } from "./vinylScene";
import { VinylShadows } from "./VinylShadows";
import { VinylStage3D } from "./VinylStage3D";

const MODE_AUTO = 0;
const MODE_MANUAL = 1;
const MODE_RETURN = 2;

const EASE = Easing.bezier(0.65, 0, 0.35, 1);
/** Play: 1 s up to speed; pause: 1.2 s to run down (the film's own curves). */
const SPIN_UP = { duration: 1000, easing: Easing.inOut(Easing.quad) };
const SPIN_DOWN = { duration: 1200, easing: Easing.out(Easing.quad) };
/** The ease back to the close after a touch while the record is out in the middle. */
const RETURN_MS = 1400;
/** A finger nearer the record's centre than this (points) turns nothing: the angle is too jumpy there. */
const SCRATCH_DEAD = 14;
/** Seconds of song between two detents of the rotary scrub. */
const TICK_S = 15;
/** Share of the screen the 3D stage covers, from the top. */
const STAGE = 0.63;
const FADE_FROM = "rgba(228,223,216,0)";

function tapFeel(): void {
  void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light);
}
function tickFeel(): void {
  void Haptics.selectionAsync();
}

const AnimatedTextInput = Animated.createAnimatedComponent(TextInput);

// ---------------------------------------------------------------- small pieces

function NowBar({ k, song, level, volume }: { k: number; song: SharedValue<number>; level: SharedValue<number>; volume: SharedValue<number> }) {
  const style = useAnimatedStyle(() => ({ transform: [{ scaleY: Math.max(0.15, barAt(k, song.get(), level.get()) * (0.4 + 0.6 * volume.get())) }] }));
  return <Animated.View style={[styles.bar, style]} />;
}

function Times({ song }: { song: SharedValue<number> }) {
  // the strings change once a second; a derived string only wakes the text props when it changes, so the text
  // (and the layout it triggers) is committed once a second instead of every frame
  const elapsedText = useDerivedValue(() => clock(song.get()));
  const remainingText = useDerivedValue(() => `-${clock(TRACK_SECONDS - song.get())}`);
  const elapsed = useAnimatedProps(() => ({ text: elapsedText.get() }) as unknown as TextInputProps);
  const remaining = useAnimatedProps(() => ({ text: remainingText.get() }) as unknown as TextInputProps);
  return (
    <View style={styles.timeRow}>
      <AnimatedTextInput animatedProps={elapsed} caretHidden defaultValue="0:00" editable={false} pointerEvents="none" style={[styles.time, styles.left]} underlineColorAndroid="transparent" />
      <AnimatedTextInput
        animatedProps={remaining}
        caretHidden
        defaultValue={`-${clock(TRACK_SECONDS)}`}
        editable={false}
        pointerEvents="none"
        style={[styles.time, styles.right]}
        underlineColorAndroid="transparent"
      />
    </View>
  );
}

/**
 * The native controls take React state; they follow the shared values in small steps. The state lives here, not in
 * the screen, so the film's slider steps (every frame while it scrubs, skips or goes back) re-render only these
 * controls, never the stage.
 */
function PlayerControls({
  column,
  scale,
  playing,
  song,
  volume,
  mode,
  scrubbing,
  initialProgress,
  onScrub,
  onScrubState,
  onVolume,
  onBack,
  onForward,
  onToggle,
}: {
  column: number;
  scale: number;
  playing: SharedValue<boolean>;
  song: SharedValue<number>;
  volume: SharedValue<number>;
  mode: SharedValue<number>;
  scrubbing: SharedValue<boolean>;
  initialProgress: number;
  onScrub: (f: number) => void;
  onScrubState: (active: boolean) => void;
  onVolume: (v: number) => void;
  onBack: () => void;
  onForward: () => void;
  onToggle: () => void;
}) {
  const [isPlaying, setIsPlaying] = useState(false);
  const [progress, setProgress] = useState(initialProgress);
  const [vol, setVol] = useState(VOLUME);
  useAnimatedReaction(
    () => playing.get(),
    (v, prev) => {
      if (v !== prev) scheduleOnRN(setIsPlaying, v);
    },
  );
  useAnimatedReaction(
    () => (scrubbing.get() ? -1 : Math.round((song.get() / TRACK_SECONDS) * 400)),
    (v, prev) => {
      if (v >= 0 && v !== prev) scheduleOnRN(setProgress, v / 400);
    },
  );
  // the film (and the return) move the volume slider; while a finger has it, the slider leads
  useAnimatedReaction(
    () => (mode.get() === MODE_MANUAL ? -1 : Math.round(volume.get() * 200)),
    (v, prev) => {
      if (v >= 0 && v !== prev) scheduleOnRN(setVol, v / 200);
    },
  );
  const changeVolume = useCallback(
    (v: number) => {
      setVol(v);
      onVolume(v);
    },
    [onVolume],
  );
  return (
    <>
      <View style={{ marginTop: 14 * scale, gap: 4 * scale }}>
        <NativeSlider label="Song position" onChange={onScrub} onEditing={onScrubState} value={progress} width={column} />
        <Times song={song} />
      </View>
      <View style={{ marginTop: 8 * scale }}>
        <Transport onBack={onBack} onForward={onForward} onToggle={onToggle} playing={isPlaying} scale={scale} />
      </View>
      <View style={{ marginTop: 8 * scale }}>
        <VolumeRow onVolume={changeVolume} scale={scale} volume={vol} width={column} />
      </View>
    </>
  );
}

// ---------------------------------------------------------------- the screen

export type VinylNowPlayingProps = { autoPlay?: boolean };

export function VinylNowPlaying({ autoPlay = true }: VinylNowPlayingProps) {
  const { width, height } = useWindowDimensions();
  const insets = useSafeAreaInsets();
  const reduced = useReducedMotionPreference();
  const film = !reduced && autoPlay;
  const scale = Math.min(1.1, Math.max(0.9, width / 393));
  const column = Math.min(width - 48 * scale, 460);
  /** The 3D stage stops above the player (its title sits at about 0.66 of the screen). */
  const stageH = Math.round(height * STAGE);
  const startT = film ? 0 : STILL_T;

  // ---- one clock on the UI thread; the 3D stage, the shadows and the player read these
  const t = useSharedValue(startT);
  /** Stage time: the film's time, held while a finger has the record, or eased by the return. */
  const stage = useSharedValue(startT);
  /** 0..1: the ease toward the close (CLOSE_T) after a touch while the record is out in the middle. */
  const ret = useSharedValue(0);
  const mode = useSharedValue(film ? MODE_AUTO : MODE_MANUAL);
  const loopStart = useSharedValue(-1);
  /** Where the film picks up after a return (0, or CLOSE_T). */
  const resumeAt = useSharedValue(0);
  const ready = useSharedValue(false);
  const lastActivity = useSharedValue(-1);
  const platter = useSharedValue(platterAt(startT));
  /** The film's spin is platterAt(t) + platterBase: the record keeps the angle a listener left it at. */
  const platterBase = useSharedValue(0);
  const song = useSharedValue(film ? 0 : songAt(STILL_T));
  const speed = useSharedValue(0);
  const level = useSharedValue(0);
  const playing = useSharedValue(false);
  const volume = useSharedValue(VOLUME);
  const dragging = useSharedValue(false);
  const scrubbing = useSharedValue(false);
  const seeking = useSharedValue(false);
  const lastAngle = useSharedValue(0);
  const centreX = useSharedValue(0);
  const centreY = useSharedValue(0);
  const reach = useSharedValue(0);

  /** What the 3D stage needs to run the film on its own clock (see VinylStage3D); it changes only at hand-overs. */
  const anchor = useDerivedValue(() => {
    const auto = mode.get() === MODE_AUTO && (loopStart.get() >= 0 || !ready.get());
    return [auto ? 1 : 0, loopStart.get(), platterBase.get()];
  });
  // a detent every 15 s of song while a finger turns the record
  useAnimatedReaction(
    () => Math.floor(song.get() / TICK_S),
    (v, prev) => {
      if (prev != null && v !== prev && dragging.get()) scheduleOnRN(tickFeel);
    },
  );

  useFrameCallback((frame) => {
    "worklet";
    const now = frame.timestamp;
    const dt = Math.min(frame.timeSincePreviousFrame ?? 16, 50) / 1000;
    const m = mode.get();
    if (m === MODE_AUTO) {
      if (!film) {
        mode.set(MODE_MANUAL);
        return;
      }
      if (!ready.get()) {
        t.set(0);
      } else {
        if (loopStart.get() < 0) {
          loopStart.set(now - resumeAt.get() * 1000);
          resumeAt.set(0);
        }
        t.set(((now - loopStart.get()) / 1000) % LOOP);
      }
      const tt = t.get();
      stage.set(tt);
      ret.set(0);
      song.set(songAt(tt));
      platter.set(platterBase.get() + platterAt(tt));
      speed.set(speedAt(tt));
      level.set(speedAt(tt));
      volume.set(volumeAt(tt));
      const p = playingAt(tt);
      if (p !== playing.get()) playing.set(p);
      return;
    }
    // the listener's record: the motor turns it unless a finger holds it; the song plays at the record's speed
    if (!dragging.get()) platter.set(platter.get() + OMEGA * speed.get() * dt);
    level.set(speed.get());
    if (!dragging.get() && !seeking.get() && !scrubbing.get() && speed.get() > 0) song.set(Math.min(TRACK_SECONDS, song.get() + speed.get() * dt));
    if (m !== MODE_MANUAL) return;
    const busy = dragging.get() || scrubbing.get() || seeking.get();
    if (lastActivity.get() < 0 || busy) {
      lastActivity.set(now);
      return;
    }
    if (!film || now - lastActivity.get() < IDLE_MS) return;
    // 2 s after the last touch the record stops and the scene finds its way back into the film: from the middle it
    // eases to the close (stopped, 0:00) and the film carries on putting it away; nearer the sleeve it runs the
    // film's own slide back to the poster
    mode.set(MODE_RETURN);
    playing.set(false);
    speed.set(withTiming(0, SPIN_DOWN));
    song.set(withTiming(0, { duration: RETURN_MS, easing: EASE }));
    volume.set(withTiming(VOLUME, { duration: RETURN_MS }));
    const s0 = stage.get();
    if (s0 >= CENTRED_FROM && s0 <= CENTRED_TO) {
      ret.set(
        withTiming(1, { duration: RETURN_MS, easing: EASE }, (finished) => {
          "worklet";
          if (!finished) return;
          stage.set(CLOSE_T);
          ret.set(0);
          platterBase.set(platter.get() - platterAt(CLOSE_T));
          resumeAt.set(CLOSE_T);
          loopStart.set(-1);
          mode.set(MODE_AUTO);
        }),
      );
    } else {
      const toEnd = s0 > CENTRED_TO;
      const target = toEnd ? LOOP : 0;
      const ms = Math.max(SPIN_DOWN.duration, Math.abs(target - s0) * 800);
      stage.set(
        withTiming(target, { duration: ms, easing: EASE }, (finished) => {
          "worklet";
          if (!finished) return;
          stage.set(0);
          platterBase.set(platter.get());
          resumeAt.set(0);
          loopStart.set(-1);
          mode.set(MODE_AUTO);
        }),
      );
    }
  });

  /** Any touch takes the record over where the film is; the film's state carries on as the listener's own. */
  const takeOver = useCallback(() => {
    "worklet";
    const m = mode.get();
    if (m === MODE_RETURN) {
      cancelAnimation(ret);
      cancelAnimation(stage);
      cancelAnimation(speed);
      cancelAnimation(song);
      cancelAnimation(volume);
    }
    if (m === MODE_AUTO) {
      const tt = t.get();
      // playing stays playing (up to full speed), a run-down finishes running down
      if (playingAt(tt)) speed.set(withTiming(1, { duration: Math.round(1000 * (1 - speedAt(tt))) + 1, easing: EASE }));
      else if (speed.get() > 0) speed.set(withTiming(0, SPIN_DOWN));
    }
    mode.set(MODE_MANUAL);
    lastActivity.set(-1);
  }, [lastActivity, mode, ret, song, speed, stage, t, volume]);

  /** Bring the record out of its sleeve into the middle (the film's own slide), if it is not there yet. */
  const bringOut = useCallback(() => {
    "worklet";
    const s0 = stage.get();
    if (s0 < CENTRED_FROM) stage.set(withTiming(STILL_T, { duration: Math.max(500, (STILL_T - s0) * 700), easing: EASE }));
    else if (s0 > CENTRED_TO) stage.set(withTiming(CENTRED_TO - 0.1, { duration: Math.max(500, (s0 - CENTRED_TO) * 700), easing: EASE }));
  }, [stage]);

  const toggle = useCallback(() => {
    tapFeel();
    takeOver();
    if (playing.get()) {
      // pause: the record runs down
      playing.set(false);
      speed.set(withTiming(0, SPIN_DOWN));
    } else {
      playing.set(true);
      bringOut();
      speed.set(withTiming(1, SPIN_UP));
    }
  }, [bringOut, playing, speed, takeOver]);

  const seek = useCallback(
    (to: number) => {
      tapFeel();
      takeOver();
      seeking.set(true);
      song.set(
        withTiming(to, { duration: 500, easing: EASE }, () => {
          "worklet";
          seeking.set(false);
        }),
      );
    },
    [seeking, song, takeOver],
  );
  const back = useCallback(() => seek(0), [seek]);
  const forward = useCallback(() => seek(Math.min(TRACK_SECONDS - 1, song.get() + SKIP_S)), [seek, song]);
  const scrub = useCallback(
    (f: number) => {
      takeOver();
      cancelAnimation(song);
      song.set(f * TRACK_SECONDS);
    },
    [song, takeOver],
  );
  const scrubState = useCallback(
    (active: boolean) => {
      scrubbing.set(active);
      if (!active) lastActivity.set(-1);
    },
    [lastActivity, scrubbing],
  );
  const setVolume = useCallback(
    (v: number) => {
      volume.set(v);
      lastActivity.set(-1);
    },
    [lastActivity, volume],
  );

  // rotary scrub: a finger on the record turns it about its centre (where the stage's camera puts it), and the song
  // moves with it, a minute a turn
  const rotary = Gesture.Pan()
    .minDistance(6)
    .onStart((e) => {
      "worklet";
      takeOver();
      dragging.set(true);
      cancelAnimation(song);
      const sc = [0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0];
      sceneAt(stage.get(), ret.get(), sc);
      const c = [sc[8], sc[9], sc[10], sc[11], sc[12], sc[13]];
      const b = [0, 0, 0, 0, 0, 0, 0, 0];
      basis(c, b);
      const q = [0, 0, 0];
      project(c, b, width, stageH, sc[0], sc[1], sc[2], q);
      centreX.set(q[0]);
      centreY.set(q[1]);
      reach.set(R_VINYL * q[2] * 1.25);
      lastAngle.set(Math.atan2(e.y - q[1], e.x - q[0]));
    })
    .onUpdate((e) => {
      "worklet";
      const dx = e.x - centreX.get();
      const dy = e.y - centreY.get();
      const a = Math.atan2(dy, dx);
      const d = angleDelta(a, lastAngle.get());
      lastAngle.set(a);
      const r2 = dx * dx + dy * dy;
      if (r2 < SCRATCH_DEAD * SCRATCH_DEAD || r2 > reach.get() * reach.get()) return;
      // screen y points down, so a growing angle is clockwise, the way the record plays
      platter.set(platter.get() + d);
      song.set(Math.max(0, Math.min(TRACK_SECONDS, song.get() + d * SEEK_PER_RAD)));
    })
    .onFinalize(() => {
      "worklet";
      dragging.set(false);
      lastActivity.set(-1);
    });
  const tap = Gesture.Tap()
    .maxDistance(8)
    .runOnJS(true)
    .onEnd((_e, success) => {
      if (success) toggle();
    });

  const onStageReady = useCallback(() => ready.set(true), [ready]);

  return (
    <View style={styles.screen}>
      <StatusBar style="dark" />
      <LinearGradient colors={[PAPER_TOP, PAPER_BOTTOM, PAPER_BOTTOM]} locations={[0, 0.55, 1]} pointerEvents="none" style={StyleSheet.absoluteFill} />
      <GestureDetector gesture={Gesture.Race(rotary, tap)}>
        <View
          accessible
          accessibilityHint="Turn the record with a finger to move through the song, tap to play or pause"
          accessibilityLabel="A vinyl record and its sleeve floating in a photo studio"
          style={[styles.stage, { height: stageH }]}
        >
          <VinylShadows height={stageH} ret={ret} stage={stage} width={width} />
          <VinylStage3D anchor={anchor} height={stageH} startT={startT} onReady={onStageReady} platter={platter} ret={ret} stage={stage} width={width} />
          {/* the stage ends above the player: a close shot fades into the paper instead of running behind the type */}
          <LinearGradient colors={[FADE_FROM, PAPER_BOTTOM]} pointerEvents="none" style={[styles.fade, { height: 56 * scale }]} />
        </View>
      </GestureDetector>
      <View pointerEvents="none" style={[styles.header, { top: insets.top + 6 * scale }]}>
        <View style={[styles.grabber, { width: 36 * scale, height: 5 * scale, borderRadius: 3 * scale }]} />
        <Text style={[styles.from, { fontSize: 13 * scale, marginTop: 10 * scale }]}>Playing from Night Drive</Text>
      </View>
      <View pointerEvents="box-none" style={{ position: "absolute", left: (width - column) / 2, width: column, bottom: insets.bottom + 14 * scale }}>
        <View pointerEvents="none">
          <Text adjustsFontSizeToFit minimumFontScale={0.75} numberOfLines={1} style={[styles.title, { fontSize: 28 * scale, lineHeight: 34 * scale }]}>
            Overpass Lights
          </Text>
          <View style={[styles.artistRow, { marginTop: 2 * scale, gap: 8 * scale }]}>
            <Text numberOfLines={1} style={[styles.artist, { fontSize: 20 * scale, lineHeight: 25 * scale }]}>
              Juno Vale
            </Text>
            <View style={styles.bars}>
              <NowBar k={0} level={level} song={song} volume={volume} />
              <NowBar k={1} level={level} song={song} volume={volume} />
              <NowBar k={2} level={level} song={song} volume={volume} />
            </View>
          </View>
        </View>
        <PlayerControls
          column={column}
          initialProgress={film ? 0 : songAt(STILL_T) / TRACK_SECONDS}
          mode={mode}
          onBack={back}
          onForward={forward}
          onScrub={scrub}
          onScrubState={scrubState}
          onToggle={toggle}
          onVolume={setVolume}
          playing={playing}
          scale={scale}
          scrubbing={scrubbing}
          song={song}
          volume={volume}
        />
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: PAPER_TOP },
  stage: { position: "absolute", left: 0, right: 0, top: 0, overflow: "hidden" },
  fade: { position: "absolute", left: 0, right: 0, bottom: 0 },
  header: { position: "absolute", left: 0, right: 0, alignItems: "center" },
  grabber: { backgroundColor: "rgba(29,28,26,0.18)" },
  from: { color: SECONDARY, fontWeight: "600" },
  title: { color: INK, fontWeight: "700", letterSpacing: -0.4 },
  artistRow: { flexDirection: "row", alignItems: "center" },
  artist: { color: SECONDARY, fontWeight: "400" },
  bars: { flexDirection: "row", alignItems: "flex-end", gap: 2, height: 13 },
  bar: { width: 3, height: 13, borderRadius: 1.5, backgroundColor: "#E4572E", transformOrigin: "bottom" },
  timeRow: { flexDirection: "row", justifyContent: "space-between" },
  time: { color: SECONDARY, fontSize: 12, fontWeight: "600", padding: 0, margin: 0, fontVariant: ["tabular-nums"], width: 64 },
  left: { textAlign: "left" },
  right: { textAlign: "right" },
});
