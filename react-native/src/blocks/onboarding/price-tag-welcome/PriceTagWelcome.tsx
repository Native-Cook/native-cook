import { Blur, Canvas, Circle, Fill, Group, ImageShader, matchFont, RadialGradient, Shader, type SkImage, Text as SkText, type Uniforms, useImage, vec } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { LinearGradient } from 'expo-linear-gradient';
import { Fragment, useCallback, useEffect, useMemo, useState } from 'react';
import { AppState, Image, type LayoutChangeEvent, StyleSheet, Text, useWindowDimensions, View } from 'react-native';
import { Gesture, GestureDetector, Pressable } from 'react-native-gesture-handler';
import Animated, { cubicBezier, type SharedValue, useAnimatedReaction, useAnimatedStyle, useFrameCallback, useSharedValue } from 'react-native-reanimated';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';

import { useCalmMotion } from './a11y';
import { stickerEffect, tagEffect } from './frost-shader';
import {
  clockNow,
  countdownAt,
  createEngine,
  digitOf,
  dragBegin,
  dragEnd,
  dragMove,
  type Engine,
  ETCH,
  type Frame,
  frostLook,
  GLINT,
  hitItem,
  isFrozen,
  LOOP,
  pivotOf,
  PRESS,
  REST_FRAME,
  setAutoPlay,
  step,
  stripLine,
  TAP_SLOP,
  tapItem,
  touchCancel,
  touchDown,
  visibleMask,
  wrapX,
} from './motion';
import { ASCENT, colors, copy, GLOW_BASE, glow, items, layout, REF_PT, REF_W, row, space, type as T, type TextStyleSpec, WAIT_EM } from './theme';

export { LOOP };

const ART = {
  headphones: require('./assets/headphones.png') as number,
  laptop: require('./assets/laptop.png') as number,
  shirt: require('./assets/shirt.png') as number,
  bag: require('./assets/bag.png') as number,
  camera: require('./assets/camera.png') as number,
};
const SHADOW = {
  headphones: require('./assets/headphones-shadow.png') as number,
  laptop: require('./assets/laptop-shadow.png') as number,
  shirt: require('./assets/shirt-shadow.png') as number,
  bag: require('./assets/bag-shadow.png') as number,
  camera: require('./assets/camera-shadow.png') as number,
};
const CARD = require('./assets/card-icon.png') as number;

/** Sticker shadow: the outline's alpha, blurred, drawn this far below and this dark. */
const STICKER_SHADOW = { dy: 4, opacity: 0.14, pressDy: 2 } as const;
const EASE_OUT = cubicBezier(0.23, 1, 0.32, 1);

type Props = {
  /**
   * Plays the scripted freeze and thaw of the laptop's price every LOOP (8) seconds. A touch takes
   * over; 2 s after the last one the row eases home (thawing) and the loop restarts. Stopped under
   * Reduce Motion or a screen reader.
   */
  autoPlay?: boolean;
};

function lightHaptic() {
  if (process.env.EXPO_OS !== 'web') Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
}
/** Freezing lands hard and crisp; a thaw is soft. */
function freezeHaptic(froze: boolean) {
  if (process.env.EXPO_OS !== 'web') Haptics.impactAsync(froze ? Haptics.ImpactFeedbackStyle.Rigid : Haptics.ImpactFeedbackStyle.Soft).catch(() => {});
}

const rgb = (c: readonly number[], a = 1) => `rgba(${c[0]},${c[1]},${c[2]},${a})`;

