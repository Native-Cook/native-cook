import { useEffect, useMemo, useState } from "react";
import { StyleSheet, Text, View } from "react-native";
import { BlurMask, Canvas, Group, RoundedRect, Shader } from "@shopify/react-native-skia";
import { Gesture, GestureDetector } from "react-native-gesture-handler";
import Animated, {
  Easing,
  FadeIn,
  ReduceMotion,
  type SharedValue,
  useAnimatedReaction,
  useAnimatedStyle,
  useDerivedValue,
  useFrameCallback,
  useSharedValue,
  withDelay,
  withSequence,
  withSpring,
  withTiming,
} from "react-native-reanimated";
import { scheduleOnRN } from "react-native-worklets";
import * as Haptics from "expo-haptics";
import { usePreferences } from "./i18n";
import { FLARE } from "./flare-shader";
import { FLARES, flareMotion as M, flareTone, hexToRgb, type Flare } from "./flare-palette";
import { blobFrame } from "./flare-blob-frame";
import { hapticRhythm } from "./haptic-rhythm";
import { Hint, Pill, systemFace, useMotionPrefs } from "./ui";

// Flare — three notification pills, each with a small fire at its cap.
// After a reference clip of brand notifications whose trailing ends burn in
// the brand's colors. Heat here means "new": a notification arrives and
// ignites, tap to read it and the fire cools to an ember, tap again to
// relight, hold to stoke it.

const EASE_OUT = Easing.bezier(0.23, 1, 0.32, 1);
const EASE_IN_OUT = Easing.bezier(0.77, 0, 0.175, 1);
// Room around each pill's canvas for the cast shadow (drop + 3 sigma).
const PAD = M.shadowDrop + M.shadowBlur * 3 + 4;

// When the last pill has finished arriving.
const arrival = (FLARES.length - 1) * M.stagger + M.enter;

export function FlareNotes() {
  const { reduced, haptics } = useMotionPrefs();
  const { t } = usePreferences();
  const [box, setBox] = useState<{ w: number; h: number } | null>(null);
  const [round, setRound] = useState(0);
  // Bumped on the UI thread by "Deliver again"; each pill replays itself in
  // place. Remounting the pills meant fresh Skia canvases, and going through
  // a JS render to restart them left the stack blank for up to a second.
  const cycle = useSharedValue(0);
  // The first frame of three Skia canvases (and their pipelines) can hold the
  // UI thread for most of a second; an arrival started on mount would play
  // out unseen and the pills would pop in whole. So the arrival waits until
  // the UI thread has run a few smooth frames in a row.
  const [live, setLive] = useState(false);
  const smooth = useSharedValue(0);
  const watch = useFrameCallback((f) => {
    const dt = f.timeSincePreviousFrame ?? 100;
    smooth.set(dt < 34 ? smooth.get() + 1 : 0);
    if (smooth.get() === 6) scheduleOnRN(setLive, true);
  }, false);

  const geo = useMemo(() => {
    if (!box) return null;
    const width = Math.min(box.w - 48, Math.round(M.height * M.aspect));
    const stack = FLARES.length * M.height + (FLARES.length - 1) * M.gap;
    const top = Math.round(box.h * 0.36 - stack / 2);
    return { width, x: (box.w - width) / 2, top, stack };
  }, [box]);

  // Count from the commit that mounts the canvases, and stop once live.
  useEffect(() => {
    smooth.set(0);
    watch.setActive(!!geo && !live);
  }, [geo, live, smooth, watch]);

  const replay = () => {
    cycle.set(cycle.get() + 1);
    // Only the read state lives in React; it can catch up a frame late.
    setRound((n) => n + 1);
    if (haptics) void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
  };

  return (
    <View
      style={StyleSheet.absoluteFill}
      onLayout={(e) => setBox({ w: e.nativeEvent.layout.width, h: e.nativeEvent.layout.height })}
    >
      {geo && (
        <>
          <View style={[styles.stack, { left: geo.x, top: geo.top, width: geo.width, gap: M.gap }]}>
            {FLARES.map((f, i) => (
              <FlarePill
                key={f.id}
                flare={f}
                round={round}
                cycle={cycle}
                live={live}
                index={i}
                width={geo.width}
                line={t(f.line[0], f.line[1])}
                seen={t("Seen just now", "Vừa xem")}
                hint={t("Double-tap to mark read or unread", "Chạm hai lần để đánh dấu đã đọc hoặc chưa đọc")}
                reduced={reduced}
                haptics={haptics}
              />
            ))}
          </View>
          {/* The controls wait for the last note to land. */}
          {live && (
            <Animated.View
              entering={reduced ? undefined : FadeIn.delay(arrival + 120).duration(320).easing(EASE_OUT)}
              style={[styles.controls, { top: geo.top + geo.stack + 34 }]}
            >
              <Hint color={flareTone.pageHint} style={styles.hint}>
                {t("Tap to read. Hold to stoke the flame.", "Chạm để đọc. Giữ để thổi bùng ngọn lửa.")}
              </Hint>
              <View style={styles.replay}>
                <Pill label={t("Deliver again", "Gửi lại")} onPress={replay} />
              </View>
            </Animated.View>
          )}
        </>
      )}
    </View>
  );
}

