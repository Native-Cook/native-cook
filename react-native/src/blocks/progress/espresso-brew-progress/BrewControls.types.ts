export type BrewControlsProps = {
  /** 0 = Single, 1 = Double. */
  dose: number;
  onDose: (dose: number) => void;
  /** The Brew button's label and press. */
  label: string;
  busy: boolean;
  onBrew: () => void;
  /** Width of the segmented control, and of the box the button is centred in. */
  pickerWidth: number;
  buttonWidth: number;
};
