import { BlurMask, Canvas, Circle, Group, LinearGradient, Path, PathOp, Rect, RoundedRect, Skia, type SkPath, StrokeJoin, vec } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { Image } from 'expo-image';
import { Fragment, type ReactNode, useCallback, useEffect, useRef, useState } from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import Animated, {
  Easing,
  ReduceMotion,
  type SharedValue,
  useAnimatedStyle,
  useDerivedValue,
  useReducedMotion,
  useSharedValue,
  withSpring,
  withTiming,
} from 'react-native-reanimated';

type AlbumPhoto = {
  /** Address of the photo. */
  photo: string;
  /** The photo's main colour: shown until the photo arrives, and as its smudge behind the frosted front. */
  tint: string;
};

type Album = {
  title: string;
  count: number;
  /** Four photos, left to right. */
  photos: AlbumPhoto[];
  /** The flag on the postage stamp: a red disc on white, or a red cross edged in white on blue. */
  flag?: 'disc' | 'cross';
  /** The second sticker: a red shrine gate, or two snowy peaks. */
  emblem?: 'gate' | 'peak';
};

type Props = {
  /** The albums a tap flips through, in order. */
  albums?: Album[];
  /** The word after the count in the pill. */
  unit?: string;
  /** Presses, releases and taps by itself, flipping through the albums and back to the first. */
  autoPlay?: boolean;
  /** Called with the index of the album a tap flipped to. */
  onSelect?: (index: number) => void;
};

const WIDTH = 340;
const HEIGHT = 400;

const FOLDER_X = 44;
const FOLDER_Y = 96;
const FOLDER_W = 252;
const FOLDER_H = 190;
// Y of the two embossed lines.
const LINES = [160, 172];
// Top of the title, measured from the top of the component.
const TEXT_Y = 310;
const TEXT_SHIFT = 6;

const PHOTO_W = 104;
const PHOTO_H = 132;
const PHOTO_BORDER = 6;
const PHOTO_RADIUS = 18;
// Each photo at rest: centre x from the folder's centre, top from the folder's top, degrees.
const REST = [
  { x: -100, y: 12, r: -11 },
  { x: -52, y: -38, r: -3 },
  { x: 36, y: -27, r: 5 },
  { x: 102, y: 4, r: 9 },
];
// What a full press adds to that.
const LIFT = [
  { x: -16, y: -20, r: -7 },
  { x: -10, y: -30, r: -4 },
  { x: 10, y: -30, r: 4 },
  { x: 18, y: -22, r: 8 },
];
// Back to front: the outer photos lie over their neighbours.
const DRAW_ORDER = [2, 1, 0, 3];
// Inside the folder the photos pull towards the middle, sink below the tab and shrink a little.
const DROP_PULL = 0.45;
const DROP_Y = 44;
const DROP_SCALE = 0.92;

const FOLDER_GROW = 0.04;
const PHOTO_GROW = 0.03;
const STICKER_GROW = 0.07;
// Opacity of a photo's smudge on the frosted front.
const SMUDGE = 0.16;
// The two stickers on the folder: centre, degrees, and what a full press adds.
const STICKERS = [
  { x: 64, y: 78, r: -8, dx: -3, dy: -5, dr: -4 },
  { x: 188, y: 112, r: 8, dx: 4, dy: -6, dr: 5 },
];

const PRESS_SPRING = { mass: 1, stiffness: 210, damping: 17, reduceMotion: ReduceMotion.Never };

// The flip, in seconds from its start.
const DROP_TIME = 0.26;
const DROP_STAGGER = 0.03;
const RISE_AT = 0.3;
const RISE_STAGGER = 0.07;
const RISE_STIFFNESS = 180;
const RISE_DAMPING = 16;
const PEEL_TIME = 0.16;
const PEEL_GROW = 0.15;
const STICK_AT = [0.5, 0.62];
const STICK_FADE = 0.1;
const STICK_GROW = 0.4;
const STICK_STIFFNESS = 300;
const STICK_DAMPING = 18;
const TEXT_OUT = 0.2;
const TEXT_AT = 0.26;
const TEXT_IN = 0.3;
// A second flip is ignored until this one has settled.
const FLIP_TIME = 1.2;
// The flip's clock runs this long: every spring above has come to rest by then.
const FLIP_CLOCK = 2;
// Length of the cross-fade that replaces the flip under reduced motion.
const FADE_TIME = 0.25;

