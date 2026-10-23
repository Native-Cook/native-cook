import { Canvas, Circle, Group, Path, Skia, vec } from '@shopify/react-native-skia';
import { LinearGradient } from 'expo-linear-gradient';
import { useState } from 'react';
import { type LayoutChangeEvent, Pressable, StyleSheet, Text, View } from 'react-native';
import Animated, { type SharedValue, useAnimatedStyle, useDerivedValue } from 'react-native-reanimated';
import Svg, { Circle as SvgCircle, Path as SvgPath } from 'react-native-svg';

import { ARROWS, BANG, CHECK, GLYPH_BOX, PAPERCLIP } from './glyphs';
import { type Events, rowLook } from './motion';

export const ROW_HEIGHT = 48;
export const ROW_GAP = 10;
const RADIUS = 18;
const FILL = '#181B1F';
// The upload fill, its bright leading edge, and the red it turns when the upload fails.
const PROGRESS = '#22262B';
const EDGE = 24;
const EDGE_COLORS = ['rgba(255,255,255,0)', 'rgba(255,255,255,0.1)'] as const;
const FAILED_FILL = '#3A1A1C';
const FAILED_ALPHA = 0.7;
// The clip on the left and the name after it.
const CLIP = 14;
const CLIP_X = 20;
const CLIP_COLOR = '#7E8187';
/** Stroke widths in points (the glyphs are drawn on a 24 grid, so they are scaled up there). */
const CLIP_STROKE = 1.2;
const NAME_X = 40;
const NAME = '#E8E9EA';
const NAME_SIZE = 11;
const NAME_LINE = 14;
// The status on the right: a ring while uploading, a green disc with a check when done.
const STATUS = 18;
const STATUS_RIGHT = 13;
/** The check fills the disc less this inset on each side. */
const CHECK_INSET = 1;
const CHECK_STROKE = 1.4;
const CHECK_SCALE = (STATUS - CHECK_INSET * 2) / GLYPH_BOX;
const RING_STROKE = 2;
const TRACK = 'rgba(255,255,255,0.1)';
const RING = '#E8E9EA';
const DISC = '#1E3A23';
const GREEN = '#46C35A';
// A failed upload: the pill after the name and Retry on the right.
const PILL_GAP = 8;
const PILL_HEIGHT = 21;
const PILL_TEXT_SIZE = 9.5;
const PILL_FILL = '#3A1A1C';
const BANG_SIZE = 10.5;
const PILL_SPACING = 5;
const BANG_FILL = '#E5484D';
const PILL_TEXT = '#FF5F57';
const RETRY = '#8B8E93';
const ARROWS_SIZE = 13;
const ARROWS_STROKE = 1.8;
const RETRY_GAP = 6;
const RETRY_SIZE = 11;
/** Room kept on the right of the name for Retry. */
const NAME_ROOM = 70;

/** The ring's arc, from 12 o'clock clockwise. */
const RING_PATH = Skia.PathBuilder.Make()
  .addArc({ x: RING_STROKE / 2, y: RING_STROKE / 2, width: STATUS - RING_STROKE, height: STATUS - RING_STROKE }, -90, 360)
  .build();

type Props = {
  id: number;
  name: string;
  width: number;
  clock: SharedValue<number>;
  events: SharedValue<Events>;
  still: boolean;
  failed: boolean;
  status: string;
  onRetryIn: (id: number) => void;
  onRetryOut: (id: number) => void;
  onRetry: (id: number) => void;
};

