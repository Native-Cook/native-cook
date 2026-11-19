export type RingControlsProps = {
  width: number;
  metal: number;
  stone: number;
  carat: number;
  /** The booking went through: the button reads "Viewing requested" for a moment. */
  booked: boolean;
  onMetal: (i: number) => void;
  onStone: (i: number) => void;
  onCarat: (ct: number) => void;
  onBook: () => void;
};
