import { Canvas, Fill, ImageShader, makeImageFromView, Shader, Skia, type SkImage } from '@shopify/react-native-skia';
import { LinearGradient } from 'expo-linear-gradient';
import { type RefObject, useEffect, useRef, useState } from 'react';
import { Image, StyleSheet, Text, View } from 'react-native';
import { GestureDetector, type GestureType } from 'react-native-gesture-handler';
import Animated, { type SharedValue, useAnimatedStyle, useDerivedValue } from 'react-native-reanimated';

import { FOIL_SKSL, foilUniform } from './foil-shader';
import { FOIL_FACE_MIN_OPACITY, type EventPassFrame } from './motion';
import { PosterFoilFallbackSvg, PosterSvg } from './PosterSvg';
import { foilFallback, pass } from './theme';

const QR_CODE = require('./assets/qr-pass.png') as number;

type Props = {
  frame: SharedValue<EventPassFrame>;
  scale: number;
  gesture: GestureType;
  backVisible: boolean;
  onAccessibilityTap: () => void;
};

const FOIL_EFFECT = Skia.RuntimeEffect.Make(FOIL_SKSL);
/** Wait for the poster's text and photo strip to draw before reading it back. */
const CAPTURE_DELAY = 250;

/**
 * Holographic foil over the poster. The artwork is read back once (a snapshot of the poster view)
 * so the shader knows where the ink is; the overlay is transparent while the pass is at rest.
 */
function usePosterFoil(posterRef: RefObject<View | null>, frame: SharedValue<EventPassFrame>, scale: number) {
  const timer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const [image, setImage] = useState<SkImage | null>(null);
  // No shader, or no snapshot to feed it: the static gradient title takes over (never no foil at all).
  const [captureFailed, setCaptureFailed] = useState(!FOIL_EFFECT);
  const size = pass.poster.size * scale;
  useEffect(() => () => {
    if (timer.current) clearTimeout(timer.current);
  }, []);
  const capture = () => {
    if (!FOIL_EFFECT) return;
    if (timer.current) clearTimeout(timer.current);
    timer.current = setTimeout(() => {
      makeImageFromView(posterRef)
        .then((snapshot) => {
          if (snapshot) {
            setImage(snapshot);
            setCaptureFailed(false);
          } else setCaptureFailed(true);
        })
        .catch(() => setCaptureFailed(true));
    }, CAPTURE_DELAY);
  };
  const uniforms = useDerivedValue(() => {
    const current = frame.get();
    const turn = current.quietCrossfade ? 0 : current.flipAngle;
    return { res: [size, size], foil: foilUniform(current.tiltX, current.tiltY, turn, scale) };
  });
  const fallbackStyle = useAnimatedStyle(() => {
    const current = frame.get();
    const turn = current.quietCrossfade ? 0 : current.flipAngle;
    return { opacity: foilUniform(current.tiltX, current.tiltY, turn, scale)[2] * foilFallback.opacity };
  });
  return { capture, image, uniforms, size, fallback: !image && captureFailed, fallbackStyle };
}

