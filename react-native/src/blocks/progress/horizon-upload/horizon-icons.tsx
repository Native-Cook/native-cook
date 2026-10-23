import { useEffect, useRef, type JSX } from "react";
import { StyleSheet } from "react-native";
import { Canvas, Image as SkiaImage, useImage, type SkImage } from "@shopify/react-native-skia";
import Animated, {
  cancelAnimation, Easing, ReduceMotion, useAnimatedReaction, useAnimatedStyle,
  useFrameCallback, useSharedValue, withSpring, withTiming,
} from "react-native-reanimated";
import { horizonSize } from "./horizon-palette";
import type { HorizonFx, HorizonPhase, Signal } from "./horizon-state";

const PADDING = 6;
const STATUS_ART = 20;
const STATUS_BOX = horizonSize.statusGlyph + PADDING * 2;
const STATUS_IMAGE = (STATUS_ART + PADDING * 2) * horizonSize.statusGlyph / STATUS_ART;
const BUTTON_BOX = 44;
const PHASES: HorizonPhase[] = ["idle", "uploading", "paused", "done", "stopped"];
const FADE = { duration: 220, easing: Easing.linear, reduceMotion: ReduceMotion.Never };

/** The status icon in a padded box, with the 20 pt art scaled to statusGlyph. */
export function HorizonStatusIcon({ phase, fx, reduced }: {
  phase: HorizonPhase; fx: HorizonFx; reduced: boolean;
}): JSX.Element {
  const idle = useImage(require("./assets/status-idle.png"));
  const spinner = useImage(require("./assets/status-spinner.png"));
  const paused = useImage(require("./assets/status-paused.png"));
  const done = useImage(require("./assets/status-done.png"));
  const canceled = useImage(require("./assets/status-canceled.png"));
  const ready = !!(idle && spinner && paused && done && canceled);
  const rotation = useSharedValue(0);
  const clock = useFrameCallback((frame) => {
    // Stop the clock itself when hidden or reduced, so an outgoing spinner
    // freezes where it was instead of jumping to a new orientation.
    const elapsed = Math.max(frame.timeSincePreviousFrame ?? 0, 0);
    rotation.set((rotation.get() + elapsed / 900 * 360) % 360);
  }, false);
  useEffect(() => {
    clock.setActive(ready && phase === "uploading" && !reduced);
    return () => clock.setActive(false);
  }, [clock, ready, phase, reduced]);
  const light = useAnimatedStyle(() => ({
    opacity: 0.7 + 0.3 * Math.min(1, Math.max(0, (fx.power.get() - 0.1) / 0.9)),
  }));
  const images = { idle, uploading: spinner, paused, done, stopped: canceled };

  return (
    <Animated.View pointerEvents="none" accessible={false} accessibilityElementsHidden
      importantForAccessibility="no-hide-descendants" style={[styles.statusBox, light]}>
      {ready && PHASES.map((key) => (
        <StatusFace key={key} ownPhase={key} phase={phase} active={phase === key} image={images[key]!}
          reduced={reduced} rotation={rotation} />
      ))}
    </Animated.View>
  );
}

function StatusFace({ ownPhase, phase, active, image, reduced, rotation }: {
  ownPhase: HorizonPhase; phase: HorizonPhase; active: boolean; image: SkImage; reduced: boolean; rotation: Signal;
}) {
  const previousActive = useRef<boolean | undefined>(undefined);
  const show = useSharedValue(active ? 1 : 0);
  const scale = useSharedValue(active && ownPhase === "done" && !reduced ? 0.6 : active ? 1 : 0.85);
  useEffect(() => {
    // All five weights use the same linear duration. Even when a transition
    // is interrupted, their total stays one instead of stacking full icons.
    const arriving = active && previousActive.current !== true;
    previousActive.current = active;
    show.set(withTiming(active ? 1 : 0, FADE));
    if (reduced) {
      cancelAnimation(scale);
      scale.set(1);
    } else if (active && ownPhase === "done" && (arriving || scale.get() < 1)) {
      // Effect replay resumes an in-flight pop; changing motion preferences
      // after it settled does not make the coin pop a second time.
      if (arriving) scale.set(0.6);
      scale.set(withSpring(1, { duration: 420, dampingRatio: 0.65 }));
    } else {
      scale.set(withTiming(active ? 1 : 0.85, FADE));
    }
    return () => { cancelAnimation(show); cancelAnimation(scale); };
  }, [active, phase, ownPhase, reduced, scale, show]);
  const appearance = useAnimatedStyle(() => ({
    opacity: show.get(),
    transform: [{ scale: reduced ? 1 : scale.get() }],
  }));
  const turn = useAnimatedStyle(() => ({
    transform: [{ rotate: `${ownPhase === "uploading" && !reduced ? rotation.get() : 0}deg` }],
  }));
  return (
    <Animated.View style={[StyleSheet.absoluteFill, appearance]}>
      <Animated.View style={[StyleSheet.absoluteFill, turn]}>
        <Raster image={image} box={STATUS_BOX} size={STATUS_IMAGE} />
      </Animated.View>
    </Animated.View>
  );
}

