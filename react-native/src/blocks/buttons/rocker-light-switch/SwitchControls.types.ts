export type SwitchControlsProps = {
  /** The brightness on show, BRIGHT_MIN..1. */
  value: number;
  onChange: (v: number) => void;
  /** A finger took / let go of the slider. */
  onEditing: (active: boolean) => void;
  width: number;
};
