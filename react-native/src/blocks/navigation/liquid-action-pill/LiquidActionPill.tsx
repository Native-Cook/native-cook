import { BlurMask, Canvas, Circle, Group, Path, Skia, type SkPath, usePathValue } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { LinearGradient } from 'expo-linear-gradient';
import { StatusBar } from 'expo-status-bar';
import { useEffect, useRef, useState } from 'react';
import { type LayoutChangeEvent, Pressable, StyleSheet, Text, View, useWindowDimensions } from 'react-native';
import Animated, {
  Easing,
  ReduceMotion,
  type SharedValue,
  useAnimatedStyle,
  useDerivedValue,
  useReducedMotion,
  useSharedValue,
  withTiming,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnUI } from 'react-native-worklets';

import {
  BAR_BOTTOM,
  BAR_HEIGHT,
  BAR_MARGIN,
  BAR_RADIUS,
  BOWL_DEPTH,
  BOWL_FLOOR,
  BOWL_HALF,
  BOWL_SHOULDER,
  BUTTON,
  BUTTON_RAISE,
  CLOSE_AT,
  DURATION,
  HOME_AT,
  ICON,
  ICON_GAP,
  KAPPA,
  LOOP,
  NECK_SNAP,
  NECK_SPREAD,
  OPEN_AT,
  PLUS_ARM,
  PROFILE_AT,
  REDUCED_DURATION,
  RISE,
  TAB_FADE,
  TAB_ICON,
  TAB_INSET,
  TURN,
  USER_HOLD,
  bowlDepth,
  bowlHalf,
  dotRadius,
  dotX,
  iconIn,
  lift,
  pillHeight,
  pillWidth,
  plusScale,
} from './motion';

type Props = {
  /** The titles of the two pages, the house's first. */
  titles?: [string, string];
  /** What the four icons of the pill are called: camera, picture, chart and the cross. */
  actionLabels?: [string, string, string, string];
  /** The page behind everything, from its top left corner to its bottom right. */
  background?: [string, string];
  /** The button and the pill. */
  accent?: string;
  /** The two tab icons. */
  tint?: string;
  barColor?: string;
  /** Opens and closes the pill and changes the page by itself, every LOOP seconds. */
  autoPlay?: boolean;
  /** Called with 0 or 1 when a tab is chosen. */
  onTabChange?: (index: number) => void;
  /** Called with 0, 1 or 2 when one of the pill's first three icons is pressed. */
  onAction?: (index: number) => void;
  onOpenChange?: (open: boolean) => void;
};

const BAR_SHADOW = 'rgba(94,70,118,0.2)';
// The shadows' offsets, and their blur as a standard deviation (half a CSS blur radius).
const BAR_SHADOW_Y = 8;
const BAR_SHADOW_BLUR = 10;
const PILL_SHADOW_Y = 6;
const PILL_SHADOW_BLUR = 6;
const PILL_SHADOW_ALPHA = 0.35;
const FROST = 'rgba(255,255,255,0.22)';
const LINE = 'rgba(255,255,255,0.5)';
// How strongly the chosen tab's icon is filled.
const TAB_FILL = 0.15;
const TAB_STROKE = 1.8;
const ICON_STROKE = 1.6;
const PLUS_STROKE = 2;
const PRESSED_SCALE = 0.9;
// An icon starts this small and its dot ends this small while one turns into the other.
const ICON_FROM_SCALE = 0.6;

const TITLE_TOP = 24;
const TITLE_LINE = 41;
const CARD_GAP = 16;
const CARDS_TOP = TITLE_TOP + TITLE_LINE + 20;
const TAB_HIT = 72;
const BUTTON_HIT = 64;
const EASE = Easing.bezier(0.42, 0, 0.58, 1);

