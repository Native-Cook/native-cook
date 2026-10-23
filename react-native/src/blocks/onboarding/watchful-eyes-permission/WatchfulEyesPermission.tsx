import { Canvas, Circle, Group, Path, RadialGradient, RoundedRect, Skia, vec } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { LinearGradient } from 'expo-linear-gradient';
import { StatusBar } from 'expo-status-bar';
import { useEffect, useMemo, useState } from 'react';
import { type LayoutChangeEvent, StyleSheet, Text, View, useWindowDimensions } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  type SharedValue,
  useAnimatedReaction,
  useAnimatedStyle,
  useDerivedValue,
  useFrameCallback,
  useReducedMotion,
  useSharedValue,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

import {
  ALL,
  ALL_OFF,
  ALL_ON,
  ASK,
  BUBBLE,
  CHAT,
  CHAT_TOP,
  CHECK,
  CHECK_STROKE,
  COUNT,
  DONE,
  EYES,
  HALO,
  ITEM,
  LABEL_Y,
  LATER,
  LIST,
  LOOP,
  MARK,
  PHONE,
  PILL,
  PLAY,
  REST,
  ROW,
  SMILE,
  SMILE_STROKE,
  SWITCHES,
  type Stage,
  type Switches,
  TAP_ALL,
  TAP_CHAT,
  TAP_LATER,
  TAP_NONE,
  TAP_PILL,
  TAP_START,
  TAP_SWITCH,
  TILE,
  TITLE_Y,
  TOGGLE,
  USER_HOLD,
  allOn,
  ask,
  chat,
  check,
  clamp01,
  eyes,
  flip,
  follow,
  heartPath,
  hit,
  house,
  housePath,
  item,
  knob,
  lookDown,
  outline,
  outlinePath,
  pill,
  pressed,
  rain,
  rainDrops,
  ready,
  seen,
  shake,
  switchesOff,
  toward,
} from './timeline';

export type ChatMessage = {
  text: string;
  /** The user's own reply, on the right. */
  mine?: boolean;
};

type Props = {
  /** The chat the screen opens on, top to bottom. */
  messages?: ChatMessage[];
  /** What the eyes say when they ask. */
  message?: string;
  startLabel?: string;
  laterLabel?: string;
  /** The name under the tile on the switches' page. */
  title?: string;
  allOnLabel?: string;
  allOffLabel?: string;
  buttonLabel?: string;
  doneLabel?: string;
  /** The colour of a switch that is on. */
  tint?: string;
  /** Plays the four pages by itself, back at the chat every LOOP seconds. */
  autoPlay?: boolean;
  onStart?: () => void;
  onLater?: () => void;
  /** Called when every switch is on and the check mark shows. */
  onDone?: () => void;
};

const MESSAGES: ChatMessage[] = [
  { text: 'Hmm, sounds great!', mine: true },
  { text: '☝️ Statistically, 81% of my users feel significant sleep improvement during the first 4 weeks. And I have a question that perfectly predicts how well we match' },
  { text: 'Good idea 🤝', mine: true },
  { text: 'Wow, we have a few interesting things in common! Usually, people who choose these options are impressed with what I have to offer 😎' },
  { text: 'Intriguing! Please, show me 🚀', mine: true },
  { text: "Super! Let's start by quickly analyzing your sleep and habits 🕵️ Sometimes, it helps to uncover curious insights right away" },
  { text: "Let's see what you can find out 🤓", mine: true },
];

