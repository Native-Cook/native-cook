import {
  BackdropFilter,
  Blur,
  BlurMask,
  Canvas,
  Fill,
  Group,
  Image as SkiaImage,
  Line,
  LinearGradient,
  Rect,
  RoundedRect,
  rect,
  rrect,
  useImage,
  vec,
} from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { SymbolView } from 'expo-symbols';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { StyleSheet, Text, useWindowDimensions, View, type AccessibilityActionEvent } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  Easing,
  useAnimatedReaction,
  useAnimatedStyle,
  useDerivedValue,
  useFrameCallback,
  useSharedValue,
  withSpring,
  withTiming,
  type SharedValue,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

import { useReducedMotionPreference } from '@/shared/useReducedMotionPreference';

import { CompareControls } from './CompareControls';
import {
  EVENING_S,
  GLINT_LEN,
  GLOW_W,
  HANDLE_H,
  HANDLE_W,
  HANDLE_Y,
  IDLE_S,
  LEAN,
  LINE_W,
  LOOP,
  MODE_AUTO,
  MODE_MANUAL,
  MODE_RETURN,
  PRESS_SQUASH_X,
  PRESS_SQUASH_Y,
  RELEASE_SPRING,
  REST,
  RETURN_S,
  SPEED_TAU,
  STOP_MAX,
  STOP_MIN,
  STRETCH_X,
  STRETCH_Y,
  clamp,
  filmEvening,
  filmGhost,
  filmPos,
  filmPress,
  filmVelocity,
  glintAt,
  glintGain,
  labelAfter,
  labelBefore,
  landing,
  rubber,
  stretchFor,
} from './compareMotion';

// Rendered in Blender for this block: one room, one camera, four versions.
const BEFORE_DAY = require('./assets/before-day.webp');
const AFTER_DAY = require('./assets/after-day.webp');
const BEFORE_EVENING = require('./assets/before-evening.webp');
const AFTER_EVENING = require('./assets/after-evening.webp');

const PAGE = '#f2eee6';
const INK = '#211d18';
const MUTE = '#7d7468';
const CARD = '#fbf9f4';
const HAIR = 'rgba(33,29,24,0.1)';

const SIDE = 16;
const RADIUS = 26;
const DOT = 46; // the ghost finger
const GRAB_R = 40; // pt around the line where a touch picks the handle up without moving it
const GLIDE_MS = 200; // a touch elsewhere: the divider glides under the finger
const MAX_ASPECT = 1.5; // the photo is at most this tall for its width
const STEP = 0.1; // one accessibility increment

const EASE_IN_OUT = Easing.inOut(Easing.cubic);
const FACTS = [
  { value: '6 weeks', label: 'Start to finish' },
  { value: '$8,420', label: 'Total spend' },
  { value: '14 m²', label: 'Floor area' },
];

function pressTick() {
  void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light);
}

function stopTick() {
  void Haptics.selectionAsync();
}

function put(sv: SharedValue<number>, v: number) {
  'worklet';
  if (sv.get() !== v) sv.set(v);
}

type Props = { autoPlay?: boolean };

/**
 * A before / after photo under a divider with mass. Drag the glass handle: it squashes under the
 * finger, stretches with speed, rubber-bands at the photo's edges and, let go, is carried on by a
 * spring that overshoots and settles. Day / Evening crossfades both photos under the divider.
 */
