import { useEffect } from "react";
import { StyleSheet, View } from "react-native";
import Animated, { useAnimatedStyle } from "react-native-reanimated";
import { GestureHandlerRootView } from "react-native-gesture-handler";
import { AuraActions } from "./aura-actions";
import { AuraActivity } from "./aura-activity";
import { AuraBalance } from "./aura-balance";
import { useAuraDrive } from "./aura-drive";
import { AuraLip } from "./aura-lip";
import { useAuraMachine, type AuraHint } from "./aura-machine";
import { auraFrames, type AuraSkin } from "./aura-palette";
import { AuraShell } from "./aura-shell";
import { useMotionPrefs } from "./ui";

// One square drawing: material below, faces and type above. The UI-thread
// drive is shared by both layers; only words leave the widget for its hint.
export function AuraWidget({ width, skin, onHint }: {
  width: number;
  skin: AuraSkin;
  autoPlay?: boolean;
  onHint?: (hint: AuraHint) => void;
}) {
  const frames = auraFrames(width);
  const drive = useAuraDrive();
  const { reduced } = useMotionPrefs();
  const machine = useAuraMachine(drive, reduced);
  const square = { width, height: width };
  const hint = machine.hint;
  const hintKind = machine.hintKind;
  useEffect(() => { onHint?.({ text: hint, kind: hintKind }); }, [hint, hintKind, onHint]);
  // Type and faces wait for the glass: see the arrival in aura-machine.ts.
  const { shown } = machine;
  const presence = useAnimatedStyle(() => ({ opacity: shown.get() }));

  return (
    <GestureHandlerRootView style={square}>
      <View style={square} onTouchStart={machine.onTouch}>
        <AuraShell width={width} skin={skin} drive={drive} />
        <Animated.View pointerEvents="box-none" style={[StyleSheet.absoluteFill, presence]}>
          <AuraBalance frames={frames} skin={skin} machine={machine} />
          <AuraActivity frames={frames} skin={skin} machine={machine} />
          <AuraLip frames={frames} drive={drive} machine={machine} />
          <AuraActions frames={frames} skin={skin} drive={drive} machine={machine} />
        </Animated.View>
      </View>
    </GestureHandlerRootView>
  );
}
