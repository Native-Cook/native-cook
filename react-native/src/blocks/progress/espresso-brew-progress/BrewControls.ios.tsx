import { Button, Host, Picker, Text } from "@expo/ui/swift-ui";
import { buttonBorderShape, buttonStyle, controlSize, disabled, pickerStyle, tag, tint } from "@expo/ui/swift-ui/modifiers";
import type { JSX } from "react";
import { StyleSheet, View } from "react-native";

import type { BrewControlsProps } from "./BrewControls.types";
import { ACCENT, DOSES } from "./espressoScene";

/** Single / Double: the system's segmented control; Brew: the system's prominent button (Android: BrewControls.tsx). */
export function BrewControls({ dose, onDose, label, busy, onBrew, pickerWidth, buttonWidth }: BrewControlsProps): JSX.Element {
  return (
    <View style={styles.row}>
      <Host colorScheme="light" matchContents={{ vertical: true }} style={{ width: pickerWidth }}>
        <Picker modifiers={[pickerStyle("segmented")]} onSelectionChange={onDose} selection={dose}>
          {DOSES.map((d, i) => (
            <Text key={d} modifiers={[tag(i)]}>
              {d}
            </Text>
          ))}
        </Picker>
      </Host>
      <View style={[styles.button, { width: buttonWidth }]}>
        <Host colorScheme="light" matchContents>
          <Button
            label={label}
            modifiers={[buttonStyle("borderedProminent"), buttonBorderShape("capsule"), controlSize("regular"), tint(ACCENT), disabled(busy)]}
            onPress={onBrew}
            systemImage={busy ? "drop.fill" : "cup.and.saucer.fill"}
          />
        </Host>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  row: { flexDirection: "row", alignItems: "center", justifyContent: "space-between" },
  button: { alignItems: "center", justifyContent: "center" },
});
