import { Blur, Canvas, Fill, Group, Paint } from '@shopify/react-native-skia';
import { useEffect } from 'react';
import { StyleSheet, View } from 'react-native';
import Animated, {
    cancelAnimation,
    Easing,
    useAnimatedStyle,
    useReducedMotion,
    useSharedValue,
    withRepeat,
    withTiming,
} from 'react-native-reanimated';

import {
    COLLECTION_CARD_ART_HEIGHT,
    COLLECTION_CARD_ART_WIDTH,
    CollectionCardArtwork,
} from './collection-card-back';
import { RisingParticles } from './rising-particles';
import { COLLECTION_CARD_BACKDROP } from '../theme/card-template';

type CollectionCardBackdropProps = {
  height: number;
  width: number;
};

const ROTATION_DURATION = 38_000;
const ENTRY_ANIMATION_DURATION = 2_600;
const START_ANGLE = 0;
const ENTRY_SCALE = 1.5;
const RESTING_SCALE = 1;
const BACKDROP_BLUR = 16;
const ENTRY_EASING = Easing.bezier(0.23, 1, 0.32, 1);

export function CollectionCardBackdrop({ height, width }: CollectionCardBackdropProps) {
  const reduceMotion = useReducedMotion();
  const rotation = useSharedValue(START_ANGLE);
  const zoom = useSharedValue(reduceMotion ? RESTING_SCALE : ENTRY_SCALE);
  const planeSize = Math.ceil(Math.hypot(width, height) * 1.08);
  const artSize = Math.max(width * 1.55, height * 0.82);
  const artOffset = (planeSize - artSize) / 2;
  const scaleX = artSize / COLLECTION_CARD_ART_WIDTH;
  const scaleY = artSize / COLLECTION_CARD_ART_HEIGHT;

  useEffect(() => {
    cancelAnimation(rotation);
    cancelAnimation(zoom);
    rotation.set(START_ANGLE);
    zoom.set(reduceMotion ? RESTING_SCALE : ENTRY_SCALE);

    if (!reduceMotion) {
      rotation.set(
        withRepeat(
          withTiming(START_ANGLE - 360, {
            duration: ROTATION_DURATION,
            easing: Easing.linear,
          }),
          -1,
          false
        )
      );
      zoom.set(
        withTiming(RESTING_SCALE, {
          duration: ENTRY_ANIMATION_DURATION,
          easing: ENTRY_EASING,
        })
      );
    }

    return () => {
      cancelAnimation(rotation);
      cancelAnimation(zoom);
    };
  }, [reduceMotion, rotation, zoom]);

  const animatedStyle = useAnimatedStyle(() => ({
    transform: [{ rotateZ: `${rotation.get()}deg` }, { scale: zoom.get() }],
  }));

  return (
    <View style={styles.container}>
      <Animated.View
        style={[
          styles.plane,
          {
            height: planeSize,
            left: (width - planeSize) / 2,
            top: (height - planeSize) / 2,
            width: planeSize,
          },
          animatedStyle,
        ]}
      >
        <Canvas style={StyleSheet.absoluteFill}>
          <Fill color={COLLECTION_CARD_BACKDROP.background} />
          <Group
            layer={
              <Paint>
                <Blur blur={BACKDROP_BLUR} mode="clamp" />
              </Paint>
            }
          >
            <Group transform={[{ translateX: artOffset }, { translateY: artOffset }]}>
              <Group transform={[{ scaleX }, { scaleY }]}>
                <CollectionCardArtwork
                  backgroundColor={COLLECTION_CARD_BACKDROP.background}
                  patternColor={COLLECTION_CARD_BACKDROP.pattern}
                  sealColor={COLLECTION_CARD_BACKDROP.seal}
                  showCollectorMark={false}
                />
              </Group>
            </Group>
          </Group>
        </Canvas>
      </Animated.View>
      <RisingParticles height={height} width={width} />
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    backgroundColor: COLLECTION_CARD_BACKDROP.background,
    bottom: 0,
    left: 0,
    overflow: 'hidden',
    pointerEvents: 'none',
    position: 'absolute',
    right: 0,
    top: 0,
  },
  plane: {
    position: 'absolute',
  },
});