export function PriceTagWelcome({ autoPlay = false }: Props) {
  const { width: W, height: H } = useWindowDimensions();
  /** ref px → pt */
  const u = W / REF_W;
  /** font pt at 402 → pt here */
  const k = W / REF_PT;
  const { calm: reduced } = useCalmMotion();
  const [busy, setBusy] = useState(true);
  const [foreground, setForeground] = useState(AppState.currentState === 'active');

  const engine = useSharedValue<Engine>(createEngine(0, autoPlay, reduced));
  const frame = useSharedValue<Frame>(REST_FRAME);
  const still = useSharedValue(reduced);
  /** Bit i: item i has frost on it (for its accessibility label). */
  const [frosted, setFrosted] = useState(0);
  const art = [useImage(ART.headphones), useImage(ART.laptop), useImage(ART.shirt), useImage(ART.bag), useImage(ART.camera)];

  useAnimatedReaction(
    () => {
      const ft = frame.get().ft;
      let m = 0;
      for (let i = 0; i < ft.length; i++) if (ft[i] >= 0) m |= 1 << i;
      return m;
    },
    (m, prev) => {
      if (m !== prev) scheduleOnRN(setFrosted, m);
    },
  );

  /** Bit i: item i is on screen, so its frost canvases are mounted (see MOUNT_LEAD). */
  const [onScreen, setOnScreen] = useState(() => visibleMask(0));
  useAnimatedReaction(
    () => visibleMask(frame.get().x),
    (m, prev) => {
      if (prev !== null && m !== prev) scheduleOnRN(setOnScreen, m);
    },
  );

  const sample = useCallback(
    (now: number) => {
      'worklet';
      const r = step(engine.get(), now);
      engine.set(r.e);
      frame.set(r.f);
      return r.moving;
    },
    [engine, frame],
  );

  const onFrame = useCallback(() => {
    'worklet';
    if (!sample(clockNow())) scheduleOnRN(setBusy, false);
  }, [sample]);
  const callback = useFrameCallback(onFrame, false);
  useEffect(() => {
    callback.setActive(foreground && busy);
  }, [callback, foreground, busy]);
  const wake = useCallback(() => setBusy(true), []);

  useEffect(() => {
    scheduleOnUI(
      (on: boolean, quiet: boolean) => {
        'worklet';
        const now = clockNow();
        still.set(quiet);
        engine.set(setAutoPlay(engine.get(), on, quiet, now));
        sample(now);
        scheduleOnRN(wake);
      },
      autoPlay,
      reduced,
    );
  }, [autoPlay, reduced, engine, still, sample, wake]);

  // Background: the loop restarts from its first frame on return.
  useEffect(() => {
    const sub = AppState.addEventListener('change', (state) => {
      const active = state === 'active';
      setForeground(active);
      if (active) {
        scheduleOnUI(() => {
          'worklet';
          const now = clockNow();
          const e = engine.get();
          engine.set({ ...e, autoStart: now, last: now, dragging: false });
          sample(now);
        });
        setBusy(true);
      }
    });
    return () => sub.remove();
  }, [engine, sample]);

  // ─── gestures: drag the conveyor, tap a sticker to turn its tag ───
  const rowTop = row.top * u;
  const gesture = useMemo(() => {
    const tap = Gesture.Tap()
      .maxDistance(TAP_SLOP * u)
      .onBegin((ev) => {
        const now = clockNow();
        const hit = hitItem(ev.x / u, ev.y / u + row.top, engine.get().x);
        engine.set(touchDown(engine.get(), now, hit));
        sample(now);
        scheduleOnRN(wake);
      })
      .onEnd((ev, success) => {
        if (!success) return;
        const now = clockNow();
        const hit = hitItem(ev.x / u, ev.y / u + row.top, engine.get().x);
        if (hit >= 0) {
          const e = tapItem(engine.get(), now, hit);
          engine.set(e);
          scheduleOnRN(freezeHaptic, isFrozen(e, hit));
        } else engine.set(touchCancel(engine.get(), now));
        sample(now);
      })
      .onFinalize((_ev, success) => {
        if (!success && !engine.get().dragging) engine.set(touchCancel(engine.get(), clockNow()));
      });
    const pan = Gesture.Pan()
      .activeOffsetX([-8, 8])
      .failOffsetY([-16, 16])
      .onStart(() => {
        const now = clockNow();
        engine.set(dragBegin(engine.get(), now));
        sample(now);
        scheduleOnRN(wake);
      })
      .onUpdate((ev) => {
        engine.set(dragMove(engine.get(), clockNow(), ev.translationX / u));
      })
      .onEnd((ev) => {
        engine.set(dragEnd(engine.get(), clockNow(), ev.velocityX / u));
      })
      .onFinalize(() => {
        const e = engine.get();
        if (e.dragging) engine.set(dragEnd(e, clockNow(), 0));
      });
    return Gesture.Race(pan, tap);
  }, [engine, sample, u, wake]);

  const flipByA11y = useCallback(
    (i: number) => {
      setBusy(true);
      scheduleOnUI(() => {
        'worklet';
        const now = clockNow();
        const e = tapItem(engine.get(), now, i);
        engine.set(e);
        sample(now);
        scheduleOnRN(freezeHaptic, isFrozen(e, i));
      });
    },
    [engine, sample],
  );

  // ─── layout ───
  const font = (s: TextStyleSpec) => ({ fontSize: s.size * k, fontWeight: s.weight, color: s.color, letterSpacing: s.ls * k }) as const;
  const textTop = (base: number, s: TextStyleSpec) => base * u - ASCENT * s.size * k;
  const ic = layout.icon;
  const btn = layout.button;
  const btnTop = H - btn.fromBottom * u;

  return (
    <View style={styles.screen}>
      <Backdrop u={u} />

      {/* The card icon (Blender render) on its shadow; a glint crosses it while the row runs. */}
      <View pointerEvents="none" style={[styles.abs, styles.iconShadow, { left: ic.box.x * u, top: ic.box.y * u, width: ic.box.w * u, height: ic.box.h * u, borderRadius: 43 * u }]} />
      <Image source={CARD} fadeDuration={0} style={[styles.abs, { left: ic.x * u, top: ic.y * u, width: ic.w * u, height: ic.h * u }]} accessibilityIgnoresInvertColors />
      <Glint frame={frame} still={still} u={u} />

      <Text allowFontScaling={false} accessibilityRole="header" style={[styles.center, font(T.headline), { top: textTop(layout.headline[0].base, T.headline) }]}>
        {copy.headline[0]}
      </Text>
      <Text allowFontScaling={false} style={[styles.center, font(T.headline), { top: textTop(layout.headline[1].base, T.headline) }]}>
        {copy.headline[1]}
      </Text>
      <Text allowFontScaling={false} style={[styles.center, font(T.sub), { top: textTop(layout.sub[0].base, T.sub) }]}>
        {copy.sub[0]}
      </Text>
      <Text allowFontScaling={false} style={[styles.center, font(T.sub), { top: textTop(layout.sub[1].base, T.sub) }]}>
        {copy.sub[1]}
      </Text>

      {/* The conveyor: stickers, then every tag above every sticker. */}
      <GestureDetector gesture={gesture}>
        <View style={[styles.abs, { left: 0, width: W, top: rowTop, height: (row.bottom - row.top) * u }]}>
          {items.map((it, i) => (
            <Sticker key={it.key} i={i} frame={frame} still={still} u={u} k={k} image={art[i]} mounted={(onScreen & (1 << i)) !== 0} frosted={(frosted & (1 << i)) !== 0} onToggle={flipByA11y} />
          ))}
          {/* Own flat layer: iOS depth-sorts sibling layers with 3D transforms, so a turning tag
              would cut into the sticker beside it. Inside this container they all sit above them. */}
          <View pointerEvents="none" accessibilityElementsHidden importantForAccessibility="no-hide-descendants" style={StyleSheet.absoluteFill}>
            {items.map((it, i) => (
              <Tag key={it.key} i={i} frame={frame} still={still} u={u} k={k} mounted={(onScreen & (1 << i)) !== 0} />
            ))}
          </View>
        </View>
      </GestureDetector>

      <GetStarted left={btn.x * u} top={btnTop} width={btn.w * u} height={btn.h * u} base={btn.base * u} font={font(T.button)} size={T.button.size * k} />
    </View>
  );
}

