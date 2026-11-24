export type AttachMenuProps = {
  /** Called with the picked item ("photo", "file" or "poll") and when the button is touched at all. */
  onPick: (item: 'photo' | 'file' | 'poll') => void;
  onTouch: () => void;
  tint: string;
};
