import {
  Canvas,
  ClipOp,
  FilterMode,
  Group,
  matchFont,
  MipmapMode,
  PaintStyle,
  Path,
  Picture,
  Rect,
  Skia,
  StrokeCap,
  StrokeJoin,
  Text as SkiaText,
  TileMode,
  useImage,
  type SkCanvas,
  type SkFont,
  type SkImage,
  type SkPath,
} from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { AccessibilityInfo, AppState, type LayoutChangeEvent, StyleSheet, useWindowDimensions, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import {
  type SharedValue,
  useDerivedValue,
  useFrameCallback,
  useReducedMotion,
  useSharedValue,
} from 'react-native-reanimated';
import { scheduleOnRN } from 'react-native-worklets';

export const LOOP = 8;
const ORANGE = '#F04A2D';
const PAPER = '#FFFDF7';
const INK = '#222B28';
const SECONDARY = '#6E7770';
const GLYPHS = ['F', 'A', 'R', 'L', 'I'];

function clamp(value: number) {
  'worklet';
  return Math.max(0, Math.min(1, value));
}

function smooth(value: number) {
  'worklet';
  const u = clamp(value);
  return u * u * (3 - 2 * u);
}

function out(value: number) {
  'worklet';
  const u = 1 - clamp(value);
  return 1 - u * u * u;
}

function mix(a: number, b: number, u: number) {
  'worklet';
  return a + (b - a) * u;
}

function paint(color: string, alpha = 1) {
  const result = Skia.Paint();
  result.setAntiAlias(true);
  result.setColor(Skia.Color(color));
  result.setAlphaf(alpha);
  return result;
}

function roundRect(x: number, y: number, width: number, height: number, radius = 0) {
  const path = Skia.Path.Make();
  path.addRRect(Skia.RRectXY(Skia.XYWHRect(x, y, width, height), radius, radius));
  return path;
}

function oval(x: number, y: number, width: number, height: number) {
  const path = Skia.Path.Make();
  path.addOval(Skia.XYWHRect(x, y, width, height));
  return path;
}

function fill(c: SkCanvas, path: SkPath, color: string, alpha = 1) {
  c.drawPath(path, paint(color, alpha));
}

function stroke(c: SkCanvas, path: SkPath, color: string, width: number, rounded = false) {
  const p = paint(color);
  p.setStyle(PaintStyle.Stroke);
  p.setStrokeWidth(width);
  if (rounded) {
    p.setStrokeCap(StrokeCap.Round);
    p.setStrokeJoin(StrokeJoin.Round);
  }
  c.drawPath(path, p);
}

function gradient(c: SkCanvas, path: SkPath, colors: string[], x0: number, y0: number, x1: number, y1: number) {
  const p = paint(PAPER);
  p.setShader(Skia.Shader.MakeLinearGradient(
    { x: x0, y: y0 }, { x: x1, y: y1 }, colors.map(color => Skia.Color(color)), null, TileMode.Clamp,
  ));
  c.drawPath(path, p);
}

type Fonts = Record<string, SkFont>;
type Weight = '400' | '500' | '600' | '700';
type Photos = { hero: SkImage; canyon: SkImage; coast: SkImage };

function text(
  c: SkCanvas, fonts: Fonts, value: string, x: number, y: number, size: number,
  color: string, weight: Weight = '400', anchor: 'leading' | 'center' | 'trailing' = 'leading', alpha = 1,
) {
  const font = fonts[`${size}-${weight}`];
  const width = font.getGlyphWidths(font.getGlyphIDs(value)).reduce((sum, n) => sum + n, 0);
  const metrics = font.getMetrics();
  const left = anchor === 'center' ? x - width / 2 : anchor === 'trailing' ? x - width : x;
  c.drawText(value, left, y - (metrics.ascent + metrics.descent) / 2, paint(color, alpha), font);
}

function photo(c: SkCanvas, image: SkImage, x: number, y: number, width: number, height: number) {
  c.drawImageRectOptions(
    image,
    Skia.XYWHRect(0, 0, image.width(), image.height()),
    Skia.XYWHRect(x, y, width, height),
    FilterMode.Linear,
    MipmapMode.None,
    paint(PAPER),
  );
}

function landscape(c: SkCanvas, fonts: Fonts, image: SkImage) {
  c.save();
  c.clipPath(roundRect(24, 286, 345, 262, 22), ClipOp.Intersect, true);
  photo(c, image, 24, 286, 345, 262);
  gradient(c, roundRect(24, 286, 345, 96), ['rgba(0,0,0,0.22)', 'rgba(0,0,0,0)'], 24, 286, 24, 382);
  gradient(c, roundRect(24, 386, 345, 162), ['rgba(23,62,54,0)', 'rgba(23,62,54,0.78)'], 24, 386, 24, 548);
  text(c, fonts, 'THE LONG WAY HOME', 43, 310, 9, PAPER, '600');
  text(c, fonts, 'The quiet side', 43, 474, 24, PAPER, '700');
  text(c, fonts, 'of everywhere.', 43, 502, 24, PAPER, '700');
  text(c, fonts, 'Explore the valley  ↗', 43, 530, 11, PAPER, '500', 'leading', 0.92);
  c.restore();
}

function mini(c: SkCanvas, fonts: Fonts, x: number, image: SkImage) {
  c.save();
  c.clipPath(roundRect(x, 610, 167, 127, 16), ClipOp.Intersect, true);
  photo(c, image, x, 610, 167, 127);
  fill(c, oval(x + 133, 620, 24, 24), PAPER, 0.95);
  text(c, fonts, '↗', x + 145, 632, 15, INK, '500', 'center');
  c.restore();
}

/** This static picture is recorded once; only its aperture changes during playback. */
function makeHome(fonts: Fonts, photos: Photos) {
  const recorder = Skia.PictureRecorder();
  const c = recorder.beginRecording(Skia.XYWHRect(0, 0, 393, 852));
  fill(c, roundRect(0, 0, 393, 852), PAPER);
  text(c, fonts, 'farli', 24, 87, 26, INK, '700');
  const sparkle = Skia.Path.MakeFromSVGString('M355 74 Q355 85 366 85 Q355 85 355 96 Q355 85 344 85 Q355 85 355 74')!;
  fill(c, sparkle, ORANGE);
  fill(c, roundRect(24, 112, 345, 46, 23), '#F1F2EA');
  stroke(c, oval(40, 128, 11, 11), SECONDARY, 1.5);
  stroke(c, Skia.Path.MakeFromSVGString('M50 138 L54 142')!, SECONDARY, 1.5, true);
  text(c, fonts, 'Where will you wander?', 65, 135, 13, SECONDARY);
  text(c, fonts, 'Find your', 24, 195, 32, INK, '700');
  text(c, fonts, 'somewhere.', 24, 232, 32, INK, '700');
  text(c, fonts, 'Small detours. Big memories.', 24, 264, 13, SECONDARY);
  landscape(c, fonts, photos.hero);
  text(c, fonts, 'A little closer', 24, 584, 21, INK, '600');
  text(c, fonts, 'View all', 369, 584, 11, ORANGE, '600', 'trailing');
  mini(c, fonts, 24, photos.canyon);
  mini(c, fonts, 202, photos.coast);
  text(c, fonts, 'Amber Canyon', 24, 756, 14, INK, '600');
  text(c, fonts, 'Moss Coast', 202, 756, 14, INK, '600');
  text(c, fonts, '12 local adventures', 24, 777, 10, SECONDARY);
  text(c, fonts, '8 slow escapes', 202, 777, 10, SECONDARY);
  fill(c, roundRect(0, 794, 393, 58), PAPER);
  fill(c, roundRect(24, 794, 345, 0.75), '#E6E8E0');
  stroke(c, oval(58, 803, 14, 14), ORANGE, 1.6);
  fill(c, Skia.Path.MakeFromSVGString('M61 813 L69 807 L66 815 Z')!, ORANGE);
  const heart = Skia.Path.MakeFromSVGString('M196.5 817 C180 807 190 798 189 807 C197 800 205 800 204 807 Q207 811 196.5 817')!;
  stroke(c, heart, SECONDARY, 1.5, true);
  stroke(c, roundRect(321, 803, 14, 14, 3), SECONDARY, 1.5);
  stroke(c, roundRect(325, 800, 6, 5, 2), SECONDARY, 1.5);
  return recorder.finishRecordingAsPicture();
}

function lightHaptic() {
  Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
}

type GlyphProps = { char: string; index: number; slot: number; width: number; font: SkFont; time: SharedValue<number> };

function FarliGlyph({ char, index, slot, width, font, time }: GlyphProps) {
  const transform = useDerivedValue(() => {
    const t = time.get();
    const enter = out((t - 0.98 - index * 0.035) / 0.38);
    const exit = smooth((t - 1.95 - index * 0.025) / 0.39);
    const scale = mix(0.65, 1, enter) * (1 - 0.82 * exit);
    return [{ translateX: mix(196.5, slot, enter) * (1 - exit) + 196.5 * exit }, { translateY: 426 }, { scale }];
  });
  const opacity = useDerivedValue(() => {
    const t = time.get();
    if (t <= 0.98 || t >= 2.45) return 0;
    return out((t - 0.98 - index * 0.035) / 0.38) * (1 - smooth((t - 1.95 - index * 0.025) / 0.39));
  });
  const metrics = font.getMetrics();
  return <Group transform={transform} opacity={opacity}>
    <SkiaText x={-width / 2} y={-(metrics.ascent + metrics.descent) / 2} text={char} font={font} color={PAPER} />
  </Group>;
}

type Props = { autoPlay?: boolean; previewTime?: number };

/** A wordmark becomes a falling dot, then launches an aperture into a travel feed. */
export function FarliDotSplash({ autoPlay = false, previewTime }: Props) {
  const window = useWindowDimensions();
  const [size, setSize] = useState({ width: window.width, height: window.height });
  const [active, setActive] = useState(AppState.currentState === 'active');
  const [interactionActive, setInteractionActive] = useState(false);
  const frameWasActive = useRef(false);
  const initiallyReduced = useReducedMotion();
  const [reduced, setReduced] = useState(initiallyReduced);
  useEffect(() => {
    const subscription = AccessibilityInfo.addEventListener('reduceMotionChanged', setReduced);
    void AccessibilityInfo.isReduceMotionEnabled().then(setReduced);
    return () => subscription.remove();
  }, []);
  const hero = useImage(require('./assets/hero.jpg'));
  const canyon = useImage(require('./assets/canyon.jpg'));
  const coast = useImage(require('./assets/coast.jpg'));
  const photos = useMemo<Photos | null>(() => {
    if (!hero || !canyon || !coast) return null;
    return {
      hero: hero.makeNonTextureImage() ?? hero,
      canyon: canyon.makeNonTextureImage() ?? canyon,
      coast: coast.makeNonTextureImage() ?? coast,
    };
  }, [hero, canyon, coast]);
  const ready = photos !== null;
  const { width, height } = size;
  const time = useSharedValue(ready ? previewTime !== undefined ? Math.max(0, previewTime) % LOOP : reduced ? 4.8 : 0 : 0);
  const now = useSharedValue(0);
  const startRaw = useSharedValue(-1);
  const lastRaw = useSharedValue(-1);
  const pausedDuration = useSharedValue(0);
  const resuming = useSharedValue(true);
  const epoch = useSharedValue(0);
  const held = useSharedValue(0);
  const released = useSharedValue(-1);
  const touching = useSharedValue(false);
  const launch = useSharedValue(false);
  const didScrub = useSharedValue(false);
  const passedLaunch = useSharedValue(false);
  const finishInteraction = useCallback(() => setInteractionActive(false), []);
  const beginInteraction = useCallback(() => setInteractionActive(true), []);

  useEffect(() => {
    const subscription = AppState.addEventListener('change', state => setActive(state === 'active'));
    return () => subscription.remove();
  }, []);

  useEffect(() => {
    time.set(ready ? previewTime !== undefined ? Math.max(0, previewTime) % LOOP : reduced ? 4.8 : 0 : 0);
    now.set(0);
    epoch.set(0);
    startRaw.set(-1);
    lastRaw.set(-1);
    pausedDuration.set(0);
    touching.set(false);
    released.set(-1);
    launch.set(false);
    held.set(0);
    // A playback-mode reset also ends the previous gesture lifecycle.
    // eslint-disable-next-line react-hooks/set-state-in-effect
    setInteractionActive(false);
  }, [autoPlay, previewTime, reduced, ready, time, now, epoch, startRaw, lastRaw, pausedDuration, touching, released, launch, held]);

  const frame = useFrameCallback(info => {
    const raw = info.timestamp / 1000;
    if (startRaw.get() < 0) {
      startRaw.set(raw);
      lastRaw.set(raw);
    }
    if (resuming.get()) {
      pausedDuration.set(pausedDuration.get() + Math.max(0, raw - lastRaw.get()));
      resuming.set(false);
    }
    lastRaw.set(raw);
    const clock = raw - startRaw.get() - pausedDuration.get();
    now.set(clock);
    if (touching.get()) {
      time.set(held.get());
      return;
    }
    if (released.get() >= 0) {
      const age = Math.max(0, clock - released.get());
      const heldPhase = launch.get() ? Math.min(4.1, 2.45 + age) : held.get();
      time.set(age < 2 ? heldPhase : age < 2.6 ? heldPhase * (1 - smooth((age - 2) / 0.6)) : autoPlay ? (age - 2.6) % LOOP : 0);
      if (age >= 2.6) {
        released.set(-1);
        launch.set(false);
        held.set(0);
        epoch.set(clock - (age - 2.6));
        if (!autoPlay) scheduleOnRN(finishInteraction);
      }
    } else {
      time.set(autoPlay ? Math.max(0, clock - epoch.get()) % LOOP : 0);
    }
  }, false);

  useEffect(() => {
    const shouldRun = ready && active && !reduced && previewTime === undefined && (autoPlay || interactionActive);
    if (shouldRun && !frameWasActive.current) resuming.set(true);
    frameWasActive.current = shouldRun;
    frame.setActive(shouldRun);
    return () => frame.setActive(false);
  }, [ready, active, reduced, previewTime, autoPlay, interactionActive, frame, resuming]);

  const fonts = useMemo(() => {
    const result: Fonts = {};
    for (const size of [9, 10, 11, 13, 14, 15, 21, 24, 26, 32, 36]) {
      for (const weight of ['400', '500', '600', '700'] as const) {
        const font = matchFont({ fontFamily: 'System', fontSize: size, fontWeight: weight });
        font.setLinearMetrics(true);
        result[`${size}-${weight}`] = font;
      }
    }
    return result;
  }, []);
  const home = useMemo(() => photos ? makeHome(fonts, photos) : null, [fonts, photos]);
  const glyphLayout = useMemo(() => {
    const font = fonts['36-700'];
    const widths = GLYPHS.map(char => font.getGlyphWidths(font.getGlyphIDs(char))[0] ?? 0);
    const start = 196.5 - (widths.reduce((sum, n) => sum + n, 0) + 6) / 2;
    return widths.map((width, index) => ({
      slot: start + widths.slice(0, index).reduce((sum, n) => sum + n, 0) + index * 1.5 + width / 2,
      width,
    }));
  }, [fonts]);
  const markStem = useMemo(() => Skia.Path.MakeFromSVGString('M-17 22 L-17 -11 Q-17 -21 -7 -21 L19 -21')!, []);
  const markArrow = useMemo(() => Skia.Path.MakeFromSVGString('M7 -33 L20 -21 L7 -9')!, []);
  const markTransform = useDerivedValue(() => {
    const t = time.get();
    const returning = smooth((t - 6.95) / 0.35);
    const scale = t < 6.95 ? 1 - 0.55 * smooth((t - 0.9) / 0.45) : mix(0.45, 1, returning);
    return [{ translateX: 196.5 }, { translateY: 426 }, { scale }];
  });
  const markOpacity = useDerivedValue(() => time.get() < 6.95 ? 1 - smooth((time.get() - 0.9) / 0.45) : smooth((time.get() - 6.95) / 0.35));
  const dot = useDerivedValue(() => {
    const t = time.get();
    const path = Skia.Path.Make();
    if (!((t >= 2.25 && t < 3.38) || (t >= 6.5 && t < 7.3))) return path;
    let y = 426;
    let radius = 5;
    let stretch = 1;
    if (t >= 2.45 && t < 2.82) {
      const u = clamp((t - 2.45) / 0.37);
      y = mix(426, 586, u * u);
      stretch = 1 + 0.7 * Math.sin(Math.PI * u) - 0.28 * Math.pow(u, 8);
    } else if (t >= 2.82 && t < 3.38) {
      const u = out((t - 2.82) / 0.56);
      y = mix(586, 92, u);
      radius = mix(5, 11, u);
      const rise = clamp((t - 2.82) / 0.56);
      stretch = 0.72 + 0.28 * smooth(rise) + 0.6 * Math.sin(Math.PI * rise);
    } else if (t >= 6.5) {
      const u = smooth((t - 6.5) / 0.45);
      y = mix(92, 426, u);
      radius = mix(11, 5, u) * (1 - smooth((t - 6.95) / 0.35));
    }
    radius *= smooth((t - 2.25) / 0.2);
    const rx = radius / Math.sqrt(stretch);
    const ry = radius * stretch;
    path.addOval(Skia.XYWHRect(196.5 - rx, y - ry, rx * 2, ry * 2));
    return path;
  });
  const aperture = useDerivedValue(() => {
    const t = time.get();
    const path = Skia.Path.Make();
    if (t >= 3.38 && t < 6.5) {
      const open = out((t - 3.38) / 0.67) * (1 - smooth((t - 5.7) / 0.8));
      path.addCircle(196.5, 92, mix(11, 950, open));
    }
    return path;
  });
  const hintOpacity = useDerivedValue(() => (time.get() < 1.2 ? 1 - smooth((time.get() - 0.9) / 0.3) : smooth((time.get() - 7.25) / 0.3)) * 0.72);
  const hintFont = fonts['11-500'];
  const hintMetrics = hintFont.getMetrics();
  const hintWidth = hintFont.getGlyphWidths(hintFont.getGlyphIDs('Drag to explore')).reduce((sum, n) => sum + n, 0);
  const scale = Math.min(width / 393, height / 852);

  function beginTouch() {
    'worklet';
    if (touching.get()) return;
    held.set(time.get());
    released.set(-1);
    launch.set(false);
    touching.set(true);
    passedLaunch.set(false);
    didScrub.set(false);
    scheduleOnRN(beginInteraction);
  }

  function scrubTo(x: number) {
    'worklet';
    didScrub.set(true);
    const phase = clamp(x / width) * 7.3;
    held.set(phase);
    time.set(phase);
    if (phase >= 2.82 && !passedLaunch.get()) {
      passedLaunch.set(true);
      scheduleOnRN(lightHaptic);
    }
  }

  function releaseTouch(isTap: boolean) {
    'worklet';
    launch.set(isTap);
    if (isTap) {
      time.set(2.45);
      scheduleOnRN(lightHaptic);
    }
    touching.set(false);
    released.set(now.get());
  }

  const touchEnabled = ready && !reduced && previewTime === undefined;
  const pan = Gesture.Pan().minDistance(8).maxPointers(1).enabled(touchEnabled)
    .onBegin(beginTouch)
    .onStart(event => scrubTo(event.x))
    .onUpdate(event => scrubTo(event.x))
    .onEnd(() => {
      if (touching.get()) releaseTouch(false);
    })
    .onFinalize(() => {
      if (touching.get()) releaseTouch(false);
    });
  const tap = Gesture.Tap().maxDistance(8).maxDuration(2_147_483_647).enabled(touchEnabled)
    .onTouchesDown((event, manager) => {
      if (event.numberOfTouches > 1) manager.fail();
    })
    .onBegin(beginTouch)
    .onEnd((_event, success) => {
      if (success) releaseTouch(true);
    });

  const onLayout = (event: LayoutChangeEvent) => {
    const next = event.nativeEvent.layout;
    if (next.width !== width || next.height !== height) setSize({ width: next.width, height: next.height });
  };

  return <GestureDetector gesture={Gesture.Race(pan, tap)}>
    <View
      style={styles.screen}
      onLayout={onLayout}
      accessible
      testID="farli-dot-splash"
      accessibilityLabel={reduced ? 'Farli travel home. Find your somewhere.' : 'Farli splash and travel preview.'}
      accessibilityValue={{ text: 'The quiet side of everywhere. Explore the valley. A little closer: Amber Canyon, 12 local adventures. Moss Coast, 8 slow escapes.' }}
      accessibilityHint={reduced ? undefined : 'Drag to scrub the splash, or tap to launch the travel feed.'}
      accessibilityActions={!ready || reduced || previewTime !== undefined ? [] : [{ name: 'activate', label: 'Launch travel feed' }]}
      onAccessibilityAction={() => {
        if (!ready || reduced || previewTime !== undefined) return;
        touching.set(false);
        launch.set(true);
        time.set(2.45);
        released.set(now.get());
        setInteractionActive(true);
        lightHaptic();
      }}
    >
      <StatusBar style="dark" />
      <Canvas style={StyleSheet.absoluteFill} pointerEvents="none">
        <Rect x={0} y={0} width={width} height={height} color={ORANGE} />
        <Group transform={[{ translateX: (width - 393 * scale) / 2 }, { translateY: (height - 852 * scale) / 2 }, { scale }]}>
          <Group transform={markTransform} opacity={markOpacity}>
            <Path path={markStem} color={PAPER} style="stroke" strokeWidth={10} strokeCap="round" strokeJoin="round" />
            <Path path={markArrow} color={PAPER} style="stroke" strokeWidth={10} strokeCap="round" strokeJoin="round" />
            <Path path={oval(-22, 13, 10, 10)} color={ORANGE} />
          </Group>
          {GLYPHS.map((char, i) => <FarliGlyph key={char} char={char} index={i} slot={glyphLayout[i].slot} width={glyphLayout[i].width} font={fonts['36-700']} time={time} />)}
          <Path path={dot} color={PAPER} />
          <Group clip={aperture}>
            <Rect x={-1000} y={-1000} width={2393} height={2852} color={PAPER} />
            {home && <Picture picture={home} />}
          </Group>
          <SkiaText x={196.5 - hintWidth / 2} y={776 - (hintMetrics.ascent + hintMetrics.descent) / 2} text="Drag to explore" font={hintFont} color={PAPER} opacity={hintOpacity} />
        </Group>
      </Canvas>
    </View>
  </GestureDetector>;
}

const styles = StyleSheet.create({ screen: { flex: 1, overflow: 'hidden', backgroundColor: ORANGE } });
