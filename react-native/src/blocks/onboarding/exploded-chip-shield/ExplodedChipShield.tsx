import { Canvas, FillType, Path, Picture, Skia } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useEffect, useMemo, useState } from 'react';
import { type LayoutChangeEvent, Pressable, StyleSheet, Text, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, { type SharedValue, useAnimatedStyle, useDerivedValue, useFrameCallback, useReducedMotion, useSharedValue } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';

import { calloutPoint, calloutX, drawBackground, drawCallouts, drawStack, type Geo, keyholePath, makeStatics, shieldPath } from './ecsArt';
import {
  begin,
  BG_BOTTOM,
  BODY,
  BRAND,
  CALLOUTS,
  CAPTION,
  CTA,
  DOT_OFF,
  drag,
  type Driver,
  driverFrame,
  easeOut,
  emptyFrame,
  end,
  EYEBROW,
  type Frame,
  GREY,
  INK,
  makeDriver,
  manual,
  seg,
  shownAmount,
  SKIP,
  TEAL,
  TEXT_BLOCK,
  TITLE,
  toggleReduced,
} from './ecsModel';

/*
 Exploded Chip Shield — the security page of the onboarding of Ferrin, an invented password vault
 (a port of ExplodedChipShield.swift).

 Look: a white, light-grey premium screen. An isometric brushed-metal chip with an embossed shield
 and keyhole sits above "Every layer, encrypted.", page dots and a Continue button.
 Hero: the chip explodes into six floating layers (ecsArt.ts): base plate, mirror-rim tray, glowing
 teal core with a raised disc, frosted glass ring, clear glass sheet printed with a circuit, and the
 lid. They separate with a stagger, hover with a slow bob while the view sways a few degrees (the
 highlights travel), and three callouts draw out of their layers. Then the stack collapses, the lid
 lands last with a small settle bump.

 autoPlay: LOOP = 7 s (timeline in ecsModel.ts); frame 7 s equals frame 0.
 Touch: drag up on the chip to open it (the layers follow the finger), sideways to tilt the view;
 release springs to the nearest state, a tap toggles. 2 s after the last touch it springs closed and
 the loop starts again.
 Reduced motion: no loop; the open stack with its callouts shows still, a tap crossfades it closed.
*/

type Props = { autoPlay?: boolean };

function css(v: number) {
  return `#${v.toString(16).padStart(6, '0')}`;
}

/** One callout label, placed from the frame on the UI thread. */
function Label({ k, frame, shown, geo, widest, onWidth }: { k: number; frame: SharedValue<Frame>; shown: SharedValue<number>; geo: Geo; widest: number; onWidth: (k: number, w: number) => void }) {
  const style = useAnimatedStyle(() => {
    const f = frame.get();
    const v = f.callout[k];
    const p = calloutPoint(geo, f, k);
    const lx = calloutX(geo, widest);
    return {
      opacity: easeOut(seg(v, 0.35, 1)) * shown.get(),
      transform: [{ translateX: lx + 10 * (1 - v) }, { translateY: p.y - 2 - 17 }],
    };
  });
  return (
    <Animated.View style={[styles.label, style]} pointerEvents="none" onLayout={(e) => onWidth(k, e.nativeEvent.layout.width)}>
      <Text style={styles.labelTitle}>{CALLOUTS[k].title}</Text>
      <Text style={styles.labelCaption}>{CALLOUTS[k].caption}</Text>
    </Animated.View>
  );
}

export function ExplodedChipShield({ autoPlay = false }: Props) {
  const insets = useSafeAreaInsets();
  const reduced = useReducedMotion();
  const auto = autoPlay && !reduced;
  const [size, setSize] = useState<{ width: number; height: number } | null>(null);
  const w = size?.width ?? 393;
  const h = size?.height ?? 852;
  const geo = useMemo<Geo>(() => ({ w, h, top: insets.top, textTop: h - insets.bottom - TEXT_BLOCK }), [w, h, insets.top, insets.bottom]);
  const statics = useMemo(() => makeStatics(), []);
  const mark = useMemo(() => {
    const p = Skia.Path.Make();
    p.addPath(shieldPath(18, 21));
    p.addPath(keyholePath(18, 21));
    p.setFillType(FillType.EvenOdd);
    p.offset(9, 10.5);
    return p;
  }, []);
  const [widths, setWidths] = useState([0, 0, 0]);
  const widest = Math.max(80, widths[0], widths[1], widths[2]);
  const onWidth = (k: number, width: number) => {
    setWidths((old) => {
      if (Math.abs(old[k] - width) < 0.5) return old;
      const next = old.slice();
      next[k] = width;
      return next;
    });
  };

  const driver = useSharedValue<Driver>(makeDriver());
  const frame = useSharedValue<Frame>(emptyFrame(0));
  const shown = useSharedValue(1);
  const now = useSharedValue(0);
  const grab = useSharedValue(false);

  useEffect(() => {
    driver.set(makeDriver());
  }, [auto, reduced, driver]);

  const feel = () => {
    if (process.env.EXPO_OS !== 'web') Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
  };

  useFrameCallback((info) => {
    const t = info.timestamp / 1000;
    now.set(t);
    const d = driver.get();
    frame.set(driverFrame(d, t, auto, reduced));
    shown.set(reduced ? shownAmount(d, t) : 1);
  });

  const touch = Gesture.Pan()
    .minDistance(0)
    .onBegin((e) => {
      if (e.y <= geo.top + 50 || e.y >= geo.textTop) {
        grab.set(false);
        return;
      }
      grab.set(true);
      if (!reduced) begin(driver.get(), now.get(), auto);
    })
    .onUpdate((e) => {
      if (!grab.get() || reduced) return;
      if (Math.hypot(e.translationX, e.translationY) > 6) drag(driver.get(), e.translationX, e.translationY, now.get());
    })
    .onFinalize((e) => {
      if (!grab.get()) return;
      grab.set(false);
      const d = driver.get();
      const t = now.get();
      const moved = Math.hypot(e.translationX, e.translationY);
      if (reduced) {
        if (moved < 10) {
          toggleReduced(d, t);
          scheduleOnRN(feel);
        }
        return;
      }
      if (end(d, e.velocityY, moved < 8, t)) scheduleOnRN(feel);
    });

  const picture = useDerivedValue(() => {
    const recorder = Skia.PictureRecorder();
    const c = recorder.beginRecording(Skia.XYWHRect(0, 0, w, h));
    const f = frame.get();
    if (reduced) {
      const s = shown.get();
      const closed = manual(0, 0, 0);
      drawBackground(c, geo, closed);
      const a = Skia.Paint();
      a.setAlphaf(1 - s);
      c.saveLayer(a);
      drawStack(c, geo, closed, statics);
      c.restore();
      const b = Skia.Paint();
      b.setAlphaf(s);
      c.saveLayer(b);
      drawBackground(c, geo, f);
      drawStack(c, geo, f, statics);
      drawCallouts(c, geo, f, widest);
      c.restore();
    } else {
      drawBackground(c, geo, f);
      drawStack(c, geo, f, statics);
      drawCallouts(c, geo, f, widest);
    }
    return recorder.finishRecordingAsPicture();
  });

  const onLayout = (e: LayoutChangeEvent) => {
    const { width, height } = e.nativeEvent.layout;
    if (!size || size.width !== width || size.height !== height) setSize({ width, height });
  };

  const toggle = () => {
    scheduleOnUI(() => {
      const d = driver.get();
      const t = now.get();
      if (reduced) {
        toggleReduced(d, t);
      } else {
        begin(d, t, auto);
        end(d, 0, true, t);
      }
    });
  };

  return (
    <View style={styles.screen} onLayout={onLayout}>
      <StatusBar style="dark" />
      <GestureDetector gesture={touch}>
        <View
          style={StyleSheet.absoluteFill}
          accessible
          accessibilityRole="button"
          accessibilityLabel="Ferrin security chip: alloy shell, cipher mesh and key enclave."
          accessibilityHint="Opens or closes the layers."
          onAccessibilityTap={toggle}
        >
          {size ? (
            <Canvas style={StyleSheet.absoluteFill}>
              <Picture picture={picture} />
            </Canvas>
          ) : null}
          {size
            ? [0, 1, 2].map((k) => <Label key={k} k={k} frame={frame} shown={shown} geo={geo} widest={widest} onWidth={onWidth} />)
            : null}
        </View>
      </GestureDetector>

      <View style={[styles.header, { top: insets.top + 10 }]} pointerEvents="none">
        <Canvas style={styles.mark}>
          <Path path={mark} color={css(INK)} />
        </Canvas>
        <Text style={styles.brand}>{BRAND}</Text>
        <View style={styles.flex} />
        <Text style={styles.skip}>{SKIP}</Text>
      </View>

      <View style={[styles.copy, { top: geo.textTop }]} pointerEvents="none">
        <Text style={styles.eyebrow}>{EYEBROW}</Text>
        <Text style={styles.title}>{TITLE}</Text>
        <Text style={styles.body}>{BODY}</Text>
      </View>

      <View style={[styles.footer, { top: h - insets.bottom - 16 - 54 - 34 }]}>
        <View style={styles.dots}>
          <View style={[styles.dot, styles.dotOff]} />
          <View style={[styles.dot, styles.dotOn]} />
          <View style={[styles.dot, styles.dotOff]} />
        </View>
        <Pressable onPress={feel} style={({ pressed }) => [styles.button, pressed ? styles.pressed : null]}>
          <Text style={styles.cta}>{CTA}</Text>
        </Pressable>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: css(BG_BOTTOM) },
  flex: { flex: 1 },
  label: { position: 'absolute', left: 0, top: 0 },
  labelTitle: { fontSize: 14, lineHeight: 17, fontWeight: '600', color: css(INK) },
  labelCaption: { fontSize: 12, lineHeight: 15, marginTop: 4, color: css(CAPTION) },
  header: { position: 'absolute', left: 24, right: 24, height: 32, flexDirection: 'row', alignItems: 'center' },
  mark: { width: 18, height: 21, marginRight: 8 },
  brand: { fontSize: 17, fontWeight: '600', letterSpacing: -0.2, color: css(INK) },
  skip: { fontSize: 16, color: css(GREY) },
  copy: { position: 'absolute', left: 24, right: 24 },
  eyebrow: { fontSize: 12, fontWeight: '600', letterSpacing: 1.4, color: css(TEAL) },
  title: { marginTop: 10, fontSize: 34, lineHeight: 39, fontWeight: '700', letterSpacing: -0.6, color: css(INK) },
  body: { marginTop: 10, fontSize: 15, lineHeight: 21, color: css(GREY) },
  footer: { position: 'absolute', left: 0, right: 0, alignItems: 'center' },
  dots: { flexDirection: 'row', gap: 8, height: 6, marginBottom: 28 },
  dot: { height: 6, borderRadius: 3 },
  dotOff: { width: 6, backgroundColor: css(DOT_OFF) },
  dotOn: { width: 20, backgroundColor: css(INK) },
  button: {
    alignSelf: 'stretch',
    marginHorizontal: 24,
    height: 54,
    borderRadius: 27,
    backgroundColor: css(INK),
    alignItems: 'center',
    justifyContent: 'center',
    shadowColor: css(INK),
    shadowOpacity: 0.18,
    shadowRadius: 12,
    shadowOffset: { width: 0, height: 6 },
    elevation: 6,
  },
  pressed: { transform: [{ scale: 0.97 }] },
  cta: { fontSize: 17, fontWeight: '600', color: '#ffffff' },
});
