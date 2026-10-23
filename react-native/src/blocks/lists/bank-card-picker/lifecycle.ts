import type { AppStateStatus } from 'react-native';

export type AppStateSource = {
  currentState: AppStateStatus;
  addEventListener: (event: 'change', listener: (state: AppStateStatus) => void) => { remove: () => void };
};

export type ForegroundReturnGate = {
  promise: Promise<void>;
  cancel: () => void;
};

/** Subscribe before launching Android's chooser, then resolve only after it leaves and returns active. */
export function createForegroundReturnGate(source: AppStateSource): ForegroundReturnGate {
  let leftActive = false;
  let finished = false;
  let resolvePromise: () => void = () => {};
  const promise = new Promise<void>((resolve) => { resolvePromise = resolve; });
  const subscription = source.addEventListener('change', (state) => {
    if (state !== 'active') {
      leftActive = true;
      return;
    }
    if (leftActive) finish();
  });
  function finish() {
    if (finished) return;
    finished = true;
    subscription.remove();
    resolvePromise();
  }
  return { promise, cancel: finish };
}

export async function runPausedAction<Token, Result>(
  begin: () => Token,
  end: (token: Token) => void,
  action: () => Result | Promise<Result>,
): Promise<Result> {
  const token = begin();
  try {
    return await action();
  } finally {
    end(token);
  }
}

export type FrameDriverFlags = {
  autoPlay: boolean;
  reducedMotion: boolean;
  frozen: boolean;
  manual: boolean;
  modalOpen: boolean;
};

export function shouldRunFrameDriver(flags: FrameDriverFlags) {
  return flags.autoPlay && !flags.reducedMotion && !flags.frozen && !flags.manual && !flags.modalOpen;
}

type Scheduler = {
  set: (callback: () => void, delay: number) => ReturnType<typeof setTimeout>;
  clear: (handle: ReturnType<typeof setTimeout>) => void;
};

type LifecycleCallbacks = {
  pause: () => void;
  startFromRest: () => void;
};

export type BankCardLifecycleController = {
  updateFlags: (flags: Pick<FrameDriverFlags, 'autoPlay' | 'reducedMotion' | 'frozen'>) => void;
  beginContacts: (count?: number) => void;
  endContacts: (count?: number) => void;
  beginAction: () => number;
  endAction: (token: number) => void;
  activate: () => void;
  dispose: () => void;
  state: () => { contacts: number; actions: number; timerArmed: boolean; allowed: boolean };
};

export function createBankCardLifecycleController(
  callbacks: LifecycleCallbacks,
  resumeAfterMs: number,
  scheduler: Scheduler = { set: setTimeout, clear: clearTimeout },
): BankCardLifecycleController {
  let flags = { autoPlay: false, reducedMotion: false, frozen: false };
  let contacts = 0;
  let nextAction = 1;
  const actions = new Set<number>();
  let generation = 0;
  let timer: ReturnType<typeof setTimeout> | null = null;
  let disposed = false;
  const allowed = () => flags.autoPlay && !flags.reducedMotion && !flags.frozen;
  const idle = () => contacts === 0 && actions.size === 0;
  const invalidate = () => {
    generation += 1;
    if (timer !== null) scheduler.clear(timer);
    timer = null;
  };
  const arm = () => {
    invalidate();
    if (disposed || !allowed() || !idle()) return;
    const expected = generation;
    timer = scheduler.set(() => {
      timer = null;
      if (disposed || expected !== generation || !allowed() || !idle()) return;
      callbacks.startFromRest();
    }, resumeAfterMs);
  };
  return {
    updateFlags(next) {
      const wasAllowed = allowed();
      flags = next;
      if (!allowed()) {
        invalidate();
        callbacks.pause();
      } else if (!wasAllowed && idle()) {
        invalidate();
        callbacks.startFromRest();
      }
    },
    beginContacts(count = 1) {
      contacts += Math.max(1, count);
      invalidate();
      callbacks.pause();
    },
    endContacts(count = 1) {
      contacts = Math.max(0, contacts - Math.max(1, count));
      if (contacts === 0) arm();
    },
    beginAction() {
      const token = nextAction++;
      actions.add(token);
      invalidate();
      callbacks.pause();
      return token;
    },
    endAction(token) {
      if (!actions.delete(token)) return;
      if (actions.size === 0) arm();
    },
    activate() {
      disposed = false;
    },
    dispose() {
      disposed = true;
      invalidate();
      actions.clear();
      contacts = 0;
      flags = { autoPlay: false, reducedMotion: false, frozen: false };
      callbacks.pause();
    },
    state: () => ({ contacts, actions: actions.size, timerArmed: timer !== null, allowed: allowed() }),
  };
}
