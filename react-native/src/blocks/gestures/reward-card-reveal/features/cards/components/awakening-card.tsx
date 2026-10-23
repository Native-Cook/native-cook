import {
    Blur,
    Canvas,
    Circle,
    Group,
    LinearGradient,
    Paint,
    RadialGradient,
    Rect,
    SweepGradient,
} from '@shopify/react-native-skia';
import { Image } from 'expo-image';
import { useEffect } from 'react';
import { Platform, StyleSheet, Text, View, type ViewStyle } from 'react-native';
import Animated, {
    type CSSAnimationKeyframes,
    type CSSAnimationProperties,
    Easing,
    cancelAnimation,
    useAnimatedStyle,
    useReducedMotion,
    useSharedValue,
    withDelay,
    withRepeat,
    withTiming,
} from 'react-native-reanimated';
import { scheduleOnRN } from 'react-native-worklets';

import { CollectionCardBack } from './collection-card-back';
import { CARD_HEIGHT_RATIO } from '../theme/card-template';

type AwakeningCardProps = {
  exiting?: boolean;
  onExitReady?: () => void;
  width: number;
};

type ScaledProps = {
  scale: number;
};

const DESIGN_WIDTH = 320;
const CARD_TILT_DEGREES = -2.55;
const CARD_TILT = `${CARD_TILT_DEGREES}deg`;
const ENTRY_DURATION = 600;
const FLIP_HOLD_DURATION = 100;
const FLIP_DELAY = ENTRY_DURATION + FLIP_HOLD_DURATION;
const FLIP_DURATION = 1300;
const SETTLE_DURATION = 650;
const PLANE_MOTION_DURATION = FLIP_DURATION + SETTLE_DURATION;
const SWAY_DELAY = FLIP_DELAY + FLIP_DURATION;
const SWAY_DURATION = 9000;
const SWAY_ANGLE_DEGREES = 3.3;
const SWAY_PITCH_DEGREES = 4.2;
const SWAY_YAW_DEGREES = 6.4;
const SWAY_PERSPECTIVE = 900;
const SWAY_HORIZONTAL_RATIO = 0.024;
const SWAY_VERTICAL_RATIO = 0.032;
const EXIT_SETTLE_DURATION = 500;
const EXIT_TRANSLATE_Y = 2;
const EXIT_SCALE = 0.985;
const EXIT_EASING = Easing.bezier(0.23, 1, 0.32, 1);
const CORNER_EMBLEM_GLYPHS = Array.from('mymind·mymind·mymind·');

const SERIF_FONT = Platform.select({
  android: 'serif',
  default: 'Georgia',
  web: 'Georgia, serif',
});

const CARD_ENTRY_KEYFRAMES = {
  '0%': {
    opacity: 0,
    transform: [{ scale: 1.4 }, { rotateZ: CARD_TILT }],
  },
  '20%': {
    opacity: 0.24,
    transform: [{ scale: 1.31 }, { rotateZ: CARD_TILT }],
  },
  '48%': {
    opacity: 0.58,
    transform: [{ scale: 1.16 }, { rotateZ: CARD_TILT }],
  },
  '72%': {
    opacity: 0.75,
    transform: [{ scale: 1.07 }, { rotateZ: CARD_TILT }],
  },
  '86%': {
    opacity: 0.88,
    transform: [{ scale: 1.03 }, { rotateZ: CARD_TILT }],
  },
  '100%': {
    opacity: 1,
    transform: [{ scale: 1 }, { rotateZ: CARD_TILT }],
  },
} satisfies CSSAnimationKeyframes<ViewStyle>;

const CARD_BACK_FLIP_KEYFRAMES = {
  '0%': {
    transform: [{ perspective: 900 }, { rotateY: '0deg' }],
  },
  '33%': {
    transform: [{ perspective: 900 }, { rotateY: '60deg' }],
  },
  '44%': {
    transform: [{ perspective: 900 }, { rotateY: '88deg' }],
  },
  '50%': {
    transform: [{ perspective: 900 }, { rotateY: '114deg' }],
  },
  '60%': {
    transform: [{ perspective: 900 }, { rotateY: '138deg' }],
  },
  '67%': {
    transform: [{ perspective: 900 }, { rotateY: '150deg' }],
  },
  '82%': {
    transform: [{ perspective: 900 }, { rotateY: '174deg' }],
  },
  '100%': {
    transform: [{ perspective: 900 }, { rotateY: '180deg' }],
  },
} satisfies CSSAnimationKeyframes<ViewStyle>;

