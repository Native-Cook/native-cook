import { AccessibilityInfo, AppState } from 'react-native';
import { scheduleOnUI } from 'react-native-worklets';
import type { SharedValue } from 'react-native-reanimated';
import { PaddockWidgetBentoController } from './controller';
import { DEFAULT_ORDER, type Plan } from './motion';
import { copy } from './theme';
import type { Destination } from './StaticViews';
import { impact, selection } from './a11y';

/** Shared values the UI runtime evaluates every frame. */
export type Shared = {
  plan: SharedValue<Plan>;
  clock: SharedValue<number>;
  offset: SharedValue<number>;
  ticking: SharedValue<boolean>;
  dragY: SharedValue<number>;
};

/** What React renders; everything else lives in the controller and the UI runtime. */
export type ViewState = {
  running: boolean;
  editing: boolean;
  order: number[];
  tab: Destination;
  destination: Destination | null;
  reaction: string;
};

export const initialView: ViewState = { running: false, editing: false, order: DEFAULT_ORDER.slice(), tab: 'home', destination: null, reaction: copy.reaction.ready };

const FRAME_THRESHOLD = 1 / 55;
/** The frame clock starts this long before a motion window so the UI samples the exact boundary, not a late wake. */
const PRE_ROLL = 0.1;
const WAKE_SLACK_MS = 8;
const REACTION_DELAY_MS = 3000;
const CARD_DESTINATION: Destination[] = ['laps', 'reflex', 'homeCards'];
const CARD_NAME = [copy.cards.laps.name, copy.cards.reflex.name, copy.cards.homeCards.name];

const now = () => performance.now() / 1000;

/** Pushes a plan to the UI runtime together with the clock: the two runtimes' `performance.now()` origins differ. */
function applyPlan(shared: Shared, next: Plan, time: number, play: boolean) {
  'worklet';
  shared.offset.set(time - performance.now() / 1000);
  shared.clock.set(time);
  shared.ticking.set(play);
  shared.plan.set(next);
}

/**
 * Owns the controller, its wake schedule, the touch ownership and the screen's event state (Swift: the `@State` and
 * private functions of PaddockWidgetBento). Arrow-function members so the gesture worklets can hand them to
 * `scheduleOnRN` without capturing the instance.
 */
export class PaddockRuntime {
  readonly controller = new PaddockWidgetBentoController();
  private view: ViewState = { ...initialView };
  private autoPlay: boolean;
  private quiet = false;
  private ready = false;
  private owner: number | null = null;
  private card = -1;
  private feedTop = true;
  private wakeTimer: ReturnType<typeof setTimeout> | null = null;
  private reactionTimer: ReturnType<typeof setTimeout> | null = null;
  private reactionStart: number | null = null;
  private mounted = false;

  constructor(private readonly shared: Shared, private readonly publish: (state: ViewState) => void, autoPlay: boolean) {
    this.autoPlay = autoPlay;
  }

  /** Subscribes to the OS and starts the controller; returns the cleanup. */
  mount = () => {
    this.mounted = true;
    const time = now();
    this.controller.appear(this.autoPlay, false, time);
    // Inactive until the reduce-motion and screen-reader preferences are known: the demo must not start before them.
    this.controller.setActive(false, time);
    this.wake();
    const appState = AppState.addEventListener('change', (phase) => this.lifecycle(phase === 'active'));
    const reduce = AccessibilityInfo.addEventListener('reduceMotionChanged', () => void this.readQuiet(false));
    const reader = AccessibilityInfo.addEventListener('screenReaderChanged', () => void this.readQuiet(false));
    void this.readQuiet(true);
    return () => {
      this.mounted = false;
      appState.remove();
      reduce.remove();
      reader.remove();
      this.clearWake();
      this.cancelReaction();
      this.clearTouch();
      this.controller.setActive(false, now());
    };
  };

  setAutoPlay = (value: boolean) => {
    if (value === this.autoPlay) return;
    this.autoPlay = value;
    const time = now();
    this.controller.setAutoPlay(value, time);
    this.wake();
  };

