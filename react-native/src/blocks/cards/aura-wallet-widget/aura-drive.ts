import { type SharedValue, useSharedValue } from "react-native-reanimated";
import { AuraPhase, type AuraPhaseValue, auraFlow } from "./aura-flow";
import { AURA_ACTIONS } from "./aura-palette";

// The values the widget's views and its shader both read. They live on the UI
// thread; nothing here causes a render. What each one means while it moves is
// in aura-flow.ts. Lengths are design points: multiply by the frames' `u`.

export type AuraDrive = {
  /** How far each pillow button is pressed, 0 at rest to 1 held down, in the order of AURA_ACTIONS. */
  press: SharedValue<number>[];
  /** What the widget is doing: a value of AuraPhase. */
  phase: SharedValue<AuraPhaseValue>;
  /** The friend whose drop is formed, pulled or falling; -1 for nobody. */
  chosen: SharedValue<number>;
  /** How formed that drop is: a spring from 0 to 1 that may overshoot. */
  formed: SharedValue<number>;
  /** The friend whose drop is letting go because another was chosen (-1 for nobody), and what is left of it, 1 to 0. */
  leaving: SharedValue<number>;
  unformed: SharedValue<number>;
  /** The pulled drop's offset from where it hangs when formed. Zero unless a finger holds it or Send is sinking it. */
  pullX: SharedValue<number>;
  pullY: SharedValue<number>;
  /** The dollars on offer: the default once a friend is tapped, the pull's depth while pulling. */
  amount: SharedValue<number>;
  /** Milliseconds since the drop was let go. Runs only while sending. */
  clock: SharedValue<number>;
  /** Where in the widget the drop was let go. */
  fromX: SharedValue<number>;
  fromY: SharedValue<number>;
  /** Send grown and Receive stepped aside: a spring from 0 to 1. */
  wide: SharedValue<number>;
  /** Milliseconds into the nudge that runs along the five when Send is pressed with nobody chosen; -1 when it is not running. */
  nudge: SharedValue<number>;
  /** The amount as a share of the largest, 0 to 1, following it with the `drop.weigh` spring: how heavy the drop looks. */
  weight: SharedValue<number>;
  /** Milliseconds into the arrival; -1 once it is over. It starts at 0, the lip closed, so the first frame of glass has no empty rings. */
  entered: SharedValue<number>;
};

export function useAuraDrive(): AuraDrive {
  const settings = useSharedValue(0);
  const swap = useSharedValue(0);
  const receive = useSharedValue(0);
  const send = useSharedValue(0);
  const press = [settings, swap, receive, send];
  if (press.length !== AURA_ACTIONS.length) throw new Error("One press value per action.");
  return {
    press,
    phase: useSharedValue<AuraPhaseValue>(AuraPhase.rest),
    chosen: useSharedValue(-1),
    formed: useSharedValue(0),
    leaving: useSharedValue(-1),
    unformed: useSharedValue(0),
    pullX: useSharedValue(0),
    pullY: useSharedValue(0),
    amount: useSharedValue<number>(auraFlow.pull.amountDefault),
    clock: useSharedValue(0),
    fromX: useSharedValue(0),
    fromY: useSharedValue(0),
    wide: useSharedValue(0),
    nudge: useSharedValue(-1),
    weight: useSharedValue<number>(auraFlow.pull.amountDefault / auraFlow.pull.amountMax),
    entered: useSharedValue(0),
  };
}
