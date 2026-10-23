import { useCallback, useMemo } from "react";
import { StyleSheet, Text, useWindowDimensions, View } from "react-native";
import { Gesture, GestureDetector } from "react-native-gesture-handler";
import { useReducedMotion, useSharedValue } from "react-native-reanimated";
import { useSafeAreaInsets } from "react-native-safe-area-context";

import { ActionButton } from "./ActionButton";
import { BallBackdrop } from "./BallBackdrop";
import { BALL_BLURB, getBall } from "./ballCatalog";
import { ballFacts, groundFromBall, wordmark } from "./ballEditorial";
import { BallStage3D } from "./BallStage3D";
import { BallStrip } from "./BallStrip";
import { BallWordmark } from "./BallWordmark";
import {
  PICKER_BALL_CENTER_FRACTION,
  pickerSourceBoundarySize,
} from "./ballPickerGeometry";
import { MAX_TYPE_SCALE } from "./constants";
import { useBallTilt } from "./useBallTilt";
import { useChooseBallCarousel } from "./useChooseBallCarousel";

const WORDMARK_FRACTION = 0.2;
const WORDMARK_MAX = 82;
const COMPACT_HEIGHT = 740;

function noop(): void {}

// The block has the whole screen: the 26-ball belt, one ball at a time on a Filament stage. Swipe
// sideways (or tap the strip) to change ball, drag up or down to look over and under it.
//
// What it needs beyond its own folder, all set up in this demo app:
// - a development build: react-native-filament is native code, so Expo Go cannot run it;
// - Metro bundling .glb and .ktx as assets (react-native/metro.config.js);
// - the patches in react-native/patches (applied by patch-package after install): the one for
//   react-native-filament adds Engine.setACESLegacyColorGrading, which BallStage3D calls.
// The host supplies GestureHandlerRootView and SafeAreaProvider.
export default function ChooseYourBallDemo() {
  const { height, width } = useWindowDimensions();
  const insets = useSafeAreaInsets();
  const reducedMotion = useReducedMotion();
  const compact = height < COMPACT_HEIGHT;
  const drain = useSharedValue(0);
  const { offset, order, panGesture, preview, previewBall, selectSlot, total } =
    useChooseBallCarousel("dusk");
  const { tilt, tiltGesture } = useBallTilt({ enabled: true, reducedMotion });
  const gesture = useMemo(() => Gesture.Race(panGesture, tiltGesture), [panGesture, tiltGesture]);
  const ball = getBall(previewBall);
  const [hero, tail] = useMemo(() => wordmark(ball.label), [ball.label]);
  const facts = useMemo(() => ballFacts(ball, BALL_BLURB[previewBall]), [ball, previewBall]);
  const grounds = useMemo(() => order.map((id) => groundFromBall(getBall(id).fx.aura)), [order]);
  const sourceBoundarySize = pickerSourceBoundarySize(width, compact);
  const sourceCentreY = height * PICKER_BALL_CENTER_FRACTION;
  const wordSize = Math.min(WORDMARK_MAX, width * WORDMARK_FRACTION) * (compact ? 0.88 : 1);
  const ballStyle = useMemo(
    () => ({
      height: sourceBoundarySize,
      top: sourceCentreY - sourceBoundarySize / 2,
      width: sourceBoundarySize,
    }),
    [sourceBoundarySize, sourceCentreY]
  );
  const handleSelect = useCallback((slot: number) => selectSlot(slot), [selectSlot]);

  return (
    <View style={styles.screen}>
      <BallBackdrop colors={grounds} drain={drain} height={height} offset={offset} total={total} />

      <GestureDetector gesture={gesture}>
        <View style={StyleSheet.absoluteFill}>
          <View
            pointerEvents="none"
            style={[styles.header, { paddingTop: Math.max(insets.top, 12) + 4 }]}
          >
            <Text maxFontSizeMultiplier={MAX_TYPE_SCALE} style={styles.eyebrow}>
              YOUR BELT
            </Text>
            <Text maxFontSizeMultiplier={MAX_TYPE_SCALE} style={styles.count}>
              {String(preview + 1).padStart(2, "0")} / {total}
            </Text>
          </View>

          <View
            pointerEvents="none"
            style={[
              styles.wordmarkSlot,
              { top: sourceCentreY - sourceBoundarySize * 0.42 - wordSize * 1.1 },
            ]}
          >
            <BallWordmark
              hero={hero}
              reducedMotion={reducedMotion}
              size={wordSize}
              tail={tail}
              width={width}
            />
          </View>

          <View pointerEvents="none" style={[styles.ballSlot, ballStyle]}>
            <BallStage3D
              offset={offset}
              reducedMotion={reducedMotion}
              size={sourceBoundarySize}
              tilt={tilt}
              variant={previewBall}
            />
          </View>

          <View
            pointerEvents="box-none"
            style={[styles.foot, { paddingBottom: Math.max(insets.bottom, 14) }]}
          >
            <View style={styles.chips}>
              {facts.map((fact) => (
                <View key={fact} style={styles.chip}>
                  <Text maxFontSizeMultiplier={MAX_TYPE_SCALE} style={styles.chipText}>
                    {fact}
                  </Text>
                </View>
              ))}
            </View>

            <View style={styles.action}>
              <ActionButton
                icon="→"
                label={`EQUIP ${ball.label.toUpperCase()}`}
                labelToken={preview}
                onPress={noop}
              />
            </View>

            <View style={styles.stripSlot}>
              <BallStrip offset={offset} onPick={handleSelect} order={order} total={total} />
            </View>
          </View>
        </View>
      </GestureDetector>
    </View>
  );
}

const styles = StyleSheet.create({
  action: {
    paddingHorizontal: 18,
    paddingTop: 14,
  },
  ballSlot: {
    alignSelf: "center",
    position: "absolute",
  },
  chip: {
    backgroundColor: "rgba(255,255,255,0.09)",
    borderColor: "rgba(255,255,255,0.16)",
    borderRadius: 999,
    borderWidth: StyleSheet.hairlineWidth,
    paddingHorizontal: 11,
    paddingVertical: 5,
  },
  chipText: {
    color: "rgba(255,255,255,0.9)",
    fontSize: 9,
    fontWeight: "800",
    letterSpacing: 0.9,
  },
  chips: {
    flexDirection: "row",
    gap: 7,
    justifyContent: "center",
  },
  count: {
    color: "rgba(255,255,255,0.45)",
    fontSize: 10,
    fontVariant: ["tabular-nums"],
    fontWeight: "800",
    letterSpacing: 1.2,
  },
  eyebrow: {
    color: "#F0C548",
    fontSize: 9,
    fontWeight: "900",
    letterSpacing: 1.9,
  },
  foot: {
    bottom: 0,
    left: 0,
    position: "absolute",
    right: 0,
  },
  header: {
    alignItems: "baseline",
    flexDirection: "row",
    justifyContent: "space-between",
    left: 0,
    paddingHorizontal: 18,
    position: "absolute",
    right: 0,
    top: 0,
  },
  screen: {
    backgroundColor: "#06080C",
    flex: 1,
  },
  stripSlot: {
    height: 42,
    marginTop: 14,
  },
  wordmarkSlot: {
    left: 0,
    position: "absolute",
    right: 0,
  },
});
