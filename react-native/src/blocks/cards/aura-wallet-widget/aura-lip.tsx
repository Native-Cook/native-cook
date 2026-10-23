import { useMemo } from "react";
import { Image, StyleSheet, View } from "react-native";
import { Gesture, GestureDetector } from "react-native-gesture-handler";
import Animated, { Easing, ReduceMotion, useAnimatedStyle, useSharedValue, withSpring, withTiming } from "react-native-reanimated";
import type { AuraDrive } from "./aura-drive";
import { AuraPhase, auraEnterOpacity, auraEnterScale, auraFlow, auraLipPose, auraNudgeDip, auraSendMoments, auraSendPose, auraSlotX } from "./aura-flow";
import type { AuraMachine } from "./aura-machine";
import { auraDesign as D, auraMotion, type AuraFrames } from "./aura-palette";
import { FRIENDS, type AuraPerson } from "./aura-people";
import { usePreferences } from "./i18n";

// Each portrait follows the same drop as the glass beneath it. A stationary
// touch target keeps the moving face from moving the finger's coordinate space.

export function AuraLip({ frames, drive, machine }: {
  frames: AuraFrames;
  drive: AuraDrive;
  machine: AuraMachine;
}) {
  return (
    <View pointerEvents="box-none" style={StyleSheet.absoluteFill}>
      {FRIENDS.map((friend, index) => (
        <Friend key={friend.id} person={friend} index={index} frames={frames} drive={drive} machine={machine} />
      ))}
    </View>
  );
}

function Friend({ person, index, frames: f, drive, machine }: {
  person: AuraPerson;
  index: number;
  frames: AuraFrames;
  drive: AuraDrive;
  machine: AuraMachine;
}) {
  const { t } = usePreferences();
  const press = useSharedValue(0);
  const accepted = useSharedValue(false);
  const { phase, chosen, formed, leaving, unformed, nudge, pullX, pullY, clock, fromX, fromY, entered } = drive;
  const { reduced, offsets, pick, beginPull, movePull, endPull, isLocked, touch: markTouched } = machine;
  const { u } = f;
  const side = f.lip.avatar;
  const zoomed = side * D.faceZoom;
  const touchSide = Math.max(side, D.touch.minimum);
  const touch = {
    left: f.lip.xs[index] - touchSide / 2,
    top: f.lip.cy - touchSide / 2,
    width: touchSide,
    height: touchSide,
  };
  const face = {
    left: (touchSide - side) / 2,
    top: (touchSide - side) / 2,
    width: side,
    height: side,
    borderRadius: side / 2,
  };
  const portrait = {
    width: zoomed,
    height: zoomed,
    left: (side - zoomed) / 2,
    top: (side - zoomed) / 2 + zoomed * D.faceLift,
  };
  const poseStyle = useAnimatedStyle(() => {
    const currentPhase = phase.get();
    const currentChosen = chosen.get();
    const currentFormed = formed.get();
    const currentLeaving = leaving.get();
    const currentUnformed = unformed.get();
    const elapsedNudge = nudge.get();
    const fingerX = pullX.get();
    const fingerY = pullY.get();
    const elapsed = clock.get();
    const originX = fromX.get();
    const originY = fromY.get();
    const residual = offsets.get();
    const held = press.get();
    const pose = auraLipPose(index, currentChosen, currentFormed, currentLeaving, currentUnformed);
    let x = pose.dx + residual[index * 4];
    let y = pose.dy + residual[index * 4 + 1] + auraNudgeDip(index, elapsedNudge);
    let scaleX = pose.scale + residual[index * 4 + 2];
    let scaleY = scaleX;
    let opacity = pose.opacity + residual[index * 4 + 3];
    if (index === currentChosen) {
      x += fingerX;
      y += fingerY;
      if (currentPhase === AuraPhase.sending) {
        const drop = auraSendPose(elapsed, originY);
        const bud = elapsed > auraSendMoments(originY).bud;
        x = bud ? 0 : originX - auraSlotX(index);
        y = bud ? 0 : drop.y - D.lip.cy;
        scaleX = bud ? drop.bud : drop.wide;
        scaleY = bud ? drop.bud : drop.tall;
        opacity = bud ? drop.budOpacity : drop.presence;
        if (reduced) opacity = bud ? drop.budOpacity : Math.max(0, 1 - elapsed / auraFlow.views.departureFade);
      }
    }
    const compression = currentPhase === AuraPhase.sending && index === currentChosen
      ? 1
      : 1 + (auraMotion.pressScale - 1) * held;
    const arrival = entered.get();
    if (!reduced && arrival >= 0) {
      const growth = auraEnterScale(index, arrival);
      scaleX *= growth;
      scaleY *= growth;
      opacity *= auraEnterOpacity(index, arrival);
    }
    return {
      opacity: Math.max(0, Math.min(1, opacity)),
      transform: [
        { translateX: reduced ? 0 : x * u },
        { translateY: reduced ? 0 : y * u },
        { scaleX: reduced ? 1 : scaleX * compression },
        { scaleY: reduced ? 1 : scaleY * compression },
      ],
    };
  });

  const gesture = useMemo(() => {
    const pan = Gesture.Pan()
      .minDistance(auraFlow.views.pullSlop)
      .maxPointers(1)
      .onBegin(() => {
        markTouched();
        accepted.set(!isLocked());
        if (!accepted.get()) return;
        press.set(withTiming(1, {
          duration: auraMotion.pressIn,
          easing: Easing.bezier(...auraMotion.easeOut),
          reduceMotion: ReduceMotion.Never,
        }));
      })
      .onStart((event) => {
        if (!accepted.get()) return;
        beginPull(index);
        movePull(index, event.translationX / u, event.translationY / u);
      })
      .onUpdate((event) => { if (accepted.get()) movePull(index, event.translationX / u, event.translationY / u); })
      .onEnd((event, success) => { endPull(index, event.velocityX / u, event.velocityY / u, !success); })
      .onFinalize((event, success) => {
        if (!success) endPull(index, event.velocityX / u, event.velocityY / u, true);
        press.set(withSpring(0, { ...auraMotion.release, reduceMotion: ReduceMotion.Never }));
      });
    const tap = Gesture.Tap()
      .maxDistance(auraFlow.views.pullSlop)
      .onEnd((_event, success) => { if (success && accepted.get()) pick(index); });
    return Gesture.Race(pan, tap);
  }, [beginPull, movePull, endPull, index, u, press, pick, isLocked, accepted, markTouched]);

  return (
    <GestureDetector gesture={gesture}>
      <View
        collapsable={false}
        accessible
        accessibilityRole="button"
        accessibilityLabel={person.name}
        accessibilityHint={t("Double tap to choose, then Send.", "Chạm hai lần để chọn, rồi nhấn Gửi.")}
        accessibilityState={{ selected: machine.state.chosen === index && machine.state.phase !== AuraPhase.rest }}
        accessibilityActions={[{ name: "activate" }]}
        onAccessibilityAction={() => machine.accessiblePick(index)}
        style={[styles.absolute, touch]}
      >
        <Animated.View
          pointerEvents="none"
          accessible={false}
          accessibilityElementsHidden
          importantForAccessibility="no-hide-descendants"
          style={[styles.disc, face, poseStyle]}
        >
          <Image source={person.image} accessible={false} style={[styles.absolute, portrait]} />
        </Animated.View>
      </View>
    </GestureDetector>
  );
}

const styles = StyleSheet.create({
  absolute: { position: "absolute" },
  disc: { position: "absolute", overflow: "hidden" },
});