// The placeholder lines of the pages: left, top, width, height inside their card.
const HOME_CARDS = [
  { height: 170, lines: [[20, 24, 96, 10], [20, 48, 200, 18], [20, 136, 140, 10]] },
  { height: 110, lines: [[20, 24, 120, 10], [20, 50, 220, 10], [20, 76, 160, 10]] },
];
const PROFILE_CARDS = [
  { height: 170, lines: [[112, 58, 140, 14], [112, 86, 180, 10], [112, 108, 110, 10]] },
  { height: 110, lines: [[20, 24, 90, 10], [20, 50, 250, 10], [20, 76, 190, 10]] },
];
const AVATAR = 72;

function path(draw: (builder: ReturnType<typeof Skia.PathBuilder.Make>) => void) {
  const builder = Skia.PathBuilder.Make();
  draw(builder);
  return builder.build();
}

const FRAME = { rect: { x: 2.8, y: 2.8, width: 14.4, height: 14.4 }, rx: 3.2, ry: 3.2 };

// The pill's icons in a 20-point box, one path for each stroke so that each can be drawn in.
const ICONS: SkPath[][] = [
  [
    path((b) =>
      b
        .moveTo(2.5, 7.2)
        .quadTo(2.5, 5.5, 4.2, 5.5)
        .lineTo(6.4, 5.5)
        .lineTo(7.6, 3.6)
        .lineTo(12.4, 3.6)
        .lineTo(13.6, 5.5)
        .lineTo(15.8, 5.5)
        .quadTo(17.5, 5.5, 17.5, 7.2)
        .lineTo(17.5, 14.6)
        .quadTo(17.5, 16.3, 15.8, 16.3)
        .lineTo(4.2, 16.3)
        .quadTo(2.5, 16.3, 2.5, 14.6)
        .close(),
    ),
    path((b) => b.addCircle(10, 10.8, 2.9)),
  ],
  [
    path((b) => b.addRRect(FRAME)),
    path((b) => b.addCircle(7.3, 7.4, 1.3)),
    path((b) => b.moveTo(3.2, 14.2).lineTo(7.6, 10.4).lineTo(10.6, 13).lineTo(13, 11).lineTo(16.8, 14.4)),
  ],
  [
    path((b) => b.addRRect(FRAME)),
    path((b) => b.moveTo(5.8, 12.8).lineTo(8.6, 9.8).lineTo(10.9, 11.6).lineTo(14.2, 7.6)),
    path((b) => b.moveTo(11.6, 7.4).lineTo(14.4, 7.4).lineTo(14.4, 10.2)),
  ],
  [path((b) => b.moveTo(5, 5).lineTo(15, 15)), path((b) => b.moveTo(15, 5).lineTo(5, 15))],
];

// The tabs' icons in a 24-point box: the shape that is stroked and filled, and what is only stroked.
const HOUSE = path((b) =>
  b
    .moveTo(4, 10.4)
    .lineTo(12, 3.6)
    .lineTo(20, 10.4)
    .lineTo(20, 18.3)
    .quadTo(20, 20.4, 17.9, 20.4)
    .lineTo(6.1, 20.4)
    .quadTo(4, 20.4, 4, 18.3)
    .close(),
);
const DOOR = path((b) => b.moveTo(9.6, 20.4).lineTo(9.6, 15.4).quadTo(9.6, 13.4, 12, 13.4).quadTo(14.4, 13.4, 14.4, 15.4).lineTo(14.4, 20.4));
const PERSON = path((b) => b.addCircle(12, 8.2, 3.9).addOval({ x: 5.6, y: 15, width: 12.8, height: 5.6 }));

function tap() {
  if (process.env.EXPO_OS !== 'web') Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
}

function select() {
  if (process.env.EXPO_OS !== 'web') Haptics.selectionAsync().catch(() => {});
}

/** One of the four white dots; it shrinks and fades as its icon comes in. */
function Dot({ index, pose }: { index: number; pose: SharedValue<number> }) {
  const cx = useDerivedValue(() => dotX(pose.get(), index));
  const r = useDerivedValue(() => dotRadius(pose.get()) * (1 - (1 - ICON_FROM_SCALE) * iconIn(pose.get(), index)));
  const opacity = useDerivedValue(() => 1 - iconIn(pose.get(), index));
  return <Circle cx={cx} cy={0} r={r} color="#ffffff" opacity={opacity} />;
}