  private readQuiet = async (initial: boolean) => {
    const [reduce, reader] = await Promise.all([AccessibilityInfo.isReduceMotionEnabled(), AccessibilityInfo.isScreenReaderEnabled()]).catch(() => [false, false]);
    if (!this.mounted) return;
    const time = now();
    this.quiet = reduce || reader;
    this.controller.setQuiet(this.quiet, time);
    if (initial) {
      this.ready = true;
      this.controller.setActive(AppState.currentState === 'active', time);
      this.controller.setVisible(this.view.tab === 'home' && this.feedTop && this.view.destination === null, time);
    }
    this.wake();
  };

  private lifecycle = (active: boolean) => {
    const time = now();
    this.controller.setActive(active && this.ready, time);
    if (!active) {
      this.clearTouch();
      this.cancelReaction();
    }
    this.wake();
  };

  /** Commits the controller's bookkeeping, pushes its plan to the UI runtime and schedules the next wake. */
  wake = () => {
    if (!this.mounted) return;
    this.clearWake();
    const time = now();
    const controller = this.controller;
    controller.advance(time);
    const plan = controller.plan(time);
    const interval = controller.cadence(time);
    let play = false;
    let delay: number | null = null;
    if (interval !== null) {
      if (interval <= FRAME_THRESHOLD) {
        play = true;
        const end = controller.frameWindowEnd(time);
        delay = end === null ? null : end;
      } else if (interval <= PRE_ROLL) {
        play = true;
        delay = interval;
      } else {
        delay = interval - PRE_ROLL;
      }
    }
    scheduleOnUI(applyPlan, this.shared, plan, time, play);
    this.emit({ running: play, editing: controller.isEditing, order: controller.order.slice() });
    if (delay !== null) this.wakeTimer = setTimeout(this.wake, delay * 1000 + WAKE_SLACK_MS);
  };

  private clearWake() {
    if (this.wakeTimer !== null) clearTimeout(this.wakeTimer);
    this.wakeTimer = null;
  }

  private emit(patch: Partial<ViewState>) {
    const next = { ...this.view, ...patch };
    const same =
      next.running === this.view.running &&
      next.editing === this.view.editing &&
      next.tab === this.view.tab &&
      next.destination === this.view.destination &&
      next.reaction === this.view.reaction &&
      next.order.length === this.view.order.length &&
      next.order.every((value, index) => value === this.view.order[index]);
    if (same) return;
    this.view = next;
    this.publish(next);
  }

  // Touch: the gesture runs on the UI runtime and reports here (Swift: rawTouch*).

  touchDown = (owner: number, id: number, referenceY: number, time: number) => {
    if (this.owner !== null && this.owner !== owner) {
      this.controller.beginPress(id, referenceY, owner, time);
      this.clearTouch();
      this.wake();
      return;
    }
    this.owner = owner;
    this.card = id;
    this.controller.beginPress(id, referenceY, owner, time);
    this.wake();
  };

  touchHold = (owner: number, id: number, referenceY: number, time: number, visibleY: number) => {
    if (this.owner !== owner || this.card !== id) return;
    this.controller.beginDrag(id, referenceY, owner, time, { recognized: true, visibleY });
    this.wake();
    impact();
  };

  /** The held card's slot changed (the UI runtime already moved the card itself). */
  candidateMoved = (owner: number, referenceY: number, time: number) => {
    if (this.owner !== owner) return;
    this.controller.updateDrag(referenceY, owner, time);
    this.wake();
    selection();
  };

  /** Finger lifted: commits a held card's slot, or finishes a short press. */
  touchUp = (owner: number, referenceY: number, time: number) => {
    if (this.owner !== owner) return;
    const changed = this.controller.endDrag(owner, time, referenceY);
    this.clearTouch();
    this.wake();
    if (changed) impact();
  };

  /** The system took the touch away (a second finger, the scroll view, the app going to the background). */
  touchCancel = (owner: number, referenceY: number, time: number) => {
    if (this.owner !== owner) return;
    this.controller.syncDragY(referenceY);
    this.controller.cancelDrag(time);
    this.clearTouch();
    this.wake();
  };

