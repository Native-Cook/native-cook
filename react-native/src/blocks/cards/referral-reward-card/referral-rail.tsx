import { useEffect, useMemo, useState } from "react";
import { AccessibilityInfo, type LayoutRectangle, StyleSheet, View } from "react-native";
import {
  Blur,
  BlurMask,
  Canvas,
  Circle,
  ColorMatrix,
  Group,
  ImageShader,
  LinearGradient,
  Paint,
  Path,
  RadialGradient,
  Rect,
  RoundedRect,
  Skia,
  rect,
  rrect,
  vec,
  type SkImage,
} from "@shopify/react-native-skia";
import { Gesture, GestureDetector } from "react-native-gesture-handler";
import {
  Easing,
  cancelAnimation,
  type DerivedValue,
  type SharedValue,
  useAnimatedReaction,
  useDerivedValue,
  useSharedValue,
  withDelay,
  withSequence,
  withSpring,
  withTiming,
} from "react-native-reanimated";
import { scheduleOnRN } from "react-native-worklets";
import * as Haptics from "expo-haptics";
import { ReferralCapsule, capsuleShift, movePeek, peekPart, PEEK, type PeekFx } from "./referral-capsule";
import { usePreferences } from "./i18n";
import { type Friend } from "./referral-friends";
import {
  easeOut,
  offsetOf,
  ramp,
  referralSize as S,
  referralTone,
  smooth,
  wrapIndex,
} from "./referral-palette";
import { EASE_OUT, gentle } from "./ui";

// The row of friends and the lens over it. The row runs under a fixed lens:
// outside it the avatars are small and dimmed, inside it the one in the
// middle is drawn larger and in full color, so scrolling reads as a
// magnifier passing over the row. Everything is one canvas driven by shared
// values; React only hears about the friend under the lens when it changes.

// The values the card animates and the rail draws from.
export type RailFx = {
  // 0 hidden, 1 arrived.
  enter: DerivedValue<number>;
  // 1 at the catch: the lens is 5% larger, then settles.
  pop: SharedValue<number>;
  // The ticks thrown outward at the catch.
  kick: SharedValue<number>;
  // The catch's ring, 0 at the lens and 1 gone; rests at 1.
  wave: SharedValue<number>;
  // The wider ring of a filled tier.
  crest: SharedValue<number>;
  // The ring it leaves round the points as it lands, 0 to 1; rests at 1.
  ripple: SharedValue<number>;
  // The lens light, 0 at rest.
  glow: SharedValue<number>;
  // The whole ring lit as a tier fills.
  flash: SharedValue<number>;
  // The arc towards the next tier: both ends, as fractions of the ring.
  arcStart: SharedValue<number>;
  arcEnd: SharedValue<number>;
  // The color of the friend under the lens, as Skia's four floats.
  tint: DerivedValue<number[]>;
};

// Side avatars lose some chroma as well as light, as in the reference.
const DULL = (() => {
  const s = 0.62;
  const r = 0.2126 * (1 - s);
  const g = 0.7152 * (1 - s);
  const b = 0.0722 * (1 - s);
  return [r + s, g, b, 0, 0, r, g + s, b, 0, 0, r, g, b + s, 0, 0, 0, 0, 0, 1, 0];
})();

// A tap must catch a settled friend, not a flick crossing an integer.
const REST_GAP = 0.005;
// How far the follower trails the row, in ms.
const LAG = 90;
// A side avatar's opacity and blur by how many friends it is from the lens.
// Both ease between these, so neither changes pace as a friend passes one.
const DIM = [0.74, 0.74, 0.52, 0.3, 0.3];
const SOFT = [0, 0, 1.1, 3, 4.5];

// A hairline of pure white inside each portrait's edge, for a consistent rim
// whatever its background color.
const EDGE = "rgba(255,255,255,0.1)";

