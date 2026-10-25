import {
  Canvas,
  Picture,
  Skia,
  type SkImage,
} from "@shopify/react-native-skia";
import * as Haptics from "expo-haptics";
import { SymbolView } from "expo-symbols";
import { useEffect, useMemo, useRef, useState } from "react";
import {
  Keyboard,
  type LayoutChangeEvent,
  Pressable,
  StyleSheet,
  Text,
  TextInput,
  View,
} from "react-native";
import Animated, {
  useAnimatedReaction,
  useAnimatedStyle,
  useDerivedValue,
  useFrameCallback,
  useReducedMotion,
  useSharedValue,
  Easing,
  withSpring,
  withTiming,
} from "react-native-reanimated";
import { useSafeAreaInsets } from "react-native-safe-area-context";
import { scheduleOnRN, scheduleOnUI } from "react-native-worklets";

import {
  THREADS,
  autoNeedle,
  drawLoopFrame,
  drawNeedle,
  drawStitches,
  makeGeo,
} from "./crossStitchArt";
import { bakeScene } from "./crossStitchBake";
import { MAX_LETTERS, borderStitches, layoutName } from "./crossStitchFont";
import {
  LOOP,
  REST_TEXT,
  REST_THREAD,
  SECOND_TEXT,
  TEXT_SCRIPT,
  chipPosition,
  easeInOut,
  restPresent,
  scriptIndex,
  secondPresent,
  seg,
  totalBlend,
} from "./crossStitchMotion";
import { CHIP, CHIP_GAP, INK, styles } from "./crossStitchStyles";
import {
  Digits,
  ThreadName,
  type UserName,
  stitchDuration,
  userColour,
  userGlyphPresent,
  userNeedleTarget,
  userShift,
  userWorked,
  zeros,
} from "./crossStitchParts";

/*
 Cross Stitch Input — "Stitchbook · Name your sampler". A wooden hoop of cream aida cloth fills the
 middle of the screen; whatever is typed in the name field is cross-stitched onto the cloth by a
 needle and thread, one X at a time, in the colour of the chosen skein. The stitch counter and the
 thread's name roll as they change.

 The cloth, hoop and printed border are one image baked at 2×; the name's stitches, the needle and
 its thread are recorded into one picture per frame on the UI thread (one Canvas).

 Touch: typing stitches each new letter (0.15 s + 55 ms a stitch), the needle following the newest
 letter and going back to rest when idle; the name re-centres on the grid with a 0.35 s glide; a skein
 chip re-threads the whole name (0.3 s). Tapping the hoop focuses the field. With autoPlay, 2 s after
 the field loses focus the cloth cross-fades back to the loop's first frame (0.8 s) and the loop
 starts again. Reduced motion: "Hi" stays stitched, nothing moves by itself, typed letters appear
 whole.
 */

const AUTO = 0;
const USER = 1;
const RETURN = 2;
const STILL = 3;
const RETURN_FOR = 0.8;
const GLIDE = 0.35;

const EASE_SHEET = Easing.bezier(0.32, 0.72, 0, 1);