/** Page colour and the three pastel blobs (static). */
function Backdrop({ u }: { u: number }) {
  const stops = [0, 0.5, 1, 1.5, 2, 2.5, 3];
  return (
    <Canvas style={StyleSheet.absoluteFill} pointerEvents="none">
      <Fill color={rgb(GLOW_BASE)} />
      {glow.map((b, i) => {
        const r = 3 * b.sigma * u;
        return (
          <Circle key={i} cx={b.cx * u} cy={b.cy * u} r={r}>
            <RadialGradient c={vec(b.cx * u, b.cy * u)} r={r} colors={stops.map((s) => rgb(b.peak, Math.exp((-s * s) / 2)))} positions={stops.map((s) => s / 3)} />
          </Circle>
        );
      })}
    </Canvas>
  );
}

/** A light band across the card icon; its place follows the row's offset, its strength the row's speed. */
function Glint({ frame, still, u }: { frame: SharedValue<Frame>; still: SharedValue<boolean>; u: number }) {
  const b = layout.icon.box;
  const bandW = b.w * 0.5 * u;
  const style = useAnimatedStyle(() => {
    const f = frame.get();
    const travel = (b.w * 1.9) * u;
    return {
      opacity: still.get() ? 0 : GLINT.alpha * f.glintA,
      transform: [{ translateX: -bandW - b.w * 0.45 * u + f.glint * travel }, { rotate: '20deg' }],
    };
  });
  return (
    <View pointerEvents="none" style={[styles.abs, { left: b.x * u, top: b.y * u, width: b.w * u, height: b.h * u, borderRadius: 43 * u, overflow: 'hidden' }]}>
      <Animated.View style={[styles.abs, { left: 0, top: -b.h * 0.3 * u, width: bandW, height: b.h * 1.6 * u }, style]}>
        <LinearGradient colors={['rgba(255,255,255,0)', 'rgba(255,255,255,1)', 'rgba(255,255,255,0)']} start={{ x: 0, y: 0.5 }} end={{ x: 1, y: 0.5 }} style={StyleSheet.absoluteFill} />
      </Animated.View>
    </View>
  );
}

