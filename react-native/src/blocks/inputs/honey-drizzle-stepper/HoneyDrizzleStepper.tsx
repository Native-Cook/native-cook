import * as Haptics from 'expo-haptics';
import { useEffect, useMemo, useState } from 'react';
import type { LayoutChangeEvent } from 'react-native';
import { Platform, Pressable, StyleSheet, useWindowDimensions, View } from 'react-native';
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

import type { Layout, Ui } from './hds-draw';
import { drawUi, SAUCER_TOP, setLine, STEPPER } from './hds-draw';
import type { Core, Frame } from './hds-motion';
import { dragEnd, dragStart, dragTo, loopFrame, newCore, setStill, step, stepBy, touchDown, touchUp } from './hds-motion';
import { HONEY } from './hds-shader';

const HONEY_IMAGE = require('./assets/honey.webp');
const FONTS = {
  fraunces: require('./assets/Fraunces-SemiBold.ttf'),
  anton: require('./assets/Anton-Regular.ttf'),
  medium: require('./assets/Inter-Medium.ttf'),
  semibold: require('./assets/Inter-SemiBold.ttf'),
};
const DESIGN_H = 852;
/** The photo's top-left in the design (pt); it keeps its size and x, and its top moves with the height. */
const IMAGE = { x: -12, y: 10 };
/** The image is magnified (1024 px over 520 pt at 3×), so mipmaps would never be used. */
const SAMPLING = { filter: FilterMode.Linear, mipmap: MipmapMode.None };

type Props = {
  /** Play the 6 s loop until a finger takes over. */
  autoPlay?: boolean;
};

/** The pixels decoded once into a raster image (a lazily decoded one sampled as black on a fresh install, #28). */
function rasterOf(image: SkImage): SkImage {
  const info = { width: image.width(), height: image.height(), colorType: ColorType.RGBA_8888, alphaType: AlphaType.Unpremul };
  const pixels = image.readPixels(0, 0, info);
  if (!(pixels instanceof Uint8Array)) return image;
  return Skia.Image.MakeImage(info, Skia.Data.fromBytes(pixels), info.width * 4) ?? image;
}

type Art = { honey: SkImage; ui: Ui };

function useArt(): Art | null {
  const honey = useImage(HONEY_IMAGE);
  const fraunces = useTypeface(FONTS.fraunces);
  const anton = useTypeface(FONTS.anton);
  const medium = useTypeface(FONTS.medium);
  const semibold = useTypeface(FONTS.semibold);
  return useMemo(() => {
    if (!honey || !fraunces || !anton || !medium || !semibold) return null;
    const fonts = {
      eyebrow: Skia.Font(semibold, 12),
      headline: Skia.Font(fraunces, 56),
      number: Skia.Font(anton, 128),
      hint: Skia.Font(medium, 14),
      unit: Skia.Font(semibold, 20),
      sign: Skia.Font(semibold, 28),
    };
    // Unhinted advances, as the reference's Canvas 2D lays them out (hinted ones round each glyph to a whole point).
    for (const font of Object.values(fonts)) {
      font.setLinearMetrics(true);
      // Skia 2.6.2's JSI setSubpixel reads a number (a boolean throws "Value is true, expected a number"); 1 keeps it on.
      font.setSubpixel(1 as unknown as boolean);
    }
    return {
      honey: rasterOf(honey),
      ui: {
        fonts,
        eyebrow: setLine('BUZZ · BREAKFAST', fonts.eyebrow, semibold, 4),
        how: setLine('How', fonts.headline, fraunces, -2),
        sweet: setLine('sweet?', fonts.headline, fraunces, -2),
        hint: setLine('Drag the dipper up for more.', fonts.hint, medium),
        unit: setLine('tsp honey', fonts.unit, semibold),
        minus: setLine('−', fonts.sign, semibold),
        plus: setLine('+', fonts.sign, semibold),
        digits: fonts.number.getGlyphIDs('0123456789'),
      },
    };
  }, [honey, fraunces, anton, medium, semibold]);
}