export function EventPass({ frame, scale, gesture, backVisible, onAccessibilityTap }: Props) {
  const passStyle = useAnimatedStyle(() => {
    const current = frame.get();
    return {
      transform: [
        { scale: current.pressScale },
      ],
    };
  });
  const frontStyle = useAnimatedStyle(() => {
    const current = frame.get();
    return {
      opacity: Math.max(FOIL_FACE_MIN_OPACITY, current.frontOpacity),
      transform: [
        { perspective: 650 * scale },
        { rotateX: `${current.tiltX}deg` },
        { rotateY: `${current.tiltY + (current.quietCrossfade ? 0 : current.flipAngle)}deg` },
      ],
    };
  });
  const backStyle = useAnimatedStyle(() => {
    const current = frame.get();
    return {
      opacity: current.backOpacity,
      transform: [
        { perspective: 650 * scale },
        { rotateX: `${current.tiltX}deg` },
        { rotateY: `${current.tiltY + (current.quietCrossfade ? 0 : current.flipAngle - 180)}deg` },
      ],
    };
  });
  const posterRef = useRef<View>(null);
  const { capture, image, uniforms, size, fallback, fallbackStyle } = usePosterFoil(posterRef, frame, scale);

  return (
    <GestureDetector gesture={gesture}>
      <Animated.View
        style={[styles.pass, { width: pass.width * scale, height: pass.height * scale }, passStyle]}
        accessible
        accessibilityRole="button"
        accessibilityLabel={backVisible
          ? 'Event pass details. Ticket COMMON-0718. Tap to return to poster.'
          : 'Event poster. Tap to show event pass and QR code.'}
        onAccessibilityTap={onAccessibilityTap}
      >
        <Animated.View
          style={[styles.faceLayer, styles.shadow, frontStyle]}
          accessible={false}
          accessibilityElementsHidden
          importantForAccessibility="no-hide-descendants"
        >
          <View style={[styles.face, { borderRadius: pass.radius * scale, borderWidth: scale }]}>
            <View ref={posterRef} collapsable={false} onLayout={capture} style={{ position: 'absolute', left: pass.poster.left * scale, top: pass.poster.top * scale }}>
              <PosterSvg width={pass.poster.size * scale} height={pass.poster.size * scale} />
            </View>
            {FOIL_EFFECT && image ? (
              <Canvas pointerEvents="none" style={{ position: 'absolute', left: pass.poster.left * scale, top: pass.poster.top * scale, width: size, height: size }}>
                <Fill>
                  <Shader source={FOIL_EFFECT} uniforms={uniforms}>
                    <ImageShader image={image} fit="fill" rect={{ x: 0, y: 0, width: size, height: size }} />
                  </Shader>
                </Fill>
              </Canvas>
            ) : null}
            {fallback ? (
              <Animated.View pointerEvents="none" style={[{ position: 'absolute', left: pass.poster.left * scale, top: pass.poster.top * scale }, fallbackStyle]}>
                <PosterFoilFallbackSvg width={size} height={size} />
              </Animated.View>
            ) : null}
            <Text
              allowFontScaling={false}
              adjustsFontSizeToFit
              numberOfLines={1}
              style={[styles.passTitle, { left: pass.titleLeft * scale, top: pass.titleTop * scale, width: pass.titleWidth * scale, fontSize: pass.titleFontSize * scale, lineHeight: pass.titleLineHeight * scale, letterSpacing: -0.55 * scale }]}
            >
              Paws, People &amp; Purpose
            </Text>
            <Text
              allowFontScaling={false}
              numberOfLines={1}
              ellipsizeMode="clip"
              style={[styles.passDate, { left: pass.dateLeft * scale, top: pass.dateTop * scale, width: pass.dateWidth * scale, fontSize: pass.dateFontSize * scale, lineHeight: pass.dateLineHeight * scale }]}
            >
              Jul 18  <Text style={styles.passPlace}>Los Angeles</Text>
            </Text>
          </View>
        </Animated.View>

        <Animated.View
          style={[styles.faceLayer, styles.shadow, backStyle]}
          accessible={false}
          accessibilityElementsHidden
          importantForAccessibility="no-hide-descendants"
        >
          <View style={[styles.face, styles.back, { borderRadius: pass.radius * scale, borderWidth: scale }]}>
            <LinearGradient colors={['rgba(255,255,255,0.04)', 'rgba(255,255,255,0)']} style={[StyleSheet.absoluteFill, { borderRadius: pass.radius * scale }]} />
            <View style={[styles.backContent, { top: 25 * scale, width: pass.width * scale, gap: 11 * scale }]}>
              <Text allowFontScaling={false} style={[styles.passKind, { fontSize: 10 * scale, lineHeight: 13 * scale, letterSpacing: 1.6 * scale }]}>COMMUNITY PASS</Text>
              <View style={[styles.qrWell, { padding: 9 * scale, borderRadius: 13 * scale }]}>
                <Image source={QR_CODE} resizeMode="contain" style={{ width: 166 * scale, height: 166 * scale }} />
              </View>
              <Text allowFontScaling={false} style={[styles.backTitle, { fontSize: 17 * scale, lineHeight: 21 * scale }]}>Paws, People &amp; Purpose</Text>
              <Text allowFontScaling={false} style={[styles.ticket, { fontSize: 11 * scale, lineHeight: 14 * scale, letterSpacing: scale }]}>COMMON-0718</Text>
              <Text allowFontScaling={false} style={[styles.returnHint, { fontSize: 10 * scale, lineHeight: 13 * scale }]}>↩  Tap to return</Text>
            </View>
          </View>
        </Animated.View>

      </Animated.View>
    </GestureDetector>
  );
}

const styles = StyleSheet.create({
  pass: {
    position: 'relative',
  },
  face: {
    position: 'absolute',
    left: 0,
    right: 0,
    top: 0,
    bottom: 0,
    overflow: 'hidden',
    // Round 3: smoked glass (was white 12 %) so both faces keep ≥ 4.5:1 over the coloured stage.
    backgroundColor: 'rgba(56,58,64,0.40)',
    borderColor: 'rgba(255,255,255,0.18)',
  },
  faceLayer: {
    position: 'absolute',
    left: 0,
    right: 0,
    top: 0,
    bottom: 0,
  },
  shadow: {
    shadowColor: '#000000',
    shadowOpacity: 0.25,
    shadowRadius: 18,
    shadowOffset: { width: 0, height: 12 },
    elevation: 0,
  },
  passTitle: {
    position: 'absolute',
    color: '#FFFFFF',
    fontWeight: '600',
  },
  passDate: {
    position: 'absolute',
    color: 'rgba(255,255,255,0.92)',
    fontWeight: '600',
  },
  passPlace: {
    color: 'rgba(255,255,255,0.62)',
    fontWeight: '500',
  },
  back: {
    alignItems: 'center',
  },
  backContent: {
    position: 'absolute',
    alignItems: 'center',
  },
  passKind: {
    color: 'rgba(255,255,255,0.80)',
    fontWeight: '700',
  },
  qrWell: {
    backgroundColor: '#FAF9F7',
  },
  backTitle: {
    color: '#FFFFFF',
    fontWeight: '600',
    textAlign: 'center',
  },
  ticket: {
    color: 'rgba(255,255,255,0.78)',
    fontWeight: '600',
    fontFamily: 'monospace',
  },
  returnHint: {
    color: 'rgba(255,255,255,0.58)',
    fontWeight: '500',
  },
});