const CARD_FRONT_FLIP_KEYFRAMES = {
  '0%': {
    transform: [{ perspective: 900 }, { rotateY: '-180deg' }],
  },
  '33%': {
    transform: [{ perspective: 900 }, { rotateY: '-120deg' }],
  },
  '44%': {
    transform: [{ perspective: 900 }, { rotateY: '-92deg' }],
  },
  '50%': {
    transform: [{ perspective: 900 }, { rotateY: '-66deg' }],
  },
  '60%': {
    transform: [{ perspective: 900 }, { rotateY: '-42deg' }],
  },
  '67%': {
    transform: [{ perspective: 900 }, { rotateY: '-30deg' }],
  },
  '82%': {
    transform: [{ perspective: 900 }, { rotateY: '-6deg' }],
  },
  '100%': {
    transform: [{ perspective: 900 }, { rotateY: '0deg' }],
  },
} satisfies CSSAnimationKeyframes<ViewStyle>;

const CARD_PLANE_MOTION_KEYFRAMES = {
  '0%': {
    transform: [{ translateY: 0 }, { scale: 1 }],
  },
  '55%': {
    transform: [{ translateY: 0 }, { scale: 1 }],
  },
  '72%': {
    transform: [{ translateY: 5 }, { scale: 1.03 }],
  },
  '100%': {
    transform: [{ translateY: 0 }, { scale: 1 }],
  },
} satisfies CSSAnimationKeyframes<ViewStyle>;

const ENTRY_ANIMATION = {
  animationDuration: `${ENTRY_DURATION}ms`,
  animationFillMode: 'both',
  animationName: CARD_ENTRY_KEYFRAMES,
  animationTimingFunction: 'linear',
} satisfies CSSAnimationProperties<ViewStyle>;

const BACK_FLIP_ANIMATION = {
  animationDelay: `${FLIP_DELAY}ms`,
  animationDuration: `${FLIP_DURATION}ms`,
  animationFillMode: 'both',
  animationName: CARD_BACK_FLIP_KEYFRAMES,
  animationTimingFunction: 'linear',
} satisfies CSSAnimationProperties<ViewStyle>;

const FRONT_FLIP_ANIMATION = {
  animationDelay: `${FLIP_DELAY}ms`,
  animationDuration: `${FLIP_DURATION}ms`,
  animationFillMode: 'both',
  animationName: CARD_FRONT_FLIP_KEYFRAMES,
  animationTimingFunction: 'linear',
} satisfies CSSAnimationProperties<ViewStyle>;

const PLANE_MOTION_ANIMATION = {
  animationDelay: `${FLIP_DELAY}ms`,
  animationDuration: `${PLANE_MOTION_DURATION}ms`,
  animationFillMode: 'both',
  animationName: CARD_PLANE_MOTION_KEYFRAMES,
  animationTimingFunction: 'linear',
} satisfies CSSAnimationProperties<ViewStyle>;

