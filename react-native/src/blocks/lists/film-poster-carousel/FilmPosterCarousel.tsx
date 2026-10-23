import { Image } from 'expo-image';
import * as Haptics from 'expo-haptics';
import { LinearGradient } from 'expo-linear-gradient';
import { StatusBar } from 'expo-status-bar';
import { useCallback, useEffect, useRef } from 'react';
import { Pressable, StyleSheet, Text, useWindowDimensions, View } from 'react-native';
import { Gesture, GestureDetector, GestureHandlerRootView } from 'react-native-gesture-handler';
import Animated, {
  Easing, ReduceMotion, type SharedValue, cancelAnimation, useAnimatedReaction, useAnimatedStyle,
  useReducedMotion, useSharedValue, withDelay, withRepeat, withSpring, withTiming,
} from 'react-native-reanimated';
import Svg, { ClipPath, Defs, G, Path, Rect } from 'react-native-svg';
import { scheduleOnRN } from 'react-native-worklets';

import {
  DOWN_SPRING, FLICK, IDLE_RESUME_MS, LIFT, LOOP_MS, PAGE_SPRING, PICK_SPRING, ROOM, SHEEN, STARS_TOTAL, THROW, WAIT,
  bobAt, poseAt, starScale,
} from './motion';
import { FILMS, LAYOUT } from './theme';

export { LOOP_MS } from './motion';

const NEVER = ReduceMotion.Never;
const LAST = FILMS.length - 1;
type Film = (typeof FILMS)[number];
const FADE_STOPS = [0, 0.2, 0.42, 0.62, 0.82, 1] as const;
type TextSpec = { size: number; weight: string; tracking: number };
const spring = (s: { response: number; damping: number }, velocity = 0) =>
  ({ duration: s.response * 1000, dampingRatio: s.damping, velocity, reduceMotion: NEVER });

// Measured on device: in a 1.2 em line box, SF Pro's baseline sits 0.97 em below the box top.
const BASELINE = 0.97;
function textTop(baseline: number, size: number) {
  return baseline - size * BASELINE;
}
function face(spec: TextSpec) {
  return {
    fontSize: spec.size, lineHeight: spec.size * 1.2, letterSpacing: spec.tracking,
    fontWeight: spec.weight as '500' | '600' | '700' | '800',
  };
}
const clamp01 = (v: number) => {
  'worklet';
  return Math.min(1, Math.max(0, v));
};

// A five-point star with the pin's full body (inner radius 0.47 of the outer one).
const STAR = (() => {
  const pts: string[] = [];
  for (let i = 0; i < 10; i++) {
    const r = i % 2 === 0 ? 11.5 : 11.5 * LAYOUT.stars.inner;
    const a = -Math.PI / 2 + (i * Math.PI) / 5;
    pts.push(`${(12 + r * Math.cos(a)).toFixed(2)} ${(13 + r * Math.sin(a)).toFixed(2)}`);
  }
  return `M${pts.join(' L')} Z`;
})();
function StarGlyph({ size, fill, id }: { size: number; fill: number; id: string }) {
  return (
    <Svg width={size} height={size} viewBox="0 0 24 24">
      <Defs><ClipPath id={id}><Rect x={0} y={0} width={24 * fill} height={24} /></ClipPath></Defs>
      <Path d={STAR} fill={LAYOUT.starOff} stroke={LAYOUT.starOff} strokeWidth={1.4} strokeLinejoin="round" />
      <G clipPath={`url(#${id})`}><Path d={STAR} fill={LAYOUT.starColor} stroke={LAYOUT.starColor} strokeWidth={1.8} strokeLinejoin="round" /></G>
    </Svg>
  );
}
/** A star that swells in its turn as its film lands (swell: 0 → 1 over STARS_TOTAL). */
function Star({ k, swell, film, reduced }: { k: number; swell: SharedValue<number>; film: Film; reduced: boolean }) {
  const style = useAnimatedStyle(() => ({ transform: [{ scale: reduced ? 1 : starScale(swell.get(), k) }] }));
  return (
    <Animated.View style={style}>
      <StarGlyph id={`${film.id}${k}`} size={LAYOUT.stars.size} fill={Math.max(0, Math.min(1, film.rating - k))} />
    </Animated.View>
  );
}