/** One of the pill's icons: it grows out of its dot while its strokes are drawn. */
function Glyph({ index, pose, held, depth }: { index: number; pose: SharedValue<number>; held: SharedValue<number>; depth: SharedValue<number> }) {
  const shown = useDerivedValue(() => iconIn(pose.get(), index));
  const transform = useDerivedValue(() => {
    const scale = (ICON_FROM_SCALE + (1 - ICON_FROM_SCALE) * shown.get()) * (held.get() === index ? 1 - (1 - PRESSED_SCALE) * depth.get() : 1);
    return [{ translateX: dotX(pose.get(), index) }, { scale }, { translateX: -ICON / 2 }, { translateY: -ICON / 2 }];
  });
  return (
    <Group transform={transform} opacity={shown}>
      {ICONS[index].map((stroke, n) => (
        <Path key={n} path={stroke} end={shown} style="stroke" strokeWidth={ICON_STROKE} strokeCap="round" strokeJoin="round" color="#ffffff" />
      ))}
    </Group>
  );
}

function Lines({ lines }: { lines: number[][] }) {
  return lines.map(([left, top, width, height]) => (
    <View key={top} style={[styles.line, { left, top, width, height, borderRadius: height / 2 }]} />
  ));
}

/**
 * A two-tab bar whose top edge dips into a bowl that holds a round button. Pressing the button
 * throws it out of the bowl, which flattens behind it, and stretches it into a pill; the white dot
 * the plus shrank into divides into four, which turn into the pill's icons. Closing runs it back.
 */
