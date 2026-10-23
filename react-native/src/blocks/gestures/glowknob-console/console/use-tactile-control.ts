import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { AppState } from "react-native";
import { useIsFocused } from "../navigation";
import { Gesture } from "react-native-gesture-handler";
import {
  cancelAnimation,
  Easing,
  ReduceMotion,
  useAnimatedReaction,
  useDerivedValue,
  useSharedValue,
  withDecay,
  withDelay,
  withSpring,
  withTiming,
  type SharedValue,
} from "react-native-reanimated";
import { scheduleOnRN, scheduleOnUI } from "react-native-worklets";
import * as Haptics from "expo-haptics";
import { useAudioPlayer } from "expo-audio";
import { useMotionPrefs } from "../ui";
import { motion } from "../theme";
import { tactile } from "./tactile-theme";
import { allEffects, type Effects } from "./effects";
import { useMeniscus } from "./use-meniscus";
import { useReadoutLegibility } from "./use-readout-legibility";
import {
  fillForLevel,
  levelForFill,
  referenceDuration,
  referencePose,
  referenceGeometry,
  rubberBand,
} from "./reference-motion";

export type Channel = 0 | 1;

// A full level range turns the knob 1.5π: at the specimen's scale that arc is
// close to the 160pt drag, so the grooves travel with the finger.
const DIAL_TURN = Math.PI * 1.5;
const DIAL_DRAG = 160;
// Visual give past 0/100 on the knob, in level units (≈21° at most).
const DIAL_GIVE = 0.08;
// Interrupting a replay or handoff morphs the last visible frame into the
// new source rather than cutting to it. Both ends are on screen, so this is
// ease-in-out: a strong ease-out spent most of the change in the first frame.
const HANDOFF = 240;
const easeInOut = Easing.bezier(0.77, 0, 0.175, 1);
// Mode switch: the light contracts into the end cap (ease-in-out, so it does
// not cut), the color transfers at the trough, then it blooms out to the new
// level with a soft start and a long tail. 380ms: long enough to read as one
// gesture, still under the platform push.
const SWITCH = 380;
const TROUGH = 150 / SWITCH;
const contract = Easing.bezier(0.65, 0, 0.35, 1).factory();
const bloom = Easing.bezier(0.3, 0, 0.15, 1).factory();
// Fill fraction at which the liquid fill is exactly the round end cap.
const CAP = referenceGeometry.track.height / referenceGeometry.track.width;
// Power-on: the light rises like liquid from 0 with a soft settle.
const rise = Easing.bezier(0.22, 1, 0.36, 1).factory();
const BOOT = 900;
// Knob detents: 28 grooves per revolution.
const GROOVE = (Math.PI * 2) / 28;
// Fine scrub: finger this far below the track switches to ¼ speed.
const FINE_DROP = 32;
const DETENT_SOUND = require("../assets/detent.wav");

type Frame = {
  fill: number;
  light: number;
  blur: number;
  number: number;
  label: number;
  readout: number;
};

// Skips the lerp when no handoff is running, so the live value is exact.
function fade(live: number, last: number, amount: number) {
  "worklet";
  return amount > 0 ? live + (last - live) * amount : live;
}

function clamp01(level: number) {
  "worklet";
  return Math.max(0, Math.min(1, level));
}