function AuroraField({ height, width }: { height: number; width: number }) {
  const scale = width / 304;
  const center = { x: 158 * scale, y: 159 * scale };
  const panelPath = [
    `M ${53 * scale} 0`,
    `L ${width - 8 * scale} 0`,
    `Q ${width} 0 ${width} ${8 * scale}`,
    `L ${width} ${377 * scale}`,
    `C ${width} ${382 * scale} ${width - 3 * scale} ${385 * scale} ${width - 9 * scale} ${386 * scale}`,
    `C ${width - 64 * scale} ${398 * scale} ${64 * scale} ${398 * scale} ${9 * scale} ${386 * scale}`,
    `C ${3 * scale} ${385 * scale} 0 ${382 * scale} 0 ${377 * scale}`,
    `L 0 ${58 * scale}`,
    `C 0 ${52 * scale} ${4 * scale} ${48 * scale} ${10 * scale} ${48 * scale}`,
    `L ${15 * scale} ${48 * scale}`,
    `C ${31 * scale} ${48 * scale} ${43 * scale} ${35 * scale} ${43 * scale} ${19 * scale}`,
    `L ${43 * scale} ${15 * scale}`,
    `C ${43 * scale} ${7 * scale} ${45 * scale} 0 ${53 * scale} 0`,
    'Z',
  ].join(' ');

  return (
    <Canvas pointerEvents="none" style={StyleSheet.absoluteFill}>
      <Group clip={panelPath}>
        <Rect height={height} width={width} x={0} y={0}>
          <LinearGradient
            colors={['#f2ad70', '#e5b8dd', '#bcdce0', '#edd9bd', '#ddc2ed']}
            end={{ x: width, y: height }}
            positions={[0, 0.27, 0.48, 0.73, 1]}
            start={{ x: 0, y: 0 }}
          />
        </Rect>

        <Group
          layer={
            <Paint>
              <Blur blur={13 * scale} mode="clamp" />
            </Paint>
          }
        >
          <Circle c={{ x: 21 * scale, y: 35 * scale }} r={166 * scale}>
            <RadialGradient
              c={{ x: 21 * scale, y: 35 * scale }}
              colors={['rgba(249,163,49,0.9)', 'rgba(249,163,49,0)']}
              r={166 * scale}
            />
          </Circle>
          <Circle c={{ x: 161 * scale, y: -2 * scale }} r={165 * scale}>
            <RadialGradient
              c={{ x: 161 * scale, y: -2 * scale }}
              colors={['rgba(216,45,193,0.88)', 'rgba(216,45,193,0)']}
              r={165 * scale}
            />
          </Circle>
          <Circle c={{ x: 305 * scale, y: 20 * scale }} r={145 * scale}>
            <RadialGradient
              c={{ x: 305 * scale, y: 20 * scale }}
              colors={['rgba(151,211,219,0.9)', 'rgba(151,211,219,0)']}
              r={145 * scale}
            />
          </Circle>
          <Circle c={{ x: 286 * scale, y: 263 * scale }} r={116 * scale}>
            <RadialGradient
              c={{ x: 286 * scale, y: 263 * scale }}
              colors={['rgba(250,222,105,0.82)', 'rgba(250,222,105,0)']}
              r={116 * scale}
            />
          </Circle>
          <Circle c={center} opacity={0.84} r={177 * scale}>
            <SweepGradient
              c={center}
              colors={[
                '#fa7654',
                '#f4d95d',
                '#ff805c',
                '#dc77c7',
                '#f13570',
                '#ff1759',
                '#d931bd',
                '#9dcede',
                '#fa7654',
              ]}
            />
          </Circle>
          <Circle c={center} r={177 * scale}>
            <RadialGradient
              c={center}
              colors={[
                'rgba(255, 30, 63, 0)',
                'rgba(255, 16, 58, 0.68)',
                'rgba(255, 73, 43, 0.48)',
                'rgba(255, 120, 65, 0)',
              ]}
              positions={[0, 0.6, 0.79, 1]}
              r={177 * scale}
            />
          </Circle>
        </Group>

        <Rect height={height} width={width} x={0} y={0}>
          <LinearGradient
            colors={[
              'rgba(255,255,255,0)',
              'rgba(255,247,226,0.12)',
              'rgba(250,232,222,0.58)',
              'rgba(236,213,244,0.78)',
            ]}
            end={{ x: width * 0.58, y: height }}
            positions={[0, 0.53, 0.77, 1]}
            start={{ x: width * 0.42, y: height * 0.25 }}
          />
        </Rect>
      </Group>
    </Canvas>
  );
}

function CornerEmblem({ scale }: ScaledProps) {
  const size = 48 * scale;
  const center = size / 2;
  const orbitRadius = 17.5 * scale;
  const glyphBoxSize = 6 * scale;

  return (
    <View
      style={{
        alignItems: 'center',
        height: size,
        justifyContent: 'center',
        left: 4 * scale,
        position: 'absolute',
        top: 5 * scale,
        width: size,
      }}
    >
      {CORNER_EMBLEM_GLYPHS.map((glyph, index) => {
        const angle = -112 + (index * 360) / CORNER_EMBLEM_GLYPHS.length;
        const angleInRadians = (angle * Math.PI) / 180;

        return (
          <Text
            allowFontScaling={false}
            key={`${glyph}-${index}`}
            style={{
              color: '#aab3b8',
              fontSize: (glyph === '·' ? 3.5 : 4.4) * scale,
              fontWeight: '700',
              height: glyphBoxSize,
              left:
                center + Math.cos(angleInRadians) * orbitRadius - glyphBoxSize / 2,
              lineHeight: glyphBoxSize,
              position: 'absolute',
              textAlign: 'center',
              top:
                center + Math.sin(angleInRadians) * orbitRadius - glyphBoxSize / 2,
              transform: [{ rotate: `${angle + 90}deg` }],
              width: glyphBoxSize,
            }}
          >
            {glyph}
          </Text>
        );
      })}

      <View style={{ height: 20 * scale, width: 22 * scale }}>
        <View
          style={{
            backgroundColor: '#acb6ba',
            borderRadius: 9 * scale,
            bottom: 1 * scale,
            height: 18 * scale,
            left: 1 * scale,
            position: 'absolute',
            width: 18 * scale,
          }}
        />
        <View
          style={{
            backgroundColor: '#f9fbfb',
            borderRadius: 8 * scale,
            bottom: 4 * scale,
            height: 17 * scale,
            left: 7 * scale,
            position: 'absolute',
            width: 17 * scale,
          }}
        />
        <View
          style={{
            backgroundColor: '#d8dde0',
            borderRadius: 4 * scale,
            height: 12 * scale,
            position: 'absolute',
            right: 0,
            top: 3 * scale,
            transform: [{ rotate: '10deg' }],
            width: 7 * scale,
          }}
        />
      </View>
    </View>
  );
}