/** One product sticker: shadow + render with its die-cut outline, and its frost. Moves with the conveyor. */
function Sticker({
  i,
  frame,
  still,
  u,
  k,
  image,
  mounted,
  frosted,
  onToggle,
}: {
  i: number;
  frame: SharedValue<Frame>;
  still: SharedValue<boolean>;
  u: number;
  k: number;
  image: SkImage | null;
  /** On screen: only then is its frost canvas mounted. */
  mounted: boolean;
  frosted: boolean;
  onToggle: (i: number) => void;
}) {
  const it = items[i];
  const move = useAnimatedStyle(() => ({ transform: [{ translateX: (wrapX(it.x, frame.get().x) - it.x) * u }] }));
  const lift = useAnimatedStyle(() => {
    const p = frame.get().press[i];
    return { transform: [{ scale: 1 - (1 - PRESS.scale) * p }] };
  });
  const shade = useAnimatedStyle(() => {
    const p = frame.get().press[i];
    return { transform: [{ translateY: (STICKER_SHADOW.dy - (STICKER_SHADOW.dy - STICKER_SHADOW.pressDy) * p) * u }, { scale: 1 - (1 - PRESS.scale) * p }] };
  });
  return (
    <Animated.View
      accessible
      accessibilityRole="button"
      accessibilityLabel={`${it.name}, ${it.price}${frosted ? `, ${copy.a11yFrozen}` : ''}`}
      accessibilityHint={copy.a11yItemHint}
      accessibilityActions={[{ name: 'activate' }]}
      onAccessibilityAction={() => onToggle(i)}
      style={[styles.abs, { left: it.x * u, top: (it.y - row.top) * u, width: it.w * u, height: it.h * u }, move]}
    >
      <Animated.Image source={SHADOW[it.key]} fadeDuration={0} style={[styles.abs, { left: 0, top: 0, width: it.w * u, height: it.h * u, opacity: STICKER_SHADOW.opacity }, shade]} />
      <Animated.View style={[styles.abs, { left: 0, top: 0, width: it.w * u, height: it.h * u }, lift]}>
        <Image source={ART[it.key]} fadeDuration={0} style={[styles.abs, { left: 0, top: 0, width: it.w * u, height: it.h * u }]} />
        {image && mounted ? <StickerFrost i={i} frame={frame} still={still} u={u} k={k} image={image} /> : null}
        <Countdown i={i} frame={frame} still={still} u={u} k={k} />
      </Animated.View>
    </Animated.View>
  );
}