/** One file in the list, drawn from the clock: its upload fill, status, and the failed pill and Retry. */
export function FileRow({ id, name, width, clock, events, still, failed, status, onRetryIn, onRetryOut, onRetry }: Props) {
  const [pillWidth, setPillWidth] = useState(0);
  const [nameWidth, setNameWidth] = useState(0);
  const look = useDerivedValue(() => {
    const rows = events.get().rows;
    for (let i = 0; i < rows.length; i++) {
      if (rows[i].id === id) return rowLook(rows, i, clock.get(), still, ROW_HEIGHT, ROW_GAP);
    }
    return null;
  });

  const boxStyle = useAnimatedStyle(() => {
    const l = look.get();
    if (!l) return { opacity: 0 };
    return {
      height: l.boxHeight,
      opacity: l.boxOpacity,
      transform: [{ translateX: l.shakeX }, { translateY: l.top + l.boxY }, { scale: l.boxScale }],
    };
  });
  const innerStyle = useAnimatedStyle(() => {
    const l = look.get();
    return {
      transform: [{ translateY: l ? (l.boxHeight - ROW_HEIGHT) / 2 : 0 }],
    };
  });
  const contentStyle = useAnimatedStyle(() => {
    const l = look.get();
    if (!l) return { opacity: 0 };
    return {
      opacity: l.contentOpacity,
      transform: [{ translateY: l.contentY }, { scale: l.contentScale }],
    };
  });
  const fillStyle = useAnimatedStyle(() => {
    const l = look.get();
    return { transform: [{ translateX: ((l ? l.fill : 0) - 1) * width }] };
  });
  const normalStyle = useAnimatedStyle(() => ({
    opacity: look.get()?.fillAlpha ?? 0,
  }));
  const redStyle = useAnimatedStyle(() => ({
    opacity: (look.get()?.redAlpha ?? 0) * FAILED_ALPHA,
  }));
  const pillOuter = useAnimatedStyle(() => {
    const l = look.get();
    const open = l ? l.pill : 0;
    return {
      opacity: l ? l.pillAlpha : 0,
      transform: [{ translateX: -(1 - open) * pillWidth }],
    };
  });
  const pillInner = useAnimatedStyle(() => {
    const l = look.get();
    return { transform: [{ translateX: (1 - (l ? l.pill : 0)) * pillWidth }] };
  });
  const bangStyle = useAnimatedStyle(() => ({
    transform: [{ scale: look.get()?.bang ?? 0 }],
  }));
  const retryStyle = useAnimatedStyle(() => {
    const l = look.get();
    if (!l) return { opacity: 0 };
    return {
      opacity: l.retryAlpha,
      transform: [{ translateX: l.retryX }, { scale: l.retryScale }],
    };
  });
  const arrowsStyle = useAnimatedStyle(() => ({
    transform: [{ rotate: `${look.get()?.spin ?? 0}deg` }],
  }));

  // The status, drawn by Skia from the same look.
  const ringEnd = useDerivedValue(() => look.get()?.ring ?? 0);
  const ringAlpha = useDerivedValue(() => look.get()?.ringAlpha ?? 0);
  const ringTransform = useDerivedValue(() => [{ scale: look.get()?.ringScale ?? 1 }]);
  const discAlpha = useDerivedValue(() => look.get()?.discAlpha ?? 0);
  const discTransform = useDerivedValue(() => [{ scale: look.get()?.discScale ?? 1 }]);
  const checkEnd = useDerivedValue(() => look.get()?.check ?? 0);

  const onPillLayout = (event: LayoutChangeEvent) => setPillWidth(event.nativeEvent.layout.width);
  const onNameLayout = (event: LayoutChangeEvent) => setNameWidth(event.nativeEvent.layout.width);

  const label = status === 'd' ? `${name}, uploaded` : status === 'f' ? `${name}, upload failed` : `${name}, uploading`;

  return (
    <Animated.View style={[styles.box, { width }, boxStyle]} accessible accessibilityLabel={label}>
      <Animated.View style={[styles.inner, innerStyle]}>
        <Animated.View style={[styles.fill, { width }, fillStyle]}>
          <Animated.View style={[StyleSheet.absoluteFill, normalStyle]}>
            <View style={[StyleSheet.absoluteFill, { backgroundColor: PROGRESS }]} />
            <LinearGradient colors={EDGE_COLORS} start={{ x: 0, y: 0 }} end={{ x: 1, y: 0 }} style={styles.edge} />
          </Animated.View>
          <Animated.View style={[StyleSheet.absoluteFill, { backgroundColor: FAILED_FILL }, redStyle]} />
        </Animated.View>

        <Animated.View style={[StyleSheet.absoluteFill, contentStyle]}>
          <Svg width={CLIP} height={CLIP} viewBox={`0 0 ${GLYPH_BOX} ${GLYPH_BOX}`} style={styles.clip}>
            <SvgPath
              d={PAPERCLIP}
              stroke={CLIP_COLOR}
              strokeWidth={CLIP_STROKE * (GLYPH_BOX / CLIP)}
              strokeLinecap="round"
              strokeLinejoin="round"
              fill="none"
            />
          </Svg>

          <Text style={[styles.name, { maxWidth: width - NAME_X - NAME_ROOM }]} numberOfLines={1} onLayout={onNameLayout}>
            {name}
          </Text>
          <View style={[styles.pillRow, { left: NAME_X + nameWidth + PILL_GAP }]}>
            <Animated.View style={[styles.pillClip, pillOuter]}>
              <Animated.View style={[styles.pill, pillInner]} onLayout={onPillLayout}>
                <Animated.View style={[styles.bang, bangStyle]}>
                  <Svg width={BANG_SIZE} height={BANG_SIZE} viewBox={`0 0 ${GLYPH_BOX} ${GLYPH_BOX}`}>
                    <SvgCircle cx={GLYPH_BOX / 2} cy={GLYPH_BOX / 2} r={GLYPH_BOX / 2} fill={BANG_FILL} />
                    <SvgPath d={BANG} fill="#ffffff" />
                  </Svg>
                </Animated.View>
                <Text style={styles.pillText} numberOfLines={1}>
                  Upload failed
                </Text>
              </Animated.View>
            </Animated.View>
          </View>

          <Canvas style={styles.status} pointerEvents="none">
            <Group transform={ringTransform} origin={vec(STATUS / 2, STATUS / 2)} opacity={ringAlpha}>
              <Circle
                cx={STATUS / 2}
                cy={STATUS / 2}
                r={(STATUS - RING_STROKE) / 2}
                style="stroke"
                strokeWidth={RING_STROKE}
                color={TRACK}
              />
              <Path
                path={RING_PATH}
                style="stroke"
                strokeWidth={RING_STROKE}
                strokeCap="round"
                color={RING}
                start={0}
                end={ringEnd}
              />
            </Group>
            <Group transform={discTransform} origin={vec(STATUS / 2, STATUS / 2)} opacity={discAlpha}>
              <Circle cx={STATUS / 2} cy={STATUS / 2} r={STATUS / 2} color={DISC} />
              <Group transform={[{ translateX: CHECK_INSET }, { translateY: CHECK_INSET }, { scale: CHECK_SCALE }]}>
                <Path
                  path={CHECK}
                  style="stroke"
                  strokeWidth={CHECK_STROKE / CHECK_SCALE}
                  strokeCap="round"
                  strokeJoin="round"
                  color={GREEN}
                  start={0}
                  end={checkEnd}
                />
              </Group>
            </Group>
          </Canvas>

          <Animated.View style={[styles.retry, retryStyle]} pointerEvents={failed ? 'auto' : 'none'}>
            <Pressable
              style={styles.retryFace}
              hitSlop={10}
              disabled={!failed}
              onPressIn={() => onRetryIn(id)}
              onPressOut={() => onRetryOut(id)}
              onPress={() => onRetry(id)}
              accessibilityRole="button"
              accessibilityLabel={`Retry ${name}`}
            >
              <Animated.View style={arrowsStyle}>
                <Svg width={ARROWS_SIZE} height={ARROWS_SIZE} viewBox={`0 0 ${GLYPH_BOX} ${GLYPH_BOX}`}>
                  <SvgPath
                    d={ARROWS.arcs}
                    stroke={RETRY}
                    strokeWidth={ARROWS_STROKE * (GLYPH_BOX / ARROWS_SIZE)}
                    strokeLinecap="round"
                    fill="none"
                  />
                  <SvgPath d={ARROWS.heads} fill={RETRY} />
                </Svg>
              </Animated.View>
              <Text style={styles.retryText}>Retry</Text>
            </Pressable>
          </Animated.View>
        </Animated.View>
      </Animated.View>
    </Animated.View>
  );
}

