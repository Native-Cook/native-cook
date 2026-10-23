import { Image } from 'expo-image';
import * as Haptics from 'expo-haptics';
import { LinearGradient } from 'expo-linear-gradient';
import { useEffect, useState } from 'react';
import { type LayoutChangeEvent, StyleSheet, Text, useWindowDimensions, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  Easing,
  interpolateColor,
  type SharedValue,
  useAnimatedStyle,
  useFrameCallback,
  useReducedMotion,
  useSharedValue,
  withTiming,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

/** One show in the list. */
export type ChainEvent = {
  id: string;
  title: string;
  venue: string;
  city: string;
  month: string;
  day: string;
  times: string;
  price: string;
  blurb: string;
};

type Props = {
  events?: ChainEvent[];
  /** Flings the list down and back up, then opens a row and closes it again. */
  autoPlay?: boolean;
  /** Called with a show when its row is opened by hand. */
  onOpen?: (event: ChainEvent) => void;
};

const EVENTS: ChainEvent[] = [
  { id: 'photo-1459749411175-04bf5292ceea', title: 'Saltwater Choir', venue: 'Harbor Hall', city: 'Lisbon', month: 'OCT', day: '18', times: 'Doors 19:30 · Show 20:30', price: 'from €28', blurb: 'Twelve voices and a brass quartet, playing the new record from start to finish.' },
  { id: 'photo-1563841930606-67e2bce48b78', title: 'Neon Orchard', venue: 'The Lantern', city: 'Porto', month: 'OCT', day: '21', times: 'Doors 20:00 · Show 21:00', price: 'from €22', blurb: 'A synth-pop four-piece on their first tour since the debut sold out twice.' },
  { id: 'photo-1533174072545-7a4b6ad7a6c3', title: 'Marigold Static', venue: 'Canal Room', city: 'Amsterdam', month: 'OCT', day: '24', times: 'Doors 19:00 · Show 20:15', price: 'from €30', blurb: 'Fuzz guitars, a string section and a light show built from old slide projectors.' },
  { id: 'photo-1493225457124-a3eb161ffa5f', title: 'The Quiet Engines', venue: 'Vault 9', city: 'Berlin', month: 'OCT', day: '29', times: 'Doors 22:00 · Set 23:30', price: 'from €26', blurb: 'An all-night live techno set from the duo who never play the same show twice.' },
  { id: 'photo-1470229722913-7c0e2dbbafd3', title: 'Ivy Lane Ensemble', venue: 'Glasshouse', city: 'Copenhagen', month: 'NOV', day: '02', times: 'Doors 18:30 · Show 19:30', price: 'from €35', blurb: 'Chamber jazz in a greenhouse, with the audience seated among the palms.' },
  { id: 'photo-1429962714451-bb934ecdc4ec', title: 'Hollow Moon', venue: 'Riverside Shed', city: 'Ghent', month: 'NOV', day: '06', times: 'Doors 19:30 · Show 20:30', price: 'from €24', blurb: 'Slow, heavy and very loud. Earplugs at the door, free of charge.' },
  { id: 'photo-1514525253161-7a46d19cd819', title: 'Copper & Kin', venue: 'Old Tram Depot', city: 'Vienna', month: 'NOV', day: '09', times: 'Doors 19:00 · Show 20:00', price: 'from €32', blurb: 'A family folk band of three generations, back with fiddles and a full choir.' },
  { id: 'photo-1565035010268-a3816f98589a', title: 'Velvet Signal', venue: 'Sala Norte', city: 'Madrid', month: 'NOV', day: '14', times: 'Doors 21:00 · Show 22:00', price: 'from €27', blurb: 'Dream pop under a ceiling of mirror balls, with support from Lake Vesper.' },
  { id: 'photo-1524368535928-5b5e00ddc76b', title: 'North Atlas', venue: 'Pier 4', city: 'Hamburg', month: 'NOV', day: '20', times: 'Doors 19:30 · Show 20:45', price: 'from €29', blurb: 'Post-rock on the water: a three-hour set as the ferries go by.' },
  { id: 'photo-1540039155733-5bb30b53aa14', title: 'Paper Tigers Club', venue: 'The Annex', city: 'Brussels', month: 'NOV', day: '27', times: 'Doors 20:00 · Show 21:00', price: 'from €18', blurb: 'Garage punk in a room for two hundred. Expect to leave with less voice.' },
  { id: 'photo-1506157786151-b8491531f063', title: 'Lumen Field Day', venue: 'Cinder Park', city: 'Milan', month: 'DEC', day: '04', times: 'Gates 14:00 · Last set 23:00', price: 'from €40', blurb: 'Nine acts, two stages and a winter market between them.' },
  { id: 'photo-1522158637959-30385a09e0da', title: 'Saint Ember', venue: 'Chapel Works', city: 'Dublin', month: 'DEC', day: '12', times: 'Doors 19:00 · Show 20:00', price: 'from €25', blurb: 'Candlelit piano and voice, closing the year where the songs were written.' },
];

const thumb = (id: string) => `https://images.unsplash.com/${id}?auto=format&fit=crop&w=220&h=220&q=80`;

const BACKGROUND = '#0e0d12';
const ROW = '#18171d';
const ROW_PRESSED = '#26242d';
const CHIP = '#24222a';
const TEXT = '#f3eee6';
const MUTED = '#8d8896';
const ACCENT = '#ffb547';

// Layout, in pt.
const SIDE = 16;
const ROW_H = 92;
const GAP = 10;
const EXTRA = 132;
const THUMB = 68;
const RADIUS = 22;
const HEADER_H = 128;
const FADE = 28;

// The chain: each row is held to the list by its own spring and lags behind it while the list
// moves, LAG s of the list's speed for every row it is from the leading edge (up to LAG_MAX pt
// per row, RANK_MAX rows), so a fast list opens up like a stretched spring and closes again.
const LAG = 0.0105;
const LAG_MAX = 42;
const RANK_MAX = 9;
const LINK_STIFFNESS = 110;
const LINK_DAMPING = 14.7;
const SPEED_SMOOTH = 14;
// Past either end each row gives a little more than the one before it, OVER_SPREAD more per row
// from the end that is being pulled.
const OVER_SPREAD = 0.2;
// The list: iOS-like deceleration, resistance past the ends over RUBBER_SPAN of the screen, and
// a critically damped spring back.
const DECELERATION = 2.1;
const EDGE_STIFFNESS = 180;
const EDGE_DAMPING = 26.8;
const RUBBER_SPAN = 0.45;
// Opening a row: a spring on its height, with a little overshoot.
const OPEN_STIFFNESS = 210;
const OPEN_DAMPING = 22;
const PRESS_IN = 90;
const PRESS_OUT = 220;

// autoPlay (seconds).
const LOOP = 9;
const FLING_UP_AT = 0.8;
const FLING_DOWN_AT = 3.3;
const FLING_SPEED = 3600;
const PRESS_AT = 5.5;
const OPEN_AT = 5.65;
const CLOSE_AT = 7.4;
const DEMO_ROW = 1;
const USER_HOLD = 3.5;
// The first fling waits this long after the screen opens, so the list rests while it arrives.
const ENTRY_DELAY = 1.2;
// Only the rows that fit on screen are built with the screen; the rest follow one per frame after
// REST_AFTER ms (or at once on the first touch), so the first frame of the push stays light.
const REST_AFTER = 360; // ms
const ENTER_MS = 320;
const ENTER_RISE = 8; // pt
const EASE_OUT = Easing.bezier(0.23, 1, 0.32, 1);

function clamp(x: number, lo: number, hi: number) {
  'worklet';
  return Math.min(hi, Math.max(lo, x));
}

/** Whether two lists of numbers differ by more than a hair anywhere. */
function changed(a: number[], b: number[]) {
  'worklet';
  if (a.length !== b.length) return true;
  for (let i = 0; i < a.length; i += 1) if (Math.abs(a[i] - b[i]) > 1e-4) return true;
  return false;
}

function rubber(x: number, span: number) {
  'worklet';
  return (1 - 1 / ((x * 0.55) / span + 1)) * span;
}

function unrubber(r: number, span: number) {
  'worklet';
  const k = Math.min(0.999, r / span);
  return (span / 0.55) * (1 / (1 - k) - 1);
}

type RowProps = {
  item: ChainEvent;
  index: number;
  width: number;
  ys: SharedValue<number[]>;
  opens: SharedValue<number[]>;
  glows: SharedValue<number[]>;
  reducedMotion: boolean;
};

/** One show: the card grows on a spring when opened, and the details fade in inside it. */
function ChainRow({ item, index, width, ys, opens, glows, reducedMotion }: RowProps) {
  const place = useAnimatedStyle(() => ({ transform: [{ translateY: ys.get()[index] ?? 0 }] }));
  const card = useAnimatedStyle(() => ({
    height: ROW_H + EXTRA * Math.max(0, opens.get()[index] ?? 0),
    backgroundColor: interpolateColor(glows.get()[index] ?? 0, [0, 1], [ROW, ROW_PRESSED]),
  }));
  const details = useAnimatedStyle(() => {
    const e = clamp(opens.get()[index] ?? 0, 0, 1);
    const shown = e * e;
    return { opacity: shown, transform: [{ translateY: reducedMotion ? 0 : (1 - e) * -10 }] };
  });
  return (
    <Animated.View style={[styles.row, { width }, place]} pointerEvents="none">
      <Animated.View style={[styles.card, card]} />
      <View style={styles.summary}>
        <Image source={{ uri: thumb(item.id) }} style={styles.thumb} contentFit="cover" transition={250} />
        <View style={styles.titles}>
          <Text style={styles.title} numberOfLines={1}>
            {item.title}
          </Text>
          <Text style={styles.venue} numberOfLines={1}>
            {item.venue} · {item.city}
          </Text>
        </View>
        <View style={styles.chip}>
          <Text style={styles.month}>{item.month}</Text>
          <Text style={styles.day}>{item.day}</Text>
        </View>
      </View>
      <Animated.View style={[styles.details, details]}>
        <Text style={styles.times}>{item.times}</Text>
        <Text style={styles.blurb} numberOfLines={2}>
          {item.blurb}
        </Text>
        <View style={styles.buy}>
          <Text style={styles.price}>{item.price}</Text>
          <View style={styles.tickets}>
            <Text style={styles.ticketsText}>Tickets</Text>
          </View>
        </View>
      </Animated.View>
    </Animated.View>
  );
}

/**
 * A list of upcoming shows whose rows hang together like a chain of springs. Flung, the rows
 * drift apart, each lagging a little more than the one before it, and catch up with a springy
 * settle when the list slows; pulled past either end, the gaps open like a stretched spring.
 * Tapping a row highlights it and opens it on a spring to show the times, a line about the show
 * and its price.
 */
export function RubberChainList({ events = EVENTS, autoPlay = false, onOpen }: Props) {
  const insets = useSafeAreaInsets();
  const reducedMotion = useReducedMotion();
  const auto = autoPlay && !reducedMotion;
  const count = events.length;
  // The block fills the screen, so the window is the right size from the very first frame; the
  // layout only corrects it if the block is placed in something smaller. The rows are drawn in
  // place on the first frame instead of popping in a frame later.
  const window = useWindowDimensions();
  const [size, setSize] = useState({ width: window.width, height: window.height });
  const { width, height } = size;
  const listTop = insets.top + HEADER_H;
  const pitch = ROW_H + GAP;
  const firstRows = Math.min(count, Math.ceil((window.height - listTop) / pitch) + 1);
  const [built, setBuilt] = useState(firstRows);
  useEffect(() => {
    if (built >= count) return;
    let frame = 0;
    const timer = setTimeout(() => {
      frame = requestAnimationFrame(() => setBuilt((n) => Math.min(count, n + 1)));
    }, built <= firstRows ? REST_AFTER : 0);
    return () => {
      clearTimeout(timer);
      cancelAnimationFrame(frame);
    };
  }, [built, count, firstRows]);
  const buildAll = () => setBuilt(count);
  const shown = useSharedValue(0);
  // Nothing moves until the block has been laid out on screen: a screen can be mounted a while
  // before it is shown, and the entrance and the script must start when it is.
  const appeared = useSharedValue(false);
  const appear = () => {
    if (appeared.get()) return;
    appeared.set(true);
    shown.set(withTiming(1, { duration: reducedMotion ? 200 : ENTER_MS, easing: EASE_OUT }));
  };

  // The list's offset: 0 at the top, negative scrolled down; its speed and a smoothed speed.
  const scroll = useSharedValue(0);
  const velocity = useSharedValue(0);
  const smooth = useSharedValue(0);
  const mode = useSharedValue<'drag' | 'free'>('free');
  const dragFrom = useSharedValue(0);
  const last = useSharedValue(0);
  // Each row's lag behind the list and its speed, how open it is, and where it is drawn.
  const lags = useSharedValue<number[]>(events.map(() => 0));
  const lagSpeeds = useSharedValue<number[]>(events.map(() => 0));
  const opens = useSharedValue<number[]>(events.map(() => 0));
  const openSpeeds = useSharedValue<number[]>(events.map(() => 0));
  // Where every row is drawn, starting at its resting place under the header.
  const ys = useSharedValue<number[]>(events.map((_, i) => listTop + i * pitch));
  // The press highlight of each row, 0…1.
  const glows = useSharedValue<number[]>(events.map(() => 0));
  const openIndex = useSharedValue(-1);
  const pressed = useSharedValue(-1);
  const byUser = useSharedValue(false);
  const stage = useSharedValue({ top: listTop, bottom: height - insets.bottom });

  const clock = useSharedValue(-ENTRY_DELAY);
  const looping = useSharedValue(auto);
  const hold = useSharedValue(0);

  useEffect(() => {
    stage.set({ top: listTop, bottom: height - insets.bottom });
  }, [listTop, height, insets.bottom, stage]);
  useEffect(() => {
    looping.set(auto);
    clock.set(-ENTRY_DELAY);
    hold.set(0);
  }, [auto, clock, hold, looping]);

  const opened = (index: number, open: boolean) => {
    if (process.env.EXPO_OS !== 'web') Haptics.impactAsync(open ? Haptics.ImpactFeedbackStyle.Light : Haptics.ImpactFeedbackStyle.Soft).catch(() => {});
    if (open) onOpen?.(events[index]);
  };

  /** The lowest the list may go, with the open rows' extra height. */
  const floorOf = () => {
    'worklet';
    const { top, bottom } = stage.get();
    let open = 0;
    for (const e of opens.get()) open += Math.max(0, e);
    const content = count * (ROW_H + GAP) - GAP + open * EXTRA + 24;
    return Math.min(0, bottom - top - content);
  };

  const bounded = (raw: number) => {
    'worklet';
    // At least 1 pt, so a stage not yet measured never divides by zero.
    const span = Math.max(1, (stage.get().bottom - stage.get().top) * RUBBER_SPAN);
    const bottom = floorOf();
    if (raw > 0) return rubber(raw, span);
    if (raw < bottom) return bottom - rubber(bottom - raw, span);
    return raw;
  };

  /** Opens a row, closing the one that was open; or closes it if it is the open one. */
  const toggle = (index: number) => {
    'worklet';
    const next = openIndex.get() === index ? -1 : index;
    openIndex.set(next);
    if (byUser.get()) scheduleOnRN(opened, index, next === index);
  };

  useFrameCallback((frame) => {
    const dt = Math.min(frame.timeSincePreviousFrame ?? 16, 64) / 1000;
    if (dt <= 0 || !appeared.get()) return;
    const { top: viewTop, bottom: viewBottom } = stage.get();
    if (viewBottom <= 0) return;
    // A new list of a different length: start every row's state over at its size.
    if (opens.get().length !== count) {
      const zeros = () => new Array<number>(count).fill(0);
      lags.set(zeros());
      lagSpeeds.set(zeros());
      opens.set(zeros());
      openSpeeds.set(zeros());
      glows.set(zeros());
      ys.set(zeros().map((_, i) => viewTop + i * (ROW_H + GAP)));
      openIndex.set(-1);
      pressed.set(-1);
    }

    // The script flings and taps as a hand would.
    if (mode.get() !== 'drag' && hold.get() > 0) {
      hold.set(hold.get() - dt);
      if (hold.get() <= 0 && looping.get()) {
        clock.set(0);
        if (openIndex.get() >= 0) openIndex.set(-1);
        // Home first, so the loop starts from the top.
        if (scroll.get() < -1) velocity.set(FLING_SPEED);
      }
    } else if (looping.get() && mode.get() !== 'drag') {
      const before = clock.get();
      const t = (before + dt) % LOOP;
      clock.set(t);
      const passed = (at: number) => before < at && t >= at;
      if (passed(FLING_UP_AT) || passed(FLING_DOWN_AT)) {
        byUser.set(false);
        velocity.set(passed(FLING_UP_AT) ? -FLING_SPEED : FLING_SPEED);
      }
      if (passed(PRESS_AT)) pressed.set(DEMO_ROW);
      if (passed(OPEN_AT)) {
        pressed.set(-1);
        byUser.set(false);
        toggle(DEMO_ROW);
      }
      if (passed(CLOSE_AT) && openIndex.get() === DEMO_ROW) {
        byUser.set(false);
        toggle(DEMO_ROW);
      }
    }

    // The list itself.
    const bottom = floorOf();
    if (mode.get() === 'free') {
      const s = scroll.get();
      let v = velocity.get();
      if (s > 0.01 || s < bottom - 0.01) {
        const edge = s > 0 ? 0 : bottom;
        v += (EDGE_STIFFNESS * (edge - s) - EDGE_DAMPING * v) * dt;
        let next = s + v * dt;
        if ((s > 0 && next < 0) || (s < bottom && next > bottom)) {
          next = edge;
          v = 0;
        }
        scroll.set(reducedMotion ? edge : next);
      } else {
        v *= Math.exp(-DECELERATION * dt);
        if (Math.abs(v) < 4) v = 0;
        scroll.set(s + v * dt);
      }
      velocity.set(v);
    } else {
      velocity.set(velocity.get() + ((scroll.get() - last.get()) / dt - velocity.get()) * 0.5);
    }
    last.set(scroll.get());
    smooth.set(smooth.get() + (velocity.get() - smooth.get()) * (1 - Math.exp(-dt * SPEED_SMOOTH)));

    // The open rows' heights.
    const open = opens.get().slice();
    const openV = openSpeeds.get().slice();
    for (let i = 0; i < count; i += 1) {
      const aim = openIndex.get() === i ? 1 : 0;
      if (reducedMotion) {
        open[i] = clamp(open[i] + Math.sign(aim - open[i]) * dt * 5, Math.min(aim, open[i]), Math.max(aim, open[i]));
        openV[i] = 0;
      } else {
        openV[i] += (OPEN_STIFFNESS * (aim - open[i]) - OPEN_DAMPING * openV[i]) * dt;
        open[i] += openV[i] * dt;
      }
    }
    // Only a value that changed is written, so a list at rest does no work on the rows' styles.
    if (changed(open, opens.get()) || changed(openV, openSpeeds.get())) {
      opens.set(open);
      openSpeeds.set(openV);
    }
    // The highlight comes up fast under the finger and fades out slower.
    const glow = glows.get().slice();
    for (let i = 0; i < count; i += 1) {
      const on = pressed.get() === i;
      const step = (dt * 1000) / (on ? PRESS_IN : PRESS_OUT);
      glow[i] = on ? Math.min(1, glow[i] + step) : Math.max(0, glow[i] - step);
    }
    if (changed(glow, glows.get())) glows.set(glow);

    // Each row: its place in the list, how far past an end the list is, and its lag.
    const s = scroll.get();
    const over = s > 0 ? s : s < bottom ? s - bottom : 0;
    const v = reducedMotion ? 0 : smooth.get();
    const lag = lags.get().slice();
    const lagV = lagSpeeds.get().slice();
    const out: number[] = [];
    const pitch = ROW_H + GAP;
    let y = viewTop + s;
    for (let i = 0; i < count; i += 1) {
      const at = y + lag[i];
      // Rows count from the edge the list is moving away from: from the top while it moves up.
      const rank = v < 0 ? clamp((at - viewTop) / pitch, 0, RANK_MAX) : clamp((viewBottom - at - ROW_H) / pitch, 0, RANK_MAX);
      const aim = -Math.sign(v) * Math.min(Math.abs(v) * LAG, LAG_MAX) * rank;
      lagV[i] += (LINK_STIFFNESS * (aim - lag[i]) - LINK_DAMPING * lagV[i]) * dt;
      lag[i] += lagV[i] * dt;
      if (reducedMotion) {
        lag[i] = 0;
        lagV[i] = 0;
      }
      // Past an end, the rows further from it give more.
      const fromEnd = over > 0 ? clamp((at - viewTop) / pitch, 0, RANK_MAX) : clamp((viewBottom - at - ROW_H) / pitch, 0, RANK_MAX);
      const stretch = reducedMotion ? 0 : over * fromEnd * OVER_SPREAD;
      out.push(y + lag[i] + stretch);
      y += pitch + EXTRA * Math.max(0, open[i]);
    }
    if (changed(lag, lags.get()) || changed(lagV, lagSpeeds.get())) {
      lags.set(lag);
      lagSpeeds.set(lagV);
    }
    if (changed(out, ys.get())) ys.set(out);
  });

  /** The row under a point, from where the rows are drawn. */
  const rowAt = (pointY: number) => {
    'worklet';
    const drawn = ys.get();
    const open = opens.get();
    for (let i = 0; i < count; i += 1) {
      if (pointY >= drawn[i] && pointY <= drawn[i] + ROW_H + EXTRA * Math.max(0, open[i])) return i;
    }
    return -1;
  };

  // One finger scrolls: a second would move the pan's centroid and jump the list.
  const pan = Gesture.Pan()
    .minDistance(6)
    .maxPointers(1)
    .onStart(() => {
      scheduleOnRN(buildAll);
      hold.set(USER_HOLD);
      byUser.set(true);
      pressed.set(-1);
      const s = scroll.get();
      const span = Math.max(1, (stage.get().bottom - stage.get().top) * RUBBER_SPAN);
      const bottom = floorOf();
      dragFrom.set(s > 0 ? unrubber(s, span) : s < bottom ? bottom - unrubber(bottom - s, span) : s);
      last.set(s);
      mode.set('drag');
    })
    .onUpdate((event) => {
      if (mode.get() !== 'drag') return;
      hold.set(USER_HOLD);
      scroll.set(bounded(dragFrom.get() + event.translationY));
    })
    .onEnd((event) => {
      if (mode.get() !== 'drag') return;
      velocity.set(reducedMotion ? 0 : event.velocityY);
      mode.set('free');
    })
    .onFinalize(() => {
      if (mode.get() === 'drag') mode.set('free');
    });

  const tap = Gesture.Tap()
    .maxDuration(500)
    .onBegin((event) => {
      scheduleOnRN(buildAll);
      hold.set(USER_HOLD);
      // A touch catches a moving list, as on iOS.
      velocity.set(0);
      pressed.set(rowAt(event.y));
    })
    .onEnd((event, success) => {
      if (!success) return;
      const index = rowAt(event.y);
      if (index < 0) return;
      byUser.set(true);
      toggle(index);
    })
    .onFinalize(() => {
      pressed.set(-1);
    });

  // The content settles in once when the screen opens: the words fade in and the rows fade up a
  // few points, so a screen that arrives a moment late never pops.
  const headerIn = useAnimatedStyle(() => ({ opacity: shown.get() }));
  const listIn = useAnimatedStyle(() => ({
    opacity: shown.get(),
    transform: [{ translateY: reducedMotion ? 0 : (1 - shown.get()) * ENTER_RISE }],
  }));

  return (
    <GestureDetector gesture={Gesture.Race(pan, tap)}>
      <View
        style={[styles.screen, { backgroundColor: BACKGROUND }]}
        accessibilityLabel={`${count} upcoming shows. Tap a show to open it.`}
        onLayout={(event: LayoutChangeEvent) => {
          const { width: w, height: h } = event.nativeEvent.layout;
          if (w !== width || h !== height) setSize({ width: w, height: h });
          if (w > 0 && h > 0) appear();
        }}
      >
        <Animated.View style={[StyleSheet.absoluteFill, listIn]} pointerEvents="none">
          {events.slice(0, built).map((item, index) => (
            <ChainRow
              key={item.id}
              item={item}
              index={index}
              width={width - SIDE * 2}
              ys={ys}
              opens={opens}
              glows={glows}
              reducedMotion={reducedMotion}
            />
          ))}
        </Animated.View>
        <View pointerEvents="none" style={[styles.header, { height: listTop, paddingTop: insets.top + 18 }]}>
          <Animated.View style={[styles.headerText, headerIn]}>
            <Text style={styles.kicker}>LIVE NEAR YOU</Text>
            <Text style={styles.heading}>Tonight & After</Text>
            <Text style={styles.subheading}>{count} shows · October to December</Text>
          </Animated.View>
        </View>
        <LinearGradient
          pointerEvents="none"
          colors={[BACKGROUND, `${BACKGROUND}00`]}
          style={[styles.fade, { top: listTop - 1, height: FADE }]}
        />
        <LinearGradient
          pointerEvents="none"
          colors={[`${BACKGROUND}00`, BACKGROUND]}
          style={[styles.fade, { bottom: 0, height: insets.bottom + FADE * 2 }]}
        />
      </View>
    </GestureDetector>
  );
}

const styles = StyleSheet.create({
  screen: {
    flex: 1,
    overflow: 'hidden',
  },
  header: {
    position: 'absolute',
    top: 0,
    left: 0,
    right: 0,
    paddingHorizontal: SIDE + 4,
    backgroundColor: BACKGROUND,
  },
  headerText: {
    gap: 4,
  },
  kicker: {
    color: ACCENT,
    fontSize: 11,
    fontWeight: '700',
    letterSpacing: 2,
  },
  heading: {
    color: TEXT,
    fontSize: 34,
    fontWeight: '700',
    letterSpacing: -0.6,
  },
  subheading: {
    color: MUTED,
    fontSize: 14,
    fontWeight: '500',
  },
  fade: {
    position: 'absolute',
    left: 0,
    right: 0,
  },
  row: {
    position: 'absolute',
    top: 0,
    left: SIDE,
    height: ROW_H + EXTRA,
  },
  card: {
    position: 'absolute',
    top: 0,
    left: 0,
    right: 0,
    borderRadius: RADIUS,
    borderCurve: 'continuous',
  },
  summary: {
    height: ROW_H,
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 12,
    gap: 14,
  },
  thumb: {
    width: THUMB,
    height: THUMB,
    borderRadius: 16,
    borderCurve: 'continuous',
    backgroundColor: CHIP,
  },
  titles: {
    flex: 1,
    gap: 4,
  },
  title: {
    color: TEXT,
    fontSize: 17,
    fontWeight: '600',
    letterSpacing: -0.2,
  },
  venue: {
    color: MUTED,
    fontSize: 13,
    fontWeight: '500',
  },
  chip: {
    width: 52,
    height: 60,
    borderRadius: 14,
    borderCurve: 'continuous',
    backgroundColor: CHIP,
    alignItems: 'center',
    justifyContent: 'center',
  },
  month: {
    color: ACCENT,
    fontSize: 11,
    fontWeight: '700',
    letterSpacing: 1.2,
  },
  day: {
    color: TEXT,
    fontSize: 22,
    fontWeight: '700',
    fontVariant: ['tabular-nums'],
  },
  details: {
    position: 'absolute',
    top: ROW_H - 2,
    left: 14,
    right: 14,
    gap: 6,
  },
  times: {
    color: ACCENT,
    fontSize: 13,
    fontWeight: '600',
    fontVariant: ['tabular-nums'],
  },
  blurb: {
    color: TEXT,
    opacity: 0.78,
    fontSize: 14,
    lineHeight: 20,
  },
  buy: {
    marginTop: 6,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  price: {
    color: TEXT,
    fontSize: 15,
    fontWeight: '600',
  },
  tickets: {
    backgroundColor: ACCENT,
    borderRadius: 18,
    paddingHorizontal: 18,
    height: 36,
    justifyContent: 'center',
  },
  ticketsText: {
    color: '#1a1408',
    fontSize: 14,
    fontWeight: '700',
  },
});