export function LiquidActionPill({
  titles = ['Home', 'Profile'],
  actionLabels = ['Camera', 'Photos', 'Insights', 'Close'],
  background = ['#B3A3B9', '#CDBACB'],
  accent = '#7B16E8',
  tint = '#7A2BE2',
  barColor = '#FFFFFF',
  autoPlay = false,
  onTabChange,
  onAction,
  onOpenChange,
}: Props) {
  const reducedMotion = useReducedMotion();
  const insets = useSafeAreaInsets();
  const window = useWindowDimensions();
  // The screen as laid out; the window's size stands in until then.
  const [laidOut, setLaidOut] = useState<{ width: number; height: number } | null>(null);
  const size = laidOut ?? window;
  const [open, setOpen] = useState(false);
  const [tab, setTab] = useState(0);
  // Changes on every touch, which restarts the autoPlay loop behind a pause.
  const [touches, setTouches] = useState(0);

  // 0 with the button in its bowl, 1 with the pill open; it moves at a constant speed.
  const way = useSharedValue(0);
  // 1 while the pill opens, 0 while it closes: the bowl behaves differently each way.
  const opening = useSharedValue(1);
  // 0 on the first page, 1 on the second.
  const page = useSharedValue(0);
  // The pill icon under a finger, and how far it is pressed in.
  const held = useSharedValue(-1);
  const depth = useSharedValue(0);

  const barWidth = size.width - BAR_MARGIN * 2;
  const barTop = size.height - Math.max(insets.bottom, BAR_BOTTOM) - BAR_HEIGHT;
  const pillY = barTop - BUTTON_RAISE - RISE;

  const move = (next: boolean) => {
    setOpen(next);
    onOpenChange?.(next);
    scheduleOnUI(
      (target: number, still: boolean) => {
        'worklet';
        const at = way.get();
        // Turned round half way, the bowl changes over smoothly; from either end there is nothing to smooth.
        const settled = at <= 0 || at >= 1;
        opening.set(settled || still ? target : withTiming(target, { duration: TURN * 1000, easing: Easing.linear, reduceMotion: ReduceMotion.Never }));
        way.set(
          withTiming(target, {
            duration: Math.abs(target - at) * (still ? REDUCED_DURATION : DURATION) * 1000,
            easing: Easing.linear,
            reduceMotion: ReduceMotion.Never,
          }),
        );
      },
      next ? 1 : 0,
      reducedMotion,
    );
  };

  const choose = (index: number) => {
    setTab(index);
    onTabChange?.(index);
    page.set(withTiming(index, { duration: TAB_FADE * 1000, easing: EASE, reduceMotion: ReduceMotion.Never }));
  };

  // The loop reads the latest functions when its timers fire.
  const latest = useRef({ move, choose });
  useEffect(() => {
    latest.current = { move, choose };
  });

  useEffect(() => {
    if (!autoPlay) return;
    let timers: ReturnType<typeof setTimeout>[] = [];
    const cycle = (origin: number) => {
      const at = (seconds: number, run: () => void) => setTimeout(run, origin + seconds * 1000 - Date.now());
      timers = [
        at(OPEN_AT, () => latest.current.move(true)),
        at(CLOSE_AT, () => latest.current.move(false)),
        at(PROFILE_AT, () => latest.current.choose(1)),
        at(HOME_AT, () => latest.current.choose(0)),
        at(LOOP, () => cycle(origin + LOOP * 1000)),
      ];
    };
    if (touches === 0) cycle(Date.now());
    else {
      timers = [
        setTimeout(() => {
          // Back to rest; the loop's first second covers the way there.
          latest.current.move(false);
          latest.current.choose(0);
          cycle(Date.now());
        }, USER_HOLD * 1000),
      ];
    }
    return () => timers.forEach(clearTimeout);
  }, [autoPlay, touches]);

  /** A touch takes over from the autoPlay loop, which starts again from rest a while later. */
  const touched = () => setTouches((n) => n + 1);

  // Under reduced motion nothing travels: the button fades out, and the pill fades in where it belongs.
  const pose = useDerivedValue(() => (reducedMotion ? (way.get() < 0.5 ? 0 : 1) : way.get()));
  const pillOpacity = useDerivedValue(() => (reducedMotion ? Math.abs(way.get() * 2 - 1) : 1));

  const bar = usePathValue((b) => {
    'worklet';
    const p = pose.get();
    // The bowl stays under reduced motion.
    const bowl = reducedMotion ? BOWL_DEPTH : bowlDepth(p, opening.get());
    const half = reducedMotion ? BOWL_HALF : bowlHalf(p, opening.get());
    const w = barWidth / 2;
    const r = BAR_RADIUS;
    const k = r * KAPPA;
    b.moveTo(-w + r, 0)
      .lineTo(-half, 0)
      .cubicTo(-half * BOWL_SHOULDER, 0, -half * BOWL_FLOOR, bowl, 0, bowl)
      .cubicTo(half * BOWL_FLOOR, bowl, half * BOWL_SHOULDER, 0, half, 0)
      .lineTo(w - r, 0)
      .cubicTo(w - r + k, 0, w, r - k, w, r)
      .lineTo(w, BAR_HEIGHT - r)
      .cubicTo(w, BAR_HEIGHT - r + k, w - r + k, BAR_HEIGHT, w - r, BAR_HEIGHT)
      .lineTo(-w + r, BAR_HEIGHT)
      .cubicTo(-w + r - k, BAR_HEIGHT, -w, BAR_HEIGHT - r + k, -w, BAR_HEIGHT - r)
      .lineTo(-w, r)
      .cubicTo(-w, r - k, -w + r - k, 0, -w + r, 0)
      .close();
  });

  const pillTransform = useDerivedValue(() => [{ translateY: -BUTTON_RAISE + lift(pose.get()) }]);
  const pill = usePathValue((b) => {
    'worklet';
    const width = pillWidth(pose.get());
    const height = pillHeight(pose.get());
    b.addRRect({ rect: { x: -width / 2, y: -height / 2, width, height }, rx: height / 2, ry: height / 2 });
  });
  const plus = usePathValue((b) => {
    'worklet';
    const arm = PLUS_ARM * plusScale(pose.get());
    b.moveTo(-arm, 0).lineTo(arm, 0).moveTo(0, -arm).lineTo(0, arm);
  });
  const plusOpacity = useDerivedValue(() => (plusScale(pose.get()) > 0 ? 1 : 0));
  // What joins two dots that have not parted far: a neck that thins as they move apart, and snaps.
  const necks = usePathValue((b) => {
    'worklet';
    const p = pose.get();
    const r = dotRadius(p);
    for (let i = 0; i < 3; i++) {
      const from = dotX(p, i);
      const to = dotX(p, i + 1);
      const gap = to - from;
      if (gap <= 0.5 || gap >= NECK_SNAP) continue;
      const joined = 1 - gap / NECK_SNAP;
      const x = r * Math.cos(NECK_SPREAD * joined);
      const y = r * Math.sin(NECK_SPREAD * joined);
      // The curve's control point, for a waist `joined` times as thick as the neck's ends.
      const c = y * (2 * joined - 1);
      const middle = (from + to) / 2;
      b.moveTo(from + x, -y)
        .quadTo(middle, -c, to - x, -y)
        .lineTo(to - x, y)
        .quadTo(middle, c, from + x, y)
        .close();
    }
  });

  const houseFill = useDerivedValue(() => TAB_FILL * (1 - page.get()));
  const personFill = useDerivedValue(() => TAB_FILL * page.get());
  const homeStyle = useAnimatedStyle(() => ({ opacity: 1 - page.get() }));
  const profileStyle = useAnimatedStyle(() => ({ opacity: page.get() }));

  const onLayout = (event: LayoutChangeEvent) => {
    const { width, height } = event.nativeEvent.layout;
    setLaidOut({ width, height });
  };

  const tabX = barWidth / 2 - TAB_INSET;
  const tabIcon = (x: number) => [{ translateX: x - TAB_ICON / 2 }, { translateY: (BAR_HEIGHT - TAB_ICON) / 2 }];

  const pages = [HOME_CARDS, PROFILE_CARDS].map((cards, index) => (
    <Animated.View
      key={index}
      pointerEvents="none"
      accessibilityElementsHidden={tab !== index}
      importantForAccessibility={tab === index ? 'auto' : 'no-hide-descendants'}
      style={[styles.fill, { paddingTop: insets.top + TITLE_TOP }, index === 0 ? homeStyle : profileStyle]}
    >
      <Text style={styles.title} numberOfLines={1} accessibilityRole="header">
        {titles[index]}
      </Text>
      {cards.map((card, n) => (
        <View key={n} style={[styles.card, { height: card.height, marginTop: n === 0 ? CARDS_TOP - TITLE_TOP - TITLE_LINE : CARD_GAP }]}>
          {index === 1 && n === 0 && <View style={styles.avatar} />}
          <Lines lines={card.lines} />
        </View>
      ))}
    </Animated.View>
  ));

  return (
    <View style={styles.screen} onLayout={onLayout}>
      <StatusBar style="light" />
      <LinearGradient style={styles.fill} colors={background} start={{ x: 0, y: 0 }} end={{ x: 1, y: 1 }} />
      {pages}

      {/* A touch on the page closes the pill. */}
      {open && (
        <Pressable
          style={styles.fill}
          accessibilityLabel={actionLabels[3]}
          onPress={() => {
            touched();
            move(false);
          }}
        />
      )}

      <Canvas style={styles.fill} pointerEvents="none">
        <Group transform={[{ translateX: size.width / 2 }, { translateY: barTop }]}>
          <Path path={bar} color={BAR_SHADOW} transform={[{ translateY: BAR_SHADOW_Y }]}>
            <BlurMask blur={BAR_SHADOW_BLUR} style="normal" />
          </Path>
          <Path path={bar} color={barColor} />

          <Group transform={tabIcon(-tabX)}>
            <Path path={HOUSE} color={tint} opacity={houseFill} />
            <Path path={HOUSE} color={tint} style="stroke" strokeWidth={TAB_STROKE} strokeJoin="round" />
            <Path path={DOOR} color={tint} style="stroke" strokeWidth={TAB_STROKE} strokeCap="round" strokeJoin="round" />
          </Group>
          <Group transform={tabIcon(tabX)}>
            <Path path={PERSON} color={tint} opacity={personFill} />
            <Path path={PERSON} color={tint} style="stroke" strokeWidth={TAB_STROKE} />
          </Group>

          <Group transform={pillTransform} opacity={pillOpacity}>
            <Path path={pill} color={accent} opacity={PILL_SHADOW_ALPHA} transform={[{ translateY: PILL_SHADOW_Y }]}>
              <BlurMask blur={PILL_SHADOW_BLUR} style="normal" />
            </Path>
            <Path path={pill} color={accent} />
            <Path path={plus} color="#ffffff" opacity={plusOpacity} style="stroke" strokeWidth={PLUS_STROKE} strokeCap="round" />
            <Path path={necks} color="#ffffff" />
            {ICONS.map((_, index) => (
              <Dot key={index} index={index} pose={pose} />
            ))}
            {ICONS.map((_, index) => (
              <Glyph key={index} index={index} pose={pose} held={held} depth={depth} />
            ))}
          </Group>
        </Group>
      </Canvas>

      {[0, 1].map((index) => (
        <Pressable
          key={index}
          style={[styles.tab, { top: barTop, left: size.width / 2 + (index === 0 ? -tabX : tabX) - TAB_HIT / 2 }]}
          accessibilityRole="tab"
          accessibilityLabel={titles[index]}
          accessibilityState={{ selected: tab === index }}
          onPress={() => {
            touched();
            if (tab === index) return;
            select();
            choose(index);
          }}
        />
      ))}

      {open ? (
        ICONS.map((_, index) => (
          <Pressable
            key={index}
            style={[styles.action, { top: pillY - BUTTON / 2, left: size.width / 2 + (index - 1.5) * ICON_GAP - ICON_GAP / 2 }]}
            accessibilityRole="button"
            accessibilityLabel={actionLabels[index]}
            onPressIn={() => {
              held.set(index);
              depth.set(withTiming(1, { duration: 80, reduceMotion: ReduceMotion.Never }));
            }}
            onPressOut={() => depth.set(withTiming(0, { duration: 160, reduceMotion: ReduceMotion.Never }))}
            onPress={() => {
              touched();
              tap();
              if (index < 3) onAction?.(index);
              move(false);
            }}
          />
        ))
      ) : (
        <Pressable
          style={[styles.button, { top: barTop - BUTTON_RAISE - BUTTON_HIT / 2, left: (size.width - BUTTON_HIT) / 2 }]}
          accessibilityRole="button"
          accessibilityLabel="Actions"
          accessibilityState={{ expanded: false }}
          onPress={() => {
            touched();
            tap();
            move(true);
          }}
        />
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  screen: {
    flex: 1,
  },
  fill: {
    position: 'absolute',
    left: 0,
    right: 0,
    top: 0,
    bottom: 0,
  },
  title: {
    marginLeft: BAR_MARGIN,
    height: TITLE_LINE,
    color: '#ffffff',
    fontSize: 34,
    lineHeight: TITLE_LINE,
    fontWeight: '700',
    includeFontPadding: false,
  },
  card: {
    marginHorizontal: BAR_MARGIN,
    borderRadius: 24,
    borderCurve: 'continuous',
    backgroundColor: FROST,
  },
  line: {
    position: 'absolute',
    backgroundColor: LINE,
  },
  avatar: {
    position: 'absolute',
    left: 20,
    top: 49,
    width: AVATAR,
    height: AVATAR,
    borderRadius: AVATAR / 2,
    backgroundColor: LINE,
  },
  tab: {
    position: 'absolute',
    width: TAB_HIT,
    height: BAR_HEIGHT,
  },
  action: {
    position: 'absolute',
    width: ICON_GAP,
    height: BUTTON,
  },
  button: {
    position: 'absolute',
    width: BUTTON_HIT,
    height: BUTTON_HIT,
  },
});