function AchievementMedal({ scale }: ScaledProps) {
  const size = 38 * scale;

  return (
    <View style={{ height: size, position: 'relative', width: size }}>
      <Image
        accessibilityIgnoresInvertColors
        contentFit="contain"
        source={require('../../../assets/cards/ball.png')}
        style={[StyleSheet.absoluteFill, { transform: [{ scale: 1.34 }] }]}
      />
      <View
        style={[
          StyleSheet.absoluteFill,
          {
            borderColor: 'rgba(255,255,255,0.96)',
            borderRadius: size / 2,
            borderWidth: 2.5 * scale,
          },
        ]}
      />
    </View>
  );
}

function TinySeal({ scale }: ScaledProps) {
  const size = 15 * scale;

  return (
    <View
      style={{
        alignItems: 'center',
        backgroundColor: '#9188cf',
        borderColor: '#d9d9ee',
        borderRadius: size / 2,
        borderWidth: 1.5 * scale,
        height: size,
        justifyContent: 'center',
        width: size,
      }}
    >
      <Text
        allowFontScaling={false}
        style={{ color: '#f5f3ff', fontSize: 7.2 * scale, fontWeight: '900' }}
      >
        ✿
      </Text>
    </View>
  );
}

function AwakeningCardFace({ width }: { width: number }) {
  const scale = width / DESIGN_WIDTH;
  const height = width * CARD_HEIGHT_RATIO;
  const panelHeight = 400 * scale;
  const panelWidth = width - 16 * scale;
  const portraitSize = 220 * scale;

  return (
    <View
      accessibilityElementsHidden
      pointerEvents="none"
      style={[
        styles.frontSurface,
        {
          borderRadius: 12 * scale,
          boxShadow: `0 ${12 * scale}px ${28 * scale}px rgba(80, 92, 108, 0.2)`,
          height,
          width,
        },
      ]}
    >
      <View
        style={{
          backgroundColor: 'rgba(247, 249, 251, 0.96)',
          bottom: 7 * scale,
          left: 8 * scale,
          position: 'absolute',
          right: 8 * scale,
          top: 394 * scale,
        }}
      />

      <View
        style={{
          borderRadius: 8 * scale,
          height: panelHeight,
          left: 8 * scale,
          overflow: 'hidden',
          position: 'absolute',
          top: 8 * scale,
          width: panelWidth,
        }}
      >
        <AuroraField height={panelHeight} width={panelWidth} />
      </View>

      <View
        style={{
          backgroundColor: '#fffefd',
          borderRadius: portraitSize / 2,
          boxShadow: `0 ${7 * scale}px ${17 * scale}px rgba(220, 32, 77, 0.28)`,
          height: portraitSize,
          left: 56 * scale,
          position: 'absolute',
          top: 58 * scale,
          width: portraitSize,
        }}
      />

      <CornerEmblem scale={scale} />

      <Text
        allowFontScaling={false}
        style={{
          color: 'rgba(108, 111, 116, 0.48)',
          fontSize: 3.4 * scale,
          fontWeight: '700',
          letterSpacing: 0.65 * scale,
          position: 'absolute',
          right: 4 * scale,
          top: 226 * scale,
          transform: [{ rotate: '90deg' }],
        }}
      >
        001 / 151
      </Text>

      <Text
        allowFontScaling={false}
        numberOfLines={1}
        style={{
          color: '#fffdf5',
          fontFamily: SERIF_FONT,
          fontSize: 22.5 * scale,
          fontWeight: '700',
          left: 18 * scale,
          lineHeight: 28 * scale,
          position: 'absolute',
          right: 12 * scale,
          textAlign: 'center',
          textShadowColor: 'rgba(137, 84, 96, 0.17)',
          textShadowOffset: { height: 1 * scale, width: 0 },
          textShadowRadius: 2 * scale,
          top: 309 * scale,
        }}
      >
        The Awakening
      </Text>

      <Text
        allowFontScaling={false}
        numberOfLines={2}
        style={{
          color: 'rgba(255, 255, 250, 0.97)',
          fontSize: 10.5 * scale,
          fontStyle: 'italic',
          fontWeight: '600',
          left: 24 * scale,
          lineHeight: 13 * scale,
          position: 'absolute',
          right: 18 * scale,
          textAlign: 'center',
          textShadowColor: 'rgba(142, 91, 102, 0.14)',
          textShadowOffset: { height: 1 * scale, width: 0 },
          textShadowRadius: 1.5 * scale,
          top: 347 * scale,
        }}
      >
        You’ve taken the first step in your journey.{`\n`}A fresh, new mind awaits you.
      </Text>

      <View
        style={{
          alignItems: 'center',
          backgroundColor: '#8790c9',
          borderRadius: 4 * scale,
          height: 18 * scale,
          justifyContent: 'center',
          left: 15 * scale,
          position: 'absolute',
          top: 405 * scale,
          transform: [{ skewX: '-7deg' }],
          width: 22 * scale,
        }}
      >
        <Text
          allowFontScaling={false}
          style={{
            color: '#f7f9ff',
            fontSize: 13 * scale,
            fontStyle: 'italic',
            fontWeight: '900',
            lineHeight: 15 * scale,
          }}
        >
          E
        </Text>
      </View>

      <Text
        allowFontScaling={false}
        style={{
          color: '#747d91',
          fontSize: 7.7 * scale,
          fontWeight: '600',
          left: 43 * scale,
          position: 'absolute',
          top: 409 * scale,
        }}
      >
        Epic Milestone
      </Text>

      <View
        style={{
          alignItems: 'center',
          flexDirection: 'row',
          gap: 6 * scale,
          position: 'absolute',
          right: 12 * scale,
          top: 407 * scale,
        }}
      >
        <Text
          allowFontScaling={false}
          style={{ color: '#747d91', fontSize: 7.7 * scale, fontWeight: '600' }}
        >
          +100 Tranquility
        </Text>
        <TinySeal scale={scale} />
      </View>

      <View
        style={{
          left: (DESIGN_WIDTH / 2 - 19) * scale,
          position: 'absolute',
          top: 383 * scale,
        }}
      >
        <AchievementMedal scale={scale} />
      </View>

      <Text
        allowFontScaling={false}
        style={{
          bottom: 6 * scale,
          color: '#a0a7b1',
          fontSize: 6.2 * scale,
          left: 14 * scale,
          position: 'absolute',
        }}
      >
        Art by mymind.com
      </Text>

      <Text
        allowFontScaling={false}
        style={{
          bottom: 5.5 * scale,
          color: '#485164',
          fontSize: 8 * scale,
          fontWeight: '900',
          left: 0,
          letterSpacing: 5 * scale,
          position: 'absolute',
          right: 0,
          textAlign: 'center',
        }}
      >
        ✦✦✦✦
      </Text>

      <Text
        allowFontScaling={false}
        style={{
          bottom: 6 * scale,
          color: '#a9afb7',
          fontSize: 6.2 * scale,
          position: 'absolute',
          right: 12 * scale,
        }}
      >
        © mymind Inc.
      </Text>
    </View>
  );
}