function FilmInfo({ film, swell, reduced }: { film: Film; swell: SharedValue<number>; reduced: boolean }) {
  const { title, meta, duration, stars, tags } = LAYOUT;
  return (
    <View style={StyleSheet.absoluteFill} pointerEvents="none">
      <View style={[styles.line, { top: textTop(title.baseline, title.size) }]}>
        <Text style={[styles.text, face(title)]} numberOfLines={1}>{film.title}</Text>
      </View>
      <View style={[styles.line, { top: textTop(meta.baseline, meta.size) }]}>
        <Text style={[styles.text, face(meta), { color: film.meta, marginLeft: meta.x - title.x }]}>{`${film.year}  ·  ${film.director}`}</Text>
      </View>
      <View style={[styles.line, { top: textTop(duration.baseline, duration.size) }]}>
        <Text style={[styles.text, face(duration), { marginLeft: duration.x - title.x }]}>{film.duration}</Text>
      </View>
      <View style={[styles.line, { top: stars.cy - stars.size / 2 }]}>
        <View style={[styles.stars, { marginLeft: stars.cx - stars.size / 2 - title.x }]} accessible accessibilityLabel={`${film.rating} out of 5 stars`}>
          {[0, 1, 2, 3, 4].map((k) => <Star key={k} k={k} swell={swell} film={film} reduced={reduced} />)}
        </View>
      </View>
      <View style={[styles.line, { top: tags.top }]}>
        <View style={[styles.tags, { marginLeft: tags.x - title.x }]}>
          {film.tags.map((t) => (
            <View key={t} style={[styles.tag, { backgroundColor: film.chip }]}>
              <Text style={[styles.tagText, face(tags)]}>{t}</Text>
            </View>
          ))}
        </View>
      </View>
    </View>
  );
}

function ChevronGlyph({ color }: { color: string }) {
  const { w, h, stroke } = LAYOUT.chevron;
  const s = stroke / 2;
  return (
    <Svg width={w} height={h} viewBox={`0 0 ${w} ${h}`}>
      <Path d={`M${s} ${h - s} L${w / 2} ${s} L${w - s} ${h - s}`} stroke={color} strokeWidth={stroke} fill="none" strokeLinecap="round" strokeLinejoin="round" />
    </Svg>
  );
}

/** The pin's "more" chevron, breathing upward on the loop clock. */
function Chevron({ color, clock, reduced }: { color: string; clock: SharedValue<number>; reduced: boolean }) {
  const style = useAnimatedStyle(() => ({ transform: [{ translateY: reduced ? 0 : bobAt(clock.get()) }] }));
  const { cx, top, w } = LAYOUT.chevron;
  return <Animated.View style={[styles.chevron, { left: cx - w / 2, top }, style]}><ChevronGlyph color={color} /></Animated.View>;
}

/**
 * One film as a whole card: art, panel, text and its own WATCH NOW bar. At rest the front card fills
 * the screen exactly as the approved copy. Picked up (lift), it becomes a card with rounded corners
 * and a shadow; thrown (page), it turns away in perspective while the next card comes forward.
 */