/** The close/retry icon in a centered 44 × 44 pt box. The caller sets retry. */
export function HorizonCloseIcon({ retry, fx, reduced, form = "glyph" }: {
  retry: Signal; fx: HorizonFx; reduced: boolean; form?: "button" | "glyph";
}): JSX.Element {
  const cross = useImage(require("./assets/glyph-close.png"));
  const arrow = useImage(require("./assets/glyph-retry.png"));
  const bead = useImage(require("./assets/button-base.png"));
  const ready = !!(cross && arrow && (form === "glyph" || bead));
  const swap = useSharedValue(0);
  useAnimatedReaction(() => retry.get(), (next, before) => {
    const value = Math.min(1, Math.max(0, next));
    if (before === null) { swap.set(value); return; }
    if (next !== before) swap.set(withTiming(value, {
      duration: 260, easing: Easing.linear, reduceMotion: ReduceMotion.Never,
    }));
  });
  useEffect(() => () => cancelAnimation(swap), [swap]);
  const press = useAnimatedStyle(() => ({
    transform: [{ scale: 1 - 0.08 * Math.min(1, Math.max(0, fx.closePress.get())) }],
  }));
  const crossStyle = useAnimatedStyle(() => {
    const out = Math.min(1, Math.max(0, swap.get() * 2));
    return { opacity: 1 - out, transform: [{ scale: reduced ? 1 : 1 - 0.2 * out }] };
  });
  const arrowStyle = useAnimatedStyle(() => {
    const into = Math.min(1, Math.max(0, swap.get() * 2 - 1));
    return { opacity: into, transform: [{ scale: reduced ? 1 : 0.8 + 0.2 * into }] };
  });
  return (
    <Animated.View pointerEvents="none" accessible={false} accessibilityElementsHidden
      importantForAccessibility="no-hide-descendants" style={[styles.closeBox, press]}>
      {ready && <>
        {/* The exported whole-button PNGs are the exact composites of this
            shared dome and these glyphs. Keeping the dome separate here
            prevents it blinking away at the glyph swap's empty midpoint. */}
        {form === "button" && <Raster image={bead!} box={BUTTON_BOX} size={BUTTON_BOX} />}
        <Animated.View style={[StyleSheet.absoluteFill, crossStyle]}>
          <Raster image={cross!} box={BUTTON_BOX} size={BUTTON_BOX} />
        </Animated.View>
        <Animated.View style={[StyleSheet.absoluteFill, arrowStyle]}>
          <Raster image={arrow!} box={BUTTON_BOX} size={BUTTON_BOX} />
        </Animated.View>
      </>}
    </Animated.View>
  );
}

function Raster({ image, box, size }: { image: SkImage; box: number; size: number }) {
  const inset = (box - size) / 2;
  return (
    <Canvas pointerEvents="none" style={StyleSheet.absoluteFill}>
      <SkiaImage image={image} x={inset} y={inset} width={size} height={size} fit="contain" />
    </Canvas>
  );
}

const styles = StyleSheet.create({
  statusBox: { width: STATUS_BOX, height: STATUS_BOX },
  closeBox: { width: BUTTON_BOX, height: BUTTON_BOX },
});
