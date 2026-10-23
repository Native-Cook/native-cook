import { useCallback, useEffect, useState } from "react";
import { StyleSheet, Text, View, useWindowDimensions } from "react-native";
import { Gesture, GestureDetector } from "react-native-gesture-handler";
import Animated, {
  Easing,
  ReduceMotion,
  cancelAnimation,
  useAnimatedReaction,
  useAnimatedStyle,
  useDerivedValue,
  useFrameCallback,
  useSharedValue,
  withDelay,
  withRepeat,
  withSpring,
  withTiming,
} from "react-native-reanimated";
import { scheduleOnRN, scheduleOnUI } from "react-native-worklets";
import * as Haptics from "expo-haptics";
import { usePreferences } from "./i18n";
import { M, chargeAt, ignitionAt, seg } from "./ember-motion";
import { EmberBpm, EmberCount } from "./ember-count";
import { EmberEffects, EmberGlass, EmberLight } from "./ember-surface";
import type { EmberMotion, EmberQuality } from "./ember-values";
import { E, emberTone as T } from "./ember-theme";
import { face, useMotionPrefs } from "./ui";

// Ember Charge — an activity widget you charge by holding it.
// After a concept image of a dark "Walk" widget with an ember glow rising
// from its bottom edge. Holding the card is effort: the stripes run faster,
// the coals heat from amber to white-hot and the step count rolls toward the
// day's goal. Reach it and the card ignites; let go early and it cools.
//
// The card is a glass slab lit from its floor (ember-shader.ts): what a hold
// charges is that light. It rises and runs hotter, and every press, release
// and ignition makes it lean and ring back like a liquid.

const EASE_OUT = Easing.bezier(0.23, 1, 0.32, 1);
const EASE_IN_OUT = Easing.bezier(0.77, 0, 0.175, 1);
const NEVER = ReduceMotion.Never;
const LOOP = M.loop.end;

type Props = {
  /** Plays by itself (rest, charge, ignite, hold, cool) until a finger takes over. */
  autoPlay?: boolean;
  /** The day's step goal; holding charges the count from 4,151 up to it. */
  goal?: number;
  /** Auto measures the first two visible seconds, then keeps its choice. */
  quality?: EmberQuality | "auto";
  /** Called on the frame the goal is reached. */
  onIgnite?: () => void;
};

