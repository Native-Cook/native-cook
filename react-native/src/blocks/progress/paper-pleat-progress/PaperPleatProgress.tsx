import * as Haptics from 'expo-haptics';
import { useEffect, useMemo, useState } from 'react';
import type { LayoutChangeEvent } from 'react-native';
import { AccessibilityInfo, Platform, StyleSheet, useWindowDimensions, View } from 'react-native';
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

import type { Layout, Ui } from './ppp-draw';
import { drawUi, SECONDS, setLine } from './ppp-draw';
import type { Core, Frame } from './ppp-motion';
import { loopFrame, newCore, setStill, step, touchDown, touchUp } from './ppp-motion';
import { PLEAT } from './ppp-shader';

const IMAGES = {
  photo: require('./assets/pleat.webp'),
  plate: require('./assets/pleat-plate.webp'),
  mask: require('./assets/pleat-mask.webp'),
};
const FONTS = {
  serif: require('./assets/InstrumentSerif-Regular.ttf'),
  display: require('./assets/InterDisplay-Bold.ttf'),
  medium: require('./assets/Inter-Medium.ttf'),
};
const DESIGN_H = 852;
/** The picture's top-left (pt): it keeps its size and x, its top at 150 × height / 852; 393 × 589.5 pt. */
const PICTURE = { x: 0, y: 150, w: 393, h: 589.5 };
/** The full-size images are magnified (1024 px over 393 pt at 3×); the blurred levels are made at load instead. */
const SAMPLING = { filter: FilterMode.Linear, mipmap: MipmapMode.None };

type Props = {
  /** Play the 7 s loop; a touch pauses it. */
  autoPlay?: boolean;
};

/** The pixels decoded once into a raster image (a lazily decoded one sampled as black on a fresh install, #28). */
function rasterOf(image: SkImage): SkImage {
  const info = { width: image.width(), height: image.height(), colorType: ColorType.RGBA_8888, alphaType: AlphaType.Unpremul };
  const pixels = image.readPixels(0, 0, info);
  if (!(pixels instanceof Uint8Array)) return image;
  return Skia.Image.MakeImage(info, Skia.Data.fromBytes(pixels), info.width * 4) ?? image;
}

/** Half the size, each pixel the mean of 2 × 2 (bilinear exactly between four texels), as a mip level is built. */
function halve(image: SkImage): SkImage {
  const w = Math.max(1, image.width() >> 1);
  const h = Math.max(1, image.height() >> 1);
  const surface = Skia.Surface.Make(w, h);
  if (!surface) return image;
  surface.getCanvas().drawImageRectOptions(
    image, Skia.XYWHRect(0, 0, image.width(), image.height()), Skia.XYWHRect(0, 0, w, h), FilterMode.Linear, MipmapMode.None,
  );
  surface.flush();
  return rasterOf(surface.makeImageSnapshot());
}

/** The mip level `level` of an image (level 0 is the image). */
function levelOf(image: SkImage, level: number): SkImage {
  let out = image;
  for (let i = 0; i < level; i++) out = halve(out);
  return out;
}

type Art = {
  photo: SkImage; plate: SkImage; mask: SkImage; photo4: SkImage; plate5: SkImage; plate6: SkImage; mask5: SkImage; ui: Ui;
};

function useArt(): Art | null {
  const photo = useImage(IMAGES.photo);
  const plate = useImage(IMAGES.plate);
  const mask = useImage(IMAGES.mask);
  const serif = useTypeface(FONTS.serif);
  const display = useTypeface(FONTS.display);
  const medium = useTypeface(FONTS.medium);
  return useMemo(() => {
    if (!photo || !plate || !mask || !serif || !display || !medium) return null;
    const fonts = { title: Skia.Font(serif, 60), percent: Skia.Font(display, 64), meta: Skia.Font(medium, 15) };
    // Unhinted advances, as the reference's Canvas 2D lays them out (hinted ones round each glyph to a whole point).
    for (const font of Object.values(fonts)) {
      font.setLinearMetrics(true);
      // Skia 2.6.2's JSI setSubpixel reads a number (a boolean throws "Value is true, expected a number"); 1 keeps it on.
      font.setSubpixel(1 as unknown as boolean);
    }
    const p = rasterOf(photo);
    const c = rasterOf(plate);
    const m = rasterOf(mask);
    const plate5 = levelOf(c, 5);
    return {
      photo: p,
      plate: c,
      mask: m,
      photo4: levelOf(p, 4),
      plate5,
      plate6: halve(plate5),
      mask5: levelOf(m, 5),
      ui: {
        fonts,
        folding: setLine('Folding your', fonts.title, serif, -1.5),
        exportLine: setLine('export', fonts.title, serif, -1.5),
        exported: setLine('Exported.', fonts.title, serif, -1.5),
        percent: Array.from({ length: 101 }, (_, i) => setLine(`${i}%`, fonts.percent, display, -2)),
        left: Array.from({ length: SECONDS + 1 }, (_, n) => setLine(`Annual report.pdf · 18 MB · ${n} s left`, fonts.meta, medium)),
        saved: setLine('Annual report.pdf · 18 MB · saved to Files', fonts.meta, medium),
      },
    };
  }, [photo, plate, mask, serif, display, medium]);
}