export function ReferralRail({
  width,
  friends,
  images,
  joined,
  scroll,
  fx,
  reduced,
  haptics,
  markDelay,
  label,
  value,
  peekFx,
  onInvite,
  onActionLayout,
  onFocus,
  onReady,
  onTouch,
}: {
  width: number;
  friends: Friend[];
  images: SkImage[];
  joined: boolean[];
  // Which friend is under the lens, as a running index: it is not wrapped, so
  // going right always counts up.
  scroll: SharedValue<number>;
  fx: RailFx;
  // How long a friend's check waits after they join, so it follows the catch.
  markDelay: number;
  reduced: boolean;
  haptics: boolean;
  label: string;
  value: string;
  peekFx: PeekFx;
  onInvite: (index: number) => void;
  onActionLayout: (layout: LayoutRectangle) => void;
  onFocus: (index: number) => void;
  // Called once the canvas has drawn every blur it will need.
  onReady: () => void;
  // A finger has come down on the row.
  onTouch: () => void;
}) {
  const n = friends.length;
  const cx = width / 2;
  const cy = S.cy;
  const contentWidth = width - S.cardPad * 2;
  const { peek } = peekFx;
  const { t } = usePreferences();
  const [capsule, setCapsule] = useState({ open: false, index: 0, joined: false });
  const actionBounds = useSharedValue<LayoutRectangle>({ x: 0, y: 0, width: 0, height: 0 });
  const actionPress = useSharedValue(0);
  const capsuleStatus = (joined: boolean) => joined
    ? t("Joined · +25 points", "Đã tham gia · +25 điểm")
    : width < 320 ? t("Not invited", "Chưa mời") : t("Not invited yet", "Chưa mời");
  const reportPeek = (open: boolean, index: number) => {
    if (open) {
      setCapsule({ open, index, joined: joined[index] });
      AccessibilityInfo.announceForAccessibility(`${friends[index].name}. ${capsuleStatus(joined[index])}.`);
    } else setCapsule((was) => ({ ...was, open }));
  };
  useAnimatedReaction(
    () => peekFx.open.get(),
    (open, before) => {
      if (before === null || open === before) return;
      scheduleOnRN(reportPeek, open, wrapIndex(Math.round(scroll.get()), n));
    },
  );
  const tick = () => {
    if (haptics) void Haptics.selectionAsync().catch(() => {});
  };
  const changePeek = (open: boolean) => {
    "worklet";
    if (open) {
      peekFx.launching.set(-1);
      scheduleOnRN(tick);
    }
    movePeek(peekFx, open, reduced);
  };
  const inviteFocused = () => {
    "worklet";
    const index = wrapIndex(Math.round(scroll.get()), n);
    if (!peekFx.open.get() || joined[index]) return;
    peekFx.launching.set(peek.get());
    peekFx.shape.set(0);
    peekFx.label.set(0);
    changePeek(false);
    scheduleOnRN(onInvite, index);
  };

  // 1 while the row is moved by a finger or settling from one; a press on
  // the card's button moves it too, and that has its own feedback.
  const hand = useSharedValue(0);
  const down = useSharedValue(0);
  const from = useSharedValue(0);
  // The friend under the lens when the finger came down.
  const origin = useSharedValue(0);
  const moved = useSharedValue(0);
  const tapMoved = useSharedValue(false);
  const canPeek = useSharedValue(false);
  const follower = useSharedValue(Number.NaN);
  // A short swell of the ticks as the row locks on a friend.
  const lock = useSharedValue(0);

  // A new friend under the lens: a detent tick when a hand brought them.
  useAnimatedReaction(
    () => Math.round(scroll.get()),
    (now, before) => {
      if (before === null || now === before) return;
      if (hand.get() === 1) scheduleOnRN(tick);
      scheduleOnRN(onFocus, wrapIndex(now, n));
    },
  );
  // The row has come to rest on a friend.
  useAnimatedReaction(
    () => down.get() === 0 && Math.abs(scroll.get() - Math.round(scroll.get())) < 0.05,
    (rest, before) => {
      if (!rest || before !== false || hand.get() === 0 || reduced) return;
      lock.set(
        withSequence(
          withTiming(1, { duration: 90, easing: EASE_OUT }),
          withSpring(0, { duration: 420, dampingRatio: 0.55 }),
        ),
      );
    },
  );

  // A flick keeps its velocity and may run a little past the friend it lands
  // on; a tap, which carried none, comes to rest without passing it.
  const settle = (to: number, velocity: number, flick: boolean) => {
    "worklet";
    if (reduced) scroll.set(gentle(to));
    else if (flick) scroll.set(withSpring(to, { duration: 400, dampingRatio: 0.86, velocity }));
    else scroll.set(withSpring(to, { duration: 380, dampingRatio: 1 }));
  };

  // The visible action can still be arriving or pressed; its target never
  // shrinks below 44 pt, and only this region invites rather than closes.
  const hitsAction = (x: number, y: number) => {
    "worklet";
    const bounds = actionBounds.get();
    if (!peekFx.open.get() || peek.get() <= PEEK.action || bounds.width === 0 || joined[wrapIndex(Math.round(scroll.get()), n)]) return false;
    const scale = reduced ? 1 : 0.92 + 0.08 * peekPart(peek.get(), PEEK.action);
    const actionX = bounds.x + bounds.width / 2 - (reduced ? 0 : capsuleShift(width, peek.get()));
    const actionY = bounds.y + bounds.height / 2;
    return Math.abs(x - actionX) <= Math.max(44, bounds.width * scale) / 2 &&
      Math.abs(y - actionY) <= Math.max(44, bounds.height * scale) / 2;
  };

  // One Pan with no minimum distance, so a touch catches a moving row at
  // once. A release that never moved is a tap: it brings the avatar under
  // the finger to the lens.
  const gesture = useMemo(
    () =>
      Gesture.Pan()
        .minDistance(0)
        .onBegin((e) => {
          actionPress.set(hitsAction(e.x, e.y) ? 1 : 0);
          const at = scroll.get();
          canPeek.set(Math.abs(at - Math.round(at)) < REST_GAP && (reduced || Math.abs(at - follower.get()) < REST_GAP));
          tapMoved.set(false);
          cancelAnimation(scroll);
          origin.set(Math.round(scroll.get()));
          moved.set(0);
          down.set(1);
          hand.set(1);
          scheduleOnRN(onTouch);
        })
        .onUpdate((e) => {
          if (!hitsAction(e.x, e.y) || Math.abs(e.translationX) > 4 || Math.abs(e.translationY) > 4) actionPress.set(0);
          if (Math.abs(e.translationY) > 4) tapMoved.set(true);
          if (moved.get() === 0 && Math.abs(e.translationX) > 4) {
            // The drag starts from where the row is now, so the 4 pt it took
            // to tell a drag from a tap is not made up in one frame.
            changePeek(false);
            moved.set(1);
            from.set(scroll.get() + e.translationX / S.pitch);
          }
          if (moved.get() === 1) scroll.set(from.get() - e.translationX / S.pitch);
        })
        // Everything is decided here and not in onEnd: a touch that never
        // moved never activates the pan, so it ends without "success", and
        // that touch is exactly the tap this has to handle.
        .onFinalize((e) => {
          actionPress.set(0);
          down.set(0);
          if (moved.get() === 1) {
            // A flick carries the row on, three friends at most, and a quick
            // one always moves it at least to the next friend.
            const v = -e.velocityX / S.pitch;
            const carry = Math.min(Math.max(v * 0.16, -3), 3);
            let to = Math.round(scroll.get() + carry);
            if (Math.abs(v) > 1.5 && to === origin.get()) to += Math.sign(v);
            settle(to, v, true);
            return;
          }
          if (tapMoved.get()) {
            changePeek(false);
            settle(Math.round(scroll.get()), 0, false);
            return;
          }
          const dx = e.x - cx;
          const dy = e.y - cy;
          if (peekFx.open.get()) {
            if (hitsAction(e.x, e.y)) {
              inviteFocused();
            } else changePeek(false);
            return;
          }
          // A closing capsule can reverse from its current width too.
          const halfWidth = S.ring + (contentWidth / 2 - S.ring) * peek.get();
          const end = Math.max(Math.abs(dx) - (halfWidth - S.ring), 0);
          if (end * end + dy * dy <= S.ring * S.ring && (canPeek.get() || peek.get() > 0)) {
            settle(Math.round(scroll.get()), 0, false);
            changePeek(true);
            return;
          }
          changePeek(false);
          const steps =
            Math.abs(dx) < S.ring ? 0 : Math.sign(dx) * Math.min(Math.max(Math.round((Math.abs(dx) - S.tuck) / S.pitch), 1), 3);
          settle(Math.round(scroll.get()) + steps, 0, false);
        }),
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [cx, cy, width, contentWidth, reduced, joined, onInvite, haptics],
  );

  // VoiceOver and TalkBack step the row with a swipe up or down.
  const step = (by: number) => {
    onTouch();
    changePeek(false);
    hand.set(1);
    const to = Math.round(scroll.get()) + by;
    scroll.set(reduced ? gentle(to) : withSpring(to, { duration: 380, dampingRatio: 1 }));
  };

  // How far the row is from resting on a friend, 0 locked to 1 half way.
  const loose = useDerivedValue(() => {
    const off = Math.abs(scroll.get() - Math.round(scroll.get())) * 2;
    return off * off * (3 - 2 * off);
  });

  // The lens light takes the color of the friend under it.
  const glowOpacity = useDerivedValue(
    () => fx.enter.get() * (0.2 + 0.55 * fx.glow.get()) * (1 - 0.5 * loose.get()),
  );
  // The lens takes the hit as the comet reaches the ring.
  const lens = useDerivedValue(() => {
    const p = fx.pop.get();
    return [{ scale: (1 + 0.05 * p) * (0.9 + 0.1 * fx.enter.get()) }];
  });
  // How fast the row is moving, in friends a second. A follower trails the
  // row by about 90 ms, and the gap between them is the speed; it closes by
  // itself when the row stops, so nothing runs while the card is at rest.
  useAnimatedReaction(
    () => scroll.get(),
    (now) => {
      if (reduced) return;
      const was = follower.get();
      if (was !== was) follower.set(now);
      else follower.set(withTiming(now, { duration: LAG, easing: Easing.linear }));
    },
  );
  // The GPU compiles a blur's shaders the first time each kernel size is
  // drawn, which can hitch the first flick. So before the card arrives the
  // row is run once through every blur it can show, drawn at 1% so nothing is
  // seen, and the card waits for that.
  const warm = useSharedValue(reduced ? 0 : 1);
  useEffect(() => {
    if (reduced) {
      onReady();
      return;
    }
    warm.set(
      withTiming(0, { duration: 320, easing: Easing.linear }, (done) => {
        if (done) scheduleOnRN(onReady);
      }),
    );
    // Once, when the canvas mounts.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);
  // The speed the blurs are drawn for: none below a slow drag, so a friend
  // being looked at stays sharp, and only a flick streaks. And the least the
  // row is drawn at.
  const rush = useDerivedValue(() => {
    const gap = follower.get();
    const speed = gap !== gap ? 0 : Math.abs(scroll.get() - gap) / (LAG / 1000);
    return Math.max(speed - 1.5, 0) + 13 * warm.get();
  });
  const faint = useDerivedValue<number>(() => (warm.get() > 0 ? 0.012 : 0));
  // Half a frame of travel under the lens, as a horizontal sigma.
  const focus = useDerivedValue(() => ({ x: Math.min(rush.get() * 0.6, 7), y: 0 }));
  const lensOpacity = useDerivedValue(() => Math.max(fx.enter.get(), faint.get()));
  const closed = useDerivedValue(() => 1 - Math.min(Math.max(peek.get(), 0), 1));
  const surface = useDerivedValue(() => 1 - closed.get());
  const ringRect = useDerivedValue(() => {
    const w = S.ring * 2 + (contentWidth - S.ring * 2) * peek.get();
    return rrect(rect(cx - w / 2, cy - S.ring, w, S.ring * 2), S.ring, S.ring);
  });
  const ring = useDerivedValue(() => {
    return [{ scale: (S.ring + (2.5 * loose.get() - 1.2 * lock.get()) * closed.get()) / S.ring }];
  });
  const trackOpacity = useDerivedValue(() => fx.enter.get() * (1 - 0.45 * loose.get()));
  // Nothing is drawn of a spent arc, not even its round caps.
  const arcOpacity = useDerivedValue(() =>
    fx.arcEnd.get() - fx.arcStart.get() < 0.004 ? 0 : fx.enter.get() * (1 - 0.3 * loose.get()) * closed.get(),
  );
  const arcWidth = useDerivedValue(() => S.arcStroke + 1.25 * fx.flash.get());
  const flashOpacity = useDerivedValue(() => fx.flash.get() * closed.get());
  const arcStart = useDerivedValue(() => Math.min(Math.max(fx.arcStart.get(), 0), 1));
  const arcEnd = useDerivedValue(() => Math.min(Math.max(fx.arcEnd.get(), 0), 1));

  const arc = useMemo(
    () =>
      Skia.PathBuilder.Make()
        .addArc(rect(cx - S.ring, cy - S.ring, S.ring * 2, S.ring * 2), -90, 359.99)
        .build(),
    [cx, cy],
  );

  // Four ticks on the diagonals. They shorten while the row moves, reach out
  // as it locks, and are thrown outward at the catch.
  const ticks = useDerivedValue(() => {
    const p = Skia.PathBuilder.Make();
    const k = fx.kick.get();
    const a = S.ring + (S.tickFrom - S.ring + 6 * k + 2 * loose.get()) * closed.get();
    const len = (S.tickLen * (1 - 0.5 * loose.get()) + 9 * k + 4 * lock.get()) * fx.enter.get() * closed.get();
    const b = a + Math.max(len, 0);
    for (let i = 0; i < 4; i++) {
      const t = Math.PI / 4 + (i * Math.PI) / 2;
      const c = Math.cos(t);
      const s = Math.sin(t);
      p.moveTo(cx + c * a, cy + s * a).lineTo(cx + c * b, cy + s * b);
    }
    return p.build();
  });
  const tickOpacity = useDerivedValue(
    () => closed.get() * fx.enter.get() * Math.min(0.38 - 0.16 * loose.get() + 0.5 * Math.max(fx.kick.get(), 0) + 0.2 * lock.get(), 1),
  );

  const waveR = useDerivedValue(() => S.ring + 40 * fx.wave.get());
  const waveOpacity = useDerivedValue(() => 0.5 * Math.pow(1 - fx.wave.get(), 1.5));
  const waveWidth = useDerivedValue(() => 0.5 + 1.5 * (1 - fx.wave.get()));
  const crestR = useDerivedValue(() => S.ring + 58 * fx.crest.get());
  const crestOpacity = useDerivedValue(() => 0.7 * Math.pow(1 - fx.crest.get(), 1.4));
  const crestWidth = useDerivedValue(() => 0.5 + 2.5 * (1 - fx.crest.get()));

  // It lands on the points with a ring of its own.
  const rippleR = useDerivedValue(() => 12 + 20 * easeOut(fx.ripple.get()));
  const rippleOpacity = useDerivedValue(() => 0.45 * Math.pow(1 - fx.ripple.get(), 1.6));

  const shine = useMemo(() => {
    const r = S.lens - 3.5;
    return Skia.PathBuilder.Make().addArc(rect(cx - r, cy - r, r * 2, r * 2), -152, 44).build();
  }, [cx, cy]);
  const mask = useMemo(
    () => ["rgba(0,0,0,0)", "rgba(0,0,0,1)", "rgba(0,0,0,1)", "rgba(0,0,0,0)"],
    [],
  );
  const lensClip = useDerivedValue(() => {
    const w = S.lens * 2 + (contentWidth - 10 - S.lens * 2) * peek.get();
    return rrect(rect(cx - w / 2, cy - S.lens, w, S.lens * 2), S.lens, S.lens);
  });

  return (
    <GestureDetector gesture={gesture}>
      <View style={{ width, height: S.railHeight }}>
        <View
          style={StyleSheet.absoluteFill}
          accessible
          accessibilityRole="adjustable"
          accessibilityLabel={label}
          accessibilityValue={{ text: value }}
          accessibilityActions={[{ name: "increment" }, { name: "decrement" }, { name: "activate" }]}
          onAccessibilityAction={(e) => {
            if (e.nativeEvent.actionName === "activate") {
              onTouch();
              if (peekFx.open.get()) changePeek(false);
              else if (Math.abs(scroll.get() - Math.round(scroll.get())) < 0.05) changePeek(true);
            } else step(e.nativeEvent.actionName === "increment" ? 1 : -1);
          }}
        />
        <Canvas pointerEvents="none" style={StyleSheet.absoluteFill}>
          <Circle cx={cx} cy={cy} r={34} color={fx.tint} opacity={glowOpacity}>
            <BlurMask blur={18} style="normal" />
          </Circle>

          {/* The row outside the lens, on its own layer so the lens can be cut
              out of it and both ends can fade, whatever lies under the card. */}
          <Group layer>
            <Group transform={[{ translateX: cx }, { translateY: cy }]}>
              {friends.map((f, i) => (
                <SideAvatar key={f.id} index={i} count={n} image={images[i]} joined={joined[i]} markDelay={markDelay} scroll={scroll} rush={rush} faint={faint} enter={fx.enter} reduced={reduced} peek={peek} width={contentWidth} />
              ))}
            </Group>
            <RoundedRect rect={ringRect} blendMode="clear" />
            <Rect x={0} y={0} width={width} height={S.railHeight} blendMode="dstIn">
              <LinearGradient start={vec(0, 0)} end={vec(width, 0)} colors={mask} positions={[0, 0.3, 0.7, 1]} />
            </Rect>
          </Group>

          <RoundedRect rect={ringRect} color={referralTone.pane} opacity={surface} />
          <Group transform={lens} origin={{ x: cx, y: cy }} opacity={lensOpacity}>
            <Group clip={lensClip}>
              <Group
                layer={
                  <Paint>
                    <Blur blur={focus} />
                  </Paint>
                }
              >
                <Group transform={[{ translateX: cx }, { translateY: cy }]}>
                  {friends.map((f, i) => (
                    <LensAvatar key={f.id} index={i} count={n} image={images[i]} joined={joined[i]} markDelay={markDelay} scroll={scroll} reduced={reduced} peek={peek} width={contentWidth} />
                  ))}
                </Group>
              </Group>
              {/* Glass: the rim of the lens darkens, and a thin light sits on
                  its upper left. */}
              <Group opacity={closed}>
                <Circle cx={cx} cy={cy} r={S.lens}>
                  <RadialGradient
                    c={vec(cx, cy)}
                    r={S.lens}
                    colors={["rgba(0,0,0,0)", "rgba(0,0,0,0)", "rgba(0,0,0,0.3)"]}
                    positions={[0, 0.74, 1]}
                  />
                </Circle>
                <Path path={shine} style="stroke" strokeWidth={1.5} strokeCap="round" color="rgba(255,255,255,0.3)">
                  <BlurMask blur={0.8} style="normal" />
                </Path>
              </Group>
            </Group>
          </Group>

          <Circle cx={cx} cy={cy} r={waveR} style="stroke" strokeWidth={waveWidth} color={referralTone.ring} opacity={waveOpacity} />
          <Circle cx={cx} cy={cy} r={crestR} style="stroke" strokeWidth={crestWidth} color={referralTone.ring} opacity={crestOpacity} />

          <Group transform={ring} origin={{ x: cx, y: cy }}>
            <RoundedRect rect={ringRect} style="stroke" strokeWidth={S.ringStroke} color={referralTone.ringTrack} opacity={trackOpacity} />
            <Circle cx={cx} cy={cy} r={S.ring} style="stroke" strokeWidth={S.ringStroke + 1} color={referralTone.ring} opacity={flashOpacity} />
            <Path
              path={arc}
              style="stroke"
              strokeWidth={arcWidth}
              strokeCap="round"
              color={referralTone.ring}
              start={arcStart}
              end={arcEnd}
              opacity={arcOpacity}
            />
          </Group>
          <Path path={ticks} style="stroke" strokeWidth={1.25} strokeCap="round" color={referralTone.tick} opacity={tickOpacity} />

          <Circle cx={cx} cy={cy - S.pointsRise} r={rippleR} style="stroke" strokeWidth={1.25} color={referralTone.ring} opacity={rippleOpacity} />
        </Canvas>
        <ReferralCapsule
          width={width}
          name={friends[capsule.index].name}
          status={capsuleStatus(capsule.joined)}
          action={capsule.joined ? t("Joined", "Đã tham gia") : t("Invite", "Mời")}
          joined={capsule.joined}
          open={capsule.open}
          fx={peekFx}
          press={actionPress}
          reduced={reduced}
          onInvite={() => { onTouch(); inviteFocused(); }}
          onActionLayout={(bounds) => {
            actionBounds.set(bounds);
            onActionLayout(bounds);
          }}
        />
      </View>
    </GestureDetector>
  );
}

// The check a friend carries once they have joined; `mark` runs 0 to 1.
function useMark(joined: boolean, delay: number, reduced: boolean) {
  const mark = useSharedValue(joined ? 1 : 0);
  useEffect(() => {
    if (!joined) mark.set(gentle(0, 180));
    else mark.set(reduced ? gentle(1) : withDelay(delay, withSpring(1, { duration: 480, dampingRatio: 0.6 })));
    // The delay belongs to the press that made this friend join.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [joined, reduced, mark]);
  return mark;
}

const CHECK = Skia.PathBuilder.Make().moveTo(-3.3, 0.2).lineTo(-1, 2.5).lineTo(3.4, -2.2).build();

function SideAvatar({
  index,
  count,
  image,
  joined,
  markDelay,
  scroll,
  rush,
  faint,
  enter,
  reduced,
  peek,
  width,
}: {
  index: number;
  count: number;
  image: SkImage;
  joined: boolean;
  markDelay: number;
  scroll: SharedValue<number>;
  // How fast the row moves, in friends a second, never negative.
  rush: DerivedValue<number>;
  // The least it is drawn at: above 0 only while the blurs warm up.
  faint: DerivedValue<number>;
  enter: DerivedValue<number>;
  reduced: boolean;
  peek: SharedValue<number>;
  width: number;
}) {
  const r = S.avatar / 2;
  const mark = useMark(joined, markDelay, reduced);
  const transform = useDerivedValue(() => {
    const d = offsetOf(index, scroll.get(), count);
    const a = Math.abs(d);
    // The row gathers in from wider apart as the card arrives.
    const spread = 1 + 0.3 * (1 - enter.get());
    return [{ translateX: Math.sign(d) * ((a * S.pitch + S.tuck * smooth(a)) * spread + (width / 2 - S.focus / 2) * peek.get()) }];
  });
  const opacity = useDerivedValue(() => {
    const a = Math.abs(offsetOf(index, scroll.get(), count));
    return Math.max(enter.get() * ramp(a, DIM) * (1 - smooth((a - 3) * 2)), faint.get()) * (1 - Math.min(Math.max(peek.get(), 0), 1));
  });
  const badge = useDerivedValue(() => Math.min(Math.max(mark.get(), 0), 1));
  // Depth of field: sharp beside the lens, softer with every friend further
  // out, so the row dissolves towards its ends instead of only fading.
  const soft = useDerivedValue(() => {
    if (reduced) return 0;
    const a = Math.abs(offsetOf(index, scroll.get(), count));
    return ramp(a, SOFT);
  });

  // The same motion blur as under the lens, on top of the depth of field.
  const blur = useDerivedValue(() => ({
    x: soft.get() + Math.min(rush.get() * 0.43, 5),
    y: soft.get(),
  }));
  // The rim and the check are too fine to survive the blur; they go with it.
  const crisp = useDerivedValue(() => Math.max(1 - soft.get() / 1.1, 0));
  const check = useDerivedValue(() => badge.get() * crisp.get());

  // The blur is a filter on each drawing's own paint. A blurred layer per
  // avatar, inside the row's layer, stops the whole canvas from drawing.
  return (
    <Group transform={transform} opacity={opacity}>
      <Circle cx={0} cy={0} r={r}>
        <ImageShader image={image} fit="cover" rect={rect(-r, -r, r * 2, r * 2)} />
        <ColorMatrix matrix={DULL} />
        <Blur blur={blur} />
      </Circle>
      <Circle cx={0} cy={0} r={r - 0.5} style="stroke" strokeWidth={1} color={EDGE} opacity={crisp} />
      <Group transform={[{ translateX: 15 }, { translateY: 15 }, { scale: 0.62 }]} opacity={check}>
        <Circle cx={0} cy={0} r={10.5} blendMode="dstOut" />
        <Circle cx={0} cy={0} r={8} color={referralTone.ink} />
        <Path path={CHECK} style="stroke" strokeWidth={2} strokeCap="round" strokeJoin="round" color={referralTone.night} />
      </Group>
    </Group>
  );
}

function LensAvatar({
  index,
  count,
  image,
  joined,
  markDelay,
  scroll,
  reduced,
  peek,
  width,
}: {
  index: number;
  count: number;
  image: SkImage;
  joined: boolean;
  markDelay: number;
  scroll: SharedValue<number>;
  reduced: boolean;
  peek: SharedValue<number>;
  width: number;
}) {
  const r = S.focus / 2;
  const mark = useMark(joined, markDelay, reduced);
  const transform = useDerivedValue(() => [{ translateX: offsetOf(index, scroll.get(), count) * S.lensPitch - (width / 2 - S.ring) * peek.get() }]);
  const opacity = useDerivedValue(() => index === wrapIndex(Math.round(scroll.get()), count)
    ? 1 : 1 - Math.min(Math.max(peek.get() / 0.33, 0), 1));
  // The badge grows from most of its size, never from nothing; its check is
  // drawn as it lands.
  const badge = useDerivedValue(() => [{ translateX: 19 }, { translateY: 19 }, { scale: 0.6 + 0.4 * mark.get() }]);
  const badgeOpacity = useDerivedValue(() => Math.min(Math.max(mark.get() * 6, 0), 1));
  const drawn = useDerivedValue(() => Math.min(Math.max((mark.get() - 0.3) / 0.6, 0), 1));

  return (
    <Group transform={transform} opacity={opacity}>
      <Circle cx={0} cy={0} r={r}>
        <ImageShader image={image} fit="cover" rect={rect(-r, -r, r * 2, r * 2)} />
      </Circle>
      <Circle cx={0} cy={0} r={r - 0.5} style="stroke" strokeWidth={1} color={EDGE} />
      <Group transform={badge} opacity={badgeOpacity}>
        <Circle cx={0} cy={0} r={10.5} blendMode="dstOut" />
        <Circle cx={0} cy={0} r={8} color={referralTone.ink} />
        <Path path={CHECK} style="stroke" strokeWidth={2} strokeCap="round" strokeJoin="round" color={referralTone.night} start={0} end={drawn} />
      </Group>
    </Group>
  );
}