function FlarePill({
  flare,
  round,
  cycle,
  live,
  index,
  width,
  line,
  seen,
  hint,
  reduced,
  haptics,
}: {
  flare: Flare;
  round: number;
  cycle: SharedValue<number>;
  // False until the stack's canvases have drawn; the arrival waits for it.
  live: boolean;
  index: number;
  width: number;
  line: string;
  seen: string;
  hint: string;
  reduced: boolean;
  haptics: boolean;
}) {
  // The round the note was read in; a new delivery leaves it unread.
  const [readIn, setReadIn] = useState(-1);
  const read = readIn === round;
  // The flame stays faint left of the copy's right edge. Both status lines
  // are measured, so reading a note never moves that edge.
  const [copyW, setCopyW] = useState(0);
  const [seenW, setSeenW] = useState(0);
  const guard = copyW > 0 ? M.inset + Math.max(copyW, seenW) + 8 : width * 0.45;
  const crackle = useMemo(() => hapticRhythm(), []);

  const body = useMemo(() => hexToRgb(flare.body), [flare]);

  const enter = useSharedValue(0);
  const reach = useSharedValue(reduced ? 1 : 0);
  const heat = useSharedValue(reduced ? 1 : 0);
  const cool = useSharedValue(0);
  // 0 shows the note's line, 1 shows "Seen just now".
  const seenT = useSharedValue(0);
  const press = useSharedValue(0);
  const stoke = useSharedValue(0);
  // 1 once the finger has stayed down past the hold threshold.
  const held = useSharedValue(0);
  // Integrated flame time in seconds, so speeding the fire up never jumps the
  // pattern. All three start together, as in the reference.
  const time = useSharedValue(0);

  // The arrival: the note fades in from above while its fire catches, licks
  // out past its rest length and settles, the core flashing white. On a
  // replay the old note first fades out over `out` ms and is reset unseen.
  const arrive = (out: number) => {
    "worklet";
    const delay = index * M.stagger;
    const reset = () => withDelay(out, withTiming(0, { duration: 0 }));
    enter.set(
      withSequence(
        withTiming(0, { duration: out, easing: EASE_OUT }),
        withDelay(delay, withTiming(1, { duration: reduced ? 200 : M.enter, easing: EASE_OUT })),
      ),
    );
    cool.set(reset());
    seenT.set(reset());
    if (reduced) return;
    reach.set(
      withSequence(reset(), withDelay(delay + M.igniteDelay, withSpring(1, { duration: 900, dampingRatio: 0.55 }))),
    );
    heat.set(
      withSequence(
        reset(),
        withDelay(
          delay + M.igniteDelay,
          withSequence(
            withTiming(1.7, { duration: M.flash, easing: EASE_OUT }),
            withTiming(1, { duration: M.settle, easing: EASE_OUT }),
          ),
        ),
      ),
    );
  };

  useEffect(() => {
    if (!live) return;
    arrive(0);
    return crackle.stop;
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [live]);

  useAnimatedReaction(
    () => cycle.get(),
    (now, before) => {
      if (before !== null && now !== before) arrive(reduced ? 120 : M.leave);
    },
  );

  useFrameCallback((f) => {
    const dt = Math.min(f.timeSincePreviousFrame ?? 16, 48) / 1000;
    const speed = 1 + stoke.get() * 1.1 + press.get() * 0.25 - cool.get() * 0.55;
    time.set(time.get() + dt * speed);
  }, !reduced);

  const uniforms = useDerivedValue(() => {
    const c = cool.get();
    const k = stoke.get();
    return {
      size: [width, M.height],
      // 0 at ignition, 1 at rest, longer when stoked.
      stretch: reach.get() * (1 + 0.55 * k + 0.08 * press.get()),
      heat: heat.get() * (1 + 0.35 * k + 0.15 * press.get()),
      cool: c * (1 - k * 0.5),
      ember: M.ember,
      guard,
      body,
      ...blobFrame(flare.blobs, time.get()),
    };
  });

  const style = useAnimatedStyle(() => ({
    opacity: enter.get(),
    transform: [
      { translateY: reduced ? 0 : (1 - enter.get()) * -10 },
      { scale: (reduced ? 1 : 0.97 + 0.03 * enter.get()) - press.get() * 0.03 },
    ],
  }));
  // The status lines trade places with a short vertical drift.
  const lineStyle = useAnimatedStyle(() => ({
    opacity: 1 - seenT.get(),
    transform: [{ translateY: reduced ? 0 : seenT.get() * -4 }],
  }));
  const seenStyle = useAnimatedStyle(() => ({
    opacity: seenT.get(),
    transform: [{ translateY: reduced ? 0 : (1 - seenT.get()) * 4 }],
  }));

  const toggle = () => {
    const next = !read;
    setReadIn(next ? round : -1);
    seenT.set(withTiming(next ? 1 : 0, { duration: reduced ? 120 : M.swap, easing: EASE_OUT }));
    cool.set(
      withTiming(next ? 1 : 0, {
        duration: reduced ? 200 : next ? M.cool : M.relight,
        easing: EASE_IN_OUT,
      }),
    );
    if (!next && !reduced) {
      heat.set(
        withSequence(
          withTiming(1.7, { duration: 140, easing: EASE_OUT }),
          withTiming(1, { duration: 600, easing: EASE_OUT }),
        ),
      );
    }
    if (!haptics) return;
    if (next) void Haptics.selectionAsync().catch(() => {});
    else void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
  };

  const beginHold = () => {
    // The crackle waits for the press to become a hold.
    if (haptics && !reduced) crackle.play({ first: 70, stretch: 0.97, jitter: 60, total: M.stoke, delay: M.holdAfter });
  };
  const endHold = (tap: boolean) => {
    crackle.stop();
    if (tap) toggle();
  };

  // Pan with no minimum distance, as in the other specimens: it reports the
  // touch-down in onBegin. (A zero-duration LongPress activates straight away
  // and never calls onBegin.) Tap vs hold is decided here, not by `success`,
  // which is false for a Pan that never moved.
  const gesture = useMemo(
    () =>
      Gesture.Pan()
        .minDistance(0)
        .onBegin(() => {
          press.set(withTiming(1, { duration: 120, easing: EASE_OUT }));
          held.set(0);
          // The hold timer must run its full 260 ms even with Reduce Motion
          // on: Reanimated's default (ReduceMotion.System) skips the delay and
          // jumps to the end, which made every press a hold and no tap ever
          // read a note.
          held.set(withDelay(M.holdAfter, withTiming(1, { duration: 1 }), ReduceMotion.Never));
          if (!reduced) {
            stoke.set(
              withDelay(M.holdAfter, withTiming(1, { duration: M.stoke, easing: Easing.out(Easing.quad) })),
            );
          }
          scheduleOnRN(beginHold);
        })
        .onFinalize((e) => {
          // A release before the hold threshold, close to where it started, is a tap.
          const tap = held.get() < 1 && Math.hypot(e.translationX, e.translationY) < 24;
          held.set(0);
          press.set(withSpring(0, { duration: 300, dampingRatio: 0.8 }));
          stoke.set(withSpring(0, { duration: 600, dampingRatio: 0.7 }));
          scheduleOnRN(endHold, tap);
        }),
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [reduced, read, round, haptics],
  );

  return (
    <GestureDetector gesture={gesture}>
      <Animated.View
        accessible
        accessibilityRole="button"
        accessibilityLabel={`${flare.title.replace(/[.*]/g, "")}, ${read ? seen : line}`}
        accessibilityHint={hint}
        accessibilityState={{ selected: read }}
        accessibilityActions={[{ name: "activate" }]}
        onAccessibilityAction={toggle}
        style={[{ width, height: M.height }, style]}
      >
        <View pointerEvents="none" style={[styles.canvas, { width: width + PAD * 2, height: M.height + PAD * 2 }]}>
          <Canvas style={StyleSheet.absoluteFill}>
            <Group transform={[{ translateX: PAD }, { translateY: PAD }]}>
              <RoundedRect
                x={M.shadowInset}
                y={M.shadowDrop}
                width={width - M.shadowInset * 2}
                height={M.height}
                r={M.height / 2}
                color={flare.shadow}
              >
                <BlurMask blur={M.shadowBlur} style="normal" />
              </RoundedRect>
              <RoundedRect x={0} y={0} width={width} height={M.height} r={M.height / 2} color={flare.body}>
                {FLARE && <Shader source={FLARE} uniforms={uniforms} />}
              </RoundedRect>
            </Group>
          </Canvas>
        </View>
        <View pointerEvents="none" style={styles.copy} onLayout={(e) => setCopyW(e.nativeEvent.layout.width)}>
          <Text style={[styles.title, { color: flare.ink }]} numberOfLines={1}>
            {flare.title}
          </Text>
          <View>
            <Animated.Text style={[styles.line, { color: flare.sub }, lineStyle]} numberOfLines={1}>
              {line.toUpperCase()}
            </Animated.Text>
            <Animated.Text
              style={[styles.line, styles.seen, { color: flare.sub }, seenStyle]}
              numberOfLines={1}
              onLayout={(e) => setSeenW(e.nativeEvent.layout.width)}
            >
              {seen.toUpperCase()}
            </Animated.Text>
          </View>
        </View>
      </Animated.View>
    </GestureDetector>
  );
}

const styles = StyleSheet.create({
  stack: { position: "absolute" },
  canvas: { position: "absolute", left: -PAD, top: -PAD },
  copy: { position: "absolute", left: M.inset, top: 0, bottom: 0, justifyContent: "center", gap: 3 },
  // The reference sets the name at a 0.185-height cap (22pt here). Its tag
  // line is ~7pt at this size; 10pt bold keeps the look and stays readable.
  title: { fontFamily: systemFace, fontSize: 22, lineHeight: 26, fontWeight: "700", letterSpacing: -0.3 },
  line: { fontFamily: systemFace, fontSize: 10, lineHeight: 13, fontWeight: "700", letterSpacing: 0.4 },
  seen: { position: "absolute", left: 0, top: 0 },
  controls: { position: "absolute", left: 0, right: 0 },
  hint: { top: 0 },
  replay: { marginTop: 34, alignItems: "center" },
});
