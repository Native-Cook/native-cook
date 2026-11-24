export type AlarmWheelProps = {
  /** The alarm's time, minutes of the day. */
  minutes: number;
  onMinutes: (minutes: number) => void;
  width: number;
  height: number;
};

export type AlarmSwitchProps = {
  on: boolean;
  onToggle: (on: boolean) => void;
  width: number;
};