export function useTactileControl(
  disabled: boolean,
  gentle: boolean,
  fx: Effects = allEffects,
) {
  "use no memo"; // RNGH stores callbacks; refs below are used only in events/effects.
  const prefs = useMotionPrefs();
  const reduced = prefs.reduced || gentle;
  const focused = useIsFocused();
  const [foreground, setForeground] = useState(
    AppState.currentState === "active",
  );
  const enabled = !disabled && focused && foreground;
  const [mode, setMode] = useState<Channel>(0);
  const [snapshot, setSnapshot] = useState([52, 53]);
  const [playing, setPlaying] = useState(false);
  const mounted = useRef(true);
  const runId = useRef(0);
  const brightness = useSharedValue(0.52),
    volume = useSharedValue(0.53);
  // Committed levels. The visible levels may still be gliding toward them.
  const goal = useSharedValue([0.52, 0.53]);
  const channel = useSharedValue<Channel>(0);
  const mix = useSharedValue(0),
    reveal = useSharedValue(1),
    selector = useSharedValue(0);
  const outgoingFill = useSharedValue(fillForLevel(0.52));
  const outgoingNumber = useSharedValue(52);
  const trackPress = useSharedValue(0),
    dialPress = useSharedValue(0),
    brightPress = useSharedValue(0),
    volumePress = useSharedValue(0),
    width = useSharedValue(1);
  const dragStart = useSharedValue(0),
    dragOwner = useSharedValue(0);
  const dialAngle = useSharedValue(0),
    dialGive = useSharedValue(0);
  const available = useSharedValue(enabled);
  const active = useSharedValue(disabled ? 0 : 1);
  const replayTime = useSharedValue(-1),
    replayRunning = useSharedValue(false),
    replayId = useSharedValue(0);
  const blend = useSharedValue(0),
    blendFill = useSharedValue(false);
  const from = useSharedValue<Frame>({
    fill: fillForLevel(0.52),
    light: 1,
    blur: 0,
    number: 1,
    label: 1,
    readout: 52,
  });
  // Effect switches mirrored to the UI thread for gestures and reactions.
  const fxs = useSharedValue(fx);
  const boot = useSharedValue(fx.powerOn && !reduced ? 0 : 1);
  const spinning = useSharedValue(false),
    lastTick = useSharedValue(0);
  const fine = useSharedValue(false),
    fineAnchor = useSharedValue({ x: 0, level: 0 }),
    fineShown = useSharedValue(0),
    magnetHeld = useSharedValue(false);
  const pose = useDerivedValue(() => referencePose(replayTime.get(), reduced));
  const value = useDerivedValue(() =>
    channel.get() === 0 ? brightness.get() : volume.get(),
  );
  const sourceFill = useDerivedValue(() => {
    if (replayTime.get() >= 0) return pose.get().fill;
    const p = reveal.get();
    const target = fillForLevel(value.get());
    if (p >= 1) return target;
    // Trough is the round cap, never the thin liquid slice below it.
    const trough = Math.min(outgoingFill.get(), CAP);
    if (p < TROUGH) {
      const k = contract(p / TROUGH);
      return outgoingFill.get() + (trough - outgoingFill.get()) * k;
    }
    return trough + (target - trough) * bloom((p - TROUGH) / (1 - TROUGH));
  });
  // A finger on the light always wins over the fade: the endpoint stays under it.
  const fill = useDerivedValue(() => {
    const b = boot.get();
    const k = b >= 1 ? 1 : rise(b);
    const live = sourceFill.get();
    if (!blendFill.get() || dragOwner.get() !== 0) return live * k;
    return fade(live, from.get().fill, blend.get()) * k;
  });
  const slosh = useMeniscus(fill, fx.meniscus && !reduced);
  const lightMix = useDerivedValue(() =>
    replayTime.get() >= 0 ? pose.get().mix : mix.get(),
  );
  const lightAlpha = useDerivedValue(() => {
    const live = replayTime.get() >= 0 ? pose.get().light : 1;
    return (
      fade(live, from.get().light, blend.get()) * Math.min(1, boot.get() * 5)
    );
  });
  const lightBlur = useDerivedValue(() => {
    if (reduced) return 0;
    let live = 0;
    if (replayTime.get() >= 0) live = pose.get().blur;
    else {
      // A light softening that peaks at the trough; kept low so the color
      // stays saturated while it blooms.
      const p = reveal.get();
      live =
        p < TROUGH ? (p / TROUGH) * 7 : Math.max(0, 1 - (p - TROUGH) / 0.4) * 7;
    }
    return fade(live, from.get().blur, blend.get());
  });
  const numberAlpha = useDerivedValue(() => {
    let live = 1;
    if (replayTime.get() >= 0) live = pose.get().number;
    else if (!reduced) {
      // Out while contracting, swapped unseen at the trough, back in while
      // the new light blooms — the old channel's number never rides along.
      const p = reveal.get();
      live =
        p < TROUGH
          ? Math.max(0, 1 - p / (TROUGH * 0.8))
          : Math.max(0, Math.min(1, (p - TROUGH - 0.12) / 0.4));
    }
    return (
      fade(live, from.get().number, blend.get()) * Math.min(1, boot.get() * 4)
    );
  });
  const readout = useDerivedValue(() => {
    // Power-on: show 0, then the number rolls up to the level in one move.
    if (boot.get() < 0.12) return 0;
    if (blend.get() > 0.5) return from.get().readout;
    if (replayTime.get() >= 0) return pose.get().display;
    return reveal.get() < TROUGH
      ? outgoingNumber.get()
      : Math.round(clamp01(value.get()) * 100);
  });
  // The mode title is one NumericText that swaps its own glyphs on a switch,
  // so live it stays fully visible. The replay keeps its sampled visibility
  // (the reference fades the title out and back in around the transfer).
  const labelAlpha = useDerivedValue(() => {
    const live =
      replayTime.get() >= 0
        ? Math.max(pose.get().brightnessLabel, pose.get().volumeLabel)
        : 1;
    return fade(live, from.get().label, blend.get());
  });
  // Selector glyphs/wells answer the press immediately; the light's color
  // transfer is choreographed separately.
  const selectorMix = useDerivedValue(() =>
    replayTime.get() >= 0 ? pose.get().mix : selector.get(),
  );
  // The number rolls for every in-place change, including under a dragging
  // finger, but not for a channel swap (that one is hidden under the fade).
  const numberRolls = useDerivedValue(() => reveal.get() >= 1);
  // A finger is moving the level continuously: rolls must be short enough to
  // settle between steps or the digits stay blurred for the whole drag.
  const numberQuick = useDerivedValue(
    () => dragOwner.get() === 1 || dragOwner.get() === 2,
  );
  const { numberRun, numberInk } = useReadoutLegibility(readout, fill, reduced);
  const dialRotation = useDerivedValue(
    () => (dialAngle.get() + dialGive.get()) * DIAL_TURN,
  );
  const glowPress = useDerivedValue(() =>
    Math.max(trackPress.get(), dialPress.get()),
  );

  const publish = useCallback((b: number, v: number) => {
    if (mounted.current)
      setSnapshot([Math.round(b * 100), Math.round(v * 100)]);
  }, []);
  const publishReplay = useCallback(
    (id: number, next: Channel, b: number, v: number) => {
      if (!mounted.current || id !== runId.current) return;
      setMode(next);
      publish(b, v);
    },
    [publish],
  );
  const finishReplay = useCallback((id: number) => {
    if (mounted.current && id === runId.current) setPlaying(false);
  }, []);
  const feedback = useCallback(() => {
    if (prefs.haptics) void Haptics.selectionAsync().catch(() => {});
  }, [prefs.haptics]);
  const edgeFeedback = useCallback(() => {
    if (prefs.haptics)
      void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(
        () => {},
      );
  }, [prefs.haptics]);
  // Detent click (opt-in): restarted per tick so rapid detents stay distinct.
  // The app's global audio mode is left alone; the click follows it.
  const click = useAudioPlayer(DETENT_SOUND);
  const tickFeedback = useCallback(() => {
    if (prefs.haptics) void Haptics.selectionAsync().catch(() => {});
    if (fx.sound) {
      click.seekTo(0);
      click.play();
    }
  }, [prefs.haptics, fx.sound, click]);
  const softFeedback = useCallback(() => {
    if (prefs.haptics)
      void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Soft).catch(
        () => {},
      );
  }, [prefs.haptics]);
  const stopPlayback = useCallback(() => {
    runId.current += 1;
    if (mounted.current) setPlaying(false);
  }, []);

  const setLevel = useCallback(
    (
      ch: number,
      target: number,
      duration: number,
      easing: typeof motion.easeOut,
    ) => {
      "worklet";
      const level = clamp01(target);
      const [b, v] = goal.get();
      goal.set(ch === 0 ? [level, v] : [b, level]);
      (ch === 0 ? brightness : volume).set(
        duration > 0 ? withTiming(level, { duration, easing }) : level,
      );
    },
    [goal, brightness, volume],
  );
  const publishGoal = useCallback(() => {
    "worklet";
    const [b, v] = goal.get();
    scheduleOnRN(publish, b, v);
  }, [goal, publish]);

  // Endless encoder: grooves turn only when the level changes inside one
  // channel, so switching modes never spins the knob.
  useAnimatedReaction(
    () => [channel.get(), value.get()],
    (next, previous) => {
      if (previous && next[0] === previous[0])
        dialAngle.set(dialAngle.get() + next[1] - previous[1]);
    },
  );
  // One light tick when a drag reaches 0 or 100; never at drag start.
  useAnimatedReaction(
    () => {
      const owner = dragOwner.get();
      if (owner !== 1 && owner !== 2) return 9;
      const v = value.get();
      return v <= 0 ? -1 : v >= 1 ? 1 : 0;
    },
    (edge, previous) => {
      if (edge !== 0 && edge !== 9 && previous === 0)
        scheduleOnRN(edgeFeedback);
    },
  );
  // Trough haptic: one soft tick in the frame the color transfer starts.
  useAnimatedReaction(
    () => reveal.get() >= TROUGH,
    (past, previous) => {
      if (
        past &&
        previous === false &&
        reveal.get() < 0.9 &&
        fxs.get().troughHaptic &&
        !reduced
      )
        scheduleOnRN(softFeedback);
    },
  );
  // Crown detents: a tick each time a groove passes under the finger or
  // during an inertial spin; at most ~33 ticks a second.
  useAnimatedReaction(
    () => Math.floor(((dialAngle.get() + dialGive.get()) * DIAL_TURN) / GROOVE),
    (groove, previous) => {
      if (previous === null || groove === previous || !fxs.get().crown) return;
      if (dragOwner.get() !== 2 && !spinning.get()) return;
      const now = Date.now();
      if (now - lastTick.get() < 30) return;
      lastTick.set(now);
      scheduleOnRN(tickFeedback);
    },
  );
  useEffect(() => {
    fxs.set(fx);
  }, [fx, fxs]);
  useAnimatedReaction(
    () => (replayRunning.get() ? pose.get().phase : -1),
    (phase, previous) => {
      if (phase < 0 || phase === previous) return;
      const frame = pose.get();
      const next = frame.mode as Channel;
      const switching = channel.get() !== next;
      channel.set(next);
      // The sampler draws the light; the level glides so the knob turns with
      // it instead of jumping at the phase boundary.
      setLevel(
        next,
        frame.level,
        switching || reduced ? 0 : motion.hero,
        easeInOut,
      );
      const [b, v] = goal.get();
      scheduleOnRN(publishReplay, replayId.get(), next, b, v);
    },
  );

  const capture = useCallback(
    (withFill: boolean) => {
      "worklet";
      from.set({
        fill: fill.get(),
        light: lightAlpha.get(),
        blur: lightBlur.get(),
        number: numberAlpha.get(),
        label: labelAlpha.get(),
        readout: readout.get(),
      });
      blendFill.set(withFill && !reduced);
      blend.set(1);
      // Opacity only under Reduce Motion; fill movement is excluded above.
      blend.set(
        withTiming(0, {
          duration: HANDOFF,
          easing: easeInOut,
          reduceMotion: ReduceMotion.Never,
        }),
      );
    },
    [
      from,
      fill,
      lightAlpha,
      lightBlur,
      numberAlpha,
      labelAlpha,
      readout,
      blendFill,
      blend,
      reduced,
    ],
  );
  const cancelVisuals = useCallback(
    (withFill: boolean) => {
      "worklet";
      const interrupted =
        replayTime.get() >= 0 || reveal.get() < 1 || blend.get() > 0;
      const shownMix = lightMix.get();
      if (interrupted) capture(withFill);
      cancelAnimation(replayTime);
      replayRunning.set(false);
      replayTime.set(-1);
      cancelAnimation(reveal);
      cancelAnimation(mix);
      reveal.set(1);
      mix.set(interrupted ? shownMix : channel.get());
      if (interrupted)
        mix.set(
          withTiming(channel.get(), {
            duration: HANDOFF,
            easing: easeInOut,
            reduceMotion: ReduceMotion.Never,
          }),
        );
      selector.set(
        withTiming(channel.get(), {
          duration: motion.fast,
          easing: motion.easeOut,
          reduceMotion: ReduceMotion.Never,
        }),
      );
      dragOwner.set(0);
    },
    [
      selector,
      replayTime,
      replayRunning,
      reveal,
      blend,
      lightMix,
      capture,
      mix,
      channel,
      dragOwner,
    ],
  );
  const quiet = useCallback(() => {
    "worklet";
    cancelVisuals(true);
    for (const sv of [trackPress, dialPress, dialGive]) {
      cancelAnimation(sv);
      sv.set(0);
    }
  }, [cancelVisuals, trackPress, dialPress, dialGive]);
  // Feedback on touch-down, before the recognizer activates or a tap completes.
  const pressIn = useCallback(
    (target: SharedValue<number>) => {
      "worklet";
      if (!available.get() || reduced) return;
      target.set(
        withTiming(1, { duration: motion.press, easing: motion.easeOut }),
      );
    },
    [available, reduced],
  );
  const pressOut = useCallback(
    (target: SharedValue<number>) => {
      "worklet";
      target.set(reduced ? 0 : withSpring(0, tactile.release));
    },
    [reduced],
  );
  const settle = useCallback(() => {
    "worklet";
    dragOwner.set(0);
    publishGoal();
  }, [dragOwner, publishGoal]);
  const begin = useCallback(
    (owner: number) => {
      "worklet";
      cancelVisuals(true);
      spinning.set(false);
      fine.set(false);
      magnetHeld.set(false);
      dragOwner.set(owner);
      dragStart.set(value.get());
      scheduleOnRN(stopPlayback);
      scheduleOnRN(feedback);
    },
    [
      cancelVisuals,
      spinning,
      fine,
      magnetHeld,
      dragOwner,
      dragStart,
      value,
      stopPlayback,
      feedback,
    ],
  );
  const update = useCallback(
    (next: number) => {
      "worklet";
      if (!available.get()) return;
      let level = next;
      // Magnet at 50%: snaps within ±1.8% with one light haptic on entry.
      if (fxs.get().magnet) {
        const near = Math.abs(level - 0.5) < 0.018;
        if (near) level = 0.5;
        if (near && !magnetHeld.get()) scheduleOnRN(edgeFeedback);
        magnetHeld.set(near);
      }
      setLevel(channel.get(), level, 0, motion.easeOut);
    },
    [available, channel, setLevel, fxs, magnetHeld, edgeFeedback],
  );
  // Track position → level, switching to ¼-speed relative mode once the
  // finger drops below the track (fine scrub). Stays fine until release.
  const trackLevel = useCallback(
    (x: number, dy: number) => {
      "worklet";
      const absolute = levelForFill(x / width.get());
      if (!fxs.get().fineScrub) return absolute;
      if (!fine.get() && dy > FINE_DROP) {
        fine.set(true);
        fineAnchor.set({ x, level: value.get() });
        fineShown.set(withTiming(1, { duration: motion.fast }));
        scheduleOnRN(tickFeedback);
      }
      if (!fine.get()) return absolute;
      const anchor = fineAnchor.get();
      return anchor.level + ((x - anchor.x) / width.get()) * 0.25;
    },
    [width, fxs, fine, fineAnchor, fineShown, value, tickFeedback],
  );

  // Gesture builders register callbacks; the compiler cannot infer the deferred RN calls.
  /* eslint-disable react-hooks/refs */
  const trackGesture = useMemo(
    () =>
      Gesture.Race(
        Gesture.Pan()
          .enabled(enabled)
          .maxPointers(1)
          .onTouchesDown((event, manager) => {
            if (event.numberOfTouches > 1) manager.fail();
          })
          .activeOffsetX([-4, 4])
          // Fine scrub needs the finger free to travel down.
          .failOffsetY(fx.fineScrub ? [-12, 400] : [-12, 12])
          .onBegin(() => pressIn(trackPress))
          .onStart((e) => {
            if (available.get()) {
              begin(1);
              update(trackLevel(e.x, e.translationY));
            }
          })
          .onUpdate((e) => {
            if (dragOwner.get() === 1) update(trackLevel(e.x, e.translationY));
          })
          .onFinalize(() => {
            pressOut(trackPress);
            if (fine.get()) {
              fine.set(false);
              fineShown.set(withTiming(0, { duration: motion.base }));
            }
            if (dragOwner.get() === 1) settle();
          }),
        Gesture.Tap()
          .enabled(enabled)
          .onTouchesDown((event, manager) => {
            if (event.numberOfTouches > 1) manager.fail();
          })
          .maxDistance(10)
          .onEnd((e, success) => {
            if (success && available.get()) {
              begin(3);
              // A jump to a new point glides; a drag follows the finger exactly.
              setLevel(
                channel.get(),
                levelForFill(e.x / width.get()),
                reduced ? 0 : 180,
                motion.easeOut,
              );
              settle();
            }
          }),
      ),
    [
      enabled,
      available,
      begin,
      update,
      trackLevel,
      setLevel,
      channel,
      reduced,
      width,
      dragOwner,
      settle,
      pressIn,
      pressOut,
      trackPress,
      fine,
      fineShown,
      fx.fineScrub,
    ],
  );
  const dialGesture = useMemo(
    () =>
      Gesture.Pan()
        .enabled(enabled)
        .maxPointers(1)
        .onTouchesDown((event, manager) => {
          if (event.numberOfTouches > 1) manager.fail();
        })
        .activeOffsetX([-3, 3])
        .failOffsetY([-12, 12])
        .onBegin(() => pressIn(dialPress))
        .onStart(() => {
          if (available.get()) begin(2);
        })
        .onUpdate((e) => {
          if (dragOwner.get() !== 2) return;
          const raw = dragStart.get() + e.translationX / DIAL_DRAG;
          update(raw);
          dialGive.set(reduced ? 0 : rubberBand(raw - clamp01(raw), DIAL_GIVE));
        })
        .onEnd((e) => {
          // Crown flick: the level keeps turning with the finger's speed and
          // decays, rubber-banding at the ends; detent ticks thin out with it.
          if (
            dragOwner.get() !== 2 ||
            !fxs.get().crown ||
            reduced ||
            Math.abs(e.velocityX) < 250
          )
            return;
          const ch = channel.get();
          spinning.set(true);
          (ch === 0 ? brightness : volume).set(
            withDecay(
              {
                velocity: (e.velocityX / DIAL_DRAG) * 0.6,
                deceleration: 0.99,
                clamp: [0, 1],
                rubberBandEffect: true,
                rubberBandFactor: 0.6,
              },
              () => {
                spinning.set(false);
                setLevel(
                  ch,
                  (ch === 0 ? brightness : volume).get(),
                  0,
                  motion.easeOut,
                );
                publishGoal();
              },
            ),
          );
        })
        .onFinalize(() => {
          pressOut(dialPress);
          if (dialGive.get() !== 0)
            dialGive.set(reduced ? 0 : withSpring(0, tactile.release));
          if (dragOwner.get() === 2) settle();
        }),
    [
      enabled,
      available,
      begin,
      update,
      dragOwner,
      dragStart,
      dialGive,
      reduced,
      settle,
      pressIn,
      pressOut,
      dialPress,
      fxs,
      channel,
      spinning,
      brightness,
      volume,
      setLevel,
      publishGoal,
    ],
  );
  /* eslint-enable react-hooks/refs */

  const handoff = useCallback(
    (next: Channel) => {
      "worklet";
      const currentFill = fill.get(),
        currentMix = lightMix.get(),
        currentNumber = readout.get();
      const changed = channel.get() !== next;
      // A mode change draws its own contraction; only fade the fill otherwise.
      cancelVisuals(!changed);
      channel.set(next);
      if (!changed) return;
      outgoingFill.set(currentFill);
      outgoingNumber.set(currentNumber);
      mix.set(currentMix);
      selector.set(
        withTiming(next, {
          duration: motion.fast,
          easing: motion.easeOut,
          reduceMotion: ReduceMotion.Never,
        }),
      );
      // Color feedback is meaningful under Reduce Motion; no movement is
      // retained. Otherwise the color transfers around the trough.
      const color = withTiming(next, {
        duration: 200,
        easing: reduced ? motion.easeOut : easeInOut,
        reduceMotion: ReduceMotion.Never,
      });
      mix.set(reduced ? color : withDelay(90, color, ReduceMotion.Never));
      if (!reduced) {
        reveal.set(0);
        reveal.set(withTiming(1, { duration: SWITCH, easing: Easing.linear }));
      }
    },
    [
      fill,
      lightMix,
      readout,
      channel,
      cancelVisuals,
      outgoingFill,
      outgoingNumber,
      mix,
      selector,
      reduced,
      reveal,
    ],
  );
  const select = useCallback(
    (next: Channel) => {
      if (!enabled) return;
      stopPlayback();
      setMode(next);
      scheduleOnUI(() => {
        if (!available.get()) return;
        handoff(next);
        publishGoal();
      });
    },
    [enabled, stopPlayback, available, handoff, publishGoal],
  );
  const adjust = useCallback(
    (delta: number) => {
      if (!enabled) return;
      stopPlayback();
      scheduleOnUI(() => {
        if (!available.get()) return;
        cancelVisuals(true);
        const ch = channel.get();
        // Step from the committed level so rapid presses never lose a step.
        const target = (Math.round(goal.get()[ch] * 100) + delta) / 100;
        setLevel(ch, target, reduced ? 0 : motion.fast, motion.easeOut);
        publishGoal();
      });
    },
    [
      enabled,
      stopPlayback,
      available,
      cancelVisuals,
      channel,
      goal,
      setLevel,
      reduced,
      publishGoal,
    ],
  );
  const reset = useCallback(() => {
    if (!enabled) return;
    stopPlayback();
    setMode(0);
    scheduleOnUI(() => {
      handoff(0);
      setLevel(0, 0.52, reduced ? 0 : motion.base, motion.easeOut);
      setLevel(1, 0.53, 0, motion.easeOut);
      publishGoal();
    });
  }, [enabled, stopPlayback, handoff, setLevel, reduced, publishGoal]);
  const stop = useCallback(() => {
    stopPlayback();
    scheduleOnUI(() => {
      cancelVisuals(true);
      publishGoal();
    });
  }, [stopPlayback, cancelVisuals, publishGoal]);
  const replay = useCallback(() => {
    if (!enabled) return;
    stopPlayback();
    const id = runId.current;
    setPlaying(true);
    setMode(0);
    scheduleOnUI(() => {
      cancelVisuals(true);
      // The live frame fades into the reference intro instead of cutting to it.
      capture(true);
      cancelAnimation(mix);
      mix.set(0);
      channel.set(0);
      setLevel(0, 0.52, reduced ? 0 : motion.base, motion.easeOut);
      setLevel(1, 0.53, 0, motion.easeOut);
      replayId.set(id);
      replayTime.set(0);
      replayRunning.set(true);
      // The clock schedules semantic phases. referencePose independently removes
      // movement when reduced motion is requested; the clock must not skip phases.
      replayTime.set(
        withTiming(
          referenceDuration,
          {
            duration: referenceDuration,
            easing: Easing.linear,
            reduceMotion: ReduceMotion.Never,
          },
          (finished) => {
            if (finished) {
              replayRunning.set(false);
              scheduleOnRN(finishReplay, id);
            }
          },
        ),
      );
    });
  }, [
    enabled,
    stopPlayback,
    cancelVisuals,
    capture,
    mix,
    channel,
    setLevel,
    reduced,
    replayId,
    replayTime,
    replayRunning,
    finishReplay,
  ]);

  // Mode selectors sink into their wells on press (90ms) and spring back on
  // release; under Reduce Motion only the darkening remains, instantly.
  const pressSelector = useCallback(
    (mode: Channel, down: boolean) => {
      const sv = mode === 0 ? brightPress : volumePress;
      if (reduced) sv.set(down ? 1 : 0);
      else
        sv.set(
          down
            ? withTiming(1, { duration: 90, easing: motion.easeOut })
            : withSpring(0, tactile.release),
        );
    },
    [reduced, brightPress, volumePress],
  );
  // Power-on: light rises from 0, the number rolls up once, the knob homes
  // from -32° with a soft overshoot, and one soft haptic lands at the top.
  const powerOn = useCallback(() => {
    if (reduced) return;
    scheduleOnUI(() => {
      boot.set(0);
      boot.set(
        withDelay(
          400,
          withTiming(
            1,
            { duration: BOOT, easing: Easing.linear },
            (finished) => {
              if (finished) scheduleOnRN(softFeedback);
            },
          ),
        ),
      );
      dialGive.set(-0.12);
      dialGive.set(
        withDelay(550, withSpring(0, { duration: 900, dampingRatio: 0.55 })),
      );
    });
  }, [reduced, boot, dialGive, softFeedback]);
  // Runs once per mount; the value seeded `boot` at 0 so there is no flash.
  const booted = useRef(false);
  useEffect(() => {
    if (booted.current) return;
    booted.current = true;
    if (fx.powerOn && !reduced) powerOn();
  }, [fx.powerOn, reduced, powerOn]);
  useEffect(() => {
    mounted.current = true;
    const sub = AppState.addEventListener("change", (state) =>
      setForeground(state === "active"),
    );
    return () => {
      mounted.current = false;
      stopPlayback();
      sub.remove();
      scheduleOnUI(quiet);
    };
  }, [quiet, stopPlayback]);
  useEffect(() => {
    available.set(enabled);
    if (!enabled || reduced) {
      stopPlayback();
      scheduleOnUI(quiet);
    }
  }, [enabled, reduced, available, stopPlayback, quiet]);
  // Only the explicit Disable toggle dims the specimen, and it eases; leaving
  // the screen or backgrounding must not flash the material on return.
  useEffect(() => {
    active.set(
      withTiming(disabled ? 0 : 1, {
        duration: 200,
        easing: motion.easeOut,
        reduceMotion: ReduceMotion.Never,
      }),
    );
  }, [disabled, active]);

  return {
    mode,
    snapshot,
    playing,
    reduced,
    reducedFromPreferences: prefs.reduced,
    enabled,
    fill,
    mix: lightMix,
    selectorMix,
    numberRolls,
    numberQuick,
    numberInk,
    numberRun,
    lightAlpha,
    lightBlur,
    numberAlpha,
    readout,
    labelAlpha,
    dialRotation,
    slosh,
    fineShown,
    brightness,
    powerOn,
    dialPress,
    trackPress,
    glowPress,
    brightPress,
    volumePress,
    pressSelector,
    active,
    width,
    trackGesture,
    dialGesture,
    select,
    adjust,
    reset,
    replay,
    stop,
  };
}
export type TactileControl = ReturnType<typeof useTactileControl>;