/** Frost origin (the tag's centre, in the sticker's frost canvas, pt) and the radius that covers it all. */
function frostGeometry(i: number, u: number, k: number) {
  const it = items[i];
  const m = space.frostMargin * u;
  const cw = it.w * u + 2 * m;
  const ch = it.h * u + 2 * m;
  const ox = (it.tag.cx - it.x) * u + m;
  const oy = (it.tag.cy - it.y) * u + m;
  const span = Math.hypot(Math.max(ox, cw - ox), Math.max(oy, ch - oy)) + 12 * k;
  return { m, cw, ch, ox, oy, span };
}

/** The ice over a sticker (runtime shader over the same render, with a rime halo around it). */
function StickerFrost({ i, frame, still, u, k, image }: { i: number; frame: SharedValue<Frame>; still: SharedValue<boolean>; u: number; k: number; image: SkImage }) {
  const it = items[i];
  const g = frostGeometry(i, u, k);
  const bw = it.w * u;
  const bh = it.h * u;
  const uniforms = useSharedValue<Uniforms>({ org: [g.ox, g.oy], grow: 0, span: g.span, melt: 0, clock: 0, k, fade: 0, seed: 1.7 + 3.1 * i, box: [bw, bh], margin: g.m });
  // Mounted from the start (a canvas mounted on the tap paints frames late and hides the growth),
  // but its uniforms change only while this item has frost, so an idle canvas never redraws.
  useAnimatedReaction(
    () => {
      const f = frame.get();
      return { ft: f.ft[i], mt: f.mt[i], clock: f.clock, quiet: still.get() };
    },
    (c, prev) => {
      if (c.ft < 0 && prev !== null && prev.ft < 0) return;
      const look = frostLook(c.ft, c.mt, c.quiet);
      uniforms.set({ org: [g.ox, g.oy], grow: look.grow * g.span, span: g.span, melt: look.melt, clock: c.clock, k, fade: look.fade, seed: 1.7 + 3.1 * i, box: [bw, bh], margin: g.m });
    },
  );
  return (
    <Canvas pointerEvents="none" style={[styles.abs, { left: -g.m, top: -g.m, width: g.cw, height: g.ch }]}>
      <Fill>
        <Shader source={stickerEffect} uniforms={uniforms}>
          <ImageShader image={image} fit="fill" rect={{ x: g.m, y: g.m, width: it.w * u, height: it.h * u }} />
        </Shader>
      </Fill>
    </Canvas>
  );
}

/**
 * Reveals a layer left to right as it is etched (a finger writing in the frost), with transforms
 * only: the clip slides in from the left while the content slides the other way, so the text stays
 * put. It melts away (fades and sinks) on a thaw. `timer` picks the countdown's window, else "Wait 24h".
 * The width is measured once (onLayout) into a shared value. (No function is passed into the worklets:
 * a captured callback is not callable on the UI thread under the React Compiler.)
 */
function useEtch(i: number, timer: boolean, frame: SharedValue<Frame>, still: SharedValue<boolean>, drop: number) {
  const width = useSharedValue(0);
  const outer = useAnimatedStyle(() => {
    const f = frame.get();
    const quiet = still.get();
    const look = frostLook(f.ft[i], f.mt[i], quiet);
    const p = quiet ? 1 : timer ? look.timer : look.wait;
    const a = quiet ? (timer ? look.timer : look.wait) : 1 - look.out;
    return { opacity: width.get() > 0 ? a : 0, transform: [{ translateY: look.out * drop }, { translateX: -width.get() * (1 - p) }] };
  });
  const inner = useAnimatedStyle(() => {
    const f = frame.get();
    const quiet = still.get();
    const look = frostLook(f.ft[i], f.mt[i], quiet);
    const p = quiet ? 1 : timer ? look.timer : look.wait;
    return { transform: [{ translateX: width.get() * (1 - p) }] };
  });
  const onLayout = useCallback((ev: LayoutChangeEvent) => width.set(ev.nativeEvent.layout.width), [width]);
  return { outer, inner, onLayout };
}

