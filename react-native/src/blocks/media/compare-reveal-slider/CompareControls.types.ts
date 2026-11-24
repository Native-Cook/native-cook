export type CompareControlsProps = {
  /** 0 = Day, 1 = Evening */
  selected: number;
  onSelect: (index: number) => void;
  width: number;
};

export const LIGHT_OPTIONS = ['Day', 'Evening'];