// A press shorter than this is a tap.
const TAP_WINDOW = 0.3;

// autoPlay (seconds into one album's turn): a long press, then a tap that flips to the next album.
const PRESS_AT = 0.8;
const RELEASE_AT = 2.8;
const TAP_AT = 3.4;
const TAP_HOLD = 0.12;
// One loop goes through the default two albums and is back at the first.
const LOOP = 10;
const CYCLE = LOOP / 2;
// After a touch the loop waits this long, then starts over from the first album.
const RESUME_AFTER = 2;

const HIDDEN = 0;
const LEAVING = 1;
const ENTERING = 2;

function photoUrl(id: string) {
  return `https://images.unsplash.com/photo-${id}?auto=format&fit=crop&w=300&h=390&q=80`;
}

const ALBUMS: Album[] = [
  {
    title: 'Japan 2024',
    count: 83,
    photos: [
      { photo: photoUrl('1540959733332-eab4deabeeaf'), tint: '#4e8690' },
      { photo: photoUrl('1493976040374-85c8e12f0c0e'), tint: '#5b5353' },
      { photo: photoUrl('1441974231531-c6227db76b6e'), tint: '#645f3d' },
      { photo: photoUrl('1519681393784-d120267933ba'), tint: '#3b4a66' },
    ],
  },
  {
    title: 'Iceland 2023',
    count: 124,
    photos: [
      { photo: photoUrl('1433086966358-54859d0ed716'), tint: '#58683f' },
      { photo: photoUrl('1531366936337-7c912a4589a7'), tint: '#4e5c80' },
      { photo: photoUrl('1504829857797-ddff29c27927'), tint: '#8e9da3' },
      { photo: photoUrl('1470071459604-3b5ec3a7fe05'), tint: '#6e756c' },
    ],
    flag: 'cross',
    emblem: 'peak',
  },
];

function outline(commands: string) {
  return Skia.Path.MakeFromSVGString(commands) ?? Skia.Path.Make();
}

// The front of the folder, 252 × 190, with its tab on the upper left.
const FOLDER = outline(
  'M 0 16 Q 0 0 16 0 L 100 0 C 120 0 120 28 140 28 L 236 28 Q 252 28 252 44 L 252 174 Q 252 190 236 190 L 16 190 Q 0 190 0 174 Z',
);

// A 54 × 50 stamp around its centre, with perforations bitten out of every side.
const STAMP = (() => {
  const paper = Skia.Path.Make().addRect(Skia.XYWHRect(-27, -25, 54, 50));
  const holes = Skia.Path.Make();
  for (let n = 0; n < 8; n++) {
    const x = -27 + (54 * (n + 0.5)) / 8;
    holes.addCircle(x, -25, 2.2).addCircle(x, 25, 2.2);
  }
  for (let n = 0; n < 7; n++) {
    const y = -25 + (50 * (n + 0.5)) / 7;
    holes.addCircle(-27, y, 2.2).addCircle(27, y, 2.2);
  }
  return Skia.Path.MakeFromOp(paper, holes, PathOp.Difference) ?? paper;
})();
const STAMP_FIELD = Skia.XYWHRect(-20, -18, 40, 36);

/** A sticker: its white backing, then its coloured parts. */
type EmblemArt = { backing: SkPath; layers: [SkPath, string][] };

/**
 * The art is given in a 60 × 56 box and moved to sit around its centre. The white backing follows the
 * silhouette 4.5 points out, with round corners.
 */
function emblem(silhouette: string, layers: [string, string][]): EmblemArt {
  const shape = outline(silhouette).offset(-30, -28);
  const rim = shape.copy().stroke({ width: 9, join: StrokeJoin.Round }) ?? shape;
  return {
    backing: Skia.Path.MakeFromOp(rim, shape, PathOp.Union) ?? shape,
    layers: layers.map(([commands, color]) => [outline(commands).offset(-30, -28), color]),
  };
}

