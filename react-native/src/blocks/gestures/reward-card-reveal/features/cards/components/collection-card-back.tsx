import { Canvas, Group, LinearGradient, Path, SweepGradient } from '@shopify/react-native-skia';
import { useEffect } from 'react';
import { StyleSheet, View } from 'react-native';
import {
  cancelAnimation,
  Easing,
  useDerivedValue,
  useReducedMotion,
  useSharedValue,
  withDelay,
  withTiming,
} from 'react-native-reanimated';

import { CARD_HEIGHT_RATIO, COLLECTION_CARD_BACK } from '../theme/card-template';

type CollectionCardBackProps = {
  collectorMarkFinish?: CollectorMarkFinish;
  markerCount?: number;
  markerVariant?: MarkerVariant;
  width: number;
};

type CollectorMarkFinish = 'holographic' | 'solid';
type MarkerVariant = 'diamond' | 'lock';

export const COLLECTION_CARD_ART_WIDTH = 218;
export const COLLECTION_CARD_ART_HEIGHT = COLLECTION_CARD_ART_WIDTH * CARD_HEIGHT_RATIO;

// Vector silhouettes traced from the proportions of the marked reference item.
const PATTERN_PATHS = [
  'M27.1 4.9 L6.2 4.9 L6.9 0 L0 0 L0 8.4 L1.4 5.6 L4.9 6.3 L4.9 43.2 L30.5 13.9 Z',
  'M5.6 66.2 L34 44.6 L56.9 36.2 L88.9 18.8 L102.8 22.3 L152.7 23.7 L164.5 22.3 L174.3 16 L175 22.3 L184 30 L212.4 41.1 L212.4 7 L206.9 4.2 L55.5 4.9 L52.1 12.5 L11.1 54.4 Z',
  'M108.3 34.1 L91.6 36.2 L85.4 42.5 L50.7 52.3 L34 62.7 L20.1 77.3 L11.1 98.3 L11.1 108.7 L19.4 120.5 L19.4 124.7 L4.9 142.8 L4.9 170 L9 185.4 L4.9 186.7 L4.9 212.5 L18.1 216.7 L18.1 209 L12.5 195.8 L16 195.1 L35.4 212.5 L55.5 208.3 L54.2 227.9 L61.1 239 L67.3 241.8 L90.9 239 L97.2 246 L115.2 247.4 L145.1 234.1 L170.1 218.1 L188.8 200 L197.9 186.7 L195.8 174.9 L202.7 157.5 L203.4 140.8 L198.6 127.5 L190.2 118.5 L177.7 117.8 L164.5 105.2 L152 100.3 L136.1 101 L134 103.8 L136.1 111.5 L155.5 115.7 L160.4 127.5 L179.1 142.8 L182.6 151.2 L181.2 163.8 L165.9 171.4 L153.4 188.8 L150.7 188.8 L148.6 183.3 L143.7 181.2 L128.4 198.6 L118 203.5 L102.8 204.9 L89.6 202.1 L86.8 205.6 L82.6 204.9 L77.8 198.6 L56.2 147.7 L51.4 141.5 L43 139.4 L46.5 110.8 L59 83.6 L54.8 72.5 L78.5 60.6 L115.2 54.4 L120.1 51.6 L121.5 46 L145.1 48.8 L164.5 55.7 L169.4 54.4 L163.8 46 L152.7 40.4 Z',
  'M181.9 65.5 L177 71.1 L177 81.5 L192.3 100.3 L212.4 114.3 L212.4 70.4 L193.7 63.4 Z',
  'M154.1 80.8 L126.4 72.5 L115.2 73.2 L107.6 77.3 L90.3 77.3 L81.2 81.5 L69.4 94.8 L61.8 109.4 L62.5 117.1 L68 118.5 L76.4 115 L81.9 110.1 L86.8 97.6 L97.2 96.9 L128.4 81.5 L143 81.5 L153.4 86.4 Z',
  'M213.1 201.4 L211.8 199.3 L206.9 208.3 L187.5 225.1 L179.1 237.6 L164.5 241.8 L152 249.5 L134 271.8 L93 272.5 L86.8 264.8 L58.3 248.8 L35.4 239.7 L11.1 235.5 L4.2 244.6 L4.2 291.3 L6.2 297.5 L21.5 295.4 L31.2 298.2 L70.1 298.2 L84.7 294.1 L115.9 294.8 L152.7 282.2 L175.6 269 L202 244.6 L207.6 236.2 L212.4 219.5 Z',
  'M212.4 288.5 L182.6 289.9 L168.7 297.5 L206.2 298.2 L212.4 296.1 Z',
] as const;