// Night: black, a band of navy low on the screen, black again.
const SKY = ['#050508', '#050508', '#0a1124', '#11182d', '#05070d', '#030305'] as const;
const SKY_AT = [0, 0.28, 0.55, 0.7, 0.86, 1] as const;
const EYE = '#f4ede2';
// The eyes' glow is a disc that fades to nothing at its rim, not a blur.
const GLOW = ['rgba(244,237,226,0.34)', 'rgba(244,237,226,0.13)', 'rgba(244,237,226,0.035)', 'rgba(244,237,226,0)'];
const GLOW_AT = [0, 0.4, 0.75, 1];
const HALO_LIGHT = ['rgba(255,255,255,0.1)', 'rgba(255,255,255,0.035)', 'rgba(255,255,255,0)'];
const HALO_AT = [0, 0.5, 1];
/** A smiling eye is flatter than an open one. */
const SQUINT = 0.5;
const HOUSE_FILL = '#191e21';
const HOUSE_EDGE = 'rgba(255,255,255,0.1)';
const RAIN_INK = 'rgba(255,255,255,0.12)';
const OUTLINE = 'rgba(255,255,255,0.22)';
const INK = '#d9dee8';
const FAINT = 'rgba(255,255,255,0.35)';
const PANEL = 'rgba(255,255,255,0.05)';
const SOFT = 'rgba(255,255,255,0.04)';
const HEART_INK = '#ff2d55';
const BAR = '#212a41';
const TRACK = '#2a3145';
const ALL_INK = '#5b6b8c';
const DISC = ['#142030', '#142030', 'rgba(20,32,48,0)'];
const DISC_AT = [0, 0.55, 1];
const CHECK_INK = '#5fe0bc';
// The chat's bubbles keep clear of the screen's sides by these, the assistant's stop `short` of the
// user's on the right, and the small tile on the button is this large.
const CHAT_SIDES = { left: 32, right: 43, short: 5 };
const SMALL_TILE = { size: 26, radius: 7, heart: 13 };

const HOUSE = Skia.Path.MakeFromSVGString(housePath())!;
const HEART = Skia.Path.MakeFromSVGString(heartPath())!;
const CHECK_MARK = Skia.Path.MakeFromSVGString(`M${CHECK[0]} ${CHECK[1]}L${CHECK[2]} ${CHECK[3]}L${CHECK[4]} ${CHECK[5]}`)!;
const SMILING = Skia.Path.MakeFromSVGString(`M${SMILE[0]} ${SMILE[1]}Q${SMILE[2]} ${SMILE[3]} ${SMILE[4]} ${SMILE[5]}`)!;
const RAIN = Skia.Path.Make();
for (const [x, y, r] of rainDrops()) RAIN.addCircle(x, y, r);

const ACTIVATE = [{ name: 'activate' as const }];
const ROWS = Array.from({ length: COUNT }, (_, row) => row);

/** What the screen is drawn from: the stage, the seconds into it, and how much of the pages shows. */
type Seen = { from: number; to: number; e: number; opacity: number };
/** The eyes as drawn: their middle, size, gap, glow, halo, height as a share of their width, and smile. */
type Pose = { x: number; y: number; size: number; gap: number; glow: number; halo: number; tall: number; smile: number };
/** Where a finger makes the eyes look, since when, and whether it is still down. */
type Look = { x: number; y: number; since: number; held: boolean };

function tick() {
  if (process.env.EXPO_OS !== 'web') Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
}

function select() {
  if (process.env.EXPO_OS !== 'web') Haptics.selectionAsync().catch(() => {});
}

function Eye({ side, pose }: { side: number; pose: SharedValue<Pose> }) {
  const glow = useDerivedValue(() => {
    const p = pose.get();
    return [{ translateX: p.x + (side * p.gap) / 2 }, { translateY: p.y }, { scale: p.size / 2 + p.glow }];
  });
  // A shut eye gives no light.
  const glowOpacity = useDerivedValue(() => Math.min(1, pose.get().tall));
  const disc = useDerivedValue(() => {
    const p = pose.get();
    return [{ translateX: p.x + (side * p.gap) / 2 }, { translateY: p.y }, { scaleX: p.size / 2 }, { scaleY: (p.size / 2) * p.tall * (1 - SQUINT * p.smile) }];
  });
  const discOpacity = useDerivedValue(() => 1 - pose.get().smile);
  const arc = useDerivedValue(() => {
    const p = pose.get();
    return [{ translateX: p.x + (side * p.gap) / 2 }, { translateY: p.y }, { scale: p.size }];
  });
  const arcOpacity = useDerivedValue(() => pose.get().smile);
  return (
    <>
      <Group transform={glow} opacity={glowOpacity}>
        <Circle cx={0} cy={0} r={1}>
          <RadialGradient c={vec(0, 0)} r={1} colors={GLOW} positions={GLOW_AT} />
        </Circle>
      </Group>
      <Group transform={disc} opacity={discOpacity}>
        <Circle cx={0} cy={0} r={1} color={EYE} />
      </Group>
      <Group transform={arc} opacity={arcOpacity}>
        <Path path={SMILING} style="stroke" strokeWidth={SMILE_STROKE} strokeCap="round" color={EYE} />
      </Group>
    </>
  );
}

