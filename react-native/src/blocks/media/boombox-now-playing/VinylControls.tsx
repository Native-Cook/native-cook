import { Button, HStack, Host, Image, Slider } from "@expo/ui/swift-ui";
import { buttonStyle, frame, tint } from "@expo/ui/swift-ui/modifiers";
import type { JSX } from "react";
import { Platform, Pressable, StyleSheet, Text, View } from "react-native";

export const INK = "#1D1C1A";
export const SECONDARY = "#77716A";

export type VinylControlsProps = {
  width: number;
  playing: boolean;
  /** Song position 0..1 (the film and the record drive it; it moves in small steps, as a native slider takes it). */
  progress: number;
  volume: number;
  onToggle: () => void;
  onBack: () => void;
  onForward: () => void;
  onScrub: (fraction: number) => void;
  onScrubbing: (active: boolean) => void;
  onVolume: (v: number) => void;
};

/** The system slider (SwiftUI through @expo/ui) on iOS; elsewhere a plain bar stands in. */
export function NativeSlider({
  width,
  value,
  onChange,
  onEditing,
  label,
}: {
  width: number;
  value: number;
  onChange: (v: number) => void;
  onEditing?: (active: boolean) => void;
  label: string;
}): JSX.Element {
  if (Platform.OS === "ios") {
    // a light scheme: the system's track colours suit the background they sit on (the dark ones vanish on paper)
    return (
      <Host colorScheme="light" style={{ width, height: 34 }}>
        <Slider max={1} min={0} modifiers={[tint(INK), frame({ width, height: 34 })]} onEditingChanged={onEditing} onValueChange={onChange} value={value} />
      </Host>
    );
  }
  return (
    <View accessibilityLabel={label} accessibilityValue={{ min: 0, max: 100, now: Math.round(value * 100) }} style={[styles.track, { width }]}>
      <View style={[styles.fill, { width: width * value }]} />
    </View>
  );
}

/** Back, play/pause and forward as system buttons with SF Symbols on iOS; elsewhere plain pressables. */
export function Transport({ playing, onToggle, onBack, onForward, scale }: { playing: boolean; onToggle: () => void; onBack: () => void; onForward: () => void; scale: number }): JSX.Element {
  if (Platform.OS === "ios") {
    const side = 30 * scale;
    const main = 40 * scale;
    return (
      <Host colorScheme="light" matchContents style={{ alignSelf: "center" }}>
        <HStack spacing={44 * scale}>
          <Button modifiers={[buttonStyle("plain"), frame({ width: 56 * scale, height: 56 * scale })]} onPress={onBack}>
            <Image color={INK} size={side} systemName="backward.fill" />
          </Button>
          <Button modifiers={[buttonStyle("plain"), frame({ width: 64 * scale, height: 64 * scale })]} onPress={onToggle}>
            <Image color={INK} size={main} systemName={playing ? "pause.fill" : "play.fill"} />
          </Button>
          <Button modifiers={[buttonStyle("plain"), frame({ width: 56 * scale, height: 56 * scale })]} onPress={onForward}>
            <Image color={INK} size={side} systemName="forward.fill" />
          </Button>
        </HStack>
      </Host>
    );
  }
  return (
    <View style={[styles.row, { gap: 40 * scale }]}>
      <Pressable accessibilityLabel="Back to the start" accessibilityRole="button" hitSlop={10} onPress={onBack}>
        <Text style={[styles.glyph, { fontSize: 26 * scale }]}>⏮</Text>
      </Pressable>
      <Pressable accessibilityLabel={playing ? "Pause" : "Play"} accessibilityRole="button" hitSlop={10} onPress={onToggle}>
        <Text style={[styles.glyph, { fontSize: 38 * scale }]}>{playing ? "⏸" : "▶"}</Text>
      </Pressable>
      <Pressable accessibilityLabel="Forward" accessibilityRole="button" hitSlop={10} onPress={onForward}>
        <Text style={[styles.glyph, { fontSize: 26 * scale }]}>⏭</Text>
      </Pressable>
    </View>
  );
}

/** The volume row: the system slider between a quiet and a loud speaker symbol. */
export function VolumeRow({ width, volume, onVolume, scale }: { width: number; volume: number; onVolume: (v: number) => void; scale: number }): JSX.Element {
  const icon = 18 * scale;
  if (Platform.OS === "ios") {
    return (
      <View style={styles.row}>
        <Host colorScheme="light" matchContents>
          <Image color={SECONDARY} size={icon * 0.8} systemName="speaker.fill" />
        </Host>
        <View style={{ marginHorizontal: 10 * scale }}>
          <NativeSlider label="Volume" onChange={onVolume} value={volume} width={width - 2 * icon - 20 * scale} />
        </View>
        <Host colorScheme="light" matchContents>
          <Image color={SECONDARY} size={icon * 0.8} systemName="speaker.wave.3.fill" />
        </Host>
      </View>
    );
  }
  return <NativeSlider label="Volume" onChange={onVolume} value={volume} width={width} />;
}

const styles = StyleSheet.create({
  row: { flexDirection: "row", alignItems: "center", justifyContent: "center" },
  track: { height: 4, borderRadius: 2, backgroundColor: "rgba(29,28,26,0.14)", overflow: "hidden", marginVertical: 13 },
  fill: { height: "100%", backgroundColor: INK },
  glyph: { color: INK },
});
