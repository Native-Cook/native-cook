import * as Haptics from 'expo-haptics';
import { Image } from 'expo-image';
import { LinearGradient } from 'expo-linear-gradient';
import { SymbolView } from 'expo-symbols';
import { useEffect, useState } from 'react';
import { type LayoutChangeEvent, StyleSheet, Text, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  Easing,
  type SharedValue,
  useAnimatedStyle,
  useFrameCallback,
  useReducedMotion,
  useSharedValue,
} from 'react-native-reanimated';
import { type EdgeInsets, useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

export type CubeStory = { uri: string; caption: string };
export type CubeStoryUser = {
  name: string;
  place: string;
  /** How long ago the stories were posted, e.g. "2h". */
  ago: string;
  avatar: string;
  stories: CubeStory[];
};

type Props = {
  /** The people whose stories sit on the faces of the cube, in order. Three or more. */
  users?: CubeStoryUser[];
  /** Plays every story, turns the cube to the next person after the last one and starts over. */
  autoPlay?: boolean;
  /** Called with a person's index each time the cube comes to rest on them. */
  onUserChange?: (index: number) => void;
};

const photo = (id: string) => `https://images.unsplash.com/${id}?auto=format&fit=crop&w=1000&h=2100&q=80`;
const avatar = (id: string) => `https://images.unsplash.com/${id}?auto=format&fit=crop&w=120&h=120&q=80`;

const USERS: CubeStoryUser[] = [
  {
    name: 'Maren Holt',
    place: 'Larch Hollow',
    ago: '2h',
    avatar: avatar('photo-1453959022778-cfda85dbe0f9'),
    stories: [
      { uri: photo('photo-1523712999610-f77fbcfc3843'), caption: 'Morning comes in sideways through the larches' },
      { uri: photo('photo-1543683840-c66117bdb1f8'), caption: 'One gust, and it rained copper' },
      { uri: photo('photo-1573776396359-5576727fa835'), caption: 'The last leaves on the old oak' },
      { uri: photo('photo-1637162330294-28cc8540d41d'), caption: 'The whole path, gold' },
    ],
  },
  {
    name: 'Ines Calder',
    place: 'Ocotillo Flats',
    ago: '5h',
    avatar: avatar('photo-1573689705887-bc0763c82ea2'),
    stories: [
      { uri: photo('photo-1517025423291-770fb99ae547'), caption: 'Four hundred years of standing still' },
      { uri: photo('photo-1533066636271-fdbe3e84ad80'), caption: 'Pink hour at the trailhead' },
      { uri: photo('photo-1517408191923-f82a669f4ea1'), caption: 'Moonrise over the red ridge' },
    ],
  },
  {
    name: 'Tove Lind',
    place: 'Kvasstind',
    ago: '9h',
    avatar: avatar('photo-1614358606268-aa86853578b4'),
    stories: [
      { uri: photo('photo-1565992441121-4367c2967103'), caption: 'Air off the cornice, nobody watching' },
      { uri: photo('photo-1516351950028-b976f7373d70'), caption: 'North face, first tracks' },
      { uri: photo('photo-1609952769887-7905b6b02c0f'), caption: 'Waist deep between the pines' },
      { uri: photo('photo-1610131279204-6d859b4b0563'), caption: 'Our lines from today' },
    ],
  },
];

// Each photo is up for PHOTO seconds while its bar fills; then the next one shows.
const PHOTO = 2;
// The cube turns a quarter turn to the next person in TURN seconds on an ease-in-out curve.
const TURN = 0.9;
const TURN_REDUCED = 0.35;
const EASE_TURN = Easing.bezierFn(0.65, 0, 0.35, 1);
// Lens distance as a share of the screen width: the faces swing through real perspective.
const PERSPECTIVE = 2.2;
// A face turned a full quarter away is this dark.
const DIM = 0.72;
// The photo on show drifts in by this much over its time (Ken Burns), and sideways by DRIFT pt.
const KEN_BURNS = 0.06;
const DRIFT = 10;

// Dragging turns the cube with the finger, one face per screen width. A release is thrown
// THROW seconds ahead at its speed, then a spring carries it to the nearest face (no further
// than one face from where the drag began). Past one face the drag only stretches, at RUBBER.
const DRAG_SLOP = 12;
const THROW = 0.22;
const RUBBER = 0.22;
const SPRING_STIFFNESS = 210;
const SPRING_DAMPING = 26;
// Taps in the left TAP_BACK of the screen go back a photo, anywhere else forward.
const TAP_BACK = 0.3;
const LONG_PRESS = 260;
// The header and the footer fade out over about this many seconds while a story is held.
const FADE_TIME = 0.09;

// autoPlay. Everything else plays by itself; the script adds one press-and-hold, on the second
// person's second photo, HOLD_AT seconds in, for HOLD seconds. A touch hands the viewer to the
// user for USER_HOLD seconds. One loop: 11 photos × 2 s + 3 turns × 0.9 s + 1.3 s held = 26 s.
const HOLD_USER = 1;
const HOLD_PHOTO = 1;
const HOLD_AT = 0.8;
const HOLD = 1.3;
const USER_HOLD = 4;
export const LOOP = 26;

// What the viewer is doing between frames.
const PLAYING = 0;
const TURNING = 1;
const DRAGGING = 2;
const SETTLING = 3;

/** `x` moved by whole turns of `n` into [-n/2, n/2). */
function centred(x: number, n: number) {
  'worklet';
  return x - n * Math.floor(x / n + 0.5);
}

function mod(i: number, n: number) {
  'worklet';
  return ((i % n) + n) % n;
}

/**
 * Where a face sits on the cube. `p` is its distance from the front in faces: 0 in front,
 * -1 a quarter turn to the left, +1 to the right. The face swings about the edge it shares with
 * the face in front, so the cube's corner stays at the screen while the far edges recede.
 */
function faceTransform(p: number, width: number) {
  'worklet';
  const half = width / 2;
  const pivot = p > 0 ? -half : half;
  return [
    { perspective: width * PERSPECTIVE },
    { translateX: p * width + pivot },
    { rotateY: `${p * 90}deg` },
    { translateX: -pivot },
  ];
}

/** The rubber band past one face from where a drag began. */
function stretch(d: number) {
  'worklet';
  const a = Math.abs(d);
  return a <= 1 ? d : Math.sign(d) * (1 + (a - 1) * RUBBER);
}

/**
 * Stories of a few people on the faces of a cube. Each person's photos play full screen under
 * a row of progress bars, their avatar and name; swiping sideways turns the cube to the next
 * person with the finger, and the turning face darkens as it swings away. Tap the right side
 * for the next photo, the left for the one before, and hold to pause and see the photo alone.
 */
export function CubeStoryViewer({ users = USERS, autoPlay = false, onUserChange }: Props) {
  const insets = useSafeAreaInsets();
  const reducedMotion = useReducedMotion();
  const count = users.length;
  const lengths = users.map((user) => user.stories.length);
  const [width, setWidth] = useState(0);
  const [height, setHeight] = useState(0);

  // The cube's turn in faces (unbounded; face u is in front at cube = u + count·k).
  const cube = useSharedValue(0);
  const cubeVelocity = useSharedValue(0);
  const mode = useSharedValue(PLAYING);
  const active = useSharedValue(0);
  const turnFrom = useSharedValue(0);
  const turnTo = useSharedValue(0);
  const turnTime = useSharedValue(0);
  const dragFrom = useSharedValue(0);
  const target = useSharedValue(0);
  // Per person: the photo on show and how far its bar has filled, [index0, fill0, index1, …].
  const marks = useSharedValue<number[]>(users.flatMap(() => [0, 0]));
  // A finger is down (the bars wait), or a story is held (the bars wait and the chrome fades).
  const touching = useSharedValue(false);
  const pressed = useSharedValue(false);
  const chrome = useSharedValue(1);
  const scriptHold = useSharedValue(0);
  const scriptDone = useSharedValue(false);
  const userHold = useSharedValue(0);
  const looping = useSharedValue(autoPlay);

  useEffect(() => {
    looping.set(autoPlay);
  }, [autoPlay, looping]);

  const committed = () => {
    if (process.env.EXPO_OS !== 'web') Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
  };
  const held = () => {
    if (process.env.EXPO_OS !== 'web') Haptics.selectionAsync().catch(() => {});
  };
  const arrived = (index: number) => onUserChange?.(index);

  const setMark = (user: number, index: number, fill: number) => {
    'worklet';
    const next = marks.get().slice();
    next[user * 2] = index;
    next[user * 2 + 1] = fill;
    marks.set(next);
  };

  /** Starts a quarter turn of the cube by `step` faces; the person it turns to starts over. */
  const turn = (step: number) => {
    'worklet';
    const from = Math.round(cube.get());
    turnFrom.set(cube.get());
    turnTo.set(from + step);
    turnTime.set(0);
    setMark(mod(from + step, count), 0, 0);
    mode.set(TURNING);
  };

  /** The cube has come to rest on face `at`. */
  const land = (at: number) => {
    'worklet';
    // Whole turns of the cube change nothing on screen, so the number is kept small.
    const wrapped = at - count * Math.floor(at / count);
    cube.set(cube.get() - (at - wrapped));
    cubeVelocity.set(0);
    mode.set(PLAYING);
    if (wrapped !== active.get()) {
      active.set(wrapped);
      scriptDone.set(false);
      scheduleOnRN(arrived, wrapped);
    }
  };

  useFrameCallback((frame) => {
    const dt = Math.min(frame.timeSincePreviousFrame ?? 16, 64) / 1000;
    if (dt <= 0) return;
    if (userHold.get() > 0) userHold.set(Math.max(0, userHold.get() - dt));
    const m = mode.get();

    if (m === TURNING) {
      const duration = reducedMotion ? TURN_REDUCED : TURN;
      const t = turnTime.get() + dt;
      turnTime.set(t);
      const e = EASE_TURN(Math.min(1, t / duration));
      cube.set(turnFrom.get() + (turnTo.get() - turnFrom.get()) * e);
      if (t >= duration) {
        cube.set(turnTo.get());
        land(turnTo.get());
        // The time left over from this frame already counts towards the new person's first photo.
        const u = active.get();
        setMark(u, 0, (t - duration) / PHOTO);
      }
    } else if (m === SETTLING) {
      // Long frames are split in two: this spring is not stable over a 64 ms step. At 60 fps
      // nothing changes.
      const steps = dt > 0.034 ? 2 : 1;
      const h = dt / steps;
      let x = cube.get();
      let v = cubeVelocity.get();
      for (let k = 0; k < steps; k += 1) {
        v += (SPRING_STIFFNESS * (target.get() - x) - SPRING_DAMPING * v) * h;
        x += v * h;
      }
      cubeVelocity.set(v);
      cube.set(x);
      if (reducedMotion || (Math.abs(target.get() - cube.get()) < 0.0015 && Math.abs(v) < 0.02)) {
        cube.set(target.get());
        land(target.get());
      }
    } else if (m === PLAYING) {
      const u = active.get();
      const index = marks.get()[u * 2];
      const fill = marks.get()[u * 2 + 1];
      // The script's one press-and-hold, when its moment comes round.
      if (
        looping.get() &&
        userHold.get() <= 0 &&
        !scriptDone.get() &&
        u === HOLD_USER % count &&
        index === HOLD_PHOTO &&
        fill * PHOTO >= HOLD_AT
      ) {
        scriptDone.set(true);
        scriptHold.set(HOLD);
      }
      if (scriptHold.get() > 0) {
        scriptHold.set(Math.max(0, scriptHold.get() - dt));
      } else if (!touching.get() && !pressed.get()) {
        const next = fill + dt / PHOTO;
        if (next < 1) {
          setMark(u, index, next);
        } else if (index < lengths[u] - 1) {
          setMark(u, index + 1, next - 1);
        } else {
          setMark(u, index, 1);
          // After the last person the viewer stops, unless it is playing by itself.
          if (u < count - 1 || looping.get()) turn(1);
        }
      }
    }

    // The header and footer fade while a story is held.
    const show = pressed.get() || scriptHold.get() > 0 ? 0 : 1;
    chrome.set(chrome.get() + (show - chrome.get()) * (1 - Math.exp(-dt / FADE_TIME)));
  });

  const touched = () => {
    'worklet';
    userHold.set(USER_HOLD);
    scriptHold.set(0);
  };

  const pan = Gesture.Pan()
    .activeOffsetX([-DRAG_SLOP, DRAG_SLOP])
    .failOffsetY([-28, 28])
    .onBegin(() => {
      touched();
      touching.set(true);
    })
    .onStart(() => {
      // A drag can catch the cube mid-turn; it starts from wherever the cube is.
      dragFrom.set(Math.round(cube.get()));
      target.set(cube.get());
      mode.set(DRAGGING);
    })
    .onUpdate((event) => {
      if (width <= 0) return;
      const d = (target.get() - dragFrom.get()) - event.translationX / width;
      cube.set(dragFrom.get() + stretch(d));
    })
    .onEnd((event) => {
      if (width <= 0) return;
      // A velocity the recogniser could not measure counts as none.
      const v = Number.isFinite(event.velocityX) ? -event.velocityX / width : 0;
      const thrown = cube.get() + v * THROW;
      const to = Math.max(dragFrom.get() - 1, Math.min(dragFrom.get() + 1, Math.round(thrown)));
      const next = mod(to, count);
      if (next !== active.get()) {
        // The person turned to picks up where they were left, or starts over once all was seen.
        const seen = marks.get()[next * 2];
        setMark(next, seen >= lengths[next] - 1 && marks.get()[next * 2 + 1] >= 1 ? 0 : seen, 0);
        scheduleOnRN(committed);
      }
      target.set(to);
      cubeVelocity.set(v);
      mode.set(SETTLING);
    })
    .onFinalize(() => {
      touching.set(false);
      // A drag that never moved the cube far enough to start leaves it where it was.
      if (mode.get() === DRAGGING) {
        target.set(Math.round(cube.get()));
        mode.set(SETTLING);
      }
    });

  const hold = Gesture.LongPress()
    .minDuration(LONG_PRESS)
    .maxDistance(14)
    .onStart(() => {
      touched();
      pressed.set(true);
      scheduleOnRN(held);
    })
    .onFinalize(() => {
      pressed.set(false);
    });

  const tap = Gesture.Tap()
    .maxDuration(LONG_PRESS - 20)
    .maxDistance(12)
    .onEnd((event, success) => {
      if (!success || mode.get() !== PLAYING) return;
      touched();
      const u = active.get();
      const index = marks.get()[u * 2];
      if (event.x < width * TAP_BACK) {
        if (index > 0) setMark(u, index - 1, 0);
        else turn(-1);
      } else if (index < lengths[u] - 1) {
        setMark(u, index + 1, 0);
      } else {
        setMark(u, index, 1);
        turn(1);
      }
    });

  return (
    <GestureDetector gesture={Gesture.Race(pan, hold, tap)}>
      <View
        style={styles.screen}
        accessible
        accessibilityLabel={`Stories from ${users.map((user) => user.name).join(', ')}`}
        accessibilityHint="Swipe sideways for the next person, tap for the next photo, hold to pause"
        onLayout={(event: LayoutChangeEvent) => {
          setWidth(event.nativeEvent.layout.width);
          setHeight(event.nativeEvent.layout.height);
        }}
      >
        {width > 0 &&
          users.map((user, u) => (
            <Face
              key={u}
              user={user}
              index={u}
              count={count}
              cube={cube}
              marks={marks}
              chrome={chrome}
              width={width}
              height={height}
              insets={insets}
              reducedMotion={reducedMotion}
            />
          ))}
      </View>
    </GestureDetector>
  );
}

type FaceProps = {
  user: CubeStoryUser;
  index: number;
  count: number;
  cube: SharedValue<number>;
  marks: SharedValue<number[]>;
  chrome: SharedValue<number>;
  width: number;
  height: number;
  insets: EdgeInsets;
  reducedMotion: boolean;
};

/** One person's stories: a face of the cube. */
function Face({ user, index, count, cube, marks, chrome, width, height, insets, reducedMotion }: FaceProps) {
  const faceStyle = useAnimatedStyle(() => {
    const p = centred(index - cube.get(), count);
    const off = Math.abs(p) >= 1;
    if (reducedMotion) return { opacity: off ? 0 : 1 - Math.abs(p), transform: [] };
    return { opacity: off ? 0 : 1, transform: off ? [] : faceTransform(p, width) };
  });
  const shadeStyle = useAnimatedStyle(() => ({
    opacity: Math.min(1, Math.abs(centred(index - cube.get(), count))) * DIM,
  }));
  const chromeStyle = useAnimatedStyle(() => ({ opacity: chrome.get() }));
  const first = user.name.split(' ')[0];

  return (
    <Animated.View pointerEvents="none" style={[styles.face, { width, height }, faceStyle]}>
      {user.stories.map((story, k) => (
        <Photo key={k} uri={story.uri} user={index} index={k} marks={marks} reducedMotion={reducedMotion} />
      ))}

      <Animated.View style={[StyleSheet.absoluteFill, chromeStyle]}>
        <LinearGradient
          colors={['rgba(0,0,0,0.55)', 'rgba(0,0,0,0.18)', 'rgba(0,0,0,0)']}
          locations={[0, 0.55, 1]}
          style={[styles.scrimTop, { height: insets.top + 150 }]}
        />
        <LinearGradient
          colors={['rgba(0,0,0,0)', 'rgba(0,0,0,0.35)', 'rgba(0,0,0,0.72)']}
          locations={[0, 0.45, 1]}
          style={[styles.scrimBottom, { height: insets.bottom + 300 }]}
        />

        <View style={[styles.bars, { top: insets.top + 8 }]}>
          {user.stories.map((_, k) => (
            <Bar key={k} user={index} index={k} marks={marks} />
          ))}
        </View>

        <View style={[styles.header, { top: insets.top + 24 }]}>
          <View style={styles.avatarRing}>
            <Image source={{ uri: user.avatar }} style={styles.avatar} contentFit="cover" transition={200} />
          </View>
          <View style={styles.who}>
            <Text style={styles.name} numberOfLines={1}>
              {user.name}
              <Text style={styles.ago}>{`  ${user.ago}`}</Text>
            </Text>
            <View style={styles.placeRow}>
              <SymbolView name={{ ios: 'mappin', android: 'place', web: 'place' }} size={11} tintColor="rgba(255,255,255,0.8)" />
              <Text style={styles.place} numberOfLines={1}>
                {user.place}
              </Text>
            </View>
          </View>
          <View style={styles.actions}>
            <SymbolView name={{ ios: 'ellipsis', android: 'more_horiz', web: 'more_horiz' }} size={20} tintColor="#ffffff" />
            <SymbolView name={{ ios: 'xmark', android: 'close', web: 'close' }} size={19} tintColor="#ffffff" />
          </View>
        </View>

        <View style={[styles.footer, { paddingBottom: insets.bottom + 10 }]}>
          <View style={styles.captions}>
            {user.stories.map((story, k) => (
              <Caption key={k} text={story.caption} user={index} index={k} total={user.stories.length} marks={marks} />
            ))}
          </View>
          <View style={styles.replyRow}>
            <View style={styles.reply}>
              <Text style={styles.replyText} numberOfLines={1}>{`Reply to ${first}…`}</Text>
            </View>
            <SymbolView name={{ ios: 'heart', android: 'favorite_border', web: 'favorite_border' }} size={25} tintColor="#ffffff" />
            <SymbolView name={{ ios: 'paperplane', android: 'send', web: 'send' }} size={23} tintColor="#ffffff" />
          </View>
        </View>
      </Animated.View>

      <Animated.View style={[StyleSheet.absoluteFill, styles.shade, shadeStyle]} />
    </Animated.View>
  );
}

type PartProps = { user: number; index: number; marks: SharedValue<number[]> };

/** A photo of the story; only the one on show is visible, and it drifts in while its bar fills. */
function Photo({ uri, user, index, marks, reducedMotion }: PartProps & { uri: string; reducedMotion: boolean }) {
  const style = useAnimatedStyle(() => {
    const m = marks.get();
    const on = m[user * 2] === index;
    const t = on && !reducedMotion ? m[user * 2 + 1] : 0;
    // Alternate photos drift the other way, so a run of them doesn't slide one way only.
    const side = index % 2 === 0 ? 1 : -1;
    return {
      opacity: on ? 1 : 0,
      transform: [{ translateX: side * DRIFT * (t - 0.5) }, { scale: 1 + KEN_BURNS * t + DRIFT / 400 }],
    };
  });
  return (
    <Animated.View style={[StyleSheet.absoluteFill, style]}>
      <Image source={{ uri }} style={StyleSheet.absoluteFill} contentFit="cover" transition={300} cachePolicy="memory-disk" />
    </Animated.View>
  );
}

/** One segment of the progress row: full for photos seen, filling for the one on show. */
function Bar({ user, index, marks }: PartProps) {
  const style = useAnimatedStyle(() => {
    const m = marks.get();
    const at = m[user * 2];
    const fill = index < at ? 1 : index === at ? Math.min(1, m[user * 2 + 1]) : 0;
    return { width: `${fill * 100}%` };
  });
  return (
    <View style={styles.bar}>
      <Animated.View style={[styles.barFill, style]} />
    </View>
  );
}

/** The line under a photo, with its number; it changes with the photo. */
function Caption({ text, user, index, total, marks }: PartProps & { text: string; total: number }) {
  const style = useAnimatedStyle(() => ({ opacity: marks.get()[user * 2] === index ? 1 : 0 }));
  const pad = (n: number) => String(n).padStart(2, '0');
  return (
    <Animated.View style={[styles.caption, style]}>
      <Text style={styles.counter}>{`${pad(index + 1)} / ${pad(total)}`}</Text>
      <Text style={styles.captionText}>{text}</Text>
    </Animated.View>
  );
}

const styles = StyleSheet.create({
  screen: {
    flex: 1,
    backgroundColor: '#000000',
    overflow: 'hidden',
  },
  face: {
    position: 'absolute',
    top: 0,
    left: 0,
    overflow: 'hidden',
    backgroundColor: '#0d0d0d',
    backfaceVisibility: 'hidden',
  },
  shade: {
    backgroundColor: '#000000',
  },
  scrimTop: {
    position: 'absolute',
    top: 0,
    left: 0,
    right: 0,
  },
  scrimBottom: {
    position: 'absolute',
    bottom: 0,
    left: 0,
    right: 0,
  },
  bars: {
    position: 'absolute',
    left: 10,
    right: 10,
    flexDirection: 'row',
    gap: 4,
  },
  bar: {
    flex: 1,
    height: 2.5,
    borderRadius: 2,
    overflow: 'hidden',
    backgroundColor: 'rgba(255,255,255,0.32)',
  },
  barFill: {
    position: 'absolute',
    top: 0,
    bottom: 0,
    left: 0,
    backgroundColor: '#ffffff',
  },
  header: {
    position: 'absolute',
    left: 12,
    right: 16,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 10,
  },
  actions: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 20,
  },
  avatarRing: {
    width: 38,
    height: 38,
    borderRadius: 19,
    borderWidth: 1.5,
    borderColor: '#ffffff',
    padding: 2,
  },
  avatar: {
    flex: 1,
    borderRadius: 16,
  },
  who: {
    flex: 1,
    gap: 2,
  },
  name: {
    color: '#ffffff',
    fontSize: 15,
    fontWeight: '600',
    letterSpacing: -0.1,
  },
  ago: {
    color: 'rgba(255,255,255,0.7)',
    fontSize: 13,
    fontWeight: '500',
  },
  placeRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 4,
  },
  place: {
    color: 'rgba(255,255,255,0.8)',
    fontSize: 12.5,
    fontWeight: '500',
  },
  footer: {
    position: 'absolute',
    left: 0,
    right: 0,
    bottom: 0,
    paddingHorizontal: 16,
    gap: 22,
  },
  captions: {
    height: 104,
    justifyContent: 'flex-end',
  },
  caption: {
    position: 'absolute',
    left: 4,
    right: 28,
    bottom: 0,
    gap: 8,
  },
  counter: {
    color: 'rgba(255,255,255,0.72)',
    fontSize: 12,
    fontWeight: '600',
    letterSpacing: 1.2,
    fontVariant: ['tabular-nums'],
  },
  captionText: {
    color: '#ffffff',
    fontSize: 27,
    lineHeight: 31,
    fontWeight: '700',
    letterSpacing: -0.6,
  },
  replyRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 18,
  },
  reply: {
    flex: 1,
    height: 46,
    borderRadius: 23,
    borderWidth: 1,
    borderColor: 'rgba(255,255,255,0.55)',
    justifyContent: 'center',
    paddingHorizontal: 18,
  },
  replyText: {
    color: 'rgba(255,255,255,0.88)',
    fontSize: 15,
  },
});
