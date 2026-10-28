import {
  BlendMode,
  BlurStyle,
  Canvas,
  matchFont,
  PaintStyle,
  Picture,
  Skia,
  type SkCanvas,
  type SkFont,
  type SkRuntimeEffect,
  TileMode,
} from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useEffect, useMemo, useState } from 'react';
import { type LayoutChangeEvent, Platform, StyleSheet, Text, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  type SharedValue,
  useAnimatedStyle,
  useDerivedValue,
  useFrameCallback,
  useReducedMotion,
  useSharedValue,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

import { drawBack, drawFront, drawItem, ellipse, fillColor, fillShader, hex, radial } from './prismBeamArt';
import { BEAM_SKSL } from './beamShader';
import {
  type Frame,
  type Geo,
  LOOP,
  makeGeo,
  makeSim,
  onFolder,
  REDUCED_AT,
  scriptFrame,
  type Sim,
  simFrame,
  simPress,
  simRelease,
} from './prismBeamMotion';

/*
 Prism Beam Folder — "Larder", a grocery list app saving the picks in your cart to a collection.

 A near-black stage and a light-blue folder named "Weekend Snacks". Press it and a prism beam
 shoots up out of it and opens into a cone of soft pink, red, amber, cream, blue and violet bands
 (one Skia runtime shader, beamShader.ts); the picks — peaches, cherries, aubergines, chilies drawn
 in code (prismBeamArt.ts) — float down through the light into the folder, which bobs as each one
 lands while its badge ticks up. Then the beam narrows to a line and drops back into the folder.

 autoPlay: LOOP = 8 s (prismBeamMotion.ts). Touch: hold the folder to open the beam and pour (one
 pick every 0.26 s while held); release and the beam closes after the last landing. Light haptics
 when the beam opens and on each landing. 2 s after the release, once the folder is back at rest,
 the loop restarts from t = 0. Reduced motion: a still of the beam fully open, no touch.
*/

type Props = {
  /** Plays the 8 s press-pour-close loop until touched. */
  autoPlay?: boolean;
};

const EFFECT = Skia.RuntimeEffect.Make(BEAM_SKSL) as SkRuntimeEffect;
const BG = '#08070C';
const TEXT = '#F3F0F8';
const MUTED = '#8D88A0';

function drawStage(c: SkCanvas, g: Geo, f: Frame, font: SkFont) {
  'worklet';
  const cx = g.cx;
  const cy = g.folderCY;

  const bg = Skia.Path.Make();
  bg.addRect(Skia.XYWHRect(0, 0, g.w, g.h));
  fillShader(c, bg, radial(cx, cy, g.h * 0.75, [hex(0x14101c), hex(0x08070c)]));

  // Floor glow under the folder.
  if (f.glow > 0.001) {
    c.save();
    c.translate(cx, cy + 44);
    c.scale(1, 0.3);
    fillShader(
      c,
      ellipse(-190, -190, 380, 380),
      radial(0, 0, 190, [hex(0xff5fd2, 0.32 * f.glow), hex(0xb65cff, 0.14 * f.glow), hex(0xb65cff, 0)], [0, 0.5, 1]),
    );
    c.restore();
  }

  // The beam.
  if (f.beamAlpha > 0.001) {
    const shader = EFFECT.makeShader([
      cx,
      g.originY,
      g.length,
      f.tip,
      f.hb,
      f.ht,
      f.beamAlpha,
      (2 * Math.PI * f.phase) / LOOP,
      g.top + 60,
      g.top + 260,
      0,
      0,
    ]);
    const paint = Skia.Paint();
    paint.setShader(shader);
    c.drawRect(Skia.XYWHRect(0, 0, g.w, g.h), paint);
  }

  // Contact shadow.
  const shadow = Skia.Paint();
  shadow.setAntiAlias(true);
  shadow.setColor(hex(0x000000, 0.7));
  shadow.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 4, true));
  c.drawOval(Skia.XYWHRect(cx - 55 * f.folderSX, cy + 38, 110 * f.folderSX, 12), shadow);

  // The folder scales from the middle of its bottom edge; items sit between its back and front.
  c.save();
  c.translate(cx, cy + 42);
  c.scale(f.folderSX, f.folderSY);
  c.translate(0, -42);
  drawBack(c);
  if (f.beamAlpha > 0.001) {
    const k = f.beamAlpha * (0.4 + 0.6 * f.glow);
    const p = Skia.Paint();
    p.setAntiAlias(true);
    p.setBlendMode(BlendMode.Plus);
    p.setShader(radial(0, -34, 70, [hex(0xfff3e6, 0.55 * k), hex(0xff5fd2, 0.22 * k), hex(0xff5fd2, 0)], [0, 0.45, 1]));
    c.drawPath(ellipse(-70, -34 - 70, 140, 140), p);
  }
  c.restore();

  for (let i = 0; i < f.items.length; i++) {
    const it = f.items[i];
    const s = (it.scale * it.size) / 48;
    c.save();
    c.translate(it.x, it.y);
    c.rotate(it.angle, 0, 0);
    c.scale(s, s);
    if (it.alpha < 0.999) {
      const layer = Skia.Paint();
      layer.setAlphaf(it.alpha);
      c.saveLayer(layer);
      drawItem(c, it.kind);
      c.restore();
    } else {
      drawItem(c, it.kind);
    }
    c.restore();
  }

  c.save();
  c.translate(cx, cy + 42);
  c.scale(f.folderSX, f.folderSY);
  c.translate(0, -42);
  drawFront(c, f.open, f.glow * f.beamAlpha);
  c.restore();

  // Press ripple.
  if (f.rippleA > 0.001) {
    fillColor(c, ellipse(cx - 22, cy - 22, 44, 44), hex(0xffffff, f.rippleA * 0.45));
    const ring = Skia.Paint();
    ring.setAntiAlias(true);
    ring.setStyle(PaintStyle.Stroke);
    ring.setStrokeWidth(2);
    ring.setColor(hex(0xffffff, f.rippleA));
    c.drawCircle(cx, cy, f.rippleR, ring);
  }

  // Badge.
  if (f.badge > 0.001) {
    c.save();
    c.translate(cx + 46, cy - 38);
    c.scale(f.badge, f.badge);
    fillColor(c, ellipse(-14.5, -14.5, 29, 29), hex(0x08070c));
    const b = Skia.Paint();
    b.setAntiAlias(true);
    b.setShader(
      Skia.Shader.MakeLinearGradient({ x: 0, y: -13 }, { x: 0, y: 13 }, [hex(0xff4d9a), hex(0xe8317f)], null, TileMode.Clamp),
    );
    c.drawCircle(0, 0, 13, b);
    const label = `${f.count}`;
    const widths = font.getGlyphWidths(font.getGlyphIDs(label));
    let w = 0;
    for (let i = 0; i < widths.length; i++) w += widths[i];
    const m = font.getMetrics();
    const white = Skia.Paint();
    white.setColor(hex(0xffffff));
    c.drawText(label, -w / 2, -(m.ascent + m.descent) / 2, white, font);
    c.restore();
  }
}

