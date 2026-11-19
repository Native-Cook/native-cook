export type CourierActionsProps = {
  /** The system dialog a finger opened: 0 none, 1 "Call Marco?", 2 the quick messages. The film never sets it. */
  dialog: number;
  onCall: () => void;
  onMessage: () => void;
  /** The dialog was dismissed by the system (a tap outside, Cancel). */
  onDismiss: () => void;
  onConfirmCall: () => void;
  onSend: (text: string) => void;
};

export type CourierLocateProps = {
  /** The camera follows the van (filled arrow) or the map was moved by hand (outline). */
  following: boolean;
  onPress: () => void;
};

/** Size of the round buttons (pt) and the gap between Call and Message. */
export const ACTION = 48;
export const ACTION_GAP = 12;
export const QUICK_REPLIES = ["Coming down now", "Leave it at the door"] as const;
export const COURIER_PHONE = "+44 20 7946 0958";