/** The countdown etched over a frozen product: 24:00:00, then a tick every second; digits roll. */
function Countdown({ i, frame, still, u, k }: { i: number; frame: SharedValue<Frame>; still: SharedValue<boolean>; u: number; k: number }) {
  const it = items[i];
  const size = T.timer.size * k;
  const lineH = Math.round(size * 1.18);
  const at = it.key === 'laptop' ? layout.timer.laptop : layout.timer.other;
  const etch = useEtch(i, true, frame, still, space.etchDrop * u);
  const cols = [0, 1, 2, 3, 4, 5];
  return (
    <Animated.View pointerEvents="none" style={[styles.abs, { left: it.w * u * (at.fx - 0.5), width: it.w * u, top: it.h * u * at.fy - lineH / 2, height: lineH, alignItems: 'center' }]}>
      <Animated.View onLayout={etch.onLayout} style={[{ overflow: 'hidden' }, etch.outer]}>
        <Animated.View style={[styles.timerRow, etch.inner]}>
          {cols.map((c) => (
            <Fragment key={c}>
              {c === 2 || c === 4 ? (
                <Text allowFontScaling={false} style={[styles.scraped, { fontSize: size, lineHeight: lineH, fontWeight: T.timer.weight, letterSpacing: T.timer.ls * k }]}>
                  :
                </Text>
              ) : null}
              <DigitColumn i={i} col={c} frame={frame} still={still} size={size} lineH={lineH} k={k} />
            </Fragment>
          ))}
        </Animated.View>
      </Animated.View>
    </Animated.View>
  );
}

const STRIP = '0\n1\n2\n3\n4\n5\n6\n7\n8\n9\n0';

/** One rolling digit: the strip "0 … 9 0" behind a one-line window. */
function DigitColumn({ i, col, frame, still, size, lineH, k }: { i: number; col: number; frame: SharedValue<Frame>; still: SharedValue<boolean>; size: number; lineH: number; k: number }) {
  const roll = useAnimatedStyle(() => {
    const f = frame.get();
    const c = countdownAt(Math.max(f.ft[i], 0), still.get());
    const line = stripLine(digitOf(c.prev, col), digitOf(c.left, col), c.roll);
    return { transform: [{ translateY: -line * lineH }] };
  });
  const style = [styles.scraped, { fontSize: size, lineHeight: lineH, fontWeight: T.timer.weight, letterSpacing: T.timer.ls * k, fontVariant: ['tabular-nums' as const] }];
  // A hidden "0" sizes the window (tabular figures are all this wide); the strip hangs in it, absolute.
  return (
    <View style={{ height: lineH, overflow: 'hidden' }}>
      <Text allowFontScaling={false} style={[style, styles.hidden]}>
        0
      </Text>
      <Animated.Text allowFontScaling={false} style={[style, styles.strip, roll]}>
        {STRIP}
      </Animated.Text>
    </View>
  );
}

/** A price tag: hangs from a pin near its top edge (the pendulum); frozen, it reads "Wait 24h". */
function Tag({ i, frame, still, u, k, mounted }: { i: number; frame: SharedValue<Frame>; still: SharedValue<boolean>; u: number; k: number; mounted: boolean }) {
  const it = items[i];
  const t = it.tag;
  const pv = pivotOf(i);
  const w = t.w * u;
  const h = t.h * u;
  const hang = useAnimatedStyle(() => {
    const f = frame.get();
    return {
      transform: [
        { translateX: (wrapX(it.x, f.x) - it.x) * u },
        { rotate: `${t.tilt}deg` },
        { translateX: pv.x * u },
        { translateY: pv.y * u },
        { rotate: `${f.th[i]}deg` },
        { translateX: -pv.x * u },
        { translateY: -pv.y * u },
      ],
    };
  });
  const price = useAnimatedStyle(() => {
    const f = frame.get();
    // The crisp price hands over to its blurred copy (PriceBlur) as the ice takes it.
    return { opacity: 1 - frostLook(f.ft[i], f.mt[i], still.get()).lost };
  });
  return (
    <Animated.View pointerEvents="none" style={[styles.abs, { left: t.cx * u - w / 2, top: (t.cy - row.top) * u - h / 2, width: w, height: h }, hang]}>
      <View style={[styles.tag, { width: w, height: h, borderRadius: h / 2 }]}>
        <Animated.Text allowFontScaling={false} style={[styles.price, { fontSize: T.price.size * k, fontWeight: T.price.weight, color: T.price.color }, price]}>
          {it.price}
        </Animated.Text>
      </View>
      {mounted ? (
        <>
          <PriceBlur i={i} frame={frame} still={still} w={w} h={h} k={k} />
          <TagFrost i={i} frame={frame} still={still} u={u} k={k} />
        </>
      ) : null}
      <WaitLabel i={i} frame={frame} still={still} u={u} k={k} />
    </Animated.View>
  );
}

