export type FeedFilterProps = {
  options: readonly string[];
  /** Index into options. */
  selected: number;
  onSelect: (index: number) => void;
  width: number;
};
