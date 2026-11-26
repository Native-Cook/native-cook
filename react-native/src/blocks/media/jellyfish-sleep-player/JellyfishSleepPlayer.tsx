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

import type { Layout, Ui } from './jsp-draw';
import { drawUi, PLAY, setLine, TRACKS } from './jsp-draw';
import type { Core, Frame } from './jsp-motion';
import { changeTrack, loopFrame, newCore, setStill, step, takeFeedback, togglePlay, touchDown, touchUp } from './jsp-motion';
import { JELLY } from './jsp-shader';

const JELLYFISH = require('./assets/jellyfish.webp');
const FONTS = {
  serif: require('./assets/InstrumentSerif-Regular.ttf'),
  display: require('./assets/InterDisplay-Bold.ttf'),
  medium: require('./assets/Inter-Medium.ttf'),
  semibold: require('./assets/Inter-SemiBold.ttf'),
};
const DESIGN = { w: 393, h: 852 };
/** The photo's top-left in the design (pt): it keeps its size, centred across, its top at 34 × height / 852. */
const IMAGE = { x: -0.41, y: 34 };
/** The image is magnified (708 px over 392.7 pt at 3×), so mipmaps would never be used. */
const SAMPLING = { filter: FilterMode.Linear, mipmap: MipmapMode.None };
/** Swipe area between the headline and the track title (design y), and how far a swipe must go. */
const SWIPE = { top: 190, bottom: 670, min: 40 };

type Props = {
  /** Play the 8 s loop until a finger takes over. */
  autoPlay?: boolean;
};

/** The pixels decoded once into a raster image (a lazily decoded one sampled as black on a fresh install, #28). */
function rasterOf(image: SkImage): SkImage {
  const info = { width: image.width(), height: image.height(), colorType: ColorType.RGBA_8888, alphaType: AlphaType.Unpremul };
  const pixels = image.readPixels(0, 0, info);
  if (!(pixels instanceof Uint8Array)) return image;
  return Skia.Image.MakeImage(info, Skia.Data.fromBytes(pixels), info.width * 4) ?? image;
}

type Art = { jelly: SkImage; ui: Ui };

function useArt(): Art | null {
  const jelly = useImage(JELLYFISH);
  const serif = useTypeface(FONTS.serif);
  const display = useTypeface(FONTS.display);
  const medium = useTypeface(FONTS.medium);
  const semibold = useTypeface(FONTS.semibold);
  return useMemo(() => {
    if (!jelly || !serif || !display || !medium || !semibold) return null;
    const fonts = {
      eyebrow: Skia.Font(semibold, 12),
      headline: Skia.Font(serif, 86),
      title: Skia.Font(display, 26),
      meta: Skia.Font(medium, 15),
      time: Skia.Font(medium, 12),
    };
    // Unhinted advances, as the reference's Canvas 2D lays them out (hinted ones round each glyph to a whole point).
    for (const font of Object.values(fonts)) {
      font.setLinearMetrics(true);
      // Skia 2.6.2's JSI setSubpixel reads a number (a boolean throws "Value is true, expected a number"); 1 keeps it on.
      font.setSubpixel(1 as unknown as boolean);
    }
    return {
      jelly: rasterOf(jelly),
      ui: {
        fonts,
        eyebrow: setLine('DRIFT · SLEEP SOUNDS', fonts.eyebrow, semibold, 4),
        headline: setLine('Drift off.', fonts.headline, serif, -2),
        tracks: TRACKS.map((t) => ({ title: setLine(t.name, fonts.title, display, -0.5), meta: setLine(t.meta, fonts.meta, medium) })),
        start: setLine('16:02', fonts.time, medium),
        end: setLine('42:00', fonts.time, medium),
      },
    };
  }, [jelly, serif, display, medium, semibold]);
}

