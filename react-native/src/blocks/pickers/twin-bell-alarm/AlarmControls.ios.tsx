import { DatePicker, Host, Toggle } from "@expo/ui/swift-ui";
import { clipped, datePickerStyle, frame, labelsHidden, tint } from "@expo/ui/swift-ui/modifiers";
import type { JSX } from "react";
import { useCallback, useMemo } from "react";

import type { AlarmSwitchProps, AlarmWheelProps } from "./AlarmControls.types";
import { YELLOW } from "./alarmScene";

/** The time wheel: the system's (SwiftUI DatePicker, wheel style). The film moves it by state; a finger uses it the
 * native way. Android gets the React Native wheels in AlarmControls.tsx. */
export function AlarmWheel({ minutes, onMinutes, width, height }: AlarmWheelProps): JSX.Element {
  const date = useMemo(() => new Date(2026, 0, 5, Math.floor(minutes / 60), minutes % 60, 0, 0), [minutes]);
  const change = useCallback(
    (d: Date) => {
      onMinutes(d.getHours() * 60 + d.getMinutes());
    },
    [onMinutes],
  );
  return (
    <Host colorScheme="light" style={{ width, height }}>
      <DatePicker
        displayedComponents={["hourAndMinute"]}
        modifiers={[datePickerStyle("wheel"), labelsHidden(), frame({ width, height }), clipped()]}
        onDateChange={change}
        selection={date}
      />
    </Host>
  );
}

/** The alarm's switch: the system's (SwiftUI Toggle), with its label. */
export function AlarmSwitch({ on, onToggle, width }: AlarmSwitchProps): JSX.Element {
  return (
    <Host colorScheme="light" style={{ width, height: 32 }}>
      <Toggle isOn={on} label="Alarm" modifiers={[tint(YELLOW)]} onIsOnChange={onToggle} />
    </Host>
  );
}
