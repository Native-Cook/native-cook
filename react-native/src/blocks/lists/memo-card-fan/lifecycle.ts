export type MemoCardFanLifecycleFlags = {
  autoPlay: boolean;
  reducedMotion: boolean;
  frozen: boolean;
};

type Scheduler = {
  set: (callback: () => void, delay: number) => ReturnType<typeof setTimeout>;
  clear: (handle: ReturnType<typeof setTimeout>) => void;
};

type Callbacks = {
  pause: () => void;
  returnToRest: (complete: () => void) => void;
  startAutoplay: () => void;
};

export type MemoCardFanLifecycle = {
  updateFlags: (flags: MemoCardFanLifecycleFlags) => void;
  activate: () => void;
  beginContacts: (count?: number) => void;
  endContacts: (count?: number) => void;
  cancelInteraction: () => void;
  dispose: () => void;
  state: () => { contacts: number; timerArmed: boolean; eligible: boolean; generation: number };
};

/** RNGH reports the number of pointers remaining after an onTouchesUp event. */
export function shouldEndMemoCardFanManualGesture(numberOfTouches: number) {
  return numberOfTouches === 0;
}

export function createMemoCardFanLifecycle(
  callbacks: Callbacks,
  delay: number,
  scheduler: Scheduler = { set: setTimeout, clear: clearTimeout },
): MemoCardFanLifecycle {
  let flags: MemoCardFanLifecycleFlags = { autoPlay: false, reducedMotion: false, frozen: false };
  let contacts = 0;
  let generation = 0;
  let timer: ReturnType<typeof setTimeout> | null = null;
  let disposed = false;
  const eligible = () => flags.autoPlay && !flags.reducedMotion && !flags.frozen;
  const invalidate = () => {
    generation += 1;
    if (timer !== null) scheduler.clear(timer);
    timer = null;
  };
  const arm = () => {
    invalidate();
    if (disposed || contacts !== 0 || !eligible()) return;
    const expected = generation;
    timer = scheduler.set(() => {
      timer = null;
      if (disposed || contacts !== 0 || !eligible() || generation !== expected) return;
      callbacks.returnToRest(() => {
        if (disposed || contacts !== 0 || !eligible() || generation !== expected) return;
        callbacks.startAutoplay();
      });
    }, delay);
  };
  return {
    activate() {
      disposed = false;
    },
    updateFlags(next) {
      flags = next;
      if (!eligible()) {
        invalidate();
        callbacks.pause();
      }
    },
    beginContacts(count = 1) {
      const previous = contacts;
      contacts += Math.max(1, count);
      if (previous === 0) {
        invalidate();
        callbacks.pause();
      }
    },
    endContacts(count = 1) {
      contacts = Math.max(0, contacts - Math.max(1, count));
      if (contacts === 0) arm();
    },
    cancelInteraction() {
      invalidate();
      callbacks.pause();
    },
    dispose() {
      disposed = true;
      contacts = 0;
      invalidate();
      callbacks.pause();
    },
    state: () => ({ contacts, timerArmed: timer !== null, eligible: eligible(), generation }),
  };
}
