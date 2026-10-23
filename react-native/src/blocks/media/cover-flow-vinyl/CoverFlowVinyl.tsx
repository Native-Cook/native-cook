import { Canvas, Circle, Group, SweepGradient, vec } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { Image } from 'expo-image';
import { LinearGradient } from 'expo-linear-gradient';
import { SymbolView } from 'expo-symbols';
import { useEffect, useState } from 'react';
import { type LayoutChangeEvent, StyleSheet, Text, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  type SharedValue,
  useAnimatedReaction,
  useAnimatedStyle,
  useFrameCallback,
  useReducedMotion,
  useSharedValue,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

export type VinylAlbum = {
  title: string;
  artist: string;
  cover: string;
  /** Track length and where playback starts, in seconds. */
  length: number;
  startAt: number;
};

type Props = {
  albums?: VinylAlbum[];
  /** Flips through the covers, pulls a record out, plays it, puts it back and flips back. */
  autoPlay?: boolean;
  /** Called with an album when its record is pulled out. */
  onPlay?: (album: VinylAlbum) => void;
};

const cover = (id: string) => `https://images.unsplash.com/${id}?auto=format&fit=crop&w=600&h=600&q=80`;

const ALBUMS: VinylAlbum[] = [
  { title: 'Low Tide Radio', artist: 'Mara Quell', cover: cover('photo-1644855640845-ab57a047320e'), length: 228, startAt: 64 },
  { title: 'Yellow Room', artist: 'June Calloway', cover: cover('photo-1645919268978-35253dfb1d75'), length: 201, startAt: 41 },
  { title: 'Glass Choir', artist: 'Ilse Varga', cover: cover('photo-1651443039959-582bbea6be6a'), length: 254, startAt: 72 },
  { title: 'Half Moon Club', artist: 'Corvo & Lune', cover: cover('photo-1614283868067-b4e06385bf93'), length: 236, startAt: 38 },
  { title: 'Paper Suns', artist: 'The Halvorsens', cover: cover('photo-1717598604697-bb2e9e841c1c'), length: 192, startAt: 55 },
  { title: 'Number Nine', artist: 'The Sundries', cover: cover('photo-1645677042909-907678e3a40a'), length: 213, startAt: 47 },
  { title: 'Field Notes', artist: 'Tomas Rieu', cover: cover('photo-1598909688344-fbe5e6d4c869'), length: 244, startAt: 80 },
  { title: 'Velvet Interval', artist: 'Odile Fenn', cover: cover('photo-1611506420749-9cbb8e5a1057'), length: 262, startAt: 59 },
  { title: 'Hand-Me-Down Heat', artist: 'Desert Bloom Society', cover: cover('photo-1694355450043-04721056e2f1'), length: 207, startAt: 33 },
  { title: 'Long Distance', artist: 'Kite Assembly', cover: cover('photo-1588066077857-70494c21533c'), length: 219, startAt: 50 },
];

const BG = '#0d0c0b';
const INK = '#f3eee6';
const MUTED = '#8f877c';
const TRACK = '#2c2925';
const ACCENT = '#e8a24a';

// The cover in front is SIZE_SHARE of the width (at most MAX_SIZE pt), centred at CENTRE_AT of
// the height. Its neighbours stand FIRST covers to each side, the rest NEXT further on, turned
// TILT° to face the middle, shrunk by SIDE_SCALE, darkened by SIDE_DIM, and seen through a lens
// PERSPECTIVE covers away. Covers further than VISIBLE from the front fade out.
const SIZE_SHARE = 0.56;
const MAX_SIZE = 250;
const CENTRE_AT = 0.4;
const FIRST = 0.62;
const NEXT = 0.16;
const TILT = 70;
const SIDE_SCALE = 0.2;
const SIDE_DIM = 0.5;
const DIM_PER_COVER = 0.1;
const PERSPECTIVE = 4.5;
const VISIBLE = 3.6;
// The floor reflection: a flipped copy fading into the background over REFLECTION of a cover.
const REFLECTION = 0.42;
// The caption sits this far under the covers, the player under it.
const CAPTION_GAP = 100;
// How strong the reflection is just under the cover.
const REFLECTION_STRENGTH = 0.42;

// Scrolling: one cover per DRAG_STEP of the cover's size under the finger; a release is thrown
// THROW seconds ahead and a spring (STIFFNESS, DAMPING; ζ ≈ 0.9) settles on the nearest cover.
const DRAG_STEP = 0.72;
const THROW = 0.16;
const STIFFNESS = 190;
const DAMPING = 25;

// Pulling the record out: a spring on 0…1 (OPEN_STIFFNESS, OPEN_DAMPING; ζ ≈ 0.78, so the
// record overshoots a touch as it stops). The cover lifts LIFT pt and moves left by SHIFT of
// its size; the record slides out to RECORD_OUT of the size right of the cover's centre; the
// other covers spread SPREAD of a cover outwards and fade.
const OPEN_STIFFNESS = 170;
const OPEN_DAMPING = 20;
const LIFT = 26;
const SHIFT = 0.24;
const RECORD_OUT = 0.56;
const RECORD_SIZE = 0.95;
const SPREAD = 0.45;
// 33⅓ rpm, reached and lost at SPIN_RATE (1/s).
const RPM = 100 / 3;
const SPIN_RATE = 2.4;
const LABEL = 0.36;

// autoPlay (seconds): two covers on, the record out and playing, back in, a flick back home.
const LOOP = 12;
const STEPS = [
  { at: 0.7, by: 1 },
  { at: 1.8, by: 1 },
  { at: 9.0, by: -2 },
];
const OPEN_AT = 3.0;
const CLOSE_AT = 7.6;
const USER_HOLD = 4;

function centred(x: number, n: number) {
  'worklet';
  return x - n * Math.floor(x / n + 0.5);
}

/** Whether the clock, going from `before` to `now` (wrapping at LOOP), went past `at`. */
function passedAt(before: number, now: number, at: number) {
  'worklet';
  return (before < at && now >= at) || (now < before && at <= now);
}

/** The album in front when the flow is at `x`. */
function indexAt(x: number, n: number) {
  'worklet';
  const r = Math.round(x);
  return r - n * Math.floor(r / n);
}

function clamp(v: number, lo: number, hi: number) {
  'worklet';
  return Math.min(hi, Math.max(lo, v));
}

/** Where a cover `d` places from the front sits: sideways offset (in covers), turn and shade. */
function placement(d: number) {
  'worklet';
  const a = Math.abs(d);
  const s = Math.sign(d);
  const near = Math.min(a, 1);
  return {
    x: s * (near * FIRST + Math.max(a - 1, 0) * NEXT),
    turn: -s * near * TILT,
    scale: 1 - near * SIDE_SCALE,
    dim: near * SIDE_DIM + Math.max(a - 1, 0) * DIM_PER_COVER,
    alpha: clamp(VISIBLE + 0.6 - a, 0, 1),
  };
}

const time = (s: number) => `${Math.floor(s / 60)}:${String(Math.floor(s % 60)).padStart(2, '0')}`;

/**
 * Albums in a cover flow on a dark floor: the one in front faces you, the rest stand turned
 * towards it on each side, overlapping, with their reflections fading into the floor. Swipe to
 * flip through. Tap the front cover and its record slides out to the right and spins, the
 * cover lifts and a small player fades in under it; tap again to slide the record back.
 */
export function CoverFlowVinyl({ albums = ALBUMS, autoPlay = false, onPlay }: Props) {
  const insets = useSafeAreaInsets();
  const reducedMotion = useReducedMotion();
  const count = albums.length;
  const [size, setSize] = useState({ width: 0, height: 0 });
  const [front, setFront] = useState(0);
  const [seconds, setSeconds] = useState(albums[0].startAt);
  const [playing, setPlaying] = useState(true);
  const { width, height } = size;
  const coverSize = Math.min(width * SIZE_SHARE, MAX_SIZE);
  const centreY = height * CENTRE_AT;

  const scroll = useSharedValue(0);
  const velocity = useSharedValue(0);
  const target = useSharedValue(0);
  const dragging = useSharedValue(false);
  const dragFrom = useSharedValue(0);
  const seen = useSharedValue(0);
  const open = useSharedValue(0);
  const openV = useSharedValue(0);
  const openTarget = useSharedValue(0);
  const spin = useSharedValue(0);
  const spinSpeed = useSharedValue(0);
  const paused = useSharedValue(false);
  const elapsed = useSharedValue(albums[0].startAt);
  const byUser = useSharedValue(false);
  const clock = useSharedValue(0);
  const looping = useSharedValue(autoPlay);
  const hold = useSharedValue(0);

  useEffect(() => {
    looping.set(autoPlay);
  }, [autoPlay, looping]);

  const tick = () => {
    if (process.env.EXPO_OS !== 'web') Haptics.selectionAsync().catch(() => {});
  };
  const thud = () => {
    if (process.env.EXPO_OS !== 'web') Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
  };
  const pulled = (index: number) => onPlay?.(albums[index]);

  /** Pulls the front record out, or slides it back in. */
  const toggle = (user: boolean) => {
    'worklet';
    const out = openTarget.get() < 0.5;
    openTarget.set(out ? 1 : 0);
    if (out) {
      // The record left in the sleeve is wound back to where this album starts.
      if (open.get() < 0.02) {
        spin.set(0);
        elapsed.set(albums[indexAt(scroll.get(), count)].startAt);
      }
      paused.set(false);
      scheduleOnRN(setPlaying, true);
      scheduleOnRN(pulled, indexAt(scroll.get(), count));
    }
    if (user) scheduleOnRN(thud);
  };

  useFrameCallback((frame) => {
    const dt = Math.min(frame.timeSincePreviousFrame ?? 16, 64) / 1000;
    if (dt <= 0) return;

    if (hold.get() > 0) {
      hold.set(hold.get() - dt);
      // The script starts over from a resting state: closed at its start, open just before it closes.
      if (hold.get() <= 0) clock.set(openTarget.get() > 0.5 ? CLOSE_AT - 0.1 : 0);
    } else if (looping.get() && !dragging.get()) {
      const before = clock.get();
      const t = (before + dt) % LOOP;
      clock.set(t);
      for (let k = 0; k < STEPS.length; k += 1) {
        if (passedAt(before, t, STEPS[k].at) && openTarget.get() < 0.5) {
          byUser.set(false);
          target.set(Math.round(target.get()) + STEPS[k].by);
          // A flick back carries its speed into the spring, like a finger's would.
          if (STEPS[k].by < -1) velocity.set(STEPS[k].by * 3.2);
        }
      }
      if (passedAt(before, t, OPEN_AT) && openTarget.get() < 0.5) toggle(false);
      if (passedAt(before, t, CLOSE_AT) && openTarget.get() > 0.5) toggle(false);
    }

    // The flow.
    if (dragging.get()) {
      velocity.set(velocity.get() + ((scroll.get() - seen.get()) / dt - velocity.get()) * 0.5);
    } else if (reducedMotion) {
      scroll.set(target.get());
      velocity.set(0);
    } else {
      const v = velocity.get() + (STIFFNESS * (target.get() - scroll.get()) - DAMPING * velocity.get()) * dt;
      velocity.set(v);
      scroll.set(scroll.get() + v * dt);
    }
    seen.set(scroll.get());
    // Whole turns of the flow change nothing on screen, so the numbers are kept small.
    const turns = Math.floor(target.get() / count) * count;
    if (turns !== 0 && !dragging.get()) {
      scroll.set(scroll.get() - turns);
      seen.set(seen.get() - turns);
      target.set(target.get() - turns);
    }

    // The record.
    if (reducedMotion) {
      open.set(open.get() + (openTarget.get() - open.get()) * (1 - Math.exp(-dt * 12)));
    } else {
      const ov = openV.get() + (OPEN_STIFFNESS * (openTarget.get() - open.get()) - OPEN_DAMPING * openV.get()) * dt;
      openV.set(ov);
      open.set(open.get() + ov * dt);
    }
    const turning = openTarget.get() > 0.5 && !paused.get();
    const goal = turning && !reducedMotion ? (RPM / 60) * 360 : 0;
    spinSpeed.set(spinSpeed.get() + (goal - spinSpeed.get()) * (1 - Math.exp(-dt * SPIN_RATE)));
    spin.set((spin.get() + spinSpeed.get() * dt) % 360);
    if (turning) {
      const length = albums[indexAt(scroll.get(), count)].length;
      elapsed.set((elapsed.get() + dt) % length);
    }
  });

  // The album in front names the record, the player and the label read out to VoiceOver.
  useAnimatedReaction(
    () => indexAt(scroll.get(), count),
    (now, before) => {
      if (before === null || now === before) return;
      scheduleOnRN(setFront, now);
      if (byUser.get()) scheduleOnRN(tick);
    },
  );
  useAnimatedReaction(
    () => Math.floor(elapsed.get()),
    (now, before) => {
      if (now !== before) scheduleOnRN(setSeconds, now);
    },
  );

  const touched = () => {
    'worklet';
    hold.set(USER_HOLD);
    byUser.set(true);
  };

  const step = coverSize * DRAG_STEP;
  const pan = Gesture.Pan()
    .activeOffsetX([-8, 8])
    .onStart(() => {
      touched();
      if (openTarget.get() > 0.5) return;
      dragFrom.set(scroll.get());
      seen.set(scroll.get());
      dragging.set(true);
    })
    .onUpdate((event) => {
      if (!dragging.get() || step <= 0) return;
      scroll.set(dragFrom.get() - event.translationX / step);
    })
    .onEnd((event) => {
      if (!dragging.get() || step <= 0) return;
      dragging.set(false);
      velocity.set(-event.velocityX / step);
      target.set(Math.round(scroll.get() + velocity.get() * THROW));
    })
    .onFinalize(() => {
      if (dragging.get()) {
        dragging.set(false);
        target.set(Math.round(scroll.get()));
      }
    });

  const tap = Gesture.Tap()
    .maxDistance(10)
    .onEnd((event, success) => {
      if (!success) return;
      touched();
      if (openTarget.get() > 0.5) {
        // The player's middle button pauses; anywhere else puts the record back.
        const controlsY = centreY + coverSize / 2 + CAPTION_GAP + 121;
        if (Math.abs(event.y - controlsY) < 34 && Math.abs(event.x - width / 2) < 34) {
          paused.set(!paused.get());
          scheduleOnRN(setPlaying, paused.get() === false);
          return;
        }
        toggle(true);
        return;
      }
      // Before the first layout there are no covers to tap (and dx would divide by zero).
      if (coverSize <= 0) return;
      // A tap on a side cover brings it to the front; on the front cover, pulls its record.
      const dx = (event.x - width / 2) / coverSize;
      if (Math.abs(dx) < 0.5 && Math.abs(event.y - centreY) < coverSize / 2) {
        target.set(Math.round(scroll.get()));
        toggle(true);
        return;
      }
      if (Math.abs(event.y - centreY) > coverSize * 0.7) return;
      const a = Math.abs(dx);
      const places = a < FIRST + 0.2 ? 1 : 1 + Math.round((a - FIRST) / NEXT);
      target.set(Math.round(scroll.get()) + Math.sign(dx) * places);
    });

  const album = albums[front];

  const captionStyle = useAnimatedStyle(() => ({
    transform: [{ translateY: -open.get() * LIFT * 0.6 }],
  }));
  const playerStyle = useAnimatedStyle(() => {
    const o = clamp((open.get() - 0.45) / 0.55, 0, 1);
    return { opacity: o, transform: [{ translateY: (1 - o) * 14 - open.get() * LIFT * 0.6 }] };
  });
  const progressStyle = useAnimatedStyle(() => ({
    width: `${(elapsed.get() / albums[indexAt(scroll.get(), count)].length) * 100}%`,
  }));
  const hintStyle = useAnimatedStyle(() => ({ opacity: 1 - clamp(open.get() * 2, 0, 1) }));

  return (
    <GestureDetector gesture={Gesture.Exclusive(pan, tap)}>
      <View
        style={styles.screen}
        accessible
        accessibilityRole="adjustable"
        accessibilityLabel={`${album.title} by ${album.artist}`}
        accessibilityHint="Swipe to flip through albums, tap the cover to play its record"
        onLayout={(event: LayoutChangeEvent) => {
          const { width: w, height: h } = event.nativeEvent.layout;
          setSize({ width: w, height: h });
        }}
      >
        {/* A warm wall down to the floor the covers stand on; the floor is flat, so reflections melt into it. */}
        <LinearGradient
          colors={['#1d1a16', '#13110f', BG]}
          locations={[0, 0.6, 1]}
          style={[styles.wall, { height: height > 0 ? centreY + coverSize / 2 : '50%' }]}
        />
        <View style={[styles.header, { top: insets.top + 14 }]}>
          <Text style={styles.heading}>Crate</Text>
          <Text style={styles.count}>{`${count} records`}</Text>
        </View>

        {width > 0 && (
          <>
            {/* Each cover stacks itself by its distance from the front; the record sits just under the front one. */}
            {albums.map((item, index) => (
              <Cover
                key={index}
                album={item}
                index={index}
                count={count}
                scroll={scroll}
                open={open}
                size={coverSize}
                centreX={width / 2}
                centreY={centreY}
                reducedMotion={reducedMotion}
              />
            ))}
            <Record
              album={album}
              open={open}
              spin={spin}
              size={coverSize}
              centreX={width / 2}
              centreY={centreY}
              reducedMotion={reducedMotion}
            />

            <Animated.View style={[styles.caption, { top: centreY + coverSize / 2 + CAPTION_GAP }, captionStyle]}>
              {albums.map((item, index) => (
                <Caption key={index} album={item} index={index} count={count} scroll={scroll} />
              ))}
            </Animated.View>

            <Animated.View pointerEvents="none" style={[styles.player, { top: centreY + coverSize / 2 + CAPTION_GAP + 66 }, playerStyle]}>
              <View style={styles.track}>
                <Animated.View style={[styles.trackFill, progressStyle]} />
              </View>
              <View style={styles.times}>
                <Text style={styles.time}>{time(seconds)}</Text>
                <Text style={styles.time}>{`-${time(album.length - seconds)}`}</Text>
              </View>
              <View style={styles.controls}>
                <SymbolView name={{ ios: 'backward.fill', android: 'fast_rewind', web: 'fast_rewind' }} size={22} tintColor={INK} />
                <View style={styles.playButton}>
                  <SymbolView
                    name={
                      playing
                        ? { ios: 'pause.fill', android: 'pause', web: 'pause' }
                        : { ios: 'play.fill', android: 'play_arrow', web: 'play_arrow' }
                    }
                    size={24}
                    tintColor={BG}
                  />
                </View>
                <SymbolView name={{ ios: 'forward.fill', android: 'fast_forward', web: 'fast_forward' }} size={22} tintColor={INK} />
              </View>
            </Animated.View>

            <Animated.Text style={[styles.hint, { bottom: insets.bottom + 22 }, hintStyle]}>
              Swipe to browse · Tap a cover to play
            </Animated.Text>
          </>
        )}
      </View>
    </GestureDetector>
  );
}

type CoverProps = {
  album: VinylAlbum;
  index: number;
  count: number;
  scroll: SharedValue<number>;
  open: SharedValue<number>;
  size: number;
  centreX: number;
  centreY: number;
  reducedMotion: boolean;
};

/** One sleeve standing on the floor, with its reflection under it. */
function Cover({ album, index, count, scroll, open, size, centreX, centreY, reducedMotion }: CoverProps) {
  const style = useAnimatedStyle(() => {
    const d = centred(index - scroll.get(), count);
    const p = placement(d);
    const o = clamp(open.get(), 0, 1.2);
    const front = Math.max(0, 1 - Math.abs(d));
    // Opening moves the front cover up and left, and spreads the rest outwards.
    const x = p.x * size + Math.sign(d) * o * SPREAD * size * (1 - front) - front * o * SHIFT * size;
    const lift = reducedMotion ? 0 : front * open.get() * LIFT;
    return {
      opacity: p.alpha * (1 - (1 - front) * clamp(o, 0, 1) * 0.85),
      transform: [
        { perspective: size * PERSPECTIVE },
        { translateX: reducedMotion ? p.x * size : x },
        { translateY: -lift },
        { rotateY: `${p.turn}deg` },
        { scale: p.scale },
      ],
    };
  });
  // iOS depth-sorts sibling layers turned in 3D against each other; each cover gets a flat
  // layer of its own to be drawn into, stacked by its distance from the front.
  const layerStyle = useAnimatedStyle(() => ({
    zIndex: 100 - Math.round(Math.abs(centred(index - scroll.get(), count)) * 10),
  }));
  const shadeStyle = useAnimatedStyle(() => ({ opacity: placement(centred(index - scroll.get(), count)).dim }));
  // The reflection is dimmed through its own opacity (not the shade), so under it is always
  // exactly the floor's colour and no box shows round it.
  const reflectionStyle = useAnimatedStyle(() => {
    const dim = placement(centred(index - scroll.get(), count)).dim;
    return { opacity: REFLECTION_STRENGTH * (1 - dim) * (1 - clamp(open.get(), 0, 1) * 0.6) };
  });

  return (
    <Animated.View pointerEvents="none" style={[StyleSheet.absoluteFill, layerStyle]}>
      <Animated.View
        style={[styles.cover, { width: size, height: size * 2, left: centreX - size / 2, top: centreY - size / 2 }, style]}
      >
        <View style={[styles.sleeve, { width: size, height: size }]}>
          <Image source={{ uri: album.cover }} style={StyleSheet.absoluteFill} contentFit="cover" transition={300} />
          <View style={styles.sleeveEdge} />
        </View>
        <View style={[styles.reflection, { width: size, height: size }]}>
          <Animated.View style={[StyleSheet.absoluteFill, reflectionStyle]}>
            <Image source={{ uri: album.cover }} style={[StyleSheet.absoluteFill, styles.flipped]} contentFit="cover" />
          </Animated.View>
          <LinearGradient
            colors={['rgba(13,12,11,0)', BG, BG]}
            locations={[0, REFLECTION, 1]}
            style={StyleSheet.absoluteFill}
          />
        </View>
        <Animated.View style={[styles.shade, { height: size }, shadeStyle]} />
      </Animated.View>
    </Animated.View>
  );
}

type CaptionProps = { album: VinylAlbum; index: number; count: number; scroll: SharedValue<number> };

/** An album's title and artist, crossfading with the flow as its cover comes to the front. */
function Caption({ album, index, count, scroll }: CaptionProps) {
  const style = useAnimatedStyle(() => {
    const d = centred(index - scroll.get(), count);
    return { opacity: clamp(1 - Math.abs(d) * 2.2, 0, 1), transform: [{ translateX: d * -24 }] };
  });
  return (
    <Animated.View style={[styles.captionInner, style]}>
      <Text style={styles.title} numberOfLines={1}>
        {album.title}
      </Text>
      <Text style={styles.artist} numberOfLines={1}>
        {album.artist}
      </Text>
    </Animated.View>
  );
}

type RecordProps = {
  album: VinylAlbum;
  open: SharedValue<number>;
  spin: SharedValue<number>;
  size: number;
  centreX: number;
  centreY: number;
  reducedMotion: boolean;
};

// The grooves: rings of slightly different greys, with three gaps between tracks.
const GROOVES = Array.from({ length: 46 }, (_, k) => k);
const GAPS = [11, 23, 34];

/** A black record behind the front sleeve; it slides out to the right and spins. */
function Record({ album, open, spin, size, centreX, centreY, reducedMotion }: RecordProps) {
  const disc = size * RECORD_SIZE;
  const r = disc / 2;
  const label = disc * LABEL;
  const style = useAnimatedStyle(() => {
    const o = open.get();
    const lift = reducedMotion ? 0 : clamp(o, 0, 1.2) * LIFT;
    return {
      opacity: reducedMotion ? clamp(o, 0, 1) : clamp(o * 6, 0, 1),
      transform: [
        { translateX: reducedMotion ? (RECORD_OUT - SHIFT) * size : o * (RECORD_OUT - SHIFT) * size },
        { translateY: -lift },
      ],
    };
  });
  const labelStyle = useAnimatedStyle(() => ({ transform: [{ rotate: `${spin.get()}deg` }] }));

  return (
    <Animated.View
      pointerEvents="none"
      style={[styles.record, { width: disc, height: disc, left: centreX - r, top: centreY - r }, style]}
    >
      <Canvas style={StyleSheet.absoluteFill}>
        <Circle cx={r} cy={r} r={r} color="#090909" />
        {GROOVES.map((k) => {
          const radius = label / 2 + 6 + ((r - label / 2 - 10) * k) / (GROOVES.length - 1);
          const gap = GAPS.includes(k);
          return (
            <Circle
              key={k}
              cx={r}
              cy={r}
              r={radius}
              style="stroke"
              strokeWidth={gap ? 1.6 : 0.7}
              color={gap ? 'rgba(0,0,0,0.95)' : k % 2 === 0 ? 'rgba(255,255,255,0.075)' : 'rgba(255,255,255,0.04)'}
            />
          );
        })}
        <Circle cx={r} cy={r} r={r - 1} style="stroke" strokeWidth={1.4} color="rgba(255,255,255,0.12)" />
        {/* The smooth run-out round the label. */}
        <Circle cx={r} cy={r} r={label / 2 + 5} color="#050505" />
        {/* The light on the grooves stays put while the record turns under it. */}
        <Group>
          <Circle cx={r} cy={r} r={r - 2}>
            <SweepGradient
              c={vec(r, r)}
              colors={[
                'rgba(255,255,255,0)',
                'rgba(255,255,255,0.2)',
                'rgba(255,255,255,0)',
                'rgba(255,255,255,0)',
                'rgba(255,255,255,0.15)',
                'rgba(255,255,255,0)',
                'rgba(255,255,255,0)',
              ]}
              positions={[0.04, 0.12, 0.22, 0.54, 0.62, 0.72, 1]}
            />
          </Circle>
        </Group>
      </Canvas>
      <Animated.View style={[styles.label, { width: label, height: label, borderRadius: label / 2, left: r - label / 2, top: r - label / 2 }, labelStyle]}>
        <Image source={{ uri: album.cover }} style={StyleSheet.absoluteFill} contentFit="cover" />
        <View style={[styles.hole, { left: label / 2 - 4, top: label / 2 - 4 }]} />
      </Animated.View>
    </Animated.View>
  );
}

const styles = StyleSheet.create({
  screen: {
    flex: 1,
    backgroundColor: BG,
    overflow: 'hidden',
  },
  header: {
    position: 'absolute',
    zIndex: 200,
    left: 22,
    right: 22,
    flexDirection: 'row',
    alignItems: 'flex-end',
    justifyContent: 'space-between',
  },
  heading: {
    flexShrink: 0,
    color: INK,
    fontSize: 30,
    fontWeight: '700',
    letterSpacing: -0.8,
  },
  count: {
    color: MUTED,
    fontSize: 13,
    fontWeight: '500',
    letterSpacing: 0.3,
  },
  cover: {
    position: 'absolute',
  },
  sleeve: {
    borderRadius: 4,
    overflow: 'hidden',
    backgroundColor: '#1c1a17',
  },
  sleeveEdge: {
    position: 'absolute', top: 0, right: 0, bottom: 0, left: 0,
    borderRadius: 4,
    borderWidth: StyleSheet.hairlineWidth,
    borderColor: 'rgba(255,255,255,0.18)',
  },
  wall: {
    position: 'absolute',
    top: 0,
    left: 0,
    right: 0,
  },
  reflection: {
    marginTop: 3,
    backgroundColor: BG,
    borderRadius: 4,
    overflow: 'hidden',
  },
  flipped: {
    transform: [{ scaleY: -1 }],
  },
  shade: {
    position: 'absolute',
    top: 0,
    left: 0,
    right: 0,
    borderRadius: 4,
    backgroundColor: '#000000',
  },
  record: {
    position: 'absolute',
    // Between the front cover (100) and its neighbours (90 and below).
    zIndex: 96,
  },
  label: {
    position: 'absolute',
    overflow: 'hidden',
    borderWidth: 2,
    borderColor: '#141414',
  },
  hole: {
    position: 'absolute',
    width: 8,
    height: 8,
    borderRadius: 4,
    backgroundColor: BG,
  },
  caption: {
    position: 'absolute',
    zIndex: 200,
    left: 32,
    right: 32,
    height: 54,
  },
  captionInner: {
    position: 'absolute', top: 0, right: 0, bottom: 0, left: 0,
    alignItems: 'center',
    gap: 4,
  },
  title: {
    color: INK,
    fontSize: 22,
    fontWeight: '700',
    letterSpacing: -0.4,
  },
  artist: {
    color: MUTED,
    fontSize: 15,
    fontWeight: '500',
  },
  player: {
    position: 'absolute',
    zIndex: 200,
    left: 40,
    right: 40,
    gap: 8,
  },
  track: {
    height: 3,
    borderRadius: 1.5,
    overflow: 'hidden',
    backgroundColor: TRACK,
  },
  trackFill: {
    position: 'absolute',
    top: 0,
    bottom: 0,
    left: 0,
    backgroundColor: ACCENT,
  },
  times: {
    flexDirection: 'row',
    justifyContent: 'space-between',
  },
  time: {
    color: MUTED,
    fontSize: 12,
    fontWeight: '500',
    fontVariant: ['tabular-nums'],
  },
  controls: {
    marginTop: 8,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 44,
  },
  playButton: {
    width: 58,
    height: 58,
    borderRadius: 29,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: INK,
  },
  hint: {
    position: 'absolute',
    zIndex: 200,
    left: 0,
    right: 0,
    textAlign: 'center',
    color: MUTED,
    fontSize: 13,
    fontWeight: '500',
  },
});