type SwitchProps = {
  row: number;
  /** The row's left end and its middle line. */
  x: number;
  y: number;
  tint: string;
  view: SharedValue<Seen>;
  switches: SharedValue<Switches>;
  clock: SharedValue<number>;
  still: boolean;
};

/** A row of the list: a bar standing in for a category's name, and its switch. */
function Switch({ row, x, y, tint, view, switches, clock, still }: SwitchProps) {
  const transform = useDerivedValue(() => {
    const v = view.get();
    return [{ translateX: x }, { translateY: y + item(v.from, v.to, v.e, ITEM.row + row).rise }];
  });
  const opacity = useDerivedValue(() => {
    const v = view.get();
    return item(v.from, v.to, v.e, ITEM.row + row).opacity;
  });
  const on = useDerivedValue(() => {
    const s = switches.get();
    return knob(s.on[row], clock.get() - s.at[row], still);
  });
  const cx = useDerivedValue(() => LIST.width - TOGGLE.width + TOGGLE.height / 2 + (TOGGLE.width - TOGGLE.height) * on.get());
  // The track of a switch that is on, faded in over the one that is off.
  const lit = useDerivedValue(() => clamp01(on.get()));
  return (
    <Group transform={transform} opacity={opacity}>
      <RoundedRect x={0} y={-LIST.thick / 2} width={LIST.bar} height={LIST.thick} r={LIST.thick / 2} color={BAR} />
      <RoundedRect x={LIST.width - TOGGLE.width} y={-TOGGLE.height / 2} width={TOGGLE.width} height={TOGGLE.height} r={TOGGLE.height / 2} color={TRACK} />
      <RoundedRect x={LIST.width - TOGGLE.width} y={-TOGGLE.height / 2} width={TOGGLE.width} height={TOGGLE.height} r={TOGGLE.height / 2} color={tint} opacity={lit} />
      <Circle cx={cx} cy={0} r={TOGGLE.knob / 2} color="#ffffff" />
    </Group>
  );
}

/**
 * A permission primer told by a pair of glowing eyes. They sit over a chat, come down and grow
 * to ask for health data from inside a little home, fly back up while a phone's outline draws
 * itself, watch six switches flip on, and smile at the check mark. They blink, glance, startle
 * and shake a "no" on their own clock, and look towards a finger.
 */
