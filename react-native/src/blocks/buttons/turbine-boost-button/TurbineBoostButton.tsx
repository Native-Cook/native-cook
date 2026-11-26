import * as Haptics from 'expo-haptics';
import { useEffect, useMemo, useState } from 'react';
import type { LayoutChangeEvent } from 'react-native';
import { AccessibilityInfo, Platform, Pressable, StyleSheet, useWindowDimensions, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import type { SharedValue } from 'react-native-reanimated';
import { useAnimatedReaction, useDerivedValue, useFrameCallback, useSharedValue } from 'react-native-reanimated';
import {
  AlphaType, Canvas, ColorType, createPicture, FilterMode, ImageShader, MipmapMode, Picture, Rect, Shader, Skia, useImage,
  useTypeface,
} from '@shopify/react-native-skia';
import type { SkImage } from '@shopify/react-native-skia';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';

import { useReducedMotionPreference } from '@/shared/useReducedMotionPreference';

import type { Layout, Ui } from './tbb-draw';
import { CX, drawUi, PILL, setGlyphs, setLine } from './tbb-draw';
import type { Core, Frame } from './tbb-motion';
import { boostOnce, hold, loopFrame, newCore, release, setStill, step } from './tbb-motion';
import { turbineEffect } from './tbb-shader';

const FAN = require('./assets/fan.webp');
const FONTS = {
  archivo: require('./assets/ArchivoExpanded-Black.ttf'),
  display: require('./assets/InterDisplay-Bold.ttf'),
  medium: require('./assets/Inter-Medium.ttf'),
  semibold: require('./assets/Inter-SemiBold.ttf'),
};
const DESIGN = { w: 393, h: 852 };
/** The fan square's top in the design, and its centre and the nacelle's radius (pt). */
const FAN_TOP = 252.1;
const FAN_C = { x: 194.2, y: 448.6, r: 192 };
/** Samples along the blur arc inside the rotor (the reference's 40; the designer allows 16–24 if the look holds). */
const SAMPLES = 40;
const TURBINE = turbineEffect(SAMPLES);
/**
 * 1 moves the shader's linear sRGB to Display P3, 0 leaves it as it is. The canvas asks for `colorSpace="srgb"`, so
 * Skia neither tags its iOS layer Display P3 nor converts the image, and the system shows the shader's output as sRGB:
 * the shader must not convert it. (The repository's P3 rule is for the default P3 canvas.)
 */
const P3 = 0;
/** The image is magnified (1024 px over 393 pt at 3×), so mipmaps would never be used. */
const SAMPLING = { filter: FilterMode.Linear, mipmap: MipmapMode.None };

type Props = {
  /** Play the 5 s loop until a finger takes over. */
  autoPlay?: boolean;
};

/** The pixels decoded once into a raster image (a lazily decoded one sampled as black on a fresh install, #28). */
function rasterOf(image: SkImage): SkImage {
  const info = { width: image.width(), height: image.height(), colorType: ColorType.RGBA_8888, alphaType: AlphaType.Unpremul };
  const pixels = image.readPixels(0, 0, info);
  if (!(pixels instanceof Uint8Array)) return image;
  return Skia.Image.MakeImage(info, Skia.Data.fromBytes(pixels), info.width * 4) ?? image;
}

type Art = { fan: SkImage; ui: Ui };

function useArt(): Art | null {
  const fan = useImage(FAN);
  const archivo = useTypeface(FONTS.archivo);
  const display = useTypeface(FONTS.display);
  const medium = useTypeface(FONTS.medium);
  const semibold = useTypeface(FONTS.semibold);
  return useMemo(() => {
    if (!fan || !archivo || !display || !medium || !semibold) return null;
    const fonts = {
      eyebrow: Skia.Font(semibold, 12),
      headline: Skia.Font(archivo, 66),
      sub: Skia.Font(medium, 15),
      percent: Skia.Font(display, 40),
      label: Skia.Font(semibold, 17),
    };
    // Unhinted advances, as the reference's Canvas 2D lays them out (hinted ones round each glyph to a whole point).
    for (const font of Object.values(fonts)) {
      font.setLinearMetrics(true);
      // Skia 2.6.2's JSI setSubpixel reads a number (a boolean throws "Value is true, expected a number"); 1 keeps it on.
      font.setSubpixel(1 as unknown as boolean);
    }
    return {
      fan: rasterOf(fan),
      ui: {
        fonts,
        eyebrow: setLine('VOLT · PERFORMANCE MODE', fonts.eyebrow, semibold, 4),
        headline: setLine('BOOST', fonts.headline, archivo, -4),
        spool: setLine('Hold to spool up. Let go to coast.', fonts.sub, medium),
        full: setLine('Full boost. Let go to coast.', fonts.sub, medium),
        rest: setLine('Hold to boost', fonts.label, semibold),
        boosting: setLine('Boosting…', fonts.label, semibold),
        digits: setGlyphs('0123456789%', fonts.percent),
      },
    };
  }, [fan, archivo, display, medium, semibold]);
}

export function TurbineBoostButton({ autoPlay = false }: Props) {
  const window = useWindowDimensions();
  const [size, setSize] = useState({ w: window.width, h: window.height });
  const art = useArt();
  const reduced = useReducedMotionPreference();

  const core = useSharedValue<Core>(newCore());
  const frame = useSharedValue<Frame>(loopFrame(0));
  const percent = useSharedValue(0);
  const ticksSeen = useSharedValue(0);

  const frames = useFrameCallback((info) => {
    let out = frame.get();
    let ticks = 0;
    let full = false;
    let byHand = false;
    core.modify((c) => {
      'worklet';
      step(c, info.timestamp, autoPlay);
      out = c.out;
      ticks = c.ticks;
      full = c.full;
      byHand = c.mode !== 0;
      c.full = false;
      return c;
    }, false);
    frame.set(out);
    const p = Math.round(100 * out.s);
    if (percent.get() !== p) percent.set(p);
    const crossed = ticks - ticksSeen.get();
    if (crossed !== 0) {
      ticksSeen.set(ticks);
      for (let i = 0; i < Math.min(crossed, 3); i++) scheduleOnRN(onTick);
    }
    if (full && byHand) scheduleOnRN(onFull);
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
      <Controls core={core} percent={percent} layout={layout} />
    </View>
  );
}

function Scene({ art, layout, frame }: { art: Art; layout: Layout; frame: SharedValue<Frame> }) {
  const { ox, sy, w, h } = layout;
  const uniforms = useDerivedValue(() => {
    const F = frame.get();
    return { uA: F.A, uArc: F.arc, uB: F.B, uOff: [ox, FAN_TOP * sy - FAN_TOP], uP3: P3 };
  });
  const ui = useDerivedValue(() => {
    const F = frame.get();
    return createPicture((canvas) => drawUi(canvas, F, art.ui, layout));
  });
  return (
    <Canvas style={StyleSheet.absoluteFill} colorSpace="srgb">
      <Rect x={0} y={0} width={w} height={h}>
        <Shader source={TURBINE} uniforms={uniforms}>
          <ImageShader image={art.fan} tx="clamp" ty="clamp" fit="none" sampling={SAMPLING} />
        </Shader>
      </Rect>
      <Picture picture={ui} />
    </Canvas>
  );
}

function Controls({ core, percent, layout }: { core: SharedValue<Core>; percent: SharedValue<number>; layout: Layout }) {
  const { ox, sy } = layout;
  const [pct, setPct] = useState(0);
  useAnimatedReaction(() => percent.get(), (now, before) => {
    if (now !== before) scheduleOnRN(setPct, now);
  });

  const run = (fn: (c: Core) => void) => scheduleOnUI(() => {
    'worklet';
    core.modify((c) => {
      'worklet';
      fn(c);
      return c;
    }, false);
  });

  // The fan: a hold anywhere on the disc inside the nacelle (the view is its bounding square).
  const fanHeld = useSharedValue(false);
  const fan = Gesture.Pan()
    .minDistance(0)
    .onBegin((e) => {
      const dx = e.x - FAN_C.r;
      const dy = e.y - FAN_C.r;
      if (dx * dx + dy * dy > FAN_C.r * FAN_C.r) return;
      fanHeld.set(true);
      core.modify((c) => {
        'worklet';
        hold(c);
        return c;
      }, false);
    })
    .onFinalize(() => {
      if (!fanHeld.get()) return;
      fanHeld.set(false);
      core.modify((c) => {
        'worklet';
        release(c);
        return c;
      }, false);
    });

  const onPillIn = () => run((c) => {
    'worklet';
    hold(c);
  });
  const onPillOut = () => run((c) => {
    'worklet';
    release(c);
  });
  const onActivate = () => run((c) => {
    'worklet';
    boostOnce(c);
  });

  const top = FAN_TOP * sy + (FAN_C.y - FAN_TOP) - FAN_C.r;
  return (
    <>
      <GestureDetector gesture={fan}>
        <View
          style={[styles.fan, { left: ox + FAN_C.x - FAN_C.r, top, width: FAN_C.r * 2, height: FAN_C.r * 2 }]}
          accessibilityElementsHidden
          importantForAccessibility="no-hide-descendants"
        />
      </GestureDetector>
      <Pressable
        accessibilityRole="button"
        accessibilityLabel="Boost, hold to spool up"
        accessibilityValue={{ text: `${pct} percent` }}
        onPressIn={onPillIn}
        onPressOut={onPillOut}
        onAccessibilityTap={onActivate}
        accessibilityActions={Platform.OS === 'android' ? [{ name: 'activate' }] : undefined}
        onAccessibilityAction={onActivate}
        style={[styles.pill, { left: ox + CX - PILL.w / 2, top: PILL.y * sy - PILL.h / 2, width: PILL.w, height: PILL.h }]}
      />
      <View
        accessible
        accessibilityRole="header"
        accessibilityLabel="Volt performance mode, boost"
        style={[styles.header, { left: ox + CX - 160, top: 70 * sy, height: 145 * sy }]}
      />
    </>
  );
}

/** A quarter of the way to full boost. On Android expo-haptics' impact is a weak raw pulse, so ask the system first. */
function onTick() {
  const light = () => Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light);
  if (Platform.OS === 'android') void Haptics.performAndroidHapticsAsync(Haptics.AndroidHaptics.Segment_Tick).catch(light).catch(() => {});
  else void light().catch(() => {});
}

/** Full boost: success, and the announcement. */
function onFull() {
  const medium = () => Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Medium);
  if (Platform.OS === 'android') void Haptics.performAndroidHapticsAsync(Haptics.AndroidHaptics.Confirm).catch(medium).catch(() => {});
  else void Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success).catch(() => {});
  AccessibilityInfo.announceForAccessibility('Full boost');
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: '#000211' },
  fan: { position: 'absolute', borderRadius: 192 },
  pill: { position: 'absolute', borderRadius: 29 },
  header: { position: 'absolute', width: 320 },
});