const COLLECTOR_MARK_PATHS = [
  'M111.1 138.7 L106.9 140.8 L106.9 142.8 L108.3 144.9 L108.3 154 L106.9 156.8 L107.6 158.9 L111.8 158.9 L110.4 152.6 Z',
  'M97.2 110.1 L92.3 112.2 L89.6 115 L82.6 126.1 L81.2 131.7 L83.3 133.1 L84.7 131.7 L84.7 129.6 L87.5 123.3 L92.3 116.4 L95.8 113.6 L99.3 114.3 L98.6 119.9 L93.7 129.6 L88.2 135.2 L88.2 139.4 L81.9 140.1 L79.1 143.5 L78.5 146.3 L79.1 158.9 L82.6 168.6 L88.2 166.5 L89.6 172.8 L91.6 176.3 L95.8 179.1 L101.4 179.1 L102.1 184 L105.5 188.1 L109 188.8 L111.1 192.3 L115.2 193.7 L118.7 184.7 L130.5 173.5 L134 166.5 L129.1 165.8 L128.4 166.5 L129.1 170 L125 175.6 L122.9 174.9 L120.1 170 L115.2 167.9 L104.8 170.7 L104.1 166.5 L101.4 161.7 L97.2 158.9 L95.1 158.9 L92.3 154.7 L90.3 146.3 L90.3 138 L91.6 135.9 L94.4 137.3 L99.3 156.8 L100.7 157.5 L104.1 154.7 L100 149.8 L95.8 131 L97.9 126.1 L103.4 117.8 L107.6 113.6 L109.7 113.6 L110.4 116.4 L102.1 132.4 L102.8 135.2 L106.2 135.9 L110.4 132.4 L110.4 131.7 L108.3 133.1 L106.2 133.1 L105.5 130.3 L109 124 L110.4 124 L113.9 126.8 L112.5 124 L113.2 121.9 L118.7 122.6 L128.4 127.5 L135.4 135.9 L135.4 142.2 L132.6 142.8 L127.7 138 L123.6 138 L116.6 142.2 L117.3 157.5 L115.9 161 L117.3 161.7 L120.8 161 L119.4 154.7 L119.4 147 L120.8 143.5 L122.9 141.5 L125.7 140.8 L129.1 143.5 L130.5 146.3 L130.5 156.1 L128.4 161 L133.3 161.7 L134 161 L133.3 151.2 L136.8 145.6 L137.5 134.5 L132.6 122.6 L129.1 117.8 L120.8 110.1 L118 108.7 L116.6 109.4 L116.6 110.8 L120.8 113.6 L127.7 120.5 L127.1 122.6 L115.9 118.5 L113.9 118.5 L113.2 117.8 L113.9 112.2 L111.8 110.1 L108.3 110.1 L104.1 113.6 L102.8 113.6 L100.7 110.8 Z',
] as const;

const COLLECTOR_MARK_HOLES = [
  'M117.3 170.7 L120.1 173.5 L120.1 177.7 L118 181.9 L112.5 186 L106.9 186 L104.1 181.9 L105.5 176.3 L111.8 170.7 L113.9 170 Z',
  'M83.3 142.8 L84.7 142.8 L86.8 144.9 L90.9 158.2 L90.9 161 L88.2 165.8 L86.8 165.8 L84.7 163.8 L81.9 156.8 L81.2 146.3 Z',
] as const;