const EMBLEMS = {
  gate: emblem('M 4 9 Q 30 15 56 9 L 54 17 L 51 24 L 51 29.5 L 46 29.5 L 47.5 52 L 12.5 52 L 14 29.5 L 9 29.5 L 9 24 L 6 17 Z', [
    ['M 15.5 17 L 21.5 17 L 19.5 50 L 12.5 50 Z M 38.5 17 L 44.5 17 L 47.5 50 L 40.5 50 Z', '#e8452c'],
    ['M 9 24 L 51 24 L 51 29.5 L 9 29.5 Z M 27.5 18 L 32.5 18 L 32.5 24 L 27.5 24 Z', '#e8452c'],
    ['M 4 9 Q 30 15 56 9 L 54 17 Q 30 21 6 17 Z', '#e8452c'],
    ['M 4 9 Q 30 15 56 9 L 55.3 11.8 Q 30 17.6 4.7 11.8 Z', '#2a2a2e'],
    ['M 11.5 46.5 L 20.5 46.5 L 20.5 52 L 11.5 52 Z M 39.5 46.5 L 48.5 46.5 L 48.5 52 L 39.5 52 Z', '#2a2a2e'],
  ]),
  peak: emblem('M 3 50 L 24 8 L 33.5 27 L 43 18 L 57 50 Z', [
    ['M 26 50 L 43 18 L 57 50 Z', '#7fa0cf'],
    ['M 43 18 L 47.6 28.5 L 44.5 26.5 L 42 30 L 39.6 27 L 37.4 28.6 Z', '#dbe7f4'],
    ['M 3 50 L 24 8 L 45 50 Z', '#3f66a3'],
    ['M 24 8 L 31.5 23 L 27.5 20.5 L 24 25.5 L 20.5 20.5 L 16.5 23 Z', '#dbe7f4'],
  ]),
};

function clamp01(t: number) {
  'worklet';
  return Math.min(1, Math.max(0, t));
}

function mix(a: number, b: number, t: number) {
  'worklet';
  return a + (b - a) * t;
}

function smooth(t: number) {
  'worklet';
  return t * t * (3 - 2 * t);
}

/** Ease-in that first backs up a little: the photos lift before they drop. */
function backIn(t: number) {
  'worklet';
  return t * t * (2.2 * t - 1.2);
}

/** Where a spring of mass 1 released at 0 towards 1 is after `t` seconds. */
function settle(t: number, stiffness: number, damping: number) {
  'worklet';
  if (t <= 0) return 0;
  const omega = Math.sqrt(stiffness);
  const decay = damping / 2;
  const ring = Math.sqrt(omega * omega - decay * decay);
  return 1 - Math.exp(-decay * t) * (Math.cos(ring * t) + (decay / ring) * Math.sin(ring * t));
}

function roleOf(album: number, from: number, to: number) {
  'worklet';
  return album === to ? ENTERING : album === from ? LEAVING : HIDDEN;
}

// --- The flip, as functions of the seconds since it began. `still` is reduced motion. ---

/** 0 down in the folder … 1 standing at rest; the spring carries it a little past 1 on the way up. */
function rise(role: number, slot: number, t: number, still: boolean) {
  'worklet';
  if (role === HIDDEN) return 0;
  if (still) return 1;
  if (role === LEAVING) return 1 - backIn(clamp01((t - slot * DROP_STAGGER) / DROP_TIME));
  return settle(t - RISE_AT - slot * RISE_STAGGER, RISE_STIFFNESS, RISE_DAMPING);
}

/** Under reduced motion the two albums cross-fade in place instead. */
function fade(role: number, t: number, still: boolean) {
  'worklet';
  if (role === HIDDEN) return 0;
  if (!still) return 1;
  return role === LEAVING ? 1 - clamp01(t / FADE_TIME) : clamp01(t / FADE_TIME);
}