export function WatchfulEyesPermission({
  messages = MESSAGES,
  message = "Don't worry, this analysis happens right on your phone, and your data remains securely protected on device.",
  startLabel = 'Start Health analysis',
  laterLabel = 'Later',
  title = 'Health',
  allOnLabel = 'Turn on all',
  allOffLabel = 'Turn off all',
  buttonLabel = 'Turn on all categories',
  doneLabel = 'Perfect!',
  tint = '#7fe3c4',
  autoPlay = false,
  onStart,
  onLater,
  onDone,
}: Props) {
  const still = useReducedMotion();
  const insets = useSafeAreaInsets();
  const window = useWindowDimensions();
  // The screen as laid out; the window's size stands in until then.
  const [laidOut, setLaidOut] = useState<{ width: number; height: number } | null>(null);
  const { width, height } = laidOut ?? window;
  const center = width / 2;
  const middle = height / 2;
  const base = Math.max(insets.bottom, 16);
  const floor = height - base;
  const eyesTop = insets.top + EYES.top;

  // Seconds since the first frame.
  const clock = useSharedValue(0);
  const firstFrame = useSharedValue(-1);
  // The chat, as if it had been arrived at a while ago: every loop begins like this.
  const stage = useSharedValue<Stage>({ from: DONE, to: CHAT, t0: -REST });
  const switches = useSharedValue<Switches>(switchesOff());
  // The last tap that did something, the last "no", and the finger the eyes look at.
  const press = useSharedValue({ target: TAP_NONE, at: -REST });
  const shook = useSharedValue(-REST);
  const look = useSharedValue<Look>({ x: 0, y: 0, since: -REST, held: false });
  // autoPlay: when its loop began, which of its taps is next, and when it may start over after a touch.
  const start = useSharedValue(Infinity);
  const step = useSharedValue(0);
  const resume = useSharedValue(Infinity);
  // The page, for the screen reader only.
  const [page, setPage] = useState(CHAT);

  useEffect(() => {
    start.set(Infinity);
    resume.set(autoPlay ? 0 : Infinity);
  }, [autoPlay, start, resume]);

  useAnimatedReaction(
    () => stage.get().to,
    (to, before) => {
      if (to !== before) scheduleOnRN(setPage, to);
    },
  );

  const started = () => onStart?.();
  const later = () => onLater?.();
  const finished = () => onDone?.();

  const go = (to: number, at: number) => {
    'worklet';
    stage.set({ from: stage.get().to, to, t0: at });
    // The switches' page always opens with everything off; it is not on the screen yet.
    if (to === SWITCHES) switches.set(switchesOff());
  };

  /** Does what a tap means, as of `at`. A touch and autoPlay both come through here. */
  const act = (action: number, at: number) => {
    'worklet';
    const s = stage.get();
    if (action === TAP_NONE || !ready(s.to, at - s.t0)) return false;
    if (s.to === CHAT) {
      go(ASK, at);
    } else if (s.to === ASK && action === TAP_START) {
      go(SWITCHES, at);
      scheduleOnRN(started);
    } else if (s.to === ASK && action === TAP_LATER) {
      shook.set(at);
      scheduleOnRN(later);
    } else if (s.to === SWITCHES && action === TAP_ALL) {
      switches.set(flip(switches.get(), switches.get().all ? ALL_OFF : ALL_ON, at));
    } else if (s.to === SWITCHES && action === TAP_PILL) {
      switches.set(flip(switches.get(), ALL_ON, at));
    } else if (s.to === SWITCHES && action >= TAP_SWITCH) {
      switches.set(flip(switches.get(), action - TAP_SWITCH, at));
    } else if (s.to === DONE && action === TAP_PILL) {
      go(CHAT, at);
    } else {
      return false;
    }
    press.set({ target: action, at });
    return true;
  };

  useFrameCallback((frame) => {
    if (firstFrame.get() < 0) firstFrame.set(frame.timestamp);
    const now = (frame.timestamp - firstFrame.get()) / 1000;
    clock.set(now);
    // Every switch on: the last page follows, from when it was due.
    const due = switches.get().doneAt;
    if (stage.get().to === SWITCHES && now >= due) {
      go(DONE, due);
      scheduleOnRN(finished);
    }
    if (!autoPlay) return;
    if (now >= resume.get()) {
      // autoPlay begins, or the touches are over: back to the chat, and on with the loop from there.
      resume.set(Infinity);
      if (stage.get().to === CHAT) stage.set({ from: CHAT, to: CHAT, t0: now - REST });
      else go(CHAT, now);
      start.set(stage.get().t0 + REST);
      step.set(0);
      return;
    }
    const index = step.get();
    const next = start.get() + (index < PLAY.length ? PLAY[index][0] : LOOP);
    if (now < next) return;
    // Timed from when it was due, not from this frame: every loop is the same length.
    if (index < PLAY.length) {
      act(PLAY[index][1], next);
      step.set(index + 1);
    } else {
      start.set(next);
      step.set(0);
    }
  });

  /** A touch takes over from autoPlay, which goes back to the chat and starts again a while later. */
  const touched = () => {
    'worklet';
    if (!autoPlay) return;
    start.set(Infinity);
    resume.set(clock.get() + USER_HOLD);
  };

  const view = useDerivedValue(() => seen(stage.get(), clock.get(), still));
  const pose = useDerivedValue<Pose>(() => {
    const v = view.get();
    const p = eyes(v.from, v.to, v.e, still, eyesTop, middle + EYES.hover, middle + EYES.ask);
    if (still) return { ...p, x: center };
    const now = clock.get();
    const l = look.get();
    const f = follow(l.held, now - l.since);
    return { ...p, x: center + p.dx + shake(now - shook.get()) + l.x * f, y: p.y + lookDown(now - switches.get().flippedAt) + l.y * f };
  });

  const tap = Gesture.Tap().onEnd((event, success) => {
    if (!success) return;
    touched();
    const action = hit(stage.get().to, event.x, event.y, width, height, floor);
    if (act(action, clock.get())) scheduleOnRN(action >= TAP_SWITCH ? select : tick);
  });

  // The eyes look towards a finger for as long as it is down.
  const pan = Gesture.Pan()
    .onBegin((event) => {
      touched();
      look.set({ x: toward(event.x - center), y: toward(event.y - pose.get().y), since: clock.get(), held: true });
    })
    .onUpdate((event) => {
      const l = look.get();
      if (l.held) look.set({ ...l, x: toward(event.x - center), y: toward(event.y - pose.get().y) });
    })
    .onFinalize(() => {
      const l = look.get();
      if (l.held) look.set({ ...l, since: clock.get(), held: false });
    });

  /** How much a tapped button gives. */
  const give = (target: number) => {
    'worklet';
    const p = press.get();
    return still || p.target !== target ? 1 : pressed(clock.get() - p.at);
  };

  const pagesStyle = useAnimatedStyle(() => ({ opacity: view.get().opacity }));
  const chatStyle = useAnimatedStyle(() => {
    const v = view.get();
    const c = chat(v.from, v.to, v.e);
    return { opacity: c.opacity, transform: [{ translateY: c.shift }] };
  });
  const messageStyle = useAnimatedStyle(() => {
    const v = view.get();
    const a = ask(v.from, v.to, v.e, 0);
    return { opacity: a.opacity, transform: [{ translateY: a.shift }] };
  });
  const startStyle = useAnimatedStyle(() => {
    const v = view.get();
    const a = ask(v.from, v.to, v.e, 1);
    return { opacity: a.opacity, transform: [{ translateY: a.shift }, { scale: give(TAP_START) }] };
  });
  const laterStyle = useAnimatedStyle(() => {
    const v = view.get();
    const a = ask(v.from, v.to, v.e, 2);
    return { opacity: a.opacity, transform: [{ translateY: a.shift }, { scale: give(TAP_LATER) }] };
  });
  // What shrinks into the check mark moves towards it as well.
  const titleStyle = useAnimatedStyle(() => {
    const v = view.get();
    const i = item(v.from, v.to, v.e, ITEM.title);
    return { opacity: i.opacity, transform: [{ translateY: i.rise + (TITLE_Y - MARK.y) * (i.scale - 1) }, { scale: i.scale }] };
  });
  const allStyle = useAnimatedStyle(() => {
    const v = view.get();
    const i = item(v.from, v.to, v.e, ITEM.all);
    return {
      opacity: i.opacity,
      transform: [{ translateY: i.rise + (ALL.top + ALL.height / 2 - MARK.y) * (i.scale - 1) }, { scale: i.scale * give(TAP_ALL) }],
    };
  });
  const allOnStyle = useAnimatedStyle(() => ({ opacity: 1 - allOn(switches.get(), clock.get()) }));
  const allOffStyle = useAnimatedStyle(() => ({ opacity: allOn(switches.get(), clock.get()) }));
  const pillStyle = useAnimatedStyle(() => {
    const v = view.get();
    return { opacity: pill(v.from, v.to, v.e), transform: [{ scale: give(TAP_PILL) }] };
  });
  const doneStyle = useAnimatedStyle(() => {
    const v = view.get();
    const c = check(v.from, v.to, v.e);
    return { opacity: c.label, transform: [{ translateY: c.rise }] };
  });

  const haloAt = useDerivedValue(() => [{ translateX: pose.get().x }, { translateY: pose.get().y }]);
  const haloOpacity = useDerivedValue(() => pose.get().halo);
  const houseAt = useDerivedValue(() => {
    const v = view.get();
    return [{ translateX: center }, { translateY: middle + EYES.ask }, { scale: house(v.from, v.to, v.e).scale }];
  });
  const houseOpacity = useDerivedValue(() => {
    const v = view.get();
    return house(v.from, v.to, v.e).opacity;
  });
  const rainAt = useDerivedValue(() => [{ translateY: rain(clock.get(), still) }]);
  const phone = useMemo(() => Skia.Path.MakeFromSVGString(outlinePath(center, middle + PHONE.top, height + PHONE.radius))!, [center, middle, height]);
  const phoneOpacity = useDerivedValue(() => {
    const v = view.get();
    return outline(v.from, v.to, v.e).opacity;
  });
  const phoneDrawn = useDerivedValue(() => {
    const v = view.get();
    return outline(v.from, v.to, v.e).drawn;
  });
  const shrink = useDerivedValue(() => {
    const v = view.get();
    const k = item(v.from, v.to, v.e, ITEM.tile).scale;
    return [{ translateX: center }, { translateY: middle + MARK.y }, { scale: k }, { translateX: -center }, { translateY: -middle - MARK.y }];
  });
  const tileAt = useDerivedValue(() => {
    const v = view.get();
    return [{ translateX: center }, { translateY: middle + TILE.y + item(v.from, v.to, v.e, ITEM.tile).rise }];
  });
  const tileOpacity = useDerivedValue(() => {
    const v = view.get();
    return item(v.from, v.to, v.e, ITEM.tile).opacity;
  });
  const markAt = useDerivedValue(() => {
    const v = view.get();
    return [{ translateX: center }, { translateY: middle + MARK.y }, { scale: check(v.from, v.to, v.e).scale }];
  });
  const markOpacity = useDerivedValue(() => {
    const v = view.get();
    return check(v.from, v.to, v.e).opacity;
  });
  const markDrawn = useDerivedValue(() => {
    const v = view.get();
    return check(v.from, v.to, v.e).drawn;
  });

  const onLayout = (event: LayoutChangeEvent) => {
    const { width: w, height: h } = event.nativeEvent.layout;
    setLaidOut({ width: w, height: h });
  };

  /** What a screen reader needs of a button that is drawn, not pressed: its page, and what a tap on it means. */
  const control = (on: number, action: number) => ({
    accessible: page === on,
    accessibilityRole: 'button' as const,
    accessibilityElementsHidden: page !== on,
    importantForAccessibility: page === on ? ('yes' as const) : ('no-hide-descendants' as const),
    accessibilityActions: ACTIVATE,
    onAccessibilityAction: () => {
      act(action, clock.get());
    },
  });

  return (
    <GestureDetector gesture={Gesture.Race(pan, tap)}>
      <View style={styles.screen} onLayout={onLayout}>
        <StatusBar style="light" />
        <LinearGradient colors={SKY} locations={SKY_AT} style={styles.fill} />

        <Animated.View style={[styles.fill, pagesStyle]}>
          <Animated.View style={[styles.chat, { top: insets.top + CHAT_TOP }, chatStyle]} {...control(CHAT, TAP_CHAT)}>
            {messages.map((one, index) => (
              <View key={index} style={[styles.bubble, one.mine ? styles.mine : styles.theirs]}>
                <Text style={styles.bubbleText}>{one.text}</Text>
              </View>
            ))}
          </Animated.View>

          <Canvas style={styles.fill} pointerEvents="none">
            {/* The soft light the eyes throw while they are large. */}
            <Group transform={haloAt} opacity={haloOpacity}>
              <Circle cx={0} cy={0} r={HALO}>
                <RadialGradient c={vec(0, 0)} r={HALO} colors={HALO_LIGHT} positions={HALO_AT} />
              </Circle>
            </Group>

            <Group transform={houseAt} opacity={houseOpacity}>
              <Path path={HOUSE} color={HOUSE_FILL} />
              <Group clip={HOUSE}>
                <Path path={RAIN} color={RAIN_INK} transform={rainAt} />
              </Group>
              <Path path={HOUSE} style="stroke" strokeWidth={1} color={HOUSE_EDGE} />
            </Group>

            {/* The phone's outline, drawn from the middle of the notch to both sides at once. */}
            <Group opacity={phoneOpacity}>
              <Path path={phone} style="stroke" strokeWidth={1} color={OUTLINE} end={phoneDrawn} />
              <Group transform={[{ translateX: width }, { scaleX: -1 }]}>
                <Path path={phone} style="stroke" strokeWidth={1} color={OUTLINE} end={phoneDrawn} />
              </Group>
            </Group>

            <Group transform={shrink}>
              <Group transform={tileAt} opacity={tileOpacity}>
                <RoundedRect x={-TILE.size / 2} y={-TILE.size / 2} width={TILE.size} height={TILE.size} r={TILE.radius} color="#ffffff" />
                <Path path={HEART} color={HEART_INK} transform={[{ scale: TILE.heart / 2 }]} />
              </Group>
              {ROWS.map((row) => (
                <Switch
                  key={row}
                  row={row}
                  x={center - LIST.width / 2}
                  y={middle + LIST.top + row * LIST.pitch}
                  tint={tint}
                  view={view}
                  switches={switches}
                  clock={clock}
                  still={still}
                />
              ))}
            </Group>

            <Group transform={markAt} opacity={markOpacity}>
              <Circle cx={0} cy={0} r={MARK.disc / 2}>
                <RadialGradient c={vec(0, 0)} r={MARK.disc / 2} colors={DISC} positions={DISC_AT} />
              </Circle>
              <Path path={CHECK_MARK} style="stroke" strokeWidth={CHECK_STROKE} strokeCap="round" strokeJoin="round" color={CHECK_INK} end={markDrawn} />
            </Group>

            <Eye side={-1} pose={pose} />
            <Eye side={1} pose={pose} />
          </Canvas>

          <Animated.View style={[styles.message, { bottom: base + BUBBLE.bottom }, messageStyle]} accessibilityElementsHidden={page !== ASK} importantForAccessibility={page === ASK ? 'auto' : 'no-hide-descendants'}>
            <Text style={styles.messageText}>{message}</Text>
          </Animated.View>
          <Animated.View style={[styles.start, { bottom: base + ROW.bottom }, startStyle]} {...control(ASK, TAP_START)}>
            <View style={styles.smallTile}>
              <Canvas style={styles.smallHeart}>
                <Path path={HEART} color={HEART_INK} transform={[{ translateX: SMALL_TILE.size / 2 }, { translateY: SMALL_TILE.size / 2 }, { scale: SMALL_TILE.heart / 2 }]} />
              </Canvas>
            </View>
            <Text style={styles.label} numberOfLines={1}>
              {startLabel}
            </Text>
          </Animated.View>
          <Animated.View style={[styles.later, { bottom: base + LATER.bottom - LATER.height / 2 }, laterStyle]} {...control(ASK, TAP_LATER)}>
            <Text style={styles.laterText} numberOfLines={1}>
              {laterLabel}
            </Text>
          </Animated.View>

          <Animated.Text
            style={[styles.title, { top: middle + TITLE_Y - 11 }, titleStyle]}
            numberOfLines={1}
            accessibilityElementsHidden={page !== SWITCHES}
            importantForAccessibility={page === SWITCHES ? 'auto' : 'no'}
          >
            {title}
          </Animated.Text>
          <Animated.View style={[styles.all, { top: middle + ALL.top, left: center - LIST.width / 2 }, allStyle]} {...control(SWITCHES, TAP_ALL)}>
            <Animated.Text style={[styles.allText, allOnStyle]} numberOfLines={1}>
              {allOnLabel}
            </Animated.Text>
            <Animated.Text style={[styles.allText, allOffStyle]} numberOfLines={1}>
              {allOffLabel}
            </Animated.Text>
          </Animated.View>
          <Animated.Text
            style={[styles.title, { top: middle + LABEL_Y - 11 }, doneStyle]}
            numberOfLines={1}
            accessibilityElementsHidden={page !== DONE}
            importantForAccessibility={page === DONE ? 'auto' : 'no'}
          >
            {doneLabel}
          </Animated.Text>
          <Animated.View style={[styles.pill, { bottom: base + PILL.bottom }, pillStyle]} {...control(page === DONE ? DONE : SWITCHES, TAP_PILL)}>
            <Text style={styles.label} numberOfLines={1}>
              {buttonLabel}
            </Text>
          </Animated.View>
        </Animated.View>
      </View>
    </GestureDetector>
  );
}