const COLLECTOR_MARK_CENTER = { x: 108, y: 151 };
const COLLECTOR_MARK_BASE = ['#625f9e', '#7859b6', '#ba54b8', '#ea64ad'];
const COLLECTOR_MARK_BASE_POSITIONS = [0, 0.34, 0.7, 1];
const COLLECTOR_MARK_BASE_START = { x: 82, y: 194 };
const COLLECTOR_MARK_BASE_END = { x: 138, y: 108 };
const COLLECTOR_MARK_RAINBOW = [
  '#625ba7',
  '#8055c2',
  '#df4cbd',
  '#ff668e',
  '#ffad58',
  '#f4e85d',
  '#62dda0',
  '#51c9e2',
  '#625ba7',
];
const COLLECTOR_MARK_RAINBOW_POSITIONS = [0, 0.12, 0.25, 0.36, 0.47, 0.58, 0.7, 0.84, 1];
const COLLECTOR_MARK_GLINT = [
  'rgba(255,255,255,0)',
  'rgba(255,218,246,0.05)',
  'rgba(255,246,184,0.78)',
  'rgba(255,255,255,0.96)',
  'rgba(171,246,255,0.5)',
  'rgba(255,255,255,0)',
];
const COLLECTOR_MARK_GLINT_POSITIONS = [0, 0.37, 0.46, 0.5, 0.56, 1];
const COLLECTOR_MARK_SWEEP_DELAY = 400;
const COLLECTOR_MARK_SWEEP_DURATION = 720;
const COLLECTOR_MARK_STATIC_PROGRESS = 0.58;
const COLLECTOR_MARK_START_ANGLE = -70;
const COLLECTOR_MARK_ANGLE_TRAVEL = 190;

function HolographicCollectorMark() {
  const reduceMotion = useReducedMotion();
  const progress = useSharedValue(reduceMotion ? COLLECTOR_MARK_STATIC_PROGRESS : 0);

  useEffect(() => {
    cancelAnimation(progress);
    progress.set(reduceMotion ? COLLECTOR_MARK_STATIC_PROGRESS : 0);

    if (!reduceMotion) {
      progress.set(
        withDelay(
          COLLECTOR_MARK_SWEEP_DELAY,
          withTiming(1, {
            duration: COLLECTOR_MARK_SWEEP_DURATION,
            easing: Easing.bezier(0.22, 0.72, 0.22, 1),
          })
        )
      );
    }

    return () => cancelAnimation(progress);
  }, [progress, reduceMotion]);

  const sweepStart = useDerivedValue(
    () => COLLECTOR_MARK_START_ANGLE + progress.get() * COLLECTOR_MARK_ANGLE_TRAVEL
  );
  const sweepEnd = useDerivedValue(() => sweepStart.get() + 360);
  const rainbowOpacity = useDerivedValue(() =>
    Math.min(Math.max((progress.get() - 0.16) / 0.66, 0), 1) * 0.88
  );
  const glintStart = useDerivedValue(() => ({
    x: 20 + progress.get() * 120,
    y: 90,
  }));
  const glintEnd = useDerivedValue(() => ({
    x: 80 + progress.get() * 120,
    y: 210,
  }));
  const glintOpacity = useDerivedValue(() => {
    if (reduceMotion) {
      return 0.2;
    }

    return Math.sin(progress.get() * Math.PI) * 0.72;
  });

  return (
    <>
      {COLLECTOR_MARK_PATHS.map((path) => (
        <Path key={`collector-base-${path}`} path={path}>
          <LinearGradient
            colors={COLLECTOR_MARK_BASE}
            end={COLLECTOR_MARK_BASE_END}
            positions={COLLECTOR_MARK_BASE_POSITIONS}
            start={COLLECTOR_MARK_BASE_START}
          />
        </Path>
      ))}

      <Group opacity={rainbowOpacity}>
        {COLLECTOR_MARK_PATHS.map((path) => (
          <Path key={`holographic-${path}`} path={path}>
            <SweepGradient
              c={COLLECTOR_MARK_CENTER}
              colors={COLLECTOR_MARK_RAINBOW}
              end={sweepEnd}
              positions={COLLECTOR_MARK_RAINBOW_POSITIONS}
              start={sweepStart}
            />
          </Path>
        ))}
      </Group>

      <Group blendMode="screen" opacity={glintOpacity}>
        {COLLECTOR_MARK_PATHS.map((path) => (
          <Path key={`glint-${path}`} path={path}>
            <LinearGradient
              colors={COLLECTOR_MARK_GLINT}
              end={glintEnd}
              positions={COLLECTOR_MARK_GLINT_POSITIONS}
              start={glintStart}
            />
          </Path>
        ))}
      </Group>
    </>
  );
}