function textOpacity(role: number, t: number, still: boolean) {
  'worklet';
  if (still || role === HIDDEN) return fade(role, t, still);
  return role === LEAVING ? 1 - clamp01(t / TEXT_OUT) : smooth(clamp01((t - TEXT_AT) / TEXT_IN));
}

/** A sticker is peeled off (grows and fades) and the next one is slapped on (lands from larger). */
function stickerLook(role: number, slot: number, t: number, still: boolean) {
  'worklet';
  if (still || role === HIDDEN) return { scale: 1, opacity: fade(role, t, still) };
  if (role === LEAVING) {
    const u = clamp01(t / PEEL_TIME);
    return { scale: 1 + PEEL_GROW * u, opacity: 1 - u };
  }
  const since = t - STICK_AT[slot];
  const landed = settle(since, STICK_STIFFNESS, STICK_DAMPING);
  return { scale: 1 + STICK_GROW * (1 - landed), opacity: clamp01(since / STICK_FADE) };
}

/**
 * Centre, rotation in degrees and scale of photo `i`, between its place inside the folder (`up` 0)
 * and its place standing behind it (`up` 1), fanned further out by `press`.
 */
function photoPose(i: number, up: number, press: number) {
  'worklet';
  const rest = REST[i];
  const lift = LIFT[i];
  return {
    x: FOLDER_X + FOLDER_W / 2 + mix(rest.x * DROP_PULL, rest.x + lift.x * press, up),
    y: FOLDER_Y + PHOTO_H / 2 + mix(DROP_Y, rest.y + lift.y * press, up),
    rotation: (rest.r + lift.r * press) * up,
    scale: mix(DROP_SCALE, 1 + PHOTO_GROW * press, up),
  };
}

type Motion = {
  /** 0 at rest … 1 pressed, on a spring. */
  press: SharedValue<number>;
  /** Seconds since the last flip began. */
  flip: SharedValue<number>;
  /** The album being left and the album shown. */
  from: SharedValue<number>;
  to: SharedValue<number>;
  still: boolean;
};

type Placed = Motion & { album: number; slot: number };

function PhotoCard({ item, album, slot, press, flip, from, to, still }: Placed & { item: AlbumPhoto }) {
  const style = useAnimatedStyle(() => {
    const role = roleOf(album, from.get(), to.get());
    const pose = photoPose(slot, rise(role, slot, flip.get(), still), press.get());
    return {
      opacity: fade(role, flip.get(), still),
      transform: [
        { translateX: pose.x - PHOTO_W / 2 },
        { translateY: pose.y - PHOTO_H / 2 },
        { rotate: `${pose.rotation}deg` },
        { scale: pose.scale },
      ],
    };
  });

  return (
    <Animated.View style={[styles.photo, style]}>
      <Image source={item.photo} style={[styles.image, { backgroundColor: item.tint }]} contentFit="cover" transition={200} />
    </Animated.View>
  );
}

/** The front is frosted: each photo behind it shows through as a soft smudge of its colour. */
function Smudge({ tint, album, slot, press, flip, from, to, still }: Placed & { tint: string }) {
  const transform = useDerivedValue(() => {
    const role = roleOf(album, from.get(), to.get());
    const pose = photoPose(slot, rise(role, slot, flip.get(), still), press.get());
    return [{ translateX: pose.x - FOLDER_X }, { translateY: pose.y - FOLDER_Y }, { rotate: (pose.rotation * Math.PI) / 180 }];
  });
  const opacity = useDerivedValue(() => {
    const role = roleOf(album, from.get(), to.get());
    return SMUDGE * clamp01(rise(role, slot, flip.get(), still)) * fade(role, flip.get(), still);
  });

  return (
    <Group transform={transform} opacity={opacity}>
      <RoundedRect x={-PHOTO_W / 2} y={-PHOTO_H / 2} width={PHOTO_W} height={PHOTO_H} r={PHOTO_RADIUS} color={tint}>
        <BlurMask blur={14} style="normal" />
      </RoundedRect>
    </Group>
  );
}

