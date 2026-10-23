import {
  Canvas,
  Glyphs,
  Group,
  Image as SkiaImage,
  LinearGradient,
  matchFont,
  Paint,
  Rect,
  RuntimeShader,
  Skia,
  type SkFont,
  type SkImage,
  type SkPath,
  Text as SkiaText,
  useImage,
  vec,
} from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { useEffect, useMemo, useState } from 'react';
import { type LayoutChangeEvent, PixelRatio, Pressable, StyleSheet, Text, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  Easing,
  type SharedValue,
  useAnimatedReaction,
  useAnimatedStyle,
  useDerivedValue,
  useFrameCallback,
  useReducedMotion,
  useSharedValue,
  withSpring,
  withTiming,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import Svg, { Path } from 'react-native-svg';
import { scheduleOnRN } from 'react-native-worklets';

type Props = {
  /** Pages through the onboarding with the glass rod, presses the button and starts over. */
  autoPlay?: boolean;
  /** Called when "Get started" is pressed on the last page. */
  onDone?: () => void;
};

type Page = { photo: string; label: string; title: [string, string]; body: [string, string]; tone: string };

// The photos fill the screen, about 400 pt wide; 1200 px covers that at 3x.
const photoUri = (id: string) => `https://images.unsplash.com/${id}?auto=format&fit=crop&w=1200&q=80`;

const PAGES: Page[] = [
  {
    photo: photoUri('photo-1511860810434-a92f84c6f01e'),
    label: 'DUNES · 01',
    title: ['Read the', 'wind.'],
    body: ['Live dune maps that shift with the', 'sand, redrawn every dawn.'],
    tone: '#b8643a',
  },
  {
    photo: photoUri('photo-1505521377774-103a8cc2f735'),
    label: 'CANYONS · 02',
    title: ['Walk into', 'the light.'],
    body: ['Slot canyons rated by the hour the', 'sun reaches the floor.'],
    tone: '#8c3f22',
  },
  {
    photo: photoUri('photo-1629659343117-237c364e69ed'),
    label: 'NIGHT · 03',
    title: ['Sleep under', 'the stars.'],
    body: ['Camps far from any road, with the', 'moon and wind for tonight.'],
    tone: '#1d2a44',
  },
];
const COUNT = PAGES.length;

const SANS = process.env.EXPO_OS === 'ios' ? 'Avenir Next' : 'sans-serif';
const INK = '#0d0907';
const CREAM = '#f6efe6';

// The rod: a glass cylinder lying across the screen, ROD_RADIUS pt either side of its centre
// line. At rest a PEEK pt sliver of it waits at the right edge, breathing by PEEK_BREATH pt.
const ROD_RADIUS = 46;
const PEEK = 18;
const PEEK_BREATH = 4;
const PEEK_PERIOD = 2.6; // s
// It leans with its own speed (rad per page/s) and with the finger's vertical travel (rad per
// screen height), never past MAX_TILT; its colour fringes widen with speed.
const LEAN_PER_SPEED = 0.07;
const TILT_PER_HEIGHT = 0.55;
const MAX_TILT = 0.2;
const TILT_FOLLOW = 10; // 1/s, how fast the lean catches up
const CHROMA_REST = 0.035;
const CHROMA_FAST = 0.09;

// Paging: the photo behind the rod slides PHOTO_SHIFT and the text TEXT_SHIFT screen widths, and
// the incoming photo settles from INCOMING_SCALE.
const PHOTO_SHIFT = 0.24;
const TEXT_SHIFT = 0.12;
const INCOMING_SCALE = 1.08;

// The finger: a page per rod travel (so the rod stays under it), RUBBER pages at most past
// either end, and a release that adds PROJECT s of the finger's speed.
const RUBBER = 0.18;
const PROJECT = 0.22;
const SETTLE_SPRING = { duration: 620, dampingRatio: 0.86 };
const TILT_SPRING = { duration: 520, dampingRatio: 0.7 };
const AUTO_EASE = Easing.bezier(0.65, 0, 0.35, 1);
const AUTO_PAGE_TIME = 1400; // ms
const SKIP_TIME = 900; // ms
const REDUCED_TIME = 320; // ms

// autoPlay, in seconds of a LOOP that rests on the first page: it pages forward at each of
// AUTO_GO, the last one wrapping from the third page round to the first, and presses the button.
const LOOP = 12;
const AUTO_GO = [0.8, 4.4, 8.4];
const AUTO_PRESS = 7.4;
// Where the clock picks up when a touch's pause ends, by page: the rest after each arrival.
const RESUME_AT = [0, 2.4, 5.9];
const USER_HOLD = 3.5;

// Dots: 8 pt, the current one stretched to 30 pt.
const DOT = 8;
const DOT_ACTIVE = 30;
const DOT_GAP = 7;

/**
 * The glass: everything under the rod is seen through a cylinder. Across it the image is flipped
 * and squeezed (the centre shows a strip 0.55 times as wide, the edges reach 1.45 radii to the
 * other side), red and blue split a little, the edges darken as the glass turns away, and two
 * highlights run down it. Outside the rod, a soft shadow falls on the page to its right.
 * Coordinates arrive in pixels; `pd` turns them into points.
 */
const ROD = Skia.RuntimeEffect.Make(`
uniform shader image;
uniform float pd;
uniform float2 size;
uniform float2 center;
uniform float angle;
uniform float radius;
uniform float chroma;
uniform float shade;

half4 main(float2 xy) {
  float2 p = xy / pd;
  float2 n = float2(cos(angle), sin(angle));
  float2 t = float2(-n.y, n.x);
  float u = dot(p - center, n);
  float s = u / radius;
  if (abs(s) >= 1.0) {
    half4 c = image.eval(xy);
    float shadow = s > 1.0 ? (1.0 - smoothstep(1.0, 1.9, s)) * 0.3 * shade : 0.0;
    return half4(c.rgb * (1.0 - shadow), c.a);
  }
  float z = sqrt(max(0.0, 1.0 - s * s));
  float bend = -s * (0.55 + 0.9 * (1.0 - z));
  float along = dot(p - center, t);
  float2 base = center + t * (along * (0.97 + 0.03 * z));
  float2 lo = float2(0.5);
  float2 hi = size - 0.5;
  float2 pr = clamp(base + n * radius * bend * (1.0 + chroma), lo, hi);
  float2 pg = clamp(base + n * radius * bend, lo, hi);
  float2 pb = clamp(base + n * radius * bend * (1.0 - chroma), lo, hi);
  float3 col = float3(image.eval(pr * pd).r, image.eval(pg * pd).g, image.eval(pb * pd).b);
  // A faint warm body to the glass, darker where it turns away.
  col = col * (0.9 + 0.1 * z) * float3(1.0, 0.985, 0.965);
  float fres = pow(1.0 - z, 2.4);
  col = mix(col, float3(0.05, 0.04, 0.035), fres * 0.6);
  // Light from the upper left: a broad highlight, a thin one on the far side, a lit rim.
  float down = clamp(along / size.y + 0.5, 0.0, 1.0);
  float spec = exp(-pow((s + 0.5) / 0.1, 2.0)) * (0.5 - 0.2 * down) + exp(-pow((s - 0.8) / 0.045, 2.0)) * 0.2;
  float rim = smoothstep(0.9, 1.0, abs(s)) * 0.28;
  col += float3(spec + rim);
  return half4(half3(clamp(col, 0.0, 1.0)), 1.0);
}
`)!;

function clamp(x: number, lo: number, hi: number) {
  'worklet';
  return Math.min(hi, Math.max(lo, x));
}

function smooth(x: number) {
  'worklet';
  const t = clamp(x, 0, 1);
  return t * t * (3 - 2 * t);
}

/** How far page `i` is from progress `p`, going round (the autoPlay wrap joins the ends). */
function ringDistance(p: number, i: number) {
  'worklet';
  const d = Math.abs((((p - i) % COUNT) + COUNT) % COUNT);
  return Math.min(d, COUNT - d);
}

/** Resistance past either end: at most RUBBER pages, however far the finger goes. */
function rubber(raw: number) {
  'worklet';
  if (raw < 0) return -RUBBER * (1 - Math.exp(raw / RUBBER));
  const over = raw - (COUNT - 1);
  if (over > 0) return COUNT - 1 + RUBBER * (1 - Math.exp(-over / RUBBER));
  return raw;
}

/**
 * Which pages the rod is between: page `a` on its left, `b` on its right, and how far it has come
 * (0 at the right edge, 1 off the left). Past the last page `b` is the first page when the
 * autoPlay wraps round, else the last page again (a rubber band); before the first, both are it.
 */
function segment(p: number, wrap: boolean) {
  'worklet';
  const n = Math.floor(p);
  const f = p - n;
  const a = n < 0 ? 0 : n % COUNT;
  const b = n < 0 ? 0 : n + 1 >= COUNT ? (wrap ? (n + 1) % COUNT : COUNT - 1) : n + 1;
  return { a, b, f };
}

/** The tracked caps label, glyph by glyph (Skia text has no letter spacing). */
function trackedGlyphs(font: SkFont, text: string, tracking: number) {
  const ids = font.getGlyphIDs(text);
  const widths = font.getGlyphWidths(ids);
  let x = 0;
  return ids.map((id, i) => {
    const glyph = { id, pos: vec(x, 0) };
    x += widths[i] + tracking;
    return glyph;
  });
}

type Layout = {
  width: number;
  height: number;
  margin: number;
  buttonTop: number;
  dotsY: number;
  label: number;
  title: [number, number];
  body: [number, number];
  titleSize: number;
};

function layoutFor(width: number, height: number, bottom: number): Layout {
  const margin = 28;
  const buttonTop = height - Math.max(bottom, 16) - 16 - 56;
  const dotsY = buttonTop - 30;
  const body1 = dotsY - 36;
  const body0 = body1 - 23;
  const titleSize = Math.min(46, (width - margin * 2) / 7.2);
  const title1 = body0 - 38;
  const title0 = title1 - titleSize * 1.04;
  const label = title0 - titleSize - 12;
  return { width, height, margin, buttonTop, dotsY, label, title: [title0, title1], body: [body0, body1], titleSize };
}

type Fonts = { label: SkFont; title: SkFont; body: SkFont };

type Motion = {
  progress: SharedValue<number>;
  wrap: SharedValue<boolean>;
  reduced: boolean;
};

/**
 * One page: its photo, filling the screen, and its words. It shows on the rod's left when it is
 * the page being left, on its right when it is the page coming in, and nowhere otherwise.
 */
function PageLayer({ index, page, image, layout, fonts, motion, cut }: {
  index: number;
  page: Page;
  image: SkImage | null;
  layout: Layout;
  fonts: Fonts;
  motion: Motion;
  cut: { left: SharedValue<SkPath>; right: SharedValue<SkPath> };
}) {
  const { width, height, margin } = layout;
  const { progress, wrap, reduced } = motion;
  const labelGlyphs = useMemo(() => trackedGlyphs(fonts.label, page.label, 2.4), [fonts.label, page.label]);

  // Which side of the rod this page is on: -1 left (leaving), 1 right (coming in), 0 hidden.
  const side = useDerivedValue(() => {
    const { a, b } = segment(progress.get(), wrap.get());
    if (index === a && index === b) return 2;
    if (index === a) return -1;
    if (index === b) return 1;
    return 0;
  });
  const clip = useDerivedValue(() => {
    const s = side.get();
    if (s === 0) return Skia.PathBuilder.Make().build();
    if (s === 2 || reduced) return Skia.PathBuilder.Make().addRect({ x: 0, y: 0, width, height }).build();
    return s < 0 ? cut.left.get() : cut.right.get();
  });
  // Under reduced motion the pages crossfade instead: the incoming one fades in over the other,
  // or, when it is drawn underneath (the wrap round to the first page), the outgoing one fades out.
  const opacity = useDerivedValue(() => {
    const s = side.get();
    if (s === 0) return 0;
    if (!reduced || s === 2) return 1;
    const { a, b, f } = segment(progress.get(), wrap.get());
    if (s === 1) return b > a ? f : 1;
    return b > a ? 1 : 1 - f;
  });
  // The page being left drifts left behind the rod, the one coming in drifts in from the right
  // and settles from a little closer.
  const travel = useDerivedValue(() => {
    const s = side.get();
    if (reduced || s === 0 || s === 2) return 0;
    const f = segment(progress.get(), wrap.get()).f;
    return s < 0 ? -f : 1 - f;
  });
  const photoTransform = useDerivedValue(() => {
    const t = travel.get();
    const zoom = t > 0 ? 1 + (INCOMING_SCALE - 1) * t : 1;
    return [
      { translateX: width / 2 + t * PHOTO_SHIFT * width },
      { translateY: height / 2 },
      { scale: zoom },
      { translateX: -width / 2 },
      { translateY: -height / 2 },
    ];
  });
  const textTransform = useDerivedValue(() => [{ translateX: travel.get() * TEXT_SHIFT * width }]);

  return (
    <Group clip={clip} opacity={opacity}>
      <Group transform={photoTransform}>
        <Rect x={0} y={0} width={width} height={height} color={page.tone} />
        {image && <SkiaImage image={image} x={0} y={0} width={width} height={height} fit="cover" />}
      </Group>
      {/* The scrim belongs to the page, so the words keep their contrast through the glass. */}
      <Rect x={0} y={height * 0.42} width={width} height={height * 0.58}>
        <LinearGradient
          start={vec(0, height * 0.42)}
          end={vec(0, height)}
          colors={['#0d090700', '#0d0907b8', '#0d0907f2']}
          positions={[0, 0.5, 1]}
        />
      </Rect>
      <Rect x={0} y={0} width={width} height={150}>
        <LinearGradient start={vec(0, 0)} end={vec(0, 150)} colors={['#0d090766', '#0d090700']} />
      </Rect>
      <Group transform={textTransform}>
        <Group transform={[{ translateX: margin }, { translateY: layout.label }]}>
          <Glyphs font={fonts.label} glyphs={labelGlyphs} color="#e9b98f" />
        </Group>
        <SkiaText x={margin - 2} y={layout.title[0]} text={page.title[0]} font={fonts.title} color={CREAM} />
        <SkiaText x={margin - 2} y={layout.title[1]} text={page.title[1]} font={fonts.title} color={CREAM} />
        <SkiaText x={margin} y={layout.body[0]} text={page.body[0]} font={fonts.body} color="#f6efe6c4" />
        <SkiaText x={margin} y={layout.body[1]} text={page.body[1]} font={fonts.body} color="#f6efe6c4" />
      </Group>
    </Group>
  );
}

function Arrow({ color }: { color: string }) {
  return (
    <Svg width={18} height={18} viewBox="0 0 18 18">
      <Path d="M3 9h11.2M9.6 4.2 14.4 9l-4.8 4.8" stroke={color} strokeWidth={2.1} strokeLinecap="round" strokeLinejoin="round" fill="none" />
    </Svg>
  );
}

/** The page dots: the current one stretches into a bar, continuously with the paging. */
function Dots({ progress, top, left }: { progress: SharedValue<number>; top: number; left: number }) {
  return (
    <View pointerEvents="none" style={[styles.dots, { top: top - DOT / 2, left }]}>
      {PAGES.map((page, i) => (
        <Dot key={page.label} index={i} progress={progress} />
      ))}
    </View>
  );
}

function Dot({ index, progress }: { index: number; progress: SharedValue<number> }) {
  const style = useAnimatedStyle(() => {
    // Every dot before this one may be stretched too, so its x is the sum of their widths.
    let x = 0;
    for (let k = 0; k < index; k += 1) {
      x += DOT + (DOT_ACTIVE - DOT) * smooth(1 - ringDistance(progress.get(), k)) + DOT_GAP;
    }
    const w = smooth(1 - ringDistance(progress.get(), index));
    return {
      transform: [{ translateX: x }],
      width: DOT + (DOT_ACTIVE - DOT) * w,
      opacity: 0.38 + 0.62 * w,
    };
  });
  return <Animated.View style={[styles.dot, style]} />;
}

/**
 * An onboarding whose pages change under a glass rod. The rod lies across the screen: drag it
 * and it rolls over the page, showing the next one behind it and the seam between the two
 * flipped and squeezed in its glass, with colour fringes, highlights and a soft shadow. It
 * follows the finger, leans as it moves and tilts as the finger rises or falls; let go and it
 * springs on to the next page or back with the finger's speed. The page dots stretch with the
 * paging, and the button reads "Get started" on the last page.
 */
export function GlassRodOnboarding({ autoPlay = false, onDone }: Props) {
  const insets = useSafeAreaInsets();
  const reduced = useReducedMotion();
  const [size, setSize] = useState<{ width: number; height: number } | null>(null);
  const layout = useMemo(() => (size ? layoutFor(size.width, size.height, insets.bottom) : null), [size, insets.bottom]);
  const width = layout?.width ?? 1;
  const height = layout?.height ?? 1;
  const pd = PixelRatio.get();

  const images = [useImage(PAGES[0].photo), useImage(PAGES[1].photo), useImage(PAGES[2].photo)];
  const titleSize = layout?.titleSize ?? 46;
  const fonts = useMemo<Fonts>(
    () => ({
      label: matchFont({ fontFamily: SANS, fontSize: 12, fontWeight: '700' }),
      title: matchFont({ fontFamily: SANS, fontSize: titleSize, fontWeight: '700' }),
      body: matchFont({ fontFamily: SANS, fontSize: 16, fontWeight: '500' }),
    }),
    [titleSize],
  );

  const progress = useSharedValue(0);
  const wrap = useSharedValue(false);
  const peek = useSharedValue(reduced ? 0 : 1);
  const time = useSharedValue(0);
  const speed = useSharedValue(0); // pages/s, smoothed
  const lastProgress = useSharedValue(0);
  const lean = useSharedValue(0);
  const fingerTilt = useSharedValue(0);
  const pivotY = useSharedValue(0);
  const chroma = useSharedValue(CHROMA_REST);
  const press = useSharedValue(0);
  const dragFrom = useSharedValue(0);
  const dragging = useSharedValue(false);
  const clock = useSharedValue(0);
  const looping = useSharedValue(autoPlay);
  const hold = useSharedValue(0);
  const span = width + ROD_RADIUS * 2 - PEEK;

  useEffect(() => {
    looping.set(autoPlay);
    clock.set(0);
    hold.set(0);
  }, [autoPlay, clock, hold, looping]);

  useEffect(() => {
    pivotY.set(height / 2);
  }, [height, pivotY]);

  const changed = () => {
    if (process.env.EXPO_OS !== 'web') Haptics.selectionAsync().catch(() => {});
  };
  const finished = () => {
    if (process.env.EXPO_OS !== 'web') Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success).catch(() => {});
    onDone?.();
  };

  const goTo = (target: number, duration: number) => {
    'worklet';
    progress.set(withTiming(target, { duration: reduced ? REDUCED_TIME : duration, easing: AUTO_EASE }));
  };
  const pressButton = () => {
    'worklet';
    press.set(withTiming(1, { duration: 110 }, () => press.set(withTiming(0, { duration: 260 }))));
  };

  /** The rod's centre line, in pt: where it crosses the pivot height, and its tilt. */
  const rodX = useDerivedValue(() => {
    const { f } = segment(progress.get(), wrap.get());
    const sliver = (PEEK + PEEK_BREATH * Math.sin((time.get() * Math.PI * 2) / PEEK_PERIOD)) * peek.get();
    if (reduced) return width + ROD_RADIUS * 4;
    return width + ROD_RADIUS - sliver - f * (width + ROD_RADIUS * 2 - sliver);
  });
  const angle = useDerivedValue(() => clamp(lean.get() + fingerTilt.get(), -MAX_TILT, MAX_TILT));

  // The two half-planes either side of the rod's centre line.
  const cut = {
    left: useDerivedValue(() => {
      const a = angle.get();
      const nx = Math.cos(a);
      const ny = Math.sin(a);
      const far = height * 3 + width * 3;
      const cx = rodX.get();
      const cy = pivotY.get();
      return Skia.PathBuilder.Make()
        .moveTo(cx + ny * far, cy - nx * far)
        .lineTo(cx - ny * far, cy + nx * far)
        .lineTo(cx - ny * far - nx * far, cy + nx * far - ny * far)
        .lineTo(cx + ny * far - nx * far, cy - nx * far - ny * far)
        .close()
        .build();
    }),
    right: useDerivedValue(() => {
      const a = angle.get();
      const nx = Math.cos(a);
      const ny = Math.sin(a);
      const far = height * 3 + width * 3;
      const cx = rodX.get();
      const cy = pivotY.get();
      return Skia.PathBuilder.Make()
        .moveTo(cx + ny * far, cy - nx * far)
        .lineTo(cx - ny * far, cy + nx * far)
        .lineTo(cx - ny * far + nx * far, cy + nx * far + ny * far)
        .lineTo(cx + ny * far + nx * far, cy - nx * far + ny * far)
        .close()
        .build();
    }),
  };

  const uniforms = useDerivedValue(() => ({
    pd,
    size: [width, height],
    center: [rodX.get(), pivotY.get()],
    angle: angle.get(),
    radius: ROD_RADIUS,
    chroma: chroma.get(),
    shade: 1,
  }));

  useFrameCallback((frame) => {
    const dt = Math.min(frame.timeSincePreviousFrame ?? 16, 64) / 1000;
    if (dt <= 0) return;
    time.set(time.get() + dt);
    // The rod's own speed, smoothed: it leans into its travel and its fringes widen.
    const p = progress.get();
    const v = (p - lastProgress.get()) / dt;
    lastProgress.set(p);
    const follow = 1 - Math.exp(-dt * TILT_FOLLOW);
    speed.set(speed.get() + (v - speed.get()) * follow);
    lean.set(lean.get() + (clamp(speed.get() * LEAN_PER_SPEED, -MAX_TILT, MAX_TILT) - lean.get()) * follow);
    chroma.set(CHROMA_REST + (CHROMA_FAST - CHROMA_REST) * clamp(Math.abs(speed.get()) / 2.5, 0, 1));
    // The sliver waits at the right edge while there is a page to go to.
    const resting = !dragging.get() && Math.abs(p - Math.round(p)) < 0.002 && Math.round(p) < COUNT - 1;
    const want = reduced ? 0 : resting ? 1 : 0;
    peek.set(peek.get() + (want - peek.get()) * (1 - Math.exp(-dt * 6)));

    if (hold.get() > 0) {
      hold.set(hold.get() - dt);
      if (hold.get() <= 0 && looping.get()) {
        // Pick the script up from the page the finger left it on.
        const page = clamp(Math.round(progress.get()), 0, COUNT - 1);
        if (Math.abs(progress.get() - page) > 0.001) goTo(page, 500);
        clock.set(RESUME_AT[page]);
      }
      return;
    }
    if (!looping.get()) return;
    const before = clock.get();
    const now = before + dt;
    for (let k = 0; k < AUTO_GO.length; k += 1) {
      if (before < AUTO_GO[k] && now >= AUTO_GO[k]) {
        const from = Math.round(progress.get());
        if (from >= COUNT - 1) wrap.set(true);
        progress.set(withTiming(from + 1, { duration: reduced ? REDUCED_TIME : AUTO_PAGE_TIME, easing: AUTO_EASE }, (done) => {
          // Round the wrap back to the first page: the same picture, so nothing moves.
          if (done && progress.get() >= COUNT - 0.0001) {
            progress.set(progress.get() - COUNT);
            lastProgress.set(progress.get());
            wrap.set(false);
          }
        }));
      }
    }
    if (before < AUTO_PRESS && now >= AUTO_PRESS) pressButton();
    clock.set(now >= LOOP ? now - LOOP : now);
  });

  const touched = () => {
    'worklet';
    hold.set(USER_HOLD);
  };

  /** Brings a progress the autoPlay left past the last page (the wrap) back into range. */
  const settleWrap = () => {
    'worklet';
    // Only the autoPlay's wrap: a finger's rubber band past the last page is a different thing.
    const p = progress.get();
    if (wrap.get()) {
      // Seen as the step from before the first page to it: the same picture.
      const q = p > COUNT - 1 ? p - COUNT : p;
      wrap.set(false);
      progress.set(q);
      lastProgress.set(q);
      progress.set(withSpring(Math.max(0, Math.round(q)), SETTLE_SPRING));
      return true;
    }
    return false;
  };

  const pan = Gesture.Pan()
    .activeOffsetX([-10, 10])
    .failOffsetY([-24, 24])
    .onBegin(() => {
      touched();
    })
    .onStart((event) => {
      if (settleWrap()) return;
      progress.set(progress.get());
      dragFrom.set(progress.get());
      dragging.set(true);
      pivotY.set(withSpring(clamp(event.y, height * 0.15, height * 0.85), { duration: 260, dampingRatio: 1 }));
    })
    .onUpdate((event) => {
      touched();
      if (!dragging.get()) return;
      progress.set(rubber(dragFrom.get() - event.translationX / span));
      fingerTilt.set(clamp((event.translationY / height) * TILT_PER_HEIGHT, -MAX_TILT, MAX_TILT));
    })
    .onEnd((event) => {
      if (!dragging.get()) return;
      const from = Math.round(dragFrom.get());
      const velocity = -event.velocityX / span;
      const projected = progress.get() + velocity * PROJECT;
      const target = clamp(Math.round(clamp(projected, from - 1, from + 1)), 0, COUNT - 1);
      progress.set(withSpring(target, { ...SETTLE_SPRING, velocity }));
      if (target !== from) scheduleOnRN(changed);
    })
    .onFinalize(() => {
      dragging.set(false);
      fingerTilt.set(withSpring(0, TILT_SPRING));
      pivotY.set(withSpring(height / 2, TILT_SPRING));
    });

  /** Springs a page on or back; true when the page changed. */
  const step = (direction: number) => {
    'worklet';
    touched();
    if (settleWrap()) return false;
    const from = Math.round(progress.get());
    const target = clamp(from + direction, 0, COUNT - 1);
    if (target === from) return false;
    progress.set(withSpring(target, SETTLE_SPRING));
    return true;
  };

  // Taps on the photo: the right 40% goes on, the left 25% goes back.
  const tap = Gesture.Tap()
    .maxDuration(400)
    .onEnd((event, success) => {
      if (!success || !layout) return;
      if (event.y > layout.dotsY - 20) return;
      const direction = event.x > width * 0.6 ? 1 : event.x < width * 0.25 ? -1 : 0;
      if (direction !== 0 && step(direction)) scheduleOnRN(changed);
    });

  const onButton = () => {
    hold.set(USER_HOLD);
    const page = Math.round(progress.get());
    if (page >= COUNT - 1) finished();
    else if (step(1)) changed();
  };
  const onSkip = () => {
    hold.set(USER_HOLD);
    if (Math.round(progress.get()) >= COUNT - 1) return;
    goTo(COUNT - 1, SKIP_TIME);
    changed();
  };

  const buttonStyle = useAnimatedStyle(() => ({ transform: [{ scale: 1 - press.get() * 0.035 }] }));
  const lastness = useDerivedValue(() => smooth(1 - ringDistance(progress.get(), COUNT - 1)));
  // The labels hand over without overlapping: "Continue" leaves in the first half, "Get started"
  // arrives in the second.
  const continueStyle = useAnimatedStyle(() => ({
    opacity: 1 - smooth(lastness.get() * 2),
    transform: [{ translateY: -8 * lastness.get() }],
  }));
  const startStyle = useAnimatedStyle(() => ({
    opacity: smooth(lastness.get() * 2 - 1),
    transform: [{ translateY: 8 * (1 - lastness.get()) }],
  }));
  const skipStyle = useAnimatedStyle(() => ({ opacity: 1 - lastness.get() }));
  // For the screen reader only: whether the button reads "Get started".
  const [onLast, setOnLast] = useState(false);
  useAnimatedReaction(
    () => lastness.get() > 0.5,
    (now, before) => {
      if (now !== before) scheduleOnRN(setOnLast, now);
    },
  );

  const motion: Motion = { progress, wrap, reduced };

  return (
    <View
      style={styles.screen}
      onLayout={(event: LayoutChangeEvent) => {
        const { width: w, height: h } = event.nativeEvent.layout;
        if (w > 0 && h > 0) setSize({ width: w, height: h });
      }}
    >
      <GestureDetector gesture={Gesture.Exclusive(pan, tap)}>
        <View
          style={StyleSheet.absoluteFill}
          accessible
          accessibilityRole="adjustable"
          accessibilityLabel="Onboarding pages"
          accessibilityHint="Swipe left or right to change page"
          accessibilityActions={[{ name: 'increment' }, { name: 'decrement' }]}
          onAccessibilityAction={(event) => {
            if (step(event.nativeEvent.actionName === 'increment' ? 1 : -1)) changed();
          }}
        >
          {layout && (
            <Canvas style={StyleSheet.absoluteFill}>
              <Rect x={0} y={0} width={width} height={height} color={INK} />
              {/* Rendered at full pixel density, then seen through the rod. */}
              <Group transform={[{ scale: 1 / pd }]}>
                <Group layer={<Paint><RuntimeShader source={ROD} uniforms={uniforms} /></Paint>} transform={[{ scale: pd }]}>
                  {PAGES.map((page, i) => (
                    <PageLayer
                      key={page.label}
                      index={i}
                      page={page}
                      image={images[i]}
                      layout={layout}
                      fonts={fonts}
                      motion={motion}
                      cut={cut}
                    />
                  ))}
                </Group>
              </Group>
            </Canvas>
          )}
        </View>
      </GestureDetector>

      {layout && (
        <>
          <View pointerEvents="box-none" style={[styles.header, { top: insets.top + 10, left: layout.margin, right: layout.margin - 10 }]}>
            <View style={styles.brand} pointerEvents="none">
              <Svg width={22} height={22} viewBox="0 0 22 22">
                <Path d="M2 15.5c3.2-5.4 6.6-8 10-8 3.1 0 5.7 2 8 6" stroke={CREAM} strokeWidth={2.2} strokeLinecap="round" fill="none" />
                <Path d="M5.5 18.5c2.4-2.6 4.8-3.8 7.2-3.8 1.9 0 3.6.7 5.1 2" stroke="#e9b98f" strokeWidth={2.2} strokeLinecap="round" fill="none" />
              </Svg>
              <Text style={styles.brandName}>Sandline</Text>
            </View>
            <Animated.View
              style={skipStyle}
              // Faded out on the last page, so screen readers pass it by there too.
              accessibilityElementsHidden={onLast}
              importantForAccessibility={onLast ? 'no-hide-descendants' : 'auto'}
            >
              <Pressable onPress={onSkip} hitSlop={12} style={styles.skip} accessibilityRole="button">
                <Text style={styles.skipText}>Skip</Text>
              </Pressable>
            </Animated.View>
          </View>
          <Dots progress={progress} top={layout.dotsY} left={layout.margin} />
          <Animated.View style={[styles.buttonWrap, { top: layout.buttonTop, left: layout.margin, right: layout.margin }, buttonStyle]}>
            <Pressable
              onPress={onButton}
              onPressIn={() => press.set(withTiming(1, { duration: 110 }))}
              onPressOut={() => press.set(withTiming(0, { duration: 220 }))}
              style={styles.button}
              accessibilityRole="button"
              accessibilityLabel={onLast ? 'Get started' : 'Continue'}
            >
              <Animated.View style={[styles.buttonLabel, continueStyle]}>
                <Text style={styles.buttonText}>Continue</Text>
                <Arrow color={INK} />
              </Animated.View>
              <Animated.View style={[styles.buttonLabel, startStyle]}>
                <Text style={styles.buttonText}>Get started</Text>
              </Animated.View>
            </Pressable>
          </Animated.View>
        </>
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  screen: {
    flex: 1,
    overflow: 'hidden',
    backgroundColor: INK,
  },
  header: {
    position: 'absolute',
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  brand: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
  },
  brandName: {
    fontFamily: SANS,
    fontSize: 17,
    fontWeight: '700',
    letterSpacing: -0.2,
    color: CREAM,
  },
  skip: {
    paddingHorizontal: 10,
    paddingVertical: 6,
  },
  skipText: {
    fontFamily: SANS,
    fontSize: 15,
    fontWeight: '600',
    color: '#f6efe6d9',
  },
  dots: {
    position: 'absolute',
    height: DOT,
    width: DOT_ACTIVE + (DOT + DOT_GAP) * (COUNT - 1),
  },
  dot: {
    position: 'absolute',
    left: 0,
    top: 0,
    height: DOT,
    borderRadius: DOT / 2,
    backgroundColor: CREAM,
  },
  buttonWrap: {
    position: 'absolute',
    height: 56,
  },
  button: {
    flex: 1,
    borderRadius: 28,
    borderCurve: 'continuous',
    backgroundColor: CREAM,
    alignItems: 'center',
    justifyContent: 'center',
  },
  buttonLabel: {
    position: 'absolute',
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
  },
  buttonText: {
    fontFamily: SANS,
    fontSize: 17,
    fontWeight: '700',
    color: INK,
  },
});