type CollectionCardArtworkProps = {
  backgroundColor: string;
  collectorMarkFinish?: CollectorMarkFinish;
  patternColor: string;
  sealColor: string;
  showCollectorMark?: boolean;
};

export function CollectionCardArtwork({
  backgroundColor,
  collectorMarkFinish = 'solid',
  patternColor,
  sealColor,
  showCollectorMark = true,
}: CollectionCardArtworkProps) {
  return (
    <>
      {PATTERN_PATHS.map((path) => (
        <Path color={patternColor} key={path} path={path} />
      ))}

      {showCollectorMark ? (
        <>
          {collectorMarkFinish === 'holographic' ? (
            <HolographicCollectorMark />
          ) : (
            COLLECTOR_MARK_PATHS.map((path) => (
              <Path color={sealColor} key={path} path={path} />
            ))
          )}
          {COLLECTOR_MARK_HOLES.map((path) => (
            <Path color={backgroundColor} key={path} path={path} />
          ))}
        </>
      ) : null}
    </>
  );
}

export function CollectionCardBack({
  collectorMarkFinish = 'solid',
  markerCount = 3,
  markerVariant = 'diamond',
  width,
}: CollectionCardBackProps) {
  const scale = width / COLLECTION_CARD_ART_WIDTH;
  const height = width * CARD_HEIGHT_RATIO;
  const radius = 10 * scale;
  const markerSize = 22 * scale;

  return (
    <View style={{ gap: 10 * scale, width }}>
      <View
        style={[
          styles.card,
          {
            backgroundColor: COLLECTION_CARD_BACK.background,
            borderColor: COLLECTION_CARD_BACK.edge,
            borderRadius: radius,
            borderWidth: Math.max(1, 1.4 * scale),
            height,
            width,
          },
        ]}
      >
        <Canvas pointerEvents="none" style={StyleSheet.absoluteFill}>
          <Group transform={[{ scale }]}>
            <CollectionCardArtwork
              backgroundColor={COLLECTION_CARD_BACK.background}
              collectorMarkFinish={collectorMarkFinish}
              patternColor={COLLECTION_CARD_BACK.patternDark}
              sealColor={COLLECTION_CARD_BACK.seal}
            />

            {/* <RoundedRect
              color={COLLECTION_CARD_BACK.edgeInner}
              height={COLLECTION_CARD_ART_HEIGHT - 6}
              r={7.5}
              style="stroke"
              strokeWidth={1}
              width={COLLECTION_CARD_ART_WIDTH - 6}
              x={3}
              y={3}
            /> */}
          </Group>
        </Canvas>
      </View>

      <View style={styles.markerRow}>
        {Array.from({ length: markerCount }, (_, marker) => (
          <View
            key={marker}
            style={{
              alignItems: 'center',
              borderColor: COLLECTION_CARD_BACK.markerBorder,
              borderRadius: markerSize / 2,
              borderWidth: Math.max(0.7, scale),
              height: markerSize,
              justifyContent: 'center',
              width: markerSize,
            }}
          >
            {markerVariant === 'lock' ? (
              <View style={{ alignItems: 'center', height: 11 * scale, width: 10 * scale }}>
                <View
                  style={{
                    borderColor: COLLECTION_CARD_BACK.markerInk,
                    borderRadius: 4 * scale,
                    borderWidth: Math.max(0.7, scale),
                    height: 6 * scale,
                    width: 7 * scale,
                  }}
                />
                <View
                  style={{
                    backgroundColor: COLLECTION_CARD_BACK.markerInk,
                    borderRadius: 1.5 * scale,
                    height: 6 * scale,
                    marginTop: -2 * scale,
                    width: 9 * scale,
                  }}
                />
              </View>
            ) : (
              <View
                style={{
                  backgroundColor: COLLECTION_CARD_BACK.markerInk,
                  borderRadius: 2 * scale,
                  height: 4 * scale,
                  transform: [{ rotate: '45deg' }],
                  width: 4 * scale,
                }}
              />
            )}
          </View>
        ))}
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  card: {
    borderCurve: 'continuous',
    overflow: 'hidden',
    position: 'relative',
  },
  markerRow: {
    flexDirection: 'row',
    gap: 8,
    justifyContent: 'center',
  },
});