export function PrismBeamFolder({ autoPlay = false }: Props) {
  const insets = useSafeAreaInsets();
  const reduced = useReducedMotion();
  const auto = autoPlay && !reduced;
  const [size, setSize] = useState<{ width: number; height: number } | null>(null);
  const geo = useMemo<Geo>(
    () => makeGeo(size?.width ?? 1, size?.height ?? 1, insets.top, insets.bottom),
    [size, insets.top, insets.bottom],
  );
  const font = useMemo(
    () => matchFont({ fontFamily: Platform.select({ ios: 'Helvetica', default: 'sans-serif' }), fontSize: 13, fontWeight: '700' }),
    [],
  );

  const sim = useSharedValue<Sim>(makeSim());
  const frame = useSharedValue<Frame>(scriptFrame(reduced ? REDUCED_AT : 0, geo));
  const now = useSharedValue(0);
  const feel = useSharedValue(0);
  const shownCount = useSharedValue(0);
  const [count, setCount] = useState(0);

  useEffect(() => {
    sim.set(makeSim());
    if (reduced) frame.set(scriptFrame(REDUCED_AT, geo));
  }, [auto, reduced, geo, sim, frame]);

  const buzz = () => {
    if (process.env.EXPO_OS !== 'web') Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
  };

  useFrameCallback((info) => {
    if (reduced || !size) return;
    const t = info.timestamp / 1000;
    now.set(t);
    const s = sim.get();
    const f = simFrame(s, t, geo, auto);
    frame.set(f);
    if (s.feel !== feel.get()) {
      feel.set(s.feel);
      scheduleOnRN(buzz);
    }
    if (f.count !== shownCount.get()) {
      shownCount.set(f.count);
      scheduleOnRN(setCount, f.count);
    }
  });

  const touch = Gesture.Pan()
    .enabled(!reduced)
    .minDistance(0)
    .onBegin((e) => {
      if (!onFolder(geo, e.x, e.y)) return;
      simPress(sim.get(), now.get(), geo, auto);
    })
    .onFinalize(() => {
      const s = sim.get();
      if (s.holding) simRelease(s, now.get());
    });

  const picture = useDerivedValue(() => {
    const recorder = Skia.PictureRecorder();
    const canvas = recorder.beginRecording(Skia.XYWHRect(0, 0, geo.w, geo.h));
    drawStage(canvas, geo, frame.get(), font);
    return recorder.finishRecordingAsPicture();
  });

  const onLayout = (e: LayoutChangeEvent) => {
    const { width, height } = e.nativeEvent.layout;
    if (!size || size.width !== width || size.height !== height) setSize({ width, height });
  };

  return (
    <GestureDetector gesture={touch}>
      <View
        style={styles.screen}
        onLayout={onLayout}
        accessible
        accessibilityLabel="Weekend Snacks folder. Hold it to pour your picks in."
      >
        <StatusBar style="light" />
        {size ? (
          <Canvas style={StyleSheet.absoluteFill}>
            <Picture picture={picture} />
          </Canvas>
        ) : null}
        {size ? <Labels geo={geo} frame={frame} count={count} /> : null}
      </View>
    </GestureDetector>
  );
}