export function CrossStitchInput({ autoPlay = false }: { autoPlay?: boolean }) {
  const insets = useSafeAreaInsets();
  const reduced = useReducedMotion();
  const auto = autoPlay && !reduced;
  const [size, setSize] = useState({ width: 0, height: 0 });
  const [hero, setHero] = useState({ x: 0, y: 0, width: 0, height: 0 });
  const [text, setText] = useState(REST_TEXT);
  const [thread, setThread] = useState(REST_THREAD);
  const input = useRef<TextInput>(null);

  const geo = useMemo(
    () =>
      size.width > 0 && hero.width > 0
        ? makeGeo(size.width, size.height, hero)
        : null,
    [size, hero],
  );
  const restL = useMemo(() => layoutName(REST_TEXT), []);
  const secondL = useMemo(() => layoutName(SECOND_TEXT), []);
  const border = useMemo(() => borderStitches(), []);
  const scene = useSharedValue<SkImage | null>(null);
  useEffect(() => {
    if (!geo) return undefined;
    const image = bakeScene(geo, 2, border);
    scene.set(image);
    return () => {
      scene.set(null);
      image?.dispose();
    };
  }, [geo, border, scene]);

  const now = useSharedValue(0);
  const loopT = useSharedValue(0);
  const mode = useSharedValue(auto ? AUTO : STILL);
  const modeAt = useSharedValue(0);
  const lastTouch = useSharedValue(0);
  const focused = useSharedValue(0);
  const fieldBottom = useSharedValue(0);
  const chipUser = useSharedValue(REST_THREAD);
  const needle = useSharedValue<number[]>([0, 0, 0, 0, 0]);
  const initialUser = useMemo<UserName>(
    () => ({
      layout: restL,
      typedAt: restL.count.map(() => -100),
      fromX: restL.count.map(() => 0),
      fromY: restL.count.map(() => 0),
      movedAt: -100,
      thread: REST_THREAD,
      threadFrom: THREADS[REST_THREAD].rgb,
      threadAt: -100,
    }),
    [restL],
  );
  const user = useSharedValue<UserName>(initialUser);
  const userRef = useRef<UserName>(initialUser);

  const resetToRest = () => {
    setText(REST_TEXT);
    setThread(REST_THREAD);
  };

  useFrameCallback((info) => {
    const dt = Math.min(0.05, (info.timeSincePreviousFrame ?? 16) / 1000);
    const t = now.get() + dt;
    now.set(t);
    const m = mode.get();
    if (m === AUTO) loopT.set((loopT.get() + dt) % LOOP);
    if (m === USER && auto && focused.get() === 0 && t - lastTouch.get() > 2) {
      mode.set(RETURN);
      modeAt.set(t);
      scheduleOnRN(resetToRest);
    }
    if (m === RETURN && t - modeAt.get() >= RETURN_FOR) {
      mode.set(AUTO);
      loopT.set(0);
    }
    // The needle under typing chases its target on a critically damped follow.
    if ((m === USER || m === RETURN) && geo) {
      const u = user.get();
      const target = userNeedleTarget(
        u,
        geo,
        t,
        userShift(u, t, 0),
        userShift(u, t, 1),
      );
      const n = needle.get();
      const k = 1 - Math.exp(-dt * (target[4] > 0.5 ? 26 : 9));
      needle.set([
        n[0] + (target[0] - n[0]) * k,
        n[1] + (target[1] - n[1]) * k,
        target[2],
        target[3],
        target[4],
      ]);
    }
  });

  // The field follows the script while the loop runs (a handful of updates per loop, never per frame).
  useAnimatedReaction(
    () => (mode.get() === AUTO ? scriptIndex(loopT.get()) : -1),
    (i, prev) => {
      if (i >= 0 && i !== prev) scheduleOnRN(setText, TEXT_SCRIPT[i][1]);
    },
  );
  useAnimatedReaction(
    () => (mode.get() === AUTO ? Math.round(chipPosition(loopT.get())) : -1),
    (i, prev) => {
      if (i >= 0 && i !== prev) scheduleOnRN(setThread, i);
    },
  );

  const chipPos = useDerivedValue(() => {
    const m = mode.get();
    if (m === AUTO) return chipPosition(loopT.get());
    if (m === STILL) return chipUser.get();
    if (m === RETURN) {
      const k = easeInOut(
        seg(now.get(), modeAt.get(), modeAt.get() + RETURN_FOR),
      );
      return chipUser.get() + (REST_THREAD - chipUser.get()) * k;
    }
    return chipUser.get();
  });

  const counts = useDerivedValue(() => {
    const B = border.cx.length;
    const m = mode.get();
    const t = loopT.get();
    const autoDone =
      B + restPresent(t, restL.total) + secondPresent(t, secondL.total);
    const autoTotal =
      B + restL.total + (secondL.total - restL.total) * totalBlend(t);
    if (m === AUTO) return [autoDone, autoTotal];
    if (m === STILL) return [B + restL.total, B + restL.total];
    const u = user.get();
    const done = B + userWorked(u, now.get());
    const total = B + u.layout.total;
    if (m === RETURN) {
      const k = easeInOut(
        seg(now.get(), modeAt.get(), modeAt.get() + RETURN_FOR),
      );
      return [
        done + (B + restL.total - done) * k,
        total + (B + restL.total - total) * k,
      ];
    }
    return [done, total];
  });
  const done = useDerivedValue(() => counts.get()[0]);
  const total = useDerivedValue(() => counts.get()[1]);

  const picture = useDerivedValue(() => {
    const recorder = Skia.PictureRecorder();
    const canvas = recorder.beginRecording(
      Skia.XYWHRect(0, 0, Math.max(1, size.width), Math.max(1, size.height)),
    );
    const image = scene.get();
    if (!geo || !image) return recorder.finishRecordingAsPicture();
    canvas.drawImageRect(
      image,
      Skia.XYWHRect(0, 0, image.width(), image.height()),
      Skia.XYWHRect(0, 0, geo.w, geo.h),
      Skia.Paint(),
    );
    const m = mode.get();
    const tNow = now.get();
    if (m === AUTO || m === STILL) {
      drawLoopFrame(
        canvas,
        geo,
        m === AUTO ? loopT.get() : 0,
        1,
        true,
        restL,
        secondL,
      );
    } else {
      const u = user.get();
      const k =
        m === RETURN
          ? easeInOut(seg(tNow, modeAt.get(), modeAt.get() + RETURN_FOR))
          : 0;
      const sx = userShift(u, tNow, 0);
      const sy = userShift(u, tNow, 1);
      const rgb = userColour(u, tNow);
      const gp = reduced ? u.layout.count : userGlyphPresent(u, tNow);
      drawStitches(
        canvas,
        geo,
        u.layout.cx,
        u.layout.cy,
        u.layout.total,
        rgb,
        1 - k,
        sx,
        sy,
        u.layout.glyphOf,
        gp,
        u.layout.first,
      );
      if (k > 0) drawLoopFrame(canvas, geo, 0, k, false, restL, secondL);
      const n = needle.get();
      const x = n[0] + (geo.parkX - n[0]) * k;
      const y = n[1] + (geo.parkY - n[1]) * k;
      drawNeedle(canvas, x, y, -0.95, n[4] * 0.5, n[2], n[3], rgb, 1);
    }
    return recorder.finishRecordingAsPicture();
  });

  /** Hands the screen to the user: the name on the cloth becomes theirs, mid-stitch if it was. */
  const takeOver = () => {
    const shown = text;
    const L = layoutName(shown);
    const which = shown === SECOND_TEXT ? 1 : shown === REST_TEXT ? 0 : -1;
    const current = thread;
    scheduleOnUI(() => {
      "worklet";
      const t = now.get();
      const m = mode.get();
      lastTouch.set(t);
      if (m === USER) return;
      const typedAt: number[] = [];
      const lt = loopT.get();
      const present =
        m === AUTO
          ? which === 1
            ? secondPresent(lt, L.total)
            : which === 0
              ? restPresent(lt, L.total)
              : L.total
          : L.total;
      for (let i = 0; i < L.count.length; i += 1) {
        const frac =
          L.count[i] === 0
            ? 1
            : Math.min(1, Math.max(0, (present - L.first[i]) / L.count[i]));
        typedAt.push(
          t - frac * stitchDuration(L.count[i]) - (frac >= 1 ? 1 : 0),
        );
      }
      const next: UserName = {
        layout: L,
        typedAt,
        fromX: zeros(L.count.length),
        fromY: zeros(L.count.length),
        movedAt: -100,
        thread: current,
        threadFrom: THREADS[current].rgb,
        threadAt: -100,
      };
      user.set(next);
      chipUser.set(current);
      if (m === AUTO && geo) {
        const n = autoNeedle(lt, geo, restL, secondL);
        needle.set([n[0], n[1], n[4], n[5], 0]);
      } else if (m !== RETURN && geo) {
        needle.set([geo.parkX, geo.parkY, geo.parkX - 30, geo.parkY, 0]);
      }
      mode.set(USER);
      scheduleOnRN(syncRef, next);
    });
  };
  const syncRef = (u: UserName) => {
    userRef.current = u;
  };

  const onType = (value: string) => {
    const next = value.slice(0, MAX_LETTERS);
    setText(next);
    const t = now.get();
    const prev = userRef.current;
    const L = layoutName(next);
    const k = 1 - easeInOut(seg(t, prev.movedAt, prev.movedAt + GLIDE));
    const typedAt: number[] = [];
    const fromX: number[] = [];
    const fromY: number[] = [];
    const prevChars = Array.from(text);
    const chars = Array.from(next);
    for (let i = 0; i < L.count.length; i += 1) {
      const same = i < prev.layout.count.length && prevChars[i] === chars[i];
      typedAt.push(same ? prev.typedAt[i] : t);
      fromX.push(same ? prev.layout.col[i] + prev.fromX[i] * k - L.col[i] : 0);
      fromY.push(same ? prev.layout.row[i] + prev.fromY[i] * k - L.row[i] : 0);
    }
    const u: UserName = {
      ...prev,
      layout: L,
      typedAt,
      fromX,
      fromY,
      movedAt: t,
    };
    userRef.current = u;
    user.set(u);
    lastTouch.set(t);
    if (chars.length > prevChars.length && process.env.EXPO_OS === "ios")
      Haptics.selectionAsync().catch(() => {});
  };

  const pickThread = (i: number) => {
    if (mode.get() !== USER) takeOver();
    setThread(i);
    Haptics.selectionAsync().catch(() => {});
    scheduleOnUI(() => {
      "worklet";
      const t = now.get();
      const u = user.get();
      const next = {
        ...u,
        thread: i,
        threadFrom: userColour(u, t),
        threadAt: t,
      };
      user.set(next);
      chipUser.set(withSpring(i, { duration: 400, dampingRatio: 0.75 }));
      lastTouch.set(t);
      scheduleOnRN(syncRef, next);
    });
  };

  const onFocus = () => {
    focused.set(1);
    if (mode.get() !== USER) takeOver();
  };
  const onBlur = () => {
    focused.set(0);
    lastTouch.set(now.get());
  };

  // The keyboard pushes the whole screen up just enough to keep the field visible.
  const keyboard = useSharedValue(0);
  useEffect(() => {
    const ios = process.env.EXPO_OS === "ios";
    const show = Keyboard.addListener(
      ios ? "keyboardWillShow" : "keyboardDidShow",
      (e) => {
        keyboard.set(
          withTiming(e.endCoordinates.height, {
            duration: e.duration || 250,
            easing: EASE_SHEET,
          }),
        );
      },
    );
    const hide = Keyboard.addListener(
      ios ? "keyboardWillHide" : "keyboardDidHide",
      (e) => {
        keyboard.set(
          withTiming(0, { duration: e?.duration || 250, easing: EASE_SHEET }),
        );
      },
    );
    return () => {
      show.remove();
      hide.remove();
    };
  }, [keyboard]);
  const lift = useAnimatedStyle(() => {
    const room = size.height - fieldBottom.get() - 12;
    return { transform: [{ translateY: -Math.max(0, keyboard.get() - room) }] };
  });

  const ring = useAnimatedStyle(() => ({
    transform: [{ translateX: chipPos.get() * (CHIP + CHIP_GAP) }],
  }));

  const onRoot = (e: LayoutChangeEvent) => {
    const { width, height } = e.nativeEvent.layout;
    if (width !== size.width || height !== size.height)
      setSize({ width, height });
  };
  const onHero = (e: LayoutChangeEvent) => {
    const { x, y, width, height } = e.nativeEvent.layout;
    if (width !== hero.width || height !== hero.height || y !== hero.y)
      setHero({ x, y, width, height });
  };

  return (
    <View style={styles.root} onLayout={onRoot}>
      <Animated.View style={[StyleSheet.absoluteFill, lift]}>
        <Canvas style={StyleSheet.absoluteFill}>
          <Picture picture={picture} />
        </Canvas>
        <View
          style={[
            styles.column,
            { paddingTop: insets.top + 6, paddingBottom: insets.bottom + 12 },
          ]}
        >
          <View style={styles.nav}>
            <View style={styles.navButton}>
              <SymbolView
                name="chevron.left"
                size={17}
                weight="semibold"
                tintColor={INK}
                fallback={<Text style={styles.navGlyph}>‹</Text>}
              />
            </View>
            <Text style={styles.navTitle}>Stitchbook</Text>
            <View style={styles.kit}>
              <Text style={styles.kitText}>KIT 04</Text>
            </View>
          </View>
          <View style={styles.titles}>
            <Text style={styles.title}>Name your sampler</Text>
            <Text style={styles.subtitle}>
              Two strands on 14-count aida · up to 10 letters
            </Text>
          </View>

          <Pressable
            style={styles.hero}
            onLayout={onHero}
            onPress={() => input.current?.focus()}
            accessibilityRole="button"
            accessibilityLabel={`Sampler preview reading ${text || "nothing yet"}. Double tap to edit the name.`}
          />

          <View style={styles.stats}>
            <View style={styles.stat}>
              <Text style={styles.statLabel}>Stitches</Text>
              <View style={styles.statValue}>
                <Digits value={done} places={3} />
                <Text style={styles.slash}> / </Text>
                <Digits value={total} places={3} />
              </View>
            </View>
            <View style={[styles.stat, { alignItems: "flex-end" }]}>
              <Text style={styles.statLabel}>Thread</Text>
              <ThreadName position={chipPos} />
            </View>
          </View>

          <View
            style={styles.field}
            onLayout={(e) => {
              fieldBottom.set(
                e.nativeEvent.layout.y + e.nativeEvent.layout.height,
              );
            }}
          >
            <Text style={styles.fieldLabel}>Name on the sampler</Text>
            <TextInput
              ref={input}
              value={text}
              onChangeText={onType}
              onFocus={onFocus}
              onBlur={onBlur}
              maxLength={MAX_LETTERS}
              autoCorrect={false}
              autoCapitalize="words"
              returnKeyType="done"
              placeholder="Type a name"
              placeholderTextColor="#B3A594"
              selectionColor={THREADS[thread].hex}
              style={styles.input}
            />
            <Text style={styles.letters}>
              {Array.from(text).length}/{MAX_LETTERS}
            </Text>
          </View>

          <View style={styles.chipsRow}>
            <View style={styles.chips}>
              <Animated.View pointerEvents="none" style={[styles.ring, ring]} />
              {THREADS.map((th, i) => (
                <Pressable
                  key={th.name}
                  onPress={() => pickThread(i)}
                  accessibilityRole="button"
                  accessibilityLabel={`${th.name} thread`}
                  accessibilityState={{ selected: thread === i }}
                  hitSlop={6}
                  style={({ pressed }) => [
                    styles.chip,
                    {
                      backgroundColor: th.hex,
                      transform: [{ scale: pressed ? 0.94 : 1 }],
                    },
                  ]}
                >
                  <View style={styles.chipShine} />
                </Pressable>
              ))}
            </View>
          </View>

          <Pressable
            style={({ pressed }) => [
              styles.cta,
              { transform: [{ scale: pressed ? 0.97 : 1 }] },
            ]}
            accessibilityRole="button"
          >
            <Text style={styles.ctaText}>Save sampler</Text>
          </Pressable>
        </View>
      </Animated.View>
    </View>
  );
}