const styles = StyleSheet.create({
  box: {
    position: 'absolute',
    left: 0,
    top: 0,
    borderRadius: RADIUS,
    borderCurve: 'continuous',
    overflow: 'hidden',
    backgroundColor: FILL,
  },
  inner: {
    position: 'absolute',
    left: 0,
    right: 0,
    top: 0,
    height: ROW_HEIGHT,
  },
  fill: {
    position: 'absolute',
    left: 0,
    top: 0,
    bottom: 0,
  },
  edge: {
    position: 'absolute',
    right: 0,
    top: 0,
    bottom: 0,
    width: EDGE,
  },
  clip: {
    position: 'absolute',
    left: CLIP_X - CLIP / 2,
    top: (ROW_HEIGHT - CLIP) / 2,
  },
  name: {
    position: 'absolute',
    left: NAME_X,
    top: (ROW_HEIGHT - NAME_LINE) / 2,
    lineHeight: NAME_LINE,
    color: NAME,
    fontSize: NAME_SIZE,
    includeFontPadding: false,
  },
  pillRow: {
    position: 'absolute',
    top: 0,
    height: ROW_HEIGHT,
    justifyContent: 'center',
  },
  pillClip: {
    height: PILL_HEIGHT,
    overflow: 'hidden',
  },
  pill: {
    height: PILL_HEIGHT,
    flexDirection: 'row',
    alignItems: 'center',
    paddingLeft: 6,
    paddingRight: 10,
    borderRadius: PILL_HEIGHT / 2,
    backgroundColor: PILL_FILL,
  },
  bang: {
    width: BANG_SIZE,
    height: BANG_SIZE,
    marginRight: PILL_SPACING,
  },
  pillText: {
    color: PILL_TEXT,
    fontSize: PILL_TEXT_SIZE,
    fontWeight: '500',
    includeFontPadding: false,
  },
  status: {
    position: 'absolute',
    right: STATUS_RIGHT,
    top: (ROW_HEIGHT - STATUS) / 2,
    width: STATUS,
    height: STATUS,
  },
  retry: {
    position: 'absolute',
    right: STATUS_RIGHT,
    top: 0,
    height: ROW_HEIGHT,
    justifyContent: 'center',
  },
  retryFace: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: RETRY_GAP,
  },
  retryText: {
    color: RETRY,
    fontSize: RETRY_SIZE,
    includeFontPadding: false,
  },
});