function FilmCard({ film, index, page, lift, swell, clock, width, reduced, press }: {
  film: Film; index: number; page: SharedValue<number>; lift: SharedValue<number>; swell: SharedValue<number>;
  clock: SharedValue<number>; width: number; reduced: boolean; press: SharedValue<number>;
}) {
  const { panelBottom, panelRadius, footerLabel, posterH } = LAYOUT;
  const outer = useAnimatedStyle(() => {
    const d = index - page.get();
    if (reduced) return { opacity: d <= 0 ? 1 - clamp01(-d) : 1 };
    const l = lift.get();
    const lc = clamp01(l);
    const base = 1 - LIFT.scale * l;
    if (d <= 0) {
      const u = clamp01(-d);
      return {
        borderRadius: LIFT.radius * lc, shadowOpacity: LIFT.shadow * lc,
        // Thrown like a card off a table: it slides away and tilts a little in the plane of the screen.
        transform: [{ translateX: -u * width * THROW.x }, { translateY: THROW.drop * u * u }, { rotate: `${-THROW.tilt * u}deg` },
          { scale: base * (1 - THROW.shrink * u) }],
      };
    }
    const v = clamp01(d);
    return { borderRadius: LIFT.radius * lc, shadowOpacity: LIFT.shadow * lc * (1 - v), transform: [{ scale: base * (1 - WAIT.scale * v) }] };
  });
  const inner = useAnimatedStyle(() => ({ borderRadius: reduced ? 0 : LIFT.radius * clamp01(lift.get()) }));
  const dim = useAnimatedStyle(() => ({ opacity: reduced ? 0 : WAIT.dim * clamp01(index - page.get()) }));
  // The art lags its card while it is thrown: parallax inside the card.
  const art = useAnimatedStyle(() => (reduced ? {} : { transform: [{ translateX: clamp01(page.get() - index) * width * THROW.artLag }] }));
  // Light crossing the art as the card turns; only a card in the air shines.
  const bandW = width * SHEEN.width;
  const sheen = useAnimatedStyle(() => {
    const d = index - page.get();
    const r = d <= 0 ? clamp01(-d) : 1 - clamp01(d);
    return {
      opacity: reduced ? 0 : clamp01(lift.get()) * Math.sin(Math.PI * r),
      transform: [{ translateX: width * (1 - SHEEN.travel * r) - bandW / 2 }, { rotate: `${SHEEN.slant}deg` }],
    };
  });
  const footerInk = useAnimatedStyle(() => ({ opacity: 1 - 0.35 * press.get() }));
  const fadeColors = [`${film.panel}00`, `${film.panel}1F`, `${film.panel}66`, `${film.panel}B3`, `${film.panel}E6`, film.panel] as const;
  return (
    <Animated.View style={[StyleSheet.absoluteFill, styles.cardOuter, { backgroundColor: film.panel }, outer]}>
      <Animated.View style={[StyleSheet.absoluteFill, styles.cardInner, inner]}>
        {/* Footer first: the panel's rounded bottom lies over it. */}
        <View style={[styles.footer, { top: panelBottom - panelRadius - 10, backgroundColor: film.footer }]}>
          <Pressable style={StyleSheet.absoluteFill} accessibilityRole="button" accessibilityLabel="Watch now"
            onPressIn={() => press.set(withTiming(1, { duration: 120 }))} onPressOut={() => press.set(withTiming(0, { duration: 180 }))} />
          <Animated.Text pointerEvents="none"
            style={[styles.footerText, face(footerLabel), { color: film.footerInk, top: textTop(footerLabel.baseline, footerLabel.size) - (panelBottom - panelRadius - 10) }, footerInk]}>
            WATCH NOW
          </Animated.Text>
        </View>
        <View style={[styles.panelShadow, { height: panelBottom, borderBottomLeftRadius: panelRadius, borderBottomRightRadius: panelRadius, backgroundColor: film.panel }]} />
        <View style={[styles.panel, { height: panelBottom, borderBottomLeftRadius: panelRadius, borderBottomRightRadius: panelRadius, backgroundColor: film.panel }]}>
          <View style={[styles.posterFrame, { width, height: posterH }]}>
            <Animated.View style={[StyleSheet.absoluteFill, art]}>
              <Image source={film.poster} style={StyleSheet.absoluteFill} contentFit="cover" transition={0} />
            </Animated.View>
          </View>
          <View style={[styles.fade, { top: posterH * LAYOUT.fadeFrom, height: posterH * (1 - LAYOUT.fadeFrom) + 1 }]}>
            <LinearGradient colors={fadeColors} locations={FADE_STOPS} style={StyleSheet.absoluteFill} />
          </View>
          <Animated.View pointerEvents="none" style={[styles.sheen, { width: bandW, height: posterH * 1.3, top: -posterH * 0.15 }, sheen]}>
            <LinearGradient colors={['#FFFFFF00', `#FFFFFF${Math.round(SHEEN.alpha * 255).toString(16)}`, '#FFFFFF00']}
              start={{ x: 0, y: 0.5 }} end={{ x: 1, y: 0.5 }} style={StyleSheet.absoluteFill} />
          </Animated.View>
          <FilmInfo film={film} swell={swell} reduced={reduced} />
          <Chevron color={film.chevron} clock={clock} reduced={reduced} />
        </View>
        <Animated.View pointerEvents="none" style={[StyleSheet.absoluteFill, styles.dim, dim]} />
      </Animated.View>
    </Animated.View>
  );
}

