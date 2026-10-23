import { Feather } from '@expo/vector-icons';
import * as Haptics from 'expo-haptics';
import { LinearGradient } from 'expo-linear-gradient';
import { StatusBar } from 'expo-status-bar';
import { type ComponentProps, type ReactNode, useEffect, useRef, useState } from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import Animated, {
  Easing,
  FadeIn,
  FadeInDown,
  FadeOut,
  FadeOutUp,
  LayoutAnimationConfig,
  ReduceMotion,
  type SharedValue,
  interpolate,
  useAnimatedStyle,
  useReducedMotion,
  useSharedValue,
  withSpring,
  withTiming,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { GENRE_PANEL, GenreDrum, PICK_MS } from './GenreDrum';
import { TIMES, TIME_PANEL, TimeDial, WHEEL_SPRING, timeLabel } from './TimeDial';

export type Friend = {
  /** Two letters on the disc; without them the disc shows a drawn head and shoulders. */
  initials?: string;
  color: string;
};

export type Filters = {
  date: string;
  /** Whether only events friends attend are wanted. */
  friends: boolean;
  /** The time, or the start and the end of the range, in half hours since midnight. */
  time: number | [number, number];
  genres: string[];
  /** Null for any price. */
  price: string | null;
};

type Props = {
  title?: string;
  /** The dates the "Dates" row goes through, a tap at a time; the first one is shown. */
  dates?: string[];
  /** The friends whose pictures turn in the "Friends attend" row, and how many there are. */
  friends?: Friend[];
  friendsCount?: string;
  /** The time the form starts on, in half hours since midnight: 38 is 7:00 pm. */
  time?: number;
  /** Every genre, in the order the right wheel lists them. */
  genres?: string[];
  /** The genres chosen at the start. */
  chosenGenres?: string[];
  /** What the "Price" row goes through; the first one means any price. */
  prices?: string[];
  datesLabel?: string;
  friendsLabel?: string;
  anyFriendsLabel?: string;
  timeLabel?: string;
  singleTimeLabel?: string;
  timeRangeLabel?: string;
  genresLabel?: string;
  allGenresLabel?: string;
  noGenresLabel?: string;
  priceLabel?: string;
  resetLabel?: string;
  continueLabel?: string;
  /** Before the number of filters that are set, on the button. */
  withLabel?: string;
  /** Opens the rows, turns the wheels and resets by itself every LOOP seconds. */
  autoPlay?: boolean;
  onChange?: (filters: Filters) => void;
  onContinue?: (filters: Filters) => void;
  onClose?: () => void;
};

const DATES = ['18 Dec 2024', '19 Dec 2024', '20 Dec 2024', '21 Dec 2024'];
const FRIENDS: Friend[] = [{ initials: 'MT', color: '#4d8df0' }, { color: '#2fa58f' }, { color: '#d98a4e' }];
const GENRES = [
  'Afrobeat', 'Alternative', 'Bluegrass', 'Blues', 'Chant', 'Classical', 'Country', 'Dancehall',
  'Disco', 'EDM', 'Electronic', 'Experimental', 'Folk', 'Funk', 'Gospel', 'Hip-Hop',
  'House', 'Indie', 'Jazz', 'Latin', 'Metal', 'Pop', 'Reggae', 'Soul',
];
const CHOSEN = ['Afrobeat', 'Bluegrass', 'Chant', 'EDM', 'Gospel', 'Jazz', 'Latin', 'Soul'];
const PRICES = ['All price', 'Free', 'Under $30', 'Under $60'];

const BACKDROP = '#000000';
const SHEET = '#121214';
const ROW_FILL = '#1c1c1f';
const ROW_PRESSED = '#26262a';
// The picker inside an open row sits in a well darker than the row.
const WELL = '#161618';
const INK = '#ffffff';
const MUTE = '#8e8e93';
const BLUE = '#1f6bff';

const SIDE = 20;
const ROW = 60;
const ROW_GAP = 16;
const ROW_RADIUS = 16;
const WELL_INSET = 6;
const TIME_EXTRA = TIME_PANEL + WELL_INSET;
const GENRE_EXTRA = GENRE_PANEL + WELL_INSET;
const AVATAR = 26;
// The friends' pictures are a column this far apart, so the next one shows at the row's edge.
const AVATAR_PITCH = 34;
const BUTTON = 52;
const BUTTON_WIDTH = 158;
// The rows fade out over this height above the bottom bar.
const FADE = 44;
// An open row keeps this much clear of the fade: the list slides up when it would not.
const CLEAR = 8;
// The genre that starts in the middle of the right wheel, as a place in the list of the others.
const RIGHT_START = 3;

const NEVER = ReduceMotion.Never;
// A row springs open and shut with a little overshoot.
const OPEN_SPRING = { damping: 14.5, stiffness: 115, mass: 1, reduceMotion: NEVER };
const RANGE_MS = 380;
const TICKER_SPRING = { damping: 16, stiffness: 150, mass: 1, reduceMotion: NEVER };
const PRESS_MS = 160;

/** autoPlay: one round every LOOP seconds; the moments below are seconds into it. */
export const LOOP = 10.5;
const OPEN_TIME_AT = 0.8;
const RANGE_AT = 1.9;
// The start wheel is carried back this many half hours.
const TURN_TIME_AT = 2.6;
const TURN_TIME_BY = -5;
const TURN_TIME_FOR = 1.2;
const OPEN_GENRES_AT = 4.4;
const TURN_GENRES_AT = 5.4;
const TURN_GENRES_BY = 5;
const TURN_GENRES_FOR = 1;
const PICK_AT = 6.9;
const CLOSE_AT = 7.9;
const RESET_AT = 8.9;
/** A friend's picture gives way to the next this often: a third of LOOP, so three make a round. */
const TICK = LOOP / 3;
/** After a touch autoPlay waits this long before it starts over. */
const USER_HOLD = 3;

type Expanded = 'time' | 'genres' | null;
type FeatherName = ComponentProps<typeof Feather>['name'];

function tap() {
  if (process.env.EXPO_OS !== 'web') Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
}

function select() {
  if (process.env.EXPO_OS !== 'web') Haptics.selectionAsync().catch(() => {});
}

/** A drag by autoPlay: a wheel is carried over `seconds`, as a finger would carry it. */
function carry(seconds: number) {
  return { duration: seconds * 1000, easing: Easing.inOut(Easing.cubic), reduceMotion: NEVER };
}

function Avatar({ friend, index, count, turn }: { friend: Friend; index: number; count: number; turn: SharedValue<number> }) {
  // The pictures go round: one that leaves at the top comes back from the bottom, out of sight.
  const style = useAnimatedStyle(() => {
    const away = ((((index - turn.get() + count / 2) % count) + count) % count) - count / 2;
    return { transform: [{ translateY: away * AVATAR_PITCH }] };
  });
  return (
    <Animated.View style={[styles.avatar, { backgroundColor: friend.color }, style]}>
      {friend.initials ? (
        <Text style={styles.initials}>{friend.initials}</Text>
      ) : (
        <>
          <View style={styles.head} />
          <View style={styles.shoulders} />
        </>
      )}
    </Animated.View>
  );
}

function Row({
  icon,
  label,
  pressed,
  extra = 0,
  shown,
  expanded,
  hint,
  onPress,
  value,
  children,
}: {
  icon: FeatherName;
  label: string;
  /** Held down by the autoPlay loop. */
  pressed?: boolean;
  /** How much taller the row is when open, and how open it is. */
  extra?: number;
  shown?: SharedValue<number>;
  expanded?: boolean;
  hint?: string;
  onPress: () => void;
  value: ReactNode;
  /** The picker of a row that opens. */
  children?: ReactNode;
}) {
  const grow = useAnimatedStyle(() => ({ height: ROW + extra * Math.max(0, shown ? shown.get() : 0) }));
  const well = useAnimatedStyle(() => {
    const open = shown ? shown.get() : 0;
    return { opacity: interpolate(open, [0.1, 0.7], [0, 1], 'clamp'), transform: [{ translateY: (1 - Math.min(1, open)) * -12 }] };
  });
  return (
    <Animated.View style={[styles.row, grow]}>
      <Pressable
        style={styles.rowHead}
        accessibilityRole="button"
        accessibilityHint={hint}
        accessibilityState={children ? { expanded } : undefined}
        onPress={onPress}
      >
        {({ pressed: held }) => (
          <>
            <Animated.View
              style={[
                styles.rowTint,
                { opacity: held || pressed ? 1 : 0, transitionProperty: 'opacity', transitionDuration: held || pressed ? 60 : 260 },
              ]}
            />
            <Feather name={icon} size={18} color={MUTE} />
            <Text style={styles.rowLabel} numberOfLines={1}>
              {label}
            </Text>
            <View style={styles.rowValue}>{value}</View>
          </>
        )}
      </Pressable>
      {children && (
        <Animated.View
          style={[styles.well, { height: extra - WELL_INSET }, well]}
          pointerEvents={expanded ? 'auto' : 'none'}
          accessibilityElementsHidden={!expanded}
          importantForAccessibility={expanded ? 'auto' : 'no-hide-descendants'}
        >
          {children}
        </Animated.View>
      )}
    </Animated.View>
  );
}

/** A row's value: a new one fades in over the old one. */
function Value({ text, dim, tail }: { text: string; dim?: boolean; tail?: string }) {
  return (
    <Animated.Text
      key={text + (tail ?? '')}
      style={[styles.value, dim && styles.valueDim]}
      numberOfLines={1}
      entering={FadeIn.duration(220)}
      exiting={FadeOut.duration(140)}
    >
      {text}
      {tail ? <Text style={styles.valueTail}>{tail}</Text> : null}
    </Animated.Text>
  );
}

/**
 * A filter sheet whose rows open in place. "Time" springs open into an analog clock tied to a
 * wheel of times, with a second wheel and a blue arc for a range; "Genres" opens two facing lists
 * on the rims of two wheels, where a tap moves a genre across. Opening one row closes the other,
 * and the button counts the filters that are set. Nothing is searched: `onContinue` gets the filters.
 */
export function ExpandingFilterRows({
  title = 'Filters',
  dates = DATES,
  friends = FRIENDS,
  friendsCount = '12+',
  time = 38,
  genres = GENRES,
  chosenGenres = CHOSEN,
  prices = PRICES,
  datesLabel = 'Dates',
  friendsLabel = 'Friends attend',
  anyFriendsLabel = 'Anyone',
  timeLabel: timeRowLabel = 'Time',
  singleTimeLabel = 'Single time',
  timeRangeLabel = 'Time range',
  genresLabel = 'Genres',
  allGenresLabel = 'All genres',
  noGenresLabel = 'Tap a genre',
  priceLabel = 'Price',
  resetLabel = 'Reset all',
  continueLabel = 'Continue',
  withLabel = 'with',
  autoPlay = false,
  onChange,
  onContinue,
  onClose,
}: Props) {
  const reducedMotion = useReducedMotion();
  const insets = useSafeAreaInsets();
  const firstTime = Math.min(TIMES - 1, Math.max(0, Math.round(time)));

  const [expanded, setExpanded] = useState<Expanded>(null);
  const [dateIndex, setDateIndex] = useState(0);
  const [withFriends, setWithFriends] = useState(true);
  const [priceIndex, setPriceIndex] = useState(0);
  const [ranged, setRanged] = useState(false);
  const [startTime, setStartTime] = useState(firstTime);
  const [endTime, setEndTime] = useState(firstTime);
  const [chosen, setChosen] = useState(chosenGenres);
  // The genre on its way to the other column, and the one that just arrived there.
  const [moving, setMoving] = useState<string | null>(null);
  const [fresh, setFresh] = useState<string | null>(null);
  // What the autoPlay loop holds down.
  const [pressed, setPressed] = useState<'time' | 'genres' | 'reset' | null>(null);
  // Changes on every touch, which restarts the autoPlay loop behind a pause.
  const [touches, setTouches] = useState(0);
  // The height the rows have to themselves, down to the screen's bottom edge.
  const [room, setRoom] = useState(0);
  const pending = useRef<ReturnType<typeof setTimeout>[]>([]);

  const timeShown = useSharedValue(0);
  const genresShown = useSharedValue(0);
  // The wheels: the time in the middle of each, in half hours, and 0 to 1 for the second wheel.
  const start = useSharedValue(firstTime);
  const end = useSharedValue(firstTime);
  const range = useSharedValue(0);
  // The drum: the name in the middle of each column, as a place in its list.
  const leftPosition = useSharedValue(0);
  const rightPosition = useSharedValue(RIGHT_START);
  // The friend whose picture is in the row, counting up for ever.
  const turn = useSharedValue(0);

  const others = genres.filter((genre) => !chosen.includes(genre));
  const price = priceIndex === 0 ? null : prices[priceIndex];
  // Dates and time always narrow the search; the other three only when something is picked.
  const count = 2 + (withFriends ? 1 : 0) + (chosen.length > 0 ? 1 : 0) + (price ? 1 : 0);
  const filters: Filters = {
    date: dates[dateIndex],
    friends: withFriends,
    time: ranged ? [startTime, endTime] : startTime,
    genres: chosen,
    price,
  };

  const report = useRef({ onChange, filters });
  useEffect(() => {
    report.current = { onChange, filters };
  });
  useEffect(() => {
    report.current.onChange?.(report.current.filters);
  }, [dateIndex, withFriends, priceIndex, ranged, startTime, endTime, chosen]);

  useEffect(() => () => pending.current.forEach(clearTimeout), []);

  /** A touch takes over from the autoPlay loop, which starts again from rest a while later. */
  const touched = () => setTouches((n) => n + 1);

  const expand = (next: Expanded) => {
    setExpanded(next);
    const move = (to: number) => (reducedMotion ? withTiming(to, { duration: 160, reduceMotion: NEVER }) : withSpring(to, OPEN_SPRING));
    timeShown.set(move(next === 'time' ? 1 : 0));
    genresShown.set(move(next === 'genres' ? 1 : 0));
  };

  const toggle = (row: 'time' | 'genres') => expand(expanded === row ? null : row);

  const setMode = (next: boolean) => {
    setRanged(next);
    if (next) {
      // A range starts as no range at all: both wheels on the same time.
      end.set(start.get());
      setEndTime(Math.round(start.get()));
    }
    range.set(withTiming(next ? 1 : 0, { duration: RANGE_MS, easing: Easing.out(Easing.cubic), reduceMotion: NEVER }));
  };

  // A range runs forward: a wheel turned past the other takes it along.
  const onStart = (half: number, byHand: boolean) => {
    setStartTime(half);
    if (byHand) select();
    if (ranged && half > Math.round(end.get())) end.set(withSpring(half, WHEEL_SPRING));
  };
  const onEnd = (half: number, byHand: boolean) => {
    setEndTime(half);
    if (byHand) select();
    if (ranged && half < Math.round(start.get())) start.set(withSpring(half, WHEEL_SPRING));
  };

  /** Moves a genre to the other column: it fills or fades where it is, then shows up over there. */
  const move = (name: string, pick: boolean) => {
    if (moving) return;
    setMoving(name);
    pending.current.push(
      setTimeout(() => {
        // A picked genre goes in right under the one in the middle, where it can be seen.
        const at = Math.min(chosen.length, Math.round(leftPosition.get()) + 1);
        setChosen(pick ? [...chosen.slice(0, at), name, ...chosen.slice(at)] : chosen.filter((genre) => genre !== name));
        setFresh(name);
        setMoving(null);
      }, PICK_MS),
    );
  };

  const reset = () => {
    expand(null);
    setDateIndex(0);
    setWithFriends(true);
    setPriceIndex(0);
    setRanged(false);
    setStartTime(firstTime);
    setEndTime(firstTime);
    setChosen(chosenGenres);
    setFresh(null);
    // The pickers are shut, or shutting: their wheels go back at once, out of sight.
    range.set(withTiming(0, { duration: RANGE_MS, reduceMotion: NEVER }));
    start.set(firstTime);
    end.set(firstTime);
    leftPosition.set(0);
    rightPosition.set(RIGHT_START);
  };

  // The loop reads the latest state when its timers fire.
  const latest = useRef({ toggle, setMode, move, reset, others });
  useEffect(() => {
    latest.current = { toggle, setMode, move, reset, others };
  });

  useEffect(() => {
    if (!autoPlay) return;
    const timers: ReturnType<typeof setTimeout>[] = [];
    const after = (ms: number, run: () => void) => timers.push(setTimeout(run, ms));
    const cycle = (origin: number) => {
      const at = (seconds: number, run: () => void) => after(origin + seconds * 1000 - Date.now(), run);
      // A press shows on the row for a moment before it acts, as a finger's would.
      const press = (seconds: number, what: 'time' | 'genres' | 'reset', run: () => void) => {
        at(seconds, () => setPressed(what));
        at(seconds + PRESS_MS / 1000, () => {
          setPressed(null);
          run();
        });
      };
      press(OPEN_TIME_AT, 'time', () => latest.current.toggle('time'));
      at(RANGE_AT, () => latest.current.setMode(true));
      at(TURN_TIME_AT, () => start.set(withTiming(firstTime + TURN_TIME_BY, carry(TURN_TIME_FOR))));
      press(OPEN_GENRES_AT, 'genres', () => latest.current.toggle('genres'));
      at(TURN_GENRES_AT, () => rightPosition.set(withTiming(RIGHT_START + TURN_GENRES_BY, carry(TURN_GENRES_FOR))));
      at(PICK_AT, () => latest.current.move(latest.current.others[RIGHT_START + TURN_GENRES_BY], true));
      press(CLOSE_AT, 'genres', () => latest.current.toggle('genres'));
      press(RESET_AT, 'reset', () => latest.current.reset());
      at(LOOP, () => cycle(origin + LOOP * 1000));
    };
    if (touches === 0) cycle(Date.now());
    else {
      // The loop starts over from the form as it was given.
      after(USER_HOLD * 1000, () => latest.current.reset());
      after(USER_HOLD * 1000 + 900, () => cycle(Date.now()));
    }
    return () => {
      timers.forEach(clearTimeout);
      setPressed(null);
    };
  }, [autoPlay, touches, firstTime, start, rightPosition]);

  // The friends' pictures take turns in their row.
  useEffect(() => {
    if (reducedMotion || friends.length < 2) return;
    const origin = Date.now();
    let step = 0;
    let timer: ReturnType<typeof setTimeout>;
    const next = () => {
      step += 1;
      timer = setTimeout(() => {
        turn.set(withSpring(step, TICKER_SPRING));
        next();
      }, origin + step * TICK * 1000 - Date.now());
    };
    next();
    return () => clearTimeout(timer);
  }, [reducedMotion, friends.length, turn]);

  const bottom = Math.max(insets.bottom, 12) + 8;
  // Where the rows have to end to stay clear of the bar and its fade.
  const limit = room - bottom - BUTTON - FADE - CLEAR;
  const listStyle = useAnimatedStyle(() => {
    const timeOpen = Math.max(0, timeShown.get());
    const timeEnd = 2 * (ROW + ROW_GAP) + ROW + TIME_EXTRA * timeOpen;
    const genresEnd = timeEnd + ROW_GAP + ROW + GENRE_EXTRA * Math.max(0, genresShown.get());
    // An open row that would run under the bar slides the whole list up.
    return { transform: [{ translateY: room > 0 ? -Math.max(0, timeEnd - limit, genresEnd - limit) : 0 }] };
  });

  // A range of no length reads as one time.
  const timeValue = ranged && endTime !== startTime ? `${timeLabel(startTime)} – ${timeLabel(endTime)}` : timeLabel(startTime);
  const genresValue = chosen.length === 0 ? allGenresLabel : chosen.slice(0, 2).join(', ');
  const genresTail = chosen.length > 2 ? ` +${chosen.length - 2}` : undefined;

  return (
    <View style={[styles.screen, { paddingTop: insets.top + 8 }]} onTouchStart={touched}>
      <StatusBar style="light" />
      <View style={styles.sheet}>
        <View style={styles.grabber} />
        <View style={styles.header}>
          <Text style={styles.title} accessibilityRole="header" numberOfLines={1}>
            {title}
          </Text>
          <Pressable
            style={styles.close}
            hitSlop={8}
            accessibilityRole="button"
            accessibilityLabel="Close"
            onPress={() => {
              touched();
              onClose?.();
            }}
          >
            <Feather name="x" size={15} color="#b8b8be" />
          </Pressable>
        </View>

        <View style={styles.list} onLayout={(event) => setRoom(event.nativeEvent.layout.height)}>
          <Animated.View style={[styles.rows, listStyle]}>
            {/* Values fade when they change, not when the sheet first shows. */}
            <LayoutAnimationConfig skipEntering>
              <Row
                icon="calendar"
                label={datesLabel}
                hint="Shows the next date"
                onPress={() => {
                  touched();
                  tap();
                  setDateIndex((index) => (index + 1) % dates.length);
                }}
                value={<Value text={dates[dateIndex]} />}
              />

              <Row
                icon="users"
                label={friendsLabel}
                hint="Turns the filter on or off"
                onPress={() => {
                  touched();
                  tap();
                  setWithFriends((on) => !on);
                }}
                value={
                  withFriends ? (
                    <Animated.View key="friends" style={styles.friends} entering={FadeIn.duration(220)} exiting={FadeOut.duration(140)}>
                      <View style={styles.avatars}>
                        {friends.map((friend, index) => (
                          <Avatar key={index} friend={friend} index={index} count={friends.length} turn={turn} />
                        ))}
                      </View>
                      <Text style={styles.value}>{friendsCount}</Text>
                    </Animated.View>
                  ) : (
                    <Value text={anyFriendsLabel} dim />
                  )
                }
              />

              <Row
                icon="clock"
                label={timeRowLabel}
                pressed={pressed === 'time'}
                extra={TIME_EXTRA}
                shown={timeShown}
                expanded={expanded === 'time'}
                onPress={() => {
                  touched();
                  tap();
                  toggle('time');
                }}
                value={<Value text={timeValue} />}
              >
                <TimeDial
                  start={start}
                  end={end}
                  range={range}
                  ranged={ranged}
                  singleLabel={singleTimeLabel}
                  rangeLabel={timeRangeLabel}
                  onMode={setMode}
                  onStart={onStart}
                  onEnd={onEnd}
                  onTouch={touched}
                  shown={timeShown}
                />
              </Row>

              <Row
                icon="music"
                label={genresLabel}
                pressed={pressed === 'genres'}
                extra={GENRE_EXTRA}
                shown={genresShown}
                expanded={expanded === 'genres'}
                onPress={() => {
                  touched();
                  tap();
                  toggle('genres');
                }}
                value={<Value text={genresValue} tail={genresTail} dim={chosen.length === 0} />}
              >
                <GenreDrum
                  chosen={chosen}
                  others={others}
                  leftPosition={leftPosition}
                  rightPosition={rightPosition}
                  shown={genresShown}
                  moving={moving}
                  fresh={fresh}
                  emptyLabel={noGenresLabel}
                  onPick={(name) => {
                    touched();
                    select();
                    move(name, true);
                  }}
                  onDrop={(name) => {
                    touched();
                    select();
                    move(name, false);
                  }}
                  onTouch={touched}
                />
              </Row>

              <Row
                icon="dollar-sign"
                label={priceLabel}
                hint="Shows the next price"
                onPress={() => {
                  touched();
                  tap();
                  setPriceIndex((index) => (index + 1) % prices.length);
                }}
                value={<Value text={prices[priceIndex]} dim={priceIndex === 0} />}
              />
            </LayoutAnimationConfig>
          </Animated.View>
        </View>

        <View style={styles.bar} pointerEvents="box-none">
          <LinearGradient colors={['rgba(18,18,20,0)', SHEET]} style={styles.fade} pointerEvents="none" />
          <View style={[styles.barRow, { paddingBottom: bottom }]}>
            <Pressable
              hitSlop={12}
              accessibilityRole="button"
              onPress={() => {
                touched();
                tap();
                reset();
              }}
            >
              {({ pressed: held }) => (
                <Text style={[styles.reset, (held || pressed === 'reset') && styles.resetPressed]}>{resetLabel}</Text>
              )}
            </Pressable>
            <Pressable
              accessibilityRole="button"
              accessibilityLabel={`${continueLabel} ${withLabel} ${count}`}
              onPress={() => {
                touched();
                tap();
                onContinue?.(filters);
              }}
            >
              {({ pressed: held }) => (
                <Animated.View
                  style={[styles.button, { transform: [{ scale: held ? 0.97 : 1 }], transitionProperty: 'transform', transitionDuration: 120 }]}
                >
                  <Text style={styles.buttonLabel}>{continueLabel}</Text>
                  <Text style={styles.buttonWith}> {withLabel} </Text>
                  {/* A new count rolls in from below as the old one leaves upward. */}
                  <View style={styles.buttonCount}>
                    <Animated.Text
                      key={count}
                      style={[styles.buttonWith, styles.buttonDigit]}
                      entering={FadeInDown.duration(220)}
                      exiting={FadeOutUp.duration(160)}
                    >
                      {count}
                    </Animated.Text>
                  </View>
                </Animated.View>
              )}
            </Pressable>
          </View>
        </View>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  screen: {
    flex: 1,
    backgroundColor: BACKDROP,
  },
  sheet: {
    flex: 1,
    backgroundColor: SHEET,
    borderTopLeftRadius: 30,
    borderTopRightRadius: 30,
    borderCurve: 'continuous',
    overflow: 'hidden',
  },
  grabber: {
    alignSelf: 'center',
    marginTop: 8,
    width: 36,
    height: 5,
    borderRadius: 2.5,
    backgroundColor: '#3a3a3e',
  },
  header: {
    marginTop: 9,
    height: 44,
    alignItems: 'center',
    justifyContent: 'center',
  },
  title: {
    color: INK,
    fontSize: 17,
    lineHeight: 22,
    fontWeight: '600',
  },
  close: {
    position: 'absolute',
    right: SIDE,
    top: 7,
    width: 30,
    height: 30,
    borderRadius: 15,
    backgroundColor: '#2a2a2d',
    alignItems: 'center',
    justifyContent: 'center',
  },
  list: {
    flex: 1,
    marginTop: 30,
    overflow: 'hidden',
  },
  rows: {
    paddingHorizontal: SIDE,
    gap: ROW_GAP,
  },
  row: {
    borderRadius: ROW_RADIUS,
    borderCurve: 'continuous',
    backgroundColor: ROW_FILL,
    overflow: 'hidden',
  },
  rowHead: {
    height: ROW,
    paddingHorizontal: 18,
    flexDirection: 'row',
    alignItems: 'center',
  },
  rowTint: {
    position: 'absolute',
    left: 0,
    right: 0,
    top: 0,
    bottom: 0,
    backgroundColor: ROW_PRESSED,
  },
  rowLabel: {
    marginLeft: 10,
    color: MUTE,
    fontSize: 16,
    lineHeight: 21,
  },
  // A row's values lie on top of each other at its right end while one fades into the next.
  rowValue: {
    position: 'absolute',
    right: 18,
    top: 0,
    bottom: 0,
    left: '40%',
    justifyContent: 'center',
  },
  value: {
    position: 'absolute',
    right: 0,
    color: INK,
    fontSize: 16,
    lineHeight: 21,
    fontWeight: '600',
    fontVariant: ['tabular-nums'],
    textAlign: 'right',
  },
  valueDim: {
    color: MUTE,
    fontWeight: '400',
  },
  valueTail: {
    color: MUTE,
    fontWeight: '500',
  },
  friends: {
    position: 'absolute',
    right: 0,
    top: 0,
    bottom: 0,
    width: 96,
    justifyContent: 'center',
  },
  avatars: {
    position: 'absolute',
    left: 14,
    top: 0,
    bottom: 0,
    width: AVATAR,
    justifyContent: 'center',
  },
  avatar: {
    position: 'absolute',
    width: AVATAR,
    height: AVATAR,
    borderRadius: AVATAR / 2,
    alignItems: 'center',
    justifyContent: 'center',
    overflow: 'hidden',
  },
  initials: {
    color: '#ffffff',
    fontSize: 10,
    lineHeight: 13,
    fontWeight: '700',
  },
  head: {
    position: 'absolute',
    top: 6,
    width: 9,
    height: 9,
    borderRadius: 4.5,
    backgroundColor: 'rgba(255,255,255,0.9)',
  },
  shoulders: {
    position: 'absolute',
    top: 17,
    width: 18,
    height: 18,
    borderRadius: 9,
    backgroundColor: 'rgba(255,255,255,0.9)',
  },
  well: {
    position: 'absolute',
    left: WELL_INSET,
    right: WELL_INSET,
    top: ROW,
    borderRadius: ROW_RADIUS - 4,
    borderCurve: 'continuous',
    backgroundColor: WELL,
    overflow: 'hidden',
  },
  bar: {
    position: 'absolute',
    left: 0,
    right: 0,
    bottom: 0,
  },
  fade: {
    height: FADE,
  },
  barRow: {
    paddingHorizontal: SIDE,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    backgroundColor: SHEET,
  },
  reset: {
    color: MUTE,
    fontSize: 16,
    lineHeight: 21,
  },
  resetPressed: {
    color: INK,
  },
  button: {
    width: BUTTON_WIDTH,
    height: BUTTON,
    borderRadius: BUTTON / 2,
    backgroundColor: BLUE,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
  },
  buttonLabel: {
    color: '#ffffff',
    fontSize: 16,
    lineHeight: 21,
    fontWeight: '600',
  },
  buttonWith: {
    color: 'rgba(255,255,255,0.62)',
    fontSize: 16,
    lineHeight: 21,
    fontWeight: '500',
  },
  buttonCount: {
    width: 11,
    height: 21,
  },
  buttonDigit: {
    position: 'absolute',
    left: 0,
    right: 0,
    fontVariant: ['tabular-nums'],
    textAlign: 'center',
  },
});
