import { Button, Host } from "@expo/ui/swift-ui";
import { buttonBorderShape, buttonStyle, controlSize, tint } from "@expo/ui/swift-ui/modifiers";
import type { JSX } from "react";

import type { SafeLockButtonProps } from "./SafeLockButton.types";
import { GREEN } from "./safeScene";

/** Locks the vault again: the system's button (Android gets the one in SafeLockButton.tsx). */
export function SafeLockButton({ onPress }: SafeLockButtonProps): JSX.Element {
  return (
    <Host colorScheme="light" matchContents>
      <Button
        label="Lock"
        modifiers={[buttonStyle("borderedProminent"), buttonBorderShape("capsule"), controlSize("small"), tint(GREEN)]}
        onPress={onPress}
        systemImage="lock.fill"
      />
    </Host>
  );
}
