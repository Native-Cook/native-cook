import { Button, HStack, Host, Picker, Slider, Text, VStack } from "@expo/ui/swift-ui";
import {
  buttonBorderShape,
  buttonStyle,
  controlSize,
  font,
  foregroundStyle,
  frame,
  monospacedDigit,
  pickerStyle,
  tag,
  tint,
} from "@expo/ui/swift-ui/modifiers";
import type { JSX } from "react";

import type { RingControlsProps } from "./RingControls.types";
import { CARAT_MAX, CARAT_MIN, CARAT_STEP, METALS, NAVY, STONES } from "./ringScene";

const LABEL_W = 56;

/**
 * Metal and stone as segmented pickers, carat on a slider, and the booking button: real SwiftUI controls (iOS). The
 * film drives them by state (the pickers' selection, the slider's value, the button's confirmation), never with
 * lookalikes. Android gets the React Native rows in RingControls.tsx.
 */
export function RingControls(p: RingControlsProps): JSX.Element {
  const label = [font({ size: 13, weight: "medium" }), foregroundStyle({ type: "hierarchical", style: "secondary" }), frame({ width: LABEL_W, alignment: "leading" })];
  return (
    <Host colorScheme="light" matchContents={{ vertical: true }} style={{ width: p.width }}>
      <VStack spacing={12}>
        <HStack spacing={8}>
          <Text modifiers={label}>Metal</Text>
          <Picker modifiers={[pickerStyle("segmented")]} onSelectionChange={p.onMetal} selection={p.metal}>
            {METALS.map((m, i) => (
              <Text key={m.name} modifiers={[tag(i)]}>
                {m.short}
              </Text>
            ))}
          </Picker>
        </HStack>
        <HStack spacing={8}>
          <Text modifiers={label}>Stone</Text>
          <Picker modifiers={[pickerStyle("segmented")]} onSelectionChange={p.onStone} selection={p.stone}>
            {STONES.map((s, i) => (
              <Text key={s.name} modifiers={[tag(i)]}>
                {s.name}
              </Text>
            ))}
          </Picker>
        </HStack>
        <HStack spacing={8}>
          <Text modifiers={label}>Carat</Text>
          <Slider max={CARAT_MAX} min={CARAT_MIN} modifiers={[tint(NAVY)]} onValueChange={p.onCarat} step={CARAT_STEP} value={p.carat} />
          <Text modifiers={[font({ size: 15, weight: "semibold" }), monospacedDigit(), frame({ width: 64, alignment: "trailing" })]}>
            {`${p.carat.toFixed(2)} ct`}
          </Text>
        </HStack>
        <Button
          label={p.booked ? "Viewing requested" : "Book a private viewing"}
          modifiers={[buttonStyle("borderedProminent"), buttonBorderShape("capsule"), controlSize("large"), tint(NAVY), frame({ maxWidth: 10000 })]}
          onPress={p.onBook}
          systemImage={p.booked ? "checkmark" : "calendar"}
        />
      </VStack>
    </Host>
  );
}