/**
 * The frozen price: the same figures drawn by Skia with a real Gaussian blur (0 … ETCH.priceBlur),
 * fading to the ghost (ETCH.priceGhost). The blur is bounded by the tag's canvas (decal edges). It
 * draws nothing until the freeze starts, so the crisp RN price is what the rest frame shows; its
 * values are written only when they change, so an idle tag never redraws.
 */
function PriceBlur({ i, frame, still, w, h, k }: { i: number; frame: SharedValue<Frame>; still: SharedValue<boolean>; w: number; h: number; k: number }) {
  const price = items[i].price;
  const size = T.price.size * k;
  const font = useMemo(() => matchFont({ fontFamily: 'System', fontSize: size, fontStyle: 'normal', fontWeight: T.price.weight }), [size]);
  const at = useMemo(() => {
    const tw = font.getGlyphWidths(font.getGlyphIDs(price)).reduce((a, b) => a + b, 0);
    const m = font.getMetrics();
    return { x: (w - tw) / 2, y: h / 2 - (m.ascent + m.descent) / 2 };
  }, [font, price, w, h]);
  const blur = useSharedValue(0);
  const opacity = useSharedValue(0);
  const maxBlur = ETCH.priceBlur * k;
  useAnimatedReaction(
    () => {
      const f = frame.get();
      return frostLook(f.ft[i], f.mt[i], still.get()).lost;
    },
    (lost, prev) => {
      if (lost === prev) return;
      // priceA = lerp(1, ghost, lost) in both modes (frostLook).
      opacity.set(lost > 0 ? 1 + (ETCH.priceGhost - 1) * lost : 0);
      blur.set(lost * maxBlur);
    },
  );
  return (
    <Canvas pointerEvents="none" style={[styles.abs, { left: 0, top: 0, width: w, height: h }]}>
      <Group opacity={opacity}>
        <SkText x={at.x} y={at.y} text={price} font={font} color={T.price.color}>
          <Blur blur={blur} mode="decal" />
        </SkText>
      </Group>
    </Canvas>
  );
}

/** The ice over a tag: the same front, from the tag's centre, in the tag's own frame. */
function TagFrost({ i, frame, still, u, k }: { i: number; frame: SharedValue<Frame>; still: SharedValue<boolean>; u: number; k: number }) {
  const t = items[i].tag;
  const w = t.w * u;
  const h = t.h * u;
  const g = frostGeometry(i, u, k);
  const uniforms = useSharedValue<Uniforms>({ org: [w / 2, h / 2], grow: 0, span: g.span, melt: 0, clock: 0, k, fade: 0, seed: 5.3 + 2.3 * i, size: [w, h] });
  useAnimatedReaction(
    () => {
      const f = frame.get();
      return { ft: f.ft[i], mt: f.mt[i], clock: f.clock, quiet: still.get() };
    },
    (c, prev) => {
      if (c.ft < 0 && prev !== null && prev.ft < 0) return;
      const look = frostLook(c.ft, c.mt, c.quiet);
      uniforms.set({ org: [w / 2, h / 2], grow: look.grow * g.span, span: g.span, melt: look.melt, clock: c.clock, k, fade: look.fade, seed: 5.3 + 2.3 * i, size: [w, h] });
    },
  );
  return (
    <Canvas pointerEvents="none" style={[styles.abs, { left: 0, top: 0, width: w, height: h }]}>
      <Fill>
        <Shader source={tagEffect} uniforms={uniforms} />
      </Fill>
    </Canvas>
  );
}