export function AwakeningCard({
  exiting = false,
  onExitReady,
  width,
}: AwakeningCardProps) {
  const height = width * CARD_HEIGHT_RATIO;
  const reduceMotion = useReducedMotion();
  const swayProgress = useSharedValue(reduceMotion ? 0.5 : 0);
  const exitProgress = useSharedValue(0);

  useEffect(() => {
    cancelAnimation(swayProgress);
    cancelAnimation(exitProgress);

    if (exiting) {
      if (reduceMotion) {
        swayProgress.set(0.5);
        exitProgress.set(1);
        onExitReady?.();
        return;
      }

      swayProgress.set(
        withTiming(0.5, {
          duration: EXIT_SETTLE_DURATION,
          easing: EXIT_EASING,
        }),
      );
      exitProgress.set(
        withTiming(
          1,
          {
            duration: EXIT_SETTLE_DURATION,
            easing: EXIT_EASING,
          },
          (finished) => {
            if (finished && onExitReady) {
              scheduleOnRN(onExitReady);
            }
          },
        ),
      );

      return () => {
        cancelAnimation(swayProgress);
        cancelAnimation(exitProgress);
      };
    }

    exitProgress.set(0);

    if (reduceMotion) {
      swayProgress.set(0.5);
      return;
    }

    swayProgress.set(0);
    swayProgress.set(
      withDelay(
        SWAY_DELAY,
        withRepeat(
          withTiming(1, {
            duration: SWAY_DURATION,
            easing: Easing.inOut(Easing.quad),
          }),
          -1,
          true,
        ),
      ),
    );

    return () => {
      cancelAnimation(swayProgress);
      cancelAnimation(exitProgress);
    };
  }, [exitProgress, exiting, onExitReady, reduceMotion, swayProgress]);

  const swayStyle = useAnimatedStyle(() => {
    const signedProgress = swayProgress.get() * 2 - 1;
    const dismissProgress = exitProgress.get();
    const rotation =
      signedProgress * SWAY_ANGLE_DEGREES - CARD_TILT_DEGREES;
    const pitch = signedProgress * SWAY_PITCH_DEGREES;
    const yaw = -signedProgress * SWAY_YAW_DEGREES;

    return {
      transform: [
        { perspective: SWAY_PERSPECTIVE },
        { translateX: signedProgress * width * SWAY_HORIZONTAL_RATIO },
        {
          translateY:
            signedProgress * signedProgress * width * SWAY_VERTICAL_RATIO +
            dismissProgress * EXIT_TRANSLATE_Y,
        },
        { rotateX: `${pitch}deg` },
        { rotateY: `${yaw}deg` },
        { rotateZ: `${rotation}deg` },
        { scale: 1 - dismissProgress * (1 - EXIT_SCALE) },
      ],
    };
  }, [exitProgress, swayProgress, width]);

  return (
    <Animated.View
      accessibilityLabel="The Awakening, Epic Milestone, cộng 100 Tranquility"
      accessibilityRole="image"
      style={[
        styles.cardContainer,
        { height, width },
        reduceMotion ? styles.entryResting : ENTRY_ANIMATION,
      ]}
    >
      <Animated.View
        renderToHardwareTextureAndroid
        shouldRasterizeIOS
        style={[
          styles.swayPlane,
          { height, width },
          swayStyle,
        ]}
      >
        <Animated.View
          style={[
            styles.flipPlane,
            { height, width },
            reduceMotion ? styles.planeResting : PLANE_MOTION_ANIMATION,
          ]}
        >
          <Animated.View
            renderToHardwareTextureAndroid
            shouldRasterizeIOS
            style={[
              styles.face,
              { borderRadius: width * 0.045, height, width },
              reduceMotion ? styles.backResting : BACK_FLIP_ANIMATION,
            ]}
          >
            <CollectionCardBack collectorMarkFinish="holographic" width={width} />
          </Animated.View>
          <Animated.View
            renderToHardwareTextureAndroid
            shouldRasterizeIOS
            style={[
              styles.face,
              { height, width },
              reduceMotion ? styles.frontResting : FRONT_FLIP_ANIMATION,
            ]}
          >
            <AwakeningCardFace width={width} />
          </Animated.View>
        </Animated.View>
      </Animated.View>
    </Animated.View>
  );
}

const styles = StyleSheet.create({
  cardContainer: {
    position: 'relative',
  },
  entryResting: {
    opacity: 1,
    transform: [{ scale: 1 }, { rotateZ: CARD_TILT }],
  },
  face: {
    backfaceVisibility: 'hidden',
    left: 0,
    overflow: 'hidden',
    position: 'absolute',
    top: 0,
  },
  flipPlane: {
    position: 'relative',
  },
  planeResting: {
    transform: [{ translateY: 0 }, { scale: 1 }],
  },
  swayPlane: {
    position: 'relative',
  },
  backResting: {
    transform: [{ perspective: 900 }, { rotateY: '180deg' }],
  },
  frontResting: {
    transform: [{ perspective: 900 }, { rotateY: '0deg' }],
  },
  frontSurface: {
    backgroundColor: '#f8fafb',
    borderColor: 'rgba(255, 255, 255, 0.92)',
    borderCurve: 'continuous',
    borderWidth: 1,
    overflow: 'hidden',
    position: 'relative',
  },
});
