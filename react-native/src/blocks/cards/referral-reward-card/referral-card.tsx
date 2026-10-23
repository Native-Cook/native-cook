import { useEffect, useEffectEvent, useMemo, useRef, useState } from "react";
import { AccessibilityInfo, type LayoutRectangle, StyleSheet, Text, View, useWindowDimensions } from "react-native";
import { interpolateColors, useImage, type SkImage } from "@shopify/react-native-skia";
import Animated, {
  cancelAnimation,
  Easing,
  useAnimatedReaction,
  useAnimatedStyle,
  useDerivedValue,
  useFrameCallback,
  useSharedValue,
  withDelay,
  withSequence,
  withSpring,
  withTiming,
} from "react-native-reanimated";
import { scheduleOnRN, scheduleOnUI } from "react-native-worklets";
import * as Haptics from "expo-haptics";
import { usePreferences } from "./i18n";
import { TRAIL_END } from "./referral-comet";
import { FRIENDS } from "./referral-friends";
import {
  JOINED_AT_START,
  POINTS_PER_FRIEND,
  TIERS,
  arcOf,
  easeOut,
  referralMotion as M,
  referralSize as S,
  referralTone,
  tierOf,
  wrapIndex,
} from "./referral-palette";
import { type GlassPill, ReferralGlass } from "./referral-glass";
import { ReferralFlight } from "./referral-flight";
import { capsuleShift, movePeek, PEEK } from "./referral-capsule";
import { buildReferralPath, type ReferralPath } from "./referral-path";
import { ReferralPoints } from "./referral-points";
import { ReferralRail, type RailFx } from "./referral-rail";
import { ReferralTiers, TIERS_WIDTH, tierCentre } from "./referral-tiers";
import { ActionButton, BUTTON_MOTION as B, EASE_IN_OUT, EASE_OUT, buttonBody, gentle, systemFace, useMotionPrefs } from "./ui";

// Referral Reward Card — "Refer friends": a row of friends under a lens, the
// points they have earned you above it, and one button. Pressing it stands
// in for a friend accepting the invite: the next friend who has not joined
// comes under the lens, the button becomes a comet carrying 25 points into the
// counter and the arc around the lens closes towards the next reward tier.
// Drag the row, or tap an avatar, to look at someone else. Tap the friend
// under the lens to open their capsule and invite them from its action.

const N = FRIENDS.length;
const START = FRIENDS.map((_, i) => i < JOINED_AT_START);