/** Moves its children to where sticker `slot` sits: it lifts, turns and grows with the press. */
function Sticker({ shape, album, slot, press, flip, from, to, still, children }: Placed & { shape: SkPath; children: ReactNode }) {
  const place = STICKERS[slot];
  const transform = useDerivedValue(() => {
    const look = stickerLook(roleOf(album, from.get(), to.get()), slot, flip.get(), still);
    const p = press.get();
    return [
      { translateX: place.x + place.dx * p },
      { translateY: place.y + place.dy * p },
      { rotate: ((place.r + place.dr * p) * Math.PI) / 180 },
      { scale: look.scale * (1 + STICKER_GROW * p) },
    ];
  });
  const opacity = useDerivedValue(() => stickerLook(roleOf(album, from.get(), to.get()), slot, flip.get(), still).opacity);
  // The shadow stays on the folder while the sticker lifts, so it slides out from under it.
  const shadow = useDerivedValue(() => [{ translateY: 1.5 + 5 * press.get() }]);

  return (
    <Group transform={transform} opacity={opacity}>
      <Group transform={shadow}>
        <Path path={shape} color="rgba(0,0,0,0.2)">
          <BlurMask blur={3} style="normal" />
        </Path>
      </Group>
      <Path path={shape} color="#ffffff" />
      {children}
    </Group>
  );
}

function Stamp({ flag, ...placed }: Placed & { flag: 'disc' | 'cross' }) {
  return (
    <Sticker shape={STAMP} {...placed}>
      <Group clip={STAMP_FIELD}>
        {flag === 'disc' ? (
          <>
            <Rect rect={STAMP_FIELD} color="#f7f7f8" />
            <Circle cx={0} cy={0} r={9.5} color="#e5383b" />
          </>
        ) : (
          <>
            <Rect rect={STAMP_FIELD} color="#2b5ba8" />
            <Rect x={-9.5} y={-18} width={8} height={36} color="#ffffff" />
            <Rect x={-20} y={-4} width={40} height={8} color="#ffffff" />
            <Rect x={-7.3} y={-18} width={3.6} height={36} color="#d72f34" />
            <Rect x={-20} y={-1.8} width={40} height={3.6} color="#d72f34" />
          </>
        )}
      </Group>
      <Rect rect={STAMP_FIELD} style="stroke" strokeWidth={0.5} color="rgba(0,0,0,0.1)" />
    </Sticker>
  );
}

function Emblem({ art, ...placed }: Placed & { art: EmblemArt }) {
  return (
    <Sticker shape={art.backing} {...placed}>
      {art.layers.map(([path, color], index) => (
        <Path key={index} path={path} color={color} />
      ))}
    </Sticker>
  );
}

/** The folder's front with everything on it: shadow, panel, the photos' smudges, embossed lines, stickers. */
function Front({ albums, ...motion }: Motion & { albums: Album[] }) {
  const { press } = motion;
  // The folder grows about its centre while it is pressed.
  const folder = useDerivedValue(() => [
    { translateX: FOLDER_X + FOLDER_W / 2 },
    { translateY: FOLDER_Y + FOLDER_H / 2 },
    { scale: 1 + FOLDER_GROW * press.get() },
    { translateX: -FOLDER_W / 2 },
    { translateY: -FOLDER_H / 2 },
  ]);
  const shadowY = useDerivedValue(() => 44 + 8 * press.get());
  const shadowOpacity = useDerivedValue(() => 0.13 + 0.05 * press.get());

  return (
    <Canvas style={styles.canvas} pointerEvents="none">
      <Group transform={folder}>
        <RoundedRect x={16} y={shadowY} width={FOLDER_W - 32} height={FOLDER_H - 40} r={16} color="#000000" opacity={shadowOpacity}>
          <BlurMask blur={16} style="normal" />
        </RoundedRect>
        <Path path={FOLDER}>
          <LinearGradient start={vec(0, 0)} end={vec(0, FOLDER_H)} colors={['#f0f0f2', '#f8f8f9']} />
        </Path>
        <Group clip={FOLDER}>
          {albums.map((album, a) =>
            album.photos
              .slice(0, REST.length)
              .map((item, slot) => <Smudge key={`${a}-${slot}`} tint={item.tint} album={a} slot={slot} {...motion} />),
          )}
          {/* Two embossed lines: a dark hairline with a light one under it. */}
          {LINES.map((y) => (
            <Fragment key={y}>
              <Rect x={20} y={y} width={FOLDER_W - 40} height={1} color="rgba(0,0,0,0.055)" />
              <Rect x={20} y={y + 1} width={FOLDER_W - 40} height={1} color="rgba(255,255,255,0.9)" />
            </Fragment>
          ))}
          {/* Half of this stroke is clipped away, which leaves a bright rim just inside the edge. */}
          <Path path={FOLDER} style="stroke" strokeWidth={3} color="rgba(255,255,255,0.95)" />
        </Group>
        <Path path={FOLDER} style="stroke" strokeWidth={0.5} color="rgba(0,0,0,0.07)" />
        {albums.map((album, a) => (
          <Fragment key={a}>
            <Stamp flag={album.flag ?? 'disc'} album={a} slot={0} {...motion} />
            <Emblem art={EMBLEMS[album.emblem ?? 'gate']} album={a} slot={1} {...motion} />
          </Fragment>
        ))}
      </Group>
    </Canvas>
  );
}