  /** A quick tap on a quick card opens its destination, unless the cards are being arranged. */
  tap = (id: number) => {
    if (id < 0 || this.controller.isEditing) return;
    this.present(CARD_DESTINATION[id]);
  };

  private clearTouch() {
    this.owner = null;
    this.card = -1;
  }

  // Accessibility and settings actions.

  accessibleMove = (id: number, offset: number) => {
    const time = now();
    if (!this.controller.isEditing) this.controller.setEditing(true, time);
    if (this.controller.move(id, offset, time)) AccessibilityInfo.announceForAccessibility(`${CARD_NAME[id]} moved`);
    this.wake();
  };

  /** The pull-to-refresh pit stop or a peel of the sticker: the demo steps aside for `seconds`, then restarts. */
  holdDemo = (seconds: number) => {
    this.controller.holdDemo(now() + seconds);
    this.wake();
  };

  doneEditing = () => {
    this.controller.done(now());
    this.wake();
  };

  toggleEditing = () => {
    const time = now();
    this.controller.setEditing(!this.controller.isEditing, time);
    this.wake();
  };

  restoreOrder = () => {
    const time = now();
    this.controller.done(time);
    this.controller.reset(this.autoPlay, this.quiet, time);
    this.controller.setVisible(this.view.tab === 'home' && this.feedTop && this.view.destination === null, time);
    this.wake();
  };

  // Navigation.

  present = (value: Destination) => {
    const time = now();
    this.controller.cancelDrag(time);
    this.controller.setVisible(false, time);
    if (value !== 'reflex') this.cancelReaction();
    this.emit({ destination: value });
    this.wake();
  };

  closeDestination = () => {
    if (this.view.destination === null) return;
    this.cancelReaction();
    const time = now();
    this.emit({ destination: null });
    this.controller.setVisible(this.view.tab === 'home' && this.feedTop, time);
    this.wake();
  };

  selectTab = (tab: Destination) => {
    if (this.view.tab === tab) return;
    this.clearTouch();
    const time = now();
    if (this.controller.isEditing) this.controller.done(time);
    this.feedTop = true;
    this.emit({ tab });
    this.controller.setVisible(tab === 'home', time);
    this.wake();
  };

  feedTopChanged = (top: boolean) => {
    if (this.view.tab !== 'home' || this.feedTop === top) return;
    this.feedTop = top;
    this.controller.setVisible(top && this.view.destination === null, now());
    this.wake();
  };

  // The local reaction test (Swift: reactionAction).

  reactionAction = () => {
    const text = this.view.reaction;
    const reaction = copy.reaction;
    if (text.endsWith(' ms')) {
      this.cancelReaction();
      return;
    }
    if (text === reaction.ready || text.startsWith('Too soon')) {
      this.emit({ reaction: reaction.getReady });
      if (this.reactionTimer !== null) clearTimeout(this.reactionTimer);
      this.reactionTimer = setTimeout(() => {
        if (this.view.destination !== 'reflex' || AppState.currentState !== 'active') return;
        this.reactionStart = now();
        this.emit({ reaction: reaction.go });
      }, REACTION_DELAY_MS);
    } else if (text === reaction.go && this.reactionStart !== null) {
      this.emit({ reaction: `${Math.floor((now() - this.reactionStart) * 1000)} ms` });
      this.reactionStart = null;
    } else {
      this.cancelReaction();
      this.emit({ reaction: reaction.tooSoon });
    }
  };

  private cancelReaction() {
    if (this.reactionTimer !== null) clearTimeout(this.reactionTimer);
    this.reactionTimer = null;
    this.reactionStart = null;
    if (this.view.reaction !== copy.reaction.ready) this.emit({ reaction: copy.reaction.ready });
  }
}

export function reactionButtonTitle(text: string) {
  const reaction = copy.reaction;
  if (text === reaction.ready || text.startsWith('Too soon')) return reaction.start;
  return text.endsWith(' ms') ? reaction.again : reaction.tap;
}