export function ReferralCard({
  width,
  autoPlay = false,
}: {
  width: number;
  // Presses the button by itself on a fixed beat and starts over at the end,
  // so the card shows what it does (and a recording of it loops). It stops
  // for good at the first touch.
  autoPlay?: boolean;
}) {
  const { reduced, haptics } = useMotionPrefs();
  const { t } = usePreferences();
  const { fontScale } = useWindowDimensions();
  // Keep room for the 149 pt title before moving the rewards below the copy.
  const stacked = width - S.cardPad * 2 < 152 * fontScale + 12 + TIERS_WIDTH;
  const [tiersLayout, setTiersLayout] = useState<LayoutRectangle | null>(null);
  const [marksLayout, setMarksLayout] = useState<LayoutRectangle | null>(null);

  const mika = useImage(FRIENDS[0].image);
  const juno = useImage(FRIENDS[1].image);
  const otto = useImage(FRIENDS[2].image);
  const nori = useImage(FRIENDS[3].image);
  const pim = useImage(FRIENDS[4].image);
  const zuzu = useImage(FRIENDS[5].image);
  const rafa = useImage(FRIENDS[6].image);
  const images = useMemo(() => {
    const all = [mika, juno, otto, nori, pim, zuzu, rafa];
    return all.every(Boolean) ? (all as SkImage[]) : null;
  }, [mika, juno, otto, nori, pim, zuzu, rafa]);

  const [joined, setJoined] = useState(START);
  const committed = useRef(START);
  const [focus, setFocus] = useState(JOINED_AT_START);
  // How long the counter waits before it rolls, set with each change.
  const [countDelay, setCountDelay] = useState(0);
  // The new friend's check follows the catch at the ring.
  const [markDelay, setMarkDelay] = useState(0);
  const count = joined.filter(Boolean).length;
  const done = count === N;
  const points = count * POINTS_PER_FRIEND;

  const scroll = useSharedValue(JOINED_AT_START);
  const peek = useSharedValue(0);
  const peekOpen = useSharedValue(false);
  const capsuleShape = useSharedValue(0);
  const capsuleLabel = useSharedValue(0);
  const capsuleLaunching = useSharedValue(-1);
  const peekFx = useMemo(() => ({ peek, open: peekOpen, shape: capsuleShape, label: capsuleLabel, launching: capsuleLaunching }),
    [peek, peekOpen, capsuleShape, capsuleLabel, capsuleLaunching]);
  const activePath = useSharedValue<ReferralPath | null>(null);
  const fromCapsule = useSharedValue(false);
  const enter = useSharedValue(0);
  const pop = useSharedValue(0);
  const kick = useSharedValue(0);
  const wave = useSharedValue(1);
  const crest = useSharedValue(1);
  const comet = useSharedValue(TRAIL_END);
  const buttonShape = useSharedValue(0);
  const buttonLabel = useSharedValue(0);
  const buttonClock = useDerivedValue(() => fromCapsule.get() ? TRAIL_END : comet.get());
  const buttonPress = useSharedValue(0);
  const buttonFx = useMemo(() => ({ shape: buttonShape, label: buttonLabel, comet: buttonClock, press: buttonPress }), [buttonShape, buttonLabel, buttonClock, buttonPress]);
  const ripple = useSharedValue(1);
  // The comet that carries a filled tier from the points to its mark.
  const flight = useSharedValue(TRAIL_END);
  const [flightTo, setFlightTo] = useState(0);
  const glow = useSharedValue(0);
  const flash = useSharedValue(0);
  const arcStart = useSharedValue(0);
  const arcEnd = useSharedValue(arcOf(JOINED_AT_START));
  const catchArc = useSharedValue(arcOf(JOINED_AT_START));
  const level = useSharedValue(tierOf(JOINED_AT_START));
  const bump = useSharedValue(0);
  // The color of the friend under the lens, blended while the row is between
  // two of them. It lights the lens and tints the sparks.
  const tints = useMemo(() => [...FRIENDS.map((f) => f.color), FRIENDS[0].color], []);
  const stops = useMemo(() => tints.map((_, i) => i), [tints]);
  const tint = useDerivedValue(() => interpolateColors(wrapIndex(scroll.get(), N), stops, tints));
  const fx = useMemo(
    () => ({ pop, kick, wave, crest, ripple, glow, flash, arcStart, arcEnd, tint }),
    [pop, kick, wave, crest, ripple, glow, flash, arcStart, arcEnd, tint],
  );
  // Where the card's surface is drawn from: its own size, and the lens in it.
  const [size, setSize] = useState<{ height: number; railTop: number } | null>(null);

  const [footerTop, setFooterTop] = useState(0);
  const [button, setButton] = useState<LayoutRectangle | null>(null);
  const railTop = size?.railTop ?? 0;
  const path = useMemo(() => button && railTop > 0 && footerTop > 0 ? buildReferralPath(
    { x: button.x + button.width / 2, y: footerTop + button.y + button.height / 2 },
    { x: width / 2, y: railTop + S.cy },
  ) : null, [button, footerTop, railTop, width]);

  const [capsuleAction, setCapsuleAction] = useState<LayoutRectangle | null>(null);
  const capsulePath = useMemo(() => capsuleAction && railTop > 0 ? buildReferralPath(
    { x: capsuleAction.x + capsuleAction.width / 2, y: railTop + capsuleAction.y + capsuleAction.height / 2 },
    { x: width / 2, y: railTop + S.cy },
  ) : null, [capsuleAction, railTop, width]);

  // The canvas's first frames, with seven images to upload and the blurs'
  // shaders to compile, can hold the UI thread; an arrival started on mount
  // would play unseen. So it waits for the rail to say it has drawn its blurs,
  // and then until the UI thread has run a few smooth frames in a row. A
  // device too busy to manage that is not kept waiting: after 1.2 s the card
  // arrives anyway.
  const [ready, setReady] = useState(false);
  const [live, setLive] = useState(false);
  const smooth = useSharedValue(0);
  const waited = useSharedValue(0);
  const watch = useFrameCallback((f) => {
    const dt = f.timeSincePreviousFrame ?? 100;
    smooth.set(dt < 34 ? smooth.get() + 1 : 0);
    waited.set(waited.get() + dt);
    if (smooth.get() === 6 || (smooth.get() < 6 && waited.get() > 1200)) {
      smooth.set(7);
      scheduleOnRN(setLive, true);
    }
  }, false);
  useEffect(() => {
    smooth.set(0);
    waited.set(0);
    watch.setActive(ready && !live);
  }, [ready, live, smooth, waited, watch]);
  useEffect(() => {
    if (!live) return;
    // One clock for the whole arrival; each part eases its own stretch of it.
    const total = M.enter + M.stagger * 2;
    enter.set(reduced ? gentle(total) : withTiming(total, { duration: total, easing: Easing.linear }));
  }, [live, reduced, enter]);

  // Haptics land on the frame the catch (or the tier) starts drawing.
  const hit = () => {
    if (haptics) void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
  };
  const reward = () => {
    if (haptics) void Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success).catch(() => {});
  };
  useAnimatedReaction(
    () => wave.get() < 0.5,
    (now, before) => {
      if (now && before === false) scheduleOnRN(hit);
    },
  );
  useAnimatedReaction(
    () => flash.get() > 0.05,
    (now, before) => {
      if (now && before === false) scheduleOnRN(reward);
    },
  );

  // The catch follows the head's clock, not an independent delay: its first
  // frame past the join is also the frame the lens and ring take the hit.
  useAnimatedReaction(
    () => {
      const path = activePath.get();
      return path !== null && comet.get() >= path.launch / path.total && comet.get() < TRAIL_END;
    },
    (caught, before) => {
      const path = activePath.get();
      if (!caught || before !== false || !path) return;
      pop.set(1);
      pop.set(withSpring(0, { duration: 520, dampingRatio: 0.8 }));
      wave.set(0);
      wave.set(withTiming(1, { duration: M.wave, easing: EASE_OUT }));
      kick.set(withSequence(
        withTiming(1, { duration: 120, easing: EASE_OUT }),
        withSpring(0, { duration: 560, dampingRatio: 0.5 }),
      ));
      glow.set(withSequence(
        withTiming(1, { duration: 120, easing: EASE_OUT }),
        withTiming(0, { duration: 720, easing: EASE_OUT }),
      ));
      arcEnd.set(withTiming(catchArc.get(), { duration: path.orbit, easing: EASE_OUT }));
    },
  );

  // One friend joins: the friend under the lens, or the next one to the
  // right who has not joined. A second press commits at once, even before
  // React has rendered the first one.
  const invite = (selected?: number) => {
    const joined = committed.current;
    const capsulePress = selected !== undefined;
    // An accessibility activation or two quick touches cannot invite another friend.
    if (capsulePress && joined[selected]) return;
    // A tap can arrive before opening settles. Freeze the action's actual
    // centre for its collapse and build that origin into this flight.
    const capsuleAt = capsuleLaunching.get();
    const route = capsulePress && capsulePath ? buildReferralPath(
      { x: capsulePath.samples[0].x - capsuleShift(width, capsuleAt), y: capsulePath.samples[0].y },
      { x: width / 2, y: railTop + S.cy },
    ) : path;
    if (!reduced && (!route || (capsulePress && !capsulePath))) return;
    const count = joined.filter(Boolean).length;
    const at = Math.round(scroll.get());
    let steps = 0;
    if (selected !== undefined) steps = wrapIndex(selected - at + Math.floor(N / 2), N) - Math.floor(N / 2);
    else while (joined[wrapIndex(at + steps, N)]) steps += 1;
    const who = wrapIndex(at + steps, N);
    const next = joined.map((j, i) => j || i === who);
    const now = count + 1;
    const filled = tierOf(now) > tierOf(count);
    const last = now === N;

    committed.current = next;
    setJoined(next);
    AccessibilityInfo.announceForAccessibility(
      t(
        `${FRIENDS[who].name} joined. ${now * POINTS_PER_FRIEND} points.`,
        `${FRIENDS[who].name} đã tham gia. ${now * POINTS_PER_FRIEND} điểm.`,
      ),
    );

    if (reduced) {
      // No travel to wait for and no burst: the state changes in place.
      movePeek(peekFx, false, reduced);
      scroll.set(gentle(at + steps));
      setCountDelay(0);
      setMarkDelay(0);
      arcStart.set(0);
      arcEnd.set(gentle(filled && !last ? 0 : arcOf(now), 240));
      level.set(gentle(tierOf(now), 240));
      if (filled) reward();
      else hit();
      return;
    }

    if (!route) return;
    const travel = steps === 0 ? 0 : M.travel + (steps - 1) * 60;
    // Even the friend already under the lens waits for the body to become a dot.
    // A closing capsule must be circular before the head is caught. Duration
    // springs settle over 1.5 times their configured duration.
    const closing = peek.get() > 0 ? PEEK.close * 1.5 : 0;
    const launch = Math.max(travel, B.collapse, closing - route.launch);
    const caught = launch + route.launch;
    const landed = caught + route.orbit + route.sling;
    const rewardLaunch = landed + M.tierAfter;
    const arrive = rewardLaunch + M.flight;
    setCountDelay(landed - M.countLead);
    setMarkDelay(caught + 40);
    if (filled) setFlightTo(tierOf(now) - 1);

    // Reset and schedule together on UI. No old delay can wake up in the new
    // flight, and the button and the head use the same clock at the hand-off.
    scheduleOnUI(() => {
      "worklet";
      for (const value of [pop, kick, glow, flash, bump]) value.set(0);
      for (const value of [wave, crest, ripple]) value.set(1);
      flight.set(TRAIL_END);
      level.set(tierOf(count));
      arcStart.set(0);
      cancelAnimation(arcEnd);
      cancelAnimation(buttonShape);
      cancelAnimation(buttonLabel);
      cancelAnimation(capsuleShape);
      cancelAnimation(capsuleLabel);
      fromCapsule.set(capsulePress);
      activePath.set(route);
      const shape = capsulePress ? capsuleShape : buttonShape;
      const label = capsulePress ? capsuleLabel : buttonLabel;
      if (capsulePress) {
        capsuleShape.set(0);
        capsuleLabel.set(0);
        buttonShape.set(0);
        buttonLabel.set(0);
      } else capsuleLaunching.set(-1);
      movePeek(peekFx, false, reduced);
      scroll.set(withSpring(at + steps, { duration: 440, dampingRatio: 1 }));
      comet.set(-launch / route.total);
      comet.set(withTiming(TRAIL_END, { duration: launch + route.total * TRAIL_END, easing: Easing.linear }));
      shape.set(capsulePress ? withTiming(1, { duration: B.collapse, easing: EASE_OUT }) : withSequence(
        withTiming(1, { duration: B.collapse, easing: EASE_OUT }),
        withDelay(launch + B.reformAfter - B.collapse, withSpring(0, { duration: B.reform, dampingRatio: 0.8 })),
      ));
      label.set(capsulePress ? withTiming(1, { duration: B.fade, easing: EASE_OUT }) : withSequence(
        withTiming(1, { duration: B.fade, easing: EASE_OUT }),
        withDelay(launch + B.reformAfter - B.fade, withTiming(0, { duration: B.focus, easing: EASE_OUT })),
      ));
      ripple.set(withDelay(landed - 30, withSequence(withTiming(0, { duration: 0 }), withTiming(1, { duration: 420, easing: Easing.linear }))));
      bump.set(withDelay(landed, withSequence(
        withTiming(1, { duration: 110, easing: EASE_OUT }),
        withSpring(0, { duration: 520, dampingRatio: 0.55 }),
      )));
      // The arc closes during the orbit, before the head leaves for the points.
      if (arcOf(count) === 0) arcEnd.set(0);
      catchArc.set(filled ? 1 : arcOf(now));
      if (!filled) return;
      flight.set(withDelay(rewardLaunch, withSequence(
        withTiming(0, { duration: 0 }),
        withTiming(TRAIL_END, { duration: M.flight * TRAIL_END, easing: Easing.linear }),
      )));
      flash.set(withDelay(arrive, withSequence(
        withTiming(1, { duration: 100, easing: EASE_OUT }),
        withTiming(0, { duration: 640, easing: EASE_OUT }),
      )));
      crest.set(withDelay(arrive, withSequence(withTiming(0, { duration: 0 }), withTiming(1, { duration: 620, easing: EASE_OUT }))));
      level.set(withDelay(arrive, withSpring(tierOf(now), { duration: 560, dampingRatio: 0.58 })));
      if (!last) arcStart.set(withDelay(arrive + M.drainAfter, withTiming(1, { duration: M.drain, easing: EASE_IN_OUT })));
    });
  };

  // Back to the two friends the card opens with.
  const startOver = () => {
    const at = Math.round(scroll.get());
    // The short way round to the friend it opened on.
    const back = wrapIndex(JOINED_AT_START - at + Math.floor(N / 2), N) - Math.floor(N / 2);
    committed.current = START;
    setJoined(START);
    // Reset also interrupts a last join that has not landed yet.
    scheduleOnUI(() => {
      "worklet";
      movePeek(peekFx, false, reduced);
      capsuleLaunching.set(-1);
      capsuleShape.set(0);
      capsuleLabel.set(0);
      comet.set(TRAIL_END);
      flight.set(TRAIL_END);
      buttonShape.set(0);
      buttonLabel.set(0);
      for (const value of [pop, kick, glow, flash, bump]) value.set(0);
      for (const value of [wave, crest, ripple]) value.set(1);
    });
    setCountDelay(reduced ? 0 : 120);
    if (haptics) void Haptics.selectionAsync().catch(() => {});
    arcStart.set(0);
    if (reduced) {
      scroll.set(gentle(at + back));
      arcEnd.set(gentle(arcOf(JOINED_AT_START), 240));
      level.set(gentle(tierOf(JOINED_AT_START), 240));
      return;
    }
    scroll.set(withSpring(at + back, { duration: 560, dampingRatio: 0.9 }));
    const open = withTiming(arcOf(JOINED_AT_START), { duration: 520, easing: EASE_IN_OUT });
    arcEnd.set(arcOf(count) === 0 ? withSequence(withTiming(0, { duration: 0 }), open) : open);
    level.set(withTiming(tierOf(JOINED_AT_START), { duration: 420, easing: EASE_OUT }));
  };

  const press = () => (committed.current.every(Boolean) ? startOver() : invite());

  // autoPlay: one press per beat, a longer rest after a tier and at the end.
  const [touched, setTouched] = useState(false);
  const beat = useEffectEvent(() => {
    const filled = !done && tierOf(count + 1) > tierOf(count);
    press();
    return done ? M.beat : count + 1 === N ? M.restBeat : filled ? M.tierBeat : M.beat;
  });
  useEffect(() => {
    if (!autoPlay || !live || touched) return;
    let timer = setTimeout(function run() {
      timer = setTimeout(run, beat());
    }, 1400);
    return () => clearTimeout(timer);
  }, [autoPlay, live, touched]);

  // The caption under the marks changes as a mark fills, not at the press.
  const [shownTier, setShownTier] = useState(tierOf(JOINED_AT_START));
  useAnimatedReaction(
    () => Math.round(level.get()),
    (now, before) => {
      if (before !== null && now !== before) scheduleOnRN(setShownTier, now);
    },
  );
  // The button's body is part of the glass, so the glass is told where it is:
  // the measured button, collapsing as the view does, arriving with the footer.
  const flightDuration = path?.total ?? 1;
  const pill = useDerivedValue<GlassPill>(() => {
    if (!button || footerTop === 0) return { x: 0, y: 0, halfWidth: 0, halfHeight: 0, radius: 0, show: 0, press: 0 };
    const b = buttonBody(button, reduced ? 0 : buttonShape.get(), buttonPress.get(), buttonClock.get(), flightDuration);
    const arrived = easeOut((enter.get() - M.stagger * 2) / M.enter);
    return {
      x: button.x + button.width / 2,
      y: footerTop + button.y + button.height / 2 + (reduced ? 0 : (1 - arrived) * 8),
      halfWidth: (b.width * b.scale) / 2,
      halfHeight: (b.height * b.scale) / 2,
      radius: b.radius * b.scale,
      show: !reduced && b.hidden ? 0 : arrived,
      press: buttonPress.get(),
    };
  });
  // Where the second comet flies: from the points to the mark of the tier.
  const mark = tierCentre(flightTo);
  const friend = FRIENDS[focus];
  const header = useAnimatedStyle(() => part(enter.get(), 0, reduced));
  const middle = useAnimatedStyle(() => part(enter.get(), 1, reduced));
  const footer = useAnimatedStyle(() => part(enter.get(), 2, reduced));
  // The rail arrives with its own gathering, on the middle part's clock.
  const railEnter = useDerivedValue(() => easeOut((enter.get() - M.stagger) / M.enter));
  const railFx: RailFx = useMemo(() => ({ ...fx, enter: railEnter }), [fx, railEnter]);

  return (
    <View
      style={[styles.card, { width }]}
      onLayout={(e) => {
        const height = e.nativeEvent.layout.height;
        setSize((was) => (was?.height === height ? was : { height, railTop: was?.railTop ?? 0 }));
      }}
    >
      {size && size.railTop > 0 && (
        <ReferralGlass
          width={width}
          height={size.height}
          lensY={size.railTop + S.cy}
          scroll={scroll}
          light={railEnter}
          glow={glow}
          flash={flash}
          pill={pill}
          drift={live && !reduced}
        />
      )}
      <Animated.View style={[styles.header, stacked && styles.headerStacked, header]}>
        <View style={[styles.copy, { maxWidth: 180 * fontScale }, stacked && styles.copyStacked]}>
          <Text style={styles.title} accessibilityRole="header">
            {t("Refer friends", "Mời bạn bè")}
          </Text>
          <Text style={styles.sub}>{t("Invite friends to earn reward points.", "Mời bạn bè để nhận điểm thưởng.")}</Text>
        </View>
        {/* The marks say what the points are for: a reward at 3, 5 and 7
            friends. The line under them says so in words. */}
        <View style={[styles.tiers, stacked && styles.tiersStacked]} onLayout={(e) => setTiersLayout(e.nativeEvent.layout)}>
          <View onLayout={(e) => setMarksLayout(e.nativeEvent.layout)}>
            <ReferralTiers
              level={level}
              bump={bump}
              label={t(
                `Rewards: ${shownTier} of ${TIERS.length} unlocked`,
                `Phần thưởng: đã mở ${shownTier} trên ${TIERS.length}`,
              )}
            />
          </View>
          <Text style={[styles.tierNote, stacked && styles.tierNoteStacked]} accessibilityElementsHidden importantForAccessibility="no">
            {t(`${shownTier} of ${TIERS.length} rewards`, `${shownTier}/${TIERS.length} phần thưởng`)}
          </Text>
        </View>
      </Animated.View>

      <Animated.View
        style={[styles.rail, middle]}
        onLayout={(e) => {
          const railTop = e.nativeEvent.layout.y;
          setSize((was) => (was?.railTop === railTop ? was : { height: was?.height ?? 0, railTop }));
        }}
      >
        {images && (
          <ReferralRail
            width={width}
            friends={FRIENDS}
            images={images}
            joined={joined}
            scroll={scroll}
            fx={railFx}
            peekFx={peekFx}
            onInvite={invite}
            onActionLayout={setCapsuleAction}
            markDelay={markDelay}
            reduced={reduced}
            haptics={haptics}
            label={t("Friends", "Bạn bè")}
            value={
              joined[focus]
                ? t(`${friend.name}, joined, ${focus + 1} of ${N}`, `${friend.name}, đã tham gia, ${focus + 1} trên ${N}`)
                : t(`${friend.name}, not invited yet, ${focus + 1} of ${N}`, `${friend.name}, chưa mời, ${focus + 1} trên ${N}`)
            }
            onFocus={setFocus}
            onReady={() => setReady(true)}
            onTouch={() => setTouched(true)}
          />
        )}
        <View pointerEvents="none" style={styles.points}>
          <ReferralPoints
            value={points}
            delay={countDelay}
            bump={bump}
            label={t(`${points} points`, `${points} điểm`)}
            reduced={reduced}
          />
        </View>
      </Animated.View>

      <Animated.View style={[styles.footer, footer]} onLayout={(e) => setFooterTop(e.nativeEvent.layout.y)}>
        <ActionButton
          label={t("Refer friends now", "Mời bạn bè ngay")}
          alt={t("Start over", "Bắt đầu lại")}
          showAlt={done}
          onPress={() => {
            setTouched(true);
            movePeek(peekFx, false, reduced);
            press();
          }}
          reduced={reduced}
          fx={buttonFx}
          flightDuration={flightDuration}
          maxWidth={width - S.cardPad * 2}
          onLayout={setButton}
        />
      </Animated.View>

      {size && path && tiersLayout && marksLayout && (
        <ReferralFlight
          sling={activePath}
          cometClock={comet}
          buttonShape={buttonShape}
          capsuleShape={capsuleShape}
          fromCapsule={fromCapsule}
          from={{ x: width / 2, y: size.railTop + S.cy - S.pointsRise }}
          to={{ x: tiersLayout.x + marksLayout.x + mark.x, y: S.cardPad + tiersLayout.y + marksLayout.y + mark.y }}
          clock={flight}
          tint={tint}
        />
      )}
    </View>
  );
}