function Caption({ album, title, count, flip, from, to, still }: Motion & { album: number; title: string; count: string }) {
  const style = useAnimatedStyle(() => {
    const shown = textOpacity(roleOf(album, from.get(), to.get()), flip.get(), still);
    return { opacity: shown, transform: [{ translateY: still ? 0 : (1 - shown) * TEXT_SHIFT }] };
  });

  return (
    <Animated.View style={[styles.caption, style]}>
      <Text style={styles.title} numberOfLines={1}>
        {title}
      </Text>
      <View style={styles.pill}>
        <Text style={styles.count} numberOfLines={1}>
          {count}
        </Text>
      </View>
    </Animated.View>
  );
}

/**
 * A photo album shown as a folder: four bordered photos stick out behind a frosted front that carries
 * two stickers, with the album's name and photo count underneath. While it is pressed the folder grows,
 * the photos rise and fan out and the stickers lift; a quick tap drops the photos into the folder and
 * brings up the next album's.
 */
export function TripAlbumFolder({ albums = ALBUMS, unit = 'photos', autoPlay = false, onSelect }: Props) {
  const still = useReducedMotion();
  const auto = autoPlay && !still && albums.length > 1;

  const press = useSharedValue(0);
  const flip = useSharedValue(FLIP_CLOCK);
  const from = useSharedValue(0);
  const to = useSharedValue(0);
  const motion: Motion = { press, flip, from, to, still };

  const [shown, setShown] = useState(0);
  // A touch stops the loop; `count` restarts the wait before it resumes.
  const [touch, setTouch] = useState({ down: false, count: 0 });
  const latest = useRef({ shown: 0, downAt: 0, flippedAt: 0 });

  const pressTo = useCallback(
    (value: number) => {
      if (!still) press.set(withSpring(value, PRESS_SPRING));
    },
    [press, still],
  );

  const flipTo = useCallback(
    (album: number) => {
      from.set(latest.current.shown);
      to.set(album);
      flip.set(0);
      flip.set(withTiming(FLIP_CLOCK, { duration: FLIP_CLOCK * 1000, easing: Easing.linear, reduceMotion: ReduceMotion.Never }));
      latest.current.shown = album;
      latest.current.flippedAt = Date.now();
      setShown(album);
    },
    [flip, from, to],
  );

  // The script a finger would follow: a long press, then a tap, once per album.
  useEffect(() => {
    if (!auto || touch.down) return;
    const steps: [number, () => void][] = [
      [PRESS_AT, () => pressTo(1)],
      [RELEASE_AT, () => pressTo(0)],
      [TAP_AT, () => pressTo(1)],
      [
        TAP_AT + TAP_HOLD,
        () => {
          pressTo(0);
          flipTo((latest.current.shown + 1) % albums.length);
        },
      ],
    ];
    let timer: ReturnType<typeof setTimeout>;
    const run = (start: number, n: number) => {
      const [at, step] = steps[n % steps.length];
      // Every step is timed from the script's start, so the loop keeps its period.
      const due = start + (Math.floor(n / steps.length) * CYCLE + at) * 1000;
      timer = setTimeout(() => {
        step();
        run(start, n + 1);
      }, due - Date.now());
    };
    // A touch took over: wait, then go back to the first album before the script restarts.
    timer = setTimeout(
      () => {
        const back = latest.current.shown !== 0;
        if (back) flipTo(0);
        run(Date.now() + (back ? FLIP_TIME * 1000 : 0), 0);
      },
      touch.count > 0 ? RESUME_AFTER * 1000 : 0,
    );
    return () => clearTimeout(timer);
  }, [auto, touch, albums.length, pressTo, flipTo]);

  const advance = () => {
    if (albums.length < 2 || Date.now() - latest.current.flippedAt < FLIP_TIME * 1000) return;
    const next = (latest.current.shown + 1) % albums.length;
    if (process.env.EXPO_OS !== 'web') Haptics.selectionAsync().catch(() => {});
    flipTo(next);
    onSelect?.(next);
  };

  const album = albums[shown];

  return (
    <View style={styles.root}>
      {albums.map((entry, a) =>
        DRAW_ORDER.filter((slot) => slot < entry.photos.length).map((slot) => (
          <PhotoCard key={`${a}-${slot}`} item={entry.photos[slot]} album={a} slot={slot} {...motion} />
        )),
      )}
      <Front albums={albums} {...motion} />
      {albums.map((entry, a) => (
        <Caption key={a} album={a} title={entry.title} count={`${entry.count} ${unit}`} {...motion} />
      ))}
      <Pressable
        style={styles.touch}
        accessibilityRole="button"
        accessibilityLabel={`${album.title}, ${album.count} ${unit}`}
        accessibilityHint="Shows the next album"
        onPressIn={() => {
          latest.current.downAt = Date.now();
          setTouch((state) => ({ ...state, down: true }));
          pressTo(1);
          if (process.env.EXPO_OS !== 'web') Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
        }}
        onPressOut={() => {
          setTouch((state) => ({ down: false, count: state.count + 1 }));
          pressTo(0);
        }}
        // A press that ends at once, where it began, is a tap: on to the next album.
        onPress={() => {
          if (Date.now() - latest.current.downAt < TAP_WINDOW * 1000) advance();
        }}
      />
    </View>
  );
}

