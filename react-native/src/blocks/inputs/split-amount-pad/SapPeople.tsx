// Split Amount Pad — the people at the table and their rolling shares.
//
// A share is six digit columns ("$0,000.00" at most), each a strip of 0…9 sprung to its digit, the
// cents first and each friend a little after the one before. Leading columns fade out and the group
// re-centres, all from the one shared array of shares in cents: no share is ever React state.

import { StyleSheet, Text, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, { type SharedValue, useAnimatedReaction, useAnimatedStyle, useSharedValue, withDelay, withSpring, withTiming } from 'react-native-reanimated';
import Svg, { Path } from 'react-native-svg';

import { SPRING, TIME } from './sap-motion';
import { C, COPY, PEOPLE, SHARE } from './sap-theme';

// one row per digit, each its own view of exactly one line's height (see SapAmount.tsx for why)
const DIGITS = ['0', '1', '2', '3', '4', '5', '6', '7', '8', '9'] as const;
const POWERS = [1, 10, 100, 1000, 10000, 100000] as const;

type DigitProps = {
  shares: SharedValue<number[]>;
  friend: number;
  /** 0 the cents' ones … 5 the thousands */
  place: number;
  instant: boolean;
};

function RollDigit({ shares, friend, place, instant }: DigitProps) {
  const digit = useSharedValue(0);
  const shown = useSharedValue(place <= 2 ? 1 : 0);
  useAnimatedReaction(
    () => shares.get()[friend],
    (cents, before) => {
      // someone who sits out keeps the digits they had
      if (cents === before || cents < 0) return;
      const d = Math.floor(cents / POWERS[place]) % 10;
      const on = place <= 2 || cents >= POWERS[place] ? 1 : 0;
      if (instant) {
        digit.set(d);
        shown.set(on);
        return;
      }
      digit.set(withDelay(friend * TIME.rollFriend + place * TIME.rollPlace, withSpring(d, SPRING.roll)));
      shown.set(withTiming(on, { duration: 140 }));
    },
  );
  const strip = useAnimatedStyle(() => ({ opacity: shown.get(), transform: [{ translateY: -digit.get() * SHARE.line }] }));
  return (
    <View style={styles.cell}>
      <Animated.View style={[styles.strip, strip]}>
        {DIGITS.map((g) => (
          <View key={g} style={styles.row}>
            <Text allowFontScaling={false} numberOfLines={1} style={styles.shareDigit}>
              {g}
            </Text>
          </View>
        ))}
      </Animated.View>
    </View>
  );
}

function ShareRoll({ shares, friend, instant }: { shares: SharedValue<number[]>; friend: number; instant: boolean }) {
  /** the width of the leading columns that are hidden */
  const hidden = useSharedValue(SHARE.digit * 3 + SHARE.mark);
  const thousands = useSharedValue(0);
  useAnimatedReaction(
    () => shares.get()[friend],
    (cents, before) => {
      if (cents === before || cents < 0) return;
      const gone = (cents < 1000 ? 1 : 0) + (cents < 10000 ? 1 : 0) + (cents < 100000 ? 1 : 0);
      const w = gone * SHARE.digit + (cents < 100000 ? SHARE.mark : 0);
      hidden.set(instant ? w : withSpring(w, SPRING.slide));
      thousands.set(instant ? (cents >= 100000 ? 1 : 0) : withTiming(cents >= 100000 ? 1 : 0, { duration: 140 }));
    },
  );
  const group = useAnimatedStyle(() => ({ transform: [{ translateX: -hidden.get() / 2 }] }));
  const dollar = useAnimatedStyle(() => ({ transform: [{ translateX: hidden.get() }] }));
  const comma = useAnimatedStyle(() => ({ opacity: thousands.get() }));
  return (
    <Animated.View style={[styles.share, group]}>
      <Animated.Text allowFontScaling={false} style={[styles.shareMark, { width: SHARE.digit }, dollar]}>
        $
      </Animated.Text>
      <RollDigit shares={shares} friend={friend} place={5} instant={instant} />
      <Animated.Text allowFontScaling={false} style={[styles.shareMark, { width: SHARE.mark }, comma]}>
        ,
      </Animated.Text>
      <RollDigit shares={shares} friend={friend} place={4} instant={instant} />
      <RollDigit shares={shares} friend={friend} place={3} instant={instant} />
      <RollDigit shares={shares} friend={friend} place={2} instant={instant} />
      <Text allowFontScaling={false} style={[styles.shareMark, { width: SHARE.mark }]}>
        .
      </Text>
      <RollDigit shares={shares} friend={friend} place={1} instant={instant} />
      <RollDigit shares={shares} friend={friend} place={0} instant={instant} />
    </Animated.View>
  );
}

type FriendProps = {
  index: number;
  width: number;
  disc: number;
  /** 1 in, 0 sitting out, per friend */
  active: SharedValue<number[]>;
  shares: SharedValue<number[]>;
  /** a worklet: the finger came down on this friend */
  onToggle: (index: number) => void;
  onRelease: () => void;
  /** the ghost finger's friend (or -1) and how hard it presses */
  autoFriend: SharedValue<number>;
  autoPress: SharedValue<number>;
  instant: boolean;
};

export function SapFriend({ index, width, disc, active, shares, onToggle, onRelease, autoFriend, autoPress, instant }: FriendProps) {
  const person = PEOPLE[index];
  const on = useSharedValue(1);
  const press = useSharedValue(0);
  useAnimatedReaction(
    () => active.get()[index],
    (now, before) => {
      if (now === before) return;
      on.set(instant ? now : withSpring(now, SPRING.toggle));
    },
  );
  const tap = Gesture.Tap()
    .maxDuration(100000)
    .onBegin(() => {
      press.set(withTiming(1, { duration: TIME.keyDown }));
      onToggle(index);
    })
    .onFinalize(() => {
      press.set(withTiming(0, { duration: TIME.keyUp }));
      onRelease();
    });
  const avatar = useAnimatedStyle(() => {
    const p = Math.max(press.get(), autoFriend.get() === index ? autoPress.get() : 0);
    const v = on.get();
    return { opacity: 0.4 + 0.6 * Math.min(1, Math.max(0, v)), transform: [{ scale: (0.9 + 0.1 * v) * (1 - 0.06 * p) }] };
  });
  const badge = useAnimatedStyle(() => ({ opacity: Math.min(1, Math.max(0, on.get() * 2)), transform: [{ scale: Math.max(0, on.get()) }] }));
  const share = useAnimatedStyle(() => ({ opacity: Math.min(1, Math.max(0, on.get())) }));
  const out = useAnimatedStyle(() => ({ opacity: 1 - Math.min(1, Math.max(0, on.get())) }));

  return (
    <GestureDetector gesture={tap}>
      <View style={[styles.friend, { width }]}>
        <Animated.View style={[{ width: disc, height: disc }, avatar]}>
          <View style={[styles.disc, { borderRadius: disc / 2, backgroundColor: person.tint }]}>
            <Text allowFontScaling={false} style={[styles.initials, { color: person.ink, fontSize: disc * 0.34 }]}>
              {person.initials}
            </Text>
          </View>
          {index > 0 ? (
            <Animated.View style={[styles.badge, badge]}>
              <Svg width={10} height={10} viewBox="0 0 10 10">
                <Path d="M1.8 5.3 4.1 7.5 8.3 2.8" stroke={C.accentInk} strokeWidth={1.7} strokeLinecap="round" strokeLinejoin="round" fill="none" />
              </Svg>
            </Animated.View>
          ) : null}
        </Animated.View>
        <Text allowFontScaling={false} style={styles.name} numberOfLines={1}>
          {person.name}
        </Text>
        <View style={styles.shareBox}>
          <Animated.View style={share}>
            <ShareRoll shares={shares} friend={index} instant={instant} />
          </Animated.View>
          <Animated.Text allowFontScaling={false} style={[styles.out, out]}>
            {COPY.out}
          </Animated.Text>
        </View>
      </View>
    </GestureDetector>
  );
}

const styles = StyleSheet.create({
  friend: { alignItems: 'center' },
  disc: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    boxShadow: '0 1px 2px rgba(40,32,20,0.14), 0 6px 14px rgba(40,32,20,0.08), inset 0 1px 0 rgba(255,255,255,0.55)',
  },
  initials: { fontFamily: 'ui-rounded', fontWeight: '700', letterSpacing: 0.2 },
  badge: {
    position: 'absolute',
    right: -3,
    bottom: -3,
    width: 20,
    height: 20,
    borderRadius: 10,
    backgroundColor: C.accent,
    borderWidth: 2,
    borderColor: C.paper[1],
    alignItems: 'center',
    justifyContent: 'center',
  },
  name: { marginTop: 8, color: C.ink, fontSize: 13, fontWeight: '500' },
  shareBox: { marginTop: 2, height: SHARE.line, alignItems: 'center', justifyContent: 'center' },
  share: { flexDirection: 'row', height: SHARE.line, overflow: 'hidden' },
  cell: { width: SHARE.digit, height: SHARE.line, overflow: 'hidden' },
  strip: { position: 'absolute', left: 0, top: 0, width: SHARE.digit, height: SHARE.line * 10 },
  row: { width: SHARE.digit, height: SHARE.line },
  shareDigit: { width: SHARE.digit, height: SHARE.line, color: C.ink, fontSize: SHARE.size, lineHeight: SHARE.line, fontWeight: '600', fontVariant: ['tabular-nums'], textAlign: 'center' },
  shareMark: { height: SHARE.line, color: C.ink, fontSize: SHARE.size, lineHeight: SHARE.line, fontWeight: '600', textAlign: 'center' },
  out: { position: 'absolute', color: C.faint, fontSize: 13, fontWeight: '500' },
});