// One of the card's three parts on the arrival clock: it fades in and rises
// 8 pt, each part 80 ms after the one above it.
function part(clock: number, index: number, reduced: boolean) {
  "worklet";
  const p = easeOut((clock - index * M.stagger) / M.enter);
  return { opacity: p, transform: [{ translateY: reduced ? 0 : (1 - p) * 8 }] };
}

const styles = StyleSheet.create({
  // No fill and no clip: the glass draws the card, and its light reaches past it.
  card: { paddingVertical: S.cardPad },
  header: {
    flexDirection: "row",
    alignItems: "flex-start",
    justifyContent: "space-between",
    paddingHorizontal: S.cardPad,
    gap: 12,
  },
  headerStacked: { flexDirection: "column" },
  copy: { flex: 1, gap: 4 },
  // This measure keeps both translations on two balanced lines without a forced break.
  copyStacked: { flex: 0, width: "100%" },
  title: {
    fontFamily: systemFace,
    fontSize: 26,
    lineHeight: 32,
    fontWeight: "700",
    letterSpacing: -0.5,
    color: referralTone.ink,
  },
  sub: { fontFamily: systemFace, fontSize: 16, lineHeight: 24, color: referralTone.sub },
  // The title's capitals centre at 16 pt within its 32 pt line.
  tiers: { alignItems: "flex-end", gap: 8, marginTop: 1 },
  tiersStacked: { flexDirection: "row", alignItems: "center", justifyContent: "space-between", alignSelf: "stretch", gap: 12, minHeight: 36, marginTop: 0 },
  tierNote: {
    fontFamily: systemFace,
    fontSize: 12,
    lineHeight: 16,
    fontVariant: ["tabular-nums"],
    color: referralTone.note,
    textAlign: "right",
  },
  tierNoteStacked: { flexShrink: 1 },
  // The visible points and ticks have equal air above and below the group.
  // The canvas still contains the full 100 pt reward ring.
  rail: { marginTop: 20, height: S.railHeight },
  points: {
    position: "absolute",
    left: 0,
    right: 0,
    top: S.cy - S.pointsRise - S.pointsLine / 2,
  },
  footer: { alignItems: "center", paddingHorizontal: S.cardPad },
});
