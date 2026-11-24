import { Host, Slider } from "@expo/ui/swift-ui";
import { tint } from "@expo/ui/swift-ui/modifiers";
import type { JSX } from "react";

import type { SwitchControlsProps } from "./SwitchControls.types";
import { BRIGHT_MIN } from "./switchMotion";
import { AMBER } from "./switchScene";

/** The brightness slider: the system's (SwiftUI). The film moves it by state; a finger uses it the native way.
 * Android gets the React Native bar in SwitchControls.tsx. */
export function SwitchControls({ value, onChange, onEditing, width }: SwitchControlsProps): JSX.Element {
  return (
    <Host colorScheme="light" style={{ width, height: 30 }}>
      <Slider max={1} min={BRIGHT_MIN} modifiers={[tint(AMBER)]} onEditingChanged={onEditing} onValueChange={onChange} value={value} />
    </Host>
  );
}