function Labels({ geo, frame, count }: { geo: Geo; frame: SharedValue<Frame>; count: number }) {
  const header = useAnimatedStyle(() => ({ opacity: frame.get().header }));
  const ready = useAnimatedStyle(() => ({ opacity: 1 - frame.get().saved }));
  const saved = useAnimatedStyle(() => ({ opacity: frame.get().saved }));
  return (
    <>
      <Animated.View pointerEvents="none" style={[styles.header, { top: geo.top + 18 }, header]}>
        <Text style={styles.eyebrow} numberOfLines={1}>
          LARDER · COLLECTIONS
        </Text>
        <Text style={styles.title} numberOfLines={1} adjustsFontSizeToFit minimumFontScale={0.6}>
          Save your picks
        </Text>
        <Text style={styles.sub} numberOfLines={1}>
          Hold a folder to pour them in.
        </Text>
      </Animated.View>
      <View pointerEvents="none" style={[styles.folderText, { top: geo.folderCY + 42 + 16 }]}>
        <Text style={styles.folderName} numberOfLines={1}>
          Weekend Snacks
        </Text>
        <View style={styles.countLine}>
          <Animated.Text style={[styles.count, styles.countReady, ready]} numberOfLines={1}>
            9 picks ready
          </Animated.Text>
          <Animated.Text style={[styles.count, styles.countSaved, saved]} numberOfLines={1}>
            {`+${count} saved`}
          </Animated.Text>
        </View>
      </View>
      <Text pointerEvents="none" style={[styles.hint, { top: geo.h - geo.bottom - 39 }]} numberOfLines={1}>
        Hold the folder to pour
      </Text>
    </>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: BG },
  header: { position: 'absolute', left: 24, right: 24 },
  eyebrow: { height: 14, lineHeight: 14, fontSize: 11, fontWeight: '600', letterSpacing: 2, color: '#6F6A82' },
  title: {
    marginTop: 6,
    height: 36,
    lineHeight: 36,
    fontSize: 30,
    fontWeight: '700',
    letterSpacing: -0.5,
    color: TEXT,
  },
  sub: { marginTop: 4, height: 20, lineHeight: 20, fontSize: 15, color: MUTED },
  folderText: { position: 'absolute', left: 0, right: 0, alignItems: 'center' },
  folderName: { height: 22, lineHeight: 22, fontSize: 17, fontWeight: '600', color: TEXT },
  countLine: { marginTop: 4, height: 18, alignSelf: 'stretch' },
  count: {
    position: 'absolute',
    left: 0,
    right: 0,
    height: 18,
    lineHeight: 18,
    fontSize: 14,
    fontWeight: '500',
    textAlign: 'center',
    fontVariant: ['tabular-nums'],
  },
  countReady: { color: MUTED },
  countSaved: { color: '#FF6FB5' },
  hint: {
    position: 'absolute',
    left: 0,
    right: 0,
    height: 18,
    lineHeight: 18,
    fontSize: 13,
    fontWeight: '500',
    textAlign: 'center',
    color: '#5F5A72',
  },
});