export function EmberCharge({ autoPlay = false, goal = E.goal, quality: requestedQuality = "auto", onIgnite }: Props) {
  const { width } = useWindowDimensions();
  const scale = Math.min(1, (width - E.pageMargin * 2) / E.size);
  const { reduced, haptics } = useMotionPrefs();
  const { t: tr } = usePreferences();
  const playing = autoPlay && !reduced;
  const [reached, setReached] = useState(false);
  const automatic = requestedQuality === "auto";
  const [autoQuality, setAutoQuality] = useState<EmberQuality>("high");
  const quality = automatic ? autoQuality : requestedQuality;

  // The interactive state: the charge itself, the ignition clock, and whether
  // the card is past ignition (holding at the goal or cooling down).
  const charge = useSharedValue(0);
  const ign = useSharedValue(0);
  const held = useSharedValue(0);
  const pressing = useSharedValue(0);
  const pressure = useSharedValue(0);
  // A quick reversal must not stretch the card or push its light below rest.
  const press = useDerivedValue(() => Math.max(0, Math.min(1, pressure.get())));
  // 1 once a finger has taken over from the self-playing loop.
  const manual = useSharedValue(0);
  const resume = useSharedValue(0);
  const clock = useSharedValue(0);
  const time = useSharedValue(0);
  const heat = useSharedValue(0);
  const drift = useSharedValue(0);
  const phase = useSharedValue(0);
  const slosh = useSharedValue(0);
  // The loop and the finger move the same light. Starting a spring keeps
  // the position already on screen, including on the first touch frame.
  const followX = useSharedValue(0);
  const followY = useSharedValue(0);
  const aimX = useDerivedValue(() => reduced ? 0 : Math.max(-1, Math.min(1, followX.get())));
  const aimY = useDerivedValue(() => reduced ? 0 : Math.max(-1, Math.min(1, followY.get())));
  const sloshV = useSharedValue(0);
  // The first frame of the shader canvas can hold the UI thread for a while,
  // and the copy would show on a blank card. So the card stays hidden until
  // a few smooth frames have gone by, then fades in.
  const enter = useSharedValue(0);
  const smooth = useSharedValue(0);
  const appeared = useSharedValue(-1);
  const slowFrames = useSharedValue(0);
  const measured = useSharedValue(false);
  useFrameCallback((frame) => {
    const dt = frame.timeSincePreviousFrame ?? 100;
    if (smooth.get() < 6) {
      smooth.set(dt < 34 ? smooth.get() + 1 : 0);
      if (smooth.get() === 6) enter.set(withTiming(1, { duration: reduced ? 200 : M.enter, easing: EASE_OUT, reduceMotion: NEVER }));
      return;
    }
    // Shader warm-up and the fade do not vote. Once visible, count two
    // seconds of real frame times and make at most one step down.
    if (!automatic || measured.get() || enter.get() < 1) return;
    if (appeared.get() < 0) {
      appeared.set(frame.timestamp);
      return;
    }
    if (frame.timestamp - appeared.get() <= M.quality.window) {
      if (dt > M.quality.frame) slowFrames.set(slowFrames.get() + 1);
      return;
    }
    measured.set(true);
    if (slowFrames.get() > M.quality.allowed) scheduleOnRN(setAutoQuality, "medium");
  });

  const fireIgnite = useCallback(() => {
    if (haptics) {
      void Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success).catch(() => {});
      void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Heavy).catch(() => {});
    }
    onIgnite?.();
  }, [haptics, onIgnite]);
  const tick = useCallback(() => {
    if (haptics) void Haptics.selectionAsync().catch(() => {});
  }, [haptics]);

  // Give the entrance its own time. A finger can still take over before it
  // finishes; the loop waits for ownership and always starts at rest.
  useAnimatedReaction(
    () => playing && enter.get() === 1 && manual.get() === 0,
    (ready, before) => {
      if (ready === before) return;
      cancelAnimation(clock);
      if (!ready) return;
      clock.set(0);
      clock.set(withRepeat(withTiming(LOOP, { duration: LOOP * 1000, easing: Easing.linear, reduceMotion: NEVER }), -1, false, undefined, NEVER));
    },
  );
  useEffect(() => () => cancelAnimation(clock), [clock]);

  // One source of truth for what is on screen: the loop's clock, or the finger.
  const c = useDerivedValue(() =>
    playing && manual.get() === 0 ? chargeAt(clock.get()) : Math.min(1, Math.max(0, charge.get())),
  );
  const ignition = useDerivedValue(() => (playing && manual.get() === 0 ? ignitionAt(clock.get()) : ign.get()));
  const glow = useDerivedValue(() => (reduced ? c.get() : heat.get()));
  const steps = useDerivedValue(() => E.restSteps + c.get() * (goal - E.restSteps));
  const progress = useDerivedValue(() => steps.get() / goal);

  const loopBefore = useSharedValue(-1);
  const hitches = useSharedValue(0);
  const worstFrame = useSharedValue(0);
  useFrameCallback((frame) => {
    const loopTime = clock.get();
    if (playing && manual.get() === 0) {
      // A small figure eight closes at zero with zero speed, so the loop
      // seam and a resumed rest frame catch the same light.
      const angle = (loopTime / LOOP) * Math.PI * 2;
      const envelope = Math.sin(angle / 2) ** 2;
      followX.set(M.aim.x * envelope * Math.sin(angle));
      followY.set(M.aim.y * envelope * Math.sin(angle * 2));
    }
    if (__DEV__) {
      if (playing && manual.get() === 0) {
        if (loopBefore.get() > loopTime) {
          console.debug(`[Ember] loop: ${hitches.get()} frames >40 ms; worst ${worstFrame.get().toFixed(1)} ms`);
          hitches.set(0);
          worstFrame.set(0);
        }
        const elapsed = frame.timeSincePreviousFrame ?? 0;
        if (elapsed > 40) hitches.set(hitches.get() + 1);
        worstFrame.set(Math.max(worstFrame.get(), elapsed));
        loopBefore.set(loopTime);
      } else {
        loopBefore.set(-1);
        hitches.set(0);
        worstFrame.set(0);
      }
    }
    const dt = Math.min(0.05, Math.max(0.001, (frame.timeSincePreviousFrame ?? 16) / 1000));
    time.set(frame.timeSinceFirstFrame / 1000);
    const cv = c.get();
    // Heat rises quickly and takes about 700 ms to cool.
    const rate = cv > heat.get() ? 16 : 5;
    heat.set(heat.get() + (cv - heat.get()) * (1 - Math.exp(-dt * rate)));
    const speed = M.stripeIdle + (M.stripeMax - M.stripeIdle) * Math.pow(cv, 1.5);
    drift.set((drift.get() + speed * dt) % E.stripe.period);
    const bpm = E.restBpm + (E.goalBpm - E.restBpm) * seg(cv, 0.9, 1);
    phase.set((phase.get() + (dt * bpm) / 60) % 1);
    // The light's lean is a damped spring around upright.
    sloshV.set(sloshV.get() + (-M.slosh.stiffness * slosh.get() - M.slosh.damping * sloshV.get()) * dt);
    slosh.set(slosh.get() + sloshV.get() * dt);
  }, !reduced);

  // Ignition kicks the liquid, whoever caused it: the finger or the loop.
  useAnimatedReaction(
    () => ignition.get() > 0,
    (lit, before) => {
      if (lit && before === false) sloshV.set(sloshV.get() + M.slosh.ignite);
    },
  );

  // A selection tick for every 500 steps the finger charges through.
  useAnimatedReaction(
    () => Math.floor(steps.get() / 500),
    (now, before) => {
      if (before !== null && now > before && pressing.get() === 1) scheduleOnRN(tick);
    },
  );
  useAnimatedReaction(
    () => steps.get() >= goal - 0.5 ? 1 : steps.get() <= E.restSteps + 0.5 ? 0 : -1,
    (now, before) => {
      // Keep the last landed count and heart rate while the digits run.
      if (now !== before && now >= 0) scheduleOnRN(setReached, now === 1);
    },
  );
  // Once the finger is gone and the card has cooled, the loop starts again
  // from its rest frame, so nothing jumps.
  useAnimatedReaction(
    () => manual.get() === 1 && pressing.get() === 0 && held.get() === 0 && charge.get() <= 0.001,
    (idle) => {
      if (!playing) return;
      if (!idle) {
        cancelAnimation(resume);
        return;
      }
      resume.set(0);
      resume.set(
        withDelay(
          M.resume,
          withTiming(1, { duration: 1, reduceMotion: NEVER }, (finished) => {
            "worklet";
            if (!finished) return;
            manual.set(0);
          }),
        ),
      );
    },
  );

  // Two worklets cannot call each other (each captures the other as it was
  // when it was made), so the chain is one-way: charging ends in ignition and
  // schedules the cool-down, and a reaction below starts the next charge.
  function coolAfter(wait: number) {
    "worklet";
    charge.set(
      withDelay(
        wait,
        withTiming(0, { duration: M.cool, easing: EASE_IN_OUT, reduceMotion: NEVER }, (finished) => {
          "worklet";
          if (!finished) return;
          ign.set(0);
          held.set(0);
        }),
      ),
    );
  }
  // Charging always continues from where the charge is, never from zero.
  function chargeUp(ms: number) {
    "worklet";
    held.set(0);
    cancelAnimation(ign);
    ign.set(0);
    charge.set(
      withTiming(1, { duration: Math.max(1, (1 - charge.get()) * ms), easing: EASE_IN_OUT, reduceMotion: NEVER }, (finished) => {
        "worklet";
        if (!finished) return;
        held.set(1);
        pressure.set(withTiming(0, { duration: M.pressRelease, easing: EASE_OUT, reduceMotion: NEVER }));
        ign.set(0);
        ign.set(withTiming(1, { duration: M.ignite * 1000, easing: Easing.linear, reduceMotion: NEVER }));
        scheduleOnRN(fireIgnite);
        coolAfter(M.hold);
      }),
    );
  }
  // A finger that is still down when the card has cooled charges it again.
  useAnimatedReaction(
    () => held.get() === 0 && pressing.get() === 1,
    (ready, before) => {
      if (ready && before === false && charge.get() <= 0.001) chargeUp(reduced ? M.chargeReduced : M.charge);
    },
  );

  function followFinger(x: number, y: number) {
    "worklet";
    if (reduced) return;
    followX.set(withSpring(Math.max(-1, Math.min(1, x / (E.size / 2) - 1)), { ...M.aim.follow, velocity: 0, reduceMotion: NEVER }));
    followY.set(withSpring(Math.max(-1, Math.min(1, y / (E.size / 2) - 1)), { ...M.aim.follow, velocity: 0, reduceMotion: NEVER }));
  }
  function releaseAim(vx: number, vy: number) {
    "worklet";
    if (reduced) return;
    // Pan velocity is pt/s; aim spans two units across the card. Cancel
    // the follow first so its velocity is not added to the finger's.
    cancelAnimation(followX);
    cancelAnimation(followY);
    followX.set(withSpring(0, { ...M.aim.release, velocity: vx / (E.size / 2), reduceMotion: NEVER }));
    followY.set(withSpring(0, { ...M.aim.release, velocity: vy / (E.size / 2), reduceMotion: NEVER }));
  }
  function activate() {
    "worklet";
    cancelAnimation(resume);
    if (playing && manual.get() === 0) {
      charge.set(chargeAt(clock.get()));
      manual.set(1);
    }
    releaseAim(0, 0);
    // Activation is a complete charge, without requiring a held finger.
    // At the goal it renews the hold instead of leaving the loop suspended.
    chargeUp(reduced ? M.chargeReduced : M.charge);
  }

  const hold = Gesture.Pan()
    .minDistance(0)
    .onBegin((event) => {
      cancelAnimation(resume);
      followFinger(event.x / scale, event.y / scale);
      const ms = reduced ? M.chargeReduced : M.charge;
      pressing.set(1);
      sloshV.set(sloshV.get() + M.slosh.press);
      pressure.set(withTiming(1, { duration: M.press, easing: EASE_OUT, reduceMotion: NEVER }));
      if (playing && manual.get() === 0) {
        // The finger takes over from exactly what the loop is showing.
        const now = clock.get();
        manual.set(1);
        charge.set(chargeAt(now));
        if (now >= M.loop.charged && now < M.loop.held) {
          // Mid-ignition: let it finish its flash and its hold, then cool.
          const lit = ignitionAt(now);
          held.set(1);
          pressure.set(withTiming(0, { duration: M.pressRelease, easing: EASE_OUT, reduceMotion: NEVER }));
          ign.set(lit);
          ign.set(withTiming(1, { duration: (1 - lit) * M.ignite * 1000, easing: Easing.linear, reduceMotion: NEVER }));
          coolAfter((M.loop.held - now) * 1000);
          return;
        }
        ign.set(0);
        held.set(0);
      }
      // Past ignition the hold and the cool-down run on their own.
      if (held.get() === 1) {
        pressure.set(withTiming(0, { duration: M.pressRelease, easing: EASE_OUT, reduceMotion: NEVER }));
        return;
      }
      chargeUp(ms);
    })
    .onUpdate((event) => followFinger(event.x / scale, event.y / scale))
    .onFinalize((event) => {
      releaseAim(event.velocityX / scale, event.velocityY / scale);
      pressure.set(withTiming(0, { duration: 160, easing: EASE_OUT, reduceMotion: NEVER }));
      if (pressing.get() === 0) return;
      pressing.set(0);
      // Past ignition the hold and the cool-down are not the finger's any more.
      if (held.get() === 1) return;
      sloshV.set(sloshV.get() + M.slosh.release);
      charge.set(withSpring(0, { ...M.release, overshootClamping: true, reduceMotion: NEVER }));
    });

  const cardStyle = useAnimatedStyle(() => {
    const cv = c.get();
    const i = ignition.get();
    const tau = i * M.ignite;
    // The press itself is the feedback (0.96, see below). The charge only
    // tightens it a little more. Both let go in the first 60 ms of ignition,
    // before the pop peaks, so lifting the finger cannot add a second bounce.
    const compress = reduced ? 0 : 0.012 * cv * (1 - seg(i, 0, 0.055));
    const pop = reduced || i <= 0 ? 0 : 0.1 * Math.exp(-9 * tau) * Math.sin(12 * tau);
    // The tremble grows with the square of the charge and stops at ignition.
    const shake = reduced ? 0 : cv * cv * (1 - seg(i, 0, 0.02));
    return {
      opacity: enter.get(),
      transform: [
        { translateX: Math.sin(time.get() * 28 * 2 * Math.PI) * 0.6 * shake },
        { translateY: Math.cos(time.get() * 31 * 2 * Math.PI) * 0.45 * shake },
        { scale: reduced ? 1 : (1 - compress + pop) * (1 - 0.04 * press.get()) },
      ],
    };
  });
  // The count lands from 1.18 with a decaying cosine: 9/s quiets it within
  // about half a second; 12 rad/s gives it one small undershoot on the way.
  const slamStyle = useAnimatedStyle(() => {
    const i = ignition.get();
    const tau = i * M.ignite;
    return { transform: [{ scale: reduced || i <= 0 ? 1 : 1 + 0.18 * Math.exp(-9 * tau) * Math.cos(12 * tau) }] };
  });

  const motion: EmberMotion = { charge: c, glow, slosh, ignition, time, drift, phase, progress, press, aimX, aimY, enter, quality, reduced };
  const shown = (reached ? goal : E.restSteps).toLocaleString("en-US");
  const bpm = reached ? E.goalBpm : E.restBpm;
  const metrics = [
    { value: "5.21", unit: "km" },
    { value: "413", unit: tr("min", "phút") },
    { value: "1343", unit: "kcal" },
  ];

  // Fixed instrument geometry: card text stays in its boxes; the accessible
  // label carries the content. The page hint still follows system text size.
  return (
    <GestureDetector gesture={hold}>
      <View
        accessible
        accessibilityRole="button"
        accessibilityLabel={tr(
          `Walk, 30 days, ${shown} steps, heart rate ${bpm}`,
          `Đi bộ, 30 ngày, ${shown} bước, nhịp tim ${bpm}`,
        )}
        accessibilityHint={tr("Double-tap to charge to the daily step goal", "Chạm hai lần để nạp số bước tới mục tiêu trong ngày")}
        accessibilityActions={[{ name: "activate" }]}
        onAccessibilityAction={(event) => {
          if (event.nativeEvent.actionName === "activate") scheduleOnUI(activate);
        }}
        style={{ width: E.size * scale, height: E.size * scale }}
      >
        {/* Keep touch space fixed while the drawn instrument and its bleed scale together. */}
        <View style={[styles.card, { transform: [{ scale }], transformOrigin: "top left" }]} pointerEvents="none">
          <Animated.View style={[styles.card, cardStyle]}>
            <View pointerEvents="none" style={styles.surface}>
              <EmberGlass {...motion} />
            </View>
            <View pointerEvents="none" style={styles.surface}>
              <EmberLight {...motion} />
            </View>

            <Text allowFontScaling={false} style={styles.title}>{tr("Walk", "Đi bộ")}</Text>
            <Text allowFontScaling={false} style={styles.subtitle}>{tr("30 days", "30 ngày")}</Text>
            <Text allowFontScaling={false} style={styles.time}>4:24 AM</Text>

            {metrics.map((metric, i) => (
              <View key={i} style={[styles.metric, { left: E.metrics.x[i] + E.metrics.text }]}>
                <Text allowFontScaling={false} style={styles.metricValue}>{metric.value}</Text>
                <Text allowFontScaling={false} style={styles.metricUnit}>{metric.unit}</Text>
              </View>
            ))}

            {/* The unit follows the number, whatever its width: 6,400 is wider than 4,151. */}
            <View pointerEvents="none" style={styles.heroRow}>
              <Animated.View style={[styles.hero, slamStyle]}>
                <EmberCount steps={steps} goal={goal} reduced={reduced} />
              </Animated.View>
              <Text allowFontScaling={false} style={styles.unit}>{tr("steps", "bước")}</Text>
            </View>
            <View pointerEvents="none" style={styles.bpm}>
              <EmberBpm value={bpm} reduced={reduced} />
            </View>

            {!reduced && (
              <View pointerEvents="none" style={styles.surface}>
                <EmberEffects {...motion} />
              </View>
            )}
          </Animated.View>
        </View>
      </View>
    </GestureDetector>
  );
}