export function PaperPleatProgress({ autoPlay = false }: Props) {
  const window = useWindowDimensions();
  const [size, setSize] = useState({ w: window.width, h: window.height });
  const art = useArt();
  const reduced = useReducedMotionPreference();

  const core = useSharedValue<Core>(newCore());
  const frame = useSharedValue<Frame>(loopFrame(0));
  const percent = useSharedValue(0);
  const donesSeen = useSharedValue(0);

  const frames = useFrameCallback((info) => {
    let out = frame.get();
    let dones = 0;
    core.modify((c) => {
      'worklet';
      step(c, info.timestamp, autoPlay);
      out = c.out;
      dones = c.dones;
      return c;
    }, false);
    frame.set(out);
    const p = Math.round(100 * out.pct);
    if (percent.get() !== p) percent.set(p);
    if (dones !== donesSeen.get()) {
      donesSeen.set(dones);
      scheduleOnRN(onDone);
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

  const sy = size.h / DESIGN_H;
  const layout = useMemo<Layout>(() => ({ sy, w: size.w, h: size.h }), [sy, size.w, size.h]);
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
  const { sy, w, h } = layout;
  const uniforms = useDerivedValue(() => {
    const F = frame.get();
    return {
      uP: F.P, uSw: F.sw, uT: F.T, uBoost: F.boost, uFade: F.fade, uLift: F.lift, uOrigin: [PICTURE.x, PICTURE.y * sy],
    };
  });
  const ui = useDerivedValue(() => {
    const F = frame.get();
    return createPicture((canvas) => drawUi(canvas, F, art.ui, layout));
  });
  return (
    <Canvas style={StyleSheet.absoluteFill} colorSpace="srgb">
      <Rect x={0} y={0} width={w} height={h}>
        <Shader source={PLEAT} uniforms={uniforms}>
          <ImageShader image={art.photo} tx="clamp" ty="clamp" fit="none" sampling={SAMPLING} />
          <ImageShader image={art.plate} tx="clamp" ty="clamp" fit="none" sampling={SAMPLING} />
          <ImageShader image={art.mask} tx="clamp" ty="clamp" fit="none" sampling={SAMPLING} />
          <ImageShader image={art.photo4} tx="clamp" ty="clamp" fit="none" sampling={SAMPLING} />
          <ImageShader image={art.plate5} tx="clamp" ty="clamp" fit="none" sampling={SAMPLING} />
          <ImageShader image={art.plate6} tx="clamp" ty="clamp" fit="none" sampling={SAMPLING} />
          <ImageShader image={art.mask5} tx="clamp" ty="clamp" fit="none" sampling={SAMPLING} />
        </Shader>
      </Rect>
      <Picture picture={ui} />
    </Canvas>
  );
}

function Controls({ core, percent, layout }: { core: SharedValue<Core>; percent: SharedValue<number>; layout: Layout }) {
  const { sy, w, h } = layout;
  const [pct, setPct] = useState(0);
  useAnimatedReaction(() => percent.get(), (now, before) => {
    if (now !== before) scheduleOnRN(setPct, now);
  });

  // Any touch pauses the export; on the sculpture it also pulses the front catch while it opens.
  const top = PICTURE.y * sy;
  const hold = Gesture.Pan()
    .minDistance(0)
    .onBegin((e) => {
      const onSculpture = e.y >= top && e.y <= top + PICTURE.h;
      core.modify((c) => {
        'worklet';
        touchDown(c, onSculpture);
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
    <GestureDetector gesture={hold}>
      <View style={[styles.area, { width: w, height: h }]} collapsable={false}>
        <View
          accessible
          accessibilityRole="progressbar"
          accessibilityLabel={pct >= 100 ? 'Exported' : `Exporting, ${pct} percent`}
          accessibilityValue={{ min: 0, max: 100, now: pct }}
          style={[styles.progress, { top: 690 * sy, height: 130 * sy }]}
        />
      </View>
    </GestureDetector>
  );
}

/** 100 % while someone watches: a light haptic and the announcement. On Android expo-haptics' impact is a weak raw
 * pulse, so ask the system first. */
function onDone() {
  const light = () => Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light);
  if (Platform.OS === 'android') void Haptics.performAndroidHapticsAsync(Haptics.AndroidHaptics.Confirm).catch(light).catch(() => {});
  else void light().catch(() => {});
  AccessibilityInfo.announceForAccessibility('Exported');
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: '#E5D9CF' },
  area: { position: 'absolute', left: 0, top: 0 },
  progress: { position: 'absolute', left: 24, width: 345 },
});