const styles = StyleSheet.create({
  screen: {
    flex: 1,
    backgroundColor: SKY[0],
    overflow: 'hidden',
  },
  fill: {
    position: 'absolute',
    left: 0,
    right: 0,
    top: 0,
    bottom: 0,
  },
  chat: {
    position: 'absolute',
    left: CHAT_SIDES.left,
    right: CHAT_SIDES.right,
    gap: 8,
  },
  bubble: {
    alignSelf: 'flex-start',
    paddingHorizontal: 14,
    paddingVertical: 10.5,
    borderRadius: 18,
    backgroundColor: PANEL,
  },
  mine: {
    alignSelf: 'flex-end',
  },
  theirs: {
    marginRight: CHAT_SIDES.short,
  },
  bubbleText: {
    color: INK,
    fontSize: 13.5,
    lineHeight: 21,
    fontWeight: '500',
    includeFontPadding: false,
  },
  message: {
    position: 'absolute',
    left: BUBBLE.side,
    right: BUBBLE.side,
    paddingHorizontal: 20,
    paddingVertical: 13.5,
    borderRadius: 22,
    backgroundColor: SOFT,
  },
  messageText: {
    color: INK,
    fontSize: 16.5,
    lineHeight: 26,
    fontWeight: '500',
    includeFontPadding: false,
  },
  start: {
    position: 'absolute',
    left: ROW.side,
    right: ROW.side,
    height: ROW.height,
    borderRadius: 18,
    backgroundColor: SOFT,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 10,
  },
  smallTile: {
    width: SMALL_TILE.size,
    height: SMALL_TILE.size,
    borderRadius: SMALL_TILE.radius,
    backgroundColor: '#ffffff',
  },
  smallHeart: {
    width: SMALL_TILE.size,
    height: SMALL_TILE.size,
  },
  label: {
    color: '#ffffff',
    fontSize: 14,
    lineHeight: 18,
    fontWeight: '500',
    includeFontPadding: false,
  },
  later: {
    position: 'absolute',
    left: 0,
    right: 0,
    height: LATER.height,
    alignItems: 'center',
    justifyContent: 'center',
  },
  laterText: {
    color: FAINT,
    fontSize: 13,
    lineHeight: 18,
    fontWeight: '500',
    includeFontPadding: false,
  },
  title: {
    position: 'absolute',
    left: 0,
    right: 0,
    color: '#ffffff',
    fontSize: 17,
    lineHeight: 22,
    fontWeight: '500',
    textAlign: 'center',
    includeFontPadding: false,
  },
  all: {
    position: 'absolute',
    width: LIST.width,
    height: ALL.height,
    borderRadius: 8,
    backgroundColor: PANEL,
  },
  allText: {
    position: 'absolute',
    left: 12,
    top: (ALL.height - 18) / 2,
    color: ALL_INK,
    fontSize: 13,
    lineHeight: 18,
    includeFontPadding: false,
  },
  pill: {
    position: 'absolute',
    left: PILL.side,
    right: PILL.side,
    height: PILL.height,
    borderRadius: PILL.height / 2,
    backgroundColor: SOFT,
    alignItems: 'center',
    justifyContent: 'center',
  },
});