/** "Wait 24h", etched into the frosted tag (shrinks to fit a narrow tag). */
function WaitLabel({ i, frame, still, u, k }: { i: number; frame: SharedValue<Frame>; still: SharedValue<boolean>; u: number; k: number }) {
  const t = items[i].tag;
  const size = Math.min(T.wait.size * k, (t.w * u - 2 * space.tagPad * u) / WAIT_EM);
  const etch = useEtch(i, false, frame, still, space.etchDrop * u);
  return (
    <View style={[StyleSheet.absoluteFill, styles.centerBox]}>
      <Animated.View onLayout={etch.onLayout} style={[{ overflow: 'hidden' }, etch.outer]}>
        <Animated.Text allowFontScaling={false} style={[styles.etched, { fontSize: size, fontWeight: T.wait.weight }, etch.inner]}>
          {copy.wait}
        </Animated.Text>
      </Animated.View>
    </View>
  );
}

/** The call to action: a quiet press (0.97, 100 ms in, 200 ms out). */
function GetStarted({ left, top, width, height, base, font, size }: { left: number; top: number; width: number; height: number; base: number; font: object; size: number }) {
  const [pressed, setPressed] = useState(false);
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={copy.button}
      onPressIn={() => setPressed(true)}
      onPressOut={() => setPressed(false)}
      onPress={lightHaptic}
      style={[styles.abs, { left, top, width, height }]}
    >
      <Animated.View
        style={[
          styles.button,
          {
            width,
            height,
            borderRadius: height / 2,
            transform: [{ scale: pressed ? PRESS.scale : 1 }],
            transitionProperty: 'transform',
            transitionDuration: (pressed ? PRESS.in : PRESS.out) * 1000,
            transitionTimingFunction: EASE_OUT,
          },
        ]}
      >
        <Text allowFontScaling={false} style={[styles.center, font, { top: base - ASCENT * size }]}>
          {copy.button}
        </Text>
      </Animated.View>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: colors.page, overflow: 'hidden' },
  abs: { position: 'absolute' },
  center: { position: 'absolute', left: 0, right: 0, textAlign: 'center' },
  iconShadow: {
    backgroundColor: '#B1E589',
    boxShadow: [
      { offsetX: 0, offsetY: 2, blurRadius: 4, color: 'rgba(0,0,0,0.06)' },
      { offsetX: 0, offsetY: 6, blurRadius: 14, color: 'rgba(0,0,0,0.07)' },
    ],
  },
  centerBox: { alignItems: 'center', justifyContent: 'center' },
  timerRow: { flexDirection: 'row', alignItems: 'flex-start' },
  hidden: { opacity: 0 },
  scraped: {
    color: colors.scrape,
    textShadowColor: colors.scrapeEdge,
    textShadowOffset: { width: 0, height: 1 },
    textShadowRadius: 0,
  },
  strip: { position: 'absolute', left: 0, right: 0, top: 0 },
  etched: {
    color: colors.etch,
    textShadowColor: colors.etchLight,
    textShadowOffset: { width: 0, height: 0.8 },
    textShadowRadius: 0,
  },
  tag: {
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: colors.tag,
    boxShadow: [
      { offsetX: 0, offsetY: 1.5, blurRadius: 3, color: 'rgba(0,0,0,0.10)' },
      { offsetX: 0, offsetY: 4, blurRadius: 10, color: 'rgba(0,0,0,0.07)' },
    ],
  },
  price: { textAlign: 'center' },
  button: {
    backgroundColor: colors.button,
    borderWidth: 1,
    borderColor: colors.buttonRim,
    boxShadow: [{ offsetX: 0, offsetY: 8, blurRadius: 26, color: 'rgba(0,0,0,0.10)' }],
  },
});