const styles = StyleSheet.create({
  card: { width: E.size, height: E.size },
  surface: { position: "absolute", left: -E.bleed, top: -E.bleed },
  title: {
    position: "absolute",
    left: E.title.x,
    top: E.title.top,
    fontFamily: face.semibold,
    fontSize: 26,
    lineHeight: E.line.title,
    letterSpacing: -0.8,
    color: T.ink,
  },
  subtitle: {
    position: "absolute",
    left: E.title.x,
    top: E.title.sub,
    fontFamily: face.regular,
    fontSize: 16,
    lineHeight: E.line.subtitle,
    letterSpacing: -0.2,
    color: T.sub,
  },
  time: {
    position: "absolute",
    right: E.time.right,
    top: E.time.top,
    fontFamily: face.regular,
    fontSize: 12,
    lineHeight: E.line.time,
    color: T.sub,
    fontVariant: ["tabular-nums"],
  },
  metric: { position: "absolute", top: E.metrics.valueTop },
  metricValue: {
    fontFamily: face.semibold,
    fontSize: 24,
    lineHeight: E.line.value,
    // These three never change, so they take the proportional figures: the
    // tabular ones are for numbers that move.
    letterSpacing: -0.65,
    color: T.ink,
  },
  metricUnit: { fontFamily: face.regular, fontSize: 12, lineHeight: E.line.unit, color: T.sub },
  heroRow: { position: "absolute", left: E.count.x, top: E.count.top, flexDirection: "row", alignItems: "flex-end" },
  hero: { transformOrigin: "left bottom" },
  unit: {
    marginLeft: E.count.unitGap,
    marginBottom: E.count.unitDrop,
    fontFamily: face.regular,
    fontSize: 12,
    lineHeight: E.line.unit,
    color: T.onEmber,
  },
  // A heart's weight sits above its middle, so the number does too.
  bpm: { position: "absolute", left: E.heart.x, top: E.heart.y + E.heart.rateOffset },
});