export function JellyfishSleepPlayer({ autoPlay = false }: Props) {
  const window = useWindowDimensions();
  const [size, setSize] = useState({ w: window.width, h: window.height });
  const art = useArt();
  const reduced = useReducedMotionPreference();

  const core = useSharedValue<Core>(newCore());
  const frame = useSharedValue<Frame>(loopFrame(0));
  /** The track shown (the one coming in once a change passes its middle) and whether it plays, for accessibility. */
  const shown = useSharedValue(0);
  const playing = useSharedValue(1);

  const frames = useFrameCallback((info) => {
    let out = frame.get();
    let fed: number[] = [];
    core.modify((c) => {
      'worklet';
      step(c, info.timestamp, autoPlay);
      out = c.out;
      fed = takeFeedback(c);
      return c;
    }, false);
    frame.set(out);
    const now = out.k > 0.5 ? out.b : out.a;
    if (shown.get() !== now) shown.set(now);
    if (playing.get() !== out.playing) playing.set(out.playing);
    // One tick and one announcement for every accepted change, in order, also when several came before this frame.
    for (let i = 0; i < fed.length; i++) scheduleOnRN(onChange, fed[i]);
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
      <Controls core={core} shown={shown} playing={playing} layout={layout} />
    </View>
  );
}

function Scene({ art, layout, frame }: { art: Art; layout: Layout; frame: SharedValue<Frame> }) {
  const { ox, sy, w, h } = layout;
  const uniforms = useDerivedValue(() => {
    const F = frame.get();
    return {
      uT: F.T, uM: F.M, uC: F.C, uH1: F.a, uH2: F.b, uK: F.k, uWave: F.wave, uLit: F.lit,
      uOrigin: [ox + IMAGE.x, IMAGE.y * sy], uOff: [ox, 0],
    };
  });
  const ui = useDerivedValue(() => {
    const F = frame.get();
    return createPicture((canvas) => drawUi(canvas, F, art.ui, layout));
  });
  return (
    <Canvas style={StyleSheet.absoluteFill} colorSpace="srgb">
      <Rect x={0} y={0} width={w} height={h}>
        <Shader source={JELLY} uniforms={uniforms}>
          <ImageShader image={art.jelly} tx="clamp" ty="clamp" fit="none" sampling={SAMPLING} />
        </Shader>
      </Rect>
      <Picture picture={ui} />
    </Canvas>
  );
}

function Controls({ core, shown, playing, layout }: {
  core: SharedValue<Core>; shown: SharedValue<number>; playing: SharedValue<number>; layout: Layout;
}) {
  const { ox, sy, w } = layout;
  const [track, setTrack] = useState(0);
  const [isPlaying, setPlaying] = useState(true);
  useAnimatedReaction(() => shown.get(), (now, before) => {
    if (now !== before) scheduleOnRN(setTrack, now);
  });
  useAnimatedReaction(() => playing.get(), (now, before) => {
    if (now !== before) scheduleOnRN(setPlaying, now > 0);
  });

  const run = (fn: (c: Core) => void) => scheduleOnUI(() => {
    'worklet';
    core.modify((c) => {
      'worklet';
      fn(c);
      return c;
    }, false);
  });

  // A swipe on the jellyfish: left for the next track, right for the previous.
  const swipe = Gesture.Pan()
    .minDistance(0)
    .onBegin(() => {
      core.modify((c) => {
        'worklet';
        touchDown(c);
        return c;
      }, false);
    })
    .onEnd((e) => {
      const far = Math.abs(e.translationX) >= SWIPE.min || Math.abs(e.velocityX) > 500;
      if (!far || Math.abs(e.translationX) < Math.abs(e.translationY)) return;
      const dir = (Math.abs(e.translationX) >= SWIPE.min ? e.translationX : e.velocityX) < 0 ? 1 : -1;
      core.modify((c) => {
        'worklet';
        changeTrack(c, dir);
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

  const button = (fn: (c: Core) => void) => ({
    onPressIn: () => run((c) => {
      'worklet';
      touchDown(c);
    }),
    onPress: () => run(fn),
    onPressOut: () => run((c) => {
      'worklet';
      touchUp(c);
    }),
  });

  const top = PLAY.y * sy;
  return (
    <>
      <GestureDetector gesture={swipe}>
        <View style={[styles.area, { top: SWIPE.top * sy, width: w, height: (SWIPE.bottom - SWIPE.top) * sy }]} />
      </GestureDetector>
      <View
        accessible
        accessibilityRole="header"
        accessibilityLabel={TRACKS[track].name}
        accessibilityHint={TRACKS[track].meta}
        style={[styles.title, { left: ox + 32, top: 676 * sy, height: 56 * sy }]}
      />
      <Pressable
        {...button((c) => {
          'worklet';
          changeTrack(c, -1);
        })}
        accessibilityRole="button"
        accessibilityLabel="Previous track"
        style={[styles.key, { left: ox + 76, top: top + 4, width: 48, height: 48 }]}
      />
      <Pressable
        {...button((c) => {
          'worklet';
          togglePlay(c);
        })}
        accessibilityRole="button"
        accessibilityLabel={isPlaying ? 'Pause' : 'Play'}
        style={[styles.key, { left: ox + PLAY.x, top, width: PLAY.w, height: PLAY.h, borderRadius: 28 }]}
      />
      <Pressable
        {...button((c) => {
          'worklet';
          changeTrack(c, 1);
        })}
        accessibilityRole="button"
        accessibilityLabel="Next track"
        style={[styles.key, { left: ox + 269, top: top + 4, width: 48, height: 48 }]}
      />
    </>
  );
}

/** A track change by touch: a light tick, and the new track's title. On Android expo-haptics' impact is a weak raw
 * pulse, so ask the system for its tick first. */
function onChange(track: number) {
  const light = () => Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light);
  if (Platform.OS === 'android') void Haptics.performAndroidHapticsAsync(Haptics.AndroidHaptics.Segment_Tick).catch(light).catch(() => {});
  else void light().catch(() => {});
  AccessibilityInfo.announceForAccessibility(TRACKS[track].name);
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: '#020C1C' },
  area: { position: 'absolute', left: 0 },
  title: { position: 'absolute', width: 300 },
  key: { position: 'absolute' },
});

