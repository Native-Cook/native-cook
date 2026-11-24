import { Host, Picker, Text } from "@expo/ui/swift-ui";
import { pickerStyle, tag } from "@expo/ui/swift-ui/modifiers";
import type { JSX } from "react";

import { PRESET_NAMES } from "./mixerScene";
import type { PresetPickerProps } from "./PresetPicker.types";

/** Intro, Interview, Outro: the system's segmented control (Android gets the row in PresetPicker.tsx). */
export function PresetPicker({ preset, onPreset, width }: PresetPickerProps): JSX.Element {
  return (
    <Host colorScheme="dark" matchContents={{ vertical: true }} style={{ width }}>
      <Picker modifiers={[pickerStyle("segmented")]} onSelectionChange={onPreset} selection={preset}>
        {PRESET_NAMES.map((p, i) => (
          <Text key={p} modifiers={[tag(i)]}>
            {p}
          </Text>
        ))}
      </Picker>
    </Host>
  );
}
