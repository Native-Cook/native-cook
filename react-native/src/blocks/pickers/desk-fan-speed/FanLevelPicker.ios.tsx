import { Host, Picker, Text } from "@expo/ui/swift-ui";
import { pickerStyle, tag } from "@expo/ui/swift-ui/modifiers";
import type { JSX } from "react";

import type { FanLevelPickerProps } from "./FanLevelPicker.types";
import { LEVELS } from "./fanScene";

/** Off, 1, 2, 3: the system's segmented control (Android gets the row in FanLevelPicker.tsx). */
export function FanLevelPicker({ level, onLevel, width }: FanLevelPickerProps): JSX.Element {
  return (
    <Host colorScheme="light" matchContents={{ vertical: true }} style={{ width }}>
      <Picker modifiers={[pickerStyle("segmented")]} onSelectionChange={onLevel} selection={level}>
        {LEVELS.map((l, i) => (
          <Text key={l} modifiers={[tag(i)]}>
            {l}
          </Text>
        ))}
      </Picker>
    </Host>
  );
}