export function CompareRevealSlider({ autoPlay = true }: Props) {
  const { width, height } = useWindowDimensions();
  const insets = useSafeAreaInsets();
  const reduced = useReducedMotionPreference();
  const film = autoPlay && !reduced;
  const compact = height < 740;

  const contentW = Math.min(width, 480);
  const w = contentW - SIDE * 2;
  const top = insets.top + (compact ? 4 : 8);
  const headerH = compact ? 50 : 58;
  const gap = compact ? 10 : 16;
  const factsH = compact ? 54 : 64;
  const bottom = Math.max(insets.bottom, 12) + (compact ? 6 : 10);
  const photoH = Math.round(clamp(height - top - headerH - gap * 3 - factsH - 32 - bottom, w * 0.9, w * MAX_ASPECT));
  const handleY = Math.round(photoH * HANDLE_Y);

  const beforeDay = useImage(BEFORE_DAY);
  const afterDay = useImage(AFTER_DAY);
  const beforeEvening = useImage(BEFORE_EVENING);
  const afterEvening = useImage(AFTER_EVENING);
  const loaded = !!beforeDay && !!afterDay && !!beforeEvening && !!afterEvening;

  const [selected, setSelected] = useState(0);

  const mode = useSharedValue(film ? MODE_AUTO : MODE_MANUAL);
  const clock = useSharedValue(0); // the frame clock, seconds
  const loopStart = useSharedValue(-1);
  const lastTouch = useSharedValue(0);
  const returnAt = useSharedValue(0);
  const ready = useSharedValue(false); // photos decoded: the film may start

  const pos = useSharedValue(REST); // the divider, 0 … 1 across the photo
  const prev = useSharedValue(REST);
  const press = useSharedValue(0); // 1 while a finger is on the handle
  const stretch = useSharedValue(0); // -1 … 1 with the divider's speed
  const evening = useSharedValue(0); // 0 day … 1 evening
  const ghost = useSharedValue(0); // the film's finger
  const glint = useSharedValue(0); // the light on the line, 0 top … 1 bottom
  const holding = useSharedValue(0);
  const fingerX = useSharedValue(0);
  const grab = useSharedValue(0); // pt between the finger and the line while dragging

  useEffect(() => {
    if (loaded) ready.set(true);
  }, [loaded, ready]);

  useFrameCallback((frame) => {
    'worklet';
    const now = frame.timestamp / 1000;
    const dt = clamp(now - clock.get(), 1 / 240, 0.05);
    clock.set(now);
    const m = mode.get();
    if (m === MODE_AUTO) {
      if (!film) {
        mode.set(MODE_MANUAL);
        pos.set(REST);
        press.set(0);
        stretch.set(0);
        ghost.set(0);
        evening.set(0);
      } else {
        let t = 0;
        if (ready.get()) {
          if (loopStart.get() < 0) loopStart.set(now);
          t = (now - loopStart.get()) % LOOP;
        }
        put(pos, filmPos(t));
        put(press, filmPress(t));
        put(stretch, stretchFor(filmVelocity(t)));
        put(ghost, filmGhost(t));
        put(evening, filmEvening(t));
        put(glint, glintAt(t));
      }
    } else {
      if (holding.get() > 0) {
        pos.set(rubber((fingerX.get() + grab.get()) / w));
      } else if (film) {
        if (m === MODE_MANUAL && now - lastTouch.get() > IDLE_S) {
          pos.set(withSpring(REST, RELEASE_SPRING));
          evening.set(withTiming(0, { duration: RETURN_S * 1000, easing: EASE_IN_OUT }));
          returnAt.set(now);
          mode.set(MODE_RETURN);
        } else if (m === MODE_RETURN && now - returnAt.get() > RETURN_S + 0.5) {
          loopStart.set(now);
          pos.set(REST);
          stretch.set(0);
          mode.set(MODE_AUTO);
        }
      }
      // the handle stretches with the divider's measured speed
      const p = pos.get();
      const target = stretchFor((p - prev.get()) / dt);
      const k = 1 - Math.exp(-dt / SPEED_TAU);
      const s = stretch.get() + (target - stretch.get()) * k;
      put(stretch, Math.abs(s) < 0.002 ? 0 : s);
      put(glint, glintAt(now));
    }
    prev.set(pos.get());
  });

  /** A finger anywhere: the film stops where it is. */
  const take = useCallback(() => {
    'worklet';
    lastTouch.set(clock.get());
    if (mode.get() === MODE_MANUAL) return;
    mode.set(MODE_MANUAL);
    // stop whatever spring is running, where it is
    pos.set(pos.get());
    press.set(withTiming(0, { duration: 160 }));
    ghost.set(withTiming(0, { duration: 160 }));
  }, [clock, ghost, lastTouch, mode, pos, press]);

  const pan = Gesture.Pan()
    .minDistance(0)
    .onBegin((e) => {
      take();
      mode.set(MODE_MANUAL);
      holding.set(1);
      fingerX.set(e.x);
      const d = pos.get() * w - e.x;
      grab.set(d);
      // away from the handle the divider comes to the finger; on it, it stays where it was grabbed
      if (Math.abs(d) > GRAB_R) grab.set(withTiming(0, { duration: reduced ? 0 : GLIDE_MS }));
      press.set(withTiming(1, { duration: 120 }));
      scheduleOnRN(pressTick);
    })
    .onChange((e) => {
      fingerX.set(e.x);
      lastTouch.set(clock.get());
    })
    .onFinalize((e) => {
      if (holding.get() <= 0) return;
      holding.set(0);
      lastTouch.set(clock.get());
      press.set(withTiming(0, { duration: 180 }));
      const p = pos.get();
      const v = e.velocityX / w;
      if (reduced) {
        pos.set(withTiming(clamp(p, STOP_MIN, STOP_MAX), { duration: 160 }));
      } else {
        pos.set(withSpring(landing(p, v), { ...RELEASE_SPRING, velocity: v }));
      }
    });

  // a tick as the divider is pulled past a stop
  useAnimatedReaction(
    () => (holding.get() > 0 ? (pos.get() < STOP_MIN ? -1 : pos.get() > STOP_MAX ? 1 : 0) : 0),
    (now, before) => {
      if (now !== 0 && now !== before) scheduleOnRN(stopTick);
    },
  );

  // the segmented control follows the film
  useAnimatedReaction(
    () => evening.get() > 0.5,
    (now, before) => {
      if (now !== before) scheduleOnRN(setSelected, now ? 1 : 0);
    },
  );

  const choose = useCallback(
    (index: number) => {
      setSelected(index);
      lastTouch.set(clock.get());
      if (mode.get() !== MODE_MANUAL) {
        mode.set(MODE_MANUAL);
        pos.set(pos.get());
        press.set(withTiming(0, { duration: 160 }));
        ghost.set(withTiming(0, { duration: 160 }));
      }
      evening.set(withTiming(index, { duration: reduced ? 200 : EVENING_S * 1000, easing: EASE_IN_OUT }));
    },
    [clock, evening, ghost, lastTouch, mode, pos, press, reduced],
  );

  const onAccessibilityAction = useCallback(
    (event: AccessibilityActionEvent) => {
      const dir = event.nativeEvent.actionName === 'increment' ? 1 : event.nativeEvent.actionName === 'decrement' ? -1 : 0;
      if (dir === 0) return;
      lastTouch.set(clock.get());
      mode.set(MODE_MANUAL);
      ghost.set(0);
      press.set(0);
      pos.set(withTiming(clamp(pos.get() + dir * STEP, STOP_MIN, STOP_MAX), { duration: 200 }));
    },
    [clock, ghost, lastTouch, mode, pos, press],
  );

  // ------------------------------------------------------------ drawing

  const afterClip = useDerivedValue(() => {
    const x = clamp(pos.get(), 0, 1) * w;
    return rect(x, 0, w - x, photoH);
  });
  const lineTransform = useDerivedValue(() => [{ translateX: pos.get() * w }]);
  const glintTransform = useDerivedValue(() => [{ translateY: -GLINT_LEN / 2 + glint.get() * (photoH + GLINT_LEN) }]);
  const glintOpacity = useDerivedValue(() => glintGain(stretch.get()));
  const handleTransform = useDerivedValue(() => {
    const s = stretch.get();
    const p = press.get();
    const a = Math.abs(s);
    return [
      { translateX: pos.get() * w - LEAN * s },
      { translateY: handleY },
      { scaleX: 1 + PRESS_SQUASH_X * p + STRETCH_X * a },
      { scaleY: 1 - PRESS_SQUASH_Y * p - STRETCH_Y * a },
    ];
  });
  // lifted (not pressed) the handle floats: its soft shadow is wider and lighter
  const liftOpacity = useDerivedValue(() => 0.3 - 0.14 * press.get());
  const contactOpacity = useDerivedValue(() => 0.22 + 0.16 * press.get());

  const handleRect = useMemo(() => rrect(rect(-HANDLE_W / 2, -HANDLE_H / 2, HANDLE_W, HANDLE_H), HANDLE_W / 2, HANDLE_W / 2), []);

  const beforeLabel = useAnimatedStyle(() => {
    const a = labelBefore(pos.get());
    return { opacity: a, transform: [{ translateX: -6 * (1 - a) }] };
  });
  const afterLabel = useAnimatedStyle(() => {
    const a = labelAfter(pos.get());
    return { opacity: a, transform: [{ translateX: 6 * (1 - a) }] };
  });
  const ghostStyle = useAnimatedStyle(() => {
    const p = press.get();
    return {
      opacity: ghost.get() * 0.9,
      transform: [{ translateX: pos.get() * w - DOT / 2 }, { translateY: handleY - DOT / 2 }, { scale: 1.12 - 0.2 * p }],
    };
  });

  const titleSize = compact ? 26 : 30;

  return (
    <View style={styles.screen}>
      <StatusBar style="dark" />
      <View style={{ width: contentW, alignSelf: 'center', paddingHorizontal: SIDE, paddingTop: top }}>
        <View style={[styles.header, { height: headerH }]}>
          <View style={styles.headerText}>
            <Text numberOfLines={1} style={styles.kicker}>
              Roomwork · Project 214
            </Text>
            <Text numberOfLines={1} style={[styles.title, { fontSize: titleSize, lineHeight: titleSize + 5 }]}>
              Linden Street
            </Text>
          </View>
          <View accessibilityLabel="Share" accessibilityRole="button" style={styles.share}>
            <SymbolView name="square.and.arrow.up" size={17} tintColor={INK} weight="medium" />
          </View>
        </View>

        <View style={[styles.photoShadow, { height: photoH, marginTop: gap }]}>
          <GestureDetector gesture={pan}>
            <Animated.View
              accessibilityActions={[{ name: 'increment' }, { name: 'decrement' }]}
              accessibilityHint="Swipe up or down to move the divider"
              accessibilityLabel="Before and after, living room"
              accessibilityRole="adjustable"
              accessible
              onAccessibilityAction={onAccessibilityAction}
              style={[styles.photo, { height: photoH }]}>
              <Canvas style={{ width: w, height: photoH }}>
                <SkiaImage fit="cover" height={photoH} image={beforeDay} width={w} x={0} y={0} />
                <SkiaImage fit="cover" height={photoH} image={beforeEvening} opacity={evening} width={w} x={0} y={0} />
                <Group clip={afterClip}>
                  <SkiaImage fit="cover" height={photoH} image={afterDay} width={w} x={0} y={0} />
                  <SkiaImage fit="cover" height={photoH} image={afterEvening} opacity={evening} width={w} x={0} y={0} />
                </Group>
                {/* a little shade behind the labels */}
                <Rect height={96} width={w} x={0} y={0}>
                  <LinearGradient colors={['rgba(24,18,12,0.34)', 'rgba(24,18,12,0.12)', 'rgba(24,18,12,0)']} end={vec(0, 96)} start={vec(0, 0)} />
                </Rect>

                {/* the divider: a soft glow, the line, and a light running down it */}
                <Group transform={lineTransform}>
                  <Rect height={photoH} width={GLOW_W} x={-GLOW_W / 2} y={0}>
                    <LinearGradient
                      colors={['rgba(255,250,240,0)', 'rgba(255,250,240,0.2)', 'rgba(255,250,240,0)']}
                      end={vec(GLOW_W / 2, 0)}
                      start={vec(-GLOW_W / 2, 0)}
                    />
                  </Rect>
                  <Rect color="rgba(40,30,20,0.18)" height={photoH} width={LINE_W + 1} x={-LINE_W / 2} y={0} />
                  <Rect color="rgba(255,252,246,0.88)" height={photoH} width={LINE_W} x={-LINE_W / 2} y={0} />
                  <Group opacity={glintOpacity} transform={glintTransform}>
                    <Rect height={GLINT_LEN} width={8} x={-4} y={-GLINT_LEN / 2}>
                      <LinearGradient
                        colors={['rgba(255,255,255,0)', 'rgba(255,255,255,0.95)', 'rgba(255,255,255,0)']}
                        end={vec(0, GLINT_LEN / 2)}
                        start={vec(0, -GLINT_LEN / 2)}
                      />
                      <BlurMask blur={3.5} style="normal" />
                    </Rect>
                    <Rect height={GLINT_LEN * 0.6} width={LINE_W} x={-LINE_W / 2} y={(-GLINT_LEN * 0.6) / 2}>
                      <LinearGradient
                        colors={['rgba(255,255,255,0)', 'rgba(255,255,255,1)', 'rgba(255,255,255,0)']}
                        end={vec(0, GLINT_LEN * 0.3)}
                        start={vec(0, -GLINT_LEN * 0.3)}
                      />
                    </Rect>
                  </Group>
                </Group>

                {/* the handle: glass over the photo, two shadows, a lit rim, the grabber */}
                <Group transform={handleTransform}>
                  <Group opacity={liftOpacity} transform={[{ translateY: 9 }]}>
                    <RoundedRect color="rgb(28,18,8)" rect={handleRect}>
                      <BlurMask blur={12} style="normal" />
                    </RoundedRect>
                  </Group>
                  <Group opacity={contactOpacity} transform={[{ translateY: 1.5 }]}>
                    <RoundedRect color="rgb(28,18,8)" rect={handleRect}>
                      <BlurMask blur={2.5} style="normal" />
                    </RoundedRect>
                  </Group>
                  <Group clip={handleRect}>
                    <BackdropFilter filter={<Blur blur={9} mode="clamp" />} />
                    <Fill color="rgba(255,252,246,0.3)" />
                    <Rect height={HANDLE_H} width={HANDLE_W} x={-HANDLE_W / 2} y={-HANDLE_H / 2}>
                      <LinearGradient
                        colors={['rgba(255,255,255,0.5)', 'rgba(255,255,255,0.06)', 'rgba(255,255,255,0.18)']}
                        end={vec(0, HANDLE_H / 2)}
                        positions={[0, 0.55, 1]}
                        start={vec(0, -HANDLE_H / 2)}
                      />
                    </Rect>
                  </Group>
                  <RoundedRect rect={handleRect} strokeWidth={1.2} style="stroke">
                    <LinearGradient
                      colors={['rgba(255,255,255,0.95)', 'rgba(255,255,255,0.35)', 'rgba(255,255,255,0.6)']}
                      end={vec(0, HANDLE_H / 2)}
                      positions={[0, 0.6, 1]}
                      start={vec(0, -HANDLE_H / 2)}
                    />
                  </RoundedRect>
                  {/* grabber: two chevrons */}
                  <Line color="rgba(33,29,24,0.82)" p1={vec(-7, -6)} p2={vec(-12.5, 0)} strokeCap="round" strokeWidth={2.2} style="stroke" />
                  <Line color="rgba(33,29,24,0.82)" p1={vec(-12.5, 0)} p2={vec(-7, 6)} strokeCap="round" strokeWidth={2.2} style="stroke" />
                  <Line color="rgba(33,29,24,0.82)" p1={vec(7, -6)} p2={vec(12.5, 0)} strokeCap="round" strokeWidth={2.2} style="stroke" />
                  <Line color="rgba(33,29,24,0.82)" p1={vec(12.5, 0)} p2={vec(7, 6)} strokeCap="round" strokeWidth={2.2} style="stroke" />
                </Group>
              </Canvas>

              <Animated.View pointerEvents="none" style={[styles.label, styles.labelLeft, beforeLabel]}>
                <Text style={styles.labelText}>Before</Text>
              </Animated.View>
              <Animated.View pointerEvents="none" style={[styles.label, styles.labelRight, afterLabel]}>
                <Text style={styles.labelText}>After</Text>
              </Animated.View>
              <View pointerEvents="none" style={styles.photoEdge} />
              <Animated.View pointerEvents="none" style={[styles.dot, ghostStyle]} />
            </Animated.View>
          </GestureDetector>
        </View>

        <View style={[styles.facts, { height: factsH, marginTop: gap }]}>
          {FACTS.map((f, i) => (
            <View key={f.label} style={[styles.fact, i > 0 && styles.factRule]}>
              <Text numberOfLines={1} style={[styles.factValue, compact && styles.factValueCompact]}>
                {f.value}
              </Text>
              <Text numberOfLines={1} style={styles.factLabel}>
                {f.label}
              </Text>
            </View>
          ))}
        </View>

        <View style={{ marginTop: gap }}>
          <CompareControls onSelect={choose} selected={selected} width={w} />
        </View>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: PAGE },
  header: { flexDirection: 'row', alignItems: 'flex-end', justifyContent: 'space-between' },
  headerText: { flex: 1, paddingRight: 12 },
  kicker: { color: MUTE, fontSize: 14, lineHeight: 18, fontWeight: '500', fontVariant: ['tabular-nums'] },
  title: { color: INK, fontWeight: '700', letterSpacing: -0.4 },
  share: {
    width: 38,
    height: 38,
    borderRadius: 19,
    marginBottom: 2,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: CARD,
    boxShadow: '0 1px 2px rgba(60,40,20,0.14), 0 6px 14px rgba(60,40,20,0.08)',
  },
  photoShadow: {
    borderRadius: RADIUS,
    borderCurve: 'continuous',
    backgroundColor: '#d9d2c4',
    boxShadow: '0 2px 4px rgba(60,40,20,0.2), 0 18px 36px rgba(60,40,20,0.2)',
  },
  photo: { borderRadius: RADIUS, borderCurve: 'continuous', overflow: 'hidden' },
  photoEdge: {
    position: 'absolute',
    left: 0,
    right: 0,
    top: 0,
    bottom: 0,
    borderRadius: RADIUS,
    borderCurve: 'continuous',
    borderWidth: StyleSheet.hairlineWidth * 2,
    borderColor: 'rgba(255,255,255,0.22)',
  },
  label: {
    position: 'absolute',
    top: 14,
    height: 28,
    paddingHorizontal: 12,
    borderRadius: 14,
    justifyContent: 'center',
    backgroundColor: 'rgba(28,22,16,0.46)',
    borderWidth: StyleSheet.hairlineWidth,
    borderColor: 'rgba(255,255,255,0.22)',
  },
  labelLeft: { left: 14 },
  labelRight: { right: 14 },
  labelText: { color: '#fffaf1', fontSize: 13, fontWeight: '600', letterSpacing: 0.1 },
  dot: {
    position: 'absolute',
    left: 0,
    top: 0,
    width: DOT,
    height: DOT,
    borderRadius: DOT / 2,
    backgroundColor: 'rgba(255,255,255,0.3)',
    borderWidth: 1.5,
    borderColor: 'rgba(255,255,255,0.75)',
  },
  facts: {
    flexDirection: 'row',
    alignItems: 'center',
    borderRadius: 18,
    borderCurve: 'continuous',
    backgroundColor: CARD,
    boxShadow: '0 1px 2px rgba(60,40,20,0.1), 0 8px 20px rgba(60,40,20,0.06)',
  },
  fact: { flex: 1, paddingHorizontal: 14, gap: 2 },
  factRule: { borderLeftWidth: StyleSheet.hairlineWidth, borderLeftColor: HAIR },
  factValue: { color: INK, fontSize: 18, fontWeight: '600', fontVariant: ['tabular-nums'], letterSpacing: -0.2 },
  factValueCompact: { fontSize: 16 },
  factLabel: { color: MUTE, fontSize: 12.5 },
});
