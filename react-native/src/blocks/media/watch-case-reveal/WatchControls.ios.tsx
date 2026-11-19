import { Button, Host, Picker, Text, VStack } from "@expo/ui/swift-ui";
import { buttonBorderShape, buttonStyle, controlSize, frame, pickerStyle, tag, tint } from "@expo/ui/swift-ui/modifiers";
import type { JSX } from "react";

import type { WatchControlsProps } from "./WatchControls.types";
import { DIALS, INK } from "./watchScene";

/**
 * The dial picker and the reserve button: real SwiftUI controls (iOS). The film drives them by state (the picker's
 * selection, the button's confirmation); a finger uses them the native way. Android gets the React Native row in WatchControls.tsx.
 */
export function WatchControls({ dial, onDial, reserved, onReserve, width }: WatchControlsProps): JSX.Element {
  return (
    <Host colorScheme="light" matchContents={{ vertical: true }} style={{ width }}>
      <VStack spacing={14}>
        <Picker modifiers={[pickerStyle("segmented")]} onSelectionChange={onDial} selection={dial}>
          {DIALS.map((d, i) => (
            <Text key={d.name} modifiers={[tag(i)]}>
              {d.name}
            </Text>
          ))}
        </Picker>
        <Button
          label={reserved ? "Reserved" : "Reserve in a boutique"}
          modifiers={[buttonStyle("borderedProminent"), buttonBorderShape("capsule"), controlSize("large"), tint(INK), frame({ maxWidth: 10000 })]}
          onPress={onReserve}
          systemImage={reserved ? "checkmark" : "bag"}
        />
      </VStack>
    </Host>
  );
}
