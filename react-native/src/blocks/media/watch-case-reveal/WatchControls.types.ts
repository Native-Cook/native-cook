export type WatchControlsProps = {
  /** The dial on show (index into DIALS). */
  dial: number;
  onDial: (i: number) => void;
  /** The reservation went through: the button reads "Reserved" for a moment. */
  reserved: boolean;
  onReserve: () => void;
  width: number;
};
