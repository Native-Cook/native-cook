import * as Haptics from 'expo-haptics';
import { useEffect, useMemo, useState } from 'react';
import type { LayoutChangeEvent } from 'react-native';
import { AccessibilityInfo, Platform, Pressable, StyleSheet, useWindowDimensions, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import type { SharedValue } from 'react-native-reanimated';
import { useDerivedValue, useFrameCallback, useSharedValue } from 'react-native-reanimated';
import {
  AlphaType, Canvas, ColorType, createPicture, FilterMode, ImageShader, MipmapMode, Picture, Rect, Shader, Skia, useImage,
  useTypeface,
} from '@shopify/react-native-skia';
import type { SkImage } from '@shopify/react-native-skia';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';

import { useReducedMotionPreference } from '@/shared/useReducedMotionPreference';

import type { Layout, Ui } from './stt-draw';
import { CX, drawUi, PILL, setLine, TENTHS } from './stt-draw';
import type { Core, Frame } from './stt-motion';
import { loopFrame, newCore, pillDown, setStill, spin, step, touchDown, touchUp } from './stt-motion';
import { TOP } from './stt-shader';

const TOP_IMAGE = require('./assets/top.webp');
const FONTS = {
  serif: require('./assets/DMSerifDisplay-Regular.ttf'),
  medium: require('./assets/Inter-Medium.ttf'),
  semibold: require('./assets/Inter-SemiBold.ttf'),
};
const DESIGN = { w: 393, h: 852 };
/** The photo's top-left (pt): it keeps its size and x, its top at 0 (the designer's answer 9 on #54). */
const IMAGE = { x: -87.5, y: 0 };
/** Where the top can be touched: the image's x 270–800, y 520–1092 px, in pt. */
const TOP_AREA = { x: 62, y: 288, w: 294, h: 318 };
/** A drag faster than this is a flick (pt/s); slower or shorter is a tap. */
const FLICK = { speed: 300, distance: 12 };
/** The image is magnified (1024 px over 568 pt at 3×), so mipmaps would never be used. */
const SAMPLING = { filter: FilterMode.Linear, mipmap: MipmapMode.None };

type Props = {
  /** Play the 7.6 s loop until a finger takes over. */
  autoPlay?: boolean;
};

/** The pixels decoded once into a raster image (a lazily decoded one sampled as black on a fresh install, #28). */
function rasterOf(image: SkImage): SkImage {
  const info = { width: image.width(), height: image.height(), colorType: ColorType.RGBA_8888, alphaType: AlphaType.Unpremul };
  const pixels = image.readPixels(0, 0, info);
  if (!(pixels instanceof Uint8Array)) return image;
  return Skia.Image.MakeImage(info, Skia.Data.fromBytes(pixels), info.width * 4) ?? image;
}

type Art = { top: SkImage; ui: Ui };

function useArt(): Art | null {
  const top = useImage(TOP_IMAGE);
  const serif = useTypeface(FONTS.serif);
  const medium = useTypeface(FONTS.medium);
  const semibold = useTypeface(FONTS.semibold);
  return useMemo(() => {
    if (!top || !serif || !medium || !semibold) return null;
    const fonts = {
      eyebrow: Skia.Font(semibold, 12),
      timer: Skia.Font(serif, 96),
      unit: Skia.Font(serif, 44),
      line: Skia.Font(medium, 15),
      chip: Skia.Font(semibold, 13),
      pill: Skia.Font(semibold, 17),
    };
    // Unhinted advances, as the reference's Canvas 2D lays them out (hinted ones round each glyph to a whole point).
    for (const font of Object.values(fonts)) {
      font.setLinearMetrics(true);
      // Skia 2.6.2's JSI setSubpixel reads a number (a boolean throws "Value is true, expected a number"); 1 keeps it on.
      font.setSubpixel(1 as unknown as boolean);
    }
    const tenths = Array.from({ length: TENTHS }, (_, i) => (i / 10).toFixed(1));
    return {
      top: rasterOf(top),
      ui: {
        fonts,
        eyebrow: setLine('SPIN · DESK TOYS', fonts.eyebrow, semibold, 4),
        timer: tenths.map((s) => setLine(s, fonts.timer, serif, -3)),
        keep: tenths.map((s) => setLine(`Flick it. Keep it up longer than ${s} s.`, fonts.line, medium)),
        downLine: tenths.map((s) => setLine(`Down after ${s} s. Flick again.`, fonts.line, medium)),
        best: tenths.map((s) => setLine(`Best · ${s} s`, fonts.chip, semibold)),
        unit: setLine('s', fonts.unit, serif),
        pill: setLine('Flick to spin', fonts.pill, semibold),
      },
    };
  }, [top, serif, medium, semibold]);
}

export function SpinningTopToy({ autoPlay = false }: Props) {
  const window = useWindowDimensions();
  const [size, setSize] = useState({ w: window.width, h: window.height });
  const art = useArt();
  const reduced = useReducedMotionPreference();

  const core = useSharedValue<Core>(newCore());
  const frame = useSharedValue<Frame>(loopFrame(0));
  const flicksSeen = useSharedValue(0);
  const landsSeen = useSharedValue(0);

  const frames = useFrameCallback((info) => {
    let out = frame.get();
    let flicks = 0;
    let lands = 0;
    let secs = 0;
    core.modify((c) => {
      'worklet';
      step(c, info.timestamp, autoPlay);
      out = c.out;
      flicks = c.flicks;
      lands = c.lands;
      secs = c.D;
      return c;
    }, false);
    frame.set(out);
    if (flicks !== flicksSeen.get()) {
      flicksSeen.set(flicks);
      scheduleOnRN(onFlick);
    }
    if (lands !== landsSeen.get()) {
      landsSeen.set(lands);
      scheduleOnRN(onLand, Math.round(secs * 10) / 10);
    }
  }, false);
  const ready = art !== null;
  useEffect(() => frames.setActive(ready), [frames, ready]);

  useEffect(() => {
    scheduleOnUI(() => {
      'worklet';
      core.modify((c) => {
        'worklet';
        setStill(c, reduced);
        return c;
      }, false);
    });
  }, [core, reduced]);

  const ox = (size.w - DESIGN.w) / 2;
  const sy = size.h / DESIGN.h;
  const layout = useMemo<Layout>(() => ({ ox, sy, w: size.w, h: size.h }), [ox, sy, size.w, size.h]);
  const onLayout = (e: LayoutChangeEvent) => setSize({ w: e.nativeEvent.layout.width, h: e.nativeEvent.layout.height });

  return (
    <View style={styles.screen} onLayout={onLayout}>
      <View style={StyleSheet.absoluteFill} pointerEvents="none" accessibilityElementsHidden importantForAccessibility="no-hide-descendants">
        {art ? <Scene art={art} layout={layout} frame={frame} /> : null}
      </View>
      <Controls core={core} layout={layout} />
    </View>
  );
}

function Scene({ art, layout, frame }: { art: Art; layout: Layout; frame: SharedValue<Frame> }) {
  const { sy, w, h } = layout;
  const uniforms = useDerivedValue(() => {
    const F = frame.get();
    return {
      uTip: F.tip, uA: F.A, uSpin: F.spin, uW: F.W, uGlow: F.glow, uAlpha: F.alpha, uFall: F.fall,
      uOrigin: [IMAGE.x, IMAGE.y * sy], uTrail: F.trail,
    };
  });
  const ui = useDerivedValue(() => {
    const F = frame.get();
    return createPicture((canvas) => drawUi(canvas, F, art.ui, layout));
  });
  return (
    <Canvas style={StyleSheet.absoluteFill} colorSpace="srgb">
      <Rect x={0} y={0} width={w} height={h}>
        <Shader source={TOP} uniforms={uniforms}>
          <ImageShader image={art.top} tx="clamp" ty="clamp" fit="none" sampling={SAMPLING} />
        </Shader>
      </Rect>
      <Picture picture={ui} />
    </Canvas>
  );
}

function Controls({ core, layout }: { core: SharedValue<Core>; layout: Layout }) {
  const { ox, sy } = layout;

  const run = (fn: (c: Core) => void) => scheduleOnUI(() => {
    'worklet';
    core.modify((c) => {
      'worklet';
      fn(c);
      return c;
    }, false);
  });

  // The top: a flick spins it as fast as the finger went; a tap spins it at rest, nudges it mid-spin.
  const flick = Gesture.Pan()
    .minDistance(0)
    .onBegin(() => {
      core.modify((c) => {
        'worklet';
        touchDown(c);
        return c;
      }, false);
    })
    .onEnd((e) => {
      const speed = Math.hypot(e.velocityX, e.velocityY);
      const moved = Math.hypot(e.translationX, e.translationY);
      const isFlick = moved >= FLICK.distance && speed >= FLICK.speed;
      core.modify((c) => {
        'worklet';
        spin(c, isFlick ? speed : 0, true);
        return c;
      }, false);
    })
    .onFinalize(() => {
      core.modify((c) => {
        'worklet';
        touchUp(c);
        return c;
      }, false);
    });

  return (
    <>
      <GestureDetector gesture={flick}>
        <View
          accessible
          accessibilityRole="button"
          accessibilityLabel="Spin the top"
          onAccessibilityTap={() => run((c) => {
            'worklet';
            spin(c, 0, false);
          })}
          style={[styles.area, { left: TOP_AREA.x, top: TOP_AREA.y, width: TOP_AREA.w, height: TOP_AREA.h }]}
        />
      </GestureDetector>
      <Pressable
        onPressIn={() => run((c) => {
          'worklet';
          pillDown(c, true);
        })}
        onPressOut={() => run((c) => {
          'worklet';
          pillDown(c, false);
        })}
        onPress={() => run((c) => {
          'worklet';
          spin(c, 0, false);
        })}
        accessibilityRole="button"
        accessibilityLabel="Flick to spin"
        style={[styles.pill, { left: ox + CX - PILL.w / 2, top: PILL.y * sy - PILL.h / 2, width: PILL.w, height: PILL.h }]}
      />
    </>
  );
}

/** The flick: a light tick, and "Spinning". On Android expo-haptics' impact is a weak raw pulse, so ask the system
 * first. */
function onFlick() {
  const light = () => Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light);
  if (Platform.OS === 'android') void Haptics.performAndroidHapticsAsync(Haptics.AndroidHaptics.Segment_Tick).catch(light).catch(() => {});
  else void light().catch(() => {});
  AccessibilityInfo.announceForAccessibility('Spinning');
}

/** It lands: a soft thud, and how long it stayed up. */
function onLand(secs: number) {
  const soft = () => Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Soft);
  if (Platform.OS === 'android') void Haptics.performAndroidHapticsAsync(Haptics.AndroidHaptics.Long_Press).catch(soft).catch(() => {});
  else void soft().catch(() => {});
  AccessibilityInfo.announceForAccessibility(`Down after ${secs.toFixed(1)} seconds`);
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: '#8E8780' },
  area: { position: 'absolute' },
  pill: { position: 'absolute', borderRadius: 29 },
});
