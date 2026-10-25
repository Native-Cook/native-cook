import {
  BlurMask,
  Canvas,
  Circle,
  Group,
  Image,
  LinearGradient,
  RadialGradient,
  Rect,
  RoundedRect,
  Skia,
  type SkImage,
  useImage,
  vec,
} from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { useMemo, useState } from 'react';
import { type LayoutChangeEvent, PixelRatio, StyleSheet, Text, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  cancelAnimation,
  Easing,
  type SharedValue,
  useAnimatedReaction,
  useAnimatedStyle,
  useDerivedValue,
  useFrameCallback,
  useReducedMotion,
  useSharedValue,
  withSpring,
  withTiming,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

import { bakeReel, bakeRoom, CAPTION_H, LEVER_PULLED, LEVER_REST, layoutReel, SLIDES } from './viewmasterArt';
import { LOOP, reelPose, rubber, shutterFor, SLOTS, smoothstep, STEP_DEG, wrap } from './viewmasterMotion';

/*
 Viewmaster Reel — a travel-memories screen ("Reels — Iceland, 2025"): a red stereo viewer's
 window shows the current slide; under it the cardboard reel with its seven film chips, and the
 viewer's lever at its right. Pull the lever down or turn the reel with a finger: the reel turns
 one slot with a mechanical clunk, and the window blinks (its shutter closes between slots) to the
 next slide. Timeline in viewmasterMotion.ts (LOOP = 9 s).

 Touch: a finger on the reel turns it about its centre (the angle under the finger); on release
 it springs to the nearest slot the flick points at (0.5 s, damping ratio 0.55, carrying the
 angular velocity: a small overshoot, the clunk). The lever follows a downward drag (rubber-banded
 past its stop); past 82 % it advances the reel one slot (spring 0.42 s, damping ratio 0.5) and
 springs back on release (damping ratio 0.45). A selection tick per slot, a light impact per lever
 pull. 2 s after the last touch the reel eases back to slide 1 (0.9 s) and the loop restarts.
 Reduced motion: slide 1, still; the reel and lever still answer the finger without the wobble.
 */

type Props = {
  /** Pulls the lever three times and spins the reel back to the first slide, on a loop. */
  autoPlay?: boolean;
};

const BG = '#121C23';
const INK = '#EEF2F3';
const MUTE = 'rgba(238,242,243,0.56)';
const ACCENT = '#E0574A';
const HOLD_AFTER_TOUCH = 2;
const RETURN_MS = 900;
const STEP_RAD = (STEP_DEG * Math.PI) / 180;
const DEG = Math.PI / 180;

function feel(kind: 'tick' | 'pull') {
  if (process.env.EXPO_OS === 'web') return;
  if (kind === 'tick') Haptics.selectionAsync().catch(() => {});
  else Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
}

type WinRect = { x: number; y: number; w: number; h: number };

/** One slide in the window, shown only while it is the current one. */
function SlideLayer({ k, idx, image, win }: { k: number; idx: SharedValue<number>; image: SkImage | null; win: WinRect }) {
  const opacity = useDerivedValue(() => (idx.get() === k ? 1 : 0));
  const [x, y, w, h] = SLIDES[k].crop;
  const iw = image?.width() ?? 800;
  const ih = image?.height() ?? 600;
  const sx = win.w / w;
  const sy = win.h / h;
  return <Image image={image} x={win.x - x * sx} y={win.y - y * sy} width={iw * sx} height={ih * sy} fit="fill" opacity={opacity} />;
}

/**
 * One caption, in its own row of the clipped caption box: it rolls up out of the box as the next
 * one rolls in from below, fading over half a row, so two captions never sit on top of each other.
 */
function CaptionRow({ k, roll, row, title, place }: { k: number; roll: SharedValue<number>; row: number; title: string; place: string }) {
  const style = useAnimatedStyle(() => {
    const d = k - roll.get();
    return { opacity: Math.max(0, 1 - Math.abs(d) * 1.8), transform: [{ translateY: d * row }] };
  });
  return (
    <Animated.View style={[styles.captionRow, { height: row }, style]}>
      <Text style={styles.counter} maxFontSizeMultiplier={1.1}>{`VIEW ${String((k % SLOTS) + 1).padStart(2, '0')} / 07`}</Text>
      <Text style={styles.captionTitle} numberOfLines={1} maxFontSizeMultiplier={1.1}>
        {title}
      </Text>
      <Text style={styles.place} numberOfLines={1} maxFontSizeMultiplier={1.1}>
        {place}
      </Text>
    </Animated.View>
  );
}

export function ViewmasterReel({ autoPlay = false }: Props) {
  const insets = useSafeAreaInsets();
  const reduced = useReducedMotion();
  const auto = autoPlay && !reduced;
  const [size, setSize] = useState<{ w: number; h: number } | null>(null);
  const p0 = useImage(require('./assets/vestrahorn.jpg'));
  const p1 = useImage(require('./assets/falls.jpg'));
  const p2 = useImage(require('./assets/kirkjufell.jpg'));
  const p3 = useImage(require('./assets/river.jpg'));
  const p4 = useImage(require('./assets/road.jpg'));
  const p5 = useImage(require('./assets/cave.jpg'));
  const photos = useMemo(() => (p0 && p1 && p2 && p3 && p4 && p5 ? [p0, p1, p2, p3, p4, p5] : null), [p0, p1, p2, p3, p4, p5]);
  const L = useMemo(() => (size ? layoutReel(size.w, size.h, insets.top) : null), [size, insets.top]);
  const scale = Math.min(3, PixelRatio.get());
  const room = useMemo(() => (L ? bakeRoom(L, scale) : null), [L, scale]);
  const reel = useMemo(() => (L && photos ? bakeReel(L.disc.r, photos, scale) : null), [L, photos, scale]);

  const D = L?.disc ?? { cx: 0, cy: 0, r: 1 };
  const Wn = L?.win ?? { x: 0, y: 0, w: 1, h: 1 };
  const LV = L?.lever ?? { px: 0, py: 0, len: 1 };

  const clock = useSharedValue(0);
  const now = useSharedValue(0);
  const mode = useSharedValue(0);
  const touching = useSharedValue(0);
  const lastTouch = useSharedValue(0);
  const manPos = useSharedValue(0);
  const manLever = useSharedValue(0);
  /** 0 none, 1 the reel, 2 the lever. */
  const grab = useSharedValue(0);
  const lastAng = useSharedValue(0);
  const leverStart = useSharedValue(0);
  const fired = useSharedValue(0);

  useFrameCallback((f) => {
    const dt = Math.min(0.05, (f.timeSincePreviousFrame ?? 16) / 1000);
    now.set(now.get() + dt);
    const m = mode.get();
    if (m === 0) {
      if (auto) clock.set((clock.get() + dt) % LOOP);
    } else if (m === 1 && touching.get() === 0 && now.get() - lastTouch.get() > HOLD_AFTER_TOUCH) {
      mode.set(2);
      const ease = Easing.bezier(0.65, 0, 0.35, 1);
      manLever.set(withTiming(0, { duration: 300 }));
      manPos.set(
        withTiming(Math.round(manPos.get() / SLOTS) * SLOTS, { duration: RETURN_MS, easing: ease }, (done) => {
          if (done) {
            clock.set(0);
            mode.set(0);
          }
        }),
      );
    }
  });

  /** [pos, lever, shake, wobble time] */
  const view = useDerivedValue(() => {
    if (mode.get() === 0) {
      const t = clock.get();
      const p = reelPose(t);
      return [p.pos, p.lever, reduced ? 0 : p.shake, reduced ? 0 : t];
    }
    return [manPos.get(), manLever.get(), 0, reduced ? 0 : now.get() % LOOP];
  });
  const idx = useDerivedValue(() => wrap(Math.round(view.get()[0]), SLOTS));
  const shut = useDerivedValue(() => shutterFor(view.get()[0]));

  const discTransform = useDerivedValue(() => [{ rotate: -view.get()[0] * STEP_RAD }]);
  const leverTransform = useDerivedValue(() => [{ rotate: (LEVER_REST + (LEVER_PULLED - LEVER_REST) * view.get()[1]) * DEG }]);
  const wobble = useDerivedValue(() => {
    const v = view.get();
    const w = (2 * Math.PI * v[3]) / LOOP;
    return [
      { translateX: 3.5 * Math.sin(2 * w) },
      { translateY: 2.2 * Math.sin(3 * w + 0.7) + v[2] },
      { scale: 1.06 + 0.012 * Math.sin(w) },
    ];
  });
  const topLid = useDerivedValue(() => Skia.XYWHRect(Wn.x, Wn.y - 2, Wn.w, (Wn.h / 2 + 2) * shut.get()));
  const botLid = useDerivedValue(() => {
    const hh = (Wn.h / 2 + 2) * shut.get();
    return Skia.XYWHRect(Wn.x, Wn.y + Wn.h + 2 - hh, Wn.w, hh);
  });
  const viewerShake = useDerivedValue(() => [{ translateY: view.get()[2] * 0.5 }]);

  // A tick per slot that passes the gate under the finger.
  useAnimatedReaction(
    () => (mode.get() === 1 ? Math.round(view.get()[0]) : null),
    (cur, prev) => {
      if (cur !== null && prev !== null && prev !== undefined && cur !== prev) scheduleOnRN(feel, 'tick');
    },
  );

  const pan = Gesture.Pan()
    .minDistance(0)
    .onBegin((e) => {
      const v = view.get();
      const a = (LEVER_REST + (LEVER_PULLED - LEVER_REST) * v[1]) * DEG;
      const kx = LV.px + Math.cos(a) * LV.len;
      const ky = LV.py + Math.sin(a) * LV.len;
      const onLever = Math.hypot(e.x - kx, e.y - ky) < 48;
      const dd = Math.hypot(e.x - D.cx, e.y - D.cy);
      const onDisc = dd < D.r + 12;
      if (!onLever && !onDisc) {
        grab.set(0);
        return;
      }
      touching.set(1);
      lastTouch.set(now.get());
      cancelAnimation(manPos);
      cancelAnimation(manLever);
      if (mode.get() !== 1) {
        manPos.set(v[0]);
        manLever.set(v[1]);
        mode.set(1);
      }
      if (onLever) {
        grab.set(2);
        leverStart.set(manLever.get());
        fired.set(0);
      } else {
        grab.set(1);
        lastAng.set(Math.atan2(e.y - D.cy, e.x - D.cx));
      }
    })
    .onUpdate((e) => {
      const g = grab.get();
      if (g === 0) return;
      lastTouch.set(now.get());
      if (g === 1) {
        const ang = Math.atan2(e.y - D.cy, e.x - D.cx);
        let d = ang - lastAng.get();
        if (d > Math.PI) d -= 2 * Math.PI;
        if (d < -Math.PI) d += 2 * Math.PI;
        lastAng.set(ang);
        manPos.set(manPos.get() - d / STEP_RAD);
      } else {
        const lv = rubber(leverStart.get() + e.translationY / 72 - e.translationX / 220, 0, 1, 0.22);
        manLever.set(lv);
        if (lv > 0.82 && fired.get() === 0) {
          fired.set(1);
          manPos.set(withSpring(Math.round(manPos.get()) + 1, { duration: 420, dampingRatio: 0.5 }));
          scheduleOnRN(feel, 'pull');
        } else if (lv < 0.3) {
          fired.set(0);
        }
      }
    })
    .onFinalize((e) => {
      const g = grab.get();
      if (g === 0) return;
      grab.set(0);
      touching.set(0);
      lastTouch.set(now.get());
      if (g === 1) {
        const rx = e.x - D.cx;
        const ry = e.y - D.cy;
        const omega = (rx * e.velocityY - ry * e.velocityX) / Math.max(400, rx * rx + ry * ry);
        const vel = -omega / STEP_RAD;
        const target = Math.round(manPos.get() + vel * 0.12);
        manPos.set(withSpring(target, { duration: 500, dampingRatio: 0.55, velocity: vel }));
      } else {
        manLever.set(withSpring(0, { duration: 380, dampingRatio: 0.45 }));
      }
    });

  const ROW = CAPTION_H;
  /** The caption's roll position: whole numbers on a slide, rolling over the middle 40 % of a step. */
  const roll = useDerivedValue(() => {
    const pm = wrap(view.get()[0], SLOTS);
    const base = Math.floor(pm);
    return base + smoothstep(0.3, 0.7, pm - base);
  });

  const onLayout = (e: LayoutChangeEvent) => {
    const { width, height } = e.nativeEvent.layout;
    if (!size || size.w !== width || size.h !== height) setSize({ w: width, h: height });
  };

  const captions = [...SLIDES, SLIDES[0]];
  const chipW = D.r * 0.3;
  const chipH = chipW * 0.75;
  const gateY = D.cy - D.r * 0.56;
  const winR = Skia.RRectXY(Skia.XYWHRect(Wn.x, Wn.y, Wn.w, Wn.h), 18, 18);

  return (
    <View style={[styles.root, { backgroundColor: BG }]} onLayout={onLayout}>
      <GestureDetector gesture={pan}>
        <View style={StyleSheet.absoluteFill} accessible accessibilityLabel="A stereo viewer reel of Iceland. Turn the reel or pull the lever down for the next view.">
          {L && room ? (
            <Canvas style={StyleSheet.absoluteFill}>
              <Image image={room} x={0} y={0} width={L.W} height={L.H} />
              <Group transform={viewerShake}>
                <Group clip={winR}>
                  <Rect x={Wn.x} y={Wn.y} width={Wn.w} height={Wn.h} color="#0A0A0B" />
                  <Group origin={vec(Wn.x + Wn.w / 2, Wn.y + Wn.h / 2)} transform={wobble}>
                    {SLIDES.map((s, k) => (
                      <SlideLayer key={k} k={k} idx={idx} image={photos ? photos[s.photo] : null} win={Wn} />
                    ))}
                  </Group>
                  {/* Lens: darker edges, a faint glass streak. */}
                  <Rect x={Wn.x} y={Wn.y} width={Wn.w} height={Wn.h}>
                    <RadialGradient c={vec(Wn.x + Wn.w / 2, Wn.y + Wn.h / 2)} r={Wn.w * 0.72} colors={['rgba(0,0,0,0)', 'rgba(0,0,0,0.08)', 'rgba(0,0,0,0.55)']} positions={[0, 0.62, 1]} />
                  </Rect>
                  <Rect x={Wn.x} y={Wn.y} width={Wn.w} height={Wn.h}>
                    <LinearGradient start={vec(Wn.x, Wn.y)} end={vec(Wn.x + Wn.w, Wn.y + Wn.h)} colors={['rgba(255,255,255,0)', 'rgba(255,255,255,0.10)', 'rgba(255,255,255,0)']} positions={[0.25, 0.36, 0.48]} />
                  </Rect>
                  <Rect rect={topLid} color="#0B0909" />
                  <Rect rect={botLid} color="#0B0909" />
                  <RoundedRect rect={winR} style="stroke" strokeWidth={10} color="rgba(0,0,0,0.55)">
                    <BlurMask blur={6} style="normal" />
                  </RoundedRect>
                </Group>
              </Group>

              {reel ? (
                <Group origin={vec(D.cx, D.cy)} transform={discTransform}>
                  <Image image={reel} x={D.cx - D.r} y={D.cy - D.r} width={D.r * 2} height={D.r * 2} />
                </Group>
              ) : null}
              {/* Light that does not turn with the reel. */}
              <Circle cx={D.cx} cy={D.cy} r={D.r}>
                <RadialGradient c={vec(D.cx - D.r * 0.4, D.cy - D.r * 0.5)} r={D.r * 1.6} colors={['rgba(255,252,240,0.22)', 'rgba(255,252,240,0)', 'rgba(40,20,0,0.16)']} positions={[0, 0.45, 1]} />
              </Circle>
              {/* The gate at 12 o'clock: a bracket, and the viewer's light through the chip in it. */}
              <RoundedRect x={D.cx - chipW / 2 - 7} y={gateY - chipH / 2 - 7} width={chipW + 14} height={chipH + 14} r={5} color="rgba(0,0,0,0.35)">
                <BlurMask blur={4} style="normal" />
              </RoundedRect>
              <RoundedRect x={D.cx - chipW / 2 - 6} y={gateY - chipH / 2 - 6} width={chipW + 12} height={chipH + 12} r={5} style="stroke" strokeWidth={5} color="#2B2321" />
              <RoundedRect x={D.cx - chipW / 2 - 3.5} y={gateY - chipH / 2 - 3.5} width={chipW + 7} height={chipH + 7} r={3} style="stroke" strokeWidth={0.8} color="rgba(255,220,200,0.35)" />
              <Rect x={D.cx - chipW / 2} y={gateY - chipH / 2} width={chipW} height={chipH} color="rgba(255,236,200,0.22)" blendMode="screen" />

              {/* The lever: red bakelite arm, a knob, a chrome pivot cap. */}
              <Group origin={vec(LV.px, LV.py)} transform={leverTransform}>
                <RoundedRect x={LV.px + 2} y={LV.py - 2} width={LV.len} height={14} r={7} color="rgba(0,0,0,0.45)">
                  <BlurMask blur={5} style="normal" />
                </RoundedRect>
                <RoundedRect x={LV.px} y={LV.py - 7} width={LV.len} height={14} r={7}>
                  <LinearGradient start={vec(0, LV.py - 7)} end={vec(0, LV.py + 7)} colors={['#E26A5A', '#B3362C', '#7E2019']} />
                </RoundedRect>
                <Circle cx={LV.px + LV.len} cy={LV.py} r={15}>
                  <RadialGradient c={vec(LV.px + LV.len - 5, LV.py - 6)} r={18} colors={['#F08A7A', '#C23E33', '#7A1E18']} positions={[0, 0.5, 1]} />
                </Circle>
              </Group>
              <Circle cx={LV.px} cy={LV.py} r={11}>
                <RadialGradient c={vec(LV.px - 3, LV.py - 4)} r={13} colors={['#FBF7F2', '#A8A29C', '#57524D']} positions={[0, 0.55, 1]} />
              </Circle>
            </Canvas>
          ) : null}
        </View>
      </GestureDetector>

      <View pointerEvents="none" style={[styles.header, { top: insets.top + 12 }]}>
        <Text style={styles.kicker}>REELS</Text>
        <Text style={styles.title}>Iceland, 2025</Text>
        <Text style={styles.sub}>7 views · Ring Road, June · 1,332 km</Text>
      </View>

      {L ? (
        <View pointerEvents="none" style={[styles.captionClip, { top: L.captionY, height: ROW }]}>
          {captions.map((s, k) => (
            <CaptionRow key={k} k={k} roll={roll} row={ROW} title={s.title} place={s.place} />
          ))}
        </View>
      ) : null}
    </View>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, overflow: 'hidden' },
  header: { position: 'absolute', left: 24, right: 24 },
  kicker: { color: ACCENT, fontSize: 12, fontWeight: '700', letterSpacing: 2.4 },
  title: { color: INK, fontSize: 30, fontWeight: '700', letterSpacing: -0.6, marginTop: 4 },
  sub: { color: MUTE, fontSize: 14, fontWeight: '500', marginTop: 2, fontVariant: ['tabular-nums'] },
  captionClip: { position: 'absolute', left: 24, right: 96, overflow: 'hidden' },
  captionRow: { position: 'absolute', left: 0, right: 0, top: 0, justifyContent: 'center', overflow: 'hidden' },
  counter: { color: ACCENT, fontSize: 11, lineHeight: 14, fontWeight: '700', letterSpacing: 1.6, fontVariant: ['tabular-nums'] },
  captionTitle: { color: INK, fontSize: 20, lineHeight: 25, fontWeight: '600', letterSpacing: -0.3 },
  place: { color: MUTE, fontSize: 13, lineHeight: 17, fontWeight: '500', fontVariant: ['tabular-nums'] },
});