export function HoneyDrizzleStepper({ autoPlay = false }: Props) {
  const window = useWindowDimensions();
  const [size, setSize] = useState({ w: window.width, h: window.height });
  const art = useArt();
  const reduced = useReducedMotionPreference();

  const core = useSharedValue<Core>(newCore());
  const frame = useSharedValue<Frame>(loopFrame(0));
  const shown = useSharedValue(2);
  const ticksSeen = useSharedValue(0);

  const frames = useFrameCallback((info) => {
    let out = frame.get();
    let ticks = 0;
    core.modify((c) => {
      'worklet';
      step(c, info.timestamp, autoPlay);
      out = c.out;
      ticks = c.ticks;
      return c;
    }, false);
    frame.set(out);
    if (shown.get() !== out.n) shown.set(out.n);
    // One tick per whole tsp passed, also when a fast finger passes two between frames.
    const passed = ticks - ticksSeen.get();
    if (passed !== 0) {
      ticksSeen.set(ticks);
      for (let i = 0; i < Math.min(passed, 4); i++) scheduleOnRN(onTick);
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
      <Controls core={core} shown={shown} layout={layout} />
    </View>
  );
}

function Scene({ art, layout, frame }: { art: Art; layout: Layout; frame: SharedValue<Frame> }) {
  const { sy, w, h } = layout;
  const uniforms = useDerivedValue(() => {
    const F = frame.get();
    return { uAmt: F.amt, uT: F.T, uStill: F.still, uOrigin: [IMAGE.x, IMAGE.y * sy] };
  });
  const ui = useDerivedValue(() => {
    const F = frame.get();
    return createPicture((canvas) => drawUi(canvas, F, art.ui, layout));
  });
  return (
    <Canvas style={StyleSheet.absoluteFill} colorSpace="srgb">
      <Rect x={0} y={0} width={w} height={h}>
        <Shader source={HONEY} uniforms={uniforms}>
          <ImageShader image={art.honey} tx="clamp" ty="clamp" fit="none" sampling={SAMPLING} />
        </Shader>
      </Rect>
      <Picture picture={ui} />
    </Canvas>
  );
}

function Controls({ core, shown, layout }: { core: SharedValue<Core>; shown: SharedValue<number>; layout: Layout }) {
  const { sy, w } = layout;
  const [tsp, setTsp] = useState(2);
  useAnimatedReaction(() => shown.get(), (now, before) => {
    if (now !== before) scheduleOnRN(setTsp, now);
  });

  const run = (fn: (c: Core) => void) => scheduleOnUI(() => {
    'worklet';
    core.modify((c) => {
      'worklet';
      fn(c);
      return c;
    }, false);
  });

  // The dipper: drag anywhere above the saucer, up for more.
  const drag = Gesture.Pan()
    .minDistance(0)
    .onBegin(() => {
      core.modify((c) => {
        'worklet';
        touchDown(c);
        dragStart(c);
        return c;
      }, false);
    })
    .onUpdate((e) => {
      core.modify((c) => {
        'worklet';
        dragTo(c, e.translationY);
        return c;
      }, false);
    })
    .onFinalize(() => {
      core.modify((c) => {
        'worklet';
        dragEnd(c);
        touchUp(c);
        return c;
      }, false);
    });

  const stepper = (dir: number) => ({
    onPressIn: () => run((c) => {
      'worklet';
      touchDown(c);
      stepBy(c, dir);
    }),
    onPressOut: () => run((c) => {
      'worklet';
      touchUp(c);
    }),
  });
  const onAction = (name: string) => {
    const dir = name === 'increment' ? 1 : name === 'decrement' ? -1 : 0;
    if (dir !== 0) run((c) => {
      'worklet';
      stepBy(c, dir);
    });
  };

  const top = STEPPER.y * sy;
  const half = STEPPER.w / 2;
  return (
    <>
      <GestureDetector gesture={drag}>
        <View
          accessible
          accessibilityRole="adjustable"
          accessibilityLabel="Honey"
          accessibilityValue={{ min: 1, max: 5, now: tsp, text: `${tsp} ${tsp === 1 ? 'teaspoon' : 'teaspoons'}` }}
          accessibilityActions={[{ name: 'increment' }, { name: 'decrement' }]}
          onAccessibilityAction={(e) => onAction(e.nativeEvent.actionName)}
          style={[styles.area, { width: w, height: SAUCER_TOP * sy }]}
        />
      </GestureDetector>
      <Pressable
        {...stepper(-1)}
        accessibilityElementsHidden
        importantForAccessibility="no"
        style={[styles.step, { left: STEPPER.x, top, width: half, height: STEPPER.h }]}
      />
      <Pressable
        {...stepper(1)}
        accessibilityElementsHidden
        importantForAccessibility="no"
        style={[styles.step, { left: STEPPER.x + half, top, width: half, height: STEPPER.h }]}
      />
    </>
  );
}

/** A whole tsp. On Android expo-haptics' impact is a weak raw pulse, so ask the system for its tick first. */
function onTick() {
  const light = () => Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light);
  if (Platform.OS === 'android') void Haptics.performAndroidHapticsAsync(Haptics.AndroidHaptics.Segment_Tick).catch(light).catch(() => {});
  else void light().catch(() => {});
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: '#F26A1B' },
  area: { position: 'absolute', left: 0, top: 0 },
  step: { position: 'absolute' },
});