const styles = StyleSheet.create({
  root: {
    width: WIDTH,
    height: HEIGHT,
  },
  photo: {
    position: 'absolute',
    left: 0,
    top: 0,
    width: PHOTO_W,
    height: PHOTO_H,
    padding: PHOTO_BORDER,
    borderRadius: PHOTO_RADIUS,
    backgroundColor: '#ffffff',
    boxShadow: '0 4 14 0 rgba(0,0,0,0.16)',
  },
  image: {
    flex: 1,
    borderRadius: PHOTO_RADIUS - PHOTO_BORDER,
  },
  canvas: {
    position: 'absolute',
    left: 0,
    top: 0,
    width: WIDTH,
    height: HEIGHT,
  },
  caption: {
    position: 'absolute',
    left: 0,
    top: TEXT_Y,
    width: WIDTH,
    alignItems: 'center',
  },
  title: {
    color: '#1c1c1e',
    fontSize: 24,
    lineHeight: 30,
    fontWeight: '500',
    letterSpacing: -0.3,
  },
  pill: {
    height: 34,
    marginTop: 11,
    paddingHorizontal: 15,
    borderRadius: 17,
    backgroundColor: '#e9e9eb',
    justifyContent: 'center',
  },
  count: {
    color: '#8a8a8f',
    fontSize: 17,
    lineHeight: 22,
    fontVariant: ['tabular-nums'],
  },
  touch: {
    position: 'absolute',
    left: 0,
    top: 0,
    width: WIDTH,
    height: FOLDER_Y + FOLDER_H,
  },
});