/** The room the lifted card floats over: its own art, blurred and dimmed. Hidden while a card lies flat. */
function Room({ page, lift, reduced }: { page: SharedValue<number>; lift: SharedValue<number>; reduced: boolean }) {
  const room = useAnimatedStyle(() => ({ opacity: reduced ? 0 : clamp01(lift.get() * 4) }));
  return (
    <Animated.View pointerEvents="none" style={[StyleSheet.absoluteFill, room]}>
      {FILMS.map((film, i) => <RoomArt key={film.id} film={film} index={i} page={page} />)}
      <View style={[StyleSheet.absoluteFill, { backgroundColor: '#000', opacity: ROOM.dim }]} />
    </Animated.View>
  );
}
function RoomArt({ film, index, page }: { film: Film; index: number; page: SharedValue<number> }) {
  const style = useAnimatedStyle(() => ({ opacity: 1 - clamp01(Math.abs(index - page.get())) }));
  return (
    <Animated.View style={[StyleSheet.absoluteFill, style]}>
      <Image source={film.poster} style={StyleSheet.absoluteFill} contentFit="cover" blurRadius={ROOM.blur} transition={0} />
    </Animated.View>
  );
}

export function FilmPosterCarousel({ autoPlay = false, freezeAt }: { autoPlay?: boolean; freezeAt?: number }) {
  const { width } = useWindowDimensions();
  const reduced = useReducedMotion();
  const frozen = freezeAt !== undefined;
  const canAutoPlay = autoPlay && !reduced && !frozen;
  const pose0 = poseAt((freezeAt ?? 0) / 1000);
  const clock = useSharedValue(freezeAt ?? 0);
  const page = useSharedValue(pose0.page);
  const lift = useSharedValue(pose0.lift);
  const swell0 = useSharedValue(pose0.swell0);
  const swell1 = useSharedValue(pose0.swell1);
  const start = useSharedValue(0);
  const press = useSharedValue(0);
  const closePress = useSharedValue(0);
  const scripted = useSharedValue(canAutoPlay);

  // The loop: every value is a pure function of the clock while the script runs.
  useAnimatedReaction(() => clock.get(), (ms) => {
    if (!scripted.get()) return;
    const p = poseAt(ms / 1000);
    page.set(p.page);
    lift.set(p.lift);
    swell0.set(p.swell0);
    swell1.set(p.swell1);
  });

  const startClock = useCallback(() => {
    cancelAnimation(clock);
    clock.set(0);
    clock.set(withRepeat(withTiming(LOOP_MS, { duration: LOOP_MS, easing: Easing.linear, reduceMotion: NEVER }), -1, false));
  }, [clock]);

  const idle = useRef<ReturnType<typeof setTimeout> | null>(null);
  const resumeTimer = useRef<ReturnType<typeof setTimeout> | null>(null);
  useEffect(() => {
    if (frozen) return;
    cancelAnimation(clock);
    if (idle.current) clearTimeout(idle.current);
    if (resumeTimer.current) clearTimeout(resumeTimer.current);
    if (canAutoPlay) {
      scripted.set(true);
      startClock();
    } else if (!reduced) {
      scripted.set(false);
      clock.set(withRepeat(withTiming(LOOP_MS, { duration: LOOP_MS, easing: Easing.linear, reduceMotion: NEVER }), -1, false));
    } else {
      scripted.set(false);
      page.set(Math.max(0, Math.min(LAST, Math.round(page.get()))));
      lift.set(0);
      press.set(0);
      closePress.set(0);
    }
    return () => {
      cancelAnimation(clock);
      if (idle.current) clearTimeout(idle.current);
      if (resumeTimer.current) clearTimeout(resumeTimer.current);
    };
  }, [canAutoPlay, clock, closePress, frozen, lift, page, press, reduced, scripted, startClock]);

  const takeOver = useCallback(() => {
    if (idle.current) clearTimeout(idle.current);
    if (resumeTimer.current) clearTimeout(resumeTimer.current);
    scripted.set(false);
  }, [scripted]);
  const scheduleResume = useCallback(() => {
    if (!canAutoPlay) return;
    if (idle.current) clearTimeout(idle.current);
    idle.current = setTimeout(() => {
      // Back to rest on the first film, then the loop starts over from its first frame.
      page.set(withTiming(0, { duration: 300, easing: Easing.bezier(0.23, 1, 0.32, 1), reduceMotion: NEVER }));
      lift.set(withTiming(0, { duration: 300, reduceMotion: NEVER }));
      resumeTimer.current = setTimeout(() => {
        scripted.set(true);
        startClock();
      }, 320);
    }, IDLE_RESUME_MS);
  }, [canAutoPlay, lift, page, scripted, startClock]);

  const landed = useCallback((to: number, changed: boolean) => {
    if (changed) Haptics.selectionAsync().catch(() => {});
    const swell = to === 0 ? swell0 : swell1;
    if (!reduced) {
      swell.set(0);
      swell.set(withDelay(450, withTiming(1, { duration: STARS_TOTAL * 1000, easing: Easing.linear, reduceMotion: NEVER })));
    }
  }, [reduced, swell0, swell1]);

  // The pan can swallow the touch end, so a finished pan also re-arms the idle timer.
  const released = useSharedValue(0);
  useAnimatedReaction(() => released.get(), (v, prev) => {
    if (prev !== null && v !== prev) scheduleOnRN(scheduleResume);
  });
  const settle = (target: number, velocity: number) => {
    'worklet';
    const to = Math.max(0, Math.min(LAST, target));
    const changed = to !== Math.round(start.get());
    page.set(reduced ? withTiming(to, { duration: 200, reduceMotion: NEVER }) : withSpring(to, spring(PAGE_SPRING, velocity)));
    lift.set(reduced ? 0 : withDelay(120, withSpring(0, spring(DOWN_SPRING))));
    scheduleOnRN(landed, to, changed);
  };
  const pan = Gesture.Pan().maxPointers(1).activeOffsetX([-10, 10])
    .onBegin(() => {
      scripted.set(false);
      cancelAnimation(page);
      start.set(Math.round(page.get()));
      // Picked up the moment the finger lands.
      if (!reduced) lift.set(withSpring(1, spring(PICK_SPRING)));
    })
    .onUpdate((e) => {
      const raw = start.get() - e.translationX / width;
      const over = raw < 0 ? raw : raw > LAST ? raw - LAST : 0;
      page.set(raw - over + over * 0.3);
    })
    .onEnd((e) => {
      const v = -e.velocityX / width;
      const flick = Math.abs(e.velocityX) / 1000 > FLICK ? Math.sign(v) : 0;
      settle(flick !== 0 ? start.get() + flick : Math.round(page.get() + v * 0.2), v);
    })
    .onFinalize((_e, success) => {
      if (!success) {
        // A touch that never became a drag: set the card back down where it was.
        page.set(reduced ? start.get() : withSpring(start.get(), spring(PAGE_SPRING)));
        lift.set(reduced ? 0 : withSpring(0, spring(DOWN_SPRING)));
      }
      released.set(released.get() + 1);
    });

  const closeStyle = useAnimatedStyle(() => ({ transform: [{ scale: 1 - 0.03 * closePress.get() }] }));
  const { close } = LAYOUT;
  const swells = [swell0, swell1];

  return (
    <GestureHandlerRootView style={styles.root}>
      <StatusBar style="light" hidden={false} />
      <GestureDetector gesture={pan}>
        <View style={styles.root} onTouchStart={takeOver} onTouchEnd={scheduleResume}>
          <Room page={page} lift={lift} reduced={reduced} />
          {/* Later films first: the page only runs between the two films, so the first one is always the
              card on top (thrown off, or coming back over the second). Each card sits in its own flat
              container, so a card turning in perspective never cuts into the one underneath. */}
          {FILMS.map((film, i) => ({ film, i })).reverse().map(({ film, i }) => (
            <View key={film.id} style={StyleSheet.absoluteFill} pointerEvents="box-none">
              <FilmCard film={film} index={i} page={page} lift={lift} swell={swells[i]} clock={clock}
                width={width} reduced={reduced} press={press} />
            </View>
          ))}
          <Animated.View style={[styles.close, { top: close.top, right: close.right, width: close.size, height: close.size, borderRadius: close.radius }, closeStyle]}>
            <Pressable accessibilityRole="button" accessibilityLabel="Close" style={styles.closeHit} hitSlop={2}
              onPressIn={() => closePress.set(withTiming(1, { duration: 120 }))}
              onPressOut={() => closePress.set(withTiming(0, { duration: 120 }))}>
              <Svg width={close.icon} height={close.icon} viewBox="0 0 24 24"><Path d="M3 3l18 18M21 3L3 21" stroke="#0A090D" strokeWidth={3} strokeLinecap="round" /></Svg>
            </Pressable>
          </Animated.View>
        </View>
      </GestureDetector>
    </GestureHandlerRootView>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: '#000' },
  cardOuter: { shadowColor: '#000', shadowRadius: LIFT.shadowBlur / 2, shadowOffset: { width: 0, height: LIFT.shadowY }, shadowOpacity: 0 },
  cardInner: { overflow: 'hidden', backgroundColor: '#000' },
  panel: { position: 'absolute', left: 0, right: 0, top: 0, overflow: 'hidden' },
  panelShadow: { position: 'absolute', left: 0, right: 0, top: 0, shadowColor: '#000', shadowOpacity: LAYOUT.panelShadow.opacity,
    shadowRadius: LAYOUT.panelShadow.radius, shadowOffset: { width: 0, height: LAYOUT.panelShadow.y } },
  posterFrame: { position: 'absolute', left: 0, top: 0, overflow: 'hidden' },
  fade: { position: 'absolute', left: 0, right: 0 },
  sheen: { position: 'absolute', left: 0 },
  dim: { backgroundColor: '#000' },
  line: { position: 'absolute', left: LAYOUT.title.x, right: 0 },
  text: { color: LAYOUT.ink },
  stars: { flexDirection: 'row', gap: LAYOUT.stars.pitch - LAYOUT.stars.size,
    shadowColor: '#000', shadowOpacity: LAYOUT.stars.halo, shadowRadius: 1.5, shadowOffset: { width: 0, height: 0 } },
  tags: { flexDirection: 'row', gap: LAYOUT.tags.gap },
  tag: { height: LAYOUT.tags.h, paddingHorizontal: LAYOUT.tags.padX, borderRadius: LAYOUT.tags.h / 2, justifyContent: 'center' },
  tagText: { color: '#FFFFFF' },
  chevron: { position: 'absolute' },
  footer: { position: 'absolute', left: 0, right: 0, bottom: 0 },
  footerText: { position: 'absolute', left: 0, right: 0, textAlign: 'center' },
  close: { position: 'absolute', backgroundColor: '#F5F5E9', zIndex: 1000 }, // above a card turned toward the viewer
  closeHit: { flex: 1, alignItems: 'center', justifyContent: 'center' },
});
